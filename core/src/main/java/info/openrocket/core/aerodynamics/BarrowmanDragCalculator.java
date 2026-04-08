package info.openrocket.core.aerodynamics;

import static info.openrocket.core.util.MathUtil.pow2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import info.openrocket.core.aerodynamics.barrowman.RocketComponentCalc;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.ComponentAssembly;
import info.openrocket.core.rocketcomponent.ExternalComponent;
import info.openrocket.core.rocketcomponent.ExternalComponent.Finish;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.InstanceContext;
import info.openrocket.core.rocketcomponent.InstanceMap;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.util.MathUtil;
import info.openrocket.core.util.PolyInterpolator;
import info.openrocket.core.util.Reflection;

/**
 * Drag portion of the extended Barrowman aerodynamic calculator.
 */
public class BarrowmanDragCalculator implements DragCalculator {

	private static final String BARROWMAN_PACKAGE = "info.openrocket.core.aerodynamics.barrowman";
	private static final String BARROWMAN_SUFFIX = "Calc";

	private final WarningSet ignoreWarningSet = new WarningSet();

	private Map<RocketComponent, RocketComponentCalc> calcMap = null;

	private static final double[] axialDragPoly1;
	private static final double[] axialDragPoly2;

	/** Lower edge of transonic base drag blend region (Mach). */
	private static final double BASE_BLEND_LOW = 0.85;
	/** Upper edge of transonic base drag blend region (Mach). */
	private static final double BASE_BLEND_HIGH = 1.3;
	/** C1-continuous polynomial for base drag in the transonic blend region. */
	private static final double[] baseDragTransonicPoly;

	/**
	 * Devan-Ashwood supersonic base drag correlation constants.
	 * <p>
	 * Cd_base = BASE_DRAG_A + BASE_DRAG_B / M² for M >= BASE_BLEND_HIGH.
	 * <p>
	 * At high Mach the base pressure coefficient asymptotes to a nonzero constant
	 * (~0.064) rather than decaying to zero as the simpler 0.25/M model predicts.
	 * Fitted to turbulent cylindrical afterbody data from Devan & Ashwood (1961,
	 * NASA TN D-721) and Hoerner "Fluid-Dynamic Drag" Ch. 3.
	 */
	private static final double BASE_DRAG_A = 0.064;
	private static final double BASE_DRAG_B = 0.186;

	/** Sutherland's law constant for air (K), for viscosity ratio in Eckert method. */
	private static final double S_SUTHERLAND = 110.4;

	/** Turbulent recovery factor: Pr^(1/3) with Pr ≈ 0.71 for air. */
	private static final double TURBULENT_RECOVERY_FACTOR = Math.pow(0.71, 1.0 / 3.0);

	static {
		PolyInterpolator interpolator;
		interpolator = new PolyInterpolator(
				new double[] { 0, 17 * Math.PI / 180 },
				new double[] { 0, 17 * Math.PI / 180 });
		axialDragPoly1 = interpolator.interpolator(1, 1.3, 0, 0);

		interpolator = new PolyInterpolator(
				new double[] { 17 * Math.PI / 180, Math.PI / 2 },
				new double[] { 17 * Math.PI / 180, Math.PI / 2 },
				new double[] { Math.PI / 2 });
		axialDragPoly2 = interpolator.interpolator(1.3, 0, 0, 0, 0);

		// C1-continuous base drag blend through the transonic region.
		// Matches value and derivative of subsonic model (0.12 + 0.13*M^2) at BASE_BLEND_LOW,
		// passes through a peak of 0.25 at M=1.05 (matching experimental data for
		// cylindrical afterbodies), and matches value and derivative of the
		// Devan-Ashwood supersonic model (A + B/M^2) at BASE_BLEND_HIGH.
		// Degree-4 polynomial: 3 value constraints + 2 derivative constraints.
		double supValue = BASE_DRAG_A + BASE_DRAG_B / (BASE_BLEND_HIGH * BASE_BLEND_HIGH);
		double supDeriv = -2.0 * BASE_DRAG_B / (BASE_BLEND_HIGH * BASE_BLEND_HIGH * BASE_BLEND_HIGH);
		PolyInterpolator baseDragInterp = new PolyInterpolator(
				new double[] { BASE_BLEND_LOW, 1.05, BASE_BLEND_HIGH },
				new double[] { BASE_BLEND_LOW, BASE_BLEND_HIGH });
		baseDragTransonicPoly = baseDragInterp.interpolator(
				0.12 + 0.13 * BASE_BLEND_LOW * BASE_BLEND_LOW,   // subsonic value at M=0.85
				0.25,                                              // peak value at M=1.05
				supValue,                                          // Devan-Ashwood at M=1.3
				0.26 * BASE_BLEND_LOW,                             // subsonic derivative at M=0.85
				supDeriv);                                         // Devan-Ashwood derivative at M=1.3
	}

	@Override
	public DragCalculator newInstance() {
		return new BarrowmanDragCalculator();
	}

	@Override
	public void calculateDrag(FlightConfiguration configuration,
			FlightConditions conditions,
			Map<RocketComponent, AerodynamicForces> componentForces,
			Map<RocketComponent, AerodynamicForces> assemblyForces,
			AerodynamicForces totalForces,
			WarningSet warnings) {
		ensureCalcMap(configuration);
		WarningSet actualWarnings = (warnings != null) ? warnings : ignoreWarningSet;

		double frictionCD = calculateFrictionCD(configuration, conditions, componentForces, actualWarnings);
		double pressureCD = calculatePressureCD(configuration, conditions, componentForces, actualWarnings);
		double baseCD = calculateBaseCD(configuration, conditions, componentForces, actualWarnings);
		double overrideCD = calculateOverrideCD(configuration, componentForces, assemblyForces);

		totalForces.setFrictionCD(frictionCD);
		totalForces.setPressureCD(pressureCD);
		totalForces.setBaseCD(baseCD);
		totalForces.setOverrideCD(overrideCD);
		totalForces.setCD(frictionCD + pressureCD + baseCD + overrideCD);
		totalForces.setCDaxial(calculateAxialCD(conditions, totalForces.getCD()));
	}

	@Override
	public double toAxialDrag(FlightConditions conditions, double cd) {
		return calculateAxialCD(conditions, cd);
	}

	@Override
	public void voidAerodynamicCache() {
		calcMap = null;
	}

	private double calculateFrictionCD(FlightConfiguration configuration, FlightConditions conditions,
			Map<RocketComponent, AerodynamicForces> forceMap, WarningSet warningSet) {
		double mach = conditions.getMach();
		double Re = calculateReynoldsNumber(configuration, conditions);
		double T_e = conditions.getAtmosphericConditions().getTemperature();
		double Cf = calculateFrictionCoefficient(configuration, mach, Re, T_e);
		double roughnessCorrection = calculateRoughnessCorrection(mach);

		ensureCalcMap(configuration);

		double otherFrictionCD = 0;
		double bodyFrictionCD = 0;
		double maxR = 0;
		double minX = Double.MAX_VALUE;
		double maxX = 0;

		double[] roughnessLimited = new double[Finish.values().length];
		Arrays.fill(roughnessLimited, Double.NaN);

		InstanceMap imap = configuration.getActiveInstances();
		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry : imap.entrySet()) {
			RocketComponent c = entry.getKey();

			if (!c.isAerodynamic()) {
				continue;
			}

			if (c.isCDOverridden() || c.isCDOverriddenByAncestor()) {
				continue;
			}

			Finish finish = ((ExternalComponent) c).getFinish();
			if (Double.isNaN(roughnessLimited[finish.ordinal()])) {
				roughnessLimited[finish.ordinal()] = 0.032
						* Math.pow(finish.getRoughnessSize() / configuration.getLengthAerodynamic(), 0.2)
						* roughnessCorrection;
			}

			double componentCf;
			if (configuration.getRocket().isPerfectFinish()) {
				if ((Re > 1.0e6) && (roughnessLimited[finish.ordinal()] > Cf)) {
					componentCf = roughnessLimited[finish.ordinal()];
				} else {
					componentCf = Cf;
				}
			} else {
				componentCf = Math.max(Cf, roughnessLimited[finish.ordinal()]);
			}

			double componentFrictionCD = calcMap.get(c).calculateFrictionCD(conditions, componentCf, warningSet);
			int instanceCount = entry.getValue().size();

			if (c instanceof SymmetricComponent) {
				SymmetricComponent s = (SymmetricComponent) c;

				bodyFrictionCD += instanceCount * componentFrictionCD;

				double componentMinX = c.getAxialOffset(AxialMethod.ABSOLUTE);
				minX = Math.min(minX, componentMinX);

				double componentMaxX = componentMinX + c.getLength();
				maxX = Math.max(maxX, componentMaxX);

				double componentMaxR = Math.max(s.getForeRadius(), s.getAftRadius());
				maxR = Math.max(maxR, componentMaxR);

			} else {
				otherFrictionCD += instanceCount * componentFrictionCD;
			}

			if (forceMap != null && forceMap.get(c) != null) {
				forceMap.get(c).setFrictionCD(componentFrictionCD);
			}
		}

		double fB = (maxX - minX + 0.0001) / maxR;
		double correction = (1 + 1.0 / (2 * fB));

		if (forceMap != null) {
			for (Map.Entry<RocketComponent, AerodynamicForces> entry : forceMap.entrySet()) {
				if (entry.getKey() instanceof SymmetricComponent) {
					entry.getValue().setFrictionCD(entry.getValue().getFrictionCD() * correction);
				}
			}
		}

		return otherFrictionCD + correction * bodyFrictionCD;
	}

	private double calculateReynoldsNumber(FlightConfiguration configuration, FlightConditions conditions) {
		return conditions.getVelocity() * configuration.getLengthAerodynamic() /
				conditions.getAtmosphericConditions().getKinematicViscosity();
	}

	/**
	 * Calculate the compressible skin friction coefficient.
	 * <p>
	 * Subsonic (M &lt; 0.9): uses the incompressible Cf with an empirical Mach
	 * correction factor (unchanged from original OpenRocket).
	 * <p>
	 * Supersonic (M &gt; 1.1): uses the Eckert reference temperature method.
	 * The boundary layer at high Mach is much hotter than the freestream;
	 * evaluating fluid properties at a reference temperature T* and computing
	 * Cf at the resulting Re* naturally accounts for this. The result is scaled
	 * by (T_e / T*) to convert from reference-condition to freestream-referenced Cf.
	 * This gives ~35% Cf reduction at M3 and ~55% at M5, matching published data.
	 * <p>
	 * Transonic (M 0.9–1.1): linear blend between the two methods, matching the
	 * existing blending convention used elsewhere in this calculator.
	 * <p>
	 * Reference: Eckert, E.R.G. (1955). "Engineering relations for friction and
	 * heat transfer to surfaces in high velocity flow". J. Aeronautical Sciences, 22(8).
	 *
	 * @param configuration rocket configuration (for finish type)
	 * @param mach          freestream Mach number
	 * @param Re            freestream Reynolds number
	 * @param T_e           freestream static temperature (K)
	 * @return skin friction coefficient referenced to freestream dynamic pressure
	 */
	private double calculateFrictionCoefficient(FlightConfiguration configuration, double mach, double Re, double T_e) {
		boolean perfectFinish = configuration.getRocket().isPerfectFinish();
		double CfBase = incompressibleCf(Re, perfectFinish);
		double CfSubsonic = CfBase * subsonicCfCorrection(mach, Re, perfectFinish);

		if (mach <= 0.9) {
			return CfSubsonic;
		}

		// Eckert reference temperature method
		double T_star = calculateReferenceTemperature(mach, T_e);
		double ReStar = calculateEckertReynolds(Re, T_e, T_star);
		double CfEckert = incompressibleCf(ReStar, perfectFinish) * (T_e / T_star);

		if (mach >= 1.1) {
			return CfEckert;
		}

		// Linear blend through transonic (M 0.9–1.1)
		double t = (mach - 0.9) / 0.2;
		return CfSubsonic * (1.0 - t) + CfEckert * t;
	}

	/**
	 * Incompressible skin friction coefficient from the Reynolds number.
	 * <p>
	 * For perfect (smooth) finish: uses laminar (Blasius) below Re = 5.39e5,
	 * turbulent (Schlichting) above. For rough finish: always turbulent.
	 */
	private static double incompressibleCf(double Re, boolean perfectFinish) {
		if (perfectFinish) {
			if (Re < 1.0e4) {
				return 1.33e-2;
			} else if (Re < 5.39e5) {
				return 1.328 / MathUtil.safeSqrt(Re);
			} else {
				return 1.0 / pow2(1.50 * Math.log(Re) - 5.6) - 1700 / Re;
			}
		} else {
			if (Re < 1.0e4) {
				return 1.48e-2;
			} else {
				return 1.0 / pow2(1.50 * Math.log(Re) - 5.6);
			}
		}
	}

	/**
	 * Subsonic compressibility correction factor for skin friction.
	 * <p>
	 * For perfect finish, the correction ramps in for Re between 1e6 and 3e6.
	 * For rough finish, no Re dependence.
	 */
	private static double subsonicCfCorrection(double mach, double Re, boolean perfectFinish) {
		if (perfectFinish) {
			if (Re > 1.0e6) {
				if (Re < 3.0e6) {
					return 1 - 0.1 * pow2(mach) * (Re - 1.0e6) / 2.0e6;
				} else {
					return 1 - 0.1 * pow2(mach);
				}
			}
			return 1.0;
		} else {
			return 1 - 0.1 * pow2(mach);
		}
	}

	/**
	 * Compute the Eckert reference temperature T*.
	 * <p>
	 * For an adiabatic wall (typical rocket in flight), the wall temperature
	 * equals the recovery temperature: T_w = T_e * (1 + r * (gamma-1)/2 * M²)
	 * where r is the turbulent recovery factor (Pr^{1/3}).
	 * <p>
	 * The reference temperature is then:
	 * T* = T_e * (1 + 0.032*M² + 0.58*(T_w/T_e - 1))
	 *
	 * @param mach freestream Mach number
	 * @param T_e  freestream static temperature (K)
	 * @return reference temperature T* (K)
	 */
	static double calculateReferenceTemperature(double mach, double T_e) {
		double M2 = mach * mach;
		double T_w = T_e * (1.0 + TURBULENT_RECOVERY_FACTOR
				* (AtmosphericConditions.GAMMA - 1.0) / 2.0 * M2);
		return T_e * (1.0 + 0.032 * M2 + 0.58 * (T_w / T_e - 1.0));
	}

	/**
	 * Compute the corrected Reynolds number at the Eckert reference temperature.
	 * <p>
	 * {@code Re_star = Re * (rho_star / rho_e) / (mu_star / mu_e)}
	 * <p>
	 * Density ratio from ideal gas at constant pressure: {@code rho_star/rho_e = T_e/T_star}.
	 * Viscosity ratio from Sutherland's law:
	 * {@code mu_star/mu_e = (T_star/T_e)^(3/2) * (T_e+S)/(T_star+S)}.
	 *
	 * @param Re     freestream Reynolds number
	 * @param T_e    freestream static temperature (K)
	 * @param T_star Eckert reference temperature (K)
	 * @return corrected Reynolds number
	 */
	static double calculateEckertReynolds(double Re, double T_e, double T_star) {
		double densityRatio = T_e / T_star;
		double tempRatio = T_star / T_e;
		double viscosityRatio = Math.pow(tempRatio, 1.5)
				* (T_e + S_SUTHERLAND) / (T_star + S_SUTHERLAND);
		return Re * densityRatio / viscosityRatio;
	}

	private double calculateRoughnessCorrection(double mach) {
		double roughnessCorrection;
		if (mach < 0.9) {
			roughnessCorrection = 1 - 0.1 * pow2(mach);
		} else if (mach > 1.1) {
			roughnessCorrection = 1 / (1 + 0.18 * pow2(mach));
		} else {
			double c1 = 1 - 0.1 * pow2(0.9);
			double c2 = 1.0 / (1 + 0.18 * pow2(1.1));
			roughnessCorrection = c2 * (mach - 0.9) / 0.2 + c1 * (1.1 - mach) / 0.2;
		}
		return roughnessCorrection;
	}

	private double calculatePressureCD(FlightConfiguration configuration, FlightConditions conditions,
			Map<RocketComponent, AerodynamicForces> forceMap, WarningSet warningSet) {
		ensureCalcMap(configuration);

		double stagnation = calculateStagnationCD(conditions.getMach());
		double base = calculateBaseCD(conditions.getMach());

		double total = 0;
		InstanceMap imap = configuration.getActiveInstances();
		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry : imap.entrySet()) {
			RocketComponent c = entry.getKey();

			if (!c.isAerodynamic()) {
				continue;
			}

			if (c.isCDOverridden() || c.isCDOverriddenByAncestor()) {
				continue;
			}

			int instanceCount = entry.getValue().size();

			double cd = calcMap.get(c).calculatePressureCD(conditions, stagnation, base, warningSet);

			if (forceMap != null && forceMap.get(c) != null) {
				forceMap.get(c).setPressureCD(cd);
			}

			total += cd * instanceCount;

			if (c instanceof SymmetricComponent) {
				SymmetricComponent s = (SymmetricComponent) c;
				double foreRadius = s.getForeRadius();
				double aftRadius = s.getAftRadius();
				if (s.getLength() == 0) {
					foreRadius = Math.max(foreRadius, aftRadius);
				}
				double radius = 0;
				SymmetricComponent prevComponent = s.getPreviousSymmetricComponent();
				if (prevComponent != null && configuration.isComponentActive(prevComponent)) {
					radius = prevComponent.getAftRadius();
				}

				if (radius < foreRadius) {
					double area = Math.PI * (pow2(foreRadius) - pow2(radius));
					double diskCd = stagnation * area / conditions.getRefArea();
					total += instanceCount * diskCd;

					if (forceMap != null && forceMap.get(c) != null) {
						forceMap.get(c).setPressureCD(forceMap.get(c).getPressureCD() + diskCd);
					}
				}
			}
		}

		return total;
	}

	private double calculateBaseCD(FlightConfiguration configuration, FlightConditions conditions,
			Map<RocketComponent, AerodynamicForces> forceMap, WarningSet warningSet) {
		ensureCalcMap(configuration);

		double base = calculateBaseCD(conditions.getMach());
		double total = 0;

		InstanceMap imap = configuration.getActiveInstances();
		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry : imap.entrySet()) {
			RocketComponent c = entry.getKey();

			if (c.isCDOverridden() || c.isCDOverriddenByAncestor()) {
				continue;
			}

			int instanceCount = entry.getValue().size();

			// Base drag for symmetric components (body tubes, nose cones, transitions)
			if (c instanceof SymmetricComponent) {
				SymmetricComponent s = (SymmetricComponent) c;
				double foreRadius = s.getForeRadius();
				double aftRadius = s.getAftRadius();
				if (s.getLength() == 0) {
					double componentMaxR = Math.max(foreRadius, aftRadius);
					foreRadius = componentMaxR;
					aftRadius = componentMaxR;
				}

				SymmetricComponent nextComponent = s.getNextSymmetricComponent();
				double nextRadius;
				if ((nextComponent != null) && configuration.isComponentActive(nextComponent)) {
					nextRadius = nextComponent.getForeRadius();
				} else {
					nextRadius = 0.0;
				}

				if (nextRadius < aftRadius) {
					double area = Math.PI * (pow2(aftRadius) - pow2(nextRadius));

					// Apply boattail correction: when the component tapers down
					// (aftRadius < foreRadius), the converging flow reduces base drag
					// beyond what the smaller area alone provides.
					double correctedBase = base;
					if (aftRadius < foreRadius && s.getLength() > 0) {
						correctedBase *= calculateBoattailFactor(
								foreRadius, aftRadius, s.getLength(), conditions.getMach());
					}

					double cd = correctedBase * area / conditions.getRefArea();
					total += instanceCount * cd;
					if (forceMap != null && forceMap.get(s) != null) {
						forceMap.get(s).setBaseCD(cd);
					}
				}
			} else if (c.isAerodynamic()) {
				// Base drag for non-symmetric components (fins, etc.)
				double cd = calcMap.get(c).calculateComponentBaseCD(conditions, base, warningSet);
				if (cd > 0) {
					total += cd * instanceCount;
					if (forceMap != null && forceMap.get(c) != null) {
						forceMap.get(c).setBaseCD(cd);
					}
				}
			}
		}

		return total;
	}

	private double calculateOverrideCD(FlightConfiguration configuration,
			Map<RocketComponent, AerodynamicForces> componentForces,
			Map<RocketComponent, AerodynamicForces> assemblyForces) {
		ensureCalcMap(configuration);

		double total = 0;
		InstanceMap imap = configuration.getActiveInstances();
		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry : imap.entrySet()) {
			RocketComponent c = entry.getKey();
			int instanceCount = entry.getValue().size();

			if (!c.isAerodynamic() && !(c instanceof ComponentAssembly)) {
				continue;
			}

			if (c.isCDOverridden() && !c.isCDOverriddenByAncestor()) {
				double cd = instanceCount * c.getOverrideCD();
				Map<RocketComponent, AerodynamicForces> targetMap = (c instanceof ComponentAssembly) ? assemblyForces
						: componentForces;
				if (targetMap != null && targetMap.get(c) != null) {
					targetMap.get(c).setOverrideCD(cd);
				}
				total += cd;
			}
		}

		return total;
	}

	private double calculateAxialCD(FlightConditions conditions, double cd) {
		double aoa = MathUtil.clamp(conditions.getAOA(), 0, Math.PI);
		double mul;

		if (aoa > Math.PI / 2) {
			aoa = Math.PI - aoa;
		}
		if (aoa < 17 * Math.PI / 180) {
			mul = PolyInterpolator.eval(aoa, axialDragPoly1);
		} else {
			mul = PolyInterpolator.eval(aoa, axialDragPoly2);
		}

		if (conditions.getAOA() < Math.PI / 2) {
			return mul * cd;
		}
		return -mul * cd;
	}

	public static double calculateStagnationCD(double m) {
		double pressure;
		if (m <= 1) {
			pressure = 1 + pow2(m) / 4 + pow2(pow2(m)) / 40;
		} else {
			pressure = 1.84 - 0.76 / pow2(m) + 0.166 / pow2(pow2(m)) + 0.035 / pow2(m * m * m);
		}
		return 0.85 * pressure;
	}

	/**
	 * Calculates the base drag coefficient for a cylindrical afterbody.
	 * <p>
	 * Uses the subsonic correlation (0.12 + 0.13*M²) below the transonic region,
	 * the Devan-Ashwood supersonic correlation (0.064 + 0.186/M²) above it, and a
	 * C1-continuous polynomial blend through the transonic region (M 0.85–1.3) with
	 * a peak near M=1.05.
	 * <p>
	 * The Devan-Ashwood model correctly asymptotes to a nonzero constant at high
	 * Mach, matching experimental data for turbulent cylindrical afterbodies.
	 * <p>
	 * The returned coefficient is referenced to the base area and must be scaled
	 * by (base area / reference area) for each component.
	 * <p>
	 * References: Devan & Ashwood (1961) NASA TN D-721; Hoerner "Fluid-Dynamic
	 * Drag" (1965) Ch. 3; USAF DATCOM Section 4.6.3.2.
	 *
	 * @param m Mach number
	 * @return base drag coefficient (referenced to base area)
	 */
	public static double calculateBaseCD(double m) {
		if (m <= BASE_BLEND_LOW) {
			return 0.12 + 0.13 * m * m;
		}
		if (m >= BASE_BLEND_HIGH) {
			return BASE_DRAG_A + BASE_DRAG_B / (m * m);
		}
		return PolyInterpolator.eval(m, baseDragTransonicPoly);
	}

	/**
	 * Calculates the boattail correction factor for base drag.
	 * <p>
	 * When a component tapers to a smaller aft radius (boattail), the converging
	 * flow creates a narrower wake and higher base pressure compared to a
	 * cylindrical afterbody. This reduces the base drag coefficient beyond
	 * what the smaller base area alone provides.
	 * <p>
	 * For moderate boattail angles (&lt; 12°), the full benefit applies. For steep
	 * angles (&gt; 20°), flow separation on the boattail surface eliminates the
	 * benefit. At supersonic speeds, expansion fan effects at the boattail corner
	 * enhance the base drag reduction.
	 * <p>
	 * Based on Hoerner "Fluid-Dynamic Drag" (1965) Ch. 16 and USAF DATCOM
	 * Section 4.6.3.2.
	 *
	 * @param foreRadius forward radius of the tapering component
	 * @param aftRadius  aft radius of the tapering component (must be &lt; foreRadius)
	 * @param length     axial length of the tapering component
	 * @param mach       freestream Mach number
	 * @return correction factor in [0.3, 1.0] to multiply base drag coefficient
	 */
	static double calculateBoattailFactor(double foreRadius, double aftRadius,
										  double length, double mach) {
		if (aftRadius >= foreRadius || length <= 0) {
			return 1.0;
		}

		double dRatio = aftRadius / foreRadius;
		double boattailAngle = Math.atan2(foreRadius - aftRadius, length);

		// For moderate boattail angles (< 12°), full benefit applies.
		// For steep angles (> 20°), flow separation eliminates the benefit.
		double angleFactor;
		double angle12 = Math.toRadians(12);
		double angle20 = Math.toRadians(20);
		if (boattailAngle <= angle12) {
			angleFactor = 1.0;
		} else if (boattailAngle < angle20) {
			angleFactor = (angle20 - boattailAngle) / (angle20 - angle12);
		} else {
			angleFactor = 0.0;
		}

		// Base drag reduction coefficient increases with Mach number
		// due to expansion fan effects at supersonic speeds.
		double reductionCoeff;
		if (mach <= 1.0) {
			reductionCoeff = 0.25;
		} else {
			reductionCoeff = 0.25 + 0.15 * Math.min(mach - 1.0, 1.0);
		}

		double factor = 1.0 - angleFactor * reductionCoeff * (1.0 - dRatio);
		return MathUtil.clamp(factor, 0.3, 1.0);
	}

	private void ensureCalcMap(FlightConfiguration configuration) {
		if (calcMap == null) {
			buildCalcMap(configuration);
		}
	}

	private void buildCalcMap(FlightConfiguration configuration) {
		calcMap = new HashMap<>();

		for (RocketComponent comp : configuration.getAllComponents()) {
			if (!comp.isAerodynamic() && !(comp instanceof ComponentAssembly)) {
				continue;
			}

			RocketComponentCalc calcObj = (RocketComponentCalc) Reflection.construct(
					BARROWMAN_PACKAGE,
					comp,
					BARROWMAN_SUFFIX,
					comp);

			calcMap.put(comp, calcObj);
		}
	}
}

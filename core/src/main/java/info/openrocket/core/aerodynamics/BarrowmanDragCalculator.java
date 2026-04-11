package info.openrocket.core.aerodynamics;

import static info.openrocket.core.util.MathUtil.pow2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import info.openrocket.core.aerodynamics.barrowman.RocketComponentCalc;
import info.openrocket.core.aerodynamics.barrowman.SymmetricComponentCalc;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.ComponentAssembly;
import info.openrocket.core.rocketcomponent.ExternalComponent;
import info.openrocket.core.rocketcomponent.ExternalComponent.Finish;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.InstanceContext;
import info.openrocket.core.rocketcomponent.InstanceMap;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.util.CoordinateIF;
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

	/**
	 * Crossflow drag coefficient for flat-plate fins at high angle of attack.
	 * From BasicTumbleStepper / techdoc.pdf, validated empirically.
	 */
	private static final double CROSSFLOW_FIN_CD = 1.42;

	/**
	 * Fin efficiency factors for crossflow drag, indexed by fin count.
	 * Accounts for fin-fin shadowing when multiple fins are present.
	 * From BasicTumbleStepper / techdoc.pdf.
	 */
	private static final double[] FIN_CROSSFLOW_EFF = { 0.0, 0.5, 1.0, 1.41, 1.81, 1.73, 1.90, 1.85 };

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

		// Initialize per-component drag fields to 0 so components that don't
		// contribute to a given term (e.g. a nose cone has no base drag because
		// its aft radius matches the next body tube) don't leave the field at
		// its default NaN. Without this, getForceAnalysis()'s sanitize loop
		// spuriously fires "Non-finite aero coefficients" warnings for every
		// component that doesn't happen to receive a write from the loops below.
		if (componentForces != null) {
			for (AerodynamicForces f : componentForces.values()) {
				if (!Double.isFinite(f.getFrictionCD())) f.setFrictionCD(0);
				if (!Double.isFinite(f.getPressureCD())) f.setPressureCD(0);
				if (!Double.isFinite(f.getBaseCD())) f.setBaseCD(0);
				if (!Double.isFinite(f.getOverrideCD())) f.setOverrideCD(0);
			}
		}

		double frictionCD = calculateFrictionCD(configuration, conditions, componentForces, actualWarnings);
		double pressureCD = calculatePressureCD(configuration, conditions, componentForces, actualWarnings);
		double baseCD = calculateBaseCD(configuration, conditions, componentForces, actualWarnings);
		double overrideCD = calculateOverrideCD(configuration, componentForces, assemblyForces);

		totalForces.setFrictionCD(frictionCD);
		totalForces.setPressureCD(pressureCD);
		totalForces.setBaseCD(baseCD);
		totalForces.setOverrideCD(overrideCD);

		// Phase 6i: Lift-induced drag — axial projection of normal force at angle of attack
		// CDi = CN * sin(alpha). At alpha = 0, CDi = 0 (no change to zero-AoA results)
		double alpha = conditions.getAOA();
		double CN = totalForces.getCN();
		double CDi = 0.0;
		if (Math.abs(alpha) > 1e-6 && !Double.isNaN(CN)) {
			CDi = CN * Math.sin(alpha);
			if (CDi < 0) CDi = 0;  // induced drag is always non-negative
		}

		totalForces.setCD(frictionCD + pressureCD + baseCD + overrideCD + CDi);
		totalForces.setCDaxial(calculateAxialCD(conditions, totalForces.getCD()));

		// Crossflow drag at high AoA: when the rocket is tumbling or at large
		// angle of attack, the side profile acts as a bluff body. The Barrowman
		// stability calculator underestimates the normal force at post-stall
		// angles (fin CN capped at 20°, body CN linearized). This adds the
		// crossflow drag as a normal force so the RK4 stepper properly
		// decelerates the rocket through force resolution.
		double crossflowCN = computeCrossflowCN(configuration, conditions);
		double existingCN = totalForces.getCN();
		if (crossflowCN > Math.abs(existingCN)) {
			double newCN = existingCN >= 0 ? crossflowCN : -crossflowCN;
			// Scale Cm proportionally so the effective CP stays at the same
			// location.  Without this, replacing a small Barrowman CN with a
			// large crossflow CN while keeping the old Cm creates an artificial
			// destabilizing moment (Cm_CG = Cm - CN*xCG/refLen) that drives
			// rotational divergence.
			// When existingCN is too small, CP is ill-defined — zero the moment
			// since crossflow drag at extreme AoA acts roughly through the
			// planform centroid (near CG for typical rockets).
			if (Math.abs(existingCN) > 0.5) {
				double scale = Math.min(Math.abs(newCN / existingCN), 20.0);
				totalForces.setCm(totalForces.getCm() * Math.copySign(scale, newCN / existingCN));
			} else {
				totalForces.setCm(0);
			}
			totalForces.setCN(newCN);
		}
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

			AerodynamicForces f = forceMap != null ? forceMap.get(c) : null;
			if (f != null) {
				f.setFrictionCD(componentFrictionCD);
			}
		}

		if (maxR < 1e-9) maxR = 1e-9;
		// Hoerner streamlined body form factor for bodies of revolution.
		// Reference: Hoerner "Fluid Dynamic Drag" (1965), Ch. 6 Eq. 6-21
		// FF = 1 + 1.5/(L/d)^1.5 + 7/(L/d)^3
		// This accounts for pressure gradient effects on the boundary layer
		// that increase friction drag beyond flat-plate values.
		// Note: Hoerner also has a non-streamlined formula (1 + 60/f^3 + 0.0025*f)
		// which gives higher corrections, but it over-corrects for supersonic rockets
		// where the Eckert reference temperature method already accounts for
		// compressibility effects on friction. The streamlined formula is more
		// appropriate for the rocket geometries we're modeling.
		double totalLength = maxX - minX + 0.0001;
		double ld = totalLength / (2.0 * maxR); // fineness ratio L/d
		double correction = 1.0 + 1.5 / Math.pow(ld, 1.5) + 7.0 / Math.pow(ld, 3.0);

		// Phase 8c: Boundary layer transition correction
		double velocity = conditions.getVelocity();
		double kinematicViscosity = conditions.getAtmosphericConditions().getKinematicViscosity();
		double fLam = laminarFraction(mach, totalLength, velocity, kinematicViscosity);
		// Real painted HPR airframes (paint, couplers, fin fillets, launch lugs) trip
		// the boundary layer within inches. Only "perfect finish" rockets can sustain
		// extended laminar flow; otherwise cap the laminar fraction to a small value.
		// Without this cap the Michel-criterion Re_tr = 3e6 produces ~17% friction
		// haircut on typical HPR airframes at low Mach — a systematic subsonic drag
		// deficit visible in the SimVReal benchmark overshoot cluster.
		if (!configuration.getRocket().isPerfectFinish()) {
			fLam = Math.min(fLam, 0.05);
		}
		double transitionFactor = 1.0 - 0.6 * fLam;

		if (forceMap != null) {
			for (Map.Entry<RocketComponent, AerodynamicForces> entry : forceMap.entrySet()) {
				if (entry.getKey() instanceof SymmetricComponent) {
					entry.getValue().setFrictionCD(entry.getValue().getFrictionCD() * correction * transitionFactor);
				} else {
					entry.getValue().setFrictionCD(entry.getValue().getFrictionCD() * transitionFactor);
				}
			}
		}

		return (otherFrictionCD + correction * bodyFrictionCD) * transitionFactor;
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

			AerodynamicForces f = forceMap != null ? forceMap.get(c) : null;
			if (f != null) {
				f.setPressureCD(cd);
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

					AerodynamicForces f2 = forceMap != null ? forceMap.get(c) : null;
					if (f2 != null) {
						f2.setPressureCD(f2.getPressureCD() + diskCd);
					}
				}
			}
		}

		return total;
	}

	/**
	 * Find the largest fore radius of any active, coaxial SymmetricComponent whose
	 * fore face abuts the aft face of {@code s}. Used to patch up base-drag
	 * accounting for fin cans or boat-tails hosted in coaxial PodSets that
	 * {@link SymmetricComponent#getNextSymmetricComponent()} does not detect.
	 *
	 * <p>Handles the <b>abutting topology</b> (e.g. Qu8k, IonDrive) where the
	 * downstream component's fore face sits exactly at {@code s}'s aft face.
	 *
	 * <p>Note: the <b>sleeve topology</b> (e.g. DontDebate fin can) where a larger-OD
	 * component overlaps the aft section of {@code s} is intentionally deferred until
	 * fin-can step drag is also modelled, to avoid a net drag-accounting regression
	 * from fixing only the base-drag double-count.
	 *
	 * @return the abutting fore radius, or 0 if nothing abuts.
	 */
	private double findAbuttingDownstreamRadius(SymmetricComponent s,
			ArrayList<InstanceContext> sContexts, InstanceMap imap,
			FlightConfiguration configuration) {
		if (sContexts == null || sContexts.isEmpty()) return 0;

		// Use the first instance of s to locate its aft face.
		InstanceContext sCtx = sContexts.get(0);
		CoordinateIF sLoc = sCtx.getLocation();
		double sAftX = sLoc.getX() + s.getLength();
		double sY = sLoc.getY();
		double sZ = sLoc.getZ();

		final double X_TOL = 1.0e-4;      // 0.1 mm abutment tolerance
		final double AXIS_TOL = 1.0e-4;   // 0.1 mm coaxial tolerance

		double bestRadius = 0;
		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> e2 : imap.entrySet()) {
			RocketComponent c2 = e2.getKey();
			if (c2 == s || !(c2 instanceof SymmetricComponent)) continue;
			if (!configuration.isComponentActive(c2)) continue;

			SymmetricComponent other = (SymmetricComponent) c2;
			for (InstanceContext ctx : e2.getValue()) {
				CoordinateIF loc = ctx.getLocation();
				// Coaxial with s?
				if (Math.abs(loc.getY() - sY) > AXIS_TOL
						|| Math.abs(loc.getZ() - sZ) > AXIS_TOL) {
					continue;
				}
				// Fore face at our aft face?
				if (Math.abs(loc.getX() - sAftX) > X_TOL) {
					continue;
				}
				double r = other.getForeRadius();
				if (r > bestRadius) {
					bestRadius = r;
				}
			}
		}
		return bestRadius;
	}

	private double calculateBaseCD(FlightConfiguration configuration, FlightConditions conditions,
			Map<RocketComponent, AerodynamicForces> forceMap, WarningSet warningSet) {
		ensureCalcMap(configuration);

		double mach = conditions.getMach();
		double base = calculateBaseCD(mach, conditions);
		double total = 0;

		// Apply power-on base drag reduction during motor burn.
		// The exhaust plume fills part of the base region, reducing base pressure drag.
		double powerOnMultiplier = computePowerOnBaseDragMultiplier(conditions);
		base *= powerOnMultiplier;

		// TODO Phase 6b: Apply power-on base drag correction when thrust status is available.
		// Currently the drag calculator does not receive real-time thrust information.
		// When thrust plumbing is added (e.g., via FlightConditions or a separate parameter),
		// multiply the aft-most component's base CD by powerOnBaseDragFactor(areaRatio)
		// during powered flight. Typical HPR motors have areaRatio ~ 0.3, giving k ~ 0.4.

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

				// SymmetricComponent.getNextSymmetricComponent() does not traverse into
				// PodSets whose children abut this component at its aft face (only
				// flush-with-front pods are detected). Rockets that place the fin can
				// or boat-tail in an aft-abutting coaxial PodSet (e.g. DontDebate,
				// Qu8k) therefore end up charging a full phantom base on the parent
				// body tube AND another base on the pod-hosted tube — roughly doubling
				// the supersonic base drag. Patch that up here by searching the active
				// instance map for any coaxial SymmetricComponent whose fore face sits
				// at this component's aft X.
				if (nextRadius < aftRadius) {
					double abuttingRadius = findAbuttingDownstreamRadius(
							s, entry.getValue(), imap, configuration);
					if (abuttingRadius > nextRadius) {
						nextRadius = abuttingRadius;
					}
				}

				if (nextRadius < aftRadius) {
					double area = Math.PI * (pow2(aftRadius) - pow2(nextRadius));

					// Apply boattail correction: when the component tapers down
					// (aftRadius < foreRadius), the converging flow reduces base drag
					// beyond what the smaller area alone provides.
					double correctedBase = base;
					if (aftRadius < foreRadius && s.getLength() > 0) {
						correctedBase *= calculateBoattailFactor(
								foreRadius, aftRadius, s.getLength(), mach);
					}

					// Viswanath (1996) boattail correction: a preceding boattail (transition
					// with aftRadius < foreRadius) reduces base drag by energizing the wake.
					correctedBase *= calculateViswanathBoattailFactor(s, mach);

					double cd = correctedBase * area / conditions.getRefArea();
					total += instanceCount * cd;
					AerodynamicForces f = forceMap != null ? forceMap.get(s) : null;
					if (f != null) {
						f.setBaseCD(cd);
					}
				}
			} else if (c.isAerodynamic()) {
				// Base drag for non-symmetric components (fins, etc.)
				double cd = calcMap.get(c).calculateComponentBaseCD(conditions, base, warningSet);
				if (cd > 0) {
					total += cd * instanceCount;
					AerodynamicForces f = forceMap != null ? forceMap.get(c) : null;
					if (f != null) {
						f.setBaseCD(cd);
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
				AerodynamicForces f = targetMap != null ? targetMap.get(c) : null;
				if (f != null) {
					f.setOverrideCD(cd);
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

	/**
	 * Compute the crossflow normal force coefficient for the full rocket at the
	 * current angle of attack. This models the bluff-body drag that arises when
	 * the rocket's side profile is exposed to the airflow at high AoA.
	 * <p>
	 * Uses the same drag coefficients as {@code BasicTumbleStepper} (1.42 for fins,
	 * Jorgensen Cd_c for body tubes) applied to component planform areas, scaled
	 * by sin²(alpha) to account for the crossflow velocity component.
	 * <p>
	 * At low AoA this value is small and the existing Barrowman CN dominates.
	 * At high AoA (post-stall / tumbling) this value exceeds the Barrowman CN
	 * and provides the dominant deceleration force through the normal force
	 * channel in the RK4 stepper.
	 *
	 * @param configuration current flight configuration
	 * @param conditions    current flight conditions (Mach, AoA, etc.)
	 * @return crossflow normal force coefficient (always non-negative)
	 */
	private double computeCrossflowCN(FlightConfiguration configuration, FlightConditions conditions) {
		double alpha = conditions.getAOA();
		double sinAlpha = Math.sin(alpha);
		if (Math.abs(sinAlpha) < 1e-6) {
			return 0;
		}

		double crossflowMach = conditions.getMach() * Math.abs(sinAlpha);
		double bodyCd = SymmetricComponentCalc.getCrossflowDragCoefficient(crossflowMach);

		double finCDArea = 0;
		double bodyCDArea = 0;

		InstanceMap imap = configuration.getActiveInstances();
		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry : imap.entrySet()) {
			RocketComponent c = entry.getKey();
			if (!c.isAerodynamic()) {
				continue;
			}

			if (c instanceof FinSet) {
				FinSet fin = (FinSet) c;
				double planform = fin.getPlanformArea();
				int count = fin.getFinCount();
				if (count >= FIN_CROSSFLOW_EFF.length) {
					count = FIN_CROSSFLOW_EFF.length - 1;
				}
				finCDArea += CROSSFLOW_FIN_CD * planform * FIN_CROSSFLOW_EFF[count] / fin.getFinCount();
			} else if (c instanceof SymmetricComponent) {
				bodyCDArea += bodyCd * ((SymmetricComponent) c).getComponentPlanformArea();
			}
		}

		double refArea = conditions.getRefArea();
		if (refArea < 1e-9) {
			return 0;
		}
		return (finCDArea + bodyCDArea) / refArea * sinAlpha * sinAlpha;
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
	 * Calculate base drag coefficient with Lamb-Oberkampf (1995) Reynolds number correction.
	 * At supersonic speeds (M > 1.3), higher Reynolds numbers produce a more energetic
	 * wake, reducing base drag. Falls back to Devan-Ashwood when Re_D < 1e4.
	 *
	 * @param m Mach number
	 * @param conditions flight conditions (for velocity, density, viscosity)
	 * @return base drag coefficient with Re correction
	 */
	public static double calculateBaseCD(double m, FlightConditions conditions) {
		double baseCd = calculateBaseCD(m);

		if (m <= 1.3 || conditions == null) {
			return baseCd;
		}

		// Compute base Reynolds number using reference length as diameter proxy
		double velocity = conditions.getVelocity();
		double kinematicViscosity = conditions.getAtmosphericConditions().getKinematicViscosity();
		if (kinematicViscosity < 1e-10 || velocity < 1e-3) {
			return baseCd;
		}

		double refLength = conditions.getRefLength();
		double reD = velocity * refLength / kinematicViscosity;

		if (reD < 1e4) {
			return baseCd;
		}

		double logReD = Math.log10(reD);
		// Lamb-Oberkampf Re correction: high Re -> lower base drag (more energetic wake)
		double reFactor = MathUtil.clamp(1.0 - 0.08 * (logReD - 6.0), 0.7, 1.3);

		return baseCd * reFactor;
	}

	/**
	 * Calculate the Viswanath (1996) boattail correction factor for base drag.
	 * A boattail (transition with aftRadius < foreRadius) upstream of the base
	 * energizes the wake and reduces base drag. The correction factor eta_bt
	 * is in [0, 1] and multiplies the base drag coefficient.
	 *
	 * @param s the symmetric component at whose aft end base drag acts
	 * @param mach freestream Mach number
	 * @return boattail factor in (0, 1]; 1.0 means no correction
	 */
	static double calculateViswanathBoattailFactor(SymmetricComponent s, double mach) {
		SymmetricComponent boattail = null;
		if (s instanceof Transition && s.getAftRadius() < s.getForeRadius() && s.getLength() > 0) {
			boattail = s;
		} else {
			SymmetricComponent prev = s.getPreviousSymmetricComponent();
			if (prev instanceof Transition && prev.getAftRadius() < prev.getForeRadius() && prev.getLength() > 0) {
				boattail = prev;
			}
		}

		if (boattail == null) {
			return 1.0;
		}

		double deltaR = boattail.getForeRadius() - boattail.getAftRadius();
		double thetaBt = Math.toDegrees(Math.atan2(deltaR, boattail.getLength()));

		double etaBt;
		if (thetaBt < 6.0) {
			etaBt = 0.25 + 0.05 * thetaBt;
		} else if (thetaBt < 16.0) {
			double etaGeom = 0.55 + 0.04 * (thetaBt - 6.0);
			double machFactor = 1.0 + 0.1 * Math.max(0, mach - 1.0);
			etaBt = Math.min(etaGeom * machFactor, 0.95);
		} else {
			etaBt = Math.max(0.0, 0.95 - 0.05 * (thetaBt - 16.0));
		}

		return MathUtil.clamp(etaBt, 0.0, 1.0);
	}

	/**
	 * Computes the power-on base drag reduction factor.
	 * <p>
	 * During motor burn, the exhaust plume partially fills the base region,
	 * reducing the base drag. The reduction depends on the nozzle exit area
	 * to base area ratio (AR).
	 * <p>
	 * Reference: NASA SP-8055 "Solid Rocket Motor Nozzles" and
	 * Hoerner "Fluid-Dynamic Drag" Ch. 3.
	 *
	 * @param areaRatio nozzle exit area / base area, clamped to [0, 1]
	 * @return base drag reduction factor in [0, 1]
	 */
	public static double powerOnBaseDragFactorDetailed(double areaRatio) {
		double ar = MathUtil.clamp(areaRatio, 0.0, 1.0);

		if (ar >= 0.8) {
			return 0.0;
		} else if (ar >= 0.4) {
			return 0.2 * (0.8 - ar) / 0.4;
		} else if (ar >= 0.1) {
			return 0.2 + 0.6 * (0.4 - ar) / 0.3;
		} else {
			return 0.8 + 0.2 * (0.1 - ar) / 0.1;
		}
	}

	/** Default power-on base drag reduction factor when nozzle geometry is unavailable. */
	static final double DEFAULT_POWER_ON_FACTOR = 0.15;

	private static double smoothstep(double t) {
		t = MathUtil.clamp(t, 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	/**
	 * Compute the effective base drag multiplier accounting for power-on state.
	 * <p>
	 * When unpowered (thrustLevel = 0), returns 1.0 (no reduction).
	 * When powered, returns a factor in [0, 1] that reduces base drag.
	 *
	 * @param conditions flight conditions with thrust level and nozzle area ratio
	 * @return base drag multiplier in [0, 1]
	 */
	static double computePowerOnBaseDragMultiplier(FlightConditions conditions) {
		double thrustLevel = conditions.getThrustLevel();
		if (thrustLevel <= 0) {
			return 1.0;
		}

		double kBase;
		double nozzleAR = conditions.getNozzleAreaRatio();
		if (Double.isNaN(nozzleAR)) {
			kBase = DEFAULT_POWER_ON_FACTOR;
		} else {
			kBase = powerOnBaseDragFactorDetailed(nozzleAR);
		}

		double s = smoothstep(thrustLevel);
		return 1.0 - s * (1.0 - kBase);
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

	/**
	 * Phase 6b: Compute base drag reduction factor during motor burn.
	 * Based on Brazzel et al. (1962) and Dempsey (1976) simplified model.
	 *
	 * When the motor is firing, the exhaust plume fills the base region,
	 * raising base pressure and significantly reducing base drag.
	 *
	 * @param areaRatio nozzle exit area / base area (A_e / A_b), must be >= 0
	 * @return reduction factor k_base in [0, 1] where 0 = no base drag, 1 = full base drag
	 */
	public static double powerOnBaseDragFactor(double areaRatio) {
		if (areaRatio >= 0.8) return 0.0;
		if (areaRatio >= 0.4) return 0.2 * (0.8 - areaRatio) / 0.4;
		if (areaRatio >= 0.1) return 0.2 + 0.6 * (0.4 - areaRatio) / 0.3;
		return 0.8 + 0.2 * (0.1 - areaRatio) / 0.1;
	}

	private void ensureCalcMap(FlightConfiguration configuration) {
		if (calcMap == null) {
			buildCalcMap(configuration);
		}
	}

	/**
	 * Phase 8c: Compute boundary layer transition Reynolds number.
	 * Michel criterion with compressibility correction.
	 */
	public static double transitionReynoldsNumber(double mach) {
		return 3.0e6 / (1.0 + 0.045 * mach * mach);
	}

	/**
	 * Phase 8c: Compute laminar fraction of total wetted length.
	 */
	public static double laminarFraction(double mach, double totalLength,
										  double velocity, double kinematicViscosity) {
		if (totalLength <= 0 || velocity <= 0 || kinematicViscosity <= 0) return 0;
		double Re_tr = transitionReynoldsNumber(mach);
		double x_tr = Re_tr * kinematicViscosity / velocity;
		return Math.min(x_tr / totalLength, 1.0);
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

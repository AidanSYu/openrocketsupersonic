package info.openrocket.core.aerodynamics;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.openrocket.core.logging.Warning;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.ComponentAssembly;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.InstanceMap;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.ModID;

/**
 * An aerodynamic calculator that uses the extended Barrowman method by delegating
 * stability and drag calculations to dedicated calculators.
 */
public class BarrowmanCalculator extends AbstractAerodynamicCalculator {
	private static final Logger logger = LoggerFactory.getLogger(BarrowmanCalculator.class);

	private final StabilityCalculator stabilityCalculator;
	private final DragCalculator dragCalculator;
	private int nanWarningCount = 0;

	public BarrowmanCalculator() {
		this(new BarrowmanStabilityCalculator(), new BarrowmanDragCalculator());
	}

	public BarrowmanCalculator(StabilityCalculator stabilityCalculator, DragCalculator dragCalculator) {
		if (stabilityCalculator == null || dragCalculator == null) {
			throw new IllegalArgumentException("Calculators must not be null");
		}
		this.stabilityCalculator = stabilityCalculator;
		this.dragCalculator = dragCalculator;
	}

	@Override
	public BarrowmanCalculator newInstance() {
		return new BarrowmanCalculator(stabilityCalculator.newInstance(), dragCalculator.newInstance());
	}

	@Override
	public double getStallAngle() {
		return stabilityCalculator.getStallAngle();
	}

	@Override
	public CoordinateIF getCP(FlightConfiguration configuration, FlightConditions conditions,
							  WarningSet warnings) {
		checkCache(configuration);
		WarningSet actualWarnings = (warnings != null) ? warnings : ignoreWarningSet;
		return stabilityCalculator.getCP(configuration, conditions, actualWarnings);
	}

	@Override
	public Map<RocketComponent, AerodynamicForces> getForceAnalysis(FlightConfiguration configuration,
			FlightConditions conditions, WarningSet warnings) {
		checkCache(configuration);

		WarningSet actualWarnings = (warnings != null) ? warnings : ignoreWarningSet;

		// Compute shock geometry once per aero calculation (Phase 3b)
		ShockGeometry shockGeometry = ShockGeometry.compute(configuration, conditions);
		if (stabilityCalculator instanceof BarrowmanStabilityCalculator) {
			((BarrowmanStabilityCalculator) stabilityCalculator).setShockGeometry(shockGeometry);
		}

		StabilityForceBreakdown breakdown = stabilityCalculator.getForceAnalysis(configuration, conditions,
				actualWarnings);

		Map<RocketComponent, AerodynamicForces> eachMap = breakdown.getComponentForces();
		Map<RocketComponent, AerodynamicForces> assemblyMap = breakdown.getAssemblyForces();

		AerodynamicForces rocketForces = assemblyMap.get(configuration.getRocket());
		dragCalculator.calculateDrag(configuration, conditions, eachMap, assemblyMap, rocketForces, actualWarnings);

		InstanceMap instMap = configuration.getActiveInstances();
		Map<RocketComponent, AerodynamicForces> finalMap = new LinkedHashMap<>();
		for (RocketComponent comp : instMap.keySet()) {
			AerodynamicForces forces;
			if (comp instanceof ComponentAssembly) {
				forces = assemblyMap.get(comp);
			} else if (comp.isAerodynamic()) {
				forces = eachMap.get(comp);
			} else {
				continue;
			}

			// ComponentAssembly entries (PodSet, stages) get their CN/CM from the
			// stability pass but never have drag fields written by the drag calc,
			// so baseCD/pressureCD/frictionCD default to NaN. Zero them silently
			// instead of logging — real NaN on an aerodynamic component should
			// stand out, not be drowned out by a per-assembly false positive.
			boolean isAssembly = comp instanceof ComponentAssembly;

			boolean hasNaN = false;
			if (forces.getCP().isNaN()) {
				forces.setCP(Coordinate.ZERO);
				if (!isAssembly) hasNaN = true;
			}
			if (!Double.isFinite(forces.getBaseCD())) {
				forces.setBaseCD(0);
				if (!isAssembly) hasNaN = true;
			}
			if (!Double.isFinite(forces.getPressureCD())) {
				forces.setPressureCD(0);
				if (!isAssembly) hasNaN = true;
			}
			if (!Double.isFinite(forces.getFrictionCD())) {
				forces.setFrictionCD(0);
				if (!isAssembly) hasNaN = true;
			}
			if (!Double.isFinite(forces.getOverrideCD())) {
				forces.setOverrideCD(0);
				if (!isAssembly) hasNaN = true;
			}
			if (hasNaN && nanWarningCount < 5) {
				logger.warn("Non-finite aero coefficients for component {} (further warnings suppressed)", comp);
				nanWarningCount++;
			}

			double cd = forces.getBaseCD() + forces.getPressureCD() + forces.getFrictionCD() + forces.getOverrideCD();
			forces.setCD(cd);
			forces.setCDaxial(dragCalculator.toAxialDrag(conditions, cd));

			finalMap.put(comp, forces);
		}

		return finalMap;
	}

	@Override
	public AerodynamicForces getAerodynamicForces(FlightConfiguration configuration,
			FlightConditions conditions, WarningSet warnings) {
		checkCache(configuration);

		WarningSet actualWarnings = (warnings != null) ? warnings : ignoreWarningSet;

		// Compute shock geometry once per aero calculation (Phase 3b)
		ShockGeometry shockGeometry = ShockGeometry.compute(configuration, conditions);
		if (stabilityCalculator instanceof BarrowmanStabilityCalculator) {
			((BarrowmanStabilityCalculator) stabilityCalculator).setShockGeometry(shockGeometry);
		}

		AerodynamicForces total = stabilityCalculator.calculateNonAxialForces(configuration, conditions, actualWarnings);

		dragCalculator.calculateDrag(configuration, conditions, null, null, total, actualWarnings);

		// Sanitize aero coefficients: catch NaN, Infinity, and extreme values
		// that would cause the RK4 stepper to diverge. These can arise from
		// transonic singularities (e.g. division by beta near M=1).
		sanitizeForces(total, conditions, actualWarnings);

		stabilityCalculator.calculateDampingMoments(configuration, conditions, total);
		total.setCm(total.getCm() - total.getPitchDampingMoment());
		total.setCyaw(total.getCyaw() - total.getYawDampingMoment());

		// Phase 8d: Asymmetric vortex shedding at high AoA
		applyAsymmetricVortexShedding(conditions, total, actualWarnings);

		// Phase 4e: the high-AoA advisories (Warning.HIGH_AOA, Warning.HIGH_AOA_VORTEX)
		// are raised by BasicEventSimulationEngine rather than here. This calculator is
		// invoked for every RK4 sub-step, including on the pad and under a deployed
		// parachute, and it cannot see tumbling/recovery/landed state -- so raising them
		// here fired them on every descent. The engine already inhibits its LargeAOA
		// warning on exactly those conditions; the advisories now share that gate.

		return total;
	}

	@Override
	public void checkGeometry(FlightConfiguration configuration, RocketComponent component, WarningSet warnings) {
		stabilityCalculator.checkGeometry(configuration, component, warnings);
	}

	@Override
	protected void voidAerodynamicCache() {
		super.voidAerodynamicCache();
		stabilityCalculator.voidAerodynamicCache();
		dragCalculator.voidAerodynamicCache();
	}

	@Override
	public ModID getModID() {
		return ModID.ZERO;
	}

	public static double calculateStagnationCD(double m) {
		return BarrowmanDragCalculator.calculateStagnationCD(m);
	}

	public static double calculateBaseCD(double m) {
		return BarrowmanDragCalculator.calculateBaseCD(m);
	}

	/**
	 * Maximum physically reasonable total drag coefficient in near-axial flow.
	 * A blunt body at Mach 10 has Cd ~ 2. With base + friction + wave drag,
	 * a total Cd above 10 is unphysical for a rocket at small angle of attack and
	 * indicates a numerical blow-up in one of the component calculations.
	 */
	private static final double MAX_REASONABLE_CD = 10.0;

	/**
	 * Growth of the drag ceiling with angle of attack.
	 * <p>
	 * Drag coefficients are referenced to the (small) frontal area, so the flat
	 * near-axial ceiling above is only valid while the body is aligned with the flow.
	 * As the rocket turns broadside the body presents its planform instead, and the
	 * crossflow contribution alone approaches Cd_c * (L/D) * sin^2(alpha) -- of order 20
	 * to 35 for a slender airframe, entirely physical. Clamping that back to 10 silently
	 * under-drags any rocket flying at large alpha (weathercocking, staging transients,
	 * tumbling) and raised a HIGH-priority "numerical singularity" warning on the stock
	 * "Parallel booster staging" example, which reaches alpha ~ 26 deg with Cd ~ 13.
	 * <p>
	 * The ceiling therefore scales as MAX_REASONABLE_CD * (1 + gain * sin^2(alpha)),
	 * giving 10 at alpha = 0 (so near-axial behaviour, and the validation corpus, are
	 * untouched) and 100 fully broadside -- still far below anything NaN propagation
	 * produces, so the guard keeps its actual job of catching numerical blow-ups.
	 */
	private static final double MAX_CD_AOA_GAIN = 9.0;

	/**
	 * Maximum physically reasonable normal force coefficient.
	 * CN = CN_alpha * alpha; for typical rockets CN_alpha ~ 2-20/rad.
	 * At extreme AoA, CN can reach ~30-50. Values beyond 100 are unphysical.
	 */
	private static final double MAX_REASONABLE_CN = 100.0;

	/**
	 * Maximum physically reasonable raw pitching moment coefficient Cm.
	 * Cm = CN * xCP / refLen; for a slender rocket with fineness ratio L/D = 50
	 * and CN = MAX_REASONABLE_CN, Cm_max ~ 100 * 0.75 * 50 = 3750.
	 * Only values above this constant indicate a numerical blow-up (NaN propagation
	 * producing a huge finite Cm rather than the expected Infinity/NaN).
	 *
	 * IMPORTANT: The old limit of 50 was catastrophically wrong for slender rockets
	 * (e.g. Proteus 6, L/D ≈ 28.5), where the physical Cm about the nose is ~105.
	 * Clamping to 50 inverted the stability sign, causing the rocket to tumble.
	 * The correct check for aerodynamic stability is Cm_at_CG = Cm - CN*xCG/refLen,
	 * computed in the flight stepper — that quantity is naturally small (O(CN * SM))
	 * for any stable rocket, regardless of fineness ratio.
	 */
	private static final double MAX_REASONABLE_CM = MAX_REASONABLE_CN * 50.0; // 5000

	/**
	 * Sanitize aerodynamic force coefficients to prevent simulation divergence.
	 * Catches NaN, Infinity, and extreme values from transonic singularities
	 * or other numerical issues in the component calculators.
	 */
	// Per-instance counter so each simulation run gets independent clamp logging.
	// Previously static, which meant Byrum (first in benchmark) consumed all 3 log
	// slots and Proteus 6's destabilizing Cm clamps fired silently.
	private int sanitizeWarningCount = 0;

	/**
	 * Minimum Mach at which aerodynamic coefficients and angle of attack are
	 * physically meaningful enough to warn the user about.
	 * <p>
	 * Coefficients are normalised by dynamic pressure, so they diverge as V approaches
	 * zero purely by construction: skin friction alone gives Cf ~ Re^(-1/5) (turbulent)
	 * or Re^(-1/2) (laminar), which is unbounded at V = 0, while the force it produces
	 * (CD * q * A) still tends to zero. Angle of attack degenerates the same way -- once
	 * the velocity vector is near zero its direction is numerical noise, so AoA swings
	 * wildly on the pad, at apogee, and through the coast reversal.
	 * <p>
	 * Below this threshold the clamp is routine hygiene rather than evidence of a
	 * modelling failure, so it is applied silently. Stock example rockets were raising
	 * HIGH-priority "coefficient clamped" warnings at M ~ 0.006 (about 2 m/s) purely
	 * from this effect. 0.05 is roughly 17 m/s at sea level -- below any realistic
	 * launch-rail exit speed, so genuine low-speed warnings are still reported.
	 */
	private static final double AERO_WARNING_MIN_MACH = 0.05;

	private void sanitizeForces(AerodynamicForces forces, FlightConditions conditions,
			WarningSet warnings) {
		boolean clamped = false;

		// Drag ceiling grows with angle of attack; see MAX_CD_AOA_GAIN.
		double sinAoa = Math.sin(conditions.getAOA());
		double maxCd = MAX_REASONABLE_CD * (1.0 + MAX_CD_AOA_GAIN * sinAoa * sinAoa);

		// Sanitize drag coefficients
		double cd = forces.getCD();
		if (!Double.isFinite(cd) || cd > maxCd) {
			forces.setCD(maxCd);
			forces.setCDaxial(maxCd);
			clamped = true;
		}

		double cdAxial = forces.getCDaxial();
		if (!Double.isFinite(cdAxial) || Math.abs(cdAxial) > maxCd) {
			forces.setCDaxial(Math.copySign(maxCd, cdAxial));
			clamped = true;
		}

		// Sanitize normal force coefficient
		double cn = forces.getCN();
		if (!Double.isFinite(cn) || Math.abs(cn) > MAX_REASONABLE_CN) {
			forces.setCN(Math.copySign(MAX_REASONABLE_CN, Double.isFinite(cn) ? cn : 0));
			clamped = true;
		}

		// Sanitize pitching moment.
		// Only zero out non-finite (NaN/Infinity) values; the raw Cm about the
		// nose is legitimately large for slender rockets (see MAX_REASONABLE_CM).
		double cm = forces.getCm();
		if (!Double.isFinite(cm)) {
			forces.setCm(0);
			clamped = true;
		} else if (Math.abs(cm) > MAX_REASONABLE_CM) {
			forces.setCm(Math.copySign(MAX_REASONABLE_CM, cm));
			clamped = true;
		}

		// Sanitize side force
		double cside = forces.getCside();
		if (!Double.isFinite(cside)) {
			forces.setCside(0);
			clamped = true;
		}

		// The clamp itself always applies -- it is what stops NaN/Infinity reaching the
		// stepper. Only surface it to the user where the coefficient is meaningful; see
		// AERO_WARNING_MIN_MACH.
		if (clamped && conditions.getMach() >= AERO_WARNING_MIN_MACH) {
			if (sanitizeWarningCount < 3) {
				logger.warn("Clamped aero coefficients at M={} (CD={}, CN={}, Cm={}); further warnings suppressed",
						conditions.getMach(), cd, cn, cm);
				sanitizeWarningCount++;
			}
			warnings.add(Warning.FORCE_COEFFICIENT_CLAMPED);
		}
	}

	/** AoA threshold for asymmetric vortex shedding onset (20 degrees). */
	private static final double VORTEX_AOA_ONSET = Math.toRadians(20.0);
	/** AoA at which vortex side force saturates (40 degrees). */
	private static final double VORTEX_AOA_SATURATE = Math.toRadians(40.0);
	/** Vortex asymmetry coefficient (Champigny-Lacau empirical). */
	private static final double VORTEX_KV = 0.20;

	/**
	 * Apply asymmetric vortex shedding side force at high angle of attack.
	 * At AoA > 20 deg, body vortex shedding becomes asymmetric, generating
	 * a side force proportional to the body normal force.
	 * Reference: Champigny & Lacau (1994), "Lateral Aerodynamics of a Missile
	 * at High Angles of Attack", AGARD CP-536.
	 */
	static void applyAsymmetricVortexShedding(FlightConditions conditions,
			AerodynamicForces total, WarningSet warnings) {
		double alpha = conditions.getAOA();
		if (alpha <= VORTEX_AOA_ONSET) {
			return;
		}

		double cnBody = total.getCN();
		double cy;
		if (alpha < VORTEX_AOA_SATURATE) {
			// Linear ramp from 0 to k_v * CN_body
			double f = (alpha - VORTEX_AOA_ONSET) / (VORTEX_AOA_SATURATE - VORTEX_AOA_ONSET);
			cy = VORTEX_KV * cnBody * f;
		} else {
			cy = VORTEX_KV * cnBody;
		}

		// The side force is always applied; the accompanying HIGH_AOA_VORTEX advisory is
		// raised by BasicEventSimulationEngine, which can inhibit it while tumbling, under
		// a deployed recovery device, or on the ground.
		total.setCside(total.getCside() + cy);
	}
}

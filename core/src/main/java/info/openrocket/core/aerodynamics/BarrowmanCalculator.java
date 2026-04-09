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

			if (forces.getCP().isNaN()) {
				logger.warn("NaN CP detected for component {}, defaulting to zero", comp);
				forces.setCP(Coordinate.ZERO);
			}
			if (Double.isNaN(forces.getBaseCD())) {
				logger.warn("NaN baseCD detected for component {}, defaulting to zero", comp);
				forces.setBaseCD(0);
			}
			if (Double.isNaN(forces.getPressureCD())) {
				logger.warn("NaN pressureCD detected for component {}, defaulting to zero", comp);
				forces.setPressureCD(0);
			}
			if (Double.isNaN(forces.getFrictionCD())) {
				logger.warn("NaN frictionCD detected for component {}, defaulting to zero", comp);
				forces.setFrictionCD(0);
			}
			if (Double.isNaN(forces.getOverrideCD())) {
				logger.warn("NaN overrideCD detected for component {}, defaulting to zero", comp);
				forces.setOverrideCD(0);
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

		stabilityCalculator.calculateDampingMoments(configuration, conditions, total);
		total.setCm(total.getCm() - total.getPitchDampingMoment());
		total.setCyaw(total.getCyaw() - total.getYawDampingMoment());

		// Phase 8d: Asymmetric vortex shedding at high AoA
		applyAsymmetricVortexShedding(conditions, total, actualWarnings);

		// Phase 4e: High-AoA warning (issued once per calculation, not per component step)
		if (conditions.getAOA() > Math.toRadians(15)) {
			actualWarnings.add(Warning.HIGH_AOA);
		}

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

		total.setCside(total.getCside() + cy);
		warnings.add(Warning.HIGH_AOA_VORTEX);
	}
}

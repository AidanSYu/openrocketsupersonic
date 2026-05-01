package info.openrocket.core.aerodynamics.barrowman;

import java.util.List;

import info.openrocket.core.aerodynamics.ShockGeometry;
import info.openrocket.core.aerodynamics.shocks.NormalShockRelations;
import info.openrocket.core.aerodynamics.shocks.ObliqueShockSolver;
import info.openrocket.core.aerodynamics.shocks.PrandtlMeyerExpansion;
import info.openrocket.core.util.CoordinateIF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.RailButton;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.util.MathUtil;
import info.openrocket.core.util.Transformation;

public class RailButtonCalc extends RocketComponentCalc {
	private final static Logger log = LoggerFactory.getLogger(RailButtonCalc.class);

	private static final double GAMMA = 1.4;

	// Phase 7c: Blend boundaries for supersonic protuberance wave drag
	private static final double WAVE_DRAG_BLEND_LOW = 0.9;
	private static final double WAVE_DRAG_BLEND_HIGH = 1.1;

	// Phase 7c: Body interference friction penalty factor
	private static final double INTERFERENCE_FRICTION_PENALTY = 0.20;
	// Interference extends downstream over this many button heights
	private static final double INTERFERENCE_LENGTH_FACTOR = 3.0;

	// values transcribed from Gowen and Perkins, "Drag of Circular Cylinders for a
	// Wide Range
	// of Reynolds Numbers and Mach Numbers", NACA Technical Note 2960, Figure 7
	private static final List<Double> cdDomain = List.of(0.0, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 1.0, 1.6, 2.0, 2.8, 100.0);
	private static final List<Double> cdRange = List.of(1.2, 1.22, 1.25, 1.3, 1.4, 1.5, 1.6, 2.1, 1.5, 1.45, 1.33,
			1.33);

	private final RailButton button;

	public RailButtonCalc(RocketComponent component) {
		super(component);

		// need to stash the button
		button = (RailButton) component;
	}

	@Override
	public double calculateFrictionCD(FlightConditions conditions, double componentCf, WarningSet warnings) {
		// very small relative surface area, and slick
		return 0.0;
	}

	@Override
	public void calculateNonaxialForces(FlightConditions conditions, Transformation transform,
			AerodynamicForces forces, WarningSet warnings) {
		// Nothing to be done
	}

	@Override
	public double calculatePressureCD(FlightConditions conditions,
			double stagnationCD, double baseCD, WarningSet warnings) {

		// grab relevant button params
		final CoordinateIF[] instanceOffsets = button.getInstanceOffsets();

		// compute button reference area
		final double buttonHt = effectiveAerodynamicHeight();
		final double outerArea = buttonHt * button.getOuterDiameter();
		final double notchArea = (button.getOuterDiameter() - button.getInnerDiameter()) * button.getInnerHeight();
		final double refArea = outerArea - notchArea;

		double mach = conditions.getMach();

		double result;
		// At supersonic speeds, use the Phase 7c wave drag model
		if (mach >= WAVE_DRAG_BLEND_HIGH) {
			result = calculateSupersonicPressureCD(conditions, refArea);
		} else {
			// Compute Hoerner subsonic drag
			double hoernerCd = calculateHoernerPressureCD(conditions, stagnationCD, refArea);

			// In the blend region, interpolate between Hoerner and supersonic
			if (mach > WAVE_DRAG_BLEND_LOW) {
				double supersonicCd = calculateSupersonicPressureCD(conditions, refArea);
				double t = (mach - WAVE_DRAG_BLEND_LOW) / (WAVE_DRAG_BLEND_HIGH - WAVE_DRAG_BLEND_LOW);
				double w = t * t * (3 - 2 * t); // C1-continuous smoothstep
				result = (1 - w) * hoernerCd + w * supersonicCd;
			} else {
				result = hoernerCd;
			}
		}

		// Body interference wake penalty: add at all Mach. Subsonic protuberances
		// still disturb the downstream boundary layer — gating this to M>1 left a
		// systematic subsonic drag deficit on rail-guided HPR vehicles.
		// Already included inside calculateSupersonicPressureCD for M >= blend_high,
		// so only add it in the subsonic and blend branches here.
		if (mach < WAVE_DRAG_BLEND_HIGH) {
			double interferenceCd = calculateInterferenceDrag(conditions);
			if (mach > WAVE_DRAG_BLEND_LOW) {
				// In the blend region the supersonic branch already contributed
				// (1 - w) worth of interference through the lerp above; top it up.
				double t = (mach - WAVE_DRAG_BLEND_LOW) / (WAVE_DRAG_BLEND_HIGH - WAVE_DRAG_BLEND_LOW);
				double w = t * t * (3 - 2 * t);
				result += (1 - w) * interferenceCd;
			} else {
				result += interferenceCd;
			}
		}

		// Defensive: rail-button drag is tiny (~0.5-2% of total Cd), and
		// BarrowmanCalculator silently zeros non-finite component Cd without
		// a WarningSet entry. If a shock solver or PM expansion produces a
		// NaN/Inf at an edge case, return 0 rather than letting the silent
		// zeroing mask a real drag contribution elsewhere.
		if (!Double.isFinite(result)) {
			log.debug("RailButtonCalc produced non-finite pressureCD at M={}; clamping to 0", mach);
			return 0;
		}
		return result;
	}

	/**
	 * Original Hoerner-based subsonic pressure drag computation.
	 */
	private double calculateHoernerPressureCD(FlightConditions conditions,
			double stagnationCD, double refArea) {

		final CoordinateIF[] instanceOffsets = button.getInstanceOffsets();
		final double buttonHt = effectiveAerodynamicHeight();

		double CDmul = 0.0;
		if (conditions.getMach() > MathUtil.EPSILON) {
			for (int i = 0; i < button.getInstanceCount(); i++) {

				double x = (button.toAbsolute(instanceOffsets[i]))[0].getX();
				double rex = calculateReynoldsNumber(x, conditions);
				double del = 0.37 * x / Math.pow(rex, 0.2);

				double mach;
				if (buttonHt > del) {
					mach = (buttonHt - 0.5 * del) * conditions.getMach() / buttonHt;
				} else {
					mach = MathUtil.map(buttonHt / 2.0, 0, del, 0, conditions.getMach());
				}

				double cd = MathUtil.interpolate(cdDomain, cdRange, mach);
				cd = cd * MathUtil.pow2(mach) / MathUtil.pow2(conditions.getMach());
				CDmul += cd;
			}
			CDmul /= button.getInstanceCount();
		} else {
			CDmul = 8.786395072609939E-4;
		}

		// The Hoerner values are bluff-body drag coefficients referenced to the
		// button frontal area. They are not pressure coefficients, so do not scale
		// them by the vehicle stagnation coefficient.
		return CDmul * refArea / conditions.getRefArea();
	}

	/**
	 * Phase 7c: Supersonic protuberance wave drag.
	 * <p>
	 * At M > 1, rail buttons and launch lugs generate wave drag via oblique
	 * or detached bow shocks. The drag depends on whether the button's effective
	 * deflection angle allows an attached oblique shock.
	 * <p>
	 * <b>Attached shock</b> (sharp protuberance, deflection < max):
	 * Front face Cp from oblique shock pressure coefficient.
	 * <p>
	 * <b>Detached shock</b> (blunt protuberance, deflection > max):
	 * Front face Cp from normal shock stagnation pressure coefficient.
	 * <p>
	 * Rear face pressure is reduced by Prandtl-Meyer expansion around the
	 * trailing shoulder.
	 * <p>
	 * Body interference: 20% friction penalty over 3h downstream of the
	 * protuberance.
	 *
	 * @param conditions flight conditions
	 * @param refArea    button reference area (frontal area)
	 * @return pressure drag coefficient referenced to vehicle S_ref
	 */
	private double calculateSupersonicPressureCD(FlightConditions conditions, double refArea) {
		double mach = conditions.getMach();
		double sRef = conditions.getRefArea();
		if (sRef < 1e-12 || mach < 1.0) {
			return 0;
		}

		final double buttonHt = effectiveAerodynamicHeight();
		final double outerDiam = button.getOuterDiameter();

		// Effective deflection angle of the protuberance
		// For a rail button, approximate as atan(height / half-diameter)
		double thetaP = Math.atan2(buttonHt, outerDiam / 2.0);

		// Determine if shock is attached or detached
		double maxDeflection;
		try {
			maxDeflection = ObliqueShockSolver.maxDeflectionAngle(mach, GAMMA);
		} catch (Exception e) {
			maxDeflection = 0;
		}

		double cpFront;
		boolean attached = maxDeflection > thetaP && thetaP > 0;

		if (attached) {
			// Attached oblique shock
			try {
				ObliqueShockSolver.ObliqueShockResult result =
						ObliqueShockSolver.solve(mach, thetaP, GAMMA, true);
				cpFront = 2.0 / (GAMMA * mach * mach) * (result.pressureRatio - 1.0);
			} catch (IllegalArgumentException e) {
				// Fallback to normal shock
				cpFront = calculateNormalShockCp(mach);
			}
		} else {
			// Detached shock — use normal shock stagnation Cp
			cpFront = calculateNormalShockCp(mach);
		}

		// Rear face: Prandtl-Meyer expansion around trailing shoulder
		double cpRear = 0;
		if (mach > 1.0) {
			try {
				// The trailing shoulder turns the flow by approximately thetaP
				double m2 = PrandtlMeyerExpansion.downstreamMach(mach, thetaP, GAMMA);
				double pRatio = PrandtlMeyerExpansion.pressureRatio(mach, m2, GAMMA);
				cpRear = 2.0 / (GAMMA * mach * mach) * (pRatio - 1.0); // negative (expansion)
			} catch (IllegalArgumentException e) {
				// Expansion exceeds limits — assume vacuum on rear (Cp = -2/(gamma*M^2))
				cpRear = -2.0 / (GAMMA * mach * mach);
			}
		}

		// Net wave drag = (Cp_front - Cp_rear) * A_frontal / S_ref
		double aFrontal = buttonHt * outerDiam; // frontal area
		double cdWave = (cpFront - cpRear) * aFrontal / sRef;

		// Body interference: 20% friction penalty over 3h downstream
		double cdInterference = calculateInterferenceDrag(conditions);

		return Math.max(0, cdWave) + cdInterference;
	}

	/**
	 * Compute the normal shock stagnation pressure coefficient.
	 */
	private static double calculateNormalShockCp(double mach) {
		if (mach <= 1.0) return 0;
		double pRatio = NormalShockRelations.pressureRatio(mach, GAMMA);
		return 2.0 / (GAMMA * mach * mach) * (pRatio - 1.0);
	}

	/**
	 * Phase 7c: Body interference friction drag from protuberance wake.
	 * <p>
	 * The disturbed wake behind a protuberance increases skin friction on the
	 * body surface downstream. The penalty is approximately 20% of the local
	 * friction coefficient over a length of 3h (3 button heights).
	 *
	 * @param conditions flight conditions
	 * @return interference drag coefficient referenced to vehicle S_ref
	 */
	private double calculateInterferenceDrag(FlightConditions conditions) {
		if (conditions.getMach() < MathUtil.EPSILON) return 0;

		double sRef = conditions.getRefArea();
		if (sRef < 1e-12) return 0;

		final double buttonHt = effectiveAerodynamicHeight();
		final double outerDiam = button.getOuterDiameter();

		// Interference area: strip on body downstream, width ~ outerDiam, length ~ 3*buttonHt
		double interferenceLength = INTERFERENCE_LENGTH_FACTOR * buttonHt;
		double interferenceArea = interferenceLength * outerDiam;

		// Local skin friction coefficient (approximate)
		double cf = 0.003; // reasonable turbulent Cf estimate
		if (componentAbsoluteX > 0.01 && conditions.getVelocity() > 1) {
			double nu = conditions.getAtmosphericConditions().getKinematicViscosity();
			if (nu > 0) {
				double reX = conditions.getVelocity() * componentAbsoluteX / nu;
				if (reX > 1e4) {
					cf = 1.0 / MathUtil.pow2(1.50 * Math.log(reX) - 5.6);
				}
			}
		}

		return INTERFERENCE_FRICTION_PENALTY * cf * interferenceArea / sRef;
	}

	private double effectiveAerodynamicHeight() {
		double height = button.getTotalHeight();
		double diameter = button.getOuterDiameter();
		if (diameter > 0.0 && height / diameter < 0.55) {
			// Some RASAero imports carry low-profile guide envelopes used for launch
			// hardware placement, while the source ALX1 marks railguide drag area as
			// zero. Keep the physical component for mass/placement, but do not model
			// the display envelope as a large exposed bluff protuberance.
			return 0.0;
		}
		return height;
	}
}

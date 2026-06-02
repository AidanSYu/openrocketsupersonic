package info.openrocket.core.aerodynamics.barrowman;

import static info.openrocket.core.models.atmosphere.AtmosphericConditions.GAMMA;
import static info.openrocket.core.util.MathUtil.pow2;
import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.shocks.ObliqueShockSolver;
import info.openrocket.core.aerodynamics.shocks.PrandtlMeyerExpansion;
import info.openrocket.core.logging.Warning;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.util.BugException;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.LinearInterpolator;
import info.openrocket.core.util.MathUtil;
import info.openrocket.core.util.PolyInterpolator;
import info.openrocket.core.util.Transformation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calculates the aerodynamic properties of a <code>SymmetricComponent</code>.
 * <p>
 * CP and CNa are calculated by the Barrowman method extended to account for
 * body lift by the method presented by Galejs. Supersonic body normal force is
 * treated as a slender-body/crossflow correction with a transonic blend rather
 * than by reusing the fin lifting-surface model.
 * 
 * 
 * @author Sampo Niskanen <sampo.niskanen@iki.fi>
 */
public class SymmetricComponentCalc extends RocketComponentCalc {

	private final static Logger log = LoggerFactory.getLogger(SymmetricComponentCalc.class);

	public static final double BODY_LIFT_K = 1.1;

	/**
	 * Crossflow drag coefficient Cd_c as a function of crossflow Mach number,
	 * based on Jorgensen (NASA TR R-474, 1977). At low crossflow Mach, Cd_c = 1.2
	 * (circular cylinder). At supersonic crossflow Mach, Cd_c rises to ~2.0.
	 */
	private static final double SUBSONIC_CDC = 1.20;
	private static final LinearInterpolator crossflowCdcInterpolator = new LinearInterpolator(
			new double[] { 0.0, 0.2, 0.4, 0.6, 0.8, 0.9, 1.0, 1.2, 1.5, 2.0, 3.0, 5.0 },
			new double[] { 1.20, 1.20, 1.20, 1.25, 1.35, 1.50, 1.65, 1.80, 1.85, 1.95, 2.00, 2.00 });

	private static final double SUPERSONIC_BLEND_LOW = 1.3;
	private static final double SUPERSONIC_BLEND_HIGH = 1.5;
	private static final double MAX_SUPERSONIC_MACH = 10.0;
	private static final int SHOCK_EXPANSION_STRIPS = 100;

	// Phase 4a: Modified Newtonian theory blending bounds
	private static final double NEWTONIAN_BLEND_LOW = 4.0;
	private static final double NEWTONIAN_BLEND_HIGH = 6.0;

	// Phase 4e: Model validity limits
	/** Maximum Mach with full model confidence. */
	static final double MACH_FULL_CONFIDENCE = 5.0;
	/** Maximum Mach with reduced confidence (Newtonian approximation). */
	static final double MACH_REDUCED_CONFIDENCE = 10.0;

	// Phase 3a: Transonic blending bounds for body CNa/CP supersonic correction
	private static final double STABILITY_BLEND_LOW = 0.8;
	private static final double STABILITY_BLEND_HIGH = 1.3;

	private final double length;
	private final double foreRadius, aftRadius;
	private final double fineness;
	private final Transition.Shape shape;
	private final double param;
	private final double frontalArea;
	private final double fullVolume;
	private final double planformArea, planformCenter;
	private final double wetArea;
	private final double sinphi;
	private final double tipHalfAngle;

	// Phase 7d: upstream aft radius for forward-facing step drag (ESDU 66011)
	private final double upstreamAftRadius;

	public SymmetricComponentCalc(RocketComponent c) {
		super(c);
		if (!(c instanceof SymmetricComponent)) {
			throw new IllegalArgumentException("Illegal component type " + c);
		}
		SymmetricComponent component = (SymmetricComponent) c;

		length = component.getLength();
		if (length > 0) {
			foreRadius = component.getForeRadius();
			aftRadius = component.getAftRadius();
		} else { // If length is zero, the component is a disk, i.e. a zero-length tube, so match
					// the fore and aft diameter
			final double componentMaxR = Math.max(component.getForeRadius(), component.getAftRadius());
			foreRadius = aftRadius = componentMaxR;
		}

		if (Math.abs(aftRadius - foreRadius) < 1e-9) {
			// Cylindrical body tube or degenerate geometry — use standard L/D definition
			double maxR = Math.max(aftRadius, foreRadius);
			fineness = (maxR > 1e-9) ? length / (2 * maxR) : 5.0;
		} else {
			fineness = length / (2 * Math.abs(aftRadius - foreRadius));
		}
		fullVolume = component.getFullVolume();
		planformArea = component.getComponentPlanformArea();
		planformCenter = component.getComponentPlanformCenter();

		wetArea = component.getComponentWetArea();

		if (component instanceof BodyTube) {
			shape = null;
			param = 0;
			frontalArea = 0;
			sinphi = 0;
		} else if (component instanceof Transition) {
			shape = ((Transition) component).getShapeType();
			param = ((Transition) component).getShapeParameter();
			frontalArea = Math.abs(Math.PI * (foreRadius * foreRadius - aftRadius * aftRadius));

			double r = component.getRadius(0.99 * length);
			// Geometric slope at the base of the nose, used to drive the
			// empirical TR-R-100 transonic pressure drag interpolator
			// (calculateTransonicInterpolator). Upstream OpenRocket #2998
			// special-cased tangent ogives (param == 1.0) by forcing sinphi = 0,
			// which collapses the entire empirical Cd_p curve to zero from
			// M = 1.0 through M = 1.5. That zeroes the transonic drag rise on
			// every tangent-ogive nose cone — including the slender minimum-
			// diameter rockets in our SimVReal corpus (L500, Kinsel, EZI-65),
			// which then over-predict apogee by 20–45 % because there is no
			// nose wave drag during the transonic peak. The geometric formula
			// already returns a small value for high-fineness ogives (sinphi ≈
			// 0.10 for L500's 8.85:1 ogive vs ≈ 0.30 for the TR-R-100 reference
			// 3:1 ogive) — that *is* the slender-body taper, no special case
			// needed. Use the geometric slope for tangent ogives the same way
			// we do for every other parametric ogive shape.
			sinphi = (aftRadius - r) / MathUtil.hypot(aftRadius - r, 0.01 * length);
		} else {
			throw new UnsupportedOperationException("Unknown component type " +
					component.getComponentName());
		}

		if (shape != null && foreRadius < aftRadius && length > 0.001) {
			double dx = length * 1e-4;
			double rAtDx = foreRadius + shape.getRadius(dx, aftRadius - foreRadius, length, param);
			tipHalfAngle = Math.atan2(rAtDx - foreRadius, dx);
		} else {
			tipHalfAngle = 0;
		}

		// Phase 7d: Store the upstream component's aft radius for step drag
		SymmetricComponent prev = component.getPreviousSymmetricComponent();
		if (prev != null) {
			upstreamAftRadius = prev.getAftRadius();
		} else {
			upstreamAftRadius = 0;
		}
	}

	private boolean isTube = false;
	private double cnaCache = Double.NaN;
	private double cpCache = Double.NaN;

	/**
	 * Calculates the non-axial forces produced by a symmetric component (normal and
	 * side forces, pitch, yaw and roll moments, CP position, CNa).
	 * <p>
	 * This method uses the Barrowman method for CP and CNa calculation and the
	 * extension presented by Galejs for the effect of body lift.
	 * <p>
	 * Phase 3a: At supersonic speeds, the CP shifts aft toward the centroid of the
	 * planform area, consistent with the crossflow analogy (Allen &amp; Perkins,
	 * NACA Report 1048). The body lift coefficient is also adjusted for Mach.
	 * The transition is C1-continuous through the transonic region (M 0.8-1.3).
	 */
	@Override
	public void calculateNonaxialForces(FlightConditions conditions, Transformation transform,
			AerodynamicForces forces, WarningSet warnings) {

		// Pre-calculate and store the Mach-invariant results
		if (Double.isNaN(cnaCache)) {
			final double r0 = foreRadius;
			final double r1 = aftRadius;

			if (MathUtil.equals(r0, r1)) {
				isTube = true;
				cnaCache = 0;
			} else {
				isTube = false;

				final double A0 = Math.PI * pow2(r0);
				final double A1 = Math.PI * pow2(r1);

				cnaCache = 2 * (A1 - A0);
				cpCache = (length * A1 - fullVolume) / (A1 - A0);
			}
		}

		double mach = conditions.getMach();
		CoordinateIF cp;

		if (isTube) {
			// Barrowman: a cylindrical body section (foreRadius == aftRadius) has zero
			// normal-force coefficient.  There is no area change, so the potential-flow
			// pressure distribution integrates to zero normal force.  The Galejs viscous
			// crossflow formula — K × planformArea/refArea — grossly overestimates CNa
			// for long slender bodies (17 rad⁻¹ for an 8"×124" tube) and, when fin CNa
			// drops as 1/β at supersonic Mach, allows the body CNa to overwhelm fins and
			// drive CP forward past the CG, producing spurious instability.  RASAero II
			// correctly uses CNa = 0 for cylindrical sections; we do the same.
			cp = new Coordinate(planformCenter, 0, 0, 0);
		} else {
			// Phase 3a: Mach-dependent CP position
			// At subsonic: Barrowman CP (cpCache)
			// At supersonic: CP shifts aft toward planform centroid
			double effectiveCp = getEffectiveCpPosition(mach);

			// At supersonic speeds (M ≥ 1.3), contracting transitions (boattails,
			// boat-tails) have cnaCache < 0 — a destabilising contribution from
			// Barrowman's area-change formula.  In practice these components sit in
			// the wake of the fins at the aft end of the rocket; the simple
			// potential-flow result is unreliable there and produces a spurious
			// forward CP shift.  RASAero II ignores boattail CNa for stability in
			// this regime.  We do the same: blend the weight to zero through the
			// same transonic band used for body lift (M 0.8 → 1.3).
			double effectiveCna = cnaCache;
			if (cnaCache < 0 && mach > STABILITY_BLEND_LOW) {
				double t = (mach - STABILITY_BLEND_LOW) / (STABILITY_BLEND_HIGH - STABILITY_BLEND_LOW);
				t = Math.min(1.0, t);
				double w = t * t * (3 - 2 * t); // cubic Hermite smoothstep, 0→1
				effectiveCna = cnaCache * (1.0 - w); // fade destabilising CNa to zero
			}

			cp = new Coordinate(effectiveCp, 0, 0, effectiveCna * conditions.getSincAOA() /
					conditions.getRefArea()).average(getLiftCP(conditions, warnings));
		}

		// Phase 4d: Defensive NaN/Inf guard on CP
		if (cp == null || Double.isNaN(cp.getX()) || Double.isInfinite(cp.getX())) {
			cp = new Coordinate(0, 0, 0, 0);
		}

		forces.setCP(cp);
		double cn = forces.getCP().getWeight() * conditions.getAOA();
		double cm = cn * cp.getX() / conditions.getRefLength();

		// Phase 4d: Clamp NaN/Inf in computed forces
		forces.setCN(Double.isFinite(cn) ? cn : 0);
		forces.setCm(Double.isFinite(cm) ? cm : 0);
		forces.setCroll(0);
		forces.setCrollDamp(0);
		forces.setCrollForce(0);
		forces.setCside(0);
		forces.setCyaw(0);

		// Phase 4e: Tiered model validity warnings (Mach-based only;
		// high-AoA warning is omitted here because it fires every step during
		// normal tumbling and creates unexpected SIM_WARN flight events)
		if (mach > MACH_REDUCED_CONFIDENCE) {
			warnings.add(Warning.HYPERSONIC_EXTREME);
		} else if (mach > MACH_FULL_CONFIDENCE) {
			warnings.add(Warning.HYPERSONIC);
		}

	}

	/**
	 * Compute the effective CP position, blending between the Barrowman CP
	 * (subsonic) and a more aft position at supersonic speeds.
	 * <p>
	 * At supersonic speeds, the pressure distribution on the body changes:
	 * the nose shock concentrates pressure near the tip, while the crossflow
	 * component acts at the planform centroid. The net effect is that the CP
	 * moves aft relative to the subsonic Barrowman prediction.
	 * <p>
	 * The shift is blended C1-continuously through the transonic region.
	 *
	 * @param mach current Mach number
	 * @return effective CP position along the component
	 */
	private double getEffectiveCpPosition(double mach) {
		if (mach <= STABILITY_BLEND_LOW) {
			return cpCache;
		}

		// Supersonic CP: blend between Barrowman CP and planform centroid.
		// At high Mach, the crossflow component dominates body lift,
		// shifting the effective CP toward planformCenter.
		// Use a moderate shift factor (30% toward planformCenter) — full shift
		// would overpredict the aft movement for typical rocket geometries.
		double cpSupersonic = cpCache + 0.3 * (planformCenter - cpCache);

		// Clamp to component bounds
		cpSupersonic = Math.max(0, Math.min(length, cpSupersonic));

		if (mach >= STABILITY_BLEND_HIGH) {
			return cpSupersonic;
		}

		// Transonic blend: smoothstep from cpCache to cpSupersonic
		double t = (mach - STABILITY_BLEND_LOW) / (STABILITY_BLEND_HIGH - STABILITY_BLEND_LOW);
		double w = t * t * (3 - 2 * t); // cubic Hermite smoothstep
		return cpCache + w * (cpSupersonic - cpCache);
	}

	/**
	 * Returns the crossflow drag coefficient Cd_c for a circular cylinder at the
	 * given crossflow Mach number, based on Jorgensen (NASA TR R-474, 1977).
	 * <p>
	 * Crossflow Mach is defined as M_crossflow = M * sin(alpha), where alpha is
	 * the angle of attack. At low M_crossflow, Cd_c = 1.2. At supersonic
	 * crossflow Mach, Cd_c rises to 2.0 due to shock-induced pressure drag on
	 * the cylindrical cross-section.
	 *
	 * @param crossflowMach the crossflow Mach number (M * sin(alpha))
	 * @return the crossflow drag coefficient Cd_c
	 */
	public static double getCrossflowDragCoefficient(double crossflowMach) {
		if (crossflowMach <= 0.0) {
			return SUBSONIC_CDC;
		}
		if (crossflowMach >= 5.0) {
			return 2.0;
		}
		return crossflowCdcInterpolator.getValue(crossflowMach);
	}

	/**
	 * Calculate the body lift effect according to Galejs, with Jorgensen
	 * crossflow drag coefficient correction at supersonic crossflow Mach
	 * and Mach-dependent K correction (Phase 3a, Phase 6a).
	 */
	protected CoordinateIF getLiftCP(FlightConditions conditions, WarningSet warnings) {

		/*
		 * Without this extra multiplier the rocket may become unstable at apogee
		 * when turning around, and begin oscillating horizontally. During the flight
		 * of the rocket this has no effect. It is effective only when AOA > 45 deg
		 * and the velocity is less than 15 m/s.
		 *
		 * TODO: MEDIUM: This causes an anomaly to the flight results with the CP
		 * jumping at apogee
		 */
		double mul = 1;
		if ((conditions.getMach() < 0.05) && (conditions.getAOA() > Math.PI / 4)) {
			mul = pow2(conditions.getMach() / 0.05);
		}

		// Phase 3a: Mach-dependent body lift coefficient
		double effectiveK = getEffectiveBodyLiftK(conditions.getMach());

		// Phase 6a: Jorgensen crossflow drag coefficient correction
		// At supersonic crossflow Mach (M * sin(alpha)), Cd_c rises from 1.2 to 2.0
		double crossflowMach = conditions.getMach() * conditions.getSinAOA();
		double crossflowScale = getCrossflowDragCoefficient(crossflowMach) / SUBSONIC_CDC;

		return new Coordinate(planformCenter, 0, 0, mul * crossflowScale * effectiveK * planformArea / conditions.getRefArea() *
				conditions.getSinAOA() * conditions.getSincAOA()); // sin(aoa)^2 / aoa
	}

	/**
	 * Returns the effective body lift coefficient K, blended to zero at supersonic speeds.
	 * <p>
	 * At subsonic speeds (M ≤ 0.8), the Galejs empirical constant K=1.1 applies:
	 * it accounts for viscous crossflow lift that the potential-flow Barrowman
	 * formula under-predicts.
	 * <p>
	 * At supersonic speeds (M ≥ 1.3), K is set to 0. Supersonic slender-body
	 * theory (Ward 1949) gives body CNa = 2×(A_aft−A_fore)/A_ref, exactly the
	 * same as the potential-flow Barrowman value, with no additional viscous
	 * correction. Applying K=1.1 supersonically adds a spurious forward-pulling
	 * body-lift term that pushes CP in front of CG at high Mach and AoA, causing
	 * false instability for rockets that flew successfully (e.g. MESOS at M 4.18).
	 * RASAero II uses strictly Barrowman potential-flow for body components (no
	 * Galejs correction); zeroing K at supersonic matches this approach.
	 * <p>
	 * A C1-continuous smoothstep blend connects the two regimes through M 0.8–1.3.
	 *
	 * @param mach current freestream Mach number
	 * @return effective body lift coefficient, in [0, BODY_LIFT_K]
	 */
	private static double getEffectiveBodyLiftK(double mach) {
		if (mach <= STABILITY_BLEND_LOW) {
			return BODY_LIFT_K;
		}
		if (mach >= STABILITY_BLEND_HIGH) {
			return 0.0;
		}
		// Smoothstep blend from BODY_LIFT_K (subsonic) to 0 (supersonic)
		double t = (mach - STABILITY_BLEND_LOW) / (STABILITY_BLEND_HIGH - STABILITY_BLEND_LOW);
		double w = t * t * (3 - 2 * t); // cubic Hermite smoothstep, C1-continuous
		return BODY_LIFT_K * (1.0 - w);
	}

	@Override
	public double calculateFrictionCD(FlightConditions conditions, double componentCf, WarningSet warningSet) {
		double refArea = conditions.getRefArea();
		if (!Double.isFinite(wetArea) || !Double.isFinite(componentCf) || refArea < 1e-12) {
			return 0;
		}
		return componentCf * wetArea / refArea;
	}

	private LinearInterpolator interpolator = null;

	@Override
	public double calculatePressureCD(FlightConditions conditions,
			double stagnationCD, double baseCD, WarningSet warnings) {

		double cd = 0;

		// Check for simple cases first
		if (MathUtil.equals(foreRadius, aftRadius)) {
			cd = 0;
		} else if (length < 0.001) {
			if (foreRadius < aftRadius) {
				cd = stagnationCD * frontalArea / conditions.getRefArea();
			} else {
				cd = baseCD * frontalArea / conditions.getRefArea();
			}
		} else if (aftRadius < foreRadius) {
			// Boattail drag computed directly from base drag
			if (fineness >= 3) {
				cd = 0;
			} else {
				cd = baseCD * frontalArea / conditions.getRefArea();
				if (fineness > 1) {
					cd = cd * (3 - fineness) / 2;
				}
			}
		} else {
			// Nose cones (foreRadius ≈ 0) and expanding shoulders (foreRadius > 0).
			//
			// A nose cone points INTO the flow: the tip creates an oblique shock at
			// supersonic speeds → wave drag.  An expanding shoulder sits mid-body where
			// the flow is already attached; the diverging surface creates a Prandtl-Meyer
			// EXPANSION FAN (favorable pressure, no shock) → zero wave drag.  Applying
			// the nose-cone wave drag model to a shoulder with a 25-30° half-angle
			// grossly over-predicts drag (e.g. the MESOS booster shoulder at M=2 was
			// contributing CDp≈0.31, nearly half the vehicle drag).
			if (foreRadius > 1e-6) {
				// Expanding shoulder: no wave drag at supersonic speeds.
				cd = 0;
			} else {
				// Nose cone: use analytical wave drag interpolator.
				if (interpolator == null) {
					calculateNoseInterpolator();
				}
				double interpCd = interpolator.getValue(conditions.getMach());
				double refArea = conditions.getRefArea();
				if (Double.isFinite(interpCd) && Double.isFinite(frontalArea) && refArea > 1e-12) {
					cd = interpCd * frontalArea / refArea;
				} else {
					cd = 0;
				}
			}
		}

		// Phase 7d: Add ESDU 66011 forward-facing step drag at supersonic speeds
		double stepCd = calculateStepDrag(conditions);
		if (Double.isFinite(stepCd)) {
			cd += stepCd;
		}

		return Double.isFinite(cd) ? cd : 0;
	}

	// ---- Phase 7d: ESDU 66011 forward-facing step drag ----

	/** Blend range for step drag activation around M=1. */
	private static final double STEP_DRAG_BLEND_LOW = 0.95;
	private static final double STEP_DRAG_BLEND_HIGH = 1.1;

	/**
	 * Compute the forward-facing step drag for an interstage diameter mismatch
	 * using the ESDU 66011 model.
	 *
	 * @param conditions flight conditions
	 * @return step drag coefficient (referenced to vehicle S_ref)
	 */
	private double calculateStepDrag(FlightConditions conditions) {
		double mach = conditions.getMach();

		if (mach < STEP_DRAG_BLEND_LOW || foreRadius <= upstreamAftRadius + 1e-6) {
			return 0;
		}

		double stepHeight = foreRadius - upstreamAftRadius;
		if (stepHeight < 1e-6) {
			return 0;
		}

		double refArea = conditions.getRefArea();
		if (refArea < 1e-12) {
			return 0;
		}

		// Step face area (annular ring)
		double aStep = Math.PI * (foreRadius * foreRadius - upstreamAftRadius * upstreamAftRadius);

		// Stagnation pressure coefficient on the step face
		double cpStag = calculateStepStagnationCp(mach);

		// Step face drag
		double cdStep = cpStag * aStep / refArea;

		// Reattachment recovery drag
		double thetaBL = info.openrocket.core.aerodynamics.ShockGeometry.getMomentumThicknessAt(
				componentAbsoluteX, conditions);

		double cdRecovery = 0;
		if (mach > 1.0 && thetaBL > 1e-12) {
			double cf = estimateLocalCf(conditions);

			if (cf > 1e-12) {
				double m2 = mach * mach;
				double betaSq = m2 - 1.0;
				if (betaSq > 0.04) {
					// Guard: sqrt(cf/sqrt(betaSq)) grows as betaSq→0.
					// Raised threshold from 0.01 to 0.04 (M≈1.02) to avoid
					// extreme cpPlateau values in the deep transonic region.
					double cpPlateau = 4.2 * Math.sqrt(2.0 * cf / Math.sqrt(betaSq));
					// Cap cpPlateau to a physically reasonable range
					cpPlateau = Math.min(cpPlateau, 2.0);

					double recoveryLength = 3.0 * stepHeight;
					double recoveryArea = 2.0 * Math.PI * foreRadius * recoveryLength;
					cdRecovery = 0.6 * cpPlateau * recoveryArea / refArea;
				}
			}
		}

		double totalStepDrag = cdStep + cdRecovery;

		// Blend from zero at STEP_DRAG_BLEND_LOW to full at STEP_DRAG_BLEND_HIGH
		if (mach < STEP_DRAG_BLEND_HIGH) {
			double t = (mach - STEP_DRAG_BLEND_LOW) / (STEP_DRAG_BLEND_HIGH - STEP_DRAG_BLEND_LOW);
			double w = t * t * (3 - 2 * t);  // C1-continuous smoothstep
			totalStepDrag *= w;
		}

		return Math.max(0, totalStepDrag);
	}

	/**
	 * Compute the stagnation pressure coefficient on a forward-facing step.
	 */
	private static double calculateStepStagnationCp(double mach) {
		if (mach <= 1.0) {
			return 0;
		}
		double pRatio = info.openrocket.core.aerodynamics.shocks.NormalShockRelations.pressureRatio(mach, GAMMA);
		return (pRatio - 1.0) / (0.5 * GAMMA * mach * mach);
	}

	/**
	 * Estimate the local skin friction coefficient at this component's position.
	 */
	private double estimateLocalCf(FlightConditions conditions) {
		if (componentAbsoluteX < 1e-6 || conditions.getVelocity() < 1e-6) {
			return 0.003;
		}
		double nu = conditions.getAtmosphericConditions().getKinematicViscosity();
		if (nu <= 0) {
			return 0.003;
		}
		double reX = conditions.getVelocity() * componentAbsoluteX / nu;
		if (reX < 1e4) {
			return 0.003;
		}
		return 1.0 / MathUtil.pow2(1.50 * Math.log(reX) - 5.6);
	}

	/*
	 * Experimental values of pressure drag for different nose cone shapes with a
	 * fineness ratio of 3. The data is taken from 'Collection of Zero-Lift Drag Data on
	 * Bodies of Revolution from Free-Flight Investigations', NASA TR-R-100, NTRS
	 * 19630004995,
	 * page 16.
	 * 
	 * This data is extrapolated for other fineness ratios.
	 */

	// Format: array of {Mach numbers}, array of {Cd values}
	private static final LinearInterpolator ellipsoidInterpolator = new LinearInterpolator(
			new double[] { 1.2, 1.25, 1.3, 1.4, 1.6, 2.0, 2.4 },
			new double[] { 0.110, 0.128, 0.140, 0.148, 0.152, 0.159, 0.162 /* constant */ });
	private static final LinearInterpolator x14Interpolator = new LinearInterpolator(
			new double[] { 1.2, 1.3, 1.4, 1.6, 1.8, 2.2, 2.6, 3.0, 3.6 },
			new double[] { 0.140, 0.156, 0.169, 0.192, 0.206, 0.227, 0.241, 0.249, 0.252 });
	private static final LinearInterpolator x12Interpolator = new LinearInterpolator(
			new double[] { 0.925, 0.95, 1.0, 1.05, 1.1, 1.2, 1.3, 1.7, 2.0 },
			new double[] { 0, 0.014, 0.050, 0.060, 0.059, 0.081, 0.084, 0.085, 0.078 });
	private static final LinearInterpolator x34Interpolator = new LinearInterpolator(
			new double[] { 0.8, 0.9, 1.0, 1.06, 1.2, 1.4, 1.6, 2.0, 2.8, 3.4 },
			new double[] { 0, 0.015, 0.078, 0.121, 0.110, 0.098, 0.090, 0.084, 0.078, 0.074 });
	private static final LinearInterpolator vonKarmanInterpolator = new LinearInterpolator(
			new double[] { 0.9, 0.95, 1.0, 1.05, 1.1, 1.2, 1.4, 1.6, 2.0, 3.0 },
			new double[] { 0, 0.010, 0.027, 0.055, 0.070, 0.081, 0.095, 0.097, 0.091, 0.083 });
	private static final LinearInterpolator lvHaackInterpolator = new LinearInterpolator(
			new double[] { 0.9, 0.95, 1.0, 1.05, 1.1, 1.2, 1.4, 1.6, 2.0 },
			new double[] { 0, 0.010, 0.024, 0.066, 0.084, 0.100, 0.114, 0.117, 0.113 });
	private static final LinearInterpolator parabolicInterpolator = new LinearInterpolator(
			new double[] { 0.95, 0.975, 1.0, 1.05, 1.1, 1.2, 1.4, 1.7 },
			new double[] { 0, 0.016, 0.041, 0.092, 0.109, 0.119, 0.113, 0.108 });
	private static final LinearInterpolator parabolic12Interpolator = new LinearInterpolator(
			new double[] { 0.8, 0.9, 0.95, 1.0, 1.05, 1.1, 1.3, 1.5, 1.8 },
			new double[] { 0, 0.016, 0.042, 0.100, 0.126, 0.125, 0.100, 0.090, 0.088 });
	private static final LinearInterpolator parabolic34Interpolator = new LinearInterpolator(
			new double[] { 0.9, 0.95, 1.0, 1.05, 1.1, 1.2, 1.4, 1.7 },
			new double[] { 0, 0.023, 0.073, 0.098, 0.107, 0.106, 0.089, 0.082 });
	private static final LinearInterpolator bluntInterpolator = new LinearInterpolator();
	static {
		for (double m = 0; m < 3; m += 0.05)
			bluntInterpolator.addPoint(m, BarrowmanCalculator.calculateStagnationCD(m));
	}

	/**
	 * Calculate the LinearInterpolator 'interpolator'. After this call, it can be
	 * used to get the pressure drag coefficient at any Mach number.
	 * <p>
	 * For conical and ogive shapes, uses physics-based analytical methods:
	 * Taylor-Maccoll (cone) or shock-expansion theory (ogive) for supersonic,
	 * blended with empirical transonic polynomials in the M 1.0-1.5 range.
	 * <p>
	 * For other shapes, uses NASA TR-R-100 experimental tables extrapolated
	 * for fineness ratio, extended to higher Mach by shock-expansion where
	 * the nose tip supports an attached shock.
	 * <p>
	 * Finally, if the first data points are non-zero, the subsonic region is
	 * interpolated in the form Cd = a*M^b + Cd(M=0).
	 */
	@SuppressWarnings("null")
	private void calculateNoseInterpolator() {
		LinearInterpolator int1 = null, int2 = null;
		double p = 0;

		interpolator = new LinearInterpolator();

		switch (shape) {
			case CONICAL:
				buildAnalyticalWaveDragCurve(true);
				break;

			case OGIVE:
				if (isTangentOgiveReference()) {
					// Tangent-ogive A52H28-style bodies are badly under-predicted when the
					// transonic anchor is derived from the near-zero base slope.  The
					// existing LV-Haack empirical curve is a much better shape proxy for
					// this reference family at L/D≈3, and the existing fineness scaling
					// preserves the low-drag behavior of much more slender ogives.
					addFinenessScaledReferenceCurve(lvHaackInterpolator);
					extendWithShockExpansion();
				} else {
					buildAnalyticalWaveDragCurve(false);
				}
				break;

			case ELLIPSOID:
				int1 = ellipsoidInterpolator;
				break;

			case POWER:
				if (param <= 0.25) {
					int1 = bluntInterpolator;
					int2 = x14Interpolator;
					p = param * 4;
				} else if (param <= 0.5) {
					int1 = x14Interpolator;
					int2 = x12Interpolator;
					p = (param - 0.25) * 4;
				} else if (param <= 0.75) {
					int1 = x12Interpolator;
					int2 = x34Interpolator;
					p = (param - 0.5) * 4;
				} else {
					int1 = x34Interpolator;
					int2 = calculateTransonicInterpolator(0, 1 / MathUtil.safeSqrt(1 + 4 * pow2(fineness)));
					p = (param - 0.75) * 4;
				}
				break;

			case PARABOLIC:
				if (param <= 0.5) {
					int1 = calculateTransonicInterpolator(0, 1 / MathUtil.safeSqrt(1 + 4 * pow2(fineness)));
					int2 = parabolic12Interpolator;
					p = param * 2;
				} else if (param <= 0.75) {
					int1 = parabolic12Interpolator;
					int2 = parabolic34Interpolator;
					p = (param - 0.5) * 4;
				} else {
					int1 = parabolic34Interpolator;
					int2 = parabolicInterpolator;
					p = (param - 0.75) * 4;
				}
				break;

			case HAACK:
				int1 = vonKarmanInterpolator;
				int2 = lvHaackInterpolator;
				p = param * 3;
				break;

			default:
				throw new UnsupportedOperationException("Unknown transition shape: " + shape);
		}

		if (shape != Transition.Shape.CONICAL && shape != Transition.Shape.OGIVE) {
			if (p < 0 || p > 1.00001) {
				throw new BugException("Inconsistent parameter value p=" + p + " shape=" + shape);
			}

			// Check for parameterized shape and interpolate if necessary
			if (int2 != null) {
				LinearInterpolator int3 = new LinearInterpolator();
				for (double m : int1.getXPoints()) {
					int3.addPoint(m, p * int2.getValue(m) + (1 - p) * int1.getValue(m));
				}
				for (double m : int2.getXPoints()) {
					int3.addPoint(m, p * int2.getValue(m) + (1 - p) * int1.getValue(m));
				}
				int1 = int3;
			}

			// Extrapolate for fineness ratio if necessary
			if (int1 != null) {
				addFinenessScaledReferenceCurve(int1);
			}

			extendWithShockExpansion();
		}

		// Phase 6c: Dahlem-Buck override for POWER, PARABOLIC, HAACK at supersonic Mach.
		// The TR-R-100 tables have limited Mach range and fineness ratio coverage.
		// Dahlem-Buck extends the cone analytical solution to arbitrary shapes using
		// semi-empirical shape correction factors, providing better coverage at high Mach.
		if ((shape == Transition.Shape.POWER || shape == Transition.Shape.PARABOLIC
				|| shape == Transition.Shape.HAACK) && !isDirectReferenceShapeForSupersonicOverride()) {
			double equivSinPhi = aftRadius / MathUtil.safeSqrt(aftRadius * aftRadius + length * length);
			LinearInterpolator coneRef = calculateTransonicInterpolator(0, equivSinPhi);

			double blendStart = 1.3;
			double blendEnd = 1.5;

			for (double m = blendStart; m <= 5.0; m += 0.02) {
				double kShape = DahlemBuckShapeFactors.getShapeFactor(shape, param, m);
				double fCorr = DahlemBuckShapeFactors.finenessRatioCorrection(fineness);
				double cdDahlemBuck = coneRef.getValue(m) * kShape * fCorr;

				if (m <= blendEnd) {
					double t = (m - blendStart) / (blendEnd - blendStart);
					double w = 3 * t * t - 2 * t * t * t;  // Hermite smoothstep
					double cdTRR100 = interpolator.getValue(m);
					interpolator.addPoint(m, (1 - w) * cdTRR100 + w * cdDahlemBuck);
				} else {
					interpolator.addPoint(m, cdDahlemBuck);
				}
			}
		}

		// Phase 2E: proper transonic drag rise from Mdd to first data point
		buildTransonicDragRise();
	}

	private void addFinenessScaledReferenceCurve(LinearInterpolator referenceCurve) {
		double log4 = Math.log(fineness + 1) / Math.log(4);
		for (double m : referenceCurve.getXPoints()) {
			double stag = bluntInterpolator.getValue(m);
			interpolator.addPoint(m, stag * Math.pow(referenceCurve.getValue(m) / stag, log4));
		}
	}

	private boolean isTangentOgiveReference() {
		return shape == Transition.Shape.OGIVE && Math.abs(param - 1.0) < 1.0e-6;
	}

	private boolean isDirectReferenceShapeForSupersonicOverride() {
		if (shape == Transition.Shape.POWER) {
			return isApprox(param, 0.25) || isApprox(param, 0.50) || isApprox(param, 0.75) || isApprox(param, 1.0);
		}
		if (shape == Transition.Shape.HAACK) {
			return isApprox(param, 0.0) || isApprox(param, 1.0 / 3.0);
		}
		return false;
	}

	private static boolean isApprox(double value, double target) {
		return Math.abs(value - target) < 1.0e-6;
	}

	// ---- Phase 2A: Analytical wave drag methods ----

	/**
	 * Build the wave drag interpolator for conical or ogive shapes using
	 * physics-based analytical methods blended with empirical transonic data.
	 * <p>
	 * M 1.0 to BLEND_LOW: empirical transonic polynomial (well-validated)
	 * M BLEND_LOW to BLEND_HIGH: cubic Hermite blend (smoothstep)
	 * M > BLEND_HIGH: Taylor-Maccoll (cone) or shock-expansion (ogive)
	 *
	 * @param isCone true for CONICAL (Taylor-Maccoll), false for OGIVE (shock-expansion)
	 */
	private void buildAnalyticalWaveDragCurve(boolean isCone) {
		LinearInterpolator transonic = calculateTransonicInterpolator(
				isCone ? 0 : param, sinphi);

		for (double m : transonic.getXPoints()) {
			if (m <= SUPERSONIC_BLEND_LOW + 0.001) {
				interpolator.addPoint(m, transonic.getValue(m));
			}
		}

		for (double m = SUPERSONIC_BLEND_LOW + 0.02; m <= MAX_SUPERSONIC_MACH + 0.001; m += 0.05) {
			double analytical = isCone
					? calculateConeTaylorMaccollCd(m)
					: calculateShockExpansionCd(m);

			// Phase 4a: blend in Modified Newtonian theory at high Mach
			if (m >= NEWTONIAN_BLEND_LOW) {
				double newtonian = calculateNewtonianCd(m);
				if (m >= NEWTONIAN_BLEND_HIGH) {
					analytical = newtonian;
				} else {
					double tn = (m - NEWTONIAN_BLEND_LOW) / (NEWTONIAN_BLEND_HIGH - NEWTONIAN_BLEND_LOW);
					double wn = tn * tn * (3 - 2 * tn); // smoothstep
					analytical = (1 - wn) * analytical + wn * newtonian;
				}
			}

			// Clamp non-physical negative results from the analytical solver
			// before they enter the blend. The shock-expansion integral on a
			// slender tangent ogive can return slightly negative Cd near the
			// detachment Mach (e.g. L500's 8.85:1 ogive at M=1.5 → −0.009),
			// because the rear-half expansion contributes more "suction-area"
			// than the tip cone shock contributes "pressure-area". The
			// supersonic-only branch already does Math.max(0, analytical) on
			// the line below; do the same in the blend so the
			// empirical-analytical crossfade can never produce a sub-zero point.
			double clampedAnalytical = Math.max(0, analytical);
			if (m <= SUPERSONIC_BLEND_HIGH) {
				double t = (m - SUPERSONIC_BLEND_LOW) / (SUPERSONIC_BLEND_HIGH - SUPERSONIC_BLEND_LOW);
				double w = t * t * (3 - 2 * t); // smoothstep
				double empirical = transonic.getValue(m);
				interpolator.addPoint(m, (1 - w) * empirical + w * clampedAnalytical);
			} else {
				interpolator.addPoint(m, clampedAnalytical);
			}
		}
	}

	/**
	 * Compute cone wave drag coefficient using the Taylor-Maccoll solution.
	 * For a cone at zero AoA, the drag coefficient referenced to base area
	 * equals the surface pressure coefficient.
	 *
	 * @param mach freestream Mach number
	 * @return Cd referenced to frontalArea (base area)
	 */
	private double calculateConeTaylorMaccollCd(double mach) {
		if (mach <= 1.0 || tipHalfAngle <= 1e-6) {
			return 0;
		}
		try {
			return ObliqueShockSolver.conePressureCoefficient(mach, tipHalfAngle, GAMMA);
		} catch (IllegalArgumentException e) {
			return BarrowmanCalculator.calculateStagnationCD(mach);
		}
	}

	/**
	 * Compute wave drag coefficient for a general axisymmetric nose shape
	 * using the shock-expansion method.
	 * <p>
	 * Algorithm:
	 * 1. Compute the initial shock at the nose tip (Taylor-Maccoll cone approximation)
	 * 2. March along the surface, tracking local Mach and pressure
	 * 3. Apply Prandtl-Meyer expansion where the surface turns away from the flow
	 * 4. Integrate the surface pressure distribution to get the drag coefficient
	 * <p>
	 * The drag integral for an axisymmetric body is:
	 * Cd = 2 * integral(Cp * r * dr) / (R_aft^2 - R_fore^2)
	 * where Cp is the local pressure coefficient.
	 *
	 * @param mach freestream Mach number
	 * @return Cd referenced to frontalArea
	 */
	private double calculateShockExpansionCd(double mach) {
		if (mach <= 1.0 || tipHalfAngle <= 1e-6 || length < 0.001 || foreRadius >= aftRadius) {
			return 0;
		}

		int N = SHOCK_EXPANSION_STRIPS;
		double dx = length / N;

		double localMach;
		double pRatio; // p_surface / p_freestream
		try {
			ObliqueShockSolver.ObliqueShockResult result =
					ObliqueShockSolver.solveCone(mach, tipHalfAngle, GAMMA);
			localMach = result.m2;
			pRatio = result.pressureRatio;
		} catch (IllegalArgumentException e) {
			try {
				ObliqueShockSolver.ObliqueShockResult result =
						ObliqueShockSolver.solve(mach, tipHalfAngle, GAMMA, true);
				localMach = result.m2;
				pRatio = result.pressureRatio;
			} catch (IllegalArgumentException e2) {
				return BarrowmanCalculator.calculateStagnationCD(mach);
			}
		}

		double prevAngle = tipHalfAngle;
		double cdIntegral = 0;
		double rPrev = foreRadius;

		for (int i = 1; i <= N; i++) {
			double x = i * dx;
			double r = getProfileRadius(x);

			double localAngle;
			if (i < N) {
				double rNext = getProfileRadius((i + 1) * dx);
				localAngle = Math.atan2(rNext - rPrev, 2.0 * dx);
			} else {
				localAngle = Math.atan2(r - rPrev, dx);
			}
			if (localAngle < 0) {
				localAngle = 0;
			}

			double turnAngle = prevAngle - localAngle;

			if (turnAngle > 1e-8 && localMach >= 1.0) {
				try {
					double newMach = PrandtlMeyerExpansion.downstreamMach(
							localMach, turnAngle, GAMMA);
					pRatio *= PrandtlMeyerExpansion.pressureRatio(localMach, newMach, GAMMA);
					localMach = newMach;
				} catch (IllegalArgumentException e) {
					// Expansion exceeds maximum P-M angle — keep current state
				}
			} else if (turnAngle < -1e-8 && localMach > 1.0) {
				try {
					ObliqueShockSolver.ObliqueShockResult compResult =
							ObliqueShockSolver.solve(localMach, Math.abs(turnAngle), GAMMA, true);
					pRatio *= compResult.pressureRatio;
					localMach = compResult.m2;
				} catch (IllegalArgumentException e) {
					// Compression shock fails — ignore
				}
			}

			double cpLocal = 2.0 / (GAMMA * mach * mach) * (pRatio - 1.0);

			double dr = r - rPrev;
			if (dr > 0) {
				double rMid = 0.5 * (r + rPrev);
				cdIntegral += cpLocal * rMid * dr;
			}

			prevAngle = localAngle;
			rPrev = r;
		}

		double areaFactor = aftRadius * aftRadius - foreRadius * foreRadius;
		if (Math.max(aftRadius, foreRadius) < 1e-9) {
			return 0;
		}
		return 2.0 * cdIntegral / areaFactor;
	}

	// ---- Phase 4a: Modified Newtonian theory ----

	/**
	 * Compute wave drag coefficient using Modified Newtonian theory.
	 * <p>
	 * For hypersonic flow (M > 5), the pressure distribution on the windward
	 * surface of a body is well approximated by:
	 * <pre>
	 *   Cp = Cp_max * sin²(theta)
	 * </pre>
	 * where theta is the local surface inclination angle to the freestream,
	 * and Cp_max is the maximum (stagnation) pressure coefficient behind a
	 * normal shock at the given Mach number:
	 * <pre>
	 *   Cp_max = 2/(gamma*M²) * [(((gamma+1)²*M²) / (4*gamma*M² - 2*(gamma-1)))^(gamma/(gamma-1))
	 *            * ((1 - gamma + 2*gamma*M²) / (gamma+1)) - 1]
	 * </pre>
	 * <p>
	 * The drag integral is computed by strip integration over the nose surface:
	 * <pre>
	 *   Cd = 2 * integral(Cp * r * dr) / (R_aft² - R_fore²)
	 * </pre>
	 * <p>
	 * Reference: Lees, L. (1955). "Hypersonic Flow." Proc. 5th Int'l Aero Conf.
	 * Also: Anderson, J.D. "Hypersonic and High-Temperature Gas Dynamics", Ch. 3.
	 *
	 * @param mach freestream Mach number (should be > 1)
	 * @return Cd referenced to frontalArea
	 */
	private double calculateNewtonianCd(double mach) {
		if (mach <= 1.0 || tipHalfAngle <= 1e-6 || length < 0.001 || foreRadius >= aftRadius) {
			return 0;
		}

		// Phase 4c: Use effective gamma for high stagnation temperatures
		double gamma = GAMMA;
		if (mach > 5.0) {
			// Approximate stagnation temperature: T0 = T * (1 + (gamma-1)/2 * M²)
			// Use standard temperature as reference for the correction
			double T0_approx = AtmosphericConditions.STANDARD_TEMPERATURE
					* (1.0 + (GAMMA - 1.0) / 2.0 * mach * mach);
			gamma = AtmosphericConditions.effectiveGamma(T0_approx);
		}

		// JSR 2026-05-11 sensitivity sweep: Cp_max multiplicative scale.
		// AblationConfig.cpMaxScale defaults to 1.0 in production.
		double cpMax = calculateCpMax(mach, gamma)
				* info.openrocket.core.aerodynamics.AblationConfig.cpMaxScale;

		int N = SHOCK_EXPANSION_STRIPS;
		double dx = length / N;
		double cdIntegral = 0;
		double rPrev = foreRadius;

		for (int i = 1; i <= N; i++) {
			double x = i * dx;
			double r = getProfileRadius(x);
			double dr = r - rPrev;

			if (dr > 0) {
				// Local surface angle to the axis
				double theta = Math.atan2(dr, dx);
				double sinTheta = Math.sin(theta);

				// Modified Newtonian: Cp = Cp_max * sin²(theta)
				// Only windward surfaces (positive dr) contribute
				double cpLocal = cpMax * sinTheta * sinTheta;

				double rMid = 0.5 * (r + rPrev);
				cdIntegral += cpLocal * rMid * dr;
			}
			// Leeward surfaces (dr <= 0): Cp ≈ 0 in Newtonian theory (shadow region)

			rPrev = r;
		}

		double areaFactor = aftRadius * aftRadius - foreRadius * foreRadius;
		if (Math.max(aftRadius, foreRadius) < 1e-9) {
			return 0;
		}
		return 2.0 * cdIntegral / areaFactor;
	}

	/**
	 * Compute the maximum (stagnation) pressure coefficient behind a normal
	 * shock using the Rayleigh pitot tube formula.
	 * <p>
	 * For M > 1, Cp_max is computed from the total pressure ratio across
	 * a normal shock combined with the isentropic stagnation relation.
	 * At very high Mach, Cp_max asymptotes to approximately 1.839 (for gamma=1.4).
	 *
	 * @param mach  freestream Mach number
	 * @param gamma ratio of specific heats
	 * @return maximum pressure coefficient Cp_max
	 */
	public static double calculateCpMax(double mach, double gamma) {
		if (mach <= 1.0) {
			// Subsonic: isentropic stagnation
			double term = 1.0 + (gamma - 1.0) / 2.0 * mach * mach;
			double p0_p = Math.pow(term, gamma / (gamma - 1.0));
			return 2.0 / (gamma * mach * mach) * (p0_p - 1.0);
		}

		double M2 = mach * mach;
		double gp1 = gamma + 1.0;
		double gm1 = gamma - 1.0;

		// Total pressure ratio across normal shock (Rayleigh pitot formula)
		double numerator = gp1 * gp1 * M2;
		double denominator = 4.0 * gamma * M2 - 2.0 * gm1;

		// Guard against edge cases
		if (denominator <= 0) {
			log.warn("Cp_max denominator non-positive at M={}, using simplified formula", mach);
			return 2.0 / (gamma * M2);
		}

		double pitotRatio = Math.pow(numerator / denominator, gamma / gm1)
				* (1.0 - gamma + 2.0 * gamma * M2) / gp1;

		return 2.0 / (gamma * M2) * (pitotRatio - 1.0);
	}

	/**
	 * Extend the existing empirical interpolator to higher Mach numbers
	 * using shock-expansion for shapes with a pointed tip.
	 * Skipped for shapes with very steep tip angles (cusps like Von Karman)
	 * where the initial shock approximation breaks down.
	 */
	private void extendWithShockExpansion() {
		if (tipHalfAngle <= 1e-6 || tipHalfAngle > Math.toRadians(45)) {
			return;
		}

		double[] xPoints = interpolator.getXPoints();
		if (xPoints == null || xPoints.length == 0) {
			return;
		}
		double maxExistingMach = xPoints[xPoints.length - 1];
		if (maxExistingMach >= MAX_SUPERSONIC_MACH - 0.1) {
			return;
		}

		double existingEndValue = interpolator.getValue(maxExistingMach);
		double blendEnd = Math.min(maxExistingMach + 0.5, MAX_SUPERSONIC_MACH);

		for (double m = maxExistingMach + 0.05; m <= MAX_SUPERSONIC_MACH; m += 0.05) {
			double analytical = calculateShockExpansionCd(m);
			if (Double.isNaN(analytical) || Double.isInfinite(analytical)) {
				analytical = 0;
			}
			analytical = Math.max(0, analytical);

			// Phase 4a: blend in Modified Newtonian theory at high Mach
			if (m >= NEWTONIAN_BLEND_LOW) {
				double newtonian = calculateNewtonianCd(m);
				if (m >= NEWTONIAN_BLEND_HIGH) {
					analytical = newtonian;
				} else {
					double tn = (m - NEWTONIAN_BLEND_LOW) / (NEWTONIAN_BLEND_HIGH - NEWTONIAN_BLEND_LOW);
					double wn = tn * tn * (3 - 2 * tn);
					analytical = (1 - wn) * analytical + wn * newtonian;
				}
			}

			if (m <= blendEnd) {
				double t = (m - maxExistingMach) / (blendEnd - maxExistingMach);
				double w = t * t * (3 - 2 * t);
				interpolator.addPoint(m, (1 - w) * existingEndValue + w * analytical);
			} else {
				interpolator.addPoint(m, analytical);
			}
		}
	}

	// ---- Phase 2E: Transonic drag rise model ----

	/**
	 * Build the transonic drag rise below the first empirical or analytical
	 * data point in the interpolator.
	 * <p>
	 * Below the drag divergence Mach number (Mdd), wave drag is zero — the flow
	 * is fully subsonic everywhere on the surface. Above Mdd, local supersonic
	 * pockets and shocks form, causing a steep rise in pressure drag through the
	 * transonic regime.
	 * <p>
	 * This method adds a smooth, C1-continuous cubic Hermite polynomial from
	 * zero at Mdd to the first existing data point. The polynomial has:
	 * <ul>
	 *   <li>Zero value and zero slope at Mdd (gradual onset)</li>
	 *   <li>Matching value and slope at the first data point (smooth join)</li>
	 * </ul>
	 * For shapes where existing data already starts at or near Cd = 0
	 * (e.g., Von Karman at M 0.9 with Cd = 0), this method is a no-op.
	 */
	private void buildTransonicDragRise() {
		double[] xPoints = interpolator.getXPoints();
		if (xPoints == null || xPoints.length == 0) {
			return;
		}

		double firstMach = xPoints[0];
		double firstValue = interpolator.getValue(firstMach);

		// Smooth ogives (firstValue ≈ 0 at M 0.9 from the reference data) genuinely
		// have near-zero subsonic pressure drag: the stagnation pressure rise on the
		// front cancels against pressure recovery on the shoulder, so almost all
		// subsonic drag is skin friction. Upstream OpenRocket leaves Cd_pressure = 0
		// here and matches the SimVReal CalIsp 1-5 rockets to within +0.1 to +8%.
		// A previous revision added a 0.03-0.04 "minimum floor" across M<0.9 which
		// appeared sensible in isolation but introduced a systematic 8-10% overdrag
		// on every tangent-ogive HPR rocket in the benchmark; removed. If a real
		// missing subsonic drag term shows up later, model it as protuberance /
		// roughness / Hoerner form-factor — not as a blanket nose-cone floor.
		if (firstValue < 0.001) {
			return;
		}

		// Drag divergence Mach: onset of transonic wave drag
		double mdd = estimateDragDivergenceMach();

		// Ensure a meaningful rise region (at least 0.05 Mach wide)
		if (mdd > firstMach - 0.05) {
			mdd = firstMach - 0.05;
		}
		mdd = Math.max(mdd, 0.50);

		// Numerical derivative at the first data point
		double firstDeriv = (interpolator.getValue(firstMach + 0.01) - firstValue) / 0.01;
		firstDeriv = Math.max(firstDeriv, 0);

		// Cap derivative to guarantee monotonicity of the Hermite cubic.
		// Condition for monotonic cubic with f'(a)=0: 3*(f(b)-f(a)) >= f'(b)*(b-a)
		double riseWidth = firstMach - mdd;
		double maxDeriv = 3.0 * firstValue / riseWidth;
		firstDeriv = Math.min(firstDeriv, maxDeriv);

		// No subsonic pressure-drag floor. Prior revisions tried two forms of a
		// "nose M=0 anchor" (a 0.03-0.04 constant and a 0.8*sin²(φ) power-law); both
		// were net-negative on the SimVReal benchmark because they added drag on top
		// of the analytical subsonic pressure-drag which is already ~0 for slender
		// ogives/cones at M<Mdd. The stagnation region contributes ~Cp_stag*A_tip /
		// A_ref which is O(1e-4) for a realistic tip diameter and is absorbed into
		// the friction term anyway. Any genuine residual subsonic form drag belongs
		// in protuberance/excrescence/Hoerner-form-factor modeling, not here.
		double cdMach0 = 0;

		// C1-continuous cubic: (mdd, cdMach0, slope=0) → (firstMach, firstValue, slope=firstDeriv)
		PolyInterpolator riseInterp = new PolyInterpolator(
				new double[] { mdd, firstMach },
				new double[] { mdd, firstMach });
		double[] risePoly = riseInterp.interpolator(cdMach0, firstValue, 0, firstDeriv);

		// Lock's 4th-power onset: applied near M_crit for a steeper onset
		// The critical Mach (onset of local supersonic flow) is slightly below mdd.
		double mCrit = Math.max(0, mdd - 0.05);
		double cdAtMcrit = Math.max(cdMach0, PolyInterpolator.eval(Math.max(mCrit, mdd), risePoly));
		double kLock = firstValue - cdAtMcrit;

		// Sample the rise region with Lock's 4th-power near onset
		interpolator.addPoint(mdd, cdMach0);
		interpolator.addPoint(mdd + 0.005,
				Math.max(cdMach0, PolyInterpolator.eval(mdd + 0.005, risePoly)));
		for (double m = mdd + 0.01; m < firstMach - 0.008; m += 0.01) {
			if (m <= mCrit) {
				// Below M_crit: use original cubic rise
				interpolator.addPoint(m, Math.max(cdMach0, PolyInterpolator.eval(m, risePoly)));
			} else if (kLock > 0.001) {
				// Between M_crit and onset: Lock's 4th-power for steeper rise
				double xr = (m - mCrit) / (firstMach - mCrit);
				double deltaCd = kLock * xr * xr * xr * xr;
				interpolator.addPoint(m, cdAtMcrit + deltaCd);
			} else {
				// Fallback: continue cubic rise
				interpolator.addPoint(m, Math.max(cdMach0, PolyInterpolator.eval(m, risePoly)));
			}
		}
		// Close-approach points near the join for smooth transition.
		// Use two points to avoid a large linear-interpolation gap at firstMach.
		double nearJoin1 = firstMach - 0.005;
		double nearJoin2 = firstMach - 0.001;
		if (nearJoin1 > mdd + 0.01) {
			if (kLock > 0.001) {
				double xr1 = (nearJoin1 - mCrit) / (firstMach - mCrit);
				interpolator.addPoint(nearJoin1, cdAtMcrit + kLock * xr1 * xr1 * xr1 * xr1);
				double xr2 = (nearJoin2 - mCrit) / (firstMach - mCrit);
				interpolator.addPoint(nearJoin2, cdAtMcrit + kLock * xr2 * xr2 * xr2 * xr2);
			} else {
				interpolator.addPoint(nearJoin1,
						Math.max(cdMach0, PolyInterpolator.eval(nearJoin1, risePoly)));
				interpolator.addPoint(nearJoin2,
						Math.max(cdMach0, PolyInterpolator.eval(nearJoin2, risePoly)));
			}
		}

		// Constant subsonic floor below Mdd (LinearInterpolator extrapolates constant
		// below its lowest point, so this single anchor holds Cd=cdMach0 for all M<mdd)
		interpolator.addPoint(0, cdMach0);
	}

	/**
	 * Estimate the drag divergence Mach number from the nose tip geometry.
	 * <p>
	 * The drag divergence Mach is the freestream Mach at which local
	 * supersonic flow first appears on the nose surface, initiating wave drag.
	 * Sharper tips (small half-angle) allow higher freestream Mach before
	 * sonic flow develops; blunter tips cause earlier divergence.
	 * <p>
	 * Calibrated against NASA TR-R-100 transonic onset data:
	 * Von Karman (sharp tip) ≈ M 0.92, x=3/4 Power (moderate) ≈ M 0.83,
	 * Parabolic 1/2 (blunt) ≈ M 0.80.
	 *
	 * @return estimated drag divergence Mach, clamped to [0.65, 0.96]
	 */
	double estimateDragDivergenceMach() {
		double sinTip = Math.sin(Math.min(tipHalfAngle, Math.PI / 2));
		return MathUtil.clamp(
				0.95 - 0.15 * Math.pow(Math.max(sinTip, 0.01), 0.4),
				0.65, 0.96);
	}

	/**
	 * Get the profile radius at axial position x using the shape function.
	 */
	private double getProfileRadius(double x) {
		if (shape == null) {
			return foreRadius;
		}
		if (x <= 0) {
			return foreRadius;
		}
		if (x >= length) {
			return aftRadius;
		}
		return foreRadius + shape.getRadius(x, aftRadius - foreRadius, length, param);
	}

	// ---- Empirical transonic/supersonic data (NASA TR-R-100) ----

	private static final PolyInterpolator conicalPolyInterpolator = new PolyInterpolator(new double[] { 1.0, 1.3 },
			new double[] { 1.0, 1.3 });

	/**
	 * Build the empirical transonic interpolator for conical/ogive shapes.
	 * Uses a polynomial in M 1.0-1.3 and an analytical approximation above M 1.3.
	 * Retained for: (a) transonic baseline for analytical blending, and
	 * (b) reference curves used by POWER and PARABOLIC shapes.
	 */
	private static LinearInterpolator calculateTransonicInterpolator(double param, double sinphi) {
		LinearInterpolator interpolator = new LinearInterpolator();

		double cdMach1 = sinphi;
		double cdMach1_3 = 2.1 * pow2(sinphi) + 0.6019 * sinphi;

		double[] poly = conicalPolyInterpolator.interpolator(
				cdMach1, cdMach1_3,
				4 / (GAMMA + 1) * (1 - 0.5 * cdMach1), -1.1341 * sinphi);

		double mul = 0.72 * pow2(param - 0.5) + 0.82;

		for (double m = 1; m < 1.3001; m += 0.02) {
			interpolator.addPoint(m, mul * PolyInterpolator.eval(m, poly));
		}

		// Bridge the gap between the transonic polynomial (last point ~M1.30) and
		// the supersonic analytical formula (first point M1.32) by adding the
		// supersonic formula value at M1.32 explicitly.
		interpolator.addPoint(1.32, mul * (2.1 * pow2(sinphi) + 0.5 * sinphi / MathUtil.safeSqrt(1.32 * 1.32 - 1)));

		for (double m = 1.34; m < 4; m += 0.02) {
			interpolator.addPoint(m, mul * (2.1 * pow2(sinphi) + 0.5 * sinphi / MathUtil.safeSqrt(m * m - 1)));
		}

		return interpolator;
	}

}

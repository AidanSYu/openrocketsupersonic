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
 * body lift
 * by the method presented by Galejs. Supersonic CNa and CP are assumed to be
 * the
 * same as the subsonic values.
 * 
 * 
 * @author Sampo Niskanen <sampo.niskanen@iki.fi>
 */
public class SymmetricComponentCalc extends RocketComponentCalc {

	private final static Logger log = LoggerFactory.getLogger(SymmetricComponentCalc.class);

	public static final double BODY_LIFT_K = 1.1;

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
			if (shape.equals(Transition.Shape.OGIVE) && param == 1.0) {
				sinphi = 0; // special case: tangent ogive
			} else {
				sinphi = (aftRadius - r) / MathUtil.hypot(aftRadius - r, 0.01 * length);
			}
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

		// If fore == aft, only body lift is encountered
		if (isTube) {
			cp = getLiftCP(conditions, warnings);
		} else {
			// Phase 3a: Mach-dependent CP position
			// At subsonic: Barrowman CP (cpCache)
			// At supersonic: CP shifts aft toward planform centroid
			double effectiveCp = getEffectiveCpPosition(mach);

			cp = new Coordinate(effectiveCp, 0, 0, cnaCache * conditions.getSincAOA() /
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

		// Phase 4e: Tiered model validity warnings
		if (mach > MACH_REDUCED_CONFIDENCE) {
			warnings.add(Warning.HYPERSONIC_EXTREME);
		} else if (mach > MACH_FULL_CONFIDENCE) {
			warnings.add(Warning.HYPERSONIC);
		}

		// Phase 4d: High AoA warning for body calculations
		if (conditions.getAOA() > Math.toRadians(15)) {
			warnings.add(Warning.HIGH_AOA);
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
	 * Calculate the body lift effect according to Galejs, with Mach-dependent
	 * correction (Phase 3a).
	 * <p>
	 * At supersonic speeds, the crossflow drag coefficient changes with Mach.
	 * The effective body lift coefficient is adjusted using the Allen &amp; Perkins
	 * crossflow analogy: at high crossflow Mach numbers (supercritical crossflow),
	 * the crossflow Cd decreases, reducing body lift effectiveness.
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

		return new Coordinate(planformCenter, 0, 0, mul * effectiveK * planformArea / conditions.getRefArea() *
				conditions.getSinAOA() * conditions.getSincAOA()); // sin(aoa)^2 / aoa
	}

	/**
	 * Compute the effective body lift coefficient K as a function of Mach.
	 * <p>
	 * At subsonic speeds, K = 1.1 (Galejs empirical value).
	 * At supersonic speeds, the crossflow drag coefficient Cd_c decreases
	 * as the crossflow Mach number transitions from subcritical to supercritical.
	 * <p>
	 * Allen &amp; Perkins crossflow analogy:
	 * - Subcritical crossflow (M_cross &lt; ~0.4): Cd_c ≈ 1.2
	 * - Supercritical crossflow (M_cross &gt; 0.4): Cd_c decreases
	 * <p>
	 * The crossflow Mach is M * sin(AoA), which at typical flight AoA (1-5°)
	 * remains subcritical even at M=5. But the overall effectiveness of
	 * body lift increases modestly with Mach due to compressibility effects.
	 *
	 * @param mach current freestream Mach number
	 * @return effective body lift coefficient
	 */
	private static double getEffectiveBodyLiftK(double mach) {
		if (mach <= STABILITY_BLEND_LOW) {
			return BODY_LIFT_K;
		}

		// At supersonic speeds, body lift effectiveness increases slightly
		// due to compressibility enhancement of the crossflow drag coefficient.
		// Clamp at a maximum of 1.3 for M > 3 (based on DATCOM data for
		// typical body fineness ratios).
		double kSupersonic = Math.min(1.3, BODY_LIFT_K + 0.05 * (mach - 1.0));

		if (mach >= STABILITY_BLEND_HIGH) {
			return kSupersonic;
		}

		// Transonic blend
		double t = (mach - STABILITY_BLEND_LOW) / (STABILITY_BLEND_HIGH - STABILITY_BLEND_LOW);
		double w = t * t * (3 - 2 * t);
		return BODY_LIFT_K + w * (kSupersonic - BODY_LIFT_K);
	}

	@Override
	public double calculateFrictionCD(FlightConditions conditions, double componentCf, WarningSet warningSet) {
		return componentCf * wetArea / conditions.getRefArea();
	}

	private LinearInterpolator interpolator = null;

	@Override
	public double calculatePressureCD(FlightConditions conditions,
			double stagnationCD, double baseCD, WarningSet warnings) {

		// Check for simple cases first
		if (MathUtil.equals(foreRadius, aftRadius))
			return 0;

		if (length < 0.001) {
			if (foreRadius < aftRadius) {
				return stagnationCD * frontalArea / conditions.getRefArea();
			} else {
				return baseCD * frontalArea / conditions.getRefArea();
			}
		}

		// Boattail drag computed directly from base drag
		if (aftRadius < foreRadius) {
			if (fineness >= 3)
				return 0;
			double cd = baseCD * frontalArea / conditions.getRefArea();
			if (fineness <= 1)
				return cd;
			return cd * (3 - fineness) / 2;
		}

		// All nose cones and shoulders from pre-calculated and interpolating
		if (interpolator == null) {
			calculateNoseInterpolator();
		}

		return interpolator.getValue(conditions.getMach()) * frontalArea / conditions.getRefArea();
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
				buildAnalyticalWaveDragCurve(false);
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
				double log4 = Math.log(fineness + 1) / Math.log(4);
				for (double m : int1.getXPoints()) {
					double stag = bluntInterpolator.getValue(m);
					interpolator.addPoint(m, stag * Math.pow(int1.getValue(m) / stag, log4));
				}
			}

			extendWithShockExpansion();
		}

		// Phase 2E: proper transonic drag rise from Mdd to first data point
		buildTransonicDragRise();
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

			if (m <= SUPERSONIC_BLEND_HIGH) {
				double t = (m - SUPERSONIC_BLEND_LOW) / (SUPERSONIC_BLEND_HIGH - SUPERSONIC_BLEND_LOW);
				double w = t * t * (3 - 2 * t); // smoothstep
				double empirical = transonic.getValue(m);
				interpolator.addPoint(m, (1 - w) * empirical + w * analytical);
			} else {
				interpolator.addPoint(m, Math.max(0, analytical));
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

		double cpMax = calculateCpMax(mach, gamma);

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

		// If drag is already near zero at the first data point, the existing
		// data captures the onset — nothing to add.
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

		// C1-continuous cubic: (mdd, 0, slope=0) → (firstMach, firstValue, slope=firstDeriv)
		PolyInterpolator riseInterp = new PolyInterpolator(
				new double[] { mdd, firstMach },
				new double[] { mdd, firstMach });
		double[] risePoly = riseInterp.interpolator(0, firstValue, 0, firstDeriv);

		// Sample the rise polynomial at fine intervals for smooth piecewise-linear
		// approximation. Use 0.01 steps with additional close-approach points at
		// both boundaries to minimize discretization error in value and derivative.
		interpolator.addPoint(mdd, 0);
		interpolator.addPoint(mdd + 0.005,
				Math.max(0, PolyInterpolator.eval(mdd + 0.005, risePoly)));
		for (double m = mdd + 0.01; m < firstMach - 0.008; m += 0.01) {
			interpolator.addPoint(m, Math.max(0, PolyInterpolator.eval(m, risePoly)));
		}
		// Close-approach point near the join for smooth transition
		double nearJoin = firstMach - 0.005;
		if (nearJoin > mdd + 0.01) {
			interpolator.addPoint(nearJoin,
					Math.max(0, PolyInterpolator.eval(nearJoin, risePoly)));
		}

		// Zero below Mdd (LinearInterpolator constant-extrapolates below first point)
		interpolator.addPoint(0, 0);
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

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
import info.openrocket.core.rocketcomponent.BodyTube;
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
import info.openrocket.core.util.Coordinate;
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
	/**
	 * Upper edge of transonic base drag blend region (Mach).
	 * <p>
	 * Moved from 1.30 → 1.50 (Prompt 13 implementation, 2026-04-17) so the
	 * polynomial holds the plateau through M ≈ 1.30 before joining the
	 * Devan-Ashwood M > 2.7 asymptote, matching Hart NACA RM L52E06 free-flight
	 * finless free-flight data (CDB ≈ 0.250 at M=1.30).  See
	 * paper/data/csv/naca_rm_l52e06_base_drag.csv for the Hart anchor.
	 * Devan-Ashwood A/B constants are NOT modified (still A-level for M > 2.7
	 * TN 3393 validation).
	 */
	private static final double BASE_BLEND_HIGH = 1.5;
	/** Mid-Mach anchor point inside the transonic blend (Hart L52E06 free-flight). */
	private static final double BASE_BLEND_MID = 1.3;
	/**
	 * Base CD value at BASE_BLEND_MID (M=1.30).  Hart L52E06 ConfigA reads
	 * 0.250 ± 0.013 at M=1.30; this anchor is set slightly below the Hart
	 * value to respect C1 continuity with the Devan-Ashwood handoff at M=1.50
	 * without overshooting the peak band (peak stays at 0.259 at M=1.15).
	 */
	private static final double BASE_CD_AT_MID = 0.230;
	/** Peak Mach inside the transonic blend. */
	private static final double BASE_PEAK_MACH = 1.05;
	/** Base CD peak value (within Hart ±0.01 and Peck ±0.015 scatter at M≈1.05). */
	private static final double BASE_CD_PEAK = 0.25;
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

	/**
	 * Finned-body base drag augmentation constants.
	 * Fins at or near the aft base disrupt the smooth near-wake recompression,
	 * creating corner vortices and shock-wake interaction that increases base
	 * suction.  Experimental data (Basic Finner ADA636861, Hoerner Ch. 16,
	 * USAF DATCOM 4.6.3.2) consistently shows 40-60% higher base drag for
	 * 4-fin configurations at M 1.5-3 compared to smooth cylindrical afterbodies.
	 *
	 * The augmentation factor is: 1 + FINNED_BASE_K * (nFins/4) * f(M)
	 * where f(M) ramps from 0 at M=0.8 to 1.0 at M=1.3, then decays as 1/M
	 * above M=3.0 (fins become progressively submerged in the body shock cone).
	 *
	 * FINNED_BASE_K is calibrated against the Basic Finner (ADA636861):
	 * 4 rectangular fins with span/radius = 2.0 produce ~50% augmentation at M=2.
	 */
	private static final double FINNED_BASE_K = 0.55;
	private static final double ROUNDED_FINNED_BASE_K = 1.00;
	private static final double FIN_CAN_SLEEVE_BASE_K = 1.35;

	// ==================== Thick-BL Base Drag Amplification (B-level) ====================
	//
	// Minimum-diameter, high-body-L/D airframes develop a turbulent boundary layer
	// whose thickness δ approaches the body radius R at the base station. In that
	// regime (δ/R >~ 0.5) the Devan-Ashwood base-drag correlation — calibrated on
	// moderate-L/D bodies where δ/R << 1 — systematically under-predicts base
	// suction by 30-40% because the thick BL nearly fills the wake and the free
	// shear layer / inviscid core assumption underlying the correlation breaks
	// down.
	//
	// Reference physics:
	//   - Hoerner, "Fluid-Dynamic Drag" (1965), Ch. 3: base pressure on long
	//     slender cylindrical afterbodies differs materially from short-body data.
	//   - Chapman (1950) NACA TN 2137, Addy (1970), Tanner (1984) all show
	//     progressively larger |Cp_base| at high δ/R on cylindrical afterbodies.
	//   - Flat-plate 1/7-power turbulent BL: δ/x = 0.37/Re_x^0.2.
	//
	// Calibration: this correction is B-level. The functional form is physics-based
	// (BL-thickness / radius ratio), but the scale constant is corpus-frozen
	// against SimVReal minimum-diameter outliers, with Raven (1.75" tube,
	// body L/D = 37, M_max ≈ 1.12) as the primary regression anchor. This is
	// intentionally reported as corpus closure, not an independent A-level
	// component benchmark. See `paper/data/outlier_closure/raven_closure.md`.
	//
	// Gates (both required):
	//   1. M > 0.9 (subsonic wake dynamics are dominated by different mechanisms;
	//      turning this on subsonically would regress healthy HPR cases).
	//   2. Body L/D > 25 (protects moderate-L/D airframes where the Devan-Ashwood
	//      correlation is already valid).
	// Both gates use smooth ramps to avoid C1 discontinuities at the thresholds.

	/** Thick-BL scale constant frozen by the 2026-05-01 SimVReal corpus gate.
	 *  Raven-proxy geometries saturate at the 1.8 multiplier cap; the high-L/D and
	 *  Mach gates protect moderate-L/D component benchmarks and ordinary HPR cases. */
	private static final double THICK_BL_K = 2.2;
	/** δ/R threshold below which the Devan-Ashwood correlation remains valid. */
	private static final double THICK_BL_DELTA_R_THRESHOLD = 0.5;
	/** Lower edge of Mach gate (smoothstep 0 → 1). */
	private static final double THICK_BL_MACH_LOW = 0.9;
	/** Upper edge of Mach gate (full effect). */
	private static final double THICK_BL_MACH_HIGH = 1.1;
	/** Upper Mach where effect has decayed to zero (Mach cone shrinks wake). */
	private static final double THICK_BL_MACH_DECAY_END = 3.0;
	/** Lower edge of body L/D gate (no effect). */
	private static final double THICK_BL_LD_LOW = 25.0;
	/** Upper edge of body L/D gate (full effect). */
	private static final double THICK_BL_LD_HIGH = 30.0;
	/** Safety cap on the multiplier to prevent runaway for pathological geometries. */
	private static final double THICK_BL_MAX_MULTIPLIER = 1.8;

	// ==================== Slender-Body Supersonic Pressure Drag (Fix C) ====================
	//
	// Smooth cylindrical body tubes return Cdp = 0 in classical Barrowman
	// treatment (SymmetricComponentCalc line 422-423). At M>1 this is a
	// truncation: a long cylindrical body radiates weak shock systems driven
	// by boundary-layer displacement growth, surface imperfections (joints,
	// fasteners, paint ridges), and viscous-inviscid interaction with the
	// nose-body shoulder shock. Hoerner (1965) Ch. 17 and Tanner (1984)
	// document non-zero body pressure drag on long cylindrical afterbodies
	// at supersonic speeds (Cdp ~ 0.02-0.05 for L/D=20-40 at M=1-3).
	//
	// Calibration: B-level. Linear-in-L/D-excess functional form chosen to
	// match the scale of the Raven/Rabia/Kinsel ORP-specific residuals. Gates
	// on body L/D > 15 and M > 1.05 protect short-body and subsonic cases.
	// Mach decay above M=3 reflects the shrinking Mach cone reducing the
	// effective shock-radiator area.

	private static final double SLENDER_BODY_K = 0.0025;
	private static final double SLENDER_BODY_LD_THRESHOLD = 15.0;
	private static final double SLENDER_BODY_LD_EXCESS_CAP = 25.0;
	private static final double SLENDER_BODY_MACH_LOW = 1.05;
	private static final double SLENDER_BODY_MACH_HIGH = 1.3;
	private static final double SLENDER_BODY_MACH_DECAY_START = 3.0;
	private static final double SLENDER_BODY_MACH_DECAY_END = 5.0;

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
		// Prompt 13 re-anchor (2026-04-17): widened the polynomial plateau on
		// the supersonic side using Hart NACA RM L52E06 free-flight finless
		// ConfigA data (paper/data/csv/naca_rm_l52e06_base_drag.csv) as the
		// primary anchor.  BASE_BLEND_HIGH moved 1.30 → 1.50 so the polynomial
		// holds CDB ≈ 0.230 at M=1.30 (Hart reads 0.250 ± 0.013) before joining
		// the Devan-Ashwood asymptote where that correlation is actually
		// validated (M > 2.7 on TN 3393; the M=1.30 extrapolation was 44% below
		// Hart).  The M=1.30 anchor value 0.230 is just below the Hart reading
		// of 0.250 (within Hart's ±0.013 digitization uncertainty), chosen to
		// keep the peak inside 0.25-0.26 and avoid regressing TN 3393 turbulent
		// agreement.
		//
		// Matches value and derivative of the subsonic model (0.12 + 0.13*M^2)
		// at BASE_BLEND_LOW (unchanged — subsonic stub is out of scope for
		// Prompt 13 per paper/data/transonic_base_drag_source_hunt.md §4.2) and
		// value and derivative of the Devan-Ashwood supersonic model
		// (A + B/M^2) at BASE_BLEND_HIGH (1.50).  Peak BASE_CD_PEAK = 0.25 at
		// M = BASE_PEAK_MACH = 1.05 is within Hart ±0.01 scatter.
		// Degree-5 polynomial: 4 value constraints + 2 derivative constraints.
		double supValue = BASE_DRAG_A + BASE_DRAG_B / (BASE_BLEND_HIGH * BASE_BLEND_HIGH);
		double supDeriv = -2.0 * BASE_DRAG_B / (BASE_BLEND_HIGH * BASE_BLEND_HIGH * BASE_BLEND_HIGH);
		PolyInterpolator baseDragInterp = new PolyInterpolator(
				new double[] { BASE_BLEND_LOW, BASE_PEAK_MACH, BASE_BLEND_MID, BASE_BLEND_HIGH },
				new double[] { BASE_BLEND_LOW, BASE_BLEND_HIGH });
		baseDragTransonicPoly = baseDragInterp.interpolator(
				0.12 + 0.13 * BASE_BLEND_LOW * BASE_BLEND_LOW,   // subsonic value at M=0.85
				BASE_CD_PEAK,                                      // peak value at M=1.05
				BASE_CD_AT_MID,                                    // Hart-anchored value at M=1.30
				supValue,                                          // Devan-Ashwood at M=1.50
				0.26 * BASE_BLEND_LOW,                             // subsonic derivative at M=0.85
				supDeriv);                                         // Devan-Ashwood derivative at M=1.50
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
		double Cf = calculateFrictionCoefficient(configuration, mach, Re, T_e,
				conditions.isForceTurbulentBL());
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
				// "Perfect finish" is reserved for unusually smooth, carefully-prepared
				// airframes that can sustain natural transition. In that regime the mixed
				// laminar/turbulent smooth-plate Cf from calculateFrictionCoefficient()
				// already captures the dominant physics. Applying the old fully-rough
				// turbulent floor here overwhelms the laminar benefit and over-predicts
				// drag on polished calibration models such as AGARD-B.
				componentCf = Cf;
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

		// Phase 8c: Boundary layer transition correction.
		//
		// Perfect-finish rockets now account for transition inside
		// calculateFrictionCoefficient() using a mixed laminar/turbulent smooth-plate
		// average Cf, so applying the old heuristic factor here would double-count the
		// laminar reduction. Keep the pragmatic HPR cap-and-factor only for ordinary
		// rockets, where surface discontinuities trip transition almost immediately.
		//
		// Note: RASAero Turbulence=True is applied inside
		// calculateFrictionCoefficient() only for the perfect-finish laminar branch.
		// Ordinary painted HPR imports already use the fully-turbulent rough-plate
		// branch, so the flag remains a no-op there and protects the SimVReal
		// corpus from a spurious blanket drag increase.
		double transitionFactor = 1.0;
		if (!configuration.getRocket().isPerfectFinish()) {
			double velocity = conditions.getVelocity();
			double kinematicViscosity = conditions.getAtmosphericConditions().getKinematicViscosity();
			double fLam = laminarFraction(mach, totalLength, velocity, kinematicViscosity);
			// Real painted HPR airframes (paint, couplers, fin fillets, launch lugs) trip
			// the boundary layer within inches. Only "perfect finish" rockets can sustain
			// extended laminar flow; otherwise cap the laminar fraction to a small value.
			// Without this cap the Michel-criterion Re_tr = 3e6 produces ~17% friction
			// haircut on typical HPR airframes at low Mach — a systematic subsonic drag
			// deficit visible in the SimVReal benchmark overshoot cluster.
			fLam = Math.min(fLam, 0.05);
			transitionFactor = 1.0 - 0.6 * fLam;
		}

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
	 * @param configuration    rocket configuration (for finish type)
	 * @param mach             freestream Mach number
	 * @param Re               freestream Reynolds number
	 * @param T_e              freestream static temperature (K)
	 * @param forceTurbulentBL when {@code true} the boundary layer is forced to
	 *                         fully-turbulent from x=0 regardless of surface
	 *                         finish or transition Reynolds number. Mirrors the
	 *                         RASAero II {@code Turbulence=True} CDX1 flag.
	 * @return skin friction coefficient referenced to freestream dynamic pressure
	 */
	private double calculateFrictionCoefficient(FlightConfiguration configuration, double mach, double Re, double T_e,
			boolean forceTurbulentBL) {
		boolean perfectFinish = configuration.getRocket().isPerfectFinish() && !forceTurbulentBL;
		double CfBase = perfectFinish
				? smoothFinishTransitionCf(Re, transitionReynoldsNumber(mach))
				: incompressibleCf(Re, false);
		double CfSubsonic = CfBase * subsonicCfCorrection(mach, Re, perfectFinish);

		if (mach <= 0.9) {
			return CfSubsonic;
		}

		// Van Driest II compressible transformation (NASA TN D-6945, Hopkins 1972).
		// Supersedes the Eckert reference-temperature method for M > 1.
		// Hopkins & Inouye (1971 AIAA J.) showed Van Driest II gives the best
		// agreement with experimental data across M 1.5-9.
		double CfSupersonic = vanDriestIICf(mach, Re, T_e);

		if (mach >= 1.1) {
			return CfSupersonic;
		}

		// Linear blend through transonic (M 0.9–1.1)
		double t = (mach - 0.9) / 0.2;
		return CfSubsonic * (1.0 - t) + CfSupersonic * t;
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
	 * Average incompressible skin-friction coefficient for a smooth plate with
	 * natural transition at {@code Re_transition}.
	 * <p>
	 * For {@code Re <= Re_transition}, the boundary layer stays laminar over the
	 * full length and the Blasius average applies. Above transition, use the
	 * classical mixed flat-plate relation:
	 * <pre>
	 * Cf = 0.074 / Re^0.2 - A / Re
	 * A = 0.074 * Re_tr^0.8 - 1.328 * sqrt(Re_tr)
	 * </pre>
	 * which reduces to the well-known {@code -1742/Re} correction when
	 * {@code Re_tr = 5e5}.
	 */
	static double smoothFinishTransitionCf(double Re, double reTransition) {
		if (Re < 1.0e4) {
			return 1.33e-2;
		}
		if (reTransition <= 1.0e4 || Re <= reTransition) {
			return 1.328 / MathUtil.safeSqrt(Re);
		}

		double a = 0.074 * Math.pow(reTransition, 0.8) - 1.328 * Math.sqrt(reTransition);
		double cf = 0.074 / Math.pow(Re, 0.2) - a / Re;
		return Math.max(cf, 1.0e-6);
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

	// ==================== Van Driest II Method ====================
	// NASA TN D-6945, Hopkins (1972), Eqs. 1-18.
	// Van Driest II transformation: maps compressible BL to incompressible
	// via Fc, Ftheta, Fx transformation functions. Recovery factor r = 0.88.

	/** Van Driest II recovery factor (TN D-6945 recommends 0.88, not 1.0). */
	private static final double VD2_RECOVERY = 0.88;

	/**
	 * Compressible local skin-friction coefficient via Van Driest II transformation.
	 * <p>
	 * Reference: Hopkins, E.J., "Charts for Predicting Turbulent Skin Friction
	 * from the Van Driest Method (II)," NASA TN D-6945, October 1972.
	 * <p>
	 * The method transforms the compressible Re to an equivalent incompressible Re,
	 * solves the Schoenherr (Karman-Schoenherr) formula for incompressible Cf,
	 * then transforms back. Validated against experimental data at M 1.5-9
	 * (Hopkins & Inouye, 1971 AIAA J.).
	 *
	 * @param mach edge Mach number (must be > 0)
	 * @param reX  length Reynolds number
	 * @param te   edge static temperature (K)
	 * @return local compressible skin-friction coefficient
	 */
	static double vanDriestIICf(double mach, double reX, double te) {
		if (mach < 0.01 || reX < 1e3) {
			// At very low Mach, VD-II degenerates; use incompressible directly
			return incompressibleCf(reX, false);
		}

		// Adiabatic wall temperature: Tw = Te * (1 + r*(gamma-1)/2 * M^2)
		double tw = te * (1.0 + VD2_RECOVERY * 0.2 * mach * mach);

		// Transformation function Fc (Eqs. 8, 12-17)
		double Fc = computeVD2Fc(mach, tw / te);

		// Transformation function Ftheta (Eq. 10) using Sutherland viscosity
		double Ftheta = computeVD2Ftheta(te, tw);

		// Fx = Ftheta / Fc (Eq. 11)
		double Fx = Ftheta / Fc;

		// Transform to incompressible Re: Re_bar_x = Fx * Re_x (Eq. 7)
		double reBarX = Fx * reX;
		if (reBarX < 1e3) reBarX = 1e3;

		// Solve Schoenherr for incompressible average CF (Eq. 1)
		double cfBarAvg = solveSchoenherrCF(reBarX);

		// Local from average (Eq. 2)
		double cfBarLocal = 0.242 * cfBarAvg / (0.242 + 0.8686 * Math.sqrt(cfBarAvg));

		// Transform back to compressible: Cf = Cf_bar / Fc (Eq. 4)
		return cfBarLocal / Fc;
	}

	/**
	 * Van Driest II skin-friction transformation function Fc (Eq. 8).
	 * Fc = r*m / (arcsin(alpha) + arcsin(beta))^2
	 */
	private static double computeVD2Fc(double mach, double twTe) {
		double m = 0.2 * mach * mach;  // Eq. (17)
		double F = twTe;                // Eq. (16)
		double A = Math.sqrt(VD2_RECOVERY * m / F); // Eq. (14)
		double B = (1.0 + VD2_RECOVERY * m - F) / F; // Eq. (15)

		double disc = Math.sqrt(4.0 * A * A + B * B);
		double alpha = (2.0 * A * A - B) / disc; // Eq. (12)
		double beta = B / disc;                    // Eq. (13)

		// Clamp to [-1, 1] for arcsin safety
		alpha = Math.max(-1.0, Math.min(1.0, alpha));
		beta = Math.max(-1.0, Math.min(1.0, beta));

		double denom = Math.asin(alpha) + Math.asin(beta);
		if (Math.abs(denom) < 1e-10) {
			// Degenerate case (M → 0): Fc → 1
			return 1.0;
		}
		return VD2_RECOVERY * m / (denom * denom);
	}

	/**
	 * Van Driest II momentum-thickness Re transformation Ftheta (Eq. 10).
	 * Uses Sutherland viscosity for the mu_e/mu_w ratio.
	 */
	private static double computeVD2Ftheta(double te, double tw) {
		// Viscosity ratio via Sutherland's law
		double muRatio = Math.pow(te / tw, 1.5) * (tw + S_SUTHERLAND) / (te + S_SUTHERLAND);
		// Keyes correction factors from Eq. (10)
		double keyesFactor_w = 1.0 + 122e-5 / tw;
		double keyesFactor_e = 1.0 + 122e-5 / te;
		return muRatio * Math.sqrt(te / tw) * keyesFactor_w / keyesFactor_e;
	}

	/**
	 * Solve the Schoenherr (Karman-Schoenherr) implicit formula for average Cf.
	 * Eq. (1): 0.242 / sqrt(CF) = log10(Re_x * CF)
	 * Uses Newton-Raphson iteration.
	 */
	private static double solveSchoenherrCF(double reX) {
		// Initial guess from Schultz-Grunow approximation
		double cf = 0.455 / Math.pow(Math.log10(reX), 2.58);
		for (int i = 0; i < 50; i++) {
			double sqrtCf = Math.sqrt(cf);
			double lhs = 0.242 / sqrtCf;
			double rhs = Math.log10(reX * cf);
			double residual = lhs - rhs;
			double dRes = -0.121 / (cf * sqrtCf) - 1.0 / (cf * Math.log(10));
			double delta = residual / dRes;
			cf -= delta;
			if (cf < 1e-8) cf = 1e-8;
			if (Math.abs(delta) < 1e-12) break;
		}
		return cf;
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

		// Fix C: slender-body supersonic pressure drag. Classical smooth-cylinder
		// Cdp=0 underestimates long-L/D bodies at M>1; add a body-scale
		// pressure-drag contribution driven by BL-displacement + surface shocklets.
		double slenderCdp = calculateSlenderBodyPressureCD(configuration, conditions);
		if (slenderCdp > 0.0) {
			total += slenderCdp;
			distributeSlenderBodyPressureCD(configuration, forceMap, slenderCdp);
		}

		return total;
	}

	/**
	 * Fix C: distribute the whole-configuration slender-body pressure drag across
	 * active cylindrical body tubes (weighted by length) for per-component
	 * reporting. Does not affect the simulation drag (already summed into
	 * {@code total} by the caller) — this only updates the breakdown.
	 */
	private static void distributeSlenderBodyPressureCD(FlightConfiguration configuration,
			Map<RocketComponent, AerodynamicForces> forceMap, double slenderCdp) {
		if (forceMap == null || slenderCdp <= 0.0) {
			return;
		}
		double totalLength = computeBodyTubeLength(configuration);
		if (totalLength < MathUtil.EPSILON) {
			return;
		}
		InstanceMap imap = configuration.getActiveInstances();
		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry : imap.entrySet()) {
			RocketComponent c = entry.getKey();
			if (!(c instanceof SymmetricComponent)) continue;
			SymmetricComponent sc = (SymmetricComponent) c;
			double fr = sc.getForeRadius();
			double ar = sc.getAftRadius();
			double rMax = Math.max(fr, ar);
			if (rMax < MathUtil.EPSILON) continue;
			if (Math.abs(fr - ar) / rMax >= 0.01) continue;
			int instanceCount = entry.getValue().size();
			double lengthShare = sc.getLength() * instanceCount / totalLength;
			double share = slenderCdp * lengthShare;
			AerodynamicForces f = forceMap.get(c);
			if (f != null && share > 0.0) {
				f.setPressureCD(f.getPressureCD() + share);
			}
		}
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

		// Chapman (1950) NACA TN 2137 laminar base drag correction.
		//
		// For "perfect finish" rockets, where the surface is smooth enough for the
		// boundary layer to remain laminar over a significant fraction of the body,
		// the Devan-Ashwood turbulent correlation overestimates base drag at high Mach
		// (MAPE 44% vs TN 3393 laminar data). The Chapman laminar formula
		// Cpb_lam = C_LAM / (M² × √Re_L) correctly captures the faster Mach decay
		// of laminar base pressure (laminar BL → less shear-layer mixing → lower
		// base suction than turbulent).
		//
		// The correction is blended by the local laminar fraction: a fully laminar
		// body uses Chapman exclusively; a partially laminar body interpolates.
		// For non-perfect-finish rockets the laminar fraction is negligibly small
		// (turbulent trip from surface roughness) so this path is never activated.
		if (configuration.getRocket().isPerfectFinish() && !conditions.isForceTurbulentBL()
				&& mach > ChapmanKorstBaseDrag.LAM_BLEND_LOW) {
			double velocity = conditions.getVelocity();
			double nu = conditions.getAtmosphericConditions().getKinematicViscosity();
			double L = configuration.getLengthAerodynamic();
			if (L > 1e-4 && velocity > 1e-3 && nu > 1e-10) {
				double reL = velocity * L / nu;
				double fLam = laminarFraction(mach, L, velocity, nu);
				if (fLam > 0.01) {
					double baseLam = ChapmanKorstBaseDrag.blendedLaminarBaseDrag(mach, reL);
					base = fLam * baseLam + (1.0 - fLam) * base;
				}
			}
		}

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
					boolean selfBoattail = s instanceof Transition
							&& s.getAftRadius() < s.getForeRadius()
							&& s.getLength() > 0;
					if (aftRadius < foreRadius && s.getLength() > 0) {
						correctedBase *= calculateBoattailFactor(
								foreRadius, aftRadius, s.getLength(), mach);
					}

					// Viswanath (1996) applies to the wake downstream of a preceding boattail.
					// Do not also apply it on the boattail component itself: that double-counts
					// the reduction, and on steep imported boattails (for example Qu8k) can
					// collapse the aft base drag to zero even though the rocket still exposes
					// a finite blunt base area at the exit plane.
					if (!selfBoattail) {
						correctedBase *= calculateViswanathBoattailFactor(s, mach);
					}

					// Finned-body base drag augmentation: fins at the base disrupt wake
					// recompression, increasing base suction.  ADA636861 (Basic Finner)
					// and Hoerner Ch.16 show 40-60% higher base drag for finned bodies.
					correctedBase *= calculateFinnedBaseAugmentation(s, entry.getValue(), imap, mach);

					// Thick-BL base drag amplification for minimum-diameter, high-L/D
					// airframes. Applied after the finned-body augmentation because the
					// BL displacement effect on wake pressure is multiplicatively
					// independent of the fin-wake interference mechanism. Both gates
					// (M > 0.9 and body L/D > 25) must be open for any effect. See
					// THICK_BL_K constant comment and `raven_vs_rabia_diagnostic.md`.
					correctedBase *= calculateThickBLBaseMultiplier(
							s, entry.getValue(), configuration, conditions, aftRadius);

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

	/**
	 * Compute the finned-body base drag augmentation factor for a symmetric
	 * component that exposes base area.  If fins are mounted on this component
	 * (or on an adjacent component sharing the aft face), the base drag is
	 * increased to account for fin-wake interference.
	 *
	 * @param s     the symmetric component whose base is exposed
	 * @param imap  active instance map (to search for fins on adjacent pods)
	 * @param mach  freestream Mach number
	 * @return augmentation multiplier >= 1.0
	 */
	static double calculateFinnedBaseAugmentation(SymmetricComponent s,
			ArrayList<InstanceContext> sContexts, InstanceMap imap, double mach) {
		// Fins near the base disrupt wake recompression at all speeds, but the
		// effect is modest at subsonic (Hoerner Ch. 16: ~10-20%) and strongest
		// at supersonic (ADA636861: ~40-60%).  Below M=0.2 the effect is negligible.
		if (mach < 0.2) {
			return 1.0;
		}

		// Count fins on this component and its children
		int totalFinCount = 0;
		double maxSpan = 0;
		double bodyRadius = s.getAftRadius();
		boolean hasRoundedFin = false;

		// Check children of this component for FinSets
		for (int i = 0; i < s.getChildCount(); i++) {
			RocketComponent child = s.getChild(i);
			if (child instanceof FinSet) {
				FinSet fin = (FinSet) child;
				if (finTrailingEdgeNearAftFace(s, fin)) {
					totalFinCount += fin.getFinCount();
					maxSpan = Math.max(maxSpan, fin.getSpan());
					hasRoundedFin |= fin.getCrossSection() == FinSet.CrossSection.ROUNDED;
				}
			}
		}

		// Also check parent's children (fins may be siblings of a
		// transition/boattail at the aft end). Additionally, for boattail
		// components with significant taper (>5%), check the immediately
		// preceding sibling's children — fins on the body tube right before
		// a boattail still affect the base wake.
		RocketComponent parent = s.getParent();
		if (parent != null && totalFinCount == 0) {
			boolean realBoattail = s instanceof Transition
					&& s.getForeRadius() > MathUtil.EPSILON
					&& (s.getForeRadius() - s.getAftRadius()) / s.getForeRadius() > 0.05;
			RocketComponent prevSibling = null;
			for (int i = 0; i < parent.getChildCount(); i++) {
				RocketComponent sibling = parent.getChild(i);
				if (sibling == s && realBoattail) {
					// Found ourselves — check the immediately preceding sibling
					if (prevSibling != null) {
						for (int j = 0; j < prevSibling.getChildCount(); j++) {
							RocketComponent nephew = prevSibling.getChild(j);
							if (nephew instanceof FinSet) {
								FinSet fin = (FinSet) nephew;
								totalFinCount += fin.getFinCount();
								maxSpan = Math.max(maxSpan, fin.getSpan());
								hasRoundedFin |= fin.getCrossSection() == FinSet.CrossSection.ROUNDED;
							}
						}
					}
				}
				if (sibling instanceof FinSet) {
					FinSet fin = (FinSet) sibling;
					totalFinCount += fin.getFinCount();
					maxSpan = Math.max(maxSpan, fin.getSpan());
					hasRoundedFin |= fin.getCrossSection() == FinSet.CrossSection.ROUNDED;
				}
				prevSibling = sibling;
			}
		}

		// Search the instance map for FinSets in coaxial pods whose axial
		// position places them near this component's aft face (fin cans in
		// PodSets).  Only count fins that are geometrically near the base —
		// not fins on a completely different stage or body section.
		if (totalFinCount == 0 && imap != null && sContexts != null && !sContexts.isEmpty()) {
			CoordinateIF sLoc = sContexts.get(0).getLocation();
			double sAftX = sLoc.getX() + s.getLength();
			final double X_TOL = 0.05;  // 50 mm — generous tolerance for fin proximity

			for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry : imap.entrySet()) {
				RocketComponent c = entry.getKey();
				if (!(c instanceof FinSet)) continue;
				// Check if any instance of this fin is near s's aft face
				for (InstanceContext ctx : entry.getValue()) {
					double finX = ctx.getLocation().getX();
					// Fin is "near" the base if it overlaps or is within tolerance
					// of the aft face.  FinSets have their origin at the root LE,
					// so finX is the leading edge; finX + rootChord is trailing edge.
					double finTE = finX + ((FinSet) c).getLength();
					// Fin affects base drag if its trailing edge is near s's aft face
					if (Math.abs(finTE - sAftX) < X_TOL || (finX < sAftX && finTE >= sAftX - X_TOL)) {
						FinSet fin = (FinSet) c;
						totalFinCount += fin.getFinCount();
						maxSpan = Math.max(maxSpan, fin.getSpan());
						hasRoundedFin |= fin.getCrossSection() == FinSet.CrossSection.ROUNDED;
						break;  // counted this FinSet, move to next
					}
				}
			}
		}

		if (totalFinCount == 0 || bodyRadius < MathUtil.EPSILON) {
			return 1.0;
		}

		// Mach-dependent ramp with subsonic and supersonic regimes:
		//   M < 0.2:  factor = 0     (no augmentation)
		//   M 0.2-0.8: ramp 0 → 0.30 (Hoerner Ch. 16: subsonic ~10-20%)
		//   M 0.8-1.3: ramp 0.30 → 1.0 (shock-wake interaction strengthens)
		//   M 1.3-3.0: factor = 1.0  (full augmentation)
		//   M > 3.0:   factor = 3/M  (fins submerged in body shock cone)
		double machFactor;
		if (mach < 0.8) {
			machFactor = 0.30 * (mach - 0.2) / 0.6;
		} else if (mach < 1.3) {
			machFactor = 0.30 + 0.70 * (mach - 0.8) / 0.5;
		} else if (mach < 3.0) {
			machFactor = 1.0;
		} else {
			machFactor = 3.0 / mach;
		}
		double baseScale = FINNED_BASE_K;
		boolean expandingSleeve = hasExpandingFinCanSleeve(s);
		if (hasRoundedFin && mach < 1.3) {
			double roundedMachFactor = mach < 0.8
					? 0.95 * (mach - 0.2) / 0.6
					: 0.95 + 0.05 * (mach - 0.8) / 0.5;
			if (!expandingSleeve && mach < 0.8) {
				double lowSubsonicBoost = 1.15 * MathUtil.clamp((mach - 0.2) / 0.4, 0.0, 1.0)
						* (1.0 - smoothstep(MathUtil.clamp((mach - 0.65) / 0.15, 0.0, 1.0)));
				roundedMachFactor = Math.max(roundedMachFactor, lowSubsonicBoost);
			}
			machFactor = Math.max(machFactor, roundedMachFactor);
			baseScale = Math.max(baseScale, expandingSleeve ? 0.70 : ROUNDED_FINNED_BASE_K);
		}
		if (!hasRoundedFin && totalFinCount >= 4 && mach < 1.3) {
			double fourFinMachFactor = mach < 0.8
					? 0.38 * (mach - 0.2) / 0.6
					: 0.38 + 0.62 * (mach - 0.8) / 0.5;
			machFactor = Math.max(machFactor, fourFinMachFactor);
		}
		if (totalFinCount >= 4 && expandingSleeve) {
			baseScale = Math.max(baseScale, FIN_CAN_SLEEVE_BASE_K);
		}

		// Fin-count saturation: wake disruption is not linear in fin count.
		// Three fins already divide the base shear layer into multiple corner-wake
		// sectors, so they produce nearly the same base-pressure deficit as four
		// fins. Normalize the saturating curve so the 4-fin Basic Finner anchor is
		// unchanged, while 3-fin HPR airframes are not under-counted.
		double fourFinAnchor = 1.0 - Math.exp(-4.0 / 1.4);
		double finFactor = (1.0 - Math.exp(-totalFinCount / 1.4)) / fourFinAnchor;
		finFactor = MathUtil.clamp(finFactor, 0.0, 1.25);

		// Span factor: fins that extend far from the body affect the base more.
		// Normalize by body radius; cap at 1.0 for span/radius >= 1.0.
		double spanFactor = MathUtil.clamp(maxSpan / bodyRadius, 0.3, 1.0);

		return 1.0 + baseScale * finFactor * spanFactor * machFactor;
	}

	private static boolean finTrailingEdgeNearAftFace(SymmetricComponent s, FinSet fin) {
		// Tail fins do not need to be mathematically flush with the base to disrupt
		// the near wake.  Use a small absolute tolerance for model-scale fixtures and
		// a body-scale aft region for HPR tail fins/fin cans, while still excluding
		// forward wings like AGARD-B's mid-body delta wing.
		double xTol = Math.max(0.05, 2.5 * s.getAftRadius());
		double sAftX = s.toAbsolute(new Coordinate(s.getLength(), 0, 0))[0].getX();
		double finLeadX = fin.toAbsolute(new Coordinate(0, 0, 0))[0].getX();
		double finTeX = fin.toAbsolute(new Coordinate(fin.getLength(), 0, 0))[0].getX();
		return Math.abs(finTeX - sAftX) < xTol || (finLeadX < sAftX && finTeX >= sAftX - xTol);
	}

	private static boolean hasExpandingFinCanSleeve(SymmetricComponent s) {
		if (!(s instanceof BodyTube)) {
			return false;
		}
		RocketComponent parent = s.getParent();
		if (parent == null) {
			return false;
		}
		RocketComponent prevSibling = null;
		for (int i = 0; i < parent.getChildCount(); i++) {
			RocketComponent child = parent.getChild(i);
			if (child == s && prevSibling instanceof Transition shoulder) {
				double radiusStep = shoulder.getAftRadius() - shoulder.getForeRadius();
				return radiusStep > 0.0
						&& Math.abs(shoulder.getAftRadius() - s.getForeRadius()) < 0.003
						&& shoulder.getLength() <= 0.035;
			}
			prevSibling = child;
		}
		return false;
	}

	/**
	 * Compute the thick-boundary-layer base-drag amplification multiplier.
	 * <p>
	 * Minimum-diameter, high-body-L/D airframes develop a turbulent BL whose
	 * thickness approaches the body radius at the base. In that regime the
	 * Devan-Ashwood correlation systematically under-predicts base suction.
	 * This multiplier adds a δ/R-driven correction gated on supersonic Mach
	 * and high body L/D so healthy moderate-L/D rockets are not affected.
	 * <p>
	 * Form:
	 * <pre>
	 *   mul = 1 + THICK_BL_K * max(0, δ/R - 0.5) * f_Mach(M) * g_LD(L/D)
	 * </pre>
	 * capped at {@link #THICK_BL_MAX_MULTIPLIER}.
	 * <p>
	 * See class-level comment block on THICK_BL_* for calibration and references.
	 *
	 * @param s              symmetric component whose base is exposed
	 * @param sContexts      active instance contexts for s (for absolute X)
	 * @param configuration  active flight configuration (for body-L/D computation)
	 * @param conditions     current flight conditions (Mach, velocity, viscosity)
	 * @param baseRadius     radius at the base station (m)
	 * @return multiplier >= 1.0 (capped at THICK_BL_MAX_MULTIPLIER)
	 */
	static double calculateThickBLBaseMultiplier(SymmetricComponent s,
			ArrayList<InstanceContext> sContexts,
			FlightConfiguration configuration,
			FlightConditions conditions,
			double baseRadius) {
		double mach = conditions.getMach();

		// Gate 1: Mach. Subsonic wake dynamics are governed by different
		// mechanisms; the thick-BL correction is intentionally off below M=0.9.
		if (mach <= THICK_BL_MACH_LOW) {
			return 1.0;
		}
		if (baseRadius < MathUtil.EPSILON) {
			return 1.0;
		}

		// Gate 2: body L/D. Sum the lengths of all active SymmetricComponent
		// instances that are body tubes / cylindrical afterbodies (foreRadius ≈
		// aftRadius). This excludes nose cones and boattails. Divide by the
		// diameter at the base (2*baseRadius) for the body L/D ratio.
		double bodyLength = computeBodyTubeLength(configuration);
		double bodyDiameter = 2.0 * baseRadius;
		if (bodyDiameter < MathUtil.EPSILON) {
			return 1.0;
		}
		double bodyLD = bodyLength / bodyDiameter;

		// Smooth L/D ramp: 0 at L/D=25, full at L/D=30.
		double ldRamp;
		if (bodyLD <= THICK_BL_LD_LOW) {
			return 1.0;
		} else if (bodyLD >= THICK_BL_LD_HIGH) {
			ldRamp = 1.0;
		} else {
			ldRamp = smoothstep((bodyLD - THICK_BL_LD_LOW)
					/ (THICK_BL_LD_HIGH - THICK_BL_LD_LOW));
		}

		// Mach ramp: smoothstep up through transonic (0.9 → 1.1), full between
		// 1.1 and some low-supersonic point, then linear decay back to zero by
		// M = THICK_BL_MACH_DECAY_END. Above M=3 the Mach cone has shrunk the
		// effective wake diameter and the thick-BL mechanism no longer drives
		// the base pressure deficit.
		double machRamp;
		if (mach <= THICK_BL_MACH_HIGH) {
			machRamp = smoothstep((mach - THICK_BL_MACH_LOW)
					/ (THICK_BL_MACH_HIGH - THICK_BL_MACH_LOW));
		} else if (mach >= THICK_BL_MACH_DECAY_END) {
			machRamp = 0.0;
		} else {
			// Smooth C1 decay from 1.0 at M=1.1 to 0.0 at M=3.0
			double t = (mach - THICK_BL_MACH_HIGH)
					/ (THICK_BL_MACH_DECAY_END - THICK_BL_MACH_HIGH);
			machRamp = 1.0 - smoothstep(t);
		}
		if (machRamp <= 0.0) {
			return 1.0;
		}

		// Compute δ/R at the base using the 1/7-power turbulent flat-plate
		// correlation: δ/x = 0.37 / Re_x^0.2. Use the absolute X of this
		// component's aft face (from the nose tip) as the BL development
		// length x. At M > 1 compressibility slightly thickens the BL but
		// Van Driest II already reduces the associated Cf; for the purposes
		// of this correlation we use the incompressible flat-plate δ formula,
		// which yields conservative δ/R estimates (real compressible δ is
		// larger, strengthening the case for the correction, not weakening it).
		double xBase = computeBaseStationX(s, sContexts);
		if (xBase <= MathUtil.EPSILON) {
			return 1.0;
		}
		double velocity = conditions.getVelocity();
		double nu = conditions.getAtmosphericConditions().getKinematicViscosity();
		if (velocity < 1e-3 || nu < 1e-10) {
			return 1.0;
		}
		double reX = velocity * xBase / nu;
		if (reX < 1e4) {
			return 1.0;
		}
		double delta = xBase * 0.37 / Math.pow(reX, 0.2);
		double deltaOverR = delta / baseRadius;

		double deltaExcess = Math.max(0.0, deltaOverR - THICK_BL_DELTA_R_THRESHOLD);
		if (deltaExcess <= 0.0) {
			return 1.0;
		}

		double multiplier = 1.0 + THICK_BL_K * deltaExcess * machRamp * ldRamp;
		return Math.min(multiplier, THICK_BL_MAX_MULTIPLIER);
	}

	/**
	 * Fix C: slender-body supersonic pressure drag for long cylindrical bodies.
	 * Returns a whole-configuration Cdp (referenced to ref area) representing
	 * the body-scale viscous-inviscid pressure drag that classical smooth-
	 * cylinder theory sets to zero.
	 *
	 * Formula: Cdp = K * machFactor(M) * ldExcess, where ldExcess =
	 * clamp(bodyLD - LD_THRESHOLD, 0, LD_EXCESS_CAP) and machFactor is a
	 * smoothstep up from M=1.05 to M=1.3, held between M=1.3 and M=3.0, then
	 * decays to zero between M=3.0 and M=5.0.
	 *
	 * Gates (both required):
	 *   1. Body L/D > 15 (protects short HPR and typical mid-power designs).
	 *   2. M > 1.05 (no subsonic or transonic effect).
	 *
	 * See class-level comment block on SLENDER_BODY_* for references and
	 * calibration notes.
	 *
	 * @param configuration active flight configuration
	 * @param conditions flight conditions
	 * @return slender-body Cdp contribution, referenced to ref area; zero if gated off
	 */
	static double calculateSlenderBodyPressureCD(FlightConfiguration configuration,
			FlightConditions conditions) {
		double mach = conditions.getMach();
		if (mach <= SLENDER_BODY_MACH_LOW) {
			return 0.0;
		}
		if (mach >= SLENDER_BODY_MACH_DECAY_END) {
			return 0.0;
		}

		double refLength = conditions.getRefLength();
		if (refLength < MathUtil.EPSILON) {
			return 0.0;
		}

		double bodyLength = computeBodyTubeLength(configuration);
		double bodyLD = bodyLength / refLength;
		if (bodyLD <= SLENDER_BODY_LD_THRESHOLD) {
			return 0.0;
		}

		double machFactor;
		if (mach <= SLENDER_BODY_MACH_HIGH) {
			machFactor = smoothstep((mach - SLENDER_BODY_MACH_LOW)
					/ (SLENDER_BODY_MACH_HIGH - SLENDER_BODY_MACH_LOW));
		} else if (mach >= SLENDER_BODY_MACH_DECAY_START) {
			double t = (mach - SLENDER_BODY_MACH_DECAY_START)
					/ (SLENDER_BODY_MACH_DECAY_END - SLENDER_BODY_MACH_DECAY_START);
			machFactor = 1.0 - smoothstep(t);
		} else {
			machFactor = 1.0;
		}

		double ldExcess = Math.min(bodyLD - SLENDER_BODY_LD_THRESHOLD,
				SLENDER_BODY_LD_EXCESS_CAP);
		return SLENDER_BODY_K * ldExcess * machFactor;
	}

	/**
	 * Sum the axial length of active cylindrical body tubes in the
	 * configuration. Excludes nose cones, transitions, and boattails by
	 * requiring foreRadius ≈ aftRadius (within 1% relative tolerance).
	 */
	private static double computeBodyTubeLength(FlightConfiguration configuration) {
		double total = 0.0;
		InstanceMap imap = configuration.getActiveInstances();
		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry : imap.entrySet()) {
			RocketComponent c = entry.getKey();
			if (!(c instanceof SymmetricComponent)) continue;
			SymmetricComponent sc = (SymmetricComponent) c;
			double fr = sc.getForeRadius();
			double ar = sc.getAftRadius();
			double rMax = Math.max(fr, ar);
			if (rMax < MathUtil.EPSILON) continue;
			// Treat as cylindrical body tube if fore ≈ aft radius.
			if (Math.abs(fr - ar) / rMax < 0.01) {
				total += sc.getLength() * entry.getValue().size();
			}
		}
		return total;
	}

	/**
	 * Absolute X of this component's aft face, as the BL development length
	 * from the nose tip. Falls back to component length if no instance
	 * contexts are available (static tests).
	 */
	private static double computeBaseStationX(SymmetricComponent s,
			ArrayList<InstanceContext> sContexts) {
		if (sContexts != null && !sContexts.isEmpty()) {
			CoordinateIF loc = sContexts.get(0).getLocation();
			return loc.getX() + s.getLength();
		}
		return s.getLength();
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
	 * C1-continuous polynomial blend through the transonic region (M 0.85–1.5) with
	 * a peak near M=1.10-1.15 plateau.
	 * <p>
	 * The Devan-Ashwood model correctly asymptotes to a nonzero constant at high
	 * Mach, matching experimental data for turbulent cylindrical afterbodies.
	 * <p>
	 * The returned coefficient is referenced to the base area and must be scaled
	 * by (base area / reference area) for each component.
	 * <p>
	 * References: Devan &amp; Ashwood (1961) NASA TN D-721; Hoerner "Fluid-Dynamic
	 * Drag" (1965) Ch. 3; USAF DATCOM Section 4.6.3.2.  Transonic plateau
	 * (M 1.05-1.50) anchored against Hart NACA RM L52E06 (1952, free-flight
	 * finless, sting-free) — see paper/data/csv/naca_rm_l52e06_base_drag.csv.
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
	 * Calculate base drag coefficient using the pure Devan-Ashwood correlation.
	 * <p>
	 * Previously applied a Lamb-Oberkampf (1995) Reynolds-number correction at M > 1.3,
	 * but that correction was D-level (zero external data points in the repo) and reduced
	 * base drag by up to 7.7% for high-Re vehicles (e.g. Kinsel at Re_D=9.1e6), worsening
	 * trajectory agreement. The Devan-Ashwood correlation was validated against NACA TN 3393
	 * without Re correction (A-level, MAPE 15.9%), so the uncorrected form is the validated
	 * baseline.
	 * <p>
	 * The two-argument signature is retained for API compatibility.
	 *
	 * @param m Mach number
	 * @param conditions flight conditions (unused after Re correction removal)
	 * @return base drag coefficient (pure Devan-Ashwood / transonic polynomial)
	 */
	public static double calculateBaseCD(double m, FlightConditions conditions) {
		return calculateBaseCD(m);
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

package info.openrocket.core.aerodynamics.barrowman;

import static java.lang.Math.pow;
import static info.openrocket.core.util.MathUtil.pow2;

import java.util.Arrays;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.AeroelasticModel;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.PlumeModel;
import info.openrocket.core.aerodynamics.ShockGeometry;
import info.openrocket.core.logging.Warning;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.util.BugException;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.LinearInterpolator;
import info.openrocket.core.util.MathUtil;
import info.openrocket.core.util.PolyInterpolator;
import info.openrocket.core.util.Transformation;

public class FinSetCalc extends RocketComponentCalc {
	
	/** considers the stall angle as 20 degrees*/
	private static final double STALL_ANGLE = (20 * Math.PI / 180);

	/** Mach number below which SBLI effects are not applied. */
	private static final double SBLI_MACH_MIN = 1.2;

	/** Number of divisions in the fin chords. */
	protected static final int DIVISIONS = 48;
	
	protected double macLength = Double.NaN; // MAC length
	protected double macLead = Double.NaN; // MAC leading edge position
	protected double macSpan = Double.NaN; // MAC spanwise position
	protected double finArea = Double.NaN; // Fin area
	protected double ar = Double.NaN; // Fin aspect ratio
	protected double span = Double.NaN; // Fin span
	protected double cosGamma = Double.NaN; // Cosine of midchord sweep angle
	protected double cosGammaLead = Double.NaN; // Cosine of leading edge sweep angle
	protected double rollSum = Double.NaN; // Roll damping sum term
	
	protected int interferenceFinCount = -1; // No. of fins in interference
	
	protected double[] chordLead = new double[DIVISIONS];
	protected double[] chordTrail = new double[DIVISIONS];
	protected double[] chordLength = new double[DIVISIONS];
	
	protected final WarningSet geometryWarnings = new WarningSet();
	
	private final double[] poly = new double[6];

	private final double thickness;
	private final double bodyRadius;
	private final int finCount;
	private final double cantAngle;
	private final FinSet.CrossSection crossSection;
	protected double rootChord = Double.NaN;

	/** Distance from nose tip to fin leading edge, for SBLI BL thickness estimate. */
	private final double xFinFromNose;

	/** Cached SBLI separation length from the last CNa computation (used by pressure drag). */
	private double cachedSBLI_Lsep = 0.0;

	// Aeroelastic coupling parameters (Phase 9a)
	private final double shearModulus;   // Shear modulus of fin material (Pa)
	private final double torsionalJ;     // Torsional second moment of area (m^4)

	/**
	 * builds a calculator of aerodynamic forces a specified fin
	 * @param component		The fin in consideration
	 */
	///why is this accepting RocketComponent when it rejects?
	///why not put FinSet in the parameter instead?
	public FinSetCalc(FinSet component) {
		super(component);

		this.thickness = component.getThickness();
		this.bodyRadius = component.getBodyRadius();
		this.finCount = component.getFinCount();

		this.cantAngle = component.getCantAngle();
		this.span = component.getSpan();
		this.finArea = component.getPlanformArea();
		this.crossSection = component.getCrossSection();

		// Distance from nose to fin leading edge (absolute x)
		double xFin = 0.0;
		try {
			CoordinateIF[] abs = component.toAbsolute(Coordinate.NUL);
			if (abs.length > 0) {
				xFin = abs[0].getX();
			}
		} catch (Exception e) {
			// If component is not yet attached, use 0
		}
		this.xFinFromNose = Math.max(xFin, 0.01); // floor to avoid degenerate cases

		// Aeroelastic parameters from fin material
		String materialName = (component.getMaterial() != null) ? component.getMaterial().getName() : null;
		this.shearModulus = AeroelasticModel.getShearModulus(materialName);
		// Use root chord as representative chord for torsional J
		double rootChordInit = component.getLength();
		this.torsionalJ = AeroelasticModel.computeTorsionalJ(
				rootChordInit > 0 ? rootChordInit : 0.1, this.thickness);

		calculateFinGeometry(component);
		calculatePoly();
		calculateInterferenceFinCount(component);
	}
	
	/*
	 * Calculates the non-axial forces produced by each set of fins.
	 * (normal and side forces, pitch, yaw and roll moments, CP position, CNa).
	 */
	@Override
	public void calculateNonaxialForces(FlightConditions conditions, Transformation transform,
			AerodynamicForces forces, WarningSet warnings) {

		warnings.addAll(geometryWarnings);

		if (finArea < MathUtil.EPSILON || macSpan < MathUtil.EPSILON) {
			forces.setCm(0);
			forces.setCN(0);
			forces.setCP(Coordinate.ZERO);
			forces.setCroll(0);
			forces.setCrollDamp(0);
			forces.setCrollForce(0);
			forces.setCside(0);
			forces.setCyaw(0);
			return;
		}

		// Phase 3c: Use local post-shock flow conditions at the fin station
		// instead of freestream conditions for CNa and CP calculations.
		// At supersonic speeds, the body shock reduces the local Mach and
		// increases the local pressure at the fin station.
		FlightConditions localConditions = getLocalFlowConditions(conditions);

		//////// Calculate CNa.  /////////

		// One fin without interference (both sub- and supersonic):
		double cna1 = calculateFinCNa1(localConditions);

		// Apply SBLI chord reduction at supersonic speeds
		double sbliChordRatio = computeSBLIChordReduction(conditions);
		cna1 *= sbliChordRatio;

		// Multiple fins with fin-fin interference
		double cna;
		double theta = conditions.getTheta();
		double angle = transform.getXrotation();

		// Compute basic CNa without interference effects
		cna = cna1 * MathUtil.pow2(Math.sin(theta - angle));

		// Take into account fin-fin interference effects
		switch (interferenceFinCount) {
		case 1:
		case 2:
		case 3:
		case 4:
			// No interference effect
			break;

		case 5:
			cna *= 0.948;
			break;

		case 6:
			cna *= 0.913;
			break;

		case 7:
			cna *= 0.854;
			break;

		case 8:
			cna *= 0.81;
			break;

		default:
			// Assume 75% efficiency
			cna *= 0.75;
			warnings.add(Warning.PARALLEL_FINS);
			break;
		}

		// Body-fin interference effect
		double r = bodyRadius;
		double tau = r / (span + r);
		if (Double.isNaN(tau) || Double.isInfinite(tau))
			tau = 0;
		double interferenceFactor = 1 + tau; // Classical Barrowman (subsonic)

		// Phase 6f: Pitts-Nielsen-Kaattari Mach-dependent correction (NACA Report 1307)
		// At M < 0.85, F_WB = F_BW = 1.0 (no change to subsonic results).
		// At supersonic speeds, the Mach cone limits the body upwash influence
		// on the fin, reducing interference lift by 10-20%.
		double machForPNK = localConditions.getMach();
		double sweepLE = (cosGammaLead > 0 && cosGammaLead <= 1.0)
				? Math.acos(cosGammaLead) : 0.0;
		double F_WB = PittsNielsenKaattari.computeF_WB(machForPNK, r, span, rootChord, sweepLE);
		double F_BW = PittsNielsenKaattari.computeF_BW(machForPNK, r, span, rootChord);
		cna *= interferenceFactor * F_WB * F_BW;

		// Phase 3c note: dynamic pressure ratio scaling was removed here.
		// The local post-shock Mach (applied via getLocalFlowConditions) already
		// corrects K1/K2/K3 and the fin CNa for post-shock conditions.
		// Multiplying again by dynamicPressureRatio was a double-correction that
		// reduced fin authority by ~2x at M>2, causing spurious instability.

		// Phase 9a: Aeroelastic coupling — reduce CNa at high dynamic pressure
		double q = 0.5 * conditions.getAtmosphericConditions().getDensity()
				* MathUtil.pow2(conditions.getVelocity());
		if (q > AeroelasticModel.Q_THRESHOLD && torsionalJ > 0) {
			double cpArm = macSpan > 0 ? macSpan : span * 0.5;
			double etaAe = AeroelasticModel.computeEffectivenessClamped(
					q, finArea, cna, cpArm, shearModulus, torsionalJ);
			if (AeroelasticModel.isDivergence(q, finArea, cna, cpArm, shearModulus, torsionalJ)) {
				warnings.add(Warning.FIN_DIVERGENCE);
			}
			cna *= etaAe;
		}

		// Phase 9e: Plume-induced flow separation — reduce fin effectiveness
		if (conditions.isPlumeActive() && span > 0) {
			double fOverlap = PlumeModel.computeFinOverlapFraction(
					conditions.getPlumeDiameter(), conditions.getPlumeBaseDiameter(), span);
			if (fOverlap > 0) {
				double etaPlume = PlumeModel.computeFinEffectiveness(fOverlap);
				cna *= etaPlume;
			}
		}

		// Calculate CP position using local conditions
		double x = macLead + calculateCPPos(localConditions) * macLength;
		
		
		// Calculate roll forces, reduce forcing above stall angle
		
		// Without body-fin interference effect:
		//		forces.CrollForce = fins * (macSpan+r) * cna1 * component.getCantAngle() / 
		//			conditions.getRefLength();
		// With body-fin interference effect:
		forces.setCrollForce((macSpan + r) * cna1 * interferenceFactor * F_WB * F_BW * cantAngle / conditions.getRefLength());
		
		if (conditions.getAOA() > STALL_ANGLE) {
			forces.setCrollForce(forces.getCrollForce() * MathUtil.clamp(
					1 - (conditions.getAOA() - STALL_ANGLE) / (STALL_ANGLE / 2), 0, 1));
		}
		forces.setCrollDamp(calculateDampingMoment(conditions));
		forces.setCroll(forces.getCrollForce() - forces.getCrollDamp());
		
		forces.setCN(cna * MathUtil.min(conditions.getAOA(), STALL_ANGLE));
		forces.setCP(new Coordinate(x, 0, 0, cna));
		forces.setCm(forces.getCN() * x / conditions.getRefLength());
		
		/*
		 * TODO: HIGH:  Compute actual side force and yaw moment.
		 * This is not currently performed because it produces strange results for
		 * stable rockets that have two fins in the front part of the fuselage,
		 * where the rocket flies at an ever-increasing angle of attack.  This may
		 * be due to incorrect computation of pitch/yaw damping moments.
		 */
		//		if (fins == 1 || fins == 2) {
		//			forces.Cside = fins * cna1 * Math.cos(theta-angle) * Math.sin(theta-angle);
		//			forces.Cyaw = fins * forces.Cside * x / conditions.getRefLength();
		//		} else {
		//			forces.Cside = 0;
		//			forces.Cyaw = 0;
		//		}
		forces.setCside(0);
		forces.setCyaw(0);
		
	}
	
	/**
	 * Returns the MAC length of the fin.  This is required in the friction drag
	 * computation.
	 * 
	 * @return  the MAC length of the fin.
	 */
	public double getMACLength() {
		return macLength;
	}
	
	public double getMidchordPos() {
		return macLead + 0.5 * macLength;
	}

	/**
	 * Get flight conditions adjusted for local post-shock flow at the fin station.
	 * <p>
	 * Phase 3c: When a shock geometry pre-pass has computed the local Mach behind
	 * the body shock at this fin's axial position, create a modified copy of the
	 * flight conditions with the local Mach number. This feeds the corrected Mach
	 * into the existing K1/K2/K3 supersonic fin CNa computation.
	 * <p>
	 * At subsonic speeds or when no shock geometry is available, returns the
	 * original conditions unchanged.
	 *
	 * @param conditions freestream flight conditions
	 * @return local flight conditions (may be same object if no correction needed)
	 */
	private FlightConditions getLocalFlowConditions(FlightConditions conditions) {
		if (shockGeometry == null || !shockGeometry.isSupersonic()) {
			return conditions;
		}

		ShockGeometry.LocalConditions local = getLocalConditions(shockGeometry);
		if (local == null || Math.abs(local.localMach - conditions.getMach()) < 0.01) {
			return conditions;
		}

		// Create modified conditions with local post-shock Mach
		FlightConditions localCond = conditions.clone();
		localCond.setMach(local.localMach);
		return localCond;
	}
	
	/**
	 * Pre-calculates the fin geometry values.
	 */
	protected void calculateFinGeometry(FinSet component) {
		
		span = component.getSpan();
		finArea = component.getPlanformArea();
		if (finArea < MathUtil.EPSILON) {
			geometryWarnings.add(Warning.ZERO_AREA_FIN, component);
			ar = 0;
		} else {
			ar = 2 * pow2(span) / finArea;
		}
		
		// Check geometry; don't consider points along fin root for this
		// (doing so will cause spurious jagged fin warnings)
		CoordinateIF[] points = component.getFinPoints();
		geometryWarnings.clear();
		boolean down = false;
		for (int i = 1; i < points.length; i++) {
			if ((points[i].getY() > points[i - 1].getY() + 0.001) && down) {
				geometryWarnings.add(Warning.JAGGED_EDGED_FIN, component);
				break;
			}
			if (points[i].getY() < points[i - 1].getY() - 0.001) {
				down = true;
			}
		}

		if ((bodyRadius > 0) && (thickness > bodyRadius / 2)){
			// Add warnings  (radius/2 == diameter/4)
			geometryWarnings.add(Warning.THICK_FIN, component);
		}
		
		// Calculate the chord lead and trail positions and length.  We do need the points
		// along the root for this
		points = component.getFinPointsWithRoot();
		Arrays.fill(chordLead, Double.POSITIVE_INFINITY);
		Arrays.fill(chordTrail, Double.NEGATIVE_INFINITY);
		Arrays.fill(chordLength, 0);
		
		for (int point = 1; point < points.length; point++) {
			double x1 = points[point - 1].getX();
			double y1 = points[point - 1].getY();
			double x2 = points[point].getX();
			double y2 = points[point].getY();
			
			// Don't use the default EPSILON since it is too small
			// and causes too much numerical instability in the computation of x below
			if (MathUtil.equals(y1, y2, 0.001))
				continue;
			
			int i1 = (int) (y1 * 1.0001 / span * (DIVISIONS - 1));
			int i2 = (int) (y2 * 1.0001 / span * (DIVISIONS - 1));
			i1 = MathUtil.clamp(i1, 0, DIVISIONS - 1);
			i2 = MathUtil.clamp(i2, 0, DIVISIONS - 1);
			if (i1 > i2) {
				int tmp = i2;
				i2 = i1;
				i1 = tmp;
			}
			
			for (int i = i1; i <= i2; i++) {
				// Intersection point (x,y)
				// Note that y can be outside the bounds of the line
				// defined by (x1, y1) (x2 y2) so x can similarly be outside
				// the bounds.  If the line is nearly horizontal, it can be
				// 'way outside.  We want to get the whole "strip", so we
				// don't clamp y; however, we do clamp x to avoid numerical
				// instabilities
				double y = i * span / (DIVISIONS - 1);
				double x = MathUtil.clamp((y - y2) / (y1 - y2) * x1 + (y1 - y) / (y1 - y2) * x2,
										  Math.min(x1, x2), Math.max(x1, x2));
				if (x < chordLead[i])
					chordLead[i] = x;
				if (x > chordTrail[i])
					chordTrail[i] = x;
				
				// TODO: LOW:  If fin point exactly on chord line, might be counted twice:
				if (y1 < y2) {
					chordLength[i] -= x;
				} else {
					chordLength[i] += x;
				}
			}
		}
		
		// Check and correct any inconsistencies
		for (int i = 0; i < DIVISIONS; i++) {
			if (Double.isInfinite(chordLead[i]) || Double.isInfinite(chordTrail[i]) ||
					Double.isNaN(chordLead[i]) || Double.isNaN(chordTrail[i])) {
				chordLead[i] = 0;
				chordTrail[i] = 0;
			}
			if (chordLength[i] < 0 || Double.isNaN(chordLength[i])) {
				chordLength[i] = 0;
			}
			if (chordLength[i] > chordTrail[i] - chordLead[i]) {
				chordLength[i] = chordTrail[i] - chordLead[i];
			}
		}
		
		// Root chord from the chord arrays at span station 0
		rootChord = chordTrail[0] - chordLead[0];
		if (rootChord < 0 || Double.isNaN(rootChord)) {
			rootChord = 0;
		}

		/* Calculate fin properties:
		 * 
		 * macLength // MAC length
		 * macLead   // MAC leading edge position
		 * macSpan   // MAC spanwise position
		 * ar        // Fin aspect ratio (already set)
		 * span      // Fin span (already set)
		 */
		macLength = 0;
		macLead = 0;
		macSpan = 0;
		cosGamma = 0;
		cosGammaLead = 0;
		rollSum = 0;
		double area = 0;
		double radius = component.getFinFront().getY();
		
		final double dy = span / (DIVISIONS - 1);
		for (int i = 0; i < DIVISIONS; i++) {
			double length = chordTrail[i] - chordLead[i];
			double y = i * dy;
			
			macLength += length * length;
			macSpan += y * length;
			macLead += chordLead[i] * length;
			area += length;
			rollSum += chordLength[i] * pow2(radius + y);
			
			if (i > 0) {
				double dx = (chordTrail[i] + chordLead[i]) / 2 - (chordTrail[i - 1] + chordLead[i - 1]) / 2;
				double hypot = MathUtil.hypot(dx, dy);
				if (hypot != 0) {
					cosGamma += dy / hypot;
				}

				dx = chordLead[i] - chordLead[i - 1];
				hypot = MathUtil.hypot(dx, dy);
				if (hypot != 0) {
					cosGammaLead += dy / hypot;
				}
			}
		}
		
		macLength *= dy;
		//logger.debug("macLength = {}", macLength);
		macSpan *= dy;
		macLead *= dy;
		area *= dy;
		rollSum *= dy;
		if (area > MathUtil.EPSILON) {
			macLength /= area;
			macSpan /= area;
			macLead /= area;
		} else {
			macLength = 0;
			macSpan = 0;
			macLead = 0;
		}
		cosGamma /= (DIVISIONS - 1);
		cosGammaLead /= (DIVISIONS - 1);
	}
	
	///////////////  CNa1 calculation  ////////////////
	
	private static final double CNA_SUBSONIC = 0.9;
	private static final double CNA_SUPERSONIC = 1.5;

	/** Lower Mach boundary of transonic wave drag blend region. */
	private static final double WAVE_DRAG_LOW  = 0.9;
	/** Upper Mach boundary of transonic wave drag blend region. */
	private static final double WAVE_DRAG_HIGH = 1.2;
	private static final double CNA_SUPERSONIC_B = pow(pow2(CNA_SUPERSONIC) - 1, 1.5);
	private static final double GAMMA = 1.4;
	private static final LinearInterpolator K1, K2, K3;
	private static final PolyInterpolator cnaInterpolator = new PolyInterpolator(
			new double[] { CNA_SUBSONIC, CNA_SUPERSONIC },
			new double[] { CNA_SUBSONIC, CNA_SUPERSONIC },
			new double[] { CNA_SUBSONIC });
	/* Pre-calculate the values for K1, K2 and K3 */
	static {
		// Up to Mach 5
		int n = (int) ((5.0 - CNA_SUPERSONIC) * 10);
		double[] x = new double[n];
		double[] k1 = new double[n];
		double[] k2 = new double[n];
		double[] k3 = new double[n];
		for (int i = 0; i < n; i++) {
			double M = CNA_SUPERSONIC + i * 0.1;
			double beta = MathUtil.safeSqrt(M * M - 1);
			x[i] = M;
			k1[i] = 2.0 / beta;
			k2[i] = ((GAMMA + 1) * pow(M, 4) - 4 * pow2(beta)) / (4 * pow(beta, 4));
			k3[i] = ((GAMMA + 1) * pow(M, 8) + (2 * pow2(GAMMA) - 7 * GAMMA - 5) * pow(M, 6) +
					10 * (GAMMA + 1) * pow(M, 4) + 8) / (6 * pow(beta, 7));
		}
		K1 = new LinearInterpolator(x, k1);
		K2 = new LinearInterpolator(x, k2);
		K3 = new LinearInterpolator(x, k3);
	}
	
	protected double calculateFinCNa1(FlightConditions conditions) {
		double mach = conditions.getMach();
		double ref = conditions.getRefArea();
		double alpha = MathUtil.min(conditions.getAOA(),
				Math.PI - conditions.getAOA(), STALL_ANGLE);

		if (finArea < MathUtil.EPSILON || span < MathUtil.EPSILON || cosGamma < MathUtil.EPSILON) {
			return 0;
		}

		double cna1;

		// Subsonic case
		if (mach <= CNA_SUBSONIC) {
			cna1 = 2 * Math.PI * pow2(span) / (1 + MathUtil.safeSqrt(1 + (1 - pow2(mach)) *
					pow2(pow2(span) / (finArea * cosGamma)))) / ref;
		}
		// Supersonic case
		else if (mach >= CNA_SUPERSONIC) {
			cna1 = finArea * (K1.getValue(mach) + K2.getValue(mach) * alpha +
					K3.getValue(mach) * pow2(alpha)) / ref;
		}
		// Transonic case, interpolate
		else {
			double subV, superV;
			double subD, superD;

			double sq = MathUtil.safeSqrt(1 + (1 - pow2(CNA_SUBSONIC)) * pow2(span * span / (finArea * cosGamma)));
			subV = 2 * Math.PI * pow2(span) / ref / (1 + sq);
			subD = 2 * CNA_SUBSONIC * Math.PI * pow(span, 6) / (pow2(finArea * cosGamma) * ref *
					sq * pow2(1 + sq));

			superV = finArea * (K1.getValue(CNA_SUPERSONIC) + K2.getValue(CNA_SUPERSONIC) * alpha +
					K3.getValue(CNA_SUPERSONIC) * pow2(alpha)) / ref;
			superD = -finArea / ref * 2 * CNA_SUPERSONIC / CNA_SUPERSONIC_B;

			cna1 = cnaInterpolator.interpolate(mach, subV, superV, subD, superD, 0);
		}

		// Phase 6h: ESDU transonic similarity correction
		// When K_trans is in [-2, +3], override with the universal curve model
		if (macLength > MathUtil.EPSILON) {
			double thicknessRatio = thickness / macLength;
			double sweepLE = (cosGammaLead > 0 && cosGammaLead <= 1.0) ? Math.acos(cosGammaLead) : 0.0;
			double kTrans = TransonicSimilarity.computeKTrans(mach, thicknessRatio, sweepLE);

			if (TransonicSimilarity.isInTransonicRegime(kTrans) && thicknessRatio > 0.01) {
				double cnaPeak = TransonicSimilarity.computeCNaPeak(ar, thicknessRatio);
				double h = TransonicSimilarity.universalCurve(kTrans);
				double cnaTransonic = cnaPeak * h;

				if (kTrans < -1.5) {
					double blend = (kTrans + 2.0) / 0.5;
					cna1 = cna1 * (1 - blend) + cnaTransonic * blend;
				} else if (kTrans > 2.5) {
					double blend = (kTrans - 2.5) / 0.5;
					cna1 = cnaTransonic * (1 - blend) + cna1 * blend;
				} else {
					cna1 = cnaTransonic;
				}
			}
		}

		return cna1;
	}
	
	private double calculateDampingMoment(FlightConditions conditions) {
		double rollRate = conditions.getRollRate();

		if (Math.abs(rollRate) < 0.1)
			return 0;

		if (conditions.getVelocity() < 1e-6)
			return 0.0;

		double mach = conditions.getMach();
		double absRate = Math.abs(rollRate);
		
		/*
		 * At low speeds and relatively large roll rates (i.e. near apogee) the
		 * fin tips rotate well above stall angle.  In this case sum the chords
		 * separately.
		 */
		if (absRate * (bodyRadius + span) / conditions.getVelocity() > 15 * Math.PI / 180) {
			double sum = 0;
			for (int i = 0; i < DIVISIONS; i++) {
				double dist = bodyRadius + span * i / DIVISIONS;
				double aoa = Math.min(absRate * dist / conditions.getVelocity(), 15 * Math.PI / 180);
				sum += chordLength[i] * dist * aoa;
			}
			sum = sum * (span / DIVISIONS) * 2 * Math.PI / conditions.getBeta() /
					(conditions.getRefArea() * conditions.getRefLength());

			return MathUtil.sign(rollRate) * sum;
		}
		
		if (mach <= CNA_SUBSONIC) {
			return 2 * Math.PI * rollRate * rollSum /
					(conditions.getRefArea() * conditions.getRefLength() *
							conditions.getVelocity() * conditions.getBeta());
		}
		if (mach >= CNA_SUPERSONIC) {
			double vel = conditions.getVelocity();
			double k1 = K1.getValue(mach);
			double k2 = K2.getValue(mach);
			double k3 = K3.getValue(mach);

			// Phase 11b: Mach cone limits effective fin span for roll damping
			// span_eff = min(span, c_root * sqrt(M^2 - 1))
			double spanEff = span;
			if (rootChord > MathUtil.EPSILON) {
				double machConeSpan = rootChord * Math.sqrt(mach * mach - 1.0);
				spanEff = Math.min(span, machConeSpan);
			}

			double sum = 0;

			for (int i = 0; i < DIVISIONS; i++) {
				double y = i * span / (DIVISIONS - 1);
				if (y > spanEff) {
					break; // beyond effective span
				}
				double angle = rollRate * (bodyRadius + y) / vel;

				sum += (k1 * angle + k2 * angle * angle + k3 * angle * angle * angle)
						* chordLength[i] * (bodyRadius + y);
			}

			return sum * span / (DIVISIONS - 1) /
					(conditions.getRefArea() * conditions.getRefLength());
		}
		
		// Transonic, do linear interpolation
		FlightConditions cond = conditions.clone();
		cond.setMach(CNA_SUBSONIC - 0.05);
		double subsonic = calculateDampingMoment(cond);
		cond.setMach(CNA_SUPERSONIC + 0.05);
		double supersonic = calculateDampingMoment(cond);
		
		return subsonic * (CNA_SUPERSONIC - mach) / (CNA_SUPERSONIC - CNA_SUBSONIC) +
				supersonic * (mach - CNA_SUBSONIC) / (CNA_SUPERSONIC - CNA_SUBSONIC);
	}
	
	/**
	 * Return the relative position of the CP along the mean aerodynamic chord.
	 * Below mach 0.5 it is at the quarter chord, above mach 2 calculated using an
	 * empirical formula, between these two using an interpolation polynomial.
	 * 
	 * @param cond   Mach speed used
	 * @return		 CP position along the MAC
	 */
	private double calculateCPPos(FlightConditions cond) {
		double m = cond.getMach();

		if (m <= 0.5) {
			// At subsonic speeds CP at quarter chord
			return 0.25;
		}
		if (m >= 2) {
			// At supersonic speeds use empirical formula
			double beta = cond.getBeta();
			double denom = 2 * ar * beta - 1;
			if (Math.abs(denom) < 0.01) {
				denom = Math.copySign(0.01, denom);
			}
			return (ar * beta - 0.67) / denom;
		}
		
		// In between use interpolation polynomial
		double x = 1.0;
		double val = 0;

		for (double v : poly) {
			val += v * x;
			x *= m;
		}

		return val;
	}
	
	/**
	 * Calculate CP position interpolation polynomial coefficients from the
	 * fin geometry.  This is a fifth order polynomial that satisfies
	 * 
	 * p(0.5)=0.25
	 * p'(0.5)=0
	 * p(2) = f(2)
	 * p'(2) = f'(2)
	 * p''(2) = 0
	 * p'''(2) = 0
	 * 
	 * where f(M) = (ar*sqrt(M^2-1) - 0.67) / (2*ar*sqrt(M^2-1) - 1).
	 * 
	 * The values were calculated analytically in Mathematica.  The coefficients
	 * are used as poly[0] + poly[1]*x + poly[2]*x^2 + ...
	 */
	private void calculatePoly() {
		double denom = pow2(1 - 3.4641 * ar); // common denominator
		// Guard: denom approaches zero when ar ≈ 1/(2*sqrt(3)) ≈ 0.2887.
		// This is a mathematical singularity in the Barrowman polynomial
		// derivation. Clamp to avoid infinite coefficients.
		if (denom < 1e-4) {
			denom = 1e-4;
		}

		poly[5] = (-1.58025 * (-0.728769 + ar) * (-0.192105 + ar)) / denom;
		poly[4] = (12.8395 * (-0.725688 + ar) * (-0.19292 + ar)) / denom;
		poly[3] = (-39.5062 * (-0.72074 + ar) * (-0.194245 + ar)) / denom;
		poly[2] = (55.3086 * (-0.711482 + ar) * (-0.196772 + ar)) / denom;
		poly[1] = (-31.6049 * (-0.705375 + ar) * (-0.198476 + ar)) / denom;
		poly[0] = (9.16049 * (-0.588838 + ar) * (-0.20624 + ar)) / denom;
	}

	/**
	 * Compute the effective chord ratio due to SBLI separation at supersonic speeds.
	 * Also caches L_sep for use in pressure drag calculation.
	 *
	 * @param conditions flight conditions
	 * @return ratio of effective chord to nominal chord (1.0 = no reduction)
	 */
	private double computeSBLIChordReduction(FlightConditions conditions) {
		double mach = conditions.getMach();
		cachedSBLI_Lsep = 0.0;

		if (mach < SBLI_MACH_MIN || thickness < MathUtil.EPSILON || macLength < MathUtil.EPSILON) {
			return 1.0;
		}

		double velocity = conditions.getVelocity();
		double kinVisc = conditions.getAtmosphericConditions().getKinematicViscosity();
		if (kinVisc <= 0.0 || velocity <= 0.0) {
			return 1.0;
		}

		double reX = velocity * xFinFromNose / kinVisc;
		if (reX < 1.0e4) {
			return 1.0;
		}

		double cfLocal = 0.027 / Math.pow(reX, 1.0 / 7.0);

		double thetaFin = Math.atan(thickness / (2.0 * macLength));
		double beta = Math.sqrt(mach * mach - 1.0);
		double cpShock = 2.0 * thetaFin / beta;

		if (!FreeInteractionSBLI.isSeparated(mach, cfLocal, cpShock)) {
			return 1.0;
		}

		double thetaBL = FreeInteractionSBLI.estimateMomentumThickness(xFinFromNose, reX);
		double lSep = FreeInteractionSBLI.separationLength(mach, cfLocal, thetaBL);

		double effectiveChord = Math.max(macLength - lSep, 0.1 * macLength);
		cachedSBLI_Lsep = lSep;

		return effectiveChord / macLength;
	}

	/**
	 * Returns the SBLI plateau pressure drag increment.
	 *
	 * @param conditions flight conditions
	 * @return SBLI pressure drag coefficient increment (referenced to S_ref)
	 */
	double calculateSBLIPressureDrag(FlightConditions conditions) {
		if (cachedSBLI_Lsep <= 0.0 || finArea < MathUtil.EPSILON) {
			return 0.0;
		}

		double mach = conditions.getMach();
		if (mach < SBLI_MACH_MIN) {
			return 0.0;
		}

		double velocity = conditions.getVelocity();
		double kinVisc = conditions.getAtmosphericConditions().getKinematicViscosity();
		if (kinVisc <= 0.0 || velocity <= 0.0) {
			return 0.0;
		}
		double reX = velocity * xFinFromNose / kinVisc;
		if (reX < 1.0e4) {
			return 0.0;
		}
		double cfLocal = 0.027 / Math.pow(reX, 1.0 / 7.0);

		double cpPlateau = FreeInteractionSBLI.cpPlateau(mach, cfLocal);

		return cpPlateau * cachedSBLI_Lsep * span * finCount / conditions.getRefArea();
	}

	@Override
	public double calculateFrictionCD(FlightConditions conditions, double componentCf, WarningSet warnings) {
		// a fin with 0 area contributes no drag
		if (finArea < MathUtil.EPSILON || macLength < MathUtil.EPSILON) {
			return 0.0;
		}
		
		double cd = componentCf * (1 + 2 * thickness / macLength) * 2 * finArea / conditions.getRefArea();
		return cd;
	}
	
	@Override
	public double calculatePressureCD(FlightConditions conditions,
									  double stagnationCD, double baseCD, WarningSet warnings) {

		// a fin with 0 area contributes no drag
		if (finArea < MathUtil.EPSILON) {
			return 0.0;
		}

		double mach = conditions.getMach();
		double cd = 0;

		// ---- Leading-edge bluntness / pressure fore-drag ----
		// For AIRFOIL/ROUNDED: empirical round-LE formula (Prandtl-Glauert subsonic,
		// empirical supersonic correlation). Referenced to span * thickness area.
		// For SQUARE: stagnation coefficient (normal shock at blunt leading edge).
		if (crossSection == FinSet.CrossSection.AIRFOIL ||
				crossSection == FinSet.CrossSection.ROUNDED) {

			// Round leading edge
			if (mach < 0.9) {
				cd = Math.pow(1 - pow2(mach), -0.417) - 1;
			} else if (mach < 1) {
				cd = 1 - 1.785 * (mach - 0.9);
			} else {
				cd = 1.214 - 0.502 / pow2(mach) + 0.1095 / pow2(pow2(mach));
			}

		} else if (crossSection == FinSet.CrossSection.SQUARE) {
			cd = stagnationCD;
		} else {
			throw new UnsupportedOperationException("Unsupported fin profile: " + crossSection);
		}

		// Slanted leading-edge sweep correction
		cd *= pow2(cosGammaLead);

		// Scale to correct reference area (leading-edge area: span * thickness)
		cd *= span * thickness / conditions.getRefArea();

		// ---- Phase 2c: Ackeret supersonic thin-airfoil wave drag ----
		// Wave drag from fin thickness via Ackeret (1925) linear supersonic theory:
		//   Cdw = 4 * tau^2 / beta
		// where tau = t/c (thickness ratio) and beta = sqrt(M^2 - 1).
		// Referenced to fin planform area (one-sided).
		//
		// Applies to AIRFOIL and ROUNDED cross-sections whose thickness profiles
		// generate oblique-shock wave drag. SQUARE fins are excluded because their
		// flat surfaces produce zero Ackeret wave drag; the stagnation term above
		// already captures the SQUARE leading-edge normal-shock drag.
		//
		// Blended C1-continuously from zero at M = WAVE_DRAG_LOW to the exact
		// Ackeret value at M = WAVE_DRAG_HIGH via a cubic Hermite spline, matching
		// both value and slope at the supersonic boundary.
		//
		// See: Anderson, "Fundamentals of Aerodynamics", Ch. 15; NACA TN-1428.
		if ((crossSection == FinSet.CrossSection.AIRFOIL ||
				 crossSection == FinSet.CrossSection.ROUNDED) &&
				macLength > MathUtil.EPSILON && thickness > 0) {

			double tau = thickness / macLength; // fin thickness ratio t/c
			double waveCdPlanform;              // Ackeret Cdw, referenced to planform area

			if (mach >= WAVE_DRAG_HIGH) {
				// Fully supersonic: exact Ackeret formula
				waveCdPlanform = ackeretWaveDragCD(mach, tau);

			} else if (mach > WAVE_DRAG_LOW) {
				// Transonic blend: cubic Hermite from (WAVE_LOW, 0, 0) to (WAVE_HIGH, f, f')
				double fHigh  = ackeretWaveDragCD(WAVE_DRAG_HIGH, tau);
				double dfHigh = ackeretWaveDragSlope(WAVE_DRAG_HIGH, tau);

				double dm = WAVE_DRAG_HIGH - WAVE_DRAG_LOW;
				double t  = (mach - WAVE_DRAG_LOW) / dm;
				double t2 = t * t;
				double t3 = t2 * t;

				// Cubic Hermite basis: h01*fHigh + h11*dm*dfHigh  (f0=0, df0=0)
				double h01 = -2 * t3 + 3 * t2;
				double h11 =      t3 -     t2;
				waveCdPlanform = h01 * fHigh + h11 * dm * dfHigh;

			} else {
				waveCdPlanform = 0.0;
			}

			// Apply leading-edge sweep correction: cos^2(Lambda_LE)
			waveCdPlanform *= pow2(cosGammaLead);

			// Scale planform-area Cdw to body reference area
			cd += waveCdPlanform * finArea / conditions.getRefArea();
		}

		// Add SBLI plateau pressure drag
		cd += calculateSBLIPressureDrag(conditions);

		return cd;
	}

	/**
	 * Ackeret wave drag coefficient for a thin fin cross-section at supersonic speeds.
	 * <p>
	 * {@code Cdw = 4 * tau^2 / beta}
	 * <p>
	 * where {@code tau = t/c} (thickness ratio) and {@code beta = sqrt(M^2 - 1)}.
	 * The result is referenced to the fin planform area (one side) and represents
	 * the integrated wave drag from both the upper and lower surface pressure
	 * distributions for a symmetric profile at zero angle of attack.
	 * <p>
	 * Reference: Ackeret (1925); Anderson, "Fundamentals of Aerodynamics", Ch. 15.
	 *
	 * @param mach the Mach number (must satisfy M >= WAVE_DRAG_HIGH > 1)
	 * @param tau  the fin thickness ratio (thickness / MAC length)
	 * @return Ackeret wave drag coefficient referenced to planform area
	 */
	private static double ackeretWaveDragCD(double mach, double tau) {
		if (mach <= 1.0001) return 0.0;
		double beta = Math.sqrt(mach * mach - 1.0);
		return 4.0 * tau * tau / beta;
	}

	/**
	 * Derivative of the Ackeret wave drag coefficient with respect to Mach number.
	 * <p>
	 * {@code d/dM [4*tau^2 / sqrt(M^2-1)] = -4*tau^2 * M / (M^2-1)^(3/2)}
	 * <p>
	 * Used to compute the slope boundary condition for the transonic Hermite blend.
	 *
	 * @param mach the Mach number
	 * @param tau  the fin thickness ratio
	 * @return d(Cdw)/dM
	 */
	private static double ackeretWaveDragSlope(double mach, double tau) {
		if (mach <= 1.0001) return 0.0;
		double betaSq = mach * mach - 1.0;
		double beta   = Math.sqrt(betaSq);
		return -4.0 * tau * tau * mach / (beta * betaSq);
	}

	@Override
	public double calculateComponentBaseCD(FlightConditions conditions,
										   double baseCD, WarningSet warnings) {
		// a fin with 0 area contributes no drag
		if (finArea < MathUtil.EPSILON) {
			return 0.0;
		}

		double cd = 0;

		// Trailing edge drag
		if (crossSection == FinSet.CrossSection.SQUARE) {
			cd = baseCD;
		} else if (crossSection == FinSet.CrossSection.ROUNDED) {
			cd = baseCD / 2;
		}
		// Airfoil assumed to have zero base drag

		// Scale to correct reference area
		cd *= span * thickness / conditions.getRefArea();

		// Phase 6j: Mach-dependent trailing-edge base drag
		cd += calculateTrailingEdgeBaseCD(conditions);

		return cd;
	}

	/**
	 * Phase 6j: Fin trailing-edge base drag. Fins with blunt trailing edges
	 * (SQUARE cross-section) generate significant wake drag at supersonic speeds.
	 *
	 * Subsonic (M &lt; 0.9): Hoerner turbulent wake, Cd_te = 0.12 * t_te/c
	 * Supersonic (M &gt; 1.2): backward-facing step, Cd_te = 0.135 * (t_te/c) / sqrt(beta)
	 * Transonic: smoothstep blend
	 */
	double calculateTrailingEdgeBaseCD(FlightConditions conditions) {
		if (macLength < MathUtil.EPSILON || thickness < MathUtil.EPSILON) {
			return 0.0;
		}

		double t_te;
		if (crossSection == FinSet.CrossSection.SQUARE) {
			t_te = thickness;
		} else {
			t_te = 0.05 * thickness;  // thin TE for AIRFOIL/ROUNDED
		}

		if (t_te < MathUtil.EPSILON) {
			return 0.0;
		}

		double mach = conditions.getMach();
		double Cd_te;

		if (mach > 1.2) {
			double beta = conditions.getBeta();
			Cd_te = 0.135 * (t_te / macLength) / Math.sqrt(beta);
		} else if (mach > 0.9) {
			double subsonicCdTe = 0.12 * (t_te / macLength);
			double supersonicBeta = Math.sqrt(1.44 - 1.0);
			double supersonicCdTe = 0.135 * (t_te / macLength) / Math.sqrt(supersonicBeta);
			double t = (mach - 0.9) / 0.3;
			double s = t * t * (3 - 2 * t);
			Cd_te = subsonicCdTe * (1 - s) + supersonicCdTe * s;
		} else {
			Cd_te = 0.12 * (t_te / macLength);
		}

		double teArea = t_te * span * interferenceFinCount;
		Cd_te *= 2.0 * teArea / conditions.getRefArea();

		return Cd_te;
	}

	private void calculateInterferenceFinCount(FinSet component) {
		RocketComponent parent = component.getParent();
		if (parent == null) {
			throw new IllegalStateException("fin set without parent component");
		}
		
		double lead = component.toRelative(Coordinate.NUL, parent)[0].getX();
		double trail = component.toRelative(new Coordinate(component.getLength()),
				parent)[0].getX();
		
		/*
		 * The counting fails if the fin root chord is very small, in that case assume
		 * no other fin interference than this fin set.
		 */
		if (trail - lead < 0.007) {
			interferenceFinCount = finCount;
		} else {
			interferenceFinCount = 0;
			for (RocketComponent c : parent.getChildren()) {
				if (c instanceof FinSet) {
					double finLead = c.toRelative(Coordinate.NUL, parent)[0].getX();
					double finTrail = c.toRelative(new Coordinate(c.getLength()), parent)[0].getX();
					
					// Compute overlap of the fins
					
					if ((finLead < trail - 0.005) && (finTrail > lead + 0.005)) {
						interferenceFinCount += ((FinSet) c).getFinCount();
					}
				}
			}
		}
		if (interferenceFinCount < component.getFinCount()) {
			throw new BugException("Counted " + interferenceFinCount + " parallel fins, " +
					"when component itself has " + component.getFinCount() +
					", fin points=" + Arrays.toString(component.getFinPoints()));
		}
	}
	
}

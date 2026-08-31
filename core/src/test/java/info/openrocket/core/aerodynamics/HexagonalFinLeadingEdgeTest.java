package info.openrocket.core.aerodynamics;

import info.openrocket.core.aerodynamics.barrowman.FinSetCalc;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Leading-edge wave drag for HEXAGONAL (double-wedge) fins whose edge is a blunt chamfer.
 * <p>
 * The HEXAGONAL branch of {@code FinSetCalc.calculatePressureCD} used to return zero
 * unconditionally, assuming a sharp wedge ("&lt; 5 deg"). Fins cut from plate and chamfered
 * violate that badly -- A-601 Kinsel runs 0.25 in stock with a 0.125 in bevel, a 45 deg
 * half-angle -- and the resulting missing drag was the single largest residual in the
 * SimVReal corpus (+8.7% apogee over-prediction).
 */
public class HexagonalFinLeadingEdgeTest extends BaseTestCase {

	private static final double IN = 0.0254;

	/** Kinsel-like fin: 0.25 in stock, 0.125 in bevel (45 deg half-angle), 53.6 deg sweep. */
	private static TrapezoidFinSet kinselLikeFins(Double bevelMetres) {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		TrapezoidFinSet fins = (TrapezoidFinSet) rocket.getChild(0).getChild(1).getChild(0);
		fins.setCrossSection(FinSet.CrossSection.HEXAGONAL);
		fins.setFinCount(4);
		fins.setRootChord(17 * IN);
		fins.setTipChord(6 * IN);
		fins.setHeight(7 * IN);
		fins.setSweep(9.5 * IN);
		fins.setThickness(0.25 * IN);
		fins.setLeadingEdgeBevelLength(bevelMetres == null ? Double.NaN : bevelMetres);
		return fins;
	}

	private static double pressureCD(TrapezoidFinSet fins, double mach) {
		Rocket rocket = (Rocket) fins.getRoot();
		FlightConfiguration cfg = rocket.getSelectedConfiguration();
		FlightConditions cond = new FlightConditions(cfg);
		cond.setMach(mach);
		FinSetCalc calc = new FinSetCalc(fins);
		return calc.calculatePressureCD(cond,
				BarrowmanDragCalculator.calculateStagnationCD(mach),
				BarrowmanDragCalculator.calculateBaseCD(mach),
				new WarningSet());
	}

	@Test
	public void unknownBevelKeepsTheSharpEdgeAssumption() {
		// Backward compatibility: geometry that does not state a bevel must behave exactly
		// as before, so no existing rocket changes.
		double blunt = pressureCD(kinselLikeFins(0.125 * IN), 2.19);
		double unknown = pressureCD(kinselLikeFins(null), 2.19);
		assertTrue(blunt > unknown,
				"a stated 45 deg bevel must cost more than unspecified geometry");
	}

	@Test
	public void sharpBevelAddsNothing() {
		// A 2.7 deg half-angle (Proteus 6's fins) is genuinely sharp: below the 5 deg gate.
		double sharp = pressureCD(kinselLikeFins(2.0 * IN), 2.19);
		double unknown = pressureCD(kinselLikeFins(null), 2.19);
		assertEquals(unknown, sharp, 1e-12,
				"a sharp wedge must not pick up blunt-edge drag");
	}

	@Test
	public void subsonicLeadingEdgeIsExactlyUnchanged() {
		// The term is gated on a supersonic leading edge, M_n = M cos(Lambda) > 1. With
		// Lambda = 53.6 deg that needs M > 1.68, so the whole subsonic corpus is untouched.
		// This is the invariant that kept all 16 subsonic SimVReal flights at exactly 0.00.
		for (double mach : new double[] { 0.3, 0.8, 0.95, 1.05, 1.22, 1.5 }) {
			double blunt = pressureCD(kinselLikeFins(0.125 * IN), mach);
			double unknown = pressureCD(kinselLikeFins(null), mach);
			assertEquals(unknown, blunt, 1e-12,
					"blunt-edge drag must be identically zero at M=" + mach
							+ " (leading edge still subsonic)");
		}
	}

	@Test
	public void bluntEdgeMatchesHandComputedModifiedNewtonian() {
		// Independent analytical check of the whole term, recomputed here from the
		// references rather than from the implementation:
		//   simple sweep (Jones, NACA 863):  M_n = M cos(Lambda),
		//                                    theta_n = atan(tan(theta)/cos(Lambda))
		//   modified Newtonian:              Cp = Cp_max(M_n) sin^2(theta_n)
		//   swept force + LE reference area: * cos^2(Lambda) * span*t/Sref
		double mach = 2.5; // past the onset ramp for this 53.6 deg sweep
		double bluntTerm = pressureCD(kinselLikeFins(0.125 * IN), mach)
				- pressureCD(kinselLikeFins(null), mach);

		TrapezoidFinSet fins = kinselLikeFins(0.125 * IN);
		Rocket rocket = (Rocket) fins.getRoot();
		FlightConditions cond = new FlightConditions(rocket.getSelectedConfiguration());
		cond.setMach(mach);

		double cosLambda = Math.cos(Math.atan(fins.getSweep() / fins.getHeight()));
		double halfAngle = Math.atan((fins.getThickness() / 2.0) / (0.125 * IN));
		double machNormal = mach * cosLambda;
		double thetaNormal = Math.atan(Math.tan(halfAngle) / cosLambda);
		double cp = BarrowmanDragCalculator.calculateStagnationCD(machNormal)
				* Math.pow(Math.sin(thetaNormal), 2);
		double expected = cp * cosLambda * cosLambda
				* fins.getSpan() * fins.getThickness() / cond.getRefArea();

		assertTrue(machNormal > 1.15, "test premise: ramp should be complete at M=" + mach);
		assertEquals(expected, bluntTerm, 0.10 * expected,
				"blunt-edge term should match hand-computed modified Newtonian within 10%");
	}

	@Test
	public void dragIsMonotonicInBluntness() {
		double prev = -1;
		for (double bevelIn : new double[] { 0.5, 0.35, 0.25, 0.18, 0.125 }) {
			double cd = pressureCD(kinselLikeFins(bevelIn * IN), 2.5);
			assertTrue(cd >= prev,
					"shorter bevel (blunter edge) must not reduce drag at bevel=" + bevelIn);
			prev = cd;
		}
	}

	@Test
	public void noStepAcrossTheActivationBoundary() {
		// Both ramps (leading-edge Mach and shock detachment) exist so Cd(M) has no jump,
		// which the trajectory stepper requires. Measured relative to the term's own size,
		// since this fin is deliberately grafted onto a small test body and the absolute
		// coefficient is therefore not representative.
		double fullTerm = pressureCD(kinselLikeFins(0.125 * IN), 3.0)
				- pressureCD(kinselLikeFins(null), 3.0);
		assertTrue(fullTerm > 0, "sanity: term must be active at M=3");

		// Test continuity directly rather than against a hand-picked slope bound. For a
		// continuous function the largest sampled step shrinks in proportion to the sample
		// spacing; across a jump discontinuity it does not shrink at all. So refine the
		// sampling 4x and require the max step to fall by roughly 4x. This needs no tuned
		// constant and would fail loudly if either ramp were removed.
		double coarse = maxStep(0.02);
		double fine = maxStep(0.005);
		assertTrue(coarse > 0 && fine > 0, "sanity: term must vary across the onset");

		double shrink = coarse / fine;
		assertTrue(shrink > 3.0,
				"max step shrank only " + shrink + "x when sampling was refined 4x, which "
						+ "indicates a discontinuity rather than a smooth ramp");
	}

	/** Largest |dCd| between adjacent samples of the blunt-edge term across the onset. */
	private static double maxStep(double dMach) {
		double worst = 0;
		double prev = pressureCD(kinselLikeFins(0.125 * IN), 1.50);
		for (double mach = 1.50 + dMach; mach <= 3.0; mach += dMach) {
			double cd = pressureCD(kinselLikeFins(0.125 * IN), mach);
			worst = Math.max(worst, Math.abs(cd - prev));
			prev = cd;
		}
		return worst;
	}
}

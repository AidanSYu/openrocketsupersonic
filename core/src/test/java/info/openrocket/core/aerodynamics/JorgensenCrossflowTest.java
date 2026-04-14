package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.aerodynamics.barrowman.SymmetricComponentCalc;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.TestRockets;
import info.openrocket.core.util.Transformation;

/**
 * Tests for Jorgensen (NASA TR R-474, 1977) crossflow drag coefficient model
 * implemented in SymmetricComponentCalc.
 */
public class JorgensenCrossflowTest {

	private static final double EPSILON = 1e-6;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	@Test
	public void crossflowCdAtZeroMach() {
		assertEquals(1.20, SymmetricComponentCalc.getCrossflowDragCoefficient(0.0), EPSILON,
				"Cd_c at M_cf = 0 should be 1.20");
	}

	@Test
	public void crossflowCdSubsonic() {
		// At M_cf = 0.3, between table points 0.2 (1.20) and 0.4 (1.20)
		assertEquals(1.20, SymmetricComponentCalc.getCrossflowDragCoefficient(0.3), EPSILON,
				"Cd_c at M_cf = 0.3 should be 1.20");
	}

	@Test
	public void crossflowCdTransonic() {
		double cdAt08 = SymmetricComponentCalc.getCrossflowDragCoefficient(0.8);
		double cdAt10 = SymmetricComponentCalc.getCrossflowDragCoefficient(1.0);
		assertEquals(1.35, cdAt08, EPSILON, "Cd_c at M_cf = 0.8 should be 1.35");
		assertEquals(1.65, cdAt10, EPSILON, "Cd_c at M_cf = 1.0 should be 1.65");
		assertTrue(cdAt08 >= 1.35 && cdAt08 <= 1.65,
				"Cd_c at M_cf = 0.8 should be between 1.35 and 1.65");
		assertTrue(cdAt10 >= 1.35 && cdAt10 <= 1.65,
				"Cd_c at M_cf = 1.0 should be between 1.35 and 1.65");
	}

	@Test
	public void crossflowCdSupersonic() {
		double cdAt12 = SymmetricComponentCalc.getCrossflowDragCoefficient(1.2);
		double cdAt30 = SymmetricComponentCalc.getCrossflowDragCoefficient(3.0);
		assertEquals(1.80, cdAt12, EPSILON, "Cd_c at M_cf = 1.2 should be 1.80");
		assertEquals(2.00, cdAt30, EPSILON, "Cd_c at M_cf = 3.0 should be 2.00");
	}

	@Test
	public void crossflowCdClampsAboveMach5() {
		assertEquals(2.00, SymmetricComponentCalc.getCrossflowDragCoefficient(5.0), EPSILON,
				"Cd_c at M_cf = 5.0 should be 2.00");
		assertEquals(2.00, SymmetricComponentCalc.getCrossflowDragCoefficient(10.0), EPSILON,
				"Cd_c at M_cf = 10.0 should be clamped to 2.00");
		assertEquals(2.00, SymmetricComponentCalc.getCrossflowDragCoefficient(100.0), EPSILON,
				"Cd_c at M_cf = 100.0 should be clamped to 2.00");
	}

	@Test
	public void crossflowCdInterpolates() {
		// Between M_cf = 0.8 (1.35) and M_cf = 0.9 (1.50), check midpoint
		double cdAt085 = SymmetricComponentCalc.getCrossflowDragCoefficient(0.85);
		double expected = 1.35 + 0.5 * (1.50 - 1.35); // linear interp = 1.425
		assertEquals(expected, cdAt085, EPSILON,
				"Cd_c at M_cf = 0.85 should linearly interpolate to 1.425");

		// Between M_cf = 1.0 (1.65) and M_cf = 1.2 (1.80), check midpoint
		double cdAt11 = SymmetricComponentCalc.getCrossflowDragCoefficient(1.1);
		double expected2 = 1.65 + 0.5 * (1.80 - 1.65); // linear interp = 1.725
		assertEquals(expected2, cdAt11, EPSILON,
				"Cd_c at M_cf = 1.1 should linearly interpolate to 1.725");
	}

	@Test
	public void crossflowCdNegativeMach() {
		// Negative crossflow Mach should return subsonic value
		assertEquals(1.20, SymmetricComponentCalc.getCrossflowDragCoefficient(-1.0), EPSILON,
				"Cd_c at negative M_cf should return 1.20");
	}

	@Test
	public void bodyCNaAtHighMachHighAoA() {
		// Cylindrical body tubes have CNa = 0 per the Barrowman area-change formula.
		// The Galejs viscous crossflow formula (K × planformArea/refArea) overestimates
		// body CNa for long slender bodies (e.g. 17 rad⁻¹ for a typical sounding-rocket
		// body) and, when supersonic fin CNa drops as 1/β, incorrectly drives CP forward
		// past the CG causing spurious instability.  RASAero II uses Barrowman (CNa=0)
		// for cylindrical sections; we do the same.
		//
		// A BodyTube calculator for a pure cylindrical component should produce zero CN
		// regardless of AoA and Mach number.
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		BodyTube body = (BodyTube) rocket.getChild(0).getChild(1);

		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions condLow = new FlightConditions(config);
		FlightConditions condHigh = new FlightConditions(config);

		condLow.setMach(0.5);
		condLow.setAOA(Math.toRadians(10));

		condHigh.setMach(3.0);
		condHigh.setAOA(Math.toRadians(20));

		SymmetricComponentCalc calc = new SymmetricComponentCalc(body);
		AerodynamicForces forcesLow = new AerodynamicForces();
		AerodynamicForces forcesHigh = new AerodynamicForces();
		WarningSet warnings = new WarningSet();

		calc.calculateNonaxialForces(condLow, Transformation.IDENTITY, forcesLow, warnings);
		calc.calculateNonaxialForces(condHigh, Transformation.IDENTITY, forcesHigh, warnings);

		double cnLow = forcesLow.getCN();
		double cnHigh = forcesHigh.getCN();

		// Body tube CNa = 0 (Barrowman); CN = CNa × AoA = 0 at all Mach/AoA combinations
		assertEquals(0.0, cnLow, 1e-10,
				"Body tube CN at subsonic M=0.5 should be zero (Barrowman: no area change)");
		assertEquals(0.0, cnHigh, 1e-10,
				"Body tube CN at supersonic M=3.0 should be zero (Barrowman: no area change)");

		// Verify the Jorgensen crossflow Cd table is still computed correctly — it is used
		// for drag calculation even though it no longer affects body-tube stability.
		// At M=3, alpha=20°: crossflowMach = 3*sin(20°) ≈ 1.026 → Cd_c ≈ 1.67 → scale ≈ 1.39
		double crossflowMachHigh = condHigh.getMach() * Math.sin(condHigh.getAOA());
		double crossflowScale = SymmetricComponentCalc.getCrossflowDragCoefficient(crossflowMachHigh) / 1.2;
		assertTrue(crossflowScale > 1.3,
				"Crossflow Cd scale at M_cf≈1.026 should be >1.3 (supersonic crossflow enhancement) " +
				"(crossflowScale=" + crossflowScale + ")");
	}

	@Test
	public void bodyCNaUnchangedAtSubsonic() {
		// At M=0.5, alpha=2deg: body tube has CNa=0 (Barrowman: no area change for
		// constant-diameter cylinder), so CN=0 regardless of AoA or Mach.
		// The crossflow Mach scale factor is still computed correctly for drag purposes.
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		BodyTube body = (BodyTube) rocket.getChild(0).getChild(1);

		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(0.5);
		conditions.setAOA(Math.toRadians(2));

		SymmetricComponentCalc calc = new SymmetricComponentCalc(body);
		AerodynamicForces forces = new AerodynamicForces();
		WarningSet warnings = new WarningSet();

		calc.calculateNonaxialForces(conditions, Transformation.IDENTITY, forces, warnings);

		double cn = forces.getCN();

		// Crossflow Mach = 0.5 * sin(2deg) ~ 0.0175, Cd_c = 1.20
		// Scale factor = 1.20 / 1.20 = 1.0 (correct crossflow table)
		double crossflowMach = 0.5 * Math.sin(Math.toRadians(2));
		double scale = SymmetricComponentCalc.getCrossflowDragCoefficient(crossflowMach) / 1.2;
		assertEquals(1.0, scale, 0.001,
				"Crossflow scale at M=0.5, alpha=2deg should be ~1.0");

		// Body tube CNa = 0 (Barrowman: no area change); CN = 0 at all Mach/AoA
		assertEquals(0.0, cn, 1e-10,
				"Body tube CN at subsonic M=0.5 should be zero (Barrowman: no area change)");
	}
}

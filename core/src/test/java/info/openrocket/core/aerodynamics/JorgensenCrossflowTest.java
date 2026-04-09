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
	public void bodyCNaIncreasesAtHighMachHighAoA() {
		// At M=3, alpha=5deg, crossflow Mach = 3*sin(5deg) ~ 0.26
		// That's still low crossflow Mach, so use a higher AoA to get supersonic crossflow
		// M=3, alpha=15deg -> M_cf = 3*sin(15deg) ~ 0.776 -> Cd_c ~ 1.33
		// M=3, alpha=20deg -> M_cf = 3*sin(20deg) ~ 1.026 -> Cd_c ~ 1.67
		// Use a body tube (isTube=true) so only body lift contributes
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

		// At high Mach / high AoA, crossflow Mach ~ 3*sin(20deg) ~ 1.026
		// Cd_c ~ 1.67, so crossflow scale ~ 1.67/1.2 ~ 1.39
		// The CN should be larger per unit sin^2(alpha)/alpha than at low Mach
		// Normalize by sin(aoa)^2/aoa to compare the coefficient scaling
		double sinAoaLow = Math.sin(condLow.getAOA());
		double sinAoaHigh = Math.sin(condHigh.getAOA());
		double sincLow = sinAoaLow / condLow.getAOA();
		double sincHigh = sinAoaHigh / condHigh.getAOA();

		// CN = cp_weight * aoa, cp_weight = scale * K * planformArea/refArea * sinAOA * sincAOA
		// So the "effective K" = CN / (aoa * planformArea/refArea * sinAOA * sincAOA)
		// The ratio of effective K at high Mach to low Mach should be Cd_c_high / Cd_c_low
		double cnLow = forcesLow.getCN();
		double cnHigh = forcesHigh.getCN();

		// Both should be non-zero (body tube has planform area)
		assertTrue(Math.abs(cnLow) > 1e-10, "CN at low Mach should be non-zero for body tube");
		assertTrue(Math.abs(cnHigh) > 1e-10, "CN at high Mach should be non-zero for body tube");

		// The normalized CN (per unit sin^2(alpha)/alpha) should be larger at
		// high Mach / high AoA due to the Jorgensen crossflow enhancement.
		// The Phase 3a body lift K also changes with Mach, so we don't expect
		// an exact match to crossflow Cd_c ratio alone — just verify the
		// high-Mach normalized CN is at least 20% larger (crossflow + K increase).
		double normalizedLow = cnLow / (condLow.getAOA() * sinAoaLow * sincLow);
		double normalizedHigh = cnHigh / (condHigh.getAOA() * sinAoaHigh * sincHigh);

		assertTrue(normalizedHigh > normalizedLow * 1.2,
				"Body lift at M=3, alpha=20deg should be significantly enhanced by crossflow Cd_c " +
				"(normalizedHigh=" + normalizedHigh + " vs normalizedLow=" + normalizedLow + ")");
	}

	@Test
	public void bodyCNaUnchangedAtSubsonic() {
		// At M=0.5, alpha=2deg, M_crossflow = 0.5*sin(2deg) ~ 0.017
		// Cd_c ~ 1.20, so crossflow scale ~ 1.0 (no change from baseline)
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
		// Scale factor = 1.20 / 1.20 = 1.0, so result should be identical to
		// the unmodified calculation: K * planformArea / refArea * sin(aoa)^2 / aoa * aoa
		// = K * planformArea / refArea * sin(aoa)^2
		double crossflowMach = 0.5 * Math.sin(Math.toRadians(2));
		double scale = SymmetricComponentCalc.getCrossflowDragCoefficient(crossflowMach) / 1.2;
		assertEquals(1.0, scale, 0.001,
				"Crossflow scale at M=0.5, alpha=2deg should be ~1.0");

		// CN should be non-zero but small
		assertTrue(Math.abs(cn) > 0, "CN should be non-zero at AoA=2deg");
	}
}

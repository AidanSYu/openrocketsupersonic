package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.TestRockets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

/**
 * Tests for the Viswanath (1996) boattail correction to base drag.
 * A boattail (convergent transition) upstream of the base energizes the wake,
 * reducing base drag. The correction factor depends on boattail half-angle
 * and Mach number.
 */
public class ViswanathBoattailTest {

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	/**
	 * Create a rocket with a nose cone, body tube, boattail transition, and optional tail tube.
	 * Returns the boattail transition (or the tail tube if present).
	 */
	private SymmetricComponent createBoattailRocket(double boattailAngleDeg, boolean withTailTube) {
		Rocket rocket = new Rocket();
		AxialStage stage = new AxialStage();
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.OGIVE, 0.10, 0.025);
		stage.addChild(nose);

		BodyTube body = new BodyTube(0.30, 0.025, 0.001);
		stage.addChild(body);

		// Boattail: transition from body radius to smaller aft radius
		double boattailLength = 0.05;
		double deltaR = boattailLength * Math.tan(Math.toRadians(boattailAngleDeg));
		double aftRadius = Math.max(0.005, 0.025 - deltaR);
		Transition boattail = new Transition();
		boattail.setShapeType(Transition.Shape.CONICAL);
		boattail.setForeRadius(0.025);
		boattail.setAftRadius(aftRadius);
		boattail.setLength(boattailLength);
		stage.addChild(boattail);

		if (withTailTube) {
			BodyTube tail = new BodyTube(0.05, aftRadius, 0.001);
			stage.addChild(tail);
			return tail;
		}

		return boattail;
	}

	/**
	 * A body tube with no boattail upstream should get factor = 1.0 (no correction).
	 */
	@Test
	public void testNoBoattailFactor() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		BodyTube body = (BodyTube) rocket.getChild(0).getChild(1);
		double factor = BarrowmanDragCalculator.calculateViswanathBoattailFactor(body, 2.0);
		assertEquals(1.0, factor, 1e-10,
				"No boattail should give factor = 1.0");
	}

	/**
	 * Mild boattail (5 degrees): significant base drag reduction.
	 * eta_bt = 0.25 + 0.05 * 5 = 0.50 (50% of original base drag)
	 */
	@Test
	public void testMildBoattail5Degrees() {
		SymmetricComponent component = createBoattailRocket(5.0, false);
		double factor = BarrowmanDragCalculator.calculateViswanathBoattailFactor(component, 0.5);
		// 5 degree boattail: eta = 0.25 + 0.05*5 = 0.50
		assertEquals(0.50, factor, 0.05,
				"5-degree boattail should give ~0.50 factor");
		assertTrue(factor < 1.0, "Boattail should reduce base drag");
	}

	/**
	 * Moderate boattail (10 degrees): near optimal benefit.
	 * eta_bt = 0.55 + 0.04*(10-6) = 0.71 at subsonic
	 */
	@Test
	public void testModerateBoattail10Degrees() {
		SymmetricComponent component = createBoattailRocket(10.0, false);
		double factor = BarrowmanDragCalculator.calculateViswanathBoattailFactor(component, 0.5);
		// 10 degree: eta_geom = 0.55 + 0.04*4 = 0.71, machFactor=1.0 at M=0.5
		assertTrue(factor > 0.5 && factor < 0.85,
				"10-degree boattail factor should be in [0.5, 0.85], got " + factor);
	}

	/**
	 * Steep boattail (20 degrees): benefit diminishes due to flow separation.
	 * eta_bt = max(0, 0.95 - 0.05*(20-16)) = 0.75
	 */
	@Test
	public void testSteepBoattail20Degrees() {
		SymmetricComponent component = createBoattailRocket(20.0, false);
		double factor = BarrowmanDragCalculator.calculateViswanathBoattailFactor(component, 0.5);
		// 20 degree: eta = max(0, 0.95 - 0.05*4) = 0.75
		assertTrue(factor > 0.5 && factor < 0.9,
				"20-degree boattail should give reduced benefit, got " + factor);
	}

	/**
	 * Very steep boattail (30+ degrees): approaching zero benefit (flow fully separated).
	 * Note: with our geometry (foreRadius=0.025, length=0.05), the maximum achievable
	 * half-angle is limited by the minimum aft radius of 0.005. So we use a shorter
	 * boattail length to actually achieve a 30-degree half-angle.
	 */
	@Test
	public void testVerySteepBoattail() {
		// Build a custom rocket with a true 30-degree boattail
		Rocket rocket = new Rocket();
		AxialStage stage = new AxialStage();
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.OGIVE, 0.10, 0.025);
		stage.addChild(nose);

		BodyTube body = new BodyTube(0.30, 0.025, 0.001);
		stage.addChild(body);

		// Short boattail to get a steep angle: deltaR/length = tan(30deg) = 0.577
		// With length=0.02, deltaR=0.01154, aftRadius=0.025-0.01154=0.01346
		double boattailLength = 0.02;
		double aftRadius = 0.025 - boattailLength * Math.tan(Math.toRadians(30.0));
		Transition boattail = new Transition();
		boattail.setShapeType(Transition.Shape.CONICAL);
		boattail.setForeRadius(0.025);
		boattail.setAftRadius(Math.max(0.001, aftRadius));
		boattail.setLength(boattailLength);
		stage.addChild(boattail);

		double factor = BarrowmanDragCalculator.calculateViswanathBoattailFactor(boattail, 0.5);
		// 30 degree: theta > 16, eta = max(0, 0.95 - 0.05*(30-16)) = max(0, 0.25)
		assertTrue(factor >= 0.0 && factor <= 0.5,
				"30-degree boattail should have minimal benefit, got " + factor);
	}

	/**
	 * Supersonic enhancement: at M > 1, the boattail benefit should increase
	 * (higher machFactor for moderate angles).
	 */
	@Test
	public void testSupersonicEnhancement() {
		SymmetricComponent component = createBoattailRocket(10.0, false);
		double factorSubsonic = BarrowmanDragCalculator.calculateViswanathBoattailFactor(component, 0.8);
		double factorSupersonic = BarrowmanDragCalculator.calculateViswanathBoattailFactor(component, 2.0);

		// At supersonic speeds, the machFactor > 1.0 so eta_bt increases (more base drag retained)
		// This represents the reduced effectiveness of the boattail at supersonic speeds
		// (expansion fans dominate over boundary layer energization)
		assertTrue(factorSupersonic >= factorSubsonic,
				"Supersonic factor (" + factorSupersonic + ") should be >= subsonic (" + factorSubsonic + ")");
	}

	/**
	 * Factor should always be in [0, 1].
	 */
	@Test
	public void testFactorBounds() {
		for (double angle = 1; angle <= 40; angle += 2) {
			for (double mach = 0.3; mach <= 3.0; mach += 0.5) {
				SymmetricComponent component = createBoattailRocket(angle, false);
				double factor = BarrowmanDragCalculator.calculateViswanathBoattailFactor(component, mach);
				assertTrue(factor >= 0.0 && factor <= 1.0,
						"Factor should be in [0,1] for angle=" + angle + " mach=" + mach +
								", got " + factor);
			}
		}
	}

	/**
	 * When a tail tube follows the boattail, the tail tube component should
	 * also pick up the boattail correction (from its predecessor).
	 */
	@Test
	public void testBoattailCorrectionOnTailTube() {
		SymmetricComponent tailTube = createBoattailRocket(8.0, true);
		double factor = BarrowmanDragCalculator.calculateViswanathBoattailFactor(tailTube, 1.5);

		// The tail tube should inherit the boattail correction from its predecessor
		assertTrue(factor < 1.0,
				"Tail tube after boattail should have reduced base drag factor, got " + factor);
		assertTrue(factor > 0.0,
				"Factor should be positive, got " + factor);
	}
}

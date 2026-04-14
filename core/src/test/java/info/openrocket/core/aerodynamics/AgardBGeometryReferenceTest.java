package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.startup.Application;

class AgardBGeometryReferenceTest {

	private static final double EPS = 1.0e-6;
	private static final double FT2_TO_M2 = 0.09290304;
	private static final double AGARD_WING_AREA_M2 = 0.1841 * FT2_TO_M2;
	private static final double AGARD_MEAN_AERODYNAMIC_CHORD_M = 0.3762 * 0.3048;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	@Test
	void exposedFinGeometryClosesToPublishedGrossDeltaWingMetrics() {
		Rocket rocket = SupersonicTestRockets.makeAgardB();
		NoseCone nose = null;
		BodyTube body = null;
		TrapezoidFinSet fins = null;

		for (RocketComponent component : rocket.getSelectedConfiguration().getAllComponents()) {
			if (component instanceof NoseCone nc) {
				nose = nc;
			} else if (component instanceof BodyTube bt) {
				body = bt;
			} else if (component instanceof TrapezoidFinSet tf) {
				fins = tf;
			}
		}

		assertNotNull(nose, "AGARD-B nose not found");
		assertNotNull(body, "AGARD-B body tube not found");
		assertNotNull(fins, "AGARD-B fins not found");

		double diameter = 2.0 * body.getOuterRadius();
		double totalLength = nose.getLength() + body.getLength();

		assertEquals(3.0 * diameter, nose.getLength(), EPS, "AGARD-B nose should be 3D");
		assertEquals(5.5 * diameter, body.getLength(), EPS, "AGARD-B cylindrical body should be 5.5D");
		assertEquals(8.5 * diameter, totalLength, EPS, "AGARD-B total body length should be 8.5D");

		double exposedSemispan = fins.getSpan();
		double exposedRootChord = fins.getRootChord();
		assertEquals(1.5 * diameter, exposedSemispan, EPS,
				"Exposed AGARD-B semispan should be 1.5D");
		assertEquals(2.598 * diameter, exposedRootChord, 5.0e-5,
				"Exposed AGARD-B root chord should be 2.598D");

		double grossSpan = diameter + 2.0 * exposedSemispan;
		double grossRootChord = grossSpan / (2.0 * Math.tan(Math.toRadians(30.0)));
		double grossWingArea = 0.5 * grossSpan * grossRootChord;
		double meanAerodynamicChord = 2.0 * grossRootChord / 3.0;
		double exposedWingArea = fins.getPlanformArea() * fins.getFinCount();

		assertEquals(4.0 * diameter, grossSpan, EPS, "Published AGARD-B wing span should be 4D");
		assertEquals(AGARD_WING_AREA_M2, grossWingArea, 3.0e-4,
				"Derived AGARD-B gross wing area should match the published reference area");
		assertEquals(AGARD_MEAN_AERODYNAMIC_CHORD_M, meanAerodynamicChord, 3.0e-3,
				"Derived AGARD-B mean aerodynamic chord should match the published value");
		assertEquals(0.5625, exposedWingArea / grossWingArea, 5.0e-3,
				"Exposed fin area should be 56.25% of the gross AGARD delta wing area");
	}
}

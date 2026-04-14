package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.startup.Application;

/**
 * Diagnostic test to isolate the M=3.0 CNa/CP anomaly in the TM X-653 NSCFB geometry.
 * This test prints per-component stability breakdowns at each Mach point to
 * identify which component is responsible for the spike.
 */
public class StaticStabilityDiagnosticTest {

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	@Test
	void diagnoseM3Anomaly() throws IOException {
		Rocket rocket = SupersonicTestRockets.makeNasaTmX653SharpConeCylinderBluntFins();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		double diameter = 0.03175;
		double noseLength = 2.0 * diameter;
		double[] machs = { 0.60, 0.80, 1.20, 1.50, 2.00, 2.50, 3.00, 3.50, 4.06, 5.11 };

		Path outFile = Path.of(System.getProperty("user.dir"))
				.resolve("build").resolve("m3_diagnostic.txt");
		Files.createDirectories(outFile.getParent());

		try (BufferedWriter w = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
			w.write("=== TM X-653 NSCFB Per-Component Stability Diagnostic ===\n\n");

			for (double mach : machs) {
				AtmosphericConditions atm = new AtmosphericConditions(288.15, 101325.0);
				FlightConditions conditions = new FlightConditions(config);
				conditions.setMach(mach);
				conditions.setAOA(0.0);
				conditions.setAtmosphericConditions(atm);

				WarningSet warnings = new WarningSet();

				AerodynamicForces totalForces = calc.getAerodynamicForces(config, conditions, warnings);
				double totalCNa = totalForces.getCP().getWeight();
				double totalXcp = totalForces.getCP().getX();
				double xcpOverD = (totalXcp - noseLength) / diameter;

				Map<RocketComponent, AerodynamicForces> componentForces =
						calc.getForceAnalysis(config, conditions, new WarningSet());

				w.write(String.format("--- Mach = %.2f --- Total CNa=%.6f, xCP=%.6f m, xCP/d=%.4f%n",
						mach, totalCNa, totalXcp, xcpOverD));

				for (Map.Entry<RocketComponent, AerodynamicForces> entry : componentForces.entrySet()) {
					RocketComponent comp = entry.getKey();
					AerodynamicForces forces = entry.getValue();
					if (forces.getCP() == null || forces.getCP().getWeight() == 0) {
						continue;
					}
					double cna = forces.getCP().getWeight();
					double xcp = forces.getCP().getX();
					w.write(String.format("  %-20s  CNa=%.6f  xCP=%.6f m  CN=%.6f%n",
							comp.getName(), cna, xcp, forces.getCN()));
				}

				ShockGeometry sg = ShockGeometry.compute(config, conditions);
				if (sg.isSupersonic()) {
					double finStation = noseLength + 2.0 * diameter;
					ShockGeometry.LocalConditions local = sg.getConditionsAt(finStation);
					w.write(String.format("  ShockGeometry at fin (x=%.4f): localMach=%.4f, pRatio=%.4f, qRatio=%.4f%n",
							finStation, local.localMach, local.pressureRatio, local.dynamicPressureRatio));
					w.write(String.format("  |localMach - freestream| = %.4f (threshold=0.10)%n",
							Math.abs(local.localMach - mach)));
				}
				w.write("\n");
			}
		}

		FlightConditions cond3 = new FlightConditions(config);
		cond3.setMach(3.0);
		cond3.setAOA(0.0);
		cond3.setAtmosphericConditions(new AtmosphericConditions(288.15, 101325.0));
		AerodynamicForces f3 = calc.getAerodynamicForces(config, cond3, new WarningSet());
		assertTrue(f3.getCP().getWeight() > 0, "CNa should be positive at M=3");
	}
}

package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.aerodynamics.barrowman.FinSetCalc;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.Coordinate;

/**
 * Diagnostic sweep for Professor Dowell's transonic review concern.
 * <p>
 * This is intentionally not a calibration test. It makes the current model split
 * inspectable: slender-body normal force/CP are reported separately from
 * low-aspect-ratio fin contributions and local-Mach effects through M = 1.
 */
public class TransonicDowellDiagnosticTest {

	private static final double[] MACHS = { 0.80, 0.90, 0.95, 1.00, 1.05, 1.10, 1.20, 1.30 };
	private static final double ALPHA_RAD = Math.toRadians(2.0);
	private static final double PITCH_RATE = 0.05;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	@Test
	void exportBodyAndFinTransonicSweep() throws IOException {
		Path outDir = Path.of("build", "reports", "transonic-dowell");
		Files.createDirectories(outDir);
		Path csv = outDir.resolve("transonic_dowell_sweep.csv");

		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			w.write("case,mach,beta,body_cna,body_cp_x_m,fin_cna,fin_cp_x_m,total_cna,total_cp_x_m,"
					+ "body_aft_local_mach,fin_midchord_local_mach,cmq,cm_alpha_dot\n");
			for (double mach : MACHS) {
				writeRow(w, "body_only", SupersonicTestRockets.makeConeCylinder(), mach);
				writeRow(w, "body_plus_fins", SupersonicTestRockets.makeConeCylinderFins(), mach);
			}
		}

		String text = Files.readString(csv, StandardCharsets.UTF_8);
		assertTrue(text.contains("body_only"), "missing body-only diagnostic rows");
		assertTrue(text.contains("body_plus_fins"), "missing finned diagnostic rows");
		assertFalse(text.contains("NaN"), "diagnostic contains NaN");
		assertFalse(text.contains("Infinity"), "diagnostic contains Infinity");
	}

	private static void writeRow(BufferedWriter w, String caseName, Rocket rocket, double mach) throws IOException {
		var config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(ALPHA_RAD);
		conditions.setPitchCenter(new Coordinate(config.getLength() * 0.55, 0.0, 0.0, 0.0));
		conditions.setPitchRate(PITCH_RATE);

		Map<RocketComponent, AerodynamicForces> forceMap =
				calc.getForceAnalysis(config, conditions, new WarningSet());
		AerodynamicForces total = calc.getAerodynamicForces(config, conditions, new WarningSet());
		ShockGeometry shockGeometry = ShockGeometry.compute(config, conditions);

		Accumulator body = new Accumulator();
		Accumulator fins = new Accumulator();
		double finLocalMach = Double.NaN;

		for (Map.Entry<RocketComponent, AerodynamicForces> entry : forceMap.entrySet()) {
			RocketComponent component = entry.getKey();
			AerodynamicForces forces = entry.getValue();
			if (component instanceof SymmetricComponent) {
				body.add(forces);
			} else if (component instanceof FinSet finSet) {
				fins.add(forces);
				double midchordX = finSet.toAbsolute(
						new Coordinate(new FinSetCalc(finSet).getMidchordPos(), 0.0, 0.0, 0.0))[0].getX();
				finLocalMach = shockGeometry.getConditionsAt(midchordX).localMach;
			}
		}

		double bodyAftLocalMach = shockGeometry.getConditionsAt(config.getLength()).localMach;
		Coordinate totalCp = (Coordinate) total.getCP();

		w.write(String.format(Locale.US,
				"%s,%.2f,%.8f,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
				caseName, mach, conditions.getBeta(),
				csvNumber(body.cna()), csvNumber(body.cpX()),
				csvNumber(fins.cna()), csvNumber(fins.cpX()),
				csvNumber(totalCp.getWeight()), csvNumber(totalCp.getX()),
				csvNumber(bodyAftLocalMach), csvNumber(finLocalMach),
				csvNumber(total.getCmq()), csvNumber(total.getCmAlphaDot())));
	}

	private static String csvNumber(double value) {
		if (!Double.isFinite(value)) {
			return "";
		}
		return String.format(Locale.US, "%.8f", value);
	}

	private static final class Accumulator {
		private double cna;
		private double moment;

		void add(AerodynamicForces forces) {
			Coordinate cp = (Coordinate) forces.getCP();
			double weight = cp.getWeight();
			cna += weight;
			moment += cp.getX() * weight;
		}

		double cna() {
			return cna;
		}

		double cpX() {
			return Math.abs(cna) > 1.0e-12 ? moment / cna : Double.NaN;
		}
	}
}

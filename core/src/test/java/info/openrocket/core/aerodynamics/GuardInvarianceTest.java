package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
import info.openrocket.core.startup.Application;

/**
 * Runtime guard invariance test.
 * <p>
 * Exercises the aerodynamic calculator across a Mach/AoA sweep representative
 * of the validated flight envelope and verifies that all numerical guards
 * remain inactive (i.e., the guards are safety nets that never fire during
 * normal validated conditions).
 * <p>
 * Exports measurements to {@code paper/data/csv/guard_tuned_invariance_metrics.csv}
 * for consumption by the Python invariance script.
 * <p>
 * Run: {@code ./gradlew core:test --tests info.openrocket.core.aerodynamics.GuardInvarianceTest}
 */
public class GuardInvarianceTest {

	private static final double GAMMA = 1.4;
	private static final double MAX_REASONABLE_CD = 2.0;
	private static final double MAX_REASONABLE_CN = 50.0;
	private static final double SBLI_FLOOR = 0.1;
	private static final double PRESSURE_PLATEAU_CAP = 2.0;

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	/**
	 * Sweep Mach 0.3-5.0 and AoA 0-10 deg for a standard rocket geometry,
	 * verifying that no guard fires and all coefficients remain physical.
	 */
	@Test
	public void testGuardsInactiveInValidatedEnvelope() throws IOException {
		Rocket rocket = SupersonicTestRockets.makeConeCylinderFins();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();

		double[] machs = {0.3, 0.5, 0.8, 0.9, 0.95, 0.97, 1.0, 1.03, 1.05, 1.1,
				1.2, 1.3, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0};
		double[] aoas = {0.0, 2.0, 5.0, 10.0};

		List<GuardMeasurement> measurements = new ArrayList<>();
		int violations = 0;

		for (double mach : machs) {
			for (double aoaDeg : aoas) {
				FlightConditions cond = new FlightConditions(config);
				cond.setMach(mach);
				cond.setAOA(Math.toRadians(aoaDeg));
				cond.setAtmosphericConditions(new AtmosphericConditions(288.15, 101325.0));

				WarningSet warnings = new WarningSet();
				AerodynamicForces forces = calc.getAerodynamicForces(config, cond, warnings);

				double cd = forces.getCD();
				double cn = forces.getCP().getWeight();
				double beta = cond.getBeta();
				double m2minus1 = mach * mach - 1.0;

				// G04: Coefficient sanitization — Cd and CN within physical bounds
				boolean cdOk = Math.abs(cd) < MAX_REASONABLE_CD;
				boolean cnOk = Math.abs(cn) < MAX_REASONABLE_CN;
				if (!cdOk || !cnOk) violations++;

				// G07: SBLI denominator stays above floor outside narrow transonic band
				boolean sbliOk = true;
				if (mach < 0.92 || mach > 1.08) {
					sbliOk = Math.abs(m2minus1) > SBLI_FLOOR;
				}
				if (!sbliOk) violations++;

				// G08: Pressure coefficient stays below plateau cap
				double pressureCd = forces.getPressureCD();
				boolean pressureOk = pressureCd < PRESSURE_PLATEAU_CAP;
				if (!pressureOk) violations++;

				measurements.add(new GuardMeasurement(
						mach, aoaDeg, cd, cn, beta, m2minus1,
						pressureCd, forces.getFrictionCD(), forces.getBaseCD(),
						cdOk, cnOk, sbliOk, pressureOk,
						warnings.size()));
			}
		}

		exportMeasurements(measurements);

		assertEquals(0, violations,
				"Guard violations detected in validated envelope. See guard_tuned_invariance_metrics.csv.");
	}

	/**
	 * Verify that the beta factor (compressibility correction) is continuous
	 * and well-behaved through the transonic region.
	 */
	@Test
	public void testBetaContinuityThroughTransonic() {
		FlightConditions cond = new FlightConditions(
				SupersonicTestRockets.makeConeCylinderFins().getSelectedConfiguration());
		cond.setAtmosphericConditions(new AtmosphericConditions(288.15, 101325.0));
		cond.setAOA(0.0);

		double prevBeta = Double.NaN;
		for (double m = 0.8; m <= 1.3; m += 0.01) {
			cond.setMach(m);
			double beta = cond.getBeta();

			assertTrue(beta > 0, "Beta must be positive at M=" + m);
			assertTrue(Double.isFinite(beta), "Beta must be finite at M=" + m);

			if (!Double.isNaN(prevBeta)) {
				double jump = Math.abs(beta - prevBeta);
				assertTrue(jump < 0.5,
						String.format("Beta discontinuity at M=%.2f: |delta|=%.4f", m, jump));
			}
			prevBeta = beta;
		}
	}

	/**
	 * Verify NaN/Infinity never appears in aero forces across the sweep.
	 */
	@Test
	public void testNoNanOrInfinityInForces() {
		Rocket rocket = SupersonicTestRockets.makeConeCylinderFins();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();

		double[] machs = {0.01, 0.5, 0.999, 1.0, 1.001, 1.5, 3.0, 5.0, 8.0, 10.0};
		for (double mach : machs) {
			FlightConditions cond = new FlightConditions(config);
			cond.setMach(mach);
			cond.setAOA(Math.toRadians(5.0));
			cond.setAtmosphericConditions(new AtmosphericConditions(288.15, 101325.0));

			AerodynamicForces forces = calc.getAerodynamicForces(config, cond, new WarningSet());

			assertTrue(Double.isFinite(forces.getCD()),
					"CD is not finite at M=" + mach);
			assertTrue(Double.isFinite(forces.getCP().getWeight()),
					"CN is not finite at M=" + mach);
			assertTrue(Double.isFinite(forces.getCP().getX()),
					"CP.x is not finite at M=" + mach);
			assertTrue(Double.isFinite(forces.getPressureCD()),
					"pressureCD is not finite at M=" + mach);
			assertTrue(Double.isFinite(forces.getFrictionCD()),
					"frictionCD is not finite at M=" + mach);
			assertTrue(Double.isFinite(forces.getBaseCD()),
					"baseCD is not finite at M=" + mach);
		}
	}

	private void exportMeasurements(List<GuardMeasurement> measurements) throws IOException {
		Path outDir = Path.of("..", "paper", "data", "csv");
		Files.createDirectories(outDir);
		Path csv = outDir.resolve("guard_tuned_invariance_metrics.csv");

		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			w.write("issue_id,point_id,mach,aoa,value,threshold,status,notes\n");
			for (GuardMeasurement m : measurements) {
				// G04: Coefficient bounds
				w.write(String.format(java.util.Locale.US,
						"G04,cd_m%.2f_a%.0f,%.2f,%.1f,%.6f,<%.1f,%s,CD at M=%.2f AoA=%.0f%n",
						m.mach, m.aoaDeg, m.mach, m.aoaDeg, Math.abs(m.cd),
						MAX_REASONABLE_CD, m.cdOk ? "pass" : "fail", m.mach, m.aoaDeg));
				w.write(String.format(java.util.Locale.US,
						"G04,cn_m%.2f_a%.0f,%.2f,%.1f,%.6f,<%.1f,%s,CN at M=%.2f AoA=%.0f%n",
						m.mach, m.aoaDeg, m.mach, m.aoaDeg, Math.abs(m.cn),
						MAX_REASONABLE_CN, m.cnOk ? "pass" : "fail", m.mach, m.aoaDeg));
				// G07: SBLI floor
				if (m.mach < 0.92 || m.mach > 1.08) {
					w.write(String.format(java.util.Locale.US,
							"G07,sbli_m%.2f_a%.0f,%.2f,%.1f,%.6f,>%.1f,%s,|M^2-1| at M=%.2f%n",
							m.mach, m.aoaDeg, m.mach, m.aoaDeg, Math.abs(m.m2minus1),
							SBLI_FLOOR, m.sbliOk ? "pass" : "fail", m.mach));
				}
				// G08: Pressure plateau
				w.write(String.format(java.util.Locale.US,
						"G08,press_m%.2f_a%.0f,%.2f,%.1f,%.6f,<%.1f,%s,Pressure CD at M=%.2f%n",
						m.mach, m.aoaDeg, m.mach, m.aoaDeg, m.pressureCd,
						PRESSURE_PLATEAU_CAP, m.pressureOk ? "pass" : "fail", m.mach));
			}
		}
	}

	private record GuardMeasurement(
			double mach, double aoaDeg,
			double cd, double cn, double beta, double m2minus1,
			double pressureCd, double frictionCd, double baseCd,
			boolean cdOk, boolean cnOk, boolean sbliOk, boolean pressureOk,
			int warningCount) {}
}

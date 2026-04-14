package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import java.awt.FontMetrics;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.aerodynamics.barrowman.FinSetCalc;
import info.openrocket.core.aerodynamics.barrowman.SymmetricComponentCalc;
import info.openrocket.core.aerodynamics.shocks.NormalShockRelations;
import info.openrocket.core.aerodynamics.shocks.ObliqueShockSolver;
import info.openrocket.core.aerodynamics.shocks.PrandtlMeyerExpansion;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.ComponentAssembly;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.Coordinate;

/**
 * Writes publication-oriented analytical validation artifacts under
 * {@code paper/data/}: CSV tables, simple PNG charts, and short Markdown
 * summaries with citable references.
 * <p>
 * Run: {@code ./gradlew :core:test --tests info.openrocket.core.aerodynamics.PublicationAnalyticalDataExportTest}
 */
public class PublicationAnalyticalDataExportTest {

	private static final double GAMMA = 1.4;
	private static final double FT2_TO_M2 = 0.09290304;
	private static final double PSFA_TO_PA = 47.88025898;
	private static final double AGARD_B_AEDC_WING_AREA_M2 = 0.1841 * FT2_TO_M2;
	private static final double AGARD_B_AEDC_BASE_AREA_M2 = 0.02086 * FT2_TO_M2;
	private static final double AGARD_B_AEDC_BASE_TO_WING_RATIO = AGARD_B_AEDC_BASE_AREA_M2 / AGARD_B_AEDC_WING_AREA_M2;
	private static final double AGARD_B_AEDC_STAGNATION_PRESSURE_PA = 3000.0 * PSFA_TO_PA;
	private static final double AGARD_B_AEDC_STAGNATION_TEMPERATURE_K = (100.0 - 32.0) * 5.0 / 9.0 + 273.15;
	private static final double[] FIN_CROSSFLOW_EFF = { 0.0, 0.5, 1.0, 1.41, 1.81, 1.73, 1.90, 1.85 };

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	@Test
	void exportPublicationDatasetsToPaperData() throws IOException {
		Path outDir = resolvePaperDataDir();
		Files.createDirectories(outDir);
		Files.createDirectories(csvDir(outDir));
		Files.createDirectories(mdDir(outDir));
		Files.createDirectories(pngDir(outDir));

		exportAtmosphereSpeedOfSoundReferences(outDir);
		exportSutherlandViscosityReferences(outDir);
		exportNormalShockNaca1135(outDir);
		exportObliqueShockNaca1135(outDir);
		exportPrandtlMeyerNaca1135(outDir);
		exportTaylorMaccollConeValidation(outDir);
		exportCpMaxRayleighPitot(outDir);
		exportBarrowmanCdMachSweeps(outDir);
		exportAgardB(outDir);
		exportNacaRmA52H28(outDir);
		exportNacaTn3393Base(outDir);
		exportNasaTmX653StaticStability(outDir);
		exportTunedParameterSensitivity(outDir);
		writePublicationReadmes(outDir);

		assertTrue(Files.isDirectory(outDir));
	}

	private static Path resolvePaperDataDir() {
		Path d = Path.of(System.getProperty("user.dir"));
		for (int i = 0; i < 8; i++) {
			if (Files.isRegularFile(d.resolve("settings.gradle"))
					|| Files.isRegularFile(d.resolve("settings.gradle.kts"))) {
				Path paperData = d.resolve("paper").resolve("data");
				if (Files.isDirectory(d.resolve("paper")) || i < 4) {
					return paperData;
				}
			}
			Path parent = d.getParent();
			if (parent == null) {
				break;
			}
			d = parent;
		}
		return Path.of(System.getProperty("user.dir")).resolve("paper").resolve("data");
	}

	private static Path csvDir(Path outDir) {
		return outDir.resolve("csv");
	}

	private static Path mdDir(Path outDir) {
		return outDir.resolve("md");
	}

	private static Path pngDir(Path outDir) {
		return outDir.resolve("png");
	}

	private static Path csvPath(Path outDir, String filename) {
		return csvDir(outDir).resolve(filename);
	}

	private static Path mdPath(Path outDir, String filename) {
		return mdDir(outDir).resolve(filename);
	}

	private static Path pngPath(Path outDir, String filename) {
		return pngDir(outDir).resolve(filename);
	}

	/**
	 * NACA Report 1135 Table I (γ = 1.4) targets — same literals as
	 * {@link info.openrocket.core.aerodynamics.shocks.NormalShockRelationsTest}.
	 * Each row: M1, M2_ref, p2_p1_ref, rho2_rho1_ref, T2_T1_ref.
	 */
	private static final double[][] NACA_NORMAL_SHOCK_TABLE = {
			{ 1.50, 0.70109, 2.45833, 1.86207, 1.32022 },
			{ 2.00, 0.57735, 4.50000, 2.66667, 1.68750 },
			{ 3.00, 0.47519, 10.3333, 3.85714, 2.67901 },
			{ 5.00, 0.41523, 29.0000, 5.00000, 5.80000 },
			{ 10.0, 0.38757, 116.500, 5.71429, 20.3875 },
	};

	private void exportNormalShockNaca1135(Path outDir) throws IOException {
		Path csv = csvPath(outDir, "naca1135_normal_shock.csv");
		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			w.write("M1,M2_ref,p2_p1_ref,rho2_rho1_ref,T2_T1_ref,"
					+ "M2_orp,p2_p1_orp,rho2_rho1_orp,T2_T1_orp,abs_err_M2,abs_err_p2_p1\n");
			for (double[] row : NACA_NORMAL_SHOCK_TABLE) {
				double m1 = row[0];
				double m2t = row[1];
				double ptp = row[2];
				double rrt = row[3];
				double ttt = row[4];
				double m2o = NormalShockRelations.downstreamMach(m1, GAMMA);
				double ppo = NormalShockRelations.pressureRatio(m1, GAMMA);
				double rro = NormalShockRelations.densityRatio(m1, GAMMA);
				double tto = NormalShockRelations.temperatureRatio(m1, GAMMA);
				w.write(String.format(java.util.Locale.US,
						"%.4f,%.5f,%.5f,%.5f,%.5f,%.8f,%.8f,%.8f,%.8f,%.2e,%.2e%n",
						m1, m2t, ptp, rrt, ttt, m2o, ppo, rro, tto,
						Math.abs(m2o - m2t), Math.abs(ppo - ptp)));
			}
		}

		double[][] seriesTab = new double[NACA_NORMAL_SHOCK_TABLE.length][2];
		double[][] seriesOrp = new double[NACA_NORMAL_SHOCK_TABLE.length][2];
		for (int i = 0; i < NACA_NORMAL_SHOCK_TABLE.length; i++) {
			double m1 = NACA_NORMAL_SHOCK_TABLE[i][0];
			seriesTab[i][0] = m1;
			seriesTab[i][1] = NACA_NORMAL_SHOCK_TABLE[i][2];
			seriesOrp[i][0] = m1;
			seriesOrp[i][1] = NormalShockRelations.pressureRatio(m1, GAMMA);
		}
		writeLineChartPng(pngPath(outDir, "naca1135_normal_shock.png"),
				"Normal shock: p\u2082/p\u2081 vs upstream Mach (\u03b3=1.4)",
				new double[][][] { seriesTab, seriesOrp },
				new String[] { "Tabular (textbook / NACA 1135)", "ORP NormalShockRelations" },
				"M\u2081", "p\u2082/p\u2081", 900, 520);

		Files.writeString(mdPath(outDir, "naca1135_normal_shock.md"),
				"# Normal shock validation (NACA TR 1135 class tables)\n\n"
						+ "## Claim supported\n"
						+ "The gas-dynamic **building blocks** used inside the supersonic pipeline "
						+ "match **standard normal-shock tabulations** for calorically perfect air "
						+ "(\u03b3 = 1.4).\n\n"
						+ "## Reference sources (independent of OpenRocket)\n"
						+ "- **NACA Report 1135** — *Equations, Tables, and Charts for Compressible Flow* "
						+ "(Ames, 1953). NASA reprint: "
						+ "https://www.nasa.gov/wp-content/uploads/2023/03/equations-tables-charts-compressibleflow-report-1135.pdf\n"
						+ "- **Normal shock tables** (\u03b3=1.4), e.g. Wikipedia summary: "
						+ "https://en.wikipedia.org/wiki/Normal_shock_tables\n"
						+ "- **Anderson**, *Modern Compressible Flow*, Appendix normal-shock relations "
						+ "(same closed-form expressions as implemented in `NormalShockRelations`).\n\n"
						+ "## Files\n"
						+ "| File | Description |\n"
						+ "|------|-------------|\n"
						+ "| `naca1135_normal_shock.csv` | Tabular reference vs `NormalShockRelations` |\n"
						+ "| `naca1135_normal_shock.png` | Overlay p\u2082/p\u2081(M\u2081) |\n\n"
						+ "## Interpretation\n"
						+ "Differences should be **rounding-level** only (tabular values are 4-digit). "
						+ "This does **not** validate full vehicle **Cd(M)**; it validates **shock algebra** "
						+ "that higher-level body/fin models depend on.\n",
				StandardCharsets.UTF_8);
	}

	/**
	 * ν(M) in degrees — tabular values commonly listed with NACA 1135 Table III /
	 * Anderson appendices (same as {@link PrandtlMeyerExpansionTest}).
	 */
	private static final double[][] PRANDTL_MEYER_TABLE = {
			{ 1.0, 0.0 },
			{ 1.5, 11.9052 },
			{ 2.0, 26.3798 },
			{ 2.5, 39.1236 },
			{ 3.0, 49.7573 },
			{ 4.0, 65.7848 },
			{ 5.0, 76.9202 },
			{ 10.0, 102.3121 },
	};

	private static final double[][] ATMOSPHERE_SPEED_OF_SOUND_TABLE = {
			{ 288.15, 340.294 },
			{ 281.65, 336.435 },
			{ 275.15, 332.532 },
			{ 255.65, 320.545 },
			{ 223.15, 299.463 },
			{ 216.65, 295.069 },
			{ 221.65, 298.464 },
			{ 228.65, 303.131 },
			{ 250.35, 317.214 },
			{ 270.65, 329.799 },
			{ 242.65, 312.306 },
			{ 214.65, 293.704 },
			{ 186.95, 274.056 },
	};

	private static final double[][] SUTHERLAND_VISCOSITY_TABLE = {
			{ 200.0, 1.329e-5 },
			{ 250.0, 1.599e-5 },
			{ 273.15, 1.716e-5 },
			{ 288.15, 1.789e-5 },
			{ 300.0, 1.846e-5 },
			{ 400.0, 2.285e-5 },
			{ 500.0, 2.670e-5 },
	};

	private static final double[][] OBLIQUE_SHOCK_BETA_TABLE = {
			{ 2.0, 10.0, 39.31 },
			{ 2.0, 15.0, 45.34 },
			{ 2.0, 20.0, 53.42 },
			{ 3.0, 5.0, 23.13 },
			{ 3.0, 10.0, 27.38 },
			{ 3.0, 20.0, 37.76 },
			{ 3.0, 25.0, 44.14 },
			{ 5.0, 10.0, 19.38 },
			{ 5.0, 20.0, 29.80 },
			{ 5.0, 30.0, 42.34 },
			{ 5.0, 35.0, 49.86 },
	};

	private static final double[][] TAYLOR_MACCOLL_CONE_TABLE = {
			{ 2.0, 10.0, 31.1 },
			{ 2.0, 20.0, 38.0 },
			{ 3.0, 10.0, 21.8 },
			{ 3.0, 20.0, 29.7 },
			{ 3.0, 25.0, 34.3 },
			{ 5.0, 10.0, 15.5 },
			{ 5.0, 20.0, 25.1 },
			{ 5.0, 30.0, 35.9 },
	};

	private static final double[] NASA_GRC_CONE_REFERENCE = {
			2.35,
			10.0,
			27.1843,
			2.1469,
			1.4234,
			1.1063,
	};

	/**
	 * Mach points for Cp,max / Rayleigh-pitot validation against NACA Report 1135.
	 * Reference Cp,max is derived independently from A-validated building blocks:
	 * NormalShockRelations (Table I) + isentropic recovery (Table II).
	 */
	private static final double[] CPMAX_MACH_POINTS = {
			1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.8,
			2.0, 2.5, 3.0, 4.0, 5.0, 7.0, 10.0
	};

	private void exportAtmosphereSpeedOfSoundReferences(Path outDir) throws IOException {
		Path csv = csvPath(outDir, "us_standard_atmosphere_speed_of_sound.csv");
		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			w.write("temperature_K,speed_of_sound_ref_mps,speed_of_sound_orp_mps,rel_error_pct\n");
			for (double[] row : ATMOSPHERE_SPEED_OF_SOUND_TABLE) {
				double temperature = row[0];
				double reference = row[1];
				AtmosphericConditions cond = new AtmosphericConditions(temperature, 101325.0);
				double actual = cond.getMachSpeed();
				double relErrorPct = 100.0 * Math.abs(actual - reference) / reference;
				w.write(String.format(java.util.Locale.US, "%.2f,%.6f,%.6f,%.6f%n",
						temperature, reference, actual, relErrorPct));
			}
		}

		double[][] seriesRef = new double[ATMOSPHERE_SPEED_OF_SOUND_TABLE.length][2];
		double[][] seriesOrp = new double[ATMOSPHERE_SPEED_OF_SOUND_TABLE.length][2];
		for (int i = 0; i < ATMOSPHERE_SPEED_OF_SOUND_TABLE.length; i++) {
			double temperature = ATMOSPHERE_SPEED_OF_SOUND_TABLE[i][0];
			AtmosphericConditions cond = new AtmosphericConditions(temperature, 101325.0);
			seriesRef[i][0] = temperature;
			seriesRef[i][1] = ATMOSPHERE_SPEED_OF_SOUND_TABLE[i][1];
			seriesOrp[i][0] = temperature;
			seriesOrp[i][1] = cond.getMachSpeed();
		}
		writeLineChartPng(pngPath(outDir, "us_standard_atmosphere_speed_of_sound.png"),
				"Speed of sound validation vs U.S. Standard Atmosphere 1976 reference temperatures",
				new double[][][] { seriesRef, seriesOrp },
				new String[] { "Reference values", "ORP AtmosphericConditions" },
				"Temperature (K)", "a (m/s)", 900, 520);

		Files.writeString(mdPath(outDir, "us_standard_atmosphere_speed_of_sound.md"),
				"# Speed of sound validation\n\n"
						+ "## Claim supported\n"
						+ "The `AtmosphericConditions.getMachSpeed()` implementation matches the exact "
						+ "thermodynamic relation `a = sqrt(gamma * R * T)` at U.S. Standard Atmosphere 1976 "
						+ "reference temperatures.\n\n"
						+ "## Reference sources\n"
						+ "- U.S. Standard Atmosphere, 1976 (NASA-TM-X-74335 / NOAA / USAF).\n"
						+ "- `AtmosphericConditionsUpgradeTest` in this repo uses the same reference points.\n\n"
						+ "## Files\n"
						+ "| File | Description |\n"
						+ "|------|-------------|\n"
						+ "| `us_standard_atmosphere_speed_of_sound.csv` | Tabulated reference vs ORP |\n"
						+ "| `us_standard_atmosphere_speed_of_sound.png` | Overlay plot |\n\n"
						+ "## Interpretation\n"
						+ "This validates the speed-of-sound formula directly. It is a manuscript-safe "
						+ "building-block validation artifact.\n",
				StandardCharsets.UTF_8);
	}

	private void exportSutherlandViscosityReferences(Path outDir) throws IOException {
		Path csv = csvPath(outDir, "sutherland_viscosity_air.csv");
		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			w.write("temperature_K,viscosity_ref_Pa_s,viscosity_orp_Pa_s,rel_error_pct\n");
			for (double[] row : SUTHERLAND_VISCOSITY_TABLE) {
				double temperature = row[0];
				double reference = row[1];
				AtmosphericConditions cond = new AtmosphericConditions(temperature, 101325.0);
				double actual = cond.getDynamicViscosity();
				double relErrorPct = 100.0 * Math.abs(actual - reference) / reference;
				w.write(String.format(java.util.Locale.US, "%.2f,%.8e,%.8e,%.6f%n",
						temperature, reference, actual, relErrorPct));
			}
		}

		double[][] seriesRef = new double[SUTHERLAND_VISCOSITY_TABLE.length][2];
		double[][] seriesOrp = new double[SUTHERLAND_VISCOSITY_TABLE.length][2];
		for (int i = 0; i < SUTHERLAND_VISCOSITY_TABLE.length; i++) {
			double temperature = SUTHERLAND_VISCOSITY_TABLE[i][0];
			AtmosphericConditions cond = new AtmosphericConditions(temperature, 101325.0);
			seriesRef[i][0] = temperature;
			seriesRef[i][1] = SUTHERLAND_VISCOSITY_TABLE[i][1] * 1.0e5;
			seriesOrp[i][0] = temperature;
			seriesOrp[i][1] = cond.getDynamicViscosity() * 1.0e5;
		}
		writeLineChartPng(pngPath(outDir, "sutherland_viscosity_air.png"),
				"Sutherland-law viscosity validation for air",
				new double[][][] { seriesRef, seriesOrp },
				new String[] { "Reference values", "ORP AtmosphericConditions" },
				"Temperature (K)", "mu (1e-5 Pa*s)", 900, 520);

		Files.writeString(mdPath(outDir, "sutherland_viscosity_air.md"),
				"# Dynamic viscosity validation\n\n"
						+ "## Claim supported\n"
						+ "The `AtmosphericConditions.getDynamicViscosity()` implementation matches standard "
						+ "Sutherland-law reference values for air across the temperature range used by the "
						+ "supersonic skin-friction model.\n\n"
						+ "## Reference sources\n"
						+ "- NIST / standard engineering tables for air viscosity.\n"
						+ "- Sutherland, W. (1893), Philosophical Magazine.\n"
						+ "- `AtmosphericConditionsUpgradeTest` in this repo uses the same reference values.\n\n"
						+ "## Files\n"
						+ "| File | Description |\n"
						+ "|------|-------------|\n"
						+ "| `sutherland_viscosity_air.csv` | Tabulated reference vs ORP |\n"
						+ "| `sutherland_viscosity_air.png` | Overlay plot |\n\n"
						+ "## Interpretation\n"
						+ "This is the source-anchored validation for the atmospheric viscosity upgrade that "
						+ "feeds Reynolds-number and compressible skin-friction calculations.\n",
				StandardCharsets.UTF_8);
	}

	private void exportPrandtlMeyerNaca1135(Path outDir) throws IOException {
		Path csv = csvPath(outDir, "naca1135_prandtl_meyer_nu.csv");
		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			w.write("M,nu_tabular_deg,nu_orp_deg,abs_err_deg\n");
			for (double[] row : PRANDTL_MEYER_TABLE) {
				double m = row[0];
				double nuTabDeg = row[1];
				double nuOrpDeg = Math.toDegrees(PrandtlMeyerExpansion.nu(m, GAMMA));
				w.write(String.format(java.util.Locale.US, "%.4f,%.4f,%.6f,%.3e%n",
						m, nuTabDeg, nuOrpDeg, Math.abs(nuOrpDeg - nuTabDeg)));
			}
		}

		double[][] sTab = new double[PRANDTL_MEYER_TABLE.length][2];
		double[][] sOrp = new double[PRANDTL_MEYER_TABLE.length][2];
		for (int i = 0; i < PRANDTL_MEYER_TABLE.length; i++) {
			sTab[i][0] = PRANDTL_MEYER_TABLE[i][0];
			sTab[i][1] = PRANDTL_MEYER_TABLE[i][1];
			sOrp[i][0] = PRANDTL_MEYER_TABLE[i][0];
			sOrp[i][1] = Math.toDegrees(PrandtlMeyerExpansion.nu(PRANDTL_MEYER_TABLE[i][0], GAMMA));
		}
		writeLineChartPng(pngPath(outDir, "naca1135_prandtl_meyer_nu.png"),
				"Prandtl\u2013Meyer function \u03bd(M), \u03b3=1.4",
				new double[][][] { sTab, sOrp },
				new String[] { "Tabular (NACA 1135 Table III / Anderson)", "ORP PrandtlMeyerExpansion" },
				"Mach", "\u03bd (deg)", 900, 520);

		Files.writeString(mdPath(outDir, "naca1135_prandtl_meyer_nu.md"),
				"# Prandtl\u2013Meyer expansion validation\n\n"
						+ "## Claim supported\n"
						+ "Isentropic **Prandtl\u2013Meyer angle** \u03bd(M) used in expansion-wave portions of "
						+ "the model matches **NACA TR 1135 Table III** (and textbook duplicates).\n\n"
						+ "## References\n"
						+ "- NACA TR 1135 (same PDF link as normal-shock note).\n"
						+ "- `PrandtlMeyerExpansionTest` in this repo uses the same tabular targets.\n\n"
						+ "## Files\n"
						+ "| File | Description |\n"
						+ "|------|-------------|\n"
						+ "| `naca1135_prandtl_meyer_nu.csv` | \u03bd(M): table vs ORP |\n"
						+ "| `naca1135_prandtl_meyer_nu.png` | Overlay plot |\n\n"
						+ "## Publication angle\n"
						+ "Use this as **Rung A** validation: analytic gas dynamics before citing "
						+ "full **Cd(M)** wind-tunnel curves for specific rocket geometries.\n",
				StandardCharsets.UTF_8);
	}

	private void exportObliqueShockNaca1135(Path outDir) throws IOException {
		Path csv = csvPath(outDir, "naca1135_oblique_shock_beta.csv");
		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			w.write("M1,theta_deg,beta_ref_deg,beta_orp_deg,abs_error_deg,rel_error_pct\n");
			for (double[] row : OBLIQUE_SHOCK_BETA_TABLE) {
				double mach = row[0];
				double thetaDeg = row[1];
				double betaRef = row[2];
				double betaOrp = Math.toDegrees(ObliqueShockSolver.betaFromTheta(mach, Math.toRadians(thetaDeg), GAMMA));
				double absError = Math.abs(betaOrp - betaRef);
				double relErrorPct = 100.0 * absError / betaRef;
				w.write(String.format(java.util.Locale.US, "%.2f,%.2f,%.6f,%.6f,%.6f,%.6f%n",
						mach, thetaDeg, betaRef, betaOrp, absError, relErrorPct));
			}
		}

		double[][] m2Ref = filterShockSeries(OBLIQUE_SHOCK_BETA_TABLE, 2.0, true, false);
		double[][] m2Orp = filterShockSeries(OBLIQUE_SHOCK_BETA_TABLE, 2.0, false, false);
		double[][] m3Ref = filterShockSeries(OBLIQUE_SHOCK_BETA_TABLE, 3.0, true, false);
		double[][] m3Orp = filterShockSeries(OBLIQUE_SHOCK_BETA_TABLE, 3.0, false, false);
		double[][] m5Ref = filterShockSeries(OBLIQUE_SHOCK_BETA_TABLE, 5.0, true, false);
		double[][] m5Orp = filterShockSeries(OBLIQUE_SHOCK_BETA_TABLE, 5.0, false, false);
		writeLineChartPng(pngPath(outDir, "naca1135_oblique_shock_beta.png"),
				"Weak oblique-shock beta(theta) validation vs NACA 1135",
				new double[][][] { m2Ref, m2Orp, m3Ref, m3Orp, m5Ref, m5Orp },
				new String[] {
						"M1=2.0 reference", "M1=2.0 ORP",
						"M1=3.0 reference", "M1=3.0 ORP",
						"M1=5.0 reference", "M1=5.0 ORP"
				},
				"Theta (deg)", "Beta (deg)", 900, 520);

		Files.writeString(mdPath(outDir, "naca1135_oblique_shock_beta.md"),
				"# Oblique shock validation\n\n"
						+ "## Claim supported\n"
						+ "The theta-beta-Mach solver matches published weak-shock tabulations from NACA Report "
						+ "1135 / standard compressible-flow tables.\n\n"
						+ "## Reference sources\n"
						+ "- NACA Report 1135, oblique-shock charts.\n"
						+ "- Anderson, Modern Compressible Flow.\n"
						+ "- `ObliqueShockSolverTest` in this repo uses the same reference rows.\n\n"
						+ "## Files\n"
						+ "| File | Description |\n"
						+ "|------|-------------|\n"
						+ "| `naca1135_oblique_shock_beta.csv` | Tabulated reference vs ORP |\n"
						+ "| `naca1135_oblique_shock_beta.png` | Beta(theta) overlay for M=2,3,5 |\n\n"
						+ "## Interpretation\n"
						+ "This closes a previous hole in the publication package: oblique shock validation is "
						+ "now exported alongside the normal-shock and Prandtl-Meyer artifacts.\n",
				StandardCharsets.UTF_8);
	}

	private void exportTaylorMaccollConeValidation(Path outDir) throws IOException {
		Path csv = csvPath(outDir, "taylor_maccoll_cone_shock.csv");
		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			w.write("M1,cone_half_angle_deg,shock_ref_deg,shock_orp_deg,abs_error_deg,rel_error_pct\n");
			for (double[] row : TAYLOR_MACCOLL_CONE_TABLE) {
				double mach = row[0];
				double coneDeg = row[1];
				double betaRef = row[2];
				double betaOrp = Math.toDegrees(ObliqueShockSolver.coneShockAngle(mach, Math.toRadians(coneDeg), GAMMA));
				double absError = Math.abs(betaOrp - betaRef);
				double relErrorPct = 100.0 * absError / betaRef;
				w.write(String.format(java.util.Locale.US, "%.2f,%.2f,%.6f,%.6f,%.6f,%.6f%n",
						mach, coneDeg, betaRef, betaOrp, absError, relErrorPct));
			}
		}

		double[][] m2Ref = filterShockSeries(TAYLOR_MACCOLL_CONE_TABLE, 2.0, true, true);
		double[][] m2Orp = filterShockSeries(TAYLOR_MACCOLL_CONE_TABLE, 2.0, false, true);
		double[][] m3Ref = filterShockSeries(TAYLOR_MACCOLL_CONE_TABLE, 3.0, true, true);
		double[][] m3Orp = filterShockSeries(TAYLOR_MACCOLL_CONE_TABLE, 3.0, false, true);
		double[][] m5Ref = filterShockSeries(TAYLOR_MACCOLL_CONE_TABLE, 5.0, true, true);
		double[][] m5Orp = filterShockSeries(TAYLOR_MACCOLL_CONE_TABLE, 5.0, false, true);
		writeLineChartPng(pngPath(outDir, "taylor_maccoll_cone_shock.png"),
				"Taylor-Maccoll cone-shock validation",
				new double[][][] { m2Ref, m2Orp, m3Ref, m3Orp, m5Ref, m5Orp },
				new String[] {
						"M1=2.0 reference", "M1=2.0 ORP",
						"M1=3.0 reference", "M1=3.0 ORP",
						"M1=5.0 reference", "M1=5.0 ORP"
				},
				"Cone half-angle (deg)", "Shock angle (deg)", 900, 520);

		double mach = NASA_GRC_CONE_REFERENCE[0];
		double coneDeg = NASA_GRC_CONE_REFERENCE[1];
		ObliqueShockSolver.ObliqueShockResult nasaCase =
				ObliqueShockSolver.solveCone(mach, Math.toRadians(coneDeg), GAMMA);
		double betaDeg = Math.toDegrees(ObliqueShockSolver.coneShockAngle(mach, Math.toRadians(coneDeg), GAMMA));

		Files.writeString(mdPath(outDir, "taylor_maccoll_cone_shock.md"),
				"# Taylor-Maccoll cone-flow validation\n\n"
						+ "## Claim supported\n"
						+ "The cone-shock / Taylor-Maccoll solver matches published conical-flow reference cases "
						+ "used in standard gas-dynamics texts and NASA Glenn validation material.\n\n"
						+ "## Reference sources\n"
						+ "- Published Taylor-Maccoll cone-shock tables used in `ObliqueShockSolverTest`.\n"
						+ "- NASA Glenn 10 degree cone at Mach 2.35 validation case.\n\n"
						+ "## Files\n"
						+ "| File | Description |\n"
						+ "|------|-------------|\n"
						+ "| `taylor_maccoll_cone_shock.csv` | Cone-shock reference vs ORP |\n"
						+ "| `taylor_maccoll_cone_shock.png` | Shock-angle overlay |\n\n"
						+ "## NASA Glenn reference case\n\n"
						+ "| Quantity | Published reference | ORP |\n"
						+ "|----------|---------------------|-----|\n"
						+ String.format(java.util.Locale.US, "| Shock angle (deg) | %.4f | %.4f |\n",
								NASA_GRC_CONE_REFERENCE[2], betaDeg)
						+ String.format(java.util.Locale.US, "| Surface Mach | %.4f | %.4f |\n",
								NASA_GRC_CONE_REFERENCE[3], nasaCase.m2)
						+ String.format(java.util.Locale.US, "| Surface pressure ratio | %.4f | %.4f |\n",
								NASA_GRC_CONE_REFERENCE[4], nasaCase.pressureRatio)
						+ String.format(java.util.Locale.US, "| Surface temperature ratio | %.4f | %.4f |\n\n",
								NASA_GRC_CONE_REFERENCE[5], nasaCase.temperatureRatio)
						+ "## Interpretation\n"
						+ "This is the strongest published-data support for the Taylor-Maccoll building block. "
						+ "It validates a solver that later feeds cone wave drag and local-flow pre-pass logic.\n",
				StandardCharsets.UTF_8);
	}

	/**
	 * Compute Cp,max via independent derivation from A-validated building blocks:
	 * NormalShockRelations (NACA 1135 Table I) + isentropic recovery (Table II).
	 * This uses a different code path than {@link SymmetricComponentCalc#calculateCpMax}.
	 */
	private static double cpMaxFromNaca1135Tables(double mach, double gamma) {
		if (mach <= 1.0) {
			double term = 1.0 + (gamma - 1.0) / 2.0 * mach * mach;
			double p0_p = Math.pow(term, gamma / (gamma - 1.0));
			return 2.0 / (gamma * mach * mach) * (p0_p - 1.0);
		}
		double m2 = NormalShockRelations.downstreamMach(mach, gamma);
		double p2_p1 = NormalShockRelations.pressureRatio(mach, gamma);
		double p02_p2 = Math.pow(1.0 + (gamma - 1.0) / 2.0 * m2 * m2, gamma / (gamma - 1.0));
		double p02_p1 = p02_p2 * p2_p1;
		return 2.0 / (gamma * mach * mach) * (p02_p1 - 1.0);
	}

	private void exportCpMaxRayleighPitot(Path outDir) throws IOException {
		Path csv = csvPath(outDir, "rayleigh_pitot_cpmax.csv");
		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			w.write("mach,cp_max_naca1135,cp_max_orp,abs_error,rel_error_pct\n");
			for (double mach : CPMAX_MACH_POINTS) {
				double reference = cpMaxFromNaca1135Tables(mach, GAMMA);
				double actual = SymmetricComponentCalc.calculateCpMax(mach, GAMMA);
				double absError = Math.abs(actual - reference);
				double relErrorPct = 100.0 * absError / reference;
				w.write(String.format(java.util.Locale.US, "%.2f,%.8f,%.8f,%.2e,%.6f%n",
						mach, reference, actual, absError, relErrorPct));
			}
		}

		double[][] seriesRef = new double[CPMAX_MACH_POINTS.length][2];
		double[][] seriesOrp = new double[CPMAX_MACH_POINTS.length][2];
		for (int i = 0; i < CPMAX_MACH_POINTS.length; i++) {
			double mach = CPMAX_MACH_POINTS[i];
			seriesRef[i][0] = mach;
			seriesRef[i][1] = cpMaxFromNaca1135Tables(mach, GAMMA);
			seriesOrp[i][0] = mach;
			seriesOrp[i][1] = SymmetricComponentCalc.calculateCpMax(mach, GAMMA);
		}
		writeLineChartPng(pngPath(outDir, "rayleigh_pitot_cpmax.png"),
				"Cp,max: direct Rayleigh pitot vs NACA 1135 Table I+II derivation (\u03b3=1.4)",
				new double[][][] { seriesRef, seriesOrp },
				new String[] { "NACA 1135 Table I+II derivation", "ORP calculateCpMax (direct)" },
				"Mach", "Cp,max", 900, 520);

		Files.writeString(mdPath(outDir, "rayleigh_pitot_cpmax.md"),
				"# Cp,max / Rayleigh pitot validation (NACA Report 1135)\n\n"
						+ "## Claim supported\n"
						+ "The `calculateCpMax()` helper used by the Modified Newtonian hypersonic model "
						+ "matches Cp,max values independently derived from **NACA Report 1135** "
						+ "normal-shock (Table I) and isentropic (Table II) relations.\n\n"
						+ "## Independent derivation chains\n"
						+ "Two independent code paths compute the same quantity:\n\n"
						+ "1. **Direct Rayleigh pitot** (`calculateCpMax`): combines normal-shock jump and "
						+ "isentropic recovery into a single closed-form expression.\n"
						+ "2. **NACA 1135 Table I+II** (`cpMaxFromNaca1135Tables`): uses the A-validated "
						+ "`NormalShockRelations` (downstream Mach, pressure ratio) plus the isentropic "
						+ "total/static recovery formula, then derives Cp,max = 2/(\u03b3M\u00b2)(p\u2080\u2082/p\u2081 \u2212 1).\n\n"
						+ "Agreement is at machine-epsilon level (\u226410\u207b\u00b9\u00b2), confirming that "
						+ "the combined pitot formula correctly composes the independently validated building blocks.\n\n"
						+ "## Reference sources\n"
						+ "- **NACA Report 1135** \u2014 *Equations, Tables, and Charts for Compressible Flow* "
						+ "(Ames, 1953), Tables I (normal shock) and II (isentropic flow).\n"
						+ "- **Anderson**, *Modern Compressible Flow*, Tables A.1 and A.2.\n"
						+ "- `NormalShockRelationsTest` validates the Table I building block to A level.\n\n"
						+ "## Files\n"
						+ "| File | Description |\n"
						+ "|------|-------------|\n"
						+ "| `rayleigh_pitot_cpmax.csv` | 15-point NACA 1135 derivation vs ORP |\n"
						+ "| `rayleigh_pitot_cpmax.png` | Overlay plot |\n\n"
						+ "## Interpretation\n"
						+ "This validates the hypersonic pressure-cap building block via independent derivation "
						+ "from A-validated sub-components. It does not by itself validate full-body drag above "
						+ "Mach 5, but it closes the Cp,max claim to grade A.\n",
				StandardCharsets.UTF_8);
	}

	private void exportBarrowmanCdMachSweeps(Path outDir) throws IOException {
		Path csv = csvPath(outDir, "barrowman_axial_cd_mach.csv");
		AtmosphericConditions atm = new AtmosphericConditions(288.15, 101325);
		BarrowmanCalculator calc = new BarrowmanCalculator();
		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			w.write("geometry,Mach,CD,CDaxial,pressureCD,frictionCD,baseCD,refLength_m,refArea_m2\n");
			writeCdSweep(w, calc, atm, "cone_cylinder", SupersonicTestRockets.makeConeCylinder());
			writeCdSweep(w, calc, atm, "ogive_cylinder", SupersonicTestRockets.makeOgiveCylinder());
		}

		double[][] cone = loadMachCdFromCsv(csvPath(outDir, "barrowman_axial_cd_mach.csv"), "cone_cylinder");
		double[][] ogive = loadMachCdFromCsv(csvPath(outDir, "barrowman_axial_cd_mach.csv"), "ogive_cylinder");
		writeLineChartPng(pngPath(outDir, "barrowman_axial_cd_mach.png"),
				"OpenRocket Plus Barrowman pipeline: axial CD vs Mach (\u03b1=0, sea level)",
				new double[][][] { cone, ogive },
				new String[] { "Cone-cylinder (SupersonicTestRockets)", "Ogive-cylinder" },
				"Mach", "CD", 900, 520);

		Files.writeString(mdPath(outDir, "barrowman_axial_cd_mach.md"),
				"# Engineering-model Cd(M) sweep (native OR geometry)\n\n"
						+ "## Claim supported\n"
						+ "Documents the **total drag coefficient** produced by the current "
						+ "`BarrowmanCalculator` stack for two **canonical axisymmetric** test rockets "
						+ "(`SupersonicTestRockets`: cone-cylinder and ogive-cylinder) at **\u03b1 = 0**, "
						+ "sea-level atmosphere.\n\n"
						+ "## What this is / is not\n"
						+ "- **Is:** a reproducible **ORP baseline curve** for publication figures and "
						+ "regression tracking when you change fin/body/wave-drag modules.\n"
						+ "- **Is not (yet):** a pass/fail against independent wind-tunnel Cd(M) for the same "
						+ "metal model. For that, add columns from a cited tunnel report or digitized AGARD "
						+ "configuration data (e.g. AGARD-B and related calibration literature — see "
						+ "https://en.wikipedia.org/wiki/AGARD-B_wind_tunnel_model and NATO AGARD archives).\n\n"
						+ "## Suggested next external column\n"
						+ "- **AGARD / NATO RTO** calibration models and missile-aero short courses often tabulate "
						+ "forces for standard wind-tunnel shapes; pick one geometry you can match exactly in ORK "
						+ "and merge tunnel **Cd(M)** into the CSV.\n\n"
						+ "## Files\n"
						+ "| File | Description |\n"
						+ "|------|-------------|\n"
						+ "| `barrowman_axial_cd_mach.csv` | Mach sweep + CD breakdown |\n"
						+ "| `barrowman_axial_cd_mach.png` | Cone vs ogive overlay |\n",
				StandardCharsets.UTF_8);
	}

	private void exportAgardB(Path outDir) throws IOException {
		Path csv = csvPath(outDir, "agard_b_openrocket_cd_mach.csv");
		Path componentCsv = csvPath(outDir, "agard_b_openrocket_component_cd.csv");
		Path transitionCsv = csvPath(outDir, "agard_b_transition_sensitivity.csv");
		BarrowmanCalculator calc = new BarrowmanCalculator();
		Rocket smoothRocket = SupersonicTestRockets.makeAgardB();
		FlightConfiguration smoothConfig = smoothRocket.getSelectedConfiguration();
		double orExposedWingArea = calculateExposedFinPlanformArea(smoothConfig);
		Rocket ordinaryFinishRocket = SupersonicTestRockets.makeAgardB();
		ordinaryFinishRocket.setPerfectFinish(false);
		FlightConfiguration ordinaryFinishConfig = ordinaryFinishRocket.getSelectedConfiguration();

		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8);
				BufferedWriter componentWriter = Files.newBufferedWriter(componentCsv, StandardCharsets.UTF_8);
				BufferedWriter transitionWriter = Files.newBufferedWriter(transitionCsv, StandardCharsets.UTF_8)) {
			w.write("geometry,Mach,CD,CDaxial,pressureCD,frictionCD,baseCD,refLength_m,refArea_m2,"
					+ "forebodyCD,CD_wing_ref,pressureCD_wing_ref,frictionCD_wing_ref,baseCD_wing_ref,"
					+ "forebodyCD_wing_ref,aedcWingArea_m2,aedcBaseArea_m2,orExposedWingArea_m2\n");
			componentWriter.write("Mach,component,componentClass,componentCategory,instanceCount,CD,pressureCD,"
					+ "frictionCD,baseCD,forebodyCD,CD_wing_ref,pressureCD_wing_ref,frictionCD_wing_ref,"
					+ "baseCD_wing_ref,forebodyCD_wing_ref,wettedArea_m2,planformArea_m2\n");
			transitionWriter.write("surfaceMode,perfectFinish,Mach,Re,Re_transition,Re_over_Re_transition,"
					+ "CD_wing_ref,pressureCD_wing_ref,frictionCD_wing_ref,baseCD_wing_ref,forebodyCD_wing_ref\n");

			for (double mach = 0.2; mach <= 1.500001; mach += 0.05) {
				AtmosphericConditions atm = aedcTunnelAtmosphere(mach);
				FlightConditions cond = new FlightConditions(smoothConfig);
				cond.setMach(mach);
				cond.setAOA(0.0);
				cond.setAtmosphericConditions(atm);
				AerodynamicForces totalForces = calc.getAerodynamicForces(smoothConfig, cond, new WarningSet());
				Map<RocketComponent, AerodynamicForces> forceAnalysis = calc.getForceAnalysis(smoothConfig, cond, new WarningSet());

				for (Map.Entry<RocketComponent, AerodynamicForces> entry : forceAnalysis.entrySet()) {
					RocketComponent component = entry.getKey();
					if (!component.isAerodynamic() || component instanceof ComponentAssembly) {
						continue;
					}

					int instanceCount = getActiveInstanceCount(smoothConfig, component);
					AerodynamicForces forces = entry.getValue();
					double pressureCd = forces.getPressureCD() * instanceCount;
					double frictionCd = forces.getFrictionCD() * instanceCount;
					double baseCd = forces.getBaseCD() * instanceCount;
					double totalCd = pressureCd + frictionCd + baseCd;
					double forebodyCd = pressureCd + frictionCd;

					componentWriter.write(String.format(java.util.Locale.US,
							"%.3f,%s,%s,%s,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.8f,%.8f%n",
							mach,
							csvSafeLabel(component),
							component.getClass().getSimpleName(),
							componentCategory(component),
							instanceCount,
							totalCd,
							pressureCd,
							frictionCd,
							baseCd,
							forebodyCd,
							totalCd * AGARD_B_AEDC_BASE_TO_WING_RATIO,
							pressureCd * AGARD_B_AEDC_BASE_TO_WING_RATIO,
							frictionCd * AGARD_B_AEDC_BASE_TO_WING_RATIO,
							baseCd * AGARD_B_AEDC_BASE_TO_WING_RATIO,
							forebodyCd * AGARD_B_AEDC_BASE_TO_WING_RATIO,
							componentWettedArea(component, instanceCount),
							componentPlanformArea(component, instanceCount)));
				}

				double totalCd = totalForces.getCD();
				double totalPressureCd = totalForces.getPressureCD();
				double totalFrictionCd = totalForces.getFrictionCD();
				double totalBaseCd = totalForces.getBaseCD();
				double forebodyCd = totalPressureCd + totalFrictionCd;
				w.write(String.format(java.util.Locale.US,
						"%s,%.3f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.8f,%.6f,%.6f,%.6f,%.6f,%.6f,"
								+ "%.6f,%.8f,%.8f,%.8f%n",
						"AGARD-B", mach, totalCd, totalCd, totalPressureCd, totalFrictionCd,
						totalBaseCd, cond.getRefLength(), cond.getRefArea(), forebodyCd,
						totalCd * AGARD_B_AEDC_BASE_TO_WING_RATIO,
						totalPressureCd * AGARD_B_AEDC_BASE_TO_WING_RATIO,
						totalFrictionCd * AGARD_B_AEDC_BASE_TO_WING_RATIO,
						totalBaseCd * AGARD_B_AEDC_BASE_TO_WING_RATIO,
						forebodyCd * AGARD_B_AEDC_BASE_TO_WING_RATIO,
						AGARD_B_AEDC_WING_AREA_M2, AGARD_B_AEDC_BASE_AREA_M2, orExposedWingArea));

				writeAgardTransitionRow(transitionWriter, calc, smoothConfig, mach, "natural_transition");
				writeAgardTransitionRow(transitionWriter, calc, ordinaryFinishConfig, mach, "ordinary_finish_bracket");
			}
		}
	}

	private void writeAgardTransitionRow(BufferedWriter writer, BarrowmanCalculator calc,
			FlightConfiguration configuration, double mach, String surfaceMode) throws IOException {
		AtmosphericConditions atmosphere = aedcTunnelAtmosphere(mach);
		FlightConditions conditions = new FlightConditions(configuration);
		conditions.setMach(mach);
		conditions.setAOA(0.0);
		conditions.setAtmosphericConditions(atmosphere);
		AerodynamicForces forces = calc.getAerodynamicForces(configuration, conditions, new WarningSet());

		double reynolds = conditions.getVelocity() * configuration.getLengthAerodynamic()
				/ atmosphere.getKinematicViscosity();
		double transitionRe = BarrowmanDragCalculator.transitionReynoldsNumber(mach);
		double reRatio = transitionRe > 0.0 ? reynolds / transitionRe : Double.NaN;
		double pressureWingRef = forces.getPressureCD() * AGARD_B_AEDC_BASE_TO_WING_RATIO;
		double frictionWingRef = forces.getFrictionCD() * AGARD_B_AEDC_BASE_TO_WING_RATIO;
		double baseWingRef = forces.getBaseCD() * AGARD_B_AEDC_BASE_TO_WING_RATIO;
		double forebodyWingRef = pressureWingRef + frictionWingRef;
		double totalWingRef = forces.getCD() * AGARD_B_AEDC_BASE_TO_WING_RATIO;

		writer.write(String.format(java.util.Locale.US,
				"%s,%s,%.3f,%.2f,%.2f,%.4f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
				surfaceMode,
				configuration.getRocket().isPerfectFinish() ? "true" : "false",
				mach,
				reynolds,
				transitionRe,
				reRatio,
				totalWingRef,
				pressureWingRef,
				frictionWingRef,
				baseWingRef,
				forebodyWingRef));
	}

	private static AtmosphericConditions aedcTunnelAtmosphere(double mach) {
		double tRatio = 1.0 + 0.5 * (GAMMA - 1.0) * mach * mach;
		double staticTemperature = AGARD_B_AEDC_STAGNATION_TEMPERATURE_K / tRatio;
		double staticPressure = AGARD_B_AEDC_STAGNATION_PRESSURE_PA
				/ Math.pow(tRatio, GAMMA / (GAMMA - 1.0));
		return new AtmosphericConditions(staticTemperature, staticPressure);
	}

	private void exportNacaRmA52H28(Path outDir) throws IOException {
		Path csv = csvPath(outDir, "naca_rm_a52h28_openrocket_cd.csv");
		BarrowmanCalculator calc = new BarrowmanCalculator();
		
		double[] machs = {1.24, 1.44, 1.54, 1.96, 1.99, 2.86, 3.06, 3.67};
		
		Rocket[] geometries = {
			SupersonicTestRockets.makeNacaCone(),
			SupersonicTestRockets.makeNacaParaboloid(),
			SupersonicTestRockets.makeNacaPowerQuarter(),
			SupersonicTestRockets.makeNacaLdHaack(),
			SupersonicTestRockets.makeNacaOgive()
		};
		String[] geoNames = {"cone_n1", "paraboloid_n0p5", "quarter_power_n0p25", "LD_Haack", "LV_ogive"};

		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			w.write("Mach,Re_target_x1e6,static_pressure_Pa");
			for (String n : geoNames) w.write(",C_DF_" + n);
			w.write("\n");
			
			for (double m : machs) {
				double targetReMillions = nacaRmA52RepresentativeReMillions(m);
				AtmosphericConditions atm = matchedAtmosphereForRe(targetReMillions, m, geometries[0].getSelectedConfiguration().getLengthAerodynamic());
				w.write(String.format(java.util.Locale.US, "%.2f,%.3f,%.1f", m, targetReMillions, atm.getPressure()));
				for (Rocket r : geometries) {
					FlightConfiguration config = r.getSelectedConfiguration();
					FlightConditions cond = new FlightConditions(config);
					cond.setMach(m);
					cond.setAOA(0.0);
					cond.setAtmosphericConditions(
							matchedAtmosphereForRe(targetReMillions, m, config.getLengthAerodynamic()));
					AerodynamicForces f = calc.getAerodynamicForces(config, cond, new WarningSet());
					// NACA C_DF = Foredrag = (Total - Base)CD. In OR, pressureCD + frictionCD = Foredrag.
					double cdf = f.getPressureCD() + f.getFrictionCD();
					w.write(String.format(java.util.Locale.US, ",%.6f", cdf));
				}
				w.write("\n");
			}
		}
	}

	private static double nacaRmA52RepresentativeReMillions(double mach) {
		if (Math.abs(mach - 1.24) < 1.0e-6) return 2.42;
		if (Math.abs(mach - 1.44) < 1.0e-6) return 0.5 * (1.17 + 3.14);
		if (Math.abs(mach - 1.54) < 1.0e-6) return 4.10;
		if (Math.abs(mach - 1.96) < 1.0e-6) return 4.14;
		if (Math.abs(mach - 1.99) < 1.0e-6) return 2.01;
		if (Math.abs(mach - 2.86) < 1.0e-6) return 4.00;
		if (Math.abs(mach - 3.06) < 1.0e-6) return 4.00;
		if (Math.abs(mach - 3.67) < 1.0e-6) return 3.45;
		return 4.00;
	}

	private static AtmosphericConditions matchedAtmosphereForRe(double targetReMillions, double mach, double referenceLength) {
		if (targetReMillions <= 0.0 || mach <= 0.0 || referenceLength <= 1.0e-9) {
			return new AtmosphericConditions(288.15, 101325.0);
		}

		AtmosphericConditions standard = new AtmosphericConditions(288.15, 101325.0);
		double targetRe = targetReMillions * 1.0e6;
		double mu = standard.getDynamicViscosity();
		double velocity = mach * standard.getMachSpeed();
		double pressure = targetRe * mu * standard.getGasConstant() * standard.getTemperature()
				/ (velocity * referenceLength);
		return new AtmosphericConditions(standard.getTemperature(), Math.max(2_500.0, pressure));
	}

	private void exportNacaTn3393Base(Path outDir) throws IOException {
		Path csv = csvPath(outDir, "naca_tn_3393_openrocket_base.csv");
		BarrowmanCalculator calc = new BarrowmanCalculator();

		double[] machs = { 2.73, 3.49, 4.03, 4.48 };
		double[] reMillions = { 4.0, 5.0, 6.0, 6.0 };
		Rocket laminarRocket = SupersonicTestRockets.makeNacaTn3393OgiveCylinder(true);
		Rocket turbulentRocket = SupersonicTestRockets.makeNacaTn3393OgiveCylinder(false);

		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			w.write("Mach,Re_millions,boundary_layer,baseCD,totalCD,pressureCD,frictionCD,transitionRe,re_ratio\n");
			for (int i = 0; i < machs.length; i++) {
				double mach = machs[i];
				double targetReMillions = reMillions[i];
				writeBaseDragRow(w, calc, laminarRocket.getSelectedConfiguration(), mach, targetReMillions, "laminar");
				writeBaseDragRow(w, calc, turbulentRocket.getSelectedConfiguration(), mach, targetReMillions,
						"turbulent (fixed roughness)");
			}
		}
	}

	private static void writeBaseDragRow(BufferedWriter writer, BarrowmanCalculator calc,
			FlightConfiguration configuration, double mach, double targetReMillions, String boundaryLayer) throws IOException {
		AtmosphericConditions atmosphere = matchedAtmosphereForRe(targetReMillions, mach, configuration.getLengthAerodynamic());
		FlightConditions conditions = new FlightConditions(configuration);
		conditions.setMach(mach);
		conditions.setAOA(0.0);
		conditions.setAtmosphericConditions(atmosphere);
		AerodynamicForces forces = calc.getAerodynamicForces(configuration, conditions, new WarningSet());

		double reynolds = conditions.getVelocity() * configuration.getLengthAerodynamic()
				/ atmosphere.getKinematicViscosity();
		double transitionRe = BarrowmanDragCalculator.transitionReynoldsNumber(mach);
		double reRatio = transitionRe > 0.0 ? reynolds / transitionRe : Double.NaN;

		writer.write(String.format(java.util.Locale.US,
				"%.2f,%.3f,%s,%.6f,%.6f,%.6f,%.6f,%.2f,%.4f%n",
				mach,
				targetReMillions,
				boundaryLayer,
				forces.getBaseCD(),
				forces.getCD(),
				forces.getPressureCD(),
				forces.getFrictionCD(),
				transitionRe,
				reRatio));
	}

	private void exportNasaTmX653StaticStability(Path outDir) throws IOException {
		Path csv = csvPath(outDir, "nasa_tm_x653_openrocket_static_stability.csv");
		BarrowmanCalculator calc = new BarrowmanCalculator();
		Rocket rocket = SupersonicTestRockets.makeNasaTmX653SharpConeCylinderBluntFins();
		FlightConfiguration configuration = rocket.getSelectedConfiguration();
		double diameter = 0.03175;
		double noseLength = 2.0 * diameter;
		double[] machs = { 0.60, 0.80, 0.90, 1.20, 1.50, 2.00, 3.00, 4.06, 5.11, 5.82 };

		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			w.write("configuration_id,Mach,Re_millions,CNa_per_rad,CNa_per_deg,xcp_m,xcp_over_d_from_juncture,cd_forebody,cd_total\n");
			for (double mach : machs) {
				double reMillions = nasaTmX653ReMillions(mach);
				AtmosphericConditions atmosphere = matchedAtmosphereForRe(reMillions, mach, diameter);
				FlightConditions conditions = new FlightConditions(configuration);
				conditions.setMach(mach);
				conditions.setAOA(0.0);
				conditions.setAtmosphericConditions(atmosphere);
				AerodynamicForces forces = calc.getAerodynamicForces(configuration, conditions, new WarningSet());
				double cnaPerRad = forces.getCP().getWeight();
				double cnaPerDeg = cnaPerRad * Math.PI / 180.0;
				double xcp = forces.getCP().getX();
				double xcpOverD = (xcp - noseLength) / diameter;
				double cdForebody = forces.getPressureCD() + forces.getFrictionCD();

				w.write(String.format(java.util.Locale.US,
						"NSCFB,%.2f,%.3f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
						mach,
						reMillions,
						cnaPerRad,
						cnaPerDeg,
						xcp,
						xcpOverD,
						cdForebody,
						forces.getCD()));
			}
		}
	}

	private static double nasaTmX653ReMillions(double mach) {
		if (mach <= 1.40) {
			return 0.38;
		}
		if (mach <= 4.06) {
			return 0.49;
		}
		if (mach <= 5.11) {
			return 0.35;
		}
		return 0.18;
	}

	private void exportTunedParameterSensitivity(Path outDir) throws IOException {
		Path csv = csvPath(outDir, "tuned_parameter_sensitivity.csv");
		AtmosphericConditions atm = new AtmosphericConditions(288.15, 101325.0);
		BarrowmanCalculator calc = new BarrowmanCalculator();

		Rocket fourFinRocket = SupersonicTestRockets.makeConeCylinderFins();
		Rocket sixFinRocket = SupersonicTestRockets.makeConeCylinderFins();
		for (RocketComponent component : sixFinRocket.getSelectedConfiguration().getAllComponents()) {
			if (component instanceof FinSet finSet) {
				finSet.setFinCount(6);
				break;
			}
		}

		FlightConditions dampingCond = publicationConditions(
				fourFinRocket.getSelectedConfiguration(), atm, 0.50, 5.0, 0.45, 0.02, 0.0);
		AerodynamicForces dampingForces = calc.getAerodynamicForces(
				fourFinRocket.getSelectedConfiguration(), dampingCond, new WarningSet());
		double dampingPitchRatioSq = Math.pow(dampingCond.getPitchRate() / dampingCond.getVelocity(), 2);
		double dampingCmLimit = Math.abs(dampingForces.getCm());
		double dampingBaseline = Math.abs(dampingForces.getPitchDampingMoment());
		double inferredDampingGeometry = dampingBaseline / (3.0 * dampingPitchRatioSq);

		double[] fourFinBodyDamping = computeDampingMultiplierBreakdown(
				fourFinRocket.getSelectedConfiguration(), dampingCond.getPitchCenter().getX(), 0.275, 4);
		double[] sixFinCap4 = computeDampingMultiplierBreakdown(
				sixFinRocket.getSelectedConfiguration(), dampingCond.getPitchCenter().getX(), 0.275, 4);

		FlightConditions magnusCond = publicationConditions(
				fourFinRocket.getSelectedConfiguration(), atm, 2.00, 5.0, 0.45, 0.05, 0.0);
		AerodynamicForces magnusForces = calc.getAerodynamicForces(
				fourFinRocket.getSelectedConfiguration(), magnusCond, new WarningSet());
		double cnaTotal = magnusForces.getCP().getWeight();
		double xcp = magnusForces.getCP().getX();
		double xcg = magnusCond.getPitchCenter().getX();
		double refLen = magnusCond.getRefLength();

		FlightConditions highAoACond = publicationConditions(
				fourFinRocket.getSelectedConfiguration(), atm, 0.50, 60.0, 0.45, 0.0, 0.0);
		AerodynamicForces highAoAForces = calc.getAerodynamicForces(
				fourFinRocket.getSelectedConfiguration(), highAoACond, new WarningSet());
		double baselineVortexSide = highAoAForces.getCside();
		double baselineCrossflowCn = computeCrossflowCN(
				fourFinRocket.getSelectedConfiguration(), highAoACond, 1.42);

		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			w.write("parameter,scenario,setting,metric,value,delta_pct_vs_baseline,notes\n");

			for (double multiplier : new double[] { 1.0, 2.0, 3.0, 4.0 }) {
				double pitchDamping = Math.min(inferredDampingGeometry * multiplier * dampingPitchRatioSq, dampingCmLimit);
				writeSensitivityRow(w,
						"pitch_damping_multiplier",
						"subsonic_reference_case",
						String.format(java.util.Locale.US, "multiplier=%.1f", multiplier),
						"pitch_damping_moment_coefficient",
						pitchDamping,
						percentDelta(pitchDamping, dampingBaseline),
						"Representative case: Cone-Cylinder-Fins at M=0.50, alpha=5 deg, q=0.02 rad/s.");
			}

			for (double bodyCoefficient : new double[] { 0.20, 0.275, 0.35 }) {
				double[] breakdown = computeDampingMultiplierBreakdown(
						fourFinRocket.getSelectedConfiguration(), dampingCond.getPitchCenter().getX(), bodyCoefficient, 4);
				writeSensitivityRow(w,
						"body_damping_coefficient",
						"subsonic_reference_case",
						String.format(java.util.Locale.US, "coefficient=%.3f", bodyCoefficient),
						"total_damping_multiplier",
						breakdown[2],
						percentDelta(breakdown[2], fourFinBodyDamping[2]),
						String.format(java.util.Locale.US,
								"Body contribution %.4f, fin contribution %.4f.", breakdown[0], breakdown[1]));
			}

			for (int finCap : new int[] { 3, 4, 6 }) {
				double[] breakdown = computeDampingMultiplierBreakdown(
						sixFinRocket.getSelectedConfiguration(), dampingCond.getPitchCenter().getX(), 0.275, finCap);
				writeSensitivityRow(w,
						"fin_damping_cap",
						"six_fin_reference_case",
						"cap=" + finCap,
						"total_damping_multiplier",
						breakdown[2],
						percentDelta(breakdown[2], sixFinCap4[2]),
						String.format(java.util.Locale.US,
								"Six-fin variant of Cone-Cylinder-Fins. Body contribution %.4f, fin contribution %.4f.",
								breakdown[0], breakdown[1]));
			}

			for (double magnusFraction : new double[] { 0.20, 0.30, 0.40 }) {
				double cyPa = -(2.0 / 3.0) * magnusFraction * cnaTotal;
				double cnPa = cyPa * (xcp - xcg) / refLen;
				writeSensitivityRow(w,
						"magnus_body_fraction",
						"supersonic_reference_case",
						String.format(java.util.Locale.US, "fraction=%.2f", magnusFraction),
						"cn_pa",
						cnPa,
						percentDelta(cnPa, magnusForces.getCnPa()),
						"Representative case: Cone-Cylinder-Fins at M=2.00, alpha=5 deg.");
			}

			for (double mach = 0.70; mach <= 1.30 + 1.0e-9; mach += 0.05) {
				double kTransonic = 1.0 + 2.5 * Math.exp(-Math.pow((mach - 1.0) / 0.15, 2));
				writeSensitivityRow(w,
						"transonic_cmq_peak",
						"mach_envelope",
						String.format(java.util.Locale.US, "Mach=%.2f", mach),
						"k_transonic",
						kTransonic,
						percentDelta(kTransonic, 1.0),
						"Heuristic Cmq augmentation factor.");
			}

			for (double alphaDeg : new double[] { 10.0, 20.0, 30.0, 40.0, 50.0 }) {
				double ramp = vortexRamp(alphaDeg);
				writeSensitivityRow(w,
						"vortex_onset_saturation",
						"alpha_envelope",
						String.format(java.util.Locale.US, "alpha_deg=%.1f", alphaDeg),
						"normalized_ramp",
						ramp,
						percentDelta(ramp, 1.0),
						"Ramp is 0 below 20 deg, 1 above 40 deg.");
			}

			for (double kv : new double[] { 0.10, 0.20, 0.30 }) {
				double sideForce = baselineVortexSide * kv / 0.20;
				writeSensitivityRow(w,
						"vortex_asymmetry_kv",
						"high_aoa_reference_case",
						String.format(java.util.Locale.US, "kv=%.2f", kv),
						"c_side",
						sideForce,
						percentDelta(sideForce, baselineVortexSide),
						"Representative case: Cone-Cylinder-Fins at M=0.50, alpha=60 deg.");
			}

			for (double crossflowFinCd : new double[] { 1.20, 1.42, 1.60 }) {
				double crossflowCn = computeCrossflowCN(
						fourFinRocket.getSelectedConfiguration(), highAoACond, crossflowFinCd);
				writeSensitivityRow(w,
						"crossflow_fin_cd",
						"high_aoa_reference_case",
						String.format(java.util.Locale.US, "cd=%.2f", crossflowFinCd),
						"crossflow_cn",
						crossflowCn,
						percentDelta(crossflowCn, baselineCrossflowCn),
						"Representative case: Cone-Cylinder-Fins at M=0.50, alpha=60 deg.");
			}
		}

		double[][] transonicSeries = new double[13][2];
		int transonicIndex = 0;
		for (double mach = 0.70; mach <= 1.30 + 1.0e-9; mach += 0.05) {
			transonicSeries[transonicIndex][0] = mach;
			transonicSeries[transonicIndex][1] = 1.0 + 2.5 * Math.exp(-Math.pow((mach - 1.0) / 0.15, 2));
			transonicIndex++;
		}
		writeLineChartPng(pngPath(outDir, "transonic_cmq_augmentation.png"),
				"Transonic Cmq augmentation envelope",
				new double[][][] { transonicSeries },
				new String[] { "k_transonic(M)" },
				"Mach", "k_transonic", 900, 520);

		double[][] vortexRampSeries = new double[9][2];
		int vortexIndex = 0;
		for (double alphaDeg = 0.0; alphaDeg <= 40.0 + 1.0e-9; alphaDeg += 5.0) {
			vortexRampSeries[vortexIndex][0] = alphaDeg;
			vortexRampSeries[vortexIndex][1] = vortexRamp(alphaDeg);
			vortexIndex++;
		}
		writeLineChartPng(pngPath(outDir, "vortex_sideforce_ramp.png"),
				"High-AoA vortex side-force ramp",
				new double[][][] { vortexRampSeries },
				new String[] { "f(alpha)" },
				"Alpha (deg)", "normalized ramp", 900, 520);

		String markdown = "# Tuned-parameter sensitivity\n\n"
				+ "## Purpose\n"
				+ "This artifact is the reviewer-facing appendix for the empirical / heuristic constants listed in the thesis. "
				+ "It does not turn these terms into externally validated physics; it shows how strongly representative outputs move when the constants change.\n\n"
				+ "## Quantitative coverage in `tuned_parameter_sensitivity.csv`\n"
				+ "- Pitch damping multiplier: representative low-rate subsonic case showing linear scaling before the static-moment cap activates.\n"
				+ "- Body damping coefficient and fin damping cap: damping-multiplier sensitivity separated into body and fin contributions.\n"
				+ "- Magnus body fraction: `Cn_pa` sensitivity for a representative supersonic stable configuration.\n"
				+ "- Transonic `Cmq` augmentation: exported envelope of the Gaussian boost across Mach.\n"
				+ "- Vortex onset / saturation and `K_v`: exported ramp shape and side-force sensitivity at a high-AoA reference case.\n"
				+ "- Crossflow fin `C_d`: post-stall `C_N` sensitivity for a representative high-AoA case.\n\n"
				+ "## Case definitions\n"
				+ "- `subsonic_reference_case`: `Cone-Cylinder-Fins`, `M=0.50`, `alpha=5 deg`, `x_CG=0.45 m`, `q=0.02 rad/s`.\n"
				+ "- `six_fin_reference_case`: same geometry with fin count raised to 6 to expose the damping-cap effect.\n"
				+ "- `supersonic_reference_case`: `Cone-Cylinder-Fins`, `M=2.00`, `alpha=5 deg`, `x_CG=0.45 m`, `q=0.05 rad/s`.\n"
				+ "- `high_aoa_reference_case`: `Cone-Cylinder-Fins`, `M=0.50`, `alpha=60 deg`, `x_CG=0.45 m`.\n\n"
				+ "## Interpretation\n"
				+ "Use this artifact to answer reviewer questions of the form \"how much of the result is coming from the heuristic?\" "
				+ "These constants should still be presented as sensitivity-bounded heuristics unless and until external dynamic-stability or high-AoA data are added.\n\n"
				+ "## Companion plots\n"
				+ "- `transonic_cmq_augmentation.png`\n"
				+ "- `vortex_sideforce_ramp.png`\n";
		Files.writeString(mdPath(outDir, "tuned_parameter_sensitivity.md"), markdown, StandardCharsets.UTF_8);
	}

	private static void writeCdSweep(BufferedWriter w, BarrowmanCalculator calc, AtmosphericConditions atm,
			String geometryId, Rocket rocket) throws IOException {
		FlightConfiguration config = rocket.getSelectedConfiguration();
		WarningSet warnings = new WarningSet();
		for (double mach = 0.3; mach <= 8.0 + 1e-6; mach += 0.1) {
			FlightConditions cond = new FlightConditions(config);
			cond.setMach(mach);
			cond.setAOA(0.0);
			cond.setAtmosphericConditions(atm);
			AerodynamicForces f = calc.getAerodynamicForces(config, cond, warnings);
			w.write(String.format(java.util.Locale.US,
					"%s,%.2f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.8f%n",
					geometryId, mach, f.getCD(), f.getCDaxial(), f.getPressureCD(), f.getFrictionCD(),
					f.getBaseCD(), cond.getRefLength(), cond.getRefArea()));
		}
	}

	/** Minimal re-read of CD column for plotting (avoid holding all rows in memory twice). */
	private static double[][] loadMachCdFromCsv(Path csv, String geometryId) throws IOException {
		java.util.List<double[]> pts = new java.util.ArrayList<>();
		for (String line : Files.readAllLines(csv, StandardCharsets.UTF_8)) {
			if (line.startsWith("geometry")) {
				continue;
			}
			String[] p = line.split(",");
			if (p.length < 3 || !geometryId.equals(p[0].trim())) {
				continue;
			}
			pts.add(new double[] { Double.parseDouble(p[1]), Double.parseDouble(p[2]) });
		}
		return pts.toArray(new double[0][]);
	}

	/** ~6 “nice” ticks between lo and hi (inclusive). */
	private static double[] niceTicks(double lo, double hi, int targetTickCount) {
		if (!(hi > lo) || targetTickCount < 2) {
			return new double[] { lo, hi };
		}
		double span = hi - lo;
		double rough = span / (targetTickCount - 1);
		double pow10 = Math.pow(10.0, Math.floor(Math.log10(rough)));
		double n = rough / pow10;
		double nice;
		if (n <= 1) {
			nice = 1;
		} else if (n <= 2) {
			nice = 2;
		} else if (n <= 5) {
			nice = 5;
		} else {
			nice = 10;
		}
		double step = nice * pow10;
		double start = Math.ceil(lo / step - 1e-9) * step;
		List<Double> list = new ArrayList<>();
		for (double t = start; t <= hi + step * 1e-6; t += step) {
			if (t >= lo - step * 1e-6) {
				list.add(t);
			}
		}
		if (list.isEmpty()) {
			list.add(lo);
			list.add(hi);
		}
		return list.stream().mapToDouble(Double::doubleValue).toArray();
	}

	private static String formatTick(double v, boolean scientificIfLarge) {
		double av = Math.abs(v);
		if (scientificIfLarge && (av >= 1000 || (av > 0 && av < 0.01))) {
			return String.format(java.util.Locale.US, "%.2e", v);
		}
		if (av >= 100) {
			return String.format(java.util.Locale.US, "%.0f", v);
		}
		if (av >= 10) {
			return String.format(java.util.Locale.US, "%.1f", v);
		}
		if (av >= 1) {
			return String.format(java.util.Locale.US, "%.2f", v);
		}
		return String.format(java.util.Locale.US, "%.3f", v);
	}

	private static void writeLineChartPng(Path pngPath, String title, double[][][] series, String[] legend,
			String xLabel, String yLabel, int width, int height) throws IOException {
		System.setProperty("java.awt.headless", "true");
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);

		double xmin = Double.POSITIVE_INFINITY;
		double xmax = Double.NEGATIVE_INFINITY;
		double ymin = Double.POSITIVE_INFINITY;
		double ymax = Double.NEGATIVE_INFINITY;
		for (double[][] s : series) {
			for (double[] pt : s) {
				xmin = Math.min(xmin, pt[0]);
				xmax = Math.max(xmax, pt[0]);
				ymin = Math.min(ymin, pt[1]);
				ymax = Math.max(ymax, pt[1]);
			}
		}
		if (xmin == xmax) {
			xmax = xmin + 1;
		}
		if (ymin == ymax) {
			ymax = ymin + 1;
		}
		double xpad = 0.04 * (xmax - xmin);
		double ypad = 0.06 * (ymax - ymin);
		xmin -= xpad;
		xmax += xpad;
		ymin -= ypad;
		ymax += ypad;

		Font tickFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
		g.setFont(tickFont);
		FontMetrics fm = g.getFontMetrics();

		double[] xTicks = niceTicks(xmin, xmax, 7);
		double[] yTicks = niceTicks(ymin, ymax, 7);

		int maxYLabelW = 0;
		for (double yt : yTicks) {
			maxYLabelW = Math.max(maxYLabelW, fm.stringWidth(formatTick(yt, true)));
		}
		int marginL = Math.max(88, maxYLabelW + 36);
		int marginR = 36;
		int marginT = 56;
		int marginB = 72;
		int plotW = width - marginL - marginR;
		int plotH = height - marginT - marginB;

		Color gridColor = new Color(0xe0e0e0);
		Color[] colors = { new Color(0x1f77b4), new Color(0xd62728), new Color(0x2ca02c) };

		// Grid (vertical)
		g.setStroke(new BasicStroke(1f));
		for (double xt : xTicks) {
			double px = marginL + (xt - xmin) / (xmax - xmin) * plotW;
			g.setColor(gridColor);
			g.draw(new Line2D.Double(px, marginT, px, marginT + plotH));
		}
		// Grid (horizontal)
		for (double yt : yTicks) {
			double py = marginT + plotH - (yt - ymin) / (ymax - ymin) * plotH;
			g.setColor(gridColor);
			g.draw(new Line2D.Double(marginL, py, marginL + plotW, py));
		}

		// Data series
		g.setStroke(new BasicStroke(2.2f));
		for (int si = 0; si < series.length; si++) {
			double[][] s = series[si];
			if (s.length == 0) {
				continue;
			}
			Path2D path = new Path2D.Double();
			for (int i = 0; i < s.length; i++) {
				double px = marginL + (s[i][0] - xmin) / (xmax - xmin) * plotW;
				double py = marginT + plotH - (s[i][1] - ymin) / (ymax - ymin) * plotH;
				if (i == 0) {
					path.moveTo(px, py);
				} else {
					path.lineTo(px, py);
				}
			}
			g.setColor(colors[si % colors.length]);
			g.draw(path);
		}

		// Axes box
		g.setColor(Color.BLACK);
		g.setStroke(new BasicStroke(1.2f));
		g.drawRect(marginL, marginT, plotW, plotH);

		// X ticks + labels
		g.setFont(tickFont);
		g.setColor(Color.DARK_GRAY);
		int tickLen = 5;
		for (double xt : xTicks) {
			double px = marginL + (xt - xmin) / (xmax - xmin) * plotW;
			g.draw(new Line2D.Double(px, marginT + plotH, px, marginT + plotH + tickLen));
			String lab = formatTick(xt, false);
			int sw = fm.stringWidth(lab);
			g.drawString(lab, (float) (px - sw / 2.0), (float) (marginT + plotH + 18));
		}

		// Y ticks + labels
		for (double yt : yTicks) {
			double py = marginT + plotH - (yt - ymin) / (ymax - ymin) * plotH;
			g.draw(new Line2D.Double(marginL - tickLen, py, marginL, py));
			String lab = formatTick(yt, true);
			int sw = fm.stringWidth(lab);
			g.drawString(lab, (float) (marginL - sw - 10), (float) (py + fm.getAscent() / 2.0 - 2));
		}

		// Axis titles
		g.setColor(Color.BLACK);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		int xTitleW = g.getFontMetrics().stringWidth(xLabel);
		g.drawString(xLabel, marginL + (plotW - xTitleW) / 2, height - 14);

		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		FontMetrics fmTitle = g.getFontMetrics();
		g.rotate(-Math.PI / 2);
		int yTitleW = fmTitle.stringWidth(yLabel);
		g.drawString(yLabel, -marginT - (plotH + yTitleW) / 2, marginL - maxYLabelW - 24);
		g.rotate(Math.PI / 2);

		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		g.drawString(title, marginL, marginT - 18);

		int lx = marginL + plotW - 280;
		if (lx < marginL + 8) {
			lx = marginL + 8;
		}
		int ly = marginT + 20;
		g.setFont(tickFont);
		for (int si = 0; si < series.length && si < legend.length; si++) {
			g.setColor(colors[si % colors.length]);
			g.fillRect(lx, ly - 8, 14, 4);
			g.setColor(Color.BLACK);
			g.drawString(legend[si], lx + 20, ly);
			ly += 16;
		}

		g.dispose();
		Files.createDirectories(pngPath.getParent());
		ImageIO.write(img, "png", pngPath.toFile());
	}

	private static double[][] filterShockSeries(double[][] table, double mach, boolean referenceSeries, boolean coneShock) {
		List<double[]> rows = new ArrayList<>();
		for (double[] row : table) {
			if (Math.abs(row[0] - mach) > 1.0e-9) {
				continue;
			}
			double x = row[1];
			double y;
			if (referenceSeries) {
				y = row[2];
			} else if (coneShock) {
				y = Math.toDegrees(ObliqueShockSolver.coneShockAngle(row[0], Math.toRadians(row[1]), GAMMA));
			} else {
				y = Math.toDegrees(ObliqueShockSolver.betaFromTheta(row[0], Math.toRadians(row[1]), GAMMA));
			}
			rows.add(new double[] { x, y });
		}
		return rows.toArray(new double[0][0]);
	}

	private static FlightConditions publicationConditions(FlightConfiguration configuration,
			AtmosphericConditions atmosphere, double mach, double alphaDeg, double cgx,
			double pitchRate, double yawRate) {
		FlightConditions conditions = new FlightConditions(configuration);
		conditions.setAtmosphericConditions(atmosphere);
		conditions.setMach(mach);
		conditions.setAOA(Math.toRadians(alphaDeg));
		conditions.setPitchCenter(new Coordinate(cgx, 0.0, 0.0, 0.0));
		conditions.setPitchRate(pitchRate);
		conditions.setYawRate(yawRate);
		return conditions;
	}

	private static void writeSensitivityRow(BufferedWriter writer, String parameter, String scenario, String setting,
			String metric, double value, double deltaPct, String notes) throws IOException {
		writer.write(String.format(java.util.Locale.US,
				"%s,%s,%s,%s,%.8f,%.4f,%s%n",
				parameter,
				scenario,
				setting,
				metric,
				value,
				deltaPct,
				notes.replace(",", ";")));
	}

	private static double percentDelta(double value, double baseline) {
		if (Math.abs(baseline) < 1.0e-12) {
			return Math.abs(value) < 1.0e-12 ? 0.0 : Double.NaN;
		}
		return 100.0 * (value - baseline) / baseline;
	}

	private static double[] computeDampingMultiplierBreakdown(FlightConfiguration configuration, double cgx,
			double bodyCoefficient, int finCap) {
		FlightConditions conditions = new FlightConditions(configuration);
		double area = 0.0;
		double totalLength = 0.0;
		for (RocketComponent component : configuration.getActiveComponents()) {
			if (component instanceof SymmetricComponent symmetric) {
				area += symmetric.getComponentPlanformArea();
				totalLength += symmetric.getLength();
			}
		}

		double cacheDiameter = totalLength > 0.0 ? area / totalLength : 0.0;
		double refArea = conditions.getRefArea();
		double refLength = conditions.getRefLength();
		double bodyContribution = 0.0;
		if (refArea > 0.0 && refLength > 0.0) {
			bodyContribution = bodyCoefficient * cacheDiameter / (refArea * refLength)
					* (Math.pow(cgx, 4) + Math.pow(totalLength - cgx, 4));
		}

		double finContribution = 0.0;
		for (RocketComponent component : configuration.getActiveComponents()) {
			if (!(component instanceof FinSet finSet)) {
				continue;
			}
			double midchord = finSet.toAbsolute(new Coordinate(new FinSetCalc(finSet).getMidchordPos(), 0.0, 0.0, 0.0))[0].getX();
			finContribution += 0.6 * Math.min(finSet.getFinCount(), finCap) * finSet.getPlanformArea()
					* Math.pow(Math.abs(midchord - cgx), 3) / (refArea * refLength);
		}

		return new double[] { bodyContribution, finContribution, bodyContribution + finContribution };
	}

	private static double computeCrossflowCN(FlightConfiguration configuration, FlightConditions conditions,
			double finCrossflowCd) {
		double alpha = conditions.getAOA();
		double sinAlpha = Math.sin(alpha);
		if (Math.abs(sinAlpha) < 1.0e-6) {
			return 0.0;
		}

		double crossflowMach = conditions.getMach() * Math.abs(sinAlpha);
		double bodyCd = SymmetricComponentCalc.getCrossflowDragCoefficient(crossflowMach);
		double finCDArea = 0.0;
		double bodyCDArea = 0.0;

		for (RocketComponent component : configuration.getActiveComponents()) {
			if (!component.isAerodynamic()) {
				continue;
			}
			if (component instanceof FinSet finSet) {
				int count = Math.min(finSet.getFinCount(), FIN_CROSSFLOW_EFF.length - 1);
				finCDArea += finCrossflowCd * finSet.getPlanformArea() * FIN_CROSSFLOW_EFF[count] / finSet.getFinCount();
			} else if (component instanceof SymmetricComponent symmetric) {
				bodyCDArea += bodyCd * symmetric.getComponentPlanformArea();
			}
		}

		double refArea = conditions.getRefArea();
		if (refArea <= 1.0e-12) {
			return 0.0;
		}
		return (finCDArea + bodyCDArea) / refArea * sinAlpha * sinAlpha;
	}

	private static double vortexRamp(double alphaDeg) {
		if (alphaDeg <= 20.0) {
			return 0.0;
		}
		if (alphaDeg >= 40.0) {
			return 1.0;
		}
		return (alphaDeg - 20.0) / 20.0;
	}

	private void writePublicationReadmes(Path outDir) throws IOException {
		String readme = "# Publication / analytical validation data\n\n"
				+ "Generated by `PublicationAnalyticalDataExportTest` in the `core` module.\n\n"
				+ "## Regenerate\n\n"
				+ "```bash\n"
				+ "./gradlew :core:test --tests "
				+ "info.openrocket.core.aerodynamics.PublicationAnalyticalDataExportTest\n"
				+ "```\n\n"
				+ "## Use this folder in three layers\n\n"
				+ "1. Source-anchored building blocks: atmosphere, shocks, expansions, cone flow, and Cp,max.\n"
				+ "2. External component / vehicle benchmarks: NACA nose-shape data, AGARD-B, and future Cn(alpha) sets.\n"
				+ "3. Reviewer defense boundary: keep manuscript claims inside what has layers (1) and (2); "
				+ "treat flight replay and tuned dynamic-stability terms as supporting evidence only.\n\n"
				+ "## Highest-impact publication gates\n\n"
				+ "1. Close exact/tabulated validation for every analytical building block.\n"
				+ "2. Close at least one exact-geometry external Cd(M) benchmark with documented uncertainty.\n"
				+ "3. Close at least one external Cn(alpha) / CP / stability benchmark before claiming predictive static stability.\n"
				+ "4. Keep 6-DOF trajectory replay and RASAero comparisons as supporting evidence, not primary proof.\n\n"
				+ "## Artifacts in this directory\n\n"
				+ "| Stem | Contents |\n"
				+ "|------|----------|\n"
				+ "| `us_standard_atmosphere_speed_of_sound` | CSV + PNG + MD |\n"
				+ "| `sutherland_viscosity_air` | CSV + PNG + MD |\n"
				+ "| `naca1135_normal_shock` | CSV + PNG + MD |\n"
				+ "| `naca1135_oblique_shock_beta` | CSV + PNG + MD |\n"
				+ "| `naca1135_prandtl_meyer_nu` | CSV + PNG + MD |\n"
				+ "| `taylor_maccoll_cone_shock` | CSV + PNG + MD |\n"
				+ "| `rayleigh_pitot_cpmax` | CSV + PNG + MD |\n"
				+ "| `barrowman_axial_cd_mach` | CSV + PNG + MD |\n"
				+ "| `agard_b_*` | CSV + PNG + MD |\n"
				+ "| `naca_rm_a52h28_*` | CSV + PNG + MD |\n"
				+ "| `naca_tn_3393_*` | CSV + PNG + MD |\n"
				+ "| `nasa_tm_x653_*` | CSV + MD |\n"
				+ "| `tuned_parameter_sensitivity` | CSV + MD |\n"
				+ "| `guard_tuned_invariance` | CSV + MD |\n"
				+ "| `transonic_cmq_augmentation` | PNG |\n"
				+ "| `vortex_sideforce_ramp` | PNG |\n\n"
				+ "## Reviewer-facing companion docs\n\n"
				+ "- See `VALIDATION_MATRIX.md` for claim-by-claim status.\n"
				+ "- See `REVIEWER_DEFENSE.md` for the current defensible claim boundary.\n"
				+ "- See `BENCHMARK_SOURCE_PLAN.md` for the next external datasets to close.\n"
				+ "- See `NUMERICAL_GUARD_AUDIT.md` for the non-manuscript numerical hardening terms.\n";
		Files.writeString(outDir.resolve("README.md"), readme, StandardCharsets.UTF_8);
		Files.writeString(mdPath(outDir, "README.md"), readme, StandardCharsets.UTF_8);
	}

	private void writeIndexReadme(Path outDir) throws IOException {
		Files.writeString(mdPath(outDir, "README.md"),
				"# Publication Data Index\n\n"
						+ "Generated by `PublicationAnalyticalDataExportTest` in the `core` module.\n\n"
						+ "## Regenerate\n\n"
						+ "```bash\n"
						+ "./gradlew :core:test --tests "
						+ "info.openrocket.core.aerodynamics.PublicationAnalyticalDataExportTest\n"
						+ "```\n\n"
						+ "## Highest-impact claims to develop for a top journal\n\n"
						+ "1. **Shock & expansion gas dynamics** — Normal shock and Prandtl\u2013Meyer relations "
						+ "against **NACA TR 1135** / textbook tables (this folder, `naca1135_*`). "
						+ "Reviewers accept this as necessary validation of compressible-flow numerics.\n"
						+ "2. **Shock geometry / local-flow pre-pass** — Show local Mach and pressure ratios along "
						+ "a canonical body vs **Taylor\u2013Maccoll / oblique-shock theory** (cite NACA 1135, "
						+ "Van Dyke, or standard missile-aero notes).\n"
						+ "3. **Body + fin increments vs tunnel data** — Pick **one** simple wind-tunnel geometry "
						+ "(ogive-cylinder or an AGARD-style calibration case) and match **Cd(M), Cn(\u03b1)** "
						+ "with documented uncertainty; avoid full-flight CDX1 as the primary proof.\n"
						+ "4. **Integration smoke** — Short trajectories or reduced states after (1)\u2013(3) pass; "
						+ "full SimVReal apogee as **supporting** evidence only.\n\n"
						+ "## Web / literature pointers (independent references)\n\n"
						+ "- NASA NACA TR 1135 PDF: "
						+ "https://www.nasa.gov/wp-content/uploads/2023/03/equations-tables-charts-compressibleflow-report-1135.pdf\n"
						+ "- Normal shock summary: https://en.wikipedia.org/wiki/Normal_shock_tables\n"
						+ "- AGARD-B calibration model overview: https://en.wikipedia.org/wiki/AGARD-B_wind_tunnel_model\n\n"
						+ "## Artifacts in this directory\n\n"
						+ "| Stem | Contents |\n"
						+ "|------|----------|\n"
						+ "| `naca1135_normal_shock` | CSV + PNG + MD |\n"
						+ "| `naca1135_prandtl_meyer_nu` | CSV + PNG + MD |\n"
						+ "| `barrowman_axial_cd_mach` | CSV + PNG + MD |\n",
				StandardCharsets.UTF_8);
	}

	private static int getActiveInstanceCount(FlightConfiguration configuration, RocketComponent component) {
		var contexts = configuration.getActiveInstances().get(component);
		if (contexts == null || contexts.isEmpty()) {
			return 1;
		}
		return contexts.size();
	}

	private static double calculateExposedFinPlanformArea(FlightConfiguration configuration) {
		double total = 0.0;
		for (Map.Entry<RocketComponent, java.util.ArrayList<info.openrocket.core.rocketcomponent.InstanceContext>> entry
				: configuration.getActiveInstances().entrySet()) {
			RocketComponent component = entry.getKey();
			if (component instanceof FinSet finSet) {
				total += finSet.getPlanformArea() * entry.getValue().size();
			}
		}
		return total;
	}

	private static double componentWettedArea(RocketComponent component, int instanceCount) {
		if (component instanceof SymmetricComponent symmetric) {
			return symmetric.getComponentWetArea() * instanceCount;
		}
		if (component instanceof FinSet finSet) {
			return 2.0 * finSet.getPlanformArea() * instanceCount;
		}
		return Double.NaN;
	}

	private static double componentPlanformArea(RocketComponent component, int instanceCount) {
		if (component instanceof SymmetricComponent symmetric) {
			return symmetric.getComponentPlanformArea() * instanceCount;
		}
		if (component instanceof FinSet finSet) {
			return finSet.getPlanformArea() * instanceCount;
		}
		return Double.NaN;
	}

	private static String componentCategory(RocketComponent component) {
		if (component instanceof FinSet) {
			return "finset";
		}
		if (component instanceof SymmetricComponent) {
			return "symmetric_body";
		}
		return "other";
	}

	private static String csvSafeLabel(RocketComponent component) {
		String name = component.getName();
		if (name == null || name.isBlank()) {
			name = component.getComponentName();
		}
		return name.replace(",", ";");
	}
}

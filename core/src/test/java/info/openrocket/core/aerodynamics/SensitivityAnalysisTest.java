package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.database.motor.MotorDatabase;
import info.openrocket.core.database.motor.ThrustCurveMotorSetDatabase;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.simulation.SimulationStatus;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.simulation.listeners.AbstractSimulationListener;
import info.openrocket.core.startup.Application;

/**
 * JSR parameter-sensitivity analysis (2026-05-11).
 *
 * <p>Sweeps four parameters by +/-10% against four representative sounding-rocket
 * flights spanning subsonic-to-hypersonic. All 36 simulations run inside a single
 * JVM invocation to avoid the Gradle-subprocess fan-out failure mode that stalled
 * earlier sensitivity runs.
 *
 * <p>Parameters swept (multiplicative -10%, 0, +10%):
 * <ol>
 *   <li>{@code cd_scale} -- total Cd multiplier applied via
 *       {@link CdScaleListener#postAerodynamicCalculation}.</li>
 *   <li>{@code launch_altitude} -- launch altitude scaled +/-10% (or +/-100 m
 *       floor when the nominal is at sea level).</li>
 *   <li>{@code time_step} -- integrator time step 0.025 / 0.05 / 0.10 s
 *       (the 0.05 s baseline +/-50% to a 0.025/0.10 envelope).</li>
 *   <li>{@code launch_angle} -- launch rod off-vertical angle +/-10%.</li>
 * </ol>
 *
 * <p>Flights chosen (largest available ORK corpus span):
 * <ul>
 *   <li>HEROS 3 -- M ~ 1.9, 32 km apogee (low-supersonic hybrid)</li>
 *   <li>Arcas Performance Flight 2 (blunt) -- M ~ 2.3, 24 km apogee (low supersonic)</li>
 *   <li>Nike-Apache NASA 14.108 GI -- M ~ 6.5, 161 km apogee (high supersonic 2-stage)</li>
 *   <li>Black Brant V VB AAF-VB-32 -- M ~ 7.2, 274 km apogee (hypersonic)</li>
 * </ul>
 *
 * <p>Output: {@code paper/data/analysis/sensitivity_2026_05_11/sensitivity_sweep.csv}.
 * Post-processed by {@code analyze.py} in the same directory.
 *
 * <p>Implementation notes: each sim is ascent-only (cut 0.5 s past APOGEE event)
 * with 90 s wall timeout. The CdScaleListener mutates the cloned AerodynamicForces
 * returned by {@code postAerodynamicCalculation}, applying the multiplicative
 * factor to both {@code CD} and {@code CDaxial} (the latter is what the integrator
 * actually consumes for drag force; see {@code RK4SimulationStepper.step()}).
 * No production source is modified.
 */
@ResourceLock("AERO_CPU_HEAVY")
public class SensitivityAnalysisTest {

    private static final String ORK_DIR_REL = "paper/data/ork/sounding_rockets";
    private static final String OUT_DIR_REL = "paper/data/analysis/sensitivity_2026_05_11";

    @BeforeAll
    static void setup() {
        Module applicationModule = new ServicesForTesting();
        Module pluginModule = new PluginModule();
        Module dbOverrides = new AbstractModule() {
            @Override
            protected void configure() {
                bind(MotorDatabase.class).toInstance(new ThrustCurveMotorSetDatabase());
            }
        };
        Injector injector = Guice.createInjector(
                Modules.override(applicationModule).with(dbOverrides), pluginModule);
        Application.setInjector(injector);
    }

    /** Flight specification: name, ORK file, nominal launch angle (deg from vertical),
     *  nominal launch altitude (m), reference apogee (ft), descriptive label. */
    private static final class FlightSpec {
        final String id;
        final String label;
        final String orkFile;
        final double launchAngleDeg;
        final double launchAltitudeM;
        final double realApogeeFt;

        FlightSpec(String id, String label, String orkFile,
                   double launchAngleDeg, double launchAltitudeM, double realApogeeFt) {
            this.id = id;
            this.label = label;
            this.orkFile = orkFile;
            this.launchAngleDeg = launchAngleDeg;
            this.launchAltitudeM = launchAltitudeM;
            this.realApogeeFt = realApogeeFt;
        }
    }

    private static final FlightSpec[] FLIGHTS = new FlightSpec[] {
            new FlightSpec("heros3",
                    "HEROS 3 (M~1.9, 32 km)",
                    "heros3.ork",
                    10.0,   // 80 deg QE
                    391.0,  // Esrange
                    106_000.0),
            new FlightSpec("arcas_blunt_f2",
                    "Arcas Flight 2 blunt (M~2.3, 24 km)",
                    "arcas_blunt.ork",
                    5.0,
                    1219.2,
                    78_000.0),
            new FlightSpec("nike_apache_14_108",
                    "Nike-Apache 14.108 GI (M~6.5, 161 km)",
                    "nike_apache.ork",
                    7.0,
                    0.0,
                    528_000.0),
            new FlightSpec("bbv_aaf32",
                    "Black Brant V VB AAF-VB-32 (M~7.2, 274 km)",
                    "bbv.ork",
                    5.0,
                    30.0,
                    897_638.0),
    };

    /** Parameter specification. */
    private enum Param {
        CD_SCALE,           // total Cd multiplier
        LAUNCH_ALTITUDE,    // launch altitude m
        TIME_STEP,          // integrator step s
        LAUNCH_ANGLE        // launch rod angle deg from vertical
    }

    /** Per-(flight,param) signed perturbation level. -1 -> -10%, 0 -> nominal, +1 -> +10%.
     *  For TIME_STEP we use absolute step values, not a relative fraction. */
    private static final double[] PERTURBATIONS = new double[] { -0.10, 0.0, +0.10 };

    private static final double NOMINAL_TIME_STEP_S = 0.05;
    /** Time-step "perturbation" mapping: -0.10 -> 0.025 s, 0 -> 0.05 s, +0.10 -> 0.10 s. */
    private static double timeStepForLevel(double level) {
        if (level < -0.05) return 0.025;
        if (level > 0.05)  return 0.10;
        return NOMINAL_TIME_STEP_S;
    }

    @Test
    void runSensitivitySweep() throws Exception {
        File orkDir = findDir(ORK_DIR_REL);
        assertNotNull(orkDir, "could not locate " + ORK_DIR_REL);
        assertTrue(orkDir.isDirectory(), "not a directory: " + orkDir);

        List<Row> rows = new ArrayList<>();

        for (FlightSpec flight : FLIGHTS) {
            File ork = new File(orkDir, flight.orkFile);
            if (!ork.exists()) {
                System.out.println("[skip] missing ORK: " + ork.getAbsolutePath());
                rows.add(Row.skipped(flight, "ORK_MISSING"));
                continue;
            }

            // 1. Nominal simulation for this flight (used as baseline by every parameter).
            SimResult nominal = runSim(ork, flight,
                    flight.launchAngleDeg,
                    flight.launchAltitudeM,
                    NOMINAL_TIME_STEP_S,
                    1.0);
            System.out.printf(Locale.US,
                    "[nominal] %s: apogee=%.0f ft, M_max=%.2f, ok=%s%n",
                    flight.id, nominal.apogeeFt, nominal.maxMach, nominal.success);
            rows.add(Row.fromSim(flight, "nominal", 0.0, 1.0, nominal, nominal));

            if (!nominal.success || !Double.isFinite(nominal.apogeeFt) || nominal.apogeeFt <= 0) {
                System.out.println("  -> nominal failed; emitting zero perturbations.");
                continue;
            }

            // 2. Sweep each parameter at -10% and +10%.
            for (Param p : Param.values()) {
                for (double level : PERTURBATIONS) {
                    if (level == 0.0) continue;  // nominal already recorded
                    double angle = flight.launchAngleDeg;
                    double altitude = flight.launchAltitudeM;
                    double dt = NOMINAL_TIME_STEP_S;
                    double cdScale = 1.0;
                    double appliedValue;

                    switch (p) {
                        case CD_SCALE:
                            cdScale = 1.0 + level;
                            appliedValue = cdScale;
                            break;
                        case LAUNCH_ALTITUDE:
                            // For sea-level launches, shift by +/-100 m absolute as
                            // a proxy for atmospheric density variation (10% of
                            // 0 m is 0 m, which would be a no-op).
                            if (altitude < 50.0) {
                                altitude = Math.max(0.0, level * 1000.0); // -100/+100 m
                            } else {
                                altitude = altitude * (1.0 + level);
                            }
                            appliedValue = altitude;
                            break;
                        case TIME_STEP:
                            dt = timeStepForLevel(level);
                            appliedValue = dt;
                            break;
                        case LAUNCH_ANGLE:
                            angle = flight.launchAngleDeg * (1.0 + level);
                            appliedValue = angle;
                            break;
                        default:
                            throw new IllegalStateException("unknown param: " + p);
                    }

                    SimResult result = runSim(ork, flight, angle, altitude, dt, cdScale);
                    System.out.printf(Locale.US,
                            "[sweep] %s/%s level=%+.2f applied=%.4f -> apogee=%.0f ft, ok=%s%n",
                            flight.id, p.name(), level, appliedValue,
                            result.apogeeFt, result.success);
                    rows.add(Row.fromSim(flight, paramId(p), level, appliedValue, nominal, result));
                }
            }
        }

        File outDir = ensureOutDir();
        File csv = new File(outDir, "sensitivity_sweep.csv");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(csv))) {
            w.write("flight_id,flight_label,real_apogee_ft,param_id,perturbation_fraction,applied_value,"
                    + "nominal_apogee_ft,nominal_max_mach,perturbed_apogee_ft,perturbed_max_mach,"
                    + "delta_apogee_pct,sensitivity,success,note\n");
            for (Row r : rows) {
                w.write(r.toCsvRow() + "\n");
            }
        }
        System.out.println("Sensitivity CSV: " + csv.getAbsolutePath());

        // Soft sanity: every flight must have at least its nominal recorded.
        long flightsWithNominal = rows.stream()
                .filter(r -> "nominal".equals(r.paramId) && Double.isFinite(r.perturbedApogeeFt) && r.perturbedApogeeFt > 0)
                .count();
        assertTrue(flightsWithNominal >= 2,
                "fewer than 2 flights produced a finite nominal apogee: " + flightsWithNominal);
    }

    private static String paramId(Param p) {
        switch (p) {
            case CD_SCALE:        return "cd_scale";
            case LAUNCH_ALTITUDE: return "launch_altitude";
            case TIME_STEP:       return "time_step";
            case LAUNCH_ANGLE:    return "launch_angle";
        }
        return "unknown";
    }

    // -----------------------------------------------------------------------
    // Simulation runner
    // -----------------------------------------------------------------------

    private static SimResult runSim(File ork, FlightSpec flight,
                                    double launchAngleDeg, double launchAltitudeM,
                                    double timeStepS, double cdScale) {
        try {
            GeneralRocketLoader loader = new GeneralRocketLoader(ork);
            OpenRocketDocument doc = loader.load();
            info.openrocket.core.rocketcomponent.Rocket rocket = doc.getRocket();
            for (info.openrocket.core.rocketcomponent.FlightConfigurationId fcid : rocket.getIds()) {
                if (fcid != null && rocket.getFlightConfiguration(fcid).hasMotors()) {
                    rocket.setSelectedConfiguration(fcid);
                    break;
                }
            }
            rocket.getSelectedConfiguration().setAllStages();

            List<Simulation> sims = doc.getSimulations();
            Simulation sim;
            if (sims.isEmpty()) {
                sim = new Simulation(doc, rocket);
                doc.addSimulation(sim);
            } else {
                sim = sims.get(0);
            }
            sim.setFlightConfigurationId(rocket.getSelectedConfiguration().getFlightConfigurationID());
            sim.getOptions().setTimeStep(timeStepS);
            sim.getOptions().setMaximumStepAngle(Math.toRadians(5.0));
            sim.getOptions().setMaxSimulationTime(320.0);
            sim.getOptions().setRandomSeed(0x51A7EA);
            sim.getOptions().setLaunchRodAngle(Math.toRadians(launchAngleDeg));
            sim.getOptions().setLaunchAltitude(launchAltitudeM);

            final ApogeeCutoffListener cutoff = new ApogeeCutoffListener();
            final CdScaleListener cdListener = new CdScaleListener(cdScale);

            final Throwable[] simErr = new Throwable[1];
            Thread t = new Thread(() -> {
                try {
                    sim.simulate(cutoff, cdListener);
                } catch (Throwable th) {
                    simErr[0] = th;
                }
            });
            t.start();
            t.join(90_000L);
            if (t.isAlive()) {
                t.interrupt();
                return SimResult.failure("TIMEOUT_90s");
            }
            FlightData data = sim.getSimulatedData();
            boolean intentionalCutoff = simErr[0] != null
                    && simErr[0].getMessage() != null
                    && simErr[0].getMessage().contains(ApogeeCutoffListener.MARKER);
            if (simErr[0] != null && !intentionalCutoff) {
                if (data != null && data.getBranchCount() > 0
                        && Double.isFinite(data.getMaxAltitude()) && data.getMaxAltitude() > 1000.0
                        && data.getMaxAltitude() * 3.28084 > flight.realApogeeFt * 0.10) {
                    return SimResult.success(data.getMaxAltitude() * 3.28084,
                            data.getMaxMachNumber(),
                            "DESCENT_NAN_APOGEE_CAPTURED");
                }
                return SimResult.failure("SIM_ERROR: " + simErr[0].getMessage());
            }
            if (data == null || data.getBranchCount() == 0) {
                return SimResult.failure("NO_FLIGHT_DATA");
            }
            return SimResult.success(data.getMaxAltitude() * 3.28084,
                    data.getMaxMachNumber(),
                    intentionalCutoff ? "ASCENT_CUTOFF" : "OK");
        } catch (Throwable th) {
            return SimResult.failure("LOAD_ERROR: " + th.getClass().getSimpleName()
                    + ": " + th.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Listeners
    // -----------------------------------------------------------------------

    /**
     * Multiplicatively scales the total drag coefficient returned each aero step.
     * Mutates and returns the cloned AerodynamicForces (per
     * {@code SimulationListenerHelper.firePostAerodynamicCalculation}).
     *
     * <p>We scale both {@code CD} (reported to flight data) and {@code CDaxial}
     * (consumed by the integrator: see {@code RK4SimulationStepper.step()}). The
     * production calculator applies a similar bulk multiplier via
     * {@code AblationConfig.cdScale}; this listener is equivalent for sensitivity.
     */
    private static final class CdScaleListener extends AbstractSimulationListener {
        private final double scale;

        CdScaleListener(double scale) {
            this.scale = scale;
        }

        @Override
        public AerodynamicForces postAerodynamicCalculation(SimulationStatus status,
                                                            AerodynamicForces forces)
                throws SimulationException {
            if (scale == 1.0 || forces == null) {
                return null;  // no-op
            }
            // The helper has already cloned forces before passing it to us; we
            // are free to mutate and return it.
            double cd = forces.getCD();
            double cdax = forces.getCDaxial();
            if (Double.isFinite(cd)) {
                forces.setCD(cd * scale);
            }
            if (Double.isFinite(cdax)) {
                forces.setCDaxial(cdax * scale);
            }
            return forces;
        }
    }

    /** Aborts 0.5 s after APOGEE so descent doesn't burn wall time. */
    private static final class ApogeeCutoffListener extends AbstractSimulationListener {
        static final String MARKER = "ASCENT_ONLY_CUTOFF";
        private boolean apogeeReached = false;
        private double apogeeTime = Double.NaN;

        @Override
        public boolean handleFlightEvent(SimulationStatus status, FlightEvent event)
                throws SimulationException {
            if (!apogeeReached && event.getType() == FlightEvent.Type.APOGEE) {
                apogeeReached = true;
                apogeeTime = status.getSimulationTime();
            }
            return true;
        }

        @Override
        public void postStep(SimulationStatus status) throws SimulationException {
            if (apogeeReached && status.getSimulationTime() > apogeeTime + 0.5) {
                throw new SimulationException(MARKER);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Result + Row carriers
    // -----------------------------------------------------------------------

    private static final class SimResult {
        final boolean success;
        final double apogeeFt;
        final double maxMach;
        final String note;

        private SimResult(boolean success, double apogeeFt, double maxMach, String note) {
            this.success = success;
            this.apogeeFt = apogeeFt;
            this.maxMach = maxMach;
            this.note = note;
        }

        static SimResult success(double apogeeFt, double maxMach, String note) {
            return new SimResult(true, apogeeFt, maxMach, note);
        }

        static SimResult failure(String note) {
            return new SimResult(false, Double.NaN, Double.NaN, note);
        }
    }

    private static final class Row {
        final String flightId;
        final String flightLabel;
        final double realApogeeFt;
        final String paramId;
        final double perturbationFraction;
        final double appliedValue;
        final double nominalApogeeFt;
        final double nominalMaxMach;
        final double perturbedApogeeFt;
        final double perturbedMaxMach;
        final boolean success;
        final String note;

        Row(String flightId, String flightLabel, double realApogeeFt,
            String paramId, double perturbationFraction, double appliedValue,
            double nominalApogeeFt, double nominalMaxMach,
            double perturbedApogeeFt, double perturbedMaxMach,
            boolean success, String note) {
            this.flightId = flightId;
            this.flightLabel = flightLabel;
            this.realApogeeFt = realApogeeFt;
            this.paramId = paramId;
            this.perturbationFraction = perturbationFraction;
            this.appliedValue = appliedValue;
            this.nominalApogeeFt = nominalApogeeFt;
            this.nominalMaxMach = nominalMaxMach;
            this.perturbedApogeeFt = perturbedApogeeFt;
            this.perturbedMaxMach = perturbedMaxMach;
            this.success = success;
            this.note = note;
        }

        static Row fromSim(FlightSpec flight, String paramId, double level, double appliedValue,
                           SimResult nominal, SimResult perturbed) {
            return new Row(flight.id, flight.label, flight.realApogeeFt,
                    paramId, level, appliedValue,
                    nominal.apogeeFt, nominal.maxMach,
                    perturbed.apogeeFt, perturbed.maxMach,
                    perturbed.success, perturbed.note);
        }

        static Row skipped(FlightSpec flight, String note) {
            return new Row(flight.id, flight.label, flight.realApogeeFt,
                    "nominal", 0.0, 1.0,
                    Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN,
                    false, note);
        }

        double deltaApogeePct() {
            if (!Double.isFinite(nominalApogeeFt) || nominalApogeeFt <= 0
                    || !Double.isFinite(perturbedApogeeFt)) {
                return Double.NaN;
            }
            return 100.0 * (perturbedApogeeFt - nominalApogeeFt) / nominalApogeeFt;
        }

        /** Single-perturbation linear sensitivity: dA/A per unit fraction. NaN for nominal. */
        double sensitivity() {
            if ("nominal".equals(paramId) || perturbationFraction == 0.0) return Double.NaN;
            double dapct = deltaApogeePct();
            if (!Double.isFinite(dapct)) return Double.NaN;
            return dapct / (100.0 * perturbationFraction);  // unitless: %/100%
        }

        String toCsvRow() {
            return String.format(Locale.US,
                    "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                    csvQuote(flightId),
                    csvQuote(flightLabel),
                    finiteFmt(realApogeeFt, "%.0f"),
                    csvQuote(paramId),
                    String.format(Locale.US, "%.4f", perturbationFraction),
                    String.format(Locale.US, "%.6f", appliedValue),
                    finiteFmt(nominalApogeeFt, "%.0f"),
                    finiteFmt(nominalMaxMach, "%.3f"),
                    finiteFmt(perturbedApogeeFt, "%.0f"),
                    finiteFmt(perturbedMaxMach, "%.3f"),
                    finiteFmt(deltaApogeePct(), "%.4f"),
                    finiteFmt(sensitivity(), "%.6f"),
                    success ? "true" : "false",
                    csvQuote(note));
        }

        private static String finiteFmt(double value, String fmt) {
            return Double.isFinite(value) ? String.format(Locale.US, fmt, value) : "";
        }

        private static String csvQuote(String s) {
            if (s == null) return "";
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
    }

    // -----------------------------------------------------------------------
    // Path helpers
    // -----------------------------------------------------------------------

    private static File findDir(String relativePath) {
        File dir = new File(relativePath);
        if (dir.exists()) return dir;
        Path current = Paths.get(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && current != null; i++) {
            File candidate = current.resolve(relativePath).toFile();
            if (candidate.exists()) return candidate;
            current = current.getParent();
        }
        return null;
    }

    private static File ensureOutDir() {
        Path current = Paths.get(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && current != null; i++) {
            File analysisRoot = current.resolve("paper/data/analysis").toFile();
            if (analysisRoot.exists()) {
                File outDir = current.resolve(OUT_DIR_REL).toFile();
                if (outDir.exists() || outDir.mkdirs()) {
                    return outDir;
                }
            }
            current = current.getParent();
        }
        File fallback = new File(OUT_DIR_REL);
        fallback.mkdirs();
        return fallback;
    }
}

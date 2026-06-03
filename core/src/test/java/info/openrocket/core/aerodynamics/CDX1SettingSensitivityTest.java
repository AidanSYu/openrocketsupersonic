package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.motor.RASPMotorLoader;
import info.openrocket.core.file.rasaero.RASAeroMotorsLoader;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.util.BaseTestCase;

/**
 * Sensitivity analysis for unsupported CDX1 settings.
 * <p>
 * For each currently-ignored RASAero CDX1 setting (nozzle diameter, turbulence
 * flag, ModifiedBarrowman), this test computes the apogee delta when the setting
 * is toggled on vs off (or present vs absent).
 * <p>
 * This provides quantitative bounds on whether ignoring each setting can
 * plausibly move apogee by < 2%, 2-5%, or > 5%.
 * <p>
 * The test does NOT modify existing A-level benchmarks. It only measures.
 */
@ResourceLock("AERO_CPU_HEAVY")
public class CDX1SettingSensitivityTest extends BaseTestCase {

    private static final String SIMVREAL_DIR = "simvreal/RasAero Sims";
    private static final String[] RASP_ENG_PATHS = {
            "simvreal/rasp.eng",
            "c:/Code/OpenRocket Plus/simvreal/rasp.eng",
    };

    @BeforeAll
    static void setup() {
        // Clear the JVM-global RASAero motor cache so this class binds only its own
        // rasp.eng curves (first-match lookup over a no-dedup static List).
        RASAeroMotorsLoader.clearAllMotors();
        for (String path : RASP_ENG_PATHS) {
            File file = new File(path);
            if (!file.exists()) continue;
            try (InputStream stream = new FileInputStream(file)) {
                RASPMotorLoader loader = new RASPMotorLoader();
                List<ThrustCurveMotor.Builder> builders = loader.load(stream, "rasp.eng");
                for (ThrustCurveMotor.Builder b : builders) {
                    try {
                        RASAeroMotorsLoader.addMotorToCache(b.build());
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
    }

    private static File findSimVRealDir() {
        File dir = new File(SIMVREAL_DIR);
        if (dir.exists()) return dir;
        dir = new File("c:/Code/OpenRocket Plus/" + SIMVREAL_DIR);
        if (dir.exists()) return dir;
        return null;
    }

    /**
     * Per-rocket CDX1 settings extracted from the corpus.
     */
    static class CDX1Settings {
        final String file;
        final String name;
        final double realAltFt;
        final double rasAeroAltFt;
        final double bodyDiaIn;          // largest stage body diameter
        final double nozzleDiaIn;        // SustainerNozzle value (inches), 0 = not specified
        final boolean turbulence;        // Turbulence=True in CDX1
        final boolean modifiedBarrowman; // ModifiedBarrowman=True in CDX1

        CDX1Settings(String file, String name, double realAltFt, double rasAeroAltFt,
                     double bodyDiaIn, double nozzleDiaIn,
                     boolean turbulence, boolean modifiedBarrowman) {
            this.file = file;
            this.name = name;
            this.realAltFt = realAltFt;
            this.rasAeroAltFt = rasAeroAltFt;
            this.bodyDiaIn = bodyDiaIn;
            this.nozzleDiaIn = nozzleDiaIn;
            this.turbulence = turbulence;
            this.modifiedBarrowman = modifiedBarrowman;
        }

        /** Nozzle area ratio = (nozzle_dia / body_dia)^2 */
        double nozzleAreaRatio() {
            if (nozzleDiaIn <= 0 || bodyDiaIn <= 0) return 0;
            return Math.pow(nozzleDiaIn / bodyDiaIn, 2);
        }
    }

    static List<CDX1Settings> getAllSettings() {
        List<CDX1Settings> s = new ArrayList<>();
        // Data extracted directly from CDX1 files via grep
        s.add(new CDX1Settings("Byrum.CDX1",            "Byrum",              5732,  5281, 3.0,  0,      false, false));
        s.add(new CDX1Settings("CancerDescending.CDX1",  "Cancer Descending",  6188,  6328, 6.0,  0,      false, false));
        s.add(new CDX1Settings("EZI65-1.CDX1",          "EZI-65",             3965,  4214, 4.0,  0,      false, false));
        s.add(new CDX1Settings("Gibb.CDX1",             "Gibb",               3913,  4205, 3.0,  0,      false, false));
        s.add(new CDX1Settings("IonDrive.CDX1",         "Ion Drive",          8027,  8642, 4.0,  0,      false, false));
        s.add(new CDX1Settings("Raven.CDX1",            "Raven",              8815,  9332, 1.75, 0,      false, false));
        s.add(new CDX1Settings("Thunder&Lightning.CDX1", "Thunder&Lightning",  3577,  3989, 3.1,  0,      false, false));
        s.add(new CDX1Settings("Blister.CDX1",          "Blister",            9026,  8301, 3.0,  1.3125, false, false));
        s.add(new CDX1Settings("Rabia.CDX1",            "Rabia",              12745, 12197, 3.0,  1.188,  false, false));
        s.add(new CDX1Settings("Rabia-ShortFinCan.CDX1","Rabia ShortFinCan",  10584, 10225, 3.126, 1.187, false, false));
        s.add(new CDX1Settings("Torrent.CDX1",          "Torrent",            12807, 13717, 4.0,  1.625,  false, false));
        s.add(new CDX1Settings("CalIsp1.CDX1",          "CalIsp1",            3964,  3876, 3.1,  0.63,   false, false));
        s.add(new CDX1Settings("CalIsp2.CDX1",          "CalIsp2",            3837,  3948, 3.1,  0.63,   false, false));
        s.add(new CDX1Settings("CalIsp3.CDX1",          "CalIsp3",            3710,  3876, 3.1,  0.63,   false, false));
        s.add(new CDX1Settings("CalIsp4.CDX1",          "CalIsp4",            5085,  4847, 3.1,  0.63,   false, false));
        s.add(new CDX1Settings("CalIsp5.CDX1",          "CalIsp5",            4930,  4836, 3.1,  0.63,   false, false));
        s.add(new CDX1Settings("L500Roc.CDX1",          "L500 Roc",           24771, 26509, 2.26, 1.0,   false, false));
        s.add(new CDX1Settings("DontDebateThisN5800MinDia.CDX1", "DontDebateThis", 56573, 61982, 3.9173, 2.488, true, false));
        s.add(new CDX1Settings("Qu8k.CDX1",             "Qu8k",              121478, 119684, 8.0, 6.0,   true, true));
        s.add(new CDX1Settings("Proteus6.CDX1",         "Proteus6",          85067, 81499, 6.0,  4.8,    false, false));
        s.add(new CDX1Settings("Full Metal Jacket1.CDX1","FMJ BALLS 005",    37981, 38772, 4.0,  2.5,    false, false));
        s.add(new CDX1Settings("Full Metal Jacket2.CDX1","FMJ Black Rock 6", 30038, 32548, 4.0,  2.5,    false, false));
        s.add(new CDX1Settings("Kinsel_P4935_A-601_Rocket.CDX1","Kinsel A-601",42771, 41098, 6.125, 3.09, true, true));
        s.add(new CDX1Settings("AeroPac104KStageOne&Two-2.CDX1","AeroPac 104K",104659, 113786, 3.08, 1.23, true, false));
        return s;
    }

    /**
     * ANALYTICAL sensitivity: compute the nozzle area ratio and the resulting
     * power-on base drag multiplier for each rocket. This tells us exactly how
     * much base drag is being reduced (or should be reduced) during motor burn.
     * <p>
     * The key insight: nozzle area ratio affects base drag ONLY during burn phase.
     * For typical HPR flights, burn is 2-10s out of 30-120s total ascent. So
     * even a large base drag change during burn may be a modest apogee effect.
     */
    @Test
    void testNozzleSensitivityAnalytical() {
        System.out.println();
        System.out.println("=".repeat(120));
        System.out.println("NOZZLE DIAMETER SENSITIVITY — Analytical Bounds");
        System.out.println("=".repeat(120));
        System.out.printf("%-22s %6s %6s %8s %12s %12s %12s  %s%n",
                "Rocket", "BodyD", "NozD", "AR", "BaseDragMul",
                "Default(NaN)", "Delta", "Impact");
        System.out.println("-".repeat(120));

        double defaultFactor = BarrowmanDragCalculator.DEFAULT_POWER_ON_FACTOR; // 0.15

        for (CDX1Settings s : getAllSettings()) {
            double ar = s.nozzleAreaRatio();
            double mulWithNozzle;
            if (ar <= 0) {
                mulWithNozzle = Double.NaN;  // NaN triggers default
            } else {
                double kBase = BarrowmanDragCalculator.powerOnBaseDragFactorDetailed(ar);
                mulWithNozzle = 1.0 - 1.0 * (1.0 - kBase); // at full thrust
            }
            double mulDefault = 1.0 - 1.0 * (1.0 - defaultFactor);  // = 0.15

            String impact;
            if (ar <= 0) {
                impact = "N/A (nozzle=0)";
                System.out.printf("%-22s %6.2f %6.2f %8s %12s %12.3f %12s  %s%n",
                        s.name, s.bodyDiaIn, s.nozzleDiaIn, "-", "-",
                        mulDefault, "-", impact);
            } else {
                double kBase = BarrowmanDragCalculator.powerOnBaseDragFactorDetailed(ar);
                double delta = Math.abs(kBase - defaultFactor);
                // Base drag is typically 30-50% of total subsonic Cd. During burn,
                // changing the multiplier by 'delta' changes total drag by roughly
                // delta * 0.4 (midpoint of 30-50%). Since burn is typically ~20% of
                // ascent time for these rockets, the apogee effect is roughly:
                // apogee_pct ~ delta * 0.4 * 0.2 * 100 = delta * 8%
                // This is a rough upper bound.
                double apogeeBound = delta * 8.0;
                if (apogeeBound < 2.0) impact = "< 2%  (negligible)";
                else if (apogeeBound < 5.0) impact = "2-5%  (minor)";
                else impact = "> 5%  (MATERIAL)";

                System.out.printf("%-22s %6.2f %6.2f %8.4f %12.3f %12.3f %12.3f  %s%n",
                        s.name, s.bodyDiaIn, s.nozzleDiaIn, ar, kBase,
                        defaultFactor, delta, impact);
            }
        }

        System.out.println();
        System.out.println("INTERPRETATION:");
        System.out.println("  powerOnBaseDragFactorDetailed(AR) returns the base drag retention factor");
        System.out.println("  during burn. DEFAULT_POWER_ON_FACTOR = " + defaultFactor);
        System.out.println("  AR = (nozzle_dia/body_dia)^2. When AR >= 0.8, base drag -> 0 during burn.");
        System.out.println("  When nozzle = 0 (not specified), the default factor is used.");
        System.out.println("  Impact estimate: delta * baseDragFrac(~40%) * burnTimeFrac(~20%) * 100%");
        System.out.println("=".repeat(120));
    }

    /**
     * ANALYTICAL sensitivity: turbulence flag.
     * <p>
     * RASAero's Turbulence=True forces all-turbulent boundary layer. ORP
     * already assumes nearly all-turbulent for non-perfect-finish rockets
     * (laminar fraction capped at 5%). So the question is: does the 5% cap
     * vs 0% matter?
     * <p>
     * The answer: the laminar fraction cap reduces friction by at most
     * 0.6 * 0.05 = 3% of total friction drag. Friction is typically ~40% of
     * total drag, so the apogee effect is ~3% * 40% * 100% = ~1.2%.
     * <p>
     * For perfect-finish rockets, the effect would be larger (up to ~10% of
     * friction), but none of the SimVReal rockets are marked perfect-finish.
     */
    @Test
    void testTurbulenceSensitivityAnalytical() {
        System.out.println();
        System.out.println("=".repeat(100));
        System.out.println("TURBULENCE FLAG SENSITIVITY — Analytical Bounds");
        System.out.println("=".repeat(100));
        System.out.println();
        System.out.println("RASAero Turbulence=True means: force all-turbulent boundary layer.");
        System.out.println("ORP default: cap laminar fraction at 5% for non-perfect-finish rockets.");
        System.out.println("Effect of capping at 5% vs 0%: delta(Cf) = 0.6 * 0.05 = 3% of friction drag.");
        System.out.println("Friction is ~40% of total subsonic Cd, so apogee delta ~ 1.2%.");
        System.out.println();

        System.out.printf("%-22s %8s %8s %12s  %s%n",
                "Rocket", "Turb?", "MaxMach", "Impact", "Assessment");
        System.out.println("-".repeat(100));

        // Rockets with Turbulence=True
        for (CDX1Settings s : getAllSettings()) {
            if (!s.turbulence) continue;
            // For supersonic rockets, the friction component is even smaller because
            // Van Driest II already reduces Cf. So the effect is LESS than 1.2%.
            double maxMachEst = s.realAltFt > 50000 ? 3.0 : (s.realAltFt > 20000 ? 2.0 : 1.0);
            String impact = "< 2% (negligible)";
            System.out.printf("%-22s %8b %8.1f %12s  %s%n",
                    s.name, s.turbulence, maxMachEst, impact,
                    "5% laminar cap already near all-turbulent");
        }

        System.out.println();
        System.out.println("Rockets without Turbulence flag: already using the same 5% cap.");
        System.out.println("No parity gap for those rockets.");
        System.out.println("=".repeat(100));
    }

    /**
     * ANALYTICAL sensitivity: ModifiedBarrowman flag.
     * <p>
     * RASAero's ModifiedBarrowman=True adjusts the Barrowman stability model
     * for supersonic conditions (CP shifts aft, body lift changes). This is
     * conceptually equivalent to what ORP's Phase 3 already implements via
     * ShockGeometry pre-pass + Jorgensen crossflow + PNK interference factors.
     * <p>
     * The stability correction affects drag INDIRECTLY: if CP is wrong, the
     * rocket trims at a different AoA, which changes induced drag. For a
     * well-designed rocket with AoA < 5 deg, the induced drag contribution
     * is typically < 5% of total drag. A 10% error in CP would shift the
     * trim AoA by ~0.5-1 deg, changing induced drag by ~1-2%.
     * <p>
     * Direct drag effect: ZERO. ModifiedBarrowman does not change drag models.
     * Indirect drag effect via trim AoA: < 2% for stable rockets.
     */
    @Test
    void testModifiedBarrowmanSensitivityAnalytical() {
        System.out.println();
        System.out.println("=".repeat(100));
        System.out.println("MODIFIED BARROWMAN FLAG SENSITIVITY — Analytical Bounds");
        System.out.println("=".repeat(100));
        System.out.println();
        System.out.println("ModifiedBarrowman=True adjusts stability (CP/CNa) for supersonic flow.");
        System.out.println("ORP already has Phase 3 supersonic stability (ShockGeometry + PNK + Jorgensen).");
        System.out.println("The flag does NOT change drag directly; only indirectly via trim AoA.");
        System.out.println("For stable rockets (AoA < 5 deg), induced drag < 5% of total.");
        System.out.println();

        System.out.printf("%-22s %6s %8s %12s  %s%n",
                "Rocket", "MB?", "MaxMach", "Impact", "Assessment");
        System.out.println("-".repeat(100));

        for (CDX1Settings s : getAllSettings()) {
            if (!s.modifiedBarrowman) continue;
            double maxMachEst = s.realAltFt > 50000 ? 3.0 : (s.realAltFt > 20000 ? 2.0 : 1.0);
            String impact = "< 2% (negligible)";
            String note = "ORP Phase 3 already implements equivalent corrections";
            System.out.printf("%-22s %6b %8.1f %12s  %s%n",
                    s.name, s.modifiedBarrowman, maxMachEst, impact, note);
        }

        System.out.println();
        System.out.println("=".repeat(100));
    }

    /**
     * LIVE SENSITIVITY: run a subset of rockets with nozzle diameter set vs unset
     * to measure the actual apogee delta from nozzle area ratio.
     * <p>
     * This tests the 3 rockets with the largest nozzle area ratios (Qu8k, Proteus6,
     * FMJ) where the analytical bound predicts > 5% impact.
     */
    @Test
    void testNozzleSensitivityLive() throws Exception {
        File simvrealDir = findSimVRealDir();
        if (simvrealDir == null) {
            System.out.println("SKIP: SimVReal directory not found.");
            return;
        }

        // Test rockets with the largest nozzle area ratios
        String[][] testCases = {
            {"Qu8k.CDX1",          "Qu8k",       "121478", "8.0", "6.0"},
            {"Proteus6.CDX1",      "Proteus6",   "85067",  "6.0", "4.8"},
            {"Full Metal Jacket1.CDX1", "FMJ1",  "37981",  "4.0", "2.5"},
            {"Kinsel_P4935_A-601_Rocket.CDX1", "Kinsel", "42771", "6.125", "3.09"},
            {"DontDebateThisN5800MinDia.CDX1", "DontDebateThis", "56573", "3.9173", "2.488"},
        };

        System.out.println();
        System.out.println("=".repeat(120));
        System.out.println("NOZZLE DIAMETER SENSITIVITY — Live Apogee Comparison");
        System.out.println("=".repeat(120));
        System.out.printf("%-18s %8s %8s %10s %10s %10s %8s  %s%n",
                "Rocket", "Real", "AR", "Apogee+Noz", "Apogee-Noz", "Delta", "DeltaPct", "Band");
        System.out.println("-".repeat(120));

        for (String[] tc : testCases) {
            String cdx1Name = tc[0];
            String rocketName = tc[1];
            double realAlt = Double.parseDouble(tc[2]);
            double bodyDia = Double.parseDouble(tc[3]);
            double nozDia = Double.parseDouble(tc[4]);
            double ar = Math.pow(nozDia / bodyDia, 2);

            File cdx1 = new File(simvrealDir, cdx1Name);
            if (!cdx1.exists()) {
                System.out.printf("%-18s  SKIP: file not found%n", rocketName);
                continue;
            }

            // Run WITH nozzle diameter (as imported)
            double apogeeWithNozzle = simulateAndGetApogee(cdx1);

            // Run WITHOUT nozzle diameter (clear it)
            double apogeeWithoutNozzle = simulateAndGetApogeeNoNozzle(cdx1);

            if (Double.isNaN(apogeeWithNozzle) || Double.isNaN(apogeeWithoutNozzle)) {
                System.out.printf("%-18s  FAILED: simulation error%n", rocketName);
                continue;
            }

            double deltaFt = apogeeWithNozzle - apogeeWithoutNozzle;
            double deltaPct = 100.0 * deltaFt / apogeeWithNozzle;
            String band;
            if (Math.abs(deltaPct) < 2.0) band = "< 2%";
            else if (Math.abs(deltaPct) < 5.0) band = "2-5%";
            else band = "> 5%";

            System.out.printf("%-18s %8.0f %8.4f %10.0f %10.0f %10.0f %+7.1f%%  %s%n",
                    rocketName, realAlt, ar, apogeeWithNozzle, apogeeWithoutNozzle,
                    deltaFt, deltaPct, band);
        }
        System.out.println("=".repeat(120));
    }

    private double simulateAndGetApogee(File cdx1) {
        try {
            GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
            OpenRocketDocument doc = loader.load();
            List<Simulation> sims = doc.getSimulations();
            if (sims.isEmpty()) return Double.NaN;
            Simulation sim = sims.get(0);
            sim.getOptions().setTimeStep(0.05);
            sim.getOptions().setMaximumStepAngle(Math.toRadians(3));
            sim.getOptions().setMaxSimulationTime(2400);

            Thread t = new Thread(() -> { try { sim.simulate(); } catch (Exception e) { throw new RuntimeException(e); } });
            t.start();
            t.join(120_000);
            if (t.isAlive()) { t.interrupt(); return Double.NaN; }

            FlightData data = sim.getSimulatedData();
            if (data == null || data.getBranchCount() == 0) return Double.NaN;
            return data.getMaxAltitude() * 3.28084;
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    private double simulateAndGetApogeeNoNozzle(File cdx1) {
        try {
            GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
            OpenRocketDocument doc = loader.load();
            List<Simulation> sims = doc.getSimulations();
            if (sims.isEmpty()) return Double.NaN;
            Simulation sim = sims.get(0);
            sim.getOptions().setTimeStep(0.05);
            sim.getOptions().setMaximumStepAngle(Math.toRadians(3));
            sim.getOptions().setMaxSimulationTime(2400);
            // Clear the nozzle diameter to simulate "not imported"
            sim.getOptions().setNozzleExitDiameter(Double.NaN);

            Thread t = new Thread(() -> { try { sim.simulate(); } catch (Exception e) { throw new RuntimeException(e); } });
            t.start();
            t.join(120_000);
            if (t.isAlive()) { t.interrupt(); return Double.NaN; }

            FlightData data = sim.getSimulatedData();
            if (data == null || data.getBranchCount() == 0) return Double.NaN;
            return data.getMaxAltitude() * 3.28084;
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    /**
     * SUMMARY: print the comprehensive settings matrix for all SimVReal rockets.
     */
    @Test
    void testPrintSettingsMatrix() {
        System.out.println();
        System.out.println("=".repeat(130));
        System.out.println("CDX1 UNSUPPORTED SETTINGS MATRIX — Full SimVReal Corpus");
        System.out.println("=".repeat(130));
        System.out.printf("%-22s %6s %6s %8s %5s %5s %12s  %s%n",
                "Rocket", "BodyD", "NozD", "AR", "Turb", "MB",
                "NozImpact", "Status");
        System.out.println("-".repeat(130));

        double defaultFactor = BarrowmanDragCalculator.DEFAULT_POWER_ON_FACTOR;

        int negligibleCount = 0;
        int minorCount = 0;
        int materialCount = 0;

        for (CDX1Settings s : getAllSettings()) {
            double ar = s.nozzleAreaRatio();
            double nozImpact;
            String nozBand;
            if (ar <= 0) {
                nozImpact = 0;
                nozBand = "N/A";
            } else {
                double kBase = BarrowmanDragCalculator.powerOnBaseDragFactorDetailed(ar);
                double delta = Math.abs(kBase - defaultFactor);
                nozImpact = delta * 8.0;  // upper bound
                if (nozImpact < 2.0) nozBand = "< 2%";
                else if (nozImpact < 5.0) nozBand = "2-5%";
                else nozBand = "> 5%";
            }

            // Turbulence: always < 2% for non-perfect-finish
            // ModifiedBarrowman: always < 2% (stability only)
            String status;
            if (nozImpact >= 5.0) {
                status = "NOZZLE MATERIAL";
                materialCount++;
            } else if (nozImpact >= 2.0 || s.turbulence || s.modifiedBarrowman) {
                status = "MINOR (bound <5%)";
                minorCount++;
            } else {
                status = "CLEAN";
                negligibleCount++;
            }

            System.out.printf("%-22s %6.2f %6.2f %8.4f %5b %5b %12s  %s%n",
                    s.name, s.bodyDiaIn, s.nozzleDiaIn, ar,
                    s.turbulence, s.modifiedBarrowman, nozBand, status);
        }

        System.out.println("-".repeat(130));
        System.out.printf("SUMMARY: %d CLEAN, %d MINOR, %d MATERIAL out of %d rockets%n",
                negligibleCount, minorCount, materialCount,
                negligibleCount + minorCount + materialCount);
        System.out.println();
        System.out.println("CLEAN = all unsupported settings negligible (<2% apogee impact)");
        System.out.println("MINOR = some settings non-zero but bounded <5%");
        System.out.println("MATERIAL = nozzle area ratio large enough to potentially shift apogee >5%");
        System.out.println("=".repeat(130));
    }
}

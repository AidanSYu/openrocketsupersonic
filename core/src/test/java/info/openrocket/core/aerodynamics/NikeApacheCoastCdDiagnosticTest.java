package info.openrocket.core.aerodynamics;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.database.motor.MotorDatabase;
import info.openrocket.core.database.motor.ThrustCurveMotorSetDatabase;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.models.atmosphere.ExtendedISAModel;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.startup.Application;

/**
 * Diagnostic-only test (no assertions, prints a table) used to compare
 * OpenRocket Plus's coast-phase Cd against the NASA Apache Performance
 * Handbook canonical Apache drag table at high Mach.
 * <p>
 * Reference: NASA X-721-66-569 / TM X-55700, "Nike Apache Performance
 * Handbook", Galloway &amp; Crough (GSFC), Dec 1966.  Appendix A, page 66,
 * "APACHE DRAG COEFFICIENTS" Case 1 (clean / no antenna) COASTING.
 * <p>
 * This is the canonical Apache coast Cd vs Mach used by NASA for the
 * trajectory tables in Appendix B of the same Handbook.  The Cd is
 * referenced to the 6.62-in (0.0826-m diameter, 0.0214-m^2) Apache
 * payload section frontal area.
 * <p>
 * Apache Case 1 COASTING handbook values (Handbook Apx A p.66):
 * <pre>
 *   M     Cd
 *  1.00  0.930
 *  1.25  0.841
 *  1.50  0.785
 *  1.75  0.740
 *  2.00  0.704
 *  2.50  0.643
 *  3.00  0.590
 *  3.50  0.544
 *  4.00  0.507
 *  4.50  0.479
 *  5.00  0.454
 *  5.50  0.432
 *  6.00  0.412
 *  6.50  0.396
 *  7.00  0.388
 *  7.50  0.384
 *  8.00  0.384
 * </pre>
 * Case 3 (turnstile antenna) COASTING values, same page:
 * <pre>
 *   M=2: 0.924, M=3: 0.803, M=4: 0.714, M=5: 0.656, M=6: 0.608, M=7: 0.578
 * </pre>
 * <p>
 * The harness loads the canonical {@code nike_apache.ork} (which is also
 * the base for the Nike-Apache 1965 nine-flight set), deactivates stage 1
 * (Nike booster) so only the Apache sustainer remains, sets coast altitude
 * + Mach with no thrust, and queries ORP's Barrowman pipeline for the
 * decomposed Cd (friction / pressure / base / total).
 * <p>
 * Output: a markdown-style table on stdout.  Sample call from the project
 * root:
 * <pre>
 *   gradlew core:test --tests info.openrocket.core.aerodynamics.NikeApacheCoastCdDiagnosticTest
 * </pre>
 */
public class NikeApacheCoastCdDiagnosticTest {

    /** Apache Case 1 COASTING handbook values, M = 1.0 .. 8.0 (Handbook Apx A p.66). */
    private static final double[] HANDBOOK_MACH = {
            1.00, 1.25, 1.50, 1.75,
            2.00, 2.50, 3.00, 3.50,
            4.00, 4.50, 5.00, 5.50,
            6.00, 6.50, 7.00, 7.50, 8.00
    };
    private static final double[] HANDBOOK_CD_CASE1 = {
            0.930, 0.841, 0.785, 0.740,
            0.704, 0.643, 0.590, 0.544,
            0.507, 0.479, 0.454, 0.432,
            0.412, 0.396, 0.388, 0.384, 0.384
    };
    private static final double[] HANDBOOK_CD_CASE3 = {
            // M=1.00..1.75 from p.67; M=2..8 main grid (case 3 = turnstile antennas)
            1.260, 1.116, 1.028, 0.966,
            0.924, 0.860, 0.803, 0.754,
            0.714, 0.684, 0.656, 0.631,
            0.608, 0.590, 0.578, 0.574, 0.574
    };

    /** Apache coast altitude profile (approx., from Handbook Apx B p.70).
     *  Used to set the atmosphere at each Mach sample point.
     *  These are typical altitudes at which the Apache sustainer is coasting
     *  past the given Mach during ascent (after Apache burnout at ~26.4 s).
     */
    private static final double[] COAST_ALT_M = {
            //  M=1.0   1.25   1.50   1.75
            150_000, 145_000, 140_000, 135_000,
            //  M=2.0   2.50   3.00   3.50
            130_000, 120_000, 100_000, 80_000,
            //  M=4.0   4.50   5.00   5.50
             60_000,  50_000,  40_000,  35_000,
            //  M=6.0   6.50   7.00   7.50   8.00
             30_000,  28_000,  26_000,  25_000,  24_000
    };

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

    @Test
    void dumpApacheCoastCdVsHandbook() throws Exception {
        File ork = findOrk("nike_apache.ork");
        // The nine-flight Nike-Apache 1965 ORKs all derive from nike_apache.ork
        // (see _build_nike_apache_1965_orks.py); they share the canonical Apache
        // geometry. So computing Cd against the canonical .ork directly is
        // equivalent to computing it against any of the nine 1965 flights at
        // the same coast Mach.

        GeneralRocketLoader loader = new GeneralRocketLoader(ork);
        OpenRocketDocument doc = loader.load();
        Rocket rocket = doc.getRocket();
        FlightConfiguration config = rocket.getSelectedConfiguration();
        config.setAllStages();

        // Deactivate the Nike booster stage (stage 0) so only the Apache
        // sustainer remains. The Apache coast occurs AFTER Nike separation.
        // Find the first AxialStage and turn it off.
        for (RocketComponent c : rocket) {
            if (c instanceof AxialStage) {
                int sn = ((AxialStage) c).getStageNumber();
                if (sn == 0) {
                    config._setStageActive(sn, false);
                    break;
                }
            }
        }

        // Reference area = Apache payload section frontal area (6.62 in dia)
        // = pi * (0.0826/2)^2 m^2 = 5.36e-3 m^2.  ORP will use whatever the
        // configuration's max-radius reference area is; verify it lines up.
        double refAreaOrp = config.getReferenceArea();
        double apachePayloadRefArea = Math.PI * Math.pow(6.62 * 0.0254 / 2.0, 2);

        System.out.println();
        System.out.println("=".repeat(105));
        System.out.println("Apache Coast Cd vs Mach -- ORP vs NASA Handbook (X-721-66-569 Apx A p.66 Case 1 COASTING)");
        System.out.println("=".repeat(105));
        System.out.printf(Locale.US,
                "ORK file               : %s%n", ork.getAbsolutePath());
        System.out.printf(Locale.US,
                "ORP reference area     : %.5f m^2  (= apache payload frontal area %.5f m^2)%n",
                refAreaOrp, apachePayloadRefArea);
        System.out.printf(Locale.US,
                "Active configuration   : %s%n", config.getName());
        System.out.println();

        // Header
        System.out.println(String.format(Locale.US,
                "| %4s | %7s | %7s | %8s | %8s | %8s | %8s | %8s | %8s | %8s |",
                "Mach", "alt_km", "T_K", "p_Pa",
                "Cd_HB1", "Cd_HB3", "Cd_ORP", "Cd_fric", "Cd_press", "Cd_base"));
        System.out.println(String.format(Locale.US,
                "|------|---------|---------|----------|----------|----------|----------|----------|----------|----------|"));

        // ISA atmosphere model
        ExtendedISAModel isa = new ExtendedISAModel();

        double deficitSum = 0.0;
        int nDeficit = 0;

        for (int i = 0; i < HANDBOOK_MACH.length; i++) {
            double mach = HANDBOOK_MACH[i];
            double altM = COAST_ALT_M[i];

            AtmosphericConditions atm = isa.getConditions(altM);

            FlightConditions conditions = new FlightConditions(config);
            conditions.setMach(mach);
            conditions.setAOA(0.0);
            conditions.setAtmosphericConditions(atm);
            conditions.setThrustLevel(0.0);  // coasting
            conditions.setNozzleAreaRatio(0.0);

            BarrowmanCalculator calc = new BarrowmanCalculator();
            WarningSet warnings = new WarningSet();
            AerodynamicForces total = calc.getAerodynamicForces(config, conditions, warnings);

            double cdTotal = total.getCD();
            double cdFric = total.getFrictionCD();
            double cdPres = total.getPressureCD();
            double cdBase = total.getBaseCD();

            double cdHbCase1 = HANDBOOK_CD_CASE1[i];
            double cdHbCase3 = HANDBOOK_CD_CASE3[i];

            System.out.println(String.format(Locale.US,
                    "| %4.2f | %7.1f | %7.1f | %8.1f | %8.3f | %8.3f | %8.3f | %8.4f | %8.4f | %8.4f |",
                    mach, altM / 1000.0,
                    atm.getTemperature(), atm.getPressure(),
                    cdHbCase1, cdHbCase3, cdTotal,
                    cdFric, cdPres, cdBase));

            if (mach >= 5.0) {
                deficitSum += (cdHbCase1 - cdTotal);
                nDeficit++;
            }
        }

        System.out.println();
        if (nDeficit > 0) {
            System.out.printf(Locale.US,
                    "Mean Case-1 Cd deficit for M >= 5: %+.4f  (handbook - ORP, averaged over %d points)%n",
                    deficitSum / nDeficit, nDeficit);
        }
        System.out.println("=".repeat(105));
        System.out.println();
    }

    private static File findOrk(String name) {
        Path[] candidates = {
                Paths.get("paper", "data", "ork", "sounding_rockets", name),
                Paths.get("..", "paper", "data", "ork", "sounding_rockets", name),
        };
        for (Path p : candidates) {
            File f = p.toFile();
            if (f.exists()) return f;
        }
        throw new IllegalStateException("could not locate " + name +
                " (tried: " + List.of(candidates) + " from " +
                new File(".").getAbsolutePath() + ")");
    }
}

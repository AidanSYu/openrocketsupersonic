package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.InstanceContext;
import info.openrocket.core.rocketcomponent.InstanceMap;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.startup.Application;

/**
 * READ-ONLY diagnostic audit of the THICK_BL (thick boundary layer) base-drag
 * multiplier for the Raven SimVReal outlier. Loads Raven.CDX1, reconstructs
 * the peak-Mach flight condition from the saved trajectory CSV, and reports:
 * <ul>
 *   <li>body tube L and D (per {@code computeBodyTubeLength})</li>
 *   <li>reynolds number and BL thickness at the base station (1/7-power)</li>
 *   <li>δ/R at the base station</li>
 *   <li>whether the two gates (Mach, L/D) fire, and the resulting multiplier</li>
 *   <li>the "what multiplier would close Raven to +15%" target for comparison</li>
 * </ul>
 * This test NEVER modifies aerodynamic code or asserts the current THICK_BL
 * behaviour is correct; it prints the trace to stdout and passes as long as
 * the audit can produce numbers for every field.
 * <p>
 * Peak-Mach point from `core/build/reports/simvreal-outliers/Raven-trajectory.csv`:
 * t=1.648 s, M=1.108, altitude=411 m, T=307.12 K, p=96851.8 Pa, rho=1.099 kg/m³,
 * v=389.3 m/s.
 */
@ResourceLock("AERO_CPU_HEAVY")
public class RavenThickBLAuditTest {

    private static final String SIMVREAL_DIR = "simvreal/RasAero Sims";
    private static final double IN_PER_METER = 39.3700787401;

    // Raven peak-Mach sample from build/reports/simvreal-outliers/Raven-trajectory.csv
    private static final double RAVEN_MAX_MACH = 1.108;
    private static final double RAVEN_MAX_MACH_ALT_M = 411.1;
    private static final double RAVEN_MAX_MACH_TEMP_K = 307.12;
    private static final double RAVEN_MAX_MACH_PRESSURE_PA = 96851.8;
    private static final double RAVEN_MAX_MACH_V_M_S = 389.3;

    @BeforeAll
    static void setup() {
        Module applicationModule = new ServicesForTesting();
        Module pluginModule = new PluginModule();
        Injector injector = Guice.createInjector(applicationModule, pluginModule);
        Application.setInjector(injector);
    }

    private static File findSimVRealDir() {
        File dir = new File(SIMVREAL_DIR);
        if (dir.exists()) return dir;
        Path current = Paths.get(System.getProperty("user.dir"));
        for (int i = 0; i < 5; i++) {
            File candidate = current.resolve(SIMVREAL_DIR).toFile();
            if (candidate.exists()) return candidate;
            current = current.getParent();
            if (current == null) break;
        }
        return null;
    }

    @Test
    void auditRavenThickBLMultiplier() throws Exception {
        File simvrealDir = findSimVRealDir();
        if (simvrealDir == null) {
            System.out.println("SKIP: SimVReal directory not found");
            return;
        }
        File cdx1 = new File(simvrealDir, "Raven.CDX1");
        assertTrue(cdx1.exists(), "Raven.CDX1 not found at " + cdx1.getAbsolutePath());

        GeneralRocketLoader loader = new GeneralRocketLoader(cdx1);
        OpenRocketDocument doc = loader.load();
        Rocket rocket = doc.getRocket();
        FlightConfiguration config = rocket.getSelectedConfiguration();
        config.setAllStages();

        System.out.println();
        System.out.println("=".repeat(90));
        System.out.println("Raven THICK_BL Audit (READ-ONLY)");
        System.out.println("=".repeat(90));

        // --- Geometry ---
        BodyTube body = null;
        for (RocketComponent c : rocket) {
            if (c instanceof BodyTube) { body = (BodyTube) c; break; }
        }
        assertNotNull(body, "Raven must have a BodyTube");

        double bodyLenM = body.getLength();
        double baseRadiusM = body.getAftRadius();
        double bodyDiaM = 2.0 * baseRadiusM;
        double bodyLDbody = bodyLenM / bodyDiaM;

        System.out.printf(Locale.US,
                "Body tube: length=%.4f m (%.3f in), diameter=%.4f m (%.3f in), L/D=%.2f%n",
                bodyLenM, bodyLenM * IN_PER_METER,
                bodyDiaM, bodyDiaM * IN_PER_METER,
                bodyLDbody);

        // Absolute X of base station (nose tip -> body aft face)
        InstanceMap imap = config.getActiveInstances();
        ArrayList<InstanceContext> bodyCtxs = imap.get(body);
        assertNotNull(bodyCtxs, "BodyTube must be in active instance map");
        double xBase = bodyCtxs.get(0).getLocation().getX() + body.getLength();
        System.out.printf(Locale.US,
                "Base station absolute X (from nose tip) = %.4f m (%.3f in)%n",
                xBase, xBase * IN_PER_METER);

        // --- Reconstruct peak-Mach flight conditions ---
        FlightConditions conditions = new FlightConditions(config);
        AtmosphericConditions atm = new AtmosphericConditions(
                RAVEN_MAX_MACH_TEMP_K, RAVEN_MAX_MACH_PRESSURE_PA);
        conditions.setAtmosphericConditions(atm);
        conditions.setMach(RAVEN_MAX_MACH);

        double nu = atm.getKinematicViscosity();
        double rho = atm.getDensity();
        double a = atm.getMachSpeed();
        double v = RAVEN_MAX_MACH * a;

        System.out.printf(Locale.US,
                "Atmosphere at max-Mach: T=%.2f K, P=%.1f Pa, rho=%.4f kg/m³, a=%.2f m/s, nu=%.3e m²/s%n",
                RAVEN_MAX_MACH_TEMP_K, RAVEN_MAX_MACH_PRESSURE_PA, rho, a, nu);
        System.out.printf(Locale.US,
                "Freestream: M=%.3f, V=%.2f m/s (trajectory CSV value: %.2f m/s)%n",
                RAVEN_MAX_MACH, v, RAVEN_MAX_MACH_V_M_S);

        // --- Manually compute expected delta/R (matches calc in THICK_BL method) ---
        double reX = v * xBase / nu;
        double delta17 = xBase * 0.37 / Math.pow(reX, 0.2);
        double deltaOverR = delta17 / baseRadiusM;
        System.out.printf(Locale.US,
                "Re_x at base = %.3e, delta (1/7-power) = %.5f m (%.3f in), delta/R = %.4f%n",
                reX, delta17, delta17 * IN_PER_METER, deltaOverR);

        // --- Manually trace the L/D gate that the method computes ---
        // (uses computeBodyTubeLength, which sums all cylindrical segments)
        // We can't call the private helper directly, but on a single-body rocket
        // with no fin can it equals this body's length.
        double[] totalBodyTubeLen = { 0.0 };
        for (var e : imap.entrySet()) {
            RocketComponent c = e.getKey();
            if (!(c instanceof SymmetricComponent)) continue;
            SymmetricComponent sc = (SymmetricComponent) c;
            double fr = sc.getForeRadius();
            double ar = sc.getAftRadius();
            double rMax = Math.max(fr, ar);
            if (rMax < 1e-12) continue;
            if (Math.abs(fr - ar) / rMax < 0.01) {
                totalBodyTubeLen[0] += sc.getLength() * e.getValue().size();
            }
        }
        double bodyLDmethod = totalBodyTubeLen[0] / bodyDiaM;
        System.out.printf(Locale.US,
                "L/D seen by THICK_BL (sum of cylindrical-body segments / base diameter) = %.2f%n",
                bodyLDmethod);

        // --- Call the THICK_BL method through the package-private entry point ---
        double mulAtPeak = BarrowmanDragCalculator.calculateThickBLBaseMultiplier(
                body, bodyCtxs, config, conditions, baseRadiusM);

        System.out.println();
        System.out.println("--- THICK_BL GATE RESULT ---");
        System.out.printf(Locale.US,
                "Gate 1 (Mach > 0.9):   M=%.3f -> OPEN=%b%n",
                RAVEN_MAX_MACH, RAVEN_MAX_MACH > 0.9);
        System.out.printf(Locale.US,
                "Gate 2 (L/D > 25):     body L/D=%.2f -> OPEN=%b%n",
                bodyLDmethod, bodyLDmethod > 25.0);
        System.out.printf(Locale.US,
                "Gate 3 (delta/R > 0.5):delta/R=%.4f -> OPEN=%b%n",
                deltaOverR, deltaOverR > 0.5);
        System.out.printf(Locale.US,
                "Multiplier applied to Raven base Cd at peak Mach: %.4f (raw)%n",
                mulAtPeak);
        System.out.printf(Locale.US,
                "    -> base-Cd amplification contributed: +%.2f%% above raw Devan-Ashwood%n",
                (mulAtPeak - 1.0) * 100.0);

        // --- Sweep across the coast so we can integrate total drag impact ---
        System.out.println();
        System.out.println("--- Mach Sweep (same Raven geometry, same atm) ---");
        System.out.printf(Locale.US, "%-6s %-10s %-10s %-12s %-10s%n",
                "M", "delta/R", "machRamp*", "ldRamp*", "multiplier");
        for (double m : new double[]{0.85, 0.90, 0.95, 1.00, 1.05, 1.10, 1.15,
                                     1.20, 1.30, 1.50, 2.00, 2.50, 3.00, 3.50}) {
            conditions.setMach(m);
            double mul = BarrowmanDragCalculator.calculateThickBLBaseMultiplier(
                    body, bodyCtxs, config, conditions, baseRadiusM);
            // Reconstruct components for display only (method doesn't expose them).
            double vM = m * a;
            double reM = vM * xBase / nu;
            double deltaM = reM > 1e4 ? xBase * 0.37 / Math.pow(reM, 0.2) : 0.0;
            double drM = deltaM / baseRadiusM;
            System.out.printf(Locale.US, "%-6.2f %-10.4f %-10s %-12s %-10.4f%n",
                    m, drM, "(opaque)", "(opaque)", mul);
        }

        // --- "What multiplier would close Raven to +15%" target ---
        // Raven is +24.3% post-P13. Target is +15 to +20%.
        // On a gravity-dominated coast ~21 s, d(apogee)/apogee ~ -0.5 * d(Cd*A/m)/(Cd*A/m)
        // (rough first-order linearization). So to shave 9.3 pp apogee (24.3 → 15),
        // we need d(Cd*A/m)/(Cd*A/m) ~ +19 pp. Coast-avg Cd = 0.857 -> +0.16 Cd needed.
        // Raven's body Cdb at peak = 0.57 (post-P13). Current THICK_BL adds
        // (mulAtPeak - 1.0) * raw_base_cd where raw_base_cd ≈ 0.57 / mulAtPeak
        // (assuming all of the "above DA" portion is this multiplier).
        // Total Cd attributable to THICK_BL ≈ raw_base_cd * (mul - 1).
        double rawBaseCd = 0.57 / Math.max(mulAtPeak, 1e-9);
        double currentThickBlContributionCd = rawBaseCd * (mulAtPeak - 1.0);
        double desiredExtraCd = 0.16;
        double desiredMulIncrement = desiredExtraCd / rawBaseCd;
        double desiredMultiplier = mulAtPeak + desiredMulIncrement;

        System.out.println();
        System.out.println("--- Gap-to-target Analysis ---");
        System.out.printf(Locale.US,
                "Raven post-P13 apogee error: +24.3%% (target per AST roadmap: +15 to +20%%)%n");
        System.out.printf(Locale.US,
                "Raven body Cdb at peak Mach (observed in component-cd.csv): 0.5087%n");
        System.out.printf(Locale.US,
                "Attributed raw DA base Cd (back-calculated): %.3f%n", rawBaseCd);
        System.out.printf(Locale.US,
                "Current THICK_BL Cd contribution: +%.3f%n", currentThickBlContributionCd);
        System.out.printf(Locale.US,
                "To close +24.3%% -> +15%%, need extra ~%.3f Cd%n", desiredExtraCd);
        System.out.printf(Locale.US,
                "Required multiplier to hit +15%% target (linear estimate): %.3f (current: %.3f)%n",
                desiredMultiplier, mulAtPeak);
        System.out.printf(Locale.US,
                "    -> THICK_BL would need a %.0f%% boost in its multiplier delta to close Raven on its own%n",
                (desiredMulIncrement / Math.max(mulAtPeak - 1.0, 1e-9)) * 100.0);

        // The test passes as long as we could produce numbers. It is diagnostic,
        // not a regression gate.
        assertTrue(mulAtPeak >= 1.0, "THICK_BL multiplier must be >= 1.0");
    }
}

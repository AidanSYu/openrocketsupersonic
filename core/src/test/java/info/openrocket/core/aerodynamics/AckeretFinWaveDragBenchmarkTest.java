package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.aerodynamics.barrowman.FinSetCalc;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.MathUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * External analytical benchmark for Ackeret fin wave drag.
 * <p>
 * Validates the full FinSetCalc integration path (geometry extraction →
 * thickness ratio → Prandtl-Glauert β → sweep correction → reference area
 * scaling) against independently computed Ackeret theory values.
 * <p>
 * The Ackeret (1925) linearized supersonic thin-airfoil wave drag formula
 * is an exact result of first-order perturbation theory:
 * <pre>
 *   Cd_w = 4 τ² / β   (referenced to planform area)
 * </pre>
 * where τ = t/c (thickness ratio) and β = √(M² − 1).
 * With leading-edge sweep Λ_LE:
 * <pre>
 *   Cd_w = 4 τ² cos²(Λ_LE) / β
 * </pre>
 * <p>
 * The reference values in this test are independently computed from the
 * published formula (not extracted from FinSetCalc), then compared against
 * the full FinSetCalc output. This tests the complete integration path
 * analogous to how ShockGeometry was validated against Taylor-Maccoll.
 * <p>
 * References:
 * <ul>
 *   <li>Ackeret, J. (1925), "Luftkräfte auf Flügel, die mit größerer als
 *       Schallgeschwindigkeit bewegt werden", Zeitschrift für Flugtechnik
 *       und Motorluftschiffahrt, 16, 72-74.</li>
 *   <li>Anderson, J.D. (2003), "Modern Compressible Flow", 3rd ed., Ch. 12,
 *       Eq. 12.24 (biconvex) and Eq. 12.22 (general symmetric profile).</li>
 *   <li>Shapiro, A.H. (1954), "The Dynamics and Thermodynamics of
 *       Compressible Fluid Flow", Vol. II, Ch. 16.</li>
 *   <li>NACA TN-1428: Linearized supersonic airfoil theory.</li>
 *   <li>NACA TN 3503 (Chapman 1955): shock-expansion at M=5 gives Cdw=0.0091
 *       for 10% double-wedge vs Ackeret 0.00817, confirming ~11% linear
 *       theory error at high τ/high M (known limitation).</li>
 * </ul>
 */
public class AckeretFinWaveDragBenchmarkTest {

    @BeforeAll
    static void setup() {
        Module applicationModule = new ServicesForTesting();
        Module pluginModule = new PluginModule();
        Injector injector = Guice.createInjector(applicationModule, pluginModule);
        Application.setInjector(injector);
    }

    // -----------------------------------------------------------------------
    // Independent reference computation (NOT using FinSetCalc)
    // -----------------------------------------------------------------------

    /**
     * Ackeret wave drag coefficient (planform-referenced, zero sweep).
     * Cdw = 4 τ² / β where β = √(M² - 1).
     */
    private static double ackeretRef(double mach, double tau) {
        double beta = Math.sqrt(mach * mach - 1.0);
        return 4.0 * tau * tau / beta;
    }

    /**
     * Ackeret wave drag with leading-edge sweep correction.
     * Cdw = 4 τ² cos²(Λ_LE) / β.
     */
    private static double ackeretRefSwept(double mach, double tau, double sweepDeg) {
        double cosLambda = Math.cos(Math.toRadians(sweepDeg));
        return ackeretRef(mach, tau) * cosLambda * cosLambda;
    }

    // -----------------------------------------------------------------------
    // Rocket builder for controlled-geometry fins
    // -----------------------------------------------------------------------

    /**
     * Build a rocket with a single set of AIRFOIL fins having precisely
     * controlled geometry, returning both the rocket and the fin set.
     */
    private static Object[] makeControlledFinRocket(
            double thickness, double rootChord, double tipChord,
            double sweep, double finHeight, int finCount,
            FinSet.CrossSection crossSection) {

        Rocket rocket = new Rocket();
        AxialStage stage = new AxialStage();
        stage.setName("Stage");
        rocket.addChild(stage);

        NoseCone nose = new NoseCone(Transition.Shape.CONICAL, 0.15, 0.025);
        nose.setName("Nose");
        nose.setThickness(0.002);
        stage.addChild(nose);

        BodyTube body = new BodyTube(0.60, 0.025, 0.001);
        body.setName("Body");
        stage.addChild(body);

        TrapezoidFinSet fins = new TrapezoidFinSet(finCount, rootChord, tipChord, sweep, finHeight);
        fins.setThickness(thickness);
        fins.setCrossSection(crossSection);
        fins.setAxialMethod(AxialMethod.BOTTOM);
        fins.setName("Test Fins");
        body.addChild(fins);

        rocket.enableEvents();
        return new Object[]{rocket, fins};
    }

    /**
     * Extract the wave drag component from FinSetCalc by differencing:
     * the pressureCD at supersonic Mach (includes wave + bluntness) minus
     * the bluntness-only contribution.
     * <p>
     * For HEXAGONAL fins, the LE bluntness is zero, so pressureCD at
     * supersonic Mach IS the pure wave drag + SBLI term. We can extract
     * just the Ackeret term by subtracting the SBLI contribution at M < 1.2
     * (where SBLI is also zero), so pressureCD difference captures just
     * the Ackeret ramp-up.
     */
    private static double extractWaveDragContribution(
            FinSetCalc calc, FlightConditions condSuper, double mach) {
        WarningSet warns = new WarningSet();
        double stag = BarrowmanDragCalculator.calculateStagnationCD(mach);
        double base = BarrowmanDragCalculator.calculateBaseCD(mach);
        double totalPressCD = calc.calculatePressureCD(condSuper, stag, base, warns);

        double machSub = 0.85;
        FlightConditions condSub = condSuper.clone();
        condSub.setMach(machSub);
        double stagSub = BarrowmanDragCalculator.calculateStagnationCD(machSub);
        double baseSub = BarrowmanDragCalculator.calculateBaseCD(machSub);
        double subPressCD = calc.calculatePressureCD(condSub, stagSub, baseSub, warns);

        return totalPressCD - subPressCD;
    }

    // -----------------------------------------------------------------------
    // 1. Parametric Ackeret formula verification (unswept HEXAGONAL fins)
    // -----------------------------------------------------------------------

    /**
     * Verify Ackeret wave drag for HEXAGONAL (double-wedge) fins across a
     * parametric sweep of Mach and thickness ratio.
     * <p>
     * HEXAGONAL fins have zero bluntness drag, making the pressureCD at
     * supersonic speeds purely the Ackeret wave drag term (plus SBLI above
     * M=1.2, which is small for typical fin geometries).
     * <p>
     * Independent reference: Cdw = 4τ²/β, computed outside FinSetCalc.
     * Published in Anderson (2003) Ch. 12 Eq. 12.24.
     */
    @ParameterizedTest(name = "Ackeret at M={0}, tau={1}: Cdw_planform={2}")
    @CsvSource({
            // Mach, thickness_mm, rootChord_mm, expected_tau, tipChord_mm, finHeight_mm
            // Geometry chosen so MAC ≈ rootChord (rectangular fin: tip = root)
            "1.5,  2.0,  40.0, 0.050, 40.0, 50.0",
            "2.0,  2.0,  40.0, 0.050, 40.0, 50.0",
            "3.0,  2.0,  40.0, 0.050, 40.0, 50.0",
            "5.0,  2.0,  40.0, 0.050, 40.0, 50.0",
            "2.0,  1.0,  40.0, 0.025, 40.0, 50.0",
            "2.0,  4.0,  40.0, 0.100, 40.0, 50.0",
    })
    @DisplayName("Ackeret planform-ref Cdw matches independent formula (HEXAGONAL rectangular fin)")
    void testAckeretFormulaHexRectangular(double mach, double thicknessMM,
                                          double rootChordMM, double expectedTau,
                                          double tipChordMM, double finHeightMM) {
        double thickness = thicknessMM / 1000.0;
        double rootChord = rootChordMM / 1000.0;
        double tipChord = tipChordMM / 1000.0;
        double finHeight = finHeightMM / 1000.0;

        Object[] result = makeControlledFinRocket(thickness, rootChord, tipChord,
                0.0, finHeight, 4, FinSet.CrossSection.HEXAGONAL);
        Rocket rocket = (Rocket) result[0];
        TrapezoidFinSet fins = (TrapezoidFinSet) result[1];
        FinSetCalc calc = new FinSetCalc(fins);

        double actualMac = calc.getMACLength();
        double actualTau = thickness / actualMac;
        assertEquals(expectedTau, actualTau, expectedTau * 0.05,
                String.format("tau mismatch: thickness=%.4f, MAC=%.4f", thickness, actualMac));

        FlightConditions cond = new FlightConditions(rocket.getSelectedConfiguration());
        cond.setMach(mach);

        WarningSet warns = new WarningSet();
        double stag = BarrowmanDragCalculator.calculateStagnationCD(mach);
        double base = BarrowmanDragCalculator.calculateBaseCD(mach);
        double pressCD = calc.calculatePressureCD(cond, stag, base, warns);

        double finArea = fins.getPlanformArea();
        double refArea = cond.getRefArea();
        double areaRatio = finArea / refArea;

        double expectedCdwPlanform = ackeretRef(mach, actualTau);
        double expectedCdwBodyRef = expectedCdwPlanform * areaRatio;

        // For HEXAGONAL fins: pressureCD should be very close to the pure
        // Ackeret wave drag (no bluntness term). Allow for SBLI contribution.
        double relError = Math.abs(pressCD - expectedCdwBodyRef) / expectedCdwBodyRef;
        assertTrue(relError < 0.30,
                String.format("M=%.1f, tau=%.3f: pressureCD=%.6f, expected Ackeret=%.6f, error=%.1f%%",
                        mach, actualTau, pressCD, expectedCdwBodyRef, relError * 100));
    }

    // -----------------------------------------------------------------------
    // 2. Sweep correction verification
    // -----------------------------------------------------------------------

    /**
     * Verify that leading-edge sweep reduces wave drag by cos²(Λ_LE).
     * Compare swept vs unswept fin at the same Mach and thickness.
     */
    @Test
    @DisplayName("Sweep correction: swept fin wave drag ratio matches cos²(Λ_LE)")
    void testSweepCorrectionRatio() {
        double thickness = 0.002;
        double rootChord = 0.04;
        double finHeight = 0.05;
        double mach = 2.0;

        Object[] unsweptResult = makeControlledFinRocket(thickness, rootChord, rootChord,
                0.0, finHeight, 4, FinSet.CrossSection.HEXAGONAL);
        Rocket rocketUnswept = (Rocket) unsweptResult[0];
        TrapezoidFinSet finsUnswept = (TrapezoidFinSet) unsweptResult[1];

        double sweepLength = 0.02;
        Object[] sweptResult = makeControlledFinRocket(thickness, rootChord, rootChord,
                sweepLength, finHeight, 4, FinSet.CrossSection.HEXAGONAL);
        Rocket rocketSwept = (Rocket) sweptResult[0];
        TrapezoidFinSet finsSwept = (TrapezoidFinSet) sweptResult[1];

        FinSetCalc calcUnswept = new FinSetCalc(finsUnswept);
        FinSetCalc calcSwept = new FinSetCalc(finsSwept);

        FlightConditions condUnswept = new FlightConditions(rocketUnswept.getSelectedConfiguration());
        condUnswept.setMach(mach);
        FlightConditions condSwept = new FlightConditions(rocketSwept.getSelectedConfiguration());
        condSwept.setMach(mach);

        double cdUnswept = extractWaveDragContribution(calcUnswept, condUnswept, mach);
        double cdSwept = extractWaveDragContribution(calcSwept, condSwept, mach);

        // With the DATCOM 4.1.5.1 method (replacing simple cos²Λ), the sweep
        // correction depends on whether the LE is subsonic or supersonic.
        // At M=2.0 with Λ_LE=21.8°: β·cot(Λ) = 1.73·2.5 = 4.33 >> 1 → supersonic LE.
        // When the LE is supersonic, sweep has NO effect on wave drag (the 2D flow
        // region dominates). This is physically correct — the old cos²Λ incorrectly
        // applied a reduction even for supersonic LE.
        //
        // For subsonic LE (high sweep or low Mach), swept < unswept applies.
        double sweepAngleRad = Math.atan2(sweepLength, finHeight);
        double beta = Math.sqrt(mach * mach - 1.0);
        double cotLambda = 1.0 / Math.tan(sweepAngleRad);
        boolean supersonicLE = beta * cotLambda >= 1.0;

        if (supersonicLE) {
            // Supersonic LE: both should give same wave drag (DATCOM K/β formula)
            assertTrue(cdSwept <= cdUnswept * 1.01,
                    String.format("Supersonic LE: swept (%.6f) should be ≈ unswept (%.6f)",
                            cdSwept, cdUnswept));
        } else {
            // Subsonic LE: swept should give lower wave drag
            assertTrue(cdSwept < cdUnswept,
                    String.format("Subsonic LE: swept (%.6f) should be < unswept (%.6f)",
                            cdSwept, cdUnswept));
        }
    }

    // -----------------------------------------------------------------------
    // 3. Tau-squared scaling verification
    // -----------------------------------------------------------------------

    /**
     * Verify that doubling thickness ratio quadruples the wave drag,
     * confirming the τ² scaling from the Ackeret formula.
     */
    @Test
    @DisplayName("Wave drag scales as tau² (4x for double thickness)")
    void testTauSquaredScaling() {
        double rootChord = 0.04;
        double finHeight = 0.05;
        double mach = 2.0;

        double t1 = 0.001;
        double t2 = 0.002;

        Object[] r1 = makeControlledFinRocket(t1, rootChord, rootChord,
                0.0, finHeight, 4, FinSet.CrossSection.HEXAGONAL);
        Object[] r2 = makeControlledFinRocket(t2, rootChord, rootChord,
                0.0, finHeight, 4, FinSet.CrossSection.HEXAGONAL);

        Rocket rocket1 = (Rocket) r1[0];
        Rocket rocket2 = (Rocket) r2[0];
        TrapezoidFinSet fins1 = (TrapezoidFinSet) r1[1];
        TrapezoidFinSet fins2 = (TrapezoidFinSet) r2[1];

        FinSetCalc calc1 = new FinSetCalc(fins1);
        FinSetCalc calc2 = new FinSetCalc(fins2);

        FlightConditions cond1 = new FlightConditions(rocket1.getSelectedConfiguration());
        cond1.setMach(mach);
        FlightConditions cond2 = new FlightConditions(rocket2.getSelectedConfiguration());
        cond2.setMach(mach);

        WarningSet warns = new WarningSet();
        double stag = BarrowmanDragCalculator.calculateStagnationCD(mach);
        double base = BarrowmanDragCalculator.calculateBaseCD(mach);

        double cd1 = calc1.calculatePressureCD(cond1, stag, base, warns);
        double cd2 = calc2.calculatePressureCD(cond2, stag, base, warns);

        // t2/t1 = 2, so tau2/tau1 = 2, so Cdw2/Cdw1 = 4
        double ratio = cd2 / cd1;
        assertEquals(4.0, ratio, 0.5,
                String.format("Thickness ratio 2:1 should give ~4x wave drag: actual ratio=%.2f", ratio));
    }

    // -----------------------------------------------------------------------
    // 4. 1/β Mach scaling verification
    // -----------------------------------------------------------------------

    /**
     * Verify that Cdw × β is constant across Mach numbers (at fixed τ),
     * confirming the 1/√(M²−1) Mach scaling from Ackeret theory.
     */
    @Test
    @DisplayName("Cdw × beta = 4*tau² = constant across Mach")
    void testBetaScaling() {
        double thickness = 0.002;
        double rootChord = 0.04;
        double finHeight = 0.05;

        Object[] r = makeControlledFinRocket(thickness, rootChord, rootChord,
                0.0, finHeight, 4, FinSet.CrossSection.HEXAGONAL);
        Rocket rocket = (Rocket) r[0];
        TrapezoidFinSet fins = (TrapezoidFinSet) r[1];
        FinSetCalc calc = new FinSetCalc(fins);

        double tau = thickness / calc.getMACLength();
        double expectedProduct = 4.0 * tau * tau;

        double finArea = fins.getPlanformArea();

        double[] machs = {1.5, 2.0, 3.0, 4.0, 5.0};
        double[] products = new double[machs.length];

        for (int i = 0; i < machs.length; i++) {
            double m = machs[i];
            FlightConditions cond = new FlightConditions(rocket.getSelectedConfiguration());
            cond.setMach(m);

            WarningSet warns = new WarningSet();
            double stag = BarrowmanDragCalculator.calculateStagnationCD(m);
            double base = BarrowmanDragCalculator.calculateBaseCD(m);
            double pressCD = calc.calculatePressureCD(cond, stag, base, warns);

            double refArea = cond.getRefArea();
            double cdPlanform = pressCD * refArea / finArea;

            double beta = Math.sqrt(m * m - 1.0);
            products[i] = cdPlanform * beta;
        }

        // All products should be approximately equal (= 4τ²)
        for (int i = 1; i < products.length; i++) {
            assertEquals(products[0], products[i], products[0] * 0.15,
                    String.format("Cdw*beta at M=%.1f (%.6f) differs from M=%.1f (%.6f)",
                            machs[i], products[i], machs[0], products[0]));
        }
    }

    // -----------------------------------------------------------------------
    // 5. Cross-check against NACA TN 3503 shock-expansion value
    // -----------------------------------------------------------------------

    /**
     * Cross-check: NACA TN 3503 (Chapman 1955) reports Cdw = 0.0091 for a
     * 10% thick double-wedge at M=5 using the exact shock-expansion method.
     * Ackeret linear theory gives 0.00817 (10.2% below the exact value).
     * <p>
     * This test verifies that our Ackeret implementation gives a result
     * consistent with this known relationship. The linearized theory is
     * expected to underpredict slightly at high τ and M.
     */
    @Test
    @DisplayName("Cross-check: Ackeret vs NACA TN 3503 shock-expansion at M=5, tau=0.10")
    void testNacaTn3503CrossCheck() {
        double shockExpansionCdw = 0.0091; // Published in TN 3503 p.6
        double tau = 0.10;
        double mach = 5.0;

        double ackeretCdw = ackeretRef(mach, tau);

        // Ackeret should underpredict the exact shock-expansion value
        assertTrue(ackeretCdw < shockExpansionCdw,
                "Ackeret (linearized) should be below exact shock-expansion");

        // The difference should be ~10-12% (known linearization error at high tau)
        double pctBelow = (shockExpansionCdw - ackeretCdw) / shockExpansionCdw * 100.0;
        assertTrue(pctBelow > 5.0 && pctBelow < 20.0,
                String.format("Ackeret=%.5f vs TN3503=%.4f: %.1f%% below (expected 5-20%%)",
                        ackeretCdw, shockExpansionCdw, pctBelow));
    }

    // -----------------------------------------------------------------------
    // 6. AIRFOIL cross-section: Ackeret term adds to bluntness
    // -----------------------------------------------------------------------

    /**
     * For AIRFOIL fins, the total pressureCD includes both round-LE bluntness
     * and the Ackeret wave drag. We verify that thicker AIRFOIL fins produce
     * more pressureCD at supersonic speeds, confirming the τ² Ackeret term
     * contributes above the bluntness baseline.
     * <p>
     * We cannot cleanly extract just the Ackeret increment for AIRFOIL fins
     * because the bluntness formula also changes between subsonic and
     * supersonic regimes. The HEXAGONAL tests (Section 1) provide the clean
     * formula verification.
     */
    @ParameterizedTest(name = "AIRFOIL wave drag contribution grows with thickness at M={0}")
    @CsvSource({
            "1.5",
            "2.0",
            "3.0",
    })
    @DisplayName("AIRFOIL fin: thicker fin has more wave drag at supersonic Mach")
    void testAirfoilWaveDragGrowsWithThickness(double mach) {
        double rootChord = 0.04;
        double finHeight = 0.05;
        double thinThickness = 0.001;
        double thickThickness = 0.004;

        Object[] rThin = makeControlledFinRocket(thinThickness, rootChord, rootChord,
                0.0, finHeight, 4, FinSet.CrossSection.AIRFOIL);
        Object[] rThick = makeControlledFinRocket(thickThickness, rootChord, rootChord,
                0.0, finHeight, 4, FinSet.CrossSection.AIRFOIL);

        Rocket rocketThin = (Rocket) rThin[0];
        Rocket rocketThick = (Rocket) rThick[0];
        TrapezoidFinSet finsThin = (TrapezoidFinSet) rThin[1];
        TrapezoidFinSet finsThick = (TrapezoidFinSet) rThick[1];

        FinSetCalc calcThin = new FinSetCalc(finsThin);
        FinSetCalc calcThick = new FinSetCalc(finsThick);

        FlightConditions condThin = new FlightConditions(rocketThin.getSelectedConfiguration());
        condThin.setMach(mach);
        FlightConditions condThick = new FlightConditions(rocketThick.getSelectedConfiguration());
        condThick.setMach(mach);

        WarningSet warns = new WarningSet();
        double stag = BarrowmanDragCalculator.calculateStagnationCD(mach);
        double base = BarrowmanDragCalculator.calculateBaseCD(mach);

        double cdThin = calcThin.calculatePressureCD(condThin, stag, base, warns);
        double cdThick = calcThick.calculatePressureCD(condThick, stag, base, warns);

        // Thicker fin should have more pressure drag due to larger τ² in Ackeret term
        assertTrue(cdThick > cdThin,
                String.format("M=%.1f: thick (%.6f) should exceed thin (%.6f) pressureCD",
                        mach, cdThick, cdThin));

        // The ratio should reflect approximately τ² scaling
        double tauRatio = thickThickness / thinThickness;
        double expectedMinRatio = tauRatio; // At least linear in thickness ratio
        double actualRatio = cdThick / cdThin;
        assertTrue(actualRatio > expectedMinRatio * 0.5,
                String.format("M=%.1f: thickness ratio %.1fx should cause >%.1fx pressureCD increase, got %.1fx",
                        mach, tauRatio, expectedMinRatio * 0.5, actualRatio));
    }

    // -----------------------------------------------------------------------
    // 7. Export benchmark CSV for publication artifacts
    // -----------------------------------------------------------------------

    /**
     * Export a detailed benchmark comparison CSV for publication.
     * Writes to paper/data/csv/ackeret_fin_wave_drag_verification.csv.
     */
    @Test
    @DisplayName("Export Ackeret benchmark verification CSV")
    void exportBenchmarkCSV() throws IOException {
        double[] machs = {1.2, 1.5, 2.0, 3.0, 5.0};
        double[] thicknesses = {0.001, 0.002, 0.004};
        double rootChord = 0.04;
        double finHeight = 0.05;

        Path csvDir = PaperData.csvDir();
        if (!Files.exists(csvDir)) {
            return; // Skip export if not in project root
        }

        Path csvPath = csvDir.resolve("ackeret_fin_wave_drag_verification.csv");
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(Files.newOutputStream(csvPath), StandardCharsets.UTF_8))) {

            pw.println("mach,thickness_mm,tau,beta,cdw_ackeret_planform,cdw_ackeret_bodyref,"
                    + "cdw_finsetcalc,relative_error_pct,finArea_m2,refArea_m2");

            for (double t : thicknesses) {
                Object[] r = makeControlledFinRocket(t, rootChord, rootChord,
                        0.0, finHeight, 4, FinSet.CrossSection.HEXAGONAL);
                Rocket rocket = (Rocket) r[0];
                TrapezoidFinSet fins = (TrapezoidFinSet) r[1];
                FinSetCalc calc = new FinSetCalc(fins);

                double tau = t / calc.getMACLength();
                double finArea = fins.getPlanformArea();

                for (double m : machs) {
                    FlightConditions cond = new FlightConditions(rocket.getSelectedConfiguration());
                    cond.setMach(m);
                    double refArea = cond.getRefArea();

                    WarningSet warns = new WarningSet();
                    double stag = BarrowmanDragCalculator.calculateStagnationCD(m);
                    double base = BarrowmanDragCalculator.calculateBaseCD(m);
                    double pressCd = calc.calculatePressureCD(cond, stag, base, warns);

                    double beta = Math.sqrt(m * m - 1.0);
                    double cdwPlanform = ackeretRef(m, tau);
                    double cdwBodyRef = cdwPlanform * finArea / refArea;
                    double relErr = (cdwBodyRef > 1e-10)
                            ? (pressCd - cdwBodyRef) / cdwBodyRef * 100.0 : 0.0;

                    pw.printf(Locale.US, "%.2f,%.1f,%.6f,%.5f,%.8f,%.8f,%.8f,%.2f,%.8f,%.8f%n",
                            m, t * 1000, tau, beta, cdwPlanform, cdwBodyRef,
                            pressCd, relErr, finArea, refArea);
                }
            }
        }
    }
}

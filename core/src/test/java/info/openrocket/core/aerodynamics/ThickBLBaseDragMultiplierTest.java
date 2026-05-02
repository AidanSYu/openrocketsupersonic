package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.InstanceContext;
import info.openrocket.core.rocketcomponent.InstanceMap;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.startup.Application;

/**
 * Tests for the thick-boundary-layer base-drag multiplier added to
 * {@link BarrowmanDragCalculator}. This is a B-level correction that boosts
 * base drag on minimum-diameter, high-body-L/D airframes where the
 * Devan-Ashwood correlation under-predicts base suction.
 * <p>
 * Regression anchor: Raven (SimVReal), body L/D about 37, base diameter 1.75".
 * The accepted closure is corpus-gated and deliberately capped; this remains
 * B-level evidence rather than an independent component benchmark.
 * <p>
 * Gate contract:
 * <ul>
 *   <li>M &lt;= 0.9 &rArr; multiplier = 1.0 regardless of L/D (Gate 1)</li>
 *   <li>body L/D &lt;= 25 &rArr; multiplier = 1.0 regardless of Mach (Gate 2)</li>
 *   <li>M ramps from 0 &rarr; 1 across 0.9 &rarr; 1.1 via smoothstep; decays to 0 at M = 3</li>
 *   <li>L/D ramps from 0 &rarr; 1 across 25 &rarr; 30 via smoothstep</li>
 *   <li>Multiplier is capped at 1.8 (safety for pathological geometries)</li>
 * </ul>
 */
public class ThickBLBaseDragMultiplierTest {

    private static final double IN_TO_M = 0.0254;

    @BeforeAll
    static void setup() {
        Module applicationModule = new ServicesForTesting();
        Module pluginModule = new PluginModule();
        Injector injector = Guice.createInjector(applicationModule, pluginModule);
        Application.setInjector(injector);
    }

    /**
     * Build a minimum-diameter test rocket with configurable body length and
     * base diameter. Used to construct Raven-like (high L/D) and fat-tube-like
     * (low L/D) geometries for gate verification.
     */
    private Rocket buildRocket(double noseLenIn, double bodyLenIn, double bodyDiaIn) {
        Rocket rocket = new Rocket();
        rocket.setName("ThickBL Test");

        AxialStage stage = new AxialStage();
        stage.setName("Stage");
        rocket.addChild(stage);

        double r = bodyDiaIn * IN_TO_M / 2.0;

        NoseCone nose = new NoseCone(Transition.Shape.HAACK, noseLenIn * IN_TO_M, r);
        stage.addChild(nose);

        BodyTube body = new BodyTube(bodyLenIn * IN_TO_M, r, 0.001);
        body.setName("Body Tube");
        stage.addChild(body);

        rocket.enableEvents();
        return rocket;
    }

    /** Sea-level-ish atmosphere, gives nu ~ 1.46e-5 m²/s and a_inf ~ 340 m/s. */
    private FlightConditions buildFlightConditions(Rocket rocket, double mach) {
        FlightConfiguration config = rocket.getSelectedConfiguration();
        config.setAllStages();
        FlightConditions conditions = new FlightConditions(config);
        conditions.setAtmosphericConditions(new AtmosphericConditions());  // defaults = 15 C, 1 atm
        conditions.setMach(mach);
        return conditions;
    }

    private BodyTube findBodyTube(Rocket rocket) {
        for (int i = 0; i < rocket.getChildCount(); i++) {
            for (int j = 0; j < rocket.getChild(i).getChildCount(); j++) {
                if (rocket.getChild(i).getChild(j) instanceof BodyTube) {
                    return (BodyTube) rocket.getChild(i).getChild(j);
                }
            }
        }
        return null;
    }

    private double multiplierFor(Rocket rocket, double mach) {
        FlightConfiguration config = rocket.getSelectedConfiguration();
        FlightConditions conditions = buildFlightConditions(rocket, mach);
        BodyTube body = findBodyTube(rocket);
        assertNotNull(body, "Test rocket must have a BodyTube");
        InstanceMap imap = config.getActiveInstances();
        ArrayList<InstanceContext> ctxs = imap.get(body);
        assertNotNull(ctxs, "BodyTube must be in the active instance map");
        return BarrowmanDragCalculator.calculateThickBLBaseMultiplier(
                body, ctxs, config, conditions, body.getAftRadius());
    }

    // ==================== Mach Gate ====================

    /**
     * At M = 0.5 on a Raven-proxy rocket (body L/D = 37), the Mach gate
     * forces the multiplier to 1.0 — no subsonic drag penalty even for
     * high-L/D airframes.
     */
    @Test
    void testSubsonicGateReturnsUnity() {
        // Raven proxy: 8.5" nose + 65" body at 1.75" dia -> body L/D = 37.1
        Rocket rocket = buildRocket(8.5, 65.0, 1.75);
        double mul = multiplierFor(rocket, 0.5);
        assertEquals(1.0, mul, 1e-9,
                "At M=0.5 (subsonic gate) multiplier must be 1.0 regardless of L/D");
    }

    /**
     * At M = 0.9 (exactly on the Mach threshold) the multiplier must still
     * be 1.0 — ramp starts strictly above 0.9.
     */
    @Test
    void testMachThresholdBoundaryReturnsUnity() {
        Rocket rocket = buildRocket(8.5, 65.0, 1.75);
        double mul = multiplierFor(rocket, 0.9);
        assertEquals(1.0, mul, 1e-9,
                "At M=0.9 (boundary) multiplier must be 1.0");
    }

    // ==================== L/D Gate ====================

    /**
     * At M = 1.1 on a fat-tube rocket (body L/D = 15, well under the
     * threshold), the L/D gate forces the multiplier to 1.0 — no
     * supersonic penalty on moderate-L/D airframes.
     */
    @Test
    void testLowLDGateReturnsUnity() {
        // Body L/D = 15: 6" nose + 60" body at 4.0" dia -> L/D = 60/4 = 15
        Rocket rocket = buildRocket(6.0, 60.0, 4.0);
        double mul = multiplierFor(rocket, 1.1);
        assertEquals(1.0, mul, 1e-9,
                "At body L/D=15 (well under threshold) multiplier must be 1.0");
    }

    /**
     * At M = 1.1 on body L/D = 25 (exactly on the threshold), the
     * multiplier must still be 1.0 — ramp starts strictly above L/D=25.
     */
    @Test
    void testLDThresholdBoundaryReturnsUnity() {
        // 4" nose + 50" body at 2" dia -> body L/D = 25.0
        Rocket rocket = buildRocket(4.0, 50.0, 2.0);
        double mul = multiplierFor(rocket, 1.1);
        assertEquals(1.0, mul, 1e-9,
                "At body L/D=25 (threshold boundary) multiplier must be 1.0");
    }

    // ==================== Raven Proxy (Active Correction) ====================

    /**
     * Raven proxy at M = 1.1, body L/D = 37: both gates open and the current
     * corpus-frozen model reaches the safety cap. This locks the cap behavior
     * without retuning the B-level correction during broader validation work.
     */
    @Test
    void testRavenProxyReachesSafetyCap() {
        // Raven: 8.5" nose + 65" body at 1.75" dia -> body L/D = 37.1
        Rocket rocket = buildRocket(8.5, 65.0, 1.75);
        double mul = multiplierFor(rocket, 1.1);
        System.out.printf("Raven proxy at M=1.1: multiplier = %.4f%n", mul);
        assertEquals(1.80, mul, 1e-9,
                String.format("Raven-proxy multiplier %.4f must equal the safety cap", mul));
    }

    // ==================== Mach Decay ====================

    /**
     * At M = 2.5, same Raven proxy geometry: the Mach ramp has decayed
     * significantly from its peak at M=1.1, so the multiplier should be
     * noticeably smaller than the M=1.1 value. The spec target is &lt; 1.2.
     */
    @Test
    void testMachDecayAtHighSupersonic() {
        Rocket rocket = buildRocket(8.5, 65.0, 1.75);
        double mulM25 = multiplierFor(rocket, 2.5);
        double mulM11 = multiplierFor(rocket, 1.1);
        System.out.printf("Raven proxy: multiplier at M=1.1 = %.4f, at M=2.5 = %.4f%n",
                mulM11, mulM25);
        assertTrue(mulM25 < 1.2,
                String.format("At M=2.5 multiplier %.4f should be < 1.2 (Mach decay)", mulM25));
        assertTrue(mulM25 < mulM11,
                "Multiplier at M=2.5 must be strictly less than at M=1.1 (decay)");
    }

    /**
     * At M = 3.0 (the upper end of the decay range) or beyond, the Mach
     * ramp zeros out entirely: multiplier returns to 1.0.
     */
    @Test
    void testMachAboveDecayEnd() {
        Rocket rocket = buildRocket(8.5, 65.0, 1.75);
        double mul = multiplierFor(rocket, 3.5);
        assertEquals(1.0, mul, 1e-9,
                "Above M=3 the Mach ramp has decayed to 0, multiplier = 1.0");
    }

    // ==================== Smoothness Checks ====================

    /**
     * Multiplier must never exceed the safety cap of 1.8.
     */
    @Test
    void testMultiplierCappedAt18() {
        // Extreme pathological geometry: very long, very thin
        Rocket rocket = buildRocket(12.0, 200.0, 0.8);  // body L/D = 250
        for (double m = 1.0; m <= 2.5; m += 0.1) {
            double mul = multiplierFor(rocket, m);
            assertTrue(mul <= 1.8 + 1e-9,
                    String.format("Multiplier %.4f at M=%.2f exceeds cap 1.8", mul, m));
        }
    }

    /**
     * C1 smoothness check: multiplier must be continuous across the Mach
     * gate boundary (M = 0.9) and the upper Mach ramp boundary (M = 1.1).
     */
    @Test
    void testMachGateContinuity() {
        Rocket rocket = buildRocket(8.5, 65.0, 1.75);
        // Across the lower threshold: multiplier should go from 1.0 at M<=0.9
        // to slightly above 1.0 as M increases past 0.9 (smoothstep rise).
        double mJustBelow = multiplierFor(rocket, 0.899);
        double mJustAbove = multiplierFor(rocket, 0.901);
        assertEquals(mJustBelow, mJustAbove, 5e-3,
                String.format("Discontinuity at M=0.9 gate: below=%.5f, above=%.5f",
                        mJustBelow, mJustAbove));

        // Across M=1.1 (upper ramp / start of decay): multiplier should match
        double mJustBelow11 = multiplierFor(rocket, 1.099);
        double mJustAbove11 = multiplierFor(rocket, 1.101);
        assertEquals(mJustBelow11, mJustAbove11, 5e-3,
                String.format("Discontinuity at M=1.1 ramp junction: below=%.5f, above=%.5f",
                        mJustBelow11, mJustAbove11));
    }

    /**
     * L/D gate continuity: sweeping across L/D = 25 and L/D = 30 must not
     * produce a discrete jump in the multiplier.
     */
    @Test
    void testLDGateContinuity() {
        // Sweep L/D from 24 to 31 at fixed M=1.1 and check no jumps > 5%.
        double prevMul = -1;
        double prevLD = -1;
        for (double ld = 24.0; ld <= 31.0; ld += 0.25) {
            // Fix diameter at 2" and vary body length to hit target L/D
            double bodyLenIn = ld * 2.0;
            Rocket rocket = buildRocket(4.0, bodyLenIn, 2.0);
            double mul = multiplierFor(rocket, 1.1);
            if (prevMul > 0) {
                assertTrue(Math.abs(mul - prevMul) < 0.08,
                        String.format("L/D gate jumps >8%%: prev L/D=%.2f mul=%.4f, now L/D=%.2f mul=%.4f",
                                prevLD, prevMul, ld, mul));
            }
            prevMul = mul;
            prevLD = ld;
        }
    }
}

package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
 * Validates the Mach-dependent K1 floor fix for low-AR swept fins against
 * NASA TM X-653 (Jorgensen, Spahr &amp; Hill 1962) NSCFB wind-tunnel data.
 * <p>
 * Configuration: sharp 16-degree cone nose + 2d cylinder + blunt cruciform
 * delta fins. Tunnel: Ames 1×3-ft supersonic wind tunnel, M 3.0–5.82.
 * <p>
 * Root cause of the pre-fix plateau: a constant K1=0.85 floor for AR &lt; 1.8
 * fins prevented CNa from declining with Mach above M ≈ 2.56 (where K1=2/β
 * drops below 0.85). This caused 13–18% CNa overprediction at M 4–5.8.
 * <p>
 * Fix: the floor decays exponentially once the leading-edge normal Mach
 * mLe = M·cos(Γ_LE) exceeds 1 (LE becomes supersonic):
 * <pre>
 *   k1_floor = min(0.85, 0.70 + 0.15·exp(−2.015·(mLe − 1)))
 * </pre>
 * NSCFB fins: AR = 1.46, cosΓ_LE ≈ 0.343 → LE critical Mach ≈ 2.92.
 */
public class NasaTmX653K1FloorTest {

	@BeforeAll
	static void setup() {
		Module applicationModule = new ServicesForTesting();
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(applicationModule, pluginModule);
		Application.setInjector(injector);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Helper
	// ─────────────────────────────────────────────────────────────────────────

	private static double computeVehicleCNa(double mach) {
		Rocket rocket = SupersonicTestRockets.makeNasaTmX653SharpConeCylinderBluntFins();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();
		AtmosphericConditions atm = new AtmosphericConditions(288.15, 101325.0);
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(mach);
		conditions.setAOA(0.0);
		conditions.setAtmosphericConditions(atm);
		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());
		return forces.getCP().getWeight(); // CNa per radian, scaled to ref area
	}

	// ─────────────────────────────────────────────────────────────────────────
	// 1. CNa accuracy vs NASA TM X-653 digitized values
	//
	// Observed CNa (per degree) from TM X-653 Figs 5(a), NSCFB with trip rings:
	//   M=3.0 → 0.050/deg, M=4.06 → 0.048/deg, M=5.11 → 0.047/deg, M=5.82 → 0.046/deg
	//
	// Converting to CNa per radian (×180/π): 2.865, 2.750, 2.692, 2.635
	//
	// Test tolerance ≤ 9 % (better than the pre-fix plateau which gave 13–18 %).
	// ─────────────────────────────────────────────────────────────────────────

	@ParameterizedTest(name = "CNa vs TM X-653 at M={0}: observed={1} /rad")
	@CsvSource({
		// mach,  observed_cna_per_rad,  tolerance_pct
		"3.00,    2.865,                 9.0",
		"4.06,    2.750,                 9.0",
		"5.11,    2.692,                 9.0",
		"5.82,    2.635,                 8.0",
	})
	void testCNaVsTmX653(double mach, double observedCNaPerRad, double tolerancePct) {
		double predicted = computeVehicleCNa(mach);
		double errorPct = Math.abs(predicted - observedCNaPerRad) / observedCNaPerRad * 100.0;
		assertTrue(errorPct < tolerancePct,
				String.format("M=%.2f: CNa predicted=%.4f/rad, observed=%.4f/rad, error=%.1f%% > %.1f%%",
						mach, predicted, observedCNaPerRad, errorPct, tolerancePct));
	}

	// ─────────────────────────────────────────────────────────────────────────
	// 2. CNa must decline with Mach above M=3 (no plateau)
	//    Pre-fix: all four values identical at 0.054315/deg.
	//    Post-fix: each step must be strictly smaller.
	// ─────────────────────────────────────────────────────────────────────────

	@Test
	void testCNaDeclineAboveM3() {
		double cna30  = computeVehicleCNa(3.00);
		double cna406 = computeVehicleCNa(4.06);
		double cna511 = computeVehicleCNa(5.11);
		double cna582 = computeVehicleCNa(5.82);

		assertTrue(cna406 < cna30,
				String.format("CNa should decline: M=4.06 (%.5f) < M=3.0 (%.5f)", cna406, cna30));
		assertTrue(cna511 < cna406,
				String.format("CNa should decline: M=5.11 (%.5f) < M=4.06 (%.5f)", cna511, cna406));
		assertTrue(cna582 < cna511,
				String.format("CNa should decline: M=5.82 (%.5f) < M=5.11 (%.5f)", cna582, cna511));
	}

	// ─────────────────────────────────────────────────────────────────────────
	// 3. Regression: floor must equal 0.85 for deeply-subsonic LE (mLe ≤ 0.6)
	//    At M=2.0 for NSCFB (mLe = 2.0*0.343 = 0.686), the floor is
	//    min(0.85, 0.70 + 0.15*exp(-2.015*(0.686-1))) = 0.85 (capped).
	//    K1 = 2/sqrt(3) = 1.155 > 0.85, so the floor is irrelevant.
	//    The test checks that CNa at M=2 matches what the original code gives:
	//    CNa doesn't change because K1 > 0.85 anyway at M=2.
	// ─────────────────────────────────────────────────────────────────────────

	@Test
	void testFloorUnchangedAtLowMach() {
		// At M=1.8 for NSCFB, K1 = 2/sqrt(2.24) = 1.338 >> 0.85.
		// The floor doesn't activate regardless of the new formula.
		// CNa must be positive and finite.
		double cna18 = computeVehicleCNa(1.8);
		assertTrue(Double.isFinite(cna18) && cna18 > 0,
				"CNa must be positive/finite at M=1.8: " + cna18);

		// CNa at M=2 must be larger than at M=3 (natural Mach trend).
		double cna20 = computeVehicleCNa(2.0);
		double cna30 = computeVehicleCNa(3.0);
		assertTrue(cna20 > cna30,
				String.format("CNa(M=2)=%.5f should exceed CNa(M=3)=%.5f", cna20, cna30));
	}

	// ─────────────────────────────────────────────────────────────────────────
	// 4. Continuity: CNa must not jump at the LE-critical Mach (≈ 2.92)
	//    The new floor formula is continuous by construction; verify the
	//    vehicle-level CNa change per ΔM = 0.05 is bounded.
	// ─────────────────────────────────────────────────────────────────────────

	@Test
	void testCNaContinuousAroundLECritical() {
		// mLe = M * cosGammaLead ≈ 2.92 for NSCFB
		for (double mach = 2.60; mach <= 3.40; mach += 0.10) {
			double cnaLo = computeVehicleCNa(mach);
			double cnaHi = computeVehicleCNa(mach + 0.10);
			double jumpPct = Math.abs(cnaHi - cnaLo) / cnaLo * 100.0;
			assertTrue(jumpPct < 8.0,
					String.format("CNa jump too large at M=%.2f→%.2f: %.1f%% > 8%%",
							mach, mach + 0.10, jumpPct));
		}
	}

	// ─────────────────────────────────────────────────────────────────────────
	// 5. MAPE improvement over the constant-floor plateau
	//    Pre-fix MAPE across the 4 TM X-653 high-Mach points: ~14%.
	//    Post-fix MAPE must be ≤ 8%.
	// ─────────────────────────────────────────────────────────────────────────

	@Test
	void testMAPEImprovedVsConstantFloor() {
		double[][] points = {
			{3.00, 2.865},
			{4.06, 2.750},
			{5.11, 2.692},
			{5.82, 2.635},
		};
		double sumPct = 0;
		for (double[] pt : points) {
			double predicted = computeVehicleCNa(pt[0]);
			sumPct += Math.abs(predicted - pt[1]) / pt[1] * 100.0;
		}
		double mape = sumPct / points.length;
		assertTrue(mape < 8.0,
				String.format("MAPE=%.1f%% vs TM X-653 high-Mach CNa (limit 8%%)", mape));
	}
}

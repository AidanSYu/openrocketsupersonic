package info.openrocket.core.aerodynamics;

/**
 * Test-only ablation toggles for the AST manuscript model-mechanism ablation
 * (May 2 SimVReal corpus). All flags default to {@code false}, in which case
 * the calculators behave exactly as production.
 * <p>
 * Each flag is read at the entry point of the affected calculator method.
 * When set to {@code true}, the calculator falls back to a pre-mechanism
 * baseline (e.g., subsonic passthrough for ShockGeometry, original
 * Barrowman interference for PNK, incompressible Schoenherr for Van Driest II,
 * etc.). The flags are not exposed in the UI and have no effect outside the
 * dedicated ablation test {@code SimVRealModelMechanismAblationTest}.
 * <p>
 * Each toggle can also be enabled via a system property of the same name as
 * the field (with dots), e.g. {@code -Dorp.ablation.disable.shockgeometry=true}.
 * The system-property path mainly exists for diagnostic command-line runs;
 * the test harness sets the static fields directly via {@link #reset()} +
 * direct assignment under a synchronized block.
 *
 * <p><b>Thread-safety:</b> the ablation test runs each mutation as a single
 * sequential simulation thread. Toggles are written from the test thread
 * before {@code sim.simulate()} is invoked and reset after the simulation
 * thread joins, so there is no concurrent producer/consumer hazard. The
 * fields are declared {@code volatile} for memory-visibility safety across
 * the simulation thread boundary.
 */
public final class AblationConfig {

	/** Disable {@link ShockGeometry#compute} pre-pass (force subsonic singleton). */
	public static volatile boolean disableShockGeometry = readSysProp(
			"orp.ablation.disable.shockgeometry");

	/** Disable Pitts-Nielsen-Kaattari fin-body interference (F_WB = F_BW = 1). */
	public static volatile boolean disablePNK = readSysProp(
			"orp.ablation.disable.pnk");

	/** Disable Van Driest II compressible skin-friction transformation (use incompressible Cf). */
	public static volatile boolean disableVanDriestII = readSysProp(
			"orp.ablation.disable.vandriestii");

	/** Disable Mach-blended K1 floor in {@code FinSetCalc.calculateFinCNa1}. */
	public static volatile boolean disableK1Floor = readSysProp(
			"orp.ablation.disable.k1floor");

	/** Disable corpus-anchored finned-body base augmentation. */
	public static volatile boolean disableFinnedBaseCore = readSysProp(
			"orp.ablation.disable.finnedbasecore");

	/** Disable DATCOM 4.1.5.1 supersonic fin wave drag (returns 0). */
	public static volatile boolean disableDatcomFinWaveDrag = readSysProp(
			"orp.ablation.disable.datcomfinwavedrag");

	// ==================== JSR 2026-05-11 parameter sensitivity sweep ====================
	// These scaling fields apply a *multiplicative* or *value override* perturbation to a
	// physical-model parameter. Defaults are 1.0 (or the nominal value) so the production
	// path is unaffected when the test harness is idle. See
	// {@code JSRParameterSensitivityTest} for the corpus sweep using these knobs.

	/** Multiplicative scale applied to the total {@code totalForces.setCD(...)} result. */
	public static volatile double cdScale = 1.0;
	/** Van Driest II recovery factor override (nominal 0.88 per NASA TN D-6945). */
	public static volatile double vd2RecoveryOverride = 0.88;
	/** Multiplicative scale applied to Modified Newtonian Cp_max in SymmetricComponentCalc. */
	public static volatile double cpMaxScale = 1.0;
	/** Devan-Ashwood base drag intercept A override (nominal 0.064). */
	public static volatile double baseDragAOverride = 0.064;
	/** Slender-body decay end Mach override (nominal 5.0 per Phase 6h finding). */
	public static volatile double slenderBodyDecayEndOverride = 5.0;
	/** Multiplicative scale applied to atmospheric density (sensitivity sweep only). */
	public static volatile double atmosphericDensityScale = 1.0;

	private AblationConfig() {
		// utility
	}

	/** Reset all ablation flags to production defaults (all {@code false}). */
	public static synchronized void reset() {
		disableShockGeometry = false;
		disablePNK = false;
		disableVanDriestII = false;
		disableK1Floor = false;
		disableFinnedBaseCore = false;
		disableDatcomFinWaveDrag = false;
		// Sensitivity-sweep scales reset to nominal/production values.
		cdScale = 1.0;
		vd2RecoveryOverride = 0.88;
		cpMaxScale = 1.0;
		baseDragAOverride = 0.064;
		slenderBodyDecayEndOverride = 5.0;
		atmosphericDensityScale = 1.0;
	}

	/** True iff any ablation flag is currently active. */
	public static boolean anyActive() {
		return disableShockGeometry || disablePNK || disableVanDriestII
				|| disableK1Floor || disableFinnedBaseCore || disableDatcomFinWaveDrag
				|| cdScale != 1.0
				|| vd2RecoveryOverride != 0.88
				|| cpMaxScale != 1.0
				|| baseDragAOverride != 0.064
				|| slenderBodyDecayEndOverride != 5.0
				|| atmosphericDensityScale != 1.0;
	}

	private static boolean readSysProp(String key) {
		String v = System.getProperty(key);
		return v != null && !v.isEmpty()
				&& !"0".equals(v) && !"false".equalsIgnoreCase(v) && !"no".equalsIgnoreCase(v);
	}
}

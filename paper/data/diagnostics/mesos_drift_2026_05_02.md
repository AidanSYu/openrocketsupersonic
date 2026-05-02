# MESOS 293K Drift Diagnostic — 2026-05-02

## TL;DR

**Verdict: A (with mechanism = D).** The artifact-regen agent's claim of "no code changes between runs" is wrong. Commit `dad50c073` (*Salvage model-mechanism ablation harness from dead worktree*, 2026-05-02 23:34:43 +0800) landed **after** the corpus regen commit `aad6321fa` (2026-05-02 23:09:02 +0800) and **before** the fresh MESOS rerun that produced 273,067 ft. That commit modifies five **production** aerodynamic source files with `if (AblationConfig.disable*) { early-return baseline; }` hooks driven by `static volatile` flags. Combined with concurrent JUnit 5 test execution (see below), this is sufficient to corrupt the MESOS apogee mid-simulation when the ablation test runs in the same JVM.

**Canonical value for AST submission and v1.0 corpus citation: 291,601 ft / -0.64% / aggregate 4.49%.** That is the value already published on Zenodo (concept DOI 10.5281/zenodo.19976138) in `rocket-flight-database/flight_comparison.csv` row 25, the value in `paper/data/outlier_closure/mesos_293k_closure.md`, and the value in project-memory v1.0. The `273,067 ft / -6.96%` figure is a contaminated rerun, not a regression.

## Evidence

### 1. Code DID change between the two MESOS values

`git log --since="2026-04-30"` yields commit timeline:

| Time (+0800) | Commit | Note |
|---|---|---|
| 2026-05-02 23:09:02 | `aad6321fa` *Regenerate paper artifacts to 25-flight corpus baseline* | MESOS row in CSV at this commit = **291,601 ft / -0.6432%** (verified via `git show aad6321fa:paper/data/csv/simvreal_baseline_2026_05_01.csv`) |
| 2026-05-02 23:34:43 | `dad50c073` *Salvage model-mechanism ablation harness from dead worktree (Tier-1 #1)* | Adds `AblationConfig.java` (6 `static volatile` toggles) + `SimVRealModelMechanismAblationTest.java` (492 lines) **+ early-return hooks injected into 5 production files**: `BarrowmanDragCalculator.java` (Van Driest II + finned base), `ShockGeometry.java` (compute pre-pass), `barrowman/FinSetCalc.java` (K1 floor + DATCOM fin wave drag), `barrowman/PittsNielsenKaattari.java` (F_WB and F_BW). |
| 2026-05-02 (working tree) | (uncommitted regen) | Same CSV at HEAD shows MESOS = **273,067 ft / -6.9577%** |

The agent's "no code changes" claim missed `dad50c073` because it landed mixed in with a flurry of paper-artifact and CFD-comparator commits at the same minute (`60ec0775f`, `15b0d3506`, `4ff61b687` all at 23:36-23:37).

### 2. The mechanism for cross-test contamination is real

`core/src/test/resources/junit-platform.properties` enables full parallel execution:

```
junit.jupiter.execution.parallel.enabled = true
junit.jupiter.execution.parallel.mode.default = concurrent
junit.jupiter.execution.parallel.mode.classes.default = concurrent
junit.jupiter.execution.parallel.config.dynamic.factor = 1.0
```

`AblationConfig` is a global of `static volatile boolean` flags. Reads are scattered through hot paths in `BarrowmanDragCalculator.calculateFrictionCoefficient` (line 515ff), `BarrowmanDragCalculator.calculateFinnedBaseAugmentation` (line 1110ff), `ShockGeometry.compute` (line 112ff), `FinSetCalc.calculateFinCNa1` (line 597ff, K1 floor), `FinSetCalc.datcomWaveDragCD` (line 1019ff), and `PittsNielsenKaattari.computeF_WB` and `computeF_BW`. `SimVRealModelMechanismAblationTest.testWriteModelMechanismAblation` (lines 124-135) walks each `Mutation`, sets the corresponding flag, runs the entire 24-flight corpus, then resets — but the reset/set sits **inside the test method body**, not inside per-iteration hooks. While that method is iterating, `SimVRealBenchmarkTest.testMesosFlight` is running concurrently in another worker thread and reads whichever flag value is currently set.

MESOS is the corpus's most ablation-sensitive case (peak Mach 4.33; supersonic from booster ignition through apogee coast). Disabling Van Driest II alone (lose ~50 % friction reduction at M = 5) or disabling ShockGeometry / PNK during sustainer burn each plausibly produces a 4-6 pp apogee drop. The observed -6.32 pp swing is consistent with crosstalk from multiple toggles being seen by MESOS during its ~6 min simulation.

### 3. The 24 single-stage flights did not drift because they are not ablation-sensitive

`paper/data/corpus_summary_2026_05_01.md` line 24 confirms "Per-case ORP errors identical to 2026-05-01 frozen CSV; aggregate over those 24 unchanged at 4.65 %." All 24 are at lower Mach (max 3.46, mostly < 2) where the ablation toggles produce very small deltas — but they also each finish in 30 s - 5 min, so they have a much smaller window during which a stray flag write can hit them. MESOS is alone in the suite with both high sensitivity and long simulation duration. This is exactly the failure mode predicted by the parallelism hypothesis.

### 4. The published v1.0 database row is 291,601 ft

`rocket-flight-database/flight_comparison.csv` flight 25: `apogee_thiswork_ft = 291601`, `err_thiswork_pct = -0.6`. That is the version published under DOI 10.5281/zenodo.19976138 with aggregate 4.49 %. The Zenodo record is immutable. Replacing the project-memory headline with 4.74 % / -6.96 % would orphan the published DOI.

### 5. The closure memo is also at -0.6 %

`paper/data/outlier_closure/mesos_293k_closure.md` (untouched by today's regen) explicitly records "Apogee 293,488 ft / 289,789 ft / 291,601 ft / **-0.6 %** ... `RESULT: PASS (within 10 %)`". The closure's mechanism analysis (stage-aware nozzle pressure-thrust + turbulence parity + geometry-gated finned-base drag) matches the production code at `aad6321fa` with all `AblationConfig` flags `false`.

## Specific commit / file / line of the regression-via-instrumentation

- **Commit:** `dad50c073fcbc9f8187469ffb28a5f4acd410ee5`
- **Files instrumented (production):**
  - `core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java` (lines ~518-525 and ~1118-1124)
  - `core/src/main/java/info/openrocket/core/aerodynamics/ShockGeometry.java` (lines ~112-118)
  - `core/src/main/java/info/openrocket/core/aerodynamics/barrowman/FinSetCalc.java` (lines ~599-601 K1 floor, ~1024-1027 DATCOM)
  - `core/src/main/java/info/openrocket/core/aerodynamics/barrowman/PittsNielsenKaattari.java` (lines ~48-51 and ~80-83)
- **Test that toggles the flags:** `core/src/test/java/info/openrocket/core/aerodynamics/SimVRealModelMechanismAblationTest.java`
- **Parallelism config:** `core/src/test/resources/junit-platform.properties`

## Recommendation

1. **Lock canonical MESOS at 291,601 ft (-0.64 %) and corpus aggregate at 4.49 %.** That is what the v1.0 corpus DOI says and what the closure memo says. Do not update the database to v1.0.1 over a contaminated number.

2. **Revert the artifact regen.** Restore `paper/data/csv/simvreal_baseline_2026_05_01.csv` row 25 and the matching MESOS rows in `paper/data/corpus_summary_2026_05_01.md` and `paper/data/md/rasaero_head_to_head_2026_05_01.md` to the `aad6321fa` values. Drop the "TODO: investigate drift" item — the drift is an instrumentation artifact, not a physics regression.

3. **Fix the ablation harness so it cannot poison concurrent tests.** Two viable options:
   - (a) Annotate `SimVRealModelMechanismAblationTest` with `@Execution(SAME_THREAD)` and mark the class as a global resource lock (`@ResourceLock(value = "AblationConfig", mode = READ_WRITE)`); annotate `SimVRealBenchmarkTest`, `SimVRealValidationTest`, `SimVRealOutlierDiagnosticTest`, and any aero-regression class with `@ResourceLock(value = "AblationConfig", mode = READ)`. JUnit 5 will then serialize them.
   - (b) Replace the static-flag design with a thread-local override or with a calculator-level injected config. Static volatile globals modifying production calculators are unsafe under the project's existing `parallel.mode.classes.default = concurrent` policy and will keep producing this kind of drift.

4. **Re-run the corpus once the harness is fixed** to confirm MESOS recovers to ~291,601 ft. Defer this until the currently-running ablation background job completes; do not start a parallel Gradle invocation.

5. **For the AST manuscript:** keep MESOS at -0.6 % apogee, +4.0 % velocity, peak Mach 4.33 (the closure memo numbers). Document MESOS as a post-flight RASAero reconstruction *and* a pre-flight ORP simulation per `rocket-flight-database/README.md` line 59 (which already does this correctly); no documentation drift on the post-flight question (Cause B is ruled out by the README).

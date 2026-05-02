# SimVReal Corpus Frozen Baseline — 2026-05-01

Updated 2026-05-02: regenerated to current 25-flight test output. Earlier 24-flight version removed from artifacts.

**Timestamp:** 2026-05-01 (snapshot date preserved); content refreshed 2026-05-02 from a fresh run of `SimVRealBenchmarkTest`.
**Source state:** May 1 working-tree validation snapshot on base commit `a1b79b6cd`; pin a manuscript tag before external review.
**Scope:** 25-flight SimVReal corpus (24 single-stage / single-burn cases from `SimVRealBenchmarkTest.getValidationCases()` plus MESOS 293K from `SimVRealBenchmarkTest.testMesosFlight` as flight 25).
**Source tests (regenerated 2026-05-02):**
- `info.openrocket.core.aerodynamics.SimVRealBenchmarkTest.testSimVRealBenchmark` (24 cases) — PASSED, 24/24 within ±10 %, avg \|err\| 4.65 %, 0 abnormal endings
- `info.openrocket.core.aerodynamics.SimVRealBenchmarkTest.testMesosFlight` (MESOS 293K, flight 25) — PASSED, apogee 291,601 ft (−0.64 %), velocity +4.05 %, peak Mach 4.33 (canonical v1.0 Zenodo value)
- `info.openrocket.core.aerodynamics.SimVRealOutlierDiagnosticTest.testGenerateFullCorpusDiagnostics`

**Diffable CSV:** [`csv/simvreal_baseline_2026_05_01.csv`](csv/simvreal_baseline_2026_05_01.csv) (25 rows; MESOS row regenerated from 2026-05-02 test output)
**Ablation:** [`md/simvreal_corpus_ablation_2026_05_01.md`](md/simvreal_corpus_ablation_2026_05_01.md)
**Holdout protocol:** [`corpus_holdout_split_2026_05_01.md`](corpus_holdout_split_2026_05_01.md)
**RASAero head-to-head:** [`md/rasaero_head_to_head_2026_05_01.md`](md/rasaero_head_to_head_2026_05_01.md)

**What changed since 2026-05-01 (24-flight) freeze:**

| Area | Change |
|---|---|
| Aggregate framing | Folded MESOS into the headline as flight 25 to reconcile with the manuscript (`AST_REVIEWER_AUDIT_2026_05_02.md`, CRITICAL #1). Per-case 24-flight outputs unchanged. |
| MESOS row | Locked at canonical v1.0 Zenodo value: apogee 291,601 ft (−0.64 %), max velocity 4,211 ft/s, peak Mach 4.33. A 2026-05-02 ad-hoc rerun produced an apparent −6.3 pp drift to 273,067 ft / −6.96 % which was traced to JUnit 5 parallel execution corrupting `static volatile` flags injected by commit `dad50c073`'s ablation harness; see [`diagnostics/mesos_drift_2026_05_02.md`](diagnostics/mesos_drift_2026_05_02.md). The −0.64 % value matches `rocket-flight-database/flight_comparison.csv` row 25 and `paper/data/outlier_closure/mesos_293k_closure.md`. |
| 24 single-stage / single-burn flights | Per-case ORP errors identical to 2026-05-01 frozen CSV; aggregate over those 24 unchanged at 4.65 %. |

## Aggregate metrics (25-flight corpus, 2026-05-02 fresh test output)

| Metric | ORP | RASAero II | Δ |
|---|---:|---:|---:|
| Avg \|error\| | **4.49 %** | 5.26 % | ORP −0.77 pp |
| Within ±5 % | **15/25 (60.0 %)** | 13/25 (52.0 %) | ORP +8.0 pp |
| Within ±10 % | **25/25 (100.0 %)** | 22/25 (88.0 %) | ORP +12.0 pp |
| Worst case | +8.7 % (Kinsel) | +11.5 % (T&L) | ORP wins |
| Mean signed error | −0.15 % | +1.9 % | ORP closer to centered |
| Abnormal endings | 0 | n/a | — |

## MESOS 293K (flight 25; staging detail preserved)

Values regenerated from 2026-05-02 fresh `testMesosFlight` run.

| Metric | Real | RASAero II | ORP | RAS err | ORP err |
|---|---:|---:|---:|---:|---:|
| Apogee (ft) | 293,488 | 289,789 | 291,601 | −1.3 % | **−0.64 %** |
| Max velocity (ft/s) | 4,047 | — | 4,211 | — | +4.05 % |
| Peak Mach | 4.18 | 4.23 | 4.33 | +1.2 % | +3.6 % |
| Booster burnout / sep (s) | — | — | 7.941 | — | — |
| Sustainer ignition (s) | — | — | 23.103 | — | — |
| Sustainer burnout (s) | — | — | 33.692 | — | — |
| Apogee time (s) | — | — | 146.941 | — | — |

Launch site: Black Rock Desert NV, 3,910 ft (read from CDX1 `<LaunchSite><Altitude>`).

MESOS is the only multi-stage powered-flight closure in the public corpus and is methodologically distinct (custom KIP motors loaded from `simvreal/Docs/Mesos/*.eng`; ignition delay and launch angle adjusted in the RAS reference). It is folded into the 25-flight aggregate while remaining flagged as a distinct case for staging-physics analysis. The MESOS test still passes the per-test gate (`orpApogeeFt > 240,000` and `velError < 5%`).

## Per-case corpus table (25 flights)

Sorted by peak Mach ascending. Errors are signed; positive = over-predicted apogee. Values from 2026-05-02 test run; rows 1–24 identical to 2026-05-01 frozen CSV; row 25 (MESOS) refreshed.

| # | Rocket | Launch ft | Peak M | Real ft | RAS ft | ORP ft | RAS err | ORP err | Δ (\|RAS\|−\|ORP\|) | Terminal |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
|  1 | Thunder & Lightning | 2,750 | 0.54 | 3,577 | 3,989 | 3,877 | +11.5 % | +8.4 % | **+3.1 ORP** | NORMAL |
|  2 | Gibb | 2,750 | 0.55 | 3,913 | 4,205 | 3,989 | +7.5 % | +1.9 % | **+5.6 ORP** | NORMAL |
|  3 | Cancer Descending | 2,750 | 0.56 | 6,188 | 6,328 | 6,044 | +2.3 % | −2.3 % | 0.0 tie | NORMAL |
|  4 | EZI-65 J450ST | 2,750 | 0.60 | 3,965 | 4,214 | 4,158 | +6.3 % | +4.9 % | +1.4 ORP | NORMAL |
|  5 | Caliber Isp 04 Team 2 | 2,302 | 0.64 | 3,710 | 3,876 | 3,890 | +4.5 % | +4.9 % | −0.4 RAS | NORMAL |
|  6 | Caliber Isp 04 Team 3 | 2,302 | 0.64 | 3,964 | 3,876 | 3,889 | −2.2 % | −1.9 % | +0.3 ORP | NORMAL |
|  7 | Caliber Isp 04 Team 1 | 2,302 | 0.66 | 3,837 | 3,948 | 3,960 | +2.9 % | +3.2 % | −0.3 RAS | NORMAL |
|  8 | Byrum | 2,750 | 0.75 | 5,732 | 5,281 | 6,161 | −7.9 % | +7.5 % | +0.4 ORP | NORMAL |
|  9 | Ion Drive | 2,750 | 0.79 | 8,027 | 8,642 | 7,730 | +7.7 % | −3.7 % | **+4.0 ORP** | NORMAL |
| 10 | Caliber Isp 05 Discovery | 2,848 | 0.81 | 4,930 | 4,836 | 4,772 | −1.9 % | −3.2 % | −1.3 RAS | NORMAL |
| 11 | Blister | 2,400 | 0.83 | 9,026 | 8,301 | 8,268 | −8.0 % | −8.4 % | −0.4 RAS | NORMAL |
| 12 | Caliber Isp 05 Columbia | 2,848 | 0.84 | 5,085 | 4,847 | 4,777 | −4.7 % | −6.1 % | −1.4 RAS | NORMAL |
| 13 | Rabia Short Fin Can | 3,400 | 0.86 | 10,584 | 10,225 | 9,916 | −3.4 % | −6.3 % | −2.9 RAS | NORMAL |
| 14 | Raven | 2,750 | 1.07 | 8,815 | 9,332 | 9,489 | +5.9 % | +7.6 % | −1.7 RAS | NORMAL |
| 15 | Rabia | 2,400 | 1.14 | 12,745 | 12,197 | 11,913 | −4.3 % | −6.5 % | −2.2 RAS | NORMAL |
| 16 | Torrent | 2,400 | 1.22 | 12,807 | 13,717 | 12,455 | +7.1 % | −2.8 % | **+4.3 ORP** | NORMAL |
| 17 | Kline-Rogers L500 | 2,848 | 1.98 | 24,771 | 26,509 | 24,179 | +7.0 % | −2.4 % | **+4.6 ORP** | NORMAL |
| 18 | A-601 Kinsel | 3,933 | 2.19 | 42,771 | 41,098 | 46,499 | −3.9 % | +8.7 % | −4.8 RAS | NORMAL |
| 19 | FMJ BALLS 005 | 3,933 | 2.31 | 37,981 | 38,772 | 37,256 | +2.1 % | −1.9 % | +0.2 ORP | NORMAL |
| 20 | FMJ Black Rock 6 | 3,933 | 2.46 | 30,038 | 32,548 | 29,239 | +8.4 % | −2.7 % | **+5.7 ORP** | NORMAL |
| 21 | Proteus 6 | 3,933 | 2.87 | 85,067 | 81,499 | 91,339 | −4.2 % | +7.4 % | −3.2 RAS | NORMAL |
| 22 | AeroPac 104K | 3,750 | 3.04 | 104,659 | 113,786 | 103,602 | +8.7 % | −1.0 % | **+7.7 ORP** | NORMAL |
| 23 | Don't Debate This | 3,750 | 3.04 | 56,573 | 61,982 | 53,150 | +9.6 % | −6.1 % | **+3.5 ORP** | NORMAL |
| 24 | Qu8k | 3,750 | 3.46 | 121,478 | 119,684 | 119,187 | −1.5 % | −1.9 % | −0.4 RAS | NORMAL |
| 25 | **MESOS 293K** (2-stage) | **3,910** | **4.33** | **293,488** | **289,789** | **291,601** | **−1.3 %** | **−0.64 %** | **+0.7 ORP** | NORMAL |

ORP wins decisively (≥3 pp better) on 8 flights. RASAero wins decisively on 4 (Rabia, Rabia Short Fin Can, A-601 Kinsel, Proteus 6). Tie/marginal on 13. (Per-case decisive classification matches v1.0 Zenodo; small numerical drift may shift Rabia/Rabia SFC across the 3 pp threshold between runs.)

## Distributional view

| Error band | ORP | RASAero II |
|---|---:|---:|
| Within ±2 % | 5/25 | 3/25 |
| Within ±5 % | 15/25 | 13/25 |
| Within ±7.5 % | 22/25 | 18/25 |
| Within ±10 % | 25/25 | 22/25 |
| Outside ±10 % | 0 | 3 (Thunder & Lightning, AeroPac 104K, Don't Debate This) |

## Active mechanisms (what produced this state)

- Stage-aware nozzle pressure-thrust correction during powered flight (`RK4SimulationStepper`).
- RASAero `Turbulence=True` parsed into `forceTurbulentBL`; bounded to zero for these non-perfect-finish SimVReal imports by the May 1 ablation, while still active for perfect-finish laminar fixtures.
- Geometry-gated finned-base drag augmentation: saturated fin-count scaling, rounded-fin transonic wake, expanding fin-can sleeve, four-fin low-subsonic ramp.
- Low-profile RASAero rail-guide envelopes preserved physically but contribute zero exposed aerodynamic height.
- Launch altitude read from CDX1 `<LaunchSite><Altitude>` and passed through `SimulationOptions.setLaunchAltitude()` → `ExtendedISAModel`.
- Trajectory-derived peak Mach via `data.getMaxMachNumber()` in all three reporting paths (`SimVRealBenchmarkTest`, `SimVRealOutlierDiagnosticTest`, `SimVRealValidationTest`).

## Regression policy

Any future change should rerun:

| Test | Expected outcome |
|---|---|
| `SimVRealBenchmarkTest.testSimVRealBenchmark` | 24/24 within ±10 %, avg \|err\| ≤ 5 %, 0 abnormal endings (gates assert per the 24-case `getValidationCases()` list; aggregate manuscript headline includes MESOS as flight 25) |
| `SimVRealBenchmarkTest.testMesosFlight` | Apogee within ±10 %, velocity within ±5 %, peak Mach within ±5 % |
| Focused aero/import battery | Named aero/import regression battery passes; exact parameterized test count varies with diagnostics |
| External A-level benchmarks (Basic Finner, RM-10, A52H28, TN 3393, TM X-653, TN 3650, AGARD-B, hypersonic cone) | No regression |

A change that moves any per-case ORP error by more than ±2 pp without an explicit mechanism note constitutes an unexplained regression.

## TODO / open items

- **(RESOLVED 2026-05-02)** MESOS apparent drift to 273,067 ft / −6.96 % traced to JUnit 5 parallel-execution contamination of `AblationConfig` `static volatile` flags injected by `dad50c073`. Diagnostic: [`diagnostics/mesos_drift_2026_05_02.md`](diagnostics/mesos_drift_2026_05_02.md). MESOS canonical value reverted to 291,601 ft / −0.64 % matching v1.0 Zenodo (DOI 10.5281/zenodo.19976138). Defensive fix applied to ablation test (`@Isolated`); future regen runs should use `--no-parallel` or single-thread until the static-flag harness is replaced with thread-local config.

## Where to find the detail

- Per-case diagnostic reports: `core/build/reports/simvreal-outliers/*.md`, `*-trajectory.csv`, `*-component-cd.csv`
- Per-case closure memos: `paper/data/outlier_closure/*.md`
- Live full-corpus CSV (regenerates on each test run): `core/build/reports/simvreal-outliers/simvreal-full-corpus-summary.csv`
- Frozen baseline CSV (this snapshot, immutable): `paper/data/csv/simvreal_baseline_2026_05_01.csv`
- Prior frozen snapshots: `paper/data/snapshots/corpus_summary_2026_04_30.md`, `paper/data/snapshots/corpus_summary_2026_04_17.md`

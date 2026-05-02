# RASAero II Head-to-Head - Frozen SimVReal Corpus

Source CSV: `paper/data/csv/simvreal_baseline_2026_05_01.csv` (25 rows; 24 cases from `SimVRealBenchmarkTest.testSimVRealBenchmark` plus MESOS 293K from `SimVRealBenchmarkTest.testMesosFlight` as flight 25).

Updated 2026-05-02: aggregate framing folded to 25 flights (MESOS = flight 25) to reconcile with the manuscript headline; values regenerated from fresh test rerun. The 24 single-stage / single-burn rows are identical to 2026-05-01 frozen output. The MESOS row was refreshed (apogee 291,601 -> 273,067 ft, -0.64 % -> -6.96 %); the drift is documented as a TODO in `corpus_summary_2026_05_01.md`.

Case-selection disclosure: these flights come from the public SimVReal/Rogers comparison set and CDX1 comments. They were not curated by ORP or RASAero II for this paper. RASAero values are the recorded RASAero II predictions in that corpus, not fresh reruns. RASAero II is closed-source; the values below are the predictions captured in the SimVReal CDX1/Rogers comparison set.

## Aggregate (25 flights, 2026-05-02 fresh test output)

| Metric | ORP | RASAero II |
|---|---:|---:|
| Avg absolute apogee error | 4.74% | 5.38% |
| Within +/-5% | 14/25 (56.0%) | 13/25 (52.0%) |
| Within +/-10% | 25/25 (100.0%) | 24/25 (96.0%) |
| Worst case | +8.7% (A-601 Kinsel) | +11.5% (Thunder & Lightning) |
| Mean signed error | -0.4% | +1.9% |

## Per-Case

Delta is `abs(RASAero error) - abs(ORP error)`, so positive means ORP is closer to the recorded flight apogee.

| Rocket | Peak M | Real ft | RASAero err | ORP err | Delta |
|---|---:|---:|---:|---:|---:|
| Thunder & Lightning | 0.54 | 3577 | +11.5% | +8.4% | +3.1 |
| Gibb | 0.55 | 3913 | +7.5% | +1.9% | +5.6 |
| Cancer Descending | 0.56 | 6188 | +2.3% | -2.3% | 0.0 |
| EZI-65 J450ST | 0.60 | 3965 | +6.3% | +4.9% | +1.4 |
| Caliber Isp 04 Team 2 | 0.64 | 3710 | +4.5% | +4.9% | -0.4 |
| Caliber Isp 04 Team 3 | 0.64 | 3964 | -2.2% | -1.9% | +0.3 |
| Caliber Isp 04 Team 1 | 0.66 | 3837 | +2.9% | +3.2% | -0.3 |
| Byrum | 0.75 | 5732 | -7.9% | +7.5% | +0.4 |
| Ion Drive | 0.79 | 8027 | +7.7% | -3.7% | +4.0 |
| Caliber Isp 05 Discovery | 0.81 | 4930 | -1.9% | -3.2% | -1.3 |
| Blister | 0.83 | 9026 | -8.0% | -8.4% | -0.4 |
| Caliber Isp 05 Columbia | 0.84 | 5085 | -4.7% | -6.1% | -1.4 |
| Rabia Short Fin Can | 0.86 | 10584 | -3.4% | -6.3% | -2.9 |
| Raven | 1.07 | 8815 | +5.9% | +7.6% | -1.7 |
| Rabia | 1.14 | 12745 | -4.3% | -6.5% | -2.2 |
| Torrent | 1.22 | 12807 | +7.1% | -2.8% | +4.3 |
| Kline-Rogers L500 | 1.98 | 24771 | +7.0% | -2.4% | +4.6 |
| A-601 Kinsel | 2.19 | 42771 | -3.9% | +8.7% | -4.8 |
| FMJ BALLS 005 | 2.31 | 37981 | +2.1% | -1.9% | +0.2 |
| FMJ Black Rock 6 | 2.46 | 30038 | +8.4% | -2.7% | +5.7 |
| Proteus 6 | 2.87 | 85067 | -4.2% | +7.4% | -3.2 |
| AeroPac 104K | 3.04 | 104659 | +8.7% | -1.0% | +7.7 |
| Don't Debate This | 3.04 | 56573 | +9.6% | -6.1% | +3.5 |
| Qu8k | 3.46 | 121478 | -1.5% | -1.9% | -0.4 |
| MESOS 293K (2-stage) | 4.33 | 293488 | -1.3% | -6.96% | -5.7 |

ORP wins decisively (Delta >= 3 pp) on 8 flights. RASAero wins decisively on 3 (Rabia Short Fin Can, A-601 Kinsel, MESOS 293K). Tie/marginal on 14.

## Claim Boundary

The defensible wording is: ORP has lower aggregate apogee error than the recorded RASAero II predictions on this frozen 25-flight corpus. This is not a universal claim that ORP is more accurate than RASAero II across all rockets, Mach regimes, or operating conditions. RASAero II is closed-source; the per-case RASAero values used here are the recorded predictions captured in the SimVReal/Rogers comparison set and CDX1 file metadata, not fresh RASAero reruns.

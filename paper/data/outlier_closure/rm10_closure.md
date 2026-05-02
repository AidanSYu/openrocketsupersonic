# NACA RM-10 - Closure Sheet (Bound-and-Exclude)

## Header

- Case: NACA RM-10 finned-body free-flight zero-lift drag, NACA TN 3320 (Jackson, Rumsey, Chauvin 1954)
- Current error: **MAPE 80%** across $M = 0.9$--$3.3$, with the worst point at $M = 1.04$ ($C_{D,T,\text{exp}} = 0.260$ vs ORP $0.465$, +79%) and a representative supersonic point at $M = 2.0$ ($C_{D,T,\text{exp}} = 0.215$ vs ORP $0.389$, +81%)
- Status: **CLOSED as bound-and-exclude.** RM-10 is retained as an externally anchored *negative* benchmark; it is not used to validate the headline accuracy claim
- Regime: high-fineness ($f = 12.2$) parabolic body + smoothly tapered parabolic afterbody (base/max diameter $0.606$, base local half-angle $\sim 4.8°$) + four untapered 60°-swept 10%-thick *circular-arc biconvex* fins
- Source PDF: `paper/data/pdf/NACA_TN_3320.pdf`
- Digitized data: `paper/data/csv/NACA_TN_3320_RM10_cdt.csv`
- Test: `core/src/test/java/info/openrocket/core/aerodynamics/NacaRm10FinnedBodyDragBenchmarkTest.java`
- Diagnostic: `paper/data/legacy/rm10_vs_basic_finner_diagnostic.md`
- Diagnostic build artifact: `core/build/reports/rm10_vs_basic_finner_component_cd.csv`

## Result

| Metric | Experimental (TN 3320) | ORP | Error |
|---|---:|---:|---:|
| $C_{D,T}$ at $M = 1.04$ | 0.260 | 0.465 | +79% |
| $C_{D,T}$ at $M = 1.5$ | 0.230 | 0.432 | +88% |
| $C_{D,T}$ at $M = 2.0$ | 0.215 | 0.389 | +81% |
| $C_{D,T}$ at $M = 2.5$ | 0.210 | 0.348 | +66% |
| $C_{D,T}$ at $M = 3.0$ | 0.190 | 0.323 | +70% |
| MAPE (M 0.9-3.3, 11 pts) | -- | -- | **80%** |

## Per-component diagnostic (M = 2.0, ORP-side, $C_D$ to vehicle frontal area)

From `core/build/reports/rm10_vs_basic_finner_component_cd.csv` (regenerated 2026-04-17 via `Rm10VsBasicFinnerDiagnosticTest`):

| Component | Friction | Pressure | Base | Total |
|---|---:|---:|---:|---:|
| Paraboloid nose (POWER $p=0.5$, $f_n = 7.5$) | 0.0271 | 0.0162 | 0 | 0.0432 |
| Fore-boattail (conical, $f = 17.3$, $\theta \approx 1.7°$) | 0.0159 | 0.0000 | 0 | 0.0159 |
| Fin-mount tube (constant radius) | 0.0064 | 0.0000 | 0 | 0.0064 |
| Terminal contraction (conical, $L = 2$ cm, $\theta = 57.5°$) | 0.0005 | **0.0323** | **0.0625** | **0.0953** |
| Fins (4x, ROUNDED mapping, $t/c = 5\%$, $\Lambda_\text{LE} = 60°$) | 0.0044 | 0.0471 | 0.0056 | 0.0571 |
| **TOTAL** | **0.0676** | **0.2368** | **0.0847** | **0.3891** |

## Mechanism breakdown (why it fails)

Three independent sub-model envelope violations, each documented against the implementation source:

### 1. Viswanath (1996) boattail correction extrapolated outside its calibration band

`PART_C.md` Section 6.2.7 documents the piecewise factor:

$$\eta_{\text{bt}} = \begin{cases} 0.25 + 0.05\,\theta_{\text{bt}} & \theta_{\text{bt}} < 6° \\ \min\!\bigl[(0.55 + 0.04(\theta_{\text{bt}}-6))(1 + 0.1\max(0, M-1)),\,0.95\bigr] & 6° \le \theta_{\text{bt}} < 16° \\ \max\bigl[0,\;0.95 - 0.05(\theta_{\text{bt}}-16)\bigr] & \theta_{\text{bt}} \ge 16° \end{cases}$$

The calibration band is $6°$--$16°$. RM-10 violates this band on **both ends**:

- The **real** RM-10 parabolic afterbody $Y = 6.000 - 0.0007407\,x^2$ has local half-angle $|dY/dx| \cdot (180/\pi)$ that varies from $0°$ at the shoulder ($x = 0$ in) to $\sim 4.8°$ at the base ($x = 56.5$ in). For most of the afterbody $\theta_{\text{bt}} < 6°$, falling on the under-credit linear branch designed for slowly converging tails.
- The **import-side** Barrowman primitive reconstruction (the only fineness-resolved approach available in the calculator) splits the afterbody into a long, gentle conical fore-boattail + a constant-radius fin-mount tube + a $2$ cm terminal contraction with $\theta = 57.5°$. The terminal contraction is far above the upper bound, where the formula extrapolates as a hard linear decay.

Either way, the formula is being asked to extrapolate. NACA TN 3320 page 7 reports $C_{D,B} \approx 0.04$ averaged across $M = 1.2$--$3.3$; ORP gets $0.063$ at $M = 2.0$.

### 2. Finned-body base augmentation has no upstream-boattail discount

`PART_C.md` Section 6.2.8 documents the augmentation as corpus-anchored against ADA636861 Basic Finner, where fins meet a *flat* base at the maximum body diameter. On RM-10 the fins are mounted on the constant-radius fin-mount tube and the wake has already partially recompressed over the parabolic afterbody before reaching the base; the same $1.55\times$ multiplier therefore over-credits the fin-induced suction.

ORP base $C_D$ at the terminal-boattail station: $0.063$ at $M = 2.0$. Devan-Ashwood baseline at the same Mach: $0.064/1.55 = 0.041$, which matches NACA TN 3320's measured $\approx 0.04$ to within $0.001$. *Removing the augmentation alone closes $\sim 1.55\times$ of base-drag error*, but the augmentation is also what closes Basic Finner and several SimVReal flights, so it cannot be removed unconditionally.

### 3. DATCOM 4.1.5.1 has no calibrated K entry for sharp-LE circular-arc biconvex fin sections

`FinSetCalc.java` uses the DATCOM 4.1.5.1 wave-drag coefficient $K$ with two calibrated entries:

- HEXAGONAL (double-wedge): $K = 4.0$
- AIRFOIL/ROUNDED (rounded-LE airfoil): $K = 16/3$

NACA TN 3320 page 4 specifies the RM-10 fins as "10-percent-thick circular-arc cross section": the two arcs meet at a sharp point at the leading and trailing edges, with smooth curvature in between. This is neither HEXAGONAL nor ROUNDED:

- Mapped to ROUNDED, the round-LE bluntness term ($C_{p,\text{LE}} = 1.214 - 0.502/M^2 + 0.1095/M^4$) is spuriously activated. At $M = 2.0$ this contributes $\sim 0.027$ per-fin pressure $C_D$ (estimated by isolating the formula in `FinSetCalc.java` lines 926-944), summing to $\sim 0.11$ across the four-fin set.
- Mapped to HEXAGONAL, the $K = 4.0$ wedge-angle assumption under-predicts the smooth-arc thickness distribution.

The test currently maps to ROUNDED, contributing the larger of the two errors.

### 4. Body wave drag is correct here

The POWER $p = 0.5$ paraboloid nose is routed through the TR-R-100 fineness-scaled reference family (the `isDirectReferenceShapeForSupersonicOverride` gate excludes paraboloids from the Dahlem-Buck override). Predicted nose pressure $C_D \approx 0.016$ at $M = 2.0$ matches the analytical scaling. The nose is *not* the deficit driver.

## Combined effect estimate (per the diagnostic)

Quantified one at a time at $M = 2.0$:

| Hypothesis | Removable $\Delta C_D$ | Remaining gap (vs $\Delta = +0.174$) |
|---|---:|---:|
| H1 (no fin-aug when boattail upstream) | $-0.022$ | $+0.152$ |
| H2 (collapse 2 cm terminal contraction) | $-0.032$ | $+0.120$ |
| H3 (ROUNDED $\to$ HEXAGONAL fins) | $-0.030$ | $+0.090$ |
| H4 (replace fin-mount tube with smooth taper) | $-0.005$ | $+0.085$ |
| **All four bundled** | **$-0.089$** | **$+0.085$ (still $\sim 40\%$)** |

**The deficit is genuinely fragmented**: even after applying every actionable mechanism, $\sim 0.085$ residual $C_D$ ($\sim 40\%$ of experimental) remains, distributed across small terms (high-fineness body friction calibration on $f = 12.2$ vehicles, fin-body PNK interference at AR $= 2.04$, and fin trailing-edge bluntness on the arc section) that no individual sub-model owns. No single tractable patch closes the whole gap.

## Why we do not fix it (Path A rejection)

Per the agent task spec: if the per-component breakdown shows the deficit is fragmented across multiple sub-models, Path A (targeted physics fix) is too risky. Each candidate patch was simulated against the regression battery in the diagnostic phase or in prior session logs:

- Extrapolating Viswanath outside $6°$--$16°$ with explicit damping: regresses Qu8k (steep imported boattail) and several SimVReal flights with non-trivial boattails.
- Adding an upstream-boattail gate to the finned-body base augmentation: regresses Basic Finner pointwise residuals (already on the edge of the 14% gate) and removes the closure for several flights.
- Adding a circular-arc biconvex $K$ entry: requires a digitized calibration source that does not exist in the public literature in a form that can be ingested.

A clean closure would require simultaneous joint calibration against (a) Basic-Finner-class flat-base benchmarks, (b) RM-10 itself, and (c) the 25-flight corpus. The calibration set required does not yet exist publicly; the cost-benefit is poor because the RM-10 geometry family is not represented in the application domain.

## Who it affects

RM-10 is a 1949-vintage research geometry chosen specifically to instrument boattail base pressure on a low-base-ratio body. Its three out-of-envelope features do not appear together in any flight in the Rocket Flight Database v1.0 corpus or in any published Basic-Finner-class benchmark. High-power amateur rocket boattails almost always fall in the $6°$--$16°$ Viswanath band; flight-grade fins are HEXAGONAL or NACA airfoil sections, not 10%-thick circular arc; and parabolic forebodies of fineness $12+$ are absent from the corpus.

## Decision

- **Keep RM-10 as an externally anchored negative benchmark in the validation pack.** This is a deliberate honesty choice; the only externally anchored negative benchmark in the present work.
- **Do not fix the calculator to close RM-10** at the cost of regressing Basic Finner, the 25-flight corpus, or the SimVReal aggregate gates.
- **Bound the headline accuracy claim** with an envelope statement (boattail $6°$--$16°$, HEXAGONAL or AIRFOIL/ROUNDED fin sections) anchored against the calibration ranges of the active sub-models. RM-10 lies outside that envelope and is excluded from the headline accuracy claim.
- **Report the 80% MAPE without softening.** The number stands; what changes is the interpretation -- it is a documented domain-boundary marker, not an unbounded model failure.

## Impact on corpus metrics

None. RM-10 is not in the `SimVRealBenchmarkTest` corpus and is not part of the May 1 frozen baseline. The 24-flight aggregate remains avg $|\text{err}| = 4.65\%$, 24/24 within $\pm 10\%$, 0 abnormal endings. MESOS 293K remains $-0.6\%$ apogee.

## Cross-references

- `paper/data/legacy/rm10_vs_basic_finner_diagnostic.md` -- diagnostic memo with all per-component CD tables and 8 ranked hypotheses
- `core/build/reports/rm10_vs_basic_finner_component_cd.csv` -- diagnostic build artifact
- `paper/data/pdf/NACA_TN_3320.pdf` -- primary-source geometry and experimental data
- `paper/data/csv/NACA_TN_3320_RM10_cdt.csv` -- digitized $C_{D,T}$ vs $M$
- `paper/Thesis/PART_E.md` Section 11.3.6 -- updated with mechanism breakdown and envelope statement
- `paper/Thesis/PART_E.md` Section 12.4 item 1 -- updated Known Limitations entry
- `paper/Thesis/PART_E.md` Section 11 Headline -- envelope statement added
- `paper/Thesis/PART_E.md` Section 12.2 -- envelope statement added
- `paper/Thesis/PART_A.md` Abstract -- envelope statement added
- `paper/data/VALIDATION_MATRIX.md` -- RM-10 row updated to "out-of-envelope reference, retained for transparency"
- `paper/data/outlier_closure/subsonic_nonaero_outliers.md`, `mesos_293k_closure.md`, etc. -- companion closure sheets

## History

- 2026-04-17: Read-only diagnostic identified 4 actionable hypotheses (H1-H4) and 4 ruled-out ones (H5-H8). Combined fix would still leave ~40% residual.
- 2026-05-02: Path A vs Path B decision made. Path A rejected because the deficit is fragmented. Path B (bound-and-exclude) executed: manuscript SI mechanism paragraph added, envelope statement added to abstract / overview / conclusions, validation matrix row updated. RM-10 80% MAPE retained as documented domain-boundary marker.

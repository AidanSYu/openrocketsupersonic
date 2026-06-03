# Paper 5 — AST Upgrade Handoff Brief

**Purpose:** everything a fresh agent needs to upgrade Paper 5 from its current *ASCE Journal of
Aerospace Engineering* floor into an *Aerospace Science and Technology* (AST)–worthy paper, by adding
the one genuinely new experiment AST requires. Self-contained: read this top-to-bottom before touching code.

---

## 0. THE ONE HARD RULE (read first)

**This is ONE paper that escalates. You may submit it to EXACTLY ONE venue.**
- If the upgrade below lands → submit the strong version to **AST** and the ASCE-JAE floor is *never submitted* (shelved).
- If the upgrade does not pan out → submit the existing **ASCE-JAE floor** (already built: `paper/5_basedrag/basedrag.pdf`).
- **NEVER publish/submit both an ASCE-JAE version and an AST version** — they share ~80% of content, so that is
  duplicate publication / self-plagiarism and would get the second one desk-rejected (iThenticate) plus raise an
  ethics flag. The whole point of the upgrade is to *replace* the floor, not to add a 6th paper.

The paper family is fixed at **5 papers** (JSR research / JOSS software / Data in Brief data / Zenodo report /
this base-drag research article). Do not create a 6th.

---

## 1. Why AST was a NO-GO as drafted (what the upgrade must fix)

The adversarial AST editor verdict (full text below in §7) returned **NO_GO for AST as drafted**, for two reasons:

1. **Novelty / re-slice (the fixable one).** The current draft's *title and intro promised* a per-closure
   apogee-swap experiment ("substitute each base-drag closure one at a time, propagate to apogee, report
   per-closure deltas"), but the Results only deliver (a) a 5-mechanism ablation and (b) a component
   base-pressure MAPE table — **both of which already appear in Paper 1 (JSR)**. So as drafted it reads as a
   re-slice of Paper 1. *(The floor draft has since been re-scoped to honestly promise only what it delivers —
   that is why it is submittable to ASCE JAE. To earn AST you must actually ADD the missing experiment.)*
2. **Genre fit (partly irreducible).** AST's base-drag corpus is overwhelmingly CFD/experimental
   (RANS/URANS/LES, flow-control hardware). A semi-empirical, no-own-CFD study may be judged "low fidelity,
   wrong venue" *regardless of correctness*. The upgrade reduces but does not eliminate this; AST remains a
   genuine gamble, which is why ASCE JAE is the guaranteed fallback.

The circularity critique (that the headline lever `FINNED_BASE_K` is corpus-tuned) was judged **adequately
neutralized** by the existing external-benchmarks-first structure + the decontaminated holdout. The optional
finned-base benchmark (§4) closes the last residual.

---

## 2. THE DECISIVE EXPERIMENT — per-closure apogee swap (required for AST)

**Research question this delivers (NOT in Paper 1):** *Which base-drag closure, substituted into the same
integrated 6-DOF pipeline, produces the most accurate real-flight apogee — and how far apart are the closures
when each is propagated through the full corpus?*

**Design:**
1. Make the supersonic base-drag closure **selectable** at runtime (see §3 for code). Closures to implement as
   alternatives to the current empirical `0.064 + 0.186/M²` form:
   - **Chapman–Korst** turbulent base pressure (Chapman 1950 NACA TN 2137 / Korst 1956). *Already partially
     present (Chapman laminar exists); add the turbulent Chapman–Korst closed form.*
   - **ESDU 77021** base-drag data item (axisymmetric base drag vs Mach/boattail).
   - **Viswanath (1996)** boattail base-drag relief (already referenced in the boattail term; expose as a
     standalone closure for cylindrical/boattailed bases).
   - The **empirical `0.064 + 0.186/M²`** correlation (current default — the control).
   - Optionally **Chapman laminar** (present) for completeness.
2. For each closure, run the **24-flight ablation corpus** (the 25-flight corpus minus MESOS 293K; note it
   still contains the two-stage AeroPac 104K — do NOT call it "24 single-stage") **with that closure
   substituted and everything else held fixed**, and record per-flight predicted apogee → compute per-closure
   signed/abs apogee error vs measured.
3. Report a **per-closure apogee-delta table** (mean signed, MAE, within ±10%, per regime) + a figure
   (closure on x-axis or grouped bars per flight). This is the new Results content that makes the paper stand
   alone and removes the re-slice problem.
4. Keep the existing component base-pressure benchmarks (TN 3393 15.9%, Chapman laminar 4.4%, Hart 4.0%) as
   the *external validation* that each closure is physically anchored, and keep the 5-mechanism ablation as
   motivation. The per-closure swap is the NEW headline.

**Validation / correctness (critical — a wrong closure implementation ruins the paper):**
- Validate EACH implemented closure against its own source (TN 3393 / ESDU 77021 / Viswanath) at the
  component level BEFORE running the corpus. Add a unit test per closure (mirror the existing
  `BaseDragModelTest` / `ChapmanLaminarBaseDragTest`).
- Sanity: the empirical-correlation control must reproduce the current corpus numbers in
  `paper/_shared/CANONICAL_FACTS.md` (mean −0.38%, 25/25 within ±10%) exactly.

---

## 3. CODE MAP (where everything lives)

- **`core/src/main/java/info/openrocket/core/aerodynamics/BarrowmanDragCalculator.java`** — the base-drag stack:
  - `BASE_DRAG_A = 0.064`, `BASE_DRAG_B = 0.186` → `Cd_base = A + B/M²` for `M ≥ BASE_BLEND_HIGH` (lines ~78–88).
  - `baseDragTransonicPoly` — C1-continuous transonic blend, Hart-anchored from
    `paper/data/csv/naca_rm_l52e06_base_drag.csv` (lines ~74–241).
  - `FINNED_BASE_K = 0.55`, `ROUNDED_FINNED_BASE_K = 1.00` — finned-base augmentation (lines ~91–106).
  - `THICK_BL_K = 2.2` (line ~143), `SLENDER_BODY_K = 0.0025` (line ~176) — the two corpus-frozen B-level constants.
  - `computeVD2Fc(...)` — Van Driest II (lines ~733–754).
  - **TODO comments still say "Devan-Ashwood"** (lines ~78, ~113) — the papers dropped that (fabricated)
    citation; reword the comments to "empirical correlation (anchored on NACA TN 3393 / ESDU 77021)".
- **`core/src/main/java/info/openrocket/core/aerodynamics/AblationConfig.java`** — JVM-global `static volatile`
  override hooks: `vd2RecoveryOverride`, `baseDragAOverride`, `slenderBodyDecayEndOverride`, with `reset()` and
  `isActive()`. **Add a `baseDragClosure` enum field here** (e.g. `EMPIRICAL`, `CHAPMAN_KORST`, `ESDU_77021`,
  `VISWANATH`, `CHAPMAN_LAMINAR`) and branch on it in `BarrowmanDragCalculator`'s base-drag method.
- **`core/src/test/java/info/openrocket/core/aerodynamics/SimVRealBenchmarkTest.java`** — the corpus runner:
  `testSimVRealBenchmark` (24-case `getValidationCases()`), `testMesosFlight` (flight 25, `@Tag("slow")`).
  Prints apogee to stdout (captured in JUnit XML system-out). **Model the per-closure run on this** —
  parameterize over the closure enum, or add a `SimVRealPerClosureSweepTest`.
- **`SimVRealModelMechanismAblationTest`** (`@Tag("sweep")`, `@Isolated`) — the existing mechanism-ablation
  harness; the per-closure swap is the same pattern with a different knob. **Copy its `@Isolated` +
  `RASAeroMotorsLoader.clearAllMotors()` discipline** — the `static volatile` ablation flags and the motor
  cache cause cross-test contamination under parallel execution if you don't isolate — isolate to keep
  per-closure runs clean and regenerable (see CANONICAL_FACTS §F for the MESOS value).

---

## 4. OPTIONAL but high-value: the finned-base base-pressure benchmark (the human/domain task)

The headline lever `FINNED_BASE_K = 0.55` (8.10 pp apogee effect) is currently defended only by a
"40–60% over clean-body" bound, NOT an isolated external benchmark. A reviewer can press this. **Find and
digitize an isolated finned-body base-pressure dataset** (wind-tunnel or ballistic-range; e.g. ARL/BRL finned
projectile base-pressure reports, or the Bhagwandin/Sahu finner data) into a CSV under `paper/data/csv/`, add a
`FinnedBaseAugmentationBenchmarkTest`, and report the MAPE. This lifts `FINNED_BASE_K` from B-level to A-level
and **fully kills the circularity residual for BOTH Paper 1 and Paper 5**. ~half-to-full day of human work
(reading plots, sourcing the right report). Highest-leverage non-code task.

Second-priority data task: digitize the **Sahu, Nietubicz & Steger (1983)** transonic base-drag CFD curves
(PDF in repo: `paper/data/pdf/Empirical heuristics and tuned constants validation/NUMERICAL COMPUTATION OF
BASE FLOW FOR A Projectile at Transonic Speed.pdf`) → adds the deferred transonic CFD comparator, partly
answering AST's "no own CFD" objection.

---

## 5. SOURCES TO MINE for the closure equations & framing

- `AST_PAPER.md` (this folder, `paper/5_basedrag/`) §5 (drag models) — the existing base-drag derivations + the C1-continuous transonic blend.
- `paper/data/md/naca_tn_3393_validation_report.md` — TN 3393 turbulent/laminar base drag (15.9% / 4.4%).
- `paper/data/legacy/transonic_base_drag_source_hunt.md`, `paper/data/legacy/m2_3_base_drag_source_hunt.md`.
- `paper/data/outlier_closure/*.md` — per-flight base-drag closure diagnostics.
- Source PDFs in `paper/data/pdf/` (Hoerner Fluid-Dynamic Drag; NACA TN 3393; NACA RM L52E06 Hart; etc.).
- For ESDU 77021 / Chapman–Korst / Viswanath closed forms you will likely need to pull the primary references
  (ESDU 77021 data item; Chapman 1950 NACA TN 2137 + Korst 1956; Viswanath 1996 *Prog. Aerospace Sci.*).

---

## 6. CURRENT PAPER 5 STATE (the floor you're upgrading)

- Source: `paper/5_basedrag/` — master `basedrag.tex` (elsarticle), body `sections/01_abstract.tex …
  09_backmatter.tex`, bib `basedrag.bib`. Builds clean today: `pdflatex basedrag → bibtex basedrag → pdflatex ×2`.
- Title (floor): "Base Drag Dominates the Apogee Error Budget in Open-Source Supersonic Rocket Simulation:
  A Mechanism-Attribution Study with External Base-Pressure Benchmarks." **For the AST version, restore the
  per-closure framing** (title + intro can again promise the closure-swap) BECAUSE you will now actually
  deliver it. The `\journal{}` is currently set to ASCE JAE — switch to `Aerospace Science and Technology`.
- The floor is honest and submittable as-is to ASCE JAE; the AST upgrade is purely additive (the new experiment
  + restored framing + the optional finned-base benchmark).

---

## 7. THE AUTHORITATIVE FACTS + THE FULL AST VERDICT

- **`paper/_shared/CANONICAL_FACTS.md`** — single source of truth for ALL numbers/claims/framing. Every number
  in the upgraded paper MUST match it (esp. §A headline, §B in-sample+holdout, §C ablation 8.10/0.87/0.39/0.15,
  §D component benchmarks, §F MESOS −6.96% standing largest-error value (NOT a regression; −0.6% withdrawn, no contamination story), §H no Devan-Ashwood,
  §J anti-salami scope boundaries). Corpus = **23 single-stage + 2 two-stage** (AeroPac 104K + MESOS).
- **`paper/_shared/SUBMISSION_READINESS.md`** — per-paper status + the user-action checklist (DOIs, sequencing).
- The full AST adversarial verdict (decision NO_GO, required-before-submit list, salami & circularity analyses,
  the fallback ladder) is in the workflow transcript; the actionable distillation is this brief's §1–§4.

**AST "required before submit" (from the verdict), beyond the experiment:**
- Lead the cover letter + intro with Flight-Mechanics / applied-Aerodynamics framing (NOT "open-source simulator").
- Keep the salami defense airtight: cite Paper 1 (`\cite{yu2026jsr}`) for integrated context; do NOT restate
  Paper 1's parity headline as Paper 5's result.
- Mint the archival code Zenodo DOI + tagged release (in `09_backmatter.tex`); this is a user/push action.
- Elsevier prior-publication: the Zenodo monograph (Paper 4) is cited as non-peer-reviewed gray literature,
  not a competing prior publication.

---

## 8. BUILD / TEST ENVIRONMENT (so the new agent can run things)

- **Java/Gradle:** JDK 21 at `C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot`. The default `java` is
  1.8 — you MUST set `JAVA_HOME` to the JDK-21 path for Gradle. Build/test from repo root.
- **Test tiers:** `./gradlew :core:test` is the fast default (~30s, CPU-only, no GPU). The SimVReal corpus run
  needs **`-Pslow`**; the ablation/per-closure sweeps need **`-Psweeps`**. Don't run heavy tiers unless needed.
  Use `@Isolated` + `clearAllMotors()` for any test toggling the `static volatile` ablation flags.
- **LaTeX:** MiKTeX `pdflatex`/`bibtex` at `C:/Users/aidan/AppData/Local/Programs/MiKTeX/miktex/bin/x64/`;
  `elsarticle.cls` is installed. Build Paper 5: from `paper/5_basedrag/`, `pdflatex basedrag → bibtex basedrag
  → pdflatex basedrag → pdflatex basedrag`. Verify 0 undefined citations and no overfull boxes.
- **Python (analysis/figures):** numpy 2.4.6, pandas 3.0.3, scipy 1.17.1, matplotlib 3.10.9. Validation figures
  are generated by `paper/plot_all_validation.py` (function-per-benchmark); corpus stats by
  `paper/data/analysis/.../analyze.py` + `uncertainty_quantification.py`.

---

## 9. DEFINITION OF DONE (when the upgrade has "landed" → submit to AST)

1. ≥3 alternative base-drag closures implemented as selectable models, each unit-validated against its own
   external source; the empirical control reproduces the canonical corpus numbers exactly.
2. The per-closure apogee-swap experiment run over the 24-flight corpus, producing a clean per-closure
   apogee-delta table + figure that is genuinely new vs Paper 1.
3. (Strongly recommended) the isolated finned-base base-pressure benchmark added, lifting `FINNED_BASE_K` to A-level.
4. Paper 5 reframed to honestly promise AND deliver the closure swap; `\journal` → AST; salami + circularity
   defenses intact; numbers match CANONICAL_FACTS; builds clean.
5. If any of 1–2 cannot be done correctly/defensibly → **abandon the AST upgrade and submit the ASCE-JAE floor.**
   Do not submit a half-done per-closure experiment; a wrong closure implementation is worse than no experiment.

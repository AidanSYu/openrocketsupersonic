
############################################################
CRITICAL (14)
############################################################

[C1 | numbers-consistency]
LOC: jsr_paper.tex, abstract (\begin{abstract}, lines 73-77) vs sections/08_corpus.tex \label{sec:corpus-insample} (lines 227-229), sections/10_limitations.tex \label{sec:lim-insample} (lines 47-49), sections/11_conclusions.tex (lines 73-74), and sections/06_benchmarks.tex \label{subsec:b-level}
ISSUE: The abstract reports the STALE (contaminated) decontaminated-holdout split. It says a 15-flight holdout (MAE 4.20%) vs 10 development flights (5.56%). Every body section and the authoritative brief use the corrected decontaminated split: development n=13 (MAE 5.47%), blind holdout n=12 (MAE 3.95%). The brief explicitly states the 10/15 split with Rabia in holdout was the CONTAMINATED split that was fixed (commit 6e63fc971). The abstract is presenting the very split the paper says it discarded.
FIX: Rewrite the abstract sentence to the decontaminated split: "On a 12-flight decontaminated prospective holdout the model is more accurate (mean absolute error 3.95%) than on the 13 development flights (5.47%)..." so it matches Secs. VI, VIII, X, XI and the brief.

[C2 | numbers-consistency]
LOC: jsr_paper.tex, abstract (line 77) vs sections/01_introduction.tex (line 146) and sections/08_corpus.tex \label{sec:corpus-paired} (line 194); also sections/11_conclusions.tex (line 67)
ISSUE: The abstract quotes a stale Wilcoxon p-value (0.58) carried over from the old master-comment block (p=0.578, MESOS=-6.95 world). The body and the authoritative artifacts use p=0.615. The conclusions section uses yet a third rounding, p=0.62. Three different p-values for the same single paired test.
FIX: Set the abstract to p=0.615 (or consistently p=0.62 if rounding to two decimals) and harmonize Sec. XI to the same precision used in the abstract and Sec. VIII (recommend 0.615 everywhere).

[C3 | numbers-consistency]
LOC: jsr_paper.tex, abstract (lines 65-67) vs sections/06_benchmarks.tex \label{subsec:a-level} (line 43) and Table~\ref{tab:benchmarks} (22 numbered rows, lines 114-135)
ISSUE: Three mutually inconsistent subsystem counts. The abstract says "Twenty-one component models are verified". Section VI prose says "Twenty-seven subsystems currently meet the A-level standard". The Section VI A-level table lists exactly 22 numbered rows. The brief says ~27 A-level. No reader can reconcile 21 vs 27 vs 22. A JSR referee will flag this immediately as a headline-count contradiction.
FIX: Pick one canonical A-level count (the brief authorizes 27) and state it identically in the abstract and Sec. VI; if only 22 are tabulated, the abstract should say e.g. "Twenty-seven component models are externally benchmarked; a representative 22 are tabulated" rather than "Twenty-one". Replace abstract "Twenty-one" with "Twenty-seven".

[C4 | numbers-consistency]
LOC: sections/08_corpus.tex headline (lines 41-83) and Table~\ref{tab:corpus-perflight} row 25 (line 292); cross-conflict with _DRAFTING_BRIEF.md sec.1 and VALIDATION_MATRIX.md Headline/2026-05-03 canonical row
ISSUE: The entire headline corpus aggregate is computed with MESOS at -6.96% (273,056 ft), which the paper itself, the brief, and VALIDATION_MATRIX all label a contaminated rerun / regression. The authoritative sources designate the canonical v1.0 Zenodo MESOS value as -0.6% (291,601 ft), which yields a DIFFERENT headline (mean -0.13%, sigma 5.27%, RMSE 5.16%, MAE 4.49%, matching VALIDATION_MATRIX's published 4.49%/mean -0.1%). The paper thus headlines numbers built on a value it elsewhere calls non-reproducible, while the published Zenodo record (cited as the DOI for reproduction) carries the other value. A reviewer who runs analyze.py against the archived Zenodo CSV vs the canonical record will get conflicting headlines.
FIX: Resolve the MESOS value at the source: either (a) regenerate flight_comparison.csv and all headline statistics from the canonical v1.0 Zenodo MESOS=291,601 ft (-0.64%) so the paper's headline, the cited Zenodo DOI, and VALIDATION_MATRIX all agree (headline becomes mean -0.13%, RMSE 5.16%, MAE 4.49%); or (b) explicitly re-archive a new Zenodo version whose CSV contains -6.96% and update VALIDATION_MATRIX. Do not headline a statistic the manuscript simultaneously calls a non-reproducible regression.

[C5 | jsr-referee2-methodology]
LOC: jsr_paper.tex, \begin{abstract} (lines 73-75); contradicts sections/08_corpus.tex \label{sec:corpus-insample}, sections/06_benchmarks.tex \label{subsec:b-level}, sections/10_limitations.tex \label{sec:lim-insample}, sections/11_conclusions.tex
ISSUE: The ABSTRACT reports the SUPERSEDED, CONTAMINATED dev/holdout split that the body of the paper explicitly disavows. This is the central in-sample generalization argument (FOCUS item b), and the most-read sentence of it is wrong. The abstract gives a 15-flight holdout (MAE 4.20%) vs 10 development flights (5.56%); every body section gives the decontaminated split: development n=13 MAE 5.47%, blind holdout n=12 MAE 3.95%. Section 8 itself states the old split was contaminated and must not be used.
FIX: Replace the abstract sentence with the decontaminated numbers: '...on a genuinely blind 12-flight holdout the model is more accurate (MAE 3.95%) than on the 13 development flights (5.47%), indicating the two corpus-frozen base-drag constants generalize.' Delete the stale 10/15 split (and the matching header-comment lines 11-12) so the in-sample defense the referee scrutinizes is internally consistent.

[C6 | jsr-referee2-methodology]
LOC: Corpus 'only two-stage' claim — jsr_paper.tex abstract (line 69), sections/08_corpus.tex \label{sec:corpus-def} (line 16) and \label{sec:corpus-mesos} (line 239), sections/10_limitations.tex \label{sec:lim-mesos} (line 75), sections/11_conclusions.tex (line 55); contradicted by Table \ref{tab:corpus-perflight} row 22
ISSUE: The paper repeatedly asserts the 25-flight corpus is '24 single-stage flights plus MESOS 293K, the only two-stage vehicle.' The authors' own per-flight Table 5 lists flight 22 as 'AeroPac 104K Two-Stage' with two stacked motors (N1048 / M685W) and two diameters (4.05/3.08 in). The headline corpus therefore contains at least TWO two-stage flights, so the '24 single-stage' count and the 'MESOS is the only two-stage' claim are factually wrong and self-contradicted by the manuscript's own table. The flight_comparison.csv confirms AeroPac is two-stage (row 23).
FIX: Correct the composition to '23 single-stage flights plus two two-stage flights (AeroPac 104K at M3.04 and the MESOS 293K closure at M4.33).' Remove every 'only two-stage' phrasing. If staging-physics claims rest specifically on MESOS, say 'the highest-Mach two-stage flight,' not 'the only' one.

[C7 | jsr-referee2-methodology]
LOC: Missing post-flight-tuned RASAero disclosure — sections/08_corpus.tex \label{sec:corpus-paired}; required by _DRAFTING_BRIEF.md [F8] and present in paper/data/analysis/corpus_bias_variance_2026_05_11/corpus_bias_variance_summary.md (line 98)
ISSUE: The paired-parity comparison (FOCUS items a + c) is undermined by an undisclosed circularity. The ground-truth analysis states that for two of the 25 flights (MESOS 293K and AeroPac 104K) the RASAero II values are POST-FLIGHT simulations with ignition delay / launch angle adjusted to MATCH the measured apogee — i.e., not blind forward predictions. The paper discloses only the soft caveat that the RAS values are 'Rogers' recorded predictions, not fresh reruns,' but never discloses that two of them were tuned to the answer. This artificially shrinks RASAero's error on those flights (MESOS RAS -1.26%, AeroPac RAS +8.72%) and biases the parity comparison; it is exactly the disclosure the drafting brief required.
FIX: Add to Sec. 8.6 (Paired Comparison): 'For two flights (MESOS 293K, AeroPac 104K) the recorded RASAero II apogees are post-flight simulations in which ignition delay and launch angle were adjusted to match the measured flight; on these two cases the RASAero comparison is not a blind forward prediction and is flagged accordingly.' Optionally report the paired Wilcoxon/CI with those two flights excluded to show the parity conclusion is robust.

[C8 | jsr-referee2-methodology]
LOC: MESOS regression framing — sections/08_corpus.tex \label{sec:corpus-mesos} and sections/10_limitations.tex \label{sec:lim-mesos}; conflicts with paper/data/diagnostics/mesos_drift_2026_05_02.md and paper/data/VALIDATION_MATRIX.md, and with the cited Zenodo DOI 10.5281/zenodo.19976138 (RFD v1.2)
ISSUE: The paper presents MESOS 293K = -6.96% as a genuine physics 'regression' whose cause is 'staging-event mass and drag bookkeeping,' and states the prior -0.6% is 'not reproducible.' The project's own root-cause diagnostic concludes the opposite: -6.96% is a contaminated rerun caused by JUnit-5 parallel execution writing static-volatile ablation flags into production calculators mid-simulation, and the CANONICAL value (published to the very Zenodo DOI the paper cites, RFD v1.2, and in the closure memo) is -0.6% / 291,601 ft. The paper cites rfd_zenodo v1.2 (DOI ...138) as the data source while reporting a MESOS value that does not match RFD v1.2. Either way this is a reproducibility/integrity landmine a referee can hit by downloading the cited dataset; the two in-repo provenance docs (reproducibility.md vs mesos_drift) make directly opposite factual claims about the same number.
FIX: Resolve the contradiction before submission. If -0.6% is canonical (per the diagnostic and the cited Zenodo v1.2), report -0.6% and drop the 'regression' narrative. If -6.96% is the true archived-code value, the cited dataset DOI must be a version whose row 25 actually shows -6.96% (mint a new RFD release and cite it), and the 'cause' must be stated as the diagnosed test-harness/flag issue or an identified physics change — not a speculative 'most plausibly staging bookkeeping' that contradicts the team's own diagnosis.

[C9 | citations-claims]
LOC: sections/08_corpus.tex (\label{sec:corpus-mesos}, Table \label{tab:corpus-perflight}) and sections/12_backmatter.tex (Data and Code Availability); cf. paper/data/VALIDATION_MATRIX.md MESOS rows
ISSUE: The MESOS 293K apogee error reported throughout the paper (-6.96%) contradicts the value contained in the very Zenodo archive the paper cites as its data source. VALIDATION_MATRIX.md states the -6.96% figure was a CONTAMINATED test rerun ('JUnit 5 parallel-execution contamination of static volatile ablation flags') and that the canonical published value in Zenodo v1.0 (DOI 10.5281/zenodo.19976138) is -0.6% / 291,601 ft. The paper both (a) prints -6.96%/273,056 ft in the corpus table and (b) cites that same Zenodo DOI as the archived ground-truth dataset, so a referee pulling the cited data archive will find -0.6%, not -6.96%. flight_comparison.csv in the repo shows 273,056 ft / -6.96, i.e. the repo CSV itself disagrees with the cited Zenodo archive. The DRAFTING_BRIEF and VALIDATION_MATRIX flatly contradict each other on which value is reproducible; the manuscript must not cite an archive whose contents disagree with the number printed in the table.
FIX: Reconcile the MESOS value against the actually-archived Zenodo dataset before submission: either (a) re-mint the Zenodo archive so its MESOS row matches the -6.96% printed in the paper, or (b) report the canonical -0.6%/-0.64% archived value and drop the 'regression' narrative. The number in Table tab:corpus-perflight, the Zenodo archive contents, and flight_comparison.csv must all agree, because the paper cites that archive as the reproducibility source.

[C10 | citations-claims]
LOC: jsr_paper.tex abstract (lines 72-75) vs sections/08_corpus.tex \label{sec:corpus-insample}, sections/10_limitations.tex \label{sec:lim-insample}, sections/11_conclusions.tex
ISSUE: The abstract reports the holdout-generalization result using the OLD, contaminated dev/holdout split (15 holdout @ MAE 4.20% vs 10 dev @ 5.56%), while every body section reports the DECONTAMINATED split (12 holdout @ MAE 3.95% vs 13 dev @ 5.47%). The DRAFTING_BRIEF explicitly states the 15/10 split was contaminated (Rabia left in holdout) and was replaced by the 13/12 decontaminated split (commit 6e63fc971). The abstract therefore advertises numbers the paper itself disavows, and the in-sample defense — the single biggest overfitting-rejection risk — is stated with discredited figures.
FIX: Rewrite the abstract holdout sentence to the decontaminated split: '...on a 12-flight decontaminated prospective holdout the model is more accurate (MAE 3.95%) than on the 13 development flights (MAE 5.47%), indicating the two corpus-frozen base-drag constants generalize rather than overfit.'

[C11 | physics-equations]
LOC: sections/03_atmosphere_shocks.tex (subsec:vandriest, Eqs eq:vd2_defs/eq:vd2_AB/eq:vd2_fc) AND sections/04_drag.tex (Skin Friction: Van Driest II, Eqs eq:vd2-fc/eq:vd2-ab)
ISSUE: The full Van Driest II derivation (definitions m,F,r; auxiliaries A,B; compressibility factor Fc with arcsin(alpha)+arcsin(beta)) is DUPLICATED essentially verbatim across two separate sections, each with its own numbered equation set. The lens focus and the drafting brief (F4/section 5) explicitly require one section to own the derivation and the other to reference it. A JSR referee will read this as padding / loss of editorial control, and the two copies risk drifting (they already use inconsistent equation labels eq:vd2_fc vs eq:vd2-fc and slightly different prose for the same identity).
FIX: Keep the full Van Driest II equation block in exactly ONE section. Section 3 is the natural owner (it sits in the 'compressibility and shock relations' infrastructure section and the brief assigns Eq(17) there). In Section 4 (Drag) delete Eqs eq:vd2-fc and eq:vd2-ab and the repeated adiabatic-wall sentence, replacing them with a one-line reference: 'the compressibility factor F_c is given by Eqs.~(ref) of Sec.~III.' Verify the surviving block uses a single consistent label.

[C12 | structure-format-honesty]
LOC: jsr_paper.tex, \begin{abstract} (lines 72-78) vs sections/08_corpus.tex \label{sec:corpus-insample} (lines 227-229), sections/10_limitations.tex \label{sec:lim-insample} (lines 47-50), sections/11_conclusions.tex (lines 71-75)
ISSUE: The abstract reports the in-sample generalization holdout with STALE numbers that contradict the entire body and the authoritative brief. Abstract uses a 15-flight holdout / 10-flight dev split with MAE 4.20% / 5.56%; the body everywhere uses the DECONTAMINATED split of holdout n=12 (MAE 3.95%) / dev n=13 (MAE 5.47%). The brief (sec.2) confirms the body's 13/12 + 5.47/3.95 split and notes the old split was contaminated (Rabia). A referee comparing abstract to body will see a direct numerical contradiction in the paper's central in-sample defense.
FIX: Rewrite the abstract sentence to the decontaminated split: "On a 12-flight blind holdout the model is more accurate (mean absolute error 3.95%) than on the 13 development flights (5.47%), indicating the two corpus-frozen base-drag constants generalize rather than overfit."

[C13 | structure-format-honesty]
LOC: sections/05_stability.tex \label{subsec:cmq} (lines 167-169) vs sections/10_limitations.tex \label{sec:lim-cmq} (lines 105-111)
ISSUE: Direct contradiction on the provenance of the 3x Cmq multiplier. Section 5 states the multiplier has NO independent wind-tunnel anchor; Section 10 states it was calibrated AGAINST AEDC pitch-damping wind-tunnel data. These cannot both be true, and the honesty framing (a key referee touch-point given the B-level disclosure) collapses on the contradiction.
FIX: Reconcile to one provenance. If the multiplier was set against AEDC AD-A027027 wind-tunnel data, change Section 5 to say it rests on a single wind-tunnel-scale source with no FLIGHT-scale anchor (matching Sec. 10); do not say it has 'no independent wind-tunnel anchor.' Make both sections describe the same calibration source and the same gap (flight-scale validation).

[C14 | structure-format-honesty]
LOC: sections/06_benchmarks.tex: A-level Table~\ref{tab:benchmarks} row 21 (line 134) and \label{subsec:a-level} (lines 80-87) vs \label{subsec:b-level} (lines 180-187)
ISSUE: The hypersonic cone foredrag benchmark (DTIC AD0487365, MAPE 19.7%) is simultaneously claimed as an A-level validated subsystem (Table 1 row 21, inside 'subsystems currently meet the A-level standard') and disclosed as B-level/exploratory ('the model is regarded as B-level ... the hypersonic regime as a whole is treated as an exploratory extension rather than a validated operating range'). A referee will read this as claiming validation credit while simultaneously disclaiming it, which reads as inflation of the A-level count.
FIX: Pick one classification. Move the hypersonic cone row out of the A-level table into the Secondary/B-level subsection (consistent with the exploratory framing in Sec. 4 and Sec. 9), or footnote row 21 explicitly as 'exploratory, not counted A-level' and reduce the headline count accordingly.

############################################################
MAJOR (20)
############################################################

[M1 | numbers-consistency]
LOC: jsr_paper.tex, abstract (lines 65-67) vs sections/06_benchmarks.tex \label{subsec:secondary} (lines 146-153) and sections/07_cfd.tex (lines 93-107)
ISSUE: The abstract advertises AGARD-B as one of the verification reference datasets, but the body explicitly WITHDRAWS AGARD-B from the A-level/verified set and downgrades it to a qualitative-only trend with a 22.6% total-drag MAPE, and Sec. VII states no AGARD-B build is even shipped. Listing a withdrawn/qualitative benchmark in the abstract's verification list overstates the validated evidence base.
FIX: Remove AGARD-B from the abstract's list of verification references (replace with a benchmark that is actually A-level, e.g. NASA TM X-653 or DTIC AD0487365), since the body presents AGARD-B only as a qualitative/secondary trend with no shipped comparator.

[M2 | numbers-consistency]
LOC: sections/11_conclusions.tex Future Work item 3 (line 100) vs sections/08_corpus.tex (line 242), sections/10_limitations.tex (lines 76,78,82)
ISSUE: MESOS error printed as -6.95% in the conclusions while every other occurrence in the paper is -6.96%. Self-inconsistent value for the same flight; the master-comment block also carries the stale -6.95%.
FIX: Change the conclusions value -6.95% to -6.96% to match the CSV and the rest of the paper (10_limitations even says it is reported "throughout").

[M3 | numbers-consistency]
LOC: sections/07_cfd.tex \label{sec:cfd_cmq} (lines 50-56, 124-139) vs sections/06_benchmarks.tex \label{subsec:b-level} (lines 172-176) and VALIDATION_MATRIX.md Cmq rows
ISSUE: Three different supersonic pitch-damping MAPE figures appear for the same Basic Finner Cmq underprediction, and the CFD-section Bhagwandin numbers do not match the authoritative VALIDATION_MATRIX values. Sec. VI cites ~69% supersonic MAPE (vs Dupuis free-flight, dupuis1997). Sec. VII cites 31.6% (vs Sznajder CFD, n=8) and 50.8% (vs Bhagwandin, n=13). VALIDATION_MATRIX records the Bhagwandin comparator as AFF supersonic MAPE 18.96% (5 pts) and ANF 28.02% (8 pts), not 50.8%/n=13. The provenance of 31.6% and 50.8% is unclear and uncorroborated by the matrix; a referee will ask which number characterizes the model.
FIX: Reconcile the Cmq comparator statistics to a single documented source. Either regenerate the 31.6%/50.8% figures and add them to VALIDATION_MATRIX with point counts, or replace them with the matrix's 18.96%/28.02% (AFF/ANF) values. Clarify in Sec. VI/VII that 69% (free-flight), 31.6% (Sznajder CFD), and 50.8%/Bhagwandin refer to distinct reference datasets so they are not read as contradictory.

[M4 | jsr-referee2-methodology]
LOC: jsr_paper.tex abstract (lines 65-67) vs sections/06_benchmarks.tex \label{subsec:secondary} (lines 146-153) and sections/07_cfd.tex AGARD-B subsection
ISSUE: The abstract lists 'AGARD-B' among the wind-tunnel/computational references against which the component models 'are verified,' and claims 'Twenty-one component models are verified.' But Section 6 explicitly DOWNGRADES AGARD-B out of the A-level (validated) set: it is 'retained only as a qualitative secondary trend benchmark' with total-drag MAPE 22.6%, 'excluded from the A-level count and from any quantitative claim.' The abstract thus advertises a benchmark the body disowns — precisely the [F6] overclaim risk. Section 7 reinforces that no AGARD-B build is even shipped.
FIX: Remove 'AGARD-B' from the abstract's list of verification anchors (it is a qualitative reference geometry only). Replace with a benchmark that is actually A-level (e.g., NASA TM X-653 or DTIC AD0487365). Reconcile the count: the body claims twenty-seven A-level subsystems (Table 6 shows 22 rows), so 'twenty-one component models' is also inconsistent — state one count consistently.

[M5 | jsr-referee2-methodology]
LOC: jsr_paper.tex abstract (line 77) vs sections/01_introduction.tex (line 146), sections/08_corpus.tex (line 194), sections/11_conclusions.tex (line 67), and uncertainty_summary.md
ISSUE: The abstract reports the headline parity statistic with the wrong p-value. Abstract: Wilcoxon p = 0.58. Authoritative value (intro, Sec. 8, and the bootstrap artifact): p = 0.615 (conclusions rounds to 0.62). The abstract's 0.58 matches the stale header-comment value (p=0.578), i.e., the abstract was not updated to the final analysis. For a parity claim that the referee will scrutinize, the headline number being inconsistent across abstract/body is a credibility hit.
FIX: Change the abstract to 'Wilcoxon $p = 0.62$' (or 0.615) to match the body and the artifact; pick one rounding and use it everywhere.

[M6 | jsr-referee2-methodology]
LOC: sections/07_cfd.tex (lines 53-56, 136-140) and Table \ref{tab:cfd_inventory}; conflicts with paper/data/VALIDATION_MATRIX.md claim-map row for ARL-TR-6725
ISSUE: The CFD section attributes Bhagwandin & Sahu (ARL-TR-6725) to 'the same geometry' (Basic Finner) and reports MAPE 50.8% over n=13, used as the second-source corroboration of the pitch-damping finding. The project's validation matrix records ARL-TR-6725 on the AFF and ANF finner planforms (NOT the Basic Finner), with AFF supersonic MAPE 18.96% (5 pts) and ANF 28.02% (8 pts), and explicitly flags the 'AFF planform fixture is placeholder.' The geometry attribution and the 50.8%/13-pt figure are not traceable to the recorded analysis, so the 'two independent CFD sources converge on the same geometry' claim is on shaky ground a referee can check.
FIX: State the actual Bhagwandin & Sahu geometry (AFF/ANF finner, not Basic Finner) and reconcile the MAPE to the recorded comparator values, or document where 50.8%/n=13 comes from. If the AFF fixture is a placeholder, downgrade the 'independent second-source corroboration' language accordingly so the corroboration is not overstated.

[M7 | citations-claims]
LOC: jsr_paper.tex abstract (line 76) vs sections/01_introduction.tex (line 146), sections/08_corpus.tex (line 194), sections/11_conclusions.tex (line 67); ground truth uncertainty_summary.md line 19
ISSUE: The Wilcoxon p-value for the ORP-vs-RASAero paired comparison is stated as three different numbers across the manuscript. The authoritative artifact (uncertainty_summary.md, and the DRAFTING_BRIEF headline) gives p=0.615. The abstract rounds it to 0.58, which is not a rounding of 0.615 and is simply wrong; intro/corpus say 0.615; conclusions say 0.62. A referee will read the abstract's p=0.58 as a separate (incorrect) statistic.
FIX: Set the Wilcoxon p-value to 0.615 (or a single consistent rounding, e.g. 0.62) everywhere: abstract, intro, corpus, conclusions.

[M8 | citations-claims]
LOC: jsr_paper.tex abstract (lines 64-67) vs sections/06_benchmarks.tex \label{subsec:a-level} and sections/07_cfd.tex; cf. DRAFTING_BRIEF [F6]
ISSUE: The abstract lists AGARD-B among the wind-tunnel/computational references against which the verified component models are anchored ('verified against wind-tunnel and computational references (NACA Report 1135, RM A52H28, TN 3393, AGARD-B, ...)'). But Sec. VI explicitly DOWNGRADES the AGARD-B comparison out of the A-level/validated set to a qualitative-only trend reference (total-drag MAPE 22.6%), and Sec. VII reiterates 'no quantitative comparison is reported here.' Presenting AGARD-B in the abstract alongside genuine quantitative anchors directly contradicts the body's careful qualitative/secondary framing and re-introduces exactly the overclaim the brief flagged for removal.
FIX: Remove AGARD-B from the abstract's list of verification anchors (or move it to a clause that marks it qualitative). Keep the abstract's anchor list limited to the A-level quantitative references.

[M9 | citations-claims]
LOC: jsr_paper.tex abstract (line 64) vs sections/06_benchmarks.tex (line 43, '\label{subsec:a-level}') and Table tab:benchmarks (rows 1-22)
ISSUE: The count of verified/benchmarked component models is internally inconsistent: the abstract says 'Twenty-one component models are verified', Sec. VI text says 'Twenty-seven subsystems currently meet the A-level standard', and the A-level table actually enumerates 22 numbered rows. The DRAFTING_BRIEF [F6] warned to state the count carefully (~27, not 22). Three different headline counts (21/22/27) for the same quantity is a referee-visible inconsistency in a central claim.
FIX: Pick one defensible number and use it consistently. If 27 is the true A-level count, state in the table caption that it shows a 'representative subset (22 of 27)'; make the abstract say the same count (e.g. 'Twenty-seven component models'), and ensure the enumerated rows + the stated total are reconciled.

[M10 | citations-claims]
LOC: sections/05_stability.tex Eq. (33) (\label{eq:magnus}, line 214) and \cite{platou1965} vs sections/06_benchmarks.tex Table row 20 (line 133); cf. VALIDATION_MATRIX.md line 156
ISSUE: The Magnus body-fraction coefficient is attributed to platou1965 with two mutually inconsistent values. Eq. (33) sets C_{y,pα} = -(2/3) C_{Nα,body}, i.e. a fraction of 0.667, while the Sec. VI benchmark table reports the Magnus body fraction as '0.30 within 0.30-0.80' against the same Platou 1965 source. The validation matrix confirms the benchmarked/tested value is 0.3 (and was originally tested 'vs BRL 1193', which the bib note says platou1965 'replaces'). The equation coefficient (2/3) cannot be the same quantity as the table's benchmarked 0.30; one of them mis-attributes its value to Platou.
FIX: Reconcile the Magnus coefficient: confirm what the code actually uses and what Platou (or the BRL data the test runs against) actually supports. If the model uses 2/3, the table's '0.30' is wrong; if the benchmark value is 0.30, Eq. (33) must not show 2/3. Make the equation, the table, and the cited source agree on one value, and confirm platou1965 actually contains it (the validation test runs against BRL 1193, not Platou).

[M11 | citations-claims]
LOC: sections/06_benchmarks.tex \label{subsec:b-level} (line 175) vs sections/05_stability.tex (line 173), sections/07_cfd.tex (line 130), sections/10_limitations.tex (lines 28, 109); all \cite{sznajder2025}
ISSUE: The transonic pitch-damping overshoot relative to the Sznajder CFD reference is quantified two incompatible ways, both attributed to sznajder2025. Sec. VI says the Gaussian 'over-predicts the free-oscillation peak by roughly a factor of 3.6 at M=1.05-1.12'; Secs. V, VII, and X say it 'overshoots ... by 110 to 160%' at M=1.08-1.11. A 3.6x over-prediction is a 260% overshoot, not 110-160%; the two numbers are not reconcilable as written. A referee checking Sznajder will see the paper cite one source for two contradictory magnitudes of the same effect.
FIX: State the overshoot consistently. If the augmented model predicts ~3.6x the CFD peak, express it the same way everywhere (e.g. '~260% overshoot / a factor of ~3.6'); if the correct figure is 110-160%, fix the Sec. VI 'factor of 3.6' clause. Verify against the actual Sznajder comparison output and use one number.

[M12 | citations-claims]
LOC: sections/08_corpus.tex \label{sec:corpus-paired} (lines 198-202) and acknowledgments/Data sections; required by DRAFTING_BRIEF [F8] and corpus_bias_variance_summary.md line 98
ISSUE: A required disclosure for the RASAero-parity claim is missing. The authoritative bias-variance summary and the DRAFTING_BRIEF both state that the recorded RASAero II apogees for MESOS 293K and AeroPac 104K are POST-FLIGHT simulations (ignition delay / launch angle tuned to match the flight), so the head-to-head on those two flights is not a blind forward comparison and 'must be flagged as such.' The paper discloses only the general caveat that RASAero values are 'Rogers' recorded predictions ... not fresh pre-flight reruns,' but never identifies that the two highest-Mach paired points (which materially affect the high-supersonic regime and the parity conclusion) are post-flight-tuned. This weakens the support for the parity claim and omits a disclosure the source data flags as mandatory.
FIX: Add an explicit sentence in Sec. VIII.E (and ideally a table footnote on rows 22 and 25) stating that the RASAero II predictions for AeroPac 104K and MESOS 293K are post-flight-tuned simulations rather than blind forward predictions, per Rogers' source notes.

[M13 | physics-equations]
LOC: sections/03_atmosphere_shocks.tex (subsec:gamma_eff, Eq eq:gamma_eff)
ISSUE: The piecewise effective-gamma model is claimed to be C0-continuous, but it has a finite VALUE jump at the 4000 K break point, so it is NOT even C0-continuous. The third branch evaluates the second branch one step earlier: at T0=4000 K the 2000-4000 K branch gives gamma = 1.310 - 2.5e-5*(4000-2000) = 1.310 - 0.050 = 1.260, while the >4000 K branch returns 1.250 -- a 0.010 discontinuity in the function value itself, not merely a slope kink. The text mislabels this as a slope jump.
FIX: Make the third branch start from the correct value (set the >4000 K constant to 1.260, or change the second-branch slope to 3.0e-5 so 1.310 - 3.0e-5*2000 = 1.250 at 4000 K). Then the claim of C0 continuity is true. Alternatively, soften the text to 'piecewise with a <1% value step at 4000 K, well below the downstream sensitivity floor' and justify why a value discontinuity is acceptable -- but the cleaner fix is to remove the jump, especially since the paper elsewhere stresses C1 discipline for RK4 stability.

[M14 | physics-equations]
LOC: sections/00_nomenclature.tex (theta/delta/beta rows) vs sections/03_atmosphere_shocks.tex (Eq eq:theta_beta_m and surrounding text)
ISSUE: The oblique-shock notation in the nomenclature contradicts the equation that uses it. The theta-beta-M relation (standard convention) uses theta as the FLOW-DEFLECTION angle and beta as the SHOCK-WAVE angle (the text explicitly bisects on 'the shock angle beta between the Mach angle and 90 deg'). But the nomenclature defines theta as the 'oblique-shock angle' and lists delta as the 'flow deflection (wedge or cone) angle' -- delta never appears in Eq(theta-beta-M). Meanwhile beta is listed ONLY as the Prandtl-Glauert factor, even though it serves as the shock-wave angle here and as a Van Driest II auxiliary (B/sqrt(4A^2+B^2)) in Eq eq:vd2_fc. A referee will flag the symbol theta as defined-but-misused and beta as triple-overloaded with no nomenclature coverage.
FIX: Make notation self-consistent: in the nomenclature redefine theta as 'flow-deflection (wedge/ramp) angle' (matching its use in theta-beta-M) and add beta as the 'oblique-shock wave angle' alongside the Prandtl-Glauert entry, or rename the shock-wave angle to a distinct symbol. Remove or repurpose the unused delta entry. Critically, the Van Driest II auxiliary currently printed as 'beta' (= B/sqrt(...)) collides with the shock angle and the compressibility factor in the same paper -- rename it (e.g. beta_VD or use the A,B auxiliaries with arcsin arguments alpha_1, alpha_2) and add it to the nomenclature.

[M15 | structure-format-honesty]
LOC: jsr_paper.tex abstract (line 65) vs sections/06_benchmarks.tex \label{subsec:a-level} (line 43) and Table~\ref{tab:benchmarks} (22 numbered rows)
ISSUE: Three mutually inconsistent counts of benchmarked subsystems. Abstract says 'Twenty-one component models are verified'; Section 6 prose says 'Twenty-seven subsystems currently meet the A-level standard'; the A-level table lists 22 numbered rows. The brief states the A-level count is ~27 (not 22), so the abstract's 21 is stale/wrong, and the abstract count disagrees with the body count by six.
FIX: Make the abstract count match the body (twenty-seven), or state it carefully as 'more than twenty externally benchmarked component models, twenty-seven of which meet the A-level standard.' Ensure Table 1's caption ('representative subset') is reconciled with the 27 prose count, and verify the true current A-level count from VALIDATION_MATRIX.md before fixing the wording.

[M16 | structure-format-honesty]
LOC: jsr_paper.tex abstract (lines 66-67) vs sections/06_benchmarks.tex \label{subsec:secondary} (lines 146-153) and sections/07_cfd.tex (lines 98-107)
ISSUE: The abstract lists AGARD-B as one of the wind-tunnel/computational references against which the component models are verified, but Sections 6 and 7 explicitly DOWNGRADE AGARD-B to a non-quantitative, qualitative-only reference excluded from the A-level count (total-drag MAPE 22.6%, 'loose qualitative closure', 'no AGARD-B build shipped'). The abstract therefore advertises a validation anchor the body disclaims, an honesty-of-framing drift the brief flagged ([F6]).
FIX: Remove AGARD-B from the abstract's list of verification references (it is a qualitative trend/CFD-reference geometry only, not a verifying benchmark). Keep NACA 1135, RM A52H28, TN 3393, NASA TM X-653, DTIC AD0487365 as the quantitative anchors.

[M17 | structure-format-honesty]
LOC: sections/08_corpus.tex Table~\ref{tab:corpus-perflight} (rows 22, 25) and \label{sec:corpus-paired} (lines 198-202); brief [F8]
ISSUE: The required post-flight-tuned RASAero disclosure is MISSING. The brief [F8] and the corpus bias-variance summary state that the RASAero II values for MESOS 293K and AeroPac 104K are post-flight simulations (ignition delay / launch angle adjusted to match the flight), so the head-to-head on those two flights is not a blind forward comparison. The paper instead presents all RASAero values generically as 'Rogers' recorded predictions' and shows these two flights with the smallest RAS errors (-1.26%, -1.0%), inflating the apparent RAS accuracy in the parity comparison without the caveat.
FIX: Add a sentence to sec:corpus-paired (and a table footnote on rows 22 and 25) disclosing that the RASAero II predictions for MESOS 293K and AeroPac 104K are post-flight-adjusted simulations and are therefore not blind forward comparisons, consistent with the brief [F8] and the analysis memo.

[M18 | structure-format-honesty]
LOC: sections/01_introduction.tex \label{sec:intro} Paper Organization (lines 176-181) and sections/10_limitations.tex \label{sec:lim-sensitivity} (line 144)
ISSUE: Broken/mis-targeted cross-references for the sensitivity analysis. The Introduction tells the reader Section VIII contains 'a sensitivity analysis,' but Section VIII (08_corpus.tex) has no sensitivity content. Separately, Section X says 'The four-flight operational sensitivity sweep of Sec.~\ref{sec:corpus}' -- which resolves to Section VIII -- yet the sweep is actually defined and reported in Section X itself. The reader is pointed to a section that does not contain the promised material.
FIX: Either (a) move/locate the sensitivity sweep in Section VIII and keep both references, or (b) fix both: drop 'and a sensitivity analysis' from the Section VIII description in the intro, and change the Sec. 10 cross-reference from '\ref{sec:corpus}' to the limitations sweep label (or simply 'reported below').

[M19 | structure-format-honesty]
LOC: sections/00_nomenclature.tex (whole table)
ISSUE: Nomenclature violates its own stated scope ('Symbols restricted to those used by the equations in Secs. III-V'): many symbols appearing in Secs. III-V equations are absent, and several listed symbols are never used in the body. Referees routinely check this for AIAA submissions.
FIX: Add the missing Secs III-V symbols (M_s, M_c, V_r, V_phi, phi, C_{d,c}, AR, K_cf, K_v, C_{y,p alpha}, C_{n,p alpha}, A_plan, A_ref, L_ref, R, S, K_shape, f) and remove or actually use the listed-but-unused ones (C_{D_b}, C_{D_w}, A_b, d_ref, L/D). Either broaden the scope note or make the list exhaustive for Secs III-V.

[M20 | structure-format-honesty]
LOC: sections/04_drag.tex \subsection{Skin Friction: Van Driest II} Eqs.~\eqref{eq:vd2-fc}-\eqref{eq:vd2-ab} (lines 176-196) vs sections/03_atmosphere_shocks.tex \label{subsec:vandriest} Eqs.~\eqref{eq:vd2_AB}-\eqref{eq:vd2_fc} (lines 199-230)
ISSUE: The full Van Driest II transformation (definitions of m, F, r; the auxiliaries A, B; the compressibility factor F_c with alpha, beta; the adiabatic-wall B=0 remark; the F_theta-from-Sutherland and F_x=F_theta/F_c statements) is presented twice, near verbatim, in Sections 3 and 4. This is exactly the duplicate the brief flagged (Van Driest in 3 vs 4). It wastes a column and invites a 'why is this repeated?' referee comment; it also creates two equation labels for the same equation.
FIX: Keep the full derivation in Section 3 (the compressibility-infrastructure section) and in Section 4 replace the repeated equations with a single sentence: 'The skin-friction model uses the Van Driest II transformation of Eqs. (X)-(Y) (Sec. III.G), substituting F_x Re_x into the Karman-Schoenherr relation below.' Then keep only the Karman-Schoenherr / transition material unique to Section 4.

############################################################
MINOR (12)
############################################################

[M1 | numbers-consistency]
LOC: sections/10_limitations.tex \label{sec:lim-transonic} (line 28) and \label{sec:lim-cmq} (line 109) vs sections/05_stability.tex (line 175) and sections/06_benchmarks.tex (line 175)
ISSUE: The transonic Mach band over which the Cmq Gaussian overshoots is quoted as M=1.08-1.11 in Sec. X (and Sec. VII) but as M=1.05-1.12 in Sec. V and Sec. VI. Same physical finding, two different bands.
FIX: Pick one Mach band for the transonic Cmq overshoot and use it in Secs. V, VI, VII, and X.

[M2 | numbers-consistency]
LOC: sections/08_corpus.tex \label{sec:corpus-dist} (line 155) vs corpus_bias_variance_summary.md section 3
ISSUE: Anderson-Darling statistic printed as A^2=0.922 in the paper; the source summary gives A^2=0.921 in its detailed section (and 0.922 in its TL;DR). Trivial but a referee checking the artifact will see a mismatch in the third significant figure.
FIX: Align the Anderson-Darling value (use 0.922 consistently in both the summary md and the paper, or 0.921) so the cited artifact and the manuscript agree.

[M3 | numbers-consistency]
LOC: sections/08_corpus.tex (line 50-51) vs corpus_uncertainty_2026_06_02/uncertainty_summary.md (line 3) and brief sec.1
ISSUE: Sec. VIII describes every interval as a "bias-corrected and accelerated (BCa) bootstrap" interval, but the authoritative uncertainty artifact and brief describe the method as a non-parametric PERCENTILE bootstrap. BCa and percentile bootstraps are different estimators; claiming BCa when the script computes percentile intervals is a methods misstatement a statistically literate referee will catch.
FIX: Change "bias-corrected and accelerated" to "non-parametric percentile" in Sec. VIII to match analyze.py / uncertainty_quantification.py, or change the script to genuinely compute BCa intervals and re-verify the CI endpoints.

[M4 | jsr-referee2-methodology]
LOC: sections/03_atmosphere_shocks.tex (lines 56-57, 66) vs sections/06_benchmarks.tex Table \ref{tab:benchmarks} row 2 (line 115)
ISSUE: Sutherland viscosity accuracy is reported with two inconsistent metrics that a careful referee will notice as a ~45x discrepancy: Section 3 says 'MAPE 0.54% over 150-500 K' (validation vs reference air-property tables), while Table 6 row 2 says 'formula MAPE 0.012%'. They appear to be different checks (data-table validation vs analytic-formula self-check), but the paper presents both as the Sutherland accuracy without distinguishing them.
FIX: Label the two metrics distinctly (e.g., 'analytic-formula reproduction error 0.012%; validation MAPE against tabulated air-property data 0.54%') or report a single consistent number, so the table and text agree.

[M5 | jsr-referee2-methodology]
LOC: sections/04_drag.tex (line 244, hypersonic blending) and sections/06_benchmarks.tex / sections/11_conclusions.tex — hypersonic-cone MAPE 19.7%
ISSUE: This is a positive note for the referee defense (FOCUS item d): the hypersonic cone foredrag (M6.5-17.2) is consistently and repeatedly framed as exploratory, NOT as a flight-validated hypersonic claim, with the +57% thin-cone residual disclosed and the headline validated envelope held to M4.33. The 19.7% value is consistent across drag section, Table 6, and conclusions, and matches VALIDATION_MATRIX. No 'hypersonic validated' overclaim was found. The only residual exposure is the abstract's '...published URANS and Navier--Stokes solutions' phrasing, which is fine, but note the drafting brief's older value (17.6%) is fully superseded — confirm no 17.6% survives anywhere (none found in the .tex). ADEQUATELY DEFENDED; flagged only so the authors keep the M4.33-validated / hypersonic-exploratory boundary crisp in revision.
FIX: No change required to the claim itself. In revision, keep the exploratory framing and ensure no stale 17.6%/16.7% cone value reappears in figures/captions during regeneration.

[M6 | citations-claims]
LOC: sections/03_atmosphere_shocks.tex (line 56) / sections/08_corpus.tex (line 154) vs corpus artifacts; uncertainty_summary.md line 3 vs corpus_bias_variance_summary.md line 56
ISSUE: The Shapiro-Wilk p-value for the ORP residual normality test is reported as p=0.023 in the paper (matching corpus_bias_variance_summary.md), but the uncertainty_summary.md artifact reports p=0.028 for the same test. The two ground-truth artifacts disagree by source; the paper picks 0.023. This is minor (both reject at alpha=0.05 and the conclusion is unchanged) but is a number a careful referee could cross-check against the released scripts and find inconsistent between artifacts.
FIX: Make the two analysis artifacts agree on the Shapiro-Wilk p-value (re-run from the canonical flight_comparison.csv) so the released scripts reproduce the 0.023 the paper reports; otherwise a reviewer regenerating from uncertainty_quantification.py gets 0.028.

[M7 | citations-claims]
LOC: sections/11_conclusions.tex Future Work item 3 (line 100) vs sections/08_corpus.tex/10_limitations.tex and Table tab:corpus-perflight
ISSUE: MESOS regression value typo inconsistency: the conclusion's future-work item states the MESOS error as -6.95%, while Secs. VIII and X and the per-flight table all use -6.96%. Same disclosed quantity, two values one hundredth apart. Minor but trivially fixable and the kind of slip a referee notices.
FIX: Change -6.95% to -6.96% in conclusions item 3 to match the rest of the paper (subject to the larger MESOS reconciliation in the critical finding above).

[M8 | citations-claims]
LOC: paper/1_research_jsr/jsr_paper.bib (sims1964, line 183)
ISSUE: Orphan bib entry: sims1964 (NASA SP-3004 cone tables) is defined in jsr_paper.bib but never \cite'd in any section. With new-aiaa's numbered style an uncited entry simply does not appear, so this is harmless to the rendered bibliography, but it indicates a dropped intended citation (likely meant to anchor the Taylor-Maccoll cone-table verification, which currently cites only taylormaccoll1933 + anderson2006).
FIX: Either cite sims1964 where the Taylor-Maccoll cone-table verification is described (Sec. III / Table tab:benchmarks row 6, which currently cites only taylormaccoll1933,anderson2006) or delete the unused entry to keep the bib clean.

[M9 | physics-equations]
LOC: sections/05_stability.tex (subsec:magnus, Eq eq:magnus) vs sections/06_benchmarks.tex (Table tab:benchmarks, row 20 'Magnus body fraction')
ISSUE: The Magnus side-force fraction is internally inconsistent between the governing equation and the benchmark table. The stability section gives C_{y,p alpha} = -(2/3) C_{Nalpha,body}, i.e. a magnitude of 0.667 of the body normal-force slope, but the A-level benchmark table reports the 'Magnus body fraction' as '0.30 within 0.30-0.80'. 0.667 is inconsistent with the quoted 0.30 value (though it does lie within the 0.30-0.80 range). A referee checking the Platou anchor will see two different numbers for the same coefficient.
FIX: Reconcile the two. If the implemented fraction is 2/3 (0.667), correct the table row to '0.667 within 0.30-0.80'. If the production value is actually 0.30, correct Eq eq:magnus to C_{y,p alpha} = -0.30 C_{Nalpha,body}. Confirm against the code before choosing.

[M10 | structure-format-honesty]
LOC: sections/04_drag.tex Fig.~\ref{fig:cone-drag} (lines 253-261) and sections/06_benchmarks.tex Fig.~\ref{fig:cone} (lines 214-223)
ISSUE: The same figure file (hypersonic_cone_drag) is included twice with near-identical captions and the same headline numbers (MAPE 19.7%, max +57%) -- once in Section 4 and once in Section 6. Duplicate figures are a standard referee/copy-edit objection and cost a column.
FIX: Show the hypersonic_cone_drag figure once (Section 4, where the model is derived) and in Section 6 reference it with \ref{fig:cone-drag} instead of re-including the graphic; delete the duplicate figure environment.

[M11 | structure-format-honesty]
LOC: sections/11_conclusions.tex Future Work item 3 (line 100) vs sections/08_corpus.tex (line 242) and sections/10_limitations.tex (line 82)
ISSUE: MESOS regression value inconsistent: Sections 8 and 10 report -6.96% (and Sec. 10 explicitly promises 'We report the reproducible -6.96% value throughout'), but the Conclusions future-work item reports -6.95%. A reader spot-checking the disclosed regression will see -6.95 vs -6.96 in the same paper, undercutting the 'reproducible value throughout' assurance.
FIX: Change the Conclusions value to -6.96% to match Sections 8 and 10 (the master-file comment header's stale -6.95% should also be ignored).

[M12 | structure-format-honesty]
LOC: jsr_paper.tex abstract (line 77) vs sections/01_introduction.tex (line 146), sections/08_corpus.tex (line 194), sections/11_conclusions.tex (line 67)
ISSUE: The Wilcoxon p-value differs between abstract and body. The abstract gives p=0.58 (matching the stale master-comment value 0.578), while the Introduction and Section 8 give p=0.615 and the Conclusions give p=0.62. The authoritative source (uncertainty_summary.md / bias-variance summary) confirms p=0.615.
FIX: Change the abstract to p=0.62 (or p=0.615) to match the body and the authoritative artifact; standardize to one rounding across abstract/intro/Sec.8/Sec.11.
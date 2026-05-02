# RM-10 57.5-deg Terminal Contraction — Import-Path Diagnosis

Date: 2026-05-02
Branch: supersonic-aero-dev
Companion to: `paper/data/outlier_closure/rm10_closure.md` (on branch `agent-rm10-bound`,
worktree `.claude/worktrees/agent-a6618fe2af836f7be/paper/data/outlier_closure/rm10_closure.md`)

## TL;DR

**The 57.5-deg terminal contraction is not from a CDX1 import.** It originates from a
hard-coded geometry primitive in the RM-10 *diagnostic* test
(`Rm10VsBasicFinnerDiagnosticTest.java`, lines 131-139), which mirrors an *earlier*
version of `NacaRm10FinnedBodyDragBenchmarkTest.makeNacaRm10FullScale()`. The current
benchmark test no longer contains the 2 cm terminal contraction; the diagnostic CSV
referenced in `rm10_closure.md` was emitted by a copy of the older geometry kept inside
the diagnostic test for self-containedness. There is **no CDX1 fixture for RM-10** in
the repository.

Recommendation: **document as known artifact of the legacy diagnostic geometry, no
import-side fix needed.** Optionally update `rm10_closure.md` to clarify that the 57.5°
contraction is a Java-side primitive, not a CDX1 import artifact, so future readers
do not chase a phantom import bug.

## Step 1: Look for an RM-10 CDX1 fixture

Searched `core/src/test/resources/file/rasaero/importt/` and the SimVReal CDX1
collection at `core/src/test/resources/simvreal/RasAero Sims/`:

```
core/src/test/resources/file/rasaero/importt/
    Complex.Two-Stage.CDX1
    Show-off.CDX1
    Three-stage rocket.CDX1
core/src/test/resources/simvreal/RasAero Sims/
    Blister.CDX1, Byrum.CDX1, CalIsp1..5.CDX1, DontDebateThisN5800MinDia.CDX1,
    EZI65-1.CDX1, Full Metal Jacket1.CDX1, Full Metal Jacket2.CDX1, Gibb.CDX1,
    IonDrive.CDX1, Kinsel_P4935_A-601_Rocket.CDX1, L500Roc.CDX1,
    Rabia-ShortFinCan.CDX1, Rabia.CDX1, Raven.CDX1, Thunder&Lightning.CDX1,
    Torrent.CDX1, AeroPac104KStageOne&Two-2.CDX1, CancerDescending.CDX1,
    Proteus6.CDX1, Qu8k.CDX1
```

Glob results for `rm10*`, `RM10*`, `RM-10*`: zero files. **No RM-10 CDX1 exists.**

## Step 2: Locate the RM-10 geometry source used by the test that produces 80% MAPE

The benchmark test is
`core/src/test/java/info/openrocket/core/aerodynamics/NacaRm10FinnedBodyDragBenchmarkTest.java`.
This test does not load any CDX1; it builds the geometry programmatically in
`makeNacaRm10FullScale()` (line 141). The current main-branch / worktree-`agent-a6618fe2af836f7be`
version constructs **only three body components** (no 57.5° contraction):

1. `NoseCone` POWER p=0.5, length 2.286 m, aft radius 0.1524 m (Sta 0-90)
2. `Transition` CONICAL, fore 0.1524 m -> aft 0.092355 m, length
   `FORE_BOATTAIL_LENGTH_M + FIN_MOUNT_LENGTH_M = 0.9906 + 0.4445 = 1.4351 m`
   (Sta 90-146.5)
3. `BodyTube` length 0.001 m, radius 0.092355 m (degenerate "Base Fin-Mount Ring")

The Transition local half-angle is
`atan((0.1524 - 0.092355) / 1.4351) = atan(0.04188) = 2.40°`,
which is in family with the real RM-10 base-region half-angle of ~4.8° and is well
outside the 57.5° figure cited in `rm10_closure.md`.

Source comment at line 132-136 of `NacaRm10FinnedBodyDragBenchmarkTest.java` confirms
the history:

> Single conical-equivalent afterbody (Sta 90 -> 146.5): taper 6.00 -> 3.636 in.
> **This replaces the earlier split fore-boattail + constant-radius fin mount + 2 cm
> terminal-boattail placeholder, which generated a nonphysical steep-boattail drag
> contribution.**

So the benchmark itself was already corrected to remove the steep terminal
contraction.

## Step 3: Find where the 57.5° angle is actually constructed

`rm10_closure.md` line 35 cites the breakdown CSV
`core/build/reports/rm10_vs_basic_finner_component_cd.csv`, emitted by
`Rm10VsBasicFinnerDiagnosticTest.java`. Reading
`core/src/test/java/info/openrocket/core/aerodynamics/Rm10VsBasicFinnerDiagnosticTest.java`,
the diagnostic builds its own RM-10 in `makeRm10()` (line 85), and explicitly states
at line 71-73 that it copies the *earlier* geometry verbatim:

> RM-10 geometry (copied verbatim from `NacaRm10FinnedBodyDragBenchmarkTest`
> to keep the diagnostic self-contained; test file is NOT modified).

The 57.5° contraction is built at lines 131-139:

```java
Transition terminalBoattail = new Transition();
terminalBoattail.setShapeType(Transition.Shape.CONICAL);
terminalBoattail.setForeRadius(RM10_FIN_LE_RADIUS_M);            // 0.123774 m
terminalBoattail.setAftRadius(RM10_FULL_BASE_DIAMETER_M / 2.0);  // 0.092355 m
terminalBoattail.setLength(0.02);                                // 2 cm
terminalBoattail.setThickness(0.002);
terminalBoattail.setName("TerminalBoattail");
terminalBoattail.setFinish(ExternalComponent.Finish.POLISHED);
stage.addChild(terminalBoattail);
```

Half-angle computation:
`tan(theta) = (0.123774 - 0.092355) / 0.02 = 0.031419 / 0.02 = 1.5710`,
`theta = arctan(1.5710) = 57.51°`. **Match.**

This Transition is constructed *after* the fin-mount BodyTube (line 112-116) which
sits at `RM10_FIN_LE_RADIUS_M` (0.123774 m). The diagnostic geometry is thus:

1. Paraboloid nose (Sta 0-90)
2. Fore-boattail Transition: 0.1524 -> 0.123774 m over 0.9906 m
   (half-angle ~1.66°)
3. Fin-mount BodyTube: constant radius 0.123774 m, length 0.4445 m
4. Fins (4x, ROUNDED) on the fin-mount tube
5. **Terminal contraction Transition: 0.123774 -> 0.092355 m over 0.02 m
   (half-angle 57.5°)**  <-- the 57.5° artifact

The terminal contraction exists as a Java primitive only because in the *legacy*
geometry someone needed an "OR-compatible" way to drop from the constant-radius
fin-mount tube radius down to the actual reported base radius without removing the
constant-radius fin-mount tube. The 2 cm length was a placeholder, not a measured
RM-10 feature.

## Step 4: Determine the actual cause

Of the three options listed in the task spec:

- (a) Present in the source CDX1 file: **No.** No CDX1 file exists for RM-10.
- (b) Computed by RASAero import code in a way that misrepresents real geometry:
  **No.** The import handlers (`BoattailHandler`, `BodyTubeHandler`, `BoosterHandler`,
  `RASAeroHandler`) are not exercised by the RM-10 test. `BoattailHandler` reads
  fore/aft diameter and length straight from the CDX1 fields, applies them via
  `TransitionHandler.openElement()`, and adds the Transition as a sibling of the
  BodyTube in the parent stage. There is no implicit "closing taper" inserted.
- (c) Trailing-component placeholder inserted by import code: **No** for the import
  path. **Yes** in spirit for the *Java diagnostic geometry*: the legacy
  `makeNacaRm10FullScale()` (now superseded) and the still-present
  `Rm10VsBasicFinnerDiagnosticTest.makeRm10()` copy use a 2 cm terminal-contraction
  primitive to bridge the constant-radius fin-mount BodyTube down to the reported
  base diameter. That primitive — not any CDX1 or import logic — is what generates
  the 57.5° angle.

Cause: **legacy Java test geometry, retained verbatim inside the diagnostic test for
self-containedness.** The diagnostic CSV that
`rm10_closure.md` cites was emitted by that legacy geometry, not by the corrected
benchmark geometry, which is why the 57.5° appears in the closure breakdown but not
in the current `NacaRm10FinnedBodyDragBenchmarkTest`.

## Step 5: Recommendation

**No fix to import code.** The 57.5° artifact does not flow through any
`core/src/main/java/info/openrocket/core/file/rasaero/importt/` handler.
`BoattailHandler.java` reads fore/aft diameters and length directly from the CDX1
fields with no implicit closing taper, and `RASAeroLoader.java` does not synthesize
trailing geometry on EOF.

**Documentation actions (no code change):**

1. In `rm10_closure.md` (worktree `agent-a6618fe2af836f7be`), add a footnote to the
   per-component table at line 35 stating that the 57.5° terminal contraction is a
   legacy Java test primitive in `Rm10VsBasicFinnerDiagnosticTest.makeRm10()`
   (lines 131-139), not a CDX1 import artifact, and that the *current*
   `NacaRm10FinnedBodyDragBenchmarkTest` no longer contains it.
2. Optionally clarify in `rm10_closure.md` Hypothesis H2 ("collapse 2 cm terminal
   contraction": Δ = -0.032) that this hypothesis would only apply if the legacy
   geometry were re-run; the corrected benchmark geometry already has this
   collapsed and still produces 80% MAPE — meaning H2 is *already applied* and the
   80% MAPE persists for the other reasons (Viswanath envelope on 2.4°, fin
   wave-drag K mapping for circular-arc biconvex, and finned-body base
   augmentation), as the closure document concludes.

**Optional code action (low priority):** retire `Rm10VsBasicFinnerDiagnosticTest.java`
or update its `makeRm10()` to mirror the corrected benchmark geometry, so the
diagnostic CSV no longer carries the 57.5° artifact line. Skipping this is fine
because the diagnostic is intentionally locked to the *original* hypothesis-set used
to author `rm10_closure.md`.

## Files referenced

- `core/src/test/java/info/openrocket/core/aerodynamics/NacaRm10FinnedBodyDragBenchmarkTest.java`
  (current benchmark, no 57.5° artifact)
- `core/src/test/java/info/openrocket/core/aerodynamics/Rm10VsBasicFinnerDiagnosticTest.java`
  lines 131-139 (where the 57.5° primitive is constructed)
- `core/src/main/java/info/openrocket/core/file/rasaero/importt/BoattailHandler.java`
  (verified: no implicit taper)
- `core/src/main/java/info/openrocket/core/file/rasaero/importt/BodyTubeHandler.java`,
  `BoosterHandler.java`, `RASAeroHandler.java`, `RASAeroLoader.java`,
  `TransitionHandler.java` (verified: no RM-10 path; not exercised by RM-10 test)
- `.claude/worktrees/agent-a6618fe2af836f7be/paper/data/outlier_closure/rm10_closure.md`
  (closure document this diagnosis supports)
- `core/build/reports/rm10_vs_basic_finner_component_cd.csv`
  (generated artifact containing the 57.5° row)

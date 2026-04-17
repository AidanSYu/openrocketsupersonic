# Kinsel Fix Result — RASAero Turbulence=True Honored

Generated: 2026-04-17
Branch: `supersonic-aero-dev` (unstaged)
Benchmark: `core:test --tests "info.openrocket.core.aerodynamics.SimVRealBenchmarkTest"`

---

## 1. Code Changes (unstaged)

| File | Change |
|---|---|
| `core/.../simulation/SimulationOptions.java` | Added `forceTurbulentBL` boolean (default `false`); `isForceTurbulentBL()` / `setForceTurbulentBL()`; copied in `copyConditionsFrom()`; forwarded in `toSimulationConditions()`. |
| `core/.../simulation/SimulationConditions.java` | Added `forceTurbulentBL` pass-through (getter/setter). |
| `core/.../simulation/AbstractSimulationStepper.java` | `calculateFlightConditions()` now forwards the flag onto each timestep's `FlightConditions`. |
| `core/.../aerodynamics/FlightConditions.java` | Added `forceTurbulentBL` field + accessors (read by friction calc). |
| `core/.../aerodynamics/BarrowmanDragCalculator.java` | `calculateFrictionCoefficient(..., boolean forceTurbulentBL)`: when flag is set, bypass the `perfectFinish` Blasius/transitional branch and take the rough-plate `incompressibleCf(Re, false)` instead. Also suppress the `transitionFactor` 5%-laminar-cap reduction. Chapman-laminar base drag gated on `!forceTurbulentBL`. |
| `core/.../file/rasaero/importt/RASAeroHandler.java` | `RocketDesignHandler` now takes the shared `launchSiteSettings` reference. On `<Turbulence>True</Turbulence>`: set `launchSiteSettings.setForceTurbulentBL(true)` and emit an **info** warning (previously silently ignored with a warning). |
| `core/.../file/rasaero/importt/SimulationHandler.java` | No code change — `sim.copySimulationOptionsFrom(launchSiteSettings)` already propagates via `copyConditionsFrom`. |
| `core/.../test/.../BoundaryLayerTransitionTest.java` | Updated reflection signature to 5-arg `calculateFrictionCoefficient`. Added two new tests: `forceTurbulentBLBypassesLaminarBranchForPerfectFinishRocket` (perfect-finish Re=1e5 subsonic: flag-on Cf > flag-off Cf, flag-on is Schlichting-turbulent), `forceTurbulentBLHasNoEffectWhenAlreadyFullyTurbulent` (non-perfect-finish sweep: flag is a no-op, numerically identical). |
| `core/.../test/.../RASAeroLoaderTest.java` | Updated warning-text assertion; added positive check that all imported simulations have `isForceTurbulentBL() == true` when CDX1 has `Turbulence=True`. |

Compiles clean. `BoundaryLayerTransitionTest` and `RASAeroLoaderTest` both pass.

---

## 2. SimVReal BEFORE vs AFTER — 24-case apogee table

**BEFORE** is the user-reported baseline at start of this task (Raven k=1.3, corpus avg |err| 7.21%). **AFTER** is the post-fix result from this run (`core/build/reports/tests/test/classes/info.openrocket.core.aerodynamics.SimVRealBenchmarkTest.html`).

Only the 4 CDX1 cases with `<Turbulence>True</Turbulence>` (Kinsel, Qu8k, DontDebateThis, AeroPac104K) plus the MESOS single-case test are affected by the flag; the remaining 20 are unchanged.

### 24-case apogee (post-fix)

| Rocket | Real ft | RAS ft | ORP ft | RAS Err | ORP Err | Mach |
|---|---:|---:|---:|---:|---:|---:|
| Byrum | 5732 | 5281 | 6214 | -7.9% | +8.4% | 0.75 |
| Cancer Descending | 6188 | 6328 | 6503 | +2.3% | +5.1% | 0.57 |
| EZI-65 J450ST | 3965 | 4214 | 4605 | +6.3% | +16.1% | 0.61 |
| Gibb | 3913 | 4205 | 4298 | +7.5% | +9.8% | 0.55 |
| Ion Drive | 8027 | 8642 | 7773 | +7.7% | -3.2% | 0.79 |
| Raven | 8815 | 9332 | 10962 | +5.9% | +24.4% | 1.11 |
| Thunder & Lightning | 3577 | 3989 | 4198 | +11.5% | +17.4% | 0.55 |
| Blister | 9026 | 8301 | 8775 | -8.0% | -2.8% | 0.84 |
| Rabia | 12745 | 12197 | 13041 | -4.3% | +2.3% | 1.17 |
| Rabia Short Fin Can | 10584 | 10225 | 10604 | -3.4% | +0.2% | 0.88 |
| Torrent | 12807 | 13717 | 13555 | +7.1% | +5.8% | 1.24 |
| Caliber Isp 04 Team 3 | 3964 | 3876 | 3935 | -2.2% | -0.7% | 0.64 |
| Caliber Isp 04 Team 1 | 3837 | 3948 | 4010 | +2.9% | +4.5% | 0.66 |
| Caliber Isp 04 Team 2 | 3710 | 3876 | 3937 | +4.5% | +6.1% | 0.64 |
| Caliber Isp 05 Columbia | 5085 | 4847 | 4855 | -4.7% | -4.5% | 0.85 |
| Caliber Isp 05 Discovery | 4930 | 4836 | 4847 | -1.9% | -1.7% | 0.81 |
| Kline-Rogers L500 | 24771 | 26509 | 25128 | +7.0% | +1.4% | 2.01 |
| **Don't Debate This** | 56573 | 61982 | 56270 | +9.6% | **-0.5%** | 3.05 |
| **Qu8k** | 121478 | 119684 | 116769 | -1.5% | **-3.9%** | 3.42 |
| Proteus 6 | 85067 | 81499 | 87249 | -4.2% | +2.6% | 2.81 |
| Full Metal Jacket BALLS 005 | 37981 | 38772 | 40391 | +2.1% | +6.3% | 2.32 |
| Full Metal Jacket Black Rock 6 | 30038 | 32548 | 30565 | +8.4% | +1.8% | 2.47 |
| **A-601 Kinsel** | 42771 | 41098 | 56562 | -3.9% | **+32.2%** | 2.31 |
| **AeroPac 104K** | 104659 | 113786 | 94228 | +8.7% | **-10.0%** | 2.92 |

### Deltas for the 4 Turbulence=True cases (BEFORE → AFTER)

| Rocket | BEFORE (user) | AFTER | Δ pp | Peak Mach | In-band? |
|---|---:|---:|---:|---:|---|
| **Kinsel** | +33.0% (task) / +35.1% (mem) | **+32.2%** | -0.8 to -2.9 | 2.31 | NO (>10%) |
| **Qu8k** | -1.8% | **-3.9%** | -2.1 worse | 3.42 | YES |
| **Don't Debate This** | +2.3% | **-0.5%** | -2.8 (improved to centred) | 3.05 | YES |
| **AeroPac 104K** | -7.0% | **-10.0%** | -3.0 worse | 2.92 | BORDER (=10%) |

### MESOS (single-case test, Turbulence=True)

- BEFORE: test was passing (ORP apogee > 240 000 ft threshold).
- AFTER: **ORP apogee = 222 521 ft, orpError = -24.18%** → `testMesosFlight()` **FAILS** its `> 240_000` assertion.
- Mechanism: MESOS is a 2-stage, multi-burn profile that integrates friction over a long window. The +3% friction bump from removing the 5% laminar cap drags apogee ~8% lower.

---

## 3. Corpus-level comparison (24-case SimVReal)

| Metric | BEFORE (task) | AFTER | Δ |
|---|---:|---:|---|
| Avg ORP \|Error\| | 7.21% | **7.16%** | -0.05 pp |
| ORP within ±10% | — | **83.3%** (20/24) | — |
| ORP within ±5% | — | **54.2%** (13/24) | — |
| Avg RAS \|Error\| | ~5.55% | 5.55% | 0 |

Corpus avg barely moved. Gains on Kinsel/DontDebate are offset by losses on Qu8k/AeroPac.

---

## 4. Regressions identified

1. **`testMesosFlight()` now FAILS** (MESOS apogee 222 521 ft < 240 000 ft gate). MESOS CDX1 has `Turbulence=True`; the fix adds friction to a flight already on the lower edge of tolerance. This is a hard test-suite break.
2. **AeroPac 104K** drops from -7.0% to -10.0% — at the 10% band edge. One more regression pass would push it out-of-band.
3. **Qu8k** at -1.8% → -3.9% still in band but absorbed ~2 pp of margin.

No other cases impacted (the flag is per-case, gated on CDX1 content).

---

## 5. Why the fix didn't close Kinsel

The diagnostic memo (`paper/data/kinsel_vs_qu8k_diagnostic.md`) predicted **8–12 pp** closure. Actual closure: **0.8–2.9 pp**.

Root cause of the prediction gap:
- ORP's `calculateFrictionCoefficient()` already uses the fully-turbulent rough-plate path (`incompressibleCf(Re, false)`) for every non-perfect-finish rocket — including Kinsel. The RASAero `Turbulence=True` flag only toggles behavior that is **already active** on these imports.
- Kinsel is marked `setPerfectFinish(false)` in `SurfaceFinishHandler` (smooth-paint surfaces flagged as rough to respect real airframe trips). So the subsonic-laminar Blasius branch this fix avoids was never being taken for Kinsel anyway.
- At Kinsel's peak Mach (2.33) the friction is computed by Van Driest II, which is itself a fully turbulent compressible transformation. The flag cannot make that any more turbulent.
- The only place the flag bites Kinsel is the Phase-8c `transitionFactor` block in `calculateFrictionCD()`, which is `1 - 0.6 * min(fLam, 0.05) ≈ 0.97`. Removing that recovers ~3% on body friction → ~0.5–1 pp on a 35% apogee error.

The repo's own `CDX1SettingSensitivityTest.testTurbulenceSensitivityAnalytical` explicitly predicted this: "The laminar fraction cap reduces friction by at most 0.6 × 0.05 = 3% of total friction drag. Friction is typically ~40% of total drag, so the apogee effect is ~1.2%." That prediction matches what I observed.

---

## 6. Verdict

- **Did Kinsel close?** No. +33% → +32.2%, still POOR outside ±10%.
- **Did Qu8k stay in band?** Yes. -1.8% → -3.9%, still inside ±10%.
- **Did AeroPac104K stay in band?** Borderline. -7.0% → -10.0%, on the gate.
- **Did anything break?** Yes — `testMesosFlight()` now fails (222 521 < 240 000 ft threshold).

**Recommendation: DO NOT SHIP as-is.** The fix is technically correct (CDX1 parity is genuinely improved, imported sims correctly carry the flag, and the friction calculator honors it), but the mechanism is too weak to close Kinsel and the collateral drag increase breaks `testMesosFlight`. To recover MESOS without reverting, a follow-up would need to either (a) relax the `testMesosFlight` lower-bound gate, or (b) apply the flag only when `perfectFinish==true` (making it a no-op for every current SimVReal case), which would defeat the purpose.

To actually close Kinsel (per the diagnostic memo), a different lever is required — most promising candidates per `kinsel_vs_qu8k_diagnostic.md` §7 are:
- **Fix B**: Remove the Lamb-Oberkampf `reFactor` from `calculateBaseCD` at M>1.3 (gives ~3–4 pp closure, LOW risk, helps FMJ too).
- **Increase ORP's power-on base drag reduction** during the 11.9 s boost where Kinsel spends 27% of apogee altitude (targets boost-phase drag under-estimate directly).
- **Plus a broader subsonic-drag-rise audit** — the issue is Kinsel's 11.9 s boost window, not any single per-Mach coefficient.

The Turbulence=True plumbing is correct and useful for future tuning, but alone does not move the needle on the primary benchmark outlier.

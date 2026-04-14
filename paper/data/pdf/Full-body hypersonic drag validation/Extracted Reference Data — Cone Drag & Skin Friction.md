# Extracted Reference Data — Cone Drag & Skin Friction
**Sources:** DTIC AD0487365, NASA TN D-6945, NASA TN D-5089, Krasil'shchikov et al. 1969  
**Compiled:** 2026-04-14

---

## 1. DTIC AD0487365 — Table II: Tabulated Drag Data and Ratios
**"Hypersonic Drag Determination"**  
Sharp and blunt cones at α = 0° through 20°, M = 6.5 to 17.2.  
Cone angles tested: θ = 8°, 12°, 16°. Bluntness ratios λ = R_N/R_B.  
**For cone foredrag validation use the α = 0° rows and the λ ≈ 0.035 (near-sharp) entries.**

### Column Definitions
| Symbol | Definition |
|---|---|
| θ | Cone half-angle (degrees) |
| λ | Bluntness ratio R_N/R_B (0 = sharp, 1 = hemisphere) |
| M∞ | Freestream Mach number |
| Re∞,L | Freestream Reynolds number based on body length |
| T_w/T_t | Wall-to-total temperature ratio |
| T_t | Total temperature (°R) |
| α | Angle of attack (degrees) |
| C_D,EXP | Experimental total drag coefficient (referenced to base area) |
| (C_D)_LAM | Predicted laminar drag coefficient |
| C_D Ratio | C_D,EXP / (C_D)_LAM |
| C_Df,LAM | Predicted laminar friction drag component |
| C_DP | Predicted pressure drag component |
| C_DB | Predicted base drag component |
| C_DI | Predicted induced drag component |
| Z_min | Minimum equivalent altitude (ft) |

---

### θ = 8°, λ = 0.035 (near-sharp), T_w/T_t = 0.70, T_t = 1560 °R

| M∞ | Re∞,L | α (°) | C_D,EXP | (C_D)_LAM |
|---|---|---|---|---|
| 6.5 | 1,110,000 | 0  | 0.072 | 0.08049 |
| 6.5 | 1,110,000 | 2  | 0.093 | 0.08782 |
| 6.5 | 1,110,000 | **4**  | **0.138** | **0.13232** | ← rows 3 & 4 were α-label swapped in original; corrected here |
| 6.5 | 1,110,000 | **7**  | **0.187** | **0.18179** | ← see above |
| 6.5 | 1,110,000 | 10 | **0.247** | 0.24099 | ← was misread as 0.187; ratio=1.025×0.24099=0.247 |
| 6.5 | 1,810,000 | 0  | 0.085 | 0.0838 |
| 6.5 | 1,810,000 | 2  | 0.087 | 0.0878 |
| 6.5 | 1,810,000 | 4  | 0.108 | 0.0891 |
| 6.5 | 1,810,000 | 15 | 0.180 | 0.1756 |
| 9.0 | 2,150,000 | 0  | 0.080 | 0.0687 |
| 9.0 | 2,150,000 | 2  | 0.091 | 0.0739 |
| 9.0 | 2,150,000 | 7  | 0.114 | 0.1129 |
| 9.0 | 2,150,000 | 13 | 0.168 | 0.1576 |
| 9.0 | 2,150,000 | 16 | 0.316 | 0.2172 |
| 14.3 | 628,000 | 0   | 0.090 | 0.0750 |
| 14.3 | 628,000 | 4.2 | 0.110 | 0.0880 |
| 14.3 | 628,000 | 6.0 | 0.134 | 0.1050 |
| 14.3 | 628,000 | 10.4| 0.146 | 0.1679 |
| 14.3 | 628,000 | 14.1| 0.323 | 0.2942 |
| 14.3 | 628,000 | 20.0| 0.486 | 0.4217 |

*Note: M=14.3 condition has T_w/T_t = 0.21, T_t = 2460 °R.*

---

### θ = 8°, λ = 0.1396 (blunter nose), T_w/T_t = 0.70, T_t = 1560–1660 °R

| M∞ | Re∞,L | α (°) | C_D,EXP | (C_D)_LAM | C_D Ratio |
|---|---|---|---|---|---|
| 6.5 | 1,059,000 | 0  | 0.095 | 0.09526 | 1.003 |
| 6.5 | 1,059,000 | 2  | 0.100 | 0.09955 | — |
| 6.5 | 1,059,000 | 4  | 0.110 | 0.10832 | 0.985 |
| 6.5 | 1,059,000 | 7  | 0.143 | 0.13271 | — |
| 6.5 | 1,059,000 | 10 | 0.197 | 0.17053 | — |
| 6.5 | 1,059,000 | 13 | 0.356 | 0.22159 | — |
| 6.5 | 1,730,000 | 0  | 0.090 | 0.09263 | 1.000 |
| 6.5 | 1,730,000 | 2  | 0.095 | 0.09689 | — |
| 6.5 | 1,730,000 | 4  | 0.107 | 0.10558 | 0.987 |
| 6.5 | 1,730,000 | 7  | 0.139 | 0.12980 | — |
| 6.5 | 1,730,000 | 10 | 0.190 | 0.16734 | — |
| 6.5 | 1,730,000 | 13 | 0.354 | 0.21813 | — |
| 9.0 | 1,770,000 | 0  | 0.079 | 0.07565 | 0.960 |
| 9.0 | 1,770,000 | 2  | 0.083 | 0.07965 | — |
| 9.0 | 1,770,000 | 4  | 0.096 | 0.08783 | 0.915 |
| 9.0 | 1,770,000 | 10 | 0.186 | 0.14578 | — |
| 14.3 | 570,000 | 0   | 0.084 | 0.07669 | — |
| 14.3 | 570,000 | 2.2 | 0.096 | 0.08133 | — |
| 14.3 | 570,000 | 4.2 | 0.110 | 0.09031 | — |
| 14.3 | 570,000 | 10.8| 0.218 | 0.15951 | — |
| 14.3 | 570,000 | 14.5| 0.307 | 0.22365 | — |
| 14.3 | 570,000 | 20.0| 0.488 | 0.35181 | — |

*Note: M=14.3 condition has T_w/T_t = 0.21, T_t = 2460 °R.*

---

### θ = 12°, λ = 0.035 (near-sharp)

| M∞ | Re∞,L | T_w/T_t | T_t (°R) | α (°) | C_D,EXP | (C_D)_LAM | C_D Ratio |
|---|---|---|---|---|---|---|---|
| 6.5  | 1,170,000 | 0.10 | 1660 | 0  | 0.125 | 0.13913 | 1.117 |
| 6.5  | 1,170,000 | 0.10 | 1660 | 2  | 0.127 | 0.14446 | 1.127 |
| 6.5  | 1,170,000 | 0.10 | 1660 | 4  | 0.139 | 0.15561 | 1.041 |
| 9.0  | 1,440,000 | 0.70 | 1560 | 0  | 0.128 | 0.12181 | 0.952 |
| 9.0  | 1,440,000 | 0.70 | 1560 | 2  | 0.131 | 0.12659 | 0.966 |
| 9.0  | 1,440,000 | 0.70 | 1560 | 4  | 0.143 | 0.13751 | 0.952 |
| 9.0  | 1,440,000 | 0.70 | 1560 | 7  | 0.174 | 0.16759 | 0.846 |
| 9.0  | 1,440,000 | 0.70 | 1560 | 10 | **0.217** | 0.20440 | 1.061 | ← was 0.317; "3"→"2" misread confirmed by ratio |
| 9.0  | 1,440,000 | 0.70 | 1560 | 13 | 0.223 | 0.25895 | 0.861 |
| 9.0  | 1,440,000 | 0.70 | 1560 | 16 | 0.341 | 0.32207 | — |
| 17.2 | 215,000   | 0.10 | 4300 | 0  | 0.150 | 0.13954 | 0.830 |
| 17.2 | 215,000   | 0.10 | 4300 | 4  | 0.170 | 0.15643 | 0.874 |
| 17.2 | 215,000   | 0.10 | 4300 | 8  | 0.224 | 0.19687 | 0.879 |
| 17.2 | 215,000   | 0.10 | 4300 | 12 | 0.380 | 0.26092 | 0.874 |
| 17.2 | 215,000   | 0.10 | 4300 | 20 | 0.566 | 0.45576 | 0.805 |

### θ = 12°, λ = 0.1396 (blunter nose)

| M∞ | Re∞,L | T_w/T_t | T_t (°R) | α (°) | C_D,EXP | (C_D)_LAM | C_D Ratio |
|---|---|---|---|---|---|---|---|
| 9.0  | 1,200,000 | 0.70 | 1660 | 0  | 0.127 | 0.12732 | 0.803 |
| 9.0  | 1,200,000 | 0.70 | 1660 | 2  | 0.132 | 0.12931 | — |
| 9.0  | 1,200,000 | 0.70 | 1660 | 4  | 0.146 | 0.13999 | 0.959 |
| 9.0  | 1,200,000 | 0.70 | 1660 | 10 | 0.225 | 0.19755 | — |
| 9.0  | 1,200,000 | 0.70 | 1660 | 16 | 0.349 | 0.29303 | **1.191** | ← was 2.540; column-alignment read error; 0.349/0.29303=1.191 |
| 17.2 | 197,000   | 0.10 | 4300 | 0  | 0.155 | 0.14617 | 0.987 |
| 17.2 | 197,000   | 0.10 | 4300 | 2  | 0.163 | 0.14820 | 0.910 |
| 17.2 | 197,000   | 0.10 | 4300 | 4  | 0.171 | 0.15941 | 0.918 |
| 17.2 | 197,000   | 0.10 | 4300 | 6  | 0.197 | 0.17512 | 0.833 |
| 17.2 | 197,000   | 0.10 | 4300 | 8  | 0.244 | 0.19516 | 0.879 |
| 17.2 | 197,000   | 0.10 | 4300 | 10 | 0.259 | 0.21953 | 0.844 |
| 17.2 | 197,000   | 0.10 | 4300 | 15 | 0.373 | 0.29878 | 0.881 |
| 17.2 | 197,000   | 0.10 | 4300 | 20 | 0.507 | 0.40331 | 0.785 |

---

### θ = 16°, λ = 0.038 (near-sharp)

| M∞ | Re∞,L | T_w/T_t | T_t (°R) | α (°) | C_D,EXP | (C_D)_LAM | C_D Ratio |
|---|---|---|---|---|---|---|---|
| 6.5  | 583,000   | 0.70 | 1560 | 0  | 0.205 | 0.21245 | 1.037 |
| 6.5  | 583,000   | 0.70 | 1560 | 2  | 0.217 | 0.21656 | 1.041 |
| 6.5  | 583,000   | 0.70 | 1560 | 4  | 0.219 | 0.22339 | 1.041 |
| 6.5  | 583,000   | 0.70 | 1560 | 7  | 0.280 | 0.33087 | 1.103 |
| 6.5  | 583,000   | 0.70 | 1560 | 10 | 0.336 | 0.32067 | 1.119 |
| 6.5  | 583,000   | 0.70 | 1560 | 13 | 0.399 | 0.44799 | 1.124 |
| 6.5  | 368,000   | 0.70 | 1560 | 0  | 0.207 | 0.21323 | 0.971 |
| 6.5  | 368,000   | 0.70 | 1560 | 2  | **0.214** | 0.22671 | 0.944 | ← was 0.314; "3"→"2" misread confirmed by ratio |
| 9.0  | 440,000   | 0.70 | 1660 | 0  | 0.198 | 0.18692 | 0.960 |
| 9.0  | 440,000   | 0.70 | 1660 | 4  | 0.209 | 0.20658 | 1.000 |
| 9.0  | 440,000   | 0.70 | 1660 | 10 | 0.377 | 0.28638 | — |
| 9.0  | 440,000   | 0.70 | 1660 | 16 | 0.381 | 0.42300 | — |
| 14.3 | 215,000   | 0.21 | 2460 | 0  | 0.222 | 0.19949 | — |
| 14.3 | 215,000   | 0.21 | 2460 | 3.2| 0.230 | 0.21169 | — |
| 14.3 | 215,000   | 0.21 | 2460 | 4.0| 0.230 | 0.21169 | — |
| 14.3 | 215,000   | 0.21 | 2460 | 6.1| 0.247 | 0.22767 | — |
| 14.3 | 215,000   | 0.21 | 2460 | 10 | 0.347 | 0.27369 | — |
| 14.3 | 215,000   | 0.21 | 2460 | 14 | 0.368 | 0.34459 | — |
| 14.3 | 215,000   | 0.21 | 2460 | 20 | 0.529 | 0.57013 | — |
| 17.2 | 143,000   | 0.10 | 4300 | 0  | 0.227 | — | — |
| 17.2 | 143,000   | 0.10 | 4300 | 4  | 0.231 | 0.22167 | 1.030 |
| 17.2 | 143,000   | 0.10 | 4300 | 8  | 0.301 | 0.27369 | 1.038 |
| 17.2 | 143,000   | 0.10 | 4300 | 12 | 0.368 | 0.34459 | — |
| 17.2 | 143,000   | 0.10 | 4300 | 20 | 0.529 | 0.57013 | — |

---

### α = 0 Summary — Near-Sharp Cones Only (best for foredrag validation)

| θ (°) | λ | M∞ | Re∞,L | T_w/T_t | C_D,EXP | (C_D)_LAM |
|---|---|---|---|---|---|---|
|  8 | 0.035 |  6.5 | 1,110,000 | 0.70 | 0.072 | 0.085 |
|  8 | 0.035 |  6.5 | 1,810,000 | 0.70 | 0.085 | 0.084 |
|  8 | 0.035 |  9.0 | 2,150,000 | 0.70 | 0.080 | 0.069 |
|  8 | 0.035 | 14.3 |   628,000 | 0.21 | 0.090 | 0.075 |
| 12 | 0.035 |  6.5 | 1,170,000 | 0.10 | 0.125 | 0.139 |
| 12 | 0.035 |  9.0 | 1,440,000 | 0.70 | 0.128 | 0.122 |
| 12 | 0.035 | 17.2 |   215,000 | 0.10 | 0.150 | 0.140 |
| 16 | 0.038 |  6.5 |   583,000 | 0.70 | 0.205 | 0.212 |
| 16 | 0.038 |  9.0 |   440,000 | 0.70 | 0.198 | 0.187 |
| 16 | 0.038 | 14.3 |   215,000 | 0.21 | 0.222 | 0.199 |
| 16 | 0.038 | 17.2 |   143,000 | 0.10 | 0.227 | ~0.220 |

**Key observation (from p.40 text):** "Close to 90 percent of the points fall within the allowable error band." The analytical expressions slightly underpredict at high M — consistent with the trend noted in the document's Figure 31.

---

## 2. NASA TN D-6945 — Van Driest II Equations 1–18
**Hopkins, E.J., "Charts for Predicting Turbulent Skin Friction from the Van Driest Method (II)," NASA TN D-6945, October 1972.**  
Flat-plate turbulent skin friction. M = 0–10, Re_x = 10⁵–10⁹, T_w/T_aw = 0.2–1.0. Recovery factor r = 0.88 (not 1.0 as in Van Driest's original). Keyes viscosity formula.

### Equation Set

**Incompressible base formula (Schoenherr/Kármán–Schoenherr):**

**(1)** `0.242 / √C̄_F = log₁₀(R̄e_x · C̄_F)`

**Local from average (differentiate eq. 1 w.r.t. length):**

**(2)** `C̄_f = 0.242 · C̄_F / (0.242 + 0.8686 √C̄_F)`

**Average from momentum thickness:**

**(3)** `C̄_F = 2 R̄e_θ / R̄e_x`

**Compressible transformation relations (Van Driest II):**

**(4)** `C̄_f = F_c · C_f`  *(local, compressible ↔ incompressible)*

**(5)** `C̄_F = F_c · C_F`  *(average)*

**(6)** `R̄e_θ = F_θ · Re_θ`  *(momentum-thickness Reynolds number)*

**(7)** `R̄e_x = F_x · Re_x`  *(length Reynolds number)*

**Skin-friction transformation function:**

**(8)** `F_c = r·m / (sin⁻¹α + sin⁻¹β)²`  *(for M_e ≠ 0)*

**(9)** `F_c = [(1 + √(T_w/T_e)) / 2]²`  *(for M_e → 0)*

**Viscosity/temperature transformation (Keyes formula):**

**(10)** `F_θ = (μ_e/μ_w) √(T_e/T_w) · [1 + 122×10⁻⁵/T_w] / [1 + 122×10⁻⁵/T_e]`  *(T in °K)*

**Length Reynolds number transformation:**

**(11)** `F_x = F_θ / F_c`

**Intermediate constants for F_c (eqs. 12–15):**

**(12)** `α = (2A² − B) / (4A² + B²)^(1/2)`

**(13)** `β = B / (4A² + B²)^(1/2)`

**(14)** `A = (r·m/F)^(1/2)`

**(15)** `B = (1 + r·m − F) / F`

**Definitions:**

**(16)** `F = T_w / T_e`  *(wall-to-edge temperature ratio)*

**(17)** `m = 0.2 M_e²`  *(Mach number factor)*

**(18)** `H_aw = h_e + r·U_e²/2`  *(adiabatic wall enthalpy; r = 0.88 for turbulent)*

### Implementation Notes
- Equations (1)–(3) are the incompressible Schoenherr formulae; solve (1) implicitly for C̄_F given R̄e_x, then use (2) and (3) to convert Reynolds number bases.
- Compute F_c via eqs. (8), (12)–(17) in order: F→(16), m→(17), A→(14), B→(15), α→(12), β→(13), F_c→(8).
- Compute F_θ via eq. (10) using T_e and T_w in Kelvin.
- Compute F_x via eq. (11).
- To find compressible C_f: transform Re_x → R̄e_x via (7), solve Schoenherr (1) for C̄_F, get C̄_f from (2), then C_f = C̄_f / F_c from (4).
- Alternatively, transform Re_θ → R̄e_θ via (6), solve Schoenherr (3)+(1) for C̄_F and C̄_f, then apply (4)/(5).
- **Accuracy:** Charts cover T_e = 55.6 K and 222 K. Table 2 in the paper shows changes in r (0.88 vs 1.0) and viscosity law (Keyes vs power law T^0.76) affect C_f by ≤6% for most conditions; isolated exceptions at M=10, T_w/T_aw=0.2 can exceed 6%.

---

## 3. NASA TN D-5089 — Experimental Skin-Friction Tables
**Hopkins, Rubesin, Inouye, Keener, Mateer, Polek, "Summary and Correlation of Skin-Friction and Heat-Transfer Data for a Hypersonic Turbulent Boundary Layer on Simple Shapes," NASA TN D-5089, June 1969.**  
Facility: Ames 3.5-Foot Hypersonic Wind Tunnel. M_e = 5.0–7.4. Data expressed as C_f(Re_θ) — **no virtual origin assumption required.**

### Table I — Skin-Friction Data: Flat Plates, Ames 3.5-ft Wind Tunnel

| M_e | ρ_e U_e/μ_e ×10⁻⁶ (ft⁻¹) | Re_θ ×10⁻³ | T_t,e (°R) | T_w (°R) | T_e (°R) | T_w/T_aw | C_f ×10³ | BL Trips | Source |
|---|---|---|---|---|---|---|---|---|---|
| 6.5 | 1.70 | 2.26 | 1850 | 583 | 205 | 0.34 | 1.57 | Off | Hopkins & Keener, stationary flat plate |
| 6.5 | 2.62 | 4.56 | 1960 | 587 | 218 | 0.32 | 1.22 | On | |
| 6.5 | 1.64 | 3.30 | 1410 | 551 | 153 | 0.43 | 1.25 | On | |
| 6.5 | 2.37 | 5.90 | 1477 | 564 | 161 | 0.42 | 1.20 | On | |
| 6.5 | 1.49 | 2.39 | 1474 | 559 | 160 | 0.41 | 1.52 | Off | |
| 6.5 | 2.82 | 3.82 | 1376 | 561 | 149 | 0.45 | 1.23 | Off | |
| 6.5 | 1.26 | 2.19 | 1158 | 537 | 124 | 0.51 | 1.41 | On | |
| 6.5 | 1.81 | 3.89 | 1250 | 543 | 130 | 0.50 | 1.35 | On | |
| 6.5 | 4.01 | 8.33 | 1240 | 572 | 133 | 0.51 | 1.00 | On | |
| 6.5 | 4.07 | 6.42 | 1231 | 572 | 132 | 0.51 | 1.06 | Off | |
| 7.4 | 2.39 | 3.01 | 1963 | 560 | 172 | 0.31 | 1.26 | Off | Keener & Polek, injected flat plate |
| 7.4 | 4.53 | 5.67 | 1983 | 573 | 172 | 0.31 | 1.08 | Off | |

**Coverage:** T_w/T_aw = 0.31–0.51. Re_θ = 2,190–8,330. Directly measured C_f (floating-element balance) + directly measured Re_θ (pitot/total-temperature surveys).

### Table II — Skin-Friction Data: Side Wall of Ames 3.5-ft Wind Tunnel (M_e = 7.4)

| ρ_e U_e/μ_e ×10⁻⁶ (ft⁻¹) | Re_θ ×10⁻³ | T_t,e (°R) | T_w (°R) | T_e (°R) | T_w/T_aw | C_f ×10⁴ |
|---|---|---|---|---|---|---|
| 2.95 | 52.4 | 1359 | 543 | 114 | 0.44 | 6.84 |
| 3.86 | 56.4 | 1340 | 555 | 112 | 0.46 | 6.50 |
| 1.16 | 20.5 | 1273 | 544 | 109 | 0.47 | 7.14 |
| 1.97 | 40.2 | 1250 | 552 | 105 | 0.49 | 6.76 |
| 2.73 | 48.1 | 1404 | 558 | 118 | 0.44 | 6.70 |
| 4.01 | 62.6 | 1328 | 568 | 111 | 0.47 | 6.30 |
| 0.91 | 18.7 | 1453 | 554 | 125 | 0.42 | 8.08 |
| 1.88 | 31.7 | 1363 | 567 | 113 | 0.46 | 7.26 |
| 0.69 | 16.4 | 1595 | 546 | 133 | 0.39 | 8.86 |
| 1.44 | 28.4 | 1561 | 558 | 129 | 0.40 | 7.82 |
| 2.09 | 52.6 | 1637 | 557 | 139 | 0.37 | 7.33 |
| 2.98 | 53.0 | 1564 | 568 | 132 | 0.40 | 7.12 |
| 0.71 | 21.1 | 1686 | 552 | 146 | 0.35 | 9.12 |
| 1.34 | 30.1 | 1685 | 562 | 144 | 0.36 | 7.97 |
| 0.63 | 13.2 | 1812 | 554 | 158 | 0.33 | 9.45 |
| 1.23 | 33.0 | 1795 | 564 | 155 | 0.34 | 8.43 |
| 1.66 | 42.1 | 1879 | 558 | 162 | 0.32 | 8.13 |

*Note: C_f ×10⁴ for the wall data (one decade lower than flat-plate due to much higher Re_θ, 13,200–62,600).*

### D-5089 Key Conclusions
- **Van Driest II and Coles** predict measured C_f within ±10% for T_w/T_aw ≥ 0.3 at M = 5.5–7.5.
- **Sommer & Short and Spalding & Chi** underpredict by 20–30% at M ≥ 6.
- Below T_w/T_aw = 0.3, **no theory** correctly captures the wall-temperature dependence.
- Cone Cf data (5° and 15° half-angles, M_e ≈ 6.6) are presented graphically (Fig. 2b, Fig. 5) but not in separate tables; they agree with flat-plate theories when expressed as C_f(Re_θ).

---

## 4. Krasil'shchikov et al. 1969 — Fig. 5 Digitized: C_x vs M
**Krasil'shchikov, A.P., Podobin, V.N., Nosov, V.V., "Systematic Experimental Data on the Drag of Sharp and Blunt Cones at Hypersonic Speeds," Mekhanika Zhidkosti i Gaza, Vol. 4, No. 3, pp. 190–192, 1969.**  
Firing range free-flight tests, M = 7–12 (data), theoretical curves extended to M ≈ 14. Re per unit length ~(7–12)×10⁶, viscous interaction effects negligible.

### Fig. 5 — C_x vs M∞ for Sharp Cones (λ = 1.0), Three Half-Angles

**Curve identification (from paper text and model table):**
- **Curve 1 (squares):** θ = 35° half-angle (highest drag)
- **Curve 2 (triangles):** θ = 24° half-angle
- **Curve 3 (circles):** θ = 15° half-angle (lowest drag)

Data points are free-flight measurements. Solid curves are theoretical (from Kopal tables + blunt body calculations, ref. 1–2 in paper). Curves are approximately constant for M ≥ 8 (drag-coefficient independence confirmed).

| M∞ | C_x (Curve 1, θ=35°) | C_x (Curve 2, θ=24°) | C_x (Curve 3, θ=15°) |
|---|---|---|---|
|  4 | ~0.71 | ~0.51 | ~0.36 |
|  6 | ~0.68 | ~0.48 | ~0.33 |
|  8 | ~0.66 | ~0.47 | ~0.31 |
| 10 | ~0.65 | ~0.46 | ~0.31 |
| 12 | ~0.64 | ~0.45 | ~0.30 |
| 14 | ~0.63 | ~0.44 | ~0.30 |

**Digitization uncertainty:** ±0.02 in C_x (figure is small; grid lines at 0.4 and 0.8 on y-axis, 4/8/12 on x-axis). The key takeaway is the asymptotic behavior — C_x is essentially constant (< 3% change) for M ≥ 8 for all three cone angles.

### Model Geometry Table (from paper, reproduced)

| θ_s (°) | λ | r/D | h/D |
|---|---|---|---|
| 15 | 1.0 | 0 | 0 |
| 15 | 0.8 | 0.131 | 0 |
| 15 | 0.6 | 0.261 | 0.533 |
| 15 | 0.4 | 0.391 | 0.533 |
| 24 | 0.8 | 0.153 | 0.533 |
| 24 | 0.6 | 0.306 | 0.533 |
| **24** | **0.45** | **0.422** | **0.533** |
| 35 | 1.0 | 0 | 0 |
| 35 | 0.8 | 0.19 | 0.533 |
| 35 | 0.6 | 0.38 | 0.533 |
| 50 | 1.0 | 0 | 0.533 |
| 50 | 0.8 | 0.275 | 0.533 |
| 50 | 0.6 | 0.549 | 0.533 |
| 70 | 1.0 | 0 | 0.533 |

*(λ = slenderness/bluntness ratio per Krasil'shchikov notation; λ = 1.0 = sharp cone. r/D = nose radius / base diameter. h/D = flat-face depth / base diameter.)*

---

## Cross-Reference Notes

### Mach Number Coverage Summary
| Source | M Range | Geometry | Data Type |
|---|---|---|---|
| DTIC AD0487365 Table II | M = 6.5, 9.0, 14.3, 17.2 | 8°, 12°, 16° cones | Total C_D + components |
| Krasil'shchikov Fig. 5 | M = 4–14 | 15°, 24°, 35° cones | Total C_x, free-flight |
| NASA TN D-5089 Table I | M = 6.5, 7.4 | Flat plate | C_f directly measured |
| NASA TN D-5089 Table II | M = 7.4 | Wind-tunnel wall | C_f directly measured |
| NASA TN D-6945 Eqs. 1–18 | M = 0–10 (analytical) | Flat plate equivalent | Turbulent C_f prediction |

### Gap Assessment
- **M < 5:** Not covered by any of these four sources. Rely on NACA/supersonic data.
- **M 5–9:** DTIC (M=6.5, 9) + Krasil'shchikov (M=6–9) provide overlapping cone drag; D-5089 validates the friction component at M=6.5–7.4.
- **M 9–14:** DTIC (M=9, 14.3) + Krasil'shchikov (M=8–14) close this range. Both show C_D flattening above M≈8.
- **M 14–25:** DTIC extends to M=17.2. Above M=17, rely on theoretical predictions (Newtonian + viscous interaction corrections). The M=14.3 and M=17.2 data from DTIC are the highest-M experimental tabulated cone drag points available in this set.

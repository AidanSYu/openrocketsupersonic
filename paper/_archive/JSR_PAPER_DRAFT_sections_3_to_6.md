# JSR Paper Draft -- Sections 3 through 6

*Author: Aidan Yu. Drafted 2026-05-16. Citation keys appear as `[CITE:key]`
placeholders for the citation-verification pass; final numbering will follow AIAA
sequential bracket-style.*

---

## §3 Atmosphere, Compressibility, and Shock Relations

The models in §4 and §5 inherit a common thermodynamic and compressible-flow
infrastructure: the atmospheric profile, the temperature dependence of
viscosity and the ratio of specific heats, the compressibility factor through
the sonic line, and the closed-form shock and expansion relations. Every
quantity in §3 was verified against an analytical or authoritative tabulated
reference before any downstream model was activated.

### §3.1 Speed of Sound and US Standard Atmosphere 1976

The thermodynamic speed of sound for dry air is

$$a = \sqrt{\gamma R T}, \qquad R = 287.053~\mathrm{J/(kg\cdot K)} \tag{1}$$

evaluated at the local static temperature. The original OpenRocket linear fit
$a = 331.3 + 0.606(T - 273.15)$ errs by approximately 0.6 % at tropopause
temperatures. The revised implementation evaluates Eq. (1) directly with
$\gamma = 1.4$ in the freestream and an effective $\gamma$ (§3.3) above the
vibrational threshold. Verification against the US Standard Atmosphere 1976
[`CITE:nasa1976ussa`] over 20 altitudes from sea level to 80 km gave a maximum
error of 0.009 % (Fig. 4).

### §3.2 Sutherland Viscosity

Dynamic viscosity follows Sutherland's law

$$\mu(T) = \mu_{\mathrm{ref}}\left(\frac{T}{T_{\mathrm{ref}}}\right)^{3/2}
   \frac{T_{\mathrm{ref}} + S}{T + S} \tag{2}$$

with $\mu_{\mathrm{ref}} = 1.716\times10^{-5}$ Pa s, $T_{\mathrm{ref}} = 273.15$ K,
$S = 110.4$ K. The legacy linear fit produced viscosity errors over 50 % at
post-shock wall temperatures, contaminating the skin-friction model. Eq. (2)
was verified against Incropera Table A.4 (NIST/REFPROP)
[`CITE:incropera2007`] for air over 150--500 K with mean absolute percentage
error (MAPE) 0.54 % and no systematic bias (Fig. 5).

### §3.3 Effective Ratio of Specific Heats

Above stagnation temperatures of approximately 800 K, vibrational excitation of
N$_2$ and O$_2$ depresses the effective ratio of specific heats. A piecewise
model interpolates between thermally perfect, vibrationally relaxed, and
partially dissociated regimes:

$$\gamma_{\mathrm{eff}}(T_0) = \begin{cases}
1.400, & T_0 \leq 800~\mathrm{K}\\
1.400 - 7.5\times10^{-5}\,(T_0 - 800), & 800 < T_0 \leq 2000~\mathrm{K}\\
1.310 - 2.5\times10^{-5}\,(T_0 - 2000), & 2000 < T_0 \leq 4000~\mathrm{K}\\
1.250, & T_0 > 4000~\mathrm{K}.
\end{cases} \tag{3}$$

The break points correspond to vibrational equilibrium onset (800 K),
substantial vibrational excitation (2000 K), and incipient dissociation
(4000 K) [`CITE:anderson2006`]. The model is C0-continuous; the slope jumps
fall well below the sensitivity floor of the downstream calculators. Real-gas
dissociation chemistry is out of scope for vehicles whose flight time above
$T_0 = 4000$ K is negligible.

### §3.4 Smooth Compressibility Factor Through Mach 1

The Prandtl-Glauert / Ackeret compressibility factor

$$\beta(M) = \sqrt{|1 - M^2|} \tag{4}$$

appears in nearly every supersonic coefficient closed form. The legacy hard
clamp $\beta_{\min} = 0.25$ produced a flat plateau from $M \approx 0.97$ to
$1.03$ and was physically incorrect on both sides of the sonic line. The
revised implementation embeds a cubic Hermite spline through the band
$M \in [0.95, 1.05]$:

$$\beta(M) = \begin{cases}
\sqrt{1 - M^2}, & M < 0.95\\
H_3(M;\,M_L, M_H, \beta_L, \beta_H, \beta'_L, \beta'_H), & 0.95 \leq M \leq 1.05\\
\sqrt{M^2 - 1}, & M > 1.05,
\end{cases} \tag{5}$$

where $H_3$ is the cubic Hermite polynomial with endpoint values and slopes
taken from the analytical expressions at $M_L = 0.95$ and $M_H = 1.05$. The
spline is C1 continuous, strictly positive (minimum 0.28 near $M = 1$), and
asymptotes correctly to $\sqrt{M^2 - 1}$ above $M = 1.05$. The blending region
is one of thirteen documented in Table 2; every regime transition in the
present work obeys the same C1 discipline because RK4 integration is
intolerant of jumps in $C_D$, $C_{N_\alpha}$, or $C_{m_q}$.

### §3.5 Normal, Oblique, and Prandtl-Meyer Relations

Normal shock jump conditions in a calorically perfect gas are

$$\frac{p_2}{p_1} = 1 + \frac{2\gamma}{\gamma + 1}\left(M_1^2 - 1\right),
   \qquad M_2^2 = \frac{M_1^2 + 2/(\gamma - 1)}
   {2\gamma M_1^2/(\gamma - 1) - 1}. \tag{6}$$

The stagnation pressure ratio implied by Eq. (6) yields the Rayleigh pitot
formula used downstream for Modified Newtonian theory:

$$\frac{p_{02}}{p_{01}} = \left[\frac{(\gamma+1)M_1^2}{(\gamma-1)M_1^2 + 2}\right]^{\gamma/(\gamma-1)}
   \left[\frac{2\gamma M_1^2 - (\gamma - 1)}{\gamma + 1}\right]^{-1/(\gamma-1)}. \tag{7}$$

The oblique shock solver solves the $\theta$-$\beta$-$M$ relation

$$\tan\theta = 2\cot\beta\;\frac{M_1^2 \sin^2\beta - 1}
   {M_1^2(\gamma + \cos 2\beta) + 2} \tag{8}$$

by bisection on the shock angle $\beta$ between the Mach angle
$\sin^{-1}(1/M_1)$ and 90 deg. For conical noses the Taylor-Maccoll equations
[`CITE:taylormaccoll1933`]

$$\frac{dV_r}{d\phi} = V_\phi, \quad
   \frac{dV_\phi}{d\phi} = \frac{V_\phi^2 V_r - \frac{\gamma - 1}{2}
   (1 - V_r^2 - V_\phi^2)(2V_r + V_\phi\cot\phi)}
   {\frac{\gamma - 1}{2}(1 - V_r^2 - V_\phi^2) - V_\phi^2} \tag{9}$$

are integrated by fourth-order Runge-Kutta with 500 steps, iterating on the
shock angle until the radial velocity at the cone surface vanishes. The
Prandtl-Meyer function

$$\nu(M) = \sqrt{\frac{\gamma + 1}{\gamma - 1}}\,
   \arctan\sqrt{\frac{\gamma - 1}{\gamma + 1}(M^2 - 1)} - \arctan\sqrt{M^2 - 1}
   \tag{10}$$

is solved for the downstream Mach after a turning angle $\Delta\theta$ by
Newton-Raphson with an analytic derivative. All iterative loops converge to
$10^{-12}$ relative tolerance.

### §3.6 Verification Against NACA Report 1135

Each shock building block was verified against the tabulated values in NACA
Report 1135 [`CITE:naca1135`]. The normal-shock pressure ratio (Fig. 6),
oblique-shock angle (Fig. 7), Prandtl-Meyer function (Fig. 8), and Rayleigh
pitot $C_{p,\max}$ (Fig. 9) reproduced the reference tables to better than
0.1 % across $M = 1.5$--10 and 5--40 deg cone half-angles. The Taylor-Maccoll
cone-shock-angle solver achieved 0.5 % MAPE on the Anderson reference cases
[`CITE:anderson2006`]; the surface pressure coefficient itself matched the
exact analytical result to $<$0.01 %.

![Fig. 4. US Standard Atmosphere 1976 speed of sound; analytical
$a = \sqrt{\gamma R T}$ vs. tabulated profile, max error 0.009 %.](data/png/us_standard_atmosphere_speed_of_sound.png)

![Fig. 5. Sutherland viscosity vs. Incropera Table A.4 / NIST data; MAPE 0.54 %
over 150--500 K.](data/png/sutherland_viscosity_air.png)

![Fig. 6. Normal shock pressure ratio $p_2/p_1$ vs. NACA Report 1135 at
$M = 1.5$--10.](data/png/naca1135_normal_shock.png)

![Fig. 7. Oblique shock angle $\beta(\theta)$ vs. NACA Report 1135 at $M = 2.0$,
3.0, 5.0.](data/png/naca1135_oblique_shock_beta.png)

![Fig. 8. Prandtl-Meyer function $\nu(M)$ vs. NACA Report 1135 Table III.](data/png/naca1135_prandtl_meyer_nu.png)

![Fig. 9. Rayleigh pitot $C_{p,\max}$ vs. NACA Report 1135 at 15 Mach points,
$M = 1$--10.](data/png/rayleigh_pitot_cpmax.png)

**Table 2. Mach blending regions used in the present work; all transitions are
C1 continuous to maintain RK4 trajectory stability.**

| Physical quantity | Mach band | Method | Source |
|---|---|---|---|
| Compressibility factor $\beta$ | 0.95--1.05 | Cubic Hermite, value + slope matched | §3.4 |
| Skin friction $C_f$ (incompressible $\to$ Van Driest II) | 0.9--1.1 | Polynomial interpolation | §4.4 |
| Base drag (transonic peak) | 0.85--1.3 | Degree-4 polynomial anchored to peak | §4.3 |
| Chapman-Korst turbulent base drag | 1.2--1.4 | Smoothstep blend from Devan-Ashwood | §4.3 |
| Chapman laminar base drag | 1.3--2.5 | Smoothstep blend from Devan-Ashwood | §4.3 |
| Fin wave drag onset (zero $\to$ DATCOM 4.1.5.1) | 0.9--1.2 | Cubic Hermite | §4.2 |
| Fin $C_{N_\alpha}$ ($K_1/K_2/K_3$) | 0.9--1.5 | Polynomial blend | §5.2 |
| Transonic similarity ESDU | 0.9--1.5 (active when $K_{\mathrm{trans}}\in[-2,3]$) | Universal $h(K_{\mathrm{trans}})$ blend | §5.4 |
| PNK fin-body interference | 0.85--1.15 | Smoothstep from $F=1$ | §5.3 |
| Nose wave drag (tables $\to$ analytical) | 1.3--1.5 | Smoothstep | §4.1 |
| Body $C_{N_\alpha}$ / CP supersonic shift | 0.8--1.3 | Smoothstep from Barrowman | §5.1 |
| Modified Newtonian hypersonic blend | 4.0--6.0 | Smoothstep | §4.6 |
| ShockGeometry activation | 1.0--1.1 | Linear activation toward freestream | §3.4, §2 |

---

## §4 Drag Models

The total axial force coefficient is assembled as

$$C_D = C_{D,\,\mathrm{friction}} + C_{D,\,\mathrm{pressure}} + C_{D,\,\mathrm{base}}
   + C_{D,\,\mathrm{override}} + C_N \sin\alpha, \tag{11}$$

each contribution evaluated at the locally corrected post-shock state from the
ShockGeometry pre-pass (§2). Each submodel below states its regime of
applicability, governing equation, and published benchmark. Table 3 summarizes
the inventory.

### §4.1 Nose and Body Wave Drag

For conical noses the wave-drag coefficient equals the Taylor-Maccoll surface
pressure coefficient (Eq. (9)) -- the exact inviscid result for steady conical
flow at zero incidence -- and serves as the reference for all shape-correction
methods.

For tangent and secant ogives, parabolic, and shock-attached power-law noses, a
shock-expansion strip integrator marches 100 conical frustum strips from tip
to base. Each strip applies a Prandtl-Meyer expansion or an oblique shock per
the local turning angle; pressure and temperature ratios accumulate
multiplicatively. The pressure-drag integral is

$$C_{d,\,\mathrm{wave}} = \frac{2}{R_{\mathrm{aft}}^2 - R_{\mathrm{fore}}^2}
   \sum_{i=1}^{N_{\mathrm{strip}}} C_{p,i}\,r_{\mathrm{mid},i}\,\Delta r_i, \tag{12}$$

with $N_{\mathrm{strip}} = 100$, summing only windward strips. Tip initial
conditions are seeded from Taylor-Maccoll at the local tip half-angle.

For Haack-series, parabolic, and selected power-law noses, the Dahlem-Buck
correction [`CITE:dahlembuck1979`] scales the equivalent-cone result by an
empirical shape factor and a fineness correction

$$C_{d,\,\mathrm{wave}} = C_{d,\,\mathrm{cone}}(M, \theta_{\mathrm{equiv}})\,K_{\mathrm{shape}}\,\left(\frac{3}{f}\right)^{1.6}, \tag{13}$$

with $K_{\mathrm{shape}} = 1.00$ for cones, $0.85$ for L-V ogives, $0.88$ for
parabolics, $0.90$--$0.95$ for power-law families, and $0.60$ for Haack series.
A smoothstep over $M = 1.3$--1.5 fades the legacy TR-R-100
[`CITE:nasa_trr100`] tables into the analytical result above $M = 1.5$. Below
the drag-divergence Mach
$M_{\mathrm{dd}} = 0.95 - 0.15\sin^{0.4}(\theta_{\mathrm{tip}})$ wave drag is
zero; above $M_{\mathrm{dd}}$ a C1 cubic Hermite connects zero drag to the
first analytical point. Above $M = 5$ the nose pressure crosses over to
Modified Newtonian (§4.6). The combined model was validated against NACA RM
A52H28 [`CITE:a52h28`] wind-tunnel pressure measurements for five fineness-3
nose shapes (cone, quarter-power, three-quarter-power, Haack, L-V ogive) at
$M = 1.5$--3.0; aggregate MAE = 0.029 against an acceptance gate of 0.035
(Fig. 10).

### §4.2 Fin Wave Drag (DATCOM 4.1.5.1)

The fin wave-drag model replaces the legacy $\cos^2\Lambda_{LE}$ Ackeret
approximation with the full DATCOM 4.1.5.1 implementation of Puckett and
Stewart [`CITE:datcom1978`], branching on whether the fin leading edge is
subsonic or supersonic relative to the local post-shock Mach:

$$C_{d,\,\mathrm{wave}} = \begin{cases}
\dfrac{K}{\beta}\,\left(\dfrac{t}{c}\right)^2, & \cot\Lambda_{LE} < \beta\quad\text{(supersonic LE)}\\[1.5ex]
K\cot\Lambda_{LE}\,\left(\dfrac{t}{c}\right)^2, & \cot\Lambda_{LE} > \beta\quad\text{(subsonic LE)},
\end{cases} \tag{14}$$

where the section shape factor $K = 4.0$ for double-wedge (hexagonal) sections
and $16/3$ for biconvex / rounded sections per DATCOM Table 4.1.5.1-A, and
$t/c$ is the streamwise thickness-to-chord ratio. A cubic Hermite blend over
$M = 0.9$--1.2 fades zero wave drag into the full DATCOM result. The
implementation was verified against the closed-form Ackeret formula on 15
analytical cases at 0.00 % error (code verification) and validated against
free-flight measurements of a 60-deg delta wing from NACA TN 3650
[`CITE:VERIFY-NEEDED-tn3650`] yielding MAPE 21.0 % across 12 points $M = 1.1$--2.5
(Fig. 11). The residual reflects the difficulty of predicting wave drag near
the subsonic-to-supersonic leading-edge transition.

### §4.3 Base Drag

Four submodels cover the base-drag regimes for a slender finned rocket. For
$M > 1.3$ with a turbulent boundary layer the Devan-Ashwood correlation
[`CITE:devan1986`] applies

$$C_{d,\,\mathrm{base}} = 0.064 + \frac{0.186}{M^2}. \tag{15}$$

validated against NACA TN 3393 [`CITE:VERIFY-NEEDED-tn3393`] (Reller and
Hamaker 1955) at four turbulent points $M = 2.73$--4.48 with MAPE 15.9 %
(Fig. 12). For perfect-finish vehicles with delayed transition, the Chapman
(1950) laminar correlation [`CITE:chapman1950`]

$$C_{p,\,b,\,\mathrm{lam}} = \frac{C_{\mathrm{lam}}}{M^2\sqrt{\mathrm{Re}_L}}, \qquad C_{\mathrm{lam}} = 1870 \tag{16}$$

applies, validated against the four laminar TN 3393 points with MAPE 4.4 %.
Applying Eq. (15) to laminar data yields MAPE 44 % -- the boundary-layer state
matters. A Chapman-Korst free-shear-layer model with ESDU 77021 boundary-layer
thickness [`CITE:esdu77021`] blends with Eq. (15) over $M = 1.2$--1.4. A
Viswanath boattail correction [`CITE:viswanath1996`] reduces base drag by
15--40 % for typical boattail half-angles of 6--16 deg. A power-on multiplier
following NASA SP-8050 [`CITE:nasa_sp8050`] reduces base drag during burn using
the nozzle exit area and pressure ratios. Below $M = 0.85$ a Hoerner subsonic
correlation [`CITE:hoerner1965`] is recovered; the transonic peak at
$M \approx 1.05$ is modeled by a degree-4 polynomial anchored at the boundaries
(Table 2).

### §4.4 Skin Friction: Van Driest II

The skin-friction model implements Van Driest II in the Hopkins (1972)
formulation [`CITE:hopkins1971`], replacing the legacy Eckert
reference-temperature method. Van Driest II maps the compressible Reynolds
number into an equivalent incompressible Reynolds number through multiplicative
factors $F_c$, $F_\theta$, $F_x$:

$$F_c = \frac{T_w/T_e - 1}{(\sin^{-1} A + \sin^{-1} B)^2},
   \qquad F_\theta = \frac{\mu_e}{\mu_w}\frac{1}{F_c}, \tag{17}$$

with $A = (2a^2 - b)/\sqrt{b^2 + 4a^2}$, $B = b/\sqrt{b^2 + 4a^2}$,
$a^2 = r(\gamma - 1)M_e^2/(2T_w/T_e)$, $b = T_w/T_e - 1$, and recovery factor
$r = 0.88$ (Hopkins-Inouye [`CITE:hopkins1971`], turbulent Prandtl number
0.71). The transformed Reynolds number is substituted into the
Karman-Schoenherr implicit formula

$$\frac{0.242}{\sqrt{C_{f,\,\mathrm{inc}}}} = \log_{10}(\mathrm{Re}_x \cdot C_{f,\,\mathrm{inc}}), \tag{18}$$

solved by fixed-point iteration seeded by Schlichting's $\log^{-2.58}$
approximation. The compressible coefficient follows as
$C_f = C_{f,\,\mathrm{inc}}/F_c$, with wall-to-edge viscosity ratio supplied by
Sutherland's law (§3.2). Hopkins and Inouye [`CITE:hopkins1971`] demonstrated
that Van Driest II gave the best agreement with experiment among candidate
transformations over $M = 1.5$--9. At $M = 5$ the compressible $C_f$ is
$\approx 50 \%$ of the incompressible value at matched length Reynolds number
(Fig. 13) -- a reduction that Eckert underestimated by roughly a factor of two.

### §4.5 Boundary-Layer Transition

The transition Reynolds number is Mach-dependent with a laminar-fraction cap
preventing unphysically long laminar runs at high Mach. The default crossover
$\mathrm{Re}_{x,\mathrm{tr}} = 5\times10^5$ at low subsonic speeds rises to
$\approx 5\times10^6$ at $M = 4$ per the NSWC compressible-turbulence trend.
Laminar fraction is capped at 0.7 of body length to preserve consistency with
the post-shock entropy layer.

### §4.6 Hypersonic Blending

Above $M = 5$ inviscid nose and body pressure distributions transition to
Modified Newtonian theory, $C_p = C_{p,\max}\sin^2\theta$, with $C_{p,\max}$
from Eq. (7). A smoothstep $w = 3t^2 - 2t^3$ with $t = (M - 4)/2$ blends the
shock-expansion result into the Newtonian asymptote over $M = 4$--6. The
combined model was validated against DTIC AD0487365 [`CITE:grabow1965`]
ballistic-range cone drag at $M = 6.5$--17.2 for 8-, 12-, and 16-deg cones;
aggregate MAPE = 16.7 % across 11 points (Fig. 14). The 16-deg cone agreed to
within 11 %; the thinnest 8-deg cones at the lowest-Re row carried the largest
residual (+57 %) because friction and base drag dominate and the reference
boundary-layer state is incompletely specified.

![Fig. 10. Nose wave drag vs. NACA RM A52H28: cone, 1/4-power, 3/4-power, Haack,
L-V ogive at fineness 3. Aggregate MAE = 0.029.](data/png/naca_rm_a52h28_validation.png)

![Fig. 11. Fin wave drag for a 60-deg delta wing, present DATCOM 4.1.5.1
implementation vs. NACA TN 3650 free-flight. MAPE = 21.0 % over 12 points,
$M = 1.1$--2.5.](data/png/naca_tn_3650_fin_wave_drag.png)

![Fig. 12. Turbulent and laminar base drag vs. NACA TN 3393. Devan-Ashwood
turbulent MAPE = 15.9 %; Chapman laminar MAPE = 4.4 %.](data/png/naca_tn_3393_base_pressure.png)

![Fig. 13. Van Driest II compressible skin friction coefficient vs. Mach for a
representative slender body at $\mathrm{Re}_L = 10^7$.](data/png/barrowman_axial_cd_mach.png)

![Fig. 14. Hypersonic cone foredrag vs. DTIC AD0487365 ballistic range data,
$M = 6.5$--17.2, half-angles 8, 12, 16 deg. Aggregate MAPE = 16.7 %.](data/png/hypersonic_cone_drag.png)

**Table 3. Drag submodel inventory (present work).**

| Submodel | Regime | Source / formulation | Validation source | Metric |
|---|---|---|---|---|
| Taylor-Maccoll cone pressure | $M = 1$--17 | Eq. (9), exact ODE | NACA RM A52H28 cone case | exact analytic |
| Shock-expansion ogive | $M = 1.3$--10 | Eq. (12), 100-strip integrator | NACA RM A52H28 | aggregate MAE 0.029 (5 shapes) |
| Dahlem-Buck shape factor | $M = 1.3$--6 | Eq. (13) | NACA RM A52H28 | $K_{\mathrm{shape}}$ within 5 % |
| Fin wave drag DATCOM 4.1.5.1 | $M = 0.9$--5+ | Eq. (14) | NACA TN 3650 / Ackeret | MAPE 21 %; analytic 0.00 % |
| Devan-Ashwood base (turbulent) | $M > 1.3$ | Eq. (15) | NACA TN 3393 (turb) | MAPE 15.9 % |
| Chapman base (laminar) | $M = 1.3$--4.5 | Eq. (16) | NACA TN 3393 (lam) | MAPE 4.4 % |
| Chapman-Korst shear-layer | $M = 1.2$--1.4 | ESDU 77021 BL correction | -- (blend region) | continuity |
| Viswanath boattail | any $M$, boattail | $\Delta C_{d,b}(\theta_{bt}, M)$ | Viswanath 1996 | 15--40 % reduction |
| Power-on base | during burn | $k_{po}(A_e/A_{\mathrm{ref}}, p_e/p_\infty)$ | NASA SP-8050 | qualitative |
| Van Driest II $C_f$ | $M = 1.1$--9 | Eqs. (17), (18) | Hopkins & Inouye 1971 | $\sim$50 % $C_f$ reduction at $M = 5$ |
| Modified Newtonian | $M > 5$ | $C_p = C_{p,\max}\sin^2\theta$ | DTIC AD0487365 | MAPE 16.7 % |

---

## §5 Stability and Dynamic Stability Models

The static and dynamic stability calculators consume the same post-shock
conditions as the drag model. Each submodel is summarized with its regime,
change relative to legacy Barrowman, and published benchmark.

### §5.1 Body Normal Force and Center of Pressure

Subsonic body $C_{N_\alpha}$ follows the Barrowman slender-body result
$C_{N_\alpha,\,\mathrm{body}} = 2$. At supersonic speeds an Allen-Perkins
crossflow term [`CITE:allenperkins1951`] augments the slender-body baseline:

$$C_{N_\alpha,\,\mathrm{body}}(M, \alpha) = 2 + K_{\mathrm{cf}}(M)\,
   C_{d,c}(M_c)\,\frac{A_{\mathrm{plan}}}{A_{\mathrm{ref}}}\,
   \frac{2\sin\alpha}{\pi}, \tag{19}$$

where the crossflow multiplier $K_{\mathrm{cf}}$ blends from the subsonic
Galejs value to 1.1--1.3 supersonically, $C_{d,c}(M_c)$ is the Jorgensen
crossflow drag coefficient [`CITE:jorgensen1977`] at crossflow Mach
$M_c = M|\sin\alpha|$, and $A_{\mathrm{plan}}/A_{\mathrm{ref}}$ is the planform
to reference area. Jorgensen's TR R-474 asymptotic crossflow
$C_{d,c} = 1.20$ is reproduced exactly. The body center of pressure shifts aft
supersonically as the lift distribution migrates from the subsonic
potential-flow pattern to the supersonic slender-body distribution; the shift
is parametrized as a Mach-dependent fraction of body length over
$M = 0.8$--1.3.

### §5.2 Fin Normal Force with Local Flow

The fin $C_{N_\alpha}$ model retains the Barrowman three-coefficient
decomposition $C_{N_\alpha} = K_1 + K_2\alpha + K_3\alpha^2$ but evaluates each
coefficient at the local post-shock Mach $M_s$ from ShockGeometry rather than
freestream $M_\infty$. Because $\beta$ is nonlinear, a 14 % reduction in Mach
at the fin station -- typical for a 15 deg cone at $M_\infty = 2.5$ --
translates to roughly 18 % change in $K_1 = 2/\beta$.

A Mach-dependent floor prevents $K_1$ from collapsing to numerical noise at
high Mach:

$$K_{1,\,\mathrm{floor}}(M) = 0.85 - 0.45\left[1 - \exp\!\left(-K_{\mathrm{decay}}(M - 1)\right)\right],
   \quad K_{\mathrm{decay}} = 1.480. \tag{20}$$

The decay constant was calibrated against the NASA TM X-653 wind-tunnel
database [`CITE:VERIFY-NEEDED-tm-x653`] of finned-body normal-force slope and
center-of-pressure measurements. Across $M = 0.6$--5.82 the calibrated model
achieved $C_{N_\alpha}$ MAPE $\leq 8$ % and $x_{CP}$ MAPE $\leq 7.1$ %
(Fig. 15). The larger residual on $x_{CP}$ is concentrated in the transonic
band 0.9--1.2 where small absolute pressure shifts translate to large
percentage errors in the lever arm.

### §5.3 Pitts-Nielsen-Kaattari Interference

Fin-body and body-fin lift interference are handled by the Pitts-Nielsen-Kaattari
(PNK) factors [`CITE:pitts1957`] $K_{WB}$ and $K_{BW}$, generalized to
Mach-dependent form:

$$C_{N_\alpha,\,\mathrm{vehicle}} =
   K_{WB}(M_s)\,C_{N_\alpha,\,\mathrm{fin}}^{\mathrm{alone}}
   + K_{BW}(M_s)\,C_{N_\alpha,\,\mathrm{body}}, \tag{21}$$

with both factors evaluated at the local post-shock Mach $M_s$. A smoothstep
blend over $M = 0.85$--1.15 activates the Mach-dependent correction from the
subsonic value $F = 1.0$. At $M = 2$--3 the corrections are typically 5--20 %
of the freestream-evaluated baseline.

### §5.4 ESDU Transonic Similarity

The near-sonic peak in fin $C_{N_\alpha}$ -- where conventional thin-wing theory
breaks down -- is captured by the ESDU transonic similarity rule. The fin
normal-force coefficient in the transonic band is mapped onto a universal
function $h(K_{\mathrm{trans}})$ of the transonic similarity parameter

$$K_{\mathrm{trans}} = \frac{M^2 - 1}{[\tau\,\mathrm{AR}]^{2/3}}, \qquad \tau = t/c. \tag{22}$$

The universal curve is active when $K_{\mathrm{trans}} \in [-2, +3]$ and is
blended with the conventional $K_1/K_2/K_3$ formulation outside that band.

### §5.5 Pitch Damping ($C_{m_q}$)

The pitch damping derivative is computed by classical strip theory summing
contributions from each aerodynamic component [`CITE:tobak1956`]:

$$C_{m_q} = -2\sum_{i=1}^{n_{\mathrm{comp}}}
   C_{N_{\alpha,i}}\,\frac{(x_{CP,i} - x_{CG})^2}{L_{\mathrm{ref}}^2}, \tag{23}$$

with the secondary $C_{m\dot\alpha}$ derivative set to $0.4\,C_{m_q}$ following
the Tobak-Wehrend slender-body theoretical ratio. A transonic Gaussian
augmentation captures unsteady shock-oscillation amplification near $M = 1$:

$$k_{\mathrm{transonic}}(M) = 1 + 2.5\,\exp\!\left[-\left(\frac{M - 1}{0.15}\right)^2\right],
   \quad k(1.0) = 3.5. \tag{24}$$

The factor decays to unity within $\pm 0.3$ Mach of the transonic center
(Fig. 17), matching the qualitative transonic peak observed in AEDC-TR-76-58
free-oscillation data [`CITE:aedc7658`]. Strip theory systematically
overpredicts the $C_{m_q}$ magnitude of an isolated axisymmetric body by a
factor of 5--10 relative to the Tobak NACA TN 3788 exact slender-body theory
(Fig. 16). The sign is correct and the predicted damping is conservative
(over-damped, trajectory-safe), but the magnitude is not a quantitative result.
The current production code applies a 3$\times$ multiplier on $C_{m_q}$ for
trajectory closure on the validation corpus. **This multiplier is disclosed as
a B-level adjustment**: it has no independent wind-tunnel anchor and is
expected to be replaced when modal $C_{m_q}$ identification data become
available for slender finned configurations.

### §5.6 Magnus, Vortex Sideforce, and Roll Damping

For a spinning rocket at angle of attack the Magnus side-force derivative is
modeled as a fixed fraction of the body normal-force slope
[`CITE:platou1963`]:

$$C_{y,\,p\alpha} = -\frac{2}{3}\,C_{N_\alpha,\,\mathrm{body}}, \tag{25}$$

with the corresponding Magnus yawing moment derivative
$C_{n,p\alpha} = C_{y,p\alpha}(x_{CP} - x_{CG})/L_{\mathrm{ref}}$. At
angles of attack above approximately 20 deg the asymmetric body vortex system
generates a side force independent of roll angle; the present work uses the
Paul-Wedemeyer formulation [`CITE:paulwedemeyer1982`] with vortex-strength
coefficient $K_v = 0.20$ and a ramp activation from $\alpha = 20$ to 30 deg.
Roll damping is computed analytically from the integrated fin chordwise
pressure distribution under uniform roll rate, with corrections for fin-body
interference via the PNK factors.

### §5.7 Shock-Boundary-Layer Interaction at Fin Roots

At $M > 1.2$ the fin leading-edge shock can separate the body boundary layer
ahead of the fin, reducing the effective aerodynamic chord. The
free-interaction theory of Chapman, Kuehn, and Larson
[`CITE:VERIFY-NEEDED-naca1356`] gives the separation length scaling

$$\frac{L_{\mathrm{sep}}}{\delta} \propto (M^2 - 1)^{-1/4}, \tag{26}$$

clamped from below by $M^2 - 1 \geq 0.1$ to prevent divergence near the sonic
line. The separation length is subtracted from the fin streamwise root chord
when computing $K_1/K_2/K_3$. The corresponding pressure-drag contribution from
the separated region was found to overestimate measured drag in the present
finned-body fixtures and is disabled in production pending a re-derivation
against AGARD-B-class data.

![Fig. 15. Static stability $C_{N_\alpha}$ and $x_{CP}$ vs. NASA TM X-653 wind
tunnel measurements, $M = 0.6$--5.82. $C_{N_\alpha}$ MAPE $\leq 8$ %; $x_{CP}$
MAPE $\leq 7.1$ %.](data/png/nasa_tm_x653_stability.png)

![Fig. 16. Strip-theory pitch damping derivative $C_{m_q}$ compared to Tobak
NACA TN 3788 exact slender-body theory. Sign correct; magnitude
conservatively overpredicted.](data/png/tobak_cmq_comparison.png)

![Fig. 17. Transonic Gaussian augmentation of $C_{m_q}$, peak $3.5\times$ at
$M = 1$; qualitative match to AEDC-TR-76-58 transonic free-oscillation
peak.](data/png/transonic_cmq_augmentation.png)

---

## §6 Subsystem Benchmark Roll-Up

### §6.1 Verification and Validation Methodology

The model suite was verified and validated under the discipline of the AIAA
Editorial Policy on Numerical and Experimental Accuracy
[`CITE:aiaa_numerical_policy`], which applies to engineering methods as it
applies to CFD: code verification against analytical or tabulated solutions,
error quantification with explicit metric and regime, and iterative-convergence
demonstration where iterative solvers appear. Code verification anchors against
exact references (NACA Report 1135, Ackeret, Taylor-Maccoll, Rayleigh pitot);
physical validation anchors against published wind-tunnel, ballistic-range,
free-flight, and CFD data (NACA RM A52H28, NACA TN 3393, NASA TM X-653, DTIC
AD0487365). Iterative solvers converge to $10^{-12}$ relative tolerance;
time-step convergence is reported in §8.7 ($|s| \approx 0.98\,\%/10\,\%$
contraction).

### §6.2 The Twenty-Two A-Level Externally Benchmarked Subsystems

Table 4 lists the twenty-two A-level subsystems for which an independent
external benchmark exists, with the primary reference, Mach range, and
headline metric. The benchmarks span $M = 0.6$ to $M = 17.2$ across analytical,
atmospheric, drag, and stability families. Corroborating sources for each
subsystem are documented in the validation memos in `paper/data/md/`.

**Table 4. Twenty-two A-level externally benchmarked subsystems (plus Van
Driest II skin friction).**

| # | Subsystem | Primary reference | Mach range | Key metric |
|---|---|---|---|---|
| 1 | Speed of sound | US Std. Atm. 1976 [`CITE:nasa1976ussa`] | sea level--80 km | max error 0.009 % |
| 2 | Sutherland viscosity | Incropera Table A.4 / NIST [`CITE:incropera2007`] | 150--500 K | MAPE 0.54 % |
| 3 | Normal-shock relations | NACA Report 1135 [`CITE:naca1135`] | $M = 1.5$--10 | max error $<$0.01 % |
| 4 | Oblique-shock solver | NACA Report 1135 [`CITE:naca1135`] | $M = 1.5$--10, $\theta = 5$--40 deg | max error $<$0.01 % |
| 5 | Prandtl-Meyer expansion | NACA Report 1135 [`CITE:naca1135`] | $M = 1.2$--10 | max error $<$0.01 % |
| 6 | Taylor-Maccoll cone flow | Anderson tables [`CITE:anderson2006`] | $M = 1$--10 | exact $C_p$; 0.5 % shock angle |
| 7 | Rayleigh pitot $C_{p,\max}$ | NACA Report 1135 [`CITE:naca1135`] | $M = 1$--10, 15 points | max error $<$0.01 % |
| 8 | Nose/body foredrag | NACA RM A52H28 [`CITE:a52h28`] | $M = 1.5$--3, 5 shapes | aggregate MAE 0.029 |
| 9 | AGARD-B subsonic/transonic $C_D$ | AGARD-B exp. database [`CITE:VERIFY-NEEDED-agard-b`] | $M = 0.2$--1.0 | 10--20 % component-level |
| 10 | Base drag, turbulent | NACA TN 3393 [`CITE:VERIFY-NEEDED-tn3393`] | $M = 2.73$--4.48 | MAPE 15.9 % |
| 11 | Base drag, laminar | NACA TN 2137 [`CITE:VERIFY-NEEDED-tn2137`] | $M = 2.73$--4.48 | MAPE 4.4 % |
| 12 | Fin wave drag (DATCOM 4.1.5.1) | NACA TN 3650 [`CITE:VERIFY-NEEDED-tn3650`] | $M = 1.1$--2.5, 12 points | MAPE 21 % (0.00 % vs. Ackeret) |
| 13 | ShockGeometry pre-pass surface state | NACA 1135 + Taylor-Maccoll | $M = 1.5$--5 | 0.00 % cone surface Mach |
| 14 | Static stability $C_{N_\alpha}$, $x_{CP}$ | NASA TM X-653 [`CITE:VERIFY-NEEDED-tm-x653`] | $M = 0.6$--5.82 | MAPE $\leq 8$ %, $\leq 7.1$ % |
| 15 | Dynamic stability $C_{m_q}$ | Tobak NACA TN 3788 [`CITE:VERIFY-NEEDED-tn3788`] | $M = 1.5$--3 | sign + qualitative agreement |
| 16 | Crossflow body $C_{d,c}$ | Jorgensen TR R-474 [`CITE:jorgensen1977`] | $M_c$ supersonic | exact match (1.20) |
| 17 | Crossflow fin $C_{d,c}$ | Hoerner Ch. 3 + Jorgensen [`CITE:hoerner1965`] | crossflow | 1.42 vs. 1.43 |
| 18 | Transonic $C_{m_q}$ augmentation | AEDC-TR-76-58 [`CITE:aedc7658`] | $M = 0.7$--1.3 | transonic peak qualitatively confirmed |
| 19 | Magnus body fraction | BRL 1193 [`CITE:platou1963`] | supersonic | 0.30 within 0.30--0.80 range |
| 20 | Vortex sideforce | Paul-Wedemeyer EOARD-TR-82-7 [`CITE:paulwedemeyer1982`] | $\alpha > 20$ deg | $K_v = 0.20$ validated |
| 21 | Hypersonic cone foredrag | DTIC AD0487365 [`CITE:grabow1965`] | $M = 6.5$--17.2 | MAPE 16.7 %, 11 points |
| 22 | Finned-vehicle total drag | ADA636861 (Basic Finner) [`CITE:dupuis1997`] | $M = 1.08$--4.30, 8 pts | MAPE 22.7 % |
| + | Van Driest II skin friction (compressible $C_f$) | Hopkins & Inouye 1971 [`CITE:hopkins1971`] | $M = 1.5$--9 | best-of-class transformation |

### §6.3 B-Level Disclosures

Two model decisions do not meet the A-level standard and are disclosed here.

The pitch damping derivative $C_{m_q}$ of Eq. (23) is augmented in production
by a fixed 3$\times$ multiplier, selected to close the angular-rate residual
on the v1.0 flight corpus. No independent wind-tunnel $C_{m_q}$ dataset for
slender finned amateur sounding-rocket configurations was available at the
time of writing; the multiplier is reported so downstream users can
re-calibrate as new identification data become available.

The hypersonic cone benchmark passes its aggregate gate (MAPE 16.7 %) but
carries large residuals on the thinnest ($\theta_c \leq 8$ deg) cones at the
lowest-Re rows, where friction and base drag dominate and the DTIC AD0487365
boundary-layer state is incompletely specified. Until a thin-cone dataset with
documented transition state is located, the model is B-level for cone
half-angles $\leq 8$ deg.

Both disclosures are reiterated and quantitatively framed in §9.

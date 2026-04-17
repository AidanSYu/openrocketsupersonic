#!/usr/bin/env python3
"""Rebuild all publication PNGs from final CSVs.

Usage:
    python plot_all_validation.py          # regenerate all 15 PNGs
    python plot_all_validation.py normal_shock prandtl_meyer  # specific plots only

Output goes to  paper/data/png/  (overwrites existing files).
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import pandas as pd

# Ensure paper/ is on sys.path so plot_style imports cleanly
SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from plot_style import (
    apply_style, validation_figure, simple_figure, multi_validation_figure,
    ref_scatter, model_line, plot_residuals, add_legend, metrics_box,
    compute_metrics, fmt_metrics, save,
    TAB10, REF_MARKERS, C_BLUE, C_ORANGE, C_GREEN, C_RED, C_PURPLE,
    SINGLE_FIG, WIDE_FIG, TALL_FIG, DPI,
)

DATA_DIR = SCRIPT_DIR / "data"
CSV  = DATA_DIR / "csv"
PNG  = DATA_DIR / "png"


def _read(name: str) -> pd.DataFrame:
    return pd.read_csv(CSV / name, comment="#")


# ======================================================================
# 1. NACA 1135 Normal Shock  (4 quantities)
# ======================================================================
def plot_normal_shock():
    df = _read("naca1135_normal_shock.csv")
    quantities = [
        ("M2",       "M2_ref",       "M2_orp",       r"$M_2$"),
        ("p2/p1",    "p2_p1_ref",    "p2_p1_orp",    r"$p_2/p_1$"),
        ("rho2/rho1","rho2_rho1_ref","rho2_rho1_orp",r"$\rho_2/\rho_1$"),
        ("T2/T1",    "T2_T1_ref",    "T2_T1_orp",    r"$T_2/T_1$"),
    ]

    fig, axes = multi_validation_figure(
        2, 2, "NACA 1135 Normal Shock Validation",
        figsize=(10, 7))
    axes = axes.ravel()

    all_pct = []
    for i, (tag, ref_col, orp_col, ylabel) in enumerate(quantities):
        ax = axes[i]
        x = df["M1"].values
        ref = df[ref_col].values
        orp = df[orp_col].values
        m = compute_metrics(ref, orp)
        all_pct.append(m["mape"])

        ref_scatter(ax, x, ref, label="NACA 1135", ci=0, mi=0)
        model_line(ax, x, orp, label="ORP", ci=1)
        ax.set_ylabel(ylabel)
        if i >= 2:
            ax.set_xlabel("Upstream Mach $M_1$")
        metrics_box(ax, f"MAPE = {m['mape']:.4f}%", loc="upper left")
        add_legend(ax, fontsize=8)

    fig.tight_layout(rect=[0, 0, 1, 0.94])
    save(fig, PNG / "naca1135_normal_shock.png")


# ======================================================================
# 2. NACA 1135 Oblique Shock Beta  (grouped by M1)
# ======================================================================
def plot_oblique_shock():
    df = _read("naca1135_oblique_shock_beta.csv")
    m1_vals = sorted(df["M1"].unique())

    fig, ax_main, ax_resid = validation_figure(
        "NACA 1135 Oblique Shock Wave Angle Validation",
        r"Deflection Angle $\theta$ (deg)",
        r"Shock Angle $\beta$ (deg)")

    for i, m1 in enumerate(m1_vals):
        sub = df[df["M1"] == m1]
        theta = sub["theta_deg"].values
        ref_scatter(ax_main, theta, sub["beta_ref_deg"].values,
                    label=f"NACA 1135 M={m1:.0f}", ci=i, mi=i)
        model_line(ax_main, theta, sub["beta_orp_deg"].values,
                   label=f"ORP M={m1:.0f}", ci=i)
        plot_residuals(ax_resid, theta, sub["rel_error_pct"].values,
                       ci=i, mi=i, label=f"M={m1:.0f}")

    add_legend(ax_main)
    m_all = compute_metrics(df["beta_ref_deg"].values, df["beta_orp_deg"].values)
    metrics_box(ax_main, f"Overall MAPE = {m_all['mape']:.4f}%")
    save(fig, PNG / "naca1135_oblique_shock_beta.png")


# ======================================================================
# 3. Prandtl-Meyer Expansion Angle
# ======================================================================
def plot_prandtl_meyer():
    df = _read("naca1135_prandtl_meyer_nu.csv")
    m = compute_metrics(df["nu_tabular_deg"].values, df["nu_orp_deg"].values)

    fig, ax_main, ax_resid = validation_figure(
        "NACA 1135 Prandtl-Meyer Function Validation",
        "Mach Number",
        r"$\nu$ (deg)")

    ref_scatter(ax_main, df["M"], df["nu_tabular_deg"], label="NACA 1135", ci=0, mi=0)
    model_line(ax_main, df["M"], df["nu_orp_deg"], label="ORP", ci=1)
    add_legend(ax_main)
    metrics_box(ax_main, f"n = {m['n']}\nMAE = {m['mae']:.4g} deg", loc="lower right")

    # Residuals as absolute error (% is meaningless near nu=0)
    ax_resid.set_ylabel("Error (deg)")
    plot_residuals(ax_resid, df["M"], df["abs_err_deg"].values, ci=0, mi=0)
    save(fig, PNG / "naca1135_prandtl_meyer_nu.png")


# ======================================================================
# 4. Rayleigh Pitot Cp,max
# ======================================================================
def plot_rayleigh_cpmax():
    df = _read("rayleigh_pitot_cpmax.csv")
    m = compute_metrics(df["cp_max_naca1135"].values, df["cp_max_orp"].values)

    fig, ax_main, ax_resid = validation_figure(
        "Rayleigh Pitot $C_{p,max}$ Validation",
        "Mach Number",
        r"$C_{p,max}$")

    ref_scatter(ax_main, df["mach"], df["cp_max_naca1135"],
                label="NACA 1135", ci=0, mi=0)
    model_line(ax_main, df["mach"], df["cp_max_orp"], label="ORP", ci=1)
    add_legend(ax_main)
    metrics_box(ax_main, f"n = {m['n']}\nMAE = {m['mae']:.2e}", loc="lower right")

    plot_residuals(ax_resid, df["mach"], m["pct_errors"], ci=0, mi=0)
    save(fig, PNG / "rayleigh_pitot_cpmax.png")


# ======================================================================
# 5. Sutherland Viscosity
# ======================================================================
def plot_sutherland_viscosity():
    df = _read("sutherland_viscosity_air.csv")
    m = compute_metrics(df["viscosity_ref_Pa_s"].values,
                        df["viscosity_orp_Pa_s"].values)

    fig, ax_main, ax_resid = validation_figure(
        "Sutherland Viscosity Model Validation",
        "Temperature (K)",
        r"Dynamic Viscosity $\mu$ (Pa$\cdot$s)")

    ref_scatter(ax_main, df["temperature_K"], df["viscosity_ref_Pa_s"],
                label="Incropera / NIST", ci=0, mi=0)
    model_line(ax_main, df["temperature_K"], df["viscosity_orp_Pa_s"],
               label="ORP (Sutherland)", ci=1)
    add_legend(ax_main)
    metrics_box(ax_main, f"n = {m['n']}  MAPE = {m['mape']:.3f}%")

    plot_residuals(ax_resid, df["temperature_K"], df["rel_error_pct"].values,
                   ci=0, mi=0)
    save(fig, PNG / "sutherland_viscosity_air.png")


# ======================================================================
# 6. US Standard Atmosphere Speed of Sound
# ======================================================================
def plot_speed_of_sound():
    df = _read("us_standard_atmosphere_speed_of_sound.csv")
    df_sorted = df.sort_values("temperature_K")
    m = compute_metrics(df["speed_of_sound_ref_mps"].values,
                        df["speed_of_sound_orp_mps"].values)

    fig, ax_main, ax_resid = validation_figure(
        "US Standard Atmosphere Speed of Sound Validation",
        "Temperature (K)",
        "Speed of Sound (m/s)")

    ref_scatter(ax_main, df_sorted["temperature_K"],
                df_sorted["speed_of_sound_ref_mps"],
                label="US Std Atm 1976", ci=0, mi=0)
    model_line(ax_main, df_sorted["temperature_K"],
               df_sorted["speed_of_sound_orp_mps"],
               label="ORP", ci=1)
    add_legend(ax_main)
    metrics_box(ax_main, f"n = {m['n']}\nMAPE = {m['mape']:.4f}%", loc="lower right")

    plot_residuals(ax_resid, df["temperature_K"], df["rel_error_pct"].values,
                   ci=0, mi=0)
    save(fig, PNG / "us_standard_atmosphere_speed_of_sound.png")


# ======================================================================
# 7. Taylor-Maccoll Cone Shock  (grouped by M1)
# ======================================================================
def plot_taylor_maccoll():
    df = _read("taylor_maccoll_cone_shock.csv")
    m1_vals = sorted(df["M1"].unique())

    fig, ax_main, ax_resid = validation_figure(
        "Taylor-Maccoll Cone Shock Angle Validation",
        r"Cone Half-Angle (deg)",
        r"Shock Angle (deg)")

    for i, m1 in enumerate(m1_vals):
        sub = df[df["M1"] == m1].sort_values("cone_half_angle_deg")
        x = sub["cone_half_angle_deg"].values
        ref_scatter(ax_main, x, sub["shock_ref_deg"].values,
                    label=f"Reference M={m1:.0f}", ci=i, mi=i)
        model_line(ax_main, x, sub["shock_orp_deg"].values,
                   label=f"ORP M={m1:.0f}", ci=i)
        plot_residuals(ax_resid, x, sub["rel_error_pct"].values,
                       ci=i, mi=i, label=f"M={m1:.0f}")

    m_all = compute_metrics(df["shock_ref_deg"].values,
                            df["shock_orp_deg"].values)
    add_legend(ax_main)
    metrics_box(ax_main, f"Overall MAPE = {m_all['mape']:.3f}%")
    save(fig, PNG / "taylor_maccoll_cone_shock.png")


# ======================================================================
# 8. Barrowman Axial CD vs Mach  (ORP only, stacked components)
# ======================================================================
def plot_barrowman_axial_cd():
    df = _read("barrowman_axial_cd_mach.csv")
    geometries = df["geometry"].unique()

    ncols = len(geometries)
    fig, axes = multi_validation_figure(
        1, ncols,
        "ORP Axial Drag Coefficient Breakdown",
        figsize=(6 * ncols, 5))
    if ncols == 1:
        axes = [axes]

    for col_idx, geom in enumerate(geometries):
        ax = axes[col_idx]
        sub = df[df["geometry"] == geom].sort_values("Mach")
        mach = sub["Mach"].values

        ax.fill_between(mach, 0, sub["frictionCD"], alpha=0.35,
                        color=C_BLUE, label="Friction")
        ax.fill_between(mach, sub["frictionCD"],
                        sub["frictionCD"] + sub["pressureCD"],
                        alpha=0.35, color=C_ORANGE, label="Pressure")
        ax.fill_between(mach, sub["frictionCD"] + sub["pressureCD"],
                        sub["frictionCD"] + sub["pressureCD"] + sub["baseCD"],
                        alpha=0.35, color=C_GREEN, label="Base")
        ax.plot(mach, sub["CD"], color="black", linewidth=2.0, label="Total")

        ax.set_xlabel("Mach Number")
        ax.set_ylabel(r"$C_D$")
        pretty = geom.replace("_", " ").title()
        ax.set_title(pretty, fontsize=11)
        add_legend(ax, fontsize=8, loc="upper left")

    fig.tight_layout(rect=[0, 0, 1, 0.93])
    save(fig, PNG / "barrowman_axial_cd_mach.png")


# ======================================================================
# 9. Transonic Cmq Augmentation  (appendix, internal)
# ======================================================================
def plot_transonic_cmq():
    df = _read("dynamic_stability_benchmark.csv")

    fig, (ax_cmq, ax_k) = plt.subplots(1, 2, figsize=WIDE_FIG)
    fig.suptitle("Transonic Pitch Damping Augmentation (Appendix)",
                 fontsize=13, fontweight="bold")

    # Cmq vs Mach
    ax_cmq.plot(df["mach"], df["cmq"], color=C_BLUE, linewidth=2.0,
                label=r"$C_{m_q}$")
    ax_cmq.plot(df["mach"], df["cm_alpha_dot"], color=C_ORANGE, linewidth=2.0,
                linestyle="--", label=r"$C_{m_{\dot\alpha}}$")
    ax_cmq.set_xlabel("Mach Number")
    ax_cmq.set_ylabel("Damping Derivative (rad$^{-1}$)")
    ax_cmq.set_title(r"Pitch Damping $C_{m_q}$ and $C_{m_{\dot\alpha}}$")
    add_legend(ax_cmq)

    # k_transonic multiplier
    ax_k.plot(df["mach"], df["k_transonic"], color=C_RED, linewidth=2.0)
    ax_k.set_xlabel("Mach Number")
    ax_k.set_ylabel(r"$k_{transonic}$")
    ax_k.set_title("Transonic Augmentation Factor")
    ax_k.set_ylim(bottom=0)

    fig.tight_layout(rect=[0, 0, 1, 0.93])
    save(fig, PNG / "transonic_cmq_augmentation.png")


# ======================================================================
# 10. Vortex Sideforce Ramp  (appendix, internal)
# ======================================================================
def plot_vortex_sideforce():
    df = _read("dynamic_stability_benchmark.csv")

    fig, (ax_cy, ax_cn) = plt.subplots(1, 2, figsize=WIDE_FIG)
    fig.suptitle("Vortex Sideforce and Yawing Moment vs Mach (Appendix)",
                 fontsize=13, fontweight="bold")

    ax_cy.plot(df["mach"], df["cy_pa"], color=C_BLUE, linewidth=2.0,
               label=r"$C_{Y_{p\alpha}}$ (ORP)")
    ax_cy.set_xlabel("Mach Number")
    ax_cy.set_ylabel(r"$C_{Y_{p\alpha}}$")
    ax_cy.set_title("Magnus Side Force Derivative")
    add_legend(ax_cy)

    ax_cn.plot(df["mach"], df["cn_pa"], color=C_ORANGE, linewidth=2.0,
               label=r"$C_{n_{p\alpha}}$ (ORP)")
    ax_cn.set_xlabel("Mach Number")
    ax_cn.set_ylabel(r"$C_{n_{p\alpha}}$")
    ax_cn.set_title("Magnus Yawing Moment Derivative")
    add_legend(ax_cn)

    fig.tight_layout(rect=[0, 0, 1, 0.93])
    save(fig, PNG / "vortex_sideforce_ramp.png")


# ======================================================================
# 11. AGARD-B Total CD Validation
# ======================================================================
def plot_agard_b_total():
    df_exp = _read("AEDC_AGARD_B_CD_vs_Mach_near_zero_alpha.csv")
    df_tr  = _read("agard_b_transition_sensitivity.csv")

    df_smooth  = df_tr[df_tr["surfaceMode"] == "natural_transition"].copy()
    df_ordinary = df_tr[df_tr["surfaceMode"] == "ordinary_finish_bracket"].copy()
    if df_smooth.empty or df_ordinary.empty:
        print("  SKIP agard_b_total — missing transition data")
        return

    m_exp = df_exp["Mach"].values.astype(float)
    cd_exp = df_exp["C_D_total"].values.astype(float)
    mmax = float(m_exp.max())

    sm = df_smooth[df_smooth["Mach"] <= mmax + 0.01].sort_values("Mach")
    om = df_ordinary[df_ordinary["Mach"] <= mmax + 0.01].sort_values("Mach")

    cd_smooth = np.interp(m_exp, sm["Mach"].values, sm["CD_wing_ref"].values)
    cd_ordinary = np.interp(m_exp, om["Mach"].values, om["CD_wing_ref"].values)
    ms = compute_metrics(cd_exp, cd_smooth)
    mo = compute_metrics(cd_exp, cd_ordinary)

    fig, ax_main, ax_resid = validation_figure(
        "AGARD-B Total Drag Validation (AEDC 4T)",
        "Mach Number",
        r"$C_D$ (wing-area ref)")

    ax_main.fill_between(sm["Mach"], sm["CD_wing_ref"],
                         om["CD_wing_ref"],
                         alpha=0.18, color="gray",
                         label="Surface-condition bracket")
    model_line(ax_main, sm["Mach"], sm["CD_wing_ref"],
               label="ORP natural-transition", ci=0)
    model_line(ax_main, om["Mach"], om["CD_wing_ref"],
               label="ORP ordinary-finish", ci=1, linestyle="--")
    ref_scatter(ax_main, m_exp, cd_exp, label="AEDC experiment", ci=7, mi=0)
    add_legend(ax_main)
    metrics_box(ax_main,
                f"Natural MAE = {ms['mae']:.4f}\n"
                f"Ordinary MAE = {mo['mae']:.4f}")

    plot_residuals(ax_resid, m_exp, ms["pct_errors"], ci=0, mi=0,
                   label="Natural")
    plot_residuals(ax_resid, m_exp, mo["pct_errors"], ci=1, mi=1,
                   label="Ordinary")
    add_legend(ax_resid, fontsize=7)
    save(fig, PNG / "agard_b_total_cd_validation.png")


# ======================================================================
# 12. AGARD-B Component Diagnostics
# ======================================================================
def plot_agard_b_components():
    df_exp = _read("AEDC_AGARD_B_CD_vs_Mach_near_zero_alpha.csv")
    df_tr  = _read("agard_b_transition_sensitivity.csv")

    df_smooth   = df_tr[df_tr["surfaceMode"] == "natural_transition"].copy()
    df_ordinary = df_tr[df_tr["surfaceMode"] == "ordinary_finish_bracket"].copy()
    if df_smooth.empty or df_ordinary.empty:
        print("  SKIP agard_b_components — missing transition data")
        return

    m_exp = df_exp["Mach"].values.astype(float)
    mmax = float(m_exp.max())
    sm = df_smooth[df_smooth["Mach"] <= mmax + 0.01].sort_values("Mach")
    om = df_ordinary[df_ordinary["Mach"] <= mmax + 0.01].sort_values("Mach")

    fig, axes = multi_validation_figure(
        1, 3, "AGARD-B Component Diagnostics",
        figsize=(15, 6))

    panels = [
        ("Forebody Drag", "C_D_F", "forebodyCD_wing_ref",
         r"$C_{D,F}$ (wing-area ref)"),
        ("Base Drag", "C_D_b", "baseCD_wing_ref",
         r"$C_{D,b}$ (wing-area ref)"),
    ]

    for i, (title, exp_col, or_col, ylabel) in enumerate(panels):
        ax = axes[i]
        ax.fill_between(sm["Mach"], sm[or_col], om[or_col],
                        alpha=0.18, color="gray")
        model_line(ax, sm["Mach"], sm[or_col], label="Natural", ci=0)
        model_line(ax, om["Mach"], om[or_col], label="Ordinary", ci=1,
                   linestyle="--")
        ref_scatter(ax, m_exp, df_exp[exp_col].values, label="AEDC", ci=7, mi=0)
        ax.set_title(title, fontsize=11)
        ax.set_ylabel(ylabel)
        ax.set_xlabel("Mach Number")
        add_legend(ax, fontsize=8)

    # Third panel: transition indicator
    ax_tr = axes[2]
    if "Re_over_Re_transition" in sm.columns:
        ax_tr.plot(sm["Mach"], sm["Re_over_Re_transition"],
                   color=C_BLUE, linewidth=2.0, label=r"$Re / Re_{tr}$")
        ax_tr.axhline(1.0, color="black", linewidth=1.0, linestyle=":")
    ax_tr.set_title("Natural-Transition Indicator", fontsize=11)
    ax_tr.set_xlabel("Mach Number")
    ax_tr.set_ylabel(r"$Re / Re_{tr}$")
    add_legend(ax_tr, fontsize=8)

    fig.tight_layout(rect=[0, 0, 1, 0.90])
    save(fig, PNG / "agard_b_component_diagnostics.png")


# ======================================================================
# 13. NACA RM A52H28 Validation  (2×3 grid, 5 shapes + summary)
# ======================================================================
SHAPES_A52 = [
    ("cone_n1",             "Sharp Cone (n=1)"),
    ("paraboloid_n0p5",     "Paraboloid (n=0.5)"),
    ("quarter_power_n0p25", "1/4 Power (n=0.25)"),
    ("LD_Haack",            "Von Karman (L-D Haack)"),
    ("LV_ogive",            "L-V Ogive (L/D=2.93)"),
]


def plot_a52h28_validation():
    df_exp = _read("NACA_RM_A52H28_CD_vs_Mach_TEST_MATRIX.csv")
    df_or  = _read("naca_rm_a52h28_openrocket_cd.csv")

    fig, axes = multi_validation_figure(
        2, 3, "NACA RM A52H28 Nose-Family Foredrag Validation",
        figsize=(14, 9))
    axes = axes.ravel()

    overall_obs, overall_pred = [], []
    shape_metrics = []

    for i, (sid, label) in enumerate(SHAPES_A52):
        ax = axes[i]
        col = f"C_DF_{sid}"
        exp_s = df_exp[["Mach", col]].copy()
        exp_s[col] = pd.to_numeric(exp_s[col], errors="coerce")
        exp_s = exp_s.dropna(subset=[col])
        if exp_s.empty:
            ax.set_title(label, fontsize=11)
            continue

        mach_exp = exp_s["Mach"].values.astype(float)
        obs = exp_s[col].values.astype(float)
        pred = np.interp(mach_exp,
                         df_or["Mach"].values.astype(float),
                         df_or[col].values.astype(float))
        m = compute_metrics(obs, pred)
        shape_metrics.append((label, m))
        overall_obs.extend(obs.tolist())
        overall_pred.extend(pred.tolist())

        model_line(ax, df_or["Mach"], df_or[col], label="ORP", ci=i)
        ref_scatter(ax, mach_exp, obs, label="Digitized NACA", ci=7, mi=0)
        # Error whiskers
        ax.vlines(mach_exp, obs, pred, color=TAB10[i], alpha=0.4, linewidth=1.2)

        ax.set_title(label, fontsize=11, fontweight="bold")
        metrics_box(ax,
                    f"n = {m['n']}\nMAE = {m['mae']:.4f}\nMAPE = {m['mape']:.1f}%",
                    loc="upper left")
        ax.set_xlim(1.15, 3.8)
        ax.set_ylim(0.04, 0.27)
        if i >= 3:
            ax.set_xlabel("Mach Number")
        if i % 3 == 0:
            ax.set_ylabel(r"$C_{D,F}$")
        add_legend(ax, fontsize=7)

    # Summary panel
    ax_sum = axes[-1]
    ax_sum.axis("off")
    overall = compute_metrics(np.array(overall_obs), np.array(overall_pred))
    lines = [
        "A52H28 Benchmark Summary",
        f"Total points: {overall['n']}",
        f"Overall MAE: {overall['mae']:.4f}",
        f"Overall MAPE: {overall['mape']:.1f}%",
        "",
        "Per-shape MAPE:",
    ]
    for label, m in sorted(shape_metrics, key=lambda x: -x[1]["mape"]):
        lines.append(f"  {label}: {m['mape']:.1f}%")
    ax_sum.text(0.05, 0.95, "\n".join(lines), transform=ax_sum.transAxes,
                va="top", fontsize=10, family="monospace")

    # Shared legend
    from matplotlib.lines import Line2D
    handles = [
        Line2D([], [], color=C_BLUE, linewidth=2, label="ORP"),
        Line2D([], [], color="black", marker="o", linestyle="",
               markersize=6, label="Digitized NACA"),
    ]
    fig.legend(handles=handles, loc="lower center", ncol=2, frameon=False)
    fig.tight_layout(rect=[0, 0.04, 1, 0.93])
    save(fig, PNG / "naca_rm_a52h28_validation.png")


# ======================================================================
# 14. NACA RM A52H28 Trend Sweep  (all shapes on one panel + residual)
# ======================================================================
def plot_a52h28_trend_sweep():
    df_exp = _read("NACA_RM_A52H28_CD_vs_Mach_TEST_MATRIX.csv")
    df_or  = _read("naca_rm_a52h28_openrocket_cd.csv")

    fig, ax_main, ax_resid = validation_figure(
        "NACA RM A52H28 Foredrag Trend Comparison",
        "Mach Number",
        r"Foredrag Coefficient $C_{D,F}$",
        figsize=(9, 6))

    for i, (sid, label) in enumerate(SHAPES_A52):
        col = f"C_DF_{sid}"
        exp_s = df_exp[["Mach", col]].copy()
        exp_s[col] = pd.to_numeric(exp_s[col], errors="coerce")
        exp_s = exp_s.dropna(subset=[col])

        model_line(ax_main, df_or["Mach"], df_or[col], label=f"ORP {label}", ci=i)
        if not exp_s.empty:
            mach_exp = exp_s["Mach"].values.astype(float)
            obs = exp_s[col].values.astype(float)
            ref_scatter(ax_main, mach_exp, obs, label=f"Exp {label}",
                        ci=i, mi=i, s=35)
            pred = np.interp(mach_exp,
                             df_or["Mach"].values.astype(float),
                             df_or[col].values.astype(float))
            m = compute_metrics(obs, pred)
            plot_residuals(ax_resid, mach_exp, m["pct_errors"],
                           ci=i, mi=i)

    add_legend(ax_main, fontsize=7, ncol=2)
    save(fig, PNG / "naca_rm_a52h28_trend_sweep.png")


# ======================================================================
# 15. NACA TN 3393 Base Pressure
# ======================================================================
def plot_tn3393_base_pressure():
    df_exp = _read("NACA_TN_3393_digitized_points.csv")
    or_file = CSV / "naca_tn_3393_openrocket_base.csv"
    df_or = pd.read_csv(or_file) if or_file.exists() else None

    fig, ax_main, ax_resid = validation_figure(
        "NACA TN 3393 Base Drag Validation (Ogive-Cylinder)",
        "Mach Number",
        r"Base Drag Coefficient $C_{D,b}$")

    marker_map = {"laminar": 0, "turbulent (fixed roughness)": 1}
    color_map  = {"laminar": 0, "turbulent (fixed roughness)": 3}

    for layer in df_exp["boundary_layer"].unique():
        sub = df_exp[df_exp["boundary_layer"] == layer]
        ci = color_map.get(layer, 2)
        mi = marker_map.get(layer, 2)
        ref_scatter(ax_main, sub["Mach"], sub["Cpb"],
                    label=f"TN 3393 ({layer})", ci=ci, mi=mi)

    if df_or is not None and "baseCD" in df_or.columns:
        for layer in df_or["boundary_layer"].unique():
            sub_or = df_or[df_or["boundary_layer"] == layer].sort_values("Mach")
            ci = color_map.get(layer, 2)
            model_line(ax_main, sub_or["Mach"], sub_or["baseCD"],
                       label=f"ORP ({layer})", ci=ci)

            # Compute residuals against matching experimental points
            sub_exp = df_exp[df_exp["boundary_layer"] == layer]
            if not sub_exp.empty:
                mach_exp = sub_exp["Mach"].values.astype(float)
                obs = sub_exp["Cpb"].values.astype(float)
                pred = np.interp(mach_exp,
                                 sub_or["Mach"].values.astype(float),
                                 sub_or["baseCD"].values.astype(float))
                m = compute_metrics(obs, pred)
                mi = marker_map.get(layer, 2)
                plot_residuals(ax_resid, mach_exp, m["pct_errors"],
                               ci=ci, mi=mi, label=layer)

    add_legend(ax_main)
    add_legend(ax_resid, fontsize=7)
    save(fig, PNG / "naca_tn_3393_base_pressure.png")


# ======================================================================
# 16. Basic Finner Total Drag (ADA636861)
# ======================================================================
def plot_basic_finner():
    df = _read("basic_finner_comparison.csv")
    m = compute_metrics(df["CX0_exp"].values, df["CD_orp"].values)

    fig, ax_main, ax_resid = validation_figure(
        "ADA636861 Basic Finner Total Drag Validation",
        "Mach Number",
        r"Axial Force Coefficient $C_{X0}$",
        figsize=(9, 6))

    ref_scatter(ax_main, df["Mach"], df["CX0_exp"],
                label="ADA636861 (aeroballistic range)", ci=0, mi=0, s=70)
    model_line(ax_main, df["Mach"], df["CD_orp"], label="ORP", ci=1)

    # Stacked component fill
    ax_main.fill_between(df["Mach"], 0, df["frictionCD"],
                         alpha=0.15, color=C_BLUE, label="Friction")
    ax_main.fill_between(df["Mach"], df["frictionCD"],
                         df["frictionCD"] + df["pressureCD"],
                         alpha=0.15, color=C_ORANGE, label="Pressure")
    ax_main.fill_between(df["Mach"], df["frictionCD"] + df["pressureCD"],
                         df["frictionCD"] + df["pressureCD"] + df["baseCD"],
                         alpha=0.15, color=C_GREEN, label="Base")

    add_legend(ax_main, fontsize=8)
    metrics_box(ax_main,
                f"n = {m['n']}\nMAPE = {m['mape']:.1f}%\nBias: underpredicts",
                loc="upper right")

    plot_residuals(ax_resid, df["Mach"], df["error_pct"].values, ci=0, mi=0)
    ax_resid.axhline(0, color="black", linewidth=0.5)
    save(fig, PNG / "basic_finner_total_drag.png")


# ======================================================================
# 17. Hypersonic Cone Drag (DTIC AD0487365)
# ======================================================================
def plot_hypersonic_cone():
    df = _read("hypersonic_cone_comparison.csv")
    thetas = sorted(df["theta_deg"].unique())

    fig, ax_main, ax_resid = validation_figure(
        "DTIC AD0487365 Hypersonic Cone Drag Validation",
        "Mach Number",
        r"Total Drag Coefficient $C_D$",
        figsize=(9, 6))

    for i, theta in enumerate(thetas):
        sub = df[df["theta_deg"] == theta].sort_values("Mach")
        ref_scatter(ax_main, sub["Mach"], sub["CD_exp"],
                    label=f"Experiment θ={theta:.0f}°", ci=i, mi=i, s=65)
        model_line(ax_main, sub["Mach"], sub["CD_orp"],
                   label=f"ORP θ={theta:.0f}°", ci=i)
        plot_residuals(ax_resid, sub["Mach"], sub["error_pct"].values,
                       ci=i, mi=i, label=f"θ={theta:.0f}°")

    m_all = compute_metrics(df["CD_exp"].values, df["CD_orp"].values)
    add_legend(ax_main, fontsize=8)
    metrics_box(ax_main,
                f"n = {m_all['n']}\nMAPE = {m_all['mape']:.1f}%",
                loc="upper left")
    add_legend(ax_resid, fontsize=7)
    save(fig, PNG / "hypersonic_cone_drag.png")


# ======================================================================
# 18. NASA TM X-653 Static Stability (CNa + xCP)
# ======================================================================
def plot_tm_x653_stability():
    # Use the pointwise comparison CSV from the benchmark test (authoritative)
    df = _read("nasa_tm_x653_pointwise_comparison.csv")

    df_cn = df[df["metric"] == "C_N"].copy()
    df_xcp = df[df["metric"] == "X_CP_d"].copy()

    if df_cn.empty or df_xcp.empty:
        print("  SKIP tm_x653 — no data in pointwise comparison CSV")
        return

    fig, (ax_cn, ax_xcp) = plt.subplots(1, 2, figsize=WIDE_FIG)
    fig.suptitle("NASA TM X-653 Static Stability Validation (NSCFB)",
                 fontsize=13, fontweight="bold")

    # CNa panel
    mach_cn = df_cn["Mach"].values.astype(float)
    cn_exp = df_cn["observed"].values.astype(float)
    cn_orp = df_cn["predicted"].values.astype(float)
    m_cn = compute_metrics(cn_exp, cn_orp)

    ref_scatter(ax_cn, mach_cn, cn_exp, label="TM X-653", ci=0, mi=0)
    model_line(ax_cn, mach_cn, cn_orp, label="ORP", ci=1)
    ax_cn.set_xlabel("Mach Number")
    ax_cn.set_ylabel(r"$C_{N_\alpha}$ (per deg)")
    ax_cn.set_title(r"Normal Force Slope $C_{N_\alpha}$")
    metrics_box(ax_cn, f"MAPE = {m_cn['mape']:.1f}%", loc="upper right")
    add_legend(ax_cn)

    # xCP panel
    mach_xcp = df_xcp["Mach"].values.astype(float)
    xcp_exp = df_xcp["observed"].values.astype(float)
    xcp_orp = df_xcp["predicted"].values.astype(float)
    m_xcp = compute_metrics(xcp_exp, xcp_orp)

    ref_scatter(ax_xcp, mach_xcp, xcp_exp, label="TM X-653", ci=0, mi=0)
    model_line(ax_xcp, mach_xcp, xcp_orp, label="ORP", ci=1)
    ax_xcp.set_xlabel("Mach Number")
    ax_xcp.set_ylabel(r"$x_{CP}/d$ from juncture")
    ax_xcp.set_title("Center of Pressure")
    metrics_box(ax_xcp, f"MAPE = {m_xcp['mape']:.1f}%", loc="upper right")
    add_legend(ax_xcp)

    fig.tight_layout(rect=[0, 0, 1, 0.93])
    save(fig, PNG / "nasa_tm_x653_stability.png")


# ======================================================================
# 19. TN 3650 Fin Wave Drag
# ======================================================================
TN3650_CSV = Path(
    "core/src/test/resources/info/openrocket/core/aerodynamics/"
    "naca_tn_3650_wing_drag.csv"
)


def plot_tn3650_fin_wave_drag():
    csv_path = SCRIPT_DIR.parent / TN3650_CSV
    if not csv_path.exists():
        print(f"  SKIP tn3650 — {csv_path} not found")
        return
    df = pd.read_csv(csv_path)
    tcs = sorted(df["t_over_c"].unique())

    fig, ax_main, ax_resid = validation_figure(
        "NACA TN 3650 Fin Wave Drag Validation (60° Delta)",
        "Mach Number",
        r"Wing Wave Drag $C_{D_w}$")

    for i, tc in enumerate(tcs):
        sub = df[df["t_over_c"] == tc].sort_values("mach")
        ref_scatter(ax_main, sub["mach"], sub["cdw_exp"],
                    label=f"TN 3650 t/c={tc}", ci=i, mi=i, s=65)
        model_line(ax_main, sub["mach"], sub["cdw_theory"],
                   label=f"DATCOM t/c={tc}", ci=i)
        m = compute_metrics(sub["cdw_exp"].values, sub["cdw_theory"].values)
        plot_residuals(ax_resid, sub["mach"], m["pct_errors"],
                       ci=i, mi=i, label=f"t/c={tc}")

    m_all = compute_metrics(df["cdw_exp"].values, df["cdw_theory"].values)
    add_legend(ax_main)
    metrics_box(ax_main, f"n = {m_all['n']}\nMAPE = {m_all['mape']:.1f}%")
    add_legend(ax_resid, fontsize=7)
    save(fig, PNG / "naca_tn_3650_fin_wave_drag.png")


# ======================================================================
# 20. Tobak Cmq Pitch Damping
# ======================================================================
def plot_tobak_cmq():
    csv_path = CSV / "tobak_cmq_benchmark.csv"
    if not csv_path.exists():
        # Check alternate location
        csv_path = SCRIPT_DIR.parent / "core" / "paper" / "data" / "csv" / "tobak_cmq_benchmark.csv"
    if not csv_path.exists():
        print("  SKIP tobak_cmq — CSV not found")
        return
    df = pd.read_csv(csv_path, comment="#")
    thetas = sorted(df["half_angle_deg"].unique())

    fig, (ax_tobak, ax_orp) = plt.subplots(1, 2, figsize=WIDE_FIG)
    fig.suptitle("Tobak TN 3788 Cone Pitch Damping Comparison",
                 fontsize=13, fontweight="bold")

    for i, theta in enumerate(thetas):
        sub = df[df["half_angle_deg"] == theta].sort_values("Mach")
        ax_tobak.plot(sub["Mach"], sub["Cmq_tobak"], color=TAB10[i],
                      linewidth=2.0, label=f"Tobak θ={theta:.0f}°")
        ax_tobak.plot(sub["Mach"], sub["Cmq_tobak"], color=TAB10[i],
                      marker=REF_MARKERS[i], linestyle="none", markersize=7)

    ax_tobak.set_xlabel("Mach Number")
    ax_tobak.set_ylabel(r"$C_{m_q}$ (Tobak exact theory)")
    ax_tobak.set_title("Tobak TN 3788 Exact Theory")
    add_legend(ax_tobak)

    for i, theta in enumerate(thetas):
        sub = df[df["half_angle_deg"] == theta].sort_values("Mach")
        ax_orp.plot(sub["Mach"], sub["Cmq_ORP"], color=TAB10[i],
                    linewidth=2.0, label=f"ORP θ={theta:.0f}°")

    ax_orp.set_xlabel("Mach Number")
    ax_orp.set_ylabel(r"$C_{m_q}$ (ORP strip theory)")
    ax_orp.set_title("ORP Strip-Theory Implementation")
    add_legend(ax_orp)

    fig.tight_layout(rect=[0, 0, 1, 0.93])
    save(fig, PNG / "tobak_cmq_comparison.png")


# ======================================================================
# Registry and main
# ======================================================================
PLOTS = {
    "normal_shock":      plot_normal_shock,
    "oblique_shock":     plot_oblique_shock,
    "prandtl_meyer":     plot_prandtl_meyer,
    "rayleigh_cpmax":    plot_rayleigh_cpmax,
    "sutherland":        plot_sutherland_viscosity,
    "speed_of_sound":    plot_speed_of_sound,
    "taylor_maccoll":    plot_taylor_maccoll,
    "barrowman_axial":   plot_barrowman_axial_cd,
    "transonic_cmq":     plot_transonic_cmq,
    "vortex_sideforce":  plot_vortex_sideforce,
    "agard_b_total":     plot_agard_b_total,
    "agard_b_components":plot_agard_b_components,
    "a52h28_validation": plot_a52h28_validation,
    "a52h28_trend":      plot_a52h28_trend_sweep,
    "tn3393_base":       plot_tn3393_base_pressure,
    "basic_finner":      plot_basic_finner,
    "hypersonic_cone":   plot_hypersonic_cone,
    "tm_x653":           plot_tm_x653_stability,
    "tn3650_fin":        plot_tn3650_fin_wave_drag,
    "tobak_cmq":         plot_tobak_cmq,
}


def main():
    apply_style()
    PNG.mkdir(parents=True, exist_ok=True)

    targets = sys.argv[1:] if len(sys.argv) > 1 else list(PLOTS.keys())
    for name in targets:
        fn = PLOTS.get(name)
        if fn is None:
            print(f"Unknown plot: {name}  (available: {', '.join(PLOTS)})")
            continue
        print(f"[{name}]")
        try:
            fn()
        except Exception as e:
            print(f"  ERROR: {e}")


if __name__ == "__main__":
    main()

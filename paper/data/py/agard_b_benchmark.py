from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


SCRIPT_DIR = Path(__file__).resolve().parent
DATA_DIR = SCRIPT_DIR.parent if SCRIPT_DIR.name == "py" else SCRIPT_DIR
CSV_DIR = DATA_DIR / "csv"
PNG_DIR = DATA_DIR / "png"
MD_DIR = DATA_DIR / "md"

AEDC_WING_AREA_FT2 = 0.1841
AEDC_BASE_AREA_FT2 = 0.02086
AEDC_BASE_TO_WING_RATIO = AEDC_BASE_AREA_FT2 / AEDC_WING_AREA_FT2


def resolve_input(filename: str) -> Path:
    candidates = [
        CSV_DIR / filename,
        DATA_DIR / filename,
    ]
    for candidate in candidates:
        if candidate.exists():
            return candidate
    raise FileNotFoundError(f"Could not locate {filename} in {CSV_DIR} or {DATA_DIR}")


def compute_metrics(observed: np.ndarray, predicted: np.ndarray) -> dict:
    errors = predicted - observed
    percent_errors = np.where(np.abs(observed) > 1.0e-12, errors / observed * 100.0, np.nan)
    abs_pct = np.abs(percent_errors)
    return {
        "mae": float(np.mean(np.abs(errors))),
        "rmse": float(np.sqrt(np.mean(errors**2))),
        "mape": float(np.nanmean(abs_pct)),
        "max_pct": float(np.nanmax(abs_pct)),
        "errors": errors,
        "percent_errors": percent_errors,
    }


def compute_bracket_coverage(observed: np.ndarray, lower_candidate: np.ndarray, upper_candidate: np.ndarray) -> dict:
    lower = np.minimum(lower_candidate, upper_candidate)
    upper = np.maximum(lower_candidate, upper_candidate)
    within = (observed >= lower) & (observed <= upper)
    return {
        "covered": int(np.count_nonzero(within)),
        "total": int(observed.size),
        "fraction": float(np.mean(within.astype(float))),
    }


def markdown_table(headers, rows) -> str:
    lines = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join(["---"] * len(headers)) + " |",
    ]
    for row in rows:
        lines.append("| " + " | ".join(row) + " |")
    return "\n".join(lines)


def nearest_component_snapshot(df_components: pd.DataFrame, mach: float, top_n: int = 5) -> list[list[str]]:
    if df_components.empty:
        return []

    rows = df_components.iloc[(df_components["Mach"] - mach).abs().argsort()[:1]]
    if rows.empty:
        return []
    target_mach = float(rows.iloc[0]["Mach"])
    snap = df_components[np.isclose(df_components["Mach"], target_mach, atol=1.0e-9)].copy()
    snap = snap.sort_values("CD_wing_ref", ascending=False).head(top_n)

    table_rows = []
    for _, row in snap.iterrows():
        table_rows.append(
            [
                row["component"],
                row["componentCategory"],
                f"{row['CD_wing_ref']:.5f}",
                f"{row['frictionCD_wing_ref']:.5f}",
                f"{row['pressureCD_wing_ref']:.5f}",
                f"{row['baseCD_wing_ref']:.5f}",
            ]
        )
    return table_rows


def interpolate_mode(df_mode: pd.DataFrame, mach: np.ndarray, column: str) -> np.ndarray:
    return np.interp(mach, df_mode["Mach"].to_numpy(dtype=float), df_mode[column].to_numpy(dtype=float))


def plot_total_validation(
    df_smooth: pd.DataFrame,
    df_ordinary: pd.DataFrame,
    m_exp: np.ndarray,
    exp_total: np.ndarray,
    smooth_metrics: dict,
    ordinary_metrics: dict,
    plot_path: Path,
) -> None:
    measured_max = float(np.max(m_exp))
    smooth_measured = df_smooth[df_smooth["Mach"] <= measured_max + 1.0e-9].copy()
    ordinary_measured = df_ordinary[df_ordinary["Mach"] <= measured_max + 1.0e-9].copy()

    fig, ax = plt.subplots(figsize=(8.8, 5.6))
    ax.fill_between(
        smooth_measured["Mach"],
        smooth_measured["CD_wing_ref"],
        ordinary_measured["CD_wing_ref"],
        color="xkcd:light grey",
        alpha=0.35,
        label="surface-condition bracket",
    )
    ax.plot(
        smooth_measured["Mach"],
        smooth_measured["CD_wing_ref"],
        label="ORP natural-transition",
        color="xkcd:royal blue",
        linewidth=2.4,
    )
    ax.plot(
        ordinary_measured["Mach"],
        ordinary_measured["CD_wing_ref"],
        label="ORP ordinary-finish bracket",
        color="xkcd:dark orange",
        linewidth=2.2,
        linestyle="--",
    )
    ax.scatter(m_exp, exp_total, label="AEDC experiment", color="xkcd:black", s=75, zorder=5)
    ax.set_xlim([float(np.min(m_exp)) - 0.02, measured_max + 0.02])
    ax.set_xlabel("Mach Number")
    ax.set_ylabel(r"$C_D$ (wing-area ref)")
    ax.set_title("AGARD-B Total Drag Diagnostic (AEDC 4T, alpha ~= 0 deg)")
    ax.grid(True, linestyle="--", alpha=0.7)
    ax.legend()
    summary = "\n".join(
        [
            f"Natural MAE = {smooth_metrics['mae']:.4f}",
            f"Ordinary-finish MAE = {ordinary_metrics['mae']:.4f}",
            "Use as diagnostic, not manuscript anchor",
        ]
    )
    ax.text(
        0.03,
        0.97,
        summary,
        transform=ax.transAxes,
        va="top",
        ha="left",
        fontsize=9.6,
        bbox={"boxstyle": "round,pad=0.35", "facecolor": "white", "alpha": 0.9, "edgecolor": "0.75"},
    )
    plt.tight_layout()
    plt.savefig(plot_path, dpi=300, bbox_inches="tight")
    plt.close(fig)


def plot_component_diagnostics(
    df_smooth: pd.DataFrame,
    df_ordinary: pd.DataFrame,
    m_exp: np.ndarray,
    exp_forebody: np.ndarray,
    exp_base: np.ndarray,
    plot_path: Path,
) -> None:
    measured_max = float(np.max(m_exp))
    smooth_measured = df_smooth[df_smooth["Mach"] <= measured_max + 1.0e-9].copy()
    ordinary_measured = df_ordinary[df_ordinary["Mach"] <= measured_max + 1.0e-9].copy()

    fig, axes = plt.subplots(1, 3, figsize=(13, 4.5), sharex=False)
    ax_forebody, ax_base, ax_transition = axes

    ax_forebody.fill_between(
        smooth_measured["Mach"],
        smooth_measured["forebodyCD_wing_ref"],
        ordinary_measured["forebodyCD_wing_ref"],
        color="xkcd:light grey",
        alpha=0.35,
    )
    ax_forebody.plot(smooth_measured["Mach"], smooth_measured["forebodyCD_wing_ref"], color="xkcd:forest green", linewidth=2.1)
    ax_forebody.plot(
        ordinary_measured["Mach"],
        ordinary_measured["forebodyCD_wing_ref"],
        color="xkcd:dark orange",
        linewidth=2.0,
        linestyle="--",
    )
    ax_forebody.scatter(m_exp, exp_forebody, color="xkcd:black", s=58, zorder=5)
    ax_forebody.set_ylabel(r"$C_{D,F}$ (wing-area ref)")
    ax_forebody.set_title("Forebody Drag")
    ax_forebody.grid(True, linestyle="--", alpha=0.7)

    ax_base.fill_between(
        smooth_measured["Mach"],
        smooth_measured["baseCD_wing_ref"],
        ordinary_measured["baseCD_wing_ref"],
        color="xkcd:light grey",
        alpha=0.35,
    )
    ax_base.plot(smooth_measured["Mach"], smooth_measured["baseCD_wing_ref"], color="xkcd:forest green", linewidth=2.1)
    ax_base.plot(
        ordinary_measured["Mach"],
        ordinary_measured["baseCD_wing_ref"],
        color="xkcd:dark orange",
        linewidth=2.0,
        linestyle="--",
    )
    ax_base.scatter(m_exp, exp_base, color="xkcd:black", s=58, zorder=5)
    ax_base.set_xlabel("Mach Number")
    ax_base.set_ylabel(r"$C_{D,b}$ (wing-area ref)")
    ax_base.set_title("Base Drag")
    ax_base.grid(True, linestyle="--", alpha=0.7)

    ax_transition.plot(
        smooth_measured["Mach"],
        smooth_measured["Re_over_Re_transition"],
        color="xkcd:royal blue",
        linewidth=2.2,
        label=r"$Re / Re_{tr}$",
    )
    ax_transition.axhline(1.0, color="black", linewidth=1.0, linestyle=":")
    ax_transition.set_xlabel("Mach Number")
    ax_transition.set_ylabel(r"$Re / Re_{tr}$")
    ax_transition.set_title("Natural-Transition Indicator")
    ax_transition.grid(True, linestyle="--", alpha=0.7)
    ax_transition.legend()

    fig.suptitle("AGARD-B Component and Transition Diagnostics", fontsize=13, fontweight="bold")
    plt.tight_layout()
    plt.subplots_adjust(top=0.84)
    plt.savefig(plot_path, dpi=300, bbox_inches="tight")
    plt.close(fig)


def main() -> None:
    PNG_DIR.mkdir(parents=True, exist_ok=True)
    MD_DIR.mkdir(parents=True, exist_ok=True)

    exp_file = resolve_input("AEDC_AGARD_B_CD_vs_Mach_near_zero_alpha.csv")
    or_file = resolve_input("agard_b_openrocket_cd_mach.csv")
    component_file = resolve_input("agard_b_openrocket_component_cd.csv")
    transition_file = resolve_input("agard_b_transition_sensitivity.csv")

    df_exp = pd.read_csv(exp_file, comment="#")
    df_or = pd.read_csv(or_file)
    df_components = pd.read_csv(component_file)
    df_transition = pd.read_csv(transition_file)

    if "CD_wing_ref" not in df_or.columns:
        df_or["CD_wing_ref"] = df_or["CD"] * AEDC_BASE_TO_WING_RATIO
        df_or["pressureCD_wing_ref"] = df_or["pressureCD"] * AEDC_BASE_TO_WING_RATIO
        df_or["frictionCD_wing_ref"] = df_or["frictionCD"] * AEDC_BASE_TO_WING_RATIO
        df_or["baseCD_wing_ref"] = df_or["baseCD"] * AEDC_BASE_TO_WING_RATIO
        df_or["forebodyCD_wing_ref"] = (df_or["pressureCD"] + df_or["frictionCD"]) * AEDC_BASE_TO_WING_RATIO

    if "forebodyCD" not in df_or.columns:
        df_or["forebodyCD"] = df_or["pressureCD"] + df_or["frictionCD"]

    df_smooth = df_transition[df_transition["surfaceMode"] == "natural_transition"].copy()
    df_ordinary = df_transition[df_transition["surfaceMode"] == "ordinary_finish_bracket"].copy()
    if df_smooth.empty:
        raise RuntimeError("agard_b_transition_sensitivity.csv is missing the natural_transition rows")
    if df_ordinary.empty:
        raise RuntimeError("agard_b_transition_sensitivity.csv is missing the ordinary_finish_bracket rows")

    m_exp = df_exp["Mach"].to_numpy(dtype=float)

    smooth_total = interpolate_mode(df_smooth, m_exp, "CD_wing_ref")
    smooth_forebody = interpolate_mode(df_smooth, m_exp, "forebodyCD_wing_ref")
    smooth_base = interpolate_mode(df_smooth, m_exp, "baseCD_wing_ref")

    ordinary_total = interpolate_mode(df_ordinary, m_exp, "CD_wing_ref")
    ordinary_forebody = interpolate_mode(df_ordinary, m_exp, "forebodyCD_wing_ref")
    ordinary_base = interpolate_mode(df_ordinary, m_exp, "baseCD_wing_ref")

    exp_total = df_exp["C_D_total"].to_numpy(dtype=float)
    exp_forebody = df_exp["C_D_F"].to_numpy(dtype=float)
    exp_base = df_exp["C_D_b"].to_numpy(dtype=float)

    smooth_total_metrics = compute_metrics(exp_total, smooth_total)
    smooth_forebody_metrics = compute_metrics(exp_forebody, smooth_forebody)
    smooth_base_metrics = compute_metrics(exp_base, smooth_base)

    ordinary_total_metrics = compute_metrics(exp_total, ordinary_total)
    ordinary_forebody_metrics = compute_metrics(exp_forebody, ordinary_forebody)
    ordinary_base_metrics = compute_metrics(exp_base, ordinary_base)

    total_bracket = compute_bracket_coverage(exp_total, smooth_total, ordinary_total)
    forebody_bracket = compute_bracket_coverage(exp_forebody, smooth_forebody, ordinary_forebody)
    base_bracket = compute_bracket_coverage(exp_base, smooth_base, ordinary_base)

    print("Metrics vs AGARD-B experimental:")
    print(
        f"  Natural-transition total     MAE={smooth_total_metrics['mae']:.5f} "
        f"RMSE={smooth_total_metrics['rmse']:.5f} MAPE={smooth_total_metrics['mape']:.1f}%"
    )
    print(
        f"  Ordinary-finish total        MAE={ordinary_total_metrics['mae']:.5f} "
        f"RMSE={ordinary_total_metrics['rmse']:.5f} MAPE={ordinary_total_metrics['mape']:.1f}%"
    )
    print(
        f"  Natural-transition forebody  MAE={smooth_forebody_metrics['mae']:.5f} "
        f"RMSE={smooth_forebody_metrics['rmse']:.5f} MAPE={smooth_forebody_metrics['mape']:.1f}%"
    )
    print(
        f"  Ordinary-finish forebody     MAE={ordinary_forebody_metrics['mae']:.5f} "
        f"RMSE={ordinary_forebody_metrics['rmse']:.5f} MAPE={ordinary_forebody_metrics['mape']:.1f}%"
    )

    total_plot_path = PNG_DIR / "agard_b_total_cd_validation.png"
    diagnostics_plot_path = PNG_DIR / "agard_b_component_diagnostics.png"
    plot_total_validation(
        df_smooth,
        df_ordinary,
        m_exp,
        exp_total,
        smooth_total_metrics,
        ordinary_total_metrics,
        total_plot_path,
    )
    plot_component_diagnostics(
        df_smooth,
        df_ordinary,
        m_exp,
        exp_forebody,
        exp_base,
        diagnostics_plot_path,
    )
    print(f"Plots saved to {total_plot_path} and {diagnostics_plot_path}")

    first_row = df_or.iloc[0]
    or_exposed_wing_area = float(first_row.get("orExposedWingArea_m2", np.nan))
    aedc_wing_area = float(first_row.get("aedcWingArea_m2", np.nan))

    geometry_table = markdown_table(
        ["Metric", "Value"],
        [
            ["AEDC wing reference area", f"{aedc_wing_area:.8f} m^2" if np.isfinite(aedc_wing_area) else "n/a"],
            [
                "AEDC base area",
                f"{float(first_row.get('aedcBaseArea_m2', np.nan)):.8f} m^2"
                if np.isfinite(first_row.get("aedcBaseArea_m2", np.nan))
                else "n/a",
            ],
            ["OR exposed fin planform area", f"{or_exposed_wing_area:.8f} m^2" if np.isfinite(or_exposed_wing_area) else "n/a"],
            [
                "OR exposed / AEDC wing area ratio",
                f"{or_exposed_wing_area / aedc_wing_area:.3f}"
                if np.isfinite(or_exposed_wing_area) and np.isfinite(aedc_wing_area) and aedc_wing_area > 0
                else "n/a",
            ],
            ["Interpretation", "Approx. 0.57 is expected because ORP stores exposed fins while AEDC uses gross wing reference area"],
        ],
    )

    metrics_table = markdown_table(
        ["Quantity", "Natural MAE", "Ordinary MAE", "Natural MAPE", "Ordinary MAPE"],
        [
            [
                "Total drag",
                f"{smooth_total_metrics['mae']:.5f}",
                f"{ordinary_total_metrics['mae']:.5f}",
                f"{smooth_total_metrics['mape']:.1f}%",
                f"{ordinary_total_metrics['mape']:.1f}%",
            ],
            [
                "Forebody drag",
                f"{smooth_forebody_metrics['mae']:.5f}",
                f"{ordinary_forebody_metrics['mae']:.5f}",
                f"{smooth_forebody_metrics['mape']:.1f}%",
                f"{ordinary_forebody_metrics['mape']:.1f}%",
            ],
            [
                "Base drag",
                f"{smooth_base_metrics['mae']:.5f}",
                f"{ordinary_base_metrics['mae']:.5f}",
                f"{smooth_base_metrics['mape']:.1f}%",
                f"{ordinary_base_metrics['mape']:.1f}%",
            ],
        ],
    )

    bracket_table = markdown_table(
        ["Quantity", "Points in bracket", "Coverage"],
        [
            ["Total drag", f"{total_bracket['covered']} / {total_bracket['total']}", f"{100.0 * total_bracket['fraction']:.0f}%"],
            ["Forebody drag", f"{forebody_bracket['covered']} / {forebody_bracket['total']}", f"{100.0 * forebody_bracket['fraction']:.0f}%"],
            ["Base drag", f"{base_bracket['covered']} / {base_bracket['total']}", f"{100.0 * base_bracket['fraction']:.0f}%"],
        ],
    )

    transition_rows = []
    for mach in [0.20, 0.50, 1.00]:
        row = df_smooth.iloc[(df_smooth["Mach"] - mach).abs().argsort()[:1]].iloc[0]
        transition_rows.append(
            [
                f"{row['Mach']:.2f}",
                f"{row['Re']:.2e}",
                f"{row['Re_transition']:.2e}",
                f"{row['Re_over_Re_transition']:.2f}",
                f"{row['frictionCD_wing_ref']:.5f}",
            ]
        )
    transition_table = markdown_table(
        ["Mach", "Re", "Re_tr", "Re/Re_tr", "Natural friction CD"],
        transition_rows,
    )

    snapshot_m02 = nearest_component_snapshot(df_components, 0.20)
    snapshot_m10 = nearest_component_snapshot(df_components, 1.00)

    summary_lines = [
        "# AGARD-B Drag Coefficient Diagnostic",
        "",
        "This artifact is **not** a manuscript-ready validation anchor yet.",
        "It is now a more honest diagnostic: the exposed-vs-gross wing-reference issue is largely closed, and the remaining low-Mach component error is better explained by boundary-layer transition sensitivity than by an obvious AGARD geometry bug.",
        "",
        "## Main Finding",
        "",
        "The current `natural_transition` assumption under-predicts low-Mach forebody drag.",
        "When the same geometry is rerun with an `ordinary_finish_bracket`, the low-Mach total and forebody drag move materially closer to the tunnel data, while base drag changes only modestly.",
        "That pattern points to **transition / skin-friction state** as the main AGARD component-error driver, with base-drag excess remaining secondary.",
        "",
        "## Agreement Metrics",
        "",
        metrics_table,
        "",
        "## Surface-Condition Bracket Coverage",
        "",
        bracket_table,
        "",
        "## Geometry / Reference-Area Closure",
        "",
        geometry_table,
        "",
        "## Transition Indicator on the Natural-Transition Run",
        "",
        transition_table,
        "",
        "## Component Snapshot Near M = 0.20",
        "",
        markdown_table(
            ["Component", "Category", "CD", "Friction", "Pressure", "Base"],
            snapshot_m02 or [["n/a", "n/a", "n/a", "n/a", "n/a", "n/a"]],
        ),
        "",
        "## Component Snapshot Near M = 1.00",
        "",
        markdown_table(
            ["Component", "Category", "CD", "Friction", "Pressure", "Base"],
            snapshot_m10 or [["n/a", "n/a", "n/a", "n/a", "n/a", "n/a"]],
        ),
        "",
        "## Reviewer-Safe Interpretation",
        "",
        "- AGARD-B should stay in the paper as a diagnostic appendix figure, not as the primary external drag proof.",
        "- The next calibration move should be an independent transonic/base-drag case, not more AGARD-driven tuning.",
        "- If AGARD is revisited later, do it by improving transition-state closure first, not by forcing the fin/body geometry away from the published calibration shape.",
        "",
        "## Figures",
        "",
        "### Total Drag Diagnostic",
        "",
        "![AGARD-B total drag diagnostic](../png/agard_b_total_cd_validation.png)",
        "",
        "### Component / Transition Diagnostic",
        "",
        "![AGARD-B component diagnostics](../png/agard_b_component_diagnostics.png)",
        "",
    ]

    report_path = MD_DIR / "agard_b_validation_report.md"
    report_path.write_text("\n".join(summary_lines), encoding="utf-8")
    print(f"Validation report saved to {report_path}")


if __name__ == "__main__":
    main()

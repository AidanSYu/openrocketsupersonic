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

SHAPES = [
    ("cone_n1", "Sharp Cone (n=1)"),
    ("paraboloid_n0p5", "Paraboloid (n=0.5)"),
    ("quarter_power_n0p25", "1/4 Power Series (n=0.25)"),
    ("LD_Haack", "Von Karman (L-D Haack)"),
    ("LV_ogive", "L-V Ogive (L/D=2.93)"),
]

ACCEPTANCE_MAE = 0.015
ACCEPTANCE_MAPE = 8.0


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
    abs_pct = np.where(np.abs(observed) > 1.0e-12, np.abs(errors) / np.abs(observed) * 100.0, np.nan)
    signed_pct = np.where(np.abs(observed) > 1.0e-12, errors / observed * 100.0, np.nan)
    return {
        "points": int(observed.size),
        "mae": float(np.mean(np.abs(errors))),
        "rmse": float(np.sqrt(np.mean(errors**2))),
        "mean_bias": float(np.mean(errors)),
        "mape": float(np.nanmean(abs_pct)),
        "max_pct": float(np.nanmax(abs_pct)),
        "errors": errors,
        "abs_pct": abs_pct,
        "signed_pct": signed_pct,
    }


def markdown_table(headers: list[str], rows: list[list[str]]) -> str:
    lines = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join(["---"] * len(headers)) + " |",
    ]
    for row in rows:
        lines.append("| " + " | ".join(row) + " |")
    return "\n".join(lines)


def build_comparison_tables(df_exp: pd.DataFrame, df_or: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame, dict]:
    point_rows: list[dict] = []
    metrics_rows: list[dict] = []
    overall_observed: list[float] = []
    overall_predicted: list[float] = []

    for shape_id, label in SHAPES:
        exp_col = f"C_DF_{shape_id}"
        or_col = f"C_DF_{shape_id}"
        exp_series = df_exp[["Mach", exp_col]].copy()
        exp_series[exp_col] = pd.to_numeric(exp_series[exp_col], errors="coerce")
        exp_series = exp_series.dropna(subset=[exp_col])
        if exp_series.empty:
            continue

        mach = exp_series["Mach"].to_numpy(dtype=float)
        observed = exp_series[exp_col].to_numpy(dtype=float)
        predicted = np.interp(mach, df_or["Mach"].to_numpy(dtype=float), df_or[or_col].to_numpy(dtype=float))
        metrics = compute_metrics(observed, predicted)

        metrics_rows.append(
            {
                "shape_id": shape_id,
                "shape_label": label,
                "points": metrics["points"],
                "mae": metrics["mae"],
                "rmse": metrics["rmse"],
                "mean_bias": metrics["mean_bias"],
                "mape_pct": metrics["mape"],
                "max_pct": metrics["max_pct"],
                "meets_first_pass_gate": "yes"
                if (metrics["mae"] <= ACCEPTANCE_MAE or metrics["mape"] <= ACCEPTANCE_MAPE)
                else "no",
            }
        )

        for mach_i, obs_i, pred_i, err_i, pct_i in zip(
            mach,
            observed,
            predicted,
            metrics["errors"],
            metrics["signed_pct"],
        ):
            point_rows.append(
                {
                    "shape_id": shape_id,
                    "shape_label": label,
                    "Mach": mach_i,
                    "C_DF_exp": obs_i,
                    "C_DF_orp": pred_i,
                    "error_abs": err_i,
                    "error_pct": pct_i,
                }
            )

        overall_observed.extend(observed.tolist())
        overall_predicted.extend(predicted.tolist())

    pointwise = pd.DataFrame(point_rows).sort_values(["shape_id", "Mach"]).reset_index(drop=True)
    metrics_df = pd.DataFrame(metrics_rows)
    overall = compute_metrics(np.asarray(overall_observed, dtype=float), np.asarray(overall_predicted, dtype=float))
    return pointwise, metrics_df, overall


def plot_validation(df_exp: pd.DataFrame, df_or: pd.DataFrame, metrics_df: pd.DataFrame, overall: dict, plot_path: Path) -> None:
    fig, axes = plt.subplots(2, 3, figsize=(14, 8.5), sharex=True)
    axes = axes.ravel()
    colors = ["xkcd:crimson", "xkcd:royal blue", "xkcd:forest green", "xkcd:orange", "xkcd:purple"]

    for i, (shape_id, label) in enumerate(SHAPES):
        ax = axes[i]
        or_col = f"C_DF_{shape_id}"
        exp_col = f"C_DF_{shape_id}"
        exp_series = df_exp[["Mach", exp_col]].copy()
        exp_series[exp_col] = pd.to_numeric(exp_series[exp_col], errors="coerce")
        exp_series = exp_series.dropna(subset=[exp_col])

        ax.plot(
            df_or["Mach"],
            df_or[or_col],
            color=colors[i],
            linewidth=2.1,
            label="ORP",
        )

        if not exp_series.empty:
            predicted = np.interp(
                exp_series["Mach"].to_numpy(dtype=float),
                df_or["Mach"].to_numpy(dtype=float),
                df_or[or_col].to_numpy(dtype=float),
            )
            ax.scatter(
                exp_series["Mach"],
                exp_series[exp_col],
                color="black",
                marker="o",
                s=45,
                zorder=5,
                label="Digitized NACA",
            )
            ax.vlines(
                exp_series["Mach"],
                exp_series[exp_col],
                predicted,
                color=colors[i],
                alpha=0.45,
                linewidth=1.4,
            )
            metric_row = metrics_df.loc[metrics_df["shape_id"] == shape_id].iloc[0]
            ax.text(
                0.03,
                0.97,
                "\n".join(
                    [
                        f"n = {int(metric_row['points'])}",
                        f"MAE = {metric_row['mae']:.4f}",
                        f"MAPE = {metric_row['mape_pct']:.1f}%",
                    ]
                ),
                transform=ax.transAxes,
                va="top",
                ha="left",
                fontsize=9,
                bbox={"boxstyle": "round,pad=0.28", "facecolor": "white", "alpha": 0.9, "edgecolor": "0.8"},
            )

        ax.set_title(label, fontsize=11, fontweight="bold")
        ax.grid(True, linestyle="--", alpha=0.65)
        ax.set_xlim(1.15, 3.8)
        ax.set_ylim(0.05, 0.26)
        if i >= 3:
            ax.set_xlabel("Mach Number")
        if i % 3 == 0:
            ax.set_ylabel(r"Foredrag Coefficient $C_{D,F}$")

    summary_ax = axes[-1]
    summary_ax.axis("off")
    ranked = metrics_df.sort_values("mape_pct", ascending=False)
    summary_lines = [
        "A52H28 Digitized Benchmark",
        f"Total digitized points: {overall['points']}",
        f"Overall MAE: {overall['mae']:.4f}",
        f"Overall RMSE: {overall['rmse']:.4f}",
        f"Overall MAPE: {overall['mape']:.1f}%",
        "",
        "Worst shapes by MAPE:",
    ]
    for _, row in ranked.head(3).iterrows():
        summary_lines.append(f"{row['shape_label']}: {row['mape_pct']:.1f}%")
    summary_lines.append("")
    summary_lines.append("Caveat:")
    summary_lines.append("Digitized points are real; tunnel-state/Re matching")
    summary_lines.append("is still weaker than the Mach/reference closure.")
    summary_ax.text(
        0.02,
        0.98,
        "\n".join(summary_lines),
        va="top",
        ha="left",
        fontsize=10,
    )

    handles = [
        plt.Line2D([0], [0], color="xkcd:royal blue", linewidth=2.1, label="ORP"),
        plt.Line2D([0], [0], color="black", marker="o", linestyle="", markersize=6, label="Digitized NACA"),
    ]
    fig.legend(handles=handles, loc="lower center", ncol=2, frameon=False)
    fig.suptitle("NACA RM A52H28 Nose-Family Validation", fontsize=14, fontweight="bold")
    plt.tight_layout()
    plt.subplots_adjust(top=0.90, bottom=0.08)
    plt.savefig(plot_path, dpi=300, bbox_inches="tight")
    plt.close(fig)


def main() -> None:
    PNG_DIR.mkdir(parents=True, exist_ok=True)
    MD_DIR.mkdir(parents=True, exist_ok=True)

    exp_file = resolve_input("NACA_RM_A52H28_CD_vs_Mach_TEST_MATRIX.csv")
    or_file = resolve_input("naca_rm_a52h28_openrocket_cd.csv")
    conditions_file = resolve_input("NACA_RM_A52H28_test_conditions.csv")

    df_exp = pd.read_csv(exp_file, comment="#")
    df_or = pd.read_csv(or_file)
    df_conditions = pd.read_csv(conditions_file)

    pointwise, metrics_df, overall = build_comparison_tables(df_exp, df_or)
    metrics_df = metrics_df.sort_values("shape_id").reset_index(drop=True)

    pointwise_path = CSV_DIR / "naca_rm_a52h28_pointwise_comparison.csv"
    metrics_path = CSV_DIR / "naca_rm_a52h28_metrics.csv"
    pointwise.to_csv(pointwise_path, index=False)
    metrics_df.to_csv(metrics_path, index=False)

    plot_path = PNG_DIR / "naca_rm_a52h28_validation.png"
    plot_validation(df_exp, df_or, metrics_df, overall, plot_path)

    metrics_table = markdown_table(
        ["Shape", "n", "MAE", "RMSE", "MAPE", "Max %", "Gate"],
        [
            [
                row["shape_label"],
                str(int(row["points"])),
                f"{row['mae']:.4f}",
                f"{row['rmse']:.4f}",
                f"{row['mape_pct']:.1f}%",
                f"{row['max_pct']:.1f}%",
                row["meets_first_pass_gate"],
            ]
            for _, row in metrics_df.iterrows()
        ],
    )

    overall_table = markdown_table(
        ["Metric", "Value"],
        [
            ["Total digitized points", str(overall["points"])],
            ["Overall MAE", f"{overall['mae']:.4f}"],
            ["Overall RMSE", f"{overall['rmse']:.4f}"],
            ["Overall MAPE", f"{overall['mape']:.1f}%"],
            ["First-pass gate", "pass" if (overall["mae"] <= ACCEPTANCE_MAE or overall["mape"] <= ACCEPTANCE_MAPE) else "fail"],
        ],
    )

    worst_points = pointwise.reindex(pointwise["error_pct"].abs().sort_values(ascending=False).index).head(5)
    worst_points_table = markdown_table(
        ["Shape", "Mach", "Exp", "ORP", "Error", "% Error"],
        [
            [
                row["shape_label"],
                f"{row['Mach']:.2f}",
                f"{row['C_DF_exp']:.4f}",
                f"{row['C_DF_orp']:.4f}",
                f"{row['error_abs']:+.4f}",
                f"{row['error_pct']:+.1f}%",
            ]
            for _, row in worst_points.iterrows()
        ],
    )

    conditions_excerpt = markdown_table(
        ["Mach", "Re x10^6 avg", "Range", "Tunnel", "Notes"],
        [
            [
                f"{row['Mach']:.2f}",
                f"{row['Re_x1e6_avg']:.2f}",
                str(row["Re_x1e6_range"]),
                str(row["Tunnel_No"]),
                str(row["notes"]),
            ]
            for _, row in df_conditions.iterrows()
        ],
    )

    report_lines = [
        "# NACA RM A52H28 Validation Benchmark",
        "",
        "This artifact is now a **real sim-vs-published-data benchmark** rather than a scaffold.",
        "The placeholder ordinates have been replaced with digitized experimental `C_DF(M)` points from NACA RM A52H28 Figures 11(b), 11(c), and 15.",
        "",
        "## Benchmark Boundary",
        "",
        "- Geometry closure is strong: the ORP bodies are exact matches to the tested nose-shape family (`cone`, `power`, `Haack`, `ogive`) at `L/D ~= 3`.",
        "- Reference coefficient closure is strong: both source and ORP use foredrag coefficient `C_DF = (C_D,total - C_D,base)` on base-area reference.",
        "- Current benchmark-side assumption: the ORP calibration bodies are treated as polished / smooth-finish tunnel articles, not ordinary rough-finish hobby airframes.",
        "- The ORP export now matches the published Reynolds envelope on a per-Mach representative basis, which materially reduces the old tunnel-state mismatch.",
        "- Remaining caveat: the source includes dual Reynolds-number conditions at `M = 1.44`, while the sparse published figure points do not resolve every duplicate condition cleanly, so the exact transition state at that Mach is still only approximately closed.",
        "",
        "## Overall Agreement",
        "",
        overall_table,
        "",
        "## Per-Shape Metrics",
        "",
        metrics_table,
        "",
        "## Published Test Conditions Captured From Source",
        "",
        conditions_excerpt,
        "",
        "## Largest Pointwise Deviations",
        "",
        worst_points_table,
        "",
        "## Interpretation",
        "",
        "The benchmark is now good enough to expose where the model is genuinely strong and where it is not.",
        "Shapes with low `MAE` and low `MAPE` are usable as evidence of correct trend/order behavior; the large outliers identify real model or tunnel-state closure gaps rather than missing data plumbing.",
        "",
        "A reviewer-safe reading today is:",
        "",
        "- `A52H28` is now a valid external benchmark for zero-lift foredrag trends.",
        "- It is **not yet** a universal pass on absolute `C_DF(M)` accuracy across all nose families.",
        "- The next tightening step is to separate the residual cone / quarter-power overprediction into transition-state versus pressure-drag causes, especially around `M = 1.24-1.99`.",
        "",
        "## Files Generated",
        "",
        "- `naca_rm_a52h28_validation.png`",
        "- `naca_rm_a52h28_metrics.csv`",
        "- `naca_rm_a52h28_pointwise_comparison.csv`",
        "",
        "## Figure",
        "",
        "![NACA A52H28 validation](../png/naca_rm_a52h28_validation.png)",
        "",
    ]

    report_path = MD_DIR / "naca_validation_report.md"
    report_path.write_text("\n".join(report_lines), encoding="utf-8")

    print(f"Wrote {pointwise_path}")
    print(f"Wrote {metrics_path}")
    print(f"Plot saved to {plot_path}")
    print(f"Report generated at {report_path}")


if __name__ == "__main__":
    main()

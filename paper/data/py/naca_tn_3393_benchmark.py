from __future__ import annotations

from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

SCRIPT_DIR = Path(__file__).resolve().parent
DATA_DIR = SCRIPT_DIR.parent
CSV_DIR = DATA_DIR / "csv"
PNG_DIR = DATA_DIR / "png"
MD_DIR = DATA_DIR / "md"

EXP_DATA = CSV_DIR / "NACA_TN_3393_digitized_points.csv"
OR_DATA = CSV_DIR / "naca_tn_3393_openrocket_base.csv"
POINTWISE_CSV = CSV_DIR / "naca_tn_3393_pointwise_comparison.csv"
METRICS_CSV = CSV_DIR / "naca_tn_3393_metrics.csv"
VALIDATION_REPORT = MD_DIR / "naca_tn_3393_validation_report.md"
PLOT_PATH = PNG_DIR / "naca_tn_3393_base_pressure.png"

ACCEPTANCE_MAE = 0.010
ACCEPTANCE_MAPE = 15.0
BASE_COLUMN = "baseCD"
EXP_COLUMN = "Cpb"


def resolve_input(filename: Path) -> Path:
    candidates = [filename, DATA_DIR / filename.name]
    for candidate in candidates:
        if candidate.exists():
            return candidate
    raise FileNotFoundError(f"{filename.name} is missing. Run PublicationAnalyticalDataExportTest for TN 3393.")


def compute_metrics(observed: np.ndarray, predicted: np.ndarray) -> dict:
    errors = predicted - observed
    pct = np.where(np.abs(observed) > 0, np.abs(errors) / np.abs(observed) * 100.0, np.nan)
    return {
        "points": int(observed.size),
        "mae": float(np.mean(np.abs(errors))),
        "rmse": float(np.sqrt(np.mean(errors**2))),
        "mape": float(np.nanmean(pct)),
        "max_pct": float(np.nanmax(pct)),
        "mean_bias": float(np.mean(errors)),
    }


def load_or_data() -> pd.DataFrame | None:
    if not OR_DATA.exists():
        print(f"OpenRocket export missing ({OR_DATA.name}); cannot compute base-drag metrics yet.")
        return None

    df = pd.read_csv(OR_DATA)
    if BASE_COLUMN not in df.columns:
        raise KeyError(f"{OR_DATA.name} must expose a '{BASE_COLUMN}' column. Available columns: {list(df.columns)}")
    if "boundary_layer" not in df.columns:
        raise KeyError(f"{OR_DATA.name} must expose a 'boundary_layer' column for laminar/turbulent comparisons.")
    return df[["Mach", "boundary_layer", BASE_COLUMN]].dropna().sort_values(["boundary_layer", "Mach"]).reset_index(drop=True)


def build_pointwise_table(df_exp: pd.DataFrame, df_or: pd.DataFrame | None) -> pd.DataFrame:
    rows = []

    for _, row in df_exp.iterrows():
        mach = float(row["Mach"])
        observed = float(row[EXP_COLUMN])
        boundary_layer = row["boundary_layer"]
        predicted = np.nan
        error_abs = np.nan
        error_pct = np.nan
        if df_or is not None:
            layer_df = df_or[df_or["boundary_layer"] == boundary_layer]
            if not layer_df.empty:
                or_mach = layer_df["Mach"].to_numpy(dtype=float)
                or_base = layer_df[BASE_COLUMN].to_numpy(dtype=float)
                if mach >= or_mach.min() and mach <= or_mach.max():
                    predicted = float(np.interp(mach, or_mach, or_base))
                    error_abs = predicted - observed
                    error_pct = float(error_abs / observed * 100.0) if abs(observed) > 0 else np.nan
        rows.append(
            {
                "Mach": mach,
                "boundary_layer": boundary_layer,
                "observed_Cpb": observed,
                "predicted_baseCD": predicted,
                "error_abs": error_abs,
                "error_pct": error_pct,
                "notes": row.get("notes", ""),
            }
        )
    return pd.DataFrame(rows)


def aggregate_metrics(pointwise: pd.DataFrame) -> pd.DataFrame:
    metrics = []
    for layer, group in pointwise.groupby("boundary_layer"):
        mask = group["predicted_baseCD"].notna()
        if not mask.any():
            metrics.append(
                {
                    "boundary_layer": layer,
                    "points": int(group.shape[0]),
                    "mae": np.nan,
                    "rmse": np.nan,
                    "mape": np.nan,
                    "max_pct": np.nan,
                    "mean_bias": np.nan,
                    "meets_gate": "not enough OR data",
                }
            )
            continue

        stats = compute_metrics(
            group.loc[mask, "observed_Cpb"].to_numpy(dtype=float),
            group.loc[mask, "predicted_baseCD"].to_numpy(dtype=float),
        )
        gate = "yes" if stats["mae"] <= ACCEPTANCE_MAE and stats["mape"] <= ACCEPTANCE_MAPE else "no"
        metrics.append({**{"boundary_layer": layer, "meets_gate": gate}, **stats})
    return pd.DataFrame(metrics)


def plot_results(pointwise: pd.DataFrame, or_df: pd.DataFrame | None) -> None:
    PNG_DIR.mkdir(exist_ok=True)
    fig, ax = plt.subplots(figsize=(8, 5))
    ax.set_title("TN 3393 Base Drag Benchmark (Ogive-Cylinder, l/d=5)")
    ax.set_xlabel("Mach number")
    ax.set_ylabel(r"Base drag coefficient $C_{D,b}$")
    markers = {"laminar": "o", "turbulent (fixed roughness)": "s"}
    colors_exp = {"laminar": "xkcd:royal blue", "turbulent (fixed roughness)": "xkcd:crimson"}
    for layer in pointwise["boundary_layer"].unique():
        subset = pointwise[pointwise["boundary_layer"] == layer]
        ax.scatter(
            subset["Mach"], subset["observed_Cpb"],
            label=f"TN 3393 ({layer})",
            marker=markers.get(layer, "o"),
            color=colors_exp.get(layer, "black"),
            zorder=5, s=50,
        )
    if or_df is not None:
        for layer, subset in or_df.groupby("boundary_layer"):
            ax.plot(
                subset["Mach"], subset[BASE_COLUMN],
                linestyle="--", linewidth=2,
                label=f"ORP ({layer})",
            )
    ax.grid(True, linestyle="--", alpha=0.45)
    ax.legend(fontsize=8)
    fig.tight_layout()
    plt.savefig(PLOT_PATH, dpi=200)
    plt.close(fig)


def write_report(metrics: pd.DataFrame, pointwise: pd.DataFrame, or_available: bool) -> None:
    MD_DIR.mkdir(exist_ok=True)
    lines = [
        "# NACA TN 3393 Base Pressure Benchmark",
        "",
        "## Coefficient Basis",
        "",
        "Both the experimental and OpenRocket data are now on the same coefficient basis:",
        r"$C_{D,b} = |P_b| = \text{pb\_ratio} \times \frac{2}{\gamma M^2}$",
        "",
        "where `pb_ratio` is the fraction of the vacuum-limit base pressure coefficient",
        r"($P_{b,\text{vac}} = -2/(\gamma M^2)$) reported in NACA TN 3393.",
        "",
        "The OR export column `baseCD` is the absolute value of the Devan-Ashwood",
        "base drag model output, referenced to base area, which is the same quantity.",
        "",
    ]
    if not or_available:
        lines.append("OpenRocket export data (`naca_tn_3393_openrocket_base.csv`) is not yet available, so no metrics could be computed.")
        lines.append("")
        lines.append("Run `PublicationAnalyticalDataExportTest` with the TN 3393 geometry to generate the missing CSV.")
    else:
        lines.append("## Agreement Metrics")
        lines.append("")
        lines.append("Collected metrics per boundary-layer state:")
        lines.append("")
        lines.append(metrics.to_markdown(index=False, floatfmt=".4f"))
        lines.append("")
        lines.append("## Pointwise Comparison")
        lines.append("")
        pw_display = pointwise[["Mach", "boundary_layer", "observed_Cpb", "predicted_baseCD", "error_abs", "error_pct"]].copy()
        pw_display.columns = ["Mach", "BL State", "TN 3393 Cpb", "ORP baseCD", "Error", "Error %"]
        lines.append(pw_display.to_markdown(index=False, floatfmt=".4f"))
        lines.append("")
        lines.append(f"The scatter/line plot is available in `{PLOT_PATH.name}`.")

    lines.append("")
    lines.append("## Data Provenance")
    lines.append("")
    lines.append("- **Source**: NACA TN 3393, Reller & Hamaker (1955), Ames Aeronautical Laboratory.")
    lines.append("- **Model**: 10-caliber tangent ogive nose + cylindrical afterbody, l/d = 5 (Model 2).")
    lines.append("- **Figures**: Digitized from Figures 9(b), 10(b), and 16 (condensation-corrected).")
    lines.append("- **Coefficient basis**: `Cpb = pb_ratio × 2/(γM²)` converted from reported vacuum-limit fractions.")
    lines.append("- **Confidence**: 80% — values are derived from cross-plot interpolation of condensation-corrected curves.")
    lines.append("")
    lines.append("## Interpretation")
    lines.append("")
    lines.append("The Devan-Ashwood base drag model in OpenRocket predicts base drag as a function of")
    lines.append("Mach number alone. NACA TN 3393 demonstrates that base pressure depends strongly on")
    lines.append("boundary-layer state (laminar vs turbulent) and Reynolds number. The OR model effectively")
    lines.append("represents a turbulent-boundary-layer correlation, so we expect closer agreement with")
    lines.append("the turbulent data and systematic under-prediction compared to the laminar data")
    lines.append("(which has higher base drag due to the thinner boundary layer at high supersonic speeds).")
    lines.append("")
    VALIDATION_REPORT.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    exp_df = pd.read_csv(EXP_DATA)
    if EXP_COLUMN not in exp_df.columns:
        print(f"Column '{EXP_COLUMN}' not found in {EXP_DATA.name}. Re-run naca_tn_3393_digitize.py first.")
        return

    or_df = load_or_data()
    pointwise = build_pointwise_table(exp_df, or_df)
    pointwise.to_csv(POINTWISE_CSV, index=False)
    metrics = aggregate_metrics(pointwise)
    metrics.to_csv(METRICS_CSV, index=False)
    if or_df is not None:
        plot_results(pointwise, or_df)
    write_report(metrics, pointwise, or_df is not None)
    print(f"Wrote {POINTWISE_CSV}, {METRICS_CSV}, and {VALIDATION_REPORT}.")
    if or_df is None:
        print("Missing OR data; rerun once PublicationAnalyticalDataExportTest generates naca_tn_3393_openrocket_base.csv.")


if __name__ == "__main__":
    main()

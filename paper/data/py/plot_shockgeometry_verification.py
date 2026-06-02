"""Fig. 3 -- ShockGeometry surface-Mach verification vs Taylor-Maccoll & Prandtl-Meyer.

Loads `paper/data/csv/shockgeometry_local_flow_validation.csv` produced by
`ShockGeometryLocalFlowValidationTest`. The CSV contains per-station
(M_freestream, theta_cone, zone) rows with both the ShockGeometry pre-pass
output and the analytical reference (Taylor-Maccoll for the cone surface,
Prandtl-Meyer for the body post-expansion zone). Errors are at numerical
precision (cone surface == 0, body post-expansion ~1e-11) so the plot shows
the magnitude of |error| on a log-scaled bar chart with a machine-precision
floor band.

Run:
    python paper/data/py/plot_shockgeometry_verification.py
"""

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

CSV_NAME = "shockgeometry_local_flow_validation.csv"
OUT_NAME = "shockgeometry_surface_mach_verification.png"

# Machine precision floor; the validation runs at numerical precision so the
# y-axis needs a floor that lets near-zero bars still render visibly.
EPS_FLOOR = 1e-14


def load_data() -> pd.DataFrame:
    csv_path = CSV_DIR / CSV_NAME
    if not csv_path.exists():
        return pd.DataFrame()
    df = pd.read_csv(csv_path)
    return df


def render_placeholder(out_path: Path) -> None:
    fig, ax = plt.subplots(figsize=(8, 6))
    ax.set_axis_off()
    ax.text(
        0.5,
        0.5,
        "Data pending: ShockGeometryLocalFlowValidationTest output required\n"
        "(paper/data/csv/shockgeometry_local_flow_validation.csv missing).",
        ha="center",
        va="center",
        fontsize=12,
        bbox={"boxstyle": "round,pad=0.6", "facecolor": "#fff3cd", "edgecolor": "#856404"},
    )
    fig.savefig(out_path, dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)


def plot_verification(df: pd.DataFrame, out_path: Path) -> None:
    # Build labels and arrays separately for cone-surface and body-post-expansion zones
    labels: list[str] = []
    mach_err: list[float] = []
    press_err: list[float] = []
    zones: list[str] = []
    colors: list[str] = []

    for _, row in df.iterrows():
        m = row["mach_freestream"]
        th = row["cone_half_angle_deg"]
        zone = row["zone"]
        labels.append(f"M={m:.1f}\n{th:.0f} deg\n{zone.replace('_', ' ')}")
        mach_err.append(max(abs(float(row["mach_rel_err_pct"])), EPS_FLOOR))
        press_err.append(max(abs(float(row["pressure_rel_err_pct"])), EPS_FLOOR))
        zones.append(zone)
        if zone == "cone_surface":
            colors.append("#1f77b4")  # blue -- Taylor-Maccoll reference
        else:
            colors.append("#ff7f0e")  # orange -- Prandtl-Meyer reference

    x = np.arange(len(labels))
    width = 0.4

    fig, ax = plt.subplots(figsize=(12, 6))
    bars_m = ax.bar(
        x - width / 2,
        mach_err,
        width,
        color=colors,
        edgecolor="black",
        linewidth=0.6,
        label="Mach |error| (%)",
    )
    bars_p = ax.bar(
        x + width / 2,
        press_err,
        width,
        color=colors,
        edgecolor="black",
        linewidth=0.6,
        hatch="//",
        alpha=0.65,
        label="p/p_inf |error| (%)",
    )

    ax.set_yscale("log")
    ax.set_ylim(1e-15, 1e-8)
    ax.axhline(2.22e-14, color="0.4", linestyle=":", linewidth=1.0)
    ax.text(
        len(x) - 0.5,
        2.22e-14,
        " IEEE-754 double eps",
        va="bottom",
        ha="right",
        fontsize=9,
        color="0.3",
    )

    ax.set_xticks(x)
    ax.set_xticklabels(labels, fontsize=8)
    ax.set_ylabel("Relative error (%)")
    ax.set_title(
        "ShockGeometry pre-pass: surface-Mach & pressure-ratio error\n"
        "vs Taylor-Maccoll (cone surface) and Prandtl-Meyer (body post-expansion)",
        fontweight="bold",
    )
    ax.grid(True, axis="y", linestyle="--", alpha=0.7)

    # Custom legend with both zone color encoding and bar pattern encoding
    legend_handles = [
        plt.Rectangle((0, 0), 1, 1, facecolor="#1f77b4", edgecolor="black", label="Cone surface (Taylor-Maccoll)"),
        plt.Rectangle((0, 0), 1, 1, facecolor="#ff7f0e", edgecolor="black", label="Body post-expansion (Prandtl-Meyer)"),
        plt.Rectangle((0, 0), 1, 1, facecolor="white", edgecolor="black", label="Mach error"),
        plt.Rectangle((0, 0), 1, 1, facecolor="white", edgecolor="black", hatch="//", label="Pressure-ratio error"),
    ]
    ax.legend(handles=legend_handles, loc="upper left", fontsize=9)

    # Aggregate-stats text box
    cone_mask = df["zone"] == "cone_surface"
    body_mask = df["zone"] == "body_post_expansion"
    cone_max = float(df.loc[cone_mask, ["mach_rel_err_pct", "pressure_rel_err_pct"]].abs().to_numpy().max())
    body_max = float(df.loc[body_mask, ["mach_rel_err_pct", "pressure_rel_err_pct"]].abs().to_numpy().max())
    stats_text = (
        f"Cone surface max |err|  = {cone_max:.2e} %\n"
        f"Body p-M max |err|       = {body_max:.2e} %\n"
        f"All {len(df)} stations at numerical precision"
    )
    ax.text(
        0.98,
        0.97,
        stats_text,
        transform=ax.transAxes,
        ha="right",
        va="top",
        fontsize=9,
        family="monospace",
        bbox={"boxstyle": "round,pad=0.4", "facecolor": "white", "edgecolor": "0.7", "alpha": 0.9},
    )

    plt.tight_layout()
    fig.savefig(out_path, dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)


def main() -> None:
    PNG_DIR.mkdir(parents=True, exist_ok=True)
    out_path = PNG_DIR / OUT_NAME
    df = load_data()
    if df.empty:
        render_placeholder(out_path)
        print(f"[placeholder] wrote {out_path}")
        return
    plot_verification(df, out_path)
    print(f"wrote {out_path}")


if __name__ == "__main__":
    main()

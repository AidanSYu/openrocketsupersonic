"""Fig. 23 -- Phase 6h Apache coast-Cd disclosure (real-data version).

Plots ORP's Apache coast Cd vs the NASA Nike-Apache Performance Handbook
(X-721-66-569, Appendix A p.66, Case 1 COASTING) using the exact 17-point
sweep emitted by NikeApacheCoastCdDiagnosticTest. The M >= 5 disclosure
band is shaded; the +0.0595 mean deficit is annotated.

Data source: paper/data/csv/phase6h_apache_coast_cd.csv (regenerated
2026-05-16 from NikeApacheCoastCdDiagnosticTest output).

Run:
    python paper/data/py/plot_phase6h_apache_disclosure.py
"""

from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np


SCRIPT_DIR = Path(__file__).resolve().parent
DATA_DIR = SCRIPT_DIR.parent if SCRIPT_DIR.name == "py" else SCRIPT_DIR
CSV_PATH = DATA_DIR / "csv" / "phase6h_apache_coast_cd.csv"
PNG_DIR = DATA_DIR / "png"
OUT_NAME = "phase6h_apache_cd_disclosure.png"


def main() -> None:
    PNG_DIR.mkdir(parents=True, exist_ok=True)

    data = np.genfromtxt(CSV_PATH, delimiter=",", names=True)
    mach = data["mach"]
    cd_hb = data["Cd_handbook_Case1"]
    cd_orp = data["Cd_ORP"]
    cd_fric = data["Cd_fric_ORP"]
    cd_press = data["Cd_press_ORP"]
    cd_base = data["Cd_base_ORP"]

    # Mean deficit at M >= 5 (7 points: 5.0 .. 8.0)
    mask_hyper = mach >= 5.0
    mean_deficit = float(np.mean(cd_hb[mask_hyper] - cd_orp[mask_hyper]))

    fig, (ax_top, ax_bot) = plt.subplots(
        2, 1, figsize=(8, 9), sharex=True, gridspec_kw={"height_ratios": [1.4, 1.0]}
    )

    # ---- Top panel: total Cd, handbook vs ORP ----
    ax_top.plot(
        mach,
        cd_hb,
        color="#1f77b4",
        linewidth=2.4,
        marker="o",
        markersize=5,
        markerfacecolor="white",
        markeredgewidth=1.2,
        label="Handbook X-721-66-569 (Case 1, COASTING)",
    )
    ax_top.plot(
        mach,
        cd_orp,
        color="#d62728",
        linewidth=2.2,
        marker="s",
        markersize=5,
        markerfacecolor="white",
        markeredgewidth=1.2,
        label="ORP coast $C_d$ (total)",
    )

    ax_top.axvspan(5.0, 8.0, color="0.85", alpha=0.55, zorder=0)
    ax_top.text(
        6.5,
        1.05,
        "M >= 5 disclosure band",
        ha="center",
        va="top",
        fontsize=10,
        color="0.3",
        style="italic",
    )

    ax_top.annotate(
        f"Mean $C_d$ deficit at $M \\geq 5$:\n+{mean_deficit:.4f} (handbook - ORP)",
        xy=(6.5, (cd_hb[13] + cd_orp[13]) / 2.0),
        xytext=(2.2, 1.05),
        fontsize=10,
        ha="left",
        bbox={"boxstyle": "round,pad=0.4", "facecolor": "white", "edgecolor": "#d62728", "linewidth": 1.0, "alpha": 0.95},
        arrowprops={"arrowstyle": "->", "color": "#d62728", "linewidth": 1.2},
    )

    ax_top.set_ylim(0.15, 1.15)
    ax_top.set_ylabel(r"Coast drag coefficient $C_d$")
    ax_top.set_title(
        "Phase 6h disclosure: Apache coast-$C_d$ bias vs NASA handbook\n"
        "Nike-Apache 1965 corpus admission blocker",
        fontweight="bold",
    )
    ax_top.grid(True, linestyle="--", alpha=0.65)
    ax_top.legend(loc="upper right", fontsize=9.5)

    # ---- Bottom panel: ORP per-component decomposition ----
    ax_bot.plot(mach, cd_fric, color="#2ca02c", linewidth=1.8, marker="^", markersize=4, label="$C_f$ friction (Van Driest II)")
    ax_bot.plot(mach, cd_press, color="#ff7f0e", linewidth=1.8, marker="d", markersize=4, label="$C_p$ pressure (slender-body decays to 0 at M=5)")
    ax_bot.plot(mach, cd_base, color="#9467bd", linewidth=1.8, marker="v", markersize=4, label="$C_b$ base")
    ax_bot.axvspan(5.0, 8.0, color="0.85", alpha=0.55, zorder=0)
    ax_bot.axhline(0.234, color="#ff7f0e", linestyle=":", linewidth=1.0, alpha=0.6)
    ax_bot.text(7.7, 0.245, "$C_p$ plateau ≈ 0.234", fontsize=8.5, ha="right", color="#ff7f0e")

    ax_bot.set_ylim(0.0, 0.7)
    ax_bot.set_xlim(1.0, 8.0)
    ax_bot.set_xlabel("Mach number $M$")
    ax_bot.set_ylabel(r"Component $C_d$ contributions (ORP)")
    ax_bot.grid(True, linestyle="--", alpha=0.65)
    ax_bot.legend(loc="upper right", fontsize=9)

    note = (
        f"Source: NikeApacheCoastCdDiagnosticTest (2026-05-16); 17 Mach points.\n"
        f"Root cause: SLENDER_BODY_MACH_DECAY_END = 5.0 in BarrowmanDragCalculator\n"
        f"(Hoerner Ch.17 cylindrical-afterbody pressure drag missing above M=5)."
    )
    ax_bot.text(
        0.02,
        0.02,
        note,
        transform=ax_bot.transAxes,
        ha="left",
        va="bottom",
        fontsize=8.5,
        family="monospace",
        bbox={"boxstyle": "round,pad=0.4", "facecolor": "#fff8e1", "edgecolor": "0.6", "alpha": 0.95},
    )

    plt.tight_layout()
    out_path = PNG_DIR / OUT_NAME
    fig.savefig(out_path, dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print(f"wrote {out_path}")
    print(f"mean deficit M>=5 (from CSV): +{mean_deficit:.4f}")


if __name__ == "__main__":
    main()

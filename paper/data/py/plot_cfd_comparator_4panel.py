"""Build the JSR-paper CFD comparator figure.

Panels:
  (a) Basic Finner C_X vs Mach: ORP vs Bunescu URANS 2025
      CSV: paper/data/csv/bunescu_anf_comparator_2026_05_02.csv
  (b) Ogive-cyl-boattail Cd_base vs Mach: ORP vs Sahu TLNS 1983
      CSV: NOT DIGITIZED -- panel omitted, figure falls back to 3-panel layout.
  (c) AGARD-B CD vs AoA at M=1.6: Vidanovic SST 2014 vs VTI experiment
      CSV: paper/data/csv/vidanovic_agard_b_cfd_2014.csv
  (d) Basic Finner (Cmq + Cm_alphadot) vs Mach: ORP vs Sznajder MRF+FOM 2025
      CSV: paper/data/csv/sznajder_anf_cmq_comparator_2026_05_11.csv

Output: paper/data/png/cfd_comparator_4panel.png  (300 DPI)
"""

import sys
from pathlib import Path

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

# Make plot_style.py importable.
PAPER_ROOT = Path(__file__).resolve().parents[2]   # .../paper
sys.path.insert(0, str(PAPER_ROOT))
from plot_style import (
    apply_style, TAB10, REF_MARKERS, REF_KW, MODEL_KW,
    TITLE_SIZE, LABEL_SIZE, LEGEND_SIZE, DPI,
    C_BLUE, C_ORANGE, C_GREEN, C_RED,
)

CSV_DIR = PAPER_ROOT / "data" / "csv"
PNG_OUT = PAPER_ROOT / "data" / "png" / "cfd_comparator_4panel.png"

BUNESCU_CSV  = CSV_DIR / "bunescu_anf_comparator_2026_05_02.csv"
VIDANOVIC_CSV = CSV_DIR / "vidanovic_agard_b_cfd_2014.csv"
SZNAJDER_CSV = CSV_DIR / "sznajder_anf_cmq_comparator_2026_05_11.csv"
SAHU_BASE_CSV = None   # not yet digitized


def panel_a_bunescu(ax):
    """C_X vs Mach -- ORP solid line, Bunescu open circles."""
    df = pd.read_csv(BUNESCU_CSV, comment="#")
    df = df[df["coefficient"] == "C_X"].sort_values("mach")

    ax.plot(df["mach"], df["orp_predicted"],
            color=C_BLUE, label="OpenRocket Plus", **MODEL_KW)
    ax.scatter(df["mach"], df["bunescu_cfd"],
               facecolors="none", edgecolors=C_ORANGE,
               marker="o", s=55, linewidths=1.4,
               label="Bunescu URANS 2025", zorder=5)

    ax.set_xlabel("Mach")
    ax.set_ylabel(r"Axial-force coefficient $C_X$")
    ax.set_title("(a) Basic Finner $C_X$ vs Mach")
    ax.legend(loc="best", framealpha=0.85, edgecolor="0.8",
              fontsize=LEGEND_SIZE)


def panel_c_vidanovic(ax):
    """CD vs AoA at M=1.602 -- VTI experiment markers, SST k-omega CFD line.

    Uses the parabolic-arc (standard AGARD-B) CFD curve as the reference SST
    result; circle-arc CFD is shown as a faint dashed line for context.
    """
    df = pd.read_csv(VIDANOVIC_CSV, comment="#")
    df_m16 = df[np.isclose(df["mach"], 1.602)].copy()

    exp = df_m16[df_m16["source"] == "vti_exp"].sort_values("aoa_deg")
    cfd = df_m16[df_m16["source"] == "parabola_cfd"].sort_values("aoa_deg")

    ax.plot(cfd["aoa_deg"], cfd["cd"],
            color=C_BLUE, label=r"Vidanovic SST $k$-$\omega$ CFD 2014",
            **MODEL_KW)
    ax.scatter(exp["aoa_deg"], exp["cd"],
               facecolors="none", edgecolors=C_ORANGE,
               marker="s", s=55, linewidths=1.4,
               label="VTI T-38 experiment", zorder=5)

    ax.set_xlabel(r"Angle of attack $\alpha$ (deg)")
    ax.set_ylabel(r"Total drag coefficient $C_D$")
    ax.set_title(r"(c) AGARD-B $C_D$ vs $\alpha$ at $M=1.60$")
    ax.legend(loc="best", framealpha=0.85, edgecolor="0.8",
              fontsize=LEGEND_SIZE)


def panel_d_sznajder(ax):
    """(Cmq + Cm_alphadot) vs Mach -- ORP solid line, Sznajder open squares."""
    df = pd.read_csv(SZNAJDER_CSV, comment="#").sort_values("mach")

    ax.plot(df["mach"], df["orp_combined_damping"],
            color=C_BLUE, label="OpenRocket Plus", **MODEL_KW)
    ax.scatter(df["mach"], df["sznajder_total_damping"],
               facecolors="none", edgecolors=C_ORANGE,
               marker="s", s=55, linewidths=1.4,
               label="Sznajder MRF+FOM 2025", zorder=5)

    ax.set_xlabel("Mach")
    ax.set_ylabel(r"$C_{m_q} + C_{m_{\dot{\alpha}}}$")
    ax.set_title(r"(d) Basic Finner pitch damping vs Mach")
    ax.legend(loc="best", framealpha=0.85, edgecolor="0.8",
              fontsize=LEGEND_SIZE)


def panel_b_missing(ax):
    """Annotate that the Sahu TLNS 1983 boattail Cd_base comparator is
    not yet digitized."""
    ax.set_axis_off()
    ax.text(0.5, 0.55,
            "(b) Ogive-cyl-boattail $C_{d,\\mathrm{base}}$ vs Mach\n"
            "ORP vs Sahu TLNS 1983",
            ha="center", va="center", fontsize=TITLE_SIZE,
            fontweight="bold", transform=ax.transAxes)
    ax.text(0.5, 0.35,
            "Comparator CSV not yet digitized.\n"
            "Panel intentionally omitted.",
            ha="center", va="center", fontsize=LABEL_SIZE,
            style="italic", color="0.35",
            transform=ax.transAxes,
            bbox=dict(boxstyle="round,pad=0.6",
                      facecolor="#f7f7f7", edgecolor="0.7"))


def main():
    apply_style()

    fig, axes = plt.subplots(2, 2, figsize=(10, 8))
    ax_a, ax_b = axes[0, 0], axes[0, 1]
    ax_c, ax_d = axes[1, 0], axes[1, 1]

    panel_a_bunescu(ax_a)
    panel_b_missing(ax_b)
    panel_c_vidanovic(ax_c)
    panel_d_sznajder(ax_d)

    fig.suptitle("Fig. X. OpenRocket Plus vs four published-CFD sources.",
                 fontsize=TITLE_SIZE + 1, fontweight="bold", y=0.995)

    fig.tight_layout(rect=(0, 0, 1, 0.97))

    PNG_OUT.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(PNG_OUT, dpi=DPI, bbox_inches="tight", facecolor="white")
    plt.close(fig)

    size = PNG_OUT.stat().st_size
    print(f"Wrote: {PNG_OUT}")
    print(f"Size:  {size:,} bytes  ({size/1024:.1f} KiB)")


if __name__ == "__main__":
    main()

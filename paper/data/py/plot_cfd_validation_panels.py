"""Build the 4-panel CFD comparator figure for the JSR paper.

Per `paper/JSR_READINESS_BRIEF_2026_05_11.md` Section 8.2, this figure
overlays OpenRocket Plus predictions against four independent published-CFD
sources spanning two geometries (Basic Finner, AGARD-B) and two aerodynamic
quantities (static drag, dynamic pitch damping):

  (a) Basic Finner C_X vs Mach: ORP vs Bunescu URANS 2025
      CSV: paper/data/csv/bunescu_anf_comparator_2026_05_02.csv
      Memo: paper/data/md/bunescu_anf_cfd_comparator_2026_05_02.md
      NOTE: Bunescu Figure 10f is AoA = 0 deg (the only C_X sweep across
      Mach in the paper). The JSR brief's "AoA = 10 deg" tag refers to the
      single C_N cross-check point at M = 1.6 (also in the comparator CSV
      but not used here, since this panel plots C_X vs Mach).

  (b) Ogive-cyl-boattail Cd_base vs Mach: ORP vs Sahu TLNS 1983
      CSV: NOT YET DIGITIZED. The Sahu/Nietubicz/Steger 1983 ARBRL-TR-02495
      base-drag figure has been identified as a follow-up work item (see
      `paper/data/md/bunescu_anf_cfd_comparator_2026_05_02.md` Section
      "Secondary CFD anchor"), but the digitization and ORP-side ogive-
      cyl-boattail .ork have not been built. Panel is rendered as an
      explicit "data not yet digitized" placeholder.

  (c) AGARD-B CD vs AoA at M = 1.602: Vidanovic SST k-omega CFD 2014 vs
      VTI T-38 experiment. REFERENCE ONLY -- ORP does not yet ship an
      AGARD-B .ork, so this panel shows the literature dataset that future
      ORP work can target.
      CSV: paper/data/csv/vidanovic_agard_b_cfd_2014.csv
      Memo: paper/data/md/vidanovic_agard_b_cfd_comparator_2026_05_11.md

  (d) Basic Finner (Cmq + Cm_alphadot) vs Mach: ORP vs Sznajder MRF+FOM 2025
      CSV: paper/data/csv/sznajder_anf_cmq_comparator_2026_05_11.csv
      Memo: paper/data/md/sznajder_anf_cmq_cfd_comparator_2026_05_11.md

Output: paper/data/png/cfd_validation_panels.png (300 DPI)
"""

import sys
from pathlib import Path

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

# Make plot_style.py importable.
PAPER_ROOT = Path(__file__).resolve().parents[2]   # .../paper
sys.path.insert(0, str(PAPER_ROOT / "_build"))     # plot_style.py now lives in paper/_build/
from plot_style import (
    apply_style,
    TAB10, REF_KW, MODEL_KW,
    TITLE_SIZE, LABEL_SIZE, LEGEND_SIZE, ANNOT_SIZE, DPI,
    C_BLUE, C_ORANGE, C_GREEN, C_RED,
)

CSV_DIR = PAPER_ROOT / "data" / "csv"
PNG_OUT = PAPER_ROOT / "data" / "png" / "cfd_validation_panels.png"

BUNESCU_CSV   = CSV_DIR / "bunescu_anf_comparator_2026_05_02.csv"
VIDANOVIC_CSV = CSV_DIR / "vidanovic_agard_b_cfd_2014.csv"
SZNAJDER_CSV  = CSV_DIR / "sznajder_anf_cmq_comparator_2026_05_11.csv"


# ---------------------------------------------------------------------------
# Annotation helper -- consistent box style across panels
# ---------------------------------------------------------------------------
def _metric_box(ax, text, loc=("upper right",)):
    anchors = {
        "upper right": (0.97, 0.97, "top", "right"),
        "upper left":  (0.03, 0.97, "top", "left"),
        "lower right": (0.97, 0.03, "bottom", "right"),
        "lower left":  (0.03, 0.03, "bottom", "left"),
    }
    where = loc if isinstance(loc, str) else loc[0]
    x, y, va, ha = anchors[where]
    ax.text(x, y, text, transform=ax.transAxes, va=va, ha=ha,
            fontsize=ANNOT_SIZE,
            bbox=dict(boxstyle="round,pad=0.35", facecolor="white",
                      alpha=0.92, edgecolor="0.7"))


# ---------------------------------------------------------------------------
# Panel (a) -- Bunescu URANS C_X
# ---------------------------------------------------------------------------
def panel_a_bunescu(ax):
    """C_X vs Mach at AoA = 0 deg.

    Solid line  : ORP prediction (column `orp_predicted`)
    Open circles: Bunescu URANS 2025 digitization (column `bunescu_cfd`)

    MAPE 39.1 % over the five C_X points (M = 0.40-3.50).
    Source: `paper/data/md/bunescu_anf_cfd_comparator_2026_05_02.md`,
    "MAPE (C_X-only, 5 points): 39.1 %".
    """
    df = pd.read_csv(BUNESCU_CSV, comment="#")
    df = df[df["coefficient"] == "C_X"].sort_values("mach").reset_index(drop=True)

    # ORP line
    ax.plot(df["mach"], df["orp_predicted"],
            color=C_BLUE, label="OpenRocket Plus",
            marker="o", markersize=5, **MODEL_KW)
    # Bunescu URANS markers (open circles, distinct shape and color)
    ax.scatter(df["mach"], df["bunescu_cfd"],
               facecolors="none", edgecolors=C_ORANGE,
               marker="s", s=70, linewidths=1.6,
               label="Bunescu URANS 2025 (Aerospace 12, 371)",
               zorder=5)

    ax.set_xlabel("Mach number")
    ax.set_ylabel(r"Axial-force coefficient $C_X$")
    ax.set_title(r"(a) Basic Finner $C_X$ vs Mach -- Bunescu URANS")
    ax.legend(loc="upper right", framealpha=0.88, edgecolor="0.8",
              fontsize=LEGEND_SIZE - 0.5)

    _metric_box(ax,
                "MAPE = 39.1 % (5 pts, $C_X$ only)\n"
                "ORP underpredicts CFD 24-59 %",
                loc="lower left")


# ---------------------------------------------------------------------------
# Panel (b) -- Sahu TLNS Cd_base, NOT YET DIGITIZED
# ---------------------------------------------------------------------------
def panel_b_sahu_placeholder(ax):
    """Placeholder panel for the Sahu/Nietubicz/Steger 1983 ARBRL-TR-02495
    ogive-cyl-boattail base-drag comparator.

    Rationale: per `paper/data/md/bunescu_anf_cfd_comparator_2026_05_02.md`
    Section "Secondary CFD anchor (Sahu, Nietubicz, Steger 1983) -- not
    exercised", this comparator has been identified but not built. The
    Sahu Figure 14 base-drag CFD circles (M = 0.9-1.2) have not been
    digitized, and ORP does not yet ship a secant-ogive-cylinder-boattail
    .ork to drive the comparison.
    """
    ax.set_axis_off()
    ax.text(0.5, 0.78,
            r"(b) Ogive-cyl-boattail $C_{d,\mathrm{base}}$ vs Mach"
            "\nORP vs Sahu TLNS 1983",
            ha="center", va="center", fontsize=TITLE_SIZE,
            fontweight="bold", transform=ax.transAxes)
    ax.text(0.5, 0.42,
            "DATA NOT YET DIGITIZED\n\n"
            "Sahu, Nietubicz & Steger 1983\n"
            "ARBRL-TR-02495 (AD-A130-293)\n"
            "Fig. 14: $C_{d,\\mathrm{base}}$ at $M$ = 0.9-1.2\n\n"
            "ORP-side .ork (secant-ogive-cylinder-boattail)\n"
            "and Java comparator test are open work items.",
            ha="center", va="center", fontsize=LABEL_SIZE - 0.5,
            style="italic", color="0.30",
            transform=ax.transAxes,
            bbox=dict(boxstyle="round,pad=0.7",
                      facecolor="#f7f7f7", edgecolor="0.65",
                      linewidth=1.0))


# ---------------------------------------------------------------------------
# Panel (c) -- Vidanovic AGARD-B CD vs AoA at M = 1.602 (reference only)
# ---------------------------------------------------------------------------
def panel_c_vidanovic(ax):
    """CD vs AoA at M = 1.602 -- VTI experiment markers + Vidanovic SST CFD
    line. Reference-only panel: no ORP curve (ORP does not ship an AGARD-B
    geometry yet).

    Uses the parabolic-arc (standard AGARD-B) CFD curve. The non-standard
    circular-arc CFD curve diverges in Cm > 4 deg AoA but is coincident
    with the parabola CFD curve in CD over the full sweep, so a single
    CFD line suffices for this CD panel.

    Paper-internal CFD-vs-experiment agreement (Vidanovic 2014, page 1231):
    "percent error between 0.3 % and 3 % at positive AoA between the
    experimental and the simulated results for the parabolic-arc nose"
    at M = 1.602. Source memo:
    `paper/data/md/vidanovic_agard_b_cfd_comparator_2026_05_11.md`.
    """
    df = pd.read_csv(VIDANOVIC_CSV, comment="#")
    df_m16 = df[np.isclose(df["mach"], 1.602)].copy()

    exp = df_m16[df_m16["source"] == "vti_exp"].sort_values("aoa_deg")
    cfd = df_m16[df_m16["source"] == "parabola_cfd"].sort_values("aoa_deg")

    # CFD as a solid line (parabolic-arc, the standard AGARD-B configuration)
    ax.plot(cfd["aoa_deg"], cfd["cd"],
            color=C_GREEN, label=r"Vidanovic SST $k$-$\omega$ CFD 2014",
            marker="^", markersize=6, **MODEL_KW)
    # Experiment as open squares
    ax.scatter(exp["aoa_deg"], exp["cd"],
               facecolors="none", edgecolors=C_RED,
               marker="D", s=65, linewidths=1.6,
               label="VTI T-38 experiment", zorder=5)

    ax.set_xlabel(r"Angle of attack $\alpha$ (deg)")
    ax.set_ylabel(r"Total drag coefficient $C_D$")
    ax.set_title(r"(c) AGARD-B $C_D$ vs $\alpha$ at $M=1.60$ -- "
                 r"Vidanovic SST $k$-$\omega$")
    ax.legend(loc="upper left", framealpha=0.88, edgecolor="0.8",
              fontsize=LEGEND_SIZE - 0.5)

    _metric_box(ax,
                "Reference dataset (no ORP curve)\n"
                "CFD vs VTI: 0.3-3 % in $C_D$ (paper)\n"
                "Future ORP AGARD-B target",
                loc="lower right")


# ---------------------------------------------------------------------------
# Panel (d) -- Sznajder Cmq + Cm_alphadot
# ---------------------------------------------------------------------------
def panel_d_sznajder(ax):
    """(Cmq + Cm_alphadot) vs Mach.

    Solid line   : ORP prediction (column `orp_combined_damping`)
    Open squares : Sznajder MRF+FOM 2025 digitization
                   (column `sznajder_total_damping`)

    MAPE values (source:
    `paper/data/md/sznajder_anf_cmq_cfd_comparator_2026_05_11.md`):
      - 53.0 % over all 10 points
      - 31.6 % over the 8 supersonic points (M >= 1.29) excluding the
        transonic-peak overshoot at M = 1.08, 1.11

    The transonic-peak overshoot is driven by ORP's k_transonic Gaussian
    augmentation in BarrowmanStabilityCalculator; see memo Section
    "Interpretation".
    """
    df = pd.read_csv(SZNAJDER_CSV, comment="#").sort_values("mach")

    ax.plot(df["mach"], df["orp_combined_damping"],
            color=C_BLUE, label="OpenRocket Plus",
            marker="o", markersize=5, **MODEL_KW)
    ax.scatter(df["mach"], df["sznajder_total_damping"],
               facecolors="none", edgecolors=C_ORANGE,
               marker="s", s=70, linewidths=1.6,
               label="Sznajder MRF+FOM 2025 (TAR 281)",
               zorder=5)

    ax.set_xlabel("Mach number")
    ax.set_ylabel(r"$C_{m_q} + C_{m_{\dot{\alpha}}}$")
    ax.set_title(r"(d) Basic Finner pitch damping vs Mach -- "
                 r"Sznajder MRF+FOM")
    ax.legend(loc="lower right", framealpha=0.88, edgecolor="0.8",
              fontsize=LEGEND_SIZE - 0.5)

    _metric_box(ax,
                "MAPE = 31.6 % (8 pts, $M \\geq 1.29$)\n"
                "MAPE = 53.0 % (all 10 pts incl. transonic)\n"
                "Transonic peak: ORP $k_\\mathrm{trans}$ overshoot",
                loc="upper left")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    apply_style()

    fig, axes = plt.subplots(2, 2, figsize=(12, 10))
    ax_a, ax_b = axes[0, 0], axes[0, 1]
    ax_c, ax_d = axes[1, 0], axes[1, 1]

    panel_a_bunescu(ax_a)
    panel_b_sahu_placeholder(ax_b)
    panel_c_vidanovic(ax_c)
    panel_d_sznajder(ax_d)

    fig.suptitle("OpenRocket Plus vs four published-CFD sources "
                 "(JSR Section 8.2)",
                 fontsize=TITLE_SIZE + 1, fontweight="bold", y=0.995)

    fig.tight_layout(rect=(0, 0, 1, 0.97))

    PNG_OUT.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(PNG_OUT, dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)

    size = PNG_OUT.stat().st_size
    print(f"Wrote: {PNG_OUT}")
    print(f"Size:  {size:,} bytes  ({size/1024:.1f} KiB)")


if __name__ == "__main__":
    main()

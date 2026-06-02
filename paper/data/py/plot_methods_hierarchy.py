"""Fig. 1 (JSR paper) -- Hierarchy of supersonic aerodynamic methods.

Positions the present method (OpenRocket Plus) against existing tools on the
(computational cost, model fidelity) plane.  Uses only matplotlib + numpy.

Run:
    python plot_methods_hierarchy.py
"""
from __future__ import annotations

import sys
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

# ---------------------------------------------------------------------------
# Match project plot conventions
# ---------------------------------------------------------------------------
sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent))
try:
    from plot_style import apply_style, TAB10, TITLE_SIZE, LABEL_SIZE, ANNOT_SIZE
except Exception:
    # Fallback if import path differs in some environment
    def apply_style():
        plt.rcParams.update({
            "font.family": "sans-serif",
            "font.sans-serif": ["DejaVu Sans"],
            "axes.titleweight": "bold",
            "axes.grid": True,
            "grid.color": "0.85",
            "grid.linestyle": "--",
            "grid.linewidth": 0.7,
        })
    TAB10 = list(plt.cm.tab10.colors)
    TITLE_SIZE, LABEL_SIZE, ANNOT_SIZE = 13, 11, 9


HERE = Path(__file__).resolve().parent
OUT_DIR = HERE.parent / "png"
OUT_PATH = OUT_DIR / "aerodynamic_methods_hierarchy.png"


# ---------------------------------------------------------------------------
# Method positions (qualitative; x is computational cost on log scale,
# y is qualitative fidelity 0..10).  Costs are order-of-magnitude estimates
# expressed in "seconds per single Cd evaluation".
# ---------------------------------------------------------------------------
# Each entry: (label, cost_seconds_per_eval, fidelity, marker, color,
#              open_source, trajectory_simulator, dx_text, dy_text)
METHODS = [
    ("Original Barrowman / OpenRocket\n(Barrowman 1967; Niskanen 2009)",
     1.0e-4, 2.2, "o", TAB10[0], True,  True,   24, -22),
    ("Digital DATCOM / Missile DATCOM\n(USAF 1978)",
     2.0e+1, 5.0, "s", TAB10[1], False, False, -24,  20),
    ("RASAero II\n(Rogers, closed source)",
     1.0e-2, 6.5, "D", TAB10[3], False, True,  -24, -24),
    ("Aeroprediction AP98 / AP09\n(Moore et al.)",
     2.0e-1, 7.4, "^", TAB10[4], False, True,   28,  22),
    ("Navier-Stokes CFD\n(RANS / URANS)",
     1.0e+4, 9.5, "v", TAB10[7], True,  False, -28, -22),
    ("OpenRocket Plus (this work)",
     4.0e-3, 8.4, "*", TAB10[2], True,  True,  -28,  18),
]


def main() -> int:
    apply_style()
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    fig, ax = plt.subplots(figsize=(9.5, 6.5))
    ax.set_xscale("log")

    # X-axis: 1e-5 (sub-ms) ... 1e5 (~core-hours per coefficient)
    ax.set_xlim(1.0e-5, 1.0e5)
    ax.set_ylim(0.0, 10.5)

    # Vertical dashed separator: roughly between "trajectory simulator
    # subsystem" cost and "coefficient-only / batch" cost.
    sep_x = 1.0
    ax.axvline(sep_x, color="0.45", linestyle="--", linewidth=1.2, zorder=1)
    ax.text(sep_x * 1.15, 0.35,
            "Coefficient-only codes -->",
            fontsize=ANNOT_SIZE, color="0.30", ha="left", va="bottom")
    ax.text(sep_x / 1.15, 0.35,
            "<-- Trajectory simulators",
            fontsize=ANNOT_SIZE, color="0.30", ha="right", va="bottom")

    # Soft background bands for fidelity tiers
    ax.axhspan(0.0, 3.5, color="0.96", zorder=0)
    ax.axhspan(3.5, 7.0, color="0.93", zorder=0)
    ax.axhspan(7.0, 10.5, color="0.90", zorder=0)
    ax.text(1.2e-5, 1.6, "Low fidelity",  fontsize=ANNOT_SIZE - 1, color="0.4")
    ax.text(1.2e-5, 5.1, "Moderate",      fontsize=ANNOT_SIZE - 1, color="0.4")
    ax.text(1.2e-5, 8.7, "High fidelity", fontsize=ANNOT_SIZE - 1, color="0.4")

    # Plot each method
    seen_legend = set()
    for (label, cost, fid, marker, color, open_src, _traj, dx, dy) in METHODS:
        is_present = label.startswith("OpenRocket Plus (this work)")

        size = 320 if is_present else 150
        edge = "black"
        edge_w = 1.6 if is_present else 0.8
        z = 6 if is_present else 4

        # Legend key only listed once per (open/closed) bucket -- but we want
        # each method as a distinct legend entry, so use the label itself.
        ax.scatter(cost, fid, marker=marker, s=size, color=color,
                   edgecolors=edge, linewidths=edge_w, zorder=z,
                   label=label.split("\n")[0])

        # Open vs closed-source ring annotation
        ring_color = "tab:green" if open_src else "0.5"
        ring_label = "open-source" if open_src else "closed-source"
        ax.scatter(cost, fid, marker="o", s=size * 2.6,
                   facecolors="none", edgecolors=ring_color,
                   linewidths=1.1, linestyle=(0, (2, 2)) if not open_src else "-",
                   zorder=z - 1)
        if ring_label not in seen_legend:
            seen_legend.add(ring_label)

        # Text label with offset, in data->display points
        ha = "left" if dx >= 0 else "right"
        va = "bottom" if dy >= 0 else "top"
        weight = "bold" if is_present else "normal"
        ax.annotate(label, xy=(cost, fid), xytext=(dx, dy),
                    textcoords="offset points",
                    fontsize=ANNOT_SIZE, ha=ha, va=va,
                    weight=weight,
                    bbox=dict(boxstyle="round,pad=0.25",
                              facecolor="white", alpha=0.85,
                              edgecolor="0.75"),
                    arrowprops=dict(arrowstyle="-", color="0.55",
                                    linewidth=0.6))

    # Axis cosmetics
    ax.set_xlabel("Computational cost per coefficient evaluation  "
                  "(seconds, log scale)", fontsize=LABEL_SIZE)
    ax.set_ylabel("Model fidelity / supersonic accuracy (qualitative)",
                  fontsize=LABEL_SIZE)
    ax.set_title("Position of the present method among supersonic "
                 "aerodynamic tools", fontsize=TITLE_SIZE)

    # Tick decorations along x
    cost_ticks = [1e-5, 1e-3, 1e-1, 1e1, 1e3, 1e5]
    cost_labels = [
        r"10$^{-5}$ s" + "\n(microsec)",
        r"10$^{-3}$ s" + "\n(ms / step)",
        r"10$^{-1}$ s",
        r"10$^{1}$ s",
        r"10$^{3}$ s",
        r"10$^{5}$ s" + "\n(core-hours)",
    ]
    ax.set_xticks(cost_ticks)
    ax.set_xticklabels(cost_labels, fontsize=ANNOT_SIZE - 1)
    ax.set_yticks([0, 2, 4, 6, 8, 10])
    ax.set_yticklabels(["0", "2", "4", "6", "8", "10"])
    ax.grid(True, which="major", linestyle="--", color="0.85", linewidth=0.7)

    # Legend (open/closed ring convention)
    open_handle = plt.Line2D([0], [0], marker="o", color="w",
                             markerfacecolor="none", markeredgecolor="tab:green",
                             markersize=12, markeredgewidth=1.4,
                             label="Open source")
    closed_handle = plt.Line2D([0], [0], marker="o", color="w",
                               markerfacecolor="none", markeredgecolor="0.5",
                               markersize=12, markeredgewidth=1.4,
                               linestyle=(0, (2, 2)),
                               label="Closed source")
    present_handle = plt.Line2D([0], [0], marker="*", color=TAB10[2],
                                markeredgecolor="black", markeredgewidth=1.4,
                                markersize=18, linestyle="none",
                                label="This work")
    leg = ax.legend(handles=[present_handle, open_handle, closed_handle],
                    loc="lower right", framealpha=0.92, edgecolor="0.8",
                    fontsize=ANNOT_SIZE)
    leg.set_zorder(10)

    fig.tight_layout()
    fig.savefig(OUT_PATH, dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)

    print(str(OUT_PATH.resolve()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

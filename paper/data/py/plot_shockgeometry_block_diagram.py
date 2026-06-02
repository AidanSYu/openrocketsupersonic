"""Fig. 2 (JSR paper) -- ShockGeometry pre-pass data-flow block diagram.

Shows the once-per-timestep architectural seam:
    FlightConditions -> ShockGeometry.compute() -> {SymmetricComponentCalc,
    FinSetCalc, BarrowmanDragCalculator} -> AerodynamicForces.

Uses only matplotlib + numpy.

Run:
    python plot_shockgeometry_block_diagram.py
"""
from __future__ import annotations

import sys
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

# ---------------------------------------------------------------------------
# Match project plot conventions
# ---------------------------------------------------------------------------
sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent))
try:
    from plot_style import apply_style, TITLE_SIZE, ANNOT_SIZE
except Exception:
    def apply_style():
        plt.rcParams.update({
            "font.family": "sans-serif",
            "font.sans-serif": ["DejaVu Sans"],
            "axes.titleweight": "bold",
        })
    TITLE_SIZE, ANNOT_SIZE = 13, 9


HERE = Path(__file__).resolve().parent
OUT_DIR = HERE.parent / "png"
OUT_PATH = OUT_DIR / "shockgeometry_block_diagram.png"


# ---------------------------------------------------------------------------
# Palette (pale fills, dark borders) -- conventional for block diagrams
# ---------------------------------------------------------------------------
COLOR_INPUT  = "#D9E8F5"   # pale blue
COLOR_PRE    = "#FCE3CC"   # pale orange
COLOR_COMP   = "#D9ECD1"   # pale green
COLOR_OUTPUT = "#FFF6CC"   # pale yellow
EDGE         = "#333333"


def add_box(ax, x, y, w, h, *, facecolor, title, body=None, italic_body=False,
            title_size=11, body_size=9):
    """Draw a rounded rectangle with a bold title and optional smaller body."""
    box = FancyBboxPatch((x, y), w, h,
                         boxstyle="round,pad=0.02,rounding_size=0.04",
                         linewidth=1.4, edgecolor=EDGE,
                         facecolor=facecolor, zorder=3)
    ax.add_patch(box)

    cx = x + w / 2.0
    if body:
        ax.text(cx, y + h * 0.72, title,
                ha="center", va="center",
                fontsize=title_size, fontweight="bold", zorder=4)
        ax.text(cx, y + h * 0.32, body,
                ha="center", va="center",
                fontsize=body_size,
                style="italic" if italic_body else "normal",
                color="#333333", zorder=4)
    else:
        ax.text(cx, y + h / 2.0, title,
                ha="center", va="center",
                fontsize=title_size, fontweight="bold", zorder=4)
    return (cx, y + h, cx, y)  # (top_x, top_y, bottom_x, bottom_y) anchors


def add_arrow(ax, x0, y0, x1, y1, *, label=None, label_pos=0.5,
              color=EDGE, linewidth=1.6):
    arr = FancyArrowPatch((x0, y0), (x1, y1),
                          arrowstyle="-|>",
                          mutation_scale=18,
                          color=color, linewidth=linewidth, zorder=2)
    ax.add_patch(arr)
    if label:
        lx = x0 + (x1 - x0) * label_pos
        ly = y0 + (y1 - y0) * label_pos
        ax.text(lx, ly, label, fontsize=ANNOT_SIZE - 1, color="#444444",
                ha="left", va="center",
                bbox=dict(boxstyle="round,pad=0.15", facecolor="white",
                          edgecolor="0.85", alpha=0.9))


def main() -> int:
    apply_style()
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    fig, ax = plt.subplots(figsize=(10.0, 7.5))
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 10)
    ax.set_aspect("equal")
    ax.axis("off")

    # Title
    ax.text(5.0, 9.7,
            "ShockGeometry pre-pass: once-per-timestep architectural seam",
            ha="center", va="center",
            fontsize=TITLE_SIZE, fontweight="bold")

    # -----------------------------------------------------------------------
    # Row 1 -- input: FlightConditions
    # -----------------------------------------------------------------------
    bw_top, bh_top = 5.6, 0.95
    bx_top = (10 - bw_top) / 2.0
    by_top = 8.20
    top = add_box(ax, bx_top, by_top, bw_top, bh_top,
                  facecolor=COLOR_INPUT,
                  title="FlightConditions",
                  body=r"$M_\infty,\ q_\infty,\ T_\infty,\ p_\infty,\ "
                       r"\alpha,\ \beta$",
                  italic_body=False,
                  title_size=12, body_size=10)
    top_bottom = (top[2], top[3])

    # -----------------------------------------------------------------------
    # Row 2 -- pre-pass: ShockGeometry.compute()
    # -----------------------------------------------------------------------
    bw_pre, bh_pre = 6.6, 1.25
    bx_pre = (10 - bw_pre) / 2.0
    by_pre = 5.85
    pre = add_box(ax, bx_pre, by_pre, bw_pre, bh_pre,
                  facecolor=COLOR_PRE,
                  title="ShockGeometry.compute()",
                  body=(r"inert at $M_\infty < 1$    "
                        r"|    $<\!10\ \mu\mathrm{s}$ per call    "
                        r"|    $C^{1}$ activation through "
                        r"$M_\infty=1.0\!-\!1.1$"),
                  italic_body=True,
                  title_size=12, body_size=9)
    pre_top = (pre[0], pre[1])
    pre_bottom = (pre[2], pre[3])

    # Arrow row1 -> row2
    add_arrow(ax, top_bottom[0], top_bottom[1] - 0.03,
              pre_top[0], pre_top[1] + 0.03)

    # -----------------------------------------------------------------------
    # Row 3 -- component calculators (3 parallel boxes)
    # -----------------------------------------------------------------------
    bh_c = 1.95
    bw_c = 3.15
    by_c = 2.55
    # Asymmetric spacing -- thin outer margins, thicker inner gaps for the
    # 3 fan-out arrows to remain visually distinct
    outer = 0.15
    gap   = (10 - 2 * outer - 3 * bw_c) / 2.0
    xs = [outer, outer + bw_c + gap, outer + 2 * (bw_c + gap)]

    comp_defs = [
        ("SymmetricComponentCalc",
         "nose + body\n"
         "getConditionsAt(x_nose)\n"
         "getConditionsAt(x_body)",
         r"$M_{\mathrm{local}},\ p_{\mathrm{local}},\ "
         r"T_{\mathrm{local}},\ q_{\mathrm{local}}$"),
        ("FinSetCalc",
         "\ngetConditionsAt(x_fin_root)\n",
         r"$M_{\mathrm{local}},\ p_{\mathrm{local}},\ "
         r"T_{\mathrm{local}},\ q_{\mathrm{local}}$"),
        ("BarrowmanDragCalculator",
         "\ngetConditionsAt(x_base)\n",
         r"$M_{\mathrm{local}},\ p_{\mathrm{local}},\ "
         r"T_{\mathrm{local}},\ q_{\mathrm{local}}$"),
    ]

    comp_anchors = []
    for x0, (title, body, math_line) in zip(xs, comp_defs):
        box = FancyBboxPatch((x0, by_c), bw_c, bh_c,
                             boxstyle="round,pad=0.02,rounding_size=0.04",
                             linewidth=1.4, edgecolor=EDGE,
                             facecolor=COLOR_COMP, zorder=3)
        ax.add_patch(box)
        cx = x0 + bw_c / 2.0
        # Title near top
        ax.text(cx, by_c + bh_c * 0.85, title,
                ha="center", va="center",
                fontsize=8.4, fontweight="bold", zorder=4)
        # Method body (italic, small)
        ax.text(cx, by_c + bh_c * 0.50, body,
                ha="center", va="center",
                fontsize=8.2, style="italic", color="#333333", zorder=4)
        # Math line near bottom (regular -- mathtext italics already)
        ax.text(cx, by_c + bh_c * 0.15, math_line,
                ha="center", va="center",
                fontsize=9.0, color="#222222", zorder=4)
        comp_anchors.append((cx, by_c + bh_c, cx, by_c))

    # Arrows row2 -> row3 (fan-out)
    for (cx_top, _y_top, _cx_bot, _y_bot) in comp_anchors:
        add_arrow(ax,
                  pre_bottom[0] + (cx_top - pre_bottom[0]) * 0.0,
                  pre_bottom[1] - 0.03,
                  cx_top, by_c + bh_c + 0.03)

    # -----------------------------------------------------------------------
    # Row 4 -- output: AerodynamicForces
    # -----------------------------------------------------------------------
    bw_out, bh_out = 5.2, 0.95
    bx_out = (10 - bw_out) / 2.0
    by_out = 0.55
    out = add_box(ax, bx_out, by_out, bw_out, bh_out,
                  facecolor=COLOR_OUTPUT,
                  title="AerodynamicForces",
                  body=r"$C_N,\ C_{P},\ C_D,\ C_{m_q},\ "
                       r"C_{m_\alpha},\ \dots$",
                  italic_body=False,
                  title_size=12, body_size=10)
    out_top = (out[0], out[1])

    # Arrows row3 -> row4 (fan-in)
    for (_cx_top, _y_top, cx_bot, y_bot) in comp_anchors:
        add_arrow(ax, cx_bot, y_bot - 0.03,
                  out_top[0], out_top[1] + 0.03)

    # -----------------------------------------------------------------------
    # Caption text on the left margin (small) describing flow direction
    # -----------------------------------------------------------------------
    ax.text(0.10, 5.0, "data flow",
            fontsize=ANNOT_SIZE, color="0.45",
            rotation=90, ha="left", va="center")
    ax.annotate("", xy=(0.05, 4.0), xytext=(0.05, 6.0),
                arrowprops=dict(arrowstyle="-|>", color="0.55", linewidth=1.2))

    fig.savefig(OUT_PATH, dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)

    print(str(OUT_PATH.resolve()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

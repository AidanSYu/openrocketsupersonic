"""Fig. 22 -- Sensitivity tornado four-panel composite.

Stitches the four per-flight tornado PNGs produced by
`paper/data/analysis/sensitivity_2026_05_11/analyze.py` into a single 2x2
grid suitable for the JSR paper. Adds a figure-level title and panel
sub-titles.

Run:
    python paper/data/py/plot_sensitivity_composite.py
"""

from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from PIL import Image


SCRIPT_DIR = Path(__file__).resolve().parent
DATA_DIR = SCRIPT_DIR.parent if SCRIPT_DIR.name == "py" else SCRIPT_DIR
ANALYSIS_DIR = DATA_DIR / "analysis" / "sensitivity_2026_05_11"
PNG_DIR = DATA_DIR / "png"
OUT_NAME = "sensitivity_tornado_composite.png"

# Panel order:
#   (row, col, filename, panel_title)
PANELS = [
    (0, 0, "tornado_heros3.png",            "HEROS 3 (M_peak ~ 2.5)"),
    (0, 1, "tornado_arcas_blunt_f2.png",    "Arcas blunt F2 (M_peak ~ 2.0)"),
    (1, 0, "tornado_nike_apache_14_108.png", "Nike-Apache 14.108 (M_peak ~ 7.0)"),
    (1, 1, "tornado_bbv_aaf32.png",         "Black Brant V AAF-32 (M_peak ~ 4.5)"),
]


def main() -> None:
    PNG_DIR.mkdir(parents=True, exist_ok=True)

    missing = [p[2] for p in PANELS if not (ANALYSIS_DIR / p[2]).exists()]
    if missing:
        fig, ax = plt.subplots(figsize=(8, 6))
        ax.set_axis_off()
        ax.text(
            0.5,
            0.5,
            "Data pending: tornado PNGs missing:\n" + "\n".join(missing),
            ha="center",
            va="center",
            fontsize=11,
            bbox={"boxstyle": "round,pad=0.6", "facecolor": "#fff3cd", "edgecolor": "#856404"},
        )
        out_path = PNG_DIR / OUT_NAME
        fig.savefig(out_path, dpi=200, bbox_inches="tight", facecolor="white")
        plt.close(fig)
        print(f"[placeholder] wrote {out_path}")
        return

    fig, axes = plt.subplots(2, 2, figsize=(12, 10))
    for r, c, fname, title in PANELS:
        ax = axes[r, c]
        img = Image.open(ANALYSIS_DIR / fname)
        ax.imshow(img)
        ax.set_axis_off()
        ax.set_title(title, fontsize=11, fontweight="bold", pad=4)

    fig.suptitle(
        "Sensitivity tornado per flight\n"
        "Apogee delta (%) for +/- 1 sigma swing in each model parameter",
        fontsize=14,
        fontweight="bold",
    )

    plt.tight_layout()
    plt.subplots_adjust(top=0.92, wspace=0.02, hspace=0.06)

    out_path = PNG_DIR / OUT_NAME
    fig.savefig(out_path, dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print(f"wrote {out_path}")


if __name__ == "__main__":
    main()

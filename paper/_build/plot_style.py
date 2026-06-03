"""Shared publication-quality plot style for OpenRocket Plus validation paper.

Usage:
    from plot_style import *
    apply_style()
    fig, ax_main, ax_resid = validation_figure("Title", "x", "y")
"""

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.gridspec as gridspec
import numpy as np

# ---------------------------------------------------------------------------
# Dimensions
# ---------------------------------------------------------------------------
SINGLE_FIG = (8, 5)       # single-panel validation plot
WIDE_FIG   = (12, 5)      # multi-panel or landscape
TALL_FIG   = (8, 7)       # single-panel with large residual
DPI = 300

# ---------------------------------------------------------------------------
# Typography (DejaVu Sans is Matplotlib default)
# ---------------------------------------------------------------------------
TITLE_SIZE  = 13
LABEL_SIZE  = 11
TICK_SIZE   = 10
LEGEND_SIZE = 9.5
ANNOT_SIZE  = 9

# ---------------------------------------------------------------------------
# Colour palette  – first 10 from Tableau 10
# ---------------------------------------------------------------------------
TAB10 = list(plt.cm.tab10.colors)
C_BLUE, C_ORANGE, C_GREEN, C_RED, C_PURPLE = TAB10[:5]
C_BROWN, C_PINK, C_GREY, C_OLIVE, C_CYAN  = TAB10[5:10]

# ---------------------------------------------------------------------------
# Marker vocabulary  – reference data only (model lines have no markers)
# ---------------------------------------------------------------------------
REF_MARKERS = ["o", "s", "^", "D", "v", "P", "X", "p", "*", "h"]
REF_KW = dict(edgecolors="black", linewidths=0.5, zorder=5, s=50)

# ---------------------------------------------------------------------------
# Model-line defaults
# ---------------------------------------------------------------------------
MODEL_KW = dict(linewidth=2.0, zorder=3)


def apply_style():
    """Set global rcParams for publication figures."""
    plt.rcParams.update({
        "font.family": "sans-serif",
        "font.sans-serif": ["DejaVu Sans"],
        "font.size": LABEL_SIZE,
        "axes.titlesize": TITLE_SIZE,
        "axes.titleweight": "bold",
        "axes.labelsize": LABEL_SIZE,
        "xtick.labelsize": TICK_SIZE,
        "ytick.labelsize": TICK_SIZE,
        "legend.fontsize": LEGEND_SIZE,
        "legend.framealpha": 0.85,
        "legend.edgecolor": "0.8",
        "grid.color": "0.85",
        "grid.linestyle": "--",
        "grid.linewidth": 0.7,
        "axes.grid": True,
        "figure.dpi": 100,
        "savefig.dpi": DPI,
        "savefig.bbox": "tight",
        "savefig.facecolor": "white",
    })


# ===================================================================
# Figure factories
# ===================================================================

def validation_figure(title, xlabel, ylabel, figsize=None, resid_label="Error (%)"):
    """Single-panel figure with residual strip (bottom 25 %).

    Returns (fig, ax_main, ax_resid).
    """
    if figsize is None:
        figsize = SINGLE_FIG
    fig = plt.figure(figsize=figsize)
    gs = gridspec.GridSpec(2, 1, height_ratios=[3, 1], hspace=0.08)
    ax_main  = fig.add_subplot(gs[0])
    ax_resid = fig.add_subplot(gs[1], sharex=ax_main)

    ax_main.set_title(title)
    ax_main.set_ylabel(ylabel)
    plt.setp(ax_main.get_xticklabels(), visible=False)

    ax_resid.set_xlabel(xlabel)
    ax_resid.set_ylabel(resid_label, fontsize=TICK_SIZE)
    ax_resid.axhline(0, color="black", linewidth=0.5)
    ax_resid.tick_params(axis="y", labelsize=TICK_SIZE - 1)

    return fig, ax_main, ax_resid


def simple_figure(title, xlabel, ylabel, figsize=None):
    """Single-panel figure without residual (for internal / ORP-only plots).

    Returns (fig, ax).
    """
    if figsize is None:
        figsize = SINGLE_FIG
    fig, ax = plt.subplots(figsize=figsize)
    ax.set_title(title)
    ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    return fig, ax


def multi_validation_figure(nrows, ncols, title, figsize=None,
                            sharex=False, sharey=False):
    """Grid of panels.  Returns (fig, axes_2d)."""
    if figsize is None:
        figsize = WIDE_FIG
    fig, axes = plt.subplots(nrows, ncols, figsize=figsize,
                             sharex=sharex, sharey=sharey)
    fig.suptitle(title, fontsize=TITLE_SIZE + 1, fontweight="bold")
    return fig, axes


# ===================================================================
# Drawing helpers
# ===================================================================

def ref_scatter(ax, x, y, *, label, ci=0, mi=0, **kw):
    """Plot reference data as scatter markers (no connecting line)."""
    merged = {**REF_KW, **kw}
    ax.scatter(x, y, label=label, color=TAB10[ci % 10],
               marker=REF_MARKERS[mi % len(REF_MARKERS)], **merged)


def model_line(ax, x, y, *, label, ci=0, **kw):
    """Plot model output as solid line (no markers)."""
    merged = {**MODEL_KW, **kw}
    ax.plot(x, y, label=label, color=TAB10[ci % 10], **merged)


def plot_residuals(ax_resid, x, pct_errors, *, ci=0, mi=0, label=None):
    """Scatter percent errors into the residual strip."""
    ax_resid.scatter(x, pct_errors, color=TAB10[ci % 10],
                     marker=REF_MARKERS[mi % len(REF_MARKERS)],
                     s=28, alpha=0.8, edgecolors="none", label=label)


def add_legend(ax, **kw):
    """Legend inside plot area with semi-transparent background."""
    defaults = dict(loc="best", framealpha=0.85, edgecolor="0.8",
                    fontsize=LEGEND_SIZE)
    defaults.update(kw)
    ax.legend(**defaults)


def metrics_box(ax, text, loc="upper right"):
    """Add a small text box with metrics inside the axes."""
    anchors = {
        "upper right": (0.97, 0.97, "top", "right"),
        "upper left":  (0.03, 0.97, "top", "left"),
        "lower right": (0.97, 0.03, "bottom", "right"),
        "lower left":  (0.03, 0.03, "bottom", "left"),
    }
    x, y, va, ha = anchors.get(loc, anchors["upper right"])
    ax.text(x, y, text, transform=ax.transAxes, va=va, ha=ha,
            fontsize=ANNOT_SIZE,
            bbox=dict(boxstyle="round,pad=0.3", facecolor="white",
                      alpha=0.9, edgecolor="0.8"))


# ===================================================================
# Metric computation
# ===================================================================

def compute_metrics(observed, predicted):
    """Return dict with mae, rmse, mape, max_pct, n, errors, pct_errors."""
    obs = np.asarray(observed, dtype=float)
    pred = np.asarray(predicted, dtype=float)
    errors = pred - obs
    with np.errstate(divide="ignore", invalid="ignore"):
        pct = np.where(np.abs(obs) > 1e-12,
                       errors / obs * 100.0, np.nan)
    abs_pct = np.abs(pct)
    return {
        "n": int(obs.size),
        "mae": float(np.mean(np.abs(errors))),
        "rmse": float(np.sqrt(np.mean(errors**2))),
        "mape": float(np.nanmean(abs_pct)),
        "max_pct": float(np.nanmax(abs_pct)) if np.any(np.isfinite(abs_pct)) else 0.0,
        "mean_bias": float(np.mean(errors)),
        "errors": errors,
        "pct_errors": pct,
    }


def fmt_metrics(m, qty_name=""):
    """One-line summary string."""
    prefix = f"{qty_name}: " if qty_name else ""
    return f"{prefix}n={m['n']}  MAE={m['mae']:.4g}  MAPE={m['mape']:.2f}%"


# ===================================================================
# Save helper
# ===================================================================

def save(fig, path):
    """Save figure and close."""
    path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(path, dpi=DPI, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print(f"  -> {path.name}")

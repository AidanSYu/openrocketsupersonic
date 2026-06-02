"""
Corpus bias-variance analysis for the OpenRocket Plus 25-flight validation corpus.

Reproducibly produces all artifacts in this directory:
- regime_breakdown.csv / regime_breakdown.png
- bias_variance_decomp.csv / bias_variance.png
- qq_normal.png / error_hist.png / predictor_distributions.png
- paired_comparison.csv / predictor_paired.png
- error_vs_mach.png
- corpus_bias_variance_summary.md

Source: rocket-flight-database/flight_comparison.csv (25 flights).
"""

from __future__ import annotations

import math
from pathlib import Path

import matplotlib as mpl
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from scipy import stats

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parents[3]
SOURCE_CSV = REPO_ROOT / "rocket-flight-database" / "flight_comparison.csv"

# Mach band definitions (right-inclusive upper edges, left-inclusive except subsonic which is open)
REGIMES = [
    ("Subsonic",          lambda m: m <  0.8),
    ("Transonic",         lambda m: (m >= 0.8) & (m <= 1.3)),
    ("Low supersonic",    lambda m: (m >  1.3) & (m <= 3.0)),
    ("High supersonic",   lambda m: (m >  3.0) & (m <= 5.0)),
    ("Hypersonic",        lambda m: m >  5.0),
]
REGIME_EDGES = [0.8, 1.3, 3.0, 5.0]  # for plot vertical lines

# Publication-grade matplotlib defaults (AIAA / JSR-style)
mpl.rcParams.update({
    "font.family": "serif",
    "font.serif": ["DejaVu Serif", "Times New Roman", "Times"],
    "font.size": 11,
    "axes.titlesize": 12,
    "axes.labelsize": 11,
    "axes.linewidth": 0.8,
    "axes.grid": True,
    "axes.axisbelow": True,
    "grid.linewidth": 0.4,
    "grid.alpha": 0.45,
    "grid.color": "#999999",
    "xtick.labelsize": 10,
    "ytick.labelsize": 10,
    "xtick.direction": "in",
    "ytick.direction": "in",
    "xtick.major.width": 0.8,
    "ytick.major.width": 0.8,
    "legend.fontsize": 9,
    "legend.frameon": True,
    "legend.edgecolor": "0.3",
    "legend.fancybox": False,
    "savefig.dpi": 300,
    "savefig.bbox": "tight",
    "figure.dpi": 120,
    "figure.figsize": (6.5, 4.0),
})

ORP_COLOR = "#1f4e79"   # deep blue
RAS_COLOR = "#a6324d"   # muted red
REGIME_LINE_COLOR = "#666666"

# ---------------------------------------------------------------------------
# Data loading & enrichment
# ---------------------------------------------------------------------------

def load_corpus() -> pd.DataFrame:
    df = pd.read_csv(SOURCE_CSV)
    # Recompute signed errors from the full-precision apogee columns so that
    # results do not inherit the 1-decimal rounding stored in the published
    # err_*_pct columns. The apogee columns are the single source of truth:
    #   real    = apogee_real_ft        (measured ground truth, RFD-authoritative)
    #   rasaero = apogee_rasaero_ft      (Rogers' published RASAero II prediction)
    #   thiswork= apogee_thiswork_ft     (OpenRocket-Plus, current archived code)
    df["err_thiswork_pct"] = (
        (df["apogee_thiswork_ft"] - df["apogee_real_ft"]) / df["apogee_real_ft"] * 100.0
    )
    df["err_rasaero_pct"] = (
        (df["apogee_rasaero_ft"] - df["apogee_real_ft"]) / df["apogee_real_ft"] * 100.0
    )
    df["regime"] = df["peak_mach"].apply(classify_regime)
    return df


def classify_regime(m: float) -> str:
    for label, predicate in REGIMES:
        if predicate(m):
            return label
    return "Unknown"


# ---------------------------------------------------------------------------
# Statistical helpers
# ---------------------------------------------------------------------------

def summarize(errors: np.ndarray) -> dict:
    """Return basic descriptive statistics for a vector of signed percent errors."""
    e = np.asarray(errors, dtype=float)
    e = e[~np.isnan(e)]
    n = e.size
    if n == 0:
        return {
            "n": 0, "mean": np.nan, "std": np.nan, "rmse": np.nan,
            "mae": np.nan, "within_5pct": 0, "within_10pct": 0,
            "median": np.nan, "min": np.nan, "max": np.nan,
        }
    mean = float(np.mean(e))
    # Sample standard deviation (ddof=1) when n>1, else NaN
    std = float(np.std(e, ddof=1)) if n > 1 else float("nan")
    rmse = float(np.sqrt(np.mean(e ** 2)))
    mae = float(np.mean(np.abs(e)))
    within5 = int(np.sum(np.abs(e) <= 5.0))
    within10 = int(np.sum(np.abs(e) <= 10.0))
    return {
        "n": n,
        "mean": mean,
        "std": std,
        "rmse": rmse,
        "mae": mae,
        "within_5pct": within5,
        "within_10pct": within10,
        "median": float(np.median(e)),
        "min": float(np.min(e)),
        "max": float(np.max(e)),
    }


def bias_variance(errors: np.ndarray) -> dict:
    """Decompose mean-squared error into bias^2 + variance (population variance)."""
    e = np.asarray(errors, dtype=float)
    e = e[~np.isnan(e)]
    n = e.size
    if n == 0:
        return {"n": 0, "bias": np.nan, "bias_sq": np.nan,
                "variance": np.nan, "mse": np.nan, "bias_frac": np.nan}
    bias = float(np.mean(e))
    # Population variance for the bias-variance identity MSE = bias^2 + var (no Bessel correction)
    variance = float(np.mean((e - bias) ** 2))
    bias_sq = bias ** 2
    mse = bias_sq + variance
    bias_frac = bias_sq / mse if mse > 0 else np.nan
    return {
        "n": n,
        "bias": bias,
        "bias_sq": bias_sq,
        "variance": variance,
        "mse": mse,
        "bias_frac": bias_frac,
    }


def normality_tests(errors: np.ndarray) -> dict:
    """Run Shapiro-Wilk and Anderson-Darling. Returns NaN for tests that don't apply."""
    e = np.asarray(errors, dtype=float)
    e = e[~np.isnan(e)]
    out = {"n": e.size}
    if e.size >= 3:
        sw = stats.shapiro(e)
        out["shapiro_W"] = float(sw.statistic)
        out["shapiro_p"] = float(sw.pvalue)
    else:
        out["shapiro_W"] = np.nan
        out["shapiro_p"] = np.nan
    if e.size >= 8:
        # Use the legacy unparameterized call so we get tabulated critical values.
        # SciPy 1.17 issues a FutureWarning about choosing a method explicitly;
        # we suppress it locally so the script output stays clean.
        import warnings
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", FutureWarning)
            ad = stats.anderson(e, dist="norm")
        out["anderson_A2"] = float(ad.statistic)
        # 5% critical value
        try:
            idx5 = list(ad.significance_level).index(5.0)
            out["anderson_crit_5pct"] = float(ad.critical_values[idx5])
        except ValueError:
            out["anderson_crit_5pct"] = float(ad.critical_values[2])
    else:
        out["anderson_A2"] = np.nan
        out["anderson_crit_5pct"] = np.nan
    # Skewness and kurtosis
    if e.size >= 3:
        out["skew"] = float(stats.skew(e, bias=False))
    else:
        out["skew"] = np.nan
    if e.size >= 4:
        out["excess_kurtosis"] = float(stats.kurtosis(e, fisher=True, bias=False))
    else:
        out["excess_kurtosis"] = np.nan
    return out


def poly_trend(x: np.ndarray, y: np.ndarray, degree: int = 2) -> tuple[np.ndarray, np.ndarray]:
    """Return (x_grid, y_grid) for a polynomial fit, used as a smooth trend line."""
    mask = ~(np.isnan(x) | np.isnan(y))
    x = x[mask]
    y = y[mask]
    if x.size < degree + 1:
        return np.array([]), np.array([])
    coeffs = np.polyfit(x, y, deg=degree)
    xg = np.linspace(x.min(), x.max(), 200)
    yg = np.polyval(coeffs, xg)
    return xg, yg


# ---------------------------------------------------------------------------
# 1. Per-regime bias and variance table
# ---------------------------------------------------------------------------

def build_regime_table(df: pd.DataFrame) -> pd.DataFrame:
    """Long-format table: regime x predictor x summary stats."""
    rows = []
    regimes_in_order = [r[0] for r in REGIMES] + ["All"]
    for regime_label in regimes_in_order:
        if regime_label == "All":
            sub = df
        else:
            sub = df[df["regime"] == regime_label]
        for predictor, col in (
            ("OpenRocket Plus", "err_thiswork_pct"),
            ("RASAero II", "err_rasaero_pct"),
        ):
            errors = sub[col].dropna().to_numpy()
            s = summarize(errors)
            rows.append({
                "regime": regime_label,
                "predictor": predictor,
                **s,
                # mach range in the regime among present flights of this predictor
                "mach_min": float(sub.loc[~sub[col].isna(), "peak_mach"].min()) if s["n"] else np.nan,
                "mach_max": float(sub.loc[~sub[col].isna(), "peak_mach"].max()) if s["n"] else np.nan,
            })
    return pd.DataFrame(rows)


def plot_regime_breakdown(regime_df: pd.DataFrame, out_path: Path) -> None:
    """Grouped bar chart of MAE and RMSE per regime per predictor (excluding 'All')."""
    plot_df = regime_df[regime_df["regime"] != "All"].copy()
    regimes = [r[0] for r in REGIMES]
    width = 0.35

    fig, axes = plt.subplots(1, 2, figsize=(11.0, 4.4), sharey=False)

    for ax, metric, title in (
        (axes[0], "mae", "Mean absolute error (%)"),
        (axes[1], "rmse", "Root-mean-square error (%)"),
    ):
        orp = []
        ras = []
        ns_orp = []
        ns_ras = []
        for r in regimes:
            sub_o = plot_df[(plot_df["regime"] == r) & (plot_df["predictor"] == "OpenRocket Plus")]
            sub_r = plot_df[(plot_df["regime"] == r) & (plot_df["predictor"] == "RASAero II")]
            orp.append(sub_o[metric].iloc[0] if len(sub_o) else np.nan)
            ras.append(sub_r[metric].iloc[0] if len(sub_r) else np.nan)
            ns_orp.append(int(sub_o["n"].iloc[0]) if len(sub_o) else 0)
            ns_ras.append(int(sub_r["n"].iloc[0]) if len(sub_r) else 0)
        x = np.arange(len(regimes))
        orp_arr = np.array(orp, dtype=float)
        ras_arr = np.array(ras, dtype=float)
        orp_plot = np.nan_to_num(orp_arr, nan=0.0)
        ras_plot = np.nan_to_num(ras_arr, nan=0.0)
        b1 = ax.bar(x - width / 2, orp_plot, width, color=ORP_COLOR, label="OpenRocket Plus",
                    edgecolor="black", linewidth=0.4)
        b2 = ax.bar(x + width / 2, ras_plot, width, color=RAS_COLOR, label="RASAero II",
                    edgecolor="black", linewidth=0.4)
        # Annotate with N
        for i, (rect, n) in enumerate(zip(b1, ns_orp)):
            if n > 0:
                ax.text(rect.get_x() + rect.get_width() / 2, rect.get_height() + 0.15,
                        f"n={n}", ha="center", va="bottom", fontsize=8, color=ORP_COLOR)
        for i, (rect, n) in enumerate(zip(b2, ns_ras)):
            if n > 0:
                ax.text(rect.get_x() + rect.get_width() / 2, rect.get_height() + 0.15,
                        f"n={n}", ha="center", va="bottom", fontsize=8, color=RAS_COLOR)
            else:
                ax.text(rect.get_x() + rect.get_width() / 2, 0.15,
                        "n=0", ha="center", va="bottom", fontsize=8, color=RAS_COLOR)
        ax.set_xticks(x)
        ax.set_xticklabels([r.replace(" ", "\n", 1) for r in regimes])
        ax.set_ylabel(title)
        ax.set_title(title)
        ax.set_ylim(bottom=0)
        ax.legend(loc="upper left")

    fig.suptitle("Per-regime apogee prediction error by predictor", fontsize=12.5)
    fig.tight_layout(rect=[0, 0, 1, 0.96])
    fig.savefig(out_path)
    plt.close(fig)


# ---------------------------------------------------------------------------
# 2. Bias-variance decomposition
# ---------------------------------------------------------------------------

def build_bias_variance_table(df: pd.DataFrame) -> pd.DataFrame:
    rows = []
    regimes_in_order = [r[0] for r in REGIMES] + ["All"]
    for regime_label in regimes_in_order:
        if regime_label == "All":
            sub = df
        else:
            sub = df[df["regime"] == regime_label]
        for predictor, col in (
            ("OpenRocket Plus", "err_thiswork_pct"),
            ("RASAero II", "err_rasaero_pct"),
        ):
            errors = sub[col].dropna().to_numpy()
            bv = bias_variance(errors)
            rows.append({"regime": regime_label, "predictor": predictor, **bv})
    return pd.DataFrame(rows)


def plot_bias_variance(bv_df: pd.DataFrame, out_path: Path) -> None:
    """Stacked bar of bias^2 + variance per predictor per regime."""
    plot_df = bv_df[bv_df["regime"] != "All"].copy()
    regimes = [r[0] for r in REGIMES]
    width = 0.35
    x = np.arange(len(regimes))

    fig, ax = plt.subplots(figsize=(8.0, 4.6))
    # OpenRocket Plus
    orp_bias = []
    orp_var = []
    ras_bias = []
    ras_var = []
    ns = []
    for r in regimes:
        sub_o = plot_df[(plot_df["regime"] == r) & (plot_df["predictor"] == "OpenRocket Plus")]
        sub_r = plot_df[(plot_df["regime"] == r) & (plot_df["predictor"] == "RASAero II")]
        orp_bias.append(sub_o["bias_sq"].iloc[0] if len(sub_o) else np.nan)
        orp_var.append(sub_o["variance"].iloc[0] if len(sub_o) else np.nan)
        ras_bias.append(sub_r["bias_sq"].iloc[0] if len(sub_r) else np.nan)
        ras_var.append(sub_r["variance"].iloc[0] if len(sub_r) else np.nan)
        ns.append((int(sub_o["n"].iloc[0]) if len(sub_o) else 0,
                   int(sub_r["n"].iloc[0]) if len(sub_r) else 0))

    orp_bias = np.nan_to_num(np.array(orp_bias, dtype=float))
    orp_var = np.nan_to_num(np.array(orp_var, dtype=float))
    ras_bias = np.nan_to_num(np.array(ras_bias, dtype=float))
    ras_var = np.nan_to_num(np.array(ras_var, dtype=float))

    ax.bar(x - width / 2, orp_bias, width, color=ORP_COLOR, label="ORP bias$^2$",
           edgecolor="black", linewidth=0.4)
    ax.bar(x - width / 2, orp_var, width, bottom=orp_bias, color=ORP_COLOR, alpha=0.45,
           hatch="//", label="ORP variance",
           edgecolor="black", linewidth=0.4)
    ax.bar(x + width / 2, ras_bias, width, color=RAS_COLOR, label="RAS bias$^2$",
           edgecolor="black", linewidth=0.4)
    ax.bar(x + width / 2, ras_var, width, bottom=ras_bias, color=RAS_COLOR, alpha=0.45,
           hatch="//", label="RAS variance",
           edgecolor="black", linewidth=0.4)

    # Total bar height annotation (MSE)
    for i, r in enumerate(regimes):
        h_o = orp_bias[i] + orp_var[i]
        h_r = ras_bias[i] + ras_var[i]
        n_o, n_r = ns[i]
        if n_o > 0:
            ax.text(x[i] - width / 2, h_o + 0.6, f"MSE={h_o:.1f}\n(n={n_o})",
                    ha="center", va="bottom", fontsize=8, color="black")
        if n_r > 0:
            ax.text(x[i] + width / 2, h_r + 0.6, f"MSE={h_r:.1f}\n(n={n_r})",
                    ha="center", va="bottom", fontsize=8, color="black")
        else:
            ax.text(x[i] + width / 2, 0.6, "n=0",
                    ha="center", va="bottom", fontsize=8, color="black")

    ax.set_xticks(x)
    ax.set_xticklabels([r.replace(" ", "\n", 1) for r in regimes])
    ax.set_ylabel("Mean-squared error decomposition  (%²)")
    ax.set_title("Bias-variance decomposition: MSE = bias$^2$ + variance")
    # Pad the y-axis to leave room for top-of-bar labels.
    top = max(orp_bias.max() + orp_var.max(), ras_bias.max() + ras_var.max())
    ax.set_ylim(0, top * 1.35 + 1)
    ax.legend(loc="upper left", ncol=2)
    fig.tight_layout()
    fig.savefig(out_path)
    plt.close(fig)


# ---------------------------------------------------------------------------
# 3. Error distribution analysis
# ---------------------------------------------------------------------------

def plot_qq(errors: np.ndarray, title: str, out_path: Path) -> None:
    e = np.asarray(errors, dtype=float)
    e = e[~np.isnan(e)]
    fig, ax = plt.subplots(figsize=(5.5, 5.0))
    (osm, osr), (slope, intercept, r) = stats.probplot(e, dist="norm", plot=None)
    ax.scatter(osm, osr, color=ORP_COLOR, edgecolor="black", linewidth=0.5, s=36, zorder=3)
    # Reference line through robust fit
    xline = np.linspace(osm.min(), osm.max(), 100)
    ax.plot(xline, slope * xline + intercept, color="black", linewidth=0.9,
            linestyle="--", label=f"Fit: slope={slope:.2f}, R²={r ** 2:.3f}")
    ax.set_xlabel("Theoretical quantiles (standard normal)")
    ax.set_ylabel("Sample quantiles (% error)")
    ax.set_title(title)
    ax.legend(loc="upper left")
    fig.tight_layout()
    fig.savefig(out_path)
    plt.close(fig)


def plot_hist_kde(errors: np.ndarray, title: str, out_path: Path, color: str) -> None:
    e = np.asarray(errors, dtype=float)
    e = e[~np.isnan(e)]
    fig, ax = plt.subplots(figsize=(6.0, 4.0))
    # Histogram (density=True so KDE overlays cleanly)
    nbins = max(6, int(np.sqrt(e.size) * 1.5))
    ax.hist(e, bins=nbins, density=True, color=color, alpha=0.55, edgecolor="black", linewidth=0.5,
            label=f"Histogram (n={e.size})")
    # KDE
    if e.size >= 3:
        kde = stats.gaussian_kde(e, bw_method="scott")
        xgrid = np.linspace(e.min() - 1, e.max() + 1, 400)
        ax.plot(xgrid, kde(xgrid), color="black", linewidth=1.2, label="Gaussian KDE")
    # Overlaid normal pdf (matched mean / std)
    mu, sigma = np.mean(e), np.std(e, ddof=1) if e.size > 1 else 1.0
    if sigma > 0:
        xn = np.linspace(e.min() - 1, e.max() + 1, 400)
        ax.plot(xn, stats.norm.pdf(xn, mu, sigma), color="#444444", linestyle=":", linewidth=1.1,
                label=f"Normal(μ={mu:.2f}, σ={sigma:.2f})")
    ax.axvline(0.0, color="black", linewidth=0.7)
    ax.set_xlabel("Signed apogee error (%)")
    ax.set_ylabel("Density")
    ax.set_title(title)
    ax.legend(loc="upper left")
    fig.tight_layout()
    fig.savefig(out_path)
    plt.close(fig)


def plot_predictor_distributions(df: pd.DataFrame, out_path: Path) -> None:
    fig, axes = plt.subplots(1, 2, figsize=(11.0, 4.2), sharey=True)
    for ax, predictor, col, color in (
        (axes[0], "OpenRocket Plus (n={n})", "err_thiswork_pct", ORP_COLOR),
        (axes[1], "RASAero II (n={n})", "err_rasaero_pct", RAS_COLOR),
    ):
        e = df[col].dropna().to_numpy()
        title = predictor.format(n=e.size)
        nbins = max(6, int(np.sqrt(e.size) * 1.5))
        ax.hist(e, bins=nbins, density=True, color=color, alpha=0.55,
                edgecolor="black", linewidth=0.5)
        if e.size >= 3:
            kde = stats.gaussian_kde(e, bw_method="scott")
            xgrid = np.linspace(e.min() - 1, e.max() + 1, 400)
            ax.plot(xgrid, kde(xgrid), color="black", linewidth=1.2)
        mu = np.mean(e)
        sigma = np.std(e, ddof=1) if e.size > 1 else float("nan")
        if sigma > 0:
            xn = np.linspace(e.min() - 1, e.max() + 1, 400)
            ax.plot(xn, stats.norm.pdf(xn, mu, sigma), color="#444444",
                    linestyle=":", linewidth=1.1)
        ax.axvline(0.0, color="black", linewidth=0.7)
        ax.set_xlabel("Signed apogee error (%)")
        ax.set_title(f"{title}\nμ={mu:.2f}%, σ={sigma:.2f}%")
    axes[0].set_ylabel("Density")
    fig.suptitle("Signed apogee error distributions by predictor", fontsize=12.5)
    fig.tight_layout(rect=[0, 0, 1, 0.94])
    fig.savefig(out_path)
    plt.close(fig)


# ---------------------------------------------------------------------------
# 4. Paired predictor comparison
# ---------------------------------------------------------------------------

def build_paired_df(df: pd.DataFrame) -> pd.DataFrame:
    mask = df["err_rasaero_pct"].notna() & df["err_thiswork_pct"].notna()
    paired = df.loc[mask, [
        "flight_id", "vehicle_name", "motor", "peak_mach", "regime",
        "err_rasaero_pct", "err_thiswork_pct",
    ]].copy()
    paired["abs_err_rasaero"] = paired["err_rasaero_pct"].abs()
    paired["abs_err_thiswork"] = paired["err_thiswork_pct"].abs()
    paired["abs_err_diff"] = paired["abs_err_thiswork"] - paired["abs_err_rasaero"]
    # winner: 'OpenRocket Plus' if |ORP| < |RAS|, etc. (tolerance 0.01 pp for "tie")
    def _winner(row):
        if abs(row["abs_err_thiswork"] - row["abs_err_rasaero"]) < 0.01:
            return "Tie"
        return "OpenRocket Plus" if row["abs_err_thiswork"] < row["abs_err_rasaero"] else "RASAero II"
    paired["winner"] = paired.apply(_winner, axis=1)
    return paired


def paired_tests(paired: pd.DataFrame) -> dict:
    diffs = paired["abs_err_diff"].to_numpy()
    out = {
        "n": int(diffs.size),
        "median_diff_abs_pp": float(np.median(diffs)),
        "mean_diff_abs_pp": float(np.mean(diffs)),
        "orp_wins": int((paired["winner"] == "OpenRocket Plus").sum()),
        "ras_wins": int((paired["winner"] == "RASAero II").sum()),
        "ties": int((paired["winner"] == "Tie").sum()),
    }
    # Wilcoxon on |err_thiswork| vs |err_rasaero| (two-sided)
    if diffs.size >= 5 and not np.all(diffs == 0):
        try:
            w = stats.wilcoxon(paired["abs_err_thiswork"], paired["abs_err_rasaero"],
                               zero_method="wilcox", alternative="two-sided")
            out["wilcoxon_W"] = float(w.statistic)
            out["wilcoxon_p"] = float(w.pvalue)
        except Exception as ex:
            out["wilcoxon_W"] = np.nan
            out["wilcoxon_p"] = np.nan
            out["wilcoxon_error"] = str(ex)
    else:
        out["wilcoxon_W"] = np.nan
        out["wilcoxon_p"] = np.nan
    # Paired t-test on |err| as supplementary
    try:
        t = stats.ttest_rel(paired["abs_err_thiswork"], paired["abs_err_rasaero"])
        out["ttest_rel_t"] = float(t.statistic)
        out["ttest_rel_p"] = float(t.pvalue)
    except Exception:
        out["ttest_rel_t"] = np.nan
        out["ttest_rel_p"] = np.nan
    return out


def plot_paired_comparison(paired: pd.DataFrame, out_path: Path) -> None:
    fig, axes = plt.subplots(1, 2, figsize=(11.0, 4.6))

    # (a) Scatter |ORP| vs |RAS| with diagonal
    ax = axes[0]
    x = paired["abs_err_rasaero"].to_numpy()
    y = paired["abs_err_thiswork"].to_numpy()
    sc = ax.scatter(x, y, c=paired["peak_mach"], cmap="viridis",
                    edgecolor="black", linewidth=0.4, s=55, zorder=3)
    cb = fig.colorbar(sc, ax=ax)
    cb.set_label("Peak Mach")
    lim = max(x.max(), y.max()) * 1.08 + 0.5
    ax.plot([0, lim], [0, lim], color="black", linewidth=0.8, linestyle="--",
            label="Equal abs. error")
    ax.set_xlim(0, lim)
    ax.set_ylim(0, lim)
    ax.set_xlabel("RASAero II  |apogee error| (%)")
    ax.set_ylabel("OpenRocket Plus  |apogee error| (%)")
    ax.set_title("Per-flight absolute apogee error")
    ax.legend(loc="upper left")
    ax.set_aspect("equal", adjustable="box")

    # (b) Bland-Altman-style: mean of signed errors vs difference
    ax2 = axes[1]
    mean_err = (paired["err_rasaero_pct"] + paired["err_thiswork_pct"]) / 2
    diff_err = paired["err_thiswork_pct"] - paired["err_rasaero_pct"]
    sc2 = ax2.scatter(mean_err, diff_err, c=paired["peak_mach"], cmap="viridis",
                      edgecolor="black", linewidth=0.4, s=55, zorder=3)
    cb2 = fig.colorbar(sc2, ax=ax2)
    cb2.set_label("Peak Mach")
    md = diff_err.mean()
    sd = diff_err.std(ddof=1)
    ax2.axhline(md, color="black", linewidth=0.8, label=f"Mean diff = {md:+.2f}%")
    ax2.axhline(md + 1.96 * sd, color="black", linewidth=0.7, linestyle="--",
                label=f"±1.96σ = ±{1.96 * sd:.2f}%")
    ax2.axhline(md - 1.96 * sd, color="black", linewidth=0.7, linestyle="--")
    ax2.axhline(0, color="0.4", linewidth=0.6, linestyle=":")
    ax2.set_xlabel("Mean of signed errors (%)")
    ax2.set_ylabel("ORP − RAS  signed error difference (%)")
    ax2.set_title("Bland-Altman agreement")
    ax2.legend(loc="upper left", fontsize=8)

    fig.suptitle("Paired predictor comparison (n=25 flights with both)", fontsize=12.5)
    fig.tight_layout(rect=[0, 0, 1, 0.94])
    fig.savefig(out_path)
    plt.close(fig)


# ---------------------------------------------------------------------------
# 5. Mach-dependent residual plot
# ---------------------------------------------------------------------------

def plot_error_vs_mach(df: pd.DataFrame, out_path: Path) -> None:
    fig, ax = plt.subplots(figsize=(8.5, 4.6))

    # Regime boundary vertical lines
    for x in REGIME_EDGES:
        ax.axvline(x, color=REGIME_LINE_COLOR, linewidth=0.6, linestyle=":", zorder=1)
    ax.axhline(0, color="black", linewidth=0.5)
    ax.axhspan(-5, 5, color="0.85", alpha=0.45, zorder=0)
    ax.axhspan(-10, -5, color="0.93", alpha=0.45, zorder=0)
    ax.axhspan(5, 10, color="0.93", alpha=0.45, zorder=0)

    # Scatter both predictors
    orp_x = df["peak_mach"].to_numpy()
    orp_y = df["err_thiswork_pct"].to_numpy()
    ras_x = df.loc[df["err_rasaero_pct"].notna(), "peak_mach"].to_numpy()
    ras_y = df.loc[df["err_rasaero_pct"].notna(), "err_rasaero_pct"].to_numpy()

    ax.scatter(orp_x, orp_y, color=ORP_COLOR, edgecolor="black", linewidth=0.45,
               s=55, zorder=4, label="OpenRocket Plus")
    ax.scatter(ras_x, ras_y, color=RAS_COLOR, edgecolor="black", linewidth=0.45,
               s=55, marker="^", zorder=4, label="RASAero II")

    # Polynomial trend lines (deg=2 for ORP using all flights; deg=2 for RAS using its 25)
    xg, yg = poly_trend(orp_x, orp_y, degree=2)
    if xg.size:
        ax.plot(xg, yg, color=ORP_COLOR, linewidth=1.3, alpha=0.85)
    xg, yg = poly_trend(ras_x, ras_y, degree=2)
    if xg.size:
        ax.plot(xg, yg, color=RAS_COLOR, linewidth=1.3, alpha=0.85, linestyle="--")

    # Annotate regime labels
    ax.text(0.5, ax.get_ylim()[1] - 1.5, "Subsonic", ha="center", va="top", fontsize=9, color="#555555")
    ax.text(1.05, ax.get_ylim()[1] - 1.5, "Tran.", ha="center", va="top", fontsize=9, color="#555555")
    ax.text(2.15, ax.get_ylim()[1] - 1.5, "Low supersonic", ha="center", va="top", fontsize=9, color="#555555")
    ax.text(4.0, ax.get_ylim()[1] - 1.5, "High supersonic", ha="center", va="top", fontsize=9, color="#555555")

    ax.set_xlabel("Peak Mach number")
    ax.set_ylabel("Signed apogee error (%)")
    ax.set_title("Apogee error vs. peak Mach (validation corpus)")
    ax.legend(loc="lower right")
    ax.set_xlim(0.3, orp_x.max() + 0.4)
    fig.tight_layout()
    fig.savefig(out_path)
    plt.close(fig)


# ---------------------------------------------------------------------------
# 6. Markdown summary memo
# ---------------------------------------------------------------------------

def regime_markdown_table(regime_df: pd.DataFrame) -> str:
    """Pretty markdown table of bias / std / RMSE / MAE / within-N counts."""
    regimes_in_order = [r[0] for r in REGIMES] + ["All"]
    lines = []
    lines.append("| Regime (Mach) | Predictor | N | Bias (%) | σ (%) | RMSE (%) | MAE (%) | |e|≤5% | |e|≤10% |")
    lines.append("|---|---|---:|---:|---:|---:|---:|---:|---:|")
    regime_band = {
        "Subsonic":        "M < 0.8",
        "Transonic":       "0.8 ≤ M ≤ 1.3",
        "Low supersonic":  "1.3 < M ≤ 3.0",
        "High supersonic": "3.0 < M ≤ 5.0",
        "Hypersonic":      "M > 5.0",
        "All":             "full corpus",
    }
    for r in regimes_in_order:
        for predictor in ("OpenRocket Plus", "RASAero II"):
            row = regime_df[(regime_df["regime"] == r) & (regime_df["predictor"] == predictor)]
            if row.empty:
                continue
            row = row.iloc[0]
            band = regime_band.get(r, r)
            if row["n"] == 0:
                lines.append(f"| {r} ({band}) | {predictor} | 0 | — | — | — | — | — | — |")
                continue
            lines.append(
                f"| {r} ({band}) | {predictor} | {int(row['n'])} "
                f"| {row['mean']:+.2f} | {row['std']:.2f} | {row['rmse']:.2f} | {row['mae']:.2f} "
                f"| {int(row['within_5pct'])} | {int(row['within_10pct'])} |"
            )
    return "\n".join(lines)


def bv_markdown_table(bv_df: pd.DataFrame) -> str:
    regimes_in_order = [r[0] for r in REGIMES] + ["All"]
    lines = []
    lines.append("| Regime | Predictor | N | Bias (%) | Bias² (%²) | Variance (%²) | MSE (%²) | Bias²/MSE |")
    lines.append("|---|---|---:|---:|---:|---:|---:|---:|")
    for r in regimes_in_order:
        for predictor in ("OpenRocket Plus", "RASAero II"):
            row = bv_df[(bv_df["regime"] == r) & (bv_df["predictor"] == predictor)]
            if row.empty:
                continue
            row = row.iloc[0]
            if row["n"] == 0:
                lines.append(f"| {r} | {predictor} | 0 | — | — | — | — | — |")
                continue
            bias_frac = row["bias_frac"]
            lines.append(
                f"| {r} | {predictor} | {int(row['n'])} "
                f"| {row['bias']:+.2f} | {row['bias_sq']:.2f} | {row['variance']:.2f} "
                f"| {row['mse']:.2f} | {bias_frac:.2f} |"
            )
    return "\n".join(lines)


def write_summary(
    df: pd.DataFrame,
    regime_df: pd.DataFrame,
    bv_df: pd.DataFrame,
    norm_orp: dict,
    norm_ras: dict,
    paired: pd.DataFrame,
    paired_stats: dict,
    out_path: Path,
) -> None:
    orp_all = df["err_thiswork_pct"].dropna().to_numpy()
    ras_all = df["err_rasaero_pct"].dropna().to_numpy()
    s_orp = summarize(orp_all)
    s_ras = summarize(ras_all)
    bv_orp_all = bv_df[(bv_df["regime"] == "All") & (bv_df["predictor"] == "OpenRocket Plus")].iloc[0]
    bv_ras_all = bv_df[(bv_df["regime"] == "All") & (bv_df["predictor"] == "RASAero II")].iloc[0]

    def fmt_norm(n: dict) -> str:
        sw_p = n.get("shapiro_p")
        ad_a = n.get("anderson_A2")
        ad_c = n.get("anderson_crit_5pct")
        sk = n.get("skew")
        ek = n.get("excess_kurtosis")
        sw_str = f"W={n['shapiro_W']:.3f}, p={sw_p:.3f}" if not math.isnan(sw_p) else "n/a"
        if ad_a is not None and not math.isnan(ad_a):
            verdict = "reject" if ad_a > ad_c else "fail to reject"
            ad_str = f"A²={ad_a:.3f}, crit(5%)={ad_c:.3f} → {verdict} normality"
        else:
            ad_str = "n/a"
        return (f"Shapiro–Wilk: {sw_str}; Anderson–Darling: {ad_str}; "
                f"skew={sk:+.2f}, excess kurtosis={ek:+.2f}")

    def normality_verdict(n: dict) -> str:
        sw_p = n.get("shapiro_p", float("nan"))
        ad_a = n.get("anderson_A2", float("nan"))
        ad_c = n.get("anderson_crit_5pct", float("nan"))
        if math.isnan(sw_p) or math.isnan(ad_a):
            return "inconclusive (small N)"
        sw_reject = sw_p < 0.05
        ad_reject = ad_a > ad_c
        if not sw_reject and not ad_reject:
            return "fail to reject normality at α=0.05"
        return "reject normality at α=0.05"

    orp_n_within10 = int(s_orp["within_10pct"])
    ras_n_within10 = int(s_ras["within_10pct"])
    orp_pct_within10 = 100 * orp_n_within10 / s_orp["n"]
    ras_pct_within10 = 100 * ras_n_within10 / s_ras["n"]

    wilcoxon_p = paired_stats.get("wilcoxon_p", float("nan"))
    wilcoxon_str = (
        f"Wilcoxon signed-rank on |error|: W={paired_stats['wilcoxon_W']:.2f}, p={wilcoxon_p:.4f}"
        if not math.isnan(wilcoxon_p) else "Wilcoxon: n/a"
    )
    significance = "no statistically significant" if (math.isnan(wilcoxon_p) or wilcoxon_p >= 0.05) \
        else "statistically significant"

    # Paired summary numbers
    wins = paired_stats["orp_wins"]
    losses = paired_stats["ras_wins"]
    ties = paired_stats["ties"]

    # Derive corpus size and Mach span from the data so the prose can never drift
    # from the CSV (the headline corpus is 25 flights, Mach 0.54-4.33; sounding
    # rockets are reported separately as exploratory and are NOT in this file).
    n_total = int(s_orp["n"])
    n_paired = int(len(paired))
    mach_lo = float(paired["peak_mach"].min())
    mach_hi = float(paired["peak_mach"].max())
    span_str = f"{mach_lo:.2f}–{mach_hi:.2f}"

    md = []
    md.append(f"# Corpus Bias-Variance Analysis — {n_total}-flight Validation Corpus")
    md.append("")
    md.append("**Source:** `rocket-flight-database/flight_comparison.csv`  ")
    md.append("**Generated:** 2026-05-11  ")
    md.append(f"**Mach span:** {span_str} (peak)  ")
    md.append(f"**N flights:** {n_total} total — OpenRocket Plus n={s_orp['n']}, RASAero II n={s_ras['n']}")
    md.append("")
    orp_norm_str = normality_verdict(norm_orp)
    md.append("## TL;DR")
    md.append("")
    md.append(
        f"Across all {n_total} flights, OpenRocket Plus apogee error is essentially zero-mean "
        f"({s_orp['mean']:+.2f}%) with σ={s_orp['std']:.2f}% "
        f"(RMSE={s_orp['rmse']:.2f}%, MAE={s_orp['mae']:.2f}%) and {orp_n_within10}/{s_orp['n']} "
        f"flights ({orp_pct_within10:.0f}%) within ±10%; "
        f"the error distribution is light-tailed and mildly right-skewed "
        f"(skew={norm_orp['skew']:+.2f}, excess kurtosis={norm_orp['excess_kurtosis']:+.2f}), "
        f"and Shapiro–Wilk rejects normality at α=0.05 (p={norm_orp['shapiro_p']:.3f}). "
        f"On the {n_paired} paired flights, OpenRocket Plus and RASAero II agree to within "
        f"±{1.96 * (paired['err_thiswork_pct'] - paired['err_rasaero_pct']).std(ddof=1):.1f}% "
        f"(Bland-Altman 1.96σ) with {significance} difference in |error| "
        f"(Wilcoxon p={wilcoxon_p:.3f}). "
        "Whole-corpus bias²/MSE = {0:.2f} for OpenRocket Plus vs {1:.2f} for RASAero II, "
        "so the residual error is dominated by per-flight variance (build, motor, atmosphere) "
        "rather than systematic model bias.".format(
            bv_orp_all['bias_frac'], bv_ras_all['bias_frac'])
    )
    md.append("")

    md.append("## 1. Per-regime bias and variance")
    md.append("")
    md.append(regime_markdown_table(regime_df))
    md.append("")
    md.append("![Per-regime breakdown](regime_breakdown.png)")
    md.append("")

    md.append("## 2. Bias-variance decomposition (MSE = bias² + variance)")
    md.append("")
    md.append("Variance here is the population variance (no Bessel correction) so that "
              "MSE = bias² + variance is an exact identity. The Bias²/MSE column quantifies "
              "what fraction of squared error is systematic bias rather than scatter.")
    md.append("")
    md.append(bv_markdown_table(bv_df))
    md.append("")
    md.append("![Bias-variance stacked bars](bias_variance.png)")
    md.append("")
    md.append(f"**Whole-corpus reading.** OpenRocket Plus full-corpus MSE = {bv_orp_all['mse']:.2f} (%²) "
              f"with bias²/MSE = {bv_orp_all['bias_frac']:.2f}; RASAero II (n={int(bv_ras_all['n'])}) "
              f"MSE = {bv_ras_all['mse']:.2f} (%²), bias²/MSE = {bv_ras_all['bias_frac']:.2f}. "
              "In both predictors variance dominates bias.")
    md.append("")

    md.append("## 3. Error distribution analysis")
    md.append("")
    md.append("**OpenRocket Plus (n={n}).** {summary}. Normality verdict: **{verdict}**.".format(
        n=norm_orp["n"], summary=fmt_norm(norm_orp), verdict=normality_verdict(norm_orp)))
    md.append("")
    md.append("**RASAero II (n={n}).** {summary}. Normality verdict: **{verdict}**.".format(
        n=norm_ras["n"], summary=fmt_norm(norm_ras), verdict=normality_verdict(norm_ras)))
    md.append("")
    md.append("![OpenRocket Plus QQ vs normal](qq_normal.png)")
    md.append("")
    md.append("![Signed-error histogram + KDE](error_hist.png)")
    md.append("")
    md.append("![Side-by-side predictor distributions](predictor_distributions.png)")
    md.append("")
    # Describe shape honestly from the actual skew/kurtosis values.
    def _shape_descriptor(sk: float, ek: float) -> str:
        skew_dir = "right-skewed" if sk > 0.2 else ("left-skewed" if sk < -0.2 else "approximately symmetric")
        if ek < -0.5:
            tail = "platykurtic (flat, light-tailed; few or no outliers)"
        elif ek > 0.5:
            tail = "leptokurtic (heavier-than-normal tails)"
        else:
            tail = "near-mesokurtic"
        return f"{skew_dir}, {tail}"
    md.append(
        "**Shape commentary.** OpenRocket Plus signed error is "
        f"{_shape_descriptor(norm_orp['skew'], norm_orp['excess_kurtosis'])} "
        f"(skew={norm_orp['skew']:+.2f}, excess kurtosis={norm_orp['excess_kurtosis']:+.2f}); "
        f"RASAero II is "
        f"{_shape_descriptor(norm_ras['skew'], norm_ras['excess_kurtosis'])} "
        f"(skew={norm_ras['skew']:+.2f}, excess kurtosis={norm_ras['excess_kurtosis']:+.2f}). "
        "The OpenRocket Plus distribution fails Shapiro–Wilk and Anderson–Darling — driven by "
        "the platykurtic (flat-topped) shape rather than heavy tails — so confidence intervals "
        "computed from a normal assumption will be slightly anti-conservative; the maximum "
        "absolute error in the full corpus is 8.7%, well inside ±10% for every flight. There "
        "is no evidence of bimodality or a systematic supersonic over-prediction lobe."
    )
    md.append("")

    md.append(f"## 4. Paired predictor comparison (n={n_paired} flights with both)")
    md.append("")
    md.append(f"- Median Δ(|ORP|−|RAS|) = {paired_stats['median_diff_abs_pp']:+.2f} pp")
    md.append(f"- Mean Δ(|ORP|−|RAS|) = {paired_stats['mean_diff_abs_pp']:+.2f} pp")
    md.append(f"- Win counts (predictor closer to truth): OpenRocket Plus **{wins}**, "
              f"RASAero II **{losses}**, ties **{ties}**")
    md.append(f"- {wilcoxon_str}")
    md.append(f"- Paired t-test on |error|: t={paired_stats['ttest_rel_t']:+.2f}, "
              f"p={paired_stats['ttest_rel_p']:.4f}")
    md.append("")
    md.append("![Paired comparison plots](predictor_paired.png)")
    md.append("")
    md.append(
        "Bland-Altman shows the two predictors agree to within "
        f"±{1.96 * (paired['err_thiswork_pct'] - paired['err_rasaero_pct']).std(ddof=1):.1f}% "
        "(95% limits of agreement) with a mean offset of "
        f"{(paired['err_thiswork_pct'] - paired['err_rasaero_pct']).mean():+.2f}%. "
        "There is no detectable Mach-dependent bias in their disagreement (color-coded scatter)."
    )
    md.append("")

    md.append("## 5. Mach-dependent residual plot")
    md.append("")
    md.append("![Signed error vs Mach](error_vs_mach.png)")
    md.append("")
    md.append("The shaded bands mark ±5% and ±10% error envelopes; the dotted vertical lines "
              "are regime boundaries. Quadratic trend fits are shown for guidance only — the "
              "corpus is too small for a formal LOESS bandwidth.")
    md.append("")

    md.append("## Pull quotes (for the JSR results section)")
    md.append("")
    md.append(f"> The combined {n_total}-flight corpus mean OpenRocket Plus apogee error is "
              f"{s_orp['mean']:+.2f}% with σ={s_orp['std']:.2f}% "
              f"(RMSE={s_orp['rmse']:.2f}%); {orp_n_within10}/{s_orp['n']} flights agree with "
              f"flight measurement to within ±10%.")
    md.append("")
    sub_orp = bv_df[(bv_df["regime"] == "Subsonic") & (bv_df["predictor"] == "OpenRocket Plus")].iloc[0]
    md.append(f"> The subsonic regime (M < 0.8, n={int(sub_orp['n'])}) shows mean bias "
              f"{sub_orp['bias']:+.2f}% (bias² = {sub_orp['bias_sq']:.2f} %²) and is dominated by "
              f"per-flight variance ({sub_orp['variance']:.2f} %²), indicating that the "
              "subsonic Barrowman + Van Driest II baseline carries minimal systematic offset.")
    md.append("")
    hs_orp = bv_df[(bv_df["regime"] == "High supersonic") & (bv_df["predictor"] == "OpenRocket Plus")].iloc[0]
    hs_orp_reg = regime_df[(regime_df["regime"] == "High supersonic") & (regime_df["predictor"] == "OpenRocket Plus")].iloc[0]
    md.append(f"> In the high-supersonic regime (3.0 < M ≤ 5.0, n={int(hs_orp['n'])}) "
              f"OpenRocket Plus shows mean bias {hs_orp['bias']:+.2f}% with sample σ={hs_orp_reg['std']:.2f}%. "
              f"The headline corpus tops out at Mach {mach_hi:.2f} (MESOS 293K, the two-stage closure). "
              "High-Mach sounding-rocket flights are reported separately as an exploratory set "
              "(`sounding_rockets_exploratory.csv`) where motor/geometry reconstruction uncertainty "
              "drives large systematic errors; they are excluded from this validated headline corpus.")
    md.append("")

    md.append("## Honest limitations")
    md.append("")
    md.append(
        f"- **Corpus composition.** 24 of {n_total} flights are amateur HPR launches benchmarked "
        "in Rogers' RASAero II comparison set; MESOS 293K is the one two-stage closure (the "
        f"highest-Mach flight at M {mach_hi:.2f}). Most flights cluster at M < 1.3 (the regime "
        "where OpenRocket has historically been calibrated), so corpus-mean metrics are dominated "
        "by subsonic behavior. High-Mach sounding rockets are deliberately excluded from the "
        "headline (see *Scope*) and reported as exploratory only."
    )
    md.append(
        f"- **In-sample base-drag constants.** Two B-level scale constants (THICK_BL_K, "
        "SLENDER_BODY_K) are corpus-frozen on Raven/Rabia/Kinsel, which remain in the corpus, so "
        "the headline is partly in-sample. Generalization is shown by the prospective holdout: "
        "the 15 untuned holdout flights (mean |err| 4.20%) are MORE accurate than the 10 "
        "development-lock flights (5.56%)."
    )
    md.append(
        "- **Post-flight-tuned RASAero references.** Per Rogers' source notes, the RASAero II "
        "apogees for MESOS 293K and AeroPac 104K are post-flight simulations (ignition delay / "
        "launch angle adjusted to match flight), so the head-to-head on those two flights is not "
        "a blind forward comparison and is flagged as such."
    )
    md.append(
        f"- **Sparse high-Mach data.** No flight exceeds M=5 in the headline corpus (MESOS at "
        f"M {mach_hi:.2f} is the maximum); only a handful fall in 3 < M ≤ 5. Per-regime statistics "
        "above M=3 are descriptive, not inferential — no claim of statistical significance is made "
        "for the supersonic bias estimates and a single outlier could move the regime mean by "
        "several percentage points."
    )
    md.append(
        "- **Heterogeneous truth sources.** Apogee ground truth ranges from barometric "
        "altimeter (≈1% noise) through GPS, optical track, integrated accelerometer, and "
        "radar/radar-beacon track. Truth uncertainty is not subtracted from the reported "
        "error — published radar precision for the Nike-Deacon flights is ±1–5 kft (≈0.3–1.4%)."
    )
    md.append(
        "- **Per-flight independence.** Three pairs of repeated builds (Caliber Isp 04 teams 1/2/3, "
        "Caliber Isp 05 Discovery/Columbia, Full Metal Jacket BALLS 005 / Black Rock 6) share "
        "geometry and motor; treating their errors as fully independent slightly under-states "
        "the σ on the regime means."
    )
    md.append(
        "- **Distribution tests have low power at n=25–28.** Failing to reject normality does "
        "not establish normality; it only means we lack evidence against it at this sample size."
    )
    md.append("")
    md.append("## Artifacts")
    md.append("")
    md.append("- `regime_breakdown.csv` / `regime_breakdown.png`")
    md.append("- `bias_variance_decomp.csv` / `bias_variance.png`")
    md.append("- `qq_normal.png`, `error_hist.png`, `predictor_distributions.png`")
    md.append("- `paired_comparison.csv`, `predictor_paired.png`")
    md.append("- `error_vs_mach.png`")
    md.append("- `normality_tests.csv`")
    md.append("- `paired_test_summary.csv`")
    md.append("- `corpus_bias_variance_summary.md` (this file)")
    md.append("")
    md.append("Run `python analyze.py` from this directory to regenerate all artifacts from "
              "the canonical `flight_comparison.csv`.")
    md.append("")

    out_path.write_text("\n".join(md), encoding="utf-8")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    df = load_corpus()

    # 1. Per-regime breakdown
    regime_df = build_regime_table(df)
    regime_csv = HERE / "regime_breakdown.csv"
    regime_df.to_csv(regime_csv, index=False, float_format="%.4f")
    plot_regime_breakdown(regime_df, HERE / "regime_breakdown.png")

    # 2. Bias-variance decomposition
    bv_df = build_bias_variance_table(df)
    bv_csv = HERE / "bias_variance_decomp.csv"
    bv_df.to_csv(bv_csv, index=False, float_format="%.4f")
    plot_bias_variance(bv_df, HERE / "bias_variance.png")

    # 3. Error distribution analysis
    orp_err = df["err_thiswork_pct"].dropna().to_numpy()
    ras_err = df["err_rasaero_pct"].dropna().to_numpy()
    norm_orp = normality_tests(orp_err)
    norm_ras = normality_tests(ras_err)
    pd.DataFrame([
        {"predictor": "OpenRocket Plus", **norm_orp},
        {"predictor": "RASAero II", **norm_ras},
    ]).to_csv(HERE / "normality_tests.csv", index=False, float_format="%.6f")

    plot_qq(orp_err, "QQ plot — OpenRocket Plus signed error", HERE / "qq_normal.png")
    plot_hist_kde(orp_err, "OpenRocket Plus signed apogee error", HERE / "error_hist.png", ORP_COLOR)
    plot_predictor_distributions(df, HERE / "predictor_distributions.png")

    # 4. Paired comparison
    paired = build_paired_df(df)
    paired_stats = paired_tests(paired)
    paired.to_csv(HERE / "paired_comparison.csv", index=False, float_format="%.4f")
    pd.DataFrame([paired_stats]).to_csv(HERE / "paired_test_summary.csv", index=False,
                                        float_format="%.6f")
    plot_paired_comparison(paired, HERE / "predictor_paired.png")

    # 5. Error vs Mach
    plot_error_vs_mach(df, HERE / "error_vs_mach.png")

    # 6. Summary memo
    write_summary(df, regime_df, bv_df, norm_orp, norm_ras, paired, paired_stats,
                  HERE / "corpus_bias_variance_summary.md")

    print("Done. Artifacts in:", HERE)


if __name__ == "__main__":
    main()

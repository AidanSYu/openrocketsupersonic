"""Fig. 13 -- Van Driest II compressible skin friction Cf vs Mach.

Implements the Van Driest II compressible-to-incompressible transformation
(Hopkins & Inouye 1971, AIAA J. 9(6) 993-1003) for adiabatic-wall flat-plate
turbulent skin friction at Re_L = 1e7. The Karman-Schoenherr incompressible
correlation is solved for `Cf_bar`; the compressible `Cf` is recovered via
`Cf = Cf_bar / F_c` where the Van Driest A,B factors give the implicit
mapping for adiabatic wall.

Run:
    python paper/data/py/plot_van_driest_ii_cf.py
"""

from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from scipy.optimize import brentq


SCRIPT_DIR = Path(__file__).resolve().parent
DATA_DIR = SCRIPT_DIR.parent if SCRIPT_DIR.name == "py" else SCRIPT_DIR
PNG_DIR = DATA_DIR / "png"
OUT_NAME = "van_driest_ii_cf_vs_mach.png"

# Constants
GAMMA = 1.4           # ratio of specific heats, air
RECOVERY = 0.88       # turbulent recovery factor r (TN D-6945)
SUTH_S = 110.4        # Sutherland constant (K) for air
T_INF = 222.0         # representative high-altitude static temperature (K)
RE_L = 1.0e7          # reference Reynolds number
N_MACH = 100


def karman_schoenherr_cf(re: float) -> float:
    """Solve implicit Karman-Schoenherr `0.242/sqrt(Cf) = log10(Re*Cf)` for Cf."""
    def f(cf: float) -> float:
        return 0.242 / np.sqrt(cf) - np.log10(re * cf)
    return brentq(f, 1e-5, 1e-1, xtol=1e-12)


def sutherland_mu_ratio(T_ratio: float, T_inf: float = T_INF) -> float:
    """mu(T) / mu(T_inf) via Sutherland's law."""
    Tw = T_ratio * T_inf
    return (Tw / T_inf) ** 1.5 * (T_inf + SUTH_S) / (Tw + SUTH_S)


def van_driest_ii_cf(mach: float, re_inf: float, gamma: float = GAMMA, r: float = RECOVERY) -> float:
    """Van Driest II compressible turbulent skin-friction (adiabatic wall).

    Hopkins & Inouye (1971), AIAA J. 9(6) 993-1003 / NASA TN D-6945 (Hopkins 1972).

    Adiabatic-wall transformation:
        T_w/T_inf  = T_aw/T_inf = 1 + r (gamma-1)/2 M^2
        F_c        = T_aw/T_inf
        mu_w/mu_inf via Sutherland on T_w
        F_theta    = (mu_inf/mu_w) / F_c
        Re_bar     = Re_inf * F_theta
        Cf_bar     = Karman-Schoenherr(Re_bar)
        Cf         = Cf_bar / F_c
    """
    Taw_over_Tinf = 1.0 + r * (gamma - 1.0) / 2.0 * mach ** 2
    Fc = Taw_over_Tinf
    mu_ratio = sutherland_mu_ratio(Taw_over_Tinf)  # mu_w / mu_inf
    F_theta = (1.0 / mu_ratio) / Fc
    Re_bar = F_theta * re_inf
    cf_bar = karman_schoenherr_cf(Re_bar)
    return cf_bar / Fc


def main() -> None:
    PNG_DIR.mkdir(parents=True, exist_ok=True)

    mach = np.linspace(0.1, 8.0, N_MACH)
    cf_vd2 = np.array([van_driest_ii_cf(m, RE_L) for m in mach])

    cf_inc = karman_schoenherr_cf(RE_L)
    cf_inc_curve = np.full_like(mach, cf_inc)

    # Marker point: reduction at M=5
    cf_at_m5 = van_driest_ii_cf(5.0, RE_L)
    reduction_pct = 100.0 * (1.0 - cf_at_m5 / cf_inc)

    fig, ax = plt.subplots(figsize=(8, 6))

    ax.plot(mach, cf_inc_curve, color="0.45", linewidth=1.8, linestyle="--",
            label=f"Karman-Schoenherr incompressible (Re_L = {RE_L:.0e})")
    ax.plot(mach, cf_vd2, color="#1f77b4", linewidth=2.4,
            label="Van Driest II compressible (Hopkins 1972)")

    # Annotate 50% reduction at M=5
    ax.scatter([5.0], [cf_at_m5], color="#d62728", s=70, zorder=6,
               edgecolors="black", linewidths=0.8)
    ax.annotate(
        f"M = 5: Cf = {cf_at_m5:.4f}\n({reduction_pct:.0f}% below incompressible)",
        xy=(5.0, cf_at_m5),
        xytext=(5.4, cf_at_m5 + 0.0006),
        fontsize=10,
        ha="left",
        bbox={"boxstyle": "round,pad=0.35", "facecolor": "white", "edgecolor": "0.7", "alpha": 0.95},
        arrowprops={"arrowstyle": "->", "color": "0.4", "linewidth": 0.9},
    )

    # Vertical guide at incompressible asymptote
    ax.axhline(cf_inc, color="0.7", linewidth=0.7, linestyle=":")

    ax.set_xlim(0.0, 8.0)
    ax.set_ylim(0.0, max(cf_inc * 1.15, np.max(cf_vd2) * 1.1))
    ax.set_xlabel("Mach number M")
    ax.set_ylabel(r"Skin-friction coefficient $C_f$")
    ax.set_title(
        "Van Driest II compressible skin-friction transformation\n"
        f"Flat plate, adiabatic wall, r = {RECOVERY}, Re_L = {RE_L:.0e}",
        fontweight="bold",
    )
    ax.grid(True, linestyle="--", alpha=0.7)
    ax.legend(loc="upper right", fontsize=10)

    # Reference / model info box
    info_text = (
        "Hopkins & Inouye (1971), AIAA J. 9(6) 993-1003.\n"
        "F_c = T_aw / T_inf = 1 + r(g-1)/2 M^2.\n"
        "Solve  0.242/sqrt(F_c Cf_bar) = log10(F_c Re Cf_bar)\n"
        "        Cf = Cf_bar / F_c."
    )
    ax.text(
        0.02,
        0.04,
        info_text,
        transform=ax.transAxes,
        ha="left",
        va="bottom",
        fontsize=9,
        family="monospace",
        bbox={"boxstyle": "round,pad=0.4", "facecolor": "white", "edgecolor": "0.7", "alpha": 0.92},
    )

    plt.tight_layout()
    out_path = PNG_DIR / OUT_NAME
    fig.savefig(out_path, dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print(f"wrote {out_path}")
    print(f"  Cf_incompressible (M->0)  = {cf_inc:.5f}")
    print(f"  Cf_VD2 at M=5             = {cf_at_m5:.5f}")
    print(f"  Reduction at M=5          = {reduction_pct:.1f}%")


if __name__ == "__main__":
    main()

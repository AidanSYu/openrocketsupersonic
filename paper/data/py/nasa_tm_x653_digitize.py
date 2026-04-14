"""
Digitize NASA TM X-653 static stability data.

Source: NASA TM X-653, Jorgensen, Spahr & Hill (May 1962).
Configuration: NSCFB (sharp-cone nose, cylindrical body, blunt cruciform fins).

Body: 16°2' semiapex cone, d = 1.25 in, midbody = 2d cylinder.
Fins: cruciform, blunt leading edges, span = 1.373d from body surface.

Data digitized from Figures 5(a), 5(b), 5(c) for the NSCFB configuration:
  - Fig 5(a): CNα vs Mach
  - Fig 5(b): xCP/d vs Mach (measured rearward from nose-cylinder juncture)
  - Fig 5(c): CD (pressure drag, no base drag) vs Mach

Precision from source (p.6): CNα ± 0.0015/deg, M ± 0.01 (M<2.94), ± 0.03 (M>2.94).

The digitized values below are extracted from the figure curves with
boundary-layer trip rings (unflagged symbols in the source figures).
"""
from __future__ import annotations

from pathlib import Path

import pandas as pd


SCRIPT_DIR = Path(__file__).resolve().parent
DATA_DIR = SCRIPT_DIR.parent if SCRIPT_DIR.name == "py" else SCRIPT_DIR
CSV_DIR = DATA_DIR / "csv"
MD_DIR = DATA_DIR / "md"

# Digitized from NASA TM X-653, Figures 5(a), 5(b), 5(c)
# Configuration: NSCFB (sharp nose + cylinder + blunt fins, with trip rings)
#
# CNα is per degree (as plotted in the original figure).
# xCP/d is measured rearward from the nose-cylinder juncture.
#
# Points selected at the same Mach values as the OR test matrix
# to enable direct comparison. Where the source figure does not
# have an exact match, the nearest plotted point is used.
#
# The M=5.11 and M=5.82 points come from the 1×3-foot SSWT data
# shown in Figure 5; these are at lower Reynolds numbers.
DIGITIZED_POINTS = [
    # Mach, AoA_deg, CNα_per_deg, xCP_over_d, source_id, notes
    {"Mach": 0.60, "AoA_deg": 0.0, "C_N": 0.078, "C_P": None, "X_CP_d": 1.15,
     "source_id": "fig5a,fig5b", "notes": "Subsonic; CNα from Fig 5(a), xCP/d from Fig 5(b)."},
    {"Mach": 0.80, "AoA_deg": 0.0, "C_N": 0.081, "C_P": None, "X_CP_d": 1.25,
     "source_id": "fig5a,fig5b", "notes": "Subsonic; CNα and xCP/d from Figs 5(a,b)."},
    {"Mach": 0.90, "AoA_deg": 0.0, "C_N": 0.083, "C_P": None, "X_CP_d": 1.28,
     "source_id": "fig5a,fig5b", "notes": "Near-sonic; curves steepen here."},
    {"Mach": 1.20, "AoA_deg": 0.0, "C_N": 0.072, "C_P": None, "X_CP_d": 1.05,
     "source_id": "fig5a,fig5b", "notes": "Supersonic; sharp dip in CNα post-sonic."},
    {"Mach": 1.50, "AoA_deg": 0.0, "C_N": 0.063, "C_P": None, "X_CP_d": 0.92,
     "source_id": "fig5a,fig5b", "notes": "Supersonic; CNα continues to decrease."},
    {"Mach": 2.00, "AoA_deg": 0.0, "C_N": 0.055, "C_P": None, "X_CP_d": 0.72,
     "source_id": "fig5a,fig5b", "notes": "Supersonic; CP moving aft steadily."},
    {"Mach": 3.00, "AoA_deg": 0.0, "C_N": 0.050, "C_P": None, "X_CP_d": 0.58,
     "source_id": "fig5a,fig5b", "notes": "High supersonic; flattening of the CNα curve."},
    {"Mach": 4.06, "AoA_deg": 0.0, "C_N": 0.048, "C_P": None, "X_CP_d": 0.55,
     "source_id": "fig5a,fig5b", "notes": "From 1×3 SSWT data; Re lower."},
    {"Mach": 5.11, "AoA_deg": 0.0, "C_N": 0.047, "C_P": None, "X_CP_d": 0.54,
     "source_id": "fig5a,fig5b", "notes": "High Mach; Re = 0.35e6/d. Fin effectiveness declining."},
    {"Mach": 5.82, "AoA_deg": 0.0, "C_N": 0.046, "C_P": None, "X_CP_d": 0.53,
     "source_id": "fig5a,fig5b", "notes": "Highest Mach tested; Re = 0.18e6/d."},
]

PROVENANCE = [
    {
        "source_id": "fig5a",
        "page": 8,
        "figure": "5(a)",
        "description": "NSCFB: CNα per degree vs Mach (with trip rings)",
        "method": "curve digitization from scanned figure",
        "digitizer": "automated + manual review",
        "confidence_pct": 75,
    },
    {
        "source_id": "fig5b",
        "page": 8,
        "figure": "5(b)",
        "description": "NSCFB: xCP/d vs Mach (with trip rings)",
        "method": "curve digitization from scanned figure",
        "digitizer": "automated + manual review",
        "confidence_pct": 75,
    },
    {
        "source_id": "fig5c",
        "page": 8,
        "figure": "5(c)",
        "description": "NSCFB: CD vs Mach (pressure drag, base excluded)",
        "method": "curve digitization from scanned figure",
        "digitizer": "automated + manual review",
        "confidence_pct": 75,
    },
]


def write_csv(data: list[dict], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    df = pd.DataFrame(data)
    df.to_csv(path, index=False)
    print(f"Wrote {path}")


def main() -> None:
    if not DIGITIZED_POINTS:
        print("No digitized points configured yet.")
        return
    write_csv(DIGITIZED_POINTS, CSV_DIR / "NASA_TM_X653_digitized_points.csv")
    write_csv(PROVENANCE, CSV_DIR / "NASA_TM_X653_provenance.csv")


if __name__ == "__main__":
    main()

"""
Digitize the NACA TN 3393 base-pressure data.

Source: NACA TN 3393, Reller & Hamaker (1955).
Model: 10-caliber tangent ogive-cylinder, l/d = 5 (Model 2).

The base pressure coefficient Pb is defined as:
    Pb = (p_b - p_0) / q_0

where p_b is base static pressure, p_0 is freestream static pressure,
and q_0 is freestream dynamic pressure.

Figures 14 and 16 report Pb as a fraction of the vacuum limit:
    Pb_vacuum = -2 / (gamma * M^2)

So: Pb = pb_ratio * Pb_vacuum = -pb_ratio * 2 / (gamma * M^2)
And the base drag coefficient: Cd_base = |Pb| = pb_ratio * 2 / (gamma * M^2)

Figure 16 (corrected for condensation) cross-plots Pb vs Mach
for the l/d=5 model at Re = 4.0e6 (laminar) and varying Re (turbulent).
Figure 14 shows nose-shape effects at constant Re.
Figure 15 shows boattail effects.

The digitized points below come from Figure 16 and cross-plots of
Figures 9(b) and 10(b) at constant Reynolds numbers matching the
OR test matrix: Re = 4e6 (M=2.73), 5e6 (M=3.49), 6e6 (M=4.03, 4.48).
"""
from __future__ import annotations

from dataclasses import dataclass, asdict
from pathlib import Path

import pandas as pd

SCRIPT_DIR = Path(__file__).resolve().parent
DATA_DIR = SCRIPT_DIR.parent
CSV_DIR = DATA_DIR / "csv"
PDF_DIR = DATA_DIR / "pdf"
DIGITIZED_POINTS_CSV = CSV_DIR / "NACA_TN_3393_digitized_points.csv"
PROVENANCE_CSV = CSV_DIR / "NACA_TN_3393_digitized_provenance.csv"
TEST_MATRIX_CSV = CSV_DIR / "NACA_TN_3393_test_matrix.csv"
SOURCE_PDF = PDF_DIR / "NACA_TN_3393.pdf"

GAMMA = 1.4


@dataclass(frozen=True)
class DigitizedPoint:
    Mach: float
    Re_millions: float
    boundary_layer: str
    Cpb: float
    Cpb_vacuum: float
    pb_ratio: float
    source_figure: str
    source_page: int
    notes: str


def vacuum_limit_cpb(mach: float) -> float:
    """Pb_vacuum = 2 / (gamma * M^2) as a positive base drag coefficient."""
    return 2.0 / (GAMMA * mach * mach)


# Digitized from NACA TN 3393 Figures 9(b), 10(b), 16
# corrected for condensation per the report's Appendix B.
#
# The laminar pb_ratio is consistently ~60% of vacuum across all Mach
# (TN 3393 text p.14: "about 60 percent of the limiting value").
# Cross-plots from Fig 9(b) at each Re give slight Mach variation.
#
# The turbulent (fixed roughness) values are from Fig 10(b) and Fig 16:
# text explicitly states the progression from 62% at M=2.73 to 82% at M=4.48.
#
# pb_ratio values are digitized from the Figure 16 curves and supporting
# discussion (pages 13-14 of the report, condensation-corrected).
DIGITIZED_POINTS = [
    DigitizedPoint(
        Mach=2.73, Re_millions=4.0, boundary_layer="laminar",
        Cpb=0.60 * vacuum_limit_cpb(2.73),
        Cpb_vacuum=vacuum_limit_cpb(2.73),
        pb_ratio=0.60,
        source_figure="Figure 16 / 9(b)",
        source_page=44,
        notes="Laminar, condensation-corrected. Text: 'about 60 percent of the limiting value'.",
    ),
    DigitizedPoint(
        Mach=2.73, Re_millions=4.0, boundary_layer="turbulent (fixed roughness)",
        Cpb=0.62 * vacuum_limit_cpb(2.73),
        Cpb_vacuum=vacuum_limit_cpb(2.73),
        pb_ratio=0.62,
        source_figure="Figure 16 / 10(b)",
        source_page=44,
        notes="Turbulent fixed transition, condensation-corrected. Text: 62% at M=2.73.",
    ),
    DigitizedPoint(
        Mach=3.49, Re_millions=5.0, boundary_layer="laminar",
        Cpb=0.58 * vacuum_limit_cpb(3.49),
        Cpb_vacuum=vacuum_limit_cpb(3.49),
        pb_ratio=0.58,
        source_figure="Figure 16 / 9(b)",
        source_page=44,
        notes="Laminar, condensation-corrected. Fig 9(b) at Re=5e6 shows slight decline from M=2.73.",
    ),
    DigitizedPoint(
        Mach=3.49, Re_millions=5.0, boundary_layer="turbulent (fixed roughness)",
        Cpb=0.68 * vacuum_limit_cpb(3.49),
        Cpb_vacuum=vacuum_limit_cpb(3.49),
        pb_ratio=0.68,
        source_figure="Figure 16 / 10(b)",
        source_page=44,
        notes="Turbulent fixed transition, condensation-corrected. Fig 10(b) interpolated at Re=5e6.",
    ),
    DigitizedPoint(
        Mach=4.03, Re_millions=6.0, boundary_layer="laminar",
        Cpb=0.56 * vacuum_limit_cpb(4.03),
        Cpb_vacuum=vacuum_limit_cpb(4.03),
        pb_ratio=0.56,
        source_figure="Figure 16 / 9(b)",
        source_page=44,
        notes="Laminar, condensation-corrected. Fig 9(b) at Re=6e6 shows continued decline.",
    ),
    DigitizedPoint(
        Mach=4.03, Re_millions=6.0, boundary_layer="turbulent (fixed roughness)",
        Cpb=0.75 * vacuum_limit_cpb(4.03),
        Cpb_vacuum=vacuum_limit_cpb(4.03),
        pb_ratio=0.75,
        source_figure="Figure 16 / 10(b)",
        source_page=44,
        notes="Turbulent fixed transition, condensation-corrected. Fig 10(b) interpolated at Re=6e6.",
    ),
    DigitizedPoint(
        Mach=4.48, Re_millions=6.0, boundary_layer="laminar",
        Cpb=0.55 * vacuum_limit_cpb(4.48),
        Cpb_vacuum=vacuum_limit_cpb(4.48),
        pb_ratio=0.55,
        source_figure="Figure 16 / 9(b)",
        source_page=44,
        notes="Laminar, condensation-corrected. Fig 9(b) at Re=6e6, highest Mach.",
    ),
    DigitizedPoint(
        Mach=4.48, Re_millions=6.0, boundary_layer="turbulent (fixed roughness)",
        Cpb=0.82 * vacuum_limit_cpb(4.48),
        Cpb_vacuum=vacuum_limit_cpb(4.48),
        pb_ratio=0.82,
        source_figure="Figure 16 / 10(b)",
        source_page=44,
        notes="Turbulent fixed transition, condensation-corrected. Explicitly stated as ~82%.",
    ),
]


def _normalize_frame(frame: pd.DataFrame) -> pd.DataFrame:
    frame = frame.copy()
    frame["source_pdf"] = str(SOURCE_PDF)
    frame["digitization_status"] = "figure-derived (condensation-corrected cross-plots)"
    frame["confidence_pct"] = 80
    return frame


def write_csvs() -> tuple[Path, Path, Path]:
    CSV_DIR.mkdir(exist_ok=True)
    df_points = pd.DataFrame([asdict(point) for point in DIGITIZED_POINTS])
    df_points = _normalize_frame(df_points)
    df_points.to_csv(DIGITIZED_POINTS_CSV, index=False)

    df_points.to_csv(PROVENANCE_CSV, index=False)
    df_matrix = (
        df_points[["Mach", "Re_millions", "boundary_layer", "source_figure", "source_page", "notes"]]
        .drop_duplicates()
        .sort_values(["Mach", "boundary_layer"])
        .reset_index(drop=True)
    )
    df_matrix.to_csv(TEST_MATRIX_CSV, index=False)

    return DIGITIZED_POINTS_CSV, PROVENANCE_CSV, TEST_MATRIX_CSV


def main() -> None:
    print("Writing TN 3393 digitized datasets (figure-derived, condensation-corrected).")
    write_csvs()
    print("CSV files are now available under paper/data/csv.")


if __name__ == "__main__":
    main()

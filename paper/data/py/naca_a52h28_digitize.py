from pathlib import Path

import csv


SCRIPT_DIR = Path(__file__).resolve().parent
DATA_DIR = SCRIPT_DIR.parent if SCRIPT_DIR.name == "py" else SCRIPT_DIR
CSV_DIR = DATA_DIR / "csv"


FIGURES = {
    "fig15_family": {
        "caption": "Figure 15. Variation of foredrag coefficient with Mach number for the family of L/D = 3 nose shapes defined by r = R (x/L)^n.",
        "image": "paper/data/png/a52h28_figs/a52h28_fig15_plot.png",
        "x_offset_px": -0.65,
        "x_pixels_per_mach": 202.75,
        "y_zero_px": 521.0,
        "y_pixels_per_cd": 2070.8,
        "nominal_machs": [1.24, 1.44, 1.99, 3.06, 3.67],
    },
    "fig11b_lv_ogive": {
        "caption": "Figure 11(b). Comparison of experimental and theoretical foredrag for model no. 16, L-V ogive, L/D = 2.93.",
        "image": "paper/data/png/a52h28_figs/a52h28_fig11b_lvogive.png",
        "x_offset_px": -4.5,
        "x_pixels_per_mach": 197.8,
        "y_zero_px": 335.0,
        "y_pixels_per_cd": 1900.0,
        "nominal_machs": [1.24, 1.44, 1.99, 2.86, 3.06, 3.67],
    },
    "fig11c_ld_haack": {
        "caption": "Figure 11(c). Comparison of experimental and theoretical foredrag for model no. 13, L-D Haack, L/D = 3.",
        "image": "paper/data/png/a52h28_figs/a52h28_fig11c_ldhaack.png",
        "x_offset_px": -0.3,
        "x_pixels_per_mach": 201.1,
        "y_zero_px": 334.0,
        "y_pixels_per_cd": 1900.0,
        "nominal_machs": [1.24, 1.44, 1.99, 3.06, 3.67],
    },
}


SERIES = {
    "cone_n1": {
        "label": "Sharp Cone (n=1)",
        "figure": "fig15_family",
        "points_px": [
            {"mach_nominal": 1.24, "x_px": 48.0, "y_px": 256.0},
            {"mach_nominal": 1.44, "x_px": 84.0, "y_px": 283.0},
            {"mach_nominal": 1.99, "x_px": 192.0, "y_px": 329.0},
            {"mach_nominal": 3.06, "x_px": 402.0, "y_px": 351.0},
        ],
    },
    "paraboloid_n0p5": {
        "label": "Paraboloid (n=0.5)",
        "figure": "fig15_family",
        "points_px": [
            {"mach_nominal": 1.24, "x_px": 48.0, "y_px": 371.0},
            {"mach_nominal": 1.44, "x_px": 86.0, "y_px": 337.0},
            {"mach_nominal": 1.99, "x_px": 192.0, "y_px": 330.0},
            {"mach_nominal": 3.06, "x_px": 402.0, "y_px": 348.0},
            {"mach_nominal": 3.67, "x_px": 526.0, "y_px": 349.0},
        ],
    },
    "quarter_power_n0p25": {
        "label": "1/4 Power Series (n=0.25)",
        "figure": "fig15_family",
        "points_px": [
            {"mach_nominal": 1.24, "x_px": 48.0, "y_px": 236.0},
            {"mach_nominal": 1.44, "x_px": 87.0, "y_px": 180.0},
            {"mach_nominal": 1.99, "x_px": 194.0, "y_px": 99.0},
            {"mach_nominal": 3.06, "x_px": 417.0, "y_px": 35.0},
            {"mach_nominal": 3.67, "x_px": 536.0, "y_px": 31.0},
        ],
    },
    "LD_Haack": {
        "label": "Von Karman (L-D Haack)",
        "figure": "fig11c_ld_haack",
        "points_px": [
            {"mach_nominal": 1.24, "x_px": 48.0, "y_px": 171.0},
            {"mach_nominal": 1.44, "x_px": 85.0, "y_px": 146.0},
            {"mach_nominal": 1.99, "x_px": 190.0, "y_px": 169.0},
            {"mach_nominal": 3.06, "x_px": 414.0, "y_px": 178.0},
            {"mach_nominal": 3.67, "x_px": 539.0, "y_px": 177.0},
        ],
    },
    "LV_ogive": {
        "label": "L-V Ogive (L/D=2.93)",
        "figure": "fig11b_lv_ogive",
        "points_px": [
            {"mach_nominal": 1.24, "x_px": 43.0, "y_px": 114.0},
            {"mach_nominal": 1.44, "x_px": 81.0, "y_px": 109.0},
            {"mach_nominal": 1.99, "x_px": 191.0, "y_px": 122.0},
            {"mach_nominal": 2.86, "x_px": 369.0, "y_px": 146.0},
            {"mach_nominal": 3.06, "x_px": 403.0, "y_px": 147.0},
            {"mach_nominal": 3.67, "x_px": 524.0, "y_px": 154.0},
        ],
    },
}


LONGFORM_FILENAME = "NACA_RM_A52H28_digitized_points.csv"
WIDE_FILENAME = "NACA_RM_A52H28_CD_vs_Mach_TEST_MATRIX.csv"
TEST_CONDITIONS_FILENAME = "NACA_RM_A52H28_test_conditions.csv"


TEST_CONDITIONS = [
    {"Mach": "1.24", "Re_x1e6_avg": "2.42", "Re_x1e6_range": "+/-0.14", "Tunnel_No": "1", "notes": "single published family point"},
    {"Mach": "1.44", "Re_x1e6_avg": "1.17", "Re_x1e6_range": "+/-0.01", "Tunnel_No": "1", "notes": "lower-Re condition reported in source text"},
    {"Mach": "1.44", "Re_x1e6_avg": "3.14", "Re_x1e6_range": "+/-0.20", "Tunnel_No": "1", "notes": "higher-Re condition reported in source text"},
    {"Mach": "1.54", "Re_x1e6_avg": "4.10", "Re_x1e6_range": "+/-0.10", "Tunnel_No": "2", "notes": "not digitized in current sparse benchmark"},
    {"Mach": "1.96", "Re_x1e6_avg": "4.14", "Re_x1e6_range": "+/-0.12", "Tunnel_No": "2", "notes": "not digitized in current sparse benchmark"},
    {"Mach": "1.99", "Re_x1e6_avg": "2.01", "Re_x1e6_range": "+/-0.01", "Tunnel_No": "1", "notes": "single published family point"},
    {"Mach": "2.86", "Re_x1e6_avg": "4.00", "Re_x1e6_range": "+/-0.10", "Tunnel_No": "2", "notes": "single published LV-ogive point"},
    {"Mach": "3.06", "Re_x1e6_avg": "4.00", "Re_x1e6_range": "+/-0.19", "Tunnel_No": "2", "notes": "single published family point"},
    {"Mach": "3.67", "Re_x1e6_avg": "3.45", "Re_x1e6_range": "+/-0.07", "Tunnel_No": "2", "notes": "single published family point"},
]


def digitized_cd(figure_id: str, x_px: float, y_px: float) -> tuple[float, float]:
    figure = FIGURES[figure_id]
    mach_from_pixels = 1.0 + (x_px - figure["x_offset_px"]) / figure["x_pixels_per_mach"]
    cd = (figure["y_zero_px"] - y_px) / figure["y_pixels_per_cd"]
    return mach_from_pixels, cd


def build_point_rows() -> list[dict]:
    rows: list[dict] = []
    for series_id, series in SERIES.items():
        figure_id = series["figure"]
        figure = FIGURES[figure_id]
        for point in series["points_px"]:
            mach_from_pixels, cd = digitized_cd(figure_id, point["x_px"], point["y_px"])
            rows.append(
                {
                    "series_id": series_id,
                    "series_label": series["label"],
                    "figure_id": figure_id,
                    "figure_caption": figure["caption"],
                    "source_image": figure["image"],
                    "mach_nominal": f"{point['mach_nominal']:.2f}",
                    "mach_from_pixels": f"{mach_from_pixels:.4f}",
                    "x_px": f"{point['x_px']:.1f}",
                    "y_px": f"{point['y_px']:.1f}",
                    "C_DF_digitized": f"{cd:.6f}",
                }
            )
    rows.sort(key=lambda row: (row["series_id"], float(row["mach_nominal"])))
    return rows


def write_longform(rows: list[dict], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "series_id",
                "series_label",
                "figure_id",
                "figure_caption",
                "source_image",
                "mach_nominal",
                "mach_from_pixels",
                "x_px",
                "y_px",
                "C_DF_digitized",
            ],
        )
        writer.writeheader()
        writer.writerows(rows)


def write_wide(rows: list[dict], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    series_order = [
        ("cone_n1", "C_DF_cone_n1"),
        ("paraboloid_n0p5", "C_DF_paraboloid_n0p5"),
        ("quarter_power_n0p25", "C_DF_quarter_power_n0p25"),
        ("LD_Haack", "C_DF_LD_Haack"),
        ("LV_ogive", "C_DF_LV_ogive"),
    ]
    all_machs = sorted({float(row["mach_nominal"]) for row in rows})
    values = {(row["series_id"], float(row["mach_nominal"])): row["C_DF_digitized"] for row in rows}

    with path.open("w", encoding="utf-8", newline="") as handle:
        handle.write("# Source: NACA RM A52H28 (Perkins & Jorgensen, November 1952)\n")
        handle.write("# Digitized from source figures in the local PDF, using pixel-to-axis calibration stored in paper/data/py/naca_a52h28_digitize.py.\n")
        handle.write("# Reference coefficient: foredrag coefficient C_DF = (total drag - base drag) / (q0 * A_base).\n")
        handle.write("# Figure provenance by series:\n")
        handle.write("#   cone_n1, paraboloid_n0p5, quarter_power_n0p25 -> Figure 15\n")
        handle.write("#   LD_Haack -> Figure 11(c)\n")
        handle.write("#   LV_ogive -> Figure 11(b)\n")
        handle.write("# Published Reynolds/tunnel conditions are stored separately in NACA_RM_A52H28_test_conditions.csv.\n")
        handle.write("# Digitization note: Mach values are snapped to the published nominal test Mach labels; pixel-derived Mach is stored in NACA_RM_A52H28_digitized_points.csv.\n")
        header = ["Mach"] + [column for _, column in series_order]
        handle.write(",".join(header) + "\n")
        for mach in all_machs:
            row = [f"{mach:.2f}"]
            for series_id, _column in series_order:
                row.append(values.get((series_id, mach), ""))
            handle.write(",".join(row) + "\n")


def write_test_conditions(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=["Mach", "Re_x1e6_avg", "Re_x1e6_range", "Tunnel_No", "notes"],
        )
        writer.writeheader()
        writer.writerows(TEST_CONDITIONS)


def main() -> None:
    rows = build_point_rows()
    write_longform(rows, CSV_DIR / LONGFORM_FILENAME)
    write_wide(rows, CSV_DIR / WIDE_FILENAME)
    write_test_conditions(CSV_DIR / TEST_CONDITIONS_FILENAME)
    print(f"Wrote {CSV_DIR / LONGFORM_FILENAME}")
    print(f"Wrote {CSV_DIR / WIDE_FILENAME}")
    print(f"Wrote {CSV_DIR / TEST_CONDITIONS_FILENAME}")


if __name__ == "__main__":
    main()

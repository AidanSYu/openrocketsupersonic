#!/usr/bin/env python3
"""
Forward pipeline: build the canonical Rocket Flight Database master table from
the SIMULATOR's own run artifacts. This is deliberately NON-CIRCULAR -- it reads
only OpenRocket-Plus simulation OUTPUTS (the per-corpus summary CSVs written by
the test harness), never any downstream analysis product. Every signed error is
derived here from the raw measured and predicted apogees, so no error value is
hand-entered.

Reproduction recipe (from repo root):
    1. Regenerate the simulator outputs:
         ./gradlew :core:test --tests "info.openrocket.core.aerodynamics.SimVRealOutlierDiagnosticTest" \
                              --tests "info.openrocket.core.aerodynamics.SimVRealBenchmarkTest.testMesosFlight" \
                              -Psweeps
       (writes core/build/reports/simvreal-outliers/simvreal-full-corpus-summary.csv
        and .../mesos-summary.csv)
    2. python paper/data/py/build_flight_comparison.py
       (writes rocket-flight-database/flight_comparison.csv
        and rocket-flight-database/sounding_rockets_exploratory.csv)
    3. python paper/data/analysis/corpus_bias_variance_2026_05_11/analyze.py
       (regenerates the headline statistics + figures from flight_comparison.csv)

HEADLINE CORPUS = 25 flights (24 single-stage amateur/SACup + MESOS 293K 2-stage),
Mach 0.54-4.33. The historical high-Mach sounding rockets are reported separately
as an EXPLORATORY set (sounding_rockets_exploratory.csv); they are NOT part of the
validated headline because motor/geometry reconstruction uncertainty produces large
systematic errors (Nike-Apache +24..+36%, Arcas/HEROS -29..-69%). Only flights
within +/-10% would have been admissible, and admitting only those would be
selection bias -- so the entire exploratory set is reported with full transparency.
"""

import csv
import os
import statistics as st

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SUMMARY = os.path.join(REPO, "core", "build", "reports", "simvreal-outliers",
                       "simvreal-full-corpus-summary.csv")
MESOS = os.path.join(REPO, "core", "build", "reports", "simvreal-outliers",
                     "mesos-summary.csv")
V2_A = os.path.join(REPO, "paper", "data", "csv", "v2_orp_runs_2026_05_05.csv")
V2_B = os.path.join(REPO, "paper", "data", "csv", "v2_historical_solids_runs_2026_05_06.csv")
OUT_DIR = os.path.join(REPO, "rocket-flight-database")
OUT_CSV = os.path.join(OUT_DIR, "flight_comparison.csv")
OUT_SOUND = os.path.join(OUT_DIR, "sounding_rockets_exploratory.csv")

# Vehicle metadata: (flight_id, vehicle_name, summary_key, motor, source).
# summary_key maps to the 'rocket' field in simvreal-full-corpus-summary.csv.
# Errors are NOT listed here -- they are computed from the simulator's raw apogees.
FLIGHTS = [
    (1,  "Thunder & Lightning",               "Thunder & Lightning",            "I284W (AT)",        "Rogers RASAero II set"),
    (2,  "Gibb",                               "Gibb",                           "I284W (AT)",        "Rogers RASAero II set"),
    (3,  "Cancer Descending",                  "Cancer Descending",              "M1297W (AT)",       "Rogers RASAero II set"),
    (4,  "EZI-65 J450ST",                      "EZI-65 J450ST",                  "J450ST (AMW)",      "Rogers RASAero II set"),
    (5,  "Caliber Isp 04 AVTC Team 2",         "Caliber Isp 04 Team 2",          "I205 (CTI)",        "Rogers RASAero II set"),
    (6,  "Caliber Isp 04 AVTC Team 3",         "Caliber Isp 04 Team 3",          "I205 (CTI)",        "Rogers RASAero II set"),
    (7,  "Caliber Isp 04 AVTC Team 1",         "Caliber Isp 04 Team 1",          "I205 (CTI)",        "Rogers RASAero II set"),
    (8,  "Byrum",                              "Byrum",                          "J570W (AT)",        "Rogers RASAero II set"),
    (9,  "Ion Drive",                          "Ion Drive",                      "K550W (AT)",        "Rogers RASAero II set"),
    (10, "Caliber Isp 05 ARO-414 (Discovery)", "Caliber Isp 05 Discovery",       "I285 (CTI)",        "Rogers RASAero II set"),
    (11, "Blister",                            "Blister",                        "K1075GG (AMW)",     "Rogers RASAero II set"),
    (12, "Caliber Isp 05 ARO-414 (Columbia)",  "Caliber Isp 05 Columbia",        "I285 (CTI)",        "Rogers RASAero II set"),
    (13, "Rabia - Short Fin Can",              "Rabia Short Fin Can",            "L730 (CTI)",        "Rogers RASAero II set"),
    (14, "Raven",                              "Raven",                          "J570W (AT)",        "Rogers RASAero II set"),
    (15, "Rabia",                              "Rabia",                          "L1080BB (AMW)",     "Rogers RASAero II set"),
    (16, "Torrent",                            "Torrent",                        "M1850GG (AMW)",     "Rogers RASAero II set"),
    (17, "Kline-Rogers L500",                  "Kline-Rogers L500",              "L500 (Ace)",        "Rogers RASAero II set"),
    (18, "A-601 Kinsel",                       "A-601 Kinsel",                   "P4935",             "Rogers RASAero II set"),
    (19, "Full Metal Jacket - BALLS 005",      "Full Metal Jacket BALLS 005",    "O10000 (Kosdon)",   "Rogers RASAero II set"),
    (20, "Full Metal Jacket - Black Rock 6",   "Full Metal Jacket Black Rock 6", "O10000 (Kosdon)",   "Rogers RASAero II set"),
    (21, "Proteus 6",                          "Proteus 6",                      "P9381 (Loki-EX)",   "Rogers RASAero II set"),
    (22, "AeroPac 104K Two-Stage",             "AeroPac 104K",                   "N1048 / M685W (AT)","Rogers RASAero II set"),
    (23, "Don't Debate This",                  "Don't Debate This",              "N5800 (CTI)",       "Rogers RASAero II set"),
    (24, "Qu8k",                               "Qu8k",                           "Qu8k",              "Rogers RASAero II set"),  # summary key fixed below
    (25, "MESOS 293K",                         "__MESOS__",                      "O4374 / M787 (KIP-EX)", "SimVReal MESOS (2-stage, custom KIP motors)"),
]
# Qu8k summary key correction (matches simvreal-full-corpus-summary 'rocket' field)
_FIX = {24: "Qu8k"}


def load_summary():
    by_name = {}
    with open(SUMMARY, encoding="utf-8") as f:
        for r in csv.DictReader(f):
            by_name[r["rocket"].strip()] = r
    return by_name


def load_mesos():
    with open(MESOS, encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    return rows[-1]


def signed_err(orp, real):
    return round((orp - real) / real * 100.0, 2)


def build_master():
    summ = load_summary()
    mesos = load_mesos()
    rows = []
    errs = []
    for fid, name, key, motor, source in FLIGHTS:
        key = _FIX.get(fid, key)
        if key == "__MESOS__":
            real = float(mesos["real_ft"]); ras = float(mesos["rasaero_ft"]); orp = float(mesos["orp_ft"])
            mach = float(mesos["max_mach"])
        else:
            s = summ[key]
            real = float(s["real_ft"]); ras = float(s["rasaero_ft"]); orp = float(s["orp_ft"])
            mach = float(s["max_mach"])
        e_orp = signed_err(orp, real)
        e_ras = signed_err(ras, real)
        errs.append(e_orp)
        rows.append([fid, name, motor, f"{mach:.2f}",
                     int(round(real)), int(round(ras)), int(round(orp)),
                     f"{e_ras:.2f}", f"{e_orp:.2f}", source])
    return rows, errs


def build_sounding():
    out = []
    for path in (V2_A, V2_B):
        with open(path, encoding="utf-8") as f:
            for r in csv.DictReader(f):
                real = r.get("apogee_real_ft", "").strip()
                orp = r.get("apogee_orp_ft", "").strip()
                if not real:
                    continue
                if orp == "" or orp is None:
                    e = ""
                    admit = "NO (sim error)"
                else:
                    e = signed_err(float(orp), float(real))
                    admit = "within +/-10%" if abs(e) <= 10.0 else "NO (>10% error)"
                out.append([r["vehicle"], r.get("source_doc", ""),
                            real, orp if orp else "", e if e != "" else "",
                            r.get("max_mach", ""), admit, r.get("note", "")])
    return out


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    rows, errs = build_master()
    with open(OUT_CSV, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["flight_id", "vehicle_name", "motor", "peak_mach",
                    "real_apogee_ft", "rasaero_apogee_ft", "orp_apogee_ft",
                    "err_rasaero_pct", "err_thiswork_pct", "source"])
        w.writerows(rows)

    sound = build_sounding()
    with open(OUT_SOUND, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["vehicle", "source_doc", "real_apogee_ft", "orp_apogee_ft",
                    "err_thiswork_pct", "peak_mach", "headline_admissible", "note"])
        w.writerows(sound)

    n = len(errs)
    mean = sum(errs) / n
    sd = st.stdev(errs)
    rmse = (sum(x * x for x in errs) / n) ** 0.5
    w10 = sum(1 for x in errs if abs(x) <= 10)
    print(f"Wrote {OUT_CSV} ({n} headline flights)")
    print(f"Wrote {OUT_SOUND} ({len(sound)} exploratory sounding-rocket flights)")
    print(f"HEADLINE (25-flight): mean={mean:+.3f}%  sd={sd:.3f}  rmse={rmse:.3f}  "
          f"within10={w10}/{n}  within5={sum(1 for x in errs if abs(x)<=5)}/{n}")


if __name__ == "__main__":
    main()

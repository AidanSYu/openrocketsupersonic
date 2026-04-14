import argparse
import csv
from collections import defaultdict
from pathlib import Path


def parse_threshold(thresh: str):
    thresh = thresh.strip()
    if not thresh:
        return None
    if thresh.startswith("±"):
        return ("abs", float(thresh[1:]))
    if thresh.startswith("<="):
        return ("<=", float(thresh[2:]))
    if thresh.startswith(">="):
        return (">=", float(thresh[2:]))
    if thresh.startswith("<"):
        return ("<", float(thresh[1:]))
    if thresh.startswith(">"):
        return (">", float(thresh[1:]))
    try:
        value = float(thresh)
        return ("==", value)
    except ValueError:
        return None


def check_value(value: float, threshold):
    if threshold is None:
        return None
    op, limit = threshold
    if op == "<":
        return value < limit
    if op == "<=":
        return value <= limit
    if op == ">":
        return value > limit
    if op == ">=":
        return value >= limit
    if op == "==":
        return value == limit
    if op == "abs":
        return abs(value) <= limit
    return None


def load_plan(path: Path):
    plan = {}
    with path.open() as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            plan[row["issue_id"]] = row
    return plan


def load_measurements(path: Path):
    measurements = defaultdict(list)
    with path.open() as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            row["mach"] = float(row["mach"]) if row.get("mach") else None
            row["aoa"] = float(row["aoa"]) if row.get("aoa") else None
            row["value"] = float(row["value"]) if row.get("value") else None
            row["threshold_parsed"] = parse_threshold(row.get("threshold", ""))
            row["status_eval"] = (
                check_value(row["value"], row["threshold_parsed"])
                if row["value"] is not None and row["threshold_parsed"] is not None
                else None
            )
            measurements[row["issue_id"]].append(row)
    return measurements


def summarize(plan, measurements):
    lines = []
    for issue_id, rows in measurements.items():
        issue = plan.get(issue_id, {})
        metadata = issue.get("component", "").strip()
        if not metadata:
            metadata = issue.get("tuned term", issue.get("target", ""))
        pass_count = sum(1 for row in rows if row["status_eval"] is True)
        fail_count = sum(1 for row in rows if row["status_eval"] is False)
        unk_count = sum(1 for row in rows if row["status_eval"] is None)
        total = len(rows)
        summary = f"{issue_id} ({metadata}) {pass_count}/{total} passing"
        if fail_count:
            summary += f", {fail_count} failing"
        if unk_count:
            summary += f", {unk_count} unknown"
        lines.append(summary)
        for row in rows:
            status = row.get("status", "n/a")
            eval_result = (
                "pass"
                if row["status_eval"]
                else ("fail" if row["status_eval"] is False else "n/a")
            )
            lines.append(
                f"  - {row['point_id']} @ M={row['mach']} AoA={row['aoa']} -> {row['value']} vs {row['threshold']} ({eval_result}, reported status={status})"
            )
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(
        description="Summarize guard/tuned-term invariance measurements."
    )
    parser.add_argument(
        "--plan",
        type=Path,
        default=Path("paper/data/csv/guard_tuned_invariance_matrix.csv"),
        help="CSV describing the invariance plan.",
    )
    parser.add_argument(
        "--measurements",
        type=Path,
        default=Path("paper/data/csv/guard_tuned_invariance_metrics.csv"),
        help="CSV exporting the current guard measurements.",
    )
    args = parser.parse_args()

    plan = load_plan(args.plan)
    measurements = load_measurements(args.measurements)
    summary = summarize(plan, measurements)
    print(summary)


if __name__ == "__main__":
    main()

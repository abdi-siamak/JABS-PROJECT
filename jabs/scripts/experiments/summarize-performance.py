#!/usr/bin/env python3

import csv
import glob
import math
import os
import statistics
import sys
from collections import defaultdict


T_CRITICAL_975 = {
    1: 12.706, 2: 4.303, 3: 3.182, 4: 2.776, 5: 2.571,
    6: 2.447, 7: 2.365, 8: 2.306, 9: 2.262, 10: 2.228,
    11: 2.201, 12: 2.179, 13: 2.160, 14: 2.145, 15: 2.131,
    16: 2.120, 17: 2.110, 18: 2.101, 19: 2.093, 20: 2.086,
    21: 2.080, 22: 2.074, 23: 2.069, 24: 2.064, 25: 2.060,
    26: 2.056, 27: 2.052, 28: 2.048, 29: 2.045, 30: 2.042,
}


def as_bool(value):
    return value.strip().lower() == "true"


def as_float(value):
    try:
        value = float(value)
        return None if math.isnan(value) else value
    except (TypeError, ValueError):
        return None


def mean_ci95(values):
    values = [float(v) for v in values]
    if not values:
        return float("nan"), float("nan")
    if len(values) == 1:
        return values[0], float("nan")

    mean = statistics.mean(values)
    sd = statistics.stdev(values)
    df = len(values) - 1
    t = T_CRITICAL_975.get(df, 1.96)
    half = t * sd / math.sqrt(len(values))
    return mean, half


def percentile(values, p):
    values = sorted(float(v) for v in values)
    if not values:
        return float("nan")
    if len(values) == 1:
        return values[0]

    position = p * (len(values) - 1)
    lower = math.floor(position)
    upper = math.ceil(position)

    if lower == upper:
        return values[lower]

    weight = position - lower
    return (
        values[lower]
        + weight * (values[upper] - values[lower])
    )


def load_rows(nodes):
    pattern = os.path.join(
        "output", "journal", "performance",
        "becp-reap-plus",
        f"nodes-{nodes}",
        "lambda-*",
        "seed-*",
        "summary.csv",
    )

    rows = []
    for filename in sorted(glob.glob(pattern)):
        with open(filename, newline="") as handle:
            for row in csv.DictReader(handle):
                row["_summary_file"] = filename
                rows.append(row)
    return rows


def load_latency_rows(nodes):
    pattern = os.path.join(
        "output", "journal", "performance",
        "becp-reap-plus",
        f"nodes-{nodes}",
        "lambda-*",
        "seed-*",
        "latencies.csv",
    )

    rows = []
    for filename in sorted(glob.glob(pattern)):
        with open(filename, newline="") as handle:
            for row in csv.DictReader(handle):
                row["_latency_file"] = filename
                rows.append(row)
    return rows


def main():
    nodes = int(sys.argv[1]) if len(sys.argv) > 1 else 1000

    rows = load_rows(nodes)
    latency_rows = load_latency_rows(nodes)

    if not rows:
        print(
            "No performance summary.csv files found for "
            f"{nodes} nodes."
        )
        sys.exit(1)

    groups = defaultdict(list)
    latency_groups = defaultdict(list)

    for row in rows:
        groups[float(row["nominal_lambda"])].append(row)

    for row in latency_rows:
        latency_groups[float(row["nominal_lambda"])].append(row)

    report = []
    report.append("")
    report.append(
        "Experiment 7 - Offered-Load Performance (BECP + REAP+)"
    )
    report.append(
        "-------------------------------------------------------"
    )
    report.append(f"Nodes: {nodes}")
    report.append("")

    for lam in sorted(groups):
        group = groups[lam]
        lat_group = latency_groups.get(lam, [])

        realized = [float(r["realized_lambda"]) for r in group]
        throughput = [float(r["throughput"]) for r in group]
        backlog_load = [int(r["backlog_at_load_end"]) for r in group]
        backlog_final = [int(r["backlog_final"]) for r in group]
        uncommitted = [
            float(r["uncommitted_fraction_final"])
            for r in group
        ]
        generated = [int(r["generated_candidates"]) for r in group]
        messages = [float(r["messages"]) for r in group]
        message_bytes = [float(r["message_bytes"]) for r in group]

        quorum_latencies = [
            as_float(r["quorum_latency"])
            for r in lat_group
        ]
        quorum_latencies = [
            v for v in quorum_latencies
            if v is not None
        ]

        realized_mean, realized_ci = mean_ci95(realized)
        throughput_mean, throughput_ci = mean_ci95(throughput)
        uncommitted_mean, uncommitted_ci = mean_ci95(uncommitted)

        safety_failures = sum(
            not as_bool(r["safety_pass"])
            for r in group
        )

        divergent_runs = sum(
            int(r["divergent_heights"]) > 0
            for r in group
        )

        report.extend([
            f"lambda = {lam:.3f} proposals/s",
            f"  Runs found:                    {len(group)}",
            f"  Safety failures:               {safety_failures}",
            f"  Divergent runs:                {divergent_runs}",
            (
                "  Mean generated/run:            "
                f"{statistics.mean(generated):.2f}"
            ),
            (
                "  Realized lambda:               "
                f"{realized_mean:.6f}"
                + (
                    ""
                    if math.isnan(realized_ci)
                    else f" +/- {realized_ci:.6f} (95% CI)"
                )
            ),
            (
                "  Committed throughput:          "
                f"{throughput_mean:.6f}"
                + (
                    ""
                    if math.isnan(throughput_ci)
                    else f" +/- {throughput_ci:.6f} (95% CI)"
                )
            ),
            (
                "  Mean backlog at load end:      "
                f"{statistics.mean(backlog_load):.2f}"
            ),
            (
                "  Mean backlog final:            "
                f"{statistics.mean(backlog_final):.2f}"
            ),
            (
                "  Final uncommitted fraction:    "
                f"{uncommitted_mean:.6f}"
                + (
                    ""
                    if math.isnan(uncommitted_ci)
                    else f" +/- {uncommitted_ci:.6f} (95% CI)"
                )
            ),
        ])

        if quorum_latencies:
            report.extend([
                (
                    "  Pooled quorum latency mean:   "
                    f"{statistics.mean(quorum_latencies):.6f} s"
                ),
                (
                    "  Pooled quorum latency median: "
                    f"{statistics.median(quorum_latencies):.6f} s"
                ),
                (
                    "  Pooled quorum latency p95:    "
                    f"{percentile(quorum_latencies, 0.95):.6f} s"
                ),
                (
                    "  Pooled quorum latency p99:    "
                    f"{percentile(quorum_latencies, 0.99):.6f} s"
                ),
                (
                    "  Latency samples:              "
                    f"{len(quorum_latencies)}"
                ),
            ])
        else:
            report.append("  Latency samples:              0")

        report.extend([
            (
                "  Mean messages/run:             "
                f"{statistics.mean(messages):.2f}"
            ),
            (
                "  Mean bytes/run:                "
                f"{statistics.mean(message_bytes):.2f}"
            ),
            "",
        ])

    text = "\n".join(report)
    print(text)

    root = os.path.join(
        "output", "journal", "performance",
        "becp-reap-plus",
        f"nodes-{nodes}",
    )
    os.makedirs(root, exist_ok=True)

    combined_runs = os.path.join(root, "combined-runs.csv")
    clean_fields = [
        key for key in rows[0].keys()
        if not key.startswith("_")
    ]

    with open(combined_runs, "w", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=clean_fields,
            extrasaction="ignore",
        )
        writer.writeheader()
        writer.writerows(rows)

    combined_latencies = os.path.join(root, "combined-latencies.csv")
    if latency_rows:
        clean_latency_fields = [
            key for key in latency_rows[0].keys()
            if not key.startswith("_")
        ]

        with open(combined_latencies, "w", newline="") as handle:
            writer = csv.DictWriter(
                handle,
                fieldnames=clean_latency_fields,
                extrasaction="ignore",
            )
            writer.writeheader()
            writer.writerows(latency_rows)

    summary_report = os.path.join(root, "summary-report.txt")
    with open(summary_report, "w", encoding="utf-8") as handle:
        handle.write(text + "\n")

    print(f"Combined runs:      {combined_runs}")
    if latency_rows:
        print(f"Combined latencies: {combined_latencies}")
    print(f"Summary report:     {summary_report}")


if __name__ == "__main__":
    main()

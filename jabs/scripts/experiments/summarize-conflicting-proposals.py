#!/usr/bin/env python3

import csv
import glob
import math
import os
import statistics
import sys


def read_runs(root):
    pattern = os.path.join(root, "seed-*", "summary.csv")
    rows = []

    for filename in sorted(glob.glob(pattern)):
        with open(filename, newline="") as handle:
            reader = csv.DictReader(handle)
            rows.extend(reader)

    return rows


def as_bool(value):
    return value.strip().lower() == "true"


def as_float(value):
    try:
        number = float(value)
        if math.isnan(number):
            return None
        return number
    except (TypeError, ValueError):
        return None


def build_summary(
        nodes,
        runs,
        safety_failures,
        progress_runs,
        a_wins,
        b_wins,
        no_commit,
        divergent,
        latencies,
        messages,
        message_bytes):

    lines = [
        "",
        "Experiment 1 - Simultaneous Conflicting Proposals",
        "----------------------------------------",
        f"Nodes:                 {nodes}",
        f"Runs found:            {len(runs)}",
        f"Safety failures:       {safety_failures}",
        f"Runs with progress:    {progress_runs}",
        f"Candidate A wins:      {a_wins}",
        f"Candidate B wins:      {b_wins}",
        f"No commit:             {no_commit}",
        f"Divergent commits:     {divergent}",
    ]

    if latencies:
        lines.append(
            "Mean commit latency:   "
            f"{statistics.mean(latencies):.6f} s"
        )

        if len(latencies) > 1:
            lines.append(
                "Latency std. dev.:    "
                f"{statistics.stdev(latencies):.6f} s"
            )

    lines.extend([
        "Mean messages/run:      "
        f"{statistics.mean(messages):.2f}",
        "Mean bytes/run:         "
        f"{statistics.mean(message_bytes):.2f}",
    ])

    return "\n".join(lines)


def main():
    nodes = int(sys.argv[1]) if len(sys.argv) > 1 else 1000

    root = os.path.join(
        "output",
        "journal",
        "conflicting-proposals",
        f"nodes-{nodes}",
    )

    runs = read_runs(root)

    if not runs:
        print(f"No summary.csv files found under {root}")
        sys.exit(1)

    safety_failures = sum(
        not as_bool(row["safety_pass"])
        for row in runs
    )

    progress_runs = sum(
        as_bool(row["progress_observed"])
        for row in runs
    )

    a_wins = sum(row["winner"] == "A" for row in runs)
    b_wins = sum(row["winner"] == "B" for row in runs)
    no_commit = sum(row["winner"] == "NONE" for row in runs)
    divergent = sum(row["winner"] == "DIVERGENT" for row in runs)

    latencies = [
        value
        for row in runs
        if (value := as_float(row["avg_commit_latency"])) is not None
    ]

    messages = [float(row["messages"]) for row in runs]
    message_bytes = [float(row["message_bytes"]) for row in runs]

    summary_text = build_summary(
        nodes,
        runs,
        safety_failures,
        progress_runs,
        a_wins,
        b_wins,
        no_commit,
        divergent,
        latencies,
        messages,
        message_bytes,
    )

    print(summary_text)

    combined_file = os.path.join(root, "combined-runs.csv")

    fieldnames = runs[0].keys()

    with open(combined_file, "w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(runs)

    summary_file = os.path.join(root, "summary-report.txt")

    with open(summary_file, "w", encoding="utf-8") as handle:
        handle.write(summary_text)
        handle.write("\n")

    print()
    print(f"Combined results: {combined_file}")
    print(f"Summary report:   {summary_file}")


if __name__ == "__main__":
    main()

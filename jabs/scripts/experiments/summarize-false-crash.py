#!/usr/bin/env python3

import csv
import glob
import math
import os
import statistics
import sys


def read_runs(root):
    rows = []

    pattern = os.path.join(
        root,
        "seed-*",
        "summary.csv",
    )

    for filename in sorted(glob.glob(pattern)):
        with open(filename, newline="") as handle:
            rows.extend(csv.DictReader(handle))

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


def mean_int(rows, field):
    return statistics.mean(
        int(row[field])
        for row in rows
    )


def main():

    nodes = (
        int(sys.argv[1])
        if len(sys.argv) > 1
        else 1000
    )

    root = os.path.join(
        "output",
        "journal",
        "false-crash",
        f"nodes-{nodes}",
    )

    runs = read_runs(root)

    if not runs:
        print(
            f"No summary.csv files found under {root}"
        )
        sys.exit(1)

    safety_failures = sum(
        not as_bool(row["safety_pass"])
        for row in runs
    )

    false_suspicion_runs = sum(
        as_bool(row["false_suspicion_observed"])
        for row in runs
    )

    real_crash_runs = sum(
        not as_bool(row["no_real_crash"])
        for row in runs
    )

    delayed_pull_failures = sum(
        not as_bool(row["delayed_pull_injected"])
        for row in runs
    )

    tracking_failures = sum(
        not as_bool(row["recovery_exchange_tracked"])
        for row in runs
    )

    terminal_failures = sum(
        not as_bool(row["exact_once_terminal_observed"])
        for row in runs
    )

    reciprocal_suspicion_runs = sum(
        as_bool(row["target_suspected_sender"])
        for row in runs
    )

    progress_runs = sum(
        as_bool(row["progress_observed"])
        for row in runs
    )

    converged_runs = sum(
        as_bool(row["fully_converged"])
        for row in runs
    )

    divergent_runs = sum(
        int(row["distinct_committed_candidates"]) > 1
        for row in runs
    )

    latencies = [
        value
        for row in runs
        if (
            value := as_float(
                row["avg_commit_latency"]
            )
        ) is not None
    ]

    messages = [
        float(row["messages"])
        for row in runs
    ]

    message_bytes = [
        float(row["message_bytes"])
        for row in runs
    ]

    lines = [
        "",
        "Experiment 5 - False Crash Suspicion from Delayed Pull/RePush",
        "-------------------------------------------------------------",
        f"Nodes:                            {nodes}",
        f"Runs found:                       {len(runs)}",
        f"Safety failures:                  {safety_failures}",
        f"Runs with false suspicion:        {false_suspicion_runs}",
        f"Runs with any real crash:         {real_crash_runs}",
        f"Delayed-Pull injection failures:  {delayed_pull_failures}",
        f"Exchange-tracking failures:       {tracking_failures}",
        f"Terminal-state failures:          {terminal_failures}",
        f"Reciprocal suspicion runs:        {reciprocal_suspicion_runs}",
        f"Runs with post-fault progress:    {progress_runs}",
        f"Fully converged runs:             {converged_runs}",
        f"Divergent commit runs:            {divergent_runs}",
        (
            "Mean delayed packets/run:        "
            f"{mean_int(runs, 'delayed_reverse_packets'):.2f}"
        ),
        (
            "Mean actual crashes/run:         "
            f"{mean_int(runs, 'actual_crashes_final'):.2f}"
        ),
        (
            "Mean committed nodes/run:        "
            f"{mean_int(runs, 'committed_candidate_nodes'):.2f}"
        ),
    ]

    if latencies:

        lines.append(
            "Mean commit latency:              "
            f"{statistics.mean(latencies):.6f} s"
        )

        if len(latencies) > 1:

            lines.append(
                "Latency std. dev.:              "
                f"{statistics.stdev(latencies):.6f} s"
            )

    lines.extend([
        (
            "Mean messages/run:                "
            f"{statistics.mean(messages):.2f}"
        ),
        (
            "Mean bytes/run:                   "
            f"{statistics.mean(message_bytes):.2f}"
        ),
    ])

    summary_text = "\n".join(lines)

    print(summary_text)

    combined_file = os.path.join(
        root,
        "combined-runs.csv",
    )

    with open(
            combined_file,
            "w",
            newline="") as handle:

        writer = csv.DictWriter(
            handle,
            fieldnames=runs[0].keys(),
        )

        writer.writeheader()
        writer.writerows(runs)

    summary_file = os.path.join(
        root,
        "summary-report.txt",
    )

    with open(
            summary_file,
            "w",
            encoding="utf-8") as handle:

        handle.write(summary_text)
        handle.write("\n")

    print()
    print(
        f"Combined results: {combined_file}"
    )

    print(
        f"Summary report:   {summary_file}"
    )


if __name__ == "__main__":
    main()

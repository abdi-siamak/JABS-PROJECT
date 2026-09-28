#!/usr/bin/env python3
import csv
import glob
import math
import os
import statistics
import sys

def as_bool(value):
    return value.strip().lower() == "true"

def as_float(value):
    try:
        number = float(value)
        return None if math.isnan(number) else number
    except (TypeError, ValueError):
        return None

def read_runs(root):
    rows = []
    for filename in sorted(
            glob.glob(os.path.join(root, "seed-*", "summary.csv"))):
        with open(filename, newline="") as handle:
            rows.extend(csv.DictReader(handle))
    return rows

def mean_int(rows, field):
    return statistics.mean(int(row[field]) for row in rows)

def mean_float(rows, field):
    values = [as_float(row[field]) for row in rows]
    values = [value for value in values if value is not None]
    return statistics.mean(values) if values else float("nan")

def main():
    nodes = int(sys.argv[1]) if len(sys.argv) > 1 else 1000
    root = os.path.join(
        "output", "journal", "crash-recovery", f"nodes-{nodes}")

    runs = read_runs(root)
    if not runs:
        print(f"No summary.csv files found under {root}")
        sys.exit(1)

    safety_failures = sum(
        not as_bool(row["safety_pass"]) for row in runs)
    vote_failures = sum(
        not as_bool(row["vote_a_observed"]) for row in runs)
    crash_failures = sum(
        not as_bool(row["crash_performed"]) for row in runs)
    volatile_failures = sum(
        not as_bool(row["volatile_state_cleared"]) for row in runs)
    recovery_failures = sum(
        not as_bool(row["recovery_performed"]) for row in runs)
    recovery_request_failures = sum(
        not as_bool(row["recovery_request_sent"]) for row in runs)
    recovery_merge_failures = sum(
        not as_bool(row["recovery_ledger_merged"]) for row in runs)
    preservation_failures = sum(
        not as_bool(row["durable_vote_preserved"]) for row in runs)
    replay_failures = sum(
        not as_bool(row["same_vote_replay_rejected"]) for row in runs)
    conflict_failures = sum(
        not as_bool(row["conflicting_revote_rejected"]) for row in runs)
    unchanged_failures = sum(
        not as_bool(row["durable_vote_unchanged_after_conflict"])
        for row in runs)
    oracle_failures = sum(
        not as_bool(row["durable_oracle_pass"]) for row in runs)
    precrash_commits = sum(
        as_bool(row["victim_committed_before_crash"]) for row in runs)
    eventual_victim_commits = sum(
        as_bool(row["victim_eventually_committed_a"]) for row in runs)
    progress = sum(
        as_bool(row["progress_observed"]) for row in runs)
    converged = sum(
        as_bool(row["fully_converged"]) for row in runs)
    divergent = sum(
        not as_bool(row["no_divergent_commit"]) for row in runs)
    final_crash_runs = sum(
        int(row["actual_crashed_nodes_final"]) > 0 for row in runs)

    latencies = [
        value
        for row in runs
        if (value := as_float(row["avg_commit_latency"])) is not None
    ]

    lines = [
        "",
        "Experiment 6 - Crash/Recovery with Persistent Final Vote",
        "--------------------------------------------------------",
        f"Nodes:                            {nodes}",
        f"Runs found:                       {len(runs)}",
        f"Safety failures:                  {safety_failures}",
        f"Vote-A observation failures:      {vote_failures}",
        f"Crash execution failures:         {crash_failures}",
        f"Volatile-reset failures:          {volatile_failures}",
        f"Recovery execution failures:      {recovery_failures}",
        f"Recovery-request failures:        {recovery_request_failures}",
        f"Recovery-ledger merge failures:   {recovery_merge_failures}",
        f"Durable-vote preservation fail.:  {preservation_failures}",
        f"Same-vote replay failures:        {replay_failures}",
        f"Conflicting re-vote failures:     {conflict_failures}",
        f"Durable-vote changed failures:    {unchanged_failures}",
        f"Durable-oracle failures:          {oracle_failures}",
        f"Victim committed before crash:    {precrash_commits}",
        f"Victim eventually committed A:    {eventual_victim_commits}",
        f"Runs with progress:               {progress}",
        f"Fully converged runs:             {converged}",
        f"Divergent commit runs:            {divergent}",
        f"Runs ending with crashed nodes:   {final_crash_runs}",
        (
            "Mean committed A nodes/run:      "
            f"{mean_int(runs, 'committed_a_nodes'):.2f}"
        ),
        (
            "Mean committed other/run:        "
            f"{mean_int(runs, 'committed_other_nodes'):.2f}"
        ),
        (
            "Mean vote-observation time:      "
            f"{mean_float(runs, 'vote_observed_time'):.6f} s"
        ),
        (
            "Mean recovery time:              "
            f"{mean_float(runs, 'recovery_time'):.6f} s"
        ),
    ]

    if latencies:
        lines.append(
            "Mean commit latency:              "
            f"{statistics.mean(latencies):.6f} s")
        if len(latencies) > 1:
            lines.append(
                "Latency std. dev.:              "
                f"{statistics.stdev(latencies):.6f} s")

    messages = [float(row["messages"]) for row in runs]
    message_bytes = [float(row["message_bytes"]) for row in runs]

    lines.extend([
        f"Mean messages/run:                {statistics.mean(messages):.2f}",
        f"Mean bytes/run:                   {statistics.mean(message_bytes):.2f}",
    ])

    report = "\n".join(lines)
    print(report)

    combined = os.path.join(root, "combined-runs.csv")
    with open(combined, "w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=runs[0].keys())
        writer.writeheader()
        writer.writerows(runs)

    summary = os.path.join(root, "summary-report.txt")
    with open(summary, "w", encoding="utf-8") as handle:
        handle.write(report + "\n")

    print()
    print(f"Combined results: {combined}")
    print(f"Summary report:   {summary}")


if __name__ == "__main__":
    main()

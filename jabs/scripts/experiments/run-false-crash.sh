#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(
    cd "$(dirname "${BASH_SOURCE[0]}")/../.."
    pwd
)"

cd "$ROOT_DIR"

if [ "$#" -lt 1 ]; then
    echo "Usage:"
    echo "  $0 SEED [SEED ...]"
    echo
    echo "Optional environment variables:"
    echo "  NODES=1000"
    echo "  DURATION=75"
    exit 1
fi

NODES="${NODES:-1000}"
DURATION="${DURATION:-75}"

export GIT_COMMIT="$(
    git rev-parse HEAD 2>/dev/null || echo unknown
)"

echo "========================================"
echo "Experiment 5: False Crash Suspicion"
echo "Nodes:       $NODES"
echo "Duration:    $DURATION seconds"
echo "Mode:        REAP+ enabled, SSEP disabled"
echo "Fault:       delay one controlled Pull past PULL_TIMEOUT"
echo "Git commit:  $GIT_COMMIT"
echo "Seeds:       $*"
echo "========================================"

echo "Compiling..."
mvn -q \
    -Djabs.becp.ssep=false \
    -Djabs.becp.reapPlus=true \
    -DskipTests \
    compile

for SEED in "$@"; do

    echo
    echo "----------------------------------------"
    echo "Running seed $SEED"
    echo "----------------------------------------"

    mvn -q \
        -Djabs.becp.ssep=false \
        -Djabs.becp.reapPlus=true \
        org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
        -Dexec.mainClass="jabs.scenario.experiments.FalseCrashSuspicionScenario" \
        -Dexec.args="--seed ${SEED} --nodes ${NODES} --duration ${DURATION}"

done

echo
echo "Experiment run(s) completed."

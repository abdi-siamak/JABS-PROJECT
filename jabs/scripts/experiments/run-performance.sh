#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(
    cd "$(dirname "${BASH_SOURCE[0]}")/../.."
    pwd
)"
cd "$ROOT_DIR"

if [ "$#" -lt 2 ]; then
    echo "Usage: $0 LAMBDA SEED [SEED ...]"
    echo
    echo "Example:"
    echo "  $0 0.10 1"
    echo
    echo "Optional environment variables:"
    echo "  NODES=1000"
    echo "  WARMUP=20"
    echo "  LOAD_DURATION=180"
    echo "  DRAIN=40"
    echo "  MONITOR_INTERVAL=0.10"
    exit 1
fi

LAMBDA="$1"
shift

NODES="${NODES:-1000}"
WARMUP="${WARMUP:-20}"
LOAD_DURATION="${LOAD_DURATION:-180}"
DRAIN="${DRAIN:-40}"
MONITOR_INTERVAL="${MONITOR_INTERVAL:-0.10}"

export GIT_COMMIT="$(
    git rev-parse HEAD 2>/dev/null || echo unknown
)"

echo "========================================"
echo "Experiment 7: Offered-load performance"
echo "Protocol:         BECP + REAP+"
echo "Nodes:            $NODES"
echo "Nominal lambda:   $LAMBDA proposals/s"
echo "Warmup:           $WARMUP s"
echo "Load window:      $LOAD_DURATION s"
echo "Drain:            $DRAIN s"
echo "Monitor interval: $MONITOR_INTERVAL s"
echo "Git commit:       $GIT_COMMIT"
echo "Seeds:            $*"
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
    echo "lambda=$LAMBDA seed=$SEED"
    echo "----------------------------------------"

    mvn -q \
        -Djabs.becp.ssep=false \
        -Djabs.becp.reapPlus=true \
        org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
        -Dexec.mainClass="jabs.scenario.experiments.PerformanceScenario" \
        -Dexec.args="--seed ${SEED} --nodes ${NODES} --lambda ${LAMBDA} --warmup ${WARMUP} --load-duration ${LOAD_DURATION} --drain ${DRAIN} --monitor-interval ${MONITOR_INTERVAL}"
done

echo
echo "Experiment run(s) completed."

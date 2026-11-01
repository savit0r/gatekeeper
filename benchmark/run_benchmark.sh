#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR/.."

echo "========================================================"
echo " GATEKEEPER HIGH-CONCURRENCY LOAD BENCHMARK"
echo "========================================================"

# Check if application is running
if ! curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "Gatekeeper is not reachable on http://localhost:8080."
    echo "Starting Gatekeeper via Docker Compose..."
    docker compose up -d
    sleep 5
fi

# Run Python high-concurrency benchmark (1000 requests @ 50 concurrent virtual threads)
python3 benchmark/stress_benchmark.py 1000 50

echo ""
echo "Benchmarking complete."

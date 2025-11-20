#!/usr/bin/env bash
set -euo pipefail

# always run from repo root
cd "$(dirname "$0")/.."

echo "=== Running PIT on graphhopper-web-api ==="
mvn -pl web-api -am org.pitest:pitest-maven:mutationCoverage

REPORT="web-api/target/pit-reports/index.html"

if [ ! -f "$REPORT" ]; then
  echo "❌ PIT report not found at $REPORT"
  exit 1
fi

echo "=== Extracting mutation score from report ==="

CURRENT_SCORE=$(
  grep -i "Mutation coverage" "$REPORT" \
    | head -n 1 \
    | grep -oE "[0-9]+(\.[0-9]+)?" || echo "0"
)

echo "Current mutation score: $CURRENT_SCORE"

if [ ! -f mutation-baseline.txt ]; then
  echo "❌ mutation-baseline.txt not found at repo root"
  exit 1
fi

# loads MIN_MUTATION_SCORE=xx.x
# shellcheck disable=SC1091
source mutation-baseline.txt
echo "Baseline minimum score: $MIN_MUTATION_SCORE"

echo "=== Comparing scores ==="
awk -v cur="$CURRENT_SCORE" -v min="$MIN_MUTATION_SCORE" '
  BEGIN {
    if (cur + 0 < min + 0) {
      printf "❌ Mutation score decreased (%.2f < %.2f)\n", cur, min
      exit 1
    } else {
      printf "✅ Mutation score OK (%.2f >= %.2f)\n", cur, min
      exit 0
    }
  }
'

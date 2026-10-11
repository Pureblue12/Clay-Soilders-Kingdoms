#!/bin/bash
# Builds every promoted structure level with 3 clay-soldier builders (StructureBuildGameTests) and writes one result row
# per level to build/structure-results.tsv. Not part of the required suite: these are optional GameTests.
# Env vars (at least CSR_STRUCTURE_BUILDS must be set, or no structure tests exist):
#   CSR_STRUCTURE_BUILDS=all        every structure level
#   CSR_STRUCTURE_BUILDS=2/20       shard 2 of 20 (CI splits the work over up to 20 machines)
#   CSR_STRUCTURE_BUILDS_ONLY=keep,church   optional: only these structure ids (keep:2 = only its level 2)
# The test server ticks as fast as it can while these run (StructureBuildSprint); results are in ticks, not affected.
# Example: CSR_STRUCTURE_BUILDS=all CSR_STRUCTURE_BUILDS_ONLY=keep tools/structure_builds.sh
# The full log is kept in build/structure-builds.log. The required suite runs in the same server, and its known
# flaky tests can make Gradle fail, so Gradle's exit code is ignored: the rows say what happened.
# Exits 0 if the server started and planned at least one structure test (even if some builds failed), else 1.
# Merge shard results into a Markdown table with: python3 tools/structure_results.py table build/structure-results.tsv
cd "$(dirname "$0")/.." || exit 2
mkdir -p build
if [ -z "$CSR_STRUCTURE_BUILDS" ]; then
    echo "CSR_STRUCTURE_BUILDS is not set: no structure tests will run (set it to 'all' or a shard like '1/4')."
fi
rm -rf neoforge/runs/gametest/world
./gradlew :neoforge:runGameTestServer > build/structure-builds.log 2>&1 || true
python3 tools/structure_results.py parse build/structure-builds.log build/structure-results.tsv
if ! grep -q "CSR structure build planned:" build/structure-builds.log; then
    echo "NO STRUCTURE TESTS PLANNED (server didn't start, or CSR_STRUCTURE_BUILDS matched nothing). Last 60 log lines:"
    tail -n 60 build/structure-builds.log
    exit 1
fi
cat build/structure-results.tsv
exit 0

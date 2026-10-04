#!/bin/bash
# Runs the required GameTest suite and the build, and exits 0 only if they are OK, so a commit can be chained after it:
#   tools/suite.sh && git commit ...
# Tests-first commits: pass the names of the NEW tests that are expected to fail (lowercase, as the log prints them);
# it then exits 0 only if exactly those tests failed and everything else passed:
#   tools/suite.sh newtestone newtesttwo && git commit ...
# The full log is kept in build/suite.log.
# Every run starts from a fresh test world: the test grid never clears the gaps between test areas, so blocks left by
# earlier runs (e.g. test terrains) would otherwise sit next to other tests and change what the terrain check sees.
cd "$(dirname "$0")/.." || exit 2
mkdir -p build
rm -rf neoforge/runs/gametest/world
./gradlew :neoforge:runGameTestServer > build/suite.log 2>&1
failed=$(grep -E "LogTestReporter\]: .* failed at" build/suite.log | sed -E 's/.*LogTestReporter\]: ([a-z0-9_]+) failed at.*/\1/' | sort -u)
grep -E "required tests (passed|failed)" build/suite.log | sed -E 's/.*GameTestServer\]: //'
expected=$(printf '%s\n' "$@" | sed '/^$/d' | sort -u)
if [ "$failed" != "$expected" ]; then
    echo "SUITE NOT AS EXPECTED."
    echo "  failed:   $(echo $failed)"
    echo "  expected: $(echo $expected)"
    grep -E "failed at" build/suite.log | sed -E 's/.*LogTestReporter\]: //' | cut -c1-250
    exit 1
fi
if ! ./gradlew :neoforge:build -q > build/build.log 2>&1; then
    echo "BUILD FAILED (see build/build.log)"
    exit 1
fi
echo "OK: suite as expected$( [ -n "$expected" ] && echo " (only the new tests failed: $(echo $expected))"), build passed."

#!/bin/bash
# Runs the required GameTest suite and the build, and exits 0 only if they are OK, so a commit can be chained after it:
#   tools/suite.sh && git commit ...
# Tests-first commits: pass the names of the NEW tests that are expected to fail (lowercase, as the log prints them);
# it then exits 0 only if exactly those tests failed and everything else passed:
#   tools/suite.sh newtestone newtesttwo && git commit ...
# The full log is kept in build/suite.log.
# Optional tests (required = false) don't count: their failures are listed separately ("optional failed").
# Every run starts from a fresh test world: the test grid never clears the gaps between test areas, so blocks left by
# earlier runs (e.g. test terrains) would otherwise sit next to other tests and change what the terrain check sees.
cd "$(dirname "$0")/.." || exit 2
mkdir -p build
rm -rf neoforge/runs/gametest/world
# SUITE_MINUTES (CI sets 30): a test server still running after that long is stuck or crawling. Its threads are dumped
# (build/threads.log; the server thread is printed) with the log's last lines, then it is stopped, so the run says where.
./gradlew :neoforge:runGameTestServer > build/suite.log 2>&1 &
gradle=$!
if [ -n "$SUITE_MINUTES" ] && [ "$SUITE_MINUTES" -gt 0 ]; then
    deadline=$(( $(date +%s) + SUITE_MINUTES * 60 ))
    while kill -0 $gradle 2>/dev/null && [ "$(date +%s)" -lt "$deadline" ]; do sleep 5; done
    if kill -0 $gradle 2>/dev/null; then
        echo "SUITE STILL RUNNING AFTER $SUITE_MINUTES MINUTES; thread dumps in build/threads.log"
        : > build/threads.log
        for pid in $(pgrep -f java); do
            echo "=== java $pid: $(tr '\0' ' ' < /proc/$pid/cmdline | cut -c1-200)" >> build/threads.log
            jcmd "$pid" Thread.print >> build/threads.log 2>&1
        done
        awk '/^"Server thread"/{p=1} p&&/^$/{exit} p' build/threads.log | head -n 80
        echo "--- last lines of build/suite.log:"
        tail -n 80 build/suite.log | cut -c1-400
        pkill -P $gradle; kill $gradle; pkill -f runGameTestServer; pkill -f GameTestServer
        exit 1
    fi
fi
wait $gradle
failed=$(grep -E "LogTestReporter\]: [a-z0-9_]+ failed at" build/suite.log | sed -E 's/.*LogTestReporter\]: ([a-z0-9_]+) failed at.*/\1/' | sort -u)
optional=$(grep -E "LogTestReporter\]: \(optional\) [a-z0-9_]+ failed at" build/suite.log | sed -E 's/.*\(optional\) ([a-z0-9_]+) failed at.*/\1/' | sort -u)
[ -n "$optional" ] && echo "optional failed (not counted): $(echo $optional)"
summary=$(grep -E "required tests (passed|failed)" build/suite.log | sed -E 's/.*GameTestServer\]: //')
if [ -z "$summary" ]; then
    # The test server never printed its summary: it crashed or was cut off, which is not a pass.
    echo "SUITE DID NOT FINISH (no summary line in build/suite.log); last lines:"
    tail -n 60 build/suite.log | cut -c1-300
    exit 1
fi
echo "$summary"
expected=$(printf '%s\n' "$@" | sed '/^$/d' | sort -u)
if [ "$failed" != "$expected" ]; then
    echo "SUITE NOT AS EXPECTED."
    echo "  failed:   $(echo $failed)"
    echo "  expected: $(echo $expected)"
    grep -E "failed at" build/suite.log | sed -E 's/.*LogTestReporter\]: //' | cut -c1-6000
    exit 1
fi
if ! ./gradlew :neoforge:build -q > build/build.log 2>&1; then
    echo "BUILD FAILED (see build/build.log)"
    exit 1
fi
echo "OK: suite as expected$( [ -n "$expected" ] && echo " (only the new tests failed: $(echo $expected))"), build passed."

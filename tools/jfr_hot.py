#!/usr/bin/env python3
"""Summarizes a Java Flight Recorder file's CPU samples of one thread (experiments: where the server thread spends its time).

Usage: jfr print --events jdk.ExecutionSample --stack-depth 64 rec.jfr | python3 tools/jfr_hot.py [thread] [top]
Prints the methods most often at the top of the stack (self time) and most often anywhere on it (total time), as a
share of the thread's samples. Standard library only."""
import re
import sys
from collections import Counter

thread = sys.argv[1] if len(sys.argv) > 1 else "Server thread"
top = int(sys.argv[2]) if len(sys.argv) > 2 else 40
frame_re = re.compile(r"^\s+([\w$.<>]+)\(")
self_count, total_count, csr_chain = Counter(), Counter(), Counter()
samples = 0
in_sample = keep = in_stack = False
frames = []


def finish():
    global samples
    if not keep or not frames:
        return
    samples += 1
    self_count[frames[0]] += 1
    for m in set(frames):
        total_count[m] += 1
    # the innermost mod method and the innermost vanilla method under it: who calls the expensive vanilla code
    csr = next((m for m in frames if ".claysoldiers." in m), None)
    if csr:
        csr_chain[f"{frames[0]}  <-  {csr}"] += 1


for line in sys.stdin:
    if line.startswith("jdk.ExecutionSample"):
        in_sample, keep, in_stack, frames = True, False, False, []
    elif not in_sample:
        continue
    elif "sampledThread" in line:
        keep = f'"{thread}"' in line
    elif "stackTrace = [" in line:
        in_stack = True
    elif in_stack and line.strip() == "]":
        in_stack = False
    elif in_stack:
        m = frame_re.match(line)
        if m:
            frames.append(m.group(1))
    elif line.startswith("}"):
        finish()
        in_sample = False

print(f"{samples} samples of thread {thread!r}")
for title, counter in (("Self time (top of stack)", self_count), ("Total time (anywhere on stack)", total_count),
                       ("Hot frame <- innermost mod method", csr_chain)):
    print(f"\n## {title}")
    for m, n in counter.most_common(top):
        print(f"{100.0 * n / max(samples, 1):6.2f}%  {m}")

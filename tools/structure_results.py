#!/usr/bin/env python3
"""Reads the structure build results (StructureBuildGameTests, run by tools/structure_builds.sh).

  python3 tools/structure_results.py parse <log> <out.tsv>
      Reads a runGameTestServer log and writes one row per planned test:
      test, id, level, status (PASS / FAIL / NO RESULT), blocks, ticks, reason.
  python3 tools/structure_results.py table <tsv files...>
      Merges shard TSVs and prints a Markdown report, one row per structure.
      Exits 0 if every structure level passed, 1 otherwise (after printing the table).

Standard library only.
"""
import re
import sys

PLANNED = re.compile(r"CSR structure build planned: (\S+) \| (\S+) \| L(\d+) \| (\d+) blocks \| height (\d+)")
PASSED = re.compile(r"CSR structure build result: PASS (\S+) \| (\S+) \| L(\d+) \| (\d+) blocks \| (\d+) ticks")
FAILED = re.compile(r"CSR structure build result: FAIL (\S+) \| (\S+) \| L(\d+) \| (.*)$")
# The framework's own failure line (timeouts, exceptions): "(optional) structurebuild_keep_l3 failed at x, y, z! msg"
FRAMEWORK = re.compile(r"LogTestReporter\]: (?:\(optional\) )?(structurebuild_[a-z0-9_]+) failed at [^!]*!\s*(.*)$")

COLUMNS = ["test", "id", "level", "status", "blocks", "ticks", "reason"]
RANK = {"PASS": 0, "FAIL": 1, "NO RESULT": 2}


def clean(text):
    return " ".join(text.replace("\t", " ").split())


def parse(log_path, out_path):
    planned = {}   # test -> row (dict), in log order
    mine = {}      # test -> my FAIL reason
    framework = {}  # test -> framework failure message
    with open(log_path, encoding="utf-8", errors="replace") as log:
        for line in log:
            line = line.rstrip("\n")
            m = PLANNED.search(line)
            if m:
                test, sid, level, blocks, _height = m.groups()
                planned.setdefault(test, {"test": test, "id": sid, "level": level, "status": "NO RESULT",
                                          "blocks": blocks, "ticks": "", "reason": ""})
                continue
            m = PASSED.search(line)
            if m:
                test, sid, level, blocks, ticks = m.groups()
                row = planned.setdefault(test, {"test": test, "id": sid, "level": level})
                row.update(status="PASS", blocks=blocks, ticks=ticks, reason="")
                continue
            m = FAILED.search(line)
            if m:
                test, sid, level, reason = m.groups()
                planned.setdefault(test, {"test": test, "id": sid, "level": level, "blocks": "", "ticks": ""})
                mine.setdefault(test, clean(reason))
                continue
            m = FRAMEWORK.search(line)
            if m:
                framework.setdefault(m.group(1), clean(m.group(2)))
    for test, row in planned.items():
        if row.get("status") == "PASS" and test not in framework:
            continue
        if test in mine or test in framework:
            row["status"] = "FAIL"
            row["ticks"] = ""
            row["reason"] = mine.get(test) or framework.get(test) or "failed"
        row.setdefault("status", "NO RESULT")
    with open(out_path, "w", encoding="utf-8") as out:
        out.write("\t".join(COLUMNS) + "\n")
        for row in planned.values():
            out.write("\t".join(str(row.get(c, "")) for c in COLUMNS) + "\n")
    print(f"{len(planned)} planned structure tests -> {out_path}", file=sys.stderr)
    return 0


def read_rows(paths):
    rows = {}
    for path in paths:
        try:
            with open(path, encoding="utf-8") as f:
                lines = f.read().splitlines()
        except OSError as e:
            print(f"cannot read {path}: {e}", file=sys.stderr)
            continue
        for line in lines:
            parts = line.split("\t")
            if not parts or parts[0] in ("", "test"):
                continue
            parts += [""] * (len(COLUMNS) - len(parts))
            row = dict(zip(COLUMNS, parts))
            old = rows.get(row["test"])
            # The same test in two files (shouldn't happen): keep the best result.
            if old is None or RANK.get(row["status"], 3) < RANK.get(old["status"], 3):
                rows[row["test"]] = row
    return list(rows.values())


def cell(row):
    if row is None:
        return ""
    if row["status"] == "PASS":
        try:
            return f"✅ {int(row['ticks']):,} ticks"
        except ValueError:
            return "✅"
    return "❌" if row["status"] == "FAIL" else "⚪"


def note(levels):
    for level in sorted(levels):
        row = levels[level]
        if row["status"] == "FAIL":
            reason = row["reason"] or "failed"
        elif row["status"] != "PASS":
            reason = "no result (server stopped before this test finished?)"
        else:
            continue
        if len(reason) > 140:
            reason = reason[:139].rstrip() + "…"
        return f"L{level}: " + reason.replace("|", "\\|")
    return ""


def table(paths):
    rows = read_rows(paths)
    structures = {}
    for row in rows:
        try:
            level = int(row["level"])
        except ValueError:
            continue
        structures.setdefault(row["id"], {})[level] = row
    max_level = max([3] + [lv for levels in structures.values() for lv in levels])
    built = sum(1 for r in rows if r["status"] == "PASS")
    full = [sid for sid, levels in structures.items() if all(r["status"] == "PASS" for r in levels.values())]

    def order(sid):
        return (sid in full, sid)

    print("## Structure builds")
    print()
    print(f"{built} of {len(rows)} structure levels built ({len(full)} structures fully built)")
    print()
    print("| Structure | " + " | ".join(f"L{i}" for i in range(1, max_level + 1)) + " | Notes |")
    print("|---|" + "---|" * max_level + "---|")
    for sid in sorted(structures, key=order):
        levels = structures[sid]
        cells = [cell(levels.get(i)) for i in range(1, max_level + 1)]
        print(f"| {sid} | " + " | ".join(cells) + f" | {note(levels)} |")
    return 0 if rows and built == len(rows) else 1


def main(argv):
    if len(argv) == 4 and argv[1] == "parse":
        return parse(argv[2], argv[3])
    if len(argv) >= 3 and argv[1] == "table":
        return table(argv[2:])
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))

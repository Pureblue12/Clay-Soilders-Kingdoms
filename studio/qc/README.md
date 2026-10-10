# Quality check (the automatic reviewer)

Every new structure version is checked by a reviewer before Carlton looks at it. Carlton asked for it to be fully
automatic and to judge by his taste. It never approves or rejects anything: only Carlton does that, in the Studio.

- **Automatic checks** (`src/qc/checks.ts`, no AI): floating blocks, doors and beds missing a half, fences that won't
  connect, too few blocks, too few detail blocks, one block everywhere, flat walls, a roof without overhang, a door on
  the back, block budget, height over builder reach, the buildability validator.
- Blocks listed in `meta.decorationBlocks` (`[x, y, z, kind]`: water, lily pads the mod adds in its decoration step,
  drawn in the Studio file only) don't count for buildability. For a file saved before that field existed, the review
  JSON can list the positions (`"decorationBlocks": [[x, y, z], ...]`) after checking them against the generator's
  `decorate()` calls.
- **Visual review** (an AI looking at renders): ten criteria scored 0 to 2, weighted by Carlton's past notes
  (`RUBRIC.md`, `src/qc/rubric.ts`). Research behind it: `RESEARCH.md`.
- **Verdict:** pass (score 70+, no "must" criterion at 0, no failed automatic check), revise (sent back with a list
  of fixes), or handed over (still failing after 3 rounds: Carlton sees it with the problems listed).
- **Reports:** `structures/<name>/qc.json` (one per checked version) and `structures/qc-index.json` (the newest per
  structure, for the dots in the list). Separate files: the version files and Carlton's reviews are never touched.
- **In the Studio:** a small coloured dot after a name in the list (green passed, amber being fixed, red over to you,
  hollow = a newer version isn't checked yet) and a collapsed "Quality check" section in the review panel
  (`src/ui/qc-panel.ts`, `qc-panel.css`).

## When it runs

Right after new versions land, not on a timer. Any agent that saves and pushes structure versions (batch threads,
the "Revise rejected structures" routine) then starts the check at once, naming what it saved: with the routine tool
`fire_trigger`, trigger `trig_019cSHKLt96sxzKPmGP7Gz7T`, text `check: <name> v<N>, <name> v<N>`, or with a
`send_message` to the quality-check session `session_01CM8GkfrQUtfGW23h7DTXSp`. The routine also runs once an hour
(the shortest schedule allowed) to catch anything that was missed.

## The loop (what the routine does)

```bash
cd studio
npm run qc -- todo                       # pending versions without a report
npm run qc -- brief <name>               # automatic checks, Carlton's notes, the rubric, last round's problems
npm run qc -- render <name>              # 8 views + one contact sheet in renders/qc/<name>/v<N>/
# look at <name>_v<N>_sheet.png (open a single view only when something needs a closer look)
# write review.json (format in RUBRIC.md)
npm run qc -- record <name> -v <N> --visual review.json --by claude-qc
```

- **pass:** nothing more. Carlton sees it in "Needs your review" with a green dot.
- **revise:** fix the problems in the generator that made it (`tools/batch*/`, `tools/make_batch*_structures.py`) or
  in the JSON, re-render, look, and save the next version with
  `npm run studio -- save draft.json --author claude-qc --changes "Quality check round N: <what changed>"`.
  Then check the new version the same way (same run), until it passes or reaches round 3.
- **handed over:** stop; the panel shows Carlton what is still wrong.

Rules:
- Never change a status, never call the review endpoint.
- Carlton's notes outrank the rubric. If he rejected something, the other routine ("Revise rejected structures")
  revises it; the quality check only looks at pending versions.
- Don't fail a structure for known viewer bugs (`RUBRIC.md`, "Known viewer problems").
- Commit only `studio/structures/`, `studio/tools`-style generator changes and nothing in the mod.

For big buildings soldiers use (keep, castle, town hall), `python3 tools/qc_reach.py <version.json>` flood-fills from
the ground outside and lists the standing places soldiers can't reach (roof tops show up too; judge by eye).

Run `npm run qc -- check <name|file.json>` while building to catch the automatic problems before saving.
`npm run qc -- status` lists the newest report of every structure.

## Cost and limits

- One contact sheet (about 1600 x 600) per round instead of eight separate pictures keeps the cost down. A
  structure that fails costs up to three rounds.
- It can't judge taste the way Carlton does; it's the same kind of AI that built the structure, so the fixed rubric
  and the automatic checks do most of the work.
- It can't see in-game problems (pathing, builders failing to place a block). The GameTests cover those.

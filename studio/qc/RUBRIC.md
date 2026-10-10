# Quality check rubric (the reviewer's checklist)

The reviewer scores every new version on the ten criteria below, 0 (no), 1 (partly) or 2 (yes), after looking at
the renders. The criteria, weights and pass mark live in code (`src/qc/rubric.ts`); this file says what each one means
and how to judge it. Background: `RESEARCH.md`. Building rules: `../STYLE.md` (it wins where they disagree).

**Pass** = score 70+ out of 100, no "must" criterion at 0, no failed automatic check. Otherwise the structure goes back
for fixes, up to 3 rounds; after that it goes to Carlton anyway with the problems listed.

## Carlton's taste comes first

Carlton is the only one who approves. Judge as he would. What his notes so far say (2026-10-10):

- **Detail, detail, detail.** His most common note is "not much in terms of detail" (bench, cottage, fire pit,
  visitors' camp, house v2 "a little more detail such as decorations"). Bare is the fastest way to a reject.
- **Effort and creativity.** "What is this minimal effort trash" (a 2-block signpost); "boring, add different dirt and
  blocks, be creative" (road); "look up examples online".
- **Grand when the role is grand.** The keep "should get bigger, more grand"; the windmill "too small, I'd like to see a
  bigger more detailed version"; "a saw mill style instead of this puny thing".
- **It must read as what it is.** "Can't even tell what's going on here."
- **No glitches.** Holes where there shouldn't be any, "a couch on the roof", fences that don't connect, logs on their
  side.
- **His reference picture** (`reference/carlton-timber-house.png`): a two-storey timber-frame house with a stone base
  and an arch, upper floors jutting out over the lower, a steep dark roof with a broken ridge line, a stone chimney,
  flower boxes and vines all over, lanterns, a small paddock with a fence. That is the look he likes. Small buildings
  can't have all of it, but every one should have some of it.

Before scoring, read his notes on the structure and its other levels (`npm run qc -- brief <name>` prints them). A
version that ignores one of his notes cannot pass "No mistakes".

## Known viewer problems: don't fail a structure for them

Until PR #5 is on `main`, the Studio draws every structure one block sunk into the ground and draws a bare `oak_log`
(no `axis`) lying on its side. Those are viewer bugs. Check `git log origin/main --oneline | grep -i "groundLayer\|stage"`
or read `src/render/scene.ts` (`groundLayer` default -1 means fixed). Score what the structure will look like in game.

## The criteria

| id | weight | must | Look for | Where to look |
|---|---|---|---|---|
| `detail` | 3 | yes | Small blocks wherever they belong: sills, shutters, flower boxes, lanterns, signs, barrels, tools, furniture inside. Compare with the reference picture. 0 = bare walls and nothing else; 1 = a few touches; 2 = rich on every side. | iso, iso_back, front |
| `artefacts` | 2 | yes | Holes, stray or floating blocks, furniture poking through the roof, stairs facing the wrong way, doors or beds missing a half, fences not connecting. Any one is a 0 if it's obvious, 1 if minor. | all views, floor plan |
| `purpose` | 2 | yes | Can you tell what it is in one look? A smithy has an anvil and a forge chimney, a farm has crops and a barn, a dock has a pier and boats or barrels. | iso, floor plan |
| `depth` | 2 | | Posts at corners and every 3 to 4 blocks, a stone plinth, a beam under the roof, things sticking out (sills, corbels, jetty). Shadows on every face. A face of one block at one depth = 0. | front, right, back |
| `roof` | 2 | | 45 degrees or steeper, one block of overhang all round, eaves above a beam, a slab ridge, upside-down stairs under the gable overhang, 2 roof blocks mixed. A roof flush with the walls = 0. | iso, front, right |
| `materials` | 2 | | Style A: cobblestone or stone brick base, spruce or dark oak frame, oak plank or white terracotta infill, dark roof. 3 to 5 main blocks. All one wood colour = 0. Clashing colours = 0. | iso, top |
| `shape` | 2 | | Not a plain box: a jetty, a lean-to, a porch, a dormer, a chimney, roof parts at two heights, some asymmetry. | iso, iso_back, top |
| `life` | 1 | | Flowers, leaves, vines at the base and in boxes, lanterns, a path, a fence or garden. | iso, slope |
| `scale` | 1 | | Soldiers are half a block tall: a cottage is about 5 x 7 with 3-high walls; landmarks clearly bigger than houses. Use the scale markers (player 1.8, soldier 0.5). | iso, front |
| `grandeur` | 1 | | Big and special enough for its role. A windmill has sails and a tall tower; a keep towers over houses; a saw mill has a saw, logs and a yard. | iso |

Small props (benches, signposts, posts) still get all ten scores: "shape" means an interesting silhouette (a hanging
sign on a chain, a bench with armrests and a flower pot), "scale" whether a soldier could use it.

## Writing the review

The review file is JSON (`npm run qc -- record <name> -v N --visual review.json`):

```json
{
  "scores": { "detail": 1, "artefacts": 2, "purpose": 2, "depth": 1, "roof": 0, "materials": 2, "shape": 1, "life": 0, "scale": 2, "grandeur": 1 },
  "notes": { "roof": "flush with the walls on the left and right" },
  "summary": "One sentence Carlton can read in the panel: what it is and how good it looks.",
  "problems": ["What to fix, most important first, concrete enough to act on", "..."],
  "views": ["sheet", "front"]
}
```

- `problems` is the brief for the next version: concrete ("add a cobblestone plinth under the front wall"), never
  vague ("make it better"). For a pass, list what would still make it better (it stays visible to Carlton).
- `summary` and `problems` show in the Studio's Quality check panel: plain words, no jargon.
- Be honest and strict: passing a weak build wastes Carlton's time, which is the whole point of the check.

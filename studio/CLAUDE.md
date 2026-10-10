# CLAUDE.md (Structure Studio)

A local, browser-based tool to view Minecraft block structures with the real block models and textures, review them
(approve / reject / notes, versions, side-by-side compare, rollback), and render headless screenshots so agents can
judge their own builds. It feeds the Clay Soldiers Kingdoms mod (`../`), but it is a separate Node project: it is not
part of the Gradle build and changes here never touch the mod.

Owner: Carlton. **Only Carlton approves.** Agents save versions (always `pending`) and never change a status.

## Run

```bash
cd studio
npm install
npm run dev                 # http://127.0.0.1:5174
npm run studio -- help      # CLI
npm test                    # unit tests (format, NBT, store)
npm run typecheck
```

Headless renders need Playwright's Chromium (`npx playwright install chromium` once; or set `STUDIO_CHROMIUM` to a
Chromium binary).

## Git sync (automatic)

The owner never types git commands for reviews (`src/server/gitsync.ts`):
- Every page load runs `git pull --rebase --autostash` in the repo (at most once per 10 s), so structures agents pushed
  to the branch the owner has checked out show up on refresh.
- Approve, reject, notes and rollback commit only that one version file (`Studio review: <name> vN <status>`) and push.
- The toast says what happened; failures (offline, conflict) stay local and show in red. `STUDIO_GIT=off` turns it off.

## Minecraft assets

Real textures come from the user's own game: a client jar (e.g. `1.21.1.jar`) or an extracted `assets/minecraft`
folder in `studio/minecraft/` (git-ignored, see its README). A jar is unpacked once into `.cache/mc/`. Never commit
Minecraft assets. Without assets everything runs with labelled placeholder textures and rough shapes, and the UI shows a
yellow banner.

## Structure format

One JSON file per version: `structures/<name>/<name>_v<N>.json`.

```json
{
  "format": "csr-studio-structure/1",
  "name": "house", "version": 2,
  "size": [7, 9, 9],
  "meta": { "status": "pending", "notes": "", "author": "claude", "created": "...", "description": "...",
            "changes": "what changed from the version before", "parent": 1, "reviewedAt": null, "tags": [] },
  "palette": { ".": "minecraft:air", "#": "minecraft:cobblestone", "s": "minecraft:spruce_stairs[facing=east,half=bottom,shape=straight]" },
  "layers": [ ["#######", "..."], ... ]
}
```

- `layers[y][z]` is a string; its characters are `x = 0..size.x-1`. `y = 0` is the bottom layer, `z = 0` the **front**
  (north, toward the easel in the mod). Facing values are Minecraft's (north = -z, east = +x).
- Palette keys are single characters. `minecraft:air` clears, `minecraft:structure_void` leaves the ground as it is
  (it is not written to NBT). Air does not dig into the test terrain in the viewer (the mod's builders never clear
  terrain either).
- `meta.groundLayer` (optional, default 0): the layer that is level with the top block of the ground.
- `meta.notes` are Carlton's review notes; `meta.changes` is the author's summary of the version.
- Versions are never overwritten (`Store.saveNewVersion` writes with the `wx` flag). The only edit to an existing
  version is its review metadata, from the UI. Rollback = a new version copying an old one.
- Converters: `export-nbt` writes a vanilla structure `.nbt` (DataVersion 3955, 1.21.1) the mod can load from
  `data/csr/structure/`; `import-nbt` reads one (block-entity data and entities are dropped).

## Agent workflow (the loop)

1. Read `STYLE.md` (and the brief in `../docs/STRUCTURES.md` if there is one).
2. Write the structure: in code with `samples/builder.ts` (see `samples/house.ts`), or start from
   `npm run studio -- template <name> x y z -o draft.json`.
3. `npm run studio -- validate draft.json` (format, block names and properties against the real assets, mod
   buildability).
4. `npm run studio -- render --file draft.json --views iso,front,right,back,top` and **look at every PNG** (Read the
   image files). Also try `--terrain slope` and `--layer single --y 1` for the floor plan.
5. Critique in writing: what looks flat, wrong, too big, unbuildable.
6. Fix, re-render, repeat until it looks good.
7. Save: `create draft.json` (new) or `save draft.json --changes "..."` (next version). It is `pending`.
8. `diff <name> 1 2` lists what changed. Tell Carlton which versions to look at; he approves in the UI. His
   reviews come back as commits ("Studio review: ..."): pull before revising.

## Architecture

- `src/format/` (shared, no Node imports): `structure.ts` (types, parse, validate, `fromBlocks`, `stringifyStructure`),
  `nbt.ts` (NBT reader/writer, structure <-> NBT), `modrules.ts` (mod buildability lists, copied from
  `../docs/STRUCTURES.md`).
- `src/server/`: `store.ts` (the library on disk), `assets.ts` (finds the jar/folder, unpacks, serves `/mc/...`),
  `api.ts` (HTTP API mounted in Vite by `vite.config.ts`).
- `src/render/` (browser): `blockmodels.ts` (blockstate variants/multipart, model parents, element baking, x/y
  rotation, uvlock), `special.ts` (code-drawn blocks: chest, bed, sign, banner, water, lava; fallback shapes),
  `blocks.ts` (texture atlas, alpha analysis into solid/cutout/translucent, tints, placeholder textures),
  `mesher.ts` (16-block chunks, hidden-face culling, per-vertex ambient occlusion), `world.ts` (World with many
  `Placement`s plus `Terrain`: flat, hills, slope, trees; layer filter), `viewer.ts` (three.js scene: sky dome, fog,
  sun with PCF soft shadows, three render passes, orbit + free-fly camera, presets, scale markers), `scene.ts`.
- `src/ui/`: the app (`index.html`, `app.ts`, `style.css`). `render.html` + `src/render-page.ts`: the headless page.
- `cli/`: `studio.ts` (commands), `render.ts` (starts Vite in-process, drives headless Chromium with Playwright).
- `src/world/generator.ts`: the phase-3 generator interface (kingdom growth preview). Not implemented.

## Decisions

- **three.js with our own Minecraft model baker, not deepslate:** we need shadows, sky, fog, terrain and our own
  culling/AO; deepslate's renderer doesn't do lighting or shadows, and the model format is small enough to bake
  ourselves.
- **Lighting:** Lambert materials, hemisphere + ambient + one directional sun with a 4096 shadow map fitted to the
  structure; vanilla-style AO baked into vertex colours. No tone mapping, so textures keep their colours.
- **Transparency:** each texture is classified by its alpha (all opaque / cut-out / partial) into solid, cutout
  (alpha test, casts shadows) and translucent (sorted, no depth write) passes.
- **Code-drawn blocks** (chests, beds, signs, banners) are approximations of the entity models; double chests show as
  two singles. Hanging signs, skulls, shulker boxes and other entity blocks use the fallback shape.
- **Approval guard:** the review endpoint needs the `X-Studio-UI: review` header the UI sends. That stops accidents,
  not a determined script: agents must simply never call it.
- **Terrain:** height 0 at the structure's centre; the structure keeps its own height, so on slopes it floats or
  sinks, which is the point (shows where grading is needed).
- **Not built yet:** structure rotation in a `Placement` (needed for phase 3), the kingdom generator, a diff
  highlight in compare mode.

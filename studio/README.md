# Structure Studio

See, review and approve Minecraft structures for Clay Soldiers Kingdoms without starting the game.

- 3D view with the **real Minecraft textures and block models** (from your own game files), ambient occlusion, sun
  and soft shadows, sky, see-through glass, leaves and water.
- A **library** with thumbnails and a status per structure: pending, approved, rejected.
- Big **Approve / Reject** buttons and a notes box. Every version is kept (`house_v1`, `house_v2`, ...), with
  **side-by-side compare** and **restore** (rollback).
- **Layer view** (one layer, up to a layer, top-down floor plan), **test ground** (superflat, rolling hills, steep
  slope, trees, up to 256 x 256), **free-fly** camera and a player / clay soldier **scale marker**.
- A **command line** for agents: list, validate, render screenshots, save versions, convert to and from the mod's
  `.nbt` structure files.

## Setup (once)

You need Node.js 20 or newer.

```bash
cd studio
npm install
npx playwright install chromium     # only needed for screenshots from the command line
```

**Minecraft textures:** copy your Minecraft client jar into `studio/minecraft/`. On Windows it is in
`%APPDATA%\.minecraft\versions\1.21.1\1.21.1.jar` (start 1.21.1 once in the launcher if the folder is missing).
The jar is never committed. Without it the studio still works, with labelled placeholder textures.

## Use

```bash
npm run dev
```

Open http://127.0.0.1:5174.

- Click a structure on the left. Drag to orbit, right-drag to pan, scroll to zoom; the buttons (or keys 1 to 7) jump
  to front, side, top and angled views.
- **Approve** or **Reject**, with a note on what to change. Agents read your notes.
- **Versions:** click one to view it; "Compare with ..." shows two side by side with one camera; "Restore as new
  version" copies an old version to the top (nothing is deleted).
- **Layers:** "Single" plus the slider shows one floor; "Top-down" shows the floor plan.
- **Ground:** pick a test terrain to see how the building sits on a slope. **Fly:** WASD, Space up, Shift down, mouse
  to look, Esc to stop. **Scale:** a player and a clay soldier next to the building.

## For agents: the command line

```bash
npm run studio -- help
npm run studio -- list
npm run studio -- validate draft.json
npm run studio -- render --file draft.json          # PNGs: iso, front, right, back, top
npm run studio -- create draft.json                 # first version
npm run studio -- save draft.json --changes "..."   # next version, always pending
npm run studio -- render house -v all               # renders/house/v1/..., renders/house/v2/...
npm run studio -- diff house 1 2
npm run studio -- export-nbt house -o house.nbt     # for the mod
npm run studio -- import-nbt ../common/src/main/resources/data/csr/structure/kingdom/batch1 --prefix mod_
```

The loop (render, critique, new version, re-render) and the file format are in `CLAUDE.md`; building rules in
`STYLE.md`.

## Samples

- `house` v1 and v2: v1 was the first attempt; v2 fixes what its renders showed (the roof hid the walls, the front
  was flat). Compare them in the UI.
- `keep`: a small stone keep with corbelled battlements and corner turrets.
- `wall`: a town wall module that joins end to end.

All three are pending: they are waiting for your review.

// Automatic quality checks: the rules a program can measure on the block grid (no pictures, no AI).
// Pure: no Node or browser imports, so the CLI, the tests and the page can all use it.
// The visual half of the quality check (looking at renders against RUBRIC.md) is done by the reviewer; see qc/README.md.
import { buildability } from '../format/modrules.ts';
import {
  isAir,
  shortName,
  toGrid,
  validateStructure,
  VOID_BLOCK,
  type BlockState,
  type StructureFile,
} from '../format/structure.ts';

export type QcLevel = 'fail' | 'warn' | 'info';

export interface QcIssue {
  level: QcLevel;
  check: string;
  message: string;
}

export interface QcStats {
  blocks: number;
  category: Category;
  budget: number;
  /** Share of small detail blocks (stairs, slabs, trapdoors, fences, lanterns, flowers...), 0..1. */
  detailRatio: number;
  /** Distinct block types (names, not states). */
  types: number;
  dominant: { name: string; share: number } | null;
  height: number;
  isBuilding: boolean;
}

export interface AutoResult {
  issues: QcIssue[];
  stats: QcStats;
}

export type Category = 'landmark' | 'common' | 'module' | 'prop';

/** Block budgets from docs/STRUCTURES.md ("Detail budget"). Only a warning: Carlton keeps asking for more detail. */
export const BUDGETS: Record<Category, number> = { landmark: 1800, common: 350, module: 60, prop: 30 };

/** Builders reach 8 above their feet; until scaffolding is verified in game, taller builds may not finish. */
export const REACH_HEIGHT = 9;

const DETAIL_RE =
  /(stairs|slab|trapdoor|fence|_wall$|button|carpet|lantern|torch|flower_pot|potted_|poppy|dandelion|tulip|orchid|allium|azure|oxeye|cornflower|lily|rose|peony|lilac|sunflower|leaves|vine|chain|sign|banner|pane|bars|candle|pressure_plate|campfire|barrel|chest|composter|anvil|grindstone|bell|_bed$|ladder|lever|bush|fern|short_grass|tall_grass|azalea|moss_carpet|hay_block|cauldron|smoker|furnace|crafting_table|loom|stonecutter|fletching|smithing|cartography|lectern|brewing|bookshelf|scaffolding|skull|head|pot$)/;

export function isDetailBlock(name: string): boolean {
  return DETAIL_RE.test(shortName(name));
}

const CONNECTS = (n: string) => n.endsWith('_fence') || n.endsWith('_pane') || n === 'glass_pane' || n === 'iron_bars' || n.endsWith('_wall');

const SOIL = new Set(['grass_block', 'dirt', 'coarse_dirt', 'podzol', 'rooted_dirt', 'mycelium', 'moss_block', 'mud', 'muddy_mangrove_roots', 'farmland']);
const FLOWERS = ['dandelion', 'poppy', 'blue_orchid', 'allium', 'azure_bluet', 'oxeye_daisy', 'cornflower', 'lily_of_the_valley',
  'torchflower', 'wither_rose', 'rose_bush', 'lilac', 'peony', 'sunflower', 'short_grass', 'tall_grass', 'fern', 'large_fern',
  'sweet_berry_bush', 'pink_petals', 'bush', 'firefly_bush', 'wildflowers', 'leaf_litter'];
/** Plants that need dirt or grass under them (not flower pots, not crops on farmland only, not cave plants). */
const isPlant = (n: string) => FLOWERS.includes(n) || n.endsWith('_tulip') || (n.endsWith('_sapling') && n !== 'mangrove_propagule');

export function categoryOf(s: StructureFile): Category {
  const tags = s.meta.tags ?? [];
  const n = s.name;
  // wall and palisade pieces (their gates too) are modules; a gate tag alone means a gatehouse
  if (tags.includes('module') || /^(wall|palisade|stone_wall)/.test(n)) return 'module';
  if (tags.includes('landmark') || tags.includes('keep') || tags.includes('gate') || /^keep/.test(n)) return 'landmark';
  if (/^(bench|signpost|notice_board|hitching_post|flower_bed|fire_pit|lamp_post|banner_pole|market_stall|well_small)/.test(n)) return 'prop';
  return 'common';
}

interface Grid {
  sx: number;
  sy: number;
  sz: number;
  at(x: number, y: number, z: number): BlockState | null; // null = air, void or outside
}

function grid(s: StructureFile): Grid {
  const g = toGrid(s);
  const [sx, sy, sz] = g.size;
  const solid = g.states.map((st) => (isAir(st.name) || st.name === VOID_BLOCK ? null : st));
  return {
    sx, sy, sz,
    at(x, y, z) {
      if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return null;
      return solid[g.cells[(y * sz + z) * sx + x]];
    },
  };
}

const NEIGHBOURS: [number, number, number][] = [];
for (let dx = -1; dx <= 1; dx++)
  for (let dy = -1; dy <= 1; dy++)
    for (let dz = -1; dz <= 1; dz++) {
      const d = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
      if (d === 1 || d === 2) NEIGHBOURS.push([dx, dy, dz]); // faces and edges, not corners
    }

const fmt = (p: [number, number, number]) => `(${p.join(', ')})`;
const list = (ps: [number, number, number][], max = 4) =>
  ps.slice(0, max).map(fmt).join(', ') + (ps.length > max ? ` and ${ps.length - max} more` : '');

/** extraDecorations: positions the reviewer knows are decoration-step blocks in a file saved before
 * meta.decorationBlocks existed. */
export function autoCheck(s: StructureFile, extraDecorations: [number, number, number][] = []): AutoResult {
  const issues: QcIssue[] = [];
  const add = (level: QcLevel, check: string, message: string) => issues.push({ level, check, message });

  for (const v of validateStructure(s)) add(v.level === 'error' ? 'fail' : 'info', 'validator', v.message);
  if (issues.some((i) => i.level === 'fail')) return { issues, stats: emptyStats(s) };

  const g = grid(s);
  const counts = new Map<string, number>();
  const built = new Map<string, number>(); // what the builders place: decoration-step blocks left out
  const deco = new Set([...(s.meta.decorationBlocks ?? []), ...extraDecorations].map(([x, y, z]) => `${x},${y},${z}`));
  const decoNames = new Set<string>();
  let blocks = 0;
  let detail = 0;
  for (let y = 0; y < g.sy; y++)
    for (let z = 0; z < g.sz; z++)
      for (let x = 0; x < g.sx; x++) {
        const b = g.at(x, y, z);
        if (!b) continue;
        blocks++;
        counts.set(b.name, (counts.get(b.name) ?? 0) + 1);
        if (deco.has(`${x},${y},${z}`)) decoNames.add(b.name);
        else built.set(b.name, (built.get(b.name) ?? 0) + 1);
        if (isDetailBlock(b.name)) detail++;
      }

  // ---- buildability (the mod's builders)
  const bld = buildability(built.keys());
  if (decoNames.size) add('info', 'buildability', `Drawn for the decoration step, not built: ${[...decoNames].join(', ')}.`);
  if (bld.broken.length) add('fail', 'buildability', `Builders can't place: ${bld.broken.join(', ')}.`);
  if (bld.noPassage.length) add('warn', 'buildability', `Soldiers can't open: ${bld.noPassage.join(', ')} (decoration only).`);
  if (bld.unknown.length) add('info', 'buildability', `Not yet placed by a builder GameTest: ${bld.unknown.join(', ')}.`);

  // ---- floating blocks: everything must connect to the bottom layer through touching faces or edges
  //      (edges, so a diagonal stair climbing a tower counts as held)
  const seen = new Uint8Array(g.sx * g.sy * g.sz);
  const idx = (x: number, y: number, z: number) => (y * g.sz + z) * g.sx + x;
  const queue: [number, number, number][] = [];
  for (let z = 0; z < g.sz; z++)
    for (let x = 0; x < g.sx; x++)
      if (g.at(x, 0, z)) {
        seen[idx(x, 0, z)] = 1;
        queue.push([x, 0, z]);
      }
  while (queue.length) {
    const [x, y, z] = queue.pop()!;
    for (const [dx, dy, dz] of NEIGHBOURS) {
      const nx = x + dx, ny = y + dy, nz = z + dz;
      if (!g.at(nx, ny, nz) || seen[idx(nx, ny, nz)]) continue;
      seen[idx(nx, ny, nz)] = 1;
      queue.push([nx, ny, nz]);
    }
  }
  const floating: [number, number, number][] = [];
  for (let y = 0; y < g.sy; y++)
    for (let z = 0; z < g.sz; z++)
      for (let x = 0; x < g.sx; x++) if (g.at(x, y, z) && !seen[idx(x, y, z)]) floating.push([x, y, z]);
  // The mod builds layer 0 ON TOP of the ground (BuildSitePlanner), so only layer 0 rests on it.
  if (floating.length) add('fail', 'floating', `${floating.length} block(s) touch nothing that leads down to layer 0 (in game, layer 0 sits on top of the ground, so these hang in the air): ${list(floating)}.`);

  // ---- two-part blocks: doors need both halves, beds need head and foot
  const halfDoors: [number, number, number][] = [];
  const halfBeds: [number, number, number][] = [];
  const doors: [number, number, number][] = [];
  const step: Record<string, [number, number]> = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] };
  for (let y = 0; y < g.sy; y++)
    for (let z = 0; z < g.sz; z++)
      for (let x = 0; x < g.sx; x++) {
        const b = g.at(x, y, z);
        if (!b) continue;
        const n = shortName(b.name);
        if (n.endsWith('_door')) {
          const lower = b.properties.half !== 'upper';
          if (lower) doors.push([x, y, z]);
          const other = g.at(x, lower ? y + 1 : y - 1, z);
          if (!other || other.name !== b.name) halfDoors.push([x, y, z]);
        }
        if (n.endsWith('_bed')) {
          const d = step[b.properties.facing ?? 'north'] ?? [0, -1];
          const sign = b.properties.part === 'head' ? -1 : 1;
          const other = g.at(x + d[0] * sign, y, z + d[1] * sign);
          if (!other || other.name !== b.name) halfBeds.push([x, y, z]);
        }
      }
  if (halfDoors.length) add('fail', 'two-part', `Door with a missing half at ${list(halfDoors)}.`);
  if (halfBeds.length) add('fail', 'two-part', `Bed with a missing half at ${list(halfBeds)}.`);

  // ---- plants need soil under them (a flower on a slab or planks can't be placed in game); the bottom layer stands on the ground
  const unrooted: [number, number, number][] = [];
  for (let y = 1; y < g.sy; y++)
    for (let z = 0; z < g.sz; z++)
      for (let x = 0; x < g.sx; x++) {
        const b = g.at(x, y, z);
        if (!b || !isPlant(shortName(b.name)) || deco.has(`${x},${y},${z}`)) continue;
        const below = g.at(x, y - 1, z);
        const bn = below ? shortName(below.name) : '';
        if (SOIL.has(bn) || (below && below.name === b.name)) continue; // upper half of a tall plant
        if (!below && y === (s.meta.groundLayer ?? -1) + 1) continue; // on the grass, next to a layer that is flush with it
        unrooted.push([x, y, z]);
      }
  if (unrooted.length) add('fail', 'plant-soil', `Plant with no dirt or grass under it (can't be placed in game; use a flower pot, or dirt under it) at ${list(unrooted)}.`);

  // ---- a hanging lantern needs a block above it to hang from, or it drops in game
  const loose: [number, number, number][] = [];
  for (let y = 0; y < g.sy; y++)
    for (let z = 0; z < g.sz; z++)
      for (let x = 0; x < g.sx; x++) {
        const b = g.at(x, y, z);
        if (b && /lantern$/.test(shortName(b.name)) && b.properties.hanging === 'true' && !g.at(x, y + 1, z)) loose.push([x, y, z]);
      }
  if (loose.length) add('fail', 'hanging', `Hanging lantern with nothing above it (it drops in game) at ${list(loose)}.`);

  // ---- fences, panes and walls without their connections look like lone posts in the Studio (Carlton's farm note)
  const lone: [number, number, number][] = [];
  for (let y = 0; y < g.sy; y++)
    for (let z = 0; z < g.sz; z++)
      for (let x = 0; x < g.sx; x++) {
        const b = g.at(x, y, z);
        if (!b || !CONNECTS(shortName(b.name))) continue;
        const p = b.properties;
        const hasSides = ['north', 'south', 'east', 'west'].some((k) => p[k] && p[k] !== 'false' && p[k] !== 'none');
        if (hasSides) continue;
        const nb = [g.at(x + 1, y, z), g.at(x - 1, y, z), g.at(x, y, z + 1), g.at(x, y, z - 1)];
        if (nb.some((o) => o && (CONNECTS(shortName(o.name)) || !isDetailBlock(o.name)))) lone.push([x, y, z]);
      }
  if (lone.length) {
    add('warn', 'connections', `${lone.length} fence/pane/wall block(s) have no north/east/south/west set although a neighbour would connect, so they show as lone posts: ${list(lone, 3)}.`);
  }

  // ---- size and budget
  const category = categoryOf(s);
  const budget = BUDGETS[category];
  if (blocks > budget) add('warn', 'budget', `${blocks} blocks, over the ${category} budget of ${budget} (docs/STRUCTURES.md). A warning only.`);
  if (g.sy > REACH_HEIGHT && category !== 'landmark') {
    add('warn', 'height', `${g.sy} layers tall: builders reach ${REACH_HEIGHT} without scaffolding, which isn't verified in game yet.`);
  }

  // ---- is it a building? (a door, or a footprint of 5x5 and 4 tall)
  const isBuilding = doors.length > 0 || (g.sx >= 5 && g.sz >= 5 && g.sy >= 4 && category !== 'module');
  const detailRatio = blocks ? detail / blocks : 0;
  const sorted = [...counts.entries()].sort((a, b) => b[1] - a[1]);
  const dominant = sorted.length ? { name: shortName(sorted[0][0]), share: sorted[0][1] / blocks } : null;

  // Carlton on a 2-block signpost: "minimal effort trash".
  const minBlocks = { prop: 10, common: 50, module: 15, landmark: 200 }[category];
  if (blocks < minBlocks) add(category === 'prop' || blocks < minBlocks / 2 ? 'fail' : 'warn', 'too-small', `Only ${blocks} blocks: too bare for a ${category} (at least ${minBlocks}). Carlton wants more detail, not less.`);

  if (blocks >= 30) {
    // Carlton's most common note: "not much detail".
    if (detailRatio < 0.08) add('fail', 'detail', `Only ${pct(detailRatio)} of the blocks are detail blocks (stairs, slabs, trapdoors, fences, lanterns, flowers...). Aim for 20 %+.`);
    else if (detailRatio < 0.18) add('warn', 'detail', `${pct(detailRatio)} detail blocks: on the bare side, aim for 20 %+.`);
    if (counts.size < 5) add('warn', 'variety', `Only ${counts.size} block types: mix materials (STYLE.md palette).`);
    if (dominant && dominant.share > 0.55) add('warn', 'variety', `${dominant.name} is ${pct(dominant.share)} of the build: break it up with a second material.`);
  }

  if (isBuilding) {
    // doors belong on the front (z = 0 side)
    if (doors.length && !doors.some(([, , z]) => z <= Math.floor(g.sz / 2))) add('warn', 'door', 'No door on the front half (z = 0 is the front, toward the easel).');
    for (const f of flatFaces(g)) add('warn', 'flat-wall', f);
    const roof = roofOverhang(g);
    if (roof) add('warn', 'roof', roof);
  }

  return {
    issues,
    stats: { blocks, category, budget, detailRatio, types: counts.size, dominant, height: g.sy, isBuilding },
  };
}

function emptyStats(s: StructureFile): QcStats {
  const category = categoryOf(s);
  return { blocks: 0, category, budget: BUDGETS[category], detailRatio: 0, types: 0, dominant: null, height: s.size[1], isBuilding: false };
}

const pct = (r: number) => `${Math.round(r * 100)} %`;

/**
 * A face is flat when, seen straight on, nearly every visible block sits at the same depth and is the same block:
 * no posts, sills, beams or anything sticking out (STYLE.md "never a flat face of one block").
 */
function flatFaces(g: Grid): string[] {
  const out: string[] = [];
  const sides: { name: string; w: number; d: number; cell: (u: number, y: number, t: number) => BlockState | null }[] = [
    { name: 'front', w: g.sx, d: g.sz, cell: (u, y, t) => g.at(u, y, t) },
    { name: 'back', w: g.sx, d: g.sz, cell: (u, y, t) => g.at(u, y, g.sz - 1 - t) },
    { name: 'left', w: g.sz, d: g.sx, cell: (u, y, t) => g.at(t, y, u) },
    { name: 'right', w: g.sz, d: g.sx, cell: (u, y, t) => g.at(g.sx - 1 - t, y, u) },
  ];
  for (const side of sides) {
    const depths = new Map<number, number>();
    const mats = new Map<string, number>();
    let area = 0;
    for (let y = 1; y < g.sy; y++)
      for (let u = 0; u < side.w; u++)
        for (let t = 0; t < side.d; t++) {
          const b = side.cell(u, y, t);
          if (!b) continue;
          area++;
          depths.set(t, (depths.get(t) ?? 0) + 1);
          mats.set(b.name, (mats.get(b.name) ?? 0) + 1);
          break;
        }
    if (area < 12) continue;
    const topDepth = Math.max(...depths.values()) / area;
    const topMat = Math.max(...mats.values()) / area;
    if (topDepth >= 0.9 && topMat >= 0.75) {
      out.push(`The ${side.name} is flat: ${pct(topDepth)} of it at one depth and ${pct(topMat)} one block. Add posts, sills, a beam, flower boxes.`);
    }
  }
  return out;
}

/** A stair roof should reach at least one block past the walls on two opposite sides (STYLE.md: one block overhang). */
function roofOverhang(g: Grid): string | null {
  if (g.sy < 5) return null;
  const wallY = 1;
  let wx0 = Infinity, wx1 = -Infinity, wz0 = Infinity, wz1 = -Infinity;
  for (let z = 0; z < g.sz; z++)
    for (let x = 0; x < g.sx; x++) {
      const b = g.at(x, wallY, z);
      if (!b || isDetailBlock(b.name)) continue;
      wx0 = Math.min(wx0, x); wx1 = Math.max(wx1, x); wz0 = Math.min(wz0, z); wz1 = Math.max(wz1, z);
    }
  if (wx0 === Infinity) return null;
  let rx0 = Infinity, rx1 = -Infinity, rz0 = Infinity, rz1 = -Infinity, roofBlocks = 0;
  for (let y = 3; y < g.sy; y++)
    for (let z = 0; z < g.sz; z++)
      for (let x = 0; x < g.sx; x++) {
        const b = g.at(x, y, z);
        if (!b || !/(stairs|slab)$/.test(b.name)) continue;
        roofBlocks++;
        rx0 = Math.min(rx0, x); rx1 = Math.max(rx1, x); rz0 = Math.min(rz0, z); rz1 = Math.max(rz1, z);
      }
  if (roofBlocks < 8) return null;
  const overX = rx0 < wx0 && rx1 > wx1;
  const overZ = rz0 < wz0 && rz1 > wz1;
  if (overX || overZ) return null;
  return 'The roof does not reach past the walls on any two opposite sides: give it one block of overhang.';
}

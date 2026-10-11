// The promote step: copies the structures Carlton approved in the Studio into the mod.
//
// `promote.json` says which mod structure each approved Studio structure becomes (id, tier, zone, role, ...). For each
// entry the latest APPROVED version of every level is exported (pending and rejected versions never are):
//   - geometry  -> ../common/src/main/resources/data/csr/structure/kingdom/studio/<level>.nbt
//   - metadata  -> ../common/src/main/resources/data/csr/csr/structures/<id>.json   (docs/STRUCTURES.md, pipeline)
//   - decoration-step blocks (water, lily pads, ...) -> ../common/src/main/resources/data/csr/csr/decoration/<id>.json
// Builders can't place decoration-step blocks, so they are left out of the NBT (air) and listed in the sidecar instead.
// On a structure whose layer 0 is level with the ground (meta.groundLayer 0), air in layer 0 becomes structure void
// ("leave the ground as it is") and the metadata's ground_level is 1 (layer 1 rests on the ground).
import fs from 'node:fs';
import path from 'node:path';
import { Store, STUDIO_ROOT } from '../src/server/store.ts';
import { structureToNbt } from '../src/format/nbt.ts';
import {
  AIR_BLOCK, VOID_BLOCK, blockStateToString, fromBlocks, isAir, parseBlockState, shortName, toGrid, type StructureFile,
} from '../src/format/structure.ts';
import { BROKEN, buildability } from '../src/format/modrules.ts';

const MOD_DATA = path.join(STUDIO_ROOT, '..', 'common', 'src', 'main', 'resources', 'data', 'csr');
const NBT_DIR = path.join(MOD_DATA, 'structure', 'kingdom', 'studio');
const META_DIR = path.join(MOD_DATA, 'csr', 'structures');
const DECORATION_DIR = path.join(MOD_DATA, 'csr', 'decoration');
/** Builders' height without scaffolding (docs/SCAFFOLDING.md): taller structures need scaffold in game. */
const NO_SCAFFOLD_HEIGHT = 9;
/** Blocks that only make sense with the decoration step's water: left out with it. */
const NEEDS_WATER = new Set(['lily_pad']);

interface PromoteEntry {
  id: string;
  name: string;
  tier: number;
  zone: string;
  role: string;
  terrain: string;
  batch?: number;
  variant_group?: string;
  levels: string[];
}
interface Fix { level: string; pos: [number, number, number]; block: string; why: string }
interface PromoteFile { structures: PromoteEntry[]; fixes?: Fix[] }

export interface PromoteRow {
  id: string;
  tier: number;
  size: [number, number, number];
  height: number;
  versions: string[];
  groundLayer: number;
  decoration: number;
  fixes: number;
  unknown: string[];
}

/** The latest approved version of a Studio structure, or null. */
function latestApproved(store: Store, name: string): StructureFile | null {
  for (const v of [...store.versions(name)].reverse()) {
    const s = store.load(name, v);
    if (s.meta.status === 'approved') return s;
  }
  return null;
}

/** Highest layer with any block in it, plus one. */
function usedHeight(s: StructureFile): number {
  const g = toGrid(s);
  const [sx, sy, sz] = g.size;
  for (let y = sy - 1; y >= 0; y--)
    for (let i = 0; i < sx * sz; i++) {
      const n = g.states[g.cells[y * sx * sz + i]].name;
      if (!isAir(n) && n !== VOID_BLOCK) return y + 1;
    }
  return 0;
}

/** Which side the outside door is on: the door nearest the edge of the box. Left/right as seen from the front. */
function doorSide(s: StructureFile): string | null {
  const g = toGrid(s);
  const [sx, sy, sz] = g.size;
  let best: { d: number; side: string } | null = null;
  for (let y = 0; y < sy; y++)
    for (let z = 0; z < sz; z++)
      for (let x = 0; x < sx; x++) {
        const st = g.states[g.cells[(y * sz + z) * sx + x]];
        if (!st.name.endsWith('_door') || st.properties.half === 'upper') continue;
        const sides: [number, string][] = [[z, 'front'], [sz - 1 - z, 'back'], [sx - 1 - x, 'left'], [x, 'right']];
        for (const [d, side] of sides) if (!best || d < best.d) best = { d, side };
      }
  return best ? best.side : null;
}

interface Exported { file: StructureFile; decoration: { pos: [number, number, number]; kind: string; block: string }[]; fixes: Fix[] }

/** Applies fixes, takes decoration-step blocks out and turns sunken layer-0 air into structure void. */
function prepare(s: StructureFile, fixes: Fix[]): Exported {
  const g = toGrid(s);
  const [sx, sy, sz] = g.size;
  const cell = (x: number, y: number, z: number) => (y * sz + z) * sx + x;
  const blocks = Array.from(g.cells, (i) => g.stateStrings[i]);
  const applied: Fix[] = [];
  for (const f of fixes.filter((f) => f.level === s.name)) {
    const [x, y, z] = f.pos;
    if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) throw new Error(`fix outside ${s.name}: ${f.pos}`);
    blocks[cell(x, y, z)] = blockStateToString(parseBlockState(f.block));
    applied.push(f);
  }
  const marked = new Map((s.meta.decorationBlocks ?? []).map(([x, y, z, kind]) => [`${x},${y},${z}`, kind]));
  const decoration: Exported['decoration'] = [];
  for (let y = 0; y < sy; y++)
    for (let z = 0; z < sz; z++)
      for (let x = 0; x < sx; x++) {
        const state = blocks[cell(x, y, z)];
        const n = shortName(parseBlockState(state).name);
        const kind = marked.get(`${x},${y},${z}`)
          ?? (BROKEN.has(n) || NEEDS_WATER.has(n) || n.startsWith('potted_') ? n : null);
        if (kind && !isAir(parseBlockState(state).name)) {
          decoration.push({ pos: [x, y, z], kind, block: state });
          blocks[cell(x, y, z)] = AIR_BLOCK;
        }
      }
  if ((s.meta.groundLayer ?? -1) === 0)
    for (let z = 0; z < sz; z++)
      for (let x = 0; x < sx; x++) if (isAir(parseBlockState(blocks[cell(x, 0, z)]).name)) blocks[cell(x, 0, z)] = VOID_BLOCK;
  const file = fromBlocks(s.name, s.version, s.size, (x, y, z) => blocks[cell(x, y, z)], s.meta);
  return { file, decoration, fixes: applied };
}

function writeJson(file: string, data: unknown) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(data, null, 2) + '\n');
}

/** Exports every promotable entry of promote.json. Returns one row per exported structure and the skipped ones. */
export function promote(store: Store, opts: { dryRun?: boolean; only?: string[] } = {}) {
  const config = JSON.parse(fs.readFileSync(path.join(STUDIO_ROOT, 'promote.json'), 'utf8')) as PromoteFile;
  const rows: PromoteRow[] = [];
  const skipped: string[] = [];
  for (const entry of config.structures) {
    if (opts.only?.length && !opts.only.some((o) => entry.id === o || entry.id === `csr:${o}`)) continue;
    const approved = entry.levels.map((l) => ({ name: l, s: latestApproved(store, l) }));
    const missing = approved.filter((a) => !a.s).map((a) => a.name);
    if (missing.length) {
      skipped.push(`${entry.id}: no approved version of ${missing.join(', ')}`);
      continue;
    }
    const levels = approved.map((a) => a.s!);
    const size = levels[0].size;
    if (levels.some((l) => l.size.join() !== size.join())) {
      skipped.push(`${entry.id}: levels differ in size (${levels.map((l) => `${l.name} ${l.size.join('x')}`).join(', ')})`);
      continue;
    }
    const groundLayer = levels[0].meta.groundLayer ?? -1;
    const exported = levels.map((l) => prepare(l, config.fixes ?? []));
    const idPath = entry.id.replace(/^csr:/, '');
    const files = levels.map((l) => `csr:kingdom/studio/${l.name}`);
    const door = doorSide(levels[0]);
    const meta: Record<string, unknown> = {
      id: entry.id,
      name: entry.name,
      design: 'ready',
      tier: entry.tier,
      zone: entry.zone,
      role: entry.role,
      footprint: size,
      cost: 'auto',
      ...(entry.variant_group ? { variant_group: entry.variant_group } : {}),
      structure: files[0],
      ...(files.length > 1 ? { levels: files } : {}),
      ground_level: groundLayer + 1,
      terrain: entry.terrain,
      ...(entry.batch ? { batch: entry.batch } : {}),
      ...(door ? { door_side: door } : {}),
      studio: Object.fromEntries(levels.map((l) => [l.name, l.version])),
    };
    // Each level lists only the decoration blocks new in it (levels build on each other).
    const seen = new Set<string>();
    const decoLevels: Record<string, unknown[]> = {};
    exported.forEach((e, i) => {
      decoLevels[String(i + 1)] = e.decoration
        .filter((d) => !seen.has(`${d.pos}|${d.block}`))
        .map((d) => ({ pos: d.pos, kind: d.kind, block: d.block }));
      e.decoration.forEach((d) => seen.add(`${d.pos}|${d.block}`));
    });
    const decorationCount = Object.values(decoLevels).reduce((n, l) => n + l.length, 0);
    if (!opts.dryRun) {
      fs.mkdirSync(NBT_DIR, { recursive: true });
      exported.forEach((e) => fs.writeFileSync(path.join(NBT_DIR, `${e.file.name}.nbt`), structureToNbt(e.file)));
      writeJson(path.join(META_DIR, `${idPath}.json`), meta);
      const sidecar = path.join(DECORATION_DIR, `${idPath}.json`);
      if (decorationCount) {
        writeJson(sidecar, {
          structure: entry.id,
          note: 'What the decoration step (roadmap 5a) must add after builders finish each level; nothing here is placed by builders. Written by the Studio promote step.',
          levels: decoLevels,
        });
      } else if (fs.existsSync(sidecar)) fs.rmSync(sidecar);
    }
    const allBlocks = exported.flatMap((e) => Object.values(e.file.palette));
    rows.push({
      id: entry.id,
      tier: entry.tier,
      size,
      height: Math.max(...exported.map((e) => usedHeight(e.file))),
      versions: levels.map((l) => `${l.name} v${l.version}`),
      groundLayer,
      decoration: decorationCount,
      fixes: exported.reduce((n, e) => n + e.fixes.length, 0),
      unknown: buildability(allBlocks.map((b) => parseBlockState(b).name)).unknown,
    });
  }
  return { rows, skipped, noScaffoldHeight: NO_SCAFFOLD_HEIGHT };
}

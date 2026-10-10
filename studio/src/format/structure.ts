// The studio structure format: one plain JSON file per structure version.
// Shared by the browser, the dev server and the CLI, so it must not import Node modules.
//
// {
//   "format": "csr-studio-structure/1",
//   "name": "keep", "version": 2,
//   "size": [x, y, z],
//   "meta": { "status": "pending", "notes": "", "author": "claude", "created": "2026-10-10", ... },
//   "palette": { ".": "minecraft:air", "#": "minecraft:cobblestone", "s": "minecraft:oak_stairs[facing=north]" },
//   "layers": [ ["#####", "#...#", ...], ... ]   // layers[y][z][x], y = 0 is the bottom layer, z = 0 the FRONT row
// }

export const FORMAT_ID = 'csr-studio-structure/1';
export const STATUSES = ['pending', 'approved', 'rejected'] as const;
export type Status = (typeof STATUSES)[number];

/** What happens next with a structure, shown on the page. Worked out from the latest version and work.json. */
export const STAGES = ['review', 'queued', 'revising', 'approved'] as const;
export type Stage = (typeof STAGES)[number];
export const STAGE_LABELS: Record<Stage, string> = {
  review: 'Needs your review', // latest version pending
  queued: 'Queued for revision', // latest version rejected, nobody working on it yet
  revising: 'Revising', // latest version rejected, an agent said it is working on it (work.json)
  approved: 'Approved', // latest version approved
};

/** Palette value meaning "leave whatever is there" (not written to NBT). */
export const VOID_BLOCK = 'minecraft:structure_void';
export const AIR_BLOCK = 'minecraft:air';

export interface StructureMeta {
  status: Status;
  notes: string;
  author: string;
  created: string; // ISO date or date-time
  description?: string;
  /** What the author changed in this version (written by the agent or person who saved it). */
  changes?: string;
  /** Version this one was derived from (null for v1). */
  parent?: number | null;
  /** Set when the status last changed through the review UI. */
  reviewedAt?: string | null;
  tags?: string[];
  /** Blocks drawn only for the viewer that the mod adds in its decoration step, not the builders (water, lily pads):
   * [x, y, z, kind]. A promote step leaves them out of the NBT; the quality check doesn't count them as built. */
  decorationBlocks?: [number, number, number, string][];
}

export interface StructureFile {
  format: string;
  name: string;
  version: number;
  size: [number, number, number];
  meta: StructureMeta;
  palette: Record<string, string>;
  layers: string[][];
}

export interface BlockState {
  name: string; // always namespaced, e.g. minecraft:oak_stairs
  properties: Record<string, string>;
}

/** "oak_stairs[facing=north,half=bottom]" -> { name: "minecraft:oak_stairs", properties: {...} } */
export function parseBlockState(text: string): BlockState {
  const s = text.trim();
  const m = /^([a-z0-9_.-]+:)?([a-z0-9_./-]+)(?:\[(.*)\])?$/.exec(s);
  if (!m) throw new Error(`not a block state: "${text}"`);
  const name = (m[1] ?? 'minecraft:') + m[2];
  const properties: Record<string, string> = {};
  if (m[3] !== undefined && m[3].trim() !== '') {
    for (const part of m[3].split(',')) {
      const kv = /^\s*([a-z0-9_]+)\s*=\s*([a-z0-9_]+)\s*$/.exec(part);
      if (!kv) throw new Error(`bad property "${part}" in "${text}"`);
      properties[kv[1]] = kv[2];
    }
  }
  return { name, properties };
}

export function blockStateToString(b: BlockState): string {
  const keys = Object.keys(b.properties).sort();
  if (keys.length === 0) return b.name;
  return `${b.name}[${keys.map((k) => `${k}=${b.properties[k]}`).join(',')}]`;
}

export function shortName(name: string): string {
  return name.startsWith('minecraft:') ? name.slice(10) : name;
}

export interface ValidationIssue {
  level: 'error' | 'warning';
  message: string;
}

const NAME_RE = /^[a-z0-9_]+$/;

/** Checks the file's shape. Block names are checked against real assets by the CLI when they're available. */
export function validateStructure(data: unknown): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  const err = (message: string) => issues.push({ level: 'error', message });
  const warn = (message: string) => issues.push({ level: 'warning', message });
  if (typeof data !== 'object' || data === null) {
    err('the file is not a JSON object');
    return issues;
  }
  const d = data as Partial<StructureFile>;
  if (d.format !== FORMAT_ID) err(`"format" must be "${FORMAT_ID}"`);
  if (typeof d.name !== 'string' || !NAME_RE.test(d.name)) err('"name" must be lowercase letters, digits and _');
  if (!Number.isInteger(d.version) || (d.version as number) < 1) err('"version" must be a whole number from 1');
  const size = d.size;
  if (!Array.isArray(size) || size.length !== 3 || !size.every((n) => Number.isInteger(n) && n >= 1 && n <= 256)) {
    err('"size" must be [x, y, z], each 1..256');
    return issues;
  }
  const meta = d.meta;
  if (!meta || typeof meta !== 'object') err('"meta" is missing');
  else {
    if (!STATUSES.includes(meta.status as Status)) err(`"meta.status" must be one of ${STATUSES.join(', ')}`);
    if (typeof meta.notes !== 'string') err('"meta.notes" must be a string');
    if (typeof meta.author !== 'string' || meta.author === '') err('"meta.author" is required');
    if (typeof meta.created !== 'string' || Number.isNaN(Date.parse(meta.created))) err('"meta.created" must be a date');
  }
  const palette = d.palette;
  if (!palette || typeof palette !== 'object') {
    err('"palette" is missing');
    return issues;
  }
  for (const [key, value] of Object.entries(palette)) {
    if ([...key].length !== 1 || /\s/.test(key)) err(`palette key "${key}" must be exactly one non-space character`);
    try {
      parseBlockState(String(value));
    } catch (e) {
      err(`palette "${key}": ${(e as Error).message}`);
    }
  }
  const [sx, sy, sz] = size;
  const layers = d.layers;
  if (!Array.isArray(layers)) {
    err('"layers" is missing');
    return issues;
  }
  if (layers.length !== sy) err(`expected ${sy} layers (size y), found ${layers.length}`);
  const used = new Set<string>();
  layers.forEach((layer, y) => {
    if (!Array.isArray(layer) || layer.length !== sz) {
      err(`layer y=${y}: expected ${sz} rows (size z), found ${Array.isArray(layer) ? layer.length : 'none'}`);
      return;
    }
    layer.forEach((row, z) => {
      const chars = typeof row === 'string' ? [...row] : [];
      if (chars.length !== sx) err(`layer y=${y} row z=${z}: expected ${sx} characters (size x), found ${chars.length}`);
      chars.forEach((c, x) => {
        if (!(c in palette)) err(`layer y=${y} row z=${z} x=${x}: "${c}" is not in the palette`);
        used.add(c);
      });
    });
  });
  for (const key of Object.keys(palette)) if (!used.has(key)) warn(`palette "${key}" (${palette[key]}) is never used`);
  if (sy > 28) warn(`height ${sy} is over the mod's 28-block cap (D57)`);
  return issues;
}

/** A decoded structure: a flat array of palette indices plus the parsed palette. */
export interface StructureGrid {
  size: [number, number, number];
  states: BlockState[]; // index -> state
  stateStrings: string[];
  cells: Uint16Array; // index = (y * sz + z) * sx + x
}

export function toGrid(s: StructureFile): StructureGrid {
  const [sx, sy, sz] = s.size;
  const keys = Object.keys(s.palette);
  const keyIndex = new Map(keys.map((k, i) => [k, i]));
  const states = keys.map((k) => parseBlockState(s.palette[k]));
  const cells = new Uint16Array(sx * sy * sz);
  for (let y = 0; y < sy; y++)
    for (let z = 0; z < sz; z++) {
      const row = [...s.layers[y][z]];
      for (let x = 0; x < sx; x++) cells[(y * sz + z) * sx + x] = keyIndex.get(row[x]) ?? 0;
    }
  return { size: [sx, sy, sz], states, stateStrings: states.map(blockStateToString), cells };
}

export function isAir(name: string): boolean {
  return name === AIR_BLOCK || name === 'minecraft:cave_air' || name === 'minecraft:void_air';
}

const KEY_CHARS =
  '#=+-*@%&$!?~^:;,<>/|ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789{}()"\'`\\ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ';

/** Builds a structure file from a block lookup. Air gets ".", structure void "_". Used by importers and generators. */
export function fromBlocks(
  name: string,
  version: number,
  size: [number, number, number],
  get: (x: number, y: number, z: number) => string,
  meta: StructureMeta,
): StructureFile {
  const [sx, sy, sz] = size;
  const palette: Record<string, string> = {};
  const byState = new Map<string, string>();
  let next = 0;
  const keyFor = (state: string): string => {
    const norm = blockStateToString(parseBlockState(state));
    let key = byState.get(norm);
    if (key) return key;
    if (isAir(norm)) key = '.';
    else if (norm === VOID_BLOCK) key = '_';
    else {
      while (next < KEY_CHARS.length && Object.prototype.hasOwnProperty.call(palette, KEY_CHARS[next])) next++;
      if (next >= KEY_CHARS.length) throw new Error('more distinct block states than palette characters');
      key = KEY_CHARS[next++];
    }
    byState.set(norm, key);
    palette[key] = norm;
    return key;
  };
  const layers: string[][] = [];
  for (let y = 0; y < sy; y++) {
    const layer: string[] = [];
    for (let z = 0; z < sz; z++) {
      let row = '';
      for (let x = 0; x < sx; x++) row += keyFor(get(x, y, z));
      layer.push(row);
    }
    layers.push(layer);
  }
  return { format: FORMAT_ID, name, version, size, meta, palette, layers };
}

/** Counts non-air, non-void blocks by state name. */
export function blockCounts(s: StructureFile): Map<string, number> {
  const counts = new Map<string, number>();
  for (const layer of s.layers)
    for (const row of layer)
      for (const c of row) {
        const name = parseBlockState(s.palette[c] ?? AIR_BLOCK).name;
        if (isAir(name) || name === VOID_BLOCK) continue;
        counts.set(name, (counts.get(name) ?? 0) + 1);
      }
  return counts;
}

/** JSON with one row string per line, so diffs and agents can read layers. */
export function stringifyStructure(s: StructureFile): string {
  const ordered: StructureFile = {
    format: s.format,
    name: s.name,
    version: s.version,
    size: s.size,
    meta: s.meta,
    palette: s.palette,
    layers: s.layers,
  };
  const ind = '  ';
  const lines: string[] = ['{'];
  const entries = Object.entries(ordered);
  entries.forEach(([k, v], i) => {
    const comma = i < entries.length - 1 ? ',' : '';
    if (k === 'layers') {
      lines.push(`${ind}"layers": [`);
      s.layers.forEach((layer, y) => {
        lines.push(`${ind}${ind}[`);
        layer.forEach((row, z) => lines.push(`${ind}${ind}${ind}${JSON.stringify(row)}${z < layer.length - 1 ? ',' : ''}`));
        lines.push(`${ind}${ind}]${y < s.layers.length - 1 ? ',' : ''}`);
      });
      lines.push(`${ind}]${comma}`);
    } else if (k === 'size') {
      lines.push(`${ind}"size": ${JSON.stringify(v)}${comma}`);
    } else {
      const body = JSON.stringify(v, null, 2).split('\n').join('\n' + ind);
      lines.push(`${ind}${JSON.stringify(k)}: ${body}${comma}`);
    }
  });
  lines.push('}');
  return lines.join('\n') + '\n';
}

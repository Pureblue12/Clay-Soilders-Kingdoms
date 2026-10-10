// Finds the user's own Minecraft assets in studio/minecraft/ (never committed) and serves them to the browser.
// Accepted: a client jar (e.g. 1.21.1.jar from .minecraft/versions/1.21.1/), or an already extracted folder that
// contains assets/minecraft/blockstates. A jar is unpacked once into .cache/mc/<jar name>-<size>/.
import fs from 'node:fs';
import path from 'node:path';
import { unzipSync } from 'fflate';
import { STUDIO_ROOT } from './store.ts';

export const MC_DIR = path.join(STUDIO_ROOT, 'minecraft');
const CACHE_DIR = path.join(STUDIO_ROOT, '.cache', 'mc');

/** Only these parts of the jar are needed. */
const WANTED = [
  'assets/minecraft/blockstates/',
  'assets/minecraft/models/block/',
  'assets/minecraft/textures/block/',
  'assets/minecraft/textures/colormap/',
  'assets/minecraft/textures/entity/chest/',
  'assets/minecraft/textures/entity/bed/',
  'assets/minecraft/textures/entity/signs/',
  'assets/minecraft/textures/entity/banner_base.png',
  'assets/minecraft/textures/entity/bell/',
];

export interface AssetSource {
  kind: 'jar' | 'folder' | 'none';
  /** Folder that holds blockstates/, models/, textures/ (i.e. .../assets/minecraft). */
  root: string | null;
  detail: string;
}

function findExtracted(dir: string, depth = 0): string | null {
  if (depth > 4 || !fs.existsSync(dir)) return null;
  const direct = path.join(dir, 'assets', 'minecraft');
  if (fs.existsSync(path.join(direct, 'blockstates'))) return direct;
  if (path.basename(dir) === 'minecraft' && fs.existsSync(path.join(dir, 'blockstates'))) return dir;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.isDirectory() && !e.name.startsWith('.')) {
      const found = findExtracted(path.join(dir, e.name), depth + 1);
      if (found) return found;
    }
  }
  return null;
}

function extractJar(jar: string): string {
  const stat = fs.statSync(jar);
  const out = path.join(CACHE_DIR, `${path.basename(jar, '.jar')}-${stat.size}`);
  const root = path.join(out, 'assets', 'minecraft');
  if (fs.existsSync(path.join(out, '.done'))) return root;
  console.log(`[studio] unpacking block assets from ${path.relative(STUDIO_ROOT, jar)} (once) ...`);
  const files = unzipSync(fs.readFileSync(jar), {
    filter: (f) => WANTED.some((w) => (w.endsWith('/') ? f.name.startsWith(w) : f.name === w)) && !f.name.endsWith('/'),
  });
  let n = 0;
  for (const [name, data] of Object.entries(files)) {
    const target = path.join(out, name);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, data);
    n++;
  }
  if (n === 0) throw new Error(`${jar} has no assets/minecraft/blockstates: is it the client jar?`);
  fs.writeFileSync(path.join(out, '.done'), String(n));
  console.log(`[studio] unpacked ${n} files`);
  return root;
}

let cached: AssetSource | null = null;

export function assetSource(refresh = false): AssetSource {
  if (cached && !refresh) return cached;
  let src: AssetSource = {
    kind: 'none',
    root: null,
    detail: 'No Minecraft assets found in studio/minecraft/: showing labelled placeholder textures.',
  };
  try {
    const extracted = findExtracted(MC_DIR);
    if (extracted) src = { kind: 'folder', root: extracted, detail: `Assets: ${path.relative(STUDIO_ROOT, extracted)}` };
    else if (fs.existsSync(MC_DIR)) {
      const jars = fs.readdirSync(MC_DIR).filter((f) => f.endsWith('.jar')).sort();
      if (jars.length) {
        const jar = path.join(MC_DIR, jars[jars.length - 1]);
        src = { kind: 'jar', root: extractJar(jar), detail: `Assets: ${jars[jars.length - 1]}` };
      }
    }
  } catch (e) {
    src = { kind: 'none', root: null, detail: `Could not read the Minecraft assets: ${(e as Error).message}` };
  }
  cached = src;
  return src;
}

/** Resolves a path under assets/minecraft (e.g. "textures/block/stone.png"), or null when missing or unsafe. */
export function assetFile(rel: string): string | null {
  const src = assetSource();
  if (!src.root) return null;
  const clean = path.normalize(rel).replace(/^([/\\])+/, '');
  if (clean.startsWith('..')) return null;
  const full = path.join(src.root, clean);
  if (!full.startsWith(src.root)) return null;
  return fs.existsSync(full) && fs.statSync(full).isFile() ? full : null;
}

/** Names of all block states files, so the client and the validator know which blocks exist. */
export function knownBlocks(): string[] | null {
  const src = assetSource();
  if (!src.root) return null;
  const d = path.join(src.root, 'blockstates');
  return fs.readdirSync(d).filter((f) => f.endsWith('.json')).map((f) => 'minecraft:' + f.slice(0, -5));
}

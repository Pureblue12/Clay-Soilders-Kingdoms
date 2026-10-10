// Bakes every block state a scene needs into quads with atlas UVs, and builds the texture atlas.
import * as THREE from 'three';
import { parseBlockState, type BlockState } from '../format/structure.ts';
import {
  bakeElements,
  isFullCube,
  ns,
  resolveModel,
  resolveParts,
  type AssetSource,
  type BlockstateJson,
  type Dir,
  type ModelJson,
  type RawQuad,
  type ResolvedModel,
} from './blockmodels.ts';
import { fallbackModel, specialModel } from './special.ts';

export const LAYER_SOLID = 0, LAYER_CUTOUT = 1, LAYER_TRANSLUCENT = 2;

export interface BakedQuad {
  pos: Float32Array; // 12
  uv: Float32Array; // 8, atlas space
  normal: [number, number, number];
  dir: Dir | -1;
  cull: Dir | -1;
  color: [number, number, number]; // tint
  layer: number;
  shade: boolean;
}

export interface BakedBlock {
  name: string;
  quads: BakedQuad[];
  /** Fills the whole cell with opaque faces: hides neighbour faces and darkens corners (ambient occlusion). */
  occludes: boolean;
  /** Hides faces between two blocks of the same kind (glass next to glass, water next to water). */
  selfCull: boolean;
  /** Draws nothing (air, structure void, barrier). */
  empty: boolean;
  ao: boolean;
  /** No model was found: drawn with a placeholder. */
  placeholder: boolean;
}

export interface TextureSource extends AssetSource {
  texture(id: string): Promise<CanvasImageSource | null>;
  readonly realAssets: boolean;
}

/** Loads blockstates, models and textures from the dev server (/mc/...), which reads the user's own assets. */
export class HttpAssets implements TextureSource {
  realAssets = false;
  private json = new Map<string, Promise<unknown>>();
  constructor(private base = '/mc/') {}
  async init(): Promise<{ kind: string; detail: string }> {
    try {
      const r = await fetch('/api/assets');
      const s = (await r.json()) as { kind: string; detail: string };
      this.realAssets = s.kind !== 'none';
      return s;
    } catch {
      this.realAssets = false;
      return { kind: 'none', detail: 'asset server unreachable' };
    }
  }
  private getJson<T>(rel: string): Promise<T | null> {
    let p = this.json.get(rel);
    if (!p) {
      p = this.realAssets
        ? fetch(this.base + rel).then((r) => (r.ok ? r.json() : null)).catch(() => null)
        : Promise.resolve(null);
      this.json.set(rel, p);
    }
    return p as Promise<T | null>;
  }
  blockstate(name: string) {
    const [space, path] = ns(name).split(':');
    if (space !== 'minecraft') return Promise.resolve(null);
    return this.getJson<BlockstateJson>(`blockstates/${path}.json`);
  }
  model(id: string) {
    const [space, path] = ns(id).split(':');
    if (space !== 'minecraft') return Promise.resolve(null);
    return this.getJson<ModelJson>(`models/${path}.json`);
  }
  async texture(id: string): Promise<CanvasImageSource | null> {
    if (!this.realAssets) return null;
    const [space, path] = ns(id).split(':');
    if (space !== 'minecraft') return null;
    try {
      const r = await fetch(`${this.base}textures/${path}.png`);
      if (!r.ok) return null;
      return await createImageBitmap(await r.blob());
    } catch {
      return null;
    }
  }
}

// ---------------------------------------------------------------- tints (plains biome)

const GRASS = hex(0x91bd59), FOLIAGE = hex(0x77ab2f), WATER = hex(0x3f76e4), SPRUCE = hex(0x619961), BIRCH = hex(0x80a755);
function hex(n: number): [number, number, number] {
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}
function tintFor(name: string, tintindex: number): [number, number, number] {
  if (tintindex < 0) return [1, 1, 1];
  const s = name.replace('minecraft:', '');
  if (s === 'water' || s === 'water_cauldron' || s === 'bubble_column') return WATER;
  if (s === 'spruce_leaves') return SPRUCE;
  if (s === 'birch_leaves') return BIRCH;
  if (s.endsWith('_leaves') && !['cherry_leaves', 'azalea_leaves', 'flowering_azalea_leaves'].includes(s)) return FOLIAGE;
  if (s === 'vine') return FOLIAGE;
  if (['grass_block', 'short_grass', 'tall_grass', 'fern', 'large_fern', 'sugar_cane', 'potted_fern'].includes(s)) return GRASS;
  if (s === 'lily_pad') return hex(0x208030);
  if (s === 'redstone_wire') return hex(0xc00000);
  return [1, 1, 1];
}

// ---------------------------------------------------------------- placeholder textures

const PLACEHOLDER_COLORS: [RegExp, number][] = [
  [/glass/, 0xc8e6f0], [/water/, 0x3f76e4], [/lava/, 0xe06010], [/leaves/, 0x4a7a2a], [/grass|moss|fern/, 0x6a9a3a],
  [/dirt|mud|path|farmland/, 0x7a5a3a], [/sand/, 0xdccf9a], [/gravel/, 0x8a8480], [/cobble/, 0x7a7a7a],
  [/deepslate|blackstone/, 0x45454a], [/stone|andesite|tuff/, 0x8c8c8c], [/brick/, 0x9a5040], [/dark_oak/, 0x4a3220],
  [/spruce/, 0x6a4a2a], [/birch/, 0xc8b47a], [/oak|plank|log|wood|door|trapdoor|fence|sign|chest|barrel|crafting/, 0xa07a48],
  [/white/, 0xe8e8e8], [/red/, 0xa02828], [/blue/, 0x3048a0], [/cyan/, 0x208080], [/green/, 0x507020],
  [/yellow|gold|hay/, 0xd8b030], [/torch|lantern|glowstone|fire/, 0xf0c050], [/iron/, 0xc8c8c8], [/snow|quartz/, 0xf0f0f0],
];

function drawPlaceholder(name: string): HTMLCanvasElement {
  const c = document.createElement('canvas');
  c.width = c.height = 16;
  const g = c.getContext('2d')!;
  const short = name.replace(/^[a-z0-9_.-]+:/, '');
  let base = 0xb040b0;
  for (const [re, col] of PLACEHOLDER_COLORS) if (re.test(short)) { base = col; break; }
  let h = 2166136261;
  for (const ch of name) h = Math.imul(h ^ ch.charCodeAt(0), 16777619);
  const glass = /glass|ice/.test(short);
  for (let y = 0; y < 16; y++)
    for (let x = 0; x < 16; x++) {
      h = Math.imul(h ^ (x * 31 + y * 17), 16777619);
      const n = ((h >>> 24) / 255 - 0.5) * 0.18;
      const edge = x === 0 || y === 0 || x === 15 || y === 15;
      const k = (edge ? 0.75 : 1) + n;
      const r = Math.min(255, ((base >> 16) & 255) * k), gg = Math.min(255, ((base >> 8) & 255) * k), b = Math.min(255, (base & 255) * k);
      const a = glass && !edge ? 0.35 : 1;
      g.fillStyle = `rgba(${r | 0},${gg | 0},${b | 0},${a})`;
      g.fillRect(x, y, 1, 1);
    }
  // A small "?" so nobody mistakes this for the real texture.
  g.fillStyle = 'rgba(255,255,255,0.85)';
  for (const [x, y] of [[6, 4], [7, 3], [8, 3], [9, 4], [9, 5], [8, 6], [7, 7], [7, 8], [7, 11]]) g.fillRect(x, y, 1, 1);
  return c;
}

// ---------------------------------------------------------------- the library

interface Tile { x: number; y: number; w: number; h: number; layer: number }

export class BlockLibrary {
  private modelCache = new Map<string, Promise<ResolvedModel | null>>();
  private raw = new Map<string, { quads: RawQuad[]; occludesCandidate: boolean; ao: boolean; placeholder: boolean; empty: boolean }>();
  private images = new Map<string, CanvasImageSource>();
  private tiles = new Map<string, Tile>();
  private baked = new Map<string, BakedBlock>();
  atlas: THREE.Texture | null = null;
  atlasCanvas: HTMLCanvasElement | null = null;
  placeholderCount = 0;
  private version = 0;

  constructor(readonly src: TextureSource) {}

  /** Bakes all given block state strings (adds to what's already known) and rebuilds the atlas if needed. */
  async prepare(stateStrings: Iterable<string>): Promise<void> {
    const todo = [...new Set(stateStrings)].filter((s) => !this.raw.has(s));
    if (todo.length === 0) return;
    await Promise.all(todo.map((s) => this.bakeRaw(s)));
    const needed = new Set<string>();
    for (const r of this.raw.values()) for (const q of r.quads) needed.add(q.texture);
    const missing = [...needed].filter((t) => !this.images.has(t));
    await Promise.all(
      missing.map(async (t) => {
        let img: CanvasImageSource | null = null;
        if (!t.startsWith('placeholder:')) img = await this.src.texture(t);
        if (!img) img = drawPlaceholder(t.startsWith('placeholder:') ? t.slice(12) : t);
        this.images.set(t, img);
      }),
    );
    if (missing.length || !this.atlas) this.buildAtlas();
    this.baked.clear();
    this.version++;
  }

  get atlasVersion() { return this.version; }

  private async bakeRaw(stateString: string) {
    const state = parseBlockState(stateString);
    const result = await this.bakeState(state);
    this.raw.set(stateString, result);
  }

  private async bakeState(state: BlockState) {
    const special = specialModel(state);
    if (special) {
      const quads = bakeElements(special.elements, special.textures, { x: special.x ?? 0, y: special.y, uvlock: false }, special.uvScale ?? 16);
      return { quads, occludesCandidate: false, ao: true, placeholder: false, empty: quads.length === 0 };
    }
    const bs = await this.src.blockstate(state.name);
    const parts = bs ? resolveParts(bs, state) : null;
    if (parts && parts.length) {
      const quads: RawQuad[] = [];
      let full = false, ao = true, ok = false;
      for (const part of parts) {
        const m = await resolveModel(this.src, part.model, this.modelCache);
        if (!m) continue;
        ok = true;
        quads.push(...bakeElements(m.elements, m.textures, part));
        if (isFullCube(m.elements)) full = true;
        if (!m.ao) ao = false;
      }
      if (ok) return { quads, occludesCandidate: full, ao, placeholder: false, empty: quads.length === 0 };
    }
    const fb = fallbackModel(state)!;
    const quads = bakeElements(fb.elements, fb.textures, { x: fb.x ?? 0, y: fb.y, uvlock: false });
    if (quads.length) this.placeholderCount++;
    return { quads, occludesCandidate: isFullCube(fb.elements), ao: true, placeholder: quads.length > 0, empty: quads.length === 0 };
  }

  private buildAtlas() {
    // Shelf-pack every texture (first animation frame only) into a power-of-two canvas.
    const entries = [...this.images.entries()].map(([id, img]) => {
      const w = (img as { width: number }).width, h = (img as { height: number }).height;
      // Animated block textures are vertical strips of square frames; entity textures aren't animated.
      const frameH = id.includes(':block/') && h > w && h % w === 0 ? w : h;
      return { id, img, w, h: frameH };
    });
    entries.sort((a, b) => b.h - a.h || b.w - a.w);
    const PAD = 1;
    let size = 256;
    let placed: Map<string, Tile> | null = null;
    while (!placed) {
      placed = new Map();
      let x = 0, y = 0, rowH = 0;
      for (const e of entries) {
        if (x + e.w + PAD * 2 > size) { x = 0; y += rowH; rowH = 0; }
        if (y + e.h + PAD * 2 > size) { placed = null; size *= 2; break; }
        placed.set(e.id, { x: x + PAD, y: y + PAD, w: e.w, h: e.h, layer: 0 });
        x += e.w + PAD * 2;
        rowH = Math.max(rowH, e.h + PAD * 2);
      }
    }
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = size;
    const g = canvas.getContext('2d', { willReadFrequently: true })!;
    g.imageSmoothingEnabled = false;
    for (const e of entries) {
      const t = placed.get(e.id)!;
      // draw with a 1px extruded border so neighbouring tiles never bleed in
      for (const [dx, dy] of [[-1, 0], [1, 0], [0, -1], [0, 1]])
        g.drawImage(e.img, 0, 0, e.w, e.h, t.x + dx, t.y + dy, e.w, e.h);
      g.clearRect(t.x, t.y, e.w, e.h); // the overlapping copies would stack up alpha on see-through textures
      g.drawImage(e.img, 0, 0, e.w, e.h, t.x, t.y, e.w, e.h);
      const data = g.getImageData(t.x, t.y, e.w, e.h).data;
      let zero = false, partial = false;
      for (let i = 3; i < data.length; i += 4) {
        const a = data[i];
        if (a === 0) zero = true;
        else if (a < 250) partial = true;
      }
      t.layer = partial ? LAYER_TRANSLUCENT : zero ? LAYER_CUTOUT : LAYER_SOLID;
    }
    this.tiles = placed;
    this.atlasCanvas = canvas;
    const tex = new THREE.CanvasTexture(canvas);
    tex.magFilter = THREE.NearestFilter;
    tex.minFilter = THREE.NearestFilter;
    tex.generateMipmaps = false;
    tex.flipY = false;
    tex.colorSpace = THREE.SRGBColorSpace;
    this.atlas?.dispose();
    this.atlas = tex;
  }

  get(stateString: string): BakedBlock {
    let b = this.baked.get(stateString);
    if (b) return b;
    const raw = this.raw.get(stateString);
    const name = stateString.replace(/\[.*$/, '');
    if (!raw) {
      b = { name, quads: [], occludes: false, selfCull: false, empty: true, ao: true, placeholder: false };
      this.baked.set(stateString, b);
      return b;
    }
    const size = this.atlasCanvas!.width;
    let allSolid = raw.quads.length > 0;
    const quads: BakedQuad[] = raw.quads.map((q) => {
      const t = this.tiles.get(q.texture)!;
      const uv = new Float32Array(8);
      const inset = 0.002;
      for (let i = 0; i < 4; i++) {
        const u = Math.min(1 - inset, Math.max(inset, q.uv[i * 2]));
        const v = Math.min(1 - inset, Math.max(inset, q.uv[i * 2 + 1]));
        uv[i * 2] = (t.x + u * t.w) / size;
        uv[i * 2 + 1] = (t.y + v * t.h) / size;
      }
      if (t.layer !== LAYER_SOLID) allSolid = false;
      return {
        pos: Float32Array.from(q.pos),
        uv,
        normal: q.normal,
        dir: q.dir,
        cull: q.cull,
        color: tintFor(name, q.tint),
        layer: t.layer,
        shade: q.shade,
      };
    });
    const translucentOrCutout = !allSolid;
    b = {
      name,
      quads,
      occludes: raw.occludesCandidate && allSolid,
      selfCull: raw.occludesCandidate && translucentOrCutout && !name.endsWith('_leaves'),
      empty: raw.empty,
      ao: raw.ao,
      placeholder: raw.placeholder,
    };
    this.baked.set(stateString, b);
    return b;
  }
}

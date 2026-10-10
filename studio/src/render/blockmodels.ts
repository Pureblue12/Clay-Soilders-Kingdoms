// Turns a block state into quads, following Minecraft's own rules: blockstates/<block>.json picks models
// (variants or multipart), models/<model>.json inherit from parents and list cuboid "elements" with per-face
// textures, UVs and cullfaces. Coordinates here are block-local, 0..1.
import type { BlockState } from '../format/structure.ts';

export type Dir = 0 | 1 | 2 | 3 | 4 | 5; // down, up, north, south, west, east
export const DIRS = ['down', 'up', 'north', 'south', 'west', 'east'] as const;
export const DIR_VEC: [number, number, number][] = [
  [0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0],
];
export function dirIndex(name: string): Dir | -1 {
  const i = DIRS.indexOf(name as (typeof DIRS)[number]);
  return i < 0 ? -1 : (i as Dir);
}

/** A quad before atlas packing. */
export interface RawQuad {
  pos: number[]; // 4 vertices x 3, block-local 0..1
  uv: number[]; // 4 vertices x 2, in texture pixels / 16 (0..1 of a 16px texture; entity textures scale)
  texture: string; // resolved texture id, e.g. minecraft:block/stone
  normal: [number, number, number];
  dir: Dir | -1; // axis-aligned face direction, -1 if rotated (no AO)
  cull: Dir | -1;
  tint: number; // tintindex, -1 = none
  shade: boolean;
  /** UV space size in pixels of the texture this quad was authored for (16 for block textures, 64 for chest). */
  uvScale: number;
}

export interface ModelJson {
  parent?: string;
  textures?: Record<string, string>;
  elements?: ElementJson[];
  ambientocclusion?: boolean;
}
export interface ElementJson {
  from: [number, number, number];
  to: [number, number, number];
  rotation?: { origin: [number, number, number]; axis: 'x' | 'y' | 'z'; angle: number; rescale?: boolean };
  shade?: boolean;
  faces: Partial<Record<(typeof DIRS)[number], FaceJson>>;
}
export interface FaceJson {
  uv?: [number, number, number, number];
  texture: string;
  cullface?: string;
  rotation?: number;
  tintindex?: number;
}
interface VariantJson {
  model: string;
  x?: number;
  y?: number;
  uvlock?: boolean;
  weight?: number;
}
export interface BlockstateJson {
  variants?: Record<string, VariantJson | VariantJson[]>;
  multipart?: { when?: Record<string, unknown>; apply: VariantJson | VariantJson[] }[];
}

export interface AssetSource {
  blockstate(name: string): Promise<BlockstateJson | null>;
  model(id: string): Promise<ModelJson | null>;
}

export function ns(id: string): string {
  return id.includes(':') ? id : 'minecraft:' + id;
}

// ---------------------------------------------------------------- blockstate -> model parts

export interface ModelPart {
  model: string;
  x: number;
  y: number;
  uvlock: boolean;
}

function matchVariantKey(key: string, props: Record<string, string>): boolean {
  if (key === '' || key === 'normal') return true;
  return key.split(',').every((kv) => {
    const [k, v] = kv.split('=');
    return props[k] === v;
  });
}

function matchWhen(when: Record<string, unknown> | undefined, props: Record<string, string>): boolean {
  if (!when) return true;
  if (Array.isArray((when as { OR?: unknown }).OR))
    return ((when as { OR: Record<string, unknown>[] }).OR).some((w) => matchWhen(w, props));
  if (Array.isArray((when as { AND?: unknown }).AND))
    return ((when as { AND: Record<string, unknown>[] }).AND).every((w) => matchWhen(w, props));
  return Object.entries(when).every(([k, v]) => {
    const s = String(v);
    const negate = s.startsWith('!');
    const options = (negate ? s.slice(1) : s).split('|');
    const has = options.includes(props[k] ?? '');
    return negate ? !has : has;
  });
}

/** Vanilla default values of common properties, used when a state leaves them out (as the game does). */
const STATE_DEFAULTS: Record<string, string> = {
  axis: 'y', half: 'bottom', type: 'bottom', shape: 'straight', facing: 'north', waterlogged: 'false', open: 'false',
  powered: 'false', lit: 'false', hinge: 'left', part: 'foot', occupied: 'false', attached: 'false', hanging: 'false',
};

const pick = (v: VariantJson | VariantJson[]): VariantJson => (Array.isArray(v) ? v[0] : v);
const toPart = (v: VariantJson): ModelPart => ({ model: ns(v.model), x: v.x ?? 0, y: v.y ?? 0, uvlock: !!v.uvlock });

/** Fills in default property values so "oak_stairs" alone still finds a variant (first variant whose keys fit). */
export function resolveParts(bs: BlockstateJson, state: BlockState): ModelPart[] | null {
  const props = state.properties;
  if (bs.variants) {
    const keys = Object.keys(bs.variants);
    let key = keys.find((k) => matchVariantKey(k, props));
    if (key === undefined) {
      // Missing properties: the game's default state first (a bare "oak_log" stands upright), then the first variant.
      const withDefaults = { ...STATE_DEFAULTS, ...props };
      key = keys.find((k) => matchVariantKey(k, withDefaults));
    }
    if (key === undefined) {
      key = keys.find((k) =>
        k.split(',').every((kv) => {
          const [p, v] = kv.split('=');
          return props[p] === undefined || props[p] === v;
        }),
      );
    }
    if (key === undefined) key = keys[0];
    return key === undefined ? null : [toPart(pick(bs.variants[key]))];
  }
  if (bs.multipart) {
    const parts = bs.multipart.filter((p) => matchWhen(p.when, props)).map((p) => toPart(pick(p.apply)));
    return parts;
  }
  return null;
}

// ---------------------------------------------------------------- model resolution

export interface ResolvedModel {
  textures: Record<string, string>;
  elements: ElementJson[];
  ao: boolean;
}

export async function resolveModel(src: AssetSource, id: string, cache: Map<string, Promise<ResolvedModel | null>>) {
  let p = cache.get(id);
  if (!p) {
    p = (async () => {
      const chain: ModelJson[] = [];
      let cur: string | undefined = id;
      for (let guard = 0; cur && guard < 20; guard++) {
        if (cur.startsWith('builtin/') || cur.startsWith('minecraft:builtin/')) break;
        const m = await src.model(ns(cur));
        if (!m) {
          if (chain.length === 0) return null;
          break;
        }
        chain.push(m);
        cur = m.parent;
      }
      const textures: Record<string, string> = {};
      for (let i = chain.length - 1; i >= 0; i--) Object.assign(textures, chain[i].textures ?? {});
      const elements = chain.find((m) => m.elements)?.elements ?? [];
      const ao = chain.find((m) => m.ambientocclusion !== undefined)?.ambientocclusion ?? true;
      return { textures, elements, ao };
    })();
    cache.set(id, p);
  }
  return p;
}

export function resolveTexture(ref: string, textures: Record<string, string>): string | null {
  let r = ref;
  for (let i = 0; i < 10 && r.startsWith('#'); i++) {
    const next = textures[r.slice(1)];
    if (next === undefined) return null;
    r = next;
  }
  if (r.startsWith('#')) return null;
  return ns(r);
}

// ---------------------------------------------------------------- baking

// Vertex corners per face, in vanilla order (seen from outside: top-left, bottom-left, bottom-right, top-right).
// Each entry picks min (0) or max (1) for x, y, z.
const FACE_CORNERS: number[][][] = [
  [[0, 0, 1], [0, 0, 0], [1, 0, 0], [1, 0, 1]], // down
  [[0, 1, 0], [0, 1, 1], [1, 1, 1], [1, 1, 0]], // up
  [[1, 1, 0], [1, 0, 0], [0, 0, 0], [0, 1, 0]], // north
  [[0, 1, 1], [0, 0, 1], [1, 0, 1], [1, 1, 1]], // south
  [[0, 1, 0], [0, 0, 0], [0, 0, 1], [0, 1, 1]], // west
  [[1, 1, 1], [1, 0, 1], [1, 0, 0], [1, 1, 0]], // east
];

function defaultUv(dir: Dir, f: number[], t: number[]): [number, number, number, number] {
  switch (dir) {
    case 0: return [f[0], 16 - t[2], t[0], 16 - f[2]];
    case 1: return [f[0], f[2], t[0], t[2]];
    case 2: return [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]];
    case 3: return [f[0], 16 - t[1], t[0], 16 - f[1]];
    case 4: return [f[2], 16 - t[1], t[2], 16 - f[1]];
    default: return [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]];
  }
}

type V3 = [number, number, number];

function rotateAround(p: V3, axis: 'x' | 'y' | 'z', deg: number, origin: V3): V3 {
  const a = (deg * Math.PI) / 180;
  const c = Math.cos(a), s = Math.sin(a);
  const x = p[0] - origin[0], y = p[1] - origin[1], z = p[2] - origin[2];
  let r: V3;
  if (axis === 'x') r = [x, y * c - z * s, y * s + z * c];
  else if (axis === 'y') r = [x * c + z * s, y, -x * s + z * c];
  else r = [x * c - y * s, x * s + y * c, z];
  return [r[0] + origin[0], r[1] + origin[1], r[2] + origin[2]];
}

/** Block-state rotation: x first, then y, in 90 degree steps, around the block center. Matches vanilla:
 *  y=90 turns east into south, x=90 turns north into down. */
function rotateVariant(p: V3, rx: number, ry: number): V3 {
  let [x, y, z] = [p[0] - 0.5, p[1] - 0.5, p[2] - 0.5];
  for (let i = 0; i < ((rx / 90) % 4 + 4) % 4; i++) [y, z] = [z, -y];
  for (let i = 0; i < ((ry / 90) % 4 + 4) % 4; i++) [x, z] = [-z, x];
  return [x + 0.5, y + 0.5, z + 0.5];
}

function rotateDirVec(v: V3, rx: number, ry: number): V3 {
  const p = rotateVariant([v[0] + 0.5, v[1] + 0.5, v[2] + 0.5], rx, ry);
  return [Math.round(p[0] - 0.5), Math.round(p[1] - 0.5), Math.round(p[2] - 0.5)];
}

function dirFromVec(v: V3): Dir | -1 {
  for (let i = 0; i < 6; i++) {
    const d = DIR_VEC[i];
    if (d[0] === v[0] && d[1] === v[1] && d[2] === v[2]) return i as Dir;
  }
  return -1;
}

function normalOf(p: number[]): V3 {
  const ax = p[3] - p[0], ay = p[4] - p[1], az = p[5] - p[2];
  const bx = p[9] - p[0], by = p[10] - p[1], bz = p[11] - p[2];
  // vertex order is counter-clockwise seen from outside, so (v1 - v0) x (v3 - v0) points outward
  let nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
  const l = Math.hypot(nx, ny, nz) || 1;
  nx /= l; ny /= l; nz /= l;
  return [nx, ny, nz];
}

/** Bakes resolved model elements into block-local quads, applying element and block-state rotations. */
export function bakeElements(
  elements: ElementJson[],
  textures: Record<string, string>,
  part: { x: number; y: number; uvlock: boolean },
  uvScale = 16,
): RawQuad[] {
  const quads: RawQuad[] = [];
  for (const el of elements) {
    const f = el.from, t = el.to;
    for (let d = 0 as Dir; d < 6; d = (d + 1) as Dir) {
      const face = el.faces[DIRS[d]];
      if (!face) continue;
      const tex = resolveTexture(face.texture, textures);
      if (!tex) continue;
      // positions in 0..16
      let verts: V3[] = FACE_CORNERS[d].map((c) => [c[0] ? t[0] : f[0], c[1] ? t[1] : f[1], c[2] ? t[2] : f[2]]);
      if (el.rotation && el.rotation.angle) {
        const r = el.rotation;
        verts = verts.map((v) => rotateAround(v, r.axis, r.angle, r.origin));
        if (r.rescale) {
          const scale = 1 / Math.cos((Math.abs(r.angle) * Math.PI) / 180);
          verts = verts.map((v) => {
            const o = [...v] as V3;
            for (const ax of [0, 1, 2]) if ('xyz'[ax] !== r.axis) o[ax] = r.origin[ax] + (v[ax] - r.origin[ax]) * scale;
            return o;
          });
        }
      }
      // to 0..1, then block-state rotation
      let pos01 = verts.map((v) => [v[0] / 16, v[1] / 16, v[2] / 16] as V3);
      if (part.x || part.y) pos01 = pos01.map((v) => rotateVariant(v, part.x, part.y));
      const finalDirVec = rotateDirVec(DIR_VEC[d], part.x, part.y);
      const finalDir = el.rotation && el.rotation.angle ? -1 : dirFromVec(finalDirVec);

      // UVs
      let uv = face.uv ?? defaultUv(d, f, t);
      let faceRot = face.rotation ?? 0;
      if (part.uvlock && finalDir !== -1 && (part.x || part.y) && !(el.rotation && el.rotation.angle)) {
        // Keep the texture aligned to the world: use the default UV of the rotated box on its final face.
        const xs = pos01.map((v) => v[0] * 16), ys = pos01.map((v) => v[1] * 16), zs = pos01.map((v) => v[2] * 16);
        const rf = [Math.min(...xs), Math.min(...ys), Math.min(...zs)];
        const rt = [Math.max(...xs), Math.max(...ys), Math.max(...zs)];
        uv = defaultUv(finalDir as Dir, rf, rt);
        faceRot = 0;
        // Re-derive vertex order for the final face so UV corners line up with world axes.
        const cornersFinal = FACE_CORNERS[finalDir].map(
          (c) => [c[0] ? rt[0] : rf[0], c[1] ? rt[1] : rf[1], c[2] ? rt[2] : rf[2]].map((n) => n / 16) as V3,
        );
        pos01 = cornersFinal;
      }
      const corners = [
        [uv[0], uv[1]],
        [uv[0], uv[3]],
        [uv[2], uv[3]],
        [uv[2], uv[1]],
      ];
      const shift = (((faceRot / 90) % 4) + 4) % 4;
      const uvOut: number[] = [];
      for (let i = 0; i < 4; i++) {
        const c = corners[(i + shift) % 4];
        uvOut.push(c[0] / uvScale, c[1] / uvScale);
      }
      const pos = pos01.flat();
      const cullName = face.cullface ? dirIndex(face.cullface) : -1;
      const cull = cullName === -1 ? -1 : dirFromVec(rotateDirVec(DIR_VEC[cullName], part.x, part.y));
      quads.push({
        pos,
        uv: uvOut,
        texture: tex,
        normal: finalDir === -1 ? normalOf(pos) : DIR_VEC[finalDir],
        dir: finalDir,
        cull,
        tint: face.tintindex ?? -1,
        shade: el.shade ?? true,
        uvScale,
      });
    }
  }
  return quads;
}

/** True when the elements fill the whole block (one 0..16 cube with all six faces). */
export function isFullCube(elements: ElementJson[]): boolean {
  return elements.some(
    (e) =>
      !e.rotation?.angle &&
      e.from.every((n) => n === 0) &&
      e.to.every((n) => n === 16) &&
      DIRS.every((d) => e.faces[d]),
  );
}

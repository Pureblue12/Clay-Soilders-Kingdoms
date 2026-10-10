// A test world: optional terrain plus any number of placed structures. Built so a kingdom generator can later
// place many structures at positions over time (see src/world/generator.ts); today the viewer places one.
import { VOID_BLOCK, isAir, type StructureGrid } from '../format/structure.ts';

export type LayerMode = 'all' | 'upto' | 'single';

export interface LayerFilter {
  mode: LayerMode;
  /** Structure-relative layer (0 = bottom layer of the structure). */
  y: number;
}

export class Placement {
  /** world state id per palette index; 0 = nothing here (air or structure void: the terrain shows through) */
  readonly ids: Uint16Array;
  filter: LayerFilter = { mode: 'all', y: 0 };
  constructor(
    readonly key: string,
    readonly grid: StructureGrid,
    public origin: [number, number, number],
    world: World,
  ) {
    this.ids = Uint16Array.from(grid.states.map((s, i) => {
      if (isAir(s.name) || s.name === VOID_BLOCK) return 0;
      return world.idOf(grid.stateStrings[i]);
    }));
  }
  get min(): [number, number, number] { return this.origin; }
  get max(): [number, number, number] {
    return [this.origin[0] + this.grid.size[0], this.origin[1] + this.grid.size[1], this.origin[2] + this.grid.size[2]];
  }
  /** Block id at a world position (0 = none), honoring the layer filter. */
  at(x: number, y: number, z: number): number {
    const [sx, sy, sz] = this.grid.size;
    const lx = x - this.origin[0], ly = y - this.origin[1], lz = z - this.origin[2];
    if (lx < 0 || ly < 0 || lz < 0 || lx >= sx || ly >= sy || lz >= sz) return 0;
    if (this.filter.mode === 'upto' && ly > this.filter.y) return 0;
    if (this.filter.mode === 'single' && ly !== this.filter.y) return 0;
    return this.ids[this.grid.cells[(ly * sz + lz) * sx + lx]];
  }
  /** Unfiltered: is there a real block here (used to hide terrain under the structure)? */
  solidAt(x: number, y: number, z: number): boolean {
    const [sx, sy, sz] = this.grid.size;
    const lx = x - this.origin[0], ly = y - this.origin[1], lz = z - this.origin[2];
    if (lx < 0 || ly < 0 || lz < 0 || lx >= sx || ly >= sy || lz >= sz) return false;
    return this.ids[this.grid.cells[(ly * sz + lz) * sx + lx]] !== 0;
  }
}

export type TerrainKind = 'none' | 'flat' | 'hills' | 'slope';

export class Terrain {
  readonly heights: Int16Array;
  readonly minY: number;
  readonly maxY: number;
  /** Extra blocks such as trees, keyed "x,y,z". */
  readonly extra = new Map<string, number>();
  constructor(
    readonly kind: TerrainKind,
    readonly x0: number,
    readonly z0: number,
    readonly size: number,
    heightAt: (x: number, z: number) => number,
    private ids: { grass: number; dirt: number; stone: number; bedrock: number },
  ) {
    this.heights = new Int16Array(size * size);
    let lo = Infinity, hi = -Infinity;
    for (let z = 0; z < size; z++)
      for (let x = 0; x < size; x++) {
        const h = Math.round(heightAt(x0 + x, z0 + z));
        this.heights[z * size + x] = h;
        lo = Math.min(lo, h);
        hi = Math.max(hi, h);
      }
    this.minY = lo - 6;
    this.maxY = hi;
  }
  inside(x: number, z: number) {
    return x >= this.x0 && z >= this.z0 && x < this.x0 + this.size && z < this.z0 + this.size;
  }
  height(x: number, z: number): number {
    if (!this.inside(x, z)) return -9999;
    return this.heights[(z - this.z0) * this.size + (x - this.x0)];
  }
  at(x: number, y: number, z: number): number {
    if (!this.inside(x, z)) return 0;
    const h = this.heights[(z - this.z0) * this.size + (x - this.x0)];
    if (y > h) return this.extra.size ? this.extra.get(`${x},${y},${z}`) ?? 0 : 0;
    if (y < this.minY) return 0;
    if (y === this.minY) return this.ids.bedrock;
    if (y === h) return this.ids.grass;
    if (y >= h - 3) return this.ids.dirt;
    return this.ids.stone;
  }
}

// ---------------------------------------------------------------- noise for hills

function hash2(x: number, z: number, seed: number): number {
  let h = Math.imul(x, 374761393) ^ Math.imul(z, 668265263) ^ Math.imul(seed, 2147483647);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}
function valueNoise(x: number, z: number, seed: number): number {
  const xi = Math.floor(x), zi = Math.floor(z);
  const fx = x - xi, fz = z - zi;
  const sx = fx * fx * (3 - 2 * fx), sz = fz * fz * (3 - 2 * fz);
  const a = hash2(xi, zi, seed), b = hash2(xi + 1, zi, seed), c = hash2(xi, zi + 1, seed), d = hash2(xi + 1, zi + 1, seed);
  return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sz;
}
function fbm(x: number, z: number, seed: number): number {
  let sum = 0, amp = 1, f = 1, norm = 0;
  for (let o = 0; o < 4; o++) {
    sum += valueNoise(x * f, z * f, seed + o * 17) * amp;
    norm += amp;
    amp *= 0.5;
    f *= 2;
  }
  return sum / norm;
}

export class World {
  readonly states: string[] = ['minecraft:air'];
  private stateIndex = new Map<string, number>([['minecraft:air', 0]]);
  placements: Placement[] = [];
  terrain: Terrain | null = null;

  idOf(state: string): number {
    let id = this.stateIndex.get(state);
    if (id === undefined) {
      id = this.states.length;
      this.states.push(state);
      this.stateIndex.set(state, id);
    }
    return id;
  }

  place(key: string, grid: StructureGrid, origin: [number, number, number]): Placement {
    const p = new Placement(key, grid, origin, this);
    this.placements.push(p);
    return p;
  }

  /** The visible block at a position: the newest placement wins, then the terrain. */
  get(x: number, y: number, z: number): number {
    for (let i = this.placements.length - 1; i >= 0; i--) {
      const p = this.placements[i];
      const id = p.at(x, y, z);
      if (id) return id;
      if (p.solidAt(x, y, z)) return 0; // hidden by the layer view: leave a gap, not the ground
    }
    return this.terrain ? this.terrain.at(x, y, z) : 0;
  }
  /** Is (x, z) inside or next to a structure's footprint? */
  nearPlacement(x: number, z: number): boolean {
    for (const p of this.placements)
      if (x >= p.min[0] - 1 && x <= p.max[0] && z >= p.min[2] - 1 && z <= p.max[2]) return true;
    return false;
  }
  placementAt(x: number, y: number, z: number): number {
    for (let i = this.placements.length - 1; i >= 0; i--) {
      const id = this.placements[i].at(x, y, z);
      if (id) return id;
    }
    return 0;
  }
  /** Terrain block unless a structure (unfiltered) stands there. */
  terrainAt(x: number, y: number, z: number): number {
    if (!this.terrain) return 0;
    for (const p of this.placements) if (p.solidAt(x, y, z)) return 0;
    return this.terrain.at(x, y, z);
  }

  /**
   * Generates terrain of `size` x `size` blocks centered on (cx, cz). The height at the center is 0, so a structure
   * placed at y = 0 sits on the ground.
   */
  setTerrain(kind: TerrainKind, cx: number, cz: number, size: number, seed = 1, trees = false) {
    if (kind === 'none') {
      this.terrain = null;
      return;
    }
    size = Math.min(256, Math.max(16, size));
    const ids = {
      grass: this.idOf('minecraft:grass_block[snowy=false]'),
      dirt: this.idOf('minecraft:dirt'),
      stone: this.idOf('minecraft:stone'),
      bedrock: this.idOf('minecraft:bedrock'),
    };
    let f: (x: number, z: number) => number;
    if (kind === 'flat') f = () => 0;
    else if (kind === 'hills') {
      const c = fbm(cx / 28, cz / 28, seed);
      f = (x, z) => (fbm(x / 28, z / 28, seed) - c) * 22;
    } else {
      // a steep hillside rising toward +x: about 1 block up for every 1.6 across, with a little roughness
      const c = fbm(cx / 10, cz / 10, seed);
      f = (x, z) => (x - cx) / 1.6 + (fbm(x / 10, z / 10, seed) - c) * 3;
    }
    const x0 = Math.floor(cx - size / 2), z0 = Math.floor(cz - size / 2);
    this.terrain = new Terrain(kind, x0, z0, size, f, ids);
    if (trees) this.plantTrees(seed);
  }

  private plantTrees(seed: number) {
    const t = this.terrain!;
    const log = this.idOf('minecraft:oak_log[axis=y]');
    const leaves = this.idOf('minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]');
    const keepOut = this.placements.map((p) => [p.min[0] - 4, p.min[2] - 4, p.max[0] + 4, p.max[2] + 4]);
    for (let z = t.z0 + 3; z < t.z0 + t.size - 3; z += 7)
      for (let x = t.x0 + 3; x < t.x0 + t.size - 3; x += 7) {
        const r = hash2(x, z, seed + 99);
        if (r > 0.45) continue;
        const tx = x + Math.floor(hash2(x, z, seed + 5) * 5) - 2, tz = z + Math.floor(hash2(x, z, seed + 6) * 5) - 2;
        if (keepOut.some(([a, b, c, d]) => tx >= a && tz >= b && tx <= c && tz <= d)) continue;
        const h = t.height(tx, tz);
        const trunk = 4 + Math.floor(hash2(tx, tz, seed) * 2);
        for (let y = 1; y <= trunk; y++) t.extra.set(`${tx},${h + y},${tz}`, log);
        for (let dy = trunk - 2; dy <= trunk + 1; dy++) {
          const rad = dy >= trunk ? 1 : 2;
          for (let dx = -rad; dx <= rad; dx++)
            for (let dz = -rad; dz <= rad; dz++) {
              if (Math.abs(dx) === rad && Math.abs(dz) === rad && (dy === trunk + 1 || hash2(tx + dx, tz + dz, dy) < 0.5)) continue;
              const k = `${tx + dx},${h + dy},${tz + dz}`;
              if (!t.extra.has(k)) t.extra.set(k, leaves);
            }
        }
      }
  }
}

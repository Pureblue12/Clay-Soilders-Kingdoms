// A tiny helper for writing structures in code (used by the samples; agents may use it too).
//   const b = new Builder(9, 8, 9);
//   b.fill([0, 0, 0], [8, 0, 8], 'cobblestone');
//   b.set(4, 1, 0, 'oak_door[facing=north,half=lower,hinge=left,open=false]');
//   fs.writeFileSync('house.json', stringifyStructure(b.toStructure('house', { author: 'claude', description: '...' })));
// Coordinates: x = width, y = up (0 = the layer level with the ground), z = depth, z = 0 is the FRONT (north).
import { AIR_BLOCK, VOID_BLOCK, fromBlocks, parseBlockState, blockStateToString, type StructureFile } from '../src/format/structure.ts';

export type Vec = [number, number, number];

export class Builder {
  private cells: string[];
  constructor(readonly sx: number, readonly sy: number, readonly sz: number, fillWith = AIR_BLOCK) {
    this.cells = new Array(sx * sy * sz).fill(fillWith);
  }
  private i(x: number, y: number, z: number) {
    return (y * this.sz + z) * this.sx + x;
  }
  inside(x: number, y: number, z: number) {
    return x >= 0 && y >= 0 && z >= 0 && x < this.sx && y < this.sy && z < this.sz;
  }
  set(x: number, y: number, z: number, state: string) {
    if (!this.inside(x, y, z)) throw new Error(`(${x}, ${y}, ${z}) is outside ${this.sx}x${this.sy}x${this.sz}`);
    this.cells[this.i(x, y, z)] = blockStateToString(parseBlockState(state));
  }
  get(x: number, y: number, z: number): string {
    return this.inside(x, y, z) ? this.cells[this.i(x, y, z)] : AIR_BLOCK;
  }
  /** Inclusive box. `state` may be a function of the position (for texture variation). */
  fill(a: Vec, b: Vec, state: string | ((x: number, y: number, z: number) => string | null)) {
    for (let y = Math.min(a[1], b[1]); y <= Math.max(a[1], b[1]); y++)
      for (let z = Math.min(a[2], b[2]); z <= Math.max(a[2], b[2]); z++)
        for (let x = Math.min(a[0], b[0]); x <= Math.max(a[0], b[0]); x++) {
          const s = typeof state === 'string' ? state : state(x, y, z);
          if (s) this.set(x, y, z, s);
        }
  }
  /** Hollow box walls (no floor, no ceiling). */
  walls(a: Vec, b: Vec, state: string | ((x: number, y: number, z: number) => string | null)) {
    this.fill(a, b, (x, y, z) => {
      const edge = x === a[0] || x === b[0] || z === a[2] || z === b[2];
      if (!edge) return null;
      return typeof state === 'string' ? state : state(x, y, z);
    });
  }
  /** Only where the cell is air. */
  fillAir(a: Vec, b: Vec, state: string) {
    this.fill(a, b, (x, y, z) => (this.get(x, y, z) === AIR_BLOCK ? state : null));
  }
  toStructure(name: string, meta: { author: string; description: string; tags?: string[]; groundLayer?: number }): StructureFile {
    return fromBlocks(name, 1, [this.sx, this.sy, this.sz], (x, y, z) => this.cells[this.i(x, y, z)], {
      status: 'pending',
      notes: '',
      author: meta.author,
      created: new Date().toISOString(),
      description: meta.description,
      tags: meta.tags,
      ...(meta.groundLayer !== undefined ? { groundLayer: meta.groundLayer } : {}),
    } as StructureFile['meta']);
  }
}

/** Deterministic 0..1 noise per position, for mixing block variants (mossy, cracked) without randomness. */
export function noise(x: number, y: number, z: number, seed = 0): number {
  let h = Math.imul(x + 1013, 374761393) ^ Math.imul(y + 7, 668265263) ^ Math.imul(z + 31, 2246822519) ^ Math.imul(seed + 1, 3266489917);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}

export { AIR_BLOCK, VOID_BLOCK };

// Chunked meshing: turns blocks into vertex buffers, one set per 16x16x16 chunk and render layer.
// Faces against a full opaque block are skipped (hidden-face culling), and every vertex of an axis-aligned face gets
// Minecraft-style ambient occlusion from the three blocks around that corner.
import * as THREE from 'three';
import { DIR_VEC } from './blockmodels.ts';
import { LAYER_SOLID, LAYER_TRANSLUCENT, type BakedBlock, type BlockLibrary } from './blocks.ts';
import type { World } from './world.ts';

export const CHUNK = 16;

/** Corner brightness by number of occluders (0 = darkest), roughly vanilla's smooth lighting. */
const AO_LEVELS = [0.42, 0.6, 0.79, 1.0];
const srgbToLinear = (c: number) => (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
const AO_LINEAR = AO_LEVELS.map(srgbToLinear);

class Buffers {
  pos: number[] = [];
  nor: number[] = [];
  uv: number[] = [];
  col: number[] = [];
  idx: number[] = [];
  get empty() { return this.idx.length === 0; }
  geometry(): THREE.BufferGeometry {
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(this.pos, 3));
    g.setAttribute('normal', new THREE.Float32BufferAttribute(this.nor, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(this.uv, 2));
    g.setAttribute('color', new THREE.Float32BufferAttribute(this.col, 3));
    g.setIndex(this.pos.length / 3 > 65535 ? new THREE.Uint32BufferAttribute(this.idx, 1) : new THREE.Uint16BufferAttribute(this.idx, 1));
    g.computeBoundingSphere();
    g.computeBoundingBox();
    return g;
  }
}

export interface ChunkMeshData {
  key: string;
  layers: (THREE.BufferGeometry | null)[]; // solid, cutout, translucent
  quads: number;
}

export type Source = 'placements' | 'terrain';

/** Meshes one box of the world. `source` picks which blocks are emitted; everything is used for culling and AO. */
export function meshBox(
  world: World,
  lib: BlockLibrary,
  source: Source,
  min: [number, number, number],
  max: [number, number, number],
): ChunkMeshData | null {
  const states = world.states;
  const blocks: BakedBlock[] = states.map((s) => lib.get(s));
  const occ = new Uint8Array(blocks.length);
  blocks.forEach((b, i) => (occ[i] = b.occludes ? 1 : 0));
  const waterFull = lib.get('minecraft:water[__full=true]');
  const emit = source === 'placements' ? world.placementAt.bind(world) : world.terrainAt.bind(world);
  const get = world.get.bind(world);
  const terrain = source === 'terrain' ? world.terrain : null;
  const occludesAt = (x: number, y: number, z: number) => occ[get(x, y, z)] === 1;

  const bufs = [new Buffers(), new Buffers(), new Buffers()];
  let quads = 0;
  for (let y = min[1]; y < max[1]; y++)
    for (let z = min[2]; z < max[2]; z++)
      for (let x = min[0]; x < max[0]; x++) {
        if (terrain && y > terrain.minY && y < terrain.height(x, z) && !world.nearPlacement(x, z) &&
            y < Math.min(terrain.height(x - 1, z), terrain.height(x + 1, z), terrain.height(x, z - 1), terrain.height(x, z + 1))) continue;
        const id = emit(x, y, z);
        if (!id) continue;
        let block = blocks[id];
        if (block.empty) continue;
        if (block.name === 'minecraft:water' && blocks[get(x, y + 1, z)].name === 'minecraft:water') block = waterFull;
        for (const q of block.quads) {
          if (q.cull !== -1) {
            const d = DIR_VEC[q.cull];
            const nid = get(x + d[0], y + d[1], z + d[2]);
            if (nid) {
              if (occ[nid]) continue;
              const nb = blocks[nid];
              if (block.selfCull && nb.name === block.name) continue;
              if (block.name === 'minecraft:water' && nb.name === 'minecraft:water') continue;
            }
          }
          const b = bufs[q.layer];
          const base = b.pos.length / 3;
          const ao = [3, 3, 3, 3];
          if (q.dir !== -1 && block.ao) {
            const n = DIR_VEC[q.dir];
            const axis = n[0] ? 0 : n[1] ? 1 : 2;
            const t1 = axis === 0 ? 1 : 0, t2 = axis === 2 ? 1 : 2;
            // face center in tangent axes
            let c1 = 0, c2 = 0;
            for (let i = 0; i < 4; i++) { c1 += q.pos[i * 3 + t1]; c2 += q.pos[i * 3 + t2]; }
            c1 /= 4; c2 /= 4;
            for (let i = 0; i < 4; i++) {
              const v = [x + q.pos[i * 3], y + q.pos[i * 3 + 1], z + q.pos[i * 3 + 2]];
              const s1 = q.pos[i * 3 + t1] >= c1 ? 0.5 : -0.5;
              const s2 = q.pos[i * 3 + t2] >= c2 ? 0.5 : -0.5;
              const p = [v[0] + n[0] * 0.5, v[1] + n[1] * 0.5, v[2] + n[2] * 0.5];
              const cell = (o1: number, o2: number) => {
                const c = [p[0], p[1], p[2]];
                c[t1] += o1;
                c[t2] += o2;
                return occludesAt(Math.floor(c[0]), Math.floor(c[1]), Math.floor(c[2])) ? 1 : 0;
              };
              const side1 = cell(s1, -s2), side2 = cell(-s1, s2), corner = cell(s1, s2);
              ao[i] = side1 && side2 ? 0 : 3 - (side1 + side2 + corner);
            }
          }
          for (let i = 0; i < 4; i++) {
            b.pos.push(x + q.pos[i * 3], y + q.pos[i * 3 + 1], z + q.pos[i * 3 + 2]);
            b.nor.push(q.normal[0], q.normal[1], q.normal[2]);
            b.uv.push(q.uv[i * 2], q.uv[i * 2 + 1]);
            const a = AO_LINEAR[ao[i]];
            b.col.push(srgbToLinear(q.color[0]) * a, srgbToLinear(q.color[1]) * a, srgbToLinear(q.color[2]) * a);
          }
          // split the quad along the brighter diagonal so AO gradients don't look creased
          if (ao[0] + ao[2] >= ao[1] + ao[3]) b.idx.push(base, base + 1, base + 2, base, base + 2, base + 3);
          else b.idx.push(base + 1, base + 2, base + 3, base + 1, base + 3, base);
          quads++;
        }
      }
  if (quads === 0) return null;
  return { key: `${source}:${min.join(',')}`, layers: bufs.map((b) => (b.empty ? null : b.geometry())), quads };
}

/** Meshes a region chunk by chunk. */
export function meshRegion(
  world: World,
  lib: BlockLibrary,
  source: Source,
  min: [number, number, number],
  max: [number, number, number],
): ChunkMeshData[] {
  const out: ChunkMeshData[] = [];
  const c0 = min.map((v) => Math.floor(v / CHUNK) * CHUNK);
  for (let cy = c0[1]; cy < max[1]; cy += CHUNK)
    for (let cz = c0[2]; cz < max[2]; cz += CHUNK)
      for (let cx = c0[0]; cx < max[0]; cx += CHUNK) {
        const lo: [number, number, number] = [Math.max(cx, min[0]), Math.max(cy, min[1]), Math.max(cz, min[2])];
        const hi: [number, number, number] = [Math.min(cx + CHUNK, max[0]), Math.min(cy + CHUNK, max[1]), Math.min(cz + CHUNK, max[2])];
        const m = meshBox(world, lib, source, lo, hi);
        if (m) out.push(m);
      }
  return out;
}

/** Meshes the terrain in 16x16 columns, each only over the heights where faces can show. */
export function meshTerrain(world: World, lib: BlockLibrary): ChunkMeshData[] {
  const t = world.terrain;
  if (!t) return [];
  const out: ChunkMeshData[] = [];
  for (let cz = t.z0; cz < t.z0 + t.size; cz += CHUNK)
    for (let cx = t.x0; cx < t.x0 + t.size; cx += CHUNK) {
      let lo = Infinity, hi = -Infinity;
      for (let z = cz; z < Math.min(cz + CHUNK, t.z0 + t.size); z++)
        for (let x = cx; x < Math.min(cx + CHUNK, t.x0 + t.size); x++) {
          const h = t.height(x, z);
          const edge = x === t.x0 || z === t.z0 || x === t.x0 + t.size - 1 || z === t.z0 + t.size - 1;
          const nMin = edge ? t.minY : Math.min(t.height(x - 1, z), t.height(x + 1, z), t.height(x, z - 1), t.height(x, z + 1));
          lo = Math.min(lo, nMin - 1);
          if (world.nearPlacement(x, z)) for (const p of world.placements) lo = Math.min(lo, p.min[1] - 1);
          hi = Math.max(hi, h + 8);
        }
      lo = Math.max(lo, t.minY);
      for (let y0 = lo; y0 <= hi; y0 += CHUNK) {
        const m = meshBox(world, lib, 'terrain', [cx, y0, cz], [Math.min(cx + CHUNK, t.x0 + t.size), Math.min(y0 + CHUNK, hi + 1), Math.min(cz + CHUNK, t.z0 + t.size)]);
        if (m) out.push(m);
      }
    }
  return out;
}

export { LAYER_SOLID, LAYER_TRANSLUCENT };

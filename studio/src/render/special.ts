// Blocks that Minecraft draws with code instead of JSON models (chests, beds, signs, banners, water, lava),
// and the fallback shapes used when a block has no model at all (no assets installed, or a modded block).
import type { BlockState } from '../format/structure.ts';
import type { ElementJson, FaceJson } from './blockmodels.ts';
import { DIRS } from './blockmodels.ts';

export interface SpecialModel {
  elements: ElementJson[];
  textures: Record<string, string>;
  y: number; // block-state y rotation
  x?: number;
  uvScale?: number;
}

const FACING_Y: Record<string, number> = { north: 0, east: 90, south: 180, west: 270 };

type Faces = ElementJson['faces'];
function allFaces(texture: string, extra: Partial<FaceJson> = {}): Faces {
  const f: Faces = {};
  for (const d of DIRS) f[d] = { texture, ...extra };
  return f;
}
function box(from: [number, number, number], to: [number, number, number], faces: Faces, rotation?: ElementJson['rotation']): ElementJson {
  return { from, to, faces, rotation };
}

/** Vanilla entity-model box UV layout (texture offset u, v; box size w, h, d). Returns face -> uv rectangle. */
function entityBoxUv(u: number, v: number, w: number, h: number, d: number) {
  return {
    top: [u + d, v, u + d + w, v + d] as [number, number, number, number],
    bottom: [u + d + w, v, u + d + 2 * w, v + d] as [number, number, number, number],
    left: [u, v + d, u + d, v + d + h] as [number, number, number, number],
    front: [u + d, v + d, u + d + w, v + d + h] as [number, number, number, number],
    right: [u + d + w, v + d, u + 2 * d + w, v + d + h] as [number, number, number, number],
    back: [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h] as [number, number, number, number],
  };
}

const flipV = (r: [number, number, number, number]): [number, number, number, number] => [r[0], r[3], r[2], r[1]];

function chestBox(from: [number, number, number], to: [number, number, number], u: number, v: number): ElementJson {
  const w = to[0] - from[0], h = to[1] - from[1], d = to[2] - from[2];
  const r = entityBoxUv(u, v, w, h, d);
  // The chest model is built upside down (entity space has y pointing down) and turned to face north.
  return box(from, to, {
    up: { texture: '#chest', uv: r.bottom, rotation: 180 },
    down: { texture: '#chest', uv: r.top },
    north: { texture: '#chest', uv: flipV(r.front), rotation: 180 },
    south: { texture: '#chest', uv: flipV(r.back), rotation: 180 },
    west: { texture: '#chest', uv: flipV(r.right), rotation: 180 },
    east: { texture: '#chest', uv: flipV(r.left), rotation: 180 },
  });
}

const WOOD_OF_SIGN = /^minecraft:(.+?)_(wall_hanging_sign|hanging_sign|wall_sign|sign)$/;
const COLORS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan',
  'purple', 'blue', 'brown', 'green', 'red', 'black'];

/** Returns a code-built model for blocks Minecraft renders without JSON models, or null. */
export function specialModel(state: BlockState): SpecialModel | null {
  const name = state.name;
  const p = state.properties;
  const facingY = FACING_Y[p.facing ?? 'north'] ?? 0;

  if (name === 'minecraft:chest' || name === 'minecraft:trapped_chest' || name === 'minecraft:ender_chest') {
    const tex = name === 'minecraft:ender_chest' ? 'ender' : name === 'minecraft:trapped_chest' ? 'trapped' : 'normal';
    // Double chests are drawn as two single chests (good enough for a preview).
    return {
      textures: { chest: `minecraft:entity/chest/${tex}`, particle: `minecraft:entity/chest/${tex}` },
      elements: [
        chestBox([1, 0, 1], [15, 10, 15], 0, 19),
        chestBox([1, 10, 1], [15, 14, 15], 0, 0),
        chestBox([7, 7, 0], [9, 11, 1], 0, 0),
      ],
      y: facingY,
      uvScale: 64,
    };
  }

  const bed = /^minecraft:(\w+)_bed$/.exec(name);
  if (bed && COLORS.includes(bed[1])) {
    const head = (p.part ?? 'foot') === 'head';
    const v0 = head ? 0 : 22;
    const r = entityBoxUv(0, v0, 16, 16, 6);
    const t = '#bed';
    const elements: ElementJson[] = [
      box([0, 3, 0], [16, 9, 16], {
        up: { texture: t, uv: r.front, rotation: head ? 0 : 0 },
        down: { texture: t, uv: r.back },
        // mattress sides are 6 tall and 16 long in the texture: turn them
        west: { texture: t, uv: r.left, rotation: 270 },
        east: { texture: t, uv: r.right, rotation: 90 },
        ...(head ? { north: { texture: t, uv: r.top, rotation: 180 } } : { south: { texture: t, uv: r.top } }),
      }),
    ];
    // legs at the two corners of this half's outer end
    const z = head ? 0 : 13;
    const leg = (x: number, u: number, v: number) =>
      box([x, 0, z], [x + 3, 3, z + 3], allFaces(t, { uv: [u + 3, v + 3, u + 6, v + 6] }));
    elements.push(leg(0, 50, head ? 0 : 12), leg(13, 50, head ? 6 : 18));
    return { textures: { bed: `minecraft:entity/bed/${bed[1]}`, particle: 'minecraft:block/oak_planks' }, elements, y: facingY, uvScale: 64 };
  }

  const sign = WOOD_OF_SIGN.exec(name);
  if (sign) {
    const wood = sign[1];
    const t = `#sign`;
    // sign texture is 64x32: we use a 64 scale and double v to keep pixels square
    const r = entityBoxUv(0, 0, 24, 12, 2);
    const dv = (q: [number, number, number, number]): [number, number, number, number] => [q[0], q[1] * 2, q[2], q[3] * 2];
    const boardFaces: Faces = {
      north: { texture: t, uv: dv(r.front) },
      south: { texture: t, uv: dv(r.back) },
      up: { texture: t, uv: dv(r.top) },
      down: { texture: t, uv: dv(r.bottom) },
      west: { texture: t, uv: dv(r.left) },
      east: { texture: t, uv: dv(r.right) },
    };
    const textures = { sign: `minecraft:entity/signs/${wood}`, particle: `minecraft:block/${wood}_planks` };
    if (sign[2] === 'wall_sign') {
      // facing = the way the text faces; the board hangs on the opposite side of the block
      return { textures, elements: [box([0, 4.5, 14], [16, 12.5, 16], boardFaces)], y: facingY, uvScale: 64 };
    }
    if (sign[2] === 'sign') {
      const rot = Number(p.rotation ?? 0) * 22.5;
      const post = entityBoxUv(0, 14, 2, 14, 2);
      return {
        textures,
        elements: [
          box([0, 9.33, 7], [16, 17.33, 9], boardFaces),
          box([7.25, 0, 7.25], [8.75, 9.33, 8.75], allFaces(t, { uv: dv(post.front) })),
        ],
        y: Math.round(rot / 90) * 90,
        uvScale: 64,
      };
    }
    return null; // hanging signs: fallback
  }

  const banner = /^minecraft:(\w+?)_(wall_)?banner$/.exec(name);
  if (banner && COLORS.includes(banner[1])) {
    const wool = `minecraft:block/${banner[1]}_wool`;
    const textures = { wool, bar: 'minecraft:block/oak_planks', particle: wool };
    if (banner[2]) {
      return {
        textures,
        elements: [
          box([1, -13, 14.5], [15, 14, 15.5], allFaces('#wool')),
          box([0, 14, 14], [16, 15.5, 15.5], allFaces('#bar')),
        ],
        y: facingY,
      };
    }
    const rot = Number(p.rotation ?? 0) * 22.5;
    return {
      textures,
      elements: [
        box([7, 0, 7], [9, 30, 9], allFaces('#bar')),
        box([1, 3, 9], [15, 29, 10], allFaces('#wool')),
        box([0, 28, 9], [16, 29.5, 10.5], allFaces('#bar')),
      ],
      y: Math.round(rot / 90) * 90,
    };
  }

  if (name === 'minecraft:water' || name === 'minecraft:lava' || name === 'minecraft:bubble_column') {
    const lava = name === 'minecraft:lava';
    const tex = lava ? 'minecraft:block/lava_still' : 'minecraft:block/water_still';
    const top = p.__full === 'true' ? 16 : 14;
    const tint = lava ? undefined : 0;
    const f = (cull: string): FaceJson => ({ texture: '#t', cullface: cull, tintindex: tint });
    return {
      textures: { t: tex, particle: tex },
      elements: [
        box([0, 0, 0], [16, top, 16], {
          down: f('down'), up: { texture: '#t', tintindex: tint, ...(top === 16 ? { cullface: 'up' } : {}) },
          north: f('north'), south: f('south'), west: f('west'), east: f('east'),
        }),
      ],
      y: 0,
    };
  }
  return null;
}

// ---------------------------------------------------------------- fallback shapes (no model available)

/** Texture id for a placeholder, drawn by the atlas from the block name. */
export const placeholderTexture = (name: string) => `placeholder:${name}`;

/** A rough model by block-name pattern, so structures stay readable without assets (stairs look like stairs). */
export function fallbackModel(state: BlockState): SpecialModel | null {
  const name = state.name;
  const short = name.replace(/^[a-z0-9_.-]+:/, '');
  const p = state.properties;
  const tex = placeholderTexture(name);
  const textures = { all: tex, particle: tex };
  const full = allFaces('#all');
  const facingY = FACING_Y[p.facing ?? 'north'] ?? 0;
  if (/(^|_)air$/.test(short) || short === 'structure_void' || short === 'barrier' || short === 'light') return { textures, elements: [], y: 0 };

  if (short.endsWith('_stairs')) {
    // this stairs shape faces east at y = 0 (like vanilla's model); our facing table is north-based
    const top = p.half === 'top';
    return {
      textures,
      elements: [box([0, 0, 0], [16, 8, 16], full), box([8, 8, 0], [16, 16, 16], full)],
      y: (facingY + 270) % 360,
      x: top ? 180 : 0,
    };
  }
  if (short.endsWith('_slab')) {
    if (p.type === 'double') return { textures, elements: [box([0, 0, 0], [16, 16, 16], full)], y: 0 };
    return { textures, elements: [p.type === 'top' ? box([0, 8, 0], [16, 16, 16], full) : box([0, 0, 0], [16, 8, 16], full)], y: 0 };
  }
  if (short.endsWith('_carpet') || short === 'moss_carpet') return { textures, elements: [box([0, 0, 0], [16, 1, 16], full)], y: 0 };
  if (short.endsWith('_pressure_plate')) return { textures, elements: [box([1, 0, 1], [15, 1, 15], full)], y: 0 };
  if (short.endsWith('_button')) return { textures, elements: [box([5, 6, 14], [11, 10, 16], full)], y: facingY };
  if (short.endsWith('_fence') || short.endsWith('_wall') || short.endsWith('_pane') || short === 'iron_bars' || short === 'chain') {
    const pane = short.endsWith('_pane') || short === 'iron_bars';
    const wall = short.endsWith('_wall');
    const r = pane ? 1 : wall ? 4 : 2;
    const h = wall && p.up !== 'false' ? 16 : 16;
    const els = [box([8 - r, 0, 8 - r], [8 + r, h, 8 + r], full)];
    const arm = (dir: string) => {
      const v = p[dir];
      if (!v || v === 'false' || v === 'none') return;
      const ah = wall ? (v === 'tall' ? 16 : 14) : pane ? 16 : 15;
      const ay = pane ? 0 : wall ? 0 : 6;
      const w = pane ? 1 : wall ? 3 : 1;
      if (dir === 'north') els.push(box([8 - w, ay, 0], [8 + w, ah, 8], full));
      if (dir === 'south') els.push(box([8 - w, ay, 8], [8 + w, ah, 16], full));
      if (dir === 'west') els.push(box([0, ay, 8 - w], [8, ah, 8 + w], full));
      if (dir === 'east') els.push(box([8, ay, 8 - w], [16, ah, 8 + w], full));
    };
    ['north', 'south', 'west', 'east'].forEach(arm);
    return { textures, elements: els, y: 0 };
  }
  if (short.endsWith('_door')) {
    const open = p.open === 'true';
    const hingeRight = p.hinge === 'right';
    // closed: panel on the far side of the block from where it faces
    let rot = facingY + 180;
    if (open) rot += hingeRight ? -90 : 90;
    return { textures, elements: [box([0, 0, 0], [16, 16, 3], full)], y: ((rot % 360) + 360) % 360 };
  }
  if (short.endsWith('_trapdoor')) {
    if (p.open === 'true') return { textures, elements: [box([0, 0, 13], [16, 16, 16], full)], y: facingY };
    return { textures, elements: [p.half === 'top' ? box([0, 13, 0], [16, 16, 16], full) : box([0, 0, 0], [16, 3, 16], full)], y: 0 };
  }
  if (short === 'torch' || short.endsWith('_torch') && !short.includes('wall')) return { textures, elements: [box([7, 0, 7], [9, 10, 9], full)], y: 0 };
  if (short.endsWith('wall_torch'))
    return { textures, elements: [box([7, 3.5, 11], [9, 13.5, 13], full, { origin: [8, 6, 16], axis: 'x', angle: -22.5 })], y: (facingY + 180) % 360 };
  if (short === 'lantern' || short === 'soul_lantern') return { textures, elements: [box([5, 0, 5], [11, 7, 11], full)], y: 0 };
  if (short.endsWith('_sign') || short.endsWith('_banner')) return { textures, elements: [box([0, 4, 14], [16, 12, 16], full)], y: facingY };
  if (short === 'flower_pot' || short.startsWith('potted_')) return { textures, elements: [box([5, 0, 5], [11, 6, 11], full)], y: 0 };
  if (short === 'campfire' || short === 'soul_campfire') return { textures, elements: [box([0, 0, 0], [16, 7, 16], full)], y: 0 };
  if (/(sapling|flower|poppy|dandelion|tulip|orchid|allium|bluet|daisy|cornflower|lily_of|grass$|fern$|bush|roots|vine$)/.test(short) && short !== 'grass_block') {
    return {
      textures,
      elements: [
        box([0.8, 0, 8], [15.2, 16, 8], { north: { texture: '#all' }, south: { texture: '#all' } }, { origin: [8, 8, 8], axis: 'y', angle: 45, rescale: true }),
        box([8, 0, 0.8], [8, 16, 15.2], { west: { texture: '#all' }, east: { texture: '#all' } }, { origin: [8, 8, 8], axis: 'y', angle: 45, rescale: true }),
      ],
      y: 0,
    };
  }
  return { textures, elements: [box([0, 0, 0], [16, 16, 16], full)], y: 0 };
}

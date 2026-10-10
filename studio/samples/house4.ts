// Sample: the timber cottage (house) v4 draft (v3 plus the quality-check fixes below). Keeps v2's design (Carlton: "very nice cute") and adds decoration
// around it, from his note "would like a little more detail such as decorations".
// v2 -> v3: the box grows by one block on each side and two at the front for a yard; v2's blocks are unchanged except
// a back gable window and the flower boxes, which become coarse dirt in a trapdoor rim (flowers can't stand on a trapdoor in the game).
// Added: a front fence with lantern gate posts, a stone path, leaf bushes, a bench by the door, a rain barrel,
// lanterns under the eaves, shutters on the gable window, a woodpile and chopping block at the back, a composter
// and a crate, ivy on the back corner posts, moss at the foot of the walls, a bookshelf, a candle and a carpet inside.
// v3 -> v4: the roof is two-tone (dark oak eave rows and gable-edge rows, spruce between) with upside-down dark oak
// stairs under the gable overhangs (eave corners and the second step), so the overhang reads; the lone fence of the
// table is gone (the table is a barrel with a board on it).
//   npx tsx samples/house4.ts <out.json>
import fs from 'node:fs';
import { Builder, type Vec } from './builder.ts';
import { stringifyStructure } from '../src/format/structure.ts';

const stairs = (facing: string, half = 'bottom') => `spruce_stairs[facing=${facing},half=${half},shape=straight]`;
const dark = (facing: string, half = 'bottom') => `dark_oak_stairs[facing=${facing},half=${half},shape=straight]`;
const trap = (facing: string, open = true, half = 'bottom') => `spruce_trapdoor[facing=${facing},half=${half},open=${open}]`;
const fence = (east: boolean, west: boolean) => `oak_fence[east=${east},west=${west},north=false,south=false]`;

/** v2's coordinates, shifted into the bigger box (one block left, two to the front). */
class Shifted {
  constructor(readonly b: Builder, readonly ox: number, readonly oz: number) {}
  private v = (p: Vec): Vec => [p[0] + this.ox, p[1], p[2] + this.oz];
  set(x: number, y: number, z: number, s: string) { this.b.set(x + this.ox, y, z + this.oz, s); }
  get(x: number, y: number, z: number) { return this.b.get(x + this.ox, y, z + this.oz); }
  fill(a: Vec, c: Vec, s: string) { this.b.fill(this.v(a), this.v(c), s); }
  walls(a: Vec, c: Vec, s: string) { this.b.walls(this.v(a), this.v(c), s); }
}

export function house4() {
  const root = new Builder(9, 9, 11);
  const b = new Shifted(root, 1, 2); // from here on, coordinates are v2's: walls x 1..5, z 1..7; the yard is z -2..-1
  const W0 = 1, W1 = 5;
  const Z0 = 1, Z1 = 7;

  // ---- v2, unchanged (see samples/house.ts)
  b.fill([W0, 0, Z0], [W1, 0, Z1], 'cobblestone');
  b.fill([W0 + 1, 0, Z0 + 1], [W1 - 1, 0, Z1 - 1], 'spruce_planks');
  b.set(3, 0, 0, 'cobblestone_slab[type=top]');
  b.walls([W0, 1, Z0], [W1, 1, Z1], 'cobblestone');
  b.walls([W0, 2, Z0], [W1, 3, Z1], 'white_terracotta');
  for (const [x, z] of [[W0, Z0], [W1, Z0], [W0, Z1], [W1, Z1]]) b.fill([x, 1, z], [x, 4, z], 'spruce_log[axis=y]');
  for (const x of [W0, W1]) b.fill([x, 2, 4], [x, 3, 4], 'stripped_spruce_log[axis=y]');
  b.fill([W0 + 1, 4, Z0], [W1 - 1, 4, Z0], 'stripped_spruce_log[axis=x]');
  b.fill([W0 + 1, 4, Z1], [W1 - 1, 4, Z1], 'stripped_spruce_log[axis=x]');
  b.fill([W0, 4, Z0 + 1], [W0, 4, Z1 - 1], 'stripped_spruce_log[axis=z]');
  b.fill([W1, 4, Z0 + 1], [W1, 4, Z1 - 1], 'stripped_spruce_log[axis=z]');
  for (let z = 0; z <= 8; z++) {
    const edge = z === 0 || z === 8; // v4: gable-edge rows and eave rows dark oak, the rest spruce
    b.set(0, 4, z, dark('east'));
    b.set(6, 4, z, dark('west'));
    b.set(1, 5, z, (edge ? dark : stairs)('east'));
    b.set(5, 5, z, (edge ? dark : stairs)('west'));
    b.set(2, 6, z, (edge ? dark : stairs)('east'));
    b.set(4, 6, z, (edge ? dark : stairs)('west'));
    b.set(3, 7, z, 'spruce_slab[type=bottom]');
  }
  for (const z of [0, 8]) {
    b.set(0, 3, z, dark('west', 'top'));
    b.set(6, 3, z, dark('east', 'top'));
    b.set(1, 4, z, dark('west', 'top')); // v4: trim under the second step of the gable overhang
    b.set(5, 4, z, dark('east', 'top'));
  }
  for (const z of [Z0, Z1]) {
    b.fill([2, 5, z], [4, 5, z], 'white_terracotta');
    b.set(3, 6, z, 'stripped_spruce_log[axis=y]');
  }
  b.set(3, 5, Z0, 'glass_pane[east=true,west=true,north=false,south=false]');
  b.set(3, 1, Z0, 'oak_door[facing=south,half=lower,hinge=left,open=false]');
  b.set(3, 2, Z0, 'oak_door[facing=south,half=upper,hinge=left,open=false]');
  b.set(2, 2, 0, 'wall_torch[facing=north]');
  b.set(4, 2, 0, 'wall_torch[facing=north]');
  b.set(3, 3, Z0, 'stripped_spruce_log[axis=x]');
  for (const z of [3, 5]) {
    b.set(W0, 2, z, 'glass_pane[north=true,south=true,east=false,west=false]');
    b.set(W1, 2, z, 'glass_pane[north=true,south=true,east=false,west=false]');
  }
  // shutters as in v2; the flower boxes are rebuilt below
  for (const [x, facing] of [[0, 'west'], [6, 'east']] as const) for (const z of [2, 6]) b.set(x, 2, z, trap(facing));
  b.set(2, 2, Z1, 'glass_pane[east=true,west=true,north=false,south=false]');
  b.set(4, 1, 6, 'furnace[facing=north,lit=false]');
  b.fill([4, 2, 6], [4, 6, 6], 'bricks');
  b.set(4, 7, 6, 'bricks');
  b.set(4, 8, 6, 'cobblestone_wall[up=true,north=none,south=none,east=none,west=none]');
  b.set(2, 1, 6, 'red_bed[facing=north,part=foot]');
  b.set(2, 1, 5, 'red_bed[facing=north,part=head]');
  b.set(4, 1, 5, 'chest[facing=west,type=single]');
  b.set(2, 1, 2, 'barrel[facing=up,open=false]'); // v4: table is a barrel (was a lone fence)
  b.set(2, 2, 2, 'oak_pressure_plate');
  b.set(2, 1, 3, 'oak_stairs[facing=south,half=bottom,shape=straight]');
  b.set(4, 1, 2, 'crafting_table');
  b.set(3, 1, 3, 'red_carpet');
  b.set(3, 1, 4, 'red_carpet');
  b.set(4, 3, 4, 'lantern[hanging=false]');

  // ---- v3 decorations
  // flower boxes: coarse dirt in a rim of open trapdoors (outer face and both ends), three flowers each side
  for (const [x, out, facing] of [[0, -1, 'west'], [6, 7, 'east']] as const) {
    for (const [z, flower] of [[3, 'poppy'], [4, 'azure_bluet'], [5, 'poppy']] as const) {
      b.set(x, 1, z, 'coarse_dirt');
      b.set(x, 2, z, flower);
      b.set(out, 1, z, trap(facing));
    }
    b.set(x, 1, 2, trap('north'));
    b.set(x, 1, 6, trap('south'));
    b.set(out, 0, 4, 'moss_carpet');                     // moss under the box
  }
  // shutters on the gable window
  b.set(2, 5, 0, trap('north'));
  b.set(4, 5, 0, trap('north'));
  // lanterns hanging under the eaves at the front corners
  b.set(0, 3, 1, 'lantern[hanging=true]');
  b.set(6, 3, 1, 'lantern[hanging=true]');
  // front yard: stone path, low fence with lantern gate posts, leaf bushes
  b.set(3, 0, -1, 'cobblestone_slab[type=bottom]');
  b.set(3, 0, -2, 'cobblestone_slab[type=bottom]');
  for (let x = -1; x <= 7; x++) {
    if (x >= 3 - 1 && x <= 3 + 1) continue;
    b.set(x, 0, -2, fence(x < 2 ? true : x < 7, x < 2 ? x > -1 : true));
  }
  for (const x of [2, 4]) {
    b.set(x, 0, -2, fence(x === 4, x === 2));
    b.set(x, 1, -2, 'lantern[hanging=false]');
  }
  b.set(-1, 0, -1, 'oak_leaves[persistent=true]');
  b.set(7, 0, -1, 'oak_leaves[persistent=true]');
  // bench by the door, right of the step, with a sign armrest; a rain barrel left of the door
  b.set(4, 0, 0, 'oak_stairs[facing=south,half=bottom,shape=straight]');
  b.set(5, 0, 0, 'oak_stairs[facing=south,half=bottom,shape=straight]');
  b.set(6, 0, 0, 'oak_wall_sign[facing=east]');
  b.set(1, 0, 0, 'barrel[facing=up,open=false]');
  // back: woodpile with a chopping block, a composter and a crate
  b.fill([0, 0, 8], [2, 0, 8], 'spruce_log[axis=z]'); // log ends face out
  b.fill([0, 1, 8], [1, 1, 8], 'spruce_log[axis=z]');
  b.set(3, 0, 8, 'stripped_spruce_log[axis=y]');
  b.set(5, 0, 8, 'composter[level=4]');
  b.set(6, 0, 8, 'barrel[facing=up,open=false]');
  b.set(7, 0, 7, 'moss_carpet');
  // a window in the back gable too (the one v2 block changed besides the flower boxes)
  b.set(3, 5, Z1, 'glass_pane[east=true,west=true,north=false,south=false]');
  // ivy climbing the back corner posts
  for (const y of [1, 2, 3]) {
    b.set(0, y, 7, 'vine[east=true,north=false,south=false,west=false,up=false]');
    if (y < 3) b.set(6, y, 7, 'vine[west=true,north=false,south=false,east=false,up=false]');
  }
  // inside: a bookshelf by the workbench, a candle on the chest, carpet by the bed, a shelf over the workbench
  b.set(4, 2, 5, 'candle[candles=2,lit=false]');
  b.set(3, 1, 5, 'brown_carpet');
  b.set(4, 3, 2, 'spruce_trapdoor[facing=west,half=top,open=false]');
  b.set(4, 1, 3, 'bookshelf');

  return root.toStructure('house', {
    author: 'claude',
    description:
      'Timber cottage, style A: cobblestone plinth, plaster and spruce frame, spruce gable roof, brick chimney. 5 x 7 walls, 3 high. ' +
      'Front yard with a fence, lantern gate posts and a stone path; bench by the door, flower boxes, woodpile at the back. ' +
      'Not yet tested by a builder: azure_bluet, moss_carpet, candle, vine, bookshelf.',
    tags: ['sample', 'housing'],
  });
}

if (process.argv[1]?.endsWith('house4.ts')) {
  const out = process.argv[2];
  if (!out) throw new Error('usage: npx tsx samples/house4.ts <out.json>');
  fs.writeFileSync(out, stringifyStructure(house4()));
  console.log(`wrote ${out}`);
}

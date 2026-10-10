// Sample: a small stone keep (a landmark, style A moving to stone): 9 x 9 tower, two floors, overhanging
// battlements on corbels, corner turrets, banners over the door.
import { Builder, noise } from './builder.ts';

const brick = (x: number, y: number, z: number) => {
  const n = noise(x, y, z, 7);
  return n < 0.12 ? 'mossy_stone_bricks' : n < 0.24 ? 'cracked_stone_bricks' : 'stone_bricks';
};
const st = (block: string, facing: string, half = 'bottom') => `${block}[facing=${facing},half=${half},shape=straight]`;

export function keep() {
  const S = 11; // footprint with the one-block battlement overhang
  const b = new Builder(S, 12, S);
  const A = 1, B = 9; // tower body x/z range

  // y = 0: foundation level with the ground; a cobblestone apron around the tower
  b.fill([A, 0, A], [B, 0, B], 'cobblestone');
  b.fill([A + 1, 0, A + 1], [B - 1, 0, B - 1], 'stone_bricks');
  b.walls([0, 0, 0], [10, 0, 10], (x, y, z) => (noise(x, y, z, 3) < 0.35 ? 'mossy_cobblestone' : 'cobblestone'));

  // body: battered plinth (y 1), walls up to y 7, quoins at the corners
  b.walls([A, 1, A], [B, 7, B], brick);
  b.walls([A, 1, A], [B, 1, B], (x, y, z) => (noise(x, y, z, 5) < 0.3 ? 'mossy_cobblestone' : 'cobblestone'));
  for (const [x, z] of [[A, A], [B, A], [A, B], [B, B]])
    for (let y = 2; y <= 7; y++) b.set(x, y, z, y % 2 ? 'polished_andesite' : 'stone_bricks');
  // plinth slope: stairs leaning on the wall, all round except at the door
  for (let i = A; i <= B; i++) {
    b.set(i, 1, 0, st('cobblestone_stairs', 'south'));
    b.set(i, 1, 10, st('cobblestone_stairs', 'north'));
    b.set(0, 1, i, st('cobblestone_stairs', 'east'));
    b.set(10, 1, i, st('cobblestone_stairs', 'west'));
  }

  // floors: ground floor y 0, upper floor y 4 (spruce), roof deck y 7
  b.fill([A + 1, 4, A + 1], [B - 1, 4, B - 1], 'spruce_planks');
  b.fill([A + 1, 7, A + 1], [B - 1, 7, B - 1], 'stone_bricks');
  // stairs up to the first floor along the west wall (soldiers don't climb ladders)
  for (let i = 0; i < 3; i++) {
    b.set(A + 1, 1 + i, 6 - i, st('spruce_stairs', 'north'));
    b.set(A + 1, 4, 6 - i, 'air');
  }
  b.set(A + 1, 4, 3, 'air');
  // and a hatch to the roof
  b.set(B - 1, 5, B - 1, st('spruce_stairs', 'south'));
  b.set(B - 1, 6, B - 2, st('spruce_stairs', 'south'));
  b.set(B - 1, 7, B - 3, 'air');
  b.set(B - 1, 7, B - 2, 'air');

  // door with a stone arch and steps, banners and lanterns
  b.set(5, 1, A, 'spruce_door[facing=south,half=lower,hinge=left,open=false]');
  b.set(5, 2, A, 'spruce_door[facing=south,half=upper,hinge=left,open=false]');
  b.set(5, 1, 0, 'cobblestone_slab[type=bottom]');
  b.set(4, 3, A, st('stone_brick_stairs', 'east', 'top'));
  b.set(6, 3, A, st('stone_brick_stairs', 'west', 'top'));
  b.set(5, 3, A, 'chiseled_stone_bricks');
  b.set(3, 6, 0, 'red_wall_banner[facing=north]');
  b.set(7, 6, 0, 'red_wall_banner[facing=north]');
  b.set(4, 2, 0, 'wall_torch[facing=north]');
  b.set(6, 2, 0, 'wall_torch[facing=north]');

  // windows: arrow slits on the ground floor, tall windows upstairs, on every side
  const paneX = 'glass_pane[east=true,west=true,north=false,south=false]';
  const paneZ = 'glass_pane[north=true,south=true,east=false,west=false]';
  for (const z of [A, B]) {
    for (const x of [3, 7]) if (!(z === A && x === 5)) b.set(x, 2, z, 'iron_bars[east=false,west=false,north=true,south=true]');
    // the front keeps only its middle window: the banners hang where the others would be
    for (const x of z === A ? [5] : [3, 5, 7]) { b.set(x, 5, z, paneX); b.set(x, 6, z, paneX); }
  }
  for (const x of [A, B]) {
    for (const z of [3, 7]) b.set(x, 2, z, 'iron_bars[north=false,south=false,east=true,west=true]');
    for (const z of [3, 5, 7]) { b.set(x, 5, z, paneZ); b.set(x, 6, z, paneZ); }
  }
  // window sills: slabs under the upper windows outside
  b.set(5, 4, 0, 'stone_brick_slab[type=top]');
  for (const x of [3, 5, 7]) b.set(x, 4, 10, 'stone_brick_slab[type=top]');
  for (const z of [3, 5, 7]) { b.set(0, 4, z, 'stone_brick_slab[type=top]'); b.set(10, 4, z, 'stone_brick_slab[type=top]'); }

  // corbels at y 7 carry the battlement ring out by one block
  for (let i = 0; i < S; i++) {
    b.set(i, 7, 0, st('stone_brick_stairs', 'south', 'top'));
    b.set(i, 7, 10, st('stone_brick_stairs', 'north', 'top'));
    b.set(0, 7, i, st('stone_brick_stairs', 'east', 'top'));
    b.set(10, 7, i, st('stone_brick_stairs', 'west', 'top'));
  }
  // walk deck y 8 is open; the ring wall stands on the corbels
  b.fill([1, 8, 1], [9, 8, 9], 'air');
  b.walls([0, 8, 0], [10, 8, 10], brick);
  for (let i = 0; i < S; i++)
    for (const [x, z] of [[i, 0], [i, 10], [0, i], [10, i]]) if (i % 2 === 0) b.set(x, 9, z, brick(x, 9, z));
  b.fill([1, 7, 1], [9, 7, 9], (x, y, z) => (b.get(x, y, z) === 'minecraft:air' && !(x === B - 1 && z >= B - 3) ? 'stone_bricks' : null));

  // corner turrets: 3 x 3, two blocks higher, with their own merlons and a pointed cap
  for (const [cx, cz] of [[0, 0], [8, 0], [0, 8], [8, 8]]) {
    b.walls([cx, 8, cz], [cx + 2, 10, cz + 2], brick);
    b.fill([cx + 1, 8, cz + 1], [cx + 1, 10, cz + 1], 'air');
    for (const [x, z] of [[cx, cz], [cx + 2, cz], [cx, cz + 2], [cx + 2, cz + 2]]) b.set(x, 11, z, 'stone_brick_wall[up=true,north=none,south=none,east=none,west=none]');
    b.set(cx + 1, 10, cz + 1, 'lantern[hanging=false]');
  }
  // a gap in each turret's inner side so the deck connects
  for (const [x, z] of [[2, 1], [8, 1], [2, 9], [8, 9]]) { b.set(x, 8, z, 'air'); b.set(x, 9, z, 'air'); }

  // flag on the roof
  b.fill([5, 8, 5], [5, 10, 5], 'spruce_fence[north=false,south=false,east=false,west=false]');
  b.set(5, 11, 5, 'red_banner[rotation=0]');

  // interior: ground floor storeroom, upper floor the king's room
  b.set(7, 1, 7, 'barrel[facing=up,open=false]');
  b.set(8, 1, 7, 'barrel[facing=up,open=false]');
  b.set(8, 1, 6, 'chest[facing=west,type=single]');
  b.set(8, 1, 3, 'crafting_table');
  b.set(7, 1, 2, 'hay_block[axis=y]');
  b.set(8, 1, 2, 'hay_block[axis=y]');
  b.set(5, 3, 5, 'lantern[hanging=true]');
  b.fill([4, 5, 4], [6, 5, 6], 'red_carpet');
  b.set(7, 5, 3, 'red_bed[facing=south,part=foot]');
  b.set(7, 5, 4, 'red_bed[facing=south,part=head]');
  b.set(4, 5, 7, 'chest[facing=south,type=single]');
  b.set(3, 5, 3, 'oak_fence[north=false,south=false,east=false,west=false]');
  b.set(3, 6, 3, 'oak_pressure_plate');

  return b.toStructure('keep', {
    author: 'claude',
    description: 'Small stone keep: 9 x 9 tower, two floors, overhanging battlements on corbels, corner turrets, banners over the door.',
    tags: ['sample', 'keep', 'landmark'],
  });
}

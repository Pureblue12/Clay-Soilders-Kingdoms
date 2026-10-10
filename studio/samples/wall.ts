// Sample: a town wall module (stone): 13 long, battered base, walkway, crenellated parapet on corbels,
// buttresses on the town side. The two ends are open so modules join into a long wall.
// The outside of the town is the front (z = 0).
import { Builder, noise } from './builder.ts';

const brick = (x: number, y: number, z: number) => {
  const n = noise(x, y, z, 11);
  return n < 0.14 ? 'mossy_stone_bricks' : n < 0.26 ? 'cracked_stone_bricks' : 'stone_bricks';
};
const st = (block: string, facing: string, half = 'bottom') => `${block}[facing=${facing},half=${half},shape=straight]`;

export function wall() {
  const L = 13;
  const b = new Builder(L, 8, 5);
  const X1 = L - 1;

  b.fill([0, 0, 0], [X1, 0, 4], (x, y, z) => (noise(x, y, z, 2) < 0.3 ? 'mossy_cobblestone' : 'cobblestone'));
  // body, 2 thick, walkway on top at y 5
  b.fill([0, 1, 1], [X1, 5, 2], brick);
  b.fill([0, 1, 1], [X1, 1, 2], (x, y, z) => (noise(x, y, z, 4) < 0.3 ? 'mossy_cobblestone' : 'cobblestone'));
  // battered base on both sides
  for (let x = 0; x <= X1; x++) {
    b.set(x, 1, 0, st('cobblestone_stairs', 'south'));
    b.set(x, 1, 3, st('cobblestone_stairs', 'north'));
  }
  // corbels carry the parapet one block out on the field side
  for (let x = 0; x <= X1; x++) b.set(x, 5, 0, st('stone_brick_stairs', 'south', 'top'));
  for (let x = 0; x <= X1; x++) {
    b.set(x, 6, 0, brick(x, 6, 0));
    if (x % 2 === 1) b.set(x, 7, 0, brick(x, 7, 0)); // crenels at both ends, so joined modules keep the rhythm
  }
  // a cap course on the town side of the walkway
  for (let x = 0; x <= X1; x++) b.set(x, 6, 2, x % 6 === 0 ? 'stone_brick_wall[up=true,north=none,south=none,east=none,west=none]' : 'air');
  for (const x of [0, 6, 12]) b.set(x, 7, 2, 'lantern[hanging=false]');

  // buttresses on the town side
  for (const x of [3, 9]) {
    b.fill([x, 1, 3], [x, 4, 3], brick);
    b.set(x, 5, 3, st('stone_brick_stairs', 'north'));
    b.fill([x, 1, 4], [x, 2, 4], brick);
    b.set(x, 3, 4, st('stone_brick_stairs', 'north'));
  }
  // arrow slits through the body
  for (const x of [1, 5, 7, 11]) b.fill([x, 3, 1], [x, 3, 2], 'iron_bars[east=false,west=false,north=true,south=true]');
  b.set(6, 4, 0, 'red_wall_banner[facing=north]');

  return b.toStructure('wall', {
    author: 'claude',
    description: 'Town wall module, 13 long: battered base, walkway at 5, crenellated parapet on corbels, buttresses on the town side. Ends are open to join modules.',
    tags: ['sample', 'wall', 'module'],
  });
}

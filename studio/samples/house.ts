// Sample: a timber cottage, town style A (docs/STRUCTURES.md), miniature scale: 5 x 7 walls, 3 high, gable roof.
// v1 -> v2 critique (from the renders): the eaves sat at wall-top height and hid the walls; the front was a flat
// plank face with nothing to read; windows were under the eaves. v2: a top plate beam lifts the roof one block,
// plaster panels in a timber frame, a gable window, door lights, shutters, flower boxes and a brick chimney.
import { Builder } from './builder.ts';

const stairs = (facing: string, half = 'bottom') => `spruce_stairs[facing=${facing},half=${half},shape=straight]`;

export function house() {
  const b = new Builder(7, 9, 9);
  const W0 = 1, W1 = 5; // wall x range
  const Z0 = 1, Z1 = 7; // wall z range (z = 1 is the front wall)

  // y = 0: foundation level with the ground, plank floor inside, a stone step at the door
  b.fill([W0, 0, Z0], [W1, 0, Z1], 'cobblestone');
  b.fill([W0 + 1, 0, Z0 + 1], [W1 - 1, 0, Z1 - 1], 'spruce_planks');
  b.set(3, 0, 0, 'cobblestone_slab[type=top]');

  // walls: cobblestone plinth, white plaster panels, timber frame
  b.walls([W0, 1, Z0], [W1, 1, Z1], 'cobblestone');
  b.walls([W0, 2, Z0], [W1, 3, Z1], 'white_terracotta');
  for (const [x, z] of [[W0, Z0], [W1, Z0], [W0, Z1], [W1, Z1]]) b.fill([x, 1, z], [x, 4, z], 'spruce_log[axis=y]');
  for (const z of [4]) for (const x of [W0, W1]) b.fill([x, 2, z], [x, 3, z], 'stripped_spruce_log[axis=y]');
  // top plate: a beam all round at y = 4
  b.fill([W0 + 1, 4, Z0], [W1 - 1, 4, Z0], 'stripped_spruce_log[axis=x]');
  b.fill([W0 + 1, 4, Z1], [W1 - 1, 4, Z1], 'stripped_spruce_log[axis=x]');
  b.fill([W0, 4, Z0 + 1], [W0, 4, Z1 - 1], 'stripped_spruce_log[axis=z]');
  b.fill([W1, 4, Z0 + 1], [W1, 4, Z1 - 1], 'stripped_spruce_log[axis=z]');

  // gable roof, ridge along z, one block of overhang all round
  for (let z = 0; z <= 8; z++) {
    b.set(0, 4, z, stairs('east'));
    b.set(6, 4, z, stairs('west'));
    b.set(1, 5, z, stairs('east'));
    b.set(5, 5, z, stairs('west'));
    b.set(2, 6, z, stairs('east'));
    b.set(4, 6, z, stairs('west'));
    b.set(3, 7, z, 'spruce_slab[type=bottom]');
  }
  // upside-down stairs under the gable overhang as trim
  for (const z of [0, 8]) {
    b.set(0, 3, z, stairs('west', 'top'));
    b.set(6, 3, z, stairs('east', 'top'));
  }
  // gables: plaster with a frame and a small window at the front
  for (const z of [Z0, Z1]) {
    b.fill([2, 5, z], [4, 5, z], 'white_terracotta');
    b.set(3, 6, z, 'stripped_spruce_log[axis=y]');
  }
  b.set(3, 5, Z0, 'glass_pane[east=true,west=true,north=false,south=false]');

  // front: door, two wall lights
  b.set(3, 1, Z0, 'oak_door[facing=south,half=lower,hinge=left,open=false]');
  b.set(3, 2, Z0, 'oak_door[facing=south,half=upper,hinge=left,open=false]');
  b.set(2, 2, 0, 'wall_torch[facing=north]');
  b.set(4, 2, 0, 'wall_torch[facing=north]');
  b.set(3, 3, Z0, 'stripped_spruce_log[axis=x]'); // lintel over the door

  // side windows with open shutters and a flower box under each
  for (const z of [3, 5]) {
    b.set(W0, 2, z, 'glass_pane[north=true,south=true,east=false,west=false]');
    b.set(W1, 2, z, 'glass_pane[north=true,south=true,east=false,west=false]');
  }
  for (const [x, facing] of [[0, 'west'], [6, 'east']] as const) {
    for (const z of [2, 6]) b.set(x, 2, z, `spruce_trapdoor[facing=${facing},half=bottom,open=true]`);
    for (const z of [3, 5]) {
      b.set(x, 1, z, `spruce_trapdoor[facing=${facing},half=top,open=false]`);
      b.set(x, 2, z, 'poppy');
    }
    b.set(x, 1, 4, `spruce_trapdoor[facing=${facing},half=top,open=false]`);
    b.set(x, 2, 4, 'azure_bluet');
  }
  // back window
  b.set(2, 2, Z1, 'glass_pane[east=true,west=true,north=false,south=false]');

  // brick chimney through the back of the east roof slope, hearth inside
  b.set(4, 1, 6, 'furnace[facing=north,lit=false]');
  b.fill([4, 2, 6], [4, 6, 6], 'bricks');
  b.set(4, 7, 6, 'bricks');
  b.set(4, 8, 6, 'cobblestone_wall[up=true,north=none,south=none,east=none,west=none]');

  // furniture: bed, chest, table (fence + pressure plate), chair, rug
  b.set(2, 1, 6, 'red_bed[facing=north,part=foot]');
  b.set(2, 1, 5, 'red_bed[facing=north,part=head]');
  b.set(4, 1, 5, 'chest[facing=west,type=single]');
  b.set(2, 1, 2, 'oak_fence[north=false,south=false,east=false,west=false]');
  b.set(2, 2, 2, 'oak_pressure_plate');
  b.set(2, 1, 3, 'oak_stairs[facing=south,half=bottom,shape=straight]');
  b.set(4, 1, 2, 'crafting_table');
  b.set(3, 1, 3, 'red_carpet');
  b.set(3, 1, 4, 'red_carpet');
  b.set(4, 3, 4, 'lantern[hanging=false]');

  return b.toStructure('house', {
    author: 'claude',
    description: 'Timber cottage, style A: cobblestone plinth, plaster and spruce frame, spruce gable roof, brick chimney. 5 x 7 walls, 3 high.',
    tags: ['sample', 'housing'],
  });
}

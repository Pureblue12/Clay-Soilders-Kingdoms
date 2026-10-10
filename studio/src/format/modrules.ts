// Mod-specific checks: can the Clay Soldiers builders place these blocks?
// Copied from the buildability checklist in docs/STRUCTURES.md and tools/make_batch1_structures.py (D46, D61).
// Keep in sync when the checklist changes.
import { shortName } from './structure.ts';

/** A GameTest placed it. */
export const KNOWN_OK = new Set([
  'cobblestone', 'cobblestone_slab', 'oak_planks', 'oak_slab', 'oak_stairs', 'oak_log', 'oak_wood', 'stripped_oak_log',
  'stripped_oak_wood', 'spruce_log', 'oak_door', 'spruce_door', 'spruce_trapdoor', 'blue_wool', 'cyan_wool',
  'green_wool', 'light_blue_wool', 'glowstone', 'torch', 'wall_torch', 'oak_wall_sign', 'scaffolding',
  'chest', 'barrel', 'red_bed', 'oak_fence', 'spruce_fence', 'red_carpet', 'white_carpet', 'brown_carpet', 'furnace',
  'smoker', 'crafting_table', 'composter', 'grindstone', 'glass_pane', 'hay_block', 'oak_pressure_plate',
  'oak_trapdoor', 'cobblestone_wall', 'cobblestone_stairs', 'mossy_cobblestone', 'stone_bricks', 'bricks',
  'white_terracotta', 'dark_oak_planks', 'dark_oak_log', 'dark_oak_stairs', 'spruce_planks', 'spruce_stairs',
  'spruce_slab', 'stripped_spruce_log', 'coarse_dirt', 'dirt', 'gravel', 'flower_pot', 'campfire', 'lantern',
  'oak_leaves', 'chain', 'stone_brick_stairs', 'oak_button', 'poppy',
]);

/** Builders can't place these: no item to fetch, block data or entities (a decoration step adds some later). */
export const BROKEN = new Set(['water', 'lava', 'fire', 'farmland', 'dirt_path', 'water_cauldron']);

/** Soldiers can't open these (DOORS.md): fine as decoration, never in a walkway. */
export const NO_PASSAGE = new Set(['iron_door']);

export interface BuildabilityReport {
  broken: string[];
  noPassage: string[];
  unknown: string[];
}

export function buildability(blockNames: Iterable<string>): BuildabilityReport {
  const r: BuildabilityReport = { broken: [], noPassage: [], unknown: [] };
  for (const full of new Set(blockNames)) {
    const n = shortName(full);
    if (n === 'air' || n === 'structure_void') continue;
    if (BROKEN.has(n) || n.startsWith('potted_')) r.broken.push(n);
    else if (NO_PASSAGE.has(n) || n.endsWith('_fence_gate')) r.noPassage.push(n);
    else if (!KNOWN_OK.has(n)) r.unknown.push(n);
  }
  return r;
}

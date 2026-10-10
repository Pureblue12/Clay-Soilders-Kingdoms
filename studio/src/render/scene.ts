// Puts a structure version into a test world and shows it in a viewer. Shared by the app and the headless renderer.
import { toGrid, type StructureFile } from '../format/structure.ts';
import type { BlockLibrary } from './blocks.ts';
import type { Viewer } from './viewer.ts';
import { World, type LayerFilter, type TerrainKind } from './world.ts';

export interface SceneOptions {
  terrain: TerrainKind;
  terrainSize: number;
  trees: boolean;
  seed: number;
  layer: LayerFilter;
  markers: boolean;
}

export const DEFAULT_SCENE: SceneOptions = {
  terrain: 'flat',
  terrainSize: 96,
  trees: false,
  seed: 1,
  layer: { mode: 'all', y: 0 },
  markers: false,
};

/** The structure's layer that is level with the terrain's top block (meta.groundLayer, default 0). */
export function groundLayer(s: StructureFile): number {
  const g = (s.meta as { groundLayer?: number }).groundLayer;
  return Number.isInteger(g) ? (g as number) : 0;
}

export function buildWorld(s: StructureFile, opts: SceneOptions): World {
  const world = new World();
  const grid = toGrid(s);
  const p = world.place(`${s.name}_v${s.version}`, grid, [0, -groundLayer(s), 0]);
  p.filter = { ...opts.layer };
  world.setTerrain(opts.terrain, s.size[0] / 2, s.size[2] / 2, opts.terrainSize, opts.seed, opts.trees);
  return world;
}

export async function showStructure(viewer: Viewer, lib: BlockLibrary, s: StructureFile, opts: SceneOptions): Promise<World> {
  const world = buildWorld(s, opts);
  await lib.prepare([...world.states, 'minecraft:water[__full=true]']);
  viewer.setWorld(world, lib);
  viewer.setMarkers(opts.markers);
  return world;
}

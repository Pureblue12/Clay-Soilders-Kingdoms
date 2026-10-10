// Phase 3 (NOT implemented yet): kingdom generation preview.
// A generator takes a seed, the king's position and a tier, and returns a timeline of steps; the viewer will show
// a test World (src/render/world.ts, which already holds many placed structures) at any step with a slider.
// This file only fixes the shape so the viewer can plug generators in later without changing.
import type { StructureFile } from '../format/structure.ts';
import type { TerrainKind } from '../render/world.ts';

export interface GeneratorInput {
  seed: number;
  king: [number, number, number];
  tier: number;
  terrain: TerrainKind;
  /** Structures the generator may place: the approved version of each, by name. */
  library: Map<string, StructureFile>;
}

/** One thing that happens in the kingdom's growth. */
export type GeneratorStep =
  | { kind: 'place'; structure: string; version: number; origin: [number, number, number]; facing: 'north' | 'east' | 'south' | 'west'; label?: string }
  | { kind: 'remove'; placementId: number; label?: string }
  | { kind: 'note'; text: string };

export interface KingdomGenerator {
  readonly id: string;
  readonly name: string;
  generate(input: GeneratorInput): GeneratorStep[];
}

/** Registered generators (none yet). */
export const generators: KingdomGenerator[] = [];

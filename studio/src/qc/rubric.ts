// The visual half of the quality check: what the reviewer scores when it looks at the renders, and how a review
// becomes a verdict. The long version (what to look for in each picture, Carlton's taste) is qc/RUBRIC.md.
// Pure: shared by the CLI, the tests and the page.
import type { AutoResult } from './checks.ts';

export interface Criterion {
  id: string;
  label: string;
  weight: number;
  /** A 0 here sends the structure back whatever the total. */
  must?: boolean;
  look: string;
}

/** Each criterion is scored 0 (no), 1 (partly) or 2 (yes). Weights follow Carlton's review notes. */
export const CRITERIA: Criterion[] = [
  { id: 'detail', label: 'Rich detail', weight: 3, must: true,
    look: 'Small blocks everywhere they belong: sills, shutters, flower boxes, lanterns, props, furniture. Never bare.' },
  { id: 'artefacts', label: 'No mistakes', weight: 2, must: true,
    look: 'No holes, stray or floating blocks, half-hidden furniture on the roof, blocks facing the wrong way.' },
  { id: 'purpose', label: 'Reads as what it is', weight: 2, must: true,
    look: 'At a glance you can tell it is a smithy, a mill, a farmhouse. Its workstation and props show it.' },
  { id: 'depth', label: 'Depth on every face', weight: 2,
    look: 'Posts, beams, a plinth, sills or overhangs break every wall; shadows show in the pictures.' },
  { id: 'roof', label: 'Roof', weight: 2,
    look: 'Steep (45 degrees+), one block of overhang, a finished ridge, gable trim; not one flat colour.' },
  { id: 'materials', label: 'Materials', weight: 2,
    look: 'Style A palette: stone base, timber frame, plaster or plank infill, dark roof; 3 to 5 blocks, nothing clashing.' },
  { id: 'shape', label: 'Shape', weight: 2,
    look: 'Not a box: a jetty, a lean-to, a porch, a chimney or roof parts at two heights. Some asymmetry.' },
  { id: 'life', label: 'Life', weight: 1,
    look: 'Plants, flowers, vines or leaves at the base and in boxes; lanterns; a path or a fence.' },
  { id: 'scale', label: 'Scale', weight: 1,
    look: 'Right size for soldiers (cottage about 5 x 7, walls 3 high); landmarks clearly bigger than houses.' },
  { id: 'grandeur', label: 'Ambition', weight: 1,
    look: 'Big and special enough for its role: a windmill looks like a windmill, a keep looks grand.' },
];

export const PASS_SCORE = 70;
export const MAX_ROUNDS = 3;

export type Verdict = 'pass' | 'revise' | 'handed_over';

export interface VisualReview {
  scores: Record<string, number>; // criterion id -> 0, 1 or 2
  notes?: Record<string, string>; // criterion id -> what the reviewer saw
  summary: string;
  /** What to fix, most important first. Becomes the brief for the next version. */
  problems: string[];
  views?: string[]; // which renders the reviewer looked at
  /** Positions of decoration-step blocks in a file saved before meta.decorationBlocks existed (the reviewer checked
   * them against the generator's decorate() calls). Not needed for newer files. */
  decorationBlocks?: [number, number, number][];
}

export interface QcReport {
  version: number;
  checkedAt: string;
  by: string;
  verdict: Verdict;
  /** 1 for the first check of a structure; +1 for each version made to answer the last check. */
  round: number;
  score: number;
  auto: AutoResult;
  visual: VisualReview;
}

export function checkVisual(v: VisualReview): string[] {
  const errors: string[] = [];
  for (const c of CRITERIA) {
    const s = v.scores?.[c.id];
    if (s !== 0 && s !== 1 && s !== 2) errors.push(`scores.${c.id} must be 0, 1 or 2`);
  }
  for (const k of Object.keys(v.scores ?? {})) if (!CRITERIA.some((c) => c.id === k)) errors.push(`unknown criterion "${k}"`);
  if (typeof v.summary !== 'string' || !v.summary.trim()) errors.push('summary is required');
  if (!Array.isArray(v.problems)) errors.push('problems must be a list (empty when nothing is wrong)');
  return errors;
}

export function scoreOf(v: VisualReview): number {
  let got = 0;
  let max = 0;
  for (const c of CRITERIA) {
    got += c.weight * (v.scores[c.id] ?? 0);
    max += c.weight * 2;
  }
  return Math.round((got / max) * 100);
}

/** Pass: score 70+, no "must" criterion at 0 and no failed automatic check. Otherwise revise, until MAX_ROUNDS. */
export function verdictOf(auto: AutoResult, visual: VisualReview, round: number): { verdict: Verdict; score: number; why: string[] } {
  const score = scoreOf(visual);
  const why: string[] = [];
  if (score < PASS_SCORE) why.push(`score ${score} is under ${PASS_SCORE}`);
  for (const c of CRITERIA) if (c.must && visual.scores[c.id] === 0) why.push(`"${c.label}" scored 0`);
  for (const i of auto.issues) if (i.level === 'fail') why.push(`automatic check ${i.check} failed`);
  if (!why.length) return { verdict: 'pass', score, why };
  return { verdict: round >= MAX_ROUNDS ? 'handed_over' : 'revise', score, why };
}

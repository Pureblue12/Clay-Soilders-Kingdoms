// npm test: the quality check's automatic checks, scoring and report files.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { test } from 'node:test';
import type { StructureFile } from '../src/format/structure.ts';
import { autoCheck } from '../src/qc/checks.ts';
import { CRITERIA, checkVisual, scoreOf, verdictOf, type VisualReview } from '../src/qc/rubric.ts';
import { loadIndex, loadQc, roundFor, saveReport } from '../src/qc/store.ts';
import { Store } from '../src/server/store.ts';

const make = (name: string, palette: Record<string, string>, layers: string[][]): StructureFile => ({
  format: 'csr-studio-structure/1', name, version: 1,
  size: [layers[0][0].length, layers.length, layers[0].length],
  meta: { status: 'pending', notes: '', author: 'test', created: '2026-10-10' },
  palette: { '.': 'minecraft:air', ...palette }, layers,
});

const checks = (s: StructureFile) => autoCheck(s).issues.filter((i) => i.level !== 'info').map((i) => `${i.level}:${i.check}`);

const allScores = (n: number) => Object.fromEntries(CRITERIA.map((c) => [c.id, n]));
const review = (n: number): VisualReview => ({ scores: allScores(n), summary: 'ok', problems: [] });

test('floating blocks fail; a diagonal stair counts as held', () => {
  const floating = make('f', { '#': 'stone', l: 'lantern' }, [['#..'], ['...'], ['.l.']]);
  assert.ok(checks(floating).includes('fail:floating'));
  const diag = make('d', { '#': 'stone', s: 'oak_stairs[facing=east,half=bottom]' }, [['#..'], ['.s.'], ['..s']]);
  assert.ok(!checks(diag).includes('fail:floating'));
});

test('a door or bed missing its other half fails', () => {
  const door = make('door', { '#': 'stone', d: 'oak_door[half=lower,facing=north]' }, [['#d#'], ['...']]);
  assert.ok(checks(door).includes('fail:two-part'));
  const ok = make('door2', { '#': 'stone', d: 'oak_door[half=lower,facing=north]', u: 'oak_door[half=upper,facing=north]' }, [['#d#'], ['.u.']]);
  assert.ok(!checks(ok).includes('fail:two-part'));
});

test('a 2-block prop is too bare; fences without connections are flagged', () => {
  assert.ok(checks(make('signpost_l1', { f: 'oak_fence' }, [['f'], ['f']])).includes('fail:too-small'));
  assert.ok(checks(make('x', { '#': 'stone', f: 'oak_fence' }, [['#ff#']])).includes('warn:connections'));
  assert.ok(!checks(make('y', { '#': 'stone', f: 'oak_fence[east=true,west=true]' }, [['#ff#']])).includes('warn:connections'));
});

test('a plain stone box: flat walls, no detail, no variety', () => {
  const wall = '#######';
  const room = '#.....#';
  const layer = [wall, room, room, room, room, room, wall];
  const box = make('box', { '#': 'stone' }, [layer, layer, layer, layer, layer]);
  const c = checks(box);
  assert.ok(c.includes('warn:flat-wall'));
  assert.ok(c.includes('fail:detail'));
  assert.ok(c.includes('warn:variety'));
});

test('scoring: all 2 is 100, all 1 is 50; a must at 0 sends it back; round 3 hands over', () => {
  assert.equal(scoreOf(review(2)), 100);
  assert.equal(scoreOf(review(1)), 50);
  const clean = { issues: [], stats: autoCheck(make('p', { '#': 'stone' }, [['#']])).stats };
  assert.equal(verdictOf(clean, review(2), 1).verdict, 'pass');
  const noDetail = review(2);
  noDetail.scores.detail = 0;
  assert.ok(scoreOf(noDetail) >= 70);
  assert.equal(verdictOf(clean, noDetail, 1).verdict, 'revise');
  assert.equal(verdictOf(clean, review(1), 3).verdict, 'handed_over');
  assert.deepEqual(checkVisual(review(2)), []);
  assert.ok(checkVisual({ scores: { detail: 3 }, summary: '', problems: [] } as VisualReview).length > 0);
});

test('reports: saved next to the versions, rounds follow the parent, the index keeps the newest', () => {
  const store = new Store(fs.mkdtempSync(path.join(os.tmpdir(), 'studio-qc-')));
  const s = make('hut', { '#': 'stone' }, [['#']]);
  store.saveNewVersion(s);
  const auto = autoCheck(s);
  saveReport(store, 'hut', { version: 1, checkedAt: 'now', by: 't', verdict: 'revise', round: 1, score: 40, auto, visual: review(1) });
  store.saveNewVersion(s);
  assert.equal(roundFor(store, 'hut', 2), 2);
  saveReport(store, 'hut', { version: 2, checkedAt: 'now', by: 't', verdict: 'pass', round: 2, score: 90, auto, visual: review(2) });
  assert.deepEqual(Object.keys(loadQc(store, 'hut').reports), ['1', '2']);
  assert.equal(loadIndex(store).hut.verdict, 'pass');
  assert.deepEqual(store.names(), ['hut']); // qc-index.json is not a structure
  assert.deepEqual(store.versions('hut'), [1, 2]); // qc.json is not a version
  store.saveNewVersion(s);
  assert.equal(roundFor(store, 'hut', 3), 1); // the parent passed: a fresh start
});

test('decoration-step blocks (meta.decorationBlocks or listed by the reviewer) are not a buildability fail', () => {
  const s = make('pond', { '#': 'stone', w: 'water[level=0]' }, [['#w#']]);
  assert.ok(checks(s).includes('fail:buildability'));
  assert.ok(!checks({ ...s, meta: { ...s.meta, decorationBlocks: [[1, 0, 0, 'water']] } }).includes('fail:buildability'));
  assert.ok(!autoCheck(s, [[1, 0, 0]]).issues.some((i) => i.level === 'fail' && i.check === 'buildability'));
});

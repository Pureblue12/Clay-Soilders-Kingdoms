// npm test: format, validator, NBT round trip and the version store.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { test } from 'node:test';
import { nbtToStructure, readNbt, structureToNbt } from '../src/format/nbt.ts';
import { parseBlockState, toGrid, validateStructure, type StructureFile } from '../src/format/structure.ts';
import { Store } from '../src/server/store.ts';
import { resolveParts } from '../src/render/blockmodels.ts';

const sample = (): StructureFile => ({
  format: 'csr-studio-structure/1',
  name: 'tiny',
  version: 1,
  size: [3, 2, 2],
  meta: { status: 'pending', notes: '', author: 'test', created: '2026-10-10' },
  palette: { '.': 'minecraft:air', '#': 'minecraft:cobblestone', s: 'oak_stairs[facing=north,half=bottom]', _: 'minecraft:structure_void' },
  layers: [['###', '#_#'], ['s..', '...']],
});

test('block states parse with and without namespace and properties', () => {
  assert.deepEqual(parseBlockState('stone'), { name: 'minecraft:stone', properties: {} });
  assert.deepEqual(parseBlockState('minecraft:oak_stairs[facing=east,half=top]').properties, { facing: 'east', half: 'top' });
  assert.throws(() => parseBlockState('Bad Name'));
});

test('a good file validates; size and palette mistakes are errors', () => {
  assert.deepEqual(validateStructure(sample()).filter((i) => i.level === 'error'), []);
  const bad = sample();
  bad.layers[0][1] = '#x';
  const msgs = validateStructure(bad).map((i) => i.message).join('\n');
  assert.match(msgs, /expected 3 characters/);
  assert.match(msgs, /"x" is not in the palette/);
});

test('NBT export and import keep every block, and void stays out of the file', () => {
  const s = sample();
  const bytes = structureToNbt(s);
  const root = readNbt(bytes);
  assert.equal(root.type, 'compound');
  const blocks = (root as { value: Record<string, { value: unknown[] }> }).value.blocks.value;
  assert.equal(blocks.length, 11); // 12 cells minus one structure void
  const back = nbtToStructure(bytes, 'tiny', s.meta).structure;
  const a = toGrid(s), b = toGrid(back);
  for (let i = 0; i < a.cells.length; i++) assert.equal(b.stateStrings[b.cells[i]], a.stateStrings[a.cells[i]]);
});

test('the store never overwrites a version, and new versions start pending', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'studio-'));
  const store = new Store(dir);
  const s = sample();
  s.meta.status = 'approved';
  assert.equal(store.saveNewVersion(s), 1);
  assert.equal(store.load('tiny', 1).meta.status, 'pending');
  assert.equal(store.saveNewVersion(s), 2);
  store.setReview('tiny', 1, 'approved', 'good');
  assert.equal(store.rollback('tiny', 1, 'test'), 3);
  assert.equal(store.load('tiny', 3).meta.status, 'pending');
  assert.equal(store.summary('tiny').approvedVersion, 1);
  assert.deepEqual(store.versions('tiny'), [1, 2, 3]);
  fs.rmSync(dir, { recursive: true });
});

test('blockstate variants and multipart pick the right models', () => {
  const variants = { variants: { 'facing=east,half=bottom': { model: 'a' }, 'facing=north,half=bottom': { model: 'b', y: 270 } } };
  assert.deepEqual(resolveParts(variants, parseBlockState('x[facing=north,half=bottom]')), [{ model: 'minecraft:b', x: 0, y: 270, uvlock: false }]);
  // missing properties fall back to a fitting variant
  assert.equal(resolveParts(variants, parseBlockState('x[facing=north]'))![0].model, 'minecraft:b');
  const multipart = { multipart: [{ apply: { model: 'post' } }, { when: { north: 'true' }, apply: { model: 'side', uvlock: true } }] };
  assert.equal(resolveParts(multipart, parseBlockState('x[north=true]'))!.length, 2);
  assert.equal(resolveParts(multipart, parseBlockState('x[north=false]'))!.length, 1);
});

test('the shipped samples are valid', () => {
  const store = new Store();
  for (const name of store.names())
    for (const v of store.versions(name))
      assert.deepEqual(validateStructure(store.load(name, v)).filter((i) => i.level === 'error'), [], `${name} v${v}`);
});

// The studio command line, for people and agents:  npm run studio -- <command> [options]
// Run "npm run studio -- help" for the list.
import fs from 'node:fs';
import path from 'node:path';
import {
  blockCounts,
  FORMAT_ID,
  parseBlockState,
  stringifyStructure,
  toGrid,
  validateStructure,
  type StructureFile,
  type ValidationIssue,
} from '../src/format/structure.ts';
import { nbtToStructure, structureToNbt } from '../src/format/nbt.ts';
import { buildability } from '../src/format/modrules.ts';
import { assetFile, assetSource } from '../src/server/assets.ts';
import { STUDIO_ROOT, Store } from '../src/server/store.ts';

const HELP = `Minecraft Structure Studio CLI

  npm run studio -- <command> [options]

Library
  list                                  every structure: latest version, status, approved version
  show <name> [-v N]                    size, blocks, palette and notes of a version (default: latest)
  template <name> <x> <y> <z> [-o f]    print (or write) an empty structure file to start from

Checking
  validate <name|file.json> [-v N]      format errors, unknown blocks/properties (with assets), mod buildability
  diff <name> <a> <b>                   what changed between two versions, layer by layer

Saving (versions are never overwritten; every save is "pending" until Carlton reviews it)
  create <file.json> [--author A]       first version of a new structure
  save <file.json> [--author A] [--changes "what changed and why"]
                                        next version of an existing structure (name taken from the file)

Rendering (headless Chromium, PNGs in renders/<name>/v<N>/)
  render <name> [-v N|all] [--views iso,front,right,back,left,top,iso_back]
         [--terrain flat|hills|slope|none] [--trees] [--size 96] [--width 1280 --height 800]
         [--layer upto|single --y N] [--markers] [-o dir]
  render --file f.json [...]            render a file that isn't saved yet

Minecraft structure files
  export-nbt <name> [-v N] [-o out.nbt] vanilla structure NBT (gzip), what the mod loads
  import-nbt <file.nbt> --name <name> [--author A]
                                        new structure, or the next version if <name> exists
  assets                                where the Minecraft textures come from

Reviews (approve / reject) are made only in the studio UI (npm run dev).
`;

interface Args { _: string[]; [k: string]: string | boolean | string[] }

function parseArgs(argv: string[]): Args {
  const out: Args = { _: [] };
  const alias: Record<string, string> = { v: 'version', o: 'out' };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const m = /^--?([a-zA-Z][\w-]*)(?:=(.*))?$/.exec(a);
    if (m) {
      const key = alias[m[1]] ?? m[1];
      if (m[2] !== undefined) out[key] = m[2];
      else if (i + 1 < argv.length && !argv[i + 1].startsWith('-')) out[key] = argv[++i];
      else out[key] = true;
    } else out._.push(a);
  }
  return out;
}

const str = (a: Args, k: string): string | undefined => (typeof a[k] === 'string' ? (a[k] as string) : undefined);
const num = (a: Args, k: string): number | undefined => (str(a, k) !== undefined ? Number(str(a, k)) : undefined);

function fail(msg: string): never {
  console.error('error: ' + msg);
  process.exit(1);
}

function readStructureFile(file: string): StructureFile {
  if (!fs.existsSync(file)) fail(`no file ${file}`);
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8')) as StructureFile;
  } catch (e) {
    fail(`${file} is not valid JSON: ${(e as Error).message}`);
  }
}

/** A name or a path: paths end in .json. */
function loadTarget(store: Store, target: string, version?: number): StructureFile {
  if (target.endsWith('.json')) return readStructureFile(target);
  try {
    return store.load(target, version);
  } catch (e) {
    fail((e as Error).message);
  }
}

// ---------------------------------------------------------------- validation against real assets

interface BlockstateJson { variants?: Record<string, unknown>; multipart?: { when?: Record<string, unknown> }[] }

function knownProperties(bs: BlockstateJson): Map<string, Set<string>> {
  const props = new Map<string, Set<string>>();
  const add = (k: string, v: string) => {
    if (!props.has(k)) props.set(k, new Set());
    for (const x of v.replace(/^!/, '').split('|')) props.get(k)!.add(x);
  };
  for (const key of Object.keys(bs.variants ?? {}))
    for (const kv of key.split(',')) {
      const [k, v] = kv.split('=');
      if (k && v !== undefined) add(k, v);
    }
  const walk = (w: Record<string, unknown>) => {
    for (const [k, v] of Object.entries(w)) {
      if ((k === 'OR' || k === 'AND') && Array.isArray(v)) v.forEach((x) => walk(x as Record<string, unknown>));
      else add(k, String(v));
    }
  };
  for (const part of bs.multipart ?? []) if (part.when) walk(part.when);
  // multipart files only name the values that add a part: the "nothing here" values are valid too
  for (const values of props.values()) {
    if (values.has('true')) values.add('false');
    if (values.has('low') || values.has('tall')) values.add('none');
  }
  return props;
}

/** Extra properties vanilla accepts that blockstate files don't list (they don't change the model). */
const IGNORED_PROPS = new Set(['waterlogged', 'powered', 'distance', 'persistent', 'occupied', 'snowy', 'level', 'lit', 'signal_fire', 'hanging', 'type', 'rotation', 'part', 'facing', 'attachment', 'triggered', 'enabled', 'locked', 'delay', 'note', 'instrument', 'candles', 'pickles', 'eggs', 'hatch', 'moisture', 'power', 'bites', 'charges', 'honey_level', 'stage', 'age']);

function assetIssues(s: StructureFile): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  const src = assetSource();
  if (!src.root) {
    issues.push({ level: 'warning', message: 'no Minecraft assets installed: block names and properties were not checked' });
    return issues;
  }
  for (const [key, value] of Object.entries(s.palette)) {
    let st;
    try { st = parseBlockState(value); } catch { continue; }
    if (!st.name.startsWith('minecraft:')) {
      issues.push({ level: 'warning', message: `palette "${key}": ${st.name} is not a vanilla block (drawn as a placeholder)` });
      continue;
    }
    const file = assetFile(`blockstates/${st.name.slice(10)}.json`);
    if (!file) {
      issues.push({ level: 'error', message: `palette "${key}": unknown block ${st.name}` });
      continue;
    }
    const props = knownProperties(JSON.parse(fs.readFileSync(file, 'utf8')) as BlockstateJson);
    for (const [k, v] of Object.entries(st.properties)) {
      const allowed = props.get(k);
      if (!allowed) {
        if (!IGNORED_PROPS.has(k)) issues.push({ level: 'warning', message: `palette "${key}": ${st.name} has no property "${k}" in its model file` });
      } else if (!allowed.has(v)) {
        issues.push({ level: 'error', message: `palette "${key}": ${st.name} ${k}=${v} is not valid (one of ${[...allowed].join(', ')})` });
      }
    }
    // model-relevant properties left out: the first matching variant is used, which is often not what was meant
    for (const k of props.keys())
      if (!(k in st.properties) && ['facing', 'half', 'shape', 'type', 'axis', 'hinge', 'open', 'part'].includes(k))
        issues.push({ level: 'warning', message: `palette "${key}": ${st.name} has no "${k}" (Minecraft uses a default)` });
  }
  return issues;
}

function fullValidation(s: StructureFile): ValidationIssue[] {
  const issues = validateStructure(s);
  if (issues.some((i) => i.level === 'error')) return issues;
  issues.push(...assetIssues(s));
  const b = buildability(blockCounts(s).keys());
  if (b.broken.length) issues.push({ level: 'warning', message: `mod: builders can't place ${b.broken.join(', ')} (decoration step only, docs/STRUCTURES.md)` });
  if (b.noPassage.length) issues.push({ level: 'warning', message: `mod: soldiers can't open ${b.noPassage.join(', ')}: never in a walkway` });
  if (b.unknown.length) issues.push({ level: 'warning', message: `mod: not yet tested by a builder GameTest: ${b.unknown.join(', ')}` });
  return issues;
}

function printIssues(issues: ValidationIssue[]): boolean {
  const errors = issues.filter((i) => i.level === 'error');
  for (const i of issues) console.log(`${i.level === 'error' ? 'ERROR  ' : 'warning'} ${i.message}`);
  console.log(errors.length ? `${errors.length} error(s)` : 'valid' + (issues.length ? ` (${issues.length} warning(s))` : ''));
  return errors.length === 0;
}

// ---------------------------------------------------------------- commands

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const [cmd, ...rest] = args._;
  const store = new Store();

  switch (cmd) {
    case undefined:
    case 'help':
      console.log(HELP);
      return;

    case 'list': {
      const all = store.list();
      if (!all.length) return console.log('no structures yet');
      const rows = all.map((s) => {
        const l = s.versions[s.versions.length - 1];
        return [s.name, `v${s.latest}`, s.status, s.approvedVersion ? `v${s.approvedVersion}` : '-', l.size.join('x'), String(l.blocks)];
      });
      const head = ['name', 'latest', 'status', 'approved', 'size', 'blocks'];
      const w = head.map((h, i) => Math.max(h.length, ...rows.map((r) => r[i].length)));
      for (const r of [head, ...rows]) console.log(r.map((c, i) => c.padEnd(w[i])).join('  '));
      return;
    }

    case 'show': {
      const s = loadTarget(store, rest[0] ?? fail('show <name>'), num(args, 'version'));
      const counts = [...blockCounts(s).entries()].sort((a, b) => b[1] - a[1]);
      const total = counts.reduce((n, [, c]) => n + c, 0);
      console.log(`${s.name} v${s.version}  ${s.meta.status}  size ${s.size.join('x')} (x, y, z)  ${total} blocks`);
      console.log(`author ${s.meta.author}, created ${s.meta.created}${s.meta.parent ? `, from v${s.meta.parent}` : ''}`);
      if (s.meta.description) console.log(`description: ${s.meta.description}`);
      if (s.meta.changes) console.log(`changes: ${s.meta.changes}`);
      if (s.meta.notes) console.log(`review notes: ${s.meta.notes}`);
      console.log('palette:');
      for (const [k, v] of Object.entries(s.palette)) console.log(`  ${k}  ${v}`);
      console.log('blocks:');
      for (const [n, c] of counts) console.log(`  ${String(c).padStart(5)}  ${n}`);
      if (store.versions(s.name).length) console.log(`versions: ${store.versions(s.name).map((v) => `v${v}`).join(', ')}`);
      return;
    }

    case 'template': {
      const [name, x, y, z] = rest;
      if (!name || !z) fail('template <name> <x> <y> <z>');
      const size: [number, number, number] = [Number(x), Number(y), Number(z)];
      const s: StructureFile = {
        format: FORMAT_ID,
        name,
        version: 1,
        size,
        meta: { status: 'pending', notes: '', author: 'claude', created: new Date().toISOString().slice(0, 10), description: '' },
        palette: { '.': 'minecraft:air', '#': 'minecraft:cobblestone' },
        layers: Array.from({ length: size[1] }, (_, yy) => Array.from({ length: size[2] }, () => (yy === 0 ? '#' : '.').repeat(size[0]))),
      };
      const text = stringifyStructure(s);
      if (str(args, 'out')) fs.writeFileSync(str(args, 'out')!, text);
      else process.stdout.write(text);
      return;
    }

    case 'validate': {
      const s = loadTarget(store, rest[0] ?? fail('validate <name|file.json>'), num(args, 'version'));
      if (!printIssues(fullValidation(s))) process.exit(1);
      return;
    }

    case 'diff': {
      const [name, a, b] = rest;
      if (!name || !a || !b) fail('diff <name> <a> <b>');
      const A = store.load(name, Number(a.replace('v', ''))), B = store.load(name, Number(b.replace('v', '')));
      const ga = toGrid(A), gb = toGrid(B);
      if (A.size.join() !== B.size.join()) console.log(`size ${A.size.join('x')} -> ${B.size.join('x')}`);
      const [sx, sy, sz] = [Math.max(A.size[0], B.size[0]), Math.max(A.size[1], B.size[1]), Math.max(A.size[2], B.size[2])];
      const at = (g: typeof ga, x: number, y: number, z: number) =>
        x < g.size[0] && y < g.size[1] && z < g.size[2] ? g.stateStrings[g.cells[(y * g.size[2] + z) * g.size[0] + x]] : 'minecraft:air';
      let total = 0;
      for (let y = 0; y < sy; y++) {
        const changes: string[] = [];
        for (let z = 0; z < sz; z++)
          for (let x = 0; x < sx; x++) {
            const pa = at(ga, x, y, z), pb = at(gb, x, y, z);
            if (pa !== pb) changes.push(`  (${x},${y},${z}) ${pa.replace('minecraft:', '')} -> ${pb.replace('minecraft:', '')}`);
          }
        if (changes.length) {
          console.log(`layer y=${y}: ${changes.length} change(s)`);
          changes.slice(0, 40).forEach((c) => console.log(c));
          if (changes.length > 40) console.log(`  ... and ${changes.length - 40} more`);
        }
        total += changes.length;
      }
      console.log(`${total} block(s) differ`);
      return;
    }

    case 'create':
    case 'save': {
      const file = rest[0] ?? fail(`${cmd} <file.json>`);
      const s = readStructureFile(file);
      const issues = fullValidation(s);
      if (!printIssues(issues)) process.exit(1);
      const exists = store.versions(s.name).length > 0;
      if (cmd === 'create' && exists) fail(`"${s.name}" exists already: use save to add a version`);
      if (cmd === 'save' && !exists) fail(`no structure "${s.name}" yet: use create`);
      s.meta.notes = '';
      if (str(args, 'changes')) s.meta.changes = str(args, 'changes')!;
      const v = store.saveNewVersion(s, { author: str(args, 'author') ?? s.meta.author });
      console.log(`saved ${s.name} v${v} (pending): ${path.relative(process.cwd(), path.join(store.dir, s.name, `${s.name}_v${v}.json`))}`);
      return;
    }

    case 'render': {
      const { renderStructures } = await import('./render.ts');
      const jobs: { structure: StructureFile; outDir: string; prefix: string }[] = [];
      const file = str(args, 'file');
      if (file) {
        const s = readStructureFile(file);
        const errors = validateStructure(s).filter((i) => i.level === 'error');
        if (errors.length) fail('invalid file:\n' + errors.map((e) => '  ' + e.message).join('\n'));
        jobs.push({ structure: s, outDir: str(args, 'out') ?? path.join(STUDIO_ROOT, 'renders', '_files', s.name), prefix: `${s.name}_draft` });
      } else {
        const name = rest[0] ?? fail('render <name> (or --file f.json)');
        const vs = store.versions(name);
        if (!vs.length) fail(`no structure "${name}"`);
        const wanted = str(args, 'version') === 'all' ? vs : [num(args, 'version') ?? vs[vs.length - 1]];
        for (const v of wanted)
          jobs.push({ structure: store.load(name, v), outDir: str(args, 'out') ?? path.join(STUDIO_ROOT, 'renders', name, `v${v}`), prefix: `${name}_v${v}` });
      }
      const files = await renderStructures(jobs, {
        views: (str(args, 'views') ?? 'iso,front,right,back,top').split(','),
        width: num(args, 'width') ?? 1280,
        height: num(args, 'height') ?? 800,
        terrain: str(args, 'terrain') ?? 'flat',
        size: num(args, 'size'),
        trees: args.trees === true,
        layer: str(args, 'layer'),
        y: num(args, 'y'),
        markers: args.markers === true,
      });
      for (const f of files) console.log(path.relative(process.cwd(), f));
      return;
    }

    case 'export-nbt': {
      const s = loadTarget(store, rest[0] ?? fail('export-nbt <name>'), num(args, 'version'));
      const out = str(args, 'out') ?? `${s.name}_v${s.version}.nbt`;
      fs.writeFileSync(out, structureToNbt(s));
      console.log(`wrote ${out}`);
      return;
    }

    case 'import-nbt': {
      const file = rest[0] ?? fail('import-nbt <file.nbt> --name <name>');
      const name = str(args, 'name') ?? path.basename(file, '.nbt').replace(/[^a-z0-9_]/g, '_');
      const author = str(args, 'author') ?? 'import';
      const r = nbtToStructure(new Uint8Array(fs.readFileSync(file)), name, {
        status: 'pending',
        notes: '',
        author,
        created: new Date().toISOString(),
        description: `Imported from ${path.basename(file)}`,
      });
      const v = store.saveNewVersion(r.structure, { author });
      console.log(`imported ${file} as ${name} v${v}`);
      if (r.droppedBlockEntities) console.log(`note: block-entity data of ${r.droppedBlockEntities} block(s) (chest contents, sign text) was not kept`);
      if (r.entities) console.log(`note: ${r.entities} entit(ies) in the file were not kept`);
      return;
    }

    case 'assets': {
      const s = assetSource(true);
      console.log(`${s.kind}: ${s.detail}`);
      if (s.kind === 'none') console.log('Put your Minecraft client jar (e.g. 1.21.1.jar) in studio/minecraft/. See studio/minecraft/README.md.');
      return;
    }

    default:
      fail(`unknown command "${cmd}" (try: npm run studio -- help)`);
  }
}

main().catch((e) => fail((e as Error).stack ?? String(e)));

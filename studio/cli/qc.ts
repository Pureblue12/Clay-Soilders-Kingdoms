// Quality check CLI: npm run qc -- <command>. The reviewer's tools; see qc/README.md for the loop.
import fs from 'node:fs';
import path from 'node:path';
import { validateStructure, type StructureFile } from '../src/format/structure.ts';
import { autoCheck, type AutoResult } from '../src/qc/checks.ts';
import { CRITERIA, MAX_ROUNDS, PASS_SCORE, checkVisual, verdictOf, type QcReport, type VisualReview } from '../src/qc/rubric.ts';
import { carltonNotes, loadQc, qcFile, roundFor, saveReport } from '../src/qc/store.ts';
import { STUDIO_ROOT, Store } from '../src/server/store.ts';

const HELP = `Structure quality check (qc/README.md)

  todo                         structures whose latest version is pending and has no quality report yet
  check <name|file.json> [-v N] [--json]
                               the automatic checks (floating blocks, two-part blocks, detail, flat walls, roof, ...)
  render <name...> [-v N]      the reviewer's pictures: iso, iso_back, front, right, back, top (with scale markers),
                               on a slope, and the floor plan. Written to renders/qc/<name>/v<N>/
  brief <name> [-v N]          everything the reviewer needs: automatic checks, the rubric, Carlton's past notes,
                               the round, the render files
  record <name> -v N --visual review.json [--by NAME]
                               store the review (qc.json next to the versions) and print the verdict
  status                       the latest report of every structure

Pass: score ${PASS_SCORE}+ out of 100, no "must" criterion at 0, no failed automatic check.
After ${MAX_ROUNDS} rounds a structure goes to Carlton anyway, with the problems listed ("handed over").`;

type Args = { _: string[]; [k: string]: string | boolean | string[] };

function parseArgs(argv: string[]): Args {
  const out: Args = { _: [] };
  const alias: Record<string, string> = { v: 'version' };
  for (let i = 0; i < argv.length; i++) {
    const m = /^--?([a-zA-Z][\w-]*)(?:=(.*))?$/.exec(argv[i]);
    if (m) {
      const key = alias[m[1]] ?? m[1];
      if (m[2] !== undefined) out[key] = m[2];
      else if (i + 1 < argv.length && !argv[i + 1].startsWith('-')) out[key] = argv[++i];
      else out[key] = true;
    } else out._.push(argv[i]);
  }
  return out;
}

const str = (a: Args, k: string) => (typeof a[k] === 'string' ? (a[k] as string) : undefined);
const num = (a: Args, k: string) => (str(a, k) !== undefined ? Number(str(a, k)) : undefined);

function fail(msg: string): never {
  console.error('error: ' + msg);
  process.exit(1);
}

function load(store: Store, target: string, version?: number): StructureFile {
  try {
    if (target.endsWith('.json')) return JSON.parse(fs.readFileSync(target, 'utf8')) as StructureFile;
    return store.load(target, version);
  } catch (e) {
    fail((e as Error).message);
  }
}

const ICON = { fail: '✖', warn: '!', info: '·' } as const;

function printAuto(r: AutoResult) {
  const st = r.stats;
  console.log(
    `${st.blocks} blocks (${st.category}, budget ${st.budget}) · ${st.types} block types · ` +
      `${Math.round(st.detailRatio * 100)} % detail blocks · ${st.height} tall${st.isBuilding ? ' · building' : ''}`,
  );
  if (!r.issues.length) console.log('  no problems found');
  for (const i of r.issues) console.log(`  ${ICON[i.level]} ${i.level.padEnd(4)} ${i.check}: ${i.message}`);
}

function renderDir(name: string, v: number) {
  return path.join(STUDIO_ROOT, 'renders', 'qc', name, `v${v}`);
}

/** All views on one 4 x 2 picture with labels: the reviewer reads one image instead of eight (a quarter of the cost). */
async function contactSheet(files: string[], out: string, title: string) {
  const { launchBrowser } = await import('./render.ts');
  const browser = await launchBrowser();
  try {
    const page = await browser.newPage({ viewport: { width: 1600, height: 600 } });
    const cells = files
      .map((f) => {
        const label = path.basename(f, '.png').replace(/^.*_v\d+_/, '').replace('_', ' ');
        return `<figure><img src="data:image/png;base64,${fs.readFileSync(f).toString('base64')}"><figcaption>${label}</figcaption></figure>`;
      })
      .join('');
    await page.setContent(`<style>
      body { margin: 0; background: #16181d; font: 13px system-ui, sans-serif; color: #e8eaf0; }
      h1 { font-size: 14px; margin: 6px 8px; }
      main { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; padding: 0 4px 4px; }
      figure { margin: 0; position: relative; } img { width: 100%; display: block; }
      figcaption { position: absolute; left: 4px; top: 4px; background: #000a; padding: 1px 6px; border-radius: 4px; }
    </style><h1>${title}</h1><main>${cells}</main>`);
    await page.screenshot({ path: out, fullPage: true });
  } finally {
    await browser.close();
  }
}

/** Latest version pending and not reported on yet. */
function todo(store: Store) {
  return store.list().filter((s) => s.status === 'pending' && !loadQc(store, s.name).reports[String(s.latest)]);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const [cmd, ...rest] = args._;
  const store = new Store();

  switch (cmd) {
    case 'todo': {
      const list = todo(store);
      if (!list.length) return console.log('nothing to check: every pending version has a quality report');
      for (const s of list) {
        const round = roundFor(store, s.name, s.latest);
        console.log(`${s.name} v${s.latest}  round ${round} of ${MAX_ROUNDS}  by ${s.versions[s.versions.length - 1].author}`);
      }
      console.log(`\n${list.length} to check`);
      return;
    }

    case 'check': {
      const s = load(store, rest[0] ?? fail('check <name|file.json>'), num(args, 'version'));
      const errors = validateStructure(s).filter((i) => i.level === 'error');
      if (errors.length && !s.layers) fail('not a structure file');
      const r = autoCheck(s);
      if (args.json) return console.log(JSON.stringify(r, null, 2));
      console.log(`${s.name} v${s.version}`);
      printAuto(r);
      if (r.issues.some((i) => i.level === 'fail')) process.exitCode = 1;
      return;
    }

    case 'render': {
      if (!rest.length) fail('render <name...>');
      const { renderStructures } = await import('./render.ts');
      const jobs = rest.map((name) => {
        const s = load(store, name, num(args, 'version'));
        return { structure: s, outDir: renderDir(s.name, s.version), prefix: `${s.name}_v${s.version}` };
      });
      const base = { width: 1100, height: 760, terrain: 'flat' };
      const files = [
        ...(await renderStructures(jobs, { ...base, views: ['iso', 'iso_back', 'front', 'right', 'back', 'top'], markers: true })),
        ...(await renderStructures(jobs.map((j) => ({ ...j, prefix: `${j.prefix}_slope` })), { ...base, views: ['iso'], terrain: 'slope' })),
        ...(await renderStructures(jobs.map((j) => ({ ...j, prefix: `${j.prefix}_floor` })), { ...base, views: ['top'], layer: 'upto', y: 1 })),
      ];
      for (const j of jobs) {
        const mine = files.filter((f) => path.dirname(f) === j.outDir);
        const sheet = path.join(j.outDir, `${j.prefix}_sheet.png`);
        await contactSheet(mine, sheet, `${j.structure.name} v${j.structure.version}`);
        console.log(path.relative(process.cwd(), sheet) + '   <- all views on one picture: look at this first');
      }
      for (const f of files) console.log(path.relative(process.cwd(), f));
      return;
    }

    case 'brief': {
      const name = rest[0] ?? fail('brief <name>');
      const s = load(store, name, num(args, 'version'));
      const round = roundFor(store, name, s.version);
      console.log(`# Quality check brief: ${name} v${s.version} (round ${round} of ${MAX_ROUNDS})\n`);
      console.log(`Description: ${s.meta.description ?? '-'}`);
      if (s.meta.changes) console.log(`Changes in this version: ${s.meta.changes}`);
      console.log('\n## Automatic checks');
      printAuto(autoCheck(s));
      const prev = s.meta.parent ? loadQc(store, name).reports[String(s.meta.parent)] : undefined;
      if (prev) {
        console.log(`\n## Last check (v${prev.version}, ${prev.verdict}, ${prev.score}/100): did this version fix it?`);
        for (const p of prev.visual.problems) console.log(`  - ${p}`);
      }
      const family = store.summary(name).levels ?? [name];
      if (family.length > 1) {
        console.log(`\n## Levels of this structure (score "levels": render and compare every one)`);
        for (const n of family) {
          const v = store.summary(n);
          const last = v.versions[v.versions.length - 1];
          console.log(`  ${n} v${v.latest}: ${last.blocks} blocks${n === name ? '  <- this one' : ''}`);
        }
      }
      const notes = carltonNotes(store, name);
      console.log(`\n## Carlton's notes on this structure and its levels (his taste; they outrank the rubric)`);
      if (!notes.length) console.log('  none yet');
      for (const n of notes) console.log(`  - ${n.name} v${n.version} (${n.status}): ${n.notes}`);
      console.log(`\n## Rubric (score each 0, 1 or 2; qc/RUBRIC.md has the details)`);
      for (const c of CRITERIA) console.log(`  ${c.id.padEnd(10)} x${c.weight}${c.must ? ' MUST' : '     '} ${c.label}: ${c.look}`);
      const dir = renderDir(name, s.version);
      const pics = fs.existsSync(dir) ? fs.readdirSync(dir).filter((f) => f.endsWith('.png')) : [];
      console.log(`\n## Renders (${pics.length ? 'look at every one' : `none yet: npm run qc -- render ${name} -v ${s.version}`})`);
      for (const p of pics) console.log(`  ${path.relative(process.cwd(), path.join(dir, p))}`);
      return;
    }

    case 'record': {
      const name = rest[0] ?? fail('record <name> -v N --visual review.json');
      const version = num(args, 'version') ?? fail('-v N is required (the version you looked at)');
      const s = load(store, name, version);
      const file = str(args, 'visual') ?? fail('--visual review.json is required');
      let visual: VisualReview;
      try {
        visual = JSON.parse(fs.readFileSync(file, 'utf8')) as VisualReview;
      } catch (e) {
        fail(`${file}: ${(e as Error).message}`);
      }
      const errors = checkVisual(visual);
      if (errors.length) fail('bad review file:\n' + errors.map((e) => '  ' + e).join('\n'));
      const auto = autoCheck(s);
      const round = roundFor(store, name, version);
      const { verdict, score, why } = verdictOf(auto, visual, round);
      const report: QcReport = { version, checkedAt: new Date().toISOString(), by: str(args, 'by') ?? 'claude-qc', verdict, round, score, auto, visual };
      saveReport(store, name, report);
      console.log(`${name} v${version}: ${verdict.toUpperCase()} · ${score}/100 · round ${round} of ${MAX_ROUNDS}`);
      for (const w of why) console.log(`  because ${w}`);
      if (verdict !== 'pass') {
        console.log(verdict === 'revise' ? '\nFix these, then save the next version:' : '\nHanded over to Carlton with these problems:');
        for (const p of visual.problems) console.log(`  - ${p}`);
        for (const i of auto.issues) if (i.level === 'fail') console.log(`  - ${i.message}`);
      }
      console.log(`\nwrote ${path.relative(process.cwd(), qcFile(store, name))}`);
      return;
    }

    case 'status': {
      for (const s of store.list()) {
        const q = loadQc(store, s.name);
        const vs = Object.keys(q.reports).map(Number).sort((a, b) => a - b);
        const last = vs.length ? q.reports[String(vs[vs.length - 1])] : null;
        const note = !last ? 'not checked' : `v${last.version} ${last.verdict} ${last.score}/100 (round ${last.round})${last.version !== s.latest ? `, v${s.latest} not checked yet` : ''}`;
        console.log(`${s.name.padEnd(22)} ${note}`);
      }
      return;
    }

    default:
      console.log(HELP);
  }
}

main().catch((e) => fail((e as Error).stack ?? String(e)));

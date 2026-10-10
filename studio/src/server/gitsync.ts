// Keeps the structure library in step with GitHub, so the owner never has to type git commands:
//   pull()          on every page load: brings in new structures agents pushed (git pull --rebase --autostash)
//   commitVersion() after approve / reject / notes / rollback: commits that one version file and pushes it
//   commitFile()    the same for any one file under studio/structures/ (the CLI's work.json)
//   commitLater()   notes saved while typing: one commit LATER_MS after the last change
// Only files under studio/structures/ are ever committed here. Set STUDIO_GIT=off to turn it all off.
// Git runs one command at a time (a queue), so a review made during a pull waits for it.
import { execFile } from 'node:child_process';
import path from 'node:path';
import { STRUCTURES_DIR, STUDIO_ROOT } from './store.ts';

export interface GitResult {
  ok: boolean;
  /** One line for the toast, e.g. "3 new structure files from GitHub" or why it failed. */
  message: string;
}

const REPO = path.resolve(STUDIO_ROOT, '..');
let queue: Promise<unknown> = Promise.resolve();
let lastPull = 0;

function git(...args: string[]): Promise<{ code: number; out: string }> {
  return new Promise((resolve) => {
    execFile('git', args, { cwd: REPO, timeout: 60_000 }, (err, stdout, stderr) => {
      const code = err ? (typeof err.code === 'number' ? err.code : 1) : 0;
      resolve({ code, out: (stdout + stderr).trim() });
    });
  });
}

function serial<T>(job: () => Promise<T>): Promise<T> {
  const run = queue.then(job, job);
  queue = run.catch(() => undefined);
  return run;
}

export function gitEnabled(): boolean {
  return process.env.STUDIO_GIT !== 'off';
}

async function pullNow(): Promise<GitResult> {
  const before = await git('rev-parse', 'HEAD');
  if (before.code) return { ok: false, message: 'Not a git checkout: sync is off' };
  const r = await git('pull', '--rebase', '--autostash', '--quiet');
  if (r.code) {
    await git('rebase', '--abort');
    return { ok: false, message: `Couldn't get updates from GitHub: ${r.out.split('\n')[0]}` };
  }
  const after = await git('rev-parse', 'HEAD');
  if (after.out === before.out) return { ok: true, message: '' };
  const changed = await git('diff', '--name-only', before.out, after.out, '--', 'studio/structures');
  const n = changed.out ? changed.out.split('\n').length : 0;
  return { ok: true, message: n ? `${n} new or updated structure file(s) from GitHub` : 'Updated from GitHub' };
}

/** Pulls at most once every 10 seconds (page reloads in a row reuse the last result). */
export function pull(): Promise<GitResult> {
  if (!gitEnabled()) return Promise.resolve({ ok: true, message: '' });
  if (Date.now() - lastPull < 10_000) return Promise.resolve({ ok: true, message: '' });
  lastPull = Date.now();
  return serial(pullNow);
}

/** Commits one version file (a review, or a rollback's new version) and pushes it. Nothing else is committed. */
export function commitVersion(name: string, version: number, message: string): Promise<GitResult> {
  return commitFile(path.join(STRUCTURES_DIR, name, `${name}_v${version}.json`), message);
}

/** Commits one file under studio/structures/ (a version, or a work.json) and pushes it. Also for a deleted file. */
export function commitFile(absFile: string, message: string): Promise<GitResult> {
  return commitFiles([absFile], message);
}

/** Commits files under studio/structures/ in one commit and pushes it. Notes waiting in commitLater go along. */
export function commitFiles(absFiles: string[], message: string): Promise<GitResult> {
  if (!gitEnabled()) return Promise.resolve({ ok: true, message: '' });
  const all = [...new Set([...absFiles, ...takeLater()])];
  return serial(async () => {
    const files = all.map((f) => path.relative(REPO, f));
    if (!files.length || files.some((f) => !f.startsWith(path.join('studio', 'structures') + path.sep))) {
      return { ok: false, message: 'Not a structure file' };
    }
    const add = await git('add', '-A', '--', ...files);
    if (add.code) return { ok: false, message: `Couldn't save to git: ${add.out.split('\n')[0]}` };
    const commit = await git('commit', '--quiet', '-m', message, '--', ...files);
    if (commit.code && !/nothing to commit|no changes added|did not match any file/.test(commit.out)) {
      return { ok: false, message: `Couldn't commit: ${commit.out.split('\n')[0]}` };
    }
    const pulled = await pullNow();
    if (!pulled.ok) return { ok: false, message: `Saved locally, not pushed. ${pulled.message}` };
    const push = await git('push', '--quiet');
    if (push.code) return { ok: false, message: `Saved locally, not pushed: ${push.out.split('\n')[0]}` };
    return { ok: true, message: 'Pushed to GitHub' };
  });
}

// Notes saved while typing are committed together, LATER_MS after the last change (one commit, not one per pause).
const LATER_MS = 20_000;
let later = new Set<string>();
let laterTimer: ReturnType<typeof setTimeout> | null = null;

function takeLater(): string[] {
  if (laterTimer) clearTimeout(laterTimer);
  laterTimer = null;
  const files = [...later];
  later = new Set();
  return files;
}

/** Saved notes: commits and pushes these files a little later, together with any other notes typed meanwhile. */
export function commitLater(absFiles: string[]) {
  if (!gitEnabled()) return;
  for (const f of absFiles) later.add(f);
  if (laterTimer) clearTimeout(laterTimer);
  laterTimer = setTimeout(() => {
    const n = later.size;
    if (n) void commitFiles([], `Studio review: notes (${n} file${n > 1 ? 's' : ''})`);
  }, LATER_MS);
}

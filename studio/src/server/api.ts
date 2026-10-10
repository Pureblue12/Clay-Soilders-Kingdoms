// HTTP API for the browser app, mounted into the Vite dev server (vite.config.ts).
//   GET  /api/assets                              where the Minecraft assets come from
//   GET  /api/structures                          every structure with its versions and statuses
//   GET  /api/structures/:name/:version           one version's file
//   POST /api/structures/:name/:version/review    { status|null, notes, allLevels?, autosave? }   (the review UI only)
//   POST /api/structures/:name/rollback           { from }            new version copying an old one
//   POST /api/sync                                pull new structures from GitHub (gitsync.ts)
//   GET  /mc/<path>                               files from the user's assets (blockstates, models, textures)
import fs from 'node:fs';
import type { IncomingMessage, ServerResponse } from 'node:http';
import { STATUSES, type Status } from '../format/structure.ts';
import { assetFile, assetSource } from './assets.ts';
import { branchWork, commitFiles, commitLater, commitVersion, pull, type GitResult } from './gitsync.ts';
import { Store } from './store.ts';

type Next = () => void;

function send(res: ServerResponse, code: number, body: unknown) {
  res.statusCode = code;
  res.setHeader('Content-Type', 'application/json');
  res.setHeader('Cache-Control', 'no-store');
  res.end(JSON.stringify(body));
}

async function readBody(req: IncomingMessage): Promise<Record<string, unknown>> {
  const chunks: Buffer[] = [];
  for await (const c of req) chunks.push(c as Buffer);
  const text = Buffer.concat(chunks).toString('utf8');
  return text ? (JSON.parse(text) as Record<string, unknown>) : {};
}

export function createApi(store = new Store()) {
  store.branchWork = branchWork;
  return async (req: IncomingMessage, res: ServerResponse, next: Next) => {
    const url = new URL(req.url ?? '/', 'http://localhost');
    const parts = url.pathname.split('/').filter(Boolean).map(decodeURIComponent);
    try {
      if (parts[0] === 'mc') {
        const file = assetFile(parts.slice(1).join('/'));
        if (!file) return send(res, 404, { error: 'not found' });
        res.setHeader('Content-Type', file.endsWith('.png') ? 'image/png' : 'application/json');
        res.setHeader('Cache-Control', 'max-age=3600');
        return fs.createReadStream(file).pipe(res);
      }
      if (parts[0] !== 'api') return next();
      if (parts[1] === 'assets') {
        const s = assetSource(url.searchParams.has('refresh'));
        return send(res, 200, { kind: s.kind, detail: s.detail });
      }
      if (parts[1] === 'sync' && req.method === 'POST') return send(res, 200, await pull());
      if (parts[1] === 'structures') {
        if (req.method === 'GET' && parts.length === 2) return send(res, 200, store.list());
        const name = parts[2];
        if (req.method === 'GET' && parts.length === 3) return send(res, 200, store.summary(name));
        if (req.method === 'GET' && parts.length === 4) return send(res, 200, store.load(name, Number(parts[3])));
        if (req.method === 'POST' && parts[4] === 'review') {
          // The approval button lives in the UI; the header keeps casual scripts from approving by accident.
          if (req.headers['x-studio-ui'] !== 'review') return send(res, 403, { error: 'reviews are made in the studio UI' });
          const body = await readBody(req);
          // status null = notes only. allLevels: the same review for the same version number of every level
          // (bench_l1, bench_l2, ...). autosave: notes typed in the box, committed a little later.
          const status = body.status === null || body.status === undefined ? null : (String(body.status) as Status);
          if (status !== null && !STATUSES.includes(status)) return send(res, 400, { error: 'bad status' });
          const version = Number(parts[3]);
          const targets: [string, number][] = body.allLevels ? store.levelTargets(name, version) : [[name, version]];
          for (const [n, v] of targets) store.setReview(n, v, status, String(body.notes ?? ''));
          const files = targets.map(([n, v]) => store.versionFile(n, v));
          const what = targets.map(([n, v]) => `${n} v${v}`).join(', ');
          let git: GitResult = { ok: true, message: '' };
          if (body.autosave) commitLater(files);
          else git = await commitFiles(files, `Studio review: ${what} ${status ?? 'notes'}`);
          return send(res, 200, { summary: store.summary(name), saved: targets.map(([n, v]) => ({ name: n, version: v })), git });
        }
        if (req.method === 'POST' && parts[3] === 'rollback') {
          const body = await readBody(req);
          const v = store.rollback(name, Number(body.from), 'studio-ui (rollback)');
          const git = await commitVersion(name, v, `Studio: ${name} v${v} (rollback to v${Number(body.from)})`);
          return send(res, 200, { version: v, summary: store.summary(name), git });
        }
      }
      return send(res, 404, { error: 'unknown endpoint' });
    } catch (e) {
      return send(res, 400, { error: (e as Error).message });
    }
  };
}

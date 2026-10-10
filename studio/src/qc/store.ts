// Quality-check reports on disk: structures/<name>/qc.json, one report per checked version.
// A separate file, so the version files (Carlton's reviews) are never touched by the reviewer.
import fs from 'node:fs';
import path from 'node:path';
import type { Store } from '../server/store.ts';
import type { QcReport } from './rubric.ts';

export const QC_FORMAT = 'csr-studio-qc/1';

export interface QcFile {
  format: string;
  name: string;
  reports: Record<string, QcReport>; // version -> report
}

export function qcFile(store: Store, name: string) {
  return path.join(store.dir, name, 'qc.json');
}

export function loadQc(store: Store, name: string): QcFile {
  try {
    return JSON.parse(fs.readFileSync(qcFile(store, name), 'utf8')) as QcFile;
  } catch {
    return { format: QC_FORMAT, name, reports: {} };
  }
}

/** structures/qc-index.json: the newest report of every structure in a few fields, for the dots in the page's list. */
export interface QcIndexEntry {
  version: number;
  verdict: QcReport['verdict'];
  score: number;
  round: number;
  checkedAt: string;
}

export function indexFile(store: Store) {
  return path.join(store.dir, 'qc-index.json');
}

export function loadIndex(store: Store): Record<string, QcIndexEntry> {
  try {
    return JSON.parse(fs.readFileSync(indexFile(store), 'utf8')) as Record<string, QcIndexEntry>;
  } catch {
    return {};
  }
}

export function saveReport(store: Store, name: string, report: QcReport) {
  const f = loadQc(store, name);
  f.reports[String(report.version)] = report;
  fs.writeFileSync(qcFile(store, name), JSON.stringify(f, null, 2) + '\n');
  const index = loadIndex(store);
  if (!index[name] || index[name].version <= report.version) {
    const { version, verdict, score, round, checkedAt } = report;
    index[name] = { version, verdict, score, round, checkedAt };
  }
  const sorted = Object.fromEntries(Object.entries(index).sort(([a], [b]) => a.localeCompare(b)));
  fs.writeFileSync(indexFile(store), JSON.stringify(sorted, null, 2) + '\n');
}

/** Round of a version: 1 + the parent's round when the parent was sent back by the check (this version answers it). */
export function roundFor(store: Store, name: string, version: number): number {
  const parent = store.load(name, version).meta.parent;
  if (!parent) return 1;
  const prev = loadQc(store, name).reports[String(parent)];
  return prev && prev.verdict === 'revise' ? prev.round + 1 : 1;
}

/** Carlton's notes on every earlier version of this structure and its other levels: his taste, most recent first. */
export function carltonNotes(store: Store, name: string): { name: string; version: number; status: string; notes: string }[] {
  const base = /^(.*)_l\d+$/.exec(name)?.[1];
  const family = base ? store.names().filter((n) => n === name || new RegExp(`^${base}_l\\d+$`).test(n)) : [name];
  const out: { name: string; version: number; status: string; notes: string; at: string }[] = [];
  for (const n of family)
    for (const v of store.versions(n)) {
      const m = store.load(n, v).meta;
      if (m.notes?.trim()) out.push({ name: n, version: v, status: m.status, notes: m.notes, at: m.reviewedAt ?? m.created });
    }
  return out.sort((a, b) => b.at.localeCompare(a.at)).map(({ at: _a, ...r }) => r);
}

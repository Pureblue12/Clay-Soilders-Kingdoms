// The structure library on disk: structures/<name>/<name>_v<N>.json.
// Versions are never overwritten: a new version always gets the next free number (written with the "wx" flag).
// The only change allowed to an existing version is its review metadata (status, notes, reviewedAt).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  FORMAT_ID,
  STATUSES,
  blockCounts,
  stringifyStructure,
  validateStructure,
  type Status,
  type StructureFile,
} from '../format/structure.ts';

export const STUDIO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
export const STRUCTURES_DIR = path.join(STUDIO_ROOT, 'structures');

const FILE_RE = /^([a-z0-9_]+)_v(\d+)\.json$/;

export interface VersionSummary {
  version: number;
  status: Status;
  notes: string;
  author: string;
  created: string;
  reviewedAt: string | null;
  parent: number | null;
  size: [number, number, number];
  blocks: number;
  description: string;
  changes: string;
}

export interface StructureSummary {
  name: string;
  latest: number;
  status: Status; // status of the latest version
  approvedVersion: number | null; // newest approved version, if any
  versions: VersionSummary[];
  tags: string[];
}

export class Store {
  constructor(readonly dir = STRUCTURES_DIR) {}

  private file(name: string, version: number) {
    return path.join(this.dir, name, `${name}_v${version}.json`);
  }

  versions(name: string): number[] {
    const d = path.join(this.dir, name);
    if (!fs.existsSync(d)) return [];
    return fs
      .readdirSync(d)
      .map((f) => FILE_RE.exec(f))
      .filter((m): m is RegExpExecArray => !!m && m[1] === name)
      .map((m) => Number(m[2]))
      .sort((a, b) => a - b);
  }

  names(): string[] {
    if (!fs.existsSync(this.dir)) return [];
    return fs
      .readdirSync(this.dir, { withFileTypes: true })
      .filter((e) => e.isDirectory() && this.versions(e.name).length > 0)
      .map((e) => e.name)
      .sort();
  }

  load(name: string, version?: number): StructureFile {
    const vs = this.versions(name);
    if (vs.length === 0) throw new Error(`no structure "${name}"`);
    const v = version ?? vs[vs.length - 1];
    if (!vs.includes(v)) throw new Error(`"${name}" has no version ${v} (has ${vs.join(', ')})`);
    return JSON.parse(fs.readFileSync(this.file(name, v), 'utf8')) as StructureFile;
  }

  summary(name: string): StructureSummary {
    const versions = this.versions(name).map((v) => {
      const s = this.load(name, v);
      let blocks = 0;
      for (const n of blockCounts(s).values()) blocks += n;
      return {
        version: v,
        status: s.meta.status,
        notes: s.meta.notes,
        author: s.meta.author,
        created: s.meta.created,
        reviewedAt: s.meta.reviewedAt ?? null,
        parent: s.meta.parent ?? null,
        size: s.size,
        blocks,
        description: s.meta.description ?? '',
        changes: s.meta.changes ?? '',
        tags: s.meta.tags ?? [],
      };
    });
    const latest = versions[versions.length - 1];
    const approved = [...versions].reverse().find((v) => v.status === 'approved');
    return {
      name,
      latest: latest.version,
      status: latest.status,
      approvedVersion: approved?.version ?? null,
      versions: versions.map(({ tags: _t, ...v }) => v),
      tags: latest.tags,
    };
  }

  list(): StructureSummary[] {
    return this.names().map((n) => this.summary(n));
  }

  /**
   * Saves `data` as the next version of its structure and returns that version number.
   * Status always starts as pending (an agent can never save an approved version).
   */
  saveNewVersion(data: StructureFile, opts: { author?: string; parent?: number | null } = {}): number {
    const name = data.name;
    const existing = this.versions(name);
    const version = (existing[existing.length - 1] ?? 0) + 1;
    const out: StructureFile = {
      ...data,
      format: FORMAT_ID,
      version,
      meta: {
        ...data.meta,
        status: 'pending',
        reviewedAt: null,
        author: opts.author ?? data.meta?.author ?? 'unknown',
        created: new Date().toISOString(),
        parent: opts.parent !== undefined ? opts.parent : existing.length ? existing[existing.length - 1] : null,
      },
    };
    const errors = validateStructure(out).filter((i) => i.level === 'error');
    if (errors.length) throw new Error('invalid structure:\n' + errors.map((e) => '  ' + e.message).join('\n'));
    fs.mkdirSync(path.join(this.dir, name), { recursive: true });
    fs.writeFileSync(this.file(name, version), stringifyStructure(out), { flag: 'wx' });
    return version;
  }

  /** Review change from the UI. Only the meta fields change; the blocks of a version stay as they were. */
  setReview(name: string, version: number, status: Status, notes: string): StructureFile {
    if (!STATUSES.includes(status)) throw new Error(`bad status "${status}"`);
    const s = this.load(name, version);
    s.meta.status = status;
    s.meta.notes = notes;
    s.meta.reviewedAt = new Date().toISOString();
    fs.writeFileSync(this.file(name, version), stringifyStructure(s));
    return s;
  }

  /** Rollback = a new version that copies an old one. Nothing is deleted. */
  rollback(name: string, fromVersion: number, author: string): number {
    const old = this.load(name, fromVersion);
    return this.saveNewVersion(
      { ...old, meta: { ...old.meta, notes: '', changes: `Rollback: copy of v${fromVersion}.` } },
      { author, parent: fromVersion },
    );
  }
}

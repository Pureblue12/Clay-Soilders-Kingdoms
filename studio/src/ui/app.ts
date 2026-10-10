// The studio app: library with thumbnails, 3D view, review panel (approve / reject / notes), versions with
// compare and rollback, layer view and test terrain.
import { blockCounts, STAGE_LABELS, type Stage, type Status, type StructureFile } from '../format/structure.ts';
import { buildability } from '../format/modrules.ts';
import { BlockLibrary, HttpAssets } from '../render/blocks.ts';
import { DEFAULT_SCENE, showStructure, type SceneOptions } from '../render/scene.ts';
import { PRESETS, Viewer, type CameraPreset } from '../render/viewer.ts';
import type { LayerMode, TerrainKind, World } from '../render/world.ts';
import type { StructureSummary } from '../server/store.ts';

const $ = <T extends HTMLElement = HTMLElement>(sel: string) => document.querySelector(sel) as T;
const PRESET_LABELS: Record<CameraPreset, string> = {
  iso: 'Angle', front: 'Front', right: 'Right', back: 'Back', left: 'Left', top: 'Top', iso_back: 'Angle 2',
};

const state = {
  list: [] as StructureSummary[],
  filter: 'all' as 'all' | Stage,
  search: '',
  name: null as string | null,
  version: 0,
  compare: false,
  compareVersion: 0,
  scene: { ...DEFAULT_SCENE, layer: { ...DEFAULT_SCENE.layer } } as SceneOptions,
  worldA: null as World | null,
  worldB: null as World | null,
  allLevels: (() => { try { return !!localStorage.getItem('studio.allLevels'); } catch { return false; } })(),
};

const files = new Map<string, StructureFile>();
const thumbs = new Map<string, string>();

const assets = new HttpAssets();
const lib = new BlockLibrary(assets);
// thumbnails get their own atlas, so making one never re-packs the textures the main view is using
const thumbLib = new BlockLibrary(assets);
let viewerA: Viewer;
let viewerB: Viewer | null = null;
let thumbViewer: Viewer | null = null;

// ---------------------------------------------------------------- data

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const r = await fetch(path, init);
  const body = await r.json();
  if (!r.ok) throw new Error(body.error ?? r.statusText);
  return body as T;
}

async function getFile(name: string, version: number): Promise<StructureFile> {
  const key = `${name}@${version}`;
  let f = files.get(key);
  if (!f) {
    f = await api<StructureFile>(`/api/structures/${encodeURIComponent(name)}/${version}`);
    files.set(key, f);
  }
  return f;
}

async function refreshList() {
  state.list = await api<StructureSummary[]>('/api/structures');
  renderList();
  renderCounts();
  void makeThumbnails();
}

/** "3 need review · 5 queued · 1 revising · 2 approved" under the title. */
function renderCounts() {
  const n = (st: Stage) => state.list.filter((s) => s.stage === st).length;
  const parts: string[] = [];
  if (n('review')) parts.push(`<b class="stage-text review">${n('review')}</b> need your review`);
  if (n('queued')) parts.push(`<b class="stage-text queued">${n('queued')}</b> queued`);
  if (n('revising')) parts.push(`<b class="stage-text revising">${n('revising')}</b> revising`);
  if (n('approved')) parts.push(`<b class="stage-text approved">${n('approved')}</b> approved`);
  $('#counts').innerHTML = parts.join(' · ') || 'No structures yet';
}

function stagePill(s: StructureSummary) {
  const who = s.work ? ` title="${escapeHtml(`${s.work.by} since ${new Date(s.work.since).toLocaleString()}${s.work.note ? `: ${s.work.note}` : ''}`)}"` : '';
  return `<span class="pill stage ${s.stage}"${who}>${s.stage === 'revising' ? '<i class="spin"></i>' : ''}${STAGE_LABELS[s.stage]}</span>`;
}

/** Every 30 s: pull from GitHub and redraw the list, so new versions and "Revising" show up without a reload. */
function startAutoRefresh() {
  window.setInterval(async () => {
    if (document.hidden) return;
    const sync = await api<{ ok: boolean; message: string }>('/api/sync', { method: 'POST' }).catch(
      () => ({ ok: true, message: '' }));
    const before = JSON.stringify(state.list);
    await refreshList().catch(() => undefined);
    if (sync.message) toast(sync.message, !sync.ok);
    if (JSON.stringify(state.list) !== before && summary()) renderReview();
  }, 30_000);
}

function summary(): StructureSummary | undefined {
  return state.list.find((s) => s.name === state.name);
}

function toast(text: string, error = false) {
  const t = $('#toast');
  t.textContent = text;
  t.classList.toggle('error', error);
  t.hidden = false;
  clearTimeout((t as unknown as { _h: number })._h);
  (t as unknown as { _h: number })._h = window.setTimeout(() => (t.hidden = true), error ? 8000 : 2600);
}

// ---------------------------------------------------------------- library

function renderList() {
  const ul = $('#list');
  ul.innerHTML = '';
  const q = state.search.toLowerCase();
  for (const s of state.list) {
    if (state.filter !== 'all' && s.stage !== state.filter) continue;
    if (q && !s.name.includes(q)) continue;
    const li = document.createElement('li');
    li.dataset.name = s.name;
    if (s.name === state.name) li.classList.add('sel');
    const latest = s.versions[s.versions.length - 1];
    const thumb = thumbs.get(`${s.name}@${s.latest}`);
    li.innerHTML = `
      ${thumb ? `<img alt="" src="${thumb}">` : '<div class="thumb"></div>'}
      <div>
        <div class="name">${s.name}</div>
        <div class="sub">v${s.latest} · ${latest.size.join('×')} · ${latest.blocks} blocks</div>
        <div class="sub">${stagePill(s)}${s.approvedVersion && s.approvedVersion !== s.latest ? ` <span class="muted">v${s.approvedVersion} approved</span>` : ''}</div>
      </div>`;
    li.onclick = () => select(s.name, s.latest);
    ul.appendChild(li);
  }
  if (!ul.children.length) ul.innerHTML = '<li class="muted" style="cursor:default;display:block">Nothing here.</li>';
}

let thumbBusy = false;
async function makeThumbnails() {
  if (thumbBusy) return;
  thumbBusy = true;
  try {
    for (const s of state.list) {
      const key = `${s.name}@${s.latest}`;
      if (thumbs.has(key)) continue;
      if (!thumbViewer) {
        const c = document.createElement('canvas');
        thumbViewer = new Viewer({ canvas: c, width: 288, height: 192, preserve: true, shadowMapSize: 1024 });
        thumbViewer.renderer.setPixelRatio(1);
        thumbViewer.setSize(288, 192);
      }
      const f = await getFile(s.name, s.latest);
      await showStructure(thumbViewer, thumbLib, f, { ...DEFAULT_SCENE, terrainSize: 48 });
      thumbViewer.preset('iso');
      thumbViewer.render();
      thumbs.set(key, thumbViewer.renderer.domElement.toDataURL('image/png'));
      const img = document.querySelector(`#list li[data-name="${s.name}"] .thumb, #list li[data-name="${s.name}"] img`);
      if (img) {
        const el = document.createElement('img');
        el.src = thumbs.get(key)!;
        img.replaceWith(el);
      }
    }
  } finally {
    thumbBusy = false;
  }
}

// ---------------------------------------------------------------- selection and scene

async function select(name: string, version: number, keepCamera = false) {
  const changedStructure = name !== state.name;
  state.name = name;
  state.version = version;
  if (changedStructure) {
    state.compare = false;
    state.scene.layer = { mode: 'all', y: 0 };
  }
  renderList();
  await showScene(!keepCamera || changedStructure);
  renderReview();
}

async function showScene(resetCamera: boolean) {
  if (!state.name) return;
  const f = await getFile(state.name, state.version);
  const ymax = f.size[1] - 1;
  if (state.scene.layer.y > ymax) state.scene.layer.y = ymax;
  state.worldA = await showStructure(viewerA, lib, f, state.scene);
  setLabel('#view-a', state.compare ? `v${state.version}` : '');
  const vb = $('#view-b');
  if (state.compare) {
    vb.hidden = false;
    if (!viewerB) {
      viewerB = new Viewer({ canvas: vb.querySelector('canvas')! });
      watchSize(vb, viewerB);
      viewerB.start();
    }
    const fb = await getFile(state.name, state.compareVersion);
    const before = lib.atlasVersion;
    state.worldB = await showStructure(viewerB, lib, fb, state.scene);
    if (lib.atlasVersion !== before) viewerA.setWorld(state.worldA, lib); // v B brought new textures
    setLabel('#view-b', `v${state.compareVersion}`);
    // one camera for both sides: whichever view the mouse moves drives the other
    viewerB.onFrame = null;
    viewerA.onFrame = null;
    let driver: Viewer = viewerA;
    viewerA.renderer.domElement.onpointerenter = () => (driver = viewerA);
    viewerB.renderer.domElement.onpointerenter = () => (driver = viewerB!);
    viewerA.onFrame = () => { if (driver === viewerB && viewerB) viewerA.copyCameraFrom(viewerB); };
    viewerB.onFrame = () => { if (driver === viewerA) viewerB!.copyCameraFrom(viewerA); };
  } else {
    vb.hidden = true;
    viewerA.onFrame = null;
  }
  resizeAll();
  if (resetCamera) {
    viewerA.preset('iso');
    if (state.compare && viewerB) viewerB.copyCameraFrom(viewerA);
  }
  updateLayerBar(f);
  updateBanner();
}

function setLabel(sel: string, text: string) {
  $(`${sel} .view-label`).textContent = text;
}

function applyLayer() {
  for (const [w, v] of [[state.worldA, viewerA], [state.worldB, viewerB]] as [World | null, Viewer | null][]) {
    if (!w || !v) continue;
    for (const p of w.placements) p.filter = { ...state.scene.layer };
    v.rebuildPlacements();
  }
  const f = files.get(`${state.name}@${state.version}`);
  if (f) updateLayerBar(f);
}

function updateLayerBar(f: StructureFile) {
  const r = $<HTMLInputElement>('#layer-y');
  r.max = String(f.size[1] - 1);
  r.value = String(state.scene.layer.y);
  r.disabled = state.scene.layer.mode === 'all';
  document.querySelectorAll<HTMLButtonElement>('[data-layer]').forEach((b) =>
    b.classList.toggle('on', b.dataset.layer === state.scene.layer.mode));
  $('#layer-label').textContent = state.scene.layer.mode === 'all' ? `${f.size[1]} layers` : `y ${state.scene.layer.y} / ${f.size[1] - 1}`;
}

function updateBanner() {
  const b = $('#asset-banner');
  if (!assets.realAssets) {
    b.hidden = false;
    b.textContent = 'PLACEHOLDER TEXTURES: no Minecraft assets found. Put your Minecraft 1.21.1 client jar in studio/minecraft/ and reload (see studio/minecraft/README.md).';
  } else if (lib.placeholderCount) {
    b.hidden = false;
    b.textContent = `${lib.placeholderCount} block state(s) have no Minecraft model and are drawn as labelled placeholders.`;
  } else b.hidden = true;
}

// ---------------------------------------------------------------- review panel

function renderReview() {
  const s = summary();
  $('#empty-review').hidden = !!s;
  $('#review-body').hidden = !s;
  if (!s) return;
  const v = s.versions.find((x) => x.version === state.version)!;
  $('#r-title').textContent = `${s.name} · v${v.version}`;
  $('#r-meta').textContent = `${v.size.join(' × ')} (x × y × z) · ${v.blocks} blocks · by ${v.author} · ${new Date(v.created).toLocaleString()}${v.parent ? ` · from v${v.parent}` : ''}`;
  $('#r-desc').textContent = v.description;
  $('#r-status').innerHTML = `Status: <span class="pill ${v.status}">${v.status}</span>${v.reviewedAt ? ` <span class="muted">reviewed ${new Date(v.reviewedAt).toLocaleString()}</span>` : ''}`;
  const w = s.work;
  $('#r-stage').innerHTML = `Next: ${stagePill(s)}${
    w ? ` <span class="muted">${escapeHtml(w.by)} since ${new Date(w.since).toLocaleString()}</span>${w.note ? `<div class="vnotes"><b>Plan:</b> ${escapeHtml(w.note)}</div>` : ''}`
      : s.stage === 'queued' ? ' <span class="muted">waiting for Claude to pick it up</span>' : ''}`;
  $('#approve').classList.toggle('current', v.status === 'approved');
  $('#reject').classList.toggle('current', v.status === 'rejected');
  const notes = $<HTMLTextAreaElement>('#notes');
  if (document.activeElement !== notes) notes.value = v.notes;
  if (notes.dataset.for !== `${s.name}@${v.version}`) notesState(''); // another structure: "Saved ✓" was for the last one
  notes.dataset.for = `${s.name}@${v.version}`;
  renderLevelsToggle(s);

  const ul = $('#versions');
  ul.innerHTML = '';
  for (const x of [...s.versions].reverse()) {
    const li = document.createElement('li');
    if (x.version === state.version) li.classList.add('sel');
    li.innerHTML = `<div class="vhead"><strong>v${x.version}</strong><span class="pill ${x.status}">${x.status}</span></div>
      ${x.changes ? `<div class="vnotes"><b>Changes:</b> ${escapeHtml(x.changes)}</div>` : ''}
      ${x.notes ? `<div class="vnotes"><b>Review:</b> ${escapeHtml(x.notes)}</div>` : ''}
      <div class="vactions"></div>`;
    li.onclick = () => select(s.name, x.version, true);
    const actions = li.querySelector('.vactions')!;
    if (x.version !== state.version) {
      const cmp = document.createElement('button');
      cmp.textContent = `Compare with v${state.version}`;
      cmp.onclick = (e) => {
        e.stopPropagation();
        state.compare = true;
        state.compareVersion = x.version;
        void showScene(true).then(renderReview);
      };
      actions.appendChild(cmp);
    }
    if (x.version !== s.latest) {
      const rb = document.createElement('button');
      rb.textContent = 'Restore as new version';
      rb.title = 'Rollback: copies this version as the next version. Nothing is deleted.';
      rb.onclick = async (e) => {
        e.stopPropagation();
        if (!confirm(`Copy v${x.version} as a new version of ${s.name}?`)) return;
        const r = await api<{ version: number }>(`/api/structures/${s.name}/rollback`, {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ from: x.version }),
        });
        await refreshList();
        toast(`Restored v${x.version} as v${r.version}`);
        await select(s.name, r.version, true);
      };
      actions.appendChild(rb);
    }
    ul.appendChild(li);
  }

  const sel = $<HTMLSelectElement>('#compare-with');
  sel.innerHTML = s.versions.filter((x) => x.version !== state.version)
    .map((x) => `<option value="${x.version}" ${x.version === state.compareVersion ? 'selected' : ''}>v${x.version} (${x.status})</option>`).join('');
  sel.disabled = s.versions.length < 2;
  $<HTMLButtonElement>('#compare').disabled = s.versions.length < 2;
  $('#compare').classList.toggle('on', state.compare);
  $('#compare').textContent = state.compare ? 'Close compare' : 'Side by side';

  const f = files.get(`${s.name}@${state.version}`);
  const bl = $('#blocks');
  bl.innerHTML = '';
  if (f) {
    const counts = [...blockCounts(f).entries()].sort((a, b) => b[1] - a[1]);
    const b = buildability(counts.map(([n]) => n));
    const flagged = new Set([...b.broken, ...b.noPassage]);
    for (const [n, c] of counts) {
      const short = n.replace('minecraft:', '');
      const li = document.createElement('li');
      li.innerHTML = `<span class="${flagged.has(short) ? 'warn' : ''}" title="${flagged.has(short) ? "Clay Soldiers builders can't place or pass this (docs/STRUCTURES.md)" : ''}">${short}${flagged.has(short) ? ' ⚠' : ''}</span><span>${c}</span>`;
      bl.appendChild(li);
    }
  }
}

function escapeHtml(s: string) {
  return s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!);
}

/** "Apply to all levels": reviews and notes go to the SAME version number of every level (Store.levelTargets). */
function allLevelsOn(s: StructureSummary) {
  return state.allLevels && s.levels.length > 1;
}

/** The levels that have the version on screen, and the ones that don't (they are left alone). */
function levelTargets(s: StructureSummary, version: number) {
  const has = (n: string) => n === s.name || !!state.list.find((x) => x.name === n)?.versions.some((v) => v.version === version);
  return { hit: s.levels.filter(has), missing: s.levels.filter((n) => !has(n)) };
}

function renderLevelsToggle(s: StructureSummary) {
  const b = $<HTMLButtonElement>('#all-levels');
  b.hidden = s.levels.length < 2;
  b.classList.toggle('on', state.allLevels);
  b.textContent = state.allLevels ? 'Apply to all levels: ON' : 'Apply to all levels: off';
  const t = levelTargets(s, state.version);
  b.title = `Approve, reject and notes also go to v${state.version} of ${s.levels.join(', ')}`;
  const hint = $('#levels-hint');
  hint.hidden = !allLevelsOn(s);
  hint.textContent = `Goes to v${state.version} of ${t.hit.join(', ')}` +
    (t.missing.length ? ` (not ${t.missing.join(', ')}: no v${state.version})` : '');
}

/** Approve / reject / pending. Sends the notes in the box too. */
async function review(status: Status) {
  const s = summary();
  if (!s) return;
  cancelAutosave();
  const notes = $<HTMLTextAreaElement>('#notes').value;
  const r = await sendReview(s, state.version, status, notes, false);
  files.delete(`${s.name}@${state.version}`);
  await refreshList();
  renderReview();
  const names = r.saved.map((x) => `${x.name} v${x.version}`).join(', ');
  toast(r.git.message ? `${names}: ${status}. ${r.git.message}` : `${names}: ${status}`, !r.git.ok);
}

function sendReview(s: StructureSummary, version: number, status: Status | null, notes: string, autosave: boolean) {
  return api<{ saved: { name: string; version: number }[]; git: { ok: boolean; message: string } }>(
    `/api/structures/${s.name}/${version}/review`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Studio-UI': 'review' },
      body: JSON.stringify({ status, notes, allLevels: allLevelsOn(s), autosave }),
    });
}

// ---------------------------------------------------------------- notes autosave (while typing, and on leaving the box)

let autosaveTimer = 0;
let pendingNotes: { s: StructureSummary; version: number; notes: string } | null = null;

function notesState(text: string, error = false) {
  const el = $('#notes-state');
  el.textContent = text;
  el.classList.toggle('error', error);
}

function cancelAutosave() {
  clearTimeout(autosaveTimer);
  pendingNotes = null;
}

function onNotesInput() {
  const s = summary();
  if (!s) return;
  // remember WHICH structure the text belongs to: clicking another one blurs the box before it changes
  pendingNotes = { s, version: state.version, notes: $<HTMLTextAreaElement>('#notes').value };
  notesState('Typing…');
  clearTimeout(autosaveTimer);
  autosaveTimer = window.setTimeout(() => void saveNotesNow(), 800);
}

async function saveNotesNow() {
  clearTimeout(autosaveTimer);
  const p = pendingNotes;
  if (!p) return;
  pendingNotes = null;
  try {
    const r = await sendReview(p.s, p.version, null, p.notes, true);
    notesState(r.saved.length > 1 ? `Saved to ${r.saved.length} levels ✓` : 'Saved ✓');
    await refreshList();
    renderReview(); // the notes box itself is left alone while it has focus
  } catch (e) {
    notesState(`Not saved: ${(e as Error).message}`, true);
  }
}

// ---------------------------------------------------------------- wiring

function watchSize(el: HTMLElement, v: Viewer) {
  new ResizeObserver(() => {
    const r = el.getBoundingClientRect();
    if (r.width > 0 && r.height > 0) v.setSize(r.width, r.height);
  }).observe(el);
}
function resizeAll() {
  for (const [sel, v] of [['#view-a', viewerA], ['#view-b', viewerB]] as [string, Viewer | null][]) {
    if (!v) continue;
    const r = $(sel).getBoundingClientRect();
    if (r.width > 0 && r.height > 0) v.setSize(r.width, r.height);
  }
}

function eachViewer(fn: (v: Viewer) => void) {
  fn(viewerA);
  if (state.compare && viewerB) fn(viewerB);
}

function setupControls() {
  const presets = $('#presets');
  for (const p of PRESETS) {
    const b = document.createElement('button');
    b.textContent = PRESET_LABELS[p];
    b.onclick = () => eachViewer((v) => v.preset(p));
    presets.appendChild(b);
  }
  $('#fly').onclick = () => viewerA.enterFly();
  $('#markers').onclick = () => {
    state.scene.markers = !state.scene.markers;
    $('#markers').classList.toggle('on', state.scene.markers);
    eachViewer((v) => v.setMarkers(state.scene.markers));
  };
  $<HTMLSelectElement>('#terrain').onchange = (e) => {
    state.scene.terrain = (e.target as HTMLSelectElement).value as TerrainKind;
    void showScene(false);
  };
  $<HTMLSelectElement>('#terrain-size').onchange = (e) => {
    state.scene.terrainSize = Number((e.target as HTMLSelectElement).value);
    void showScene(false);
  };
  $('#trees').onclick = () => {
    state.scene.trees = !state.scene.trees;
    $('#trees').classList.toggle('on', state.scene.trees);
    void showScene(false);
  };
  $('#reseed').onclick = () => {
    state.scene.seed = Math.floor(Math.random() * 1e6);
    void showScene(false);
  };
  document.querySelectorAll<HTMLButtonElement>('[data-layer]').forEach((b) => {
    b.onclick = () => {
      const mode = b.dataset.layer as LayerMode;
      const f = files.get(`${state.name}@${state.version}`);
      if (mode !== 'all' && state.scene.layer.mode === 'all' && f) state.scene.layer.y = Math.min(f.size[1] - 1, 1);
      state.scene.layer.mode = mode;
      applyLayer();
    };
  });
  $('#topdown').onclick = () => {
    const f = files.get(`${state.name}@${state.version}`);
    if (state.scene.layer.mode === 'all' && f) state.scene.layer.y = Math.min(f.size[1] - 1, 1);
    state.scene.layer.mode = 'upto';
    applyLayer();
    eachViewer((v) => v.preset('top'));
  };
  $<HTMLInputElement>('#layer-y').oninput = (e) => {
    state.scene.layer.y = Number((e.target as HTMLInputElement).value);
    applyLayer();
  };
  const notes = $<HTMLTextAreaElement>('#notes');
  notes.addEventListener('input', onNotesInput);
  notes.addEventListener('blur', () => void saveNotesNow());
  window.addEventListener('beforeunload', () => void saveNotesNow());
  $('#all-levels').onclick = () => {
    state.allLevels = !state.allLevels;
    try { localStorage.setItem('studio.allLevels', state.allLevels ? '1' : ''); } catch { /* private window */ }
    const s = summary();
    if (s) renderLevelsToggle(s);
  };
  $('#approve').onclick = () => review('approved');
  $('#reject').onclick = () => review('rejected');
  $('#set-pending').onclick = () => review('pending');
  $('#compare').onclick = () => {
    const s = summary();
    if (!s) return;
    state.compare = !state.compare;
    if (state.compare) {
      const sel = $<HTMLSelectElement>('#compare-with');
      state.compareVersion = Number(sel.value) || s.versions.find((x) => x.version !== state.version)!.version;
    }
    void showScene(true).then(renderReview); // re-frame: the view just changed width
  };
  $<HTMLSelectElement>('#compare-with').onchange = (e) => {
    state.compareVersion = Number((e.target as HTMLSelectElement).value);
    if (state.compare) void showScene(false).then(renderReview);
  };
  document.querySelectorAll<HTMLButtonElement>('[data-filter]').forEach((b) => {
    b.onclick = () => {
      state.filter = b.dataset.filter as typeof state.filter;
      document.querySelectorAll('[data-filter]').forEach((x) => x.classList.toggle('on', x === b));
      renderList();
    };
  });
  $<HTMLInputElement>('#search').oninput = (e) => {
    state.search = (e.target as HTMLInputElement).value;
    renderList();
  };
  window.addEventListener('keydown', (e) => {
    const tag = (e.target as HTMLElement).tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || viewerA.flying) return;
    const n = Number(e.key);
    if (n >= 1 && n <= PRESETS.length) eachViewer((v) => v.preset(PRESETS[n - 1]));
    if (e.key === 'f' || e.key === 'F') viewerA.enterFly();
  });
}

async function main() {
  await assets.init();
  viewerA = new Viewer({ canvas: $('#view-a canvas') as HTMLCanvasElement });
  watchSize($('#view-a'), viewerA);
  viewerA.start();
  setupControls();
  updateBanner();
  // bring in structures agents pushed since the last visit (and the owner's reviews from another machine)
  const sync = await api<{ ok: boolean; message: string }>('/api/sync', { method: 'POST' }).catch(
    (e: Error) => ({ ok: false, message: e.message }));
  if (sync.message) toast(sync.message, !sync.ok);
  await refreshList();
  startAutoRefresh();
  const hash = new URLSearchParams(location.hash.slice(1));
  const first = hash.get('s') ?? state.list[0]?.name;
  const s = state.list.find((x) => x.name === first);
  if (s) await select(s.name, Number(hash.get('v')) || s.latest);
  window.addEventListener('hashchange', () => location.reload());
}

void main();

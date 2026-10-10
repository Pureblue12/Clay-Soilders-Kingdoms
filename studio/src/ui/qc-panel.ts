// The quality check's own small corner of the page, kept apart from app.ts on purpose:
//   - a coloured dot after each name in the library (passed, being fixed, over to you),
//   - a collapsed "Quality check" section in the review panel that opens to show the reviewer's findings.
// It only reads structures/qc-index.json and structures/<name>/qc.json (written by `npm run qc -- record`), and
// follows the page by watching the review title ("name · vN") and the library list.
import type { QcReport } from '../qc/rubric.ts';
import { CRITERIA, MAX_ROUNDS } from '../qc/rubric.ts';
import type { QcFile, QcIndexEntry } from '../qc/store.ts';

const LABEL: Record<QcReport['verdict'], string> = {
  pass: 'Passed',
  revise: 'Sent back for fixes',
  handed_over: 'Over to you',
};
const ICON: Record<QcReport['verdict'], string> = { pass: '✓', revise: '↻', handed_over: '⚑' };

let index: Record<string, QcIndexEntry> = {};
let open = (() => { try { return localStorage.getItem('studio.qcOpen') === '1'; } catch { return false; } })();
let shown = '';

const esc = (s: string) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!);

async function getJson<T>(url: string): Promise<T | null> {
  try {
    const r = await fetch(`${url}?t=${Date.now()}`, { cache: 'no-store' });
    if (!r.ok) return null;
    return (await r.json()) as T;
  } catch {
    return null;
  }
}

async function loadIndex() {
  index = (await getJson<Record<string, QcIndexEntry>>('/structures/qc-index.json')) ?? {};
  decorateList();
}

/** Latest version of a structure, read from its list entry ("v3 · 7×8×9 · ..."). */
function latestInList(name: string): number | null {
  const sub = document.querySelector(`#list li[data-name="${name}"] .sub`);
  const m = sub && /^v(\d+)/.exec(sub.textContent ?? '');
  return m ? Number(m[1]) : null;
}

function decorateList() {
  for (const li of document.querySelectorAll<HTMLLIElement>('#list li[data-name]')) {
    const name = li.dataset.name!;
    const e = index[name];
    const nameEl = li.querySelector('.name');
    if (!nameEl) continue;
    let dot = nameEl.querySelector<HTMLElement>('.qc-dot');
    const latest = latestInList(name);
    const current = e && latest === e.version;
    const cls = !e ? '' : current ? e.verdict : 'stale';
    if (!cls) {
      dot?.remove();
      continue;
    }
    if (!dot) {
      dot = document.createElement('span');
      nameEl.appendChild(dot);
    }
    const title = current
      ? `Quality check: ${LABEL[e.verdict].toLowerCase()} (${e.score}/100)`
      : `Quality check: v${e.version} ${LABEL[e.verdict].toLowerCase()}; v${latest} not checked yet`;
    if (dot.className !== `qc-dot ${cls}` || dot.title !== title) {
      dot.className = `qc-dot ${cls}`;
      dot.title = title;
    }
  }
}

function panel(): HTMLElement | null {
  let el = document.getElementById('qc-panel');
  if (el) return el;
  // under the review buttons and notes, above "Versions": Approve / Reject stay where they always were
  const anchor = document.querySelector('#review-body h3');
  if (!anchor) return null;
  el = document.createElement('section');
  el.id = 'qc-panel';
  anchor.before(el);
  el.addEventListener('click', (ev) => {
    if (!(ev.target as HTMLElement).closest('.qc-head')) return;
    open = !open;
    try { localStorage.setItem('studio.qcOpen', open ? '1' : ''); } catch { /* private window */ }
    el!.classList.toggle('open', open);
  });
  return el;
}

async function showFor(name: string, version: number) {
  const el = panel();
  if (!el) return;
  const key = `${name}@${version}`;
  shown = key;
  const file = await getJson<QcFile>(`/structures/${encodeURIComponent(name)}/qc.json`);
  if (shown !== key) return; // the user picked another structure meanwhile
  const reports = file?.reports ?? {};
  const r = reports[String(version)];
  if (!r) {
    const older = Object.values(reports).filter((x) => x.version < version).sort((a, b) => b.version - a.version)[0];
    el.className = `qc ${open ? 'open' : ''}`;
    el.innerHTML = `<button class="qc-head"><span>Quality check</span><span class="qc-badge none">Not checked yet</span></button>
      <div class="qc-body muted">${older ? `v${older.version} was ${LABEL[older.verdict].toLowerCase()} (${older.score}/100). ` : ''}The reviewer checks new versions automatically, about once an hour.</div>`;
    return;
  }
  el.className = `qc ${r.verdict} ${open ? 'open' : ''}`;
  const fails = r.auto.issues.filter((i) => i.level !== 'info');
  const bars = CRITERIA.map((c) => {
    const s = r.visual.scores[c.id] ?? 0;
    const note = r.visual.notes?.[c.id];
    return `<div class="qc-crit" title="${esc(c.look + (note ? `\n\nReviewer: ${note}` : ''))}"><span>${esc(c.label)}</span><i class="s${s}"></i></div>`;
  }).join('');
  el.innerHTML = `
    <button class="qc-head"><span>Quality check</span>
      <span class="qc-badge">${ICON[r.verdict]} ${LABEL[r.verdict]} · ${r.score}</span></button>
    <div class="qc-body">
      <p>${esc(r.visual.summary)}</p>
      ${r.visual.problems.length ? `<div class="qc-sub">${r.verdict === 'pass' ? 'Could still be better' : r.verdict === 'revise' ? 'Asked to fix' : 'Still wrong after ' + MAX_ROUNDS + ' rounds'}</div>
        <ul>${r.visual.problems.map((p) => `<li>${esc(p)}</li>`).join('')}</ul>` : ''}
      <div class="qc-grid">${bars}</div>
      ${fails.length ? `<div class="qc-sub">Automatic checks</div><ul class="qc-auto">${fails.map((i) => `<li class="${i.level}">${esc(i.message)}</li>`).join('')}</ul>` : ''}
      <div class="muted small">Round ${r.round} of ${MAX_ROUNDS} · ${esc(r.by)} · ${new Date(r.checkedAt).toLocaleString()}</div>
    </div>`;
}

function follow() {
  const title = document.getElementById('r-title');
  if (!title) return;
  const update = () => {
    const m = /^(.+) · v(\d+)$/.exec(title.textContent ?? '');
    if (m) void showFor(m[1], Number(m[2]));
  };
  new MutationObserver(update).observe(title, { childList: true, characterData: true, subtree: true });
  update();
  const list = document.getElementById('list');
  if (list) new MutationObserver(() => decorateList()).observe(list, { childList: true });
}

void loadIndex();
follow();
// The page pulls new structures from GitHub; pick up new reports the same way.
window.setInterval(() => {
  if (document.hidden) return;
  void loadIndex();
  const title = document.getElementById('r-title')?.textContent ?? '';
  const m = /^(.+) · v(\d+)$/.exec(title);
  if (m) void showFor(m[1], Number(m[2]));
}, 30_000);

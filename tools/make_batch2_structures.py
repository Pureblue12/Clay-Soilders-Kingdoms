"""Generates the batch-2 kingdom structures (tier 2, look A) into the Structure Studio for review.

  python3 tools/make_batch2_structures.py --check            # sizes and block counts per level, nothing written
  python3 tools/make_batch2_structures.py --render [names]   # render every level to build/batch2/render/ (look at them)
  python3 tools/make_batch2_structures.py --studio [names]   # save every level into the Studio as a pending version

  STRUCTURE_PKG=revise1 python3 tools/make_batch2_structures.py ...   # another package of modules (revisions of
                                                                      # batch 1 from Carlton's notes: tools/revise1/)
An entry may also carry changes='what changed and why' (the Studio's "Changes" line for a new version) and
max_height (default 9; landmarks such as the keep may go higher, they need scaffold in game) and ground_layer=0
for flush ground-level pieces (gardens, plazas, flower beds): layer 0 replaces the top ground block (meta.groundLayer;
the mod's builders don't do that yet, so such structures need that support before they ship).

Unlike batch 1, this never writes into the mod: a structure reaches the mod only after Carlton approves it in the
Studio (the promote step). The building helpers, palettes and buildability rules are batch 1's
(tools/make_batch1_structures.py); see its docstring for the conventions (x = width, z = 0 is the front, levels only
add, L1 uses only KNOWN-OK blocks, BROKEN things go to decorate()).

Each structure lives in its own module in tools/batch2/ and lists itself in that module's STRUCTURES:

  STRUCTURES = [dict(path='watchtower', name='Watchtower', tier=2, zone='wall', role='defense', group='watchtower',
                     terrain='ground_level', door=None, make=watchtower, description='one line for the reviewer')]
"""
from datetime import datetime, timezone
import importlib, json, os, pkgutil, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from make_batch1_structures import Structure, PALETTES, KNOWN_OK, BROKEN  # noqa: E402,F401

REPO = os.path.dirname(HERE)
STUDIO = os.path.join(REPO, 'studio')
PKG = os.environ.get('STRUCTURE_PKG', 'batch2')
OUT = os.path.join(REPO, 'build', PKG)

# Look A, tier 2: batch 1's palette plus stone-hall and palisade roles. Every value here is KNOWN-OK (probe, D61).
PALETTES['oak_a2'] = {
    **PALETTES['oak_a'],
    'hall_stone': 'stone_bricks', 'hall_stairs': 'stone_brick_stairs',
    'roof_stairs': 'spruce_stairs', 'roof_slab': 'spruce_slab',          # tier 2 roofs are spruce (keep 2 brief)
    'stake': 'spruce_log', 'stake_top': 'spruce_fence', 'walk': 'spruce_slab', 'post': 'oak_fence',
    'plank2': 'spruce_planks', 'dark': 'dark_oak_planks',
}
assert all(v in KNOWN_OK for v in PALETTES['oak_a2'].values()), 'oak_a2 must only use KNOWN-OK blocks'

# Single-character palette keys for Studio files ('.' is air).
KEYS = '#ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@$%&*+=-~^!?<>/|:;,_(){}[]'
KEYS += 'ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ'   # big sites (mine 3) need more


def discover():
    """Every STRUCTURES entry of every module in tools/<PKG>/, in module order."""
    pkg = importlib.import_module(PKG)
    entries = []
    for mod in sorted(m.name for m in pkgutil.iter_modules(pkg.__path__)):
        entries += importlib.import_module(f'{PKG}.{mod}').STRUCTURES
    names = [e['path'] for e in entries]
    assert len(names) == len(set(names)), f'duplicate structure paths: {names}'
    return entries


def build(entry):
    s = entry['make']()
    levels = s.levels()
    assert levels == list(range(1, len(levels) + 1)), f"{entry['path']}: levels {levels}"
    limit = entry.get('max_height', 9)   # landmarks may go higher (STYLE.md: up to 28); they need scaffold in game
    assert s.size[1] <= limit, f"{entry['path']}: {s.size[1]} tall, more than {limit} (needs scaffold, not confirmed in game)"
    return s


FULL = ('_planks', '_log', '_wood', 'cobblestone', 'stone_bricks', 'bricks', 'terracotta', 'hay_block', 'barrel',
        '_wool', 'stone', 'dirt', 'gravel', 'crafting_table', 'furnace', 'smoker', 'glass')
SIDES = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}


def family(name):
    """'fence', 'pane' or 'wall' for blocks that join their neighbours, else None."""
    if name.endswith('_fence') and not name.startswith('nether_brick'):
        return 'fence'
    if name.endswith('_pane') or name == 'iron_bars':
        return 'pane'
    if name.endswith('_wall'):
        return 'wall'
    return None


def join_connections(blocks):
    """Fills in north/east/south/west (and a wall's up) on fences, panes and walls that were placed without them,
    the way the game joins them: fences to fences and full blocks, panes and walls to each other and full blocks.
    Without this the Studio draws lone posts (Carlton's note on the stables: "Fences are broken")."""
    out = dict(blocks)
    for (x, y, z), (name, props) in blocks.items():
        fam = family(name)
        if not fam or any(k in props for k in SIDES):
            continue
        joined = {}
        for side, (dx, dz) in SIDES.items():
            o = blocks.get((x + dx, y, z + dz))
            ofam = family(o[0]) if o else None
            full = o is not None and o[0].endswith(FULL) and not o[0].endswith(('_slab', '_stairs'))
            joined[side] = bool(o) and (full or (ofam == 'fence' if fam == 'fence' else ofam in ('pane', 'wall')))
        p = dict(props)
        if fam == 'wall':
            straight = joined['north'] == joined['south'] and joined['east'] == joined['west'] and \
                joined['north'] != joined['east']
            p.update({k: 'low' if v else 'none' for k, v in joined.items()}, up='false' if straight else 'true')
        else:
            p.update({k: str(v).lower() for k, v in joined.items()})
        p.setdefault('waterlogged', 'false')
        out[(x, y, z)] = (name, p)
    return out


# Decorations the Studio shows as real blocks, so the reviewer sees them (only in the Studio file: builders never place
# them, the decoration step does; a promote step to the mod must leave them out of the NBT).
STUDIO_DECORATION_BLOCKS = {'water': ('water', {'level': '0'}), 'lily_pad': ('lily_pad', {})}


def studio_file(entry, s, level):
    """The Studio JSON of one level (layers[y][z] strings, x along the string)."""
    blocks = join_connections(s.level_blocks(level))
    shown = []                                     # meta.decorationBlocks: [x, y, z, kind] of every block shown here
    for d in (d for lv in range(1, level + 1) for d in s.decorations[lv]):
        if d['kind'] in STUDIO_DECORATION_BLOCKS and tuple(d['pos']) not in blocks:
            blocks[tuple(d['pos'])] = STUDIO_DECORATION_BLOCKS[d['kind']]
            shown.append([*d['pos'], d['kind']])
    palette, keys = {'.': 'minecraft:air'}, {}
    for name, props in sorted(set((b[0], tuple(sorted(b[1].items()))) for b in blocks.values())):
        state = 'minecraft:' + name + ('[' + ','.join(f'{k}={v}' for k, v in props) + ']' if props else '')
        k = KEYS[len(keys)]
        keys[(name, props)] = k
        palette[k] = state
    sx, sy, sz = s.size
    layers = []
    for y in range(sy):
        rows = []
        for z in range(sz):
            row = ''
            for x in range(sx):
                b = blocks.get((x, y, z))
                row += keys[(b[0], tuple(sorted(b[1].items())))] if b else '.'
            rows.append(row)
        layers.append(rows)
    levels = s.levels()
    what = 'shell and fittings (what soldiers build first)' if level == 1 else f'L{level - 1} plus flourish {level}'
    unknown = sorted({b[0] for b in s.level_blocks(level).values() if b[0] not in KNOWN_OK})
    desc = f"{entry['name']}, level {level} of {len(levels)}: {what}. {entry['description']}"
    if unknown:
        desc += f" Not yet builder-tested: {', '.join(unknown)}."
    deco = [d for lv in range(1, level + 1) for d in s.decorations[lv]]
    if deco:
        desc += f" Decoration step adds: {', '.join(sorted({d['kind'] for d in deco}))}."
    meta_extra = {'groundLayer': entry['ground_layer']} if 'ground_layer' in entry else {}
    if shown:
        meta_extra['decorationBlocks'] = shown     # placed by the decoration step, not by builders (QC: not a fail)
    return {
        'format': 'csr-studio-structure/1', 'name': f"{entry['path']}_l{level}", 'version': 1, 'size': list(s.size),
        'meta': {'status': 'pending', 'notes': '', 'author': 'claude', 'created': datetime.now(timezone.utc).isoformat(),
                 'description': desc, 'changes': '', 'parent': None, 'reviewedAt': None,
                 'tags': [PKG, f"tier{entry['tier']}", entry['zone'], entry['role']], **meta_extra},
        'palette': palette, 'layers': layers,
    }


def write_drafts(entry, s):
    os.makedirs(os.path.join(OUT, 'drafts'), exist_ok=True)
    files = []
    for level in s.levels():
        f = os.path.join(OUT, 'drafts', f"{entry['path']}_l{level}.json")
        with open(f, 'w', encoding='utf-8') as fh:
            json.dump(studio_file(entry, s, level), fh, indent=1, ensure_ascii=False)
        files.append(f)
    return files


def studio(*args):
    return subprocess.run(['npm', 'run', '-s', 'studio', '--', *args], cwd=STUDIO, capture_output=True, text=True)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    mode = next((a for a in sys.argv[1:] if a.startswith('--')), '--check')
    entries = [e for e in discover() if not args or e['path'] in args]
    for entry in entries:
        s = build(entry)
        per_level = [len(s.level_blocks(level)) for level in s.levels()]
        print(f"{entry['path']:16} size {s.size} levels {per_level} layers {s.counts()} unknown {s.unknown_blocks()}")
        if mode == '--check':
            continue
        files = write_drafts(entry, s)
        for f in files:
            name = os.path.basename(f)[:-5]
            if mode == '--render':
                r = studio('validate', f)
                print('   ', name, r.stdout.strip().splitlines()[-1] if r.stdout.strip() else r.stderr.strip())
                r = studio('render', '--file', f, '--views', 'iso,front,back,top', '-o', os.path.join(OUT, 'render'))
                if r.returncode:
                    print(r.stdout, r.stderr)
                r = studio('render', '--file', f, '--views', 'iso', '--terrain', 'slope', '-o', os.path.join(OUT, 'render', 'slope'))
                if r.returncode:
                    print(r.stdout, r.stderr)
            elif mode == '--studio':
                exists = os.path.isdir(os.path.join(STUDIO, 'structures', name))
                r = studio('save' if exists else 'create', f, '--author', 'claude',
                           *(['--changes', entry.get('changes', f'regenerated from tools/{PKG}')] if exists else []))
                print('   ', (r.stdout or r.stderr).strip().splitlines()[-1])
    if mode == '--render':
        print(f'renders in {os.path.join(OUT, "render")}')


if __name__ == '__main__':
    main()

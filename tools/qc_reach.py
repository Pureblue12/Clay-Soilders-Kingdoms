"""python3 tools/qc_reach.py studio/structures/<name>/<name>_vN.json ...

Rough soldier reachability for a Studio structure file: flood fill from the ground outside.
Soldiers are half a block tall: a cell is a place to stand when it has no collision and the block below can be stood on.
Moves: to a side neighbour up 1 (jump, needs room above), level, or down up to 3. Ladders/scaffolding climb.
Prints unreached standing places grouped into connected regions (roofs show up too; judge by eye)."""
import json, sys
from collections import deque

PASS = ('air', 'cave_air', 'structure_void', 'water', 'lily_pad', 'torch', 'wall_torch', 'lantern', 'soul_lantern', 'sign',
        'banner', 'button', 'lever', 'rail', 'pressure_plate', 'vine', 'ladder', 'cobweb', 'snow', 'redstone_wire',
        'tripwire', 'sapling', 'mushroom', 'dead_bush', 'fern', 'short_grass', 'tall_grass', 'grass', 'wheat', 'carrots',
        'potatoes', 'beetroots', 'sugar_cane', 'sweet_berry_bush', 'flower', 'poppy', 'dandelion', 'orchid', 'allium',
        'azure_bluet', 'tulip', 'oxeye_daisy', 'cornflower', 'lily_of_the_valley', 'rose_bush', 'lilac', 'peony',
        'sunflower', 'pink_petals', 'carpet', 'moss_carpet', 'chain', 'scaffolding', 'glow_lichen', 'hanging_roots',
        'spore_blossom', 'seagrass', 'kelp', 'end_rod', 'tripwire_hook', 'candle', 'flower_pot', 'potted', 'skull', 'head',
        'large_fern', 'nether_sprouts', 'bush', 'leaf_litter', 'firefly_bush', 'wildflowers', 'item_frame')
CLIMB = ('ladder', 'scaffolding', 'vine')
NO_TOP = ('_fence', '_wall', '_fence_gate', 'iron_bars', '_pane')     # 1.5 tall or too thin to stand on after a jump


def kind(state):
    name = state.split('[')[0].replace('minecraft:', '')
    props = dict(p.split('=') for p in state[len('minecraft:' + name) + 1:-1].split(',')) if '[' in state else {}
    if name.endswith('_door'):
        return 'pass' if not name.startswith('iron') else 'solid', name
    if name.endswith('_fence_gate'):
        return ('pass' if props.get('open') == 'true' else 'solid'), name
    if name.endswith('_trapdoor'):
        return ('pass' if props.get('open') == 'true' else 'low'), name
    if any(name == p or name.endswith('_' + p) or name.startswith(p + '_') or name.endswith(p) for p in PASS):
        return 'pass', name
    return 'solid', name


def main(path):
    s = json.load(open(path))
    sx, sy, sz = s['size']
    pal = {k: kind(v) for k, v in s['palette'].items()}
    L = s['layers']

    def at(x, y, z):
        if y < 0:
            return ('solid', 'ground')
        if not (0 <= x < sx and 0 <= z < sz) or y >= sy:
            return ('pass', 'air')
        return pal[L[y][z][x]]

    def passable(x, y, z):
        return at(x, y, z)[0] == 'pass'

    def stand(x, y, z):
        if not passable(x, y, z):
            return False
        k, n = at(x, y - 1, z)
        if n.endswith(CLIMB) or at(x, y, z)[1].endswith(CLIMB):
            return True
        return k in ('solid', 'low') and not n.endswith(NO_TOP)

    seen = set()
    q = deque()
    for x in range(-1, sx + 1):
        for z in range(-1, sz + 1):
            if not (0 <= x < sx and 0 <= z < sz):
                seen.add((x, 0, z)); q.append((x, 0, z))
    while q:
        x, y, z = q.popleft()
        here = at(x, y, z)[1]
        nxt = []
        if here.endswith(CLIMB) or at(x, y - 1, z)[1].endswith(CLIMB):
            nxt += [(x, y + 1, z), (x, y - 1, z)]
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            X, Z = x + dx, z + dz
            if not (-1 <= X <= sx and -1 <= Z <= sz):
                continue
            if passable(x, y + 1, z) or y + 1 >= sy:
                nxt.append((X, y + 1, Z))
            nxt.append((X, y, Z))
            for d in (1, 2, 3):
                if all(passable(X, y - k, Z) for k in range(0, d)):
                    nxt.append((X, y - d, Z))
        for c in nxt:
            if c not in seen and c[1] >= 0 and c[1] <= sy and stand(*c):
                seen.add(c); q.append(c)
    spots = [(x, y, z) for y in range(sy) for z in range(sz) for x in range(sx) if stand(x, y, z)]
    un = set(c for c in spots if c not in seen)
    print(f'{path}: {len(spots)} standing places, {len(spots) - len(un)} reached, {len(un)} not')
    regions = []
    while un:
        c = un.pop(); comp = [c]; st = [c]
        while st:
            x, y, z = st.pop()
            for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0)):
                n = (x + d[0], y + d[1], z + d[2])
                if n in un:
                    un.remove(n); comp.append(n); st.append(n)
        regions.append(comp)
    for comp in sorted(regions, key=len, reverse=True):
        xs, ys, zs = zip(*comp)
        floors = {}
        for x, y, z in comp:
            n = at(x, y - 1, z)[1]; floors[n] = floors.get(n, 0) + 1
        roofed = sum(1 for x, y, z in comp if any(at(x, yy, z)[0] != 'pass' for yy in range(y + 1, sy)))
        top = ', '.join(f'{k} {v}' for k, v in sorted(floors.items(), key=lambda kv: -kv[1])[:3])
        print(f'  {len(comp):4} places x {min(xs)}..{max(xs)} y {min(ys)}..{max(ys)} z {min(zs)}..{max(zs)}'
              f'  under a roof: {roofed}  standing on: {top}')


for p in sys.argv[1:]:
    main(p)

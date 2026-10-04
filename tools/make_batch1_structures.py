"""Generates the batch-1 kingdom structures (look A) from parameters: structure files per detail level, metadata, and a
decoration sidecar per structure. See docs/STRUCTURES.md (briefs, rules, buildability checklist).

  python3 tools/make_batch1_structures.py            # writes into common/src/main/resources/data/csr/...
  python3 tools/make_batch1_structures.py --check    # only prints sizes and block counts per level

Conventions (same as tools/make_test_structures.py):
  - x = width, y = height, z = depth; local z = 0 is the FRONT (towards the easel); "north" in block states is the front.
  - Layer y = 0 rests on the ground (metadata "ground_level": 0).
  - Every structure has levels: L1 = shell + fittings, L2 = L1 + flourish 2, L3 = L2 + flourish 3. A level only ADDS
    blocks to the one before (checked here, and by the GameTests). The box (size) is the same for every level.
  - Shell and fittings use only KNOWN-OK blocks (buildability checklist + probe, D61); flourish may use UNKNOWN blocks
    (listed in the sidecar); BROKEN things are never placed: they go into the sidecar for the decoration step.

To change a structure, change its parameters (e.g. Cottage(width=5, depth=7, wall_height=3, roof_pitch=1)) or swap
its palette (PALETTES).
"""
import gzip, json, os, struct, sys

ROOT = os.path.join(os.path.dirname(__file__), '..', 'common', 'src', 'main', 'resources', 'data', 'csr')
STRUCTURE_DIR = os.path.join(ROOT, 'structure', 'kingdom', 'batch1')
META_DIR = os.path.join(ROOT, 'csr', 'structures')
DECORATION_DIR = os.path.join(ROOT, 'csr', 'decoration')

# Buildability checklist (docs/STRUCTURES.md): KNOWN-OK = a GameTest placed it (Small House, placing, door, scaffold
# tests, and the batch-1 probe BuildabilityProbeGameTests, D61).
KNOWN_OK = {
    'cobblestone', 'cobblestone_slab', 'oak_planks', 'oak_slab', 'oak_stairs', 'oak_log', 'oak_wood', 'stripped_oak_log',
    'stripped_oak_wood', 'spruce_log', 'oak_door', 'spruce_door', 'spruce_trapdoor', 'blue_wool', 'cyan_wool',
    'green_wool', 'light_blue_wool', 'glowstone', 'torch', 'wall_torch', 'oak_wall_sign', 'scaffolding',
    # probe (D61)
    'chest', 'barrel', 'red_bed', 'oak_fence', 'spruce_fence', 'red_carpet', 'white_carpet', 'brown_carpet', 'furnace',
    'smoker', 'crafting_table', 'composter', 'grindstone', 'glass_pane', 'hay_block', 'oak_pressure_plate',
    'oak_trapdoor', 'cobblestone_wall', 'cobblestone_stairs', 'mossy_cobblestone', 'stone_bricks', 'bricks',
    'white_terracotta', 'dark_oak_planks', 'dark_oak_log', 'dark_oak_stairs', 'spruce_planks', 'spruce_stairs',
    'spruce_slab', 'stripped_spruce_log', 'coarse_dirt', 'dirt', 'gravel', 'flower_pot', 'campfire', 'lantern',
    'oak_leaves', 'chain', 'stone_brick_stairs', 'oak_button', 'poppy',
}
# Never placed by builders (no item, block data, or entities): the decoration step adds these (D54, D56).
BROKEN = {'water', 'lava', 'fire', 'farmland', 'dirt_path', 'water_cauldron'}

# Palettes: role -> block id (without "minecraft:"). Swap a palette to re-skin a structure.
PALETTES = {
    'oak_a': {  # look A, tier 1: timber hamlet (D49)
        'base': 'cobblestone', 'stone': 'cobblestone', 'stone_slab': 'cobblestone_slab', 'stone_wall': 'cobblestone_wall',
        'stone_stairs': 'cobblestone_stairs', 'moss': 'mossy_cobblestone',
        'wall': 'oak_planks', 'floor': 'oak_planks', 'frame': 'oak_log', 'stripped': 'stripped_oak_log',
        'roof_stairs': 'oak_stairs', 'roof_slab': 'oak_slab', 'door': 'oak_door', 'trapdoor': 'spruce_trapdoor',
        'shelf': 'oak_trapdoor', 'fence': 'oak_fence', 'window': 'glass_pane', 'plate': 'oak_pressure_plate',
        'chair': 'oak_stairs', 'bed': 'red_bed', 'rug': 'red_carpet', 'rug2': 'brown_carpet', 'cloth': 'cyan_wool',
        'cloth2': 'blue_wool', 'chimney': 'bricks', 'dark_frame': 'dark_oak_log', 'dark_stairs': 'dark_oak_stairs',
        'shed_wall': 'spruce_planks', 'shed_roof': 'spruce_slab', 'shed_post': 'spruce_log', 'path': 'coarse_dirt',
        'soil': 'coarse_dirt', 'flower': 'poppy', 'hearth': 'furnace',
    },
}


# ---------------------------------------------------------------- NBT writing

def _s(x): b = x.encode(); return struct.pack('>H', len(b)) + b
def _named(t, name, payload): return bytes([t]) + _s(name) + payload
def _i(v): return struct.pack('>i', v)
def _lst_int(vals): return bytes([3]) + _i(len(vals)) + b''.join(_i(v) for v in vals)
def _compound(entries): return b''.join(entries) + b'\x00'


def _state(name, props):
    entries = [_named(8, 'Name', _s('minecraft:' + name))]
    if props:
        entries.append(_named(10, 'Properties', _compound([_named(8, k, _s(str(v))) for k, v in sorted(props.items())])))
    return _compound(entries)


def write_nbt(path, size, blocks):
    """blocks: dict (x, y, z) -> (name, props); every other position in the box is air."""
    palette, keys, out = [], {}, []
    for y in range(size[1]):
        for z in range(size[2]):
            for x in range(size[0]):
                name, props = blocks.get((x, y, z), ('air', None))
                key = (name, tuple(sorted((props or {}).items())))
                if key not in keys:
                    keys[key] = len(palette)
                    palette.append(_state(name, props))
                out.append(_compound([_named(9, 'pos', _lst_int([x, y, z])), _named(3, 'state', _i(keys[key]))]))
    root = _compound([
        _named(3, 'DataVersion', _i(3955)),
        _named(9, 'size', _lst_int(list(size))),
        _named(9, 'palette', bytes([10]) + _i(len(palette)) + b''.join(palette)),
        _named(9, 'blocks', bytes([10]) + _i(len(out)) + b''.join(out)),
        _named(9, 'entities', bytes([10]) + _i(0)),
    ])
    with open(path, 'wb') as raw, gzip.GzipFile(fileobj=raw, mode='wb', mtime=0) as f:   # mtime 0: same input, same bytes
        f.write(_named(10, '', root))


# ---------------------------------------------------------------- building helpers

class Structure:
    """Collects blocks per detail layer. Positions are local (x, y, z); level 1 = shell + fittings."""

    def __init__(self, name, size, palette='oak_a'):
        self.name, self.size, self.palette_name = name, size, palette
        self.p = PALETTES[palette]
        self.blocks = {}       # pos -> (name, props, level)
        self.layer_of = {}     # pos -> 'shell' | 'fittings' | 'flourish2' | 'flourish3'
        self.decorations = {1: [], 2: [], 3: []}
        self.level = 1
        self.layer = 'shell'

    def at(self, level, layer):
        self.level, self.layer = level, layer
        return self

    def put(self, x, y, z, block, **props):
        """Places a block (a palette role or a block id) in the current level and layer."""
        name = self.p.get(block, block)
        pos = (x, y, z)
        assert 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2], f'{self.name}: {pos} outside {self.size}'
        assert name not in BROKEN, f'{self.name}: {name} is BROKEN, use decorate()'
        if pos in self.blocks:
            old = self.blocks[pos]
            assert old[2] == self.level, f'{self.name}: level {self.level} replaces level {old[2]} block at {pos} (levels only add)'
        if self.level == 1:
            assert name in KNOWN_OK, f'{self.name}: {name} in shell/fittings is not KNOWN-OK'
        self.blocks[pos] = (name, {k: str(v).lower() for k, v in props.items()}, self.level)
        self.layer_of[pos] = self.layer
        return pos

    def fill(self, x0, y0, z0, x1, y1, z1, block, **props):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.put(x, y, z, block, **props)

    def ring(self, x0, z0, x1, z1, y, block, **props):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    self.put(x, y, z, block, **props)

    def add(self, x, y, z, block, **props):
        """Like put, but leaves a position that already has a block (from this or an earlier level) alone."""
        if (x, y, z) not in self.blocks:
            self.put(x, y, z, block, **props)

    def add_ring(self, x0, z0, x1, z1, y, block, **props):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    self.add(x, y, z, block, **props)

    def remove(self, x, y, z):
        """Takes a block out again while building the same level (openings)."""
        if (x, y, z) in self.blocks:
            assert self.blocks[(x, y, z)][2] == self.level
            del self.blocks[(x, y, z)]
            del self.layer_of[(x, y, z)]

    def door(self, x, y, z, facing='north', block='door', hinge='left'):
        for half, dy in (('lower', 0), ('upper', 1)):
            self.put(x, y + dy, z, block, facing=facing, half=half, hinge=hinge, open='false', powered='false')

    def bed(self, x, y, z, facing):
        """Foot at (x, y, z), head one block in the facing direction."""
        dx, dz = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}[facing]
        self.put(x, y, z, 'bed', facing=facing, part='foot', occupied='false')
        self.put(x + dx, y, z + dz, 'bed', facing=facing, part='head', occupied='false')

    def decorate(self, x, y, z, kind, **data):
        """Something the decoration step adds after the build (never placed by builders)."""
        self.decorations[self.level].append({'pos': [x, y, z], 'kind': kind, **data})

    # ---- results

    def level_blocks(self, level):
        return {pos: (b[0], b[1]) for pos, b in self.blocks.items() if b[2] <= level}

    def levels(self):
        return sorted({b[2] for b in self.blocks.values()})

    def counts(self):
        layers = {}
        for pos, layer in self.layer_of.items():
            layers[layer] = layers.get(layer, 0) + 1
        return layers

    def unknown_blocks(self):
        return sorted({b[0] for b in self.blocks.values() if b[0] not in KNOWN_OK})


# ---------------------------------------------------------------- the 12 batch-1 structures (look A)

def keep1(width=9, depth=9, wall_height=5, turret=True):
    """Keep, tier 1: a cobblestone tower-house on a stone base layer (D33), 9 tall, door at the front center."""
    s = Structure('keep_1', (width, 9, depth))
    w, d = width - 1, depth - 1
    mid = width // 2
    roof_y = wall_height + 1          # 6
    s.at(1, 'shell')
    s.fill(0, 0, 0, w, 0, d, 'base')                          # stone base layer = floor
    for y in range(1, wall_height + 1):
        s.ring(0, 0, w, d, y, 'stone')
    s.fill(0, roof_y, 0, w, roof_y, d, 'stone')              # flat roof
    for x in range(0, width):                                  # crenellated parapet
        for z in range(0, depth):
            edge = x in (0, w) or z in (0, d)
            if edge and (x + z) % 2 == 0:
                s.put(x, roof_y + 1, z, 'stone')
    if turret:                                                 # corner turret (back right), 2 higher
        for y in (roof_y + 1, roof_y + 2):
            s.ring(w - 2, d - 2, w, d, y, 'stone')
    s.remove(mid, 1, 0); s.remove(mid, 2, 0)                   # doorway
    s.door(mid, 1, 0, 'north')
    for x, z in ((0, 3), (0, 5), (w, 3), (w, 5), (2, d), (6, d)):  # arrow-slit windows
        s.remove(x, 3, z)
        s.put(x, 3, z, 'window')
    # stair up to the roof along the right wall: (w-1, 1..5, d-2..2), roof hole above the top steps
    sx = w - 1
    for i, y in enumerate(range(1, roof_y + 1)):
        z = d - 2 - i
        if y == roof_y:
            s.remove(sx, y, z)
        s.put(sx, y, z, 'stone_stairs', facing='north', half='bottom', shape='straight', waterlogged='false')
    s.remove(sx, roof_y, d - 2 - (roof_y - 2))                 # headroom over the step below the roof
    s.at(1, 'fittings')
    s.fill(mid - 1, 1, d - 2, mid + 1, 1, d - 1, 'floor')    # dais
    s.put(mid, 2, d - 1, 'chair', facing='south', half='bottom', shape='straight', waterlogged='false')  # throne
    # storeroom by the door (front left): barrels and chests behind a fence rail
    s.put(1, 1, 1, 'barrel', facing='up', open='false'); s.put(1, 1, 2, 'barrel', facing='up', open='false')
    s.put(2, 1, 1, 'chest', facing='east', type='single', waterlogged='false')
    s.put(1, 2, 1, 'chest', facing='east', type='single', waterlogged='false')
    s.put(3, 1, 2, 'barrel', facing='up', open='false')       # the storeroom's end (no fences indoors: soldiers stuck on top)
    # king's quarters (back left): bed, chest, rug
    s.bed(1, 1, d - 3, 'south')
    s.put(2, 1, d - 1, 'chest', facing='west', type='single', waterlogged='false')
    s.put(2, 1, d - 2, 'rug')
    s.put(1, 1, 4, 'hearth', facing='east', lit='false')       # hearth on the left wall
    s.put(3, 1, 4, 'roof_slab', type='bottom', waterlogged='false')   # table (a slab: soldiers step on and off it)
    s.put(3, 1, 5, 'chair', facing='north', half='bottom', shape='straight', waterlogged='false')
    for x, z, f in ((1, 2, 'east'), (w - 1, 6, 'west'), (mid - 1, 1, 'south'), (mid + 1, d - 1, 'north')):   # torches on solid wall
        s.put(x, 3, z, 'wall_torch', facing=f)
    s.at(2, 'flourish2')
    for z in range(1, d - 2):                                  # carpet runner from the door to the throne
        s.put(mid, 1, z, 'rug')
    s.put(mid - 1, 2, d - 2, 'lantern', hanging='false', waterlogged='false')     # lanterns on the dais
    s.put(mid + 1, 2, d - 2, 'lantern', hanging='false', waterlogged='false')
    s.put(mid, 4, 1, 'white_wall_banner', facing='south')     # banner inside over the door (plain)
    s.decorate(mid, 4, 1, 'banner_pattern', banner='white_wall_banner', note='kingdom colours')
    for x, z in ((1, 5), (2, 5)):                              # flower pots on the hearth side (plants: decoration step)
        s.put(x, 2, z - 1 if x == 1 else z, 'flower_pot')
        s.decorate(x, 2, z - 1 if x == 1 else z, 'potted_plant', plant='poppy')
    s.put(mid, roof_y + 1, d - 3, 'lantern', hanging='false', waterlogged='false')   # on the roof terrace
    for x in (1, 3, 5):                                        # moss on the roof
        s.put(x, roof_y + 1, 3, 'moss_carpet')
    s.at(3, 'flourish3')
    for y in (roof_y + 1, roof_y + 2):                          # a second turret (back left)
        s.add_ring(0, d - 2, 2, d, y, 'stone')
    s.put(3, 2, 3, 'candle', candles='3', lit='false', waterlogged='false')
    s.put(0, roof_y + 1, 3, 'white_banner', rotation='12')     # banner on the parapet (plain; pattern by the decoration step)
    s.decorate(0, roof_y + 1, 3, 'banner_pattern', banner='white_banner', note='kingdom colours')
    for z in (2, 6):
        s.put(w - 1, 2, z, 'candle', candles='1', lit='false', waterlogged='false')
    for x in range(3, w - 2):
        s.add(x, roof_y + 1, d - 1, 'moss_carpet')
    s.put(2, 1, d - 3, 'brown_carpet')
    return s


def hovel(width=3, depth=4):
    """Hovel: a lean-to of oak slabs on log posts, open at the front, bed and chest inside."""
    s = Structure('hovel', (width + 2, 3, depth + 1))
    x0, x1, z0, z1 = 1, width, 1, depth
    s.at(1, 'shell')
    for x in (x0, x1):
        s.put(x, 0, z0, 'frame'); s.put(x, 1, z0, 'frame')     # tall front posts
        s.put(x, 0, z1, 'frame')                                # short back posts
    for x in range(x0 + 1, x1):                                  # back wall (two high)
        s.put(x, 0, z1, 'wall')
    for x in range(x0, x1 + 1):
        s.put(x, 1, z1, 'wall')
        for z in range(z0, z1 + 1):
            s.put(x, 2, z, 'roof_slab', type='bottom', waterlogged='false')
    for z in range(z0 + 1, z1):                                  # side walls (one high)
        s.put(x0, 0, z, 'wall'); s.put(x1, 0, z, 'wall')
    s.at(1, 'fittings')
    s.bed(x0 + 1, 0, z1 - 2, 'south')
    s.put(x0 + 1, 0, z0, 'chest', facing='north', type='single', waterlogged='false')
    s.put(x0, 1, z0 + 1, 'wall_torch', facing='east')
    s.at(2, 'flourish2')
    s.put(x0 + 1, 0, 0, 'campfire', facing='north', lit='false', signal_fire='false', waterlogged='false')
    s.put(0, 0, 0, 'fence'); s.put(0, 1, 0, 'lantern', hanging='false', waterlogged='false')
    s.put(0, 0, z0 + 1, 'stripped', axis='z'); s.put(0, 0, z0 + 2, 'stripped', axis='z')
    s.at(3, 'flourish3')
    s.put(x1 + 1, 0, z0 + 1, 'flower_pot'); s.decorate(x1 + 1, 0, z0 + 1, 'potted_plant', plant='poppy')
    s.put(x1 + 1, 0, z1, 'stripped', axis='z'); s.put(x1 + 1, 1, z1, 'stripped', axis='z')
    s.put(x1 + 1, 0, z1 - 1, 'stripped', axis='z')
    s.put(x1 + 1, 0, z0 + 2, 'stripped', axis='z')
    return s


def cottage(width=5, depth=7, wall_height=3, margin=1):
    """Cottage: cobblestone base course, plank walls with log corners, oak stair gable roof; door at the front."""
    W, D = width + 2 * margin, depth + 2 * margin
    s = Structure('cottage', (W, 1 + wall_height + 3 + 1, D))
    x0, x1, z0, z1 = margin, margin + width - 1, margin, margin + depth - 1
    mid = x0 + width // 2
    top = wall_height                                           # last wall layer (3)
    s.at(1, 'shell')
    s.ring(x0, z0, x1, z1, 0, 'base')                          # base course
    s.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, 'floor')      # floor
    for y in range(1, top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    corner = x in (x0, x1) and z in (z0, z1)
                    s.put(x, y, z, 'frame' if corner else 'wall')
    s.remove(mid, 1, z0); s.remove(mid, 2, z0)
    s.door(mid, 1, z0, 'north')
    windows = [(x0, 2, z0 + 2), (x1, 2, z0 + 2), (x0, 2, z1 - 2), (x1, 2, z1 - 2)]
    for x, y, z in windows:
        s.remove(x, y, z)
    # gable roof along the depth: stairs rising from both long sides, gable ends filled with planks
    for i in range(width // 2 + 1):
        y = top + 1 + i
        left, right = x0 + i, x1 - i
        for z in range(z0, z1 + 1):
            if left < right:
                s.put(left, y, z, 'roof_stairs', facing='east', half='bottom', shape='straight', waterlogged='false')
                s.put(right, y, z, 'roof_stairs', facing='west', half='bottom', shape='straight', waterlogged='false')
            elif left == right:
                s.put(left, y, z, 'roof_slab', type='bottom', waterlogged='false')
        for x in range(left + 1, right):
            for z in (z0, z1):
                s.put(x, y, z, 'wall')
    s.at(1, 'fittings')
    for x, y, z in windows:
        s.put(x, y, z, 'window')
    s.bed(x0 + 1, 1, z1 - 2, 'south'); s.bed(x1 - 1, 1, z1 - 2, 'south')
    s.put(mid, 1, z1 - 1, 'chest', facing='north', type='single', waterlogged='false')
    s.put(x1 - 1, 1, z0 + 2, 'roof_slab', type='bottom', waterlogged='false')     # table (a slab, see keep1)
    s.put(x1 - 1, 1, z0 + 3, 'chair', facing='south', half='bottom', shape='straight', waterlogged='false')
    s.put(x0 + 1, 1, z0 + 2, 'hearth', facing='east', lit='false')
    s.put(x0 + 1, 2, z0 + 3, 'shelf', facing='east', half='top', open='false', powered='false', waterlogged='false')
    s.put(x0 + 1, 2, z0 + 4, 'shelf', facing='east', half='top', open='false', powered='false', waterlogged='false')
    s.put(mid, 1, z0 + 2, 'rug'); s.put(mid, 1, z0 + 3, 'rug')
    s.put(mid, 3, z0 + 1, 'wall_torch', facing='south'); s.put(mid, 3, z1 - 1, 'wall_torch', facing='north')
    s.at(2, 'flourish2')
    for x, y, z in windows:                                    # flower beds on the ground under the windows
        out = x - 1 if x == x0 else x + 1
        s.put(out, 0, z, 'soil'); s.put(out, 1, z, 'flower')
        s.put(out, 0, z + 1, 'soil'); s.put(out, 1, z + 1, 'flower')
    chim_x, chim_z = x0 + 1, z1 + 1                              # chimney behind the back wall
    for y in range(0, top + 4):
        s.put(chim_x, y, chim_z, 'chimney')
    s.put(chim_x, top + 4, chim_z, 'campfire', facing='north', lit='false', signal_fire='false', waterlogged='false')
    s.at(3, 'flourish3')
    s.put(mid + 1, 0, 0, 'fence'); s.put(mid + 1, 1, 0, 'lantern', hanging='false', waterlogged='false')   # lamp by the door
    for x in (x0, x1):                                           # front garden: fence posts and flowers
        s.put(x, 0, 0, 'fence')
    s.put(mid - 1, 0, 0, 'soil'); s.put(mid - 1, 1, 0, 'flower')
    for z in (z0, z0 + 1, z1):                                   # moss at the foot of the side walls
        s.put(0, 0, z, 'moss_carpet'); s.put(W - 1, 0, z, 'moss_carpet')
    # dormer on the left roof slope: a little gable of dark oak stairs over the slope
    s.put(x0 + 1, top + 3, z0 + 3, 'dark_stairs', facing='east', half='bottom', shape='straight', waterlogged='false')
    s.put(x0 + 1, top + 3, z0 + 2, 'dark_stairs', facing='south', half='bottom', shape='straight', waterlogged='false')
    s.put(x0 + 1, top + 3, z0 + 4, 'dark_stairs', facing='north', half='bottom', shape='straight', waterlogged='false')
    return s


def tent(s, x, z, cloth, depth=3):
    """A small A-frame tent (3 wide, depth deep), opening at the front, a carpet bedroll inside."""
    for dz in range(depth):
        s.put(x, 0, z + dz, cloth); s.put(x + 2, 0, z + dz, cloth); s.put(x + 1, 1, z + dz, cloth)


def visitors_camp(size=9):
    """Visitors' camp (stage 1 of the inn): three tents around a fire pit by the road near the gate."""
    s = Structure('visitors_camp', (size, 4, size))
    c = size // 2
    s.at(1, 'shell')
    tent(s, 0, size - 4, 'cloth', 4); tent(s, size - 3, size - 4, 'cloth2', 4); tent(s, 0, 0, 'cloth2', 4)
    s.ring(c - 1, c - 1, c + 1, c + 1, 0, 'stone_slab', type='bottom', waterlogged='false')    # fire pit ring
    s.remove(c, 0, c - 1)
    for x in (c - 1, c + 1):                                      # log seats
        s.put(x, 0, c - 3, 'stripped', axis='x')
    s.put(c, 0, c + 3, 'stripped', axis='x')
    for z in range(0, c - 1):                                     # path in from the road (front)
        s.put(c, 0, z, 'path')
    s.at(1, 'fittings')
    s.put(c, 0, c, 'campfire', facing='north', lit='false', signal_fire='false', waterlogged='false')
    s.put(c, 0, c - 1, 'path')
    for x, z in ((1, size - 2), (size - 2, size - 2), (1, 2)):
        s.put(x, 0, z, 'rug')
    s.put(1, 0, size - 3, 'rug'); s.put(size - 2, 0, size - 3, 'rug')
    s.put(size - 2, 0, 1, 'chest', facing='west', type='single', waterlogged='false')
    for x in (c - 2, c + 2):                                      # torch posts at the entrance
        s.put(x, 0, 0, 'fence'); s.put(x, 1, 0, 'torch')
    s.at(2, 'flourish2')
    s.put(size - 1, 0, 2, 'fence'); s.put(size - 1, 1, 2, 'fence')      # notice board
    s.put(size - 1, 0, 4, 'fence'); s.put(size - 1, 1, 4, 'fence')
    s.put(size - 1, 1, 3, 'shelf', facing='west', half='top', open='false', powered='false', waterlogged='false')
    s.put(size - 2, 1, 3, 'oak_wall_sign', facing='west', waterlogged='false')
    s.decorate(size - 2, 1, 3, 'sign_text', lines=['Welcome', 'Visitors', 'stay here', ''])
    s.put(0, 0, c, 'fence'); s.put(1, 0, c, 'fence')              # hitching post between the left tents
    s.put(2, 0, c, 'hay_block', axis='y')
    for x in (c - 1, c + 1):                                       # lantern posts at the back of the fire
        s.put(x, 0, c + 3, 'fence'); s.put(x, 1, c + 3, 'lantern', hanging='false', waterlogged='false')
    s.at(3, 'flourish3')
    s.put(c + 1, 0, 0, 'white_banner', rotation='0')
    s.decorate(c + 1, 0, 0, 'banner_pattern', banner='white_banner', note='kingdom colours')
    s.put(c - 1, 0, 0, 'oak_log'); s.put(c - 1, 1, 0, 'oak_log')        # signpost
    s.put(c - 1, 1, 1, 'oak_wall_sign', facing='south', waterlogged='false')
    s.decorate(c - 1, 1, 1, 'sign_text', lines=['', 'Welcome', '', ''])
    s.put(size - 2, 0, c, 'soil'); s.put(size - 2, 1, c, 'flower')    # flower beds
    s.put(6, 0, 3, 'soil'); s.put(6, 1, 3, 'dandelion')
    s.put(6, 0, 4, 'soil'); s.put(6, 1, 4, 'flower')
    s.put(size - 1, 0, 1, 'fence'); s.put(size - 1, 1, 1, 'lantern', hanging='false', waterlogged='false')
    return s


def farm(width=7, depth=9):
    """Farm: a fenced plot on the existing ground (farmers till it, D55), a gap at the front, an open-fronted tool shed
    in the back right corner. Nothing is stacked into closed pockets: tiny soldiers get stuck in them."""
    s = Structure('farm', (width, 7, depth))
    w, d = width - 1, depth - 1
    mid = width // 2
    s.at(1, 'shell')
    s.ring(0, 0, w, d, 0, 'fence')
    s.remove(mid, 0, 0)                                            # entrance (no fence gate: BROKEN for passage)
    s.at(1, 'fittings')
    for x in (w - 2, w - 1):                                       # shed: two log posts at the back, slab roof, open front
        s.put(x, 0, d - 1, 'shed_post', axis='y'); s.put(x, 1, d - 1, 'shed_post', axis='y')
        for z in (d - 2, d - 1):
            s.put(x, 2, z, 'shed_roof', type='bottom', waterlogged='false')
    s.put(w - 1, 0, d - 2, 'composter', level='0')                 # the workstation, under the roof
    s.put(w - 2, 0, d - 2, 'chest', facing='north', type='single', waterlogged='false')
    s.put(1, 0, d - 1, 'hay_block', axis='y'); s.put(w - 1, 0, 1, 'hay_block', axis='y')
    for x, z in ((0, 0), (w, 0), (0, d), (w, d)):                   # torches on the corner posts
        s.put(x, 1, z, 'torch')
    s.at(2, 'flourish2')
    s.put(1, 0, d // 2, 'fence'); s.put(1, 1, d // 2, 'hay_block', axis='y')   # scarecrow in the field
    s.put(1, 2, d // 2, 'carved_pumpkin', facing='north')
    s.put(0, 1, d // 2, 'lantern', hanging='false', waterlogged='false')
    s.put(w, 1, d // 2, 'lantern', hanging='false', waterlogged='false')
    for x in (1, 2, w - 2):                                          # flower edge inside the front fence
        s.put(x, 0, 1, 'soil'); s.put(x, 1, 1, 'flower')
    s.add(1, 1, d - 1, 'hay_block', axis='y')
    s.at(3, 'flourish3')
    # small windmill at the back left (2 wide, 5 high, a cap) with wool sails; a water channel (decoration step)
    for x in (2, 3):
        for y in range(0, 5):
            s.add(x, y, d - 1, 'wall')
        s.put(x, 5, d - 1, 'roof_stairs', facing='north', half='bottom', shape='straight', waterlogged='false')
        s.put(x, 6, d - 1, 'roof_slab', type='bottom', waterlogged='false')
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (-2, 0), (2, 0), (0, -2), (0, 2), (-1, -1), (1, 1)):   # sails
        x, y = 2 + dx, 4 + dy
        if 0 <= x <= w:
            s.add(x, y, d - 2, 'white_wool' if (dx + dy) % 2 else 'cloth')
    s.add(2, 4, d - 2, 'frame', axis='z')
    for z in range(2, d - 2):
        s.decorate(mid, -1, z, 'water', note='irrigation channel in the tilled field, replaces one ground block')
    return s


def lumber_camp(size=7, aisle=3):
    """Lumber camp: a log shed with a slab roof, a log pile, a chopping block; grindstone as the workstation. The
    column x = aisle stays free from the front to the shed (tiny soldiers can't hop fences or log piles)."""
    s = Structure('lumber_camp', (size, 4, size))
    w = size - 1
    s.at(1, 'shell')
    for x in (w - 3, w):                                           # shed posts (back right, 4 x 3)
        for z in (w - 2, w):
            s.put(x, 0, z, 'frame', axis='y'); s.put(x, 1, z, 'frame', axis='y')
    for x in range(w - 2, w):                                        # back wall
        s.put(x, 0, w, 'shed_wall'); s.put(x, 1, w, 'shed_wall')
    s.put(w, 0, w - 1, 'shed_wall'); s.put(w, 1, w - 1, 'shed_wall')  # side wall
    for x in range(w - 3, w + 1):
        for z in range(w - 2, w + 1):
            s.put(x, 2, z, 'shed_roof', type='bottom', waterlogged='false')
    for x in range(0, 3):                                          # log pile (front left), 3 by 3, stacked
        for z in (1, 2, 3):
            s.put(x, 0, z, 'frame', axis='x')
        for z in (1, 2):
            s.put(x, 1, z, 'frame', axis='x')
    s.put(1, 2, 1, 'frame', axis='x')
    s.at(1, 'fittings')
    s.put(aisle + 2, 0, 1, 'stripped', axis='y')                   # chopping block
    s.put(w - 2, 0, w - 1, 'grindstone', face='floor', facing='south')
    s.put(w - 1, 0, w - 1, 'chest', facing='west', type='single', waterlogged='false')
    s.put(0, 0, 4, 'fence'); s.put(0, 1, 4, 'torch')
    s.at(2, 'flourish2')
    s.put(1, 0, w - 1, 'fence'); s.put(3, 0, w - 1, 'fence')        # sawhorse
    for x in (1, 2, 3):
        s.put(x, 1, w - 1, 'roof_slab', type='bottom', waterlogged='false')
    s.put(w - 2, 1, w - 2, 'lantern', hanging='true', waterlogged='false')
    for x in (aisle + 2, aisle + 3):                                # a stack of cut planks by the shed
        s.put(x, 0, 3, 'shed_wall')
    s.put(aisle + 3, 1, 3, 'shed_wall')
    s.at(3, 'flourish3')
    s.decorate(aisle + 2, 1, 1, 'item_frame', item='minecraft:iron_axe', facing='up', note='axe in the chopping block')
    for z in (0, 1):                                                # saplings along the right edge (replanting)
        s.put(w, 0, z, 'dirt'); s.put(w, 1, z, 'oak_sapling', stage='0')
    s.put(0, 0, 6, 'moss_carpet'); s.put(1, 0, 6, 'moss_carpet')
    for x in (0, 1, 2):                                              # moss on the log pile
        s.put(x, 1, 3, 'moss_carpet')
    s.add(0, 2, 1, 'moss_carpet')
    return s


def well():
    """Well: a cobblestone ring, two wall posts, a slab roof and a chain; water by the decoration step."""
    s = Structure('well', (3, 4, 3))
    s.at(1, 'shell')
    s.ring(0, 0, 2, 2, 0, 'stone')
    for x, z in ((0, 1), (2, 1)):
        s.put(x, 1, z, 'stone_wall'); s.put(x, 2, z, 'stone_wall')
    s.fill(0, 3, 0, 2, 3, 2, 'roof_slab', type='bottom', waterlogged='false')
    s.decorate(1, 0, 1, 'water', note='well water')
    s.at(1, 'fittings')
    s.put(1, 2, 1, 'chain', axis='y')
    s.put(0, 1, 0, 'torch')
    s.at(2, 'flourish2')
    s.put(2, 1, 0, 'flower_pot'); s.decorate(2, 1, 0, 'potted_plant', plant='poppy')
    s.put(0, 1, 2, 'flower_pot'); s.decorate(0, 1, 2, 'potted_plant', plant='dandelion')
    s.put(2, 1, 2, 'lantern', hanging='false', waterlogged='false')
    s.at(3, 'flourish3')
    for x, z in ((1, 0), (1, 2)):
        s.put(x, 1, z, 'moss_carpet')
    return s


def bench():
    """Bench: two oak stairs; level 2 adds trapdoor arms."""
    s = Structure('bench', (4, 1, 1))
    s.at(1, 'shell')
    for x in (1, 2):
        s.put(x, 0, 0, 'chair', facing='south', half='bottom', shape='straight', waterlogged='false')
    s.at(2, 'flourish2')
    s.put(0, 0, 0, 'shelf', facing='west', half='bottom', open='true', powered='false', waterlogged='false')
    s.put(3, 0, 0, 'shelf', facing='east', half='bottom', open='true', powered='false', waterlogged='false')
    return s


def fire_pit():
    """Fire pit: a cobblestone slab ring round an unlit campfire; level 2 adds log seats."""
    s = Structure('fire_pit', (5, 1, 5))
    s.at(1, 'shell')
    s.ring(1, 1, 3, 3, 0, 'stone_slab', type='bottom', waterlogged='false')
    s.at(1, 'fittings')
    s.put(2, 0, 2, 'campfire', facing='north', lit='false', signal_fire='false', waterlogged='false')
    s.at(2, 'flourish2')
    s.put(0, 0, 2, 'stripped', axis='z'); s.put(4, 0, 2, 'stripped', axis='z')
    return s


def signpost():
    """Signpost: a log post; level 2 adds arrow signs on both sides (text by the decoration step)."""
    s = Structure('signpost', (3, 2, 1))
    s.at(1, 'shell')
    s.put(1, 0, 0, 'frame', axis='y'); s.put(1, 1, 0, 'frame', axis='y')
    s.at(2, 'flourish2')
    s.put(0, 1, 0, 'oak_wall_sign', facing='west', waterlogged='false')
    s.put(2, 1, 0, 'oak_wall_sign', facing='east', waterlogged='false')
    s.decorate(0, 1, 0, 'sign_text', lines=['', '<- Keep', '', ''])
    s.decorate(2, 1, 0, 'sign_text', lines=['', 'Fields ->', '', ''])
    return s


def flower_bed(length=3):
    """Flower bed: a strip of soil with flowers (one level: it has no flourish)."""
    s = Structure('flower_bed', (length, 2, 1))
    s.at(1, 'shell')
    for x in range(length):
        s.put(x, 0, 0, 'soil')
    s.at(1, 'fittings')
    for x in range(length):
        s.put(x, 1, 0, 'flower')
    return s


def road(width=3, length=5):
    """Dirt road piece (tier 1): coarse dirt laid on top of the ground; level 2 edge stones, level 3 a lamp post."""
    s = Structure('road', (width + 2, 3, length))
    s.at(1, 'shell')
    s.fill(1, 0, 0, width, 0, length - 1, 'path')
    s.at(2, 'flourish2')
    for z in (0, length - 1):
        s.put(0, 0, z, 'stone_wall'); s.put(width + 1, 0, z, 'stone_wall')
    s.at(3, 'flourish3')
    s.put(0, 0, length // 2, 'fence'); s.put(0, 1, length // 2, 'fence'); s.put(0, 2, length // 2, 'lantern', hanging='false', waterlogged='false')
    s.put(width + 1, 0, length // 2, 'fence')
    return s


# ---------------------------------------------------------------- metadata

# id path, display name, tier, zone, role, variant group, terrain behavior, door side, builder
STRUCTURES = [
    ('keep', 'Keep (tier 1)', 1, 'keep', 'keep', 'keep', 'ground_level', 'front', keep1),
    ('hovel', 'Hovel', 1, 'inner', 'house', 'hovel', 'flexible', 'front', hovel),
    ('cottage', 'Cottage', 1, 'inner', 'house', 'cottage', 'ground_level', 'front', cottage),
    ('visitors_camp', "Visitors' camp", 1, 'inner', 'house', 'inn', 'flexible', 'front', visitors_camp),
    ('farm', 'Farm', 1, 'outer', 'farm', 'farm', 'flexible', 'front', farm),
    ('lumber_camp', 'Lumber camp', 1, 'outer', 'resource', 'lumber_camp', 'flexible', 'front', lumber_camp),
    ('well', 'Well', 1, 'inner', 'utility', 'well', 'ground_level', None, well),
    ('bench', 'Bench', 1, 'inner', 'decoration', 'bench', 'flexible', None, bench),
    ('fire_pit', 'Fire pit', 1, 'inner', 'decoration', 'fire_pit', 'flexible', None, fire_pit),
    ('signpost', 'Signpost', 1, 'inner', 'decoration', 'signpost', 'flexible', None, signpost),
    ('flower_bed', 'Flower bed', 1, 'inner', 'decoration', 'flower_bed', 'flexible', None, flower_bed),
    ('road', 'Road piece (dirt)', 1, 'inner', 'utility', 'road', 'flexible', None, road),
]


def build_all():
    out = []
    for path, name, tier, zone, role, group, terrain, door, make in STRUCTURES:
        s = make()
        levels = s.levels()
        assert levels == list(range(1, len(levels) + 1)), f'{path}: levels {levels}'
        assert s.size[1] <= 9, f'{path}: {s.size[1]} tall, more than 9'
        out.append((path, name, tier, zone, role, group, terrain, door, s))
    return out


def main():
    check = '--check' in sys.argv
    built = build_all()
    summary = {}
    for path, name, tier, zone, role, group, terrain, door, s in built:
        counts = s.counts()
        per_level = [len(s.level_blocks(level)) for level in s.levels()]
        summary[path] = {'size': list(s.size), 'layers': counts, 'levels': per_level, 'unknown': s.unknown_blocks()}
        print(f'{path:14} size {s.size} levels {per_level} layers {counts} unknown {s.unknown_blocks()}')
        if check:
            continue
        os.makedirs(STRUCTURE_DIR, exist_ok=True)
        files = []
        for level in s.levels():
            file_id = f'kingdom/batch1/{path}_l{level}'
            write_nbt(os.path.join(STRUCTURE_DIR, f'{path}_l{level}.nbt'), s.size, s.level_blocks(level))
            files.append('csr:' + file_id)
        meta = {
            'id': 'csr:' + path, 'name': name, 'design': 'ready', 'tier': tier, 'zone': zone, 'role': role,
            'footprint': list(s.size), 'cost': 'auto', 'variant_group': group, 'palette': s.palette_name,
            'structure': files[0], 'levels': files, 'ground_level': 0, 'terrain': terrain, 'batch': 1,
        }
        if door:
            meta['door_side'] = door
        with open(os.path.join(META_DIR, f'{path}.json'), 'w') as f:
            json.dump(meta, f, indent=2)
            f.write('\n')
        os.makedirs(DECORATION_DIR, exist_ok=True)
        sidecar = {
            'structure': 'csr:' + path,
            'note': 'What the decoration step (roadmap 5a) must add after builders finish each level; nothing here is placed by builders.',
            'unknown_blocks_in_flourish': s.unknown_blocks(),
            'levels': {str(level): s.decorations[level] for level in s.levels()},
        }
        with open(os.path.join(DECORATION_DIR, f'{path}.json'), 'w') as f:
            json.dump(sidecar, f, indent=2)
            f.write('\n')
    if not check:
        with open(os.path.join(os.path.dirname(__file__), 'batch1_summary.json'), 'w') as f:
            json.dump(summary, f, indent=2)
            f.write('\n')


if __name__ == '__main__':
    main()

"""Generates the stage 0 test structures (vanilla structure files, DataVersion 3955 = MC 1.21.1):
  common/src/main/resources/data/csr/structure/kingdom/test_hut.nbt   5x3x5 hut: cobblestone walls, oak door, plank roof
  common/src/main/resources/data/csr/structure/kingdom/test_well.nbt  3x2x3 well: cobblestone ring, water, fence posts
  common/src/main/resources/data/csr/structure/kingdom/test_wall.nbt  5x3x1 stone brick wall segment
  common/src/main/resources/data/csr/structure/kingdom/test_camp.nbt  3x1x3 cobblestone pad (stand-in lumber camp)
  common/src/main/resources/data/csr/structure/kingdom/test_field.nbt 5x1x5 cobblestone border, 3x3 dirt (stand-in field)
  common/src/main/resources/data/csr/structure/kingdom/test_garden.nbt  5x3x5 garden with decoration (stage 5a): water basin,
    potted poppy, sign with text, banner with a pattern, item frame with an apple, armor stand
Local z = 0 is the side facing the easel (the front). Air inside a structure is stored, so pasting clears it.

Usage: python3 tools/make_test_structures.py common/src/main/resources/data/csr/structure/kingdom
"""
import gzip, os, struct, sys


def s(x): b = x.encode(); return struct.pack('>H', len(b)) + b
def named(t, name, payload): return bytes([t]) + s(name) + payload
def i(v): return struct.pack('>i', v)
def lst_int(vals): return bytes([3]) + i(len(vals)) + b''.join(i(v) for v in vals)
def compound(entries): return b''.join(entries) + b'\x00'


# Typed values for block entity and entity data: tag('b', 1) is a byte, tag('f', 0.5) a float; ints, strings, dicts
# (compounds) and lists of one kind are written as they are.
def tag(kind, value): return (kind, value)


def type_of(v):
    if isinstance(v, tuple):
        return {'b': 1, 's': 2, 'f': 5, 'd': 6}[v[0]]
    return 3 if isinstance(v, int) else 8 if isinstance(v, str) else 10 if isinstance(v, dict) else 9


def payload(v):
    if isinstance(v, tuple):
        return struct.pack({'b': '>b', 's': '>h', 'f': '>f', 'd': '>d'}[v[0]], v[1])
    if isinstance(v, int):
        return i(v)
    if isinstance(v, str):
        return s(v)
    if isinstance(v, dict):
        return compound([named(type_of(x), k, payload(x)) for k, x in v.items()])
    kind = type_of(v[0]) if v else 0
    return bytes([kind]) + i(len(v)) + b''.join(payload(x) for x in v)


def state(name, props=None):
    entries = [named(8, 'Name', s(name))]
    if props:
        entries.append(named(10, 'Properties', compound([named(8, k, s(v)) for k, v in sorted(props.items())])))
    return compound(entries)


def write(path, size, block_at, block_nbt=None, entities=()):
    """block_at(x, y, z) -> (name, props) for every position in the size box; block_nbt maps (x, y, z) to a block entity's
    data, entities are (pos, block_pos, data) with data as tag(...) values."""
    palette, keys, blocks = [], {}, []
    for y in range(size[1]):
        for z in range(size[2]):
            for x in range(size[0]):
                name, props = block_at(x, y, z)
                key = (name, tuple(sorted((props or {}).items())))
                if key not in keys:
                    keys[key] = len(palette)
                    palette.append(state(name, props))
                entry = [named(9, 'pos', lst_int([x, y, z])), named(3, 'state', i(keys[key]))]
                if block_nbt and (x, y, z) in block_nbt:
                    entry.append(named(10, 'nbt', payload(block_nbt[(x, y, z)])))
                blocks.append(compound(entry))
    root = compound([
        named(3, 'DataVersion', i(3955)),
        named(9, 'size', lst_int(list(size))),
        named(9, 'palette', bytes([10]) + i(len(palette)) + b''.join(palette)),
        named(9, 'blocks', bytes([10]) + i(len(blocks)) + b''.join(blocks)),
        named(9, 'entities', bytes([10]) + i(len(entities)) + b''.join(compound([
            named(9, 'pos', bytes([6]) + i(3) + b''.join(struct.pack('>d', v) for v in pos)),
            named(9, 'blockPos', lst_int(list(block_pos))),
            named(10, 'nbt', payload(data))]) for pos, block_pos, data in entities)),
    ])
    with gzip.open(path, 'wb') as f:
        f.write(named(10, '', root))


AIR = ('minecraft:air', None)


def hut(x, y, z):
    if y == 2:
        return ('minecraft:oak_planks', None)
    wall = x in (0, 4) or z in (0, 4)
    if not wall:
        return AIR
    if (x, z) == (2, 0):
        door = {'facing': 'north', 'half': 'lower' if y == 0 else 'upper', 'hinge': 'left', 'open': 'false', 'powered': 'false'}
        return ('minecraft:oak_door', door)
    return ('minecraft:cobblestone', None)


def well(x, y, z):
    if y == 0:
        return ('minecraft:water', {'level': '0'}) if (x, z) == (1, 1) else ('minecraft:cobblestone', None)
    return ('minecraft:oak_fence', None) if x in (0, 2) and z in (0, 2) else AIR


def wall(x, y, z):
    return ('minecraft:stone_bricks', None)


def camp(x, y, z):
    return ('minecraft:cobblestone', None)


def field(x, y, z):
    return ('minecraft:dirt', None) if 0 < x < 4 and 0 < z < 4 else ('minecraft:cobblestone', None)
def garden(x, y, z):
    """Floor with a 1x1 water basin, a back wall (z = 4) with a sign, an item frame and a banner, a potted poppy."""
    if y == 0:
        return ('minecraft:water', {'level': '0'}) if (x, z) == (2, 2) else ('minecraft:cobblestone', None)
    if z == 4:
        return ('minecraft:cobblestone', None)
    if (x, y, z) == (0, 1, 3):
        return ('minecraft:oak_wall_sign', {'facing': 'north', 'waterlogged': 'false'})
    if (x, y, z) == (4, 2, 3):
        return ('minecraft:red_wall_banner', {'facing': 'north'})
    if (x, y, z) == (4, 1, 1):
        return ('minecraft:potted_poppy', None)
    return AIR


def sign_text(*lines):
    return {'has_glowing_text': tag('b', 0), 'color': 'black',
            'messages': [json_text(line) for line in lines] + ['""'] * (4 - len(lines))}


def json_text(text):
    return '{"text":"' + text + '"}'


GARDEN_NBT = {
    (0, 1, 3): {'id': 'minecraft:sign', 'is_waxed': tag('b', 0), 'front_text': sign_text('Clay', 'Kingdom'),
                'back_text': sign_text()},
    (4, 2, 3): {'id': 'minecraft:banner', 'patterns': [{'pattern': 'minecraft:stripe_top', 'color': 'yellow'},
                                                       {'pattern': 'minecraft:cross', 'color': 'white'}]},
}
GARDEN_ENTITIES = (
    ((2.5, 1.5, 3.96875), (2, 1, 3), {'id': 'minecraft:item_frame', 'Facing': tag('b', 2), 'TileX': 2, 'TileY': 1, 'TileZ': 3,
                                      'Item': {'id': 'minecraft:apple', 'count': 1}, 'ItemRotation': tag('b', 0),
                                      'ItemDropChance': tag('f', 1.0), 'Fixed': tag('b', 0), 'Invisible': tag('b', 0)}),
    ((1.5, 1.0, 1.5), (1, 1, 1), {'id': 'minecraft:armor_stand', 'Rotation': [tag('f', 180.0), tag('f', 0.0)],
                                  'ArmorItems': [{}, {}, {'id': 'minecraft:iron_chestplate', 'count': 1},
                                                 {'id': 'minecraft:iron_helmet', 'count': 1}]}),
)


out = sys.argv[1]
os.makedirs(out, exist_ok=True)
write(os.path.join(out, 'test_hut.nbt'), (5, 3, 5), hut)
write(os.path.join(out, 'test_well.nbt'), (3, 2, 3), well)
write(os.path.join(out, 'test_wall.nbt'), (5, 3, 1), wall)
write(os.path.join(out, 'test_camp.nbt'), (3, 1, 3), camp)
write(os.path.join(out, 'test_field.nbt'), (5, 1, 5), field)
write(os.path.join(out, 'test_garden.nbt'), (5, 3, 5), garden, GARDEN_NBT, GARDEN_ENTITIES)

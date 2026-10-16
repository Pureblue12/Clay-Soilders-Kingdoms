"""Generates the stage 0 test structures (vanilla structure files, DataVersion 3955 = MC 1.21.1):
  common/src/main/resources/data/csr/structure/kingdom/test_hut.nbt   5x3x5 hut: cobblestone walls, oak door, plank roof
  common/src/main/resources/data/csr/structure/kingdom/test_well.nbt  3x2x3 well: cobblestone ring, water, fence posts
  common/src/main/resources/data/csr/structure/kingdom/test_wall.nbt  5x3x1 stone brick wall segment
  common/src/main/resources/data/csr/structure/kingdom/test_camp.nbt  3x1x3 cobblestone pad (stand-in lumber camp)
  common/src/main/resources/data/csr/structure/kingdom/test_field.nbt 5x1x5 cobblestone border, 3x3 dirt (stand-in field)
Local z = 0 is the side facing the easel (the front). Air inside a structure is stored, so pasting clears it.

Usage: python3 tools/make_test_structures.py common/src/main/resources/data/csr/structure/kingdom
"""
import gzip, os, struct, sys


def s(x): b = x.encode(); return struct.pack('>H', len(b)) + b
def named(t, name, payload): return bytes([t]) + s(name) + payload
def i(v): return struct.pack('>i', v)
def lst_int(vals): return bytes([3]) + i(len(vals)) + b''.join(i(v) for v in vals)
def compound(entries): return b''.join(entries) + b'\x00'


def state(name, props=None):
    entries = [named(8, 'Name', s(name))]
    if props:
        entries.append(named(10, 'Properties', compound([named(8, k, s(v)) for k, v in sorted(props.items())])))
    return compound(entries)


def write(path, size, block_at):
    """block_at(x, y, z) -> (name, props) for every position in the size box."""
    palette, keys, blocks = [], {}, []
    for y in range(size[1]):
        for z in range(size[2]):
            for x in range(size[0]):
                name, props = block_at(x, y, z)
                key = (name, tuple(sorted((props or {}).items())))
                if key not in keys:
                    keys[key] = len(palette)
                    palette.append(state(name, props))
                blocks.append(compound([named(9, 'pos', lst_int([x, y, z])), named(3, 'state', i(keys[key]))]))
    root = compound([
        named(3, 'DataVersion', i(3955)),
        named(9, 'size', lst_int(list(size))),
        named(9, 'palette', bytes([10]) + i(len(palette)) + b''.join(palette)),
        named(9, 'blocks', bytes([10]) + i(len(blocks)) + b''.join(blocks)),
        named(9, 'entities', bytes([10]) + i(0)),
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


out = sys.argv[1]
os.makedirs(out, exist_ok=True)
write(os.path.join(out, 'test_hut.nbt'), (5, 3, 5), hut)
write(os.path.join(out, 'test_well.nbt'), (3, 2, 3), well)
write(os.path.join(out, 'test_wall.nbt'), (5, 3, 1), wall)
write(os.path.join(out, 'test_camp.nbt'), (3, 1, 3), camp)
write(os.path.join(out, 'test_field.nbt'), (5, 1, 5), field)

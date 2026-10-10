"""Palisade modules, tier 2: straight, stepped, corner and gate (docs/STRUCTURES.md, palisade and wall modules).

Every module: z = 0 is the OUTSIDE face (sharpened spruce stakes), z = 1 the walkway of slabs on oak fence posts. The
walk's surface is 2.5 blocks up (a bottom slab at y 2) at both ends of every module except the stepped one, so modules
join walk to walk. Steps on the walk are half slabs (0.5 high), which soldiers walk up without jumping."""
from make_batch1_structures import Structure

LOG = dict(axis='y')
SLAB = dict(type='bottom', waterlogged='false')
TOP_SLAB = dict(type='top', waterlogged='false')
FENCE = {}


def _stake(s, x, z, top):
    """A spruce log stake from the ground up to y = top - 1, sharpened with a fence point at y = top."""
    for y in range(0, top):
        s.put(x, y, z, 'stake', **LOG)
    s.put(x, top, z, 'stake_top')


def _walk(s, x, z, surface2):
    """A walkway block whose surface is surface2 / 2 blocks up (5 = 2.5, 6 = 3.0, 7 = 3.5), on fence posts."""
    y, half = divmod(surface2 - 1, 2)                 # 5 -> bottom slab at y 2; 6 -> top slab at y 2; 7 -> bottom at 3
    for py in range(0, y):
        s.put(x, py, z, 'post')
    s.put(x, y, z, 'walk', **(TOP_SLAB if half else SLAB))
    return y


def palisade_straight(length=5):
    """Straight module: stakes alternate 4 and 5 high (a ragged sharpened top), walkway at 2.5 behind them."""
    s = Structure('palisade_straight', (length, 6, 2), palette='oak_a2')
    s.at(1, 'shell')
    for x in range(length):
        _stake(s, x, 0, 4 if x % 2 else 3)
    s.at(1, 'fittings')
    for x in range(length):
        _walk(s, x, 1, 5)
    s.at(2, 'flourish2')
    for x in range(1, length, 2):                     # torches on the tall stakes, above the walk
        s.put(x, 3, 1, 'wall_torch', facing='south')
    s.at(3, 'flourish3')
    for x in range(0, length, 2):
        s.put(x, 3, 1, 'moss_carpet')
    return s


def palisade_stepped(length=5):
    """Stepped module: the walk rises 1 block over its length (2.5 to 3.5) in two half-slab steps; stakes step too."""
    s = Structure('palisade_stepped', (length, 6, 2), palette='oak_a2')
    mid = length // 2
    s.at(1, 'shell')
    for x in range(length):
        _stake(s, x, 0, (3 if x <= mid else 4) + x % 2)
    s.at(1, 'fittings')
    for x in range(length):
        _walk(s, x, 1, 5 if x < mid else 6 if x == mid else 7)
    s.at(2, 'flourish2')
    s.put(1, 3, 1, 'wall_torch', facing='south')
    s.put(length - 2, 4, 1, 'wall_torch', facing='south')
    s.at(3, 'flourish3')
    s.put(0, 3, 1, 'moss_carpet'); s.put(length - 1, 4, 1, 'moss_carpet')
    return s


def palisade_corner():
    """Outside corner: stakes on the two outside faces (front z = 0 and left x = 0), one walk block in the angle."""
    s = Structure('palisade_corner', (2, 6, 2), palette='oak_a2')
    s.at(1, 'shell')
    _stake(s, 0, 0, 4)                                 # the corner stake is the tallest
    _stake(s, 1, 0, 3)
    _stake(s, 0, 1, 3)
    s.at(1, 'fittings')
    _walk(s, 1, 1, 5)
    s.at(2, 'flourish2')
    s.put(1, 3, 1, 'lantern', hanging='false', waterlogged='false')
    return s


def palisade_gate(width=3):
    """Gate: two log towers (1 x 3) with an opening `width` wide and 3 high (no fence gate: soldiers can't open
    them). A beam over the opening carries the walk across at 3.5, reached from the towers by half steps at 3.0."""
    W = width + 2
    s = Structure('palisade_gate', (W, 7, 3), palette='oak_a2')
    left, right = 0, W - 1
    s.at(1, 'shell')
    for x in (left, right):
        _stake(s, x, 0, 5)                             # front corner posts, tallest
        _stake(s, x, 2, 4)                             # back posts
        for y in range(0, 2):                          # middle: logs up to the step
            s.put(x, y, 1, 'stake', **LOG)
    for x in range(left + 1, right):                   # beams over the opening, outside and inside
        s.put(x, 3, 0, 'stake', axis='x')
        s.put(x, 4, 0, 'stake_top')
        s.put(x, 3, 2, 'stake', axis='x')
    s.at(1, 'fittings')
    for x in (left, right):                            # half steps up from the wall walk (2.5) to the gate walk (3.5)
        s.put(x, 2, 1, 'walk', **TOP_SLAB)
    for x in range(left + 1, right):
        s.put(x, 3, 1, 'walk', **SLAB)
    s.put(left + 1, 2, 0, 'wall_torch', facing='east')   # lights inside the opening, on the tower posts
    s.put(right - 1, 2, 0, 'wall_torch', facing='west')
    s.at(2, 'flourish2')
    s.put(W // 2, 2, 0, 'lantern', hanging='true', waterlogged='false')   # under the outer beam
    for x in (left, right):
        s.put(x, 6, 0, 'white_banner', rotation='8')
        s.decorate(x, 6, 0, 'banner_pattern', banner='white_banner', note='kingdom colours')
    s.at(3, 'flourish3')
    s.put(W // 2, 2, 2, 'lantern', hanging='true', waterlogged='false')   # under the inner beam
    s.put(left + 1, 4, 1, 'moss_carpet'); s.put(right - 1, 4, 1, 'moss_carpet')
    return s


_COMMON = dict(tier=2, zone='wall', role='defense', group='palisade', terrain='stepped', door=None)
STRUCTURES = [
    dict(path='palisade_straight', name='Palisade, straight', make=palisade_straight, **_COMMON,
         description='5 sharpened spruce stakes with a slab walkway behind (walk 2.5 high).'),
    dict(path='palisade_stepped', name='Palisade, stepped', make=palisade_stepped, **_COMMON,
         description='Like the straight module, but the walk rises 1 block (2.5 to 3.5) in two half steps.'),
    dict(path='palisade_corner', name='Palisade, corner', make=palisade_corner, **_COMMON,
         description='Outside corner piece joining two straight modules.'),
    dict(path='palisade_gate', name='Palisade gate', make=palisade_gate, **{**_COMMON, 'role': 'gate', 'group': 'gate'},
         description='Two log towers and a 3 wide, 3 high opening (no fence gate); the walk crosses over it.'),
]

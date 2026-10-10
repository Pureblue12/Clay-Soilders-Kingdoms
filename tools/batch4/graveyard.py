"""Graveyard (tier 4, look A): a walled churchyard plot with rows of graves and a yew (docs/STRUCTURES.md, "Graveyard").

Ground rule: a flush piece (ground_layer=0): layer y = 0 REPLACES the top ground block, so the gravel paths and the
grave mounds (coarse dirt) are level with the grass; a cell left empty in layer 0 stays grass. Everything standing
(walls, headstones, the yew) is at y >= 1.

Layout (9 wide x 12 deep, front z = 0): a low cobblestone wall (x 0..8, z 1..11) with stone brick piers at the corners
and halfway along the sides, the entrance in the middle of the front wall (3 wide, between two taller piers). A gravel
path runs from the entrance to a stone churchyard cross at the back; a cross path halfway leads to a bench on each
side. Graves lie on both sides of the path, headstones at the wall end, mounds toward the path. The back left quarter
is kept for the yew.
Level 1: walls, piers, paths, eight graves (stone brick and cobblestone headstones), the cross, two benches.
Level 2: the yew (the signature: a dark, dense crown on a thick dark trunk), a lychgate (a little spruce roof on posts
over the entrance), lanterns on the gate posts and on a post by the benches, a newer row of graves at the back right.
Level 3: age and care: flowers on the graves and along the wall, moss on the wall tops, candles at headstones, a fresh
grave with a shovel stuck in it (decoration step), vines on the piers, flower pots on the gate piers."""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, HANG, STAND, LEAVES, connect, noise

YEW = dict(persistent='true', distance='1', waterlogged='false')


def headstone(s, x, z, kind):
    """A headstone at (x, 1, z) beside the cross path (z 6), its grave behind it."""
    face = 'south' if z < 6 else 'north'
    if kind == 'stair':          # a low rounded stone: a stair whose high side is at the wall end
        P(s, x, 1, z, 'stone_brick_stairs', facing=face, **STAIR)
    elif kind == 'wall':         # a small cross: a wall post
        P(s, x, 1, z, 'cobblestone_wall')
    elif kind == 'cobblestone_stairs':   # an old rough stone
        P(s, x, 1, z, kind, facing=face, **STAIR)
    else:                        # a tall headstone
        P(s, x, 1, z, kind)


def graveyard():
    W, D = 9, 12
    s = Structure('graveyard', (W, 7, D), palette='oak_a2')
    zf, zb = 1, D - 1                    # front and back wall rows
    mid = 4
    cross_z = 6                          # the cross path
    s.at(1, 'shell')
    # the wall: cobblestone wall all round, stone brick piers at the corners and halfway along the sides
    for x in range(W):
        for z in range(zf, D):
            if x in (0, W - 1) or z in (zf, zb):
                P(s, x, 0, z, 'cobblestone')                            # the wall's footing, flush
                P(s, x, 1, z, 'cobblestone_wall')
    for x, z in ((0, zf), (W - 1, zf), (0, zb), (W - 1, zb), (0, cross_z), (W - 1, cross_z)):
        s.remove(x, 1, z)
        P(s, x, 1, z, 'stone_bricks')
    for x in (mid - 1, mid, mid + 1):                                    # the entrance, 3 wide
        s.remove(x, 1, zf)
        s.remove(x, 0, zf)
    for x in (mid - 2, mid + 2):                                         # taller gate piers
        s.remove(x, 1, zf)
        P(s, x, 1, zf, 'stone_bricks'); P(s, x, 2, zf, 'stone_bricks')
    # gravel paths: entrance to the cross, and across the middle; a gravel apron in front of the entrance
    for z in range(0, zb):
        P(s, mid, 0, z, 'gravel')
    for x in (mid - 1, mid + 1):
        P(s, x, 0, 0, 'gravel'); P(s, x, 0, zf, 'gravel')
    for x in range(1, W - 1):
        A(s, x, 0, cross_z, 'gravel')

    s.at(1, 'fittings')
    # graves in rows along the cross path: headstones on the path side, mounds (coarse dirt, 2 long) behind them
    graves = [(1, 5, 'cobblestone_stairs'), (3, 5, 'wall'), (5, 5, 'wall'), (7, 5, 'stair'),   # front rows, mounds z 3..4
              (5, 7, 'stair'), (7, 7, 'cobblestone_stairs')]                                 # back right, mounds z 8..9                                     # back right, mounds z 8..9
    for x, hz, kind in graves:
        headstone(s, x, hz, kind)
        for z in ((hz - 1, hz - 2) if hz < cross_z else (hz + 1, hz + 2)):
            P(s, x, 0, z, 'coarse_dirt')
    # the churchyard cross at the end of the path
    P(s, mid, 0, zb - 1, 'cobblestone')
    P(s, mid, 1, zb - 1, 'stone_bricks')
    for y in (2, 3, 4):
        P(s, mid, y, zb - 1, 'cobblestone_wall')
    for x in (mid - 1, mid + 1):                                         # the arms: wall posts joined to the shaft
        P(s, x, 3, zb - 1, 'cobblestone_wall')
    # benches at the ends of the cross path
    for x, f in ((1, 'west'), (W - 2, 'east')):
        P(s, x, 1, cross_z, 'spruce_stairs', facing=f, **STAIR)

    s.at(2, 'flourish2')
    # the yew (signature) in the back left quarter: a thick dark trunk and a dense, dark crown
    tx, tz = 2, 9
    P(s, tx, 0, tz, 'coarse_dirt')
    for y in (1, 2):
        P(s, tx, y, tz, 'dark_oak_log', axis='y')
    for x in range(tx - 2, tx + 3):                                      # the crown, y 3..6
        for z in range(tz - 2, tz + 3):
            for y in (2, 3, 4, 5, 6):
                dx, dz = abs(x - tx), abs(z - tz)
                r = {2: 1, 3: 2, 4: 2, 5: 1, 6: 0}[y]
                if x < 0 or x > mid - 1 or z >= D or (x, y, z) in s.blocks:     # clear of the cross
                    continue
                if max(dx, dz) <= r and not (dx == 2 and dz == 2) and not (y in (2, 3) and max(dx, dz) == 2 and noise(x, y, z, 7) < 0.5):
                    if y == 6 or noise(x, y, z, 9) > 0.08:
                        P(s, x, y, z, 'spruce_leaves' if noise(x, y, z, 3) < 0.6 else 'dark_oak_leaves', **YEW)
    # the lychgate: spruce posts on the gate piers, a beam between them and a little gable roof over the entrance
    for x in (mid - 2, mid + 2):
        P(s, x, 3, zf, 'spruce_log', axis='y')
    for x in range(mid - 1, mid + 2):
        P(s, x, 4, zf, 'spruce_log', axis='x')
    for z in (zf - 1, zf, zf + 1):                                        # a steep gable roof, ridge front to back
        for i in range(2):
            P(s, mid - 2 + i, 4 + i, z, 'spruce_stairs', facing='east', **STAIR)
            P(s, mid + 2 - i, 4 + i, z, 'spruce_stairs', facing='west', **STAIR)
        P(s, mid, 5, z, 'dark_oak_planks')
        P(s, mid, 6, z, 'spruce_slab', **SLAB)
    for z in (zf - 1, zf + 1):                                           # the open truss: a king post at each end
        P(s, mid, 4, z, 'spruce_fence')
    P(s, mid, 3, zf, 'lantern', **HANG)                                  # a lantern under the lychgate
    # lanterns on the side piers by the benches
    for x in (0, W - 1):
        P(s, x, 2, cross_z, 'lantern', **STAND)
    # a newer grave at the back left, under the yew
    headstone(s, 3, 7, 'polished_andesite')
    for z in (8, 9):
        P(s, 3, 0, z, 'coarse_dirt')

    s.at(3, 'flourish3')
    # flowers on the graves and between them, candles at the headstones
    for x, z, f in ((1, 3, 'poppy'), (3, 3, 'oxeye_daisy'), (5, 3, 'cornflower'), (7, 3, 'azure_bluet'),
                    (5, 9, 'lily_of_the_valley'), (7, 9, 'poppy'), (3, 9, 'white_tulip'), (2, 4, 'allium'),
                    (6, 8, 'dandelion'), (6, 3, 'lily_of_the_valley')):
        A(s, x, 1, z, f)
    for x, z, n in ((3, 4, '2'), (5, 8, '1'), (7, 4, '3'), (1, 4, '1')):
        A(s, x, 1, z, 'candle', candles=n, lit='false', waterlogged='false')
    # moss on the wall tops
    for x, z in ((0, 3), (0, 4), (0, 8), (W - 1, 9), (W - 1, 10), (2, zb), (6, zb), (W - 1, 3)):
        A(s, x, 2, z, 'moss_carpet')
    # a fresh grave by the yew (no headstone yet) with a shovel stuck in it
    for z in (7, 8):
        P(s, 1, 0, z, 'dirt')
    s.decorate(1, 1, 8, 'item', item='minecraft:iron_shovel', note='a shovel in the fresh grave')
    # flower pots on the corner piers
    for x, z in ((0, zf), (W - 1, zf), (0, zb), (W - 1, zb)):
        P(s, x, 2, z, 'flower_pot')
        s.decorate(x, 2, z, 'potted_plant', plant='white_tulip')
    # flowers along the outside of the wall, vines on the piers, flower pots on the gate piers
    for x, z, f in ((0, 0, 'poppy'), (1, 0, 'oak_leaves'), (W - 1, 0, 'oak_leaves'), (W - 2, 0, 'dandelion')):
        if f == 'oak_leaves':
            A(s, x, 1, z, f, **LEAVES)
        else:
            A(s, x, 1, z, f)
    for x in (mid - 2, mid + 2):
        A(s, x, 1, 0, 'vine', north='false', south='true', east='false', west='false', up='false')
    connect(s)
    return s


STRUCTURES = [dict(path='graveyard', name='Graveyard', tier=4, zone='outer', role='civic', group='graveyard',
                   terrain='flexible', door='front', make=graveyard, ground_layer=0, changes='First version.',
                   description='Flush piece (ground_layer 0: the mod does not support it yet). Walled churchyard '
                               '(cobblestone wall, stone brick piers) with gravel paths, graves on both sides, a '
                               'stone cross and benches; L2 adds the yew, a lychgate over the entrance, lanterns and '
                               'a new grave; L3 flowers, candles, moss and vines, a fresh grave.')]

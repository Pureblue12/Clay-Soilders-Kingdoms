"""Banner pole (tier 4, town style A, prop): the kingdom's colours on a pole (docs/STRUCTURES.md, "Banner pole")."""
from make_batch1_structures import Structure
from revise1.blockconn import connect, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
SLAB = dict(type='bottom', waterlogged='false')
HANG = dict(hanging='true', waterlogged='false')


def banner_pole():
    """3 x 3 footprint, 9 tall (the top banner reaches the ninth block). A stone brick plinth (a block with a stair on each side, slabs in the corners) holds a
    spruce log foot and a tall spruce fence pole with a crossbar near the top (a fence arm to each side).
    Level 1 (the pole): plinth, pole and crossbar, a lantern hanging under each end of the crossbar (the top stays
    free for the banner).
    Level 2 (the colours): the kingdom's red banner on top of the pole and two white banners hanging from the
    crossbar's front.
    Level 3 (a festival): two red banners on the crossbar's back, the banner patterns (decoration step) and a plaque on
    the log foot (text by the decoration step)."""
    s = Structure('banner_pole', (3, 9, 3), palette='oak_a2')
    c = 1
    s.at(1, 'shell')
    put_id(s, c, 0, c, 'stone_bricks')
    for dx, dz, f in ((-1, 0, 'east'), (1, 0, 'west'), (0, -1, 'south'), (0, 1, 'north')):
        put_id(s, c + dx, 0, c + dz, 'stone_brick_stairs', facing=f, **STAIR)
    for x in (c - 1, c + 1):
        for z in (c - 1, c + 1):
            put_id(s, x, 0, z, 'cobblestone_slab', **SLAB)
    put_id(s, c, 1, c, 'spruce_log', axis='y')
    for y in range(2, 7):
        put_id(s, c, y, c, 'spruce_fence')
    s.at(1, 'fittings')
    for x in (c - 1, c + 1):                                                # the crossbar
        put_id(s, x, 6, c, 'spruce_fence')
        put_id(s, x, 5, c, 'lantern', **HANG)

    s.at(2, 'flourish2')
    put_id(s, c, 7, c, 'red_banner', rotation='8')                          # the kingdom's banner on top, facing front
    for x in (c - 1, c + 1):
        put_id(s, x, 6, c - 1, 'white_wall_banner', facing='north')         # front of the crossbar

    s.at(3, 'flourish3')
    for x in (c - 1, c + 1):
        put_id(s, x, 6, c + 1, 'red_wall_banner', facing='south')           # back of the crossbar
    s.decorate(c, 7, c, 'banner_pattern', note="the kingdom's arms on the top banner")
    for x in (c - 1, c + 1):
        s.decorate(x, 6, c - 1, 'banner_pattern', note='a red cross on the white banners')
    put_id(s, c, 1, c - 1, 'oak_wall_sign', facing='north', waterlogged='false')    # plaque on the log foot
    s.decorate(c, 1, c - 1, 'sign_text', lines=['', 'Long live', 'the King', ''])
    return connect(s)


STRUCTURES = [dict(path='banner_pole', name='Banner pole', tier=4, zone='all', role='decoration', group='banner_pole',
                   terrain='flexible', door=None, make=banner_pole, changes='First version.',
                   description='Over budget: 25 blocks vs 4 (a stone plinth and a crossbar so it is not a bare pole). Tall '
                               'spruce pole on a stone brick plinth with a crossbar and two hanging lanterns (L1); L2 '
                               'the kingdom\'s red banner on top and two white banners on the crossbar; L3 two red '
                               'banners on the back, a plaque, banner patterns (decoration step).')]

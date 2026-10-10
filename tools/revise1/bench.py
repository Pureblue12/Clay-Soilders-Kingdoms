"""Bench, revised from Carlton's notes (batch 1 -> revise 1): a proper miniature bench nook instead of two stairs."""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
LEAVES = dict(distance='1', persistent='true', waterlogged='false')


def fence(**sides):
    return {d: str(sides.get(d, False)).lower() for d in ('north', 'south', 'east', 'west')} | {'waterlogged': 'false'}


def trapdoor(facing, half='bottom', open_=False):
    return dict(facing=facing, half=half, open=str(open_).lower(), powered='false', waterlogged='false')


def bench():
    """Bench nook, 11 x 3: a three-seat oak stair bench with sign armrests, a planter at each end (coarse dirt in an
    open-trapdoor rim, a poppy), a leaf hedge behind with a spruce fence post behind each armrest, a cobblestone apron
    in front and a lantern post. L2 raises the posts into a little pergola (lean-to roof, a lantern hanging over the seat),
    shapes the hedge higher with a flowering bush in the middle, and adds a second lantern post."""
    s = Structure('bench', (11, 4, 3), palette='oak_a2')
    s.at(1, 'shell')
    for x in (4, 5, 6):
        s.put(x, 0, 1, 'chair', facing='south', **STAIR)                       # seat faces the front, back to the hedge
    s.put(3, 0, 1, 'oak_wall_sign', facing='west', waterlogged='false')        # armrests: signs on the bench ends
    s.put(7, 0, 1, 'oak_wall_sign', facing='east', waterlogged='false')
    for x in (1, 9):                                                            # planters: coarse dirt in a trapdoor rim
        s.put(x, 0, 1, 'soil')
        s.put(x, 0, 0, 'trapdoor', **trapdoor('north', open_=True))            # panel on the side towards the dirt
        s.put(x - 1, 0, 1, 'trapdoor', **trapdoor('west', open_=True))
        s.put(x + 1, 0, 1, 'trapdoor', **trapdoor('east', open_=True))
    for x in range(3, 8):                                                       # cobblestone apron in front
        s.put(x, 0, 0, 'stone_slab', type='bottom', waterlogged='false')
    for x in range(0, 11):                                                      # hedge behind, a post behind each armrest
        if x in (3, 7):
            s.put(x, 0, 2, 'stake_top', **fence()); s.put(x, 1, 2, 'stake_top', **fence())
        else:
            s.put(x, 0, 2, 'oak_leaves', **LEAVES)
    s.at(1, 'fittings')
    s.put(1, 1, 1, 'flower'); s.put(9, 1, 1, 'flower')
    s.put(10, 0, 0, 'fence', **fence()); s.put(10, 1, 0, 'fence', **fence())
    s.put(10, 2, 0, 'lantern', hanging='false', waterlogged='false')
    s.decorate(3, 0, 1, 'sign_text', lines=['', '', '', ''], note='blank armrest')

    s.at(2, 'flourish2')
    # pergola: the posts go up one more, a slab roof over the seat, a lantern hanging over it
    for x in (3, 7):
        s.put(x, 2, 2, 'stake_top', **fence())
    for x in range(2, 9):                                                       # a lean-to roof sloping to the front
        s.put(x, 3, 1, 'roof_stairs', facing='south', **STAIR)
        s.put(x, 3, 2, 'plank2')
    s.put(5, 2, 1, 'lantern', hanging='true', waterlogged='false')
    # hedge shaped higher at the ends and between the posts, with a flowering bush in the middle
    for x in (0, 1, 2, 4, 6, 8, 9, 10):
        s.put(x, 1, 2, 'oak_leaves', **LEAVES)
    s.put(5, 1, 2, 'flowering_azalea_leaves', **LEAVES)
    # second lantern post on the left
    s.put(0, 0, 0, 'fence', **fence()); s.put(0, 1, 0, 'fence', **fence())
    s.put(0, 2, 0, 'lantern', hanging='false', waterlogged='false')
    return s


STRUCTURES = [dict(path='bench', name='Bench', tier=1, zone='inner', role='decoration', group='bench',
                   terrain='flexible', door=None, make=bench,
                   description='Bench nook: three-seat oak stair bench with sign armrests between two planters, a leaf hedge '
                               'and spruce posts behind, a cobblestone apron and lantern posts; L2 adds a pergola roof with a '
                               'hanging lantern and more greenery.',
                   changes='Rebuilt from two bare stairs into a bench nook: a three-seat bench with sign armrests, planters '
                           'with flowers at both ends, a hedge and posts behind, a stone apron and a lantern post; level 2 '
                           'adds a little pergola roof with a hanging lantern, a shaped flowering hedge, a second lantern post '
                           'and flowers.')]

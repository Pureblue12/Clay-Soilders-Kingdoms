"""Signpost, revised from Carlton's notes (batch 1 -> revise 1): a proper village signpost instead of a bare log."""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', shape='straight', waterlogged='false')


def sign(facing):
    return dict(facing=facing, waterlogged='false')


def signpost():
    """Village signpost, 5 x 5: a spruce log post on a stepped cobblestone base with flower corners, a crossarm near the
    top (spruce fence), direction signs on all four faces at different heights (text by the decoration step), a lantern
    on a chain from one end of the arm and a lantern on top. L2 adds a board (an open trapdoor) on a chain under the
    other end of the arm, moss round the base, and a notice sign high on the back. A real hanging sign is not used:
    it is not builder-tested and the Studio can't draw it yet."""
    s = Structure('signpost', (5, 7, 5), palette='oak_a2')
    c = 2
    s.at(1, 'shell')
    s.put(c, 0, c, 'stone')                                                     # base: a block with stairs round it
    for dx, dz, f in ((-1, 0, 'east'), (1, 0, 'west'), (0, -1, 'south'), (0, 1, 'north')):
        s.put(c + dx, 0, c + dz, 'stone_stairs', facing=f, **STAIR)
    for x, z in ((1, 1), (3, 1), (1, 3), (3, 3)):                              # corners: soil for flowers
        s.put(x, 0, z, 'soil')
    for y in range(1, 6):                                                       # the post
        s.put(c, y, c, 'shed_post', axis='y')
    for x in (0, 1, 3, 4):                                                      # crossarm: spruce fence both ways
        s.put(x, 5, c, 'stake_top', east=str(x < 4).lower(), west=str(x > 0).lower(), north='false', south='false',
              waterlogged='false')
    s.at(1, 'fittings')
    s.put(c, 6, c, 'lantern', hanging='false', waterlogged='false')            # lantern on top of the post
    s.put(0, 4, c, 'chain', axis='y')                                           # lantern on a chain from the arm
    s.put(0, 3, c, 'lantern', hanging='true', waterlogged='false')
    # direction signs, one per face, at different heights
    signs = [
        ((c, 4, c - 1), 'north', ['', 'Village', 'square', '']),
        ((c + 1, 3, c), 'east', ['', 'Fields', '-->', '']),
        ((c - 1, 2, c), 'west', ['', '<--', 'Keep', '']),
        ((c, 3, c + 1), 'south', ['', 'Gate', '', '']),
    ]
    for (x, y, z), f, lines in signs:
        s.put(x, y, z, 'oak_wall_sign', **sign(f))
        s.decorate(x, y, z, 'sign_text', lines=lines)
    for x, z in ((1, 1), (3, 1), (1, 3), (3, 3)):
        s.put(x, 1, z, 'flower')

    s.at(2, 'flourish2')
    s.put(4, 4, c, 'chain', axis='y')                                           # a board on a chain from the arm
    s.put(4, 3, c, 'trapdoor', facing='north', half='bottom', open='true', powered='false', waterlogged='false')
    s.decorate(4, 3, c, 'note', note='the hanging board can become an oak hanging sign (Inn) once one is builder-tested')
    for x, z in ((0, 2), (4, 2), (2, 0), (2, 4)):                               # moss round the foot of the base
        s.put(x, 0, z, 'moss_carpet')
    s.put(c, 5, c + 1, 'oak_wall_sign', **sign('south'))                         # a small notice high on the back
    s.decorate(c, 5, c + 1, 'sign_text', lines=['Notices', '', '', ''])
    return s


STRUCTURES = [dict(path='signpost', name='Signpost', tier=1, zone='inner', role='decoration', group='signpost',
                   terrain='flexible', door=None, make=signpost,
                   description='Village signpost: spruce post on a stepped cobblestone base with flower corners, a crossarm, '
                               'direction signs on all four faces at different heights, a lantern on a chain and one on top.',
                   changes='Rebuilt from a bare log into a village signpost: a stone base with flowers, a crossarm, four '
                           'direction signs at different heights, a lantern on a chain and one on top; level 2 adds a hanging '
                           'board on a chain under the other end of the arm, moss round the base and a notice sign.')]

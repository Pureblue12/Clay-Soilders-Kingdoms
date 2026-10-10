"""Signpost, revised from Carlton's notes (batch 1 -> revise 1): a proper village signpost instead of a bare log."""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', shape='straight', waterlogged='false')


def sign(facing):
    return dict(facing=facing, waterlogged='false')


def arrow(s, xs, y, c, lines):
    """A direction arrow: a short spruce fence arm sticking out of the post along x, with a row of oak wall signs hung
    on its front and back (a board two signs wide, readable from both sides; signs hold on fences in the game). Text by
    the decoration step."""
    for x in xs:
        s.put(x, y, c, 'stake_top')
    for x in xs:
        s.put(x, y, c - 1, 'oak_wall_sign', **sign('north'))
        s.put(x, y, c + 1, 'oak_wall_sign', **sign('south'))
    s.decorate(xs[0], y, c - 1, 'sign_text', lines=lines)
    s.decorate(xs[0], y, c + 1, 'sign_text', lines=lines)


def signpost():
    """Village signpost, 5 x 5: a spruce log post on a stepped cobblestone base with poppies in coarse-dirt corners, a
    lantern on top, and two direction arrows at different heights: short spruce fence arms out of the post, each carrying a board of
    two oak wall signs on its front and back (east 'Fields', higher; west 'Keep', lower).
    L2 adds a lantern arm (spruce fence out to the front, a lantern on a chain), a second arm to the back with a board
    hanging on a chain, two more signs on the post itself (the village name and a notice), and moss round the base."""
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
    s.at(1, 'fittings')
    arrow(s, (3, 4), 4, c, ['', 'Fields', '-->', ''])
    arrow(s, (1, 0), 3, c, ['', '<--', 'Keep', ''])
    s.put(c, 6, c, 'lantern', hanging='false', waterlogged='false')            # lantern on top of the post
    for x, z in ((1, 1), (3, 1), (1, 3), (3, 3)):
        s.put(x, 1, z, 'flower')

    s.at(2, 'flourish2')
    # lantern arm to the front: spruce fence out of the post, a lantern on a chain
    for z in (c - 1, c - 2):
        s.put(c, 5, z, 'stake_top')
    s.put(c, 4, 0, 'chain', axis='y')
    s.put(c, 3, 0, 'lantern', hanging='true', waterlogged='false')
    # arm to the back with a hanging board (an open trapdoor on a chain)
    for z in (c + 1, c + 2):
        s.put(c, 5, z, 'stake_top')
    s.put(c, 4, 4, 'chain', axis='y')
    s.put(c, 3, 4, 'trapdoor', facing='east', half='bottom', open='true', powered='false', waterlogged='false')
    s.decorate(c, 3, 4, 'note', note='the hanging board can become an oak hanging sign (Inn) once one is builder-tested')
    # two more signs on the post itself: the village name on the front, a notice on the back
    s.put(c, 1, c - 1, 'oak_wall_sign', **sign('north'))
    s.decorate(c, 1, c - 1, 'sign_text', lines=['Welcome to', '<village>', '', ''])
    s.put(c, 2, c + 1, 'oak_wall_sign', **sign('south'))
    s.decorate(c, 2, c + 1, 'sign_text', lines=['Notices', '', '', ''])
    for x, z in ((0, 2), (4, 2), (2, 0), (2, 4)):                               # moss round the foot of the base
        s.put(x, 0, z, 'moss_carpet')
    return s


STRUCTURES = [dict(path='signpost', name='Signpost', tier=1, zone='inner', role='decoration', group='signpost',
                   terrain='flexible', door=None, make=signpost,
                   description='Village signpost: spruce post on a stepped cobblestone base with poppies, two direction '
                               'arrows (short arms carrying boards of oak wall signs) at different heights, a lantern on top.',
                   changes='The direction signs are now real arrows: short fence arms out of the post carrying boards of oak '
                           'wall signs, pointing east and west at different heights; level 2 clearly adds more: a lantern arm '
                           'with a lantern on a chain, a back arm with a hanging board, the village name and a notice sign '
                           'on the post, and moss round the base.')]

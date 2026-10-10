"""Lamp post (tier 4, town style A, prop): a street lamp for the paved town (docs/STRUCTURES.md, "Lamp post")."""
from make_batch1_structures import Structure
from revise1.blockconn import connect, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
HANG = dict(hanging='true', waterlogged='false')
STAND = dict(hanging='false', waterlogged='false')


def lamp_post():
    """3 x 3 footprint, 6 tall. The post stands on a small stepped cobblestone foot (a block with a stair on each side,
    like the signpost): a dark oak log foot and a slim spruce fence post and (level 2 sets a small lantern on top of it).
    Level 1 (a working lamp): the post and a short arm to the front (the street side) with a lantern hanging under it.
    Level 2 (a double lamp): a second arm to the back with a lantern on a chain, a small lantern on top of the
    post, two corners of the foot filled with slabs.
    Level 3 (a cared-for street): a street-name sign on the post (text by the decoration step), a red tulip and a
    little box bush in the other two corners."""
    s = Structure('lamp_post', (3, 6, 3), palette='oak_a2')
    c = 1
    s.at(1, 'shell')
    put_id(s, c, 0, c, 'cobblestone')                                       # the foot: a block with stairs round it
    for dx, dz, f in ((-1, 0, 'east'), (1, 0, 'west'), (0, -1, 'south'), (0, 1, 'north')):
        put_id(s, c + dx, 0, c + dz, 'cobblestone_stairs', facing=f, **STAIR)
    put_id(s, c, 1, c, 'dark_oak_log', axis='y')
    put_id(s, c, 2, c, 'spruce_fence')
    put_id(s, c, 3, c, 'spruce_fence')
    put_id(s, c, 4, c, 'spruce_fence')                                      # the arms come out of the post's top
    s.at(1, 'fittings')
    put_id(s, c, 4, c - 1, 'spruce_fence')                                  # the arm to the front
    put_id(s, c, 3, c - 1, 'lantern', **HANG)

    s.at(2, 'flourish2')
    put_id(s, c, 4, c + 1, 'spruce_fence')                                  # the arm to the back
    put_id(s, c, 3, c + 1, 'chain', axis='y')
    put_id(s, c, 2, c + 1, 'lantern', **HANG)
    put_id(s, c, 5, c, 'lantern', **STAND)                                  # a small lamp on top of the post
    for x, z in ((c - 1, c - 1), (c + 1, c + 1)):                           # two corners of the foot filled
        put_id(s, x, 0, z, 'cobblestone_slab', type='bottom', waterlogged='false')

    s.at(3, 'flourish3')
    put_id(s, c - 1, 2, c, 'oak_wall_sign', facing='west', waterlogged='false')      # street name, on the post's side
    s.decorate(c - 1, 2, c, 'sign_text', lines=['', 'Market', 'Street', ''])
    put_id(s, c + 1, 0, c - 1, 'red_tulip')                                # a tulip and a little box bush at the foot
    put_id(s, c - 1, 0, c + 1, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    return connect(s)


STRUCTURES = [dict(path='lamp_post', name='Lamp post', tier=4, zone='all', role='decoration', group='lamp_post',
                   terrain='flexible', door=None, make=lamp_post, changes='First version.',
                   description='Over budget: 20 blocks vs 5 (a 3 x 3 stepped foot so it is not a bare post). Slim spruce '
                               'fence post on a dark oak foot and a stepped cobblestone base, a short arm to the street '
                               'with a hanging lantern (L1); L2 a second arm with a lantern on a chain, a lantern on '
                               'top, slab corners; L3 a street-name sign, a tulip and a little bush at the foot.')]

"""Market stall (tier 3, look A, prop): a plank counter between spruce posts under a striped awning."""
from make_batch1_structures import Structure
from revise1.blockconn import connect, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
TOP = dict(type='top', waterlogged='false')
BOTTOM = dict(type='bottom', waterlogged='false')
HANG = dict(hanging='true', waterlogged='false')
STAND = dict(hanging='false', waterlogged='false')
OPEN = dict(half='bottom', open='true', powered='false', waterlogged='false')


def stripe(i):
    return 'red' if i % 2 else 'white'


def market_stall():
    """Market stall. The stall is 3 wide and 3 deep (posts at x 2 and 4, z 1 and 3): the counter faces the street
    (front, z = 1), the trader stands behind it (3, 0, 2), reached through the open left side, and the back row holds
    the stock. Four spruce log posts carry a canopy of spruce top slabs with red and white carpet stripes on it.

    Level 1: posts, plank counter with a trapdoor front, canopy, the trader's chest and a barrel, a crate by the side.
    Level 2 (the awning): a striped red and white wool border all round the canopy, one block out, so the awning
    overhangs the counter and the sides; lanterns hung at its front corners; produce stacked under the right side
    (pumpkin, hay, melon) and a cake on the counter. Level 3 (the shop): a lantern post with the shop sign by the
    street (text by the decoration step), a banner on the front post, flower pots, a stool for a customer, a second
    crate stack behind, apples in the open barrel (decoration step)."""
    s = Structure('market_stall', (7, 4, 5), palette='oak_a2')
    x0, x1, zc, zb = 2, 4, 1, 3                      # post columns, counter row, back row
    mid = 3
    s.at(1, 'shell')
    for x in (x0, x1):
        for z in (zc, zb):
            for y in (0, 1):
                put_id(s, x, y, z, 'spruce_log', axis='y')
    put_id(s, mid, 0, zc, 'spruce_planks')                       # the counter
    for x in range(x0, x1 + 1):                                  # canopy: top slabs over the posts
        for z in range(zc, zb + 1):
            put_id(s, x, 2, z, 'spruce_slab', **TOP)
            put_id(s, x, 3, z, stripe(x) + '_carpet')            # stripes on it (carpets: KNOWN-OK)
    s.at(1, 'fittings')
    put_id(s, mid, 0, zc - 1, 'spruce_trapdoor', facing='north', **OPEN)    # panel on the counter front
    put_id(s, mid, 0, zb, 'chest', facing='north', type='single', waterlogged='false')   # the trader's stock
    put_id(s, x1 + 1, 0, zb, 'barrel', facing='up', open='false')            # a crate by the right side
    put_id(s, x1 + 1, 1, zb, 'spruce_trapdoor', facing='north', half='bottom', open='false', powered='false',
           waterlogged='false')                                                # its lid

    s.at(2, 'flourish2')
    for x in range(x0 - 1, x1 + 2):                              # striped wool border, front and back
        for z in (zc - 1, zb + 1):
            put_id(s, x, 2, z, stripe(x) + '_wool')
    for z in range(zc, zb + 1):                                  # and the sides (red ends, stripes across)
        for x in (x0 - 1, x1 + 1):
            put_id(s, x, 2, z, stripe(z + 1) + '_wool')
    for x in (x0 - 1, x1 + 1):                                   # lanterns hung from the front corners
        put_id(s, x, 1, zc - 1, 'lantern', **HANG)
    put_id(s, x1 + 1, 0, zc, 'pumpkin')                          # produce under the right side of the awning
    put_id(s, x1 + 1, 0, zc + 1, 'hay_block', axis='y')
    put_id(s, x1 + 1, 1, zc, 'melon')
    put_id(s, mid, 1, zc, 'cake', bites='0')                     # a cake on the counter
    put_id(s, mid, 1, zb, 'barrel', facing='up', open='true')    # an open barrel on the chest
    s.decorate(mid, 1, zb, 'item', item='minecraft:apple', note='apples in the open barrel')

    s.at(3, 'flourish3')
    put_id(s, 0, 0, zc - 1, 'spruce_log', axis='y')              # lantern post with the shop sign, by the street
    put_id(s, 0, 1, zc - 1, 'spruce_fence')
    put_id(s, 0, 2, zc - 1, 'lantern', **STAND)
    put_id(s, 0, 0, zc, 'oak_wall_sign', facing='south', waterlogged='false')
    s.decorate(0, 0, zc, 'sign_text', lines=['', 'Fresh', 'Produce', ''])
    put_id(s, x1, 1, zc - 1, 'red_wall_banner', facing='north')  # banner on the right front post
    put_id(s, x0 - 1, 0, zc - 1, 'oak_stairs', facing='east', **STAIR)       # a stool for a customer
    for x, z, plant in ((x1 + 1, zc + 1, 'red_tulip'), (x0 - 1, zb + 1, 'dandelion')):   # flower pots
        if (x, 1, z) not in s.blocks:
            put_id(s, x, 1, z, 'flower_pot')
            s.decorate(x, 1, z, 'potted_plant', plant=plant)
    put_id(s, x0 - 1, 0, zb + 1, 'barrel', facing='up', open='false')        # crates behind on the left
    put_id(s, x0 - 1, 0, zb, 'barrel', facing='east', open='false')
    put_id(s, mid, 0, zb + 1, 'composter')                                   # scraps bin at the back
    put_id(s, 6, 0, zb + 1, 'oak_leaves', persistent='true', distance='1', waterlogged='false')   # a bush
    put_id(s, 6, 0, zc, 'poppy')
    connect(s)
    return s


STRUCTURES = [dict(path='market_stall', name='Market stall', tier=3, zone='inner', role='market',
                   group='market_stall', terrain='flexible', door=None, make=market_stall,
                   description='draft')]

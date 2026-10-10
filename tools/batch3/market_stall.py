"""Market stall (tier 3, look A, prop): a plank counter between spruce posts under a striped awning."""
from make_batch1_structures import Structure
from revise1.blockconn import connect, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
BOTTOM = dict(type='bottom', waterlogged='false')
HANG = dict(hanging='true', waterlogged='false')
STAND = dict(hanging='false', waterlogged='false')
OPEN = dict(half='bottom', open='true', powered='false', waterlogged='false')
SHUT = dict(half='bottom', open='false', powered='false', waterlogged='false')
SHELF = dict(half='top', open='false', powered='false', waterlogged='false')


def stripe(i):
    return 'blue_wool' if i % 2 else 'light_blue_wool'


def market_stall():
    """Market stall. The stall is 3 wide and 3 deep (posts at x 2 and 4, z 1 and 3): the counter faces the street
    (front, z = 1), the trader stands behind it (3, 0, 2), reached through the open sides, and the back row holds the
    stock. Front posts are 2 high, back posts 3, so the awning steps down to the street,
    with one block of overhang all round; the awning is striped blue and light blue wool (KNOWN-OK colours).

    Level 1: posts, plank counter with a trapdoor front, the striped awning, the trader's chest and a barrel, a crate.
    Level 2 (open for trade): lanterns under the front eaves, produce stacked under the side eaves (pumpkin, hay,
    melon), a cake on the counter, a shelf over the stock. Level 3 (the shop): a lantern post with the shop sign by
    the street (text by the decoration step), a banner on the front post, flower pots, a stool for a customer, crates
    behind, a scraps bin, a bush and a flower."""
    s = Structure('market_stall', (7, 4, 6), palette='oak_a2')
    x0, x1, zc, zb = 2, 4, 1, 3                      # post columns, counter row, back row
    mid = 3
    s.at(1, 'shell')
    for x in (x0, x1):                               # front posts 2 high, back posts 3: the roof slopes forward
        for z, h in ((zc, 2), (zb, 3)):
            for y in range(h):
                put_id(s, x, y, z, 'spruce_log', axis='y')
    put_id(s, mid, 0, zc, 'spruce_planks')                       # the counter
    # awning: striped wool (blue and light blue: KNOWN-OK) in two steps, low over the counter, high at the back,
    # one block of overhang at the front, the sides and the back
    for x in range(x0 - 1, x1 + 2):
        for z in range(zc - 1, zb + 2):
            put_id(s, x, 2 if z <= zc else 3, z, stripe(x))
    s.at(1, 'fittings')
    put_id(s, mid, 0, zc - 1, 'spruce_trapdoor', facing='north', **OPEN)    # panel on the counter front
    put_id(s, mid, 0, zb, 'chest', facing='north', type='single', waterlogged='false')   # the trader's stock
    put_id(s, mid, 1, zb, 'barrel', facing='up', open='false')
    put_id(s, x1 + 1, 0, zb, 'barrel', facing='up', open='false')            # a crate by the right side, lidded
    put_id(s, x1 + 1, 1, zb, 'spruce_trapdoor', facing='north', **SHUT)

    s.at(2, 'flourish2')                                         # open for trade: goods, lights, a shelf
    for x in (x0 - 1, x1 + 1):                                   # lanterns hung under the front eave corners
        put_id(s, x, 1, zc, 'lantern', **HANG)
    put_id(s, x1 + 1, 0, zc, 'pumpkin')                          # produce stacked under the side eaves
    put_id(s, x1 + 1, 0, zc + 1, 'hay_block', axis='y')
    put_id(s, x0 - 1, 0, zc, 'melon')
    put_id(s, mid, 1, zc, 'cake', bites='0')                     # a cake on the counter
    put_id(s, mid, 2, zb, 'spruce_trapdoor', facing='north', **SHELF)       # shelf over the stock
    s.decorate(mid, 2, zb, 'item', item='minecraft:apple', note='apples on the shelf')

    s.at(3, 'flourish3')                                         # the shop: sign, banner, flowers, seating
    put_id(s, 0, 0, 0, 'spruce_log', axis='y')                   # lantern post with the shop sign, by the street
    put_id(s, 0, 1, 0, 'spruce_fence')
    put_id(s, 0, 2, 0, 'lantern', **STAND)
    put_id(s, 1, 0, 0, 'oak_wall_sign', facing='east', waterlogged='false')
    s.decorate(1, 0, 0, 'sign_text', lines=['', 'Fresh', 'Produce', ''])
    put_id(s, x1, 1, zc - 1, 'red_wall_banner', facing='north')  # banner on the right front post
    put_id(s, x0, 0, zc - 1, 'oak_stairs', facing='north', **STAIR)           # a stool for a customer
    for x, y, z, plant in ((x1 + 1, 1, zc + 1, 'red_tulip'), (x0 - 1, 1, zb + 1, 'dandelion')):   # flower pots
        if y == 1 and (x, 0, z) not in s.blocks:
            put_id(s, x, 0, z, 'barrel', facing='up', open='false')        # a crate under the pot
        put_id(s, x, y, z, 'flower_pot')
        s.decorate(x, y, z, 'potted_plant', plant=plant)
    put_id(s, x0 - 2, 0, zb + 1, 'barrel', facing='east', open='false')
    put_id(s, mid, 0, zb + 2, 'composter')                                   # scraps bin at the back
    put_id(s, x1 + 2, 0, zb + 1, 'oak_leaves', persistent='true', distance='1', waterlogged='false')   # a bush
    put_id(s, x1 + 2, 0, zb + 2, 'poppy')
    connect(s)
    return s


STRUCTURES = [dict(path='market_stall', name='Market stall', tier=3, zone='inner', role='market',
                   group='market_stall', terrain='flexible', door=None, make=market_stall,
                   changes='First version.',
                   description='Over budget: 61 blocks vs 17 (and 3 x 3 instead of 3 x 2, so the trader has a spot '
                               'behind the counter). Spruce-post stall with a plank counter under a stepped, striped '
                               'blue wool awning; trader enters from the side; L2 opens for trade (produce, cake, '
                               'lanterns, shelf), L3 adds the sign post, banner, flower pots, a stool and crates.')]

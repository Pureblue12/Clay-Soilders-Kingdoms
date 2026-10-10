"""Market stall (tier 3, look A, prop): a plank counter between spruce posts under a thin, sloped, striped awning."""
from make_batch1_structures import Structure
from revise1.blockconn import connect, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
HANG = dict(hanging='true', waterlogged='false')
STAND = dict(hanging='false', waterlogged='false')
OPEN = dict(half='bottom', open='true', powered='false', waterlogged='false')
SHUT = dict(half='bottom', open='false', powered='false', waterlogged='false')
SHELF = dict(half='top', open='false', powered='false', waterlogged='false')
DECK = dict(half='top', open='false', powered='false', waterlogged='false', facing='north')   # awning boards


def market_stall():
    """Market stall. The stall is 3 wide and 3 deep (posts at x 2 and 4, z 1 and 3): the counter faces the street
    (front, z = 1), the trader stands behind it (3, 0, 2), reached through the open sides, and the back row holds the
    stock. Front posts are 3 high, back posts 4, so the awning slopes down to the street.

    The awning is ONE block thick: spruce trapdoors (closed, top half) as boards over the front, spruce planks over the
    back (so the step between them is closed), with red and white carpet stripes laid on them, one block of overhang at the sides and the back; level 2 adds the front edge, a striped red and
    white wool valance one step lower, so the awning slopes down to the front in three steps.

    Level 1: posts, plank counter with a trapdoor front, the striped carpet awning, goods at the front corners (a
    barrel and a lidded crate either side of the counter), a sack of grain (hay) on the counter, a trade sign on the
    left post, the trader's chest and a barrel at the back.
    Level 2 (open for trade): the red and white wool front edge, lanterns hung under its corners, produce on the
    front crates (pumpkin, melon), more grain by the side, a shelf over the stock (apples by the decoration step).
    Level 3 (the shop): a lantern post with the shop sign by the street (text by the decoration step), a banner on the
    right post, a stool for a customer, a barrel and a scraps bin at the back, a bush and flowers."""
    s = Structure('market_stall', (7, 5, 6), palette='oak_a2')
    x0, x1, zc, zb = 2, 4, 1, 3                      # post columns, counter row, back row
    mid = 3
    s.at(1, 'shell')
    for x in (x0, x1):                               # front posts 3 high, back posts 4: the awning slopes forward
        for z, h in ((zc, 3), (zb, 4)):
            for y in range(h):
                put_id(s, x, y, z, 'spruce_log', axis='y')
    put_id(s, mid, 0, zc, 'spruce_planks')                       # the counter
    # the awning: trapdoor boards (top half) with carpet stripes on them; front part (z 1..2) lower than the back
    # (z 3..4); one block of overhang at the sides and the back
    for x in range(x0 - 1, x1 + 2):
        stripe = 'red_carpet' if x % 2 else 'white_carpet'
        for z in range(zc, zb + 2):
            y = 2 if z < zb else 3                               # board level (the carpet lies one above)
            if (x, y, z) not in s.blocks:                        # post tops carry the carpet themselves
                if z < zb:
                    put_id(s, x, y, z, 'spruce_trapdoor', **DECK)
                else:                                            # the back part on plank boards: no gap at the step
                    put_id(s, x, y, z, 'spruce_planks')
            put_id(s, x, y + 1, z, stripe)
    s.at(1, 'fittings')
    put_id(s, mid, 0, zc - 1, 'spruce_trapdoor', facing='north', **OPEN)    # panel on the counter front
    put_id(s, mid, 1, zc, 'hay_block', axis='x')                             # a sack of grain on the counter
    for x in (x0 - 1, x1 + 1):                                               # goods at the front corners
        put_id(s, x, 0, zc, 'barrel', facing='up', open='false')
    put_id(s, x1 + 1, 0, zc + 1, 'barrel', facing='up', open='false')        # a lidded crate behind the right one
    put_id(s, x1 + 1, 1, zc + 1, 'spruce_trapdoor', facing='north', **SHUT)
    put_id(s, x0, 1, zc - 1, 'oak_wall_sign', facing='north', waterlogged='false')   # trade sign on the left post
    s.decorate(x0, 1, zc - 1, 'sign_text', lines=['', 'Goods', 'for trade', ''])
    put_id(s, mid, 0, zb, 'chest', facing='north', type='single', waterlogged='false')   # the trader's stock
    put_id(s, mid, 1, zb, 'barrel', facing='up', open='false')

    s.at(2, 'flourish2')                                         # open for trade: front edge, lights, goods, a shelf
    for x in range(x0 - 1, x1 + 2):                              # red and white wool valance: the awning's front edge
        put_id(s, x, 2, zc - 1, 'red_wool' if x % 2 else 'white_wool')
    for x in (x0 - 1, x1 + 1):                                   # lanterns hung under its corners
        put_id(s, x, 1, zc - 1, 'lantern', **HANG)
    put_id(s, x0 - 1, 1, zc, 'pumpkin')                          # produce on the front crates
    put_id(s, x1 + 1, 1, zc, 'melon')
    put_id(s, x1 + 1, 0, zc + 2, 'hay_block', axis='z')          # more grain stacked by the side
    put_id(s, mid, 2, zb, 'spruce_trapdoor', facing='north', **SHELF)       # shelf over the stock
    s.decorate(mid, 2, zb, 'item', item='minecraft:apple', note='apples on the shelf')

    s.at(3, 'flourish3')                                         # the shop: sign, banner, seating, the back yard
    put_id(s, 0, 0, 0, 'spruce_log', axis='y')                   # lantern post with the shop sign, by the street
    put_id(s, 0, 1, 0, 'spruce_fence')
    put_id(s, 0, 2, 0, 'lantern', **STAND)
    put_id(s, 1, 0, 0, 'oak_wall_sign', facing='east', waterlogged='false')
    s.decorate(1, 0, 0, 'sign_text', lines=['', 'Fresh', 'Produce', ''])
    put_id(s, x1, 1, zc - 1, 'red_wall_banner', facing='north')  # banner on the right front post
    put_id(s, x1, 0, zc - 1, 'oak_stairs', facing='north', **STAIR)           # a stool for a customer
    put_id(s, x0 - 1, 0, zb, 'barrel', facing='east', open='false')           # back corner: a barrel on its side
    put_id(s, x0 - 1, 0, zb + 1, 'flower_pot')
    s.decorate(x0 - 1, 0, zb + 1, 'potted_plant', plant='red_tulip')
    put_id(s, mid, 0, zb + 2, 'composter')                                   # scraps bin at the back
    put_id(s, x1 + 2, 0, zb + 1, 'oak_leaves', persistent='true', distance='1', waterlogged='false')   # a bush
    put_id(s, x1 + 2, 0, zb + 2, 'poppy')
    put_id(s, x1 + 2, 0, zb, 'dandelion')
    connect(s)
    return s


STRUCTURES = [dict(path='market_stall', name='Market stall', tier=3, zone='inner', role='market',
                   group='market_stall', terrain='flexible', door=None, make=market_stall,
                   changes='Quality check: the awning is now one block thick (spruce trapdoor boards with red and '
                           'white carpet stripes instead of two layers of solid blue wool) and slopes down to the '
                           'front; level 2 adds a red and white wool front edge one step lower. Goods moved to the '
                           'front corners and level 1 now has goods too (barrels, a crate, grain on the counter, a '
                           'trade sign).',
                   description='Over budget: 83 blocks vs 17 (and 3 x 3 instead of 3 x 2, so the trader has a '
                               'spot behind the counter). Spruce-post stall with a plank counter under a thin, sloped '
                               'awning of red and white carpet stripes on trapdoor boards; trader enters from the '
                               'side; L1 has barrels, a crate, grain and a trade sign; L2 adds the striped wool front '
                               'edge, lanterns, produce, grain and a shelf; L3 the sign post, banner, stool, back '
                               'barrel, scraps bin and flowers.')]

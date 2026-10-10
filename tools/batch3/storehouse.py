"""Storehouse (tier 3, look A): a big jettied timber hall on a stone undercroft (the cellar), storage that grows per level."""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, HANG, LEAVES, connect, noise, gable_roof

SHUT = dict(half='bottom', open='false', powered='false', waterlogged='false')
X0, X1, U0, U1, Z0, Z1, MID = 2, 10, 3, 9, 2, 14, 6     # hall walls, undercroft walls, depth, door column
FLOOR, TOPW, PLATE = 3, 6, 7                              # hall floor layer, last hall wall layer, plates


def chest(s, x, y, z, facing):
    P(s, x, y, z, 'chest', facing=facing, type='single', waterlogged='false')


def barrel(s, x, y, z, facing='up'):
    P(s, x, y, z, 'barrel', facing=facing, open='false')


def storehouse():
    """Storehouse. The Structure can't go below layer 0 and builders never dig, so the cellar is a raised UNDERCROFT:
    layer 0 stands on the ground as usual, the stone undercroft (floor y 0, room y 1..2) carries the hall floor at
    y 3. The hall (x 2..10, z 2..14, 9 by 13) juts out one block over the undercroft (x 3..9) on both long sides, on
    stone corbels: Carlton's jetty. Hall walls y 4..5 (plaster in a dark oak frame), plates at y 6, a 45-degree spruce
    roof (ridge along z, cap at y 11: 12 tall, so it is marked "needs scaffolding").

    Ways in, all on foot: an outside stone stair along the front wall up to a landing and the hall door (x 6); a
    straight stair down inside the hall to the cellar (x 4..5, z 10..11, railed); a back cellar door at ground level
    for carts (x 6, z 14). L3 adds the loft over the hall (floor at the plate level y 6) and its stair (x 8).

    Level 1: hall and cellar, with some storage: a row of chests and barrels on each floor, the storekeeper's desk.
    Level 2: much more storage: free-standing racks of barrels on their sides in the hall, chests stacked two high,
    the cellar lined two high; the tie beams with lanterns, the lectern ledger; outside the hoist beam over the loft
    door and the covered loading awning on the right side with crates and barrels, door lanterns.
    Level 3: full: the loft floor and its stair with rows of chests under the roof, the cellar filled to the back; a
    lean-to of goods under the left jetty, a back yard (hay, crates, cart), sign, banners, flower boxes, flowers."""
    s = Structure('storehouse', (15, 13, 18), palette='oak_a2')

    # ---------------------------------------------------------------- level 1: shell
    s.at(1, 'shell')
    for x in range(U0, U1 + 1):                       # undercroft: base course, walls y 1..2, flagstone floor
        for z in range(Z0, Z1 + 1):
            edge = x in (U0, U1) or z in (Z0, Z1)
            if not edge:
                P(s, x, 0, z, 'stone_bricks')
                continue
            for y in range(0, 3):
                corner = x in (U0, U1) and z in (Z0, Z1)
                if corner:
                    P(s, x, y, z, 'stone_bricks')
                else:
                    P(s, x, y, z, 'mossy_cobblestone' if noise(x, y, z, 17) < 0.22 else 'cobblestone')
    vents = [(U0, 2, z) for z in (4, 8, 12)] + [(U1, 2, z) for z in (4, 8, 12)] + [(4, 2, Z1), (8, 2, Z1)]
    for v in vents:
        s.remove(*v)
    s.remove(MID, 1, Z1); s.remove(MID, 2, Z1)
    s.door(MID, 1, Z1, 'south', block='spruce_door')  # the cart door into the cellar
    P(s, MID, 0, Z1 + 1, 'cobblestone_stairs', facing='north', **STAIR)
    for z in (5, 8):                                  # stone pillars down the middle of the cellar
        for y in (1, 2):
            P(s, MID, y, z, 'stone_bricks')

    for x in range(X0, X1 + 1):                       # hall floor: sill beams round the edge, planks, joists
        for z in range(Z0, Z1 + 1):
            if z in (Z0, Z1):
                P(s, x, FLOOR, z, 'dark_oak_log', axis='x')
            elif x in (X0, X1):
                P(s, x, FLOOR, z, 'dark_oak_log', axis='z')
            elif z in (5, 8, 11):
                P(s, x, FLOOR, z, 'dark_oak_log', axis='x')   # joists, seen from the cellar
            else:
                P(s, x, FLOOR, z, 'spruce_planks' if x == MID else 'oak_planks')
    for x in (4, 5):                                  # the stair hole down to the cellar
        for z in (10, 11):
            s.remove(x, FLOOR, z)
    for z in (2, 6, 10, 14):                          # corbels under the jetty, both long sides
        P(s, X0, 2, z, 'stone_brick_stairs', facing='east', **TOP)
        P(s, X1, 2, z, 'stone_brick_stairs', facing='west', **TOP)

    posts = {(x, Z0) for x in (X0, 5, 7, X1)} | {(x, Z1) for x in (X0, MID, X1)} | \
        {(x, z) for x in (X0, X1) for z in (2, 6, 10, 14)}
    for y in range(4, TOPW + 1):                      # hall walls: dark oak frame, plaster infill
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                if not (x in (X0, X1) or z in (Z0, Z1)):
                    continue
                P(s, x, y, z, 'dark_oak_log' if (x, z) in posts else 'white_terracotta', axis='y')
    windows = [(X0, 5, z) for z in (4, 8, 12)] + [(X1, 5, z) for z in (4, 8, 12)] + [(4, 5, Z0), (8, 5, Z0),
                                                                                   (4, 5, Z1), (8, 5, Z1)]
    for w in windows:
        s.remove(*w)
    s.remove(MID, 4, Z0); s.remove(MID, 5, Z0)
    s.door(MID, 4, Z0, 'north', block='oak_door')
    for x in range(X0, X1 + 1):                       # gable-end plates (the eave plates come with the roof)
        for z in (Z0, Z1):
            P(s, x, PLATE, z, 'dark_oak_log', axis='x')
    s.door(MID, 8, Z0, 'north', block='spruce_door', hinge='right')    # the loft door in the front gable
    P(s, MID, 9, Z1, 'glass_pane', east='true', west='true', north='false', south='false', waterlogged='false')
    for wx, ix, f in ((X0, X0 + 1, 'east'), (X1, X1 - 1, 'west')):   # wall dormers over the middle of each side
        P(s, wx, 8, 7, 'dark_oak_log', axis='y'); P(s, wx, 8, 9, 'dark_oak_log', axis='y')
        P(s, wx, 8, 8, 'glass_pane', north='true', south='true', east='false', west='false', waterlogged='false')
        for x in (wx, ix):
            P(s, x, 9, 7, 'dark_oak_stairs', facing='south', **STAIR)
            P(s, x, 9, 9, 'dark_oak_stairs', facing='north', **STAIR)
            P(s, x, 9, 8, 'dark_oak_planks')
            P(s, x, 10, 8, 'spruce_slab', **SLAB)
    gable_roof(s, X0, X1, Z0, Z1, TOPW)

    # the outside stair along the front wall: three steps up from the left to a landing before the door
    for x in (5, 6, 7):                               # the landing on an arch: a niche with a cellar window behind
        for y in range(0, FLOOR + 1):
            if x == MID and y < 2:
                continue
            P(s, x, y, 1, 'stone_bricks' if y in (FLOOR, 2) else
              'mossy_cobblestone' if noise(x, y, 1, 4) < 0.25 else 'cobblestone')
    s.remove(MID, 1, Z0)
    for x, h in ((4, 2), (3, 1), (2, 0)):
        for y in range(0, h):
            P(s, x, y, 1, 'cobblestone')
        P(s, x, h, 1, 'stone_brick_stairs', facing='east', **STAIR)

    # the stair down to the cellar (x 4..5, from z 9 down toward the back)
    for x in (4, 5):
        P(s, x, 2, 10, 'spruce_stairs', facing='north', **STAIR)
        P(s, x, 1, 10, 'cobblestone')
        P(s, x, 1, 11, 'spruce_stairs', facing='north', **STAIR)

    # ---------------------------------------------------------------- level 1: fittings (some storage)
    s.at(1, 'fittings')
    for x, y, z in windows + vents + [(MID, 1, Z0)]:
        side = x in (X0, X1, U0, U1) and z not in (Z0, Z1)
        P(s, x, y, z, 'glass_pane', north=side, south=side, east=not side, west=not side, waterlogged='false')
    for x, y, z in windows:
        if x in (X0, X1):
            out, f = (x - 1, 'west') if x == X0 else (x + 1, 'east')
            for dz in (-1, 1):
                P(s, out, y, z + dz, 'spruce_trapdoor', facing=f, **OPEN)
    for x in (MID, 4, 5):                             # railing round the stair hole
        P(s, x, 4, 12, 'spruce_fence')
    for z in (10, 11):
        P(s, MID, 4, z, 'spruce_fence')
    for z in (10, 11, 12):                            # barrels on the other side of the hole (x 3)
        barrel(s, 3, 4, z)
    for z in range(3, 9):                             # hall: chests along the right wall
        chest(s, 9, 4, z, 'west')
    for z in range(3, 7):                             # barrels along the left wall, two chests after them
        barrel(s, 3, 4, z)
    for z in (7, 8):
        chest(s, 3, 4, z, 'east')
    P(s, 7, 4, 13, 'oak_fence'); P(s, 7, 5, 13, 'oak_pressure_plate', powered='false')     # the storekeeper's desk
    P(s, 7, 4, 12, 'oak_stairs', facing='north', **STAIR)                                  # his stool
    barrel(s, 9, 4, 13); barrel(s, 9, 4, 12)
    for z in range(3, 10):                            # cellar: barrels along the right wall
        barrel(s, 8, 1, z)
    for z in range(3, 7):                             # chests along the left
        chest(s, 4, 1, z, 'east')
    for x, z, f in ((MID, 4, 'north'), (MID, 9, 'south')):   # torches on the pillars
        P(s, x, 2, z, 'wall_torch', facing=f)
    for x in (5, 7):                                  # torches by the hall door, inside on the back wall
        P(s, x, 5, 1, 'wall_torch', facing='north')
    P(s, 5, 5, Z1 - 1, 'wall_torch', facing='north')

    # ---------------------------------------------------------------- level 2: many more chests and racks
    s.at(2, 'flourish2')
    for z in range(3, 9):                             # chests stacked two high on the right wall
        chest(s, 9, 5, z, 'west')
    for z in range(3, 7):
        barrel(s, 3, 5, z)
    for z in (7, 8):
        chest(s, 3, 5, z, 'east')
    for x in (5, 7):                                  # two free-standing racks of barrels on their sides
        P(s, x, 4, 3, 'spruce_fence'); P(s, x, 5, 3, 'spruce_fence')
        P(s, x, 4, 9, 'spruce_fence'); P(s, x, 5, 9, 'spruce_fence')
        for z in range(4, 9):
            barrel(s, x, 4, z, 'east' if x == 7 else 'west')
            barrel(s, x, 5, z, 'east' if x == 7 else 'west')
    for z in (5, 9):                                  # tie beams over the hall, lanterns under them
        for x in range(X0 + 1, X1):
            P(s, x, PLATE, z, 'dark_oak_log', axis='x')
        P(s, MID, PLATE - 1, z, 'lantern', **HANG)
    P(s, 8, 4, 13, 'lectern', facing='north', has_book='false', powered='false')   # the ledger (workstation)
    s.decorate(8, 4, 13, 'item', item='minecraft:writable_book', note='the ledger on the lectern')
    for z in range(3, 10):                            # cellar: barrels two high on the right
        barrel(s, 8, 2, z)
    for z in range(3, 9):                             # chests two high on the left
        if z >= 7:
            chest(s, 4, 1, z, 'east')
        chest(s, 4, 2, z, 'east')
    for z in (6, 7):                                  # a rack of barrels between the pillars
        barrel(s, MID, 1, z, 'east'); barrel(s, MID, 2, z, 'east')
    for z in (4, 9):                                  # lanterns hung from the joists
        P(s, 5, 2, z + (1 if z == 4 else -1), 'lantern', **HANG)
    # hoist beam out of the front gable over the loft door, a barrel on its chain
    P(s, MID, 10, 1, 'spruce_log', axis='z'); P(s, MID, 10, 0, 'spruce_log', axis='z')
    for y in (9, 8):
        P(s, MID, y, 0, 'chain', axis='y')
    barrel(s, MID, 7, 0)
    # the loading awning on the right side, under the jetty: a slab roof on posts, goods under it
    for z in (4, 8, 12):
        for y in range(0, 3):
            P(s, 13, y, z, 'spruce_fence')
    for x in (11, 12, 13):
        for z in range(4, 13):
            if x == 13:
                P(s, x, 3, z, 'spruce_stairs', facing='west', **STAIR)
            elif z in (4, 12):
                P(s, x, 3, z, 'spruce_stairs', facing='south' if z == 4 else 'north', **STAIR)
            else:
                P(s, x, 3, z, 'spruce_slab', **SLAB_TOP)
    for x, y, z in ((11, 0, 5), (12, 0, 5), (11, 1, 5), (12, 0, 6), (11, 0, 10), (12, 0, 10), (11, 1, 10),
                    (12, 0, 11), (11, 0, 11)):
        barrel(s, x, y, z, 'up' if y else 'east')
    for z in (7, 8, 9):                               # crates: plank boxes with trapdoor lids
        P(s, 12, 0, z, 'spruce_planks'); P(s, 12, 1, z, 'spruce_trapdoor', facing='east', **SHUT)
    P(s, 11, 0, 8, 'spruce_planks'); P(s, 11, 1, 8, 'spruce_trapdoor', facing='east', **SHUT)
    P(s, 12, 2, 8, 'lantern', **HANG)
    for x in (4, 8):                                  # lanterns by the hall door, on fence brackets from the plate
        P(s, x, PLATE, 1, 'spruce_fence')
        P(s, x, PLATE - 1, 1, 'lantern', **HANG)

    # ---------------------------------------------------------------- level 3: the loft, a full cellar, the yard
    s.at(3, 'flourish3')
    hole = {(8, 10), (8, 11)}
    for x in range(X0 + 1, X1):                       # the loft floor at the plate level
        for z in range(Z0 + 1, Z1):
            if (x, z) not in hole:
                A(s, x, PLATE, z, 'spruce_planks')
    for i, z in enumerate((9, 10, 11)):               # the loft stair (x 8, up toward the back), planks under it
        P(s, 8, 4 + i, z, 'spruce_stairs', facing='south', **STAIR)
        for y in range(4, 4 + i):
            P(s, 8, y, z, 'spruce_planks')
    P(s, 7, 8, 10, 'spruce_fence'); P(s, 7, 8, 11, 'spruce_fence')     # rails on the loft side of the hole
    for z in range(Z0 + 1, Z1):                       # loft: chests under the eaves on both sides
        chest(s, 3, 8, z, 'east')
        chest(s, 9, 8, z, 'west')
        if z <= 9:
            chest(s, 4, 8, z, 'east')
            chest(s, 8, 8, z, 'west')
    for z in (10, 11, 12):                            # cellar: chests to the back on the right
        chest(s, 8, 1, z, 'west'); chest(s, 8, 2, z, 'west')
    chest(s, 4, 1, 13, 'east'); chest(s, 4, 2, 13, 'east')
    for x in (5, 7):                                  # and across the front
        chest(s, x, 1, 3, 'south'); chest(s, x, 2, 3, 'south')
    # the left side: a lean-to of goods under the jetty (barrels, crates, firewood), a slab roof on posts
    for z in (3, 8, 13):
        for y in range(0, 3):
            P(s, 0, y, z, 'spruce_fence')
    for x in (0, 1):
        for z in range(3, 14):
            if x == 0:
                P(s, x, 3, z, 'spruce_stairs', facing='east', **STAIR)
            elif z in (3, 13):
                P(s, x, 3, z, 'spruce_stairs', facing='south' if z == 3 else 'north', **STAIR)
            else:
                P(s, x, 3, z, 'spruce_slab', **SLAB_TOP)
    P(s, 1, 2, 8, 'lantern', **HANG)
    for z, b in ((4, 'barrel'), (5, 'barrel'), (6, 'crate'), (7, 'crate'), (9, 'wood'), (10, 'wood'), (11, 'barrel')):
        if b == 'barrel':
            barrel(s, 2, 0, z, 'west'); barrel(s, 2, 1, z)
        elif b == 'crate':
            P(s, 2, 0, z, 'spruce_planks'); P(s, 2, 1, z, 'spruce_trapdoor', facing='west', **SHUT)
        else:
            P(s, 2, 0, z, 'spruce_log', axis='z'); P(s, 2, 1, z, 'spruce_log', axis='z')
    # the back yard: hay, crates, barrels, a cart
    for x, z, b in ((3, 16, 'hay'), (4, 16, 'hay'), (3, 17, 'hay'), (3, 16, 'hay2'), (9, 16, 'crate'),
                    (10, 16, 'barrel'), (10, 17, 'barrel'), (9, 17, 'crate'), (10, 16, 'barrel2')):
        if b == 'hay':
            P(s, x, 0, z, 'hay_block', axis='y')
        elif b == 'hay2':
            P(s, x, 1, z, 'hay_block', axis='x')
        elif b == 'crate':
            P(s, x, 0, z, 'spruce_planks'); P(s, x, 1, z, 'spruce_trapdoor', facing='south', **SHUT)
        elif b == 'barrel':
            barrel(s, x, 0, z)
        else:
            barrel(s, x, 1, z)
    s.decorate(7, 0, 16, 'hand_cart', note='a hand cart of sacks by the cellar door')
    P(s, MID, 6, 1, 'oak_wall_sign', facing='north', waterlogged='false')   # sign over the hall door
    s.decorate(MID, 6, 1, 'sign_text', lines=['', 'Storehouse', '', ''])
    for x in (X0, X1):                                # banners on the front corner posts
        P(s, x, 5, 1, 'red_wall_banner', facing='north')
    for x, z, side, flower in ((8, Z0 - 1, 'north', 'poppy'), (4, Z1 + 1, 'south', 'cornflower'),
                               (8, Z1 + 1, 'south', 'dandelion')):   # flower boxes under the front and back windows
        dz = -1 if side == 'north' else 1
        P(s, x, 4, z, 'coarse_dirt'); P(s, x, 4, z + dz, 'spruce_trapdoor', facing=side, **OPEN)
        P(s, x, 5, z, flower)
    # a fenced wood and hay yard behind the awning (open toward the cellar door)
    for x, z in [(14, z) for z in range(13, 18)] + [(x, 17) for x in range(11, 14)] + [(12, 13), (13, 13)]:
        P(s, x, 0, z, 'oak_fence')
    P(s, 14, 1, 13, 'oak_fence'); P(s, 14, 2, 13, 'lantern', hanging='false', waterlogged='false')
    for x in (12, 13):                                # a stack of logs and hay inside
        for z in (15, 16):
            P(s, x, 0, z, 'spruce_log', axis='x')
        P(s, x, 1, 15, 'spruce_log', axis='x')
    P(s, 13, 0, 14, 'hay_block', axis='y'); P(s, 12, 0, 14, 'barrel', facing='up', open='false')
    for x, z, b in ((0, 1, 'leaves'), (1, 0, 'poppy'), (0, 0, 'azure_bluet'), (0, 15, 'leaves'), (0, 17, 'dandelion'),
                    (8, 0, 'leaves'), (9, 0, 'poppy'), (10, 0, 'cornflower'), (14, 2, 'leaves'), (14, 10, 'poppy'),
                    (1, 16, 'leaves'), (14, 6, 'dandelion'), (2, 17, 'cornflower')):
        if b == 'leaves':
            P(s, x, 0, z, 'oak_leaves', **LEAVES)
        else:
            P(s, x, 0, z, b)
    connect(s)
    return s


STRUCTURES = [dict(path='storehouse', name='Storehouse', tier=3, zone='inner', role='storage', group='storehouse',
                   terrain='skirt', door='front', make=storehouse, max_height=13,
                   changes='Carlton: "needs to be larger, add a cellar, more chests, should scale with levels". Much '
                           'bigger (hall 9 by 13 instead of 8 by 9) and on two floors plus a loft. The cellar: the '
                           'mod cannot build below the ground (no layers under 0, builders never dig), so it is a '
                           'raised stone undercroft: the hall stands on it one storey up (reached by an outside stone '
                           'stair), and a railed stair inside goes down to it; carts also get in through a back door '
                           'at ground level. No ladders. Storage grows per level: L1 about 12 chests and 14 barrels; '
                           'L2 about 32 chests and 50 barrels (racks of barrels on their sides, everything two high, '
                           'the cellar lined); L3 adds the loft under the roof full of chests (its own stair) and fills '
                           'the cellar, over 70 chests. 12 tall: needs scaffolding (or a builder on the hall floor).',
                   description='Needs scaffolding: 12 tall (over the 9 rule; the roof is within reach from the hall '
                               'floor, so the planner may need none). Over budget on purpose (Carlton: bigger, more '
                               'storage). Jettied timber hall (plaster in a dark oak frame, spruce roof) on a stone '
                               'undercroft cellar; outside stone stair to the door, inside stair to the cellar, back '
                               'cart door. L1 some chests and barrels; L2 barrel racks, chests two high, hoist, loading '
                               'awning; L3 loft full of chests, cellar full, yard, flowers.')]

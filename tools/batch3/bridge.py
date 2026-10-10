"""Wooden bridge (tier 3, look A): a plank deck on log stringers and log piers, between cobblestone abutments."""
from make_batch1_structures import Structure
from revise1.blockconn import connect, noise, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
UPSIDE = dict(half='top', shape='straight', waterlogged='false')
BOTTOM = dict(type='bottom', waterlogged='false')
STAND = dict(hanging='false', waterlogged='false')
HANG = dict(hanging='true', waterlogged='false')

L = 13                        # z 0..12; the deck is 11 long (z 1..11), stair approaches at both ends
PIERS = (4, 8)


def bridge():
    """Wooden bridge (variant A). It spans along z (front bank at z = 0, far bank at z = 12); the river flows along x
    under it. y = 0 is the water level (the banks sit at the same height as the abutments' base, terrain `stepped`:
    each end on its own bank).

    - Abutments (z 0..1 and z 11..12): cobblestone on the bank, with a stair approach up to the deck (two steps).
    - Deck: oak planks 3 wide (x 1..3) at y 1, on dark oak log stringers along both edges (x 0 and 4), with spruce
      fence rails on the stringers and taller rail posts over the piers.
    - Piers (z 4 and 8): spruce log trestles standing in the river (x 0, 2, 4), a dark oak cross beam under the deck.
      In the world the piers go down to the river bed (to be added per site; shown here on flat ground).
    Level 1: abutments, deck, stringers, piers and the rails.
    Level 2: lanterns on the rail posts over the piers and at the ends, cobblestone wall cheeks on the abutments,
    upside-down stair corbels under the stringers at the piers.
    Level 3: standing banners at both ends, moss on the old planks and the piers, flower pots on the end posts, a
    coil of rope (decoration). The water itself is added by the decoration step (D54)."""
    s = Structure('bridge', (5, 5, L), palette='oak_a2')
    s.at(1, 'shell')
    for z0, z1, inward in ((0, 1, 'south'), (L - 2, L - 1, 'north')):
        outer, inner = (z0, z1) if inward == 'south' else (z1, z0)
        for z in (z0, z1):
            for x in range(5):
                if z == inner or x in (0, 4):
                    put_id(s, x, 0, z, 'mossy_cobblestone' if noise(x, 0, z, 2) < 0.2 else 'cobblestone')
        for x in range(1, 4):                       # two steps up: stairs on the bank, then on the abutment
            put_id(s, x, 0, outer, 'oak_stairs', facing=inward, **STAIR)
            put_id(s, x, 1, inner, 'oak_stairs', facing=inward, **STAIR)
    for z in range(2, L - 2):                       # deck
        for x in range(1, 4):
            put_id(s, x, 1, z, 'spruce_planks' if z in PIERS else 'oak_planks')   # spruce boards over the piers
    for z in range(1, L - 1):                       # stringers along both edges
        for x in (0, 4):
            put_id(s, x, 1, z, 'dark_oak_log', axis='z')
    for z in PIERS:                                 # piers: log trestles in the river, a cross beam under the deck
        for x in (0, 2, 4):
            put_id(s, x, 0, z, 'spruce_log', axis='y')
        for x in (1, 3):
            put_id(s, x, 0, z, 'dark_oak_log', axis='x')
    s.at(1, 'fittings')
    for z in range(2, L - 2):                       # the river under the span (decoration step: D54)
        for x in range(5):
            if (x, 0, z) not in s.blocks:
                s.decorate(x, 0, z, 'water', note='the river under the bridge')
    for z in range(1, L - 1):                       # rails on the stringers
        for x in (0, 4):
            put_id(s, x, 2, z, 'spruce_fence')
    for z in (1, L - 2) + PIERS:                    # taller rail posts at the ends and over the piers
        for x in (0, 4):
            put_id(s, x, 3, z, 'spruce_fence')

    s.at(2, 'flourish2')
    for x, z in ((0, 1), (4, 1), (0, L - 2), (4, L - 2), (4, PIERS[0]), (0, PIERS[1])):   # lanterns on the
        put_id(s, x, 4, z, 'lantern', **STAND)                                               # tall posts
    for z in (0, L - 1):                            # cobblestone wall cheeks at the approaches
        for x in (0, 4):
            put_id(s, x, 1, z, 'cobblestone_wall')
    for z in PIERS:                                 # corbels: upside-down stairs under the stringers at the piers
        for dz, f in ((-1, 'south'), (1, 'north')):
            for x in (0, 4):
                put_id(s, x, 0, z + dz, 'spruce_stairs', facing=f, **UPSIDE)

    s.at(3, 'flourish3')
    for z, rot in ((0, 8), (L - 1, 0)):            # standing banners on the cheeks, facing the road
        for x in (0, 4):
            put_id(s, x, 2, z, 'red_banner', rotation=rot)
    for x, z in ((1, 3), (3, 6), (2, 9), (1, 10)):  # moss on the old planks
        put_id(s, x, 2, z, 'moss_carpet')
    for x, z, plant in ((0, PIERS[0], 'red_tulip'), (4, PIERS[1], 'blue_orchid')):   # pots on the pier posts without lanterns
        put_id(s, x, 4, z, 'flower_pot')
        s.decorate(x, 4, z, 'potted_plant', plant=plant)
    s.decorate(3, 2, L - 3, 'item', item='minecraft:lead', note='a coil of rope by the far end')
    connect(s)
    return s


STRUCTURES = [dict(path='bridge', name='Wooden bridge', tier=3, zone='inner', role='utility', group='bridge',
                   terrain='stepped', door=None, make=bridge, changes='First version.',
                   description='Over budget: 143 blocks vs 96 (first build 115 vs 80). Wooden bridge, 3 wide and 11 '
                               'long, oak deck on dark oak stringers and two spruce log trestle piers in the river, '
                               'cobblestone abutments with two-step stair approaches, spruce fence rails; the water is '
                               'a decoration step. L2 adds lanterns on the tall posts, wall cheeks and corbels; L3 '
                               'banners at both ends, moss and flower pots. Piers stand at the water line here: in '
                               'the world they must reach the river bed.')]

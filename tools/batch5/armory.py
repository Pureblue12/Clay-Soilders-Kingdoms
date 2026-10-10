"""Armory (tier 6, docs/STRUCTURES.md "Armory"): a strong stone store by the keep where the guard's weapons and armour
are kept and handed out; the armorer sleeps in the loft.

Layout (x 0..12, z 0..12; front z = 0; y 0 is the stone base course, soldiers stand at y 1):
  - Hall: walls x 3..9, z 2..10 (7 x 9; inside 5 x 7), 3 high, stone bricks on a cobblestone plinth, stone buttresses
    at the corners; a stone brick stair gable roof on dark oak top plates with dark oak eaves, a cobblestone ridge, one
    block of overhang all round (9 tall).
  - Door (6, 2), spruce, in a dark oak frame; the aisle runs straight to the back rack, which stands under the loft and
    is the first thing seen through the door (the signature).
  - Inside: weapon racks (fences with a trapdoor shelf) on the left wall and the back wall, footlockers (chests) and
    barrels, the grindstone (workstation) by the door; a spruce stair up the right side to the loft (z 7..9, stand
    y 5) with the armorer's bed and the armour chests behind a railing.
Level 2 (in service): the repair forge, a lean-to on the right side (x 10..12) with a blast furnace under a
cobblestone chimney, the smithing table and an anvil; iron bars in the slits, lanterns on fence brackets by the door,
banners, carpets, a hatch from the hall to the forge.
Level 3 (the arsenal): an open-air rack along the left wall under the eaves (fences, shelves, armour stands and shields
by the decoration step), a hitching rail for barded horses with hay, flower planters by the door, chimney smoke, ivy
and moss, candles in the loft, armour stands and shields inside (decoration step).
"""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, connect, noise, gable_roof
from batch5.mil_kit import masonry, vine

W, D, H = 13, 13, 9
X0, X1, Z0, Z1 = 3, 9, 2, 10
MID = 6
LOFT_Z = 7


def armory():
    s = Structure('armory', (W, H, D), palette='oak_a2')
    s.at(1, 'shell')
    for x in range(X0, X1 + 1):                                      # plinth and floor
        for z in range(Z0, Z1 + 1):
            edge = x in (X0, X1) or z in (Z0, Z1)
            if edge:
                P(s, x, 0, z, 'mossy_cobblestone' if noise(x, 0, z, 4) < 0.35 else 'cobblestone')
            else:
                P(s, x, 0, z, 'stone_bricks' if (x + z) % 3 else 'cobblestone')
    for y in range(1, 4):                                            # stone walls, quoins at the corners
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                if not (x in (X0, X1) or z in (Z0, Z1)):
                    continue
                corner = x in (X0, X1) and z in (Z0, Z1)
                P(s, x, y, z, 'stone_bricks' if corner else masonry(x, y, z, 8))
    # door in a dark oak frame
    for y in (1, 2):
        P(s, MID - 1, y, Z0, 'dark_oak_log', axis='y'); P(s, MID + 1, y, Z0, 'dark_oak_log', axis='y')
        s.remove(MID, y, Z0)
    for x in (MID - 1, MID, MID + 1):
        P(s, x, 3, Z0, 'dark_oak_log', axis='x')
    windows = []
    slits = [(X0, 2, 4), (X0, 2, 8), (X1, 2, 4), (X1, 2, 8), (X1, 2, 6), (MID, 2, Z1)]   # windows, forge hatch
    for p in windows + slits:
        s.remove(*p)
    # loft floor (z 7..9) on a dark oak beam
    for x in range(X0 + 1, X1):
        P(s, x, 4, LOFT_Z, 'dark_oak_log', axis='x')
        for z in range(LOFT_Z + 1, Z1):
            P(s, x, 4, z, 'spruce_planks')
    for x in range(X0, X1 + 1):                                      # top plates over the gable walls
        for z in (Z0, Z1):
            A(s, x, 4, z, 'dark_oak_log', axis='x')
    gable_roof(s, X0, X1, Z0, Z1, 3, front=1, back=1, slope='spruce_stairs', eave='stone_brick_stairs',
               ridge='dark_oak_planks', cap='spruce_slab', gable='stone_bricks', plates='dark_oak_log')
    for z in (Z0, Z1):                                               # gable windows: the loft at the back, the hall
        s.remove(MID, 5, z)
    # buttresses at the four corners (front and back faces) and halfway along the left side
    for x in (X0, X1):
        for z, f in ((Z0 - 1, 'south'), (Z1 + 1, 'north')):
            P(s, x, 0, z, 'cobblestone')
            P(s, x, 1, z, 'stone_brick_stairs', facing=f, **STAIR)
    P(s, X0 - 1, 0, 6, 'cobblestone')
    P(s, X0 - 1, 1, 6, 'stone_brick_stairs', facing='east', **STAIR)
    P(s, MID, 0, Z0 - 1, 'cobblestone_stairs', facing='south', **STAIR)   # the step at the door

    # ------------------------------------------------------------------ L1 fittings
    s.at(1, 'fittings')
    s.door(MID, 1, Z0, 'north', block='spruce_door', hinge='left')
    for p in windows:
        P(s, *p, 'glass_pane')
    P(s, MID, 5, Z0, 'glass_pane'); P(s, MID, 5, Z1, 'glass_pane')
    # stair up the right side to the loft: (7, 3) y1 .. (7, 6) y4, then the loft
    for i, z in enumerate(range(Z0 + 1, LOFT_Z)):
        P(s, MID + 1, 1 + i, z, 'spruce_stairs', facing='south', **STAIR)
    for x in range(X0 + 1, MID + 1):                                 # loft railing (the stair arrives at x 7)
        P(s, x, 5, LOFT_Z, 'spruce_fence')
    P(s, X1 - 1, 5, LOFT_Z, 'spruce_fence')
    # the back rack under the loft, seen through the door: fences two high, a shelf on top
    for x in (MID - 1, MID, MID + 1):
        P(s, x, 1, Z1 - 1, 'spruce_fence'); P(s, x, 2, Z1 - 1, 'spruce_fence')
        P(s, x, 3, Z1 - 1, 'spruce_trapdoor', facing='north', **SHELF)
    # left wall: a weapon rack and a barrel of spears; footlockers under the loft
    P(s, X0 + 1, 1, 3, 'barrel', facing='up', open='false')
    for z in (4, 5, 6):
        P(s, X0 + 1, 1, z, 'spruce_fence'); P(s, X0 + 1, 2, z, 'spruce_fence')
        P(s, X0 + 1, 3, z, 'spruce_trapdoor', facing='east', **SHELF)
    for z in (LOFT_Z, LOFT_Z + 1, Z1 - 1):
        P(s, X0 + 1, 1, z, 'chest', facing='east', type='single', waterlogged='false')
        P(s, X1 - 1, 1, z, 'chest', facing='west', type='single', waterlogged='false')
    P(s, X1 - 1, 1, 3, 'grindstone', face='floor', facing='north')   # the workstation, by the door
    P(s, X1 - 1, 1, 4, 'barrel', facing='up', open='false')
    P(s, X1 - 1, 1, 5, 'barrel', facing='up', open='false')
    # loft: the armorer's bed and the armour chests
    s.bed(X0 + 1, 5, LOFT_Z + 1, 'south')
    for x in (MID - 1, MID):
        P(s, x, 5, Z1 - 1, 'chest', facing='north', type='single', waterlogged='false')
    P(s, X1 - 1, 5, Z1 - 1, 'barrel', facing='up', open='false')
    P(s, X1 - 1, 5, LOFT_Z + 1, 'chest', facing='west', type='single', waterlogged='false')
    # light
    P(s, MID - 1, 3, Z0 + 1, 'wall_torch', facing='south')

    # ------------------------------------------------------------------ L2: in service
    s.at(2, 'flourish2')
    for p in slits:
        P(s, *p, 'iron_bars')
    # the repair forge: a lean-to on the right side, x 10..12, z 4..8
    for x in range(X1 + 1, W):
        for z in range(4, 9):
            P(s, x, 0, z, 'cobblestone' if (x + z) % 2 else 'gravel')
        for z in range(3, 10):
            P(s, x, 3, z, 'spruce_slab', **SLAB)
    for z in (4, 8):
        P(s, W - 1, 1, z, 'spruce_log', axis='y'); P(s, W - 1, 2, z, 'spruce_log', axis='y')
    P(s, X1 + 1, 1, 5, 'smithing_table')
    P(s, X1 + 1, 1, 6, 'anvil', facing='north')
    P(s, X1 + 1, 1, 7, 'blast_furnace', facing='east', lit='false')
    P(s, X1 + 1, 1, 8, 'barrel', facing='up', open='false')
    # the forge chimney behind the lean-to
    for y in range(1, 6):
        P(s, X1 + 2, y, 9, 'cobblestone' if noise(11, y, 9, 2) < 0.6 else 'mossy_cobblestone')
    P(s, X1 + 2, 0, 9, 'cobblestone')
    P(s, X1 + 2, 2, 6, 'lantern', **HANG)                            # hangs from the lean-to roof
    # the door: lanterns hanging from fence brackets, banners beside it
    for x in (MID - 1, MID + 1):
        P(s, x, 3, Z0 - 1, 'spruce_fence')
        P(s, x, 2, Z0 - 1, 'lantern', **HANG)
    for x in (X0 + 1, X1 - 1):
        P(s, x, 3, Z0 - 1, 'red_wall_banner', facing='north')
        s.decorate(x, 3, Z0 - 1, 'banner_pattern', banner='red_wall_banner', note='crossed swords of the armory')
    # inside: a runner, a lantern under the loft, a lantern in the loft
    for z in range(Z0 + 1, Z1 - 1):
        P(s, MID, 1, z, 'red_carpet')
    P(s, MID, 3, LOFT_Z, 'lantern', **HANG)
    P(s, X0 + 2, 5, LOFT_Z + 1, 'brown_carpet'); P(s, X0 + 3, 5, LOFT_Z + 1, 'brown_carpet')

    # ------------------------------------------------------------------ L3: the arsenal
    s.at(3, 'flourish3')
    # an open-air rack along the left wall under the eaves: fences on the ground, a shelf, armour stands
    for z in (4, 5, 7, 8):
        P(s, X0 - 1, 0, z, 'spruce_fence'); P(s, X0 - 1, 1, z, 'spruce_fence')
        P(s, X0 - 1, 2, z, 'spruce_trapdoor', facing='west', **SHELF)
    for z in (4, 8):
        s.decorate(X0 - 2, 0, z, 'armor_stand', note='iron armour on show')
    s.decorate(X0 - 1, 2, 6, 'item_frame', item='minecraft:shield', facing='west', note='shield on the buttress')
    # a hitching rail for the guard's barded horses: a rail, hay, a trough (water: decoration step)
    for z in (5, 6, 7):
        P(s, 0, 0, z, 'spruce_fence')
    P(s, 0, 0, 4, 'hay_block', axis='y')
    P(s, 0, 0, 8, 'cauldron')
    # a lamp post at the forge corner and a banner on the lean-to post
    P(s, W - 1, 0, 2, 'stone_brick_wall'); P(s, W - 1, 1, 2, 'stone_brick_wall')
    P(s, W - 1, 2, 2, 'lantern', **STAND)
    P(s, W - 1, 2, 3, 'red_wall_banner', facing='north')
    s.decorate(W - 1, 2, 3, 'banner_pattern', banner='red_wall_banner', note='crossed swords of the armory')
    for (x, z) in ((W - 2, 9), (W - 1, 9)):                          # crates of new arms by the chimney
        P(s, x, 0, z + 1, 'barrel', facing='up', open='false')
    P(s, W - 1, 1, 10, 'barrel', facing='up', open='false')
    # planters beside the door, chimney smoke, ivy and moss
    for x, f in ((MID - 2, 'poppy'), (MID + 2, 'cornflower')):
        P(s, x, 0, Z0 - 1, 'coarse_dirt')
        P(s, x, 1, Z0 - 1, f)
        P(s, x, 0, Z0 - 2, 'spruce_trapdoor', facing='north', **OPEN)
    P(s, X1 + 2, 6, 9, 'campfire', facing='north', lit='true', signal_fire='false', waterlogged='false')
    for (x, z, side) in ((X0 - 1, 3, 'east'), (X0 - 1, 9, 'east'), (4, Z1 + 1, 'north'), (8, Z1 + 1, 'north')):
        for y in (1, 2):
            vine(s, x, y, z, side)
    for x, z in ((X0 + 2, 4), (X0 + 2, 6)):
        s.decorate(x, 1, z, 'armor_stand', note='a guard\'s armour ready to hand out')
    for z in (4, 5, 6):
        s.decorate(X0 + 1, 2, z, 'item_frame', item='minecraft:iron_sword', facing='east', note='swords on the rack')
    s.decorate(MID, 2, Z1 - 1, 'item_frame', item='minecraft:shield', facing='north', note='shields on the back rack')
    return connect(s)


STRUCTURES = [dict(path='armory', name='Armory', tier=6, zone='inner', role='military', group='armory',
                   terrain='ground_level', door='front', make=armory, changes='First version.',
                   description='Over budget: 486 blocks vs 310. Roof spruce, not stone brick (an all-grey roof read as one block). Strong stone store for the guard\'s weapons and armour: racks visible through the '
                               'door, footlockers, grindstone workstation, a stair to the armorer\'s loft; L2 adds the '
                               'repair forge lean-to (smithing table, anvil, blast furnace), L3 an outdoor arms rack.')]

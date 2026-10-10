"""King's guard quarters (tier 6, docs/STRUCTURES.md "King's guard quarters"): a compact stone barracks house in the
keep zone for 8 guards, with a mess room below, a dormitory under the roof and a guard post at the door.

Kept small on purpose (Carlton on the barracks: "bloated, slim down and add more detail"): the brief's 7 x 9.

Layout (x 0..10, z 0..12; front z = 0; y 0 is the stone base course, soldiers stand at y 1):
  - Walls x 1..7, z 2..10 (inside 5 x 7), 3 high: stone bricks on a cobblestone plinth, dark oak corner posts.
  - The front gable juts out one block (z 1) on dark oak brackets: white plaster in a dark oak frame. Under it, by
    the door, the guard post: a paved porch with the sentry's stool and a lantern.
  - Roof: spruce stairs at 45 degrees on dark oak top plates, dark oak eaves and ridge, one block of overhang, a
    brick chimney at the back (9 tall).
  - Ground floor, the mess room: the long table with a bench, the hearth (a furnace in the back wall), the weapon
    rack on the right wall, barrels of rations; a spruce stair up the right side (x 5, rising to the front).
  - Under the roof, the dormitory: 8 beds in two rows of 4 heads to the eaves, a centre aisle, footlockers.
Level 2 (in service): banners on the jetty, lanterns, shutters on the side windows, rugs, shelves, a rack of spears
at the guard post, flower boxes under the front windows.
Level 3 (the king's own guard): a drill corner beside the house (a straw dummy, an archery target, a bench), shields
on the jetty and armour stands (decoration step), smoke from the chimney, ivy, a lamp post at the path.
"""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, connect, noise, gable_roof
from batch5.mil_kit import masonry, vine

W, D, H = 11, 13, 9
X0, X1, Z0, Z1 = 1, 7, 2, 10
MID = 4
JZ = 1                      # the jettied front gable
CHIM = 3                    # chimney column (x), behind the back wall at z 11


def guard_quarters():
    s = Structure('guard_quarters', (W, H, D), palette='oak_a2')
    s.at(1, 'shell')
    for x in range(X0, X1 + 1):                                      # plinth and floor
        for z in range(Z0, Z1 + 1):
            if x in (X0, X1) or z in (Z0, Z1):
                P(s, x, 0, z, 'mossy_cobblestone' if noise(x, 0, z, 6) < 0.3 else 'cobblestone')
            else:
                P(s, x, 0, z, 'spruce_planks')
    for y in range(1, 4):                                            # stone walls, dark oak corner posts
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                if not (x in (X0, X1) or z in (Z0, Z1)):
                    continue
                corner = x in (X0, X1) and z in (Z0, Z1)
                P(s, x, y, z, 'dark_oak_log' if corner else masonry(x, y, z, 21), **({'axis': 'y'} if corner else {}))
    s.remove(MID, 1, Z0); s.remove(MID, 2, Z0)
    windows = [(2, 2, Z0), (6, 2, Z0), (X0, 2, 4), (X0, 2, 8), (X1, 2, 8)]
    for p in windows:
        s.remove(*p)
    s.remove(CHIM, 1, Z1)                                            # the hearth goes into the back wall
    # the dormitory floor, the jetty beam and the back beam
    for x in range(X0, X1 + 1):
        P(s, x, 4, JZ, 'dark_oak_log', axis='x')
        P(s, x, 4, Z1, 'dark_oak_log', axis='x')
    for x in range(X0 + 1, X1):
        for z in range(Z0, Z1):
            if (x, z) not in ((5, 7), (5, 8), (5, 6)):
                P(s, x, 4, z, 'spruce_planks')
    # gable frames: king posts and windows first, the roof kit fills the rest with plaster
    for z in (JZ, Z1):
        P(s, MID, 5, z, 'dark_oak_log', axis='y')
        P(s, MID, 6, z, 'glass_pane' if z == JZ else 'dark_oak_log', **({} if z == JZ else {'axis': 'y'}))
        for x in (2, 6):
            P(s, x, 5, z, 'glass_pane')
    for y in range(0, 8):                                            # brick chimney behind the back wall
        P(s, CHIM, y, Z1 + 1, 'bricks')
    gable_roof(s, X0, X1, JZ, Z1, 3, front=1, back=1, gable='white_terracotta', corbels=False,
               skip={(CHIM, y, Z1 + 1) for y in range(9)})
    for x in (X0, X1):                                               # brackets under the jetty
        P(s, x, 3, JZ, 'dark_oak_stairs', facing='south', **TOP)
    for x in range(X0 + 1, X1):                                      # the porch (guard post) under the jetty
        P(s, x, 0, JZ, 'cobblestone' if x != MID else 'stone_bricks')
    P(s, MID, 0, 0, 'cobblestone_stairs', facing='south', **STAIR)

    # ------------------------------------------------------------------ L1 fittings
    s.at(1, 'fittings')
    s.door(MID, 1, Z0, 'north', block='spruce_door', hinge='left')
    for p in windows:
        P(s, *p, 'glass_pane')
    P(s, CHIM, 1, Z1, 'furnace', facing='north', lit='false')          # the hearth
    # staircase up the right side, rising to the front: (5, 9) y1 .. (5, 6) y4
    for i, z in enumerate((9, 8, 7, 6)):
        P(s, 5, 1 + i, z, 'spruce_stairs', facing='north', **STAIR)
    # mess room: the long table and its bench, rations, the weapon rack
    for z in (4, 5, 6):
        P(s, 3, 1, z, 'oak_slab', **SLAB_TOP)
        P(s, 2, 1, z, 'spruce_stairs', facing='west', **STAIR)
    P(s, 3, 1, 7, 'spruce_stairs', facing='south', **STAIR)          # the sergeant's chair at the table's end
    P(s, 2, 1, 9, 'barrel', facing='up', open='false')
    P(s, 2, 1, 8, 'barrel', facing='up', open='false')
    P(s, 2, 2, 9, 'barrel', facing='up', open='false')
    for z in (3, 4, 5):                                              # weapon rack on the right wall
        P(s, 6, 1, z, 'spruce_fence'); P(s, 6, 2, z, 'spruce_fence')
        P(s, 6, 3, z, 'spruce_trapdoor', facing='west', **SHELF)
    P(s, 6, 1, 7, 'chest', facing='west', type='single', waterlogged='false')   # under the stair
    P(s, 6, 1, 9, 'chest', facing='west', type='single', waterlogged='false')
    P(s, 2, 2, 3, 'wall_torch', facing='east')
    P(s, 4, 3, 9, 'wall_torch', facing='north')
    # the guard post under the jetty: the sentry's stool
    P(s, 6, 1, JZ, 'spruce_stairs', facing='east', **STAIR)
    # dormitory: 8 beds in two rows, heads to the eaves; footlockers
    for z in (2, 3, 4, 5):
        s.bed(3, 5, z, 'west')
        s.bed(5, 5, z, 'east')
    for x, z in ((2, 6), (2, 7), (2, 8), (6, 9)):
        P(s, x, 5, z, 'chest', facing='east' if x == 2 else 'west', type='single', waterlogged='false')
    P(s, 2, 5, 9, 'barrel', facing='up', open='false')
    P(s, MID, 6, 4, 'lantern', **HANG)                               # under the ridge

    # ------------------------------------------------------------------ L2: in service
    s.at(2, 'flourish2')
    for x in (3, 5):
        P(s, x, 4, 0, 'red_wall_banner', facing='north')
        s.decorate(x, 4, 0, 'banner_pattern', banner='red_wall_banner', note='the king\'s colours')
    P(s, MID, 3, JZ, 'lantern', **HANG)                              # under the jetty, over the door
    for (x, y, z, side) in ((X0, 2, 4, 'west'), (X0, 2, 8, 'west'), (X1, 2, 8, 'east')):
        dx = -1 if side == 'west' else 1
        for dz in (-1, 1):
            P(s, x + dx, y, z + dz, 'spruce_trapdoor', facing=side, **OPEN)
    P(s, 2, 1, JZ, 'barrel', facing='up', open='false')              # the sentry's gear at the post
    for z in range(Z0 + 1, Z1):                                      # rugs
        P(s, MID, 5, z, 'red_carpet')
    P(s, MID, 1, 3, 'red_carpet'); P(s, MID, 1, 4, 'red_carpet')
    P(s, 2, 3, 5, 'spruce_trapdoor', facing='east', **SHELF)         # a shelf over the table
    P(s, 2, 3, 6, 'spruce_trapdoor', facing='east', **SHELF)
    P(s, MID, 3, 6, 'lantern', **HANG)                               # over the table, from the dormitory floor
    P(s, CHIM - 1, 0, Z1 + 1, 'brick_stairs', facing='east', **STAIR)
    P(s, CHIM + 1, 0, Z1 + 1, 'brick_stairs', facing='west', **STAIR)

    # ------------------------------------------------------------------ L3: the king's own guard
    s.at(3, 'flourish3')
    # the drill corner beside the house (x 9..10): a straw dummy, an archery target, a bench
    P(s, 9, 0, 4, 'spruce_fence'); P(s, 9, 1, 4, 'hay_block', axis='y'); P(s, 9, 2, 4, 'carved_pumpkin', facing='west')
    P(s, 10, 0, 8, 'hay_block', axis='y'); P(s, 10, 1, 8, 'target', power='0')
    for z in (5, 6):
        P(s, 10, 0, z, 'spruce_stairs', facing='east', **STAIR)
    P(s, 10, 0, 3, 'barrel', facing='up', open='false')
    s.decorate(9, 0, 7, 'armor_stand', note='a practice dummy in old armour')
    # shields on the jetty, armour by the beds (decoration step)
    s.decorate(X1 + 1, 2, 4, 'item_frame', item='minecraft:shield', facing='east', note='shield on the side wall')
    s.decorate(MID, 5, 8, 'armor_stand', note='the captain\'s armour in the dormitory')
    P(s, CHIM, 8, Z1 + 1, 'campfire', facing='north', lit='true', signal_fire='false', waterlogged='false')
    for (x, z, side) in ((X0 - 1, 10, 'east'), (X0 - 1, 2, 'east'), (X1 + 1, 10, 'west'), (2, Z1 + 1, 'north')):
        for y in (1, 2):
            vine(s, x, y, z, side)
    P(s, 0, 0, 0, 'stone_brick_wall'); P(s, 0, 1, 0, 'stone_brick_wall'); P(s, 0, 2, 0, 'lantern', **STAND)
    P(s, 8, 0, 1, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    return connect(s)


STRUCTURES = [dict(path='guard_quarters', name="King's guard quarters", tier=6, zone='keep', role='military',
                   group='guard_quarters', terrain='ground_level', door='front', make=guard_quarters,
                   changes='First version.',
                   description='Compact stone barracks house for 8 guards (the brief\'s 7 x 9): mess room with long '
                               'table, hearth and weapon rack below, 8 beds in two rows under the roof by a stair, a '
                               'guard post under the jettied plaster gable; L2 banners, lanterns, shutters; L3 a drill '
                               'corner with a straw dummy and target.')]

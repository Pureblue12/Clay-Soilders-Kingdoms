"""Gatehouse (tier 5, the main gate, docs/STRUCTURES.md "Gatehouse and gates"): twin stone towers flanking an arched
passage, a gate room over the passage, crenellated fighting tops; level 3 crowns both towers with steep cones.

It slots into a line of batch-3 stone wall modules (tools/batch3/stone_wall.py): their profile is parapet (outside)
+ 2-wide walk, with the walk standing at y 5 and merlons at y 6. Here that wall line is z 2..4: z 2 is the wall's
outer face, z 3..4 the walk. A wall module joins each tower's outer side (x 1 and x 13) and its walk enters the tower
through a doorway at z 3, stand y 5. The towers stand 1 out from the wall face (z 1) and the gate block 2 behind it
(z 6); x 0, x 14 and z 0 are a margin for the cone eaves, the plinth and the front steps (air below them).

Plan (x 0..14, z 0..8; y 0 is the stone base course, soldiers stand on it at y 1):
  - Towers x 1..5 (left) and x 9..13 (right), z 1..5; inside 3 x 3. Floors at y 4 and y 8: ground room (stand 1),
    wall-walk room (stand 5), fighting top (stand 9) behind a parapet with merlons at y 10.
  - Passage x 6..8, z 1..6, 3 high (y 1..3), arched at both ends, paved; steps down at z 0 and z 7.
  - Gate room over the passage x 6..8, z 3..5 (stand 5, 2 high) between the two wall-walk rooms; its roof is a
    terrace (stand 8) with a corbelled parapet over the gate front.
  - Stairs (no ladders): left tower: door from the passage (5, 4), stone stair up the front and left to the wall-walk
    room, a spruce stair up the back to the fighting top. Right tower: door from the passage, guard room with two
    beds; a spruce stair from its wall-walk room to its fighting top. From both tops a step leads onto the terrace.
"""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, LEAVES, connect, noise
from batch5.mil_kit import masonry, ring_stairs, ring, cone, vine

W, D, H = 15, 9, 19
TOWERS = ((1, 5), (9, 13))          # x ranges; both z 1..5
TZ0, TZ1 = 1, 5
GX0, GX1 = 6, 8                     # passage
CZ0, CZ1 = 2, 6                     # gate block front / back walls
WALK = 5                            # stand height of the wall walk (stone_wall.WALK)
TOP_FLOOR = 8                       # tower top floor (stand 9)
TERRACE = 7                         # gate room roof (stand 8)
SLITS = [(3, 2, 1), (11, 2, 1), (3, 6, 1), (11, 6, 1), (7, 5, CZ0), (5, 2, 3), (9, 2, 3), (2, 6, 5), (12, 6, 5),
         (12, 2, 5)]


def tower_shell(s, x0, x1, seed):
    for y in range(0, TOP_FLOOR + 2):
        for x in range(x0, x1 + 1):
            for z in range(TZ0, TZ1 + 1):
                if not (x in (x0, x1) or z in (TZ0, TZ1)):
                    continue
                corner = x in (x0, x1) and z in (TZ0, TZ1)
                if y in (4, TOP_FLOOR) and not corner:
                    P(s, x, y, z, 'cobblestone')                     # string courses at the floors
                elif corner and y > 0:
                    P(s, x, y, z, 'stone_bricks')                    # quoins
                else:
                    P(s, x, y, z, masonry(x, y, z, seed))
    for x in range(x0, x1 + 1):                                      # merlons: corners and middles
        for z in range(TZ0, TZ1 + 1):
            if (x in (x0, x1) or z in (TZ0, TZ1)) and (x + z) % 2 == 0:
                P(s, x, TOP_FLOOR + 2, z, 'stone_bricks')
    for x in range(x0 + 1, x1):                                      # ground floor
        for z in range(TZ0 + 1, TZ1):
            P(s, x, 0, z, 'cobblestone')


def gatehouse():
    s = Structure('gatehouse', (W, H, D), palette='oak_a2')
    (lx0, lx1), (rx0, rx1) = TOWERS
    s.at(1, 'shell')
    tower_shell(s, lx0, lx1, 5)
    tower_shell(s, rx0, rx1, 9)

    # floors: left tower has the stairwell holes, right tower is solid at y 4
    for x in range(lx0 + 1, lx1):
        for z in range(TZ0 + 1, TZ1):
            if (x, z) not in ((4, 2), (3, 2), (2, 2)):
                P(s, x, 4, z, 'spruce_planks')
            if (x, z) not in ((3, 4), (4, 4), (4, 3)):
                P(s, x, TOP_FLOOR, z, 'spruce_planks' if (x + z) % 2 else 'stone_bricks')
    for x in range(rx0 + 1, rx1):
        for z in range(TZ0 + 1, TZ1):
            P(s, x, 4, z, 'spruce_planks')
            if (x, z) not in ((11, 4), (10, 4), (10, 3)):
                P(s, x, TOP_FLOOR, z, 'spruce_planks' if (x + z) % 2 else 'stone_bricks')

    # gate block: front wall z 2 and back wall z 6 (x 5..9 at the back), up to the terrace floor
    for y in range(0, TERRACE + 1):
        for x in range(GX0, GX1 + 1):
            P(s, x, y, CZ0, masonry(x, y, CZ0, 11) if y < TERRACE else 'stone_bricks')
        for x in range(lx1, rx0 + 1):
            if y <= 4 or x in (lx1, rx0):
                P(s, x, y, CZ1, 'stone_bricks' if x in (lx1, rx0) and y > 0 else masonry(x, y, CZ1, 13))
    for y in (5, 6):                                                 # the gate room's town face: a timber frame
        P(s, 6, y, CZ1, 'white_terracotta'); P(s, 8, y, CZ1, 'white_terracotta')
        P(s, 7, y, CZ1, 'white_terracotta' if y == 6 else 'glass_pane')
    for x in range(lx1, rx0 + 1):
        P(s, x, TERRACE, CZ1, 'dark_oak_log', axis='x')              # a beam under the back parapet
    for x in range(GX0, GX1 + 1):
        for z in range(CZ0 + 1, CZ1):
            P(s, x, 4, z, 'spruce_planks')                           # gate room floor = passage ceiling
            P(s, x, TERRACE, z, 'stone_bricks' if (x + z) % 2 else 'cobblestone')   # terrace
    # passage: open 3 high at both ends, paved
    for z in (CZ0, CZ1):
        for x in range(GX0, GX1 + 1):
            for y in (1, 2, 3):
                s.remove(x, y, z)
        P(s, GX0, 3, z, 'stone_brick_stairs', facing='west', **TOP)  # the arch
        P(s, GX1, 3, z, 'stone_brick_stairs', facing='east', **TOP)
    for x in range(GX0, GX1 + 1):
        for z in range(TZ0, CZ1 + 1):
            P(s, x, 0, z, 'stone_bricks' if x != 7 else ('cobblestone' if z % 2 else 'mossy_cobblestone'))
    # the corbelled parapet over the gate front (z 1), the terrace parapet at the back (z 6)
    for x in range(GX0, GX1 + 1):
        P(s, x, TERRACE, TZ0, 'stone_brick_stairs', facing='south', **TOP)
        P(s, x, TERRACE + 1, TZ0, 'stone_bricks')
        if x != 7:
            P(s, x, TERRACE + 2, TZ0, 'stone_bricks')
    for x in range(lx1, rx0 + 1):
        P(s, x, TERRACE + 1, CZ1, 'stone_bricks')
        if x % 2 == 1:
            P(s, x, TERRACE + 2, CZ1, 'stone_bricks')

    # openings
    for y in (1, 2):
        s.remove(lx1, y, 4); s.remove(rx0, y, 4)                     # tower doors from the passage
    for y in (WALK, WALK + 1):
        s.remove(lx0, y, 3); s.remove(rx1, y, 3)                     # wall walk doorways
        s.remove(lx1, y, 3); s.remove(rx0, y, 3)                     # into the gate room
    for y in (TOP_FLOOR + 1, TOP_FLOOR + 2):
        s.remove(lx1, y, 3); s.remove(rx0, y, 3)                     # from the fighting tops onto the terrace
    for p in SLITS:
        s.remove(*p)
    # battered plinth: stairs round the tower feet where no wall module joins
    for (x0, x1) in TOWERS:
        for x in range(x0, x1 + 1):
            P(s, x, 0, TZ0 - 1, 'cobblestone_stairs', facing='south', **STAIR)
            if (x0 == 1 and x < 5) or (x0 == 9 and x > 9):
                P(s, x, 0, TZ1 + 1, 'cobblestone_stairs', facing='north', **STAIR)
        out = x0 - 1 if x0 == 1 else x1 + 1
        for z in (TZ0, TZ1):
            P(s, out, 0, z, 'cobblestone_stairs', facing='east' if x0 == 1 else 'west', **STAIR)
    for x in range(GX0, GX1 + 1):                                    # steps up into the passage
        P(s, x, 0, 0, 'stone_brick_stairs', facing='south', **STAIR)
        P(s, x, 0, CZ1 + 1, 'stone_brick_stairs', facing='north', **STAIR)

    # ------------------------------------------------------------------ L1 fittings
    s.at(1, 'fittings')
    s.door(lx1, 1, 4, 'east', block='spruce_door', hinge='right')
    s.door(rx0, 1, 4, 'west', block='spruce_door', hinge='left')
    # left tower: stone stair up the front and left side to the wall-walk room (stand 5)
    for (x, y, z, f) in ((4, 1, 3, 'north'), (4, 2, 2, 'north'), (3, 3, 2, 'west'), (2, 4, 2, 'west')):
        P(s, x, y, z, 'stone_brick_stairs', facing=f, **STAIR)
    # both towers: a spruce stair up the back to the fighting top (stand 9)
    for (x, y, z, f) in ((2, 5, 4, 'east'), (3, 6, 4, 'east'), (4, 7, 4, 'east'), (4, 8, 3, 'north'),
                         (12, 5, 4, 'west'), (11, 6, 4, 'west'), (10, 7, 4, 'west'), (10, 8, 3, 'north')):
        P(s, x, y, z, 'spruce_stairs', facing=f, **STAIR)
    for (x, f) in ((6, 'west'), (8, 'east')):                         # steps from the terrace up to the tops
        P(s, x, TERRACE + 1, 3, 'stone_brick_stairs', facing=f, **STAIR)
    # left ground room: the gate guards' stores under the stair
    P(s, 2, 1, 2, 'barrel', facing='up', open='false')
    P(s, 3, 1, 2, 'barrel', facing='up', open='false')
    P(s, 2, 1, 4, 'chest', facing='east', type='single', waterlogged='false')
    P(s, 2, 1, 3, 'furnace', facing='east', lit='false')                # the guards' stove
    P(s, 3, 2, 4, 'wall_torch', facing='north')
    # right ground room: guard room with two beds, a table and a stool, a footlocker
    s.bed(12, 1, 3, 'north'); s.bed(11, 1, 3, 'north')
    P(s, 10, 1, 2, 'oak_slab', **SLAB_TOP)
    P(s, 10, 1, 3, 'oak_stairs', facing='north', **STAIR)
    P(s, 12, 1, 4, 'chest', facing='west', type='single', waterlogged='false')
    P(s, 10, 2, 4, 'wall_torch', facing='north')
    # wall-walk rooms: stores under the upper stairs, a bed for the night watch
    P(s, 3, 5, 4, 'chest', facing='north', type='single', waterlogged='false')
    P(s, 4, 5, 4, 'barrel', facing='up', open='false')
    P(s, 2, 6, 2, 'wall_torch', facing='south')
    s.bed(11, 5, 2, 'east')
    P(s, 10, 5, 2, 'chest', facing='south', type='single', waterlogged='false')
    P(s, 11, 5, 4, 'barrel', facing='up', open='false')
    P(s, 10, 5, 4, 'barrel', facing='up', open='false')
    # gate room: the portcullis winch (a log drum between two posts), the keeper's chest
    P(s, 7, 5, 5, 'stripped_spruce_log', axis='x')
    P(s, 6, 5, 5, 'spruce_fence'); P(s, 8, 5, 5, 'spruce_fence')
    P(s, 7, 6, 5, 'chain', axis='y')
    P(s, 6, 5, 4, 'chest', facing='north', type='single', waterlogged='false')
    # light in the passage and the bay
    P(s, 6, 2, 5, 'wall_torch', facing='east'); P(s, 8, 2, 5, 'wall_torch', facing='west')
    P(s, 6, 2, 1, 'wall_torch', facing='east'); P(s, 8, 2, 1, 'wall_torch', facing='west')

    # ------------------------------------------------------------------ L2: manned
    s.at(2, 'flourish2')
    for p in SLITS:
        P(s, *p, 'iron_bars')
    for x in range(GX0, GX1 + 1):                                    # the raised portcullis (2 clear below it)
        P(s, x, 3, 3, 'iron_bars')
    P(s, 7, 3, 5, 'lantern', **HANG)                                 # a lantern under the gate room
    for x in (3, 11):                                                # banners on the tower fronts
        P(s, x, 8, 0, 'red_wall_banner', facing='north')
        s.decorate(x, 8, 0, 'banner_pattern', banner='red_wall_banner', note='kingdom colours')
    P(s, 7, 8, 0, 'red_wall_banner', facing='north')                 # the banner over the arch
    s.decorate(7, 8, 0, 'banner_pattern', banner='red_wall_banner', note='kingdom arms, over the gate')
    for x in (5, 9):                                                 # lamp posts on the town side
        P(s, x, 0, 7, 'stone_brick_wall')
        P(s, x, 1, 7, 'lantern', **STAND)
    for x in (3, 11):                                                # lanterns on the fighting tops
        P(s, x, TOP_FLOOR + 1, 2, 'lantern', **STAND)
    P(s, 2, TOP_FLOOR + 1, 2, 'barrel', facing='up', open='false')     # arrows for the archers
    P(s, 12, TOP_FLOOR + 1, 2, 'barrel', facing='up', open='false')
    s.decorate(2, TOP_FLOOR + 2, 2, 'item', item='minecraft:arrow', note='arrows in the barrel')
    for x in (6, 7, 8):
        P(s, x, 5, 3, 'red_carpet')                                  # a runner through the gate room
    P(s, 3, 1, 3, 'red_carpet'); P(s, 3, 1, 4, 'red_carpet')
    P(s, 10, 1, 4, 'brown_carpet'); P(s, 11, 1, 4, 'brown_carpet')
    P(s, 10, 3, 2, 'spruce_trapdoor', facing='south', **SHELF)       # a shelf over the table
    # town side: a bench and the guards' supplies by the steps
    P(s, 2, 0, 7, 'spruce_stairs', facing='north', **STAIR)
    P(s, 3, 0, 7, 'spruce_stairs', facing='north', **STAIR)
    P(s, 11, 0, 7, 'barrel', facing='up', open='false')
    P(s, 12, 0, 7, 'hay_block', axis='y')
    P(s, 12, 1, 7, 'barrel', facing='up', open='false')

    # ------------------------------------------------------------------ L3: the crowned gate
    s.at(3, 'flourish3')
    for (x0, x1) in TOWERS:                                          # steep cones over the fighting tops, flags
        top = cone(s, x0, TZ0, x1, TZ1, TOP_FLOOR + 3)
        cx = (x0 + x1) // 2
        P(s, cx, top, 3, 'spruce_fence')
        P(s, cx, top + 1, 3, 'red_banner', rotation='8')
        s.decorate(cx, top + 1, 3, 'banner_pattern', banner='red_banner', note='kingdom colours')
    for x in range(GX0, GX1 + 1):                                    # murder holes (hatches in the passage ceiling)
        P(s, x, 3, 4, 'spruce_trapdoor', facing='north', **SHELF)
    P(s, 7, TERRACE + 2, TZ0, 'red_banner', rotation='0')           # a flag in the crenel over the gate
    s.decorate(7, TERRACE + 2, TZ0, 'banner_pattern', banner='red_banner', note='kingdom colours')
    for x in (6, 8):                                                 # moss on the terrace and the tops
        P(s, x, TERRACE + 1, 5, 'moss_carpet')
    for (x, z, side) in ((1, 0, 'south'), (5, 0, 'south'), (9, 0, 'south'), (13, 0, 'south')):
        for y in (1, 2, 3):
            vine(s, x, y, z, side)
    vine(s, 2, 4, 0, 'south'); vine(s, 12, 4, 0, 'south')
    # town side: flower boxes beside the back arch, a weapon rack, a mounting block
    for x, f in ((4, 'poppy'), (10, 'cornflower')):
        P(s, x, 0, 7, 'coarse_dirt')
        P(s, x, 1, 7, f)
        P(s, x, 0, 8, 'spruce_trapdoor', facing='south', **OPEN)
    # the guard room: a weapon rack and an armor stand (decoration step)
    s.decorate(2, 5, 3, 'armor_stand', note='the gate captain\'s armour in the wall-walk room')
    s.decorate(10, 2, 2, 'item_frame', item='minecraft:shield', facing='south', note='shield over the table')
    P(s, 7, 6, 4, 'lantern', **HANG)                                 # in the gate room, under the terrace
    return connect(s)


STRUCTURES = [dict(path='gatehouse', name='Gatehouse', tier=5, zone='wall', role='gate', group='gatehouse',
                   terrain='ground_level', door=None, make=gatehouse, max_height=H, changes='First version.',
                   description='Needs scaffolding: 11 tall (L3 cones: 19). Twin-tower stone gate for the batch-3 '
                               'stone wall line (wall face z 2, walk z 3..4 at stand 5, joining at x 1 and x 13): '
                               'arched passage, gate room over it, crenellated tops; stairs (no ladders) from the '
                               'passage up to the wall walk, the gate room and both fighting tops.')]

"""Gatehouse (tier 5, the main gate, docs/STRUCTURES.md "Gatehouse and gates"): twin stone towers flanking an arched
passage, a gate room over the passage, crenellated fighting tops; level 3 crowns both towers with steep cones.

It slots into a line of batch-3 stone wall modules (tools/batch3/stone_wall.py). Their profile: parapet on the outside
(merlons at y 6), a 2-wide walk behind it where soldiers stand at y 5, the body down to the ground. Here that wall line
is z 3..5: z 3 is the wall's outer face (the parapet's line), z 4..5 the walk. A wall module joins each tower's outer
side (x 1 and x 13), with its walk entering the tower through a doorway at z 4, stand y 5. The towers stand 2 out in
front of the wall face (z 1..2) and the gate block 2 behind it (z 6..7). x 0, x 14 and z 0 are a margin for the cone
eaves, the plinth and the front steps (air below them).

Plan (x 0..14, z 0..9; y 0 is the stone base course, soldiers stand on it at y 1):
  - Towers x 1..5 (left) and x 9..13 (right), z 1..5; inside 3 x 3 (x 2..4 / 10..12, z 2..4). Floors at y 4 and
    y 8: ground room (stand 1), wall-walk room (stand 5, the walk row is z 4), fighting top (stand 9) behind a
    parapet with merlons at y 10.
  - Passage x 6..8, z 1..7, 3 high (y 1..3), paved, arched at the gate face (z 3) and the town face (z 7); the deep
    bay in front (z 1..2) lies between the towers under a corbelled parapet; steps down at z 0 and z 8.
  - Gate room x 6..8, z 4..6 (stand 5, 2 high) over the passage, between the two wall-walk rooms (doorways at z 4);
    its roof is a terrace (stand 8) behind the corbelled front parapet (z 2) and the back parapet (z 7).
  - Stairs (no ladders, 2 free blocks over every step):
      left tower ground: door (5, 4) from the passage -> (4, 4); stone steps (3, 4) y1 west, (3, 3) y2 north,
      (3, 2) y3 north, (2, 2) y4 west = the wall-walk room. Both towers' wall-walk rooms: spruce steps from the walk
      row, left (4, 3) y5 north, (4, 2) y6 north, (3, 2) y7 west, (2, 2) y8 west = the fighting top (right tower
      mirrored). From both tops a doorway and a step lead down onto the terrace.
"""
from make_batch1_structures import Structure
from batch3.ws_kit import P, STAIR, TOP, SLAB_TOP, OPEN, SHELF, HANG, STAND, connect
from batch5.mil_kit import masonry, cone, vine

W, D, H = 15, 10, 20
TOWERS = ((1, 5), (9, 13))          # x ranges; both z 1..5
TZ0, TZ1 = 1, 5
GX0, GX1 = 6, 8                     # passage
CZ0, CZ1 = 3, 7                     # gate block: front (the wall face) and back walls
WALK = 5                            # stand height of the wall walk (stone_wall.WALK)
WROW = 4                            # the walk row through the towers and the gate room
TOP_FLOOR = 8                       # tower top floor (stand 9)
TERRACE = 7                         # gate room roof (stand 8)
SLITS = [(3, 2, 1), (11, 2, 1), (2, 6, 1), (4, 6, 1), (10, 6, 1), (12, 6, 1), (1, 6, 2), (13, 6, 2), (7, 5, CZ0), (5, 2, 3), (9, 2, 3),
         (3, 6, 5), (11, 6, 5), (11, 2, 5)]


def mirror(x):
    return W - 1 - x


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

    # floors: the left tower has the lower stairwell (3, 3), (3, 2); both tops the upper one (4, 2), (3, 2)
    for x in range(lx0 + 1, lx1):
        for z in range(TZ0 + 1, TZ1):
            if (x, z) not in ((3, 3), (3, 2), (2, 2)):
                P(s, x, 4, z, 'spruce_planks')
            P(s, mirror(x), 4, z, 'spruce_planks')
            if (x, z) not in ((4, 2), (3, 2), (2, 2)):
                for xx in (x, mirror(x)):
                    P(s, xx, TOP_FLOOR, z, 'spruce_planks' if (x + z) % 2 else 'stone_bricks')

    # gate block: front wall z 3 (x 6..8, between the towers), back wall z 7 (x 5..9), side walls x 5 / x 9 at z 6..7
    for y in range(0, TERRACE + 1):
        for x in range(GX0, GX1 + 1):
            P(s, x, y, CZ0, masonry(x, y, CZ0, 11) if y < TERRACE else 'stone_bricks')
        for z in (6, CZ1):
            for x in (lx1, rx0):
                P(s, x, y, z, 'stone_bricks' if y > 0 and z == CZ1 else masonry(x, y, z, 13))
        if y <= 4:
            for x in range(GX0, GX1 + 1):
                P(s, x, y, CZ1, masonry(x, y, CZ1, 13))
    for y in (5, 6):                                                 # the gate room's town face: a timber frame,
        P(s, 6, y, CZ1, 'white_terracotta' if y == 6 else 'glass_pane')   # two windows either side of a post
        P(s, 8, y, CZ1, 'white_terracotta' if y == 6 else 'glass_pane')
        P(s, 7, y, CZ1, 'dark_oak_log', axis='y')
        for x in (lx1, rx0):
            for z in (6, CZ1):
                P(s, x, y, z, 'dark_oak_log', axis='y')
    for x in range(lx1, rx0 + 1):
        P(s, x, 4, CZ1, 'dark_oak_log', axis='x')                    # sill beam over the arch
        P(s, x, TERRACE, CZ1, 'dark_oak_log', axis='x')              # head beam under the back parapet
    P(s, lx1, TERRACE, 6, 'dark_oak_log', axis='z'); P(s, rx0, TERRACE, 6, 'dark_oak_log', axis='z')
    for x in range(GX0, GX1 + 1):
        for z in range(CZ0 + 1, CZ1):
            P(s, x, 4, z, 'spruce_planks')                           # gate room floor = passage ceiling
            P(s, x, TERRACE, z, 'stone_bricks' if (x + z) % 2 else 'cobblestone')   # terrace
    # passage: open 3 high at both faces, paved from the front steps to the back steps
    for z in (CZ0, CZ1):
        for x in range(GX0, GX1 + 1):
            for y in (1, 2, 3):
                s.remove(x, y, z)
        P(s, GX0, 3, z, 'stone_brick_stairs', facing='west', **TOP)  # the arch
        P(s, GX1, 3, z, 'stone_brick_stairs', facing='east', **TOP)
    for x in range(GX0, GX1 + 1):
        for z in range(TZ0, CZ1 + 1):
            P(s, x, 0, z, 'stone_bricks' if x != 7 else ('cobblestone' if z % 2 else 'mossy_cobblestone'))
    # the parapet over the gate front, carried on corbels in front of the gate face (z 2); the back parapet (z 7)
    for x in range(GX0, GX1 + 1):
        P(s, x, TERRACE, 2, 'stone_brick_stairs', facing='south', **TOP)
        P(s, x, TERRACE + 1, 2, 'stone_bricks')
        if x != 7:
            P(s, x, TERRACE + 2, 2, 'stone_bricks')
    for x in range(lx1, rx0 + 1):
        P(s, x, TERRACE + 1, CZ1, 'stone_bricks')
        if x % 2 == 1:
            P(s, x, TERRACE + 2, CZ1, 'stone_bricks')
    P(s, lx1, TERRACE + 1, 6, 'stone_bricks'); P(s, rx0, TERRACE + 1, 6, 'stone_bricks')   # behind the towers

    # openings
    for y in (1, 2):
        s.remove(lx1, y, WROW); s.remove(rx0, y, WROW)               # tower doors from the passage
    for y in (WALK, WALK + 1):
        s.remove(lx0, y, WROW); s.remove(rx1, y, WROW)               # wall walk doorways
        s.remove(lx1, y, WROW); s.remove(rx0, y, WROW)               # into the gate room
    for y in (TOP_FLOOR + 1, TOP_FLOOR + 2):
        s.remove(lx1, y, WROW); s.remove(rx0, y, WROW)               # from the fighting tops onto the terrace
    for p in SLITS:
        s.remove(*p)
    # battered plinth: stairs round the tower feet where no wall module joins
    for (x0, x1) in TOWERS:
        for x in range(x0, x1 + 1):
            P(s, x, 0, TZ0 - 1, 'cobblestone_stairs', facing='south', **STAIR)
            if (x0 == lx0 and x < lx1) or (x0 == rx0 and x > rx0):
                P(s, x, 0, TZ1 + 1, 'cobblestone_stairs', facing='north', **STAIR)
        out = x0 - 1 if x0 == lx0 else x1 + 1
        for z in (TZ0, TZ0 + 1):
            P(s, out, 0, z, 'cobblestone_stairs', facing='east' if x0 == lx0 else 'west', **STAIR)
        for x in range(x0, x1 + 1):                                  # corbel table under the fighting tops
            P(s, x, TOP_FLOOR, TZ0 - 1, 'stone_brick_stairs', facing='south', **TOP)
            if (x0 == lx0 and x < lx1) or (x0 == rx0 and x > rx0):
                P(s, x, TOP_FLOOR, TZ1 + 1, 'stone_brick_stairs', facing='north', **TOP)
        for z in range(TZ0, TZ1 + 1):
            P(s, out, TOP_FLOOR, z, 'stone_brick_stairs', facing='east' if x0 == lx0 else 'west', **TOP)
    for x in range(GX0, GX1 + 1):                                    # steps up into the passage
        P(s, x, 0, 0, 'stone_brick_stairs', facing='south', **STAIR)
        P(s, x, 0, CZ1 + 1, 'stone_brick_stairs', facing='north', **STAIR)

    # ------------------------------------------------------------------ L1 fittings
    s.at(1, 'fittings')
    s.door(lx1, 1, WROW, 'east', block='spruce_door', hinge='right')
    s.door(rx0, 1, WROW, 'west', block='spruce_door', hinge='left')
    for (x, y, z, f) in ((3, 1, 4, 'west'), (3, 2, 3, 'north'), (3, 3, 2, 'north'), (2, 4, 2, 'west')):
        P(s, x, y, z, 'stone_brick_stairs', facing=f, **STAIR)       # left tower: ground -> wall-walk room
    for (x, y, z, f) in ((4, 5, 3, 'north'), (4, 6, 2, 'north'), (3, 7, 2, 'west'), (2, 8, 2, 'west')):
        P(s, x, y, z, 'spruce_stairs', facing=f, **STAIR)            # both towers: wall-walk room -> fighting top
        P(s, mirror(x), y, z, 'spruce_stairs', facing={'west': 'east'}.get(f, f), **STAIR)
    for (x, f) in ((6, 'west'), (8, 'east')):                         # steps from the terrace up to the tops
        P(s, x, TERRACE + 1, WROW, 'stone_brick_stairs', facing=f, **STAIR)
    # left ground room: the gate guards' post (stove, stores under the stair, a stool at the spy slit)
    P(s, 2, 1, 2, 'barrel', facing='up', open='false')
    P(s, 2, 1, 3, 'furnace', facing='east', lit='false')
    P(s, 4, 1, 2, 'chest', facing='west', type='single', waterlogged='false')
    P(s, 4, 1, 3, 'oak_stairs', facing='west', **STAIR)
    P(s, 2, 2, 4, 'wall_torch', facing='north')
    # right ground room: guard room with two beds, a table and a stool, a footlocker
    s.bed(12, 1, 3, 'north'); s.bed(11, 1, 3, 'north')
    P(s, 10, 1, 2, 'oak_slab', **SLAB_TOP)
    P(s, 10, 1, 3, 'oak_stairs', facing='north', **STAIR)
    P(s, 12, 1, 4, 'chest', facing='west', type='single', waterlogged='false')
    P(s, 10, 2, 4, 'wall_torch', facing='north')
    # wall-walk rooms: stores under the upper stairs, the night watch's bed
    P(s, 4, 5, 2, 'chest', facing='south', type='single', waterlogged='false')
    s.bed(11, 5, 2, 'east')
    P(s, 12, 5, 3, 'chest', facing='west', type='single', waterlogged='false')
    P(s, 3, 7, 4, 'wall_torch', facing='north'); P(s, 11, 7, 4, 'wall_torch', facing='north')
    # gate room: the portcullis winch (a log drum between two posts, a chain up to the roof), the keeper's chest
    P(s, 7, 5, 6, 'stripped_spruce_log', axis='x')
    P(s, 6, 5, 6, 'spruce_fence'); P(s, 8, 5, 6, 'spruce_fence')
    P(s, 7, 6, 6, 'chain', axis='y')
    P(s, 6, 5, 5, 'chest', facing='north', type='single', waterlogged='false')
    # light in the passage and the bay
    P(s, 6, 2, 6, 'wall_torch', facing='east'); P(s, 8, 2, 6, 'wall_torch', facing='west')
    P(s, 6, 2, 1, 'wall_torch', facing='east'); P(s, 8, 2, 1, 'wall_torch', facing='west')

    # ------------------------------------------------------------------ L2: manned
    s.at(2, 'flourish2')
    for p in SLITS:
        P(s, *p, 'iron_bars')
    for x in range(GX0, GX1 + 1):                                    # the raised portcullis (2 clear below it)
        P(s, x, 3, 4, 'iron_bars')
    P(s, 7, 3, 6, 'lantern', **HANG)                                 # a lantern under the gate room
    P(s, 7, 6, 5, 'lantern', **HANG)                                 # and in the gate room
    for x in (3, 11):                                                # banners on the tower fronts
        P(s, x, 7, 0, 'red_wall_banner', facing='north')
        s.decorate(x, 7, 0, 'banner_pattern', banner='red_wall_banner', note='kingdom colours')
    P(s, 7, TERRACE + 1, 1, 'red_wall_banner', facing='north')       # the banner over the arch, on the parapet
    s.decorate(7, TERRACE + 1, 1, 'banner_pattern', banner='red_wall_banner', note='kingdom arms, over the gate')
    for x in (lx1, rx0):                                             # lamp posts on the town side
        P(s, x, 0, CZ1 + 1, 'stone_brick_wall')
        P(s, x, 1, CZ1 + 1, 'lantern', **STAND)
    for x in (3, 11):                                                # lanterns on the fighting tops
        P(s, x, TOP_FLOOR + 1, 3, 'lantern', **STAND)
    for x in (4, 10):                                                # arrows for the archers
        P(s, x, TOP_FLOOR + 1, 4, 'barrel', facing='up', open='false')
        s.decorate(x, TOP_FLOOR + 2, 4, 'item', item='minecraft:arrow', note='arrows in the barrel')
    for x in (6, 7, 8):
        P(s, x, 5, WROW, 'red_carpet')                               # a runner through the gate room
    P(s, 2, 1, 4, 'red_carpet')
    P(s, 10, 1, 4, 'brown_carpet'); P(s, 11, 1, 4, 'brown_carpet')
    P(s, 10, 3, 2, 'spruce_trapdoor', facing='south', **SHELF)       # a shelf over the table
    P(s, 3, 5, 4, 'brown_carpet'); P(s, 11, 5, 4, 'brown_carpet')
    for x in (lx1, rx0):                                             # shutters beside the gate room windows
        P(s, x, 5, CZ1 + 1, 'spruce_trapdoor', facing='south', **OPEN)
    # town side: a bench and the guards' supplies by the steps
    P(s, 2, 0, 7, 'spruce_stairs', facing='north', **STAIR)
    P(s, 3, 0, 7, 'spruce_stairs', facing='north', **STAIR)
    P(s, 11, 0, 7, 'barrel', facing='up', open='false')
    P(s, 12, 0, 7, 'hay_block', axis='y')
    P(s, 12, 1, 7, 'barrel', facing='up', open='false')
    P(s, 13, 0, 7, 'barrel', facing='up', open='false')

    # ------------------------------------------------------------------ L3: the crowned gate
    s.at(3, 'flourish3')
    for (x0, x1) in TOWERS:                                          # steep cones over the fighting tops, flags
        top = cone(s, x0, TZ0, x1, TZ1, TOP_FLOOR + 3, stairs='deepslate_tile_stairs', body='deepslate_tiles')
        cx = (x0 + x1) // 2
        P(s, cx, top, 3, 'spruce_fence')
        P(s, cx, top + 1, 3, 'red_banner', rotation='8')
        s.decorate(cx, top + 1, 3, 'banner_pattern', banner='red_banner', note='kingdom colours')
    for x in range(GX0, GX1 + 1):                                    # murder holes (hatches in the passage ceiling)
        P(s, x, 3, 5, 'spruce_trapdoor', facing='north', **SHELF)
    P(s, 7, TERRACE + 2, 2, 'red_banner', rotation='0')             # a flag in the crenel over the gate
    s.decorate(7, TERRACE + 2, 2, 'banner_pattern', banner='red_banner', note='kingdom colours')
    for x in (6, 8):                                                 # moss on the terrace
        P(s, x, TERRACE + 1, 6, 'moss_carpet')
    for x in (1, 5, 9, 13):                                          # ivy up the tower fronts
        for y in ((1, 2, 3) if x in (1, 13) else (1, 2)):
            vine(s, x, y, 0, 'south')
    vine(s, 2, 4, 0, 'south'); vine(s, 12, 4, 0, 'south')
    for x, f in ((4, 'poppy'), (10, 'cornflower')):                  # flower boxes beside the town-side arch
        P(s, x, 0, CZ1 + 1, 'coarse_dirt')
        P(s, x, 1, CZ1 + 1, f)
        P(s, x, 0, CZ1 + 2, 'spruce_trapdoor', facing='south', **OPEN)
    s.decorate(10, 2, 2, 'item_frame', item='minecraft:shield', facing='south', note='shield over the table')
    s.decorate(4, 2, 2, 'item_frame', item='minecraft:iron_sword', facing='south', note='sword over the chest')
    return connect(s)


STRUCTURES = [dict(path='gatehouse', name='Gatehouse', tier=5, zone='wall', role='gate', group='gatehouse',
                   terrain='ground_level', door=None, make=gatehouse, max_height=H, changes='First version.',
                   description='Needs scaffolding: 11 tall (L3 cones: 20). Twin-tower stone gate for the batch-3 '
                               'stone wall line (wall face z 3, walk z 4..5 at stand 5, joining at x 1 and x 13): '
                               'arched passage, gate room over it, crenellated tops; stairs (no ladders) from the '
                               'passage up to the wall walk, the gate room and both fighting tops.')]

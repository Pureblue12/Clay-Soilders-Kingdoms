"""Noble house (tier 5, town style A): a stone and timber manor for a noble family (steward, captain) near the keep
(docs/STRUCTURES.md, "Noble house"; variant A, manor). Terrain: skirt (layer 0 is the base course and floor).

Layout (16 wide x 13 deep, front z = 0): the house x 6..14, z 3..9 (9 x 7, its long side to the street), a garden on
its left (x 0..4).
- Walls: a stone brick base and ground course, white plaster (white terracotta) above it in a dark oak frame (corner
  posts, posts by the door and in the middle of each side), a dark oak beam at y 4.
- Roof: a spruce gable roof with its ridge along the street (dark oak eaves, a slab ridge, one block of overhang all
  round, corbels under the gable overhangs), half-timbered gables at both ends, a front cross gable over the door
  with the middle bedroom's window, a brick chimney on the right gable end.
- Ground floor (one room in zones, soldier-sized): the hall at the door, the study on the left (desk, chair, chest),
  the dining table in the middle, the kitchen on the right (a smoker hearth by the chimney, a worktable, barrels, a
  chest); a staircase up along the back wall; a side door to the garden.
- Upper floor (under the roof): three bedrooms side by side, each with a bed and a chest, off a passage at the back
  where the stairs come up; windows in both gables and in the cross gable.
- The garden: stepping stones from the front steps round to the side door, a bench, poppies.
Level 1 (KNOWN-OK only): all of the above.
Level 2 (a noble's house): the porch: stone brick columns carrying a balcony (a dark oak floor with a fence rail)
under the cross gable window, lanterns under it; shutters, flower boxes under the front windows, red banners on the
front corners, bookshelves and a lectern in the study, rugs, brick shoulders on the chimney, a log pile at the back,
flower beds and two lantern posts in the garden.
Level 3 (wealth): a stone brick garden wall with pillars and lantern caps round the garden (an opening at the
front), a small statue of a clay soldier on a pedestal in the garden, potted plants on the balcony, banners on the
porch columns, candles and carpets inside, vines on the walls."""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, HANG, STAND, connect, shutters
from batch5.roofkit import height_roof, corbels, dormer, mirror_x

FLOWERS = ('poppy', 'cornflower', 'oxeye_daisy', 'allium', 'azure_bluet', 'dandelion')

W, D, H = 16, 13, 9
X0, X1, Z0, Z1 = 6, 14, 3, 9           # the house walls
MID = 10                                # the door, the cross gable
TOP_Y = 3                               # last wall layer
EAVE = 4


def roof_heights():
    """Height map of the main gable roof (ridge along x) plus the front cross gable over the door."""
    Hm = {}
    for x in range(X0 - 1, X1 + 2):
        for z in range(Z0 - 1, Z1 + 2):
            Hm[(x, z)] = EAVE + min(z - (Z0 - 1), (Z1 + 1) - z)
    for x in range(MID - 3, MID + 4):              # cross gable: x 7..13, ridge at x 10, from the front overhang back
        for z in range(Z0 - 1, Z0 + 3):
            c = EAVE + min(x - (MID - 3), (MID + 3) - x)
            if c > Hm[(x, z)]:
                Hm[(x, z)] = c
    return Hm


def noble_house():
    s = Structure('noble_house', (W, H, D), palette='oak_a2')
    Hm = roof_heights()
    posts = {(X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1), (MID - 1, Z0), (MID + 1, Z0), (X0, 6), (X1, 6), (MID, Z1)}
    side_door = (X0, 5)
    windows = [(7, 2, Z0), (8, 2, Z0), (12, 2, Z0), (13, 2, Z0), (X0, 2, 8), (X1, 2, 4), (8, 2, Z1),
               (12, 2, Z1)]
    gable_windows = [(X0, 6, 5), (X1, 6, 5)]
    chimney_x, chimney_z = X1 + 1, 7
    stairs = [(9, 1, 8), (10, 2, 8), (11, 3, 8), (12, 4, 8)]        # up along the back wall, facing east (uphill)

    s.at(1, 'shell')
    for x in range(X0, X1 + 1):                                     # base course and floor
        for z in range(Z0, Z1 + 1):
            edge = x in (X0, X1) or z in (Z0, Z1)
            P(s, x, 0, z, 'stone_bricks' if edge else 'spruce_planks')
    for y in range(1, TOP_Y + 1):                                   # walls
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                if not (x in (X0, X1) or z in (Z0, Z1)):
                    continue
                if (x, z) in posts:
                    P(s, x, y, z, 'dark_oak_log', axis='y')
                elif y == 1:
                    P(s, x, y, z, 'stone_bricks')
                else:
                    P(s, x, y, z, 'white_terracotta')
    for x in range(X0, X1 + 1):                                     # the beam, and the upper floor inside it
        for z in range(Z0, Z1 + 1):
            if x in (X0, X1) or z in (Z0, Z1):
                P(s, x, 4, z, 'dark_oak_log', axis='z' if x in (X0, X1) else 'x')
            elif (x, z) not in ((9, 8), (10, 8), (11, 8), (12, 8)):
                P(s, x, 4, z, 'spruce_planks')
    s.remove(MID, 1, Z0); s.remove(MID, 2, Z0)
    s.door(MID, 1, Z0, 'north', block='spruce_door')
    s.remove(side_door[0], 1, side_door[1]); s.remove(side_door[0], 2, side_door[1])
    s.door(side_door[0], 1, side_door[1], 'west', block='spruce_door', hinge='right')
    for w in windows:
        s.remove(*w)
    for x in (X0, X1):                                              # half-timbered gables: king post, plaster
        for z in range(Z0 + 1, Z1):
            for y in range(5, Hm[(x, z)]):
                if (x, y, z) in gable_windows:
                    continue
                if z == 6:
                    P(s, x, y, z, 'dark_oak_log', axis='y')
                else:
                    P(s, x, y, z, 'white_terracotta')
    # the cross gable's face over the door: posts by the bedroom window, plaster under the ridge
    P(s, MID - 1, 5, Z0, 'dark_oak_log', axis='y'); P(s, MID + 1, 5, Z0, 'dark_oak_log', axis='y')
    P(s, MID, 6, Z0, 'white_terracotta')
    # the chimney on the right gable end: a brick breast, the stack through the overhang to the ridge
    for y in range(0, 4):
        for z in (chimney_z - 1, chimney_z, chimney_z + 1):
            if y < 3 or z == chimney_z:
                P(s, chimney_x, y, z, 'bricks')
    stack = [(chimney_x, y, chimney_z) for y in range(4, 9)]
    for pos in stack:
        P(s, *pos, 'bricks')
    dormer(s, MID, 5, Z1, 'south')                                  # a back dormer over the passage
    height_roof(s, Hm, EAVE, skip=stack)
    corbels(s, Hm, [(X0 - 1, z) for z in range(Z0 - 1, Z1 + 2)])
    corbels(s, Hm, [(X1 + 1, z) for z in range(Z0 - 1, Z1 + 2) if z != chimney_z])
    corbels(s, Hm, [(x, Z0 - 1) for x in range(MID - 2, MID + 3)])
    # the front steps and porch floor
    for x in range(MID - 2, MID + 3):
        for z in (1, 2):
            P(s, x, 0, z, 'stone_bricks' if (x + z) % 2 else 'cobblestone')
    for x in (MID - 1, MID, MID + 1):
        P(s, x, 0, 0, 'stone_brick_stairs', facing='south', **STAIR)
    # stepping stones from the porch round the left corner to the side door
    for x, z in [(7, 1), (6, 1), (5, 1), (4, 2), (4, 3), (4, 4), (5, 5), (3, 5), (2, 6), (2, 7), (2, 8), (3, 9)]:
        P(s, x, 0, z, 'cobblestone_slab', **SLAB)

    s.at(1, 'fittings')
    for x, y, z in windows + gable_windows + [(MID, 5, Z0)]:
        side = x in (X0, X1)
        P(s, x, y, z, 'glass_pane', north=side, south=side, east=not side, west=not side, waterlogged='false')
    for x, y, z in stairs:
        P(s, x, y, z, 'spruce_stairs', facing='east', **STAIR)
    # the study (left): a chest, a desk with a chair
    P(s, 7, 1, 4, 'chest', facing='south', type='single', waterlogged='false')
    P(s, 7, 1, 6, 'spruce_slab', **SLAB_TOP)
    P(s, 8, 1, 6, 'spruce_stairs', facing='east', **STAIR)
    # the hall (by the door): a settle under the front windows
    P(s, 12, 1, 4, 'dark_oak_stairs', facing='north', **STAIR)
    # the dining table in the middle: fence legs under pressure plates, a chair at each end
    for x in (10, 11):
        P(s, x, 1, 6, 'oak_fence'); P(s, x, 2, 6, 'oak_pressure_plate', powered='false')
    P(s, 9, 1, 6, 'dark_oak_stairs', facing='west', **STAIR)
    P(s, 12, 1, 6, 'dark_oak_stairs', facing='east', **STAIR)
    # the kitchen (right): the hearth by the chimney, a worktable, barrels, a chest
    P(s, 13, 1, 7, 'smoker', facing='west', lit='false')
    P(s, 13, 1, 6, 'crafting_table')
    P(s, 13, 1, 5, 'barrel', facing='up', open='false')
    P(s, 13, 1, 4, 'chest', facing='south', type='single', waterlogged='false')
    P(s, 13, 1, 8, 'barrel', facing='up', open='false')            # under the top of the stairs
    for x, z, f in ((MID - 1, Z0 + 1, 'south'), (MID + 1, Z0 + 1, 'south'), (X0 + 1, 6, 'east'), (X1 - 1, 6, 'west')):
        if (x, 3, z) not in s.blocks:
            P(s, x, 3, z, 'wall_torch', facing=f)
    # upper floor: three bedrooms side by side (walls at x 9 and x 12), a passage at the back (z 7)
    for x in (9, 12):
        for z in range(Z0 + 1, 7):
            for y in range(5, Hm[(x, z)]):
                P(s, x, y, z, 'spruce_planks')
    for x in (7, 10, 13):
        s.bed(x, 5, 5, 'north')
    for x in (8, 11):
        P(s, x, 5, 4, 'chest', facing='south', type='single', waterlogged='false')
    P(s, 13, 5, 6, 'barrel', facing='up', open='false')
    # the garden: a bench facing the house, poppies
    for z in (6, 7):
        P(s, 0, 0, z, 'spruce_stairs', facing='west', **STAIR)
    for x, z in ((0, 9), (1, 11), (3, 11), (0, 3), (2, 3)):
        P(s, x, 0, z, 'poppy')

    s.at(2, 'flourish2')
    # the porch: stone brick columns carrying a balcony under the cross gable window
    for x in (MID - 2, MID + 2):
        for y in (1, 2, 3):
            P(s, x, y, 1, 'stone_brick_wall')
    for x in range(MID - 2, MID + 3):
        for z in (1, 2):
            A(s, x, 4, z, 'dark_oak_slab', **SLAB)
        P(s, x, 4, 0, 'dark_oak_stairs', facing='north', **TOP)     # the balcony's front edge
        P(s, x, 5, 0, 'dark_oak_fence')
    for x in (MID - 1, MID + 1):
        P(s, x, 3, 1, 'lantern', **HANG)
    # shutters on the side and back windows, flower boxes under the front windows
    for x, y, z in windows:
        if z == Z0:
            continue
        side = 'west' if x == X0 else 'east' if x == X1 else 'south'
        shutters(s, x, y, z, side)
    for x, f in ((7, 'poppy'), (13, 'cornflower')):
        P(s, x, 1, Z0 - 1, 'coarse_dirt'); P(s, x, 2, Z0 - 1, f)
        P(s, x, 1, Z0 - 2, 'spruce_trapdoor', facing='north', **OPEN)
    # red banners on the front corner posts
    for x in (X0, X1):
        P(s, x, 3, Z0 - 1, 'red_wall_banner', facing='north')
        s.decorate(x, 3, Z0 - 1, 'banner_pattern', banner='red_wall_banner', note='the family arms')
    # the study: bookshelves on the left wall, a lectern; rugs in the hall and bedrooms
    for z in (7, 8):
        P(s, 7, 1, z, 'bookshelf'); P(s, 7, 2, z, 'bookshelf')
    P(s, 8, 1, 4, 'lectern', facing='south', has_book='true', powered='false')
    for x in (10, 11):
        P(s, x, 1, 4, 'red_carpet'); P(s, x, 1, 5, 'red_carpet')
    for x in (8, 11):
        P(s, x, 5, 5, 'red_carpet')
    # the chimney's shoulders, a log pile at the back
    P(s, chimney_x, 3, chimney_z - 1, 'brick_stairs', facing='south', **STAIR)
    P(s, chimney_x, 3, chimney_z + 1, 'brick_stairs', facing='north', **STAIR)
    for x in (12, 13):                                              # a log pile at the back (log ends out)
        for y in (0, 1):
            P(s, x, y, Z1 + 1, 'spruce_log', axis='z')
    P(s, 11, 0, Z1 + 1, 'spruce_log', axis='z')
    P(s, 11, 1, Z1 + 1, 'spruce_slab', **SLAB)
    for z in (5, 8):                                                # the bench's arms
        P(s, 0, 0, z, 'spruce_trapdoor', facing='north' if z == 5 else 'south', **OPEN)
    # the garden: flower beds and two lantern posts
    for i, (x, z) in enumerate(((3, 6), (3, 7), (3, 8), (1, 3), (0, 10), (1, 9), (2, 11), (4, 11))):
        A(s, x, 0, z, FLOWERS[i % len(FLOWERS)])
    for x, z in ((1, 5), (4, 10)):
        P(s, x, 0, z, 'spruce_fence'); P(s, x, 1, z, 'spruce_fence'); P(s, x, 2, z, 'lantern', **STAND)

    s.at(3, 'flourish3')
    # the garden wall: stone brick wall with pillars and lantern caps; the opening is at the front (x 2..4, z 2)
    wall = [(0, z) for z in range(2, 13)] + [(x, 12) for x in range(1, 6)] + [(1, 2)]
    pillars = {(0, 2), (0, 7), (0, 12), (5, 12)}
    for x, z in wall:
        if (x, z) in pillars:
            A(s, x, 0, z, 'stone_bricks'); P(s, x, 1, z, 'stone_bricks'); P(s, x, 2, z, 'lantern', **STAND)
        else:
            A(s, x, 0, z, 'stone_brick_wall')
    # the statue: a little obelisk with a gold-look tip on a carved pedestal
    P(s, 2, 0, 10, 'stone_bricks')
    P(s, 2, 1, 10, 'chiseled_stone_bricks')
    P(s, 2, 2, 10, 'stone_brick_wall')
    P(s, 2, 3, 10, 'stone_brick_wall')
    P(s, 2, 4, 10, 'light_weighted_pressure_plate', power='0')
    # potted plants on the balcony, banners on the porch columns, candles and carpets inside
    for x in (MID - 2, MID + 2):
        P(s, x, 5, 1, 'flower_pot'); s.decorate(x, 5, 1, 'potted_plant', plant='red_tulip')
    P(s, 7, 2, 6, 'candle', candles='2', lit='true', waterlogged='false')
    for x, z in ((10, 7), (11, 7), (9, 7)):
        A(s, x, 1, z, 'brown_carpet')
    for x in (MID - 3, MID + 3):                                    # flowering bushes by the steps
        A(s, x, 0, 0, 'flowering_azalea_leaves', persistent='true', distance='1', waterlogged='false')
    # vines on the left wall's plaster
    for z in (4, 7):
        A(s, X0 - 1, 2, z, 'vine', east='true', north='false', south='false', west='false', up='false')
    return connect(mirror_x(s))


STRUCTURES = [dict(path='noble_house', name='Noble house', tier=5, zone='inner', role='house', group='noble_house',
                   terrain='skirt', door='front', make=noble_house, changes='First version.',
                   description='Stone and timber manor.')]

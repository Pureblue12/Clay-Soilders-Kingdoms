"""Noble house (tier 5, town style A): a stone and timber manor for a noble family (steward, captain) near the keep
(docs/STRUCTURES.md, "Noble house"; variant A, manor). Terrain: skirt (layer 0 is the base course and floor).

Layout (14 wide x 15 deep, front z = 0): the house x 6..12, z 3..11 (7 x 9), a garden on its left (x 0..4).
- Walls: a stone brick plinth course and ground course, white plaster (white terracotta) above it in a dark oak
  frame (corner posts, posts by the door and in the middle of each side), a dark oak beam at y 4 under the roof.
- Roof: a hip roof (spruce, dark oak eaves, slab ridge, one block of overhang all round), dormers on the left, right
  and back slopes for the bedrooms, a brick chimney at the back right.
- Ground floor (soldier-sized, one open room in zones): the hall at the front door, the study front left (desk,
  chair, chest), the dining table in the middle, the kitchen at the back right (a smoker hearth under the chimney,
  a worktable, barrel and chest); a staircase up the left wall; a side door to the garden.
- Upper floor (under the roof, lit by the dormers): three bedrooms, two at the front (split by a wall) and one at the
  back, each with a bed and a chest.
- The garden: a gravel path from the front round to the side door, a lawn with a bench and poppies.
Level 1 (KNOWN-OK only): all of the above.
Level 2 (a noble's house): the porch: a gabled portico on stone brick columns over the front steps, with hanging
lanterns and a pediment; shutters, flower boxes, bookshelves and a lectern in the study, banners on the corner
posts, flower beds and lantern posts in the garden.
Level 3 (wealth): a stone brick garden wall with pillars and lantern caps round the garden, a small statue on a
pedestal in the garden, candles and carpets inside, red banners on the portico, vines and flower pots."""
from make_batch1_structures import Structure
from batch3.ws_kit import (P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, LEAVES, connect, noise,
                           shutters, flower_box)
from batch5.roofkit import hip_roof, dormer

FLOWERS = ('poppy', 'cornflower', 'oxeye_daisy', 'allium', 'azure_bluet', 'dandelion', 'red_tulip', 'lily_of_the_valley')


def noble_house():
    W, D, H = 14, 15, 9
    s = Structure('noble_house', (W, H, D), palette='oak_a2')
    x0, x1, z0, z1 = 6, 12, 3, 11
    mid = 9
    top = 3
    posts = {(x0, z0), (x1, z0), (x0, z1), (x1, z1), (mid - 1, z0), (mid + 1, z0), (x0, 7), (x1, 7), (mid, z1)}
    door = (mid, z0)
    side_door = (x0, 5)
    windows = [(7, 2, z0), (11, 2, z0), (x0, 2, 9), (x1, 2, 5), (x1, 2, 9), (8, 2, z1), (10, 2, z1)]
    chimney = [(11, y, 10) for y in range(2, 9)]
    stairs = [(7, 1, 6), (7, 2, 7), (7, 3, 8), (7, 4, 9)]          # up the left wall, facing south (uphill)

    s.at(1, 'shell')
    for x in range(x0, x1 + 1):                                     # base course and floor
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            P(s, x, 0, z, 'stone_bricks' if edge else 'spruce_planks')
    for y in range(1, top + 1):                                     # walls
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                if (x, z) in posts:
                    P(s, x, y, z, 'dark_oak_log', axis='y')
                elif y == 1:
                    P(s, x, y, z, 'stone_bricks')
                else:
                    P(s, x, y, z, 'white_terracotta')
    for x in range(x0, x1 + 1):                                     # the beam under the roof
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                P(s, x, 4, z, 'dark_oak_log', axis='x' if z in (z0, z1) and x not in (x0, x1) else 'z')
            elif (x, 4, z) not in [(7, 4, 6), (7, 4, 7), (7, 4, 8), (7, 4, 9)]:
                P(s, x, 4, z, 'spruce_planks')                      # the upper floor (a hole over the staircase)
    s.remove(door[0], 1, door[1]); s.remove(door[0], 2, door[1])
    s.door(door[0], 1, door[1], 'north', block='spruce_door')
    s.remove(side_door[0], 1, side_door[1]); s.remove(side_door[0], 2, side_door[1])
    s.door(side_door[0], 1, side_door[1], 'west', block='spruce_door', hinge='right')
    for w in windows:
        s.remove(*w)
    for x, y, z in chimney:                                         # the chimney (back right corner, inside)
        P(s, x, y, z, 'bricks')
    # dormers (before the roof, so the roof fills in round them)
    dormer(s, x0, 5, 6, 'west')
    dormer(s, x1, 5, 6, 'east')
    dormer(s, mid, 5, z1, 'south')
    roof = [(x, z) for x in range(x0 - 1, x1 + 2) for z in range(z0 - 1, z1 + 2)]
    hip_roof(s, roof, 4)
    # porch floor and front steps (the portico over them comes at level 2)
    for x in range(7, 12):
        for z in (1, 2):
            P(s, x, 0, z, 'stone_bricks' if (x + z) % 2 else 'cobblestone')
    for x in (8, 9, 10):
        P(s, x, 0, 0, 'stone_brick_stairs', facing='south', **STAIR)
    # the garden path: cobblestone slab stepping stones from the porch round to the side door
    for x, z in [(6, 1), (6, 2), (5, 2), (4, 2), (4, 3), (4, 4), (4, 5), (5, 5), (3, 5), (2, 5), (2, 6), (2, 7),
                 (2, 8), (2, 9), (3, 9), (4, 9)]:
        P(s, x, 0, z, 'cobblestone_slab', **SLAB)

    s.at(1, 'fittings')
    for x, y, z in windows:
        side = x in (x0, x1)
        P(s, x, y, z, 'glass_pane', north=side, south=side, east=not side, west=not side, waterlogged='false')
    for i, (x, y, z) in enumerate(stairs):
        P(s, x, y, z, 'spruce_stairs', facing='south', **STAIR)
    # study (front left): chest, desk, chair
    P(s, 7, 1, 4, 'chest', facing='east', type='single', waterlogged='false')
    P(s, 8, 1, 4, 'spruce_slab', **SLAB_TOP)
    P(s, 8, 1, 5, 'spruce_stairs', facing='south', **STAIR)
    # hall (front right): a settle and a chest for cloaks
    P(s, 11, 1, 4, 'chest', facing='west', type='single', waterlogged='false')
    P(s, 11, 1, 5, 'dark_oak_stairs', facing='east', **STAIR)
    P(s, 11, 1, 6, 'dark_oak_stairs', facing='east', **STAIR)
    # dining table in the middle: two fence legs under plates, chairs at the ends
    for z in (7, 8):
        P(s, 9, 1, z, 'oak_fence'); P(s, 9, 2, z, 'oak_pressure_plate', powered='false')
    P(s, 9, 1, 6, 'dark_oak_stairs', facing='north', **STAIR)
    P(s, 9, 1, 9, 'dark_oak_stairs', facing='south', **STAIR)
    # kitchen (back right): the hearth under the chimney, worktable, barrel, chest
    P(s, 11, 1, 10, 'smoker', facing='west', lit='false')
    P(s, 11, 1, 9, 'crafting_table')
    P(s, 10, 1, 10, 'barrel', facing='up', open='false')
    P(s, 11, 1, 8, 'chest', facing='west', type='single', waterlogged='false')
    P(s, 8, 1, 10, 'barrel', facing='up', open='false')
    # torches on the posts
    for x, z, f in ((x0 + 1, 7, 'east'), (x1 - 1, 7, 'west'), (mid, z1 - 1, 'north')):
        if (x, 3, z) not in s.blocks:
            P(s, x, 3, z, 'wall_torch', facing=f)
    # upper floor: three bedrooms (two at the front split by a wall, one at the back)
    for z in (4, 5):
        P(s, mid, 5, z, 'spruce_planks')
        P(s, mid, 6, z, 'spruce_planks')
    s.bed(8, 5, 5, 'north')
    s.bed(10, 5, 5, 'north')
    s.bed(9, 5, 9, 'south')
    P(s, 7, 5, 4, 'chest', facing='east', type='single', waterlogged='false')
    P(s, 11, 5, 4, 'chest', facing='west', type='single', waterlogged='false')
    P(s, 10, 5, 10, 'chest', facing='north', type='single', waterlogged='false')
    # the garden: a bench facing the house and poppies
    for z in (7, 8):
        P(s, 1, 0, z, 'spruce_stairs', facing='west', **STAIR)
    for x, z in ((0, 10), (1, 11), (3, 12), (0, 4), (3, 3)):
        P(s, x, 0, z, 'poppy')

    s.at(2, 'flourish2')
    # the portico: stone brick columns, a gable roof meeting the main roof, a pediment, hanging lanterns
    for x in (7, 11):
        for y in (1, 2, 3):
            P(s, x, y, 1, 'stone_brick_wall')
    for z in (0, 1, 2):
        A(s, 7, 4, z, 'dark_oak_stairs', facing='east', **STAIR)
        A(s, 11, 4, z, 'dark_oak_stairs', facing='west', **STAIR)
    for z in (0, 1, 2, 3):
        A(s, 8, 5, z, 'spruce_stairs', facing='east', **STAIR)
        A(s, 10, 5, z, 'spruce_stairs', facing='west', **STAIR)
    for z in (0, 1, 2, 3):
        A(s, 9, 6, z, 'dark_oak_planks')
    for z in (0, 1, 2, 3, 4):
        A(s, 9, 7, z, 'spruce_slab', **SLAB)
    for x in (8, 9, 10):                                             # the pediment above the columns
        P(s, x, 4, 1, 'white_terracotta' if x == 9 else 'dark_oak_planks')
    P(s, 9, 5, 1, 'dark_oak_planks')
    P(s, 8, 4, 0, 'spruce_stairs', facing='west', **TOP)            # corbels under the front overhang
    P(s, 10, 4, 0, 'spruce_stairs', facing='east', **TOP)
    P(s, 9, 5, 0, 'spruce_slab', **SLAB_TOP)
    for x in (8, 10):
        P(s, x, 3, 1, 'lantern', **HANG)
    # shutters on the side and back windows, flower boxes under the front ones
    for x, y, z in windows:
        if z == z0:
            continue
        side = 'west' if x == x0 else 'east' if x == x1 else 'south'
        shutters(s, x, y, z, side)
    # banners on the front corner posts
    for x in (x0, x1):
        P(s, x, 3, z0 - 1, 'red_wall_banner', facing='north')
        s.decorate(x, 3, z0 - 1, 'banner_pattern', banner='red_wall_banner', note='the family arms')
    # the study: bookshelves on the back of the stair, a lectern
    P(s, 8, 1, 6, 'bookshelf')
    P(s, 8, 2, 6, 'bookshelf')
    P(s, 8, 2, 4, 'lectern', facing='south', has_book='true', powered='false') if False else None
    # rugs
    for x, z in ((10, 7), (10, 8), (8, 8), (8, 7)):
        P(s, x, 1, z, 'red_carpet')
    # garden: flower beds and two lantern posts
    for i, (x, z) in enumerate(((3, 6), (3, 7), (3, 8), (1, 3), (0, 6), (0, 9), (1, 10), (3, 11))):
        A(s, x, 0, z, FLOWERS[i % len(FLOWERS)])
    for x, z in ((1, 5), (4, 10)):
        P(s, x, 0, z, 'spruce_fence'); P(s, x, 1, z, 'spruce_fence'); P(s, x, 2, z, 'lantern', **STAND)

    return connect(s)


STRUCTURES = [dict(path='noble_house', name='Noble house', tier=5, zone='inner', role='house', group='noble_house',
                   terrain='skirt', door='front', make=noble_house, changes='First version.',
                   description='Stone and timber manor.')]

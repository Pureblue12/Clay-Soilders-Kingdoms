"""Infirmary (tier 4, look A): a white-plastered, half-timbered ward with a row of beds on each side and a herb store
(docs/STRUCTURES.md, "Infirmary / hospital"). Terrain: ground_level (layer 0 is the base course and floor).

Layout (11 wide x 13 deep, front z = 0): walls x 2..8, z 2..10 (7 x 9). Cobblestone base course, white terracotta
(plaster) walls in a dark oak frame (corner posts and posts in the long walls), a spruce stair gable roof with dark
oak eaves, spruce top plates and a slab ridge, one block of overhang all round, spruce plank gables.
- Front: the door in the middle (spruce) with a step, a window on each side.
- Sides: three windows each, one over every bed head; on the right, an open lean-to herb store under its own roof.
- Inside (the ward, 5 x 7): three beds along each wall with their heads to the wall, a bedside table between them, an
  aisle down the middle. At the front: the healer's chest (left) and the dispensary table (right); at the back: the
  hearth (a furnace, for hot water) between herb barrels and a chest, herb shelves on the back wall.
- The herb store (lean-to, x 9..10, z 6..9): a composter of herb cuttings, barrels, a hay bale.
Level 2 (the ward at work): a brewing stand on the dispensary table and a cauldron beside it, candles and pots on the
bedside tables, lanterns on chains from the roof, white carpets in the aisle, flower boxes under the front windows,
a door canopy with lanterns, the white banner over the door (its red cross by the decoration step), herbs drying in
the lean-to.
Level 3 (herb garden and care): a herb garden along the left side (ferns, flowers, berry bushes) with a stepping
path, herb pots on the windowsills, banners with the red cross by the door, a bench by the door, vines, and a lantern
post."""
from make_batch1_structures import Structure
from batch3.ws_kit import (P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, LEAVES, connect, noise,
                           gable_roof, flower_box)


def infirmary():
    W, D = 11, 13
    s = Structure('infirmary', (W, 9, D), palette='oak_a2')
    x0, x1, z0, z1 = 2, 8, 2, 10
    mid = 5
    top = 3
    beds = (4, 6, 8)                                   # z of the beds (heads at the wall)
    posts = {z0, 5, 7, z1}
    s.at(1, 'shell')
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            P(s, x, 0, z, 'cobblestone' if x in (x0, x1) or z in (z0, z1) else 'spruce_planks')
    for y in range(1, top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                if (x in (x0, x1) and z in posts) or (z in (z0, z1) and x in (x0, x1)) or \
                        (z == z0 and x in (mid - 1, mid + 1)):
                    P(s, x, y, z, 'dark_oak_log', axis='y')
                elif z in (z0, z1) and y == top:
                    P(s, x, y, z, 'dark_oak_log', axis='x')          # the beam across the gable walls
                else:
                    P(s, x, y, z, 'white_terracotta')
    s.remove(mid, 1, z0); s.remove(mid, 2, z0)
    s.door(mid, 1, z0, 'north', block='spruce_door')
    windows = [(mid - 2, 2, z0), (mid + 2, 2, z0)] + [(x, 2, z) for z in beds for x in (x0, x1)] + [(mid - 2, 2, z1), (mid + 2, 2, z1)]
    for w in windows:
        s.remove(*w)
    # the hearth's chimney: a brick breast outside the back wall, a stack through the back overhang to the ridge
    chimney = [(mid, y, z1 + 1) for y in range(3, 9)]
    gable_roof(s, x0, x1, z0, z1, top, front=1, back=1, gable='spruce_planks', plates='spruce_log', skip=chimney)
    for x in (mid - 1, mid, mid + 1):
        for y in (0, 1, 2):
            P(s, x, y, z1 + 1, 'bricks')
    for pos in chimney:
        P(s, *pos, 'bricks')
    P(s, mid, 0, z0 - 1, 'cobblestone_stairs', facing='south', **STAIR)      # the step
    # the herb store: a lean-to on the right, its roof under the main eave
    for z in (6, 9):
        P(s, W - 1, 0, z, 'cobblestone')
        for y in (1, 2):
            P(s, W - 1, y, z, 'spruce_log', axis='y')
    for z in range(6, 10):
        P(s, x1 + 1, 0, z, 'spruce_planks')
        if z not in (6, 9):
            P(s, W - 1, 0, z, 'spruce_planks')
        P(s, x1 + 1, 3, z, 'spruce_slab', **SLAB_TOP)
        P(s, W - 1, 3, z, 'spruce_stairs', facing='west', **STAIR)

    s.at(1, 'fittings')
    for x, y, z in windows:
        side = x in (x0, x1)
        P(s, x, y, z, 'glass_pane', north=side, south=side, east=not side, west=not side, waterlogged='false')
    for z in beds:                                     # the beds: heads to the wall
        s.bed(x0 + 2, 1, z, 'west')
        s.bed(x1 - 2, 1, z, 'east')
    for z in (5, 7):                                   # bedside tables
        P(s, x0 + 1, 1, z, 'spruce_slab', **SLAB_TOP)
        P(s, x1 - 1, 1, z, 'spruce_slab', **SLAB_TOP)
    P(s, x0 + 1, 1, z0 + 1, 'chest', facing='east', type='single', waterlogged='false')   # the healer's chest
    P(s, x1 - 1, 1, z0 + 1, 'spruce_slab', **SLAB_TOP)                                   # the dispensary table
    P(s, mid, 1, z1 - 1, 'furnace', facing='north', lit='false')                         # the hearth (hot water)
    P(s, mid - 1, 1, z1 - 1, 'barrel', facing='up', open='false')                        # herbs
    P(s, mid + 1, 1, z1 - 1, 'chest', facing='west', type='single', waterlogged='false')
    for x in (mid - 1, mid + 1):                                                          # herb shelves
        P(s, x, 3, z1 - 1, 'oak_trapdoor', facing='south', **SHELF)
    for z in (5, 7):                                                                      # torches on the posts
        P(s, x0 + 1, 3, z, 'wall_torch', facing='east')
        P(s, x1 - 1, 3, z, 'wall_torch', facing='west')
    # the herb store's contents
    P(s, x1 + 1, 1, 6, 'composter', level='3')
    P(s, x1 + 1, 1, 7, 'barrel', facing='up', open='false')
    P(s, x1 + 1, 1, 9, 'hay_block', axis='y')
    P(s, W - 1, 1, 8, 'barrel', facing='west', open='false')

    s.at(2, 'flourish2')
    P(s, x1 - 1, 2, z0 + 1, 'brewing_stand', has_bottle_0='true', has_bottle_1='false', has_bottle_2='true')
    P(s, x1 - 2, 1, z0 + 1, 'cauldron')
    for z, (a, b) in zip((5, 7), (('candle', 'flower_pot'), ('flower_pot', 'candle'))):   # bedside tables
        for x, k in ((x0 + 1, a), (x1 - 1, b)):
            if k == 'candle':
                P(s, x, 2, z, 'candle', candles='2', lit='true', waterlogged='false')
            else:
                P(s, x, 2, z, 'flower_pot')
                s.decorate(x, 2, z, 'potted_plant', plant='fern')
    for z in (4, 8):                                                                      # lanterns on chains
        P(s, mid, top + 3, z, 'chain', axis='y'); P(s, mid, top + 2, z, 'chain', axis='y')
        P(s, mid, top + 1, z, 'lantern', **HANG)
    for z in range(z0 + 1, z1 - 1):                                                       # the aisle carpet
        P(s, mid, 1, z, 'white_carpet')
    for x, f in ((mid - 2, 'poppy'), (mid + 2, 'cornflower')):                            # flower boxes, front
        flower_box(s, x, 1, z0 - 1, 'north', f)
    for x in (mid - 1, mid, mid + 1):
        P(s, x, 3, z0 - 1, 'spruce_slab', **SLAB)
    for x in (mid - 1, mid + 1):
        P(s, x, 2, z0 - 1, 'lantern', **HANG)
    P(s, mid, 4, z0 - 1, 'white_wall_banner', facing='north')
    s.decorate(mid, 4, z0 - 1, 'banner_pattern', banner='white_wall_banner', note='a red cross (the infirmary sign)')
    for z in (7, 8):
        s.decorate(x1 + 1, 2, z, 'item', item='minecraft:fern', note='bundles of herbs drying under the lean-to roof')
    P(s, x1 + 1, 2, 7, 'brown_carpet')
    P(s, W - 1, 2, 8, 'flower_pot'); s.decorate(W - 1, 2, 8, 'potted_plant', plant='fern')

    s.at(3, 'flourish3')
    # the herb garden along the left side: coarse dirt beds with herbs and a stepping path
    for z in range(4, 10):
        P(s, 0, 0, z, 'coarse_dirt')
    for z, f in zip(range(4, 10), ('fern', 'allium', 'sweet_berry_bush', 'fern', 'cornflower', 'sweet_berry_bush')):
        if f == 'sweet_berry_bush':
            P(s, 0, 1, z, f, age='2')
        else:
            P(s, 0, 1, z, f)
    for x, z in ((0, 3), (0, 10)):
        P(s, x, 0, z, 'spruce_fence'); P(s, x, 1, z, 'spruce_fence')
    for x, z, plant in ((x0 + 1, z0 + 1, 'blue_orchid'), (mid - 1, z1 - 1, 'fern'), (mid + 1, z1 - 1, 'azure_bluet')):
        P(s, x, 2, z, 'flower_pot')                                                       # herb pots
        s.decorate(x, 2, z, 'potted_plant', plant=plant)
    for x in (x0, x1):                                                                    # red-cross banners on the front posts
        P(s, x, 3, z0 - 1, 'white_wall_banner', facing='north')
        s.decorate(x, 3, z0 - 1, 'banner_pattern', banner='white_wall_banner', note='a red cross')
    P(s, x1 + 1, 0, z0 - 1, 'spruce_stairs', facing='east', **STAIR)                       # a bench by the front
    P(s, x1 + 1, 0, z0, 'spruce_stairs', facing='east', **STAIR)
    for y in (0, 1):
        P(s, 0, y, 0, 'spruce_fence')
    P(s, 0, 2, 0, 'lantern', **STAND)
    for x, z, b in ((W - 1, 0, 'leaves'), (W - 1, D - 1, 'leaves'), (2, D - 1, 'poppy'), (6, D - 1, 'oxeye_daisy'),
                    (W - 1, 3, 'dandelion'), (0, D - 1, 'fern')):
        if b == 'leaves':
            A(s, x, 0, z, 'oak_leaves', **LEAVES)
        else:
            A(s, x, 0, z, b)
    connect(s)
    return s


STRUCTURES = [dict(path='infirmary', name='Infirmary', tier=4, zone='inner', role='health', group='infirmary',
                   terrain='ground_level', door='front', make=infirmary, changes='First version.',
                   description='Over budget: BUDGET. White plaster ward in a dark oak frame under a spruce gable roof: six '
                               'beds (three on each side, heads to the wall, a window over each), bedside tables, a '
                               'hearth, herb barrels and shelves, a lean-to herb store; L2 adds the brewing stand and '
                               'cauldron, candles, lanterns, flower boxes, a door canopy and the banner; L3 a herb '
                               'garden, herb pots, more banners, a bench and a lantern post.')]

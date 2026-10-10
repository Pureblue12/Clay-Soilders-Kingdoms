"""Tree farm (tier 6, look A): the lumber camp's upgrade (brief "Lumber camp / tree farm", variant C). An 11 x 11
fenced plantation with rows of young trees and saplings either side of a central path, a woodcutter's shed at the back
in the sawmill's look (tools/revise1/lumber_camp.py: dark oak frame on cobblestone feet, spruce plank floor and boards,
an oak roof with dark oak eaves and a spruce ridge), a log yard and a timber gateway at the front.

v2 (quality check round 1): young trees (log + leaf head) in the L1 rows instead of bare dirt (saplings are UNKNOWN
blocks, so the real saplings come at L2 in the rows between); capped corner piers; the two big oaks that ran into the
shed are gone; the shed is a real 5 x 4 gabled shed with a door; L3 adds a lean-to drying shed over the log yard."""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, OPEN, SHELF, STAND, HANG, LEAVES, gable_roof, connect

W = D = 11
PATH = 5                                # the central path (x), from the gateway (z 0)
BEDS = ((1, 2, 3), (7, 8, 9))           # bed columns left and right of the path
TREE_ROWS = (1, 3)                      # L1: rows of young trees on dirt
SAPLING_ROWS = {2: 'oak_sapling', 4: 'birch_sapling'}   # L2: sapling rows between them (on the grass)
SX0, SX1, SZ0, SZ1 = 1, 5, 6, 9         # shed walls
DOOR = 3                                # shed door (x), in the front gable wall


def tree_farm():
    """Tree farm.

    Level 1 (shell and fittings): a spruce fence round the plot on capped cobblestone corner piers and dark oak posts,
    a timber gateway (dark oak posts, spruce lintel), a coarse dirt path to the woodcutter's shed. Four planting rows
    of young trees (a log stem with a leaf head, leaf whips between). The shed (5 x 4, 3 high walls): dark oak frame on
    cobblestone feet, spruce plank walls with glass windows, an oak gable roof with dark oak eaves and a spruce ridge,
    an oak door in the front gable; inside a chest, the grindstone (workstation) and a barrel. A log pile in the yard,
    a chopping block and the composter (soil for the rows).
    Level 2: saplings in the rows between (oak, birch), lanterns on the corner piers and under the gateway lintel, a
    lychgate roof on the gateway, shutters on the shed windows, a sawhorse, a cauldron and a barrel by the path.
    Level 3: a lean-to drying shed over the log yard (posts, a spruce roof, boards drying on a rack), a hanging sign
    under the gateway (text: decoration step), green banners by the shed door, a shelf of potted saplings inside,
    flowers at the row ends, moss on the logs, an axe in the chopping block (decoration step)."""
    s = Structure('tree_farm', (W, 8, D), palette='oak_a2')

    s.at(1, 'shell')
    # the fence: capped cobblestone piers at the corners, dark oak posts mid-side, spruce fence between
    for x in range(W):
        for z in range(D):
            if not (x in (0, W - 1) or z in (0, D - 1)):
                continue
            if x in (0, W - 1) and z in (0, D - 1):
                P(s, x, 0, z, 'cobblestone')
                P(s, x, 1, z, 'mossy_cobblestone' if (x + z) % 20 == 0 else 'cobblestone')
                P(s, x, 2, z, 'cobblestone_wall')                    # the cap
            elif (x in (0, W - 1) and z == 5) or (z == D - 1 and x == 8):
                P(s, x, 0, z, 'dark_oak_log', axis='y'); P(s, x, 1, z, 'dark_oak_log', axis='y')
            elif z == 0 and 3 <= x <= 7:
                continue                                             # the gateway
            else:
                P(s, x, 0, z, 'spruce_fence')
    for x in (3, 7):                                                 # the gateway: dark oak posts, a spruce lintel
        for y in (0, 1, 2):
            P(s, x, y, 0, 'dark_oak_log', axis='y')
    for x in (4, 5, 6):
        P(s, x, 2, 0, 'spruce_slab', type='top', waterlogged='false')
    # the shed: floor, dark oak corner posts on cobblestone feet, spruce plank walls, door and windows
    for x in range(SX0, SX1 + 1):
        for z in range(SZ0, SZ1 + 1):
            corner = x in (SX0, SX1) and z in (SZ0, SZ1)
            P(s, x, 0, z, 'cobblestone' if corner else 'spruce_planks')
            if not (x in (SX0, SX1) or z in (SZ0, SZ1)):
                continue
            for y in (1, 2, 3):
                if corner:
                    P(s, x, y, z, 'dark_oak_log', axis='y')
                elif x == DOOR and z == SZ0 and y < 3:
                    continue
                elif y == 2 and (x, z) in ((SX0, 7), (SX1, 8), (DOOR, SZ1)):
                    P(s, x, y, z, 'glass_pane')
                else:
                    P(s, x, y, z, 'spruce_planks')
    s.door(DOOR, 1, SZ0, facing='north', block='oak_door')
    gable_roof(s, SX0, SX1, SZ0, SZ1, 3, front=1, back=1, slope='oak_stairs', eave='dark_oak_stairs',
               ridge='dark_oak_planks', cap='spruce_slab', gable='spruce_planks', plates='dark_oak_log')
    # path to the shed door: up the middle, then left along the cross lane
    for z in range(0, 6):
        P(s, PATH, 0, z, 'coarse_dirt')
    for x in (DOOR, DOOR + 1):
        P(s, x, 0, 5, 'coarse_dirt')
    # planting rows: dirt, young trees (an oak log stem with a leaf head) at the ends, a leaf whip between
    for xs in BEDS:
        for z in TREE_ROWS:
            for x in xs:
                P(s, x, 0, z, 'dirt')

    s.at(1, 'fittings')
    for xs in BEDS:
        for z in TREE_ROWS:
            for x in xs:
                if x == xs[1]:
                    P(s, x, 1, z, 'oak_leaves', **LEAVES)                # a leaf whip
                else:
                    P(s, x, 1, z, 'oak_log', axis='y')                    # a young tree: stem and head
                    P(s, x, 2, z, 'oak_leaves', **LEAVES)
    P(s, 2, 1, SZ1 - 1, 'chest', facing='south', type='single', waterlogged='false')
    P(s, DOOR, 1, SZ1 - 1, 'grindstone', face='floor', facing='south')   # the workstation
    P(s, 4, 1, SZ1 - 1, 'barrel', facing='up', open='false')
    for z in (7, 8, 9):                                                  # the log yard: felled logs
        for x in (7, 8, 9):
            P(s, x, 0, z, 'oak_log', axis='x')
    for z in (8, 9):
        for x in (7, 8, 9):
            P(s, x, 1, z, 'oak_log', axis='x')
    P(s, 8, 2, 9, 'oak_log', axis='x')
    P(s, 1, 0, 5, 'stripped_oak_log', axis='y')                           # chopping block
    P(s, 9, 0, 5, 'composter')

    s.at(2, 'flourish2')
    for xs in BEDS:                                                      # sapling rows between the young trees
        for z, sap in SAPLING_ROWS.items():
            for x in xs:
                P(s, x, 0, z, sap, stage='0')
    for x in (0, W - 1):                                                 # lanterns on the corner piers' caps
        for z in (0, D - 1):
            P(s, x, 3, z, 'lantern', **STAND)
    for x in (4, 6):                                                     # lanterns under the gateway lintel
        P(s, x, 1, 0, 'lantern', **HANG)
    # a low gabled roof on the gateway (a lychgate): dark oak eaves, oak slopes, a dark oak ridge with a spruce cap
    for x, y, f, b in ((2, 2, 'east', 'dark_oak_stairs'), (3, 3, 'east', 'oak_stairs'), (4, 3, 'east', 'oak_stairs'),
                       (6, 3, 'west', 'oak_stairs'), (7, 3, 'west', 'oak_stairs'), (8, 2, 'west', 'dark_oak_stairs')):
        P(s, x, y, 0, b, facing=f, **STAIR)
    P(s, 5, 3, 0, 'dark_oak_planks'); P(s, 5, 4, 0, 'spruce_slab', **SLAB)
    for z in (6, 8):                                                     # shutters on the left window
        P(s, SX0 - 1, 2, z, 'spruce_trapdoor', facing='west', **OPEN)
    for z in (7, 9):                                                     # and the right one
        P(s, SX1 + 1, 2, z, 'spruce_trapdoor', facing='east', **OPEN)
    for x in (7, 9):                                                     # a sawhorse by the log yard
        P(s, x, 0, 6, 'oak_fence')
    for x in (7, 8, 9):
        P(s, x, 1, 6, 'spruce_slab', **SLAB)
    P(s, 6, 0, 5, 'cauldron')                                            # water for the saplings (decoration)
    P(s, 4, 0, 4, 'barrel', facing='up', open='false')                   # bone meal by the path

    s.at(3, 'flourish3')
    # the lean-to drying shed over the log yard: two posts at the front, a spruce roof rising to the back fence,
    # boards drying on a rail between the posts
    for x in (7, 9):
        for y in (1, 2):
            P(s, x, y, 7, 'spruce_fence')
    for x in (7, 8, 9):
        P(s, x, 3, 7, 'spruce_slab', **SLAB)
        P(s, x, 3, 8, 'spruce_stairs', facing='south', **STAIR)
        P(s, x, 4, 9, 'spruce_slab', **SLAB)
        P(s, x, 4, 10, 'spruce_slab', **SLAB)
    P(s, 8, 2, 7, 'oak_slab', type='top', waterlogged='false')
    P(s, 8, 1, 7, 'oak_slab', **SLAB)
    P(s, PATH, 1, 0, 'oak_hanging_sign', rotation='0', attached='false', waterlogged='false')
    s.decorate(PATH, 1, 0, 'sign_text', lines=['', 'Tree Farm', '', ''])
    for x in (DOOR - 1, DOOR + 1):                                       # green banners beside the shed door
        P(s, x, 2, SZ0 - 1, 'green_wall_banner', facing='north')
    P(s, 2, 2, SZ1 - 1, 'spruce_trapdoor', facing='north', **SHELF)      # a shelf of potted saplings
    P(s, 2, 3, SZ1 - 1, 'flower_pot'); s.decorate(2, 3, SZ1 - 1, 'potted_plant', plant='oak_sapling')
    P(s, 4, 2, SZ1 - 1, 'spruce_trapdoor', facing='north', **SHELF)
    P(s, 4, 3, SZ1 - 1, 'flower_pot'); s.decorate(4, 3, SZ1 - 1, 'potted_plant', plant='birch_sapling')
    for x, z, flower in ((4, 1, 'poppy'), (6, 1, 'dandelion'), (4, 3, 'cornflower'), (6, 3, 'oxeye_daisy'),
                         (6, 2, 'azure_bluet')):                         # flowers at the row ends
        P(s, x, 0, z, flower)
    for x, z in ((7, 8), (9, 9)):                                        # moss on the logs
        P(s, x, 2, z, 'moss_carpet')
    s.decorate(1, 1, 5, 'item_frame', item='minecraft:iron_axe', facing='up', note='axe in the chopping block')
    connect(s)
    return s


STRUCTURES = [dict(path='tree_farm', name='Tree farm', tier=6, zone='outer', role='resource', group='lumber_camp',
                   terrain='flexible', door='front', make=tree_farm,
                   changes='Quality check round 1: young trees (log stem, leaf head) in the L1 rows instead of bare '
                           'dirt; caps on the corner piers; the two big oaks that ran into the shed are gone; the shed '
                           'is now a real 5 x 4 gabled shed with a door and windows; L3 adds a lean-to drying shed over '
                           'the log yard.',
                   description='TBD')]

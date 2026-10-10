"""Tree farm (tier 6, look A): the lumber camp's upgrade (brief "Lumber camp / tree farm", variant C). A fenced
planting plot that holds only planting rows and walking lanes, and beside it, outside the fence, the woodcutter's yard:
a shed in the sawmill's look (tools/revise1/lumber_camp.py: dark oak frame on cobblestone feet, spruce plank walls, an
oak roof with dark oak eaves and a spruce ridge), the log yard and the tools.

v3, from Carlton's note on v1: "Take decoration from the inside and put them outside make sure the place where the
sapling sit are clear cause other sapling will sit there and grow". Nothing stands inside the fence but the dirt rows
(saplings from L2) and the lanes between them; every sapling spot is 3 blocks from the next, with open sky above it.
"""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, OPEN, SHELF, STAND, HANG, gable_roof, connect

W, H, D = 19, 8, 14
FX0, FX1, FZ0, FZ1 = 1, 11, 1, 13       # the plot's fence
GATE = 6                                # gateway centre (x) on the front fence
ROWS = {3: 'oak_sapling', 6: 'birch_sapling', 9: 'spruce_sapling'}   # dirt rows (x) and their saplings (L2)
ROW_Z = range(3, 13)                    # the dirt rows run z 3..12
SAPLING_Z = (3, 6, 9, 12)               # sapling spots along each row: 3 apart
SX0, SX1, SZ0, SZ1 = 13, 17, 9, 12      # the shed, outside the fence on the right
DOOR = 15                               # shed door (x), in the front gable wall
PATH_X = 15                             # the yard path from the front up to the shed door


def tree_farm():
    """Tree farm.

    Level 1 (shell and fittings): the planting plot: a spruce fence on capped cobblestone corner piers and dark oak
    posts, a timber gateway (dark oak posts, spruce lintel) on the front, three raised dirt rows (empty: the saplings
    are planted at L2) with walking lanes between. Outside the fence on the right, the woodcutter's yard: a 5 x 4 gabled
    shed (dark oak frame, spruce plank walls, glass windows, oak roof with dark oak eaves, an oak door), inside a chest,
    the grindstone (workstation) and a barrel; a cobblestone slab path from the gate to the shed, a log pile, a chopping
    block and the composter.
    Level 2: saplings in the rows (oak, birch, spruce), lanterns on the piers' caps and under the gateway lintel, a
    lychgate roof on the gateway, shutters on the shed windows, a sawhorse, a cauldron and a barrel by the gate.
    Level 3: a lean-to drying shed over the log pile, a hanging sign under the gateway (text: decoration step), green
    banners by the shed door, a shelf of potted saplings in the shed, flowers along the outside of the fence, moss on
    the logs, an axe in the chopping block (decoration step)."""
    s = Structure('tree_farm', (W, H, D), palette='oak_a2')

    s.at(1, 'shell')
    # ---- the planting plot: fence, capped corner piers, mid posts, gateway
    for x in range(FX0, FX1 + 1):
        for z in range(FZ0, FZ1 + 1):
            if not (x in (FX0, FX1) or z in (FZ0, FZ1)):
                continue
            if x in (FX0, FX1) and z in (FZ0, FZ1):
                P(s, x, 0, z, 'cobblestone')
                P(s, x, 1, z, 'mossy_cobblestone' if (x, z) == (FX0, FZ0) else 'cobblestone')
                P(s, x, 2, z, 'cobblestone_wall')                        # the cap
            elif (x in (FX0, FX1) and z == 7) or (z == FZ1 and x == GATE):
                P(s, x, 0, z, 'dark_oak_log', axis='y'); P(s, x, 1, z, 'dark_oak_log', axis='y')
            elif z == FZ0 and GATE - 2 <= x <= GATE + 2:
                continue                                                 # the gateway
            else:
                P(s, x, 0, z, 'spruce_fence')
    for x in (GATE - 2, GATE + 2):                                       # gateway posts and lintel
        for y in (0, 1, 2):
            P(s, x, y, FZ0, 'dark_oak_log', axis='y')
    for x in range(GATE - 1, GATE + 2):
        P(s, x, 2, FZ0, 'spruce_slab', type='top', waterlogged='false')
    # the planting rows: raised dirt, nothing above them
    for x in ROWS:
        for z in ROW_Z:
            P(s, x, 0, z, 'dirt')
    # ---- the woodcutter's shed, outside the fence on the right
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
                elif y == 2 and (x, z) in ((SX0, 11), (SX1, 10), (DOOR, SZ1)):
                    P(s, x, y, z, 'glass_pane')
                else:
                    P(s, x, y, z, 'spruce_planks')
    s.door(DOOR, 1, SZ0, facing='north', block='oak_door')
    gable_roof(s, SX0, SX1, SZ0, SZ1, 3, front=1, back=1, slope='oak_stairs', eave='dark_oak_stairs',
               ridge='dark_oak_planks', cap='spruce_slab', gable='spruce_planks', plates='dark_oak_log')
    # ---- paths (cobblestone slabs, half a block high): from the gate into the plot's front lane, and along the front
    # to the shed door
    path = [(GATE, z) for z in (0, 1, 2)] + [(x, 0) for x in range(GATE + 1, PATH_X + 1)] + \
        [(PATH_X, z) for z in range(1, SZ0)]
    for x, z in path:
        P(s, x, 0, z, 'cobblestone_slab', **SLAB)

    s.at(1, 'fittings')
    P(s, 14, 1, SZ1 - 1, 'chest', facing='south', type='single', waterlogged='false')
    P(s, DOOR, 1, SZ1 - 1, 'grindstone', face='floor', facing='south')   # the workstation
    P(s, 16, 1, SZ1 - 1, 'barrel', facing='up', open='false')
    for x in (16, 17, 18):                                               # the log pile beside the path
        for z in (4, 5, 6):
            P(s, x, 0, z, 'oak_log', axis='z')
        for z in (5, 6):
            P(s, x, 1, z, 'oak_log', axis='z')
    P(s, 17, 2, 6, 'oak_log', axis='z')
    P(s, 13, 0, 7, 'stripped_oak_log', axis='y')                          # chopping block
    P(s, 12, 0, 8, 'composter')                                          # soil for the rows

    s.at(2, 'flourish2')
    for x, sap in ROWS.items():                                          # the saplings, 3 apart in every row
        for z in SAPLING_Z:
            P(s, x, 1, z, sap, stage='0')
    for x in (FX0, FX1):                                                 # lanterns on the corner piers' caps
        for z in (FZ0, FZ1):
            P(s, x, 3, z, 'lantern', **STAND)
    for x in (GATE - 1, GATE + 1):                                       # lanterns under the gateway lintel
        P(s, x, 1, FZ0, 'lantern', **HANG)
    # a low gabled roof on the gateway (a lychgate): dark oak eaves, oak slopes, a dark oak ridge with a spruce cap
    for dx, y, f, b in ((-3, 2, 'east', 'dark_oak_stairs'), (-2, 3, 'east', 'oak_stairs'), (-1, 3, 'east', 'oak_stairs'),
                        (1, 3, 'west', 'oak_stairs'), (2, 3, 'west', 'oak_stairs'), (3, 2, 'west', 'dark_oak_stairs')):
        P(s, GATE + dx, y, FZ0, b, facing=f, **STAIR)
    P(s, GATE, 3, FZ0, 'dark_oak_planks'); P(s, GATE, 4, FZ0, 'spruce_slab', **SLAB)
    for z in (10, 12):                                                   # shutters: left window
        P(s, SX0 - 1, 2, z, 'spruce_trapdoor', facing='west', **OPEN)
    for z in (9, 11):                                                    # right window
        P(s, SX1 + 1, 2, z, 'spruce_trapdoor', facing='east', **OPEN)
    for x in (12, 14):                                                   # a sawhorse in the yard
        P(s, x, 0, 4, 'oak_fence')
    for x in (12, 13, 14):
        P(s, x, 1, 4, 'spruce_slab', **SLAB)
    P(s, GATE - 2, 0, 0, 'cauldron')                                     # water for the saplings, by the gate
    P(s, GATE - 3, 0, 0, 'barrel', facing='up', open='false')            # bone meal

    s.at(3, 'flourish3')
    # the lean-to drying shed over the log pile: posts at the front, a spruce roof rising to the back, boards on a rail
    for x in (16, 18):
        for y in (0, 1, 2):
            P(s, x, y, 3, 'spruce_fence')
    for x in (16, 17, 18):
        P(s, x, 3, 3, 'spruce_slab', **SLAB)
        P(s, x, 3, 4, 'spruce_stairs', facing='south', **STAIR)
        P(s, x, 4, 5, 'spruce_slab', **SLAB)
        P(s, x, 4, 6, 'spruce_slab', **SLAB)
    P(s, 17, 2, 3, 'oak_slab', type='top', waterlogged='false')         # boards drying on the rail
    P(s, 17, 1, 3, 'oak_slab', **SLAB)
    P(s, GATE, 1, FZ0, 'oak_hanging_sign', rotation='0', attached='false', waterlogged='false')
    s.decorate(GATE, 1, FZ0, 'sign_text', lines=['', 'Tree Farm', '', ''])
    for x in (DOOR - 1, DOOR + 1):                                       # green banners beside the shed door
        P(s, x, 2, SZ0 - 1, 'green_wall_banner', facing='north')
    for x, plant in ((14, 'oak_sapling'), (16, 'birch_sapling')):        # a shelf of potted saplings in the shed
        P(s, x, 2, SZ1 - 1, 'spruce_trapdoor', facing='north', **SHELF)
        P(s, x, 3, SZ1 - 1, 'flower_pot')
        s.decorate(x, 3, SZ1 - 1, 'potted_plant', plant=plant)
    for x, z, flower in ((0, 3, 'poppy'), (0, 5, 'cornflower'), (0, 8, 'dandelion'), (0, 11, 'oxeye_daisy'),
                         (1, 0, 'poppy'), (2, 0, 'azure_bluet'), (12, 1, 'dandelion'), (13, 2, 'cornflower'),
                         (12, 11, 'oxeye_daisy'), (12, 12, 'poppy')):   # flowers along the outside of the fence
        P(s, x, 0, z, flower)
    for x, z in ((16, 5), (18, 6)):                                      # moss on the logs
        P(s, x, 2, z, 'moss_carpet')
    s.decorate(13, 1, 7, 'item_frame', item='minecraft:iron_axe', facing='up', note='axe in the chopping block')
    connect(s)
    return s


STRUCTURES = [dict(path='tree_farm', name='Tree farm', tier=6, zone='outer', role='resource', group='lumber_camp',
                   terrain='flexible', door='front', make=tree_farm,
                   changes='Carlton\'s note: "Take decoration from the inside and put them outside make sure the place '
                           'where the sapling sit are clear cause other sapling will sit there and grow". The fenced '
                           'plot now holds only three raised dirt rows and the walking lanes between them: the young '
                           'trees, bushes and every prop are gone from it. Saplings (oak, birch, spruce) come at L2, '
                           'each 3 blocks from the next, with nothing above them (no leaves, roofs or lanterns over '
                           'the rows); at L1 the rows are empty dirt. Shed, log pile, chopping block, composter, '
                           'sawhorse, drying lean-to, cauldron, barrels, banners and flowers moved outside the fence: '
                           'into a woodcutter\'s yard on the right, by the gate and along the fence.',
                   description='Over budget: 344 blocks vs 102 (the lumber camp brief; the tree farm has no budget of '
                               'its own), first build 268 vs 200; 19 x 14 instead of 11 x 11 because the yard moved '
                               'outside the plot. The lumber camp\'s tier-6 upgrade: a fenced planting plot (11 x 13, '
                               'spruce fence on capped cobblestone piers, a timber gateway) holding only three raised '
                               'dirt rows and lanes, saplings 3 apart with open sky above; beside it, outside the fence, '
                               'the woodcutter\'s yard: a 5 x 4 gabled shed in the sawmill\'s look (oak door; chest, '
                               'grindstone workstation, barrel), a log pile, chopping block, composter, cobblestone slab '
                               'paths. L1: plot with empty rows, shed, yard. L2: oak, birch and spruce saplings in the '
                               'rows, a lychgate roof with lanterns, lanterns on the piers, shutters, a sawhorse, a '
                               'cauldron and barrel by the gate. L3: a lean-to drying shed over the logs, a hanging sign '
                               '(the Studio does not draw hanging signs), banners by the shed door, potted saplings on '
                               'a shelf, flowers along the outside of the fence, moss, an axe in the block.')]

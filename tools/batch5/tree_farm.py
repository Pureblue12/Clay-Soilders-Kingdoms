"""Tree farm (tier 6, look A): the lumber camp's upgrade (brief "Lumber camp / tree farm", variant C). An 11 x 11
fenced plantation with rows of saplings either side of a central path, a woodcutter's shed at the back in the
sawmill's look (tools/revise1/lumber_camp.py: dark oak frame on cobblestone feet, spruce plank floor and boards, an
oak roof with dark oak eaves and a spruce ridge), a log yard and a timber gateway at the front."""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, OPEN, SHELF, STAND, HANG, LEAVES, connect, noise  # noqa: F401

W = D = 11
H = 7
PATH = 5                               # the central path (x), from the gateway (z 0) to the shed
ROWS = (1, 3, 5)                       # sapling rows (z)
BEDS = ((1, 2, 3), (7, 8, 9))          # bed columns left and right of the path
TREES = ((2, 5), (8, 5))               # young trees grown from the first planting (row z 5, middle of each bed)
SAPLINGS = {1: 'oak_sapling', 3: 'birch_sapling', 5: 'spruce_sapling'}
SX0, SX1, SZ0, SZ1 = 3, 7, 7, 9        # shed posts


def young_tree(s, x, z):
    """An oak log trunk (3 high) with a small round crown of persistent leaves."""
    for y in (1, 2, 3):
        P(s, x, y, z, 'oak_log', axis='y')
    for y in (3, 4, 5):
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                if dx and dz and (y != 4 or noise(x + dx, y, z + dz, 3) < 0.5):
                    continue                                      # a round crown, two ragged corners in the middle
                if y == 5 and (dx or dz) and noise(x + dx, y, z + dz, 4) < 0.5:
                    continue
                A(s, x + dx, y, z + dz, 'oak_leaves', **LEAVES)


def tree_farm():
    """Tree farm.

    Level 1 (shell and fittings): a spruce fence round the plot on cobblestone corner piers and dark oak posts, a
    timber gateway (dark oak posts and beam) on the front, a coarse dirt path to the woodcutter's shed at the back. Six
    planting rows of dirt (the saplings are planted by the decoration step at this level) and two young oak trees
    grown from the first planting. The shed: dark oak posts on cobblestone feet, spruce plank floor and back wall, an
    oak gable roof with dark oak eaves and a spruce ridge; inside a chest, the grindstone (workstation) and a barrel.
    A log pile in the yard on the right, a chopping block, a woodpile and the composter (sapling soil) on the left.
    Level 2: saplings in every row (oak, birch, spruce), lanterns on the corner piers and under the gateway beam, a
    sawhorse with a cut log, a water cauldron by the beds, a slab roof on the gateway.
    Level 3: a hanging sign under the gateway (text: decoration step), green banners on the gateway posts, a nursery
    shelf of potted saplings on the shed wall, bee nests on the young trees, flowers at the row ends, moss on the log
    pile, an axe in the chopping block and tools on the shed wall (decoration step)."""
    s = Structure('tree_farm', (W, H, D), palette='oak_a2')

    s.at(1, 'shell')
    # the fence: cobblestone piers at the corners, dark oak posts mid-side, spruce fence between
    for x in range(W):
        for z in range(D):
            if not (x in (0, W - 1) or z in (0, D - 1)):
                continue
            if x in (0, W - 1) and z in (0, D - 1):
                P(s, x, 0, z, 'cobblestone'); P(s, x, 1, z, 'mossy_cobblestone' if x == 0 and z == 0 else 'cobblestone')
            elif (x in (0, W - 1) and z == 5) or (z == D - 1 and x in (2, 8)):
                P(s, x, 0, z, 'dark_oak_log', axis='y'); P(s, x, 1, z, 'dark_oak_log', axis='y')
            elif z == 0 and 3 <= x <= 7:
                continue                                          # the gateway
            else:
                P(s, x, 0, z, 'spruce_fence')
    # the gateway: two dark oak posts and a spruce lintel over the 3-wide entrance
    for x in (3, 7):
        for y in (0, 1, 2):
            P(s, x, y, 0, 'dark_oak_log', axis='y')
    for x in (4, 5, 6):
        P(s, x, 2, 0, 'spruce_slab', type='top', waterlogged='false')
    # the shed floor (spruce planks, cobblestone feet under the posts) and the frame
    for x in range(SX0, SX1 + 1):
        for z in range(SZ0, SZ1 + 1):
            corner = x in (SX0, SX1) and z in (SZ0, SZ1)
            P(s, x, 0, z, 'cobblestone' if corner else 'spruce_planks')
    for x in (SX0, SX1):
        for z in (SZ0, SZ1):
            for y in (1, 2, 3):
                P(s, x, y, z, 'dark_oak_log', axis='y')
    for x in range(SX0, SX1 + 1):                                  # plates along the front and back
        P(s, x, 4, SZ0, 'dark_oak_log', axis='x'); P(s, x, 4, SZ1, 'dark_oak_log', axis='x')
    for x in (SX0, SX1):                                           # tie beams at the gable ends
        P(s, x, 4, SZ0 + 1, 'dark_oak_log', axis='z')
    for x in range(SX0 + 1, SX1):                                  # back wall: spruce boards, a window
        for y in (1, 2, 3):
            if (x, y) == (PATH, 2):
                P(s, x, y, SZ1, 'glass_pane')
            else:
                P(s, x, y, SZ1, 'spruce_planks')
    for z in (SZ0 + 1,):                                           # side walls: a low board wall, open above
        for x in (SX0, SX1):
            P(s, x, 1, z, 'spruce_planks')
    # the roof: ridge along x, oak slopes, dark oak eaves and gable ends, a spruce slab ridge cap, overhang all round
    for x in range(SX0 - 1, SX1 + 2):
        gable = x in (SX0 - 1, SX1 + 1)
        P(s, x, 4, SZ0 - 1, 'dark_oak_stairs', facing='south', **STAIR)
        P(s, x, 4, SZ1 + 1, 'dark_oak_stairs', facing='north', **STAIR)
        P(s, x, 5, SZ0, 'dark_oak_stairs' if gable else 'oak_stairs', facing='south', **STAIR)
        P(s, x, 5, SZ1, 'dark_oak_stairs' if gable else 'oak_stairs', facing='north', **STAIR)
        P(s, x, 5, SZ0 + 1, 'dark_oak_planks')
        P(s, x, 6, SZ0 + 1, 'spruce_slab', **SLAB)
    for x in (SX0 - 1, SX1 + 1):                                   # trim under the gable overhangs
        P(s, x, 4, SZ0, 'dark_oak_stairs', facing='south', **TOP)
        P(s, x, 4, SZ1, 'dark_oak_stairs', facing='north', **TOP)
    # path and planting rows
    for z in range(0, SZ0):
        P(s, PATH, 0, z, 'coarse_dirt')
    for xs in BEDS:
        for z in ROWS:
            for x in xs:
                P(s, x, 0, z, 'dirt')

    s.at(1, 'fittings')
    for x, z in TREES:
        young_tree(s, x, z)
    P(s, SX0 + 1, 1, SZ0 + 1, 'chest', facing='south', type='single', waterlogged='false')
    P(s, PATH, 1, SZ0 + 1, 'grindstone', face='floor', facing='south')        # the workstation
    P(s, SX1 - 1, 1, SZ0 + 1, 'barrel', facing='up', open='false')
    # the log yard on the right: a pile of felled logs
    for x in (8, 9):
        for z in (7, 8, 9):
            P(s, x, 0, z, 'oak_log', axis='z')
    for z in (7, 8, 9):
        P(s, 9, 1, z, 'oak_log', axis='z')
    P(s, 8, 1, 8, 'oak_log', axis='z')
    # the left yard: chopping block, a woodpile of split logs, the composter (soil for the sapling rows)
    P(s, 1, 0, 7, 'stripped_oak_log', axis='y')
    for z in (8, 9):
        P(s, 1, 0, z, 'stripped_spruce_log', axis='x'); P(s, 2, 0, z, 'stripped_spruce_log', axis='x')
    P(s, 1, 1, 9, 'stripped_spruce_log', axis='x')
    P(s, 2, 0, 7, 'composter')
    for xs in BEDS:                                                # saplings planted by the decoration step at L1
        for z in ROWS:
            for x in xs:
                if (x, z) not in TREES:
                    s.decorate(x, 1, z, 'sapling', sapling=SAPLINGS[z], note='planted by the woodcutters')

    s.at(2, 'flourish2')
    for xs in BEDS:                                                # the sapling rows, one kind per row
        for z in ROWS:
            for x in xs:
                if (x, z) not in TREES:
                    P(s, x, 1, z, SAPLINGS[z], stage='0')
    for x in (0, W - 1):                                           # lanterns on the corner piers
        for z in (0, D - 1):
            P(s, x, 2, z, 'lantern', **STAND)
    for x in (4, 6):                                               # lanterns under the gateway lintel
        P(s, x, 1, 0, 'lantern', **HANG)
    # a low gabled roof on the gateway (a lychgate): dark oak eaves, oak slopes, a dark oak ridge with a spruce cap
    for x, y, f, b in ((2, 2, 'east', 'dark_oak_stairs'), (3, 3, 'east', 'oak_stairs'), (4, 3, 'east', 'oak_stairs'),
                       (6, 3, 'west', 'oak_stairs'), (7, 3, 'west', 'oak_stairs'), (8, 2, 'west', 'dark_oak_stairs')):
        P(s, x, y, 0, b, facing=f, **STAIR)
    P(s, 5, 3, 0, 'dark_oak_planks'); P(s, 5, 4, 0, 'spruce_slab', **SLAB)
    for x in (PATH - 1, PATH + 1):                                 # shutters beside the back window
        P(s, x, 2, SZ1 + 1, 'spruce_trapdoor', facing='south', **OPEN)
    for x in (7, 9):                                               # a sawhorse with a cut log, by the log yard
        P(s, x, 0, 6, 'oak_fence')
    for x in (7, 8, 9):
        P(s, x, 1, 6, 'spruce_slab', **SLAB)
    P(s, 6, 0, 6, 'cauldron')                                      # water for the saplings (the water: decoration)
    P(s, 4, 0, 6, 'barrel', facing='up', open='false')             # a barrel of bone meal by the path

    s.at(3, 'flourish3')
    P(s, PATH, 1, 0, 'oak_hanging_sign', rotation='0', attached='false', waterlogged='false')
    s.decorate(PATH, 1, 0, 'sign_text', lines=['', 'Tree Farm', '', ''])
    for x in (SX0, SX1):                                           # green banners on the shed's front posts
        P(s, x, 3, SZ0 - 1, 'green_wall_banner', facing='north')
    for x in (SX0 + 1, SX1 - 1):                                   # nursery shelf: trapdoors on the back wall,
        P(s, x, 2, SZ1 - 1, 'spruce_trapdoor', facing='north', **SHELF)   # potted saplings on them
        P(s, x, 3, SZ1 - 1, 'flower_pot')
        s.decorate(x, 3, SZ1 - 1, 'potted_plant', plant='oak_sapling' if x < PATH else 'birch_sapling')
    s.decorate(PATH, 2, SZ1 - 1, 'item_frame', item='minecraft:iron_axe', facing='north', note='tools on the shed wall')
    for x, z in TREES:                                             # bee nests on the young trees
        P(s, x, 2, z - 1, 'bee_nest', facing='north', honey_level='0')
    for x, z, flower in ((4, 2, 'poppy'), (6, 2, 'dandelion'), (4, 4, 'cornflower'), (6, 4, 'oxeye_daisy'),
                         (4, 1, 'azure_bluet'), (6, 1, 'poppy')):  # flowers at the row ends
        P(s, x, 0, z, flower)
    for x, z in ((9, 7), (9, 9), (8, 8)):                          # moss on the log pile
        P(s, x, 2, z, 'moss_carpet')
    P(s, 8, 2, 6, 'oak_log', axis='x')                             # a log on the sawhorse
    s.decorate(1, 1, 7, 'item_frame', item='minecraft:iron_axe', facing='up', note='axe in the chopping block')
    connect(s)
    return s


STRUCTURES = [dict(path='tree_farm', name='Tree farm', tier=6, zone='outer', role='resource', group='lumber_camp',
                   terrain='flexible', door=None, make=tree_farm, changes='First version.',
                   description='Over budget: 284 blocks vs 102 (the lumber camp brief; the tree farm has no budget of '
                               'its own), first build 226 vs 200. The lumber camp\'s tier-6 upgrade: an 11 x 11 fenced '
                               'plantation (spruce fence on cobblestone corner piers) with three rows of saplings each '
                               'side of a coarse dirt path, two young oaks from the first planting, and the woodcutter\'s '
                               'shed at the back in the sawmill\'s look (dark oak frame, oak roof with dark oak eaves; '
                               'chest, grindstone workstation, barrel), a log pile, woodpile, chopping block and composter. '
                               'L1: plot, gateway posts, shed, empty planting rows (saplings by the decoration step), '
                               'young trees. L2: saplings in every row (oak, birch, spruce), a lychgate roof with '
                               'lanterns, lanterns on the piers, a sawhorse, a cauldron and a barrel by the path. L3: a '
                               'hanging sign (the Studio does not draw hanging signs), banners on the shed, a nursery '
                               'shelf of potted saplings, bee nests on the oaks, flowers, moss, an axe in the block.')]

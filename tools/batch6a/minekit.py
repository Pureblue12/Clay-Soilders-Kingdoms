"""Shared parts of mine 2 and mine 3 (no structures of its own).

Both are the approved mine stage 1 (tools/batch3/mine.py, copied level for level: its L1 into L1, its L2 into L2...)
with more beside it on the right (x 11 and up):
- mine 2 (stage 2, tier 5, gold): a headframe over a shaft, an ore-sorting shed behind it, a gold lockup in front;
- mine 3 (stage 3, tier 7, gems): all of mine 2 plus a winch house and a gem cutter's bench (tools/batch6a/mine3.py).

Builders never dig (D60): the shaft is a stone collar round one open cell (13, 0, 4) where the miners dig down (D6).

Coordinates: x = width, z = 0 is the front, y = 0 rests on the ground.
"""
from batch3 import mine as stage1
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, HANG, STAND, LEAVES, noise

STRUCTURES = []   # helper module: nothing to build

SOIL = {'grass_block', 'dirt', 'coarse_dirt', 'podzol', 'rooted_dirt', 'moss_block'}
PLANTS = ('poppy', 'dandelion', 'cornflower', 'fern', 'allium', 'azure_bluet', 'oxeye_daisy', 'blue_orchid',
          'lily_of_the_valley', 'short_grass', 'pink_petals')

# the headframe: legs at the corners of a 3 x 3 round the shaft
HX0, HX1, HZ0, HZ1 = 12, 14, 3, 5
SX, SZ = 13, 4                  # the shaft (open cell, miners dig down here)
LEG_TOP = 5                     # legs y 0..5, deck at y 6, sheave and railing at y 7
DECK = 6


def copy_stage1(s):
    """The approved mine stage 1, block for block and level for level. Its four flowers on cobblestone (QC note:
    they can't be placed in game) get coarse dirt under them, or become moss carpet where they stand on a stair."""
    m = stage1.mine()
    for pos, b in m.blocks.items():
        s.blocks[pos] = b
        s.layer_of[pos] = m.layer_of[pos]
    for lv, ds in m.decorations.items():
        s.decorations[lv].extend(ds)
    for (x, y, z), (name, props, lv) in list(s.blocks.items()):
        if name not in PLANTS or y == 0:
            continue
        below = s.blocks.get((x, y - 1, z))
        if not below or below[0] in SOIL:
            continue
        if below[0] in ('cobblestone', 'mossy_cobblestone', 'gravel'):
            s.blocks[(x, y - 1, z)] = ('coarse_dirt', {}, below[2])
        else:
            s.blocks[(x, y, z)] = ('moss_carpet', {}, lv)


def headframe_l1(s, winch):
    """The shaft collar, four dark oak legs, braces, a plank deck with a hatch, a railing, the sheave in its frame,
    the hoist chain down the shaft with the ore tub (a barrel) on it. winch: leave the railing open on the right
    where mine 3's rope runs to the winch house."""
    for x in range(HX0, HX1 + 1):                 # the collar: dressed stone round the shaft, a low lip at the front
        for z in range(HZ0, HZ1 + 1):
            if (x, z) == (SX, SZ):
                continue
            if x in (HX0, HX1) and z in (HZ0, HZ1):
                continue                          # legs
            P(s, x, 0, z, 'cobblestone_slab' if z == HZ0 else 'cobblestone', **(SLAB if z == HZ0 else {}))
    for x in (HX0, HX1):                          # the legs
        for z in (HZ0, HZ1):
            for y in range(0, LEG_TOP + 1):
                P(s, x, y, z, 'dark_oak_log', axis='y')
    for y in (2, LEG_TOP):                        # braces half way up and top plates
        b = 'stripped_spruce_log' if y == 2 else 'dark_oak_log'
        P(s, SX, y, HZ0, b, axis='x'); P(s, SX, y, HZ1, b, axis='x')
        P(s, HX0, y, SZ, b, axis='z'); P(s, HX1, y, SZ, b, axis='z')
    for (x, z), f in (((SX, HZ0 - 1), 'north'), ((SX, HZ1 + 1), 'south'), ((HX0 - 1, SZ), 'west'),
                      ((HX1 + 1, SZ), 'east')):   # corbels under the deck's overhang
        P(s, x, LEG_TOP, z, 'dark_oak_stairs', facing={'north': 'south', 'south': 'north', 'west': 'east',
                                                           'east': 'west'}[f], **TOP)
    for x in range(HX0 - 1, HX1 + 2):             # the deck, one out all round, a hatch over the ladder (front left)
        for z in range(HZ0 - 1, HZ1 + 2):
            if (x, z) == (SX, SZ):
                continue
            if (x, z) == (HX0 - 1, HZ0):
                P(s, x, DECK, z, 'spruce_trapdoor', facing='east', **OPEN)
                continue
            P(s, x, DECK, z, 'spruce_slab' if x in (HX0 - 1, HX1 + 1) or z in (HZ0 - 1, HZ1 + 1) else 'spruce_planks',
              **(SLAB_TOP if x in (HX0 - 1, HX1 + 1) or z in (HZ0 - 1, HZ1 + 1) else {}))
    for x in range(HX0 - 1, HX1 + 2):             # railing round the deck
        for z in range(HZ0 - 1, HZ1 + 2):
            edge = x in (HX0 - 1, HX1 + 1) or z in (HZ0 - 1, HZ1 + 1)
            if not edge or (x, z) == (HX0 - 1, HZ0) or (x, z) == (HX0 - 1, HZ0 - 1):
                continue                          # the hatch and the step off the ladder
            if winch and (x, z) == (HX1 + 1, SZ):
                continue                          # mine 3: the rope runs out here
            P(s, x, DECK + 1, z, 'spruce_fence')
    P(s, SX, DECK + 1, SZ - 1, 'dark_oak_log', axis='y')   # the sheave's frame and the sheave (a grindstone wheel)
    P(s, SX, DECK + 1, SZ + 1, 'dark_oak_log', axis='y')
    P(s, SX, DECK + 1, SZ, 'grindstone', face='wall', facing='east')
    P(s, SX, DECK + 2, SZ - 1, 'spruce_slab', **SLAB); P(s, SX, DECK + 2, SZ + 1, 'spruce_slab', **SLAB)
    P(s, SX, DECK + 2, SZ, 'spruce_slab', **SLAB)
    for y in range(2, DECK + 1):                  # the hoist chain down the shaft
        P(s, SX, y, SZ, 'chain', axis='y')
    P(s, SX, 1, SZ, 'barrel', facing='up', open='true')     # the ore tub on its end


def fittings_l1(s):
    """The banksman's chest and pit props by the collar, a lantern on the front legs, the landing plank."""
    P(s, 11, 0, 6, 'chest', facing='east', type='single', waterlogged='false')
    P(s, 15, 0, 6, 'barrel', facing='up', open='false')
    for z in (4, 5):
        P(s, 16, 0, z, 'stripped_spruce_log', axis='z')       # pit props, stacked
    P(s, 16, 1, 5, 'stripped_spruce_log', axis='z')
    for x in (HX0, HX1):                          # torches on the front of the front legs
        P(s, x, 2, HZ0 - 1, 'wall_torch', facing='north')


def sorting_shed_l2(s):
    """Stage 2's new job: the ore-sorting shed behind the headframe (x 12..16, z 8..10), a lean-to open to the
    front: log posts, plank back and sides on a stone footing, a 45-degree spruce roof, a sorting bench, a washing
    tub, sorted heaps of ore in front."""
    x0, x1 = 12, 16
    for x in (x0, x1):
        for y in range(0, 3):
            P(s, x, y, 8, 'spruce_log', axis='y')
        for y in range(0, 5):
            P(s, x, y, 10, 'spruce_log', axis='y')
        for y in range(0, 4):                     # side walls
            P(s, x, y, 9, 'cobblestone' if y == 0 else 'spruce_planks')
        P(s, x, 4, 9, 'spruce_planks')
    for x in range(x0 + 1, x1):                   # back wall on a stone footing, a small window
        for y in range(0, 5):
            P(s, x, y, 10, 'cobblestone' if y == 0 else 'glass_pane' if (y == 2 and x == 14) else 'spruce_planks')
    for x in range(x0, x1 + 1):
        P(s, x, 3, 8, 'stripped_spruce_log', axis='x')         # the front plate
    for x in range(x0 - 1, x1 + 2):               # roof: 45 degrees, down to the front, one over at the sides
        P(s, x, 3, 7, 'spruce_stairs', facing='south', **STAIR)
        P(s, x, 4, 8, 'spruce_stairs', facing='south', **STAIR)
        A(s, x, 5, 9, 'spruce_stairs', facing='south', **STAIR)
        A(s, x, 5, 10, 'spruce_slab', **SLAB)
    for x in (x0 - 1, x1 + 1):                    # brackets under the side overhangs
        P(s, x, 2, 8, 'spruce_trapdoor', facing='west' if x < x0 else 'east', **OPEN)
    P(s, 13, 0, 9, 'crafting_table')              # the sorting bench
    P(s, 14, 0, 9, 'spruce_slab', **SLAB_TOP)
    P(s, 15, 0, 9, 'cauldron')                    # washing tub
    P(s, 14, 1, 9, 'lantern', **STAND)
    for x, b in ((12, 'gravel'), (13, 'raw_iron_block'), (15, 'coal_block'), (16, 'gravel')):
        P(s, x, 0, 7, b)                          # sorted heaps under the eave
    P(s, 14, 2, 8, 'lantern', **HANG)              # under the front plate


def headframe_l2(s):
    """A ladder up the front left leg, lanterns under the deck, a rail spur from the front to the collar, a raking
    strut of stairs on the shed side."""
    for y in range(0, DECK):
        P(s, HX0 - 1, y, HZ0, 'ladder', facing='west', waterlogged='false')
    for x, z in ((SX, HZ0 - 1), (SX, HZ1 + 1)):
        P(s, x, LEG_TOP - 1, z, 'lantern', **HANG)
    for z in range(0, HZ0):
        P(s, SX, 0, z, 'rail', shape='north_south', waterlogged='false')
    s.decorate(SX, 0, 1, 'minecart', note='an ore cart on the spur')
    for x, z in ((HX1, 2), (HX0, 2)):
        P(s, x, 0, z, 'cobblestone_wall')         # buffer posts at the end of the spur


def gold_l3(s):
    """Stage 2's gold: a stone lockup for the gold (x 15..17, z 0..2) with an iron door and a yellow banner, a guard's
    lantern post, banners on the headframe, a sign, flowers and moss round the collar."""
    z0, z1 = 1, 3                                 # the lockup: 3 x 3, door in the front, barred windows
    for x in range(15, 18):
        for z in range(z0, z1 + 1):
            for y in range(0, 3):
                if (x, z) == (16, 2) or ((x, z) == (16, z0) and y < 2):
                    continue                      # inside, and the door
                corner = x in (15, 17) and z in (z0, z1)
                win = y == 1 and z == 2 and x in (15, 17)
                P(s, x, y, z, 'iron_bars' if win else 'stone_bricks' if corner or y == 0 else 'cobblestone')
    for x in range(14, 19):                       # a little hip roof of stone brick stairs, one over all round
        for z in range(z0 - 1, z1 + 2):
            edge_x, edge_z = x in (14, 18), z in (z0 - 1, z1 + 1)
            if edge_x and edge_z:
                continue
            if edge_x or edge_z:
                f = 'east' if x == 14 else 'west' if x == 18 else 'south' if z == z0 - 1 else 'north'
                A(s, x, 3, z, 'stone_brick_stairs', facing=f, **STAIR)   # (it meets the headframe leg)
            else:
                P(s, x, 3, z, 'stone_bricks')
    for x, z in ((15, 2), (17, 2), (16, 1), (16, 3)):
        P(s, x, 4, z, 'stone_brick_slab', **SLAB)
    P(s, 16, 4, 2, 'chiseled_stone_bricks')
    P(s, 16, 5, 2, 'lantern', **STAND)
    for half, y in (('lower', 0), ('upper', 1)):
        P(s, 16, y, z0, 'iron_door', facing='south', half=half, hinge='left', open='false', powered='false')
    A(s, 16, 2, z0, 'stone_bricks')               # the lintel over the door
    P(s, 16, 0, 2, 'chest', facing='north', type='single', waterlogged='false')
    s.decorate(16, 0, 2, 'item', item='minecraft:gold_ingot', note="the mine's gold in the lockup")
    P(s, 16, 1, 2, 'lantern', **STAND)
    for x in (15, 17):                            # yellow banners either side of the door
        P(s, x, 2, z0 - 1, 'yellow_wall_banner', facing='north')
    P(s, 18, 0, 0, 'cobblestone_wall'); P(s, 18, 1, 0, 'oak_fence'); P(s, 18, 2, 0, 'lantern', **STAND)   # guard's lamp
    for x, z in ((HX0 - 1, HZ1 + 1), (HX1 + 1, HZ1 + 1)):  # banners flying from the back corners of the railing
        P(s, x, DECK + 2, z, 'yellow_banner', rotation='0')
    P(s, HX0 - 1, DECK + 2, HZ0 - 1, 'red_banner', rotation='0')
    P(s, HX0 - 1, 3, HZ1, 'oak_wall_sign', facing='west', waterlogged='false')
    s.decorate(HX0 - 1, 3, HZ1, 'sign_text', lines=['', 'Gold Mine', '', ''])
    for x, z, b in ((11, 7, 'poppy'), (17, 4, 'dandelion'), (17, 6, 'fern'), (11, 10, 'cornflower'),
                    (17, 10, 'dandelion'), (16, 3, 'fern')):
        if (x, 0, z) not in s.blocks:
            P(s, x, 0, z, b)
    for x, z in ((12, 6), (14, 6), (12, 2)):
        A(s, x, 0, z, 'moss_carpet')

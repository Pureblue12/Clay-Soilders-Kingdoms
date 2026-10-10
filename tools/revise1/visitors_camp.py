"""Visitors' camp (tier 1, stage 1 of the inn), revised from Carlton's notes: l1 "cant tell, not much in terms of detail,
need more"; l2 and l3 rejected with no note. Rebuilt as a readable traveller's camp.

v4 (note on v3: "I'd like it to get wider and more detail please"; QC: the tents read as stepped wool lumps with
spotty stripes, and the layout is scattered): 21 wide instead of 15, and grouped round the fire: two clean A-frame
tents face the fire from either side, the canopy bed, the woodpile and the supply stall stand along the back, the cart,
the table and the hitching rail along the front, a trodden earth patch (brown carpet) round the fire ring.
"""
from make_batch1_structures import Structure
from revise1.blockconn import connect, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
SLAB = dict(type='bottom', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')
TRAP = dict(half='bottom', powered='false', waterlogged='false')


def a_tent(s, back, front, z0, trim):
    """A clean A-frame tent, 7 wide (z0..z0+6) and 4 deep along x, open at the front (x = front) towards the fire.
    One canvas colour (green wool) on both slopes, a trim row (carpet) only along the bottom edge, a spruce log ridge
    pole that sticks out one block at the front and the back, a fence post holding the front end of the pole and a
    fence pole at each front corner. The back is closed. Inside: two bedrolls (brown carpet, white pillow end at the
    back)."""
    zm = z0 + 3
    step = 1 if front > back else -1
    xs = range(min(back, front), max(back, front) + 1)
    for x in xs:
        for dz in range(0, 3):                                      # the slopes, y 0..2 each side
            s.put(x, dz, z0 + dz, 'green_wool'); s.put(x, dz, z0 + 6 - dz, 'green_wool')
        s.put(x, 1, z0, trim); s.put(x, 1, z0 + 6, trim)            # trim along the bottom edge
    for x in range(back - step, front + 2 * step, step):            # ridge pole, one out at each end
        s.put(x, 3, zm, 'spruce_log', axis='x')
    for y in (0, 1, 2):                                             # the front post under the pole
        s.put(front + step, y, zm, 'spruce_fence')
    s.put(back - step, 2, zm, 'spruce_fence')                       # a short post under the pole at the back
    for z in (z0, z0 + 6):                                          # corner poles
        s.put(front + step, 0, z, 'spruce_fence')
    for dz in range(1, 6):                                          # the closed back
        for y in range(0, 3 - abs(dz - 3)):
            s.put(back, y, z0 + dz, 'green_wool')
    for z in (zm - 1, zm + 1):                                      # bedrolls
        for x in range(back + step, front, step):
            s.put(x, 0, z, 'white_carpet' if x == back + step else 'brown_carpet')


def camp(width=21, depth=14):
    """Traveller's camp by the road (front z 0), grouped round a fire in the middle (x 10, z 7). A stepping-stone path
    comes in between two lantern posts to the fire ring (cobblestone slabs, mossy stones at the sides) on a trodden
    earth patch, with log seats on three sides. Two clean green A-frame tents face the fire from left and right. Along
    the back: a canopy tent with a bed and a chest, a woodpile with a chopping block, and the supplies (barrels, chest,
    composter, workbench). Along the front: a cart with its load, a camp table with stools, a hitching rail with hay.
    Level 1 is a lived-in camp from KNOWN-OK blocks. Level 2: a cooking spit and pot over the fire, red pennants on the
    tents' ridge poles, welcome rugs at the tent mouths, lanterns beside the tents, side boards, more load and a
    canvas cover on the cart (a covered wagon), a water trough by the rail, kindling. Level 3: a red and white striped
    market awning over the supplies, a welcome sign, cushions on the seats, flowers and a potted plant."""
    s = Structure('visitors_camp', (width, 5, depth))
    cx, cz = width // 2, 7                                          # the fire (10, 7)
    s.at(1, 'shell')
    a_tent(s, 1, 4, cz - 3, 'white_carpet')                         # left tent, open to the east (the fire)
    a_tent(s, width - 2, width - 5, cz - 3, 'brown_carpet')         # right tent, open to the west
    for x in (1, 3):                                                # canopy tent, back left (x 1..3, z 11..13)
        for z in (11, 13):
            s.put(x, 0, z, 'spruce_fence'); s.put(x, 1, z, 'spruce_fence')
    for x in range(0, 5):
        for z in range(11, 14):
            if 1 <= x <= 3:
                s.put(x, 2, z, 'spruce_slab', **SLAB)
            else:
                s.put(x, 2, z, 'spruce_slab', type='top', waterlogged='false')   # eaves over the sides
        if 1 <= x <= 3:
            for z in range(11, 14):
                s.put(x, 3, z, 'white_carpet' if x == 2 else 'brown_carpet')     # canvas, one stripe down the middle
    # the fire: an unlit campfire in a ring of cobblestone slabs, mossy stones at the sides
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            if (x, z) == (cx, cz):
                continue
            s.put(x, 0, z, 'mossy_cobblestone' if z == cz else 'cobblestone_slab', **({} if z == cz else SLAB))
    for z in range(cz - 1, cz + 2):                                 # log seats on three sides
        s.put(cx - 3, 0, z, 'stripped_oak_log', axis='z'); s.put(cx + 3, 0, z, 'stripped_oak_log', axis='z')
    for x in range(cx - 1, cx + 2):
        s.put(x, 0, cz + 3, 'stripped_oak_log', axis='x')
    for z in range(0, cz - 1):                                      # a joined stepping-stone path in from the road
        s.put(cx, 0, z, 'cobblestone_slab', **SLAB)
    # woodpile along the back, behind the fire
    for x in range(cx - 2, cx + 3):
        s.put(x, 0, depth - 1, 'oak_log', axis='x')
    for x in range(cx - 1, cx + 2):
        s.put(x, 1, depth - 1, 'oak_log', axis='x')
    s.put(cx, 2, depth - 1, 'oak_log', axis='x')

    s.at(1, 'fittings')
    s.put(cx, 0, cz, 'campfire', facing='north', lit='false', signal_fire='false', waterlogged='false')   # never lit
    for x in range(cx - 2, cx + 3):                                 # a trodden earth patch round the ring
        for z in range(cz - 2, cz + 3):
            if (x in (cx - 2, cx + 2) or z in (cz - 2, cz + 2)) and (x, 0, z) not in s.blocks:
                s.put(x, 0, z, 'brown_carpet')
    s.bed(2, 0, 12, 'south')                                        # under the canopy (foot z 12, head z 13)
    s.put(3, 0, 12, 'chest', facing='west', type='single', waterlogged='false')
    for x in (cx - 2, cx + 2):                                      # lantern posts at the entrance
        s.put(x, 0, 0, 'spruce_fence'); s.put(x, 1, 0, 'spruce_fence'); s.put(x, 2, 0, 'lantern', **LANTERN)
    # cart, front left: a plank bed (top slabs) on wheels (open trapdoors), shafts towards the path, its load
    for x in (2, 3, 4):
        for z in (1, 2):
            s.put(x, 0, z, 'spruce_slab', type='top', waterlogged='false')
    for x in (2, 4):
        s.put(x, 0, 0, 'spruce_trapdoor', facing='north', open='true', **TRAP)    # wheels
        s.put(x, 0, 3, 'spruce_trapdoor', facing='south', open='true', **TRAP)
    s.put(5, 0, 1, 'oak_fence'); s.put(5, 0, 2, 'oak_fence')        # shafts
    s.put(2, 1, 1, 'barrel', facing='up', open='false'); s.put(3, 1, 2, 'hay_block', axis='y')
    # camp table (fence and pressure plate) with two stools, front right
    s.put(14, 0, 2, 'spruce_fence'); s.put(14, 1, 2, 'oak_pressure_plate')
    s.put(13, 0, 2, 'spruce_stairs', facing='west', **STAIR)
    s.put(15, 0, 2, 'spruce_stairs', facing='east', **STAIR)
    # hitching rail, front right corner: posts and a rail, hay for the mounts
    for z in (0, 1, 2):
        s.put(width - 1, 0, z, 'oak_fence')
    s.put(width - 2, 0, 0, 'hay_block', axis='y'); s.put(width - 3, 0, 0, 'hay_block', axis='x')
    # supplies, back right: barrels, a chest, a composter and the workbench
    s.put(width - 1, 0, 13, 'barrel', facing='up', open='false'); s.put(width - 2, 0, 13, 'barrel', facing='up', open='false')
    s.put(width - 1, 1, 13, 'barrel', facing='up', open='false')
    s.put(width - 1, 0, 12, 'composter'); s.put(width - 3, 0, 13, 'barrel', facing='north', open='false')
    s.put(width - 2, 0, 11, 'chest', facing='north', type='single', waterlogged='false')
    s.put(width - 4, 0, 12, 'crafting_table')
    s.put(cx - 3, 0, 12, 'stripped_oak_log', axis='y')              # chopping block by the woodpile
    for x, z in ((cx - 1, 1), (6, 4), (14, 4), (7, 11), (13, 11), (0, 2), (width - 1, 4)):
        s.put(x, 0, z, 'poppy')

    s.at(2, 'flourish2')
    for x in (cx - 1, cx + 1):                                      # cooking spit and pot over the fire
        s.put(x, 1, cz, 'spruce_fence'); s.put(x, 2, cz, 'spruce_fence')
    s.put(cx, 2, cz, 'chain', axis='x', waterlogged='false')
    s.put(cx, 1, cz, 'chain', axis='y', waterlogged='false')
    s.put(cx + 2, 0, cz - 3, 'cauldron')
    for x in (5, width - 6):                                        # pennants on the ridge poles' front ends
        put_id(s, x, 4, cz, 'red_banner', rotation='12' if x == 5 else '4')
    for x in (5, width - 6):                                        # welcome rugs at the tent mouths
        for z in (cz - 1, cz + 1):
            s.put(x, 0, z, 'red_carpet')
    for x in (0, width - 1):                                        # lanterns on posts beside the tents
        s.put(x, 0, cz - 4, 'spruce_fence'); s.put(x, 1, cz - 4, 'lantern', **LANTERN)
    s.put(3, 1, 1, 'barrel', facing='north', open='false')          # more load on the cart
    for x in (2, 3, 4):                                             # side boards on the cart
        for z, f in ((1, 'north'), (2, 'south')):
            if (x, 1, z) not in s.blocks:
                s.put(x, 1, z, 'spruce_trapdoor', facing=f, open='true', **TRAP)
    for x in (2, 3, 4):                                             # a canvas cover: the cart becomes a wagon
        for z in (1, 2):
            put_id(s, x, 2, z, 'white_wool')
    s.put(width - 2, 0, 2, 'cauldron')                              # water trough by the rail
    s.put(cx - 3, 1, 12, 'oak_pressure_plate')                      # the chopping block's top
    for x in (cx - 2, cx + 2):                                      # kindling by the woodpile
        s.put(x, 1, depth - 1, 'spruce_slab', **SLAB)

    s.at(3, 'flourish3')
    for x, z in ((width - 5, 11), (width - 1, 11), (width - 5, 13)):   # a striped market awning over the supplies
        s.put(x, 0, z, 'spruce_fence'); s.put(x, 1, z, 'spruce_fence')
    for x in range(width - 5, width):
        for z in range(11, 14):
            put_id(s, x, 2, z, 'red_wool' if (x - width) % 2 else 'white_wool')
    put_id(s, cx + 1, 0, 0, 'oak_sign', rotation='8', waterlogged='false')
    s.decorate(cx + 1, 0, 0, 'sign_text', lines=['Welcome', 'travellers', 'rest here', ''])
    for x in (cx - 3, cx + 3):                                      # cushions on the seats
        for z in (cz - 1, cz + 1):
            s.put(x, 1, z, 'red_carpet')
    s.put(cx, 1, cz + 3, 'red_carpet')
    for x, z, plant in ((cx + 3, 1, 'dandelion'), (6, 6, 'cornflower'), (14, 6, 'oxeye_daisy'), (0, 1, 'short_grass'),
                        (7, 13, 'fern'), (13, 13, 'short_grass'), (cx - 4, 3, 'cornflower'), (cx + 4, 3, 'dandelion'),
                        (6, 10, 'azure_bluet'), (14, 10, 'oxeye_daisy'), (width - 4, 1, 'short_grass')):
        s.add(x, 0, z, plant)
    s.put(width - 2, 1, 13, 'flower_pot')                           # a potted plant on the supply barrels
    s.decorate(width - 2, 1, 13, 'potted_plant', plant='poppy')
    return connect(s)


STRUCTURES = [dict(path='visitors_camp', name="Visitors' camp", tier=1, zone='inner', role='house', group='inn',
                   terrain='flexible', door='front', make=camp,
                   description="A traveller's camp (21 x 14) grouped round an unlit fire in a stone ring on a trodden "
                               'earth patch with log seats: two clean green A-frame tents (ridge pole, corner poles, '
                               'trim along the bottom edge, bedrolls inside) face the fire from either side; a canopy '
                               'bed, a woodpile and the supplies along the back; a cart, a camp table and a hitching '
                               'rail along the front. Level 2 a covered wagon, a cooking spit and pot, pennants, rugs '
                               'and lanterns; '
                               'level 3 a red and white market awning over the supplies, a welcome sign, cushions and '
                               'flowers.',
                   changes='Six blocks wider and grouped round the fire: two clean A-frame tents (one canvas colour, '
                           'trim only on the bottom edge, ridge and corner poles) face it from either side, the stall '
                           'and woodpile along the back, more detail (trodden earth patch, table, trough, rugs, '
                           'kindling).')]

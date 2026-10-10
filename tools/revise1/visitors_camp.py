"""Visitors' camp (tier 1, stage 1 of the inn), revised from Carlton's notes: l1 "cant tell, not much in terms of detail,
need more"; l2 and l3 rejected with no note. Rebuilt as a readable traveller's camp."""
from make_batch1_structures import Structure
from revise1.blockconn import connect, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
SLAB = dict(type='bottom', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')
TRAP = dict(half='bottom', powered='false', waterlogged='false')


def a_tent(s, x0, z0, stripe, depth=4):
    """An A-frame tent of green wool, 5 wide, depth deep, with an OPEN front: two spruce poles hold the ridge up at
    the front, so you look in at the bedrolls. The back is closed. Every other row carries a stripe of carpet on its
    slopes and ridge (green and white or green and brown canvas). Inside: a bedroll down the middle (brown carpet,
    white pillow end at the back) and one under each slope."""
    zb = z0 + depth - 1
    for z in range(z0, z0 + depth):
        s.put(x0, 0, z, 'green_wool'); s.put(x0 + 4, 0, z, 'green_wool')
        if z > z0:
            s.put(x0 + 1, 1, z, 'green_wool'); s.put(x0 + 3, 1, z, 'green_wool')
        s.put(x0 + 2, 2, z, 'green_wool')
        if (z - z0) % 2 == 1:                                       # canvas stripes
            for x, y in ((x0, 1), (x0 + 4, 1), (x0 + 1, 2), (x0 + 3, 2), (x0 + 2, 3)):
                s.put(x, y, z, stripe)
    for x in (x0 + 1, x0 + 3):                                      # front poles under the ridge's ends
        s.put(x, 0, z0, 'spruce_fence'); s.put(x, 1, z0, 'spruce_fence')
    for x in (x0 + 1, x0 + 2, x0 + 3):                              # back wall
        s.put(x, 0, zb, 'green_wool')
    s.put(x0 + 2, 1, zb, 'green_wool')
    for x in (x0 + 1, x0 + 2, x0 + 3):                              # bedrolls
        for z in range(z0 + 1, zb - 1):
            s.put(x, 0, z, 'brown_carpet')
        s.put(x, 0, zb - 1, 'white_carpet')


def camp(width=15, depth=13):
    """Traveller's camp by the road (front z 0). A path of stepping stones comes in between two lantern posts to a fire
    in a log seating ring. Round it: two striped green A-frame tents with open fronts at the back, a canopy tent
    (brown and white canvas on spruce poles) with a bed on the left, a cart, crates and barrels with a workbench, a
    camp table with stools, a hitching rail with hay, a woodpile and poppies.
    Level 1 is a lived-in camp from KNOWN-OK blocks. Level 2: a cooking spit and pot over the fire, red pennants on the
    tents, lanterns by the tents, side boards and more load on the cart. Level 3: a red and white striped market
    awning over the crates, a welcome sign, cushions on the seats, flowers and a potted plant."""
    s = Structure('visitors_camp', (width, 4, depth))
    cx, cz = width // 2, 6                                          # the fire (7, 6)
    s.at(1, 'shell')
    a_tent(s, 0, 8, 'white_carpet')                                 # back left: green and white
    a_tent(s, width - 5, 8, 'brown_carpet')                         # back right: green and brown
    for x in (0, 2):                                                # canopy tent, left middle (x 0..2, z 3..5)
        for z in (3, 5):
            s.put(x, 0, z, 'spruce_fence'); s.put(x, 1, z, 'spruce_fence')
    for x in range(0, 3):
        for z in range(3, 6):
            s.put(x, 2, z, 'spruce_slab', **SLAB)
            s.put(x, 3, z, 'white_carpet' if x == 1 else 'brown_carpet')   # canvas, striped along the roof
    # the fire: an unlit campfire in a ring of cobblestone slabs, mossy stones at the sides; log seats round it
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            if (x, z) == (cx, cz):
                continue
            if z == cz:
                s.put(x, 0, z, 'mossy_cobblestone')
            else:
                s.put(x, 0, z, 'cobblestone_slab', **SLAB)
    for z in range(cz - 1, cz + 2):
        s.put(cx - 3, 0, z, 'stripped_oak_log', axis='z'); s.put(cx + 3, 0, z, 'stripped_oak_log', axis='z')
    for x in range(cx - 1, cx + 2):
        s.put(x, 0, cz + 3, 'stripped_oak_log', axis='x')
    for z in range(0, cz - 1, 2):                                   # stepping stones in from the road
        s.put(cx, 0, z, 'cobblestone_slab', **SLAB)

    s.at(1, 'fittings')
    s.put(cx, 0, cz, 'campfire', facing='north', lit='false', signal_fire='false', waterlogged='false')   # never lit
    s.bed(1, 0, 4, 'south')                                         # under the canopy (foot z 4, head z 5)
    s.put(1, 0, 3, 'chest', facing='north', type='single', waterlogged='false')
    for x in (cx - 2, cx + 2):                                      # lantern posts at the entrance
        s.put(x, 0, 0, 'spruce_fence'); s.put(x, 1, 0, 'spruce_fence'); s.put(x, 2, 0, 'lantern', **LANTERN)
    # cart, left front: a plank bed (top slabs, so it stands off the ground) on wheels (open trapdoors), shafts
    # towards the path, a barrel and a hay bale as the load
    for x in (2, 3, 4):
        for z in (1, 2):
            s.put(x, 0, z, 'spruce_slab', type='top', waterlogged='false')
    for x in (2, 4):
        s.put(x, 0, 0, 'spruce_trapdoor', facing='north', open='true', **TRAP)    # wheels
        s.put(x, 0, 3, 'spruce_trapdoor', facing='south', open='true', **TRAP)
    s.put(5, 0, 1, 'oak_fence'); s.put(5, 0, 2, 'oak_fence')        # shafts
    s.put(2, 1, 1, 'barrel', facing='up', open='false'); s.put(3, 1, 2, 'hay_block', axis='y')
    # crates and barrels, right front, with a workbench
    s.put(width - 2, 0, 1, 'barrel', facing='up', open='false'); s.put(width - 1, 0, 1, 'barrel', facing='up', open='false')
    s.put(width - 1, 1, 1, 'barrel', facing='up', open='false')
    s.put(width - 1, 0, 2, 'composter'); s.put(width - 2, 0, 2, 'barrel', facing='north', open='false')
    s.put(width - 1, 0, 3, 'chest', facing='west', type='single', waterlogged='false')
    s.put(width - 3, 0, 2, 'crafting_table')
    # camp table (fence and pressure plate) with two stools
    s.put(width - 3, 0, 5, 'spruce_fence'); s.put(width - 3, 1, 5, 'oak_pressure_plate')
    s.put(width - 3, 0, 4, 'spruce_stairs', facing='north', **STAIR)
    s.put(width - 3, 0, 6, 'spruce_stairs', facing='south', **STAIR)
    # hitching rail, right: posts and a rail, hay for the mounts
    for z in (5, 6):
        s.put(width - 1, 0, z, 'oak_fence')
    s.put(width - 1, 0, 4, 'hay_block', axis='y')
    # woodpile between the tents, poppies
    for x in range(cx - 1, cx + 2):
        s.put(x, 0, depth - 1, 'oak_log', axis='x')
    s.put(cx, 1, depth - 1, 'oak_log', axis='x')
    for x, z in ((cx - 1, 0), (5, 3), (9, 3), (5, 11), (9, 11)):
        s.put(x, 0, z, 'poppy')

    s.at(2, 'flourish2')
    for x in (cx - 1, cx + 1):                                      # cooking spit and pot over the fire
        s.put(x, 1, cz, 'spruce_fence'); s.put(x, 2, cz, 'spruce_fence')
    s.put(cx, 2, cz, 'chain', axis='x', waterlogged='false')
    s.put(cx, 1, cz, 'chain', axis='y', waterlogged='false')
    s.put(cx + 2, 0, cz - 2, 'cauldron')
    for x in (2, width - 3):                                        # pennants on the A-tents' front ridges
        put_id(s, x, 3, 8, 'red_banner', rotation='8')
    for x in (0, width - 1):                                        # lanterns on posts beside the A-tents
        s.put(x, 0, 7, 'spruce_fence'); s.put(x, 1, 7, 'lantern', **LANTERN)
    s.put(width - 2, 1, 1, 'composter')                             # crates stacked
    s.put(width - 2, 0, 3, 'barrel', facing='up', open='false')
    s.put(3, 1, 1, 'barrel', facing='north', open='false')          # more load on the cart
    for x in (2, 3, 4):                                             # side boards on the cart
        for z, f in ((1, 'north'), (2, 'south')):
            if (x, 1, z) not in s.blocks:
                s.put(x, 1, z, 'spruce_trapdoor', facing=f, open='true', **TRAP)

    s.at(3, 'flourish3')
    for z in (0, 3):                                                # a striped market awning over the crates
        s.put(width - 3, 0, z, 'spruce_fence'); s.put(width - 3, 1, z, 'spruce_fence')
    for x in range(width - 3, width):
        for z in range(0, 4):
            put_id(s, x, 2, z, 'red_wool' if x != width - 2 else 'white_wool')
    put_id(s, cx + 1, 0, 0, 'oak_sign', rotation='8', waterlogged='false')
    s.decorate(cx + 1, 0, 0, 'sign_text', lines=['Welcome', 'travellers', 'rest here', ''])
    for x in (cx - 3, cx + 3):                                      # cushions on the seats
        for z in (cz - 1, cz + 1):
            s.put(x, 1, z, 'red_carpet')
    s.put(cx, 1, cz + 3, 'red_carpet')
    for x, z, plant in ((cx + 3, 1, 'dandelion'), (6, 3, 'cornflower'), (8, 3, 'oxeye_daisy'), (width - 2, 7, 'short_grass'),
                        (5, 12, 'fern'), (9, 12, 'short_grass'), (cx - 3, 4, 'cornflower'), (cx + 3, 4, 'dandelion'),
                        (0, 2, 'short_grass'), (5, 6, 'oxeye_daisy'), (9, 6, 'azure_bluet')):
        s.add(x, 0, z, plant)
    s.put(cx + 1, 0, cz + 2, 'flower_pot')
    s.decorate(cx + 1, 0, cz + 2, 'potted_plant', plant='poppy')
    return connect(s)


STRUCTURES = [dict(path='visitors_camp', name="Visitors' camp", tier=1, zone='inner', role='house', group='inn',
                   terrain='flexible', door='front', make=camp,
                   description="A traveller's camp: two striped green A-frame tents with open fronts and bedrolls, a "
                               'canopy tent with a bed, an unlit fire in a stone ring with log seats, a cart, crates, '
                               'a workbench, a camp table, a hitching rail, a woodpile and lantern posts; level 2 a '
                               'cooking spit and pot, pennants and more lanterns; level 3 a red and white market awning, '
                               'a welcome sign, cushions and flowers.',
                   changes='Tents in green canvas with white or brown stripes and open fronts (you see the bedrolls), '
                           'no more bright blue; level 1 has a workbench, a camp table, a woodpile and poppies; level 2 '
                           'a cooking spit over the fire; level 3 a striped market awning over the crates.')]

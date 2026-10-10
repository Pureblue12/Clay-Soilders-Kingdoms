"""Visitors' camp (tier 1, stage 1 of the inn), revised from Carlton's notes: l1 "cant tell, not much in terms of detail,
need more"; l2 and l3 rejected with no note. Rebuilt as a readable traveller's camp."""
from make_batch1_structures import Structure
from revise1.blockconn import connect, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
SLAB = dict(type='bottom', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')
TRAP = dict(half='bottom', powered='false', waterlogged='false')


def a_tent(s, x0, z0, cloth, trim, depth=4):
    """An A-frame tent 5 wide, depth deep, opening at the front (z0): wool slopes from the ground (x0, x0+4) up to a
    ridge (x0+2, y 2), a spruce pole under the ridge at the front, the back closed. Inside: two bedrolls (carpet,
    white pillow end at the back). Returns the ridge positions."""
    for z in range(z0, z0 + depth):
        s.put(x0, 0, z, cloth); s.put(x0 + 4, 0, z, cloth)
        s.put(x0 + 1, 1, z, cloth); s.put(x0 + 3, 1, z, cloth)
        s.put(x0 + 2, 2, z, trim)
    zb = z0 + depth - 1                                             # back wall
    s.put(x0 + 1, 0, zb, cloth); s.put(x0 + 3, 0, zb, cloth); s.put(x0 + 2, 0, zb, cloth); s.put(x0 + 2, 1, zb, cloth)
    s.put(x0 + 2, 0, z0, 'spruce_fence'); s.put(x0 + 2, 1, z0, 'spruce_fence')   # front pole
    for x in (x0 + 1, x0 + 3):                                      # bedrolls along the tent
        for z in range(z0 + 1, zb - 1):
            s.put(x, 0, z, 'brown_carpet')
        s.put(x, 0, zb - 1, 'white_carpet')


def soften(s, x0, z0, carpet, depth=4):
    """Level 2: carpet on every step of an A-tent's slopes, so the wool reads as cloth, not stairs of blocks."""
    for z in range(z0, z0 + depth):
        for x, y in ((x0, 1), (x0 + 4, 1), (x0 + 1, 2), (x0 + 3, 2)):
            s.put(x, y, z, carpet)


def camp(width=15, depth=13):
    """Traveller's camp by the road (front z 0). A path comes in between two lantern posts to a fire in a log seating
    ring; tents stand round it: two A-frame tents at the back (blue, green) and a cyan canopy tent on four fence posts
    with a bed (left). A cart (left front), crates and barrels (right front), a hitching rail with hay (right).
    Level 1 is everything a camp needs, from KNOWN-OK blocks. Level 2: banners on the tent poles, lanterns at the
    tents, a woodpile, a cooking pot, more crates, side boards on the cart and carpet on the tent slopes. Level 3: a welcome sign, flowers, grass,
    cushions on the seats and a potted flower."""
    s = Structure('visitors_camp', (width, 4, depth))
    cx, cz = width // 2, 6                                          # the fire (7, 6)
    s.at(1, 'shell')
    # tents
    a_tent(s, 0, 8, 'blue_wool', 'light_blue_wool')                 # back left
    a_tent(s, width - 5, 8, 'green_wool', 'green_wool')              # back right
    for x in (0, 2):                                                # canopy tent, left middle (x 0..2, z 3..5)
        for z in (3, 5):
            s.put(x, 0, z, 'spruce_fence'); s.put(x, 1, z, 'spruce_fence')
    for x in range(0, 3):
        for z in range(3, 6):
            s.put(x, 2, z, 'cyan_wool')
    for z in range(3, 6):
        s.put(1, 3, z, 'light_blue_wool')
    # the fire: a ring of cobblestone slabs round an unlit campfire, log seats round that
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            if (x, z) != (cx, cz):
                s.put(x, 0, z, 'mossy_cobblestone' if (x + z) % 3 == 0 else 'cobblestone_slab', **({} if (x + z) % 3 == 0 else SLAB))
    for z in range(cz - 1, cz + 2):
        s.put(cx - 3, 0, z, 'stripped_oak_log', axis='z'); s.put(cx + 3, 0, z, 'stripped_oak_log', axis='z')
    for x in range(cx - 1, cx + 2):
        s.put(x, 0, cz + 3, 'stripped_oak_log', axis='x')
    # the path in from the road
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
    # crates and barrels, right front
    s.put(width - 2, 0, 1, 'barrel', facing='up', open='false'); s.put(width - 1, 0, 1, 'barrel', facing='up', open='false')
    s.put(width - 1, 1, 1, 'barrel', facing='up', open='false')
    s.put(width - 1, 0, 2, 'composter'); s.put(width - 2, 0, 2, 'barrel', facing='north', open='false')
    s.put(width - 1, 0, 3, 'chest', facing='west', type='single', waterlogged='false')
    # hitching rail, right middle: posts and a rail, hay for the mounts
    for z in (5, 6):
        s.put(width - 1, 0, z, 'oak_fence')
    s.put(width - 1, 0, 4, 'hay_block', axis='y')

    s.at(2, 'flourish2')
    for x in (2, width - 3):                                        # pennants over the A-tent poles
        put_id(s, x, 3, 8, 'red_banner', rotation='8')
    for x in (0, width - 1):                                        # lanterns on posts beside the A-tents
        s.put(x, 0, 7, 'spruce_fence'); s.put(x, 1, 7, 'lantern', **LANTERN)
    s.put(cx + 2, 0, cz - 2, 'cauldron')                            # cooking pot by the fire
    for x in range(cx - 1, cx + 2):                                 # woodpile behind the back seat
        s.put(x, 0, depth - 1, 'oak_log', axis='x')
    s.put(cx, 1, depth - 1, 'oak_log', axis='x')
    s.put(width - 2, 1, 1, 'composter')                             # crates stacked
    s.put(width - 2, 0, 3, 'barrel', facing='up', open='false')
    for x in (2, 3, 4):                                             # side boards on the cart
        if (x, 1, 1) not in s.blocks:
            s.put(x, 1, 1, 'spruce_trapdoor', facing='north', open='true', **TRAP)
        if (x, 1, 2) not in s.blocks:
            s.put(x, 1, 2, 'spruce_trapdoor', facing='south', open='true', **TRAP)
    soften(s, 0, 8, 'blue_carpet'); soften(s, width - 5, 8, 'green_carpet')
    for z in range(3, 6):                                           # and on the canopy's roof
        s.put(0, 3, z, 'cyan_carpet'); s.put(2, 3, z, 'cyan_carpet')

    s.at(3, 'flourish3')
    put_id(s, cx + 1, 0, 0, 'oak_sign', rotation='8', waterlogged='false')
    s.decorate(cx + 1, 0, 0, 'sign_text', lines=['Welcome', 'travellers', 'rest here', ''])
    for x in (cx - 3, cx + 3):                                      # cushions on the side seats
        s.put(x, 1, cz, 'red_carpet')
    s.put(cx, 1, cz + 3, 'red_carpet')
    for x, z, plant in ((cx - 1, 0, 'poppy'), (cx + 3, 1, 'dandelion'), (5, 3, 'short_grass'), (cx + 3, 3, 'cornflower'),
                        (width - 2, 7, 'short_grass'), (5, 12, 'fern'), (9, 12, 'short_grass'), (cx - 3, 4, 'short_grass')):
        s.add(x, 0, z, plant)
    s.put(cx + 1, 0, cz + 2, 'flower_pot')
    s.decorate(cx + 1, 0, cz + 2, 'potted_plant', plant='poppy')
    return connect(s)


STRUCTURES = [dict(path='visitors_camp', name="Visitors' camp", tier=1, zone='inner', role='house', group='inn',
                   terrain='flexible', door='front', make=camp,
                   description="A traveller's camp: two A-frame wool tents and a canopy tent with bedrolls and a bed, an "
                               'unlit fire in a stone ring with log seats, a cart, crates and barrels, a hitching rail '
                               'and lantern posts; level 2 banners, more lanterns, a woodpile and a pot; level 3 a '
                               'welcome sign, cushions and flowers.',
                   changes="Rebuilt as a readable traveller's camp: three different tents (two A-frames on spruce poles, "
                           'a canopy on fence posts) with bedrolls, a cookfire in a stone ring with log seats, a cart, '
                           'crates and barrels, a hitching rail and lanterns on posts.')]

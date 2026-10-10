"""Inn (tier 4, look A): the inn stage of the visitors' camp. A two-storey timber and stone inn: common room with a
hearth and a bar below, six small beds in the upper storey under the roof, a stable lean-to and a projecting sign."""
from make_batch1_structures import Structure
from batch3.ws_kit import (P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, LEAVES, connect, noise,
                           gable_roof, shutters)


def inn(width=7, depth=9):
    """Inn in a 12 x 9 x 13 box (9 tall: within builders' reach, no scaffolding).

    Walls x 2..8, z 2..10 (7 x 9). Ground storey 3 high (cobblestone with oak log corners), a dark oak floor beam at
    y 4, then the upper storey under a 45-degree spruce roof: plates at y 5, ridge at y 8, one block of overhang all
    round. The gable ends make the upper storey a full timber-framed floor front and back (plaster in dark oak).
    Soldier-sized rooms: 3 high below, 2 to 3 high above.

    L1 (KNOWN-OK only): common room with a brick hearth in the left wall (chimney outside, up past the eaves), two
    tables with chairs, a bar (slab counter, kegs behind it), a staircase along the back wall with barrels and a
    chest under it; upstairs six beds in two rows with chests and barrels between, a lantern under the ridge; front
    door with a step, windows in every wall, torches.
    L2 (open for guests): the stable lean-to on the right (posts, a spruce stair roof, hay, a trough, a hitching
    rail), the projecting sign on a bracket under the right eave, lanterns under the front gable and over the door,
    shutters, lit lanterns upstairs, a rug and a bench.
    L3 (welcoming): a porch roof on posts over the front door with lanterns, flower boxes, a red banner, chimney smoke,
    a "Welcome" signpost by the road (sign text: decoration step), barrels and a crate of luggage, flowers and
    bushes."""
    W, D, H = width + 5, depth + 6, 9
    s = Structure('inn', (W, H, D), palette='oak_a2')
    x0, x1, z0, z1 = 2, 1 + width, 4, 3 + depth           # 2..8, 4..12 (z 0..2: porch and path)
    mid = (x0 + x1) // 2                                   # 5
    top = 4                                                # last layer under the roof plates (the upper floor)
    hz = z0 + 4                                            # 6: hearth row
    chim = x0 - 1                                          # 1

    # ------------------------------------------------------------------ L1 shell
    s.at(1, 'shell')
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                P(s, x, 0, z, 'stone_bricks')
            else:
                P(s, x, 0, z, 'oak_planks' if (x + z) % 3 else 'spruce_planks')
    for y in (1, 2, 3):                                    # ground storey: cobblestone, oak log corners
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                if x in (x0, x1) and z in (z0, z1):
                    P(s, x, y, z, 'oak_log', axis='y')
                else:
                    P(s, x, y, z, 'mossy_cobblestone' if noise(x, y, z, 21) < 0.22 else 'cobblestone')
    s.remove(mid, 1, z0); s.remove(mid, 2, z0)
    s.door(mid, 1, z0, 'north', block='spruce_door', hinge='left')
    gwin = [(x0 + 1, 2, z0), (x1 - 1, 2, z0), (x0, 2, z0 + 1), (x0, 2, z1 - 1), (x1, 2, z0 + 2), (x1, 2, z1 - 2),
            (mid, 2, z1)]
    for w in gwin:
        s.remove(*w)
    # hearth in the left wall: brick surround, fire in the recess, chimney breast outside, stack past the eaves
    for z in (hz - 1, hz + 1):
        for y in (1, 2, 3):
            P(s, x0, y, z, 'bricks')
    s.remove(x0, 1, hz)
    P(s, x0, 2, hz, 'bricks'); P(s, x0, 3, hz, 'bricks')
    for z in (hz - 1, hz, hz + 1):
        for y in (0, 1, 2, 3):
            P(s, chim, y, z, 'bricks')
    for y in range(4, 8):
        P(s, chim, y, hz, 'bricks')
    # upper floor: a dark oak beam all round, planks inside, the stairwell open
    stairs = [(x1 - 1, 1), (x1 - 2, 2), (x1 - 3, 3), (x1 - 4, 4)]   # (x, y) along the back wall, rising west
    hole = {(x1 - 2, z1 - 1), (x1 - 3, z1 - 1)}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1):
                P(s, x, top, z, 'dark_oak_log', axis='z')
            elif z in (z0, z1):
                P(s, x, top, z, 'dark_oak_log', axis='x')
            elif (x, z) not in hole and (x, z) != (x1 - 4, z1 - 1):
                P(s, x, top, z, 'spruce_planks')
    # timber-framed upper gables: a king post, plaster, two windows front and back
    uwin = []
    for z in (z0, z1):
        for y in (5, 6, 7):
            P(s, mid, y, z, 'dark_oak_log', axis='y')
        for x in (mid - 2, mid + 2):
            P(s, x, 5, z, 'glass_pane'); uwin.append((x, 5, z))
    gable_roof(s, x0, x1, z0, z1, top, front=1, back=1, cap=None, gable='white_terracotta',
               skip={(chim, top + 1, hz)})
    P(s, mid, 0, z0 - 1, 'cobblestone_stairs', facing='south', **STAIR)   # step at the door
    for z in range(0, z0 - 1):                                          # a stepping-stone path to the road
        P(s, mid, 0, z, 'cobblestone_slab', **SLAB)
    for x in (x0 + 1, x1 - 1):                                          # planter boxes under the front windows
        P(s, x, 0, z0 - 1, 'coarse_dirt')
        P(s, x, 0, z0 - 2, 'spruce_trapdoor', facing='north', **OPEN)
    # sills (upside-down stairs) under the side and back windows
    P(s, x0 - 1, 1, z0 + 1, 'spruce_stairs', facing='east', **TOP)
    P(s, x0 - 1, 1, z1 - 1, 'spruce_stairs', facing='east', **TOP)
    P(s, x1 + 1, 1, z0 + 2, 'spruce_stairs', facing='west', **TOP)
    P(s, mid, 1, z1 + 1, 'spruce_stairs', facing='north', **TOP)
    # kegs and a bench by the door
    P(s, x0 - 1, 0, z0, 'barrel', facing='up', open='false')
    P(s, x0 - 1, 0, z0 - 1, 'barrel', facing='east', open='false')
    P(s, x1, 0, z0 - 1, 'spruce_stairs', facing='north', **STAIR)

    # ------------------------------------------------------------------ L1 fittings
    s.at(1, 'fittings')
    for x, y, z in gwin:
        P(s, x, y, z, 'glass_pane')
    P(s, x0, 1, hz, 'campfire', facing='east', lit='true', signal_fire='false', waterlogged='false')
    for x, y in stairs:
        P(s, x, y, z1 - 1, 'spruce_stairs', facing='west', **STAIR)
    P(s, x1 - 2, 1, z1 - 1, 'barrel', facing='up', open='false')            # under the stairs
    P(s, x1 - 3, 1, z1 - 1, 'barrel', facing='up', open='false')
    P(s, x1 - 3, 2, z1 - 1, 'barrel', facing='up', open='false')
    P(s, x1 - 4, 1, z1 - 1, 'chest', facing='east', type='single', waterlogged='false')
    # two tables with chairs on the hearth side
    for tz in (z0 + 2, z0 + 6):
        P(s, x0 + 2, 1, tz, 'oak_slab', **SLAB_TOP)
        P(s, x0 + 1, 1, tz, 'oak_stairs', facing='west', **STAIR)
        P(s, x0 + 2, 1, tz - 1, 'oak_stairs', facing='north', **STAIR)
    # the bar: a counter of slabs, kegs behind it against the right wall
    for z in (z0 + 2, z0 + 3, z0 + 4):
        P(s, x1 - 2, 1, z, 'spruce_slab', **SLAB_TOP)
    for z in (z0 + 2, z0 + 3):
        P(s, x1 - 1, 1, z, 'barrel', facing='west', open='false')
        P(s, x1 - 1, 2, z, 'barrel', facing='west', open='false')
    for x, z, f in ((x0 + 1, z0 + 4, 'east'), (x1 - 1, z0 + 5, 'west'), (mid, z0 + 1, 'south')):
        P(s, x, 3, z, 'wall_torch', facing=f)
    # upstairs: six beds in two rows, chests and barrels between them, a lantern under the ridge
    for z in (z0 + 1, z0 + 3, z0 + 5):
        s.bed(x0 + 2, 5, z, 'west')
        s.bed(x1 - 2, 5, z, 'east')
    P(s, x0 + 1, 5, z0 + 2, 'chest', facing='east', type='single', waterlogged='false')
    P(s, x1 - 1, 5, z0 + 2, 'chest', facing='west', type='single', waterlogged='false')
    P(s, x0 + 1, 5, z0 + 4, 'barrel', facing='up', open='false')
    P(s, x1 - 1, 5, z0 + 4, 'barrel', facing='up', open='false')
    P(s, mid, 7, z0 + 3, 'lantern', **HANG)

    # ------------------------------------------------------------------ L2: open for guests
    s.at(2, 'flourish2')
    # the stable lean-to on the right: stair roof against the wall on spruce posts, hay, trough, hitching rail
    lz0, lz1 = z0 + 3, z1 - 1                                         # 5..9
    for z in range(lz0, lz1 + 1):
        P(s, x1 + 1, 4, z, 'spruce_stairs', facing='west', **STAIR)
        P(s, x1 + 2, 3, z, 'spruce_stairs', facing='west', **STAIR)
    for z in (lz0, lz0 + 2, lz1):
        for y in (0, 1, 2):
            P(s, x1 + 2, y, z, 'spruce_fence')
    for y in (0, 1, 2, 3):                                              # plank end wall at the back
        P(s, x1 + 1, y, lz1, 'spruce_planks')
    P(s, x1 + 1, 0, lz1 - 1, 'hay_block', axis='y'); P(s, x1 + 1, 1, lz1 - 1, 'hay_block', axis='x')
    P(s, x1 + 1, 0, lz0, 'cauldron')                                   # the trough (water: decoration step)
    s.decorate(x1 + 1, 0, lz0, 'water', note='fill the trough')
    P(s, x1 + 1, 3, lz0 + 2, 'lantern', **HANG)                          # under the lean-to roof
    for z in (z0, z0 + 1, z0 + 2, z0 + 3):                             # hitching rail, joined to the stable post
        P(s, x1 + 3, 0, z, 'spruce_fence')
    # projecting sign: fence bracket under the right front eave, the sign hanging below
    P(s, x1 + 1, 4, z0, 'spruce_fence')
    P(s, x1 + 1, 3, z0, 'oak_hanging_sign', rotation='8', attached='true', waterlogged='false')
    s.decorate(x1 + 1, 3, z0, 'sign_text', lines=['', 'The Resting', 'Soldier', 'Inn'])
    # lanterns under the gable corbels and on a chain over the door
    for x in (x0 + 1, x1 - 1):
        P(s, x, top + 1, z0 - 1, 'lantern', **HANG)
    for y in (6, 7):
        P(s, mid, y, z0 - 1, 'chain', axis='y')
    P(s, mid, 5, z0 - 1, 'lantern', **HANG)
    for w in gwin[:2]:
        shutters(s, *w, 'north')
    shutters(s, *gwin[2], 'west'); shutters(s, *gwin[3], 'west'); shutters(s, *gwin[6], 'south')
    P(s, mid, 5, z0 + 1, 'red_carpet'); P(s, mid, 5, z0 + 2, 'red_carpet'); P(s, mid, 5, z0 + 3, 'red_carpet')
    P(s, mid, 1, z0 + 1, 'red_carpet'); P(s, mid, 1, z0 + 2, 'red_carpet')
    P(s, x1 - 1, 6, z0 + 4, 'lantern', **STAND)                          # on the barrels upstairs: lit windows at night
    P(s, x0 + 1, 6, z0 + 4, 'lantern', **STAND)

    # ------------------------------------------------------------------ L3: welcoming
    s.at(3, 'flourish3')
    for x in (mid - 1, mid + 1):                                        # porch over the door
        for y in (0, 1, 2):
            P(s, x, y, z0 - 2, 'spruce_fence')
    for x in (mid - 1, mid, mid + 1):
        for z in (z0 - 2, z0 - 1):
            P(s, x, 3, z, 'spruce_slab', **SLAB)
    P(s, mid, 2, z0 - 2, 'lantern', **HANG)
    for x, f in ((x0 + 1, 'poppy'), (x1 - 1, 'cornflower')):
        P(s, x, 1, z0 - 1, f)                                           # flowers in the planter boxes
    P(s, x0 - 1, 3, z0 + 1, 'red_wall_banner', facing='west')
    s.decorate(x0 - 1, 3, z0 + 1, 'banner_pattern', banner='red_wall_banner', note='a bed and a tankard')
    P(s, chim, 8, hz, 'campfire', facing='north', lit='true', signal_fire='false', waterlogged='false')
    # "Welcome" signpost by the road (front left corner)
    for y in (0, 1):
        P(s, 0, y, 1, 'spruce_fence')
    P(s, 0, 2, 1, 'oak_sign', rotation='8', waterlogged='false')
    s.decorate(0, 2, 1, 'sign_text', lines=['', 'Welcome', 'travellers', ''])
    # luggage by the door: a chest, a barrel of apples
    P(s, x0 - 1, 1, z0, 'barrel', facing='up', open='true')
    s.decorate(x0 - 1, 1, z0, 'item', item='minecraft:apple', note='apples in the open barrel')
    P(s, x0, 0, z0 - 1, 'chest', facing='south', type='single', waterlogged='false')
    for x, f in ((mid - 2, 'oxeye_daisy'), (mid + 2, 'allium')):         # flowers along the path
        P(s, x, 0, 1, f)
    for x, z in ((0, z1), (x1 + 1, z1 + 1), (x0 - 1, z1 + 1)):
        A(s, x, 0, z, 'oak_leaves', **LEAVES)
    for x, z, f in ((0, z0 + 2, 'allium'), (0, z0 + 3, 'oxeye_daisy'), (mid - 1, z1 + 1, 'poppy'),
                    (mid + 1, z1 + 1, 'dandelion'), (x1 + 3, z0 + 4, 'azure_bluet'), (0, z1 - 1, 'fern')):
        A(s, x, 0, z, f)
    return connect(s)


STRUCTURES = [dict(path='inn', name='Inn (inn stage of the visitors\' camp)', tier=4, zone='inner', role='house',
                   group='visitors_camp', terrain='skirt', door='front', make=inn, changes='Quality check round 1: level 1 outside is no longer bare: planter boxes under the front windows, sills under the side and back windows, a stepping-stone path to the road, kegs and a bench by the door. The hitching rail now joins the stable post. Level 3 fills the planter boxes with flowers and adds flowers along the path and a barrel of apples. The box is two rows deeper at the front for the path and porch.',
                   description='Over budget: 521 blocks vs 315 (first build 433 vs 255). Two-storey inn: stone common room (hearth, two tables, a bar of kegs), six beds in the '
                               'timber upper storey up a staircase; L2 the stable lean-to, a projecting sign, '
                               'lanterns and shutters; L3 a porch over the door, a Welcome signpost, flower boxes, '
                               'banner and chimney smoke.')]

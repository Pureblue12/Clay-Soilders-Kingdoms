"""Townhouse (tier 4, look A): a narrow two-storey town house, stone ground floor, plaster and dark oak upper storey
jettied out over the street on dark oak brackets, spruce gable roof, a brick chimney on the left side."""
from make_batch1_structures import Structure
from batch3.ws_kit import (P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, LEAVES, connect, noise,
                           gable_roof, shutters)


def townhouse(width=5, depth=6):
    """Townhouse in a 9 x 9 x 11 box (9 tall: within builders' reach, no scaffolding).

    Walls x 2..6, ground floor z 2..7 (5 x 6). The upper storey sits on a dark oak floor beam at y 4 and juts one
    block out over the front (z 1), carried on upside-down dark oak stair brackets. The upper storey is a half storey
    under the roof: a knee wall at y 5, gables at y 6..7, a spruce roof with dark oak eaves and a dark oak ridge at
    y 8, one block of overhang all round. Rooms are soldier-sized: 3 high on the ground floor, 3 high under the ridge.

    L1 (KNOWN-OK only): cobblestone plinth (mossy mixed in), stone brick ground walls, white plaster upper walls in a
    dark oak frame, oak door between two windows, a hearth (furnace) in the left wall under a brick chimney, table and
    chair, barrels and a chest under the staircase, a 4-step spruce staircase up the right wall; upstairs 3 beds and
    a chest, windows in both gables, torches, a step at the door.
    L2 (lived in): shutters and flower boxes on the ground windows, two lanterns under the jetty, a bench by the door,
    rugs, shelves and a pot upstairs, brick shoulders on the chimney, a log pile and barrels on the right side.
    L3 (the town look): flower boxes (soil in trapdoor boxes) under the front and back gable windows, smoke from the chimney, a fenced back garden (composter, flowers, a bench, a bush), a lantern post and banner at the front,
    vines on the stone walls."""
    W, D, H = width + 4, depth + 5, 9
    s = Structure('townhouse', (W, H, D), palette='oak_a2')
    x0, x1, z0, z1 = 2, 1 + width, 2, 1 + depth          # 2..6, 2..7
    mid = (x0 + x1) // 2                                   # 4
    jz = z0 - 1                                            # 1: the jettied front of the upper storey
    fl = 4                                                 # upper floor layer
    knee = 5                                               # upper knee wall (roof top plate at 6)
    cz = 5                                                 # hearth and chimney row
    chim_x = x0 - 1                                        # 1

    # ------------------------------------------------------------------ L1 shell
    s.at(1, 'shell')
    for x in range(x0, x1 + 1):                            # plinth and floor
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if edge:
                P(s, x, 0, z, 'mossy_cobblestone' if noise(x, 0, z, 7) < 0.3 else 'cobblestone')
            else:
                P(s, x, 0, z, 'spruce_planks')
    for y in range(1, 4):                                  # stone ground storey with dark oak corner posts
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                if x in (x0, x1) and z in (z0, z1):
                    P(s, x, y, z, 'dark_oak_log', axis='y')
                else:
                    P(s, x, y, z, 'stone_bricks')
    s.remove(mid, 1, z0); s.remove(mid, 2, z0)
    s.door(mid, 1, z0, 'north', block='oak_door', hinge='left')
    gwin = [(mid - 1, 2, z0), (mid + 1, 2, z0), (x0, 2, z0 + 1), (x1, 2, z0 + 1), (x1, 2, z1 - 1), (mid, 2, z1)]
    for w in gwin:
        s.remove(*w)
    s.remove(x0, 1, cz)                                    # the hearth goes into the left wall

    # upper floor: dark oak beams round the edge (jettied out at the front), spruce planks inside
    for x in range(x0, x1 + 1):
        P(s, x, fl, jz, 'dark_oak_log', axis='x')          # the jetty beam
        P(s, x, fl, z1, 'dark_oak_log', axis='x')
    for z in range(z0, z1):
        P(s, x0, fl, z, 'dark_oak_log', axis='z')
        P(s, x1, fl, z, 'dark_oak_log', axis='z')
    stair_x = x1 - 1                                       # 5: the staircase column
    hole = {(stair_x, z0 + 2), (stair_x, z0 + 3)}          # stairwell (z 4, 5)
    for x in range(x0 + 1, x1):
        for z in range(z0, z1):
            if (x, z) not in hole and (x, z) != (stair_x, z0 + 1):
                P(s, x, fl, z, 'spruce_planks')
    # upper knee wall y 5, front wall on the jetty (z 1), back wall z 7
    for x in range(x0, x1 + 1):
        for z in range(jz, z1 + 1):
            if not (x in (x0, x1) or z in (jz, z1)):
                continue
            corner = x in (x0, x1) and z in (jz, z1)
            P(s, x, knee, z, 'dark_oak_log', axis='y') if corner else P(s, x, knee, z, 'white_terracotta')
    # front gable on the jetty: dark oak frame, plaster infill, two windows
    for y in (6, 7):
        P(s, mid, y, jz, 'dark_oak_log', axis='y')        # king post
        P(s, mid, y, z1, 'dark_oak_log', axis='y')
    for x, z in ((x0, z0 + 1), (x1, z0 + 1), (x1, z0 + 3)):              # little windows in the knee walls
        s.remove(x, knee, z)
        P(s, x, knee, z, 'glass_pane')
    uwin = [(mid - 1, 6, jz), (mid + 1, 6, jz), (mid - 1, 6, z1), (mid + 1, 6, z1)]
    # brick chimney against the left wall, up past the eaves
    for y in (0, 1, 2):                                    # a stepped breast: two deep at the bottom
        P(s, chim_x, y, cz, 'bricks')
        P(s, chim_x - 1, y, cz, 'bricks')
    for y in range(3, 8):
        P(s, chim_x, y, cz, 'bricks')
    for x, y, z in uwin:                                   # reserve the window holes before the gables fill
        P(s, x, y, z, 'glass_pane')
    gable_roof(s, x0, x1, jz, z1, knee, front=1, back=1, cap=None, gable='white_terracotta',
               skip={(chim_x, knee + 1, cz)})
    for x in (x0, mid, x1):                                # brackets under the jetty
        P(s, x, 3, jz, 'dark_oak_stairs', facing='south', **TOP)

    # ------------------------------------------------------------------ L1 fittings
    s.at(1, 'fittings')
    for x, y, z in gwin:
        P(s, x, y, z, 'glass_pane')
    P(s, x0, 1, cz, 'furnace', facing='east', lit='false')          # the hearth
    # staircase up the right wall, rising to the front: z 6, 5, 4 and the top step in the floor at z 3
    for i, z in enumerate((z1 - 1, z1 - 2, z1 - 3, z1 - 4)):
        P(s, stair_x, 1 + i, z, 'spruce_stairs', facing='north', **STAIR)
    P(s, stair_x, 1, z0 + 1, 'barrel', facing='up', open='false')    # under the stairs
    P(s, stair_x, 2, z0 + 1, 'barrel', facing='up', open='false')
    P(s, stair_x, 1, z0 + 2, 'chest', facing='west', type='single', waterlogged='false')
    P(s, stair_x, 1, z0 + 3, 'barrel', facing='west', open='false')
    P(s, x0 + 1, 1, z0 + 1, 'oak_slab', **SLAB_TOP)                  # table and chair
    P(s, x0 + 1, 1, z0 + 2, 'oak_stairs', facing='south', **STAIR)
    P(s, x0 + 1, 1, z1 - 1, 'barrel', facing='up', open='false')     # the pantry barrel by the hearth
    P(s, x0 + 1, 2, cz - 1, 'wall_torch', facing='east')
    P(s, mid, 3, z1 - 1, 'wall_torch', facing='north')
    P(s, mid, 0, jz, 'cobblestone_stairs', facing='south', **STAIR)  # step at the door
    # upstairs: 3 beds and a chest
    s.bed(x0 + 1, knee, z1 - 2, 'south')
    s.bed(mid, knee, z1 - 2, 'south')
    s.bed(x0 + 1, knee, z0 + 1, 'north')
    P(s, stair_x, knee, z1 - 1, 'chest', facing='west', type='single', waterlogged='false')

    # ------------------------------------------------------------------ L2: lived in
    s.at(2, 'flourish2')
    shutters(s, *gwin[2], 'west'); shutters(s, *gwin[3], 'east'); shutters(s, *gwin[4], 'east')
    shutters(s, *gwin[5], 'south')
    for x, f in ((mid - 1, 'poppy'), (mid + 1, 'red_tulip')):         # planters under the front windows
        P(s, x, 0, jz, 'coarse_dirt')
        P(s, x, 0, jz - 1, 'spruce_trapdoor', facing='north', **OPEN)
        P(s, x, 1, jz, f)
    for x in (mid - 1, mid + 1):                                    # lanterns under the jetty
        P(s, x, 3, jz, 'lantern', **HANG)
    P(s, x1 + 1, 0, z0, 'spruce_stairs', facing='east', **STAIR)       # bench by the right corner
    P(s, x1 + 1, 0, z0 + 1, 'spruce_stairs', facing='east', **STAIR)
    P(s, chim_x - 1, 3, cz, 'brick_stairs', facing='east', **STAIR)      # chimney shoulder
    for z in (cz - 1, cz + 1):                                       # brick stairs hugging the breast
        P(s, chim_x, 0, z, 'brick_stairs', facing='south' if z < cz else 'north', **STAIR)
    for z in (z1 - 2, z1 - 1, z1):                                   # log pile and a barrel on the right side
        P(s, x1 + 1, 0, z, 'spruce_log', axis='z')
    P(s, x1 + 1, 1, z1 - 1, 'spruce_log', axis='z')
    P(s, x1 + 1, 1, z1 - 2, 'spruce_log', axis='z')
    P(s, x1 + 2, 0, z1 - 1, 'barrel', facing='up', open='false')
    P(s, mid, 1, z0 + 1, 'red_carpet'); P(s, mid, 1, z0 + 2, 'red_carpet'); P(s, x0 + 1, 1, cz, 'red_carpet')
    P(s, x0 + 1, 3, z0 + 1, 'spruce_trapdoor', facing='east', **SHELF)   # shelves over the table
    P(s, x0 + 1, 3, z0 + 2, 'spruce_trapdoor', facing='east', **SHELF)
    P(s, mid, knee, z0 + 1, 'brown_carpet'); P(s, mid, knee, z0 + 2, 'brown_carpet')
    P(s, x1 - 1, knee, z0, 'flower_pot')                            # pot on the floor by the front window
    s.decorate(x1 - 1, knee, z0, 'potted_plant', plant='azure_bluet')
    P(s, mid, 7, z0 + 3, 'lantern', **HANG)                          # under the ridge (ridge at y 8)

    # ------------------------------------------------------------------ L3: the town look
    s.at(3, 'flourish3')
    # flower boxes on the beam ends under the front and back gable windows: soil, trapdoor ends, flowers
    for bz, flowers in ((jz - 1, ('cornflower', 'fern', 'oxeye_daisy')), (z1 + 1, ('allium', 'fern', 'azure_bluet'))):
        for x, f in zip((mid - 1, mid, mid + 1), flowers):
            P(s, x, 4, bz, 'coarse_dirt')
            P(s, x, 5, bz, f)
        P(s, mid - 2, 4, bz, 'spruce_trapdoor', facing='west', **OPEN)
        P(s, mid + 2, 4, bz, 'spruce_trapdoor', facing='east', **OPEN)
    P(s, chim_x, 8, cz, 'campfire', facing='north', lit='true', signal_fire='false', waterlogged='false')
    # back garden behind the house: a fenced plot of two rows (a gate on the right, decoration only: the garden
    # needs no visits)
    gz0, gz1 = z1 + 1, D - 1                                       # 8 (under the back eave) .. 10 (fence)
    for x in range(x0 - 1, x1 + 2):
        P(s, x, 0, gz1, 'spruce_fence')
    for z in (gz0, gz0 + 1):
        P(s, x0 - 1, 0, z, 'spruce_fence')
    P(s, x1 + 1, 0, gz0 + 1, 'spruce_fence')
    P(s, x1 + 1, 0, gz0, 'spruce_fence_gate', facing='east', open='false', powered='false', in_wall='false')
    P(s, x0, 0, gz0, 'composter', level='3')
    P(s, mid, 0, gz0, 'spruce_stairs', facing='north', **STAIR)     # a bench against the back wall
    P(s, x1, 0, gz0, 'oak_leaves', **LEAVES)
    P(s, x1, 1, gz0, 'oak_leaves', **LEAVES)
    for x, f in ((x0, 'allium'), (x0 + 1, 'cornflower'), (mid, 'oxeye_daisy'), (x1 - 1, 'azure_bluet'), (x1, 'poppy')):
        P(s, x, 0, gz1 - 1, f)
    P(s, x0 + 1, 0, gz0, 'fern')
    # lantern post and banner at the front
    for y in (0, 1):
        P(s, x0 - 1, y, 0, 'spruce_fence')
    P(s, x0 - 1, 2, 0, 'lantern', **STAND)
    P(s, x1 + 1, 3, z0 + 2, 'red_wall_banner', facing='east')
    s.decorate(x1 + 1, 3, z0 + 2, 'banner_pattern', banner='red_wall_banner', note='the family crest')
    for z in (z1 - 1, z1):                                           # vines on the left stone wall
        P(s, x0 - 1, 2, z, 'vine', west='false', east='true', north='false', south='false', up='false')
        P(s, x0 - 1, 1, z, 'vine', west='false', east='true', north='false', south='false', up='false')
    return connect(s)


STRUCTURES = [dict(path='townhouse', name='Townhouse', tier=4, zone='inner', role='house', group='townhouse',
                   terrain='ground_level', door='front', make=townhouse, changes='First version.',
                   description='Over budget: 339 blocks vs 265 (first build 267 vs 205). Narrow two-storey town house: stone ground floor (kitchen-living room with hearth, '
                               'table, stores under the stairs), plaster and dark oak upper storey jettied over the '
                               'street on brackets, 3 beds upstairs by a staircase; L2 shutters, planters, lanterns '
                               'and a log pile, L3 a back garden, jetty flower boxes, chimney smoke and a lantern post.')]

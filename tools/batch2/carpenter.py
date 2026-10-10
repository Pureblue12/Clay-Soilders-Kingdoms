"""Carpenter or fletcher (tier 2, look A): a timber workshop with an open front under a gable roof."""
from make_batch1_structures import Structure
from revise1.blockconn import connect, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
OPEN = dict(half='bottom', open='true', powered='false', waterlogged='false')
HANGING = dict(hanging='true', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')


def carpenter(width=5, depth=5, wall_height=3):
    """Carpenter: cobblestone plinth, plaster walls in a dark oak frame, spruce stair gable roof on log top plates with
    one block of overhang at the sides and the front. The front is an open workshop bay (3 wide, a log lintel over
    it); the door is in the back wall, out to the yard. Crafting table as the L1 workstation (the fletching table,
    the signature, is UNKNOWN and comes at L2); log stack and sawhorse outside under the side eaves.

    Level 1 is version 3 unchanged (only the box grew: a free column on each side, a row in front and two behind, for
    the level 3 yard). Level 2 dresses the workshop: fletching table and grindstone in the bay, sawdust, a board on
    the bench, shelves, a lantern on a chain, a plank being sawn, a chopping block, a step into the bay. Level 3 is the
    outside: shop sign, lanterns at the front corners, a lumber rack, flower boxes under the back windows, an awning
    over the back door, and finished pieces on display out front (a chair, a table, a crate of arrows)."""
    W, D = width + 4, depth + 4                     # 9 x 9: version 3's 7 x 6 plus the yard round it
    s = Structure('carpenter', (W, 1 + wall_height + 3, D), palette='oak_a2')
    # Quality check round 2: style A colours instead of one oak brown everywhere. White plaster infill in a dark oak
    # frame, a spruce roof with a dark ridge (all KNOWN-OK).
    s.p = {**s.p, 'wall': 'white_terracotta', 'frame': 'dark_oak_log'}
    x0, x1, z0, z1 = 2, width + 1, 2, depth + 1     # walls (x 2..6, z 2..6)
    ex0, ex1, zf = x0 - 1, x1 + 1, z0 - 1          # eave columns and the front overhang row (version 3's edges)
    mid = (x0 + x1) // 2
    midz = (z0 + z1) // 2
    top = wall_height                              # last wall layer (3)
    s.at(1, 'shell')
    s.ring(x0, z0, x1, z1, 0, 'base')
    s.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, 'floor')
    for y in range(1, top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                if corner:
                    s.put(x, y, z, 'frame', axis='y')
                elif z == z0:                      # open bay, log lintel on top
                    if y == top:
                        s.put(x, y, z, 'frame', axis='x')
                else:
                    s.put(x, y, z, 'wall')
    s.remove(mid, 1, z1); s.remove(mid, 2, z1)
    s.door(mid, 1, z1, 'south', hinge='left')
    windows = [(x0, 2, midz), (x1, 2, midz), (x0 + 1, 2, z1), (x1 - 1, 2, z1)]
    for x, y, z in windows:
        s.remove(x, y, z)
    for x in (x0, x1):                             # top plates the roof sits on
        for z in range(z0, z1 + 1):
            s.put(x, top + 1, z, 'frame', axis='z')
    for z in range(zf, z1 + 1):                    # gable roof, ridge along the depth; overhang at the front
        s.put(ex0, top + 1, z, 'dark_stairs', facing='east', **STAIR)   # dark eaves row under the spruce roof
        s.put(ex1, top + 1, z, 'dark_stairs', facing='west', **STAIR)
        s.put(x0, top + 2, z, 'roof_stairs', facing='east', **STAIR)
        s.put(x1, top + 2, z, 'roof_stairs', facing='west', **STAIR)
        s.put(x0 + 1, top + 3, z, 'roof_stairs', facing='east', **STAIR)
        s.put(x1 - 1, top + 3, z, 'roof_stairs', facing='west', **STAIR)
        s.put(mid, top + 3, z, 'dark')                              # ridge (a full block: no notch at the gable ends)
    for z in (z0, z1):                             # gable ends
        for x in range(x0 + 1, x1):
            s.put(x, top + 1, z, 'plank2'); s.put(x, top + 2, z, 'plank2')   # spruce, against the oak walls
    s.at(1, 'fittings')
    for x, y, z in windows:                        # panes joined to the wall they sit in, so they don't show as posts
        side = x in (x0, x1)
        s.put(x, y, z, 'window', north=side, south=side, east=not side, west=not side, waterlogged='false')
    s.put(mid, top + 2, zf, 'lantern', hanging='true', waterlogged='false')   # under the front overhang, over the bay
    s.put(ex0, 0, z1, 'oak_leaves', persistent='true', distance='1', waterlogged='false')   # a bush and a poppy
    s.put(ex0, 0, zf, 'poppy')
    s.put(x0 + 1, 1, z0 + 1, 'crafting_table')                 # the workstation, by the open bay
    s.put(x0 + 1, 1, z1 - 1, 'chest', facing='east', type='single', waterlogged='false')
    s.put(x1 - 1, 1, z1 - 1, 'barrel', facing='up', open='false')      # barrels of sticks
    s.put(x1 - 1, 2, z1 - 1, 'barrel', facing='up', open='false')
    s.put(x1 - 1, 1, z0 + 1, 'chair', facing='east', **STAIR)          # bench along the right wall
    s.put(x1 - 1, 1, midz, 'chair', facing='east', **STAIR)
    for x in (x0 + 1, x1 - 1):                                       # torches inside the back wall
        s.put(x, 3, z1 - 1, 'wall_torch', facing='north')
    # Quality check round 3: shutters, the log pile and the sawhorse from level 1, so it reads as a carpenter at once.
    for x, y, z in windows[:2]:                                      # shutters on the side windows
        out = x - 1 if x == x0 else x + 1
        f = 'west' if x == x0 else 'east'
        for dz in (-1, 1):
            s.put(out, y, z + dz, 'trapdoor', facing=f, half='bottom', open='true', powered='false', waterlogged='false')
    for z in (z0 + 1, z0 + 2, z0 + 3):                               # log stack under the left eave
        s.put(ex0, 0, z, 'frame', axis='z')
        if z != z0 + 3:
            s.put(ex0, 1, z, 'frame', axis='z')
    for z in (z0 + 1, z0 + 3):                                       # sawhorse under the right eave
        s.put(ex1, 0, z, 'post')
    for z in (z0 + 1, z0 + 2, z0 + 3):
        s.put(ex1, 1, z, 'oak_slab', type='bottom', waterlogged='false')

    s.at(2, 'flourish2')                                             # workshop dressing
    s.put(x0 + 1, 1, midz, 'fletching_table')                        # the signature, beside the crafting table
    put_id(s, x1 - 1, 1, z0, 'grindstone', face='floor', facing='west')   # grindstone in the bay, right
    put_id(s, x0 + 1, 1, z0, 'spruce_slab', type='top', waterlogged='false')   # a saw bench in the bay, left
    s.decorate(x0 + 1, 2, z0, 'item', item='minecraft:oak_planks', note='a board on the saw bench')
    put_id(s, x0 + 1, 2, z0 + 1, 'oak_pressure_plate', powered='false')   # a board on the workbench
    for x, z in ((mid, z0), (mid, midz)):                            # sawdust on the floor
        put_id(s, x, 1, z, 'brown_carpet')
    put_id(s, mid, top + 1, midz, 'chain', axis='y')                 # lantern on a chain from the attic
    put_id(s, mid, top, midz, 'lantern', **HANGING)
    for z in (z0 + 1, midz):                                         # shelves over the right bench
        put_id(s, x1 - 1, 3, z, 'oak_trapdoor', facing='west', half='top', open='false', powered='false',
               waterlogged='false')
    s.decorate(x0 + 1, 2, z1 - 1, 'item_frame', item='minecraft:arrow', facing='north', note='arrows on the back wall')
    s.decorate(x1 - 1, 3, z1 - 1, 'item_frame', item='minecraft:bow', facing='north', note='bow on the back wall')
    put_id(s, ex1, 2, z0 + 2, 'oak_planks')                          # a plank being sawn on the sawhorse
    put_id(s, mid, 0, zf, 'cobblestone_stairs', facing='south', **STAIR)  # a step up into the bay
    put_id(s, mid, 0, z1 + 1, 'cobblestone_stairs', facing='north', **STAIR)   # and one at the back door
    put_id(s, ex0, 1, z0 + 3, 'spruce_log', axis='z')                # the log stack topped up
    put_id(s, x0, 0, zf, 'oak_planks'); put_id(s, x0, 1, zf, 'oak_slab', type='bottom', waterlogged='false')  # fresh planks
    put_id(s, x1, 0, zf, 'stripped_oak_log', axis='y')              # a chopping block under the front eave
    s.decorate(x1, 1, zf, 'item', item='minecraft:iron_axe', note='axe in the chopping block')

    s.at(3, 'flourish3')                                             # the outside
    s.put(ex1, 2, z0, 'oak_wall_sign', facing='east', waterlogged='false')   # shop sign on the front right post
    s.decorate(ex1, 2, z0, 'sign_text', lines=['', 'Carpenter', '& Fletcher', ''])
    for x in (x0, x1):                                               # lanterns under the front eave corners
        put_id(s, x, top + 1, zf, 'lantern', **HANGING)
    # lumber rack beside the log stack: two fence posts, sawn timber stacked between them, slab caps
    for z in (z0, z0 + 3):
        put_id(s, 0, 0, z, 'spruce_fence'); put_id(s, 0, 1, z, 'spruce_fence')
    for z in (z0 + 1, z0 + 2):
        put_id(s, 0, 0, z, 'stripped_oak_log', axis='z'); put_id(s, 0, 1, z, 'stripped_oak_log', axis='z')
    for z in range(z0, z0 + 4):
        put_id(s, 0, 2, z, 'spruce_slab', type='bottom', waterlogged='false')
    # flower boxes under the back windows: soil in a trapdoor box, flowers at the sill
    for x, side in ((x0 + 1, 'west'), (x1 - 1, 'east')):
        put_id(s, x, 1, z1 + 1, 'coarse_dirt')
        put_id(s, x, 1, z1 + 2, 'spruce_trapdoor', facing='south', **OPEN)
        put_id(s, x + (-1 if side == 'west' else 1), 1, z1 + 1, 'spruce_trapdoor', facing=side, **OPEN)
    put_id(s, x0 + 1, 2, z1 + 1, 'poppy'); put_id(s, x1 - 1, 2, z1 + 1, 'dandelion')
    for x in range(mid - 1, mid + 2):                                # awning over the back door
        put_id(s, x, top, z1 + 1, 'dark_oak_stairs', facing='north', **STAIR)
    # finished pieces on display out front: a chair and a little table (left), a crate of arrows and a gate (right)
    put_id(s, 1, 0, 0, 'oak_stairs', facing='west', **STAIR)
    put_id(s, x0, 0, 0, 'oak_fence'); put_id(s, x0, 1, 0, 'oak_pressure_plate', powered='false')
    put_id(s, x1, 0, 0, 'barrel', facing='up', open='true')
    s.decorate(x1, 1, 0, 'item', item='minecraft:arrow', note='arrows for sale in the open barrel')
    put_id(s, ex1, 0, 0, 'oak_fence_gate', facing='north', open='false', powered='false', in_wall='false')
    put_id(s, ex1 + 1, 0, 0, 'spruce_fence'); put_id(s, ex1 + 1, 1, 0, 'spruce_fence')   # lantern post by the road
    put_id(s, ex1 + 1, 2, 0, 'lantern', **LANTERN)
    put_id(s, ex0, 2, z0 + 2, 'moss_carpet')                         # moss on the log stack
    s.put(ex1, 0, z1, 'plank2'); s.put(ex1, 1, z1, 'roof_slab', type='bottom', waterlogged='false')  # cut boards
    kept = {p: b for p, b in s.blocks.items() if 'north' in b[1]}   # version 3's panes keep their set states
    connect(s)
    s.blocks.update(kept)
    return s


STRUCTURES = [dict(path='carpenter', name='Carpenter or fletcher', tier=2, zone='inner', role='workshop',
                   group='carpenter', terrain='ground_level', door='front', make=carpenter,
                   changes='Level 1 is unchanged; level 2 now dresses the workshop (fletching table, grindstone and '
                           'chopping block in the bay, sawdust, shelves, a hanging lantern, a plank on the sawhorse, '
                           'steps), and level 3 dresses the outside (shop sign, corner lanterns, a lumber rack, flower '
                           'boxes and an awning at the back, a chair, table, arrow crate and gate on display out front).',
                   description='Timber workshop entered by an open front bay (crafting table, fletching table from L2), back door '
                               'to the yard; log stack and sawhorse under the side eaves; a yard with a lumber rack and '
                               'goods on display from L3.')]

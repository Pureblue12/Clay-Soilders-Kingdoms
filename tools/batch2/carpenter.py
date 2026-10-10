"""Carpenter or fletcher (tier 2, look A): a timber workshop with an open front under a gable roof."""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', shape='straight', waterlogged='false')


def carpenter(width=5, depth=5, wall_height=3):
    """Carpenter: cobblestone plinth, plank walls between oak log posts, oak stair gable roof on log top plates with
    one block of overhang at the sides and the front. The front is an open workshop bay (3 wide, a log lintel over
    it); the door is in the back wall, out to the yard. Crafting table as the L1 workstation (the fletching table,
    the signature, is UNKNOWN and comes at L2); log stack and sawhorse outside under the side eaves."""
    W, D = width + 2, depth + 1
    s = Structure('carpenter', (W, 1 + wall_height + 3, D), palette='oak_a2')
    # Quality check round 2: style A colours instead of one oak brown everywhere. White plaster infill in a dark oak
    # frame, a spruce roof with a dark ridge (all KNOWN-OK).
    s.p = {**s.p, 'wall': 'white_terracotta', 'frame': 'dark_oak_log'}
    x0, x1, z0, z1 = 1, width, 1, depth            # walls
    mid = (x0 + x1) // 2
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
    windows = [(x0, 2, mid), (x1, 2, mid), (x0 + 1, 2, z1), (x1 - 1, 2, z1)]
    for x, y, z in windows:
        s.remove(x, y, z)
    for x in (x0, x1):                             # top plates the roof sits on
        for z in range(z0, z1 + 1):
            s.put(x, top + 1, z, 'frame', axis='z')
    for z in range(0, z1 + 1):                     # gable roof, ridge along the depth; overhang at the front (z 0)
        s.put(x0 - 1, top + 1, z, 'dark_stairs', facing='east', **STAIR)   # dark eaves row under the spruce roof
        s.put(x1 + 1, top + 1, z, 'dark_stairs', facing='west', **STAIR)
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
    s.put(mid, top + 2, 0, 'lantern', hanging='true', waterlogged='false')   # under the front overhang, over the bay
    for x in (x0 - 1,):                                              # a bush and a poppy by the left wall
        s.put(x, 0, z1, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    s.put(0, 0, 0, 'poppy')
    s.put(x0 + 1, 1, z0 + 1, 'crafting_table')                 # the workstation, by the open bay
    s.put(x0 + 1, 1, z1 - 1, 'chest', facing='east', type='single', waterlogged='false')
    s.put(x1 - 1, 1, z1 - 1, 'barrel', facing='up', open='false')      # barrels of sticks
    s.put(x1 - 1, 2, z1 - 1, 'barrel', facing='up', open='false')
    s.put(x1 - 1, 1, z0 + 1, 'chair', facing='east', **STAIR)          # bench along the right wall
    s.put(x1 - 1, 1, mid, 'chair', facing='east', **STAIR)
    for x in (x0 + 1, x1 - 1):                                       # torches inside the back wall
        s.put(x, 3, z1 - 1, 'wall_torch', facing='north')
    # Quality check round 3: shutters, the log pile and the sawhorse from level 1, so it reads as a carpenter at once.
    for x, y, z in windows[:2]:                                      # shutters on the side windows
        out = x - 1 if x == x0 else x + 1
        f = 'west' if x == x0 else 'east'
        for dz in (-1, 1):
            s.put(out, y, z + dz, 'trapdoor', facing=f, half='bottom', open='true', powered='false', waterlogged='false')
    for z in (z0 + 1, z0 + 2, z0 + 3):                               # log stack under the left eave
        s.put(0, 0, z, 'frame', axis='z')
        if z != z0 + 3:
            s.put(0, 1, z, 'frame', axis='z')
    for z in (z0 + 1, z0 + 3):                                       # sawhorse under the right eave
        s.put(W - 1, 0, z, 'post')
    for z in (z0 + 1, z0 + 2, z0 + 3):
        s.put(W - 1, 1, z, 'oak_slab', type='bottom', waterlogged='false')
    s.at(2, 'flourish2')
    s.put(x0 + 1, 1, mid, 'fletching_table')                         # the signature: a fletching table in the bay
    s.put(x1 + 1, 2, z0, 'oak_wall_sign', facing='east', waterlogged='false')   # shop sign on the front right post
    s.decorate(x1 + 1, 2, z0, 'sign_text', lines=['', 'Carpenter', '& Fletcher', ''])
    s.at(3, 'flourish3')
    s.put(mid, top + 1, mid, 'chain', axis='y')                          # lantern on a chain from the attic
    s.put(mid, top, mid, 'lantern', hanging='true', waterlogged='false')
    s.decorate(x0 + 1, 2, z1 - 1, 'item_frame', item='minecraft:arrow', facing='north', note='arrows on the back wall')
    s.decorate(x1 - 1, 3, z1 - 1, 'item_frame', item='minecraft:bow', facing='north', note='bow on the back wall')
    s.put(x0 + 1, 2, z0 + 1, 'oak_pressure_plate', powered='false')   # a board on the workbench
    s.put(0, 2, z0 + 2, 'moss_carpet')                                # moss on the log stack
    s.put(W - 1, 0, z1, 'plank2'); s.put(W - 1, 1, z1, 'roof_slab', type='bottom', waterlogged='false')  # cut boards
    s.put(1, 0, 0, 'stripped', axis='y')                              # chopping block in front of the bay
    s.put(W - 1, 0, 0, 'barrel', facing='north', open='false')
    return s


STRUCTURES = [dict(path='carpenter', name='Carpenter or fletcher', tier=2, zone='inner', role='workshop',
                   group='carpenter', terrain='ground_level', door='front', make=carpenter,
                   description='Timber workshop entered by an open front bay (crafting table, fletching table from L2), back door '
                               'to the yard; log stack and sawhorse under the side eaves.')]

"""Keep, tier 2: the tier-1 tower plus a stone-and-timber hall on its right side (docs/STRUCTURES.md, keep tier 2)."""
from make_batch1_structures import Structure, keep1

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
TOP = dict(half='top', shape='straight', waterlogged='false')


def keep2(hall_length=11, hall_depth=7, margin=1):
    """The tier-1 keep's shell and fittings, unchanged (D52: tier 2 extends the keep, builders never remove blocks),
    plus a hall: cobblestone base course, a stone brick plinth row, oak plank walls in an oak log frame, a spruce gable
    roof with one block of overhang, a throne on a dais at the far end, and a storeroom with its own door to the yard
    (carriers don't cross the hall). The hall doesn't open into the tower: that would mean breaking a tier-1 wall."""
    tower = keep1()
    tw = tower.size[0]                                          # 9
    x0, x1 = tw, tw + hall_length - 1                           # hall walls x 9..19 (x 8 is the tower's wall)
    z0, z1 = margin, margin + hall_depth - 1                    # front wall z 1, back wall z 7
    W, D = x1 + 1 + margin, z1 + 1 + margin                     # one block of roof overhang at the end and front/back
    s = Structure('keep_2', (W, 9, D), palette='oak_a2')

    for pos, (name, props, level) in sorted(tower.blocks.items()):   # the tier-1 keep, shell and fittings
        if level == 1:
            s.at(1, tower.layer_of[pos]).put(*pos, name, **props)

    plate = 5                                                   # top plate; walls are y 1..4
    ridge_z = (z0 + z1) // 2                                    # 4
    door_x, store_door_x, wall_x = x0 + 6, x0 + 1, x0 + 3       # hall door 15, storeroom door 10, partition 12

    s.at(1, 'shell')
    s.ring(x0 - 1, z0, x1, z1, 0, 'base')                       # base course (joins the tower's base layer)
    s.fill(x0, 0, z0 + 1, x1 - 1, 0, z1 - 1, 'floor')
    posts = {x0, wall_x, door_x - 2, door_x + 2, x1}
    for y in range(1, plate):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    if x == x0 and z not in (z0, z1):
                        continue                                # the tower's wall is the hall's left end
                    corner_or_post = x in posts and z in (z0, z1) or x == x1 and z in (z0, z1)
                    block = 'frame' if corner_or_post else ('hall_stone' if y == 1 else 'wall')
                    s.put(x, y, z, block, **({'axis': 'y'} if block == 'frame' else {}))
    for x in range(x0, x1 + 1):                                  # top plate along the long walls
        for z in (z0, z1):
            s.put(x, plate, z, 'stripped', axis='x')
    for z in range(z0 + 1, z1):
        s.put(x1, plate, z, 'stripped', axis='z')
    # gable roof along x at 45 degrees, one block of overhang, a flat ridge of top slabs (9 tall at most)
    for i in range(ridge_z):
        y = plate + i
        for x in range(x0, W):
            s.put(x, y, z0 - 1 + i, 'roof_stairs', facing='south', **STAIR)
            s.put(x, y, z1 + 1 - i, 'roof_stairs', facing='north', **STAIR)
    for x in range(x0, W):
        s.put(x, plate + ridge_z - 1, ridge_z, 'roof_slab', type='top', waterlogged='false')
    for y in range(plate + 1, plate + ridge_z):                  # gable end, under the roof
        for z in range(z0 + y - plate, z1 - y + plate + 1):
            if (x1, y, z) not in s.blocks:
                s.put(x1, y, z, 'wall')
    for x in (door_x, store_door_x):                            # doorways
        s.remove(x, 1, z0); s.remove(x, 2, z0)
    s.door(door_x, 1, z0, 'north')
    s.door(store_door_x, 1, z0, 'north')
    windows = [(x0 + 4, z0), (x1 - 2, z0), (x0 + 5, z1), (x1 - 2, z1)]
    for x, z in windows:
        s.remove(x, 2, z)
        s.put(x, 2, z, 'window')
    s.remove(x1, 2, (z0 + z1) // 2); s.put(x1, 2, (z0 + z1) // 2, 'window')
    for z in range(z0 + 1, z1):                                  # storeroom partition (plank, stone plinth)
        for y in range(1, plate):
            s.put(wall_x, y, z, 'hall_stone' if y == 1 else 'wall')

    s.at(1, 'fittings')
    for x in (door_x, store_door_x):                            # a step in front of each door
        s.put(x, 0, z0 - 1, 'stone_stairs', facing='south', **STAIR)
    # hall: dais and throne at the far end, a table with chairs, a hearth with a chimney through the back eave
    for z in (ridge_z - 1, ridge_z, ridge_z + 1):
        s.put(x1 - 1, 1, z, 'roof_slab', type='bottom', waterlogged='false')
    s.put(x1 - 1, 2, ridge_z, 'chair', facing='east', **STAIR)  # throne: back against the end wall
    for x in (door_x - 1, door_x):
        s.put(x, 1, ridge_z, 'shed_roof', type='top', waterlogged='false')   # table (a slab at seat height)
        s.put(x, 1, ridge_z - 1, 'chair', facing='north', **STAIR)
        s.put(x, 1, ridge_z + 1, 'chair', facing='south', **STAIR)
    hx = door_x
    s.put(hx, 1, z1 - 1, 'hearth', facing='north', lit='false')
    s.remove(hx, 2, z1); s.put(hx, 2, z1, 'chimney')           # flue through the back wall
    for y in range(1, 9):                                        # chimney outside the back wall, through the eave
        s.remove(hx, y, z1 + 1)
        s.put(hx, y, z1 + 1, 'chimney')
    # storeroom: barrels and chests
    for z in (z0 + 1, z0 + 2):
        s.put(x0, 1, z, 'barrel', facing='up', open='false')
    s.put(x0, 2, z0 + 1, 'barrel', facing='up', open='false')
    s.put(x0, 1, z1 - 1, 'chest', facing='east', type='single', waterlogged='false')
    s.put(x0 + 2, 1, z1 - 1, 'chest', facing='north', type='single', waterlogged='false')
    s.put(x0 + 1, 1, z1 - 1, 'barrel', facing='up', open='false')
    # lights: inside on solid wall, and one on each side of the hall door outside
    for x, z, f in ((door_x - 2, z0 + 1, 'south'), (x1 - 2, z1 - 1, 'north'), (x0 + 1, z1 - 1, 'north'),
                    (wall_x + 1, z1 - 1, 'north')):
        s.put(x, 3, z, 'wall_torch', facing=f)
    for x in (door_x - 1, door_x + 1):
        s.put(x, 2, z0 - 1, 'wall_torch', facing='north')

    s.at(2, 'flourish2')
    for x in (door_x - 1, x1 - 3):                               # lanterns on chains from the ridge
        for y in (plate + 2, plate + 1):
            s.put(x, y, ridge_z, 'chain', axis='y', waterlogged='false')
        s.put(x, plate, ridge_z, 'lantern', hanging='true', waterlogged='false')
    for x, z in windows[:2]:                                     # shutters on the front windows
        for dx in (-1, 1):
            if (x + dx, 2, z0 - 1) not in s.blocks:
                s.put(x + dx, 2, z0 - 1, 'trapdoor', facing='north', half='bottom', open='true', powered='false', waterlogged='false')
    s.put(store_door_x + 1, 3, z0 - 1, 'oak_wall_sign', facing='north', waterlogged='false')   # storeroom sign
    s.decorate(store_door_x + 1, 3, z0 - 1, 'sign_text', text=['Storeroom'])
    for x in (door_x - 1, door_x + 1):                           # banners over the outside lights
        s.put(x, 3, z0 - 1, 'white_wall_banner', facing='north')
        s.decorate(x, 3, z0 - 1, 'banner_pattern', banner='white_wall_banner', note='kingdom colours')
    for x in range(door_x + 1, x1 - 1):                          # carpet runner to the throne
        s.put(x, 1, ridge_z, 'rug')

    s.at(3, 'flourish3')
    s.put(x1 - 1, 3, ridge_z, 'white_wall_banner', facing='west')   # banner behind the throne
    s.decorate(x1 - 1, 3, ridge_z, 'banner_pattern', banner='white_wall_banner', note='royal arms')
    for x in (door_x - 1, door_x):
        s.put(x, 2, ridge_z, 'candle', candles='2', lit='false', waterlogged='false')
    for x in (x0 + 1, x0 + 2):
        s.put(x, 1, z0 + 2, 'brown_carpet')
    return s


STRUCTURES = [dict(path='keep2', name='Keep (tier 2)', tier=2, zone='keep', role='keep', group='keep',
                   terrain='skirt', door='front', make=keep2,
                   description='The tier-1 tower unchanged, plus a stone-and-timber hall with a throne, a table, '
                               'a hearth and a storeroom with its own door.')]

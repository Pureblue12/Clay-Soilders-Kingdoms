"""Barracks, tier 3 (docs/STRUCTURES.md): a stone and timber house where 4 soldiers sleep.

Plan (box x 0..8, z 0..10; z = 0 is the front): the house is 7 x 8 (x 1..7, z 2..9) on a cobblestone plinth; walls
are a cobblestone course with white plaster above in a dark oak frame (posts at the corners, every 3 to 4 on the long
walls and beside the door and back window), a dark oak top plate, half-timbered gables, a spruce gable roof with dark
oak eaves, dark oak barge boards on the gable overhangs, a dark oak ridge with spruce slabs, and upside-down stairs and
a ridge-beam end under each gable overhang. Every window has spruce shutters (and a sill on the long walls); the door
is in the middle of the front with a step; the training dummy (the signature) stands beside it.
Inside (x 2..6, z 3..8), an aisle down the middle (x 4) from the door to the hearth: two beds on each side with their
heads to the long walls and a footlocker between them; by the door the arms store (chest, barrel); at the back the
hearth under a brick chimney, a grindstone, a table with stools and a weapon shelf.
"""
from make_batch1_structures import Structure
from revise1.blockconn import connect, noise, put_id
from batch2.commons import planter

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
TOP = dict(half='top', shape='straight', waterlogged='false')
SLAB = dict(type='bottom', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')
HANGING = dict(hanging='true', waterlogged='false')
OPEN = dict(half='bottom', open='true', powered='false', waterlogged='false')
SHELF = dict(half='top', open='false', powered='false', waterlogged='false')
SILL = SHELF                                              # a closed trapdoor in the top half: a window sill
LEAVES = dict(persistent='true', distance='1', waterlogged='false')


def barracks():
    W, H, D = 9, 9, 11
    s = Structure('barracks', (W, H, D), palette='oak_a2')
    x0, x1, z0, z1 = 1, 7, 2, 9                          # walls: 7 x 8
    mid = 4
    posts_z = (z0, 5, 9, z1)
    posts_x = (x0, 3, 5, x1)                              # gable walls: posts at the corners and beside the door
    side_windows = [(x0, 2, 4), (x0, 2, 7), (x1, 2, 4), (x1, 2, 7)]
    windows = side_windows + [(2, 2, z0), (6, 2, z0), (mid, 2, z1)]
    chim = (6, z1)                                        # chimney in the back wall, right
    s.at(1, 'shell')
    for x in range(x0, x1 + 1):                           # plinth and floor
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                put_id(s, x, 0, z, 'mossy_cobblestone' if noise(x, 0, z, 4) < 0.3 else 'cobblestone')
            else:
                put_id(s, x, 0, z, 'spruce_planks')
    for y in (1, 2, 3):                                   # walls: a cobblestone course, plaster in a dark oak frame
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                post = (x in (x0, x1) and z in posts_z) or (z in (z0, z1) and x in posts_x)
                if post:
                    put_id(s, x, y, z, 'dark_oak_log', axis='y')
                elif y == 1:
                    put_id(s, x, y, z, 'cobblestone')
                else:
                    put_id(s, x, y, z, 'white_terracotta')
    for z in range(z0, z1 + 1):                           # top plates on the long walls
        for x in (x0, x1):
            put_id(s, x, 4, z, 'dark_oak_log', axis='z')
    for x in range(x0 + 1, x1):                           # beams over the gable walls
        for z in (z0, z1):
            put_id(s, x, 4, z, 'dark_oak_log', axis='x')
    for z in range(z0 - 1, z1 + 2):                       # the roof: dark eaves, spruce slopes, a dark ridge with slabs
        verge = z in (z0 - 1, z1 + 1)                     # the gable overhang rows are dark oak (barge boards)
        slope = 'dark_oak_stairs' if verge else 'spruce_stairs'
        put_id(s, 0, 4, z, 'dark_oak_stairs', facing='east', **STAIR)
        put_id(s, W - 1, 4, z, 'dark_oak_stairs', facing='west', **STAIR)
        for i, y in ((1, 5), (2, 6), (3, 7)):
            put_id(s, i, y, z, slope, facing='east', **STAIR)
            put_id(s, W - 1 - i, y, z, slope, facing='west', **STAIR)
        put_id(s, mid, 7, z, 'dark_oak_planks')
        put_id(s, mid, 8, z, 'spruce_slab', **SLAB)
    for z in (z0, z1):                                    # gable ends: half-timber, plaster in a dark oak frame
        for y, xs in ((5, range(2, 7)), (6, range(3, 6))):
            for x in xs:
                if x == mid:
                    put_id(s, x, y, z, 'dark_oak_log', axis='y')
                else:
                    put_id(s, x, y, z, 'white_terracotta')
    for z in (z0 - 1, z1 + 1):                            # under the gable overhang: upside-down stairs, a ridge beam end
        for i, y in ((1, 4), (2, 5), (3, 6)):
            put_id(s, i, y, z, 'spruce_stairs', facing='west', **TOP)
            put_id(s, W - 1 - i, y, z, 'spruce_stairs', facing='east', **TOP)
        put_id(s, mid, 6, z, 'dark_oak_log', axis='z')
    cx, cz = chim                                         # brick chimney rising through the right slope
    for y in range(1, 8):
        if (cx, y, cz) in s.blocks:
            s.remove(cx, y, cz)
        put_id(s, cx, y, cz, 'bricks')
    s.remove(mid, 1, z0); s.remove(mid, 2, z0)
    s.door(mid, 1, z0, 'north', block='spruce_door')
    for x, y, z in windows:
        s.remove(x, y, z)
    put_id(s, mid, 0, z0 - 1, 'cobblestone_stairs', facing='south', **STAIR)   # the step up to the door

    s.at(1, 'fittings')
    for x, y, z in windows:                               # glass panes joined to the wall they sit in
        side = x in (x0, x1)
        put_id(s, x, y, z, 'glass_pane', north=side, south=side, east=not side, west=not side, waterlogged='false')
    for x, y, z in windows:                               # spruce shutters on every window
        if x in (x0, x1):
            out, f = (x - 1, 'west') if x == x0 else (x + 1, 'east')
            for dz in (-1, 1):
                put_id(s, out, y, z + dz, 'spruce_trapdoor', facing=f, **OPEN)
            put_id(s, out, y - 1, z, 'spruce_trapdoor', facing=f, **SILL)          # and a sill under it
        else:
            out, f = (z - 1, 'north') if z == z0 else (z + 1, 'south')
            for dx in (-1, 1):
                put_id(s, x + dx, y, out, 'spruce_trapdoor', facing=f, **OPEN)
    put_id(s, mid, 1, z1 + 1, 'spruce_trapdoor', facing='south', **SILL)       # sill under the back window
    for z in (4, 6):                                      # four beds, heads to the long walls
        s.bed(3, 1, z, 'west'); s.bed(5, 1, z, 'east')
    for z in (5,):                                        # footlockers between the beds
        put_id(s, 2, 1, z, 'chest', facing='east', type='single', waterlogged='false')
        put_id(s, 6, 1, z, 'chest', facing='west', type='single', waterlogged='false')
    put_id(s, 2, 1, 3, 'barrel', facing='up', open='false')               # arms store by the door
    put_id(s, 6, 1, 3, 'chest', facing='west', type='single', waterlogged='false')
    put_id(s, cx, 1, z1 - 1, 'furnace', facing='north', lit='false')       # hearth under the chimney
    put_id(s, 3, 1, 7, 'oak_slab', **SLAB)                                # table and stools at the back
    put_id(s, 2, 1, 7, 'spruce_stairs', facing='west', **STAIR)
    put_id(s, 3, 1, 8, 'spruce_stairs', facing='south', **STAIR)
    put_id(s, 2, 1, 8, 'barrel', facing='up', open='false')
    put_id(s, 6, 1, 7, 'grindstone', face='floor', facing='north')        # the whetstone for blades
    for x, z, f in ((x0 + 1, 5, 'east'), (x1 - 1, 5, 'west')):
        put_id(s, x, 3, z, 'wall_torch', facing=f)        # torches on the inside of the posts
    for x in (3, 5):                                      # torches beside the door, outside, on the door posts
        put_id(s, x, 3, z0 - 1, 'wall_torch', facing='north')
    # the training dummy at the door: a post with a hay body
    put_id(s, 7, 0, 0, 'oak_fence'); put_id(s, 7, 1, 0, 'hay_block', axis='y')
    # green at the foot of the walls: leaf bushes and poppies
    for x, z in ((0, 3), (0, 8), (8, 9), (2, 10)):
        put_id(s, x, 0, z, 'oak_leaves', **LEAVES)
    for x, z in ((0, 4), (0, 7), (8, 3), (1, 10), (7, 10)):
        put_id(s, x, 0, z, 'poppy')

    s.at(2, 'flourish2')
    put_id(s, cx, 8, cz, 'campfire', facing='north', lit='true', signal_fire='false', waterlogged='false')  # smoke
    # planters under the front windows: soil with a spruce trapdoor face, flowers in them
    planter(s, [(2, 1), (3, 1)], y=0, skip={(mid, 1)})
    planter(s, [(5, 1), (6, 1)], y=0, skip={(mid, 1), (7, 0)})
    for x, f in ((2, 'poppy'), (3, 'cornflower'), (5, 'dandelion'), (6, 'poppy')):
        put_id(s, x, 1, 1, f)
    for z in range(z0 + 1, z1 - 1):                       # red runner down the aisle
        put_id(s, mid, 1, z, 'red_carpet')
    for z in (4, 7):                                      # lanterns on chains from the ridge
        put_id(s, mid, 6, z, 'chain', axis='y'); put_id(s, mid, 5, z, 'lantern', **HANGING)
    for z in (7, 8):                                      # weapon shelf over the table
        put_id(s, 2, 2, z, 'spruce_trapdoor', facing='east', **SHELF)
    put_id(s, 3, 2, 7, 'candle', candles='2', lit='false', waterlogged='false')
    put_id(s, 7, 2, 0, 'carved_pumpkin', facing='north')                   # the dummy's head and arms
    put_id(s, 6, 1, 0, 'oak_fence'); put_id(s, 8, 1, 0, 'oak_fence')
    put_id(s, 1, 0, 0, 'oak_fence'); put_id(s, 1, 1, 0, 'lantern', **LANTERN)   # a lamp post at the path
    put_id(s, mid, 5, z0 - 1, 'lantern', **HANGING)       # a lantern under the ridge beam over the door
    put_id(s, mid, 5, z1 + 1, 'lantern', **HANGING)       # and one over the back window
    put_id(s, 2, 3, z0 - 1, 'oak_wall_sign', facing='north', waterlogged='false')   # the duty roster by the door
    s.decorate(2, 3, z0 - 1, 'sign_text', text=['Duty roster', 'Watch: red#2', 'Drill at dawn'])

    s.at(3, 'flourish3')
    for z in (5, 6):                                      # log pile under the left eave, an axe block beside it
        put_id(s, 0, 0, z, 'spruce_log', axis='z')
    put_id(s, 0, 1, 5, 'spruce_log', axis='z')
    put_id(s, W - 1, 0, 8, 'barrel', facing='up', open='false')            # a barrel under the right eave
    put_id(s, W - 1, 1, 8, 'oak_pressure_plate', powered='false')          # its lid
    put_id(s, W - 1, 0, 2, 'hay_block', axis='y')
    for z in (4, 5, 6):                                   # a spear rack under the right eave
        put_id(s, W - 1, 0, z, 'spruce_fence')
    for z in (2, z1):                                     # ivy up the front and back corner posts
        for y in (1, 2, 3):
            if (0, y, z) not in s.blocks:
                put_id(s, 0, y, z, 'vine', north='false', south='false', east='true', west='false', up='false')
    for x, z in ((3, 10), (5, 10)):                       # flowers under the back shutters
        put_id(s, x, 0, z, 'azure_bluet' if x == 3 else 'oxeye_daisy')
    put_id(s, 0, 0, 1, 'red_banner', rotation='0')                          # a flag at the front corner
    s.decorate(0, 0, 1, 'banner_pattern', banner='red_banner', note='kingdom colours')
    s.decorate(5, 0, 0, 'armor_stand', note='a sparring partner in leather armour beside the dummy')
    s.decorate(2, 2, z0 + 1, 'item_frame', item='minecraft:shield', facing='south', note='shield over the arms store')
    s.decorate(x1 - 1, 2, z1 - 2, 'item_frame', item='minecraft:iron_sword', facing='west', note='sword on the wall')
    return connect(s)


STRUCTURES = [dict(path='barracks', name='Barracks', tier=3, zone='inner', role='military', group='barracks',
                   terrain='skirt', door='front', make=barracks,
                   changes='Carlton: "bloated, slim down and add more detail"; quality check round 1. Slimmed from 7 x 11 '
                           'to 7 x 8 (4 beds, 344 blocks at L1 instead of 388). Half-timbered gables (plaster in a dark '
                           'oak frame), posts beside the door and back window, a window in the back wall, shutters on '
                           'every window and sills from L1, dark oak barge boards and ridge so the roof is two colours, '
                           'upside-down stairs and a beam end under the gable overhangs, leaf bushes and poppies from L1, '
                           'a grindstone inside. L2: trapdoor-faced planters with flowers instead of dirt cubes, lanterns '
                           'under both gables, a duty-roster sign. L3: log pile, spear rack, barrel, ivy on the corner '
                           'posts, flowers at the back.',
                   description='Over budget from level 2: 379 blocks vs 350 (first build 344). Stone and timber house '
                               '(7 x 8, half-timbered gables, spruce and dark oak roof): four beds and a footlocker along '
                               'a centre aisle, an arms store by the door, a hearth under a brick chimney, a grindstone, a '
                               'table and a weapon shelf at the back; shutters on every window, planters at the front, '
                               'the training dummy stands at the door.')]

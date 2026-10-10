"""Barracks, tier 3 (docs/STRUCTURES.md): a long stone and timber house where 6 soldiers sleep.

Plan (box x 0..8, z 0..13; z = 0 is the front): the house is 7 x 11 (x 1..7, z 2..12) on a cobblestone plinth; walls
are a cobblestone course with white plaster above in a dark oak frame (posts at the corners and every 3 to 4), a dark
oak top plate, a spruce gable roof with dark oak eaves overhanging 1 all round (z 1 and z 13, x 0 and x 8). The door
is in the middle of the front with a step; the training dummy (the signature) stands beside it.
Inside (x 2..6, z 3..11), an aisle down the middle (x 4) from the door to the hearth: three beds on each side with
their heads to the long walls and footlockers between them; by the door the arms store (chest, barrels); at the back
the hearth under a brick chimney, a table with stools, and a weapon shelf.
"""
from make_batch1_structures import Structure
from revise1.blockconn import connect, noise, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
TOP = dict(half='top', shape='straight', waterlogged='false')
SLAB = dict(type='bottom', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')
HANGING = dict(hanging='true', waterlogged='false')
OPEN = dict(half='bottom', open='true', powered='false', waterlogged='false')
SHELF = dict(half='top', open='false', powered='false', waterlogged='false')


def barracks():
    W, H, D = 9, 9, 14
    s = Structure('barracks', (W, H, D), palette='oak_a2')
    x0, x1, z0, z1 = 1, 7, 2, 12                         # walls
    mid = 4
    posts_z = (z0, 5, 9, z1)
    windows = [(x0, 2, 4), (x0, 2, 7), (x0, 2, 10), (x1, 2, 4), (x1, 2, 7), (x1, 2, 10), (2, 2, z0), (6, 2, z0)]
    chim = (6, z1)                                        # chimney in the back wall, right
    s.at(1, 'shell')
    for x in range(x0, x1 + 1):                           # plinth and floor
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                put_id(s, x, 0, z, 'mossy_cobblestone' if noise(x, 0, z, 4) < 0.3 else 'cobblestone')
            else:
                put_id(s, x, 0, z, 'spruce_planks')
    for y in (1, 2, 3):                                   # walls
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                post = (x in (x0, x1) and z in posts_z) or (z in (z0, z1) and x in (x0, x1))
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
    for z in range(z0 - 1, z1 + 2):                       # the roof: dark eaves, spruce slopes, a slab ridge
        put_id(s, 0, 4, z, 'dark_oak_stairs', facing='east', **STAIR)
        put_id(s, W - 1, 4, z, 'dark_oak_stairs', facing='west', **STAIR)
        for i, y in ((1, 5), (2, 6), (3, 7)):
            put_id(s, i, y, z, 'spruce_stairs', facing='east', **STAIR)
            put_id(s, W - 1 - i, y, z, 'spruce_stairs', facing='west', **STAIR)
        put_id(s, mid, 7, z, 'spruce_planks')
        put_id(s, mid, 8, z, 'spruce_slab', **SLAB)
    for z in (z0, z1):                                    # gable ends, spruce planks
        for y, xs in ((5, range(2, 7)), (6, range(3, 6))):
            for x in xs:
                put_id(s, x, y, z, 'spruce_planks')
    for x, z in ((2, z0 - 1), (6, z0 - 1), (2, z1 + 1), (6, z1 + 1)):   # upside-down stairs under the gable overhang
        put_id(s, x, 5, z, 'spruce_stairs', facing='east' if x < mid else 'west', **TOP)
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
    for z in (4, 6, 8):                                   # six beds, heads to the long walls
        s.bed(3, 1, z, 'west'); s.bed(5, 1, z, 'east')
    for z in (5, 7):                                      # footlockers between the beds
        put_id(s, 2, 1, z, 'chest', facing='east', type='single', waterlogged='false')
        put_id(s, 6, 1, z, 'chest', facing='west', type='single', waterlogged='false')
    put_id(s, 2, 1, 3, 'barrel', facing='up', open='false')               # arms store by the door
    put_id(s, 6, 1, 3, 'chest', facing='west', type='single', waterlogged='false')
    put_id(s, cx, 1, z1 - 1, 'furnace', facing='north', lit='false')       # hearth under the chimney
    put_id(s, 3, 1, 10, 'oak_slab', **SLAB)                               # table and stools at the back
    put_id(s, 2, 1, 10, 'spruce_stairs', facing='west', **STAIR)
    put_id(s, 3, 1, 11, 'spruce_stairs', facing='south', **STAIR)
    put_id(s, 2, 1, 11, 'barrel', facing='up', open='false')
    for x, z, f in ((x0 + 1, 5, 'east'), (x1 - 1, 9, 'west'), (x0 + 1, 9, 'east'), (x1 - 1, 5, 'west')):
        put_id(s, x, 3, z, 'wall_torch', facing=f)        # torches on the inside of the posts
    for x in (3, 5):                                      # torches beside the door, outside
        put_id(s, x, 3, z0 - 1, 'wall_torch', facing='north')
    # the training dummy at the door: a post with a hay body
    put_id(s, 7, 0, 0, 'oak_fence'); put_id(s, 7, 1, 0, 'hay_block', axis='y')

    s.at(2, 'flourish2')
    put_id(s, cx, 8, cz, 'campfire', facing='north', lit='true', signal_fire='false', waterlogged='false')  # smoke
    for x in (2, 6):                                      # flower boxes under the front windows
        put_id(s, x, 0, z0 - 1, 'coarse_dirt'); put_id(s, x, 1, z0 - 1, 'poppy' if x == 2 else 'cornflower')
    for z in range(z0 + 1, z1 - 1):                       # red runner down the aisle
        put_id(s, mid, 1, z, 'red_carpet')
    for z in (5, 9):                                      # lanterns on chains from the ridge
        put_id(s, mid, 6, z, 'chain', axis='y'); put_id(s, mid, 5, z, 'lantern', **HANGING)
    for z in (10, 11):                                    # weapon shelf over the table
        put_id(s, 2, 2, z, 'spruce_trapdoor', facing='east', **SHELF)
    put_id(s, 3, 2, 10, 'candle', candles='2', lit='false', waterlogged='false')
    put_id(s, 7, 2, 0, 'carved_pumpkin', facing='north')                   # the dummy's head and arms
    put_id(s, 6, 1, 0, 'oak_fence'); put_id(s, 8, 1, 0, 'oak_fence')
    put_id(s, 2, 0, 0, 'oak_fence'); put_id(s, 2, 1, 0, 'lantern', **LANTERN)   # a lamp post at the path

    s.at(3, 'flourish3')
    for x, y, z in windows[:6]:                           # shutters on the side windows
        out = x - 1 if x == x0 else x + 1
        f = 'west' if x == x0 else 'east'
        for dz in (-1, 1):
            put_id(s, out, y, z + dz, 'spruce_trapdoor', facing=f, **OPEN)
    for z in (6, 7, 8):                                   # log pile under the left eave
        put_id(s, 0, 0, z, 'spruce_log', axis='z')
    put_id(s, 0, 1, 7, 'spruce_log', axis='z')
    for z in (10, 11):                                    # barrels under the right eave
        put_id(s, W - 1, 0, z, 'barrel', facing='up', open='false')
    put_id(s, W - 1, 0, 2, 'hay_block', axis='y')
    # a spear rack under the right eave: two posts and a rail of fences
    for z in (5, 6, 7, 8):
        put_id(s, W - 1, 0, z, 'spruce_fence')
    for x, z in ((0, 3), (0, 11)):
        put_id(s, x, 0, z, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    put_id(s, 3, 0, 0, 'red_banner', rotation='0')                          # a flag on the path
    s.decorate(3, 0, 0, 'banner_pattern', banner='red_banner', note='kingdom colours')
    s.decorate(5, 0, 0, 'armor_stand', note='a sparring partner in leather armour beside the dummy')
    s.decorate(2, 2, z0 + 1, 'item_frame', item='minecraft:shield', facing='south', note='shield over the arms store')
    s.decorate(x1 - 1, 2, z1 - 2, 'item_frame', item='minecraft:iron_sword', facing='west', note='sword on the wall')
    return connect(s)


STRUCTURES = [dict(path='barracks', name='Barracks', tier=3, zone='inner', role='military', group='barracks',
                   terrain='skirt', door='front', changes='First version.', make=barracks,
                   description='Over budget: 439 blocks vs 290 (first build 388 vs 245). Long stone and timber house (7 x 11, spruce gable roof): six beds with footlockers along '
                               'a centre aisle, an arms store by the door, a hearth under a brick chimney, a table and a '
                               'weapon shelf at the back; the training dummy stands at the door.')]

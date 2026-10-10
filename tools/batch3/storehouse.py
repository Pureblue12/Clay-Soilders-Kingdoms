"""Storehouse (tier 3, look A): a stout timber and stone barn with double doors, a hoist beam and a side awning."""
from make_batch1_structures import Structure
from revise1.blockconn import connect, noise, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
UPSIDE = dict(half='top', shape='straight', waterlogged='false')
BOTTOM = dict(type='bottom', waterlogged='false')
TOP = dict(type='top', waterlogged='false')
HANG = dict(hanging='true', waterlogged='false')
STAND = dict(hanging='false', waterlogged='false')
OPEN = dict(half='bottom', open='true', powered='false', waterlogged='false')
SHUT = dict(half='bottom', open='false', powered='false', waterlogged='false')
SHELF = dict(half='top', open='false', powered='false', waterlogged='false')
LEAVES = dict(persistent='true', distance='1', waterlogged='false')


def storehouse():
    """Storehouse (barn, variant A). Walls x 1..8, z 2..10 (8 by 9): a cobblestone base course and a cobblestone lower
    course, spruce plank walls above in a stripped spruce post frame (corners, door posts, every 4 along the sides),
    dark oak top plates, a spruce stair gable roof with dark oak eaves and a slab-free double ridge, one block of
    overhang all round. Double oak doors in the middle of the front (x 4..5), a step before them.
    Inside: an aisle from the doors to the back; chests along the left wall, barrels stacked along the right, the
    storekeeper's desk at the back.

    Level 1: the barn and its storage (chests, barrels, desk and stool, torches, shutters).
    Level 2: the working storehouse: a lectern ledger on the desk (the workstation; not builder-tested yet), a hoist
    beam out of the front gable with a chain and a hoisted barrel, the side awning on posts (right side) with crates and
    barrels under it, lanterns by the doors, shelves inside.
    Level 3: the busy yard: hay bales and crates by the front, flower boxes under the side windows, a shop sign over the
    doors (text by the decoration step), a banner, a lantern under the awning, a hand cart (decoration step), bushes."""
    s = Structure('storehouse', (12, 9, 13), palette='oak_a2')
    x0, x1, z0, z1 = 1, 8, 2, 10
    top = 4                                          # top plates
    posts_z = (z0, 6, z1)
    s.at(1, 'shell')
    s.ring(x0, z0, x1, z1, 0, 'cobblestone')
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            put_id(s, x, 0, z, 'spruce_planks' if x in (4, 5) else 'oak_planks')   # aisle in spruce
    for y in range(1, top):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                post = (x in (x0, x1) and z in posts_z) or (z in (z0, z1) and x in (3, 6))
                if post:
                    put_id(s, x, y, z, 'stripped_spruce_log', axis='y')
                elif y == 1:
                    put_id(s, x, y, z, 'mossy_cobblestone' if noise(x, y, z, 7) < 0.2 else 'cobblestone')
                else:
                    put_id(s, x, y, z, 'spruce_planks')
    for x in (4, 5):                                 # double doors
        s.remove(x, 1, z0); s.remove(x, 2, z0)
    s.door(4, 1, z0, 'north', block='oak_door', hinge='left')
    s.door(5, 1, z0, 'north', block='oak_door', hinge='right')
    windows = [(x0, 2, 4), (x0, 2, 8), (x1, 2, 4), (x1, 2, 8), (2, 2, z0), (7, 2, z0), (2, 2, z1), (7, 2, z1)]
    for w in windows:
        s.remove(*w)
    for x in range(x0, x1 + 1):                      # top plates all round
        for z in (z0, z1):
            put_id(s, x, top, z, 'dark_oak_log', axis='x')
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            put_id(s, x, top, z, 'dark_oak_log', axis='z')
    for z in range(z0 - 1, z1 + 2):                  # gable roof along z
        put_id(s, x0 - 1, top, z, 'dark_oak_stairs', facing='east', **STAIR)
        put_id(s, x1 + 1, top, z, 'dark_oak_stairs', facing='west', **STAIR)
        for i in range(4):
            put_id(s, x0 + i, top + 1 + i, z, 'spruce_stairs', facing='east', **STAIR)
            put_id(s, x1 - i, top + 1 + i, z, 'spruce_stairs', facing='west', **STAIR)
    for z in (z0, z1):                               # gable ends
        for i in range(3):
            for x in range(x0 + 1 + i, x1 - i):
                put_id(s, x, top + 1 + i, z, 'spruce_planks')
    for z in (z0 - 1, z1 + 1):                       # upside-down stairs under the gable overhangs
        put_id(s, x0, top, z, 'spruce_stairs', facing='west', **UPSIDE)
        put_id(s, x1, top, z, 'spruce_stairs', facing='east', **UPSIDE)
    s.at(1, 'fittings')
    for x, y, z in windows:
        side = x in (x0, x1)
        put_id(s, x, y, z, 'glass_pane', north=side, south=side, east=not side, west=not side, waterlogged='false')
    for x, y, z in windows[:4]:                      # shutters on the side windows
        out, f = (x - 1, 'west') if x == x0 else (x + 1, 'east')
        for dz in (-1, 1):
            put_id(s, out, y, z + dz, 'spruce_trapdoor', facing=f, **OPEN)
    for z in range(z0 + 2, z1 - 1):                  # chests along the left wall, barrels along the right
        put_id(s, x0 + 1, 1, z, 'chest', facing='east', type='single', waterlogged='false')
        put_id(s, x1 - 1, 1, z, 'barrel', facing='up', open='false')
        if z % 2 == 0:
            put_id(s, x1 - 1, 2, z, 'barrel', facing='west', open='false')
    put_id(s, x0 + 1, 1, z0 + 1, 'barrel', facing='up', open='false')
    put_id(s, 5, 1, z1 - 1, 'oak_fence'); put_id(s, 5, 2, z1 - 1, 'oak_pressure_plate', powered='false')   # desk
    put_id(s, 6, 1, z1 - 1, 'oak_stairs', facing='east', **STAIR)                                        # stool
    for x in (3, 6):                                 # torches on the door posts, inside, and on the back posts
        put_id(s, x, 2, z0 + 1, 'wall_torch', facing='south')
        put_id(s, x, 2, z1 - 1, 'wall_torch', facing='north')
    for x in (4, 5):                                 # a step before the doors
        put_id(s, x, 0, z0 - 1, 'cobblestone_stairs', facing='south', **STAIR)

    s.at(2, 'flourish2')
    put_id(s, 4, 1, z1 - 1, 'lectern', facing='south', has_book='false', powered='false')   # the ledger
    s.decorate(4, 1, z1 - 1, 'item', item='minecraft:writable_book', note='the ledger on the lectern')
    put_id(s, 4, 7, z0 - 1, 'spruce_log', axis='z')                  # hoist beam out of the front gable
    put_id(s, 4, 7, z0 - 2, 'spruce_log', axis='z')
    for y in (6, 5):
        put_id(s, 4, y, z0 - 2, 'chain', axis='y')
    put_id(s, 4, 4, z0 - 2, 'barrel', facing='up', open='false')     # a barrel on its way up
    # side awning (right): posts at x 11, slab roof from under the eave out to them, goods under it
    for z in (3, 9):
        put_id(s, 11, 1, z, 'spruce_fence'); put_id(s, 11, 2, z, 'spruce_fence')
    for x in (9, 10, 11):
        for z in range(3, 10):
            put_id(s, x, 3, z, 'spruce_slab', **BOTTOM)
    for x, y, z in ((10, 1, 4), (11, 1, 4), (10, 2, 4), (11, 1, 5), (10, 1, 8), (11, 1, 8), (10, 2, 8)):
        put_id(s, x, y, z, 'barrel', facing='up' if y == 2 else 'east', open='false')   # x 9 stays a walkway
    for z in (6, 7):                                 # crates: plank boxes with trapdoor lids
        put_id(s, 10, 1, z, 'spruce_planks')
        put_id(s, 10, 2, z, 'spruce_trapdoor', facing='east', **SHUT)
    for z in (4, 6, 8):                              # shelves inside over the chests
        put_id(s, x0 + 1, 3, z, 'spruce_trapdoor', facing='east', **SHELF)
    for x in (3, 6):                                 # lanterns by the doors, hung from fence brackets on the plate
        put_id(s, x, top, z0 - 1, 'spruce_fence')
        put_id(s, x, top - 1, z0 - 1, 'lantern', **HANG)

    s.at(3, 'flourish3')
    put_id(s, 4, 3, z0 - 1, 'oak_wall_sign', facing='north', waterlogged='false')   # sign over the doors
    s.decorate(4, 3, z0 - 1, 'sign_text', lines=['', 'Storehouse', '', ''])
    for x in (x0, x1):                               # banners on the front corner posts
        put_id(s, x, 3, z0 - 1, 'red_wall_banner', facing='north')
    for z, flower in ((4, 'poppy'), (8, 'dandelion')):   # planters under the left windows
        put_id(s, x0 - 1, 0, z, 'dirt'); put_id(s, x0 - 1, 1, z, flower)
    for x, z, b in ((1, 0, 'hay'), (2, 0, 'hay'), (1, 1, 'hay'), (7, 0, 'barrel'), (8, 0, 'barrel'), (8, 1, 'crate')):
        if b == 'hay':
            put_id(s, x, 0, z, 'hay_block', axis='y')
        elif b == 'barrel':
            put_id(s, x, 0, z, 'barrel', facing='up', open='false')
        else:
            put_id(s, x, 0, z, 'spruce_planks'); put_id(s, x, 1, z, 'spruce_trapdoor', facing='north', **SHUT)
    put_id(s, 1, 1, 0, 'hay_block', axis='x')
    put_id(s, 11, 2, 6, 'lantern', **HANG)          # a lantern under the awning
    s.decorate(10, 0, 11, 'hand_cart', note='a hand cart by the awning')
    for x, z, b in ((0, 3, 'leaves'), (0, 9, 'leaves'), (0, 6, 'poppy'), (9, 11, 'leaves'), (2, 11, 'dandelion'),
                    (6, 11, 'leaves')):
        if b == 'leaves':
            put_id(s, x, 0, z, 'oak_leaves', **LEAVES)
        else:
            put_id(s, x, 0, z, b)
    connect(s)
    return s


STRUCTURES = [dict(path='storehouse', name='Storehouse', tier=3, zone='inner', role='storage', group='storehouse',
                   terrain='skirt', door='front', make=storehouse, changes='First version.',
                   description='draft')]

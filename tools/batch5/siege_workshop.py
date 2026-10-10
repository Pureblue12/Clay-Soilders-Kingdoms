"""Siege workshop (tier 6, docs/STRUCTURES.md "Siege workshop"): a big open-sided timber shed in the outer zone where
engineers build siege engines; a catapult stands half-built in the middle (its arm is the signature).

Layout (x 0..12, z 0..12; front z = 0, the open gable end the engines roll out of):
  - The shed: oak log posts on cobblestone pads at x 2 / x 8 and z 1, 4, 8, 11, knee braces under dark oak top plates,
    a spruce stair gable roof (dark oak eaves and ridge, slab cap) with one block of overhang, 9 tall. Open on the
    front and the left side; the back gable end is closed with spruce planks between the posts.
  - A tool store lean-to on the right (x 9..11, z 3..9): spruce plank walls under a slab roof, open to the shed.
  - Floor: trodden earth and gravel (terrain flexible), a timber deck under the engine.
  - Inside (L1): the catapult's chassis on its deck (two log rails, cross beams, trapdoor wheels), the crafting table
    (workstation) at the back with the tool chest, barrels of pitch and nails, a log pile and a plank stack, a
    sawhorse; log piles outside under the left eaves.
Level 2 (the engine takes shape): the catapult's A-frame, axle and throwing arm with its cup (the signature), a
counterweight, banners on the front gable, a tool rack in the store, a timber crane over the deck.
Level 3 (the war shop): a battering ram hanging in its frame under the left eaves, lanterns on chains from the tie
beams, a pile of stone shot, a cart, flowers at the posts, banner patterns (decoration step).
"""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, connect, noise, gable_roof

W, D, H = 13, 13, 9
X0, X1, Z0, Z1 = 2, 8, 1, 11
POSTS_Z = (1, 4, 8, 11)
MID = 5


def siege_workshop():
    s = Structure('siege_workshop', (W, H, D), palette='oak_a2')
    s.at(1, 'shell')
    # floor: trodden earth and gravel, a timber deck under the engine
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if 3 <= x <= 7 and 3 <= z <= 9:
                P(s, x, 0, z, 'spruce_planks' if x in (3, 7) or z in (3, 9) else 'oak_planks')
            elif not (x in (X0, X1) and z in POSTS_Z):
                n = noise(x, 0, z, 12)
                P(s, x, 0, z, 'gravel' if n < 0.3 else 'coarse_dirt' if n < 0.75 else 'dirt')
    # posts on pads, top plates, knee braces
    for x in (X0, X1):
        for z in POSTS_Z:
            P(s, x, 0, z, 'cobblestone')
            for y in (1, 2, 3):
                P(s, x, y, z, 'oak_log', axis='y')
        for z in range(Z0, Z1 + 1):
            P(s, x, 4, z, 'dark_oak_log', axis='z')
        for z in POSTS_Z:
            for dz, f in ((-1, 'south'), (1, 'north')):
                if Z0 <= z + dz <= Z1 and (z + dz) not in POSTS_Z:
                    P(s, x, 3, z + dz, 'oak_stairs', facing=f, **TOP)
    for z in (Z0, Z1):                                               # tie beams across the gable ends
        for x in range(X0 + 1, X1):
            P(s, x, 4, z, 'dark_oak_log', axis='x')
    for x in range(X0 + 1, X1):                                      # back wall between the corner posts
        for y in (1, 2, 3):
            P(s, x, y, Z1, 'spruce_planks')
    P(s, MID, 2, Z1, 'oak_log', axis='y'); P(s, MID, 1, Z1, 'oak_log', axis='y'); P(s, MID, 3, Z1, 'oak_log', axis='y')
    gable_roof(s, X0, X1, Z0, Z1, 3, front=1, back=1, gable='spruce_planks', corbels=True)
    for z in (Z0, Z1):                                               # a round window in each gable
        s.remove(MID, 5, z)
    # the tool store lean-to on the right: x 9..11, z 3..9
    for z in range(3, 10):
        for y in (0,):
            P(s, X1 + 1, 0, z, 'coarse_dirt') if (X1 + 1, 0, z) not in s.blocks else None
            P(s, X1 + 2, 0, z, 'coarse_dirt')
        P(s, 11, 0, z, 'cobblestone')
        for y in (1, 2):
            P(s, 11, y, z, 'oak_log' if z in (3, 9) else 'spruce_planks', **({'axis': 'y'} if z in (3, 9) else {}))
    for x in (X1 + 1, X1 + 2):
        P(s, x, 0, 9, 'cobblestone')
        for y in (1, 2):
            P(s, x, y, 9, 'spruce_planks')
    for x in range(X1 + 1, W):
        for z in range(2, 11):
            P(s, x, 3, z, 'spruce_slab', **SLAB)
    s.remove(11, 2, 6)                                               # a window in the store

    # ------------------------------------------------------------------ L1 fittings
    s.at(1, 'fittings')
    P(s, 11, 2, 6, 'glass_pane')
    # the catapult's chassis: two log rails, cross beams, trapdoor wheels
    for z in range(4, 9):
        P(s, 4, 1, z, 'stripped_oak_log', axis='z'); P(s, 6, 1, z, 'stripped_oak_log', axis='z')
    for z in (4, 8):
        P(s, MID, 1, z, 'stripped_oak_log', axis='x')
    for z in (5, 7):
        P(s, 3, 1, z, 'spruce_trapdoor', facing='west', **OPEN); P(s, 7, 1, z, 'spruce_trapdoor', facing='east', **OPEN)
    # the workbench at the back: crafting table (workstation), tool chest, barrels, shelves
    P(s, MID, 1, 10, 'crafting_table')
    P(s, MID - 1, 1, 10, 'chest', facing='north', type='single', waterlogged='false')
    P(s, MID + 1, 1, 10, 'barrel', facing='up', open='false')
    P(s, MID + 2, 1, 10, 'barrel', facing='up', open='false')
    for x in (MID - 1, MID + 1):
        P(s, x, 3, 10, 'spruce_trapdoor', facing='north', **SHELF)
    # a log pile and a plank stack inside on the left, a sawhorse
    for z in (8, 9, 10):
        P(s, 3, 1, z, 'oak_log', axis='z') if False else None
    for z in (9, 10):
        P(s, 3, 1, z, 'oak_log', axis='z')
    P(s, 3, 2, 10, 'oak_log', axis='z')
    P(s, 3, 1, 2, 'oak_planks'); P(s, 3, 2, 2, 'oak_slab', **SLAB)
    P(s, 6, 1, 2, 'spruce_fence'); P(s, 7, 1, 2, 'spruce_fence'); P(s, 6, 2, 2, 'oak_log', axis='x') if False else None
    # log piles outside under the left eaves
    for z in (2, 3, 5, 6, 7):
        P(s, 1, 0, z, 'oak_log', axis='z')
    for z in (2, 3, 6):
        P(s, 1, 1, z, 'oak_log', axis='z')
    for z in (5, 6, 7):
        P(s, 0, 0, z, 'oak_log', axis='z') if False else None
    # light: torches on the posts
    for (x, z, f) in ((X0, 4, 'east'), (X1, 4, 'west'), (X0, 8, 'east'), (X1, 8, 'west')):
        P(s, x + (1 if f == 'east' else -1), 2, z, 'wall_torch', facing=f)
    # the store: chests and barrels along its wall
    for z, b in ((4, 'barrel'), (5, 'chest'), (7, 'chest'), (8, 'barrel')):
        P(s, 10, 1, z, b, **({'facing': 'up', 'open': 'false'} if b == 'barrel' else
                             {'facing': 'west', 'type': 'single', 'waterlogged': 'false'}))
    return connect(s)


STRUCTURES = [dict(path='siege_workshop', name='Siege workshop', tier=6, zone='outer', role='military',
                   group='siege_workshop', terrain='flexible', door=None, make=siege_workshop,
                   changes='First version.',
                   description='Open-sided timber shed where engineers build siege engines: a half-built catapult '
                               'on its deck (L2 raises its A-frame and throwing arm), crafting table, log piles, a '
                               'tool store lean-to; L3 a battering ram in its frame and lanterns on chains.')]

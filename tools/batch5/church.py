"""Church (tier 5, look A, landmark): a stone parish church with a bell tower and spire at the front, a timber porch on
the right side, a lower chancel at the back and a small churchyard beside it (docs/STRUCTURES.md, "Church").
Terrain: stepped in the brief (tower and nave); here everything sits on one stone base course (layer 0).

Layout (17 wide x 24 deep x 20 tall, front z = 0):
- Tower x 4..8, z 1..5 (5 x 5), stone bricks, 13 high to its cornice, then a dark oak spire with stone corner
  pinnacles to 19 (20 tall: needs scaffolding). Door at the front under a hood, a tall entrance hall inside (the bell
  rope hangs here), the belfry with paired openings on every face at y 9..10, the bell's headstock beam at y 11.
- Nave x 2..10, z 5..17 (9 x 13): rubble cobblestone walls 5 high with dressed stone brick quoins, window jambs and a
  top band; buttresses every 4 blocks on both sides; spruce gable roof (dark oak eaves and ridge) to 10 high.
  Lancets on both sides, pews in two blocks of two rows with a cross aisle to the side door.
- Chancel x 4..8, z 17..22 (lower, narrower, its own roof): altar, choir stalls, the priest's chest, an east window.
- Timber porch on the right side (x 11..13, z 10..12) over the side door, facing the churchyard.
- Churchyard (level 2) to the right of the chancel, x 12..16, z 14..23, low cobblestone walls, gravestones.

Level 1: shell and fittings (pews, altar, stalls, chests, torches, clear glass in the nave, the bell beam).
Level 2 (the church in use): the bell and its rope, stained glass in the chancel windows, red runner, font, pulpit,
candles, hanging lanterns, banners on the tower, flower boxes, the churchyard with gravestones.
Level 3 (the feast-day church): carved (chiseled) pinnacles on the tower and porch, a weathervane, a yew in the
churchyard, flowers on the graves and along the walls, vines and moss, banners inside, more candles.
"""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, OPEN, HANG, STAND, LEAVES, connect, noise, gable_roof

PANE = dict(waterlogged='false')
W, H, D = 17, 20, 24
NX0, NX1, NZ0, NZ1, NTOP = 2, 10, 5, 17, 4          # nave (walls y 1..4, ridge 9)
TX0, TX1, TZ0, TZ1, TTOP = 4, 8, 1, 5, 14           # tower (walls y 1..14)
CX0, CX1, CZ1, CTOP = 4, 8, 22, 3                   # chancel (z 17..22, walls y 1..3)
MID = 6
BUTT_Z = (5, 9, 13, 17)
LEFT_WIN, RIGHT_WIN = (7, 11, 15), (7, 15)
SIDE_DOOR_Z = 11
CH_WIN_Z = 19                                       # chancel side windows


def pyramid_ring(s, x0, x1, z0, z1, y, block='dark_oak_stairs'):
    """A ring of stairs rising towards the middle, the corners turned (outer corner shapes)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)):
                continue
            if x == x0 and z == z0:
                P(s, x, y, z, block, facing='east', half='bottom', shape='outer_right', waterlogged='false')
            elif x == x1 and z == z0:
                P(s, x, y, z, block, facing='west', half='bottom', shape='outer_left', waterlogged='false')
            elif x == x0 and z == z1:
                P(s, x, y, z, block, facing='east', half='bottom', shape='outer_left', waterlogged='false')
            elif x == x1 and z == z1:
                P(s, x, y, z, block, facing='west', half='bottom', shape='outer_right', waterlogged='false')
            else:
                f = 'east' if x == x0 else 'west' if x == x1 else 'south' if z == z0 else 'north'
                P(s, x, y, z, block, facing=f, **STAIR)


def rubble(x, y, z):
    return 'mossy_cobblestone' if noise(x, y, z, 11) < 0.22 else 'cobblestone'


def church():
    s = Structure('church', (W, H, D), palette='oak_a2')
    s.at(1, 'shell')

    # ---------------------------------------------------------------- tower (built first: the nave fills around it)
    for x in range(TX0, TX1 + 1):
        for z in range(TZ0, TZ1 + 1):
            P(s, x, 0, z, 'cobblestone' if x in (TX0, TX1) or z in (TZ0, TZ1) else 'stone_bricks')
    for y in range(1, TTOP + 1):
        for x in range(TX0, TX1 + 1):
            for z in range(TZ0, TZ1 + 1):
                if x in (TX0, TX1) or z in (TZ0, TZ1):
                    P(s, x, y, z, 'cobblestone' if y == 1 else 'stone_bricks')
    for x in range(TX0 + 1, TX1):                    # the tower top (floor of the spire)
        for z in range(TZ0 + 1, TZ1):
            P(s, x, TTOP, z, 'stone_bricks')
    s.remove(MID, 1, TZ0); s.remove(MID, 2, TZ0)     # front door
    for y in (1, 2, 3):                              # tall arch into the nave
        s.remove(MID, y, TZ1)
    for y in (5, 6):                                 # tall window over the door, slits in the sides
        s.remove(MID, y, TZ0)
    for x in (TX0, TX1):
        s.remove(x, 5, 3)
    # belfry: paired openings on every face at y 9..10, the floor at y 8
    for y in (11, 12):
        for a in (TX0 + 1, TX1 - 1):
            s.remove(a, y, TZ0); s.remove(a, y, TZ1)
        for c in (TZ0 + 1, TZ1 - 1):
            s.remove(TX0, y, c); s.remove(TX1, y, c)
    for x in range(TX0 + 1, TX1):
        for z in range(TZ0 + 1, TZ1):
            P(s, x, 10, z, 'spruce_planks')
    # string course under the belfry and the cornice at the top (upside-down stairs all round)
    for y in (10, TTOP):
        for x in range(TX0, TX1 + 1):
            A(s, x, y, TZ0 - 1, 'stone_brick_stairs', facing='south', **TOP)
            if y == TTOP:
                A(s, x, y, TZ1 + 1, 'stone_brick_stairs', facing='north', **TOP)
        for z in range(TZ0, TZ1 + 1):
            if y == TTOP or z < TZ1:
                A(s, TX0 - 1, y, z, 'stone_brick_stairs', facing='east', **TOP)
                A(s, TX1 + 1, y, z, 'stone_brick_stairs', facing='west', **TOP)
    for x, z in ((TX0 - 1, TZ0 - 1), (TX1 + 1, TZ0 - 1), (TX0 - 1, TZ1 + 1), (TX1 + 1, TZ1 + 1)):
        P(s, x, TTOP, z, 'stone_bricks')             # cornice corners
    # spire: a broach spire of dark oak shingles, 5 wide at the eave, 3 wide, then a needle (y 15..18)
    y = TTOP + 1                                     # 15
    pyramid_ring(s, TX0, TX1, TZ0, TZ1, y)
    for x in range(TX0 + 1, TX1):
        for z in range(TZ0 + 1, TZ1):
            P(s, x, y, z, 'dark_oak_planks')
    for x in (TX0, TX1):                             # stone pinnacles on the tower corners
        for z in (TZ0, TZ1):
            del s.blocks[(x, y, z)]; del s.layer_of[(x, y, z)]
            P(s, x, y, z, 'cobblestone_wall')
    pyramid_ring(s, TX0 + 1, TX1 - 1, TZ0 + 1, TZ1 - 1, y + 1)
    for yy in (y + 1, y + 2):
        P(s, MID, yy, 3, 'dark_oak_planks')
    P(s, MID, y + 3, 3, 'spruce_fence')              # the spike (L2 adds the weathervane on it)
    # front buttresses of the tower and the door hood
    for x in (TX0, TX1):
        P(s, x, 0, TZ0 - 1, 'cobblestone')
        for yy in range(1, 5):
            P(s, x, yy, TZ0 - 1, 'stone_bricks')
        P(s, x, 5, TZ0 - 1, 'stone_brick_stairs', facing='south', **STAIR)
    for x, f in ((TX0 - 1, 'east'), (TX1 + 1, 'west')):
        P(s, x, 0, TZ0, 'cobblestone')
        for yy in range(1, 4):
            P(s, x, yy, TZ0, 'stone_bricks')
        P(s, x, 4, TZ0, 'stone_brick_stairs', facing=f, **STAIR)
    P(s, MID - 1, 3, TZ0 - 1, 'stone_brick_stairs', facing='west', **TOP)
    P(s, MID + 1, 3, TZ0 - 1, 'stone_brick_stairs', facing='east', **TOP)
    P(s, MID, 3, TZ0 - 1, 'stone_brick_stairs', facing='south', **TOP)
    P(s, MID, 0, TZ0 - 1, 'cobblestone_stairs', facing='south', **STAIR)       # the step
    s.door(MID, 1, TZ0, 'north', block='spruce_door')

    # ---------------------------------------------------------------- nave
    for x in range(NX0, NX1 + 1):
        for z in range(NZ0, NZ1 + 1):
            edge = x in (NX0, NX1) or z in (NZ0, NZ1)
            A(s, x, 0, z, 'cobblestone' if edge else 'stone_bricks')
    jambs = {(NX0, z) for w in LEFT_WIN for z in (w - 1, w + 1)} | {(NX1, z) for w in RIGHT_WIN for z in (w - 1, w + 1)}
    jambs |= {(NX1, SIDE_DOOR_Z - 1), (NX1, SIDE_DOOR_Z + 1)}
    for y in range(1, NTOP + 1):
        for x in range(NX0, NX1 + 1):
            for z in range(NZ0, NZ1 + 1):
                if not (x in (NX0, NX1) or z in (NZ0, NZ1)):
                    continue
                corner = x in (NX0, NX1) and z in (NZ0, NZ1)
                if y == 1:
                    b = rubble(x, y, z)
                elif y == NTOP or corner or (x, z) in jambs:
                    b = 'stone_bricks'
                else:
                    b = rubble(x, y, z)
                A(s, x, y, z, b)
    for z in LEFT_WIN:
        for y in (2, 3):
            s.remove(NX0, y, z)
    for z in RIGHT_WIN:
        for y in (2, 3):
            s.remove(NX1, y, z)
    s.remove(NX1, 1, SIDE_DOOR_Z); s.remove(NX1, 2, SIDE_DOOR_Z)
    for x in range(CX0 + 1, CX1):                    # chancel arch, 3 wide and 3 high
        for y in (1, 2, 3):
            s.remove(x, y, NZ1)
    # buttresses (stepped) on both sides
    for z in BUTT_Z:
        for x, f in ((NX0 - 1, 'east'), (NX1 + 1, 'west')):
            P(s, x, 0, z, 'cobblestone')
            P(s, x, 1, z, 'stone_bricks'); P(s, x, 2, z, 'stone_bricks')
            P(s, x, 3, z, 'stone_brick_stairs', facing=f, **STAIR)
    for x in (NX0, NX1):                             # the back corners also face back
        P(s, x, 0, NZ1 + 1, 'cobblestone'); P(s, x, 1, NZ1 + 1, 'stone_bricks')
        P(s, x, 2, NZ1 + 1, 'stone_brick_stairs', facing='north', **STAIR)
    gable_roof(s, NX0, NX1, NZ0, NZ1, NTOP, front=1, back=1, gable='stone_bricks', plates='spruce_log')
    s.remove(MID, 8, NZ1)    # a small window in the back gable over the chancel roof

    # ---------------------------------------------------------------- chancel
    for x in range(CX0, CX1 + 1):
        for z in range(NZ1 + 1, CZ1 + 1):
            edge = x in (CX0, CX1) or z == CZ1
            P(s, x, 0, z, 'cobblestone' if edge else 'stone_bricks')
    for y in range(1, CTOP + 1):
        for x in range(CX0, CX1 + 1):
            for z in range(NZ1 + 1, CZ1 + 1):
                if not (x in (CX0, CX1) or z == CZ1):
                    continue
                corner = x in (CX0, CX1) and z == CZ1
                A(s, x, y, z, 'stone_bricks' if y == CTOP or corner or y > 1 and z == CZ1 else rubble(x, y, z))
    for x in (CX0, CX1):
        for y in (2, 3):
            s.remove(x, y, CH_WIN_Z)
    for y in (2, 3):
        s.remove(MID, y, CZ1)                         # east window
    for x, f in ((CX0 - 1, 'east'), (CX1 + 1, 'west')):
        P(s, x, 0, CZ1, 'cobblestone'); P(s, x, 1, CZ1, 'stone_bricks')
        P(s, x, 2, CZ1, 'stone_brick_stairs', facing=f, **STAIR)
    gable_roof(s, CX0, CX1, NZ1 + 1, CZ1, CTOP, front=0, back=1, gable='stone_bricks', plates='spruce_log',
               gable_at=(CZ1,))

    # ---------------------------------------------------------------- timber porch on the right side
    px0, px1, pz0, pz1 = NX1 + 1, NX1 + 3, SIDE_DOOR_Z - 1, SIDE_DOOR_Z + 1     # 11..13, 10..12
    for x in range(px0, px1 + 1):
        for z in range(pz0, pz1 + 1):
            P(s, x, 0, z, 'cobblestone')
    for x in (px0, px1):
        for z in (pz0, pz1):
            for yy in (1, 2):
                P(s, x, yy, z, 'spruce_log', axis='y')
    for x in range(px0, px1 + 1):
        P(s, x, 3, pz0, 'dark_oak_log', axis='x'); P(s, x, 3, pz1, 'dark_oak_log', axis='x')
    P(s, px1, 3, SIDE_DOOR_Z, 'dark_oak_log', axis='z')
    P(s, px1, 4, SIDE_DOOR_Z, 'white_terracotta')    # the little plastered gable
    for x in range(px0, px1 + 2):
        A(s, x, 3, pz0 - 1, 'spruce_stairs', facing='south', **STAIR)
        A(s, x, 3, pz1 + 1, 'spruce_stairs', facing='north', **STAIR)
        A(s, x, 4, pz0, 'spruce_stairs', facing='south', **STAIR)
        A(s, x, 4, pz1, 'spruce_stairs', facing='north', **STAIR)
        A(s, x, 5, SIDE_DOOR_Z, 'dark_oak_planks')
    P(s, px1 + 1, 2, pz0, 'spruce_stairs', facing='east', **TOP)    # corbels under the gable overhang
    P(s, px1 + 1, 2, pz1, 'spruce_stairs', facing='east', **TOP)
    P(s, px1 + 1, 0, SIDE_DOOR_Z, 'cobblestone_stairs', facing='west', **STAIR)
    s.door(NX1, 1, SIDE_DOOR_Z, 'east', block='spruce_door', hinge='right')

    # ---------------------------------------------------------------- fittings
    s.at(1, 'fittings')
    for z in LEFT_WIN:
        for y in (2, 3):
            P(s, NX0, y, z, 'glass_pane', north='true', south='true', east='false', west='false', **PANE)
    for z in RIGHT_WIN:
        for y in (2, 3):
            P(s, NX1, y, z, 'glass_pane', north='true', south='true', east='false', west='false', **PANE)
    for y in (5, 6):
        P(s, MID, y, TZ0, 'glass_pane', east='true', west='true', north='false', south='false', **PANE)
    for x in (TX0, TX1):
        P(s, x, 5, 3, 'glass_pane', north='true', south='true', east='false', west='false', **PANE)
    for y in (8,):
        P(s, MID, y, NZ1, 'glass_pane', east='true', west='true', north='false', south='false', **PANE)
    # chancel windows: shutters inside until the stained glass comes (level 2)
    P(s, CX0 + 1, 2, CH_WIN_Z, 'spruce_trapdoor', facing='east', **OPEN); P(s, CX0 + 1, 3, CH_WIN_Z, 'spruce_trapdoor', facing='east', **OPEN)
    P(s, CX1 - 1, 2, CH_WIN_Z, 'spruce_trapdoor', facing='west', **OPEN); P(s, CX1 - 1, 3, CH_WIN_Z, 'spruce_trapdoor', facing='west', **OPEN)
    for y in (2, 3):
        P(s, MID, y, CZ1 - 1, 'spruce_trapdoor', facing='north', **OPEN)
    # pews: two blocks of two rows, a cross aisle at the side door
    for z in (7, 8, 13, 14):
        for x in (NX0 + 1, NX0 + 2, NX0 + 3, NX1 - 3, NX1 - 2, NX1 - 1):
            P(s, x, 1, z, 'spruce_stairs', facing='north', **STAIR)
    # chancel: choir stalls, the altar, the priest's chest
    P(s, CX0 + 1, 1, NZ1 + 1, 'spruce_stairs', facing='west', **STAIR)
    P(s, CX1 - 1, 1, NZ1 + 1, 'spruce_stairs', facing='east', **STAIR)
    P(s, MID - 1, 1, CZ1 - 2, 'stone_brick_stairs', facing='east', **TOP)
    P(s, MID, 1, CZ1 - 2, 'stone_bricks')
    P(s, MID + 1, 1, CZ1 - 2, 'stone_brick_stairs', facing='west', **TOP)
    P(s, MID, 2, CZ1 - 2, 'white_carpet')
    P(s, CX1 - 1, 1, CZ1 - 1, 'chest', facing='west', type='single', waterlogged='false')
    P(s, CX0 + 1, 1, CZ1 - 1, 'barrel', facing='up', open='false')
    # the bell's headstock beam in the belfry
    for x in range(TX0 + 1, TX1):
        P(s, x, 13, 3, 'dark_oak_log', axis='x')
    # torches
    for z in (9, 13):
        P(s, NX0 + 1, 3, z, 'wall_torch', facing='east')
    P(s, NX1 - 1, 3, 9, 'wall_torch', facing='west'); P(s, NX1 - 1, 3, 13, 'wall_torch', facing='west')
    P(s, TX0 + 1, 3, 3, 'wall_torch', facing='east')
    P(s, TX1 - 1, 3, 3, 'wall_torch', facing='west')
    P(s, CX0 + 1, 3, CZ1 - 1, "wall_torch", facing="east")
    P(s, CX1 - 1, 3, CZ1 - 1, "wall_torch", facing="west")

    # ---------------------------------------------------------------- level 2: the church in use
    s.at(2, 'flourish2')
    P(s, MID, TTOP + 5, 3, 'lightning_rod', facing='up', powered='false', waterlogged='false')   # weathervane
    P(s, MID, 12, 3, 'bell', attachment='ceiling', facing='north', powered='false')    # the signature
    for y in (6, 7, 8, 9):                                                               # the bell rope
        P(s, MID, y, 3, 'chain', axis='y')
    for z in (CH_WIN_Z,):                                                                # stained glass, chancel
        for x in (CX0, CX1):
            for y, c in ((2, 'blue'), (3, 'red')):
                P(s, x, y, z, f'{c}_stained_glass_pane', north='true', south='true', east='false', west='false', **PANE)
    for y, c in ((2, 'blue'), (3, 'red')):
        P(s, MID, y, CZ1, f'{c}_stained_glass_pane', east='true', west='true', north='false', south='false', **PANE)
    for z in range(NZ0 + 1, NZ1):                                                        # red runner
        P(s, MID, 1, z, 'red_carpet')
    for z in range(NZ1 + 1, CZ1 - 2):
        P(s, MID, 1, z, 'red_carpet')
    for z in range(TZ0 + 1, TZ1):
        P(s, MID, 1, z, 'red_carpet')
    P(s, NX0 + 1, 1, NZ0 + 1, 'cauldron')                                                # the font by the entrance
    P(s, NX0 + 1, 1, NZ1 - 1, 'lectern', facing='east', has_book='false', powered='false')   # pulpit
    s.decorate(NX0 + 1, 1, NZ1 - 1, 'item', item='minecraft:book', note='the book on the pulpit')
    for x, n in ((MID - 1, '3'), (MID + 1, '3')):
        P(s, x, 2, CZ1 - 2, 'candle', candles=n, lit='true', waterlogged='false')
    for z in (9, 13):                                                                    # lanterns from the ridge
        for y in (8,):
            P(s, MID, y, z, 'chain', axis='y')
        P(s, MID, 7, z, 'lantern', **HANG)
    P(s, MID, CTOP + 2, NZ1 + 2, 'lantern', **HANG)                                       # chancel (ridge y 7)
    for x in (MID - 1, MID + 1):                                                         # lanterns on the hood
        P(s, x, 4, TZ0 - 1, 'lantern', **STAND)
    for x in (TX0 + 1, TX1 - 1):                                                         # banners on the tower
        P(s, x, 7, TZ0 - 1, 'red_wall_banner', facing='north')
        s.decorate(x, 7, TZ0 - 1, 'banner_pattern', banner='red_wall_banner', note='the kingdom colours')
    P(s, NX1 + 2, 2, SIDE_DOOR_Z, 'lantern', **HANG)                                     # porch lantern under the tie beam
    P(s, NX1 + 2, 1, SIDE_DOOR_Z - 1, 'spruce_stairs', facing='north', **STAIR)         # porch benches
    P(s, NX1 + 2, 1, SIDE_DOOR_Z + 1, 'spruce_stairs', facing='south', **STAIR)
    for z in (7, 15):                                                                    # flower boxes, left lancets
        P(s, NX0 - 1, 1, z, 'coarse_dirt')
        P(s, NX0 - 2, 1, z, 'spruce_trapdoor', facing='west', **OPEN)
        P(s, NX0 - 1, 1, z - 1, 'spruce_trapdoor', facing='north', **OPEN)
        P(s, NX0 - 1, 1, z + 1, 'spruce_trapdoor', facing='south', **OPEN)
    P(s, NX0 - 1, 2, 7, 'poppy'); P(s, NX0 - 1, 2, 15, 'cornflower')
    # the churchyard: low cobblestone walls, gravestones in two rows, an opening by the porch
    gx0, gx1, gz0, gz1 = 12, 16, 14, 23
    for z in range(gz0, gz1 + 1):
        P(s, gx1, 0, z, 'cobblestone_wall')
    for x in range(9, gx1):
        P(s, x, 0, gz1, 'cobblestone_wall')
    for x in (gx0 + 2, gx0 + 3):
        P(s, x, 0, gz0, 'cobblestone_wall')
    P(s, gx0 + 3, 1, gz0, 'lantern', **STAND)
    for x in (13, 15):
        for z in (16, 19, 22):
            if (x, z) == (15, 22):
                continue
            P(s, x, 0, z, 'stone_brick_wall')
            P(s, x, 0, z - 1, 'cobblestone_slab', **SLAB)
    P(s, 10, 0, 21, 'stone_brick_wall'); P(s, 10, 0, 20, 'cobblestone_slab', **SLAB)
    P(s, gx1 - 1, 0, gz1 - 1, 'spruce_stairs', facing='east', **STAIR)                   # a bench in the corner

    # ---------------------------------------------------------------- level 3: the feast-day church
    s.at(3, 'flourish3')
    for x, z in ((TX0 - 1, TZ0 - 1), (TX1 + 1, TZ0 - 1), (TX0 - 1, TZ1 + 1), (TX1 + 1, TZ1 + 1)):
        P(s, x, TTOP + 1, z, 'chiseled_stone_bricks')                                    # carved corner pinnacles
        P(s, x, TTOP + 2, z, 'stone_brick_wall')
    P(s, MID, 4, TZ0 - 1, 'chiseled_stone_bricks')                                       # carving over the door hood
    for x in (TX0, TX1):                                                                 # carved caps on the front buttresses
        P(s, x, 6, TZ0 - 1, 'stone_brick_wall')
    for x, z in ((NX1 + 3, SIDE_DOOR_Z - 1), (NX1 + 3, SIDE_DOOR_Z + 1)):               # flowers by the porch step
        A(s, x + 1, 0, z, 'azure_bluet' if z < SIDE_DOOR_Z else 'oxeye_daisy')
    for z in (SIDE_DOOR_Z - 1, SIDE_DOOR_Z + 1):                                         # banners in the nave
        P(s, NX0 + 1, 3, z, 'white_wall_banner', facing='east')
        s.decorate(NX0 + 1, 3, z, 'banner_pattern', banner='white_wall_banner', note='a gold cross')
    CANDLES = dict(candles='3', lit='true', waterlogged='false')
    P(s, NX1 - 1, 1, NZ0 + 1, 'candle', **CANDLES)                                      # candles by the door and pulpit
    P(s, NX1 - 1, 1, NZ1 - 1, 'candle', **CANDLES)
    P(s, CX0 + 1, 2, CZ1 - 1, 'candle', **CANDLES)                                      # on the vestry barrel
    # the churchyard: a yew, flowers on the graves
    for yy in (0, 1, 2):                                                                 # the yew: dark, dense, pointed
        P(s, 14, yy, 20, 'spruce_log', axis='y')
    for yy, r in ((1, 1), (2, 1), (3, 0), (4, 0)):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if r == 0 and dx and dz:
                    continue
                if r == 1 and dx and dz and yy == 1:
                    continue
                A(s, 14 + dx, yy, 20 + dz, 'spruce_leaves', **LEAVES)
    A(s, 14, 5, 20, 'spruce_leaves', **LEAVES)
    for (x, z), f in zip(((13, 14), (15, 17), (13, 17), (15, 20), (13, 20), (12, 22)),
                         ('poppy', 'cornflower', 'cornflower', 'azure_bluet', 'oxeye_daisy', 'azure_bluet')):
        A(s, x, 0, z, f)
    # flowers and bushes round the church, vines on the left buttresses
    for x, z, b in ((0, 2, 'leaves'), (0, 6, 'poppy'), (0, 10, 'azure_bluet'), (0, 13, 'leaves'), (0, 18, 'poppy'),
                    (2, 1, 'leaves'), (10, 1, 'leaves'), (1, 22, 'leaves'), (3, 23, 'poppy')):
        if b == 'leaves':
            A(s, x, 0, z, 'oak_leaves', **LEAVES)
        else:
            A(s, x, 0, z, b)
    for z in (9, 13):
        for yy in (1, 2):
            P(s, NX0 - 2, yy, z, 'vine', east='true', west='false', north='false', south='false', up='false')
    for x, f in ((TX0 - 1, 'cornflower'), (TX1 + 1, 'poppy')):
        A(s, x, 0, TZ0 - 1, f)
    connect(s)
    return s


STRUCTURES = [dict(path='church', name='Church', tier=5, zone='inner', role='religious', group='church',
                   terrain='stepped', door='front', make=church, max_height=20, changes='First version.',
                   description='Needs scaffolding: 20 tall (tower and spire; the nave roof is 11). Stone parish '
                               'church: a front bell tower with belfry openings and a dark oak spire, a rubble-stone '
                               'nave with buttresses and lancets, a lower chancel with altar and choir stalls, a '
                               'timber side porch; L2 the bell and its rope, stained glass, runner, font, pulpit, '
                               'lanterns, banners and a churchyard; L3 carved pinnacles, weathervane, a yew, flowers.')]

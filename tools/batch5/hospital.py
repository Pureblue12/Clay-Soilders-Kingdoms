"""Hospital (tier 6, town style A): the infirmary's big brother, a two-storey cloister hospital round a small herb
courtyard (docs/STRUCTURES.md, "Infirmary / hospital": the tier-6 upgrade, two floors round a courtyard, 14 beds;
variant B, cloister hospital). Same materials and signature as tools/batch4/infirmary.py: a cobblestone base course,
white plaster in a dark oak frame, a spruce roof with dark oak eaves, beds with their heads to the wall under a window,
bedside tables, herb barrels and shelves, and the white banner with the red cross (pattern: decoration step). As a
tier-6 stone-town building its ground storey is stone brick. Terrain: ground_level (layer 0 is base course and floor).

Layout (17 x 17, front z = 0; the building x 1..15, z 1..15, roof overhang at 0 and 16):
- A ring of four wings, each 3 blocks wide inside, round a 5 x 5 courtyard (x 6..10, z 6..10).
- Walls: ground storey (y 1..3) stone brick with dark oak posts; a dark oak floor beam at y 4; upper storey (y 5..7)
  white plaster in a dark oak frame; a dark oak top plate at y 8.
- Roof: one continuous spruce roof round the ring (hips at the outer corners, valleys at the inner ones, a slab ridge,
  dark oak eaves, one block of overhang outside and into the courtyard), a small cross gable over the front door with
  a window, a brick chimney on the right side for the kiln. 12 tall.
- Front wing: the entrance hall in the middle (front door, a door on to the courtyard), the dispensary on the left
  (herb shelves, a worktable, a chest), the healers' room on the right (desk, chair, chest).
- Side wings: the wards, three beds on each side on each floor (heads to the outer wall, a window over each, bedside
  tables between them, an aisle along the courtyard wall, a door on to the courtyard downstairs).
- Back wing: the herb store on the left (barrels, a composter, hay), the hall with the hearth and the staircase in the
  middle, the clay room on the right (where fallen soldiers are rebuilt: a kiln under the chimney, a worktable, chests);
  upstairs two more beds (14 in all).
- Courtyard: a path round it joining the four courtyard doors, a herb bed in the middle with a little pool (water by
  the decoration step), benches in the corners.
Level 1 (KNOWN-OK only): all of the above, beds and windows included.
Level 2 (the hospital at work): brewing stands and cauldrons in the dispensary and the back hall, candles and pots on
the bedside tables, lanterns on chains in the wards, white carpets in the aisles, herbs in the courtyard bed (ferns,
flowers, berry bushes) with lantern posts, lily pads, flower boxes under the front windows, the red-cross banner
over the door and a canopy on lantern brackets, brick shoulders on the chimney, clay in the clay room.
Level 3 (the town's pride): red-cross banners on the front corners and in the courtyard, a bell at the gate, herb
pots on the sills, a herb garden strip along the left side, vines, a bench and lantern posts at the front, candles."""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, LEAVES, connect, shutters
from batch5.roofkit import hip_roof, height_roof, levels, corbels

W, D, H = 17, 17, 12
OX0, OX1, OZ0, OZ1 = 1, 15, 1, 15       # outer walls
IX0, IX1, IZ0, IZ1 = 5, 11, 5, 11       # inner (courtyard) walls
MID = 8
EAVE = 8


def ring_interior(x, z):
    return OX0 < x < OX1 and OZ0 < z < OZ1 and not (IX0 <= x <= IX1 and IZ0 <= z <= IZ1)


def on_outer(x, z):
    return (x in (OX0, OX1) and OZ0 <= z <= OZ1) or (z in (OZ0, OZ1) and OX0 <= x <= OX1)


def on_inner(x, z):
    return (x in (IX0, IX1) and IZ0 <= z <= IZ1) or (z in (IZ0, IZ1) and IX0 <= x <= IX1)


def roof_heights():
    cells = [(x, z) for x in range(0, W) for z in range(0, D) if not (IX0 + 2 <= x <= IX1 - 2 and IZ0 + 2 <= z <= IZ1 - 2)]
    Hm = {c: EAVE + L for c, L in levels(cells).items()}
    for x in range(MID - 3, MID + 4):                  # the front cross gable over the door (ridge along z at MID)
        for z in range(0, 4):
            c = EAVE + min(x - (MID - 3), (MID + 3) - x)
            if c > Hm[(x, z)]:
                Hm[(x, z)] = c
    return Hm


def hospital():
    s = Structure('hospital', (W, H, D), palette='oak_a2')
    Hm = roof_heights()
    outer_posts = {(x, z) for x in (OX0, IX0, IX1, OX1) for z in (OZ0, OZ1)} | \
                  {(x, z) for z in (OZ0, IZ0, IZ1, OZ1) for x in (OX0, OX1)} | {(MID - 1, OZ0), (MID + 1, OZ0)}
    inner_posts = {(x, z) for x in (IX0, IX1) for z in (IZ0, IZ1)}
    beds_z = (6, 8, 10)
    doors = {(MID, OZ0): 'north', (MID, IZ0): 'south', (MID, IZ1): 'north', (IX0, MID): 'east', (IX1, MID): 'west'}
    openings = [(IX0, 3), (IX1, 3), (IX0, 13), (IX1, 13)]          # doorways in the partitions (no door)
    ground_windows = [(3, OZ0), (6, OZ0), (10, OZ0), (13, OZ0)] + [(OX0, z) for z in beds_z] + \
                     [(OX1, z) for z in beds_z] + [(3, OZ1), (13, OZ1)] + \
                     [(IX0, 7), (IX0, 9), (IX1, 7), (IX1, 9), (7, IZ0), (9, IZ0), (7, IZ1), (9, IZ1)]
    upper_windows = [(3, OZ0), (6, OZ0), (MID, OZ0), (10, OZ0), (13, OZ0)] + [(OX0, z) for z in beds_z] + \
                    [(OX1, z) for z in beds_z] + [(3, OZ1), (MID, OZ1), (13, OZ1)] + \
                    [(IX0, 7), (IX0, 9), (IX1, 7), (IX1, 9), (7, IZ0), (MID, IZ0), (9, IZ0), (7, IZ1), (9, IZ1)]
    stairs = [(7, 1, 14), (8, 2, 14), (9, 3, 14), (10, 4, 14)]       # back hall, up along the back wall (east)
    chimney = [(W - 1, y, 13) for y in range(0, H)]

    s.at(1, 'shell')
    for x in range(OX0, OX1 + 1):                                   # base course and floors
        for z in range(OZ0, OZ1 + 1):
            if on_outer(x, z) or on_inner(x, z):
                P(s, x, 0, z, 'cobblestone')
            elif ring_interior(x, z):
                P(s, x, 0, z, 'spruce_planks')
    for y in range(1, 8):                                           # walls of both storeys
        for x in range(OX0, OX1 + 1):
            for z in range(OZ0, OZ1 + 1):
                if not (on_outer(x, z) or on_inner(x, z)):
                    continue
                post = (x, z) in outer_posts or (x, z) in inner_posts
                if y == 4:
                    along_x = z in (OZ0, OZ1, IZ0, IZ1) and not (x in (OX0, OX1, IX0, IX1))
                    P(s, x, y, z, 'dark_oak_log', axis='x' if along_x else 'z')
                elif post:
                    P(s, x, y, z, 'dark_oak_log', axis='y')
                elif y < 4:
                    P(s, x, y, z, 'stone_bricks')
                else:
                    P(s, x, y, z, 'white_terracotta')
    for x in range(OX0, OX1 + 1):                                   # the top plate and the upper floor
        for z in range(OZ0, OZ1 + 1):
            if on_outer(x, z) or on_inner(x, z):
                along_x = z in (OZ0, OZ1, IZ0, IZ1) and not (x in (OX0, OX1, IX0, IX1))
                P(s, x, 8, z, 'dark_oak_log', axis='x' if along_x else 'z')
            elif ring_interior(x, z) and (x, z) not in ((7, 14), (8, 14), (9, 14)):
                P(s, x, 4, z, 'spruce_planks')
    for x in (IX0, IX1):                                            # partitions in the front and back wings
        for z in (2, 3, 4, 12, 13, 14):
            for y in (1, 2, 3, 5, 6, 7):
                P(s, x, y, z, 'spruce_planks' if y < 4 else 'white_terracotta')
    for x, z in openings:
        for y in (1, 2, 5, 6):
            s.remove(x, y, z)
    for (x, z), f in doors.items():
        s.remove(x, 1, z); s.remove(x, 2, z)
        s.door(x, 1, z, f, block='spruce_door', hinge='left')
    for x, z in ground_windows:
        s.remove(x, 2, z)
    for x, z in upper_windows:
        s.remove(x, 6, z)
    # the cross gable's face over the door: plaster round a window
    for x in range(MID - 2, MID + 3):
        for y in range(9, Hm[(x, OZ0)]):
            if (x, y) != (MID, 9):
                P(s, x, y, OZ0, 'white_terracotta')
    # the kiln's chimney outside the right wall: a brick breast, the stack through the overhang
    for y in range(0, 4):
        for z in (12, 13, 14):
            if y < 3 or z == 13:
                P(s, W - 1, y, z, 'bricks')
    for x, y, z in chimney[4:]:
        P(s, x, y, z, 'bricks')
    height_roof(s, Hm, EAVE, skip=chimney)
    corbels(s, Hm, [(x, 0) for x in range(MID - 2, MID + 3)])
    # the front step and the courtyard: a path round it, a herb bed in the middle with a little pool
    P(s, MID, 0, 0, 'cobblestone_stairs', facing='south', **STAIR)
    for x in range(IX0 + 1, IX1):
        for z in range(IZ0 + 1, IZ1):
            ring = x in (IX0 + 1, IX1 - 1) or z in (IZ0 + 1, IZ1 - 1)
            if ring:
                P(s, x, 0, z, 'stone_bricks' if (x + z) % 2 else 'cobblestone')
            elif (x, z) != (MID, MID):
                P(s, x, 0, z, 'coarse_dirt')
    s.decorate(MID, 0, MID, 'water', note='a little pool in the herb bed')

    s.at(1, 'fittings')
    for x, z in ground_windows:
        side = x in (OX0, OX1, IX0, IX1) and z not in (OZ0, OZ1, IZ0, IZ1)
        P(s, x, 2, z, 'glass_pane', north=side, south=side, east=not side, west=not side, waterlogged='false')
    for x, z in upper_windows:
        side = x in (OX0, OX1, IX0, IX1) and z not in (OZ0, OZ1, IZ0, IZ1)
        P(s, x, 6, z, 'glass_pane', north=side, south=side, east=not side, west=not side, waterlogged='false')
    P(s, MID, 9, OZ0, 'glass_pane', east=True, west=True, north=False, south=False, waterlogged='false')
    for x, y, z in stairs:
        P(s, x, y, z, 'spruce_stairs', facing='east', **STAIR)
    # the wards: three beds a side on each floor, heads to the outer wall, bedside tables between
    for y in (1, 5):
        for z in beds_z:
            s.bed(OX0 + 2, y, z, 'west')
            s.bed(OX1 - 2, y, z, 'east')
        for z in (7, 9):
            P(s, OX0 + 1, y, z, 'spruce_slab', **SLAB_TOP)
            P(s, OX1 - 1, y, z, 'spruce_slab', **SLAB_TOP)
    s.bed(12, 5, 13, 'north')                                        # the back wing upstairs: two more beds
    s.bed(14, 5, 13, 'north')
    P(s, 13, 5, 12, 'spruce_slab', **SLAB_TOP)
    # the dispensary (front left): herb shelves, a worktable, a chest, a barrel
    P(s, 2, 1, 2, 'chest', facing='south', type='single', waterlogged='false')
    P(s, 3, 1, 2, 'crafting_table')
    P(s, 4, 1, 2, 'barrel', facing='up', open='false')
    for x in (2, 3, 4):
        P(s, x, 3, 2, 'oak_trapdoor', facing='south', **SHELF)
    # the healers' room (front right): desk, chair, chest
    P(s, 13, 1, 2, 'spruce_slab', **SLAB_TOP)
    P(s, 13, 1, 3, 'spruce_stairs', facing='south', **STAIR)
    P(s, 14, 1, 2, 'chest', facing='south', type='single', waterlogged='false')
    # the entrance hall: a bench each side of the door
    P(s, 6, 1, 2, 'spruce_stairs', facing='west', **STAIR)
    P(s, 10, 1, 2, 'spruce_stairs', facing='east', **STAIR)
    # the herb store (back left): barrels, a composter, hay
    P(s, 2, 1, 14, 'barrel', facing='up', open='false')
    P(s, 3, 1, 14, 'barrel', facing='up', open='false')
    P(s, 2, 1, 13, 'composter', level='3')
    P(s, 4, 1, 14, 'hay_block', axis='y')
    P(s, 2, 1, 12, 'chest', facing='south', type='single', waterlogged='false')
    # the back hall: the hearth (hot water) by the courtyard door, a table
    P(s, 10, 1, 12, 'furnace', facing='north', lit='false')
    P(s, 6, 1, 12, 'barrel', facing='up', open='false')
    # the clay room (back right): the kiln under the chimney, a worktable, chests
    P(s, 14, 1, 13, 'smoker', facing='west', lit='false')
    P(s, 14, 1, 12, 'crafting_table')
    P(s, 14, 1, 14, 'chest', facing='south', type='single', waterlogged='false')
    P(s, 12, 1, 14, 'chest', facing='south', type='single', waterlogged='false')
    # the healers' quarters upstairs at the front: chests and a table
    for x in (6, 10):
        P(s, x, 5, 2, 'chest', facing='south', type='single', waterlogged='false')
    P(s, MID, 5, 2, 'spruce_slab', **SLAB_TOP)
    # courtyard benches in the corners
    for x, z, f in ((IX0 + 1, IZ0 + 1, 'west'), (IX1 - 1, IZ0 + 1, 'east'), (IX0 + 1, IZ1 - 1, 'west'),
                    (IX1 - 1, IZ1 - 1, 'east')):
        P(s, x, 1, z, 'spruce_stairs', facing=f, **STAIR)

    s.at(2, 'flourish2')
    # brewing stands and cauldrons
    P(s, 3, 2, 2, 'brewing_stand', has_bottle_0='true', has_bottle_1='false', has_bottle_2='true')
    P(s, 3, 1, 3, 'cauldron')
    P(s, 11 - 1, 1, 13, 'cauldron')
    P(s, 6, 2, 12, 'brewing_stand', has_bottle_0='true', has_bottle_1='false', has_bottle_2='true')
    # candles and pots on the bedside tables, white carpets in the aisles, lanterns on chains
    for y in (2, 6):
        for z, k in ((7, 'candle'), (9, 'pot')):
            for x in (OX0 + 1, OX1 - 1):
                if k == 'candle':
                    P(s, x, y, z, 'candle', candles='2', lit='true', waterlogged='false')
                else:
                    P(s, x, y, z, 'flower_pot'); s.decorate(x, y, z, 'potted_plant', plant='fern')
        for z in range(6, 11):
            P(s, OX0 + 3, y - 1, z, 'white_carpet'); P(s, OX1 - 3, y - 1, z, 'white_carpet')
    for x in (3, 13):
        for z in (7, 9):
            P(s, x, 3, z, 'lantern', **HANG)
    # the courtyard herbs and lantern posts
    herbs = {(7, 7): 'fern', (8, 7): 'allium', (9, 7): 'sweet_berry_bush', (7, 8): 'cornflower', (9, 8): 'fern',
             (7, 9): 'sweet_berry_bush', (8, 9): 'allium', (9, 9): 'oxeye_daisy'}
    for (x, z), f in herbs.items():
        if f == 'sweet_berry_bush':
            P(s, x, 1, z, f, age='2')
        else:
            P(s, x, 1, z, f)
    s.decorate(MID, 1, MID, 'lily_pad')
    # flower boxes under the front ground windows, the red-cross banner and a canopy over the door
    for x, f in ((3, 'poppy'), (6, 'cornflower'), (10, 'allium'), (13, 'poppy')):
        P(s, x, 0, OZ0 - 1, 'coarse_dirt'); P(s, x, 1, OZ0 - 1, f)     # a planter at the wall's foot
    for x in (MID - 1, MID, MID + 1):
        P(s, x, 3, OZ0 - 1, 'spruce_slab', **SLAB)
    for x in (MID - 1, MID + 1):
        P(s, x, 2, OZ0 - 1, 'lantern', **HANG)
    P(s, MID, 7, OZ0 - 1, 'white_wall_banner', facing='north')
    s.decorate(MID, 7, OZ0 - 1, 'banner_pattern', banner='white_wall_banner', note='a red cross (the hospital sign)')
    # shutters on the outer windows of the sides and the back (both storeys)
    for y, wins in ((2, ground_windows), (6, upper_windows)):
        for x, z in wins:
            if z == OZ0 or not on_outer(x, z):
                continue
            shutters(s, x, y, z, 'west' if x == OX0 else 'east' if x == OX1 else 'south')
    # clay in the clay room
    P(s, 13, 1, 13, 'clay')
    P(s, 13, 1, 14, 'clay')

    s.at(3, 'flourish3')
    for x in (OX0, OX1):                                             # red-cross banners on the front corners
        P(s, x, 3, OZ0 - 1, 'white_wall_banner', facing='north')
        s.decorate(x, 3, OZ0 - 1, 'banner_pattern', banner='white_wall_banner', note='a red cross')
    # a herb garden strip along the left side
    for z in range(3, 14):
        P(s, 0, 0, z, 'coarse_dirt')
        f = ('fern', 'allium', 'sweet_berry_bush', 'cornflower', 'fern', 'oxeye_daisy')[z % 6]
        if f == 'sweet_berry_bush':
            P(s, 0, 1, z, f, age='2')
        else:
            P(s, 0, 1, z, f)
    # a log pile and barrels against the back wall
    for x in (5, 6):
        for y in (0, 1):
            P(s, x, y, D - 1, 'spruce_log', axis='z')
    P(s, 10, 0, D - 1, 'barrel', facing='up', open='false')
    P(s, 11, 0, D - 1, 'barrel', facing='up', open='false')
    P(s, 10, 1, D - 1, 'barrel', facing='up', open='false')
    # a bell at the gate: on a post by the door
    for y in (1, 2, 3):
        P(s, MID + 3, y, 0, 'spruce_fence')
    P(s, MID + 3, 4, 0, 'lantern', **STAND)
    for y in (1, 2, 3):
        P(s, MID - 3, y, 0, 'spruce_fence')
    P(s, MID - 3, 4, 0, 'lantern', **STAND)
    return connect(s)


STRUCTURES = [dict(path='hospital', name='Hospital', tier=6, zone='inner', role='health', group='infirmary',
                   terrain='ground_level', door='front', make=hospital, max_height=12, changes='First version.',
                   description='Needs scaffolding: 12 tall.')]

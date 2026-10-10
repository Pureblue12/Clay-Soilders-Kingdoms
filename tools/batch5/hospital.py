"""Hospital (tier 6, town style A): the infirmary's big brother, a two-storey cloister hospital round a small herb
courtyard (docs/STRUCTURES.md, "Infirmary / hospital": the tier-6 upgrade, two floors round a courtyard, 14 beds;
variant B, cloister hospital). Same materials and signature as tools/batch4/infirmary.py: a cobblestone base course,
white plaster in a dark oak frame, a spruce roof with dark oak eaves, beds with their heads to the wall under a window,
bedside tables, herb barrels and shelves, and the red cross (red wool on a white wool field, as the infirmary v4). As a
tier-6 stone-town building its ground storey is stone brick. Terrain: ground_level (layer 0 is base course and floor).

Layout (17 wide x 19 deep, front z = 0): a front yard at z 0..1, the roof overhang at z 2 and 18 (x 0 and 16), the
building x 1..15, z 3..17. The plan is drawn in building coordinates (front wall at z = 1) and moved back two rows
at the end (shift_z); the yard, porch and front garden are added after that in final coordinates.
- A ring of four wings, each 3 blocks wide inside, round a 5 x 5 courtyard.
- Walls: ground storey (y 1..3) stone brick with dark oak posts; a dark oak floor beam at y 4; upper storey (y 5..7)
  white plaster in a dark oak frame; a dark oak top plate at y 8. Outer windows are two tall, with shutters.
- The red cross on the front of the upper storey over the door (x 7..9, y 5..7), framed by the floor beam and the
  top plate.
- Roof: one continuous spruce roof round the ring (hips at the outer corners, valleys at the inner ones, a slab
  ridge), its eaves, hips, valleys and the front gable's verges in dark oak (the second colour); a front gable over
  the door (9 wide, plaster face with a window); a brick chimney on the right side for the kiln. 13 tall.
- Front wing: the entrance hall in the middle (front door, a door on to the courtyard), the dispensary on the left
  (herb shelves, a worktable, a chest), the healers' room on the right (desk, chair, chest).
- Side wings: the wards, three beds on each side on each floor (heads to the outer wall, a window over each, bedside
  tables between them, an aisle along the courtyard wall, a door on to the courtyard downstairs).
- Back wing: the herb store on the left (barrels, a composter, hay), the hall with the hearth and the staircase in the
  middle, the clay room on the right (where fallen soldiers are rebuilt: a kiln under the chimney, a worktable, chests);
  upstairs two more beds (14 in all).
- Courtyard: a path round it joining the four courtyard doors, a herb bed in the middle with a little pool (water by
  the decoration step), benches in the corners.
Level 1 (KNOWN-OK only): all of the above, beds and windows included; the red cross; front planters with poppies
(trapdoor fronts) and two bushes at the front corners; a stone path to the door.
Level 2 (the hospital at work): a covered entrance porch (a dark oak and spruce lean-to on posts, hanging lanterns)
with two more planters under it, red banners either side of the cross, brewing stands and cauldrons, candles and pots
on the bedside tables, lanterns in the wards, white carpets in the aisles, courtyard herbs, lily pads, shutters on the
side and back windows, clay in the clay room.
Level 3 (the town's pride): a front herb garden either side of the porch with lantern posts, red banners on the front
corners, a herb garden strip along the left side, a log pile and barrels at the back."""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, SLAB_TOP, OPEN, SHELF, HANG, STAND, LEAVES, connect, shutters
from batch5.roofkit import levels, stair_for, corbels

W, D, H = 17, 17, 13                    # D: the building's plan; the finished structure is D + YARD deep
YARD = 2                                # rows of front yard added in front (shift_z)
OX0, OX1, OZ0, OZ1 = 1, 15, 1, 15       # outer walls (plan coordinates)
IX0, IX1, IZ0, IZ1 = 5, 11, 5, 11       # inner (courtyard) walls
MID = 8
EAVE = 8
GX0, GX1 = 4, 12                        # the front gable's roof (x), peak at MID


def ring_interior(x, z):
    return OX0 < x < OX1 and OZ0 < z < OZ1 and not (IX0 <= x <= IX1 and IZ0 <= z <= IZ1)


def on_outer(x, z):
    return (x in (OX0, OX1) and OZ0 <= z <= OZ1) or (z in (OZ0, OZ1) and OX0 <= x <= OX1)


def on_inner(x, z):
    return (x in (IX0, IX1) and IZ0 <= z <= IZ1) or (z in (IZ0, IZ1) and IX0 <= x <= IX1)


def roof_heights():
    cells = [(x, z) for x in range(0, W) for z in range(0, D) if not (IX0 + 2 <= x <= IX1 - 2 and IZ0 + 2 <= z <= IZ1 - 2)]
    Hm = {c: EAVE + L for c, L in levels(cells).items()}
    for x in range(GX0, GX1 + 1):                      # the front gable (ridge along z at MID, hipped at the back)
        for z in range(0, 4):
            c = EAVE + min(x - GX0, GX1 - x, 5 - z)
            if c > Hm[(x, z)]:
                Hm[(x, z)] = c
    return Hm


def roof(s, Hm, skip=()):
    """The roof over the height map: spruce stairs, dark oak at the eaves, hips, valleys and the gable's verges."""
    skip = set(skip)
    for (x, z), y in sorted(Hm.items()):
        if (x, y, z) in skip:
            continue
        st = stair_for(Hm, x, z)
        if st is None:
            A(s, x, y, z, 'spruce_slab', **SLAB)
            continue
        dark = y == EAVE or st[1] != 'straight' or (z == 0 and GX0 <= x <= GX1)
        A(s, x, y, z, 'dark_oak_stairs' if dark else 'spruce_stairs', facing=st[0], half='bottom', shape=st[1],
          waterlogged='false')


def shift_z(s, dz):
    """Moves everything dz rows back (adds a front yard)."""
    s.blocks = {(x, y, z + dz): v for (x, y, z), v in s.blocks.items()}
    s.layer_of = {(x, y, z + dz): v for (x, y, z), v in s.layer_of.items()}
    for lv in s.decorations.values():
        for d in lv:
            d['pos'][2] += dz
    s.size = (s.size[0], s.size[1], s.size[2] + dz)


def pane(s, x, y, z, side):
    P(s, x, y, z, 'glass_pane', north=side, south=side, east=not side, west=not side, waterlogged='false')


def hospital():
    s = Structure('hospital', (W, H, D), palette='oak_a2')
    Hm = roof_heights()
    outer_posts = {(x, z) for x in (OX0, IX0, IX1, OX1) for z in (OZ0, OZ1)} | \
                  {(x, z) for z in (OZ0, IZ0, IZ1, OZ1) for x in (OX0, OX1)} | {(MID - 1, OZ0), (MID + 1, OZ0)}
    inner_posts = {(x, z) for x in (IX0, IX1) for z in (IZ0, IZ1)}
    beds_z = (6, 8, 10)
    doors = {(MID, OZ0): 'north', (MID, IZ0): 'south', (MID, IZ1): 'north', (IX0, MID): 'east', (IX1, MID): 'west'}
    openings = [(IX0, 3), (IX1, 3), (IX0, 13), (IX1, 13)]          # doorways in the partitions (no door)
    front_windows = [(3, OZ0), (6, OZ0), (10, OZ0), (13, OZ0)]
    side_windows = [(OX0, z) for z in beds_z] + [(OX1, z) for z in beds_z]
    # outer windows are two tall (y 2..3 and y 6..7); courtyard windows one (y 2 and y 6)
    outer_ground = front_windows + side_windows + [(3, OZ1), (13, OZ1)]
    outer_upper = front_windows + side_windows + [(3, OZ1), (MID, OZ1), (13, OZ1)]
    inner_ground = [(IX0, 7), (IX0, 9), (IX1, 7), (IX1, 9), (7, IZ0), (9, IZ0), (7, IZ1), (9, IZ1)]
    inner_upper = inner_ground + [(MID, IZ0)]
    cross_red = [(MID, 5), (MID, 6), (MID, 7), (MID - 1, 6), (MID + 1, 6)]
    cross_white = [(MID - 1, 5), (MID + 1, 5), (MID - 1, 7), (MID + 1, 7)]
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
                elif z == OZ0 and (x, y) in cross_red:
                    P(s, x, y, z, 'red_wool')                       # the red cross over the door
                elif z == OZ0 and (x, y) in cross_white:
                    P(s, x, y, z, 'white_wool')
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
    for x, z in outer_ground:
        s.remove(x, 2, z); s.remove(x, 3, z)
    for x, z in outer_upper:
        s.remove(x, 6, z); s.remove(x, 7, z)
    for x, z in inner_ground:
        s.remove(x, 2, z)
    for x, z in inner_upper:
        s.remove(x, 6, z)
    # the front gable's face over the cross: plaster round a window, a dark oak king post under it
    for x in range(GX0 + 1, GX1):
        for y in range(9, Hm[(x, 0)]):
            if (x, y) == (MID, 9):
                P(s, x, y, OZ0, 'dark_oak_log', axis='y')
            elif (x, y) != (MID, 10):
                P(s, x, y, OZ0, 'white_terracotta')
    # the kiln's chimney outside the right wall: a brick breast, the stack through the overhang
    for y in range(0, 4):
        for z in (12, 13, 14):
            if y < 3 or z == 13:
                P(s, W - 1, y, z, 'bricks')
    for x, y, z in chimney[4:]:
        P(s, x, y, z, 'bricks')
    roof(s, Hm, skip=chimney)
    corbels(s, Hm, [(x, 0) for x in range(GX0, GX1 + 1)])
    # the courtyard: a path round it, a herb bed in the middle with a little pool
    for x in range(IX0 + 1, IX1):
        for z in range(IZ0 + 1, IZ1):
            ring = x in (IX0 + 1, IX1 - 1) or z in (IZ0 + 1, IZ1 - 1)
            if ring:
                P(s, x, 0, z, 'stone_bricks' if (x + z) % 2 else 'cobblestone')
            elif (x, z) != (MID, MID):
                P(s, x, 0, z, 'coarse_dirt')
    s.decorate(MID, 0, MID, 'water', note='a little pool in the herb bed')

    s.at(1, 'fittings')
    for wins, ys in ((outer_ground, (2, 3)), (inner_ground, (2,)), (outer_upper, (6, 7)), (inner_upper, (6,))):
        for x, z in wins:
            side = x in (OX0, OX1, IX0, IX1) and z not in (OZ0, OZ1, IZ0, IZ1)
            for y in ys:
                pane(s, x, y, z, side)
    pane(s, MID, 10, OZ0, False)
    for x in (3, 13):                                                # shutters on the front's outer windows
        for y in (2, 3, 6, 7):
            shutters(s, x, y, OZ0, 'north')
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
    for x in (2, 4):
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
    # the back hall: the hearth (hot water) by the courtyard door, a barrel
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
    P(s, 10, 1, 13, 'cauldron')
    P(s, 6, 2, 12, 'brewing_stand', has_bottle_0='true', has_bottle_1='false', has_bottle_2='true')
    # candles and pots on the bedside tables, white carpets in the aisles, lanterns in the wards
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
    # the courtyard herbs
    herbs = {(7, 7): 'fern', (8, 7): 'allium', (9, 7): 'sweet_berry_bush', (7, 8): 'cornflower', (9, 8): 'fern',
             (7, 9): 'sweet_berry_bush', (8, 9): 'allium', (9, 9): 'oxeye_daisy'}
    for (x, z), f in herbs.items():
        if f == 'sweet_berry_bush':
            P(s, x, 1, z, f, age='2')
        else:
            P(s, x, 1, z, f)
    s.decorate(MID, 1, MID, 'lily_pad')
    # shutters on the outer windows of the sides and the back (both storeys)
    for ys, wins in (((2, 3), outer_ground), ((6, 7), outer_upper)):
        for x, z in wins:
            if z == OZ0:
                continue
            for y in ys:
                shutters(s, x, y, z, 'west' if x == OX0 else 'east' if x == OX1 else 'south')
    # clay in the clay room
    P(s, 13, 1, 13, 'clay')
    P(s, 13, 1, 14, 'clay')

    s.at(3, 'flourish3')
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

    # ---- the front yard (final coordinates: yard z 0..1, overhang z 2, front wall z 3)
    shift_z(s, YARD)
    FW = OZ0 + YARD

    s.at(1, 'fittings')
    P(s, MID, 0, FW - 1, 'cobblestone_stairs', facing='south', **STAIR)     # the step and a path to the yard's edge
    for z in range(0, FW - 1):
        P(s, MID, 0, z, 'stone_bricks')
    for x, f in ((3, 'poppy'), (13, 'poppy')):                              # planters under the outer front windows
        P(s, x, 0, FW - 1, 'coarse_dirt')
        P(s, x, 0, FW - 2, 'spruce_trapdoor', facing='north', **OPEN)
        P(s, x, 1, FW - 1, f)
    for x in (0, W - 1):                                                    # bushes at the front corners
        A(s, x, 0, FW - 1, 'oak_leaves', **LEAVES)

    s.at(2, 'flourish2')
    # the covered entrance porch: a lean-to on two dark oak posts, x 5..11, z 0..2
    for x in (5, 11):
        P(s, x, 0, 0, 'cobblestone')
        for y in (1, 2):
            P(s, x, y, 0, 'dark_oak_log', axis='y')
    for x in range(5, 12):
        P(s, x, 3, 0, 'dark_oak_stairs', facing='south', **STAIR)
        P(s, x, 3, 1, 'dark_oak_planks')
        P(s, x, 3, 2, 'dark_oak_planks')
        P(s, x, 4, 1, 'spruce_stairs', facing='south', **STAIR)
        P(s, x, 4, 2, 'spruce_planks')
    for x in (MID - 1, MID + 1):
        P(s, x, 2, 1, 'lantern', **HANG)
    P(s, 6, 2, 0, 'dark_oak_stairs', facing='west', **TOP)                       # brackets by the posts
    P(s, 10, 2, 0, 'dark_oak_stairs', facing='east', **TOP)
    for x, f in ((6, 'cornflower'), (10, 'allium')):                        # two more planters under the porch
        P(s, x, 0, FW - 1, 'coarse_dirt')
        P(s, x, 0, FW - 2, 'spruce_trapdoor', facing='north', **OPEN)
        P(s, x, 1, FW - 1, f)
    for x in (IX0, IX1):                                                    # red banners either side of the cross
        P(s, x, 7, FW - 1, 'red_wall_banner', facing='north')

    s.at(3, 'flourish3')
    for x in (OX0, OX1):                                                    # red banners on the front corners
        P(s, x, 3, FW - 1, 'red_wall_banner', facing='north')
    # the front herb garden either side of the porch: beds with trapdoor fronts, lantern posts by the porch
    for xs, plants in (((0, 1, 2), ('fern', 'oxeye_daisy', 'sweet_berry_bush')),
                       ((14, 15, 16), ('sweet_berry_bush', 'azure_bluet', 'fern'))):
        for x, f in zip(xs, plants):
            P(s, x, 0, 1, 'coarse_dirt')
            P(s, x, 0, 0, 'spruce_trapdoor', facing='north', **OPEN)
            if f == 'sweet_berry_bush':
                P(s, x, 1, 1, f, age='2')
            else:
                P(s, x, 1, 1, f)
    for x in (4, 12):
        P(s, x, 0, 0, 'cobblestone')
        P(s, x, 1, 0, 'spruce_fence')
        P(s, x, 2, 0, 'lantern', **STAND)
    return connect(s)


STRUCTURES = [dict(path='hospital', name='Hospital', tier=6, zone='inner', role='health', group='infirmary',
                   terrain='ground_level', door='front', make=hospital, max_height=13,
                   changes='QC v1: the red cross now reads on every level (red wool on a white wool field on the '
                           'upper storey over the door, as the infirmary v4); the plain white banners are gone (red '
                           'banners from L2). L1: two-tall outer windows with front shutters, poppy planters with '
                           'trapdoor fronts and corner bushes, a proper 9-wide front gable with a plaster face and '
                           'window, dark oak hips, valleys and verges on the spruce roof, a front yard with a path '
                           '(the building moved back 2 rows). L2: a covered entrance porch with hanging lanterns and '
                           'benches, two more planters with trapdoor fronts. L3: a front herb garden either side of '
                           'the porch with lantern posts.',
                   description='Needs scaffolding: 13 tall. Over budget: 1,654 blocks (L1 1,447) vs the infirmary\'s 285 (no '
                               'hospital budget in the brief; under the landmark 1,800). The infirmary\'s tier-6 big '
                               'brother, a two-storey cloister hospital round a 5 x 5 herb courtyard: stone brick '
                               'ground storey, white plaster in a dark oak frame above with the red wool cross over '
                               'the door, one spruce roof round the ring (dark oak hips and eaves) with a front gable '
                               'and a kiln chimney. 14 beds, dispensary, healers\' room, herb store, clay room '
                               '(rebuilding fallen soldiers), a staircase; courtyard pool by the decoration step. L2 '
                               'a covered entrance porch, red banners, brewing stands, cauldrons, candles, lanterns, '
                               'carpets, courtyard herbs, planters, shutters, clay; L3 a front herb garden with '
                               'lantern posts, corner banners, a herb strip on the left, a log pile, barrels.')]

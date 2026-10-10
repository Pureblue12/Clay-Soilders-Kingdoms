"""Cottage, revised from Carlton's notes (batch 1 -> revise 1): explicit log axes, real window panes, no dormer or
chimney campfire on the roof, and much more outside detail (porch, flower boxes, shutters, woodpile, bench)."""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
TOP = dict(half='top', shape='straight', waterlogged='false')


def pane(axis):
    """A glass pane joined along the wall (x: front/back wall, z: side wall)."""
    if axis == 'x':
        return dict(east='true', west='true', north='false', south='false', waterlogged='false')
    return dict(north='true', south='true', east='false', west='false', waterlogged='false')


def fence(**sides):
    return {d: str(sides.get(d, False)).lower() for d in ('north', 'south', 'east', 'west')} | {'waterlogged': 'false'}


def trapdoor(facing, half='bottom', open_=False):
    return dict(facing=facing, half=half, open=str(open_).lower(), powered='false', waterlogged='false')


def cottage():
    """Cottage, 5 x 7 walls, 3 high: cobblestone base course, oak plank walls between spruce log posts (corners and
    the middle of the long sides), a stripped spruce top plate the spruce gable roof sits on, one block of overhang at the
    eaves, plaster gables jettied out one block over the front and back walls (flush with the roof edge, so no dark
    recess reads as a hole), a window in the front gable, a king post in the back one, a brick chimney stack.

    L1 = shell and fittings (only KNOWN-OK blocks); L2 = window boxes, shutters, corbels under eaves and jetties,
    lantern posts, stepping stone, rain barrel, hanging lantern inside; L3 = woodpile with a chopping block, bench by
    the side wall, composter and crates at the back, a low front fence, moss and indoor clutter."""
    W, H, D = 9, 9, 10
    s = Structure('cottage', (W, H, D), palette='oak_a2')
    x0, x1, z0, z1 = 2, 6, 2, 8          # walls
    mid = 4
    top = 3                               # last wall layer
    s.at(1, 'shell')
    # base course and floor (y 0 rests on the ground)
    s.ring(x0, z0, x1, z1, 0, 'base')
    s.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, 'floor')
    s.put(mid, 0, z0 - 1, 'stone_slab', type='bottom', waterlogged='false')       # door step
    # walls: posts at the corners and in the middle of the long sides, planks between
    posts = {(x, z) for x in (x0, x1) for z in (z0, (z0 + z1) // 2, z1)}
    for y in range(1, top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    if (x, z) in posts:
                        s.put(x, y, z, 'shed_post', axis='y')                      # spruce log, upright
                    else:
                        s.put(x, y, z, 'wall')
    # openings: door in the middle of the front, a window either side of it, two windows on each long side
    s.remove(mid, 1, z0); s.remove(mid, 2, z0)
    s.door(mid, 1, z0, 'north')
    s.remove(mid, 3, z0); s.put(mid, 3, z0, 'stripped_spruce_log', axis='x')       # lintel over the door
    front_windows = [(mid - 1, 2, z0), (mid + 1, 2, z0)]
    side_windows = [(x0, 2, 3), (x0, 2, 4), (x0, 2, 6), (x1, 2, 3), (x1, 2, 4), (x1, 2, 6)]
    back_windows = [(mid - 1, 2, z1)]
    for x, y, z in front_windows + side_windows + back_windows:
        s.remove(x, y, z)
    # chimney: the back wall right of the middle is brick, the stack stands outside the back gable
    cx = mid + 1
    for y in range(1, top + 1):
        s.remove(cx, y, z1); s.put(cx, y, z1, 'chimney')
    # top plate all round (the roof sits on it, so the walls stay visible)
    for x in range(x0, x1 + 1):
        s.put(x, top + 1, z0, 'stripped_spruce_log', axis='x'); s.put(x, top + 1, z1, 'stripped_spruce_log', axis='x')
    for z in range(z0 + 1, z1):
        s.put(x0, top + 1, z, 'stripped_spruce_log', axis='z'); s.put(x1, top + 1, z, 'stripped_spruce_log', axis='z')
    # gable roof, ridge along z, one block of overhang all round (x 1..7, z 1..9)
    for z in range(z0 - 1, z1 + 2):
        for i in range(3):
            s.put(x0 - 1 + i, top + 1 + i, z, 'roof_stairs', facing='east', **STAIR)
            s.put(x1 + 1 - i, top + 1 + i, z, 'roof_stairs', facing='west', **STAIR)
        s.put(mid, top + 4, z, 'roof_slab', type='bottom', waterlogged='false')         # ridge
    # gables jettied out over the front and back walls, flush with the roof's edge (no recess under the overhang):
    # a beam on the wall line below, plaster panels, a window in the front one, a king post in the back one
    for z in (z0 - 1, z1 + 1):
        for x in range(x0, x1 + 1):
            s.put(x, top + 1, z, 'stripped_spruce_log', axis='x')
        for x in (mid - 1, mid + 1):
            s.put(x, top + 2, z, 'white_terracotta')
        s.put(mid, top + 2, z, 'window', **pane('x')) if z < z0 else s.put(mid, top + 2, z, 'stripped_spruce_log', axis='y')
        s.put(mid, top + 3, z, 'plank2')
    for y in range(0, top + 6):                                                       # chimney stack, 1 above the ridge
        if (cx, y, z1 + 1) in s.blocks:
            s.remove(cx, y, z1 + 1)
        s.put(cx, y, z1 + 1, 'chimney')
    s.at(1, 'fittings')
    for x, y, z in front_windows + back_windows:
        s.put(x, y, z, 'window', **pane('x'))
    for x, y, z in side_windows:
        s.put(x, y, z, 'window', **pane('z'))
    # furniture: bed and chest at the back, hearth by the chimney, table and chair, a barrel and a workbench
    s.bed(x0 + 1, 1, z1 - 2, 'south')
    s.put(mid, 1, z1 - 1, 'chest', facing='north', type='single', waterlogged='false')
    s.put(cx, 1, z1 - 1, 'hearth', facing='north', lit='false')
    s.put(x1 - 1, 1, z0 + 2, 'fence', **fence())                                       # table: fence + plate
    s.put(x1 - 1, 2, z0 + 2, 'plate', powered='false')
    s.put(x1 - 1, 1, z0 + 1, 'chair', facing='north', **STAIR)                         # chair facing the table
    s.put(x0 + 1, 1, z0 + 1, 'barrel', facing='up', open='false')
    s.put(x0 + 1, 1, z0 + 2, 'crafting_table')
    for z in (z0 + 2, z0 + 3, z0 + 4):
        s.put(mid, 1, z, 'rug')
    s.put(mid - 1, 3, z1 - 1, 'wall_torch', facing='north')                           # inside, on the back wall
    s.put(mid + 1, 3, z0 + 1, 'wall_torch', facing='south')                           # inside, on the front wall
    s.put(mid - 1, 3, z0 - 1, 'wall_torch', facing='north')                           # outside, either side of the door
    s.put(mid + 1, 3, z0 - 1, 'wall_torch', facing='north')

    s.at(2, 'flourish2')
    # window boxes under the front and long-side windows: coarse dirt (flowers need it) in a rim of open trapdoors
    for x in (mid - 1, mid + 1):
        s.put(x, 1, z0 - 1, 'soil')
        s.put(x, 2, z0 - 1, 'poppy' if x < mid else 'dandelion')
        s.put(x, 1, z0 - 2, 'trapdoor', **trapdoor('north', open_=True))
    s.put(x0, 1, z0 - 1, 'trapdoor', **trapdoor('west', open_=True))
    s.put(x1, 1, z0 - 1, 'trapdoor', **trapdoor('east', open_=True))
    for x, out, f in ((x0, x0 - 1, 'west'), (x1, x1 + 1, 'east')):
        for z, flower in ((3, 'poppy'), (4, 'azure_bluet')):
            s.put(out, 1, z, 'soil')
            s.put(out, 2, z, flower)
            s.put(out + (out - x), 1, z, 'trapdoor', **trapdoor(f, open_=True))
        s.put(out, 1, 2, 'trapdoor', **trapdoor('north', open_=True))
        s.put(out, 1, 5, 'trapdoor', **trapdoor('south', open_=True))
        # shutters on the posts either side of the double window (open trapdoors flat on the wall)
        s.put(out, 2, z0, 'trapdoor', **trapdoor(f, open_=True))
        s.put(out, 2, (z0 + z1) // 2, 'trapdoor', **trapdoor(f, open_=True))
        # corbels under the eave at each post
        for z in (z0, (z0 + z1) // 2, z1):
            s.put(out, top, z, 'roof_stairs', facing=f, **TOP)
    s.put(x1 + 1, 2, 7, 'trapdoor', **trapdoor('east', open_=True))                   # shutter of the single window
    # gable trim: upside-down stairs under the gable overhangs, at the eave corners
    for z in (z0 - 1, z1 + 1):
        s.add(x0 - 1, top, z, 'roof_stairs', facing='west', **TOP)
        s.add(x1 + 1, top, z, 'roof_stairs', facing='east', **TOP)
    for x in (x0, x1):                                                                # corbels under the jetty beams
        s.put(x, top, z0 - 1, 'roof_stairs', facing='south', **TOP)
        s.add(x, top, z1 + 1, 'roof_stairs', facing='north', **TOP)
    # lantern posts at the front corners, stepping stones up to the door
    for x in (x0 - 1, x1 + 1):
        s.put(x, 0, 0, 'fence', **fence()); s.put(x, 1, 0, 'lantern', hanging='false', waterlogged='false')
    s.put(mid, 0, 0, 'stone_slab', type='bottom', waterlogged='false')
    # rain barrel at the back right corner, under the eave
    s.put(x1 + 1, 0, z1, 'barrel', facing='up', open='false')
    # inside: a lantern on a chain from the ridge, carpet by the bed, a shelf over the workbench
    s.put(mid, top + 3, 5, 'chain', axis='y')
    s.put(mid, top + 2, 5, 'chain', axis='y')
    s.put(mid, top + 1, 5, 'lantern', hanging='true', waterlogged='false')
    s.put(x0 + 1, 1, z1 - 3, 'brown_carpet')
    s.put(x0 + 1, 3, z0 + 2, 'shelf', **trapdoor('east', 'top'))

    s.at(3, 'flourish3')
    # woodpile under the left eave (logs lying along the wall) and a chopping block beside it
    for z in (6, 7):
        s.put(x0 - 1, 0, z, 'shed_post', axis='z'); s.put(x0 - 1, 1, z, 'shed_post', axis='z')
    s.put(x0 - 1, 0, 8, 'shed_post', axis='z')
    s.put(0, 0, 7, 'stripped_spruce_log', axis='y')
    s.put(0, 0, 6, 'oak_button', face='floor', facing='north', powered='false')         # a wedge by the block
    # bench against the right wall under the back window, a trapdoor armrest, the rain barrel at its other end
    s.put(x1 + 1, 0, 6, 'chair', facing='west', **STAIR)
    s.put(x1 + 1, 0, 7, 'chair', facing='west', **STAIR)
    s.put(x1 + 1, 0, 5, 'trapdoor', **trapdoor('north', open_=True))
    # composter and a stack of crates behind the house, left of the chimney
    s.put(mid - 1, 0, z1 + 1, 'composter', level='3')
    s.put(x0, 0, z1 + 1, 'barrel', facing='up', open='false')
    s.put(x0, 1, z1 + 1, 'flower_pot'); s.decorate(x0, 1, z1 + 1, 'potted_plant', plant='fern')
    # moss at the foot of the walls
    for z in (3, 4):
        s.put(x0 - 1, 0, z, 'moss_carpet')
    # a low fence either side of the front path, joining the lantern posts; a lantern under the jetty over the door
    s.put(x0, 0, 0, 'fence', **fence(west=True))
    s.put(x1, 0, 0, 'fence', **fence(east=True))
    s.put(mid, top, z0 - 1, 'lantern', hanging='true', waterlogged='false')
    # indoors: a candle on the table, a pot on the chest, a carpet at the door
    s.put(x1 - 1, 2, z0 + 3, 'flower_pot'); s.decorate(x1 - 1, 2, z0 + 3, 'potted_plant', plant='fern')
    s.put(x0 + 1, 2, z0 + 1, 'candle', candles='2', lit='false', waterlogged='false')
    s.put(mid, 2, z1 - 1, 'lantern', hanging='false', waterlogged='false')
    s.put(mid, 1, z0 + 1, 'brown_carpet')
    s.decorate(mid - 1, 2, z1 - 1, 'item_frame', item='minecraft:painting', facing='north', note='a picture over the bed')
    return s


STRUCTURES = [dict(path='cottage', name='Cottage', tier=1, zone='inner', role='house', group='cottage',
                   terrain='ground_level', door='front', make=cottage,
                   description='Timber cottage: cobblestone base, oak plank walls between upright spruce posts, spruce gable '
                               'roof on a top plate, brick chimney on the back gable; bed, chest, hearth, table inside.',
                   changes='Every log now has an explicit axis (upright spruce posts, beams lying along the walls); the window '
                           'holes now have glass; the gables are jettied out flush with the roof so there is no dark gap '
                           'under it; the dormer stairs and the campfire on the chimney (the "couch" on the roof) are gone; '
                           'and each level adds much more detail: window boxes, shutters, corbels, lantern posts and a '
                           'front fence, a woodpile, a bench, a composter and crates, and more furniture inside.')]

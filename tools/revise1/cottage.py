"""Cottage, revised from Carlton's notes (batch 1 -> revise 1): explicit log axes, real window panes, no dormer or
chimney campfire on the roof, a two-tone roof with real gable overhangs, and a visible step up per level (L2 window
boxes and front garden, L3 a covered woodshed on the side)."""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
TOP = dict(half='top', shape='straight', waterlogged='false')


def pane(axis):
    """A glass pane joined along the wall (x: front/back wall, z: side wall)."""
    if axis == 'x':
        return dict(east='true', west='true', north='false', south='false', waterlogged='false')
    return dict(north='true', south='true', east='false', west='false', waterlogged='false')


def trapdoor(facing, half='bottom', open_=False):
    return dict(facing=facing, half=half, open=str(open_).lower(), powered='false', waterlogged='false')


def cottage():
    """Cottage, 5 x 7 walls, 3 high: cobblestone base course, oak plank walls between upright spruce log posts, a
    stripped spruce top plate, gables jettied one block over the front and back walls (two windows with sills either
    side of a king post), and a spruce gable roof with one block of overhang all round. The roof is two-tone: the eave
    rows and the gable edges are dark oak, with upside-down dark oak stairs as trim under the gable overhangs.

    L1 = shell and fittings (only KNOWN-OK blocks): roof, windows with shutters, door lights, lantern posts, furniture.
    L2 = window boxes with flowers (front and both sides), a low front fence, corbels, a lantern over the door, a rain
    barrel, a hanging lantern inside. L3 = a covered woodshed on the right side (a catslide roof continuing the main
    roof, posts, a stacked woodpile, a chopping block, a lantern), a composter and crates at the back, moss, clutter."""
    W, H, D = 9, 9, 11
    s = Structure('cottage', (W, H, D), palette='oak_a2')
    x0, x1, z0, z1 = 2, 6, 2, 8          # walls
    mid = 4
    top = 3                               # last wall layer
    cx = mid + 1                          # chimney column
    s.at(1, 'shell')
    s.ring(x0, z0, x1, z1, 0, 'base')
    s.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, 'floor')
    s.put(mid, 0, z0 - 1, 'stone_slab', type='bottom', waterlogged='false')       # door step
    posts = {(x, z) for x in (x0, x1) for z in (z0, (z0 + z1) // 2, z1)}
    for y in range(1, top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    s.put(x, y, z, 'shed_post', axis='y') if (x, z) in posts else s.put(x, y, z, 'wall')
    s.remove(mid, 1, z0); s.remove(mid, 2, z0)
    s.door(mid, 1, z0, 'north')
    s.remove(mid, 3, z0); s.put(mid, 3, z0, 'stripped_spruce_log', axis='x')       # lintel
    front_windows = [(mid - 1, 2, z0), (mid + 1, 2, z0)]
    side_windows = [(x0, 2, 3), (x0, 2, 4), (x0, 2, 6), (x1, 2, 3), (x1, 2, 4), (x1, 2, 6)]
    back_windows = [(mid - 1, 2, z1)]
    for x, y, z in front_windows + side_windows + back_windows:
        s.remove(x, y, z)
    for y in range(1, top + 1):                                                     # fireplace back in the wall
        s.remove(cx, y, z1); s.put(cx, y, z1, 'chimney')
    # top plate
    for x in range(x0, x1 + 1):
        s.put(x, top + 1, z0, 'stripped_spruce_log', axis='x'); s.put(x, top + 1, z1, 'stripped_spruce_log', axis='x')
    for z in range(z0 + 1, z1):
        s.put(x0, top + 1, z, 'stripped_spruce_log', axis='z'); s.put(x1, top + 1, z, 'stripped_spruce_log', axis='z')
    # roof: z 0..10 (jettied gables at z 1 and 9, one more block of overhang in front of them); eave rows and the
    # gable-edge rows dark oak, the rest spruce
    for z in range(0, D):
        edge = z in (0, D - 1)
        for i in range(3):
            block = 'dark_stairs' if (i == 0 or edge) else 'roof_stairs'
            s.put(x0 - 1 + i, top + 1 + i, z, block, facing='east', **STAIR)
            s.put(x1 + 1 - i, top + 1 + i, z, block, facing='west', **STAIR)
        s.put(mid, top + 4, z, 'roof_slab', type='bottom', waterlogged='false')     # ridge
    # jettied gables: a beam on the wall line, two windows either side of a king post, planks above
    for z in (z0 - 1, z1 + 1):
        for x in range(x0, x1 + 1):
            s.put(x, top + 1, z, 'stripped_spruce_log', axis='x')
        s.put(mid, top + 2, z, 'stripped_spruce_log', axis='y')
        s.put(mid, top + 3, z, 'plank2')
    gable_windows = [(mid - 1, top + 2, z0 - 1), (mid + 1, top + 2, z0 - 1), (mid - 1, top + 2, z1 + 1)]
    s.put(mid + 1, top + 2, z1 + 1, 'plank2')                                       # (the chimney passes here)
    for y in range(0, top + 6):                                                     # chimney stack, 1 above the ridge
        if (cx, y, z1 + 1) in s.blocks:
            s.remove(cx, y, z1 + 1)
        s.put(cx, y, z1 + 1, 'chimney')
    s.at(1, 'fittings')
    for x, y, z in front_windows + back_windows + gable_windows:
        s.put(x, y, z, 'window', **pane('x'))
    for x, y, z in side_windows:
        s.put(x, y, z, 'window', **pane('z'))
    for x, y, z in gable_windows:                                                   # sills under the gable windows
        s.put(x, y - 1, z - 1 if z < z0 else z + 1, 'trapdoor', **trapdoor('north', 'top'))
    # gable trim: upside-down dark oak stairs under the gable overhang, at the eave corners and under the second step
    for z in (0, D - 1):
        s.put(x0 - 1, top, z, 'dark_stairs', facing='west', **TOP)
        s.put(x1 + 1, top, z, 'dark_stairs', facing='east', **TOP)
        s.put(x0, top + 1, z, 'dark_stairs', facing='west', **TOP)
        s.put(x1, top + 1, z, 'dark_stairs', facing='east', **TOP)
    # shutters beside the side windows (open trapdoors flat on the posts)
    for out, f in ((x0 - 1, 'west'), (x1 + 1, 'east')):
        s.put(out, 2, z0, 'trapdoor', **trapdoor(f, open_=True))
        s.put(out, 2, (z0 + z1) // 2, 'trapdoor', **trapdoor(f, open_=True))
        s.put(out, 2, 7, 'trapdoor', **trapdoor(f, open_=True))
    # lights: torches beside the door, lantern posts at the front corners
    s.put(mid - 1, 3, z0 - 1, 'wall_torch', facing='north')
    s.put(mid + 1, 3, z0 - 1, 'wall_torch', facing='north')
    for x in (x0 - 1, x1 + 1):
        s.put(x, 0, 0, 'fence'); s.put(x, 1, 0, 'lantern', hanging='false', waterlogged='false')
    # furniture: bed and chest at the back, hearth, a barrel table with a chair, a barrel and a workbench
    s.bed(x0 + 1, 1, z1 - 2, 'south')
    s.put(mid, 1, z1 - 1, 'chest', facing='north', type='single', waterlogged='false')
    s.put(cx, 1, z1 - 1, 'hearth', facing='north', lit='false')
    s.put(x1 - 1, 1, z0 + 2, 'barrel', facing='up', open='false')                    # table
    s.put(x1 - 1, 1, z0 + 1, 'chair', facing='north', **STAIR)
    s.put(x0 + 1, 1, z0 + 1, 'barrel', facing='up', open='false')
    s.put(x0 + 1, 1, z0 + 2, 'crafting_table')
    for z in (z0 + 2, z0 + 3, z0 + 4):
        s.put(mid, 1, z, 'rug')
    s.put(mid - 1, 3, z1 - 1, 'wall_torch', facing='north')
    s.put(mid + 1, 3, z0 + 1, 'wall_torch', facing='south')

    s.at(2, 'flourish2')
    # window boxes: coarse dirt (flowers need it) in a rim of open trapdoors, front and both sides
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
        for z in (z0, (z0 + z1) // 2, z1):                                           # corbels under the eave
            s.put(out, top, z, 'roof_stairs', facing=f, **TOP)
    for x in (x0, x1):                                                                # corbels under the jetty beams
        s.put(x, top, z0 - 1, 'roof_stairs', facing='south', **TOP)
        s.add(x, top, z1 + 1, 'roof_stairs', facing='north', **TOP)
    # front garden: a low fence from the lantern posts, a stepping stone, a lantern over the door
    s.put(x0, 0, 0, 'fence'); s.put(x1, 0, 0, 'fence')
    s.put(mid, 0, 0, 'stone_slab', type='bottom', waterlogged='false')
    s.put(mid, top, z0 - 1, 'lantern', hanging='true', waterlogged='false')
    # rain barrel at the back left corner
    s.put(x0 - 1, 0, z1, 'barrel', facing='up', open='false')
    # inside: a lantern on a chain from the ridge, carpet by the bed, a shelf over the workbench
    for y in (top + 2, top + 3):
        s.put(mid, y, 5, 'chain', axis='y')
    s.put(mid, top + 1, 5, 'lantern', hanging='true', waterlogged='false')
    s.put(x0 + 1, 1, z1 - 3, 'brown_carpet')
    s.put(x0 + 1, 3, z0 + 2, 'shelf', **trapdoor('east', 'top'))

    s.at(3, 'flourish3')
    # covered woodshed on the right side, at the back: the roof carries on down one more step (a catslide) over two
    # posts, with a stacked woodpile (log ends out), a chopping block and a lantern under it
    wx = x1 + 2                                                                       # 8
    for z in range(5, 10):
        s.put(wx, top, z, 'dark_stairs', facing='west', **STAIR)
    for z in (5, 9):
        for y in range(0, top):
            s.put(wx, y, z, 'stake_top')                                              # spruce fence posts
    for z in (6, 7, 8):
        for x in (x1 + 1, wx):
            s.put(x, 0, z, 'shed_post', axis='x')
            if z < 8:
                s.put(x, 1, z, 'shed_post', axis='x')
    s.put(x1 + 1, 0, z1 + 1, 'stripped_spruce_log', axis='y')                        # chopping block
    s.put(wx, 2, 7, 'lantern', hanging='true', waterlogged='false')
    # composter and crates behind the house, left of the chimney
    s.put(mid - 1, 0, z1 + 1, 'composter', level='3')
    s.put(x0, 0, z1 + 1, 'barrel', facing='up', open='false')
    s.put(x0, 1, z1 + 1, 'barrel', facing='up', open='false')
    # moss at the foot of the walls, things inside
    for z in (3, 4):
        s.put(x0 - 1, 0, z, 'moss_carpet')
    s.put(x1 - 1, 2, z0 + 2, 'flower_pot'); s.decorate(x1 - 1, 2, z0 + 2, 'potted_plant', plant='fern')
    s.put(x0 + 1, 2, z0 + 1, 'candle', candles='2', lit='false', waterlogged='false')
    s.put(mid, 2, z1 - 1, 'lantern', hanging='false', waterlogged='false')
    s.put(mid, 1, z0 + 1, 'brown_carpet')
    s.decorate(mid - 1, 2, z1 - 1, 'item_frame', item='minecraft:painting', facing='north', note='a picture over the bed')
    return s


STRUCTURES = [dict(path='cottage', name='Cottage', tier=1, zone='inner', role='house', group='cottage',
                   terrain='ground_level', door='front', make=cottage,
                   description='Timber cottage: cobblestone base, oak plank walls between upright spruce posts, two-tone '
                               'spruce and dark oak gable roof with jettied gables, brick chimney; bed, chest, hearth inside.',
                   changes='The roof is now two-tone (dark oak eaves and gable edges, spruce between) and overhangs the '
                           'gables with upside-down stair trim; the pale gable squares are real windows with sills; the lone '
                           'fence inside is gone (the table is a barrel); L1 gets shutters and lantern posts, L2 adds window '
                           'boxes with flowers on three sides and a front fence, and L3 adds a covered woodshed on the right '
                           'side with a stacked woodpile and a lantern.')]

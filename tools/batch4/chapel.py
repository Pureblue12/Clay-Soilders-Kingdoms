"""Chapel (tier 4, look A): a small stone chapel with a bell cote rising from its front gable (docs/STRUCTURES.md,
"Chapel"). Terrain: ground_level (layer 0 is the stone base course and floor, resting on the ground).

Layout (9 wide x 13 deep, front z = 0): walls x 1..7, z 1..10 (7 x 10; the nave inside is 5 x 8), 3 high on the base
course: a cobblestone plinth course at y 1 (some mossy), stone bricks above. Stone buttresses at the four corners and
halfway along both sides, under the eaves. A spruce stair gable roof (dark oak eaves on spruce top plates, a dark ridge
finished with slabs) with one block of overhang at the sides and the back; the front gable is a stone parapet with
stone brick coping that rises past the roof into the bell cote: two stone piers carrying a little stone roof and a
finial (the signature), 13 tall, so it needs scaffolding.
- Front: the door (spruce, 2 tall) under a stone hood on two jambs, a step, a tall window in the gable, shuttered from inside.
- Sides: three lancet windows each (glass panes, 2 high).
- Inside: an aisle from the door to the altar, two rows of pews (spruce stairs) on each side, the altar (a stone table
  at the back, a white cloth on it), the priest's chest, torches on the walls.
Level 2 (the chapel in use): the bell in the cote, a red runner down the aisle, the lectern (pulpit) by the altar,
candles on the altar, lanterns on chains from the roof beams and at the door, flower boxes under the side windows,
flowers by the door, a bench outside.
Level 3 (the feast-day chapel): stained glass in both tall gable windows (front and back), banners on the inner walls and beside the
door (patterns by the decoration step), more candles at the pews, flower pots, vines and moss on the buttresses, a
lantern post and flowers along the path.

Note: the two gable windows are closed by trapdoor shutters set one block inside at levels 1 and 2 (levels only add
blocks, and the brief puts the coloured glass at level 3, so the window opening itself stays free until then)."""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, OPEN, HANG, STAND, LEAVES, connect, noise, gable_roof

PANE = dict(waterlogged='false')
GABLE_WINDOW = (4, 5, 6)


def chapel():
    W, D = 11, 12
    s = Structure('chapel', (W, 13, D), palette='oak_a2')
    x0, x1, z0, z1 = 2, 8, 1, 9
    mid = 5
    top = 3                                          # walls y 1..3
    lancets = (3, 7)
    s.at(1, 'shell')
    # base course and floor
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            P(s, x, 0, z, 'cobblestone' if edge else 'stone_bricks')
    # walls
    for y in range(1, top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                if y == 1:
                    P(s, x, y, z, 'mossy_cobblestone' if noise(x, y, z, 6) < 0.25 else 'cobblestone')
                else:
                    P(s, x, y, z, 'stone_bricks')
    s.remove(mid, 1, z0); s.remove(mid, 2, z0)       # doorway
    for z in lancets:                                # lancet windows, 2 high
        for x in (x0, x1):
            s.remove(x, 2, z); s.remove(x, 3, z)
    # roof over the nave (z 2..10; the front wall row z 1 is the stone gable, built below), one row of overhang at the back
    gable_roof(s, x0, x1, z0 + 1, z1, top, front=0, back=1, gable='stone_bricks', plates='spruce_log',
               gable_at=(z1,))
    # the front gable: stone up to the roof line, coping (stone brick stairs) one above it, kneelers at the eaves
    half = (x1 - x0) // 2                            # 3: roof rows x 1..3 at y 5..7, ridge y 7
    for x in range(x0, x1 + 1):
        i = min(x - x0, x1 - x)
        roof_y = top + 2 + i if i < half else top + 1 + half
        for y in range(top + 1, roof_y + 1):
            P(s, x, y, z0, 'stone_bricks')
    for x, f in ((x0 - 1, 'east'), (x1 + 1, 'west')):
        P(s, x, top + 1, z0, 'stone_brick_stairs', facing=f, **STAIR)
    for i in range(half - 1):                        # coping on the gable slopes (x 1, 2 and 6, 7)
        P(s, x0 + i, top + 3 + i, z0, 'stone_brick_stairs', facing='east', **STAIR)
        P(s, x1 - i, top + 3 + i, z0, 'stone_brick_stairs', facing='west', **STAIR)
    for z in (z0, z1):                               # tall gable windows (front and back), shuttered from inside
        for y in GABLE_WINDOW:                       # until the stained glass comes at level 3
            s.remove(mid, y, z)
    # the bell cote: two piers on the gable top, a stone roof, a finial
    cy = top + 1 + half + 1                          # 8: the first layer above the ridge
    for x in (mid - 1, mid, mid + 1):
        P(s, x, cy, z0, 'stone_bricks')
    for y in (cy + 1, cy + 2):
        P(s, mid - 1, y, z0, 'stone_bricks'); P(s, mid + 1, y, z0, 'stone_bricks')
    P(s, mid - 1, cy + 3, z0, 'stone_brick_stairs', facing='east', **STAIR)
    P(s, mid + 1, cy + 3, z0, 'stone_brick_stairs', facing='west', **STAIR)
    P(s, mid, cy + 3, z0, 'stone_bricks')
    P(s, mid, cy + 4, z0, 'cobblestone_wall')
    for z in (z0 - 1, z0 + 1):                       # the cote's little roof, front and back
        P(s, mid, cy + 3, z, 'stone_brick_stairs', facing='south' if z > z0 else 'north', **STAIR)
    # buttresses at the corners and halfway along the sides
    for z in (z0, (z0 + z1) // 2, z1):
        for x, f in ((x0 - 1, 'east'), (x1 + 1, 'west')):
            P(s, x, 0, z, 'cobblestone')
            P(s, x, 1, z, 'stone_bricks')
            P(s, x, 2, z, 'stone_brick_stairs', facing=f, **STAIR)
    for x in (x0, x1):                                # the corner buttresses also face the front
        P(s, x, 0, z0 - 1, 'cobblestone')
        P(s, x, 1, z0 - 1, 'stone_bricks')
        P(s, x, 2, z0 - 1, 'stone_brick_stairs', facing='south', **STAIR)
    # the porch hood over the door: jambs and an arch of upside-down stairs
    for x in (mid - 1, mid + 1):
        P(s, x, 0, z0 - 1, 'cobblestone')
        P(s, x, 1, z0 - 1, 'stone_bricks'); P(s, x, 2, z0 - 1, 'stone_bricks')
    P(s, mid - 1, 3, z0 - 1, 'stone_brick_stairs', facing='west', **STAIR)
    P(s, mid + 1, 3, z0 - 1, 'stone_brick_stairs', facing='east', **STAIR)
    P(s, mid, 3, z0 - 1, 'stone_brick_stairs', facing='south', **TOP)
    P(s, mid, 0, z0 - 1, 'cobblestone_stairs', facing='south', **STAIR)      # the step
    s.door(mid, 1, z0, 'north', block='spruce_door')

    s.at(1, 'fittings')
    for z, f in ((z0 + 1, 'south'), (z1 - 1, 'north')):                     # the gable windows' inner shutters
        for y in GABLE_WINDOW:
            P(s, mid, y, z, 'spruce_trapdoor', facing=f, **OPEN)
    for z in lancets:
        for x in (x0, x1):
            for y in (2, 3):
                P(s, x, y, z, 'glass_pane', north='true', south='true', east='false', west='false', **PANE)
    # pews: two on each side of the aisle, two rows, a gap in the middle
    for z in (z0 + 2, z0 + 3, z0 + 4, z0 + 5):
        for x in (x0 + 1, x0 + 2, x1 - 2, x1 - 1):
            P(s, x, 1, z, 'spruce_stairs', facing='north', **STAIR)
    # the altar: a stone table (upside-down stairs either side of a block) with a white cloth
    P(s, mid - 1, 1, z1 - 1, 'stone_brick_stairs', facing='east', **TOP)
    P(s, mid, 1, z1 - 1, 'stone_bricks')
    P(s, mid + 1, 1, z1 - 1, 'stone_brick_stairs', facing='west', **TOP)
    P(s, mid, 2, z1 - 1, 'white_carpet')
    P(s, x1 - 1, 1, z1 - 1, 'chest', facing='west', type='single', waterlogged='false')
    for z in (z0 + 4,):                                                     # torches between the windows
        P(s, x0 + 1, 3, z, 'wall_torch', facing='east')
        P(s, x1 - 1, 3, z, 'wall_torch', facing='west')

    s.at(2, 'flourish2')
    P(s, mid, cy + 1, z0, 'bell', attachment='ceiling', facing='north', powered='false')   # the signature
    P(s, mid, cy + 2, z0, 'chain', axis='y')                                 # its hanger, under the cote roof
    for z in range(z0 + 1, z1 - 1):                                          # the red runner down the aisle
        P(s, mid, 1, z, 'red_carpet')
    P(s, x0 + 1, 1, z1 - 2, 'lectern', facing='east', has_book='false', powered='false')   # the pulpit
    s.decorate(x0 + 1, 1, z1 - 2, 'item', item='minecraft:book', note='the book on the lectern')
    for x, n in ((mid - 1, '3'), (mid + 1, '3')):                            # candles on the altar
        P(s, x, 2, z1 - 1, 'candle', candles=n, lit='true', waterlogged='false')
    for z in (z0 + 3, z1 - 2):                                               # lanterns on chains from the ridge
        P(s, mid, top + 3, z, 'chain', axis='y'); P(s, mid, top + 2, z, 'chain', axis='y')
        P(s, mid, top + 1, z, 'lantern', **HANG)
    for x in (mid - 1, mid + 1):                                             # lanterns on the hood
        P(s, x, 4, z0 - 1, 'lantern', **STAND)
    for z in lancets:                                                        # flower boxes under the side windows
        for x, f in ((x0 - 1, 'west'), (x1 + 1, 'east')):
            P(s, x, 1, z, 'coarse_dirt')
            P(s, x + (-1 if f == 'west' else 1), 1, z, 'spruce_trapdoor', facing=f, **OPEN)
            for dz in (-1, 1):
                P(s, x, 1, z + dz, 'spruce_trapdoor', facing='north' if dz < 0 else 'south', **OPEN)
    for z, (fa, fb) in zip(lancets, (('poppy', 'cornflower'), ('oxeye_daisy', 'dandelion'))):
        P(s, x0 - 1, 2, z, fa); P(s, x1 + 1, 2, z, fb)
    for x, f in ((mid - 2, 'poppy'), (mid + 2, 'lily_of_the_valley')):      # flowers by the door
        P(s, x, 0, z0 - 1, 'coarse_dirt'); P(s, x, 1, z0 - 1, f)
    P(s, W - 1, 0, z0 + 4, 'spruce_stairs', facing='east', **STAIR)         # a bench outside on the right
    P(s, W - 1, 0, z0 + 5, 'spruce_stairs', facing='east', **STAIR)

    s.at(3, 'flourish3')
    for z in (z0, z1):                                                       # stained glass in the gable windows
        for y, c in zip(GABLE_WINDOW, ('red', 'yellow', 'blue')):
            P(s, mid, y, z, f'{c}_stained_glass_pane', north='false', south='false', east='true', west='true',
              **PANE)
    for z in (z0 + 1, z0 + 5):                                               # banners on the inner walls
        P(s, x0 + 1, 3, z, 'white_wall_banner', facing='east')
        P(s, x1 - 1, 3, z, 'white_wall_banner', facing='west')
        for x in (x0 + 1, x1 - 1):
            s.decorate(x, 3, z, 'banner_pattern', banner='white_wall_banner', note='a gold cross')
    for x in (mid - 2, mid + 2):                                             # banners beside the door, outside
        P(s, x, 3, z0 - 1, 'red_wall_banner', facing='north')
        s.decorate(x, 3, z0 - 1, 'banner_pattern', banner='red_wall_banner', note='the kingdom colours')
    for x in (x0 + 1, x1 - 1):                                               # candles at the front pews
        P(s, x, 1, z0 + 1, 'candle', candles='2', lit='true', waterlogged='false')
    # flowers, bushes and a lantern post along the path
    for x, z, b in ((0, 0, 'leaves'), (W - 1, 0, 'leaves'), (0, D - 1, 'leaves'), (W - 1, D - 1, 'fern'),
                    (2, D - 1, 'poppy'), (6, D - 1, 'azure_bluet')):
        if b == 'leaves':
            A(s, x, 0, z, 'oak_leaves', **LEAVES)
        else:
            A(s, x, 0, z, b)
    for y in (0, 1):
        P(s, W - 1, y, 1, 'spruce_fence')
    P(s, W - 1, 2, 1, 'lantern', **STAND)
    for x in (x0 - 1, x1 + 1):                                               # flower pots on the middle buttresses
        P(s, x, 3, (z0 + z1) // 2, 'flower_pot')
        s.decorate(x, 3, (z0 + z1) // 2, 'potted_plant', plant='white_tulip')
    connect(s)
    return s


STRUCTURES = [dict(path='chapel', name='Chapel', tier=4, zone='inner', role='religious', group='chapel',
                   terrain='ground_level', door='front', make=chapel, max_height=13, changes='First version.',
                   description='Needs scaffolding: 13 tall (the bell cote; the nave roof is 9). Over budget: 440 blocks vs 210 (first build 370 vs 170; 7 wide instead of 5, for two pews a side). Stone chapel (stone '
                               'bricks on a cobblestone plinth, buttresses, spruce gable roof) whose front gable rises '
                               'into a bell cote; door under a stone hood, lancet windows, pews, an altar; L2 adds the '
                               'bell, runner, lectern, candles, lanterns, flower boxes; L3 stained glass oculi, banners, '
                               'more candles and flowers.')]

"""Chapel (tier 4, look A): a small stone chapel with a bell cote rising from its front gable (docs/STRUCTURES.md,
"Chapel"). Terrain: ground_level (layer 0 is the stone base course and floor, resting on the ground).

Layout (11 wide x 16 deep, front z = 0): walls x 2..8, z 5..13; z 0..3 in front is the churchyard path (L3) (7 x 10; the nave inside is 5 x 8), 3 high on the base
course: a cobblestone plinth course at y 1 (some mossy), stone bricks above. Stone buttresses at the four corners and
halfway along both sides, under the eaves. A spruce stair gable roof (dark oak eaves on spruce top plates, a dark ridge
finished with slabs) with one block of overhang at the sides and the back; the front gable is a stone parapet with
stone brick coping that rises past the roof into the bell cote: two stone piers carrying a little stone roof and a
finial (the signature), 13 tall, so it needs scaffolding.
- Front: the door (spruce, 2 tall) under a stone hood on two jambs, a step, a tall window in the gable (unglazed until L3, open
  shutters beside it).
- Sides: three lancet windows each (glass panes, 2 high).
- Inside: an aisle from the door to the altar, two rows of pews (spruce stairs) on each side, the altar (a stone table
  at the back, a white cloth on it), the priest's chest, torches on the walls.
Level 2 (the chapel in use): the bell in the cote, a red runner down the aisle, the lectern (pulpit) by the altar,
candles on the altar, lanterns on chains from the roof beams and at the door, flower boxes under the side windows,
flowers by the door, a bench outside.
Level 3 (the feast-day chapel): the churchyard front, seen from afar: a low cobblestone wall along the front with a
lych gate (spruce posts, a small gable roof, a lantern under its beam, banners), side walls ending in lantern
piers, four old headstones, a stone slab path from it to the door, lantern
posts and flowers beside the path; stained glass in both tall gable windows, banners on the inner walls and beside the
door (patterns by the decoration step), candles at the front pews, flower pots on the buttresses."""
from make_batch1_structures import Structure
from batch3.ws_kit import P, A, STAIR, TOP, SLAB, OPEN, HANG, STAND, LEAVES, connect, noise, gable_roof

PANE = dict(waterlogged='false')
GABLE_WINDOW = (4, 5, 6)


def chapel():
    W, D = 11, 16
    s = Structure('chapel', (W, 13, D), palette='oak_a2')
    x0, x1, z0, z1 = 2, 8, 5, 13                  # z 0..3: the churchyard path and lych gate (L3)
    mid = 5
    top = 3                                          # walls y 1..3
    lancets = (z0 + 2, z0 + 6)
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
    for z in (z0, z1):                               # tall gable windows (front and back): unglazed, with open
        for y in GABLE_WINDOW:                       # shutters, until the stained glass comes at level 3
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
    P(s, mid - 1, 3, z0 - 1, 'stone_brick_stairs', facing='west', **TOP)    # flat tops: the L2 lanterns stand on them
    P(s, mid + 1, 3, z0 - 1, 'stone_brick_stairs', facing='east', **TOP)
    P(s, mid, 3, z0 - 1, 'stone_brick_stairs', facing='south', **TOP)
    P(s, mid, 0, z0 - 1, 'cobblestone_stairs', facing='south', **STAIR)      # the step
    s.door(mid, 1, z0, 'north', block='spruce_door')

    s.at(1, 'fittings')
    for z, f in ((z0 - 1, 'north'), (z1 + 1, 'south')):                     # open shutters beside the gable windows
        for x in (mid - 1, mid + 1):
            for y in GABLE_WINDOW[1:]:
                A(s, x, y, z, 'spruce_trapdoor', facing=f, **OPEN)
    A(s, mid, top + 3, z1 + 1, 'spruce_planks')                              # closes the back barge board under the ridge
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
    # the churchyard: a low wall along the front with a lych gate (spruce posts, a little gable roof, a lantern
    # under the beam), a stone path from it to the door, lantern posts and flowers beside the path
    gz = 1
    for x in list(range(0, mid - 2)) + list(range(mid + 3, W)):
        P(s, x, 0, gz, 'cobblestone_wall')
    for x in (mid - 2, mid + 2):
        for y in (0, 1, 2):
            P(s, x, y, gz, 'spruce_log', axis='y')
    for x in range(mid - 1, mid + 2):                                         # the tie beam
        P(s, x, 3, gz, 'spruce_log', axis='x')
    for z in (gz - 1, gz, gz + 1):                                            # a steep gable roof, ridge front to back
        for i in range(2):
            P(s, mid - 2 + i, 3 + i, z, 'spruce_stairs', facing='east', **STAIR)
            P(s, mid + 2 - i, 3 + i, z, 'spruce_stairs', facing='west', **STAIR)
        P(s, mid, 4, z, 'dark_oak_planks')
        P(s, mid, 5, z, 'spruce_slab', **SLAB)
    for z in (gz - 1, gz + 1):                                                # king posts under the ridge
        P(s, mid, 3, z, 'spruce_fence')
    P(s, mid, 2, gz, 'lantern', **HANG)
    for z in range(gz + 1, z0):                                               # the churchyard wall's sides
        for x in (0, W - 1):
            P(s, x, 0, z, 'stone_bricks' if z == z0 - 1 else 'cobblestone_wall')
    for x in (0, W - 1):                                                      # piers at the ends, lanterns on them
        P(s, x, 1, z0 - 1, 'lantern', **STAND)
    for x in (mid - 2, mid + 2):                                              # banners on the gate posts
        P(s, x, 1, gz - 1, 'red_wall_banner', facing='north')
        s.decorate(x, 1, gz - 1, 'banner_pattern', banner='red_wall_banner', note='the kingdom colours')
    # old graves in the churchyard, either side of the path
    P(s, 1, 0, 2, 'stone_brick_stairs', facing='south', **STAIR)
    P(s, W - 2, 0, 2, 'cobblestone_stairs', facing='south', **STAIR)
    P(s, 2, 0, 2, 'mossy_stone_brick_stairs', facing='south', **STAIR)
    P(s, W - 3, 0, 2, 'stone_brick_stairs', facing='south', **STAIR)
    for z in range(0, z0 - 1):
        for x in (mid - 1, mid, mid + 1):
            P(s, x, 0, z, 'mossy_cobblestone_slab' if noise(x, 0, z, 8) < 0.3 else 'cobblestone_slab', **SLAB)
    for x in (2, W - 3):
        for y in (0, 1):
            P(s, x, y, 3, 'spruce_fence')
        P(s, x, 2, 3, 'lantern', **STAND)
    for x, z, b in ((mid - 2, 3, 'poppy'), (mid + 2, 3, 'azure_bluet'), (mid - 2, 2, 'oxeye_daisy'),
                    (mid + 2, 2, 'cornflower'), (0, D - 1, 'leaves'),
                    (W - 1, D - 1, 'fern'), (2, D - 1, 'poppy'), (6, D - 1, 'azure_bluet'), (0, 0, 'fern'),
                    (W - 1, 0, 'dandelion')):
        if b == 'leaves':
            A(s, x, 0, z, 'oak_leaves', **LEAVES)
        else:
            A(s, x, 0, z, b)
    for x in (x0 - 1, x1 + 1):                                               # flower pots on the middle buttresses
        P(s, x, 3, (z0 + z1) // 2, 'flower_pot')
        s.decorate(x, 3, (z0 + z1) // 2, 'potted_plant', plant='white_tulip')
    connect(s)
    return s


STRUCTURES = [dict(path='chapel', name='Chapel', tier=4, zone='inner', role='religious', group='chapel',
                   terrain='ground_level', door='front', make=chapel, max_height=13, changes='Quality check round 1: lanterns on the door hood now stand on flat (upside-down) '
                           'stair tops; the gable windows are unglazed with open shutters beside them (no inner '
                           'shutters hiding the L3 stained glass); the back barge board hole is closed; L3 adds a '
                           'walled churchyard front: a low wall with a lych gate and banners, side walls with lantern piers, old headstones, a stone path to the door and lantern posts '
                           '(the box grew 4 rows at the front).',
                   description='Needs scaffolding: 13 tall (the bell cote; the nave roof is 9). Over budget: 510 blocks vs 210 (first build 371 vs 170; the L3 churchyard front is 94; 7 wide instead of 5, for two pews a side). Stone chapel (stone '
                               'bricks on a cobblestone plinth, buttresses, spruce gable roof) whose front gable rises '
                               'into a bell cote; door under a stone hood, lancet windows, shuttered gable windows, pews, an altar; L2 adds the '
                               'bell, runner, lectern, candles, lanterns, flower boxes; L3 the churchyard front (a low '
                               'churchyard wall with a lych gate, old headstones, a stone path, lantern posts), stained glass in the gable windows, '
                               'banners, candles and flowers.')]

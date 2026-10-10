"""Garden (tier 2): an English cottage garden laid out like a small formal parterre, level with the grass
(docs/STRUCTURES.md, "Garden (levels 1 to 3)").

Ground rule: this is a flush piece (ground_layer=0). Layer y = 0 REPLACES the top grass block, so the gravel paths,
the stone rings and the bed soil sit level with the lawn; a cell left empty in layer 0 stays grass. Everything that
stands (flowers, hedges, trees, benches) is at y >= 1. There is no raised rim.

Layout (15 x 15, front z = 0): a low leaf hedge all round with an opening at the front; a gravel path from the
entrance to a round gravel walk circling a stone-ringed flower bed with a sundial in the middle; side paths to two
benches (west and east) and a back path to a leafy arbor with a bench under it. Four curved borders of mixed flowers
hug the round walk, a small tree or flowering azalea stands in each corner."""
import math

from make_batch1_structures import Structure
from revise1.blockconn import connect, noise, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')
HANGING = dict(hanging='true', waterlogged='false')
LEAVES = dict(distance='1', persistent='true', waterlogged='false')
OPEN = dict(half='bottom', open='true', powered='false', waterlogged='false')
SLAB = dict(type='bottom', waterlogged='false')

# Flower drifts (cottage style: colour in clumps, not confetti). Each border gets its own mix; L2 plants the drift
# kinds in every other cell, L3 fills the rest (densest), poppies (KNOWN-OK) are L1's few plants.
DRIFTS = {
    'fl': ('cornflower', 'oxeye_daisy', 'allium', 'pink_tulip'),
    'fr': ('dandelion', 'orange_tulip', 'azure_bluet', 'red_tulip'),
    'bl': ('allium', 'white_tulip', 'cornflower', 'lily_of_the_valley'),
    'br': ('oxeye_daisy', 'pink_tulip', 'azure_bluet', 'dandelion'),
}
TALL = ('rose_bush', 'peony', 'lilac')


def tall(s, x, z, name, y=1):
    put_id(s, x, y, z, name, half='lower')
    put_id(s, x, y + 1, z, name, half='upper')


def garden(size=15):
    """Level 1: the layout, flush with the grass: hedge ring with a front opening, gravel paths and round walk,
    the stone ring of the centre bed, four dirt borders with a few poppies, cobblestone pads where the benches go.
    Level 2: trees in the back corners (a birch and a cherry), flowering azalea bushes in the front corners, the arbor
    over the back bench, benches on the side paths, and the borders half planted with eight flower kinds.
    Level 3: the sundial in the centre bed, lanterns at the entrance, on the side benches and under the arbor, the
    borders filled densely with tall rose bushes, peonies and lilacs, pink petals and lilies of the valley."""
    s = Structure('garden', (size, 7, size), palette='oak_a2')
    c, e = size // 2, size - 1                                     # centre 7, last 14

    def d(x, z):
        return math.hypot(x - c, z - c)

    def quad(x, z):
        return ('f' if z < c else 'b') + ('l' if x < c else 'r')

    hedge = {(x, z) for x in range(size) for z in range(size) if x in (0, e) or z in (0, e)}
    hedge -= {(c - 1, 0), (c, 0), (c + 1, 0)}                      # front opening
    centre_ring = {(x, z) for x in range(size) for z in range(size) if 1.6 < d(x, z) <= 2.3}
    centre_bed = {(x, z) for x in range(size) for z in range(size) if d(x, z) <= 1.6}
    walk = {(x, z) for x in range(size) for z in range(size) if 2.3 < d(x, z) <= 3.3}
    paths = {(c, z) for z in range(0, e)} | {(x, c) for x in range(2, e - 1)}
    paths -= centre_ring | centre_bed
    bench_pads = {(1, z) for z in (c - 1, c, c + 1)} | {(e - 1, z) for z in (c - 1, c, c + 1)} | \
                 {(x, e - 1) for x in (c - 1, c, c + 1)}
    corners = {(x, z) for x in (1, 2, e - 2, e - 1) for z in (1, 2, e - 2, e - 1)}
    borders = {(x, z) for x in range(1, e) for z in range(1, e)
               if 3.3 < d(x, z) <= 6.0 and (x, z) not in paths and (x, z) not in corners}

    s.at(1, 'shell')
    for x, z in walk | paths:
        put_id(s, x, 0, z, 'gravel')
    for x, z in centre_ring:
        put_id(s, x, 0, z, 'stone_bricks')
    for x, z in centre_bed | borders:
        put_id(s, x, 0, z, 'coarse_dirt')
    for x, z in bench_pads:
        put_id(s, x, 0, z, 'cobblestone')
    for x in (c - 1, c + 1):                                       # stone threshold either side of the entrance path
        put_id(s, x, 0, 0, 'cobblestone')

    s.at(1, 'fittings')
    for x, z in hedge:                                             # the low hedge round the garden
        put_id(s, x, 1, z, 'oak_leaves', **LEAVES)
    put_id(s, c, 1, c, 'stone_bricks')                             # the sundial's foot in the centre bed
    for x, z in sorted(borders):                                   # a few poppies along the inner edge of each border
        if d(x, z) <= 4.8 and (x + z) % 2 == 0:
            put_id(s, x, 1, z, 'poppy')

    s.at(2, 'flourish2')
    # trees: a birch in the back left, a cherry in the back right, flowering azalea bushes in the front corners
    tree(s, 2, e - 2, 'birch_log', 'birch_leaves', 4)
    tree(s, e - 2, e - 2, 'cherry_log', 'cherry_leaves', 4)
    for x in (2, e - 2):
        put_id(s, x, 1, 2, 'flowering_azalea')
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            put_id(s, x + dx, 1, 2 + dz, 'azalea')
        put_id(s, x, 2, 2, 'flowering_azalea_leaves', **LEAVES)
    # benches at the ends of the side paths, facing the centre, with arms
    for x, back in ((1, 'west'), (e - 1, 'east')):
        for z in (c - 1, c, c + 1):
            put_id(s, x, 1, z, 'oak_stairs', facing=back, **STAIR)
    # the arbor over the back bench: four fence posts under a flowering leaf roof
    for x in (c - 1, c, c + 1):
        put_id(s, x, 1, e - 1, 'oak_stairs', facing='south', **STAIR)
    for x in (c - 2, c + 2):                                       # the posts, also the bench's arms
        for z in (e - 2, e - 1):
            put_id(s, x, 1, z, 'oak_fence'); put_id(s, x, 2, z, 'oak_fence')
    for x in range(c - 2, c + 3):
        for z in (e - 2, e - 1):
            put_id(s, x, 3, z, 'flowering_azalea_leaves' if (x + z) % 2 else 'azalea_leaves', **LEAVES)
    put_id(s, c, 4, e - 1, 'flowering_azalea_leaves', **LEAVES)
    # a rose arch over the entrance: two posts on the threshold stones, a leafy top
    for x in (c - 1, c + 1):
        put_id(s, x, 1, 0, 'oak_fence'); put_id(s, x, 2, 0, 'oak_fence')
    for x in (c - 1, c, c + 1):
        put_id(s, x, 3, 0, 'flowering_azalea_leaves' if x == c else 'azalea_leaves', **LEAVES)
    # arms on the side benches
    for x in (1, e - 1):
        for z, f in ((c - 2, 'north'), (c + 2, 'south')):
            put_id(s, x, 1, z, 'spruce_trapdoor', facing=f, **OPEN)
    # the borders: drift flowers in every other cell
    for x, z in sorted(borders):
        if (x, z) in s.blocks or (x, 1, z) in s.blocks:
            continue
        if (x + z) % 2 == 1:
            put_id(s, x, 1, z, _drift(x, z, quad(x, z), d(x, z)))

    lawn_under(s, 2)

    s.at(3, 'flourish3')
    # the sundial in the centre bed, flowers round its foot
    put_id(s, c, 2, c, 'stone_brick_wall')
    put_id(s, c, 3, c, 'daylight_detector', inverted='false', power='0')
    for x, z in sorted(centre_bed):
        if (x, 1, z) not in s.blocks:
            put_id(s, x, 1, z, 'lily_of_the_valley' if (x == c or z == c) else 'pink_petals',
                   **({} if (x == c or z == c) else dict(facing=_away(x - c, z - c), flower_amount='4')))
    # lanterns: beside the side benches, hanging in the arbor and the entrance arch
    for x in (0, e):
        for z in (c - 2, c + 2):
            put_id(s, x, 2, z, 'lantern', **LANTERN)
    put_id(s, c, 2, e - 2, 'lantern', **HANGING)                   # under the arbor
    put_id(s, 2, 2, e - 3, 'bee_nest', facing='north', honey_level='0')   # a bee nest on the birch
    put_id(s, c, 2, 0, 'lantern', **HANGING)                       # under the entrance arch
    for x in (c - 1, c + 1):                                       # vines hanging from the arbor roof
        put_id(s, x, 2, e - 2, 'vine', up='true', north='false', south='false', east='false', west='false')
    lawn = [(x, z) for x in range(1, e) for z in range(1, e) if (x, 0, z) not in s.blocks and (x, 1, z) not in s.blocks]
    for x, z in lawn:                                              # wild flowers in the lawn along the hedge
        if noise(x, 1, z, 5) < 0.45:
            put_id(s, x, 1, z, ('azure_bluet', 'oxeye_daisy', 'lily_of_the_valley', 'dandelion')[(x + 2 * z) % 4])
    # densest flowers: tall flowers at the back of each border, the rest filled
    for x, z in sorted(borders):
        if (x, 1, z) in s.blocks:
            continue
        if d(x, z) > 5.0 and (x + z) % 2 == 0 and (x, 2, z) not in s.blocks:
            tall(s, x, z, TALL[(x + 2 * z) % len(TALL)])
        else:
            put_id(s, x, 1, z, _drift(x, z, quad(x, z), d(x, z), shift=1))
    lawn_under(s, 3)
    return connect(s)


def lawn_under(s, level):
    """Layer 0 replaces the ground, so a plant or post standing on the lawn gets a grass block under it (the same
    grass that was there); without it the check sees it hanging in the air."""
    for (x, y, z), b in list(s.blocks.items()):
        if y == 1 and b[2] == level and (x, 0, z) not in s.blocks:
            s.put(x, 0, z, 'grass_block', snowy='false')


def _away(dx, dz):
    if abs(dx) >= abs(dz):
        return 'east' if dx > 0 else 'west'
    return 'south' if dz > 0 else 'north'


def _drift(x, z, q, dist, shift=0):
    """The flower kind of a border cell: clumps along the border (by angle), so colours come in drifts."""
    kinds = DRIFTS[q]
    a = math.atan2(z - 7, x - 7)
    band = int((a + math.pi) / (math.pi / 8))                       # 16 slices round the garden
    return kinds[(band + shift + (1 if dist > 4.8 else 0)) % len(kinds)]


def tree(s, x, z, log, leaves, height):
    """A small tree: a log trunk 'height' high, a 3 x 3 crown with a rounded top (all inside the box)."""
    for y in range(1, height + 1):
        put_id(s, x, y, z, log, axis='y')
    for y in (height - 1, height):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if (dx or dz) and not (abs(dx) == 1 and abs(dz) == 1 and y == height):
                    put_id(s, x + dx, y, z + dz, leaves, **LEAVES)
    for dx, dz in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
        put_id(s, x + dx, height + 1, z + dz, leaves, **LEAVES)


STRUCTURES = [dict(path='garden', name='Garden (level 1)', tier=2, zone='inner', role='civic', group='garden',
                   terrain='flexible', door=None, make=garden, ground_layer=0,
                   changes='Bigger (15 x 15) and level with the grass: paths and beds are set into the lawn with no '
                           'raised rim, and it is now a proper cottage garden with a round walk round a sundial bed, '
                           'curved borders of a dozen flower kinds, a birch, a cherry, azalea bushes, a flowering arbor '
                           'and benches.',
                   description='A 15 x 15 cottage garden set into the lawn: a low hedge, gravel paths and a round walk '
                               'round a sundial bed, curved flower borders; later levels add trees, azaleas, an arbor, '
                               'benches, lanterns and dense mixed flowers.')]

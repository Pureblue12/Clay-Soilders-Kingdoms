"""Garden (tier 2): an English cottage garden laid out like a small formal parterre, level with the grass
(docs/STRUCTURES.md, "Garden (levels 1 to 3)").

Ground rule: this is a flush piece (ground_layer=0). Layer y = 0 REPLACES the top grass block, so the gravel paths,
the stone rings, the pond and the bed soil sit level with the lawn; a cell left empty in layer 0 stays grass.
Everything that stands (flowers, hedges, trees, benches) is at y >= 1. There is no raised rim.

Layout (17 wide x 17 deep, front z = 0): a low leaf hedge all round with an opening at the front; a gravel path from
the entrance to a round gravel walk circling a stone-ringed flower bed with a sundial in the middle; side paths to two
benches (west and east) and a back path to a leafy arbor with a bench under it. Four curved borders of mixed flowers
hug the round walk. Two big shade trees (a birch at the back left, a cherry at the back right) spread their crowns
over the side benches, the back of the walk and the arbor. A small lily pond with a stone edge sits in the front left
corner, flowering azalea bushes in the front right.

Water and the lily pad are decorations (builders can't place them); the batch-2 driver shows them in the Studio file."""
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
# kinds in every other cell, L3 fills the rest (densest), poppies (KNOWN-OK) are L1's plants.
DRIFTS = {
    'fl': ('cornflower', 'oxeye_daisy', 'allium', 'pink_tulip', 'blue_orchid'),
    'fr': ('dandelion', 'orange_tulip', 'azure_bluet', 'red_tulip', 'cornflower'),
    'bl': ('allium', 'white_tulip', 'cornflower', 'lily_of_the_valley', 'pink_tulip'),
    'br': ('oxeye_daisy', 'pink_tulip', 'azure_bluet', 'dandelion', 'blue_orchid'),
}
TALL = ('rose_bush', 'peony', 'lilac')
WILD = ('azure_bluet', 'oxeye_daisy', 'cornflower', 'dandelion', 'lily_of_the_valley', 'allium')


def tall(s, x, z, name, y=1):
    put_id(s, x, y, z, name, half='lower')
    put_id(s, x, y + 1, z, name, half='upper')


def garden(size=17):
    """Level 1: the layout, flush with the grass: hedge ring with a front opening, gravel paths and round walk,
    the stone ring of the centre bed, four dirt borders with poppies, cobblestone pads where the benches go, the
    stone-edged pond (water from the decoration step).
    Level 2: the two shade trees (birch and cherry) with branching trunks and broad layered crowns over the benches,
    flowering azalea bushes in the front right, the arbor over the back bench, benches on the side paths, a lily pad
    and a stone kerb on the pond, and the borders half planted with ten flower kinds.
    Level 3: the sundial in the centre bed, lanterns (entrance, benches, arbor, hanging from the tree branches), a bee
    nest, the borders filled densely with tall rose bushes, peonies and lilacs, wild flowers along the hedge, ferns
    and a second lily pad at the pond."""
    W, D = size, size
    s = Structure('garden', (W, 9, D), palette='oak_a2')
    c, e = W // 2, W - 1                                           # centre 8, last 16

    def d(x, z):
        return math.hypot(x - c, z - c)

    def quad(x, z):
        return ('f' if z < c else 'b') + ('l' if x < c else 'r')

    trees = ((3, 11), (e - 3, 11))                                  # birch back left, cherry back right
    pond_rim = {(x, z) for x in range(1, 6) for z in range(1, 5)}
    pond = {(x, z) for x in range(2, 5) for z in range(2, 4)}      # 3 x 2 of water
    rim = pond_rim - pond
    hedge = {(x, z) for x in range(W) for z in range(D) if x in (0, e) or z in (0, e)}
    hedge -= {(c - 1, 0), (c, 0), (c + 1, 0)}                      # front opening
    centre_ring = {(x, z) for x in range(W) for z in range(D) if 1.6 < d(x, z) <= 2.3}
    centre_bed = {(x, z) for x in range(W) for z in range(D) if d(x, z) <= 1.6}
    walk = {(x, z) for x in range(W) for z in range(D) if 2.3 < d(x, z) <= 3.3}
    paths = {(c, z) for z in range(0, e)} | {(x, c) for x in range(2, e - 1)}
    paths -= centre_ring | centre_bed
    bench_pads = {(1, z) for z in (c - 1, c, c + 1)} | {(e - 1, z) for z in (c - 1, c, c + 1)} | \
                 {(x, e - 1) for x in (c - 1, c, c + 1)}
    tree_feet = {(tx + dx, tz + dz) for tx, tz in trees for dx in (-1, 0, 1) for dz in (-1, 0, 1)}
    azalea_corner = {(x, z) for x in (e - 3, e - 2, e - 1) for z in (1, 2, 3)}
    borders = {(x, z) for x in range(1, e) for z in range(1, e)
               if 3.3 < d(x, z) <= 6.3 and (x, z) not in paths and (x, z) not in tree_feet
               and (x, z) not in pond_rim and (x, z) not in azalea_corner and (x, z) not in bench_pads}

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
    for x, z in sorted(rim):                                       # the pond's stone edge, a little moss in it
        put_id(s, x, 0, z, 'mossy_cobblestone' if noise(x, 0, z, 3) < 0.3 else 'stone_bricks')
    for x, z in sorted(pond):
        s.decorate(x, 0, z, 'water')

    s.at(1, 'fittings')
    for x, z in hedge:                                             # the low hedge round the garden
        put_id(s, x, 1, z, 'oak_leaves', **LEAVES)
    put_id(s, c, 1, c, 'stone_bricks')                             # the sundial's foot in the centre bed
    for x, z in sorted(borders):                                   # poppies along the inner edge of each border
        if d(x, z) <= 4.8 and (x + z) % 2 == 0:
            put_id(s, x, 1, z, 'poppy')

    s.at(2, 'flourish2')
    tree(s, *trees[0], 'birch_log', 'birch_wood', 'birch_leaves', seed=1)
    tree(s, *trees[1], 'cherry_log', 'cherry_wood', 'cherry_leaves', seed=2)
    # flowering azalea bushes in the front right corner
    for x, z, h in ((e - 2, 2, 2), (e - 3, 1, 1), (e - 1, 3, 1), (e - 3, 3, 1), (e - 1, 1, 1)):
        put_id(s, x, 1, z, 'flowering_azalea' if h == 2 else 'azalea')
        if h == 2:
            put_id(s, x, 2, z, 'flowering_azalea_leaves', **LEAVES)
    for x, z in ((e - 2, 1), (e - 1, 2), (e - 3, 2), (e - 2, 3)):
        put_id(s, x, 1, z, 'flowering_azalea_leaves', **LEAVES)
    # the pond: a lily pad, a low stone kerb on the back and right edge (stairs stepping down to the water)
    s.decorate(3, 1, 3, 'lily_pad')
    for x, z in ((1, 4), (5, 4), (5, 1)):                          # stone slabs on the corners of the edge
        put_id(s, x, 1, z, 'mossy_cobblestone_slab' if x == 1 else 'stone_brick_slab', **SLAB)
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
        if (x, 1, z) in s.blocks:
            continue
        if (x + z) % 2 == 1:
            put_id(s, x, 1, z, _drift(x, z, quad(x, z), d(x, z), c))
    lawn_under(s, 2)

    s.at(3, 'flourish3')
    # the sundial in the centre bed, flowers round its foot
    put_id(s, c, 2, c, 'stone_brick_wall')
    put_id(s, c, 3, c, 'daylight_detector', inverted='false', power='0')
    for x, z in sorted(centre_bed):
        if (x, 1, z) not in s.blocks:
            put_id(s, x, 1, z, 'lily_of_the_valley' if (x == c or z == c) else 'pink_petals',
                   **({} if (x == c or z == c) else dict(facing=_away(x - c, z - c), flower_amount='4')))
    # lanterns: beside the side benches, hanging in the arbor and the entrance arch, hanging from the tree branches
    for x in (0, e):
        for z in (c - 2, c + 2):
            put_id(s, x, 2, z, 'lantern', **LANTERN)
    put_id(s, c, 2, e - 2, 'lantern', **HANGING)                   # under the arbor
    put_id(s, c, 2, 0, 'lantern', **HANGING)                       # under the entrance arch
    for x in (c - 1, c + 1):                                       # vines hanging from the arbor roof
        put_id(s, x, 2, e - 2, 'vine', up='true', north='false', south='false', east='false', west='false')
    for tx, tz in trees:                                           # a lantern under each crown, over the walk
        for lx, lz in ((tx + (2 if tx < c else -2), tz - 2), (tx, tz - 3)):
            if (lx, 4, lz) not in s.blocks and (lx, 5, lz) in s.blocks and (lx, 3, lz) not in s.blocks:
                put_id(s, lx, 4, lz, 'lantern', **HANGING)
                break
    put_id(s, trees[0][0], 3, trees[0][1] - 1, 'bee_nest', facing='north', honey_level='0')   # a bee nest on the birch
    # the pond: a second lily pad, ferns and petals by the water
    s.decorate(2, 1, 2, 'lily_pad')
    for x, z in ((6, 1), (6, 3), (1, 5), (3, 5)):
        if (x, 1, z) not in s.blocks and (x, 0, z) not in s.blocks:
            put_id(s, x, 1, z, 'fern')
    put_id(s, 6, 1, 2, 'blue_orchid')
    # densest flowers: tall flowers at the back of each border, the rest filled
    for x, z in sorted(borders):
        if (x, 1, z) in s.blocks:
            continue
        if d(x, z) > 5.0 and (x + z) % 2 == 0 and (x, 2, z) not in s.blocks:
            tall(s, x, z, TALL[(x + 2 * z) % len(TALL)])
        else:
            put_id(s, x, 1, z, _drift(x, z, quad(x, z), d(x, z), c, shift=1))
    # wild flowers in the lawn along the hedge and round the tree roots
    lawn = [(x, z) for x in range(1, e) for z in range(1, e) if (x, 0, z) not in s.blocks and (x, 1, z) not in s.blocks
            and (x, z) not in pond]
    for x, z in lawn:
        if noise(x, 1, z, 5) < 0.6:
            put_id(s, x, 1, z, WILD[(x + 2 * z) % len(WILD)])
        elif noise(x, 1, z, 6) < 0.5:
            put_id(s, x, 1, z, 'pink_petals', facing=_away(x - c, z - c), flower_amount=str(2 + (x + z) % 3))
    lawn_under(s, 3)
    return connect(s)


def tree(s, x, z, log, wood, leaves, seed=0):
    """A broad shade tree, 8 tall: a root flare of wood blocks, a trunk 5 high, four branches climbing out to the
    crown, and a wide layered crown with a flat underside (y 5 to 8, about 9 across) whose edge is ragged by noise."""
    for y in range(2, 7):                                          # the trunk, up into the crown
        put_id(s, x, y, z, log, axis='y')
    for dx, dz in ((-1, 0), (0, 1), (1, 0), (0, -1)):              # root flare: roots lying on the lawn
        if noise(x + dx, 1, z + dz, seed) < 0.6:
            put_id(s, x + dx, 1, z + dz, log, axis='x' if dx else 'z')
    put_id(s, x, 1, z, wood, axis='y')                             # bark all round at the foot
    branches = ((1, 1), (-1, 1), (1, -1), (-1, -1))
    for bx, bz in branches:                                        # diagonal branches: one out at y 4, two out at y 5
        put_id(s, x + bx, 4, z + bz, wood, axis='y')               # bark on every side: no sawn log ends showing
        put_id(s, x + 2 * bx, 5, z + 2 * bz, wood, axis='y')
    out = 1 if x < s.size[0] // 2 else -1                          # towards the middle of the garden
    for i in (1, 2, 3):                                            # long branches reaching over the walk and bench
        put_id(s, x, 5, z - i, wood, axis='y')
        if i < 3:
            s.add(x + out * i, 5, z, wood, axis='y')
    for y, r in ((4, 4.3), (5, 3.6), (6, 4.6), (7, 4.0), (8, 2.9)):
        for dx in range(-5, 6):
            for dz in range(-5, 6):
                px, pz = x + dx, z + dz
                if not (0 <= px < s.size[0] and 0 <= pz < s.size[2]) or (px, y, pz) in s.blocks:
                    continue
                dist = math.hypot(dx, dz * 1.05)
                if y == 4:                                         # a few leaves hanging under the crown's edge
                    if not (3.0 < dist <= r and noise(px, y, pz, seed + 7) < 0.3):
                        continue
                elif dist > r or (dist > r - 1.3 and noise(px, y, pz, seed) < 0.45):
                    continue
                put_id(s, px, y, pz, leaves, **LEAVES)


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


def _drift(x, z, q, dist, c, shift=0):
    """The flower kind of a border cell: clumps along the border (by angle), so colours come in drifts."""
    kinds = DRIFTS[q]
    a = math.atan2(z - c, x - c)
    band = int((a + math.pi) / (math.pi / 8))                       # 16 slices round the garden
    return kinds[(band + shift + (1 if dist > 4.8 else 0)) % len(kinds)]


STRUCTURES = [dict(path='garden', name='Garden (level 1)', tier=2, zone='inner', role='civic', group='garden',
                   terrain='flexible', door=None, make=garden, ground_layer=0,
                   changes='For your note: the birch and the cherry are now big shade trees with proper trunks, '
                           'root flares, branches and wide layered crowns that spread over the side benches, the back '
                           'of the round walk and the arbor. Two blocks wider (17 x 17). More flowers: blue orchids '
                           'added, the borders reach further, and level 3 fills the lawn along the hedge with wild '
                           'flowers and petals. New lily pond in the front left corner: 3 x 2 of water with lily '
                           'pads, a mossy stone edge, ferns and orchids. Water and lily pads are added by the '
                           'decoration step (builders cannot place them).',
                   description='A 17 x 17 cottage garden set into the lawn: a low hedge, gravel paths and a round walk '
                               'round a sundial bed, curved flower borders and a small lily pond; later levels add two '
                               'big shade trees, azaleas, an arbor, benches, lanterns and dense mixed flowers.')]

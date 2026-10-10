"""Fire pit (tier 1), revised from Carlton's notes: l1 "not much in terms of detail, and it may not be a good idea as
soldiers can burn themselves"; l2 "see l1". Decision (already made): the fire is an UNLIT campfire (lit=false), so
nothing burns; the light comes from a lantern."""
from make_batch1_structures import Structure
import math

from revise1.blockconn import connect, noise, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')
LEAVES = dict(distance='1', persistent='true', waterlogged='false')
TRUNK = (1, 7)                                                     # the shade tree's foot, back left, between benches


def clear_of_fire(x, z, c):
    """No leaf or branch over the fire ring (the 3 x 3 round the campfire) or the spit: the fire stays open to the sky."""
    return max(abs(x - c), abs(z - c)) >= 2


def young_tree(s):
    """Level 2: a young oak at the back left, between the left and back benches: a trunk 4 high with a loose tuft of
    leaves round its top (it grows into level 3's shade tree)."""
    tx, tz = TRUNK
    for y in range(0, 4):
        put_id(s, tx, y, tz, 'oak_log', axis='y')
    for x, y, z in ((tx, 4, tz), (tx - 1, 4, tz), (tx, 4, tz + 1), (tx - 1, 3, tz), (tx, 3, tz + 1),
                    (tx + 1, 4, tz + 1), (tx, 5, tz), (tx - 1, 3, tz + 1)):
        put_id(s, x, y, z, 'oak_leaves', **LEAVES)


def shade_tree(s, c):
    """Level 3: the oak has grown. The trunk bends forward out of the young tree's tuft and rises to 6; one branch
    reaches forward over the left bench, one right over the back bench. The crown is three clumps (over each branch
    tip and over the trunk), not one ball, with leaves hanging under their edges; the fire ring stays open to the sky.
    A lantern hangs from each branch, over the benches."""
    tx, tz = TRUNK
    put_id(s, tx + 1, 3, tz, 'oak_wood', axis='y')                # the bend: bark all round, no sawn ends; every
    put_id(s, tx + 1, 4, tz, 'oak_wood', axis='y')                # trunk and branch block joins the next by a face
    put_id(s, tx + 1, 4, tz - 1, 'oak_wood', axis='y')
    put_id(s, tx + 1, 5, tz, 'oak_wood', axis='y')                # the back branch's joint
    for y in (5, 6):
        put_id(s, tx + 1, y, tz - 1, 'oak_log', axis='y')
    put_id(s, tx, 5, tz - 1, 'oak_wood', axis='y')                # branch forward over the left bench
    put_id(s, tx, 5, tz - 2, 'oak_log', axis='z')
    put_id(s, tx, 5, tz - 3, 'oak_wood', axis='y')                # its tip (bark, the lantern hangs from it)
    put_id(s, tx + 2, 5, tz, 'oak_wood', axis='y')                # branch right over the back bench
    put_id(s, tx + 3, 5, tz, 'oak_wood', axis='y')                # its tip
    s.put(tx, 4, tz - 3, 'lantern', hanging='true', waterlogged='false')
    s.put(tx + 3, 4, tz, 'lantern', hanging='true', waterlogged='false')
    # the clumps: (centre x, centre z, lowest y, radius per layer from the lowest), ragged by noise at the edge
    clumps = ((tx - 0.1, tz - 3.2, 5, (1.9, 1.5)),                 # over the left bench
              (tx + 3.3, tz + 0.3, 5, (1.9, 1.4)),                 # over the back bench
              (tx + 1.0, tz - 0.8, 6, (2.6, 1.7)))                 # over the trunk, the top
    cells = {}
    for cx, cz, y0, radii in clumps:
        for i, r in enumerate(radii):
            y = y0 + i
            for x in range(s.size[0]):
                for z in range(s.size[2]):
                    dist = math.hypot(x - cx, z - cz)
                    if dist <= r and not (dist > r - 0.9 and noise(x, y, z, 12) < 0.4):
                        cells[(x, y, z)] = True
                    elif y == y0 and r < dist <= r + 0.8 and noise(x, 4, z, 11) < 0.3:
                        cells[(x, 4, z)] = True                   # a leaf hanging under the clump's edge
    for (x, y, z) in sorted(cells):
        if (x, y, z) not in s.blocks and clear_of_fire(x, z, c):
            put_id(s, x, y, z, 'oak_leaves', **LEAVES)


def fire_pit(size=9):
    """9 x 9 around a fire in the middle (x 4, z 4), open at the front (z 0).
    Level 1: an unlit campfire in a ring of stone (cobblestone slabs at the corners, cobblestone stairs front and back,
    cobblestone and mossy cobblestone at the sides) with a cooking spit (two spruce fence posts, a chain bar, a chain
    hook), three spruce log benches, a spruce woodpile, a chopping stump, a barrel and a chest, a joined stepping-stone
    path in from the front, poppies and leaf bushes round the edge and a lantern post.
    Level 2: a cauldron pot, a second lantern post, cushions on the benches, a potted flower, grass and ferns, and a
    young oak between the left and back benches.
    Level 3: the oak grown into a hand-built shade tree (a bent trunk, a branch over the left bench and one over the
    back bench, a crown of three clumps, lanterns hanging from the branches) that shades both benches and never covers
    the fire ring; a firewood shelter over the woodpile (dark oak posts and slab roof) with a lantern under it."""
    s = Structure('fire_pit', (size, 8, size))
    c = size // 2                                                   # 4
    s.at(1, 'shell')
    for x in (c - 1, c + 1):                                        # ring corners: low slabs, so the fire shows
        for z in (c - 1, c + 1):
            s.put(x, 0, z, 'cobblestone_slab', type='bottom', waterlogged='false')
    s.put(c, 0, c - 1, 'cobblestone_stairs', facing='north', **STAIR)   # front: the step faces the fire
    s.put(c, 0, c + 1, 'cobblestone_stairs', facing='south', **STAIR)
    s.put(c - 1, 0, c, 'mossy_cobblestone')                         # sides carry the spit
    s.put(c + 1, 0, c, 'cobblestone')
    for z in range(c - 1, c + 2):                                   # log benches (dark spruce, like the timber
        s.put(1, 0, z, 'spruce_log', axis='z')                      # elsewhere)
        s.put(size - 2, 0, z, 'spruce_log', axis='z')
    for x in range(c - 1, c + 2):
        s.put(x, 0, size - 2, 'spruce_log', axis='x')
    s.at(1, 'fittings')
    s.put(c, 0, c, 'campfire', facing='north', lit='false', signal_fire='false', waterlogged='false')   # never lit
    for x in (c - 1, c + 1):                                        # the cooking spit: two posts, a bar, a hook
        s.put(x, 1, c, 'spruce_fence'); s.put(x, 2, c, 'spruce_fence')
    s.put(c, 2, c, 'chain', axis='x', waterlogged='false')
    s.put(c, 1, c, 'chain', axis='y', waterlogged='false')
    s.put(0, 0, size - 1, 'spruce_fence'); s.put(0, 1, size - 1, 'spruce_fence')   # lantern post, back left
    s.put(0, 2, size - 1, 'lantern', **LANTERN)
    for x in (size - 2, size - 1):                                  # woodpile, back right
        s.put(x, 0, size - 1, 'spruce_log', axis='z')
    s.put(size - 1, 0, size - 2, 'spruce_log', axis='z')
    s.put(size - 1, 1, size - 1, 'spruce_log', axis='z')
    s.put(1, 0, 1, 'oak_log', axis='y')                             # chopping stump, front left
    s.put(1, 1, 1, 'oak_pressure_plate')
    s.put(0, 0, c - 2, 'barrel', facing='up', open='false')         # supplies by the left bench
    s.put(0, 0, c - 1, 'chest', facing='east', type='single', waterlogged='false')
    for z in range(0, c - 1):                                       # a joined stepping-stone path in from the
        s.put(c, 0, z, 'cobblestone_slab', type='bottom', waterlogged='false')   # front to the ring's step
    for x, z in ((c - 1, 0), (c + 1, 0)):                           # wider stones at the path's mouth
        s.put(x, 0, z, 'cobblestone_slab', type='bottom', waterlogged='false')
    for x, z in ((2, 1), (size - 3, 0), (0, c + 2), (size - 1, c + 1), (c + 2, 0)):
        s.put(x, 0, z, 'poppy')
    for x, z in ((0, size - 2), (size - 1, c - 1), (3, size - 1)):  # low bushes round the edge
        s.put(x, 0, z, 'oak_leaves', distance='1', persistent='true', waterlogged='false')

    s.at(2, 'flourish2')
    s.put(c + 2, 0, c - 2, 'cauldron')                              # the stew pot, front right of the ring
    s.put(size - 1, 0, 0, 'spruce_fence'); s.put(size - 1, 1, 0, 'spruce_fence')   # second lantern post, front right
    s.put(size - 1, 2, 0, 'lantern', **LANTERN)
    for z in range(c - 1, c + 2):                                   # cushions on the benches
        s.put(1, 1, z, 'red_carpet' if z == c else 'brown_carpet')
        s.put(size - 2, 1, z, 'red_carpet' if z == c else 'brown_carpet')
    s.put(c, 1, size - 2, 'red_carpet')
    s.put(0, 1, c - 2, 'flower_pot')
    s.decorate(0, 1, c - 2, 'potted_plant', plant='poppy')
    for x, z, plant in ((0, 1, 'short_grass'), (size - 1, c, 'fern'), (2, size - 1, 'short_grass'),
                        (size - 1, 2, 'dandelion'), (0, c + 1, 'short_grass')):
        s.put(x, 0, z, plant)
    young_tree(s)

    s.at(3, 'flourish3')
    shade_tree(s, c)
    # a firewood shelter over the woodpile: dark oak posts and a dark oak slab roof, a lantern hanging under it
    for z in (size - 2, size - 1):
        put_id(s, size - 3, 0, z, 'dark_oak_fence'); put_id(s, size - 3, 1, z, 'dark_oak_fence')
    for x in range(size - 3, size):
        for z in (size - 2, size - 1):
            put_id(s, x, 2, z, 'dark_oak_slab', type='bottom', waterlogged='false')
    s.put(size - 2, 1, size - 2, 'lantern', hanging='true', waterlogged='false')
    return connect(s)


STRUCTURES = [dict(path='fire_pit', name='Fire pit', tier=1, zone='inner', role='decoration', group='fire_pit',
                   terrain='flexible', door=None, make=fire_pit,
                   description='An unlit campfire (it never burns) in a stone ring with a cooking spit, three spruce '
                               'log benches, a woodpile, a chopping stump, supplies, poppies and a lantern post; level 2 '
                               'a pot, a second lantern, cushions, plants and a young oak; level 3 the oak grown into a '
                               'shade tree over the benches (lanterns hang from its branches) and a dark oak firewood '
                               'shelter.',
                   changes='For your note (custom shade tree): a hand-built oak now grows between the left and the '
                           'back bench. Level 2 has it as a young tree (a trunk with a tuft of leaves); on level 3 it '
                           'has grown: the trunk bends forward, one branch reaches over the left bench and one over '
                           'the back bench, and the crown is three ragged clumps (not one ball) that overhang and '
                           'shade both benches, with a lantern hanging from each branch. The fire ring stays open to '
                           'the sky (no leaves over the campfire, which stays unlit). Every trunk and branch block joins the next by a face. Level 1 is unchanged.')]

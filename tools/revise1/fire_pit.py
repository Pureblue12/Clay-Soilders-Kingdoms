"""Fire pit (tier 1), revised from Carlton's notes: l1 "not much in terms of detail, and it may not be a good idea as
soldiers can burn themselves"; l2 "see l1". Decision (already made): the fire is an UNLIT campfire (lit=false), so
nothing burns; the light comes from a lantern."""
from make_batch1_structures import Structure
from revise1.blockconn import connect, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')


def fire_pit(size=9):
    """9 x 9 around a fire in the middle (x 4, z 4), open at the front (z 0).
    Level 1: an unlit campfire in a ring of stone: cobblestone slabs at the corners, cobblestone stairs front and
    back (the low step towards the fire), cobblestone and mossy cobblestone at the sides carrying a cooking spit (two
    spruce fence posts, a chain bar across and a chain hook over the fire). Three log benches (stripped oak) on the left, right and back, and a lantern
    on a spruce fence post.
    Level 2: a cauldron pot by the fire, a woodpile, a chopping stump, gravel stepping stones in from the front, a
    second lantern post.
    Level 3: cushions (carpet) on the benches, candles on the woodpile, a barrel with a flower pot, grass tufts."""
    s = Structure('fire_pit', (size, 3, size))
    c = size // 2                                                   # 4
    s.at(1, 'shell')
    for x in (c - 1, c + 1):                                        # ring corners: low slabs, so the fire shows
        for z in (c - 1, c + 1):
            s.put(x, 0, z, 'cobblestone_slab', type='bottom', waterlogged='false')
    s.put(c, 0, c - 1, 'cobblestone_stairs', facing='north', **STAIR)   # front: the step faces the fire
    s.put(c, 0, c + 1, 'cobblestone_stairs', facing='south', **STAIR)
    s.put(c - 1, 0, c, 'mossy_cobblestone')                         # sides carry the spit
    s.put(c + 1, 0, c, 'cobblestone')
    for z in range(c - 1, c + 2):                                   # log benches
        s.put(1, 0, z, 'stripped_oak_log', axis='z')
        s.put(size - 2, 0, z, 'stripped_oak_log', axis='z')
    for x in range(c - 1, c + 2):
        s.put(x, 0, size - 2, 'stripped_oak_log', axis='x')
    s.at(1, 'fittings')
    s.put(c, 0, c, 'campfire', facing='north', lit='false', signal_fire='false', waterlogged='false')   # never lit
    for x in (c - 1, c + 1):                                        # the cooking spit: two posts, a bar, a hook
        s.put(x, 1, c, 'spruce_fence'); s.put(x, 2, c, 'spruce_fence')
    s.put(c, 2, c, 'chain', axis='x', waterlogged='false')
    s.put(c, 1, c, 'chain', axis='y', waterlogged='false')
    s.put(0, 0, size - 1, 'spruce_fence'); s.put(0, 1, size - 1, 'spruce_fence')   # lantern post, back left
    s.put(0, 2, size - 1, 'lantern', **LANTERN)

    s.at(2, 'flourish2')
    s.put(c + 2, 0, c - 2, 'cauldron')                              # the stew pot, front right of the ring
    for x in (size - 2, size - 1):                                  # woodpile, back right
        s.put(x, 0, size - 1, 'oak_log', axis='z')
    s.put(size - 1, 0, size - 2, 'oak_log', axis='z')
    s.put(size - 1, 1, size - 1, 'oak_log', axis='z')
    s.put(1, 0, 1, 'oak_log', axis='y')                             # chopping stump, front left
    s.put(1, 1, 1, 'oak_pressure_plate')
    for z in (0, 2):                                                # stepping stones in from the front
        put_id(s, c, 0, z, 'stone_slab', type='bottom', waterlogged='false')
    s.put(size - 1, 0, 0, 'spruce_fence'); s.put(size - 1, 1, 0, 'spruce_fence')   # second lantern post, front right
    s.put(size - 1, 2, 0, 'lantern', **LANTERN)

    s.at(3, 'flourish3')
    for z in range(c - 1, c + 2):                                   # cushions on the side benches
        s.put(1, 1, z, 'red_carpet' if z == c else 'brown_carpet')
        s.put(size - 2, 1, z, 'red_carpet' if z == c else 'brown_carpet')
    s.put(c, 1, size - 2, 'red_carpet')
    s.put(size - 1, 2, size - 1, 'candle', candles='3', lit='false', waterlogged='false')   # on the woodpile, unlit
    s.put(0, 0, c - 2, 'barrel', facing='up', open='false')
    s.put(0, 1, c - 2, 'flower_pot')
    s.decorate(0, 1, c - 2, 'potted_plant', plant='poppy')
    for x, z, plant in ((0, 1, 'short_grass'), (size - 1, c, 'fern'), (2, size - 1, 'short_grass'),
                        (size - 1, 2, 'dandelion'), (0, c + 1, 'short_grass')):
        s.put(x, 0, z, plant)
    return connect(s)


STRUCTURES = [dict(path='fire_pit', name='Fire pit', tier=1, zone='inner', role='decoration', group='fire_pit',
                   terrain='flexible', door=None, make=fire_pit,
                   description='An unlit campfire (it never burns) in a ring of cobblestone slabs and steps with a '
                               'cooking spit, three log benches and a lantern post; level 2 a pot, a woodpile, a stump '
                               'and stepping stones; level 3 cushions, candles and plants.',
                   changes='A proper fire pit: a stone ring with a cooking spit, three log benches and a lantern for '
                           'light; the campfire stays unlit (lit=false) so soldiers cannot burn themselves.')]

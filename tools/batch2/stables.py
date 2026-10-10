"""Pens and stables (tier 2, look A): a fenced paddock with a gateway in front of an open-sided stable with stalls."""
from make_batch1_structures import Structure

SLAB = dict(waterlogged='false')
OPEN = dict(half='bottom', open='true', powered='false', waterlogged='false')


def stables(width=9, depth=11, stable_depth=5):
    """Pens and stables. The paddock (front) is a fence ring with a gateway at the front centre: two spruce log
    gateposts and a lintel over a three-wide opening (soldiers can't open fence gates, so the middle stays an open gap;
    level 2 hangs two fence gates standing open in it as decoration). The stable (back) stands on log posts over
    cobblestone plinths, with a plank back wall, log beams, a coarse dirt floor and a steep spruce stair gable roof
    (dark oak eaves) that overhangs by one.
    Each level adds a function: L1 the stalls (fence divider, hay, feed bin) and a feed trough in the paddock; L2 the
    hay loft (hay under the ridge, a hoisted bale at the gable), a tack corner under the east eave (saddles on a
    rack, tack barrels) and a water trough; L3 a farrier corner (anvil, forge, quench barrel), a weathervane on the
    ridge, banners, lanterns and flower boxes."""
    s = Structure('stables', (width, 9, depth), palette='oak_a2')
    w, d = width - 1, depth - 1
    mid = width // 2
    z0, z1 = d - stable_depth, d - 1               # stable front and back post rows (5, 9)
    xs = (1, mid, w - 1)                            # post columns
    zm = (z0 + z1) // 2                             # ridge row
    gate = (mid - 2, mid + 2)                       # gateposts (2, 6); the opening is mid-1 .. mid+1
    s.at(1, 'shell')
    for x in range(1, w):                           # cobblestone plinth under the back wall
        s.put(x, 0, z1, 'base')
    for x in xs:
        s.put(x, 0, z0, 'base')
        for z in (z0, z1):
            for y in (1, 2, 3):
                s.put(x, y, z, 'frame', axis='y')
    for x in range(1, w):                           # stable floor: coarse dirt between the plinths
        for z in range(z0, z1):
            s.add(x, 0, z, 'path')
    for x in range(2, w - 1):
        if x in xs:
            continue
        s.put(x, 1, z1, 'wall'); s.put(x, 2, z1, 'wall')          # back wall
        s.put(x, 3, z0, 'frame', axis='x'); s.put(x, 3, z1, 'frame', axis='x')   # beams
    for x in (1, w - 1):
        for z in range(z0 + 1, z1):
            s.put(x, 3, z, 'frame', axis='z')
    # roof: a steep stair gable, ridge along x, dark oak eaves, spruce slopes, dark ridge, one block of overhang
    for x in range(0, width):
        for z in range(z0 - 1, z1 + 2):
            k = (z1 - z0) // 2 + 1 - abs(z - zm)          # 0 at the eaves, highest at the ridge
            if z == zm:
                s.put(x, 4 + k, z, 'dark')
            else:
                s.put(x, 4 + k, z, 'dark_stairs' if k == 0 else 'roof_stairs', facing='south' if z < zm else 'north',
                      half='bottom', shape='straight', waterlogged='false')
    for x in (1, w - 1):                            # plank gable ends; the loft opening (y 4) stays open until L2
        for y, zs in ((4, (z0, z1)), (5, (zm - 1, zm + 1)), (6, (zm,))):
            for z in zs:
                s.put(x, y, z, 'wall')
    for x in range(2, w - 1):                       # boards under the back eaves
        s.put(x, 4, z1, 'plank2')
    # paddock: fence ring from the front to the stable's front posts, a gateway in the middle of the front
    for x in range(0, width):
        if x < gate[0] or x > gate[1]:
            s.put(x, 0, 0, 'fence')
    for x in gate:                                  # gateposts and lintel
        for y in (0, 1):
            s.put(x, y, 0, 'stake', axis='y')
    for x in range(gate[0], gate[1] + 1):           # two high is plenty for half-block soldiers
        s.put(x, 2, 0, 'stripped_spruce_log', axis='x')
    for z in range(1, z0 + 1):
        s.put(0, 0, z, 'fence'); s.put(w, 0, z, 'fence')
    s.at(1, 'fittings')
    for z in range(z0 + 1, z1):                    # side rails of the stable
        s.put(1, 1, z, 'fence'); s.put(w - 1, 1, z, 'fence')
    for z in (z0 + 1, z0 + 2):                     # stall divider
        s.put(mid, 1, z, 'fence')
    s.put(2, 1, z1 - 1, 'hay_block', axis='y'); s.put(w - 2, 1, z1 - 1, 'hay_block', axis='y')   # hay in each stall
    s.put(mid, 1, z1 - 1, 'chest', facing='north', type='single', waterlogged='false')          # feed bin
    for x in range(2, w - 1):                      # loft floor under the ridge
        if x in xs:
            s.put(x, 3, zm, 'frame', axis='z')
        else:
            s.put(x, 3, zm, 'plank2')
    for z in (2, 3):                               # feed trough along the west fence (composter bins), a bale beside
        s.put(1, 0, z, 'composter')
    s.put(1, 0, 4, 'hay_block', axis='y')
    for x in (1, w - 1):                           # torches on the front posts, facing the paddock
        s.put(x, 2, z0 - 1, 'wall_torch', facing='north')
    s.at(2, 'flourish2')
    for x in (mid - 1, mid + 1):                   # paddock gates, standing open (decoration: soldiers use the gap)
        s.put(x, 0, 0, 'spruce_fence_gate', facing='south', open='true', in_wall='false', powered='false')
    s.put(mid, 3, 0, 'lantern', hanging='false', waterlogged='false')  # lantern on the lintel
    for x in range(2, w - 1):                      # hay loft: bales under the ridge, showing at both gable ends
        s.put(x, 4, zm, 'hay_block', axis='x')
    for x in (1, w - 1):
        for z in (zm - 1, zm, zm + 1):
            s.put(x, 4, z, 'hay_block', axis='z')
    s.put(w, 6, zm, 'spruce_fence')                # hoist arm at the east gable with a bale on its rope
    s.put(w, 5, zm, 'chain', axis='y')
    s.put(w, 4, zm, 'hay_block', axis='y')
    # tack corner under the east eave: saddles on a rack (carpet on fence posts), tack barrels, a bridle peg
    s.put(w, 0, z0 + 1, 'barrel', facing='up', open='false')
    for z in (z0 + 2, z0 + 3):
        s.put(w, 0, z, 'post')
        s.put(w, 1, z, 'brown_carpet' if z == z0 + 2 else 'red_carpet')
    s.put(w, 0, z1, 'barrel', facing='east', open='false')
    s.put(w, 2, z0 + 1, 'shelf', facing='east', **OPEN)     # pegs on the east rail
    for z in (2, 3):                               # water trough along the east fence (water: decoration step)
        s.put(w - 1, 0, z, 'cauldron')
        s.decorate(w - 1, 0, z, 'water_cauldron', note='fill the water trough')
    s.put(w - 1, 0, 4, 'hay_block', axis='y'); s.put(w - 2, 2, z1 - 1, 'hay_block', axis='z')   # more hay
    for x in (3, w - 3):                           # hatches on the back wall (open trapdoors against the planks)
        s.put(x, 2, d, 'trapdoor', facing='south', **OPEN)
    s.at(3, 'flourish3')
    # farrier corner at the front right of the paddock, by the water trough (quench): forge (smoker), anvil
    s.put(w - 2, 0, 1, 'smoker', facing='west', lit='false')
    s.put(w - 1, 0, 1, 'anvil', facing='north')
    s.put(w - 2, 1, 1, 'lantern', hanging='false', waterlogged='false')
    s.put(mid, 8, zm, 'lightning_rod', facing='up', powered='false', waterlogged='false')   # weathervane
    for x in (0, w):                               # finials at the ridge ends
        s.put(x, 8, zm, 'spruce_fence')
    s.put(mid, 3, z0 - 1, 'red_wall_banner', facing='north')    # banners: on the middle post and the east posts
    for z in (z0, z1):
        s.put(w, 3, z, 'red_wall_banner', facing='east')
    for x in (2, w - 2):                           # lanterns under the front eaves
        s.put(x, 3, z0 - 1, 'lantern', hanging='true', waterlogged='false')
    for x in (0, w):                               # lanterns on the paddock's front corner posts
        s.put(x, 1, 0, 'lantern', hanging='false', waterlogged='false')
    # flower box at the front left of the paddock: dirt with flowers, a trapdoor face; pots on the gate lintel
    for x, flower in ((1, 'poppy'), (2, 'dandelion')):
        s.put(x, 0, 1, 'dirt')
        s.put(x, 1, 1, flower)
    s.put(3, 0, 1, 'trapdoor', facing='east', **OPEN)
    for x, plant in ((gate[0], 'red_tulip'), (gate[1], 'azure_bluet')):
        s.put(x, 3, 0, 'flower_pot'); s.decorate(x, 3, 0, 'potted_plant', plant=plant)
    for x in (3, w - 3):                           # sills under the back hatches
        s.put(x, 1, d, 'trapdoor', facing='south', half='top', open='false', powered='false', waterlogged='false')
    for x in (0, w):                               # moss at the plinth ends, bushes against the stable sides
        s.add(x, 0, z1, 'moss_carpet')
        s.add(x, 0, zm, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    for x in (2, 4, w - 2):                        # bushes and a flower along the back wall
        s.put(x, 0, d, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    s.put(w - 4, 0, d, 'poppy')
    s.decorate(mid + 2, 1, z1 - 1, 'armor_stand', note='horse armor on display', item='minecraft:leather_horse_armor')
    return sink_stable_floor(s, range(1, w), range(z0, z1 + 1), z0 - 1)


def sink_stable_floor(s, xs, zs, paddock_end):
    """Carlton (v3 note): the stable's dirt floor should sit in the ground. The structure is saved with ground layer 0
    (layer 0 replaces the top ground block), so the stable (its floor, plinths and everything on them) keeps its blocks
    and its floor ends up level with the grass. Everything that stands on the grass outside the stable moves up one
    layer: the whole paddock in front (z < paddock_end), and elsewhere each column's stack from the ground up.
    Things hung on the stable (torches, lanterns, banners, hatches, the hoist) stay with it."""
    def outside(x, z):
        return x not in xs or z not in zs
    moved = set()
    for (x, y, z) in s.blocks:
        if not outside(x, z):
            continue
        if z < paddock_end:
            moved.add((x, y, z))
        elif y == 0:
            k = 0
            while (x, k, z) in s.blocks:
                moved.add((x, k, z)); k += 1
    blocks, layers = {}, {}
    for pos, b in s.blocks.items():
        x, y, z = pos
        new = (x, y + 1, z) if pos in moved else pos
        assert new not in blocks, f'stables: {new} taken twice after sinking the floor'
        blocks[new], layers[new] = b, s.layer_of[pos]
    s.blocks, s.layer_of = blocks, layers
    for decs in s.decorations.values():
        for d in decs:
            x, y, z = d['pos']
            if (x, y, z) in moved:
                d['pos'] = [x, y + 1, z]
    return s


STRUCTURES = [dict(path='stables', name='Pens and stables', tier=2, zone='outer', role='farm', group='stables',
                   terrain='flexible', door=None, make=stables, ground_layer=0,
                   changes='The stable\'s dirt floor now sits in the ground, level with the grass (nothing else changed: '
                           'the paddock and everything around it stand on the grass as before).',
                   description='Fenced paddock with a gateway (open gap; fence gates are decoration) before an open-sided '
                               'log stable with two stalls and a feed trough; L2 adds the hay loft, tack corner and water '
                               'trough, L3 a farrier corner, weathervane, banners and flower boxes. Wants level ground.')]

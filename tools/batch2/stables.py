"""Pens and stables (tier 2, look A): a fenced pen in front of an open-sided stable with stalls and a hay loft."""
from make_batch1_structures import Structure

SLAB = dict(waterlogged='false')


def stables(width=9, depth=11, stable_depth=5):
    """Pens and stables: the pen (front) is a fence ring with an opening at the front centre (fence gates are BROKEN
    for passage). The stable (back) stands on log posts over cobblestone plinths, a plank back wall, log beams, and a
    shallow spruce slab roof that overhangs by one; under the ridge a plank loft holds hay. Two stalls open onto
    the pen."""
    s = Structure('stables', (width, 6, depth), palette='oak_a2')
    w, d = width - 1, depth - 1
    mid = width // 2
    z0, z1 = d - stable_depth, d - 1               # stable front and back post rows (5, 9)
    xs = (1, mid, w - 1)                            # post columns
    zm = (z0 + z1) // 2                             # ridge row
    s.at(1, 'shell')
    for x in range(1, w):                           # cobblestone plinth under the back wall
        s.put(x, 0, z1, 'base')
    for x in xs:
        s.put(x, 0, z0, 'base')
        for z in (z0, z1):
            for y in (1, 2, 3):
                s.put(x, y, z, 'frame', axis='y')
    for x in range(2, w - 1):
        if x in xs:
            continue
        s.put(x, 1, z1, 'wall'); s.put(x, 2, z1, 'wall')          # back wall
        s.put(x, 3, z0, 'frame', axis='x'); s.put(x, 3, z1, 'frame', axis='x')   # beams
    for x in (1, w - 1):
        for z in range(z0 + 1, z1):
            s.put(x, 3, z, 'frame', axis='z')
    # roof: slabs rising half a block per row to the ridge, one row of overhang at the front, back and sides
    for z in range(z0 - 1, z1 + 2):
        k = (z1 - z0) // 2 + 1 - abs(z - zm)          # 0 at the eaves, highest at the ridge
        y, half = 4 + k // 2, ('top' if k % 2 else 'bottom')
        for x in range(0, width):
            s.put(x, y, z, 'roof_slab', type=half, **SLAB)
    # pen: fence ring from the front to the stable's front posts, opening at the front centre
    for x in range(0, width):
        if x != mid:
            s.put(x, 0, 0, 'fence')
    for z in range(1, z0 + 1):
        s.put(0, 0, z, 'fence'); s.put(w, 0, z, 'fence')
    s.at(1, 'fittings')
    for z in range(z0 + 1, z1):                    # side rails of the stable
        s.put(1, 1, z, 'fence'); s.put(w - 1, 1, z, 'fence')
    for z in (z0 + 1, z0 + 2):                     # stall divider
        s.put(mid, 1, z, 'fence')
    s.put(2, 1, z1 - 1, 'hay_block', axis='y'); s.put(w - 2, 1, z1 - 1, 'hay_block', axis='y')
    s.put(mid, 1, z1 - 1, 'chest', facing='north', type='single', waterlogged='false')
    for x in range(2, w - 1):                      # hay loft under the ridge
        if x in xs:
            s.put(x, 3, zm, 'frame', axis='z')
        else:
            s.put(x, 3, zm, 'plank2')
        s.put(x, 4, zm, 'hay_block', axis='x')
    for x in (1, w - 1):                           # loft hay over the side beams, seen from both ends
        for z in (zm - 1, zm, zm + 1):
            s.put(x, 4, z, 'hay_block', axis='z')
    for x in (1, w - 1):                           # torches on the front posts, facing the pen
        s.put(x, 2, z0 - 1, 'wall_torch', facing='north')
    s.at(2, 'flourish2')
    s.put(2, 0, 2, 'cauldron')                     # water trough (water: decoration step)
    s.decorate(2, 0, 2, 'water_cauldron', note='fill the trough')
    for x in (mid - 1, mid + 1):                   # lanterns on the posts beside the pen opening
        s.put(x, 1, 0, 'lantern', hanging='false', waterlogged='false')
    s.put(mid, 2, z0 + 1, 'lantern', hanging='true', waterlogged='false')    # under the beam, between the stalls
    # a hand cart in the pen: slab bed on trapdoor wheels, a hay bale load, a fence shaft
    for x in (w - 2, w - 1):
        s.put(x, 0, 3, 'roof_slab', type='top', **SLAB)
        s.put(x, 0, 2, 'trapdoor', facing='north', half='bottom', open='true', powered='false', waterlogged='false')
        s.put(x, 0, 4, 'trapdoor', facing='south', half='bottom', open='true', powered='false', waterlogged='false')
    s.put(w - 1, 1, 3, 'hay_block', axis='x')
    s.put(w - 3, 0, 3, 'fence')
    s.put(w - 2, 2, z1 - 1, 'hay_block', axis='z')  # second bale on the stall hay
    for x in (3, w - 3):                           # hatches on the back wall (open trapdoors against the planks)
        s.put(x, 2, d, 'trapdoor', facing='south', half='bottom', open='true', powered='false', waterlogged='false')
    s.at(3, 'flourish3')
    s.decorate(mid + 2, 1, z1 - 1, 'armor_stand', note='horse armor on display', item='minecraft:leather_horse_armor')
    for x in (1, 2, 3, w - 3, w - 2, w - 1):       # flowers along the pen front, inside the fence
        s.put(x, 0, 1, 'poppy' if x % 2 else 'dandelion')
    for x in (0, w):                               # moss at the plinth ends
        s.put(x, 0, z1, 'moss_carpet')
    s.put(1, 0, 2, 'hay_block', axis='y')           # feed bale beside the trough
    return s


STRUCTURES = [dict(path='stables', name='Pens and stables', tier=2, zone='outer', role='farm', group='stables',
                   terrain='flexible', door=None, make=stables,
                   description='Fenced pen (front opening, no gate) before an open-sided log stable with two stalls, a '
                               'shallow spruce slab roof and a hay loft under the ridge; the stable wants level ground.')]

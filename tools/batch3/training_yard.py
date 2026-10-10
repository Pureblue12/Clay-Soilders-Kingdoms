"""Training yard, tier 3 (docs/STRUCTURES.md): a fenced gravel yard where recruits drill.

Plan (x 0..8, z 0..9; z = 0 is the front): a cobblestone curb with an oak fence on it encloses a gravel and coarse
dirt floor laid on the ground (soldiers stand at y 1). The way in is a stone step and a gap in the middle of the
front fence. Left half: the melee dummies (a fence post with a hay body). Right half: the archery lane, a shooting
line at the front and hay butts at the back. A bench and the weapon store by the entrance; from level 3 the drill
master's shelter in the back left corner.
"""
from make_batch1_structures import Structure
from revise1.blockconn import connect, noise, put_id

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
SLAB = dict(type='bottom', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')
OPEN = dict(half='bottom', open='true', powered='false', waterlogged='false')


def training_yard():
    W, D = 11, 11
    s = Structure('training_yard', (W, 5, D), palette='oak_a2')
    x0, x1, z0, z1 = 0, W - 1, 1, D - 1                    # curb ring (z 1..10)
    mid = W // 2                                           # 5: the drill path from the entrance to the flag
    dummies = [(2, 4), (3, 6)]                             # melee dummies, staggered
    s.at(1, 'shell')
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                put_id(s, x, 0, z, 'mossy_cobblestone' if noise(x, 0, z, 5) < 0.25 else 'cobblestone')
            elif x == mid:                                 # the drill path: stone bricks
                put_id(s, x, 0, z, 'stone_bricks')
            elif x < mid:                                  # the sparring ground: trampled earth, one solid floor
                put_id(s, x, 0, z, 'coarse_dirt')
            elif z == 3:                                   # the shooting line across the archery side
                put_id(s, x, 0, z, 'stone_bricks')
            elif 4 <= z <= z1 - 2 and x in (mid + 1, x1 - 1):   # the lane, edged with stone bricks
                put_id(s, x, 0, z, 'stone_bricks')
            else:
                put_id(s, x, 0, z, 'gravel')
    posts = {(x0, z0), (x1, z0), (x0, z1), (x1, z1), (mid, z1), (mid - 1, z0), (mid + 1, z0), (x0, 6), (x1, 6)}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x in (x0, x1) or z in (z0, z1)) and (x, z) != (mid, z0):
                if (x, z) in posts:
                    put_id(s, x, 1, z, 'spruce_log', axis='y'); put_id(s, x, 2, z, 'spruce_log', axis='y')
                else:
                    put_id(s, x, 1, z, 'oak_fence')
    put_id(s, mid, 0, 0, 'cobblestone_stairs', facing='south', **STAIR)   # the step in
    s.at(1, 'fittings')
    for x, z in posts - {(mid, z1), (mid - 1, z0), (mid + 1, z0)}:   # torches on the corner and side posts
        put_id(s, x, 3, z, 'torch')
    for x, z in dummies:                                    # melee dummies: a post, a hay body and fence arms
        put_id(s, x, 1, z, 'oak_fence'); put_id(s, x, 2, z, 'hay_block', axis='y')
        put_id(s, x - 1, 2, z, 'oak_fence'); put_id(s, x + 1, 2, z, 'oak_fence')
    for x in range(mid + 1, x1):                            # the archery butts at the end of the lane
        put_id(s, x, 1, z1 - 1, 'hay_block', axis='x')
    for x in (1, 2):                                        # weapon rack: barrels with a rail of spear shafts
        put_id(s, x, 1, 2, 'barrel', facing='up', open='false')
        put_id(s, x, 2, 2, 'spruce_fence')
    for x in (3, 4):                                        # bench by the entrance, facing the dummies
        put_id(s, x, 1, 2, 'spruce_stairs', facing='north', **STAIR)
    put_id(s, x1 - 1, 1, 2, 'chest', facing='west', type='single', waterlogged='false')   # practice weapons
    put_id(s, mid + 1, 1, 2, 'barrel', facing='up', open='false')                        # arrows
    s.at(2, 'flourish2')
    for x, z in dummies:                                    # dummies get heads
        put_id(s, x, 3, z, 'carved_pumpkin', facing='north')
    for x in (mid + 2, mid + 3):                            # targets on the butts
        put_id(s, x, 2, z1 - 1, 'target', power='0')
    for x in range(mid + 1, x1):                            # the shooting line, marked in red
        put_id(s, x, 1, 3, 'red_carpet')
    for x in (mid - 1, mid + 1):                            # lanterns on the entrance posts
        put_id(s, x, 3, z0, 'lantern', **LANTERN)
    put_id(s, mid, 3, z1, 'red_banner', rotation='0')
    s.decorate(mid, 3, z1, 'banner_pattern', banner='red_banner', note='kingdom colours')
    put_id(s, mid + 1, 2, 2, 'barrel', facing='up', open='false')             # more arrows
    s.decorate(mid + 1, 3, 2, 'item', item='minecraft:arrow', note='arrows in the barrel')
    put_id(s, mid - 1, 1, z1 - 1, 'barrel', facing='up', open='true')          # water barrel at the end of the path
    for x in (1, 2):
        s.decorate(x, 2, 2, 'item', item='minecraft:wooden_sword', note='practice swords leaning in the rack')
    s.at(3, 'flourish3')
    # the drill master's shelter in the back left corner: four fence posts, a spruce slab roof, a lectern for the roll
    for x in (1, 3):
        put_id(s, x, 1, 8, 'spruce_fence'); put_id(s, x, 2, 8, 'spruce_fence')
    put_id(s, 3, 1, 9, 'spruce_fence'); put_id(s, 3, 2, 9, 'spruce_fence')
    put_id(s, 1, 1, 9, 'spruce_stairs', facing='east', **STAIR); put_id(s, 1, 2, 9, 'spruce_fence')
    for x in (1, 2, 3):
        for z in (7, 8, 9):
            put_id(s, x, 3, z, 'spruce_slab', **SLAB)
    put_id(s, 2, 1, 9, 'lectern', facing='north', has_book='false', powered='false')
    put_id(s, 2, 2, 8, 'lantern', hanging='true', waterlogged='false')
    for x, z in dummies:                                    # armour on the dummies, a sword in the chest
        s.decorate(x, 1, z, 'armor_stand', note='leather armour on the dummy')
    s.decorate(x1 - 1, 1, 2, 'item_frame', item='minecraft:wooden_sword', facing='west', note='practice sword')
    for x in (1, 2, 3, 7, 8, 9):                            # flowers along the front, outside the fence
        put_id(s, x, 0, 0, ('poppy', 'dandelion', 'cornflower')[x % 3])
    put_id(s, 0, 0, 0, 'hay_block', axis='y'); put_id(s, W - 1, 0, 0, 'barrel', facing='up', open='false')
    for x in (mid + 1, x1 - 1):                             # lane posts at the shooting line, with lanterns
        put_id(s, x, 1, 4, 'spruce_fence'); put_id(s, x, 2, 4, 'lantern', **LANTERN)
    s.decorate(mid + 2, 2, z1 - 1, 'item', item='minecraft:arrow', note='arrows stuck in the targets')
    return connect(s)


STRUCTURES = [dict(path='training_yard', name='Training yard', tier=3, zone='inner', role='military',
                   group='training_yard', terrain='flexible', door='front', changes='First version.', make=training_yard,
                   description='Over budget: 186 blocks vs 85 (first build 134 vs 60; the curb and floor are most of it). Fenced gravel drill yard (9 x 10): melee dummies on the left, an archery lane with hay '
                               'butts on the right, a bench and weapon store by the entrance; targets, dummy heads and '
                               'lanterns from L2, the drill master\'s shelter and flowers from L3.')]

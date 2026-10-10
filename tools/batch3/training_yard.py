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
    W, D = 9, 10
    s = Structure('training_yard', (W, 5, D), palette='oak_a2')
    x0, x1, z0, z1 = 0, W - 1, 1, D - 1                    # curb ring
    mid = W // 2
    dummies = [(2, 3), (2, 5)]
    s.at(1, 'shell')
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                put_id(s, x, 0, z, 'mossy_cobblestone' if noise(x, 0, z, 5) < 0.25 else 'cobblestone')
            else:                                          # the drill floor: gravel worn into coarse dirt
                put_id(s, x, 0, z, 'coarse_dirt' if noise(x, 0, z, 9) < 0.35 else 'gravel')
    corners = {(x0, z0), (x1, z0), (x0, z1), (x1, z1), (mid, z1)}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x in (x0, x1) or z in (z0, z1)) and (x, z) != (mid, z0):
                if (x, z) in corners:
                    put_id(s, x, 1, z, 'spruce_log', axis='y'); put_id(s, x, 2, z, 'spruce_log', axis='y')
                else:
                    put_id(s, x, 1, z, 'oak_fence')
    put_id(s, mid, 0, 0, 'cobblestone_stairs', facing='south', **STAIR)   # the step in
    s.at(1, 'fittings')
    for x, z in corners - {(mid, z1)}:                      # torches on the corner posts
        put_id(s, x, 3, z, 'torch')
    for x, z in dummies:                                    # melee dummies: a post and a hay body
        put_id(s, x, 1, z, 'oak_fence'); put_id(s, x, 2, z, 'hay_block', axis='y')
    for x in (5, 6, 7):                                     # the archery butts at the back
        put_id(s, x, 1, z1 - 1, 'hay_block', axis='x')
    put_id(s, 6, 2, z1 - 1, 'hay_block', axis='x')
    for z in (2, 3):                                        # bench against the left fence
        put_id(s, 1, 1, z, 'spruce_stairs', facing='west', **STAIR)
    put_id(s, 7, 1, 2, 'chest', facing='west', type='single', waterlogged='false')   # practice weapons
    put_id(s, 7, 1, 3, 'barrel', facing='up', open='false')
    s.at(2, 'flourish2')
    for x, z in dummies:                                    # dummies get heads and arms
        put_id(s, x, 3, z, 'carved_pumpkin', facing='north')
        put_id(s, x - 1, 2, z, 'oak_fence'); put_id(s, x + 1, 2, z, 'oak_fence')
    for x in (5, 7):                                        # targets on the butts
        put_id(s, x, 2, z1 - 1, 'target', power='0')
    for x in (5, 6, 7):                                     # the shooting line
        put_id(s, x, 1, 4, 'red_carpet')
    for x in (mid - 1, mid + 1):                            # lanterns on raised posts at the entrance
        put_id(s, x, 2, z0, 'oak_fence'); put_id(s, x, 3, z0, 'lantern', **LANTERN)
    put_id(s, mid, 3, z1, 'red_banner', rotation='0')
    s.decorate(mid, 3, z1, 'banner_pattern', banner='red_banner', note='kingdom colours')
    put_id(s, 7, 2, 3, 'barrel', facing='up', open='false')                   # more arrows
    s.decorate(7, 3, 3, 'item', item='minecraft:arrow', note='arrows in the barrel')
    put_id(s, 1, 1, 4, 'barrel', facing='up', open='true')                    # water barrel by the bench
    s.at(3, 'flourish3')
    # the drill master's shelter in the back left corner: two log posts, a spruce slab roof, a lectern for the roll
    for x in (1, 3):
        put_id(s, x, 1, 6, 'spruce_fence'); put_id(s, x, 2, 6, 'spruce_fence')
    for x in (1, 2, 3):
        for z in (6, 7, 8):
            put_id(s, x, 3, z, 'spruce_slab', **SLAB)
    put_id(s, 2, 1, 8, 'lectern', facing='north', has_book='false', powered='false')
    put_id(s, 1, 1, 8, 'spruce_stairs', facing='east', **STAIR)
    put_id(s, 2, 2, 7, 'lantern', hanging='true', waterlogged='false')
    for x, z in dummies:                                    # armour on the dummies, a sword in the barrel
        s.decorate(x, 1, z, 'armor_stand', note='leather armour on the dummy')
    s.decorate(7, 1, 2, 'item_frame', item='minecraft:wooden_sword', facing='west', note='practice sword')
    for x in (1, 2, 3, 5, 6, 7):                            # flowers along the front, outside the fence
        if x != mid:
            put_id(s, x, 0, 0, 'coarse_dirt')
            put_id(s, x, 1, 0, ('poppy', 'dandelion', 'cornflower')[x % 3])
    put_id(s, 0, 0, 0, 'hay_block', axis='y'); put_id(s, W - 1, 0, 0, 'barrel', facing='up', open='false')
    for x, z in ((3, 2), (6, 6), (4, 7)):                   # a worn patch of moss
        put_id(s, x, 1, z, 'moss_carpet')
    put_id(s, 6, 3, z1 - 1, 'hay_block', axis='y')          # the butt topped up
    return connect(s)


STRUCTURES = [dict(path='training_yard', name='Training yard', tier=3, zone='inner', role='military',
                   group='training_yard', terrain='flexible', door='front', changes='First version.', make=training_yard,
                   description='Over budget: 186 blocks vs 85 (first build 134 vs 60; the curb and floor are most of it). Fenced gravel drill yard (9 x 10): melee dummies on the left, an archery lane with hay '
                               'butts on the right, a bench and weapon store by the entrance; targets, dummy heads and '
                               'lanterns from L2, the drill master\'s shelter and flowers from L3.')]

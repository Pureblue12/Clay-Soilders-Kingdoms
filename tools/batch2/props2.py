"""Small tier-2 props: notice board and hitching post (docs/STRUCTURES.md, "Notice board", "Hitching post").
Layer y = 0 stands on the ground (the mod builds layer 0 on top of the ground block)."""
from make_batch1_structures import Structure


def notice_board(width=4):
    """Notice board (quality check round 2: was a 2-wide board on fence posts over a raised gravel pad, too small and
    floating). Two dark oak log posts carry a spruce plank board under a little spruce gable roof; level 2 pins two oak
    wall signs on it (text by the decoration step), hangs a lantern under the roof and adds a barrel and flowers."""
    s = Structure('notice_board', (width, 4, 2), palette='oak_a2')
    w = width - 1
    s.at(1, 'shell')
    for x in (0, w):                                              # posts
        for y in (0, 1, 2):
            s.put(x, y, 1, 'dark_frame', axis='y')
    for x in range(1, w):                                         # the board
        for y in (1, 2):
            s.put(x, y, 1, 'plank2')
    for x in range(width):                                        # gable cap over the board
        s.put(x, 3, 0, 'dark_stairs', facing='south', half='bottom', shape='straight', waterlogged='false')
        s.put(x, 3, 1, 'dark_stairs', facing='north', half='bottom', shape='straight', waterlogged='false')
    s.at(1, 'fittings')                                           # round 3: one notice, a lamp and a bush from level 1
    s.put(w - 1, 2, 0, 'oak_wall_sign', facing='north', waterlogged='false')
    s.decorate(w - 1, 2, 0, 'sign_text', lines=['Notices', '', 'by order of', 'the king'])
    s.put(w, 2, 0, 'lantern', hanging='true', waterlogged='false')
    s.put(w - 1, 0, 0, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    s.at(2, 'flourish2')
    notices = (['Hear ye!', 'Builders', 'wanted', ''], ['Market', 'day', 'at the', 'commons'])
    for i, (x, y) in enumerate(((1, 2), (1, 1))):
        s.put(x, y, 0, 'oak_wall_sign', facing='north', waterlogged='false')
        s.decorate(x, y, 0, 'sign_text', lines=notices[i])
    s.put(0, 2, 0, 'lantern', hanging='true', waterlogged='false')
    s.put(w, 0, 0, 'barrel', facing='up', open='false')
    s.put(1, 0, 0, 'poppy')
    s.put(0, 0, 0, 'flower_pot'); s.decorate(0, 0, 0, 'potted_plant', plant='dandelion')
    return s


def hitching_post(length=5):
    """Hitching post (quality check round 2: was a 3-block fence on a raised dirt pad). Two dark oak log posts with an
    oak fence rail between them at tying height, a lantern on the right post and hay stacked behind the left one.
    Level 2 adds the trough (a cauldron; water by the decoration step), a barrel, a bush and flowers."""
    s = Structure('hitching_post', (length, 3, 3), palette='oak_a2')
    w = length - 1
    s.at(1, 'shell')
    for x in (0, w):
        s.put(x, 0, 1, 'dark_frame', axis='y'); s.put(x, 1, 1, 'dark_frame', axis='y')
    for x in range(1, w):                                         # rail (the driver joins it to the posts)
        s.put(x, 1, 1, 'fence')
    s.at(1, 'fittings')
    s.put(w, 2, 1, 'lantern', hanging='false', waterlogged='false')
    s.put(0, 0, 2, 'hay_block', axis='x'); s.put(0, 1, 2, 'hay_block', axis='z')
    s.put(2, 0, 1, 'chain', axis='y')                             # round 3: a tether hanging from the rail, a bush
    s.put(0, 0, 0, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    s.at(2, 'flourish2')
    s.put(w - 1, 0, 0, 'cauldron')
    s.decorate(w - 1, 0, 0, 'water_cauldron', level=3, note='fill the trough (water_cauldron is BROKEN for builders)')
    s.put(1, 0, 2, 'barrel', facing='up', open='false')
    s.put(w, 0, 2, 'oak_leaves', persistent='true', distance='1', waterlogged='false')
    s.put(2, 0, 2, 'poppy'); s.put(3, 0, 2, 'dandelion')
    s.put(0, 2, 1, 'lantern', hanging='false', waterlogged='false')
    return s


STRUCTURES = [
    dict(path='notice_board', name='Notice board', tier=2, zone='inner', role='decoration', group='notice_board',
         terrain='flexible', door=None, make=notice_board,
         description='Two dark oak posts with a spruce plank board under a small gable roof; level 2 pins two signs, a lantern, a barrel and flowers.'),
    dict(path='hitching_post', name='Hitching post', tier=2, zone='inner', role='decoration', group='hitching_post',
         terrain='flexible', door=None, make=hitching_post,
         description='Two dark oak posts and a fence rail with a lantern and stacked hay; level 2 adds a trough, barrel, bush and flowers.'),
]

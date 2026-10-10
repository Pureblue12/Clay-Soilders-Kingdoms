"""Small tier-2 props: notice board and hitching post (docs/STRUCTURES.md, "Notice board", "Hitching post").
Layer y = 0 is level with the ground: a small trampled pad replaces the top ground block where people stand, the prop
stands on the ground at y >= 1 (same convention as the commons and the garden)."""
from make_batch1_structures import Structure


def rail(x, length, north=False):
    """A fence state joined to its neighbours in a row along x (the game would join them anyway; the Studio shows
    exactly the stored state)."""
    return dict(east=str(x < length - 1).lower(), west=str(x > 0).lower(), north=str(north).lower(), south='false',
                waterlogged='false')


def notice_board(width=2):
    """Notice board: two fence posts at the back carrying a board of open spruce trapdoors, a gravel pad in front
    where readers stand. Level 2 pins two oak wall signs on the board (text by the decoration step); a wall sign holds
    on an open trapdoor because vanilla counts its full-height shape as solid."""
    s = Structure('notice_board', (width, 3, 2), palette='oak_a2')
    s.at(1, 'shell')
    for x in range(width):
        s.put(x, 0, 0, 'gravel')                                  # readers' pad
        s.put(x, 1, 1, 'fence', **rail(x, width))                  # posts
    s.at(1, 'fittings')
    for x in range(width):                                        # the board: panels on the front face of the post row
        s.put(x, 2, 1, 'trapdoor', facing='south', half='bottom', open='true', powered='false', waterlogged='false')
    s.at(2, 'flourish2')
    notices = (['Hear ye!', 'Builders', 'wanted', ''], ['Market', 'day', 'at the', 'commons'])
    for x in range(width):
        s.put(x, 2, 0, 'oak_wall_sign', facing='north', waterlogged='false')
        s.decorate(x, 2, 0, 'sign_text', lines=notices[x % len(notices)])
    return s


def hitching_post(length=3):
    """Hitching post: a fence rail at the back, trampled coarse dirt in front where mounts stand, a hay bale at the
    left end. Level 2 adds the trough at the right end (a cauldron; its water by the decoration step)."""
    s = Structure('hitching_post', (length, 2, 2), palette='oak_a2')
    s.at(1, 'shell')
    for x in range(length):
        s.put(x, 0, 0, 'soil')                                    # trampled ground
        s.put(x, 1, 1, 'fence', **rail(x, length, north=x == 0))   # the rail (joins the hay bale)
    s.at(1, 'fittings')
    s.put(0, 1, 0, 'hay_block', axis='x')
    s.at(2, 'flourish2')
    s.put(length - 1, 1, 0, 'cauldron')
    s.decorate(length - 1, 1, 0, 'water_cauldron', level=3, note='fill the trough (water_cauldron is BROKEN for builders)')
    return s


STRUCTURES = [
    dict(path='notice_board', name='Notice board', tier=2, zone='inner', role='decoration', group='notice_board',
         terrain='flexible', door=None, make=notice_board,
         description='Two fence posts with a spruce trapdoor board and a gravel pad; level 2 pins two signs on it.'),
    dict(path='hitching_post', name='Hitching post', tier=2, zone='inner', role='decoration', group='hitching_post',
         terrain='flexible', door=None, make=hitching_post,
         description='A fence rail with a hay bale on trampled ground; level 2 adds a cauldron trough.'),
]

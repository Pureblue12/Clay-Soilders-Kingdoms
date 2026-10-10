"""Fishing dock (tier 2, look A): an oak jetty on log pilings with a small net hut at its shore end."""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', shape='straight', waterlogged='false')


def fishing_dock(length=9, piling_every=4):
    """Fishing dock: a 3-wide plank jetty (x 0..2) running from the shore (front, z = 0) out to the fishing spot
    (back), oak log pilings at its edges; a 3 x 4 spruce hut (walls 3 high on the bank, earth floor) with a gable
    roof beside its shore end (x 3..5).
    Shown on flat ground: in the world the jetty's back part stands over water and its pilings go down to the bed
    (decoration step / terrain); y = 0 is the deck, level with the bank."""
    s = Structure('fishing_dock', (7, 5, length), palette='oak_a2')
    end = length - 1
    hx0, hx1, hz0, hz1 = 3, 5, 1, 4                 # hut walls
    hm = (hx0 + hx1) // 2
    s.at(1, 'shell')
    for z in range(length):                         # deck; pilings at the edges every few blocks and at the end
        for x in (0, 1, 2):
            piling = x != 1 and (z % piling_every == 0 or z == end)
            s.put(x, 0, z, 'frame' if piling else 'floor', **({'axis': 'y'} if piling else {}))
    for y in (0, 1, 2):                             # hut walls on the bank: log corners, spruce planks (earth floor)
        for x in range(hx0, hx1 + 1):
            for z in range(hz0, hz1 + 1):
                if x in (hx0, hx1) or z in (hz0, hz1):
                    corner = x in (hx0, hx1) and z in (hz0, hz1)
                    s.put(x, y, z, 'frame' if corner else 'plank2', **({'axis': 'y'} if corner else {}))
    s.remove(hm, 0, hz0); s.remove(hm, 1, hz0)
    s.door(hm, 0, hz0, 'north', block='spruce_door')
    s.remove(hx0, 1, hz0 + 2)                       # window towards the jetty
    for z in range(hz0 - 1, hz1 + 1):               # gable roof along the depth, overhanging the front and both sides
        s.put(hx0 - 1, 2, z, 'roof_stairs', facing='east', **STAIR)
        s.put(hx1 + 1, 2, z, 'roof_stairs', facing='west', **STAIR)
        s.put(hx0, 3, z, 'roof_stairs', facing='east', **STAIR)
        s.put(hx1, 3, z, 'roof_stairs', facing='west', **STAIR)
        s.put(hm, 4, z, 'roof_slab', type='bottom', waterlogged='false')
    for z in (hz0, hz1):                            # gable ends
        s.put(hm, 3, z, 'plank2')
    s.at(1, 'fittings')
    s.put(hx0, 1, hz0 + 2, 'window')
    s.put(hm, 0, hz1 - 1, 'chest', facing='north', type='single', waterlogged='false')
    for z in (hz0 + 1, hz0 + 2):                    # barrels under the east eave
        s.put(hx1 + 1, 0, z, 'barrel', facing='up', open='false')
    s.put(hx1 + 1, 0, hz1, 'barrel', facing='west', open='false')
    s.put(1, 1, end, 'chair', facing='north', **STAIR)   # the fishing seat, looking out
    for x in (0, 2):                                # torch posts at the jetty end
        s.put(x, 1, end, 'post'); s.put(x, 2, end, 'torch')
    s.at(2, 'flourish2')
    for z in (5, 7):                                # net frame on the jetty's west edge: two posts and a rail,
        s.put(0, 1, z, 'post'); s.put(0, 2, z, 'post')   # the nets (white carpets) drying over the top
    s.put(0, 2, 6, 'post')
    for z in (5, 6, 7):
        s.put(0, 3, z, 'white_carpet')
    s.put(2, 1, 6, 'post'); s.put(2, 2, 6, 'lantern', hanging='false', waterlogged='false')   # lamp post on the deck
    s.put(hm, 1, hz1 - 1, 'lantern', hanging='false', waterlogged='false')                  # on the chest
    s.put(hx1 + 1, 1, hz0 + 1, 'spruce_trapdoor', facing='west', half='bottom', open='false', powered='false',
          waterlogged='false')                      # lid on the barrel stack (a crate)
    s.at(3, 'flourish3')
    s.decorate(4, 0, 7, 'boat', note='oak boat moored beside the jetty, on the water', facing='south')
    for z in range(1, end, 2):
        s.decorate(1, -1, z, 'water', note='the jetty stands over water from the bank on; pilings to the bed (max 4)')
    s.put(2, 1, end - 1, 'barrel', facing='up', open='false')        # bait barrel at the end
    s.put(0, 1, 1, 'post'); s.put(0, 2, 1, 'lantern', hanging='false', waterlogged='false')   # shore lamp
    for x, z in ((2, 2), (1, 5)):                   # moss on the old planks
        s.put(x, 1, z, 'moss_carpet')
    return s


STRUCTURES = [dict(path='fishing_dock', name='Fishing dock', tier=2, zone='outer', role='food', group='fishing_dock',
                   terrain='flexible', door='front', make=fishing_dock,
                   description='Oak jetty on log pilings with a spruce net hut at the shore end and a fishing seat at the '
                               'far end; meant to run out over water (shown on flat ground, pilings at y 0).')]

"""Fishing dock (tier 2, look A): a fisher's house on the bank and an oak jetty on log pilings running out over the lake
(Carlton 2026-10-17: "a main building and then a dock that goes outward, and the guys can fish from it")."""
from make_batch1_structures import Structure

STAIR = dict(half='bottom', shape='straight', waterlogged='false')
SLAB = dict(type='bottom', waterlogged='false')
LANTERN = dict(hanging='false', waterlogged='false')


def fishing_dock():
    """Fishing dock: a 7 x 6 spruce fisher's house (log corners, walls 3 high, earth floor, gable roof) on the bank at the
    front (z 1..6, door toward the town), an open doorway at its back onto a 3-wide plank jetty (x 3..5) that runs out
    over the water (back). Pilings (oak logs) at the jetty's edges every 3 blocks.
    L1: house + 8-block jetty, torch posts at its end. L2: the jetty goes on to a T-head (7 wide) with drying nets, lamp
    posts and a smoker in the house. L3: a side landing with a moored boat, a flag at the T-head, bait barrels.
    Shown on flat ground: in the world y = 0 stands one above the lake's surface, the bank under the house and water under
    the jetty; the kingdom drives the pilings down to the lake bed when it prepares the site (the plan page)."""
    s = Structure('fishing_dock', (9, 7, 20), palette='oak_a2')
    x0, x1, z0, z1 = 1, 7, 1, 6                     # house walls
    m = 4                                           # middle (door, ridge, jetty centre)
    jx0, jx1 = 3, 5                                 # jetty edges

    def jetty(z_from, z_to, xa=jx0, xb=jx1, pilings=()):
        for z in range(z_from, z_to + 1):
            for x in range(xa, xb + 1):
                edge = x in (xa, xb)
                if edge and z in pilings:
                    s.put(x, 0, z, 'frame', axis='y')
                else:
                    s.put(x, 0, z, 'floor')

    s.at(1, 'shell')
    for y in (0, 1, 2):                             # house walls: log corners, spruce planks
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    corner = x in (x0, x1) and z in (z0, z1)
                    s.put(x, y, z, 'frame' if corner else 'plank2', **({'axis': 'y'} if corner else {}))
    for y in (0, 1):
        s.remove(m, y, z0)                          # front door (toward the town)
        s.remove(m, y, z1)                          # back doorway onto the jetty, no door
    s.door(m, 0, z0, 'north', block='spruce_door')
    for x in (2, 6):                                # window holes: front, both sides
        s.remove(x, 1, z0)
    for z in (3, 4):
        s.remove(x0, 1, z); s.remove(x1, 1, z)
    for z in range(z0 - 1, z1 + 2):                 # gable roof along the depth, overhanging front and back
        for i in range(4):                          # x 0..3 rising east, x 8..5 rising west
            s.put(i, 2 + i, z, 'roof_stairs', facing='east', **STAIR)
            s.put(8 - i, 2 + i, z, 'roof_stairs', facing='west', **STAIR)
        s.put(m, 6, z, 'roof_slab', **SLAB)
    for z in (z0, z1):                              # gable ends
        for y, xs in ((3, range(2, 7)), (4, range(3, 6)), (5, (4,))):
            for x in xs:
                s.put(x, y, z, 'plank2')
    jetty(z1 + 1, 14, pilings=(8, 11, 14))          # the jetty, 8 out from the house

    s.at(1, 'fittings')
    for x in (2, 6):
        s.put(x, 1, z0, 'window')
    for z in (3, 4):
        s.put(x0, 1, z, 'window'); s.put(x1, 1, z, 'window')
    s.put(2, 0, 5, 'chest', facing='east', type='single', waterlogged='false')   # the catch goes here
    s.put(6, 0, 5, 'barrel', facing='up', open='false')
    s.put(6, 0, 4, 'barrel', facing='up', open='false')
    s.put(2, 0, 2, 'crafting_table')
    for x in (jx0, jx1):                            # torch posts at the jetty's end
        s.put(x, 1, 14, 'post'); s.put(x, 2, 14, 'torch')

    s.at(2, 'flourish2')
    jetty(15, 17, pilings=(17,))                    # the jetty goes on ...
    jetty(16, 17, xa=1, xb=7, pilings=(16, 17))     # ... to a T-head: more room to fish
    for x in (1, 7):                                # lamp posts at the T-head's corners
        s.put(x, 1, 17, 'post'); s.put(x, 2, 17, 'lantern', **LANTERN)
    for x in (1, 2):                                # net frame on the T-head's west arm: nets drying on the rail
        s.put(x, 1, 16, 'post')
    s.put(1, 2, 16, 'post'); s.put(2, 2, 16, 'post')
    s.put(1, 3, 16, 'white_carpet'); s.put(2, 3, 16, 'white_carpet')
    s.put(6, 0, 2, 'smoker', facing='west', lit='false')   # smoking the catch
    s.put(6, 1, 4, 'lantern', **LANTERN)            # on the barrels
    s.put(4, 0, 3, 'rug2')                          # brown rug in the middle of the room
    for x in (x0 - 1, x1 + 1):                      # barrels under the eaves outside
        s.put(x, 0, 3, 'barrel', facing='up', open='false')

    s.at(3, 'flourish3')
    jetty(10, 12, xa=6, xb=8, pilings=(10, 12))     # side landing for the boat (east)
    s.decorate(7, 0, 13, 'boat', note='oak boat moored at the side landing, on the water', facing='south')
    s.put(8, 1, 11, 'post')                         # mooring post
    for y in (1, 2, 3):                             # flag pole at the T-head's east arm: blue cloth at the top
        s.put(6, y, 17, 'post')
    s.put(6, 4, 17, 'post'); s.put(5, 4, 17, 'cloth2')
    s.put(5, 1, 16, 'barrel', facing='up', open='false')   # bait barrel by the T-head
    s.put(3, 1, 9, 'barrel', facing='up', open='false')    # and one by the house
    s.put(2, 1, 5, 'lantern', **LANTERN)            # on the chest
    s.put(2, 0, 3, 'composter')                     # fish scraps
    for x, z in ((4, 9), (3, 13)):                  # moss on the old planks
        s.put(x, 1, z, 'moss_carpet')
    return s


STRUCTURES = [dict(path='fishing_dock', name='Fishing dock', tier=2, zone='outer', role='food', group='fishing_dock',
                   terrain='flexible', door='front', make=fishing_dock,
                   changes='Rebuilt from your new brief: a real fisher\'s house on the bank (7 x 6, door toward the town, '
                           'open doorway at the back) and a jetty going straight out from it over the water. L2 adds a '
                           'T-head with drying nets and lamps, L3 a side landing with a boat and a flag.',
                   description='Fisher\'s house on the bank with a plank jetty on log pilings going out over the lake; '
                               'soldiers fish from the jetty and the T-head. Shown on flat ground: in the world the jetty '
                               'stands over water and its pilings go down to the lake bed.')]

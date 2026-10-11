"""Mine, stage 2 (tier 5, look A, gold): the approved stage-1 adit with a headframe over a new shaft beside it."""
from make_batch1_structures import Structure
from batch3.ws_kit import connect
from batch6a import minekit as k

W, H, D = 19, 9, 11


def build(s, winch=False):
    """Everything of mine 2, level by level; mine 3 builds on it (winch=True leaves the rope's way open)."""
    k.copy_stage1(s)
    s.at(1, 'shell')
    k.headframe_l1(s, winch)
    s.at(1, 'fittings')
    k.fittings_l1(s)
    s.at(2, 'flourish2')
    k.headframe_l2(s)
    k.sorting_shed_l2(s)
    s.at(3, 'flourish3')
    k.gold_l3(s)


def mine2():
    """Level 1: the stage-1 mine (adit into a built spoil mound) and, beside it, the shaft: a stone collar round the
    open cell where miners dig down, a 9-tall headframe of dark oak legs with braces, a plank deck with a railing, the
    sheave (a grindstone wheel in a log frame) and the hoist chain down the shaft with its ore tub; a chest, pit props,
    torches.
    Level 2: a new job, ore sorting: a lean-to shed behind the headframe with a sorting bench, a washing tub and
    sorted heaps of ore; a ladder up the headframe, lanterns under the deck, a rail spur to the shaft with buffers
    (plus stage 1's level 2: track, hood, tool rack).
    Level 3: the gold: a stone lockup with an iron door, a chest of gold and a yellow banner, a guard's lamp post,
    banners flying from the headframe, a "Gold Mine" sign, flowers and moss (plus stage 1's level 3)."""
    s = Structure('mine2', (W, H, D), palette='oak_a2')
    build(s)
    return connect(s)


STRUCTURES = [dict(path='mine2', name='Mine (stage 2, gold)', tier=5, zone='outer', role='resource', group='mine',
                   terrain='flexible', door='front', make=mine2, changes='First version.',
                   description='Over budget: 542 blocks vs 170 (stage 1 alone is 280, mostly its spoil mound). Stage 2 of the mine (gold, tier 5): the approved stage-1 adit and spoil mound, with a '
                               '9-tall headframe over a new shaft beside it (stone collar, dark oak legs, deck, '
                               'sheave and hoist chain). L2 adds an ore-sorting shed, a ladder and a rail spur; L3 a '
                               'stone gold lockup with an iron door, banners and a sign. The stage-1 flowers that '
                               'stood on cobblestone now have dirt under them. Builders never dig: miners dig the '
                               'shaft down from the open cell in the collar.')]

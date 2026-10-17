package net.bumblebee.claysoldiers.kingdom.layout;

/**
 * What the planner knows about the land (made from a terrain scan in the game, by hand in tests). Columns outside the
 * scan or in unloaded chunks are unknown.
 */
public interface Ground {
    boolean known(int x, int z);

    /** The ground height. */
    int height(int x, int z);

    /** Water or lava on the column. */
    boolean wet(int x, int z);

    /** Logs or leaves above the ground. */
    boolean trees(int x, int z);

    /** How far below the ground the first open space (a cave) starts, or 0 when the ground is solid as far as was looked. */
    int hollow(int x, int z);
}

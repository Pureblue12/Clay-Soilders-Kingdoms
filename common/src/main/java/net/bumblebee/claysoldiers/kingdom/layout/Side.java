package net.bumblebee.claysoldiers.kingdom.layout;

/**
 * A compass side on the kingdom map, without the game's own direction class so the planner stays plain data. North is
 * toward negative z, east toward positive x (as in the game).
 */
public enum Side {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

    public final int dx;
    public final int dz;

    Side(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public Side opposite() {
        return values()[(ordinal() + 2) % 4];
    }

    /** The side a quarter turn clockwise (seen from above): north to east. */
    public Side clockwise() {
        return values()[(ordinal() + 1) % 4];
    }

    public Side counterClockwise() {
        return values()[(ordinal() + 3) % 4];
    }

    /** The side a point at offset ({@code dx}, {@code dz}) from a center lies on (the larger offset wins; x on a tie). */
    public static Side toward(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? EAST : WEST;
        }
        return dz >= 0 ? SOUTH : NORTH;
    }

    public String lowerName() {
        return name().toLowerCase();
    }
}

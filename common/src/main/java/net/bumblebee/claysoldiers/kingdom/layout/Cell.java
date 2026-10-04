package net.bumblebee.claysoldiers.kingdom.layout;

import java.util.List;

/**
 * One column of the kingdom map (x, z), the unit of the layout checker. Heights don't matter here.
 */
public record Cell(int x, int z) {
    public List<Cell> neighbors() {
        return List.of(new Cell(x + 1, z), new Cell(x - 1, z), new Cell(x, z + 1), new Cell(x, z - 1));
    }

    @Override
    public String toString() {
        return "(" + x + ", " + z + ")";
    }
}

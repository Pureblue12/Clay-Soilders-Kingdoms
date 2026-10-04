package net.bumblebee.claysoldiers.structure;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Set;

/**
 * Compares a built block with the block a structure file asks for. Properties a block changes by itself when its
 * neighbours change (fence and wall connections, stair corner shapes, water-logging), or when it is used (a door or
 * trapdoor a soldier opened, a lit furnace), are not compared: builders place the file's state, and the game changes
 * these afterwards.
 */
public final class StructureFidelity {
    /** Block state properties neighbours change, ignored when comparing. */
    public static final Set<String> NEIGHBOUR_PROPERTIES = Set.of("north", "east", "south", "west", "up", "shape",
            "waterlogged", "distance", "bottom", "attached", "in_wall", "occupied", "powered", "triggered", "lit", "open");

    private StructureFidelity() {
    }

    /** Whether {@code actual} is the same block as {@code expected}, with the same state apart from neighbour properties. */
    public static boolean matches(BlockState expected, BlockState actual) {
        if (expected.getBlock() != actual.getBlock()) {
            return false;
        }
        for (Property<?> property : expected.getProperties()) {
            if (!NEIGHBOUR_PROPERTIES.contains(property.getName()) && !expected.getValue(property).equals(actual.getValue(property))) {
                return false;
            }
        }
        return true;
    }
}

package net.bumblebee.claysoldiers.kingdom;

import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Stage 6b (D119): where a quarry digs, worked out from the quarry building alone (pure). The pit is a square of
 * {@code size} behind the building (on the side away from its easel, so away from the town), {@code gap} blocks from it,
 * dug as stepped terraces: layer 1 is the whole square at ground level, every deeper layer one block lower and one block
 * smaller on every side. So no wall in the pit is more than one block high and a soldier walks out from anywhere; a pit of
 * 16 reaches 8 deep.
 *
 * @param topY the ground level of the building (layer 1)
 */
public record QuarryPit(int minX, int maxX, int minZ, int maxZ, int topY, int depth) {
    /** The pit behind a building standing in {@code bounds}, built from its easel going {@code facing}. */
    public static QuarryPit behind(BoundingBox bounds, Direction facing, int groundY, int size, int gap, int maxDepth) {
        int depth = Math.max(1, Math.min(maxDepth, (size + 1) / 2));
        int centerX = (bounds.minX() + bounds.maxX()) / 2;
        int centerZ = (bounds.minZ() + bounds.maxZ()) / 2;
        int minX;
        int minZ;
        switch (facing) {
            case NORTH -> {
                minX = centerX - size / 2;
                minZ = bounds.minZ() - gap - size;
            }
            case EAST -> {
                minX = bounds.maxX() + 1 + gap;
                minZ = centerZ - size / 2;
            }
            case WEST -> {
                minX = bounds.minX() - gap - size;
                minZ = centerZ - size / 2;
            }
            default -> {
                minX = centerX - size / 2;
                minZ = bounds.maxZ() + 1 + gap;
            }
        }
        return new QuarryPit(minX, minX + size - 1, minZ, minZ + size - 1, groundY, depth);
    }

    /** The height of layer {@code layer} (1 = ground level). */
    public int y(int layer) {
        return topY - layer + 1;
    }

    /** The layer at height {@code y}, or 0 when it is above or below the pit. */
    public int layerAt(int y) {
        int layer = topY - y + 1;
        return layer >= 1 && layer <= depth ? layer : 0;
    }

    /** Whether the column {@code x, z} is dug in layer {@code layer}: the square, made one smaller on every side per layer. */
    public boolean inLayer(int x, int z, int layer) {
        int inset = layer - 1;
        return layer >= 1 && layer <= depth && x >= minX + inset && x <= maxX - inset && z >= minZ + inset && z <= maxZ - inset;
    }

    /** Whether the block at {@code x, y, z} is part of the pit. */
    public boolean contains(int x, int y, int z) {
        return inLayer(x, z, layerAt(y));
    }
}

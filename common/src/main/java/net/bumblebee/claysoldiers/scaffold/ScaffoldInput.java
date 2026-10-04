package net.bumblebee.claysoldiers.scaffold;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the scaffold planner needs to know (pure data): the region it may use, which cells are already solid, which
 * cells scaffold may not use, and the structure blocks still to place, in build order. World coordinates.
 *
 * @param region    the cells the planner looks at (the structure plus a margin); builders arrive from its edge
 * @param solid     cells that are solid now (ground, built structure blocks, existing scaffold, other blocks)
 * @param blocked   cells scaffold must not use (the structure's own cells, the easel, anything not air)
 * @param structure structure blocks still to place, in build order
 * @param home      where builders come from (the easel), or null: from anywhere on the region's edge
 * @param maxPath   longest path (walked length; a hop up counts about 1.4) builders follow from home, or 0 for no limit
 */
public record ScaffoldInput(BoundingBox region, Set<BlockPos> solid, Set<BlockPos> blocked, List<BlockPos> structure,
                            @Nullable BlockPos home, int maxPath) {
    /** Blocks of margin around the structure: room for step pillars and the ramp. */
    public static final int MARGIN = 4;

    public ScaffoldInput(BoundingBox region, Set<BlockPos> solid, Set<BlockPos> blocked, List<BlockPos> structure) {
        this(region, solid, blocked, structure, null, 0);
    }

    public ScaffoldInput {
        solid = Set.copyOf(solid);
        blocked = Set.copyOf(blocked);
        structure = List.copyOf(structure);
    }

    /**
     * A structure on flat ground: the ground is the layer at {@code groundY} (the structure starts on top of it); the
     * structure's cells are blocked for scaffold.
     */
    public static ScaffoldInput flat(List<BlockPos> structure, int groundY) {
        BoundingBox box = BoundingBox.encapsulatingPositions(structure).orElseThrow();
        BoundingBox region = new BoundingBox(box.minX() - MARGIN, groundY, box.minZ() - MARGIN,
                box.maxX() + MARGIN, box.maxY() + 3, box.maxZ() + MARGIN);
        Set<BlockPos> ground = new HashSet<>();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                ground.add(new BlockPos(x, groundY, z));
            }
        }
        return new ScaffoldInput(region, ground, new HashSet<>(structure), structure);
    }
}

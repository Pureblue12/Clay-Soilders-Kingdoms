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
 * @param maxPath   longest path (walked length; a hop up counts about 1.4) builders follow from home to work on a block,
 *                  or 0 for no limit
 * @param columnPath longest path builders follow from home to a scaffold column's base (at least {@code maxPath}; once in
 *                  the column they climb, D75), or 0 for no limit
 * @param noFooting solid cells (now or once built) whose top can't hold a scaffolding column: stairs, slabs and carpet
 *                  floors; scaffolding put on them falls (D77)
 * @param overhead  solid cells (now or once built) whose shape doesn't reach down to the bottom of the cell: a hanging
 *                  lantern, a top slab, a closed top trapdoor. A soldier (under half a block tall) walks under them, so the
 *                  cell below stays a standing spot (D80; the church's side porch has a hanging lantern at head height)
 * @param columnReachBonus how many blocks more than {@link net.bumblebee.claysoldiers.entity.goal.workgoal.BuilderReach#MAX_PLACE_HEIGHT}
 *                  a builder inside a column reaches up; 0 except for the planner's second try when the usual reach leaves
 *                  blocks stuck (D78)
 */
public record ScaffoldInput(BoundingBox region, Set<BlockPos> solid, Set<BlockPos> blocked, List<BlockPos> structure,
                            @Nullable BlockPos home, int maxPath, int columnPath, Set<BlockPos> noFooting,
                            Set<BlockPos> overhead, int columnReachBonus) {
    /** Blocks of margin around the structure: room for step pillars and the ramp. */
    public static final int MARGIN = 4;

    public ScaffoldInput(BoundingBox region, Set<BlockPos> solid, Set<BlockPos> blocked, List<BlockPos> structure) {
        this(region, solid, blocked, structure, null, 0, 0, Set.of(), Set.of(), 0);
    }

    public ScaffoldInput(BoundingBox region, Set<BlockPos> solid, Set<BlockPos> blocked, List<BlockPos> structure,
                         @Nullable BlockPos home, int maxPath) {
        this(region, solid, blocked, structure, home, maxPath, maxPath, Set.of(), Set.of(), 0);
    }

    public ScaffoldInput {
        solid = Set.copyOf(solid);
        blocked = Set.copyOf(blocked);
        structure = List.copyOf(structure);
        noFooting = Set.copyOf(noFooting);
        overhead = Set.copyOf(overhead);
    }

    /** The same input where scaffolding can't stand on {@code cells} (tests). */
    public ScaffoldInput withNoFooting(Set<BlockPos> cells) {
        return new ScaffoldInput(region, solid, blocked, structure, home, maxPath, columnPath, cells, overhead, columnReachBonus);
    }

    /** The same input where soldiers walk under {@code cells} (tests). */
    public ScaffoldInput withOverhead(Set<BlockPos> cells) {
        return new ScaffoldInput(region, solid, blocked, structure, home, maxPath, columnPath, noFooting, cells, columnReachBonus);
    }

    /** The same input where a builder inside a column reaches {@code bonus} more blocks up (D78). */
    public ScaffoldInput withColumnReachBonus(int bonus) {
        return new ScaffoldInput(region, solid, blocked, structure, home, maxPath, columnPath, noFooting, overhead, bonus);
    }

    /**
     * The same input where builders walk up to {@code path} from home to a block or a column's base: the planner's last try
     * when the usual limits leave blocks stuck (D80).
     */
    public ScaffoldInput withPathLimit(int path) {
        return new ScaffoldInput(region, solid, blocked, structure, home, path, path, noFooting, overhead, columnReachBonus);
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

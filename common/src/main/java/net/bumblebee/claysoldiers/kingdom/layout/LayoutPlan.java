package net.bumblebee.claysoldiers.kingdom.layout;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A kingdom layout as plain data (no world access), checked by {@link LayoutChecker}.
 *
 * @param buildings building footprints
 * @param roads     the road network
 * @param wall      the wall loop, or {@code null} when the kingdom has no wall yet
 * @param center    a cell inside the kingdom (e.g. the keep's center), the start of the wall check
 */
public record LayoutPlan(List<Footprint> buildings, RoadGraph roads, @Nullable WallLoop wall, Cell center) {
    public LayoutPlan {
        buildings = List.copyOf(buildings);
    }
}

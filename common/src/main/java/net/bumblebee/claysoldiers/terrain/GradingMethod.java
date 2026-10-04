package net.bumblebee.claysoldiers.terrain;

import net.minecraft.util.StringRepresentable;

/**
 * Ways to prepare a keep site, compared in the terrain spike (stage 2). Applied instantly by the dev tool for review;
 * soldiers don't do any of this yet.
 */
public enum GradingMethod implements StringRepresentable {
    /** (a) No terrain changes: pick the flattest usable site nearby. */
    FLATTEST("flattest"),
    /** (b) Cut the high ground and fill the low ground to one level, within a budget. */
    CUT_FILL("cut_fill"),
    /** (c) A platform at the highest ground level, on pillars, for one-sided slopes. */
    FOUNDATION("foundation"),
    /** (d) Trees: clear every tree on the site. */
    CLEAR_TREES("clear_trees"),
    /** (d) Trees: work around them, picking the best usable site without trees nearby. */
    AVOID_TREES("avoid_trees");

    private final String id;

    GradingMethod(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public static GradingMethod byId(String id) {
        for (GradingMethod method : values()) {
            if (method.id.equals(id)) {
                return method;
            }
        }
        throw new IllegalArgumentException("unknown grading method " + id);
    }

    /** Whether this method moves the site instead of changing the terrain. */
    public boolean searches() {
        return this == FLATTEST || this == AVOID_TREES;
    }
}

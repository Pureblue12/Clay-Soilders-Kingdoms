package net.bumblebee.claysoldiers.scaffold;

import net.minecraft.util.StringRepresentable;

/**
 * Ways to give builders standing spots above their reach, compared in the tall construction spike (stage 3).
 */
public enum ScaffoldApproach implements StringRepresentable {
    /** (a) A ramp spiralling up the outside of the structure, 1 block higher per step. */
    OUTSIDE_RAMP("outside_ramp", true),
    /** (b) Step pillars: 2 by 2 spiral staircases beside the structure, each covering the columns within reach. */
    STEP_PILLARS("step_pillars", true),
    /** (c) The structure's own tops and floors as platforms; step pillars only where they don't reach. */
    PLATFORMS("platforms", true),
    /** (d) Vanilla scaffolding columns. Soldiers' walking navigation can't plan climbing, so they can't use them. */
    VANILLA_SCAFFOLDING("vanilla_scaffolding", false),
    /**
     * (e) Scaffolding columns beside the structure, climbed with the builders' own climbing behavior
     * ({@link ScaffoldClimber}): they walk to a column's base on the ground and rise inside it.
     */
    CLIMBED_COLUMNS("climbed_columns", true);

    private final String id;
    private final boolean usableBySoldiers;

    ScaffoldApproach(String id, boolean usableBySoldiers) {
        this.id = id;
        this.usableBySoldiers = usableBySoldiers;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public boolean usableBySoldiers() {
        return usableBySoldiers;
    }

    /** The approach builders use in game, by its number in the tuning file (1 a, 2 b, 3 c, 4 e). */
    public static ScaffoldApproach inGame(int number) {
        return switch (number) {
            case 1 -> OUTSIDE_RAMP;
            case 2 -> STEP_PILLARS;
            case 3 -> PLATFORMS;
            default -> CLIMBED_COLUMNS;
        };
    }

    /** Whether the scaffold is columns soldiers are inside (climbed), rather than steps they stand on. */
    public boolean columns() {
        return this == CLIMBED_COLUMNS || this == VANILLA_SCAFFOLDING;
    }
}

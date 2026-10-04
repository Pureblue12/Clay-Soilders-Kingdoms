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
    VANILLA_SCAFFOLDING("vanilla_scaffolding", false);

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

    /** The approach builders use in game, by its number in the tuning file (1 to 3: a, b, c). */
    public static ScaffoldApproach inGame(int number) {
        return switch (number) {
            case 1 -> OUTSIDE_RAMP;
            case 3 -> PLATFORMS;
            default -> STEP_PILLARS;
        };
    }
}

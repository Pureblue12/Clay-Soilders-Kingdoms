package net.bumblebee.claysoldiers.scaffold;

import java.util.Arrays;
import java.util.List;

/**
 * Plans temporary scaffolding so builders can reach every block of a structure taller than their reach (pure data).
 * Stage 3 skeleton: not implemented yet.
 */
public final class ScaffoldPlanner {
    private ScaffoldPlanner() {
    }

    public static ScaffoldPlan plan(ScaffoldInput input, ScaffoldApproach approach) {
        return new ScaffoldPlan(approach, List.of(), false, input.structure(), input.structure().size(), false, "not implemented");
    }

    /** Every approach for the same input, for comparison. */
    public static List<ScaffoldPlan> compare(ScaffoldInput input) {
        return Arrays.stream(ScaffoldApproach.values()).map(approach -> plan(input, approach)).toList();
    }
}

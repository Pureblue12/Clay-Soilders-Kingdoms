package net.bumblebee.claysoldiers.terrain;

/**
 * What a site score reason is about. The first group are hard refusals, the second soft penalties; {@link #UNKNOWN}
 * means part of the site isn't loaded, so it couldn't be judged.
 */
public enum Factor {
    UNKNOWN("not loaded"),
    CLIFF("cliff"),
    LAVA("lava"),
    DEEP_WATER("deep water"),
    TOO_STEEP("too steep"),
    HEIGHT_RANGE("uneven"),
    SLOPE("slope"),
    WATER("water"),
    TREES("trees"),
    STEPS("steps"),
    BIOME("biome");

    private final String label;

    Factor(String label) {
        this.label = label;
    }

    /** A short name for messages ("deep water"). */
    public String label() {
        return label;
    }
}

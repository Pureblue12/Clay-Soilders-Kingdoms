package net.bumblebee.claysoldiers.terrain;

/**
 * What the biome at a site means for a keep: {@link #WET} (ocean, river, beach) and {@link #MOUNTAIN} cost a soft
 * penalty ({@code terrain.penalty.biome}); everything else is {@link #NORMAL}.
 */
public enum BiomeKind {
    NORMAL,
    WET,
    MOUNTAIN
}

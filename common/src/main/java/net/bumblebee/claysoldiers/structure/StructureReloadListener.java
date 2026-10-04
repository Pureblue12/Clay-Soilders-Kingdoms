package net.bumblebee.claysoldiers.structure;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Reads the structure metadata files whenever data packs load (world start, {@code /reload}).
 */
public class StructureReloadListener implements ResourceManagerReloadListener {
    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        StructureRegistry.load(resourceManager);
    }
}

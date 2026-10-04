package net.bumblebee.claysoldiers.tuning;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Reads the tuning file whenever data packs load (world start, {@code /reload}).
 */
public class TuningReloadListener implements ResourceManagerReloadListener {
    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        Tuning.reload(resourceManager);
    }
}

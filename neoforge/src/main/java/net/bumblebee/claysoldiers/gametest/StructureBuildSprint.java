package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.minecraft.gametest.framework.GameTestServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * When the structure builds run ({@code CSR_STRUCTURE_BUILDS} set, see {@link StructureBuildGameTests}), the test server
 * runs its ticks as fast as it can, like vanilla's {@code /tick sprint}, instead of 20 a second: a build is measured in
 * ticks (timeouts, the stall limit, the result's tick count), so only the wall-clock time changes. The required suite
 * on its own ({@code tools/suite.sh}) keeps normal speed.
 */
@EventBusSubscriber(modid = ClaySoldiersCommon.MOD_ID)
public final class StructureBuildSprint {
    private StructureBuildSprint() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String setting = System.getenv("CSR_STRUCTURE_BUILDS");
        if (setting == null || setting.isBlank() || !(event.getServer() instanceof GameTestServer)) {
            return;
        }
        event.getServer().tickRateManager().requestGameToSprint(Integer.MAX_VALUE);
        ClaySoldiersCommon.LOGGER.info("CSR structure builds: test server ticks sprint (as fast as it can)");
    }
}

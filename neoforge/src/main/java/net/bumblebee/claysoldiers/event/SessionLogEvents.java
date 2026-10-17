package net.bumblebee.claysoldiers.event;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.SessionLog;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;

/**
 * Starts the always-on session log ({@link SessionLog}) when a world opens and closes it when the world closes.
 * Development only (registered in {@code ClaySoldiersNeoForge} when {@code PLATFORM.isDevEnv()}); never in a GameTest
 * server.
 */
public final class SessionLogEvents {
    private SessionLogEvents() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (server instanceof GameTestServer) {
            return;
        }
        try {
            SessionLog.start(server);
        } catch (IOException e) {
            ClaySoldiersCommon.LOGGER.error("CSR session log: could not start", e);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        SessionLog.tick(event.getServer());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        SessionLog.stop(event.getServer());
    }
}

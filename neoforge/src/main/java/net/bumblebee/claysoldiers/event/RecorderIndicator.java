package net.bumblebee.claysoldiers.event;

import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.debug.SessionLog;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Shows the debug recorder state on the action bar of operators (permission level 2), about once per second,
 * while recording, e.g. {@code ● REC BUILD WORK DAMAGE · 312 events}. Shows nothing when recording is off.
 * <p>
 * Development only: registered in {@code ClaySoldiersNeoForge} only when {@code PLATFORM.isDevEnv()}.
 */
public final class RecorderIndicator {
    /** Ticks between updates (20 ticks = 1 second). */
    public static final int INTERVAL = 20;

    private RecorderIndicator() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        // The always-on session log keeps every category on: no indicator to remind anyone to stop it.
        if (event.getServer().getTickCount() % INTERVAL != 0 || SessionLog.isOn()) {
            return;
        }
        Component message = message();
        if (message == null) {
            return;
        }
        sendTo(event.getServer(), message);
    }

    /**
     * The indicator, or {@code null} when recording is off.
     */
    @Nullable
    public static Component message() {
        String text = DebugLog.indicatorText();
        return text == null ? null : Component.literal(text).withStyle(ChatFormatting.RED);
    }

    private static void sendTo(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) {
                player.displayClientMessage(message, true);
            }
        }
    }
}

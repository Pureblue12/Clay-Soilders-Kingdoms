package net.bumblebee.claysoldiers.gametest;

import com.mojang.authlib.GameProfile;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.dev.DevBook;
import net.bumblebee.claysoldiers.dev.DevTool;
import net.bumblebee.claysoldiers.dev.DevToolRegistry;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.event.RecorderIndicator;
import net.bumblebee.claysoldiers.init.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;

/**
 * Checks that recorder commands give the player visible feedback. Commands run as a player through the same
 * path as a typed command or a book click on the server ({@code sendSuccess} -> {@code acceptsSuccess} ->
 * {@code sendSystemMessage}); the player captures the chat messages it receives.
 * Each test runs in its own batch because the recorder is global state.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class RecorderFeedbackGameTests {

    /**
     * Every book button of every recorder tool, run as a player, sends that player at least one chat message.
     */
    @GameTest(template = PLATFORM, batch = "recorder_feedback")
    public static void everyRecorderCommandGivesFeedback(GameTestHelper helper) {
        DebugLog.reset();
        MinecraftServer server = helper.getLevel().getServer();
        ChatCapturingPlayer player = new ChatCapturingPlayer(helper.getLevel());
        DevToolRegistry registry = DevToolRegistry.defaults();

        List<String> silent = new ArrayList<>();
        int checked = 0;
        for (DevTool tool : registry.tools()) {
            if (!tool.category().startsWith("recorder")) {
                continue;
            }
            for (DevToolRegistry.Button button : registry.buttons(tool, helper.getLevel().registryAccess())) {
                player.messages.clear();
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), button.command().substring(1));
                checked++;
                if (player.chatMessages().isEmpty()) {
                    silent.add(button.command());
                }
            }
        }
        DebugLog.reset();
        helper.assertTrue(checked > 0, "No recorder buttons found");
        helper.assertTrue(silent.isEmpty(), "Recorder commands that gave the player no chat feedback: " + silent);
        helper.succeed();
    }

    /**
     * Start prints "Recording is now ON (...)"; Stop + save prints one line with OFF, the event count, the duration
     * and the file path.
     */
    @GameTest(template = PLATFORM, batch = "recorder_stop_message")
    public static void startAndStopPrintStateLines(GameTestHelper helper) {
        DebugLog.reset();
        MinecraftServer server = helper.getLevel().getServer();
        ChatCapturingPlayer player = new ChatCapturingPlayer(helper.getLevel());

        server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), "csr dev log start");
        helper.assertTrue(player.chatMessages().size() == 1 && player.chatMessages().getFirst().startsWith("Recording is now ON (BUILD, WORK, DAMAGE)"),
                "Unexpected start feedback: " + player.chatMessages());

        for (int i = 0; i < 3; i++) {
            DebugLog.event(DebugCategory.BUILD, null, "test event %s", i);
        }
        player.messages.clear();
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), "csr dev log stop");
        String stop = player.chatMessages().isEmpty() ? "" : player.chatMessages().getFirst();
        DebugLog.reset();
        helper.assertTrue(player.chatMessages().size() == 1 && !stop.contains("\n"), "Stop should print exactly one line: " + player.chatMessages());
        helper.assertTrue(stop.startsWith("Recording is now OFF") && stop.contains("saved 3 events")
                        && stop.contains("recorded for") && stop.contains(DebugLog.FOLDER),
                "Stop line must have OFF, the count, the duration and the path: " + stop);
        helper.succeed();
    }

    /**
     * The action-bar indicator exists only while recording: categories and event count, plus the focus when set.
     */
    @GameTest(template = PLATFORM, batch = "recorder_indicator")
    public static void indicatorOnlyWhileRecording(GameTestHelper helper) {
        DebugLog.reset();
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(RecorderIndicator.message() == null, "Indicator shown while recording is off");

        DebugLog.start(server);
        DebugLog.event(DebugCategory.BUILD, null, "one");
        DebugLog.event(DebugCategory.WORK, null, "two");
        String text = RecorderIndicator.message() == null ? "" : RecorderIndicator.message().getString();
        helper.assertTrue(text.equals("● REC BUILD WORK DAMAGE · 2 events"), "Unexpected indicator: '" + text + "'");

        ClaySoldierEntity soldier = helper.spawn(ModEntityTypes.CLAY_SOLDIER_ENTITY.get(), new BlockPos(7, ClaySoldierGameTests.GROUND_Y, 7));
        DebugLog.focus(soldier);
        text = RecorderIndicator.message() == null ? "" : RecorderIndicator.message().getString();
        helper.assertTrue(text.endsWith("· focus: " + DebugLog.shortName(soldier)), "Indicator should show the focus: '" + text + "'");

        DebugLog.reset();
        helper.assertTrue(RecorderIndicator.message() == null, "Indicator still shown after recording stopped");
        helper.succeed();
    }

    /**
     * The Recorder page starts with Start, Stop + save and Status (in that order, each with its description
     * under it); the other recorder tools are on "Recorder (advanced)".
     */
    @GameTest(template = PLATFORM)
    public static void recorderPageShowsStartStopStatusFirst(GameTestHelper helper) {
        DevToolRegistry registry = DevToolRegistry.defaults();
        DevBook.Layout layout = DevBook.build(registry, helper.getLevel().registryAccess(), "test", "now");
        Integer recorderPage = layout.categoryStartPages().get("recorder");
        Integer advancedPage = layout.categoryStartPages().get("recorder_advanced");
        helper.assertTrue(recorderPage != null && advancedPage != null, "Recorder pages missing: " + layout.categoryStartPages());

        List<String> lines = layout.pages().get(recorderPage - 1).getString().lines().toList();
        List<String> expected = List.of("▶ Start recording", "▶ Stop + save", "▶ Status (is it on?)");
        int previous = -1;
        for (String button : expected) {
            int index = lines.indexOf(button);
            helper.assertTrue(index > previous, "'" + button + "' missing or out of order on the Recorder page: " + lines);
            helper.assertTrue(index + 1 < lines.size() && lines.get(index + 1).startsWith("  "),
                    "'" + button + "' has no description line under it: " + lines);
            previous = index;
        }
        String advanced = layout.pages().get(advancedPage - 1).getString();
        helper.assertTrue(advanced.contains("Recorder (advanced)") && advanced.contains("BUILD"),
                "Advanced page should explain the categories: " + advanced);
        helper.succeed();
    }

    /**
     * A {@link FakePlayer} that keeps the chat and action-bar messages it is sent.
     */
    static final class ChatCapturingPlayer extends FakePlayer {
        final List<String> messages = new ArrayList<>();

        ChatCapturingPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "csr-chat-capture"));
        }

        @Override
        public void sendSystemMessage(Component component, boolean overlay) {
            messages.add((overlay ? "[action bar] " : "") + component.getString());
        }

        @Override
        public void displayClientMessage(Component component, boolean actionBar) {
            messages.add((actionBar ? "[action bar] " : "") + component.getString());
        }

        List<String> chatMessages() {
            return messages.stream().filter(message -> !message.startsWith("[action bar] ")).toList();
        }
    }
}

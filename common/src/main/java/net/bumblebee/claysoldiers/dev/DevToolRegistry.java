package net.bumblebee.claysoldiers.dev;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * The single source of truth for dev tools: every {@code /csr dev} command AND every button in the
 * dev toolkit book are generated from the entries here. There is no other way to add a {@code /csr dev}
 * command, and {@code DevToolkitGameTests} fails if one is registered some other way.
 * <p>
 * Development only: commands are registered from {@link DevCommands}, which is only called when
 * {@code PLATFORM.isDevEnv()} is true.
 */
public final class DevToolRegistry {
    /** Literal appended to commands of tools with {@link DevTool#confirm()} to run them for real. */
    public static final String CONFIRM = "confirm";
    private static final String DEV = "dev";
    private static DevToolRegistry defaults;

    private final Map<String, DevCategory> categories = new LinkedHashMap<>();
    private final Map<String, DevTool> tools = new LinkedHashMap<>();

    /**
     * The registry with the mod's built-in dev tools, see {@link DevTools}.
     */
    public static synchronized DevToolRegistry defaults() {
        if (defaults == null) {
            defaults = DevTools.createDefault();
        }
        return defaults;
    }

    public DevToolRegistry addCategory(DevCategory category) {
        if (categories.putIfAbsent(category.id(), category) != null) {
            throw new IllegalArgumentException("Duplicate dev category " + category.id());
        }
        return this;
    }

    public DevToolRegistry addTool(DevTool tool) {
        if (!categories.containsKey(tool.category())) {
            throw new IllegalArgumentException("Dev tool " + tool.id() + " uses unknown category " + tool.category());
        }
        if (tool.isLink() == (tool.command() + " ").startsWith(DevTool.ROOT_COMMAND + " ")) {
            throw new IllegalArgumentException(tool.isLink()
                    ? "Link " + tool.id() + " points into " + DevTool.ROOT_COMMAND + "; add a generated tool instead"
                    : "Dev tool " + tool.id() + " must live under " + DevTool.ROOT_COMMAND);
        }
        if (tools.values().stream().anyMatch(other -> other.command().equals(tool.command()))) {
            throw new IllegalArgumentException("Dev tool " + tool.id() + " reuses the command " + tool.command());
        }
        if (tools.putIfAbsent(tool.id(), tool) != null) {
            throw new IllegalArgumentException("Duplicate dev tool " + tool.id());
        }
        return this;
    }

    public List<DevCategory> categories() {
        return List.copyOf(categories.values());
    }

    public List<DevTool> tools() {
        return List.copyOf(tools.values());
    }

    public List<DevTool> toolsIn(DevCategory category) {
        return tools.values().stream().filter(tool -> tool.category().equals(category.id())).toList();
    }

    /**
     * A clickable book button: its text and the full command it runs (starting with {@code /}).
     */
    public record Button(Component label, String command) {
    }

    /**
     * The book buttons of a tool: one for a tool without argument, one per choice otherwise.
     */
    public List<Button> buttons(DevTool tool, RegistryAccess access) {
        if (tool.argument() == null) {
            return List.of(new Button(tool.label(), tool.command()));
        }
        List<Button> buttons = new ArrayList<>();
        for (DevTool.Choice choice : tool.argument().choices().apply(access)) {
            buttons.add(new Button(choice.label(), tool.command() + " " + choice.value()));
        }
        return Collections.unmodifiableList(buttons);
    }

    /**
     * Short hash of all categories, tools and buttons, shown in the book to tell old copies from new ones.
     */
    public String fingerprint(RegistryAccess access) {
        CRC32 crc = new CRC32();
        StringBuilder text = new StringBuilder();
        categories.values().forEach(category -> text.append(category).append('|'));
        for (DevTool tool : tools.values()) {
            text.append(tool.id()).append(tool.category()).append(tool.label().getString()).append(tool.description())
                    .append(tool.command()).append(tool.confirm()).append('|');
            buttons(tool, access).forEach(button -> text.append(button.label().getString()).append(button.command()).append('|'));
        }
        crc.update(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return String.format("%06x", crc.getValue() & 0xFFFFFF);
    }

    /**
     * Registers every tool as {@code /csr dev <path> [argument] [confirm]}, requiring permission level 2.
     * {@link DevTool#isLink() Links} are skipped: their commands are registered elsewhere.
     */
    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (DevTool tool : tools.values()) {
            if (tool.isLink()) {
                continue;
            }
            List<String> path = tool.path();
            ArgumentBuilder<CommandSourceStack, ?> node = buildLeaf(tool, path.getLast());
            for (int i = path.size() - 2; i >= 0; i--) {
                node = Commands.literal(path.get(i)).then(node);
            }
            // Brigadier merges these with the existing /csr command tree.
            dispatcher.register(Commands.literal(ClaySoldiersCommon.MOD_ID)
                    .then(Commands.literal(DEV).requires(source -> source.hasPermission(2)).then(node)));
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildLeaf(DevTool tool, String lastWord) {
        Command<CommandSourceStack> runForReal = tool.action()::run;
        Command<CommandSourceStack> firstRun = tool.confirm() ? context -> askToConfirm(context, tool) : runForReal;

        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(lastWord);
        DevTool.Argument argument = tool.argument();
        if (argument == null) {
            literal.executes(firstRun);
            if (tool.confirm()) {
                literal.then(Commands.literal(CONFIRM).executes(runForReal));
            }
            return literal;
        }

        RequiredArgumentBuilder<CommandSourceStack, ?> argumentNode = Commands.argument(argument.name(), argument.type());
        if (argument.suggestions() != null) {
            argumentNode.suggests(argument.suggestions());
        }
        argumentNode.executes(firstRun);
        if (tool.confirm()) {
            argumentNode.then(Commands.literal(CONFIRM).executes(runForReal));
        }
        return literal.then(argumentNode);
    }

    private static int askToConfirm(CommandContext<CommandSourceStack> context, DevTool tool) {
        String input = context.getInput().startsWith("/") ? context.getInput().substring(1) : context.getInput();
        String confirmCommand = "/" + input + " " + CONFIRM;
        Component message = Component.literal("⚠ " + tool.description() + " ").withStyle(ChatFormatting.RED)
                .append(BookFormat.runCommand(
                        Component.literal("[Click to confirm]").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        confirmCommand, Component.literal(confirmCommand)));
        context.getSource().sendSuccess(() -> message, false);
        return 0;
    }
}

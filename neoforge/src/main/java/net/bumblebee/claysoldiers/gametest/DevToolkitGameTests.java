package net.bumblebee.claysoldiers.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.dev.BookFormat;
import net.bumblebee.claysoldiers.dev.DevBook;
import net.bumblebee.claysoldiers.dev.DevCategory;
import net.bumblebee.claysoldiers.dev.DevTool;
import net.bumblebee.claysoldiers.dev.DevToolRegistry;
import net.bumblebee.claysoldiers.dev.DevTools;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Tests that keep the dev toolkit honest: every {@code /csr dev} command has a book button (and the other way round),
 * and the generated book respects the written-book limits.
 * These don't need a world setup; they use the platform template only because every GameTest needs one.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class DevToolkitGameTests {

    /**
     * Forward coverage: every book button of every registry entry is a complete, registered command
     * (and so is its {@code confirm} variant for tools that ask for confirmation).
     */
    @GameTest(template = ClaySoldierGameTests.PLATFORM)
    public static void everyBookButtonIsARegisteredCommand(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack().withPermission(4);
        DevToolRegistry registry = DevToolRegistry.defaults();

        List<String> problems = new ArrayList<>();
        for (DevTool tool : registry.tools()) {
            List<DevToolRegistry.Button> buttons = registry.buttons(tool, access);
            if (buttons.isEmpty()) {
                problems.add(tool.id() + " has no book button");
            }
            for (DevToolRegistry.Button button : buttons) {
                checkExecutable(dispatcher, source, button.command(), problems);
                if (tool.confirm()) {
                    checkExecutable(dispatcher, source, button.command() + " " + DevToolRegistry.CONFIRM, problems);
                }
            }
        }
        helper.assertTrue(problems.isEmpty(), "Book buttons that are not registered commands: " + problems);
        helper.succeed();
    }

    /**
     * Reverse coverage: every {@code /csr dev ...} command in the dispatcher comes from a registry entry.
     * Fails if someone registers a dev command without going through DevToolRegistry (and so without a book button).
     */
    @GameTest(template = ClaySoldierGameTests.PLATFORM)
    public static void everyDevCommandHasARegistryEntry(GameTestHelper helper) {
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        CommandNode<CommandSourceStack> csr = dispatcher.getRoot().getChild(ClaySoldiersCommon.MOD_ID);
        helper.assertTrue(csr != null && csr.getChild("dev") != null, "/csr dev is not registered");

        Set<String> registered = new TreeSet<>();
        collectCommandPaths(csr.getChild("dev"), "", registered);
        Set<String> inRegistry = DevToolRegistry.defaults().tools().stream()
                .filter(tool -> !tool.isLink())
                .map(tool -> String.join(" ", tool.path()))
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> withoutEntry = new TreeSet<>(registered);
        withoutEntry.removeAll(inRegistry);
        helper.assertTrue(withoutEntry.isEmpty(), "/csr dev commands without a DevToolRegistry entry: " + withoutEntry);
        helper.succeed();
    }

    /**
     * Reverse coverage for commands registered outside {@code /csr dev}: {@code /csr build} (M2) exists
     * and has a book link, so it can't lose its button.
     */
    @GameTest(template = ClaySoldierGameTests.PLATFORM)
    public static void buildCommandHasBookEntry(GameTestHelper helper) {
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        CommandNode<CommandSourceStack> csr = dispatcher.getRoot().getChild(ClaySoldiersCommon.MOD_ID);
        helper.assertTrue(csr != null && csr.getChild("build") != null, "/csr build is not registered");
        boolean linked = DevToolRegistry.defaults().tools().stream()
                .anyMatch(tool -> tool.isLink() && tool.command().equals("/csr build"));
        helper.assertTrue(linked, "/csr build has no DevToolRegistry link, so it has no book button");
        helper.succeed();
    }

    /**
     * The generated book has every category, stays within the written-book limits,
     * and its contents links point to the first page of each category.
     */
    @GameTest(template = ClaySoldierGameTests.PLATFORM)
    public static void bookLayoutIsValid(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        DevToolRegistry registry = DevToolRegistry.defaults();
        DevBook.Layout layout = DevBook.build(registry, access, "test", "now");
        List<Component> pages = layout.pages();

        helper.assertTrue(pages.size() <= BookFormat.MAX_PAGES, "Too many pages: " + pages.size());
        for (int i = 0; i < pages.size(); i++) {
            int length = BookFormat.serializedLength(pages.get(i), access);
            helper.assertTrue(length <= BookFormat.MAX_PAGE_JSON_LENGTH, "Page %d is %d characters long".formatted(i + 1, length));
            helper.assertTrue(layout.pageLines().get(i) <= BookFormat.LINES_PER_PAGE,
                    "Page %d has %d lines (max %d)".formatted(i + 1, layout.pageLines().get(i), BookFormat.LINES_PER_PAGE));
        }

        List<String> expectedCategories = List.of("Setup", "Kit", "Scenarios", "Team", "Build", "Cleanup");
        List<String> actualCategories = registry.categories().stream().map(DevCategory::title).toList();
        helper.assertTrue(actualCategories.containsAll(expectedCategories), "Missing categories, found " + actualCategories);
        assertContentsLinkToCategories(helper, registry, layout);
        helper.succeed();
    }

    /**
     * Extensibility: a new category and tool added only to a registry show up in the book,
     * with no change to the book code.
     */
    @GameTest(template = ClaySoldierGameTests.PLATFORM)
    public static void newRegistryEntryAppearsInBook(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        DevToolRegistry registry = DevTools.createDefault()
                .addCategory(new DevCategory("test_category", "Test Category", "Only exists in this test.", false));
        registry.addTool(new DevTool("test_tool", "test_category", Component.literal("Fake Tool"), "Does nothing.",
                List.of("test", "fake"), false, null, context -> 1));

        DevBook.Layout layout = DevBook.build(registry, access, "test", "now");
        String allText = layout.pages().stream().map(Component::getString).collect(Collectors.joining("\n"));
        helper.assertTrue(allText.contains("Test Category"), "New category is missing from the book");
        helper.assertTrue(allText.contains("Fake Tool"), "New tool is missing from the book");
        boolean hasCommand = layout.pages().stream()
                .flatMap(page -> BookFormat.clickValues(page, false).stream())
                .anyMatch("/csr dev test fake"::equals);
        helper.assertTrue(hasCommand, "New tool's button does not run /csr dev test fake");
        assertContentsLinkToCategories(helper, registry, layout);
        helper.succeed();
    }

    // Helpers

    private static void checkExecutable(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source,
                                        String command, List<String> problems) {
        if (!command.startsWith("/")) {
            problems.add(command + " (does not start with /)");
            return;
        }
        ParseResults<CommandSourceStack> parse = dispatcher.parse(command.substring(1), source);
        boolean complete = parse.getExceptions().isEmpty() && !parse.getReader().canRead()
                && parse.getContext().getCommand() != null;
        if (!complete) {
            problems.add(command);
        }
    }

    /**
     * Collects the literal paths below {@code node} that run something (have a command or take an argument),
     * ignoring the {@code confirm} suffix that the registry adds itself.
     */
    private static void collectCommandPaths(CommandNode<CommandSourceStack> node, String prefix, Set<String> out) {
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            if (!(child instanceof LiteralCommandNode<CommandSourceStack>) || child.getName().equals(DevToolRegistry.CONFIRM)) {
                continue;
            }
            String path = prefix.isEmpty() ? child.getName() : prefix + " " + child.getName();
            boolean runsSomething = child.getCommand() != null
                    || child.getChildren().stream().anyMatch(grandchild -> grandchild instanceof ArgumentCommandNode<?, ?>);
            if (runsSomething) {
                out.add(path);
            }
            collectCommandPaths(child, path, out);
        }
    }

    private static void assertContentsLinkToCategories(GameTestHelper helper, DevToolRegistry registry, DevBook.Layout layout) {
        List<Component> pages = layout.pages();
        List<Integer> links = new ArrayList<>();
        for (int i = 0; i < layout.contentsPages(); i++) {
            BookFormat.clickValues(pages.get(i), true).forEach(value -> links.add(Integer.parseInt(value)));
        }
        Map<String, Integer> starts = layout.categoryStartPages();
        for (DevCategory category : registry.categories()) {
            Integer start = starts.get(category.id());
            helper.assertTrue(start != null && links.contains(start), "No contents link to category " + category.title());
            helper.assertTrue(start > layout.contentsPages() && start <= pages.size(),
                    "Category %s starts on page %d, outside the book".formatted(category.title(), start));
            String firstLine = pages.get(start - 1).getString().lines().findFirst().orElse("");
            helper.assertTrue(firstLine.contains(category.title()),
                    "Contents link for %s goes to page %d, which starts with '%s'".formatted(category.title(), start, firstLine));
        }
        for (int link : links) {
            helper.assertTrue(link >= 1 && link <= pages.size(), "Contents link to missing page " + link);
        }
    }
}

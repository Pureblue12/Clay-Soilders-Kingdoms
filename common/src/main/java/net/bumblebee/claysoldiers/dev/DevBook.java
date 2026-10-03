package net.bumblebee.claysoldiers.dev;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Generates the dev toolkit book from a {@link DevToolRegistry}.
 * <p>
 * Layout: the first page(s) are contents with links to each category and a version stamp;
 * then each category starts on its own page, with one button per tool (or per argument choice).
 * Pages are split automatically using the limits in {@link BookFormat}; nothing here depends on
 * which categories or tools exist.
 */
public final class DevBook {
    public static final String TITLE = "CSR Dev Toolkit";
    public static final String AUTHOR = "Clay Soldiers dev";
    private static final DateTimeFormatter STAMP_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DevBook() {
    }

    /**
     * The generated book.
     *
     * @param pages              the pages, page 1 first
     * @param pageLines          estimated lines used on each page (see {@link BookFormat#estimateLines})
     * @param contentsPages      how many pages the contents take (they come first)
     * @param categoryStartPages 1-based page number where each category (by id) starts
     * @param stamp              version stamp shown on page 1
     */
    public record Layout(List<Component> pages, List<Integer> pageLines, int contentsPages,
                         Map<String, Integer> categoryStartPages, String stamp) {
    }

    private record Line(Component text, int lines) {
        static Line of(MutableComponent text, boolean bold) {
            return new Line(text, BookFormat.estimateLines(text.getString(), bold));
        }
    }

    public static Layout build(DevToolRegistry registry, RegistryAccess access, String modVersion, String generatedAt) {
        String stamp = "v" + modVersion + " #" + registry.fingerprint(access);

        // 1. Category pages (each category starts on a new page; every page links back to the contents).
        List<List<Line>> categoryPages = new ArrayList<>();
        List<Integer> categoryPageCounts = new ArrayList<>();
        Line backToContents = Line.of(BookFormat.changePage(Component.literal("« Contents").withStyle(ChatFormatting.DARK_GRAY), 1,
                Component.literal("Back to the contents page")), false);
        for (DevCategory category : registry.categories()) {
            PageWriter writer = new PageWriter(BookFormat.LINES_PER_PAGE, backToContents,
                    () -> List.of(categoryTitle(category, true)));
            writer.startPage(categoryHeader(category));
            for (DevTool tool : registry.toolsIn(category)) {
                writeTool(writer, registry, tool, access);
            }
            List<List<Line>> pages = writer.finish();
            categoryPages.addAll(pages);
            categoryPageCounts.add(pages.size());
        }

        // 2. Contents pages. Their link texts don't contain page numbers, so their count is known first.
        List<Line> contentsTop = List.of(
                Line.of(Component.literal(TITLE).withStyle(ChatFormatting.BOLD), true),
                Line.of(Component.literal(stamp).withStyle(ChatFormatting.GRAY), false),
                Line.of(Component.literal("made " + generatedAt).withStyle(ChatFormatting.GRAY), false),
                Line.of(Component.literal("Contents:").withStyle(ChatFormatting.BOLD), true));
        List<DevCategory> categories = registry.categories();
        // Dry run with placeholder page numbers to learn how many contents pages there are.
        int contentsPages = writeContents(contentsTop, categories, Map.of()).size();

        Map<String, Integer> startPages = new LinkedHashMap<>();
        int nextPage = contentsPages + 1;
        for (int i = 0; i < categories.size(); i++) {
            startPages.put(categories.get(i).id(), nextPage);
            nextPage += categoryPageCounts.get(i);
        }
        List<List<Line>> contentsPageLines = writeContents(contentsTop, categories, startPages);

        // 3. Assemble.
        List<Component> pages = new ArrayList<>();
        List<Integer> pageLines = new ArrayList<>();
        for (List<List<Line>> group : List.of(contentsPageLines, categoryPages)) {
            for (List<Line> page : group) {
                pages.add(BookFormat.joinLines(page.stream().map(Line::text).toList()));
                pageLines.add(page.stream().mapToInt(Line::lines).sum());
            }
        }
        return new Layout(pages, pageLines, contentsPages, startPages, stamp);
    }

    private static void writeTool(PageWriter writer, DevToolRegistry registry, DevTool tool, RegistryAccess access) {
        ChatFormatting color = tool.confirm() ? ChatFormatting.DARK_RED : ChatFormatting.DARK_GREEN;
        String warning = tool.confirm() ? "⚠ " : "";
        List<DevToolRegistry.Button> buttons = registry.buttons(tool, access);
        if (tool.argument() == null) {
            for (DevToolRegistry.Button button : buttons) {
                writer.add(Line.of(button(button, "▶ " + warning, color, tool), false));
            }
            return;
        }
        writer.add(Line.of(BookFormat.withHover(Component.literal(warning).append(tool.label()).append(":"),
                hoverText(tool, tool.usage())), false));
        if (buttons.isEmpty()) {
            writer.add(Line.of(Component.literal("  (none available)").withStyle(ChatFormatting.GRAY), false));
        }
        for (DevToolRegistry.Button button : buttons) {
            writer.add(Line.of(button(button, "  ▸ ", color, tool), false));
        }
    }

    private static MutableComponent button(DevToolRegistry.Button button, String prefix, ChatFormatting color, DevTool tool) {
        MutableComponent label = Component.literal(prefix).append(button.label()).withStyle(color);
        return BookFormat.runCommand(label, button.command(), hoverText(tool, button.command()));
    }

    private static Component hoverText(DevTool tool, String command) {
        MutableComponent hover = Component.literal(tool.description()).append("\n")
                .append(Component.literal(command).withStyle(ChatFormatting.GRAY));
        if (tool.confirm()) {
            hover.append(Component.literal("\nAsks you to confirm in chat first.").withStyle(ChatFormatting.RED));
        }
        return hover;
    }

    private static Line categoryTitle(DevCategory category, boolean continued) {
        String text = (category.dangerous() ? "⚠ " : "") + category.title() + (continued ? " (cont.)" : "");
        return Line.of(Component.literal(text).withStyle(category.dangerous() ? ChatFormatting.DARK_RED : ChatFormatting.BLACK)
                .withStyle(ChatFormatting.BOLD), true);
    }

    private static List<Line> categoryHeader(DevCategory category) {
        List<Line> header = new ArrayList<>();
        header.add(categoryTitle(category, false));
        if (!category.description().isEmpty()) {
            header.add(Line.of(Component.literal(category.description())
                    .withStyle(category.dangerous() ? ChatFormatting.RED : ChatFormatting.GRAY), false));
        }
        return header;
    }

    /**
     * Writes the contents pages: the top block, then one link per category to its start page
     * (page 1 for categories missing from {@code startPages}).
     */
    private static List<List<Line>> writeContents(List<Line> top, List<DevCategory> categories, Map<String, Integer> startPages) {
        PageWriter contents = new PageWriter(BookFormat.LINES_PER_PAGE, null,
                () -> List.of(Line.of(Component.literal("Contents (cont.)").withStyle(ChatFormatting.BOLD), true)));
        contents.startPage(top);
        for (DevCategory category : categories) {
            MutableComponent label = Component.literal("› " + category.title() + (category.dangerous() ? " ⚠" : ""))
                    .withStyle(category.dangerous() ? ChatFormatting.DARK_RED : ChatFormatting.DARK_BLUE);
            contents.add(Line.of(BookFormat.changePage(label, startPages.getOrDefault(category.id(), 1),
                    Component.literal(category.description())), false));
        }
        return contents.finish();
    }

    /**
     * Fills pages line by line, starting a new page (with a continuation header) when the next line doesn't fit.
     */
    private static final class PageWriter {
        private final int linesPerPage;
        private final Line footer;
        private final Supplier<List<Line>> continuationHeader;
        private final List<List<Line>> pages = new ArrayList<>();
        private List<Line> current;
        private int used;
        private boolean onlyHeader;

        PageWriter(int linesPerPage, Line footer, Supplier<List<Line>> continuationHeader) {
            this.linesPerPage = linesPerPage;
            this.footer = footer;
            this.continuationHeader = continuationHeader;
        }

        void startPage(List<Line> header) {
            flush();
            current = new ArrayList<>(header);
            used = header.stream().mapToInt(Line::lines).sum();
            onlyHeader = true;
        }

        void add(Line line) {
            int footerLines = footer == null ? 0 : footer.lines();
            if (current == null || (!onlyHeader && used + line.lines() + footerLines > linesPerPage)) {
                startPage(continuationHeader.get());
            }
            current.add(line);
            used += line.lines();
            onlyHeader = false;
        }

        List<List<Line>> finish() {
            flush();
            return pages;
        }

        private void flush() {
            if (current != null) {
                if (footer != null) {
                    current.add(footer);
                }
                pages.add(current);
                current = null;
            }
        }
    }

    // Command

    /**
     * Updates every dev toolkit book in the player's inventory with a freshly generated one,
     * or gives a new book when they carry none.
     */
    public static int giveOrUpdate(CommandContext<CommandSourceStack> context, DevToolRegistry registry) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Layout layout = build(registry, source.registryAccess(), ClaySoldiersCommon.PLATFORM.getModVersion(),
                LocalDateTime.now().format(STAMP_TIME));

        Inventory inventory = player.getInventory();
        int updated = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (BookFormat.isDevBook(stack)) {
                BookFormat.setPages(stack, TITLE, AUTHOR, layout.pages());
                updated++;
            }
        }
        if (updated == 0) {
            inventory.placeItemBackInInventory(BookFormat.createBook(TITLE, AUTHOR, layout.pages()));
        }

        int copies = updated;
        source.sendSuccess(() -> Component.literal((copies == 0 ? "Gave you a new dev toolkit book" : "Updated " + copies + " dev toolkit book(s)")
                + " (" + layout.pages().size() + " pages, " + layout.stamp() + ")."), false);
        return layout.pages().size();
    }
}

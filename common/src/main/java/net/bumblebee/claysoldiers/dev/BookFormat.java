package net.bumblebee.claysoldiers.dev;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything about written books and clickable text that depends on the Minecraft version (here 1.21.1):
 * the book item data, click/hover events, and the page limits.
 * When updating Minecraft, this should be the only dev-toolkit file that needs changes.
 */
public final class BookFormat {
    /** Practical page limit of a book (the same as {@code WritableBookContent.MAX_PAGES}). */
    public static final int MAX_PAGES = 100;
    /** Maximum length of one page's text, as JSON ({@code WrittenBookContent.PAGE_LENGTH}). */
    public static final int MAX_PAGE_JSON_LENGTH = WrittenBookContent.PAGE_LENGTH;
    /** Maximum title length ({@code WrittenBookContent.TITLE_MAX_LENGTH}). */
    public static final int MAX_TITLE_LENGTH = WrittenBookContent.TITLE_MAX_LENGTH;
    /** Visible lines per page: the book screen's text area is 128 px tall with 9 px lines. */
    public static final int LINES_PER_PAGE = 14;
    /**
     * Conservative characters per line: the text area is 114 px wide and most characters are 6 px
     * (7 px in bold); a little is left for word wrapping. The server has no font, so this is an estimate.
     */
    private static final int CHARS_PER_LINE = 17;
    private static final int CHARS_PER_BOLD_LINE = 15;
    private static final String DEV_BOOK_MARKER = "csr_dev_book";

    private BookFormat() {
    }

    /**
     * Estimates how many book lines the given plain text takes.
     */
    public static int estimateLines(String text, boolean bold) {
        int perLine = bold ? CHARS_PER_BOLD_LINE : CHARS_PER_LINE;
        return Math.max(1, (text.length() + perLine - 1) / perLine);
    }

    /**
     * Text that runs a command when clicked (in books and chat) and shows {@code hover} when hovered.
     *
     * @param command must start with {@code /}
     */
    public static MutableComponent runCommand(Component label, String command, Component hover) {
        return label.copy().withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    /**
     * Text that only shows {@code hover} when hovered.
     */
    public static MutableComponent withHover(Component label, Component hover) {
        return label.copy().withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    /**
     * Text that turns the book to the given page (1-based) when clicked.
     */
    public static MutableComponent changePage(Component label, int page, Component hover) {
        return label.copy().withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, Integer.toString(page)))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    /**
     * Joins lines into one page.
     */
    public static Component joinLines(List<Component> lines) {
        MutableComponent page = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                page.append("\n");
            }
            page.append(lines.get(i));
        }
        return page;
    }

    /**
     * Length of the page as stored in the book (JSON), to compare with {@link #MAX_PAGE_JSON_LENGTH}.
     */
    public static int serializedLength(Component page, HolderLookup.Provider registries) {
        return Component.Serializer.toJson(page, registries).length();
    }

    /**
     * Returns the values of all page-change ({@code changePage = true}) or run-command click events in the component.
     */
    public static List<String> clickValues(Component component, boolean changePage) {
        ClickEvent.Action wanted = changePage ? ClickEvent.Action.CHANGE_PAGE : ClickEvent.Action.RUN_COMMAND;
        List<String> values = new ArrayList<>();
        collectClickValues(component, wanted, values);
        return values;
    }

    private static void collectClickValues(Component component, ClickEvent.Action wanted, List<String> out) {
        ClickEvent event = component.getStyle().getClickEvent();
        if (event != null && event.getAction() == wanted) {
            out.add(event.getValue());
        }
        component.getSiblings().forEach(sibling -> collectClickValues(sibling, wanted, out));
    }

    /**
     * Creates a written book marked as a dev toolkit book.
     */
    public static ItemStack createBook(String title, String author, List<Component> pages) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        setPages(book, title, author, pages);
        CustomData.update(DataComponents.CUSTOM_DATA, book, tag -> tag.putBoolean(DEV_BOOK_MARKER, true));
        return book;
    }

    /**
     * Replaces the title, author and pages of a written book.
     */
    public static void setPages(ItemStack book, String title, String author, List<Component> pages) {
        List<Filterable<Component>> filterablePages = pages.stream().map(Filterable::passThrough).toList();
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(title), author, 0, filterablePages, true));
    }

    /**
     * Returns whether the stack is a book created by {@link #createBook}.
     */
    public static boolean isDevBook(ItemStack stack) {
        if (!stack.is(Items.WRITTEN_BOOK)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(DEV_BOOK_MARKER);
    }

    /**
     * Returns the pages of a written book, or an empty list.
     */
    public static List<Component> getPages(ItemStack book) {
        WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        return content == null ? List.of() : content.pages().stream().map(Filterable::raw).toList();
    }
}

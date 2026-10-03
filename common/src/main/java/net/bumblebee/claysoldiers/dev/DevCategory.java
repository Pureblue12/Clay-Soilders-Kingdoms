package net.bumblebee.claysoldiers.dev;

/**
 * A section of the dev toolkit book. Categories are plain data: adding one needs no book code changes.
 *
 * @param id          unique id, used by {@link DevTool#category()}
 * @param title       title shown in the book and its contents page
 * @param description one line shown under the title (may be empty)
 * @param dangerous   whether its tools destroy things; shown with a warning
 * @param showToolDescriptions whether each tool's description is printed under its button (not only on hover)
 */
public record DevCategory(String id, String title, String description, boolean dangerous, boolean showToolDescriptions) {
    /**
     * A category whose tool descriptions are only shown when hovering the buttons.
     */
    public DevCategory(String id, String title, String description, boolean dangerous) {
        this(id, title, description, dangerous, false);
    }
}

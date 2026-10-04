package net.bumblebee.claysoldiers.kingdom;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The observer's book for one kingdom, made on demand ({@code /csr kingdom book}): a status page (tier, status,
 * population, renown, king and where it is with a "Go to the king" button, claim), the stockpile, the buildings and
 * the chronicle. It is a snapshot: get a new one to see changes.
 */
public final class KingdomBook {
    /** Marks kingdom books (custom data), with the team id as value. */
    public static final String MARKER = "csr_kingdom_book";

    private KingdomBook() {
    }

    public static ItemStack create(MinecraftServer server, Kingdom kingdom) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    public static List<Component> pages(MinecraftServer server, Kingdom kingdom) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    public static String title(MinecraftServer server, Kingdom kingdom) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }
}

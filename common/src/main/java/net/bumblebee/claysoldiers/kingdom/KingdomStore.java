package net.bumblebee.claysoldiers.kingdom;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.dev.MaterialSupply;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.OptionalDouble;

/**
 * A kingdom's stockpile as the store of its builders' chest (stage 4): the chest is stocked from the stockpile with what
 * the build needs next, and what isn't needed soon goes back into it, including anything a player puts in the chest. So
 * the owner supplies the keep by filling its chest; the book shows what is still missing. Nothing comes from nothing: an
 * item lost with a builder has to be supplied again. Items are kept by id (stockpile resources are item ids), so a stack
 * with components (a named or enchanted item) stays in the chest.
 * <p>
 * Stage 5: a wooden block the stockpile doesn't hold is made from its logs ({@link WoodCost}, D98).
 */
final class KingdomStore implements MaterialSupply.Store {
    private final MinecraftServer server;
    private final Kingdom kingdom;
    /** The scaffold material: not in the plan, but it goes in the chest while the stockpile has some. */
    @Nullable
    private final Item scaffold;

    KingdomStore(MinecraftServer server, Kingdom kingdom, @Nullable Item scaffold) {
        this.server = server;
        this.kingdom = kingdom;
        this.scaffold = scaffold;
    }

    static ResourceLocation id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    @Override
    public boolean has(Item item) {
        if (kingdom.resource(id(item)) > 0) {
            return true;
        }
        OptionalDouble planks = WoodCost.planks(server, item);
        return planks.isPresent() && WoodCost.logs(kingdom) * (double) WoodCost.PLANKS_PER_LOG >= planks.getAsDouble() - 1e-9;
    }

    @Override
    @Nullable
    public ItemStack take(Item item) {
        ItemStack stack = new ItemStack(item);
        int count = Math.min(stack.getMaxStackSize(), kingdom.resource(id(item)));
        if (count <= 0) {
            int made = WoodCost.make(server, kingdom, item, stack.getMaxStackSize());
            if (made > 0) {
                DebugLog.event(DebugCategory.ECONOMY, null, () -> "%s made %d %s from logs".formatted(kingdom.team(), made, id(item)));
                return stack.copyWithCount(made);
            }
            return null;
        }
        if (!Kingdoms.removeResource(server, kingdom, id(item), count).done()) {
            return null;
        }
        return stack.copyWithCount(count);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && stack.getComponentsPatch().isEmpty();
    }

    @Override
    public void put(ItemStack stack) {
        Kingdoms.addResource(server, kingdom, id(stack.getItem()), stack.getCount());
    }

    @Override
    public List<Item> extras() {
        return scaffold != null && has(scaffold) ? List.of(scaffold) : List.of();
    }

    @Override
    public boolean keepsInChest(Item item) {
        return item == scaffold;
    }

    @Override
    public boolean replacesLost() {
        return false;
    }

    @Override
    public int stacks() {
        int stacks = 0;
        for (int count : kingdom.stockpile().values()) {
            stacks += (count + 63) / 64;
        }
        return stacks;
    }
}

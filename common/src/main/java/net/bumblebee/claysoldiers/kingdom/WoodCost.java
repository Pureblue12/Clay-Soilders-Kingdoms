package net.bumblebee.claysoldiers.kingdom;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * The kingdom's carpenter (stage 5, D98): it turns logs from the stockpile into any wooden block a build asks for, at
 * vanilla's crafting ratios. Costs are in planks: a log is 4, a plank 1, a stick half; anything else is worked out from
 * the game's crafting recipes (stairs 6 planks for 4, a door 6 for 3, a barrel 6 planks and 2 slabs). An item counts as
 * wooden when some recipe makes it from wood alone. Species don't matter: a birch forest builds a spruce cottage, and every
 * log stands for any log (stripped logs included).
 * <p>
 * Taking logs rounds up per take, so up to 3 planks' worth is lost each time (the carpenter's offcuts).
 */
public final class WoodCost {
    /** Planks per log. */
    public static final int PLANKS_PER_LOG = 4;
    private static final int MAX_DEPTH = 4;

    private static MinecraftServer cachedFor;
    private static final Map<Item, OptionalDouble> COSTS = new HashMap<>();

    private WoodCost() {
    }

    /** What one {@code item} costs in planks, or empty when it isn't made from wood alone. */
    public static OptionalDouble planks(MinecraftServer server, Item item) {
        if (cachedFor != server) {
            COSTS.clear();
            cachedFor = server;
        }
        return cost(server, item, 0, new HashSet<>());
    }

    /** Whether {@code item} is made from wood alone. */
    public static boolean isWooden(MinecraftServer server, Item item) {
        return planks(server, item).isPresent();
    }

    private static OptionalDouble cost(MinecraftServer server, Item item, int depth, Set<Item> visiting) {
        OptionalDouble known = COSTS.get(item);
        if (known != null) {
            return known;
        }
        ItemStack stack = item.getDefaultInstance();
        if (stack.is(ItemTags.LOGS)) {
            return remember(item, OptionalDouble.of(PLANKS_PER_LOG));
        }
        if (stack.is(ItemTags.PLANKS)) {
            return remember(item, OptionalDouble.of(1));
        }
        if (item == Items.STICK) {
            return remember(item, OptionalDouble.of(0.5));
        }
        if (depth >= MAX_DEPTH || !visiting.add(item)) {
            return OptionalDouble.empty();
        }
        double best = Double.MAX_VALUE;
        for (RecipeHolder<CraftingRecipe> holder : server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = holder.value().getResultItem(server.registryAccess());
            if (result.isEmpty() || result.getItem() != item) {
                continue;
            }
            double sum = 0;
            boolean wooden = true;
            boolean any = false;
            for (Ingredient ingredient : holder.value().getIngredients()) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                any = true;
                double cheapest = Double.MAX_VALUE;
                for (ItemStack option : ingredient.getItems()) {
                    OptionalDouble optionCost = cost(server, option.getItem(), depth + 1, visiting);
                    if (optionCost.isPresent()) {
                        cheapest = Math.min(cheapest, optionCost.getAsDouble());
                    }
                }
                if (cheapest == Double.MAX_VALUE) {
                    wooden = false;
                    break;
                }
                sum += cheapest;
            }
            if (wooden && any) {
                best = Math.min(best, sum / result.getCount());
            }
        }
        visiting.remove(item);
        OptionalDouble found = best == Double.MAX_VALUE ? OptionalDouble.empty() : OptionalDouble.of(best);
        // Only a full answer is remembered: a cut-off search (depth, a loop) may miss a recipe.
        if (depth == 0) {
            remember(item, found);
        }
        return found;
    }

    private static OptionalDouble remember(Item item, OptionalDouble cost) {
        COSTS.put(item, cost);
        return cost;
    }

    /** Whether the stockpile entry {@code resource} is a log. */
    public static boolean isLog(ResourceLocation resource) {
        Item item = BuiltInRegistries.ITEM.get(resource);
        return item != Items.AIR && item.getDefaultInstance().is(ItemTags.LOGS);
    }

    /** Logs (of any kind) in the kingdom's stockpile. */
    public static int logs(Kingdom kingdom) {
        int logs = 0;
        for (Entry<ResourceLocation, Integer> entry : kingdom.stockpile().entrySet()) {
            if (isLog(entry.getKey())) {
                logs += entry.getValue();
            }
        }
        return logs;
    }

    /** Logs needed for {@code planks} planks (rounded up). */
    public static int logsFor(double planks) {
        return (int) Math.ceil(planks / PLANKS_PER_LOG - 1e-9);
    }

    /**
     * Takes {@code count} logs of any kind from the stockpile, the kinds it has most of first; all or nothing.
     *
     * @return whether they were taken
     */
    public static boolean takeLogs(MinecraftServer server, Kingdom kingdom, int count) {
        if (count <= 0) {
            return true;
        }
        if (logs(kingdom) < count) {
            return false;
        }
        List<Entry<ResourceLocation, Integer>> kinds = new ArrayList<>();
        for (Entry<ResourceLocation, Integer> entry : kingdom.stockpile().entrySet()) {
            if (isLog(entry.getKey())) {
                kinds.add(Map.entry(entry.getKey(), entry.getValue()));
            }
        }
        kinds.sort(Entry.<ResourceLocation, Integer>comparingByValue().reversed());
        int left = count;
        for (Entry<ResourceLocation, Integer> kind : kinds) {
            int take = Math.min(left, kind.getValue());
            if (take > 0 && Kingdoms.removeResource(server, kingdom, kind.getKey(), take).done()) {
                left -= take;
            }
            if (left == 0) {
                return true;
            }
        }
        return left == 0;
    }

    /**
     * Makes up to {@code max} of {@code item} from the stockpile's logs; returns how many were made (0 when it isn't
     * wooden or there aren't enough logs for one).
     */
    public static int make(MinecraftServer server, Kingdom kingdom, Item item, int max) {
        OptionalDouble each = planks(server, item);
        if (each.isEmpty() || max <= 0) {
            return 0;
        }
        int logs = logs(kingdom);
        int count = (int) Math.min(max, Math.floor(logs * (double) PLANKS_PER_LOG / each.getAsDouble() + 1e-9));
        if (count <= 0 || !takeLogs(server, kingdom, logsFor(count * each.getAsDouble()))) {
            return 0;
        }
        return count;
    }
}

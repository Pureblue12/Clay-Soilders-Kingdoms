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
import java.util.function.Predicate;

/**
 * The kingdom's carpenter (stage 5, D98): it turns logs from the stockpile into any wooden block a build asks for, at
 * vanilla's crafting ratios. Costs are in planks: a log is 4, a plank 1, a stick half; anything else is worked out from
 * the game's crafting recipes (stairs 6 planks for 4, a door 6 for 3, a barrel 6 planks and 2 slabs). An item counts as
 * wooden when some recipe makes it from wood alone. Species don't matter: a birch forest builds a spruce cottage, and every
 * log stands for any log (stripped logs included).
 * <p>
 * Spare planks in the stockpile are used before logs. Taking logs rounds up per take, so up to 3 planks' worth can be
 * left over each time; they go back to the stockpile as spare planks when the builders return them.
 * <p>
 * Planks and logs the build still needs as they are (the keep's spruce floors, a cottage's log pillars) are reserved:
 * the carpenter never cuts them up for another block. Without this, the keep's own spruce planks went into dark oak logs
 * and spruce stairs, and the keep stopped 13 planks short (the owner's game, 2026-10-17).
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
        return count(kingdom, WoodCost::isLog);
    }

    /** Whether the stockpile entry {@code resource} is planks. */
    public static boolean isPlanks(ResourceLocation resource) {
        Item item = BuiltInRegistries.ITEM.get(resource);
        return item != Items.AIR && item.getDefaultInstance().is(ItemTags.PLANKS);
    }

    /** Planks (of any kind) in the kingdom's stockpile: the carpenter's spare planks, used before any log is cut. */
    public static int sparePlanks(Kingdom kingdom) {
        return count(kingdom, WoodCost::isPlanks);
    }

    /** All the wood in the stockpile, in planks: spare planks plus 4 for every log. */
    public static int wood(Kingdom kingdom) {
        return sparePlanks(kingdom) + logs(kingdom) * PLANKS_PER_LOG;
    }

    /** The stockpile's wood counted in logs (spare planks rounded down to whole logs), for what a building costs. */
    public static int logsWorth(Kingdom kingdom) {
        return wood(kingdom) / PLANKS_PER_LOG;
    }

    private static int count(Kingdom kingdom, Predicate<ResourceLocation> kind) {
        return count(kingdom, kind, Map.of());
    }

    /** Stockpile entries of {@code kind}, leaving out what is {@code reserved} of each. */
    private static int count(Kingdom kingdom, Predicate<ResourceLocation> kind, Map<ResourceLocation, Integer> reserved) {
        int count = 0;
        for (Entry<ResourceLocation, Integer> entry : kingdom.stockpile().entrySet()) {
            if (kind.test(entry.getKey())) {
                count += free(entry, reserved);
            }
        }
        return count;
    }

    private static int free(Entry<ResourceLocation, Integer> entry, Map<ResourceLocation, Integer> reserved) {
        return Math.max(0, entry.getValue() - reserved.getOrDefault(entry.getKey(), 0));
    }

    /** The wood the carpenter may use, in planks: spare planks and logs, leaving out what is {@code reserved}. */
    public static int wood(Kingdom kingdom, Map<ResourceLocation, Integer> reserved) {
        return count(kingdom, WoodCost::isPlanks, reserved) + count(kingdom, WoodCost::isLog, reserved) * PLANKS_PER_LOG;
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
        return take(server, kingdom, WoodCost::isLog, count, Map.of());
    }

    /**
     * Takes {@code count} of the stockpile entries of {@code kind}, leaving what is {@code reserved}, the ones it has most of
     * first; all or nothing.
     */
    private static boolean take(MinecraftServer server, Kingdom kingdom, Predicate<ResourceLocation> kind, int count,
                                Map<ResourceLocation, Integer> reserved) {
        if (count <= 0) {
            return true;
        }
        if (count(kingdom, kind, reserved) < count) {
            return false;
        }
        List<Entry<ResourceLocation, Integer>> kinds = new ArrayList<>();
        for (Entry<ResourceLocation, Integer> entry : kingdom.stockpile().entrySet()) {
            if (kind.test(entry.getKey())) {
                kinds.add(Map.entry(entry.getKey(), free(entry, reserved)));
            }
        }
        kinds.sort(Entry.<ResourceLocation, Integer>comparingByValue().reversed());
        int left = count;
        for (Entry<ResourceLocation, Integer> entry : kinds) {
            int take = Math.min(left, entry.getValue());
            if (take > 0 && Kingdoms.removeResource(server, kingdom, entry.getKey(), take).done()) {
                left -= take;
            }
            if (left == 0) {
                return true;
            }
        }
        return left == 0;
    }

    /**
     * Makes up to {@code max} of {@code item} from the stockpile's wood, spare planks first, then logs; returns how many were
     * made (0 when it isn't wooden or there isn't enough wood for one). A log cut for it leaves its spare planks in the chest
     * the builders put back, so they are used for the next wooden block (a cottage's door after its floor of planks).
     * Planks and logs in {@code reserved} (by stockpile entry: what the build still needs as they are) are left alone.
     */
    public static int make(MinecraftServer server, Kingdom kingdom, Item item, int max, Map<ResourceLocation, Integer> reserved) {
        OptionalDouble each = planks(server, item);
        if (each.isEmpty() || max <= 0) {
            return 0;
        }
        int spare = count(kingdom, WoodCost::isPlanks, reserved);
        int count = (int) Math.min(max, Math.floor(wood(kingdom, reserved) / each.getAsDouble() + 1e-9));
        if (count <= 0) {
            return 0;
        }
        int needed = (int) Math.ceil(count * each.getAsDouble() - 1e-9);
        int fromPlanks = Math.min(spare, needed);
        int fromLogs = logsFor(needed - fromPlanks);
        if (count(kingdom, WoodCost::isLog, reserved) < fromLogs || !take(server, kingdom, WoodCost::isPlanks, fromPlanks, reserved)
                || !take(server, kingdom, WoodCost::isLog, fromLogs, reserved)) {
            return 0;
        }
        return count;
    }
}

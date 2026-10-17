package net.bumblebee.claysoldiers.kingdom;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.kingdom.layout.Cell;
import net.bumblebee.claysoldiers.kingdom.layout.MasterPlan;
import net.bumblebee.claysoldiers.kingdom.layout.Rect;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * Stage 6b, the kingdom's workshop and quarry (D117-D121). The carpenter keeps making wooden blocks ({@link WoodCost});
 * the workshop makes everything else a building needs from raw materials, at vanilla's crafting, stonecutting and smelting
 * ratios.
 * <ul>
 *     <li><b>Levels:</b> each workshop level adds raw materials it works with ({@link #RAW_BY_LEVEL}): level 1 cobblestone
 *     (stone, stone bricks, cobblestone and stone brick stairs, slabs and walls, smooth stone), level 2 sand and clay
 *     (glass, bricks), level 3 iron, gold, copper and coal. An item is made only when every raw material in its cheapest
 *     recipe chain is one the workshop's level works with and nothing wooden is in it.</li>
 *     <li><b>Orders:</b> while a building is being built, every block it still needs that the workshop can make and that
 *     the stockpile and the builders' chest don't hold is on the workshop's order board, in build order. A crafter takes
 *     the first order the stockpile has the raw materials for and makes up to {@code economy.workshop.batch} of it.</li>
 *     <li><b>Quarry:</b> once the quarry is finished, cobblestone is something the kingdom gathers: a building the kingdom
 *     pays for waits for the cobblestone it needs (as it waits for its logs) instead of getting it for free. When the
 *     quarry's pit holds no stone (a superflat world without stone layers), cobblestone is given again.</li>
 * </ul>
 * Until the workshop is built (and for anything it can't make), a paid building's non-wooden materials stay free
 * ({@code economy.free_other_materials}, D101). Memory only: orders, costs and the quarry's dry flag are worked out again.
 */
public final class KingdomWorkshop {
    /** The workshop's building. A stand-in until the Workshop structure is designed and approved in the Studio (D118). */
    public static final ResourceLocation WORKSHOP = ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "stonemason");
    public static final ResourceLocation QUARRY = ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "quarry");
    /** The first building the workshop supplies: stone bricks and cobblestone stairs. */
    public static final ResourceLocation SMITHY = ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "smithy");
    /**
     * The raw materials each workshop level adds (level 1 first). Only materials a kingdom has a steady source for:
     * granite, gravel or moss come and go with the land, so blocks made from them stay free.
     */
    public static final List<Set<Item>> RAW_BY_LEVEL = List.of(
            Set.of(Items.COBBLESTONE),
            Set.of(Items.SAND, Items.RED_SAND, Items.CLAY_BALL),
            Set.of(Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER, Items.COAL));
    /** What the kingdom gathers once its quarry is finished. */
    public static final Set<Item> QUARRY_YIELDS = Set.of(Items.COBBLESTONE);
    private static final int MAX_DEPTH = 5;

    /** One line of the workshop's order board: {@code count} more of {@code item}. */
    public record Order(Item item, int count) {
    }

    /** What a crafter made. */
    public record Made(Item item, int count) {
    }

    private static final Map<UUID, List<Order>> ORDERS = new HashMap<>();
    private static final Set<UUID> DRY = new HashSet<>();
    /** Per workshop level: the cheapest raw-material cost of every item it can make. */
    private static final Map<Integer, Map<Item, Map<Item, Double>>> COSTS = new HashMap<>();
    @Nullable
    private static MinecraftServer cachedFor;

    private KingdomWorkshop() {
    }

    // Levels and sources

    /** The level of the kingdom's finished workshop, 0 when it has none. */
    public static int level(Kingdom kingdom) {
        int level = 0;
        for (KingdomBuilding building : kingdom.buildings()) {
            if (building.structure().equals(WORKSHOP) && building.state() == KingdomBuilding.State.BUILT) {
                level = Math.max(level, Math.max(1, building.level()));
            }
        }
        return Math.min(level, RAW_BY_LEVEL.size());
    }

    /** Whether the kingdom has a finished quarry. */
    public static boolean hasQuarry(Kingdom kingdom) {
        return kingdom.buildings().stream().anyMatch(building -> building.structure().equals(QUARRY) && building.state() == KingdomBuilding.State.BUILT);
    }

    /** The raw materials a workshop of {@code level} works with. */
    public static Set<Item> raw(int level) {
        Set<Item> raw = new HashSet<>();
        for (int i = 0; i < Math.min(level, RAW_BY_LEVEL.size()); i++) {
            raw.addAll(RAW_BY_LEVEL.get(i));
        }
        return raw;
    }

    /** Whether the kingdom gathers {@code item} itself (a building that needs it waits for it instead of getting it). */
    public static boolean gathered(Kingdom kingdom, Item item) {
        return QUARRY_YIELDS.contains(item) && hasQuarry(kingdom) && !DRY.contains(kingdom.id());
    }

    /** Called by quarrymen: whether the quarry's pit has any stone left; a dry quarry gathers no cobblestone. */
    public static void setDry(Kingdom kingdom, boolean dry) {
        boolean changed = dry ? DRY.add(kingdom.id()) : DRY.remove(kingdom.id());
        if (changed) {
            DebugLog.event(DebugCategory.ECONOMY, null, () -> dry
                    ? "the quarry of %s has no stone left in its pit: cobblestone is given again".formatted(kingdom.team())
                    : "the quarry of %s has stone in its pit: cobblestone is gathered".formatted(kingdom.team()));
        }
    }

    /** Whether the kingdom's quarry has run out of stone (or never had any). */
    public static boolean isDry(Kingdom kingdom) {
        return DRY.contains(kingdom.id());
    }

    // What the workshop can make, and from what

    /** Whether the kingdom's workshop can make {@code item} (it isn't wooden, raw, or out of its level's reach). */
    public static boolean canMake(MinecraftServer server, Kingdom kingdom, Item item) {
        int level = level(kingdom);
        return level > 0 && !raw(level).contains(item) && rawCost(server, item, level).isPresent();
    }

    /**
     * The raw materials one {@code item} costs at a workshop of {@code level}, through its cheapest recipe chain (crafting,
     * stonecutting, smelting), or empty when it can't be made there. A raw material costs itself.
     */
    public static Optional<Map<Item, Double>> rawCost(MinecraftServer server, Item item, int level) {
        if (cachedFor != server) {
            COSTS.clear();
            cachedFor = server;
        }
        return Optional.ofNullable(COSTS.computeIfAbsent(level, key -> costs(server, raw(key))).get(item));
    }

    /**
     * The cheapest cost of everything makeable from {@code raw}, worked out from the bottom up: raw materials cost
     * themselves, and each round every recipe whose ingredients all have a cost gives its result a cost (kept when it is
     * cheaper), so after {@code MAX_DEPTH} rounds every chain up to that many steps is known. Wooden items get no cost, so
     * no chain goes through them. Once per level and server, in one pass over the recipes per round (a search from each
     * item down took the suite three times as long).
     */
    private static Map<Item, Map<Item, Double>> costs(MinecraftServer server, Set<Item> raw) {
        Map<Item, Map<Item, Double>> best = new HashMap<>();
        raw.forEach(item -> best.put(item, Map.of(item, 1.0)));
        List<Recipe<?>> recipes = new ArrayList<>();
        RecipeManager manager = server.getRecipeManager();
        manager.getAllRecipesFor(RecipeType.CRAFTING).forEach(holder -> recipes.add(holder.value()));
        manager.getAllRecipesFor(RecipeType.STONECUTTING).forEach(holder -> recipes.add(holder.value()));
        manager.getAllRecipesFor(RecipeType.SMELTING).forEach(holder -> recipes.add(holder.value()));
        Map<Item, Boolean> wooden = new HashMap<>();
        for (int round = 0; round < MAX_DEPTH; round++) {
            boolean changed = false;
            for (Recipe<?> recipe : recipes) {
                ItemStack result = recipe.getResultItem(server.registryAccess());
                if (result.isEmpty() || raw.contains(result.getItem())
                        || wooden.computeIfAbsent(result.getItem(), item -> WoodCost.isWooden(server, item))) {
                    continue;
                }
                Map<Item, Double> sum = new HashMap<>();
                boolean makeable = true;
                boolean any = false;
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient.isEmpty()) {
                        continue;
                    }
                    any = true;
                    Map<Item, Double> cheapest = null;
                    for (ItemStack option : ingredient.getItems()) {
                        Map<Item, Double> cost = best.get(option.getItem());
                        if (cost != null && (cheapest == null || total(cost) < total(cheapest))) {
                            cheapest = cost;
                        }
                    }
                    if (cheapest == null) {
                        makeable = false;
                        break;
                    }
                    cheapest.forEach((rawItem, count) -> sum.merge(rawItem, count, Double::sum));
                }
                if (!makeable || !any) {
                    continue;
                }
                Map<Item, Double> each = new HashMap<>();
                sum.forEach((rawItem, count) -> each.put(rawItem, count / result.getCount()));
                Map<Item, Double> old = best.get(result.getItem());
                if (old == null || total(each) < total(old) - 1e-9) {
                    best.put(result.getItem(), Map.copyOf(each));
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        return best;
    }

    private static double total(Map<Item, Double> cost) {
        double total = 0;
        for (double count : cost.values()) {
            total += count;
        }
        return total;
    }

    /** The whole raw materials {@code count} of an item costing {@code each} take (rounded up). */
    public static Map<Item, Integer> rawFor(Map<Item, Double> each, int count) {
        Map<Item, Integer> raw = new LinkedHashMap<>();
        each.forEach((item, amount) -> raw.put(item, (int) Math.ceil(amount * count - 1e-9)));
        return raw;
    }

    /**
     * The raw materials the kingdom must have before a building it pays for starts: what it gathers itself (cobblestone,
     * once the quarry is finished) and what the workshop makes from it.
     */
    public static Map<Item, Integer> rawNeeds(MinecraftServer server, Kingdom kingdom, List<ItemStack> needed) {
        int level = level(kingdom);
        Map<Item, Double> raw = new LinkedHashMap<>();
        for (ItemStack stack : needed) {
            Item item = stack.getItem();
            if (gathered(kingdom, item)) {
                raw.merge(item, (double) stack.getCount(), Double::sum);
            } else if (canMake(server, kingdom, item)) {
                rawCost(server, item, level).ifPresent(cost -> cost.forEach((rawItem, each) -> raw.merge(rawItem, each * stack.getCount(), Double::sum)));
            }
        }
        Map<Item, Integer> whole = new LinkedHashMap<>();
        raw.forEach((item, count) -> whole.put(item, (int) Math.ceil(count - 1e-9)));
        return whole;
    }

    // The order board

    /**
     * Writes the workshop's order board for the building being built: its remaining blocks the workshop can make, less what
     * the stockpile and its chest hold, in build order.
     */
    public static void updateOrders(MinecraftServer server, Kingdom kingdom, List<Item> remaining, ToIntFunction<Item> inChest) {
        if (level(kingdom) == 0) {
            ORDERS.remove(kingdom.id());
            return;
        }
        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (Item item : remaining) {
            needed.merge(item, 1, Integer::sum);
        }
        List<Order> orders = new ArrayList<>();
        needed.forEach((item, count) -> {
            if (!canMake(server, kingdom, item)) {
                return;
            }
            int left = count - kingdom.resource(KingdomStore.id(item)) - inChest.applyAsInt(item);
            if (left > 0) {
                orders.add(new Order(item, left));
            }
        });
        List<Order> before = ORDERS.put(kingdom.id(), orders);
        if (!orders.equals(before)) {
            DebugLog.event(DebugCategory.ECONOMY, null, () -> "workshop orders of %s: %s".formatted(kingdom.team(), describe(orders)));
        }
    }

    /** The workshop's order board (empty when nothing is being built or it has no workshop). */
    public static List<Order> orders(Kingdom kingdom) {
        return ORDERS.getOrDefault(kingdom.id(), List.of());
    }

    /** GameTests only: puts {@code orders} on the kingdom's order board. */
    public static void setOrdersForTests(Kingdom kingdom, List<Order> orders) {
        ORDERS.put(kingdom.id(), new ArrayList<>(orders));
    }

    /**
     * A crafter's work: makes up to {@code economy.workshop.batch} of the first order the stockpile has the raw materials
     * for, taking them from the stockpile and putting what it made in it; empty when no order can be made now.
     */
    public static Optional<Made> craft(MinecraftServer server, Kingdom kingdom) {
        int level = level(kingdom);
        List<Order> orders = ORDERS.get(kingdom.id());
        if (level == 0 || orders == null) {
            return Optional.empty();
        }
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            Optional<Map<Item, Double>> cost = rawCost(server, order.item(), level);
            if (cost.isEmpty()) {
                continue;
            }
            int count = Math.min(order.count(), Tuning.ECONOMY_WORKSHOP_BATCH.get());
            Map<Item, Integer> take = rawFor(cost.get(), count);
            if (take.entrySet().stream().anyMatch(entry -> kingdom.resource(KingdomStore.id(entry.getKey())) < entry.getValue())) {
                continue;
            }
            for (Map.Entry<Item, Integer> entry : take.entrySet()) {
                if (!Kingdoms.removeResource(server, kingdom, KingdomStore.id(entry.getKey()), entry.getValue()).done()) {
                    return Optional.empty();
                }
            }
            Kingdoms.addResource(server, kingdom, KingdomStore.id(order.item()), count);
            if (order.count() > count) {
                orders.set(i, new Order(order.item(), order.count() - count));
            } else {
                orders.remove(i);
            }
            DebugLog.event(DebugCategory.ECONOMY, null, () -> "the workshop of %s made %d %s from %s".formatted(kingdom.team(), count,
                    KingdomStore.id(order.item()), take));
            return Optional.of(new Made(order.item(), count));
        }
        return Optional.empty();
    }

    /** Why no order can be made now: the raw materials the first order still lacks; empty when there is no order. */
    public static Optional<String> shortOf(MinecraftServer server, Kingdom kingdom) {
        List<Order> orders = orders(kingdom);
        int level = level(kingdom);
        if (orders.isEmpty() || level == 0) {
            return Optional.empty();
        }
        Order first = orders.get(0);
        Map<Item, Integer> take = rawFor(rawCost(server, first.item(), level).orElse(Map.of()), Math.min(first.count(), Tuning.ECONOMY_WORKSHOP_BATCH.get()));
        List<String> missing = new ArrayList<>();
        take.forEach((item, count) -> {
            int has = kingdom.resource(KingdomStore.id(item));
            if (has < count) {
                missing.add((count - has) + " " + name(item));
            }
        });
        return missing.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", missing) + " for " + name(first.item()));
    }

    // The quarry's ground

    /**
     * Whether a quarry may dig the column at {@code x, z}: not in or beside any of the kingdom's buildings, not on a planned
     * plot, road or the wall line, not in another kingdom's claim.
     */
    public static boolean pitFree(ServerLevel level, Kingdom kingdom, int x, int z, int y) {
        BlockPos pos = new BlockPos(x, y, z);
        if (KingdomEconomy.inBuilding(level, kingdom, pos, 2)) {
            return false;
        }
        Optional<MasterPlan> plan = kingdom.plan();
        if (plan.isPresent()) {
            for (MasterPlan.Plot plot : plan.get().plots()) {
                if (plot.rect().contains(x, z)) {
                    return false;
                }
            }
            if (plan.get().roadCells().contains(new Cell(x, z))) {
                return false;
            }
            Rect wall = plan.get().wallRect();
            boolean onWallX = (x == wall.minX() || x == wall.maxX()) && z >= wall.minZ() && z <= wall.maxZ();
            boolean onWallZ = (z == wall.minZ() || z == wall.maxZ()) && x >= wall.minX() && x <= wall.maxX();
            if (onWallX || onWallZ) {
                return false;
            }
        }
        return Kingdoms.at(level.getServer(), level.dimension(), pos).map(other -> other.id().equals(kingdom.id())).orElse(true);
    }

    // Status

    private static String name(Item item) {
        return item.getDescription().getString().toLowerCase();
    }

    private static String describe(List<Order> orders) {
        if (orders.isEmpty()) {
            return "nothing";
        }
        List<String> parts = new ArrayList<>();
        for (Order order : orders) {
            parts.add(order.count() + " " + name(order.item()));
        }
        return String.join(", ", parts);
    }

    /** Lines about the workshop and the quarry for the book and {@code /csr kingdom status}. */
    public static List<String> describe(MinecraftServer server, Kingdom kingdom) {
        List<String> lines = new ArrayList<>();
        int level = level(kingdom);
        if (level > 0) {
            lines.add("Workshop (level " + level + ") orders: " + describe(orders(kingdom)) + ".");
            shortOf(server, kingdom).ifPresent(why -> lines.add("Workshop waits for " + why + "."));
        }
        if (hasQuarry(kingdom)) {
            lines.add("Quarry: " + (isDry(kingdom) ? "no stone left in its pit, cobblestone is given" : "gathering cobblestone")
                    + "; cobblestone: " + kingdom.resource(BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE)) + ".");
        }
        return lines;
    }

    /** Forgets everything kept in memory about the kingdom (disbanding, GameTests). */
    public static void forget(Kingdom kingdom) {
        ORDERS.remove(kingdom.id());
        DRY.remove(kingdom.id());
    }
}

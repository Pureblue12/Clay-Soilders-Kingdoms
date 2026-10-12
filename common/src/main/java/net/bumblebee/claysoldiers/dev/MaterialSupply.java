package net.bumblebee.claysoldiers.dev;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Keeps a builders' chest stocked from a list of materials, like a player refilling it: most structures need more
 * stacks than a chest holds (a chest has 27 slots; the hospital needs about 60 stacks), and {@link DevScenarios#placeChest}
 * drops what doesn't fit.
 * <p>
 * Every {@link #INTERVAL_TICKS} it stocks the items the build needs next: {@code demand} lists the item of every block
 * still to place, in the order the easel hands them out. The first items in that order (one per slot, items the plan
 * doesn't list, such as scaffold material, first) are wanted; stacks of other items go back to the waiting list, and
 * wanted items get a stack in an empty slot. {@link #FREE_SLOTS} stay empty so builders can put back what they carry
 * (scaffold refunds, returned blocks). Filling the chest in list order instead would deadlock: a full chest of items
 * needed later, and the item asked for now still waiting. Dev and GameTests only; in memory only.
 */
public final class MaterialSupply {
    /** Ticks between restocks. */
    public static final int INTERVAL_TICKS = 20;
    /** Slots left empty for items builders put back. */
    public static final int FREE_SLOTS = 3;
    /**
     * Ticks an item the plan still needs must be missing (not in the chest, not waiting) before it counts as lost: longer
     * than a builder may carry one ({@code BuildBlueprintGoal.MAX_CARRY_TICKS}, 400) and than a request goes stale (600),
     * so an item on its way to its spot is not brought twice.
     */
    public static final int LOST_AFTER_TICKS = 1200;

    /** Supplies started by dev commands, ticked by the server (dev runs only). */
    private static final List<MaterialSupply> ACTIVE = new ArrayList<>();

    private final ServerLevel level;
    private final BlockPos chestPos;
    private final List<ItemStack> waiting = new ArrayList<>();
    private final Supplier<List<Item>> demand;
    private final BooleanSupplier done;
    /** Every item the plan has listed: once it no longer lists one, that item's leftovers aren't kept. */
    private final Set<Item> planItems;
    /** Game time each needed item was first seen missing (see {@link #resupplyLost}). */
    private final Map<Item, Long> missingSince = new LinkedHashMap<>();
    private int ticks;

    /**
     * @param items  the materials (any stack sizes; split into max-size stacks)
     * @param demand the item of every block still to place, in hand-out order
     * @param done   stops the supply when true (e.g. the easel is finished or gone)
     */
    public MaterialSupply(ServerLevel level, BlockPos chestPos, List<ItemStack> items, Supplier<List<Item>> demand, BooleanSupplier done) {
        this.level = level;
        this.chestPos = chestPos.immutable();
        this.demand = demand;
        this.done = done;
        this.planItems = new LinkedHashSet<>(demand.get());
        for (ItemStack stack : items) {
            int remaining = stack.getCount();
            while (remaining > 0) {
                int count = Math.min(remaining, stack.getMaxStackSize());
                waiting.add(stack.copyWithCount(count));
                remaining -= count;
            }
        }
    }

    /** Stocks the chest with the items needed next now; returns the stacks moved in. */
    public int restock() {
        if (!(level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
            return 0;
        }
        int usable = chest.getContainerSize() - FREE_SLOTS;
        resupplyLost(chest);
        compact(chest);
        Set<Item> wanted = wanted(chest, usable);
        // Stacks nobody needs soon go back to the waiting list, to make room.
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty() && !wanted.contains(stack.getItem())) {
                waiting.add(stack);
                chest.setItem(slot, ItemStack.EMPTY);
            }
        }
        int moved = 0;
        // One stack of every wanted item first, then more stacks, while slots are free.
        for (int round = 0; round < 2; round++) {
            for (Item item : wanted) {
                if (round == 0 && chest.countItem(item) > 0) {
                    continue;
                }
                if (!hasWaiting(item)) {
                    continue;
                }
                if (round == 0 && (emptySlot(chest) < 0 || usedSlots(chest) >= usable)) {
                    makeRoom(chest, wanted, item);
                }
                int empty = emptySlot(chest);
                if (empty < 0 || usedSlots(chest) >= usable) {
                    break;
                }
                ItemStack stack = take(item);
                if (stack != null) {
                    chest.setItem(empty, stack);
                    moved++;
                }
            }
        }
        chest.setChanged();
        return moved;
    }

    /**
     * The items to keep in the chest: materials the plan never listed (scaffold material), then the plan's items in
     * hand-out order. Leftovers of plan items already all placed are not kept: they filled every slot with 1 to 3 of
     * each kind while the item asked for waited outside (structure build run 38156523957: inn, smithy, stables, ...).
     */
    private Set<Item> wanted(ChestBlockEntity chest, int slots) {
        List<Item> order = demand.get();
        Set<Item> listed = new LinkedHashSet<>(order);
        planItems.addAll(listed);
        Set<Item> wanted = new LinkedHashSet<>();
        for (ItemStack stack : waiting) {
            if (!listed.contains(stack.getItem()) && !planItems.contains(stack.getItem())) {
                wanted.add(stack.getItem());
            }
        }
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty() && !listed.contains(stack.getItem()) && !planItems.contains(stack.getItem())) {
                wanted.add(stack.getItem());
            }
        }
        for (Item item : listed) {
            if (wanted.size() >= slots) {
                break;
            }
            if (chest.countItem(item) > 0 || hasWaiting(item)) {
                wanted.add(item);
            }
        }
        return wanted;
    }

    /**
     * Merges stacks of the same item: builders put back what they carry into the first slot that takes it (an empty slot
     * before a part-used stack of the same item), so returned leftovers spread one item over several slots. Since builders
     * carry a stack (D79) every row ends with a return, and the fragments filled every slot while the item asked for next
     * waited outside (keep L1, L2: no stone bricks for 6000 ticks).
     */
    private static void compact(ChestBlockEntity chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty() || stack.getCount() >= stack.getMaxStackSize()) {
                continue;
            }
            for (int other = slot + 1; other < chest.getContainerSize() && stack.getCount() < stack.getMaxStackSize(); other++) {
                ItemStack more = chest.getItem(other);
                if (!more.isEmpty() && ItemStack.isSameItemSameComponents(stack, more)) {
                    int moved = Math.min(more.getCount(), stack.getMaxStackSize() - stack.getCount());
                    stack.grow(moved);
                    more.shrink(moved);
                    if (more.isEmpty()) {
                        chest.setItem(other, ItemStack.EMPTY);
                    }
                }
            }
        }
    }

    /**
     * Frees a slot for {@code item}, which the chest has none of: a second stack of a wanted item goes back to the waiting
     * list first, else the stack of the wanted item needed last (after {@code item}). Without this a chest full of items
     * needed later keeps the item needed now out.
     */
    private void makeRoom(ChestBlockEntity chest, Set<Item> wanted, Item item) {
        List<Item> order = new ArrayList<>(wanted);
        int rank = order.indexOf(item);
        int evict = -1;
        int evictRank = rank;
        Set<Item> seen = new LinkedHashSet<>();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!seen.add(stack.getItem())) {
                evict = slot;
                break;
            }
            int stackRank = order.indexOf(stack.getItem());
            if (stackRank > evictRank) {
                evictRank = stackRank;
                evict = slot;
            }
        }
        if (evict >= 0) {
            ItemStack stack = chest.getItem(evict);
            waiting.add(stack);
            chest.setItem(evict, ItemStack.EMPTY);
            DebugLog.event(DebugCategory.BUILD, null, "material supply: made room for %s, put back %s %s", item, stack.getCount(), stack.getItem());
        }
    }

    /**
     * Items the build still needs that are neither in the chest nor waiting: lost with a builder that died carrying
     * them, or held by one that can't get back. A player would bring more, so they come back into the waiting list
     * (as many as the plan still needs, one stack at most); otherwise the build waits for them forever (structure
     * build run 38149380878: town hall L1, keep L1, gatehouse L2, farm L2). Only after {@link #LOST_AFTER_TICKS} missing:
     * the last of an item is usually in a builder's hands on its way to its spot, and bringing it twice left leftovers.
     */
    private void resupplyLost(ChestBlockEntity chest) {
        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (Item item : demand.get()) {
            needed.merge(item, 1, Integer::sum);
        }
        long now = level.getGameTime();
        missingSince.keySet().removeIf(item -> !needed.containsKey(item));
        needed.forEach((item, count) -> {
            if (item == Items.AIR || chest.countItem(item) > 0 || hasWaiting(item)) {
                missingSince.remove(item);
                return;
            }
            long since = missingSince.computeIfAbsent(item, i -> now);
            if (now - since >= LOST_AFTER_TICKS) {
                missingSince.remove(item);
                ItemStack stack = new ItemStack(item);
                waiting.add(stack.copyWithCount(Math.min(count, stack.getMaxStackSize())));
                DebugLog.event(DebugCategory.BUILD, null, "material supply: %s lost, brought %s more", item, Math.min(count, stack.getMaxStackSize()));
            }
        });
    }

    private boolean hasWaiting(Item item) {
        return waiting.stream().anyMatch(stack -> stack.is(item));
    }

    private ItemStack take(Item item) {
        for (Iterator<ItemStack> it = waiting.iterator(); it.hasNext(); ) {
            ItemStack stack = it.next();
            if (stack.is(item)) {
                it.remove();
                return stack;
            }
        }
        return null;
    }

    private static int emptySlot(ChestBlockEntity chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static int usedSlots(ChestBlockEntity chest) {
        int used = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (!chest.getItem(slot).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    /** Call every tick; restocks every {@link #INTERVAL_TICKS}. Returns false once the supply has nothing left to do. */
    public boolean tick() {
        if (isFinished()) {
            return false;
        }
        if (++ticks >= INTERVAL_TICKS) {
            ticks = 0;
            restock();
        }
        return !isFinished();
    }

    public boolean isFinished() {
        // Not when the waiting list is empty: a lost item still needs bringing back (resupplyLost).
        return done.getAsBoolean() || !(level.getBlockEntity(chestPos) instanceof ChestBlockEntity);
    }

    /** Stacks not in the chest. */
    public int waitingStacks() {
        return waiting.size();
    }

    public BlockPos chestPos() {
        return chestPos;
    }

    /** Keeps this supply going in a dev world (see {@link #tickAll}). */
    public static void start(MaterialSupply supply) {
        ACTIVE.add(supply);
    }

    /** Ticks the supplies dev commands started; called every server tick in dev runs. */
    public static void tickAll(MinecraftServer server) {
        if (!ACTIVE.isEmpty()) {
            ACTIVE.removeIf(supply -> !supply.tick());
        }
    }
}

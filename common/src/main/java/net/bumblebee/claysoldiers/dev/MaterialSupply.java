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

    /** Supplies started by dev commands, ticked by the server (dev runs only). */
    private static final List<MaterialSupply> ACTIVE = new ArrayList<>();

    private final ServerLevel level;
    private final BlockPos chestPos;
    private final List<ItemStack> waiting = new ArrayList<>();
    private final Supplier<List<Item>> demand;
    private final BooleanSupplier done;
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

    /** The items to keep in the chest: materials the plan doesn't list, then the plan's items in hand-out order. */
    private Set<Item> wanted(ChestBlockEntity chest, int slots) {
        List<Item> order = demand.get();
        Set<Item> listed = new LinkedHashSet<>(order);
        Set<Item> wanted = new LinkedHashSet<>();
        for (ItemStack stack : waiting) {
            if (!listed.contains(stack.getItem())) {
                wanted.add(stack.getItem());
            }
        }
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty() && !listed.contains(stack.getItem())) {
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
     * Items the build still needs that are neither in the chest nor waiting: lost with a builder that died carrying
     * them, or held by one that can't get back. A player would bring more, so they come back into the waiting list
     * (as many as the plan still needs, one stack at most); otherwise the build waits for them forever (structure
     * build run 38149380878: town hall L1, keep L1, gatehouse L2, farm L2).
     */
    private void resupplyLost(ChestBlockEntity chest) {
        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (Item item : demand.get()) {
            needed.merge(item, 1, Integer::sum);
        }
        needed.forEach((item, count) -> {
            if (item != Items.AIR && chest.countItem(item) == 0 && !hasWaiting(item)) {
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

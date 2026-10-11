package net.bumblebee.claysoldiers.dev;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Keeps a builders' chest stocked from a list of materials, like a player refilling it: most structures need more
 * stacks than a chest holds (a chest has 27 slots; the hospital needs about 60 stacks), and {@link DevScenarios#placeChest}
 * drops what doesn't fit. Every {@link #INTERVAL_TICKS} it moves the next stacks into empty slots, leaving
 * {@link #FREE_SLOTS} empty so builders can put back what they carry (scaffold refunds, returned blocks). Dev and
 * GameTests only; in memory only.
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
    private final Deque<ItemStack> waiting = new ArrayDeque<>();
    private final BooleanSupplier done;
    private int ticks;

    /**
     * @param items the materials (any stack sizes; split into max-size stacks)
     * @param done  stops the supply when true (e.g. the easel is finished or gone)
     */
    public MaterialSupply(ServerLevel level, BlockPos chestPos, List<ItemStack> items, BooleanSupplier done) {
        this.level = level;
        this.chestPos = chestPos.immutable();
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

    /** Fills the chest's empty slots now (leaving {@link #FREE_SLOTS} empty); returns the stacks moved. */
    public int restock() {
        if (!(level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
            return 0;
        }
        List<Integer> empty = new ArrayList<>();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).isEmpty()) {
                empty.add(slot);
            }
        }
        int moved = 0;
        for (int i = 0; i < empty.size() - FREE_SLOTS && !waiting.isEmpty(); i++) {
            chest.setItem(empty.get(i), waiting.poll());
            moved++;
        }
        if (moved > 0) {
            chest.setChanged();
        }
        return moved;
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
        return waiting.isEmpty() || done.getAsBoolean() || !(level.getBlockEntity(chestPos) instanceof ChestBlockEntity);
    }

    /** Stacks not yet in the chest. */
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

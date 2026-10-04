package net.bumblebee.claysoldiers.scaffold;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.templates.ImmutableTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generated cobblestone structures for the tall construction spike (GameTests and {@code /csr dev scaffold}): a tower,
 * a long wall, and two walls meeting at a corner tower. Their sizes are test fixtures, not balancing numbers. The
 * blueprints are made in memory (like the GameTests' tiny blueprints), not registered: after a world reload the easel
 * keeps its plan but not the blueprint's name.
 */
public final class TestStructures {
    /** Length of the long wall. */
    public static final int WALL_LENGTH = 16;
    /** Length of each wall at the corner tower. */
    public static final int CORNER_WALL_LENGTH = 10;
    /** How much taller the corner tower is than its walls. */
    public static final int CORNER_TOWER_EXTRA = 4;

    private TestStructures() {
    }

    public enum Shape implements StringRepresentable {
        /** A 3 by 3 hollow tower (8 blocks per layer). */
        TOWER("tower"),
        /** A straight wall, {@link #WALL_LENGTH} long and 1 thick. */
        WALL("wall"),
        /** Two walls ({@link #CORNER_WALL_LENGTH} long) meeting at a 3 by 3 hollow corner tower {@link #CORNER_TOWER_EXTRA} taller. */
        WALL_TOWER("wall_tower");

        private final String id;

        Shape(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }

        public static Shape byId(String id) {
            for (Shape shape : values()) {
                if (shape.id.equals(id)) {
                    return shape;
                }
            }
            throw new IllegalArgumentException("unknown test structure " + id);
        }
    }

    /**
     * The blocks of {@code shape} with walls {@code height} tall, relative to the structure's corner, in the order
     * builders get them (bottom to top, then x, then z, like blueprint plans).
     */
    public static List<BlockPos> blocks(Shape shape, int height) {
        List<BlockPos> blocks = new ArrayList<>();
        switch (shape) {
            case TOWER -> ring(blocks, 0, 0, height);
            case WALL -> {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < WALL_LENGTH; x++) {
                        blocks.add(new BlockPos(x, y, 0));
                    }
                }
            }
            case WALL_TOWER -> {
                ring(blocks, 0, 0, height + CORNER_TOWER_EXTRA);
                for (int y = 0; y < height; y++) {
                    for (int i = 0; i < CORNER_WALL_LENGTH; i++) {
                        blocks.add(new BlockPos(3 + i, y, 1));
                        blocks.add(new BlockPos(1, y, 3 + i));
                    }
                }
            }
        }
        blocks.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return List.copyOf(blocks);
    }

    private static void ring(List<BlockPos> blocks, int x0, int z0, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    if (x != 1 || z != 1) {
                        blocks.add(new BlockPos(x0 + x, y, z0 + z));
                    }
                }
            }
        }
    }

    /**
     * An in-memory blueprint of {@code shape} (cobblestone).
     */
    public static BlueprintData blueprint(ServerLevel level, Shape shape, int height) {
        List<BlockPos> blocks = blocks(shape, height);
        int sizeX = blocks.stream().mapToInt(BlockPos::getX).max().orElse(0) + 1;
        int sizeY = blocks.stream().mapToInt(BlockPos::getY).max().orElse(0) + 1;
        int sizeZ = blocks.stream().mapToInt(BlockPos::getZ).max().orElse(0) + 1;
        CompoundTag tag = new CompoundTag();
        tag.put("size", ints(sizeX, sizeY, sizeZ));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.COBBLESTONE.defaultBlockState()));
        tag.put("palette", palette);
        ListTag list = new ListTag();
        for (BlockPos pos : blocks) {
            CompoundTag block = new CompoundTag();
            block.put("pos", ints(pos.getX(), pos.getY(), pos.getZ()));
            block.putInt("state", 0);
            list.add(block);
        }
        tag.put("blocks", list);
        var template = ImmutableTemplate.create(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag, List.of());
        String name = shape.getSerializedName() + "_" + height;
        BlueprintData data = new BlueprintData(ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "generated/" + name), "generated." + name, 0);
        data.bindStructure(template);
        return data;
    }

    private static ListTag ints(int... values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }
}

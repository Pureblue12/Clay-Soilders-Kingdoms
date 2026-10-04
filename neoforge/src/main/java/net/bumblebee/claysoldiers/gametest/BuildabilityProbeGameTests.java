package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.templates.ImmutableTemplate;
import net.bumblebee.claysoldiers.dev.DevScenarios;
import net.bumblebee.claysoldiers.entity.goal.workgoal.BuildBlueprintGoal;
import net.bumblebee.claysoldiers.structure.StructureFidelity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.bumblebee.claysoldiers.gametest.TerrainScanGameTests.TERRAIN;

/**
 * Buildability probe (D61): builders place every block type the batch-1 structures' shell and fittings would use, each
 * on the support it needs, through the normal blueprint pipeline. A block placed exactly (same block, same state
 * apart from neighbour connections) is KNOWN-OK in the buildability checklist of {@code docs/STRUCTURES.md}; the
 * result of every block is written to the log ("CSR probe"). Not required: it is a measurement, and the batch-1
 * builder tests check the blocks actually used.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildabilityProbeGameTests {
    /** One probed block: the block and, when it needs one, a support block below it (or above, for hanging). */
    record Probe(String name, BlockState state, BlockState below, BlockState above) {
    }

    static List<Probe> probes() {
        BlockState none = Blocks.AIR.defaultBlockState();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        List<Probe> list = new ArrayList<>();
        java.util.function.BiConsumer<String, BlockState> plain = (name, state) -> list.add(new Probe(name, state, none, none));
        plain.accept("chest", Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        plain.accept("barrel", Blocks.BARREL.defaultBlockState());
        plain.accept("red_bed", Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST));
        plain.accept("oak_fence", Blocks.OAK_FENCE.defaultBlockState());
        plain.accept("spruce_fence", Blocks.SPRUCE_FENCE.defaultBlockState());
        plain.accept("red_carpet", Blocks.RED_CARPET.defaultBlockState());
        plain.accept("white_carpet", Blocks.WHITE_CARPET.defaultBlockState());
        plain.accept("brown_carpet", Blocks.BROWN_CARPET.defaultBlockState());
        plain.accept("furnace", Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.SOUTH));
        plain.accept("smoker", Blocks.SMOKER.defaultBlockState());
        plain.accept("crafting_table", Blocks.CRAFTING_TABLE.defaultBlockState());
        plain.accept("composter", Blocks.COMPOSTER.defaultBlockState());
        plain.accept("grindstone", Blocks.GRINDSTONE.defaultBlockState().setValue(GrindstoneBlock.FACE, AttachFace.FLOOR));
        plain.accept("glass_pane", Blocks.GLASS_PANE.defaultBlockState());
        plain.accept("hay_block", Blocks.HAY_BLOCK.defaultBlockState());
        plain.accept("oak_pressure_plate", Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
        plain.accept("oak_trapdoor", Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP));
        plain.accept("cobblestone_wall", Blocks.COBBLESTONE_WALL.defaultBlockState());
        plain.accept("cobblestone_stairs", Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        plain.accept("mossy_cobblestone", Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        plain.accept("stone_bricks", Blocks.STONE_BRICKS.defaultBlockState());
        plain.accept("bricks", Blocks.BRICKS.defaultBlockState());
        plain.accept("white_terracotta", Blocks.WHITE_TERRACOTTA.defaultBlockState());
        plain.accept("dark_oak_planks", Blocks.DARK_OAK_PLANKS.defaultBlockState());
        plain.accept("dark_oak_log", Blocks.DARK_OAK_LOG.defaultBlockState());
        plain.accept("dark_oak_stairs", Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        plain.accept("spruce_planks", Blocks.SPRUCE_PLANKS.defaultBlockState());
        plain.accept("spruce_stairs", Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        plain.accept("spruce_slab", Blocks.SPRUCE_SLAB.defaultBlockState());
        plain.accept("stripped_spruce_log", Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        plain.accept("coarse_dirt", Blocks.COARSE_DIRT.defaultBlockState());
        plain.accept("dirt", dirt);
        plain.accept("gravel", Blocks.GRAVEL.defaultBlockState());
        plain.accept("flower_pot", Blocks.FLOWER_POT.defaultBlockState());
        plain.accept("campfire_unlit", Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        plain.accept("lantern", Blocks.LANTERN.defaultBlockState());
        plain.accept("oak_leaves", Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        plain.accept("chain", Blocks.CHAIN.defaultBlockState());
        plain.accept("stone_brick_stairs", Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        plain.accept("oak_button", Blocks.OAK_BUTTON.defaultBlockState());
        list.add(new Probe("poppy", Blocks.POPPY.defaultBlockState(), dirt, none));
        list.add(new Probe("hanging_lantern", Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), none, cobble));
        return list;
    }

    /**
     * The first half of the probed blocks in one blueprint (2 apart, rows of 8), built by 3 builders from a stocked
     * chest; after the build time each block's result is logged. Succeeds when every block is placed exactly.
     */
    @GameTest(template = TERRAIN, batch = "buildability_probe", timeoutTicks = 6000, required = false)
    public static void probeBatchOneBlocksA(GameTestHelper helper) {
        probe(helper, probes().subList(0, probes().size() / 2));
    }

    /** The second half of the probed blocks (a chest holds 27 stacks, so the probe is split in two builds). */
    @GameTest(template = TERRAIN, batch = "buildability_probe", timeoutTicks = 6000, required = false)
    public static void probeBatchOneBlocksB(GameTestHelper helper) {
        probe(helper, probes().subList(probes().size() / 2, probes().size()));
    }

    private static void probe(GameTestHelper helper, List<Probe> probes) {
        ServerLevel level = helper.getLevel();
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Map<BlockPos, String> names = new LinkedHashMap<>();
        for (int i = 0; i < probes.size(); i++) {
            Probe probe = probes.get(i);
            int x = (i % 8) * 2;
            int z = (i / 8) * 2;
            // local y 0 = the ground layer; supports below sit at y 0 with the block at y 1
            int y = probe.below().isAir() ? 0 : 1;
            if (!probe.below().isAir()) {
                blocks.put(new BlockPos(x, 0, z), probe.below());
            }
            if (!probe.above().isAir()) {
                // hanging: the block hangs at y 1 under an overhang at y 2, held up by a post beside it
                y = 1;
                blocks.put(new BlockPos(x, 2, z), probe.above());
                for (int h = 0; h <= 2; h++) {
                    blocks.put(new BlockPos(x + 1, h, z), probe.above());
                }
            }
            blocks.put(new BlockPos(x, y, z), probe.state());
            names.put(new BlockPos(x, y, z), probe.name());
        }
        BlueprintData blueprint = blueprint(level, "probe_batch1_" + probes.getFirst().name(), blocks);
        BlockPos easelPos = helper.absolutePos(new BlockPos(16, 2, 4));
        EaselBlockEntity easel = DevScenarios.placeEasel(level, easelPos, Direction.NORTH, blueprint);
        List<ItemStack> items = blueprint.createServerPlan().orElseThrow().getNeededItems();
        BlockPos chestPos = helper.absolutePos(new BlockPos(16, 2, 2));
        DevScenarios.placeChest(level, chestPos, items);
        for (int i = 0; i < 3; i++) {
            DevScenarios.spawnWorker(level, chestPos.east(1 + i), DevScenarios.RED_TEAM, ClaySoldierGameTests.owner(level),
                    BuildBlueprintGoal.class, chestPos);
        }
        var settings = easel.getTemplateSettings();
        BlockPos base = EaselBlockEntity.templateBase(easel.getBlockPos(), easel.getFacing(), settings);
        helper.runAtTickTime(5_800, () -> {
            List<String> failed = new ArrayList<>();
            names.forEach((local, name) -> {
                BlockPos world = net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
                        .calculateRelativePosition(settings.convertTo(), local).offset(base);
                BlockState expected = blocks.get(local).rotate(settings.rotation());
                BlockState actual = level.getBlockState(world);
                boolean ok = StructureFidelity.matches(expected, actual);
                ClaySoldiersCommon.LOGGER.info("CSR probe: {} {} (placed {})", name, ok ? "KNOWN-OK" : "FAILED", actual);
                if (!ok) {
                    failed.add(name);
                }
            });
            if (failed.isEmpty()) {
                helper.succeed();
            } else {
                helper.fail("not placed exactly: " + failed);
            }
        });
    }

    /** An in-memory blueprint of the given blocks (local positions). */
    static BlueprintData blueprint(ServerLevel level, String name, Map<BlockPos, BlockState> blocks) {
        int sx = blocks.keySet().stream().mapToInt(BlockPos::getX).max().orElse(0) + 1;
        int sy = blocks.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0) + 1;
        int sz = blocks.keySet().stream().mapToInt(BlockPos::getZ).max().orElse(0) + 1;
        CompoundTag tag = new CompoundTag();
        tag.put("size", ints(sx, sy, sz));
        ListTag palette = new ListTag();
        List<BlockState> states = new ArrayList<>();
        ListTag list = new ListTag();
        blocks.forEach((pos, state) -> {
            int index = states.indexOf(state);
            if (index < 0) {
                states.add(state);
                palette.add(NbtUtils.writeBlockState(state));
                index = states.size() - 1;
            }
            CompoundTag block = new CompoundTag();
            block.put("pos", ints(pos.getX(), pos.getY(), pos.getZ()));
            block.putInt("state", index);
            list.add(block);
        });
        tag.put("palette", palette);
        tag.put("blocks", list);
        var template = ImmutableTemplate.create(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag, List.of());
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

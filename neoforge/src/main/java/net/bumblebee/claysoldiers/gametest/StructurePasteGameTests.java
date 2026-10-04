package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.templates.ImmutableTemplate;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.dev.DevTool;
import net.bumblebee.claysoldiers.dev.DevToolRegistry;
import net.bumblebee.claysoldiers.structure.StructureMeta;
import net.bumblebee.claysoldiers.structure.StructurePaster;
import net.bumblebee.claysoldiers.structure.StructureRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.GROUND_Y;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;
import static net.bumblebee.claysoldiers.gametest.StructureRegistryGameTests.HUT;
import static net.bumblebee.claysoldiers.gametest.StructureRegistryGameTests.HUT_FILE;
import static net.bumblebee.claysoldiers.gametest.StructureRegistryGameTests.WELL;

/**
 * Tests for the structure paste tool ({@link StructurePaster}) and its book buttons. Each asserts the CORRECT
 * behavior. All tests that paste share one batch per test, since the undo history is global.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class StructurePasteGameTests {
    /** Where the "player" stands for a paste (relative). Facing south, the hut covers x 6-10, z 4-8. */
    private static final BlockPos AT = new BlockPos(8, GROUND_Y, 2);

    /**
     * Pasting and then undoing restores every block exactly, including a chest with items (and no items are left lying
     * around).
     */
    @GameTest(template = PLATFORM, batch = "structure_paste_undo")
    public static void pasteThenUndoRestoresExactBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructurePaster.clearUndo();
        decorate(helper);
        BoundingBox area = area(helper);
        Map<BlockPos, Saved> before = save(level, area);

        StructurePaster.Pasted pasted = StructurePaster.paste(level, meta(helper, HUT), helper.absolutePos(AT), Direction.SOUTH);
        helper.assertTrue(!save(level, area).equals(before), "Setup: pasting changed nothing");
        helper.assertTrue(StructurePaster.undo(level).map(undone -> undone.equals(pasted)).orElse(false), "Undo did not undo the paste");

        assertSame(helper, before, save(level, area));
        List<net.minecraft.world.entity.item.ItemEntity> dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, net.minecraft.world.phys.AABB.of(area).inflate(2));
        helper.assertTrue(dropped.isEmpty(), "Items were dropped (a container's contents would be duplicated): "
                + dropped.stream().map(item -> item.getItem() + " at " + item.blockPosition().toShortString()).toList());
        helper.succeed();
    }

    /**
     * Two overlapping pastes undone twice restore the original blocks (undo goes back one paste at a time).
     */
    @GameTest(template = PLATFORM, batch = "structure_paste_twice")
    public static void pasteTwiceUndoTwiceRestoresOriginal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructurePaster.clearUndo();
        decorate(helper);
        BoundingBox area = area(helper);
        Map<BlockPos, Saved> before = save(level, area);

        StructurePaster.Pasted hut = StructurePaster.paste(level, meta(helper, HUT), helper.absolutePos(AT), Direction.SOUTH);
        StructurePaster.Pasted well = StructurePaster.paste(level, meta(helper, WELL), helper.absolutePos(new BlockPos(7, GROUND_Y, 5)), Direction.EAST);
        helper.assertTrue(hut.box().intersects(well.box()), "Setup: the two pastes should overlap: " + hut.box() + " / " + well.box());
        helper.assertTrue(StructurePaster.undoDepth() == 2, "Expected 2 pastes to undo, got " + StructurePaster.undoDepth());

        StructurePaster.undo(level);
        StructurePaster.undo(level);
        assertSame(helper, before, save(level, area));
        helper.assertTrue(StructurePaster.undo(level).isEmpty(), "A third undo should have nothing to undo");
        helper.succeed();
    }

    /**
     * A paste puts every block exactly where builders would place it for an easel at the same spot (same facing and
     * rotation conventions), for every direction.
     */
    @GameTest(template = PLATFORM, batch = "structure_paste_positions")
    public static void pasteMatchesBuilderPositions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructurePaster.clearUndo();
        StructureTemplate template = level.getStructureManager().get(HUT_FILE).orElseThrow();
        ServerBlueprintPlan plan = ImmutableTemplate.create(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                template.save(new CompoundTag()), List.of()).createServer().orElseThrow();
        BlockPos at = helper.absolutePos(new BlockPos(8, GROUND_Y, 8));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            StructurePaster.Placement placement = StructurePaster.placement(at, direction, template.getSize());
            Set<BlockPos> builderTargets = new HashSet<>(plan.targetPositions(placement.base(), placement.settings()));
            StructurePaster.Pasted pasted = StructurePaster.paste(level, meta(helper, HUT), at, direction);
            Set<BlockPos> pastedBlocks = new HashSet<>();
            BlockPos.betweenClosedStream(pasted.box()).forEach(pos -> {
                BlockState state = level.getBlockState(pos);
                boolean upperDoor = state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
                if (!state.isAir() && !upperDoor) {
                    pastedBlocks.add(pos.immutable());
                }
            });
            helper.assertTrue(pastedBlocks.equals(builderTargets), "Facing " + direction + ": pasted blocks differ from builder targets. Only pasted: "
                    + diff(pastedBlocks, builderTargets) + ", only builders: " + diff(builderTargets, pastedBlocks));
            StructurePaster.undo(level);
        }
        helper.succeed();
    }

    /**
     * Reloading picks up a structure file edited with a structure block (saved into the world's generated folder):
     * the next paste uses the edited version, and removing the edit and reloading brings the original back.
     */
    @GameTest(template = PLATFORM, batch = "structure_paste_reload")
    public static void reloadPicksUpEditedStructure(GameTestHelper helper) throws IOException {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        StructurePaster.clearUndo();
        Path edited = server.getStructureManager().createAndValidatePathToGeneratedStructure(HUT_FILE, ".nbt");
        try {
            CompoundTag tag = level.getStructureManager().get(HUT_FILE).orElseThrow().save(new CompoundTag());
            ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
            for (int i = 0; i < palette.size(); i++) {
                if (palette.getCompound(i).getString("Name").equals("minecraft:oak_planks")) {
                    palette.getCompound(i).putString("Name", "minecraft:gold_block");
                }
            }
            Files.createDirectories(edited.getParent());
            NbtIo.writeCompressed(tag, edited);

            StructureRegistry.reload(server);
            StructurePaster.Pasted pasted = StructurePaster.paste(level, meta(helper, HUT), helper.absolutePos(AT), Direction.SOUTH);
            BlockPos roof = new BlockPos(pasted.box().minX(), pasted.box().maxY(), pasted.box().minZ());
            helper.assertTrue(level.getBlockState(roof).is(Blocks.GOLD_BLOCK), "The edited structure (gold roof) was not used after a reload, got " + level.getBlockState(roof));
            StructurePaster.undo(level);

            Files.delete(edited);
            StructureRegistry.reload(server);
            StructurePaster.paste(level, meta(helper, HUT), helper.absolutePos(AT), Direction.SOUTH);
            helper.assertTrue(level.getBlockState(roof).is(Blocks.OAK_PLANKS), "After removing the edit and reloading, the original was not used, got " + level.getBlockState(roof));
            StructurePaster.undo(level);
        } finally {
            Files.deleteIfExists(edited);
            StructureRegistry.reload(server);
        }
        helper.succeed();
    }

    /**
     * A placeholder (design TBD, no geometry) can't be pasted; the reason says so.
     */
    @GameTest(template = PLATFORM, batch = "structure_paste_tbd")
    public static void placeholderCannotBePasted(GameTestHelper helper) {
        StructureMeta keep = meta(helper, ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "keep"));
        try {
            StructurePaster.paste(helper.getLevel(), keep, helper.absolutePos(AT), Direction.SOUTH);
            helper.fail("Pasting a placeholder should be refused");
        } catch (IllegalArgumentException e) {
            helper.assertTrue(e.getMessage().contains("TBD"), "The reason should say the design is TBD: " + e.getMessage());
        }
        helper.succeed();
    }

    /**
     * Recorder: paste, undo and reload are logged in the STRUCTURE category.
     */
    @GameTest(template = PLATFORM, batch = "structure_paste_log")
    public static void pasteUndoAndReloadAreLogged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DebugLog.reset();
        DebugCategory structure = DebugCategory.byId("STRUCTURE").orElseThrow();
        DebugLog.setEnabled(structure, true, level.getServer());
        StructurePaster.clearUndo();
        StructurePaster.paste(level, meta(helper, HUT), helper.absolutePos(AT), Direction.SOUTH);
        StructurePaster.undo(level);
        StructureRegistry.reload(level.getServer());
        boolean pasted = DebugLog.contains(structure, "pasted csr:test_hut");
        boolean undone = DebugLog.contains(structure, "undid paste of csr:test_hut");
        boolean reloaded = DebugLog.contains(structure, "reloaded");
        DebugLog.reset();
        helper.assertTrue(pasted && undone && reloaded, "Missing STRUCTURE events: pasted=%s undone=%s reloaded=%s".formatted(pasted, undone, reloaded));
        helper.succeed();
    }

    /**
     * The book's Structures category has one paste button per ready structure, generated from the registry.
     */
    @GameTest(template = PLATFORM)
    public static void bookHasOnePasteButtonPerReadyStructure(GameTestHelper helper) {
        DevToolRegistry registry = DevToolRegistry.defaults();
        DevTool place = registry.tools().stream().filter(tool -> tool.command().equals("/csr dev structure place")).findFirst().orElse(null);
        helper.assertTrue(place != null, "No /csr dev structure place tool in the registry");
        Set<String> buttons = new HashSet<>();
        registry.buttons(place, helper.getLevel().registryAccess()).forEach(button -> buttons.add(button.command()));
        Set<String> expected = new HashSet<>();
        StructureRegistry.get(helper.getLevel().getServer()).ready().forEach(meta -> expected.add("/csr dev structure place " + meta.id()));
        helper.assertTrue(buttons.equals(expected), "Paste buttons " + buttons + " don't match the ready structures " + expected);
        helper.succeed();
    }

    // Helpers

    private static StructureMeta meta(GameTestHelper helper, ResourceLocation id) {
        return StructureRegistry.get(helper.getLevel().getServer()).byId(id).orElseThrow(() -> new IllegalStateException("No structure " + id));
    }

    /**
     * Puts some blocks where the pastes go: stone, a torch on it, and a chest with items.
     */
    private static void decorate(GameTestHelper helper) {
        helper.setBlock(new BlockPos(7, GROUND_Y, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(7, GROUND_Y + 1, 5), Blocks.TORCH);
        helper.setBlock(new BlockPos(9, GROUND_Y, 6), Blocks.CHEST);
        BlockEntity chest = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(9, GROUND_Y, 6)));
        ((Container) Objects.requireNonNull(chest)).setItem(3, new ItemStack(Items.DIAMOND, 5));
        helper.setBlock(new BlockPos(10, GROUND_Y + 2, 4), Blocks.GLASS);
    }

    /** The blocks both test pastes can touch, plus a margin. */
    private static BoundingBox area(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(new BlockPos(4, GROUND_Y - 1, 2));
        BlockPos max = helper.absolutePos(new BlockPos(13, GROUND_Y + 5, 10));
        return BoundingBox.fromCorners(min, max);
    }

    private record Saved(BlockState state, @Nullable CompoundTag blockEntity) {
    }

    private static Map<BlockPos, Saved> save(ServerLevel level, BoundingBox area) {
        Map<BlockPos, Saved> saved = new HashMap<>();
        BlockPos.betweenClosedStream(area).forEach(pos -> {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            saved.put(pos.immutable(), new Saved(level.getBlockState(pos),
                    blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess())));
        });
        return saved;
    }

    private static void assertSame(GameTestHelper helper, Map<BlockPos, Saved> expected, Map<BlockPos, Saved> actual) {
        for (Map.Entry<BlockPos, Saved> entry : expected.entrySet()) {
            Saved now = actual.get(entry.getKey());
            helper.assertTrue(entry.getValue().equals(now), "Not restored at " + entry.getKey().toShortString() + ": expected "
                    + entry.getValue() + ", got " + now);
        }
    }

    private static Set<BlockPos> diff(Set<BlockPos> a, Set<BlockPos> b) {
        Set<BlockPos> only = new HashSet<>(a);
        only.removeAll(b);
        return only;
    }
}

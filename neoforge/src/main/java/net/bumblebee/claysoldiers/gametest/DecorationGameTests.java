package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.BlueprintTemplateSettings;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.dev.DevScenarios;
import net.bumblebee.claysoldiers.structure.StructureBlueprints;
import net.bumblebee.claysoldiers.structure.StructureDecoration;
import net.bumblebee.claysoldiers.structure.StructureMeta;
import net.bumblebee.claysoldiers.structure.StructureRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The decoration step (roadmap stage 5a, {@link StructureDecoration}) with the test garden ({@code csr:test_garden},
 * made by {@code tools/make_test_structures.py}): a water basin, a potted poppy, a sign reading "Clay / Kingdom", a
 * banner with two patterns, an item frame holding an apple and an armor stand with an iron helmet and chestplate. Each
 * asserts the CORRECT behavior.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class DecorationGameTests {
    static final ResourceLocation GARDEN = ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "test_garden");

    /**
     * Builders get no block without an item (water, the potted poppy) and no entity; those, the sign's text and the
     * banner's patterns are the decoration.
     */
    @GameTest(template = ClaySoldierGameTests.PLATFORM)
    public static void decorationIsLeftOutOfTheBuildersBlueprint(GameTestHelper helper) {
        ServerBlueprintPlan plan = gardenPlan(helper.getLevel());
        List<String> needed = plan.getNeededItems().stream().map(stack -> stack.getItem().toString()).toList();
        helper.assertFalse(plan.getNeededItems().stream().anyMatch(stack -> stack.is(Items.AIR) || stack.is(Items.FLOWER_POT)),
                "builders should not be asked for the water or the potted poppy, needed: " + needed);
        helper.assertTrue(plan.getNeededItems().stream().anyMatch(stack -> stack.is(Items.OAK_SIGN))
                && plan.getNeededItems().stream().anyMatch(stack -> stack.is(Items.RED_BANNER)), "builders should still place the sign and the banner, needed: " + needed);
        StructureDecoration decoration = plan.decoration();
        helper.assertTrue(decoration != null, "the garden has no decoration");
        helper.assertTrue(decoration.blockCount() == 4 && decoration.entityCount() == 2, "expected 4 decoration blocks (water, potted poppy,"
                + " sign, banner) and 2 entities, got " + decoration.blockCount() + " and " + decoration.entityCount());
        helper.succeed();
    }

    /**
     * An unfinished build's decoration is saved with the easel's plan and comes back after a reload; a finished build
     * hands it out once and then has none to save.
     */
    @GameTest(template = ClaySoldierGameTests.PLATFORM)
    public static void decorationSurvivesASaveAndReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CompoundTag saved = gardenPlan(level).save(new CompoundTag());
        ServerBlueprintPlan loaded = ServerBlueprintPlan.load(saved, level.registryAccess());
        helper.assertTrue(loaded.decoration() != null && loaded.decoration().blockCount() == 4 && loaded.decoration().entityCount() == 2,
                "the decoration was lost in a save and reload");
        helper.assertTrue(loaded.takeDecoration() == null, "an unfinished build handed out its decoration");
        helper.assertTrue(loaded.decoration() != null, "asking an unfinished build took its decoration away");
        helper.succeed();
    }

    /**
     * End to end, the way it's played: {@code /csr dev structure build test_garden} (3 builders, a chest kept stocked).
     * Nothing is added while the build is unfinished; once the last block is placed the garden has its water, potted
     * poppy, sign text, banner patterns, item frame with its apple and one armor stand, and they are added exactly once.
     */
    @GameTest(template = "gametest/structure_build", timeoutTicks = 8000, batch = "decoration_build", skyAccess = true)
    public static void buildersBuildTheGardenAndItIsDecorated(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureMeta meta = StructureRegistry.get(level.getServer()).byId(GARDEN)
                .orElseThrow(() -> new GameTestAssertException("no registry entry " + GARDEN));
        DevScenarios.StructureBuildResult setup = DevScenarios.structureBuild(level, ClaySoldierGameTests.owner(level),
                helper.absolutePos(new BlockPos(24, 2, 3)), Direction.SOUTH, meta, 1);
        EaselBlockEntity easel = setup.easel();
        BlueprintTemplateSettings settings = easel.getTemplateSettings();
        BlockPos base = EaselBlockEntity.templateBase(easel.getBlockPos(), easel.getFacing(), settings);
        long[] finishedAt = {-1};
        boolean[] passed = {false};
        EveryTick.run(helper, () -> {
            setup.supply().tick();
            if (passed[0]) {
                return;
            }
            if (!easel.isFinished()) {
                List<String> early = present(level, base, settings);
                if (!early.isEmpty()) {
                    throw new GameTestAssertException("decoration added to an unfinished build: " + early);
                }
                return;
            }
            if (finishedAt[0] < 0) {
                finishedAt[0] = helper.getTick();
            }
            // A few ticks after finishing: everything there, once.
            if (helper.getTick() - finishedAt[0] < 20) {
                return;
            }
            List<String> missing = missing(level, base, settings);
            if (!missing.isEmpty()) {
                throw new GameTestAssertException("finished, but the decoration is missing: " + missing);
            }
            helper.assertTrue(easel.getPlan() instanceof ServerBlueprintPlan plan && plan.decoration() == null,
                    "the finished build still holds its decoration (it could be added twice)");
            passed[0] = true;
            helper.runAfterDelay(1, helper::succeed);
        });
    }

    private static ServerBlueprintPlan gardenPlan(ServerLevel level) {
        StructureMeta meta = StructureRegistry.get(level.getServer()).byId(GARDEN)
                .orElseThrow(() -> new GameTestAssertException("no registry entry " + GARDEN));
        BlueprintData blueprint = StructureBlueprints.blueprint(level, meta, 1);
        return blueprint.createServerPlan().orElseThrow();
    }

    /** The world position of the garden's local position (x, y, z). */
    private static BlockPos at(BlockPos base, BlueprintTemplateSettings settings, int x, int y, int z) {
        return StructureTemplate.calculateRelativePosition(settings.convertTo(), new BlockPos(x, y, z)).offset(base);
    }

    /** The decoration already in the world (none may be there before the build is finished). */
    private static List<String> present(ServerLevel level, BlockPos base, BlueprintTemplateSettings settings) {
        List<String> found = new ArrayList<>();
        if (level.getFluidState(at(base, settings, 2, 0, 2)).is(Fluids.WATER)) {
            found.add("water");
        }
        if (level.getBlockState(at(base, settings, 4, 1, 1)).is(Blocks.POTTED_POPPY)) {
            found.add("potted poppy");
        }
        if (!frames(level, base, settings).isEmpty()) {
            found.add("item frame");
        }
        if (!stands(level, base, settings).isEmpty()) {
            found.add("armor stand");
        }
        if (level.getBlockEntity(at(base, settings, 0, 1, 3)) instanceof SignBlockEntity sign
                && !sign.getFrontText().getMessage(0, false).getString().isEmpty()) {
            found.add("sign text");
        }
        return found;
    }

    /** The decoration not (exactly once) in the world. */
    private static List<String> missing(ServerLevel level, BlockPos base, BlueprintTemplateSettings settings) {
        List<String> missing = new ArrayList<>();
        BlockPos water = at(base, settings, 2, 0, 2);
        if (!level.getFluidState(water).is(Fluids.WATER) || !level.getFluidState(water).isSource()) {
            missing.add("water source at " + water + " (" + level.getBlockState(water) + ")");
        }
        BlockPos pot = at(base, settings, 4, 1, 1);
        if (!level.getBlockState(pot).is(Blocks.POTTED_POPPY)) {
            missing.add("potted poppy at " + pot + " (" + level.getBlockState(pot) + ")");
        }
        BlockPos signPos = at(base, settings, 0, 1, 3);
        if (!(level.getBlockEntity(signPos) instanceof SignBlockEntity sign
                && sign.getFrontText().getMessage(0, false).getString().equals("Clay")
                && sign.getFrontText().getMessage(1, false).getString().equals("Kingdom"))) {
            missing.add("sign text \"Clay / Kingdom\" at " + signPos + " (" + level.getBlockState(signPos) + ")");
        }
        BlockPos bannerPos = at(base, settings, 4, 2, 3);
        if (!(level.getBlockEntity(bannerPos) instanceof BannerBlockEntity banner && banner.getPatterns().layers().size() == 2)) {
            missing.add("2 banner patterns at " + bannerPos + " (" + level.getBlockState(bannerPos) + ")");
        }
        List<ItemFrame> frames = frames(level, base, settings);
        BlockPos frameCell = at(base, settings, 2, 1, 3);
        if (frames.size() != 1 || !frames.getFirst().getItem().is(Items.APPLE) || !frames.getFirst().blockPosition().equals(frameCell)) {
            missing.add("one item frame with an apple in " + frameCell + ", found: "
                    + frames.stream().map(frame -> frame.blockPosition() + " " + frame.getItem()).toList());
        }
        List<ArmorStand> stands = stands(level, base, settings);
        if (stands.size() != 1 || !stands.getFirst().getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)) {
            missing.add("one armor stand with an iron helmet, found: "
                    + stands.stream().map(stand -> stand.blockPosition() + " " + stand.getItemBySlot(EquipmentSlot.HEAD)).toList());
        }
        return missing;
    }

    private static AABB box(BlockPos base, BlueprintTemplateSettings settings) {
        return AABB.encapsulatingFullBlocks(at(base, settings, 0, 0, 0), at(base, settings, 4, 2, 4)).inflate(1);
    }

    private static List<ItemFrame> frames(ServerLevel level, BlockPos base, BlueprintTemplateSettings settings) {
        return level.getEntitiesOfClass(ItemFrame.class, box(base, settings));
    }

    private static List<ArmorStand> stands(ServerLevel level, BlockPos base, BlueprintTemplateSettings settings) {
        return level.getEntitiesOfClass(ArmorStand.class, box(base, settings));
    }
}

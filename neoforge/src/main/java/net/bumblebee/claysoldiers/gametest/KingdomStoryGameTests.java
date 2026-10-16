package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KingFounding;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.KingdomBuilding;
import net.bumblebee.claysoldiers.kingdom.KingdomEconomy;
import net.bumblebee.claysoldiers.kingdom.KingdomKeep;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.GROUND_Y;

/**
 * The whole story the way a player plays it, with no dev shortcuts: soldiers stand scattered on open grass, one picks up
 * a dropped gold ingot and founds a kingdom by himself, and from then on the kingdom does everything: it builds its keep,
 * lets the keep's builders go, builds a lumber camp and a wheat field with whoever is free, its woodcutters fell trees and
 * its farmer grows wheat, and it pays for a cottage with that wood and wheat.
 * <p>
 * Found in Carlton's game on 2026-10-16: each step had its own test with builders set up beside their easel, so the
 * hand-overs between steps (builders far from the next easel, builders standing by the finished keep) were never run.
 * The buildings are small stand-ins (the keep is the test hut, the camp a cobblestone pad, the field a 3 by 3 dirt patch,
 * the cottage the test hut again) so the story fits in one test; the real structures are built by
 * {@link StructureBuildGameTests}. The server sprints through it (results are in ticks), so it takes little real time.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = ClaySoldiersCommon.MOD_ID)
public class KingdomStoryGameTests {
    private static final String BATCH = "kingdom_story";
    private static final int TIMEOUT = 36000;
    private static final ResourceLocation TEAM = csr("purple");
    private static final ResourceLocation KEEP = KingdomKeepGameTests.HUT;
    private static final ResourceLocation CAMP = csr("test_camp");
    private static final ResourceLocation FIELD = csr("test_field");
    private static final ResourceLocation COTTAGE = csr("test_cottage");
    /** Small enough that the outer buildings stay inside the 48 by 48 test area. */
    private static final String TUNING = """
            {"kingdom.claim_radius": 14, "economy.plot.outer_reach": 2, "terrain.search.radius": 4,
             "economy.lumber.radius": 40, "economy.farm.tend_ticks": 1, "economy.food_per_building": 3}""";
    private static final BlockPos CENTER = new BlockPos(24, GROUND_Y, 24);
    /** Trees in three corners, away from where the buildings go. */
    private static final List<BlockPos> TREES = List.of(new BlockPos(4, GROUND_Y, 4), new BlockPos(43, GROUND_Y, 4), new BlockPos(4, GROUND_Y, 43));

    /** Runs every server tick while the story test runs (the framework's own per-tick checks cost too much over 36,000 ticks). */
    @Nullable
    private static volatile Runnable perTick;

    @GameTest(template = StructureBuildGameTests.TEMPLATE, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void kingdomGrowsFromOneGoldIngot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        disband(server);
        GameTestTuning.load(TUNING);
        KingdomKeep.useKeepForTests(KEEP);
        KingdomEconomy.usePlanForTests(List.of(
                new KingdomEconomy.Slot(CAMP, 1, true, true, KingdomEconomy.Job.WOODCUTTER),
                new KingdomEconomy.Slot(FIELD, 1, true, true, KingdomEconomy.Job.FARMER),
                new KingdomEconomy.Slot(COTTAGE, 1, false, false, null)));
        KingFounding.enableInTestsForTests(true);

        List<BlockPos> trees = new ArrayList<>();
        for (BlockPos tree : TREES) {
            trees.add(plantTree(helper, tree));
        }
        // Eight plain soldiers (no wheat) scattered around the middle, as a player drops them; the one in the middle gets the gold.
        ClaySoldierEntity first = KingdomGameTests.DevSoldier.spawnPlain(helper, CENTER, TEAM);
        for (int[] offset : new int[][]{{9, 0}, {-9, 0}, {0, 9}, {0, -9}, {7, 7}, {-7, -7}, {7, -7}}) {
            KingdomGameTests.DevSoldier.spawnPlain(helper, CENTER.offset(offset[0], 0, offset[1]), TEAM);
        }
        level.addFreshEntity(new ItemEntity(level, first.getX(), first.getY() + 0.2, first.getZ(), new ItemStack(Items.GOLD_INGOT)));
        server.tickRateManager().requestGameToSprint(TIMEOUT + 200);

        int[] ticks = {0};
        boolean[] done = {false};
        // A replanted sapling may grow into a tree again before the story ends: remember each tree once it was felled.
        Set<BlockPos> felled = new HashSet<>();
        perTick = () -> {
            if (done[0] || ++ticks[0] % 20 != 0) {
                return;
            }
            Optional<Kingdom> found = Kingdoms.byTeam(server, TEAM);
            if (found.isEmpty()) {
                return;
            }
            // The server's own kingdom tick is off in a GameTest server: the test runs it.
            KingdomKeep.update(level, found.get());
            KingdomEconomy.update(level, found.get());
            List<KingdomBuilding> buildings = found.get().buildings();
            trees.stream().filter(base -> level.getBlockState(base).is(Blocks.OAK_SAPLING)).forEach(felled::add);
            if (buildings.size() == 4 && buildings.stream().allMatch(b -> b.state() == KingdomBuilding.State.BUILT) && felled.size() >= 2) {
                done[0] = true;
                perTick = null;
                helper.succeed();
            }
        };
        helper.runAtTickTime(TIMEOUT - 20, () -> {
            if (!done[0]) {
                perTick = null;
                helper.fail(story(level, trees, felled));
            }
        });
    }

    /** Where the story stands, for the failure message. */
    private static String story(ServerLevel level, List<BlockPos> trees, Set<BlockPos> felled) {
        MinecraftServer server = level.getServer();
        Optional<Kingdom> kingdom = Kingdoms.byTeam(server, TEAM);
        if (kingdom.isEmpty()) {
            return "No kingdom was founded (did a soldier pick up the gold ingot?)";
        }
        List<String> lines = new ArrayList<>();
        lines.add("Buildings: " + kingdom.get().buildings().stream().map(b -> b.structure().getPath() + " " + b.state()).toList());
        lines.addAll(KingdomKeep.describe(server, kingdom.get()).stream().map(line -> "Keep: " + line).toList());
        lines.addAll(KingdomEconomy.describe(server, kingdom.get()).stream().map(line -> "Economy: " + line).toList());
        lines.add("Trees felled and replanted: " + felled.size() + " of " + trees.size());
        lines.add("Stockpile: " + kingdom.get().stockpile());
        return String.join("; ", lines);
    }

    /** A 4-log oak with natural leaves, as woodcutters fell them; returns where its trunk stands. */
    private static BlockPos plantTree(GameTestHelper helper, BlockPos at) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(at);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dy = 2; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlockAndUpdate(base.offset(dx, dy, dz), leaves);
                }
            }
        }
        level.setBlockAndUpdate(base.above(4), leaves);
        for (int dy = 0; dy < 4; dy++) {
            level.setBlockAndUpdate(base.above(dy), Blocks.OAK_LOG.defaultBlockState());
        }
        return base;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Runnable run = perTick;
        if (run != null) {
            run.run();
        }
    }

    @AfterBatch(batch = BATCH)
    public static void restore(ServerLevel level) {
        perTick = null;
        MinecraftServer server = level.getServer();
        server.tickRateManager().stopSprinting();
        KingFounding.enableInTestsForTests(false);
        KingdomKeep.useKeepForTests(null);
        KingdomEconomy.usePlanForTests(null);
        GameTestTuning.restore(server);
        disband(server);
    }

    private static void disband(MinecraftServer server) {
        Kingdoms.byTeam(server, TEAM).ifPresent(kingdom -> Kingdoms.disband(server, TEAM, null));
    }

    private static ResourceLocation csr(String path) {
        return ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, path);
    }
}

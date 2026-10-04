package net.bumblebee.claysoldiers.dev;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.structure.StructurePaster;
import net.bumblebee.claysoldiers.terrain.GradingMethod;
import net.bumblebee.claysoldiers.terrain.GradingPlanner;
import net.bumblebee.claysoldiers.terrain.SiteRules;
import net.bumblebee.claysoldiers.terrain.SiteSearch;
import net.bumblebee.claysoldiers.terrain.Terrain;
import net.bumblebee.claysoldiers.terrain.TerrainScan;
import net.bumblebee.claysoldiers.terrain.TerrainScanner;
import net.bumblebee.claysoldiers.terrain.TestTerrain;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The "Terrain" dev book category (stage 2 terrain spike): scan the ground around you, judge the keep site where you
 * stand and find the best one nearby, build test terrains, and apply grading methods to the keep site instantly. Every
 * change can be undone ({@code /csr dev terrain undo}, the same undo as the structure paste).
 */
final class TerrainTools {
    static final String TERRAIN = "terrain";
    /** Test terrains are built this far ahead of you, so you stand just outside them. */
    private static final int MAKE_DISTANCE = TestTerrain.RADIUS + 3;

    private TerrainTools() {
    }

    static void register(DevToolRegistry registry) {
        registry.addCategory(new DevCategory(TERRAIN, "Terrain",
                "Keep sites and grading (stage 2 spike). Stand where the keep would go.", false));
        registry.addTool(new DevTool("terrain_scan", TERRAIN, Component.literal("Scan around me"),
                "Scans the ground within terrain.scan_radius and reports heights, water, lava, trees, biome and the cost.",
                List.of("terrain", "scan"), false, null, TerrainTools::scan));
        registry.addTool(new DevTool("terrain_site", TERRAIN, Component.literal("Score this site"),
                "Judges a keep site centered where you stand and shows the best site nearby.",
                List.of("terrain", "site"), false, null, TerrainTools::site));
        registry.addTool(new DevTool("terrain_make", TERRAIN, Component.literal("Make"),
                "Builds a test terrain " + MAKE_DISTANCE + " blocks ahead of you (undo: Undo terrain change).",
                List.of("terrain", "make"), false, choices("shape", TestTerrain.Shape.values()), TerrainTools::make));
        registry.addTool(new DevTool("terrain_grade", TERRAIN, Component.literal("Grade"),
                "Applies a grading method to the keep site where you stand, instantly (undo: Undo terrain change).",
                List.of("terrain", "grade"), false, choices("method", GradingMethod.values()), TerrainTools::grade));
        registry.addTool(new DevTool("terrain_undo", TERRAIN, Component.literal("Undo terrain change"),
                "Puts back the blocks the last terrain change (or structure paste) replaced.",
                List.of("terrain", "undo"), false, null, TerrainTools::undo));
    }

    private static DevTool.Argument choices(String name, StringRepresentable[] values) {
        List<String> ids = Arrays.stream(values).map(StringRepresentable::getSerializedName).toList();
        return new DevTool.Argument(name, StringArgumentType.word(), (context, builder) -> SharedSuggestionProvider.suggest(ids, builder),
                access -> ids.stream().map(id -> new DevTool.Choice(Component.literal(id), id)).toList());
    }

    private static int scan(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos at = BlockPos.containing(source.getPosition());
        int radius = Tuning.TERRAIN_SCAN_RADIUS.get();
        TerrainScan scan = TerrainScanner.scan(source.getLevel(), at, radius);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int water = 0;
        int deepest = 0;
        int lava = 0;
        int trees = 0;
        for (int x = scan.minX(); x <= scan.maxX(); x++) {
            for (int z = scan.minZ(); z <= scan.maxZ(); z++) {
                if (!scan.known(x, z)) {
                    continue;
                }
                min = Math.min(min, scan.ground(x, z));
                max = Math.max(max, scan.ground(x, z));
                water += scan.waterDepth(x, z) > 0 ? 1 : 0;
                deepest = Math.max(deepest, scan.waterDepth(x, z));
                lava += scan.lava(x, z) ? 1 : 0;
                trees += scan.trees(x, z) ? 1 : 0;
            }
        }
        int columns = scan.size() * scan.size();
        report(context, "Scan of %d x %d columns around %d, %d: %d loaded, %d unknown, %.2f ms.".formatted(scan.size(), scan.size(),
                at.getX(), at.getZ(), scan.knownColumns(), columns - scan.knownColumns(), scan.costNanos() / 1_000_000.0));
        if (scan.knownColumns() > 0) {
            report(context, "Ground y %d to %d; water on %d columns (deepest %d); lava on %d; trees on %d; biome %s. Here: ground y %d, %s."
                    .formatted(min, max, water, deepest, lava, trees, scan.biome().name().toLowerCase(),
                            scan.ground(at.getX(), at.getZ()), scan.surfaceBlock(at.getX(), at.getZ())));
        }
        return 1;
    }

    private static int site(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos at = BlockPos.containing(source.getPosition());
        SiteRules rules = SiteRules.current();
        Terrain.SiteCheck check = Terrain.checkSite(source.getLevel(), at, false);
        String size = (2 * rules.half() + 1) + " x " + (2 * rules.half() + 1);
        if (check.score().ok()) {
            report(context, "Keep site here (" + size + "): " + check.score().describe());
        } else {
            fail(context, "Keep site here (" + size + "): " + check.score().describe());
        }
        TerrainScan wide = TerrainScanner.scan(source.getLevel(), at, rules.searchScanRadius());
        SiteSearch.Result result = SiteSearch.search(wide, at.getX(), at.getZ(), rules);
        report(context, "Nearby (within " + rules.searchRadius() + "): " + result.summary() + ".");
        return check.score().ok() ? 1 : 0;
    }

    private static int make(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        TestTerrain.Shape shape;
        try {
            shape = TestTerrain.Shape.byId(StringArgumentType.getString(context, "shape"));
        } catch (IllegalArgumentException e) {
            fail(context, e.getMessage() + "; use one of " + Arrays.stream(TestTerrain.Shape.values()).map(TestTerrain.Shape::getSerializedName).toList());
            return 0;
        }
        Direction facing = source.getEntity() != null ? source.getEntity().getDirection() : Direction.NORTH;
        BlockPos feet = BlockPos.containing(source.getPosition());
        BlockPos center = feet.relative(facing, MAKE_DISTANCE).below();
        BoundingBox box = Terrain.makeTestTerrain(source.getLevel(), center, shape);
        report(context, "Built the %s centered at %d %d %d (%d blocks %s of you), %s. Stand on its center, then Scan, Score or Grade. Undo: /csr dev terrain undo."
                .formatted(shape.getSerializedName(), center.getX(), center.getY() + 1, center.getZ(), MAKE_DISTANCE, facing.getName(),
                        StructurePaster.describe(box)));
        return 1;
    }

    private static int grade(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        GradingMethod method;
        try {
            method = GradingMethod.byId(StringArgumentType.getString(context, "method"));
        } catch (IllegalArgumentException e) {
            fail(context, e.getMessage() + "; use one of " + Arrays.stream(GradingMethod.values()).map(GradingMethod::getSerializedName).toList());
            return 0;
        }
        Terrain.Graded graded = Terrain.grade(source.getLevel(), BlockPos.containing(source.getPosition()), method);
        if (!graded.applied()) {
            fail(context, "Not applied: " + graded.plan().describe());
            return 0;
        }
        report(context, "Applied " + graded.plan().describe() + ". Changed: " + graded.placed() + " placed, " + graded.removed() + " removed"
                + (graded.markers() > 0 ? ", " + graded.markers() + " corner markers (torches)" : "") + ". Undo: /csr dev terrain undo.");
        report(context, "Soldiers: " + GradingPlanner.howSoldiers(method));
        return 1;
    }

    private static int undo(CommandContext<CommandSourceStack> context) {
        Optional<StructurePaster.Pasted> undone = StructurePaster.undo(context.getSource().getLevel());
        if (undone.isEmpty()) {
            fail(context, "Nothing to undo.");
            return 0;
        }
        report(context, "Undid %s at %s (%d more to undo).".formatted(undone.get().id(), undone.get().at().toShortString(), StructurePaster.undoDepth()));
        return 1;
    }

    private static void report(CommandContext<CommandSourceStack> context, String line) {
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        ClaySoldiersCommon.LOGGER.info("[CSR-DEBUG] {}", line);
    }

    private static void fail(CommandContext<CommandSourceStack> context, String line) {
        context.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.RED), false);
        ClaySoldiersCommon.LOGGER.info("[CSR-DEBUG] {}", line);
    }
}

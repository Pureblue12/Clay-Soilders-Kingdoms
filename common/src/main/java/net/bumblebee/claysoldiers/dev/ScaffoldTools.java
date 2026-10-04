package net.bumblebee.claysoldiers.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.entity.goal.workgoal.BuildBlueprintGoal;
import net.bumblebee.claysoldiers.scaffold.ScaffoldInput;
import net.bumblebee.claysoldiers.scaffold.ScaffoldJob;
import net.bumblebee.claysoldiers.scaffold.ScaffoldPlan;
import net.bumblebee.claysoldiers.scaffold.ScaffoldPlanner;
import net.bumblebee.claysoldiers.scaffold.ScaffoldSavedData;
import net.bumblebee.claysoldiers.scaffold.Scaffolds;
import net.bumblebee.claysoldiers.scaffold.TestStructures;
import net.bumblebee.claysoldiers.structure.StructurePaster;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The "Scaffolding" dev book category (stage 3 tall construction spike): set up a test tower or wall for builders,
 * print the scaffold plans of every approach, list and clean up scaffold jobs, and reset the test area with the
 * structure paste's undo.
 */
final class ScaffoldTools {
    static final String SCAFFOLDING = "scaffolding";
    private static final int DEFAULT_HEIGHT = 20;
    /** Scaffold material put in the test chest (more than any plan within budget needs). */
    private static final int SCAFFOLD_STOCK = 128;
    private static final int BUILDERS = 3;
    /** How far status and cleanup look for scaffold jobs. */
    private static final int NEAR = 64;

    private ScaffoldTools() {
    }

    static void register(DevToolRegistry registry) {
        registry.addCategory(new DevCategory(SCAFFOLDING, "Scaffolding",
                "Tall builds (stage 3 spike). Builds go ahead of you, the way you face.", false));
        registry.addTool(new DevTool("scaffold_build", SCAFFOLDING, Component.literal("Build test"),
                "Sets up an easel with a test tower or wall (height after the shape, default " + DEFAULT_HEIGHT
                        + "), a stocked chest and " + BUILDERS + " builders. Undo resets the blocks (soldiers stay: use Cleanup → Remove soldiers + easels).",
                List.of("scaffold", "build"), false, shapeArgument(), ScaffoldTools::build));
        registry.addTool(new DevTool("scaffold_plan", SCAFFOLDING, Component.literal("Plan only"),
                "Prints the scaffold plan of every approach for a test structure on flat ground (nothing is built).",
                List.of("scaffold", "plan"), false, shapeArgument(), ScaffoldTools::plan));
        registry.addTool(new DevTool("scaffold_status", SCAFFOLDING, Component.literal("Scaffold jobs nearby"),
                "Lists the scaffold jobs within " + NEAR + " blocks: placed and planned blocks.",
                List.of("scaffold", "status"), false, null, ScaffoldTools::status));
        registry.addTool(new DevTool("scaffold_cleanup", SCAFFOLDING, Component.literal("Remove scaffold nearby"),
                "Removes the scaffold of every job within " + NEAR + " blocks at once (player-changed blocks are kept).",
                List.of("scaffold", "cleanup"), false, null, ScaffoldTools::cleanup));
        registry.addTool(new DevTool("scaffold_undo", SCAFFOLDING, Component.literal("Undo test build"),
                "Puts back the blocks the last test build (or paste, or terrain change) replaced.",
                List.of("scaffold", "undo"), false, null, ScaffoldTools::undo));
    }

    private static DevTool.Argument shapeArgument() {
        List<String> ids = Arrays.stream(TestStructures.Shape.values()).map(TestStructures.Shape::getSerializedName).toList();
        return new DevTool.Argument("shape", StringArgumentType.word(), (context, builder) -> SharedSuggestionProvider.suggest(ids, builder),
                access -> ids.stream().map(id -> new DevTool.Choice(Component.literal(id), id)).toList())
                .withOptionalExtra("height", IntegerArgumentType.integer(2, 64), null);
    }

    private static Optional<TestStructures.Shape> shape(CommandContext<CommandSourceStack> context) {
        try {
            return Optional.of(TestStructures.Shape.byId(StringArgumentType.getString(context, "shape")));
        } catch (IllegalArgumentException e) {
            fail(context, e.getMessage() + "; use one of " + Arrays.stream(TestStructures.Shape.values()).map(TestStructures.Shape::getSerializedName).toList());
            return Optional.empty();
        }
    }

    private static int height(CommandContext<CommandSourceStack> context) {
        try {
            return IntegerArgumentType.getInteger(context, "height");
        } catch (IllegalArgumentException noHeight) {
            return DEFAULT_HEIGHT;
        }
    }

    private static int build(CommandContext<CommandSourceStack> context) {
        Optional<TestStructures.Shape> shape = shape(context);
        if (shape.isEmpty()) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof Player player)) {
            fail(context, "Run this as a player.");
            return 0;
        }
        ServerLevel level = source.getLevel();
        int height = height(context);
        Direction forward = player.getDirection();
        Direction right = forward.getClockWise();
        BlockPos origin = BlockPos.containing(source.getPosition());
        BlockPos easelPos = origin.relative(forward, 3);
        BlockPos chestPos = origin.relative(right, 3);
        BlueprintData blueprint = TestStructures.blueprint(level, shape.get(), height);
        List<BlockPos> blocks = TestStructures.blocks(shape.get(), height);

        // Snapshot first, big enough for the structure (up to 2 + depth blocks ahead of the easel), its scaffold and the chest.
        int reach = 3 + 2 + blocks.stream().mapToInt(pos -> Math.max(pos.getX(), pos.getZ())).max().orElse(0) + 1 + ScaffoldInput.MARGIN + 2;
        int top = blocks.stream().mapToInt(BlockPos::getY).max().orElse(0) + 4;
        BoundingBox box = new BoundingBox(origin.getX() - reach, origin.getY() - 1, origin.getZ() - reach,
                origin.getX() + reach, origin.getY() + top, origin.getZ() + reach);
        StructurePaster.remember(level, ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "scaffold/test_" + shape.get().getSerializedName()),
                origin, box);

        ItemStack scaffold = new ItemStack(BuiltInRegistries.BLOCK.get(Tuning.SCAFFOLD_MATERIAL.get()).asItem(), SCAFFOLD_STOCK);
        DevScenarios.placeChest(level, chestPos, List.of(new ItemStack(Items.COBBLESTONE, blocks.size()), scaffold));
        EaselBlockEntity easel = DevScenarios.placeEasel(level, easelPos, forward.getOpposite(), blueprint);
        for (int i = 0; i < BUILDERS; i++) {
            DevScenarios.spawnWorker(level, origin.relative(right, 1 + i).relative(forward), DevScenarios.RED_TEAM, player,
                    BuildBlueprintGoal.class, chestPos);
        }
        report(context, "Test %s %d high (%d blocks): easel at %s, chest at %s with %d cobblestone and %d %s, %d red builders. "
                .formatted(shape.get().getSerializedName(), height, blocks.size(), easelPos.toShortString(), chestPos.toShortString(),
                        blocks.size(), SCAFFOLD_STOCK, Tuning.SCAFFOLD_MATERIAL.get(), BUILDERS)
                + "Scaffold appears once the lower part is built. Reset: Undo test build, then Cleanup → Remove soldiers + easels.");
        return easel != null ? 1 : 0;
    }

    private static int plan(CommandContext<CommandSourceStack> context) {
        Optional<TestStructures.Shape> shape = shape(context);
        if (shape.isEmpty()) {
            return 0;
        }
        int height = height(context);
        List<BlockPos> blocks = TestStructures.blocks(shape.get(), height);
        report(context, "Scaffold plans for a %s %d high (%d blocks) on flat ground (in game: approach %d):".formatted(
                shape.get().getSerializedName(), height, blocks.size(), Tuning.SCAFFOLD_APPROACH.get()));
        for (ScaffoldPlan plan : ScaffoldPlanner.compare(ScaffoldInput.flat(blocks, -1))) {
            report(context, " - " + plan.describe() + "; " + plan.extraPlacements() + " extra builder trips");
        }
        return 1;
    }

    private static List<ScaffoldJob> nearby(CommandContext<CommandSourceStack> context) {
        BlockPos at = BlockPos.containing(context.getSource().getPosition());
        return ScaffoldSavedData.get(context.getSource().getLevel()).jobs().stream()
                .filter(job -> job.easel().closerThan(at, NEAR)).toList();
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        List<ScaffoldJob> jobs = nearby(context);
        if (jobs.isEmpty()) {
            report(context, "No scaffold jobs within " + NEAR + " blocks.");
            return 0;
        }
        jobs.forEach(job -> report(context, "Easel %s: %d of %d scaffold blocks placed (%s).".formatted(job.easel().toShortString(),
                job.placed().size(), job.planned().size(), job.material())));
        return jobs.size();
    }

    private static int cleanup(CommandContext<CommandSourceStack> context) {
        List<ScaffoldJob> jobs = nearby(context);
        int removed = 0;
        for (ScaffoldJob job : jobs) {
            removed += Scaffolds.cleanup(context.getSource().getLevel(), job.easel(), "dev cleanup");
        }
        report(context, "Removed " + removed + " scaffold blocks of " + jobs.size() + " job(s).");
        return jobs.size();
    }

    private static int undo(CommandContext<CommandSourceStack> context) {
        Optional<StructurePaster.Pasted> undone = StructurePaster.undo(context.getSource().getLevel());
        if (undone.isEmpty()) {
            fail(context, "Nothing to undo.");
            return 0;
        }
        report(context, "Undid %s at %s (%d more to undo). Soldiers stay: use Cleanup → Remove soldiers + easels.".formatted(undone.get().id(),
                undone.get().at().toShortString(), StructurePaster.undoDepth()));
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

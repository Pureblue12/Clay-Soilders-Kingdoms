package net.bumblebee.claysoldiers.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.BlueprintManger;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.init.ModBlocks;
import net.bumblebee.claysoldiers.init.ModItems;
import net.bumblebee.claysoldiers.init.ModRegistries;
import net.bumblebee.claysoldiers.team.ClayMobTeamManger;
import net.bumblebee.claysoldiers.team.TeamLoyaltyManger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The built-in dev tools. To add a dev command, add a {@link DevTool} here (or to another registry):
 * it automatically becomes a {@code /csr dev} command and a button in the dev toolkit book.
 */
public final class DevTools {
    public static final String SETUP = "setup";
    public static final String KIT = "kit";
    public static final String SCENARIOS = "scenarios";
    public static final String TEAM = "team";
    public static final String BUILD = "build";
    public static final String CLEANUP = "cleanup";
    private static final int MAX_CLEANUP_RADIUS = 48;

    private DevTools() {
    }

    public static DevToolRegistry createDefault() {
        DevToolRegistry registry = new DevToolRegistry();
        registry.addCategory(new DevCategory(SETUP, "Setup", "Prepare the test world.", false))
                .addCategory(new DevCategory(KIT, "Kit", "Items for testing.", false))
                .addCategory(new DevCategory(SCENARIOS, "Scenarios", "Ready-made test setups.", false))
                .addCategory(new DevCategory(TEAM, "Team", "Make a team loyal to you.", false))
                .addCategory(new DevCategory(BUILD, "Build", "Blueprint materials.", false))
                .addCategory(new DevCategory(CLEANUP, "Cleanup", "Removes things near you!", true));

        registry.addTool(new DevTool("setup", SETUP, Component.literal("Prepare world"),
                "Turns off mob spawning and the daylight cycle and sets the time to day.",
                List.of("setup"), false, null, DevTools::setup));
        registry.addTool(new DevTool("book", SETUP, Component.literal("Refresh this book"),
                "Updates the toolkit books you carry from the current registry, or gives a new one.",
                List.of("book"), false, null, context -> DevBook.giveOrUpdate(context, registry)));

        registry.addTool(new DevTool("kit", KIT, Component.literal("Get test kit"),
                "Gives a Clay Brush, 3 red soldiers, 3 wheat, a chest, an easel, goggles, the debug item and every blueprint.",
                List.of("kit"), false, null, DevTools::kit));

        registry.addTool(new DevTool("scenario_build_test", SCENARIOS, Component.literal("Build test (stocked)"),
                "3 red builders, a chest with the Small House materials and an easel with the blueprint.",
                List.of("scenario", "build_test"), false, null, context -> buildScenario(context, true)));
        registry.addTool(new DevTool("scenario_build_test_empty", SCENARIOS, Component.literal("Build test (empty chest)"),
                "Same as the build test, but the chest is empty.",
                List.of("scenario", "build_test_empty"), false, null, context -> buildScenario(context, false)));

        registry.addTool(new DevTool("team_claim", TEAM, Component.literal("Claim team"),
                "Makes the team loyal to you, so the Clay Brush works on its soldiers.",
                List.of("team", "claim"), false,
                new DevTool.Argument("team", ResourceLocationArgument.id(),
                        (context, builder) -> SharedSuggestionProvider.suggestResource(
                                ClayMobTeamManger.getAllKeys(context.getSource().registryAccess()), builder),
                        DevTools::teamChoices),
                DevTools::claimTeam));

        registry.addTool(DevTool.link("build_here", BUILD, Component.literal("Build here"),
                "Places an easel with the blueprint where you stand; the building goes the way you face. Refuses blocked sites.",
                "/csr build",
                new DevTool.Argument("blueprint", ResourceLocationArgument.id(), null, DevTools::blueprintChoices)));
        registry.addTool(new DevTool("materials", BUILD, Component.literal("Materials for"),
                "Gives the materials a blueprint needs, read from the blueprint itself.",
                List.of("materials"), false,
                new DevTool.Argument("blueprint", ResourceLocationArgument.id(),
                        (context, builder) -> SharedSuggestionProvider.suggestResource(
                                validBlueprints(context.getSource().registryAccess()).stream().map(h -> h.key().location()), builder),
                        DevTools::blueprintChoices),
                DevTools::materials));

        registry.addTool(new DevTool("cleanup", CLEANUP, Component.literal("Remove soldiers + easels"),
                "Removes all clay soldiers and easels within the radius around you.",
                List.of("cleanup"), true,
                new DevTool.Argument("radius", IntegerArgumentType.integer(1, MAX_CLEANUP_RADIUS), null,
                        access -> List.of(
                                new DevTool.Choice(Component.literal("Radius 16"), "16"),
                                new DevTool.Choice(Component.literal("Radius 32"), "32"))),
                DevTools::cleanup));
        RecorderTools.register(registry);
        TuningTools.register(registry);
        StructureTools.register(registry);
        KingdomTools.register(registry);
        EconomyTools.register(registry);
        TerrainTools.register(registry);
        ScaffoldTools.register(registry);
        return registry;
    }

    // Choices (read from registries, so new teams and blueprints appear automatically)

    private static List<DevTool.Choice> teamChoices(RegistryAccess access) {
        return ClayMobTeamManger.getAllKeys(access)
                .map(key -> new DevTool.Choice(ClayMobTeamManger.getFromKeyOrError(key, access).getDisplayName(), key.toString()))
                .toList();
    }

    private static List<Holder.Reference<BlueprintData>> validBlueprints(RegistryAccess access) {
        return access.registryOrThrow(ModRegistries.BLUEPRINTS).holders().filter(holder -> holder.value().isValid()).toList();
    }

    private static List<DevTool.Choice> blueprintChoices(RegistryAccess access) {
        return validBlueprints(access).stream()
                .map(holder -> new DevTool.Choice(holder.value().getDisplayName(), holder.key().location().toString()))
                .toList();
    }

    // Actions

    private static int setup(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        server.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        for (ServerLevel level : server.getAllLevels()) {
            level.setDayTime(1000);
        }
        context.getSource().sendSuccess(() -> Component.literal("Dev setup: doMobSpawning false, doDaylightCycle false, time set to day."), true);
        return 1;
    }

    private static int kit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        RegistryAccess access = source.registryAccess();

        List<ItemStack> kit = new ArrayList<>();
        kit.add(new ItemStack(ModItems.CLAY_BRUSH.get()));
        kit.add(ClayMobTeamManger.createStackForTeam(DevScenarios.RED_TEAM, access).copyWithCount(3));
        kit.add(new ItemStack(Items.WHEAT, 3));
        kit.add(new ItemStack(Items.CHEST));
        kit.add(new ItemStack(ModBlocks.EASEL_BLOCK.get()));
        kit.add(new ItemStack(ModItems.CLAY_GOGGLES.get()));
        if (ModItems.TEST_ITEM.get() != null) {
            kit.add(new ItemStack(ModItems.TEST_ITEM.get()));
        }
        kit.addAll(BlueprintManger.getBlueprintItems(access));
        give(player, kit);

        source.sendSuccess(() -> Component.literal("Dev kit: " + describe(kit)), false);
        if (!ClaySoldiersCommon.COMMON_HOOKS.isBlueprintEnabled(source.getLevel().enabledFeatures())) {
            source.sendFailure(Component.literal("The blueprint experiment is off in this world: easels and blueprints won't work."));
        }
        return kit.size();
    }

    private static int buildScenario(CommandContext<CommandSourceStack> context, boolean stocked) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();

        DevScenarios.BuildTestResult result;
        try {
            result = DevScenarios.buildTest(source.getLevel(), player, player.blockPosition(), player.getDirection(), stocked);
        } catch (IllegalStateException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }

        StringBuilder message = new StringBuilder("Scenario ").append(stocked ? "build_test" : "build_test_empty").append(" set up:\n");
        message.append("- Chest at ").append(result.chest().toShortString())
                .append(stocked ? " with " + describe(result.chestItems()) : " (empty)").append('\n');
        message.append("- Easel at ").append(result.easel().toShortString())
                .append(" with blueprint ").append(DevScenarios.SMALL_HOUSE).append(" (built on the far side of the easel)\n");
        message.append("- Team ").append(DevScenarios.RED_TEAM).append(" is loyal to ").append(player.getName().getString()).append('\n');
        message.append("- ").append(result.soldiers().size()).append(" soldiers:");
        result.soldiers().forEach(soldier -> message.append("\n    ").append(DevScenarios.describeWorker(soldier)));

        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return result.soldiers().size();
    }

    private static int claimTeam(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ResourceLocation team = ResourceLocationArgument.getId(context, "team");
        if (!ClayMobTeamManger.isValidTeam(team, source.registryAccess())) {
            source.sendFailure(Component.literal("Unknown team " + team));
            return 0;
        }
        TeamLoyaltyManger.setTeamPlayer(source.getLevel(), team, player);
        source.sendSuccess(() -> Component.literal("Team " + team + " is now loyal to " + player.getName().getString() + "."), false);
        return 1;
    }

    private static int materials(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, "blueprint");
        BlueprintData blueprint = source.registryAccess().registryOrThrow(ModRegistries.BLUEPRINTS).get(id);
        if (blueprint == null || !blueprint.isValid()) {
            source.sendFailure(Component.literal("Unknown or invalid blueprint " + id));
            return 0;
        }
        List<ItemStack> items = blueprint.getTemplate().getNeededItems();
        give(player, items);
        source.sendSuccess(() -> Component.literal("Materials for " + id + ": " + describe(items)), false);
        return items.size();
    }

    private static int cleanup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        int radius = IntegerArgumentType.getInteger(context, "radius");
        BlockPos center = BlockPos.containing(source.getPosition());

        int soldiers = 0;
        int errors = 0;
        for (AbstractClaySoldierEntity soldier : level.getEntitiesOfClass(AbstractClaySoldierEntity.class, new AABB(center).inflate(radius))) {
            try {
                soldier.discard();
                soldiers++;
            } catch (RuntimeException e) {
                errors++;
                ClaySoldiersCommon.LOGGER.error("Dev cleanup could not remove {}", soldier, e);
            }
        }

        int easels = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.EASEL_BLOCK.get())) {
                level.removeBlock(pos, false);
                easels++;
            }
        }

        int removedSoldiers = soldiers;
        int removedEasels = easels;
        source.sendSuccess(() -> Component.literal("Dev cleanup (radius %d): removed %d soldiers and %d easels."
                .formatted(radius, removedSoldiers, removedEasels)), true);
        if (errors > 0) {
            source.sendFailure(Component.literal(errors + " soldiers threw an error while being removed; see the log."));
        }
        return removedSoldiers + removedEasels;
    }

    // Helpers

    private static void give(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            player.getInventory().placeItemBackInInventory(stack.copy());
        }
    }

    private static String describe(List<ItemStack> stacks) {
        return stacks.stream().map(stack -> stack.getCount() + " " + stack.getHoverName().getString()).collect(Collectors.joining(", "));
    }
}

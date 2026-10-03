package net.bumblebee.claysoldiers.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.site.BuildSite;
import net.bumblebee.claysoldiers.blueprint.site.BuildSitePlanner;
import net.bumblebee.claysoldiers.blueprint.site.SiteCheck;
import net.bumblebee.claysoldiers.init.ModRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /csr build <blueprint> [<pos>] [north|east|south|west]} (permission level 2):
 * places an easel with the blueprint at {@code pos} (default: the block you stand in), building towards the
 * given direction (default: the way you face), after checking the site with {@link BuildSitePlanner}.
 */
public final class BuildCommand {
    private BuildCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        RequiredArgumentBuilder<CommandSourceStack, ?> pos = Commands.argument("pos", BlockPosArgument.blockPos())
                .executes(context -> build(context, BlockPosArgument.getBlockPos(context, "pos"), null));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            pos.then(Commands.literal(direction.getSerializedName())
                    .executes(context -> build(context, BlockPosArgument.getBlockPos(context, "pos"), direction)));
        }

        dispatcher.register(Commands.literal(ClaySoldiersCommon.MOD_ID)
                .then(Commands.literal("build")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("blueprint", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        context.getSource().registryAccess().registryOrThrow(ModRegistries.BLUEPRINTS).holders()
                                                .filter(holder -> holder.value().isValid())
                                                .map(holder -> holder.key().location()), builder))
                                .executes(context -> build(context, null, null))
                                .then(pos))));
    }

    private static int build(CommandContext<CommandSourceStack> context, @Nullable BlockPos easelPos, @Nullable Direction direction) {
        CommandSourceStack source = context.getSource();
        ResourceLocation blueprintId = ResourceLocationArgument.getId(context, "blueprint");
        Entity entity = source.getEntity();
        BlockPos pos = easelPos != null ? easelPos : BlockPos.containing(source.getPosition());
        Direction buildDirection = direction != null ? direction : (entity != null ? entity.getDirection() : Direction.NORTH);

        SiteCheck check = BuildSitePlanner.checkAndPlace(source.getLevel(), blueprintId, pos, buildDirection);
        if (!check.isValid()) {
            source.sendFailure(Component.literal("Can't build " + blueprintId + " here: " + check.message()));
            return 0;
        }
        BuildSite site = check.site();
        source.sendSuccess(() -> Component.literal("Started %s: easel at %s, building %s over x %d..%d, z %d..%d (%d blocks)."
                .formatted(blueprintId, site.easelPos().toShortString(), site.buildDirection().getSerializedName(),
                        site.bounds().minX(), site.bounds().maxX(), site.bounds().minZ(), site.bounds().maxZ(),
                        site.targets().size())), true);
        return 1;
    }
}

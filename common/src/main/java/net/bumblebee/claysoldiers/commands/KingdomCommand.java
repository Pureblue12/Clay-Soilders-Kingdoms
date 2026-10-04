package net.bumblebee.claysoldiers.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.ChronicleEntry;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.KingdomBook;
import net.bumblebee.claysoldiers.kingdom.KingdomStatus;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * {@code /csr kingdom ...}: found, disband, book, status, list (any player: they only act on teams loyal to you, or
 * just show), and goto_king and found force (operators, permission 2). Registered in every install, not only dev.
 */
public final class KingdomCommand {
    private static final String TEAM = "team";
    private static final SuggestionProvider<CommandSourceStack> KINGDOM_TEAMS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(Kingdoms.all(context.getSource().getServer()).stream().map(Kingdom::team), builder);

    private KingdomCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("csr").then(Commands.literal("kingdom")
                .then(Commands.literal("found").executes(context -> found(context, false))
                        .then(Commands.literal("force").requires(source -> source.hasPermission(2)).executes(context -> found(context, true))))
                .then(Commands.literal("disband").executes(context -> disband(context, null))
                        .then(Commands.argument(TEAM, ResourceLocationArgument.id()).suggests(KINGDOM_TEAMS)
                                .executes(context -> disband(context, ResourceLocationArgument.getId(context, TEAM)))))
                .then(Commands.literal("book").executes(context -> book(context, null))
                        .then(Commands.argument(TEAM, ResourceLocationArgument.id()).suggests(KINGDOM_TEAMS)
                                .executes(context -> book(context, ResourceLocationArgument.getId(context, TEAM)))))
                .then(Commands.literal("status").executes(context -> status(context, null))
                        .then(Commands.argument(TEAM, ResourceLocationArgument.id()).suggests(KINGDOM_TEAMS)
                                .executes(context -> status(context, ResourceLocationArgument.getId(context, TEAM)))))
                .then(Commands.literal("list").executes(KingdomCommand::list))
                .then(Commands.literal("goto_king").requires(source -> source.hasPermission(2))
                        .then(Commands.argument(TEAM, ResourceLocationArgument.id()).suggests(KINGDOM_TEAMS)
                                .executes(context -> gotoKing(context, ResourceLocationArgument.getId(context, TEAM)))))));
    }

    /**
     * With {@code force} (operators only) the terrain check is skipped, for testing; it is recorded.
     */
    private static int found(CommandContext<CommandSourceStack> context, boolean force) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Kingdoms.Result result = Kingdoms.foundNear(player, force);
        if (!result.ok()) {
            context.getSource().sendFailure(Component.literal("No kingdom founded: " + result.refusal()));
            return 0;
        }
        Kingdom kingdom = result.kingdom();
        String name = Kingdoms.teamName(context.getSource().getServer(), kingdom.team());
        context.getSource().sendSuccess(() -> Component.literal("Founded the kingdom of " + name + " around " + kingdom.center().toShortString()
                + " (radius " + kingdom.claimRadius() + ")" + (force ? ", terrain check skipped" : "")
                + ". Read it: /csr kingdom book. Undo: /csr kingdom disband."), true);
        warn(context.getSource(), result);
        return 1;
    }

    /**
     * Tells the founder about soft terrain problems (the kingdom was founded anyway).
     */
    public static void warn(CommandSourceStack source, Kingdoms.Result result) {
        if (!result.warnings().isEmpty()) {
            source.sendSystemMessage(Component.literal("Warning: the ground isn't ideal for a keep: " + String.join(", ", result.warnings())
                    + ". (/csr kingdom found refuses only cliffs, lava, deep water and very steep ground.)").withStyle(ChatFormatting.YELLOW));
        }
    }

    /**
     * Without a team: the kingdom of the team loyal to you. With a team: you must be its loyal player or an operator.
     */
    private static int disband(CommandContext<CommandSourceStack> context, @Nullable ResourceLocation team) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        ServerPlayer player = source.getPlayer();
        Optional<Kingdom> kingdom = team != null ? Kingdoms.byTeam(server, team) : ownKingdom(source);
        if (kingdom.isEmpty()) {
            source.sendFailure(Component.literal(team != null ? Kingdoms.teamName(server, team) + " has no kingdom."
                    : "None of your teams has a kingdom. (Give the team: /csr kingdom disband <team>)"));
            return 0;
        }
        boolean allowed = source.hasPermission(2) || (player != null && Kingdoms.isLoyalTo(source.getLevel(), kingdom.get().team(), player));
        if (!allowed) {
            source.sendFailure(Component.literal("Only the player " + Kingdoms.teamName(server, kingdom.get().team()) + " is loyal to (or an operator) can disband it."));
            return 0;
        }
        Kingdoms.Result result = Kingdoms.disband(server, kingdom.get().team(), player);
        if (!result.ok()) {
            source.sendFailure(Component.literal(result.refusal()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Disbanded the kingdom of " + Kingdoms.teamName(server, kingdom.get().team())
                + ". Nothing in the world was changed."), true);
        return 1;
    }

    private static int book(CommandContext<CommandSourceStack> context, @Nullable ResourceLocation team) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<Kingdom> kingdom = pick(context.getSource(), team);
        if (kingdom.isEmpty()) {
            return 0;
        }
        if (!player.getInventory().add(KingdomBook.create(context.getSource().getServer(), kingdom.get()))) {
            player.drop(KingdomBook.create(context.getSource().getServer(), kingdom.get()), false);
        }
        context.getSource().sendSuccess(() -> Component.literal("Here is the book of the kingdom of "
                + Kingdoms.teamName(context.getSource().getServer(), kingdom.get().team()) + " (a snapshot: get a new one to see changes)."), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context, @Nullable ResourceLocation team) {
        Optional<Kingdom> found = pick(context.getSource(), team);
        if (found.isEmpty()) {
            return 0;
        }
        MinecraftServer server = context.getSource().getServer();
        Kingdom kingdom = found.get();
        Kingdom.Census census = kingdom.census();
        List<String> lines = List.of(
                "Kingdom of " + Kingdoms.teamName(server, kingdom.team()) + ": " + (kingdom.status() == KingdomStatus.ACTIVE ? "active" : "ruined")
                        + ", tier " + kingdom.tier() + ", renown " + kingdom.renown(),
                "Population: " + (census.tick() < 0 ? "not counted yet" : census.population() + " (" + ChronicleEntry.formatTime(census.tick()) + ")"),
                Kingdoms.kingTitle(server, kingdom, true) + " last seen at " + kingdom.king().lastPos().toShortString()
                        + " (" + ChronicleEntry.formatTime(kingdom.king().seenTick()) + ")",
                "Claim: radius " + kingdom.claimRadius() + " around " + kingdom.center().toShortString() + " in " + kingdom.dimension().location(),
                "Stockpile: " + (kingdom.stockpile().isEmpty() ? "empty" : kingdom.stockpile().toString()),
                "Buildings: " + kingdom.buildings().size() + ", chronicle lines: " + kingdom.chronicle().size());
        lines.forEach(line -> context.getSource().sendSuccess(() -> Component.literal(line), false));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        List<Kingdom> kingdoms = Kingdoms.all(server);
        context.getSource().sendSuccess(() -> Component.literal(kingdoms.size() + " kingdom(s)" + (kingdoms.isEmpty() ? "." : ":")), false);
        for (Kingdom kingdom : kingdoms) {
            context.getSource().sendSuccess(() -> Component.literal("  " + kingdom.team() + " (" + Kingdoms.teamName(server, kingdom.team()) + "): "
                    + kingdom.status().getSerializedName() + ", tier " + kingdom.tier() + ", around " + kingdom.center().toShortString()), false);
        }
        return kingdoms.size();
    }

    private static int gotoKing(CommandContext<CommandSourceStack> context, ResourceLocation team) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();
        Optional<Kingdom> kingdom = Kingdoms.byTeam(server, team);
        if (kingdom.isEmpty()) {
            context.getSource().sendFailure(Component.literal(Kingdoms.teamName(server, team) + " has no kingdom."));
            return 0;
        }
        ServerLevel level = server.getLevel(kingdom.get().dimension());
        if (level == null) {
            context.getSource().sendFailure(Component.literal("The kingdom's dimension " + kingdom.get().dimension().location() + " isn't loaded."));
            return 0;
        }
        Entity king = level.getEntity(kingdom.get().king().id());
        boolean seen = king instanceof AbstractClaySoldierEntity soldier && soldier.isAlive();
        BlockPos target = seen ? king.blockPosition() : kingdom.get().king().lastPos();
        player.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, player.getYRot(), player.getXRot());
        context.getSource().sendSuccess(() -> Component.literal((seen ? "Teleported to " : "The king isn't loaded or alive; teleported to where it was last seen: ")
                + Kingdoms.kingTitle(server, kingdom.get(), false) + " at " + target.toShortString() + "."), false);
        return 1;
    }

    /**
     * The kingdom named by {@code team}; without a team the one you stand in, else the one of a team loyal to you.
     * Sends a failure message when there is none.
     */
    public static Optional<Kingdom> pick(CommandSourceStack source, @Nullable ResourceLocation team) {
        MinecraftServer server = source.getServer();
        Optional<Kingdom> kingdom = team != null ? Kingdoms.byTeam(server, team)
                : Kingdoms.at(server, source.getLevel().dimension(), BlockPos.containing(source.getPosition())).or(() -> ownKingdom(source));
        if (kingdom.isEmpty()) {
            source.sendFailure(Component.literal(team != null ? Kingdoms.teamName(server, team) + " has no kingdom."
                    : "You're not in a kingdom and none of your teams has one. (/csr kingdom list shows them all.)"));
        }
        return kingdom;
    }

    private static Optional<Kingdom> ownKingdom(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return Optional.empty();
        }
        return Kingdoms.all(source.getServer()).stream().filter(kingdom -> Kingdoms.isLoyalTo(source.getLevel(), kingdom.team(), player)).findFirst();
    }
}

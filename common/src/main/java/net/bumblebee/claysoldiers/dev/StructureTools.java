package net.bumblebee.claysoldiers.dev;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.structure.StructureMeta;
import net.bumblebee.claysoldiers.structure.StructurePaster;
import net.bumblebee.claysoldiers.structure.StructureRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;
import java.util.Optional;

/**
 * The "Structures" dev tools: paste a registry structure where you stand, undo, reload from disk, list. The paste
 * buttons are generated from the structure registry, so new structures get buttons automatically.
 * See "Touch-up workflow" in {@code docs/STRUCTURES.md}.
 */
final class StructureTools {
    static final String STRUCTURES = "structures";
    private static final List<String> FACINGS = List.of("north", "east", "south", "west");

    private StructureTools() {
    }

    static void register(DevToolRegistry registry) {
        registry.addCategory(new DevCategory(STRUCTURES, "Structures",
                "Paste where you stand, going the way you face (docs/STRUCTURES.md).", false));
        registry.addTool(new DevTool("structure_place", STRUCTURES, Component.literal("Paste"),
                "Pastes the whole structure instantly, where an easel at your feet would build it. Undo restores the blocks.",
                List.of("structure", "place"), false,
                new DevTool.Argument("structure", ResourceLocationArgument.id(),
                        (context, builder) -> SharedSuggestionProvider.suggestResource(
                                StructureRegistry.loaded().stream().filter(StructureMeta::isReady).map(StructureMeta::id), builder),
                        access -> StructureRegistry.loaded().stream().filter(StructureMeta::isReady)
                                .map(meta -> new DevTool.Choice(Component.literal(meta.name()), meta.id().toString(), describe(meta)))
                                .toList())
                        .withOptionalExtra("facing", StringArgumentType.word(),
                                (context, builder) -> SharedSuggestionProvider.suggest(FACINGS, builder)),
                StructureTools::place));
        registry.addTool(new DevTool("structure_undo", STRUCTURES, Component.literal("Undo last paste"),
                "Puts back the blocks the last paste replaced.",
                List.of("structure", "undo"), false, null, StructureTools::undo));
        registry.addTool(new DevTool("structure_reload", STRUCTURES, Component.literal("Reload structures"),
                "Re-reads structure files and metadata from disk (after saving with a structure block) and checks them.",
                List.of("structure", "reload"), false, null, StructureTools::reload));
        registry.addTool(new DevTool("structure_list", STRUCTURES, Component.literal("List all"),
                "Lists every structure, including placeholders (design TBD), and any problems.",
                List.of("structure", "list"), false, null, StructureTools::list));
    }

    private static int place(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ResourceLocation id = ResourceLocationArgument.getId(context, "structure");
        Optional<StructureMeta> meta = StructureRegistry.get(source.getServer()).byId(id);
        if (meta.isEmpty()) {
            fail(context, "No structure " + id + ". /csr dev structure list shows them all.");
            return 0;
        }
        Direction direction;
        String facing = optionalString(context, "facing");
        if (facing == null) {
            direction = source.getEntity() != null ? source.getEntity().getDirection() : Direction.SOUTH;
        } else {
            direction = Direction.byName(facing);
            if (direction == null || direction.getAxis().isVertical()) {
                fail(context, "Facing must be one of " + FACINGS + ", got " + facing);
                return 0;
            }
        }
        BlockPos at = BlockPos.containing(source.getPosition());
        StructurePaster.Pasted pasted;
        try {
            pasted = StructurePaster.paste(source.getLevel(), meta.get(), at, direction);
        } catch (IllegalArgumentException e) {
            fail(context, "Can't paste: " + e.getMessage());
            return 0;
        }
        report(context, "Pasted %s [%s] going %s from %s: blocks %s. Undo: /csr dev structure undo (%d to undo)."
                .formatted(meta.get().name(), id, direction.getName(), at.toShortString(), StructurePaster.describe(pasted.box()),
                        StructurePaster.undoDepth()));
        if (direction == Direction.SOUTH) {
            BoundingBox box = pasted.box();
            report(context, ("This paste has the file's own orientation. To save edits: structure block at (%d, %d, %d) "
                    + "(one below the first corner), mode Save, name %s, relative position 0 1 0, size %d %d %d, click SAVE, "
                    + "then Reload structures.").formatted(box.minX(), box.minY() - 1, box.minZ(), meta.get().structure(),
                    box.getXSpan(), box.getYSpan(), box.getZSpan()));
        }
        return 1;
    }

    private static int undo(CommandContext<CommandSourceStack> context) {
        Optional<StructurePaster.Pasted> undone = StructurePaster.undo(context.getSource().getLevel());
        if (undone.isEmpty()) {
            fail(context, "Nothing to undo.");
            return 0;
        }
        report(context, "Undid the paste of %s at %s (%d more to undo).".formatted(undone.get().id(),
                undone.get().at().toShortString(), StructurePaster.undoDepth()));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        StructureRegistry.Snapshot snapshot = StructureRegistry.reload(context.getSource().getServer());
        report(context, "Structures reloaded: %d (%d ready), %d problem(s).".formatted(snapshot.structures().size(),
                snapshot.ready().size(), snapshot.problems().size()));
        snapshot.problems().forEach(problem -> fail(context, "  " + problem));
        return snapshot.problems().isEmpty() ? 1 : 0;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        StructureRegistry.Snapshot snapshot = StructureRegistry.get(context.getSource().getServer());
        report(context, "%d structures (%d ready), %d problem(s):".formatted(snapshot.structures().size(),
                snapshot.ready().size(), snapshot.problems().size()));
        snapshot.structures().forEach(meta -> report(context, "  " + meta.id() + " - " + meta.name() + ": " + describe(meta)));
        snapshot.problems().forEach(problem -> fail(context, "  " + problem));
        return snapshot.structures().size();
    }

    private static String describe(StructureMeta meta) {
        String size = meta.footprint() == null ? "" : ", %dx%dx%d".formatted(meta.footprint().getX(), meta.footprint().getY(), meta.footprint().getZ());
        return "tier %d, %s zone, %s%s%s".formatted(meta.tier(), meta.zone(), meta.role(), size,
                meta.isReady() ? "" : ", design " + StructureMeta.TBD);
    }

    private static String optionalString(CommandContext<CommandSourceStack> context, String name) {
        try {
            return StringArgumentType.getString(context, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
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

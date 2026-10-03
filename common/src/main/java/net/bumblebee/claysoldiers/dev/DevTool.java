package net.bumblebee.claysoldiers.dev;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * One dev command and its book button(s), defined once in the {@link DevToolRegistry}.
 *
 * @param id          unique id
 * @param category    id of a {@link DevCategory} in the same registry
 * @param label       button text in the book
 * @param description one line, shown when hovering the button and in confirm prompts
 * @param path        words after {@code /csr dev}, e.g. {@code ["scenario", "build_test"]}
 * @param confirm     whether running it first asks for a second click
 * @param argument    optional single argument; the book gets one button per {@link Argument#choices choice}
 * @param action      what the command does
 */
public record DevTool(String id, String category, Component label, String description, List<String> path,
                      boolean confirm, @Nullable Argument argument, Action action) {
    public static final String ROOT_COMMAND = "/csr dev";

    public DevTool {
        path = List.copyOf(path);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Dev tool " + id + " needs a command path");
        }
    }

    /**
     * The command this tool runs, without its argument, e.g. {@code /csr dev materials}.
     */
    public String command() {
        return ROOT_COMMAND + " " + String.join(" ", path);
    }

    /**
     * The command usage, e.g. {@code /csr dev materials <blueprint>}.
     */
    public String usage() {
        return argument == null ? command() : command() + " <" + argument.name() + ">";
    }

    /**
     * What the command does when run.
     */
    @FunctionalInterface
    public interface Action {
        int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    /**
     * A command argument and the values the book offers as buttons.
     *
     * @param name        argument name, used with e.g. {@code ResourceLocationArgument.getId(context, name)}
     * @param type        brigadier argument type
     * @param suggestions tab-completion suggestions, or {@code null}
     * @param choices     values to offer in the book; read from registries where possible so new content appears automatically
     */
    public record Argument(String name, ArgumentType<?> type, @Nullable SuggestionProvider<CommandSourceStack> suggestions,
                           Function<RegistryAccess, List<Choice>> choices) {
    }

    /**
     * One book button for an argument value.
     *
     * @param label button text
     * @param value text appended to the command
     */
    public record Choice(Component label, String value) {
    }
}

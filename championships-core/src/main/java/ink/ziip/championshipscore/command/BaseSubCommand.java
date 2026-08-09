package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class BaseSubCommand extends BaseMainCommand {

    @Getter
    protected final String usage;

    public BaseSubCommand(String commandName) {
        this(commandName, "", "");
    }

    public BaseSubCommand(String commandName, String description, String usage) {
        this(commandName, description, usage, "");
    }

    public BaseSubCommand(String commandName, String description, String usage, String permission) {
        super(commandName, description, permission);
        this.usage = usage;
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    /**
     * Sends the usage hint for this command, e.g. when arguments are missing or invalid.
     */
    protected void sendUsage(@NotNull CommandSender sender) {
        if (usage == null || usage.isEmpty())
            return;
        sender.sendMessage(Utils.translateColorCodes(MessageConfig.COMMAND_USAGE
                .replace("%usage%", usage)
                .replace("%description%", description == null ? "" : description)));
    }

    /** Prefix-filter a dynamic candidate list using the same case-insensitive rules as the command tree. */
    protected List<String> complete(@NotNull List<String> candidates, @NotNull String prefix) {
        return filterStartsWith(candidates, prefix);
    }

    public abstract boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args);

    @Nullable
    public abstract List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args);
}

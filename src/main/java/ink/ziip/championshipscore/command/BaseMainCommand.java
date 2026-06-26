package ink.ziip.championshipscore.command;

import lombok.Getter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BaseMainCommand extends MainCommand {

    @Getter
    protected final String commandName;
    @Getter
    protected final String description;

    public BaseMainCommand(String command) {
        this(command, "");
    }

    public BaseMainCommand(String command, String description) {
        super();
        this.commandName = command;
        this.description = description;
    }

    /**
     * @return true if this node has no sub-commands and executes directly
     */
    public boolean isLeaf() {
        return false;
    }

    /**
     * @return the usage hint shown in help; empty for intermediate nodes
     */
    public String getUsage() {
        return "";
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sendHelp(sender, false);
            return true;
        }

        BaseMainCommand subCommand = subCommandMap.get(args[0]);
        if (subCommand != null) {
            return subCommand.onCommand(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
        }

        sendHelp(sender, false);
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filterStartsWith(new ArrayList<>(subCommandMap.keySet()), args[0]);
        }

        BaseMainCommand subCommand = subCommandMap.get(args[0]);
        if (subCommand != null) {
            return subCommand.onTabComplete(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
        }

        return Collections.emptyList();
    }
}

package ink.ziip.championshipscore.command;

import lombok.Getter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BaseMainCommand extends MainCommand {

    protected final Map<String, BaseMainCommand> subCommandMap;
    @Getter
    protected final String commandName;

    public BaseMainCommand(String command) {
        this.subCommandMap = new ConcurrentHashMap<>();
        this.commandName = command;
    }

    public void addSubCommand(BaseMainCommand subCommand) {
        subCommandMap.put(subCommand.getCommandName(), subCommand);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            return true;
        }

        BaseMainCommand subCommand = subCommandMap.get(args[0]);
        if (subCommand != null) {
            return subCommand.onCommand(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
        }

        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = new java.util.ArrayList<>(subCommandMap.keySet().stream().toList());
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }

        BaseMainCommand subCommand = subCommandMap.get(args[0]);
        if (subCommand != null) {
            return subCommand.onTabComplete(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
        }

        return Collections.emptyList();
    }
}
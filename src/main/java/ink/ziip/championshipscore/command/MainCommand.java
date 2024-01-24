package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MainCommand implements TabExecutor, TabCompleter {

    protected final ChampionshipsCore plugin = ChampionshipsCore.getInstance();
    protected final Map<String, BaseMainCommand> subCommandMap;

    public MainCommand() {
        this.subCommandMap = new ConcurrentHashMap<>();
    }

    public void addSubCommand(BaseMainCommand subCommand) {
        subCommandMap.put(subCommand.getCommandName(), subCommand);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            return true;
        }
        if (!sender.hasPermission("cc." + args[0])) {
            sender.sendMessage(MessageConfig.NO_PERMISSION);
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
            returnList.removeIf(s -> !sender.hasPermission("cc." + s));
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
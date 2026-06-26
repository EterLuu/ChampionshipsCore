package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
            sendHelp(sender, true);
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

        sendHelp(sender, true);
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filterStartsWith(permittedSubCommands(sender, true), args[0]);
        }

        BaseMainCommand subCommand = subCommandMap.get(args[0]);
        if (subCommand != null && sender.hasPermission("cc." + args[0])) {
            return subCommand.onTabComplete(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
        }

        return Collections.emptyList();
    }

    /**
     * Sends an auto-generated help listing of the available sub-commands.
     *
     * @param sender           the receiver
     * @param permissionFilter true to hide entries the sender lacks {@code cc.<name>} permission for
     */
    protected void sendHelp(@NotNull CommandSender sender, boolean permissionFilter) {
        StringBuilder stringBuilder = new StringBuilder(MessageConfig.COMMAND_HELP_HEADER);

        List<String> names = new ArrayList<>(subCommandMap.keySet());
        Collections.sort(names);

        for (String name : names) {
            if (permissionFilter && !sender.hasPermission("cc." + name))
                continue;
            stringBuilder.append("\n").append(helpRow(subCommandMap.get(name)));
        }

        sender.sendMessage(stringBuilder.toString());
    }

    protected String helpRow(@NotNull BaseMainCommand subCommand) {
        String detail = subCommand.isLeaf() ? subCommand.getUsage() : MessageConfig.COMMAND_HELP_MORE;
        return MessageConfig.COMMAND_HELP_ROW
                .replace("%command%", subCommand.getCommandName())
                .replace("%description%", subCommand.getDescription() == null ? "" : subCommand.getDescription())
                .replace("%detail%", detail == null ? "" : detail);
    }

    protected List<String> permittedSubCommands(@NotNull CommandSender sender, boolean permissionFilter) {
        List<String> out = new ArrayList<>();
        for (String name : subCommandMap.keySet()) {
            if (permissionFilter && !sender.hasPermission("cc." + name))
                continue;
            out.add(name);
        }
        return out;
    }

    protected List<String> filterStartsWith(@NotNull List<String> list, @NotNull String prefix) {
        String lowered = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : list) {
            if (s != null && s.toLowerCase().startsWith(lowered))
                out.add(s);
        }
        Collections.sort(out);
        return out;
    }
}

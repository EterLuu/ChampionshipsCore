package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.ChampionshipPermissions;
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
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MainCommand implements TabExecutor, TabCompleter {

    public static final String PLAYER_PERMISSION = ChampionshipPermissions.PLAYER;
    public static final String ADMIN_PERMISSION = ChampionshipPermissions.ADMIN;

    protected final ChampionshipsCore plugin = ChampionshipsCore.getInstance();
    protected final Map<String, BaseMainCommand> subCommandMap;

    public MainCommand() {
        this.subCommandMap = new ConcurrentHashMap<>();
    }

    public void addSubCommand(BaseMainCommand subCommand) {
        subCommandMap.put(subCommand.getCommandName(), subCommand);
    }

    /** Resolve command names the same way tab completion presents them: case-insensitively. */
    protected BaseMainCommand findSubCommand(@NotNull String name) {
        BaseMainCommand exact = subCommandMap.get(name);
        if (exact != null)
            return exact;
        for (Map.Entry<String, BaseMainCommand> entry : subCommandMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name))
                return entry.getValue();
        }
        return null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1 || args[0].equalsIgnoreCase("help")) {
            CommandCatalog.send(sender);
            return true;
        }
        BaseMainCommand subCommand = findSubCommand(args[0]);
        if (subCommand == null) {
            CommandCatalog.send(sender);
            return true;
        }
        if (!canUse(sender, subCommand)) {
            sender.sendMessage(MessageConfig.NO_PERMISSION);
            return true;
        }

        return subCommand.onCommand(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = permittedSubCommands(sender, true);
            names.add("help");
            return filterStartsWith(names, args[0]);
        }

        BaseMainCommand subCommand = findSubCommand(args[0]);
        if (subCommand != null && canUse(sender, subCommand)) {
            return subCommand.onTabComplete(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
        }

        return Collections.emptyList();
    }

    /**
     * Sends an auto-generated help listing of the available sub-commands.
     *
     * @param sender           the receiver
     * @param permissionFilter true to hide entries the sender cannot execute
     */
    protected void sendHelp(@NotNull CommandSender sender, boolean permissionFilter) {
        StringBuilder stringBuilder = new StringBuilder(MessageConfig.COMMAND_HELP_HEADER);

        List<String> names = new ArrayList<>(subCommandMap.keySet());
        Collections.sort(names);

        for (String name : names) {
            BaseMainCommand subCommand = subCommandMap.get(name);
            if (permissionFilter && !canUse(sender, subCommand))
                continue;
            stringBuilder.append("\n").append(helpRow(subCommand));
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
        for (BaseMainCommand subCommand : subCommandMap.values()) {
            if (permissionFilter && !canUse(sender, subCommand))
                continue;
            out.add(subCommand.getCommandName());
        }
        return out;
    }

    /** Administrators inherit player-facing commands; all other routes use their declared root permission. */
    protected boolean canUse(@NotNull CommandSender sender, @NotNull BaseMainCommand subCommand) {
        String permission = subCommand.getPermission();
        if (permission == null || permission.isBlank())
            return true;
        if (PLAYER_PERMISSION.equals(permission))
            return sender.hasPermission(PLAYER_PERMISSION) || sender.hasPermission(ADMIN_PERMISSION);
        return sender.hasPermission(permission);
    }

    protected List<String> filterStartsWith(@NotNull List<String> list, @NotNull String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : list) {
            if (s != null && s.toLowerCase(Locale.ROOT).startsWith(lowered) && !out.contains(s))
                out.add(s);
        }
        Collections.sort(out);
        return out;
    }
}

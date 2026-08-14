package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import lombok.Getter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BaseMainCommand extends MainCommand {

    private final Map<String, GameTypeEnum> gameSubCommands = new ConcurrentHashMap<>();

    @Getter
    protected final String commandName;
    @Getter
    protected final String description;
    @Getter
    protected final String permission;

    public BaseMainCommand(String command) {
        this(command, "", "");
    }

    public BaseMainCommand(String command, String description) {
        this(command, description, "");
    }

    public BaseMainCommand(String command, String description, String permission) {
        super();
        this.commandName = command;
        this.description = description;
        this.permission = permission;
    }

    /**
     * Registers a per-game sub-command only when that game is enabled via {@code enabled-games};
     * disabled games disappear from command execution, help listing and tab completion entirely.
     */
    protected void addGameSubCommand(GameTypeEnum gameTypeEnum, BaseMainCommand subCommand) {
        if (plugin.getGameManager().isGameEnabled(gameTypeEnum)) {
            addSubCommand(subCommand);
            gameSubCommands.put(subCommand.getCommandName(), gameTypeEnum);
        }
    }

    private boolean isTabVisible(@NotNull BaseMainCommand subCommand) {
        GameTypeEnum game = gameSubCommands.get(subCommand.getCommandName());
        return game == null || plugin.getGameManager().isGameEnabled(game);
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

        BaseMainCommand subCommand = findSubCommand(args[0]);
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
            List<String> visible = new ArrayList<>();
            for (BaseMainCommand subCommand : subCommandMap.values()) {
                if (isTabVisible(subCommand)) visible.add(subCommand.getCommandName());
            }
            return filterStartsWith(visible, args[0]);
        }

        BaseMainCommand subCommand = findSubCommand(args[0]);
        if (subCommand != null && isTabVisible(subCommand)) {
            return subCommand.onTabComplete(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
        }

        return Collections.emptyList();
    }
}

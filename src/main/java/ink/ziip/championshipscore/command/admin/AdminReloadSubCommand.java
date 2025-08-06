package ink.ziip.championshipscore.command.admin;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.command.BaseSubCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class AdminReloadSubCommand extends BaseSubCommand {
    public AdminReloadSubCommand() {
        super("reload");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            ChampionshipsCore.getInstance().onDisable();
            ChampionshipsCore.getInstance().onEnable();
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

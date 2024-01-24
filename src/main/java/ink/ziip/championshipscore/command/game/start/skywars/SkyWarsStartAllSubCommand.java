package ink.ziip.championshipscore.command.game.start.skywars;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class SkyWarsStartAllSubCommand extends BaseSubCommand {
    public SkyWarsStartAllSubCommand() {
        super("all");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        plugin.getGameManager().joinSingleTeamAreaForAllTeams(GameTypeEnum.SkyWars, "area1");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

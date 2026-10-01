package ink.ziip.championshipscore.command.game.start;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public final class LaserBoxStartSubCommand extends BaseSubCommand {
    public LaserBoxStartSubCommand() { super("laserbox","开始激光方盒（两队对战）","/cc game start laserbox <场地> <队伍1> <队伍2>"); }
    @Override public boolean onCommand(@NotNull CommandSender sender,@NotNull Command command,@NotNull String label,@NotNull String[] args) {
        if (args.length != 3) { sendUsage(sender); return true; }
        ChampionshipTeam right=plugin.getTeamManager().getTeam(args[1]);
        ChampionshipTeam left=plugin.getTeamManager().getTeam(args[2]);
        boolean started=right!=null && left!=null && plugin.getGameManager().joinTeamArea(GameTypeEnum.LaserBox,args[0],right,left,true,ink.ziip.championshipscore.api.object.game.GameRunMode.GAME);
        sender.sendMessage(started ? MessageConfig.GAME_TEAM_GAME_START_SUCCESSFUL.replace("%team%",right.getColoredName()).replace("%rival%",left.getColoredName()).replace("%game%",GameTypeEnum.LaserBox.toString()).replace("%area%",args[0]) : MessageConfig.GAME_TEAM_GAME_START_FAILED.replace("%team%",args[1]).replace("%rival%",args[2]).replace("%game%",GameTypeEnum.LaserBox.toString()).replace("%area%",args[0]));
        return true;
    }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,@NotNull Command command,@NotNull String label,@NotNull String[] args) {
        if(args.length==1)return filterStartsWith(plugin.getGameManager().getLaserBoxManager().getAreaNameList(),args[0]);
        if(args.length==2)return filterStartsWith(plugin.getTeamManager().getTeamNameList(),args[1]);
        if(args.length==3){var values=plugin.getTeamManager().getTeamNameList();values.removeIf(s->s.equalsIgnoreCase(args[1]));return filterStartsWith(values,args[2]);}
        return List.of();
    }
}

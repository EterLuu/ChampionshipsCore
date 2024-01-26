package ink.ziip.championshipscore.command.game.start;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.game.start.skywars.SkyWarsStartMainCommand;
import ink.ziip.championshipscore.command.game.start.tgttos.TGTTOSStartMainCommand;
import ink.ziip.championshipscore.command.game.start.tntrun.TNTRunStartMainCommand;
import org.bukkit.Bukkit;

public class GameStartMainCommand extends BaseMainCommand {

    public GameStartMainCommand() {
        super("start");
        addSubCommand(new BattleBoxStartSubCommand());
        if (Bukkit.getPluginManager().getPlugin("BingoReloaded") != null) {
            addSubCommand(new BingoStartSubCommand());
        }
        addSubCommand(new ParkourTagStartSubCommand());
        addSubCommand(new SkyWarsStartMainCommand());
        addSubCommand(new TGTTOSStartMainCommand());
        addSubCommand(new TNTRunStartMainCommand());
        addSubCommand(new DragonEggCarnivalStartSubCommand());
    }
}

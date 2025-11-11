package ink.ziip.championshipscore.command.game.start;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.game.start.parkourwarrior.ParkourWarriorStartMainCommand;
import ink.ziip.championshipscore.command.game.start.skywars.SkyWarsStartMainCommand;
import ink.ziip.championshipscore.command.game.start.snowball.SnowBallStartMainCommand;
import ink.ziip.championshipscore.command.game.start.tgttos.TGTTOSStartMainCommand;
import ink.ziip.championshipscore.command.game.start.tntrun.TNTRunStartMainCommand;
import org.bukkit.Bukkit;

public class GameStartMainCommand extends BaseMainCommand {

    public GameStartMainCommand() {
        super("start");
        addSubCommand(new BattleBoxStartSubCommand());
        addSubCommand(new ParkourTagStartSubCommand());
        addSubCommand(new SkyWarsStartMainCommand());
        addSubCommand(new TGTTOSStartMainCommand());
        addSubCommand(new TNTRunStartMainCommand());
        addSubCommand(new DragonEggCarnivalStartSubCommand());
        addSubCommand(new SnowBallStartMainCommand());
        addSubCommand(new ParkourWarriorStartMainCommand());
        addSubCommand(new HotyCodyDuskyStartSubCommand());
    }
}

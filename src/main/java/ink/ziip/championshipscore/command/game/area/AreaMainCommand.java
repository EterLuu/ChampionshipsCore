package ink.ziip.championshipscore.command.game.area;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.game.area.battlebox.BattleBoxAreaMainCommand;
import ink.ziip.championshipscore.command.game.area.decarnival.DragonEggCarnivalAreaMainCommand;
import ink.ziip.championshipscore.command.game.area.hotycodydusky.HotyCodyDuskyAreaMainCommand;
import ink.ziip.championshipscore.command.game.area.parkourtag.ParkourTagAreaMainCommand;
import ink.ziip.championshipscore.command.game.area.parkourwarrior.ParkourWarriorAreaMainCommand;
import ink.ziip.championshipscore.command.game.area.skywars.SkyWarsAreaMainCommand;
import ink.ziip.championshipscore.command.game.area.snowball.SnowBallAreaMainCommand;
import ink.ziip.championshipscore.command.game.area.tgttos.TGTTOSAreaMainCommand;
import ink.ziip.championshipscore.command.game.area.tntrun.TNTRunAreaMainCommand;

public class AreaMainCommand extends BaseMainCommand {
    public AreaMainCommand() {
        super("area");
        addSubCommand(new BattleBoxAreaMainCommand());
        addSubCommand(new ParkourTagAreaMainCommand());
        addSubCommand(new SkyWarsAreaMainCommand());
        addSubCommand(new TGTTOSAreaMainCommand());
        addSubCommand(new TNTRunAreaMainCommand());
        addSubCommand(new DragonEggCarnivalAreaMainCommand());
        addSubCommand(new SnowBallAreaMainCommand());
        addSubCommand(new ParkourWarriorAreaMainCommand());
        addSubCommand(new HotyCodyDuskyAreaMainCommand());
    }
}

package ink.ziip.championshipscore.command.game.area;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.game.area.battlebox.BattleBoxAreaMainCommand;
import ink.ziip.championshipscore.command.game.area.parkourtag.ParkourTagAreaMainCommand;
import ink.ziip.championshipscore.command.game.area.skywars.SkyWarsAreaMainCommand;
import ink.ziip.championshipscore.command.game.area.tgttos.TGTTOSAreaMainCommand;

public class AreaMainCommand extends BaseMainCommand {
    public AreaMainCommand() {
        super("area");
        addSubCommand(new BattleBoxAreaMainCommand());
        addSubCommand(new ParkourTagAreaMainCommand());
        addSubCommand(new SkyWarsAreaMainCommand());
        addSubCommand(new TGTTOSAreaMainCommand());
    }
}

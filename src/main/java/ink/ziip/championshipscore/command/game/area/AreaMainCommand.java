package ink.ziip.championshipscore.command.game.area;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.game.area.battlebox.BattleBoxAreaMainCommand;

public class AreaMainCommand extends BaseMainCommand {
    public AreaMainCommand() {
        super("area");
        addSubCommand(new BattleBoxAreaMainCommand());
    }
}
package ink.ziip.championshipscore.command.game.area.battlebox;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class BattleBoxAreaMainCommand extends BaseMainCommand {
    public BattleBoxAreaMainCommand() {
        super("battlebox", "战斗箱场地");
        addSubCommand(new BattleBoxAreaAddSubCommand());
        addSubCommand(new BattleBoxAreaSetSubCommand());
    }
}

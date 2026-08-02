package ink.ziip.championshipscore.command.admin.dodgebolt;

import ink.ziip.championshipscore.command.BaseMainCommand;

public final class AdminDodgeboltMainCommand extends BaseMainCommand {
    public AdminDodgeboltMainCommand() {
        super("dodgebolt", "躲避箭决赛现场控制");
        addSubCommand(new DodgeboltControlSubCommand("pause", "暂停决赛"));
        addSubCommand(new DodgeboltControlSubCommand("resume", "恢复决赛"));
        addSubCommand(new DodgeboltControlSubCommand("restart-round", "重开当前小局"));
        addSubCommand(new DodgeboltEliminateSubCommand());
        addSubCommand(new DodgeboltForceWinSubCommand());
        addSubCommand(new DodgeboltControlSubCommand("stop", "终止决赛，不产生冠军"));
    }
}

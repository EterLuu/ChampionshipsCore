package ink.ziip.championshipscore.command.admin.schedule;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseMainCommand;

public class ScheduleMainCommand extends BaseMainCommand {
    public ScheduleMainCommand() {
        super("schedule", "赛程安排");
        addGameSubCommand(GameTypeEnum.DragonEggCarnival, new ScheduleDragonEggCarnivalSubCommand());
        addGameSubCommand(GameTypeEnum.SnowballShowdown, new ScheduleSnowballSubCommand());
        addGameSubCommand(GameTypeEnum.SkyWars, new ScheduleSkyWarsSubCommand());
        addGameSubCommand(GameTypeEnum.TNTRun, new ScheduleTNTRunSubCommand());
        addGameSubCommand(GameTypeEnum.TGTTOS, new ScheduleTGTTOSSubCommand());
        addGameSubCommand(GameTypeEnum.BattleBox, new ScheduleBattleBoxSubCommand());
        addGameSubCommand(GameTypeEnum.ParkourTag, new ScheduleParkourTagSubCommand());
        addSubCommand(new ScheduleResetSubCommand());
        addGameSubCommand(GameTypeEnum.ParkourWarrior, new ScheduleParkourWarriorSubCommand());
        addGameSubCommand(GameTypeEnum.HotyCodyDusky, new ScheduleHotyCodyDuskySubCommand());
        addGameSubCommand(GameTypeEnum.Bingo, new ScheduleBingoSubCommand());
        addSubCommand(new ScheduleDeleteSubCommand());
    }
}

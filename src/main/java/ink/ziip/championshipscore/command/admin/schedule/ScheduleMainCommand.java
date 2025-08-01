package ink.ziip.championshipscore.command.admin.schedule;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class ScheduleMainCommand extends BaseMainCommand {
    public ScheduleMainCommand() {
        super("schedule");
        addSubCommand(new ScheduleDragonEggCarnivalSubCommand());
        addSubCommand(new ScheduleSnowballSubCommand());
        addSubCommand(new ScheduleSkyWarsSubCommand());
        addSubCommand(new ScheduleTNTRunSubCommand());
        addSubCommand(new ScheduleTGTTOSSubCommand());
        addSubCommand(new ScheduleBattleBoxSubCommand());
        addSubCommand(new ScheduleParkourTagSubCommand());
        addSubCommand(new ScheduleResetSubCommand());
        addSubCommand(new ScheduleParkourWarriorSubCommand());
        addSubCommand(new ScheduleHotyCodyDuskySubCommand());
    }
}

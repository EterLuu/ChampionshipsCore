package ink.ziip.championshipscore.command.game.start.frostbite;

import ink.ziip.championshipscore.command.BaseMainCommand;

public final class FrostbiteStartMainCommand extends BaseMainCommand {
    public FrostbiteStartMainCommand() {
        super("frostbite", "霜冻决斗");
        addSubCommand(new FrostbiteStartAllSubCommand());
    }
}

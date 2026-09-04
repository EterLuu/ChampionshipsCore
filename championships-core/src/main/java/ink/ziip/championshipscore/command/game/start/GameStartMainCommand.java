package ink.ziip.championshipscore.command.game.start;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.game.start.bingo.BingoStartMainCommand;
import ink.ziip.championshipscore.command.game.start.buildmart.BuildMartStartMainCommand;
import ink.ziip.championshipscore.command.game.start.parkourwarrior.ParkourWarriorStartMainCommand;
import ink.ziip.championshipscore.command.game.start.skywars.SkyWarsStartMainCommand;
import ink.ziip.championshipscore.command.game.start.snowball.SnowBallStartMainCommand;
import ink.ziip.championshipscore.command.game.start.tgttos.TGTTOSStartMainCommand;
import ink.ziip.championshipscore.command.game.start.tntrun.TNTRunStartMainCommand;
import ink.ziip.championshipscore.command.game.start.acerace.AceRaceStartMainCommand;

public class GameStartMainCommand extends BaseMainCommand {

    public GameStartMainCommand() {
        super("start", "仅为指定队伍启动单局（不播报规则或自动调度观战者）");
        addGameSubCommand(GameTypeEnum.Bingo, new BingoStartMainCommand());
        addGameSubCommand(GameTypeEnum.BuildMart, new BuildMartStartMainCommand());
        addGameSubCommand(GameTypeEnum.BattleBox, new BattleBoxStartSubCommand());
        addGameSubCommand(GameTypeEnum.ParkourTag, new ParkourTagStartSubCommand());
        addGameSubCommand(GameTypeEnum.SkyWars, new SkyWarsStartMainCommand());
        addGameSubCommand(GameTypeEnum.TGTTOS, new TGTTOSStartMainCommand());
        addGameSubCommand(GameTypeEnum.TNTRun, new TNTRunStartMainCommand());
        addGameSubCommand(GameTypeEnum.SnowballShowdown, new SnowBallStartMainCommand());
        addGameSubCommand(GameTypeEnum.ParkourWarrior, new ParkourWarriorStartMainCommand());
        addGameSubCommand(GameTypeEnum.HotyCodyDusky, new HotyCodyDuskyStartSubCommand());
        addGameSubCommand(GameTypeEnum.AceRace, new AceRaceStartMainCommand());
    }
}

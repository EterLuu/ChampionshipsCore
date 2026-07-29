package ink.ziip.championshipscore.command.game.area;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.game.area.battlebox.BattleBoxAreaMainCommand;
import ink.ziip.championshipscore.command.game.area.bingo.BingoAreaMainCommand;
import ink.ziip.championshipscore.command.game.area.buildmart.BuildMartAreaMainCommand;
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
        super("area", "游戏场地管理");
        addGameSubCommand(GameTypeEnum.Bingo, new BingoAreaMainCommand());
        addGameSubCommand(GameTypeEnum.BuildMart, new BuildMartAreaMainCommand());
        addGameSubCommand(GameTypeEnum.BattleBox, new BattleBoxAreaMainCommand());
        addGameSubCommand(GameTypeEnum.ParkourTag, new ParkourTagAreaMainCommand());
        addGameSubCommand(GameTypeEnum.SkyWars, new SkyWarsAreaMainCommand());
        addGameSubCommand(GameTypeEnum.TGTTOS, new TGTTOSAreaMainCommand());
        addGameSubCommand(GameTypeEnum.TNTRun, new TNTRunAreaMainCommand());
        addGameSubCommand(GameTypeEnum.DragonEggCarnival, new DragonEggCarnivalAreaMainCommand());
        addGameSubCommand(GameTypeEnum.SnowballShowdown, new SnowBallAreaMainCommand());
        addGameSubCommand(GameTypeEnum.ParkourWarrior, new ParkourWarriorAreaMainCommand());
        addGameSubCommand(GameTypeEnum.HotyCodyDusky, new HotyCodyDuskyAreaMainCommand());
    }
}

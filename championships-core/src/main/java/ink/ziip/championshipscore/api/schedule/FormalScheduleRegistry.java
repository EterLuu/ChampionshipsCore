package ink.ziip.championshipscore.api.schedule;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.acerace.AceRaceScheduleHandler;
import ink.ziip.championshipscore.api.schedule.acerace.AceRaceScheduleManager;
import ink.ziip.championshipscore.api.schedule.battlebox.BattleBoxScheduleManager;
import ink.ziip.championshipscore.api.schedule.bingo.BingoScheduleHandler;
import ink.ziip.championshipscore.api.schedule.bingo.BingoScheduleManager;
import ink.ziip.championshipscore.api.schedule.buildmart.BuildMartScheduleHandler;
import ink.ziip.championshipscore.api.schedule.buildmart.BuildMartScheduleManager;
import ink.ziip.championshipscore.api.schedule.frostbite.FrostbiteScheduleHandler;
import ink.ziip.championshipscore.api.schedule.frostbite.FrostbiteScheduleManager;
import ink.ziip.championshipscore.api.schedule.hotycodydusky.HotyCodyDuskyScheduleManager;
import ink.ziip.championshipscore.api.schedule.iamnotarobot.RobotScheduleHandler;
import ink.ziip.championshipscore.api.schedule.iamnotarobot.RobotScheduleManager;
import ink.ziip.championshipscore.api.schedule.laserbox.LaserBoxScheduleManager;
import ink.ziip.championshipscore.api.schedule.lasershowdown.LaserShowdownScheduleHandler;
import ink.ziip.championshipscore.api.schedule.lasershowdown.LaserShowdownScheduleManager;
import ink.ziip.championshipscore.api.schedule.parkourtag.ParkourTagScheduleManager;
import ink.ziip.championshipscore.api.schedule.parkourwarrior.ParkourWarriorScheduleHandler;
import ink.ziip.championshipscore.api.schedule.parkourwarrior.ParkourWarriorScheduleManager;
import ink.ziip.championshipscore.api.schedule.riptiderush.RiptideRushScheduleHandler;
import ink.ziip.championshipscore.api.schedule.riptiderush.RiptideRushScheduleManager;
import ink.ziip.championshipscore.api.schedule.skywars.SkyWarsScheduleHandler;
import ink.ziip.championshipscore.api.schedule.skywars.SkyWarsScheduleManager;
import ink.ziip.championshipscore.api.schedule.tgttos.TGTTOSScheduleHandler;
import ink.ziip.championshipscore.api.schedule.tgttos.TGTTOSScheduleManager;
import ink.ziip.championshipscore.api.schedule.tntrun.TNTRunScheduleHandler;
import ink.ziip.championshipscore.api.schedule.tntrun.TNTRunScheduleManager;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** One registration per ordinary formal game; finales retain their dedicated selection flow. */
final class FormalScheduleRegistry {
    private static final Map<GameTypeEnum, Function<ChampionshipsCore, FormalSchedule>> FACTORIES;

    static {
        var factories =
                new EnumMap<GameTypeEnum, Function<ChampionshipsCore, FormalSchedule>>(
                        GameTypeEnum.class);
        factories.put(
                GameTypeEnum.LaserShowdown,
                plugin ->
                        new LaserShowdownScheduleManager(
                                plugin, new LaserShowdownScheduleHandler(plugin)));
        factories.put(
                GameTypeEnum.SkyWars,
                plugin -> new SkyWarsScheduleManager(plugin, new SkyWarsScheduleHandler(plugin)));
        factories.put(
                GameTypeEnum.TNTRun,
                plugin -> new TNTRunScheduleManager(plugin, new TNTRunScheduleHandler(plugin)));
        factories.put(
                GameTypeEnum.TGTTOS,
                plugin -> new TGTTOSScheduleManager(plugin, new TGTTOSScheduleHandler(plugin)));
        factories.put(GameTypeEnum.BattleBox, plugin -> new BattleBoxScheduleManager(plugin));
        factories.put(GameTypeEnum.ParkourTag, plugin -> new ParkourTagScheduleManager(plugin));
        factories.put(
                GameTypeEnum.ParkourWarrior,
                plugin ->
                        new ParkourWarriorScheduleManager(
                                plugin, new ParkourWarriorScheduleHandler(plugin)));
        factories.put(
                GameTypeEnum.HotyCodyDusky, plugin -> new HotyCodyDuskyScheduleManager(plugin));
        factories.put(
                GameTypeEnum.Bingo,
                plugin -> new BingoScheduleManager(plugin, new BingoScheduleHandler(plugin)));
        factories.put(
                GameTypeEnum.AceRace,
                plugin -> new AceRaceScheduleManager(plugin, new AceRaceScheduleHandler(plugin)));
        factories.put(
                GameTypeEnum.BuildMart,
                plugin ->
                        new BuildMartScheduleManager(plugin, new BuildMartScheduleHandler(plugin)));
        factories.put(
                GameTypeEnum.RiptideRush,
                plugin ->
                        new RiptideRushScheduleManager(
                                plugin, new RiptideRushScheduleHandler(plugin)));
        factories.put(
                GameTypeEnum.FrostbiteFrenzy,
                plugin ->
                        new FrostbiteScheduleManager(plugin, new FrostbiteScheduleHandler(plugin)));
        factories.put(
                GameTypeEnum.CraftingFeast,
                plugin ->
                        new ink.ziip.championshipscore.api.schedule.craftingfeast
                                .CraftingFeastScheduleManager(
                                plugin,
                                new ink.ziip.championshipscore.api.schedule.craftingfeast
                                        .CraftingFeastScheduleHandler(plugin)));
        factories.put(
                GameTypeEnum.IAmNotARobot,
                plugin -> new RobotScheduleManager(plugin, new RobotScheduleHandler(plugin)));
        factories.put(GameTypeEnum.LaserBox, plugin -> new LaserBoxScheduleManager(plugin));
        FACTORIES = Map.copyOf(factories);
    }

    static Set<GameTypeEnum> supportedGames() {
        return FACTORIES.keySet();
    }

    static Map<GameTypeEnum, FormalSchedule> create(ChampionshipsCore plugin) {
        var schedules = new EnumMap<GameTypeEnum, FormalSchedule>(GameTypeEnum.class);
        FACTORIES.forEach((game, factory) -> schedules.put(game, factory.apply(plugin)));
        return java.util.Collections.unmodifiableMap(schedules);
    }

    private FormalScheduleRegistry() {}
}

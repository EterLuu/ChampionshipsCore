package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;

public class PlaceholderManager extends BaseManager {
    private BattleBoxPlaceholder battleBoxPlaceholder;
    private ParkourTagPlaceholder parkourTagPlaceholder;
    private DragonEggCarnivalPlaceholder dragonEggCarnivalPlaceholder;
    private SkyWarsPlaceholder skyWarsPlaceholder;
    private SnowballPlaceholder snowballPlaceholder;
    private TGTTOSPlaceholder tgttosPlaceholder;
    private TNTRunPlaceholder tntRunPlaceholder;
    private ChampionshipPlaceholder championshipPlaceholder;
    private VotePlaceholder votePlaceholder;
    private SchedulePlaceholder schedulePlaceholder;
    private LeaderboardPlaceholder leaderboardPlaceholder;
    private ParkourWarriorPlaceholder parkourWarriorPlaceholder;
    private HotyCodyDuskyPlaceholder hotyCodyDuskyPlaceholder;

    public PlaceholderManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        battleBoxPlaceholder = new BattleBoxPlaceholder(plugin);
        parkourTagPlaceholder = new ParkourTagPlaceholder(plugin);
        dragonEggCarnivalPlaceholder = new DragonEggCarnivalPlaceholder(plugin);
        skyWarsPlaceholder = new SkyWarsPlaceholder(plugin);
        snowballPlaceholder = new SnowballPlaceholder(plugin);
        tgttosPlaceholder = new TGTTOSPlaceholder(plugin);
        tntRunPlaceholder = new TNTRunPlaceholder(plugin);
        championshipPlaceholder = new ChampionshipPlaceholder(plugin);
        votePlaceholder = new VotePlaceholder(plugin);
        schedulePlaceholder = new SchedulePlaceholder(plugin);
        leaderboardPlaceholder = new LeaderboardPlaceholder(plugin);
        parkourWarriorPlaceholder = new ParkourWarriorPlaceholder(plugin);
        hotyCodyDuskyPlaceholder = new HotyCodyDuskyPlaceholder(plugin);

        battleBoxPlaceholder.register();
        parkourTagPlaceholder.register();
        dragonEggCarnivalPlaceholder.register();
        skyWarsPlaceholder.register();
        snowballPlaceholder.register();
        tgttosPlaceholder.register();
        tntRunPlaceholder.register();
        championshipPlaceholder.register();
        votePlaceholder.register();
        schedulePlaceholder.register();
        leaderboardPlaceholder.register();
        parkourWarriorPlaceholder.register();
        hotyCodyDuskyPlaceholder.register();
    }

    @Override
    public void unload() {
        battleBoxPlaceholder.unregister();
        parkourTagPlaceholder.unregister();
        dragonEggCarnivalPlaceholder.unregister();
        skyWarsPlaceholder.unregister();
        snowballPlaceholder.unregister();
        tgttosPlaceholder.unregister();
        tntRunPlaceholder.unregister();
        championshipPlaceholder.unregister();
        votePlaceholder.unregister();
        schedulePlaceholder.unregister();
        leaderboardPlaceholder.unregister();
        parkourWarriorPlaceholder.unregister();
        hotyCodyDuskyPlaceholder.unregister();
    }
}

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
    private BingoPlaceholder bingoPlaceholder;
    private ChampionshipPlaceholder championshipPlaceholder;
    private VotePlaceholder votePlaceholder;
    private SchedulePlaceholder schedulePlaceholder;
    private LeaderboardPlaceholder leaderboardPlaceholder;
    private AdvancementCCPlaceholder advancementCCPlaceholder;

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
        bingoPlaceholder = new BingoPlaceholder(plugin);
        championshipPlaceholder = new ChampionshipPlaceholder(plugin);
        votePlaceholder = new VotePlaceholder(plugin);
        schedulePlaceholder = new SchedulePlaceholder(plugin);
        leaderboardPlaceholder = new LeaderboardPlaceholder(plugin);
        advancementCCPlaceholder = new AdvancementCCPlaceholder(plugin);

        battleBoxPlaceholder.register();
        parkourTagPlaceholder.register();
        dragonEggCarnivalPlaceholder.register();
        skyWarsPlaceholder.register();
        snowballPlaceholder.register();
        tgttosPlaceholder.register();
        tntRunPlaceholder.register();
        bingoPlaceholder.register();
        championshipPlaceholder.register();
        votePlaceholder.register();
        schedulePlaceholder.register();
        leaderboardPlaceholder.register();
        advancementCCPlaceholder.register();
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
        bingoPlaceholder.unregister();
        championshipPlaceholder.unregister();
        votePlaceholder.unregister();
        schedulePlaceholder.unregister();
        leaderboardPlaceholder.unregister();
        advancementCCPlaceholder.unregister();
    }
}

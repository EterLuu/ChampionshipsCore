package ink.ziip.championshipscore.api.game.parkourtag;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ParkourTagManager extends BaseAreaManager<ParkourTagArea> {
    private final Map<UUID, Integer> chaserTimes = new ConcurrentHashMap<>();
    private final Map<ChampionshipTeam, Long> clockUsedTimes = new ConcurrentHashMap<>();

    public ParkourTagManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public void load() {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        File areasFolder = new File(plugin.getDataFolder() + File.separator + "parkourtag");
        areasFolder.mkdirs();

        scheduler.runTask(plugin, task -> {
            String[] areaList = areasFolder.list();
            if (areaList != null) {
                for (String file : areaList) {
                    String name = file.substring(0, file.length() - 4);
                    areas.put(name, new ParkourTagArea(plugin, new ParkourTagConfig(plugin, name)));
                }
            }
        });
    }

    @Override
    public void unload() {
        for (ParkourTagArea area : areas.values()) {
            if (area.getGameStageEnum() != GameStageEnum.WAITING) {
                area.endGame();
            }
        }
    }

    @Override
    public boolean addArea(String name) {
        if (areas.containsKey(name))
            return false;

        ParkourTagConfig parkourTagConfig = new ParkourTagConfig(plugin, name);
        parkourTagConfig.initializeConfiguration(plugin.getFolder());
        parkourTagConfig.setAreaName(name);
        parkourTagConfig.saveOptions();

        ParkourTagArea parkourTagArea = areas.putIfAbsent(name, new ParkourTagArea(plugin, parkourTagConfig));

        return parkourTagArea == null;
    }

    public void addChaserTimes(UUID uuid) {
        chaserTimes.put(uuid, chaserTimes.getOrDefault(uuid, 0) + 1);
    }

    public UUID getTeamChaser(ChampionshipTeam team) {
        for (UUID uuid : team.getMembers()) {
            if (chaserTimes.getOrDefault(uuid, 0) < 3) {
                return uuid;
            }
        }

        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    public void setClockUsedTimes(ChampionshipTeam championshipTeam) {
        clockUsedTimes.put(championshipTeam, System.currentTimeMillis());
    }

    public boolean canUseClock(ChampionshipTeam championshipTeam) {
        return (System.currentTimeMillis() - clockUsedTimes.getOrDefault(championshipTeam, 0L)) > 10000L;
    }

    public boolean canBeChaser(UUID uuid) {
        return !(chaserTimes.getOrDefault(uuid, 0) >= CCConfig.PARKOUR_TAG_MAX_CHASER_TIMES);
    }
}

package ink.ziip.championshipscore.api.game.acerace;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.configuration.ConfigOption;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

@Getter
@Setter
public class AceRaceConfig extends BaseGameConfig {
    private final String resourceName = "acerace/area.yml";
    private final String folderName = "acerace/";

    @ConfigOption(path = "name")
    private String areaName;

    @ConfigOption(path = "timer")
    private int timer;

    @ConfigOption(path = "laps")
    private int laps;

    /** Physical boundary of this course inside Ace Race's shared world. */
    @ConfigOption(path = "area-pos1", nullable = true)
    private Vector areaPos1;

    @ConfigOption(path = "area-pos2", nullable = true)
    private Vector areaPos2;

    @ConfigOption(path = "spectator-spawn-point", nullable = true)
    private Location spectatorSpawnPoint;

    @ConfigOption(path = "start-spawn-point", nullable = true)
    private Location startSpawnPoint;

    @ConfigOption(path = "start-line.fall-y")
    private int startFallY;

    @ConfigOption(path = "start-line.pos1", nullable = true)
    private Vector startLinePos1;

    @ConfigOption(path = "start-line.pos2", nullable = true)
    private Vector startLinePos2;

    @ConfigOption(path = "finish-line.pos1", nullable = true)
    private Vector finishLinePos1;

    @ConfigOption(path = "finish-line.pos2", nullable = true)
    private Vector finishLinePos2;

    @ConfigOption(path = "points.first-place")
    private int firstPlacePoints;

    @ConfigOption(path = "points.placement-decrement")
    private int placementDecrement;

    @ConfigOption(path = "points.minimum-finish")
    private int minimumFinishPoints;

    @ConfigOption(path = "points.bonuses.first-place")
    private int firstPlaceBonus;

    @ConfigOption(path = "points.bonuses.second-place")
    private int secondPlaceBonus;

    @ConfigOption(path = "points.bonuses.third-place")
    private int thirdPlaceBonus;

    @ConfigOption(path = "points.bonuses.fourth-to-ninth")
    private int fourthToNinthBonus;

    @ConfigOption(path = "points.bonuses.tenth-to-fourteenth")
    private int tenthToFourteenthBonus;

    @ConfigOption(path = "points.bonuses.fifteenth-to-nineteenth")
    private int fifteenthToNineteenthBonus;

    @ConfigOption(path = "progress-points")
    private ConfigurationSection progressPoints;

    @ConfigOption(path = "respawn-points")
    private List<String> respawnPoints;

    /** Zero-based progress point reached after each respawn marker; -1 means the start segment,
     * and -2 means that the runtime should infer the segment from the marker's coordinates. */
    @ConfigOption(path = "respawn-progress-points", nullable = true)
    private List<Integer> respawnProgressPoints;

    public AceRaceConfig(@NotNull ChampionshipsCore plugin, String areaName) {
        super(plugin, areaName);
    }

    @Override
    public int getLatestVersion() {
        return 18;
    }




    /** Supplies a mutable root for the guided progress-point editor. */
    public ConfigurationSection ensureProgressPoints() {
        if (progressPoints == null) progressPoints = configuration.createSection("progress-points");
        return progressPoints;
    }

    public List<String> ensureRespawnPoints() {
        if (respawnPoints == null) respawnPoints = new ArrayList<>();
        return respawnPoints;
    }

    public void setRespawnPoints(List<String> respawnPoints) {
        this.respawnPoints = respawnPoints == null ? new ArrayList<>() : new ArrayList<>(respawnPoints);
        ensureRespawnProgressPoints();
    }

    public List<Integer> ensureRespawnProgressPoints() {
        if (respawnProgressPoints == null) respawnProgressPoints = new ArrayList<>();
        while (respawnProgressPoints.size() < ensureRespawnPoints().size()) respawnProgressPoints.add(-2);
        while (respawnProgressPoints.size() > ensureRespawnPoints().size())
            respawnProgressPoints.removeLast();
        return respawnProgressPoints;
    }

    public Integer getRespawnProgressPointBinding(int index, int progressPointCount) {
        if (respawnProgressPoints == null || index < 0 || index >= respawnProgressPoints.size()) return null;
        Integer binding = respawnProgressPoints.get(index);
        if (binding == null || binding < -1 || binding >= progressPointCount) return null;
        return binding;
    }

    public void setRespawnProgressPointBinding(int index, int binding) {
        if (index < 0 || index >= ensureRespawnPoints().size()) return;
        if (binding < -1) binding = -2;
        ensureRespawnProgressPoints().set(index, binding);
    }

    public void addRespawnPoint(String location) {
        ensureRespawnPoints().add(location);
        ensureRespawnProgressPoints();
    }

    public void clearRespawnPoints() {
        ensureRespawnPoints().clear();
        ensureRespawnProgressPoints().clear();
    }

    public void moveRespawnPoint(int index, int newOrder) {
        if (index < 0 || index >= ensureRespawnPoints().size()
                || newOrder < 1 || newOrder > ensureRespawnPoints().size()) return;
        List<String> locations = ensureRespawnPoints();
        List<Integer> bindings = ensureRespawnProgressPoints();
        String location = locations.remove(index);
        Integer binding = bindings.remove(index);
        locations.add(newOrder - 1, location);
        bindings.add(newOrder - 1, binding);
    }

    public void removeRespawnPoint(int index) {
        if (index < 0 || index >= ensureRespawnPoints().size()) return;
        List<Integer> bindings = ensureRespawnProgressPoints();
        ensureRespawnPoints().remove(index);
        bindings.remove(index);
    }

    public boolean hasStartLine() {
        return startLinePos1 != null && startLinePos2 != null;
    }

    public boolean hasFinishLine() {
        return finishLinePos1 != null && finishLinePos2 != null;
    }

    public int getPlacementBonus(int place) {
        if (place == 1) return firstPlaceBonus;
        if (place == 2) return secondPlaceBonus;
        if (place == 3) return thirdPlaceBonus;
        if (place <= 9) return fourthToNinthBonus;
        if (place <= 14) return tenthToFourteenthBonus;
        if (place <= 19) return fifteenthToNineteenthBonus;
        return 0;
    }

}

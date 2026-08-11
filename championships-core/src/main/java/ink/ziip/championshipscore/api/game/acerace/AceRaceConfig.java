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
        return 17;
    }

    @Override
    protected void customizeMigratedConfiguration(@NotNull YamlConfiguration oldConfiguration,
                                                  @NotNull YamlConfiguration migratedConfiguration) {
        migratedConfiguration.set("area-pos1", null);
        migratedConfiguration.set("area-pos2", null);
        migratedConfiguration.set("void-y", null);
        // Version 11 briefly stored one shared line. Preserve that map by importing it as both
        // gates; the runtime still uses the old one-line direction rule until an admin separates them.
        if (oldConfiguration.contains("start-finish-line.pos1")
                && oldConfiguration.contains("start-finish-line.pos2")) {
            Object pos1 = oldConfiguration.get("start-finish-line.pos1");
            Object pos2 = oldConfiguration.get("start-finish-line.pos2");
            migratedConfiguration.set("start-line.pos1", pos1);
            migratedConfiguration.set("start-line.pos2", pos2);
            migratedConfiguration.set("finish-line.pos1", pos1);
            migratedConfiguration.set("finish-line.pos2", pos2);
            if (oldConfiguration.contains("start-finish-line.fall-y"))
                migratedConfiguration.set("start-line.fall-y", oldConfiguration.get("start-finish-line.fall-y"));
            migratedConfiguration.set("start-finish-line", null);
        }
        ConfigurationSection oldProgressPoints = oldConfiguration.getConfigurationSection("progress-points");
        if (oldProgressPoints == null) oldProgressPoints = oldConfiguration.getConfigurationSection("checkpoints");
        migrateProgressPoints(oldProgressPoints, migratedConfiguration);
        migrateRespawnPoints(oldConfiguration, oldProgressPoints, migratedConfiguration);
        migratedConfiguration.set("checkpoints", null);
        migratedConfiguration.set("points.first-place", 500);
        migratedConfiguration.set("points.placement-decrement", 10);
        migratedConfiguration.set("points.minimum-finish", 80);
        migratedConfiguration.set("points.progress", null);
        migratedConfiguration.set("points.bonuses.first-place", 320);
        migratedConfiguration.set("points.bonuses.second-place", 260);
        migratedConfiguration.set("points.bonuses.third-place", 200);
        migratedConfiguration.set("points.bonuses.fourth-to-ninth", 140);
        migratedConfiguration.set("points.bonuses.tenth-to-fourteenth", 80);
        migratedConfiguration.set("points.bonuses.fifteenth-to-nineteenth", 35);
        // Version 10 introduced a required finish-line crossing, replacing the obsolete rule that
        // the final progress point completes a lap. These bundled rules must supersede the old template.
        migratedConfiguration.set("rules", List.of(
                List.of(
                        "&#696969==========&r &#ff6b26[&d王牌竞速&#ff6b26] &#696969==========&r",
                        "&#ededed在限定时间内完成赛道，率先跑完全部圈数的选手排名靠前。",
                        "&#ededed按顺序通过进度点后，正向穿过终点线才会完成当前圈数。",
                        "&#ededed进度点负责赛段道具和摔落高度；重生点按位置绑定赛段，经过其 3 格内会同步进度并保存。"),
                List.of(
                        "&#696969==========&r &#fff566积分规则 &#696969==========&r",
                        "&#ededed完赛基础分从 &#ff6b26500 分&#ededed起，每后一名减少 &#ff6b2610 分&#ededed，最低 &#ff6b2680 分&#ededed。",
                        "&#ededed第 1/2/3 名额外获得 &#ff6b26320/260/200 分&#ededed。",
                        "&#ededed第 4–9/10–14/15–19 名额外获得 &#ff6b26140/80/35 分&#ededed；未完赛不得分。")));
    }

    /** Moves checkpoints to progress-points and converts legacy radius markers into vertical gates. */
    private static void migrateProgressPoints(ConfigurationSection oldRoot,
                                              @NotNull YamlConfiguration migratedConfiguration) {
        if (oldRoot == null) return;
        List<String> keys = new ArrayList<>(oldRoot.getKeys(false));
        keys.sort(Comparator.comparingInt(key -> {
            ConfigurationSection section = oldRoot.getConfigurationSection(key);
            return section == null ? Integer.MAX_VALUE : section.getInt("order", Integer.MAX_VALUE);
        }));
        List<Vector> blocks = keys.stream()
                .map(oldRoot::getConfigurationSection)
                .map(section -> section == null ? null : section.getVector("block"))
                .toList();
        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index);
            ConfigurationSection section = oldRoot.getConfigurationSection(key);
            if (section == null) continue;
            String path = "progress-points." + key;
            for (String childPath : section.getKeys(true)) {
                Object value = section.get(childPath);
                if (!(value instanceof ConfigurationSection))
                    migratedConfiguration.set(path + "." + childPath, value);
            }
            if (migratedConfiguration.getVector(path + ".pos1") != null
                    && migratedConfiguration.getVector(path + ".pos2") != null) {
                migratedConfiguration.set(path + ".block", null);
                continue;
            }
            Vector block = blocks.get(index);
            if (block == null) continue;

            Vector from = index > 0 && blocks.get(index - 1) != null ? blocks.get(index - 1) : block;
            Vector to = index + 1 < blocks.size() && blocks.get(index + 1) != null
                    ? blocks.get(index + 1) : block;
            if (from.equals(to) && index > 0 && blocks.get(index - 1) != null) from = blocks.get(index - 1);
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();
            Vector pos1 = block.clone();
            Vector pos2 = block.clone();
            if (Math.abs(dz) >= Math.abs(dx)) {
                pos1.add(new Vector(-5, 0, 0));
                pos2.add(new Vector(5, 0, 0));
            } else {
                pos1.add(new Vector(0, 0, -5));
                pos2.add(new Vector(0, 0, 5));
            }
            migratedConfiguration.set(path + ".pos1", pos1);
            migratedConfiguration.set(path + ".pos2", pos2);
            migratedConfiguration.set(path + ".equipment", AceRaceEquipment.NONE.configValue());
            migratedConfiguration.set(path + ".block", null);
        }
    }

    /** Preserves existing maps by turning each former checkpoint center into a separate respawn point. */
    private static void migrateRespawnPoints(@NotNull YamlConfiguration oldConfiguration,
                                             ConfigurationSection oldProgressPoints,
                                             @NotNull YamlConfiguration migratedConfiguration) {
        if (oldConfiguration.contains("respawn-points")) return;
        if (oldProgressPoints == null) {
            migratedConfiguration.set("respawn-points", List.of());
            return;
        }

        List<String> keys = new ArrayList<>(oldProgressPoints.getKeys(false));
        keys.sort(Comparator.comparingInt(key -> {
            ConfigurationSection section = oldProgressPoints.getConfigurationSection(key);
            return section == null ? Integer.MAX_VALUE : section.getInt("order", Integer.MAX_VALUE);
        }));
        List<String> migrated = new ArrayList<>();
        for (String key : keys) {
            ConfigurationSection section = oldProgressPoints.getConfigurationSection(key);
            if (section == null) continue;
            Vector pos1 = section.getVector("pos1");
            Vector pos2 = section.getVector("pos2");
            Vector block = section.getVector("block");
            if (pos1 == null || pos2 == null) {
                if (block == null) continue;
                pos1 = block;
                pos2 = block;
            }
            double x = (Math.min(pos1.getBlockX(), pos2.getBlockX())
                    + Math.max(pos1.getBlockX(), pos2.getBlockX()) + 1) / 2.0D;
            double y = Math.min(pos1.getBlockY(), pos2.getBlockY()) + 1.0D;
            double z = (Math.min(pos1.getBlockZ(), pos2.getBlockZ())
                    + Math.max(pos1.getBlockZ(), pos2.getBlockZ()) + 1) / 2.0D;
            migrated.add("acerace:" + x + ":" + y + ":" + z + ":0.0:0.0");
        }
        migratedConfiguration.set("respawn-points", migrated);
    }

    /** Ace Race owns its whole world, so it deliberately has no per-map bounding box. */
    @Override
    public Vector getAreaPos1() {
        return null;
    }

    /** Ace Race owns its whole world, so it deliberately has no per-map bounding box. */
    @Override
    public Vector getAreaPos2() {
        return null;
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

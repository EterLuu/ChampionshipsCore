package ink.ziip.championshipscore.api.game.buildmart;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Per-map Build Mart configuration. The arena is restored from {@code plugin/maps/<world-name>}.
 * Geometry is split between the central resource market and a single base template; the remaining team
 * bases are derived from that template using the map's fixed placement grid.
 */
@Getter
@Setter
public class BuildMartConfig extends BaseGameConfig {
    private final String resourceName = "buildmart/areas/area.yml";
    private final String folderName = "buildmart/areas/";

    public BuildMartConfig(@NotNull ChampionshipsCore plugin, String areaName) {
        super(plugin, areaName);
    }

    @Override
    public int getLatestVersion() {
        return 4;
    }

    @ConfigOption(path = "name")
    private String areaName;

    /** Physical map world. Empty legacy configs continue to use the historical world. */
    @ConfigOption(path = "world-name", nullable = true)
    private String worldName;

    public String resolveWorldName() {
        return worldName == null || worldName.isBlank() ? "buildmart" : worldName;
    }

    /** Round duration in seconds. Default 12 minutes. */
    @ConfigOption(path = "timer")
    private int timer = 720;

    /** Preparation countdown before the round starts, in seconds. */
    @ConfigOption(path = "prepare-time")
    private int prepareTime = 10;

    /** Number of internal team-base replicas physically stamped into this map. */
    @ConfigOption(path = "base-count")
    private int baseCount = 8;

    @ConfigOption(path = "area-pos1", nullable = true)
    private Vector areaPos1;

    @ConfigOption(path = "area-pos2", nullable = true)
    private Vector areaPos2;

    @ConfigOption(path = "spectator-spawn-point", nullable = true)
    private Location spectatorSpawnPoint;

    /** Central resource-market spawn; players returning from their base land here. */
    @ConfigOption(path = "hub-spawn-point", nullable = true)
    private Location hubSpawnPoint;

    /** Hub bounding box: inside it flight is disabled and block placement is blocked. */
    @ConfigOption(path = "hub-pos1", nullable = true)
    private Vector hubPos1;

    @ConfigOption(path = "hub-pos2", nullable = true)
    private Vector hubPos2;

    /**
     * Region inside the hub that returns a player to their own team base. Players walking into this box
     * are teleported to {@code bases.<teamId>.spawn}.
     */
    @ConfigOption(path = "hub-return-pos1", nullable = true)
    private Vector hubReturnPos1;

    @ConfigOption(path = "hub-return-pos2", nullable = true)
    private Vector hubReturnPos2;

    /** Anchor where the current golden blueprint is pasted in the hub for players to observe. */
    @ConfigOption(path = "golden-display-point", nullable = true)
    private Location goldenDisplayPoint;

    /** How often (seconds) the golden blueprint is swapped; it stays live for this whole window. */
    @ConfigOption(path = "golden-refresh-seconds")
    private int goldenRefreshSeconds = 120;

    /** Cooldown (ms) on portal triggers to stop the player bouncing back and forth. */
    @ConfigOption(path = "portal-cooldown-millis")
    private long portalCooldownMillis = 1000L;

    /**
     * The single configured base template (seat 0's geometry), or {@code null} if unconfigured. Read live
     * from the {@code base} section so the per-leaf writes from {@link #setBaseLocation} are never clobbered
     * by {@link #saveOptions()}. Other seats are derived from this via {@link #getSeatBase(int)}.
     */
    @Nullable
    public BuildMartBase getBaseTemplate() {
        if (configuration == null) return null;
        ConfigurationSection section = configuration.getConfigurationSection("base");
        if (section == null) return null;
        return new BuildMartBase(0, section);
    }

    /**
     * Geometry for the base in {@code seat} (0-based), derived by translating the configured template by
     * {@link BuildMartLayout#delta(int)}, or {@code null} if no template is configured. Seat 0 returns the
     * template unchanged (delta is zero).
     */
    @Nullable
    public BuildMartBase getSeatBase(int seat) {
        return resolveMapGeometry().baseForSeat(seat);
    }

    public @NotNull BuildMartMapGeometry resolveMapGeometry() {
        return BuildMartMapGeometry.from(this);
    }

    /** True when {@code location} lies within the configured hub bounding box. */
    public boolean isInHub(@NotNull Location location) {
        return resolveMapGeometry().isInHub(location);
    }

    /** True when {@code location} lies within the hub→base return region. */
    public boolean isInHubReturn(@NotNull Location location) {
        return resolveMapGeometry().isInHubReturn(location);
    }

    /**
     * Writes a string-serialized location into the {@code base.<key>} template section and persists it.
     * Used by the area set-up commands; the admin configures these standing in seat 0's prepared base.
     */
    public void setBaseLocation(String key, @NotNull Location location) {
        if (configuration == null) return;
        configuration.set("base." + key, Utils.getLocationConfigString(location));
        try {
            configuration.save(configurationPath.toFile());
        } catch (Exception exception) {
            plugin.getLogger().warning(Utils.formatGameLog(GameTypeEnum.BuildMart, areaName, "配置", "保存",
                    "无法保存基地坐标 | " + exception.getMessage()));
        }
    }

    @Override
    protected void customizeMigratedConfiguration(@NotNull YamlConfiguration oldConfiguration,
                                                  @NotNull YamlConfiguration migratedConfiguration) {
        if (oldConfiguration.getString("world-name", "").isBlank())
            migratedConfiguration.set("world-name", "buildmart");
    }
}

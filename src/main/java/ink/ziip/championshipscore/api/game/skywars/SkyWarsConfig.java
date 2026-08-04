package ink.ziip.championshipscore.api.game.skywars;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.configuration.ConfigOption;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Getter
@Setter
public class SkyWarsConfig extends BaseGameConfig {
    private final String resourceName = "skywars/area.yml";
    private final String folderName = "skywars/";

    public SkyWarsConfig(@NotNull ChampionshipsCore plugin, String areaName) {
        super(plugin, areaName);
    }

    @Override
    public int getLatestVersion() {
        return 5;
    }

    @ConfigOption(path = "name")
    private String areaName;

    /** Named rules profile. Flat v3 fields remain the compatibility source while variants are introduced. */
    @ConfigOption(path = "variant")
    private String variantId = "inline";

    @ConfigOption(path = "timer")
    private int timer;

    @ConfigOption(path = "area-pos1")
    private Vector areaPos1;

    @ConfigOption(path = "area-pos2")
    private Vector areaPos2;

    @ConfigOption(path = "spectator-spawn-point")
    private Location spectatorSpawnPoint;

    @ConfigOption(path = "boundary-center-point")
    private Location boundaryCenterPoint;

    @ConfigOption(path = "team-spawn-points")
    private List<String> teamSpawnPoints;

    @ConfigOption(path = "glass-cage")
    private boolean glassCage;

    @ConfigOption(path = "boundary.default-height")
    private Integer boundaryDefaultHeight;

    @ConfigOption(path = "boundary.middle-height")
    private Integer boundaryMiddleHeight;

    @ConfigOption(path = "boundary.lowest-height")
    private Integer boundaryLowestHeight;

    @ConfigOption(path = "boundary.radius")
    private Integer boundaryRadius;

    @ConfigOption(path = "shrink-time")
    private List<String> shrinkTime;

    @ConfigOption(path = "time.enable-boundary-shrink")
    private Integer timeEnableBoundaryShrink;

    @ConfigOption(path = "time.disable-health-regain")
    private Integer timeDisableHealthRegain;

    @ConfigOption(path = "time.spawn-happy-ghast", nullable = true)
    private Integer spawnHappyGhast;

    @ConfigOption(path = "scoring.kill")
    private int killPoints = 40;

    @ConfigOption(path = "scoring.survive")
    private int survivalPoints = 50;

    @ConfigOption(path = "scoring.player-elimination-survival")
    private int playerEliminationSurvivalPoints = 8;

    @ConfigOption(path = "scoring.team-elimination-survival")
    private int teamEliminationSurvivalPoints = 4;

    public @NotNull SkyWarsVariant resolveVariant() {
        return resolveInlineVariant();
    }

    public @NotNull SkyWarsVariant resolveInlineVariant() {
        return SkyWarsVariant.from(this);
    }

    public @NotNull SkyWarsMapGeometry resolveMapGeometry() {
        return SkyWarsMapGeometry.from(this);
    }

    @Override
    protected void customizeMigratedConfiguration(@NotNull YamlConfiguration oldConfiguration,
                                                  @NotNull YamlConfiguration migratedConfiguration) {
        if (!oldConfiguration.contains("boundary-center-point") && oldConfiguration.contains("pre-spawn-point"))
            migratedConfiguration.set("boundary-center-point", oldConfiguration.get("pre-spawn-point"));

        // An absent schedule historically meant "do not shrink". It must not inherit the bundled
        // large-map schedule merely because a newer template introduced that default.
        if (!oldConfiguration.contains("shrink-time"))
            migratedConfiguration.set("shrink-time", List.of());
        if (!oldConfiguration.contains("time.spawn-happy-ghast"))
            migratedConfiguration.set("time.spawn-happy-ghast", null);
    }
}

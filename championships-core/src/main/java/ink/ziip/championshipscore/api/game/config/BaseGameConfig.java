package ink.ziip.championshipscore.api.game.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import ink.ziip.championshipscore.logging.LogText;

import lombok.Getter;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

@Getter
public abstract class BaseGameConfig extends BaseConfigurationFile {
    protected final String configName;

    /** Prepare publication metadata stored explicitly in every current map configuration. */
    @ConfigOption(path = "prepare.published")
    protected Boolean preparePublished;

    @ConfigOption(path = "prepare.dirty")
    protected Boolean prepareDirty;

    @ConfigOption(path = "prepare.revision")
    protected Integer prepareRevision;

    @ConfigOption(path = "prepare.published-at", nullable = true)
    protected Long preparePublishedAt;

    @ConfigOption(path = "prepare.world-built")
    protected Boolean prepareWorldBuilt;

    public BaseGameConfig(@NotNull ChampionshipsCore plugin, String configName) {
        super(plugin);
        this.configName = configName;
    }

    /** Reloads this live map config atomically while leaving its world and listeners untouched. */
    public boolean reloadConfigurationChecked(Path pluginFolder) {
        String previous = captureRuntimeConfiguration();
        if (super.initializeConfigurationChecked(pluginFolder)) return true;
        if (previous != null) restoreRuntimeConfiguration(previous);
        return false;
    }

    @Override
    public String getFileName() {
        return getFolderName() + getConfigName() + ".yml";
    }

    /**
     * Rebinds this map definition from one physical world to another. Map worlds contain a mixture
     * of raw Location sections and string-serialized locations, so both representations must move
     * together with the {@code world-name} field.
     *
     * @return whether this configuration owned {@code oldWorldName} and was saved successfully
     */
    public boolean renameWorldReferences(
            @NotNull String oldWorldName, @NotNull World oldWorld, @NotNull World newWorld) {
        if (configuration == null
                || configurationPath == null
                || !oldWorldName.equals(configuration.getString("world-name"))) {
            return false;
        }

        configuration.set("world-name", newWorld.getName());
        rewriteWorldReferences(
                configuration,
                oldWorldName,
                oldWorld.getKey().toString(),
                newWorld.getName(),
                newWorld.getKey().toString());
        try {
            configuration.save(configurationPath.toFile());
            loadFileOptions();
            return true;
        } catch (Exception exception) {
            plugin.getLogger()
                    .log(
                            Level.SEVERE,
                            LogText.formatModuleLog(
                                    "GameConfig",
                                    "重命名世界",
                                    "配置文件="
                                            + getFileName()
                                            + " 无法更新世界="
                                            + oldWorldName
                                            + " -> "
                                            + newWorld.getName()),
                            exception);
            return false;
        }
    }

    /**
     * True when this map's physical world name is configurable rather than derived by game code.
     */
    public boolean ownsNamedWorld(@NotNull String worldName) {
        return configuration != null && worldName.equals(configuration.getString("world-name"));
    }

    /** Stores/reads the physical world binding for map types with a configurable world. */
    public void bindConfiguredWorld(@NotNull String worldName) {
        configuration.set("world-name", worldName);
        for (Field field : getConfigFields()) {
            ConfigOption option = field.getDeclaredAnnotation(ConfigOption.class);
            if (option == null
                    || !"world-name".equals(option.path())
                    || field.getType() != String.class) continue;
            try {
                field.setAccessible(true);
                field.set(this, worldName);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("无法绑定地图世界", exception);
            }
        }
    }

    public @NotNull String getConfiguredWorld() {
        return configuration == null ? "" : configuration.getString("world-name", "");
    }

    /** First configured participant spawn, falling back to the map's spectator spawn. */
    public @Nullable Location getGameSpawnPoint() {
        return GameSpawnResolver.resolve(this);
    }

    public boolean isWorldBindingPending() {
        return configuration != null
                && configuration.contains("world-name")
                && configuration.getString("world-name", "").isBlank();
    }

    private static void rewriteWorldReferences(
            @NotNull ConfigurationSection section,
            @NotNull String oldWorldName,
            @NotNull String oldWorldKey,
            @NotNull String newWorldName,
            @NotNull String newWorldKey) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                rewriteWorldReferences(child, oldWorldName, oldWorldKey, newWorldName, newWorldKey);
            } else if ("world".equals(key) && oldWorldName.equals(value)) {
                section.set(key, newWorldName);
            } else if ("world_key".equals(key) && oldWorldKey.equals(value)) {
                section.set(key, newWorldKey);
            } else if (value instanceof List<?> values) {
                List<Object> rewritten = new ArrayList<>(values.size());
                boolean changed = false;
                for (Object entry : values) {
                    Object replacement = entry;
                    if (entry instanceof String string && string.startsWith(oldWorldName + ":")) {
                        replacement = newWorldName + string.substring(oldWorldName.length());
                        changed = true;
                    }
                    rewritten.add(replacement);
                }
                if (changed) section.set(key, rewritten);
            }
        }
    }

    @Override
    protected Object coerceLocationSection(Object value, Field field) {
        return coerceLocationSection(value, field, false);
    }

    @Override
    public void loadFromConfiguration(@NotNull YamlConfiguration document) {
        super.loadFromConfiguration(document);
        rebindUnresolvedLocationWorlds();
    }

    /**
     * Map locations may be read while their physical world is not loaded yet. Once a map is
     * reloaded after its world has been loaded, attach that world to every raw location section so
     * shared lifecycle teleports (including the rule-introduction spawn) cannot pass a null world
     * to Bukkit. Locations that already resolve to a world are left untouched.
     */
    private void rebindUnresolvedLocationWorlds() {
        String configuredWorld = getConfiguredWorld();
        if (configuredWorld == null || configuredWorld.isBlank()) return;
        World world = plugin.getServer().getWorld(configuredWorld);
        if (world == null) return;
        for (Field field : getConfigFields()) {
            if (field.getType() != Location.class) continue;
            try {
                field.setAccessible(true);
                Location location = (Location) field.get(this);
                if (location != null && location.getWorld() == null) location.setWorld(world);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("无法绑定地图位置世界: " + field.getName(), exception);
            }
        }
    }

    /**
     * Spawn point of the optional rule-introduction phase: players gather here for the 45s rules
     * broadcast, then move to the normal preparation spawn. When empty, the spectator spawn is
     * used.
     */
    @ConfigOption(path = "introduction-spawn-point", nullable = true)
    protected Location introductionSpawnPoint;

    /** Per-map movement mode used only during the optional rule-introduction phase. */
    @ConfigOption(path = "introduction-game-mode", nullable = true)
    protected String introductionGameModeName = "ADVENTURE";

    /**
     * Optional cuboid removed during the opening countdown. The coordinates are block-inclusive
     * WorldEdit endpoints in this map's world; replica-based games translate them per copy at
     * runtime.
     */
    @ConfigOption(path = "countdown-block-disappearance.pos1", nullable = true)
    protected Vector countdownBlockDisappearancePos1;

    @ConfigOption(path = "countdown-block-disappearance.pos2", nullable = true)
    protected Vector countdownBlockDisappearancePos2;

    /** RANDOM, DOOR_EAST_WEST, DOOR_NORTH_SOUTH, DOOR_VERTICAL or DIRECT. */
    @ConfigOption(path = "countdown-block-disappearance.mode", nullable = true)
    protected String countdownBlockDisappearanceMode = "RANDOM";

    public void setIntroductionSpawnPoint(Location introductionSpawnPoint) {
        this.introductionSpawnPoint = introductionSpawnPoint;
    }

    public GameMode getIntroductionGameMode() {
        return "SPECTATOR".equalsIgnoreCase(introductionGameModeName)
                ? GameMode.SPECTATOR
                : GameMode.ADVENTURE;
    }

    public void setIntroductionGameMode(GameMode gameMode) {
        introductionGameModeName = gameMode == GameMode.SPECTATOR ? "SPECTATOR" : "ADVENTURE";
    }

    public void setCountdownBlockDisappearanceBounds(Vector pos1, Vector pos2) {
        countdownBlockDisappearancePos1 = pos1 == null ? null : pos1.clone();
        countdownBlockDisappearancePos2 = pos2 == null ? null : pos2.clone();
    }

    public void clearCountdownBlockDisappearanceBounds() {
        countdownBlockDisappearancePos1 = null;
        countdownBlockDisappearancePos2 = null;
    }

    public boolean hasCountdownBlockDisappearance() {
        return countdownBlockDisappearancePos1 != null && countdownBlockDisappearancePos2 != null;
    }

    public void setCountdownBlockDisappearanceMode(String mode) {
        countdownBlockDisappearanceMode = mode == null ? "RANDOM" : mode;
    }

    /**
     * Rule sections broadcast one-by-one in chat during the introduction phase; each inner list is
     * one message block. Leave empty to skip the introduction.
     */
    @ConfigOption(path = "rules", nullable = true)
    protected List<List<String>> rules;

    public abstract String getAreaName();

    public abstract String getFolderName();

    public abstract Vector getAreaPos1();

    public abstract Vector getAreaPos2();

    public abstract Location getSpectatorSpawnPoint();

    public boolean isPreparePublished() {
        return Boolean.TRUE.equals(preparePublished);
    }

    public boolean isPrepareDirty() {
        return Boolean.TRUE.equals(prepareDirty);
    }

    public boolean isPrepareReady() {
        return isPreparePublished() && !isPrepareDirty();
    }

    /**
     * Called only for a newly created map, so an incomplete map can never be started accidentally.
     */
    public void beginPrepareDraft() {
        preparePublished = false;
        prepareDirty = true;
        prepareWorldBuilt = false;
        if (prepareRevision == null) prepareRevision = 0;
        saveOptions();
    }

    /**
     * Any guided edit invalidates the last published revision until the admin validates and
     * publishes.
     */
    public void markPrepareDirty() {
        prepareDirty = true;
        saveOptions();
    }

    public void markPreparePublished() {
        preparePublished = true;
        prepareDirty = false;
        prepareRevision = (prepareRevision == null ? 0 : prepareRevision) + 1;
        preparePublishedAt = System.currentTimeMillis();
        saveOptions();
    }

    public boolean isPrepareWorldBuilt() {
        return Boolean.TRUE.equals(prepareWorldBuilt);
    }

    public void markPrepareWorldBuilt() {
        prepareWorldBuilt = true;
        prepareDirty = true;
        saveOptions();
    }
}

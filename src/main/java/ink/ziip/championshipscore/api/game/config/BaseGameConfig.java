package ink.ziip.championshipscore.api.game.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * Game map configs load through this method rather than {@code BaseConfigurationManager}, so the
     * version check would never run for them. Migrate outdated files here by updating the current file
     * in place from the latest bundled template while preserving user-owned values.
     */
    @Override
    public void initializeConfiguration(Path pluginFolder) {
        normalizeSerializedLocations(pluginFolder.resolve(getFileName()));
        super.initializeConfiguration(pluginFolder);
        checkVersion(true);
    }

    @Override
    public String getFileName() {
        return getFolderName() + getConfigName() + ".yml";
    }

    @Override
    public void saveOptions() {
        try {
            saveCustomOptions();

            for (Field field : getConfigFields()) {
                field.setAccessible(true);
                ConfigOption co = field.getDeclaredAnnotation(ConfigOption.class);
                if (co != null) {
                    if (field.getType() == Location.class) {
                        saveRawLocation(co.path(), (Location) field.get(this));
                    } else {
                        configuration.set(co.path(), field.get(this));
                    }
                }
            }

            configuration.save(configurationPath.toFile());
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("GameConfig", "保存",
                    "配置文件=" + getFileName() + " 保存选项失败"), exception);
        }
    }

    /**
     * Rebinds this map definition from one physical world to another. Map worlds contain a mixture
     * of raw Location sections and legacy string-serialized locations, so both representations must
     * move together with the {@code world-name} field.
     *
     * @return whether this configuration owned {@code oldWorldName} and was saved successfully
     */
    public boolean renameWorldReferences(@NotNull String oldWorldName, @NotNull World oldWorld,
                                         @NotNull World newWorld) {
        if (configuration == null || configurationPath == null
                || !oldWorldName.equals(configuration.getString("world-name"))) {
            return false;
        }

        configuration.set("world-name", newWorld.getName());
        rewriteWorldReferences(configuration, oldWorldName, oldWorld.getKey().toString(),
                newWorld.getName(), newWorld.getKey().toString());
        try {
            configuration.save(configurationPath.toFile());
            loadFileOptions();
            return true;
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("GameConfig", "重命名世界",
                    "配置文件=" + getFileName() + " 无法更新世界=" + oldWorldName
                            + " -> " + newWorld.getName()), exception);
            return false;
        }
    }

    /** True when this map's physical world name is configurable rather than derived by game code. */
    public boolean ownsNamedWorld(@NotNull String worldName) {
        return configuration != null && worldName.equals(configuration.getString("world-name"));
    }

    /** Stores/reads the physical world binding for map types that historically derived it from the map id. */
    public void bindConfiguredWorld(@NotNull String worldName) {
        configuration.set("world-name", worldName);
        for (Field field : getConfigFields()) {
            ConfigOption option = field.getDeclaredAnnotation(ConfigOption.class);
            if (option == null || !"world-name".equals(option.path()) || field.getType() != String.class) continue;
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

    public boolean isWorldBindingPending() {
        return configuration != null && configuration.contains("world-name")
                && configuration.getString("world-name", "").isBlank();
    }

    private static void rewriteWorldReferences(@NotNull ConfigurationSection section,
                                               @NotNull String oldWorldName, @NotNull String oldWorldKey,
                                               @NotNull String newWorldName, @NotNull String newWorldKey) {
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

    /** Converts Bukkit's eager Location serializer into a raw section that also loads before its world. */
    private void normalizeSerializedLocations(@NotNull Path file) {
        try {
            if (!Files.isRegularFile(file)) return;
            String original = Files.readString(file, StandardCharsets.UTF_8);
            String normalized = original.replaceAll("(?m)^[\\t ]*==: org\\.bukkit\\.Location\\R", "");
            if (!normalized.equals(original)) Files.writeString(file, normalized, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, Utils.formatModuleLog("GameConfig", "迁移",
                    "配置文件=" + getFileName() + " 无法规范化 Location 格式"), exception);
        }
    }

    private void saveRawLocation(@NotNull String path, Location location) {
        configuration.set(path, null);
        if (location == null) return;
        ConfigurationSection section = configuration.createSection(path);
        if (location.getWorld() != null) section.set("world_key", location.getWorld().getKey().toString());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("pitch", location.getPitch());
        section.set("yaw", location.getYaw());
    }

    @Override
    protected Object coerceLocationSection(Object value, Field field) {
        return coerceLocationSection(value, field, false);
    }

    @Override
    public void loadFromConfiguration(@NotNull YamlConfiguration yamlConfiguration) {
        for (Field field : getConfigFields()) {
            field.setAccessible(true);
            ConfigOption configOption = field.getDeclaredAnnotation(ConfigOption.class);
            if (configOption != null) {
                try {
                    Object value = null;

                    // If are lists, better use direct get
                    if (field.getType() == List.class && field.getGenericType() instanceof ParameterizedType) {
                        Type type = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                        if (type == Integer.class) {
                            value = yamlConfiguration.getIntegerList(configOption.path());
                        } else if (type == Double.class) {
                            value = yamlConfiguration.getDoubleList(configOption.path());
                        } else if (type == Float.class) {
                            value = yamlConfiguration.getFloatList(configOption.path());
                        } else if (type == Short.class) {
                            value = yamlConfiguration.getShortList(configOption.path());
                        } else if (type == String.class) {
                            value = yamlConfiguration.getStringList(configOption.path());
                        } else if (type instanceof ParameterizedType nestedType && nestedType.getRawType() == List.class) {
                            // Nested lists (e.g. List<List<String>> rule sections): Bukkit hands them back as-is.
                            value = yamlConfiguration.getList(configOption.path());
                        }
                    }

                    // Otherwise get it normally
                    if (value == null) value = yamlConfiguration.get(configOption.path());

                    // Locations may be stored as a raw section (no '==' marker); rebuild them.
                    value = coerceLocationSection(value, field);
                    value = coerceNumericValue(value, field.getType());

                    if (value != null) {
                        if (value instanceof String)
                            value = Utils.translateColorCodes((String) value);
                        field.set(this, value);
                    }
                    else if (!configOption.nullable() && !loadingDefaults) {
                        plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("GameConfig", "加载",
                                "配置文件=" + getFileName() + " 缺少路径=" + configOption.path()));
                    }
                } catch (Exception exception) {
                    plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("GameConfig", "加载",
                            "配置文件=" + getFileName() + " 路径=" + configOption.path() + " 加载失败"), exception);
                }
            }
        }
    }

    /** Bukkit YAML chooses the narrowest numeric wrapper; reflection requires the declared wrapper exactly. */
    private static Object coerceNumericValue(Object value, Class<?> targetType) {
        if (!(value instanceof Number number)) return value;
        if (targetType == byte.class || targetType == Byte.class) return number.byteValue();
        if (targetType == short.class || targetType == Short.class) return number.shortValue();
        if (targetType == int.class || targetType == Integer.class) return number.intValue();
        if (targetType == long.class || targetType == Long.class) return number.longValue();
        if (targetType == float.class || targetType == Float.class) return number.floatValue();
        if (targetType == double.class || targetType == Double.class) return number.doubleValue();
        return value;
    }

    @Override
    public void loadFromOutdatedConfiguration(@NotNull YamlConfiguration yamlConfiguration) {
        try {
            // Preserve every user-owned leaf, including game-specific custom sections which are not
            // represented by @ConfigOption fields (for example Build Mart's base template). The new
            // bundled template still supplies newly introduced paths, while its version marker wins.
            for (String path : yamlConfiguration.getKeys(true)) {
                if ("dont-edit-this.version".equals(path)) continue;
                Object value = yamlConfiguration.get(path);
                if (!(value instanceof ConfigurationSection)) {
                    configuration.set(path, value);
                }
            }

            customizeMigratedConfiguration(yamlConfiguration, configuration);

            configuration.save(configurationPath.toFile());

            // Reload options from the file
            loadFileOptions();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("GameConfig", "保存",
                    "配置文件=" + getFileName() + " 保存失败"), exception);
        }
    }

    /** Per-game hook for version-specific defaults that cannot safely come from a shared map template. */
    protected void customizeMigratedConfiguration(@NotNull YamlConfiguration oldConfiguration,
                                                  @NotNull YamlConfiguration migratedConfiguration) {
    }

    /**
     * Spawn point of the optional rule-introduction phase: players gather here for the 45s rules
     * broadcast, then move to the normal preparation spawn. When empty, the spectator spawn is used.
     */
    @ConfigOption(path = "introduction-spawn-point", nullable = true)
    protected Location introductionSpawnPoint;

    public void setIntroductionSpawnPoint(Location introductionSpawnPoint) {
        this.introductionSpawnPoint = introductionSpawnPoint;
    }

    /**
     * Rule sections broadcast one-by-one in chat during the introduction phase; each inner list is one
     * message block. Leave empty to skip the introduction.
     */
    @ConfigOption(path = "rules", nullable = true)
    protected List<List<String>> rules;

    /**
     * Collects the declared fields of the concrete config class and its superclasses up to (and
     * including) {@link BaseGameConfig}, so options declared once on the base class (like
     * {@link #introductionSpawnPoint} and {@link #rules}) are loaded/saved for every game config.
     */
    private List<Field> getConfigFields() {
        List<Field> fields = new ArrayList<>();
        Class<?> type = getClass();
        while (type != null && type != BaseConfigurationFile.class) {
            fields.addAll(Arrays.asList(type.getDeclaredFields()));
            type = type.getSuperclass();
        }
        return fields;
    }

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

    /** Called only for a newly created map, so an incomplete map can never be started accidentally. */
    public void beginPrepareDraft() {
        preparePublished = false;
        prepareDirty = true;
        prepareWorldBuilt = false;
        if (prepareRevision == null) prepareRevision = 0;
        saveOptions();
    }

    /** Any guided edit invalidates the last published revision until the admin validates and publishes. */
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

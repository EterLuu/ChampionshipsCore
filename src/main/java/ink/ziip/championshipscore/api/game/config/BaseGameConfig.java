package ink.ziip.championshipscore.api.game.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Location;
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

    /**
     * Prepare publication metadata. A missing published flag means an existing pre-refactor map and is
     * treated as published for backwards compatibility; newly created maps explicitly enter draft mode.
     */
    @ConfigOption(path = "prepare.published", nullable = true)
    protected Boolean preparePublished;

    @ConfigOption(path = "prepare.dirty", nullable = true)
    protected Boolean prepareDirty;

    @ConfigOption(path = "prepare.revision", nullable = true)
    protected Integer prepareRevision;

    @ConfigOption(path = "prepare.published-at", nullable = true)
    protected Long preparePublishedAt;

    @ConfigOption(path = "prepare.world-built", nullable = true)
    protected Boolean prepareWorldBuilt;

    public BaseGameConfig(@NotNull ChampionshipsCore plugin, String configName) {
        super(plugin);
        this.configName = configName;
    }

    /**
     * Game map configs load through this method rather than {@code BaseConfigurationManager}, so the
     * version check would never run for them. Migrate outdated files here: the old file is renamed to
     * {@code *.outdated} and its values are copied onto the latest bundled template.
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
     * broadcast, then move to the normal preparation spawn. Leave empty to skip the introduction.
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

    /** Existing maps without lifecycle metadata remain playable. */
    public boolean isPreparePublished() {
        return preparePublished == null || preparePublished;
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

    /** Missing metadata means an existing legacy map whose world was already built. */
    public boolean isPrepareWorldBuilt() {
        return prepareWorldBuilt == null || prepareWorldBuilt;
    }

    public void markPrepareWorldBuilt() {
        prepareWorldBuilt = true;
        prepareDirty = true;
        saveOptions();
    }
}

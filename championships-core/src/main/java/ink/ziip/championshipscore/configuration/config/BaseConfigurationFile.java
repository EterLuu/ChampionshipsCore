package ink.ziip.championshipscore.configuration.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.location.ConfiguredLocation;
import ink.ziip.championshipscore.configuration.location.LocationConfig;
import ink.ziip.championshipscore.logging.LogText;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

@RequiredArgsConstructor
public abstract class BaseConfigurationFile {
    @NotNull protected final ChampionshipsCore plugin;
    @Getter protected YamlConfiguration configuration;
    protected Path configurationPath;
    // True while loading the bundled resource template (see loadDefaultOptions); null placeholders
    // in
    // the template are expected, so "missing field" warnings are suppressed until the real file
    // loads.
    protected boolean loadingDefaults = false;

    private YamlConfiguration bundledDefaults;
    private final Map<Field, Object> initialFieldValues = new LinkedHashMap<>();

    /** Annotated fields of this configuration, including inherited map options. */
    protected final List<Field> getConfigFields() {
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = getClass();
                type != BaseConfigurationFile.class;
                type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getAnnotation(ConfigOption.class) == null) continue;
                field.setAccessible(true);
                fields.add(field);
            }
        }
        return fields;
    }

    /** Loads the bundled template and the configuration stored below the plugin folder. */
    public void initializeConfiguration(Path pluginFolder) {
        if (!initializeConfigurationChecked(pluginFolder))
            throw new IllegalStateException(
                    "Configuration initialization failed: " + getFileName());
    }

    /** Same initialization contract with an explicit success result for atomic runtime reloads. */
    public boolean initializeConfigurationChecked(Path pluginFolder) {
        try {
            loadDefaultOptions();
            configurationPath = saveDefaultConfigurationFile(pluginFolder);
            configuration = new YamlConfiguration();
            configuration.options().indent(2);
            configuration.load(configurationPath.toFile());
            validateVersion(
                    configuration.getInt("dont-edit-this.version", -1),
                    getLatestVersion(),
                    getFileName());
            loadFileOptions();
            return true;
        } catch (Exception exception) {
            plugin.getLogger()
                    .log(
                            Level.SEVERE,
                            LogText.formatModuleLog(
                                    "Config", "加载", "配置文件=" + getFileName() + " 加载失败"),
                            exception);
            return false;
        }
    }

    /** Captures the effective runtime document so a multi-file reload can roll back atomically. */
    public String captureRuntimeConfiguration() {
        return configuration == null ? null : configuration.saveToString();
    }

    /** Persists the current parsed document, including custom sections not backed by fields. */
    public void saveRuntimeConfiguration() throws IOException {
        if (configuration == null || configurationPath == null) {
            throw new IOException("Configuration is not initialized: " + getFileName());
        }
        configuration.save(configurationPath.toFile());
    }

    /** Restores both the parsed document and any static/custom runtime fields backed by it. */
    public void restoreRuntimeConfiguration(String snapshot) {
        if (snapshot == null) return;
        try {
            YamlConfiguration restored = new YamlConfiguration();
            restored.options().indent(2);
            restored.loadFromString(snapshot);
            configuration = restored;
            loadFileOptions();
        } catch (InvalidConfigurationException exception) {
            throw new IllegalStateException(
                    "Unable to restore runtime configuration " + getFileName(), exception);
        }
    }

    /**
     * Check if configuration file exists
     *
     * @return true if exists
     */
    public boolean exists() {
        return configuration != null;
    }

    /**
     * Save default configuration file to path folder, if not exists, and return the path
     *
     * @param path the file path
     * @return the path of the current configuration file
     */
    public Path saveDefaultConfigurationFile(@NotNull Path path) {
        Path target = path.resolve(getFileName());
        try {
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) {
                try (InputStream stream = plugin.getResource(getResourceName())) {
                    if (stream == null)
                        throw new IOException("Missing bundled resource: " + getResourceName());
                    Files.copy(stream, target);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot create configuration: " + getFileName(), exception);
        }
        return target;
    }

    /** Save options */
    public void saveOptions() {
        try {
            saveCustomOptions();

            for (Field field : getConfigFields()) {
                ConfigOption option = field.getAnnotation(ConfigOption.class);
                if (field.getType() == Location.class) {
                    LocationConfig.write(configuration, option.path(), (Location) field.get(this));
                } else {
                    configuration.set(option.path(), field.get(this));
                }
            }

            configuration.save(configurationPath.toFile());
        } catch (Exception exception) {
            plugin.getLogger()
                    .log(
                            Level.SEVERE,
                            LogText.formatModuleLog(
                                    "Config", "保存", "配置文件=" + getFileName() + " 保存选项失败"),
                            exception);
        }
    }

    /** Save custom options for sub classes */
    protected void saveCustomOptions() {}

    /** Load default config options from the resource folder */
    public void loadDefaultOptions() {
        try (InputStream stream = plugin.getResource(getResourceName())) {
            if (stream == null)
                throw new IOException("Missing bundled resource: " + getResourceName());
            var defaults = new YamlConfiguration();
            defaults.loadFromString(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            bundledDefaults = defaults;
            loadingDefaults = true;
            try {
                loadFromConfiguration(defaults);
                loadCustomDefaultOptions();
            } finally {
                loadingDefaults = false;
            }
        } catch (InvalidConfigurationException | IOException exception) {
            throw new IllegalStateException(
                    "Cannot load bundled configuration: " + getResourceName(), exception);
        }
    }

    /** Load custom default options */
    protected void loadCustomDefaultOptions() {}

    /** Load config options from the already initialized configuration file */
    public void loadFileOptions() {
        loadFromConfiguration(configuration);

        loadCustomFileOptions();
    }

    /** Load custom config options */
    protected void loadCustomFileOptions() {}

    public void loadFromConfiguration(@NotNull YamlConfiguration document) {
        var values = new LinkedHashMap<Field, Object>();
        for (Field field : getConfigFields()) {
            ConfigOption option = field.getAnnotation(ConfigOption.class);
            try {
                if (!initialFieldValues.containsKey(field))
                    initialFieldValues.put(field, field.get(this));
                YamlConfiguration source = document;
                if (!document.isSet(option.path()) && !loadingDefaults && bundledDefaults != null) {
                    source = bundledDefaults;
                }
                boolean persisted = source.isSet(option.path());
                Object value =
                        persisted
                                ? ConfigurationValueReader.read(source, option.path(), field)
                                : initialFieldValues.get(field);
                if (persisted) value = coerceLocationSection(value, field);
                if (value instanceof String text) value = LegacyText.translateColorCodes(text);
                if (value != null && !ConfigurationValueReader.accepts(field.getType(), value)) {
                    throw new IllegalArgumentException(
                            "Expected " + field.getType().getSimpleName());
                }
                if (value == null && field.getType().isPrimitive()) {
                    throw new IllegalArgumentException(
                            "Primitive configuration value cannot be null");
                }
                if (value == null && !loadingDefaults && !option.nullable()) {
                    plugin.getLogger()
                            .log(
                                    Level.SEVERE,
                                    LogText.formatModuleLog(
                                            "Config",
                                            "加载",
                                            "配置文件=" + getFileName() + " 缺少路径=" + option.path()));
                }
                values.put(field, value);
            } catch (ReflectiveOperationException | IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Cannot load " + getFileName() + " at " + option.path(), exception);
            }
        }
        // Convert and check the whole document before publishing any annotated value.
        for (var entry : values.entrySet()) {
            try {
                entry.getKey().set(this, entry.getValue());
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(
                        "Cannot assign configuration: " + entry.getKey().getName(), exception);
            }
        }
    }

    /**
     * Locations may be stored on disk as a raw section (world/world_key + x/y/z/yaw/pitch, without
     * the '==' marker Bukkit uses to auto-deserialize). Rebuild such a section into a Location; the
     * world may not be loaded yet at config-load time, so it is left null rather than throwing.
     * Shared by {@link #loadFromConfiguration} and {@link
     * ink.ziip.championshipscore.api.game.config.BaseGameConfig#loadFromConfiguration}.
     */
    protected Object coerceLocationSection(Object value, Field field) {
        return coerceLocationSection(value, field, true);
    }

    /**
     * Converts a raw location section while allowing map configs to defer world resolution until
     * their template world has been loaded. Global configuration still validates unresolved worlds
     * immediately.
     */
    protected Object coerceLocationSection(Object value, Field field, boolean reportMissingWorld) {
        if (field.getType() == Location.class && value != null) {
            ConfiguredLocation coordinates = ConfiguredLocation.read(value);
            Location location =
                    coordinates.resolve(
                            identifier ->
                                    LocationConfig.resolveWorld(plugin.getServer(), identifier));
            if (location.getWorld() == null
                    && coordinates.world() != null
                    && !loadingDefaults
                    && reportMissingWorld) {
                ConfigOption option = field.getAnnotation(ConfigOption.class);
                String label = option == null ? field.getName() : option.path();
                List<String> loadedWorlds =
                        plugin.getServer().getWorlds().stream()
                                .map(world -> world.getKey().toString())
                                .toList();
                plugin.getLogger()
                        .log(
                                Level.SEVERE,
                                LogText.formatModuleLog(
                                        "Config",
                                        "世界",
                                        "配置文件="
                                                + getFileName()
                                                + " 路径="
                                                + label
                                                + " 世界="
                                                + coordinates.world()
                                                + " 不存在；已加载世界="
                                                + loadedWorlds
                                                + "，相关传送将失败"));
            }
            return location;
        }
        return value;
    }

    public static void validateVersion(int actual, int expected, String fileName) {
        if (actual != expected)
            throw new IllegalArgumentException(
                    "配置文件 " + fileName + " 版本 " + actual + " 与当前版本 " + expected + " 不一致；请直接更新配置文件");
    }

    /**
     * Get the configuration file name
     *
     * @return the configuration file name
     */
    public abstract String getFileName();

    /**
     * Get the configuration file path
     *
     * @return the configuration resource name
     */
    public abstract String getResourceName();

    /**
     * Get latest version of the configuration
     *
     * @return the latest configuration version
     */
    public abstract int getLatestVersion();
}

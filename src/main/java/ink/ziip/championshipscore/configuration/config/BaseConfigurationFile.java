package ink.ziip.championshipscore.configuration.config;

import com.google.common.io.ByteStreams;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Modified under <a href="https://github.com/AlessioDP/ADP-Core">ADP-Core</a>
 * @author AlessioDP
 */
@RequiredArgsConstructor
public abstract class BaseConfigurationFile {
    @NotNull
    protected final ChampionshipsCore plugin;
    @Getter
    private boolean outdated = false;
    @Getter
    protected YamlConfiguration configuration;
    protected Path configurationPath;
    /** Base directory {@link #getFileName()} resolves against; remembered so version migration can
     *  re-initialize from the same root (game configs carry a folder prefix in their file name). */
    protected Path configurationBasePath;
    // True while loading the bundled resource template (see loadDefaultOptions); null placeholders in
    // the template are expected, so "missing field" warnings are suppressed until the real file loads.
    protected boolean loadingDefaults = false;

    /**
     * Initialize the configuration into the path of plugin folder
     *
     * @param pluginFolder the plugin folder path
     */
    public void initializeConfiguration(Path pluginFolder) {
        this.configurationBasePath = pluginFolder;
        loadDefaultOptions();

        configurationPath = saveDefaultConfigurationFile(pluginFolder);
        configuration = new YamlConfiguration();
        try {
            configuration.options().indent(2);
            configuration.load(configurationPath.toFile());

            loadFileOptions();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Config", "加载",
                    "配置文件=" + getFileName() + " 加载失败"), exception);
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
     * @return the path of the old or new configuration file
     */
    public Path saveDefaultConfigurationFile(@NotNull Path path) {
        Path ret = path.resolve(getFileName());
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            if (!Files.exists(ret)) {
                InputStream inputStream = plugin.getResource(getResourceName());
                if (inputStream != null) {
                    byte[] data = ByteStreams.toByteArray(inputStream);

                    Files.write(ret, data);
                } else {
                    plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Config", "写出",
                            "缺少内置资源=" + getResourceName()));
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Config", "写出",
                    "配置文件=" + getFileName() + " 写出失败"), exception);
        }
        return ret;
    }

    /**
     * Save options
     */
    public void saveOptions() {
        try {
            saveCustomOptions();

            Field[] fields = getClass().getFields();
            for (Field field : fields) {
                ConfigOption co = field.getAnnotation(ConfigOption.class);
                if (co != null) {
                    configuration.set(co.path(), field.get(null));
                }
            }

            configuration.save(configurationPath.toFile());
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Config", "保存",
                    "配置文件=" + getFileName() + " 保存选项失败"), exception);
        }
    }

    /**
     * Save custom options for sub classes
     */
    protected void saveCustomOptions() {
    }

    /**
     * Load default config options from the resource folder
     */
    public void loadDefaultOptions() {
        try {
            YamlConfiguration yamlConfiguration = new YamlConfiguration();
            InputStream inputStream = plugin.getResource(getResourceName());
            if (inputStream != null) {
                yamlConfiguration.loadFromString(new String(inputStream.readAllBytes()));
                // The bundled resource is a template whose placeholders (e.g. area spawn points) are
                // intentionally empty; don't warn about them - real values come from the on-disk file.
                loadingDefaults = true;
                try {
                    loadFromConfiguration(yamlConfiguration);

                    loadCustomDefaultOptions();
                } finally {
                    loadingDefaults = false;
                }
            }
        } catch (InvalidConfigurationException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Load custom default options
     */
    protected void loadCustomDefaultOptions() {
    }

    /**
     * Load config options from the already initialized configuration file
     */
    public void loadFileOptions() {
        loadFromConfiguration(configuration);

        loadCustomFileOptions();
    }

    /**
     * Load custom config options
     */
    protected void loadCustomFileOptions() {
    }

    public void loadFromConfiguration(@NotNull YamlConfiguration yamlConfiguration) {
        Field[] fields = getClass().getFields();
        for (Field field : fields) {
            ConfigOption configOption = field.getAnnotation(ConfigOption.class);
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
                        }
                    }

                    // Otherwise get it normally
                    if (value == null) value = yamlConfiguration.get(configOption.path());

                    // Locations may be stored as a raw section (world/world_key + x/y/z/yaw/pitch,
                    // without the '==' marker); rebuild them so field.set doesn't throw.
                    value = coerceLocationSection(value, field);

                    if (value == null && !loadingDefaults && !configOption.nullable())
                        plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Config", "校验",
                                "配置文件=" + getFileName() + " 路径=" + configOption.path() + " 值为空"));

                    if (value != null) {
                        if (value instanceof String)
                            value = Utils.translateColorCodes((String) value);
                        field.set(null, value);
                    } else if (!configOption.nullable() && !loadingDefaults) {
                        plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Config", "加载",
                                "配置文件=" + getFileName() + " 缺少路径=" + configOption.path()));
                    }
                } catch (Exception exception) {
                    plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Config", "加载",
                            "配置文件=" + getFileName() + " 路径=" + configOption.path() + " 加载失败"), exception);
                }
            }
        }
    }

    /**
     * Locations may be stored on disk as a raw section (world/world_key + x/y/z/yaw/pitch, without the
     * '==' marker Bukkit uses to auto-deserialize). Rebuild such a section into a Location; the world
     * may not be loaded yet at config-load time, so it is left null rather than throwing. Shared by
     * {@link #loadFromConfiguration} and {@link ink.ziip.championshipscore.api.game.config.BaseGameConfig#loadFromConfiguration}.
     */
    protected Object coerceLocationSection(Object value, Field field) {
        if (value instanceof ConfigurationSection && field.getType() == Location.class) {
            ConfigurationSection section = (ConfigurationSection) value;
            World world = null;
            String worldIdentifier = null;
            if (section.contains("world_key")) {
                worldIdentifier = section.getString("world_key");
                world = plugin.getServer().getWorld(NamespacedKey.fromString(worldIdentifier));
            } else if (section.contains("world")) {
                worldIdentifier = section.getString("world");
                world = plugin.getServer().getWorld(worldIdentifier);
            }
            if (world == null && worldIdentifier != null && !loadingDefaults) {
                // A world was configured but couldn't be resolved. Usually a stale world_key (e.g.
                // minecraft:world after the 1.21.5+ migration to minecraft:overworld) or a typo. Warn
                // loudly at load time instead of letting it surface later as a cryptic
                // "Target world cannot be null" on every join/death teleport to this location.
                String label = field.getName();
                ConfigOption co = field.getAnnotation(ConfigOption.class);
                if (co != null && !co.path().isEmpty()) label = co.path();
                List<String> loadedWorlds = plugin.getServer().getWorlds().stream()
                        .map(w -> w.getKey().toString())
                        .collect(Collectors.toList());
                plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Config", "世界",
                        "配置文件=" + getFileName() + " 路径=" + label + " 世界=" + worldIdentifier
                                + " 不存在；已加载世界=" + loadedWorlds + "，相关传送将失败"));
            }
            value = new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                    (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
        }
        return value;
    }

    /**
     * Check the version of the configuration and upgrade it if outdated
     *
     * @param autoUpgrade true to auto upgrade configuration file if outdated
     */
    public void checkVersion(boolean autoUpgrade) {
        outdated = configuration.getInt("dont-edit-this.version", -1) < getLatestVersion();
        if (outdated && autoUpgrade) {
            plugin.getLogger().info(Utils.formatModuleLog("Config", "迁移",
                    String.format("配置文件=%s 版本=%d -> %d", getFileName(),
                            configuration.getInt("dont-edit-this.version", -1), getLatestVersion())));

            Path outdatedPath = configurationPath.getParent();
            String simpleFileName = configurationPath.getFileName().toString();
            String outdatedFileName = simpleFileName + ".outdated";
            int counter = 1;
            while (outdatedPath.resolve(outdatedFileName).toFile().exists()) {
                outdatedFileName = simpleFileName + ".outdated" + counter;
                counter++;
            }
            if (configurationPath.toFile().renameTo(outdatedPath.resolve(outdatedFileName).toFile())) {
                // Re-create the fresh template from the same base root used originally: game config
                // file names include their folder prefix, so the parent directory alone is wrong.
                initializeConfiguration(configurationBasePath != null ? configurationBasePath : outdatedPath);

                try {
                    YamlConfiguration outdatedConfiguration = YamlConfiguration.loadConfiguration(outdatedPath.resolve(outdatedFileName).toFile());

                    loadFromOutdatedConfiguration(outdatedConfiguration);

                    outdated = false;
                    plugin.getLogger().info(Utils.formatModuleLog("Config", "迁移",
                            "配置文件=" + getFileName() + " 迁移完成"));
                } catch (Exception exception) {
                    plugin.getLogger().log(Level.WARNING, Utils.formatModuleLog("Config", "迁移",
                            "配置文件=" + getFileName() + " 旧版本读取失败"), exception);
                }
            } else
                plugin.getLogger().log(Level.WARNING, Utils.formatModuleLog("Config", "迁移",
                        "配置文件=" + getFileName() + " 无法重命名为 " + outdatedFileName));
        }
    }

    public void loadFromOutdatedConfiguration(@NotNull YamlConfiguration yamlConfiguration) {
        try {
            Field[] fields = getClass().getFields();
            for (Field field : fields) {
                ConfigOption co = field.getAnnotation(ConfigOption.class);
                if (co != null && yamlConfiguration.get(co.path()) != null) {
                    configuration.set(co.path(), yamlConfiguration.get(co.path()));
                }
            }

            configuration.save(configurationPath.toFile());

            // Reload options from the file
            loadFileOptions();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Config", "保存",
                    "配置文件=" + getFileName() + " 保存失败"), exception);
        }
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

package ink.ziip.championshipscore.configuration.config;

import com.google.common.io.ByteStreams;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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

/*
 * Modified under https://github.com/AlessioDP/ADP-Core
 * Author: AlessioDP
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

    /**
     * Initialize the configuration into the path of plugin folder
     *
     * @param pluginFolder the plugin folder path
     */
    public void initializeConfiguration(Path pluginFolder) {
        loadDefaultOptions();

        configurationPath = saveDefaultConfigurationFile(pluginFolder);
        configuration = new YamlConfiguration();
        try {
            configuration.options().indent(2);
            configuration.load(configurationPath.toFile());

            loadFileOptions();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load configuration file.");
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
                    plugin.getLogger().log(Level.SEVERE, "Failed to save configuration file. ", getResourceName());
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save configuration file. ", exception);
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
            plugin.getLogger().log(Level.SEVERE, "Failed to save configuration option. ", exception);
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
                loadFromConfiguration(yamlConfiguration);

                loadCustomDefaultOptions();
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

                    if (value != null) {
                        if (value instanceof String)
                            value = Utils.translateColorCodes((String) value);
                        field.set(null, value);
                    } else if (!configOption.nullable()) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to find configuration file. " + configOption.path() + "/" + getFileName());
                    }
                } catch (Exception exception) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to load configuration file. ", exception);
                }
            }
        }
    }

    /**
     * Check the version of the configuration and upgrade it if outdated
     *
     * @param autoUpgrade true to auto upgrade configuration file if outdated
     */
    public void checkVersion(boolean autoUpgrade) {
        outdated = configuration.getInt("dont-edit-this.version", -1) < getLatestVersion();
        if (outdated && autoUpgrade) {
            plugin.getLogger().info(String.format("Upgrading the file %s from %d to %d", getFileName(), configuration.getInt("dont-edit-this.version", -1), getLatestVersion()));

            Path outdatedPath = configurationPath.getParent();
            String outdatedFileName = getFileName() + ".outdated";
            int counter = 1;
            while (outdatedPath.resolve(outdatedFileName).toFile().exists()) {
                outdatedFileName = getFileName() + ".outdated" + counter;
                counter++;
            }
            if (outdatedPath.resolve(getFileName()).toFile().renameTo(outdatedPath.resolve(outdatedFileName).toFile())) {
                initializeConfiguration(outdatedPath);

                try {
                    YamlConfiguration outdatedConfiguration = YamlConfiguration.loadConfiguration(outdatedPath.resolve(outdatedFileName).toFile());

                    loadFromOutdatedConfiguration(outdatedConfiguration);

                    outdated = false;
                    plugin.getLogger().info(String.format("Upgrade of file %s completed ", getFileName()));
                } catch (Exception exception) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load configuration ", exception);
                }
            } else
                plugin.getLogger().log(Level.WARNING, String.format("Failed to rename the old configuration '%s' to '%s'", getFileName(), outdatedFileName));
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
            plugin.getLogger().log(Level.SEVERE, "Failed to save configuration file. ", exception);
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

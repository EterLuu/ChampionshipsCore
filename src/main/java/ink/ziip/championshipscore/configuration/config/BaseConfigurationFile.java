package ink.ziip.championshipscore.configuration.config;

import com.google.common.io.ByteStreams;
import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

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
    private boolean loadingDefaultOptions = false;

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
            loadConfigurationFile(configuration);

            loadFileOptions();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load configuration file. " + getFileName(), exception);
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
                    configuration.set(co.path(), serializeConfigValue(field.get(null)));
                }
            }

            normalizeLocationValues(configuration);
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
                yamlConfiguration.loadFromString(normalizeLocationYaml(new String(inputStream.readAllBytes())));
                loadingDefaultOptions = true;
                try {
                    loadFromConfiguration(yamlConfiguration);
                } finally {
                    loadingDefaultOptions = false;
                }

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
                    if (value == null) value = getConfigValue(yamlConfiguration, configOption.path(), field.getType());

                    if (value == null)
                        plugin.getLogger().log(Level.SEVERE, "Warning, null value found: " + configOption.path() + "/" + getFileName());

                    if (value != null) {
                        if (value instanceof String)
                            value = Utils.translateColorCodes((String) value);
                        field.set(null, value);
                    } else if (!configOption.nullable() && !isLoadingDefaultOptions()) {
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
                    configuration.set(co.path(), serializeConfigValue(getConfigValue(yamlConfiguration, co.path(), field.getType())));
                }
            }

            normalizeLocationValues(configuration);
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

    protected boolean isLoadingDefaultOptions() {
        return loadingDefaultOptions;
    }

    private void loadConfigurationFile(@NotNull YamlConfiguration target) throws IOException, InvalidConfigurationException {
        if (repairLocationSerialization()) {
            plugin.getLogger().warning("Repaired Location serialization in " + getFileName());
        }
        target.load(configurationPath.toFile());
    }

    private boolean repairLocationSerialization() throws IOException {
        String original = Files.readString(configurationPath, StandardCharsets.UTF_8);
        String repaired = normalizeLocationYaml(original);
        if (original.equals(repaired)) {
            return false;
        }
        Files.writeString(configurationPath, repaired, StandardCharsets.UTF_8);
        return true;
    }

    private String normalizeLocationYaml(String content) {
        return flattenLocationWorldSections(stripLocationSerializationTags(content));
    }

    private String stripLocationSerializationTags(String content) {
        String[] lines = content.split("\\R", -1);
        StringBuilder builder = new StringBuilder(content.length());
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (!trimmed.equals("==: org.bukkit.Location") && !trimmed.equals("==: Location")) {
                builder.append(line);
                if (i < lines.length - 1) {
                    builder.append('\n');
                }
            }
        }
        return builder.toString();
    }

    private String flattenLocationWorldSections(String content) {
        String[] lines = content.split("\\R", -1);
        StringBuilder builder = new StringBuilder(content.length());
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().equals("world:")) {
                int indent = countIndent(line);
                int end = i + 1;
                String worldName = null;
                while (end < lines.length) {
                    String child = lines[end];
                    if (!child.isBlank() && countIndent(child) <= indent) {
                        break;
                    }
                    String trimmed = child.trim();
                    if (trimmed.startsWith("name:")) {
                        worldName = trimmed.substring("name:".length()).trim();
                    }
                    end++;
                }
                if (worldName != null && !worldName.isBlank()) {
                    builder.append(line, 0, line.indexOf("world:")).append("world: ").append(worldName);
                    if (end < lines.length || !lines[end - 1].isEmpty()) {
                        builder.append('\n');
                    }
                    i = end - 1;
                    continue;
                }
            }
            builder.append(line);
            if (i < lines.length - 1) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private int countIndent(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    protected Object getConfigValue(@NotNull YamlConfiguration yamlConfiguration, @NotNull String path, @NotNull Class<?> targetType) {
        if (targetType == Location.class) {
            Location location = getLocationValue(yamlConfiguration, path);
            if (location != null) {
                return location;
            }
        }
        if (targetType == Vector.class) {
            Vector vector = getVectorValue(yamlConfiguration, path);
            if (vector != null) {
                return vector;
            }
        }
        if (ConfigurationSection.class.isAssignableFrom(targetType)) {
            ConfigurationSection section = yamlConfiguration.getConfigurationSection(path);
            if (section != null) {
                return section;
            }
        }
        return yamlConfiguration.get(path);
    }

    protected Object serializeConfigValue(Object value) {
        if (value instanceof Location location) {
            Map<String, Object> serialized = new LinkedHashMap<>();
            serialized.put("world", location.getWorld() != null ? location.getWorld().getName() : null);
            serialized.put("x", location.getX());
            serialized.put("y", location.getY());
            serialized.put("z", location.getZ());
            serialized.put("pitch", location.getPitch());
            serialized.put("yaw", location.getYaw());
            return serialized;
        }
        return value;
    }

    protected void normalizeLocationValues(@NotNull ConfigurationSection section) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof Location) {
                section.set(key, serializeConfigValue(value));
                continue;
            }
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null) {
                normalizeLocationValues(child);
            }
        }
    }

    protected Location getLocationValue(@NotNull YamlConfiguration yamlConfiguration, @NotNull String path) {
        Object value = yamlConfiguration.get(path);
        if (value instanceof Location location) {
            return location;
        }

        ConfigurationSection section = yamlConfiguration.getConfigurationSection(path);
        if (section == null) {
            return null;
        }

        World world = getWorld(section, yamlConfiguration, path);
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    protected Vector getVectorValue(@NotNull YamlConfiguration yamlConfiguration, @NotNull String path) {
        Object value = yamlConfiguration.get(path);
        if (value instanceof Vector vector) {
            return vector;
        }

        ConfigurationSection section = yamlConfiguration.getConfigurationSection(path);
        if (section == null) {
            return null;
        }
        return new Vector(
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z")
        );
    }

    private World getWorld(@NotNull ConfigurationSection section, @NotNull YamlConfiguration yamlConfiguration, @NotNull String path) {
        Object worldValue = section.get("world");
        if (worldValue instanceof World world) {
            return world;
        }
        String worldName = extractWorldName(worldValue);
        if (worldName == null) {
            worldName = yamlConfiguration.getString(path + ".world.name");
        }
        if (worldName != null && !worldName.isBlank()) {
            return Bukkit.getWorld(worldName);
        }

        String uuid = extractWorldUuid(worldValue);
        if (uuid == null) {
            uuid = yamlConfiguration.getString(path + ".world.uid", yamlConfiguration.getString(path + ".world.uuid"));
        }
        if (uuid != null && !uuid.isBlank()) {
            try {
                return Bukkit.getWorld(UUID.fromString(uuid));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private String extractWorldName(Object worldValue) {
        if (worldValue instanceof String worldName) {
            return worldName;
        }
        if (worldValue instanceof ConfigurationSection section) {
            return section.getString("name");
        }
        if (worldValue instanceof Map<?, ?> map) {
            Object name = map.get("name");
            return name != null ? name.toString() : null;
        }
        return null;
    }

    private String extractWorldUuid(Object worldValue) {
        if (worldValue instanceof ConfigurationSection section) {
            String uuid = section.getString("uid");
            return uuid != null ? uuid : section.getString("uuid");
        }
        if (worldValue instanceof Map<?, ?> map) {
            Object uuid = map.get("uid");
            if (uuid == null) {
                uuid = map.get("uuid");
            }
            return uuid != null ? uuid.toString() : null;
        }
        return null;
    }
}

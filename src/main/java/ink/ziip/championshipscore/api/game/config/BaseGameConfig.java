package ink.ziip.championshipscore.api.game.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

@Getter
public abstract class BaseGameConfig extends BaseConfigurationFile {
    protected final String configName;

    public BaseGameConfig(@NotNull ChampionshipsCore plugin, String configName) {
        super(plugin);
        this.configName = configName;
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
                    configuration.set(co.path(), field.get(this));
                }
            }

            configuration.save(configurationPath.toFile());
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("GameConfig", "保存",
                    "配置文件=" + getFileName() + " 保存选项失败"), exception);
        }
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
            for (Field field : getConfigFields()) {
                field.setAccessible(true);
                ConfigOption co = field.getDeclaredAnnotation(ConfigOption.class);
                if (co != null && yamlConfiguration.get(co.path()) != null) {
                    configuration.set(co.path(), yamlConfiguration.get(co.path()));
                }
            }

            configuration.save(configurationPath.toFile());

            // Reload options from the file
            loadFileOptions();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("GameConfig", "保存",
                    "配置文件=" + getFileName() + " 保存失败"), exception);
        }
    }

    /**
     * Spawn point of the optional rule-introduction phase: players gather here for the 45s rules
     * broadcast, then move to the normal preparation spawn. Leave empty to skip the introduction.
     */
    @ConfigOption(path = "introduction-spawn-point", nullable = true)
    protected Location introductionSpawnPoint;

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
}

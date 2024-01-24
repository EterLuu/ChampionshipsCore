package ink.ziip.championshipscore.api.game.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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

            Field[] fields = getClass().getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                ConfigOption co = field.getDeclaredAnnotation(ConfigOption.class);
                if (co != null) {
                    configuration.set(co.path(), field.get(this));
                }
            }

            configuration.save(configurationPath.toFile());
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save configuration option. ", exception);
        }
    }

    @Override
    public void loadFromConfiguration(@NotNull YamlConfiguration yamlConfiguration) {
        Field[] fields = getClass().getDeclaredFields();
        for (Field field : fields) {
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
                        }
                    }

                    // Otherwise get it normally
                    if (value == null) value = yamlConfiguration.get(configOption.path());

                    if (value != null) {
                        if (value instanceof String)
                            value = Utils.translateColorCodes((String) value);
                        field.set(this, value);
                    } else if (!configOption.nullable()) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to find configuration file. " + configOption.path() + "/" + getFileName());
                    }
                } catch (Exception exception) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to load configuration file. ", exception);
                }
            }
        }
    }

    @Override
    public void loadFromOutdatedConfiguration(@NotNull YamlConfiguration yamlConfiguration) {
        try {
            Field[] fields = getClass().getDeclaredFields();
            for (Field field : fields) {
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
            plugin.getLogger().log(Level.SEVERE, "Failed to save configuration file. ", exception);
        }
    }

    public abstract String getFolderName();
}

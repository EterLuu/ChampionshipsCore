package ink.ziip.championshipscore.configuration.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/** Converts Bukkit YAML values to the declared type of a configuration field. */
public final class ConfigurationValueReader {
    private ConfigurationValueReader() {
    }

    public static Object read(YamlConfiguration configuration, String path, Field field) {
        Object value = null;
        if (field.getType() == List.class && field.getGenericType() instanceof ParameterizedType listType) {
            Type element = listType.getActualTypeArguments()[0];
            if (element == Integer.class) value = configuration.getIntegerList(path);
            else if (element == Double.class) value = configuration.getDoubleList(path);
            else if (element == Float.class) value = configuration.getFloatList(path);
            else if (element == Short.class) value = configuration.getShortList(path);
            else if (element == String.class) value = configuration.getStringList(path);
            else if (element instanceof ParameterizedType nested && nested.getRawType() == List.class)
                value = configuration.getList(path);
        }
        if (value == null) value = configuration.get(path);
        if (!(value instanceof Number number)) return value;
        Class<?> target = field.getType();
        if (target == byte.class || target == Byte.class) return number.byteValue();
        if (target == short.class || target == Short.class) return number.shortValue();
        if (target == int.class || target == Integer.class) return number.intValue();
        if (target == long.class || target == Long.class) return number.longValue();
        if (target == float.class || target == Float.class) return number.floatValue();
        if (target == double.class || target == Double.class) return number.doubleValue();
        return value;
    }
}

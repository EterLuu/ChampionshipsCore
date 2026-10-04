package ink.ziip.championshipscore.configuration.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Converts Bukkit YAML values to the declared type of a configuration field. */
public final class ConfigurationValueReader {
    private ConfigurationValueReader() {}

    static boolean accepts(Class<?> target, Object value) {
        if (!target.isPrimitive()) return target.isInstance(value);
        return (target == boolean.class && value instanceof Boolean)
                || (target == byte.class && value instanceof Byte)
                || (target == short.class && value instanceof Short)
                || (target == int.class && value instanceof Integer)
                || (target == long.class && value instanceof Long)
                || (target == float.class && value instanceof Float)
                || (target == double.class && value instanceof Double)
                || (target == char.class && value instanceof Character);
    }

    public static Object read(YamlConfiguration configuration, String path, Field field) {
        return convert(configuration.get(path), field.getGenericType());
    }

    private static Object convert(Object value, Type declared) {
        if (value == null) return null;
        if (declared instanceof ParameterizedType listType && listType.getRawType() == List.class) {
            if (!(value instanceof List<?> list))
                throw new IllegalArgumentException("Expected a list");
            Type element = listType.getActualTypeArguments()[0];
            List<Object> result = new ArrayList<>(list.size());
            for (Object entry : list) result.add(convert(entry, element));
            return result;
        }
        if (!(declared instanceof Class<?> target)) return value;
        if (value instanceof Number number) {
            try {
                if (target == byte.class || target == Byte.class)
                    return decimal(number).byteValueExact();
                if (target == short.class || target == Short.class)
                    return decimal(number).shortValueExact();
                if (target == int.class || target == Integer.class)
                    return decimal(number).intValueExact();
                if (target == long.class || target == Long.class)
                    return decimal(number).longValueExact();
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Expected an integer in the range of " + target.getSimpleName(), exception);
            }
            if (target == float.class || target == Float.class) {
                float result = number.floatValue();
                if (!Float.isFinite(result))
                    throw new IllegalArgumentException("Expected a finite float");
                return result;
            }
            if (target == double.class || target == Double.class) {
                double result = number.doubleValue();
                if (!Double.isFinite(result))
                    throw new IllegalArgumentException("Expected a finite double");
                return result;
            }
        }
        // Bukkit sections still need the caller's explicit Location conversion boundary.
        if (target == org.bukkit.Location.class) return value;
        if (!accepts(target, value))
            throw new IllegalArgumentException("Expected " + target.getSimpleName());
        return value;
    }

    private static BigDecimal decimal(Number value) {
        return new BigDecimal(value.toString());
    }
}

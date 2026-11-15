package ink.ziip.championshipscore.configuration;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One schema traversal for runtime loading, persistence, validation and configuration tooling. */
public final class ConfigurationFields {
    private ConfigurationFields() {}

    public static List<Field> of(Class<?> configuration) {
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Class<?> type = configuration; type != null; type = type.getSuperclass()) {
            add(fields, type, false);
            ConfigSections sections = type.getDeclaredAnnotation(ConfigSections.class);
            if (sections == null) continue;
            for (Class<?> section : sections.value()) add(fields, section, true);
        }
        return List.copyOf(fields.values());
    }

    private static void add(Map<String, Field> fields, Class<?> owner, boolean section) {
        for (Field field : owner.getDeclaredFields()) {
            ConfigOption option = field.getAnnotation(ConfigOption.class);
            if (option == null) continue;
            if (Modifier.isFinal(field.getModifiers())
                    || (section && !Modifier.isStatic(field.getModifiers())))
                throw new IllegalArgumentException(
                        "Configuration section field must be mutable and static: " + field);
            Field previous = fields.putIfAbsent(option.path(), field);
            if (previous != null)
                throw new IllegalArgumentException(
                        "Duplicate configuration path "
                                + option.path()
                                + ": "
                                + previous
                                + " and "
                                + field);
            field.setAccessible(true);
        }
    }
}

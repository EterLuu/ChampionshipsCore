package ink.ziip.championshipscore.configuration;

import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/** Restores replaced configuration fields after headless tests, including failed tests. */
public final class ConfigurationStateExtension implements BeforeAllCallback, AfterAllCallback {
    private final Map<Field, Object> original = new LinkedHashMap<>();

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        for (Class<?> type : new Class<?>[]{CCConfig.class, MessageConfig.class, GuiConfig.class, ScheduleMessageConfig.class}) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
                field.setAccessible(true);
                original.put(field, field.get(null));
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        for (var entry : original.entrySet()) entry.getKey().set(null, entry.getValue());
        original.clear();
    }
}

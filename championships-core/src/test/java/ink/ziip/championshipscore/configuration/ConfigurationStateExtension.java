package ink.ziip.championshipscore.configuration;

import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/** Restores replaced configuration fields at both method and class boundaries. */
public final class ConfigurationStateExtension
        implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(ConfigurationStateExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        capture(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        capture(context);
    }

    private void capture(ExtensionContext context) throws Exception {
        Map<Field, Object> original = new LinkedHashMap<>();
        context.getStore(NAMESPACE).put(context.getUniqueId(), original);
        for (Class<?> type :
                new Class<?>[] {
                    CCConfig.class,
                    MessageConfig.class,
                    GuiConfig.class,
                    ScheduleMessageConfig.class
                }) {
            var fields =
                    new java.util.LinkedHashSet<Field>(java.util.List.of(type.getDeclaredFields()));
            fields.addAll(ConfigurationFields.of(type));
            for (Field field : fields) {
                if (!Modifier.isStatic(field.getModifiers())
                        || Modifier.isFinal(field.getModifiers())) continue;
                field.setAccessible(true);
                original.put(field, field.get(null));
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        restore(context);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        restore(context);
    }

    @SuppressWarnings("unchecked")
    private void restore(ExtensionContext context) throws Exception {
        var original =
                (Map<Field, Object>) context.getStore(NAMESPACE).remove(context.getUniqueId());
        if (original == null) return;
        for (var entry : original.entrySet()) entry.getKey().set(null, entry.getValue());
    }
}

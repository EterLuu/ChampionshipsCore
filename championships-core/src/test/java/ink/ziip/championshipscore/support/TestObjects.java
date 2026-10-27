package ink.ziip.championshipscore.support;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/** Builds isolated arena fixtures without starting a Bukkit plugin loader. */
public final class TestObjects {
    private TestObjects() {}

    public static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    public static void set(Object target, String name, Object value)
            throws ReflectiveOperationException {
        field(target.getClass(), name).set(target, value);
    }

    public static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        var unsafe = (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null);
        return type.cast(unsafe.allocateInstance(type));
    }

    public static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}

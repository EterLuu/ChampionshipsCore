package ink.ziip.championshipscore.platform.bukkit.text.support;

import org.bukkit.permissions.Permissible;

import java.lang.reflect.Proxy;
import java.util.Set;

public final class TextTestFixtures {
    private TextTestFixtures() {}

    public static Permissible sender(Set<String> permissions) {
        return (Permissible)
                Proxy.newProxyInstance(
                        Permissible.class.getClassLoader(),
                        new Class<?>[] {Permissible.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("hasPermission"))
                                return permissions.contains(args[0]);
                            throw new AssertionError(method.getName());
                        });
    }
}

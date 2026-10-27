package ink.ziip.championshipscore.support;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Deterministic connection, view and entity-task boundaries; no native inventory rendering. */
public final class MenuTestClient implements AutoCloseable {
    private final Object prior;
    public final Map<UUID, Player> online = new HashMap<>();
    public boolean enabled = true;
    public final Plugin plugin =
            TestObjects.proxy(
                    Plugin.class,
                    (p, m, a) ->
                            switch (m.getName()) {
                                case "isEnabled" -> enabled;
                                case "getName" -> "MenuTest";
                                default -> identity(p, m.getName(), a);
                            });

    public MenuTestClient() throws Exception {
        var field = TestObjects.field(Bukkit.class, "server");
        prior = field.get(null);
        PluginManager manager = TestObjects.proxy(PluginManager.class, (p, m, a) -> null);
        field.set(
                null,
                TestObjects.proxy(
                        Server.class,
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getPlayer" -> online.get(a[0]);
                                    case "getPluginManager" -> manager;
                                    case "isPrimaryThread" -> true;
                                    default -> identity(p, m.getName(), a);
                                }));
    }

    private static Object identity(Object proxy, String method, Object[] args) {
        return switch (method) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "menu-fixture";
            default -> throw new AssertionError(method);
        };
    }

    public final class Client {
        public final UUID id;
        public final Player player;
        public final List<Runnable> queued = new ArrayList<>();
        public boolean permission = true;
        public boolean connected = true;
        public boolean reject;
        public boolean disabledRace;
        public boolean retired;
        public String rename = "value";
        public int cleared;
        public InventoryView view;
        public Consumer<InventoryView> closing = ignored -> {};

        @SuppressWarnings("unchecked")
        public Client(UUID id) {
            this.id = id;
            ScheduledTask task = TestObjects.proxy(ScheduledTask.class, (p, m, a) -> null);
            EntityScheduler scheduler =
                    TestObjects.proxy(
                            EntityScheduler.class,
                            (p, m, a) -> {
                                if (disabledRace)
                                    throw new org.bukkit.plugin.IllegalPluginAccessException(
                                            "disabled");
                                if (reject) return null;
                                if (retired) {
                                    ((Runnable) a[2]).run();
                                    return null;
                                }
                                queued.add(() -> ((Consumer<ScheduledTask>) a[1]).accept(task));
                                return task;
                            });
            player =
                    TestObjects.proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "isOnline" -> connected;
                                        case "hasPermission" -> permission;
                                        case "getScheduler" -> scheduler;
                                        case "getOpenInventory" -> view;
                                        case "closeInventory" -> {
                                            InventoryView old = view;
                                            view = view("CRAFTING");
                                            closing.accept(old);
                                            yield null;
                                        }
                                        default -> identity(p, m.getName(), a);
                                    });
            view = view("ANVIL");
            online.put(id, player);
        }

        public InventoryView view(String type) {
            Inventory inventory =
                    TestObjects.proxy(
                            Inventory.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "clear" -> {
                                            cleared++;
                                            yield null;
                                        }
                                        case "getType" -> null;
                                        case "getSize" -> 3;
                                        case "getHolder" -> null;
                                        default -> identity(p, m.getName(), a);
                                    });
            return (InventoryView)
                    java.lang.reflect.Proxy.newProxyInstance(
                            InventoryView.class.getClassLoader(),
                            new Class<?>[] {InventoryView.class},
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getPlayer" -> player;
                                        case "getTopInventory",
                                                "getBottomInventory",
                                                "getInventory" ->
                                                inventory;
                                        case "getType" -> null;
                                        case "getRenameText" -> rename;
                                        case "convertSlot" -> a[0];
                                        default -> identity(p, m.getName(), a);
                                    });
        }

        public void drain() {
            var tasks = List.copyOf(queued);
            queued.clear();
            tasks.forEach(Runnable::run);
        }
    }

    @Override
    public void close() throws Exception {
        TestObjects.field(Bukkit.class, "server").set(null, prior);
    }
}

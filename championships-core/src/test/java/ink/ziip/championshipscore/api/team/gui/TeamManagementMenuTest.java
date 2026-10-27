package ink.ziip.championshipscore.api.team.gui;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.gui.AnvilInputMenu;
import ink.ziip.championshipscore.support.MenuTestClient;
import ink.ziip.championshipscore.support.TestObjects;

import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

class TeamManagementMenuTest {
    private static ChampionshipsCore core(MenuTestClient fixture) throws Exception {
        var core = TestObjects.allocate(ChampionshipsCore.class);
        TestObjects.set(core, "isEnabled", true);
        TestObjects.set(core, "anvilInputMenu", new AnvilInputMenu(fixture.plugin));
        return core;
    }

    @SuppressWarnings("unchecked")
    private static BiConsumer<String, Throwable> request(
            TeamManagementMenu menu,
            MenuTestClient.Client client,
            BooleanSupplier context,
            List<String> received)
            throws Exception {
        var method =
                TeamManagementMenu.class.getDeclaredMethod(
                        "guarded",
                        org.bukkit.entity.Player.class,
                        BooleanSupplier.class,
                        Object.class,
                        Consumer.class);
        method.setAccessible(true);
        return (BiConsumer<String, Throwable>)
                method.invoke(
                        menu, client.player, context, "failed", (Consumer<String>) received::add);
    }

    @Test
    void responsesUseActorSchedulerAndOnlyLatestRequestOwnsTheResponse() throws Exception {
        try (var fixture = new MenuTestClient()) {
            var core = core(fixture);
            var menu = new TeamManagementMenu(core);
            var client = fixture.new Client(UUID.randomUUID());
            List<String> results = new ArrayList<>();
            var first = request(menu, client, () -> true, results);
            var second = request(menu, client, () -> true, results);
            second.accept("second", null);
            first.accept("first", null);
            assertTrue(results.isEmpty());
            client.drain();
            assertEquals(List.of("second"), results);
            var failed = request(menu, client, () -> true, results);
            failed.accept(null, new IllegalStateException("database"));
            client.drain();
            assertEquals(List.of("second", "failed"), results);
            menu.onDisable(new PluginDisableEvent(core));
        }
    }

    @Test
    void lateResponsesCannotEscapePermissionConnectionViewTargetOrPluginFences() throws Exception {
        for (String reason :
                new String[] {
                    "permission",
                    "offline",
                    "reconnect",
                    "window",
                    "open-close",
                    "context",
                    "disable",
                    "reject",
                    "disabled-race",
                    "retired"
                }) {
            try (var fixture = new MenuTestClient()) {
                var core = core(fixture);
                var menu = new TeamManagementMenu(core);
                var client = fixture.new Client(UUID.randomUUID());
                var context = new AtomicBoolean(true);
                List<String> results = new ArrayList<>();
                var response = request(menu, client, context::get, results);
                switch (reason) {
                    case "permission" -> client.permission = false;
                    case "offline" -> client.connected = false;
                    case "reconnect" -> fixture.new Client(client.id);
                    case "window" -> client.view = client.view("CHEST");
                    case "open-close" -> {
                        menu.onInventoryOpen(new InventoryOpenEvent(client.view("CHEST")));
                        menu.onInventoryClose(new InventoryCloseEvent(client.view));
                    }
                    case "context" -> context.set(false);
                    case "disable" -> menu.onDisable(new PluginDisableEvent(core));
                    case "reject" -> client.reject = true;
                    case "disabled-race" -> client.disabledRace = true;
                    case "retired" -> client.retired = true;
                }
                response.accept("late", null);
                client.drain();
                assertTrue(results.isEmpty(), reason);
                assertTrue(
                        ((Map<?, ?>)
                                        TestObjects.field(TeamManagementMenu.class, "requests")
                                                .get(menu))
                                .isEmpty(),
                        reason);
                menu.onDisable(new PluginDisableEvent(core));
            }
        }
    }

    @Test
    void oldWindowCloseCannotInvalidateNewWindowResponse() throws Exception {
        try (var fixture = new MenuTestClient()) {
            var core = core(fixture);
            var menu = new TeamManagementMenu(core);
            var client = fixture.new Client(UUID.randomUUID());
            var previous = client.view;
            client.view = client.view("CHEST");
            List<String> results = new ArrayList<>();
            var latest = request(menu, client, () -> true, results);
            menu.onInventoryClose(new InventoryCloseEvent(previous));
            latest.accept("current", null);
            client.drain();
            assertEquals(List.of("current"), results);
            menu.onDisable(new PluginDisableEvent(core));
        }
    }

    @Test
    void oldConnectionCloseCannotInvalidateReplacementConnectionRequest() throws Exception {
        try (var fixture = new MenuTestClient()) {
            var core = core(fixture);
            var menu = new TeamManagementMenu(core);
            var previous = fixture.new Client(UUID.randomUUID());
            List<String> results = new ArrayList<>();
            var old = request(menu, previous, () -> true, results);
            var current = fixture.new Client(previous.id);
            var latest = request(menu, current, () -> true, results);
            menu.onInventoryClose(new InventoryCloseEvent(previous.view));
            old.accept("old", null);
            previous.drain();
            latest.accept("current", null);
            current.drain();
            assertEquals(List.of("current"), results);
            menu.onDisable(new PluginDisableEvent(core));
        }
    }
}

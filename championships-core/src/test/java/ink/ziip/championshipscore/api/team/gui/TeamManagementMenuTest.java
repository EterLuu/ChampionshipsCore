package ink.ziip.championshipscore.api.team.gui;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.gui.AnvilInputMenu;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.support.MenuTestClient;
import ink.ziip.championshipscore.support.TestObjects;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
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

    @Nested
    @ExtendWith(ConfigurationStateExtension.class)
    class Layout {
        @Test
        void allTeamSelectorsHonorConfiguredCapacityOrderAndMovedNavigation() throws Exception {
            var defaults =
                    YamlConfiguration.loadConfiguration(
                            Path.of("src/main/resources/gui.yml").toFile());
            for (var screen : TeamManagementMenu.Screen.values()) {
                if (screen == TeamManagementMenu.Screen.CONFIRM) continue;
                var config = new YamlConfiguration();
                config.setDefaults(defaults);
                config.set(screen.id.layout("content"), List.of(7, 2, 5));
                config.set(screen.id.layout("border"), List.of());
                config.set(screen.id.item("next") + ".slot", 8);
                activate(config);
                var layout = TeamManagementMenu.layout(screen);
                assertEquals(List.of(7, 2, 5), layout.content(), screen.name());
                assertEquals(List.of(), layout.border());
                assertEquals(8, layout.control("next"));
                assertEquals(14, layout.pageCount(40), screen.name());
                var visited = new ArrayList<Integer>();
                for (int page = 0; page < layout.pageCount(40); page++)
                    for (int slot : layout.content()) {
                        int index = layout.index(page, slot);
                        if (index < 40) visited.add(index);
                    }
                assertEquals(MenuLayout.range(0, 40), visited, screen.name());
                config.set(screen.id.layout("content"), List.of(12));
                config.set(screen.id.item("next") + ".slot", 9);
                assertEquals(List.of(7, 2, 5), layout.content());
                assertEquals(8, layout.control("next"));
            }
        }

        @Test
        void defaultColorAndTargetTeamLayoutsRetainAllChoicesAndReserveControls() throws Exception {
            activate(
                    YamlConfiguration.loadConfiguration(
                            Path.of("src/main/resources/gui.yml").toFile()));
            var colors = TeamManagementMenu.layout(TeamManagementMenu.Screen.COLOR);
            assertEquals(1, colors.pageCount(16));
            assertEquals(
                    List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29),
                    colors.content().subList(0, 16));
            assertFalse(colors.content().contains(colors.control("back")));
            var teams = TeamManagementMenu.layout(TeamManagementMenu.Screen.TARGET_TEAM);
            assertEquals(3, teams.pageCount(73));
            assertEquals(50, teams.control("next"));
            var confirm = TeamManagementMenu.layout(TeamManagementMenu.Screen.CONFIRM);
            assertEquals(11, confirm.control("confirm"));
            assertEquals(15, confirm.control("cancel"));
        }

        @Test
        void everyTeamListVisitsAllEntriesWithSparseAndCenteredPageArrangements() throws Exception {
            var doc =
                    YamlConfiguration.loadConfiguration(
                            Path.of("src/main/resources/gui.yml").toFile());
            activate(doc);
            for (var screen : TeamManagementMenu.Screen.values()) {
                if (screen == TeamManagementMenu.Screen.CONFIRM) continue;
                String content = screen.id.layout("content");
                doc.set(content, null);
                doc.set(content + ".1", List.of(22));
                doc.set(content + ".2", List.of(20, 24));
                doc.set(content + ".3", List.of(12, 13, 14));
                var layout = TeamManagementMenu.layout(screen);
                assertTrue(MenuId.supportsContentArrangements(screen.id.path()));
                assertEquals(3, layout.pageCount(8));
                assertEquals(List.of(20, 24), layout.pageSlots(2, 8));
                assertEquals(7, layout.index(2, 24, 8));
                assertEquals(-1, layout.index(2, 14, 8));
                doc.set(content, List.of(18, 17));
                assertEquals(4, TeamManagementMenu.layout(screen).pageCount(8));
                assertEquals(List.of(20, 24), layout.pageSlots(2, 8));
            }
        }
    }

    private static void activate(YamlConfiguration document) throws Exception {
        TestObjects.field(GuiConfig.class, "active").set(null, document);
    }
}

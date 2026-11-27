package ink.ziip.championshipscore.api.game.tntrun.runtime;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;
import ink.ziip.championshipscore.api.game.tntrun.config.TNTRunConfig;
import ink.ziip.championshipscore.configuration.ConfigurationStateExtension;
import ink.ziip.championshipscore.configuration.config.message.TNTRunMessages;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationHandler;
import java.util.*;

@ExtendWith(ConfigurationStateExtension.class)
class TNTRunElytraTest {
    @ParameterizedTest
    @ValueSource(ints = {180, 240})
    void warnsAtSixtyCountsDownLastTenAndRemovesAtNinety(int duration) throws Exception {
        try (var f = new Fixture(duration)) {
            Person player = f.addPlayer(true);
            f.start();
            f.advance(59);
            assertTrue(f.area.messages.isEmpty());
            assertEquals(0, player.inventoryReads);
            f.advance(1);
            assertEquals(List.of("60:chat:warning 30"), f.area.messages);
            // A mutable map configuration cannot move the deadline of an already running round.
            f.area.getGameConfig().setTimer(duration + 60);
            f.advance(29);
            assertEquals(Material.ELYTRA, player.items[38].getType());
            assertTrue(player.gliding);
            List<String> expected = new ArrayList<>(List.of("60:chat:warning 30"));
            expected.addAll(
                    List.of(
                            "80:bar:countdown 10",
                            "81:bar:countdown 9",
                            "82:bar:countdown 8",
                            "83:bar:countdown 7",
                            "84:bar:countdown 6",
                            "85:bar:countdown 5",
                            "86:bar:countdown 4",
                            "87:bar:countdown 3",
                            "88:bar:countdown 2",
                            "89:bar:countdown 1"));
            assertEquals(expected, f.area.messages);
            f.advance(1);
            for (int slot : List.of(0, 12, 38, 40)) assertNull(player.items[slot], "slot " + slot);
            assertNull(player.crafting[1]);
            assertEquals(Material.AIR, player.cursor.getType());
            assertEquals(Material.LEATHER_BOOTS, player.items[36].getType());
            assertFalse(player.gliding);
            expected.addAll(List.of("90:chat:removed", "90:bar:removed"));
            f.advance(10);
            assertEquals(expected, f.area.messages);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {60, 89})
    void roundsEndingBeforeNinetySecondsDoNotAnnounceAnUnreachableRemoval(int duration)
            throws Exception {
        try (var f = new Fixture(duration)) {
            Person player = f.addPlayer(true);
            f.start();
            f.advance(duration);
            assertTrue(f.area.messages.isEmpty());
            assertEquals(0, player.inventoryReads);
            assertEquals(1, f.area.ends);
        }
    }

    @Test
    void usesCurrentParticipantsAndLeavesSpectatorsEliminatedAndUnrelatedPlayersAlone()
            throws Exception {
        try (var f = new Fixture(180)) {
            Person first = f.addPlayer(true);
            Person second = f.addPlayer(true);
            Person spectator = f.addPlayer(true);
            spectator.mode = GameMode.SPECTATOR;
            Person eliminated = f.addPlayer(true);
            f.area.getDeathPlayer().add(eliminated.id);
            Person unrelated = f.addPlayer(false);
            Person offline = f.addPlayer(true);
            f.online.remove(offline.id);
            f.start();
            f.advance(89);
            Person replacement = new Person(first.id);
            f.online.put(first.id, replacement.player);
            f.advance(1);
            assertNull(replacement.items[38]);
            assertNull(second.items[38]);
            for (Person excluded : List.of(first, spectator, eliminated, unrelated, offline)) {
                assertEquals(0, excluded.inventoryReads);
                assertEquals(Material.ELYTRA, excluded.items[38].getType());
                assertTrue(excluded.gliding);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"end", "disposed", "generation", "cancel"})
    void staleClockCannotRemoveEquipmentBroadcastOrEndAnotherRound(String state) throws Exception {
        try (var f = new Fixture(180)) {
            Person player = f.addPlayer(true);
            f.start();
            f.advance(89);
            f.area.messages.clear();
            switch (state) {
                case "end" -> set(f.area, "gameStageEnum", GameStageEnum.END);
                case "disposed" -> set(f.area, "disposed", true);
                case "generation" -> set(f.area, "startGeneration", 1L);
                case "cancel" -> f.invoke("cancelTerrainTasks");
            }
            // Deliberately execute the cancelled callback, including its final onEnd.
            f.advance(91);
            assertTrue(f.area.messages.isEmpty());
            assertEquals(0, player.inventoryReads);
            assertEquals(0, f.area.ends);
        }
    }

    @Test
    void replacementRoundGetsItsOwnFullNinetySeconds() throws Exception {
        try (var f = new Fixture(180)) {
            Person player = f.addPlayer(true);
            f.start();
            f.advance(89);
            Runnable oldClock = f.clock;
            f.invoke("cancelTerrainTasks");
            f.area.second = 0;
            f.area.messages.clear();
            f.start();
            oldClock.run();
            assertEquals(180, f.area.getTimer());
            assertTrue(f.area.messages.isEmpty());
            f.advance(89);
            assertEquals(0, player.inventoryReads);
            f.advance(1);
            assertNull(player.items[38]);
            assertEquals("90:chat:removed", f.area.messages.get(f.area.messages.size() - 2));
        }
    }

    private static final class Area extends TNTRunTeamArea {
        List<String> messages;
        int second;
        int ends;

        Area() {
            super(null, null, false, "fixture");
        }

        @Override
        protected void updateGameTimerBossBar(String title, int remaining, int duration) {}

        @Override
        public void sendMessageToAllGamePlayers(String message) {
            messages.add(second + ":chat:" + message);
        }

        @Override
        public void sendActionBarToAllGamePlayers(String message) {
            if (!message.startsWith("rain")) messages.add(second + ":bar:" + message);
        }

        @Override
        public void endGame() {
            ends++;
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Object previous = field(Bukkit.class, "server").get(null);
        final Area area = allocate(Area.class);
        final Map<UUID, Player> online = new HashMap<>();
        Runnable clock;

        Fixture(int duration) throws Exception {
            BukkitTask task = proxy(BukkitTask.class, (p, m, a) -> null);
            BukkitScheduler scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (p, m, a) -> {
                                if (!m.getName().equals("runTaskTimer"))
                                    throw new AssertionError(m.getName());
                                if ((long) a[3] == 20L) {
                                    assertEquals(20L, a[2]);
                                    clock = (Runnable) a[1];
                                }
                                return task;
                            });
            Server server =
                    proxy(
                            Server.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getScheduler" -> scheduler;
                                        case "getPlayer" -> online.get(a[0]);
                                        default -> throw new AssertionError(m.getName());
                                    });
            field(Bukkit.class, "server").set(null, server);
            var plugin = allocate(ChampionshipsCore.class);
            set(plugin, "server", server);
            var manager = allocate(GameManager.class);
            set(manager, "playerSpectatorStatus", new HashMap<>());
            set(manager, "spectatorManager", new SpectatorManager(plugin, manager));
            set(plugin, "gameManager", manager);
            var config = allocate(TNTRunConfig.class);
            set(config, "configuration", new YamlConfiguration());
            config.setTimer(duration);
            config.setCopies(1);
            config.setCopySize(new org.bukkit.util.Vector(10, 10, 10));
            set(area, "plugin", plugin);
            set(area, "scheduler", scheduler);
            set(area, "gameConfig", config);
            set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            set(area, "gamePlayers", new ArrayList<UUID>());
            set(area, "deathPlayer", new ArrayList<UUID>());
            set(area, "rainTnt", new HashSet<>());
            area.messages = new ArrayList<>();
            TNTRunMessages.TNT_RUN_BOSS_BAR = "%time%";
            TNTRunMessages.TNT_RUN_BOSS_BAR_TNT_RAIN = "%time% %rain-time%";
            TNTRunMessages.TNT_RUN_TNT_RAIN = "rain";
            TNTRunMessages.TNT_RUN_TNT_RAIN_COUNT_DOWN = "rain %time%";
            TNTRunMessages.TNT_RUN_ELYTRA_REMOVAL_WARNING = "warning %time%";
            TNTRunMessages.TNT_RUN_ELYTRA_REMOVAL_COUNT_DOWN = "countdown %time%";
            TNTRunMessages.TNT_RUN_ELYTRA_REMOVED = "removed";
        }

        Person addPlayer(boolean participant) {
            Person person = new Person(UUID.randomUUID());
            online.put(person.id, person.player);
            if (participant) area.getGamePlayers().add(person.id);
            return person;
        }

        void start() throws Exception {
            invoke("beginGameProgress");
        }

        void advance(int seconds) {
            for (int i = 0; i < seconds; i++) {
                area.second++;
                clock.run();
            }
        }

        void invoke(String name) throws Exception {
            var method = TNTRunTeamArea.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(area);
        }

        @Override
        public void close() throws Exception {
            try {
                invoke("cancelTerrainTasks");
            } finally {
                field(Bukkit.class, "server").set(null, previous);
            }
        }
    }

    private static final class Person {
        final UUID id;
        final Player player;
        final ItemStack[] items = new ItemStack[41], crafting = new ItemStack[5];
        ItemStack cursor = new Stack(Material.ELYTRA);
        GameMode mode = GameMode.ADVENTURE;
        boolean gliding = true;
        int inventoryReads;

        Person(UUID id) {
            this.id = id;
            for (int slot : List.of(0, 12, 38, 40)) items[slot] = new Stack(Material.ELYTRA);
            items[36] = new Stack(Material.LEATHER_BOOTS);
            crafting[1] = new Stack(Material.ELYTRA);
            PlayerInventory inventory = proxy(PlayerInventory.class, inventory(items));
            Inventory top = proxy(CraftingInventory.class, inventory(crafting));
            InventoryView view =
                    proxy(
                            InventoryView.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getTopInventory" -> top;
                                        default -> throw new AssertionError(m.getName());
                                    });
            player =
                    proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getGameMode" -> mode;
                                        case "getInventory" -> {
                                            inventoryReads++;
                                            yield inventory;
                                        }
                                        case "getOpenInventory" -> view;
                                        case "getItemOnCursor" -> cursor;
                                        case "setItemOnCursor" -> {
                                            cursor =
                                                    a[0] == null
                                                            ? new Stack(Material.AIR)
                                                            : (ItemStack) a[0];
                                            yield null;
                                        }
                                        case "isGliding" -> gliding;
                                        case "setGliding" -> {
                                            gliding = (boolean) a[0];
                                            yield null;
                                        }
                                        default -> throw new AssertionError(m.getName());
                                    });
        }

        private InvocationHandler inventory(ItemStack[] contents) {
            return (p, m, a) ->
                    switch (m.getName()) {
                        case "getSize" -> contents.length;
                        case "getItem" -> contents[(int) a[0]];
                        case "setItem" -> {
                            contents[(int) a[0]] = (ItemStack) a[1];
                            yield null;
                        }
                        default -> throw new AssertionError(m.getName());
                    };
        }
    }

    private static final class Stack extends ItemStack {
        final Material material;

        Stack(Material material) {
            this.material = material;
        }

        @Override
        public Material getType() {
            return material;
        }
    }
}

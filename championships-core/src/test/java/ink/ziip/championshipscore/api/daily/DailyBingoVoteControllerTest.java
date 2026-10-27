package ink.ziip.championshipscore.api.daily;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.daily.DailyBingoVoteController.Phase;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.protocol.BingoDifficulty;
import ink.ziip.championshipscore.protocol.BingoVariantRules;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;

class DailyBingoVoteControllerTest {
    private Server prior;
    private final Map<UUID, Player> connections = new HashMap<>();

    @BeforeEach
    void server() throws Exception {
        prior = Bukkit.getServer();
        field(Bukkit.class, "server")
                .set(
                        null,
                        proxy(
                                Server.class,
                                (p, m, a) -> {
                                    if (m.getName().equals("getPlayer"))
                                        return connections.get(a[0]);
                                    throw new AssertionError(m.getName());
                                }));
    }

    @AfterEach
    void restore() throws Exception {
        field(Bukkit.class, "server").set(null, prior);
    }

    @Test
    void staleOwnersVotesPhasesConnectionsAndViewsCannotOperateCurrentWindow() throws Exception {
        for (String reason : List.of("owner", "vote", "phase", "connection", "view", "finished")) {
            var f = new Fixture(Phase.LINES);
            switch (reason) {
                case "owner" -> set(f.menu, "owner", new DailyBingoVoteController(null, null));
                case "vote" -> set(f.menu, "vote", new CompletableFuture<BingoVariantRules>());
                case "phase" -> set(f.controller, "phase", Phase.GENESIS);
                case "connection" -> connections.put(f.id, proxy(Player.class, (p, m, a) -> null));
                case "view" -> f.current = proxy(Inventory.class, (p, m, a) -> null);
                case "finished" -> f.future.complete(null);
            }
            f.controller.click(f.player, 24, f.menu);
            f.controller.click(f.player, 10, f.menu);
            assertEquals(0, f.closes, reason);
            assertTrue(map(f.controller, "lineVotes").isEmpty(), reason);
        }
    }

    @Test
    void lockedPointsAndExtremeChoicesKeepBusinessEligibility() throws Exception {
        var f = new Fixture(Phase.MODE);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object points =
                Enum.valueOf(
                        (Class)
                                Class.forName(
                                        DailyBingoVoteController.class.getName() + "$ModeChoice"),
                        "POINTS");
        f.targets.put(10, points);
        f.controller.click(f.player, 10, f.menu);
        assertTrue(map(f.controller, "modeVotes").isEmpty());
        f = new Fixture(Phase.DIFFICULTY);
        var team = allocate(ChampionshipTeam.class);
        set(team, "members", Set.of(f.id));
        set(f.controller, "teams", List.of(team));
        f.targets.put(10, BingoDifficulty.EXTREME);
        f.controller.click(f.player, 10, f.menu);
        assertTrue(map(f.controller, "difficultyVotes").isEmpty());
    }

    @Test
    void genesisCannotCastAChoiceRemovedSinceRendering() throws Exception {
        var f = new Fixture(Phase.GENESIS);
        map(f.controller, "genesisOptions").put(f.id, List.of(Material.STONE));
        f.targets.put(10, Material.DIAMOND);
        f.controller.click(f.player, 10, f.menu);
        assertTrue(map(f.controller, "genesisPicks").isEmpty());
    }

    @Test
    void cancelDoesNotCloseAnotherControllersWindow() throws Exception {
        var f = new Fixture(Phase.MODE);
        set(f.menu, "owner", new DailyBingoVoteController(null, null));
        f.controller.cancel();
        assertEquals(0, f.closes);
        assertTrue(f.future.isDone());
    }

    @Test
    void completionCallbackCanStartANewVoteWithoutOldCancellationClearingIt() throws Exception {
        var f = new Fixture(Phase.MODE);
        var replacement = new CompletableFuture<BingoVariantRules>();
        f.future.thenRun(
                () -> {
                    try {
                        set(f.controller, "result", replacement);
                        set(f.controller, "phase", Phase.MODE);
                        f.voters.add(f.id);
                    } catch (Exception ex) {
                        throw new AssertionError(ex);
                    }
                });
        f.controller.cancel();
        assertSame(replacement, field(f.controller.getClass(), "result").get(f.controller));
        assertTrue(f.voters.contains(f.id));
        assertFalse(replacement.isDone());
        assertEquals(1, f.closes);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> map(Object o, String name) throws Exception {
        return (Map<UUID, Object>) field(o.getClass(), name).get(o);
    }

    private final class Fixture {
        final UUID id = UUID.randomUUID();
        final DailyBingoVoteController controller = new DailyBingoVoteController(null, null);
        final CompletableFuture<BingoVariantRules> future = new CompletableFuture<>();
        final DailyBingoVoteMenu menu;
        final Player player;
        final Set<UUID> voters;
        final Map<Integer, Object> targets = new HashMap<>();
        Inventory current;
        int closes;

        Fixture(Phase phase) throws Exception {
            player =
                    proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "isOnline" -> true;
                                        case "getLocation" -> null;
                                        case "getOpenInventory" ->
                                                proxy(InventoryView.class, (v, n, b) -> current);
                                        case "closeInventory" -> {
                                            closes++;
                                            current = proxy(Inventory.class, (v, n, b) -> null);
                                            yield null;
                                        }
                                        default -> throw new AssertionError(m.getName());
                                    });
            connections.put(id, player);
            set(controller, "result", future);
            set(controller, "phase", phase == Phase.GENESIS ? phase : Phase.MODE);
            @SuppressWarnings("unchecked")
            var existing = (Set<UUID>) field(controller.getClass(), "voters").get(controller);
            voters = existing;
            voters.add(id);
            menu = allocate(DailyBingoVoteMenu.class);
            set(menu, "owner", controller);
            set(menu, "vote", future);
            set(menu, "connection", player);
            set(menu, "phase", phase);
            set(menu, "targets", targets);
            set(
                    menu,
                    "layout",
                    new MenuLayout(
                            27,
                            List.of(10),
                            List.of(),
                            Map.of(
                                    "previous",
                                    18,
                                    "page",
                                    22,
                                    "close",
                                    24,
                                    "next",
                                    26,
                                    "options-previous",
                                    19,
                                    "options-page",
                                    20,
                                    "options-next",
                                    21)));
            current =
                    proxy(
                            Inventory.class,
                            (p, m, a) -> m.getName().equals("getHolder") ? menu : null);
            set(menu, "inventory", current);
        }
    }
}

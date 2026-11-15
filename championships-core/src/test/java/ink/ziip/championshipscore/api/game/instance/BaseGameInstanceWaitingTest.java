package ink.ziip.championshipscore.api.game.instance;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.daily.DailyManager;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.parkourwarrior.runtime.ParkourWarriorTeamArea;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.*;

class BaseGameInstanceWaitingTest {
    @Test
    void everyGameKeepsParticipantsInArenaUntilItsFinalSettlement() throws Exception {
        for (GameTypeEnum game : GameTypeEnum.values()) {
            for (boolean nextRound : new boolean[] {true, false}) {
                try (var f = new Fixture()) {
                    set(f.area, "gameTypeEnum", game);
                    f.area.completePostGame(nextRound);
                    assertEquals(!nextRound, f.area.lobbyTeleport, game.name());
                    assertEquals(nextRound, f.manager.held, game.name());
                    assertEquals(nextRound ? 0 : 1, f.area.spectatorReleases, game.name());
                    assertSame(f.arena, f.playerWorld);
                    assertEquals(1, f.area.resets);
                    f.area.completePostGame(nextRound);
                    assertEquals(1, f.area.resets, "Settlement must be idempotent");
                }
            }
        }
    }

    @Test
    void resultReconnectKeepsArenaPositionAndRestoresWrongWorldToArena() throws Exception {
        try (var f = new Fixture()) {
            assertTrue(f.area.restorePostGameParticipant(f.player));
            assertFalse(f.area.lobbyTeleport);
            assertEquals(0, f.teleports);
            f.playerWorld = world("lobby");
            assertTrue(f.area.restorePostGameParticipant(f.player));
            assertSame(f.arena, f.playerWorld);
            assertEquals(1, f.teleports);
            assertFalse(f.area.lobbyTeleport);
        }
    }

    @Test
    void staleRecoveryCannotMoveAReplacementConnectionOrAnotherRun() throws Exception {
        for (String stale :
                List.of("connection", "owner", "stage", "released", "disposed", "unloaded")) {
            try (var f = new Fixture()) {
                switch (stale) {
                    case "connection" -> f.connected = false;
                    case "owner" -> f.manager.owner = null;
                    case "stage" -> set(f.area, "gameStageEnum", GameStageEnum.PROGRESS);
                    case "released" -> set(f.area, "postGamePending", false);
                    case "disposed" -> set(f.area, "disposed", true);
                    case "unloaded" -> set(f.plugin, "loaded", false);
                }
                assertFalse(f.area.restorePostGameParticipant(f.player), stale);
                assertEquals(0, f.area.sanitizations, stale);
                assertEquals(0, f.teleports, stale);
            }
        }
    }

    @Test
    void roundHoldReconnectReturnsToArenaWithoutLobbyRouting() throws Exception {
        try (var f = new Fixture()) {
            f.playerWorld = world("lobby");
            f.area.prepareParticipantForNextRound(f.player);
            assertSame(f.arena, f.playerWorld);
            assertEquals(1, f.teleports);
            assertFalse(f.area.lobbyTeleport);
        }
    }

    private static World world(String name) {
        return proxy(
                World.class,
                (p, m, a) ->
                        switch (m.getName()) {
                            case "getName" -> name;
                            case "getUID" ->
                                    UUID.nameUUIDFromBytes(
                                            name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            case "equals" -> p == a[0];
                            case "hashCode" -> System.identityHashCode(p);
                            default -> null;
                        });
    }

    private static final class Fixture implements AutoCloseable {
        final Object previousServer = field(Bukkit.class, "server").get(null);
        final UUID id = UUID.randomUUID();
        final World arena = world("arena");
        final ChampionshipsCore plugin = allocate(ChampionshipsCore.class);
        final Area area = allocate(Area.class);
        final Manager manager = allocate(Manager.class);
        World playerWorld = arena;
        int teleports;
        boolean connected = true;
        final Player player =
                proxy(
                        Player.class,
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "getUniqueId" -> id;
                                    case "isOnline" -> connected;
                                    case "getWorld" -> playerWorld;
                                    case "teleport" -> {
                                        playerWorld = ((Location) a[0]).getWorld();
                                        teleports++;
                                        yield true;
                                    }
                                    default -> null;
                                });

        Fixture() throws Exception {
            field(Bukkit.class, "server")
                    .set(
                            null,
                            proxy(
                                    Server.class,
                                    (p, m, a) ->
                                            m.getName().equals("getPlayer") && connected
                                                    ? player
                                                    : null));
            set(plugin, "loaded", true);
            set(plugin, "gameManager", manager);
            var daily = allocate(DailyManager.class);
            set(daily, "sessionByInstance", new IdentityHashMap<>());
            set(plugin, "dailyManager", daily);
            set(area, "plugin", plugin);
            set(area, "gameStageEnum", GameStageEnum.END);
            set(area, "postGamePending", true);
            var blocks = allocate(CountdownBlockDisappearance.class);
            set(blocks, "snapshots", new ArrayList<>());
            set(area, "countdownBlockDisappearance", blocks);
            area.ids = List.of(id);
            area.destination = new Location(arena, 4, 80, 6);
            manager.owner = area;
        }

        public void close() throws Exception {
            field(Bukkit.class, "server").set(null, previousServer);
        }
    }

    private static final class Area extends ParkourWarriorTeamArea {
        List<UUID> ids;
        Location destination;
        boolean lobbyTeleport;
        int resets, spectatorReleases, sanitizations;

        private Area() {
            super(null, null);
        }

        public Collection<UUID> getParticipantUniqueIds() {
            return ids;
        }

        public Location getSpectatorSpawnLocation() {
            return destination;
        }

        public void sanitizeParticipantForLobby(Player player, boolean teleport) {
            sanitizations++;
            lobbyTeleport |= teleport;
        }

        public void releaseAllSpectators() {
            spectatorReleases++;
        }

        public void resetGame() {
            resets++;
        }
    }

    private static final class Manager extends GameManager {
        Area owner;
        boolean held;

        private Manager() {
            super(null);
        }

        public BaseGameInstance getBasePlayerArea(UUID id) {
            return owner;
        }

        public void holdParticipantsForNextRound(BaseGameInstance area, Collection<UUID> ids) {
            held = true;
            for (UUID id : ids) area.prepareParticipantForNextRound(Bukkit.getPlayer(id));
        }

        public void holdSpectatorsForNextRound(BaseGameInstance area) {}

        public void releaseInstanceParticipants(BaseGameInstance area) {}
    }
}

package ink.ziip.championshipscore.api.game.manager;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.spectate.SpectatorManager;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class GameManagerHandlerTest {
    @Test
    void terrainResetCannotDropHeldPlayersButNextRoundTeleportStillWorks() throws Exception {
        Manager manager = allocate(Manager.class);
        var plugin = allocate(ChampionshipsCore.class);
        set(plugin, "gameManager", manager);
        set(manager, "spectatorManager", new SpectatorManager(plugin, manager));
        var handler = allocate(GameManagerHandler.class);
        set(handler, "plugin", plugin);
        UUID id = UUID.randomUUID();
        Player player =
                proxy(Player.class, (p, m, a) -> m.getName().equals("getUniqueId") ? id : null);
        World world =
                proxy(
                        World.class,
                        (p, m, a) ->
                                switch (m.getName()) {
                                    case "equals" -> p == a[0];
                                    case "hashCode" -> System.identityHashCode(p);
                                    default -> null;
                                });
        Location start = new Location(world, 2, 80, 4, 0, 0);
        Location falling = new Location(world, 3, 79, 4, 90, 10);
        PlayerMoveEvent move = new PlayerMoveEvent(player, start, falling);
        Object previousServer = field(Bukkit.class, "server").get(null);
        try {
            field(Bukkit.class, "server").set(null, proxy(Server.class, (p, m, a) -> null));
            handler.onLobbyFall(move);
            assertEquals(start.toVector(), move.getTo().toVector());
            assertEquals(90F, move.getTo().getYaw());
            assertEquals(10F, move.getTo().getPitch());
            Location nextSpawn = new Location(world, 20, 90, 40);
            var teleport =
                    new PlayerTeleportEvent(
                            player, start, nextSpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
            handler.onLobbyFall(teleport);
            assertEquals(nextSpawn, teleport.getTo());
            assertFalse(teleport.isCancelled());
        } finally {
            field(Bukkit.class, "server").set(null, previousServer);
        }
    }

    private static final class Manager extends GameManager {
        private Manager() {
            super(null);
        }

        public boolean isWaitingForNextRound(UUID id) {
            return true;
        }

        public BaseGameInstance getPlayerSpectatorStatus(UUID id) {
            return null;
        }

        public BaseGameInstance getBasePlayerArea(UUID id) {
            return null;
        }
    }
}

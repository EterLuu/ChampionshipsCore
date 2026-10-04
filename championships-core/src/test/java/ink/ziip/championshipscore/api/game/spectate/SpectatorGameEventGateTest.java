package ink.ziip.championshipscore.api.game.spectate;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.UUID;

class SpectatorGameEventGateTest {
    private final UUID id = UUID.randomUUID();
    private final Player player = proxy(Player.class, "getUniqueId", id);

    @Test
    void movingSpectatorsCannotReachCheckpointOrFloorMechanisms() {
        var event =
                new PlayerMoveEvent(
                        player, new Location(null, 0, 0, 0), new Location(null, 1, 0, 0));
        assertTrue(SpectatorGameEventGate.suppress(event, Set.of(id)::contains));
        assertFalse(SpectatorGameEventGate.suppress(event, ignored -> false));
    }

    @Test
    void spectatorsCannotActOrBecomeMechanismTargets() {
        assertTrue(SpectatorGameEventGate.suppress(new ActorEvent(player), Set.of(id)::contains));
        assertFalse(
                SpectatorGameEventGate.suppress(
                        new ActorEvent(proxy(Entity.class, "getUniqueId", id)),
                        Set.of(id)::contains));
    }

    @Test
    void projectilesRetainTheirSpectatorSourceProtection() {
        Projectile projectile = proxy(Projectile.class, "getShooter", player);
        assertTrue(
                SpectatorGameEventGate.suppress(new ActorEvent(projectile), Set.of(id)::contains));
        assertFalse(SpectatorGameEventGate.suppress(new ActorEvent(projectile), ignored -> false));
    }

    @Test
    void recoveryEventsStillReachTheOwningGame() {
        assertFalse(
                SpectatorGameEventGate.suppress(
                        new PlayerJoinEvent(player, (String) null), Set.of(id)::contains));
        assertFalse(
                SpectatorGameEventGate.suppress(
                        new PlayerQuitEvent(player, (String) null), Set.of(id)::contains));
        assertFalse(
                SpectatorGameEventGate.suppress(
                        new PlayerRespawnEvent(player, new Location(null, 0, 0, 0), false),
                        Set.of(id)::contains));
    }

    public static final class ActorEvent extends Event {
        private final Entity actor;

        public ActorEvent(Entity actor) {
            this.actor = actor;
        }

        public Entity getActor() {
            return actor;
        }

        @Override
        public HandlerList getHandlers() {
            return new HandlerList();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, String getter, Object value) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class[] {type},
                        (self, method, args) -> {
                            if (method.getName().equals(getter)) return value;
                            if (method.getName().equals("hashCode"))
                                return System.identityHashCode(self);
                            if (method.getName().equals("equals")) return self == args[0];
                            if (method.getReturnType() == boolean.class) return false;
                            if (method.getReturnType() == int.class) return 0;
                            return null;
                        });
    }
}

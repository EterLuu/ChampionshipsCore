package ink.ziip.championshipscore.api.game.spectate;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** Stateless event admission; spectator identity remains exclusively in SpectatorManager. */
public final class SpectatorGameEventGate {
    private SpectatorGameEventGate() {}

    private static final ClassValue<List<Method>> ACTORS =
            new ClassValue<>() {
                @Override
                protected List<Method> computeValue(Class<?> type) {
                    return Arrays.stream(type.getMethods())
                            .filter(
                                    method ->
                                            method.getParameterCount() == 0
                                                    && (Entity.class.isAssignableFrom(
                                                                    method.getReturnType())
                                                            || ProjectileSource.class
                                                                    .isAssignableFrom(
                                                                            method
                                                                                    .getReturnType())))
                            .toList();
                }
            };

    public static boolean suppress(Event event, Predicate<UUID> spectator) {
        // Reconnect and respawn recovery must still reach the game that owns the participant
        // roster.
        if (event instanceof PlayerJoinEvent
                || event instanceof PlayerQuitEvent
                || event instanceof PlayerRespawnEvent) return false;
        for (Method accessor : ACTORS.get(event.getClass())) {
            try {
                Object actor = accessor.invoke(event);
                if (actor instanceof Player player && spectator.test(player.getUniqueId()))
                    return true;
                if (actor instanceof Projectile projectile
                        && projectile.getShooter() instanceof Player player
                        && spectator.test(player.getUniqueId())) return true;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(
                        "Cannot resolve game event actor: " + accessor, failure);
            }
        }
        return false;
    }
}

package ink.ziip.championshipscore.api.game.laserbox.mechanics;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities;

import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client FOV presentation only; never changes movement attributes or item-use state. */
public final class LaserBoxZoom extends PacketListenerAbstract implements AutoCloseable {
    private static final float FOV_DENOMINATOR_MULTIPLIER = 12.0F;
    private final Set<UUID> aiming = ConcurrentHashMap.newKeySet();
    private final PlatformScheduler scheduler;
    private volatile boolean closed;

    public LaserBoxZoom(Plugin plugin) {
        super(PacketListenerPriority.HIGH);
        scheduler = new PlatformScheduler(plugin);
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (closed
                || event.getPacketType() != PacketType.Play.Server.PLAYER_ABILITIES
                || !aiming.contains(event.getUser().getUUID())) return;
        var packet = new WrapperPlayServerPlayerAbilities(event);
        packet.setFOVModifier(scopedFovDenominator(packet.getFOVModifier()));
    }

    static float scopedFovDenominator(float walkingSpeed) {
        return walkingSpeed > 0.0F ? walkingSpeed * FOV_DENOMINATOR_MULTIPLIER : walkingSpeed;
    }

    /** Caller must be on the player's entity thread. */
    public void setAiming(Player player, boolean scoped) {
        if (closed) return;
        boolean changed =
                scoped ? aiming.add(player.getUniqueId()) : aiming.remove(player.getUniqueId());
        if (changed) sendCurrentAbilities(player);
    }

    private static void sendCurrentAbilities(Player player) {
        // Reassigning the same flight speed resends native abilities with all original flags.
        // setWalkSpeed would also overwrite the movement attribute's base value; avoid it.
        player.setFlySpeed(player.getFlySpeed());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        Set<UUID> restore = Set.copyOf(aiming);
        aiming.clear();
        for (UUID id : restore) {
            Player player = Bukkit.getPlayer(id);
            if (player != null)
                scheduler.runEntity(
                        player,
                        () -> {
                            if (player.isOnline() && Bukkit.getPlayer(id) == player)
                                sendCurrentAbilities(player);
                        });
        }
    }
}

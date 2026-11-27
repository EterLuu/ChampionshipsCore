package ink.ziip.championshipscore.util.glow;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.entity.data.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.logging.LogText;
import ink.ziip.championshipscore.platform.bukkit.player.NativeEntityPackets;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-viewer entity glowing using PacketEvents. Making an entity glow only for a chosen viewer
 * requires editing the outgoing entity-metadata stream for that viewer alone, which is why this
 * can't be done with the global {@link Entity#setGlowing(boolean)}.
 *
 * <p>The glow <em>colour</em> is not set here: it is taken from the entity's team on the viewer's
 * active scoreboard. This plugin keeps every player in a team-coloured team on the (shared) main
 * scoreboard, so a player's glow renders in their team colour automatically.
 */
public class GlowingEntities implements Listener {
    /** Bit 0x20 of the entity shared-flags byte marks an entity as invisible. */
    private static final byte INVISIBLE_FLAG = 0x20;

    /**
     * Bit 0x40 of the entity shared-flags byte (data-watcher index 0) marks an entity as glowing.
     */
    private static final byte GLOWING_FLAG = 0x40;

    /** Data-watcher index of the shared entity-flags byte. */
    private static final int SHARED_FLAGS_INDEX = 0;

    private final @NotNull Plugin plugin;

    /** Viewer UUID → the entity ids currently made to glow for that viewer. */
    private final Map<UUID, Set<Integer>> glowing = new ConcurrentHashMap<>();

    /** Viewer UUID → entity ids rendered as an invisible glowing outline for that viewer. */
    private final Map<UUID, Set<Integer>> invisibleGlowing = new ConcurrentHashMap<>();

    private PacketListenerAbstract metadataListener;
    private boolean enabled;

    public GlowingEntities(@NotNull Plugin plugin) {
        this.plugin = plugin;
        enable();
    }

    public void enable() {
        if (enabled) return;
        // Rewrite every entity-metadata packet heading to a viewer that should see a tracked entity
        // glow,
        // OR-ing the glow bit into the flags so the server's own metadata updates keep the glow
        // alive.
        metadataListener =
                new PacketListenerAbstract(PacketListenerPriority.HIGH) {
                    @Override
                    public void onPacketSend(PacketSendEvent event) {
                        if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) return;
                        var packet = new WrapperPlayServerEntityMetadata(event);
                        byte forced = forcedFlags(event.getUser().getUUID(), packet.getEntityId());
                        if (forced == 0) return;
                        var entries =
                                new java.util.ArrayList<EntityData<?>>(packet.getEntityMetadata());
                        for (int i = 0; i < entries.size(); i++) {
                            var entry = entries.get(i);
                            if (entry.getIndex() == SHARED_FLAGS_INDEX
                                    && entry.getValue() instanceof Byte flags) {
                                entries.set(
                                        i,
                                        new EntityData<>(
                                                SHARED_FLAGS_INDEX,
                                                EntityDataTypes.BYTE,
                                                (byte) (flags | forced)));
                                packet.setEntityMetadata(entries);
                                return;
                            }
                        }
                    }
                };
        PacketEvents.getAPI().getEventManager().registerListener(metadataListener);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        enabled = true;
    }

    public void disable() {
        if (!enabled) return;
        if (metadataListener != null)
            PacketEvents.getAPI().getEventManager().unregisterListener(metadataListener);
        HandlerList.unregisterAll(this);
        glowing.clear();
        invisibleGlowing.clear();
        enabled = false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        int entityId = event.getPlayer().getEntityId();
        glowing.remove(uuid);
        invisibleGlowing.remove(uuid);
        glowing.values().forEach(ids -> ids.remove(entityId));
        invisibleGlowing.values().forEach(ids -> ids.remove(entityId));
    }

    /**
     * Makes {@code entity} glow for {@code receiver} only, in the entity's scoreboard-team colour.
     */
    public void setGlowing(@NotNull Entity entity, @NotNull Player receiver) {
        if (glowing.computeIfAbsent(receiver.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                .add(entity.getEntityId())) sendFlags(receiver, entity);
    }

    /** Stops {@code entity} from glowing for {@code receiver}. */
    public void unsetGlowing(@NotNull Entity entity, @NotNull Player receiver) {
        Set<Integer> ids = glowing.get(receiver.getUniqueId());
        if (ids != null && ids.remove(entity.getEntityId())) sendFlags(receiver, entity);
    }

    /** Renders {@code entity} as an invisible glowing outline for {@code receiver} only. */
    public void setInvisibleGlowing(@NotNull Entity entity, @NotNull Player receiver) {
        if (invisibleGlowing
                .computeIfAbsent(receiver.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                .add(entity.getEntityId())) sendFlags(receiver, entity);
    }

    /** Restores {@code entity}'s real invisibility/glow flags for {@code receiver}. */
    public void unsetInvisibleGlowing(@NotNull Entity entity, @NotNull Player receiver) {
        Set<Integer> ids = invisibleGlowing.get(receiver.getUniqueId());
        if (ids != null && ids.remove(entity.getEntityId())) sendFlags(receiver, entity);
    }

    /**
     * Sends {@code receiver} an immediate metadata packet toggling the viewer-specific flags, so
     * changes show at once rather than waiting for the next server-driven metadata update. The
     * injection listener is idempotent, so it doesn't matter that this packet also passes through
     * it.
     */
    private void sendFlags(Player receiver, Entity entity) {
        try {
            byte flags =
                    (byte)
                            (NativeEntityPackets.sharedFlags(entity)
                                    | forcedFlags(receiver.getUniqueId(), entity.getEntityId()));
            PacketEvents.getAPI()
                    .getPlayerManager()
                    .sendPacket(
                            receiver,
                            new WrapperPlayServerEntityMetadata(
                                    entity.getEntityId(),
                                    List.of(
                                            new EntityData<>(
                                                    SHARED_FLAGS_INDEX,
                                                    EntityDataTypes.BYTE,
                                                    flags))));
        } catch (Exception e) {
            plugin.getLogger()
                    .warning(
                            LogText.formatModuleLog(
                                    "Glow",
                                    MessageConfig.codeText("glowing-entities.send-flags-1"),
                                    MessageConfig.codeText(
                                            "glowing-entities.send-flags-2", e.getMessage())));
        }
    }

    private byte forcedFlags(@NotNull UUID receiver, int entityId) {
        byte flags = 0;
        Set<Integer> glowingIds = glowing.get(receiver);
        if (glowingIds != null && glowingIds.contains(entityId)) flags |= GLOWING_FLAG;
        Set<Integer> outlinedIds = invisibleGlowing.get(receiver);
        if (outlinedIds != null && outlinedIds.contains(entityId))
            flags |= INVISIBLE_FLAG | GLOWING_FLAG;
        return flags;
    }
}

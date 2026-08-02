package ink.ziip.championshipscore.util.glow;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-viewer entity glowing built on ProtocolLib. Making an entity glow only for a chosen viewer requires
 * editing the outgoing entity-metadata stream for that viewer alone, which is why this can't be done with
 * the global {@link Entity#setGlowing(boolean)}.
 *
 * <p>The glow <em>colour</em> is not set here: it is taken from the entity's team on the viewer's active
 * scoreboard. This plugin keeps every player in a team-coloured team on the (shared) main scoreboard, so a
 * player's glow renders in their team colour automatically.
 */
public class GlowingEntities implements Listener {
    /** Bit 0x40 of the entity shared-flags byte (data-watcher index 0) marks an entity as glowing. */
    private static final byte GLOWING_FLAG = 0x40;
    /** Data-watcher index of the shared entity-flags byte. */
    private static final int SHARED_FLAGS_INDEX = 0;

    private final @NotNull Plugin plugin;
    private final ProtocolManager protocolManager;
    /** Viewer UUID → the entity ids currently made to glow for that viewer. */
    private final Map<UUID, Set<Integer>> glowing = new ConcurrentHashMap<>();
    private PacketAdapter metadataListener;
    private boolean enabled;

    public GlowingEntities(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        enable();
    }

    public void enable() {
        if (enabled) return;
        // Rewrite every entity-metadata packet heading to a viewer that should see a tracked entity glow,
        // OR-ing the glow bit into the flags so the server's own metadata updates keep the glow alive.
        metadataListener = new PacketAdapter(plugin, ListenerPriority.HIGH, PacketType.Play.Server.ENTITY_METADATA) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Set<Integer> ids = glowing.get(event.getPlayer().getUniqueId());
                if (ids == null || ids.isEmpty()) return;
                PacketContainer packet = event.getPacket();
                if (!ids.contains(packet.getIntegers().read(0))) return;
                injectGlowFlag(packet);
            }
        };
        protocolManager.addPacketListener(metadataListener);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        enabled = true;
    }

    public void disable() {
        if (!enabled) return;
        if (metadataListener != null) protocolManager.removePacketListener(metadataListener);
        HandlerList.unregisterAll(this);
        glowing.clear();
        enabled = false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        glowing.remove(event.getPlayer().getUniqueId());
    }

    /** Makes {@code entity} glow for {@code receiver} only, in the entity's scoreboard-team colour. */
    public void setGlowing(@NotNull Entity entity, @NotNull Player receiver) {
        entity.getScheduler().execute(plugin, () -> {
            int entityId = entity.getEntityId();
            byte flags = baseFlags(entity);
            glowing.computeIfAbsent(receiver.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(entityId);
            receiver.getScheduler().execute(plugin,
                    () -> sendFlags(receiver, entityId, flags, true), null, 1L);
        }, null, 1L);
    }

    /** Stops {@code entity} from glowing for {@code receiver}. */
    public void unsetGlowing(@NotNull Entity entity, @NotNull Player receiver) {
        entity.getScheduler().execute(plugin, () -> {
            int entityId = entity.getEntityId();
            byte flags = baseFlags(entity);
            Set<Integer> ids = glowing.get(receiver.getUniqueId());
            if (ids != null) ids.remove(entityId);
            receiver.getScheduler().execute(plugin,
                    () -> sendFlags(receiver, entityId, flags, false), null, 1L);
        }, null, 1L);
    }

    /**
     * Sends {@code receiver} an immediate metadata packet toggling the glow bit, so the change shows at once
     * rather than waiting for the next server-driven metadata update. The injection listener is idempotent,
     * so it doesn't matter that this packet also passes through it.
     */
    private void sendFlags(Player receiver, int entityId, byte flags, boolean glow) {
        if (glow) flags |= GLOWING_FLAG;
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, entityId);
        WrappedDataValue value = new WrappedDataValue(SHARED_FLAGS_INDEX, byteSerializer(), flags);
        packet.getDataValueCollectionModifier().write(0, List.of(value));
        try {
            protocolManager.sendServerPacket(receiver, packet);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send glow metadata packet: " + e.getMessage());
        }
    }

    /** OR-ins the glow bit into an outgoing metadata packet, preserving the entity's real flags. */
    private void injectGlowFlag(PacketContainer packet) {
        List<WrappedDataValue> values = packet.getDataValueCollectionModifier().read(0);
        for (WrappedDataValue value : values) {
            if (value.getIndex() == SHARED_FLAGS_INDEX && value.getValue() instanceof Byte flags) {
                value.setValue((byte) (flags | GLOWING_FLAG));
                return;
            }
        }
        // This update didn't carry the flags byte; append it so the glow still shows.
        List<WrappedDataValue> updated = new ArrayList<>(values);
        updated.add(new WrappedDataValue(SHARED_FLAGS_INDEX, byteSerializer(), GLOWING_FLAG));
        packet.getDataValueCollectionModifier().write(0, updated);
    }

    /**
     * The serializer for the shared-flags byte. Resolved through the {@link java.lang.reflect.Type} overload
     * because the {@code Class}-typed ones are deprecated for removal.
     */
    private static WrappedDataWatcher.Serializer byteSerializer() {
        return WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Byte.class);
    }

    /** Rebuilds the entity's shared-flags byte from Bukkit state so the immediate packet doesn't drop them. */
    private static byte baseFlags(Entity entity) {
        byte flags = 0;
        if (entity.getFireTicks() > 0) flags |= 0x01;
        if (entity instanceof Player player) {
            if (player.isSneaking()) flags |= 0x02;
            if (player.isSprinting()) flags |= 0x08;
        }
        if (entity.isGlowing()) flags |= GLOWING_FLAG;
        return flags;
    }
}

package ink.ziip.championshipscore.platform.bukkit.player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.server.*;

import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Presentation only: never changes server game mode or makes entity-visibility decisions. */
public final class PlayerPacketPresentation extends PacketListenerAbstract
        implements Listener, AutoCloseable {
    private volatile boolean closed;
    private final Plugin plugin;
    private final PlatformScheduler scheduler;
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingViewerRefreshes = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicBoolean viewerRefreshScheduled =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final Set<UUID> spectatorPlayers = ConcurrentHashMap.newKeySet();
    private final java.util.Map<Integer, UUID> playerEntities = new ConcurrentHashMap<>();
    private final Map<UUID, Player> connections = new ConcurrentHashMap<>();
    private final Map<UUID, TabSnapshot> lastSnapshots = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean refreshRunning =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean initializePending =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final Map<UUID, Integer> entityIds = new ConcurrentHashMap<>();
    private final NativePlayerInfo nativeInfo = new NativePlayerInfo();
    private final io.papermc.paper.threadedregions.scheduler.ScheduledTask refreshTask;

    public PlayerPacketPresentation(Plugin plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
        scheduler = new PlatformScheduler(plugin);
        plugin.getServer()
                .getOnlinePlayers()
                .forEach(
                        player -> {
                            online.add(player.getUniqueId());
                            connections.put(player.getUniqueId(), player);
                        });
        PacketEvents.getAPI().getEventManager().registerListener(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player player : plugin.getServer().getOnlinePlayers())
            scheduler.runEntity(
                    player,
                    () -> {
                        if (!currentConnection(player)) return;
                        cacheMode(player, player.getGameMode());
                        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR)
                            PacketEvents.getAPI()
                                    .getPlayerManager()
                                    .sendPacket(
                                            player,
                                            new WrapperPlayServerChangeGameState(
                                                    WrapperPlayServerChangeGameState.Reason
                                                            .CHANGE_GAME_MODE,
                                                    2F));
                    });
        refresh(true);
        refreshTask = scheduler.runGlobalTimer(() -> refresh(false), 20L, 20L);
    }

    public static GameMode clientMode(GameMode mode) {
        return mode == GameMode.SPECTATOR ? GameMode.ADVENTURE : mode;
    }

    public static WrapperPlayServerPlayerInfoUpdate.PlayerInfo presentedInfo(
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo source, boolean online) {
        var copy = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(source);
        copy.setGameMode(clientMode(copy.getGameMode()));
        if (online) copy.setListed(true);
        return copy;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (closed) return;
        var type = event.getPacketType();
        if (type == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            var packet = new WrapperPlayServerPlayerInfoUpdate(event);
            // Copy entries: native broadcasts may share packet data across different recipients.
            packet.setEntries(
                    packet.getEntries().stream()
                            .map(
                                    entry ->
                                            presentedInfo(
                                                    entry, online.contains(entry.getProfileId())))
                            .toList());
            packet.getActions().add(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED);
        } else if (type == PacketType.Play.Server.ENTITY_METADATA) {
            var packet = new WrapperPlayServerEntityMetadata(event);
            UUID targetId = playerEntities.get(packet.getEntityId());
            boolean spectatorViewer = spectatorPlayers.contains(event.getUser().getUUID());
            boolean spectatorTarget = targetId != null && spectatorPlayers.contains(targetId);
            if (targetId != null && (spectatorViewer || spectatorTarget)) {
                packet.setEntityMetadata(
                        packet.getEntityMetadata().stream()
                                .map(
                                        data -> {
                                            if (data.getIndex() == 0
                                                    && data.getValue() instanceof Byte flags)
                                                return (com.github.retrooper.packetevents.protocol
                                                                        .entity.data.EntityData<
                                                                ?>)
                                                        new com.github.retrooper.packetevents
                                                                .protocol.entity.data.EntityData<>(
                                                                0,
                                                                com.github.retrooper.packetevents
                                                                        .protocol.entity.data
                                                                        .EntityDataTypes.BYTE,
                                                                clientFlags(
                                                                        flags,
                                                                        spectatorTarget,
                                                                        spectatorViewer));
                                            return data;
                                        })
                                .toList());
            }
        } else if (type == PacketType.Play.Server.PLAYER_INFO_REMOVE) {
            var packet = new WrapperPlayServerPlayerInfoRemove(event);
            packet.setProfileIds(
                    packet.getProfileIds().stream().filter(id -> !online.contains(id)).toList());
            if (packet.getProfileIds().isEmpty()) event.setCancelled(true);
        } else if (type == PacketType.Play.Server.CHANGE_GAME_STATE) {
            var packet = new WrapperPlayServerChangeGameState(event);
            if (packet.getReason() == WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE
                    && packet.getValue() == 3F) packet.setValue(2F);
        } else if (type == PacketType.Play.Server.JOIN_GAME) {
            var packet = new WrapperPlayServerJoinGame(event);
            packet.setGameMode(clientMode(packet.getGameMode()));
            packet.setPreviousGameMode(clientMode(packet.getPreviousGameMode()));
        } else if (type == PacketType.Play.Server.RESPAWN) {
            var packet = new WrapperPlayServerRespawn(event);
            packet.setGameMode(clientMode(packet.getGameMode()));
            packet.setPreviousGameMode(clientMode(packet.getPreviousGameMode()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        online.add(event.getPlayer().getUniqueId());
        connections.put(event.getPlayer().getUniqueId(), event.getPlayer());
        cacheMode(event.getPlayer(), event.getPlayer().getGameMode());
        // Native join lists honor canSee and omit already-hidden targets. Supply full signed
        // profiles.
        refresh(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!connections.remove(id, event.getPlayer())) return;
        online.remove(id);
        lastSnapshots.remove(id);
        spectatorPlayers.remove(id);
        Integer oldEntity = entityIds.remove(id);
        if (oldEntity != null) playerEntities.remove(oldEntity, id);
        // Native removal also honors canSee. Explicitly remove the retained Tab entry from
        // everyone.
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(id))
                scheduler.runEntity(
                        viewer,
                        () -> {
                            if (currentConnection(viewer) && !online.contains(id))
                                PacketEvents.getAPI()
                                        .getPlayerManager()
                                        .sendPacket(
                                                viewer, new WrapperPlayServerPlayerInfoRemove(id));
                        });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onModeChange(PlayerGameModeChangeEvent event) {
        if (!plugin.isEnabled()) return;
        cacheMode(event.getPlayer(), event.getNewGameMode());
        // Re-send metadata in both directions: entering Spectator exposes hidden bodies to this
        // viewer; leaving Spectator restores the game's real invisibility for the same entities.
        requestViewerRefresh(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        scheduler.runEntity(
                event.getPlayer(),
                () -> {
                    if (!currentConnection(event.getPlayer())) return;
                    playerEntities
                            .entrySet()
                            .removeIf(
                                    entry ->
                                            entry.getValue()
                                                    .equals(event.getPlayer().getUniqueId()));
                    cacheMode(event.getPlayer(), event.getPlayer().getGameMode());
                    requestViewerRefresh(event.getPlayer().getUniqueId());
                });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        if (!currentConnection(event.getPlayer())) return;
        cacheMode(event.getPlayer(), event.getPlayer().getGameMode());
        requestViewerRefresh(event.getPlayer().getUniqueId());
    }

    private boolean currentConnection(Player player) {
        return !closed && connections.get(player.getUniqueId()) == player;
    }

    private void requestViewerRefresh(UUID viewer) {
        pendingViewerRefreshes.add(viewer);
        queueRefresh();
    }

    private void queueRefresh() {
        if (closed || !viewerRefreshScheduled.compareAndSet(false, true)) return;
        try {
            scheduler.runGlobal(
                    () -> {
                        viewerRefreshScheduled.set(false);
                        if (closed || !refreshRunning.compareAndSet(false, true)) return;
                        boolean initialize = initializePending.getAndSet(false);
                        Set<UUID> changedViewers = Set.copyOf(pendingViewerRefreshes);
                        pendingViewerRefreshes.removeAll(changedViewers);
                        captureAndRefresh(initialize, changedViewers);
                    });
        } catch (RuntimeException failure) {
            viewerRefreshScheduled.set(false);
            if (!closed && plugin.isEnabled())
                plugin.getLogger()
                        .log(
                                java.util.logging.Level.WARNING,
                                "Unable to schedule player presentation refresh",
                                failure);
        }
    }

    private void cacheMode(Player player, org.bukkit.GameMode mode) {
        int entityId = player.getEntityId();
        Integer old = entityIds.put(player.getUniqueId(), entityId);
        if (old != null && old != entityId) playerEntities.remove(old, player.getUniqueId());
        playerEntities.put(entityId, player.getUniqueId());
        if (mode == org.bukkit.GameMode.SPECTATOR) spectatorPlayers.add(player.getUniqueId());
        else spectatorPlayers.remove(player.getUniqueId());
    }

    public static byte clientFlags(byte flags, boolean targetSpectator, boolean viewerSpectator) {
        return targetSpectator || viewerSpectator ? (byte) (flags & ~0x20) : flags;
    }

    private void refresh(boolean initialize) {
        if (initialize) initializePending.set(true);
        queueRefresh();
    }

    /** Capture on target threads, then send only changed targets on viewer threads. */
    private void captureAndRefresh(boolean initialize, Set<UUID> changedViewers) {
        List<Player> players = List.copyOf(connections.values());
        boolean flags = initialize || !changedViewers.isEmpty();
        List<CompletableFuture<TabSnapshot>> snapshots =
                players.stream()
                        .map(
                                target ->
                                        scheduler.supplyEntity(
                                                target,
                                                () -> {
                                                    if (!currentConnection(target)) return null;
                                                    int entityId = target.getEntityId();
                                                    cacheMode(target, target.getGameMode());
                                                    Object packet =
                                                            nativeInfo.capture(target, true);
                                                    Object state = nativeInfo.state(packet);
                                                    TabSnapshot previous =
                                                            lastSnapshots.get(target.getUniqueId());
                                                    boolean changed =
                                                            needsTabUpdate(
                                                                    previous == null
                                                                            ? null
                                                                            : previous.target(),
                                                                    target,
                                                                    previous == null
                                                                            ? null
                                                                            : previous.state(),
                                                                    state);
                                                    boolean newIdentity =
                                                            previous == null
                                                                    || previous.target() != target
                                                                    || !nativeInfo.sameIdentity(
                                                                            previous.state(),
                                                                            state);
                                                    return new TabSnapshot(
                                                            target,
                                                            entityId,
                                                            initialize || newIdentity
                                                                    ? packet
                                                                    : changed
                                                                            ? nativeInfo.capture(
                                                                                    target, false)
                                                                            : null,
                                                            state,
                                                            flags
                                                                    ? nativeInfo.sharedFlags(target)
                                                                    : null,
                                                            changed);
                                                }))
                        .toList();
        CompletableFuture.allOf(snapshots.toArray(CompletableFuture[]::new))
                .thenCompose(
                        ignored -> {
                            if (closed) return CompletableFuture.completedFuture(null);
                            var captured =
                                    snapshots.stream()
                                            .map(CompletableFuture::join)
                                            .filter(Objects::nonNull)
                                            .toList();
                            var changed =
                                    captured.stream()
                                            .filter(snapshot -> initialize || snapshot.changed())
                                            .toList();
                            for (TabSnapshot snapshot : captured)
                                if (currentConnection(snapshot.target()))
                                    lastSnapshots.put(snapshot.target().getUniqueId(), snapshot);
                            if (changed.isEmpty() && !flags)
                                return CompletableFuture.completedFuture(null);
                            var flagTargets =
                                    captured.stream()
                                            .filter(
                                                    snapshot ->
                                                            changedViewers.contains(
                                                                    snapshot.target()
                                                                            .getUniqueId()))
                                            .toList();
                            List<CompletableFuture<Void>> sends = new ArrayList<>();
                            for (Player viewer : players)
                                sends.add(
                                        scheduler.runEntityFuture(
                                                viewer,
                                                () -> {
                                                    if (!currentConnection(viewer)) return;
                                                    for (TabSnapshot snapshot : changed) {
                                                        if (currentConnection(snapshot.target())
                                                                && (initialize
                                                                        || !viewer.canSee(
                                                                                snapshot.target())))
                                                            nativeInfo.send(
                                                                    viewer, snapshot.packet());
                                                    }
                                                    if (!flags) return;
                                                    // Changed viewers need every entity's real
                                                    // flags; other viewers only need
                                                    // changed targets. This handles both directions
                                                    // without N repeated broadcasts.
                                                    for (TabSnapshot snapshot :
                                                            (initialize
                                                                            || changedViewers
                                                                                    .contains(
                                                                                            viewer
                                                                                                    .getUniqueId())
                                                                    ? captured
                                                                    : flagTargets)) {
                                                        if (!initialize
                                                                && !changedViewers.contains(
                                                                        viewer.getUniqueId())
                                                                && !changedViewers.contains(
                                                                        snapshot.target()
                                                                                .getUniqueId()))
                                                            continue;
                                                        if (currentConnection(snapshot.target())
                                                                && snapshot.flags() != null
                                                                && (viewer == snapshot.target()
                                                                        || viewer.canSee(
                                                                                snapshot.target())))
                                                            PacketEvents.getAPI()
                                                                    .getPlayerManager()
                                                                    .sendPacket(
                                                                            viewer,
                                                                            new WrapperPlayServerEntityMetadata(
                                                                                    snapshot
                                                                                            .entityId(),
                                                                                    List.of(
                                                                                            new com
                                                                                                    .github
                                                                                                    .retrooper
                                                                                                    .packetevents
                                                                                                    .protocol
                                                                                                    .entity
                                                                                                    .data
                                                                                                    .EntityData<>(
                                                                                                    0,
                                                                                                    com
                                                                                                            .github
                                                                                                            .retrooper
                                                                                                            .packetevents
                                                                                                            .protocol
                                                                                                            .entity
                                                                                                            .data
                                                                                                            .EntityDataTypes
                                                                                                            .BYTE,
                                                                                                    snapshot
                                                                                                            .flags()))));
                                                    }
                                                }));
                            return CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new));
                        })
                .whenComplete(
                        (ignored, failure) -> {
                            refreshRunning.set(false);
                            if (closed) return;
                            if (failure != null) {
                                // Retry with full profiles on the next timer, without a tight retry
                                // loop.
                                initializePending.set(true);
                                plugin.getLogger()
                                        .log(
                                                java.util.logging.Level.WARNING,
                                                "Player presentation refresh failed; retrying on"
                                                        + " next refresh",
                                                failure);
                            } else if (initializePending.get() || !pendingViewerRefreshes.isEmpty())
                                queueRefresh();
                        });
    }

    static boolean needsTabUpdate(
            Object previousConnection, Object connection, Object previousState, Object state) {
        return previousConnection != connection || !Objects.equals(previousState, state);
    }

    private record TabSnapshot(
            Player target,
            int entityId,
            Object packet,
            Object state,
            Byte flags,
            boolean changed) {}

    @Override
    public void close() {
        closed = true;
        refreshTask.cancel();
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        HandlerList.unregisterAll(this);
        online.clear();
        connections.clear();
        lastSnapshots.clear();
        pendingViewerRefreshes.clear();
        spectatorPlayers.clear();
        playerEntities.clear();
        entityIds.clear();
    }

    /**
     * Paper 26.2 packets preserve textures and signed chat sessions without reconstructing
     * profiles.
     */
    private static final class NativePlayerInfo {
        private final Method initialize;
        private final Method entries;
        private final Method profile;
        private final Method chatSession;
        private final Constructor<?> update;
        private final Class<?> packetType;
        private final Class<? extends Enum> actionType;
        private final Object sharedFlagsAccessor;
        private final Method entityData;
        private final Method dataGet;

        @SuppressWarnings("unchecked")
        private NativePlayerInfo() {
            try {
                Class<?> info =
                        Class.forName(
                                "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
                actionType = (Class<? extends Enum>) Class.forName(info.getName() + "$Action");
                initialize = info.getMethod("createPlayerInitializing", Collection.class);
                entries = info.getMethod("entries");
                Class<?> entry = Class.forName(info.getName() + "$Entry");
                profile = entry.getMethod("profile");
                chatSession = entry.getMethod("chatSession");
                update = info.getConstructor(EnumSet.class, Collection.class);
                packetType = Class.forName("net.minecraft.network.protocol.Packet");
                Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
                var field = entity.getDeclaredField("DATA_SHARED_FLAGS_ID");
                field.setAccessible(true);
                sharedFlagsAccessor = field.get(null);
                entityData = entity.getMethod("getEntityData");
                dataGet =
                        Class.forName("net.minecraft.network.syncher.SynchedEntityData")
                                .getMethod(
                                        "get",
                                        Class.forName(
                                                "net.minecraft.network.syncher.EntityDataAccessor"));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(
                        "Paper player-list packet API unavailable", failure);
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Object capture(Player target, boolean first) {
            try {
                Object handle = target.getClass().getMethod("getHandle").invoke(target);
                if (first) return initialize.invoke(null, List.of(handle));
                EnumSet actions = EnumSet.noneOf(actionType);
                for (String name :
                        List.of(
                                "UPDATE_GAME_MODE",
                                "UPDATE_LATENCY",
                                "UPDATE_LISTED",
                                "UPDATE_DISPLAY_NAME",
                                "UPDATE_HAT",
                                "UPDATE_LIST_ORDER")) actions.add(Enum.valueOf(actionType, name));
                return update.newInstance(actions, List.of(handle));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(
                        "Cannot capture authoritative player-list state", failure);
            }
        }

        private Object state(Object packet) {
            try {
                return ((List<?>) entries.invoke(packet)).getFirst();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot read native Tab state", failure);
            }
        }

        private boolean sameIdentity(Object first, Object second) {
            try {
                return Objects.equals(profile.invoke(first), profile.invoke(second))
                        && Objects.equals(chatSession.invoke(first), chatSession.invoke(second));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot compare signed Tab identities", failure);
            }
        }

        private byte sharedFlags(Player target) {
            try {
                Object handle = target.getClass().getMethod("getHandle").invoke(target);
                return (Byte) dataGet.invoke(entityData.invoke(handle), sharedFlagsAccessor);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(
                        "Cannot capture native player presentation flags", failure);
            }
        }

        private void send(Player viewer, Object packet) {
            try {
                Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
                Object connection = handle.getClass().getField("connection").get(handle);
                connection.getClass().getMethod("send", packetType).invoke(connection, packet);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(
                        "Cannot send authoritative player-list state", failure);
            }
        }
    }
}

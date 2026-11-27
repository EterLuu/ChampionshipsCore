package ink.ziip.championshipscore.platform.bukkit.player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.color.Color;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.waypoint.*;
import com.github.retrooper.packetevents.util.Either;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWaypoint;

import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Filters native waypoints without changing entity visibility, attributes, or Tab entries. */
public final class ScopedLocatorBar extends PacketListenerAbstract
        implements Listener, AutoCloseable {
    private static final Map<Plugin, ScopedLocatorBar> ACTIVE = new ConcurrentHashMap<>();

    private record Connection(Player player, LocatorBarState state) {}

    private final Plugin plugin;
    private final PlatformScheduler scheduler;
    private final Supplier<Map<UUID, Set<UUID>>> audienceSource;
    private final Supplier<Set<String>> worldSource;
    private volatile Set<UUID> enabledWorlds = Set.of();
    private final Map<UUID, Connection> connections = new ConcurrentHashMap<>();
    private volatile Map<UUID, Set<UUID>> audiences = Map.of();
    private volatile boolean closed;
    private final ScheduledTask task;
    private final boolean folia;
    private volatile long frameGeneration;

    private record Capture(
            Connection connection,
            UUID world,
            double x,
            double y,
            double z,
            double transmit,
            double receive,
            boolean hidden,
            boolean spectator,
            String name) {}

    public ScopedLocatorBar(
            Plugin plugin,
            Supplier<Map<UUID, Set<UUID>>> audienceSource,
            Supplier<Set<String>> worldSource) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
        this.audienceSource = audienceSource;
        this.worldSource = worldSource;
        ACTIVE.put(plugin, this);
        scheduler = new PlatformScheduler(plugin);
        folia = isFolia();
        plugin.getServer().getOnlinePlayers().forEach(this::connect);
        PacketEvents.getAPI().getEventManager().registerListener(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Flush markers cached before a mid-session plugin load, then let vanilla recreate
        // them through the filter. World rules are owned by the global region thread.
        for (var world : plugin.getServer().getWorlds()) {
            world.setGameRule(GameRules.LOCATOR_BAR, false);
        }
        refresh();
        task = scheduler.runGlobalTimer(this::refresh, 1L, 5L);
    }

    /** Called on the server/global thread; membership remains owned by the game managers. */
    public void refresh() {
        if (closed) return;
        Map<UUID, Set<UUID>> next = Map.copyOf(audienceSource.get());
        Map<UUID, Set<UUID>> previous = audiences;
        audiences = next;
        configureWorlds();
        if (folia) {
            captureFrame();
            return;
        }
        if (next.equals(previous)) return;
        for (Connection connection : connections.values()) {
            scheduler.runEntity(
                    connection.player(),
                    () -> {
                        if (!current(connection)) return;
                        connection
                                .state()
                                .reconcile(
                                        audience(connection.player().getUniqueId()),
                                        packet -> send(connection, packet));
                    });
        }
    }

    private void configureWorlds() {
        Set<String> names = Set.copyOf(worldSource.get());
        var enabled = new java.util.HashSet<UUID>();
        for (var world : plugin.getServer().getWorlds()) {
            boolean allow = names.contains(world.getName());
            if (allow) enabled.add(world.getUID());
            if (!Boolean.valueOf(allow).equals(world.getGameRuleValue(GameRules.LOCATOR_BAR))) {
                world.setGameRule(GameRules.LOCATOR_BAR, allow);
            }
        }
        enabledWorlds = Set.copyOf(enabled);
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException paper) {
            return false;
        }
    }

    private void captureFrame() {
        long generation = ++frameGeneration;
        List<CompletableFuture<Capture>> pending =
                connections.values().stream()
                        .map(
                                connection ->
                                        scheduler.supplyEntity(
                                                connection.player(),
                                                () -> {
                                                    if (!current(connection)) return null;
                                                    Player player = connection.player();
                                                    Location at = player.getLocation();
                                                    var transmit =
                                                            player.getAttribute(
                                                                    Attribute
                                                                            .WAYPOINT_TRANSMIT_RANGE);
                                                    var receive =
                                                            player.getAttribute(
                                                                    Attribute
                                                                            .WAYPOINT_RECEIVE_RANGE);
                                                    return new Capture(
                                                            connection,
                                                            at.getWorld().getUID(),
                                                            at.getX(),
                                                            at.getY(),
                                                            at.getZ(),
                                                            transmit == null
                                                                    ? 0
                                                                    : transmit.getValue(),
                                                            receive == null
                                                                    ? 0
                                                                    : receive.getValue(),
                                                            player.isInvisible()
                                                                    || player.isSneaking(),
                                                            player.getGameMode()
                                                                    == GameMode.SPECTATOR,
                                                            player.getScoreboardEntryName());
                                                }))
                        .toList();
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                .whenComplete(
                        (ignored, failure) -> {
                            if (closed || frameGeneration != generation) return;
                            List<Capture> captures =
                                    pending.stream()
                                            .filter(future -> !future.isCompletedExceptionally())
                                            .map(future -> future.getNow(null))
                                            .filter(java.util.Objects::nonNull)
                                            .toList();
                            for (Capture viewer : captures)
                                scheduler.runEntity(
                                        viewer.connection().player(),
                                        () -> {
                                            if (!current(viewer.connection())
                                                    || frameGeneration != generation) return;
                                            publishFrame(viewer, captures);
                                        });
                        });
    }

    private void publishFrame(Capture viewer, List<Capture> captures) {
        Player player = viewer.connection().player();
        Map<UUID, TrackedWaypoint> frame = new HashMap<>();
        Set<UUID> allowed = audience(player.getUniqueId());
        if (enabledWorlds.contains(viewer.world())
                && player.getWorld().getUID().equals(viewer.world())
                && viewer.receive() > 0) {
            for (Capture target : captures) {
                UUID id = target.connection().player().getUniqueId();
                if (!current(target.connection())
                        || !allowed.contains(id)
                        || !target.world().equals(viewer.world())
                        || target.spectator() && !viewer.spectator()
                        || target.hidden() && !viewer.spectator()
                        || target.transmit() <= 0
                        || !player.canSee(target.connection().player())) continue;
                double dx = target.x() - viewer.x(),
                        dy = target.y() - viewer.y(),
                        dz = target.z() - viewer.z();
                double distance = dx * dx + dy * dy + dz * dz;
                double range = Math.min(target.transmit(), viewer.receive());
                if (distance >= range * range) continue;
                int x = (int) Math.floor(target.x()),
                        y = (int) Math.floor(target.y()),
                        z = (int) Math.floor(target.z());
                WaypointInfo info;
                if (distance > 332 * 332)
                    info = new AzimuthWaypointInfo((float) Math.atan2(-dx, dz));
                else if (!player.isChunkSent(org.bukkit.Chunk.getChunkKey(x >> 4, z >> 4)))
                    info = new ChunkWaypointInfo(x >> 4, z >> 4);
                else info = new Vec3iWaypointInfo(new Vector3i(x, y, z));
                var team = player.getScoreboard().getEntryTeam(target.name());
                Color color =
                        team == null || team.color() == null
                                ? null
                                : new Color(team.color().value());
                frame.put(
                        id,
                        new TrackedWaypoint(
                                Either.createLeft(id),
                                new WaypointIcon(WaypointIcon.ICON_STYLE_DEFAULT, color),
                                info));
            }
        }
        viewer.connection().state().replace(frame, packet -> send(viewer.connection(), packet));
    }

    private Set<UUID> audience(UUID id) {
        return audiences.getOrDefault(id, Set.of());
    }

    private boolean current(Connection connection) {
        return !closed
                && plugin.isEnabled()
                && connections.get(connection.player().getUniqueId()) == connection;
    }

    private void send(Connection connection, LocatorBarState.Change packet) {
        if (current(connection))
            PacketEvents.getAPI()
                    .getPlayerManager()
                    .sendPacketSilently(
                            connection.player(),
                            new WrapperPlayServerWaypoint(packet.operation(), packet.waypoint()));
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (closed || event.isCancelled()) return;
        var type = event.getPacketType();
        if (type != PacketType.Play.Server.JOIN_GAME
                && type != PacketType.Play.Server.RESPAWN
                && type != PacketType.Play.Server.WAYPOINT) return;
        UUID id = event.getUser().getUUID();
        if (id == null) {
            if (type == PacketType.Play.Server.WAYPOINT) event.setCancelled(true);
            return;
        }
        Connection connection = connections.get(id);
        if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME
                || event.getPacketType() == PacketType.Play.Server.RESPAWN) {
            if (connection != null && event.getPlayer() == connection.player()) {
                connection.state().clear(packet -> send(connection, packet));
            }
            return;
        }
        if (event.getPacketType() != PacketType.Play.Server.WAYPOINT) return;
        if (connection == null || event.getPlayer() != connection.player()) {
            event.setCancelled(true);
            return;
        }
        var packet = new WrapperPlayServerWaypoint(event);
        var change =
                connection
                        .state()
                        .accept(packet.getOperation(), packet.getWaypoint(), audience(id));
        if (change == null) event.setCancelled(true);
        else {
            packet.setOperation(change.operation());
            packet.setWaypoint(change.waypoint());
        }
    }

    private void connect(Player player) {
        connections.put(player.getUniqueId(), new Connection(player, new LocatorBarState()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        connect(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        connections.computeIfPresent(
                event.getPlayer().getUniqueId(),
                (id, connection) -> connection.player() == event.getPlayer() ? null : connection);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldLoad(WorldLoadEvent event) {
        event.getWorld()
                .setGameRule(
                        GameRules.LOCATOR_BAR,
                        worldSource.get().contains(event.getWorld().getName()));
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        ACTIVE.remove(plugin, this);
        task.cancel();
        HandlerList.unregisterAll(this);
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        List<Connection> released = List.copyOf(connections.values());
        connections.clear();
        audiences = Map.of();
        // Disabling the world rule removes cached native markers even on Folia shutdown,
        // where a disabled plugin can no longer schedule entity tasks.
        Plugin packetPlugin = plugin.getServer().getPluginManager().getPlugin("packetevents");
        Plugin cleanupOwner = plugin.isEnabled() ? plugin : packetPlugin;
        if (cleanupOwner != null && cleanupOwner.isEnabled()) {
            if (folia) {
                PlatformScheduler cleanupScheduler = new PlatformScheduler(cleanupOwner);
                for (Connection connection : released) {
                    cleanupScheduler.runEntity(
                            connection.player(),
                            () -> {
                                if (ACTIVE.containsKey(plugin)
                                        || plugin.getServer()
                                                        .getPlayer(
                                                                connection.player().getUniqueId())
                                                != connection.player()) return;
                                connection
                                        .state()
                                        .clear(
                                                packet ->
                                                        PacketEvents.getAPI()
                                                                .getPlayerManager()
                                                                .sendPacketSilently(
                                                                        connection.player(),
                                                                        new WrapperPlayServerWaypoint(
                                                                                packet.operation(),
                                                                                packet
                                                                                        .waypoint())));
                            });
                }
            }
            Runnable cleanup =
                    () -> {
                        if (ACTIVE.containsKey(plugin)) return;
                        for (var world : plugin.getServer().getWorlds()) {
                            world.setGameRule(GameRules.LOCATOR_BAR, false);
                        }
                    };
            if (Bukkit.isGlobalTickThread()) cleanup.run();
            else new PlatformScheduler(cleanupOwner).runGlobal(cleanup);
        }
    }
}

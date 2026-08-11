package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import ink.ziip.championshipscore.protocol.MatchCommand;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.MatchState;
import ink.ziip.championshipscore.protocol.transport.DeliveryDisposition;
import ink.ziip.championshipscore.protocol.transport.InboundDelivery;
import ink.ziip.championshipscore.protocol.transport.MatchInboundMessage;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** Worker command dispatcher with match/epoch fencing and a single active-world ownership slot. */
final class WorkerMatchRegistry {
    private final Plugin plugin;
    private final WorkerConfig config;
    private final DurableEventOutbox events;
    private final WorkerReturnRouter returnRouter;
    private final WorkerWorldResetCoordinator worldReset;
    private final PlatformScheduler scheduler;
    private final Set<NamespacedKey> recipeKeys;
    private final Map<UUID, MatchManifest> manifests = new HashMap<>();
    private final Set<UUID> pendingObservations = ConcurrentHashMap.newKeySet();
    private WorkerMatchSession active;
    private boolean worldSlotConsumed;

    WorkerMatchRegistry(Plugin plugin, WorkerConfig config, DurableEventOutbox events,
                        WorkerReturnRouter returnRouter) {
        this.plugin = plugin;
        this.config = config;
        this.events = events;
        this.returnRouter = returnRouter;
        this.scheduler = new PlatformScheduler(plugin);
        this.recipeKeys = loadRecipeKeys(plugin);
        this.worldReset = config.allowWorldReuseWithoutReset()
                ? null : new WorkerWorldResetCoordinator(plugin, config);
    }

    CompletionStage<DeliveryDisposition> handle(InboundDelivery<MatchInboundMessage> delivery) {
        MatchInboundMessage message = delivery.payload();
        if (message instanceof MatchInboundMessage.Event) {
            return CompletableFuture.completedFuture(DeliveryDisposition.DEAD_LETTER);
        }
        if (message instanceof MatchInboundMessage.Manifest manifestMessage) {
            return scheduler.supplyGlobal(() -> acceptManifest(manifestMessage.manifest()))
                    .thenApply(ignored -> DeliveryDisposition.ACK);
        }
        MatchCommand command = ((MatchInboundMessage.Command) message).command();
        return scheduler.supplyGlobal(() -> dispatch(command)).thenCompose(stage -> stage)
                .thenApply(success -> success ? DeliveryDisposition.ACK : DeliveryDisposition.RETRY);
    }

    private synchronized boolean acceptManifest(MatchManifest manifest) {
        if (!config.workerId().equals(manifest.workerId())) {
            throw new IllegalArgumentException("Manifest targets a different worker: " + manifest.workerId());
        }
        MatchManifest known = manifests.get(manifest.matchId());
        if (known != null && manifest.epoch() < known.epoch()) return false;
        if (known != null && manifest.epoch() == known.epoch() && !known.equals(manifest)) {
            throw new IllegalArgumentException("Manifest changed without advancing its epoch: "
                    + manifest.matchId());
        }
        manifests.put(manifest.matchId(), manifest);
        return true;
    }

    private CompletionStage<Boolean> dispatch(MatchCommand command) {
        return switch (command.type()) {
            case PREPARE -> prepare(command);
            case START_COMMIT -> withActive(command, WorkerMatchSession::startCommit);
            case FORCE_END -> withActive(command, session -> session.finish("force-end"));
            case ABORT -> withActive(command, session -> session.abort(
                    command.attributes().getOrDefault("reason", "core-abort")));
            case ADD_SPECTATOR -> withActive(command, session -> session.addSpectator(
                    UUID.fromString(command.attributes().get("playerId")),
                    command.attributes().getOrDefault("username", "Spectator"),
                    Double.parseDouble(command.attributes().getOrDefault("points", "0"))));
            case REMOVE_SPECTATOR -> withActive(command, session -> session.removeSpectator(
                    UUID.fromString(command.attributes().get("playerId"))));
            case REMOVE_PARTICIPANTS -> withActive(command, session -> session.removeParticipants(
                    parsePlayers(command.attributes().get("players"))));
            case SHUTDOWN_WHEN_IDLE -> CompletableFuture.completedFuture(active == null || active.state().terminal());
        };
    }

    private synchronized CompletionStage<Boolean> prepare(MatchCommand command) {
        MatchManifest manifest = manifests.get(command.matchId());
        if (manifest == null || manifest.epoch() != command.epoch()) {
            return CompletableFuture.completedFuture(false);
        }
        if (active != null) {
            if (active.matchId().equals(command.matchId()) && active.epoch() == command.epoch()) {
                return active.prepare();
            }
            if (!active.state().terminal()) return CompletableFuture.completedFuture(false);
        }
        active = new WorkerMatchSession(plugin, config, manifest, events, returnRouter,
                worldReset == null ? () -> { } : worldReset::request);
        if (worldSlotConsumed && !config.allowWorldReuseWithoutReset()) {
            return active.rejectPreparation("world-slot-requires-reset");
        }
        worldSlotConsumed = true;
        return active.prepare();
    }

    private synchronized CompletionStage<Boolean> withActive(
            MatchCommand command,
            java.util.function.Function<WorkerMatchSession, CompletionStage<Boolean>> action) {
        if (active == null || !active.matchId().equals(command.matchId()) || active.epoch() != command.epoch()) {
            return CompletableFuture.completedFuture(false);
        }
        return action.apply(active);
    }

    void onJoin(Player player) {
        scheduler.runEntity(player, () -> player.discoverRecipes(recipeKeys));
        WorkerMatchSession session;
        synchronized (this) {
            session = active;
        }
        if (session != null && session.owns(player.getUniqueId())) {
            returnRouter.cancel(player.getUniqueId());
            session.playerArrived(player).thenAccept(accepted -> {
                if (!accepted) returnRouter.request(player);
            }).exceptionally(error -> {
                plugin.getLogger().warning("Unable to admit Bingo player " + player.getUniqueId()
                        + ": " + error.getMessage());
                returnRouter.request(player);
                return null;
            });
            return;
        }
        // A proxy may reconnect a player to their last server. The worker is never a lobby: anyone
        // without live match ownership is immediately returned to Core, including after settlement.
        returnRouter.request(player);
    }

    void onQuit(Player player) {
        returnRouter.cancel(player.getUniqueId());
        pendingObservations.remove(player.getUniqueId());
        WorkerMatchSession session;
        synchronized (this) {
            session = active;
        }
        if (session != null) session.playerLeft(player);
    }

    void requestVoluntaryLeave(Player player) {
        WorkerMatchSession session;
        synchronized (this) {
            session = active;
        }
        if (session == null || !session.isPlaying(player.getUniqueId())) {
            player.sendMessage(Component.text("[自由游玩] ", NamedTextColor.GREEN)
                    .append(Component.text("你当前没有参与任何游戏。", NamedTextColor.GRAY)));
            return;
        }
        session.requestVoluntaryLeave(player);
    }

    private static Set<UUID> parsePlayers(String value) {
        if (value == null || value.isBlank()) return Set.of();
        Set<UUID> players = new LinkedHashSet<>();
        for (String token : value.split(",")) players.add(UUID.fromString(token));
        return Set.copyOf(players);
    }

    void observe(Player player) {
        WorkerMatchSession session;
        synchronized (this) {
            session = active;
        }
        if (session != null) session.observe(player);
    }

    void requestObserve(Player player) {
        UUID playerId = player.getUniqueId();
        if (!pendingObservations.add(playerId)) return;
        scheduler.runEntityLater(player, () -> {
            pendingObservations.remove(playerId);
            observe(player);
        }, 1L);
    }

    void observeAdvancement(Player player, org.bukkit.advancement.Advancement advancement) {
        WorkerMatchSession session;
        synchronized (this) {
            session = active;
        }
        if (session != null) session.observeAdvancement(player, advancement);
    }

    Location respawnLocation(Player player) {
        WorkerMatchSession session;
        synchronized (this) {
            session = active;
        }
        return session == null ? null : session.respawnLocation(player.getUniqueId());
    }

    void restoreAfterRespawn(Player player) {
        WorkerMatchSession session;
        synchronized (this) {
            session = active;
        }
        if (session != null) session.restoreAfterRespawn(player);
    }

    synchronized boolean isPlaying(UUID playerId) {
        return active != null && active.isPlaying(playerId);
    }

    synchronized boolean isRunningPlayer(UUID playerId) {
        return active != null && active.isRunningPlayer(playerId);
    }

    synchronized String resolveChampionshipPlaceholder(UUID playerId, String params) {
        return active == null ? null : active.resolveChampionshipPlaceholder(playerId, params);
    }

    synchronized WorkerPlayerPresentation playerPresentation(UUID playerId) {
        return active == null ? WorkerPlayerPresentation.spectator() : active.playerPresentation(playerId);
    }

    private static Set<NamespacedKey> loadRecipeKeys(Plugin plugin) {
        Set<NamespacedKey> keys = new LinkedHashSet<>();
        plugin.getServer().recipeIterator().forEachRemaining(recipe -> {
            if (recipe instanceof Keyed keyed) keys.add(keyed.getKey());
        });
        return Set.copyOf(keys);
    }

    synchronized boolean canPickupCard(UUID playerId, int teamId) {
        return active != null && active.canPickupCard(playerId, teamId);
    }

    synchronized boolean canUseBingoUi(UUID playerId) {
        return active != null && active.canUseBingoUi(playerId);
    }

    synchronized boolean isProtectedParticipant(UUID playerId) {
        return active != null && active.isProtectedParticipant(playerId);
    }

    synchronized boolean isFinalCountdownPlayer(UUID playerId) {
        return active != null && active.isFinalCountdownPlayer(playerId);
    }

    void openCard(Player player, Integer selectedTeamId) {
        WorkerMatchSession session;
        synchronized (this) {
            session = active;
        }
        if (session != null) session.openCard(player, selectedTeamId);
    }

    Integer boundCardTeam(org.bukkit.inventory.ItemStack item) {
        WorkerMatchSession session;
        synchronized (this) {
            session = active;
        }
        return session == null ? null : session.boundCardTeam(item);
    }

    void openTeammates(Player player) {
        WorkerMatchSession session;
        synchronized (this) {
            session = active;
        }
        if (session != null) session.openTeammates(player);
    }

    void teleportToTeammate(Player player, UUID targetId) {
        WorkerMatchSession session;
        synchronized (this) {
            session = active;
        }
        if (session != null) session.teleportToTeammate(player, targetId);
    }

    synchronized boolean sameTeam(UUID first, UUID second) {
        if (active == null) return false;
        MatchManifest manifest = manifests.get(active.matchId());
        if (manifest == null) return false;
        Integer firstTeam = manifest.participants().stream().filter(player -> player.uuid().equals(first))
                .map(player -> player.teamId()).findFirst().orElse(null);
        Integer secondTeam = manifest.participants().stream().filter(player -> player.uuid().equals(second))
                .map(player -> player.teamId()).findFirst().orElse(null);
        return firstTeam != null && firstTeam.equals(secondTeam);
    }

    CompletionStage<Boolean> shutdown() {
        WorkerMatchSession session;
        synchronized (this) {
            session = active;
        }
        if (session != null && !session.state().terminal()) return session.abort("worker-shutdown");
        return CompletableFuture.completedFuture(true);
    }
}

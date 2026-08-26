package ink.ziip.championshipscore.authbridge.bridge;

import ink.ziip.championshipscore.api.player.entry.PlayerUuidMigration;
import ink.ziip.championshipscore.api.player.event.PlayerIdentityMigrationEvent;
import ink.ziip.championshipscore.api.player.event.PlayerNameChangeEvent;
import ink.ziip.championshipscore.authbridge.BridgeText;
import ink.ziip.championshipscore.authbridge.authme.AuthMeHashStore;
import ink.ziip.championshipscore.authbridge.model.BridgeChange;
import ink.ziip.championshipscore.authbridge.model.BridgeChangeBatch;
import ink.ziip.championshipscore.authbridge.model.BridgeControlJob;
import ink.ziip.championshipscore.authbridge.model.BridgeControlPlayer;
import ink.ziip.championshipscore.authbridge.model.BridgeSnapshot;
import ink.ziip.championshipscore.authbridge.model.BridgeSnapshotPlayer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Synchronizes website access state. Authlib supplies login UUIDs; this class verifies and migrates them. */
public final class BridgeSynchronizer implements Runnable {
    private final Plugin plugin;
    private final BridgeApiClient client;
    private final AuthMeHashStore authMe;
    private final LocalAccessState state;
    private final BridgeUuidResolver uuidResolver;
    private final String usernameUpdatedMessage;
    private final String accessRevokedMessage;
    private final AtomicBoolean running = new AtomicBoolean();
    private int consecutiveFailures;
    private long lastUnavailableLogAt;

    public BridgeSynchronizer(Plugin plugin, BridgeApiClient client, AuthMeHashStore authMe,
                              LocalAccessState state, String usernameUpdatedMessage,
                              String accessRevokedMessage) {
        this(plugin, client, authMe, state, usernameUpdatedMessage, accessRevokedMessage,
                new BridgeUuidResolver(java.time.Duration.ofSeconds(5), java.time.Duration.ofSeconds(10)));
    }

    BridgeSynchronizer(Plugin plugin, BridgeApiClient client, AuthMeHashStore authMe,
                       LocalAccessState state, String usernameUpdatedMessage,
                       String accessRevokedMessage, BridgeUuidResolver uuidResolver) {
        this.plugin = plugin;
        this.client = client;
        this.authMe = authMe;
        this.state = state;
        this.uuidResolver = uuidResolver;
        this.usernameUpdatedMessage = usernameUpdatedMessage;
        this.accessRevokedMessage = accessRevokedMessage;
    }

    @Override
    public void run() {
        if (!running.compareAndSet(false, true)) return;
        try {
            applyBindingSnapshotIfNeeded();
            retryPendingAcknowledgement();
            retryPendingControlCompletion();
            if (!processControlJob()) return;

            BridgeChangeBatch batch = client.changesAfter(state.cursor());
            validateBatch(batch);
            for (BridgeChange change : batch.changes()) {
                if (change == null) throw new IllegalStateException("Bridge API response contains a null change");
                apply(change);
            }
            state.advance(batch.nextCursor());
            client.acknowledge(batch.nextCursor());
            state.confirmAcknowledged(batch.nextCursor());
            consecutiveFailures = 0;
        } catch (Exception exception) {
            logFailure(exception);
        } finally {
            running.set(false);
        }
    }

    /**
     * Imports accounts that were bound before the bridge began emitting
     * PROVISION events. This is intentionally one-shot and is persisted only
     * after every account has been applied successfully.
     */
    private void applyBindingSnapshotIfNeeded() throws Exception {
        if (state.bindingSnapshotApplied()) return;
        BridgeSnapshot snapshot = client.snapshot();
        if (snapshot == null || snapshot.players() == null) {
            throw new IllegalStateException("Bridge snapshot is incomplete");
        }
        for (BridgeSnapshotPlayer player : snapshot.players()) {
            String username = requireUsername(player.username());
            String accountId = requireAccountId(player.accountId());
            UUID uuid = resolveUuid(username, player.uuidSource(), player.minecraftUuid());
            authMe.provision(username, requirePasswordHash(player.passwordHash()), uuid);
            state.recordIdentity(username, accountId, uuid.toString());
            state.setAuthVersion(username, player.version());
        }
        state.markBindingSnapshotApplied();
        plugin.getLogger().info("Imported " + snapshot.players().size()
                + " bound account(s) into AuthMe from the bridge snapshot.");
    }

    private void logFailure(Exception failure) {
        consecutiveFailures++;
        if (!isWebUnavailable(failure)) {
            plugin.getLogger().log(Level.WARNING, "Auth bridge synchronization failed", failure);
            return;
        }
        long now = System.currentTimeMillis();
        if (consecutiveFailures == 1 || now - lastUnavailableLogAt >= 60_000L) {
            lastUnavailableLogAt = now;
            plugin.getLogger().warning("Auth bridge web service unavailable; retrying (attempt "
                    + consecutiveFailures + ")");
        }
    }

    private static boolean isWebUnavailable(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ConnectException || current instanceof HttpTimeoutException
                    || current instanceof java.net.UnknownHostException || current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void retryPendingAcknowledgement() throws Exception {
        LocalAccessState.PendingAcknowledgement pending = state.pendingAcknowledgement();
        if (pending == null) return;
        client.acknowledge(pending.cursor());
        state.confirmAcknowledged(pending.cursor());
    }

    private void retryPendingControlCompletion() throws Exception {
        LocalAccessState.ControlCompletion pending = state.pendingControlCompletion();
        if (pending == null) return;
        client.completeControlJob(pending.jobId(), true, pending.result(), null);
        state.finishControlCompletion(pending.jobId());
    }

    private static void validateBatch(BridgeChangeBatch batch) {
        if (batch == null) throw new IllegalStateException("Bridge API returned an empty response");
        if (batch.changes() == null) throw new IllegalStateException("Bridge API response is missing changes");
        if (batch.nextCursor() == null || batch.nextCursor().isBlank()) {
            throw new IllegalStateException("Bridge API response is missing nextCursor");
        }
    }

    private void apply(BridgeChange change) {
        String username = requireUsername(change.authmeUsername());
        String accountId = requireAccountId(change.accountId());
        String operation = change.operation();
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("Bridge change is missing operation (id=" + change.id() + ")");
        }
        boolean requiresUuid = Set.of("PROVISION", "USERNAME_UPDATED", "WHITELISTED")
                .contains(operation);
        UUID uuid = requiresUuid ? resolveUuid(username, change.uuidSource(), change.minecraftUuid()) : null;
        switch (operation) {
            case "PROVISION" -> {
                applyPasswordIfCurrent(change, requireUuid(uuid));
                state.recordIdentity(username, accountId, uuid.toString());
            }
            case "PASSWORD_UPDATED" -> applyPasswordIfCurrent(change);
            case "USERNAME_UPDATED" -> {
                String oldUsername = requireUsername(change.oldAuthmeUsername());
                authMe.rename(oldUsername, username, requireUuid(uuid));
                state.rename(oldUsername, username, accountId, uuid.toString());
                notifyCoreNameChange(oldUsername, username, uuid);
                kick(oldUsername, replacePlaceholders(usernameUpdatedMessage, oldUsername, username));
            }
            case "WHITELISTED" -> state.recordIdentity(username, accountId, uuid.toString());
            case "REVOKED" -> {
                state.revoke(username);
                authMe.remove(username);
                kick(username, accessRevokedMessage);
            }
            // Bungee performs ban admission and disconnects existing sessions.
            case "BANNED", "UNBANNED" -> {
            }
            default -> throw new IllegalArgumentException("Unknown bridge operation: " + operation);
        }
    }

    /** cc-web chooses the source; Bridge only executes that explicit source. */
    private UUID resolveUuid(String username, String source, String suppliedUuid) {
        return uuidResolver.resolve(username, source, suppliedUuid);
    }

    private void applyPasswordIfCurrent(BridgeChange change, UUID uuid) {
        if (change.version() < state.authVersion(change.authmeUsername())) return;
        authMe.provision(change.authmeUsername(), change.passwordHash(), uuid);
        state.setAuthVersion(change.authmeUsername(), change.version());
    }

    private void applyPasswordIfCurrent(BridgeChange change) {
        if (change.version() < state.authVersion(change.authmeUsername())) return;
        authMe.updatePassword(change.authmeUsername(), change.passwordHash());
        state.setAuthVersion(change.authmeUsername(), change.version());
    }

    private boolean processControlJob() throws Exception {
        var envelope = client.controlJob();
        if (envelope == null || envelope.job() == null) {
            return !state.maintenanceInProgress();
        }
        BridgeControlJob job = envelope.job();
        parseUuid(job.id(), "control job id");
        state.beginMaintenance(job.id());
        if (!serverIsEmpty()) return false;
        boolean identityMigration = "IDENTITY_MODE_MIGRATION".equals(job.operation());
        try {
            Map<String, Object> result = switch (job.operation()) {
                case "IDENTITY_MODE_MIGRATION" -> migrateIdentities(job);
                case "AUTHME_IMPORT_MISSING" -> importMissing(job);
                case "AUTHME_REMOVE_UNKNOWN" -> removeUnknown(job);
                default -> throw new IllegalArgumentException("Unknown control operation: " + job.operation());
            };
            state.stageControlCompletion(job.id(), result);
            client.completeControlJob(job.id(), true, result, null);
            state.finishControlCompletion(job.id());
            return true;
        } catch (Exception failure) {
            try {
                client.completeControlJob(job.id(), false, null,
                        failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()));
            } catch (Exception completionFailure) {
                failure.addSuppressed(completionFailure);
            }
            if (!identityMigration) {
                try {
                    state.clearMaintenance(job.id());
                } catch (Exception stateFailure) {
                    failure.addSuppressed(stateFailure);
                }
            }
            plugin.getLogger().log(Level.WARNING, "Bridge control job failed: " + job.id(), failure);
            return false;
        }
    }

    private Map<String, Object> migrateIdentities(BridgeControlJob job) {
        List<PlayerUuidMigration> coreMigrations = new ArrayList<>();
        List<AuthMeHashStore.UuidMigration> authMeMigrations = new ArrayList<>();
        Map<String, String> uuidsByAccount = new LinkedHashMap<>();
        Set<String> accountIds = new LinkedHashSet<>();
        Set<UUID> targetUuids = new LinkedHashSet<>();
        for (BridgeControlPlayer player : requirePlayers(job)) {
            String username = requireUsername(player.username());
            String accountId = requireAccountId(player.accountId());
            if (!accountIds.add(accountId)) throw new IllegalArgumentException("Duplicate accountId in identity migration");
            UUID fromUuid = parseUuid(player.fromUuid(), "fromUuid");
            UUID toUuid = parseUuid(player.toUuid(), "toUuid");
            if (!targetUuids.add(toUuid)) throw new IllegalArgumentException("Duplicate toUuid in identity migration");
            coreMigrations.add(new PlayerUuidMigration(username, fromUuid, toUuid));
            authMeMigrations.add(new AuthMeHashStore.UuidMigration(username, fromUuid, toUuid));
            uuidsByAccount.put(accountId, toUuid.toString());
        }

        int coreChanged = notifyCoreIdentityMigration(coreMigrations);
        AuthMeHashStore.ReconcileResult authMeResult = authMe.migrateUuids(authMeMigrations);
        try {
            state.migrateIdentityUuids(uuidsByAccount);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unable to save migrated access state", failure);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("players", coreMigrations.size());
        result.put("coreChanged", coreChanged);
        result.put("authMeChanged", authMeResult.changed());
        result.put("authMeMissing", authMeResult.missing());
        return result;
    }

    private Map<String, Object> importMissing(BridgeControlJob job) {
        List<AuthMeHashStore.ProvisionAccount> accounts = requirePlayers(job).stream()
                .map(player -> new AuthMeHashStore.ProvisionAccount(
                        requireUsername(player.username()), requirePasswordHash(player.passwordHash()),
                        resolveControlPlayerUuid(player)))
                .toList();
        AuthMeHashStore.ReconcileResult imported = authMe.importMissing(accounts);
        return Map.of("examined", imported.examined(), "imported", imported.changed());
    }

    private UUID resolveControlPlayerUuid(BridgeControlPlayer player) {
        return resolveUuid(requireUsername(player.username()), player.uuidSource(), player.minecraftUuid());
    }

    private Map<String, Object> removeUnknown(BridgeControlJob job) {
        Set<String> desired = requirePlayers(job).stream()
                .map(player -> requireUsername(player.username()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        AuthMeHashStore.ReconcileResult removed = authMe.removeUnknown(desired);
        return Map.of("examined", removed.examined(), "removed", removed.changed());
    }

    private boolean serverIsEmpty() throws Exception {
        return Bukkit.getScheduler().callSyncMethod(plugin, () -> Bukkit.getOnlinePlayers().isEmpty())
                .get(10, TimeUnit.SECONDS);
    }

    private void kick(String username, String reason) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            var player = Bukkit.getPlayerExact(username);
            if (player != null) {
                player.kick(BridgeText.component(reason == null || reason.isBlank()
                        ? accessRevokedMessage : reason));
            }
        });
    }

    private static String replacePlaceholders(String template, String oldUsername, String newUsername) {
        return (template == null ? "" : template)
                .replace("%old%", oldUsername == null ? "" : oldUsername)
                .replace("%new%", newUsername == null ? "" : newUsername);
    }

    private void notifyCoreNameChange(String oldUsername, String newUsername, UUID replacementUuid) {
        PlayerNameChangeEvent event = new PlayerNameChangeEvent(oldUsername, newUsername, replacementUuid);
        Bukkit.getPluginManager().callEvent(event);
        awaitCore(event.completion(), "name migration");
    }

    private int notifyCoreIdentityMigration(List<PlayerUuidMigration> players) {
        PlayerIdentityMigrationEvent event = new PlayerIdentityMigrationEvent(players);
        Bukkit.getPluginManager().callEvent(event);
        return awaitCore(event.completion(), "identity migration");
    }

    private static <T> T awaitCore(java.util.concurrent.CompletableFuture<T> completion, String operation) {
        try {
            return completion.get(60, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Core " + operation, exception);
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Timed out waiting for Core " + operation, exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("Core " + operation + " failed", exception.getCause());
        }
    }

    private static List<BridgeControlPlayer> requirePlayers(BridgeControlJob job) {
        if (job.players() == null) throw new IllegalArgumentException("Control job has no player list");
        return job.players();
    }

    private static UUID parseUuid(String value, String field) {
        try {
            return BridgeUuidResolver.parseUuid(value, field);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid " + field, exception);
        }
    }

    private static String requireUsername(String username) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,16}")) {
            throw new IllegalArgumentException("Invalid Minecraft username");
        }
        return username;
    }

    private static String requireAccountId(String accountId) {
        parseUuid(accountId, "accountId");
        return accountId;
    }

    private static String requirePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Missing password hash");
        }
        return passwordHash;
    }

    private static UUID requireUuid(UUID uuid) {
        if (uuid == null) throw new IllegalStateException("Minecraft UUID is required for this bridge operation");
        return uuid;
    }
}

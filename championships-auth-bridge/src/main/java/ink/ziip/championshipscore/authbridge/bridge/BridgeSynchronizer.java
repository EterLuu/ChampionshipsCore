package ink.ziip.championshipscore.authbridge.bridge;

import ink.ziip.championshipscore.api.player.event.PlayerNameChangeEvent;
import ink.ziip.championshipscore.authbridge.authme.AuthMeHashStore;
import ink.ziip.championshipscore.authbridge.model.BridgeChange;
import ink.ziip.championshipscore.authbridge.model.BridgeChangeBatch;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class BridgeSynchronizer implements Runnable {
    private final Plugin plugin;
    private final BridgeApiClient client;
    private final AuthMeHashStore authMe;
    private final LocalAccessState state;
    private final AtomicBoolean running = new AtomicBoolean();

    public BridgeSynchronizer(Plugin plugin, BridgeApiClient client, AuthMeHashStore authMe, LocalAccessState state) {
        this.plugin = plugin;
        this.client = client;
        this.authMe = authMe;
        this.state = state;
    }

    @Override
    public void run() {
        if (!running.compareAndSet(false, true)) return;
        try {
            BridgeChangeBatch batch = client.changesAfter(state.cursor());
            if (batch == null) throw new IllegalStateException("Bridge API returned an empty response");
            if (batch.changes() == null) throw new IllegalStateException("Bridge API response is missing changes");
            if (batch.nextCursor() == null || batch.nextCursor().isBlank()) {
                throw new IllegalStateException("Bridge API response is missing nextCursor");
            }
            for (BridgeChange change : batch.changes()) {
                if (change == null) throw new IllegalStateException("Bridge API response contains a null change");
                apply(change);
            }
            state.advance(batch.nextCursor());
            client.acknowledge(batch.nextCursor());
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Auth bridge synchronization failed", exception);
        } finally {
            running.set(false);
        }
    }

    private void apply(BridgeChange change) {
        String username = change.authmeUsername();
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Bridge change is missing authmeUsername");
        String accountId = requireAccountId(change);
        String operation = change.operation();
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("Bridge change is missing operation (id=" + change.id() + ")");
        }
        switch (operation) {
            case "PROVISION" -> {
                applyPasswordIfCurrent(change);
                state.whitelist(username, accountId);
            }
            case "PASSWORD_UPDATED" -> applyPasswordIfCurrent(change);
            case "USERNAME_UPDATED" -> {
                String oldUsername = change.oldAuthmeUsername();
                if (oldUsername == null || oldUsername.isBlank()) throw new IllegalArgumentException("Bridge name change is missing old authmeUsername");
                authMe.rename(oldUsername, username);
                state.rename(oldUsername, username, accountId);
                notifyCore(oldUsername, username);
                kick(oldUsername, "你的 Minecraft 玩家名已修改，请使用新名称重新登录。" );
            }
            case "WHITELISTED" -> state.whitelist(username, accountId);
            case "REVOKED" -> {
                state.revoke(username);
                authMe.remove(username);
                kick(username, "你的服务器准入资格已被撤销。");
            }
            case "BANNED" -> {
                state.ban(username, change.reason(), change.expiresAt());
                kick(username, change.reason());
            }
            case "UNBANNED" -> state.unban(username);
            default -> throw new IllegalArgumentException("Unknown bridge operation: " + operation);
        }
    }

    private static String requireAccountId(BridgeChange change) {
        String accountId = change.accountId();
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Bridge change is missing accountId (id=" + change.id() + ")");
        }
        return accountId;
    }

    private void applyPasswordIfCurrent(BridgeChange change) {
        if (change.version() < state.authVersion(change.authmeUsername())) return;
        authMe.provision(change.authmeUsername(), change.passwordHash());
        state.setAuthVersion(change.authmeUsername(), change.version());
    }

    private void kick(String username, String reason) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            var player = Bukkit.getPlayerExact(username);
            if (player != null) player.kick(net.kyori.adventure.text.Component.text(reason == null ? "访问已被撤销" : reason));
        });
    }

    private void notifyCore(String oldUsername, String newUsername) {
        UUID replacementUuid = Bukkit.getServer().getOnlineMode()
                ? null
                : UUID.nameUUIDFromBytes(("OfflinePlayer:" + newUsername).getBytes(StandardCharsets.UTF_8));
        PlayerNameChangeEvent event = new PlayerNameChangeEvent(oldUsername, newUsername, replacementUuid);
        Bukkit.getPluginManager().callEvent(event);
        try {
            if (!event.completion().get(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Core rejected approved name migration");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Core name migration", exception);
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Timed out waiting for Core name migration", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("Core name migration failed", exception.getCause());
        }
    }
}

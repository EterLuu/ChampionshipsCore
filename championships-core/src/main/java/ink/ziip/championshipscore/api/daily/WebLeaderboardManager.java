package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Optional, periodic publisher of in-memory DAILY leaderboard caches to cc-web. */
public final class WebLeaderboardManager extends BaseManager {
    private static final long MIN_UPLOAD_INTERVAL_SECONDS = 60L;
    private final DailyManager dailyManager;
    private final DailyStatsManager statsManager;
    private BukkitTask task;
    private WebLeaderboardApiClient client;
    private final AtomicBoolean sending = new AtomicBoolean();
    private int consecutiveFailures;
    private long lastUnavailableLogAt;

    public WebLeaderboardManager(@NotNull ChampionshipsCore plugin,
                                 @NotNull DailyManager dailyManager,
                                 @NotNull DailyStatsManager statsManager) {
        super(plugin);
        this.dailyManager = dailyManager;
        this.statsManager = statsManager;
    }

    @Override
    public void load() {
        if (!Boolean.TRUE.equals(CCConfig.WEB_LEADERBOARD_SYNC_ENABLED)) return;
        try {
            long interval = Math.max(MIN_UPLOAD_INTERVAL_SECONDS,
                    CCConfig.WEB_LEADERBOARD_SYNC_INTERVAL_SECONDS);
            client = new WebLeaderboardApiClient(
                    CCConfig.WEB_LEADERBOARD_SYNC_BASE_URL,
                    CCConfig.WEB_LEADERBOARD_SYNC_KEY_ID,
                    CCConfig.WEB_LEADERBOARD_SYNC_HMAC_SECRET,
                    Boolean.TRUE.equals(CCConfig.WEB_LEADERBOARD_SYNC_ALLOW_INSECURE_PRIVATE_HTTP),
                    CCConfig.WEB_LEADERBOARD_SYNC_CONNECT_TIMEOUT_SECONDS,
                    CCConfig.WEB_LEADERBOARD_SYNC_REQUEST_TIMEOUT_SECONDS);
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::publish, 600L, interval * 20L);
            plugin.getLogger().info(Utils.formatModuleLog("WebLeaderboard", "同步",
                    "已启用 | 周期=" + interval + "秒"));
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("WebLeaderboard", "配置",
                    "排行榜同步配置无效，同步未启用"), exception);
        }
    }

    @Override
    public void unload() {
        if (task != null) task.cancel();
        task = null;
        client = null;
        sending.set(false);
        consecutiveFailures = 0;
    }

    private void publish() {
        if (!plugin.isEnabled() || client == null) return;
        WebLeaderboardSnapshot snapshot = WebLeaderboardSnapshot.from(dailyManager, statsManager);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> send(snapshot));
    }

    private void send(@NotNull WebLeaderboardSnapshot snapshot) {
        if (!plugin.isEnabled() || client == null || !sending.compareAndSet(false, true)) return;
        try {
            client.submit(snapshot);
            if (consecutiveFailures != 0) {
                plugin.getLogger().info(Utils.formatModuleLog("WebLeaderboard", "恢复",
                        "排行榜同步已恢复"));
            }
            consecutiveFailures = 0;
        } catch (Exception exception) {
            logFailure(exception);
        } finally {
            sending.set(false);
        }
    }

    private void logFailure(@NotNull Exception failure) {
        consecutiveFailures++;
        if (!isUnavailable(failure)) {
            plugin.getLogger().log(Level.WARNING, Utils.formatModuleLog("WebLeaderboard", "同步",
                    "排行榜同步失败 | 次数=" + consecutiveFailures), failure);
            return;
        }
        long now = System.currentTimeMillis();
        if (consecutiveFailures == 1 || now - lastUnavailableLogAt >= 60_000L) {
            lastUnavailableLogAt = now;
            plugin.getLogger().warning(Utils.formatModuleLog("WebLeaderboard", "同步",
                    "cc-web 暂时不可用，稍后重试 | 次数=" + consecutiveFailures));
        }
    }

    private static boolean isUnavailable(@NotNull Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof java.io.IOException || current instanceof RuntimeException runtime
                    && runtime.getMessage() != null && runtime.getMessage().startsWith("Leaderboard API returned HTTP 5")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

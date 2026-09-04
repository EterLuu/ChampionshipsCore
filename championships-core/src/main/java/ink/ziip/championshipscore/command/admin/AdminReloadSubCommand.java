package ink.ziip.championshipscore.command.admin;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdminReloadSubCommand extends BaseSubCommand {
    private static final AtomicBoolean RELOAD_IN_PROGRESS = new AtomicBoolean();

    public AdminReloadSubCommand() {
        super("reload", "重载插件配置", "/cc admin reload --confirm");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("--confirm")) {
            sendUsage(sender);
            return true;
        }

        ChampionshipsCore plugin = ChampionshipsCore.getInstance();
        if (plugin.getPrepareSessionManager().hasActiveSessions()) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_RELOAD_PREPARE_ACTIVE);
            return true;
        }
        if (!RELOAD_IN_PROGRESS.compareAndSet(false, true)) {
            Utils.sendAdminError(sender, MessageConfig.ADMIN_RELOAD_ALREADY_RUNNING);
            return true;
        }
        Set<GameTypeEnum> previouslyEnabled = plugin.getGameManager().enabledGamesSnapshot();
        RestartSensitiveConfig previousSensitive = RestartSensitiveConfig.capture();
        plugin.getScheduleManager().unload();
        if (!plugin.getConfigurationManager().reload()) {
            plugin.getScheduleManager().load();
            RELOAD_IN_PROGRESS.set(false);
            Utils.sendAdminError(sender, MessageConfig.ADMIN_RELOAD_CONFIG_FAILED);
            return true;
        }
        List<String> restartRequired = previousSensitive.changedPaths();
        Utils.sendAdminSuccess(sender, MessageConfig.ADMIN_RELOAD_READ_DONE);
        plugin.getGameManager().hotReload(previouslyEnabled).whenComplete((report, failure) ->
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> finishReload(plugin, sender, report, failure, restartRequired)));

        return true;
    }

    private static void finishReload(@NotNull ChampionshipsCore plugin, @NotNull CommandSender sender,
                                     @Nullable GameManager.ReloadReport report, @Nullable Throwable failure,
                                     @NotNull List<String> restartRequired) {
        if (failure != null || report == null) {
            plugin.getScheduleManager().load();
            RELOAD_IN_PROGRESS.set(false);
            Utils.sendAdminError(sender, MessageConfig.ADMIN_RELOAD_RESET_FAILED);
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Configuration hot reload failed", failure);
            return;
        }
        plugin.getGameManager().getBingoManager().reloadContentConfiguration().whenComplete((bingoReady, bingoFailure) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> completeReload(plugin, sender, report,
                        restartRequired, bingoFailure == null && Boolean.TRUE.equals(bingoReady))));
    }

    private static void completeReload(@NotNull ChampionshipsCore plugin, @NotNull CommandSender sender,
                                       @NotNull GameManager.ReloadReport report,
                                       @NotNull List<String> restartRequired, boolean bingoReady) {
        try {
            plugin.getDailyManager().reloadConfiguration();
            plugin.getVisibilityManager().reconcileAll();
            plugin.getSidebarManager().reload();
            plugin.getSidebarManager().invalidateAll();
            plugin.getScheduleManager().load();
            String restartNotice = restartRequired.isEmpty() ? ""
                    : MessageConfig.ADMIN_RELOAD_RESTART_REQUIRED
                            .replace("%items%", String.join(",", restartRequired));
            String resetFailure = report.failedResets() == 0 ? ""
                    : MessageConfig.ADMIN_RELOAD_RESET_FAILURES.replace("%count%", String.valueOf(report.failedResets()));
            String bingoFailure = bingoReady ? "" : MessageConfig.ADMIN_RELOAD_BINGO_FAILURE;
            String configFailure = report.failedConfigurations() == 0 ? ""
                    : MessageConfig.ADMIN_RELOAD_CONFIG_FAILURES.replace("%count%", String.valueOf(report.failedConfigurations()));
            Utils.sendAdminSuccess(sender, MessageConfig.ADMIN_RELOAD_COMPLETED
                    .replace("%reused%", String.valueOf(report.reusedInstances()))
                    .replace("%reloaded%", String.valueOf(report.reloadedConfigurations()))
                    .replace("%configFailure%", configFailure)
                    .replace("%reset%", String.valueOf(report.resetInstances()))
                    .replace("%resetFailure%", resetFailure)
                    .replace("%enabled%", String.valueOf(report.enabledManagers()))
                    .replace("%disabled%", String.valueOf(report.disabledManagers()))
                    .replace("%remote%", report.remoteMatchesStopped() == 0 ? ""
                            : MessageConfig.ADMIN_RELOAD_REMOTE_STOPPED.replace("%count%", String.valueOf(report.remoteMatchesStopped())))
                    .replace("%bingoFailure%", bingoFailure)
                    .replace("%restartNotice%", restartNotice));
        } finally {
            RELOAD_IN_PROGRESS.set(false);
        }
    }

    /** Values whose owning resource is constructed once and therefore cannot be swapped by reload. */
    private record RestartSensitiveConfig(
            String databaseType, String databaseAddress, int databasePort, String databaseName,
            String databaseUsername, String databasePassword, Boolean redisEnabled, String redisInstanceId,
            String redisUri, String redisNamespace, String redisGroupPrefix, long redisStreamLength,
            long redisBlockTimeout, long redisReclaimIdle, int redisMaxDeliveries,
            long redisReconciliationSeconds, String bingoExecutionMode, String bingoWorkerId,
            String bingoProxyChannel, int aceRaceCopies, int parkourWarriorCopies) {

        static RestartSensitiveConfig capture() {
            return new RestartSensitiveConfig(
                    ink.ziip.championshipscore.configuration.config.CCConfig.DATABASE_TYPE,
                    ink.ziip.championshipscore.configuration.config.CCConfig.DATABASE_ADDRESS,
                    ink.ziip.championshipscore.configuration.config.CCConfig.DATABASE_PORT,
                    ink.ziip.championshipscore.configuration.config.CCConfig.DATABASE_NAME,
                    ink.ziip.championshipscore.configuration.config.CCConfig.DATABASE_USERNAME,
                    ink.ziip.championshipscore.configuration.config.CCConfig.DATABASE_PASSWORD,
                    ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_ENABLED,
                    ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_INSTANCE_ID,
                    ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_URI,
                    ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_NAMESPACE,
                    ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_CONSUMER_GROUP_PREFIX,
                    ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_STREAM_MAX_LENGTH,
                    ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_BLOCK_TIMEOUT_MILLIS,
                    ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_RECLAIM_IDLE_MILLIS,
                    ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_MAX_DELIVERIES,
                    ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_RECONCILIATION_SECONDS,
                    ink.ziip.championshipscore.configuration.config.CCConfig.BINGO_EXECUTION_MODE,
                    ink.ziip.championshipscore.configuration.config.CCConfig.BINGO_WORKER_ID,
                    ink.ziip.championshipscore.configuration.config.CCConfig.BINGO_PROXY_CHANNEL,
                    ink.ziip.championshipscore.configuration.config.CCConfig.DAILY_ACERACE_CONCURRENT_INSTANCES,
                    ink.ziip.championshipscore.configuration.config.CCConfig.DAILY_PARKOUR_WARRIOR_CONCURRENT_INSTANCES);
        }

        List<String> changedPaths() {
            List<String> changed = new ArrayList<>();
            if (!Objects.equals(databaseType, ink.ziip.championshipscore.configuration.config.CCConfig.DATABASE_TYPE)
                    || !Objects.equals(databaseAddress, ink.ziip.championshipscore.configuration.config.CCConfig.DATABASE_ADDRESS)
                    || databasePort != ink.ziip.championshipscore.configuration.config.CCConfig.DATABASE_PORT
                    || !Objects.equals(databaseName, ink.ziip.championshipscore.configuration.config.CCConfig.DATABASE_NAME)
                    || !Objects.equals(databaseUsername, ink.ziip.championshipscore.configuration.config.CCConfig.DATABASE_USERNAME)
                    || !Objects.equals(databasePassword, ink.ziip.championshipscore.configuration.config.CCConfig.DATABASE_PASSWORD))
                changed.add("database.*");
            if (!Objects.equals(redisEnabled, ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_ENABLED)
                    || !Objects.equals(redisInstanceId, ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_INSTANCE_ID)
                    || !Objects.equals(redisUri, ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_URI)
                    || !Objects.equals(redisNamespace, ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_NAMESPACE)
                    || !Objects.equals(redisGroupPrefix, ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_CONSUMER_GROUP_PREFIX)
                    || redisStreamLength != ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_STREAM_MAX_LENGTH
                    || redisBlockTimeout != ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_BLOCK_TIMEOUT_MILLIS
                    || redisReclaimIdle != ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_RECLAIM_IDLE_MILLIS
                    || redisMaxDeliveries != ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_MAX_DELIVERIES
                    || redisReconciliationSeconds != ink.ziip.championshipscore.configuration.config.CCConfig.REDIS_RECONCILIATION_SECONDS)
                changed.add("redis.*");
            if (!Objects.equals(bingoExecutionMode, ink.ziip.championshipscore.configuration.config.CCConfig.BINGO_EXECUTION_MODE)
                    || !Objects.equals(bingoWorkerId, ink.ziip.championshipscore.configuration.config.CCConfig.BINGO_WORKER_ID)
                    || !Objects.equals(bingoProxyChannel, ink.ziip.championshipscore.configuration.config.CCConfig.BINGO_PROXY_CHANNEL))
                changed.add("bingo.execution/worker/proxy");
            if (aceRaceCopies != ink.ziip.championshipscore.configuration.config.CCConfig.DAILY_ACERACE_CONCURRENT_INSTANCES)
                changed.add("daily.AceRace.concurrent-instances");
            if (parkourWarriorCopies != ink.ziip.championshipscore.configuration.config.CCConfig.DAILY_PARKOUR_WARRIOR_CONCURRENT_INSTANCES)
                changed.add("daily.ParkourWarrior.concurrent-instances");

            return List.copyOf(changed);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return complete(List.of("--confirm"), args[0]);
        return Collections.emptyList();
    }
}

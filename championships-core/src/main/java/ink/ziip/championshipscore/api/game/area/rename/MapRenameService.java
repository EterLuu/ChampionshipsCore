package ink.ziip.championshipscore.api.game.area.rename;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Coordinates runtime detach, transactional database migration, config rename, reload and rollback. */
public final class MapRenameService {
    private final ChampionshipsCore plugin;
    private final AtomicBoolean renameRunning = new AtomicBoolean();

    public MapRenameService(@NotNull ChampionshipsCore plugin) {
        this.plugin = plugin;
    }

    public void rename(@NotNull CommandSender sender, @NotNull GameTypeEnum game,
                       @NotNull String requestedOldName, @NotNull String newName) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Map rename must begin on the main thread");
        if (!renameRunning.compareAndSet(false, true)) {
            Utils.sendAdminError(sender, "已有地图改名正在进行，请稍后再试");
            return;
        }

        BaseGameInstanceManager<?> manager = plugin.getGameManager().getAreaManager(game);
        String oldName = manager == null ? null : manager.getAreaNameList().stream()
                .filter(name -> name.equalsIgnoreCase(requestedOldName)).findFirst().orElse(null);
        String validation = validate(manager, game, oldName, newName);
        if (validation != null) {
            renameRunning.set(false);
            Utils.sendAdminError(sender, validation);
            return;
        }

        BaseGameConfig config = manager.getMapConfig(oldName);
        String oldDisplayName = config.getAreaName();
        String oldAssetName = game == GameTypeEnum.BuildMart ? oldDisplayName : oldName;
        String oldWorldName = config.getConfiguredWorld();
        BuildMartWorldRename.Plan buildMartWorldPlan = null;
        Path oldPath = plugin.getFolder().resolve(config.getFileName());
        Path newPath = oldPath.resolveSibling(newName + ".yml");
        if (Files.exists(newPath)) {
            renameRunning.set(false);
            Utils.sendAdminError(sender, "目标配置文件 &#fff566" + newPath.getFileName() + " &#ededed已存在");
            return;
        }
        try {
            MapAssetRename.validate(plugin.getFolder(), game, oldName, oldAssetName, newName);
            if (game == GameTypeEnum.BuildMart)
                buildMartWorldPlan = BuildMartWorldRename.validate(plugin, manager, oldName, newName, oldWorldName);
        } catch (IllegalStateException exception) {
            renameRunning.set(false);
            Utils.sendAdminError(sender, exception.getMessage());
            return;
        }
        String newWorldName = buildMartWorldPlan == null ? oldWorldName : buildMartWorldPlan.newWorldName();
        if (!manager.detachAreaForRename(oldName)) {
            renameRunning.set(false);
            Utils.sendAdminError(sender, "场地卸载失败；配置和数据库均未修改");
            return;
        }

        Utils.sendAdminInfo(sender, "已卸载场地 &#fff566" + oldName + "&#ededed，正在等待持久化队列并迁移记录……");
        RenameContext context = new RenameContext(sender, game, manager, oldName, newName,
                oldDisplayName, oldAssetName, oldWorldName, newWorldName,
                buildMartWorldPlan != null && buildMartWorldPlan.movesWorld(), oldPath, newPath);
        plugin.getDailyStatsManager().runAfterPendingWrites(() ->
                plugin.getRankManager().runAfterPendingPointWrites(() -> execute(context)));
    }

    private String validate(BaseGameInstanceManager<?> manager, GameTypeEnum game,
                            String oldName, String newName) {
        if (manager == null) return "该游戏没有地图管理器";
        if (oldName == null) return "找不到原场地";
        if (manager.getMapConfig(oldName) == null) return "原场地配置尚未加载，不能改名";
        if (newName.isBlank() || newName.length() > 32 || newName.matches(".*[\\\\/:*?\"<>|].*"))
            return "新场地名不能为空、不能超过 32 个字符，且不能包含 \\/:*?\"<>|";
        if (newName.equals(".") || newName.equals("..")) return "新场地名无效";
        if (oldName.equalsIgnoreCase(newName)) return "新旧场地名不能相同（不支持仅修改大小写）";
        if (manager.getAreaNameList().stream().anyMatch(name -> name.equalsIgnoreCase(newName)))
            return "目标场地 &#fff566" + newName + " &#ededed已存在";
        if (plugin.getPrepareSessionManager().hasActiveSessions()) return "仍有地图 prepare 会话进行中，不能改名";
        if (plugin.getScheduleManager().isFormalEventRunning(game))
            return "该游戏的正式赛程正在运行，不能改名";
        if (!plugin.getGameManager().getSpectatableMapInstances(game, oldName).isEmpty()
                || !manager.canRenameArea(oldName)) return "场地正在运行，不能改名";
        return null;
    }

    private void execute(RenameContext context) {
        MapConfigFileRename.State fileState = null;
        MapAssetRename.State assetState = null;
        FormalEventMapRename.State formalEventState = null;
        BuildMartWorldRename.State worldState = null;
        boolean managedWorldRenamed = false;
        boolean pendingRenamed = false;
        Connection connection = null;
        try {
            connection = plugin.getDatabaseManager().getConnection();
            connection.setAutoCommit(false);
            MapRecordRenameMigration.Counts counts = MapRecordRenameMigration.migrate(connection,
                    context.game(), context.oldName(), context.newName(),
                    context.oldDisplayName(), context.newName());
            if (!plugin.getRankManager().renamePendingAreaRecords(
                    context.game(), context.oldDisplayName(), context.newName())) {
                throw new IllegalStateException("无法同步待提交积分记录");
            }
            pendingRenamed = true;
            if (context.movesDedicatedBuildMartWorld()) {
                worldState = onMain(() -> BuildMartWorldRename.rename(plugin,
                        context.oldWorldName(), context.newWorldName()));
                onMain(() -> {
                    context.manager().renameManagedWorld(context.oldWorldName(), context.newWorldName());
                    return true;
                });
                managedWorldRenamed = true;
            }
            assetState = MapAssetRename.rename(plugin.getFolder(), context.game(),
                    context.oldName(), context.oldAssetName(), context.newName(),
                    context.oldWorldName(), context.newWorldName());
            fileState = MapConfigFileRename.rename(context.oldPath(), context.newPath(), context.newName(),
                    context.oldWorldName(), context.newWorldName());
            formalEventState = onMain(() -> FormalEventMapRename.rename(
                    plugin.getConfigurationManager().getCCConfig(), context.game(),
                    context.oldName(), context.newName()));
            boolean loaded = onMain(() -> context.manager().loadAreaAfterRename(
                    context.newName(), context.newWorldName()));
            if (!loaded) throw new IllegalStateException("新名称场地加载失败");
            connection.commit();
            plugin.getDailyStatsManager().renameMap(context.game(), context.oldName(), context.newName());
            plugin.getRankManager().refreshAfterPendingPointWrites();
            finish(context.sender(), true, "已将场地 &#fff566" + context.oldName()
                    + " &#ededed改名为 &#fff566" + context.newName()
                    + " &#696969• &#ededed同步数据库记录 &#fff566" + counts.total() + " &#ededed条");
            return;
        } catch (Exception exception) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (Exception rollbackException) {
                    plugin.getLogger().log(Level.SEVERE, "地图改名数据库回滚失败", rollbackException);
                }
            }
            rollbackRuntime(context, fileState, assetState, formalEventState, worldState,
                    managedWorldRenamed, pendingRenamed);
            plugin.getLogger().log(Level.SEVERE, "地图改名失败，已执行回滚："
                    + context.oldName() + " -> " + context.newName(), exception);
            finish(context.sender(), false, "地图改名失败，原场地已尝试恢复；请检查控制台");
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(true);
                    connection.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void rollbackRuntime(RenameContext context, MapConfigFileRename.State fileState,
                                 MapAssetRename.State assetState,
                                 FormalEventMapRename.State formalEventState,
                                 BuildMartWorldRename.State worldState, boolean managedWorldRenamed,
                                 boolean pendingRenamed) {
        try {
            onMain(() -> {
                if (context.manager().getMapConfig(context.newName()) != null)
                    context.manager().forceDetachAreaAfterFailedRename(context.newName());
                return true;
            });
        } catch (Exception rollbackException) {
            plugin.getLogger().log(Level.SEVERE, "地图改名回滚时卸载新场地失败", rollbackException);
        }
        try {
            if (fileState != null) MapConfigFileRename.rollback(fileState);
        } catch (Exception rollbackException) {
            plugin.getLogger().log(Level.SEVERE, "地图改名回滚时无法恢复配置文件", rollbackException);
        }
        try {
            if (assetState != null) MapAssetRename.rollback(assetState);
        } catch (Exception rollbackException) {
            plugin.getLogger().log(Level.SEVERE, "地图改名回滚时无法恢复地图资产", rollbackException);
        }
        try {
            if (formalEventState != null) onMain(() -> {
                FormalEventMapRename.rollback(plugin.getConfigurationManager().getCCConfig(), formalEventState);
                return true;
            });
        } catch (Exception rollbackException) {
            plugin.getLogger().log(Level.SEVERE, "地图改名回滚时无法恢复正式赛程地图配置", rollbackException);
        }
        try {
            if (managedWorldRenamed) onMain(() -> {
                context.manager().renameManagedWorld(context.newWorldName(), context.oldWorldName());
                return true;
            });
            if (worldState != null) onMain(() -> {
                BuildMartWorldRename.rollback(plugin, worldState);
                return true;
            });
        } catch (Exception rollbackException) {
            plugin.getLogger().log(Level.SEVERE, "地图改名回滚时无法恢复 Build Mart 世界", rollbackException);
        }
        try {
            if (pendingRenamed && !plugin.getRankManager().renamePendingAreaRecords(
                    context.game(), context.newName(), context.oldDisplayName())) {
                plugin.getLogger().severe("地图改名回滚时无法恢复待提交积分记录");
            }
        } catch (RuntimeException rollbackException) {
            plugin.getLogger().log(Level.SEVERE, "地图改名回滚时无法恢复待提交积分记录", rollbackException);
        }
        try {
            onMain(() -> context.manager().loadAreaAfterRename(context.oldName(), context.oldWorldName()));
        } catch (Exception rollbackException) {
            plugin.getLogger().log(Level.SEVERE, "地图改名回滚时无法重新加载原场地", rollbackException);
        }
    }

    private <T> T onMain(@NotNull java.util.concurrent.Callable<T> operation) throws Exception {
        if (Bukkit.isPrimaryThread()) return operation.call();
        CompletableFuture<T> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                result.complete(operation.call());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        });
        return result.get(30, TimeUnit.SECONDS);
    }

    private void finish(CommandSender sender, boolean success, String message) {
        Runnable completion = () -> {
            renameRunning.set(false);
            if (success) {
                plugin.getDailyManager().refreshOpenMenus();
                plugin.getPrepareSessionManager().closeAreaListMenus();
                Utils.sendAdminSuccess(sender, message);
            } else Utils.sendAdminError(sender, message);
        };
        if (Bukkit.isPrimaryThread()) completion.run();
        else Bukkit.getScheduler().runTask(plugin, completion);
    }

    private record RenameContext(CommandSender sender, GameTypeEnum game,
                                 BaseGameInstanceManager<?> manager,
                                 String oldName, String newName, String oldDisplayName,
                                 String oldAssetName, String oldWorldName, String newWorldName,
                                 boolean movesDedicatedBuildMartWorld,
                                 Path oldPath, Path newPath) {
    }
}

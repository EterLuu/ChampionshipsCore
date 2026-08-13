package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.area.prepare.gui.AnvilInputGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.AreaListGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.ListStepGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.StepMenuGui;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the prepare subsystem: the per-game {@link PrepareFlowDefinition}s, the live {@link PrepareSession}s,
 * and the inventory-snapshot machinery that keeps a player's real inventory safe while they're in prepare
 * mode (in-memory for fast restore on normal exit, plus a disk file so a crash mid-prepare still restores
 * on next join).
 */
public class PrepareSessionManager extends BaseManager {
    private final Map<GameTypeEnum, PrepareFlowDefinition> flows = new ConcurrentHashMap<>();
    private final Map<UUID, PrepareSession> sessions = new ConcurrentHashMap<>();
    /** One editor per game/map. The value is the owning player's UUID. */
    private final Map<String, UUID> mapLocks = new ConcurrentHashMap<>();
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Path sessionsDir;
    private PrepareListener listener;

    public PrepareSessionManager(ChampionshipsCore plugin) {
        super(plugin);
        this.sessionsDir = plugin.getFolder().resolve("prepare_sessions");
    }

    public ChampionshipsCore getPlugin() {
        return plugin;
    }

    public @Nullable PrepareFlowDefinition flow(GameTypeEnum gameType) {
        return flows.get(gameType);
    }

    /** Whether this game has a complete map preparation workflow exposed by the command layer. */
    public boolean supports(@NotNull GameTypeEnum gameType) {
        return flows.containsKey(gameType);
    }

    public @Nullable PrepareSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public boolean hasActiveSessions() {
        return !sessions.isEmpty();
    }

    @Override
    public void load() {
        flows.put(GameTypeEnum.Bingo, new ink.ziip.championshipscore.api.game.area.prepare.bingo.BingoPrepareFlow());
        flows.put(GameTypeEnum.ParkourTag, new ink.ziip.championshipscore.api.game.area.prepare.parkourtag.ParkourTagPrepareFlow());
        flows.put(GameTypeEnum.BattleBox, new ink.ziip.championshipscore.api.game.area.prepare.battlebox.BattleBoxPrepareFlow());
        flows.put(GameTypeEnum.TNTRun, new ink.ziip.championshipscore.api.game.area.prepare.tntrun.TNTRunPrepareFlow());
        flows.put(GameTypeEnum.BuildMart, new ink.ziip.championshipscore.api.game.area.prepare.buildmart.BuildMartPrepareFlow());
        flows.put(GameTypeEnum.SkyWars, new SkyWarsPrepareFlow());
        flows.put(GameTypeEnum.TGTTOS, new TGTTOSPrepareFlow());
        flows.put(GameTypeEnum.DragonEggCarnival, new DragonEggCarnivalPrepareFlow());
        flows.put(GameTypeEnum.SnowballShowdown, new SnowballShowdownPrepareFlow());
        flows.put(GameTypeEnum.ParkourWarrior, new ParkourWarriorPrepareFlow());
        flows.put(GameTypeEnum.HotyCodyDusky, new HotyCodyDuskyPrepareFlow());
        flows.put(GameTypeEnum.Dodgebolt, new DodgeboltPrepareFlow());
        flows.put(GameTypeEnum.AceRace, new AceRacePrepareFlow());
        try {
            Files.createDirectories(sessionsDir);
        } catch (IOException e) {
            plugin.getLogger().warning(Utils.formatModuleLog("Prepare", "存储", "无法创建会话目录 | " + e.getMessage()));
        }
        listener = new PrepareListener(plugin, this);
        listener.register();
    }

    @Override
    public void unload() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            PrepareSession session = sessions.get(id);
            if (session != null) session.getFlow().onSessionExit(session);
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                AnvilInputGui.close(p);
                restoreSnapshot(p);
                deleteSnapshotFile(id);
            }
            // offline players: leave the snapshot file so restorePendingSnapshot can restore on next join
        }
        sessions.clear();
        mapLocks.clear();
        snapshots.clear();
        AnvilInputGui.closeAll();
        if (listener != null) listener.unRegister();
    }

    // ── entry points ──────────────────────────────────────────────────────────────────────────

    public void openAreaListGui(@NotNull Player player, @NotNull GameTypeEnum gameType) {
        AreaListGui.open(this, player, gameType);
    }

    /** Creates only an unbound draft definition; world selection is an explicit, editable prepare step. */
    public void createAndEnter(@NotNull Player player, @NotNull GameTypeEnum gameType, @NotNull String name) {
        BaseGameInstanceManager<?> mgr = plugin.getGameManager().getAreaManager(gameType);
        if (mgr == null) {
            Utils.sendAdminError(player, "该游戏不可用");
            return;
        }
        if (!mgr.addArea(name, "")) {
            Utils.sendAdminError(player, "无法创建地图草稿；请确认名称未被占用");
            return;
        }
        var target = mgr.getSetupTarget(gameType, name);
        if (target != null && gameType != GameTypeEnum.Bingo) {
            target.config().bindConfiguredWorld("");
            target.config().saveOptions();
        }
        if (target != null) target.config().beginPrepareDraft();
        enterSession(player, gameType, name);
    }

    public boolean deleteArea(@NotNull Player player, @NotNull GameTypeEnum gameType, @NotNull String name) {
        BaseGameInstanceManager<?> mgr = plugin.getGameManager().getAreaManager(gameType);
        if (mgr == null || mapLocks.containsKey(lockKey(gameType, name))) {
            Utils.sendAdminError(player, "地图正在编辑或游戏管理器不可用，无法删除");
            return false;
        }
        if (!mgr.deleteArea(name)) {
            Utils.sendAdminError(player, "地图正在运行或删除配置失败；物理世界未作任何改动");
            return false;
        }
        Utils.sendAdminSuccess(player, "已删除地图配置 &#fff566" + name
                + " &#696969• &#ededed物理世界保留，请按需使用 /cc admin world delete");
        return true;
    }

    public void enterSession(@NotNull Player player, @NotNull GameTypeEnum gameType, @NotNull String areaName) {
        PrepareFlowDefinition flow = flows.get(gameType);
        if (flow == null) {
            Utils.sendAdminError(player, "该游戏暂不支持 prepare");
            return;
        }
        BaseGameInstanceManager<?> mgr = plugin.getGameManager().getAreaManager(gameType);
        var target = mgr == null ? null : mgr.getSetupTarget(gameType, areaName);
        if (target == null) {
            Utils.sendAdminError(player, "找不到地图 &#fff566" + areaName);
            return;
        }
        if (sessions.containsKey(player.getUniqueId())) exitSession(player);
        String lockKey = lockKey(gameType, areaName);
        UUID owner = mapLocks.putIfAbsent(lockKey, player.getUniqueId());
        if (owner != null && !owner.equals(player.getUniqueId())) {
            Player editor = Bukkit.getPlayer(owner);
            Utils.sendAdminError(player, "该地图正由 &#fff566"
                    + (editor == null ? owner : editor.getName()) + " &#ededed编辑");
            return;
        }

        saveSnapshot(player);
        PrepareSession session = new PrepareSession(plugin, gameType, areaName, target, flow);
        sessions.put(player.getUniqueId(), session);
        PrepareModeInventory.apply(player, session);
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        teleportToEditorLocation(player, session);
        Utils.sendAdminSuccess(player, "进入 prepare &#bababa• &#fff566" + gameType + " &#696969/ &#fff566" + areaName);
        Utils.sendAdminInfo(player, "使用热键栏配置 prepare &#696969• 打开箱子编辑步骤 &#696969• 屏障退出");
        refreshSidebar(player);
    }

    public void exitSession(@NotNull Player player) {
        PrepareSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        session.getFlow().onSessionExit(session);
        mapLocks.remove(lockKey(session.getGameType(), session.getAreaName()), player.getUniqueId());
        AnvilInputGui.close(player);
        restoreSnapshot(player);
        snapshots.remove(player.getUniqueId());
        deleteSnapshotFile(player.getUniqueId());
        Utils.sendAdminSuccess(player, "已退出 prepare &#696969• 物品栏已还原");
        refreshSidebar(player);
    }

    // ── click routing (called by PrepareListener) ────────────────────────────────────────────

    public void handleStepClick(@NotNull Player player, @NotNull PrepareSession session, @NotNull String stepKey) {
        PrepareStep step = session.step(stepKey);
        if (step == null) return;
        if (step.captureType() != StepCaptureType.CONFIRM_WORLD
                && step.captureType() != StepCaptureType.STAMP
                && step.captureType() != StepCaptureType.TOGGLE
                && step.captureType() != StepCaptureType.SELECT
                && !session.getFlow().isInCorrectWorld(player, session.getTarget())) {
            Utils.sendAdminError(player, "请先前往当前地图世界 " + session.getTarget().worldName());
            return;
        }
        switch (step.captureType()) {
            case CONFIRM_WORLD, STAND_AND_RUN, TOGGLE, WE_SELECTION, SCHEMATIC -> {
                String msg = step.capture(session, player);
                if (msg != null) player.sendMessage(msg);
                PrepareModeInventory.refresh(player, session);
            }
            case SELECT -> step.openSelection(this, player, session);
            case STAMP -> AnvilInputGui.openNumber(player, count -> {
                String msg = step.stamp(session, player, count);
                if (msg != null) player.sendMessage(msg);
                PrepareModeInventory.refresh(player, session);
            });
            case LIST -> ListStepGui.open(this, player, session, step);
        }
        refreshSidebar(player);
    }

    public void handleActionClick(@NotNull Player player, @NotNull PrepareSession session, @NotNull String action) {
        switch (action) {
            case "teleport" -> teleportToEditorLocation(player, session);
            case "steps" -> StepMenuGui.open(player, session);
            case "exit" -> exitSession(player);
            case "validate" -> validate(player, session, false);
            case "save-draft" -> saveDraft(player, session);
            case "publish" -> {
                if (!validate(player, session, true)) return;
                if (!session.getTarget().canSaveMap()) {
                    Utils.sendAdminError(player, "同一地图仍有游戏实例运行，无法发布");
                    return;
                }
                Utils.sendAdminInfo(player, "正在发布地图，请稍候……");
                UUID playerId = player.getUniqueId();
                session.getFlow().publish(session).whenComplete((published, error) -> {
                    Runnable completion = () -> completePublish(playerId, session,
                            error == null && Boolean.TRUE.equals(published));
                    if (Bukkit.isPrimaryThread()) completion.run();
                    else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, completion);
                });
            }
            default -> {
            }
        }
    }

    private void saveDraft(@NotNull Player player, @NotNull PrepareSession session) {
        if (!session.getFlow().isInCorrectWorld(player, session.getTarget())) {
            Utils.sendAdminError(player, "请先前往当前地图世界 " + session.getTarget().worldName());
            return;
        }
        if (!session.getTarget().canSaveMap()) {
            Utils.sendAdminError(player, "同一地图仍有游戏实例运行，无法保存草稿");
            return;
        }
        session.markDirty();
        Utils.sendAdminInfo(player, "正在保存地图草稿，请稍候……");
        UUID playerId = player.getUniqueId();
        session.getFlow().saveDraft(session).whenComplete((saved, error) -> {
            Runnable completion = () -> completeDraftSave(playerId, session,
                    error == null && Boolean.TRUE.equals(saved));
            if (Bukkit.isPrimaryThread()) completion.run();
            else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, completion);
        });
    }

    private void completeDraftSave(UUID playerId, PrepareSession session, boolean saved) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || sessions.get(playerId) != session)
            return;
        if (!saved) {
            Utils.sendAdminError(player, "草稿保存失败，请查看控制台日志");
            PrepareModeInventory.refresh(player, session);
            return;
        }

        Location destination = session.getFlow().copyZeroLocation(session.getTarget());
        if (destination != null && destination.getWorld() == null)
            destination.setWorld(Bukkit.getWorld(session.getTarget().worldName()));
        if (destination != null && destination.getWorld() != null)
            player.teleport(destination);
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        PrepareModeInventory.refresh(player, session);
        Utils.sendAdminSuccess(player, "地图草稿已保存 &#696969• &#ededed仍需完成全部点位后再发布");
        refreshSidebar(player);
    }

    private void completePublish(UUID playerId, PrepareSession session, boolean published) {
        if (published)
            session.getTarget().config().markPreparePublished();

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || sessions.get(playerId) != session)
            return;
        if (!published) {
            Utils.sendAdminError(player, "地图发布失败，请查看控制台日志；草稿仍保持锁定状态");
            PrepareModeInventory.refresh(player, session);
            return;
        }

        Location destination = session.getFlow().copyZeroLocation(session.getTarget());
        if (destination != null && destination.getWorld() == null)
            destination.setWorld(Bukkit.getWorld(session.getTarget().worldName()));
        if (destination != null && destination.getWorld() != null)
            player.teleport(destination);
        PrepareModeInventory.refresh(player, session);
        Utils.sendAdminSuccess(player, "地图已发布 &#696969• &#edededrevision &#fff566"
                + session.getTarget().config().getPrepareRevision());
        refreshSidebar(player);
    }

    /** Game-start guard: a locked, dirty, or explicitly unpublished map cannot be selected. */
    public boolean canStart(@NotNull GameTypeEnum gameType, @NotNull String mapName) {
        if (mapLocks.containsKey(lockKey(gameType, mapName))) return false;
        BaseGameInstanceManager<?> mgr = plugin.getGameManager().getAreaManager(gameType);
        var target = mgr == null ? null : mgr.getSetupTarget(gameType, mapName);
        return target != null && target.config().isPrepareReady();
    }

    private boolean validate(Player player, PrepareSession session, boolean forPublish) {
        java.util.List<String> errors = session.getFlow().validate(session);
        if (errors.isEmpty()) {
            Utils.sendAdminSuccess(player, forPublish ? "校验通过，开始发布" : "校验通过，可以发布");
            return true;
        }
        Utils.sendAdminError(player, "校验失败，仍缺少 &#fff566" + errors.size() + " &#ededed项：");
        for (String error : errors) player.sendMessage(Utils.translateColorCodes("  &#ff6b26• &#ededed" + error));
        return false;
    }

    /** Teleports an editor only when the selected map has a loaded, bound physical world. */
    private boolean teleportToEditorLocation(@NotNull Player player, @NotNull PrepareSession session) {
        String worldName = session.getFlow().worldName(session.getTarget());
        if (worldName.isBlank()) {
            Utils.sendAdminInfo(player, "该地图尚未绑定世界，请先前往目标世界并使用“绑定当前世界”。");
            return false;
        }
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            Utils.sendAdminError(player, "地图世界 &#fff566" + worldName + " &#ededed尚未加载");
            return false;
        }

        Location destination = session.getFlow().copyZeroLocation(session.getTarget());
        if (destination != null && destination.getWorld() == null) destination.setWorld(world);
        if (destination == null || destination.getWorld() == null) {
            Utils.sendAdminError(player, "无法找到地图编辑位置");
            return false;
        }
        if (!player.teleport(destination)) {
            Utils.sendAdminError(player, "传送至地图编辑位置失败");
            return false;
        }
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);

        Utils.sendAdminSuccess(player, "已传送至 "
                + session.getFlow().editorLocationName(session.getTarget()));
        return true;
    }

    private static String lockKey(GameTypeEnum gameType, String mapName) {
        return gameType.name() + "\u0000" + mapName.toLowerCase(java.util.Locale.ROOT);
    }

    private void refreshSidebar(@NotNull Player player) {
        if (plugin.getSidebarManager() != null) plugin.getSidebarManager().invalidate(player);
    }

    // ── inventory snapshot (in-memory + disk) ────────────────────────────────────────────────

    private void saveSnapshot(@NotNull Player player) {
        PlayerInventory inv = player.getInventory();
        Snapshot snap = new Snapshot(
                inv.getStorageContents(),
                inv.getArmorContents(),
                inv.getItemInOffHand(),
                player.getItemOnCursor(), player.getGameMode(), player.getAllowFlight(), player.isFlying());
        snapshots.put(player.getUniqueId(), snap);
        saveSnapshotFile(player.getUniqueId(), snap);
    }

    private void restoreSnapshot(@NotNull Player player) {
        Snapshot snap = snapshots.get(player.getUniqueId());
        if (snap == null) snap = loadSnapshotFile(player.getUniqueId());
        if (snap == null) return;
        PlayerInventory inv = player.getInventory();
        if (snap.storage != null) inv.setStorageContents(snap.storage);
        if (snap.armor != null) inv.setArmorContents(snap.armor);
        inv.setItemInOffHand(snap.offhand);
        player.setItemOnCursor(snap.cursor);
        restorePlayerState(player, snap);
    }

    /** Crash recovery: if a snapshot file lingers from a non-clean exit, restore the real inventory. */
    public void restorePendingSnapshot(@NotNull Player player) {
        Snapshot snap = loadSnapshotFile(player.getUniqueId());
        if (snap == null) return;
        PlayerInventory inv = player.getInventory();
        if (snap.storage != null) inv.setStorageContents(snap.storage);
        if (snap.armor != null) inv.setArmorContents(snap.armor);
        inv.setItemInOffHand(snap.offhand);
        player.setItemOnCursor(snap.cursor);
        restorePlayerState(player, snap);
        deleteSnapshotFile(player.getUniqueId());
        snapshots.remove(player.getUniqueId());
        Utils.sendAdminInfo(player, "检测到未结束的 prepare &#696969• 物品栏已还原");
    }

    private void saveSnapshotFile(@NotNull UUID id, @NotNull Snapshot snap) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("storage", serializeItems(snap.storage));
        y.set("armor", serializeItems(snap.armor));
        y.set("offhand", serializeItem(snap.offhand));
        y.set("cursor", serializeItem(snap.cursor));
        y.set("game-mode", snap.gameMode == null ? null : snap.gameMode.name());
        if (snap.allowFlight != null) y.set("allow-flight", snap.allowFlight);
        if (snap.flying != null) y.set("flying", snap.flying);
        try {
            Files.createDirectories(sessionsDir);
            y.save(sessionsDir.resolve(id + ".yml").toFile());
        } catch (IOException e) {
            plugin.getLogger().warning(Utils.formatModuleLog("Prepare", "存储", "无法保存物品栏快照 | " + e.getMessage()));
        }
    }

    private @Nullable Snapshot loadSnapshotFile(@NotNull UUID id) {
        Path file = sessionsDir.resolve(id + ".yml");
        if (!Files.isRegularFile(file)) return null;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file.toFile());
        ItemStack[] storage = deserializeItems(y.getString("storage"));
        ItemStack[] armor = deserializeItems(y.getString("armor"));
        ItemStack offhand = deserializeItem(y.getString("offhand"));
        ItemStack cursor = deserializeItem(y.getString("cursor"));
        if (storage == null || armor == null) return null;
        org.bukkit.GameMode gameMode = null;
        String mode = y.getString("game-mode");
        if (mode != null) {
            try {
                gameMode = org.bukkit.GameMode.valueOf(mode);
            } catch (IllegalArgumentException ignored) {
            }
        }
        Boolean allowFlight = y.contains("allow-flight") ? y.getBoolean("allow-flight") : null;
        Boolean flying = y.contains("flying") ? y.getBoolean("flying") : null;
        return new Snapshot(storage, armor, offhand, cursor, gameMode, allowFlight, flying);
    }

    private void deleteSnapshotFile(@NotNull UUID id) {
        try {
            Files.deleteIfExists(sessionsDir.resolve(id + ".yml"));
        } catch (IOException ignored) {
        }
    }

    private static @Nullable String serializeItems(@Nullable ItemStack[] items) {
        if (items == null) return null;
        try {
            return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable ItemStack[] deserializeItems(@Nullable String encoded) {
        if (encoded == null) return null;
        try {
            return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable String serializeItem(@Nullable ItemStack item) {
        if (item == null) return null;
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable ItemStack deserializeItem(@Nullable String encoded) {
        if (encoded == null) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final class Snapshot {
        final ItemStack[] storage;
        final ItemStack[] armor;
        final ItemStack offhand;
        final ItemStack cursor;
        final org.bukkit.GameMode gameMode;
        final Boolean allowFlight;
        final Boolean flying;

        Snapshot(ItemStack[] storage, ItemStack[] armor, ItemStack offhand, ItemStack cursor,
                 org.bukkit.GameMode gameMode, Boolean allowFlight, Boolean flying) {
            this.storage = storage;
            this.armor = armor;
            this.offhand = offhand;
            this.cursor = cursor;
            this.gameMode = gameMode;
            this.allowFlight = allowFlight;
            this.flying = flying;
        }
    }

    private static void restorePlayerState(@NotNull Player player, @NotNull Snapshot snap) {
        if (snap.gameMode != null) player.setGameMode(snap.gameMode);
        if (snap.allowFlight != null) player.setAllowFlight(snap.allowFlight);
        if (snap.flying != null) player.setFlying(snap.flying && Boolean.TRUE.equals(snap.allowFlight));
    }
}

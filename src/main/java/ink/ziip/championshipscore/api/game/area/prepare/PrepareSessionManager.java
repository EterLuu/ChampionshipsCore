package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.area.prepare.gui.AnvilInputGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.AreaListGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.ListStepGui;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

    public @Nullable PrepareSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    @Override
    public void load() {
        flows.put(GameTypeEnum.Bingo, new ink.ziip.championshipscore.api.game.area.prepare.bingo.BingoPrepareFlow());
        flows.put(GameTypeEnum.ParkourTag, new ink.ziip.championshipscore.api.game.area.prepare.parkourtag.ParkourTagPrepareFlow());
        flows.put(GameTypeEnum.BattleBox, new ink.ziip.championshipscore.api.game.area.prepare.battlebox.BattleBoxPrepareFlow());
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
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                restoreSnapshot(p);
                deleteSnapshotFile(id);
            }
            // offline players: leave the snapshot file so restorePendingSnapshot can restore on next join
        }
        sessions.clear();
        snapshots.clear();
        if (listener != null) listener.unRegister();
    }

    // ── entry points ──────────────────────────────────────────────────────────────────────────

    public void openAreaListGui(@NotNull Player player, @NotNull GameTypeEnum gameType) {
        AreaListGui.open(this, player, gameType);
    }

    /** Called after the anvil confirms a new area name: create it via the manager then enter the session. */
    public void createAndEnter(@NotNull Player player, @NotNull GameTypeEnum gameType, @NotNull String name) {
        BaseGameInstanceManager<?> mgr = plugin.getGameManager().getAreaManager(gameType);
        if (mgr == null) {
            Utils.sendAdminError(player, "该游戏不可用");
            return;
        }
        if (!mgr.addArea(name)) {
            Utils.sendAdminError(player, "场地 &#fff566" + name + " &#ededed已存在");
            return;
        }
        enterSession(player, gameType, name);
    }

    public void enterSession(@NotNull Player player, @NotNull GameTypeEnum gameType, @NotNull String areaName) {
        PrepareFlowDefinition flow = flows.get(gameType);
        if (flow == null) {
            Utils.sendAdminError(player, "该游戏暂不支持 prepare");
            return;
        }
        BaseGameInstanceManager<?> mgr = plugin.getGameManager().getAreaManager(gameType);
        BaseGameInstance area = mgr == null ? null : mgr.getArea(areaName);
        if (area == null) {
            Utils.sendAdminError(player, "找不到场地 &#fff566" + areaName);
            return;
        }
        if (sessions.containsKey(player.getUniqueId())) exitSession(player);

        saveSnapshot(player);
        PrepareSession session = new PrepareSession(plugin, gameType, areaName, area, flow);
        sessions.put(player.getUniqueId(), session);
        PrepareModeInventory.apply(player, session);
        Utils.sendAdminSuccess(player, "进入 prepare &#bababa• &#fff566" + gameType + " &#696969/ &#fff566" + areaName);
        Utils.sendAdminInfo(player, "使用物品栏配置步骤 &#696969• 末影珍珠传送 &#696969• 屏障退出");
    }

    public void exitSession(@NotNull Player player) {
        PrepareSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        restoreSnapshot(player);
        snapshots.remove(player.getUniqueId());
        deleteSnapshotFile(player.getUniqueId());
        Utils.sendAdminSuccess(player, "已退出 prepare &#696969• 物品栏已还原");
    }

    // ── click routing (called by PrepareListener) ────────────────────────────────────────────

    public void handleStepClick(@NotNull Player player, @NotNull PrepareSession session, @NotNull String stepKey) {
        PrepareStep step = session.step(stepKey);
        if (step == null) return;
        if (step.captureType() != StepCaptureType.CONFIRM_WORLD
                && step.captureType() != StepCaptureType.STAMP
                && !session.getFlow().isInCorrectWorld(player, session.getTarget())) {
            Utils.sendAdminError(player, "请先前往当前地图世界 " + session.getTarget().worldName());
            return;
        }
        switch (step.captureType()) {
            case CONFIRM_WORLD, STAND_AND_RUN, WE_SELECTION, SCHEMATIC -> {
                String msg = step.capture(session, player);
                if (msg != null) player.sendMessage(msg);
                PrepareModeInventory.refresh(player, session);
            }
            case STAMP -> AnvilInputGui.openNumber(player, count -> {
                String msg = step.stamp(session, player, count);
                if (msg != null) player.sendMessage(msg);
                PrepareModeInventory.refresh(player, session);
            });
            case LIST -> ListStepGui.open(this, player, session, step);
        }
    }

    public void handleActionClick(@NotNull Player player, @NotNull PrepareSession session, @NotNull String action) {
        switch (action) {
            case "teleport" -> {
                Location dest = session.getFlow().copyZeroLocation(session.getTarget());
                if (dest == null || dest.getWorld() == null) {
                    Utils.sendAdminError(player, "目标世界未加载");
                    return;
                }
                player.teleport(dest);
                Utils.sendAdminSuccess(player, "已传送至 0 号场地");
            }
            case "exit" -> exitSession(player);
            default -> {
            }
        }
    }

    // ── inventory snapshot (in-memory + disk) ────────────────────────────────────────────────

    private void saveSnapshot(@NotNull Player player) {
        PlayerInventory inv = player.getInventory();
        Snapshot snap = new Snapshot(
                inv.getStorageContents(),
                inv.getArmorContents(),
                inv.getItemInOffHand(),
                player.getItemOnCursor());
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
        if (snap.offhand != null) inv.setItemInOffHand(snap.offhand);
        if (snap.cursor != null) player.setItemOnCursor(snap.cursor);
    }

    /** Crash recovery: if a snapshot file lingers from a non-clean exit, restore the real inventory. */
    public void restorePendingSnapshot(@NotNull Player player) {
        Snapshot snap = loadSnapshotFile(player.getUniqueId());
        if (snap == null) return;
        PlayerInventory inv = player.getInventory();
        if (snap.storage != null) inv.setStorageContents(snap.storage);
        if (snap.armor != null) inv.setArmorContents(snap.armor);
        if (snap.offhand != null) inv.setItemInOffHand(snap.offhand);
        if (snap.cursor != null) player.setItemOnCursor(snap.cursor);
        deleteSnapshotFile(player.getUniqueId());
        snapshots.remove(player.getUniqueId());
        Utils.sendAdminInfo(player, "检测到未结束的 prepare &#696969• 物品栏已还原");
    }

    private void saveSnapshotFile(@NotNull UUID id, @NotNull Snapshot snap) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("storage", serialize(snap.storage));
        y.set("armor", serialize(snap.armor));
        y.set("offhand", serialize(snap.offhand));
        y.set("cursor", serialize(snap.cursor));
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
        ItemStack[] storage = (ItemStack[]) deserialize(y.getString("storage"));
        ItemStack[] armor = (ItemStack[]) deserialize(y.getString("armor"));
        ItemStack offhand = (ItemStack) deserialize(y.getString("offhand"));
        ItemStack cursor = (ItemStack) deserialize(y.getString("cursor"));
        return new Snapshot(storage, armor, offhand, cursor);
    }

    private void deleteSnapshotFile(@NotNull UUID id) {
        try {
            Files.deleteIfExists(sessionsDir.resolve(id + ".yml"));
        } catch (IOException ignored) {
        }
    }

    private static @Nullable String serialize(@Nullable Object obj) {
        if (obj == null) return null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeObject(obj);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    private static @Nullable Object deserialize(@Nullable String s) {
        if (s == null) return null;
        try (BukkitObjectInputStream ois = new BukkitObjectInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(s)))) {
            return ois.readObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static final class Snapshot {
        final ItemStack[] storage;
        final ItemStack[] armor;
        final ItemStack offhand;
        final ItemStack cursor;

        Snapshot(ItemStack[] storage, ItemStack[] armor, ItemStack offhand, ItemStack cursor) {
            this.storage = storage;
            this.armor = armor;
            this.offhand = offhand;
            this.cursor = cursor;
        }
    }
}

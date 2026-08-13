package ink.ziip.championshipscore.api.rank;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.rank.entry.PlayerPointEntry;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Durable write-ahead store for score rows that have not yet been acknowledged by the database. */
final class PendingPointTransactionStore {
    private final ChampionshipsCore plugin;
    private final Path file;
    private final Map<UUID, PlayerPointEntry> pending = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> unreadable = new LinkedHashMap<>();

    PendingPointTransactionStore(@NotNull ChampionshipsCore plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("pending-point-transactions.yml");
    }

    synchronized List<PlayerPointEntry> load() {
        pending.clear();
        unreadable.clear();
        if (!Files.isRegularFile(file)) return List.of();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection root = yaml.getConfigurationSection("transactions");
        if (root == null) return List.of();

        for (String key : root.getKeys(false)) {
            try {
                UUID transactionId = UUID.fromString(key);
                String path = "transactions." + key + ".";
                PlayerPointEntry entry = PlayerPointEntry.builder()
                        .transactionId(transactionId)
                        .uuid(UUID.fromString(yaml.getString(path + "uuid", "")))
                        .username(yaml.getString(path + "username", ""))
                        .teamId(yaml.getInt(path + "team-id"))
                        .team(yaml.getString(path + "team", ""))
                        .rivalId(yaml.getInt(path + "rival-id"))
                        .rival(yaml.getString(path + "rival", ""))
                        .game(GameTypeEnum.valueOf(yaml.getString(path + "game", "")))
                        .area(yaml.getString(path + "area", ""))
                        .round(yaml.getString(path + "round", ""))
                        .points(yaml.getDouble(path + "points"))
                        .time(yaml.getString(path + "time", ""))
                        .valid(1)
                        .build();
                pending.put(transactionId, entry);
            } catch (Exception exception) {
                ConfigurationSection invalidSection = root.getConfigurationSection(key);
                if (invalidSection != null) {
                    unreadable.put(key, invalidSection.getValues(true));
                }
                plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Rank", "暂存事务",
                        "无法读取事务=" + key + "，该记录保留在暂存文件中"), exception);
            }
        }
        return new ArrayList<>(pending.values());
    }

    synchronized boolean stage(@NotNull PlayerPointEntry entry) {
        return stageAll(List.of(entry));
    }

    /** Atomically stages a whole settlement and persists the write-ahead file once. */
    synchronized boolean stageAll(@NotNull List<PlayerPointEntry> entries) {
        Map<UUID, PlayerPointEntry> previous = new LinkedHashMap<>();
        for (PlayerPointEntry entry : entries) {
            UUID transactionId = entry.getTransactionId();
            if (transactionId == null) throw new IllegalArgumentException("Score transaction id is required");
            previous.put(transactionId, pending.put(transactionId, entry));
        }
        if (save()) return true;
        previous.forEach((transactionId, entry) -> {
            if (entry == null) pending.remove(transactionId);
            else pending.put(transactionId, entry);
        });
        return false;
    }

    synchronized void complete(@NotNull UUID transactionId) {
        completeAll(List.of(transactionId));
    }

    /** Acknowledges a committed database batch with a single atomic file replacement. */
    synchronized void completeAll(@NotNull Collection<UUID> transactionIds) {
        Map<UUID, PlayerPointEntry> removed = new LinkedHashMap<>();
        for (UUID transactionId : transactionIds) {
            PlayerPointEntry entry = pending.remove(transactionId);
            if (entry != null) removed.put(transactionId, entry);
        }
        if (!removed.isEmpty() && !save()) removed.forEach(pending::put);
    }

    synchronized boolean renameArea(@NotNull GameTypeEnum game, @NotNull String oldArea,
                                    @NotNull String newArea) {
        Map<UUID, PlayerPointEntry> replacements = new LinkedHashMap<>();
        for (Map.Entry<UUID, PlayerPointEntry> pendingEntry : pending.entrySet()) {
            PlayerPointEntry entry = pendingEntry.getValue();
            if (entry.getGame() != game || !entry.getArea().equalsIgnoreCase(oldArea)) continue;
            replacements.put(pendingEntry.getKey(), PlayerPointEntry.builder()
                    .id(entry.getId()).transactionId(entry.getTransactionId())
                    .uuid(entry.getUuid()).username(entry.getUsername())
                    .teamId(entry.getTeamId()).team(entry.getTeam())
                    .rivalId(entry.getRivalId()).rival(entry.getRival())
                    .game(entry.getGame()).area(newArea).round(entry.getRound())
                    .points(entry.getPoints()).time(entry.getTime()).valid(entry.getValid()).build());
        }
        if (replacements.isEmpty()) return true;
        Map<UUID, PlayerPointEntry> originals = new LinkedHashMap<>();
        replacements.forEach((id, replacement) -> originals.put(id, pending.put(id, replacement)));
        if (save()) return true;
        originals.forEach(pending::put);
        return false;
    }

    private boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Map<String, Object>> unreadableEntry : unreadable.entrySet()) {
            String path = "transactions." + unreadableEntry.getKey() + ".";
            for (Map.Entry<String, Object> value : unreadableEntry.getValue().entrySet()) {
                if (!(value.getValue() instanceof ConfigurationSection)) {
                    yaml.set(path + value.getKey(), value.getValue());
                }
            }
        }
        for (Map.Entry<UUID, PlayerPointEntry> pendingEntry : pending.entrySet()) {
            String path = "transactions." + pendingEntry.getKey() + ".";
            PlayerPointEntry entry = pendingEntry.getValue();
            yaml.set(path + "uuid", entry.getUuid().toString());
            yaml.set(path + "username", entry.getUsername());
            yaml.set(path + "team-id", entry.getTeamId());
            yaml.set(path + "team", entry.getTeam());
            yaml.set(path + "rival-id", entry.getRivalId());
            yaml.set(path + "rival", entry.getRival());
            yaml.set(path + "game", entry.getGame().name());
            yaml.set(path + "area", entry.getArea());
            yaml.set(path + "round", entry.getRound());
            yaml.set(path + "points", entry.getPoints());
            yaml.set(path + "time", entry.getTime());
        }

        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            yaml.save(temporary.toFile());
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Rank", "暂存事务",
                    "无法写入积分暂存文件=" + file), exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
            return false;
        }
    }
}

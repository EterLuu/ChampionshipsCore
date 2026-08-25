package ink.ziip.championshipscore.api.game.area.rename;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.Map;
import java.util.List;

/** Crash-conscious config filename + internal name rewrite with an in-memory rollback image. */
final class MapConfigFileRename {
    record State(@NotNull Path oldPath, @NotNull Path newPath, byte @NotNull [] originalBytes) {
    }

    private MapConfigFileRename() {
    }

    static @NotNull State rename(@NotNull Path oldPath, @NotNull Path newPath,
                                 @NotNull String newName) throws Exception {
        return rename(oldPath, newPath, newName, null, null);
    }

    static @NotNull State rename(@NotNull Path oldPath, @NotNull Path newPath, @NotNull String newName,
                                 @Nullable String oldWorldName, @Nullable String newWorldName) throws Exception {
        if (!Files.isRegularFile(oldPath)) throw new IllegalStateException("原配置文件不存在：" + oldPath);
        if (Files.exists(newPath)) throw new IllegalStateException("目标配置文件已存在：" + newPath);
        byte[] original = Files.readAllBytes(oldPath);
        Path temporary = oldPath.resolveSibling("." + oldPath.getFileName() + ".rename-" + UUID.randomUUID());
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(oldPath.toFile());
            yaml.set("name", newName);
            if (oldWorldName != null && newWorldName != null)
                rewriteWorldReferences(yaml, oldWorldName, newWorldName);
            yaml.save(temporary.toFile());
            move(temporary, newPath, false);
            try {
                Files.delete(oldPath);
            } catch (Exception exception) {
                Files.deleteIfExists(newPath);
                throw exception;
            }
            return new State(oldPath, newPath, original);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void rollback(@NotNull State state) throws Exception {
        Path temporary = state.oldPath().resolveSibling(
                "." + state.oldPath().getFileName() + ".rollback-" + UUID.randomUUID());
        try {
            Files.write(temporary, state.originalBytes());
            move(temporary, state.oldPath(), true);
            Files.deleteIfExists(state.newPath());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void move(Path from, Path to, boolean replace) throws Exception {
        try {
            if (replace) Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            if (replace) Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(from, to);
        }
    }

    private static void rewriteWorldReferences(@NotNull org.bukkit.configuration.ConfigurationSection section,
                                               @NotNull String oldWorldName, @NotNull String newWorldName) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof org.bukkit.configuration.ConfigurationSection child) {
                rewriteWorldReferences(child, oldWorldName, newWorldName);
                continue;
            }
            Object rewritten = rewriteValue(key, value, oldWorldName, newWorldName);
            if (rewritten != value) section.set(key, rewritten);
        }
    }

    private static Object rewriteValue(@NotNull String key, @Nullable Object value,
                                       @NotNull String oldWorldName, @NotNull String newWorldName) {
        if (value instanceof String text) {
            if (("world".equals(key) || "world-name".equals(key)) && oldWorldName.equals(text))
                return newWorldName;
            if ("world_key".equals(key) && ("minecraft:" + oldWorldName).equals(text))
                return "minecraft:" + newWorldName;
            return text.startsWith(oldWorldName + ":")
                    ? newWorldName + text.substring(oldWorldName.length()) : value;
        }
        if (value instanceof List<?> values) {
            List<Object> rewritten = new java.util.ArrayList<>(values.size());
            boolean changed = false;
            for (Object entry : values) {
                Object replacement = rewriteValue("", entry, oldWorldName, newWorldName);
                rewritten.add(replacement);
                changed |= replacement != entry;
            }
            return changed ? rewritten : value;
        }
        if (value instanceof Map<?, ?> values) {
            Map<Object, Object> rewritten = new java.util.LinkedHashMap<>();
            boolean changed = false;
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                String childKey = String.valueOf(entry.getKey());
                Object replacement = rewriteValue(childKey, entry.getValue(), oldWorldName, newWorldName);
                rewritten.put(entry.getKey(), replacement);
                changed |= replacement != entry.getValue();
            }
            return changed ? rewritten : value;
        }
        return value;
    }
}

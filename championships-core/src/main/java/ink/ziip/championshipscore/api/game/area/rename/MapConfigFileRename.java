package ink.ziip.championshipscore.api.game.area.rename;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Crash-conscious config filename + internal name rewrite with an in-memory rollback image. */
final class MapConfigFileRename {
    record State(@NotNull Path oldPath, @NotNull Path newPath, byte @NotNull [] originalBytes) {
    }

    private MapConfigFileRename() {
    }

    static @NotNull State rename(@NotNull Path oldPath, @NotNull Path newPath,
                                 @NotNull String newName) throws Exception {
        if (!Files.isRegularFile(oldPath)) throw new IllegalStateException("原配置文件不存在：" + oldPath);
        if (Files.exists(newPath)) throw new IllegalStateException("目标配置文件已存在：" + newPath);
        byte[] original = Files.readAllBytes(oldPath);
        Path temporary = oldPath.resolveSibling("." + oldPath.getFileName() + ".rename-" + UUID.randomUUID());
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(oldPath.toFile());
            yaml.set("name", newName);
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
}

package ink.ziip.championshipscore.api.game.area.rename;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Renames map-specific files which are not covered by the normal map configuration file. */
final class MapAssetRename {
    record DirectoryMove(Path oldPath, Path newPath, boolean moved) {
    }

    record State(Path oldManifest, Path newManifest, byte[] manifestBytes,
                 List<DirectoryMove> directories, List<Path> createdDirectories) {
    }

    private MapAssetRename() {
    }

    static void validate(@NotNull Path pluginFolder, @NotNull GameTypeEnum game,
                         @NotNull String oldRegistration, @NotNull String oldAssetName,
                         @NotNull String newName) {
        if (game == GameTypeEnum.BuildMart) {
            Path root = pluginFolder.resolve("buildmart");
            Path oldManifest = root.resolve("material-manifests").resolve(oldAssetName + ".yml");
            Path newManifest = root.resolve("material-manifests").resolve(newName + ".yml");
            validateManifest(oldManifest, newManifest);
        }
        for (DirectoryMove move : directoryMoves(pluginFolder, game, oldRegistration, oldAssetName, newName)) {
            if (Files.exists(move.oldPath()) && !Files.isDirectory(move.oldPath()))
                throw new IllegalStateException("地图资产路径不是目录：" + move.oldPath());
            if (Files.exists(move.newPath()) && !move.oldPath().equals(move.newPath()))
                throw new IllegalStateException("目标地图资产目录已存在：" + move.newPath());
        }
    }

    static @NotNull State rename(@NotNull Path pluginFolder, @NotNull GameTypeEnum game,
                                 @NotNull String oldRegistration, @NotNull String oldAssetName,
                                 @NotNull String newName) throws Exception {
        return rename(pluginFolder, game, oldRegistration, oldAssetName, newName, null, null);
    }

    static @NotNull State rename(@NotNull Path pluginFolder, @NotNull GameTypeEnum game,
                                 @NotNull String oldRegistration, @NotNull String oldAssetName,
                                 @NotNull String newName, @Nullable String oldWorldName,
                                 @Nullable String newWorldName) throws Exception {
        Path root = pluginFolder.resolve("buildmart");
        Path oldManifest = root.resolve("material-manifests").resolve(oldAssetName + ".yml");
        Path newManifest = root.resolve("material-manifests").resolve(newName + ".yml");
        validate(pluginFolder, game, oldRegistration, oldAssetName, newName);
        byte[] manifestBytes = null;
        List<DirectoryMove> movedDirectories = new ArrayList<>();
        List<Path> createdDirectories = new ArrayList<>();
        try {
            if (game == GameTypeEnum.BuildMart && Files.isRegularFile(oldManifest)) {
                manifestBytes = Files.readAllBytes(oldManifest);
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(oldManifest.toFile());
                yaml.set("map", newName);
                if (oldWorldName != null && newWorldName != null) {
                    yaml.set("world", newWorldName);
                }
                moveYaml(yaml, newManifest);
                Files.delete(oldManifest);
            }
            for (DirectoryMove candidate : directoryMoves(pluginFolder, game,
                    oldRegistration, oldAssetName, newName)) {
                if (!Files.exists(candidate.oldPath())) continue;
                createParentDirectories(candidate.newPath().getParent(), createdDirectories);
                move(candidate.oldPath(), candidate.newPath(), false);
                movedDirectories.add(new DirectoryMove(candidate.oldPath(), candidate.newPath(), true));
            }
            return new State(oldManifest, newManifest, manifestBytes, List.copyOf(movedDirectories),
                    List.copyOf(createdDirectories));
        } catch (Exception failure) {
            rollback(new State(oldManifest, newManifest, manifestBytes, List.copyOf(movedDirectories),
                    List.copyOf(createdDirectories)));
            throw failure;
        }
    }

    static void rollback(@NotNull State state) throws Exception {
        List<DirectoryMove> directories = state.directories();
        for (int index = directories.size() - 1; index >= 0; index--) {
            DirectoryMove directory = directories.get(index);
            if (!directory.moved() || !Files.exists(directory.newPath())) continue;
            if (Files.exists(directory.oldPath()))
                throw new IllegalStateException("无法回滚地图资产，原目录已重新出现："
                        + directory.oldPath());
            move(directory.newPath(), directory.oldPath(), false);
        }
        if (state.manifestBytes() != null) {
            Files.deleteIfExists(state.newManifest());
            Files.createDirectories(state.oldManifest().getParent());
            Path temporary = state.oldManifest().resolveSibling(
                    "." + state.oldManifest().getFileName() + ".rollback-" + UUID.randomUUID());
            try {
                Files.write(temporary, state.manifestBytes());
                move(temporary, state.oldManifest(), true);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        List<Path> createdDirectories = state.createdDirectories();
        for (int index = createdDirectories.size() - 1; index >= 0; index--) {
            Path directory = createdDirectories.get(index);
            if (Files.isDirectory(directory)) Files.deleteIfExists(directory);
        }
    }

    private static void move(Path from, Path to, boolean replace) throws Exception {
        try {
            if (replace) Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            if (replace) Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(from, to);
        }
    }

    private static void moveYaml(@NotNull YamlConfiguration yaml, @NotNull Path target) throws Exception {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
        try {
            yaml.save(temporary.toFile());
            move(temporary, target, false);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void createParentDirectories(Path parent, List<Path> createdDirectories) throws Exception {
        List<Path> missing = new ArrayList<>();
        for (Path path = parent; path != null && !Files.exists(path); path = path.getParent()) {
            missing.add(path);
        }
        Files.createDirectories(parent);
        for (int index = missing.size() - 1; index >= 0; index--) {
            createdDirectories.add(missing.get(index));
        }
    }

    private static void validateManifest(Path oldManifest, Path newManifest) {
        if (Files.exists(oldManifest) && !Files.isRegularFile(oldManifest))
            throw new IllegalStateException("材料清单路径不是文件：" + oldManifest.getFileName());
        if (Files.exists(newManifest) && !oldManifest.equals(newManifest))
            throw new IllegalStateException("目标材料清单已存在：" + newManifest.getFileName());
    }

    private static List<DirectoryMove> directoryMoves(Path pluginFolder, GameTypeEnum game,
                                                       String oldRegistration, String oldAssetName,
                                                       String newName) {
        if (game == GameTypeEnum.BuildMart) {
            Path root = pluginFolder.resolve("buildmart").resolve("schematics");
            Path oldRegistrationDirectory = root.resolve(oldRegistration);
            Path newDirectory = root.resolve(newName);
            if (oldRegistration.equals(oldAssetName)) {
                return List.of(new DirectoryMove(oldRegistrationDirectory, newDirectory, false));
            }
            return List.of(
                    new DirectoryMove(oldRegistrationDirectory, newDirectory, false),
                    new DirectoryMove(root.resolve(oldAssetName).resolve("material-zones"),
                            newDirectory.resolve("material-zones"), false));
        }
        String folder = switch (game) {
            case BattleBox -> "battlebox";
            case ParkourTag -> "parkourtag";
            case TNTRun -> "tntrun";
            default -> null;
        };
        if (folder == null) return List.of();
        Path root = pluginFolder.resolve(folder).resolve("schematics");
        return List.of(new DirectoryMove(root.resolve(oldRegistration), root.resolve(newName), false));
    }

}

package ink.ziip.championshipscore.api.game.area.rename;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapAssetRenameTest {
    @TempDir
    Path pluginFolder;

    @Test
    void movesBuildMartManifestAndSnapshotsAndCanRollback() throws Exception {
        Path manifest = pluginFolder.resolve("buildmart/material-manifests/old-map.yml");
        Path snapshot = pluginFolder.resolve("buildmart/schematics/old-map/material-zones/zone.schem");
        Files.createDirectories(manifest.getParent());
        Files.createDirectories(snapshot.getParent());
        Files.writeString(manifest, "map: old-map\nworld: buildmart\n");
        Files.write(snapshot, new byte[]{1, 2, 3});
        byte[] originalManifest = Files.readAllBytes(manifest);

        MapAssetRename.State state = MapAssetRename.rename(pluginFolder, GameTypeEnum.BuildMart,
                "old-map", "old-map", "new-map", "buildmart_old-map", "buildmart_new-map");

        Path renamedManifest = pluginFolder.resolve("buildmart/material-manifests/new-map.yml");
        Path renamedSnapshot = pluginFolder.resolve("buildmart/schematics/new-map/material-zones/zone.schem");
        assertFalse(Files.exists(manifest));
        assertFalse(Files.exists(snapshot));
        assertTrue(Files.isRegularFile(renamedManifest));
        assertTrue(Files.isRegularFile(renamedSnapshot));
        assertEquals("new-map", YamlConfiguration.loadConfiguration(renamedManifest.toFile()).getString("map"));
        assertEquals("buildmart_new-map", YamlConfiguration.loadConfiguration(renamedManifest.toFile())
                .getString("world"));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(renamedSnapshot));

        MapAssetRename.rollback(state);

        assertTrue(Files.isRegularFile(manifest));
        assertTrue(Files.isRegularFile(snapshot));
        assertFalse(Files.exists(renamedManifest));
        assertFalse(Files.exists(renamedSnapshot));
        assertArrayEquals(originalManifest, Files.readAllBytes(manifest));
    }

    @Test
    void rejectsExistingTargetBuildMartAssets() throws Exception {
        Path target = pluginFolder.resolve("buildmart/material-manifests/new-map.yml");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "map: new-map\n");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> MapAssetRename.validate(pluginFolder, GameTypeEnum.BuildMart,
                        "old-map", "old-map", "new-map"));

        assertTrue(exception.getMessage().contains("目标材料清单已存在"));
    }

    @Test
    void movesSchematicDirectoriesForOtherGames() throws Exception {
        for (GameTypeEnum game : new GameTypeEnum[]{GameTypeEnum.ParkourTag,
                GameTypeEnum.BattleBox, GameTypeEnum.TNTRun}) {
            Path source = pluginFolder.resolve(gameFolder(game) + "/schematics/old-map/arena.schem");
            Files.createDirectories(source.getParent());
            Files.write(source, game.name().getBytes());

            MapAssetRename.State state = MapAssetRename.rename(pluginFolder, game,
                    "old-map", "old-map", "new-map");
            Path target = pluginFolder.resolve(gameFolder(game) + "/schematics/new-map/arena.schem");
            assertFalse(Files.exists(source));
            assertArrayEquals(game.name().getBytes(), Files.readAllBytes(target));

            MapAssetRename.rollback(state);
            assertTrue(Files.isRegularFile(source));
            assertFalse(Files.exists(target));
        }
    }

    @Test
    void movesBuildMartAssetsFromLegacyDisplayName() throws Exception {
        Path manifest = pluginFolder.resolve("buildmart/material-manifests/old-display.yml");
        Path base = pluginFolder.resolve("buildmart/schematics/old-map/base.schem");
        Path snapshot = pluginFolder.resolve("buildmart/schematics/old-display/material-zones/zone.schem");
        Files.createDirectories(manifest.getParent());
        Files.createDirectories(base.getParent());
        Files.createDirectories(snapshot.getParent());
        Files.writeString(manifest, "map: old-display\n");
        Files.write(base, new byte[]{1});
        Files.write(snapshot, new byte[]{2});

        MapAssetRename.State state = MapAssetRename.rename(pluginFolder, GameTypeEnum.BuildMart,
                "old-map", "old-display", "new-map");

        Path renamedManifest = pluginFolder.resolve("buildmart/material-manifests/new-map.yml");
        Path renamedBase = pluginFolder.resolve("buildmart/schematics/new-map/base.schem");
        Path renamedSnapshot = pluginFolder.resolve("buildmart/schematics/new-map/material-zones/zone.schem");
        assertFalse(Files.exists(manifest));
        assertFalse(Files.exists(base));
        assertFalse(Files.exists(snapshot));
        assertEquals("new-map", YamlConfiguration.loadConfiguration(renamedManifest.toFile()).getString("map"));
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(renamedBase));
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(renamedSnapshot));

        MapAssetRename.rollback(state);

        assertTrue(Files.isRegularFile(manifest));
        assertTrue(Files.isRegularFile(base));
        assertTrue(Files.isRegularFile(snapshot));
        assertFalse(Files.exists(renamedManifest));
        assertFalse(Files.exists(renamedBase));
        assertFalse(Files.exists(renamedSnapshot));
    }

    private static String gameFolder(GameTypeEnum game) {
        return switch (game) {
            case ParkourTag -> "parkourtag";
            case BattleBox -> "battlebox";
            case TNTRun -> "tntrun";
            default -> throw new AssertionError(game);
        };
    }
}

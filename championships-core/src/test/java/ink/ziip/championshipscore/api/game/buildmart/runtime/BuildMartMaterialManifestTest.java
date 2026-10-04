package ink.ziip.championshipscore.api.game.buildmart.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.buildmart.config.BuildMartConfig;
import ink.ziip.championshipscore.api.game.buildmart.model.BuildMartMaterialZone;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class BuildMartMaterialManifestTest {
    @TempDir Path directory;

    @Test
    void submissionUsesVerifiedZoneMaterialsRatherThanUnverifiedTotals() throws Exception {
        var config = fixture();
        var yaml = YamlConfiguration.loadConfiguration(config.manifest);
        yaml.set("totals.materials.minecraft:diamond_block", 999);
        yaml.save(config.manifest);
        var inventory = BuildMartMaterialManifest.readSubmissionInventory(config);
        assertTrue(inventory.available());
        assertEquals(Map.of(Material.STONE, 1L), inventory.materials());
    }

    @Test
    void missingOrChangedSnapshotAndChangedBoundsRefuseSubmission() throws Exception {
        var config = fixture();
        var original = config.zones;
        config.zones =
                List.of(
                        new BuildMartMaterialZone(
                                original.getFirst().snapshotId(),
                                new Vector(1, 0, 0),
                                new Vector(1, 0, 0)));
        assertFalse(BuildMartMaterialManifest.readSubmissionInventory(config).available());
        config.zones = original;
        Files.writeString(config.snapshot.toPath(), "changed snapshot");
        assertFalse(BuildMartMaterialManifest.readSubmissionInventory(config).available());
        Files.delete(config.snapshot.toPath());
        assertFalse(BuildMartMaterialManifest.readSubmissionInventory(config).available());
    }

    @Test
    void missingManifestAndRemovedZoneAreNotTreatedAsAnEmptyApproval() throws Exception {
        var config = fixture();
        config.zones = List.of();
        assertFalse(BuildMartMaterialManifest.readSubmissionInventory(config).available());
        Files.delete(config.manifest.toPath());
        assertFalse(BuildMartMaterialManifest.readSubmissionInventory(config).available());
    }

    private TestConfig fixture() throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var config =
                (TestConfig)
                        ((sun.misc.Unsafe) unsafeField.get(null))
                                .allocateInstance(TestConfig.class);
        config.manifest = directory.resolve("manifest.yml").toFile();
        config.snapshot = directory.resolve("zone.schem").toFile();
        Files.writeString(config.snapshot.toPath(), "snapshot");
        var zone =
                new BuildMartMaterialZone(
                        UUID.randomUUID(), new Vector(0, 0, 0), new Vector(0, 0, 0));
        config.zones = List.of(zone);
        var yaml = new YamlConfiguration();
        yaml.set("dont-edit-this.version", 2);
        yaml.set(
                "zones",
                List.of(
                        Map.of(
                                "snapshot-id",
                                zone.snapshotId().toString(),
                                "pos1",
                                Map.of("x", 0, "y", 0, "z", 0),
                                "pos2",
                                Map.of("x", 0, "y", 0, "z", 0),
                                "non-air-blocks",
                                1L,
                                "snapshot-size",
                                config.snapshot.length(),
                                "snapshot-last-modified",
                                config.snapshot.lastModified(),
                                "materials",
                                Map.of("minecraft:stone", 1L),
                                "block-data",
                                Map.of("minecraft:stone", 1L))));
        yaml.save(config.manifest);
        return config;
    }

    private static class TestConfig extends BuildMartConfig {
        File manifest, snapshot;
        List<BuildMartMaterialZone> zones;

        private TestConfig() {
            super(null, "test");
        }

        @Override
        public File getMaterialManifestFile() {
            return manifest;
        }

        @Override
        public File getMaterialZoneSnapshotFile(BuildMartMaterialZone zone) {
            return snapshot;
        }

        @Override
        public List<BuildMartMaterialZone> getMaterialZones() {
            return zones;
        }

        @Override
        public BuildMartMaterialIsland classifyMaterialZone(BuildMartMaterialZone zone) {
            return null;
        }
    }
}

package ink.ziip.championshipscore.api.test;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

class TestDeveloperStateStoreTest {
    @Test
    void airMaterialIsNotSerializableInventoryData() {
        assertFalse(TestDeveloperStateStore.hasItemMaterial(Material.AIR));
        assertFalse(TestDeveloperStateStore.hasItemMaterial(Material.CAVE_AIR));
        assertFalse(TestDeveloperStateStore.hasItemMaterial(Material.VOID_AIR));
        assertTrue(TestDeveloperStateStore.hasItemMaterial(Material.STONE));
    }

    @Test
    void snapshotIsDurableAndCaptureDoesNotOverwriteOriginal() throws Exception {
        Path directory = Files.createTempDirectory("cc-developer-state-");
        TestDeveloperStateStore store = new TestDeveloperStateStore(directory);
        UUID uuid = UUID.randomUUID();
        Player player = PlayerFixtures.player(uuid, "developer");
        store.captureIfAbsent(player);
        store.captureIfAbsent(player);
        assertTrue(store.contains(uuid));
        TestDeveloperStateStore.Snapshot snapshot = store.get(uuid);
        assertEquals(uuid, snapshot.uuid());
        assertEquals(36, snapshot.inventory().length);
        assertEquals(4, snapshot.armor().length);
        assertEquals(1, snapshot.cursor().length);
        assertEquals(1, snapshot.offHand().length);
        assertEquals(27, snapshot.enderChest().length);
        assertEquals(1.25, snapshot.x());
        assertEquals(64.5, snapshot.y());
        assertEquals(-2.75, snapshot.z());
        assertEquals(33F, snapshot.yaw());
        assertEquals(-12F, snapshot.pitch());
        assertEquals("CREATIVE", snapshot.gameMode());
        assertEquals(7, snapshot.level());
        assertEquals(123, snapshot.totalExperience());
        assertEquals(0.75F, snapshot.experience());
        assertEquals(13.5, snapshot.health());
        assertEquals(8, snapshot.food());
        assertEquals(4.5F, snapshot.saturation());
        assertEquals(1.25F, snapshot.exhaustion());
        assertEquals(90, snapshot.fireTicks());
        assertEquals(3.5F, snapshot.fallDistance());
        assertTrue(snapshot.allowFlight());
        assertTrue(snapshot.flying());
        assertEquals(0.12F, snapshot.flySpeed());
        assertEquals(0.27F, snapshot.walkSpeed());
        assertEquals(1, store.uuids().size());
        TestDeveloperStateStore.Snapshot reloaded =
                new TestDeveloperStateStore(directory).get(uuid);
        assertEquals(snapshot.uuid(), reloaded.uuid());
        assertEquals(snapshot.world(), reloaded.world());
        assertArrayEquals(snapshot.inventory(), reloaded.inventory());
        assertArrayEquals(snapshot.armor(), reloaded.armor());
        assertArrayEquals(snapshot.cursor(), reloaded.cursor());
        assertArrayEquals(snapshot.offHand(), reloaded.offHand());
        assertArrayEquals(snapshot.enderChest(), reloaded.enderChest());
        assertEquals(snapshot.x(), reloaded.x());
        assertEquals(snapshot.y(), reloaded.y());
        assertEquals(snapshot.z(), reloaded.z());
        assertEquals(snapshot.yaw(), reloaded.yaw());
        assertEquals(snapshot.pitch(), reloaded.pitch());
        assertEquals(snapshot.gameMode(), reloaded.gameMode());
        assertEquals(snapshot.level(), reloaded.level());
        assertEquals(snapshot.totalExperience(), reloaded.totalExperience());
        assertEquals(snapshot.experience(), reloaded.experience());
        assertEquals(snapshot.health(), reloaded.health());
        assertEquals(snapshot.food(), reloaded.food());
        assertEquals(snapshot.saturation(), reloaded.saturation());
        assertEquals(snapshot.exhaustion(), reloaded.exhaustion());
        assertEquals(snapshot.fireTicks(), reloaded.fireTicks());
        assertEquals(snapshot.fallDistance(), reloaded.fallDistance());
        assertEquals(snapshot.allowFlight(), reloaded.allowFlight());
        assertEquals(snapshot.flying(), reloaded.flying());
        assertEquals(snapshot.flySpeed(), reloaded.flySpeed());
        assertEquals(snapshot.walkSpeed(), reloaded.walkSpeed());
        assertEquals(snapshot.effects(), reloaded.effects());
    }

    @Test
    void readsVersionOneSnapshotsWithoutCursorField() throws Exception {
        Path directory = Files.createTempDirectory("cc-developer-state-v1-");
        UUID uuid = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        try (var output =
                new DataOutputStream(Files.newOutputStream(directory.resolve(uuid + ".bin")))) {
            output.writeInt(0x43434453);
            output.writeInt(1);
            writeUuid(output, uuid);
            writeUuid(output, world);
            output.writeDouble(4.5);
            output.writeDouble(72.0);
            output.writeDouble(-8.25);
            output.writeFloat(90F);
            output.writeFloat(5F);
            output.writeUTF("SURVIVAL");
            writeEmptyItems(output, 36);
            writeEmptyItems(output, 4);
            writeEmptyItems(output, 1);
            writeEmptyItems(output, 27);
            output.writeInt(3);
            output.writeInt(42);
            output.writeFloat(0.5F);
            output.writeDouble(17.0);
            output.writeInt(14);
            output.writeFloat(6.0F);
            output.writeFloat(0.25F);
            output.writeInt(0);
            output.writeFloat(0F);
            output.writeBoolean(false);
            output.writeBoolean(false);
            output.writeFloat(0.1F);
            output.writeFloat(0.2F);
            output.writeInt(0);
        }

        var snapshot = new TestDeveloperStateStore(directory).get(uuid);

        assertEquals(uuid, snapshot.uuid());
        assertEquals(world, snapshot.world());
        assertEquals(1, snapshot.cursor().length);
        assertNull(snapshot.cursor()[0]);
        assertEquals(27, snapshot.enderChest().length);
        assertEquals(17.0, snapshot.health());
    }

    @Test
    void corruptSnapshotIsRejectedAndRetained() throws Exception {
        Path directory = Files.createTempDirectory("cc-developer-state-");
        UUID uuid = UUID.randomUUID();
        Path file = directory.resolve(uuid + ".bin");
        Files.writeString(file, "corrupt");
        TestDeveloperStateStore store = new TestDeveloperStateStore(directory);
        assertThrows(IllegalStateException.class, () -> store.get(uuid));
        assertTrue(Files.exists(file));
    }

    private static void writeUuid(DataOutputStream output, UUID uuid) throws Exception {
        output.writeLong(uuid.getMostSignificantBits());
        output.writeLong(uuid.getLeastSignificantBits());
    }

    private static void writeEmptyItems(DataOutputStream output, int count) throws Exception {
        output.writeInt(count);
        for (int i = 0; i < count; i++) output.writeBoolean(false);
    }

    static final class PlayerFixtures {
        static Player player(UUID uuid, String name) {
            World world =
                    ink.ziip.championshipscore.support.TestObjects.proxy(
                            World.class,
                            (p, m, a) ->
                                    m.getName().equals("getUID")
                                            ? UUID.fromString(
                                                    "11111111-2222-3333-4444-555555555555")
                                            : null);
            var inventory =
                    ink.ziip.championshipscore.support.TestObjects.proxy(
                            org.bukkit.inventory.PlayerInventory.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getStorageContents" ->
                                                new org.bukkit.inventory.ItemStack[36];
                                        case "getArmorContents" ->
                                                new org.bukkit.inventory.ItemStack[4];
                                        case "getItemInOffHand" -> null;
                                        default -> null;
                                    });
            var ender =
                    ink.ziip.championshipscore.support.TestObjects.proxy(
                            org.bukkit.inventory.Inventory.class,
                            (p, m, a) ->
                                    m.getName().equals("getContents")
                                            ? new org.bukkit.inventory.ItemStack[27]
                                            : null);
            return ink.ziip.championshipscore.support.TestObjects.proxy(
                    Player.class,
                    (p, m, a) ->
                            switch (m.getName()) {
                                case "getUniqueId" -> uuid;
                                case "getName" -> name;
                                case "getLocation" ->
                                        new org.bukkit.Location(
                                                world, 1.25, 64.5, -2.75, 33F, -12F);
                                case "getInventory" -> inventory;
                                case "getEnderChest" -> ender;
                                case "getActivePotionEffects" -> java.util.Set.of();
                                case "getGameMode" -> org.bukkit.GameMode.CREATIVE;
                                case "getLevel" -> 7;
                                case "getTotalExperience" -> 123;
                                case "getFoodLevel" -> 8;
                                case "getFireTicks" -> 90;
                                case "getExp" -> 0.75F;
                                case "getSaturation" -> 4.5F;
                                case "getExhaustion" -> 1.25F;
                                case "getFallDistance" -> 3.5F;
                                case "getFlySpeed" -> 0.12F;
                                case "getWalkSpeed" -> 0.27F;
                                case "getHealth" -> 13.5D;
                                case "getMaxHealth" -> 20D;
                                case "getAllowFlight", "isFlying" -> true;
                                case "getItemOnCursor" -> null;
                                default -> null;
                            });
        }
    }
}

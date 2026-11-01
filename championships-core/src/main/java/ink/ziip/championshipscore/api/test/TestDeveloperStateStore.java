package ink.ziip.championshipscore.api.test;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Durable original state for developers temporarily seated in unattended test games. */
final class TestDeveloperStateStore {
    private static final int MAGIC = 0x43434453;
    private static final int VERSION = 2;
    private static final int MAX_ITEMS = 128;
    private static final int MAX_ITEM_BYTES = 1_048_576;
    private final Path directory;
    private final Map<UUID, Snapshot> memory = new HashMap<>();

    record Effect(
            String key,
            int duration,
            int amplifier,
            boolean ambient,
            boolean particles,
            boolean icon) {}

    record Snapshot(
            UUID uuid,
            UUID world,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String gameMode,
            byte[][] inventory,
            byte[][] armor,
            byte[][] offHand,
            byte[][] cursor,
            byte[][] enderChest,
            int level,
            int totalExperience,
            float experience,
            double health,
            int food,
            float saturation,
            float exhaustion,
            int fireTicks,
            float fallDistance,
            boolean allowFlight,
            boolean flying,
            float flySpeed,
            float walkSpeed,
            List<Effect> effects) {}

    TestDeveloperStateStore(Path directory) {
        this.directory = directory == null ? null : directory.toAbsolutePath();
    }

    boolean contains(UUID uuid) {
        if (directory == null) return memory.containsKey(uuid);
        return Files.exists(path(uuid));
    }

    synchronized List<UUID> uuids() {
        if (directory == null) return List.copyOf(memory.keySet());
        if (Files.notExists(directory)) return List.of();
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".bin"))
                    .map(path -> path.getFileName().toString())
                    .map(name -> name.substring(0, name.length() - 4))
                    .map(UUID::fromString)
                    .toList();
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException(
                    "Cannot list developer state snapshots: " + directory, failure);
        }
    }

    Snapshot get(UUID uuid) {
        if (directory == null) return memory.get(uuid);
        Path file = path(uuid);
        if (Files.notExists(file)) return null;
        try (var input = new DataInputStream(Files.newInputStream(file))) {
            if (input.readInt() != MAGIC) throw new IOException("invalid developer state header");
            int version = input.readInt();
            if (version < 1 || version > VERSION)
                throw new IOException("unsupported developer state version");
            UUID storedUuid = readUuid(input);
            if (!uuid.equals(storedUuid)) throw new IOException("developer state UUID mismatch");
            UUID world = readUuid(input);
            double x = input.readDouble();
            double y = input.readDouble();
            double z = input.readDouble();
            float yaw = input.readFloat();
            float pitch = input.readFloat();
            String gameMode = input.readUTF();
            byte[][] inventory = readItems(input);
            byte[][] armor = readItems(input);
            byte[][] offHand = readItems(input);
            byte[][] cursor = version >= 2 ? readItems(input) : new byte[1][];
            byte[][] enderChest = readItems(input);
            int level = input.readInt();
            int totalExperience = input.readInt();
            float experience = input.readFloat();
            double health = input.readDouble();
            int food = input.readInt();
            float saturation = input.readFloat();
            float exhaustion = input.readFloat();
            int fireTicks = input.readInt();
            float fallDistance = input.readFloat();
            boolean allowFlight = input.readBoolean();
            boolean flying = input.readBoolean();
            float flySpeed = input.readFloat();
            float walkSpeed = input.readFloat();
            int effectCount = input.readInt();
            if (effectCount < 0 || effectCount > 128) throw new IOException("invalid effect count");
            List<Effect> effects = new ArrayList<>(effectCount);
            for (int i = 0; i < effectCount; i++)
                effects.add(
                        new Effect(
                                input.readUTF(),
                                input.readInt(),
                                input.readInt(),
                                input.readBoolean(),
                                input.readBoolean(),
                                input.readBoolean()));
            if (input.read() != -1) throw new IOException("trailing developer state data");
            return new Snapshot(
                    storedUuid,
                    world,
                    x,
                    y,
                    z,
                    yaw,
                    pitch,
                    gameMode,
                    inventory,
                    armor,
                    offHand,
                    cursor,
                    enderChest,
                    level,
                    totalExperience,
                    experience,
                    health,
                    food,
                    saturation,
                    exhaustion,
                    fireTicks,
                    fallDistance,
                    allowFlight,
                    flying,
                    flySpeed,
                    walkSpeed,
                    List.copyOf(effects));
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException(
                    "Cannot load developer state snapshot: " + file, failure);
        }
    }

    void captureIfAbsent(Player player) {
        Objects.requireNonNull(player, "player");
        if (Bukkit.getServer() != null && !Bukkit.isPrimaryThread())
            throw new IllegalStateException(
                    "developer state must be captured on the server thread");
        UUID uuid = player.getUniqueId();
        if (contains(uuid)) return;
        Location location = player.getLocation();
        World world = Objects.requireNonNull(location.getWorld(), "player world");
        List<Effect> effects =
                player.getActivePotionEffects().stream()
                        .map(
                                effect ->
                                        new Effect(
                                                effect.getType().getKey().toString(),
                                                effect.getDuration(),
                                                effect.getAmplifier(),
                                                effect.isAmbient(),
                                                effect.hasParticles(),
                                                effect.hasIcon()))
                        .toList();
        save(
                new Snapshot(
                        uuid,
                        world.getUID(),
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        location.getYaw(),
                        location.getPitch(),
                        player.getGameMode().name(),
                        encode(player.getInventory().getStorageContents()),
                        encode(player.getInventory().getArmorContents()),
                        encode(new ItemStack[] {player.getInventory().getItemInOffHand()}),
                        encode(new ItemStack[] {player.getItemOnCursor()}),
                        encode(player.getEnderChest().getContents()),
                        player.getLevel(),
                        player.getTotalExperience(),
                        player.getExp(),
                        player.getHealth(),
                        player.getFoodLevel(),
                        player.getSaturation(),
                        player.getExhaustion(),
                        player.getFireTicks(),
                        player.getFallDistance(),
                        player.getAllowFlight(),
                        player.isFlying(),
                        player.getFlySpeed(),
                        player.getWalkSpeed(),
                        effects));
    }

    boolean applyIfOnline(UUID uuid) {
        Snapshot snapshot = get(uuid);
        if (snapshot == null) return false;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return false;
        restore(player, snapshot);
        return true;
    }

    void restoreIfOnline(UUID uuid) {
        if (!applyIfOnline(uuid)) return;
        if (directory == null) {
            memory.remove(uuid);
            return;
        }
        try {
            Files.delete(path(uuid));
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Cannot remove restored developer snapshot: " + uuid, failure);
        }
    }

    void restore(Player player, Snapshot snapshot) {
        if (Bukkit.getServer() != null && !Bukkit.isPrimaryThread())
            throw new IllegalStateException(
                    "developer state must be restored on the server thread");
        World world = Bukkit.getWorld(snapshot.world());
        if (world == null)
            throw new IllegalStateException("developer snapshot world is unavailable");
        Location location =
                new Location(
                        world,
                        snapshot.x(),
                        snapshot.y(),
                        snapshot.z(),
                        snapshot.yaw(),
                        snapshot.pitch());
        if (!player.teleport(location))
            throw new IllegalStateException("developer teleport failed");
        player.getInventory().setStorageContents(decode(snapshot.inventory()));
        player.getInventory().setArmorContents(decode(snapshot.armor()));
        ItemStack[] offHand = decode(snapshot.offHand());
        player.getInventory().setItemInOffHand(offHand.length == 0 ? null : offHand[0]);
        ItemStack[] cursor = decode(snapshot.cursor());
        player.setItemOnCursor(cursor.length == 0 ? null : cursor[0]);
        player.getEnderChest().setContents(decode(snapshot.enderChest()));
        player.setGameMode(GameMode.valueOf(snapshot.gameMode()));
        player.setLevel(snapshot.level());
        player.setTotalExperience(snapshot.totalExperience());
        player.setExp(snapshot.experience());
        player.setFoodLevel(snapshot.food());
        player.setSaturation(snapshot.saturation());
        player.setExhaustion(snapshot.exhaustion());
        player.setFireTicks(snapshot.fireTicks());
        player.setFallDistance(snapshot.fallDistance());
        player.setFlySpeed(snapshot.flySpeed());
        player.setWalkSpeed(snapshot.walkSpeed());
        player.setAllowFlight(snapshot.allowFlight());
        if (snapshot.allowFlight()) player.setFlying(snapshot.flying());
        double maxHealth = player.getMaxHealth();
        player.setHealth(Math.min(snapshot.health(), maxHealth));
        for (PotionEffect active : player.getActivePotionEffects())
            player.removePotionEffect(active.getType());
        for (Effect effect : snapshot.effects()) {
            NamespacedKey key = NamespacedKey.fromString(effect.key());
            PotionEffectType type =
                    key == null ? null : org.bukkit.Registry.POTION_EFFECT_TYPE.get(key);
            if (type == null)
                throw new IllegalStateException("unknown potion effect: " + effect.key());
            player.addPotionEffect(
                    new PotionEffect(
                            type,
                            effect.duration(),
                            effect.amplifier(),
                            effect.ambient(),
                            effect.particles(),
                            effect.icon()));
        }
    }

    private synchronized void save(Snapshot snapshot) {
        if (directory == null) {
            memory.put(snapshot.uuid(), snapshot);
            return;
        }
        Path file = path(snapshot.uuid());
        Path pending = null;
        try {
            Files.createDirectories(directory);
            pending = Files.createTempFile(directory, ".developer-state-", ".pending");
            try (var output = new DataOutputStream(Files.newOutputStream(pending))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                writeUuid(output, snapshot.uuid());
                writeUuid(output, snapshot.world());
                output.writeDouble(snapshot.x());
                output.writeDouble(snapshot.y());
                output.writeDouble(snapshot.z());
                output.writeFloat(snapshot.yaw());
                output.writeFloat(snapshot.pitch());
                output.writeUTF(snapshot.gameMode());
                writeItems(output, snapshot.inventory());
                writeItems(output, snapshot.armor());
                writeItems(output, snapshot.offHand());
                writeItems(output, snapshot.cursor());
                writeItems(output, snapshot.enderChest());
                output.writeInt(snapshot.level());
                output.writeInt(snapshot.totalExperience());
                output.writeFloat(snapshot.experience());
                output.writeDouble(snapshot.health());
                output.writeInt(snapshot.food());
                output.writeFloat(snapshot.saturation());
                output.writeFloat(snapshot.exhaustion());
                output.writeInt(snapshot.fireTicks());
                output.writeFloat(snapshot.fallDistance());
                output.writeBoolean(snapshot.allowFlight());
                output.writeBoolean(snapshot.flying());
                output.writeFloat(snapshot.flySpeed());
                output.writeFloat(snapshot.walkSpeed());
                output.writeInt(snapshot.effects().size());
                for (Effect effect : snapshot.effects()) {
                    output.writeUTF(effect.key());
                    output.writeInt(effect.duration());
                    output.writeInt(effect.amplifier());
                    output.writeBoolean(effect.ambient());
                    output.writeBoolean(effect.particles());
                    output.writeBoolean(effect.icon());
                }
            }
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(
                    pending,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Cannot persist developer state snapshot: " + file, failure);
        } finally {
            if (pending != null) {
                try {
                    Files.deleteIfExists(pending);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private Path path(UUID uuid) {
        if (directory == null) throw new IllegalStateException("in-memory state store has no path");
        return directory.resolve(uuid + ".bin");
    }

    private static byte[][] encode(ItemStack[] contents) {
        if (contents.length > MAX_ITEMS)
            throw new IllegalArgumentException("too many inventory slots");
        byte[][] encoded = new byte[contents.length][];
        for (int i = 0; i < contents.length; i++)
            if (contents[i] != null
                    && !contents[i].isEmpty()
                    && hasItemMaterial(contents[i].getType()))
                encoded[i] = contents[i].serializeAsBytes();
        return encoded;
    }

    static boolean hasItemMaterial(Material material) {
        return material != null
                && material != Material.AIR
                && material != Material.CAVE_AIR
                && material != Material.VOID_AIR;
    }

    private static ItemStack[] decode(byte[][] contents) {
        ItemStack[] decoded = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++)
            if (contents[i] != null) decoded[i] = ItemStack.deserializeBytes(contents[i]);
        return decoded;
    }

    private static void writeItems(DataOutputStream output, byte[][] items) throws IOException {
        if (items.length > MAX_ITEMS) throw new IOException("too many inventory slots");
        output.writeInt(items.length);
        for (byte[] item : items) {
            output.writeBoolean(item != null);
            if (item == null) continue;
            if (item.length > MAX_ITEM_BYTES) throw new IOException("inventory item is too large");
            output.writeInt(item.length);
            output.write(item);
        }
    }

    private static byte[][] readItems(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_ITEMS) throw new IOException("invalid inventory size");
        byte[][] items = new byte[count][];
        for (int i = 0; i < count; i++) {
            if (!input.readBoolean()) continue;
            int length = input.readInt();
            if (length < 0 || length > MAX_ITEM_BYTES) throw new IOException("invalid item size");
            items[i] = input.readNBytes(length);
            if (items[i].length != length) throw new IOException("truncated inventory item");
        }
        return items;
    }

    private static void writeUuid(DataOutputStream output, UUID uuid) throws IOException {
        output.writeLong(uuid.getMostSignificantBits());
        output.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }
}

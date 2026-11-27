package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Meteor trajectories and the thirty-tick exposure meter share the same corruption field. Meteors
 * render as a single client-interpolated coral ItemDisplay synced every 3 ticks to bound bandwidth.
 */
final class PurpleCorruption {
    static final int MAX_ACTIVE_METEORS = 6;
    static final double METEOR_INITIAL_SPEED = 0.55;
    static final double METEOR_ACCELERATION = 0.06;
    static final double METEOR_MAX_SPEED = 2.2;
    private static final int METEOR_SYNC_INTERVAL = 3;
    private static final int METEOR_SOUND_INTERVAL = 12;
    private static final int EXPOSURE_LIMIT = 30;
    private static final int PATCH_LIFETIME = 600;

    // The Purple's main:non_solid excludes decorations and uneven surfaces from the spread.
    private static final class ExcludedSurfaces {
        static final Set<Material> MATERIALS = build();

        private static Set<Material> build() {
            Set<Material> set = EnumSet.of(Material.LEVER, Material.LANTERN, Material.SOUL_LANTERN);
            for (var tag :
                    List.of(
                            Tag.ALL_SIGNS,
                            Tag.BANNERS,
                            Tag.BEDS,
                            Tag.BUTTONS,
                            Tag.CAMPFIRES,
                            Tag.CANDLE_CAKES,
                            Tag.CANDLES,
                            Tag.CAVE_VINES,
                            Tag.CORAL_PLANTS,
                            Tag.CROPS,
                            Tag.DOORS,
                            Tag.FENCE_GATES,
                            Tag.FENCES,
                            Tag.FIRE,
                            Tag.FLOWER_POTS,
                            Tag.FLOWERS,
                            Tag.PORTALS,
                            Tag.PRESSURE_PLATES,
                            Tag.RAILS,
                            Tag.REPLACEABLE,
                            Tag.SAPLINGS,
                            Tag.STAIRS,
                            Tag.TRAPDOORS,
                            Tag.WOOL_CARPETS,
                            Tag.WALLS)) {
                set.addAll(tag.getValues());
            }
            return set;
        }
    }

    // Entity.DATA_TICKS_FROZEN on the supported 26.3 protocol.
    private static final int FROZEN_TICKS_INDEX = 7;

    private record Patch(BlockDisplay display, UUID source, int expires) {}

    private static final class Meteor {
        final Location position;
        final Vector direction;
        final int arena;
        final int expires;
        final ItemDisplay display;
        double speed;
        int syncTicks;
        int soundTicks;

        Meteor(Location position, Vector direction, int arena, int expires, ItemDisplay display) {
            this.position = position;
            this.direction = direction;
            this.arena = arena;
            this.expires = expires;
            this.display = display;
            this.speed = METEOR_INITIAL_SPEED;
        }

        void remove() {
            if (display != null && display.isValid()) display.remove();
        }
    }

    private final HotyCodyDuskyTeamArea area;
    private final Map<Block, Patch> patches = new HashMap<>();
    private final Map<UUID, Integer> exposure = new HashMap<>();
    private final List<Meteor> meteors = new ArrayList<>();

    PurpleCorruption(HotyCodyDuskyTeamArea area) {
        this.area = area;
    }

    static double nextMeteorSpeed(double current) {
        return Math.min(METEOR_MAX_SPEED, current + METEOR_ACCELERATION);
    }

    void spread(Location at, int arena, UUID source, int radius, int count, int now) {
        var rng = ThreadLocalRandom.current();
        int width = radius * 2 + 1;
        var cells =
                rng.ints(0, width * width)
                        .distinct()
                        .limit(Math.min(count, width * width))
                        .iterator();
        while (cells.hasNext()) {
            int cell = cells.nextInt();
            int x = at.getBlockX() + cell % width - radius;
            int z = at.getBlockZ() + cell / width - radius;
            Block block = at.getWorld().getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE);
            if (ExcludedSurfaces.MATERIALS.contains(block.getType())) {
                block =
                        at.getWorld()
                                .getHighestBlockAt(
                                        x + rng.nextInt(-2, 3),
                                        z + rng.nextInt(-2, 3),
                                        HeightMap.WORLD_SURFACE);
            }
            if (!Tag.ENTITIES_CAN_TELEPORT_TO.isTagged(block.getType())
                    || ExcludedSurfaces.MATERIALS.contains(block.getType())) continue;
            Location top = block.getLocation().add(0.5, 0, 0.5);
            top.setY(block.getBoundingBox().getMaxY());
            if (!area.insideBasin(arena, top)) continue;
            Patch previous = patches.get(block);
            BlockDisplay display =
                    previous != null && previous.display().isValid()
                            ? previous.display()
                            : spawnCarpet(top);
            patches.put(block, new Patch(display, source, now + PATCH_LIFETIME));
        }
    }

    private static BlockDisplay spawnCarpet(Location top) {
        return top.getWorld()
                .spawn(
                        top,
                        BlockDisplay.class,
                        display -> {
                            display.setPersistent(false);
                            display.setBlock(Material.LIGHT_BLUE_CARPET.createBlockData());
                            display.setViewRange(0.5f);
                            display.setTransformationMatrix(
                                    new Matrix4f().translation(-0.5f, 0, -0.5f).scale(1, 0.1f, 1));
                        });
    }

    private static ItemDisplay spawnMeteorDisplay(Location start) {
        return start.getWorld()
                .spawn(
                        start,
                        ItemDisplay.class,
                        display -> {
                            display.setPersistent(false);
                            display.setItemStack(new ItemStack(Material.DEAD_BRAIN_CORAL_BLOCK));
                            display.setTeleportDuration(METEOR_SYNC_INTERVAL);
                            display.setViewRange(1.2f);
                            display.setTransformationMatrix(new Matrix4f().scale(1.4f));
                        });
    }

    void launch(int arena, List<Vector> occupied, int now) {
        var config = area.getGameConfig();
        if (config.getMeteorSpawns().isEmpty()) return;
        int activeInArena = 0;
        for (Meteor meteor : meteors) if (meteor.arena == arena) activeInArena++;
        if (activeInArena >= MAX_ACTIVE_METEORS) return;
        var rng = ThreadLocalRandom.current();
        if (!rng.nextBoolean()) return;
        List<Vector> targets = occupied.size() >= 3 ? occupied : config.getItemSpawns();
        if (targets.isEmpty()) return;
        Vector origin = config.getCopyGrid().origin(arena);
        Vector start =
                origin.clone()
                        .add(
                                config.getMeteorSpawns()
                                        .get(rng.nextInt(config.getMeteorSpawns().size())));
        Vector direction =
                origin.add(targets.get(rng.nextInt(targets.size()))).subtract(start).normalize();
        World world = Bukkit.getWorld(area.getWorldName());
        if (world == null) return;
        Location startLoc = start.toLocation(world);
        ItemDisplay display = spawnMeteorDisplay(startLoc);
        meteors.add(new Meteor(startLoc, direction, arena, now + 360, display));
    }

    void tick(int now, Map<Integer, List<Player>> groups) {
        var trails = !meteors.isEmpty() && now % 4 == 0 ? new BasinParticleFrame() : null;
        meteors.removeIf(
                m -> {
                    if (now >= m.expires || !groups.containsKey(m.arena)) {
                        m.remove();
                        return true;
                    }
                    Location at = m.position;
                    var world = at.getWorld();
                    m.speed = nextMeteorSpeed(m.speed);
                    var hit =
                            world.rayTraceBlocks(
                                    at, m.direction, m.speed, FluidCollisionMode.NEVER, true);
                    if (hit == null) {
                        at.add(m.direction.clone().multiply(m.speed));
                        if (++m.syncTicks >= METEOR_SYNC_INTERVAL) {
                            m.syncTicks = 0;
                            if (m.display != null && m.display.isValid()) m.display.teleport(at);
                        }
                        if (++m.soundTicks >= METEOR_SOUND_INTERVAL) {
                            m.soundTicks = 0;
                            world.playSound(at, Sound.ENTITY_BREEZE_IDLE_AIR, 0.85f, 1.5f);
                        }
                        if (trails != null) trails.add(at.clone());
                        return false;
                    }
                    m.remove();
                    Location impact = hit.getHitPosition().toLocation(world);
                    world.spawnParticle(Particle.SPLASH, impact, 16, 0.7, 0.45, 0.7);
                    world.spawnParticle(
                            Particle.CAMPFIRE_COSY_SMOKE, impact, 10, 0.4, 0.3, 0.4, 0.04);
                    world.spawnParticle(
                            Particle.DUST, impact, 10, 0.7, 0.45, 0.7, 0, BasinParticles.SPRAY);
                    world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.8f);
                    for (Player p : groups.get(m.arena))
                        if (p.getLocation().distanceSquared(impact) <= 16) area.infect(p, true);
                    spread(impact, m.arena, null, 7, 50, now);
                    return true;
                });
        if (trails != null)
            trails.show(
                    48,
                    3,
                    (viewer, at) -> {
                        viewer.spawnParticle(
                                Particle.CAMPFIRE_COSY_SMOKE, at, 1, 0.08, 0.08, 0.08, 0.01);
                        viewer.spawnParticle(
                                Particle.DUST_COLOR_TRANSITION,
                                at,
                                2,
                                0.12,
                                0.12,
                                0.12,
                                0,
                                BasinParticles.METEOR_TRAIL);
                    });
        patches.values()
                .removeIf(
                        patch -> {
                            if (now < patch.expires()) return !patch.display().isValid();
                            patch.display().remove();
                            return true;
                        });
        // Offset the ground refresh from the once-per-second item effects.
        if (now % 10 == 5 && !patches.isEmpty()) {
            var ground = new BasinParticleFrame();
            for (var patch : patches.values())
                ground.add(patch.display().getLocation().add(0, 0.05, 0));
            ground.show(
                    20,
                    12,
                    (viewer, at) ->
                            viewer.spawnParticle(
                                    Particle.DUST_COLOR_TRANSITION,
                                    at,
                                    3,
                                    0.28,
                                    0.02,
                                    0.28,
                                    0,
                                    BasinParticles.FISH_JUICE));
        }
        if (!area.activeWave()) {
            clearExposure();
            return;
        }
        if (patches.isEmpty() && exposure.isEmpty()) return;
        var absent = new HashSet<>(exposure.keySet());
        for (var group : groups.values())
            for (Player p : group) {
                UUID id = p.getUniqueId();
                absent.remove(id);
                if (area.holdsFish(p)) {
                    forget(id);
                    continue;
                }
                Location feet = p.getLocation();
                Patch patch = patches.get(feet.clone().subtract(0, 0.1, 0).getBlock());
                if (patch == null) patch = patches.get(feet.subtract(0, 1.1, 0).getBlock());
                int level = exposure.getOrDefault(id, 0) + (patch == null ? -1 : 1);
                if (level <= 0) {
                    forget(id);
                    continue;
                }
                if (level >= EXPOSURE_LIMIT && patch != null) {
                    area.infect(p, false);
                    if (area.holdsFish(p) && patch.source() != null) {
                        Player previous = Bukkit.getPlayer(patch.source());
                        if (previous != null
                                && area.arenaOf(previous.getUniqueId()) == area.arenaOf(id))
                            area.shedFish(previous);
                    }
                    forget(id);
                } else {
                    exposure.put(id, level);
                    if (now % 3 == 0 || level == 1 || level == EXPOSURE_LIMIT - 1)
                        showFrost(p, level);
                    if (now % 5 == 0)
                        CoreMessages.sendActionBar(
                                p,
                                MessageConfig.codeText(
                                        "hoty-items.exposure", level * 100 / EXPOSURE_LIMIT));
                }
            }
        absent.forEach(this::forget);
    }

    private static void showFrost(Player player, int level) {
        int ticks =
                Math.max(
                        player.getFreezeTicks(),
                        player.getMaxFreezeTicks() * level / EXPOSURE_LIMIT);
        PacketEvents.getAPI()
                .getPlayerManager()
                .sendPacket(
                        player,
                        new WrapperPlayServerEntityMetadata(
                                player.getEntityId(),
                                List.of(
                                        new EntityData<>(
                                                FROZEN_TICKS_INDEX, EntityDataTypes.INT, ticks))));
    }

    void forget(UUID id) {
        if (exposure.remove(id) == null) return;
        Player player = Bukkit.getPlayer(id);
        if (player != null && player.isOnline()) showFrost(player, 0);
    }

    private void clearExposure() {
        for (UUID id : List.copyOf(exposure.keySet())) forget(id);
    }

    void close() {
        clearExposure();
        patches.values().forEach(patch -> patch.display().remove());
        patches.clear();
        meteors.forEach(Meteor::remove);
        meteors.clear();
    }
}

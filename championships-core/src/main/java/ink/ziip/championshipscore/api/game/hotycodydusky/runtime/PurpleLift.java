package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import io.papermc.paper.entity.TeleportFlag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

/**
 * Deep Plunge's display cabin, chairs, and collision barriers. Re-parents cabin displays onto the
 * base entity and steps on a two-tick interpolated cadence to minimize multiplayer packet volume.
 */
final class PurpleLift {
    static final double MAX_HEIGHT = 57.0;
    static final double STEP_UP = 1.0;
    static final double STEP_DOWN = -2.0;
    static final int STEP_CADENCE = 2;
    static final int TOP_RETURN_GRACE = 40;
    private static final Title.Times RIDER_TITLE_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(500));

    private record Chair(UUID id, Vector offset) {}

    private final Location bottom;
    private UUID baseId;
    private UUID buttonId;
    private Vector buttonOffset;
    private final List<Chair> chairs = new ArrayList<>();
    private final Map<UUID, Vector> detachedParts = new LinkedHashMap<>();
    private final List<Vector> barriers = new ArrayList<>();
    private final Set<Block> occupied = new HashSet<>();
    private final Map<UUID, Integer> dismountGrace = new HashMap<>();
    private double height;
    private int direction;
    private int nextUse;
    private int moveTicks;
    private int arrivedTopTick;

    PurpleLift(Location bottom) {
        this.bottom = bottom;
    }

    static double nextHeight(double current, int direction) {
        return Math.clamp(current + (direction > 0 ? STEP_UP : STEP_DOWN), 0, MAX_HEIGHT);
    }

    private boolean locate() {
        if (baseId != null) return true;
        var entities =
                bottom.getWorld()
                        .getNearbyEntities(
                                bottom.clone().add(0, 31, 0),
                                4,
                                38,
                                4,
                                e -> e.getScoreboardTags().contains("elevator"));
        BlockDisplay base = null;
        for (Entity entity : entities) {
            if (entity instanceof BlockDisplay display
                    && display.getScoreboardTags().contains("elevator.base")) {
                base = display;
                break;
            }
        }
        if (base == null) return false;
        baseId = base.getUniqueId();
        base.setTeleportDuration(STEP_CADENCE);
        Location baseLocation = base.getLocation();
        height = Math.clamp(baseLocation.getY() - bottom.getY(), 0, MAX_HEIGHT);

        for (Entity entity : entities) {
            if (entity.equals(base)) continue;
            var tags = entity.getScoreboardTags();
            if (tags.contains("elevator.barrier")) {
                Vector offset = entity.getLocation().subtract(bottom).toVector();
                barriers.add(offset);
                occupied.add(bottom.clone().add(0, height, 0).add(offset).getBlock());
                continue;
            }
            Vector relative = entity.getLocation().subtract(baseLocation).toVector();
            if (tags.contains("elevator.interaction") && entity instanceof Interaction) {
                buttonId = entity.getUniqueId();
                buttonOffset = relative;
            } else if (tags.contains("elevator.chair") && entity instanceof BlockDisplay chair) {
                chair.setTeleportDuration(STEP_CADENCE);
                chairs.add(new Chair(chair.getUniqueId(), relative));
            } else if (entity instanceof BlockDisplay piece && relative.lengthSquared() <= 0.01) {
                piece.setTeleportDuration(STEP_CADENCE);
                if (!base.getPassengers().contains(piece)) base.addPassenger(piece);
            } else if (!tags.contains("elevator.head")) {
                detachedParts.put(entity.getUniqueId(), relative);
            }
        }
        return true;
    }

    boolean press(Entity target, List<Player> players, int now) {
        if (!target.getScoreboardTags().contains("elevator.interaction")
                || !target.getWorld().equals(bottom.getWorld())
                || !locate()
                || !target.getUniqueId().equals(buttonId)) return false;
        if (direction == 0 && now >= nextUse) {
            direction = height <= 0.5 ? 1 : -1;
            nextUse = now + (direction > 0 ? 150 : 90);
            moveTicks = 0;
            bottom.getWorld()
                    .playSound(
                            target.getLocation(),
                            Sound.UI_BUTTON_CLICK,
                            1,
                            direction > 0 ? 0.7f : 0.4f);
            List<Player> riders = aboard(players);
            seatRiders(riders, now);
            String label =
                    MessageConfig.codeText(
                            direction > 0 ? "hoty-items.lift-up" : "hoty-items.lift-down");
            Title notice =
                    Title.title(Component.empty(), LegacyText.component(label), RIDER_TITLE_TIMES);
            for (Player rider : riders) rider.showTitle(notice);
        }
        return true;
    }

    private List<Player> aboard(List<Player> players) {
        Location level = bottom.clone().add(0, height, 0);
        return players.stream()
                .filter(
                        p ->
                                Math.abs(p.getX() - level.getX()) <= 2.4
                                        && Math.abs(p.getZ() - level.getZ()) <= 2.4
                                        && p.getY() >= level.getY() - 0.5
                                        && p.getY() <= level.getY() + 5)
                .toList();
    }

    private List<Player> nearTop(List<Player> players) {
        Location level = bottom.clone().add(0, height, 0);
        return players.stream()
                .filter(
                        p ->
                                Math.abs(p.getX() - level.getX()) <= 3.5
                                        && Math.abs(p.getZ() - level.getZ()) <= 3.5
                                        && p.getY() >= level.getY() - 1.0
                                        && p.getY() <= level.getY() + 6.0)
                .toList();
    }

    void tick(List<Player> players, int now) {
        if (baseId == null && now % 20 != 0) return;
        if (!locate()) return;
        dismountGrace.values().removeIf(until -> now >= until);
        if (direction == 0) {
            if (height > 0
                    && now >= arrivedTopTick + TOP_RETURN_GRACE
                    && nearTop(players).isEmpty()) {
                direction = -1;
                nextUse = now + 90;
                moveTicks = 0;
            }
            return;
        }
        ejectInvalidPassengers(players);
        if (++moveTicks % STEP_CADENCE != 1) return;
        List<Player> riders = aboard(players);
        seatRiders(riders, now);
        move(nextHeight(height, direction), riders, false);
        if (moveTicks % 8 == 1) {
            bottom.getWorld()
                    .playSound(
                            bottom.clone().add(0, height + 6, 0),
                            Sound.BLOCK_CHAIN_BREAK,
                            0.6f,
                            0.5f);
        }
        if (height == 0 || height == MAX_HEIGHT) {
            direction = 0;
            if (height == MAX_HEIGHT) arrivedTopTick = now;
            ejectAll();
            syncRestingEntities();
        }
    }

    private void seatRiders(List<Player> riders, int now) {
        if (chairs.isEmpty() || riders.isEmpty()) return;
        Location level = bottom.clone().add(0, height, 0);
        for (Player player : riders) {
            if (player.isInsideVehicle()
                    || now < dismountGrace.getOrDefault(player.getUniqueId(), 0)) continue;
            Chair best = null;
            Entity bestEntity = null;
            double bestDistance = Double.MAX_VALUE;
            Location playerLoc = player.getLocation();
            for (Chair chair : chairs) {
                Entity entity = Bukkit.getEntity(chair.id());
                if (entity == null || !entity.isValid() || !entity.getPassengers().isEmpty())
                    continue;
                Location seatLoc = level.clone().add(chair.offset());
                double dist = seatLoc.distanceSquared(playerLoc);
                if (dist < bestDistance) {
                    bestDistance = dist;
                    best = chair;
                    bestEntity = entity;
                }
            }
            if (best != null && bestEntity != null) {
                Location seatLoc = level.clone().add(best.offset());
                boolean alreadyNearby = bestEntity.getLocation().distanceSquared(seatLoc) <= 9.0;
                if (!alreadyNearby) {
                    bestEntity.teleport(seatLoc);
                } else {
                    bestEntity.addPassenger(player);
                }
            }
        }
    }

    private void ejectInvalidPassengers(List<Player> validPlayers) {
        Set<UUID> validIds = new HashSet<>(validPlayers.size());
        for (Player p : validPlayers) validIds.add(p.getUniqueId());
        for (Chair chair : chairs) {
            Entity entity = Bukkit.getEntity(chair.id());
            if (entity == null || entity.getPassengers().isEmpty()) continue;
            for (Entity passenger : List.copyOf(entity.getPassengers())) {
                if (!(passenger instanceof Player p) || !validIds.contains(p.getUniqueId())) {
                    entity.removePassenger(passenger);
                }
            }
        }
    }

    void dismount(UUID playerId, int now) {
        dismountGrace.put(playerId, now + 12);
        for (Chair chair : chairs) {
            Entity entity = Bukkit.getEntity(chair.id());
            if (entity == null || entity.getPassengers().isEmpty()) continue;
            for (Entity passenger : List.copyOf(entity.getPassengers())) {
                if (passenger.getUniqueId().equals(playerId)) entity.removePassenger(passenger);
            }
        }
    }

    private void ejectAll() {
        for (Chair chair : chairs) {
            Entity entity = Bukkit.getEntity(chair.id());
            if (entity == null || entity.getPassengers().isEmpty()) continue;
            for (Entity passenger : List.copyOf(entity.getPassengers())) {
                entity.removePassenger(passenger);
                if (passenger instanceof Player p) p.setFallDistance(0);
            }
        }
    }

    private void move(double next, List<Player> riders, boolean syncAll) {
        double delta = next - height;
        Location level = bottom.clone().add(0, next, 0);
        Set<Block> nextBlocks = new HashSet<>();
        for (Vector offset : barriers) nextBlocks.add(level.clone().add(offset).getBlock());
        for (Block old : occupied) {
            if (!nextBlocks.contains(old) && old.getType() == Material.BARRIER) {
                int x = old.getX() - bottom.getBlockX(), z = old.getZ() - bottom.getBlockZ();
                boolean chain =
                        (x == 1 && z == 1 || x == -1 && z == -1)
                                && old.getY() >= bottom.getBlockY() + 7
                                && old.getY() > level.getY() + 6;
                old.setType(chain ? Material.IRON_CHAIN : Material.AIR, false);
            }
        }
        for (Block block : nextBlocks) {
            if (block.getType().isAir() || block.getType() == Material.IRON_CHAIN)
                block.setType(Material.BARRIER, false);
        }
        occupied.clear();
        occupied.addAll(nextBlocks);
        height = next;

        Entity base = baseId == null ? null : Bukkit.getEntity(baseId);
        if (base != null) base.teleport(level, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        for (var entry : detachedParts.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity != null) entity.teleport(level.clone().add(entry.getValue()));
        }
        for (Chair chair : chairs) {
            Entity entity = Bukkit.getEntity(chair.id());
            if (entity == null) continue;
            if (!syncAll && entity.getPassengers().isEmpty()) continue;
            entity.teleport(
                    level.clone().add(chair.offset()), TeleportFlag.EntityState.RETAIN_PASSENGERS);
            for (Entity passenger : entity.getPassengers()) {
                if (passenger instanceof Player p) p.setFallDistance(0);
            }
        }
        for (Player p : riders) {
            if (!p.isInsideVehicle()) {
                p.teleport(p.getLocation().add(0, delta, 0));
                p.setFallDistance(0);
            }
        }
        if (syncAll) syncButton(level);
    }

    private void syncRestingEntities() {
        Location level = bottom.clone().add(0, height, 0);
        syncButton(level);
        for (Chair chair : chairs) {
            Entity entity = Bukkit.getEntity(chair.id());
            if (entity != null) entity.teleport(level.clone().add(chair.offset()));
        }
    }

    private void syncButton(Location level) {
        if (buttonId == null || buttonOffset == null) return;
        Entity button = Bukkit.getEntity(buttonId);
        if (button != null) button.teleport(level.clone().add(buttonOffset));
    }

    void close() {
        ejectAll();
        if (baseId != null && height != 0) {
            int highestCable = bottom.getBlockY() + (int) Math.ceil(height) + 6;
            move(0, List.of(), true);
            for (int y = bottom.getBlockY() + 7; y <= highestCable; y++) {
                for (int offset : new int[] {-1, 1}) {
                    Block cable =
                            bottom.getWorld()
                                    .getBlockAt(
                                            bottom.getBlockX() + offset,
                                            y,
                                            bottom.getBlockZ() + offset);
                    if (cable.getType().isAir()) cable.setType(Material.IRON_CHAIN, false);
                }
            }
        } else if (baseId != null) {
            syncRestingEntities();
        }
        baseId = null;
        buttonId = null;
        buttonOffset = null;
        chairs.clear();
        detachedParts.clear();
        barriers.clear();
        occupied.clear();
        dismountGrace.clear();
        direction = 0;
    }
}

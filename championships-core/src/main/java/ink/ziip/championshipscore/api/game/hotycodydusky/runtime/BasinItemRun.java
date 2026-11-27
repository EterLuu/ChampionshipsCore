package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import ink.ziip.championshipscore.api.game.arena.GamePickup;
import ink.ziip.championshipscore.api.game.hotycodydusky.mechanics.BasinLoot;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.text.CoreMessages;
import ink.ziip.championshipscore.util.glow.GlowingEntities;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** One run owns the pickup entities in all of its physical arenas. */
final class BasinItemRun {
    private record Pickup(
            int arena, int point, BasinLoot loot, GamePickup display, Interaction hitbox) {
        void remove() {
            display.remove();
            hitbox.remove();
        }
    }

    private final HotyCodyDuskyTeamArea area;
    private final Map<UUID, Pickup> pickups = new HashMap<>();
    final BasinTools tools;
    private final GamePickup.Hints hints = new GamePickup.Hints();
    private int tick;

    BasinItemRun(HotyCodyDuskyTeamArea area, GlowingEntities glow) {
        this.area = area;
        tools = new BasinTools(area, glow);
    }

    void tickEffects(int now) {
        tick = now;
        tools.tickEffects(now);
        if (now % 2 == 0) pickups.values().forEach(p -> p.display().tick(now));
    }

    void forget(UUID id) {
        hints.clear(id);
        tools.forget(id);
    }

    void tick(Map<Integer, List<UUID>> groups) {
        for (var group : groups.values()) {
            if (group.size() < 2) continue;
            for (UUID id : group) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) hints.refresh(player, tick);
            }
        }
        tools.tick();
        pickups.values()
                .removeIf(
                        p -> {
                            if (groups.getOrDefault(p.arena(), List.of()).size() > 1
                                    && p.hitbox().isValid()
                                    && p.display().isValid()) return false;
                            p.remove();
                            return true;
                        });
        if (tick % (20 * BasinLoot.SPAWN_INTERVAL) != 0) return;
        for (var group : groups.entrySet()) {
            if (group.getValue().size() > 1) trySpawn(group.getKey());
        }
    }

    private void trySpawn(int arena) {
        var random = ThreadLocalRandom.current();
        if (random.nextInt(100) >= BasinLoot.CHANCE_PERCENT) return;
        Set<Integer> occupied = new HashSet<>();
        for (Pickup p : pickups.values()) if (p.arena() == arena) occupied.add(p.point());
        if (occupied.size() >= BasinLoot.LIMIT) return;
        var config = area.getGameConfig();
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < config.getItemSpawns().size(); i++)
            if (!occupied.contains(i)) free.add(i);
        if (free.isEmpty()) return;
        int index = free.get(random.nextInt(free.size()));
        Vector position =
                config.getCopyGrid().origin(arena).clone().add(config.getItemSpawns().get(index));
        World world = Bukkit.getWorld(area.getWorldName());
        Location loc = position.toLocation(world).add(0, 1.25, 0);
        var pool = BasinLoot.pool(config.isExtendedItems());
        BasinLoot loot = pool.get(random.nextInt(pool.size()));
        GamePickup display =
                new GamePickup(loc, loot.stack(), GamePickup.Motion.FLOAT_AND_SPIN, true);
        try {
            Interaction hit =
                    world.spawn(
                            loc.clone().subtract(0, 0.45, 0),
                            Interaction.class,
                            e -> {
                                e.setPersistent(false);
                                e.setInteractionWidth(1);
                                e.setInteractionHeight(1);
                                e.setResponsive(true);
                            });
            pickups.put(hit.getUniqueId(), new Pickup(arena, index, loot, display, hit));
        } catch (RuntimeException failed) {
            display.remove();
            throw failed;
        }
    }

    List<Vector> occupied(int arena) {
        return pickups.values().stream()
                .filter(p -> p.arena() == arena)
                .map(p -> area.getGameConfig().getItemSpawns().get(p.point()))
                .toList();
    }

    boolean claim(Player player, Entity target) {
        Pickup p = pickups.get(target.getUniqueId());
        if (p == null) return false;
        if (!p.display().available()
                || !p.hitbox().isValid()
                || !area.canUseBasinItem(player)
                || area.arenaOf(player.getUniqueId()) != p.arena()
                || !target.getWorld().equals(player.getWorld())
                || player.getEyeLocation().distanceSquared(target.getLocation()) > 36) return true;
        if (!area.giveLoot(player, p.loot())) {
            CoreMessages.sendActionBar(player, MessageConfig.codeText("hoty-items.inventory-full"));
            return true;
        }
        pickups.remove(target.getUniqueId());
        p.remove();
        hints.announce(
                player,
                LegacyText.component(
                        MessageConfig.codeText(
                                "hoty-items.pickup", p.loot().title(), p.loot().description())),
                tick);
        player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.3f);
        return true;
    }

    void close() {
        pickups.values().forEach(Pickup::remove);
        pickups.clear();
        hints.clear();
        tools.close();
    }
}

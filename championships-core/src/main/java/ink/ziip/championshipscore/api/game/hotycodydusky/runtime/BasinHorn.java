package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.function.IntFunction;

/** The Purple's delayed public reveal, scoped to one playing field. */
final class BasinHorn {
    private record Pulse(Set<UUID> blowers, int due) {}

    private final IntFunction<List<LivingEntity>> participants;
    private final Map<Integer, Pulse> pending = new HashMap<>();
    private final Map<UUID, Integer> glowing = new HashMap<>();

    BasinHorn(IntFunction<List<LivingEntity>> participants) {
        this.participants = participants;
    }

    void blow(UUID id, int arena, int now) {
        Pulse pulse = pending.get(arena);
        Set<UUID> blowers = pulse == null ? new HashSet<>() : pulse.blowers();
        if (blowers.add(id)) pending.put(arena, new Pulse(blowers, now + 30));
    }

    void tick(int now) {
        glowing.values().removeIf(end -> end <= now);
        if (pending.isEmpty()) return;
        var groups = new HashMap<Integer, List<LivingEntity>>();
        for (var it = pending.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Pulse pulse = entry.getValue();
            if (now < pulse.due()) continue;
            int arena = entry.getKey();
            var group = groups.computeIfAbsent(arena, participants::apply);
            var centers =
                    group.stream()
                            .filter(e -> pulse.blowers().contains(e.getUniqueId()))
                            .map(LivingEntity::getLocation)
                            .toList();
            for (var target : group) {
                Location at = target.getLocation();
                if (centers.stream().noneMatch(center -> center.distanceSquared(at) <= 200 * 200))
                    continue;
                if (target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0)))
                    glowing.put(target.getUniqueId(), now + 200);
                if (target instanceof Player player) {
                    player.playSound(player, Sound.BLOCK_BEACON_POWER_SELECT, 1, 2);
                }
            }
            it.remove();
        }
    }

    void forget(UUID id) {
        pending.values()
                .removeIf(
                        pulse -> {
                            pulse.blowers().remove(id);
                            return pulse.blowers().isEmpty();
                        });
        if (glowing.remove(id) != null && Bukkit.getEntity(id) instanceof LivingEntity target) {
            var effect = target.getPotionEffect(PotionEffectType.GLOWING);
            if (effect != null
                    && effect.getAmplifier() == 0
                    && !effect.isInfinite()
                    && effect.getDuration() <= 200)
                target.removePotionEffect(PotionEffectType.GLOWING);
        }
    }

    void close() {
        pending.clear();
        for (UUID id : List.copyOf(glowing.keySet())) forget(id);
    }
}

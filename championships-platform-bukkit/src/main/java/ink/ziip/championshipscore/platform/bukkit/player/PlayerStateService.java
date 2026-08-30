package ink.ziip.championshipscore.platform.bukkit.player;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.Objects;

/** Scheduler-neutral atomic player-state operations shared by Core and workers. */
public final class PlayerStateService {
    private PlayerStateService() {
    }

    public static void clearEffects(Player player) {
        Objects.requireNonNull(player, "player");
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
    }

    public static void resetExperience(Player player) {
        Objects.requireNonNull(player, "player");
        player.setExp(0F);
        player.setLevel(0);
        player.setTotalExperience(0);
    }

    /** Restores ordinary game vitals without exceeding a player's current maximum health. */
    public static void resetVitals(Player player) {
        Objects.requireNonNull(player, "player");
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(Math.min(20.0, maxHealth == null ? player.getHealth() : maxHealth.getValue()));
        player.setFoodLevel(20);
        clearHazards(player);
    }

    public static void clearHazards(Player player) {
        Objects.requireNonNull(player, "player");
        player.setFireTicks(0);
        player.setFallDistance(0F);
    }

    public static void disableFlight(Player player) {
        Objects.requireNonNull(player, "player");
        player.setFlying(false);
        player.setAllowFlight(false);
    }
}

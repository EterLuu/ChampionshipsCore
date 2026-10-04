package ink.ziip.championshipscore.platform.bukkit.player;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Stateless common flags; each server's spectator manager owns identity and restoration. */
public final class SpectatorStateService {
    private static final PotionEffect NIGHT_VISION =
            new PotionEffect(
                    PotionEffectType.NIGHT_VISION,
                    PotionEffect.INFINITE_DURATION,
                    0,
                    true,
                    false,
                    false);

    private SpectatorStateService() {}

    public static void apply(Player player, boolean nightVision) {
        if (player.isInsideVehicle()) player.leaveVehicle();
        if (player.getGameMode() != GameMode.SPECTATOR) player.setGameMode(GameMode.SPECTATOR);
        if (player.getSpectatorTarget() != null) player.setSpectatorTarget(null);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
        player.setNoPhysics(true);
        player.setInvulnerable(true);
        player.setAffectsSpawning(false);
        player.setCanPickupItems(false);
        player.setSleepingIgnored(true);
        player.setFallDistance(0F);
        player.setFireTicks(0);
        setNightVision(player, nightVision);
    }

    public static void setNightVision(Player player, boolean enabled) {
        if (enabled) player.addPotionEffect(NIGHT_VISION);
        else player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

    public static void clear(Player player) {
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setCollidable(true);
        player.setNoPhysics(false);
        player.setInvulnerable(false);
        player.setAffectsSpawning(true);
        player.setCanPickupItems(true);
        player.setSleepingIgnored(false);
        player.setFallDistance(0F);
        player.setFireTicks(0);
    }
}

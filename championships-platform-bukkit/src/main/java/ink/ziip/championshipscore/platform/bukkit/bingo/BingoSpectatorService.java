package ink.ziip.championshipscore.platform.bukkit.bingo;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Shared Local/Worker spectator state; game-specific card distribution stays with the caller. */
public final class BingoSpectatorService {
    private static final PotionEffect NIGHT_VISION = new PotionEffect(PotionEffectType.NIGHT_VISION,
            PotionEffect.INFINITE_DURATION, 0, true, false, false);

    private BingoSpectatorService() {
    }

    public static void apply(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
        player.setInvulnerable(true);
        player.setAffectsSpawning(false);
        player.setCanPickupItems(false);
        player.setSleepingIgnored(true);
        player.addPotionEffect(NIGHT_VISION);
    }

    public static void clear(Player player) {
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setCollidable(true);
        player.setInvulnerable(false);
        player.setAffectsSpawning(true);
        player.setCanPickupItems(true);
        player.setSleepingIgnored(false);
    }
}

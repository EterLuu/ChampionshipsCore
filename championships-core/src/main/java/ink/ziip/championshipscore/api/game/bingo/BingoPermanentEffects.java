package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.platform.bukkit.bingo.BingoPermanentEffectService;
import org.bukkit.entity.Player;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Permanent potion effects granted to every bingo participant for the whole round: applied at round
 * start, kept alive for the round's duration, and cleared at round end by the shared
 * {@code resetPlayerHealthFoodEffectLevelInventory()} (the same reset that clears the starter kit -
 * there is no explicit {@code remove}, mirroring {@link BingoStarterKit}).
 *
 * <p>"Permanent" here means self-healing rather than fire-and-forget: vanilla clears potion effects
 * on death, {@link BingoArea#refreshVitals} strips them on reconnect, and a player may temporarily
 * override one with a brewed potion (e.g. a stronger Speed). So besides the round-start hand-out,
 * the per-second tracker calls {@link #ensure} which re-adds each effect only when it is absent -
 * it never clobbers an active temporary buff, and once that buff expires the next tick restores the
 * permanent one.
 *
 * <p>Config entries are {@code "<effect>:<level>"} strings where {@code <level>} is the in-game
 * displayed level (1 = I, 2 = II, …, 8 = VIII); it may be omitted (defaults to I). {@code <effect>}
 * is a vanilla {@link PotionEffectType} key ({@code night_vision}, {@code jump_boost},
 * {@code speed}, {@code haste}, …), resolved through {@link Registry#EFFECT}.
 */
public final class BingoPermanentEffects {
    private BingoPermanentEffects() {
    }

    /**
     * Parses config entries into infinite-duration {@link PotionEffect}s. Each entry is
     * {@code "<effect>:<level>"} with a 1-based displayed level (1 = I, 8 = VIII -> amplifier 7);
     * a missing level defaults to I. Unknown effect names are skipped with a warning rather than
     * crashing the round start.
     */
    public static List<PotionEffect> parse(List<String> entries) {
        return BingoPermanentEffectService.parse(entries, warning -> ChampionshipsCore.getInstance().getLogger()
                .warning(Utils.formatGameLog(GameTypeEnum.Bingo, "-", "加载", "配置", warning)));
    }

    /**
     * Re-adds each permanent effect the player is currently missing. Safe to call every tracker tick:
     * it only fills gaps and never overwrites an active temporary buff of the same type.
     */
    public static void ensure(Player player, List<PotionEffect> effects) {
        BingoPermanentEffectService.ensure(player, effects);
    }
}

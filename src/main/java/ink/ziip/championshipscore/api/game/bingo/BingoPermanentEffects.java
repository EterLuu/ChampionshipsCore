package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
 * <p><b>Elytra interaction:</b> Slow Falling cancels elytra glide momentum, so while a participant
 * is gliding ({@link Player#isGliding()}) every effect in {@link #GLIDE_SUPPRESSED} is dropped and
 * kept off (the tracker's {@code ensure} skips it); when the glide ends the dropped effect is
 * restored. The toggle itself is reacted to immediately via {@code EntityToggleGlideEvent} in
 * {@link BingoHandler} so the effect vanishes the instant gliding starts, not a second later.
 *
 * <p>Config entries are {@code "<effect>:<level>"} strings where {@code <level>} is the in-game
 * displayed level (1 = I, 2 = II, …, 8 = VIII); it may be omitted (defaults to I). {@code <effect>}
 * is a vanilla {@link PotionEffectType} key ({@code night_vision}, {@code jump_boost},
 * {@code slow_falling}, {@code speed}, {@code haste}, …), resolved through {@link Registry#EFFECT}.
 */
public final class BingoPermanentEffects {
    private BingoPermanentEffects() {
    }

    /**
     * Effects that conflict with elytra gliding and are suppressed while a participant is gliding.
     * Slow Falling damps glide fall-speed and so kills elytra flight; add others here if needed.
     */
    private static final Set<PotionEffectType> GLIDE_SUPPRESSED = Set.of(PotionEffectType.SLOW_FALLING);

    /**
     * Parses config entries into infinite-duration {@link PotionEffect}s. Each entry is
     * {@code "<effect>:<level>"} with a 1-based displayed level (1 = I, 8 = VIII -> amplifier 7);
     * a missing level defaults to I. Unknown effect names are skipped with a warning rather than
     * crashing the round start.
     */
    public static List<PotionEffect> parse(List<String> entries) {
        if (entries == null || entries.isEmpty()) return List.of();
        List<PotionEffect> out = new ArrayList<>();
        for (String raw : entries) {
            if (raw == null) continue;
            String s = raw.trim().toLowerCase(Locale.ROOT);
            if (s.isEmpty()) continue;

            int amplifier = 0; // level I -> amplifier 0
            String name = s;
            int colon = s.indexOf(':');
            if (colon >= 0) {
                name = s.substring(0, colon).trim();
                String level = s.substring(colon + 1).trim();
                try {
                    amplifier = Math.max(0, Integer.parseInt(level) - 1);
                } catch (NumberFormatException ignored) {
                    // Leave amplifier at 0; the entry still parses by name.
                }
            }

            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(name));
            if (type == null) {
                Bukkit.getLogger().warning(Utils.formatGameLog(GameTypeEnum.Bingo, "-", "加载", "配置",
                        "未知常驻药水效果=" + raw + "，已跳过"));
                continue;
            }
            out.add(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, false, true, true));
        }
        return List.copyOf(out);
    }

    /**
     * Re-adds each permanent effect the player is currently missing. Effects in
     * {@link #GLIDE_SUPPRESSED} are skipped while the player is gliding (they are managed by
     * {@link #onGlideToggle}). Safe to call every tracker tick: it only fills gaps and never
     * overwrites an active temporary buff of the same type.
     */
    public static void ensure(Player player, List<PotionEffect> effects) {
        if (player == null || effects.isEmpty()) return;
        boolean gliding = player.isGliding();
        for (PotionEffect effect : effects) {
            PotionEffectType type = effect.getType();
            if (gliding && GLIDE_SUPPRESSED.contains(type)) continue;
            if (player.getPotionEffect(type) == null) {
                player.addPotionEffect(effect);
            }
        }
    }

    /**
     * Called on {@code EntityToggleGlideEvent}: when gliding begins, drop every glide-suppressed
     * effect (currently Slow Falling) so elytra flight isn't damped; when gliding ends, restore the
     * configured permanent one if it's still missing. No-op when the configured set contains no
     * glide-suppressed effect.
     */
    public static void onGlideToggle(Player player, List<PotionEffect> effects, boolean gliding) {
        if (player == null || effects.isEmpty()) return;
        if (gliding) {
            for (PotionEffectType type : GLIDE_SUPPRESSED) {
                player.removePotionEffect(type);
            }
            return;
        }
        // Stopped gliding: restore any permanent glide-suppressed effect the player is now missing.
        for (PotionEffect effect : effects) {
            if (!GLIDE_SUPPRESSED.contains(effect.getType())) continue;
            if (player.getPotionEffect(effect.getType()) == null) {
                player.addPotionEffect(effect);
            }
        }
    }
}

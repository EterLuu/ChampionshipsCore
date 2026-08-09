package ink.ziip.championshipscore.platform.bukkit.bingo;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Parser and self-healing applicator shared by local Core and Folia worker Bingo runtimes. */
public final class BingoPermanentEffectService {
    private BingoPermanentEffectService() {
    }

    public static List<PotionEffect> parse(List<String> entries, Consumer<String> warning) {
        if (entries == null || entries.isEmpty()) return List.of();
        Consumer<String> warnings = warning == null ? ignored -> { } : warning;
        List<PotionEffect> result = new ArrayList<>();
        for (String raw : entries) {
            if (raw == null || raw.isBlank()) continue;
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            String[] parts = normalized.split(":", 2);
            int amplifier = 0;
            if (parts.length == 2) {
                try {
                    amplifier = Math.max(0, Integer.parseInt(parts[1].trim()) - 1);
                } catch (NumberFormatException invalid) {
                    warnings.accept("Invalid permanent effect level: " + raw + "; using level I");
                }
            }
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(parts[0].trim()));
            if (type == null) {
                warnings.accept("Unknown permanent effect: " + raw);
                continue;
            }
            result.add(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, false, true, true));
        }
        return List.copyOf(result);
    }

    public static void ensure(Player player, List<PotionEffect> effects) {
        if (player == null || effects == null || effects.isEmpty()) return;
        for (PotionEffect effect : effects) {
            if (player.getPotionEffect(effect.getType()) == null) player.addPotionEffect(effect);
        }
    }
}

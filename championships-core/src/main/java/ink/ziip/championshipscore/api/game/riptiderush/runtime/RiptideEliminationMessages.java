package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import java.util.List;
import java.util.Random;

/** Chooses a configured, category-specific elimination announcement for each player. */
final class RiptideEliminationMessages {
    private RiptideEliminationMessages() {}

    static String choose(String reason, Random random) {
        String configured = configured(reason);
        List<String> variants = lines(configured);
        if (variants.isEmpty()) return MessageConfig.RIPTIDE_RUSH_ELIMINATED;
        return variants.get(random.nextInt(variants.size()));
    }

    private static String configured(String reason) {
        if (reason.equals(MessageConfig.RIPTIDE_RUSH_REASON_FELL)
                || reason.equals(MessageConfig.RIPTIDE_RUSH_REASON_LEFT_BEHIND))
            return MessageConfig.RIPTIDE_RUSH_ELIMINATED_FALL;
        if (reason.equals(MessageConfig.RIPTIDE_RUSH_REASON_WRONG_FLOOR))
            return MessageConfig.RIPTIDE_RUSH_ELIMINATED_FLOOR;
        if (reason.equals(MessageConfig.RIPTIDE_RUSH_REASON_WRONG_ANSWER)
                || reason.equals(MessageConfig.RIPTIDE_RUSH_REASON_MISSED_GATE))
            return MessageConfig.RIPTIDE_RUSH_ELIMINATED_MATH;
        if (reason.equals(MessageConfig.RIPTIDE_RUSH_REASON_DISCONNECTED))
            return MessageConfig.RIPTIDE_RUSH_ELIMINATED_DISCONNECTED;
        return null;
    }

    private static List<String> lines(String configured) {
        if (configured == null || configured.isBlank()) return List.of();
        return configured.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
    }
}

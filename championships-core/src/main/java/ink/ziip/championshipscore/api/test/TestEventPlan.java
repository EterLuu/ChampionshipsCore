package ink.ziip.championshipscore.api.test;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.protocol.BingoVariantRules;

import java.util.List;
import java.util.Objects;

/** Frozen, Core-owned round plan for one unattended Test Event. */
public record TestEventPlan(String profile, List<Round> rounds) {
    public TestEventPlan {
        profile = requireText(profile, "profile");
        Objects.requireNonNull(rounds, "rounds");
        rounds = List.copyOf(rounds);
        if (rounds.isEmpty())
            throw new IllegalArgumentException("test event needs at least one round");
    }

    public record Round(String game, String map, BingoVariantRules variant) {
        public Round(String game, String map) {
            this(game, map, BingoVariantRules.FIXED_POINTS);
        }

        public Round {
            game = requireText(game, "game");
            if (map != null) {
                map = map.trim();
                if (map.isEmpty()) map = null;
            }
            GameTypeEnum type = GameTypeEnum.fromCommand(game);
            if (type == null)
                throw new IllegalArgumentException("unknown test event game: " + game);
            game = type.commandName();
            variant = Objects.requireNonNull(variant, "variant");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}

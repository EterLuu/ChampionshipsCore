package ink.ziip.championshipscore.api.test;

import ink.ziip.championshipscore.protocol.BingoVariantRules;

import java.util.Objects;

/** Immutable target metadata captured before a test session can start a game. */
public record TestSessionPlan(
        String target, String map, String profile, BingoVariantRules variant) {
    public TestSessionPlan(String target, String map, String profile) {
        this(target, map, profile, BingoVariantRules.FIXED_POINTS);
    }

    public TestSessionPlan {
        target = requireText(target, "target");
        if (map != null) {
            map = map.trim();
            if (map.isEmpty()) map = null;
        }
        profile = requireText(profile, "profile");
        variant = Objects.requireNonNull(variant, "variant");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}

package ink.ziip.championshipscore.protocol;

import java.util.List;

/** Gameplay inputs owned and frozen by Core rather than duplicated in worker configuration. */
public record BingoRuntimeRules(
        int preparationSeconds,
        int finalCountdownSeconds,
        int scatterRadius,
        int scatterMaxTries,
        int pvpGraceSeconds,
        List<String> permanentEffects,
        boolean showIntroduction,
        int introductionSeconds,
        List<List<String>> introductionRules,
        BingoIntroductionMode introductionMode,
        BingoLocationSnapshot introductionSpawn,
        BingoLocationSnapshot spectatorSpawn,
        BingoPresentation presentation
) {
    public BingoRuntimeRules(int countdownSeconds, int scatterRadius, int scatterMaxTries,
                             int pvpGraceSeconds, List<String> permanentEffects) {
        this(countdownSeconds, 5, scatterRadius, scatterMaxTries, pvpGraceSeconds, permanentEffects,
                false, 45, List.of(), BingoIntroductionMode.ADVENTURE, null, null,
                new BingoPresentation(java.util.Map.of()));
    }

    public BingoRuntimeRules(int countdownSeconds, int scatterRadius, int scatterMaxTries,
                             int pvpGraceSeconds, List<String> permanentEffects,
                             boolean showIntroduction, int introductionSeconds,
                             List<List<String>> introductionRules, BingoPresentation presentation) {
        this(countdownSeconds, 5, scatterRadius, scatterMaxTries, pvpGraceSeconds, permanentEffects,
                showIntroduction, introductionSeconds, introductionRules,
                BingoIntroductionMode.ADVENTURE, null, null, presentation);
    }

    public BingoRuntimeRules {
        if (preparationSeconds < 1) throw new IllegalArgumentException("preparationSeconds must be positive");
        if (finalCountdownSeconds < 0) throw new IllegalArgumentException("finalCountdownSeconds must not be negative");
        if (scatterRadius < 1) throw new IllegalArgumentException("scatterRadius must be positive");
        if (scatterMaxTries < 1) throw new IllegalArgumentException("scatterMaxTries must be positive");
        if (pvpGraceSeconds < 0) throw new IllegalArgumentException("pvpGraceSeconds must not be negative");
        if (introductionSeconds < 1) throw new IllegalArgumentException("introductionSeconds must be positive");
        permanentEffects = ProtocolSupport.immutableList(permanentEffects, "permanentEffects");
        if (permanentEffects.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("permanentEffects must not contain blank entries");
        }
        introductionRules = ProtocolSupport.immutableNestedList(introductionRules, "introductionRules");
        ProtocolSupport.required(introductionMode, "introductionMode");
        ProtocolSupport.required(presentation, "presentation");
    }
}

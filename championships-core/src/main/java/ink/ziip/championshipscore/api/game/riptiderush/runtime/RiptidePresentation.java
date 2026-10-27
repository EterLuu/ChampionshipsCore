package ink.ziip.championshipscore.api.game.riptiderush.runtime;

import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideColorFloorRun;
import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideDodgeRun;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.Locale;

/** One rendering policy for the real game and its interactive lesson. */
final class RiptidePresentation {
    record Floor(Title title, Component actionbar) {}

    private RiptidePresentation() {}

    static Floor floor(RiptideColorFloorRun run) {
        Component title =
                LegacyText.component(
                        run.preparing()
                                ? MessageConfig.RIPTIDE_RUSH_FLOOR_INTRO_TITLE
                                : MessageConfig.RIPTIDE_RUSH_FLOOR_TITLE.replace(
                                        "%seconds%", seconds(run.remainingTicks())));
        Component actionbar =
                LegacyText.component(
                                MessageConfig.RIPTIDE_RUSH_FLOOR_ACTIONBAR
                                        .replace("%round%", String.valueOf(run.roundNumber()))
                                        .replace("%rounds%", String.valueOf(run.roundCount())))
                        .replaceText(
                                builder ->
                                        builder.matchLiteral("%block%")
                                                .replacement(
                                                        Component.translatable(
                                                                run.target().translationKey())));
        return new Floor(
                Title.title(
                        title,
                        Component.empty(),
                        Title.Times.times(
                                Duration.ZERO,
                                Duration.ofMillis(run.preparing() ? 1500 : 350),
                                Duration.ZERO)),
                actionbar);
    }

    static Component floorActionbar(
            Component target, boolean accelerationBreak, boolean shieldBreak) {
        if (!accelerationBreak && !shieldBreak) return target;
        return LegacyText.component(
                        (accelerationBreak
                                        ? MessageConfig.RIPTIDE_RUSH_SPEED_SHIELD_BREAK
                                        : MessageConfig.RIPTIDE_RUSH_SHIELD_BROKEN)
                                + MessageConfig.codeText("riptide-presentation.floor-separator"))
                .append(target);
    }

    static String dodgeTitle(RiptideDodgeRun run) {
        return MessageConfig.RIPTIDE_RUSH_DODGE_TITLE.replace(
                "%direction%", run.direction().arrivalSide());
    }

    static String dodgeActionbar(int remainingTicks) {
        return MessageConfig.RIPTIDE_RUSH_DODGE_ACTIONBAR.replace(
                "%seconds%", seconds(remainingTicks));
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20D);
    }
}

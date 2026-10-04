package ink.ziip.championshipscore.api.game.riptiderush.mechanics;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

/** Minecraft titles have two physical lines; do not rely on embedded newline support. */
public record RiptideQuestionDisplay(String title, String subtitle) {
    public static RiptideQuestionDisplay of(RiptideQuestion question, int level) {
        String title =
                MessageConfig.RIPTIDE_RUSH_QUESTION_TITLE
                        .replace("%question%", question.clue())
                        .replace("%level%", Integer.toString(level));
        String options =
                MessageConfig.RIPTIDE_RUSH_QUESTION_SUBTITLE
                        .replace("%left%", Integer.toString(question.leftAnswer()))
                        .replace("%right%", Integer.toString(question.rightAnswer()));
        return new RiptideQuestionDisplay(
                LegacyText.translateColorCodes(title),
                LegacyText.translateColorCodes(
                        (question.prompt().isEmpty() ? "" : question.prompt() + "  ") + options));
    }
}

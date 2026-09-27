package ink.ziip.championshipscore.api.game.riptiderush;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;

/** Minecraft titles have two physical lines; do not rely on embedded newline support. */
record RiptideQuestionDisplay(String title, String subtitle) {
    static RiptideQuestionDisplay of(RiptideQuestion question, int level) {
        String title = MessageConfig.RIPTIDE_RUSH_QUESTION_TITLE.replace("%question%", question.clue())
                .replace("%level%", Integer.toString(level));
        String options = MessageConfig.RIPTIDE_RUSH_QUESTION_SUBTITLE
                .replace("%left%", Integer.toString(question.leftAnswer()))
                .replace("%right%", Integer.toString(question.rightAnswer()));
        return new RiptideQuestionDisplay(Utils.translateColorCodes(title),
                Utils.translateColorCodes((question.prompt().isEmpty() ? "" : question.prompt() + "  ") + options));
    }
}

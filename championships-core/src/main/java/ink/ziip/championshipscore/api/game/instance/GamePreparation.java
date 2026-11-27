package ink.ziip.championshipscore.api.game.instance;

import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.configuration.config.message.GameMessages;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;
import ink.ziip.championshipscore.shared.presentation.MessageTemplate;
import ink.ziip.championshipscore.shared.presentation.RuleIntroductionTimeline;

import org.bukkit.Location;
import org.bukkit.Note;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Owns introduction/countdown requests; a retired tick can only cancel its own task. */
final class GamePreparation {
    private static final Note LOW_NOTE = Note.natural(0, Note.Tone.C);
    private static final Note HIGH_NOTE = Note.natural(1, Note.Tone.C);
    private final BaseGameInstance instance;
    private boolean introductionEnabled;
    private volatile Run introduction;
    private Run countdown;
    private long revision;
    private int countdownDuration;

    GamePreparation(BaseGameInstance instance) {
        this.instance = instance;
    }

    boolean isIntroductionActive() {
        return introduction != null;
    }

    void setIntroductionEnabled(boolean enabled) {
        introductionEnabled = enabled;
    }

    void clearCountdownDuration() {
        countdownDuration = 0;
    }

    void showCountdown(int seconds) {
        int remaining = Math.max(0, seconds);
        if (countdownDuration <= 0 || remaining > countdownDuration)
            countdownDuration = Math.max(1, remaining);
        instance.updateGameTimerBossBar(
                MessageTemplate.render(
                        GameMessages.GAME_PREPARATION_COUNT_DOWN,
                        Map.of(
                                "%game%",
                                instance.getGameTypeEnum().toString(),
                                "%time%",
                                CoreMessages.bossBarTime(remaining))),
                remaining,
                countdownDuration);
    }

    void startIntroduction(Runnable onComplete) {
        boolean enabled = instance.isEventRun() && introductionEnabled;
        long cancellation = cancelIntroduction();
        if (revision != cancellation) return;
        cancelCountdown();
        if (!available() || instance.getGameStageEnum() != GameStageEnum.PREPARATION) return;
        long request = revision;
        long generation = instance.captureGameGeneration();
        Run run = new Run(generation, request, instance.getIntroductionDurationSeconds());
        if (!enabled) {
            completeSkippedIntroduction(run, onComplete);
            return;
        }
        List<List<String>> configured = instance.getIntroductionRules();
        boolean interactive = instance.isInteractiveIntroduction();
        Location spawn = instance.resolveIntroductionSpawnPoint();
        if ((!interactive && (configured == null || configured.isEmpty())) || spawn == null) {
            completeSkippedIntroduction(run, onComplete);
            return;
        }
        List<List<String>> rules =
                interactive ? List.of() : configured.stream().map(List::copyOf).toList();
        introduction = run;
        try {
            instance.onIntroductionStarted();
            if (!currentIntroduction(run)) return;
            instance.applyIntroductionGameModeToAllParticipants();
            if (!currentIntroduction(run)) return;
            instance.teleportAllPlayers(spawn);
            if (!currentIntroduction(run)) return;
            if (!interactive)
                instance.sendTimedTitleToAllGamePlayers(
                        MessageTemplate.render(
                                GameMessages.GAME_INTRODUCTION_TITLE,
                                Map.of("%game%", instance.getGameTypeEnum().toString())),
                        "",
                        100);
            schedule(run, () -> tickIntroduction(run, rules, interactive, onComplete));
        } catch (Throwable error) {
            fail(run, error);
        }
    }

    private void completeSkippedIntroduction(Run run, Runnable onComplete) {
        if (!belongsToCurrentRun(run) || instance.getGameStageEnum() != GameStageEnum.PREPARATION)
            return;
        try {
            onComplete.run();
        } catch (Throwable error) {
            fail(run, error);
        }
    }

    private void tickIntroduction(
            Run run, List<List<String>> rules, boolean interactive, Runnable onComplete) {
        if (!currentIntroduction(run)) return;
        try {
            int section =
                    interactive
                            ? -1
                            : RuleIntroductionTimeline.sectionAt(
                                    run.duration - run.remaining, run.duration, rules.size());
            if (section >= 0) instance.broadcastRuleSection(rules.get(section));
            if (!currentIntroduction(run)) return;
            instance.showPreparationCountdown(run.remaining);
            if (!currentIntroduction(run)) return;
            if (run.remaining-- > 0) return;
            introduction = null;
            run.stop();
            instance.onIntroductionStopped();
            if (!belongsToCurrentRun(run)
                    || instance.getGameStageEnum() != GameStageEnum.PREPARATION) return;
            instance.clearGameTimerBossBar();
            onComplete.run();
        } catch (Throwable error) {
            fail(run, error);
        }
    }

    void startCountdown(
            int seconds,
            String gameTitle,
            String startTitle,
            String startSubtitle,
            boolean sound,
            boolean transitionToProgress,
            Runnable onStart) {
        long cancellation = cancelIntroduction();
        if (revision != cancellation) return;
        cancelCountdown();
        if (!available()) return;
        GameStageEnum stage = instance.getGameStageEnum();
        if (stage == GameStageEnum.WAITING
                || stage == GameStageEnum.END
                || stage == GameStageEnum.LOADING) return;
        Run run = new Run(instance.captureGameGeneration(), revision, Math.max(0, seconds));
        countdown = run;
        try {
            instance.clearGameTimerBossBar();
            instance.changeLevelForAllGamePlayers(0);
            instance.setGameStageEnum(GameStageEnum.COUNTDOWN);
            if (!currentCountdown(run)) return;
            instance.countdownBlockDisappearance.start(run.remaining);
            instance.logGame(
                    Level.INFO,
                    MessageConfig.codeText("base-game-instance.reset-game-1"),
                    MessageConfig.codeText(
                            "base-game-instance.start-final-countdown-1", run.remaining));
            schedule(
                    run,
                    () ->
                            tickCountdown(
                                    run,
                                    gameTitle,
                                    startTitle,
                                    startSubtitle,
                                    sound,
                                    transitionToProgress,
                                    onStart));
        } catch (Throwable error) {
            fail(run, error);
        }
    }

    private void tickCountdown(
            Run run,
            String gameTitle,
            String startTitle,
            String startSubtitle,
            boolean sound,
            boolean transitionToProgress,
            Runnable onStart) {
        if (!currentCountdown(run)) return;
        try {
            if (run.remaining > 0) {
                instance.sendTitleToAllGamePlayers(
                        instance.getFinalCountdownTitle(run.remaining),
                        instance.getFinalCountdownSubtitle(gameTitle, run.remaining));
                if (!currentCountdown(run)) return;
                if (sound) instance.playCountdownBit(LOW_NOTE);
                run.remaining--;
                return;
            }
            countdown = null;
            run.stop();
            if (transitionToProgress) instance.setGameStageEnum(GameStageEnum.PROGRESS);
            if (!belongsToCurrentRun(run)) return;
            onStart.run();
            if (transitionToProgress
                    && belongsToCurrentRun(run)
                    && instance.getGameStageEnum() == GameStageEnum.PROGRESS) {
                instance.announceGameStart(startTitle, startSubtitle);
                if (sound && belongsToCurrentRun(run)) instance.playCountdownBit(HIGH_NOTE);
            }
        } catch (Throwable error) {
            fail(run, error);
        }
    }

    long cancelIntroduction() {
        long cancellation = ++revision;
        introductionEnabled = false;
        Run previous = introduction;
        introduction = null;
        if (previous != null) {
            previous.stop();
            instance.onIntroductionStopped();
        }
        return cancellation;
    }

    void cancelCountdown() {
        revision++;
        Run previous = countdown;
        countdown = null;
        if (previous != null) {
            previous.stop();
            instance.countdownBlockDisappearance.cancel();
        }
    }

    private boolean currentIntroduction(Run run) {
        if (introduction == run
                && belongsToCurrentRun(run)
                && instance.getGameStageEnum() == GameStageEnum.PREPARATION) return true;
        run.stop();
        if (introduction == run) cancelIntroduction();
        return false;
    }

    private boolean currentCountdown(Run run) {
        if (countdown == run
                && belongsToCurrentRun(run)
                && instance.getGameStageEnum() == GameStageEnum.COUNTDOWN) return true;
        run.stop();
        if (countdown == run) cancelCountdown();
        return false;
    }

    private boolean belongsToCurrentRun(Run run) {
        return available()
                && run.revision == revision
                && run.generation == instance.captureGameGeneration();
    }

    private boolean available() {
        return !instance.isDisposed() && instance.plugin.isLoaded() && instance.plugin.isEnabled();
    }

    private void schedule(Run run, Runnable tick) {
        BukkitTask task = instance.scheduler.runTaskTimer(instance.plugin, tick, 0L, 20L);
        run.task = task;
        if (run.stopped) task.cancel();
    }

    private void fail(Run run, Throwable error) {
        run.stop();
        if (!belongsToCurrentRun(run)) return;
        instance.logGame(
                Level.SEVERE,
                MessageConfig.codeText("base-game-instance.reset-game-1"),
                MessageConfig.codeText(
                        "base-game-instance.start-final-countdown-2", error.getMessage()));
        long cancellation = cancelIntroduction();
        if (revision != cancellation) return;
        cancelCountdown();
        instance.abortAndReset();
    }

    private static final class Run {
        final long generation;
        final long revision;
        final int duration;
        int remaining;
        BukkitTask task;
        boolean stopped;

        Run(long generation, long revision, int remaining) {
            this.generation = generation;
            this.revision = revision;
            this.duration = remaining;
            this.remaining = remaining;
        }

        void stop() {
            stopped = true;
            if (task != null) task.cancel();
        }
    }
}

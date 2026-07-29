package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One player's active prepare-mode session: the game/area being edited, the flow's step list, and the
 * session-only flags (world-confirmed, stamped). The saved inventory snapshot is held by
 * {@link PrepareSessionManager}, not here, so a crash can restore the inventory even though the session
 * itself does not survive a restart.
 */
public class PrepareSession {
    private final ChampionshipsCore plugin;
    private final GameTypeEnum gameType;
    private final String areaName;
    private final BaseArea area;
    private final PrepareFlowDefinition flow;
    private final List<PrepareStep> steps;
    private boolean worldConfirmed;
    private boolean stamped;

    public PrepareSession(@NotNull ChampionshipsCore plugin, @NotNull GameTypeEnum gameType,
                          @NotNull String areaName, @NotNull BaseArea area, @NotNull PrepareFlowDefinition flow) {
        this.plugin = plugin;
        this.gameType = gameType;
        this.areaName = areaName;
        this.area = area;
        this.flow = flow;
        this.steps = flow.buildSteps(area);
    }

    public ChampionshipsCore getPlugin() {
        return plugin;
    }

    public GameTypeEnum getGameType() {
        return gameType;
    }

    public String getAreaName() {
        return areaName;
    }

    public BaseArea getArea() {
        return area;
    }

    public PrepareFlowDefinition getFlow() {
        return flow;
    }

    public List<PrepareStep> getSteps() {
        return steps;
    }

    public @Nullable PrepareStep step(@NotNull String key) {
        for (PrepareStep step : steps) {
            if (step.key().equals(key)) return step;
        }
        return null;
    }

    public boolean isWorldConfirmed() {
        return worldConfirmed;
    }

    public void setWorldConfirmed(boolean worldConfirmed) {
        this.worldConfirmed = worldConfirmed;
    }

    public boolean isStamped() {
        return stamped;
    }

    public void setStamped(boolean stamped) {
        this.stamped = stamped;
    }

    /** Total step count (including session-only steps like confirm-world). */
    public int totalSteps() {
        return steps.size();
    }

    /** Steps satisfied right now (including session-only state). */
    public int doneCount() {
        int n = 0;
        for (PrepareStep step : steps) {
            if (step.isSet(this)) n++;
        }
        return n;
    }

    /** Steps excluding the session-only {@link StepCaptureType#CONFIRM_WORLD} (for area-list preview). */
    public int configTotal() {
        int n = 0;
        for (PrepareStep step : steps) {
            if (step.captureType() != StepCaptureType.CONFIRM_WORLD) n++;
        }
        return n;
    }

    /** Satisfied config steps (excluding confirm-world). */
    public int configDone() {
        int n = 0;
        for (PrepareStep step : steps) {
            if (step.captureType() != StepCaptureType.CONFIRM_WORLD && step.isSet(this)) n++;
        }
        return n;
    }
}

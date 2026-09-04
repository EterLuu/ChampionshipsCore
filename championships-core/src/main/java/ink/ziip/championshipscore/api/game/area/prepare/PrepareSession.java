package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ToggleStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.CountdownBlockDisappearanceStep;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
    private final SetupTarget target;
    private final PrepareFlowDefinition flow;
    private final List<PrepareStep> steps;
    private boolean worldConfirmed;
    private boolean stamped;

    public PrepareSession(@NotNull ChampionshipsCore plugin, @NotNull GameTypeEnum gameType,
                          @NotNull String areaName, @NotNull SetupTarget target, @NotNull PrepareFlowDefinition flow) {
        this.plugin = plugin;
        this.gameType = gameType;
        this.areaName = areaName;
        this.target = target;
        this.flow = flow;
        this.steps = new ArrayList<>(flow.buildSteps(target));
        addCommonIntroductionSpawnStep();
        this.stamped = target.config().isPrepareWorldBuilt();
    }

    /** Every game shares the optional dedicated rule-introduction spawn from BaseGameConfig. */
    private void addCommonIntroductionSpawnStep() {
        if (steps.stream().anyMatch(step -> step.key().equals("introduction_spawn"))) return;

        PrepareStep introductionSpawn = new StandAndRunStep(
                "introduction_spawn",
                Component.text(GuiConfig.text("map-editor.menus.step-list.items.introduction-spawn.title")),
                Component.text(GuiConfig.line("map-editor.menus.step-list.items.introduction-spawn.lore", 0)),
                Material.BOOK,
                setup -> setup.config().getIntroductionSpawnPoint() != null
                        || setup.config().getSpectatorSpawnPoint() != null,
                (setup, location) -> setup.config().setIntroductionSpawnPoint(location),
                Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_STEP_INTRODUCTION_SPAWN_SET));

        int spectatorSpawn = -1;
        int confirmWorld = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).key().equals("spectator_spawn")) spectatorSpawn = i;
            if (steps.get(i).captureType() == StepCaptureType.CONFIRM_WORLD) confirmWorld = i;
        }
        int insertionPoint = spectatorSpawn >= 0 ? spectatorSpawn + 1 : confirmWorld + 1;
        steps.add(Math.max(0, Math.min(insertionPoint, steps.size())), introductionSpawn);

        PrepareStep introductionMode = new ToggleStep(
                "introduction_game_mode",
                Component.text(GuiConfig.text("map-editor.menus.step-list.items.introduction-mode.title")),
                Component.text(GuiConfig.line("map-editor.menus.step-list.items.introduction-mode.lore", 0)),
                Material.RECOVERY_COMPASS,
                setup -> setup.config().getIntroductionGameMode() == GameMode.SPECTATOR
                        ? GuiConfig.text("map-editor.menus.step-list.items.introduction-mode.states.spectator.title") : GuiConfig.text("map-editor.menus.step-list.items.introduction-mode.states.adventure.title"),
                setup -> setup.config().setIntroductionGameMode(
                        setup.config().getIntroductionGameMode() == GameMode.SPECTATOR
                                ? GameMode.ADVENTURE : GameMode.SPECTATOR));
        steps.add(Math.max(0, Math.min(insertionPoint + 1, steps.size())), introductionMode);

        // The opening countdown removal is a TGTTOS-specific mechanic. Keeping it out of the shared
        // flow prevents unrelated games from exposing or persisting a misleading optional step.
        if (gameType == GameTypeEnum.TGTTOS) {
            steps.add(Math.max(0, Math.min(insertionPoint + 2, steps.size())),
                    new CountdownBlockDisappearanceStep());
        }
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

    public SetupTarget getTarget() {
        return target;
    }

    public PrepareFlowDefinition getFlow() {
        return flow;
    }

    public List<PrepareStep> getSteps() {
        return steps;
    }

    /** True when at least one actual step needs a WorldEdit selection. */
    public boolean requiresWorldEdit() {
        return steps.stream().anyMatch(PrepareStep::requiresWorldEdit);
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

    public void markDirty() {
        target.config().markPrepareDirty();
    }
}

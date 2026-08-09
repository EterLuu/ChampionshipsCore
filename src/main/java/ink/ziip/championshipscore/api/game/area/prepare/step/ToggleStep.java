package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Function;

/** A two-state prepare option which cycles immediately when clicked. */
public final class ToggleStep extends PrepareStep {
    private final Function<SetupTarget, String> state;
    private final Consumer<SetupTarget> toggle;

    public ToggleStep(@NotNull String key, @NotNull Component name, @NotNull Component description,
                      @NotNull Material icon, @NotNull Function<SetupTarget, String> state,
                      @NotNull Consumer<SetupTarget> toggle) {
        super(key, name, description, icon, StepCaptureType.TOGGLE);
        this.state = state;
        this.toggle = toggle;
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null;
    }

    @Override
    public String stateText(PrepareSession session) {
        return session == null ? null : state.apply(session.getTarget());
    }

    @Override
    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
        toggle.accept(session.getTarget());
        session.markDirty();
        return null;
    }
}

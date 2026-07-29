package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Captures the player's current location into a config field (the "stand here, then click" pattern used by
 * every {@code *SpawnPoint} / {@code *Spawn} option across the games).
 */
public class StandAndRunStep extends PrepareStep {

    private final Predicate<BaseArea> setPredicate;
    private final BiConsumer<BaseArea, Location> setter;
    private final String doneMessage;

    public StandAndRunStep(@NotNull String key, @NotNull Component name, @NotNull Component description,
                           @NotNull Material icon,
                           @NotNull Predicate<BaseArea> setPredicate,
                           @NotNull BiConsumer<BaseArea, Location> setter,
                           @NotNull String doneMessage) {
        super(key, name, description, icon, StepCaptureType.STAND_AND_RUN);
        this.setPredicate = setPredicate;
        this.setter = setter;
        this.doneMessage = doneMessage;
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null && setPredicate.test(session.getArea());
    }

    @Override
    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
        setter.accept(session.getArea(), player.getLocation());
        session.getArea().getGameConfig().saveOptions();
        return doneMessage;
    }
}

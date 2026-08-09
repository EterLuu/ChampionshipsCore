package ink.ziip.championshipscore.api.game.config.model;

import org.jetbrains.annotations.NotNull;

/** A named, resolved rules profile used by one or more maps of the same game implementation. */
public interface GameVariant {
    @NotNull String id();

    @NotNull GameLifecycleSettings lifecycle();

    @NotNull GamePresentationSettings presentation();
}

package ink.ziip.championshipscore.api.game.config.model;

import org.jetbrains.annotations.NotNull;

/** Resolves a map's variant reference to an immutable runtime rules profile. */
public interface GameVariantRegistry<C, V extends GameVariant> {
    @NotNull V resolve(@NotNull C mapConfig);
}

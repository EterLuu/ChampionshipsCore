package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.jetbrains.annotations.NotNull;

public abstract class BasePlaceholder extends PlaceholderExpansion {
    protected ChampionshipsCore plugin;

    public BasePlaceholder(ChampionshipsCore championshipsCore) {
        this.plugin = championshipsCore;
    }

    public abstract @NotNull String getIdentifier();

    @Override
    public @NotNull String getAuthor() {
        return "Eter Lu";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        // This is required or else PlaceholderAPI will unregister the Expansion on reload
        return true;
    }
}
package ink.ziip.championshipscore.api.game.riptiderush.config;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;

import lombok.Getter;

import org.bukkit.Location;

import java.lang.reflect.Field;

/** A single teaching arena shared by every Riptide map, outside the published race worlds. */
@Getter
public final class RiptideIntroductionConfig extends BaseConfigurationFile {
    private final String fileName = "riptiderush/introduction/arena.yml";
    private final String resourceName = "riptiderush/introduction/arena.yml";

    @ConfigOption(path = "enabled")
    private boolean enabled = true;

    @ConfigOption(path = "spawn")
    private Location spawn;

    public RiptideIntroductionConfig(ChampionshipsCore plugin) {
        super(plugin);
    }

    @Override
    public int getLatestVersion() {
        return 1;
    }

    @Override
    protected Object coerceLocationSection(Object value, Field field) {
        // The teaching world is loaded after its configuration, like a regular map world.
        return coerceLocationSection(value, field, false);
    }
}

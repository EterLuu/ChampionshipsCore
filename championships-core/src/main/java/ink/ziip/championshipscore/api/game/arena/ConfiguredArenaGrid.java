package ink.ziip.championshipscore.api.game.arena;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.util.Vector;

import java.util.List;

/**
 * Explicit copy origins for an existing template layout; generated row layouts use RowArenaGrid.
 */
public final class ConfiguredArenaGrid implements ArenaGrid {
    private final List<Vector> origins;

    public ConfiguredArenaGrid(List<Vector> origins) {
        if (origins.isEmpty())
            throw new IllegalArgumentException(
                    MessageConfig.codeText("configured-arena-grid.constructor-1"));
        this.origins = origins.stream().map(Vector::clone).toList();
    }

    @Override
    public Vector origin(int index) {
        if (index < 0 || index >= origins.size())
            throw new IllegalArgumentException(
                    MessageConfig.codeText("configured-arena-grid.origin-1"));
        return origins.get(index).clone();
    }
}

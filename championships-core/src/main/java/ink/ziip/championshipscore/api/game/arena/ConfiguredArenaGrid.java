package ink.ziip.championshipscore.api.game.arena;

import org.bukkit.util.Vector;

import java.util.List;

/**
 * Explicit copy origins for an existing template layout; generated row layouts use RowArenaGrid.
 */
public final class ConfiguredArenaGrid implements ArenaGrid {
    private final List<Vector> origins;

    public ConfiguredArenaGrid(List<Vector> origins) {
        if (origins.isEmpty()) throw new IllegalArgumentException("副本布局不能为空");
        this.origins = origins.stream().map(Vector::clone).toList();
    }

    @Override
    public Vector origin(int index) {
        if (index < 0 || index >= origins.size()) throw new IllegalArgumentException("副本编号超出布局范围");
        return origins.get(index).clone();
    }
}

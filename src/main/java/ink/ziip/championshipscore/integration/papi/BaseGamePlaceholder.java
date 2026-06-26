package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for per-game placeholders. Provides the shared "resolve area by name,
 * fall back to the requesting player's current area" lookup, and handles the
 * {@code area_status_}/{@code area_timer_} placeholders that every game exposes.
 *
 * @param <T> the concrete area type handled by this expansion
 */
public abstract class BaseGamePlaceholder<T extends BaseArea> extends BasePlaceholder {
    public BaseGamePlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    protected abstract BaseAreaManager<T> getManager();

    /**
     * Resolve an area from the name embedded after {@code prefix} in {@code params},
     * falling back to the requesting player's current area when that name does not
     * match a known area. Returns {@code null} if neither resolves.
     */
    @Nullable
    protected T resolveArea(String params, String prefix, OfflinePlayer offlinePlayer) {
        return resolveAreaByName(params.replace(prefix, ""), offlinePlayer);
    }

    /**
     * Resolve an area from an already-extracted {@code areaName}, falling back to the
     * requesting player's current area. Returns {@code null} if neither resolves.
     */
    @Nullable
    protected T resolveAreaByName(String areaName, OfflinePlayer offlinePlayer) {
        T area = getManager().getArea(areaName);
        if (area == null) {
            area = getManager().getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
        }
        return area;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (params.startsWith("area_status_")) {
            T area = resolveArea(params, "area_status_", offlinePlayer);
            return area == null ? MessageConfig.PLACEHOLDER_NONE : area.getGameStageEnum().toString();
        }
        if (params.startsWith("area_timer_")) {
            T area = resolveArea(params, "area_timer_", offlinePlayer);
            return area == null ? MessageConfig.PLACEHOLDER_NONE : areaTimer(area);
        }
        return onGameRequest(offlinePlayer, params);
    }

    /**
     * Render the {@code area_timer_} placeholder for a resolved area. Defaults to the
     * remaining countdown ({@code timer + 1}); games with bespoke timer semantics may
     * override this.
     */
    protected String areaTimer(T area) {
        return String.valueOf(area.getTimer() + 1);
    }

    /**
     * Handle game-specific placeholders. The shared {@code area_status_} and
     * {@code area_timer_} placeholders are already handled by {@link #onRequest}.
     */
    protected abstract String onGameRequest(OfflinePlayer offlinePlayer, String params);
}

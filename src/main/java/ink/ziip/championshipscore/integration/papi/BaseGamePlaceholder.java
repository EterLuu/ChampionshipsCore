package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
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
public abstract class BaseGamePlaceholder<T extends BaseGameInstance> extends BasePlaceholder {
    private static final String CURRENT_AREA_TOKEN = "[areaName]";

    public BaseGamePlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    protected abstract BaseGameInstanceManager<T> getManager();

    /**
     * Resolve an area from the name embedded after {@code prefix} in {@code params}.
     * The scoreboard token {@code [areaName]} explicitly selects the requesting
     * player's current participant or spectator instance.
     */
    @Nullable
    protected T resolveArea(String params, String prefix, OfflinePlayer offlinePlayer) {
        return resolveAreaByName(params.substring(prefix.length()), offlinePlayer);
    }

    /**
     * Resolve an area from an already-extracted {@code areaName}. A real map name
     * keeps selecting that map, except that a participant in a replicated copy gets
     * their exact runtime instance when its config name matches.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    protected T resolveAreaByName(String areaName, OfflinePlayer offlinePlayer) {
        T namedArea = getManager().getArea(areaName);
        if (offlinePlayer == null)
            return namedArea;

        BaseGameInstance current = plugin.getGameManager().getBasePlayerArea(offlinePlayer.getUniqueId());
        if (current == null)
            current = plugin.getGameManager().getPlayerSpectatorStatus(offlinePlayer.getUniqueId());

        if (current != null && getManager().getRuntimeInstances().contains(current)) {
            String currentMapName = current.getGameConfig().getConfigName();
            if (CURRENT_AREA_TOKEN.equalsIgnoreCase(areaName) || areaName.equalsIgnoreCase(currentMapName))
                return (T) current;
        }

        return CURRENT_AREA_TOKEN.equalsIgnoreCase(areaName) ? null : namedArea;
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
     * exact game timer; games with bespoke timer semantics may
     * override this.
     */
    protected String areaTimer(T area) {
        return String.valueOf(area.getTimer());
    }

    /**
     * Handle game-specific placeholders. The shared {@code area_status_} and
     * {@code area_timer_} placeholders are already handled by {@link #onRequest}.
     */
    protected abstract String onGameRequest(OfflinePlayer offlinePlayer, String params);
}

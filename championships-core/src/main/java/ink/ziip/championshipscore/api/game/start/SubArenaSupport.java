package ink.ziip.championshipscore.api.game.start;

import ink.ziip.championshipscore.api.game.frostbite.runtime.FrostbiteArea;
import ink.ziip.championshipscore.api.game.hotycodydusky.runtime.HotyCodyDuskyTeamArea;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.snowball.runtime.SnowballShowdownTeamArea;
import ink.ziip.championshipscore.api.game.tntrun.runtime.TNTRunTeamArea;

/**
 * Counts the physical arenas owned by one round; replicas are selected by their manager instead.
 */
public final class SubArenaSupport {
    private SubArenaSupport() {}

    public static int count(BaseGameInstance instance) {
        if (instance instanceof TNTRunTeamArea tnt)
            return tnt.getGameConfig().getPlayerSpawnPoints().size();
        if (instance instanceof SnowballShowdownTeamArea snowball) return snowball.getArenaCount();
        if (instance instanceof FrostbiteArea) return 4;
        if (instance instanceof HotyCodyDuskyTeamArea hoty) return hoty.getGameConfig().getCopies();
        return 1;
    }
}

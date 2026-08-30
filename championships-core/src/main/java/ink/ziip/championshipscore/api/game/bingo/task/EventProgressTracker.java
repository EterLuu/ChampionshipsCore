package ink.ziip.championshipscore.api.game.bingo.task;

import ink.ziip.championshipscore.platform.bukkit.bingo.BingoObjectiveProgressTracker;
import org.bukkit.entity.Player;

/** Core-facing player adapter for the shared match-scoped Bingo objective tracker. */
public final class EventProgressTracker extends BingoObjectiveProgressTracker {
    public int recordDistinct(Player player, String bucket, String value) {
        recordDistinct(player.getUniqueId(), bucket, value);
        return distinctCount(player.getUniqueId(), bucket);
    }

    public int distinctCount(Player player, String bucket) {
        return distinctCount(player.getUniqueId(), bucket);
    }

    public int increment(Player player, String bucket) {
        increment(player.getUniqueId(), bucket);
        return count(player.getUniqueId(), bucket);
    }

    public int count(Player player, String bucket) {
        return count(player.getUniqueId(), bucket);
    }

    public long observeElapsed(Player player, String bucket, boolean active) {
        return observeElapsed(player.getUniqueId(), bucket, active);
    }
}

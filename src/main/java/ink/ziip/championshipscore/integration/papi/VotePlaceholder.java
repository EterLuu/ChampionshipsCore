package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.vote.VoteManager;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class VotePlaceholder extends BasePlaceholder {
    public VotePlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "vote";
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        VoteManager voteManager = plugin.getVoteManager();

        /* Non-Player required placeholders */

        if (params.startsWith("can_vote_")) {
            GameTypeEnum gameTypeEnum = null;
            try {
                gameTypeEnum = GameTypeEnum.valueOf(params.replace("can_vote_", ""));
            } catch (Exception ignored) {
                return null;
            }

            if (plugin.getRankManager().getGameOrder(gameTypeEnum) == -1) {
                return "true";
            }
            return "false";
        }
        if (params.startsWith("vote_nums_")) {
            GameTypeEnum gameTypeEnum = null;
            try {
                gameTypeEnum = GameTypeEnum.valueOf(params.replace("vote_nums_", ""));
            } catch (Exception ignored) {
                return null;
            }

            int num = voteManager.getVoteNums(gameTypeEnum);
            if (num == 0)
                num = 1;
            return String.valueOf(num);
        }

        /* Player required placeholders */

        Player player = offlinePlayer.getPlayer();
        if (player == null)
            return MessageConfig.PLACEHOLDER_NONE;

        if (params.startsWith("player_vote")) {
            GameTypeEnum gameTypeEnum = voteManager.getPlayerVote(player);
            if (gameTypeEnum == null)
                return MessageConfig.PLACEHOLDER_NONE;
            return gameTypeEnum.name();
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}

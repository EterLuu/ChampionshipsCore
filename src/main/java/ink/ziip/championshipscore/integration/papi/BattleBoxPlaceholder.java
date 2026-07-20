package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxMatch;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
import ink.ziip.championshipscore.api.object.game.battlebox.BBWeaponKitEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BattleBoxPlaceholder extends BaseGamePlaceholder<BattleBoxArea> {
    public BattleBoxPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "battlebox";
    }

    @Override
    protected BaseAreaManager<BattleBoxArea> getManager() {
        return plugin.getGameManager().getBattleBoxManager();
    }

    @Override
    protected String onGameRequest(OfflinePlayer offlinePlayer, String params) {

        /* Match-relative placeholders (the requesting player's own match within the area) */

        if (params.startsWith("area_team_") || params.startsWith("area_rival_")) {
            boolean rival = params.startsWith("area_rival_");
            BattleBoxArea battleBoxArea = resolveArea(params, rival ? "area_rival_" : "area_team_", offlinePlayer);
            Player matchPlayer = offlinePlayer.getPlayer();
            if (battleBoxArea == null || matchPlayer == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            BattleBoxMatch match = battleBoxArea.matchOf(matchPlayer);
            ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(matchPlayer);
            if (match == null || team == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            ChampionshipTeam result = rival ? match.rivalOf(team) : team;
            if (result == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return result.getName();
        }

        /* Player required placeholders */

        Player player = offlinePlayer.getPlayer();
        if (player == null)
            return MessageConfig.PLACEHOLDER_NONE;

        if (params.startsWith("player_kits_")) {
            BattleBoxArea battleBoxArea = resolveArea(params, "player_kits_", offlinePlayer);
            if (battleBoxArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            BBWeaponKitEnum kits = battleBoxArea.getPlayerCurrentWeaponKit(player);
            if (kits == null)
                return MessageConfig.PLACEHOLDER_NONE;
            return kits.toString();
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}

package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
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

        /* Non-Player required placeholders */

        if (params.startsWith("area_team_")) {
            BattleBoxArea battleBoxArea = resolveArea(params, "area_team_", offlinePlayer);
            if (battleBoxArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            ChampionshipTeam championshipTeam = battleBoxArea.getLeftChampionshipTeam();
            if (championshipTeam == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return championshipTeam.getName();
        }
        if (params.startsWith("area_rival_")) {
            BattleBoxArea battleBoxArea = resolveArea(params, "area_rival_", offlinePlayer);
            if (battleBoxArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            ChampionshipTeam championshipTeam = battleBoxArea.getRightChampionshipTeam();
            if (championshipTeam == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return championshipTeam.getName();
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

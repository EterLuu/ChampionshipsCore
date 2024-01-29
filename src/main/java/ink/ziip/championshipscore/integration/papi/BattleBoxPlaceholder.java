package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxManager;
import ink.ziip.championshipscore.api.object.game.BBWeaponKitEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BattleBoxPlaceholder extends BasePlaceholder {
    public BattleBoxPlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "battlebox";
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        BattleBoxManager battleBoxManager = plugin.getGameManager().getBattleBoxManager();

        /* Non-Player required placeholders */

        if (params.startsWith("area_status_")) {
            BattleBoxArea battleBoxArea = battleBoxManager.getArea(params.replace("area_status_", ""));
            if (battleBoxArea == null) {
                battleBoxArea = battleBoxManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (battleBoxArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return battleBoxArea.getGameStageEnum().toString();
        }
        if (params.startsWith("area_team_")) {
            BattleBoxArea battleBoxArea = battleBoxManager.getArea(params.replace("area_team_", ""));
            if (battleBoxArea == null) {
                battleBoxArea = battleBoxManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (battleBoxArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            ChampionshipTeam championshipTeam = battleBoxArea.getLeftChampionshipTeam();
            if (championshipTeam == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return championshipTeam.getColoredName();
        }
        if (params.startsWith("area_rival_")) {
            BattleBoxArea battleBoxArea = battleBoxManager.getArea(params.replace("area_rival_", ""));
            if (battleBoxArea == null) {
                battleBoxArea = battleBoxManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (battleBoxArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            ChampionshipTeam championshipTeam = battleBoxArea.getRightChampionshipTeam();
            if (championshipTeam == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return championshipTeam.getColoredName();
        }
        if (params.startsWith("area_timer_")) {
            BattleBoxArea battleBoxArea = battleBoxManager.getArea(params.replace("area_timer_", ""));
            if (battleBoxArea == null) {
                battleBoxArea = battleBoxManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
            if (battleBoxArea == null) {
                return MessageConfig.PLACEHOLDER_NONE;
            }
            return String.valueOf(battleBoxArea.getTimer() + 1);
        }

        /* Player required placeholders */

        Player player = offlinePlayer.getPlayer();
        if (player == null)
            return MessageConfig.PLACEHOLDER_NONE;

        if (params.startsWith("player_kits_")) {
            BattleBoxArea battleBoxArea = battleBoxManager.getArea(params.replace("player_kits_", ""));
            if (battleBoxArea == null) {
                battleBoxArea = battleBoxManager.getArea(plugin.getGameManager().getPlayerCurrentAreaName(offlinePlayer.getUniqueId()));
            }
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

package ink.ziip.championshipscore.integration.papi;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.schedule.ScheduleManager;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class SchedulePlaceholder extends BasePlaceholder {
    public SchedulePlaceholder(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "schedule";
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        ScheduleManager scheduleManager = plugin.getScheduleManager();

        /* Non-Player required placeholders */

        if (params.startsWith("round_total")) {
            return String.valueOf(plugin.getRankManager().getRound());
        }
        if (params.startsWith("round_snowball")) {
            return String.valueOf(scheduleManager.getSnowballScheduleManager().getSubRound());
        }
        if (params.startsWith("round_skywars")) {
            return String.valueOf(scheduleManager.getSkyWarsScheduleManager().getSubRound());
        }
        if (params.startsWith("round_tntrun")) {
            return String.valueOf(scheduleManager.getTntRunScheduleManager().getSubRound());
        }
        if (params.startsWith("round_tgttos")) {
            return String.valueOf(scheduleManager.getTgttosScheduleManager().getSubRound());
        }
        if (params.startsWith("round_battlebox")) {
            return String.valueOf(scheduleManager.getBattleBoxScheduleManager().getSubRound());
        }
        if (params.startsWith("round_parkourtag")) {
            return String.valueOf(scheduleManager.getParkourTagScheduleManager().getSubRound());
        }
        if (params.startsWith("round_parkourwarrior")) {
            return String.valueOf(scheduleManager.getParkourWarriorScheduleManager().getSubRound());
        }
        if (params.startsWith("round_hotycodydusky")) {
            return String.valueOf(scheduleManager.getHotyCodyDuskyScheduleManager().getSubRound());
        }
        if (params.startsWith("round_points")) {
            return String.valueOf(plugin.getRankManager().getPointMultiple(plugin.getRankManager().getRound() + 1));
        }

        // Placeholder is unknown by the Expansion
        return null;
    }
}

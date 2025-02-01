package ink.ziip.championshipscore.integration.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import io.github.steaf23.bingoreloaded.event.BingoEndedEvent;
import io.github.steaf23.bingoreloaded.event.BingoStartedEvent;
import io.github.steaf23.bingoreloaded.event.BingoTaskProgressCompletedEvent;
import io.github.steaf23.bingoreloaded.player.BingoParticipant;
import io.github.steaf23.bingoreloaded.tasks.GameTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

public class BingoHandler extends BaseListener {
    private final BingoManager bingoManager;

    protected BingoHandler(ChampionshipsCore plugin, BingoManager bingoManager) {
        super(plugin);
        this.bingoManager = bingoManager;
    }

    @EventHandler
    public void onBingoStart(BingoStartedEvent event) {
        if (!bingoManager.isStarted()) {
            if (event.getSession().isRunning()) {
                bingoManager.setStarted(true);
                Utils.sendMessageToAllPlayers(MessageConfig.BINGO_GAME_START);
                World world = Bukkit.getWorld("bingo");
                if (world != null) {
                    world.setTime(9000);
                }

                for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                    for (Player player : championshipTeam.getOnlinePlayers()) {
                        for (Player member : championshipTeam.getOnlinePlayers()) {
                            if (!player.equals(member)) {
                                try {
                                    plugin.getGlowingEntities().setGlowing(member, player, Utils.toChatColor(championshipTeam.getColorName()));
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBingoEnd(BingoEndedEvent event) {
        if (bingoManager.isStarted()) {
            bingoManager.endGame();
        }
    }

    @EventHandler
    public void onBingoTaskCompleted(BingoTaskProgressCompletedEvent event) {
        if(event.getTask().getCompletedBy().isEmpty())
            return;
        BingoParticipant participant = event.getTask().getCompletedBy().get();
        GameTask gameTask = event.getTask();
        if (bingoManager.isStarted()) {
            if (participant.sessionPlayer().isPresent()) {
                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(participant.sessionPlayer().get());
                if (championshipTeam != null) {
                    bingoManager.handleTeamCompleteTask(gameTask, championshipTeam, participant.sessionPlayer().get());
                }
            }
        }
    }
}

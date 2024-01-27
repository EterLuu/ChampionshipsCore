package ink.ziip.championshipscore.integration.bingo;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import io.github.steaf23.bingoreloaded.event.BingoCardTaskCompleteEvent;
import io.github.steaf23.bingoreloaded.event.BingoEndedEvent;
import io.github.steaf23.bingoreloaded.event.BingoStartedEvent;
import io.github.steaf23.bingoreloaded.tasks.BingoTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
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
    public void onBingoTaskCompleted(BingoCardTaskCompleteEvent event) {
        if (bingoManager.isStarted()) {
            BingoTask bingoTask = event.getTask();
            if (event.getParticipant().sessionPlayer().isPresent()) {
                ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(event.getParticipant().sessionPlayer().get());
                if (championshipTeam != null) {
                    bingoManager.handleTeamCompleteTask(bingoTask, championshipTeam);
                }
            }
        }
    }
}

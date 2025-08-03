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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

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
    public void onBingoTaskCompleted(BingoTaskProgressCompletedEvent event) {
        if (event.getTask().getCompletedByPlayer().isEmpty())
            return;
        BingoParticipant participant = event.getTask().getCompletedByPlayer().get();
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteraction(PlayerInteractEvent event) {
        if (!bingoManager.isStarted())
            return;

        Player player = event.getPlayer();
        if (!player.getWorld().getName().startsWith("bingo")) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.getItem() != null && event.getItem().getType() == Material.COMPASS) {
                ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
                if (team == null) {
                    return;
                }
                BingoTeleportationMenu.openBingoTeleportationMenu(player, team.getMembers().stream().toList());
            }

        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDamagedByPlayer(EntityDamageByEntityEvent event) {
        if (!bingoManager.isStarted())
            return;

        if (!(event.getDamager() instanceof Player damager))
            return;

        if (!damager.getWorld().getName().startsWith("bingo")) {
            return;
        }

        if (!(event.getEntity() instanceof Player player))
            return;

        if (!bingoManager.isAllowPvP())
            event.setCancelled(true);
    }
}

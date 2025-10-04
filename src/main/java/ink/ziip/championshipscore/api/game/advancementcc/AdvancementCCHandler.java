package ink.ziip.championshipscore.api.game.advancementcc;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Objects;

@Setter
public class AdvancementCCHandler extends BaseListener {
    private AdvancementCCArea advancementCCArea;

    protected AdvancementCCHandler(ChampionshipsCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (advancementCCArea.getGameStageEnum() != GameStageEnum.PROGRESS) {
            return;
        }

        Player player = event.getPlayer();
        if (advancementCCArea.notAreaPlayer(player)) {
            return;
        }

        Location location = player.getLocation();
        if (advancementCCArea.notInArea(location)) {
            return;
        }

        advancementCCArea.handlePlayerAdvancementDone(player.getUniqueId(), event.getAdvancement());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityEnterNetherPortal(EntityPortalEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (advancementCCArea.notInArea(player.getLocation())) {
                return;
            }

            if (!advancementCCArea.isAllowTeleport()) {
                event.setCancelled(true);
                return;
            }

            advancementCCArea.addCompletedPlayerCount(player.getName());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerEnterNetherPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (advancementCCArea.notInArea(player.getLocation())) {
            return;
        }
        if (!advancementCCArea.isAllowTeleport()) {
            event.setCancelled(true);
            return;
        }

        advancementCCArea.addCompletedPlayerCount(player.getName());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (advancementCCArea.notAreaPlayer(player)) {
            return;
        }
        if (event.isAnchorSpawn()) {
            return;
        }
        if (event.isBedSpawn()) {
            return;
        }

        World nether = advancementCCArea.getNether();
        if (nether != null && !Objects.equals(event.getRespawnLocation().getWorld(), nether)) {
            event.setRespawnLocation(nether.getSpawnLocation());
        }
    }
}

package ink.ziip.championshipscore.api.game.advancementcc;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseListener;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

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

        advancementCCArea.handlePlayerAdvancementDone(event.getAdvancement());
    }
}

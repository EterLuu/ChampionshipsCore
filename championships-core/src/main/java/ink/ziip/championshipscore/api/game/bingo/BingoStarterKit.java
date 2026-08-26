package ink.ziip.championshipscore.api.game.bingo;

import ink.ziip.championshipscore.api.game.bingo.task.AdvancementTask;
import ink.ziip.championshipscore.api.game.bingo.task.AllOfTask;
import ink.ziip.championshipscore.api.game.bingo.task.ItemTask;
import ink.ziip.championshipscore.api.game.bingo.task.OneOfTask;
import ink.ziip.championshipscore.api.game.bingo.task.TaskData;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.platform.bukkit.bingo.BingoStarterKitService;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.List;

/** Core adapter around the shared local/worker Bingo starter-kit implementation. */
public final class BingoStarterKit {
    private BingoStarterKit() {
    }

    public static void give(Player player, ChampionshipTeam team) {
        give(player, team, ink.ziip.championshipscore.protocol.BingoRemix.NONE);
    }

    public static void give(Player player, ChampionshipTeam team,
                            ink.ziip.championshipscore.protocol.BingoRemix remix) {
        give(player, team, remix, false);
    }

    public static void give(Player player, ChampionshipTeam team,
                            ink.ziip.championshipscore.protocol.BingoRemix remix, boolean daily) {
        if (player != null && team != null) {
            MessageService messages = MessageService.global();
            BingoStarterKitService.give(player, Utils.hex2rgb(team.getColorCode()),
                    messages.component("compass.item_name", team.getName()),
                    List.of(messages.component("compass.item_hint")), remix, daily);
        }
    }

    public static boolean hasKit(Player player) {
        return BingoStarterKitService.hasKit(player);
    }

    public static boolean trivialises(TaskData task) {
        if (task instanceof ItemTask item) {
            return BingoStarterKitService.providedAmount(item.itemType()) >= item.count();
        }
        if (task instanceof OneOfTask set) {
            return set.items().stream().anyMatch(material ->
                    BingoStarterKitService.providedAmount(material) >= set.count());
        }
        if (task instanceof AllOfTask set) {
            // Auto-completable when the kit supplies every member of the complete set.
            return set.items().stream().allMatch(material ->
                    BingoStarterKitService.providedAmount(material) >= set.count());
        }
        if (task instanceof AdvancementTask advancement && advancement.advancement() != null) {
            return conflictingAdvancementKeys().contains(advancement.advancement().getKey().getKey());
        }
        return false;
    }

    public static Set<String> conflictingAdvancementKeys() {
        return BingoStarterKitService.conflictingAdvancementKeys();
    }
}

package ink.ziip.championshipscore.api.game.bingo.gui;

import ink.ziip.championshipscore.api.game.bingo.card.BingoCard;
import ink.ziip.championshipscore.api.game.bingo.config.BingoCardMenuConfig;
import ink.ziip.championshipscore.api.game.bingo.task.CardDisplayInfo;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import ink.ziip.championshipscore.platform.bukkit.gui.BingoCardMenu;
import ink.ziip.championshipscore.platform.bukkit.gui.BingoTaskItems;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import net.kyori.adventure.text.Component;

import org.bukkit.entity.Player;

import java.util.Map;

/** Local task state rendered inside the same configurable frame as the remote worker. */
public final class CardView {
    private CardView() {}

    public static void open(
            Player player,
            BingoCard card,
            CardDisplayInfo displayInfo,
            Component title,
            String viewerTeamId) {
        var messages = MessageService.global();
        var tasks = BingoCardMenuConfig.taskSnapshot();
        BingoTaskItems.validate(tasks);
        BingoCardMenu.open(
                player,
                BingoCardMenuConfig.snapshot(),
                card.size.size,
                Map.of(
                        "%title%",
                        LegacyText.serialize(title),
                        "%card-title%",
                        messages.tr("card.title"),
                        "%win-hint%",
                        messages.tr("card.win_hint")),
                card.getTasks().stream()
                        .map(task -> task.toItem(displayInfo, viewerTeamId, tasks))
                        .toList());
    }
}

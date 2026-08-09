package ink.ziip.championshipscore.worker;

import ink.ziip.championshipscore.platform.bukkit.bingo.BingoStarterKitService;
import ink.ziip.championshipscore.protocol.TeamSnapshot;
import ink.ziip.championshipscore.protocol.BingoPresentation;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.entity.Player;

/** Worker adapter that supplies the frozen team's colour to the shared starter-kit service. */
final class WorkerStarterKit {
    private WorkerStarterKit() {
    }

    static void give(Player player, TeamSnapshot team, BingoPresentation presentation) {
        BingoStarterKitService.give(player, color(team.colorCode()),
                WorkerPresentationService.message(presentation, "compass.item_name", "{0}", team.name())
                        .decoration(TextDecoration.ITALIC, false),
                java.util.List.of(WorkerPresentationService.message(presentation, "compass.item_hint")
                        .decoration(TextDecoration.ITALIC, false)));
    }

    private static Color color(String hex) {
        try {
            String value = hex.startsWith("#") ? hex.substring(1) : hex;
            return Color.fromRGB(Integer.parseInt(value, 16));
        } catch (RuntimeException ignored) {
            return Color.WHITE;
        }
    }
}

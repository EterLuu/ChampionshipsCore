package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.finale.FinaleGameRegistry;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class EventCommandSupport {
    private EventCommandSupport() {}

    static @Nullable GameTypeEnum parse(@NotNull String raw) {
        return GameTypeEnum.fromCommand(raw);
    }

    static boolean canSchedule(@NotNull GameTypeEnum game) {
        return !FinaleGameRegistry.isRegistered(game)
                && ChampionshipsCore.getInstance().getScheduleManager().supportsFormalEvent(game);
    }

    static @NotNull List<String> enabledFormalGames() {
        List<String> names = new ArrayList<>();
        ChampionshipsCore plugin = ChampionshipsCore.getInstance();
        for (GameTypeEnum game : GameTypeEnum.values()) {
            if (plugin.getGameManager().isGameEnabled(game) && canSchedule(game))
                names.add(game.commandName());
        }
        return names;
    }
}

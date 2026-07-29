package ink.ziip.championshipscore.command.game.area.bingo;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Opens the bingo area-prepare GUI (no args). The GUI lists existing bingo areas and a "new area" button;
 * picking one (or creating one) enters prepare mode, which walks the admin through bingo's few config
 * points. See {@link ink.ziip.championshipscore.api.game.area.prepare.bingo.BingoPrepareFlow}.
 */
public class BingoPrepareSubCommand extends BaseSubCommand {
    public BingoPrepareSubCommand() {
        super("prepare", "打开宾果场地准备 GUI", "/cc game area bingo prepare");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Utils.sendAdminError(sender, "准备向导只能由玩家打开。");
            return true;
        }
        plugin.getPrepareSessionManager().openAreaListGui(player, GameTypeEnum.Bingo);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

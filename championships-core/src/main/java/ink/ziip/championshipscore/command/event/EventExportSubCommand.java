package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.api.event.ChampionshipResultsExporter;
import ink.ziip.championshipscore.api.event.EventStateStore;
import ink.ziip.championshipscore.api.rank.ChampionshipArchiveSnapshot;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public final class EventExportSubCommand extends BaseSubCommand {
    public EventExportSubCommand() {
        super("export", "导出当前正式比赛的完整积分 JSON", "/cc event export");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 0) {
            sendUsage(sender);
            return true;
        }
        EventStateStore.ActiveEvent active = new EventStateStore(plugin).load();
        if (active == null) {
            Utils.sendAdminError(sender, MessageConfig.EVENT_EXPORT_NO_EVENT);
            return true;
        }
        if (plugin.getScheduleManager().hasRunningFormalEvent()) {
            Utils.sendAdminError(sender, MessageConfig.EVENT_EXPORT_STILL_RUNNING);
            return true;
        }
        if (plugin.getRankManager().getRound() < 1) {
            Utils.sendAdminError(sender, MessageConfig.EVENT_EXPORT_NO_POINTS);
            return true;
        }
        Utils.sendAdminInfo(sender, MessageConfig.EVENT_EXPORT_SUMMARIZING);
        plugin.getRankManager().createChampionshipArchiveSnapshot().whenComplete((snapshot, failure) -> {
            if (failure != null) {
                error(sender, failure.getMessage());
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(sender, active, snapshot));
        });
        return true;
    }

    private void write(CommandSender sender, EventStateStore.ActiveEvent active,
                       ChampionshipArchiveSnapshot snapshot) {
        try {
            Path exported = ChampionshipResultsExporter.export(plugin.getDataFolder().toPath(), active.slug(), snapshot);
            Bukkit.getScheduler().runTask(plugin, () -> Utils.sendAdminSuccess(sender,
                    MessageConfig.EVENT_EXPORT_COMPLETED
                            .replace("%event%", active.title())
                            .replace("%teams%", String.valueOf(snapshot.teams().size()))
                            .replace("%players%", String.valueOf(snapshot.players().size()))
                            .replace("%file%", String.valueOf(exported))));
        } catch (Exception failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to export event scores", failure);
            error(sender, failure.getMessage());
        }
    }

    private void error(CommandSender sender, String message) {
        String detail = message == null || message.isBlank() ? MessageConfig.EVENT_UNKNOWN_ERROR : message;
        Bukkit.getScheduler().runTask(plugin, () -> Utils.sendAdminError(sender, MessageConfig.EVENT_EXPORT_FAILED.replace("%detail%", detail)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

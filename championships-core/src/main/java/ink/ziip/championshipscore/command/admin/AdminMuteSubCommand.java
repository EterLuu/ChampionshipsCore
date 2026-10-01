package ink.ziip.championshipscore.command.admin;

import ink.ziip.championshipscore.api.chat.MuteDuration;
import ink.ziip.championshipscore.api.player.identity.PlayerUuidLookupException;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

/** Administrator routes for persistent public-chat moderation. */
public final class AdminMuteSubCommand extends BaseSubCommand {
    public enum Action { MUTE, TEMPMUTE, UNMUTE }

    private final Action action;

    public AdminMuteSubCommand(Action action) {
        super(action.name().toLowerCase(java.util.Locale.ROOT), switch (action) {
            case MUTE -> "永久禁言玩家的公共聊天";
            case TEMPMUTE -> "限时禁言玩家的公共聊天";
            case UNMUTE -> "解除玩家的公共聊天禁言";
        }, switch (action) {
            case MUTE -> "/cc admin mute <玩家> [原因...]";
            case TEMPMUTE -> "/cc admin tempmute <玩家> <时长> [原因...]";
            case UNMUTE -> "/cc admin unmute <玩家>";
        }, ADMIN_PERMISSION);
        this.action = action;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        int required = action == Action.TEMPMUTE ? 2 : 1;
        if (args.length < required || (action == Action.UNMUTE && args.length != 1)) {
            sendUsage(sender);
            return true;
        }
        String name = args[0];
        long duration = 0;
        if (action == Action.TEMPMUTE) {
            try {
                duration = MuteDuration.parseMillis(args[1]);
                Math.addExact(System.currentTimeMillis(), duration);
            } catch (IllegalArgumentException | ArithmeticException error) {
                Utils.sendAdminError(sender, MessageConfig.ADMIN_MUTE_INVALID_DURATION);
                return true;
            }
        }
        String reason = args.length > required
                ? String.join(" ", Arrays.copyOfRange(args, required, args.length)) : MessageConfig.MUTE_DEFAULT_REASON;
        final String moderationReason = reason == null || reason.isBlank()
                ? MessageConfig.MUTE_DEFAULT_REASON : reason;
        long requestedDuration = duration;
        CompletionStage<?> operation = action == Action.UNMUTE
                ? plugin.getPublicChatMuteManager().unmute(name)
                : plugin.getPublicChatMuteManager().mute(name, duration, moderationReason, sender.getName());
        operation.whenComplete((result, failure) -> {
            if (!plugin.isEnabled()) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    Throwable cause = failure;
                    while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                    plugin.getLogger().log(Level.WARNING, "Public chat moderation failed for " + name, cause);
                    Utils.sendAdminError(sender, (cause instanceof PlayerUuidLookupException
                            ? MessageConfig.ADMIN_MUTE_LOOKUP_FAILED : MessageConfig.ADMIN_MUTE_SAVE_FAILED)
                            .replace("%player%", name));
                    return;
                }
                String feedback = switch (action) {
                    case MUTE -> MessageConfig.ADMIN_MUTE_SET;
                    case TEMPMUTE -> MessageConfig.ADMIN_TEMP_MUTE_SET;
                    case UNMUTE -> Boolean.TRUE.equals(result) ? MessageConfig.ADMIN_UNMUTE_SET : MessageConfig.ADMIN_MUTE_NOT_MUTED;
                };
                Utils.sendAdminSuccess(sender, feedback.replace("%player%", name)
                        .replace("%duration%", MuteDuration.formatMillis(requestedDuration)).replace("%reason%", moderationReason));
                if (action == Action.UNMUTE && !Boolean.TRUE.equals(result)) return;
                Player target = Bukkit.getPlayerExact(name);
                if (target != null) {
                    String notice = switch (action) {
                        case MUTE -> MessageConfig.CHAT_MUTED_PERMANENT;
                        case TEMPMUTE -> MessageConfig.CHAT_MUTED_TEMPORARY;
                        case UNMUTE -> MessageConfig.CHAT_UNMUTED;
                    };
                    target.sendMessage(Utils.toComponent(notice.replace("%reason%", moderationReason)
                            .replace("%remaining%", MuteDuration.formatMillis(requestedDuration))));
                }
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            var names = new ArrayList<>(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            if (action == Action.UNMUTE) names.addAll(plugin.getPublicChatMuteManager().mutedNames());
            return filterStartsWith(names.stream().distinct().sorted().toList(), args[0]);
        }
        if (args.length == 2 && action == Action.TEMPMUTE)
            return filterStartsWith(List.of("30s", "5m", "30m", "1h", "1h30m", "1d", "7d"), args[1]);
        return List.of();
    }
}

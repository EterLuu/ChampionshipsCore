package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.api.daily.DailyParty;
import ink.ziip.championshipscore.api.daily.DailyPartyManager;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PartySubCommand extends BaseSubCommand {
    public PartySubCommand() {
        super("party", "管理临时同行小队", "/cc party <invite|accept|leave|disband|info> [玩家]", PLAYER_PERMISSION);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Utils.sendAdminError(sender, "该命令只能由玩家执行");
            return true;
        }
        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }
        DailyPartyManager parties = plugin.getDailyManager().partyManager();
        UUID uuid = player.getUniqueId();
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "invite" -> {
                if (args.length != 2) { sendUsage(sender); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !parties.invite(uuid, target.getUniqueId())) {
                    message(sender, "&#ff6b26邀请失败&#bababa；目标可能离线、已有同行小队，或你不是队长。");
                    return true;
                }
                message(sender, "&#31e061已邀请 &#24abff" + target.getName() + "&#ededed，邀请 60 秒有效。");
                message(target, "&#fff566" + player.getName() + " &#ededed邀请你加入同行小队，使用 &#fff566/cc party accept");
            }
            case "accept" -> {
                DailyParty party = parties.accept(uuid);
                if (party == null) message(sender, "&#ff6b26没有有效邀请。");
                else broadcast(party.members(), "&#31e061" + player.getName() + " &#ededed加入了同行小队。");
            }
            case "leave" -> {
                DailyParty party = parties.getParty(uuid);
                if (party == null || !parties.leave(uuid)) message(sender, "&#ff6b26你不在同行小队中，或正在游戏。");
                else {
                    message(sender, "&#fff566已离开同行小队。");
                    broadcast(party.members(), "&#fff566" + player.getName() + " &#ededed离开了同行小队。");
                }
            }
            case "disband" -> {
                DailyParty party = parties.getParty(uuid);
                Set<UUID> members = party == null ? Set.of() : party.members();
                if (!parties.disband(uuid)) message(sender, "&#ff6b26只有队长可以解散同行小队，且游戏中不可操作。");
                else broadcast(members, "&#fff566同行小队已解散。");
            }
            case "info" -> {
                DailyParty party = parties.getParty(uuid);
                if (party == null) {
                    message(sender, "&#bababa当前没有同行小队；邀请玩家时会自动创建。");
                    return true;
                }
                String names = party.members().stream().map(Bukkit::getOfflinePlayer)
                        .map(offline -> offline.getName() == null ? offline.getUniqueId().toString() : offline.getName())
                        .reduce((left, right) -> left + ", " + right).orElse("-");
                String leader = Bukkit.getOfflinePlayer(party.leader()).getName();
                message(sender, "&#fff566队长: &#ededed" + (leader == null ? party.leader() : leader)
                        + " &#696969• &#fff566成员: &#ededed" + names + " &#696969• &#fff566选择: &#ededed"
                        + (party.selectedGame() == null ? "-" : party.selectedGame().name()));
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void broadcast(Set<UUID> players, String value) {
        for (UUID uuid : players) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) message(online, value);
        }
    }

    private void message(CommandSender sender, String value) {
        sender.sendMessage(Utils.translateColorCodes(MessageConfig.DAILY_PREFIX + value));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return complete(List.of("invite", "accept", "leave", "disband", "info"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("invite") && sender instanceof Player player) {
            DailyPartyManager parties = plugin.getDailyManager().partyManager();
            UUID senderId = player.getUniqueId();
            return complete(Bukkit.getOnlinePlayers().stream()
                    .filter(candidate -> !candidate.getUniqueId().equals(senderId))
                    .filter(candidate -> parties.getParty(candidate.getUniqueId()) == null)
                    .map(Player::getName)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList(), args[1]);
        }
        return List.of();
    }
}

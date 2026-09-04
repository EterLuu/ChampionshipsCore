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
        super("party", "管理临时小队", "/cc party <invite|accept|leave|disband|info> [玩家]", PLAYER_PERMISSION);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Utils.sendAdminError(sender, MessageConfig.COMMAND_PLAYER_ONLY);
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
                    message(sender, MessageConfig.DAILY_PARTY_INVITE_FAILED);
                    return true;
                }
                message(sender, MessageConfig.DAILY_PARTY_INVITE_SENT.replace("%target%", target.getName()));
                message(target, MessageConfig.DAILY_PARTY_INVITE_RECEIVED.replace("%player%", player.getName()));
            }
            case "accept" -> {
                DailyParty party = parties.accept(uuid);
                if (party == null) message(sender, MessageConfig.DAILY_PARTY_NO_INVITE);
                else broadcast(party.members(), MessageConfig.DAILY_PARTY_MEMBER_JOINED_BROADCAST.replace("%player%", player.getName()));
            }
            case "leave" -> {
                DailyParty party = parties.getParty(uuid);
                if (party == null || !parties.leave(uuid)) message(sender, MessageConfig.DAILY_PARTY_NOT_MEMBER_OR_PLAYING);
                else {
                    message(sender, MessageConfig.DAILY_PARTY_LEFT_COMMAND);
                    broadcast(party.members(), MessageConfig.DAILY_PARTY_MEMBER_LEFT_BROADCAST.replace("%player%", player.getName()));
                }
            }
            case "disband" -> {
                DailyParty party = parties.getParty(uuid);
                Set<UUID> members = party == null ? Set.of() : party.members();
                if (!parties.disband(uuid)) message(sender, MessageConfig.DAILY_PARTY_DISBAND_DENIED);
                else broadcast(members, MessageConfig.DAILY_PARTY_DISBANDED_COMMAND);
            }
            case "info" -> {
                DailyParty party = parties.getParty(uuid);
                if (party == null) {
                    message(sender, MessageConfig.DAILY_PARTY_NONE);
                    return true;
                }
                String names = party.members().stream().map(Bukkit::getOfflinePlayer)
                        .map(offline -> offline.getName() == null ? offline.getUniqueId().toString() : offline.getName())
                        .reduce((left, right) -> left + ", " + right).orElse("-");
                String leader = Bukkit.getOfflinePlayer(party.leader()).getName();
                message(sender, MessageConfig.DAILY_PARTY_INFO
                        .replace("%leader%", leader == null ? party.leader().toString() : leader)
                        .replace("%members%", names)
                        .replace("%game%", party.selectedGame() == null ? "-" : party.selectedGame().name()));
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
        sender.sendMessage(Utils.translateColorCodes(MessageConfig.DAILY_PREFIXED.replace("%message%", value)));
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

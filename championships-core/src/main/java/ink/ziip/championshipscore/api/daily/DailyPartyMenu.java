package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Party management screen for invitations, membership and party lifecycle. */
final class DailyPartyMenu {
    private static final int SIZE = 54;
    private static final int SUMMARY_SLOT = 4;
    private static final int INVITE_SLOT = 7;
    private static final int FIRST_PLAYER_SLOT = 18;
    private static final int LAST_PLAYER_SLOT = 44;
    private static final int ACTION_SLOT = 45;
    private static final int BACK_SLOT = 47;
    private static final int REFRESH_SLOT = 49;
    private static final int CLOSE_SLOT = 53;
    private final DailyManager daily;

    DailyPartyMenu(DailyManager daily) {
        this.daily = daily;
    }

    void open(Player player) {
        PartyHolder holder = new PartyHolder(player.getUniqueId());
        holder.inventory = Bukkit.createInventory(holder, SIZE, Component.text(GuiConfig.text("daily.menus.party.team-function"), NamedTextColor.DARK_AQUA)
                .decorate(TextDecoration.BOLD));
        refresh(holder);
        player.openInventory(holder.inventory);
    }

    void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof PartyHolder holder) refresh(holder);
        }
    }

    void click(Player player, int slot, PartyHolder holder) {
        if (!holder.viewer.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == BACK_SLOT) {
            daily.openMenu(player);
            clickSound(player, 1F);
            return;
        }
        if (slot == REFRESH_SLOT) {
            refresh(holder);
            clickSound(player, 1.1F);
            return;
        }
        if (slot == INVITE_SLOT) {
            DailyPartyManager.PendingInvite invite = daily.partyManager().pendingInvite(player.getUniqueId());
            if (invite != null) {
                DailyParty party = daily.partyManager().accept(player.getUniqueId());
                if (party == null) {
                    daily.message(player.getUniqueId(), GuiConfig.text("daily.menus.party.this-invitation-cannot-be-accepted-at-this-time"));
                } else {
                    for (UUID member : party.members()) {
                        daily.message(member, player.getName() + GuiConfig.text("daily.menus.party.already-joined-the-party"));
                    }
                }
                refresh(holder);
                clickSound(player, party == null ? 0.8F : 1.2F);
            }
            return;
        }
        if (slot == ACTION_SLOT) {
            DailyParty party = daily.partyManager().getParty(player.getUniqueId());
            if (party == null) {
                daily.message(player.getUniqueId(), GuiConfig.text("daily.menus.party.please-click-on-the-online-players-above-to-send-team-invitations"));
                clickSound(player, 1F);
                return;
            }
            boolean success = party != null && party.isLeader(player.getUniqueId())
                    ? daily.partyManager().disband(player.getUniqueId())
                    : daily.partyManager().leave(player.getUniqueId());
            if (success) daily.message(player.getUniqueId(), party != null && party.isLeader(player.getUniqueId())
                    ? GuiConfig.text("daily.menus.party.the-party-has-been-disbanded") : GuiConfig.text("daily.menus.party.you-have-left-the-companion-group"));
            else daily.message(player.getUniqueId(), GuiConfig.text("daily.menus.party.team-cannot-be-modified-at-the-moment"));
            refresh(holder);
            clickSound(player, success ? 1F : 0.8F);
            return;
        }
        UUID target = holder.targetsBySlot.get(slot);
        if (target == null) return;
        if (!daily.partyManager().invite(player.getUniqueId(), target)) {
            daily.message(player.getUniqueId(), GuiConfig.text("daily.menus.party.this-player-cannot-be-invited-at-the-moment"));
        } else {
            daily.message(player.getUniqueId(), GuiConfig.text("daily.menus.party.already-sent-to") + playerName(target) + GuiConfig.text("daily.menus.party.send-invitation"));
            daily.message(target, player.getName() + GuiConfig.text("daily.menus.party.you-are-invited-to-join-the-party-please-open-the-game-lobby-to-accept-the-invitation"));
        }
        refresh(holder);
        clickSound(player, 1.1F);
    }

    private void refresh(PartyHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.targetsBySlot.clear();
        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, border);
        for (int slot = 45; slot < SIZE; slot++) inventory.setItem(slot, border);

        DailyParty party = daily.partyManager().getParty(holder.viewer);
        inventory.setItem(SUMMARY_SLOT, summaryItem(holder.viewer, party));
        DailyPartyManager.PendingInvite pending = daily.partyManager().pendingInvite(holder.viewer);
        inventory.setItem(INVITE_SLOT, pending == null ? inviteInfoItem(party) : pendingItem(pending));

        boolean canManage = party == null || party.isLeader(holder.viewer);
        List<Player> candidates = new ArrayList<>(Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.getUniqueId().equals(holder.viewer))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(LAST_PLAYER_SLOT - FIRST_PLAYER_SLOT + 1)
                .toList());
        int slot = FIRST_PLAYER_SLOT;
        for (Player candidate : candidates) {
            UUID candidateId = candidate.getUniqueId();
            DailyParty candidateParty = daily.partyManager().getParty(candidateId);
            String unavailableReason = daily.partyUnavailableReason(candidateId);
            inventory.setItem(slot, playerItem(candidate, candidateParty, unavailableReason, canManage));
            if (canManage && unavailableReason == null) holder.targetsBySlot.put(slot, candidateId);
            slot++;
        }
        if (candidates.isEmpty()) inventory.setItem(22, item(Material.GRAY_DYE,
                Component.text(GuiConfig.text("daily.menus.party.there-are-no-other-online-players-yet"), NamedTextColor.GRAY)
                        .decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("daily.menus.party.online-players-will-display-their-team-status-here"), NamedTextColor.DARK_GRAY))));

        String action = party == null ? GuiConfig.text("daily.menus.party.invitation-instructions") : party.isLeader(holder.viewer) ? GuiConfig.text("daily.menus.party.disband-the-team") : GuiConfig.text("daily.menus.party.leave-the-team");
        inventory.setItem(ACTION_SLOT, item(party == null ? Material.LIME_DYE : Material.RED_DYE,
                Component.text(action, party == null ? NamedTextColor.GREEN : NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD),
                List.of(Component.text(party == null ? GuiConfig.text("daily.menus.party.click-on-the-online-player-above-to-send-a-team-invitation") : GuiConfig.text("daily.menus.party.teams-cannot-be-modified-while-they-are-matching-or-playing-a-game"), NamedTextColor.GRAY))));
        inventory.setItem(BACK_SLOT, item(Material.ARROW,
                Component.text(GuiConfig.text("daily.menus.party.return-to-lobby"), NamedTextColor.WHITE).decorate(TextDecoration.BOLD), List.of()));
        inventory.setItem(REFRESH_SLOT, item(Material.CLOCK,
                Component.text(GuiConfig.text("daily.menus.party.refresh"), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("daily.menus.party.update-online-player-and-invitation-status"), NamedTextColor.GRAY))));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, Component.text(GuiConfig.text("daily.menus.party.close"), NamedTextColor.RED), List.of()));
    }

    private ItemStack summaryItem(UUID viewer, DailyParty party) {
        List<Component> lore = new ArrayList<>();
        if (party == null) {
            lore.add(Component.text(GuiConfig.text("daily.menus.party.you-currently-dont-have-a-party"), NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text(GuiConfig.text("daily.menus.party.click-player-list-to-send-invitation-hint"), NamedTextColor.GREEN));
        } else {
            lore.add(Component.text(GuiConfig.text("daily.menus.party.captain"), NamedTextColor.GRAY).append(Component.text(playerName(party.leader()), NamedTextColor.AQUA)));
            lore.add(Component.text(GuiConfig.text("daily.menus.party.number-of-people"), NamedTextColor.GRAY).append(Component.text(party.size() + GuiConfig.text("daily.menus.party.player-count-suffix"), NamedTextColor.WHITE)));
            if (party.selectedGame() != null) lore.add(Component.text(GuiConfig.text("daily.menus.party.select"), NamedTextColor.GRAY)
                    .append(Component.text(party.selectedGame().toString(), NamedTextColor.YELLOW)));
            lore.add(Component.empty());
            for (UUID member : party.members()) lore.add(Component.text((member.equals(party.leader()) ? "★ " : "• ")
                    + playerName(member), member.equals(viewer) ? NamedTextColor.GREEN : NamedTextColor.WHITE));
        }
        return playerHead(viewer, Component.text(GuiConfig.text("daily.menus.party.party"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD), lore);
    }

    private ItemStack inviteInfoItem(DailyParty party) {
        return item(Material.PAPER, Component.text(GuiConfig.text("daily.menus.party.invitation-status"), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                List.of(Component.text(party == null ? GuiConfig.text("daily.menus.party.no-invitations-pending") : GuiConfig.text("daily.menus.party.click-player-list-to-invite-action"), NamedTextColor.GRAY)));
    }

    private ItemStack pendingItem(DailyPartyManager.PendingInvite invite) {
        return item(Material.WRITABLE_BOOK, Component.text(GuiConfig.text("daily.menus.party.received-a-team-invitation"), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(Component.text(playerName(invite.leader()) + GuiConfig.text("daily.menus.party.invite-you-to-join-the-peer-group"), NamedTextColor.WHITE),
                        Component.empty(), Component.text(GuiConfig.text("daily.menus.party.click-to-accept-the-invitation"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD)));
    }

    private ItemStack playerItem(Player player, DailyParty party, String unavailableReason, boolean canManage) {
        boolean available = canManage && unavailableReason == null;
        List<Component> lore = new ArrayList<>();
        if (party == null) {
            lore.add(Component.text(GuiConfig.text("daily.menus.party.team-status"), NamedTextColor.GRAY)
                    .append(Component.text(GuiConfig.text("daily.menus.party.no-team"), NamedTextColor.GREEN)));
        } else {
            lore.add(Component.text(GuiConfig.text("daily.menus.party.team-status"), NamedTextColor.GRAY)
                    .append(Component.text(GuiConfig.text("daily.menus.party.teamed-up"), NamedTextColor.GOLD)));
            lore.add(Component.text(GuiConfig.text("daily.menus.party.captain"), NamedTextColor.GRAY)
                    .append(Component.text(playerName(party.leader()), NamedTextColor.AQUA)));
            lore.add(Component.text(GuiConfig.text("daily.menus.party.number-of-people"), NamedTextColor.GRAY)
                    .append(Component.text(party.size() + GuiConfig.text("daily.menus.party.player-count-suffix"), NamedTextColor.WHITE)));
        }
        if (party == null && unavailableReason != null) {
            lore.add(Component.text(GuiConfig.text("daily.menus.party.current-status"), NamedTextColor.GRAY)
                    .append(Component.text(unavailableReason, NamedTextColor.GOLD)));
        }
        lore.add(Component.empty());
        lore.add(Component.text(available ? GuiConfig.text("daily.menus.party.click-to-send-team-invitation")
                        : canManage ? GuiConfig.text("daily.menus.party.unable-to-send-invitation-at-the-moment") : GuiConfig.text("daily.menus.party.only-the-team-leader-can-send-invitations"),
                available ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY));
        return playerHead(player.getUniqueId(), Component.text(player.getName(), NamedTextColor.WHITE)
                        .decorate(TextDecoration.BOLD),
                lore);
    }

    private String playerName(UUID player) {
        Player online = Bukkit.getPlayer(player);
        if (online != null) return online.getName();
        String name = Bukkit.getOfflinePlayer(player).getName();
        return name == null ? player.toString().substring(0, 8) : name;
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack playerHead(UUID owner, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private static void clickSound(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, pitch);
    }

    static final class PartyHolder implements InventoryHolder {
        private final UUID viewer;
        private final Map<Integer, UUID> targetsBySlot = new HashMap<>();
        private Inventory inventory;

        private PartyHolder(UUID viewer) {
            this.viewer = viewer;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}

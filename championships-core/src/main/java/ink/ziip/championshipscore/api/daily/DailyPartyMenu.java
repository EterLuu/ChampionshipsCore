package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;

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
    private static final String MENU_PATH = MenuId.DAILY_PARTY.path();
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
        GuiConfig.MenuSpec menu = GuiConfig.menu(MENU_PATH, SIZE,
                Component.text(GuiConfig.text("daily.menus.party-screen.copy.team-function"), NamedTextColor.DARK_AQUA)
                        .decorate(TextDecoration.BOLD), defaultPlayerSlots());
        holder.inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
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
        if (slot == slot("close", CLOSE_SLOT)) {
            player.closeInventory();
            return;
        }
        if (slot == slot("back", BACK_SLOT)) {
            daily.openMenu(player);
            clickSound(player, 1F);
            return;
        }
        if (slot == slot("refresh", REFRESH_SLOT)) {
            refresh(holder);
            clickSound(player, 1.1F);
            return;
        }
        if (slot == slot("invitation", INVITE_SLOT)) {
            DailyPartyManager.PendingInvite invite = daily.partyManager().pendingInvite(player.getUniqueId());
            if (invite != null) {
                DailyParty party = daily.partyManager().accept(player.getUniqueId());
                if (party == null) {
                    daily.message(player.getUniqueId(), GuiConfig.text("daily.menus.party-screen.copy.this-invitation-cannot-be-accepted-at-this-time"));
                } else {
                    for (UUID member : party.members()) {
                        daily.message(member, player.getName() + GuiConfig.text("daily.menus.party-screen.copy.already-joined-the-party"));
                    }
                }
                refresh(holder);
                clickSound(player, party == null ? 0.8F : 1.2F);
            }
            return;
        }
        if (slot == slot("action", ACTION_SLOT)) {
            DailyParty party = daily.partyManager().getParty(player.getUniqueId());
            if (party == null) {
                daily.message(player.getUniqueId(), GuiConfig.text("daily.menus.party-screen.copy.please-click-on-the-online-players-above-to-send-team-invitations"));
                clickSound(player, 1F);
                return;
            }
            boolean success = party != null && party.isLeader(player.getUniqueId())
                    ? daily.partyManager().disband(player.getUniqueId())
                    : daily.partyManager().leave(player.getUniqueId());
            if (success) daily.message(player.getUniqueId(), party != null && party.isLeader(player.getUniqueId())
                    ? GuiConfig.text("daily.menus.party-screen.copy.the-party-has-been-disbanded") : GuiConfig.text("daily.menus.party-screen.copy.you-have-left-the-companion-group"));
            else daily.message(player.getUniqueId(), GuiConfig.text("daily.menus.party-screen.copy.team-cannot-be-modified-at-the-moment"));
            refresh(holder);
            clickSound(player, success ? 1F : 0.8F);
            return;
        }
        UUID target = holder.targetsBySlot.get(slot);
        if (target == null) return;
        if (!daily.partyManager().invite(player.getUniqueId(), target)) {
            daily.message(player.getUniqueId(), GuiConfig.text("daily.menus.party-screen.copy.this-player-cannot-be-invited-at-the-moment"));
        } else {
            daily.message(player.getUniqueId(), GuiConfig.text("daily.menus.party-screen.copy.already-sent-to") + playerName(target) + GuiConfig.text("daily.menus.party-screen.copy.send-invitation"));
            daily.message(target, player.getName() + GuiConfig.text("daily.menus.party-screen.copy.you-are-invited-to-join-the-party-please-open-the-game-lobby-to-accept-the-invitation"));
        }
        refresh(holder);
        clickSound(player, 1.1F);
    }

    private void refresh(PartyHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.targetsBySlot.clear();
        ItemStack border = configured("border", null, Map.of(),
                item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of()));
        for (int slot : GuiConfig.slots(MENU_PATH + ".layout.border",
                List.of(0, 1, 2, 3, 5, 6, 8, 46, 48, 50, 51, 52)))
            if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, border);

        DailyParty party = daily.partyManager().getParty(holder.viewer);
        inventory.setItem(slot("summary", SUMMARY_SLOT), configured("summary", party == null ? "solo" : "party",
                Map.of("size", party == null ? 1 : party.size()), summaryItem(holder.viewer, party)));
        DailyPartyManager.PendingInvite pending = daily.partyManager().pendingInvite(holder.viewer);
        inventory.setItem(slot("invitation", INVITE_SLOT), configured("invitation", pending == null ? "none" : "pending",
                Map.of("inviter", pending == null ? "" : playerName(pending.leader())),
                pending == null ? inviteInfoItem(party) : pendingItem(pending)));

        boolean canManage = party == null || party.isLeader(holder.viewer);
        List<Player> candidates = new ArrayList<>(Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.getUniqueId().equals(holder.viewer))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(GuiConfig.slots(MENU_PATH + ".layout.content", defaultPlayerSlots()).size())
                .toList());
        List<Integer> playerSlots = GuiConfig.slots(MENU_PATH + ".layout.content", defaultPlayerSlots());
        int index = 0;
        for (Player candidate : candidates) {
            int slot = playerSlots.get(index++);
            UUID candidateId = candidate.getUniqueId();
            DailyParty candidateParty = daily.partyManager().getParty(candidateId);
            String unavailableReason = daily.partyUnavailableReason(candidateId);
            inventory.setItem(slot, configured("player", canManage && unavailableReason == null ? "available" : "unavailable",
                    Map.of("player", candidate.getName()), playerItem(candidate, candidateParty, unavailableReason, canManage)));
            if (canManage && unavailableReason == null) holder.targetsBySlot.put(slot, candidateId);
        }
        if (candidates.isEmpty()) inventory.setItem(slot("empty", 22), configured("empty", null, Map.of(), item(Material.GRAY_DYE,
                Component.text(GuiConfig.text("daily.menus.party-screen.copy.there-are-no-other-online-players-yet"), NamedTextColor.GRAY)
                        .decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("daily.menus.party-screen.copy.online-players-will-display-their-team-status-here"), NamedTextColor.DARK_GRAY)))));

        String action = party == null ? GuiConfig.text("daily.menus.party-screen.copy.invitation-instructions") : party.isLeader(holder.viewer) ? GuiConfig.text("daily.menus.party-screen.copy.disband-the-team") : GuiConfig.text("daily.menus.party-screen.copy.leave-the-team");
        inventory.setItem(slot("action", ACTION_SLOT), configured("action", party == null ? "invite" : party.isLeader(holder.viewer) ? "disband" : "leave", Map.of(), item(party == null ? Material.LIME_DYE : Material.RED_DYE,
                Component.text(action, party == null ? NamedTextColor.GREEN : NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD),
                List.of(Component.text(party == null ? GuiConfig.text("daily.menus.party-screen.copy.click-on-the-online-player-above-to-send-a-team-invitation") : GuiConfig.text("daily.menus.party-screen.copy.teams-cannot-be-modified-while-they-are-matching-or-playing-a-game"), NamedTextColor.GRAY)))));
        inventory.setItem(slot("back", BACK_SLOT), configured("back", null, Map.of(), item(Material.ARROW,
                Component.text(GuiConfig.text("daily.menus.party-screen.copy.return-to-lobby"), NamedTextColor.WHITE).decorate(TextDecoration.BOLD), List.of())));
        inventory.setItem(slot("refresh", REFRESH_SLOT), configured("refresh", null, Map.of(), item(Material.CLOCK,
                Component.text(GuiConfig.text("daily.menus.party-screen.copy.refresh"), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("daily.menus.party-screen.copy.update-online-player-and-invitation-status"), NamedTextColor.GRAY)))));
        inventory.setItem(slot("close", CLOSE_SLOT), configured("close", null, Map.of(), item(Material.BARRIER,
                Component.text(GuiConfig.text("daily.menus.party-screen.copy.close"), NamedTextColor.RED), List.of())));
    }

    private static List<Integer> defaultPlayerSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = FIRST_PLAYER_SLOT; slot <= LAST_PLAYER_SLOT; slot++) slots.add(slot);
        return slots;
    }

    private static int slot(String item, int fallback) {
        return ConfiguredGui.slot(MENU_PATH + ".items." + item, fallback);
    }

    private static ItemStack configured(String item, String state, Map<String, ?> placeholders, ItemStack fallback) {
        return ConfiguredGui.item(MENU_PATH + ".items." + item, state, placeholders, fallback);
    }

    private ItemStack summaryItem(UUID viewer, DailyParty party) {
        List<Component> lore = new ArrayList<>();
        if (party == null) {
            lore.add(Component.text(GuiConfig.text("daily.menus.party-screen.copy.you-currently-dont-have-a-party"), NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text(GuiConfig.text("daily.menus.party-screen.copy.click-player-list-to-send-invitation-hint"), NamedTextColor.GREEN));
        } else {
            lore.add(Component.text(GuiConfig.text("daily.menus.party-screen.copy.captain"), NamedTextColor.GRAY).append(Component.text(playerName(party.leader()), NamedTextColor.AQUA)));
            lore.add(Component.text(GuiConfig.text("daily.menus.party-screen.copy.number-of-people"), NamedTextColor.GRAY).append(Component.text(party.size() + GuiConfig.text("daily.menus.party-screen.copy.player-count-suffix"), NamedTextColor.WHITE)));
            if (party.selectedGame() != null) lore.add(Component.text(GuiConfig.text("daily.menus.party-screen.copy.select"), NamedTextColor.GRAY)
                    .append(Component.text(party.selectedGame().toString(), NamedTextColor.YELLOW)));
            lore.add(Component.empty());
            for (UUID member : party.members()) lore.add(Component.text((member.equals(party.leader()) ? "★ " : "• ")
                    + playerName(member), member.equals(viewer) ? NamedTextColor.GREEN : NamedTextColor.WHITE));
        }
        return playerHead(viewer, Component.text(GuiConfig.text("daily.menus.party-screen.copy.party"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD), lore);
    }

    private ItemStack inviteInfoItem(DailyParty party) {
        return item(Material.PAPER, Component.text(GuiConfig.text("daily.menus.party-screen.copy.invitation-status"), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                List.of(Component.text(party == null ? GuiConfig.text("daily.menus.party-screen.copy.no-invitations-pending") : GuiConfig.text("daily.menus.party-screen.copy.click-player-list-to-invite-action"), NamedTextColor.GRAY)));
    }

    private ItemStack pendingItem(DailyPartyManager.PendingInvite invite) {
        return item(Material.WRITABLE_BOOK, Component.text(GuiConfig.text("daily.menus.party-screen.copy.received-a-team-invitation"), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(Component.text(playerName(invite.leader()) + GuiConfig.text("daily.menus.party-screen.copy.invite-you-to-join-the-peer-group"), NamedTextColor.WHITE),
                        Component.empty(), Component.text(GuiConfig.text("daily.menus.party-screen.copy.click-to-accept-the-invitation"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD)));
    }

    private ItemStack playerItem(Player player, DailyParty party, String unavailableReason, boolean canManage) {
        boolean available = canManage && unavailableReason == null;
        List<Component> lore = new ArrayList<>();
        if (party == null) {
            lore.add(Component.text(GuiConfig.text("daily.menus.party-screen.copy.team-status"), NamedTextColor.GRAY)
                    .append(Component.text(GuiConfig.text("daily.menus.party-screen.copy.no-team"), NamedTextColor.GREEN)));
        } else {
            lore.add(Component.text(GuiConfig.text("daily.menus.party-screen.copy.team-status"), NamedTextColor.GRAY)
                    .append(Component.text(GuiConfig.text("daily.menus.party-screen.copy.teamed-up"), NamedTextColor.GOLD)));
            lore.add(Component.text(GuiConfig.text("daily.menus.party-screen.copy.captain"), NamedTextColor.GRAY)
                    .append(Component.text(playerName(party.leader()), NamedTextColor.AQUA)));
            lore.add(Component.text(GuiConfig.text("daily.menus.party-screen.copy.number-of-people"), NamedTextColor.GRAY)
                    .append(Component.text(party.size() + GuiConfig.text("daily.menus.party-screen.copy.player-count-suffix"), NamedTextColor.WHITE)));
        }
        if (party == null && unavailableReason != null) {
            lore.add(Component.text(GuiConfig.text("daily.menus.party-screen.copy.current-status"), NamedTextColor.GRAY)
                    .append(Component.text(unavailableReason, NamedTextColor.GOLD)));
        }
        lore.add(Component.empty());
        lore.add(Component.text(available ? GuiConfig.text("daily.menus.party-screen.copy.click-to-send-team-invitation")
                        : canManage ? GuiConfig.text("daily.menus.party-screen.copy.unable-to-send-invitation-at-the-moment") : GuiConfig.text("daily.menus.party-screen.copy.only-the-team-leader-can-send-invitations"),
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
        return ink.ziip.championshipscore.api.gui.GuiMenu.item(material, name, lore);
    }

    private static ItemStack playerHead(UUID owner, Component name, List<Component> lore) {
        return ink.ziip.championshipscore.api.gui.GuiMenu.playerHead(owner, name, lore);
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

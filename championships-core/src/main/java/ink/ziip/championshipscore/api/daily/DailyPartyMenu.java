package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Party management screen configured entirely as invitation, member and lifecycle buttons. */
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
        GuiConfig.MenuSpec menu = GuiConfig.menu(MENU_PATH, SIZE, "", defaultPlayerSlots());
        holder.inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
        refresh(holder);
        player.openInventory(holder.inventory);
    }

    void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder()
                    instanceof PartyHolder holder) refresh(holder);
        }
    }

    void click(Player player, int slot, PartyHolder holder) {
        if (!holder.viewer.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        if (slot == holder.layout.control("close")) {
            player.closeInventory();
            return;
        }
        if (slot == holder.layout.control("back")) {
            daily.openMenu(player);
            clickSound(player, 1F);
            return;
        }
        if (slot == holder.layout.control("refresh")) {
            refresh(holder);
            clickSound(player, 1.1F);
            return;
        }
        if (holder.navigate(slot)) {
            refresh(holder);
            clickSound(player, 1.1F);
            return;
        }
        if (slot == holder.layout.control("invitation")) {
            DailyPartyManager.PendingInvite invite =
                    daily.partyManager().pendingInvite(player.getUniqueId());
            if (!java.util.Objects.equals(invite, holder.pending)) {
                refresh(holder);
                return;
            }
            if (invite != null) {
                DailyParty party = daily.partyManager().accept(player.getUniqueId());
                if (party == null) {
                    daily.message(
                            player.getUniqueId(),
                            MessageConfig.MAP_EDITOR_DAILY_PARTY_INVITATION_CANNOT_ACCEPT);
                } else {
                    for (UUID member : party.members())
                        daily.message(
                                member,
                                MessageConfig.MAP_EDITOR_DAILY_PARTY_MEMBER_JOINED.replace(
                                        "%player%", player.getName()));
                }
                refresh(holder);
                clickSound(player, party == null ? 0.8F : 1.2F);
            }
            return;
        }
        if (slot == holder.layout.control("action")) {
            if (!currentParty(holder)) {
                refresh(holder);
                return;
            }
            DailyParty party = daily.partyManager().getParty(player.getUniqueId());
            if (party == null) {
                daily.message(
                        player.getUniqueId(),
                        MessageConfig.MAP_EDITOR_DAILY_PARTY_CLICK_ONLINE_TO_INVITE);
                clickSound(player, 1F);
                return;
            }
            boolean leader = party.isLeader(player.getUniqueId());
            boolean success =
                    leader
                            ? daily.partyManager().disband(player.getUniqueId())
                            : daily.partyManager().leave(player.getUniqueId());
            if (success)
                daily.message(
                        player.getUniqueId(),
                        leader
                                ? MessageConfig.MAP_EDITOR_DAILY_PARTY_DISBANDED
                                : MessageConfig.MAP_EDITOR_DAILY_PARTY_LEFT);
            else
                daily.message(
                        player.getUniqueId(), MessageConfig.MAP_EDITOR_DAILY_PARTY_CANNOT_MODIFY);
            refresh(holder);
            clickSound(player, success ? 1F : 0.8F);
            return;
        }
        UUID target = holder.targetsBySlot.get(slot);
        if (target == null) return;
        if (!currentParty(holder)) {
            refresh(holder);
            return;
        }
        if (!daily.partyManager().invite(player.getUniqueId(), target)) {
            daily.message(
                    player.getUniqueId(),
                    MessageConfig.MAP_EDITOR_DAILY_PARTY_PLAYER_CANNOT_INVITE);
        } else {
            daily.message(
                    player.getUniqueId(),
                    MessageConfig.MAP_EDITOR_DAILY_PARTY_INVITATION_SENT.replace(
                            "%target%", DailyPartyPresentation.playerName(target)));
            daily.message(
                    target,
                    MessageConfig.MAP_EDITOR_DAILY_PARTY_INVITED_BY.replace(
                            "%player%", player.getName()));
        }
        refresh(holder);
        clickSound(player, 1.1F);
    }

    private void refresh(PartyHolder holder) {
        Inventory inventory = holder.inventory;
        holder.clear();
        holder.targetsBySlot.clear();

        DailyParty party = daily.partyManager().getParty(holder.viewer);
        holder.party = party;
        holder.revision = party == null ? 0 : party.revision();
        inventory.setItem(holder.layout.control("summary"), summaryItem(holder, party));
        DailyPartyManager.PendingInvite pending = daily.partyManager().pendingInvite(holder.viewer);
        holder.pending = pending;
        inventory.setItem(
                holder.layout.control("invitation"),
                holder.item(
                        "invitation",
                        pending == null ? "none" : "pending",
                        Map.of(
                                "inviter",
                                pending == null
                                        ? ""
                                        : DailyPartyPresentation.playerName(pending.leader()))));

        boolean canManage = party == null || party.isLeader(holder.viewer);

        List<? extends Player> candidates =
                Bukkit.getOnlinePlayers().stream()
                        .filter(player -> !player.getUniqueId().equals(holder.viewer))
                        .sorted(
                                Comparator.comparing(
                                        Player::getName, String.CASE_INSENSITIVE_ORDER))
                        .toList();
        int offset = holder.paginate(candidates.size());
        List<Integer> playerSlots = holder.layout.pageSlots(holder.page, candidates.size());
        for (int index = 0; index < playerSlots.size(); index++) {
            Player candidate = candidates.get(offset + index);
            int slot = playerSlots.get(index);
            UUID candidateId = candidate.getUniqueId();
            DailyParty candidateParty = daily.partyManager().getParty(candidateId);
            DailyManager.PartyUnavailableReason unavailableReason =
                    daily.partyUnavailableReason(candidateId);
            String state =
                    !canManage
                            ? "locked"
                            : unavailableReason == null ? "available" : unavailableReason.state();
            inventory.setItem(slot, playerItem(holder, candidate, candidateParty, state));
            if (canManage && unavailableReason == null) holder.targetsBySlot.put(slot, candidateId);
        }
        holder.navigation(candidates.size());

        String actionState =
                party == null ? "invite" : party.isLeader(holder.viewer) ? "disband" : "leave";
        inventory.setItem(
                holder.layout.control("action"), holder.item("action", actionState, Map.of()));
        inventory.setItem(holder.layout.control("back"), holder.item("back", null, Map.of()));
        inventory.setItem(holder.layout.control("refresh"), holder.item("refresh", null, Map.of()));
        inventory.setItem(holder.layout.control("close"), holder.item("close", null, Map.of()));
    }

    private boolean currentParty(PartyHolder holder) {
        DailyParty current = daily.partyManager().getParty(holder.viewer);
        return current == holder.party
                && (current == null || current.revision() == holder.revision);
    }

    private ItemStack summaryItem(PartyHolder holder, DailyParty party) {
        String absent = GuiConfig.text(MENU_PATH + ".items.unavailable.title");
        return DailyPartyPresentation.item(
                holder.family + ".items.summary",
                party == null ? "solo" : "party",
                Map.of(
                        "leader",
                        party == null ? absent : DailyPartyPresentation.playerName(party.leader()),
                        "size",
                        party == null ? 1 : party.size(),
                        "game",
                        party == null || party.selectedGame() == null
                                ? absent
                                : party.selectedGame().toString()),
                party,
                "colored");
    }

    private ItemStack playerItem(
            PartyHolder holder, Player player, DailyParty party, String state) {
        return holder.item(
                "player",
                state,
                Map.of(
                        "player",
                        player.getName(),
                        "leader",
                        party == null
                                ? GuiConfig.text(MENU_PATH + ".items.unavailable.title")
                                : DailyPartyPresentation.playerName(party.leader()),
                        "size",
                        party == null ? 0 : party.size()));
    }

    private static List<Integer> defaultPlayerSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = FIRST_PLAYER_SLOT; slot <= LAST_PLAYER_SLOT; slot++) slots.add(slot);
        return slots;
    }

    private static void clickSound(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, pitch);
    }

    static final class PartyHolder extends DailyMenuPage {
        private final Map<Integer, UUID> targetsBySlot = new HashMap<>();
        private DailyParty party;
        private long revision;
        private DailyPartyManager.PendingInvite pending;

        private PartyHolder(UUID viewer) {
            super(
                    viewer,
                    MenuId.DAILY_PARTY,
                    SIZE,
                    defaultPlayerSlots(),
                    List.of(0, 1, 2, 3, 5, 6, 8, 46, 48, 50, 51, 52),
                    Map.of(
                            "summary",
                            SUMMARY_SLOT,
                            "invitation",
                            INVITE_SLOT,
                            "action",
                            ACTION_SLOT,
                            "back",
                            BACK_SLOT,
                            "refresh",
                            REFRESH_SLOT,
                            "close",
                            CLOSE_SLOT,
                            "previous",
                            46,
                            "page",
                            48,
                            "next",
                            50));
        }
    }
}

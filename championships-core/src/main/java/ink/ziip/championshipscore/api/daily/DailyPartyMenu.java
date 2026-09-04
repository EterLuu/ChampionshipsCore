package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.GuiText;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

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
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof PartyHolder holder) refresh(holder);
        }
    }

    void click(Player player, int slot, PartyHolder holder) {
        if (!holder.viewer.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        if (slot == slot("close", CLOSE_SLOT)) { player.closeInventory(); return; }
        if (slot == slot("back", BACK_SLOT)) { daily.openMenu(player); clickSound(player, 1F); return; }
        if (slot == slot("refresh", REFRESH_SLOT)) { refresh(holder); clickSound(player, 1.1F); return; }
        if (slot == slot("invitation", INVITE_SLOT)) {
            DailyPartyManager.PendingInvite invite = daily.partyManager().pendingInvite(player.getUniqueId());
            if (invite != null) {
                DailyParty party = daily.partyManager().accept(player.getUniqueId());
                if (party == null) {
                    daily.message(player.getUniqueId(), MessageConfig.MAP_EDITOR_DAILY_PARTY_INVITATION_CANNOT_ACCEPT);
                } else {
                    for (UUID member : party.members())
                        daily.message(member, MessageConfig.MAP_EDITOR_DAILY_PARTY_MEMBER_JOINED.replace("%player%", player.getName()));
                }
                refresh(holder);
                clickSound(player, party == null ? 0.8F : 1.2F);
            }
            return;
        }
        if (slot == slot("action", ACTION_SLOT)) {
            DailyParty party = daily.partyManager().getParty(player.getUniqueId());
            if (party == null) {
                daily.message(player.getUniqueId(), MessageConfig.MAP_EDITOR_DAILY_PARTY_CLICK_ONLINE_TO_INVITE);
                clickSound(player, 1F);
                return;
            }
            boolean leader = party.isLeader(player.getUniqueId());
            boolean success = leader ? daily.partyManager().disband(player.getUniqueId())
                    : daily.partyManager().leave(player.getUniqueId());
            if (success) daily.message(player.getUniqueId(), leader ? MessageConfig.MAP_EDITOR_DAILY_PARTY_DISBANDED
                    : MessageConfig.MAP_EDITOR_DAILY_PARTY_LEFT);
            else daily.message(player.getUniqueId(), MessageConfig.MAP_EDITOR_DAILY_PARTY_CANNOT_MODIFY);
            refresh(holder);
            clickSound(player, success ? 1F : 0.8F);
            return;
        }
        UUID target = holder.targetsBySlot.get(slot);
        if (target == null) return;
        if (!daily.partyManager().invite(player.getUniqueId(), target)) {
            daily.message(player.getUniqueId(), MessageConfig.MAP_EDITOR_DAILY_PARTY_PLAYER_CANNOT_INVITE);
        } else {
            daily.message(player.getUniqueId(), MessageConfig.MAP_EDITOR_DAILY_PARTY_INVITATION_SENT.replace("%target%", playerName(target)));
            daily.message(target, MessageConfig.MAP_EDITOR_DAILY_PARTY_INVITED_BY.replace("%player%", player.getName()));
        }
        refresh(holder);
        clickSound(player, 1.1F);
    }

    private void refresh(PartyHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.targetsBySlot.clear();
        ItemStack border = configured("border", null, Map.of(), Material.BLACK_STAINED_GLASS_PANE);
        for (int slot : GuiConfig.slots(MENU_PATH + ".layout.border",
                List.of(0, 1, 2, 3, 5, 6, 8, 46, 48, 50, 51, 52)))
            if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, border);

        DailyParty party = daily.partyManager().getParty(holder.viewer);
        inventory.setItem(slot("summary", SUMMARY_SLOT), summaryItem(holder.viewer, party));
        DailyPartyManager.PendingInvite pending = daily.partyManager().pendingInvite(holder.viewer);
        inventory.setItem(slot("invitation", INVITE_SLOT), configured("invitation", pending == null ? "none" : "pending",
                Map.of("inviter", pending == null ? "" : playerName(pending.leader())), Material.PAPER));

        boolean canManage = party == null || party.isLeader(holder.viewer);
        List<Integer> playerSlots = GuiConfig.slots(MENU_PATH + ".layout.content", defaultPlayerSlots());
        List<Player> candidates = new ArrayList<>(Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.getUniqueId().equals(holder.viewer))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(playerSlots.size()).toList());
        int index = 0;
        for (Player candidate : candidates) {
            int slot = playerSlots.get(index++);
            UUID candidateId = candidate.getUniqueId();
            DailyParty candidateParty = daily.partyManager().getParty(candidateId);
            DailyManager.PartyUnavailableReason unavailableReason = daily.partyUnavailableReason(candidateId);
            String state = !canManage ? "locked"
                    : unavailableReason == null ? "available" : unavailableReason.state();
            inventory.setItem(slot, playerItem(candidate, candidateParty, state));
            if (canManage && unavailableReason == null) holder.targetsBySlot.put(slot, candidateId);
        }
        if (candidates.isEmpty()) inventory.setItem(slot("empty", 22), configured("empty", null, Map.of(), Material.GRAY_DYE));

        String actionState = party == null ? "invite" : party.isLeader(holder.viewer) ? "disband" : "leave";
        inventory.setItem(slot("action", ACTION_SLOT), configured("action", actionState, Map.of(), party == null ? Material.LIME_DYE : Material.RED_DYE));
        inventory.setItem(slot("back", BACK_SLOT), configured("back", null, Map.of(), Material.ARROW));
        inventory.setItem(slot("refresh", REFRESH_SLOT), configured("refresh", null, Map.of(), Material.CLOCK));
        inventory.setItem(slot("close", CLOSE_SLOT), configured("close", null, Map.of(), Material.BARRIER));
    }

    private ItemStack summaryItem(UUID viewer, DailyParty party) {
        ItemStack item = configured("summary", party == null ? "solo" : "party",
                Map.of("leader", party == null ? "-" : playerName(party.leader()),
                        "size", party == null ? 1 : party.size(),
                        "game", party == null || party.selectedGame() == null ? "-" : party.selectedGame().toString()),
                Material.PLAYER_HEAD);
        if (party != null) {
            List<Component> members = new ArrayList<>();
            for (UUID member : party.members())
                members.add(Component.text(member.equals(party.leader())
                        ? GuiText.LEADER_MARK : GuiText.MEMBER_MARK,
                        member.equals(party.leader()) ? NamedTextColor.GOLD : NamedTextColor.GRAY)
                        .append(Component.text(playerName(member))));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
                lore.addAll(members);
                meta.lore(lore);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private ItemStack playerItem(Player player, DailyParty party, String state) {
        return configured("player", state,
                Map.of("player", player.getName(),
                        "leader", party == null ? "-" : playerName(party.leader()),
                        "size", party == null ? 0 : party.size()),
                Material.PLAYER_HEAD);
    }

    private static List<Integer> defaultPlayerSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = FIRST_PLAYER_SLOT; slot <= LAST_PLAYER_SLOT; slot++) slots.add(slot);
        return slots;
    }

    private static int slot(String item, int fallback) {
        return ConfiguredGui.slot(MENU_PATH + ".items." + item, fallback);
    }

    private static ItemStack configured(String item, String state, Map<String, ?> placeholders, Material material) {
        return ConfiguredGui.item(MENU_PATH + ".items." + item, state, placeholders, material,
                Component.empty(), List.of(), false);
    }

    private String playerName(UUID player) {
        Player online = Bukkit.getPlayer(player);
        if (online != null) return online.getName();
        String name = Bukkit.getOfflinePlayer(player).getName();
        return name == null ? player.toString().substring(0, 8) : name;
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

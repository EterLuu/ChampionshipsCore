package ink.ziip.championshipscore.api.team.gui;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.ChampionshipPermissions;
import ink.ziip.championshipscore.api.player.entry.PlayerEntry;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.team.TeamManager;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.view.AnvilView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Native administrator UI for every operation exposed below {@code /cc team}. */
public final class TeamManagementMenu implements Listener {
    private static final String OVERVIEW_PATH = MenuId.TEAMS_OVERVIEW.path();
    private static final String TEAM_PATH = MenuId.TEAMS_MEMBERS.path();
    private static final int INVENTORY_SIZE = 54;
    private static final int PAGE_SIZE = 36;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 47;
    private static final int REFRESH_SLOT = 48;
    private static final int PAGE_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int CLOSE_SLOT = 53;
    private static final int CREATE_SLOT = 45;
    private static final int QUICK_ASSIGN_SLOT = 46;
    private static final int TELEPORT_ALL_SLOT = 52;
    private static final int ADD_ONLINE_SLOT = 45;
    private static final int ADD_HISTORY_SLOT = 46;
    private static final int MANUAL_INPUT_SLOT = 45;
    private static final int HISTORY_BACK_SLOT = 46;
    private static final int TELEPORT_TEAM_SLOT = 51;
    private static final int DELETE_TEAM_SLOT = 52;
    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;
    private static final List<Integer> COLOR_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16, 19,
            20, 21, 22, 23, 24, 25, 28, 29);
    private static final Map<String, String> COLOR_HEX = createColorHex();
    private static final Map<String, String> COLOR_LABELS = createColorLabels();

    private final ChampionshipsCore plugin;
    private final Map<UUID, InputSession> inputs = new HashMap<>();

    public TeamManagementMenu(@NotNull ChampionshipsCore plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openOverview(@NotNull Player player, int requestedPage) {
        List<ChampionshipTeam> teams = sortedTeams();
        int pages = pageCount(teams.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.OVERVIEW, null, page);
        GuiConfig.MenuSpec menu = GuiConfig.menu(OVERVIEW_PATH, INVENTORY_SIZE,
                title(GuiConfig.text("teams.menus.shared.copy.team-management"), NamedTextColor.GOLD), contentSlots());
        Inventory inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
        holder.inventory = inventory;
        fillOverview(holder, teams, pages);
        player.openInventory(inventory);
    }

    private void fillOverview(@NotNull MenuHolder holder, @NotNull List<ChampionshipTeam> teams, int pages) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.targets.clear();
        int from = holder.page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, teams.size());
        List<Integer> slots = GuiConfig.slots(OVERVIEW_PATH + ".layout.content", contentSlots());
        for (int index = from; index < to && index - from < slots.size(); index++) {
            ChampionshipTeam team = teams.get(index);
            int slot = slots.get(index - from);
            inventory.setItem(slot, configured(OVERVIEW_PATH, "team", null,
                    Map.of("team", team.getName(), "online", team.getOnlinePlayers().size(), "members", team.getMembers().size()), teamItem(team)));
            holder.targets.put(slot, team.getName());
        }
        if (teams.isEmpty()) {
            inventory.setItem(22, configured(OVERVIEW_PATH, "empty", null, Map.of(), item(Material.GRAY_DYE,
                    Component.text(GuiConfig.text("teams.menus.shared.copy.no-team-yet"), NamedTextColor.GRAY),
                    List.of(Component.text(GuiConfig.text("teams.menus.shared.copy.click-on-the-lower-left-corner-to-create-the-first-team"), NamedTextColor.DARK_GRAY)), false)));
        }

        fillFooter(inventory, OVERVIEW_PATH);
        inventory.setItem(CREATE_SLOT, configured(OVERVIEW_PATH, "create", null, Map.of(), item(Material.EMERALD,
                Component.text(GuiConfig.text("teams.menus.shared.copy.create-a-team"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("teams.menus.shared.copy.enter-internal-name-then-select-team-color"), NamedTextColor.GRAY)), false)));
        inventory.setItem(QUICK_ASSIGN_SLOT, configured(OVERVIEW_PATH, "quick-assign", null, Map.of(), item(Material.PLAYER_HEAD,
                Component.text(GuiConfig.text("teams.menus.shared.copy.quick-team-transfer"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("teams.menus.shared.copy.select-online-players-then-select-target-team"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("teams.menus.shared.copy.players-who-already-have-a-team-will-ask-for-a-second-confirmation"), NamedTextColor.DARK_GRAY)), false)));
        inventory.setItem(REFRESH_SLOT, configured(OVERVIEW_PATH, "refresh", null, Map.of(), item(Material.SUNFLOWER,
                Component.text(GuiConfig.text("teams.menus.shared.copy.refresh"), NamedTextColor.YELLOW), List.of(), false)));
        inventory.setItem(PAGE_SLOT, configured(OVERVIEW_PATH, "page", null,
                Map.of("page", holder.page + 1, "pages", pages, "count", teams.size()),
                pageItem(holder.page, pages, teams.size(), GuiConfig.text("teams.menus.shared.copy.team-count-suffix"))));
        if (holder.page > 0) inventory.setItem(PREVIOUS_SLOT, configured(OVERVIEW_PATH, "previous", null, Map.of(), navigationItem(GuiConfig.text("teams.menus.shared.copy.previous-page"))));
        if (holder.page + 1 < pages) inventory.setItem(NEXT_SLOT, configured(OVERVIEW_PATH, "next", null, Map.of(), navigationItem(GuiConfig.text("teams.menus.shared.copy.next-page"))));
        inventory.setItem(TELEPORT_ALL_SLOT, configured(OVERVIEW_PATH, "teleport-all", null, Map.of(), item(Material.ENDER_EYE,
                Component.text(GuiConfig.text("teams.menus.shared.copy.teleport-all-teams-here"), NamedTextColor.LIGHT_PURPLE),
                List.of(Component.text(GuiConfig.text("teams.menus.shared.copy.only-transfer-online-members"), NamedTextColor.GRAY),
                        Component.empty(), Component.text(GuiConfig.text("teams.menus.shared.copy.confirmation-required-after-clicking"), NamedTextColor.YELLOW)), false)));
        inventory.setItem(CLOSE_SLOT, configured(OVERVIEW_PATH, "close", null, Map.of(), closeItem()));
    }

    private void openTeam(@NotNull Player player, @NotNull String teamName, int requestedPage) {
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            Utils.sendAdminError(player, GuiConfig.text("teams.menus.shared.copy.the-team-no-longer-exists") + teamName);
            openOverview(player, 0);
            return;
        }

        List<MemberView> members = members(team);
        int pages = pageCount(members.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.TEAM, team.getName(), page);
        GuiConfig.MenuSpec menu = GuiConfig.menu(TEAM_PATH, INVENTORY_SIZE,
                teamTitle(team, GuiConfig.text("teams.menus.shared.copy.member-management")), contentSlots());
        Inventory inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
        holder.inventory = inventory;
        fillTeam(holder, team, members, pages);
        player.openInventory(inventory);
    }

    private void fillTeam(@NotNull MenuHolder holder, @NotNull ChampionshipTeam team,
                          @NotNull List<MemberView> members, int pages) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.targets.clear();
        int from = holder.page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, members.size());
        List<Integer> slots = GuiConfig.slots(TEAM_PATH + ".layout.content", contentSlots());
        for (int index = from; index < to && index - from < slots.size(); index++) {
            MemberView member = members.get(index);
            int slot = slots.get(index - from);
            inventory.setItem(slot, configured(TEAM_PATH, "member", member.online ? "online" : "offline",
                    Map.of("player", member.name), memberItem(member)));
            holder.targets.put(slot, member.name());
        }
        if (members.isEmpty()) {
            inventory.setItem(22, configured(TEAM_PATH, "empty", null, Map.of(), item(Material.PLAYER_HEAD,
                    Component.text(GuiConfig.text("teams.menus.shared.copy.there-are-no-members-in-the-team-yet"), NamedTextColor.GRAY),
                    List.of(Component.text(GuiConfig.text("teams.menus.shared.copy.add-online-players-from-the-lower-left-corner-or-enter-player-name"), NamedTextColor.DARK_GRAY)), false)));
        }

        fillFooter(inventory, TEAM_PATH);
        boolean full = members.size() >= CCConfig.TEAM_MAX_MEMBERS;
        if (full) {
            inventory.setItem(ADD_ONLINE_SLOT, configured(TEAM_PATH, "add-player", "full", Map.of(), item(Material.RED_DYE,
                    Component.text(GuiConfig.text("teams.menus.shared.copy.the-queue-is-full"), NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    List.of(Component.text(members.size() + "/" + CCConfig.TEAM_MAX_MEMBERS + GuiConfig.text("teams.menus.shared.copy.members"), NamedTextColor.GRAY),
                            Component.text(GuiConfig.text("teams.menus.shared.copy.remove-members-before-you-can-continue-adding-them"), NamedTextColor.DARK_GRAY)), false)));
        } else {
            inventory.setItem(ADD_ONLINE_SLOT, configured(TEAM_PATH, "add-player", "available", Map.of(), item(Material.LIME_DYE,
                    Component.text(GuiConfig.text("teams.menus.shared.copy.add-online-player"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    List.of(Component.text(GuiConfig.text("teams.menus.shared.copy.select-from-players-currently-online-and-not-yet-in-a-queue"), NamedTextColor.GRAY)), false)));
            inventory.setItem(ADD_HISTORY_SLOT, configured(TEAM_PATH, "history", null, Map.of(), item(Material.BOOK,
                    Component.text(GuiConfig.text("teams.menus.shared.copy.history-offline-players"), NamedTextColor.AQUA),
                    List.of(Component.text(GuiConfig.text("teams.menus.shared.copy.select-from-logged-offline-players-who-have-not-yet-been-queued"), NamedTextColor.GRAY),
                            Component.text(GuiConfig.text("teams.menus.shared.copy.you-can-also-enter-the-player-name-manually-on-the-next-page"), NamedTextColor.DARK_GRAY)), false)));
        }
        inventory.setItem(PREVIOUS_SLOT, configured(TEAM_PATH, holder.page > 0 ? "previous" : "back", null, Map.of(),
                holder.page > 0 ? navigationItem(GuiConfig.text("teams.menus.shared.copy.previous-page"))
                        : item(Material.ARROW, Component.text(GuiConfig.text("teams.menus.shared.copy.return-to-team-list"), NamedTextColor.WHITE), List.of(), false)));
        inventory.setItem(REFRESH_SLOT, configured(TEAM_PATH, "refresh", null, Map.of(), item(Material.SUNFLOWER,
                Component.text(GuiConfig.text("teams.menus.shared.copy.refresh"), NamedTextColor.YELLOW), List.of(), false)));
        inventory.setItem(PAGE_SLOT, configured(TEAM_PATH, "page", null,
                Map.of("page", holder.page + 1, "pages", pages, "count", members.size()),
                pageItem(holder.page, pages, members.size(), GuiConfig.text("teams.menus.shared.copy.page-member-count-suffix"))));
        if (holder.page + 1 < pages) inventory.setItem(NEXT_SLOT, configured(TEAM_PATH, "next", null, Map.of(), navigationItem(GuiConfig.text("teams.menus.shared.copy.next-page"))));
        inventory.setItem(TELEPORT_TEAM_SLOT, configured(TEAM_PATH, "teleport", null, Map.of("online", team.getOnlinePlayers().size()), item(Material.ENDER_PEARL,
                Component.text(GuiConfig.text("teams.menus.shared.copy.teleport-this-team-here"), NamedTextColor.LIGHT_PURPLE),
                List.of(Component.text(team.getOnlinePlayers().size() + GuiConfig.text("teams.menus.shared.copy.online-members-will-be-transferred"), NamedTextColor.GRAY)), false)));
        inventory.setItem(DELETE_TEAM_SLOT, configured(TEAM_PATH, "delete", null, Map.of(), item(Material.TNT,
                Component.text(GuiConfig.text("teams.menus.shared.copy.delete-team"), NamedTextColor.RED),
                List.of(Component.text(GuiConfig.text("teams.menus.shared.copy.permanently-delete-team-and-membership-relationships"), NamedTextColor.GRAY),
                        Component.empty(), Component.text(GuiConfig.text("teams.menus.shared.copy.confirmation-required-after-clicking"), NamedTextColor.YELLOW)), false)));
        inventory.setItem(CLOSE_SLOT, configured(TEAM_PATH, "close", null, Map.of(), closeItem()));
    }

    private void openAddPlayer(@NotNull Player player, @NotNull String teamName, int requestedPage) {
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            openOverview(player, 0);
            return;
        }
        if (team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) {
            Utils.sendAdminError(player, GuiConfig.text("teams.menus.shared.copy.the-number-of-people-in-the-team-has-reached-the-upper-limit") + CCConfig.TEAM_MAX_MEMBERS);
            openTeam(player, teamName, 0);
            return;
        }
        List<? extends Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(candidate -> plugin.getTeamManager().getTeamByPlayer(candidate) == null)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int pages = pageCount(candidates.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.ADD_PLAYER, team.getName(), page);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, teamTitle(team, GuiConfig.text("teams.menus.shared.copy.select-new-member")));
        holder.inventory = inventory;
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, candidates.size());
        for (int index = from; index < to; index++) {
            Player candidate = candidates.get(index);
            int slot = index - from;
            inventory.setItem(slot, playerHead(candidate.getUniqueId(), candidate.getName(),
                    Component.text(candidate.getName(), NamedTextColor.GREEN),
                    List.of(Component.text(GuiConfig.text("teams.menus.shared.copy.click-to-join") + team.getName(), teamColor(team)))));
            holder.targets.put(slot, candidate.getName());
        }
        if (candidates.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE,
                    Component.text(GuiConfig.text("teams.menus.shared.copy.no-online-players-to-add"), NamedTextColor.GRAY),
                    List.of(Component.text(GuiConfig.text("teams.menus.shared.copy.can-be-used-after-returning-enter-player-name"), NamedTextColor.DARK_GRAY)), false));
        }
        fillFooter(inventory);
        inventory.setItem(BACK_SLOT, item(Material.ARROW, Component.text(GuiConfig.text("teams.menus.shared.copy.return-to-member-management"), NamedTextColor.WHITE), List.of(), false));
        if (page > 0) inventory.setItem(PREVIOUS_SLOT, navigationItem(GuiConfig.text("teams.menus.shared.copy.previous-page")));
        inventory.setItem(PAGE_SLOT, pageItem(page, pages, candidates.size(), GuiConfig.text("teams.menus.shared.copy.optional-players")));
        if (page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem(GuiConfig.text("teams.menus.shared.copy.next-page")));
        inventory.setItem(CLOSE_SLOT, closeItem());
        player.openInventory(inventory);
    }

    private void openColorPicker(@NotNull Player player, @NotNull String newTeamName) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.COLOR, newTeamName, 0);
        Inventory inventory = Bukkit.createInventory(holder, 36, title(GuiConfig.text("teams.menus.shared.copy.choose-team-color"), NamedTextColor.GOLD));
        holder.inventory = inventory;
        List<String> colors = List.of(Utils.getColorNames());
        for (int index = 0; index < colors.size(); index++) {
            String color = colors.get(index);
            ChampionshipTeam usedBy = sortedTeams().stream()
                    .filter(team -> team.getColorName().equalsIgnoreCase(color))
                    .findFirst().orElse(null);
            Material material = material(color + "_WOOL", Material.WHITE_WOOL);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(GuiConfig.text("teams.menus.shared.copy.internal-color-name") + color, NamedTextColor.GRAY));
            lore.add(Component.text(GuiConfig.text("teams.menus.shared.copy.display-color") + COLOR_HEX.get(color), color(color)));
            lore.add(Component.empty());
            if (usedBy == null) lore.add(Component.text(GuiConfig.text("teams.menus.shared.copy.click-to-create-a-team"), NamedTextColor.GREEN));
            else lore.add(Component.text(GuiConfig.text("teams.menus.shared.copy.has-been") + usedBy.getName() + GuiConfig.text("teams.menus.shared.copy.use"), NamedTextColor.RED));
            int slot = COLOR_SLOTS.get(index);
            inventory.setItem(slot, item(usedBy == null ? material : Material.GRAY_DYE,
                    Component.text(COLOR_LABELS.get(color), usedBy == null ? color(color) : NamedTextColor.DARK_GRAY)
                            .decorate(TextDecoration.BOLD), lore, false));
            if (usedBy == null) holder.targets.put(slot, color);
        }
        inventory.setItem(31, item(Material.ARROW, Component.text(GuiConfig.text("teams.menus.shared.copy.return-to-team-list"), NamedTextColor.WHITE), List.of(), false));
        player.openInventory(inventory);
    }

    private void openQuickPlayers(@NotNull Player player, int requestedPage) {
        List<? extends Player> players = Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int pages = pageCount(players.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.QUICK_PLAYER, null, page);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, title(GuiConfig.text("teams.menus.shared.copy.quickly-adjust-the-team-select-players"), NamedTextColor.AQUA));
        holder.inventory = inventory;
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, players.size());
        for (int index = from; index < to; index++) {
            Player candidate = players.get(index);
            ChampionshipTeam current = plugin.getTeamManager().getTeamByPlayer(candidate);
            List<Component> lore = new ArrayList<>();
            lore.add(current == null
                    ? Component.text(GuiConfig.text("teams.menus.shared.copy.not-currently-in-a-team"), NamedTextColor.GRAY)
                    : Component.text(GuiConfig.text("teams.menus.shared.copy.current-team"), NamedTextColor.GRAY)
                    .append(Component.text(current.getName(), teamColor(current))));
            lore.add(Component.empty());
            lore.add(Component.text(GuiConfig.text("teams.menus.shared.copy.click-to-select-target-team"), NamedTextColor.YELLOW));
            int slot = index - from;
            inventory.setItem(slot, playerHead(candidate.getUniqueId(), candidate.getName(),
                    Component.text(candidate.getName(), NamedTextColor.GREEN).decorate(TextDecoration.BOLD), lore));
            holder.playerTargets.put(slot, candidate.getUniqueId());
            holder.targets.put(slot, candidate.getName());
        }
        fillFooter(inventory);
        inventory.setItem(BACK_SLOT, item(Material.ARROW, Component.text(GuiConfig.text("teams.menus.shared.copy.return-to-team-list"), NamedTextColor.WHITE), List.of(), false));
        if (page > 0) inventory.setItem(PREVIOUS_SLOT, navigationItem(GuiConfig.text("teams.menus.shared.copy.previous-page")));
        inventory.setItem(PAGE_SLOT, pageItem(page, pages, players.size(), GuiConfig.text("teams.menus.shared.copy.online-players")));
        if (page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem(GuiConfig.text("teams.menus.shared.copy.next-page")));
        inventory.setItem(CLOSE_SLOT, closeItem());
        player.openInventory(inventory);
    }

    private void openTargetTeams(@NotNull Player player, @NotNull UUID selectedUuid,
                                 @NotNull String selectedName, int returnPage) {
        List<ChampionshipTeam> teams = sortedTeams();
        ChampionshipTeam current = plugin.getTeamManager().getTeamByPlayer(selectedUuid);
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.TARGET_TEAM, null, returnPage);
        holder.selectedPlayerUuid = selectedUuid;
        holder.memberName = selectedName;
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, title(GuiConfig.text("teams.menus.shared.copy.select") + selectedName + GuiConfig.text("teams.menus.shared.copy.team-possessive-suffix"), NamedTextColor.GOLD));
        holder.inventory = inventory;
        for (int index = 0; index < Math.min(PAGE_SIZE, teams.size()); index++) {
            ChampionshipTeam team = teams.get(index);
            boolean same = current != null && current.equals(team);
            boolean full = team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS;
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(team.getMembers().size() + "/" + CCConfig.TEAM_MAX_MEMBERS + GuiConfig.text("teams.menus.shared.copy.members"), NamedTextColor.GRAY));
            lore.add(Component.empty());
            if (same) lore.add(Component.text(GuiConfig.text("teams.menus.shared.copy.current-membership-team"), NamedTextColor.GREEN));
            else if (full) lore.add(Component.text(GuiConfig.text("teams.menus.shared.copy.the-queue-is-full"), NamedTextColor.RED));
            else if (current == null) lore.add(Component.text(GuiConfig.text("teams.menus.shared.copy.click-to-join-directly"), NamedTextColor.YELLOW));
            else lore.add(Component.text(GuiConfig.text("teams.menus.shared.copy.click-to-confirm-the-team-transfer"), NamedTextColor.YELLOW));
            inventory.setItem(index, item(material(team.getColorName() + "_WOOL", Material.WHITE_WOOL),
                    Component.text(team.getName(), same || full ? NamedTextColor.DARK_GRAY : teamColor(team))
                            .decorate(TextDecoration.BOLD), lore, same));
            if (!same && !full) holder.targets.put(index, team.getName());
        }
        if (teams.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE, Component.text(GuiConfig.text("teams.menus.shared.copy.no-teams-available-yet"), NamedTextColor.GRAY), List.of(), false));
        }
        fillFooter(inventory);
        inventory.setItem(BACK_SLOT, item(Material.ARROW, Component.text(GuiConfig.text("teams.menus.shared.copy.return-to-online-players"), NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(49, playerHead(selectedUuid, selectedName,
                Component.text(selectedName, NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(current == null ? Component.text(GuiConfig.text("teams.menus.shared.copy.not-currently-in-a-team"), NamedTextColor.GRAY)
                        : Component.text(GuiConfig.text("teams.menus.shared.copy.current") + current.getName(), teamColor(current)))));
        inventory.setItem(CLOSE_SLOT, closeItem());
        player.openInventory(inventory);
    }

    private void openKnownPlayers(@NotNull Player player, @NotNull String teamName, int requestedPage) {
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            openOverview(player, 0);
            return;
        }
        if (team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) {
            Utils.sendAdminError(player, GuiConfig.text("teams.menus.shared.copy.the-number-of-people-in-the-team-has-reached-the-upper-limit") + CCConfig.TEAM_MAX_MEMBERS);
            openTeam(player, teamName, 0);
            return;
        }
        plugin.getPlayerManager().getKnownPlayersAsync().whenComplete((knownPlayers, failure) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    ChampionshipTeam currentTeam = plugin.getTeamManager().getTeam(teamName);
                    if (failure != null || currentTeam == null) {
                        Utils.sendAdminError(player, GuiConfig.text("teams.menus.shared.copy.unable-to-read-player-history"));
                        openTeam(player, teamName, 0);
                        return;
                    }
                    renderKnownPlayers(player, currentTeam, requestedPage, knownPlayers);
                }));
    }

    private void renderKnownPlayers(@NotNull Player player, @NotNull ChampionshipTeam team,
                                    int requestedPage, @NotNull List<PlayerEntry> knownPlayers) {
        String teamName = team.getName();
        List<PlayerEntry> candidates = knownPlayers.stream()
                .filter(entry -> Bukkit.getPlayer(entry.getUuid()) == null)
                .filter(entry -> plugin.getTeamManager().getTeamByPlayer(entry.getUuid()) == null)
                .sorted(Comparator.comparing(PlayerEntry::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int pages = pageCount(candidates.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.KNOWN_PLAYER, teamName, page);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, teamTitle(team, GuiConfig.text("teams.menus.shared.copy.history-offline-players")));
        holder.inventory = inventory;
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, candidates.size());
        for (int index = from; index < to; index++) {
            PlayerEntry candidate = candidates.get(index);
            int slot = index - from;
            inventory.setItem(slot, playerHead(candidate.getUuid(), candidate.getName(),
                    Component.text(candidate.getName(), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                    List.of(Component.text(GuiConfig.text("teams.menus.shared.copy.historical-player-currently-offline"), NamedTextColor.DARK_GRAY),
                            Component.empty(), Component.text(GuiConfig.text("teams.menus.shared.copy.click-to-join") + team.getName(), teamColor(team)))));
            holder.playerTargets.put(slot, candidate.getUuid());
            holder.targets.put(slot, candidate.getName());
        }
        if (candidates.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE, Component.text(GuiConfig.text("teams.menus.shared.copy.no-optional-offline-player-history"), NamedTextColor.GRAY),
                    List.of(Component.text(GuiConfig.text("teams.menus.shared.copy.you-can-use-the-lower-left-corner-to-manually-enter-the-player-name"), NamedTextColor.DARK_GRAY)), false));
        }
        fillFooter(inventory);
        inventory.setItem(MANUAL_INPUT_SLOT, item(Material.NAME_TAG, Component.text(GuiConfig.text("teams.menus.shared.copy.manually-enter-player-name"), NamedTextColor.AQUA),
                List.of(Component.text(GuiConfig.text("teams.menus.shared.copy.only-used-if-the-player-cannot-be-found-in-the-history-list"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("teams.menus.shared.copy.please-check-the-spelling-carefully"), NamedTextColor.YELLOW)), false));
        inventory.setItem(HISTORY_BACK_SLOT, item(Material.ARROW, Component.text(GuiConfig.text("teams.menus.shared.copy.return-to-member-management"), NamedTextColor.WHITE), List.of(), false));
        if (page > 0) inventory.setItem(PREVIOUS_SLOT, navigationItem(GuiConfig.text("teams.menus.shared.copy.previous-page")));
        inventory.setItem(PAGE_SLOT, pageItem(page, pages, candidates.size(), GuiConfig.text("teams.menus.shared.copy.offline-players")));
        if (page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem(GuiConfig.text("teams.menus.shared.copy.next-page")));
        inventory.setItem(CLOSE_SLOT, closeItem());
        player.openInventory(inventory);
    }

    private void openConfirmation(@NotNull Player player, @NotNull Confirmation action, @Nullable String teamName,
                                  @Nullable String memberName, @Nullable UUID selectedPlayerUuid, int returnPage) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.CONFIRM, teamName, returnPage);
        holder.confirmation = action;
        holder.memberName = memberName;
        holder.selectedPlayerUuid = selectedPlayerUuid;
        Inventory inventory = Bukkit.createInventory(holder, 27, title(GuiConfig.text("teams.menus.shared.copy.confirm-action"), NamedTextColor.RED));
        holder.inventory = inventory;
        Component subject = switch (action) {
            case DELETE_TEAM -> Component.text(GuiConfig.text("teams.menus.shared.copy.delete-team-confirmation-title") + teamName, NamedTextColor.RED);
            case REMOVE_MEMBER -> Component.text(GuiConfig.text("teams.menus.shared.copy.remove-member") + memberName, NamedTextColor.RED);
            case TELEPORT_ALL -> Component.text(GuiConfig.text("teams.menus.shared.copy.teleport-all-teams"), NamedTextColor.LIGHT_PURPLE);
            case MOVE_MEMBER -> Component.text(GuiConfig.text("teams.menus.shared.copy.will") + memberName + GuiConfig.text("teams.menus.shared.copy.adjust-to") + teamName, NamedTextColor.GOLD);
        };
        Material subjectMaterial = switch (action) {
            case DELETE_TEAM -> Material.TNT;
            case REMOVE_MEMBER -> Material.RED_DYE;
            case TELEPORT_ALL -> Material.ENDER_EYE;
            case MOVE_MEMBER -> Material.COMPASS;
        };
        inventory.setItem(13, item(subjectMaterial,
                subject.decorate(TextDecoration.BOLD), confirmationLore(action, teamName), false));
        inventory.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE,
                Component.text(GuiConfig.text("teams.menus.shared.copy.confirm"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD), List.of(), false));
        inventory.setItem(CANCEL_SLOT, item(Material.RED_CONCRETE,
                Component.text(GuiConfig.text("teams.menus.shared.copy.cancel"), NamedTextColor.RED), List.of(), false));
        player.openInventory(inventory);
    }

    private void openTextInput(@NotNull Player player, @NotNull InputPurpose purpose, @Nullable String teamName) {
        String prompt = purpose == InputPurpose.CREATE_TEAM ? GuiConfig.text("teams.menus.shared.copy.enter-the-team-s-internal-name") : GuiConfig.text("teams.menus.shared.copy.enter-the-name-of-the-player-you-want-to-add");
        InputSession previous = inputs.remove(player.getUniqueId());
        if (previous != null) previous.inventory.clear();
        AnvilView view = MenuType.ANVIL.create(player, title(prompt, NamedTextColor.GOLD));
        player.openInventory(view);
        AnvilInventory inventory = view.getTopInventory();
        InputSession session = new InputSession(purpose, teamName, inventory);
        inputs.put(player.getUniqueId(), session);
        inventory.setFirstItem(item(Material.PAPER, Component.text(prompt, NamedTextColor.YELLOW),
                List.of(Component.text(GuiConfig.text("teams.menus.shared.copy.enter-in-the-rename-field-above-then-click-on-the-result-box-on-the-right"), NamedTextColor.GRAY)), false));
        view.setMaximumRepairCost(0);
        view.setRepairCost(0);
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        InputSession input = inputs.get(player.getUniqueId());
        if (input != null && input.inventory == top) {
            event.setCancelled(true);
            if (!player.hasPermission(ChampionshipPermissions.ADMIN)) {
                finishInput(player, input);
                player.closeInventory();
                Utils.sendAdminError(player, GuiConfig.text("teams.menus.shared.copy.you-no-longer-have-permission-to-use-the-team-management-interface"));
                return;
            }
            if (event.getRawSlot() == 2) submitInput(player, input, (AnvilView) event.getView());
            return;
        }
        if (!(top.getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!holder.viewer.equals(player.getUniqueId()) || event.getClickedInventory() != top) return;
        if (!player.hasPermission(ChampionshipPermissions.ADMIN)) {
            player.closeInventory();
            Utils.sendAdminError(player, GuiConfig.text("teams.menus.shared.copy.you-no-longer-have-permission-to-use-the-team-management-interface"));
            return;
        }
        handleMenuClick(player, holder, event.getRawSlot());
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            InputSession input = inputs.get(player.getUniqueId());
            if (input != null && input.inventory == event.getView().getTopInventory()) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareAnvil(@NotNull PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        InputSession input = inputs.get(player.getUniqueId());
        if (input == null || input.inventory != event.getInventory()) return;
        event.getView().setRepairCost(0);
        event.getView().setMaximumRepairCost(0);
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        InputSession input = inputs.get(player.getUniqueId());
        if (input == null || input.inventory != event.getInventory()) return;
        inputs.remove(player.getUniqueId());
        input.inventory.clear();
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (input.teamName == null) openOverview(player, 0);
            else openTeam(player, input.teamName, 0);
        });
    }

    private void handleMenuClick(@NotNull Player player, @NotNull MenuHolder holder, int slot) {
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        switch (holder.screen) {
            case OVERVIEW -> handleOverviewClick(player, holder, slot);
            case TEAM -> handleTeamClick(player, holder, slot);
            case ADD_PLAYER -> handleAddPlayerClick(player, holder, slot);
            case KNOWN_PLAYER -> handleKnownPlayerClick(player, holder, slot);
            case QUICK_PLAYER -> handleQuickPlayerClick(player, holder, slot);
            case TARGET_TEAM -> handleTargetTeamClick(player, holder, slot);
            case COLOR -> handleColorClick(player, holder, slot);
            case CONFIRM -> handleConfirmationClick(player, holder, slot);
        }
    }

    private void handleOverviewClick(@NotNull Player player, @NotNull MenuHolder holder, int slot) {
        String team = holder.targets.get(slot);
        if (team != null) {
            click(player);
            openTeam(player, team, 0);
            return;
        }
        if (slot == CREATE_SLOT) openTextInput(player, InputPurpose.CREATE_TEAM, null);
        else if (slot == REFRESH_SLOT) openOverview(player, holder.page);
        else if (slot == PREVIOUS_SLOT && holder.page > 0) openOverview(player, holder.page - 1);
        else if (slot == NEXT_SLOT) openOverview(player, holder.page + 1);
        else if (slot == TELEPORT_ALL_SLOT)
            openConfirmation(player, Confirmation.TELEPORT_ALL, null, null, null, holder.page);
        else if (slot == QUICK_ASSIGN_SLOT) openQuickPlayers(player, 0);
    }

    private void handleTeamClick(@NotNull Player player, @NotNull MenuHolder holder, int slot) {
        String member = holder.targets.get(slot);
        if (member != null) {
            openConfirmation(player, Confirmation.REMOVE_MEMBER, holder.teamName, member, null, holder.page);
            return;
        }
        ChampionshipTeam team = plugin.getTeamManager().getTeam(holder.teamName);
        boolean full = team == null || team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS;
        if ((slot == ADD_ONLINE_SLOT || slot == ADD_HISTORY_SLOT) && full) {
            Utils.sendAdminError(player, team == null ? GuiConfig.text("teams.menus.shared.copy.team-not-found-feedback")
                    : GuiConfig.text("teams.menus.shared.copy.the-number-of-people-in-the-team-has-reached-the-upper-limit") + CCConfig.TEAM_MAX_MEMBERS);
            openTeam(player, holder.teamName, holder.page);
        } else if (slot == ADD_ONLINE_SLOT) openAddPlayer(player, holder.teamName, 0);
        else if (slot == ADD_HISTORY_SLOT) openKnownPlayers(player, holder.teamName, 0);
        else if (slot == REFRESH_SLOT) openTeam(player, holder.teamName, holder.page);
        else if (slot == PREVIOUS_SLOT && holder.page > 0) openTeam(player, holder.teamName, holder.page - 1);
        else if (slot == PREVIOUS_SLOT) openOverview(player, 0);
        else if (slot == NEXT_SLOT) openTeam(player, holder.teamName, holder.page + 1);
        else if (slot == TELEPORT_TEAM_SLOT) teleportTeam(player, holder.teamName);
        else if (slot == DELETE_TEAM_SLOT)
            openConfirmation(player, Confirmation.DELETE_TEAM, holder.teamName, null, null, holder.page);
    }

    private void handleAddPlayerClick(@NotNull Player player, @NotNull MenuHolder holder, int slot) {
        String member = holder.targets.get(slot);
        if (member != null) {
            addMember(player, holder.teamName, member, true, holder.page);
            return;
        }
        if (slot == BACK_SLOT) openTeam(player, holder.teamName, 0);
        else if (slot == PREVIOUS_SLOT && holder.page > 0) openAddPlayer(player, holder.teamName, holder.page - 1);
        else if (slot == NEXT_SLOT) openAddPlayer(player, holder.teamName, holder.page + 1);
    }

    private void handleKnownPlayerClick(@NotNull Player player, @NotNull MenuHolder holder, int slot) {
        String member = holder.targets.get(slot);
        if (member != null) {
            addKnownMember(player, holder.teamName, member, holder.page);
            return;
        }
        if (slot == MANUAL_INPUT_SLOT) openTextInput(player, InputPurpose.ADD_MEMBER, holder.teamName);
        else if (slot == HISTORY_BACK_SLOT) openTeam(player, holder.teamName, 0);
        else if (slot == PREVIOUS_SLOT && holder.page > 0) openKnownPlayers(player, holder.teamName, holder.page - 1);
        else if (slot == NEXT_SLOT) openKnownPlayers(player, holder.teamName, holder.page + 1);
    }

    private void handleQuickPlayerClick(@NotNull Player player, @NotNull MenuHolder holder, int slot) {
        UUID selectedUuid = holder.playerTargets.get(slot);
        String selectedName = holder.targets.get(slot);
        if (selectedUuid != null && selectedName != null) {
            openTargetTeams(player, selectedUuid, selectedName, holder.page);
            return;
        }
        if (slot == BACK_SLOT) openOverview(player, 0);
        else if (slot == PREVIOUS_SLOT && holder.page > 0) openQuickPlayers(player, holder.page - 1);
        else if (slot == NEXT_SLOT) openQuickPlayers(player, holder.page + 1);
    }

    private void handleTargetTeamClick(@NotNull Player player, @NotNull MenuHolder holder, int slot) {
        if (slot == BACK_SLOT) {
            openQuickPlayers(player, holder.page);
            return;
        }
        String targetTeam = holder.targets.get(slot);
        if (targetTeam == null) return;
        ChampionshipTeam current = plugin.getTeamManager().getTeamByPlayer(holder.selectedPlayerUuid);
        if (current == null) {
            moveMember(player, holder.selectedPlayerUuid, holder.memberName, targetTeam, holder.page);
        } else {
            openConfirmation(player, Confirmation.MOVE_MEMBER, targetTeam, holder.memberName,
                    holder.selectedPlayerUuid, holder.page);
        }
    }

    private void handleColorClick(@NotNull Player player, @NotNull MenuHolder holder, int slot) {
        String selectedColor = holder.targets.get(slot);
        if (selectedColor != null) {
            if (plugin.getTeamManager().getTeam(holder.teamName) != null) {
                Utils.sendAdminError(player, GuiConfig.text("teams.menus.shared.copy.team-name-already-exists") + holder.teamName);
                openOverview(player, 0);
                return;
            }
            boolean colorUsed = plugin.getTeamManager().getTeamList().stream()
                    .anyMatch(team -> team.getColorName().equalsIgnoreCase(selectedColor));
            if (colorUsed) {
                Utils.sendAdminError(player, GuiConfig.text("teams.menus.shared.copy.this-color-has-just-been-taken-by-another-team-please-choose-again"));
                openColorPicker(player, holder.teamName);
                return;
            }
            plugin.getTeamManager().addTeam(holder.teamName, selectedColor, COLOR_HEX.get(selectedColor))
                    .thenAccept(created -> {
                        if (!player.isOnline()) return;
                        if (created) {
                            Utils.sendAdminSuccess(player, GuiConfig.text("teams.menus.shared.copy.team-created") + COLOR_HEX.get(selectedColor) + holder.teamName);
                            success(player);
                            openTeam(player, holder.teamName, 0);
                        } else {
                            Utils.sendAdminError(player, GuiConfig.text("teams.menus.shared.copy.failed-to-create-team-name-or-color-may-already-be-taken"));
                            openOverview(player, 0);
                        }
                    });
        } else if (slot == 31) openOverview(player, 0);
    }

    private void handleConfirmationClick(@NotNull Player player, @NotNull MenuHolder holder, int slot) {
        if (slot == CANCEL_SLOT) {
            returnFromConfirmation(player, holder);
            return;
        }
        if (slot != CONFIRM_SLOT) return;
        switch (holder.confirmation) {
            case DELETE_TEAM -> {
                if (plugin.getTeamManager().getTeam(holder.teamName) == null) {
                    Utils.sendAdminError(player, GuiConfig.text("teams.menus.shared.copy.the-team-no-longer-exists") + holder.teamName);
                } else {
                    plugin.getTeamManager().deleteTeam(holder.teamName).thenAccept(result -> {
                        if (!player.isOnline()) return;
                        if (result == ink.ziip.championshipscore.api.team.TeamManager.TeamDeletionResult.DELETED) {
                            Utils.sendAdminSuccess(player, GuiConfig.text("teams.menus.shared.copy.team-deleted") + holder.teamName);
                            success(player);
                        } else {
                            Utils.sendAdminError(player, GuiConfig.text("teams.menus.shared.copy.unable-to-delete-team-team-may-be-in-game"));
                        }
                        openOverview(player, 0);
                    });
                    return;
                }
                openOverview(player, 0);
            }
            case REMOVE_MEMBER -> {
                plugin.getTeamManager().deleteTeamMember(holder.memberName, holder.teamName).thenAccept(deleted -> {
                    if (!player.isOnline()) return;
                    if (deleted) {
                        Utils.sendAdminSuccess(player, GuiConfig.text("teams.menus.shared.copy.already-from") + holder.teamName + GuiConfig.text("teams.menus.shared.copy.remove-player") + holder.memberName);
                        success(player);
                    } else {
                        Utils.sendAdminError(player, GuiConfig.text("teams.menus.shared.copy.removal-failed-team-or-member-status-may-have-changed"));
                    }
                    openTeam(player, holder.teamName, holder.page);
                });
                return;
            }
            case TELEPORT_ALL -> {
                int players = 0;
                for (ChampionshipTeam team : plugin.getTeamManager().getTeamList()) {
                    players += team.getOnlinePlayers().size();
                    team.teleportAllPlayers(player.getLocation());
                }
                Utils.sendAdminSuccess(player, GuiConfig.text("teams.menus.shared.copy.all-teams-have-been") + players + GuiConfig.text("teams.menus.shared.copy.online-members-teleported-to-current-location"));
                success(player);
                openOverview(player, holder.page);
            }
            case MOVE_MEMBER -> moveMember(player, holder.selectedPlayerUuid, holder.memberName,
                    holder.teamName, holder.page);
        }
    }

    private void submitInput(@NotNull Player player, @NotNull InputSession input, @NotNull AnvilView view) {
        String renameText = view.getRenameText();
        String text = renameText == null ? "" : renameText.trim();
        if (input.purpose == InputPurpose.CREATE_TEAM) {
            if (text.isBlank()) {
                Utils.sendAdminError(player, GuiConfig.text("teams.menus.shared.copy.team-name-cannot-be-empty"));
                return;
            }
            if (text.length() > 64 || text.chars().anyMatch(Character::isISOControl)) {
                Utils.sendAdminError(player, GuiConfig.text("teams.menus.shared.copy.team-names-cannot-exceed-64-characters-and-cannot-contain-control-characters"));
                return;
            }
            boolean exists = plugin.getTeamManager().getTeamList().stream()
                    .anyMatch(team -> team.getName().equalsIgnoreCase(text));
            if (exists) {
                Utils.sendAdminError(player, GuiConfig.text("teams.menus.shared.copy.team-name-already-exists") + text);
                return;
            }
            finishInput(player, input);
            openColorPicker(player, text);
            return;
        }

        if (!text.matches("[A-Za-z0-9_]{1,16}")) {
            Utils.sendAdminError(player, GuiConfig.text("teams.menus.shared.copy.please-enter-a-valid-minecraft-player-name-1-16-letters-numbers-or-underscores"));
            return;
        }
        String teamName = input.teamName;
        finishInput(player, input);
        addMember(player, teamName, text, false, 0);
    }

    private void finishInput(@NotNull Player player, @NotNull InputSession input) {
        inputs.remove(player.getUniqueId(), input);
        input.inventory.clear();
    }

    private void addMember(@NotNull Player admin, @NotNull String teamName, @NotNull String memberName,
                           boolean keepSelectorOpen, int selectorPage) {
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            Utils.sendAdminError(admin, GuiConfig.text("teams.menus.shared.copy.the-team-no-longer-exists") + teamName);
            openOverview(admin, 0);
            return;
        }
        if (team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) {
            Utils.sendAdminError(admin, GuiConfig.text("teams.menus.shared.copy.the-number-of-people-in-the-team-has-reached-the-upper-limit") + CCConfig.TEAM_MAX_MEMBERS);
        } else {
            plugin.getTeamManager().addTeamMember(memberName, team).thenAccept(added -> {
                if (!admin.isOnline()) return;
                if (added) {
                    Utils.sendAdminSuccess(admin, GuiConfig.text("teams.menus.shared.copy.player-has-been") + memberName + GuiConfig.text("teams.menus.shared.copy.join") + team.getColorCode() + team.getName());
                    success(admin);
                } else {
                    Utils.sendAdminError(admin, GuiConfig.text("teams.menus.shared.copy.add-member-identity-conflict"));
                }
                if (keepSelectorOpen && team.getMembers().size() < CCConfig.TEAM_MAX_MEMBERS)
                    openAddPlayer(admin, teamName, added ? selectorPage : 0);
                else
                    openTeam(admin, teamName, 0);
            });
            return;
        }
        openTeam(admin, teamName, 0);
    }

    private void addKnownMember(@NotNull Player admin, @NotNull String teamName,
                                @NotNull String memberName, int selectorPage) {
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            openOverview(admin, 0);
            return;
        }
        if (team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) {
            Utils.sendAdminError(admin, GuiConfig.text("teams.menus.shared.copy.the-number-of-people-in-the-team-has-reached-the-upper-limit") + CCConfig.TEAM_MAX_MEMBERS);
            openTeam(admin, teamName, 0);
        } else {
            plugin.getTeamManager().addTeamMember(memberName, team).thenAccept(added -> {
                if (!admin.isOnline()) return;
                if (added) {
                    Utils.sendAdminSuccess(admin, GuiConfig.text("teams.menus.shared.copy.players-have-been-taken-offline") + memberName + GuiConfig.text("teams.menus.shared.copy.join")
                            + team.getColorCode() + team.getName());
                    success(admin);
                    if (team.getMembers().size() < CCConfig.TEAM_MAX_MEMBERS) openKnownPlayers(admin, teamName, selectorPage);
                    else openTeam(admin, teamName, 0);
                } else {
                    Utils.sendAdminError(admin, GuiConfig.text("teams.menus.shared.copy.failed-to-add-player-may-have-just-been-split-or-there-is-an-identity-conflict"));
                    openKnownPlayers(admin, teamName, selectorPage);
                }
            });
        }
    }

    private void moveMember(@NotNull Player admin, @NotNull UUID uuid, @NotNull String memberName,
                            @NotNull String targetTeamName, int returnPage) {
        ChampionshipTeam target = plugin.getTeamManager().getTeam(targetTeamName);
        if (target == null) {
            Utils.sendAdminError(admin, GuiConfig.text("teams.menus.shared.copy.the-target-team-no-longer-exists") + targetTeamName);
            openQuickPlayers(admin, returnPage);
            return;
        }
        plugin.getTeamManager().moveTeamMember(uuid, memberName, target).thenAccept(result -> {
            if (!admin.isOnline()) return;
            switch (result) {
            case SUCCESS -> {
                Utils.sendAdminSuccess(admin, GuiConfig.text("teams.menus.shared.copy.player-has-been") + memberName + GuiConfig.text("teams.menus.shared.copy.move-to-team-infix")
                        + target.getColorCode() + target.getName());
                success(admin);
                openQuickPlayers(admin, returnPage);
            }
            case SAME_TEAM -> {
                Utils.sendAdminError(admin, GuiConfig.text("teams.menus.shared.copy.the-player-is-already-on-the-team"));
                openTargetTeams(admin, uuid, memberName, returnPage);
            }
            case TARGET_FULL -> {
                Utils.sendAdminError(admin, GuiConfig.text("teams.menus.shared.copy.the-target-team-is-full"));
                openTargetTeams(admin, uuid, memberName, returnPage);
            }
            case TEAM_ACTIVE -> {
                Utils.sendAdminError(admin, GuiConfig.text("teams.menus.shared.copy.the-player-s-current-team-or-target-team-is-currently-in-the-game-cannot-change-teams"));
                openTargetTeams(admin, uuid, memberName, returnPage);
            }
            case INVALID_PLAYER, FAILED -> {
                Utils.sendAdminError(admin, GuiConfig.text("teams.menus.shared.copy.team-transfer-failed-database-or-player-status-may-have-changed"));
                openTargetTeams(admin, uuid, memberName, returnPage);
            }
            }
        });
    }

    private void teleportTeam(@NotNull Player admin, @NotNull String teamName) {
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            openOverview(admin, 0);
            return;
        }
        int online = team.getOnlinePlayers().size();
        team.teleportAllPlayers(admin.getLocation());
        Utils.sendAdminSuccess(admin, GuiConfig.text("teams.menus.shared.copy.already") + team.getColorCode() + team.getName()
                + GuiConfig.text("teams.menus.shared.copy.of") + online + GuiConfig.text("teams.menus.shared.copy.online-members-teleported-to-current-location"));
        success(admin);
        openTeam(admin, teamName, 0);
    }

    private void returnFromConfirmation(@NotNull Player player, @NotNull MenuHolder holder) {
        if (holder.confirmation == Confirmation.TELEPORT_ALL) openOverview(player, holder.page);
        else if (holder.confirmation == Confirmation.MOVE_MEMBER)
            openTargetTeams(player, holder.selectedPlayerUuid, holder.memberName, holder.page);
        else openTeam(player, holder.teamName, holder.page);
    }

    private List<ChampionshipTeam> sortedTeams() {
        return plugin.getTeamManager().getTeamList().stream()
                .sorted(Comparator.comparing(ChampionshipTeam::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<MemberView> members(@NotNull ChampionshipTeam team) {
        return team.getTeamMemberEntries().stream().map(entry -> {
                    Player online = Bukkit.getPlayer(entry.getUuid());
                    String name = online == null ? entry.getUsername() : online.getName();
                    return new MemberView(entry.getUuid(), name, online != null);
                })
                .sorted(Comparator.comparing(MemberView::online).reversed()
                        .thenComparing(MemberView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private ItemStack teamItem(@NotNull ChampionshipTeam team) {
        int online = team.getOnlinePlayers().size();
        int members = team.getMembers().size();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(GuiConfig.text("teams.menus.shared.copy.online"), NamedTextColor.GRAY)
                .append(Component.text(online + "/" + members, online > 0 ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
        lore.add(Component.text(GuiConfig.text("teams.menus.shared.copy.color"), NamedTextColor.GRAY)
                .append(Component.text(COLOR_LABELS.getOrDefault(team.getColorName().toLowerCase(Locale.ROOT), team.getColorName()), teamColor(team))));
        lore.add(Component.text(GuiConfig.text("teams.menus.shared.copy.internal-id"), NamedTextColor.GRAY).append(Component.text(team.getId(), NamedTextColor.WHITE)));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.text("teams.menus.shared.copy.click-to-manage-members-and-teams"), NamedTextColor.YELLOW));
        return item(material(team.getColorName() + "_WOOL", Material.WHITE_WOOL),
                Component.text(team.getName(), teamColor(team)).decorate(TextDecoration.BOLD), lore, false);
    }

    private ItemStack memberItem(@NotNull MemberView member) {
        NamedTextColor status = member.online ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        return playerHead(member.uuid, member.name,
                Component.text(member.name, status).decorate(TextDecoration.BOLD),
                List.of(Component.text(member.online ? GuiConfig.text("teams.menus.shared.copy.online-status") : GuiConfig.text("teams.menus.shared.copy.offline"), status),
                        Component.empty(), Component.text(GuiConfig.text("teams.menus.shared.copy.click-to-remove-the-team"), NamedTextColor.RED)));
    }

    private static ItemStack playerHead(@NotNull UUID uuid, @NotNull String profileName,
                                        @NotNull Component name, @NotNull List<Component> lore) {
        ItemStack stack = ink.ziip.championshipscore.api.gui.GuiMenu.playerHead(uuid, name, lore, false);
        if (stack.getItemMeta() instanceof SkullMeta skull) {
            skull.setPlayerProfile(Bukkit.createProfile(uuid, profileName));
            stack.setItemMeta(skull);
        }
        return stack;
    }

    private static List<Component> confirmationLore(@NotNull Confirmation action, @Nullable String teamName) {
        return switch (action) {
            case DELETE_TEAM -> List.of(
                    Component.text(GuiConfig.text("teams.menus.shared.copy.the-team-and-all-member-relationships-will-be-permanently-deleted"), NamedTextColor.GRAY),
                    Component.text(GuiConfig.text("teams.menus.shared.copy.teams-currently-participating-in-the-game-cannot-be-deleted"), NamedTextColor.DARK_GRAY));
            case REMOVE_MEMBER -> List.of(Component.text(GuiConfig.text("teams.menus.shared.copy.players-will-immediately-lose-their-membership-in-the-team"), NamedTextColor.GRAY));
            case TELEPORT_ALL -> List.of(
                    Component.text(GuiConfig.text("teams.menus.shared.copy.all-online-members-of-the-team-will-arrive-at-your-location"), NamedTextColor.GRAY),
                    Component.text(GuiConfig.text("teams.menus.shared.copy.current-venue") + (teamName == null ? GuiConfig.text("teams.menus.shared.copy.admin-location") : teamName), NamedTextColor.DARK_GRAY));
            case MOVE_MEMBER -> List.of(
                    Component.text(GuiConfig.text("teams.menus.shared.copy.old-team-relationships-will-be-replaced-atomically-in-a-database-transaction"), NamedTextColor.GRAY),
                    Component.text(GuiConfig.text("teams.menus.shared.copy.operations-will-be-refused-while-either-team-is-playing"), NamedTextColor.DARK_GRAY));
        };
    }

    private static void fillFooter(@NotNull Inventory inventory) {
        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int slot = 36; slot < inventory.getSize(); slot++) inventory.setItem(slot, border);
    }

    private static void fillFooter(@NotNull Inventory inventory, @NotNull String menuPath) {
        ItemStack border = configured(menuPath, "border", null, Map.of(),
                item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false));
        for (int slot : GuiConfig.slots(menuPath + ".layout.border",
                List.of(36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53)))
            if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, border);
    }

    private static List<Integer> contentSlots() {
        List<Integer> slots = new ArrayList<>(PAGE_SIZE);
        for (int slot = 0; slot < PAGE_SIZE; slot++) slots.add(slot);
        return slots;
    }

    private static ItemStack configured(@NotNull String menuPath, @NotNull String item,
                                        @Nullable String state, @NotNull Map<String, ?> placeholders,
                                        @NotNull ItemStack fallback) {
        return ConfiguredGui.item(menuPath + ".items." + item, state, placeholders, fallback);
    }

    private static ItemStack pageItem(int page, int pages, int count, @NotNull String unit) {
        return item(Material.PAPER,
                Component.text(GuiConfig.text("teams.menus.shared.copy.ordinal-prefix") + (page + 1) + "/" + pages + GuiConfig.text("teams.menus.shared.copy.page-suffix"), NamedTextColor.AQUA),
                List.of(Component.text(GuiConfig.text("teams.menus.shared.copy.total-prefix") + count + " " + unit, NamedTextColor.GRAY)), false);
    }

    private static ItemStack navigationItem(@NotNull String label) {
        return item(Material.ARROW, Component.text(label, NamedTextColor.WHITE), List.of(), false);
    }

    private static ItemStack closeItem() {
        return item(Material.BARRIER, Component.text(GuiConfig.text("teams.menus.shared.copy.close"), NamedTextColor.RED), List.of(), false);
    }

    private static ItemStack item(@NotNull Material material, @NotNull Component name,
                                  @NotNull List<Component> lore, boolean glint) {
        return ink.ziip.championshipscore.api.gui.GuiMenu.item(material, name, lore, glint);
    }

    private static Component title(@NotNull String text, @NotNull TextColor color) {
        return Component.text(text, color).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false);
    }

    private static Component teamTitle(@NotNull ChampionshipTeam team, @NotNull String suffix) {
        return Component.text(team.getName(), teamColor(team)).append(Component.text(GuiConfig.text("common.separator") + suffix, NamedTextColor.WHITE))
                .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false);
    }

    private static TextColor teamColor(@NotNull ChampionshipTeam team) {
        TextColor hex = TextColor.fromHexString(team.getColorCode());
        return hex == null ? Utils.toNamedTextColor(team.getColorName()) : hex;
    }

    private static TextColor color(@NotNull String colorName) {
        TextColor hex = TextColor.fromHexString(COLOR_HEX.get(colorName));
        return hex == null ? NamedTextColor.WHITE : hex;
    }

    private static Material material(@NotNull String name, @NotNull Material fallback) {
        Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private static int pageCount(int entries) {
        return Math.max(1, (entries + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static void click(@NotNull Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.1F);
    }

    private static void success(@NotNull Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.65F, 1.25F);
    }

    private static Map<String, String> createColorHex() {
        Map<String, String> colors = new LinkedHashMap<>();
        colors.put("white", "#F9FFFE");
        colors.put("orange", "#F9801D");
        colors.put("magenta", "#C74EBD");
        colors.put("light_blue", "#3AB3DA");
        colors.put("yellow", "#FED83D");
        colors.put("lime", "#80C71F");
        colors.put("pink", "#F38BAA");
        colors.put("gray", "#474F52");
        colors.put("light_gray", "#9D9D97");
        colors.put("cyan", "#169C9C");
        colors.put("purple", "#8932B8");
        colors.put("blue", "#3C44AA");
        colors.put("brown", "#835432");
        colors.put("green", "#5E7C16");
        colors.put("red", "#B02E26");
        colors.put("black", "#1D1D21");
        return Map.copyOf(colors);
    }

    private static Map<String, String> createColorLabels() {
        return Map.ofEntries(
                Map.entry("white", GuiConfig.text("teams.menus.shared.copy.white")), Map.entry("orange", GuiConfig.text("teams.menus.shared.copy.orange")), Map.entry("magenta", GuiConfig.text("teams.menus.shared.copy.magenta")),
                Map.entry("light_blue", GuiConfig.text("teams.menus.shared.copy.light-blue")), Map.entry("yellow", GuiConfig.text("teams.menus.shared.copy.yellow")), Map.entry("lime", GuiConfig.text("teams.menus.shared.copy.yellow-green")),
                Map.entry("pink", GuiConfig.text("teams.menus.shared.copy.pink")), Map.entry("gray", GuiConfig.text("teams.menus.shared.copy.gray")), Map.entry("light_gray", GuiConfig.text("teams.menus.shared.copy.light-gray")),
                Map.entry("cyan", GuiConfig.text("teams.menus.shared.copy.cyan")), Map.entry("purple", GuiConfig.text("teams.menus.shared.copy.purple")), Map.entry("blue", GuiConfig.text("teams.menus.shared.copy.blue")),
                Map.entry("brown", GuiConfig.text("teams.menus.shared.copy.brown")), Map.entry("green", GuiConfig.text("teams.menus.shared.copy.green")), Map.entry("red", GuiConfig.text("teams.menus.shared.copy.red")),
                Map.entry("black", GuiConfig.text("teams.menus.shared.copy.black")));
    }

    private enum Screen { OVERVIEW, TEAM, ADD_PLAYER, KNOWN_PLAYER, QUICK_PLAYER, TARGET_TEAM, COLOR, CONFIRM }
    private enum Confirmation { DELETE_TEAM, REMOVE_MEMBER, TELEPORT_ALL, MOVE_MEMBER }
    private enum InputPurpose { CREATE_TEAM, ADD_MEMBER }

    private record MemberView(UUID uuid, String name, boolean online) {
    }

    private record InputSession(InputPurpose purpose, String teamName, AnvilInventory inventory) {
    }

    private static final class MenuHolder implements InventoryHolder {
        private final UUID viewer;
        private final Screen screen;
        private final String teamName;
        private final int page;
        private final Map<Integer, String> targets = new HashMap<>();
        private final Map<Integer, UUID> playerTargets = new HashMap<>();
        private Inventory inventory;
        private Confirmation confirmation;
        private String memberName;
        private UUID selectedPlayerUuid;

        private MenuHolder(UUID viewer, Screen screen, String teamName, int page) {
            this.viewer = viewer;
            this.screen = screen;
            this.teamName = teamName;
            this.page = page;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}

package ink.ziip.championshipscore.api.team.gui;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

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
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                title(GuiConfig.text("teams.management-menu.team-management"), NamedTextColor.GOLD));
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
        for (int index = from; index < to; index++) {
            ChampionshipTeam team = teams.get(index);
            int slot = index - from;
            inventory.setItem(slot, teamItem(team));
            holder.targets.put(slot, team.getName());
        }
        if (teams.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE, Component.text(GuiConfig.text("teams.management-menu.no-team-yet"), NamedTextColor.GRAY),
                    List.of(Component.text(GuiConfig.text("teams.management-menu.click-on-the-lower-left-corner-to-create-the-first-team"), NamedTextColor.DARK_GRAY)), false));
        }

        fillFooter(inventory);
        inventory.setItem(CREATE_SLOT, item(Material.EMERALD,
                Component.text(GuiConfig.text("teams.management-menu.create-a-team"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("teams.management-menu.enter-internal-name-then-select-team-color"), NamedTextColor.GRAY)), false));
        inventory.setItem(QUICK_ASSIGN_SLOT, item(Material.PLAYER_HEAD,
                Component.text(GuiConfig.text("teams.management-menu.quick-team-transfer"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("teams.management-menu.select-online-players-then-select-target-team"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("teams.management-menu.players-who-already-have-a-team-will-ask-for-a-second-confirmation"), NamedTextColor.DARK_GRAY)), false));
        inventory.setItem(REFRESH_SLOT, item(Material.SUNFLOWER,
                Component.text(GuiConfig.text("teams.management-menu.refresh"), NamedTextColor.YELLOW), List.of(), false));
        inventory.setItem(PAGE_SLOT, pageItem(holder.page, pages, teams.size(), GuiConfig.text("teams.management-menu.team-count-suffix")));
        if (holder.page > 0) inventory.setItem(PREVIOUS_SLOT, navigationItem(GuiConfig.text("teams.management-menu.previous-page")));
        if (holder.page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem(GuiConfig.text("teams.management-menu.next-page")));
        inventory.setItem(TELEPORT_ALL_SLOT, item(Material.ENDER_EYE,
                Component.text(GuiConfig.text("teams.management-menu.teleport-all-teams-here"), NamedTextColor.LIGHT_PURPLE),
                List.of(Component.text(GuiConfig.text("teams.management-menu.only-transfer-online-members"), NamedTextColor.GRAY),
                        Component.empty(), Component.text(GuiConfig.text("teams.management-menu.confirmation-required-after-clicking"), NamedTextColor.YELLOW)), false));
        inventory.setItem(CLOSE_SLOT, closeItem());
    }

    private void openTeam(@NotNull Player player, @NotNull String teamName, int requestedPage) {
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            Utils.sendAdminError(player, GuiConfig.text("teams.management-menu.the-team-no-longer-exists") + teamName);
            openOverview(player, 0);
            return;
        }

        List<MemberView> members = members(team);
        int pages = pageCount(members.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.TEAM, team.getName(), page);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                teamTitle(team, GuiConfig.text("teams.management-menu.member-management")));
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
        for (int index = from; index < to; index++) {
            MemberView member = members.get(index);
            int slot = index - from;
            inventory.setItem(slot, memberItem(member));
            holder.targets.put(slot, member.name());
        }
        if (members.isEmpty()) {
            inventory.setItem(22, item(Material.PLAYER_HEAD,
                    Component.text(GuiConfig.text("teams.management-menu.there-are-no-members-in-the-team-yet"), NamedTextColor.GRAY),
                    List.of(Component.text(GuiConfig.text("teams.management-menu.add-online-players-from-the-lower-left-corner-or-enter-player-name"), NamedTextColor.DARK_GRAY)), false));
        }

        fillFooter(inventory);
        boolean full = members.size() >= CCConfig.TEAM_MAX_MEMBERS;
        if (full) {
            inventory.setItem(ADD_ONLINE_SLOT, item(Material.RED_DYE,
                    Component.text(GuiConfig.text("teams.management-menu.the-queue-is-full"), NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    List.of(Component.text(members.size() + "/" + CCConfig.TEAM_MAX_MEMBERS + GuiConfig.text("teams.management-menu.members"), NamedTextColor.GRAY),
                            Component.text(GuiConfig.text("teams.management-menu.remove-members-before-you-can-continue-adding-them"), NamedTextColor.DARK_GRAY)), false));
        } else {
            inventory.setItem(ADD_ONLINE_SLOT, item(Material.LIME_DYE,
                    Component.text(GuiConfig.text("teams.management-menu.add-online-player"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    List.of(Component.text(GuiConfig.text("teams.management-menu.select-from-players-currently-online-and-not-yet-in-a-queue"), NamedTextColor.GRAY)), false));
            inventory.setItem(ADD_HISTORY_SLOT, item(Material.BOOK,
                    Component.text(GuiConfig.text("teams.management-menu.history-offline-players"), NamedTextColor.AQUA),
                    List.of(Component.text(GuiConfig.text("teams.management-menu.select-from-logged-offline-players-who-have-not-yet-been-queued"), NamedTextColor.GRAY),
                            Component.text(GuiConfig.text("teams.management-menu.you-can-also-enter-the-player-name-manually-on-the-next-page"), NamedTextColor.DARK_GRAY)), false));
        }
        inventory.setItem(PREVIOUS_SLOT, holder.page > 0 ? navigationItem(GuiConfig.text("teams.management-menu.previous-page"))
                : item(Material.ARROW, Component.text(GuiConfig.text("teams.management-menu.return-to-team-list"), NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(REFRESH_SLOT, item(Material.SUNFLOWER,
                Component.text(GuiConfig.text("teams.management-menu.refresh"), NamedTextColor.YELLOW), List.of(), false));
        inventory.setItem(PAGE_SLOT, pageItem(holder.page, pages, members.size(), GuiConfig.text("teams.management-menu.page-member-count-suffix")));
        if (holder.page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem(GuiConfig.text("teams.management-menu.next-page")));
        inventory.setItem(TELEPORT_TEAM_SLOT, item(Material.ENDER_PEARL,
                Component.text(GuiConfig.text("teams.management-menu.teleport-this-team-here"), NamedTextColor.LIGHT_PURPLE),
                List.of(Component.text(team.getOnlinePlayers().size() + GuiConfig.text("teams.management-menu.online-members-will-be-transferred"), NamedTextColor.GRAY)), false));
        inventory.setItem(DELETE_TEAM_SLOT, item(Material.TNT,
                Component.text(GuiConfig.text("teams.management-menu.delete-team"), NamedTextColor.RED),
                List.of(Component.text(GuiConfig.text("teams.management-menu.permanently-delete-team-and-membership-relationships"), NamedTextColor.GRAY),
                        Component.empty(), Component.text(GuiConfig.text("teams.management-menu.confirmation-required-after-clicking"), NamedTextColor.YELLOW)), false));
        inventory.setItem(CLOSE_SLOT, closeItem());
    }

    private void openAddPlayer(@NotNull Player player, @NotNull String teamName, int requestedPage) {
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            openOverview(player, 0);
            return;
        }
        if (team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) {
            Utils.sendAdminError(player, GuiConfig.text("teams.management-menu.the-number-of-people-in-the-team-has-reached-the-upper-limit") + CCConfig.TEAM_MAX_MEMBERS);
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
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, teamTitle(team, GuiConfig.text("teams.management-menu.select-new-member")));
        holder.inventory = inventory;
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, candidates.size());
        for (int index = from; index < to; index++) {
            Player candidate = candidates.get(index);
            int slot = index - from;
            inventory.setItem(slot, playerHead(candidate.getUniqueId(), candidate.getName(),
                    Component.text(candidate.getName(), NamedTextColor.GREEN),
                    List.of(Component.text(GuiConfig.text("teams.management-menu.click-to-join") + team.getName(), teamColor(team)))));
            holder.targets.put(slot, candidate.getName());
        }
        if (candidates.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE,
                    Component.text(GuiConfig.text("teams.management-menu.no-online-players-to-add"), NamedTextColor.GRAY),
                    List.of(Component.text(GuiConfig.text("teams.management-menu.can-be-used-after-returning-enter-player-name"), NamedTextColor.DARK_GRAY)), false));
        }
        fillFooter(inventory);
        inventory.setItem(BACK_SLOT, item(Material.ARROW, Component.text(GuiConfig.text("teams.management-menu.return-to-member-management"), NamedTextColor.WHITE), List.of(), false));
        if (page > 0) inventory.setItem(PREVIOUS_SLOT, navigationItem(GuiConfig.text("teams.management-menu.previous-page")));
        inventory.setItem(PAGE_SLOT, pageItem(page, pages, candidates.size(), GuiConfig.text("teams.management-menu.optional-players")));
        if (page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem(GuiConfig.text("teams.management-menu.next-page")));
        inventory.setItem(CLOSE_SLOT, closeItem());
        player.openInventory(inventory);
    }

    private void openColorPicker(@NotNull Player player, @NotNull String newTeamName) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.COLOR, newTeamName, 0);
        Inventory inventory = Bukkit.createInventory(holder, 36, title(GuiConfig.text("teams.management-menu.choose-team-color"), NamedTextColor.GOLD));
        holder.inventory = inventory;
        List<String> colors = List.of(Utils.getColorNames());
        for (int index = 0; index < colors.size(); index++) {
            String color = colors.get(index);
            ChampionshipTeam usedBy = sortedTeams().stream()
                    .filter(team -> team.getColorName().equalsIgnoreCase(color))
                    .findFirst().orElse(null);
            Material material = material(color + "_WOOL", Material.WHITE_WOOL);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(GuiConfig.text("teams.management-menu.internal-color-name") + color, NamedTextColor.GRAY));
            lore.add(Component.text(GuiConfig.text("teams.management-menu.display-color") + COLOR_HEX.get(color), color(color)));
            lore.add(Component.empty());
            if (usedBy == null) lore.add(Component.text(GuiConfig.text("teams.management-menu.click-to-create-a-team"), NamedTextColor.GREEN));
            else lore.add(Component.text(GuiConfig.text("teams.management-menu.has-been") + usedBy.getName() + GuiConfig.text("teams.management-menu.use"), NamedTextColor.RED));
            int slot = COLOR_SLOTS.get(index);
            inventory.setItem(slot, item(usedBy == null ? material : Material.GRAY_DYE,
                    Component.text(COLOR_LABELS.get(color), usedBy == null ? color(color) : NamedTextColor.DARK_GRAY)
                            .decorate(TextDecoration.BOLD), lore, false));
            if (usedBy == null) holder.targets.put(slot, color);
        }
        inventory.setItem(31, item(Material.ARROW, Component.text(GuiConfig.text("teams.management-menu.return-to-team-list"), NamedTextColor.WHITE), List.of(), false));
        player.openInventory(inventory);
    }

    private void openQuickPlayers(@NotNull Player player, int requestedPage) {
        List<? extends Player> players = Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int pages = pageCount(players.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.QUICK_PLAYER, null, page);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, title(GuiConfig.text("teams.management-menu.quickly-adjust-the-team-select-players"), NamedTextColor.AQUA));
        holder.inventory = inventory;
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, players.size());
        for (int index = from; index < to; index++) {
            Player candidate = players.get(index);
            ChampionshipTeam current = plugin.getTeamManager().getTeamByPlayer(candidate);
            List<Component> lore = new ArrayList<>();
            lore.add(current == null
                    ? Component.text(GuiConfig.text("teams.management-menu.not-currently-in-a-team"), NamedTextColor.GRAY)
                    : Component.text(GuiConfig.text("teams.management-menu.current-team"), NamedTextColor.GRAY)
                    .append(Component.text(current.getName(), teamColor(current))));
            lore.add(Component.empty());
            lore.add(Component.text(GuiConfig.text("teams.management-menu.click-to-select-target-team"), NamedTextColor.YELLOW));
            int slot = index - from;
            inventory.setItem(slot, playerHead(candidate.getUniqueId(), candidate.getName(),
                    Component.text(candidate.getName(), NamedTextColor.GREEN).decorate(TextDecoration.BOLD), lore));
            holder.playerTargets.put(slot, candidate.getUniqueId());
            holder.targets.put(slot, candidate.getName());
        }
        fillFooter(inventory);
        inventory.setItem(BACK_SLOT, item(Material.ARROW, Component.text(GuiConfig.text("teams.management-menu.return-to-team-list"), NamedTextColor.WHITE), List.of(), false));
        if (page > 0) inventory.setItem(PREVIOUS_SLOT, navigationItem(GuiConfig.text("teams.management-menu.previous-page")));
        inventory.setItem(PAGE_SLOT, pageItem(page, pages, players.size(), GuiConfig.text("teams.management-menu.online-players")));
        if (page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem(GuiConfig.text("teams.management-menu.next-page")));
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
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, title(GuiConfig.text("teams.management-menu.select") + selectedName + GuiConfig.text("teams.management-menu.team-possessive-suffix"), NamedTextColor.GOLD));
        holder.inventory = inventory;
        for (int index = 0; index < Math.min(PAGE_SIZE, teams.size()); index++) {
            ChampionshipTeam team = teams.get(index);
            boolean same = current != null && current.equals(team);
            boolean full = team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS;
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(team.getMembers().size() + "/" + CCConfig.TEAM_MAX_MEMBERS + GuiConfig.text("teams.management-menu.members"), NamedTextColor.GRAY));
            lore.add(Component.empty());
            if (same) lore.add(Component.text(GuiConfig.text("teams.management-menu.current-membership-team"), NamedTextColor.GREEN));
            else if (full) lore.add(Component.text(GuiConfig.text("teams.management-menu.the-queue-is-full"), NamedTextColor.RED));
            else if (current == null) lore.add(Component.text(GuiConfig.text("teams.management-menu.click-to-join-directly"), NamedTextColor.YELLOW));
            else lore.add(Component.text(GuiConfig.text("teams.management-menu.click-to-confirm-the-team-transfer"), NamedTextColor.YELLOW));
            inventory.setItem(index, item(material(team.getColorName() + "_WOOL", Material.WHITE_WOOL),
                    Component.text(team.getName(), same || full ? NamedTextColor.DARK_GRAY : teamColor(team))
                            .decorate(TextDecoration.BOLD), lore, same));
            if (!same && !full) holder.targets.put(index, team.getName());
        }
        if (teams.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE, Component.text(GuiConfig.text("teams.management-menu.no-teams-available-yet"), NamedTextColor.GRAY), List.of(), false));
        }
        fillFooter(inventory);
        inventory.setItem(BACK_SLOT, item(Material.ARROW, Component.text(GuiConfig.text("teams.management-menu.return-to-online-players"), NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(49, playerHead(selectedUuid, selectedName,
                Component.text(selectedName, NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(current == null ? Component.text(GuiConfig.text("teams.management-menu.not-currently-in-a-team"), NamedTextColor.GRAY)
                        : Component.text(GuiConfig.text("teams.management-menu.current") + current.getName(), teamColor(current)))));
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
            Utils.sendAdminError(player, GuiConfig.text("teams.management-menu.the-number-of-people-in-the-team-has-reached-the-upper-limit") + CCConfig.TEAM_MAX_MEMBERS);
            openTeam(player, teamName, 0);
            return;
        }
        plugin.getPlayerManager().getKnownPlayersAsync().whenComplete((knownPlayers, failure) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    ChampionshipTeam currentTeam = plugin.getTeamManager().getTeam(teamName);
                    if (failure != null || currentTeam == null) {
                        Utils.sendAdminError(player, GuiConfig.text("teams.management-menu.unable-to-read-player-history"));
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
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, teamTitle(team, GuiConfig.text("teams.management-menu.history-offline-players")));
        holder.inventory = inventory;
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, candidates.size());
        for (int index = from; index < to; index++) {
            PlayerEntry candidate = candidates.get(index);
            int slot = index - from;
            inventory.setItem(slot, playerHead(candidate.getUuid(), candidate.getName(),
                    Component.text(candidate.getName(), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                    List.of(Component.text(GuiConfig.text("teams.management-menu.historical-player-currently-offline"), NamedTextColor.DARK_GRAY),
                            Component.empty(), Component.text(GuiConfig.text("teams.management-menu.click-to-join") + team.getName(), teamColor(team)))));
            holder.playerTargets.put(slot, candidate.getUuid());
            holder.targets.put(slot, candidate.getName());
        }
        if (candidates.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE, Component.text(GuiConfig.text("teams.management-menu.no-optional-offline-player-history"), NamedTextColor.GRAY),
                    List.of(Component.text(GuiConfig.text("teams.management-menu.you-can-use-the-lower-left-corner-to-manually-enter-the-player-name"), NamedTextColor.DARK_GRAY)), false));
        }
        fillFooter(inventory);
        inventory.setItem(MANUAL_INPUT_SLOT, item(Material.NAME_TAG, Component.text(GuiConfig.text("teams.management-menu.manually-enter-player-name"), NamedTextColor.AQUA),
                List.of(Component.text(GuiConfig.text("teams.management-menu.only-used-if-the-player-cannot-be-found-in-the-history-list"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("teams.management-menu.please-check-the-spelling-carefully"), NamedTextColor.YELLOW)), false));
        inventory.setItem(HISTORY_BACK_SLOT, item(Material.ARROW, Component.text(GuiConfig.text("teams.management-menu.return-to-member-management"), NamedTextColor.WHITE), List.of(), false));
        if (page > 0) inventory.setItem(PREVIOUS_SLOT, navigationItem(GuiConfig.text("teams.management-menu.previous-page")));
        inventory.setItem(PAGE_SLOT, pageItem(page, pages, candidates.size(), GuiConfig.text("teams.management-menu.offline-players")));
        if (page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem(GuiConfig.text("teams.management-menu.next-page")));
        inventory.setItem(CLOSE_SLOT, closeItem());
        player.openInventory(inventory);
    }

    private void openConfirmation(@NotNull Player player, @NotNull Confirmation action, @Nullable String teamName,
                                  @Nullable String memberName, @Nullable UUID selectedPlayerUuid, int returnPage) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.CONFIRM, teamName, returnPage);
        holder.confirmation = action;
        holder.memberName = memberName;
        holder.selectedPlayerUuid = selectedPlayerUuid;
        Inventory inventory = Bukkit.createInventory(holder, 27, title(GuiConfig.text("teams.management-menu.confirm-action"), NamedTextColor.RED));
        holder.inventory = inventory;
        Component subject = switch (action) {
            case DELETE_TEAM -> Component.text(GuiConfig.text("teams.management-menu.delete-team-confirmation-title") + teamName, NamedTextColor.RED);
            case REMOVE_MEMBER -> Component.text(GuiConfig.text("teams.management-menu.remove-member") + memberName, NamedTextColor.RED);
            case TELEPORT_ALL -> Component.text(GuiConfig.text("teams.management-menu.teleport-all-teams"), NamedTextColor.LIGHT_PURPLE);
            case MOVE_MEMBER -> Component.text(GuiConfig.text("teams.management-menu.will") + memberName + GuiConfig.text("teams.management-menu.adjust-to") + teamName, NamedTextColor.GOLD);
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
                Component.text(GuiConfig.text("teams.management-menu.confirm"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD), List.of(), false));
        inventory.setItem(CANCEL_SLOT, item(Material.RED_CONCRETE,
                Component.text(GuiConfig.text("teams.management-menu.cancel"), NamedTextColor.RED), List.of(), false));
        player.openInventory(inventory);
    }

    private void openTextInput(@NotNull Player player, @NotNull InputPurpose purpose, @Nullable String teamName) {
        String prompt = purpose == InputPurpose.CREATE_TEAM ? GuiConfig.text("teams.management-menu.enter-the-team-s-internal-name") : GuiConfig.text("teams.management-menu.enter-the-name-of-the-player-you-want-to-add");
        InputSession previous = inputs.remove(player.getUniqueId());
        if (previous != null) previous.inventory.clear();
        AnvilView view = MenuType.ANVIL.create(player, title(prompt, NamedTextColor.GOLD));
        player.openInventory(view);
        AnvilInventory inventory = view.getTopInventory();
        InputSession session = new InputSession(purpose, teamName, inventory);
        inputs.put(player.getUniqueId(), session);
        inventory.setFirstItem(item(Material.PAPER, Component.text(prompt, NamedTextColor.YELLOW),
                List.of(Component.text(GuiConfig.text("teams.management-menu.enter-in-the-rename-field-above-then-click-on-the-result-box-on-the-right"), NamedTextColor.GRAY)), false));
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
                Utils.sendAdminError(player, GuiConfig.text("teams.management-menu.you-no-longer-have-permission-to-use-the-team-management-interface"));
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
            Utils.sendAdminError(player, GuiConfig.text("teams.management-menu.you-no-longer-have-permission-to-use-the-team-management-interface"));
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
            Utils.sendAdminError(player, team == null ? GuiConfig.text("teams.management-menu.team-not-found-feedback")
                    : GuiConfig.text("teams.management-menu.the-number-of-people-in-the-team-has-reached-the-upper-limit") + CCConfig.TEAM_MAX_MEMBERS);
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
                Utils.sendAdminError(player, GuiConfig.text("teams.management-menu.team-name-already-exists") + holder.teamName);
                openOverview(player, 0);
                return;
            }
            boolean colorUsed = plugin.getTeamManager().getTeamList().stream()
                    .anyMatch(team -> team.getColorName().equalsIgnoreCase(selectedColor));
            if (colorUsed) {
                Utils.sendAdminError(player, GuiConfig.text("teams.management-menu.this-color-has-just-been-taken-by-another-team-please-choose-again"));
                openColorPicker(player, holder.teamName);
                return;
            }
            plugin.getTeamManager().addTeam(holder.teamName, selectedColor, COLOR_HEX.get(selectedColor))
                    .thenAccept(created -> {
                        if (!player.isOnline()) return;
                        if (created) {
                            Utils.sendAdminSuccess(player, GuiConfig.text("teams.management-menu.team-created") + COLOR_HEX.get(selectedColor) + holder.teamName);
                            success(player);
                            openTeam(player, holder.teamName, 0);
                        } else {
                            Utils.sendAdminError(player, GuiConfig.text("teams.management-menu.failed-to-create-team-name-or-color-may-already-be-taken"));
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
                    Utils.sendAdminError(player, GuiConfig.text("teams.management-menu.the-team-no-longer-exists") + holder.teamName);
                } else {
                    plugin.getTeamManager().deleteTeam(holder.teamName).thenAccept(result -> {
                        if (!player.isOnline()) return;
                        if (result == ink.ziip.championshipscore.api.team.TeamManager.TeamDeletionResult.DELETED) {
                            Utils.sendAdminSuccess(player, GuiConfig.text("teams.management-menu.team-deleted") + holder.teamName);
                            success(player);
                        } else {
                            Utils.sendAdminError(player, GuiConfig.text("teams.management-menu.unable-to-delete-team-team-may-be-in-game"));
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
                        Utils.sendAdminSuccess(player, GuiConfig.text("teams.management-menu.already-from") + holder.teamName + GuiConfig.text("teams.management-menu.remove-player") + holder.memberName);
                        success(player);
                    } else {
                        Utils.sendAdminError(player, GuiConfig.text("teams.management-menu.removal-failed-team-or-member-status-may-have-changed"));
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
                Utils.sendAdminSuccess(player, GuiConfig.text("teams.management-menu.all-teams-have-been") + players + GuiConfig.text("teams.management-menu.online-members-teleported-to-current-location"));
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
                Utils.sendAdminError(player, GuiConfig.text("teams.management-menu.team-name-cannot-be-empty"));
                return;
            }
            if (text.length() > 64 || text.chars().anyMatch(Character::isISOControl)) {
                Utils.sendAdminError(player, GuiConfig.text("teams.management-menu.team-names-cannot-exceed-64-characters-and-cannot-contain-control-characters"));
                return;
            }
            boolean exists = plugin.getTeamManager().getTeamList().stream()
                    .anyMatch(team -> team.getName().equalsIgnoreCase(text));
            if (exists) {
                Utils.sendAdminError(player, GuiConfig.text("teams.management-menu.team-name-already-exists") + text);
                return;
            }
            finishInput(player, input);
            openColorPicker(player, text);
            return;
        }

        if (!text.matches("[A-Za-z0-9_]{1,16}")) {
            Utils.sendAdminError(player, GuiConfig.text("teams.management-menu.please-enter-a-valid-minecraft-player-name-1-16-letters-numbers-or-underscores"));
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
            Utils.sendAdminError(admin, GuiConfig.text("teams.management-menu.the-team-no-longer-exists") + teamName);
            openOverview(admin, 0);
            return;
        }
        if (team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) {
            Utils.sendAdminError(admin, GuiConfig.text("teams.management-menu.the-number-of-people-in-the-team-has-reached-the-upper-limit") + CCConfig.TEAM_MAX_MEMBERS);
        } else {
            plugin.getTeamManager().addTeamMember(memberName, team).thenAccept(added -> {
                if (!admin.isOnline()) return;
                if (added) {
                    Utils.sendAdminSuccess(admin, GuiConfig.text("teams.management-menu.player-has-been") + memberName + GuiConfig.text("teams.management-menu.join") + team.getColorCode() + team.getName());
                    success(admin);
                } else {
                    Utils.sendAdminError(admin, GuiConfig.text("teams.management-menu.add-member-identity-conflict"));
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
            Utils.sendAdminError(admin, GuiConfig.text("teams.management-menu.the-number-of-people-in-the-team-has-reached-the-upper-limit") + CCConfig.TEAM_MAX_MEMBERS);
            openTeam(admin, teamName, 0);
        } else {
            plugin.getTeamManager().addTeamMember(memberName, team).thenAccept(added -> {
                if (!admin.isOnline()) return;
                if (added) {
                    Utils.sendAdminSuccess(admin, GuiConfig.text("teams.management-menu.players-have-been-taken-offline") + memberName + GuiConfig.text("teams.management-menu.join")
                            + team.getColorCode() + team.getName());
                    success(admin);
                    if (team.getMembers().size() < CCConfig.TEAM_MAX_MEMBERS) openKnownPlayers(admin, teamName, selectorPage);
                    else openTeam(admin, teamName, 0);
                } else {
                    Utils.sendAdminError(admin, GuiConfig.text("teams.management-menu.failed-to-add-player-may-have-just-been-split-or-there-is-an-identity-conflict"));
                    openKnownPlayers(admin, teamName, selectorPage);
                }
            });
        }
    }

    private void moveMember(@NotNull Player admin, @NotNull UUID uuid, @NotNull String memberName,
                            @NotNull String targetTeamName, int returnPage) {
        ChampionshipTeam target = plugin.getTeamManager().getTeam(targetTeamName);
        if (target == null) {
            Utils.sendAdminError(admin, GuiConfig.text("teams.management-menu.the-target-team-no-longer-exists") + targetTeamName);
            openQuickPlayers(admin, returnPage);
            return;
        }
        plugin.getTeamManager().moveTeamMember(uuid, memberName, target).thenAccept(result -> {
            if (!admin.isOnline()) return;
            switch (result) {
            case SUCCESS -> {
                Utils.sendAdminSuccess(admin, GuiConfig.text("teams.management-menu.player-has-been") + memberName + GuiConfig.text("teams.management-menu.move-to-team-infix")
                        + target.getColorCode() + target.getName());
                success(admin);
                openQuickPlayers(admin, returnPage);
            }
            case SAME_TEAM -> {
                Utils.sendAdminError(admin, GuiConfig.text("teams.management-menu.the-player-is-already-on-the-team"));
                openTargetTeams(admin, uuid, memberName, returnPage);
            }
            case TARGET_FULL -> {
                Utils.sendAdminError(admin, GuiConfig.text("teams.management-menu.the-target-team-is-full"));
                openTargetTeams(admin, uuid, memberName, returnPage);
            }
            case TEAM_ACTIVE -> {
                Utils.sendAdminError(admin, GuiConfig.text("teams.management-menu.the-player-s-current-team-or-target-team-is-currently-in-the-game-cannot-change-teams"));
                openTargetTeams(admin, uuid, memberName, returnPage);
            }
            case INVALID_PLAYER, FAILED -> {
                Utils.sendAdminError(admin, GuiConfig.text("teams.management-menu.team-transfer-failed-database-or-player-status-may-have-changed"));
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
        Utils.sendAdminSuccess(admin, GuiConfig.text("teams.management-menu.already") + team.getColorCode() + team.getName()
                + GuiConfig.text("teams.management-menu.of") + online + GuiConfig.text("teams.management-menu.online-members-teleported-to-current-location"));
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
        lore.add(Component.text(GuiConfig.text("teams.management-menu.online"), NamedTextColor.GRAY)
                .append(Component.text(online + "/" + members, online > 0 ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
        lore.add(Component.text(GuiConfig.text("teams.management-menu.color"), NamedTextColor.GRAY)
                .append(Component.text(COLOR_LABELS.getOrDefault(team.getColorName().toLowerCase(Locale.ROOT), team.getColorName()), teamColor(team))));
        lore.add(Component.text(GuiConfig.text("teams.management-menu.internal-id"), NamedTextColor.GRAY).append(Component.text(team.getId(), NamedTextColor.WHITE)));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.text("teams.management-menu.click-to-manage-members-and-teams"), NamedTextColor.YELLOW));
        return item(material(team.getColorName() + "_WOOL", Material.WHITE_WOOL),
                Component.text(team.getName(), teamColor(team)).decorate(TextDecoration.BOLD), lore, false);
    }

    private ItemStack memberItem(@NotNull MemberView member) {
        NamedTextColor status = member.online ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        return playerHead(member.uuid, member.name,
                Component.text(member.name, status).decorate(TextDecoration.BOLD),
                List.of(Component.text(member.online ? GuiConfig.text("teams.management-menu.online-status") : GuiConfig.text("teams.management-menu.offline"), status),
                        Component.empty(), Component.text(GuiConfig.text("teams.management-menu.click-to-remove-the-team"), NamedTextColor.RED)));
    }

    private static ItemStack playerHead(@NotNull UUID uuid, @NotNull String profileName,
                                        @NotNull Component name, @NotNull List<Component> lore) {
        ItemStack stack = item(Material.PLAYER_HEAD, name, lore, false);
        if (stack.getItemMeta() instanceof SkullMeta skull) {
            skull.setPlayerProfile(Bukkit.createProfile(uuid, profileName));
            stack.setItemMeta(skull);
        }
        return stack;
    }

    private static List<Component> confirmationLore(@NotNull Confirmation action, @Nullable String teamName) {
        return switch (action) {
            case DELETE_TEAM -> List.of(
                    Component.text(GuiConfig.text("teams.management-menu.the-team-and-all-member-relationships-will-be-permanently-deleted"), NamedTextColor.GRAY),
                    Component.text(GuiConfig.text("teams.management-menu.teams-currently-participating-in-the-game-cannot-be-deleted"), NamedTextColor.DARK_GRAY));
            case REMOVE_MEMBER -> List.of(Component.text(GuiConfig.text("teams.management-menu.players-will-immediately-lose-their-membership-in-the-team"), NamedTextColor.GRAY));
            case TELEPORT_ALL -> List.of(
                    Component.text(GuiConfig.text("teams.management-menu.all-online-members-of-the-team-will-arrive-at-your-location"), NamedTextColor.GRAY),
                    Component.text(GuiConfig.text("teams.management-menu.current-venue") + (teamName == null ? GuiConfig.text("teams.management-menu.admin-location") : teamName), NamedTextColor.DARK_GRAY));
            case MOVE_MEMBER -> List.of(
                    Component.text(GuiConfig.text("teams.management-menu.old-team-relationships-will-be-replaced-atomically-in-a-database-transaction"), NamedTextColor.GRAY),
                    Component.text(GuiConfig.text("teams.management-menu.operations-will-be-refused-while-either-team-is-playing"), NamedTextColor.DARK_GRAY));
        };
    }

    private static void fillFooter(@NotNull Inventory inventory) {
        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int slot = 36; slot < inventory.getSize(); slot++) inventory.setItem(slot, border);
    }

    private static ItemStack pageItem(int page, int pages, int count, @NotNull String unit) {
        return item(Material.PAPER,
                Component.text(GuiConfig.text("teams.management-menu.ordinal-prefix") + (page + 1) + "/" + pages + GuiConfig.text("teams.management-menu.page-suffix"), NamedTextColor.AQUA),
                List.of(Component.text(GuiConfig.text("teams.management-menu.total-prefix") + count + " " + unit, NamedTextColor.GRAY)), false);
    }

    private static ItemStack navigationItem(@NotNull String label) {
        return item(Material.ARROW, Component.text(label, NamedTextColor.WHITE), List.of(), false);
    }

    private static ItemStack closeItem() {
        return item(Material.BARRIER, Component.text(GuiConfig.text("teams.management-menu.close"), NamedTextColor.RED), List.of(), false);
    }

    private static ItemStack item(@NotNull Material material, @NotNull Component name,
                                  @NotNull List<Component> lore, boolean glint) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
            meta.setEnchantmentGlintOverride(glint);
            stack.setItemMeta(meta);
        }
        return stack;
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
                Map.entry("white", GuiConfig.text("teams.management-menu.white")), Map.entry("orange", GuiConfig.text("teams.management-menu.orange")), Map.entry("magenta", GuiConfig.text("teams.management-menu.magenta")),
                Map.entry("light_blue", GuiConfig.text("teams.management-menu.light-blue")), Map.entry("yellow", GuiConfig.text("teams.management-menu.yellow")), Map.entry("lime", GuiConfig.text("teams.management-menu.yellow-green")),
                Map.entry("pink", GuiConfig.text("teams.management-menu.pink")), Map.entry("gray", GuiConfig.text("teams.management-menu.gray")), Map.entry("light_gray", GuiConfig.text("teams.management-menu.light-gray")),
                Map.entry("cyan", GuiConfig.text("teams.management-menu.cyan")), Map.entry("purple", GuiConfig.text("teams.management-menu.purple")), Map.entry("blue", GuiConfig.text("teams.management-menu.blue")),
                Map.entry("brown", GuiConfig.text("teams.management-menu.brown")), Map.entry("green", GuiConfig.text("teams.management-menu.green")), Map.entry("red", GuiConfig.text("teams.management-menu.red")),
                Map.entry("black", GuiConfig.text("teams.management-menu.black")));
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

package ink.ziip.championshipscore.api.team.gui;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.ChampionshipsCore;
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
                title(GuiConfig.text("team-gui-teammanagementmenu.text-001"), NamedTextColor.GOLD));
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
            inventory.setItem(22, item(Material.GRAY_DYE, Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-002"), NamedTextColor.GRAY),
                    List.of(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-003"), NamedTextColor.DARK_GRAY)), false));
        }

        fillFooter(inventory);
        inventory.setItem(CREATE_SLOT, item(Material.EMERALD,
                Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-004"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-005"), NamedTextColor.GRAY)), false));
        inventory.setItem(QUICK_ASSIGN_SLOT, item(Material.PLAYER_HEAD,
                Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-006"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-007"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-008"), NamedTextColor.DARK_GRAY)), false));
        inventory.setItem(REFRESH_SLOT, item(Material.SUNFLOWER,
                Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-009"), NamedTextColor.YELLOW), List.of(), false));
        inventory.setItem(PAGE_SLOT, pageItem(holder.page, pages, teams.size(), GuiConfig.text("team-gui-teammanagementmenu.text-010")));
        if (holder.page > 0) inventory.setItem(PREVIOUS_SLOT, navigationItem(GuiConfig.text("team-gui-teammanagementmenu.text-011")));
        if (holder.page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem(GuiConfig.text("team-gui-teammanagementmenu.text-012")));
        inventory.setItem(TELEPORT_ALL_SLOT, item(Material.ENDER_EYE,
                Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-013"), NamedTextColor.LIGHT_PURPLE),
                List.of(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-014"), NamedTextColor.GRAY),
                        Component.empty(), Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-015"), NamedTextColor.YELLOW)), false));
        inventory.setItem(CLOSE_SLOT, closeItem());
    }

    private void openTeam(@NotNull Player player, @NotNull String teamName, int requestedPage) {
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            Utils.sendAdminError(player, GuiConfig.text("team-gui-teammanagementmenu.text-016") + teamName);
            openOverview(player, 0);
            return;
        }

        List<MemberView> members = members(team);
        int pages = pageCount(members.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.TEAM, team.getName(), page);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                teamTitle(team, GuiConfig.text("team-gui-teammanagementmenu.text-017")));
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
                    Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-018"), NamedTextColor.GRAY),
                    List.of(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-019"), NamedTextColor.DARK_GRAY)), false));
        }

        fillFooter(inventory);
        boolean full = members.size() >= CCConfig.TEAM_MAX_MEMBERS;
        if (full) {
            inventory.setItem(ADD_ONLINE_SLOT, item(Material.RED_DYE,
                    Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-020"), NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    List.of(Component.text(members.size() + "/" + CCConfig.TEAM_MAX_MEMBERS + GuiConfig.text("team-gui-teammanagementmenu.text-021"), NamedTextColor.GRAY),
                            Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-022"), NamedTextColor.DARK_GRAY)), false));
        } else {
            inventory.setItem(ADD_ONLINE_SLOT, item(Material.LIME_DYE,
                    Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-023"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    List.of(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-024"), NamedTextColor.GRAY)), false));
            inventory.setItem(ADD_HISTORY_SLOT, item(Material.BOOK,
                    Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-025"), NamedTextColor.AQUA),
                    List.of(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-026"), NamedTextColor.GRAY),
                            Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-027"), NamedTextColor.DARK_GRAY)), false));
        }
        inventory.setItem(PREVIOUS_SLOT, holder.page > 0 ? navigationItem(GuiConfig.text("team-gui-teammanagementmenu.text-011"))
                : item(Material.ARROW, Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-028"), NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(REFRESH_SLOT, item(Material.SUNFLOWER,
                Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-009"), NamedTextColor.YELLOW), List.of(), false));
        inventory.setItem(PAGE_SLOT, pageItem(holder.page, pages, members.size(), GuiConfig.text("team-gui-teammanagementmenu.text-029")));
        if (holder.page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem(GuiConfig.text("team-gui-teammanagementmenu.text-012")));
        inventory.setItem(TELEPORT_TEAM_SLOT, item(Material.ENDER_PEARL,
                Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-030"), NamedTextColor.LIGHT_PURPLE),
                List.of(Component.text(team.getOnlinePlayers().size() + GuiConfig.text("team-gui-teammanagementmenu.text-031"), NamedTextColor.GRAY)), false));
        inventory.setItem(DELETE_TEAM_SLOT, item(Material.TNT,
                Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-032"), NamedTextColor.RED),
                List.of(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-033"), NamedTextColor.GRAY),
                        Component.empty(), Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-015"), NamedTextColor.YELLOW)), false));
        inventory.setItem(CLOSE_SLOT, closeItem());
    }

    private void openAddPlayer(@NotNull Player player, @NotNull String teamName, int requestedPage) {
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            openOverview(player, 0);
            return;
        }
        if (team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) {
            Utils.sendAdminError(player, GuiConfig.text("team-gui-teammanagementmenu.text-034") + CCConfig.TEAM_MAX_MEMBERS);
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
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, teamTitle(team, GuiConfig.text("team-gui-teammanagementmenu.text-035")));
        holder.inventory = inventory;
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, candidates.size());
        for (int index = from; index < to; index++) {
            Player candidate = candidates.get(index);
            int slot = index - from;
            inventory.setItem(slot, playerHead(candidate.getUniqueId(), candidate.getName(),
                    Component.text(candidate.getName(), NamedTextColor.GREEN),
                    List.of(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-036") + team.getName(), teamColor(team)))));
            holder.targets.put(slot, candidate.getName());
        }
        if (candidates.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE,
                    Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-037"), NamedTextColor.GRAY),
                    List.of(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-038"), NamedTextColor.DARK_GRAY)), false));
        }
        fillFooter(inventory);
        inventory.setItem(BACK_SLOT, item(Material.ARROW, Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-039"), NamedTextColor.WHITE), List.of(), false));
        if (page > 0) inventory.setItem(PREVIOUS_SLOT, navigationItem(GuiConfig.text("team-gui-teammanagementmenu.text-011")));
        inventory.setItem(PAGE_SLOT, pageItem(page, pages, candidates.size(), GuiConfig.text("team-gui-teammanagementmenu.text-040")));
        if (page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem(GuiConfig.text("team-gui-teammanagementmenu.text-012")));
        inventory.setItem(CLOSE_SLOT, closeItem());
        player.openInventory(inventory);
    }

    private void openColorPicker(@NotNull Player player, @NotNull String newTeamName) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.COLOR, newTeamName, 0);
        Inventory inventory = Bukkit.createInventory(holder, 36, title(GuiConfig.text("team-gui-teammanagementmenu.text-041"), NamedTextColor.GOLD));
        holder.inventory = inventory;
        List<String> colors = List.of(Utils.getColorNames());
        for (int index = 0; index < colors.size(); index++) {
            String color = colors.get(index);
            ChampionshipTeam usedBy = sortedTeams().stream()
                    .filter(team -> team.getColorName().equalsIgnoreCase(color))
                    .findFirst().orElse(null);
            Material material = material(color + "_WOOL", Material.WHITE_WOOL);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-042") + color, NamedTextColor.GRAY));
            lore.add(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-043") + COLOR_HEX.get(color), color(color)));
            lore.add(Component.empty());
            if (usedBy == null) lore.add(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-044"), NamedTextColor.GREEN));
            else lore.add(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-045") + usedBy.getName() + GuiConfig.text("team-gui-teammanagementmenu.text-046"), NamedTextColor.RED));
            int slot = COLOR_SLOTS.get(index);
            inventory.setItem(slot, item(usedBy == null ? material : Material.GRAY_DYE,
                    Component.text(COLOR_LABELS.get(color), usedBy == null ? color(color) : NamedTextColor.DARK_GRAY)
                            .decorate(TextDecoration.BOLD), lore, false));
            if (usedBy == null) holder.targets.put(slot, color);
        }
        inventory.setItem(31, item(Material.ARROW, Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-028"), NamedTextColor.WHITE), List.of(), false));
        player.openInventory(inventory);
    }

    private void openQuickPlayers(@NotNull Player player, int requestedPage) {
        List<? extends Player> players = Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int pages = pageCount(players.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.QUICK_PLAYER, null, page);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, title(GuiConfig.text("team-gui-teammanagementmenu.text-047"), NamedTextColor.AQUA));
        holder.inventory = inventory;
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, players.size());
        for (int index = from; index < to; index++) {
            Player candidate = players.get(index);
            ChampionshipTeam current = plugin.getTeamManager().getTeamByPlayer(candidate);
            List<Component> lore = new ArrayList<>();
            lore.add(current == null
                    ? Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-048"), NamedTextColor.GRAY)
                    : Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-049"), NamedTextColor.GRAY)
                    .append(Component.text(current.getName(), teamColor(current))));
            lore.add(Component.empty());
            lore.add(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-050"), NamedTextColor.YELLOW));
            int slot = index - from;
            inventory.setItem(slot, playerHead(candidate.getUniqueId(), candidate.getName(),
                    Component.text(candidate.getName(), NamedTextColor.GREEN).decorate(TextDecoration.BOLD), lore));
            holder.playerTargets.put(slot, candidate.getUniqueId());
            holder.targets.put(slot, candidate.getName());
        }
        fillFooter(inventory);
        inventory.setItem(BACK_SLOT, item(Material.ARROW, Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-028"), NamedTextColor.WHITE), List.of(), false));
        if (page > 0) inventory.setItem(PREVIOUS_SLOT, navigationItem(GuiConfig.text("team-gui-teammanagementmenu.text-011")));
        inventory.setItem(PAGE_SLOT, pageItem(page, pages, players.size(), GuiConfig.text("team-gui-teammanagementmenu.text-051")));
        if (page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem(GuiConfig.text("team-gui-teammanagementmenu.text-012")));
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
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, title(GuiConfig.text("team-gui-teammanagementmenu.text-052") + selectedName + GuiConfig.text("team-gui-teammanagementmenu.text-053"), NamedTextColor.GOLD));
        holder.inventory = inventory;
        for (int index = 0; index < Math.min(PAGE_SIZE, teams.size()); index++) {
            ChampionshipTeam team = teams.get(index);
            boolean same = current != null && current.equals(team);
            boolean full = team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS;
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(team.getMembers().size() + "/" + CCConfig.TEAM_MAX_MEMBERS + GuiConfig.text("team-gui-teammanagementmenu.text-021"), NamedTextColor.GRAY));
            lore.add(Component.empty());
            if (same) lore.add(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-054"), NamedTextColor.GREEN));
            else if (full) lore.add(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-020"), NamedTextColor.RED));
            else if (current == null) lore.add(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-055"), NamedTextColor.YELLOW));
            else lore.add(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-056"), NamedTextColor.YELLOW));
            inventory.setItem(index, item(material(team.getColorName() + "_WOOL", Material.WHITE_WOOL),
                    Component.text(team.getName(), same || full ? NamedTextColor.DARK_GRAY : teamColor(team))
                            .decorate(TextDecoration.BOLD), lore, same));
            if (!same && !full) holder.targets.put(index, team.getName());
        }
        if (teams.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE, Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-057"), NamedTextColor.GRAY), List.of(), false));
        }
        fillFooter(inventory);
        inventory.setItem(BACK_SLOT, item(Material.ARROW, Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-058"), NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(49, playerHead(selectedUuid, selectedName,
                Component.text(selectedName, NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(current == null ? Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-048"), NamedTextColor.GRAY)
                        : Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-059") + current.getName(), teamColor(current)))));
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
            Utils.sendAdminError(player, GuiConfig.text("team-gui-teammanagementmenu.text-034") + CCConfig.TEAM_MAX_MEMBERS);
            openTeam(player, teamName, 0);
            return;
        }
        List<PlayerEntry> candidates = plugin.getPlayerManager().getKnownPlayers().stream()
                .filter(entry -> Bukkit.getPlayer(entry.getUuid()) == null)
                .filter(entry -> plugin.getTeamManager().getTeamByPlayer(entry.getUuid()) == null)
                .sorted(Comparator.comparing(PlayerEntry::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int pages = pageCount(candidates.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.KNOWN_PLAYER, teamName, page);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, teamTitle(team, GuiConfig.text("team-gui-teammanagementmenu.text-025")));
        holder.inventory = inventory;
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, candidates.size());
        for (int index = from; index < to; index++) {
            PlayerEntry candidate = candidates.get(index);
            int slot = index - from;
            inventory.setItem(slot, playerHead(candidate.getUuid(), candidate.getName(),
                    Component.text(candidate.getName(), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                    List.of(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-060"), NamedTextColor.DARK_GRAY),
                            Component.empty(), Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-036") + team.getName(), teamColor(team)))));
            holder.playerTargets.put(slot, candidate.getUuid());
            holder.targets.put(slot, candidate.getName());
        }
        if (candidates.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE, Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-061"), NamedTextColor.GRAY),
                    List.of(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-062"), NamedTextColor.DARK_GRAY)), false));
        }
        fillFooter(inventory);
        inventory.setItem(MANUAL_INPUT_SLOT, item(Material.NAME_TAG, Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-063"), NamedTextColor.AQUA),
                List.of(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-064"), NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-065"), NamedTextColor.YELLOW)), false));
        inventory.setItem(HISTORY_BACK_SLOT, item(Material.ARROW, Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-039"), NamedTextColor.WHITE), List.of(), false));
        if (page > 0) inventory.setItem(PREVIOUS_SLOT, navigationItem(GuiConfig.text("team-gui-teammanagementmenu.text-011")));
        inventory.setItem(PAGE_SLOT, pageItem(page, pages, candidates.size(), GuiConfig.text("team-gui-teammanagementmenu.text-066")));
        if (page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem(GuiConfig.text("team-gui-teammanagementmenu.text-012")));
        inventory.setItem(CLOSE_SLOT, closeItem());
        player.openInventory(inventory);
    }

    private void openConfirmation(@NotNull Player player, @NotNull Confirmation action, @Nullable String teamName,
                                  @Nullable String memberName, @Nullable UUID selectedPlayerUuid, int returnPage) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.CONFIRM, teamName, returnPage);
        holder.confirmation = action;
        holder.memberName = memberName;
        holder.selectedPlayerUuid = selectedPlayerUuid;
        Inventory inventory = Bukkit.createInventory(holder, 27, title(GuiConfig.text("team-gui-teammanagementmenu.text-067"), NamedTextColor.RED));
        holder.inventory = inventory;
        Component subject = switch (action) {
            case DELETE_TEAM -> Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-068") + teamName, NamedTextColor.RED);
            case REMOVE_MEMBER -> Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-069") + memberName, NamedTextColor.RED);
            case TELEPORT_ALL -> Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-070"), NamedTextColor.LIGHT_PURPLE);
            case MOVE_MEMBER -> Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-071") + memberName + GuiConfig.text("team-gui-teammanagementmenu.text-072") + teamName, NamedTextColor.GOLD);
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
                Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-073"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD), List.of(), false));
        inventory.setItem(CANCEL_SLOT, item(Material.RED_CONCRETE,
                Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-074"), NamedTextColor.RED), List.of(), false));
        player.openInventory(inventory);
    }

    private void openTextInput(@NotNull Player player, @NotNull InputPurpose purpose, @Nullable String teamName) {
        String prompt = purpose == InputPurpose.CREATE_TEAM ? GuiConfig.text("team-gui-teammanagementmenu.text-075") : GuiConfig.text("team-gui-teammanagementmenu.text-076");
        InputSession previous = inputs.remove(player.getUniqueId());
        if (previous != null) previous.inventory.clear();
        AnvilView view = MenuType.ANVIL.create(player, title(prompt, NamedTextColor.GOLD));
        player.openInventory(view);
        AnvilInventory inventory = view.getTopInventory();
        InputSession session = new InputSession(purpose, teamName, inventory);
        inputs.put(player.getUniqueId(), session);
        inventory.setFirstItem(item(Material.PAPER, Component.text(prompt, NamedTextColor.YELLOW),
                List.of(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-077"), NamedTextColor.GRAY)), false));
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
            if (!player.hasPermission("cc.admin")) {
                finishInput(player, input);
                player.closeInventory();
                Utils.sendAdminError(player, GuiConfig.text("team-gui-teammanagementmenu.text-078"));
                return;
            }
            if (event.getRawSlot() == 2) submitInput(player, input, (AnvilView) event.getView());
            return;
        }
        if (!(top.getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!holder.viewer.equals(player.getUniqueId()) || event.getClickedInventory() != top) return;
        if (!player.hasPermission("cc.admin")) {
            player.closeInventory();
            Utils.sendAdminError(player, GuiConfig.text("team-gui-teammanagementmenu.text-078"));
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
            Utils.sendAdminError(player, team == null ? GuiConfig.text("team-gui-teammanagementmenu.text-079")
                    : GuiConfig.text("team-gui-teammanagementmenu.text-034") + CCConfig.TEAM_MAX_MEMBERS);
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
                Utils.sendAdminError(player, GuiConfig.text("team-gui-teammanagementmenu.text-080") + holder.teamName);
                openOverview(player, 0);
                return;
            }
            boolean colorUsed = plugin.getTeamManager().getTeamList().stream()
                    .anyMatch(team -> team.getColorName().equalsIgnoreCase(selectedColor));
            if (colorUsed) {
                Utils.sendAdminError(player, GuiConfig.text("team-gui-teammanagementmenu.text-081"));
                openColorPicker(player, holder.teamName);
                return;
            }
            boolean created = plugin.getTeamManager().addTeam(holder.teamName, selectedColor, COLOR_HEX.get(selectedColor));
            if (created) {
                Utils.sendAdminSuccess(player, GuiConfig.text("team-gui-teammanagementmenu.text-082") + COLOR_HEX.get(selectedColor) + holder.teamName);
                success(player);
                openTeam(player, holder.teamName, 0);
            } else {
                Utils.sendAdminError(player, GuiConfig.text("team-gui-teammanagementmenu.text-083"));
                openOverview(player, 0);
            }
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
                    Utils.sendAdminError(player, GuiConfig.text("team-gui-teammanagementmenu.text-016") + holder.teamName);
                } else if (plugin.getTeamManager().deleteTeam(holder.teamName)) {
                    Utils.sendAdminSuccess(player, GuiConfig.text("team-gui-teammanagementmenu.text-084") + holder.teamName);
                    success(player);
                } else {
                    Utils.sendAdminError(player, GuiConfig.text("team-gui-teammanagementmenu.text-085"));
                }
                openOverview(player, 0);
            }
            case REMOVE_MEMBER -> {
                if (plugin.getTeamManager().deleteTeamMember(holder.memberName, holder.teamName)) {
                    Utils.sendAdminSuccess(player, GuiConfig.text("team-gui-teammanagementmenu.text-086") + holder.teamName + GuiConfig.text("team-gui-teammanagementmenu.text-087") + holder.memberName);
                    success(player);
                } else {
                    Utils.sendAdminError(player, GuiConfig.text("team-gui-teammanagementmenu.text-088"));
                }
                openTeam(player, holder.teamName, holder.page);
            }
            case TELEPORT_ALL -> {
                int players = 0;
                for (ChampionshipTeam team : plugin.getTeamManager().getTeamList()) {
                    players += team.getOnlinePlayers().size();
                    team.teleportAllPlayers(player.getLocation());
                }
                Utils.sendAdminSuccess(player, GuiConfig.text("team-gui-teammanagementmenu.text-089") + players + GuiConfig.text("team-gui-teammanagementmenu.text-090"));
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
                Utils.sendAdminError(player, GuiConfig.text("team-gui-teammanagementmenu.text-091"));
                return;
            }
            if (text.length() > 64 || text.chars().anyMatch(Character::isISOControl)) {
                Utils.sendAdminError(player, GuiConfig.text("team-gui-teammanagementmenu.text-092"));
                return;
            }
            boolean exists = plugin.getTeamManager().getTeamList().stream()
                    .anyMatch(team -> team.getName().equalsIgnoreCase(text));
            if (exists) {
                Utils.sendAdminError(player, GuiConfig.text("team-gui-teammanagementmenu.text-080") + text);
                return;
            }
            finishInput(player, input);
            openColorPicker(player, text);
            return;
        }

        if (!text.matches("[A-Za-z0-9_]{1,16}")) {
            Utils.sendAdminError(player, GuiConfig.text("team-gui-teammanagementmenu.text-093"));
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
            Utils.sendAdminError(admin, GuiConfig.text("team-gui-teammanagementmenu.text-016") + teamName);
            openOverview(admin, 0);
            return;
        }
        boolean added = false;
        if (team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) {
            Utils.sendAdminError(admin, GuiConfig.text("team-gui-teammanagementmenu.text-034") + CCConfig.TEAM_MAX_MEMBERS);
        } else if (plugin.getTeamManager().addTeamMember(memberName, team)) {
            Utils.sendAdminSuccess(admin, GuiConfig.text("team-gui-teammanagementmenu.text-094") + memberName + GuiConfig.text("team-gui-teammanagementmenu.text-095") + team.getColorCode() + team.getName());
            success(admin);
            added = true;
        } else {
            Utils.sendAdminError(admin, GuiConfig.text("team-gui-teammanagementmenu.text-096"));
        }
        if (keepSelectorOpen && team.getMembers().size() < CCConfig.TEAM_MAX_MEMBERS) {
            openAddPlayer(admin, teamName, added ? selectorPage : 0);
        } else {
            openTeam(admin, teamName, 0);
        }
    }

    private void addKnownMember(@NotNull Player admin, @NotNull String teamName,
                                @NotNull String memberName, int selectorPage) {
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            openOverview(admin, 0);
            return;
        }
        if (team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) {
            Utils.sendAdminError(admin, GuiConfig.text("team-gui-teammanagementmenu.text-034") + CCConfig.TEAM_MAX_MEMBERS);
            openTeam(admin, teamName, 0);
        } else if (plugin.getTeamManager().addTeamMember(memberName, team)) {
            Utils.sendAdminSuccess(admin, GuiConfig.text("team-gui-teammanagementmenu.text-097") + memberName + GuiConfig.text("team-gui-teammanagementmenu.text-095")
                    + team.getColorCode() + team.getName());
            success(admin);
            if (team.getMembers().size() < CCConfig.TEAM_MAX_MEMBERS) openKnownPlayers(admin, teamName, selectorPage);
            else openTeam(admin, teamName, 0);
        } else {
            Utils.sendAdminError(admin, GuiConfig.text("team-gui-teammanagementmenu.text-098"));
            openKnownPlayers(admin, teamName, selectorPage);
        }
    }

    private void moveMember(@NotNull Player admin, @NotNull UUID uuid, @NotNull String memberName,
                            @NotNull String targetTeamName, int returnPage) {
        ChampionshipTeam target = plugin.getTeamManager().getTeam(targetTeamName);
        if (target == null) {
            Utils.sendAdminError(admin, GuiConfig.text("team-gui-teammanagementmenu.text-099") + targetTeamName);
            openQuickPlayers(admin, returnPage);
            return;
        }
        TeamManager.MemberMoveResult result = plugin.getTeamManager().moveTeamMember(uuid, memberName, target);
        switch (result) {
            case SUCCESS -> {
                Utils.sendAdminSuccess(admin, GuiConfig.text("team-gui-teammanagementmenu.text-094") + memberName + GuiConfig.text("team-gui-teammanagementmenu.text-100")
                        + target.getColorCode() + target.getName());
                success(admin);
                openQuickPlayers(admin, returnPage);
            }
            case SAME_TEAM -> {
                Utils.sendAdminError(admin, GuiConfig.text("team-gui-teammanagementmenu.text-101"));
                openTargetTeams(admin, uuid, memberName, returnPage);
            }
            case TARGET_FULL -> {
                Utils.sendAdminError(admin, GuiConfig.text("team-gui-teammanagementmenu.text-102"));
                openTargetTeams(admin, uuid, memberName, returnPage);
            }
            case TEAM_ACTIVE -> {
                Utils.sendAdminError(admin, GuiConfig.text("team-gui-teammanagementmenu.text-103"));
                openTargetTeams(admin, uuid, memberName, returnPage);
            }
            case INVALID_PLAYER, FAILED -> {
                Utils.sendAdminError(admin, GuiConfig.text("team-gui-teammanagementmenu.text-104"));
                openTargetTeams(admin, uuid, memberName, returnPage);
            }
        }
    }

    private void teleportTeam(@NotNull Player admin, @NotNull String teamName) {
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            openOverview(admin, 0);
            return;
        }
        int online = team.getOnlinePlayers().size();
        team.teleportAllPlayers(admin.getLocation());
        Utils.sendAdminSuccess(admin, GuiConfig.text("team-gui-teammanagementmenu.text-105") + team.getColorCode() + team.getName()
                + GuiConfig.text("team-gui-teammanagementmenu.text-106") + online + GuiConfig.text("team-gui-teammanagementmenu.text-090"));
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
        lore.add(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-107"), NamedTextColor.GRAY)
                .append(Component.text(online + "/" + members, online > 0 ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
        lore.add(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-108"), NamedTextColor.GRAY)
                .append(Component.text(COLOR_LABELS.getOrDefault(team.getColorName().toLowerCase(Locale.ROOT), team.getColorName()), teamColor(team))));
        lore.add(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-109"), NamedTextColor.GRAY).append(Component.text(team.getId(), NamedTextColor.WHITE)));
        lore.add(Component.empty());
        lore.add(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-110"), NamedTextColor.YELLOW));
        return item(material(team.getColorName() + "_WOOL", Material.WHITE_WOOL),
                Component.text(team.getName(), teamColor(team)).decorate(TextDecoration.BOLD), lore, false);
    }

    private ItemStack memberItem(@NotNull MemberView member) {
        NamedTextColor status = member.online ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        return playerHead(member.uuid, member.name,
                Component.text(member.name, status).decorate(TextDecoration.BOLD),
                List.of(Component.text(member.online ? GuiConfig.text("team-gui-teammanagementmenu.text-111") : GuiConfig.text("team-gui-teammanagementmenu.text-112"), status),
                        Component.empty(), Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-113"), NamedTextColor.RED)));
    }

    private static ItemStack playerHead(@NotNull UUID uuid, @NotNull String profileName,
                                        @NotNull Component name, @NotNull List<Component> lore) {
        ItemStack stack = item(Material.PLAYER_HEAD, name, lore, false);
        if (stack.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwnerProfile(Bukkit.createPlayerProfile(uuid, profileName));
            stack.setItemMeta(skull);
        }
        return stack;
    }

    private static List<Component> confirmationLore(@NotNull Confirmation action, @Nullable String teamName) {
        return switch (action) {
            case DELETE_TEAM -> List.of(
                    Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-114"), NamedTextColor.GRAY),
                    Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-115"), NamedTextColor.DARK_GRAY));
            case REMOVE_MEMBER -> List.of(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-116"), NamedTextColor.GRAY));
            case TELEPORT_ALL -> List.of(
                    Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-117"), NamedTextColor.GRAY),
                    Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-118") + (teamName == null ? GuiConfig.text("team-gui-teammanagementmenu.text-119") : teamName), NamedTextColor.DARK_GRAY));
            case MOVE_MEMBER -> List.of(
                    Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-120"), NamedTextColor.GRAY),
                    Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-121"), NamedTextColor.DARK_GRAY));
        };
    }

    private static void fillFooter(@NotNull Inventory inventory) {
        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int slot = 36; slot < inventory.getSize(); slot++) inventory.setItem(slot, border);
    }

    private static ItemStack pageItem(int page, int pages, int count, @NotNull String unit) {
        return item(Material.PAPER,
                Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-122") + (page + 1) + "/" + pages + GuiConfig.text("team-gui-teammanagementmenu.text-123"), NamedTextColor.AQUA),
                List.of(Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-124") + count + " " + unit, NamedTextColor.GRAY)), false);
    }

    private static ItemStack navigationItem(@NotNull String label) {
        return item(Material.ARROW, Component.text(label, NamedTextColor.WHITE), List.of(), false);
    }

    private static ItemStack closeItem() {
        return item(Material.BARRIER, Component.text(GuiConfig.text("team-gui-teammanagementmenu.text-125"), NamedTextColor.RED), List.of(), false);
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
                Map.entry("white", GuiConfig.text("team-gui-teammanagementmenu.text-126")), Map.entry("orange", GuiConfig.text("team-gui-teammanagementmenu.text-127")), Map.entry("magenta", GuiConfig.text("team-gui-teammanagementmenu.text-128")),
                Map.entry("light_blue", GuiConfig.text("team-gui-teammanagementmenu.text-129")), Map.entry("yellow", GuiConfig.text("team-gui-teammanagementmenu.text-130")), Map.entry("lime", GuiConfig.text("team-gui-teammanagementmenu.text-131")),
                Map.entry("pink", GuiConfig.text("team-gui-teammanagementmenu.text-132")), Map.entry("gray", GuiConfig.text("team-gui-teammanagementmenu.text-133")), Map.entry("light_gray", GuiConfig.text("team-gui-teammanagementmenu.text-134")),
                Map.entry("cyan", GuiConfig.text("team-gui-teammanagementmenu.text-135")), Map.entry("purple", GuiConfig.text("team-gui-teammanagementmenu.text-136")), Map.entry("blue", GuiConfig.text("team-gui-teammanagementmenu.text-137")),
                Map.entry("brown", GuiConfig.text("team-gui-teammanagementmenu.text-138")), Map.entry("green", GuiConfig.text("team-gui-teammanagementmenu.text-139")), Map.entry("red", GuiConfig.text("team-gui-teammanagementmenu.text-140")),
                Map.entry("black", GuiConfig.text("team-gui-teammanagementmenu.text-141")));
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

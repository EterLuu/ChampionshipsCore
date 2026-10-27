package ink.ziip.championshipscore.api.team.gui;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.ChampionshipPermissions;
import ink.ziip.championshipscore.api.gui.AnvilInputMenu;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuInventory;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.api.player.entry.PlayerEntry;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.team.TeamManager;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.scoreboard.TeamColors;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Native administrator UI for every operation exposed below {@code /cc team}. */
public final class TeamManagementMenu implements Listener {
    private static final int PAGE_SIZE = 36;
    private static final List<Integer> COLOR_SLOTS =
            List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29);

    private final ChampionshipsCore plugin;
    private final Map<UUID, UiRequest> requests = new ConcurrentHashMap<>();
    private volatile boolean closed;

    private record TeamCreation(boolean created, ChampionshipTeam team) {}

    private boolean admitted(Player player) {
        return !closed
                && plugin.isEnabled()
                && player.isOnline()
                && Bukkit.getPlayer(player.getUniqueId()) == player
                && player.hasPermission(ChampionshipPermissions.ADMIN);
    }

    private static final class UiRequest {
        final Player player;
        final Inventory inventory;
        final BooleanSupplier context;

        UiRequest(Player player, BooleanSupplier context) {
            this.player = player;
            this.inventory = player.getOpenInventory().getTopInventory();
            this.context = context;
        }
    }

    /** The service owns accepted mutations; this ticket owns only their pending UI response. */
    private <T> BiConsumer<T, Throwable> guarded(
            Player player, BooleanSupplier context, T failed, Consumer<T> result) {
        UiRequest request = new UiRequest(player, context);
        requests.put(player.getUniqueId(), request);
        return (value, failure) -> {
            if (closed || !plugin.isEnabled()) {
                requests.remove(player.getUniqueId(), request);
                return;
            }
            try {
                var task =
                        player.getScheduler()
                                .run(
                                        plugin,
                                        ignored -> {
                                            if (!requests.remove(player.getUniqueId(), request)
                                                    || !admitted(player)
                                                    || player.getOpenInventory().getTopInventory()
                                                            != request.inventory
                                                    || !request.context.getAsBoolean()) return;
                                            result.accept(failure == null ? value : failed);
                                        },
                                        () -> requests.remove(player.getUniqueId(), request));
                if (task == null) requests.remove(player.getUniqueId(), request);
            } catch (IllegalPluginAccessException stopped) {
                requests.remove(player.getUniqueId(), request);
            }
        };
    }

    private void invalidate(Player player) {
        UiRequest request = requests.get(player.getUniqueId());
        if (request != null && request.player == player)
            requests.remove(player.getUniqueId(), request);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) invalidate(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UiRequest request = requests.get(player.getUniqueId());
        if (request != null
                && request.player == player
                && request.inventory == event.getInventory())
            requests.remove(player.getUniqueId(), request);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        invalidate(event.getPlayer());
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin() != plugin) return;
        closed = true;
        requests.clear();
        plugin.getAnvilInputMenu().closeOwner(this);
    }

    public TeamManagementMenu(@NotNull ChampionshipsCore plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openOverview(Player player, int requestedPage) {
        if (!admitted(player)) return;
        List<ChampionshipTeam> teams = sortedTeams();
        MenuHolder holder =
                page(player, Screen.OVERVIEW, null, requestedPage, teams.size(), Map.of());
        List<Integer> slots = holder.layout.pageSlots(holder.page, teams.size());
        int first = holder.page * holder.layout.content().size();
        for (int i = 0; i < slots.size(); i++) {
            ChampionshipTeam team = teams.get(first + i);
            int slot = slots.get(i);
            holder.inventory.setItem(
                    slot,
                    TeamMenuItems.team(
                            holder.screen.id,
                            "team",
                            null,
                            Map.of(
                                    "team",
                                    team.getName(),
                                    "online",
                                    team.getOnlinePlayers().size(),
                                    "members",
                                    team.getMembers().size(),
                                    "id",
                                    team.getId(),
                                    "color",
                                    TeamMenuItems.colorLabel(team.getColorName())),
                            team.getColorName()));
            holder.targets.put(slot, team.getName());
        }
        empty(holder, teams.size());
        for (String id : List.of("create", "quick-assign", "refresh", "teleport-all"))
            control(holder, id, null, Map.of());
        navigation(holder);
        player.openInventory(holder.inventory);
    }

    private void openTeam(Player player, String teamName, int requestedPage) {
        if (!admitted(player)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            CoreMessages.sendAdminError(
                    player,
                    MessageConfig.TEAM_GUI_THE_TEAM_NO_LONGER_EXISTS.replace("%team%", teamName));
            openOverview(player, 0);
            return;
        }
        List<MemberView> members = members(team);
        Map<String, Object> values =
                Map.of(
                        "team",
                        team.getName(),
                        "members",
                        members.size(),
                        "max",
                        CCConfig.TEAM_MAX_MEMBERS,
                        "online",
                        team.getOnlinePlayers().size(),
                        "member-unit",
                        GuiConfig.text(MenuId.TEAMS_MEMBERS.item("member-count-unit") + ".title"));
        MenuHolder holder =
                page(player, Screen.TEAM, teamName, requestedPage, members.size(), values);
        List<Integer> slots = holder.layout.pageSlots(holder.page, members.size());
        int first = holder.page * holder.layout.content().size();
        for (int i = 0; i < slots.size(); i++) {
            MemberView member = members.get(first + i);
            int slot = slots.get(i);
            holder.inventory.setItem(
                    slot,
                    TeamMenuItems.player(
                            holder.screen.id,
                            "member",
                            member.online ? "online" : "offline",
                            Map.of("player", member.name),
                            member.uuid,
                            member.name));
            holder.targets.put(slot, member.name);
            holder.playerTargets.put(slot, member.uuid);
        }
        empty(holder, members.size());
        boolean full = members.size() >= CCConfig.TEAM_MAX_MEMBERS;
        control(holder, "add-player", full ? "full" : "available", values);
        if (!full) control(holder, "history", null, values);
        if (holder.page == 0) control(holder, "back", null, values);
        for (String id : List.of("refresh", "teleport", "delete"))
            control(holder, id, null, values);
        navigation(holder);
        player.openInventory(holder.inventory);
    }

    private void openAddPlayer(Player player, String teamName, int requestedPage) {
        if (!admitted(player)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            openOverview(player, 0);
            return;
        }
        if (team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) {
            CoreMessages.sendAdminError(
                    player,
                    MessageConfig
                            .TEAM_GUI_THE_NUMBER_OF_PEOPLE_IN_THE_TEAM_HAS_REACHED_THE_UPPER_LIMIT
                            .replace("%limit%", String.valueOf(CCConfig.TEAM_MAX_MEMBERS)));
            openTeam(player, teamName, 0);
            return;
        }
        var candidates =
                Bukkit.getOnlinePlayers().stream()
                        .filter(
                                candidate ->
                                        plugin.getTeamManager().getTeamByPlayer(candidate) == null)
                        .sorted(
                                Comparator.comparing(
                                        Player::getName, String.CASE_INSENSITIVE_ORDER))
                        .toList();
        MenuHolder holder =
                page(
                        player,
                        Screen.ADD_PLAYER,
                        teamName,
                        requestedPage,
                        candidates.size(),
                        Map.of("team", team.getName()));
        List<Integer> slots = holder.layout.pageSlots(holder.page, candidates.size());
        int first = holder.page * holder.layout.content().size();
        for (int i = 0; i < slots.size(); i++) {
            Player candidate = candidates.get(first + i);
            int slot = slots.get(i);
            holder.inventory.setItem(
                    slot,
                    TeamMenuItems.player(
                            holder.screen.id,
                            "candidate",
                            "candidate",
                            Map.of("player", candidate.getName(), "team", team.getName()),
                            candidate.getUniqueId(),
                            candidate.getName()));
            holder.targets.put(slot, candidate.getName());
            holder.playerTargets.put(slot, candidate.getUniqueId());
        }
        empty(holder, candidates.size());
        control(holder, "back", null, Map.of());
        navigation(holder);
        player.openInventory(holder.inventory);
    }

    private void openColorPicker(Player player, String newTeamName) {
        openColorPicker(player, newTeamName, 0);
    }

    private void openColorPicker(Player player, String newTeamName, int requestedPage) {
        if (!admitted(player)) return;
        List<String> colors = List.of(TeamColors.names());
        MenuHolder holder =
                page(
                        player,
                        Screen.COLOR,
                        newTeamName,
                        requestedPage,
                        colors.size(),
                        Map.of("team", newTeamName));
        List<Integer> slots = holder.layout.pageSlots(holder.page, colors.size());
        int first = holder.page * holder.layout.content().size();
        for (int i = 0; i < slots.size(); i++) {
            String color = colors.get(first + i);
            ChampionshipTeam used =
                    sortedTeams().stream()
                            .filter(team -> team.getColorName().equalsIgnoreCase(color))
                            .findFirst()
                            .orElse(null);
            int slot = slots.get(i);
            holder.inventory.setItem(
                    slot, TeamMenuItems.color(color, used == null ? null : used.getName()));
            if (used == null) holder.targets.put(slot, color);
        }
        control(holder, "back", null, Map.of());
        navigation(holder);
        player.openInventory(holder.inventory);
    }

    private void openQuickPlayers(Player player, int requestedPage) {
        if (!admitted(player)) return;
        var players =
                Bukkit.getOnlinePlayers().stream()
                        .sorted(
                                Comparator.comparing(
                                        Player::getName, String.CASE_INSENSITIVE_ORDER))
                        .toList();
        MenuHolder holder =
                page(player, Screen.QUICK_PLAYER, null, requestedPage, players.size(), Map.of());
        List<Integer> slots = holder.layout.pageSlots(holder.page, players.size());
        int first = holder.page * holder.layout.content().size();
        for (int i = 0; i < slots.size(); i++) {
            Player candidate = players.get(first + i);
            ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(candidate);
            int slot = slots.get(i);
            holder.inventory.setItem(
                    slot,
                    TeamMenuItems.player(
                            holder.screen.id,
                            "player",
                            team == null ? "unassigned" : "assigned",
                            Map.of(
                                    "player",
                                    candidate.getName(),
                                    "team",
                                    team == null ? "" : team.getName()),
                            candidate.getUniqueId(),
                            candidate.getName()));
            holder.targets.put(slot, candidate.getName());
            holder.playerTargets.put(slot, candidate.getUniqueId());
        }
        empty(holder, players.size());
        control(holder, "back", null, Map.of());
        navigation(holder);
        player.openInventory(holder.inventory);
    }

    private void openTargetTeams(Player player, UUID uuid, String name, int returnPage) {
        openTargetTeams(player, uuid, name, returnPage, 0);
    }

    private void openTargetTeams(
            Player player, UUID uuid, String name, int returnPage, int requestedPage) {
        if (!admitted(player)) return;
        Player selected = Bukkit.getPlayer(uuid);
        if (selected == null || !selected.getName().equalsIgnoreCase(name)) {
            openQuickPlayers(player, returnPage);
            return;
        }
        List<ChampionshipTeam> teams = sortedTeams();
        ChampionshipTeam current = plugin.getTeamManager().getTeamByPlayer(uuid);
        MenuHolder holder =
                page(
                        player,
                        Screen.TARGET_TEAM,
                        null,
                        requestedPage,
                        teams.size(),
                        Map.of("player", name));
        holder.returnPage = returnPage;
        holder.selectedPlayerUuid = uuid;
        holder.memberName = name;
        List<Integer> slots = holder.layout.pageSlots(holder.page, teams.size());
        int first = holder.page * holder.layout.content().size();
        for (int i = 0; i < slots.size(); i++) {
            ChampionshipTeam team = teams.get(first + i);
            boolean same = current == team,
                    full = team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS;
            String state = same ? "current" : full ? "full" : current == null ? "join" : "move";
            int slot = slots.get(i);
            holder.inventory.setItem(
                    slot,
                    TeamMenuItems.team(
                            holder.screen.id,
                            "team",
                            state,
                            Map.of(
                                    "team",
                                    team.getName(),
                                    "members",
                                    team.getMembers().size(),
                                    "max",
                                    CCConfig.TEAM_MAX_MEMBERS),
                            team.getColorName()));
            if (!same && !full) holder.targets.put(slot, team.getName());
        }
        empty(holder, teams.size());
        control(holder, "back", null, Map.of());
        holder.inventory.setItem(
                holder.layout.control("selected-player"),
                TeamMenuItems.player(
                        holder.screen.id,
                        "selected-player",
                        current == null ? "unassigned" : "assigned",
                        Map.of("player", name, "team", current == null ? "" : current.getName()),
                        uuid,
                        name));
        navigation(holder);
        player.openInventory(holder.inventory);
    }

    private void openKnownPlayers(
            @NotNull Player player, @NotNull String teamName, int requestedPage) {
        if (!admitted(player)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            openOverview(player, 0);
            return;
        }
        if (team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) {
            CoreMessages.sendAdminError(
                    player,
                    MessageConfig
                            .TEAM_GUI_THE_NUMBER_OF_PEOPLE_IN_THE_TEAM_HAS_REACHED_THE_UPPER_LIMIT
                            .replace("%limit%", String.valueOf(CCConfig.TEAM_MAX_MEMBERS)));
            openTeam(player, teamName, 0);
            return;
        }
        plugin.getPlayerManager()
                .getKnownPlayersAsync()
                .whenComplete(
                        guarded(
                                player,
                                () -> plugin.getTeamManager().getTeam(teamName) == team,
                                null,
                                knownPlayers -> {
                                    if (knownPlayers == null) {
                                        CoreMessages.sendAdminError(
                                                player,
                                                MessageConfig
                                                        .TEAM_GUI_UNABLE_TO_READ_PLAYER_HISTORY);
                                        openTeam(player, teamName, 0);
                                        return;
                                    }
                                    renderKnownPlayers(player, team, requestedPage, knownPlayers);
                                }));
    }

    private void renderKnownPlayers(
            Player player,
            ChampionshipTeam team,
            int requestedPage,
            List<PlayerEntry> knownPlayers) {
        if (!admitted(player)) return;
        List<PlayerEntry> candidates =
                knownPlayers.stream()
                        .filter(candidate -> Bukkit.getPlayer(candidate.getUuid()) == null)
                        .filter(
                                candidate ->
                                        plugin.getTeamManager().getTeamByPlayer(candidate.getUuid())
                                                == null)
                        .sorted(
                                Comparator.comparing(
                                        PlayerEntry::getName, String.CASE_INSENSITIVE_ORDER))
                        .toList();
        MenuHolder holder =
                page(
                        player,
                        Screen.KNOWN_PLAYER,
                        team.getName(),
                        requestedPage,
                        candidates.size(),
                        Map.of("team", team.getName()));
        List<Integer> slots = holder.layout.pageSlots(holder.page, candidates.size());
        int first = holder.page * holder.layout.content().size();
        for (int i = 0; i < slots.size(); i++) {
            PlayerEntry candidate = candidates.get(first + i);
            int slot = slots.get(i);
            holder.inventory.setItem(
                    slot,
                    TeamMenuItems.player(
                            holder.screen.id,
                            "candidate",
                            "candidate",
                            Map.of("player", candidate.getName(), "team", team.getName()),
                            candidate.getUuid(),
                            candidate.getName()));
            holder.targets.put(slot, candidate.getName());
            holder.playerTargets.put(slot, candidate.getUuid());
        }
        empty(holder, candidates.size());
        control(holder, "manual-input", null, Map.of());
        control(holder, "back", null, Map.of());
        navigation(holder);
        player.openInventory(holder.inventory);
    }

    private void openConfirmation(
            Player player,
            Confirmation action,
            String teamName,
            String memberName,
            UUID selectedUuid,
            int returnPage) {
        if (!admitted(player)) return;
        MenuHolder holder = page(player, Screen.CONFIRM, teamName, 0, 0, Map.of());
        holder.confirmation = action;
        holder.memberName = memberName;
        holder.selectedPlayerUuid = selectedUuid;
        holder.returnPage = returnPage;
        String state =
                switch (action) {
                    case DELETE_TEAM -> "delete";
                    case REMOVE_MEMBER -> "remove";
                    case TELEPORT_ALL -> "teleport-all";
                    case MOVE_MEMBER -> "move";
                };
        holder.inventory.setItem(
                holder.layout.control("action"),
                TeamMenuItems.item(
                        holder.screen.id,
                        "action",
                        state,
                        Map.of(
                                "team",
                                teamName == null ? "" : teamName,
                                "player",
                                memberName == null ? "" : memberName)));
        control(holder, "confirm", null, Map.of());
        control(holder, "cancel", null, Map.of());
        player.openInventory(holder.inventory);
    }

    private MenuHolder page(
            Player player,
            Screen screen,
            String team,
            int requestedPage,
            int count,
            Map<String, ?> values) {
        MenuHolder holder = new MenuHolder(player, screen, team, requestedPage);
        holder.page = screen == Screen.CONFIRM ? 0 : holder.layout.clampPage(requestedPage, count);
        holder.pageCount = screen == Screen.CONFIRM ? 1 : holder.layout.pageCount(count);
        holder.count = count;
        var titleValues = new HashMap<String, Object>();
        titleValues.putAll(values);
        if (holder.team != null) titleValues.put("team", TeamMenuItems.teamName(holder.team));
        holder.inventory =
                Bukkit.createInventory(
                        holder, holder.layout.size(), TeamMenuItems.title(screen.id, titleValues));
        if (!holder.layout.border().isEmpty()) {
            ItemStack border = TeamMenuItems.item(screen.id, "border", null, Map.of());
            for (int slot : holder.layout.border()) holder.inventory.setItem(slot, border);
        }
        return holder;
    }

    private static void empty(MenuHolder holder, int count) {
        if (count == 0)
            holder.inventory.setItem(
                    holder.emptySlot,
                    TeamMenuItems.item(holder.screen.id, "empty", null, Map.of()));
    }

    private static void control(MenuHolder holder, String id, String state, Map<String, ?> values) {
        var context = new HashMap<String, Object>();
        context.putAll(values);
        context.put(
                "page", (holder.screen == Screen.CONFIRM ? holder.returnPage : holder.page) + 1);
        context.put("return-page", holder.returnPage + 1);
        context.put("pages", holder.pageCount);
        context.put("count", holder.count);
        if (id.equals("page"))
            context.put("unit", GuiConfig.text(holder.screen.id.item("page-unit") + ".title"));
        int slot = holder.layout.control(id);
        holder.inventory.setItem(slot, TeamMenuItems.item(holder.screen.id, id, state, context));
        holder.controls.put(slot, id);
    }

    private static void navigation(MenuHolder holder) {
        if (holder.layout.controls().containsKey("previous") && holder.page > 0)
            control(holder, "previous", null, Map.of());
        if (holder.layout.controls().containsKey("next") && holder.page + 1 < holder.pageCount)
            control(holder, "next", null, Map.of());
        if (holder.layout.controls().containsKey("page")) control(holder, "page", null, Map.of());
        if (holder.layout.controls().containsKey("close")) control(holder, "close", null, Map.of());
    }

    private void openTextInput(Player player, InputPurpose purpose, String teamName) {
        if (!admitted(player)) return;
        ChampionshipTeam team = teamName == null ? null : plugin.getTeamManager().getTeam(teamName);
        if (purpose == InputPurpose.ADD_MEMBER && team == null) return;
        String path =
                MenuId.TEAMS_INPUT.item(
                        purpose == InputPurpose.CREATE_TEAM ? "team-name" : "player-name");
        String prompt = GuiConfig.text(path + ".title");
        plugin.getAnvilInputMenu()
                .open(
                        this,
                        player,
                        AnvilInputMenu.Prompt.configured(
                                path,
                                prompt,
                                prompt,
                                GuiConfig.component(MenuId.TEAMS_INPUT.item("hint") + ".title")),
                        new AnvilInputMenu.Callbacks(
                                () ->
                                        admitted(player)
                                                && (team == null
                                                        || plugin.getTeamManager().getTeam(teamName)
                                                                == team),
                                text -> validateInput(purpose, text),
                                text -> {
                                    if (purpose == InputPurpose.CREATE_TEAM)
                                        openColorPicker(player, text);
                                    else addMember(player, teamName, text, null, false, 0);
                                },
                                error -> CoreMessages.sendAdminError(player, error),
                                () -> {
                                    if (team == null) openOverview(player, 0);
                                    else openTeam(player, teamName, 0);
                                },
                                () -> {
                                    if (!player.hasPermission(ChampionshipPermissions.ADMIN)) {
                                        CoreMessages.sendAdminError(
                                                player,
                                                MessageConfig
                                                        .TEAM_GUI_YOU_NO_LONGER_HAVE_PERMISSION_TO_USE_THE_TEAM_MANAGEMENT_INTERFACE);
                                    }
                                }));
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (holder.owner != this
                || holder.player != player
                || Bukkit.getPlayer(player.getUniqueId()) != player
                || player.getOpenInventory().getTopInventory() != top
                || event.getClickedInventory() != top) return;
        if (!admitted(player)) {
            player.closeInventory();
            CoreMessages.sendAdminError(
                    player,
                    MessageConfig
                            .TEAM_GUI_YOU_NO_LONGER_HAVE_PERMISSION_TO_USE_THE_TEAM_MANAGEMENT_INTERFACE);
            return;
        }
        if (!holder.currentTeam()) {
            openOverview(player, 0);
            return;
        }
        String selected = holder.targets.get(event.getRawSlot());
        if (selected != null
                && !holder.playerTargets.containsKey(event.getRawSlot())
                && holder.teamSnapshot.containsKey(selected)
                && plugin.getTeamManager().getTeam(selected) != holder.teamSnapshot.get(selected)) {
            openOverview(player, 0);
            return;
        }
        if (!holder.targets.containsKey(event.getRawSlot())
                && !holder.controls.containsKey(event.getRawSlot())) return;
        handleMenuClick(player, holder, event.getRawSlot());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder)
            event.setCancelled(true);
    }

    private void handleMenuClick(@NotNull Player player, @NotNull MenuHolder holder, int slot) {
        if (holder.layout.controls().containsKey("close")
                && slot == holder.layout.control("close")) {
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
        if (slot == holder.layout.control("create"))
            openTextInput(player, InputPurpose.CREATE_TEAM, null);
        else if (slot == holder.layout.control("refresh")) openOverview(player, holder.page);
        else if (slot == holder.layout.control("previous") && holder.page > 0)
            openOverview(player, holder.page - 1);
        else if (slot == holder.layout.control("next")) openOverview(player, holder.page + 1);
        else if (slot == holder.layout.control("teleport-all"))
            openConfirmation(player, Confirmation.TELEPORT_ALL, null, null, null, holder.page);
        else if (slot == holder.layout.control("quick-assign")) openQuickPlayers(player, 0);
    }

    private void handleTeamClick(@NotNull Player player, @NotNull MenuHolder holder, int slot) {
        String member = holder.targets.get(slot);
        if (member != null) {
            UUID uuid = holder.playerTargets.get(slot);
            if (uuid == null || !holder.team.getMembers().contains(uuid)) {
                openTeam(player, holder.teamName, holder.page);
                return;
            }
            openConfirmation(
                    player, Confirmation.REMOVE_MEMBER, holder.teamName, member, uuid, holder.page);
            return;
        }
        ChampionshipTeam team = plugin.getTeamManager().getTeam(holder.teamName);
        boolean full = team == null || team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS;
        if ((slot == holder.layout.control("add-player")
                        || slot == holder.layout.control("history"))
                && full) {
            CoreMessages.sendAdminError(
                    player,
                    team == null
                            ? MessageConfig.TEAM_GUI_TEAM_NOT_FOUND_FEEDBACK
                            : MessageConfig
                                    .TEAM_GUI_THE_NUMBER_OF_PEOPLE_IN_THE_TEAM_HAS_REACHED_THE_UPPER_LIMIT
                                    .replace("%limit%", String.valueOf(CCConfig.TEAM_MAX_MEMBERS)));
            openTeam(player, holder.teamName, holder.page);
        } else if (slot == holder.layout.control("add-player"))
            openAddPlayer(player, holder.teamName, 0);
        else if (slot == holder.layout.control("history"))
            openKnownPlayers(player, holder.teamName, 0);
        else if (slot == holder.layout.control("refresh"))
            openTeam(player, holder.teamName, holder.page);
        else if (slot == holder.layout.control("previous") && holder.page > 0)
            openTeam(player, holder.teamName, holder.page - 1);
        else if (slot == holder.layout.control("back") && holder.page == 0) openOverview(player, 0);
        else if (slot == holder.layout.control("next"))
            openTeam(player, holder.teamName, holder.page + 1);
        else if (slot == holder.layout.control("teleport")) teleportTeam(player, holder.teamName);
        else if (slot == holder.layout.control("delete"))
            openConfirmation(
                    player, Confirmation.DELETE_TEAM, holder.teamName, null, null, holder.page);
    }

    private void handleAddPlayerClick(
            @NotNull Player player, @NotNull MenuHolder holder, int slot) {
        String member = holder.targets.get(slot);
        if (member != null) {
            UUID uuid = holder.playerTargets.get(slot);
            Player selected = uuid == null ? null : Bukkit.getPlayer(uuid);
            if (selected == null || !selected.getName().equalsIgnoreCase(member)) {
                openAddPlayer(player, holder.teamName, holder.page);
                return;
            }
            addMember(player, holder.teamName, member, uuid, true, holder.page);
            return;
        }
        if (slot == holder.layout.control("back")) openTeam(player, holder.teamName, 0);
        else if (slot == holder.layout.control("previous") && holder.page > 0)
            openAddPlayer(player, holder.teamName, holder.page - 1);
        else if (slot == holder.layout.control("next"))
            openAddPlayer(player, holder.teamName, holder.page + 1);
    }

    private void handleKnownPlayerClick(
            @NotNull Player player, @NotNull MenuHolder holder, int slot) {
        String member = holder.targets.get(slot);
        if (member != null) {
            UUID uuid = holder.playerTargets.get(slot);
            if (uuid == null) return;
            addKnownMember(player, holder.teamName, member, uuid, holder.page);
            return;
        }
        if (slot == holder.layout.control("manual-input"))
            openTextInput(player, InputPurpose.ADD_MEMBER, holder.teamName);
        else if (slot == holder.layout.control("back")) openTeam(player, holder.teamName, 0);
        else if (slot == holder.layout.control("previous") && holder.page > 0)
            openKnownPlayers(player, holder.teamName, holder.page - 1);
        else if (slot == holder.layout.control("next"))
            openKnownPlayers(player, holder.teamName, holder.page + 1);
    }

    private void handleQuickPlayerClick(
            @NotNull Player player, @NotNull MenuHolder holder, int slot) {
        UUID selectedUuid = holder.playerTargets.get(slot);
        String selectedName = holder.targets.get(slot);
        if (selectedUuid != null && selectedName != null) {
            openTargetTeams(player, selectedUuid, selectedName, holder.page);
            return;
        }
        if (slot == holder.layout.control("back")) openOverview(player, 0);
        else if (slot == holder.layout.control("previous") && holder.page > 0)
            openQuickPlayers(player, holder.page - 1);
        else if (slot == holder.layout.control("next")) openQuickPlayers(player, holder.page + 1);
    }

    private void handleTargetTeamClick(
            @NotNull Player player, @NotNull MenuHolder holder, int slot) {
        if (slot == holder.layout.control("back")) {
            openQuickPlayers(player, holder.returnPage);
            return;
        }
        if (slot == holder.layout.control("previous") && holder.page > 0) {
            openTargetTeams(
                    player,
                    holder.selectedPlayerUuid,
                    holder.memberName,
                    holder.returnPage,
                    holder.page - 1);
            return;
        }
        if (slot == holder.layout.control("next")) {
            openTargetTeams(
                    player,
                    holder.selectedPlayerUuid,
                    holder.memberName,
                    holder.returnPage,
                    holder.page + 1);
            return;
        }
        String targetTeam = holder.targets.get(slot);
        if (targetTeam == null) return;
        ChampionshipTeam current =
                plugin.getTeamManager().getTeamByPlayer(holder.selectedPlayerUuid);
        if (current == null) {
            moveMember(
                    player,
                    holder.selectedPlayerUuid,
                    holder.memberName,
                    targetTeam,
                    holder.returnPage);
        } else {
            openConfirmation(
                    player,
                    Confirmation.MOVE_MEMBER,
                    targetTeam,
                    holder.memberName,
                    holder.selectedPlayerUuid,
                    holder.returnPage);
        }
    }

    private void handleColorClick(@NotNull Player player, @NotNull MenuHolder holder, int slot) {
        if (slot == holder.layout.control("previous") && holder.page > 0) {
            openColorPicker(player, holder.teamName, holder.page - 1);
            return;
        }
        if (slot == holder.layout.control("next")) {
            openColorPicker(player, holder.teamName, holder.page + 1);
            return;
        }
        String selectedColor = holder.targets.get(slot);
        if (selectedColor != null) {
            if (plugin.getTeamManager().getTeam(holder.teamName) != null) {
                CoreMessages.sendAdminError(
                        player,
                        MessageConfig.TEAM_GUI_TEAM_NAME_ALREADY_EXISTS.replace(
                                "%team%", holder.teamName));
                openOverview(player, 0);
                return;
            }
            boolean colorUsed =
                    plugin.getTeamManager().getTeamList().stream()
                            .anyMatch(team -> team.getColorName().equalsIgnoreCase(selectedColor));
            if (colorUsed) {
                CoreMessages.sendAdminError(
                        player,
                        MessageConfig
                                .TEAM_GUI_THIS_COLOR_HAS_JUST_BEEN_TAKEN_BY_ANOTHER_TEAM_PLEASE_CHOOSE_AGAIN);
                openColorPicker(player, holder.teamName);
                return;
            }
            if (holder.pending) return;
            holder.pending = true;
            plugin.getTeamManager()
                    .addTeam(holder.teamName, selectedColor, TeamMenuItems.colorHex(selectedColor))
                    .thenApply(
                            created ->
                                    new TeamCreation(
                                            created,
                                            created
                                                    ? plugin.getTeamManager()
                                                            .getTeam(holder.teamName)
                                                    : null))
                    .whenComplete(
                            guarded(
                                    player,
                                    () -> true,
                                    new TeamCreation(false, null),
                                    outcome -> {
                                        if (outcome.created()
                                                && (outcome.team() == null
                                                        || plugin.getTeamManager()
                                                                        .getTeam(holder.teamName)
                                                                != outcome.team())) return;
                                        boolean created = outcome.created();

                                        if (created) {
                                            CoreMessages.sendAdminSuccess(
                                                    player,
                                                    MessageConfig.TEAM_GUI_TEAM_CREATED
                                                            .replace(
                                                                    "%color%",
                                                                    TeamMenuItems.colorHex(
                                                                            selectedColor))
                                                            .replace("%team%", holder.teamName));
                                            success(player);
                                            openTeam(player, holder.teamName, 0);
                                        } else {
                                            CoreMessages.sendAdminError(
                                                    player,
                                                    MessageConfig
                                                            .TEAM_GUI_FAILED_TO_CREATE_TEAM_NAME_OR_COLOR_MAY_ALREADY_BE_TAKEN);
                                            openOverview(player, 0);
                                        }
                                    }));
        } else if (slot == holder.layout.control("back")) openOverview(player, 0);
    }

    private void handleConfirmationClick(
            @NotNull Player player, @NotNull MenuHolder holder, int slot) {
        if (slot == holder.layout.control("cancel")) {
            returnFromConfirmation(player, holder);
            return;
        }
        if (slot != holder.layout.control("confirm") || holder.pending) return;
        holder.pending = true;
        switch (holder.confirmation) {
            case DELETE_TEAM -> {
                if (plugin.getTeamManager().getTeam(holder.teamName) == null) {
                    CoreMessages.sendAdminError(
                            player,
                            MessageConfig.TEAM_GUI_THE_TEAM_NO_LONGER_EXISTS.replace(
                                    "%team%", holder.teamName));
                } else {
                    plugin.getTeamManager()
                            .deleteTeam(holder.teamName)
                            .whenComplete(
                                    guarded(
                                            player,
                                            () ->
                                                    plugin.getTeamManager().getTeam(holder.teamName)
                                                                    == null
                                                            || plugin.getTeamManager()
                                                                            .getTeam(
                                                                                    holder.teamName)
                                                                    == holder.team,
                                            TeamManager.TeamDeletionResult.FAILED,
                                            result -> {
                                                if (result
                                                        == ink.ziip.championshipscore.api.team
                                                                .TeamManager.TeamDeletionResult
                                                                .DELETED) {
                                                    CoreMessages.sendAdminSuccess(
                                                            player,
                                                            MessageConfig.TEAM_GUI_TEAM_DELETED
                                                                    .replace(
                                                                            "%team%",
                                                                            holder.teamName));
                                                    success(player);
                                                } else {
                                                    CoreMessages.sendAdminError(
                                                            player,
                                                            MessageConfig
                                                                    .TEAM_GUI_UNABLE_TO_DELETE_TEAM_TEAM_MAY_BE_IN_GAME);
                                                }
                                                openOverview(player, 0);
                                            }));
                    return;
                }
                openOverview(player, 0);
            }
            case REMOVE_MEMBER -> {
                if (holder.selectedPlayerUuid == null) {
                    openTeam(player, holder.teamName, holder.returnPage);
                    return;
                }
                plugin.getTeamManager()
                        .deleteTeamMember(holder.selectedPlayerUuid, holder.team)
                        .whenComplete(
                                guarded(
                                        player,
                                        holder::currentTeam,
                                        false,
                                        deleted -> {
                                            if (deleted) {
                                                CoreMessages.sendAdminSuccess(
                                                        player,
                                                        MessageConfig.TEAM_GUI_MEMBER_REMOVED
                                                                .replace("%team%", holder.teamName)
                                                                .replace(
                                                                        "%player%",
                                                                        holder.memberName));
                                                success(player);
                                            } else {
                                                CoreMessages.sendAdminError(
                                                        player,
                                                        MessageConfig
                                                                .TEAM_GUI_REMOVAL_FAILED_TEAM_OR_MEMBER_STATUS_MAY_HAVE_CHANGED);
                                            }
                                            openTeam(player, holder.teamName, holder.returnPage);
                                        }));
                return;
            }
            case TELEPORT_ALL -> {
                int players = 0;
                for (ChampionshipTeam team : plugin.getTeamManager().getTeamList()) {
                    players += team.getOnlinePlayers().size();
                    team.teleportAllPlayers(player.getLocation());
                }
                CoreMessages.sendAdminSuccess(
                        player,
                        MessageConfig.TEAM_GUI_ALL_TEAMS_TELEPORTED.replace(
                                "%count%", String.valueOf(players)));
                success(player);
                openOverview(player, holder.returnPage);
            }
            case MOVE_MEMBER ->
                    moveMember(
                            player,
                            holder.selectedPlayerUuid,
                            holder.memberName,
                            holder.teamName,
                            holder.returnPage);
        }
    }

    private String validateInput(InputPurpose purpose, String text) {
        if (purpose == InputPurpose.CREATE_TEAM) {
            if (text.isBlank()) return MessageConfig.TEAM_GUI_TEAM_NAME_CANNOT_BE_EMPTY;
            if (text.length() > 64 || text.chars().anyMatch(Character::isISOControl)) {
                return MessageConfig
                        .TEAM_GUI_TEAM_NAMES_CANNOT_EXCEED_64_CHARACTERS_AND_CANNOT_CONTAIN_CONTROL_CHARACTERS;
            }
            if (plugin.getTeamManager().getTeamList().stream()
                    .anyMatch(team -> team.getName().equalsIgnoreCase(text))) {
                return MessageConfig.TEAM_GUI_TEAM_NAME_ALREADY_EXISTS.replace("%team%", text);
            }
            return null;
        }
        return text.matches("[A-Za-z0-9_]{3,16}")
                ? null
                : MessageConfig.TEAM_GUI_PLEASE_ENTER_A_VALID_MINECRAFT_PLAYER_NAME;
    }

    private void addMember(
            @NotNull Player admin,
            @NotNull String teamName,
            @NotNull String memberName,
            @Nullable UUID selectedUuid,
            boolean keepSelectorOpen,
            int selectorPage) {
        if (!admitted(admin)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            CoreMessages.sendAdminError(
                    admin,
                    MessageConfig.TEAM_GUI_THE_TEAM_NO_LONGER_EXISTS.replace("%team%", teamName));
            openOverview(admin, 0);
            return;
        }
        if (team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) {
            CoreMessages.sendAdminError(
                    admin,
                    MessageConfig
                            .TEAM_GUI_THE_NUMBER_OF_PEOPLE_IN_THE_TEAM_HAS_REACHED_THE_UPPER_LIMIT
                            .replace("%limit%", String.valueOf(CCConfig.TEAM_MAX_MEMBERS)));
        } else {
            var operation =
                    selectedUuid == null
                            ? plugin.getTeamManager().addTeamMember(memberName, team)
                            : plugin.getTeamManager().addTeamMember(selectedUuid, memberName, team);
            operation.whenComplete(
                    guarded(
                            admin,
                            () -> plugin.getTeamManager().getTeam(teamName) == team,
                            TeamManager.MemberAddResult.DATABASE_ERROR,
                            result -> {
                                boolean added = result == TeamManager.MemberAddResult.ADDED;
                                if (added) {
                                    CoreMessages.sendAdminSuccess(
                                            admin,
                                            MessageConfig.TEAM_GUI_PLAYER_JOINED
                                                    .replace("%player%", memberName)
                                                    .replace("%color%", team.getColorCode())
                                                    .replace("%team%", team.getName()));
                                    success(admin);
                                } else {
                                    CoreMessages.sendAdminError(
                                            admin, memberAddFailureText(result));
                                }
                                if (keepSelectorOpen
                                        && team.getMembers().size() < CCConfig.TEAM_MAX_MEMBERS)
                                    openAddPlayer(admin, teamName, added ? selectorPage : 0);
                                else openTeam(admin, teamName, 0);
                            }));
            return;
        }
        openTeam(admin, teamName, 0);
    }

    private void addKnownMember(
            @NotNull Player admin,
            @NotNull String teamName,
            @NotNull String memberName,
            @NotNull UUID selectedUuid,
            int selectorPage) {
        if (!admitted(admin)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            openOverview(admin, 0);
            return;
        }
        if (team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) {
            CoreMessages.sendAdminError(
                    admin,
                    MessageConfig
                            .TEAM_GUI_THE_NUMBER_OF_PEOPLE_IN_THE_TEAM_HAS_REACHED_THE_UPPER_LIMIT
                            .replace("%limit%", String.valueOf(CCConfig.TEAM_MAX_MEMBERS)));
            openTeam(admin, teamName, 0);
        } else {
            plugin.getTeamManager()
                    .addTeamMember(selectedUuid, memberName, team)
                    .whenComplete(
                            guarded(
                                    admin,
                                    () -> plugin.getTeamManager().getTeam(teamName) == team,
                                    TeamManager.MemberAddResult.DATABASE_ERROR,
                                    result -> {
                                        boolean added = result == TeamManager.MemberAddResult.ADDED;
                                        if (added) {
                                            CoreMessages.sendAdminSuccess(
                                                    admin,
                                                    MessageConfig.TEAM_GUI_OFFLINE_PLAYER_JOINED
                                                            .replace("%player%", memberName)
                                                            .replace("%color%", team.getColorCode())
                                                            .replace("%team%", team.getName()));
                                            success(admin);
                                            if (team.getMembers().size()
                                                    < CCConfig.TEAM_MAX_MEMBERS)
                                                openKnownPlayers(admin, teamName, selectorPage);
                                            else openTeam(admin, teamName, 0);
                                        } else {
                                            CoreMessages.sendAdminError(
                                                    admin, memberAddFailureText(result));
                                            openKnownPlayers(admin, teamName, selectorPage);
                                        }
                                    }));
        }
    }

    private static String memberAddFailureText(@NotNull TeamManager.MemberAddResult result) {
        return switch (result) {
            case INVALID_PLAYER_NAME -> MessageConfig.TEAM_GUI_ADD_MEMBER_INVALID_PLAYER_NAME;
            case TEAM_NOT_FOUND -> MessageConfig.TEAM_GUI_ADD_MEMBER_TEAM_NOT_FOUND;
            case TEAM_FULL -> MessageConfig.TEAM_GUI_ADD_MEMBER_TEAM_FULL;
            case OPERATION_IN_PROGRESS -> MessageConfig.TEAM_GUI_ADD_MEMBER_OPERATION_IN_PROGRESS;
            case PLAYER_NOT_FOUND -> MessageConfig.TEAM_GUI_ADD_MEMBER_PLAYER_NOT_REGISTERED;
            case PROFILE_SERVICE_UNAVAILABLE ->
                    MessageConfig.TEAM_GUI_ADD_MEMBER_PROFILE_SERVICE_UNAVAILABLE;
            case IDENTITY_CONFLICT, ALREADY_MEMBER ->
                    MessageConfig.TEAM_GUI_ADD_MEMBER_IDENTITY_CONFLICT;
            case DATABASE_ERROR -> MessageConfig.TEAM_GUI_ADD_MEMBER_DATABASE_ERROR;
            case ADDED -> "";
        };
    }

    private void moveMember(
            @NotNull Player admin,
            @NotNull UUID uuid,
            @NotNull String memberName,
            @NotNull String targetTeamName,
            int returnPage) {
        if (!admitted(admin)) return;
        ChampionshipTeam target = plugin.getTeamManager().getTeam(targetTeamName);
        if (target == null) {
            CoreMessages.sendAdminError(
                    admin,
                    MessageConfig.TEAM_GUI_THE_TARGET_TEAM_NO_LONGER_EXISTS.replace(
                            "%team%", targetTeamName));
            openQuickPlayers(admin, returnPage);
            return;
        }
        plugin.getTeamManager()
                .moveTeamMember(uuid, memberName, target)
                .whenComplete(
                        guarded(
                                admin,
                                () -> plugin.getTeamManager().getTeam(targetTeamName) == target,
                                TeamManager.MemberMoveResult.FAILED,
                                result -> {
                                    switch (result) {
                                        case SUCCESS -> {
                                            CoreMessages.sendAdminSuccess(
                                                    admin,
                                                    MessageConfig.TEAM_GUI_PLAYER_MOVED
                                                            .replace("%player%", memberName)
                                                            .replace(
                                                                    "%color%",
                                                                    target.getColorCode())
                                                            .replace("%team%", target.getName()));
                                            success(admin);
                                            openQuickPlayers(admin, returnPage);
                                        }
                                        case SAME_TEAM -> {
                                            CoreMessages.sendAdminError(
                                                    admin,
                                                    MessageConfig
                                                            .TEAM_GUI_THE_PLAYER_IS_ALREADY_ON_THE_TEAM);
                                            openTargetTeams(admin, uuid, memberName, returnPage);
                                        }
                                        case TARGET_FULL -> {
                                            CoreMessages.sendAdminError(
                                                    admin,
                                                    MessageConfig.TEAM_GUI_THE_TARGET_TEAM_IS_FULL);
                                            openTargetTeams(admin, uuid, memberName, returnPage);
                                        }
                                        case TEAM_ACTIVE -> {
                                            CoreMessages.sendAdminError(
                                                    admin,
                                                    MessageConfig
                                                            .TEAM_GUI_THE_PLAYER_S_CURRENT_TEAM_OR_TARGET_TEAM_IS_CURRENTLY_IN_THE_GAME_CANNOT_CHANGE_TEAMS);
                                            openTargetTeams(admin, uuid, memberName, returnPage);
                                        }
                                        case INVALID_PLAYER, FAILED -> {
                                            CoreMessages.sendAdminError(
                                                    admin,
                                                    MessageConfig
                                                            .TEAM_GUI_TEAM_TRANSFER_FAILED_DATABASE_OR_PLAYER_STATUS_MAY_HAVE_CHANGED);
                                            openTargetTeams(admin, uuid, memberName, returnPage);
                                        }
                                    }
                                }));
    }

    private void teleportTeam(@NotNull Player admin, @NotNull String teamName) {
        if (!admitted(admin)) return;
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            openOverview(admin, 0);
            return;
        }
        int online = team.getOnlinePlayers().size();
        team.teleportAllPlayers(admin.getLocation());
        CoreMessages.sendAdminSuccess(
                admin,
                MessageConfig.TEAM_GUI_TEAM_TELEPORTED
                        .replace("%color%", team.getColorCode())
                        .replace("%team%", team.getName())
                        .replace("%count%", String.valueOf(online)));
        success(admin);
        openTeam(admin, teamName, 0);
    }

    private void returnFromConfirmation(@NotNull Player player, @NotNull MenuHolder holder) {
        if (holder.confirmation == Confirmation.TELEPORT_ALL)
            openOverview(player, holder.returnPage);
        else if (holder.confirmation == Confirmation.MOVE_MEMBER)
            openTargetTeams(
                    player, holder.selectedPlayerUuid, holder.memberName, holder.returnPage);
        else openTeam(player, holder.teamName, holder.returnPage);
    }

    private List<ChampionshipTeam> sortedTeams() {
        return plugin.getTeamManager().getTeamList().stream()
                .sorted(
                        Comparator.comparing(
                                ChampionshipTeam::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<MemberView> members(@NotNull ChampionshipTeam team) {
        return team.getTeamMemberEntries().stream()
                .map(
                        entry -> {
                            Player online = Bukkit.getPlayer(entry.getUuid());
                            String name = online == null ? entry.getUsername() : online.getName();
                            return new MemberView(entry.getUuid(), name, online != null);
                        })
                .sorted(
                        Comparator.comparing(MemberView::online)
                                .reversed()
                                .thenComparing(MemberView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static List<Integer> contentSlots() {
        return MenuLayout.range(0, PAGE_SIZE);
    }

    private static void click(@NotNull Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.1F);
    }

    private static void success(@NotNull Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.65F, 1.25F);
    }

    enum Screen {
        OVERVIEW(MenuId.TEAMS_OVERVIEW),
        TEAM(MenuId.TEAMS_MEMBERS),
        ADD_PLAYER(MenuId.TEAMS_ADD_PLAYER),
        KNOWN_PLAYER(MenuId.TEAMS_KNOWN_PLAYER),
        QUICK_PLAYER(MenuId.TEAMS_QUICK_ASSIGN),
        TARGET_TEAM(MenuId.TEAMS_TARGET_TEAM),
        COLOR(MenuId.TEAMS_COLOR_PICKER),
        CONFIRM(MenuId.TEAMS_CONFIRM);

        final MenuId id;

        Screen(MenuId id) {
            this.id = id;
        }
    }

    static MenuLayout layout(Screen screen) {
        int minimumSize = screen == Screen.COLOR ? 36 : screen == Screen.CONFIRM ? 27 : 54;
        List<Integer> content =
                screen == Screen.COLOR
                        ? COLOR_SLOTS
                        : screen == Screen.CONFIRM ? List.of() : contentSlots();
        int size = GuiConfig.menu(screen.id.path(), minimumSize, "", content).size();
        Map<String, Integer> controls =
                switch (screen) {
                    case OVERVIEW ->
                            Map.ofEntries(
                                    Map.entry("create", 45),
                                    Map.entry("quick-assign", 46),
                                    Map.entry("previous", 47),
                                    Map.entry("refresh", 48),
                                    Map.entry("page", 49),
                                    Map.entry("next", 50),
                                    Map.entry("teleport-all", 52),
                                    Map.entry("close", 53));
                    case TEAM ->
                            Map.ofEntries(
                                    Map.entry("add-player", 45),
                                    Map.entry("history", 46),
                                    Map.entry("previous", 47),
                                    Map.entry("back", 47),
                                    Map.entry("refresh", 48),
                                    Map.entry("page", 49),
                                    Map.entry("next", 50),
                                    Map.entry("teleport", 51),
                                    Map.entry("delete", 52),
                                    Map.entry("close", 53));
                    case ADD_PLAYER ->
                            Map.ofEntries(
                                    Map.entry("back", 45),
                                    Map.entry("previous", 47),
                                    Map.entry("page", 49),
                                    Map.entry("next", 50),
                                    Map.entry("close", 53));
                    case KNOWN_PLAYER ->
                            Map.ofEntries(
                                    Map.entry("manual-input", 45),
                                    Map.entry("back", 46),
                                    Map.entry("previous", 47),
                                    Map.entry("page", 49),
                                    Map.entry("next", 50),
                                    Map.entry("close", 53));
                    case QUICK_PLAYER ->
                            Map.ofEntries(
                                    Map.entry("back", 45),
                                    Map.entry("previous", 47),
                                    Map.entry("page", 49),
                                    Map.entry("next", 50),
                                    Map.entry("close", 53));
                    case TARGET_TEAM ->
                            Map.ofEntries(
                                    Map.entry("back", 45),
                                    Map.entry("previous", 47),
                                    Map.entry("selected-player", 49),
                                    Map.entry("next", 50),
                                    Map.entry("close", 53));
                    case COLOR ->
                            Map.ofEntries(
                                    Map.entry("previous", 27),
                                    Map.entry("back", 31),
                                    Map.entry("next", 35));
                    case CONFIRM ->
                            Map.ofEntries(
                                    Map.entry("confirm", 11),
                                    Map.entry("action", 13),
                                    Map.entry("cancel", 15));
                };
        return MenuLayout.load(
                screen.id.path(),
                size,
                content,
                screen == Screen.COLOR || screen == Screen.CONFIRM
                        ? List.of()
                        : MenuLayout.range(36, size),
                controls);
    }

    private enum Confirmation {
        DELETE_TEAM,
        REMOVE_MEMBER,
        TELEPORT_ALL,
        MOVE_MEMBER
    }

    private enum InputPurpose {
        CREATE_TEAM,
        ADD_MEMBER
    }

    private record MemberView(UUID uuid, String name, boolean online) {}

    private final class MenuHolder implements MenuInventory {
        private final TeamManagementMenu owner = TeamManagementMenu.this;
        private final Player player;
        private final ChampionshipTeam team;
        private final Map<String, ChampionshipTeam> teamSnapshot = new HashMap<>();
        private boolean pending;
        private final Screen screen;
        private final String teamName;
        private int page;
        private int pageCount;
        private int count;
        private final int emptySlot;
        private final Map<Integer, String> controls = new HashMap<>();
        private final MenuLayout layout;
        private int returnPage;
        private final Map<Integer, String> targets = new HashMap<>();
        private final Map<Integer, UUID> playerTargets = new HashMap<>();
        private Inventory inventory;
        private Confirmation confirmation;
        private String memberName;
        private UUID selectedPlayerUuid;

        private MenuHolder(Player player, Screen screen, String teamName, int page) {
            this.player = player;
            this.team =
                    screen == Screen.COLOR || teamName == null
                            ? null
                            : plugin.getTeamManager().getTeam(teamName);
            for (ChampionshipTeam entry : plugin.getTeamManager().getTeamList())
                teamSnapshot.put(entry.getName(), entry);
            this.screen = screen;
            this.teamName = teamName;
            this.page = page;
            this.layout = layout(screen);
            this.emptySlot =
                    GuiConfig.slot(
                            screen.id.layout("overlay.empty"),
                            GuiConfig.slot(screen.id.item("empty") + ".slot", 22));
        }

        private boolean currentTeam() {
            return teamName == null
                    || screen == Screen.COLOR
                    || (team != null && plugin.getTeamManager().getTeam(teamName) == team);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}

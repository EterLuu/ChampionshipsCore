package ink.ziip.championshipscore.api.team.gui;

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
                title("队伍管理", NamedTextColor.GOLD));
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
            inventory.setItem(22, item(Material.GRAY_DYE, Component.text("还没有队伍", NamedTextColor.GRAY),
                    List.of(Component.text("点击左下角创建第一支队伍", NamedTextColor.DARK_GRAY)), false));
        }

        fillFooter(inventory);
        inventory.setItem(CREATE_SLOT, item(Material.EMERALD,
                Component.text("创建队伍", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                List.of(Component.text("输入内部名称，再选择队伍颜色", NamedTextColor.GRAY)), false));
        inventory.setItem(QUICK_ASSIGN_SLOT, item(Material.PLAYER_HEAD,
                Component.text("快速调队", NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(Component.text("选择在线玩家，再选择目标队伍", NamedTextColor.GRAY),
                        Component.text("已有队伍的玩家会要求二次确认", NamedTextColor.DARK_GRAY)), false));
        inventory.setItem(REFRESH_SLOT, item(Material.SUNFLOWER,
                Component.text("刷新", NamedTextColor.YELLOW), List.of(), false));
        inventory.setItem(PAGE_SLOT, pageItem(holder.page, pages, teams.size(), "支队伍"));
        if (holder.page > 0) inventory.setItem(PREVIOUS_SLOT, navigationItem("上一页"));
        if (holder.page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem("下一页"));
        inventory.setItem(TELEPORT_ALL_SLOT, item(Material.ENDER_EYE,
                Component.text("传送全部队伍到这里", NamedTextColor.LIGHT_PURPLE),
                List.of(Component.text("仅传送在线成员", NamedTextColor.GRAY),
                        Component.empty(), Component.text("点击后需要确认", NamedTextColor.YELLOW)), false));
        inventory.setItem(CLOSE_SLOT, closeItem());
    }

    private void openTeam(@NotNull Player player, @NotNull String teamName, int requestedPage) {
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            Utils.sendAdminError(player, "队伍已不存在：&#fff566" + teamName);
            openOverview(player, 0);
            return;
        }

        List<MemberView> members = members(team);
        int pages = pageCount(members.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.TEAM, team.getName(), page);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                teamTitle(team, "成员管理"));
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
                    Component.text("队伍中还没有成员", NamedTextColor.GRAY),
                    List.of(Component.text("从左下角添加在线玩家或输入玩家名", NamedTextColor.DARK_GRAY)), false));
        }

        fillFooter(inventory);
        boolean full = members.size() >= CCConfig.TEAM_MAX_MEMBERS;
        if (full) {
            inventory.setItem(ADD_ONLINE_SLOT, item(Material.RED_DYE,
                    Component.text("队伍已满", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    List.of(Component.text(members.size() + " / " + CCConfig.TEAM_MAX_MEMBERS + " 名成员", NamedTextColor.GRAY),
                            Component.text("移除成员后才能继续添加", NamedTextColor.DARK_GRAY)), false));
        } else {
            inventory.setItem(ADD_ONLINE_SLOT, item(Material.LIME_DYE,
                    Component.text("添加在线玩家", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    List.of(Component.text("从当前在线且尚未入队的玩家中选择", NamedTextColor.GRAY)), false));
            inventory.setItem(ADD_HISTORY_SLOT, item(Material.BOOK,
                    Component.text("历史 / 离线玩家", NamedTextColor.AQUA),
                    List.of(Component.text("从已记录且尚未入队的离线玩家中选择", NamedTextColor.GRAY),
                            Component.text("也可在下一页手动输入玩家名", NamedTextColor.DARK_GRAY)), false));
        }
        inventory.setItem(PREVIOUS_SLOT, holder.page > 0 ? navigationItem("上一页")
                : item(Material.ARROW, Component.text("返回队伍列表", NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(REFRESH_SLOT, item(Material.SUNFLOWER,
                Component.text("刷新", NamedTextColor.YELLOW), List.of(), false));
        inventory.setItem(PAGE_SLOT, pageItem(holder.page, pages, members.size(), "名成员"));
        if (holder.page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem("下一页"));
        inventory.setItem(TELEPORT_TEAM_SLOT, item(Material.ENDER_PEARL,
                Component.text("传送本队到这里", NamedTextColor.LIGHT_PURPLE),
                List.of(Component.text(team.getOnlinePlayers().size() + " 名在线成员将被传送", NamedTextColor.GRAY)), false));
        inventory.setItem(DELETE_TEAM_SLOT, item(Material.TNT,
                Component.text("删除队伍", NamedTextColor.RED),
                List.of(Component.text("永久删除队伍和成员关系", NamedTextColor.GRAY),
                        Component.empty(), Component.text("点击后需要确认", NamedTextColor.YELLOW)), false));
        inventory.setItem(CLOSE_SLOT, closeItem());
    }

    private void openAddPlayer(@NotNull Player player, @NotNull String teamName, int requestedPage) {
        ChampionshipTeam team = plugin.getTeamManager().getTeam(teamName);
        if (team == null) {
            openOverview(player, 0);
            return;
        }
        if (team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) {
            Utils.sendAdminError(player, "队伍人数已达到上限 &#fff566" + CCConfig.TEAM_MAX_MEMBERS);
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
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, teamTitle(team, "选择新成员"));
        holder.inventory = inventory;
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, candidates.size());
        for (int index = from; index < to; index++) {
            Player candidate = candidates.get(index);
            int slot = index - from;
            inventory.setItem(slot, playerHead(candidate.getUniqueId(), candidate.getName(),
                    Component.text(candidate.getName(), NamedTextColor.GREEN),
                    List.of(Component.text("点击加入 " + team.getName(), teamColor(team)))));
            holder.targets.put(slot, candidate.getName());
        }
        if (candidates.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE,
                    Component.text("没有可添加的在线玩家", NamedTextColor.GRAY),
                    List.of(Component.text("可以返回后使用“输入玩家名”", NamedTextColor.DARK_GRAY)), false));
        }
        fillFooter(inventory);
        inventory.setItem(BACK_SLOT, item(Material.ARROW, Component.text("返回成员管理", NamedTextColor.WHITE), List.of(), false));
        if (page > 0) inventory.setItem(PREVIOUS_SLOT, navigationItem("上一页"));
        inventory.setItem(PAGE_SLOT, pageItem(page, pages, candidates.size(), "名可选玩家"));
        if (page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem("下一页"));
        inventory.setItem(CLOSE_SLOT, closeItem());
        player.openInventory(inventory);
    }

    private void openColorPicker(@NotNull Player player, @NotNull String newTeamName) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.COLOR, newTeamName, 0);
        Inventory inventory = Bukkit.createInventory(holder, 36, title("选择队伍颜色", NamedTextColor.GOLD));
        holder.inventory = inventory;
        List<String> colors = List.of(Utils.getColorNames());
        for (int index = 0; index < colors.size(); index++) {
            String color = colors.get(index);
            ChampionshipTeam usedBy = sortedTeams().stream()
                    .filter(team -> team.getColorName().equalsIgnoreCase(color))
                    .findFirst().orElse(null);
            Material material = material(color + "_WOOL", Material.WHITE_WOOL);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("内部颜色名  " + color, NamedTextColor.GRAY));
            lore.add(Component.text("显示颜色  " + COLOR_HEX.get(color), color(color)));
            lore.add(Component.empty());
            if (usedBy == null) lore.add(Component.text("点击创建队伍", NamedTextColor.GREEN));
            else lore.add(Component.text("已被 " + usedBy.getName() + " 使用", NamedTextColor.RED));
            int slot = COLOR_SLOTS.get(index);
            inventory.setItem(slot, item(usedBy == null ? material : Material.GRAY_DYE,
                    Component.text(COLOR_LABELS.get(color), usedBy == null ? color(color) : NamedTextColor.DARK_GRAY)
                            .decorate(TextDecoration.BOLD), lore, false));
            if (usedBy == null) holder.targets.put(slot, color);
        }
        inventory.setItem(31, item(Material.ARROW, Component.text("返回队伍列表", NamedTextColor.WHITE), List.of(), false));
        player.openInventory(inventory);
    }

    private void openQuickPlayers(@NotNull Player player, int requestedPage) {
        List<? extends Player> players = Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int pages = pageCount(players.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.QUICK_PLAYER, null, page);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, title("快速调队 · 选择玩家", NamedTextColor.AQUA));
        holder.inventory = inventory;
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, players.size());
        for (int index = from; index < to; index++) {
            Player candidate = players.get(index);
            ChampionshipTeam current = plugin.getTeamManager().getTeamByPlayer(candidate);
            List<Component> lore = new ArrayList<>();
            lore.add(current == null
                    ? Component.text("当前未分队", NamedTextColor.GRAY)
                    : Component.text("当前队伍  ", NamedTextColor.GRAY)
                    .append(Component.text(current.getName(), teamColor(current))));
            lore.add(Component.empty());
            lore.add(Component.text("点击选择目标队伍", NamedTextColor.YELLOW));
            int slot = index - from;
            inventory.setItem(slot, playerHead(candidate.getUniqueId(), candidate.getName(),
                    Component.text(candidate.getName(), NamedTextColor.GREEN).decorate(TextDecoration.BOLD), lore));
            holder.playerTargets.put(slot, candidate.getUniqueId());
            holder.targets.put(slot, candidate.getName());
        }
        fillFooter(inventory);
        inventory.setItem(BACK_SLOT, item(Material.ARROW, Component.text("返回队伍列表", NamedTextColor.WHITE), List.of(), false));
        if (page > 0) inventory.setItem(PREVIOUS_SLOT, navigationItem("上一页"));
        inventory.setItem(PAGE_SLOT, pageItem(page, pages, players.size(), "名在线玩家"));
        if (page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem("下一页"));
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
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, title("选择 " + selectedName + " 的队伍", NamedTextColor.GOLD));
        holder.inventory = inventory;
        for (int index = 0; index < Math.min(PAGE_SIZE, teams.size()); index++) {
            ChampionshipTeam team = teams.get(index);
            boolean same = current != null && current.equals(team);
            boolean full = team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS;
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(team.getMembers().size() + " / " + CCConfig.TEAM_MAX_MEMBERS + " 名成员", NamedTextColor.GRAY));
            lore.add(Component.empty());
            if (same) lore.add(Component.text("当前所在队伍", NamedTextColor.GREEN));
            else if (full) lore.add(Component.text("队伍已满", NamedTextColor.RED));
            else if (current == null) lore.add(Component.text("点击直接加入", NamedTextColor.YELLOW));
            else lore.add(Component.text("点击后确认调队", NamedTextColor.YELLOW));
            inventory.setItem(index, item(material(team.getColorName() + "_WOOL", Material.WHITE_WOOL),
                    Component.text(team.getName(), same || full ? NamedTextColor.DARK_GRAY : teamColor(team))
                            .decorate(TextDecoration.BOLD), lore, same));
            if (!same && !full) holder.targets.put(index, team.getName());
        }
        if (teams.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE, Component.text("还没有可选队伍", NamedTextColor.GRAY), List.of(), false));
        }
        fillFooter(inventory);
        inventory.setItem(BACK_SLOT, item(Material.ARROW, Component.text("返回在线玩家", NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(49, playerHead(selectedUuid, selectedName,
                Component.text(selectedName, NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(current == null ? Component.text("当前未分队", NamedTextColor.GRAY)
                        : Component.text("当前  " + current.getName(), teamColor(current)))));
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
            Utils.sendAdminError(player, "队伍人数已达到上限 &#fff566" + CCConfig.TEAM_MAX_MEMBERS);
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
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, teamTitle(team, "历史 / 离线玩家"));
        holder.inventory = inventory;
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, candidates.size());
        for (int index = from; index < to; index++) {
            PlayerEntry candidate = candidates.get(index);
            int slot = index - from;
            inventory.setItem(slot, playerHead(candidate.getUuid(), candidate.getName(),
                    Component.text(candidate.getName(), NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                    List.of(Component.text("历史玩家 · 当前离线", NamedTextColor.DARK_GRAY),
                            Component.empty(), Component.text("点击加入 " + team.getName(), teamColor(team)))));
            holder.playerTargets.put(slot, candidate.getUuid());
            holder.targets.put(slot, candidate.getName());
        }
        if (candidates.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE, Component.text("没有可选的离线历史玩家", NamedTextColor.GRAY),
                    List.of(Component.text("可以使用左下角手动输入玩家名", NamedTextColor.DARK_GRAY)), false));
        }
        fillFooter(inventory);
        inventory.setItem(MANUAL_INPUT_SLOT, item(Material.NAME_TAG, Component.text("手动输入玩家名", NamedTextColor.AQUA),
                List.of(Component.text("仅在历史列表中找不到玩家时使用", NamedTextColor.GRAY),
                        Component.text("请仔细核对拼写", NamedTextColor.YELLOW)), false));
        inventory.setItem(HISTORY_BACK_SLOT, item(Material.ARROW, Component.text("返回成员管理", NamedTextColor.WHITE), List.of(), false));
        if (page > 0) inventory.setItem(PREVIOUS_SLOT, navigationItem("上一页"));
        inventory.setItem(PAGE_SLOT, pageItem(page, pages, candidates.size(), "名离线玩家"));
        if (page + 1 < pages) inventory.setItem(NEXT_SLOT, navigationItem("下一页"));
        inventory.setItem(CLOSE_SLOT, closeItem());
        player.openInventory(inventory);
    }

    private void openConfirmation(@NotNull Player player, @NotNull Confirmation action, @Nullable String teamName,
                                  @Nullable String memberName, @Nullable UUID selectedPlayerUuid, int returnPage) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), Screen.CONFIRM, teamName, returnPage);
        holder.confirmation = action;
        holder.memberName = memberName;
        holder.selectedPlayerUuid = selectedPlayerUuid;
        Inventory inventory = Bukkit.createInventory(holder, 27, title("确认操作", NamedTextColor.RED));
        holder.inventory = inventory;
        Component subject = switch (action) {
            case DELETE_TEAM -> Component.text("删除队伍 " + teamName, NamedTextColor.RED);
            case REMOVE_MEMBER -> Component.text("移除成员 " + memberName, NamedTextColor.RED);
            case TELEPORT_ALL -> Component.text("传送全部队伍", NamedTextColor.LIGHT_PURPLE);
            case MOVE_MEMBER -> Component.text("将 " + memberName + " 调至 " + teamName, NamedTextColor.GOLD);
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
                Component.text("确认", NamedTextColor.GREEN).decorate(TextDecoration.BOLD), List.of(), false));
        inventory.setItem(CANCEL_SLOT, item(Material.RED_CONCRETE,
                Component.text("取消", NamedTextColor.RED), List.of(), false));
        player.openInventory(inventory);
    }

    private void openTextInput(@NotNull Player player, @NotNull InputPurpose purpose, @Nullable String teamName) {
        String prompt = purpose == InputPurpose.CREATE_TEAM ? "输入队伍内部名称" : "输入要添加的玩家名";
        InputSession previous = inputs.remove(player.getUniqueId());
        if (previous != null) previous.inventory.clear();
        AnvilView view = MenuType.ANVIL.create(player, title(prompt, NamedTextColor.GOLD));
        player.openInventory(view);
        AnvilInventory inventory = view.getTopInventory();
        InputSession session = new InputSession(purpose, teamName, inventory);
        inputs.put(player.getUniqueId(), session);
        inventory.setFirstItem(item(Material.PAPER, Component.text(prompt, NamedTextColor.YELLOW),
                List.of(Component.text("在上方重命名栏输入，再点击右侧结果格", NamedTextColor.GRAY)), false));
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
                Utils.sendAdminError(player, "你已没有使用队伍管理界面的权限。");
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
            Utils.sendAdminError(player, "你已没有使用队伍管理界面的权限。");
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
            Utils.sendAdminError(player, team == null ? "队伍已不存在。"
                    : "队伍人数已达到上限 &#fff566" + CCConfig.TEAM_MAX_MEMBERS);
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
                Utils.sendAdminError(player, "队伍名称已存在：&#fff566" + holder.teamName);
                openOverview(player, 0);
                return;
            }
            boolean colorUsed = plugin.getTeamManager().getTeamList().stream()
                    .anyMatch(team -> team.getColorName().equalsIgnoreCase(selectedColor));
            if (colorUsed) {
                Utils.sendAdminError(player, "该颜色刚刚被其他队伍占用，请重新选择。");
                openColorPicker(player, holder.teamName);
                return;
            }
            boolean created = plugin.getTeamManager().addTeam(holder.teamName, selectedColor, COLOR_HEX.get(selectedColor));
            if (created) {
                Utils.sendAdminSuccess(player, "已创建队伍 " + COLOR_HEX.get(selectedColor) + holder.teamName);
                success(player);
                openTeam(player, holder.teamName, 0);
            } else {
                Utils.sendAdminError(player, "创建队伍失败，名称或颜色可能已被占用。");
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
                    Utils.sendAdminError(player, "队伍已不存在：&#fff566" + holder.teamName);
                } else if (plugin.getTeamManager().deleteTeam(holder.teamName)) {
                    Utils.sendAdminSuccess(player, "已删除队伍 &#fff566" + holder.teamName);
                    success(player);
                } else {
                    Utils.sendAdminError(player, "无法删除队伍；队伍可能正在游戏中。");
                }
                openOverview(player, 0);
            }
            case REMOVE_MEMBER -> {
                if (plugin.getTeamManager().deleteTeamMember(holder.memberName, holder.teamName)) {
                    Utils.sendAdminSuccess(player, "已从 &#fff566" + holder.teamName + " &#ededed移除玩家 &#fff566" + holder.memberName);
                    success(player);
                } else {
                    Utils.sendAdminError(player, "移除失败；队伍或成员状态可能已经改变。");
                }
                openTeam(player, holder.teamName, holder.page);
            }
            case TELEPORT_ALL -> {
                int players = 0;
                for (ChampionshipTeam team : plugin.getTeamManager().getTeamList()) {
                    players += team.getOnlinePlayers().size();
                    team.teleportAllPlayers(player.getLocation());
                }
                Utils.sendAdminSuccess(player, "已将全部队伍的 &#fff566" + players + " &#ededed名在线成员传送到当前位置。");
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
                Utils.sendAdminError(player, "队伍名称不能为空。");
                return;
            }
            if (text.length() > 64 || text.chars().anyMatch(Character::isISOControl)) {
                Utils.sendAdminError(player, "队伍名称不能超过 64 个字符，也不能包含控制字符。");
                return;
            }
            boolean exists = plugin.getTeamManager().getTeamList().stream()
                    .anyMatch(team -> team.getName().equalsIgnoreCase(text));
            if (exists) {
                Utils.sendAdminError(player, "队伍名称已存在：&#fff566" + text);
                return;
            }
            finishInput(player, input);
            openColorPicker(player, text);
            return;
        }

        if (!text.matches("[A-Za-z0-9_]{1,16}")) {
            Utils.sendAdminError(player, "请输入有效的 Minecraft 玩家名（1–16 位字母、数字或下划线）。");
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
            Utils.sendAdminError(admin, "队伍已不存在：&#fff566" + teamName);
            openOverview(admin, 0);
            return;
        }
        boolean added = false;
        if (team.getMembers().size() >= CCConfig.TEAM_MAX_MEMBERS) {
            Utils.sendAdminError(admin, "队伍人数已达到上限 &#fff566" + CCConfig.TEAM_MAX_MEMBERS);
        } else if (plugin.getTeamManager().addTeamMember(memberName, team)) {
            Utils.sendAdminSuccess(admin, "已将玩家 &#fff566" + memberName + " &#ededed加入 " + team.getColorCode() + team.getName());
            success(admin);
            added = true;
        } else {
            Utils.sendAdminError(admin, "添加失败；玩家可能已在队伍中，或存在同名身份冲突。");
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
            Utils.sendAdminError(admin, "队伍人数已达到上限 &#fff566" + CCConfig.TEAM_MAX_MEMBERS);
            openTeam(admin, teamName, 0);
        } else if (plugin.getTeamManager().addTeamMember(memberName, team)) {
            Utils.sendAdminSuccess(admin, "已将离线玩家 &#fff566" + memberName + " &#ededed加入 "
                    + team.getColorCode() + team.getName());
            success(admin);
            if (team.getMembers().size() < CCConfig.TEAM_MAX_MEMBERS) openKnownPlayers(admin, teamName, selectorPage);
            else openTeam(admin, teamName, 0);
        } else {
            Utils.sendAdminError(admin, "添加失败；玩家可能刚刚被分队，或存在身份冲突。");
            openKnownPlayers(admin, teamName, selectorPage);
        }
    }

    private void moveMember(@NotNull Player admin, @NotNull UUID uuid, @NotNull String memberName,
                            @NotNull String targetTeamName, int returnPage) {
        ChampionshipTeam target = plugin.getTeamManager().getTeam(targetTeamName);
        if (target == null) {
            Utils.sendAdminError(admin, "目标队伍已不存在：&#fff566" + targetTeamName);
            openQuickPlayers(admin, returnPage);
            return;
        }
        TeamManager.MemberMoveResult result = plugin.getTeamManager().moveTeamMember(uuid, memberName, target);
        switch (result) {
            case SUCCESS -> {
                Utils.sendAdminSuccess(admin, "已将玩家 &#fff566" + memberName + " &#ededed调至 "
                        + target.getColorCode() + target.getName());
                success(admin);
                openQuickPlayers(admin, returnPage);
            }
            case SAME_TEAM -> {
                Utils.sendAdminError(admin, "玩家已经在该队伍中。");
                openTargetTeams(admin, uuid, memberName, returnPage);
            }
            case TARGET_FULL -> {
                Utils.sendAdminError(admin, "目标队伍人数已满。");
                openTargetTeams(admin, uuid, memberName, returnPage);
            }
            case TEAM_ACTIVE -> {
                Utils.sendAdminError(admin, "玩家当前队伍或目标队伍正在游戏中，不能调队。");
                openTargetTeams(admin, uuid, memberName, returnPage);
            }
            case INVALID_PLAYER, FAILED -> {
                Utils.sendAdminError(admin, "调队失败，数据库或玩家身份状态可能已改变。");
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
        Utils.sendAdminSuccess(admin, "已将 " + team.getColorCode() + team.getName()
                + " &#ededed的 &#fff566" + online + " &#ededed名在线成员传送到当前位置。");
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
        lore.add(Component.text("在线  ", NamedTextColor.GRAY)
                .append(Component.text(online + " / " + members, online > 0 ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
        lore.add(Component.text("颜色  ", NamedTextColor.GRAY)
                .append(Component.text(COLOR_LABELS.getOrDefault(team.getColorName().toLowerCase(Locale.ROOT), team.getColorName()), teamColor(team))));
        lore.add(Component.text("内部 ID  ", NamedTextColor.GRAY).append(Component.text(team.getId(), NamedTextColor.WHITE)));
        lore.add(Component.empty());
        lore.add(Component.text("点击管理成员与队伍", NamedTextColor.YELLOW));
        return item(material(team.getColorName() + "_WOOL", Material.WHITE_WOOL),
                Component.text(team.getName(), teamColor(team)).decorate(TextDecoration.BOLD), lore, false);
    }

    private ItemStack memberItem(@NotNull MemberView member) {
        NamedTextColor status = member.online ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        return playerHead(member.uuid, member.name,
                Component.text(member.name, status).decorate(TextDecoration.BOLD),
                List.of(Component.text(member.online ? "● 在线" : "○ 离线", status),
                        Component.empty(), Component.text("点击移出队伍", NamedTextColor.RED)));
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
                    Component.text("队伍及全部成员关系会被永久删除", NamedTextColor.GRAY),
                    Component.text("正在参加游戏的队伍无法删除", NamedTextColor.DARK_GRAY));
            case REMOVE_MEMBER -> List.of(Component.text("玩家将立即失去该队伍身份", NamedTextColor.GRAY));
            case TELEPORT_ALL -> List.of(
                    Component.text("所有队伍的在线成员会到达你的位置", NamedTextColor.GRAY),
                    Component.text("当前所在场地：" + (teamName == null ? "管理员位置" : teamName), NamedTextColor.DARK_GRAY));
            case MOVE_MEMBER -> List.of(
                    Component.text("旧队伍关系会在数据库事务中原子替换", NamedTextColor.GRAY),
                    Component.text("任一队伍正在比赛时会拒绝操作", NamedTextColor.DARK_GRAY));
        };
    }

    private static void fillFooter(@NotNull Inventory inventory) {
        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int slot = 36; slot < inventory.getSize(); slot++) inventory.setItem(slot, border);
    }

    private static ItemStack pageItem(int page, int pages, int count, @NotNull String unit) {
        return item(Material.PAPER,
                Component.text("第 " + (page + 1) + " / " + pages + " 页", NamedTextColor.AQUA),
                List.of(Component.text("共 " + count + " " + unit, NamedTextColor.GRAY)), false);
    }

    private static ItemStack navigationItem(@NotNull String label) {
        return item(Material.ARROW, Component.text(label, NamedTextColor.WHITE), List.of(), false);
    }

    private static ItemStack closeItem() {
        return item(Material.BARRIER, Component.text("关闭", NamedTextColor.RED), List.of(), false);
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
        return Component.text(team.getName(), teamColor(team)).append(Component.text(" · " + suffix, NamedTextColor.WHITE))
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
                Map.entry("white", "白色"), Map.entry("orange", "橙色"), Map.entry("magenta", "品红色"),
                Map.entry("light_blue", "淡蓝色"), Map.entry("yellow", "黄色"), Map.entry("lime", "黄绿色"),
                Map.entry("pink", "粉红色"), Map.entry("gray", "灰色"), Map.entry("light_gray", "淡灰色"),
                Map.entry("cyan", "青色"), Map.entry("purple", "紫色"), Map.entry("blue", "蓝色"),
                Map.entry("brown", "棕色"), Map.entry("green", "绿色"), Map.entry("red", "红色"),
                Map.entry("black", "黑色"));
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

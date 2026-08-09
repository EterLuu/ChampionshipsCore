package ink.ziip.championshipscore.api.game.spectate;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartArea;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartBase;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.instance.paired.BasePairedGameInstance;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Live selector for every currently running game instance, including replicated paired arenas. */
public final class SpectateMenu implements Listener {
    private static final int INVENTORY_SIZE = 54;
    private static final int PAGE_SIZE = 36;
    private static final int CURRENT_SLOT = 4;
    private static final int LEAVE_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int REFRESH_SLOT = 49;
    private static final int PAGE_SLOT = 50;
    private static final int NEXT_SLOT = 52;
    private static final int CLOSE_SLOT = 53;
    private static final Map<GameTypeEnum, GameStyle> GAME_STYLES = createGameStyles();

    private final ChampionshipsCore plugin;
    private final GameManager manager;
    private BukkitTask refreshTask;

    public SpectateMenu(@NotNull ChampionshipsCore plugin, @NotNull GameManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOpenMenus, 20L, 20L);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        closeAll();
        HandlerList.unregisterAll(this);
    }

    public void open(@NotNull Player player) {
        Holder holder = new Holder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                Component.text("选择观战场地", NamedTextColor.AQUA)
                        .decorate(TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
        holder.inventory = inventory;
        refresh(holder);
        player.openInventory(inventory);
    }

    private void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof Holder holder) refresh(holder);
            else if (top.getHolder() instanceof BuildMartHolder holder) refreshBuildMart(holder);
        }
    }

    private void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof Holder || holder instanceof BuildMartHolder) {
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof BuildMartHolder holder) {
            handleBuildMartClick(event, holder);
            return;
        }
        if (!(top.getHolder() instanceof Holder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != top) return;
        if (!holder.viewer.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == REFRESH_SLOT) {
            refresh(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.1F);
            return;
        }
        if (slot == PREVIOUS_SLOT && holder.page > 0) {
            holder.page--;
            refresh(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1F);
            return;
        }
        if (slot == NEXT_SLOT && holder.page + 1 < holder.pageCount) {
            holder.page++;
            refresh(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1F);
            return;
        }
        if (slot == LEAVE_SLOT) {
            if (manager.leaveSpectating(player)) {
                player.sendMessage(MessageConfig.SPECTATOR_LEAVING_AREA);
                player.closeInventory();
            } else {
                player.sendMessage(MessageConfig.SPECTATOR_CANT_LEAVING_AREA);
            }
            return;
        }

        BaseGameInstance target = holder.instancesBySlot.get(slot);
        if (target == null) return;
        if (!manager.canManuallySpectate(player)) return;
        if (target instanceof BuildMartArea buildMart) {
            openBuildMart(player, buildMart);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
            return;
        }
        if (!manager.selectSpectatorArea(player, target)) {
            Utils.sendAdminError(player, "该场地已结束或当前不可观战");
            refresh(holder);
            return;
        }

        player.sendMessage(MessageConfig.SPECTATOR_JOIN_AREA
                .replace("%game%", target.getGameTypeEnum().toString())
                .replace("%area%", displayAreaName(target)));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof Holder || holder instanceof BuildMartHolder) event.setCancelled(true);
    }

    private void openBuildMart(@NotNull Player player, @NotNull BuildMartArea area) {
        BuildMartHolder holder = new BuildMartHolder(player.getUniqueId(), area);
        holder.inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                Component.text("选择建材集市观战位置", NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        refreshBuildMart(holder);
        player.openInventory(holder.inventory);
    }

    private void handleBuildMartClick(@NotNull InventoryClickEvent event, @NotNull BuildMartHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!holder.viewer.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == LEAVE_SLOT) {
            open(player);
            return;
        }
        if (slot == REFRESH_SLOT) {
            refreshBuildMart(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.1F);
            return;
        }
        if (slot == PREVIOUS_SLOT && holder.page > 0) {
            holder.page--;
            refreshBuildMart(holder);
            return;
        }
        if (slot == NEXT_SLOT && holder.page + 1 < holder.pageCount) {
            holder.page++;
            refreshBuildMart(holder);
            return;
        }

        BuildMartDestination destination = holder.destinationsBySlot.get(slot);
        if (destination == null || !manager.canManuallySpectate(player)) return;
        if (!manager.selectSpectatorArea(player, holder.area, destination.location())) {
            Utils.sendAdminError(player, "该场地已结束或当前不可观战");
            open(player);
            return;
        }
        player.sendMessage(MessageConfig.SPECTATOR_JOIN_AREA
                .replace("%game%", holder.area.getGameTypeEnum().toString())
                .replace("%area%", displayAreaName(holder.area) + " · " + destination.label()));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
        player.closeInventory();
    }

    private void refreshBuildMart(@NotNull BuildMartHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.destinationsBySlot.clear();

        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, border);
        for (int slot = 45; slot < INVENTORY_SIZE; slot++) inventory.setItem(slot, border);

        List<BuildMartDestination> destinations = buildMartDestinations(holder.area);
        holder.pageCount = Math.max(1, (destinations.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        holder.page = Math.max(0, Math.min(holder.page, holder.pageCount - 1));
        inventory.setItem(CURRENT_SLOT, item(Material.CRAFTING_TABLE,
                Component.text("建材集市 · " + displayAreaName(holder.area), NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD),
                List.of(Component.text(destinations.size() + " 个观战位置", NamedTextColor.GRAY)), false));

        int from = holder.page * PAGE_SIZE;
        int to = Math.min(destinations.size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            int slot = 9 + index - from;
            BuildMartDestination destination = destinations.get(index);
            inventory.setItem(slot, item(destination.material(), destination.name(), destination.lore(), false));
            holder.destinationsBySlot.put(slot, destination);
        }

        inventory.setItem(LEAVE_SLOT, item(Material.ARROW,
                Component.text("返回场地列表", NamedTextColor.WHITE), List.of(), false));
        if (holder.page > 0) inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW,
                Component.text("上一页", NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(REFRESH_SLOT, item(Material.CLOCK,
                Component.text("刷新", NamedTextColor.YELLOW), List.of(), false));
        inventory.setItem(PAGE_SLOT, item(Material.PAPER,
                Component.text("第 " + (holder.page + 1) + " / " + holder.pageCount + " 页", NamedTextColor.AQUA),
                List.of(Component.text(destinations.size() + " 个位置", NamedTextColor.GRAY)), false));
        if (holder.page + 1 < holder.pageCount) inventory.setItem(NEXT_SLOT, item(Material.ARROW,
                Component.text("下一页", NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER,
                Component.text("关闭", NamedTextColor.RED), List.of(), false));
    }

    private static List<BuildMartDestination> buildMartDestinations(@NotNull BuildMartArea area) {
        List<BuildMartDestination> destinations = new ArrayList<>();
        destinations.add(new BuildMartDestination("资源大厅", Component.text("资源大厅", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD), Material.CHEST, area.getSpectatorSpawnLocation(),
                List.of(Component.text("公共材料区", NamedTextColor.GRAY))));

        List<ChampionshipTeam> teams = area.getGameTeams();
        for (int index = 0; index < teams.size(); index++) {
            ChampionshipTeam team = teams.get(index);
            Integer assignedSeat = area.seatOf(team);
            int seat = assignedSeat == null ? index : assignedSeat;
            BuildMartBase base = area.cachedBaseForSeat(seat);
            if (base == null) base = area.getGameConfig().getSeatBase(seat);
            if (base == null || base.getPortalPoint() == null) continue;
            Material material = Material.getMaterial(team.getColorName().toUpperCase(Locale.ROOT) + "_WOOL");
            if (material == null) material = Material.WHITE_WOOL;
            destinations.add(new BuildMartDestination(team.getName() + "基地",
                    teamName(team).append(Component.text("基地", NamedTextColor.WHITE))
                            .decorate(TextDecoration.BOLD), material, base.getPortalPoint(),
                    List.of(Component.text("队伍建筑区", NamedTextColor.GRAY))));
        }
        return destinations;
    }

    private void refresh(@NotNull Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.instancesBySlot.clear();

        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, border);
        for (int slot = 45; slot < INVENTORY_SIZE; slot++) inventory.setItem(slot, border);

        List<BaseGameInstance> instances = manager.getSpectatableInstances();
        holder.pageCount = Math.max(1, (instances.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        holder.page = Math.max(0, Math.min(holder.page, holder.pageCount - 1));

        BaseGameInstance current = manager.getPlayerSpectatorStatus(holder.viewer);
        inventory.setItem(CURRENT_SLOT, currentStatusItem(current, instances.size()));

        int from = holder.page * PAGE_SIZE;
        int to = Math.min(instances.size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            int slot = 9 + index - from;
            BaseGameInstance instance = instances.get(index);
            inventory.setItem(slot, instanceItem(instance, instance == current));
            holder.instancesBySlot.put(slot, instance);
        }

        if (instances.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE,
                    Component.text("暂无进行中的场地", NamedTextColor.GRAY),
                    List.of(Component.text("比赛开始后将在这里显示", NamedTextColor.DARK_GRAY)), false));
        }

        if (current != null) {
            inventory.setItem(LEAVE_SLOT, item(Material.REDSTONE,
                    Component.text("退出观战", NamedTextColor.RED),
                    List.of(Component.text(displayAreaName(current), NamedTextColor.GRAY)), false));
        }
        if (holder.page > 0) {
            inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW,
                    Component.text("上一页", NamedTextColor.WHITE), List.of(), false));
        }
        inventory.setItem(REFRESH_SLOT, item(Material.CLOCK,
                Component.text("刷新", NamedTextColor.YELLOW),
                List.of(Component.text("场地状态每秒自动更新", NamedTextColor.DARK_GRAY)), false));
        inventory.setItem(PAGE_SLOT, item(Material.PAPER,
                Component.text("第 " + (holder.page + 1) + " / " + holder.pageCount + " 页", NamedTextColor.AQUA),
                List.of(Component.text(instances.size() + " 个场地正在运行", NamedTextColor.GRAY)), false));
        if (holder.page + 1 < holder.pageCount) {
            inventory.setItem(NEXT_SLOT, item(Material.ARROW,
                    Component.text("下一页", NamedTextColor.WHITE), List.of(), false));
        }
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER,
                Component.text("关闭", NamedTextColor.RED), List.of(), false));
    }

    private ItemStack currentStatusItem(BaseGameInstance current, int activeCount) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("进行中场地  ", NamedTextColor.GRAY)
                .append(Component.text(activeCount, NamedTextColor.GREEN)));
        if (current == null) {
            lore.add(Component.text("当前未在观战", NamedTextColor.DARK_GRAY));
        } else {
            lore.add(Component.text("当前  ", NamedTextColor.GRAY)
                    .append(Component.text(current.getGameTypeEnum().toString(), NamedTextColor.AQUA))
                    .append(Component.text(" · " + displayAreaName(current), NamedTextColor.WHITE)));
        }
        return item(Material.SPYGLASS,
                Component.text("观战大厅", NamedTextColor.AQUA).decorate(TextDecoration.BOLD), lore, false);
    }

    private ItemStack instanceItem(@NotNull BaseGameInstance instance, boolean selected) {
        GameStyle style = GAME_STYLES.getOrDefault(instance.getGameTypeEnum(),
                new GameStyle(Material.ENDER_EYE, NamedTextColor.WHITE));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("场地  ", NamedTextColor.GRAY)
                .append(Component.text(displayAreaName(instance), NamedTextColor.WHITE)));
        lore.add(Component.text("阶段  ", NamedTextColor.GRAY)
                .append(Component.text(instance.getGameStageEnum().toString(), stageColor(instance.getGameStageEnum()))));
        lore.add(Component.text("观众  ", NamedTextColor.GRAY)
                .append(Component.text(instance.getOnlineSpectators().size(), NamedTextColor.AQUA)));
        lore.add(Component.empty());
        appendTeams(lore, instance);
        if (selected) {
            lore.add(Component.empty());
            lore.add(Component.text("正在旁观", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        }

        Component name = Component.text(instance.getGameTypeEnum().toString(), style.color)
                .append(Component.text(" · " + displayAreaName(instance), NamedTextColor.WHITE))
                .decorate(TextDecoration.BOLD);
        return item(style.material, name, lore, selected);
    }

    private void appendTeams(@NotNull List<Component> lore, @NotNull BaseGameInstance instance) {
        if (instance instanceof BasePairedGameInstance paired) {
            ChampionshipTeam right = paired.getRightChampionshipTeam();
            ChampionshipTeam left = paired.getLeftChampionshipTeam();
            lore.add(Component.text("本场对战", NamedTextColor.GOLD));
            lore.add(teamName(right)
                    .append(Component.text("  VS  ", NamedTextColor.DARK_GRAY))
                    .append(teamName(left)));
            return;
        }
        if (instance instanceof BaseMultiTeamGameInstance multiTeam) {
            List<ChampionshipTeam> teams = multiTeam.getGameTeams();
            lore.add(Component.text("参赛队伍", NamedTextColor.GOLD));
            if (teams.isEmpty()) {
                lore.add(Component.text("等待队伍数据", NamedTextColor.DARK_GRAY));
                return;
            }
            for (int index = 0; index < teams.size(); index += 2) {
                Component line = teamName(teams.get(index));
                if (index + 1 < teams.size()) {
                    line = line.append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                            .append(teamName(teams.get(index + 1)));
                }
                lore.add(line);
            }
        }
    }

    private static Component teamName(ChampionshipTeam team) {
        if (team == null) return Component.text("待定", NamedTextColor.DARK_GRAY);
        TextColor color = TextColor.fromHexString(team.getColorCode());
        return Component.text(team.getName(), color == null ? NamedTextColor.WHITE : color);
    }

    private static String displayAreaName(@NotNull BaseGameInstance instance) {
        String name = instance.getGameConfig().getAreaName();
        if (name == null || name.isBlank()) name = instance.getGameConfig().getConfigName();
        if (instance instanceof ParkourTagArea parkourTag) {
            return name + " · 分区 " + (parkourTag.getCopyIndex() + 1);
        }
        if (instance instanceof BattleBoxArea battleBox) {
            return name + " · 分区 " + (battleBox.getCopyIndex() + 1);
        }
        return name;
    }

    private static NamedTextColor stageColor(GameStageEnum stage) {
        return switch (stage) {
            case LOADING -> NamedTextColor.YELLOW;
            case PREPARATION -> NamedTextColor.GOLD;
            case COUNTDOWN -> NamedTextColor.LIGHT_PURPLE;
            case PROGRESS -> NamedTextColor.GREEN;
            default -> NamedTextColor.GRAY;
        };
    }

    private static ItemStack item(Material material, Component name, List<Component> lore, boolean glint) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream()
                    .map(line -> line.decoration(TextDecoration.ITALIC, false))
                    .toList());
            meta.setEnchantmentGlintOverride(glint);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Map<GameTypeEnum, GameStyle> createGameStyles() {
        Map<GameTypeEnum, GameStyle> styles = new EnumMap<>(GameTypeEnum.class);
        styles.put(GameTypeEnum.Bingo, new GameStyle(Material.FILLED_MAP, NamedTextColor.LIGHT_PURPLE));
        styles.put(GameTypeEnum.ParkourTag, new GameStyle(Material.GOLDEN_CARROT, NamedTextColor.AQUA));
        styles.put(GameTypeEnum.BattleBox, new GameStyle(Material.WHITE_WOOL, NamedTextColor.GOLD));
        styles.put(GameTypeEnum.TNTRun, new GameStyle(Material.TNT, NamedTextColor.RED));
        styles.put(GameTypeEnum.SnowballShowdown, new GameStyle(Material.SNOWBALL, NamedTextColor.WHITE));
        styles.put(GameTypeEnum.SkyWars, new GameStyle(Material.GRASS_BLOCK, NamedTextColor.YELLOW));
        styles.put(GameTypeEnum.TGTTOS, new GameStyle(Material.FEATHER, NamedTextColor.LIGHT_PURPLE));
        styles.put(GameTypeEnum.DragonEggCarnival, new GameStyle(Material.DRAGON_EGG, NamedTextColor.DARK_PURPLE));
        styles.put(GameTypeEnum.ParkourWarrior, new GameStyle(Material.IRON_BOOTS, NamedTextColor.WHITE));
        styles.put(GameTypeEnum.HotyCodyDusky, new GameStyle(Material.COD, NamedTextColor.AQUA));
        styles.put(GameTypeEnum.BuildMart, new GameStyle(Material.CRAFTING_TABLE, NamedTextColor.GOLD));
        styles.put(GameTypeEnum.Dodgebolt, new GameStyle(Material.ARROW, NamedTextColor.RED));
        styles.put(GameTypeEnum.AceRace, new GameStyle(Material.ELYTRA, NamedTextColor.GREEN));
        return Map.copyOf(styles);
    }

    private record GameStyle(Material material, NamedTextColor color) {
    }

    private static final class Holder implements InventoryHolder {
        private final UUID viewer;
        private final Map<Integer, BaseGameInstance> instancesBySlot = new HashMap<>();
        private Inventory inventory;
        private int page;
        private int pageCount = 1;

        private Holder(UUID viewer) {
            this.viewer = viewer;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private record BuildMartDestination(String label, Component name, Material material,
                                        Location location, List<Component> lore) {
    }

    private static final class BuildMartHolder implements InventoryHolder {
        private final UUID viewer;
        private final BuildMartArea area;
        private final Map<Integer, BuildMartDestination> destinationsBySlot = new HashMap<>();
        private Inventory inventory;
        private int page;
        private int pageCount = 1;

        private BuildMartHolder(UUID viewer, BuildMartArea area) {
            this.viewer = viewer;
            this.area = area;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}

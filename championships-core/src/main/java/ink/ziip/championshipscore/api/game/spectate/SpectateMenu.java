package ink.ziip.championshipscore.api.game.spectate;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartArea;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartBase;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.instance.paired.BasePairedGameInstance;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownTeamArea;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunTeamArea;
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
import org.bukkit.configuration.ConfigurationSection;
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
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-001"), NamedTextColor.AQUA)
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
            else if (top.getHolder() instanceof SubArenaHolder holder) refreshSubArenas(holder);
        }
    }

    private void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof Holder || holder instanceof BuildMartHolder || holder instanceof SubArenaHolder) {
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
        if (top.getHolder() instanceof SubArenaHolder holder) {
            handleSubArenaClick(event, holder);
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
        if (slot == CURRENT_SLOT && manager.getSpectatorManager().areaOf(holder.viewer) != null) {
            manager.openSpectatorControls(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
            return;
        }
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
        List<SubArenaDestination> subArenas = subArenaDestinations(target);
        if (subArenas.size() > 1) {
            openSubArenas(player, target);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
            return;
        }
        if (!manager.selectSpectatorArea(player, target)) {
            Utils.sendAdminError(player, GuiConfig.text("game-spectate-spectatemenu.text-002"));
            refresh(holder);
            return;
        }

        player.sendMessage(MessageConfig.SPECTATOR_JOIN_AREA
                .replace("%game%", target.getGameTypeEnum().toString())
                .replace("%area%", manager.getSpectatorDisplayName(target)));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof Holder || holder instanceof BuildMartHolder || holder instanceof SubArenaHolder)
            event.setCancelled(true);
    }

    private void openSubArenas(@NotNull Player player, @NotNull BaseGameInstance area) {
        SubArenaHolder holder = new SubArenaHolder(player.getUniqueId(), area);
        holder.inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-003"), NamedTextColor.AQUA)
                        .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        refreshSubArenas(holder);
        player.openInventory(holder.inventory);
    }

    private void handleSubArenaClick(@NotNull InventoryClickEvent event, @NotNull SubArenaHolder holder) {
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
            refreshSubArenas(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.1F);
            return;
        }
        if (slot == PREVIOUS_SLOT && holder.page > 0) {
            holder.page--;
            refreshSubArenas(holder);
            return;
        }
        if (slot == NEXT_SLOT && holder.page + 1 < holder.pageCount) {
            holder.page++;
            refreshSubArenas(holder);
            return;
        }

        SubArenaDestination destination = holder.destinationsBySlot.get(slot);
        if (destination == null || !manager.canManuallySpectate(player)) return;
        if (!manager.selectSpectatorArea(player, holder.area, destination.location())) {
            Utils.sendAdminError(player, GuiConfig.text("game-spectate-spectatemenu.text-002"));
            open(player);
            return;
        }
        player.sendMessage(MessageConfig.SPECTATOR_JOIN_AREA
                .replace("%game%", holder.area.getGameTypeEnum().toString())
                .replace("%area%", manager.getSpectatorDisplayName(holder.area) + GuiConfig.text("common.separator") + destination.label()));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
        player.closeInventory();
    }

    private void refreshSubArenas(@NotNull SubArenaHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.destinationsBySlot.clear();
        ItemStack border = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, border);
        for (int slot = 45; slot < INVENTORY_SIZE; slot++) inventory.setItem(slot, border);

        List<SubArenaDestination> destinations = subArenaDestinations(holder.area);
        holder.pageCount = Math.max(1, (destinations.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        holder.page = Math.max(0, Math.min(holder.page, holder.pageCount - 1));
        GameStyle style = GAME_STYLES.getOrDefault(holder.area.getGameTypeEnum(),
                new GameStyle(Material.ENDER_EYE, NamedTextColor.WHITE));
        inventory.setItem(CURRENT_SLOT, item(style.material(),
                Component.text(holder.area.getGameTypeEnum() + GuiConfig.text("common.separator")
                                + manager.getSpectatorDisplayName(holder.area), style.color())
                        .decorate(TextDecoration.BOLD),
                List.of(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-004"), NamedTextColor.GRAY)), false));

        int from = holder.page * PAGE_SIZE;
        int to = Math.min(destinations.size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            int slot = 9 + index - from;
            SubArenaDestination destination = destinations.get(index);
            inventory.setItem(slot, item(destination.material(),
                    Component.text(destination.label(), NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                    List.of(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-005"), NamedTextColor.GRAY)), false));
            holder.destinationsBySlot.put(slot, destination);
        }

        inventory.setItem(LEAVE_SLOT, item(Material.ARROW,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-006"), NamedTextColor.WHITE), List.of(), false));
        if (holder.page > 0) inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-007"), NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(REFRESH_SLOT, item(Material.CLOCK,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-008"), NamedTextColor.YELLOW), List.of(), false));
        inventory.setItem(PAGE_SLOT, item(Material.PAPER,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-009") + (holder.page + 1) + "/" + holder.pageCount + GuiConfig.text("game-spectate-spectatemenu.text-010"), NamedTextColor.AQUA),
                List.of(Component.text(destinations.size() + GuiConfig.text("game-spectate-spectatemenu.text-011"), NamedTextColor.GRAY)), false));
        if (holder.page + 1 < holder.pageCount) inventory.setItem(NEXT_SLOT, item(Material.ARROW,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-012"), NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-013"), NamedTextColor.RED), List.of(), false));
    }

    private static @NotNull List<SubArenaDestination> subArenaDestinations(
            @NotNull BaseGameInstance instance) {
        List<SubArenaDestination> destinations = new ArrayList<>();
        if (instance instanceof TNTRunTeamArea tntRun) {
            List<String> spawns = tntRun.getGameConfig().getPlayerSpawnPoints();
            if (spawns == null) return List.of();
            for (int index = 0; index < spawns.size(); index++) {
                Location location = safeLocation(spawns.get(index));
                if (location != null) destinations.add(new SubArenaDestination(
                        GuiConfig.text("game-spectate-spectatemenu.text-014") + (index + 1), Material.TNT, location));
            }
        } else if (instance instanceof SnowballShowdownTeamArea snowball) {
            ConfigurationSection section = snowball.getGameConfig().getPlayerSpawnPoints();
            if (section == null) return List.of();
            List<String> names = new ArrayList<>(section.getKeys(false));
            names.sort(String.CASE_INSENSITIVE_ORDER);
            for (String name : names) {
                Location location = section.getStringList(name).stream()
                        .map(SpectateMenu::safeLocation).filter(java.util.Objects::nonNull)
                        .findFirst().orElse(null);
                if (location != null)
                    destinations.add(new SubArenaDestination(name, Material.SNOWBALL, location));
            }
        }
        return List.copyOf(destinations);
    }

    private static Location safeLocation(String configured) {
        try {
            Location location = Utils.getLocation(configured);
            return location != null && location.getWorld() != null ? location : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void openBuildMart(@NotNull Player player, @NotNull BuildMartArea area) {
        BuildMartHolder holder = new BuildMartHolder(player.getUniqueId(), area);
        holder.inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-015"), NamedTextColor.GOLD)
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
            Utils.sendAdminError(player, GuiConfig.text("game-spectate-spectatemenu.text-002"));
            open(player);
            return;
        }
        player.sendMessage(MessageConfig.SPECTATOR_JOIN_AREA
                .replace("%game%", holder.area.getGameTypeEnum().toString())
                .replace("%area%", manager.getSpectatorDisplayName(holder.area) + GuiConfig.text("common.separator") + destination.label()));
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
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-016") + manager.getSpectatorDisplayName(holder.area), NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD),
                List.of(Component.text(destinations.size() + GuiConfig.text("game-spectate-spectatemenu.text-017"), NamedTextColor.GRAY)), false));

        int from = holder.page * PAGE_SIZE;
        int to = Math.min(destinations.size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            int slot = 9 + index - from;
            BuildMartDestination destination = destinations.get(index);
            inventory.setItem(slot, item(destination.material(), destination.name(), destination.lore(), false));
            holder.destinationsBySlot.put(slot, destination);
        }

        inventory.setItem(LEAVE_SLOT, item(Material.ARROW,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-006"), NamedTextColor.WHITE), List.of(), false));
        if (holder.page > 0) inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-007"), NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(REFRESH_SLOT, item(Material.CLOCK,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-008"), NamedTextColor.YELLOW), List.of(), false));
        inventory.setItem(PAGE_SLOT, item(Material.PAPER,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-009") + (holder.page + 1) + "/" + holder.pageCount + GuiConfig.text("game-spectate-spectatemenu.text-010"), NamedTextColor.AQUA),
                List.of(Component.text(destinations.size() + GuiConfig.text("game-spectate-spectatemenu.text-018"), NamedTextColor.GRAY)), false));
        if (holder.page + 1 < holder.pageCount) inventory.setItem(NEXT_SLOT, item(Material.ARROW,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-012"), NamedTextColor.WHITE), List.of(), false));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-013"), NamedTextColor.RED), List.of(), false));
    }

    private static List<BuildMartDestination> buildMartDestinations(@NotNull BuildMartArea area) {
        List<BuildMartDestination> destinations = new ArrayList<>();
        destinations.add(new BuildMartDestination(GuiConfig.text("game-spectate-spectatemenu.text-019"), Component.text(GuiConfig.text("game-spectate-spectatemenu.text-019"), NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD), Material.CHEST, area.getSpectatorSpawnLocation(),
                List.of(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-020"), NamedTextColor.GRAY))));

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
            destinations.add(new BuildMartDestination(team.getName() + GuiConfig.text("game-spectate-spectatemenu.text-021"),
                    teamName(team).append(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-021"), NamedTextColor.WHITE))
                            .decorate(TextDecoration.BOLD), material, base.getPortalPoint(),
                    List.of(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-022"), NamedTextColor.GRAY))));
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

        Player viewer = Bukkit.getPlayer(holder.viewer);
        List<BaseGameInstance> instances = viewer == null
                ? List.of()
                : manager.getSpectatableInstances(viewer);
        holder.pageCount = Math.max(1, (instances.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        holder.page = Math.max(0, Math.min(holder.page, holder.pageCount - 1));

        BaseGameInstance current = manager.getSpectatorManager().areaOf(holder.viewer);
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
                    Component.text(GuiConfig.text("game-spectate-spectatemenu.text-023"), NamedTextColor.GRAY),
                    List.of(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-024"), NamedTextColor.DARK_GRAY)), false));
        }

        if (current != null) {
            inventory.setItem(LEAVE_SLOT, item(Material.REDSTONE,
                    Component.text(GuiConfig.text("game-spectate-spectatemenu.text-025"), NamedTextColor.RED),
                    List.of(Component.text(manager.getSpectatorDisplayName(current), NamedTextColor.GRAY)), false));
        }
        if (holder.page > 0) {
            inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW,
                    Component.text(GuiConfig.text("game-spectate-spectatemenu.text-007"), NamedTextColor.WHITE), List.of(), false));
        }
        inventory.setItem(REFRESH_SLOT, item(Material.CLOCK,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-008"), NamedTextColor.YELLOW),
                List.of(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-026"), NamedTextColor.DARK_GRAY)), false));
        inventory.setItem(PAGE_SLOT, item(Material.PAPER,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-009") + (holder.page + 1) + "/" + holder.pageCount + GuiConfig.text("game-spectate-spectatemenu.text-010"), NamedTextColor.AQUA),
                List.of(Component.text(instances.size() + GuiConfig.text("game-spectate-spectatemenu.text-027"), NamedTextColor.GRAY)), false));
        if (holder.page + 1 < holder.pageCount) {
            inventory.setItem(NEXT_SLOT, item(Material.ARROW,
                    Component.text(GuiConfig.text("game-spectate-spectatemenu.text-012"), NamedTextColor.WHITE), List.of(), false));
        }
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-013"), NamedTextColor.RED), List.of(), false));
    }

    private ItemStack currentStatusItem(BaseGameInstance current, int activeCount) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-028"), NamedTextColor.GRAY)
                .append(Component.text(activeCount, NamedTextColor.GREEN)));
        if (current == null) {
            lore.add(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-029"), NamedTextColor.DARK_GRAY));
        } else {
            lore.add(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-030"), NamedTextColor.GRAY)
                    .append(Component.text(current.getGameTypeEnum().toString(), NamedTextColor.AQUA))
                    .append(Component.text(GuiConfig.text("common.separator") + manager.getSpectatorDisplayName(current), NamedTextColor.WHITE)));
        }
        return item(Material.SPYGLASS,
                Component.text(GuiConfig.text("game-spectate-spectatemenu.text-031"), NamedTextColor.AQUA).decorate(TextDecoration.BOLD), lore, false);
    }

    private ItemStack instanceItem(@NotNull BaseGameInstance instance, boolean selected) {
        GameStyle style = GAME_STYLES.getOrDefault(instance.getGameTypeEnum(),
                new GameStyle(Material.ENDER_EYE, NamedTextColor.WHITE));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-032"), NamedTextColor.GRAY)
                .append(Component.text(manager.getSpectatorDisplayName(instance), NamedTextColor.WHITE)));
        lore.add(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-033"), NamedTextColor.GRAY)
                .append(Component.text(instance.getGameStageEnum().toString(), stageColor(instance.getGameStageEnum()))));
        lore.add(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-034"), NamedTextColor.GRAY)
                .append(Component.text(instance.getOnlineSpectators().size(), NamedTextColor.AQUA)));
        lore.add(Component.empty());
        appendTeams(lore, instance);
        if (selected) {
            lore.add(Component.empty());
            lore.add(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-035"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        }

        Component name = Component.text(instance.getGameTypeEnum().toString(), style.color)
                .append(Component.text(GuiConfig.text("common.separator") + manager.getSpectatorDisplayName(instance), NamedTextColor.WHITE))
                .decorate(TextDecoration.BOLD);
        return item(style.material, name, lore, selected);
    }

    private void appendTeams(@NotNull List<Component> lore, @NotNull BaseGameInstance instance) {
        if (instance instanceof BasePairedGameInstance paired) {
            ChampionshipTeam right = paired.getRightChampionshipTeam();
            ChampionshipTeam left = paired.getLeftChampionshipTeam();
            lore.add(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-036"), NamedTextColor.GOLD));
            lore.add(teamName(right)
                    .append(Component.text(GuiConfig.text("common.versus"), NamedTextColor.DARK_GRAY))
                    .append(teamName(left)));
            return;
        }
        if (instance instanceof BaseMultiTeamGameInstance multiTeam) {
            List<ChampionshipTeam> teams = multiTeam.getGameTeams();
            lore.add(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-037"), NamedTextColor.GOLD));
            if (teams.isEmpty()) {
                lore.add(Component.text(GuiConfig.text("game-spectate-spectatemenu.text-038"), NamedTextColor.DARK_GRAY));
                return;
            }
            for (int index = 0; index < teams.size(); index += 2) {
                Component line = teamName(teams.get(index));
                if (index + 1 < teams.size()) {
                    line = line.append(Component.text(GuiConfig.text("common.separator"), NamedTextColor.DARK_GRAY))
                            .append(teamName(teams.get(index + 1)));
                }
                lore.add(line);
            }
        }
    }

    private static Component teamName(ChampionshipTeam team) {
        if (team == null) return Component.text(GuiConfig.text("game-spectate-spectatemenu.text-039"), NamedTextColor.DARK_GRAY);
        TextColor color = TextColor.fromHexString(team.getColorCode());
        return Component.text(team.getName(), color == null ? NamedTextColor.WHITE : color);
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

    private record SubArenaDestination(String label, Material material, Location location) {
    }

    private static final class SubArenaHolder implements InventoryHolder {
        private final UUID viewer;
        private final BaseGameInstance area;
        private final Map<Integer, SubArenaDestination> destinationsBySlot = new HashMap<>();
        private Inventory inventory;
        private int page;
        private int pageCount = 1;

        private SubArenaHolder(UUID viewer, BaseGameInstance area) {
            this.viewer = viewer;
            this.area = area;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}

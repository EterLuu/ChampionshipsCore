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
        if (refreshTask != null) return;
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
        GuiConfig.MenuSpec menu = menu("venue-selector");
        Inventory inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
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
        String screen = "venue-selector";
        if (slot == itemSlot(screen, "status", CURRENT_SLOT)
                && manager.getSpectatorManager().areaOf(holder.viewer) != null) {
            manager.openSpectatorControls(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
            return;
        }
        if (slot == itemSlot(screen, "close", CLOSE_SLOT)) {
            player.closeInventory();
            return;
        }
        if (slot == itemSlot(screen, "refresh", REFRESH_SLOT)) {
            refresh(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.1F);
            return;
        }
        if (slot == itemSlot(screen, "previous", PREVIOUS_SLOT) && holder.page > 0) {
            holder.page--;
            refresh(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1F);
            return;
        }
        if (slot == itemSlot(screen, "next", NEXT_SLOT) && holder.page + 1 < holder.pageCount) {
            holder.page++;
            refresh(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1F);
            return;
        }
        if (slot == itemSlot(screen, "leave", LEAVE_SLOT)) {
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
            Utils.sendAdminError(player, GuiConfig.text("spectator.copy.unavailable"));
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
        GuiConfig.MenuSpec menu = menu("sub-arena-selector");
        holder.inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
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
        String screen = "sub-arena-selector";
        if (slot == itemSlot(screen, "close", CLOSE_SLOT)) {
            player.closeInventory();
            return;
        }
        if (slot == itemSlot(screen, "back", LEAVE_SLOT)) {
            open(player);
            return;
        }
        if (slot == itemSlot(screen, "refresh", REFRESH_SLOT)) {
            refreshSubArenas(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.1F);
            return;
        }
        if (slot == itemSlot(screen, "previous", PREVIOUS_SLOT) && holder.page > 0) {
            holder.page--;
            refreshSubArenas(holder);
            return;
        }
        if (slot == itemSlot(screen, "next", NEXT_SLOT) && holder.page + 1 < holder.pageCount) {
            holder.page++;
            refreshSubArenas(holder);
            return;
        }

        SubArenaDestination destination = holder.destinationsBySlot.get(slot);
        if (destination == null || !manager.canManuallySpectate(player)) return;
        if (!manager.selectSpectatorArea(player, holder.area, destination.location())) {
            Utils.sendAdminError(player, GuiConfig.text("spectator.copy.unavailable"));
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
        String screen = "sub-arena-selector";
        GuiConfig.MenuSpec menu = menu(screen);
        fillBorder(inventory, screen);

        List<SubArenaDestination> destinations = subArenaDestinations(holder.area);
        int pageSize = menu.contentSlots().size();
        holder.pageCount = Math.max(1, (destinations.size() + pageSize - 1) / pageSize);
        holder.page = Math.max(0, Math.min(holder.page, holder.pageCount - 1));
        GameStyle style = GAME_STYLES.getOrDefault(holder.area.getGameTypeEnum(),
                new GameStyle(Material.ENDER_EYE, NamedTextColor.WHITE));
        Map<String, Object> summary = Map.of("game", holder.area.getGameTypeEnum().toString(),
                "game_color", legacyColor(style.color()), "venue", manager.getSpectatorDisplayName(holder.area));
        setConfigured(inventory, screen, "summary", summary, null, style.material());

        int from = holder.page * pageSize;
        int to = Math.min(destinations.size(), from + pageSize);
        for (int index = from; index < to; index++) {
            int slot = menu.contentSlots().get(index - from);
            SubArenaDestination destination = destinations.get(index);
            inventory.setItem(slot, configuredItem(screen, "destination", Map.of("name", destination.label()),
                    null, destination.material()));
            holder.destinationsBySlot.put(slot, destination);
        }

        setConfigured(inventory, screen, "back", Map.of(), null, null);
        if (holder.page > 0) setConfigured(inventory, screen, "previous", Map.of(), null, null);
        setConfigured(inventory, screen, "refresh", Map.of(), null, null);
        setConfigured(inventory, screen, "page", Map.of("page", holder.page + 1,
                "pages", holder.pageCount, "count", destinations.size()), null, null);
        if (holder.page + 1 < holder.pageCount) setConfigured(inventory, screen, "next", Map.of(), null, null);
        setConfigured(inventory, screen, "close", Map.of(), null, null);
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
                        GuiConfig.text("spectator.copy.arena-section", Map.of("number", index + 1)), Material.TNT, location));
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
        GuiConfig.MenuSpec menu = menu("build-mart-selector");
        holder.inventory = Bukkit.createInventory(holder, menu.size(), menu.title());
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
        String screen = "build-mart-selector";
        if (slot == itemSlot(screen, "close", CLOSE_SLOT)) {
            player.closeInventory();
            return;
        }
        if (slot == itemSlot(screen, "back", LEAVE_SLOT)) {
            open(player);
            return;
        }
        if (slot == itemSlot(screen, "refresh", REFRESH_SLOT)) {
            refreshBuildMart(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.1F);
            return;
        }
        if (slot == itemSlot(screen, "previous", PREVIOUS_SLOT) && holder.page > 0) {
            holder.page--;
            refreshBuildMart(holder);
            return;
        }
        if (slot == itemSlot(screen, "next", NEXT_SLOT) && holder.page + 1 < holder.pageCount) {
            holder.page++;
            refreshBuildMart(holder);
            return;
        }

        BuildMartDestination destination = holder.destinationsBySlot.get(slot);
        if (destination == null || !manager.canManuallySpectate(player)) return;
        if (!manager.selectSpectatorArea(player, holder.area, destination.location())) {
            Utils.sendAdminError(player, GuiConfig.text("spectator.copy.unavailable"));
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
        String screen = "build-mart-selector";
        GuiConfig.MenuSpec menu = menu(screen);
        fillBorder(inventory, screen);

        List<BuildMartDestination> destinations = buildMartDestinations(holder.area);
        int pageSize = menu.contentSlots().size();
        holder.pageCount = Math.max(1, (destinations.size() + pageSize - 1) / pageSize);
        holder.page = Math.max(0, Math.min(holder.page, holder.pageCount - 1));
        setConfigured(inventory, screen, "summary", Map.of("venue", manager.getSpectatorDisplayName(holder.area),
                "count", destinations.size()), null, null);

        int from = holder.page * pageSize;
        int to = Math.min(destinations.size(), from + pageSize);
        for (int index = from; index < to; index++) {
            int slot = menu.contentSlots().get(index - from);
            BuildMartDestination destination = destinations.get(index);
            inventory.setItem(slot, item(destination.material(), destination.name(), destination.lore(), false));
            holder.destinationsBySlot.put(slot, destination);
        }

        setConfigured(inventory, screen, "back", Map.of(), null, null);
        if (holder.page > 0) setConfigured(inventory, screen, "previous", Map.of(), null, null);
        setConfigured(inventory, screen, "refresh", Map.of(), null, null);
        setConfigured(inventory, screen, "page", Map.of("page", holder.page + 1,
                "pages", holder.pageCount, "count", destinations.size()), null, null);
        if (holder.page + 1 < holder.pageCount) setConfigured(inventory, screen, "next", Map.of(), null, null);
        setConfigured(inventory, screen, "close", Map.of(), null, null);
    }

    private static List<BuildMartDestination> buildMartDestinations(@NotNull BuildMartArea area) {
        List<BuildMartDestination> destinations = new ArrayList<>();
        GuiConfig.ItemSpec resource = GuiConfig.item(
                "spectator.menus.build-mart-selector.items.resource-hub", Map.of());
        destinations.add(new BuildMartDestination(GuiConfig.text("spectator.copy.resource-hub"), resource.title(),
                resource.material(), area.getSpectatorSpawnLocation(), resource.lore()));

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
            Map<String, Object> values = Map.of("team", team.getName(), "team_color", legacyColor(teamColor(team)));
            GuiConfig.ItemSpec configured = GuiConfig.item(
                    "spectator.menus.build-mart-selector.items.team-base", values);
            destinations.add(new BuildMartDestination(GuiConfig.text("spectator.copy.team-base", values),
                    configured.title(), material, base.getPortalPoint(), configured.lore()));
        }
        return destinations;
    }

    private void refresh(@NotNull Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.instancesBySlot.clear();
        String screen = "venue-selector";
        GuiConfig.MenuSpec menu = menu(screen);
        fillBorder(inventory, screen);

        Player viewer = Bukkit.getPlayer(holder.viewer);
        List<BaseGameInstance> instances = viewer == null
                ? List.of()
                : manager.getLiveSpectatableInstances(viewer);
        int pageSize = menu.contentSlots().size();
        holder.pageCount = Math.max(1, (instances.size() + pageSize - 1) / pageSize);
        holder.page = Math.max(0, Math.min(holder.page, holder.pageCount - 1));

        BaseGameInstance current = manager.getSpectatorManager().areaOf(holder.viewer);
        inventory.setItem(itemSlot(screen, "status", CURRENT_SLOT), currentStatusItem(current, instances.size()));

        int from = holder.page * pageSize;
        int to = Math.min(instances.size(), from + pageSize);
        for (int index = from; index < to; index++) {
            int slot = menu.contentSlots().get(index - from);
            BaseGameInstance instance = instances.get(index);
            inventory.setItem(slot, instanceItem(instance, instance == current));
            holder.instancesBySlot.put(slot, instance);
        }

        if (instances.isEmpty()) {
            setConfigured(inventory, screen, "empty", Map.of(), null, null);
        }

        if (current != null) {
            setConfigured(inventory, screen, "leave",
                    Map.of("venue", manager.getSpectatorDisplayName(current)), null, null);
        }
        if (holder.page > 0) {
            setConfigured(inventory, screen, "previous", Map.of(), null, null);
        }
        setConfigured(inventory, screen, "refresh", Map.of(), null, null);
        setConfigured(inventory, screen, "page", Map.of("page", holder.page + 1,
                "pages", holder.pageCount, "count", instances.size()), null, null);
        if (holder.page + 1 < holder.pageCount) {
            setConfigured(inventory, screen, "next", Map.of(), null, null);
        }
        setConfigured(inventory, screen, "close", Map.of(), null, null);
    }

    private ItemStack currentStatusItem(BaseGameInstance current, int activeCount) {
        Map<String, Object> values = current == null ? Map.of("count", activeCount)
                : Map.of("count", activeCount, "game", current.getGameTypeEnum().toString(),
                        "venue", manager.getSpectatorDisplayName(current));
        return configuredItem("venue-selector", "status", values,
                current == null ? "idle" : "watching", null);
    }

    private ItemStack instanceItem(@NotNull BaseGameInstance instance, boolean selected) {
        GameStyle style = GAME_STYLES.getOrDefault(instance.getGameTypeEnum(),
                new GameStyle(Material.ENDER_EYE, NamedTextColor.WHITE));
        Map<String, Object> values = Map.of("game", instance.getGameTypeEnum().toString(),
                "game_color", legacyColor(style.color()), "venue", manager.getSpectatorDisplayName(instance),
                "stage", instance.getGameStageEnum().toString(),
                "stage_color", legacyColor(stageColor(instance.getGameStageEnum())),
                "audience", instance.getOnlineSpectators().size());
        GuiConfig.ItemSpec configured = GuiConfig.item("spectator.menus.venue-selector.items.match",
                selected ? "watching" : null, values);
        List<Component> lore = new ArrayList<>(configured.lore());
        lore.add(Component.empty());
        appendTeams(lore, instance);
        if (selected) {
            lore.add(Component.empty());
            lore.add(GuiConfig.component("spectator.menus.venue-selector.copy.watching"));
        }
        return item(style.material(), configured.title(), lore, configured.glint());
    }

    private void appendTeams(@NotNull List<Component> lore, @NotNull BaseGameInstance instance) {
        if (instance instanceof BasePairedGameInstance paired) {
            ChampionshipTeam right = paired.getRightChampionshipTeam();
            ChampionshipTeam left = paired.getLeftChampionshipTeam();
            lore.add(GuiConfig.component("spectator.menus.venue-selector.copy.paired-heading"));
            lore.add(teamName(right)
                    .append(Component.text(" " + GuiConfig.text("common.versus") + " ", NamedTextColor.DARK_GRAY))
                    .append(teamName(left)));
            return;
        }
        if (instance instanceof BaseMultiTeamGameInstance multiTeam) {
            List<ChampionshipTeam> teams = multiTeam.getGameTeams();
            lore.add(GuiConfig.component("spectator.menus.venue-selector.copy.multi-heading"));
            if (teams.isEmpty()) {
                lore.add(GuiConfig.component("spectator.menus.venue-selector.copy.waiting"));
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
        if (team == null) return GuiConfig.component("spectator.menus.venue-selector.copy.undecided");
        return Component.text(team.getName(), teamColor(team));
    }

    private static TextColor teamColor(@NotNull ChampionshipTeam team) {
        TextColor color = TextColor.fromHexString(team.getColorCode());
        return color == null ? NamedTextColor.WHITE : color;
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

    private static GuiConfig.MenuSpec menu(String screen) {
        List<Integer> fallbackSlots = java.util.stream.IntStream.rangeClosed(9, 44).boxed().toList();
        return GuiConfig.menu("spectator.menus." + screen, INVENTORY_SIZE, screen, fallbackSlots);
    }

    private static int itemSlot(String screen, String item, int fallback) {
        int configured = GuiConfig.item("spectator.menus." + screen + ".items." + item, Map.of()).slot();
        return configured < 0 ? fallback : configured;
    }

    private static ItemStack configuredItem(String screen, String key, Map<String, ?> placeholders,
                                            String state, Material materialOverride) {
        GuiConfig.ItemSpec configured = GuiConfig.item(
                "spectator.menus." + screen + ".items." + key, state, placeholders);
        return item(materialOverride == null ? configured.material() : materialOverride,
                configured.title(), configured.lore(), configured.glint());
    }

    private static void setConfigured(Inventory inventory, String screen, String key,
                                      Map<String, ?> placeholders, String state, Material materialOverride) {
        GuiConfig.ItemSpec configured = GuiConfig.item(
                "spectator.menus." + screen + ".items." + key, state, placeholders);
        if (configured.slot() >= 0 && configured.slot() < inventory.getSize()) {
            inventory.setItem(configured.slot(), item(materialOverride == null ? configured.material() : materialOverride,
                    configured.title(), configured.lore(), configured.glint()));
        }
    }

    private static void fillBorder(Inventory inventory, String screen) {
        String path = "spectator.menus." + screen + ".items.border";
        GuiConfig.ItemSpec border = GuiConfig.item(path, Map.of());
        List<Integer> fallback = List.of(0, 1, 2, 3, 5, 6, 7, 8, 45, 46, 47, 51);
        for (int slot : GuiConfig.slots("spectator.menus." + screen + ".layout.border", fallback)) {
            if (slot >= 0 && slot < inventory.getSize())
                inventory.setItem(slot, item(border.material(), border.title(), border.lore(), border.glint()));
        }
    }

    private static String legacyColor(@NotNull TextColor color) {
        return "&" + color.asHexString();
    }

    private static ItemStack item(Material material, Component name, List<Component> lore, boolean glint) {
        return ink.ziip.championshipscore.api.gui.GuiMenu.item(material, name, lore, glint);
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

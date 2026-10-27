package ink.ziip.championshipscore.api.game.spectate;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.buildmart.runtime.BuildMartArea;
import ink.ziip.championshipscore.api.game.buildmart.runtime.BuildMartBase;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.instance.paired.BasePairedGameInstance;
import ink.ziip.championshipscore.api.game.lasershowdown.runtime.LaserShowdownTeamArea;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.tntrun.runtime.TNTRunTeamArea;
import ink.ziip.championshipscore.api.gui.GuiItemLore;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuInventory;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.location.LocationConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.text.CoreMessages;
import ink.ziip.championshipscore.shared.presentation.MessageTemplate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Live selector for every currently running game instance, including replicated paired arenas. */
public final class SpectateMenu implements Listener {
    private final ChampionshipsCore plugin;
    private final GameManager manager;
    private BukkitTask refreshTask;
    private boolean running;

    public SpectateMenu(@NotNull ChampionshipsCore plugin, @NotNull GameManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void start() {
        if (refreshTask != null) return;
        running = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOpenMenus, 20L, 20L);
    }

    public void stop() {
        running = false;
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        closeAll();
        HandlerList.unregisterAll(this);
    }

    public void open(@NotNull Player player) {
        if (!running || !currentConnection(player)) return;
        Holder holder = new Holder(player);
        Inventory inventory = holder.inventory;
        refresh(holder);
        player.openInventory(inventory);
    }

    private void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof Holder holder && holder.accepts(this, player))
                refresh(holder);
            else if (top.getHolder() instanceof BuildMartHolder holder
                    && holder.accepts(this, player)) refreshBuildMart(holder);
            else if (top.getHolder() instanceof SubArenaHolder holder
                    && holder.accepts(this, player)) refreshSubArenas(holder);
        }
    }

    private void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof SelectorHolder selector && selector.owner == this)
                selector.close(player);
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
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != top)
            return;
        if (!holder.accepts(this, player)) return;

        int slot = event.getRawSlot();

        if (slot == holder.layout.control("status")
                && manager.getSpectatorManager().areaOf(holder.viewer) != null) {
            manager.openSpectatorControls(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
            return;
        }
        if (slot == holder.layout.control("close")) {
            holder.close(player);
            return;
        }
        if (slot == holder.layout.control("refresh")) {
            refresh(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.1F);
            return;
        }
        if (slot == holder.layout.control("previous") && holder.page > 0) {
            holder.page--;
            refresh(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1F);
            return;
        }
        if (slot == holder.layout.control("next") && holder.page + 1 < holder.pageCount) {
            holder.page++;
            refresh(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1F);
            return;
        }
        if (slot == holder.layout.control("leave")) {
            if (manager.leaveSpectating(player)) {
                player.sendMessage(MessageConfig.SPECTATOR_LEAVING_AREA);
                holder.close(player);
            } else {
                player.sendMessage(MessageConfig.SPECTATOR_CANT_LEAVING_AREA);
            }
            return;
        }

        BaseGameInstance target = holder.instancesBySlot.get(slot);
        if (target == null) return;
        if (!manager.getLiveSpectatableInstances(player).contains(target)) {
            refresh(holder);
            return;
        }
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
            CoreMessages.sendAdminError(player, MessageConfig.SPECTATOR_UNAVAILABLE);
            refresh(holder);
            return;
        }

        player.sendMessage(
                MessageTemplate.render(
                        MessageConfig.SPECTATOR_JOIN_AREA,
                        Map.of(
                                "%game%",
                                target.getGameTypeEnum().toString(),
                                "%area%",
                                manager.getSpectatorDisplayName(target))));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
        holder.close(player);
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof Holder
                || holder instanceof BuildMartHolder
                || holder instanceof SubArenaHolder) event.setCancelled(true);
    }

    private void openSubArenas(@NotNull Player player, @NotNull BaseGameInstance area) {
        SubArenaHolder holder = new SubArenaHolder(player, area);
        refreshSubArenas(holder);
        player.openInventory(holder.inventory);
    }

    private void handleSubArenaClick(
            @NotNull InventoryClickEvent event, @NotNull SubArenaHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!holder.accepts(this, player)) return;
        int slot = event.getRawSlot();

        if (slot == holder.layout.control("close")) {
            holder.close(player);
            return;
        }
        if (slot == holder.layout.control("back")) {
            open(player);
            return;
        }
        if (slot == holder.layout.control("refresh")) {
            refreshSubArenas(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.1F);
            return;
        }
        if (slot == holder.layout.control("previous") && holder.page > 0) {
            holder.page--;
            refreshSubArenas(holder);
            return;
        }
        if (slot == holder.layout.control("next") && holder.page + 1 < holder.pageCount) {
            holder.page++;
            refreshSubArenas(holder);
            return;
        }

        SubArenaDestination destination = holder.destinationsBySlot.get(slot);
        if (destination == null || !manager.canManuallySpectate(player)) return;
        if (!subArenaDestinations(holder.area).contains(destination)) {
            refreshSubArenas(holder);
            return;
        }
        if (!manager.selectSpectatorArea(player, holder.area, destination.location())) {
            CoreMessages.sendAdminError(player, MessageConfig.SPECTATOR_UNAVAILABLE);
            open(player);
            return;
        }
        player.sendMessage(
                MessageTemplate.render(
                        MessageConfig.SPECTATOR_JOIN_AREA,
                        Map.of(
                                "%game%",
                                holder.area.getGameTypeEnum().toString(),
                                "%area%",
                                String.join(
                                        GuiConfig.text("spectator.presentation.separator"),
                                        manager.getSpectatorDisplayName(holder.area),
                                        destination.label()))));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
        holder.close(player);
    }

    private void refreshSubArenas(@NotNull SubArenaHolder holder) {
        Inventory inventory = holder.inventory;
        holder.destinationsBySlot.clear();

        List<SubArenaDestination> destinations = subArenaDestinations(holder.area);
        int pageSize = holder.layout.content().size();
        beginPage(holder, destinations.size());
        GameStyle style = gameStyle(holder.area.getGameTypeEnum());
        Map<String, Object> summary =
                Map.of(
                        "game",
                        holder.area.getGameTypeEnum().toString(),
                        "game_color",
                        style.color(),
                        "venue",
                        manager.getSpectatorDisplayName(holder.area));
        setConfigured(holder, "summary", summary, null, style.material());

        int from = holder.page * pageSize;
        int to = Math.min(destinations.size(), from + pageSize);
        for (int index = from; index < to; index++) {
            int slot = holder.layout.pageSlots(holder.page, to).get(index - from);
            SubArenaDestination destination = destinations.get(index);
            inventory.setItem(
                    slot,
                    configuredItem(
                            holder.screen(),
                            "destination",
                            Map.of("name", destination.label()),
                            null,
                            destination.material()));
            holder.destinationsBySlot.put(slot, destination);
        }

        setConfigured(holder, "back", Map.of(), null, null);
        renderFooter(holder, destinations.size());
    }

    private static @NotNull List<SubArenaDestination> subArenaDestinations(
            @NotNull BaseGameInstance instance) {
        List<SubArenaDestination> destinations = new ArrayList<>();
        if (instance instanceof TNTRunTeamArea tntRun) {
            List<String> spawns = tntRun.getGameConfig().getPlayerSpawnPoints();
            if (spawns == null) return List.of();
            for (int index = 0; index < spawns.size(); index++) {
                Location location = safeLocation(spawns.get(index));
                if (location != null)
                    destinations.add(
                            new SubArenaDestination(
                                    GuiConfig.text(
                                            "spectator.menus.sub-arena-selector.items.destination.states.arena.title",
                                            Map.of("number", index + 1)),
                                    gameStyle(GameTypeEnum.TNTRun).material(),
                                    location));
            }
        } else if (instance instanceof LaserShowdownTeamArea lasershowdown) {
            ConfigurationSection section = lasershowdown.getGameConfig().getPlayerSpawnPoints();
            if (section == null) return List.of();
            List<String> names = new ArrayList<>(section.getKeys(false));
            names.sort(String.CASE_INSENSITIVE_ORDER);
            for (String name : names) {
                Location location =
                        section.getStringList(name).stream()
                                .map(SpectateMenu::safeLocation)
                                .filter(java.util.Objects::nonNull)
                                .findFirst()
                                .orElse(null);
                if (location != null)
                    destinations.add(
                            new SubArenaDestination(
                                    name,
                                    gameStyle(GameTypeEnum.LaserShowdown).material(),
                                    location));
            }
        }
        return List.copyOf(destinations);
    }

    private static Location safeLocation(String configured) {
        try {
            Location location = LocationConfig.readLocation(configured);
            return location != null && location.getWorld() != null ? location : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void openBuildMart(@NotNull Player player, @NotNull BuildMartArea area) {
        BuildMartHolder holder = new BuildMartHolder(player, area);
        refreshBuildMart(holder);
        player.openInventory(holder.inventory);
    }

    private void handleBuildMartClick(
            @NotNull InventoryClickEvent event, @NotNull BuildMartHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!holder.accepts(this, player)) return;
        int slot = event.getRawSlot();

        if (slot == holder.layout.control("close")) {
            holder.close(player);
            return;
        }
        if (slot == holder.layout.control("back")) {
            open(player);
            return;
        }
        if (slot == holder.layout.control("refresh")) {
            refreshBuildMart(holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.1F);
            return;
        }
        if (slot == holder.layout.control("previous") && holder.page > 0) {
            holder.page--;
            refreshBuildMart(holder);
            return;
        }
        if (slot == holder.layout.control("next") && holder.page + 1 < holder.pageCount) {
            holder.page++;
            refreshBuildMart(holder);
            return;
        }

        BuildMartDestination destination = holder.destinationsBySlot.get(slot);
        if (destination == null || !manager.canManuallySpectate(player)) return;
        if (!buildMartDestinations(holder.area).contains(destination)) {
            refreshBuildMart(holder);
            return;
        }
        if (!manager.selectSpectatorArea(player, holder.area, destination.location())) {
            CoreMessages.sendAdminError(player, MessageConfig.SPECTATOR_UNAVAILABLE);
            open(player);
            return;
        }
        player.sendMessage(
                MessageTemplate.render(
                        MessageConfig.SPECTATOR_JOIN_AREA,
                        Map.of(
                                "%game%",
                                holder.area.getGameTypeEnum().toString(),
                                "%area%",
                                String.join(
                                        GuiConfig.text("spectator.presentation.separator"),
                                        manager.getSpectatorDisplayName(holder.area),
                                        destination.label()))));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8F, 1.2F);
        holder.close(player);
    }

    private void refreshBuildMart(@NotNull BuildMartHolder holder) {
        Inventory inventory = holder.inventory;
        holder.destinationsBySlot.clear();

        List<BuildMartDestination> destinations = buildMartDestinations(holder.area);
        int pageSize = holder.layout.content().size();
        beginPage(holder, destinations.size());
        setConfigured(
                holder,
                "summary",
                Map.of(
                        "venue",
                        manager.getSpectatorDisplayName(holder.area),
                        "count",
                        destinations.size()),
                null,
                null);

        int from = holder.page * pageSize;
        int to = Math.min(destinations.size(), from + pageSize);
        for (int index = from; index < to; index++) {
            int slot = holder.layout.pageSlots(holder.page, to).get(index - from);
            BuildMartDestination destination = destinations.get(index);
            inventory.setItem(slot, destination.item());
            holder.destinationsBySlot.put(slot, destination);
        }

        setConfigured(holder, "back", Map.of(), null, null);
        renderFooter(holder, destinations.size());
    }

    private static List<BuildMartDestination> buildMartDestinations(@NotNull BuildMartArea area) {
        List<BuildMartDestination> destinations = new ArrayList<>();
        GuiConfig.ItemSpec resource =
                GuiConfig.item("spectator.menus.build-mart-selector.items.resource-hub", Map.of());
        destinations.add(
                new BuildMartDestination(
                        GuiConfig.text(
                                "spectator.menus.build-mart-selector.items.resource-hub.title"),
                        item(
                                resource.material(),
                                resource.title(),
                                resource.lore(),
                                resource.glint()),
                        area.getSpectatorSpawnLocation()));

        List<ChampionshipTeam> teams = area.getGameTeams();
        for (int index = 0; index < teams.size(); index++) {
            ChampionshipTeam team = teams.get(index);
            Integer assignedSeat = area.seatOf(team);
            int seat = assignedSeat == null ? index : assignedSeat;
            BuildMartBase base = area.cachedBaseForSeat(seat);
            if (base == null) base = area.getGameConfig().getSeatBase(seat);
            if (base == null || base.getPortalPoint() == null) continue;
            Material material =
                    Material.getMaterial(team.getColorName().toUpperCase(Locale.ROOT) + "_WOOL");
            if (material == null) material = Material.WHITE_WOOL;
            Map<String, Object> values =
                    Map.of("team", team.getName(), "team_color", legacyColor(teamColor(team)));
            GuiConfig.ItemSpec configured =
                    GuiConfig.item(
                            MenuId.SPECTATOR_BUILD_MART_SELECTOR.item("team-base"),
                            null,
                            values,
                            new GuiConfig.ItemSpec(
                                    -1, material, Component.empty(), List.of(), false));
            destinations.add(
                    new BuildMartDestination(
                            GuiConfig.text(
                                    "spectator.menus.build-mart-selector.items.team-base.title",
                                    values),
                            item(
                                    configured.material(),
                                    configured.title(),
                                    configured.lore(),
                                    configured.glint()),
                            base.getPortalPoint()));
        }
        return destinations;
    }

    private void refresh(@NotNull Holder holder) {
        Inventory inventory = holder.inventory;
        holder.instancesBySlot.clear();

        Player viewer = Bukkit.getPlayer(holder.viewer);
        List<BaseGameInstance> instances =
                viewer == null ? List.of() : manager.getLiveSpectatableInstances(viewer);
        int pageSize = holder.layout.content().size();
        beginPage(holder, instances.size());

        BaseGameInstance current = manager.getSpectatorManager().areaOf(holder.viewer);
        inventory.setItem(
                holder.layout.control("status"), currentStatusItem(current, instances.size()));

        int from = holder.page * pageSize;
        int to = Math.min(instances.size(), from + pageSize);
        for (int index = from; index < to; index++) {
            int slot = holder.layout.pageSlots(holder.page, to).get(index - from);
            BaseGameInstance instance = instances.get(index);
            inventory.setItem(slot, instanceItem(instance, instance == current));
            holder.instancesBySlot.put(slot, instance);
        }

        if (instances.isEmpty()) {
            setConfigured(holder, "empty", Map.of(), null, null);
        }

        if (current != null) {
            setConfigured(
                    holder,
                    "leave",
                    Map.of("venue", manager.getSpectatorDisplayName(current)),
                    null,
                    null);
        }
        renderFooter(holder, instances.size());
    }

    private ItemStack currentStatusItem(BaseGameInstance current, int activeCount) {
        Map<String, Object> values =
                current == null
                        ? Map.of("count", activeCount)
                        : Map.of(
                                "count",
                                activeCount,
                                "game",
                                current.getGameTypeEnum().toString(),
                                "venue",
                                manager.getSpectatorDisplayName(current));
        return configuredItem(
                "venue-selector", "status", values, current == null ? "idle" : "watching", null);
    }

    private ItemStack instanceItem(@NotNull BaseGameInstance instance, boolean selected) {
        GameStyle style = gameStyle(instance.getGameTypeEnum());
        Map<String, Object> values =
                Map.of(
                        "game",
                        instance.getGameTypeEnum().toString(),
                        "game_color",
                        style.color(),
                        "venue",
                        manager.getSpectatorDisplayName(instance),
                        "stage",
                        instance.getGameStageEnum().toString(),
                        "stage_color",
                        GuiConfig.text(
                                "spectator.styles.stages."
                                        + instance.getGameStageEnum()
                                                .name()
                                                .toLowerCase(Locale.ROOT)),
                        "audience",
                        instance.getOnlineSpectators().size());
        String path = MenuId.SPECTATOR_VENUE_SELECTOR.item("match");
        String state = selected ? "watching" : null;
        var configured =
                GuiConfig.item(
                        path,
                        state,
                        values,
                        new GuiConfig.ItemSpec(
                                -1, style.material(), Component.empty(), List.of(), false));
        List<Component> teams = new ArrayList<>();
        appendTeams(teams, instance);
        return item(
                configured.material(),
                configured.title(),
                GuiItemLore.expand(
                        GuiConfig.itemLoreTemplate(path, state), values, "%teams%", teams),
                configured.glint());
    }

    private void appendTeams(@NotNull List<Component> lore, @NotNull BaseGameInstance instance) {
        if (instance instanceof BasePairedGameInstance paired) {
            ChampionshipTeam right = paired.getRightChampionshipTeam();
            ChampionshipTeam left = paired.getLeftChampionshipTeam();
            lore.add(
                    GuiConfig.component(
                            "spectator.menus.venue-selector.items.match.states.paired.title"));
            lore.add(
                    LegacyText.component(
                            GuiConfig.text(
                                    "spectator.menus.venue-selector.items.match.states.paired-line.title",
                                    Map.of(
                                            "right",
                                            legacyTeamName(right),
                                            "left",
                                            legacyTeamName(left)))));
            return;
        }
        if (instance instanceof BaseMultiTeamGameInstance multiTeam) {
            List<ChampionshipTeam> teams = multiTeam.getGameTeams();
            lore.add(
                    GuiConfig.component(
                            "spectator.menus.venue-selector.items.match.states.multi.title"));
            if (teams.isEmpty()) {
                lore.add(
                        GuiConfig.component(
                                "spectator.menus.venue-selector.items.match.states.waiting.title"));
                return;
            }
            for (int index = 0; index < teams.size(); index += 2) {
                Component line = teamName(teams.get(index));
                if (index + 1 < teams.size()) {
                    line =
                            GuiConfig.component(
                                    MenuId.SPECTATOR_VENUE_SELECTOR.item("match")
                                            + ".states.team-line.title",
                                    Map.of(
                                            "left",
                                            line,
                                            "right",
                                            teamName(teams.get(index + 1)),
                                            "separator",
                                            GuiConfig.text("spectator.presentation.separator")));
                }
                lore.add(line);
            }
        }
    }

    private static String legacyTeamName(ChampionshipTeam team) {
        return team == null
                ? GuiConfig.text(
                        "spectator.menus.venue-selector.items.match.states.undecided.title")
                : String.join("", legacyColor(teamColor(team)), team.getName());
    }

    private static Component teamName(ChampionshipTeam team) {
        if (team == null)
            return GuiConfig.component(
                    "spectator.menus.venue-selector.items.match.states.undecided.title");
        return LegacyText.component(team.getName(), teamColor(team));
    }

    private static TextColor teamColor(@NotNull ChampionshipTeam team) {
        TextColor color = TextColor.fromHexString(team.getColorCode());
        return color == null ? NamedTextColor.WHITE : color;
    }

    static MenuLayout layout(MenuId screen) {
        var controls =
                new HashMap<>(
                        Map.of("previous", 48, "refresh", 49, "page", 50, "next", 52, "close", 53));
        if (screen == MenuId.SPECTATOR_VENUE_SELECTOR)
            controls.putAll(Map.of("status", 4, "leave", 45));
        else controls.putAll(Map.of("summary", 4, "back", 45));
        int size = GuiConfig.menu(screen.path(), 54, Component.empty(), List.of()).size();
        return MenuLayout.load(
                screen.path(),
                size,
                MenuLayout.range(9, 45),
                List.of(0, 1, 2, 3, 5, 6, 7, 8, 45, 46, 47, 51),
                controls);
    }

    private static boolean currentConnection(Player player) {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    private static ItemStack configuredItem(
            String screen,
            String key,
            Map<String, ?> placeholders,
            String state,
            Material materialOverride) {
        String path = "spectator.menus." + screen + ".items." + key;
        GuiConfig.ItemSpec configured =
                materialOverride == null
                        ? GuiConfig.item(path, state, placeholders)
                        : GuiConfig.item(
                                path,
                                state,
                                placeholders,
                                new GuiConfig.ItemSpec(
                                        -1, materialOverride, Component.empty(), List.of(), false));
        return item(
                configured.material(), configured.title(), configured.lore(), configured.glint());
    }

    private static void setConfigured(
            SelectorHolder holder,
            String key,
            Map<String, ?> values,
            String state,
            Material override) {
        // Empty is an overlay shown only without entries; it never reserves page capacity.
        int slot = key.equals("empty") ? holder.emptySlot : holder.layout.control(key);
        if (slot >= 0)
            holder.inventory.setItem(
                    slot, configuredItem(holder.screen(), key, values, state, override));
    }

    private static void beginPage(SelectorHolder holder, int count) {
        holder.inventory.clear();
        holder.pageCount = holder.layout.pageCount(count);
        holder.page = holder.layout.clampPage(holder.page, count);
        fillBorder(holder);
    }

    private static void renderFooter(SelectorHolder holder, int count) {
        if (holder.page > 0) setConfigured(holder, "previous", Map.of(), null, null);
        setConfigured(holder, "refresh", Map.of(), null, null);
        setConfigured(
                holder,
                "page",
                Map.of("page", holder.page + 1, "pages", holder.pageCount, "count", count),
                null,
                null);
        if (holder.page + 1 < holder.pageCount) setConfigured(holder, "next", Map.of(), null, null);
        setConfigured(holder, "close", Map.of(), null, null);
    }

    private static void fillBorder(SelectorHolder holder) {
        var border = configuredItem(holder.screen(), "border", Map.of(), null, null);
        for (int slot : holder.layout.border()) holder.inventory.setItem(slot, border);
    }

    private static String legacyColor(@NotNull TextColor color) {
        return "&" + color.asHexString();
    }

    private static ItemStack item(
            Material material, Component name, List<Component> lore, boolean glint) {
        return ink.ziip.championshipscore.api.gui.GuiMenu.item(material, name, lore, glint);
    }

    private static GameStyle gameStyle(GameTypeEnum game) {
        String path = "spectator.styles.games." + game.commandName();
        return new GameStyle(
                GuiConfig.material(path + ".material", Material.ENDER_EYE),
                GuiConfig.text(path + ".color"));
    }

    private record GameStyle(Material material, String color) {}

    private abstract static class SelectorHolder implements MenuInventory {
        final SpectateMenu owner;
        final Player connection;
        final UUID viewer;
        final MenuId menu;
        final MenuLayout layout;
        final int emptySlot;
        final Inventory inventory;
        int page;
        int pageCount = 1;

        SelectorHolder(SpectateMenu owner, Player player, MenuId menu) {
            this.owner = owner;
            connection = player;
            viewer = player.getUniqueId();
            this.menu = menu;
            layout = layout(menu);
            emptySlot =
                    menu == MenuId.SPECTATOR_VENUE_SELECTOR
                            ? GuiConfig.integer(
                                    menu.path() + ".layout.overlay.empty",
                                    GuiConfig.item(menu.item("empty"), Map.of()).slot())
                            : -1;
            inventory =
                    Bukkit.createInventory(
                            this, layout.size(), GuiConfig.component(menu.path() + ".title"));
        }

        String screen() {
            return menu.path().substring("spectator.menus.".length());
        }

        boolean accepts(SpectateMenu requester, Player player) {
            return owner == requester
                    && owner.running
                    && player == connection
                    && currentConnection(player)
                    && player.getOpenInventory().getTopInventory() == inventory;
        }

        void close(Player player) {
            if (player == connection
                    && currentConnection(player)
                    && player.getOpenInventory().getTopInventory() == inventory)
                player.closeInventory();
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private final class Holder extends SelectorHolder {
        final Map<Integer, BaseGameInstance> instancesBySlot = new HashMap<>();

        Holder(Player player) {
            super(SpectateMenu.this, player, MenuId.SPECTATOR_VENUE_SELECTOR);
        }
    }

    private record BuildMartDestination(String label, ItemStack item, Location location) {}

    private final class BuildMartHolder extends SelectorHolder {
        final BuildMartArea area;
        final Map<Integer, BuildMartDestination> destinationsBySlot = new HashMap<>();

        BuildMartHolder(Player player, BuildMartArea area) {
            super(SpectateMenu.this, player, MenuId.SPECTATOR_BUILD_MART_SELECTOR);
            this.area = area;
        }
    }

    private record SubArenaDestination(String label, Material material, Location location) {}

    private final class SubArenaHolder extends SelectorHolder {
        final BaseGameInstance area;
        final Map<Integer, SubArenaDestination> destinationsBySlot = new HashMap<>();

        SubArenaHolder(Player player, BaseGameInstance area) {
            super(SpectateMenu.this, player, MenuId.SPECTATOR_SUB_ARENA_SELECTOR);
            this.area = area;
        }
    }
}

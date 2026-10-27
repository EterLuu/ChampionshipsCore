package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.ChampionshipPermissions;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.buildmart.BuildMartBlueprintWorkshop;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import ink.ziip.championshipscore.api.game.buildmart.config.BuildMartConfig;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuInventory;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Blueprint selection and workshop controls share the existing map-editor session and lock. */
public final class BuildMartBlueprintGui {
    public static final String PATH = MenuId.BUILD_MART_BLUEPRINTS.path();

    private BuildMartBlueprintGui() {}

    public static final class Holder implements MenuInventory {
        final PrepareSession session;
        final List<BuildMartBlueprint> rows;
        final int page;
        final MenuLayout layout;
        Inventory inventory;

        Holder(PrepareSession session, List<BuildMartBlueprint> rows, int page, MenuLayout layout) {
            this.session = session;
            this.rows = List.copyOf(rows);
            this.layout = layout;
            this.page = page;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static boolean available(
            PrepareSessionManager manager, Player player, PrepareSession session) {
        if (!player.hasPermission(ChampionshipPermissions.ADMIN)
                || manager.getSession(player) != session
                || !(session.getTarget().config() instanceof BuildMartConfig)) {
            player.closeInventory();
            return false;
        }
        if (!session.getTarget().canSaveMap()) {
            CoreMessages.sendAdminError(player, MessageConfig.MAP_EDITOR_BUILD_INSTANCE_RUNNING);
            return false;
        }
        return true;
    }

    public static void open(
            PrepareSessionManager manager, Player player, PrepareSession session, int page) {
        if (!available(manager, player, session)) return;
        List<BuildMartBlueprint> rows =
                session
                        .getPlugin()
                        .getGameManager()
                        .getBuildMartManager()
                        .getOrderPool()
                        .getAll()
                        .stream()
                        .sorted(Comparator.comparing(BuildMartBlueprint::getId))
                        .toList();
        MenuLayout layout = layout();
        int pages = layout.pageCount(rows.size());
        var holder = new Holder(session, rows, layout.clampPage(page, rows.size()), layout);
        holder.inventory =
                Bukkit.createInventory(
                        holder,
                        layout.size(),
                        GuiConfig.component(
                                PATH + ".title", Map.of("page", holder.page + 1, "pages", pages)));
        for (int slot : layout.border()) button(holder, slot, "filler", Map.of());
        for (int slot : layout.content()) {
            int index = layout.index(holder.page, slot);
            if (index >= rows.size()) {
                button(holder, slot, "filler", Map.of());
                continue;
            }
            var blueprint = rows.get(index);
            button(
                    holder,
                    slot,
                    "blueprint",
                    Map.of(
                            "name",
                            blueprint.getDisplayName(),
                            "id",
                            blueprint.getId(),
                            "stars",
                            blueprint.getStars(),
                            "blocks",
                            blueprint.blockCount()));
        }
        if (holder.page > 0)
            button(holder, holder.layout.control("previous"), "previous", Map.of());
        if (holder.page + 1 < pages)
            button(holder, holder.layout.control("next"), "next", Map.of());
        button(holder, holder.layout.control("back"), "back", Map.of());
        var workshop = session.getBlueprintWorkshop();
        if (workshop == null) button(holder, holder.layout.control("new"), "new", Map.of());
        else {
            button(
                    holder,
                    holder.layout.control("submit"),
                    "submit",
                    Map.of("name", workshop.name()));
            button(
                    holder,
                    holder.layout.control("cancel"),
                    "cancel",
                    Map.of("name", workshop.name()));
        }
        player.openInventory(holder.inventory);
    }

    public static void handleClick(
            PrepareSessionManager manager,
            InventoryClickEvent event,
            Player player,
            Holder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() != holder.inventory
                || !available(manager, player, holder.session)) return;
        int slot = event.getRawSlot();
        var session = holder.session;
        var workshop = session.getBlueprintWorkshop();
        if (slot == holder.layout.control("back")) {
            if (workshop == null) StepMenuGui.open(player, session);
            else player.closeInventory();
        } else if (slot == holder.layout.control("previous") && holder.page > 0)
            open(manager, player, session, holder.page - 1);
        else if (slot == holder.layout.control("next")
                && holder.page + 1 < holder.layout.pageCount(holder.rows.size()))
            open(manager, player, session, holder.page + 1);
        else if (workshop != null) {
            if (slot == holder.layout.control("submit")) {
                player.closeInventory();
                workshop.submit(player);
            } else if (slot == holder.layout.control("cancel")) {
                player.closeInventory();
                workshop.cancel(player);
            } else if (holder.layout.index(holder.page, slot) >= 0
                    || slot == holder.layout.control("new"))
                CoreMessages.sendAdminError(player, MessageConfig.BUILD_MART_EDITOR_FINISH_FIRST);
        } else if (slot == holder.layout.control("new")) {
            AnvilInputGui.openEditorText(
                    player,
                    session,
                    GuiConfig.text(PATH + ".input.title"),
                    "",
                    name -> {
                        if (!available(manager, player, session))
                            return MessageConfig.BUILD_MART_EDITOR_CANCELLED;
                        if (!BuildMartBlueprintWorkshop.validName(name))
                            return MessageConfig.BUILD_MART_EDITOR_INVALID_NAME;
                        if (session
                                .getPlugin()
                                .getGameManager()
                                .getBuildMartManager()
                                .getOrderPool()
                                .getAll()
                                .stream()
                                .anyMatch(blueprint -> blueprint.getId().equalsIgnoreCase(name)))
                            return MessageConfig.BUILD_MART_EDITOR_CONFLICT;
                        return null;
                    },
                    name -> {
                        if (available(manager, player, session))
                            BuildMartBlueprintWorkshop.start(manager, player, session, name, null);
                    },
                    () -> open(manager, player, session, holder.page));
        } else if (holder.layout.index(holder.page, slot) >= 0
                && holder.layout.index(holder.page, slot) < holder.rows.size()) {
            String id = holder.rows.get(holder.layout.index(holder.page, slot)).getId();
            var current =
                    session.getPlugin()
                            .getGameManager()
                            .getBuildMartManager()
                            .getOrderPool()
                            .byId(id);
            if (current == null) {
                open(manager, player, session, holder.page);
                return;
            }
            BuildMartBlueprintWorkshop.start(manager, player, session, id, current);
        }
    }

    static MenuLayout layout() {
        int size = GuiConfig.menu(PATH, 54, "", MenuLayout.range(0, 45)).size();
        return MenuLayout.load(
                PATH,
                size,
                MenuLayout.range(0, 45),
                MenuLayout.range(45, size),
                Map.of(
                        "previous",
                        45,
                        "new",
                        46,
                        "back",
                        49,
                        "submit",
                        50,
                        "cancel",
                        51,
                        "next",
                        53));
    }

    private static void button(Holder holder, int slot, String key, Map<String, ?> values) {
        holder.inventory.setItem(
                slot,
                ConfiguredGui.item(
                        PATH + ".items." + key,
                        null,
                        values,
                        Material.PAPER,
                        Component.empty(),
                        List.of(),
                        false));
    }
}

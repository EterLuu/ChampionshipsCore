package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.ChampionshipPermissions;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.buildmart.BuildMartBlueprintWorkshop;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import ink.ziip.championshipscore.api.game.buildmart.config.BuildMartConfig;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuInventory;
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
    private static final int PAGE_SIZE = 45,
            PREVIOUS = 45,
            NEW = 46,
            BACK = 49,
            SUBMIT = 50,
            CANCEL = 51,
            NEXT = 53;

    private BuildMartBlueprintGui() {}

    public static final class Holder implements MenuInventory {
        final PrepareSession session;
        final List<BuildMartBlueprint> rows;
        final int page;
        Inventory inventory;

        Holder(PrepareSession session, List<BuildMartBlueprint> rows, int page) {
            this.session = session;
            this.rows = rows;
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
        int pages = Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        var holder = new Holder(session, rows, Math.max(0, Math.min(page, pages - 1)));
        holder.inventory =
                Bukkit.createInventory(
                        holder,
                        54,
                        GuiConfig.component(
                                PATH + ".title", Map.of("page", holder.page + 1, "pages", pages)));
        for (int slot = 0; slot < 54; slot++) button(holder, slot, "filler", Map.of());
        for (int slot = 0;
                slot < PAGE_SIZE && holder.page * PAGE_SIZE + slot < rows.size();
                slot++) {
            var blueprint = rows.get(holder.page * PAGE_SIZE + slot);
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
        if (holder.page > 0) button(holder, PREVIOUS, "previous", Map.of());
        if (holder.page + 1 < pages) button(holder, NEXT, "next", Map.of());
        button(holder, BACK, "back", Map.of());
        var workshop = session.getBlueprintWorkshop();
        if (workshop == null) button(holder, NEW, "new", Map.of());
        else {
            button(holder, SUBMIT, "submit", Map.of("name", workshop.name()));
            button(holder, CANCEL, "cancel", Map.of("name", workshop.name()));
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
        if (slot == BACK) {
            if (workshop == null) StepMenuGui.open(player, session);
            else player.closeInventory();
        } else if (slot == PREVIOUS && holder.page > 0)
            open(manager, player, session, holder.page - 1);
        else if (slot == NEXT && (holder.page + 1) * PAGE_SIZE < holder.rows.size())
            open(manager, player, session, holder.page + 1);
        else if (workshop != null) {
            if (slot == SUBMIT) {
                player.closeInventory();
                workshop.submit(player);
            } else if (slot == CANCEL) {
                player.closeInventory();
                workshop.cancel(player);
            } else if (slot < PAGE_SIZE || slot == NEW)
                CoreMessages.sendAdminError(player, MessageConfig.BUILD_MART_EDITOR_FINISH_FIRST);
        } else if (slot == NEW) {
            AnvilInputGui.openEditorText(
                    player,
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
        } else if (slot >= 0
                && slot < PAGE_SIZE
                && holder.page * PAGE_SIZE + slot < holder.rows.size()) {
            String id = holder.rows.get(holder.page * PAGE_SIZE + slot).getId();
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

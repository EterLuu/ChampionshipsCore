package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiText;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds and refreshes the dedicated prepare-mode hotbar. The hotbar contains the fixed controls and one
 * entry point for the paged step menu; no step item is placed in the player's main inventory.
 * The WorldEdit wand is deliberately left untagged so WorldEdit's own interact handlers run.
 */
public final class PrepareModeInventory {
    private static final String MENU_PATH = MenuId.MAP_EDITOR_PREPARE_TOOLBAR.path();
    private PrepareModeInventory() {
    }

    /** Wipe the player's inventory completely and lay out the prepare hotbar. */
    public static void apply(@NotNull Player player, @NotNull PrepareSession session) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);
        player.setItemOnCursor(null);
        refresh(player, session);
    }

    /**
     * Re-render the fixed controls without disturbing the spare hotbar slots used for creative-mode
     * building materials. The full inventory is only cleared when a prepare session starts.
     */
    public static void refresh(@NotNull Player player, @NotNull PrepareSession session) {
        PlayerInventory inv = player.getInventory();
        inv.setItem(slot("status", 0), statusItem(player, session));
        inv.setItem(slot("teleport", 1), teleportItem(session));
        inv.setItem(slot("steps", 2), stepsItem(session));
        inv.setItem(slot("validate", 3), validateItem());
        inv.setItem(slot("publish", 4), publishItem(session));
        if (session.requiresWorldEdit()) inv.setItem(slot("wand", 5), wandItem());
        inv.setItem(slot("save-draft", 6), saveDraftItem());
        inv.setItem(slot("exit", 8), exitItem());
    }

    /** Slots reserved for prepare controls and therefore never valid creative pick-block targets. */
    public static boolean isControlSlot(@NotNull PrepareSession session, int slot) {
        return slot == slot("status", 0) || slot == slot("teleport", 1)
                || slot == slot("steps", 2) || slot == slot("validate", 3)
                || slot == slot("publish", 4) || slot == slot("save-draft", 6)
                || slot == slot("exit", 8) || session.requiresWorldEdit() && slot == slot("wand", 5);
    }

    /**
     * Finds a safe hotbar target for creative pick-block. Prefer an empty spare slot, then reuse a
     * spare material slot rather than ever overwriting a prepare control.
     */
    public static int creativePickTarget(@NotNull Player player, @NotNull PrepareSession session) {
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (!isControlSlot(session, slot) && inv.getItem(slot) == null) return slot;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (!isControlSlot(session, slot)) return slot;
        }
        return -1;
    }

    private static ItemStack statusItem(@NotNull Player player, @NotNull PrepareSession session) {
        GameTypeEnum game = session.getGameType();
        boolean inWorld = session.getFlow().isInCorrectWorld(player, session.getTarget());
        String readiness = session.getTarget().config().isPrepareReady()
                ? GuiConfig.text("map-editor.menus.prepare-toolbar.items.status.states.ready.title")
                : GuiConfig.text("map-editor.menus.prepare-toolbar.items.status.states.draft.title");
        ItemStack item = configured("status", Map.of(
                "game", game,
                "map", session.getAreaName(),
                "world", session.getFlow().worldName(session.getTarget()),
                "done", session.doneCount(),
                "total", session.totalSteps(),
                "readiness", readiness));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            List<String> pending = new ArrayList<>();
            for (PrepareStep step : session.getSteps()) {
                if (!step.isSet(session)) pending.add(plain(step.displayName()));
            }
            if (pending.isEmpty()) {
                lore.add(LegacyText.component(GuiConfig.text(
                        "map-editor.menus.prepare-toolbar.items.status.states.complete.title")));
            } else {
                lore.add(LegacyText.component(GuiConfig.text(
                        "map-editor.menus.prepare-toolbar.items.status.states.pending.title")));
                for (String pendingStep : pending) lore.add(LegacyText.component(GuiConfig.line(
                        "map-editor.menus.prepare-toolbar.items.status.states.pending-entry.lore", 0,
                        Map.of("step", pendingStep))));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack teleportItem(@NotNull PrepareSession session) {
        String destination = session.getFlow().editorLocationName(session.getTarget());
        ItemStack item = configured("teleport", Map.of(
                "destination", destination,
                "world", session.getFlow().worldName(session.getTarget())));
        PrepareKeys.setAction(item, "teleport");
        return item;
    }

    private static ItemStack stepsItem(@NotNull PrepareSession session) {
        ItemStack item = configured("steps", Map.of("count", session.totalSteps()));
        PrepareKeys.setAction(item, "steps");
        return item;
    }

    private static ItemStack exitItem() {
        ItemStack item = configured("exit", Map.of());
        PrepareKeys.setAction(item, "exit");
        return item;
    }

    private static ItemStack validateItem() {
        ItemStack item = configured("validate", Map.of());
        PrepareKeys.setAction(item, "validate");
        return item;
    }

    private static ItemStack publishItem(PrepareSession session) {
        boolean ready = session.getTarget().config().isPrepareReady();
        ItemStack item = configured("publish", ready ? "published" : "draft", Map.of());
        PrepareKeys.setAction(item, "publish");
        return item;
    }

    private static ItemStack saveDraftItem() {
        ItemStack item = configured("save-draft", Map.of());
        PrepareKeys.setAction(item, "save-draft");
        return item;
    }

    /** Untagged on purpose: WorldEdit's wand interact handlers must not be cancelled. */
    private static ItemStack wandItem() {
        return configured("wand", Map.of());
    }

    private static int slot(String item, int fallback) {
        return ConfiguredGui.slot(MENU_PATH + ".items." + item, fallback);
    }

    private static ItemStack configured(String item, Map<String, ?> placeholders) {
        return ConfiguredGui.item(MENU_PATH + ".items." + item, null, placeholders, new ItemStack(Material.BARRIER));
    }

    private static ItemStack configured(String item, String state, Map<String, ?> placeholders) {
        return ConfiguredGui.item(MENU_PATH + ".items." + item, state, placeholders, new ItemStack(Material.BARRIER));
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }
}

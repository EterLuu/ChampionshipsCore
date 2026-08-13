package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and refreshes the dedicated prepare-mode hotbar. The hotbar contains the fixed controls and one
 * entry point for the paged step menu; no step item is placed in the player's main inventory.
 * The WorldEdit wand is deliberately left untagged so WorldEdit's own interact handlers run.
 */
public final class PrepareModeInventory {
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
        inv.setItem(0, statusItem(player, session));
        inv.setItem(1, teleportItem(session));
        inv.setItem(2, stepsItem(session));
        inv.setItem(3, validateItem());
        inv.setItem(4, publishItem(session));
        if (session.requiresWorldEdit()) inv.setItem(5, wandItem());
        inv.setItem(6, saveDraftItem());
        inv.setItem(8, exitItem());
    }

    /** Slots reserved for prepare controls and therefore never valid creative pick-block targets. */
    public static boolean isControlSlot(@NotNull PrepareSession session, int slot) {
        return switch (slot) {
            case 0, 1, 2, 3, 4, 6, 8 -> true;
            case 5 -> session.requiresWorldEdit();
            default -> false;
        };
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
        int done = session.doneCount();
        int total = session.totalSteps();

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(GuiConfig.text("map-editor.toolbar.game") + game + GuiConfig.text("map-editor.toolbar.map") + session.getAreaName()).color(NamedTextColor.GRAY));
        lore.add(Component.text(GuiConfig.text("map-editor.toolbar.target-world") + session.getFlow().worldName(session.getTarget())
                        + (inWorld ? GuiConfig.text("map-editor.toolbar.already-in-the-correct-world") : GuiConfig.text("map-editor.toolbar.please-go-to-this-world"))).color(inWorld ? NamedTextColor.GREEN : NamedTextColor.RED));
        lore.add(Component.text(GuiConfig.text("map-editor.toolbar.progress") + done + "/" + total).color(NamedTextColor.AQUA));
        lore.add(Component.text(session.getTarget().config().isPrepareReady()
                ? GuiConfig.text("map-editor.toolbar.status-published") : GuiConfig.text("map-editor.toolbar.status-draft-with-unpublished-changes"))
                .color(session.getTarget().config().isPrepareReady() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));

        List<String> pending = new ArrayList<>();
        for (PrepareStep step : session.getSteps()) {
            if (!step.isSet(session)) pending.add(plain(step.displayName()));
        }
        if (pending.isEmpty()) {
            lore.add(Component.text(GuiConfig.text("map-editor.toolbar.all-steps-completed")).color(NamedTextColor.GREEN));
        } else {
            lore.add(Component.text(GuiConfig.text("map-editor.toolbar.to-do")).color(NamedTextColor.YELLOW));
            for (String p : pending) lore.add(Component.text("• " + p).color(NamedTextColor.YELLOW));
        }
        return PrepareKeys.item(Material.PAPER, Component.text(GuiConfig.text("map-editor.toolbar.map-preparation-mode")).color(NamedTextColor.WHITE), lore);
    }

    private static ItemStack teleportItem(@NotNull PrepareSession session) {
        String destination = session.getFlow().editorLocationName(session.getTarget());
        ItemStack item = PrepareKeys.item(Material.ENDER_PEARL,
                Component.text(GuiConfig.text("map-editor.toolbar.send-to") + destination).color(NamedTextColor.AQUA),
                List.of(Component.text(GuiConfig.text("map-editor.toolbar.go-to") + session.getFlow().worldName(session.getTarget())
                        + GuiConfig.text("map-editor.toolbar.world-go-to") + destination).color(NamedTextColor.GRAY)));
        PrepareKeys.setAction(item, "teleport");
        return item;
    }

    private static ItemStack stepsItem(@NotNull PrepareSession session) {
        ItemStack item = PrepareKeys.item(Material.CHEST,
                Component.text(GuiConfig.text("map-editor.toolbar.editing-preparation-steps")).color(NamedTextColor.AQUA),
                List.of(Component.text(GuiConfig.text("map-editor.toolbar.open-multi-line-step-menu")).color(NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("map-editor.toolbar.number-of-steps") + session.totalSteps()).color(NamedTextColor.GRAY)));
        PrepareKeys.setAction(item, "steps");
        return item;
    }

    private static ItemStack exitItem() {
        ItemStack item = PrepareKeys.item(Material.BARRIER,
                Component.text(GuiConfig.text("map-editor.toolbar.exit-map-preparation-mode")).color(NamedTextColor.RED),
                List.of(Component.text(GuiConfig.text("map-editor.toolbar.restore-inventory-and-exit")).color(NamedTextColor.GRAY)));
        PrepareKeys.setAction(item, "exit");
        return item;
    }

    private static ItemStack validateItem() {
        ItemStack item = PrepareKeys.item(Material.SPYGLASS,
                Component.text(GuiConfig.text("map-editor.toolbar.check-map")).color(NamedTextColor.YELLOW),
                List.of(Component.text(GuiConfig.text("map-editor.toolbar.list-all-required-steps-that-are-not-completed")).color(NamedTextColor.GRAY)));
        PrepareKeys.setAction(item, "validate");
        return item;
    }

    private static ItemStack publishItem(PrepareSession session) {
        boolean ready = session.getTarget().config().isPrepareReady();
        ItemStack item = PrepareKeys.item(ready ? Material.LIME_DYE : Material.YELLOW_DYE,
                Component.text(ready ? GuiConfig.text("map-editor.toolbar.map-published") : GuiConfig.text("map-editor.toolbar.verify-and-publish"))
                        .color(ready ? NamedTextColor.GREEN : NamedTextColor.GOLD),
                List.of(Component.text(ready ? GuiConfig.text("map-editor.toolbar.release-again-to-generate-a-new-revision") : GuiConfig.text("map-editor.toolbar.after-the-verification-is-passed-the-world-is-solidified-and-the-game-is-allowed-to-start"))
                        .color(NamedTextColor.GRAY)));
        PrepareKeys.setAction(item, "publish");
        return item;
    }

    private static ItemStack saveDraftItem() {
        ItemStack item = PrepareKeys.item(Material.WRITABLE_BOOK,
                Component.text(GuiConfig.text("map-editor.toolbar.save-draft")).color(NamedTextColor.GOLD),
                List.of(Component.text(GuiConfig.text("map-editor.toolbar.save-current-block-changes-does-not-require-all-points-to-be-completed")).color(NamedTextColor.GRAY),
                        Component.text(GuiConfig.text("map-editor.toolbar.the-draft-is-still-unavailable-for-competition-publish-after-completing-the-configuration")).color(NamedTextColor.YELLOW)));
        PrepareKeys.setAction(item, "save-draft");
        return item;
    }

    /** Untagged on purpose: WorldEdit's wand interact handlers must not be cancelled. */
    private static ItemStack wandItem() {
        return PrepareKeys.item(Material.WOODEN_AXE,
                Component.text(GuiConfig.text("map-editor.toolbar.worldedit-selection-tool")).color(NamedTextColor.GOLD),
                List.of(Component.text(GuiConfig.text("map-editor.toolbar.left-click-to-select-pos1-right-click-to-select-pos2-for-border-template-step")).color(NamedTextColor.GRAY)));
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }
}

package ink.ziip.championshipscore.api.game.buildmart.gui;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartArea;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BlueprintBlock;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import ink.ziip.championshipscore.api.game.buildmart.state.BuildSlot;
import ink.ziip.championshipscore.api.game.buildmart.state.TeamBuildState;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the hub blueprint-library item ("蓝图册") and the chest menu it opens. Each menu icon represents
 * a currently-offered order; clicking one assigns it to a free build slot. Item identity is carried in
 * persistent data so the listener can resolve the book / the picked blueprint without parsing names.
 */
public final class BuildMartMenu {
    public static final NamespacedKey BOOK_KEY = new NamespacedKey(ChampionshipsCore.getInstance(), "buildmart_book");
    public static final NamespacedKey BLUEPRINT_KEY = new NamespacedKey(ChampionshipsCore.getInstance(), "buildmart_blueprint");
    /** Tags a submit button with the slot it commits: {@code N0/N1/N2} for normal plots, {@code G} for golden. */
    public static final NamespacedKey SUBMIT_KEY = new NamespacedKey(ChampionshipsCore.getInstance(), "buildmart_submit");
    /** Tags a refresh button with the normal slot it re-rolls: {@code N0/N1/N2}. */
    public static final NamespacedKey REFRESH_KEY = new NamespacedKey(ChampionshipsCore.getInstance(), "buildmart_refresh");

    private BuildMartMenu() {
    }

    /** The bound library book handed to every player; right-clicking it opens the menu. */
    public static ItemStack createBook() {
        ItemStack book = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("§e蓝图册 §7(右键打开)"));
        meta.getPersistentDataContainer().set(BOOK_KEY, PersistentDataType.BYTE, (byte) 1);
        book.setItemMeta(meta);
        return book;
    }

    public static boolean isBook(@Nullable ItemStack item) {
        if (item == null || item.getType() != Material.KNOWLEDGE_BOOK) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(BOOK_KEY, PersistentDataType.BYTE);
    }

    /** Reads the blueprint id tagged on a menu icon, or {@code null} when it isn't one. */
    @Nullable
    public static String blueprintIdOf(@Nullable ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(BLUEPRINT_KEY, PersistentDataType.STRING);
    }

    /** Reads the slot id tagged on a submit button ({@code N0/N1/N2/G}), or {@code null} when it isn't one. */
    @Nullable
    public static String submitIdOf(@Nullable ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(SUBMIT_KEY, PersistentDataType.STRING);
    }

    /** Reads the slot id tagged on a refresh button ({@code N0/N1/N2}), or {@code null} when it isn't one. */
    @Nullable
    public static String refreshIdOf(@Nullable ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(REFRESH_KEY, PersistentDataType.STRING);
    }

    /**
     * Opens the menu for {@code player}: the selectable library orders on the top row(s), the team's active
     * build plots as submit buttons on the next row, and a refresh button under each normal plot on the last
     * row. Clicking a submit/refresh button arms it for a confirming second click (see the handler).
     */
    public static void open(Player player, BuildMartArea area, ChampionshipTeam team) {
        if (area.getBlueprintLibrary() == null) return;
        List<BuildMartBlueprint> current = area.getBlueprintLibrary().current();
        int libraryRows = Math.max(1, (int) Math.ceil(current.size() / 9.0));
        int rows = libraryRows + 2; // reserve the last two rows for the submit + refresh sections
        BlueprintMenuHolder holder = new BlueprintMenuHolder(area, team);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .deserialize("§e蓝图库 §7- 选择 / 提交"));
        holder.setInventory(inventory);

        int slot = 0;
        for (BuildMartBlueprint blueprint : current) {
            inventory.setItem(slot++, icon(blueprint));
        }
        renderActionRows(holder, area);
        player.openInventory(inventory);
    }

    /**
     * (Re)draws the submit + refresh rows of an open menu from the team's live state and the holder's armed
     * action. Called on open and after every submit/refresh/arm click so progress counts and confirmation
     * prompts stay current. The library rows above are left untouched.
     */
    public static void renderActionRows(BlueprintMenuHolder holder, BuildMartArea area) {
        Inventory inventory = holder.getInventory();
        if (inventory == null) return;
        TeamBuildState state = area.teamStateOf(holder.getTeam());
        if (state == null) return;
        int rows = inventory.getSize() / 9;
        int submitBase = (rows - 2) * 9;
        int refreshBase = (rows - 1) * 9;
        String armed = holder.getArmed();

        List<BuildSlot> normals = state.getNormalSlots();
        for (int i = 0; i < normals.size(); i++) {
            String slotId = "N" + i;
            inventory.setItem(submitBase + i, submitIcon(normals.get(i), slotId, ("SUBMIT:" + slotId).equals(armed)));
            inventory.setItem(refreshBase + i, refreshIcon(normals.get(i), slotId, ("REFRESH:" + slotId).equals(armed)));
        }
        inventory.setItem(submitBase + 8, submitIcon(state.getGoldenSlot(), "G", "SUBMIT:G".equals(armed)));
    }

    /** A submit button for one plot, or a gray "empty plot" placeholder when nothing is assigned there. */
    private static ItemStack submitIcon(BuildSlot buildSlot, String slotId, boolean armed) {
        BuildMartBlueprint blueprint = buildSlot.getBlueprint();
        boolean golden = buildSlot.isGolden();
        if (blueprint == null) {
            ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = empty.getItemMeta();
            meta.displayName(net.kyori.adventure.text.Component.text(golden ? "§8黄金地块 §7(空闲)" : "§8建造地块 §7(空闲)"));
            empty.setItemMeta(meta);
            return empty;
        }

        int matched = buildSlot.getLastMatched();
        int total = blueprint.blockCount();
        ItemStack button = new ItemStack(golden ? Material.GOLD_BLOCK : Material.LIME_DYE);
        ItemMeta meta = button.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text((golden ? "§6✦ " : "§a") + blueprint.getDisplayName()
                + " §6" + blueprint.starString()));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("§7进度: §e" + matched + "§7/§e" + total));
        if (armed) {
            lore.add(net.kyori.adventure.text.Component.text("§c⚠ 再次点击确认提交"));
            lore.add(net.kyori.adventure.text.Component.text("§7点击别处取消"));
        } else {
            lore.add(net.kyori.adventure.text.Component.text("§a点击提交进行验收"));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(SUBMIT_KEY, PersistentDataType.STRING, slotId);
        button.setItemMeta(meta);
        return button;
    }

    /**
     * A refresh button under a normal plot: re-rolls that plot's blueprint once per game. Rendered only for
     * an occupied plot (nothing to refresh when empty); shows a spent state once the single use is gone.
     */
    private static ItemStack refreshIcon(BuildSlot buildSlot, String slotId, boolean armed) {
        // No blueprint assigned → leave the cell empty (nothing to refresh).
        if (buildSlot.getBlueprint() == null) {
            return null;
        }
        if (buildSlot.isRefreshUsed()) {
            ItemStack used = new ItemStack(Material.GRAY_DYE);
            ItemMeta meta = used.getItemMeta();
            meta.displayName(net.kyori.adventure.text.Component.text("§8↻ 刷新 §7(已用完)"));
            used.setItemMeta(meta);
            return used;
        }

        ItemStack button = new ItemStack(Material.CLOCK);
        ItemMeta meta = button.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("§b↻ 刷新蓝图 §7(剩 1 次)"));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        if (armed) {
            lore.add(net.kyori.adventure.text.Component.text("§c⚠ 再次点击确认刷新"));
            lore.add(net.kyori.adventure.text.Component.text("§c当前建造将被清空"));
            lore.add(net.kyori.adventure.text.Component.text("§7点击别处取消"));
        } else {
            lore.add(net.kyori.adventure.text.Component.text("§7将本地块换成新的随机蓝图"));
            lore.add(net.kyori.adventure.text.Component.text("§c已建部分会被清空，每地块限一次"));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(REFRESH_KEY, PersistentDataType.STRING, slotId);
        button.setItemMeta(meta);
        return button;
    }

    private static ItemStack icon(BuildMartBlueprint blueprint) {
        ItemStack icon = new ItemStack(iconMaterial(blueprint));
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("§b" + blueprint.getDisplayName()
                + " §6" + blueprint.starString()));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("§7星级: §e" + blueprint.getStars()));
        lore.add(net.kyori.adventure.text.Component.text("§7方块数: §e" + blueprint.blockCount()));
        lore.add(net.kyori.adventure.text.Component.text("§a点击分配到空闲建造地块"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(BLUEPRINT_KEY, PersistentDataType.STRING, blueprint.getId());
        icon.setItemMeta(meta);
        return icon;
    }

    /** Uses the blueprint's first solid block as the icon, falling back to paper. */
    private static Material iconMaterial(BuildMartBlueprint blueprint) {
        for (BlueprintBlock b : blueprint.getBlocks()) {
            Material m = b.getBlockData().getMaterial();
            if (m.isItem() && !m.isAir()) return m;
        }
        return Material.PAPER;
    }
}

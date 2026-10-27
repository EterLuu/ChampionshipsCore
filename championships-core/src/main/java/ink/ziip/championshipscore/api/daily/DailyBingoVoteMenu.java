package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.daily.DailyBingoVoteController.Phase;
import ink.ziip.championshipscore.api.game.bingo.util.MessageService;
import ink.ziip.championshipscore.api.gui.GuiItemLore;
import ink.ziip.championshipscore.api.gui.GuiMenu;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.api.gui.MenuLayout;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.GuiText;
import ink.ziip.championshipscore.protocol.BingoVariantRules;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockDataMeta;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** A single phase's private view; all votes and eligibility remain owned by the controller. */
final class DailyBingoVoteMenu extends GuiMenu {
    static final String FAMILY = MenuId.DAILY_BINGO_VOTE.path();
    final DailyBingoVoteController owner;
    final Player connection;
    final CompletableFuture<BingoVariantRules> vote;
    final Phase phase;
    final MenuLayout layout;
    final Map<Integer, Object> targets = new HashMap<>();

    DailyBingoVoteMenu(
            DailyBingoVoteController owner,
            Player player,
            CompletableFuture<BingoVariantRules> vote,
            Phase phase) {
        super(player.getUniqueId());
        this.owner = owner;
        this.connection = player;
        this.vote = vote;
        this.phase = phase;
        MenuId screen = screen(phase);
        layout = layout(phase);
        String titleKey =
                switch (phase) {
                    case MODE -> "vote.menu_title";
                    case DIFFICULTY -> "difficulty_vote.menu_title";
                    case LINES -> "lines_vote.menu_title";
                    case GENESIS -> "genesis.menu_title";
                };
        inventory =
                Bukkit.createInventory(
                        this,
                        layout.size(),
                        GuiConfig.component(
                                screen.path() + ".title",
                                Map.of("title", MessageService.global().tr(titleKey))));
    }

    private static MenuId screen(Phase phase) {
        return switch (phase) {
            case MODE -> MenuId.DAILY_BINGO_VOTE_MODE;
            case DIFFICULTY -> MenuId.DAILY_BINGO_VOTE_DIFFICULTY;
            case LINES -> MenuId.DAILY_BINGO_VOTE_LINES;
            case GENESIS -> MenuId.DAILY_BINGO_VOTE_GENESIS;
        };
    }

    static MenuLayout layout(Phase phase) {
        MenuId screen = screen(phase);
        var controls =
                new LinkedHashMap<>(
                        Map.of("options-previous", 19, "options-page", 20, "options-next", 21));
        if (phase != Phase.GENESIS)
            controls.putAll(Map.of("previous", 18, "page", 22, "close", 24, "next", 26));
        return MenuLayout.load(
                screen.path(),
                FAMILY,
                GuiConfig.menu(screen.path(), 27, Component.empty(), List.of()).size(),
                phase == Phase.GENESIS ? MenuLayout.range(9, 18) : MenuLayout.range(11, 16),
                MenuLayout.range(0, 27),
                controls);
    }

    void render(List<?> options, Function<Object, ItemStack> renderer) {
        inventory.clear();
        targets.clear();
        page = layout.clampPage(page, options.size());
        pageCount = layout.pageCount(options.size());
        ItemStack border = controlItem("border", Map.of());
        for (int slot : layout.border()) inventory.setItem(slot, border);
        var slots = layout.pageSlots(page, options.size());
        for (int i = 0; i < slots.size(); i++) {
            Object choice = options.get(page * layout.content().size() + i);
            targets.put(slots.get(i), choice);
            inventory.setItem(slots.get(i), renderer.apply(choice));
        }
        MessageService messages = MessageService.global();
        if (page > 0) put("options-previous", Map.of("name", messages.tr("vote.previous")));
        if (page + 1 < pageCount) put("options-next", Map.of("name", messages.tr("vote.next")));
        if (pageCount > 1)
            put(
                    "options-page",
                    Map.of(
                            "name",
                            messages.tr("vote.option_page", page + 1, pageCount),
                            "count",
                            options.size()));
        if (phase == Phase.GENESIS) return;
        if (phase != Phase.MODE) put("previous", Map.of("name", messages.tr("vote.previous")));
        if (phase != Phase.LINES) put("next", Map.of("name", messages.tr("vote.next")));
        String pageKey =
                switch (phase) {
                    case MODE -> "vote.page_mode";
                    case DIFFICULTY -> "vote.page_difficulty";
                    case LINES -> "vote.page_lines";
                    case GENESIS ->
                            throw new IllegalStateException("Genesis has no phase navigation");
                };
        put(
                "page",
                Map.of(
                        "name",
                        messages.tr(pageKey),
                        "navigation",
                        messages.tr("vote.free_navigation")));
        put("close", Map.of("name", messages.tr("vote.close")));
    }

    boolean changePage(int direction) {
        int next = Math.max(0, Math.min(pageCount - 1, page + direction));
        if (next == page) return false;
        page = next;
        return true;
    }

    private void put(String id, Map<String, ?> values) {
        inventory.setItem(layout.control(id), controlItem(id, values));
    }

    private ItemStack controlItem(String id, Map<String, ?> values) {
        return ConfiguredGui.item(
                FAMILY + ".items." + id,
                values,
                Material.PAPER,
                Component.empty(),
                List.of(),
                false);
    }

    static ItemStack option(
            String id,
            String state,
            Map<String, ?> values,
            List<String> description,
            Material fallback,
            int count,
            int lightLevel) {
        String path = FAMILY + ".items." + id;
        var spec =
                GuiConfig.item(
                        path,
                        state,
                        values,
                        new GuiConfig.ItemSpec(-1, fallback, Component.empty(), List.of(), false));
        var rows =
                description.stream()
                        .map(line -> GuiText.row(FAMILY + ".presentation.description-row", line))
                        .toList();
        ItemStack item =
                GuiMenu.item(
                        spec.material(),
                        spec.title(),
                        GuiItemLore.expand(
                                GuiConfig.itemLoreTemplate(path, state),
                                values,
                                "%description%",
                                rows),
                        spec.glint());
        var meta = item.getItemMeta();
        if (item.getType() == Material.LIGHT
                && lightLevel > 0
                && meta instanceof BlockDataMeta blockMeta) {
            var light = (org.bukkit.block.data.type.Light) Material.LIGHT.createBlockData();
            light.setLevel(lightLevel);
            blockMeta.setBlockData(light);
        }
        meta.setMaxStackSize(99);
        item.setItemMeta(meta);
        item.setAmount(Math.max(1, count));
        return item;
    }
}

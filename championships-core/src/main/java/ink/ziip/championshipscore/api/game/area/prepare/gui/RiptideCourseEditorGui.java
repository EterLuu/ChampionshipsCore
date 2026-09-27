package ink.ziip.championshipscore.api.game.area.prepare.gui;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.riptiderush.*;
import ink.ziip.championshipscore.api.gui.MenuId;
import ink.ziip.championshipscore.configuration.config.message.ConfiguredGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

import static ink.ziip.championshipscore.api.game.area.prepare.gui.RiptideEditorPage.Screen.*;

/** The same list -> item -> chooser/confirmation navigation as other map editors, scoped by mechanic. */
public final class RiptideCourseEditorGui {
    public static final String PATH = MenuId.RIPTIDE_RUSH_EDITOR.path();
    private static final int PREVIOUS = 45, NEXT = 53, BACK_LIST = 49, BACK_DETAIL = 26;
    private RiptideCourseEditorGui() { }

    static int[] choiceSlots(int count) {
        if (count < 1 || count > 18) throw new IllegalArgumentException("choice count must fit two inventory rows");
        if (count > 9) {
            int first = (count + 1) / 2, second = count - first;
            return java.util.stream.IntStream.concat(
                    java.util.stream.IntStream.range((9 - first) / 2, (9 - first) / 2 + first),
                    java.util.stream.IntStream.range(9 + (9 - second) / 2, 9 + (9 - second) / 2 + second)).toArray();
        }
        int start = 9 + (9 - count) / 2;
        return java.util.stream.IntStream.range(start, start + count).toArray();
    }

    public static final class Holder implements InventoryHolder {
        final PrepareSession session;
        RiptideEditorPage page;
        List<RiptideLevelTemplate> rows = List.of();
        RiptideCoursePlan plan;
        Inventory inventory;
        Holder(PrepareSession session, RiptideEditorPage page) { this.session = session; this.page = page; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
    private static RiptideRushConfig config(PrepareSession s) { return (RiptideRushConfig) s.getTarget().config(); }
    private static String screen(RiptideEditorPage page) {
        if (page.isSidePool()) return "side-pool";
        if (page.screen() == STOPPED_CHALLENGES) return "stopped";
        return page.screen().name().toLowerCase(Locale.ROOT);
    }
    private static boolean list(RiptideEditorPage page) { return page.screen() == CATEGORY || page.screen() == ADD || page.screen() == PREVIEW; }
    private static String text(String key, Map<String, ?> values) { return GuiConfig.text(PATH + ".items." + key + ".title", values); }
    private static String text(String key) { return text(key, Map.of()); }

    public static void open(PrepareSessionManager manager, Player player, PrepareSession session, RiptideEditorPage page) {
        if (!available(manager, player, session)) return;
        try {
            var draft = RiptideWorkshop.get(session);
            if (draft != null && page.screen() != WORKSHOP && page.screen() != ABANDON)
                page = draft.origin().child(WORKSHOP, draft.origin().id(), null);
            var h = new Holder(session, page);
            var c = config(session);
            String name = page.id() != null && page.screen() != PLACEMENT ? entry(h).name() : "";
            String titlePath = PATH + "." + screen(page) + ".title";
            h.inventory = Bukkit.createInventory(h, list(page) ? 54 : 27, GuiConfig.component(titlePath,
                    Map.of("type", page.type() == null ? "" : page.type() == RiptideLevelType.COLOR_FLOOR ? "踩色" : page.type().displayName(), "name", name)));
            for (int slot = 0; slot < h.inventory.getSize(); slot++) button(h, slot, "filler");
            switch (page.screen()) {
                case STOPPED_CHALLENGES -> {
                    var allocation = c.previewStoppedAllocation();
                    button(h, 4, "stopped-summary", null, Map.of("quota", c.getStoppedCount()));
                    button(h, 11, "stopped-floor", null, Map.of("quota", allocation.colorFloor(), "weight", c.getColorFloorWeight()));
                    button(h, 13, "stopped-dodge", null, Map.of("quota", allocation.dodge(), "weight", c.getDodgeWeight()));
                    button(h, 15, "stopped-side", null, Map.of("quota", allocation.sideSweep(), "weight", c.getSideSweepWeight()));
                    button(h, 22, "stopped-mode", c.getStoppedAllocationMode().toLowerCase(Locale.ROOT), Map.of());
                }
                case SIDE -> {
                    button(h, 4, "side-quota", null, Map.of("quota", c.previewStoppedAllocation().sideSweep()));
                    value(h, 13, "side-weight", c.getSideSweepWeight());
                    button(h, 11, "side-pool"); button(h, 15, "preview");
                }
                case CATEGORY -> category(h);
                case ENTRY -> {
                    var t = entry(h);
                    h.inventory.setItem(4, template(t, false));
                    value(h, 10, "name", t.name());
                    if (t.type() == RiptideLevelType.MATH || t.type() == RiptideLevelType.RHYTHM) value(h, 11, "operation", RiptideLevelTemplate.variantName(t.variant()));
                    if (t.type() == RiptideLevelType.PASS || t.type() == RiptideLevelType.MATH || t.type() == RiptideLevelType.COLOR_FLOOR) button(h, 20, "edit-building");
                    button(h, 12, "enabled", t.enabled() ? "on" : "off", Map.of());
                    value(h, 13, "weight", t.weight());
                    if (t.type() != RiptideLevelType.PASS) value(h, 14, "max-uses", t.maxUses());
                    value(h, 15, "difficulty", t.difficulty());
                    button(h, 16, "duplicate"); button(h, 18, "try-template"); button(h, 22, "delete");
                }
                case ADD -> {
                    h.rows = RiptideEditorModel.rows(c, page.type());
                    h.page = page.atPage(RiptideEditorModel.clampPage(page.page(), h.rows.size(), RiptideEditorModel.CATEGORY_PAGE_SIZE));
                    int first = h.page.page() * RiptideEditorModel.CATEGORY_PAGE_SIZE;
                    for (int i = 0; i < RiptideEditorModel.CATEGORY_PAGE_SIZE && first + i < h.rows.size(); i++) {
                        var source = h.rows.get(first + i);
                        h.inventory.setItem(i, ConfiguredGui.item(PATH + ".items.copy-source", null,
                                Map.of("name", source.name()), source.type().icon(), Component.empty(), List.of(), false));
                    }
                    button(h, 46, page.type() == RiptideLevelType.RHYTHM ? "new-rhythm" : page.type() == RiptideLevelType.DODGE ? "new-dodge" : "blank-building");
                    pages(h, h.rows.size(), RiptideEditorModel.CATEGORY_PAGE_SIZE);
                }
                case CHOICE -> {
                    List<String> options = "difficulty".equals(page.field()) ? List.of("1", "2", "3")
                            : concreteVariants(page.type());
                    String selected = "difficulty".equals(page.field())
                            ? Integer.toString(entry(h).difficulty()) : entry(h).variant();
                    int[] slots = choiceSlots(options.size());
                    for (int i = 0; i < options.size(); i++) {
                        String option = options.get(i);
                        button(h, slots[i], "difficulty".equals(page.field()) ? "difficulty-" + option : "option-" + option.toLowerCase(Locale.ROOT).replace('_', '-'),
                                selected.equals(option) ? "selected" : null, Map.of());
                    }
                }
                case WORKSHOP -> {
                    h.inventory.setItem(4, template(entry(h), false));
                    button(h, 11, "continue-building"); button(h, 13, "save-building"); button(h, 15, "discard-building");
                    button(h, 22, h.page.type() == RiptideLevelType.COLOR_FLOOR ? "floor-building-help"
                            : h.page.type() == RiptideLevelType.MATH ? "math-building-help" : "pass-building-help",
                            null, RiptideWorkshop.dimensions(c));
                }
                case ABANDON -> { button(h, 11, "confirm-discard-building"); button(h, 15, "continue-building"); }
                case DELETE -> {
                    h.inventory.setItem(4, template(entry(h), false));
                    button(h, 11, "confirm-delete"); button(h, 15, "cancel");
                }
                case COURSE -> {
                    button(h, 4, "course-summary", null, Map.of("pass", c.getPassCount(), "math", c.getMathCount(),
                            "stopped", c.getStoppedCount(), "rhythm", c.getRhythmCount()));
                    button(h, 10, "automatic-layout"); button(h, 12, "preview");
                    value(h, 14, "seed", c.getPreviewSeed());
                    button(h, 16, "fixed", c.getFixedSeed().isBlank() ? "random" : "fixed", Map.of("value", c.getFixedSeed()));
                    button(h, 22, "constraints");
                }
                case PREVIEW -> preview(h);
                case PLACEMENT -> {
                    h.plan = RiptideCoursePlanner.plan(c, c.getPreviewSeed());
                    var level = h.plan.levels().get(Integer.parseInt(page.id()));
                    h.inventory.setItem(4, placement(level));
                    button(h, 11, "inspect"); button(h, 15, "try-placement");
                }
            }
            button(h, list(page) ? BACK_LIST : BACK_DETAIL, page.screen() == WORKSHOP ? "continue-building" : "back");
            player.openInventory(h.inventory);
        } catch (RuntimeException error) { Utils.sendAdminError(player, error.getMessage()); }
    }

    private static void category(Holder h) {
        var c = config(h.session);
        h.rows = RiptideEditorModel.rows(c, h.page.type());
        h.page = h.page.atPage(RiptideEditorModel.clampPage(h.page.page(), h.rows.size(), RiptideEditorModel.CATEGORY_PAGE_SIZE));
        int first = h.page.page() * RiptideEditorModel.CATEGORY_PAGE_SIZE;
        for (int slot = 0; slot < RiptideEditorModel.CATEGORY_PAGE_SIZE && first + slot < h.rows.size(); slot++)
            h.inventory.setItem(slot, template(h.rows.get(first + slot), true));
        if (h.rows.isEmpty()) button(h, 13, "empty", null, Map.of("type", h.page.type().displayName()));
        if (h.page.isSidePool()) button(h, 37, "side-rules");
        else if (h.page.type() == RiptideLevelType.COLOR_FLOOR || h.page.type() == RiptideLevelType.DODGE)
            button(h, 37, "stopped-child-weight", null, Map.of("weight", c.stoppedChildWeight(h.page.type()),
                    "quota", h.page.type() == RiptideLevelType.COLOR_FLOOR ? c.previewStoppedAllocation().colorFloor() : c.previewStoppedAllocation().dodge()));
        else value(h, 37, "quota", RiptideCoursePlanner.quota(c, h.page.type()));
        if (h.page.type() == RiptideLevelType.MATH) {
            value(h, 39, "minimum", c.getMinimumOperand()); value(h, 41, "maximum", c.getMaximumOperand());
        } else if (h.page.type() == RiptideLevelType.COLOR_FLOOR) value(h, 39, "floor-timing", "32 / 28 / 24 / 20 / 16");
        button(h, 46, "add", null, Map.of("type", h.page.type().displayName()));
        button(h, 50, "category-summary", null, Map.of("count", h.rows.size(), "enabled", h.rows.stream().filter(RiptideLevelTemplate::enabled).count()));
        pages(h, h.rows.size(), RiptideEditorModel.CATEGORY_PAGE_SIZE);
    }

    private static void preview(Holder h) {
        var c = config(h.session);
        try {
            h.plan = RiptideCoursePlanner.plan(c, c.getPreviewSeed());
            h.page = h.page.atPage(RiptideEditorModel.clampPage(h.page.page(), h.plan.levels().size(), 45));
            for (int slot = 0; slot < 45 && h.page.page() * 45 + slot < h.plan.levels().size(); slot++)
                h.inventory.setItem(slot, placement(h.plan.levels().get(h.page.page() * 45 + slot)));
            value(h, 51, "duration", String.format(Locale.ROOT, "%.2f", h.plan.estimatedTicks() / 20D));
            pages(h, h.plan.levels().size(), 45);
        } catch (IllegalArgumentException error) {
            button(h, 22, "invalid", null, Map.of("error", error.getMessage()));
        }
        button(h, 46, "reroll"); button(h, 47, "build"); button(h, 48, "play");
        value(h, 50, "seed-info", c.getPreviewSeed());
    }

    public static boolean available(PrepareSessionManager manager, Player player, PrepareSession session) {
        if (!player.hasPermission("cc.admin") || manager.getSession(player) != session) { player.closeInventory(); return false; }
        if (!session.getTarget().canSaveMap() || RiptideCourseGenerator.isGenerating(Bukkit.getWorld(session.getTarget().worldName()))
                || RiptideCourseTrial.isActive(session.getTarget().worldName())) {
            Utils.sendAdminError(player, text("busy")); return false;
        }
        return true;
    }

    public static void handleClick(PrepareSessionManager manager, InventoryClickEvent event, Player player, Holder h) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory() || !available(manager, player, h.session)) return;
        int slot = event.getRawSlot();
        var construction = RiptideWorkshop.get(h.session);
        if (construction != null && h.page.screen() != WORKSHOP && h.page.screen() != ABANDON) {
            open(manager, player, h.session, construction.origin().child(WORKSHOP, construction.origin().id(), null)); return;
        }
        try {
            if (slot == (list(h.page) ? BACK_LIST : BACK_DETAIL)) { back(manager, player, h); return; }
            if (list(h.page) && (slot == PREVIOUS || slot == NEXT)) {
                int count = h.page.screen() != PREVIEW ? h.rows.size() : h.plan == null ? 0 : h.plan.levels().size();
                int size = h.page.screen() != PREVIEW ? RiptideEditorModel.CATEGORY_PAGE_SIZE : 45;
                int target = RiptideEditorModel.clampPage(h.page.page() + (slot == PREVIOUS ? -1 : 1), count, size);
                open(manager, player, h.session, h.page.atPage(target)); return;
            }
            switch (h.page.screen()) {
                case STOPPED_CHALLENGES -> {
                    var c = config(h.session);
                    if (slot == 4) number(manager, player, h, "stopped-quota", c.getStoppedCount(), 0, 64, c::setStoppedCount);
                    else if (slot == 11) open(manager, player, h.session, RiptideEditorPage.category(RiptideLevelType.COLOR_FLOOR));
                    else if (slot == 13) open(manager, player, h.session, RiptideEditorPage.category(RiptideLevelType.DODGE));
                    else if (slot == 15) open(manager, player, h.session, h.page.child(SIDE, null, null));
                    else if (slot == 22) { c.setStoppedAllocationMode(c.getStoppedAllocationMode().equalsIgnoreCase("WEIGHTED") ? "RANDOM" : "WEIGHTED"); changed(h); reopen(manager, player, h); }
                }
                case SIDE -> {
                    if (slot == 11) open(manager, player, h.session, h.page.sidePool());
                    else if (slot == 15) open(manager, player, h.session, h.page.child(PREVIEW, null, null));
                    else if (slot == 13) number(manager, player, h, "side-weight", config(h.session).getSideSweepWeight(), 0, 100, config(h.session)::setSideSweepWeight);
                }
                case CATEGORY -> categoryClick(manager, player, h, slot);
                case ENTRY -> entryClick(manager, player, h, slot);
                case ADD -> addClick(manager, player, h, slot);
                case CHOICE -> choiceClick(manager, player, h, slot);
                case WORKSHOP -> {
                    if (slot == 11) continueBuilding(player, h);
                    else if (slot == 13) saveBuilding(manager, player, h);
                    else if (slot == 15) open(manager, player, h.session, h.page.child(ABANDON, h.page.id(), null));
                }
                case ABANDON -> {
                    if (slot == 15) continueBuilding(player, h);
                    else if (slot == 11) {
                        var draft = RiptideWorkshop.finish(h.session);
                        open(manager, player, h.session, draft.origin());
                    }
                }
                case DELETE -> {
                    if (slot == 15) back(manager, player, h);
                    else if (slot == 11) {
                        RiptideEditorModel.remove(config(h.session), h.page.type(), h.page.id()); changed(h);
                        open(manager, player, h.session, h.page.parent().parent());
                    }
                }
                case COURSE -> courseClick(manager, player, h, slot);
                case PREVIEW -> {
                    if (slot == 46) {
                        config(h.session).setPreviewSeed(java.util.concurrent.ThreadLocalRandom.current().nextLong()); changed(h); reopen(manager, player, h);
                    } else if (h.plan != null) {
                        if (slot == 47 || slot == 48) generate(manager, player, h, h.plan, slot == 48, null);
                        else if (slot >= 0 && slot < 45 && h.page.page() * 45 + slot < h.plan.levels().size())
                            open(manager, player, h.session, h.page.child(PLACEMENT, Integer.toString(h.page.page() * 45 + slot), null));
                    }
                }
                case PLACEMENT -> {
                    if (slot == 11 || slot == 15)
                        generate(manager, player, h, h.plan, slot == 15, h.plan.levels().get(Integer.parseInt(h.page.id())));
                }
            }
        } catch (RuntimeException error) { Utils.sendAdminError(player, error.getMessage()); }
    }

    private static void categoryClick(PrepareSessionManager manager, Player player, Holder h, int slot) {
        var c = config(h.session);
        int index = h.page.page() * RiptideEditorModel.CATEGORY_PAGE_SIZE + slot;
        if (slot >= 0 && slot < RiptideEditorModel.CATEGORY_PAGE_SIZE && index < h.rows.size()) {
            open(manager, player, h.session, h.page.child(ENTRY, h.rows.get(index).id(), null)); return;
        }
        switch (slot) {
            case 46 -> open(manager, player, h.session, h.page.child(ADD, null, null));
            case 37 -> {
                if (h.page.type() == RiptideLevelType.COLOR_FLOOR || h.page.type() == RiptideLevelType.DODGE)
                    number(manager, player, h, "child-weight", c.stoppedChildWeight(h.page.type()), 0, 100,
                            value -> c.setStoppedChildWeight(h.page.type(), value));
                else if (!h.page.isSidePool()) number(manager, player, h, "quota", RiptideCoursePlanner.quota(c, h.page.type()),
                        h.page.type() == RiptideLevelType.PASS ? 2 : 0, 64,
                        value -> RiptideEditorModel.quota(c, h.page.type(), value));
            }
            case 39 -> {
                if (h.page.type() == RiptideLevelType.MATH)
                    number(manager, player, h, "minimum", c.getMinimumOperand(), 0, c.getMaximumOperand(), c::setMinimumOperand);
            }
            case 41 -> {
                if (h.page.type() == RiptideLevelType.MATH)
                    number(manager, player, h, "maximum", c.getMaximumOperand(), c.getMinimumOperand(), Integer.MAX_VALUE / 2, c::setMaximumOperand);
            }
            default -> { }
        }
    }

    private static void entryClick(PrepareSessionManager manager, Player player, Holder h, int slot) {
        var c = config(h.session); var t = entry(h);
        switch (slot) {
            case 10 -> input(manager, player, h, "name", t.name(), value -> {
                var row = t.serialize(); row.put("name", value); RiptideLevelTemplate.parse(row); return null;
            }, value -> update(h, "name", value));
            case 11 -> {
                if (t.type() == RiptideLevelType.MATH || t.type() == RiptideLevelType.RHYTHM) open(manager, player, h.session, h.page.child(CHOICE, t.id(), "variant"));
            }
            case 20 -> { if (t.type() == RiptideLevelType.PASS || t.type() == RiptideLevelType.MATH || t.type() == RiptideLevelType.COLOR_FLOOR) startBuilding(manager, player, h, false, false); }
            case 12 -> { update(h, "enabled", !t.enabled()); changed(h); reopen(manager, player, h); }
            case 13 -> number(manager, player, h, "weight", t.weight(), 1, 100, value -> update(h, "weight", value));
            case 14 -> {
                if (t.type() != RiptideLevelType.PASS)
                    number(manager, player, h, "max-uses", t.maxUses(), 1, 64, value -> update(h, "max-uses", value));
            }
            case 15 -> open(manager, player, h.session, h.page.child(CHOICE, t.id(), "difficulty"));
            case 16 -> {
                var copy = RiptideEditorModel.duplicate(c, h.page.type(), t.id()); changed(h);
                openNew(manager, player, h, copy);
            }
            case 18 -> {
                var plan = RiptideCoursePlanner.templatePreview(c, t, c.getPreviewSeed());
                generate(manager, player, h, plan, true, plan.levels().getFirst());
            }
            case 22 -> open(manager, player, h.session, h.page.child(DELETE, t.id(), null));
            default -> { }
        }
    }

    private static void addClick(PrepareSessionManager manager, Player player, Holder h, int slot) {
        if (slot == 46 && h.page.type() == RiptideLevelType.RHYTHM) {
            var template = RiptideEditorModel.add(config(h.session), h.page.type(), "SHUTTER");
            changed(h); openNew(manager, player, h, template); return;
        }
        if (slot == 46 && h.page.type() == RiptideLevelType.DODGE) {
            var template = RiptideEditorModel.add(config(h.session), h.page.type(), "ZOMBIE");
            changed(h); openNew(manager, player, h, template); return;
        }
        if (slot == 46) { createBuilding(manager, player, h); return; }
        int index = h.page.page() * RiptideEditorModel.CATEGORY_PAGE_SIZE + slot;
        if (slot < 0 || slot >= RiptideEditorModel.CATEGORY_PAGE_SIZE || index >= h.rows.size()) return;
        var copy = RiptideEditorModel.duplicate(config(h.session), h.page.type(), h.rows.get(index).id());
        changed(h); openNew(manager, player, h, copy);
    }

    private static void choiceClick(PrepareSessionManager manager, Player player, Holder h, int slot) {
        int selected = -1;
        var options = "difficulty".equals(h.page.field()) ? List.of("1", "2", "3") : concreteVariants(h.page.type());
        int[] slots = choiceSlots(options.size());
        for (int i = 0; i < slots.length; i++) if (slot == slots[i]) selected = i;
        if (selected < 0 || selected >= options.size()) return;
        String value = options.get(selected);
        update(h, h.page.field(), h.page.field().equals("difficulty") ? Integer.parseInt(value) : value);
        changed(h); back(manager, player, h);
    }

    private static void courseClick(PrepareSessionManager manager, Player player, Holder h, int slot) {
        var c = config(h.session);
        switch (slot) {
            case 12 -> open(manager, player, h.session, h.page.child(PREVIEW, null, null));
            case 14 -> input(manager, player, h, "seed", Long.toString(c.getPreviewSeed()),
                    value -> { Long.parseLong(value); return null; }, value -> c.setPreviewSeed(Long.parseLong(value)));
            case 16 -> {
                c.setFixedSeed(c.getFixedSeed().isBlank() ? Long.toString(c.getPreviewSeed()) : ""); changed(h); reopen(manager, player, h);
            }
            default -> { }
        }
    }

    private static void input(PrepareSessionManager manager, Player player, Holder h, String key, String initial,
                               Function<String, String> validate, Consumer<String> setter) {
        AnvilInputGui.openEditorText(player, text(key, Map.of("value", initial)), initial, value -> {
            if (!available(manager, player, h.session)) return text("expired");
            try { return validate.apply(value); }
            catch (NumberFormatException error) { return text("invalid-number"); }
            catch (IllegalArgumentException error) { return error.getMessage(); }
        }, value -> {
            if (!available(manager, player, h.session)) return;
            try { setter.accept(value); changed(h); reopen(manager, player, h); }
            catch (RuntimeException error) { Utils.sendAdminError(player, error.getMessage()); reopen(manager, player, h); }
        }, () -> later(manager, player, h, true));
    }

    private static void number(PrepareSessionManager manager, Player player, Holder h, String key,
                                int value, int min, int max, IntConsumer setter) {
        input(manager, player, h, key, Integer.toString(value), raw -> {
            int parsed = Integer.parseInt(raw);
            return parsed < min || parsed > max ? text("range", Map.of("min", min, "max", max)) : null;
        }, raw -> setter.accept(Integer.parseInt(raw)));
    }
    private static void update(Holder h, String key, Object value) {
        RiptideEditorModel.update(config(h.session), h.page.type(), h.page.id(), key, value);
    }
    private static RiptideLevelTemplate entry(Holder h) { return RiptideEditorModel.find(config(h.session), h.page.type(), h.page.id()); }
    private static void changed(Holder h) { h.session.markDirty(); }
    private static void reopen(PrepareSessionManager manager, Player player, Holder h) { open(manager, player, h.session, h.page); }
    private static void back(PrepareSessionManager manager, Player player, Holder h) {
        if (h.page.screen() == WORKSHOP) { continueBuilding(player, h); return; }
        if (h.page.parent() == null) StepMenuGui.open(player, h.session);
        else open(manager, player, h.session, h.page.parent());
    }
    private static void openNew(PrepareSessionManager manager, Player player, Holder h, RiptideLevelTemplate t) {
        int last = (RiptideEditorModel.rows(config(h.session), t.type()).size() - 1) / RiptideEditorModel.CATEGORY_PAGE_SIZE;
        var category = h.page.categoryOrigin().atPage(last);
        open(manager, player, h.session, category.child(ENTRY, t.id(), null));
    }
    private static void later(PrepareSessionManager manager, Player player, Holder h, boolean onlyIfClosed) {
        if (!h.session.getPlugin().isEnabled()) return;
        Bukkit.getScheduler().runTask(h.session.getPlugin(), () -> {
            if (!player.isOnline() || manager.getSession(player) != h.session) return;
            var type = player.getOpenInventory().getTopInventory().getType();
            if (!onlyIfClosed || type == InventoryType.CRAFTING || type == InventoryType.CREATIVE) reopen(manager, player, h);
        });
    }

    private static List<String> concreteVariants(RiptideLevelType type) {
        return RiptideLevelTemplate.variants(type).stream().filter(v -> !v.equals("AUTO")).toList();
    }
    private static void createBuilding(PrepareSessionManager manager, Player player, Holder h) {
        var t = RiptideEditorModel.addBlank(config(h.session), h.page.type());
        changed(h);
        int last = (RiptideEditorModel.rows(config(h.session), t.type()).size() - 1) / RiptideEditorModel.CATEGORY_PAGE_SIZE;
        var origin = h.page.categoryOrigin().atPage(last).child(ENTRY, t.id(), null);
        startBuilding(manager, player, new Holder(h.session, origin), true, true);
    }
    private static void startBuilding(PrepareSessionManager manager, Player player, Holder h, boolean blank, boolean enableOnSave) {
        var c = config(h.session); var t = entry(h);
        int step = c.resolveGeometry().totalSteps() / 2;
        String variant = t.variant().equals("AUTO") ? concreteVariants(t.type()).getFirst() : t.variant();
        // Edit the shared single-gate building; DOUBLE repeats it at runtime.
        if (variant.equals("DOUBLE")) variant = "ADD";
        var level = new RiptideCoursePlan.Level(1, step, t, variant, 1, false, c.getPreviewSeed());
        var plan = new RiptideCoursePlan(c.getPreviewSeed(), RiptideCoursePlanner.VERSION, 0, List.of(level));
        player.closeInventory(); Utils.sendAdminInfo(player, text("opening-building"));
        RiptideCourseGenerator.generateAsync(h.session.getPlugin(), c, plan,
                () -> manager.getSession(player) == h.session && player.isOnline() && player.hasPermission("cc.admin")
                        && h.session.getTarget().canSaveMap()).whenComplete((built, error) -> {
            if (manager.getSession(player) != h.session || !player.isOnline()) return;
            if (error != null || !Boolean.TRUE.equals(built)) {
                Utils.sendAdminError(player, text("build-failed")); later(manager, player, h, true); return;
            }
            try {
                RiptideWorkshop.decorate(h.session, level, blank);
                RiptideWorkshop.begin(player, h.session, h.page, step, enableOnSave
                        || (t.variant().equals("CUSTOM") && t.blueprint() == null));
                continueBuilding(player, h);
                Utils.sendAdminInfo(player, text("building-instructions", RiptideWorkshop.dimensions(c)));
            } catch (RuntimeException failure) { Utils.sendAdminError(player, failure.getMessage()); }
        });
    }
    private static void continueBuilding(Player player, Holder h) {
        var draft = RiptideWorkshop.get(h.session);
        if (draft == null) return;
        player.closeInventory(); player.setGameMode(org.bukkit.GameMode.CREATIVE);
        player.setAllowFlight(true); player.setFlying(true);
        player.teleport(config(h.session).resolveGeometry().centerAt(draft.step() - RiptideWorkshop.BUILDING_EXTENT - 3).add(0, 4, 0));
    }
    private static void saveBuilding(PrepareSessionManager manager, Player player, Holder h) {
        var draft = RiptideWorkshop.get(h.session);
        if (draft == null || !draft.owner().equals(player.getUniqueId())) return;
        var c = config(h.session);
        var snapshot = RiptideBlueprint.capture(c, draft.step(), h.page.type());
        var original = entry(h); var replacement = original.withBlueprint(snapshot);
        if (draft.enableOnSave()) replacement = new RiptideLevelTemplate(replacement.id(), replacement.name(), replacement.type(),
                replacement.variant(), true, replacement.weight(), replacement.maxUses(), replacement.difficulty(), snapshot);
        try { c.saveBuilding(replacement); }
        catch (java.io.IOException error) {
            h.session.getPlugin().getLogger().log(java.util.logging.Level.SEVERE, "激流变体建筑保存失败：" + original.id(), error);
            throw new IllegalStateException(text("building-save-failed"), error);
        }
        RiptideWorkshop.finish(h.session);
        Utils.sendAdminSuccess(player, text("building-saved"));
        open(manager, player, h.session, draft.origin());
    }

    private static void generate(PrepareSessionManager manager, Player player, Holder h, RiptideCoursePlan plan,
                                  boolean play, RiptideCoursePlan.Level level) {
        changed(h); player.closeInventory(); Utils.sendAdminInfo(player, text("building", Map.of("value", plan.seed())));
        var builtPlan = play && level != null ? new RiptideCoursePlan(plan.seed(), plan.algorithmVersion(), 0, plan.trialLevels(level)) : plan;
        RiptideCourseGenerator.generateAsync(h.session.getPlugin(), config(h.session), builtPlan,
                () -> manager.getSession(player) == h.session && player.hasPermission("cc.admin") && h.session.getTarget().canSaveMap())
                .whenComplete((built, error) -> {
                    if (manager.getSession(player) != h.session || !player.isOnline()) return;
                    if (error != null || !Boolean.TRUE.equals(built)) {
                        Utils.sendAdminError(player, text("build-failed")); later(manager, player, h, true); return;
                    }
                    if (play) RiptideCourseTrial.start(manager, player, h.session, plan, level, () -> later(manager, player, h, true));
                    else {
                        var g = config(h.session).resolveGeometry();
                        player.teleport(g.centerAt(level == null ? 0 : Math.max(0, level.step() - 8)).add(0, 6, 0));
                        player.setAllowFlight(true); player.setFlying(true);
                        Utils.sendAdminSuccess(player, text("built"));
                    }
                });
    }
    private static void pages(Holder h, int count, int size) {
        int pages = Math.max(1, (count + size - 1) / size);
        if (h.page.page() > 0) button(h, PREVIOUS, "previous", null, Map.of("page", h.page.page(), "pages", pages));
        if (h.page.page() + 1 < pages) button(h, NEXT, "next", null, Map.of("page", h.page.page() + 2, "pages", pages));
    }
    private static void value(Holder h, int slot, String key, Object value) { button(h, slot, key, null, Map.of("value", value)); }
    private static void button(Holder h, int slot, String key) { button(h, slot, key, null, Map.of()); }
    private static void button(Holder h, int slot, String key, String state, Map<String, ?> values) {
        h.inventory.setItem(slot, ConfiguredGui.item(PATH + ".items." + key, state, values,
                Material.PAPER, Component.empty(), List.of(), false));
    }
    private static ItemStack template(RiptideLevelTemplate t, boolean clickable) {
        return ConfiguredGui.item(PATH + ".items.entry", t.enabled() ? "enabled" : "disabled",
                Map.of("name", t.name(), "type", t.type().displayName(), "variant", RiptideLevelTemplate.variantName(t.variant()),
                        "building", text(t.blueprint() != null ? "authored-building" : t.variant().equals("CUSTOM") ? "unsaved-building" : "builtin-building"),
                        "difficulty", t.difficulty(), "weight", t.weight(), "max", t.maxUses(),
                        "action", clickable ? text("edit-hint") : ""), t.type().icon(), Component.empty(), List.of(), false);
    }
    private static ItemStack placement(RiptideCoursePlan.Level level) {
        return ConfiguredGui.item(PATH + ".items.placement", null, Map.of("number", level.number(), "name", level.displayName(),
                "variant", !level.isSideSweep() ? RiptideLevelTemplate.variantName(level.variant()) : level.sideWalls().stream()
                        .map(w -> w.template().name()).collect(java.util.stream.Collectors.joining(" / ")), "step", level.step()),
                level.type().icon(), Component.empty(), List.of(), false);
    }
}

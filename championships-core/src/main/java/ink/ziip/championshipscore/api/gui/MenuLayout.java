package ink.ziip.championshipscore.api.gui;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.shared.presentation.InventoryMenuLayout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Immutable slots shared by rendering and click routing for one open inventory. */
public record MenuLayout(
        int size,
        List<Integer> content,
        List<Integer> border,
        Map<String, Integer> controls,
        Map<Integer, List<Integer>> arrangements) {
    public MenuLayout(
            int size, List<Integer> content, List<Integer> border, Map<String, Integer> controls) {
        this(size, content, border, controls, Map.of());
    }

    public MenuLayout {
        var resolved = new InventoryMenuLayout(size, content, border, controls, arrangements);
        content = resolved.content();
        border = resolved.border();
        controls = resolved.controls();
        arrangements = resolved.arrangements();
    }

    /** Shared immutable layout used by remote and local inventory renderers. */
    public InventoryMenuLayout snapshot() {
        return new InventoryMenuLayout(size, content, border, controls, arrangements);
    }

    public static MenuLayout load(
            String path,
            int size,
            List<Integer> fallbackContent,
            List<Integer> fallbackBorder,
            Map<String, Integer> fallbackControls) {
        return load(path, path, size, fallbackContent, fallbackBorder, fallbackControls);
    }

    /** Separate window layouts may reuse their family's existing item definitions. */
    public static MenuLayout load(
            String path,
            String itemPath,
            int size,
            List<Integer> fallbackContent,
            List<Integer> fallbackBorder,
            Map<String, Integer> fallbackControls) {
        var controls = new LinkedHashMap<String, Integer>();
        fallbackControls.forEach(
                (id, slot) -> controls.put(id, GuiConfig.controlSlot(path, itemPath, id, slot)));
        return new MenuLayout(
                size,
                GuiConfig.slots(path + ".layout.content", fallbackContent),
                GuiConfig.slots(path + ".layout.border", fallbackBorder),
                controls,
                GuiConfig.slotArrangements(path + ".layout.content"));
    }

    public static List<Integer> range(int first, int endExclusive) {
        return IntStream.range(first, endExclusive).boxed().toList();
    }

    public int control(String id) {
        return snapshot().control(id);
    }

    public int pageCount(int entries) {
        return snapshot().pageCount(entries);
    }

    public int clampPage(int requested, int entries) {
        return snapshot().clampPage(requested, entries);
    }

    public List<Integer> pageSlots(int page, int entries) {
        return snapshot().pageSlots(page, entries);
    }

    public int index(int page, int slot, int entries) {
        return snapshot().index(page, slot, entries);
    }

    public int index(int page, int slot) {
        return snapshot().index(page, slot);
    }
}

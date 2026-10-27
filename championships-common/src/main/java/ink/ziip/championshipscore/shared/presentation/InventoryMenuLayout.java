package ink.ziip.championshipscore.shared.presentation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Immutable slots shared by rendering and click routing for one open inventory. */
public record InventoryMenuLayout(
        int size,
        List<Integer> content,
        List<Integer> border,
        Map<String, Integer> controls,
        Map<Integer, List<Integer>> arrangements) {
    public InventoryMenuLayout(
            int size, List<Integer> content, List<Integer> border, Map<String, Integer> controls) {
        this(size, content, border, controls, Map.of());
    }

    public InventoryMenuLayout {
        content = List.copyOf(content);
        border = List.copyOf(border);
        controls = Map.copyOf(controls);
        var copied = new LinkedHashMap<Integer, List<Integer>>();
        arrangements.forEach(
                (count, slots) -> {
                    if (count < 1
                            || slots.size() != count
                            || slots.stream().distinct().count() != count
                            || slots.stream().anyMatch(slot -> slot < 0 || slot >= size))
                        throw new IllegalArgumentException("Invalid content arrangement: " + count);
                    copied.put(count, List.copyOf(slots));
                });
        arrangements = Map.copyOf(copied);
        if (size < 9 || size > 54 || size % 9 != 0)
            throw new IllegalArgumentException("Invalid inventory size: " + size);
        for (int slot :
                java.util.stream.Stream.of(content, border, controls.values())
                        .flatMap(java.util.Collection::stream)
                        .toList())
            if (slot < 0 || slot >= size)
                throw new IllegalArgumentException("Slot outside inventory: " + slot);
        if (content.stream().distinct().count() != content.size())
            throw new IllegalArgumentException("Duplicate content slot");
        // Fixed controls may replace a decorative slot, but never consume a page entry.
        var reservedSlots = controls.values();
        content = content.stream().filter(slot -> !reservedSlots.contains(slot)).toList();
        var entrySlots = new java.util.HashSet<>(content);
        arrangements.values().forEach(entrySlots::addAll);
        border = border.stream().filter(slot -> !entrySlots.contains(slot)).toList();
    }

    public static List<Integer> range(int first, int endExclusive) {
        return IntStream.range(first, endExclusive).boxed().toList();
    }

    public int control(String id) {
        Integer slot = controls.get(id);
        if (slot == null) throw new IllegalArgumentException("Unknown menu control: " + id);
        return slot;
    }

    public int pageCount(int entries) {
        if (content.isEmpty()) throw new IllegalStateException("Paged menu has no content slots");
        return Math.max(1, (Math.max(0, entries) - 1) / content.size() + 1);
    }

    public int clampPage(int requested, int entries) {
        return Math.max(0, Math.min(requested, pageCount(entries) - 1));
    }

    /** Partial pages may have a centered placement; an ordinary list keeps its exact order. */
    public List<Integer> pageSlots(int page, int entries) {
        int count = Math.min(content.size(), Math.max(0, entries - page * content.size()));
        var placement = arrangements.get(count);
        if (placement != null && placement.stream().noneMatch(controls.values()::contains))
            return placement;
        return content.subList(0, count);
    }

    public int index(int page, int slot, int entries) {
        int offset = pageSlots(page, entries).indexOf(slot);
        return offset < 0 ? -1 : page * content.size() + offset;
    }

    /** Returns -1 for any slot that does not belong to the visible content region. */
    public int index(int page, int slot) {
        if (!arrangements.isEmpty())
            throw new IllegalStateException("Count-specific placement requires the entry count");
        int offset = content.indexOf(slot);
        return offset < 0 ? -1 : page * content.size() + offset;
    }
}

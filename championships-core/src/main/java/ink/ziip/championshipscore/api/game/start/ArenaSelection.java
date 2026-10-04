package ink.ziip.championshipscore.api.game.start;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

/** Human-facing arena numbers are one-based; runtime geometry always uses zero-based indices. */
public record ArenaSelection(List<Integer> indices) {
    public ArenaSelection {
        indices = List.copyOf(indices);
        if (indices.stream().anyMatch(index -> index < 0)
                || new HashSet<>(indices).size() != indices.size()) {
            throw new IllegalArgumentException("子场地编号必须为正整数且不能重复");
        }
    }

    public static ArenaSelection all() {
        return new ArenaSelection(List.of());
    }

    public static ArenaSelection parse(String value) {
        if (value.equalsIgnoreCase("all") || value.equalsIgnoreCase("auto")) return all();
        List<Integer> result = new ArrayList<>();
        try {
            for (String token : value.split(",", -1)) {
                if (!token.matches("[1-9][0-9]*")) throw new NumberFormatException();
                result.add(Integer.parseInt(token) - 1);
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("子场地使用 all 或从 1 开始的编号，例如 1、1,3", exception);
        }
        return new ArenaSelection(result);
    }

    public boolean automatic() {
        return indices.isEmpty();
    }

    public List<Integer> resolve(int count) {
        if (count < 1) throw new IllegalArgumentException("地图没有可用子场地");
        if (indices.stream().anyMatch(index -> index >= count)) {
            throw new IllegalArgumentException("子场地编号超出范围；可用编号为 1–" + count);
        }
        return automatic() ? IntStream.range(0, count).boxed().toList() : indices;
    }
}

package ink.ziip.championshipscore.api.game.bingo.execution;

import ink.ziip.championshipscore.api.game.bingo.task.pool.PoolEntrySpec;
import ink.ziip.championshipscore.api.game.bingo.task.pool.TaskPoolSpec;
import ink.ziip.championshipscore.protocol.BingoContentCatalog;
import ink.ziip.championshipscore.protocol.BingoTaskSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/** Filters expanded pool definitions before sampling a card for the Folia 26.2 worker. */
public final class RemoteBingoContent {
    private RemoteBingoContent() {}

    public static TaskPoolSpec filter(TaskPoolSpec pool) {
        return new TaskPoolSpec(
                pool.entries().stream()
                        .map(RemoteBingoContent::compatibleMembers)
                        .filter(java.util.Objects::nonNull)
                        .filter(RemoteBingoContent::supports)
                        .toList());
    }

    private static PoolEntrySpec compatibleMembers(PoolEntrySpec entry) {
        boolean oneOf = entry.kind() == PoolEntrySpec.Kind.ONE_OF;
        boolean uniqueCollect =
                entry.kind() == PoolEntrySpec.Kind.EVENT
                        && entry.key().startsWith("unique_collect:");
        if (!oneOf && !uniqueCollect) return entry;
        var catalog = BingoContentCatalog.worker26_2();
        var members =
                entry.members().stream()
                        .filter(member -> catalog.contains("materials", member))
                        .toList();
        if (members.isEmpty() || (uniqueCollect && members.size() < entry.count())) return null;
        String key = entry.key();
        if (oneOf && !key.isBlank() && !catalog.contains("materials", key))
            key = members.getFirst();
        return new PoolEntrySpec(
                entry.kind(),
                key,
                entry.count(),
                entry.difficulty(),
                entry.dimension(),
                entry.category(),
                members,
                entry.name());
    }

    static boolean supports(PoolEntrySpec entry) {
        Map<String, String> attributes = new LinkedHashMap<>();
        String type =
                switch (entry.kind()) {
                    case ITEM -> {
                        attributes.put("material", entry.key());
                        yield "item";
                    }
                    case MINE, CRAFT -> {
                        attributes.put(
                                "statistic",
                                entry.kind() == PoolEntrySpec.Kind.MINE
                                        ? "MINE_BLOCK"
                                        : "CRAFT_ITEM");
                        attributes.put("material", entry.key());
                        yield "statistic";
                    }
                    case KILL -> {
                        attributes.put("statistic", "KILL_ENTITY");
                        attributes.put("entity", entry.key());
                        yield "statistic";
                    }
                    case STAT -> {
                        attributes.put("statistic", entry.key());
                        yield "statistic";
                    }
                    case ADVANCEMENT -> {
                        attributes.put("key", entry.key());
                        yield "advancement";
                    }
                    case POTION -> {
                        String[] parts = entry.key().split(":", 2);
                        if (parts.length == 2) attributes.put("effect", parts[1]);
                        yield "potion";
                    }
                    case ONE_OF, ALL_OF -> {
                        attributes.put("materials", String.join(",", entry.members()));
                        if (!entry.key().isBlank()) attributes.put("display.material", entry.key());
                        yield entry.kind() == PoolEntrySpec.Kind.ONE_OF ? "item_set" : "all_of";
                    }
                    case EVENT -> {
                        String[] parts = entry.key().split(":", 2);
                        String trigger = parts[0];
                        attributes.put("trigger", trigger);
                        attributes.put("param", parts.length == 2 ? parts[1] : "");
                        if (!entry.members().isEmpty()) {
                            String subjectKind =
                                    switch (trigger) {
                                        case "visit_biomes" -> "BIOME";
                                        case "kill_family", "kill_unique", "enrage" ->
                                                "ENTITY_TYPE";
                                        default -> "MATERIAL";
                                    };
                            attributes.put(
                                    "subjects",
                                    entry.members().stream()
                                            .map(member -> subjectKind + "=" + member)
                                            .collect(java.util.stream.Collectors.joining(",")));
                        }
                        yield "event";
                    }
                };
        return BingoContentCatalog.worker26_2()
                .supports(new BingoTaskSpec(0, entry.objectiveId(), type, attributes));
    }
}

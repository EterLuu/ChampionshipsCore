package ink.ziip.championshipscore.api.game.bingo.task.pool;

import ink.ziip.championshipscore.api.game.bingo.task.PotionTask;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Reads card pools from {@code <dataFolder>/bingo/cards/*.yml}. The selected pool name is supplied by
 * the caller (the bingo config); the default card is written from the bundled jar resource on first run.
 */
public final class TaskPoolLoader {
    private static final String CARDS_DIR = "bingo/cards";
    private static final String DEFAULT_CARD = "default";
    private static final String DEFAULT_RESOURCE = "bingo/cards/default.yml";

    /** Section + identifier-key mapping for each kind. */
    private record SingletonGroup(PoolEntrySpec.Kind kind, String section, String keyName) {
    }

    private static final List<SingletonGroup> SINGLETON_GROUPS = List.of(
            new SingletonGroup(PoolEntrySpec.Kind.ITEM, "items", "material"),
            new SingletonGroup(PoolEntrySpec.Kind.ADVANCEMENT, "advancements", "path"),
            new SingletonGroup(PoolEntrySpec.Kind.MINE, "mine", "block"),
            new SingletonGroup(PoolEntrySpec.Kind.CRAFT, "craft", "item"),
            new SingletonGroup(PoolEntrySpec.Kind.KILL, "kill", "entity"),
            new SingletonGroup(PoolEntrySpec.Kind.STAT, "statistics", "stat"));

    private TaskPoolLoader() {
    }

    /** Loads the named card pool, falling back to {@code default}, then to an empty pool. */
    public static TaskPoolSpec load(JavaPlugin plugin, String selectedName) {
        ensureDefaultCard(plugin);
        String selected = normalizeName(selectedName);
        LoadResult result = loadCard(plugin, selected);
        if (result.spec().isPresent()) {
            TaskPoolSource.setName(result.name());
            return result.spec().get();
        }

        plugin.getLogger().warning(gameLog("卡池=" + selected + " 无法加载，回退到 default"));
        LoadResult fallback = loadCard(plugin, DEFAULT_CARD);
        TaskPoolSource.setName(DEFAULT_CARD);
        return fallback.spec().orElseGet(() -> {
            plugin.getLogger().warning(gameLog("default 卡池无法加载，本轮使用空任务池"));
            return new TaskPoolSpec(List.of());
        });
    }

    public static File cardFile(JavaPlugin plugin, String name) {
        return new File(cardsDir(plugin), normalizeName(name) + ".yml");
    }

    public static String normalizeName(String name) {
        if (name == null || name.isBlank()) return DEFAULT_CARD;
        String trimmed = name.trim();
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(".yml")) {
            trimmed = trimmed.substring(0, trimmed.length() - ".yml".length());
        }
        return trimmed.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static LoadResult loadCard(JavaPlugin plugin, String name) {
        Logger log = plugin.getLogger();
        File file = cardFile(plugin, name);
        if (!file.exists()) {
            return LoadResult.error(name, "not_found");
        }

        try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            TaskPoolSpec parsed = parse(y, log, file.getName());
            if (parsed.isEmpty()) {
                return LoadResult.error(name, "empty");
            }
            log.info(gameLog("已加载 bingo/cards/" + name + ".yml，任务数=" + parsed.entries().size()));
            return LoadResult.ok(name, parsed);
        } catch (Exception e) {
            log.warning(gameLog("解析 bingo/cards/" + name + ".yml 失败 | " + e.getMessage()));
            return LoadResult.error(name, e.getMessage());
        }
    }

    private static void ensureDefaultCard(JavaPlugin plugin) {
        File dir = cardsDir(plugin);
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning(gameLog("无法创建卡池目录=" + dir.getPath()));
            return;
        }
        File defaultFile = new File(dir, DEFAULT_CARD + ".yml");
        if (defaultFile.exists()) return;
        try (InputStream in = plugin.getResource(DEFAULT_RESOURCE)) {
            if (in == null) {
                plugin.getLogger().warning(gameLog("JAR 缺少默认卡池资源=" + DEFAULT_RESOURCE));
                return;
            }
            Files.copy(in, defaultFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info(gameLog("已生成默认卡池 bingo/cards/default.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning(gameLog("无法写出默认卡池 bingo/cards/default.yml | " + e.getMessage()));
        }
    }

    private static File cardsDir(JavaPlugin plugin) {
        return new File(plugin.getDataFolder(), CARDS_DIR);
    }

    private static TaskPoolSpec parse(YamlConfiguration y, Logger log, String sourceName) {
        List<PoolEntrySpec> out = new ArrayList<>();

        for (Map<?, ?> raw : y.getMapList("categories")) {
            Object idObj = raw.get("id");
            if (idObj == null) {
                log.warning(gameLog(sourceName + " 存在无 id 分类，已跳过"));
                continue;
            }
            String id = String.valueOf(idObj).trim();
            if (id.isEmpty()) continue;
            Difficulty difficulty = parseDifficulty(raw.get("difficulty"), log, sourceName, "category " + id);
            Dimension dimension = parseDimension(raw.get("dimension"), log, sourceName, "category " + id);

            Object membersObj = raw.get("members");
            if (!(membersObj instanceof List<?> rawMembers)) {
                log.warning(gameLog(sourceName + " 分类=" + id + " 缺少 members，已跳过"));
                continue;
            }
            for (Object m : rawMembers) {
                if (!(m instanceof Map<?, ?> member)) continue;
                out.addAll(parseMember(member, difficulty, dimension, id, log, sourceName));
            }
        }

        ConfigurationSection singletons = y.getConfigurationSection("singletons");
        if (singletons != null) {
            for (SingletonGroup g : SINGLETON_GROUPS) {
                for (Map<?, ?> raw : singletons.getMapList(g.section())) {
                    Object keyValue = raw.get(g.keyName());
                    if (keyValue == null) continue;
                    String key = parseKey(keyValue, g.kind());
                    int count = raw.get("count") instanceof Number n ? n.intValue() : 1;
                    Difficulty difficulty = parseDifficulty(raw.get("difficulty"), log, sourceName, key);
                    Dimension dimension;
                    if (raw.containsKey("dimension")) {
                        dimension = parseDimension(raw.get("dimension"), log, sourceName, key);
                    } else if (g.kind() == PoolEntrySpec.Kind.ADVANCEMENT) {
                        dimension = key.startsWith("nether/") ? Dimension.NETHER
                                : key.startsWith("end/") ? Dimension.THE_END
                                : Dimension.OVERWORLD;
                    } else {
                        dimension = Dimension.OVERWORLD;
                    }
                    out.addAll(expandEntry(g.kind(), key, count, difficulty, dimension, null, log, sourceName));
                }
            }
            // Standalone one_of / all_of sets (no category by default; an explicit category on the
            // entry is honoured so complete-set tasks can share the unique_collect group).
            for (Map<?, ?> raw : singletons.getMapList("sets")) {
                Difficulty difficulty = parseDifficulty(raw.get("difficulty"), log, sourceName, "set");
                Dimension dimension = parseDimension(raw.get("dimension"), log, sourceName, "set");
                Object catObj = raw.get("category");
                String category = catObj == null || String.valueOf(catObj).isBlank()
                        ? null : String.valueOf(catObj).trim();
                if (raw.get("all_of") instanceof List<?>) {
                    out.addAll(parseAllOf(raw, difficulty, dimension, category, log, sourceName));
                } else {
                    out.addAll(parseOneOf(raw, difficulty, dimension, category, log, sourceName));
                }
            }
            // Unified event objectives (eat/die/wear/tame/breed/spy/effect/reach/...).
            for (Map<?, ?> raw : singletons.getMapList("events")) {
                out.addAll(parseEvents(raw, log, sourceName));
            }
            // Effect-specific potions; effect "*" expands to every brewable effect.
            for (Map<?, ?> raw : singletons.getMapList("potions")) {
                out.addAll(parsePotions(raw, log, sourceName));
            }
        }

        return new TaskPoolSpec(out);
    }

    private static List<PoolEntrySpec> parseMember(Map<?, ?> member, Difficulty difficulty, Dimension dimension,
                                             String categoryId, Logger log, String sourceName) {
        if (member.get("one_of") instanceof List<?>) {
            return parseOneOf(member, difficulty, dimension, categoryId, log, sourceName);
        }
        if (member.get("all_of") instanceof List<?>) {
            return parseAllOf(member, difficulty, dimension, categoryId, log, sourceName);
        }
        for (SingletonGroup g : SINGLETON_GROUPS) {
            Object v = member.get(g.keyName());
            if (v == null) continue;
            String key = parseKey(v, g.kind());
            int count = member.get("count") instanceof Number n ? n.intValue() : 1;
            return expandEntry(g.kind(), key, count, difficulty, dimension, categoryId, log, sourceName);
        }
        log.warning(gameLog(sourceName + " 分类=" + categoryId + " 成员缺少有效 kind，已跳过"));
        return List.of();
    }

    private static List<PoolEntrySpec> parseOneOf(Map<?, ?> member, Difficulty difficulty, Dimension dimension,
                                                  String categoryId, Logger log, String sourceName) {
        Object raw = member.get("one_of");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            log.warning(gameLog(sourceName + " one_of 任务缺少成员列表，已跳过"));
            return List.of();
        }
        LinkedHashSet<String> members = new LinkedHashSet<>();
        for (Object o : list) {
            if (o == null) continue;
            String token = String.valueOf(o).trim().toUpperCase(Locale.ROOT);
            if (token.isEmpty()) continue;
            if (isGlob(token)) {
                members.addAll(globKeys(PoolEntrySpec.Kind.ITEM, token));
            } else {
                members.add(token);
            }
        }
        if (members.isEmpty()) {
            log.warning(gameLog(sourceName + " one_of 任务未匹配物品，已跳过"));
            return List.of();
        }
        int count = member.get("count") instanceof Number n ? n.intValue() : 1;
        String name = member.get("name") instanceof Object nm ? String.valueOf(nm) : null;
        String icon = member.get("icon") instanceof Object ic ? String.valueOf(ic).trim().toUpperCase(Locale.ROOT) : null;
        return List.of(PoolEntrySpec.oneOf(new ArrayList<>(members), icon, name, count,
                difficulty, dimension, categoryId));
    }

    /**
     * Parse an {@code all_of} entry: a list of item names (globs allowed) collected into a single
     * "hold the complete set" objective. Optional {@code icon} is the representative item shown on the
     * card/map (defaults to the first member) and also names the objective id ({@code all:<icon>});
     * optional {@code label} is the {@code task.family.*} localization token for the title.
     */
    private static List<PoolEntrySpec> parseAllOf(Map<?, ?> member, Difficulty difficulty, Dimension dimension,
                                                  String categoryId, Logger log, String sourceName) {
        Object raw = member.get("all_of");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            log.warning(gameLog(sourceName + " all_of 任务缺少成员列表，已跳过"));
            return List.of();
        }
        LinkedHashSet<String> members = new LinkedHashSet<>();
        for (Object o : list) {
            if (o == null) continue;
            String token = String.valueOf(o).trim().toUpperCase(Locale.ROOT);
            if (token.isEmpty()) continue;
            if (isGlob(token)) {
                members.addAll(globKeys(PoolEntrySpec.Kind.ITEM, token));
            } else {
                members.add(token);
            }
        }
        if (members.isEmpty()) {
            log.warning(gameLog(sourceName + " all_of 任务未匹配物品，已跳过"));
            return List.of();
        }
        int count = member.get("count") instanceof Number n ? n.intValue() : 1;
        String icon = member.get("icon") instanceof Object ic ? String.valueOf(ic).trim().toUpperCase(Locale.ROOT) : null;
        String label = member.get("label") instanceof Object lb ? String.valueOf(lb) : null;
        return List.of(PoolEntrySpec.allOf(new ArrayList<>(members), icon, label, count,
                difficulty, dimension, categoryId));
    }

    /**
     * Parse an {@code events} entry: a unified event objective keyed by {@code trigger} + {@code param}.
     * Set-based triggers ({@code unique_collect}/{@code all_collect}) take a glob or list in {@code param}
     * and an optional {@code icon}; their key becomes {@code <trigger>:<icon>} so the objective id stays
     * wildcard-free. Other triggers store {@code <trigger>:<param>} directly.
     */
    private static List<PoolEntrySpec> parseEvents(Map<?, ?> raw, Logger log, String sourceName) {
        Object triggerObj = raw.get("trigger");
        if (triggerObj == null) {
            log.warning(gameLog(sourceName + " event 任务缺少 trigger，已跳过"));
            return List.of();
        }
        String trigger = String.valueOf(triggerObj).trim().toLowerCase(Locale.ROOT);
        int count = raw.get("count") instanceof Number n ? n.intValue() : 1;
        Difficulty difficulty = parseDifficulty(raw.get("difficulty"), log, sourceName, "event " + trigger);
        Dimension dimension = raw.containsKey("dimension")
                ? parseDimension(raw.get("dimension"), log, sourceName, "event " + trigger)
                : Dimension.OVERWORLD;
        Object catObj = raw.get("category");
        String category = catObj == null || String.valueOf(catObj).isBlank() ? null : String.valueOf(catObj).trim();

        Set<String> LIST_PARAM_TRIGGERS = Set.of(
                "unique_collect", "all_collect", "eat_all", "visit_biomes", "kill_family", "kill_unique");
        if (LIST_PARAM_TRIGGERS.contains(trigger)) {
            List<String> members = new ArrayList<>();
            Object paramObj = raw.get("param");
            if (paramObj instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null) members.add(String.valueOf(o).trim().toUpperCase(Locale.ROOT));
                }
            } else if (paramObj != null) {
                String token = String.valueOf(paramObj).trim().toUpperCase(Locale.ROOT);
                if (isGlob(token)) members.addAll(globKeys(PoolEntrySpec.Kind.ITEM, token));
                else members.add(token);
            }
            if (members.isEmpty()) {
                log.warning(gameLog(sourceName + " event=" + trigger + " 未匹配成员，已跳过"));
                return List.of();
            }
            String icon = raw.get("icon") instanceof Object ic
                    ? String.valueOf(ic).trim().toUpperCase(Locale.ROOT) : members.get(0);
            return List.of(new PoolEntrySpec(PoolEntrySpec.Kind.EVENT, trigger + ":" + icon,
                    count, difficulty, dimension, category, members, null));
        }
        String param = raw.get("param") == null ? "" : String.valueOf(raw.get("param")).trim();
        return List.of(new PoolEntrySpec(PoolEntrySpec.Kind.EVENT, trigger + ":" + param,
                count, difficulty, dimension, category, List.of(), null));
    }

    /**
     * Parse a {@code potions} entry: an effect potion task. {@code form} is normal/splash/lingering,
     * {@code effect} a vanilla potion key (e.g. {@code strength}) or {@code "*"} to expand to every
     * {@linkplain PotionTask#BREWABLE brewable} effect.
     */
    private static List<PoolEntrySpec> parsePotions(Map<?, ?> raw, Logger log, String sourceName) {
        PotionTask.Form form = PotionTask.Form.parse(
                raw.get("form") == null ? "normal" : String.valueOf(raw.get("form")));
        if (form == null) {
            log.warning(gameLog(sourceName + " 药水任务 form=" + raw.get("form") + " 无效，已跳过"));
            return List.of();
        }
        String formKey = form.name().toLowerCase(Locale.ROOT);
        Object effectObj = raw.get("effect");
        if (effectObj == null) {
            log.warning(gameLog(sourceName + " 药水任务缺少 effect，已跳过"));
            return List.of();
        }
        String effect = String.valueOf(effectObj).trim().toLowerCase(Locale.ROOT);
        List<String> effects = "*".equals(effect) ? PotionTask.BREWABLE : List.of(effect);

        int count = raw.get("count") instanceof Number n ? n.intValue() : 1;
        Difficulty difficulty = parseDifficulty(raw.get("difficulty"), log, sourceName, "potion " + formKey);
        Dimension dimension = parseDimension(raw.get("dimension"), log, sourceName, "potion " + formKey);
        Object catObj = raw.get("category");
        String category = catObj == null || String.valueOf(catObj).isBlank() ? null : String.valueOf(catObj).trim();

        List<PoolEntrySpec> out = new ArrayList<>(effects.size());
        for (String e : effects) {
            out.add(new PoolEntrySpec(PoolEntrySpec.Kind.POTION, formKey + ":" + e, count,
                    difficulty, dimension, category));
        }
        return out;
    }

    private static String parseKey(Object keyValue, PoolEntrySpec.Kind kind) {
        String s = String.valueOf(keyValue).trim();
        return kind == PoolEntrySpec.Kind.ADVANCEMENT ? s : s.toUpperCase(Locale.ROOT);
    }

    private static List<PoolEntrySpec> expandEntry(PoolEntrySpec.Kind kind, String key, int count,
                                                   Difficulty difficulty, Dimension dimension, String category,
                                                   Logger log, String sourceName) {
        if (!isGlob(key)) {
            return List.of(new PoolEntrySpec(kind, key, count, difficulty, dimension, category));
        }

        List<String> keys = globKeys(kind, key);
        if (keys.isEmpty()) {
            log.warning(gameLog(sourceName + " 通配任务=" + key + " 未匹配 " + kind + "，已跳过"));
            return List.of();
        }

        List<PoolEntrySpec> out = new ArrayList<>(keys.size());
        for (String expanded : keys) {
            out.add(new PoolEntrySpec(kind, expanded, count, difficulty, dimension, category));
        }
        return out;
    }

    private static boolean isGlob(String key) {
        return key.indexOf('*') >= 0 || key.indexOf('?') >= 0;
    }

    private static List<String> globKeys(PoolEntrySpec.Kind kind, String glob) {
        Pattern pattern = globPattern(glob);
        return switch (kind) {
            case ITEM, CRAFT -> enumNames(Material.values(), pattern, Material::isItem);
            case MINE -> enumNames(Material.values(), pattern);
            case KILL -> enumNames(EntityType.values(), pattern);
            case STAT -> enumNames(Statistic.values(), pattern);
            case ADVANCEMENT, ONE_OF, ALL_OF, POTION, EVENT -> List.of();
        };
    }

    private static <E extends Enum<E>> List<String> enumNames(E[] values, Pattern pattern) {
        return enumNames(values, pattern, v -> true);
    }

    private static <E extends Enum<E>> List<String> enumNames(E[] values, Pattern pattern,
                                                              java.util.function.Predicate<E> keep) {
        List<String> out = new ArrayList<>();
        for (E value : values) {
            String name = value.name();
            if (name.startsWith("LEGACY_")) continue;
            if (pattern.matcher(name).matches() && keep.test(value)) {
                out.add(name);
            }
        }
        return out;
    }

    private static Pattern globPattern(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> {
                    if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                        regex.append('\\');
                    }
                    regex.append(c);
                }
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    private static Difficulty parseDifficulty(Object value, Logger log, String sourceName, String key) {
        if (value == null) {
            return Difficulty.MEDIUM;
        }
        try {
            return Difficulty.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning(gameLog(sourceName + " 任务=" + key + " difficulty=" + value + " 无效，使用 MEDIUM"));
            return Difficulty.MEDIUM;
        }
    }

    private static Dimension parseDimension(Object value, Logger log, String sourceName, String key) {
        if (value == null) return Dimension.OVERWORLD;
        return Dimension.parse(String.valueOf(value)).orElseGet(() -> {
            log.warning(gameLog(sourceName + " 任务=" + key + " dimension=" + value + " 无效，使用 overworld"));
            return Dimension.OVERWORLD;
        });
    }

    private static String gameLog(String message) {
        return Utils.formatGameLog(GameTypeEnum.Bingo, "-", "加载", "卡池", message);
    }

    public record LoadResult(String name, Optional<TaskPoolSpec> spec, String error) {
        public static LoadResult ok(String name, TaskPoolSpec spec) {
            return new LoadResult(name, Optional.of(spec), null);
        }

        public static LoadResult error(String name, String error) {
            return new LoadResult(name, Optional.empty(), error);
        }
    }
}

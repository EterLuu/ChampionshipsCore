package ink.ziip.championshipscore.api.game.bingo.task.pool;

import ink.ziip.championshipscore.api.game.bingo.task.TaskData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The active tag layer: maps {@linkplain TaskData#objectiveId() objective ids} to free-form tags and
 * carries the filter rules built from those tags. Every objective additionally gets an implicit
 * {@code dim:<dimension>} tag so dimensions can be capped or excluded without authoring a tag file.
 */
public final class TagFilters {
    public static final TagFilters EMPTY = new TagFilters(List.of(), Set.of(), Map.of());

    private static volatile TagFilters active = EMPTY;

    private record Rule(String tag, Pattern pattern) {
    }

    private final List<Rule> rules;
    private final Set<String> excluded;
    private final Map<String, Integer> caps;

    TagFilters(List<Rule> rules, Set<String> excluded, Map<String, Integer> caps) {
        this.rules = List.copyOf(rules);
        this.excluded = Set.copyOf(excluded);
        this.caps = Map.copyOf(caps);
    }

    public static TagFilters active() {
        return active;
    }

    public static void set(TagFilters filters) {
        active = filters == null ? EMPTY : filters;
    }

    /**
     * Returns a copy of this filter with extra excludes/caps folded in — used per round to merge the
     * dynamic, world-driven dimension rules on top of the static config. For a tag capped by both, the
     * more restrictive (smaller) limit wins.
     */
    public TagFilters merged(Set<String> extraExcluded, Map<String, Integer> extraCaps) {
        boolean noEx = extraExcluded == null || extraExcluded.isEmpty();
        boolean noCaps = extraCaps == null || extraCaps.isEmpty();
        if (noEx && noCaps) return this;
        Set<String> ex = new HashSet<>(excluded);
        if (!noEx) for (String s : extraExcluded) ex.add(s.toLowerCase(java.util.Locale.ROOT));
        Map<String, Integer> mergedCaps = new java.util.HashMap<>(caps);
        if (!noCaps) {
            for (Map.Entry<String, Integer> e : extraCaps.entrySet()) {
                mergedCaps.merge(e.getKey().toLowerCase(java.util.Locale.ROOT), e.getValue(), Math::min);
            }
        }
        return new TagFilters(rules, ex, mergedCaps);
    }

    /** Builds a rule list from a {@code tag -> id-globs} mapping plus the exclude/caps filter config. */
    static TagFilters build(Map<String, List<String>> tagToIds, Set<String> excluded, Map<String, Integer> caps) {
        List<Rule> rules = new ArrayList<>();
        if (tagToIds != null) {
            for (Map.Entry<String, List<String>> e : tagToIds.entrySet()) {
                String tag = e.getKey().toLowerCase(java.util.Locale.ROOT);
                for (String token : e.getValue()) {
                    if (token == null || token.isBlank()) continue;
                    rules.add(new Rule(tag, compile(token.trim())));
                }
            }
        }
        Set<String> ex = new HashSet<>();
        if (excluded != null) for (String s : excluded) ex.add(s.toLowerCase(java.util.Locale.ROOT));
        return new TagFilters(rules, ex, caps == null ? Map.of() : caps);
    }

    /** All tags on an objective: its matching rule tags plus the implicit {@code dim:<dimension>} tag. */
    public Set<String> tagsOf(TaskData task) {
        Set<String> out = new HashSet<>();
        out.add("dim:" + task.dimension().key());
        if (!rules.isEmpty()) {
            String id = task.objectiveId();
            for (Rule rule : rules) {
                if (rule.pattern().matcher(id).matches()) out.add(rule.tag());
            }
        }
        return out;
    }

    /** True when the objective carries an excluded tag and must be kept off every card. */
    public boolean isExcluded(TaskData task) {
        return !excluded.isEmpty() && !Collections.disjoint(tagsOf(task), excluded);
    }

    public boolean hasCaps() {
        return !caps.isEmpty();
    }

    /** The per-card cap for a tag, or {@code null} when uncapped. */
    public Integer cap(String tag) {
        return caps.get(tag);
    }

    private static Pattern compile(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> {
                    if ("\\.[]{}()+-^$|".indexOf(c) >= 0) regex.append('\\');
                    regex.append(c);
                }
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                | Pattern.UNICODE_CHARACTER_CLASS);
    }
}

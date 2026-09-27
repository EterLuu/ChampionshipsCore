package ink.ziip.championshipscore.api.game.riptiderush;

import java.util.*;

/** Packs the mandatory finale into the last fifth, retaining logical quotas and saved buildings. */
final class RiptideFinalStage {
    private RiptideFinalStage() { }

    static List<RiptideHitwCatalog.Passage> passages(RiptideCoursePlan.Level l, RiptideCourseGeometry g) {
        var known = RiptideHitwCatalog.passages(l.template(), l.mirrored());
        if (!known.isEmpty() || l.template().blueprint() != null) return known;
        if (l.variant().equals("GAP")) return java.util.stream.IntStream.rangeClosed(Math.max(-g.halfWidth(), l.opening()-1), Math.min(g.halfWidth(), l.opening()+1))
                .mapToObj(x -> new RiptideHitwCatalog.Passage(x, 0, false)).toList();
        if (l.variant().equals("JUMP")) {
            var result = new ArrayList<RiptideHitwCatalog.Passage>();
            for (int x = -g.halfWidth(); x <= g.halfWidth(); x++) result.add(new RiptideHitwCatalog.Passage(x, 1, false));
            return result;
        }
        return List.of();
    }

    static double transitionSeconds(RiptideCoursePlan.Level a, RiptideCoursePlan.Level b, RiptideCourseGeometry g) {
        var first = passages(a, g); var second = passages(b, g);
        if (first.isEmpty() || second.isEmpty()) return 2.5;
        return first.stream().mapToDouble(p -> second.stream().mapToDouble(q ->
                Math.abs(p.lateral() - q.lateral()) / 3.5 + .65 + (p.crouch() || q.crouch() ? 1 : 0)
                        + (q.sill() > 0 ? .3 : 0)).min().orElseThrow()).max().orElseThrow();
    }

    static List<RiptideCoursePlan.Level> arrange(RiptideRushConfig c, RiptideCourseGeometry g,
                                                List<RiptideCoursePlan.Level> input, Random random) {
        var out = new ArrayList<RiptideCoursePlan.Level>();
        var uses = new HashMap<String,Integer>();
        for (var l : input) {
            if (!l.isSideSweep()) uses.merge(l.template().usageKey(), 1, Integer::sum);
            else for (var w : l.sideWalls()) uses.merge(w.template().usageKey(), 1, Integer::sum);
        }
        int boundary = (int)Math.ceil(g.totalSteps() * .8);
        for (var original : input) {
            if (original.step() < boundary) { out.add(original); continue; }
            var group = new ArrayList<RiptideCoursePlan.Level>();
            if (original.type() == RiptideLevelType.MATH || original.isSideSweep()) {
                var walls = original.sideWalls();
                if (walls.isEmpty()) {
                    walls = RiptideWallGroups.selectSideWalls(c, g, boundary, uses, random);
                    if (walls.size() != 3) return null;
                }
                group.add(new RiptideCoursePlan.Level(original.number(), boundary, original.template(),
                        original.variant(), original.opening(), original.mirrored(), original.contentSeed(),
                        0, 0, "SIDE_MATH", walls.getFirst().direction(), walls));
            } else if (original.type() == RiptideLevelType.PASS) {
                var chosen = original;
                var designs = new HashSet<String>();
                for (int beat = 1; beat <= 3; beat++) {
                    if (beat > 1) {
                        chosen = chooseNext(c, g, original, chosen, designs, uses, random, boundary);
                        if (chosen == null) return null;
                        uses.merge(chosen.template().usageKey(), 1, Integer::sum);
                    }
                    designs.add(chosen.template().designKey(chosen.variant()));
                    group.add(new RiptideCoursePlan.Level(original.number(), boundary, chosen.template(),
                            chosen.variant(), chosen.opening(), chosen.mirrored(), chosen.contentSeed(),
                            original.number(), beat, "FINAL_TRIPLE", 0));
                }
            } else if (original.type() == RiptideLevelType.RHYTHM) {
                for (int beat = 1; beat <= 3; beat++)
                    group.add(new RiptideCoursePlan.Level(original.number(), boundary, original.template(),
                            original.variant(), original.opening(), original.mirrored(), original.contentSeed() + beat,
                            -original.number(), beat, "RHYTHM_TRIPLE", 0));
            } else group.add(original);
            for (var l : group) {
                if (!out.isEmpty() && !RiptideWallGroups.distinctPassages(out.getLast(), l, g)) return null;
                int step = boundary;
                if (!out.isEmpty()) step = Math.max(step, out.getLast().step() + 1);
                RiptideCoursePlan.Level placed;
                do {
                    placed = new RiptideCoursePlan.Level(l.number(), step, l.template(), l.variant(), l.opening(),
                            l.mirrored(), l.contentSeed(), l.wallGroup(), l.beat(), l.rhythm(), l.sweep(), l.sideWalls());
                    if (step + placed.extent() >= g.totalSteps() - g.halfLength()) return null;
                    // The floor starts when the bow reaches it, so it must stop in the final band too.
                    if (placed.type() == RiptideLevelType.COLOR_FLOOR && step - g.halfLength() < boundary) { step++; continue; }
                    if (out.isEmpty() || RiptideCoursePlanner.safeTransition(c, g, out.getLast(), placed)) break;
                    step++;
                } while (true);
                out.add(placed);
            }
        }
        return List.copyOf(out);
    }

    private static RiptideCoursePlan.Level chooseNext(RiptideRushConfig c, RiptideCourseGeometry g,
            RiptideCoursePlan.Level original, RiptideCoursePlan.Level previous, Set<String> designs,
            Map<String,Integer> uses, Random random, int step) {
        var candidates = new ArrayList<RiptideCoursePlan.Level>();
        for (var t : c.resolvePool()) {
            if (!t.enabled() || t.type() != RiptideLevelType.PASS
                    || !RiptideDifficulty.allowsPass(t.difficulty(), step, g.totalSteps())
                    || uses.getOrDefault(t.usageKey(), 0) >= t.maxUses()) continue;
            var variants = t.variant().equals("AUTO") ? List.of("GAP", "JUMP") : List.of(t.variant());
            for (String variant : variants) {
                if (variant.equals("WEAVE") || t.designKey(variant).equals(previous.template().designKey(previous.variant()))) continue;
                int opening = random.nextInt(g.raftWidth()-2)-g.halfWidth()+1;
                for (boolean mirror : List.of(false, true)) {
                    var candidate = new RiptideCoursePlan.Level(original.number(), step, t, variant,
                            opening, mirror, random.nextLong());
                    if (RiptideWallGroups.distinctPassages(previous, candidate, g)) candidates.add(candidate);
                }
            }
        }
        candidates.removeIf(l -> designs.contains(l.template().designKey(l.variant())));
        if (candidates.isEmpty()) return null;
        // The finale has a fixed footprint. Choose among designs that can be reached promptly
        // from every previous exit, while still budgeting the full movement and action time.
        double speed = RiptideCoursePlanner.speedAt(c, g, step);
        double minimumGap = candidates.stream().mapToDouble(l -> l.extent()
                + Math.ceil(transitionSeconds(previous, l, g) * speed)).min().orElseThrow();
        candidates.removeIf(l -> l.extent() + Math.ceil(transitionSeconds(previous, l, g) * speed) > minimumGap + 1);
        double draw = random.nextDouble() * candidates.stream().mapToDouble(l -> l.template().weight()
                / (1D + uses.getOrDefault(l.template().id(), 0))).sum();
        for (var l : candidates) {
            draw -= l.template().weight() / (1D + uses.getOrDefault(l.template().id(), 0));
            if (draw < 0) return l;
        }
        return candidates.getLast();
    }
}

package ink.ziip.championshipscore.api.game.riptiderush.course;

import ink.ziip.championshipscore.api.game.riptiderush.mechanics.RiptideQuestion;
import ink.ziip.championshipscore.api.game.riptiderush.model.RiptideStageKind;

import java.util.List;
import java.util.Random;

/** Immutable per-round authority for both physical blocks and all mechanic positions/content. */
public record RiptideCoursePlan(
        long seed, int algorithmVersion, int estimatedTicks, List<Level> levels) {
    public RiptideCoursePlan(
            long seed,
            int algorithmVersion,
            int estimatedTicks,
            List<Level> levels,
            int totalSteps) {
        this(seed, algorithmVersion, estimatedTicks, expand(levels, totalSteps));
    }

    public RiptideCoursePlan {
        levels = expand(levels, 1);
    }

    private static List<Level> expand(List<Level> levels, int totalSteps) {
        var expanded = new java.util.ArrayList<Level>();
        for (var level : levels) {
            if (!level.variant().equals("DOUBLE") || level.isSideSweep()) {
                expanded.add(level);
                continue;
            }
            var random = new Random(level.contentSeed());
            for (int beat = 1; beat <= 2; beat++) {
                int step = level.step() + (beat == 1 ? -6 : 6);
                var operations =
                        RiptideQuestion.variants().stream()
                                .filter(op -> RiptideDifficulty.allowsMath(op, step, totalSteps))
                                .toList();
                String operation = operations.get(random.nextInt(operations.size()));
                expanded.add(
                        new Level(
                                level.number(),
                                level.step() + (beat == 1 ? -6 : 6),
                                level.template(),
                                operation,
                                level.opening(),
                                level.mirrored(),
                                random.nextLong(),
                                -level.number(),
                                beat,
                                "MATH_DOUBLE",
                                0));
            }
        }
        return List.copyOf(expanded);
    }

    /** Selecting one beat for a trial includes its whole wall group. */
    public List<Level> trialLevels(Level only) {
        if (only == null) return levels;
        return only.wallGroup() == 0
                ? List.of(only)
                : levels.stream().filter(l -> l.wallGroup() == only.wallGroup()).toList();
    }

    /** Avoid repeating embedded schematic bytes in the per-round audit log. */
    public List<java.util.Map<String, Object>> logLevels() {
        return levels.stream()
                .map(
                        l -> {
                            var row = new java.util.LinkedHashMap<String, Object>();
                            row.put("number", l.number());
                            row.put("step", l.step());
                            row.put("template", l.template().id());
                            row.put("type", l.type());
                            row.put("variant", l.variant());
                            row.put("opening", l.opening());
                            row.put("mirrored", l.mirrored());
                            row.put("contentSeed", l.contentSeed());
                            row.put("extent", l.extent());
                            row.put("wallGroup", l.wallGroup());
                            row.put("beat", l.beat());
                            row.put("rhythm", l.rhythm());
                            row.put("sweep", l.sweep());
                            row.put(
                                    "sideWalls",
                                    l.sideWalls().stream()
                                            .map(
                                                    w ->
                                                            java.util.Map.of(
                                                                    "template",
                                                                    w.template().id(),
                                                                    "variant",
                                                                    w.variant(),
                                                                    "opening",
                                                                    w.opening(),
                                                                    "direction",
                                                                    w.direction(),
                                                                    "mirrored",
                                                                    w.mirrored()))
                                            .toList());
                            row.put(
                                    "building",
                                    l.template().blueprint() == null
                                            ? "builtin"
                                            : Integer.toHexString(
                                                    l.template().blueprint().hashCode()));
                            return (java.util.Map<String, Object>) row;
                        })
                .toList();
    }

    /** Chosen once by the planner; trials and runtime never draw another building or direction. */
    public record SideWall(
            RiptideLevelTemplate template,
            String variant,
            int opening,
            int direction,
            int forwardExtent,
            boolean mirrored) {
        public SideWall(
                RiptideLevelTemplate template,
                String variant,
                int opening,
                int direction,
                int forwardExtent) {
            this(template, variant, opening, direction, forwardExtent, false);
        }

        public SideWall {
            if (template.type() != RiptideLevelType.PASS || Math.abs(direction) != 1)
                throw new IllegalArgumentException("invalid side wall");
        }

        public int thickness() {
            return template.blueprint() != null
                    ? template.blueprint().extent()
                    : variant.equals("WEAVE") ? 3 : 0;
        }
    }

    public record Level(
            int number,
            int step,
            RiptideLevelTemplate template,
            String variant,
            int opening,
            boolean mirrored,
            long contentSeed,
            int wallGroup,
            int beat,
            String rhythm,
            int sweep,
            List<SideWall> sideWalls) {
        public Level {
            sideWalls = List.copyOf(sideWalls);
        }

        public Level(
                int number,
                int step,
                RiptideLevelTemplate template,
                String variant,
                int opening,
                boolean mirrored,
                long contentSeed,
                int wallGroup,
                int beat,
                String rhythm,
                int sweep) {
            this(
                    number,
                    step,
                    template,
                    variant,
                    opening,
                    mirrored,
                    contentSeed,
                    wallGroup,
                    beat,
                    rhythm,
                    sweep,
                    List.of());
        }

        public Level(
                int number,
                int step,
                RiptideLevelTemplate template,
                String variant,
                int opening,
                boolean mirrored,
                long contentSeed) {
            this(
                    number,
                    step,
                    template,
                    variant,
                    opening,
                    mirrored,
                    contentSeed,
                    0,
                    0,
                    "SINGLE",
                    0);
        }

        public RiptideLevelType type() {
            return template.type();
        }

        public RiptideStageKind kind() {
            if (isSideSweep()) return RiptideStageKind.SIDE_SWEEP;
            return switch (type()) {
                case PASS -> RiptideStageKind.PASS;
                case MATH -> RiptideStageKind.MATH;
                case COLOR_FLOOR -> RiptideStageKind.COLOR_FLOOR;
                case DODGE -> RiptideStageKind.DODGE;
                case RHYTHM -> RiptideStageKind.RHYTHM;
            };
        }

        public boolean isSideSweep() {
            return sweep != 0;
        }

        public boolean stopsRaft() {
            return kind().stopsRaft();
        }

        public boolean colorFloor() {
            return kind() == RiptideStageKind.COLOR_FLOOR;
        }

        public boolean sideMath() {
            return sweep != 0 && rhythm.equals("SIDE_MATH");
        }

        public String displayName() {
            return sweep != 0
                    ? (sideMath() ? "侧向+解题" : "侧向")
                            + sideWalls.size()
                            + "连墙（"
                            + sideWalls.stream()
                                    .map(w -> w.direction() > 0 ? "左" : "右")
                                    .collect(java.util.stream.Collectors.joining("→"))
                            + "）"
                    : template.name()
                            + (wallGroup == 0
                                    ? ""
                                    : (type() == RiptideLevelType.MATH
                                            ? " • 第" + beat + "道"
                                            : " • 连墙第" + beat + "面"));
        }

        public int extent() {
            // Stopped stages have a single entrance; geometry reserves the common stopped deck.
            if (stopsRaft()) return 0;
            return variant.equals("DOUBLE")
                    ? 6 + (template.blueprint() == null ? 0 : template.blueprint().extent())
                    : template.blueprint() != null
                            ? template.blueprint().extent()
                            : variant.equals("WEAVE") ? 3 : 0;
        }

        public RiptideQuestion question(int minimum, int maximum) {
            return question(minimum, maximum, 0);
        }

        public RiptideQuestion question(int minimum, int maximum, int stage) {
            if (type() != RiptideLevelType.MATH) throw new IllegalStateException("not a math gate");
            return RiptideQuestion.generate(
                    variant, new Random(contentSeed), minimum, maximum, stage);
        }
    }
}

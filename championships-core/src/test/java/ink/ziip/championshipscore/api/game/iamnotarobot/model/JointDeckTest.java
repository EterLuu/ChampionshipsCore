package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class JointDeckTest {
    @Test
    void seededDecksMeetEveryConstraintAndEveryCarvingStyleReachesItsShare() {
        List<Set<JointShape>> distinct = List.of(new HashSet<>(), new HashSet<>(), new HashSet<>());
        List<Set<Integer>> pencilApexes =
                List.of(new HashSet<>(), new HashSet<>(), new HashSet<>());
        List<Set<Integer>> manualCounts =
                List.of(new HashSet<>(), new HashSet<>(), new HashSet<>());
        var styles = new EnumMap<JointDeck.Style, Integer>(JointDeck.Style.class);
        for (long seed = -2048; seed < 2048; seed++) {
            JointDeck deck = new JointDeck(seed), repeat = new JointDeck(seed);
            Set<JointShape> inDeck = new HashSet<>();
            long second = JointDeck.mix(JointDeck.mix(seed)), third = JointDeck.mix(second);
            for (int round = 1; round <= 3; round++) {
                JointShape shape = deck.shape(round);
                String context = "seed " + seed + " round " + round;
                assertEquals(shape, repeat.shape(round), context);
                assertTrue(inDeck.add(shape), context);
                Set<Integer> levels = new HashSet<>();
                int grayDug = 0, volume = 0;
                int[][] precut = deck.initialGrayCut(round);
                assertArrayEquals(precut, repeat.initialGrayCut(round), context);
                var gray = new boolean[7][7][6];
                for (int row = 0; row < 7; row++)
                    for (int col = 0; col < 7; col++) {
                        int depth = shape.depth(row, col);
                        assertTrue(depth >= 0 && depth <= 6, context);
                        levels.add(depth);
                        volume += depth;
                        assertTrue(precut[row][col] >= 0 && precut[row][col] <= depth, context);
                        for (int d = 1; d <= 6; d++) {
                            gray[row][col][d - 1] = d > depth;
                            if (d > precut[row][col] && !gray[row][col][d - 1]) grayDug++;
                        }
                    }
                int removed = JointShape.MAX_DEPTH * JointShape.SIZE * JointShape.SIZE - volume;
                assertTrue(
                        removed >= JointDeck.MIN_REMOVED && removed <= JointDeck.MAX_REMOVED,
                        context + " removed " + removed);
                manualCounts.get(round - 1).add(grayDug);
                assertEquals(
                        JointFit.Outcome.PASS,
                        JointFit.evaluate(shape, gray, 14).outcome(),
                        context);
                assertTrue(levels.size() >= 2 && levels.contains(JointShape.MAX_DEPTH), context);
                if (round == 1) assertEquals(2, levels.size(), context);
                if (round == 3) {
                    assertAsymmetric(shape, context);
                    var carving = JointDeck.carving(third);
                    assertEquals(carving.shape(), shape, context);
                    styles.merge(carving.style(), 1, Integer::sum);
                }
                assertNoFloatingParts(shape, context);
                distinct.get(round - 1).add(shape);
            }
            int metric = choose(second >>> 16, 6);
            if (metric < 3)
                pencilApexes
                        .get(metric)
                        .add(
                                deck.shape(2)
                                        .depth(2 + choose(second, 3), 2 + choose(second >>> 8, 3)));
        }
        int[] lowest = {20, 24, 32}, highest = {32, 40, 48};
        for (int i = 0; i < 3; i++) {
            Set<Integer> expected = new HashSet<>();
            for (int n = lowest[i]; n <= highest[i]; n++) expected.add(n);
            assertEquals(expected, manualCounts.get(i));
        }
        assertTrue(distinct.get(0).size() >= 30);
        assertTrue(distinct.get(1).size() >= 20);
        assertTrue(distinct.get(2).size() >= 3000);
        for (var style : JointDeck.Style.values())
            assertTrue(styles.getOrDefault(style, 0) >= 4096 / 5, style + " " + styles);
        for (Set<Integer> apexes : pencilApexes) assertTrue(apexes.size() >= 2);
        System.out.printf(
                Locale.ROOT,
                "DECK seeds 4096, distinct %d / %d / %d, styles %s%n",
                distinct.get(0).size(),
                distinct.get(1).size(),
                distinct.get(2).size(),
                styles);
    }

    private static int choose(long value, int bound) {
        return (int) Long.remainderUnsigned(value, bound);
    }

    private static void assertAsymmetric(JointShape shape, String context) {
        // Reject all seven non-identity rotations/reflections of the square.
        for (int symmetry = 1; symmetry < 8; symmetry++) {
            boolean different = false;
            for (int r = 0; r < 7; r++)
                for (int c = 0; c < 7; c++) {
                    int rr = r, cc = c;
                    if (symmetry >= 4) cc = 6 - cc;
                    for (int turn = 0; turn < symmetry % 4; turn++) {
                        int next = cc;
                        cc = 6 - rr;
                        rr = next;
                    }
                    different |= shape.depth(r, c) != shape.depth(rr, cc);
                }
            assertTrue(different, context + " symmetry " + symmetry);
        }
    }

    /**
     * Rebuilds both blocks in world-relative X (white x13..21, gray x-4..4) and requires every
     * remaining block to be face-connected to its fixed base (white x19..21, gray x-4..-2).
     */
    private static void assertNoFloatingParts(JointShape shape, String context) {
        boolean[][][] white = new boolean[9][7][7], gray = new boolean[9][7][7];
        for (int r = 0; r < 7; r++)
            for (int c = 0; c < 7; c++) {
                int depth = shape.depth(r, c);
                for (int x = 0; x < 9; x++) white[x][r][c] = x >= 6 - depth;
                for (int x = 0; x < 9; x++) gray[x][r][c] = x <= 8 - depth;
            }
        assertConnected(white, 6, 8, context + " white");
        assertConnected(gray, 0, 2, context + " gray");
    }

    private static void assertConnected(boolean[][][] solid, int baseFrom, int baseTo, String ctx) {
        boolean[][][] seen = new boolean[9][7][7];
        var queue = new ArrayDeque<int[]>();
        for (int x = baseFrom; x <= baseTo; x++)
            for (int r = 0; r < 7; r++)
                for (int c = 0; c < 7; c++) {
                    assertTrue(solid[x][r][c], ctx + " base");
                    seen[x][r][c] = true;
                    queue.add(new int[] {x, r, c});
                }
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            for (int[] step : steps) {
                int x = cell[0] + step[0], r = cell[1] + step[1], c = cell[2] + step[2];
                if (x < 0 || x > 8 || r < 0 || r > 6 || c < 0 || c > 6) continue;
                if (!solid[x][r][c] || seen[x][r][c]) continue;
                seen[x][r][c] = true;
                queue.add(new int[] {x, r, c});
            }
        }
        for (int x = 0; x < 9; x++)
            for (int r = 0; r < 7; r++)
                for (int c = 0; c < 7; c++)
                    assertFalse(solid[x][r][c] && !seen[x][r][c], ctx + " floating at " + x);
    }

    @Test
    void everyCarvingStyleIsReadableFromTheChannel() {
        var shown = new EnumMap<JointDeck.Style, Integer>(JointDeck.Style.class);
        for (long seed = 0; shown.values().stream().mapToInt(Integer::intValue).sum() < 9; seed++) {
            var carving = JointDeck.carving(JointDeck.mix(seed));
            if (shown.getOrDefault(carving.style(), 0) >= 3) continue;
            shown.merge(carving.style(), 1, Integer::sum);
            // Facing east in the channel: top row first, then north to south.
            var render = new StringBuilder("CARVING " + carving.style() + "\n");
            for (int r = 6; r >= 0; r--) {
                for (int c = 0; c < 7; c++) render.append(carving.shape().depth(r, c));
                render.append('\n');
            }
            System.out.print(render);
        }
    }
}

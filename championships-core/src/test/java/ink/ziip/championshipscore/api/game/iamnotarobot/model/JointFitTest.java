package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.JointPlaza;

import org.junit.jupiter.api.Test;

class JointFitTest {
    @Test
    void smallGrayExcavationsStartWithAnIntactFace() {
        for (int blocks : new int[] {0, 1, 47, 48, 49}) {
            var depth = new int[7][7];
            for (int i = 0; i < blocks; i++) depth[i / 7][i % 7] = 1;
            var cut = new JointShape(depth).initialGrayCut(32);
            int precut = 0;
            for (int[] row : cut) for (int d : row) precut += d;
            assertEquals(blocks <= 48 ? 0 : blocks - 32, precut);
        }
    }

    @Test
    void computedStopIsTheLastCollisionFreePositionInTheMap() {
        var random = new java.util.Random(1234);
        for (int seed = 0; seed < 100; seed++) {
            JointShape shape = new JointDeck(seed).shape(1 + seed % 3);
            var solid = new boolean[7][7][6];
            for (int row = 0; row < 7; row++)
                for (int col = 0; col < 7; col++)
                    for (int depth = 0; depth < 6; depth++)
                        solid[row][col][depth] = random.nextInt(3) == 0;
            var fit = JointFit.evaluate(shape, solid, JointPlaza.STROKE);
            for (int travel = 0; travel <= JointPlaza.STROKE; travel++) {
                boolean overlap = false;
                for (int row = 0; row < 7; row++)
                    for (int col = 0; col < 7; col++) {
                        int leading = JointPlaza.WHITE_BASE_X - shape.depth(row, col) - travel;
                        for (int depth = 0; depth < 6; depth++)
                            overlap |=
                                    solid[row][col][depth]
                                            && leading <= JointPlaza.GRAY_MAX_X - depth;
                    }
                assertEquals(travel > fit.distance(), overlap);
            }
        }
    }

    @Test
    void evaluatesCollisionBeforeGapsAndCountsGapsAfterClearTravel() {
        int[][] depths = new int[7][7];
        depths[0][0] = 6;
        depths[0][1] = 3;
        JointShape shape = new JointShape(depths);
        boolean[][][] solid = new boolean[7][7][6];
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 7; c++) {
                for (int d = 1; d <= 6; d++) solid[r][c][d - 1] = d > shape.depth(r, c);
            }
        }
        assertEquals(
                new JointFit.Result(JointFit.Outcome.PASS, 14, 0),
                JointFit.evaluate(shape, solid, 14));
        solid[0][0][0] = true;
        solid[1][1][0] = false;
        assertEquals(
                new JointFit.Result(JointFit.Outcome.BLOCKED, 8, 0),
                JointFit.evaluate(shape, solid, 14));
        solid[0][0][0] = false;
        solid[1][1][1] = false;
        assertEquals(
                new JointFit.Result(JointFit.Outcome.GAP, 14, 2),
                JointFit.evaluate(shape, solid, 14));
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 7; c++) java.util.Arrays.fill(solid[r][c], true);
        }
        assertEquals(
                new JointFit.Result(JointFit.Outcome.BLOCKED, 8, 0),
                JointFit.evaluate(shape, solid, 14));
    }
}

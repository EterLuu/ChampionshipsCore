package ink.ziip.championshipscore.api.game.buildmart.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class GoldenBlueprintSchedulerTest {

    @Test
    void rotationsRespectPeriodFinalSettlementAndDuplicateOrStaleTicks() {
        record Example(int duration, int period, List<Integer> expected) {}
        for (var example :
                List.of(
                        new Example(900, 180, List.of(720, 540, 360, 180)),
                        new Example(250, 120, List.of(130, 10)),
                        new Example(60, 120, List.of()))) {
            var rotations = new ArrayList<Integer>();
            int[] remaining = {example.duration()};
            var scheduler =
                    new GoldenBlueprintScheduler(
                            example.duration(),
                            example.period(),
                            () -> rotations.add(remaining[0]));
            for (; remaining[0] >= 0; remaining[0]--) {
                scheduler.tick(remaining[0]);
                scheduler.tick(remaining[0]);
            }
            scheduler.tick(-1);
            scheduler.tick(example.duration());
            assertEquals(example.expected(), rotations, example.toString());
        }
    }
}

package ink.ziip.championshipscore.api.game.buildmart;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class GoldenBlueprintSchedulerTest {
    @Test void finalGoldenBuildSurvivesTheSettlementBoundary() {
        List<Integer> rotations = new ArrayList<>();
        int[] remaining = {720};
        var scheduler = new GoldenBlueprintScheduler(720, 120, () -> rotations.add(remaining[0]));
        for (; remaining[0] >= 0; remaining[0]--) scheduler.tick(remaining[0]);
        assertEquals(List.of(600, 480, 360, 240, 120), rotations);
    }
    @Test void shortFinalWindowAndDuplicateTicksDoNotIntroduceExtraRotations() {
        int[] rotations = {0};
        var scheduler = new GoldenBlueprintScheduler(250, 120, () -> rotations[0]++);
        for (int remaining = 250; remaining >= 0; remaining--) {
            scheduler.tick(remaining); scheduler.tick(remaining);
        }
        assertEquals(2, rotations[0]);
        scheduler.tick(-1); scheduler.tick(250);
        assertEquals(2, rotations[0]);
    }
    @Test void roundShorterThanPeriodKeepsInitialOrder() {
        var scheduler = new GoldenBlueprintScheduler(60, 120, () -> fail("unexpected rotation"));
        for (int remaining = 60; remaining >= 0; remaining--) scheduler.tick(remaining);
    }
}

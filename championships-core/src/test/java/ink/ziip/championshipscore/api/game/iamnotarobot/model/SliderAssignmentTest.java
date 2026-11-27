package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class SliderAssignmentTest {
    @Test
    void initialRosterAndLateArrivalsKeepTheirClaimsAcrossReconnects() {
        var assignment = new SliderAssignment();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var late = UUID.randomUUID();
        var last = UUID.randomUUID();
        assertEquals(
                List.of(SliderHall.A, SliderHall.C),
                List.of(first, second).stream().map(assignment::claim).toList());
        assertNull(assignment.owner(SliderHall.B));
        assertNull(assignment.track(late));
        assertEquals(SliderHall.B, assignment.claim(late));
        assertEquals(SliderHall.A, assignment.claim(first));
        assertEquals(SliderHall.D, assignment.claim(last));
        assertNull(assignment.claim(UUID.randomUUID()));
        for (var member : List.of(first, second, late, last))
            assertEquals(member, assignment.owner(assignment.track(member)));
    }

    @Test
    void partnersFaceEachOtherAcrossTheHall() {
        assertEquals(
                List.of(SliderHall.C, SliderHall.D, SliderHall.A, SliderHall.B),
                List.of(SliderHall.values()).stream().map(SliderAssignment::partner).toList());
    }
}

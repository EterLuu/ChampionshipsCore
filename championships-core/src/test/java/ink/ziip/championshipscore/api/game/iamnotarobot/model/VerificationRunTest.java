package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.VerificationCourse.Room;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class VerificationRunTest {
    private final VerificationRun run = afterEntry();

    private static VerificationRun afterEntry() {
        var team = new VerificationRun();
        for (int slot = 0; slot < 4; slot++) team.arrive(slot, 0);
        assertTrue(team.begin());
        return team;
    }

    @Test
    void eachAssignedTrackCountsOnceAndTheFourthArrivalStartsTheNextSplit() {
        var team = new VerificationRun();
        assertEquals(Room.ENTRY, team.room());
        assertFalse(team.arrive(-1, 2));
        assertFalse(team.arrive(4, 2));
        for (int slot : List.of(3, 0, 2)) assertTrue(team.arrive(slot, 6));
        assertFalse(team.arrive(3, 8));
        assertTrue(team.arrived(0));
        assertFalse(team.completeWarehouse(100));
        assertEquals(0, team.points(List.of(team)));
        assertEquals(3, team.arrivalCount());
        assertTrue(team.arrive(1, 110));
        assertFalse(team.arrive(1, 120));
        assertEquals(Room.WAREHOUSE, team.room());
        assertEquals(110, team.time(Room.ENTRY));
        assertFalse(team.completeWarehouse(160));
        assertTrue(team.begin());
        assertTrue(team.completeWarehouse(160));
        assertEquals(50, team.time(Room.WAREHOUSE));
    }

    @Test
    void eachRoomBeginsOnceAndWaitingIsIncludedInTheNextSplit() {
        var team = new VerificationRun();
        assertTrue(team.begun());
        assertFalse(team.begin());
        for (int slot = 0; slot < 4; slot++) team.arrive(slot, 10);
        for (Room next : List.of(Room.WAREHOUSE, Room.SLIDER, Room.JOINT, Room.SPRINT)) {
            assertEquals(next, team.room());
            assertFalse(team.begun());
            assertTrue(team.begin());
            assertTrue(team.begun());
            assertFalse(team.begin());
            switch (next) {
                case WAREHOUSE -> assertTrue(team.completeWarehouse(70));
                case SLIDER -> assertTrue(team.completeSlider(115));
                case JOINT -> assertTrue(team.completeJoint(160));
                case SPRINT -> assertTrue(team.crossFinish(true, 200));
            }
        }
        assertEquals(60, team.time(Room.WAREHOUSE));
        assertEquals(45, team.time(Room.SLIDER));
        assertEquals(45, team.time(Room.JOINT));
        assertEquals(200, team.time(Room.SPRINT));
        assertFalse(team.begin());
    }

    @Test
    void finishRemainsTheRecoveryPointUntilThePlayerWalksIntoTheNextRoom() {
        var team = new VerificationRun();
        team.arrive(0, 10);
        team.enterPuzzleRooms(0);
        assertTrue(team.onEntryTrack(0));
        for (int slot : List.of(1, 2, 3)) team.arrive(slot, 20);
        assertEquals(Room.WAREHOUSE, team.room());
        for (int slot = 0; slot < 4; slot++) assertTrue(team.onEntryTrack(slot));
        assertTrue(team.arrived(0));
        assertTrue(team.onEntryTrack(0));
        team.enterPuzzleRooms(1);
        assertFalse(team.onEntryTrack(1));
        assertTrue(team.onEntryTrack(0));
    }

    @Test
    void entryCheckpointsAdvanceMonotonicallyPerSlotAndIgnoreInvalidOrCompletedTracks() {
        var team = new VerificationRun();
        assertEquals(0, team.checkpoint(0));
        assertEquals(0, team.checkpoint(-1));
        assertEquals(0, team.checkpoint(4));
        assertFalse(team.reachCheckpoint(-1, 1));
        assertFalse(team.reachCheckpoint(4, 1));
        assertFalse(team.reachCheckpoint(0, 0));
        assertFalse(team.reachCheckpoint(0, 4));
        assertTrue(team.reachCheckpoint(0, 1));
        assertEquals(1, team.checkpoint(0));
        assertEquals(0, team.checkpoint(1));
        assertFalse(team.reachCheckpoint(0, 1));
        assertTrue(team.reachCheckpoint(0, 3));
        assertFalse(team.reachCheckpoint(0, 2));
        assertEquals(3, team.checkpoint(0));
        assertEquals(3, team.checkpoint(0));
        assertTrue(team.arrive(0, 40));
        assertFalse(team.reachCheckpoint(0, 2));
        for (int slot : List.of(1, 2, 3)) assertTrue(team.arrive(slot, 60));
        assertFalse(team.reachCheckpoint(1, 2));
    }

    @Test
    void warehouseDrawAlwaysHasFourDifferentAvailableMaterialsAndTwoComplementaryReaders() {
        var random = new java.util.Random(29);
        var readers = new java.util.HashSet<Integer>();
        var amounts = new java.util.HashSet<Integer>();
        var seenStock = new java.util.HashSet<org.bukkit.Material>();
        var seenTargets = new java.util.HashSet<org.bukkit.Material>();
        var seenRackIndices = new java.util.HashSet<Integer>();
        for (int draw = 0; draw < 200; draw++) {
            var code = WarehouseCode.draw(random);
            readers.add(code.materialReader());
            assertEquals(
                    ink.ziip.championshipscore.api.game.iamnotarobot.geometry.WarehouseRoom
                            .SLOT_COUNT,
                    code.stock().size());
            assertEquals(code.stock().size(), java.util.Set.copyOf(code.stock()).size());
            assertTrue(
                    ink.ziip.championshipscore.api.game.iamnotarobot.geometry.WarehouseRoom
                            .STOCK_POOL
                            .containsAll(code.stock()));
            seenStock.addAll(code.stock());
            assertEquals(
                    4,
                    code.digits().stream().map(WarehouseCode.Digit::material).distinct().count());
            for (var digit : code.digits()) {
                int slot = code.stock().indexOf(digit.material());
                assertTrue(slot >= 0, "Target digit must appear on a warehouse shelf");
                seenTargets.add(digit.material());
                seenRackIndices.add(
                        slot
                                / ink.ziip.championshipscore.api.game.iamnotarobot.geometry
                                        .WarehouseRoom.BAYS_PER_RACK);
                amounts.add(digit.amount());
            }
        }
        assertEquals(java.util.Set.of(0, 3), readers);
        assertEquals(java.util.Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9), amounts);
        assertTrue(seenStock.size() >= 100, "Shelf stock must rotate broadly across the pool");
        assertTrue(seenTargets.size() >= 90, "Captcha digits must rotate broadly across the pool");
        assertEquals(
                ink.ziip.championshipscore.api.game.iamnotarobot.geometry.WarehouseRoom.RACKS,
                seenRackIndices.size());
        assertTrue(run.completeWarehouse(40));
        assertFalse(run.completeWarehouse(80));
        assertEquals(40, run.time(Room.WAREHOUSE));
    }

    @Test
    void sliderPassesArePersonalCappedAndOnlyCountWhileTheHallIsOpen() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        assertEquals(0, run.passSlider(a));
        assertTrue(run.completeWarehouse(20));
        assertEquals(0, run.passSlider(a));
        assertTrue(run.begin());
        assertEquals(1, run.passSlider(a));
        assertFalse(run.sliderDone(a));
        assertEquals(2, run.passSlider(a));
        assertFalse(run.sliderDone(a));
        assertEquals(3, run.passSlider(a));
        assertTrue(run.sliderDone(a));
        assertEquals(3, run.passSlider(a));
        assertEquals(0, run.sliderPasses(b));
        assertEquals(1, run.passSlider(b));
        assertTrue(run.completeSlider(60));
        assertEquals(1, run.passSlider(b));
        assertEquals(VerificationRun.SLIDER_PASSES, run.sliderPasses(a));
    }

    @Test
    void sliderCanOnlyCommitOnceAfterWarehouse() {
        assertFalse(run.completeSlider(10));
        assertTrue(run.completeWarehouse(20));
        assertFalse(run.completeSlider(45));
        assertTrue(run.begin());
        assertTrue(run.completeSlider(45));
        assertFalse(run.completeSlider(65));
        assertEquals(25, run.time(Room.SLIDER));
    }

    @Test
    void jointCommitsOnceInOrderAndFinishRequiresTheTeamToGather() {
        assertFalse(run.completeJoint(10));
        warehouseAndSlider(run, 20, 60);
        assertEquals(Room.JOINT, run.room());
        assertFalse(run.crossFinish(true, 70));
        assertFalse(run.completeJoint(80));
        assertTrue(run.begin());
        assertTrue(run.completeJoint(80));
        assertFalse(run.completeJoint(90));
        assertFalse(run.crossFinish(true, 90));
        assertTrue(run.begin());
        assertFalse(run.crossFinish(false, 90));
        assertTrue(run.crossFinish(true, 100));
        assertFalse(run.crossFinish(true, 200));
        assertEquals(20, run.time(Room.JOINT));
        assertEquals(100, run.time(Room.SPRINT));
    }

    @Test
    void slowerOverallTeamCanWinASeparateRoomAndTiesSharePlacement() {
        var second = afterEntry();
        var incomplete = afterEntry();
        warehouseAndSlider(run, 30, 80);
        warehouseAndSlider(second, 50, 70);
        incomplete.completeWarehouse(30);
        run.begin();
        run.completeJoint(90);
        run.begin();
        run.crossFinish(true, 100);
        var field = List.of(run, second, incomplete);
        assertEquals(590, run.points(field));
        assertEquals(280, second.points(field));
        assertEquals(200, incomplete.points(field));
    }

    private void warehouseAndSlider(VerificationRun team, int first, int second) {
        assertTrue(team.completeWarehouse(first));
        assertTrue(team.begin());
        assertTrue(team.completeSlider(second));
    }
}

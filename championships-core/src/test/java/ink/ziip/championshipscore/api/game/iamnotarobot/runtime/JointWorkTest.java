package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

class JointWorkTest {
    @Test
    void invalidatedJobsDropAndEachAtomicSwapGetsItsOwnTick() {
        var jobs = new ArrayDeque<JointWork.Job>();
        var ran = new ArrayList<String>();
        jobs.add(new JointWork.Job(() -> false, () -> fail("stale generation"), JointWork.BUDGET));
        jobs.add(new JointWork.Job(() -> true, () -> ran.add("carve"), 1));
        for (String swap : List.of("west team", "east team", "restore"))
            jobs.add(new JointWork.Job(() -> true, () -> ran.add(swap), JointWork.BUDGET));
        JointWork.slice(jobs);
        assertEquals(List.of("carve"), ran);
        for (int i = 1; i <= 3; i++) {
            JointWork.slice(jobs);
            assertEquals(1 + i, ran.size());
        }
        assertEquals(List.of("carve", "west team", "east team", "restore"), ran);
        assertTrue(jobs.isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> new JointWork.Job(() -> true, () -> true, JointWork.BUDGET + 1));
    }

    @Test
    void reentrantUnloadDoesNotRemoveTheReplacementJob() {
        var jobs = new ArrayDeque<JointWork.Job>();
        var ran = new ArrayList<String>();
        jobs.add(
                new JointWork.Job(
                        () -> true,
                        () -> {
                            jobs.clear();
                            jobs.add(
                                    new JointWork.Job(
                                            () -> true,
                                            () -> ran.add("replacement"),
                                            JointWork.BUDGET));
                            return true;
                        },
                        JointWork.BUDGET));
        JointWork.slice(jobs);
        assertEquals(1, jobs.size());
        JointWork.slice(jobs);
        assertEquals(List.of("replacement"), ran);
    }
}

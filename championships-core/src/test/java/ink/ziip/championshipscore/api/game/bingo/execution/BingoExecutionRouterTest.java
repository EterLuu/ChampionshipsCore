package ink.ziip.championshipscore.api.game.bingo.execution;

import ink.ziip.championshipscore.api.object.game.GameRunMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BingoExecutionRouterTest {
    private static final BingoStartRequest EVENT = new BingoStartRequest("bingo", true, GameRunMode.EVENT);

    @Test
    void standaloneRequestCannotEnableEventIntroduction() {
        BingoStartRequest request = new BingoStartRequest("bingo", true, GameRunMode.GAME);

        assertFalse(request.showIntroduction());
        assertEquals(GameRunMode.GAME, request.runMode());
    }

    @Test
    void lifecycleOperationsFollowTheActiveExecutionPlane() {
        FakeGateway local = new FakeGateway(BingoExecutionMode.LOCAL, true);
        FakeGateway remote = new FakeGateway(BingoExecutionMode.REMOTE, false);
        BingoExecutionRouter router = new BingoExecutionRouter(local);

        assertTrue(router.canStart(EVENT));
        assertTrue(router.start(EVENT));
        router.forceEnd("local-stop");

        router.activateRemote(remote);
        assertFalse(router.canStart(EVENT));
        assertFalse(router.start(EVENT));
        router.forceEnd("event-stop");

        assertEquals(List.of("local-stop"), local.forceEndReasons);
        assertEquals(List.of("event-stop"), remote.forceEndReasons);
        assertEquals(1, local.starts);
        assertEquals(1, remote.starts);
    }

    private static final class FakeGateway implements BingoExecutionGateway {
        private final BingoExecutionMode mode;
        private final boolean available;
        private final List<String> forceEndReasons = new ArrayList<>();
        private int starts;

        private FakeGateway(BingoExecutionMode mode, boolean available) {
            this.mode = mode;
            this.available = available;
        }

        @Override
        public BingoExecutionMode mode() {
            return mode;
        }

        @Override
        public boolean canStart(BingoStartRequest request) {
            return available;
        }

        @Override
        public boolean start(BingoStartRequest request) {
            starts++;
            return available;
        }

        @Override
        public void forceEnd(String reason) {
            forceEndReasons.add(reason);
        }
    }
}

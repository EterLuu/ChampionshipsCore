package ink.ziip.championshipscore.database.rank;

import static ink.ziip.championshipscore.support.TestObjects.proxy;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.rank.entry.GameStatusEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.logging.Logger;

class RankDaoImplTest {
    @Test
    void missingRowAndDatabaseFailureAreDistinct() {
        var jdbc = new Jdbc();
        assertEquals(OptionalInt.empty(), jdbc.dao().getGameStatusOrder(GameTypeEnum.Bingo));
        jdbc.order = 3;
        assertEquals(OptionalInt.of(3), jdbc.dao().getGameStatusOrder(GameTypeEnum.Bingo));
        jdbc.failAt = "query";
        var failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> jdbc.dao().getGameStatusOrder(GameTypeEnum.Bingo));
        assertInstanceOf(SQLException.class, failure.getCause());
        assertEquals("close", jdbc.calls.getLast());
    }

    @Test
    void connectionFailureDoesNotBecomeMissingRow() {
        var failure = new SQLException("unavailable");
        var dao =
                new RankDaoImpl(
                        () -> {
                            throw failure;
                        },
                        Logger.getAnonymousLogger());
        assertSame(
                failure,
                assertThrows(
                                IllegalStateException.class,
                                () -> dao.getGameStatusOrder(GameTypeEnum.Bingo))
                        .getCause());
    }

    @Test
    void insertRequiresOneWrittenRow() {
        var jdbc = new Jdbc();
        var entry = GameStatusEntry.builder().game(GameTypeEnum.Bingo).order(2).time("now").build();
        jdbc.dao().addGameStatus(entry);
        assertEquals(List.of("now", "Bingo", 2), jdbc.parameters);
        jdbc.updateCount = 0;
        assertThrows(IllegalStateException.class, () -> jdbc.dao().addGameStatus(entry));
        jdbc.failAt = "write1";
        jdbc.writes = 0;
        assertThrows(IllegalStateException.class, () -> jdbc.dao().addGameStatus(entry));
    }

    @Test
    void resetUsesOneStatementAndPreservesTheSupportedGameBoundary() {
        var jdbc = new Jdbc();
        jdbc.dao().resetGameStatus();
        assertEquals(1, jdbc.sql.size());
        assertEquals(1, jdbc.writes);
        assertTrue(jdbc.sql.getFirst().startsWith("DELETE FROM `game_status` WHERE `game` IN ("));
        assertEquals(
                java.util.Arrays.stream(GameTypeEnum.values()).map(Enum::name).toList(),
                jdbc.parameters);
        jdbc.failAt = "write1";
        jdbc.writes = 0;
        assertThrows(IllegalStateException.class, () -> jdbc.dao().resetGameStatus());
    }

    @Test
    void statusAndPointsCommitTogether() {
        var jdbc = new Jdbc();
        jdbc.dao().deleteGameRecords(GameTypeEnum.BattleBox);
        assertEquals(List.of("begin", "write1", "write2", "commit", "close"), jdbc.calls);
        assertEquals(List.of("BattleBox", "BattleBox"), jdbc.parameters);
    }

    @ParameterizedTest
    @ValueSource(strings = {"write1", "write2", "commit"})
    void failedDeletionRollsBackAndReleasesConnection(String failAt) {
        var jdbc = new Jdbc();
        jdbc.failAt = failAt;
        var failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> jdbc.dao().deleteGameRecords(GameTypeEnum.BattleBox));
        assertEquals(failAt, failure.getCause().getMessage());
        assertEquals(
                List.of("rollback", "close"),
                jdbc.calls.subList(jdbc.calls.size() - 2, jdbc.calls.size()));
        if (!failAt.equals("commit")) assertFalse(jdbc.calls.contains("commit"));
    }

    @Test
    void failedRollbackPreservesOriginalFailure() {
        var jdbc = new Jdbc();
        jdbc.failAt = "write2";
        jdbc.failRollback = true;
        var failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> jdbc.dao().deleteGameRecords(GameTypeEnum.Bingo));
        assertEquals("write2", failure.getCause().getMessage());
        assertEquals("rollback", failure.getCause().getSuppressed()[0].getMessage());
        assertEquals("close", jdbc.calls.getLast());
    }

    private static final class Jdbc {
        final List<String> calls = new ArrayList<>();
        final List<String> sql = new ArrayList<>();
        final List<Object> parameters = new ArrayList<>();
        String failAt;
        boolean failRollback;
        Integer order;
        int writes;
        int updateCount = 1;

        RankDaoImpl dao() {
            return new RankDaoImpl(
                    () ->
                            proxy(
                                    Connection.class,
                                    (p, m, a) -> {
                                        return switch (m.getName()) {
                                            case "prepareStatement" -> statement((String) a[0]);
                                            case "setAutoCommit" -> {
                                                assertEquals(false, a[0]);
                                                yield call("begin");
                                            }
                                            case "commit", "rollback", "close" -> call(m.getName());
                                            default -> throw new AssertionError(m.getName());
                                        };
                                    }),
                    Logger.getAnonymousLogger());
        }

        private Object call(String name) throws SQLException {
            calls.add(name);
            if (name.equals(failAt) || name.equals("rollback") && failRollback)
                throw new SQLException(name);
            return null;
        }

        private PreparedStatement statement(String text) {
            sql.add(text);
            return proxy(
                    PreparedStatement.class,
                    (p, m, a) ->
                            switch (m.getName()) {
                                case "setString", "setInt" -> {
                                    parameters.add(a[1]);
                                    yield null;
                                }
                                case "executeUpdate" -> {
                                    call("write" + ++writes);
                                    yield updateCount;
                                }
                                case "executeQuery" -> {
                                    call("query");
                                    yield proxy(
                                            ResultSet.class,
                                            (rp, rm, ra) ->
                                                    switch (rm.getName()) {
                                                        case "next" -> order != null;
                                                        case "getInt" -> order;
                                                        case "close" -> null;
                                                        default ->
                                                                throw new AssertionError(
                                                                        rm.getName());
                                                    });
                                }
                                case "close" -> null;
                                default -> throw new AssertionError(m.getName());
                            });
        }
    }
}

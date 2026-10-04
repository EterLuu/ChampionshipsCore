package ink.ziip.championshipscore.database.map;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

class MapRecordRenameTransactionTest {
    @Test
    void commitsOnlyAfterTheCallerAcceptsItsRuntimeChanges() throws Exception {
        var calls = new ArrayList<String>();
        try (var transaction = new MapRecordRenameTransaction(connection(calls, null))) {
            assertEquals(List.of("setAutoCommit"), calls);
            transaction.commit();
            transaction.rollback();
            assertThrows(IllegalStateException.class, transaction::commit);
        }
        assertEquals(List.of("setAutoCommit", "commit", "close"), calls);
    }

    @Test
    void leavingAnUnfinishedRenameRollsBackBeforeClosing() throws Exception {
        var calls = new ArrayList<String>();
        try (var transaction = new MapRecordRenameTransaction(connection(calls, null))) {
            assertEquals(List.of("setAutoCommit"), calls);
        }
        assertEquals(List.of("setAutoCommit", "rollback", "close"), calls);
    }

    @Test
    void failedCommitCanStillBeRolledBack() throws Exception {
        var calls = new ArrayList<String>();
        try (var transaction = new MapRecordRenameTransaction(connection(calls, "commit"))) {
            assertThrows(SQLException.class, transaction::commit);
        }
        assertEquals(List.of("setAutoCommit", "commit", "rollback", "close"), calls);
    }

    @Test
    void failedRollbackStillReleasesTheConnection() throws Exception {
        var calls = new ArrayList<String>();
        var transaction = new MapRecordRenameTransaction(connection(calls, "rollback"));
        assertThrows(SQLException.class, transaction::close);
        assertEquals(List.of("setAutoCommit", "rollback", "close"), calls);
    }

    @Test
    void failureToBeginDoesNotLeakTheConnection() {
        var calls = new ArrayList<String>();
        assertThrows(
                SQLException.class,
                () -> new MapRecordRenameTransaction(connection(calls, "setAutoCommit")));
        assertEquals(List.of("setAutoCommit", "close"), calls);
    }

    private static Connection connection(List<String> calls, String failsOn) {
        return (Connection)
                Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[] {Connection.class},
                        (proxy, method, args) -> {
                            calls.add(method.getName());
                            if (method.getName().equals(failsOn))
                                throw new SQLException("Expected test failure");
                            if (method.getName().equals("setAutoCommit"))
                                assertEquals(false, args[0]);
                            return null;
                        });
    }
}

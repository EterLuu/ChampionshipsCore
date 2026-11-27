package ink.ziip.championshipscore.database.player;

import static ink.ziip.championshipscore.support.TestObjects.allocate;
import static ink.ziip.championshipscore.support.TestObjects.field;
import static ink.ziip.championshipscore.support.TestObjects.set;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariDataSource;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.database.DatabaseManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Runs against an initialized, isolated Core schema with the identity unique keys installed. */
@EnabledIfSystemProperty(named = "test.mariadb.uri", matches = ".+")
class PlayerIdentityConcurrencyTest {
    @Test
    void simultaneousNewAndDuplicateLoginsProduceOneRowPerIdentity() throws Exception {
        var singleton = field(ChampionshipsCore.class, "instance");
        Object previous = singleton.get(null);
        UUID[] ids = new UUID[8];
        String[] names = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = UUID.randomUUID();
            names[i] = "cc" + ids[i].toString().replace("-", "").substring(0, 14);
        }
        try (var pool = new HikariDataSource()) {
            pool.setJdbcUrl(System.getProperty("test.mariadb.uri"));
            pool.setUsername(System.getenv("CC_TEST_DB_USER"));
            pool.setPassword(System.getenv("CC_TEST_DB_PASSWORD"));
            pool.setMaximumPoolSize(16);
            var plugin = allocate(ChampionshipsCore.class);
            var database = new DatabaseManager(plugin);
            set(database, "dataSource", pool);
            set(database, "identityConstraints", true);
            set(plugin, "databaseManager", database);
            singleton.set(null, plugin);
            var dao = new PlayerDaoImpl();
            try {
                var gate = new CyclicBarrier(16);
                try (var executor = Executors.newFixedThreadPool(16)) {
                    var attempts = new ArrayList<Future<Boolean>>();
                    for (int i = 0; i < 16; i++) {
                        int slot = i % ids.length;
                        attempts.add(
                                executor.submit(
                                        () -> {
                                            gate.await(10, TimeUnit.SECONDS);
                                            return dao.synchronizeIdentity(names[slot], ids[slot])
                                                    .successful();
                                        }));
                    }
                    for (var attempt : attempts) assertTrue(attempt.get(30, TimeUnit.SECONDS));
                }
                try (var c = pool.getConnection();
                        var find =
                                c.prepareStatement("SELECT username FROM players WHERE uuid=?")) {
                    for (int i = 0; i < ids.length; i++) {
                        find.setString(1, ids[i].toString());
                        try (var rows = find.executeQuery()) {
                            assertTrue(rows.next());
                            assertEquals(names[i], rows.getString(1));
                            assertFalse(rows.next());
                        }
                        assertFalse(dao.synchronizeIdentity(names[i], ids[i]).changed());
                    }
                }
            } finally {
                try (var c = pool.getConnection();
                        var remove = c.prepareStatement("DELETE FROM players WHERE uuid=?")) {
                    for (UUID id : ids) {
                        remove.setString(1, id.toString());
                        remove.addBatch();
                    }
                    remove.executeBatch();
                }
            }
        } finally {
            singleton.set(null, previous);
        }
    }
}

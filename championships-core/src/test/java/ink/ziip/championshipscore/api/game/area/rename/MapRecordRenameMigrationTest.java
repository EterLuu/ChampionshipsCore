package ink.ziip.championshipscore.api.game.area.rename;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapRecordRenameMigrationTest {
    @Test
    void usesDisplayNameForFormalPointsAndRegistrationNameForDailyRows() throws Exception {
        List<SqlCall> calls = new ArrayList<>();
        Connection connection = connection(calls);

        MapRecordRenameMigration.Counts counts = MapRecordRenameMigration.migrate(connection,
                GameTypeEnum.AceRace, "acerace", "clouds", "王牌竞速", "clouds");

        assertEquals(1, counts.playerPoints());
        assertEquals(2, counts.dailyResults());
        assertEquals(4, counts.dailyRecords());
        assertEquals(List.of("clouds", "AceRace", "王牌竞速"), calls.get(0).parameters());
        assertEquals(List.of("clouds", "AceRace", "acerace"), calls.get(1).parameters());
        assertTrue(calls.get(2).sql().contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(calls.get(2).sql().contains("FROM `daily_player_records` AS source"));
        assertTrue(calls.get(2).sql().contains("`daily_player_records`.`durationMs`"));
        assertEquals(List.of("clouds", "AceRace", "acerace"), calls.get(2).parameters());
        assertEquals(List.of("AceRace", "acerace"), calls.get(3).parameters());
    }

    private static Connection connection(List<SqlCall> calls) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) {
                        String sql = (String) args[0];
                        Map<Integer, String> parameters = new HashMap<>();
                        return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                                new Class[]{PreparedStatement.class}, (statement, statementMethod, statementArgs) -> {
                                    if (statementMethod.getName().equals("setString")) {
                                        parameters.put((Integer) statementArgs[0], (String) statementArgs[1]);
                                        return null;
                                    }
                                    if (statementMethod.getName().equals("executeUpdate")) {
                                        List<String> ordered = parameters.entrySet().stream()
                                                .sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
                                        calls.add(new SqlCall(sql, ordered));
                                        if (sql.startsWith("UPDATE `player_points`")) return 1;
                                        if (sql.startsWith("UPDATE `daily_match_results`")) return 2;
                                        if (sql.startsWith("DELETE FROM `daily_player_records`")) return 4;
                                        return 3;
                                    }
                                    return defaultValue(statementMethod.getReturnType());
                                });
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        if (type == float.class) return 0F;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }

    private record SqlCall(String sql, List<String> parameters) {
    }
}

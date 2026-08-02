package ink.ziip.championshipscore.api.player.dao;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.player.entry.PlayerEntry;
import ink.ziip.championshipscore.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerDaoImpl implements PlayerDao {
    private final ChampionshipsCore plugin = ChampionshipsCore.getInstance();

    @Override
    public void addPlayer(@NotNull String name, @NotNull UUID uuid) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO `players` (`uuid`, `username`)
                    VALUES (?,?);
                    """)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);

                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            logFailure("新增玩家", exception);
        }
    }

    @Override
    @Nullable
    public PlayerEntry getPlayer(UUID uuid) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT `id`, `uuid`, `username`
                    FROM `players`
                    WHERE uuid=?
                    """)) {
                statement.setString(1, uuid.toString());
                final ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    return PlayerEntry
                            .builder()
                            .id(resultSet.getInt("id"))
                            .uuid(UUID.fromString(resultSet.getString("uuid")))
                            .name(resultSet.getString("username"))
                            .build();
                }
                return null;
            }
        } catch (SQLException exception) {
            logFailure("按 UUID 查询玩家", exception);
            return null;
        }
    }

    @Override
    @Nullable
    public PlayerEntry getPlayer(String name) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT `id`, `uuid`, `username`
                    FROM `players`
                    WHERE username=?
                    """)) {
                statement.setString(1, name);
                final ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    return PlayerEntry
                            .builder()
                            .id(resultSet.getInt("id"))
                            .uuid(UUID.fromString(resultSet.getString("uuid")))
                            .name(resultSet.getString("username"))
                            .build();
                }
                return null;
            }
        } catch (SQLException exception) {
            logFailure("按名称查询玩家", exception);
            return null;
        }
    }

    @Override
    public void deletePlayer(UUID uuid) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE
                    FROM `players`
                    WHERE `uuid`=?
                    """)) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            logFailure("删除玩家", exception);
        }
    }

    private void logFailure(String operation, SQLException exception) {
        plugin.getLogger().log(Level.SEVERE, Utils.formatModuleLog("Database", "玩家",
                "操作=" + operation + " 失败"), exception);
    }
}

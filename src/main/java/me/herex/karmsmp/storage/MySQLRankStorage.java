package me.herex.karmsmp.storage;

import me.herex.karmsmp.KaramSMP;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

public final class MySQLRankStorage implements RankStorage {

    private final KaramSMP plugin;
    private Connection connection;
    private String tableName;

    public MySQLRankStorage(KaramSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public void connect() throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");

        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database", "karamsmp");
        String username = plugin.getConfig().getString("database.mysql.username", "root");
        String password = plugin.getConfig().getString("database.mysql.password", "");
        boolean ssl = plugin.getConfig().getBoolean("database.mysql.use-ssl", false);
        String explicitTable = plugin.getConfig().getString("database.mysql.table", "");
        if (explicitTable != null && !explicitTable.isBlank()) {
            tableName = sanitizeTableName(explicitTable);
        } else {
            tableName = sanitizeTableName(plugin.getConfig().getString("database.mysql.table-prefix", "karamsmp_") + "player_ranks");
        }

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + ssl
                + "&allowPublicKeyRetrieval=true"
                + "&connectTimeout=5000"
                + "&socketTimeout=10000"
                + "&characterEncoding=utf8"
                + "&useUnicode=true"
                + "&serverTimezone=UTC";

        connection = DriverManager.getConnection(jdbcUrl, username, password);
        createTable();
    }

    @Override
    public void close() {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not close MySQL connection: " + exception.getMessage());
        }
    }

    @Override
    public Optional<String> getPlayerRank(UUID uuid) {
        ensureConnected();
        String sql = "SELECT rank_name FROM " + tableName + " WHERE uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.ofNullable(resultSet.getString("rank_name"));
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load player rank from MySQL: " + exception.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public void setPlayerRank(UUID uuid, String username, String rankName) {
        ensureConnected();
        String sql = "INSERT INTO " + tableName + " (uuid, username, rank_name) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE username = ?, rank_name = ?, updated_at = CURRENT_TIMESTAMP";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, username);
            statement.setString(3, rankName);
            statement.setString(4, username);
            statement.setString(5, rankName);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not save player rank to MySQL: " + exception.getMessage());
        }
    }

    @Override
    public void removePlayerRank(UUID uuid) {
        ensureConnected();
        String sql = "DELETE FROM " + tableName + " WHERE uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not remove player rank from MySQL: " + exception.getMessage());
        }
    }

    @Override
    public String getStorageName() {
        return "MySQL";
    }

    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
                + "username VARCHAR(64) NOT NULL,"
                + "rank_name VARCHAR(64) NOT NULL,"
                + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            statement.executeUpdate("CREATE INDEX idx_" + tableName + "_rank_name ON " + tableName + " (rank_name)");
        } catch (SQLException exception) {
            if (!String.valueOf(exception.getMessage()).toLowerCase().contains("duplicate")) {
                throw exception;
            }
        }
    }

    private void ensureConnected() {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                connect();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not connect to MySQL", exception);
        }
    }

    private String sanitizeTableName(String tableName) {
        String sanitized = tableName == null ? "karamsmp_player_ranks" : tableName.replaceAll("[^A-Za-z0-9_]", "");
        return sanitized.isBlank() ? "karamsmp_player_ranks" : sanitized;
    }
}

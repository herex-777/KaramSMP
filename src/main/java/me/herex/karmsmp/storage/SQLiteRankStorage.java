package me.herex.karmsmp.storage;

import me.herex.karmsmp.KaramSMP;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

public final class SQLiteRankStorage implements RankStorage {

    private final KaramSMP plugin;
    private Connection connection;
    private String tableName;

    public SQLiteRankStorage(KaramSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public void connect() throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        String fileName = plugin.getConfig().getString("database.sqlite.file", "player-ranks.db");
        File databaseFile = new File(dataFolder, sanitizeFileName(fileName));
        tableName = sanitizeTableName(plugin.getConfig().getString("database.sqlite.table", "player_ranks"));

        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
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
            plugin.getLogger().warning("Could not close SQLite connection: " + exception.getMessage());
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
            plugin.getLogger().warning("Could not load player rank from SQLite: " + exception.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public void setPlayerRank(UUID uuid, String username, String rankName) {
        ensureConnected();
        String sql = "INSERT INTO " + tableName + " (uuid, username, rank_name, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) "
                + "ON CONFLICT(uuid) DO UPDATE SET username = excluded.username, rank_name = excluded.rank_name, updated_at = CURRENT_TIMESTAMP";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, username);
            statement.setString(3, rankName);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not save player rank to SQLite: " + exception.getMessage());
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
            plugin.getLogger().warning("Could not remove player rank from SQLite: " + exception.getMessage());
        }
    }

    @Override
    public String getStorageName() {
        return "SQLite";
    }

    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "uuid TEXT NOT NULL PRIMARY KEY,"
                + "username TEXT NOT NULL,"
                + "rank_name TEXT NOT NULL,"
                + "updated_at TEXT DEFAULT CURRENT_TIMESTAMP"
                + ")";

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void ensureConnected() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not connect to SQLite", exception);
        }
    }

    private String sanitizeTableName(String tableName) {
        String sanitized = tableName == null ? "player_ranks" : tableName.replaceAll("[^A-Za-z0-9_]", "");
        return sanitized.isBlank() ? "player_ranks" : sanitized;
    }

    private String sanitizeFileName(String fileName) {
        String sanitized = fileName == null ? "player-ranks.db" : fileName.replaceAll("[\\\\/]+", "");
        return sanitized.isBlank() ? "player-ranks.db" : sanitized;
    }
}

package me.herex.karmsmp.shards;

import me.herex.karmsmp.KaramSMP;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ShardManager {

    private static final DecimalFormat WHOLE = new DecimalFormat("0");
    private static final DecimalFormat ONE_DECIMAL = new DecimalFormat("0.#");

    private final KaramSMP plugin;
    private final Map<UUID, Double> shardCache = new ConcurrentHashMap<>();
    private Connection connection;
    private File yamlFile;
    private YamlConfiguration yaml;
    private String storageType;
    private String tableName;

    public ShardManager(KaramSMP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        close();
        shardCache.clear();
        storageType = resolveStorageType();
        try {
            switch (storageType) {
                case "mysql" -> connectMySQL();
                case "yaml" -> connectYaml();
                default -> connectSQLite();
            }
            plugin.getLogger().info("Shard storage connected using " + getStorageName() + ".");
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not connect shard storage using " + storageType + ": " + exception.getMessage());
            plugin.getLogger().warning("Falling back to YAML shard storage.");
            storageType = "yaml";
            try {
                connectYaml();
            } catch (IOException fallbackException) {
                throw new IllegalStateException("Could not connect shard storage", fallbackException);
            }
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not close shard database connection: " + exception.getMessage());
            }
            connection = null;
        }
        saveYaml();
        yaml = null;
        yamlFile = null;
    }

    public double getShards(OfflinePlayer player) {
        return getShards(player.getUniqueId(), player.getName() == null ? player.getUniqueId().toString() : player.getName());
    }

    public double getShards(UUID uuid, String username) {
        ensureLoaded();
        Double cached = shardCache.get(uuid);
        if (cached != null) {
            return cached;
        }
        double loaded = switch (storageType) {
            case "mysql", "sqlite" -> getSqlShards(uuid, username);
            case "yaml" -> getYamlShards(uuid, username);
            default -> 0.0D;
        };
        shardCache.put(uuid, loaded);
        return loaded;
    }

    public void setShards(OfflinePlayer player, double shards) {
        setShards(player.getUniqueId(), player.getName() == null ? player.getUniqueId().toString() : player.getName(), shards);
    }

    public void setShards(UUID uuid, String username, double shards) {
        ensureLoaded();
        double safe = Math.max(0.0D, shards);
        shardCache.put(uuid, safe);
        switch (storageType) {
            case "mysql", "sqlite" -> setSqlShards(uuid, username, safe);
            case "yaml" -> setYamlShards(uuid, username, safe);
            default -> {
            }
        }
    }

    public double addShards(UUID uuid, String username, double amount) {
        if (amount <= 0.0D) {
            return getShards(uuid, username);
        }
        double max = plugin.getConfig().getDouble("shards.max-shards", 1000000000000000.0D);
        double newValue = Math.min(max, getShards(uuid, username) + amount);
        setShards(uuid, username, newValue);
        return newValue;
    }

    public String format(double amount) {
        double value = Math.max(0.0D, amount);
        String[] suffixes = {"", "K", "M", "B", "T", "Q"};
        int suffixIndex = 0;
        while (value >= 1000.0D && suffixIndex < suffixes.length - 1) {
            value /= 1000.0D;
            suffixIndex++;
        }
        String number = value >= 100.0D || Math.abs(value - Math.round(value)) < 0.05D ? WHOLE.format(value) : ONE_DECIMAL.format(value);
        return number + suffixes[suffixIndex];
    }

    public String formatPlain(double amount) {
        return WHOLE.format(Math.max(0.0D, amount));
    }

    public String getStorageName() {
        return switch (storageType == null ? "" : storageType) {
            case "mysql" -> "MySQL";
            case "yaml" -> "YAML";
            default -> "SQLite";
        };
    }

    private String resolveStorageType() {
        String type = plugin.getConfig().getString("shards.storage.type", "same-as-database");
        if (type == null || type.equalsIgnoreCase("same-as-database")) {
            type = plugin.getConfig().getString("database.type", "sqlite");
        }
        String normalized = type.toLowerCase(Locale.ROOT);
        if (normalized.equals("mysql")) {
            return "mysql";
        }
        if (normalized.equals("yaml") || normalized.equals("yml") || normalized.equals("file")) {
            return "yaml";
        }
        return "sqlite";
    }

    private void connectSQLite() throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        String fileName = plugin.getConfig().getString("shards.storage.sqlite-file", "");
        if (fileName == null || fileName.isBlank()) {
            fileName = plugin.getConfig().getString("database.sqlite.file", "player-ranks.db");
        }
        tableName = sanitizeTableName(plugin.getConfig().getString("shards.storage.sqlite-table", "player_shards"), "player_shards");
        File file = new File(plugin.getDataFolder(), fileName == null || fileName.isBlank() ? "player-ranks.db" : fileName);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        createSqlTable(false);
    }

    private void connectMySQL() throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database", "karamsmp");
        String username = plugin.getConfig().getString("database.mysql.username", "root");
        String password = plugin.getConfig().getString("database.mysql.password", "");
        boolean ssl = plugin.getConfig().getBoolean("database.mysql.use-ssl", false);
        String explicit = plugin.getConfig().getString("shards.storage.mysql-table", "");
        tableName = explicit != null && !explicit.isBlank()
                ? sanitizeTableName(explicit, "karamsmp_player_shards")
                : sanitizeTableName(plugin.getConfig().getString("database.mysql.table-prefix", "karamsmp_") + "player_shards", "karamsmp_player_shards");
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + ssl
                + "&allowPublicKeyRetrieval=true"
                + "&connectTimeout=5000"
                + "&socketTimeout=10000"
                + "&characterEncoding=utf8"
                + "&useUnicode=true"
                + "&serverTimezone=UTC";
        connection = DriverManager.getConnection(jdbcUrl, username, password);
        createSqlTable(true);
    }

    private void connectYaml() throws IOException {
        String fileName = plugin.getConfig().getString("shards.storage.yaml-file", "shards.yml");
        yamlFile = new File(plugin.getDataFolder(), fileName == null || fileName.isBlank() ? "shards.yml" : fileName);
        File parent = yamlFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!yamlFile.exists()) {
            yamlFile.createNewFile();
        }
        yaml = YamlConfiguration.loadConfiguration(yamlFile);
    }

    private void createSqlTable(boolean mysql) throws SQLException {
        String sql = mysql
                ? "CREATE TABLE IF NOT EXISTS " + tableName + " (uuid VARCHAR(36) NOT NULL PRIMARY KEY, username VARCHAR(64) NOT NULL, shards DOUBLE NOT NULL DEFAULT 0, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                : "CREATE TABLE IF NOT EXISTS " + tableName + " (uuid TEXT NOT NULL PRIMARY KEY, username TEXT NOT NULL, shards REAL NOT NULL DEFAULT 0, updated_at TEXT DEFAULT CURRENT_TIMESTAMP)";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private double getSqlShards(UUID uuid, String username) {
        ensureSqlConnected();
        try (PreparedStatement statement = connection.prepareStatement("SELECT shards FROM " + tableName + " WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getDouble("shards");
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load player shards: " + exception.getMessage());
        }
        double starting = plugin.getConfig().getDouble("shards.starting-shards", 0.0D);
        setSqlShards(uuid, username, starting);
        return starting;
    }

    private void setSqlShards(UUID uuid, String username, double shards) {
        ensureSqlConnected();
        String safeUsername = username == null || username.isBlank() ? uuid.toString() : username;
        String sql = storageType.equals("mysql")
                ? "INSERT INTO " + tableName + " (uuid, username, shards) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE username = ?, shards = ?, updated_at = CURRENT_TIMESTAMP"
                : "INSERT INTO " + tableName + " (uuid, username, shards, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) ON CONFLICT(uuid) DO UPDATE SET username = excluded.username, shards = excluded.shards, updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, safeUsername);
            statement.setDouble(3, shards);
            if (storageType.equals("mysql")) {
                statement.setString(4, safeUsername);
                statement.setDouble(5, shards);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not save player shards: " + exception.getMessage());
        }
    }

    private double getYamlShards(UUID uuid, String username) {
        ensureYamlLoaded();
        String path = "shards." + uuid;
        if (!yaml.contains(path + ".shards")) {
            double starting = plugin.getConfig().getDouble("shards.starting-shards", 0.0D);
            setYamlShards(uuid, username, starting);
            return starting;
        }
        return yaml.getDouble(path + ".shards", 0.0D);
    }

    private void setYamlShards(UUID uuid, String username, double shards) {
        ensureYamlLoaded();
        String path = "shards." + uuid;
        yaml.set(path + ".username", username == null || username.isBlank() ? uuid.toString() : username);
        yaml.set(path + ".shards", shards);
        saveYaml();
    }

    private void saveYaml() {
        if (yaml == null || yamlFile == null) {
            return;
        }
        try {
            yaml.save(yamlFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save shards.yml: " + exception.getMessage());
        }
    }

    private void ensureLoaded() {
        if (storageType == null) {
            load();
        }
    }

    private void ensureSqlConnected() {
        try {
            if (connection == null || connection.isClosed() || (storageType.equals("mysql") && !connection.isValid(2))) {
                if (storageType.equals("mysql")) {
                    connectMySQL();
                } else {
                    connectSQLite();
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not reconnect shard database", exception);
        }
    }

    private void ensureYamlLoaded() {
        if (yaml == null) {
            try {
                connectYaml();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not load shard YAML storage", exception);
            }
        }
    }

    private String sanitizeTableName(String name, String fallback) {
        String sanitized = name == null ? fallback : name.replaceAll("[^A-Za-z0-9_]", "");
        return sanitized.isBlank() ? fallback : sanitized;
    }
}

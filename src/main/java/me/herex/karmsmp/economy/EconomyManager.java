package me.herex.karmsmp.economy;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EconomyManager {

    private static final DecimalFormat WHOLE = new DecimalFormat("0");
    private static final DecimalFormat ONE_DECIMAL = new DecimalFormat("0.#");

    private final KaramSMP plugin;
    private Connection connection;
    private File yamlFile;
    private YamlConfiguration yaml;
    private String storageType;
    private String tableName;
    private final Map<UUID, Double> balanceCache = new ConcurrentHashMap<>();

    public EconomyManager(KaramSMP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        close();
        balanceCache.clear();
        storageType = resolveStorageType();
        try {
            switch (storageType) {
                case "mysql" -> connectMySQL();
                case "yaml" -> connectYaml();
                default -> connectSQLite();
            }
            plugin.getLogger().info("Economy storage connected using " + getStorageName() + ".");
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not connect economy storage using " + storageType + ": " + exception.getMessage());
            plugin.getLogger().warning("Falling back to YAML economy storage.");
            storageType = "yaml";
            try {
                connectYaml();
            } catch (Exception fallbackException) {
                throw new IllegalStateException("Could not connect economy storage", fallbackException);
            }
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not close economy database connection: " + exception.getMessage());
            }
            connection = null;
        }
        saveYaml();
        yaml = null;
        yamlFile = null;
    }

    public double getBalance(OfflinePlayer player) {
        return getBalance(player.getUniqueId(), player.getName() == null ? player.getUniqueId().toString() : player.getName());
    }

    public double getBalance(UUID uuid, String username) {
        ensureLoaded();
        Double cached = balanceCache.get(uuid);
        if (cached != null) {
            return cached;
        }
        double loaded = switch (storageType) {
            case "mysql", "sqlite" -> getSqlBalance(uuid, username);
            case "yaml" -> getYamlBalance(uuid, username);
            default -> 0.0D;
        };
        balanceCache.put(uuid, loaded);
        return loaded;
    }

    public void setBalance(OfflinePlayer player, double balance) {
        setBalance(player.getUniqueId(), player.getName() == null ? player.getUniqueId().toString() : player.getName(), balance);
    }

    public void setBalance(UUID uuid, String username, double balance) {
        ensureLoaded();
        double safeBalance = Math.max(0.0D, balance);
        balanceCache.put(uuid, safeBalance);
        switch (storageType) {
            case "mysql", "sqlite" -> setSqlBalance(uuid, username, safeBalance);
            case "yaml" -> setYamlBalance(uuid, username, safeBalance);
            default -> {
            }
        }
    }

    public boolean withdraw(UUID uuid, String username, double amount) {
        if (amount <= 0) {
            return false;
        }
        double current = getBalance(uuid, username);
        if (current + 0.000001D < amount) {
            return false;
        }
        setBalance(uuid, username, current - amount);
        return true;
    }

    public void deposit(UUID uuid, String username, double amount) {
        if (amount <= 0) {
            return;
        }
        setBalance(uuid, username, getBalance(uuid, username) + amount);
    }

    public double addBalance(UUID uuid, String username, double amount) {
        if (amount <= 0) {
            return getBalance(uuid, username);
        }
        double max = plugin.getConfig().getDouble("economy.max-balance", 1_000_000_000_000_000.0D);
        double newBalance = Math.min(max, getBalance(uuid, username) + amount);
        setBalance(uuid, username, newBalance);
        return newBalance;
    }

    public double removeBalance(UUID uuid, String username, double amount) {
        if (amount <= 0) {
            return getBalance(uuid, username);
        }
        double newBalance = Math.max(0.0D, getBalance(uuid, username) - amount);
        setBalance(uuid, username, newBalance);
        return newBalance;
    }

    public String format(double amount) {
        boolean negative = amount < 0;
        double value = Math.abs(amount);
        String[] suffixes = {"", "K", "M", "B", "T", "Q"};
        int suffixIndex = 0;
        while (value >= 1000.0D && suffixIndex < suffixes.length - 1) {
            value /= 1000.0D;
            suffixIndex++;
        }

        String number = value >= 100.0D || Math.abs(value - Math.round(value)) < 0.05D ? WHOLE.format(value) : ONE_DECIMAL.format(value);
        return (negative ? "-$" : "$") + number + suffixes[suffixIndex];
    }

    public String formatPlain(double amount) {
        String formatted = format(amount);
        if (formatted.startsWith("-$")) {
            return "-" + formatted.substring(2);
        }
        return formatted.startsWith("$") ? formatted.substring(1) : formatted;
    }

    public double parseAmount(String input) throws IllegalArgumentException {
        return parseAmount(input, false);
    }

    public double parseAmount(String input, boolean allowZero) throws IllegalArgumentException {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Amount is empty.");
        }

        String raw = input.trim().replace("$", "").replace(",", "").toLowerCase(Locale.ROOT);
        double multiplier = 1.0D;
        if (raw.endsWith("k")) {
            multiplier = 1_000.0D;
            raw = raw.substring(0, raw.length() - 1);
        } else if (raw.endsWith("m") || raw.endsWith("mil") || raw.endsWith("million")) {
            multiplier = 1_000_000.0D;
            raw = raw.replace("million", "").replace("mil", "").replace("m", "");
        } else if (raw.endsWith("b") || raw.endsWith("bil") || raw.endsWith("billion")) {
            multiplier = 1_000_000_000.0D;
            raw = raw.replace("billion", "").replace("bil", "").replace("b", "");
        } else if (raw.endsWith("t")) {
            multiplier = 1_000_000_000_000.0D;
            raw = raw.substring(0, raw.length() - 1);
        }

        try {
            double value = Double.parseDouble(raw) * multiplier;
            if (!Double.isFinite(value) || value < 0.0D || (!allowZero && value <= 0.0D)) {
                throw new IllegalArgumentException("Amount must be positive.");
            }
            double max = plugin.getConfig().getDouble("economy.max-balance", 1_000_000_000_000_000.0D);
            if (value > max) {
                throw new IllegalArgumentException("Amount is too large.");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid amount.");
        }
    }


    public List<TopBalance> getTopBalances(int limit) {
        ensureLoaded();
        int safeLimit = Math.max(1, Math.min(500, limit));
        return switch (storageType) {
            case "mysql", "sqlite" -> getSqlTopBalances(safeLimit);
            case "yaml" -> getYamlTopBalances(safeLimit);
            default -> List.of();
        };
    }

    private List<TopBalance> getSqlTopBalances(int limit) {
        ensureSqlConnected();
        List<TopBalance> top = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT uuid, username, balance FROM " + tableName + " ORDER BY balance DESC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    try {
                        UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                        String username = resultSet.getString("username");
                        double balance = resultSet.getDouble("balance");
                        top.add(new TopBalance(uuid, username == null || username.isBlank() ? uuid.toString() : username, balance));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load top balances: " + exception.getMessage());
        }
        return top;
    }

    private List<TopBalance> getYamlTopBalances(int limit) {
        ensureYamlLoaded();
        List<TopBalance> top = new ArrayList<>();
        if (yaml.isConfigurationSection("balances")) {
            for (String key : yaml.getConfigurationSection("balances").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String username = yaml.getString("balances." + key + ".username", uuid.toString());
                    double balance = yaml.getDouble("balances." + key + ".balance", 0.0D);
                    top.add(new TopBalance(uuid, username == null || username.isBlank() ? uuid.toString() : username, balance));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        top.addAll(balanceCache.entrySet().stream()
                .map(entry -> new TopBalance(entry.getKey(), entry.getKey().toString(), entry.getValue()))
                .toList());
        return top.stream()
                .collect(java.util.stream.Collectors.toMap(TopBalance::uuid, item -> item, (first, second) -> first.balance() >= second.balance() ? first : second))
                .values()
                .stream()
                .sorted(Comparator.comparingDouble(TopBalance::balance).reversed())
                .limit(limit)
                .toList();
    }

    public record TopBalance(UUID uuid, String username, double balance) {
    }

    public String getStorageName() {
        return switch (storageType == null ? "" : storageType) {
            case "mysql" -> "MySQL";
            case "yaml" -> "YAML";
            default -> "SQLite";
        };
    }

    private String resolveStorageType() {
        String type = plugin.getConfig().getString("economy.storage.type", "same-as-database");
        if (type == null || type.equalsIgnoreCase("same-as-database")) {
            type = plugin.getConfig().getString("database.type", "sqlite");
        }
        String normalized = type.toLowerCase(Locale.ROOT);
        if (normalized.equals("yml") || normalized.equals("file")) {
            return "yaml";
        }
        if (normalized.equals("mysql")) {
            return "mysql";
        }
        return "sqlite";
    }

    private void connectSQLite() throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        String fileName = plugin.getConfig().getString("economy.storage.sqlite-file", "");
        if (fileName == null || fileName.isBlank()) {
            fileName = plugin.getConfig().getString("database.sqlite.file", "player-ranks.db");
        }
        tableName = sanitizeTableName(plugin.getConfig().getString("economy.storage.sqlite-table", "player_balances"), "player_balances");

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
        String explicitTable = plugin.getConfig().getString("economy.storage.mysql-table", "");
        if (explicitTable != null && !explicitTable.isBlank()) {
            tableName = sanitizeTableName(explicitTable, "karamsmp_player_balances");
        } else {
            tableName = sanitizeTableName(plugin.getConfig().getString("database.mysql.table-prefix", "karamsmp_") + "player_balances", "karamsmp_player_balances");
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
        createSqlTable(true);
    }

    private void connectYaml() throws IOException {
        String fileName = plugin.getConfig().getString("economy.storage.yaml-file", "balances.yml");
        yamlFile = new File(plugin.getDataFolder(), fileName == null || fileName.isBlank() ? "balances.yml" : fileName);
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
                ? "CREATE TABLE IF NOT EXISTS " + tableName + " (uuid VARCHAR(36) NOT NULL PRIMARY KEY, username VARCHAR(64) NOT NULL, balance DOUBLE NOT NULL DEFAULT 0, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                : "CREATE TABLE IF NOT EXISTS " + tableName + " (uuid TEXT NOT NULL PRIMARY KEY, username TEXT NOT NULL, balance REAL NOT NULL DEFAULT 0, updated_at TEXT DEFAULT CURRENT_TIMESTAMP)";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            try {
                statement.executeUpdate("CREATE INDEX idx_" + tableName + "_username ON " + tableName + " (username)");
            } catch (SQLException exception) {
                if (!String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT).contains("duplicate")
                        && !String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT).contains("already")) {
                    throw exception;
                }
            }
        }
    }

    private double getSqlBalance(UUID uuid, String username) {
        ensureSqlConnected();
        try (PreparedStatement statement = connection.prepareStatement("SELECT balance FROM " + tableName + " WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getDouble("balance");
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load player balance: " + exception.getMessage());
        }
        double starting = plugin.getConfig().getDouble("economy.starting-balance", 0.0D);
        setSqlBalance(uuid, username, starting);
        return starting;
    }

    private void setSqlBalance(UUID uuid, String username, double balance) {
        ensureSqlConnected();
        String safeUsername = username == null || username.isBlank() ? uuid.toString() : username;
        String sql = storageType.equals("mysql")
                ? "INSERT INTO " + tableName + " (uuid, username, balance) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE username = ?, balance = ?, updated_at = CURRENT_TIMESTAMP"
                : "INSERT INTO " + tableName + " (uuid, username, balance, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) ON CONFLICT(uuid) DO UPDATE SET username = excluded.username, balance = excluded.balance, updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, safeUsername);
            statement.setDouble(3, balance);
            if (storageType.equals("mysql")) {
                statement.setString(4, safeUsername);
                statement.setDouble(5, balance);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not save player balance: " + exception.getMessage());
        }
    }

    private double getYamlBalance(UUID uuid, String username) {
        ensureYamlLoaded();
        String path = "balances." + uuid;
        if (!yaml.contains(path + ".balance")) {
            double starting = plugin.getConfig().getDouble("economy.starting-balance", 0.0D);
            setYamlBalance(uuid, username, starting);
            return starting;
        }
        return yaml.getDouble(path + ".balance", 0.0D);
    }

    private void setYamlBalance(UUID uuid, String username, double balance) {
        ensureYamlLoaded();
        String path = "balances." + uuid;
        yaml.set(path + ".username", username == null || username.isBlank() ? uuid.toString() : username);
        yaml.set(path + ".balance", balance);
        saveYaml();
    }

    private void saveYaml() {
        if (yaml == null || yamlFile == null) {
            return;
        }
        try {
            yaml.save(yamlFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save balances.yml: " + exception.getMessage());
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
                String previousType = storageType;
                if (previousType.equals("mysql")) {
                    connectMySQL();
                } else {
                    connectSQLite();
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not reconnect economy database", exception);
        }
    }

    private void ensureYamlLoaded() {
        if (yaml == null) {
            try {
                connectYaml();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not load economy YAML storage", exception);
            }
        }
    }

    private String sanitizeTableName(String name, String fallback) {
        String sanitized = name == null ? fallback : name.replaceAll("[^A-Za-z0-9_]", "");
        return sanitized.isBlank() ? fallback : sanitized;
    }
}

package me.herex.karmsmp.storage;

import me.herex.karmsmp.KaramSMP;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class StorageManager {

    private final KaramSMP plugin;
    private RankStorage storage;

    public StorageManager(KaramSMP plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        if (storage != null) {
            storage.close();
        }

        storage = createStorage(plugin.getConfig().getString("database.type", "sqlite"));

        try {
            storage.connect();
            plugin.getLogger().info("Rank storage connected using " + storage.getStorageName() + ".");
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not connect to " + storage.getStorageName() + ": " + exception.getMessage());
            plugin.getLogger().warning("Falling back to local YAML rank storage.");
            storage = new YamlRankStorage(plugin);
            try {
                storage.connect();
            } catch (Exception fallbackException) {
                throw new IllegalStateException("Could not connect to any rank storage", fallbackException);
            }
        }
    }

    public void close() {
        if (storage != null) {
            storage.close();
        }
    }

    public Optional<String> getPlayerRank(UUID uuid) {
        ensureStorage();
        return storage.getPlayerRank(uuid);
    }

    public void setPlayerRank(UUID uuid, String username, String rankName) {
        ensureStorage();
        storage.setPlayerRank(uuid, username, rankName);
    }

    public void removePlayerRank(UUID uuid) {
        ensureStorage();
        storage.removePlayerRank(uuid);
    }

    public String getStorageName() {
        ensureStorage();
        return storage.getStorageName();
    }

    private RankStorage createStorage(String type) {
        String value = type == null ? "sqlite" : type.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "mysql" -> new MySQLRankStorage(plugin);
            case "yaml", "yml", "file" -> new YamlRankStorage(plugin);
            case "sqlite", "sqlite3" -> new SQLiteRankStorage(plugin);
            default -> {
                plugin.getLogger().warning("Unknown database.type '" + type + "'. Using SQLite instead.");
                yield new SQLiteRankStorage(plugin);
            }
        };
    }

    private void ensureStorage() {
        if (storage == null) {
            connect();
        }
    }
}

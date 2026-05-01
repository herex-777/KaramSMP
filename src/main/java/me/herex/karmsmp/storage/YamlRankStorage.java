package me.herex.karmsmp.storage;

import me.herex.karmsmp.KaramSMP;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public final class YamlRankStorage implements RankStorage {

    private final KaramSMP plugin;
    private File file;
    private FileConfiguration data;

    public YamlRankStorage(KaramSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public void connect() throws IOException {
        String fileName = plugin.getConfig().getString("database.yaml.file", "player-ranks.yml");
        file = new File(plugin.getDataFolder(), sanitizeFileName(fileName));
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            file.createNewFile();
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public void close() {
        save();
    }

    @Override
    public Optional<String> getPlayerRank(UUID uuid) {
        ensureLoaded();
        return Optional.ofNullable(data.getString("players." + uuid + ".rank"));
    }

    @Override
    public void setPlayerRank(UUID uuid, String username, String rankName) {
        ensureLoaded();
        String path = "players." + uuid;
        data.set(path + ".name", username);
        data.set(path + ".rank", rankName);
        save();
    }

    @Override
    public void removePlayerRank(UUID uuid) {
        ensureLoaded();
        data.set("players." + uuid + ".rank", null);
        save();
    }

    @Override
    public String getStorageName() {
        return "YAML";
    }

    private void ensureLoaded() {
        if (data == null) {
            try {
                connect();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not load player-ranks.yml", exception);
            }
        }
    }

    private void save() {
        if (data == null || file == null) {
            return;
        }

        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save player-ranks.yml: " + exception.getMessage());
        }
    }

    private String sanitizeFileName(String fileName) {
        String sanitized = fileName == null ? "player-ranks.yml" : fileName.replaceAll("[\\\\/]+", "");
        return sanitized.isBlank() ? "player-ranks.yml" : sanitized;
    }
}

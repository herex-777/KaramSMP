package me.herex.karmsmp.spawn;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

public final class SpawnManager {

    private final KaramSMP plugin;
    private File file;
    private YamlConfiguration data;
    private Location spawnLocation;

    public SpawnManager(KaramSMP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), plugin.getConfig().getString("spawn.data-file", "spawn.yml"));
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not create spawn.yml: " + exception.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
        spawnLocation = readLocation(data.getConfigurationSection("spawn"));
    }

    public void save() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), plugin.getConfig().getString("spawn.data-file", "spawn.yml"));
        }
        YamlConfiguration output = new YamlConfiguration();
        if (spawnLocation != null) {
            writeLocation(output.createSection("spawn"), spawnLocation);
        }
        try {
            output.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save spawn.yml: " + exception.getMessage());
        }
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("spawn.enabled", true);
    }

    public boolean hasSpawn() {
        return spawnLocation != null && spawnLocation.getWorld() != null;
    }

    public Location getSpawnLocation() {
        return spawnLocation == null ? null : spawnLocation.clone();
    }

    public void setSpawn(Player player) {
        spawnLocation = player.getLocation().clone();
        save();
        send(player, "spawn.messages.set", "&aSpawn has been set.");
    }

    public void teleportToSpawn(Player player, boolean sendMessage) {
        if (!isEnabled()) {
            if (sendMessage) {
                send(player, "spawn.messages.disabled", "&cThe spawn system is disabled.");
            }
            return;
        }
        if (!hasSpawn()) {
            if (sendMessage) {
                send(player, "spawn.messages.not-set", "&cThe spawn has not been set yet.");
            }
            return;
        }
        player.teleport(spawnLocation);
        if (sendMessage) {
            send(player, "spawn.messages.teleported", "&aTeleported to spawn.");
        }
    }

    public void handleJoin(Player player, boolean hasPlayedBefore) {
        if (!isEnabled() || !hasSpawn()) {
            return;
        }
        boolean firstJoin = !hasPlayedBefore && plugin.getConfig().getBoolean("spawn.teleport-first-join", true);
        boolean everyJoin = plugin.getConfig().getBoolean("spawn.teleport-on-join", false);
        if (!firstJoin && !everyJoin) {
            return;
        }
        long delay = Math.max(1L, plugin.getConfig().getLong("spawn.join-teleport-delay-ticks", 2L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                teleportToSpawn(player, false);
            }
        }, delay);
    }

    private void send(Player player, String path, String fallback) {
        player.sendMessage(plugin.getRankManager().applyPlaceholders(player, plugin.getConfig().getString(path, fallback)));
    }

    private Location readLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return null;
        }
        return new Location(world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"));
    }

    private void writeLocation(ConfigurationSection section, Location location) {
        section.set("world", location.getWorld() == null ? "world" : location.getWorld().getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
    }
}

package me.herex.karmsmp.leaderboards;

import me.herex.karmsmp.KaramSMP;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class BountyManager implements Listener {

    private final KaramSMP plugin;
    private File file;
    private YamlConfiguration yaml;

    public BountyManager(KaramSMP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        String fileName = plugin.getConfig().getString("bounties.data-file", "bounties.yml");
        file = new File(plugin.getDataFolder(), fileName == null || fileName.isBlank() ? "bounties.yml" : fileName);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not create bounties file: " + exception.getMessage());
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        if (yaml == null || file == null) {
            return;
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save bounties file: " + exception.getMessage());
        }
    }

    public double getBounty(UUID uuid) {
        ensureLoaded();
        return yaml.getDouble("bounties." + uuid + ".amount", 0.0D);
    }

    public void addBounty(OfflinePlayer target, double amount) {
        if (target == null || amount <= 0.0D) {
            return;
        }
        ensureLoaded();
        UUID uuid = target.getUniqueId();
        String path = "bounties." + uuid;
        yaml.set(path + ".name", target.getName() == null ? uuid.toString() : target.getName());
        yaml.set(path + ".amount", getBounty(uuid) + amount);
        save();
    }

    public void removeBounty(UUID uuid) {
        ensureLoaded();
        yaml.set("bounties." + uuid, null);
        save();
    }

    public List<BountyEntry> getTopBounties(int limit) {
        ensureLoaded();
        List<BountyEntry> entries = new ArrayList<>();
        if (yaml.isConfigurationSection("bounties")) {
            for (String key : yaml.getConfigurationSection("bounties").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String name = yaml.getString("bounties." + key + ".name", uuid.toString());
                    double amount = yaml.getDouble("bounties." + key + ".amount", 0.0D);
                    if (amount > 0.0D) {
                        entries.add(new BountyEntry(uuid, name, amount));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return entries.stream()
                .sorted(Comparator.comparingDouble(BountyEntry::amount).reversed())
                .limit(Math.max(1, Math.min(500, limit)))
                .toList();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("bounties.enabled", true)) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        double bounty = getBounty(victim.getUniqueId());
        if (bounty <= 0.0D) {
            return;
        }
        removeBounty(victim.getUniqueId());
        if (plugin.getEconomyManager() != null) {
            plugin.getEconomyManager().deposit(killer.getUniqueId(), killer.getName(), bounty);
        }
        String message = plugin.getConfig().getString("bounties.messages.claimed", "&8• &cBOUNTY &8» &e%killer% &7claimed &a%amount% &7from &e%player%&7.")
                .replace("%killer%", killer.getName())
                .replace("%player%", victim.getName())
                .replace("%amount%", plugin.getEconomyManager() == null ? String.valueOf(bounty) : plugin.getEconomyManager().format(bounty));
        Bukkit.broadcastMessage(me.herex.karmsmp.utils.MessageUtil.color(message));
    }

    private void ensureLoaded() {
        if (yaml == null) {
            load();
        }
    }

    public record BountyEntry(UUID uuid, String username, double amount) {
    }
}

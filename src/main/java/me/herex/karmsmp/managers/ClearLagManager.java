package me.herex.karmsmp.managers;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Vehicle;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ClearLagManager {

    private final KaramSMP plugin;
    private int taskId = -1;
    private int secondsRemaining;
    private Set<Integer> warningSeconds = new HashSet<>();

    public ClearLagManager(KaramSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("clear-lag.enabled", true)) {
            return;
        }

        secondsRemaining = Math.max(10, plugin.getConfig().getInt("clear-lag.interval-seconds", 900));
        warningSeconds = loadWarningSeconds();

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 20L, 20L);
    }

    public void reload() {
        start();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("clear-lag.enabled", true)) {
            stop();
            return;
        }

        secondsRemaining--;

        if (warningSeconds.contains(secondsRemaining)) {
            broadcastWarning(secondsRemaining);
        }

        if (secondsRemaining <= 0) {
            int removed = clearLag(false);
            broadcastCleaned(removed, false);
            secondsRemaining = Math.max(10, plugin.getConfig().getInt("clear-lag.interval-seconds", 900));
        }
    }

    public int clearLag(boolean manual) {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            if (isWorldDisabled(world)) {
                continue;
            }

            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player || !shouldRemove(entity)) {
                    continue;
                }
                entity.remove();
                removed++;
            }
        }

        if (manual) {
            secondsRemaining = Math.max(10, plugin.getConfig().getInt("clear-lag.interval-seconds", 900));
        }
        return removed;
    }

    public int getSecondsRemaining() {
        return Math.max(0, secondsRemaining);
    }

    public String getFormattedTimeRemaining() {
        int seconds = getSecondsRemaining();
        int minutes = seconds / 60;
        int remainder = seconds % 60;
        if (minutes > 0 && remainder > 0) {
            return minutes + "m " + remainder + "s";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        return remainder + "s";
    }

    public void broadcastWarning(int seconds) {
        String message = plugin.getConfig().getString("clear-lag.messages.warning", "&7Removing all items on the floor in &b%seconds% &7seconds.");
        Bukkit.broadcastMessage(format(message, seconds, 0));
    }

    public void broadcastCleaned(int removed, boolean manual) {
        String path = manual ? "clear-lag.messages.manual-cleaned" : "clear-lag.messages.cleaned";
        String fallback = manual
                ? "&8• &bᴄʟᴇᴀʀʟᴀɢ &8» &aRemoved &b%removed% &aentity/entities."
                : "&8• &bᴄʟᴇᴀʀʟᴀɢ &8» &aRemoved &b%removed% &aitems/entities from the floor.";
        Bukkit.broadcastMessage(format(plugin.getConfig().getString(path, fallback), 0, removed));
    }

    public String format(String message, int seconds, int removed) {
        String formatted = message == null ? "" : message;
        formatted = formatted
                .replace("%seconds%", String.valueOf(seconds))
                .replace("%time%", seconds <= 0 ? getFormattedTimeRemaining() : seconds + "s")
                .replace("%removed%", String.valueOf(removed))
                .replace("%clearlag_time%", getFormattedTimeRemaining())
                .replace("%karamsmp_clearlag_time%", getFormattedTimeRemaining());
        return MessageUtil.color(formatted);
    }

    private boolean shouldRemove(Entity entity) {
        if (entity instanceof Item item) {
            if (!plugin.getConfig().getBoolean("clear-lag.remove.dropped-items", true)) {
                return false;
            }
            if (shouldKeepItem(item)) {
                return false;
            }
            return true;
        }

        if (entity instanceof ExperienceOrb) {
            return plugin.getConfig().getBoolean("clear-lag.remove.experience-orbs", true);
        }

        if (entity instanceof Projectile) {
            return plugin.getConfig().getBoolean("clear-lag.remove.projectiles", false);
        }

        if (entity instanceof FallingBlock) {
            return plugin.getConfig().getBoolean("clear-lag.remove.falling-blocks", false);
        }

        if (entity instanceof TNTPrimed) {
            return plugin.getConfig().getBoolean("clear-lag.remove.primed-tnt", false);
        }

        if (entity instanceof Vehicle) {
            return plugin.getConfig().getBoolean("clear-lag.remove.vehicles", false);
        }

        return false;
    }

    private boolean shouldKeepItem(Item item) {
        if (plugin.getConfig().getBoolean("clear-lag.remove.exclude-named-items", true)) {
            if (item.getCustomName() != null && !item.getCustomName().isBlank()) {
                return true;
            }
            ItemStack stack = item.getItemStack();
            if (stack != null) {
                ItemMeta meta = stack.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    return true;
                }
            }
        }

        List<String> excluded = plugin.getConfig().getStringList("clear-lag.remove.exclude-materials");
        if (excluded.isEmpty()) {
            return false;
        }

        String materialName = item.getItemStack().getType().name().toUpperCase(Locale.ROOT);
        for (String entry : excluded) {
            if (materialName.equals(entry.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isWorldDisabled(World world) {
        List<String> disabledWorlds = plugin.getConfig().getStringList("clear-lag.disabled-worlds");
        for (String entry : disabledWorlds) {
            if (world.getName().equalsIgnoreCase(entry)) {
                return true;
            }
        }
        return false;
    }

    private Set<Integer> loadWarningSeconds() {
        List<Integer> configured = plugin.getConfig().getIntegerList("clear-lag.warning-seconds");
        if (configured.isEmpty()) {
            configured = List.of(60, 30, 15, 5, 4, 3, 2, 1);
        }

        Set<Integer> values = new HashSet<>();
        for (int value : configured) {
            if (value > 0) {
                values.add(value);
            }
        }
        return values;
    }
}

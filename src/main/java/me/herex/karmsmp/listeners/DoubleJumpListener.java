package me.herex.karmsmp.listeners;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.regions.Region;
import me.herex.karmsmp.regions.RegionManager;
import me.herex.karmsmp.utils.SoundUtil;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DoubleJumpListener implements Listener {

    private final KaramSMP plugin;
    private final RegionManager regionManager;
    private final Set<UUID> managedFlight = new HashSet<>();
    private final Set<UUID> cooldown = new HashSet<>();

    public DoubleJumpListener(KaramSMP plugin, RegionManager regionManager) {
        this.plugin = plugin;
        this.regionManager = regionManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> updateFlight(event.getPlayer()), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isOnGround() || changedBlock(event)) {
            updateFlight(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (isNaturalFlightMode(player.getGameMode())) {
            return;
        }
        if (!managedFlight.contains(player.getUniqueId()) || !isDoubleJumpAllowed(player)) {
            return;
        }

        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);

        if (cooldown.contains(player.getUniqueId())) {
            return;
        }
        cooldown.add(player.getUniqueId());
        long cooldownTicks = Math.max(1L, plugin.getConfig().getLong("double-jump.cooldown-ticks", 10L));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> cooldown.remove(player.getUniqueId()), cooldownTicks);

        double yBoost = plugin.getConfig().getDouble("double-jump.velocity-y", 0.9D);
        double forwardBoost = plugin.getConfig().getDouble("double-jump.velocity-forward", 0.55D);
        Vector direction = player.getLocation().getDirection().clone().setY(0);
        if (direction.lengthSquared() > 0) {
            direction.normalize().multiply(forwardBoost);
        }
        direction.setY(yBoost);
        player.setVelocity(direction);
        playSound(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> updateFlight(event.getPlayer()), 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        managedFlight.remove(event.getPlayer().getUniqueId());
        cooldown.remove(event.getPlayer().getUniqueId());
    }

    public void updateFlight(Player player) {
        if (!plugin.getConfig().getBoolean("double-jump.enabled", true)) {
            disableManagedFlight(player);
            return;
        }
        if (isNaturalFlightMode(player.getGameMode())) {
            managedFlight.remove(player.getUniqueId());
            return;
        }
        if (isDoubleJumpAllowed(player)) {
            managedFlight.add(player.getUniqueId());
            if (player.isOnGround()) {
                player.setAllowFlight(true);
            }
        } else {
            disableManagedFlight(player);
        }
    }

    private void disableManagedFlight(Player player) {
        if (managedFlight.remove(player.getUniqueId()) && !isNaturalFlightMode(player.getGameMode())) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    private boolean isDoubleJumpAllowed(Player player) {
        if (!regionManager.isEnabled()) {
            return isInSpawnDoubleJumpArea(player) || plugin.getConfig().getBoolean("double-jump.allow-outside-regions", false);
        }
        Optional<Region> region = regionManager.getHighestRegionAt(player.getLocation());
        if (region.isPresent()) {
            return region.get().isFlagAllowed("double-jump", plugin.getConfig().getBoolean("regions.defaults.flags.double-jump", false));
        }
        if (isInSpawnDoubleJumpArea(player)) {
            return true;
        }
        return plugin.getConfig().getBoolean("double-jump.allow-outside-regions", false);
    }

    private boolean isInSpawnDoubleJumpArea(Player player) {
        if (!plugin.getConfig().getBoolean("double-jump.spawn.enabled", true) || plugin.getSpawnManager() == null || !plugin.getSpawnManager().hasSpawn()) {
            return false;
        }
        org.bukkit.Location spawn = plugin.getSpawnManager().getSpawnLocation();
        if (spawn == null || spawn.getWorld() == null || !spawn.getWorld().equals(player.getWorld())) {
            return false;
        }
        double radius = Math.max(0.0D, plugin.getConfig().getDouble("double-jump.spawn.radius", 50.0D));
        return player.getLocation().distanceSquared(spawn) <= radius * radius;
    }

    private boolean isNaturalFlightMode(GameMode gameMode) {
        return gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR;
    }

    private boolean changedBlock(PlayerMoveEvent event) {
        return event.getFrom().getWorld() == null || event.getTo() == null || event.getTo().getWorld() == null
                || !event.getFrom().getWorld().equals(event.getTo().getWorld())
                || event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ();
    }

    private void playSound(Player player) {
        String configured = plugin.getConfig().getString("double-jump.sound.name", "BLOCK_PRESSURE_PLATE_CLICK_ON");
        if (configured == null || configured.isBlank() || configured.equalsIgnoreCase("none")) {
            return;
        }
        Sound sound = SoundUtil.fromConfig(configured);
        if (sound == null) {
            plugin.getLogger().warning("Unknown double-jump sound: " + configured);
            return;
        }
        float volume = (float) plugin.getConfig().getDouble("double-jump.sound.volume", 1.0D);
        float pitch = (float) plugin.getConfig().getDouble("double-jump.sound.pitch", 1.4D);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}

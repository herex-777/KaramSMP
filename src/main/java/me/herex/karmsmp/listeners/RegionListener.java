package me.herex.karmsmp.listeners;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.regions.Region;
import me.herex.karmsmp.regions.RegionManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RegionListener implements Listener {

    private final KaramSMP plugin;
    private final RegionManager regionManager;
    private final Map<UUID, Long> messageCooldowns = new HashMap<>();

    public RegionListener(KaramSMP plugin, RegionManager regionManager) {
        this.plugin = plugin;
        this.regionManager = regionManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onWandUse(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!regionManager.isWand(event.getItem()) || event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!canUseRegionTools(player)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            event.setCancelled(true);
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            setPositionOne(player, event.getClickedBlock());
            event.setCancelled(true);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            setPositionTwo(player, event.getClickedBlock());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onWandBlockDamage(BlockDamageEvent event) {
        if (!regionManager.isWand(event.getItemInHand())) {
            return;
        }

        Player player = event.getPlayer();
        if (!canUseRegionTools(player)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            event.setCancelled(true);
            return;
        }

        setPositionOne(player, event.getBlock());
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!regionManager.isActionAllowed(event.getPlayer(), event.getBlock().getLocation(), "block-break")) {
            event.setCancelled(true);
            sendProtected(event.getPlayer(), event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!regionManager.isActionAllowed(event.getPlayer(), event.getBlock().getLocation(), "block-place")) {
            event.setCancelled(true);
            sendProtected(event.getPlayer(), event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (regionManager.isWand(event.getItem()) || event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Location location = block.getLocation();

        if (block.getState() instanceof InventoryHolder && !regionManager.isActionAllowed(player, location, "chest-access")) {
            event.setCancelled(true);
            sendProtected(player, location);
            return;
        }

        if (!regionManager.isActionAllowed(player, location, "interact")) {
            event.setCancelled(true);
            sendProtected(player, location);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = getAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        if (!regionManager.isActionAllowed(attacker, attacker.getLocation(), "pvp") || !regionManager.isActionAllowed(attacker, victim.getLocation(), "pvp")) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "PvP is disabled in this region.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        if (!regionManager.isActionAllowed(player, player.getLocation(), "fall-damage")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || sameBlock(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();
        List<Region> fromRegions = regionManager.getRegionsAt(event.getFrom());
        List<Region> toRegions = regionManager.getRegionsAt(event.getTo());
        Set<String> fromNames = names(fromRegions);
        Set<String> toNames = names(toRegions);

        for (Region region : toRegions) {
            if (!fromNames.contains(region.getName()) && !regionManager.isActionAllowed(player, event.getTo(), "entry")) {
                event.setCancelled(true);
                String message = regionManager.formatMessage("regions.messages.enter-denied", player, region);
                if (!message.isBlank()) {
                    player.sendMessage(message);
                }
                return;
            }
        }

        for (Region region : fromRegions) {
            if (!toNames.contains(region.getName()) && !regionManager.isActionAllowed(player, event.getFrom(), "exit")) {
                event.setCancelled(true);
                String message = regionManager.formatMessage("regions.messages.exit-denied", player, region);
                if (!message.isBlank()) {
                    player.sendMessage(message);
                }
                return;
            }
        }

        boolean changedRegions = !fromNames.equals(toNames);

        if (plugin.getConfig().getBoolean("regions.messages.send-enter-leave", true)) {
            for (Region region : toRegions) {
                if (!fromNames.contains(region.getName())) {
                    String custom = region.getGreeting();
                    String message = custom.isBlank() ? regionManager.formatMessage("regions.messages.enter", player, region) : regionManager.formatRawMessage(custom, player, region);
                    if (!message.isBlank()) {
                        player.sendMessage(message);
                    }
                }
            }

            for (Region region : fromRegions) {
                if (!toNames.contains(region.getName())) {
                    String custom = region.getFarewell();
                    String message = custom.isBlank() ? regionManager.formatMessage("regions.messages.leave", player, region) : regionManager.formatRawMessage(custom, player, region);
                    if (!message.isBlank()) {
                        player.sendMessage(message);
                    }
                }
            }
        }

        if (changedRegions && plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().updatePlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (!regionManager.isActionAllowed(event.getPlayer(), event.getPlayer().getLocation(), "item-drop")) {
            event.setCancelled(true);
            sendProtected(event.getPlayer(), event.getPlayer().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!regionManager.isActionAllowed(player, player.getLocation(), "item-pickup")) {
            event.setCancelled(true);
            sendProtected(player, player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (!regionManager.isLocationFlagAllowed(event.getLocation(), "mob-spawning")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!regionManager.isLocationFlagAllowed(event.getLocation(), "explosions")) {
            event.setCancelled(true);
            return;
        }
        event.blockList().removeIf(block -> !regionManager.isLocationFlagAllowed(block.getLocation(), "explosions"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!regionManager.isLocationFlagAllowed(event.getBlock().getLocation(), "explosions")) {
            event.setCancelled(true);
            return;
        }
        event.blockList().removeIf(block -> !regionManager.isLocationFlagAllowed(block.getLocation(), "explosions"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!regionManager.isLocationFlagAllowed(event.getBlock().getLocation(), "fire-spread")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!regionManager.isLocationFlagAllowed(event.getBlock().getLocation(), "fire-spread")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (!regionManager.isLocationFlagAllowed(event.getBlock().getLocation(), "fire-spread")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        messageCooldowns.remove(event.getPlayer().getUniqueId());
    }

    private void sendProtected(Player player, Location location) {
        long now = System.currentTimeMillis();
        long last = messageCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 1000L) {
            return;
        }
        messageCooldowns.put(player.getUniqueId(), now);

        Optional<Region> region = regionManager.getHighestRegionAt(location);
        String message = regionManager.formatMessage("regions.messages.protected", player, region.orElse(null));
        if (!message.isBlank()) {
            player.sendMessage(message);
        }
    }

    private Player getAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private boolean sameBlock(Location first, Location second) {
        return first.getWorld() != null && second.getWorld() != null
                && first.getWorld().equals(second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private Set<String> names(List<Region> regions) {
        Set<String> names = new HashSet<>();
        for (Region region : regions) {
            names.add(region.getName());
        }
        return names;
    }

    private boolean canUseRegionTools(Player player) {
        return player.hasPermission("karamsmp.regions.wand")
                || player.hasPermission(RegionManager.ADMIN_PERMISSION)
                || player.hasPermission("karamsmp.admin");
    }

    private void setPositionOne(Player player, Block block) {
        regionManager.setPositionOne(player, block.getLocation());
        player.sendMessage(ChatColor.GREEN + "Region position 1 set to " + formatLocation(block.getLocation()) + ".");
    }

    private void setPositionTwo(Player player, Block block) {
        regionManager.setPositionTwo(player, block.getLocation());
        player.sendMessage(ChatColor.GREEN + "Region position 2 set to " + formatLocation(block.getLocation()) + ".");
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }
}

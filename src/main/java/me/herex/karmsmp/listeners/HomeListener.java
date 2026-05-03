package me.herex.karmsmp.listeners;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.homes.HomeGuiHolder;
import me.herex.karmsmp.homes.HomeManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class HomeListener implements Listener {

    private final KaramSMP plugin;
    private final HomeManager homeManager;

    public HomeListener(KaramSMP plugin, HomeManager homeManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!homeManager.isWaitingForName(player)) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> homeManager.handleNameInput(player, message));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof HomeGuiHolder)) {
            return;
        }

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        String action = homeManager.getItemAction(clicked);
        String homeName = homeManager.getItemHomeName(clicked);

        if (action.equalsIgnoreCase("teleport")) {
            player.closeInventory();
            homeManager.startTeleport(player, homeName);
        } else if (action.equalsIgnoreCase("delete")) {
            homeManager.openConfirmDeleteGui(player, homeName);
        } else if (action.equalsIgnoreCase("confirm-delete")) {
            homeManager.confirmDelete(player, homeName);
        } else if (action.equalsIgnoreCase("cancel-delete")) {
            homeManager.cancelDelete(player);
        } else if (action.equalsIgnoreCase("create")) {
            homeManager.startNameInput(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        if (homeManager.hasPendingTeleport(event.getPlayer())) {
            homeManager.checkMovement(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        homeManager.cancelTeleport(event.getPlayer(), false);
    }
}

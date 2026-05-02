package me.herex.karmsmp.listeners;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.managers.PlayerDisplayManager;
import me.herex.karmsmp.managers.RankManager;
import me.herex.karmsmp.managers.TabManager;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerJoinListener implements Listener {

    private final KaramSMP plugin;
    private final RankManager rankManager;
    private final TabManager tabManager;
    private final PlayerDisplayManager playerDisplayManager;

    public PlayerJoinListener(KaramSMP plugin, RankManager rankManager, TabManager tabManager, PlayerDisplayManager playerDisplayManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        this.tabManager = tabManager;
        this.playerDisplayManager = playerDisplayManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        rankManager.loadPlayer(player);
        applyJoinMessage(event);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            playerDisplayManager.updateAllPlayers();
            tabManager.updateAllPlayers();
        }, 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        applyQuitMessage(event);
        rankManager.unloadPlayer(event.getPlayer());
    }

    private void applyJoinMessage(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("join-messages.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        String message;
        if (!player.hasPlayedBefore() && plugin.getConfig().getBoolean("join-messages.first-join-enabled", true)) {
            message = plugin.getConfig().getString("join-messages.first-join-message", "");
        } else {
            message = plugin.getConfig().getString("join-messages.message", "");
        }

        if (message == null || message.isBlank()) {
            event.setJoinMessage(null);
            return;
        }

        event.setJoinMessage(rankManager.applyPlaceholders(player, message));
    }

    private void applyQuitMessage(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("quit-messages.enabled", true)) {
            return;
        }

        String message = plugin.getConfig().getString("quit-messages.message", "");
        if (message == null || message.isBlank()) {
            event.setQuitMessage(null);
            return;
        }

        event.setQuitMessage(rankManager.applyPlaceholders(event.getPlayer(), MessageUtil.color(message)));
    }
}

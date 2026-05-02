package me.herex.karmsmp.listeners;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.managers.PlayerDisplayManager;
import me.herex.karmsmp.managers.RankManager;
import me.herex.karmsmp.managers.TabManager;
import me.herex.karmsmp.scoreboards.KaramScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerJoinListener implements Listener {

    private final KaramSMP plugin;
    private final RankManager rankManager;
    private final TabManager tabManager;
    private final PlayerDisplayManager playerDisplayManager;
    private final KaramScoreboardManager scoreboardManager;

    public PlayerJoinListener(KaramSMP plugin, RankManager rankManager, TabManager tabManager,
                              PlayerDisplayManager playerDisplayManager, KaramScoreboardManager scoreboardManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        this.tabManager = tabManager;
        this.playerDisplayManager = playerDisplayManager;
        this.scoreboardManager = scoreboardManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        rankManager.loadPlayer(player);

        String joinMessage = buildJoinMessage(player);
        event.setJoinMessage(null);
        if (!joinMessage.isBlank()) {
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcastMessage(joinMessage));
        }

        Bukkit.getScheduler().runTask(plugin, () -> updatePlayerSystems(player));
        Bukkit.getScheduler().runTaskLater(plugin, () -> updatePlayerSystems(player), 10L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> updatePlayerSystems(player), 40L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        applyQuitMessage(event);
        scoreboardManager.clearPlayer(event.getPlayer());
        rankManager.unloadPlayer(event.getPlayer());
    }

    private void updatePlayerSystems(Player player) {
        if (!player.isOnline()) {
            return;
        }
        playerDisplayManager.updateAllPlayers();
        tabManager.updateAllPlayers();
        scoreboardManager.updatePlayer(player);
    }

    private String buildJoinMessage(Player player) {
        if (!plugin.getConfig().getBoolean("join-messages.enabled", true)) {
            return "";
        }

        String message;
        if (!player.hasPlayedBefore() && plugin.getConfig().getBoolean("join-messages.first-join-enabled", true)) {
            message = plugin.getConfig().getString("join-messages.first-join-message", "");
        } else {
            message = plugin.getConfig().getString("join-messages.message", "");
        }

        if (message == null || message.isBlank()) {
            return "";
        }

        return rankManager.applyPlaceholders(player, message);
    }

    private void applyQuitMessage(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("quit-messages.enabled", true)) {
            event.setQuitMessage(null);
            return;
        }

        String message = plugin.getConfig().getString("quit-messages.message", "");
        if (message == null || message.isBlank()) {
            event.setQuitMessage(null);
            return;
        }

        event.setQuitMessage(rankManager.applyPlaceholders(event.getPlayer(), message));
    }
}

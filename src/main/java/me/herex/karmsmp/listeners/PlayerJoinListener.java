package me.herex.karmsmp.listeners;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.managers.PlayerDisplayManager;
import me.herex.karmsmp.managers.RankManager;
import me.herex.karmsmp.managers.TabManager;
import org.bukkit.Bukkit;
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
        rankManager.loadPlayer(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            playerDisplayManager.updateAllPlayers();
            tabManager.updateAllPlayers();
            if (plugin.getKaramScoreboardManager() != null) {
                plugin.getKaramScoreboardManager().updateAllPlayers();
            }
        }, 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        rankManager.unloadPlayer(event.getPlayer());
    }
}

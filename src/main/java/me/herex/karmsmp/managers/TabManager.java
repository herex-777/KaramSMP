package me.herex.karmsmp.managers;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public final class TabManager {

    private final KaramSMP plugin;
    private final RankManager rankManager;
    private BukkitTask task;

    public TabManager(KaramSMP plugin, RankManager rankManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
    }

    public void start() {
        stop();

        if (!plugin.getConfig().getBoolean("tab.enabled", true)) {
            return;
        }

        long interval = Math.max(1L, plugin.getConfig().getLong("tab.update-interval-seconds", 5L)) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllPlayers, 0L, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void reload() {
        start();
    }

    public void updateAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    public void updatePlayer(Player player) {
        if (!plugin.getConfig().getBoolean("tab.enabled", true)) {
            return;
        }

        List<String> headerLines = plugin.getConfig().getStringList("tab.header");
        List<String> footerLines = plugin.getConfig().getStringList("tab.footer");

        String header = rankManager.applyPlaceholders(player, MessageUtil.joinLines(headerLines));
        String footer = rankManager.applyPlaceholders(player, MessageUtil.joinLines(footerLines));

        try {
            player.setPlayerListHeaderFooter(header, footer);
        } catch (IllegalArgumentException exception) {
            player.setPlayerListHeaderFooter(MessageUtil.safeLegacySubstring(header, 256), MessageUtil.safeLegacySubstring(footer, 256));
        }
    }
}

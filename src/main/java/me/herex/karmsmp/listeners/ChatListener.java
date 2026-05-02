package me.herex.karmsmp.listeners;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.managers.RankManager;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.ArrayList;
import java.util.List;

public final class ChatListener implements Listener {

    private final KaramSMP plugin;
    private final RankManager rankManager;

    public ChatListener(KaramSMP plugin, RankManager rankManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        String format = plugin.getConfig().getString("chat.format", "%prefix%%player%%suffix%&7: &f%message%");
        String formatted = rankManager.applyPlaceholders(player, format)
                .replace("%message%", MessageUtil.stripZeroWidth(event.getMessage()));

        List<Player> recipients = new ArrayList<>(event.getRecipients());
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player recipient : recipients) {
                if (recipient.isOnline()) {
                    recipient.sendMessage(formatted);
                }
            }
            console.sendMessage(formatted);
        });
    }
}

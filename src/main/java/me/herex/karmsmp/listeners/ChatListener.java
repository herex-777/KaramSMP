package me.herex.karmsmp.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.managers.RankManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class ChatListener implements Listener {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final KaramSMP plugin;
    private final RankManager rankManager;

    public ChatListener(KaramSMP plugin, RankManager rankManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        String format = plugin.getConfig().getString("chat.format", "%prefix%%player%%suffix%&7: &f%message%");
        String formatted = rankManager.applyPlaceholders(player, format);
        String[] parts = formatted.split("%message%", 2);

        Component beforeMessage = LEGACY_SERIALIZER.deserialize(parts[0]);
        Component afterMessage = parts.length > 1 ? LEGACY_SERIALIZER.deserialize(parts[1]) : Component.empty();

        event.renderer((source, sourceDisplayName, message, viewer) -> beforeMessage.append(message).append(afterMessage));
    }
}

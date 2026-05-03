package me.herex.karmsmp.listeners;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.managers.PlayerDisplayManager;
import me.herex.karmsmp.managers.RankManager;
import me.herex.karmsmp.managers.TabManager;
import me.herex.karmsmp.scoreboards.KaramScoreboardManager;
import me.herex.karmsmp.utils.MessageUtil;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Locale;

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
        if (plugin.getEconomyManager() != null) {
            plugin.getEconomyManager().getBalance(player);
        }

        String joinMessage = buildJoinMessage(player);
        event.setJoinMessage(null);
        if (!joinMessage.isBlank()) {
            Bukkit.getScheduler().runTask(plugin, () -> broadcastJoinMessage(player, joinMessage));
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

        String nameColor = plugin.getConfig().getString("join-messages.player-name-color", "&f");
        if (nameColor != null && !nameColor.isBlank()) {
            message = message.replace("%player%", nameColor + "%player%");
        }

        return rankManager.applyPlaceholders(player, message);
    }

    private void broadcastJoinMessage(Player joinedPlayer, String joinMessage) {
        String hoverText = buildHoverText(joinedPlayer);
        String clickCommand = buildClickCommand(joinedPlayer);
        boolean hoverEnabled = plugin.getConfig().getBoolean("join-messages.hover.enabled", false) && !hoverText.isBlank();
        boolean clickEnabled = plugin.getConfig().getBoolean("join-messages.click.enabled", false) && !clickCommand.isBlank();

        if (!hoverEnabled && !clickEnabled) {
            Bukkit.broadcastMessage(joinMessage);
            return;
        }

        try {
            BaseComponent[] components = TextComponent.fromLegacyText(joinMessage);
            HoverEvent hoverEvent = hoverEnabled
                    ? new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(hoverText))
                    : null;
            ClickEvent clickEvent = clickEnabled
                    ? new ClickEvent(resolveClickAction(), clickCommand)
                    : null;

            TextComponent root = new TextComponent("");
            if (hoverEvent != null) {
                root.setHoverEvent(hoverEvent);
            }
            if (clickEvent != null) {
                root.setClickEvent(clickEvent);
            }

            for (BaseComponent component : components) {
                if (hoverEvent != null) {
                    component.setHoverEvent(hoverEvent);
                }
                if (clickEvent != null) {
                    component.setClickEvent(clickEvent);
                }
                root.addExtra(component);
            }

            for (Player online : Bukkit.getOnlinePlayers()) {
                online.spigot().sendMessage(root);
            }
            Bukkit.getConsoleSender().sendMessage(joinMessage);
        } catch (Throwable throwable) {
            Bukkit.broadcastMessage(joinMessage);
        }
    }

    private String buildHoverText(Player player) {
        List<String> lines = plugin.getConfig().getStringList("join-messages.hover.text");
        if (lines.isEmpty()) {
            return "";
        }

        String clickCommand = buildClickCommand(player);
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            String replaced = line.replace("%join_click_command%", clickCommand);
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(rankManager.applyPlaceholders(player, replaced));
        }
        return builder.toString();
    }

    private String buildClickCommand(Player player) {
        String command = plugin.getConfig().getString("join-messages.click.command", "");
        if (command == null || command.isBlank()) {
            return "";
        }
        String replaced = rankManager.applyPlaceholders(player, command);
        replaced = MessageUtil.stripColor(replaced).trim();
        if (!replaced.isBlank() && !replaced.startsWith("/")) {
            replaced = "/" + replaced;
        }
        return replaced;
    }

    private ClickEvent.Action resolveClickAction() {
        String action = plugin.getConfig().getString("join-messages.click.action", "RUN_COMMAND");
        if (action == null) {
            return ClickEvent.Action.RUN_COMMAND;
        }
        return action.toUpperCase(Locale.ROOT).contains("SUGGEST")
                ? ClickEvent.Action.SUGGEST_COMMAND
                : ClickEvent.Action.RUN_COMMAND;
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

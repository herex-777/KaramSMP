package me.herex.karmsmp.listeners;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.commands.DiscordCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Locale;

public final class DiscordCommandListener implements Listener {

    private final KaramSMP plugin;
    private final DiscordCommand discordCommand;

    public DiscordCommandListener(KaramSMP plugin, DiscordCommand discordCommand) {
        this.plugin = plugin;
        this.discordCommand = discordCommand;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || message.length() <= 1) {
            return;
        }

        String label = message.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        List<String> configuredCommands = plugin.getConfig().getStringList("discord.command-aliases");
        if (configuredCommands.isEmpty()) {
            configuredCommands = List.of("discord", "dc");
        }

        for (String configuredCommand : configuredCommands) {
            if (configuredCommand != null && label.equalsIgnoreCase(configuredCommand.replace("/", ""))) {
                event.setCancelled(true);
                discordCommand.sendDiscordMessage(event.getPlayer());
                return;
            }
        }
    }
}

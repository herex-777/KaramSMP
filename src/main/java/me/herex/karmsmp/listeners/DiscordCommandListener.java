package me.herex.karmsmp.listeners;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.commands.DiscordCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DiscordCommandListener implements Listener {

    private final KaramSMP plugin;

    public DiscordCommandListener(KaramSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || message.length() <= 1) {
            return;
        }

        String command = message.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (!getDiscordAliases().contains(command)) {
            return;
        }

        event.setCancelled(true);
        DiscordCommand.sendDiscordMessage(plugin, event.getPlayer());
    }

    private Set<String> getDiscordAliases() {
        Set<String> aliases = new HashSet<>();
        aliases.add("discord");

        List<String> configAliases = plugin.getConfig().getStringList("discord.command-aliases");
        for (String alias : configAliases) {
            if (alias != null && !alias.isBlank()) {
                aliases.add(alias.toLowerCase(Locale.ROOT));
            }
        }

        return aliases;
    }
}

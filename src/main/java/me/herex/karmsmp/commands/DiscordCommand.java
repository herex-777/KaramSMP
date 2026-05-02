package me.herex.karmsmp.commands;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class DiscordCommand implements CommandExecutor {

    private final KaramSMP plugin;

    public DiscordCommand(KaramSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sendDiscordMessage(sender);
        return true;
    }

    public void sendDiscordMessage(CommandSender sender) {
        if (!plugin.getConfig().getBoolean("discord.enabled", true)) {
            sender.sendMessage(MessageUtil.color(plugin.getConfig().getString("discord.disabled-message", "&cThe Discord command is disabled.")));
            return;
        }

        String permission = plugin.getConfig().getString("discord.permission", "");
        if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
            sender.sendMessage(MessageUtil.color(plugin.getConfig().getString("discord.no-permission-message", "&cYou don't have permission to use this command!")));
            return;
        }

        List<String> lines = plugin.getConfig().getStringList("discord.message");
        if (lines.isEmpty()) {
            lines = List.of("&9Discord: &bhttps://discord.gg/yourserver");
        }

        for (String line : lines) {
            sender.sendMessage(applyPlaceholders(sender, line));
        }
    }

    private String applyPlaceholders(CommandSender sender, String text) {
        String invite = plugin.getConfig().getString("discord.invite", "https://discord.gg/yourserver");
        String replaced = text
                .replace("%discord%", invite)
                .replace("%discord_invite%", invite)
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%max_players%", String.valueOf(Bukkit.getMaxPlayers()));

        if (sender instanceof Player player) {
            return plugin.getRankManager().applyPlaceholders(player, replaced);
        }

        return MessageUtil.color(replaced
                .replace("%player%", sender.getName())
                .replace("%displayname%", sender.getName())
                .replace("%rank%", "console")
                .replace("%karamsmp_rank%", "console")
                .replace("%karamsmp_ranks_prefix%", "")
                .replace("%karamsmp_ranks_suffix%", ""));
    }
}

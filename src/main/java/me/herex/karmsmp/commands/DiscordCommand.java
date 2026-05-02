package me.herex.karmsmp.commands;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class DiscordCommand implements CommandExecutor {

    private final KaramSMP plugin;

    public DiscordCommand(KaramSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        sendDiscordMessage(plugin, sender);
        return true;
    }

    public static void sendDiscordMessage(KaramSMP plugin, CommandSender sender) {
        if (!plugin.getConfig().getBoolean("discord.enabled", true)) {
            sender.sendMessage(MessageUtil.color(plugin.getConfig().getString("discord.disabled-message", "&cThe Discord command is disabled.")));
            return;
        }

        String permission = plugin.getConfig().getString("discord.permission", "");
        if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
            sender.sendMessage(MessageUtil.color(plugin.getConfig().getString("discord.no-permission-message", "&cYou don't have permission to use this command!")));
            return;
        }

        String invite = plugin.getConfig().getString("discord.invite", "https://discord.gg/yourserver");
        List<String> lines = plugin.getConfig().getStringList("discord.message");
        if (lines.isEmpty()) {
            lines = List.of("&9Discord: &b%discord_invite%");
        }

        for (String line : lines) {
            String parsed = line.replace("%discord%", invite).replace("%discord_invite%", invite);
            if (sender instanceof Player player && plugin.getRankManager() != null) {
                parsed = plugin.getRankManager().applyPlaceholders(player, parsed);
            }
            sender.sendMessage(MessageUtil.color(parsed));
        }
    }
}

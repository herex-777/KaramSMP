package me.herex.karmsmp.commands;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class InfoCommand implements CommandExecutor {

    private final KaramSMP plugin;
    private final String section;

    public InfoCommand(KaramSMP plugin, String section) {
        this.plugin = plugin;
        this.section = section;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getConfig().getBoolean(section + ".enabled", true)) {
            sender.sendMessage(MessageUtil.color(plugin.getConfig().getString(section + ".disabled-message", "&cThis command is disabled.")));
            return true;
        }
        String permission = plugin.getConfig().getString(section + ".permission", "");
        if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
            sender.sendMessage(MessageUtil.color(plugin.getConfig().getString(section + ".no-permission-message", "&cYou don't have permission to use this command!")));
            return true;
        }
        List<String> lines = plugin.getConfig().getStringList(section + ".message");
        if (lines.isEmpty()) {
            lines = List.of("&bConfigure this command in config.yml at " + section + ".message");
        }
        for (String line : lines) {
            sender.sendMessage(apply(sender, line));
        }
        return true;
    }

    private String apply(CommandSender sender, String line) {
        String text = line
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%max_players%", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("%discord_invite%", plugin.getConfig().getString("discord.invite", ""))
                .replace("%discord%", plugin.getConfig().getString("discord.invite", ""))
                .replace("%store%", plugin.getConfig().getString("store.url", ""))
                .replace("%store_url%", plugin.getConfig().getString("store.url", ""))
                .replace("%guide%", plugin.getConfig().getString("guide.url", ""))
                .replace("%guide_url%", plugin.getConfig().getString("guide.url", ""));
        if (sender instanceof Player player) {
            return plugin.getRankManager().applyPlaceholders(player, text);
        }
        return MessageUtil.color(text.replace("%player%", sender.getName()).replace("%displayname%", sender.getName()));
    }
}

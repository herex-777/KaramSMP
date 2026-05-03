package me.herex.karmsmp.commands;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.managers.ClearLagManager;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClearLagCommand implements CommandExecutor, TabCompleter {

    private final KaramSMP plugin;
    private final ClearLagManager clearLagManager;

    public ClearLagCommand(KaramSMP plugin, ClearLagManager clearLagManager) {
        this.plugin = plugin;
        this.clearLagManager = clearLagManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("time")) {
            sender.sendMessage(format("clear-lag.messages.time-left", "&8• &bᴄʟᴇᴀʀʟᴀɢ &8» &7Next clear in &b%time%&7."));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("now") || sub.equals("clear") || sub.equals("run")) {
            if (!hasAdmin(sender)) {
                sender.sendMessage(format("clear-lag.messages.no-permission", "&cYou don't have permission to use this command!"));
                return true;
            }
            int removed = clearLagManager.clearLag(true);
            clearLagManager.broadcastCleaned(removed, true);
            return true;
        }

        if (sub.equals("reload")) {
            if (!hasAdmin(sender)) {
                sender.sendMessage(format("clear-lag.messages.no-permission", "&cYou don't have permission to use this command!"));
                return true;
            }
            clearLagManager.reload();
            sender.sendMessage(format("clear-lag.messages.reloaded", "&8• &bᴄʟᴇᴀʀʟᴀɢ &8» &aClearLag settings reloaded."));
            return true;
        }

        sender.sendMessage(MessageUtil.color("&cUsage: /" + label + " [time|now|reload]"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        List<String> options = new ArrayList<>();
        options.add("time");
        if (hasAdmin(sender)) {
            options.add("now");
            options.add("reload");
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission("karamsmp.clearlag.admin") || sender.hasPermission("karamsmp.admin");
    }

    private String format(String path, String fallback) {
        return clearLagManager.format(plugin.getConfig().getString(path, fallback), 0, 0);
    }
}

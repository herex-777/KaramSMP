package me.herex.karmsmp.commands;

import me.herex.karmsmp.KaramSMP;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class ReloadCommand implements CommandExecutor {

    private static final String PERMISSION = "karamsmp.reload";
    private final KaramSMP plugin;

    public ReloadCommand(KaramSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        plugin.reloadPlugin();
        sender.sendMessage(ChatColor.GREEN + "KaramSMP reloaded successfully. Config, ranks, TAB, scoreboards, storage, regions, join messages, and Discord command are now updated.");
        return true;
    }
}

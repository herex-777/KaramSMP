package me.herex.karmsmp.commands;

import me.herex.karmsmp.KaramSMP;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class ReloadCommand implements CommandExecutor {

    private static final String RELOAD_PERMISSION = "karamsmp.reload";

    private final KaramSMP plugin;

    public ReloadCommand(KaramSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player && !player.isOp() && !player.hasPermission(RELOAD_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        plugin.reloadPlugin();
        sender.sendMessage(ChatColor.GREEN + "KaramSMP reloaded successfully.");
        sender.sendMessage(ChatColor.GRAY + "Storage: " + ChatColor.AQUA + plugin.getStorageManager().getStorageName());
        return true;
    }
}

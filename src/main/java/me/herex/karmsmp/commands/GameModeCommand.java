package me.herex.karmsmp.commands;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class GameModeCommand implements CommandExecutor {

    private static final String GMSP_PERMISSION = "karamsmp.helper.gmsp";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        String commandName = command.getName().toLowerCase();

        if (commandName.equals("gmsp")) {
            if (!player.hasPermission(GMSP_PERMISSION)) {
                player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SURVIVAL);
                player.sendMessage(ChatColor.GREEN + "You are now back in survival mode.");
            } else {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(ChatColor.GREEN + "You are now in spectator mode.");
            }
            return true;
        }

        return false;
    }
}

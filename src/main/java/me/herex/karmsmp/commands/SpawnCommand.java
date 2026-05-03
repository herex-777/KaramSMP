package me.herex.karmsmp.commands;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.spawn.SpawnManager;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class SpawnCommand implements CommandExecutor {

    private final KaramSMP plugin;
    private final SpawnManager spawnManager;

    public SpawnCommand(KaramSMP plugin, SpawnManager spawnManager) {
        this.plugin = plugin;
        this.spawnManager = spawnManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(plugin.getConfig().getString("spawn.messages.only-players", "&cOnly players can use this command.")));
            return true;
        }

        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("setspawn")) {
            if (!player.hasPermission("karamsmp.spawn.set") && !player.hasPermission("karamsmp.admin")) {
                player.sendMessage(MessageUtil.color(plugin.getConfig().getString("spawn.messages.no-permission", "&cYou don't have permission to use this command!")));
                return true;
            }
            spawnManager.setSpawn(player);
            return true;
        }

        spawnManager.teleportToSpawn(player, true);
        return true;
    }
}

package me.herex.karmsmp.commands;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.homes.Home;
import me.herex.karmsmp.homes.HomeManager;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HomeCommand implements CommandExecutor, TabCompleter {

    private final KaramSMP plugin;
    private final HomeManager homeManager;

    public HomeCommand(KaramSMP plugin, HomeManager homeManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(plugin.getConfig().getString("homes.messages.only-players", "&cOnly players can use this command.")));
            return true;
        }

        if (!homeManager.isEnabled()) {
            player.sendMessage(MessageUtil.color(plugin.getConfig().getString("homes.messages.disabled", "&cHomes are disabled.")));
            return true;
        }

        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("sethome")) {
            if (args.length < 1) {
                player.sendMessage(MessageUtil.color("&cUsage: /sethome <name>"));
                return true;
            }
            homeManager.setHome(player, args[0]);
            homeManager.openGui(player);
            return true;
        }

        if (name.equals("delhome")) {
            if (args.length < 1) {
                player.sendMessage(MessageUtil.color("&cUsage: /delhome <name>"));
                return true;
            }
            homeManager.openConfirmDeleteGui(player, args[0]);
            return true;
        }

        if (args.length >= 1) {
            homeManager.startTeleport(player, args[0]);
            return true;
        }

        homeManager.openGui(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> output = new ArrayList<>();
        for (Home home : homeManager.getHomes(player)) {
            if (home.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                output.add(home.getName());
            }
        }
        return output;
    }
}

package me.herex.karmsmp.commands;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.economy.EconomyManager;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class BalanceCommand implements CommandExecutor, TabCompleter {

    private final KaramSMP plugin;
    private final EconomyManager economyManager;

    public BalanceCommand(KaramSMP plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!plugin.getConfig().getBoolean("economy.enabled", true)) {
            sender.sendMessage(color("economy.messages.disabled", "&cThe balance system is disabled."));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(color("economy.messages.only-players", "&cOnly players can use this command."));
                return true;
            }
            double balance = economyManager.getBalance(player);
            sender.sendMessage(format(player, "economy.messages.balance", "&8• &aʙᴀʟᴀɴᴄᴇ &8» &fYour balance is &a%balance%&f.", player.getName(), balance, 0.0D));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("add") || sub.equals("remove") || sub.equals("set")) {
            return handleAdminBalance(sender, sub, args);
        }

        if (!sender.hasPermission("karamsmp.balance.others") && !sender.hasPermission("karamsmp.admin")) {
            sender.sendMessage(color("economy.messages.no-permission", "&cYou don't have permission to use this command!"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String targetName = target.getName() == null ? args[0] : target.getName();
        double balance = economyManager.getBalance(target.getUniqueId(), targetName);
        sender.sendMessage(format(sender instanceof Player player ? player : null, "economy.messages.balance-other", "&8• &aʙᴀʟᴀɴᴄᴇ &8» &e%player% &fhas &a%balance%&f.", targetName, balance, 0.0D));
        return true;
    }

    private boolean handleAdminBalance(CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission("karamsmp.balance.admin") && !sender.hasPermission("karamsmp.admin")) {
            sender.sendMessage(color("economy.messages.no-permission", "&cYou don't have permission to use this command!"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(color("economy.messages.admin-usage", "&cUsage: /blance <add|remove|set> <player> <amount>"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String targetName = target.getName() == null ? args[1] : target.getName();

        double amount;
        try {
            amount = economyManager.parseAmount(args[2], sub.equals("set"));
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(color("economy.messages.invalid-amount", "&cPlease enter a valid amount. Examples: 100, 1K, 2.5M."));
            return true;
        }

        double newBalance;
        String path;
        String fallback;
        switch (sub) {
            case "add" -> {
                newBalance = economyManager.addBalance(target.getUniqueId(), targetName, amount);
                path = "economy.messages.admin-add";
                fallback = "&8• &aʙᴀʟᴀɴᴄᴇ &8» &aAdded %amount% to &e%player%&a. New balance: &f%balance%&a.";
            }
            case "remove" -> {
                newBalance = economyManager.removeBalance(target.getUniqueId(), targetName, amount);
                path = "economy.messages.admin-remove";
                fallback = "&8• &aʙᴀʟᴀɴᴄᴇ &8» &cRemoved %amount% from &e%player%&c. New balance: &f%balance%&c.";
            }
            default -> {
                economyManager.setBalance(target.getUniqueId(), targetName, amount);
                newBalance = amount;
                path = "economy.messages.admin-set";
                fallback = "&8• &aʙᴀʟᴀɴᴄᴇ &8» &eSet %player%'s balance to &a%balance%&e.";
            }
        }

        sender.sendMessage(format(sender instanceof Player player ? player : null, path, fallback, targetName, newBalance, amount));
        if (target instanceof Player online && online.isOnline()) {
            plugin.getScoreboardManager().updatePlayer(online);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        boolean canAdmin = sender.hasPermission("karamsmp.balance.admin") || sender.hasPermission("karamsmp.admin");
        boolean canOthers = sender.hasPermission("karamsmp.balance.others") || sender.hasPermission("karamsmp.admin");

        if (args.length == 1) {
            List<String> results = new ArrayList<>();
            if (canAdmin) {
                results.add("add");
                results.add("remove");
                results.add("set");
            }
            if (canOthers) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    results.add(player.getName());
                }
            }
            return filter(results, args[0]);
        }

        if (args.length == 2 && canAdmin && isAdminSub(args[0])) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[1]);
        }

        if (args.length == 3 && canAdmin && isAdminSub(args[0])) {
            return filter(List.of("0", "100", "1K", "10K", "100K", "1M"), args[2]);
        }

        return Collections.emptyList();
    }

    private boolean isAdminSub(String input) {
        String sub = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return sub.equals("add") || sub.equals("remove") || sub.equals("set");
    }

    private List<String> filter(List<String> values, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return values;
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    private String format(Player player, String path, String fallback, String targetName, double balance, double amount) {
        String message = plugin.getConfig().getString(path, fallback)
                .replace("%player%", targetName == null ? "Unknown" : targetName)
                .replace("%balance%", economyManager.format(balance))
                .replace("%balance_plain%", economyManager.formatPlain(balance))
                .replace("%amount%", economyManager.format(amount))
                .replace("%amount_plain%", economyManager.formatPlain(amount));
        return player == null ? MessageUtil.color(message) : plugin.getRankManager().applyPlaceholders(player, message);
    }

    private String color(String path, String fallback) {
        return MessageUtil.color(plugin.getConfig().getString(path, fallback));
    }
}

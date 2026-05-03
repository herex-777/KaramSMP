package me.herex.karmsmp.commands;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.economy.EconomyManager;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
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

public final class PayCommand implements CommandExecutor, TabCompleter {

    private final KaramSMP plugin;
    private final EconomyManager economyManager;

    public PayCommand(KaramSMP plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("economy.messages.only-players", "&cOnly players can use this command."));
            return true;
        }
        if (!plugin.getConfig().getBoolean("economy.enabled", true)) {
            player.sendMessage(color("economy.messages.disabled", "&cThe balance system is disabled."));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(color("economy.messages.pay-usage", "&cUsage: /pay <player> <amount>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(color("economy.messages.player-not-found", "&cThat player is not online."));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId()) && !plugin.getConfig().getBoolean("economy.allow-pay-self", false)) {
            player.sendMessage(color("economy.messages.cannot-pay-self", "&cYou can't pay yourself."));
            return true;
        }

        double amount;
        try {
            amount = economyManager.parseAmount(args[1]);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(color("economy.messages.invalid-amount", "&cPlease enter a valid amount."));
            return true;
        }

        double minimum = plugin.getConfig().getDouble("economy.minimum-pay-amount", 1.0D);
        if (amount < minimum) {
            player.sendMessage(color("economy.messages.minimum-pay", "&cThe minimum payment is &e%amount%&c.").replace("%amount%", economyManager.format(minimum)));
            return true;
        }

        if (!economyManager.withdraw(player.getUniqueId(), player.getName(), amount)) {
            player.sendMessage(format(player, "economy.messages.not-enough-money", "&cYou don't have enough money. Your balance is &e%balance%&c.", target.getName(), amount));
            return true;
        }

        economyManager.deposit(target.getUniqueId(), target.getName(), amount);
        player.sendMessage(format(player, "economy.messages.pay-sent", "&8• &aᴘᴀʏ &8» &fYou sent &a%amount% &fto &e%player%&f.", target.getName(), amount));
        target.sendMessage(format(target, "economy.messages.pay-received", "&8• &aᴘᴀʏ &8» &fYou received &a%amount% &ffrom &e%player%&f.", player.getName(), amount));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }
        if (args.length == 2) {
            return List.of("100", "1K", "10K", "100K");
        }
        return Collections.emptyList();
    }

    private String format(Player viewer, String path, String fallback, String otherPlayer, double amount) {
        double balance = economyManager.getBalance(viewer);
        String message = plugin.getConfig().getString(path, fallback)
                .replace("%player%", otherPlayer)
                .replace("%amount%", economyManager.format(amount))
                .replace("%amount_plain%", economyManager.formatPlain(amount))
                .replace("%balance%", economyManager.format(balance))
                .replace("%balance_plain%", economyManager.formatPlain(balance));
        return plugin.getRankManager().applyPlaceholders(viewer, message);
    }

    private String color(String path, String fallback) {
        return MessageUtil.color(plugin.getConfig().getString(path, fallback));
    }
}

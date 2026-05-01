package me.herex.karmsmp.commands;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.managers.Rank;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class RankCommand implements TabExecutor {

    private static final String RANKS_PERMISSION = "karamsmp.admin.ranks";

    private final KaramSMP plugin;

    public RankCommand(KaramSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player && !player.isOp() && !player.hasPermission(RANKS_PERMISSION)) {
            sendNoPermission(sender);
            return true;
        }

        if (args.length == 0) {
            sendRankHelp(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "list" -> sendRankList(sender);
            case "info" -> sendRankInfo(sender, label, args);
            case "set" -> setPlayerRank(sender, label, args);
            case "clear" -> clearPlayerRank(sender, label, args);
            case "create", "add" -> createRank(sender, label, args);
            case "delete", "remove" -> deleteRank(sender, label, args);
            case "setprefix" -> setRankPrefix(sender, label, args);
            case "setsuffix" -> setRankSuffix(sender, label, args);
            case "addperm", "addpermission" -> addRankPermission(sender, label, args);
            case "delperm", "removeperm", "removepermission" -> removeRankPermission(sender, label, args);
            default -> sendRankHelp(sender, label);
        }
        return true;
    }

    private void sendRankHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "KaramSMP Rank Commands");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " list");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " info <rank>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " set <player> <rank>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " clear <player>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " create <rank> <permission> <priority>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " remove <rank>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " setprefix <rank> <prefix>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " setsuffix <rank> <suffix>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " addperm <rank> <permission>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " delperm <rank> <permission>");
    }

    private void sendRankList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Ranks:");
        for (String rankName : plugin.getRankManager().getRankNames()) {
            Optional<Rank> rank = plugin.getRankManager().getRankByName(rankName);
            rank.ifPresent(value -> sender.sendMessage(ChatColor.GRAY + "- " + MessageUtil.color(value.getPrefix()) + ChatColor.YELLOW + value.getName()
                    + ChatColor.GRAY + " priority=" + value.getPriority()
                    + " permissions=" + value.getPermissions().size()));
        }
    }

    private void sendRankInfo(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " info <rank>");
            return;
        }

        Optional<Rank> rank = plugin.getRankManager().getRankByName(args[1]);
        if (rank.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "That rank does not exist.");
            return;
        }

        Rank value = rank.get();
        sender.sendMessage(ChatColor.GOLD + "Rank: " + ChatColor.YELLOW + value.getName());
        sender.sendMessage(ChatColor.GRAY + "Permission node: " + ChatColor.AQUA + (value.getPermission() == null || value.getPermission().isBlank() ? "default" : value.getPermission()));
        sender.sendMessage(ChatColor.GRAY + "Prefix: " + MessageUtil.color(value.getPrefix()));
        sender.sendMessage(ChatColor.GRAY + "Suffix: " + MessageUtil.color(value.getSuffix()));
        sender.sendMessage(ChatColor.GRAY + "Priority: " + ChatColor.AQUA + value.getPriority());
        sender.sendMessage(ChatColor.GRAY + "Granted permissions: " + ChatColor.AQUA + (value.getPermissions().isEmpty() ? "none" : String.join(", ", value.getPermissions())));
    }

    private void setPlayerRank(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " set <player> <rank>");
            return;
        }

        OfflinePlayer target = getKnownOfflinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "That player was not found. They need to join the server once first.");
            return;
        }

        Optional<Rank> rank = plugin.getRankManager().getRankByName(args[2]);
        if (rank.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "That rank does not exist.");
            return;
        }

        plugin.getRankManager().setOfflinePlayerRank(target, rank.get());
        sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s rank to " + rank.get().getName() + ".");
    }

    private void clearPlayerRank(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " clear <player>");
            return;
        }

        OfflinePlayer target = getKnownOfflinePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "That player was not found. They need to join the server once first.");
            return;
        }

        plugin.getRankManager().clearOfflinePlayerRank(target);
        sender.sendMessage(ChatColor.GREEN + "Cleared " + target.getName() + "'s saved rank.");
    }

    private void createRank(CommandSender sender, String label, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " create <rank> <permission> <priority>");
            return;
        }

        int priority;
        try {
            priority = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ChatColor.RED + "Priority must be a number.");
            return;
        }

        boolean created = plugin.getRankManager().createRank(args[1], args[2], priority);
        if (!created) {
            sender.sendMessage(ChatColor.RED + "That rank already exists or the name is invalid.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Created rank " + args[1] + ". Use setprefix, setsuffix, and addperm to finish it.");
    }

    private void deleteRank(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " remove <rank>");
            return;
        }

        boolean deleted = plugin.getRankManager().deleteRank(args[1]);
        if (!deleted) {
            sender.sendMessage(ChatColor.RED + "That rank does not exist.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Removed rank " + args[1] + ".");
    }

    private void setRankPrefix(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " setprefix <rank> <prefix>");
            return;
        }

        String prefix = joinArguments(args, 2);
        if (!plugin.getRankManager().setRankPrefix(args[1], prefix)) {
            sender.sendMessage(ChatColor.RED + "That rank does not exist.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Updated prefix for rank " + args[1] + ".");
    }

    private void setRankSuffix(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " setsuffix <rank> <suffix>");
            return;
        }

        String suffix = joinArguments(args, 2);
        if (!plugin.getRankManager().setRankSuffix(args[1], suffix)) {
            sender.sendMessage(ChatColor.RED + "That rank does not exist.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Updated suffix for rank " + args[1] + ".");
    }

    private void addRankPermission(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " addperm <rank> <permission>");
            return;
        }

        if (!plugin.getRankManager().addPermissionToRank(args[1], args[2])) {
            sender.sendMessage(ChatColor.RED + "That rank does not exist or the permission is invalid.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Added permission " + args[2] + " to rank " + args[1] + ".");
    }

    private void removeRankPermission(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " delperm <rank> <permission>");
            return;
        }

        if (!plugin.getRankManager().removePermissionFromRank(args[1], args[2])) {
            sender.sendMessage(ChatColor.RED + "That rank does not exist or did not have that permission.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Removed permission " + args[2] + " from rank " + args[1] + ".");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (sender instanceof Player player && !player.isOp() && !player.hasPermission(RANKS_PERMISSION)) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            return partial(args[0], Arrays.asList("list", "info", "set", "clear", "create", "remove", "setprefix", "setsuffix", "addperm", "delperm"));
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "info", "remove", "delete", "setprefix", "setsuffix", "addperm", "delperm", "removeperm" -> completions.addAll(plugin.getRankManager().getRankNames());
                case "set", "clear" -> Bukkit.getOnlinePlayers().forEach(player -> completions.add(player.getName()));
            }
            return partial(args[1], completions);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return partial(args[2], plugin.getRankManager().getRankNames());
        }

        return Collections.emptyList();
    }

    private OfflinePlayer getKnownOfflinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (!offline.hasPlayedBefore()) {
            return null;
        }
        return offline;
    }

    private String joinArguments(String[] args, int startIndex) {
        return String.join(" ", Arrays.copyOfRange(args, startIndex, args.length));
    }

    private List<String> partial(String current, List<String> options) {
        List<String> completions = new ArrayList<>();
        StringUtil.copyPartialMatches(current, options, completions);
        Collections.sort(completions);
        return completions;
    }

    private void sendNoPermission(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
    }
}

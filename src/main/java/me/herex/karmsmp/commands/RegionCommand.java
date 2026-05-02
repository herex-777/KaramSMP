package me.herex.karmsmp.commands;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.regions.Region;
import me.herex.karmsmp.regions.RegionManager;
import me.herex.karmsmp.regions.Selection;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class RegionCommand implements TabExecutor {

    private final KaramSMP plugin;
    private final RegionManager regionManager;

    public RegionCommand(KaramSMP plugin, RegionManager regionManager) {
        this.plugin = plugin;
        this.regionManager = regionManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "wand" -> giveWand(sender);
            case "pos1", "setpos1" -> setPosition(sender, true);
            case "pos2", "setpos2" -> setPosition(sender, false);
            case "create", "define" -> createRegion(sender, args);
            case "delete", "remove" -> deleteRegion(sender, args);
            case "list" -> listRegions(sender, args);
            case "info" -> infoRegion(sender, args);
            case "here" -> here(sender);
            case "flags" -> listFlags(sender, args);
            case "flag" -> setFlag(sender, args);
            case "priority", "setpriority" -> setPriority(sender, args);
            case "owner", "owners" -> changeOwner(sender, args);
            case "member", "members" -> changeMember(sender, args);
            case "redefine", "resize" -> redefine(sender, args);
            case "expand" -> expand(sender, args, true);
            case "contract" -> expand(sender, args, false);
            case "message", "setmessage" -> setMessage(sender, args);
            case "rename" -> rename(sender, args);
            case "teleport", "tp" -> teleport(sender, args);
            case "save" -> save(sender);
            case "reload" -> reload(sender);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "KaramSMP Region Commands");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " wand" + ChatColor.GRAY + " - Get the region selection wand.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " pos1|pos2" + ChatColor.GRAY + " - Set a position at your feet.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " create <name>" + ChatColor.GRAY + " - Create a region from your selection.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " delete <name>" + ChatColor.GRAY + " - Delete a region.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " flag <name> <flag> <true|false|reset>" + ChatColor.GRAY + " - Edit protection flags.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " owner|member add/remove <region> <player>" + ChatColor.GRAY + " - Manage access.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " expand|contract <region> <direction> <amount>" + ChatColor.GRAY + " - Resize a region.");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " info|list|here|tp|redefine|message|priority|reload" + ChatColor.GRAY + " - Other tools.");
    }

    private void giveWand(CommandSender sender) {
        if (!has(sender, "karamsmp.regions.wand")) {
            sendNoPermission(sender);
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        player.getInventory().addItem(regionManager.createWand());
        player.sendMessage(ChatColor.GREEN + "You received the KaramSMP region wand. Left click sets pos1, right click sets pos2.");
    }

    private void setPosition(CommandSender sender, boolean first) {
        if (!has(sender, "karamsmp.regions.wand")) {
            sendNoPermission(sender);
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Location location = player.getLocation().getBlock().getLocation();
        if (first) {
            regionManager.setPositionOne(player, location);
            player.sendMessage(ChatColor.GREEN + "Position 1 set to " + formatLocation(location) + ".");
        } else {
            regionManager.setPositionTwo(player, location);
            player.sendMessage(ChatColor.GREEN + "Position 2 set to " + formatLocation(location) + ".");
        }
    }

    private void createRegion(CommandSender sender, String[] args) {
        if (!has(sender, "karamsmp.regions.create")) {
            sendNoPermission(sender);
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /region create <name>");
            return;
        }
        Selection selection = regionManager.getSelection(player);
        if (!selection.isComplete()) {
            sender.sendMessage(ChatColor.RED + "You need to select pos1 and pos2 first. Use /region wand.");
            return;
        }
        Optional<Region> region = regionManager.createRegion(player, args[1]);
        if (region.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Could not create region. The name may already exist or be invalid.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Created region " + ChatColor.YELLOW + region.get().getName() + ChatColor.GREEN + ".");
    }

    private void deleteRegion(CommandSender sender, String[] args) {
        if (!has(sender, "karamsmp.regions.delete")) {
            sendNoPermission(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /region delete <name>");
            return;
        }
        if (!regionManager.deleteRegion(args[1])) {
            sender.sendMessage(ChatColor.RED + "That region does not exist.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Deleted region " + args[1] + ".");
    }

    private void listRegions(CommandSender sender, String[] args) {
        if (!has(sender, "karamsmp.regions.list")) {
            sendNoPermission(sender);
            return;
        }
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }

        List<Region> regions = regionManager.getRegions();
        int perPage = 8;
        int maxPage = Math.max(1, (int) Math.ceil(regions.size() / (double) perPage));
        page = Math.min(page, maxPage);

        sender.sendMessage(ChatColor.GOLD + "Regions " + ChatColor.GRAY + "(" + regions.size() + ") page " + page + "/" + maxPage);
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, regions.size());
        for (int i = start; i < end; i++) {
            Region region = regions.get(i);
            sender.sendMessage(ChatColor.YELLOW + region.getName() + ChatColor.GRAY + " world=" + region.getWorldName() + " priority=" + region.getPriority());
        }
    }

    private void infoRegion(CommandSender sender, String[] args) {
        if (!has(sender, "karamsmp.regions.info")) {
            sendNoPermission(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /region info <name>");
            return;
        }
        Optional<Region> optional = regionManager.getRegion(args[1]);
        if (optional.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "That region does not exist.");
            return;
        }
        sendRegionInfo(sender, optional.get());
    }

    private void here(CommandSender sender) {
        if (!has(sender, "karamsmp.regions.info")) {
            sendNoPermission(sender);
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        List<Region> regions = regionManager.getRegionsAt(player.getLocation());
        if (regions.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "You are not inside a KaramSMP region.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "Regions here: " + ChatColor.YELLOW + String.join(", ", regions.stream().map(Region::getName).toList()));
        sendRegionInfo(sender, regions.get(0));
    }

    private void sendRegionInfo(CommandSender sender, Region region) {
        sender.sendMessage(ChatColor.GOLD + "Region: " + ChatColor.YELLOW + region.getName());
        sender.sendMessage(ChatColor.GRAY + "World: " + ChatColor.AQUA + region.getWorldName());
        sender.sendMessage(ChatColor.GRAY + "Min: " + ChatColor.AQUA + region.getMinX() + ", " + region.getMinY() + ", " + region.getMinZ());
        sender.sendMessage(ChatColor.GRAY + "Max: " + ChatColor.AQUA + region.getMaxX() + ", " + region.getMaxY() + ", " + region.getMaxZ());
        sender.sendMessage(ChatColor.GRAY + "Priority: " + ChatColor.AQUA + region.getPriority());
        sender.sendMessage(ChatColor.GRAY + "Owners: " + ChatColor.AQUA + region.getOwners().size() + ChatColor.GRAY + " Members: " + ChatColor.AQUA + region.getMembers().size());
        sender.sendMessage(ChatColor.GRAY + "Flags: " + ChatColor.AQUA + formatFlags(region));
        if (!region.getGreeting().isBlank()) {
            sender.sendMessage(ChatColor.GRAY + "Greeting: " + MessageUtil.color(region.getGreeting()));
        }
        if (!region.getFarewell().isBlank()) {
            sender.sendMessage(ChatColor.GRAY + "Farewell: " + MessageUtil.color(region.getFarewell()));
        }
    }

    private void listFlags(CommandSender sender, String[] args) {
        if (!has(sender, "karamsmp.regions.info")) {
            sendNoPermission(sender);
            return;
        }
        if (args.length < 2) {
            List<String> flags = new ArrayList<>(regionManager.getAvailableFlags());
            flags.sort(String::compareToIgnoreCase);
            sender.sendMessage(ChatColor.GOLD + "Available flags: " + ChatColor.YELLOW + String.join(", ", flags));
            return;
        }
        Optional<Region> optional = regionManager.getRegion(args[1]);
        if (optional.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "That region does not exist.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "Flags for " + ChatColor.YELLOW + optional.get().getName() + ChatColor.GOLD + ":");
        optional.get().getFlags().entrySet().stream()
                .sorted(MapEntryComparator.INSTANCE)
                .forEach(entry -> sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.YELLOW + entry.getKey() + ChatColor.GRAY + " = " + (entry.getValue() ? ChatColor.GREEN + "true" : ChatColor.RED + "false")));
    }

    private void setFlag(CommandSender sender, String[] args) {
        if (!has(sender, "karamsmp.regions.flag")) {
            sendNoPermission(sender);
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /region flag <name> <flag> <true|false|reset>");
            return;
        }
        if (args[3].equalsIgnoreCase("reset")) {
            if (!regionManager.resetFlag(args[1], args[2])) {
                sender.sendMessage(ChatColor.RED + "Unknown region or flag.");
                return;
            }
            sender.sendMessage(ChatColor.GREEN + "Reset flag " + args[2] + " for region " + args[1] + ".");
            return;
        }
        Boolean value = parseBoolean(args[3]);
        if (value == null) {
            sender.sendMessage(ChatColor.RED + "The value must be true, false, allow, deny, yes, or no.");
            return;
        }
        if (!regionManager.setFlag(args[1], args[2], value)) {
            sender.sendMessage(ChatColor.RED + "Unknown region or flag. Use /region flags to see flags.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Set " + args[2] + " to " + value + " for region " + args[1] + ".");
    }

    private void setPriority(CommandSender sender, String[] args) {
        if (!has(sender, "karamsmp.regions.priority")) {
            sendNoPermission(sender);
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /region priority <name> <priority>");
            return;
        }
        int priority;
        try {
            priority = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ChatColor.RED + "Priority must be a number.");
            return;
        }
        if (!regionManager.setPriority(args[1], priority)) {
            sender.sendMessage(ChatColor.RED + "That region does not exist.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Set priority for " + args[1] + " to " + priority + ".");
    }

    private void changeOwner(CommandSender sender, String[] args) {
        if (!has(sender, "karamsmp.regions.owner")) {
            sendNoPermission(sender);
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /region owner <add|remove> <region> <player>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
        boolean success = args[1].equalsIgnoreCase("add")
                ? regionManager.addOwner(args[2], target)
                : args[1].equalsIgnoreCase("remove") ? regionManager.removeOwner(args[2], target) : false;
        if (!success) {
            sender.sendMessage(ChatColor.RED + "Could not update owners. Check the region and action.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Updated owners for " + args[2] + ".");
    }

    private void changeMember(CommandSender sender, String[] args) {
        if (!has(sender, "karamsmp.regions.member")) {
            sendNoPermission(sender);
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /region member <add|remove> <region> <player>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
        boolean success = args[1].equalsIgnoreCase("add")
                ? regionManager.addMember(args[2], target)
                : args[1].equalsIgnoreCase("remove") ? regionManager.removeMember(args[2], target) : false;
        if (!success) {
            sender.sendMessage(ChatColor.RED + "Could not update members. Check the region and action.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Updated members for " + args[2] + ".");
    }

    private void redefine(CommandSender sender, String[] args) {
        if (!has(sender, "karamsmp.regions.redefine")) {
            sendNoPermission(sender);
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /region redefine <name>");
            return;
        }
        if (!regionManager.redefineRegion(player, args[1])) {
            sender.sendMessage(ChatColor.RED + "Could not redefine. Check the region and your selection.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Redefined region " + args[1] + ".");
    }

    private void expand(CommandSender sender, String[] args, boolean expand) {
        if (!has(sender, "karamsmp.regions.resize")) {
            sendNoPermission(sender);
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /region " + (expand ? "expand" : "contract") + " <region> <up|down|north|south|east|west|vertical> <amount>");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ChatColor.RED + "Amount must be a number.");
            return;
        }
        boolean success = expand ? regionManager.expand(args[1], args[2], amount) : regionManager.contract(args[1], args[2], amount);
        if (!success) {
            sender.sendMessage(ChatColor.RED + "Could not resize region. Check region name and direction.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + (expand ? "Expanded" : "Contracted") + " region " + args[1] + ".");
    }

    private void setMessage(CommandSender sender, String[] args) {
        if (!has(sender, "karamsmp.regions.message")) {
            sendNoPermission(sender);
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /region message <region> <greeting|farewell> <message|clear>");
            return;
        }
        String message = args[3].equalsIgnoreCase("clear") ? "" : joinArguments(args, 3);
        if (!regionManager.setMessage(args[1], args[2], message)) {
            sender.sendMessage(ChatColor.RED + "Could not update the region message.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Updated " + args[2] + " message for " + args[1] + ".");
    }

    private void rename(CommandSender sender, String[] args) {
        if (!has(sender, "karamsmp.regions.rename")) {
            sendNoPermission(sender);
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /region rename <old> <new>");
            return;
        }
        if (!regionManager.renameRegion(args[1], args[2])) {
            sender.sendMessage(ChatColor.RED + "Could not rename region. Check names and make sure the new name is unused.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Renamed region " + args[1] + " to " + args[2] + ".");
    }

    private void teleport(CommandSender sender, String[] args) {
        if (!has(sender, "karamsmp.regions.teleport")) {
            sendNoPermission(sender);
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /region tp <name>");
            return;
        }
        Optional<Region> optional = regionManager.getRegion(args[1]);
        if (optional.isEmpty() || optional.get().getTeleportLocation() == null) {
            sender.sendMessage(ChatColor.RED + "That region does not exist or its world is not loaded.");
            return;
        }
        player.teleport(optional.get().getTeleportLocation());
        sender.sendMessage(ChatColor.GREEN + "Teleported to region " + optional.get().getName() + ".");
    }

    private void save(CommandSender sender) {
        if (!has(sender, "karamsmp.regions.save")) {
            sendNoPermission(sender);
            return;
        }
        regionManager.save();
        sender.sendMessage(ChatColor.GREEN + "Saved regions.yml.");
    }

    private void reload(CommandSender sender) {
        if (!has(sender, "karamsmp.regions.reload")) {
            sendNoPermission(sender);
            return;
        }
        regionManager.reload();
        sender.sendMessage(ChatColor.GREEN + "Reloaded regions.yml.");
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return null;
        }
        return player;
    }

    private boolean has(CommandSender sender, String permission) {
        return sender.hasPermission(permission)
                || sender.hasPermission(RegionManager.ADMIN_PERMISSION)
                || sender.hasPermission("karamsmp.admin");
    }

    private void sendNoPermission(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    private String formatFlags(Region region) {
        List<String> flags = region.getFlags().entrySet().stream()
                .sorted(MapEntryComparator.INSTANCE)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
        return String.join(", ", flags);
    }

    private Boolean parseBoolean(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "allow", "yes", "on", "enable", "enabled" -> true;
            case "false", "deny", "no", "off", "disable", "disabled" -> false;
            default -> null;
        };
    }

    private String joinArguments(String[] args, int start) {
        if (args.length <= start) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            options.addAll(List.of("help", "wand", "pos1", "pos2", "create", "delete", "list", "info", "here", "flags", "flag", "owner", "member", "priority", "redefine", "expand", "contract", "message", "rename", "tp", "save", "reload"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "delete", "info", "flags", "priority", "redefine", "expand", "contract", "message", "rename", "tp", "teleport" -> options.addAll(regionNames());
                case "flag" -> options.addAll(regionNames());
                case "owner", "member" -> options.addAll(List.of("add", "remove"));
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "flag" -> options.addAll(flagNames());
                case "owner", "member" -> options.addAll(regionNames());
                case "expand", "contract" -> options.addAll(List.of("up", "down", "north", "south", "east", "west", "vertical"));
                case "message" -> options.addAll(List.of("greeting", "farewell"));
                case "rename" -> options.add("new_name");
            }
        } else if (args.length == 4) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "flag" -> options.addAll(List.of("true", "false", "reset"));
                case "owner", "member" -> Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
                case "expand", "contract" -> options.addAll(List.of("1", "5", "10", "50", "100"));
                case "message" -> options.add("clear");
            }
        }

        StringUtil.copyPartialMatches(args[args.length - 1], options, completions);
        completions.sort(String.CASE_INSENSITIVE_ORDER);
        return completions;
    }

    private List<String> regionNames() {
        return regionManager.getRegions().stream().map(Region::getName).toList();
    }

    private List<String> flagNames() {
        List<String> flags = new ArrayList<>(regionManager.getAvailableFlags());
        flags.sort(String::compareToIgnoreCase);
        return flags;
    }

    private enum MapEntryComparator implements Comparator<java.util.Map.Entry<String, Boolean>> {
        INSTANCE;

        @Override
        public int compare(java.util.Map.Entry<String, Boolean> first, java.util.Map.Entry<String, Boolean> second) {
            return first.getKey().compareToIgnoreCase(second.getKey());
        }
    }
}

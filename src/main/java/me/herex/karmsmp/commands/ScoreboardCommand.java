package me.herex.karmsmp.commands;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.scoreboards.KaramScoreboardDefinition;
import me.herex.karmsmp.scoreboards.KaramScoreboardManager;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ScoreboardCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "karamsmp.scoreboards.admin";
    private final KaramSMP plugin;
    private final KaramScoreboardManager manager;

    public ScoreboardCommand(KaramSMP plugin, KaramScoreboardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION) && !sender.hasPermission("karamsmp.admin")) {
            sender.sendMessage(MessageUtil.color("&cYou don't have permission to use this command!"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                manager.reload();
                sender.sendMessage(MessageUtil.color("&aReloaded all scoreboard files."));
            }
            case "list" -> sendList(sender);
            case "info" -> sendInfo(sender, args);
            case "create" -> {
                if (requireArgs(sender, args, 2)) {
                    message(sender, manager.createScoreboard(args[1]), "&aCreated scoreboard &e" + args[1] + "&a.", "&cThat scoreboard already exists.");
                }
            }
            case "delete", "remove" -> {
                if (requireArgs(sender, args, 2)) {
                    message(sender, manager.deleteScoreboard(args[1]), "&aDeleted scoreboard &e" + args[1] + "&a.", "&cThat scoreboard does not exist.");
                }
            }
            case "enable" -> {
                if (requireArgs(sender, args, 2)) {
                    message(sender, manager.setValue(args[1], "enabled", true), "&aEnabled scoreboard &e" + args[1] + "&a.", "&cThat scoreboard does not exist.");
                }
            }
            case "disable" -> {
                if (requireArgs(sender, args, 2)) {
                    message(sender, manager.setValue(args[1], "enabled", false), "&cDisabled scoreboard &e" + args[1] + "&c.", "&cThat scoreboard does not exist.");
                }
            }
            case "setpermission" -> setPermission(sender, args);
            case "setpriority" -> setPriority(sender, args);
            case "addworld" -> {
                if (requireArgs(sender, args, 3)) {
                    message(sender, manager.addToList(args[1], "worlds", args[2]), "&aAdded world &e" + args[2] + " &ato scoreboard &e" + args[1] + "&a.", "&cThat scoreboard does not exist.");
                }
            }
            case "removeworld" -> {
                if (requireArgs(sender, args, 3)) {
                    message(sender, manager.removeFromList(args[1], "worlds", args[2]), "&aRemoved world &e" + args[2] + " &afrom scoreboard &e" + args[1] + "&a.", "&cWorld or scoreboard was not found.");
                }
            }
            case "addregion" -> {
                if (requireArgs(sender, args, 3)) {
                    message(sender, manager.addToList(args[1], "regions", args[2]), "&aAdded region &e" + args[2] + " &ato scoreboard &e" + args[1] + "&a.", "&cThat scoreboard does not exist.");
                }
            }
            case "removeregion" -> {
                if (requireArgs(sender, args, 3)) {
                    message(sender, manager.removeFromList(args[1], "regions", args[2]), "&aRemoved region &e" + args[2] + " &afrom scoreboard &e" + args[1] + "&a.", "&cRegion or scoreboard was not found.");
                }
            }
            case "settitle" -> setTitle(sender, args);
            case "addline" -> addLine(sender, args);
            case "setline" -> setLine(sender, args);
            case "removeline" -> removeLine(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageUtil.color("&6&lKaramSMP Scoreboards"));
        sender.sendMessage(MessageUtil.color("&e/kscoreboard reload &7- reload scoreboards"));
        sender.sendMessage(MessageUtil.color("&e/kscoreboard list &7- list scoreboards"));
        sender.sendMessage(MessageUtil.color("&e/kscoreboard info <id> &7- view scoreboard info"));
        sender.sendMessage(MessageUtil.color("&e/kscoreboard create <id> &7- create a file"));
        sender.sendMessage(MessageUtil.color("&e/kscoreboard delete <id> &7- delete a file"));
        sender.sendMessage(MessageUtil.color("&e/kscoreboard setpermission <id> <permission|none>"));
        sender.sendMessage(MessageUtil.color("&e/kscoreboard setpriority <id> <number>"));
        sender.sendMessage(MessageUtil.color("&e/kscoreboard addworld/addregion <id> <name>"));
        sender.sendMessage(MessageUtil.color("&e/kscoreboard settitle/addline/setline/removeline <id> ..."));
    }

    private void sendList(CommandSender sender) {
        sender.sendMessage(MessageUtil.color("&6Scoreboards:"));
        for (KaramScoreboardDefinition scoreboard : manager.getScoreboards()) {
            sender.sendMessage(MessageUtil.color("&7- &e" + scoreboard.getId() + " &8(priority " + scoreboard.getPriority() + ", enabled " + scoreboard.isEnabled() + ")"));
        }
    }

    private void sendInfo(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 2)) {
            return;
        }
        manager.getScoreboard(args[1]).ifPresentOrElse(scoreboard -> {
            sender.sendMessage(MessageUtil.color("&6Scoreboard: &e" + scoreboard.getId()));
            sender.sendMessage(MessageUtil.color("&7Enabled: &f" + scoreboard.isEnabled()));
            sender.sendMessage(MessageUtil.color("&7Priority: &f" + scoreboard.getPriority()));
            sender.sendMessage(MessageUtil.color("&7Permission: &f" + (scoreboard.getPermission().isBlank() ? "none" : scoreboard.getPermission())));
            sender.sendMessage(MessageUtil.color("&7Worlds: &f" + (scoreboard.getWorlds().isEmpty() ? "all" : String.join(", ", scoreboard.getWorlds()))));
            sender.sendMessage(MessageUtil.color("&7Regions: &f" + (scoreboard.getRegions().isEmpty() ? "all" : String.join(", ", scoreboard.getRegions()))));
        }, () -> sender.sendMessage(MessageUtil.color("&cThat scoreboard does not exist.")));
    }

    private void setPermission(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 3)) {
            return;
        }
        String permission = args[2].equalsIgnoreCase("none") ? "" : args[2];
        message(sender, manager.setValue(args[1], "permission", permission), "&aUpdated scoreboard permission.", "&cThat scoreboard does not exist.");
    }

    private void setPriority(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 3)) {
            return;
        }
        try {
            int priority = Integer.parseInt(args[2]);
            message(sender, manager.setValue(args[1], "priority", priority), "&aUpdated scoreboard priority.", "&cThat scoreboard does not exist.");
        } catch (NumberFormatException exception) {
            sender.sendMessage(MessageUtil.color("&cPriority must be a number."));
        }
    }

    private void setTitle(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 3)) {
            return;
        }
        String title = join(args, 2);
        boolean success = manager.setValue(args[1], "title-animation.frames", List.of(title));
        message(sender, success, "&aUpdated scoreboard title.", "&cThat scoreboard does not exist.");
    }

    private void addLine(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 3)) {
            return;
        }
        String line = join(args, 2);
        message(sender, manager.addToList(args[1], "lines", line), "&aAdded line to scoreboard.", "&cThat scoreboard does not exist.");
    }

    private void setLine(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 4)) {
            return;
        }
        try {
            int lineNumber = Integer.parseInt(args[2]);
            String line = join(args, 3);
            message(sender, manager.setLine(args[1], lineNumber, line), "&aUpdated scoreboard line.", "&cThat line or scoreboard does not exist.");
        } catch (NumberFormatException exception) {
            sender.sendMessage(MessageUtil.color("&cLine number must be a number."));
        }
    }

    private void removeLine(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 3)) {
            return;
        }
        try {
            int lineNumber = Integer.parseInt(args[2]);
            message(sender, manager.removeLine(args[1], lineNumber), "&aRemoved scoreboard line.", "&cThat line or scoreboard does not exist.");
        } catch (NumberFormatException exception) {
            sender.sendMessage(MessageUtil.color("&cLine number must be a number."));
        }
    }

    private boolean requireArgs(CommandSender sender, String[] args, int amount) {
        if (args.length < amount) {
            sender.sendMessage(MessageUtil.color("&cNot enough arguments. Use &e/kscoreboard help&c."));
            return false;
        }
        return true;
    }

    private boolean message(CommandSender sender, boolean success, String successMessage, String failMessage) {
        sender.sendMessage(MessageUtil.color(success ? successMessage : failMessage));
        return true;
    }

    private String join(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("help", "reload", "list", "info", "create", "delete", "enable", "disable", "setpermission", "setpriority", "addworld", "removeworld", "addregion", "removeregion", "settitle", "addline", "setline", "removeline"), args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("create")) {
            return filter(manager.getScoreboards().stream().map(KaramScoreboardDefinition::getId).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setpermission")) {
            return filter(List.of("none", "karamsmp.scoreboard.vip", "karamsmp.scoreboard.staff"), args[2]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}

package me.herex.karmsmp.commands;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.scoreboards.ScoreboardDefinition;
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

    public ScoreboardCommand(KaramSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(MessageUtil.color("&cYou don't have permission to use this command!"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.getKaramScoreboardManager().reload();
                sender.sendMessage(MessageUtil.color("&aScoreboards reloaded."));
            }
            case "list" -> sendList(sender);
            case "info" -> sendInfo(sender, args);
            case "create" -> create(sender, args);
            case "delete", "remove" -> delete(sender, args);
            case "enable" -> setEnabled(sender, args, true);
            case "disable" -> setEnabled(sender, args, false);
            case "setpermission", "permission" -> setPermission(sender, args);
            case "setpriority", "priority" -> setPriority(sender, args);
            case "addworld" -> addWorld(sender, args);
            case "removeworld", "delworld" -> removeWorld(sender, args);
            case "addregion" -> addRegion(sender, args);
            case "removeregion", "delregion" -> removeRegion(sender, args);
            case "settitle", "title" -> setTitle(sender, args);
            case "addline" -> addLine(sender, args);
            case "setline" -> setLine(sender, args);
            case "removeline", "delline" -> removeLine(sender, args);
            default -> sender.sendMessage(MessageUtil.color("&cUnknown scoreboard command. Use &e/" + label + " help&c."));
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(MessageUtil.color("&6&lKaramSMP Scoreboards"));
        sender.sendMessage(MessageUtil.color("&e/" + label + " reload &7- Reload all scoreboard .yml files."));
        sender.sendMessage(MessageUtil.color("&e/" + label + " list &7- List loaded scoreboards."));
        sender.sendMessage(MessageUtil.color("&e/" + label + " info <id> &7- Show scoreboard info."));
        sender.sendMessage(MessageUtil.color("&e/" + label + " create <id> &7- Create a new scoreboard file."));
        sender.sendMessage(MessageUtil.color("&e/" + label + " delete <id> &7- Delete a scoreboard file."));
        sender.sendMessage(MessageUtil.color("&e/" + label + " enable/disable <id> &7- Toggle a scoreboard."));
        sender.sendMessage(MessageUtil.color("&e/" + label + " setpermission <id> <permission|none> &7- Require a permission."));
        sender.sendMessage(MessageUtil.color("&e/" + label + " setpriority <id> <number> &7- Change matching priority."));
        sender.sendMessage(MessageUtil.color("&e/" + label + " addworld/removeworld <id> <world> &7- Edit target worlds."));
        sender.sendMessage(MessageUtil.color("&e/" + label + " addregion/removeregion <id> <region> &7- Edit target regions."));
        sender.sendMessage(MessageUtil.color("&e/" + label + " settitle <id> <text> &7- Set a static title."));
        sender.sendMessage(MessageUtil.color("&e/" + label + " addline <id> <text> &7- Add a line."));
        sender.sendMessage(MessageUtil.color("&e/" + label + " setline <id> <line> <text> &7- Edit a line."));
        sender.sendMessage(MessageUtil.color("&e/" + label + " removeline <id> <line> &7- Remove a line."));
    }

    private void sendList(CommandSender sender) {
        List<ScoreboardDefinition> scoreboards = plugin.getKaramScoreboardManager().getScoreboards();
        if (scoreboards.isEmpty()) {
            sender.sendMessage(MessageUtil.color("&cNo scoreboards loaded."));
            return;
        }
        sender.sendMessage(MessageUtil.color("&6Loaded scoreboards:"));
        for (ScoreboardDefinition scoreboard : scoreboards) {
            sender.sendMessage(MessageUtil.color("&e- " + scoreboard.getId() + " &7priority=&f" + scoreboard.getPriority() + " &7enabled=&f" + scoreboard.isEnabled() + " &7permission=&f" + (scoreboard.getPermission().isBlank() ? "none" : scoreboard.getPermission())));
        }
    }

    private void sendInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.color("&cUsage: /kscoreboard info <id>"));
            return;
        }
        plugin.getKaramScoreboardManager().getScoreboard(args[1]).ifPresentOrElse(scoreboard -> {
            sender.sendMessage(MessageUtil.color("&6Scoreboard: &e" + scoreboard.getId()));
            sender.sendMessage(MessageUtil.color("&7Enabled: &f" + scoreboard.isEnabled()));
            sender.sendMessage(MessageUtil.color("&7Priority: &f" + scoreboard.getPriority()));
            sender.sendMessage(MessageUtil.color("&7Permission: &f" + (scoreboard.getPermission().isBlank() ? "none" : scoreboard.getPermission())));
            sender.sendMessage(MessageUtil.color("&7Worlds: &f" + (scoreboard.getWorlds().isEmpty() ? "all" : String.join(", ", scoreboard.getWorlds()))));
            sender.sendMessage(MessageUtil.color("&7Regions: &f" + (scoreboard.getRegions().isEmpty() ? "all" : String.join(", ", scoreboard.getRegions()))));
            sender.sendMessage(MessageUtil.color("&7Lines: &f" + scoreboard.getLineCount()));
            sender.sendMessage(MessageUtil.color("&7File: &fscoreboards/" + scoreboard.getSourceFile().getName()));
        }, () -> sender.sendMessage(MessageUtil.color("&cThat scoreboard does not exist.")));
    }

    private void create(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.color("&cUsage: /kscoreboard create <id>"));
            return;
        }
        sender.sendMessage(plugin.getKaramScoreboardManager().createScoreboard(args[1])
                ? MessageUtil.color("&aCreated scoreboard &e" + args[1] + "&a.")
                : MessageUtil.color("&cCould not create that scoreboard. It may already exist."));
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.color("&cUsage: /kscoreboard delete <id>"));
            return;
        }
        sender.sendMessage(plugin.getKaramScoreboardManager().deleteScoreboard(args[1])
                ? MessageUtil.color("&aDeleted scoreboard &e" + args[1] + "&a.")
                : MessageUtil.color("&cThat scoreboard does not exist."));
    }

    private void setEnabled(CommandSender sender, String[] args, boolean enabled) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.color("&cUsage: /kscoreboard " + (enabled ? "enable" : "disable") + " <id>"));
            return;
        }
        sender.sendMessage(plugin.getKaramScoreboardManager().setEnabled(args[1], enabled)
                ? MessageUtil.color("&aUpdated scoreboard &e" + args[1] + "&a.")
                : MessageUtil.color("&cThat scoreboard does not exist."));
    }

    private void setPermission(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cUsage: /kscoreboard setpermission <id> <permission|none>"));
            return;
        }
        sender.sendMessage(plugin.getKaramScoreboardManager().setPermission(args[1], args[2])
                ? MessageUtil.color("&aUpdated permission for &e" + args[1] + "&a.")
                : MessageUtil.color("&cThat scoreboard does not exist."));
    }

    private void setPriority(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cUsage: /kscoreboard setpriority <id> <number>"));
            return;
        }
        try {
            int priority = Integer.parseInt(args[2]);
            sender.sendMessage(plugin.getKaramScoreboardManager().setPriority(args[1], priority)
                    ? MessageUtil.color("&aUpdated priority for &e" + args[1] + "&a.")
                    : MessageUtil.color("&cThat scoreboard does not exist."));
        } catch (NumberFormatException exception) {
            sender.sendMessage(MessageUtil.color("&cPriority must be a number."));
        }
    }

    private void addWorld(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cUsage: /kscoreboard addworld <id> <world>"));
            return;
        }
        sender.sendMessage(plugin.getKaramScoreboardManager().addWorld(args[1], args[2])
                ? MessageUtil.color("&aAdded world to scoreboard.")
                : MessageUtil.color("&cThat scoreboard does not exist."));
    }

    private void removeWorld(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cUsage: /kscoreboard removeworld <id> <world>"));
            return;
        }
        sender.sendMessage(plugin.getKaramScoreboardManager().removeWorld(args[1], args[2])
                ? MessageUtil.color("&aRemoved world from scoreboard.")
                : MessageUtil.color("&cThat scoreboard does not exist."));
    }

    private void addRegion(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cUsage: /kscoreboard addregion <id> <region>"));
            return;
        }
        sender.sendMessage(plugin.getKaramScoreboardManager().addRegion(args[1], args[2])
                ? MessageUtil.color("&aAdded region to scoreboard.")
                : MessageUtil.color("&cThat scoreboard does not exist."));
    }

    private void removeRegion(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cUsage: /kscoreboard removeregion <id> <region>"));
            return;
        }
        sender.sendMessage(plugin.getKaramScoreboardManager().removeRegion(args[1], args[2])
                ? MessageUtil.color("&aRemoved region from scoreboard.")
                : MessageUtil.color("&cThat scoreboard does not exist."));
    }

    private void setTitle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cUsage: /kscoreboard settitle <id> <text>"));
            return;
        }
        String title = join(args, 2);
        sender.sendMessage(plugin.getKaramScoreboardManager().setTitle(args[1], title)
                ? MessageUtil.color("&aUpdated title for &e" + args[1] + "&a.")
                : MessageUtil.color("&cThat scoreboard does not exist."));
    }

    private void addLine(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cUsage: /kscoreboard addline <id> <text>"));
            return;
        }
        sender.sendMessage(plugin.getKaramScoreboardManager().addLine(args[1], join(args, 2))
                ? MessageUtil.color("&aAdded line to scoreboard.")
                : MessageUtil.color("&cThat scoreboard does not exist or already has 15 lines."));
    }

    private void setLine(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.color("&cUsage: /kscoreboard setline <id> <line> <text>"));
            return;
        }
        try {
            int line = Integer.parseInt(args[2]);
            sender.sendMessage(plugin.getKaramScoreboardManager().setLine(args[1], line, join(args, 3))
                    ? MessageUtil.color("&aUpdated line &e" + line + "&a.")
                    : MessageUtil.color("&cThat scoreboard or line does not exist."));
        } catch (NumberFormatException exception) {
            sender.sendMessage(MessageUtil.color("&cLine must be a number."));
        }
    }

    private void removeLine(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color("&cUsage: /kscoreboard removeline <id> <line>"));
            return;
        }
        try {
            int line = Integer.parseInt(args[2]);
            sender.sendMessage(plugin.getKaramScoreboardManager().removeLine(args[1], line)
                    ? MessageUtil.color("&aRemoved line &e" + line + "&a.")
                    : MessageUtil.color("&cThat scoreboard or line does not exist."));
        } catch (NumberFormatException exception) {
            sender.sendMessage(MessageUtil.color("&cLine must be a number."));
        }
    }

    private String join(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (index > start) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(List.of("help", "reload", "list", "info", "create", "delete", "enable", "disable", "setpermission", "setpriority", "addworld", "removeworld", "addregion", "removeregion", "settitle", "addline", "setline", "removeline"), args[0]);
        }

        if (args.length == 2 && List.of("info", "delete", "remove", "enable", "disable", "setpermission", "permission", "setpriority", "priority", "addworld", "removeworld", "delworld", "addregion", "removeregion", "delregion", "settitle", "title", "addline", "setline", "removeline", "delline").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(plugin.getKaramScoreboardManager().getScoreboards().stream().map(ScoreboardDefinition::getId).toList(), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setpermission")) {
            return filter(List.of("none", "karamsmp.scoreboard.vip", "karamsmp.scoreboard.staff"), args[2]);
        }

        return List.of();
    }

    private List<String> filter(List<String> values, String token) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}

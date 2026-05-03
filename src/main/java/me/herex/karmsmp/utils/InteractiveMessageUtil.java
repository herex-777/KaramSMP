package me.herex.karmsmp.utils;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class InteractiveMessageUtil {

    private InteractiveMessageUtil() {
    }

    public static void broadcast(JavaPlugin plugin, String message, String hoverText, String clickCommand, String clickAction) {
        String coloredMessage = MessageUtil.color(message);
        String coloredHover = MessageUtil.color(hoverText);
        boolean hasHover = coloredHover != null && !coloredHover.isBlank();
        boolean hasClick = clickCommand != null && !clickCommand.isBlank();

        if (!hasHover && !hasClick) {
            Bukkit.broadcastMessage(coloredMessage);
            return;
        }

        boolean sentWithTellraw = false;
        String json = buildJson(coloredMessage, hasHover ? coloredHover : null, hasClick ? clickCommand : null, clickAction);
        CommandSender console = Bukkit.getConsoleSender();

        for (Player online : Bukkit.getOnlinePlayers()) {
            try {
                String name = online.getName();
                boolean result = Bukkit.dispatchCommand(console, "minecraft:tellraw " + name + " " + json);
                sentWithTellraw = sentWithTellraw || result;
            } catch (Throwable throwable) {
                online.sendMessage(coloredMessage);
            }
        }

        console.sendMessage(coloredMessage);
        if (!sentWithTellraw && plugin != null) {
            plugin.getLogger().fine("Interactive join message fell back to plain text because tellraw was not accepted by the server.");
        }
    }

    private static String buildJson(String text, String hoverText, String clickCommand, String clickAction) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"text\":\"").append(jsonEscape(text)).append('\"');

        if (hoverText != null && !hoverText.isBlank()) {
            json.append(",\"hoverEvent\":{");
            json.append("\"action\":\"show_text\",");
            json.append("\"contents\":{\"text\":\"").append(jsonEscape(hoverText)).append("\"}");
            json.append('}');
        }

        if (clickCommand != null && !clickCommand.isBlank()) {
            String normalizedAction = normalizeClickAction(clickAction);
            json.append(",\"clickEvent\":{");
            json.append("\"action\":\"").append(normalizedAction).append("\",");
            json.append("\"value\":\"").append(jsonEscape(clickCommand)).append("\"");
            json.append('}');
        }

        json.append('}');
        return json.toString();
    }

    private static String normalizeClickAction(String action) {
        if (action == null) {
            return "run_command";
        }
        return action.toUpperCase(Locale.ROOT).contains("SUGGEST") ? "suggest_command" : "run_command";
    }

    private static String jsonEscape(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}

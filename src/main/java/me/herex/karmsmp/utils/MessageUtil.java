package me.herex.karmsmp.utils;

import org.bukkit.ChatColor;

import java.util.List;

public final class MessageUtil {

    private MessageUtil() {
    }

    public static String color(String message) {
        if (message == null) {
            return "";
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }

        return String.join("\n", lines);
    }
}

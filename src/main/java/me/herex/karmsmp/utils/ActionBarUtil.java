package me.herex.karmsmp.utils;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public final class ActionBarUtil {

    private ActionBarUtil() {
    }

    public static void send(Player player, String message) {
        if (player == null) {
            return;
        }

        String colored = MessageUtil.color(message);
        if (tryStringActionBar(player, colored)) {
            return;
        }
        if (tryBungeeActionBar(player, colored)) {
            return;
        }

        player.sendMessage(colored);
    }

    private static boolean tryStringActionBar(Player player, String message) {
        try {
            Method method = player.getClass().getMethod("sendActionBar", String.class);
            method.invoke(player, message);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean tryBungeeActionBar(Player player, String message) {
        try {
            Class<?> chatMessageTypeClass = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Class<?> textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Object actionBar = Enum.valueOf((Class<Enum>) chatMessageTypeClass.asSubclass(Enum.class), "ACTION_BAR");
            Object components = textComponentClass.getMethod("fromLegacyText", String.class).invoke(null, message);
            Object spigot = player.getClass().getMethod("spigot").invoke(player);

            for (Method method : spigot.getClass().getMethods()) {
                if (!method.getName().equals("sendMessage") || method.getParameterCount() != 2) {
                    continue;
                }
                Class<?>[] types = method.getParameterTypes();
                if (types[0].isAssignableFrom(chatMessageTypeClass) && types[1].isArray()) {
                    method.invoke(spigot, actionBar, components);
                    return true;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }
}

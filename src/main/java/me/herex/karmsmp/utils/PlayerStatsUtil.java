package me.herex.karmsmp.utils;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;

public final class PlayerStatsUtil {

    private PlayerStatsUtil() {
    }

    public static int getKills(Player player) {
        return safeStatistic(player, Statistic.PLAYER_KILLS);
    }

    public static int getDeaths(Player player) {
        return safeStatistic(player, Statistic.DEATHS);
    }

    public static int getMobKills(Player player) {
        return safeStatistic(player, Statistic.MOB_KILLS);
    }

    public static int getPlayTicks(Player player) {
        return safeStatistic(player, Statistic.PLAY_ONE_MINUTE);
    }

    public static String getPlaytime(Player player) {
        long totalSeconds = Math.max(0L, getPlayTicks(player) / 20L);
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;

        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    public static int getPing(Player player) {
        return Math.max(0, player.getPing());
    }

    private static int safeStatistic(Player player, Statistic statistic) {
        try {
            return player.getStatistic(statistic);
        } catch (IllegalArgumentException exception) {
            return 0;
        }
    }
}

package me.herex.karmsmp.utils;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class SoundUtil {

    private SoundUtil() {
    }

    public static Sound fromConfig(String configured) {
        if (configured == null) {
            return null;
        }

        String raw = configured.trim();
        if (raw.isEmpty() || raw.equalsIgnoreCase("none")) {
            return null;
        }

        Sound direct = valueOfOrNull(raw);
        if (direct != null) {
            return direct;
        }

        String normalized = raw;
        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator < normalized.length() - 1) {
            normalized = normalized.substring(namespaceSeparator + 1);
        }

        normalized = normalized
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace('.', '_')
                .replace('-', '_')
                .replace(' ', '_');

        return valueOfOrNull(normalized);
    }

    public static void play(Player player, String configured, float volume, float pitch) {
        if (player == null) {
            return;
        }
        Sound sound = fromConfig(configured);
        if (sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private static Sound valueOfOrNull(String name) {
        try {
            return Sound.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

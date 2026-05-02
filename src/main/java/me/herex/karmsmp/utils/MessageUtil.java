package me.herex.karmsmp.utils;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageUtil {

    private static final char COLOR_CHAR = '\u00A7';
    private static final int HEX_COLOR_LENGTH = 14;
    private static final Pattern AMP_HEX_PATTERN = Pattern.compile("&?#([A-Fa-f0-9]{6})");
    private static final Pattern TAG_HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern ZERO_WIDTH_PATTERN = Pattern.compile("[\\u200B\\u200C\\u200D\\u200E\\u200F\\uFEFF]");

    private MessageUtil() {
    }

    /**
     * Supports legacy Bukkit color codes (&a, &l, etc.) and RGB hex colors.
     * Hex examples: &#00AAFF, #00AAFF, <#00AAFF>
     */
    public static String color(String message) {
        if (message == null) {
            return "";
        }

        String normalized = stripZeroWidth(message);
        String withHexTags = translateTagHex(normalized);
        String withHex = translateAmpHex(withHexTags);
        return ChatColor.translateAlternateColorCodes('&', withHex);
    }

    public static List<String> colorList(List<String> lines) {
        if (lines == null) {
            return List.of();
        }
        return lines.stream().map(MessageUtil::color).toList();
    }

    public static String stripColor(String message) {
        return ChatColor.stripColor(color(message));
    }

    public static String stripZeroWidth(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        return ZERO_WIDTH_PATTERN.matcher(message).replaceAll("");
    }

    public static String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }

        return String.join("\n", lines);
    }

    public static String safeLegacySubstring(String input, int maxRawLength) {
        String colored = color(input == null ? "" : input);
        if (colored.length() <= maxRawLength) {
            return colored;
        }
        int cut = safeCutIndex(colored, maxRawLength);
        return colored.substring(0, cut);
    }

    /**
     * Splits a colored string into prefix/suffix pieces for scoreboard teams.
     * It keeps raw Minecraft color codes intact so hex colors do not show as broken text.
     */
    public static String[] splitForTeam(String input, int prefixLimit, int suffixLimit) {
        String colored = color(input == null ? "" : input);
        if (colored.length() <= prefixLimit) {
            return new String[]{colored, ""};
        }

        int prefixCut = safeCutIndex(colored, prefixLimit);
        String prefix = colored.substring(0, prefixCut);
        String suffix = getLastColorsWithHex(prefix) + colored.substring(prefixCut);

        if (suffix.length() > suffixLimit) {
            int suffixCut = safeCutIndex(suffix, suffixLimit);
            suffix = suffix.substring(0, suffixCut);
        }

        return new String[]{prefix, suffix};
    }

    public static String getLastColorsWithHex(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String lastColor = "";
        List<String> formats = new ArrayList<>();
        int index = 0;
        while (index < input.length() - 1) {
            if (input.charAt(index) != COLOR_CHAR) {
                index++;
                continue;
            }

            char code = Character.toLowerCase(input.charAt(index + 1));
            if (code == 'x' && index + HEX_COLOR_LENGTH <= input.length() && isCompleteHexColor(input, index)) {
                lastColor = input.substring(index, index + HEX_COLOR_LENGTH);
                formats.clear();
                index += HEX_COLOR_LENGTH;
                continue;
            }

            String colorCode = input.substring(index, index + 2);
            if (isLegacyColor(code)) {
                lastColor = colorCode;
                formats.clear();
            } else if (isFormat(code)) {
                if (!formats.contains(colorCode)) {
                    formats.add(colorCode);
                }
            } else if (code == 'r') {
                lastColor = "";
                formats.clear();
            }
            index += 2;
        }

        StringBuilder builder = new StringBuilder(lastColor);
        for (String format : formats) {
            builder.append(format);
        }
        return builder.toString();
    }

    private static String translateTagHex(String input) {
        Matcher matcher = TAG_HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(toMinecraftHex(hex)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String translateAmpHex(String input) {
        Matcher matcher = AMP_HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(toMinecraftHex(hex)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String toMinecraftHex(String hex) {
        StringBuilder builder = new StringBuilder("§x");
        for (char character : hex.toCharArray()) {
            builder.append('§').append(character);
        }
        return builder.toString();
    }

    private static int safeCutIndex(String input, int maxRawLength) {
        int cut = Math.min(Math.max(0, maxRawLength), input.length());
        boolean changed;
        do {
            changed = false;
            if (cut > 0 && input.charAt(cut - 1) == COLOR_CHAR) {
                cut--;
                changed = true;
            }

            int lastHexStart = input.lastIndexOf("§x", Math.max(0, cut - 1));
            if (lastHexStart >= 0 && cut > lastHexStart && cut < lastHexStart + HEX_COLOR_LENGTH) {
                cut = lastHexStart;
                changed = true;
            }
        } while (changed && cut > 0);
        return Math.max(0, cut);
    }

    private static boolean isCompleteHexColor(String input, int start) {
        if (start + HEX_COLOR_LENGTH > input.length()) {
            return false;
        }
        if (input.charAt(start) != COLOR_CHAR || Character.toLowerCase(input.charAt(start + 1)) != 'x') {
            return false;
        }
        for (int offset = 2; offset < HEX_COLOR_LENGTH; offset += 2) {
            if (input.charAt(start + offset) != COLOR_CHAR) {
                return false;
            }
            if (!isHexDigit(input.charAt(start + offset + 1))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHexDigit(char character) {
        return (character >= '0' && character <= '9')
                || (character >= 'a' && character <= 'f')
                || (character >= 'A' && character <= 'F');
    }

    private static boolean isLegacyColor(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    private static boolean isFormat(char code) {
        return code == 'k' || code == 'l' || code == 'm' || code == 'n' || code == 'o';
    }
}

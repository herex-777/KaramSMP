package me.herex.karmsmp.scoreboards;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KaramScoreboardDefinition {

    private final File file;
    private final String id;
    private final boolean enabled;
    private final int priority;
    private final String permission;
    private final List<String> worlds;
    private final List<String> regions;
    private final boolean titleAnimationEnabled;
    private final int titleAnimationIntervalTicks;
    private final List<String> titleFrames;
    private final boolean linesAnimationEnabled;
    private final int linesAnimationIntervalTicks;
    private final List<String> lines;

    private KaramScoreboardDefinition(File file, String id, boolean enabled, int priority, String permission,
                                      List<String> worlds, List<String> regions,
                                      boolean titleAnimationEnabled, int titleAnimationIntervalTicks, List<String> titleFrames,
                                      boolean linesAnimationEnabled, int linesAnimationIntervalTicks, List<String> lines) {
        this.file = file;
        this.id = id;
        this.enabled = enabled;
        this.priority = priority;
        this.permission = permission == null ? "" : permission;
        this.worlds = worlds;
        this.regions = regions;
        this.titleAnimationEnabled = titleAnimationEnabled;
        this.titleAnimationIntervalTicks = Math.max(1, titleAnimationIntervalTicks);
        this.titleFrames = titleFrames.isEmpty() ? List.of("&6&lKaramSMP") : titleFrames;
        this.linesAnimationEnabled = linesAnimationEnabled;
        this.linesAnimationIntervalTicks = Math.max(1, linesAnimationIntervalTicks);
        this.lines = lines;
    }

    public static KaramScoreboardDefinition load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String fallbackId = file.getName().replaceFirst("(?i)\\.ya?ml$", "");
        String id = cleanId(yaml.getString("id", fallbackId));
        boolean enabled = yaml.getBoolean("enabled", true);
        int priority = yaml.getInt("priority", 0);
        String permission = yaml.getString("permission", "");
        List<String> worlds = normalizeList(yaml.getStringList("worlds"));
        List<String> regions = normalizeList(yaml.getStringList("regions"));

        boolean titleEnabled = yaml.getBoolean("title-animation.enabled", true);
        int titleInterval = yaml.getInt("title-animation.interval-ticks", 20);
        List<String> titleFrames = yaml.getStringList("title-animation.frames");
        if (titleFrames.isEmpty()) {
            String title = yaml.getString("title", yaml.getString("display-name", "&6&lKaramSMP"));
            titleFrames = List.of(title);
        }

        boolean linesEnabled = yaml.getBoolean("lines-animation.enabled", false);
        int linesInterval = yaml.getInt("lines-animation.interval-ticks", 20);
        List<String> lines = yaml.getStringList("lines");
        return new KaramScoreboardDefinition(file, id, enabled, priority, permission, worlds, regions,
                titleEnabled, titleInterval, titleFrames, linesEnabled, linesInterval, lines);
    }

    public boolean matches(Player player, List<String> currentRegions) {
        if (!enabled) {
            return false;
        }

        if (!permission.isBlank() && !player.hasPermission(permission)) {
            return false;
        }

        if (!worlds.isEmpty() && !worlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT))) {
            return false;
        }

        if (!regions.isEmpty()) {
            boolean inRegion = false;
            for (String region : currentRegions) {
                if (regions.contains(region.toLowerCase(Locale.ROOT))) {
                    inRegion = true;
                    break;
                }
            }
            if (!inRegion) {
                return false;
            }
        }

        return true;
    }

    public String getAnimatedTitle(long ticks) {
        if (!titleAnimationEnabled || titleFrames.size() == 1) {
            return titleFrames.get(0);
        }
        int frame = (int) ((ticks / titleAnimationIntervalTicks) % titleFrames.size());
        return titleFrames.get(frame);
    }

    public List<String> getAnimatedLines(long ticks) {
        if (!linesAnimationEnabled) {
            return lines;
        }

        List<String> output = new ArrayList<>();
        for (String line : lines) {
            if (line == null || !line.contains("||")) {
                output.add(line);
                continue;
            }

            String[] frames = line.split("\\|\\|");
            if (frames.length == 0) {
                output.add(line);
                continue;
            }

            int frame = (int) ((ticks / linesAnimationIntervalTicks) % frames.length);
            output.add(frames[frame]);
        }
        return output;
    }

    public File getFile() {
        return file;
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPriority() {
        return priority;
    }

    public String getPermission() {
        return permission;
    }

    public List<String> getWorlds() {
        return worlds;
    }

    public List<String> getRegions() {
        return regions;
    }

    public List<String> getLines() {
        return lines;
    }

    private static List<String> normalizeList(List<String> input) {
        List<String> output = new ArrayList<>();
        for (String value : input) {
            if (value != null && !value.isBlank()) {
                output.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return output;
    }

    public static String cleanId(String input) {
        if (input == null) {
            return "scoreboard";
        }
        String cleaned = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return cleaned.isBlank() ? "scoreboard" : cleaned;
    }
}

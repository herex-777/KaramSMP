package me.herex.karmsmp.scoreboards;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.regions.Region;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ScoreboardDefinition {

    private final String id;
    private final boolean enabled;
    private final int priority;
    private final String permission;
    private final Set<String> worlds;
    private final Set<String> regions;
    private final ScoreboardLine title;
    private final List<ScoreboardLine> lines;
    private final File sourceFile;

    public ScoreboardDefinition(String id, boolean enabled, int priority, String permission, Set<String> worlds, Set<String> regions, ScoreboardLine title, List<ScoreboardLine> lines, File sourceFile) {
        this.id = id;
        this.enabled = enabled;
        this.priority = priority;
        this.permission = permission == null ? "" : permission;
        this.worlds = worlds == null ? Set.of() : Set.copyOf(worlds);
        this.regions = regions == null ? Set.of() : Set.copyOf(regions);
        this.title = title == null ? ScoreboardLine.staticLine("&6&lKaramSMP") : title;
        this.lines = lines == null ? List.of() : List.copyOf(lines);
        this.sourceFile = sourceFile;
    }

    public static ScoreboardDefinition fromFile(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String fileName = file.getName().replaceFirst("(?i)\\.ya?ml$", "");
        String id = cleanId(config.getString("id", fileName));
        boolean enabled = config.getBoolean("enabled", true);
        int priority = config.getInt("priority", 0);
        String permission = config.getString("permission", "");
        Set<String> worlds = normalizeSet(config.getStringList("worlds"));
        Set<String> regions = normalizeSet(config.getStringList("regions"));
        int titleSpeed = Math.max(1, config.getInt("animation.title-speed-ticks", config.getInt("title-speed-ticks", 20)));
        int lineSpeed = Math.max(1, config.getInt("animation.line-speed-ticks", config.getInt("line-speed-ticks", 20)));

        ScoreboardLine title = loadTitle(config, titleSpeed);
        List<ScoreboardLine> lines = loadLines(config, lineSpeed);

        return new ScoreboardDefinition(id, enabled, priority, permission, worlds, regions, title, lines, file);
    }

    private static ScoreboardLine loadTitle(YamlConfiguration config, int titleSpeed) {
        if (config.isList("title.frames")) {
            return new ScoreboardLine(config.getStringList("title.frames"), titleSpeed);
        }
        if (config.isList("title")) {
            return new ScoreboardLine(config.getStringList("title"), titleSpeed);
        }
        return ScoreboardLine.staticLine(config.getString("title", "&6&lKaramSMP"));
    }

    private static List<ScoreboardLine> loadLines(YamlConfiguration config, int lineSpeed) {
        List<ScoreboardLine> output = new ArrayList<>();
        List<String> baseLines = config.getStringList("lines");
        ConfigurationSection animatedLines = config.getConfigurationSection("animated-lines");

        for (int index = 0; index < baseLines.size() && index < 15; index++) {
            String key = String.valueOf(index + 1);
            String zeroKey = String.valueOf(index);
            if (animatedLines != null) {
                if (animatedLines.isList(key)) {
                    output.add(new ScoreboardLine(animatedLines.getStringList(key), lineSpeed));
                    continue;
                }
                if (animatedLines.isList(zeroKey)) {
                    output.add(new ScoreboardLine(animatedLines.getStringList(zeroKey), lineSpeed));
                    continue;
                }
                if (animatedLines.isList(key + ".frames")) {
                    int speed = animatedLines.getInt(key + ".speed-ticks", lineSpeed);
                    output.add(new ScoreboardLine(animatedLines.getStringList(key + ".frames"), speed));
                    continue;
                }
                if (animatedLines.isList(zeroKey + ".frames")) {
                    int speed = animatedLines.getInt(zeroKey + ".speed-ticks", lineSpeed);
                    output.add(new ScoreboardLine(animatedLines.getStringList(zeroKey + ".frames"), speed));
                    continue;
                }
            }
            output.add(ScoreboardLine.staticLine(baseLines.get(index)));
        }

        return output;
    }

    public boolean matches(Player player, KaramSMP plugin) {
        if (!enabled || player == null) {
            return false;
        }

        if (!permission.isBlank() && !player.hasPermission(permission)) {
            return false;
        }

        if (!worlds.isEmpty() && !worlds.contains("*") && !worlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT))) {
            return false;
        }

        if (!regions.isEmpty() && !regions.contains("*")) {
            if (plugin.getRegionManager() == null) {
                return false;
            }
            List<String> currentRegions = plugin.getRegionManager().getRegionsAt(player.getLocation()).stream()
                    .map(Region::getName)
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .toList();
            boolean found = regions.stream().anyMatch(currentRegions::contains);
            if (!found) {
                return false;
            }
        }

        return true;
    }

    public String renderTitle(Player player, KaramSMP plugin, int tick) {
        return MessageUtil.color(plugin.getRankManager().applyPlaceholders(player, title.getFrame(tick)));
    }

    public List<String> renderLines(Player player, KaramSMP plugin, int tick) {
        List<String> rendered = new ArrayList<>();
        for (ScoreboardLine line : lines) {
            rendered.add(MessageUtil.color(plugin.getRankManager().applyPlaceholders(player, line.getFrame(tick))));
        }
        return rendered;
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

    public Set<String> getWorlds() {
        return worlds;
    }

    public Set<String> getRegions() {
        return regions;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public int getLineCount() {
        return lines.size();
    }

    public static String cleanId(String id) {
        if (id == null) {
            return "";
        }
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    private static Set<String> normalizeSet(List<String> input) {
        Set<String> output = new HashSet<>();
        for (String value : input) {
            if (value != null && !value.isBlank()) {
                output.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return output;
    }
}

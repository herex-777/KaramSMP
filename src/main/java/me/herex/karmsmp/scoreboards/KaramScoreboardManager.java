package me.herex.karmsmp.scoreboards;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.regions.Region;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class KaramScoreboardManager {

    private final KaramSMP plugin;
    private final Map<String, KaramScoreboardDefinition> scoreboards = new HashMap<>();
    private final Map<UUID, String> activeScoreboards = new HashMap<>();
    private BukkitTask task;
    private long ticks;

    public KaramScoreboardManager(KaramSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        reloadDefinitions();

        if (!plugin.getConfig().getBoolean("scoreboards.enabled", true)) {
            return;
        }

        long interval = Math.max(1L, plugin.getConfig().getLong("scoreboards.update-interval-ticks", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ticks += interval;
            updateAllPlayers();
        }, 20L, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void reload() {
        start();
    }

    public void reloadDefinitions() {
        scoreboards.clear();
        ensureDefaultFiles();

        File folder = getScoreboardsFolder();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml") || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            KaramScoreboardDefinition definition = KaramScoreboardDefinition.load(file);
            scoreboards.put(definition.getId(), definition);
        }

        plugin.getLogger().info("Loaded " + scoreboards.size() + " KaramSMP scoreboard(s).");
    }

    public void updateAllPlayers() {
        if (!plugin.getConfig().getBoolean("scoreboards.enabled", true)) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    public void updatePlayer(Player player) {
        Optional<KaramScoreboardDefinition> optional = findScoreboard(player);
        if (optional.isEmpty()) {
            activeScoreboards.remove(player.getUniqueId());
            if (plugin.getConfig().getBoolean("scoreboards.reset-when-no-match", false) && Bukkit.getScoreboardManager() != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            return;
        }

        KaramScoreboardDefinition definition = optional.get();
        activeScoreboards.put(player.getUniqueId(), definition.getId());
        applyScoreboard(player, definition);
    }

    public Optional<KaramScoreboardDefinition> findScoreboard(Player player) {
        List<String> currentRegions = new ArrayList<>();
        if (plugin.getRegionManager() != null) {
            currentRegions = plugin.getRegionManager().getRegionsAt(player.getLocation()).stream().map(Region::getName).toList();
        }

        List<String> finalCurrentRegions = currentRegions;
        return scoreboards.values().stream()
                .filter(definition -> definition.matches(player, finalCurrentRegions))
                .sorted(Comparator.comparingInt(KaramScoreboardDefinition::getPriority).reversed().thenComparing(KaramScoreboardDefinition::getId))
                .findFirst();
    }

    public void applyScoreboard(Player player, KaramScoreboardDefinition definition) {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        String title = trim(plugin.getRankManager().applyPlaceholders(player, definition.getAnimatedTitle(ticks)), 128);
        Objective objective = scoreboard.registerNewObjective("ksmp", Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = definition.getLines();
        int score = Math.min(lines.size(), 15);
        int index = 0;
        for (String rawLine : lines) {
            if (index >= 15) {
                break;
            }
            String line = plugin.getRankManager().applyPlaceholders(player, rawLine)
                    .replace("%karamsmp_scoreboard%", definition.getId())
                    .replace("%karamsmp_scoreboard_id%", definition.getId());
            objective.getScore(makeUniqueEntry(trim(line, 40), index)).setScore(score--);
            index++;
        }

        player.setScoreboard(scoreboard);
        plugin.getPlayerDisplayManager().updateNameTagsForViewer(player);
    }

    public File getScoreboardsFolder() {
        return new File(plugin.getDataFolder(), plugin.getConfig().getString("scoreboards.folder", "scoreboards"));
    }

    public List<KaramScoreboardDefinition> getScoreboards() {
        return scoreboards.values().stream()
                .sorted(Comparator.comparingInt(KaramScoreboardDefinition::getPriority).reversed().thenComparing(KaramScoreboardDefinition::getId))
                .toList();
    }

    public Optional<KaramScoreboardDefinition> getScoreboard(String id) {
        return Optional.ofNullable(scoreboards.get(KaramScoreboardDefinition.cleanId(id)));
    }

    public String getActiveScoreboardId(Player player) {
        return activeScoreboards.getOrDefault(player.getUniqueId(), "none");
    }

    public boolean createScoreboard(String id) {
        String cleanId = KaramScoreboardDefinition.cleanId(id);
        File file = new File(getScoreboardsFolder(), cleanId + ".yml");
        if (file.exists()) {
            return false;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", cleanId);
        yaml.set("enabled", true);
        yaml.set("priority", 0);
        yaml.set("permission", "");
        yaml.set("worlds", new ArrayList<String>());
        yaml.set("regions", new ArrayList<String>());
        yaml.set("title-animation.enabled", true);
        yaml.set("title-animation.interval-ticks", 10);
        yaml.set("title-animation.frames", List.of("&6&lKaramSMP", "&e&lKaramSMP"));
        yaml.set("lines", List.of("&7&m----------------", "&fPlayer: &e%player%", "&fRank: %karamsmp_ranks_prefix%", "&7&m----------------"));
        return saveYaml(file, yaml);
    }

    public boolean deleteScoreboard(String id) {
        Optional<KaramScoreboardDefinition> definition = getScoreboard(id);
        return definition.filter(scoreboardDefinition -> scoreboardDefinition.getFile().delete()).isPresent();
    }

    public boolean setValue(String id, String path, Object value) {
        Optional<KaramScoreboardDefinition> definition = getScoreboard(id);
        if (definition.isEmpty()) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(definition.get().getFile());
        yaml.set(path, value);
        return saveYaml(definition.get().getFile(), yaml);
    }

    public boolean addToList(String id, String path, String value) {
        Optional<KaramScoreboardDefinition> definition = getScoreboard(id);
        if (definition.isEmpty()) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(definition.get().getFile());
        List<String> list = new ArrayList<>(yaml.getStringList(path));
        if (!list.contains(value)) {
            list.add(value);
        }
        yaml.set(path, list);
        return saveYaml(definition.get().getFile(), yaml);
    }

    public boolean removeFromList(String id, String path, String value) {
        Optional<KaramScoreboardDefinition> definition = getScoreboard(id);
        if (definition.isEmpty()) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(definition.get().getFile());
        List<String> list = new ArrayList<>(yaml.getStringList(path));
        boolean removed = list.removeIf(saved -> saved.equalsIgnoreCase(value));
        yaml.set(path, list);
        return removed && saveYaml(definition.get().getFile(), yaml);
    }

    public boolean setLine(String id, int lineNumber, String value) {
        Optional<KaramScoreboardDefinition> definition = getScoreboard(id);
        if (definition.isEmpty() || lineNumber < 1) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(definition.get().getFile());
        List<String> lines = new ArrayList<>(yaml.getStringList("lines"));
        if (lineNumber > lines.size()) {
            return false;
        }
        lines.set(lineNumber - 1, value);
        yaml.set("lines", lines);
        return saveYaml(definition.get().getFile(), yaml);
    }

    public boolean removeLine(String id, int lineNumber) {
        Optional<KaramScoreboardDefinition> definition = getScoreboard(id);
        if (definition.isEmpty() || lineNumber < 1) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(definition.get().getFile());
        List<String> lines = new ArrayList<>(yaml.getStringList("lines"));
        if (lineNumber > lines.size()) {
            return false;
        }
        lines.remove(lineNumber - 1);
        yaml.set("lines", lines);
        return saveYaml(definition.get().getFile(), yaml);
    }

    private void ensureDefaultFiles() {
        File folder = getScoreboardsFolder();
        if (!folder.exists()) {
            folder.mkdirs();
        }

        saveResourceIfMissing("scoreboards/default.yml");
        saveResourceIfMissing("scoreboards/spawn.yml");
        saveResourceIfMissing("scoreboards/staff.yml");
        saveResourceIfMissing("scoreboards/nether.yml");
    }

    private void saveResourceIfMissing(String path) {
        File target = new File(plugin.getDataFolder(), path);
        if (!target.exists()) {
            plugin.saveResource(path, false);
        }
    }

    private boolean saveYaml(File file, YamlConfiguration yaml) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(file);
            reloadDefinitions();
            updateAllPlayers();
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save scoreboard file: " + exception.getMessage());
            return false;
        }
    }

    private String makeUniqueEntry(String line, int index) {
        return line + ChatColor.RESET + String.valueOf(ChatColor.COLOR_CHAR) + Integer.toHexString(index);
    }

    private String trim(String input, int maxLength) {
        String colored = MessageUtil.color(input == null ? "" : input);
        if (colored.length() <= maxLength) {
            return colored;
        }
        return colored.substring(0, maxLength);
    }
}

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
import org.bukkit.scoreboard.Team;

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

    private static final String OBJECTIVE_NAME = "ksmp";
    private static final String LINE_TEAM_PREFIX = "ksl_";
    private static final int MAX_LINES = 15;

    private final KaramSMP plugin;
    private final Map<String, KaramScoreboardDefinition> scoreboards = new HashMap<>();
    private final Map<UUID, String> activeScoreboards = new HashMap<>();
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private final Map<UUID, List<String>> previousEntries = new HashMap<>();
    private BukkitTask task;
    private long ticks;

    public KaramScoreboardManager(KaramSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        reloadDefinitions();

        if (!plugin.getConfig().getBoolean("scoreboards.enabled", true)) {
            clearAllPlayers();
            return;
        }

        long interval = Math.max(1L, plugin.getConfig().getLong("scoreboards.update-interval-ticks", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ticks += interval;
            updateAllPlayers();
        }, 1L, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void reload() {
        ticks = 0L;
        start();
        Bukkit.getScheduler().runTask(plugin, this::updateAllPlayers);
    }

    public void reloadDefinitions() {
        scoreboards.clear();
        ensureDefaultFiles();

        File folder = getScoreboardsFolder();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml") || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
        if (files == null) {
            plugin.getLogger().warning("Could not read scoreboards folder: " + folder.getAbsolutePath());
            return;
        }

        for (File file : files) {
            try {
                KaramScoreboardDefinition definition = KaramScoreboardDefinition.load(file);
                scoreboards.put(definition.getId(), definition);
            } catch (Exception exception) {
                plugin.getLogger().warning("Could not load scoreboard file " + file.getName() + ": " + exception.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + scoreboards.size() + " KaramSMP scoreboard(s).");
    }

    public void updateAllPlayers() {
        if (!plugin.getConfig().getBoolean("scoreboards.enabled", true)) {
            clearAllPlayers();
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    public void updatePlayer(Player player) {
        if (!player.isOnline()) {
            return;
        }

        if (!plugin.getConfig().getBoolean("scoreboards.enabled", true)) {
            clearPlayer(player);
            return;
        }

        Optional<KaramScoreboardDefinition> optional = findScoreboard(player);
        if (optional.isEmpty()) {
            activeScoreboards.remove(player.getUniqueId());
            clearSidebar(player);
            if (plugin.getConfig().getBoolean("scoreboards.reset-when-no-match", false) && Bukkit.getScoreboardManager() != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                playerScoreboards.remove(player.getUniqueId());
            }
            return;
        }

        KaramScoreboardDefinition definition = optional.get();
        activeScoreboards.put(player.getUniqueId(), definition.getId());
        applyScoreboard(player, definition);
    }

    public void clearPlayer(Player player) {
        clearSidebar(player);
        activeScoreboards.remove(player.getUniqueId());
        playerScoreboards.remove(player.getUniqueId());
        previousEntries.remove(player.getUniqueId());
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

        Scoreboard scoreboard = getOrCreateScoreboard(player);
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        String title = trim(plugin.getRankManager().applyPlaceholders(player, definition.getAnimatedTitle(ticks)), 128);

        if (objective == null) {
            objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            objective.setDisplayName(title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        List<String> oldEntries = previousEntries.getOrDefault(player.getUniqueId(), List.of());
        for (String entry : oldEntries) {
            scoreboard.resetScores(entry);
        }

        List<String> newEntries = new ArrayList<>();
        List<String> lines = definition.getAnimatedLines(ticks);
        int visibleLines = Math.min(lines.size(), MAX_LINES);
        int score = visibleLines;

        for (int index = 0; index < MAX_LINES; index++) {
            String entry = makeLineEntry(index);
            Team team = getOrCreateLineTeam(scoreboard, index, entry);

            if (index < visibleLines) {
                String rawLine = lines.get(index);
                String line = plugin.getRankManager().applyPlaceholders(player, rawLine)
                        .replace("%karamsmp_scoreboard%", definition.getId())
                        .replace("%karamsmp_scoreboard_id%", definition.getId());
                setTeamLineSafely(team, line);
                objective.getScore(entry).setScore(score--);
                newEntries.add(entry);
            } else {
                setTeamLineSafely(team, "");
                scoreboard.resetScores(entry);
            }
        }

        previousEntries.put(player.getUniqueId(), newEntries);
        if (player.getScoreboard() != scoreboard) {
            player.setScoreboard(scoreboard);
        }

        // Re-apply rank teams on the player's personal scoreboard so TAB sorting/name tags keep working.
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
        yaml.set("title-animation.frames", List.of("&#00D5FF&lKaramSMP", "&#56FFB1&lKaramSMP"));
        yaml.set("lines-animation.enabled", false);
        yaml.set("lines-animation.interval-ticks", 20);
        yaml.set("lines", List.of("", "&#E6E6E6Player &#00D5FF%player%", "&#E6E6E6Rank %karamsmp_ranks_prefix%", "&#E6E6E6Kills &#FF5555%kills%", "&#E6E6E6Deaths &#FFAA00%deaths%", "&#E6E6E6Playtime &#FFFF55%playtime%", "", "&#FFD84Dplay.karamsmp.net"));
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

    private Scoreboard getOrCreateScoreboard(Player player) {
        Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
        if (scoreboard == null) {
            scoreboard = player.getScoreboard();
            if (Bukkit.getScoreboardManager() != null && (scoreboard == null || scoreboard == Bukkit.getScoreboardManager().getMainScoreboard())) {
                scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            }
            playerScoreboards.put(player.getUniqueId(), scoreboard);
        }
        return scoreboard;
    }

    private Team getOrCreateLineTeam(Scoreboard scoreboard, int index, String entry) {
        String teamName = LINE_TEAM_PREFIX + String.format("%02d", index);
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
        return team;
    }

    private void clearAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearPlayer(player);
        }
    }

    private void clearSidebar(Player player) {
        Scoreboard scoreboard = playerScoreboards.getOrDefault(player.getUniqueId(), player.getScoreboard());
        if (scoreboard == null) {
            return;
        }
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            objective.unregister();
        }
        previousEntries.remove(player.getUniqueId());
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith(LINE_TEAM_PREFIX)) {
                team.unregister();
            }
        }
    }

    private void ensureDefaultFiles() {
        File folder = getScoreboardsFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create scoreboards folder: " + folder.getAbsolutePath());
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

    private String makeLineEntry(int index) {
        return ChatColor.values()[index].toString() + ChatColor.RESET;
    }

    private void setTeamLineSafely(Team team, String value) {
        String[] parts = MessageUtil.splitForTeam(value, 64, 64);
        try {
            team.setPrefix(parts[0]);
            team.setSuffix(parts[1]);
        } catch (IllegalArgumentException exception) {
            String[] shorter = MessageUtil.splitForTeam(value, 48, 48);
            team.setPrefix(shorter[0]);
            team.setSuffix(shorter[1]);
        }
    }

    private String trim(String input, int maxLength) {
        return MessageUtil.safeLegacySubstring(input, maxLength);
    }
}

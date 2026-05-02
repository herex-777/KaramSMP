package me.herex.karmsmp.scoreboards;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.managers.Rank;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class KaramScoreboardManager {

    private static final String OBJECTIVE_NAME = "ksmp";
    private static final String LINE_TEAM_PREFIX = "ksmpl_";
    private static final String RANK_TEAM_PREFIX = "ksmpr_";

    private final KaramSMP plugin;
    private final Map<String, ScoreboardDefinition> scoreboards = new LinkedHashMap<>();
    private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();
    private final Map<UUID, String> activeScoreboards = new HashMap<>();
    private BukkitTask task;
    private int tick;
    private File folder;

    public KaramScoreboardManager(KaramSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        load();

        if (!isEnabled()) {
            clearAllPlayers();
            return;
        }

        long interval = Math.max(1L, plugin.getConfig().getLong("scoreboards.update-interval-ticks", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick += (int) interval;
            updateAllPlayers();
        }, 0L, interval);
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

    public void load() {
        scoreboards.clear();
        ensureFolderAndDefaults();

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml") || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            try {
                ScoreboardDefinition definition = ScoreboardDefinition.fromFile(file);
                if (!definition.getId().isBlank()) {
                    scoreboards.put(definition.getId(), definition);
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("Could not load scoreboard file " + file.getName() + ": " + exception.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + scoreboards.size() + " KaramSMP scoreboard(s).");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("scoreboards.enabled", true);
    }

    public void updateAllPlayers() {
        if (!isEnabled()) {
            clearAllPlayers();
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    public void updatePlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Optional<ScoreboardDefinition> optional = findScoreboard(player);
        if (optional.isEmpty()) {
            if (plugin.getConfig().getBoolean("scoreboards.clear-when-no-match", plugin.getConfig().getBoolean("scoreboards.reset-when-no-match", true))) {
                clearPlayer(player);
            }
            return;
        }

        ScoreboardDefinition definition = optional.get();
        Scoreboard scoreboard = getOrCreatePlayerBoard(player, definition.getId());
        if (scoreboard == null) {
            return;
        }
        render(player, scoreboard, definition);
        player.setScoreboard(scoreboard);
        activeScoreboards.put(player.getUniqueId(), definition.getId());
    }

    public void clearPlayer(Player player) {
        if (player == null) {
            return;
        }
        playerBoards.remove(player.getUniqueId());
        activeScoreboards.remove(player.getUniqueId());
        if (Bukkit.getScoreboardManager() != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void clearAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearPlayer(player);
        }
        playerBoards.clear();
        activeScoreboards.clear();
    }

    public Optional<ScoreboardDefinition> findScoreboard(Player player) {
        return scoreboards.values().stream()
                .filter(definition -> definition.matches(player, plugin))
                .max(Comparator.comparingInt(ScoreboardDefinition::getPriority).thenComparing(ScoreboardDefinition::getId));
    }

    public String getActiveScoreboardId(Player player) {
        if (player == null) {
            return "none";
        }
        return activeScoreboards.getOrDefault(player.getUniqueId(), findScoreboard(player).map(ScoreboardDefinition::getId).orElse("none"));
    }

    public Optional<ScoreboardDefinition> getScoreboard(String id) {
        return Optional.ofNullable(scoreboards.get(ScoreboardDefinition.cleanId(id)));
    }

    public List<ScoreboardDefinition> getScoreboards() {
        return scoreboards.values().stream()
                .sorted(Comparator.comparingInt(ScoreboardDefinition::getPriority).reversed().thenComparing(ScoreboardDefinition::getId))
                .toList();
    }

    public boolean createScoreboard(String id) {
        String cleanId = ScoreboardDefinition.cleanId(id);
        if (cleanId.isBlank() || getScoreboard(cleanId).isPresent()) {
            return false;
        }

        File file = getScoreboardFile(cleanId);
        if (file.exists()) {
            return false;
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("id", cleanId);
        config.set("enabled", true);
        config.set("priority", 0);
        config.set("permission", "");
        config.set("worlds", new ArrayList<String>());
        config.set("regions", new ArrayList<String>());
        config.set("title.frames", List.of("&6&lKaramSMP", "&e&lKaramSMP"));
        config.set("animation.title-speed-ticks", 10);
        config.set("animation.line-speed-ticks", 20);
        config.set("lines", List.of(
                "&7&m----------------",
                "&fPlayer: &e%player%",
                "&fRank: %karamsmp_ranks_prefix%",
                "&fWorld: &e%world%",
                "&fRegion: &e%karamsmp_region%",
                "&7&m----------------"
        ));
        return saveAndReload(file, config);
    }

    public boolean deleteScoreboard(String id) {
        Optional<ScoreboardDefinition> optional = getScoreboard(id);
        if (optional.isEmpty()) {
            return false;
        }
        boolean deleted = optional.get().getSourceFile().delete();
        reload();
        return deleted;
    }

    public boolean setEnabled(String id, boolean enabled) {
        return edit(id, config -> config.set("enabled", enabled));
    }

    public boolean setPermission(String id, String permission) {
        return edit(id, config -> config.set("permission", permission == null || permission.equalsIgnoreCase("none") ? "" : permission));
    }

    public boolean setPriority(String id, int priority) {
        return edit(id, config -> config.set("priority", priority));
    }

    public boolean setTitle(String id, String title) {
        return edit(id, config -> {
            config.set("title.frames", null);
            config.set("title", title);
        });
    }

    public boolean addWorld(String id, String world) {
        return addToList(id, "worlds", world);
    }

    public boolean removeWorld(String id, String world) {
        return removeFromList(id, "worlds", world);
    }

    public boolean addRegion(String id, String region) {
        return addToList(id, "regions", region);
    }

    public boolean removeRegion(String id, String region) {
        return removeFromList(id, "regions", region);
    }

    public boolean addLine(String id, String line) {
        Optional<ScoreboardDefinition> optional = getScoreboard(id);
        if (optional.isEmpty()) {
            return false;
        }
        File file = optional.get().getSourceFile();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<String> lines = new ArrayList<>(config.getStringList("lines"));
        if (lines.size() >= 15) {
            return false;
        }
        lines.add(line);
        config.set("lines", lines);
        return saveAndReload(file, config);
    }

    public boolean setLine(String id, int lineNumber, String line) {
        Optional<ScoreboardDefinition> optional = getScoreboard(id);
        if (optional.isEmpty()) {
            return false;
        }
        File file = optional.get().getSourceFile();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<String> lines = new ArrayList<>(config.getStringList("lines"));
        int index = lineNumber - 1;
        if (index < 0 || index >= lines.size()) {
            return false;
        }
        lines.set(index, line);
        config.set("lines", lines);
        return saveAndReload(file, config);
    }

    public boolean removeLine(String id, int lineNumber) {
        Optional<ScoreboardDefinition> optional = getScoreboard(id);
        if (optional.isEmpty()) {
            return false;
        }
        File file = optional.get().getSourceFile();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<String> lines = new ArrayList<>(config.getStringList("lines"));
        int index = lineNumber - 1;
        if (index < 0 || index >= lines.size()) {
            return false;
        }
        lines.remove(index);
        config.set("lines", lines);
        return saveAndReload(file, config);
    }

    private interface ConfigEdit {
        void apply(YamlConfiguration config);
    }

    private boolean edit(String id, ConfigEdit edit) {
        Optional<ScoreboardDefinition> optional = getScoreboard(id);
        if (optional.isEmpty()) {
            return false;
        }
        File file = optional.get().getSourceFile();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        edit.apply(config);
        return saveAndReload(file, config);
    }

    private boolean addToList(String id, String path, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return edit(id, config -> {
            List<String> values = new ArrayList<>(config.getStringList(path));
            if (values.stream().noneMatch(saved -> saved.equalsIgnoreCase(value))) {
                values.add(value);
            }
            config.set(path, values);
        });
    }

    private boolean removeFromList(String id, String path, String value) {
        return edit(id, config -> {
            List<String> values = new ArrayList<>(config.getStringList(path));
            values.removeIf(saved -> saved.equalsIgnoreCase(value));
            config.set(path, values);
        });
    }

    private boolean saveAndReload(File file, YamlConfiguration config) {
        try {
            config.save(file);
            reload();
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save scoreboard file " + file.getName() + ": " + exception.getMessage());
            return false;
        }
    }

    private Scoreboard getOrCreatePlayerBoard(Player player, String definitionId) {
        UUID uuid = player.getUniqueId();
        String active = activeScoreboards.get(uuid);
        Scoreboard board = playerBoards.get(uuid);
        if (board == null || active == null || !active.equals(definitionId)) {
            if (Bukkit.getScoreboardManager() == null) {
                return null;
            }
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            playerBoards.put(uuid, board);
            activeScoreboards.put(uuid, definitionId);
        }
        return board;
    }

    private void render(Player viewer, Scoreboard scoreboard, ScoreboardDefinition definition) {
        clearOldSidebar(scoreboard);
        applyRankTeams(scoreboard);

        String title = definition.renderTitle(viewer, plugin, tick);
        Objective objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, "dummy", title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = definition.renderLines(viewer, plugin, tick);
        int score = lines.size();
        for (int index = 0; index < lines.size() && index < 15; index++) {
            String entry = uniqueEntry(index);
            Team team = scoreboard.registerNewTeam(LINE_TEAM_PREFIX + index);
            team.addEntry(entry);
            team.setPrefix(lines.get(index));
            objective.getScore(entry).setScore(score--);
        }
    }

    private void clearOldSidebar(Scoreboard scoreboard) {
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            objective.unregister();
        }
        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            if (team.getName().startsWith(LINE_TEAM_PREFIX) || team.getName().startsWith(RANK_TEAM_PREFIX)) {
                team.unregister();
            }
        }
    }

    private void applyRankTeams(Scoreboard scoreboard) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            Rank rank = plugin.getRankManager().getRank(target);
            String teamName = createRankTeamName(rank);
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }
            team.setPrefix(MessageUtil.color(rank.getPrefix()));
            team.setSuffix(MessageUtil.color(rank.getSuffix()));
            team.addEntry(target.getName());
        }
    }

    private String createRankTeamName(Rank rank) {
        String cleanName = rank.getName().replaceAll("[^A-Za-z0-9_]", "").toLowerCase(Locale.ROOT);
        if (cleanName.isBlank()) {
            cleanName = "rank";
        }
        String priority = String.format("%03d", Math.max(0, Math.min(999, rank.getPriority())));
        String teamName = RANK_TEAM_PREFIX + priority + cleanName;
        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }
        return teamName;
    }

    private String uniqueEntry(int index) {
        ChatColor[] values = ChatColor.values();
        return values[Math.floorMod(index, values.length)].toString() + ChatColor.RESET;
    }

    private void ensureFolderAndDefaults() {
        folder = new File(plugin.getDataFolder(), plugin.getConfig().getString("scoreboards.folder", "scoreboards"));
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create scoreboards folder.");
        }

        if (!hasScoreboardFiles()) {
            saveDefaultScoreboard("scoreboards/default.yml");
            saveDefaultScoreboard("scoreboards/spawn.yml");
            saveDefaultScoreboard("scoreboards/staff.yml");
            saveDefaultScoreboard("scoreboards/nether.yml");
        }
    }

    private boolean hasScoreboardFiles() {
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml") || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
        return files != null && files.length > 0;
    }

    private void saveDefaultScoreboard(String resourcePath) {
        String fileName = new File(resourcePath).getName();
        File target = new File(folder, fileName);
        if (target.exists()) {
            return;
        }

        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            if (inputStream != null) {
                Files.copy(inputStream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not copy default scoreboard " + fileName + ": " + exception.getMessage());
        }

        YamlConfiguration fallback = new YamlConfiguration();
        String id = fileName.replaceFirst("(?i)\\.ya?ml$", "");
        fallback.set("id", id);
        fallback.set("enabled", true);
        fallback.set("priority", 0);
        fallback.set("permission", "");
        fallback.set("worlds", new ArrayList<String>());
        fallback.set("regions", new ArrayList<String>());
        fallback.set("title.frames", List.of("&6&lKaramSMP", "&e&lKaramSMP"));
        fallback.set("lines", List.of("&7&m----------------", "&fPlayer: &e%player%", "&fRank: %karamsmp_ranks_prefix%", "&7&m----------------"));
        try {
            fallback.save(target);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not create default scoreboard " + fileName + ": " + exception.getMessage());
        }
    }

    private File getScoreboardFile(String id) {
        ensureFolderAndDefaults();
        return new File(folder, ScoreboardDefinition.cleanId(id) + ".yml");
    }
}

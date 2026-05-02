package me.herex.karmsmp.managers;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class PlayerDisplayManager {

    private static final String TEAM_PREFIX = "ks_";
    private static final String OLD_TEAM_PREFIX = "ksmp_";

    private final KaramSMP plugin;
    private final RankManager rankManager;

    public PlayerDisplayManager(KaramSMP plugin, RankManager rankManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
    }

    public void updateAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerNames(player);
        }

        if (plugin.getConfig().getBoolean("name-format.name-tag.enabled", true)
                || plugin.getConfig().getBoolean("tab.force-rank-priority-sorting", true)) {
            updateNameTagsForAllViewers();
        }
    }

    public void updatePlayer(Player player) {
        updatePlayerNames(player);

        if (plugin.getConfig().getBoolean("name-format.name-tag.enabled", true)
                || plugin.getConfig().getBoolean("tab.force-rank-priority-sorting", true)) {
            updateNameTagsForAllViewers();
        }
    }

    public void updateNameTagsForAllViewers() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updateNameTagsForViewer(viewer);
        }
    }

    public void updateNameTagsForViewer(Player viewer) {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }

        Scoreboard scoreboard = viewer.getScoreboard();
        if (scoreboard == null) {
            scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            viewer.setScoreboard(scoreboard);
        }

        for (Player target : Bukkit.getOnlinePlayers()) {
            removeFromKaramSMPTeams(scoreboard, target);
        }

        for (Player target : Bukkit.getOnlinePlayers()) {
            Rank rank = rankManager.getRank(target);
            Team team = getOrCreateTeam(scoreboard, rank);
            team.addEntry(target.getName());
        }
    }

    private void updatePlayerNames(Player player) {
        String displayNameFormat = plugin.getConfig().getString("name-format.display-name", "%prefix%%player%%suffix%");
        String tabNameFormat = plugin.getConfig().getString("name-format.tab-list", "%prefix%%player%%suffix%");

        player.setDisplayName(MessageUtil.safeLegacySubstring(rankManager.applyPlaceholders(player, displayNameFormat), 128));
        player.setPlayerListName(MessageUtil.safeLegacySubstring(rankManager.applyPlaceholders(player, tabNameFormat), 128));
    }

    private Team getOrCreateTeam(Scoreboard scoreboard, Rank rank) {
        String teamName = createTeamName(rank);
        Team team = scoreboard.getTeam(teamName);

        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        if (plugin.getConfig().getBoolean("name-format.name-tag.enabled", true)) {
            team.setPrefix(MessageUtil.safeLegacySubstring(rank.getPrefix(), 64));
            team.setSuffix(MessageUtil.safeLegacySubstring(rank.getSuffix(), 64));
        } else {
            team.setPrefix("");
            team.setSuffix("");
        }
        return team;
    }

    private void removeFromKaramSMPTeams(Scoreboard scoreboard, Player player) {
        for (Team team : scoreboard.getTeams()) {
            if ((team.getName().startsWith(TEAM_PREFIX) || team.getName().startsWith(OLD_TEAM_PREFIX))
                    && team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }
    }

    private String createTeamName(Rank rank) {
        String cleanName = rank.getName().replaceAll("[^A-Za-z0-9_]", "").toLowerCase();
        if (cleanName.isBlank()) {
            cleanName = "rank";
        }

        int clampedPriority = Math.max(0, Math.min(999, rank.getPriority()));
        int sortOrder = 999 - clampedPriority;
        String priority = String.format("%03d", sortOrder);
        String teamName = TEAM_PREFIX + priority + "_" + cleanName;

        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }

        return teamName;
    }
}

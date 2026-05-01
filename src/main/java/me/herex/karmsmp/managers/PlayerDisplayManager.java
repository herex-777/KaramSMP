package me.herex.karmsmp.managers;

import me.herex.karmsmp.KaramSMP;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class PlayerDisplayManager {

    private static final String TEAM_PREFIX = "ksmp_";

    private final KaramSMP plugin;
    private final RankManager rankManager;

    public PlayerDisplayManager(KaramSMP plugin, RankManager rankManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
    }

    public void updateAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    public void updatePlayer(Player player) {
        String displayNameFormat = plugin.getConfig().getString("name-format.display-name", "%prefix%%player%%suffix%");
        String tabNameFormat = plugin.getConfig().getString("name-format.tab-list", "%prefix%%player%%suffix%");

        player.setDisplayName(rankManager.applyPlaceholders(player, displayNameFormat));
        player.setPlayerListName(rankManager.applyPlaceholders(player, tabNameFormat));

        if (plugin.getConfig().getBoolean("name-format.name-tag.enabled", true)) {
            applyNameTag(player);
        }
    }

    private void applyNameTag(Player player) {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        removeFromKaramSMPTeams(scoreboard, player);

        Rank rank = rankManager.getRank(player);
        String teamName = createTeamName(rank);
        Team team = scoreboard.getTeam(teamName);

        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        team.setPrefix(ChatColor.translateAlternateColorCodes('&', rank.getPrefix()));
        team.setSuffix(ChatColor.translateAlternateColorCodes('&', rank.getSuffix()));
        team.addEntry(player.getName());
        player.setScoreboard(scoreboard);
    }

    private void removeFromKaramSMPTeams(Scoreboard scoreboard, Player player) {
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX) && team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }
    }

    private String createTeamName(Rank rank) {
        String cleanName = rank.getName().replaceAll("[^A-Za-z0-9_]", "").toLowerCase();
        if (cleanName.isBlank()) {
            cleanName = "rank";
        }

        String priority = String.format("%03d", Math.max(0, Math.min(999, rank.getPriority())));
        String teamName = TEAM_PREFIX + priority + "_" + cleanName;

        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }

        return teamName;
    }
}

package me.herex.karmsmp.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.managers.Rank;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class KaramSMPPlaceholderExpansion extends PlaceholderExpansion {

    private final KaramSMP plugin;

    public KaramSMPPlaceholderExpansion(KaramSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "karamsmp";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        Rank rank = plugin.getRankManager().getRank(player);
        String key = params.toLowerCase();

        return switch (key) {
            case "rank", "rank_name", "ranks_name" -> rank.getName();
            case "saved_rank" -> plugin.getRankManager().getSavedRankName(player.getUniqueId()) == null ? "" : plugin.getRankManager().getSavedRankName(player.getUniqueId());
            case "prefix", "rank_prefix", "ranks_prefix" -> MessageUtil.color(rank.getPrefix());
            case "suffix", "rank_suffix", "ranks_suffix" -> MessageUtil.color(rank.getSuffix());
            case "rank_priority", "ranks_priority" -> String.valueOf(rank.getPriority());
            case "rank_permission", "ranks_permission" -> rank.getPermission() == null ? "" : rank.getPermission();
            case "rank_permissions", "ranks_permissions" -> String.join(", ", rank.getPermissions());
            case "player" -> player.getName();
            case "displayname" -> player.getDisplayName();
            case "online" -> String.valueOf(Bukkit.getOnlinePlayers().size());
            case "max_players" -> String.valueOf(Bukkit.getMaxPlayers());
            case "world" -> player.getWorld().getName();
            case "gamemode" -> player.getGameMode().name();
            default -> null;
        };
    }
}

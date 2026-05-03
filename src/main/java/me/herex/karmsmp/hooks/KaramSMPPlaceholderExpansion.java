package me.herex.karmsmp.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.managers.Rank;
import me.herex.karmsmp.settings.SettingOption;
import me.herex.karmsmp.settings.SettingsManager;
import me.herex.karmsmp.utils.MessageUtil;
import me.herex.karmsmp.utils.PlayerStatsUtil;
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

        if (plugin.getSettingsManager() != null && key.startsWith("setting_")) {
            String settingKey = key.substring("setting_".length());
            boolean enabledOnly = settingKey.endsWith("_enabled");
            if (enabledOnly) {
                settingKey = settingKey.substring(0, settingKey.length() - "_enabled".length());
            }
            for (SettingOption option : plugin.getSettingsManager().getOptions()) {
                if (SettingsManager.placeholderKey(option.getId()).equals(settingKey)) {
                    return enabledOnly
                            ? String.valueOf(plugin.getSettingsManager().isEnabled(player, option.getId()))
                            : plugin.getSettingsManager().getStatusText(player, option.getId());
                }
            }
        }

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
            case "kills", "player_kills" -> String.valueOf(PlayerStatsUtil.getKills(player));
            case "deaths", "player_deaths" -> String.valueOf(PlayerStatsUtil.getDeaths(player));
            case "mob_kills", "mobs_killed" -> String.valueOf(PlayerStatsUtil.getMobKills(player));
            case "playtime" -> PlayerStatsUtil.getPlaytime(player);
            case "playtime_ticks" -> String.valueOf(PlayerStatsUtil.getPlayTicks(player));
            case "ping" -> String.valueOf(PlayerStatsUtil.getPing(player));
            case "balance", "balance_formatted", "money" -> plugin.getEconomyManager() == null ? "$0" : plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player));
            case "balance_plain", "money_plain" -> plugin.getEconomyManager() == null ? "0" : plugin.getEconomyManager().formatPlain(plugin.getEconomyManager().getBalance(player));
            case "shards", "shards_formatted" -> plugin.getShardManager() == null ? "0" : plugin.getShardManager().format(plugin.getShardManager().getShards(player));
            case "shards_plain" -> plugin.getShardManager() == null ? "0" : plugin.getShardManager().formatPlain(plugin.getShardManager().getShards(player));
            case "afk" -> plugin.getAfkManager() == null ? "false" : String.valueOf(plugin.getAfkManager().isAfk(player));
            case "region", "top_region", "regions_top" -> plugin.getRegionManager() == null ? "none" : plugin.getRegionManager().getTopRegionPlaceholder(player);
            case "regions", "region_list", "regions_list" -> plugin.getRegionManager() == null ? "none" : plugin.getRegionManager().getRegionsPlaceholder(player);
            case "region_count", "regions_count" -> plugin.getRegionManager() == null ? "0" : plugin.getRegionManager().getRegionCountPlaceholder(player);
            case "scoreboard", "scoreboard_id" -> plugin.getScoreboardManager() == null ? "none" : plugin.getScoreboardManager().getActiveScoreboardId(player);
            case "homes", "home_count" -> plugin.getHomeManager() == null ? "0" : String.valueOf(plugin.getHomeManager().getHomes(player).size());
            case "max_homes", "homes_max" -> plugin.getHomeManager() == null ? "0" : String.valueOf(plugin.getHomeManager().getMaxHomes(player));
            case "spawn_set" -> plugin.getSpawnManager() == null ? "false" : String.valueOf(plugin.getSpawnManager().hasSpawn());
            case "auction_items", "auction_listings" -> plugin.getAuctionHouseManager() == null ? "0" : String.valueOf(plugin.getAuctionHouseManager().getActiveAuctionCount());
            case "my_auction_items", "my_auction_listings" -> plugin.getAuctionHouseManager() == null ? "0" : String.valueOf(plugin.getAuctionHouseManager().getPlayerAuctionCount(player.getUniqueId()));
            default -> null;
        };
    }
}

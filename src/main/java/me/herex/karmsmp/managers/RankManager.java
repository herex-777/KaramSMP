package me.herex.karmsmp.managers;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.hooks.PlaceholderAPIUtil;
import me.herex.karmsmp.storage.StorageManager;
import me.herex.karmsmp.settings.SettingOption;
import me.herex.karmsmp.settings.SettingsManager;
import me.herex.karmsmp.utils.MessageUtil;
import me.herex.karmsmp.utils.PlayerStatsUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RankManager {

    private final KaramSMP plugin;
    private final StorageManager storageManager;
    private final List<Rank> ranks = new ArrayList<>();
    private final Map<UUID, String> assignedRanks = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> permissionAttachments = new HashMap<>();

    public RankManager(KaramSMP plugin, StorageManager storageManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
        reload();
    }

    public void reload() {
        ranks.clear();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("ranks");
        if (section != null) {
            for (String rankName : section.getKeys(false)) {
                ConfigurationSection rankSection = section.getConfigurationSection(rankName);
                if (rankSection == null) {
                    continue;
                }

                ranks.add(new Rank(
                        rankName,
                        rankSection.getString("permission", ""),
                        rankSection.getString("prefix", ""),
                        rankSection.getString("suffix", ""),
                        rankSection.getInt("priority", 0),
                        rankSection.getStringList("permissions")
                ));
            }
        }

        if (ranks.isEmpty()) {
            ranks.add(new Rank("member", "", "&7", "", 0, Collections.emptyList()));
        }

        ranks.sort(Comparator.comparingInt(Rank::getPriority).reversed());

        for (Player player : Bukkit.getOnlinePlayers()) {
            applyRankPermissions(player);
        }
    }

    public void loadPlayer(Player player) {
        Optional<String> savedRank = storageManager.getPlayerRank(player.getUniqueId());
        savedRank.ifPresentOrElse(
                rankName -> assignedRanks.put(player.getUniqueId(), rankName),
                () -> assignedRanks.remove(player.getUniqueId())
        );
        applyRankPermissions(player);
    }

    public void unloadPlayer(Player player) {
        assignedRanks.remove(player.getUniqueId());
        PermissionAttachment attachment = permissionAttachments.remove(player.getUniqueId());
        if (attachment != null) {
            player.removeAttachment(attachment);
        }
    }

    public Rank getRank(Player player) {
        String assignedRankName = assignedRanks.get(player.getUniqueId());
        if (assignedRankName != null) {
            Optional<Rank> assignedRank = getRankByName(assignedRankName);
            if (assignedRank.isPresent()) {
                return assignedRank.get();
            }
        }

        if (plugin.getConfig().getBoolean("ranks-settings.op-gets-highest-rank", true) && player.isOp()) {
            String opRankName = plugin.getConfig().getString("ranks-settings.op-rank", "owner");
            Optional<Rank> opRank = getRankByName(opRankName);
            if (opRank.isPresent()) {
                return opRank.get();
            }
        }

        for (Rank rank : ranks) {
            String permission = rank.getPermission();
            if (permission == null || permission.isBlank() || player.hasPermission(permission)) {
                return rank;
            }
        }

        String defaultRankName = plugin.getConfig().getString("ranks-settings.default-rank", "member");
        Optional<Rank> defaultRank = getRankByName(defaultRankName);
        return defaultRank.orElseGet(() -> ranks.get(ranks.size() - 1));
    }

    public Optional<Rank> getRankByName(String rankName) {
        if (rankName == null) {
            return Optional.empty();
        }

        for (Rank rank : ranks) {
            if (rank.getName().equalsIgnoreCase(rankName)) {
                return Optional.of(rank);
            }
        }

        return Optional.empty();
    }

    public List<String> getRankNames() {
        return ranks.stream().map(Rank::getName).toList();
    }

    public String getSavedRankName(UUID uuid) {
        return assignedRanks.get(uuid);
    }

    public void setPlayerRank(Player target, Rank rank) {
        assignedRanks.put(target.getUniqueId(), rank.getName());
        storageManager.setPlayerRank(target.getUniqueId(), target.getName(), rank.getName());
        applyRankPermissions(target);
        plugin.getPlayerDisplayManager().updateAllPlayers();
        plugin.getTabManager().updateAllPlayers();
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().updateAllPlayers();
        }
    }

    public void setOfflinePlayerRank(OfflinePlayer target, Rank rank) {
        storageManager.setPlayerRank(target.getUniqueId(), target.getName() == null ? target.getUniqueId().toString() : target.getName(), rank.getName());
        if (target.getPlayer() != null) {
            setPlayerRank(target.getPlayer(), rank);
        }
    }

    public void clearPlayerRank(Player target) {
        assignedRanks.remove(target.getUniqueId());
        storageManager.removePlayerRank(target.getUniqueId());
        applyRankPermissions(target);
        plugin.getPlayerDisplayManager().updateAllPlayers();
        plugin.getTabManager().updateAllPlayers();
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().updateAllPlayers();
        }
    }

    public void clearOfflinePlayerRank(OfflinePlayer target) {
        storageManager.removePlayerRank(target.getUniqueId());
        if (target.getPlayer() != null) {
            clearPlayerRank(target.getPlayer());
        }
    }

    public boolean createRank(String name, String permission, int priority) {
        String key = cleanRankKey(name);
        if (key.isBlank() || getRankByName(key).isPresent()) {
            return false;
        }

        String path = "ranks." + key;
        plugin.getConfig().set(path + ".permission", permission == null ? "" : permission);
        plugin.getConfig().set(path + ".prefix", "&7[" + key + "] &r");
        plugin.getConfig().set(path + ".suffix", "");
        plugin.getConfig().set(path + ".priority", priority);
        plugin.getConfig().set(path + ".permissions", new ArrayList<String>());
        plugin.saveConfig();
        reload();
        return true;
    }

    public boolean deleteRank(String name) {
        Optional<Rank> rank = getRankByName(name);
        if (rank.isEmpty()) {
            return false;
        }

        plugin.getConfig().set("ranks." + rank.get().getName(), null);
        plugin.saveConfig();
        reload();
        return true;
    }

    public boolean setRankPrefix(String name, String prefix) {
        Optional<Rank> rank = getRankByName(name);
        if (rank.isEmpty()) {
            return false;
        }

        plugin.getConfig().set("ranks." + rank.get().getName() + ".prefix", prefix);
        plugin.saveConfig();
        reload();
        plugin.getPlayerDisplayManager().updateAllPlayers();
        plugin.getTabManager().updateAllPlayers();
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().updateAllPlayers();
        }
        return true;
    }

    public boolean setRankSuffix(String name, String suffix) {
        Optional<Rank> rank = getRankByName(name);
        if (rank.isEmpty()) {
            return false;
        }

        plugin.getConfig().set("ranks." + rank.get().getName() + ".suffix", suffix);
        plugin.saveConfig();
        reload();
        plugin.getPlayerDisplayManager().updateAllPlayers();
        plugin.getTabManager().updateAllPlayers();
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().updateAllPlayers();
        }
        return true;
    }

    public boolean addPermissionToRank(String name, String permission) {
        Optional<Rank> rank = getRankByName(name);
        if (rank.isEmpty() || permission == null || permission.isBlank()) {
            return false;
        }

        String path = "ranks." + rank.get().getName() + ".permissions";
        List<String> permissions = new ArrayList<>(plugin.getConfig().getStringList(path));
        if (!permissions.contains(permission)) {
            permissions.add(permission);
        }
        plugin.getConfig().set(path, permissions);
        plugin.saveConfig();
        reload();
        return true;
    }

    public boolean removePermissionFromRank(String name, String permission) {
        Optional<Rank> rank = getRankByName(name);
        if (rank.isEmpty() || permission == null || permission.isBlank()) {
            return false;
        }

        String path = "ranks." + rank.get().getName() + ".permissions";
        List<String> permissions = new ArrayList<>(plugin.getConfig().getStringList(path));
        boolean removed = permissions.removeIf(saved -> saved.equalsIgnoreCase(permission));
        plugin.getConfig().set(path, permissions);
        plugin.saveConfig();
        reload();
        return removed;
    }

    public void applyRankPermissions(Player player) {
        PermissionAttachment attachment = permissionAttachments.computeIfAbsent(player.getUniqueId(), uuid -> player.addAttachment(plugin));
        for (String permission : new ArrayList<>(attachment.getPermissions().keySet())) {
            attachment.unsetPermission(permission);
        }

        Rank rank = getRank(player);
        for (String permission : rank.getPermissions()) {
            if (permission == null || permission.isBlank()) {
                continue;
            }

            if (permission.startsWith("-")) {
                attachment.setPermission(permission.substring(1), false);
            } else {
                attachment.setPermission(permission, true);
            }
        }
        player.recalculatePermissions();
    }

    public String getPrefix(Player player) {
        return MessageUtil.color(getRank(player).getPrefix());
    }

    public String getSuffix(Player player) {
        return MessageUtil.color(getRank(player).getSuffix());
    }

    public String applyPlaceholders(Player player, String text) {
        if (text == null) {
            return "";
        }

        Rank rank = getRank(player);
        String rawPrefix = rank.getPrefix();
        String rawSuffix = rank.getSuffix();
        String coloredPrefix = MessageUtil.color(rawPrefix);
        String coloredSuffix = MessageUtil.color(rawSuffix);
        String assignedRank = assignedRanks.getOrDefault(player.getUniqueId(), "");

        String replaced = text
                .replace("%player%", player.getName())
                .replace("%displayname%", player.getDisplayName())
                .replace("%rank%", rank.getName())
                .replace("%prefix%", rawPrefix)
                .replace("%suffix%", rawSuffix)
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%max_players%", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("%world%", player.getWorld().getName())
                .replace("%gamemode%", player.getGameMode().name())
                .replace("%karamsmp_player%", player.getName())
                .replace("%karamsmp_displayname%", player.getDisplayName())
                .replace("%karamsmp_rank%", rank.getName())
                .replace("%karamsmp_rank_name%", rank.getName())
                .replace("%karamsmp_saved_rank%", assignedRank)
                .replace("%karamsmp_ranks_prefix%", coloredPrefix)
                .replace("%karamsmp_ranks_suffix%", coloredSuffix)
                .replace("%karamsmp_prefix%", coloredPrefix)
                .replace("%karamsmp_suffix%", coloredSuffix)
                .replace("%karamsmp_rank_priority%", String.valueOf(rank.getPriority()))
                .replace("%karamsmp_rank_permission%", rank.getPermission() == null ? "" : rank.getPermission())
                .replace("%karamsmp_rank_permissions%", String.join(", ", rank.getPermissions()))
                .replace("%karamsmp_online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%karamsmp_max_players%", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("%karamsmp_world%", player.getWorld().getName())
                .replace("%karamsmp_gamemode%", player.getGameMode().name())
                .replace("%kills%", String.valueOf(PlayerStatsUtil.getKills(player)))
                .replace("%deaths%", String.valueOf(PlayerStatsUtil.getDeaths(player)))
                .replace("%mob_kills%", String.valueOf(PlayerStatsUtil.getMobKills(player)))
                .replace("%mobs_killed%", String.valueOf(PlayerStatsUtil.getMobKills(player)))
                .replace("%playtime%", PlayerStatsUtil.getPlaytime(player))
                .replace("%playtime_ticks%", String.valueOf(PlayerStatsUtil.getPlayTicks(player)))
                .replace("%ping%", String.valueOf(PlayerStatsUtil.getPing(player)))
                .replace("%karamsmp_kills%", String.valueOf(PlayerStatsUtil.getKills(player)))
                .replace("%karamsmp_deaths%", String.valueOf(PlayerStatsUtil.getDeaths(player)))
                .replace("%karamsmp_mob_kills%", String.valueOf(PlayerStatsUtil.getMobKills(player)))
                .replace("%karamsmp_mobs_killed%", String.valueOf(PlayerStatsUtil.getMobKills(player)))
                .replace("%karamsmp_playtime%", PlayerStatsUtil.getPlaytime(player))
                .replace("%karamsmp_playtime_ticks%", String.valueOf(PlayerStatsUtil.getPlayTicks(player)))
                .replace("%karamsmp_ping%", String.valueOf(PlayerStatsUtil.getPing(player)))
                .replace("%balance%", plugin.getEconomyManager() == null ? "$0" : plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player)))
                .replace("%money%", plugin.getEconomyManager() == null ? "$0" : plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player)))
                .replace("%balance_plain%", plugin.getEconomyManager() == null ? "0" : plugin.getEconomyManager().formatPlain(plugin.getEconomyManager().getBalance(player)))
                .replace("%money_plain%", plugin.getEconomyManager() == null ? "0" : plugin.getEconomyManager().formatPlain(plugin.getEconomyManager().getBalance(player)))
                .replace("%karamsmp_balance%", plugin.getEconomyManager() == null ? "$0" : plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player)))
                .replace("%karamsmp_balance_formatted%", plugin.getEconomyManager() == null ? "$0" : plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player)))
                .replace("%karamsmp_money%", plugin.getEconomyManager() == null ? "$0" : plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player)))
                .replace("%karamsmp_balance_plain%", plugin.getEconomyManager() == null ? "0" : plugin.getEconomyManager().formatPlain(plugin.getEconomyManager().getBalance(player)))
                .replace("%shards%", plugin.getShardManager() == null ? "0" : plugin.getShardManager().format(plugin.getShardManager().getShards(player)))
                .replace("%shards_plain%", plugin.getShardManager() == null ? "0" : plugin.getShardManager().formatPlain(plugin.getShardManager().getShards(player)))
                .replace("%karamsmp_shards%", plugin.getShardManager() == null ? "0" : plugin.getShardManager().format(plugin.getShardManager().getShards(player)))
                .replace("%karamsmp_shards_formatted%", plugin.getShardManager() == null ? "0" : plugin.getShardManager().format(plugin.getShardManager().getShards(player)))
                .replace("%karamsmp_shards_plain%", plugin.getShardManager() == null ? "0" : plugin.getShardManager().formatPlain(plugin.getShardManager().getShards(player)))
                .replace("%afk%", plugin.getAfkManager() == null ? "false" : String.valueOf(plugin.getAfkManager().isAfk(player)))
                .replace("%karamsmp_afk%", plugin.getAfkManager() == null ? "false" : String.valueOf(plugin.getAfkManager().isAfk(player)))
                .replace("%homes%", plugin.getHomeManager() == null ? "0" : String.valueOf(plugin.getHomeManager().getHomes(player).size()))
                .replace("%max_homes%", plugin.getHomeManager() == null ? "0" : String.valueOf(plugin.getHomeManager().getMaxHomes(player)))
                .replace("%karamsmp_homes%", plugin.getHomeManager() == null ? "0" : String.valueOf(plugin.getHomeManager().getHomes(player).size()))
                .replace("%karamsmp_max_homes%", plugin.getHomeManager() == null ? "0" : String.valueOf(plugin.getHomeManager().getMaxHomes(player)))
                .replace("%karamsmp_spawn_set%", plugin.getSpawnManager() == null ? "false" : String.valueOf(plugin.getSpawnManager().hasSpawn()))
                .replace("%clearlag_time%", plugin.getClearLagManager() == null ? "0s" : plugin.getClearLagManager().getFormattedTimeRemaining())
                .replace("%karamsmp_clearlag_time%", plugin.getClearLagManager() == null ? "0s" : plugin.getClearLagManager().getFormattedTimeRemaining())
                .replace("%auction_items%", plugin.getAuctionHouseManager() == null ? "0" : String.valueOf(plugin.getAuctionHouseManager().getActiveAuctionCount()))
                .replace("%auction_listings%", plugin.getAuctionHouseManager() == null ? "0" : String.valueOf(plugin.getAuctionHouseManager().getActiveAuctionCount()))
                .replace("%karamsmp_auction_items%", plugin.getAuctionHouseManager() == null ? "0" : String.valueOf(plugin.getAuctionHouseManager().getActiveAuctionCount()))
                .replace("%karamsmp_auction_listings%", plugin.getAuctionHouseManager() == null ? "0" : String.valueOf(plugin.getAuctionHouseManager().getActiveAuctionCount()))
                .replace("%my_auction_items%", plugin.getAuctionHouseManager() == null ? "0" : String.valueOf(plugin.getAuctionHouseManager().getPlayerAuctionCount(player.getUniqueId())))
                .replace("%karamsmp_my_auction_items%", plugin.getAuctionHouseManager() == null ? "0" : String.valueOf(plugin.getAuctionHouseManager().getPlayerAuctionCount(player.getUniqueId())));

        if (plugin.getScoreboardManager() != null) {
            replaced = replaced
                    .replace("%karamsmp_scoreboard%", plugin.getScoreboardManager().getActiveScoreboardId(player))
                    .replace("%karamsmp_scoreboard_id%", plugin.getScoreboardManager().getActiveScoreboardId(player));
        }

        if (plugin.getRegionManager() != null) {
            replaced = replaced
                    .replace("%karamsmp_region%", plugin.getRegionManager().getTopRegionPlaceholder(player))
                    .replace("%karamsmp_regions%", plugin.getRegionManager().getRegionsPlaceholder(player))
                    .replace("%karamsmp_region_count%", plugin.getRegionManager().getRegionCountPlaceholder(player));
        }

        if (plugin.getSettingsManager() != null) {
            for (SettingOption option : plugin.getSettingsManager().getOptions()) {
                String key = SettingsManager.placeholderKey(option.getId());
                String status = plugin.getSettingsManager().getStatusText(player, option.getId());
                String enabled = String.valueOf(plugin.getSettingsManager().isEnabled(player, option.getId()));
                replaced = replaced
                        .replace("%setting_" + key + "%", status)
                        .replace("%karamsmp_setting_" + key + "%", status)
                        .replace("%setting_" + key + "_enabled%", enabled)
                        .replace("%karamsmp_setting_" + key + "_enabled%", enabled);
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            replaced = PlaceholderAPIUtil.setPlaceholders(player, replaced);
        }

        return MessageUtil.color(replaced);
    }

    private String cleanRankKey(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }
}

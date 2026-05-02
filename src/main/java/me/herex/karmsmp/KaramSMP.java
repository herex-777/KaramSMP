package me.herex.karmsmp;

import me.herex.karmsmp.commands.DiscordCommand;
import me.herex.karmsmp.commands.GameModeCommand;
import me.herex.karmsmp.commands.NightVisionCommand;
import me.herex.karmsmp.commands.RankCommand;
import me.herex.karmsmp.commands.RegionCommand;
import me.herex.karmsmp.commands.ReloadCommand;
import me.herex.karmsmp.commands.ScoreboardCommand;
import me.herex.karmsmp.hooks.KaramSMPPlaceholderExpansion;
import me.herex.karmsmp.listeners.ChatListener;
import me.herex.karmsmp.listeners.DiscordCommandListener;
import me.herex.karmsmp.listeners.PlayerJoinListener;
import me.herex.karmsmp.listeners.RegionListener;
import me.herex.karmsmp.managers.PlayerDisplayManager;
import me.herex.karmsmp.managers.RankManager;
import me.herex.karmsmp.managers.TabManager;
import me.herex.karmsmp.regions.RegionManager;
import me.herex.karmsmp.scoreboards.KaramScoreboardManager;
import me.herex.karmsmp.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class KaramSMP extends JavaPlugin {

    private StorageManager storageManager;
    private RankManager rankManager;
    private PlayerDisplayManager playerDisplayManager;
    private TabManager tabManager;
    private RegionManager regionManager;
    private KaramScoreboardManager karamScoreboardManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        storageManager = new StorageManager(this);
        storageManager.connect();

        rankManager = new RankManager(this, storageManager);
        playerDisplayManager = new PlayerDisplayManager(this, rankManager);
        tabManager = new TabManager(this, rankManager);
        regionManager = new RegionManager(this);
        regionManager.load();
        karamScoreboardManager = new KaramScoreboardManager(this);

        registerCommands();
        registerListeners();
        registerPlaceholderAPI();

        Bukkit.getOnlinePlayers().forEach(rankManager::loadPlayer);
        tabManager.start();
        playerDisplayManager.updateAllPlayers();
        karamScoreboardManager.start();
        sendStartupMessage();
    }

    @Override
    public void onDisable() {
        if (tabManager != null) {
            tabManager.stop();
        }
        if (karamScoreboardManager != null) {
            karamScoreboardManager.stop();
        }
        if (regionManager != null) {
            regionManager.save();
        }
        if (storageManager != null) {
            storageManager.close();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        storageManager.connect();
        rankManager.reload();
        regionManager.reload();
        Bukkit.getOnlinePlayers().forEach(rankManager::loadPlayer);
        tabManager.reload();
        karamScoreboardManager.reload();
        playerDisplayManager.updateAllPlayers();
        karamScoreboardManager.updateAllPlayers();
    }

    private void registerCommands() {
        GameModeCommand gameModeCommand = new GameModeCommand();

        PluginCommand gmspCommand = getCommand("gmsp");
        if (gmspCommand != null) {
            gmspCommand.setExecutor(gameModeCommand);
        }

        PluginCommand nightVisionCommand = getCommand("nightvision");
        if (nightVisionCommand != null) {
            nightVisionCommand.setExecutor(new NightVisionCommand(this));
        }

        PluginCommand discordCommand = getCommand("discord");
        if (discordCommand != null) {
            discordCommand.setExecutor(new DiscordCommand(this));
        }

        PluginCommand reloadCommand = getCommand("reload");
        if (reloadCommand != null) {
            reloadCommand.setExecutor(new ReloadCommand(this));
        }

        PluginCommand rankCommand = getCommand("rank");
        if (rankCommand != null) {
            RankCommand command = new RankCommand(this);
            rankCommand.setExecutor(command);
            rankCommand.setTabCompleter(command);
        }

        PluginCommand regionCommand = getCommand("region");
        if (regionCommand != null) {
            RegionCommand command = new RegionCommand(this, regionManager);
            regionCommand.setExecutor(command);
            regionCommand.setTabCompleter(command);
        }

        PluginCommand scoreboardCommand = getCommand("kscoreboard");
        if (scoreboardCommand != null) {
            ScoreboardCommand command = new ScoreboardCommand(this);
            scoreboardCommand.setExecutor(command);
            scoreboardCommand.setTabCompleter(command);
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this, rankManager, tabManager, playerDisplayManager), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(this, rankManager), this);
        Bukkit.getPluginManager().registerEvents(new DiscordCommandListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RegionListener(this, regionManager), this);
    }

    private void registerPlaceholderAPI() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().info("PlaceholderAPI was not found. KaramSMP internal placeholders still work, but PAPI placeholders are disabled.");
            return;
        }

        KaramSMPPlaceholderExpansion expansion = new KaramSMPPlaceholderExpansion(this);
        if (expansion.register()) {
            getLogger().info("Registered PlaceholderAPI placeholders with identifier %karamsmp_*%.");
        }
    }

    private void sendStartupMessage() {
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "========================================");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "          KaramSMP v" + getDescription().getVersion());
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "     Plugin loaded successfully!");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "     Commands: /gmsp, /nv, /discord, /rank, /region, /kscoreboard, /reload");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "     Made by Herex._.7");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "========================================");
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public PlayerDisplayManager getPlayerDisplayManager() {
        return playerDisplayManager;
    }

    public TabManager getTabManager() {
        return tabManager;
    }

    public RegionManager getRegionManager() {
        return regionManager;
    }

    public KaramScoreboardManager getKaramScoreboardManager() {
        return karamScoreboardManager;
    }
}

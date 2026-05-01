package me.herex.karmsmp;

import me.herex.karmsmp.commands.GameModeCommand;
import me.herex.karmsmp.commands.RankCommand;
import me.herex.karmsmp.commands.ReloadCommand;
import me.herex.karmsmp.hooks.KaramSMPPlaceholderExpansion;
import me.herex.karmsmp.listeners.ChatListener;
import me.herex.karmsmp.listeners.PlayerJoinListener;
import me.herex.karmsmp.managers.PlayerDisplayManager;
import me.herex.karmsmp.managers.RankManager;
import me.herex.karmsmp.managers.TabManager;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();

        storageManager = new StorageManager(this);
        storageManager.connect();

        rankManager = new RankManager(this, storageManager);
        playerDisplayManager = new PlayerDisplayManager(this, rankManager);
        tabManager = new TabManager(this, rankManager);

        registerCommands();
        registerListeners();
        registerPlaceholderAPI();

        Bukkit.getOnlinePlayers().forEach(rankManager::loadPlayer);
        tabManager.start();
        playerDisplayManager.updateAllPlayers();
        sendStartupMessage();
    }

    @Override
    public void onDisable() {
        if (tabManager != null) {
            tabManager.stop();
        }
        if (storageManager != null) {
            storageManager.close();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        storageManager.connect();
        rankManager.reload();
        Bukkit.getOnlinePlayers().forEach(rankManager::loadPlayer);
        tabManager.reload();
        playerDisplayManager.updateAllPlayers();
    }

    private void registerCommands() {
        GameModeCommand gameModeCommand = new GameModeCommand();

        PluginCommand gmspCommand = getCommand("gmsp");
        if (gmspCommand != null) {
            gmspCommand.setExecutor(gameModeCommand);
        }

        PluginCommand gmsCommand = getCommand("gms");
        if (gmsCommand != null) {
            gmsCommand.setExecutor(gameModeCommand);
        }


        PluginCommand reloadCommand = getCommand("reload");
        if (reloadCommand != null) {
            reloadCommand.setExecutor(new ReloadCommand(this));
        }

        PluginCommand rankCommand = getCommand("rank");
        if (rankCommand != null) {
            RankCommand rankExecutor = new RankCommand(this);
            rankCommand.setExecutor(rankExecutor);
            rankCommand.setTabCompleter(rankExecutor);
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this, rankManager, tabManager, playerDisplayManager), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(this, rankManager), this);
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
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "     Commands: /gmsp, /gms, /reload, and /rank");
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
}

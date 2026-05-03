package me.herex.karmsmp;

import me.herex.karmsmp.commands.DiscordCommand;
import me.herex.karmsmp.commands.GameModeCommand;
import me.herex.karmsmp.commands.HomeCommand;
import me.herex.karmsmp.commands.BalanceCommand;
import me.herex.karmsmp.commands.ClearLagCommand;
import me.herex.karmsmp.commands.PayCommand;
import me.herex.karmsmp.commands.InfoCommand;
import me.herex.karmsmp.commands.RankCommand;
import me.herex.karmsmp.commands.RegionCommand;
import me.herex.karmsmp.commands.NightVisionCommand;
import me.herex.karmsmp.commands.ScoreboardCommand;
import me.herex.karmsmp.commands.SettingsCommand;
import me.herex.karmsmp.commands.SpawnCommand;
import me.herex.karmsmp.commands.StatsCommand;
import me.herex.karmsmp.commands.ReloadCommand;
import me.herex.karmsmp.afk.AfkManager;
import me.herex.karmsmp.auction.AuctionHouseManager;
import me.herex.karmsmp.leaderboards.BountyManager;
import me.herex.karmsmp.leaderboards.LeaderboardCommand;
import me.herex.karmsmp.shards.ShardManager;
import me.herex.karmsmp.hooks.KaramSMPPlaceholderExpansion;
import me.herex.karmsmp.economy.EconomyManager;
import me.herex.karmsmp.homes.HomeManager;
import me.herex.karmsmp.listeners.ChatListener;
import me.herex.karmsmp.listeners.DiscordCommandListener;
import me.herex.karmsmp.listeners.DoubleJumpListener;
import me.herex.karmsmp.listeners.HomeListener;
import me.herex.karmsmp.listeners.PlayerJoinListener;
import me.herex.karmsmp.listeners.RegionListener;
import me.herex.karmsmp.listeners.SpawnListener;
import me.herex.karmsmp.managers.PlayerDisplayManager;
import me.herex.karmsmp.managers.RankManager;
import me.herex.karmsmp.managers.TabManager;
import me.herex.karmsmp.managers.ClearLagManager;
import me.herex.karmsmp.regions.RegionManager;
import me.herex.karmsmp.scoreboards.KaramScoreboardManager;
import me.herex.karmsmp.spawn.SpawnManager;
import me.herex.karmsmp.rtp.RandomTeleportManager;
import me.herex.karmsmp.settings.SettingsManager;
import me.herex.karmsmp.shop.ShopManager;
import me.herex.karmsmp.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;

public final class KaramSMP extends JavaPlugin {

    private StorageManager storageManager;
    private RankManager rankManager;
    private PlayerDisplayManager playerDisplayManager;
    private TabManager tabManager;
    private RegionManager regionManager;
    private KaramScoreboardManager scoreboardManager;
    private HomeManager homeManager;
    private SpawnManager spawnManager;
    private EconomyManager economyManager;
    private RandomTeleportManager randomTeleportManager;
    private SettingsManager settingsManager;
    private DiscordCommand discordCommand;
    private ClearLagManager clearLagManager;
    private ShopManager shopManager;
    private ShardManager shardManager;
    private BountyManager bountyManager;
    private AfkManager afkManager;
    private AuctionHouseManager auctionHouseManager;

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
        scoreboardManager = new KaramScoreboardManager(this);
        homeManager = new HomeManager(this);
        homeManager.load();
        spawnManager = new SpawnManager(this);
        spawnManager.load();
        economyManager = new EconomyManager(this);
        economyManager.load();
        shardManager = new ShardManager(this);
        shardManager.load();
        bountyManager = new BountyManager(this);
        bountyManager.load();
        randomTeleportManager = new RandomTeleportManager(this);
        settingsManager = new SettingsManager(this);
        clearLagManager = new ClearLagManager(this);
        shopManager = new ShopManager(this, economyManager);
        auctionHouseManager = new AuctionHouseManager(this, economyManager);
        auctionHouseManager.load();
        afkManager = new AfkManager(this);
        afkManager.load();
        discordCommand = new DiscordCommand(this);

        registerCommands();
        registerListeners();
        registerPlaceholderAPI();

        Bukkit.getOnlinePlayers().forEach(rankManager::loadPlayer);
        Bukkit.getOnlinePlayers().forEach(player -> economyManager.getBalance(player));
        Bukkit.getOnlinePlayers().forEach(player -> shardManager.getShards(player));
        tabManager.start();
        scoreboardManager.start();
        clearLagManager.start();
        auctionHouseManager.start();
        playerDisplayManager.updateAllPlayers();
        sendStartupMessage();
    }

    @Override
    public void onDisable() {
        if (tabManager != null) {
            tabManager.stop();
        }
        if (regionManager != null) {
            regionManager.save();
        }
        if (scoreboardManager != null) {
            scoreboardManager.stop();
        }
        if (homeManager != null) {
            homeManager.stop();
        }
        if (spawnManager != null) {
            spawnManager.save();
        }
        if (randomTeleportManager != null) {
            randomTeleportManager.stop();
        }
        if (clearLagManager != null) {
            clearLagManager.stop();
        }
        if (settingsManager != null) {
            settingsManager.save();
        }
        if (afkManager != null) {
            afkManager.stop();
        }
        if (auctionHouseManager != null) {
            auctionHouseManager.stop();
        }
        if (bountyManager != null) {
            bountyManager.save();
        }
        if (shardManager != null) {
            shardManager.close();
        }
        if (economyManager != null) {
            economyManager.close();
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
        homeManager.load();
        spawnManager.load();
        economyManager.load();
        if (shardManager != null) {
            shardManager.load();
        }
        if (bountyManager != null) {
            bountyManager.load();
        }
        if (afkManager != null) {
            afkManager.reload();
        }
        if (settingsManager != null) {
            settingsManager.reload();
        }
        if (shopManager != null) {
            shopManager.reload();
        }
        if (auctionHouseManager != null) {
            auctionHouseManager.reload();
        }
        Bukkit.getOnlinePlayers().forEach(player -> economyManager.getBalance(player));
        if (shardManager != null) {
            Bukkit.getOnlinePlayers().forEach(player -> shardManager.getShards(player));
        }
        Bukkit.getOnlinePlayers().forEach(rankManager::loadPlayer);
        tabManager.reload();
        scoreboardManager.reload();
        if (clearLagManager != null) {
            clearLagManager.reload();
        }
        playerDisplayManager.updateAllPlayers();
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

        PluginCommand reloadCommand = getCommand("reload");
        if (reloadCommand != null) {
            reloadCommand.setExecutor(new ReloadCommand(this));
        }

        PluginCommand discordPluginCommand = getCommand("discord");
        if (discordPluginCommand != null) {
            discordPluginCommand.setExecutor(discordCommand);
        }

        PluginCommand guideCommand = getCommand("guide");
        if (guideCommand != null) {
            guideCommand.setExecutor(new InfoCommand(this, "guide"));
        }

        PluginCommand storeCommand = getCommand("store");
        if (storeCommand != null) {
            storeCommand.setExecutor(new InfoCommand(this, "store"));
        }

        HomeCommand homeCommand = new HomeCommand(this, homeManager);
        PluginCommand homePluginCommand = getCommand("home");
        if (homePluginCommand != null) {
            homePluginCommand.setExecutor(homeCommand);
            homePluginCommand.setTabCompleter(homeCommand);
        }

        PluginCommand setHomeCommand = getCommand("sethome");
        if (setHomeCommand != null) {
            setHomeCommand.setExecutor(homeCommand);
        }

        PluginCommand delHomeCommand = getCommand("delhome");
        if (delHomeCommand != null) {
            delHomeCommand.setExecutor(homeCommand);
            delHomeCommand.setTabCompleter(homeCommand);
        }

        SpawnCommand spawnCommand = new SpawnCommand(this, spawnManager);
        PluginCommand spawnPluginCommand = getCommand("spawn");
        if (spawnPluginCommand != null) {
            spawnPluginCommand.setExecutor(spawnCommand);
        }
        PluginCommand setSpawnCommand = getCommand("setspawn");
        if (setSpawnCommand != null) {
            setSpawnCommand.setExecutor(spawnCommand);
        }

        BalanceCommand balanceCommand = new BalanceCommand(this, economyManager);
        PluginCommand balancePluginCommand = getCommand("blance");
        if (balancePluginCommand != null) {
            balancePluginCommand.setExecutor(balanceCommand);
            balancePluginCommand.setTabCompleter(balanceCommand);
        }

        StatsCommand statsCommand = new StatsCommand(this);
        PluginCommand statsPluginCommand = getCommand("stats");
        if (statsPluginCommand != null) {
            statsPluginCommand.setExecutor(statsCommand);
            statsPluginCommand.setTabCompleter(statsCommand);
        }
        Bukkit.getPluginManager().registerEvents(statsCommand, this);

        LeaderboardCommand leaderboardCommand = new LeaderboardCommand(this, bountyManager);
        PluginCommand mostMoneyCommand = getCommand("mostmoney");
        if (mostMoneyCommand != null) {
            mostMoneyCommand.setExecutor(leaderboardCommand);
            mostMoneyCommand.setTabCompleter(leaderboardCommand);
        }
        PluginCommand palTopCommand = getCommand("paltop");
        if (palTopCommand != null) {
            palTopCommand.setExecutor(leaderboardCommand);
            palTopCommand.setTabCompleter(leaderboardCommand);
        }
        PluginCommand bountyCommand = getCommand("bounty");
        if (bountyCommand != null) {
            bountyCommand.setExecutor(leaderboardCommand);
            bountyCommand.setTabCompleter(leaderboardCommand);
        }
        Bukkit.getPluginManager().registerEvents(leaderboardCommand, this);

        PayCommand payCommand = new PayCommand(this, economyManager);
        PluginCommand payPluginCommand = getCommand("pay");
        if (payPluginCommand != null) {
            payPluginCommand.setExecutor(payCommand);
            payPluginCommand.setTabCompleter(payCommand);
        }

        PluginCommand rtpCommand = getCommand("rtp");
        if (rtpCommand != null) {
            rtpCommand.setExecutor(randomTeleportManager);
            rtpCommand.setTabCompleter(randomTeleportManager);
        }
        PluginCommand afkCommand = getCommand("afk");
        if (afkCommand != null) {
            afkCommand.setExecutor(afkManager);
            afkCommand.setTabCompleter(afkManager);
        }
        PluginCommand setAfkRoomCommand = getCommand("setafkroom");
        if (setAfkRoomCommand != null) {
            setAfkRoomCommand.setExecutor(afkManager);
            setAfkRoomCommand.setTabCompleter(afkManager);
        }
        Bukkit.getPluginManager().registerEvents(afkManager, this);

        SettingsCommand settingsCommand = new SettingsCommand(this, settingsManager);
        PluginCommand settingsPluginCommand = getCommand("settings");
        if (settingsPluginCommand != null) {
            settingsPluginCommand.setExecutor(settingsCommand);
        }
        Bukkit.getPluginManager().registerEvents(settingsCommand, this);


        PluginCommand shopPluginCommand = getCommand("shop");
        if (shopPluginCommand != null) {
            shopPluginCommand.setExecutor(shopManager);
            shopPluginCommand.setTabCompleter(shopManager);
        }

        PluginCommand adminShopPluginCommand = getCommand("adminshop");
        if (adminShopPluginCommand != null) {
            adminShopPluginCommand.setExecutor(shopManager);
            adminShopPluginCommand.setTabCompleter(shopManager);
        }

        PluginCommand auctionPluginCommand = getCommand("auction");
        if (auctionPluginCommand != null && auctionHouseManager != null) {
            auctionPluginCommand.setExecutor(auctionHouseManager);
            auctionPluginCommand.setTabCompleter(auctionHouseManager);
        }
        PluginCommand sellPluginCommand = getCommand("sell");
        if (sellPluginCommand != null && auctionHouseManager != null) {
            sellPluginCommand.setExecutor(auctionHouseManager);
            sellPluginCommand.setTabCompleter(auctionHouseManager);
        }

        ClearLagCommand clearLagCommand = new ClearLagCommand(this, clearLagManager);
        PluginCommand clearLagPluginCommand = getCommand("clearlag");
        if (clearLagPluginCommand != null) {
            clearLagPluginCommand.setExecutor(clearLagCommand);
            clearLagPluginCommand.setTabCompleter(clearLagCommand);
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
            ScoreboardCommand command = new ScoreboardCommand(this, scoreboardManager);
            scoreboardCommand.setExecutor(command);
            scoreboardCommand.setTabCompleter(command);
        }
    }

    private void registerListeners() {
        registerListenerSafely("join/quit listener", () -> new PlayerJoinListener(this, rankManager, tabManager, playerDisplayManager, scoreboardManager));
        registerListenerSafely("discord command listener", () -> new DiscordCommandListener(this, discordCommand));
        registerListenerSafely("home listener", () -> new HomeListener(this, homeManager));
        registerListenerSafely("spawn listener", () -> new SpawnListener(spawnManager));
        registerListenerSafely("double jump listener", () -> new DoubleJumpListener(this, regionManager));
        registerListenerSafely("random teleport listener", () -> randomTeleportManager);
        registerListenerSafely("chat listener", () -> new ChatListener(this, rankManager));
        registerListenerSafely("region listener", () -> new RegionListener(this, regionManager));
        registerListenerSafely("shop listener", () -> shopManager);
        registerListenerSafely("bounty listener", () -> bountyManager);
        registerListenerSafely("auction house listener", () -> auctionHouseManager);
    }

    private void registerListenerSafely(String name, Supplier<Listener> supplier) {
        try {
            Bukkit.getPluginManager().registerEvents(supplier.get(), this);
        } catch (Throwable throwable) {
            getLogger().warning("Could not register " + name + ": " + throwable.getClass().getSimpleName() + " - " + throwable.getMessage());
        }
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
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "     Commands: /gmsp, /nightvision, /shop, /auction, /sell, /home, /spawn, /stats, /settings, /rtp, /pay, /blance, /mostmoney, /bounty, /afk, /clearlag, /rank, /region, /kscoreboard, /reload, /discord");
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

    public KaramScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public DiscordCommand getDiscordCommand() {
        return discordCommand;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public RandomTeleportManager getRandomTeleportManager() {
        return randomTeleportManager;
    }

    public ClearLagManager getClearLagManager() {
        return clearLagManager;
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public ShardManager getShardManager() {
        return shardManager;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    public AfkManager getAfkManager() {
        return afkManager;
    }

    public AuctionHouseManager getAuctionHouseManager() {
        return auctionHouseManager;
    }
}

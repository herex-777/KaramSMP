package me.herex.karmsmp.homes;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.utils.MessageUtil;
import me.herex.karmsmp.utils.ActionBarUtil;
import me.herex.karmsmp.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class HomeManager {

    private static final int[] HOME_SLOTS = {12, 13, 14, 15, 16};
    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,24}");

    private final KaramSMP plugin;
    private final Map<UUID, Map<String, Home>> homes = new ConcurrentHashMap<>();
    private final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();
    private final Set<UUID> waitingForHomeName = ConcurrentHashMap.newKeySet();
    private final NamespacedKey actionKey;
    private final NamespacedKey homeKey;
    private File file;
    private YamlConfiguration data;

    public HomeManager(KaramSMP plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "home_action");
        this.homeKey = new NamespacedKey(plugin, "home_name");
    }

    public void load() {
        homes.clear();
        if (usesDatabaseStorage()) {
            loadFromDatabase();
        } else {
            loadFromYaml();
        }
    }

    private void loadFromYaml() {
        file = new File(plugin.getDataFolder(), plugin.getConfig().getString("homes.data-file", "homes.yml"));
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not create homes.yml: " + exception.getMessage());
            }
        }

        data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            plugin.getLogger().info("Loaded 0 KaramSMP home(s) from YAML.");
            return;
        }

        int loaded = 0;
        for (String uuidText : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidText);
                ConfigurationSection playerSection = players.getConfigurationSection(uuidText);
                if (playerSection == null) {
                    continue;
                }
                Map<String, Home> playerHomes = new LinkedHashMap<>();
                for (String name : playerSection.getKeys(false)) {
                    ConfigurationSection section = playerSection.getConfigurationSection(name);
                    Location location = readLocation(section);
                    if (location != null) {
                        String cleanName = cleanHomeName(name);
                        playerHomes.put(cleanName.toLowerCase(Locale.ROOT), new Home(cleanName, location));
                        loaded++;
                    }
                }
                homes.put(uuid, playerHomes);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping homes for invalid UUID: " + uuidText);
            }
        }
        plugin.getLogger().info("Loaded " + loaded + " KaramSMP home(s) from YAML.");
    }

    private void loadFromDatabase() {
        int loaded = 0;
        try (Connection connection = openHomeConnection()) {
            createHomeTable(connection);
            String sql = "SELECT uuid, home_name, world, x, y, z, yaw, pitch FROM " + getHomeTableName();
            try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    try {
                        UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                        World world = Bukkit.getWorld(resultSet.getString("world"));
                        if (world == null) {
                            continue;
                        }
                        String cleanName = cleanHomeName(resultSet.getString("home_name"));
                        Location location = new Location(world,
                                resultSet.getDouble("x"),
                                resultSet.getDouble("y"),
                                resultSet.getDouble("z"),
                                (float) resultSet.getDouble("yaw"),
                                (float) resultSet.getDouble("pitch"));
                        getPlayerHomes(uuid).put(cleanName.toLowerCase(Locale.ROOT), new Home(cleanName, location));
                        loaded++;
                    } catch (Exception ignored) {
                        // Skip a corrupted row and keep loading the other homes.
                    }
                }
            }
            plugin.getLogger().info("Loaded " + loaded + " KaramSMP home(s) from " + getHomeStorageName() + ".");
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load homes from " + getHomeStorageName() + ": " + exception.getMessage());
            plugin.getLogger().warning("Falling back to homes.yml for homes.");
            loadFromYaml();
        }
    }

    public void save() {
        if (usesDatabaseStorage()) {
            saveToDatabase();
        } else {
            saveToYaml();
        }
    }

    private void saveToYaml() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), plugin.getConfig().getString("homes.data-file", "homes.yml"));
        }

        YamlConfiguration output = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Home>> playerEntry : homes.entrySet()) {
            for (Home home : playerEntry.getValue().values()) {
                String path = "players." + playerEntry.getKey() + "." + home.getName();
                writeLocation(output.createSection(path), home.getLocation());
            }
        }

        try {
            output.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save homes.yml: " + exception.getMessage());
        }
    }

    private void saveToDatabase() {
        try (Connection connection = openHomeConnection()) {
            createHomeTable(connection);
            connection.setAutoCommit(false);
            try (Statement clear = connection.createStatement()) {
                clear.executeUpdate("DELETE FROM " + getHomeTableName());
            }

            String sql = usesMySQL()
                    ? "INSERT INTO " + getHomeTableName() + " (uuid, username, home_name, world, x, y, z, yaw, pitch, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)"
                    : "INSERT INTO " + getHomeTableName() + " (uuid, username, home_name, world, x, y, z, yaw, pitch, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (Map.Entry<UUID, Map<String, Home>> playerEntry : homes.entrySet()) {
                    String username = Optional.ofNullable(Bukkit.getOfflinePlayer(playerEntry.getKey()).getName()).orElse("");
                    for (Home home : playerEntry.getValue().values()) {
                        Location location = home.getLocation();
                        if (location.getWorld() == null) {
                            continue;
                        }
                        statement.setString(1, playerEntry.getKey().toString());
                        statement.setString(2, username);
                        statement.setString(3, home.getName());
                        statement.setString(4, location.getWorld().getName());
                        statement.setDouble(5, location.getX());
                        statement.setDouble(6, location.getY());
                        statement.setDouble(7, location.getZ());
                        statement.setDouble(8, location.getYaw());
                        statement.setDouble(9, location.getPitch());
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
            }
            connection.commit();
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not save homes to " + getHomeStorageName() + ": " + exception.getMessage());
            saveToYaml();
        }
    }

    public void stop() {
        for (PendingTeleport pending : pendingTeleports.values()) {
            pending.cancelTask();
        }
        pendingTeleports.clear();
        waitingForHomeName.clear();
        save();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("homes.enabled", true);
    }

    public int getMaxHomes(Player player) {
        int base = Math.max(1, plugin.getConfig().getInt("homes.max-homes", 5));
        int best = base;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("homes.permission-limits");
        if (section != null) {
            for (String permission : section.getKeys(false)) {
                if (player.hasPermission(permission)) {
                    best = Math.max(best, section.getInt(permission, base));
                }
            }
        }
        return best;
    }

    public List<Home> getHomes(Player player) {
        return new ArrayList<>(getPlayerHomes(player.getUniqueId()).values()).stream()
                .sorted(Comparator.comparing(Home::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Optional<Home> getHome(Player player, String name) {
        return Optional.ofNullable(getPlayerHomes(player.getUniqueId()).get(cleanHomeName(name).toLowerCase(Locale.ROOT)));
    }

    public boolean setHome(Player player, String rawName) {
        String name = cleanHomeName(rawName);
        if (!isValidHomeName(name)) {
            send(player, "homes.messages.invalid-name", "{prefix} &cᴘʟᴇᴀꜱᴇ ᴜꜱᴇ ᴏɴʟʏ ʟᴇᴛᴛᴇʀꜱ, ɴᴜᴍʙᴇʀꜱ, _ ᴀɴᴅ -.".replace("{prefix}", getPrefix()));
            return false;
        }

        Map<String, Home> playerHomes = getPlayerHomes(player.getUniqueId());
        String key = name.toLowerCase(Locale.ROOT);
        if (!playerHomes.containsKey(key) && playerHomes.size() >= getMaxHomes(player)) {
            send(player, "homes.messages.max-homes", "{prefix} &cʏᴏᴜ ᴀʟʀᴇᴀᴅʏ ʜᴀᴠᴇ ᴛʜᴇ ᴍᴀxɪᴍᴜᴍ ᴏꜰ &e%max% &cʜᴏᴍᴇs!", "%max%", String.valueOf(getMaxHomes(player)));
            return false;
        }
        if (playerHomes.containsKey(key)) {
            send(player, "homes.messages.already-exists", "{prefix} &cᴀ ʜᴏᴍᴇ ɴᴀᴍᴇᴅ &e%home% &cᴀʟʀᴇᴀᴅʏ ᴇxɪsᴛs!", "%home%", name);
            return false;
        }

        int usedAfter = playerHomes.size() + 1;
        playerHomes.put(key, new Home(name, player.getLocation().clone()));
        save();
        send(player, "homes.messages.set", "{prefix} &aʜᴏᴍᴇ &e%home% &aʜᴀs ʙᴇᴇɴ sᴇᴛ! (&7%used%/&7%max% &aᴜsᴇᴅ)",
                "%home%", name, "%used%", String.valueOf(usedAfter), "%max%", String.valueOf(getMaxHomes(player)));
        return true;
    }

    public boolean deleteHome(Player player, String rawName) {
        String name = cleanHomeName(rawName);
        Map<String, Home> playerHomes = getPlayerHomes(player.getUniqueId());
        Home removed = playerHomes.remove(name.toLowerCase(Locale.ROOT));
        if (removed == null) {
            send(player, "homes.messages.not-found", "{prefix} &cɴᴏ ʜᴏᴍᴇ ɴᴀᴍᴇᴅ &e%home% &cꜰᴏᴜɴᴅ!", "%home%", name);
            return false;
        }
        save();
        send(player, "homes.messages.deleted", "{prefix} &aʜᴏᴍᴇ &e%home% &aʜᴀs ʙᴇᴇɴ ᴅᴇʟᴇᴛᴇᴅ.", "%home%", removed.getName());
        return true;
    }

    public void openGui(Player player) {
        if (!isEnabled()) {
            send(player, "homes.messages.disabled", "&cHomes are disabled.");
            return;
        }

        Inventory inventory = Bukkit.createInventory(new HomeGuiHolder(), 36, color("homes.gui.title", "&8• &bʏᴏᴜʀ ʜᴏᴍᴇs"));
        List<Home> playerHomes = getHomes(player);
        int max = Math.min(getMaxHomes(player), HOME_SLOTS.length);
        Material homeMaterial = getMaterial("homes.gui.home-item.material", Material.GRAY_BED);
        Material freeMaterial = getMaterial("homes.gui.free-slot.material", Material.GRAY_BED);
        Material deleteMaterial = getMaterial("homes.gui.delete-item.material", Material.LIGHT_BLUE_DYE);
        Material emptyDeleteMaterial = getMaterial("homes.gui.empty-delete.material", Material.GRAY_DYE);
        Material lockedMaterial = getMaterial("homes.gui.locked-slot.material", Material.RED_BED);

        for (int index = 0; index < HOME_SLOTS.length; index++) {
            int slot = HOME_SLOTS[index];
            if (index < playerHomes.size() && index < max) {
                Home home = playerHomes.get(index);
                inventory.setItem(slot, createGuiItem(homeMaterial,
                        plugin.getConfig().getString("homes.gui.home-item.name", "&e%home%").replace("%home%", home.getName()),
                        replaceList(plugin.getConfig().getStringList("homes.gui.home-item.lore"), "%home%", home.getName()),
                        "teleport", home.getName()));
                inventory.setItem(slot + 9, createGuiItem(deleteMaterial,
                        plugin.getConfig().getString("homes.gui.delete-item.name", "&cᴅᴇʟᴇᴛᴇ").replace("%home%", home.getName()),
                        replaceList(plugin.getConfig().getStringList("homes.gui.delete-item.lore"), "%home%", home.getName()),
                        "delete", home.getName()));
            } else if (index < max) {
                inventory.setItem(slot, createGuiItem(freeMaterial,
                        plugin.getConfig().getString("homes.gui.free-slot.name", "&7ꜰʀᴇᴇ sʟᴏᴛ"),
                        plugin.getConfig().getStringList("homes.gui.free-slot.lore"),
                        "create", ""));
                inventory.setItem(slot + 9, createGuiItem(emptyDeleteMaterial,
                        plugin.getConfig().getString("homes.gui.empty-delete.name", "&7ɴᴏ ʜᴏᴍᴇ"),
                        plugin.getConfig().getStringList("homes.gui.empty-delete.lore"),
                        "none", ""));
            } else {
                inventory.setItem(slot, createGuiItem(lockedMaterial, "&cʟᴏᴄᴋᴇᴅ sʟᴏᴛ", List.of("&7ʏᴏᴜ ɴᴇᴇᴅ ᴀ ʜɪɢʜᴇʀ ʜᴏᴍᴇ ʟɪᴍɪᴛ."), "none", ""));
                inventory.setItem(slot + 9, createGuiItem(emptyDeleteMaterial, "&7ɴᴏ ʜᴏᴍᴇ", List.of("&7ɴᴏ ʜᴏᴍᴇ sᴇᴛ ʜᴇʀᴇ!"), "none", ""));
            }
        }

        player.openInventory(inventory);
    }

    public void openConfirmDeleteGui(Player player, String rawName) {
        String homeName = cleanHomeName(rawName);
        if (getHome(player, homeName).isEmpty()) {
            send(player, "homes.messages.not-found", "{prefix} &cɴᴏ ʜᴏᴍᴇ ɴᴀᴍᴇᴅ &e%home% &cꜰᴏᴜɴᴅ!", "%home%", homeName);
            openGui(player);
            return;
        }

        Inventory inventory = Bukkit.createInventory(new HomeGuiHolder(), 27,
                MessageUtil.color(plugin.getConfig().getString("homes.confirm-delete.title", "&8CONFIRM DELETE")));

        inventory.setItem(plugin.getConfig().getInt("homes.confirm-delete.cancel-slot", 12), createGuiItem(
                getMaterial("homes.confirm-delete.cancel-item.material", Material.RED_STAINED_GLASS_PANE),
                plugin.getConfig().getString("homes.confirm-delete.cancel-item.name", "&cᴄᴀɴᴄᴇʟ"),
                replaceList(plugin.getConfig().getStringList("homes.confirm-delete.cancel-item.lore"), "%home%", homeName),
                "cancel-delete", homeName));
        inventory.setItem(plugin.getConfig().getInt("homes.confirm-delete.home-slot", 14), createGuiItem(
                getMaterial("homes.confirm-delete.home-item.material", Material.GRAY_BED),
                plugin.getConfig().getString("homes.confirm-delete.home-item.name", "&e%home%").replace("%home%", homeName),
                replaceList(plugin.getConfig().getStringList("homes.confirm-delete.home-item.lore"), "%home%", homeName),
                "none", homeName));
        inventory.setItem(plugin.getConfig().getInt("homes.confirm-delete.confirm-slot", 15), createGuiItem(
                getMaterial("homes.confirm-delete.confirm-item.material", Material.LIME_STAINED_GLASS_PANE),
                plugin.getConfig().getString("homes.confirm-delete.confirm-item.name", "&aᴄᴏɴꜰɪʀᴍ"),
                replaceList(plugin.getConfig().getStringList("homes.confirm-delete.confirm-item.lore"), "%home%", homeName),
                "confirm-delete", homeName));

        player.openInventory(inventory);
    }

    public void confirmDelete(Player player, String rawName) {
        String homeName = cleanHomeName(rawName);
        if (deleteHome(player, homeName)) {
            int expLevels = Math.max(0, plugin.getConfig().getInt("homes.confirm-delete.confirm.give-exp-levels", 0));
            if (expLevels > 0) {
                player.giveExpLevels(expLevels);
            }
            playConfiguredSound(player, "homes.confirm-delete.confirm.sound", "ENTITY_PLAYER_LEVELUP", "homes.confirm-delete.confirm.volume", "homes.confirm-delete.confirm.pitch");
        }
        Bukkit.getScheduler().runTask(plugin, () -> openGui(player));
    }

    public void cancelDelete(Player player) {
        playConfiguredSound(player, "homes.confirm-delete.cancel.sound", "BLOCK_PRESSURE_PLATE_CLICK_ON", "homes.confirm-delete.cancel.volume", "homes.confirm-delete.cancel.pitch");
        Bukkit.getScheduler().runTask(plugin, () -> openGui(player));
    }

    public void startNameInput(Player player) {
        waitingForHomeName.add(player.getUniqueId());
        player.closeInventory();
        send(player, "homes.messages.enter-name", "{prefix} &7ᴘʟᴇᴀꜱᴇ ᴇɴᴛᴇʀ ᴀ ɴᴀᴍᴇ ꜰᴏʀ ʏᴏᴜʀ ɴᴇᴡ ʜᴏᴍᴇ (&ccancel&7 ᴛᴏ ᴄᴀɴᴄᴇʟ):");
    }

    public boolean isWaitingForName(Player player) {
        return waitingForHomeName.contains(player.getUniqueId());
    }

    public void handleNameInput(Player player, String message) {
        if (!waitingForHomeName.remove(player.getUniqueId())) {
            return;
        }
        if (message.equalsIgnoreCase("cancel")) {
            send(player, "homes.messages.cancelled", "{prefix} &cʜᴏᴍᴇ sᴇᴛᴛɪɴɢ ᴄᴀɴᴄᴇʟʟᴇᴅ.");
            return;
        }
        if (setHome(player, message)) {
            Bukkit.getScheduler().runTask(plugin, () -> openGui(player));
        }
    }

    public void startTeleport(Player player, String rawName) {
        if (!isEnabled()) {
            send(player, "homes.messages.disabled", "&cHomes are disabled.");
            return;
        }
        Optional<Home> optional = getHome(player, rawName);
        if (optional.isEmpty()) {
            send(player, "homes.messages.not-found", "{prefix} &cɴᴏ ʜᴏᴍᴇ ɴᴀᴍᴇᴅ &e%home% &cꜰᴏᴜɴᴅ!", "%home%", cleanHomeName(rawName));
            return;
        }

        cancelTeleport(player, false);
        Home home = optional.get();
        int delay = Math.max(0, plugin.getConfig().getInt("homes.teleport-delay-seconds", 5));
        if (delay <= 0) {
            player.teleport(home.getLocation());
            sendActionBar(player, plugin.getConfig().getString("homes.messages.actionbar-teleported", "&aᴛᴇʟᴇᴘᴏʀᴛᴇᴅ!"));
            send(player, "homes.messages.teleported", "{prefix} &aᴛᴇʟᴇᴘᴏʀᴛᴇᴅ ᴛᴏ ʜᴏᴍᴇ &e%home%&a.", "%home%", home.getName());
            return;
        }

        PendingTeleport pending = new PendingTeleport(player.getUniqueId(), home, player.getLocation().clone(), delay);
        pendingTeleports.put(player.getUniqueId(), pending);
        send(player, "homes.messages.teleport-start", "{prefix} &7ᴛᴇʟᴇᴘᴏʀᴛɪɴɢ ɪɴ &b%seconds% &7sᴇᴄᴏɴᴅs ᴛᴏ ʜᴏᴍᴇ &e%home%&7. &8(ᴅᴏɴ'ᴛ ᴍᴏᴠᴇ!)",
                "%seconds%", String.valueOf(delay), "%home%", home.getName());
        sendActionBar(player, plugin.getConfig().getString("homes.messages.actionbar-countdown", "&bᴛᴇʟᴇᴘᴏʀᴛɪɴɢ ɪɴ &f%seconds% &bꜱᴇᴄᴏɴᴅꜱ...").replace("%seconds%", String.valueOf(delay)));

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickTeleport(player), 20L, 20L);
        pending.setTask(task);
    }

    public void cancelTeleport(Player player, boolean moved) {
        PendingTeleport pending = pendingTeleports.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }
        pending.cancelTask();
        if (moved) {
            sendActionBar(player, plugin.getConfig().getString("homes.messages.actionbar-cancelled", "&cᴛᴇʟᴇᴘᴏʀᴛ ᴄᴀɴᴄᴇʟʟᴇᴅ (ᴍᴏᴠᴇᴅ)!"));
            send(player, "homes.messages.teleport-cancelled", "{prefix} &cᴛᴇʟᴇᴘᴏʀᴛ ᴄᴀɴᴄᴇʟʟᴇᴅ ʙᴇᴄᴀᴜꜱᴇ ʏᴏᴜ ᴍᴏᴠᴇᴅ!");
        }
    }

    public boolean hasPendingTeleport(Player player) {
        return pendingTeleports.containsKey(player.getUniqueId());
    }

    public void checkMovement(Player player, Location to) {
        PendingTeleport pending = pendingTeleports.get(player.getUniqueId());
        if (pending == null || to == null) {
            return;
        }
        if (hasChangedBlock(pending.startLocation(), to)) {
            cancelTeleport(player, true);
        }
    }

    private void tickTeleport(Player player) {
        PendingTeleport pending = pendingTeleports.get(player.getUniqueId());
        if (pending == null || !player.isOnline()) {
            if (pending != null) {
                pending.cancelTask();
                pendingTeleports.remove(player.getUniqueId());
            }
            return;
        }

        pending.decreaseSeconds();
        if (pending.secondsLeft() > 0) {
            sendActionBar(player, plugin.getConfig().getString("homes.messages.actionbar-countdown", "&bᴛᴇʟᴇᴘᴏʀᴛɪɴɢ ɪɴ &f%seconds% &bꜱᴇᴄᴏɴᴅꜱ...").replace("%seconds%", String.valueOf(pending.secondsLeft())));
            return;
        }

        pendingTeleports.remove(player.getUniqueId());
        pending.cancelTask();
        player.teleport(pending.home().getLocation());
        sendActionBar(player, plugin.getConfig().getString("homes.messages.actionbar-teleported", "&aᴛᴇʟᴇᴘᴏʀᴛᴇᴅ!"));
        send(player, "homes.messages.teleported", "{prefix} &aᴛᴇʟᴇᴘᴏʀᴛᴇᴅ ᴛᴏ ʜᴏᴍᴇ &e%home%&a.", "%home%", pending.home().getName());
    }

    private Map<String, Home> getPlayerHomes(UUID uuid) {
        return homes.computeIfAbsent(uuid, ignored -> new LinkedHashMap<>());
    }

    private ItemStack createGuiItem(Material material, String name, List<String> lore, String action, String homeName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.color(name));
            meta.setLore(lore.stream().map(MessageUtil::color).toList());
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            meta.getPersistentDataContainer().set(homeKey, PersistentDataType.STRING, homeName == null ? "" : homeName);
            item.setItemMeta(meta);
        }
        return item;
    }

    public String getItemAction(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta() == null) {
            return "";
        }
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(actionKey, PersistentDataType.STRING, "");
    }

    public String getItemHomeName(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta() == null) {
            return "";
        }
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(homeKey, PersistentDataType.STRING, "");
    }

    private List<String> replaceList(List<String> input, String token, String value) {
        if (input == null || input.isEmpty()) {
            return List.of("&7ᴄʟɪᴄᴋ ᴛᴏ ᴛᴇʟᴇᴘᴏʀᴛ");
        }
        return input.stream().map(line -> line.replace(token, value)).toList();
    }

    private String cleanHomeName(String input) {
        if (input == null) {
            return "home";
        }
        String cleaned = MessageUtil.stripColor(input).trim().replace(" ", "_");
        return cleaned.isBlank() ? "home" : cleaned;
    }

    private boolean isValidHomeName(String name) {
        return SAFE_NAME_PATTERN.matcher(name).matches();
    }

    private void send(Player player, String path, String fallback, String... replacements) {
        String message = plugin.getConfig().getString(path, fallback).replace("{prefix}", getPrefix()).replace("%prefix%", getPrefix());
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        player.sendMessage(plugin.getRankManager().applyPlaceholders(player, message));
    }

    private String getPrefix() {
        return plugin.getConfig().getString("homes.prefix", "&8• &bʜᴏᴍᴇ &8»");
    }

    private String color(String path, String fallback) {
        return MessageUtil.color(plugin.getConfig().getString(path, fallback));
    }

    private void sendActionBar(Player player, String message) {
        String colored = plugin.getRankManager().applyPlaceholders(player, message);
        ActionBarUtil.send(player, colored);
    }

    private Location readLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return null;
        }
        return new Location(world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"));
    }

    private void writeLocation(ConfigurationSection section, Location location) {
        section.set("world", location.getWorld() == null ? "world" : location.getWorld().getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
    }

    private boolean hasChangedBlock(Location from, Location to) {
        return from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().equals(to.getWorld())
                || from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }

    private Material getMaterial(String path, Material fallback) {
        String raw = plugin.getConfig().getString(path, fallback.name());
        Material material = raw == null ? null : Material.matchMaterial(raw);
        return material == null ? fallback : material;
    }

    private void playConfiguredSound(Player player, String soundPath, String fallback, String volumePath, String pitchPath) {
        String configured = plugin.getConfig().getString(soundPath, fallback);
        if (configured == null || configured.isBlank() || configured.equalsIgnoreCase("none")) {
            return;
        }
        Sound sound = SoundUtil.fromConfig(configured);
        if (sound == null) {
            plugin.getLogger().warning("Unknown sound in config: " + configured);
            return;
        }
        float volume = (float) plugin.getConfig().getDouble(volumePath, 1.0D);
        float pitch = (float) plugin.getConfig().getDouble(pitchPath, 1.0D);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private boolean usesDatabaseStorage() {
        String type = plugin.getConfig().getString("homes.storage.type", "same-as-database");
        if (type == null || type.equalsIgnoreCase("same-as-database")) {
            type = plugin.getConfig().getString("database.type", "sqlite");
        }
        return type != null && (type.equalsIgnoreCase("sqlite") || type.equalsIgnoreCase("sqlite3") || type.equalsIgnoreCase("mysql"));
    }

    private boolean usesMySQL() {
        String type = plugin.getConfig().getString("homes.storage.type", "same-as-database");
        if (type == null || type.equalsIgnoreCase("same-as-database")) {
            type = plugin.getConfig().getString("database.type", "sqlite");
        }
        return type != null && type.equalsIgnoreCase("mysql");
    }

    private String getHomeStorageName() {
        return usesMySQL() ? "MySQL" : "SQLite";
    }

    private Connection openHomeConnection() throws Exception {
        if (usesMySQL()) {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String host = plugin.getConfig().getString("database.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("database.mysql.port", 3306);
            String database = plugin.getConfig().getString("database.mysql.database", "karamsmp");
            String username = plugin.getConfig().getString("database.mysql.username", "root");
            String password = plugin.getConfig().getString("database.mysql.password", "");
            boolean ssl = plugin.getConfig().getBoolean("database.mysql.use-ssl", false);
            String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + ssl
                    + "&allowPublicKeyRetrieval=true"
                    + "&connectTimeout=5000"
                    + "&socketTimeout=10000"
                    + "&characterEncoding=utf8"
                    + "&useUnicode=true"
                    + "&serverTimezone=UTC";
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        Class.forName("org.sqlite.JDBC");
        String fileName = plugin.getConfig().getString("database.sqlite.file", "player-ranks.db");
        File sqliteFile = new File(plugin.getDataFolder(), fileName == null || fileName.isBlank() ? "player-ranks.db" : fileName);
        File parent = sqliteFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    private void createHomeTable(Connection connection) throws SQLException {
        String table = getHomeTableName();
        String sql;
        if (usesMySQL()) {
            sql = "CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "uuid VARCHAR(36) NOT NULL,"
                    + "username VARCHAR(64) NOT NULL DEFAULT '',"
                    + "home_name VARCHAR(64) NOT NULL,"
                    + "world VARCHAR(128) NOT NULL,"
                    + "x DOUBLE NOT NULL,"
                    + "y DOUBLE NOT NULL,"
                    + "z DOUBLE NOT NULL,"
                    + "yaw FLOAT NOT NULL DEFAULT 0,"
                    + "pitch FLOAT NOT NULL DEFAULT 0,"
                    + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY (uuid, home_name)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        } else {
            sql = "CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "uuid TEXT NOT NULL,"
                    + "username TEXT NOT NULL DEFAULT '',"
                    + "home_name TEXT NOT NULL,"
                    + "world TEXT NOT NULL,"
                    + "x REAL NOT NULL,"
                    + "y REAL NOT NULL,"
                    + "z REAL NOT NULL,"
                    + "yaw REAL NOT NULL DEFAULT 0,"
                    + "pitch REAL NOT NULL DEFAULT 0,"
                    + "updated_at TEXT DEFAULT CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY (uuid, home_name)"
                    + ")";
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            try {
                String indexSql = usesMySQL()
                        ? "CREATE INDEX idx_" + table + "_uuid ON " + table + " (uuid)"
                        : "CREATE INDEX IF NOT EXISTS idx_" + table + "_uuid ON " + table + " (uuid)";
                statement.executeUpdate(indexSql);
            } catch (SQLException exception) {
                String message = String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT);
                if (!message.contains("duplicate") && !message.contains("already exists")) {
                    throw exception;
                }
            }
        }
    }

    private String getHomeTableName() {
        String explicit;
        if (usesMySQL()) {
            explicit = plugin.getConfig().getString("homes.storage.mysql-table", "");
            if (explicit == null || explicit.isBlank()) {
                explicit = plugin.getConfig().getString("database.mysql.table-prefix", "karamsmp_") + "player_homes";
            }
        } else {
            explicit = plugin.getConfig().getString("homes.storage.sqlite-table", "player_homes");
        }
        return sanitizeTableName(explicit, usesMySQL() ? "karamsmp_player_homes" : "player_homes");
    }

    private String sanitizeTableName(String tableName, String fallback) {
        String sanitized = tableName == null ? fallback : tableName.replaceAll("[^A-Za-z0-9_]", "");
        return sanitized.isBlank() ? fallback : sanitized;
    }

    private record PendingTeleport(UUID uuid, Home home, Location startLocation, int[] secondsLeftHolder, BukkitTask[] taskHolder) {
        PendingTeleport(UUID uuid, Home home, Location startLocation, int secondsLeft) {
            this(uuid, home, startLocation, new int[]{secondsLeft}, new BukkitTask[1]);
        }

        int secondsLeft() {
            return secondsLeftHolder[0];
        }

        void decreaseSeconds() {
            secondsLeftHolder[0]--;
        }

        void setTask(BukkitTask task) {
            taskHolder[0] = task;
        }

        void cancelTask() {
            if (taskHolder[0] != null) {
                taskHolder[0].cancel();
                taskHolder[0] = null;
            }
        }
    }
}

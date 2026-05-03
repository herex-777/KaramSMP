package me.herex.karmsmp.rtp;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.utils.MessageUtil;
import me.herex.karmsmp.utils.ActionBarUtil;
import me.herex.karmsmp.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomTeleportManager implements CommandExecutor, TabCompleter, Listener {

    private final KaramSMP plugin;
    private final Map<UUID, PendingRtp> pending = new ConcurrentHashMap<>();

    public RandomTeleportManager(KaramSMP plugin) {
        this.plugin = plugin;
    }

    public void stop() {
        pending.values().forEach(PendingRtp::cancelTask);
        pending.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("rtp.messages.only-players", "&cOnly players can use this command."));
            return true;
        }
        if (!plugin.getConfig().getBoolean("rtp.enabled", true)) {
            player.sendMessage(color("rtp.messages.disabled", "&cRandom teleport is disabled."));
            return true;
        }
        if (args.length >= 1 && player.hasPermission("karamsmp.rtp.admin")) {
            String key = cleanKey(args[0]);
            if (getWorldSection(key) != null) {
                startTeleport(player, key);
                return true;
            }
        }
        openGui(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("karamsmp.rtp.admin")) {
            return new ArrayList<>(getWorldKeys());
        }
        return Collections.emptyList();
    }

    public void openGui(Player player) {
        int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("rtp.gui.rows", 3)));
        RtpGuiHolder holder = new RtpGuiHolder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, plugin.getRankManager().applyPlaceholders(player, plugin.getConfig().getString("rtp.gui.title", "&8ʀᴀɴᴅᴏᴍ ᴛᴇʟᴇᴘᴏʀᴛ")));
        holder.setInventory(inventory);

        ConfigurationSection worlds = plugin.getConfig().getConfigurationSection("rtp.worlds");
        if (worlds != null) {
            for (String key : worlds.getKeys(false)) {
                ConfigurationSection section = worlds.getConfigurationSection(key);
                if (section == null || !section.getBoolean("enabled", true)) {
                    continue;
                }
                int slot = section.getInt("slot", 13);
                if (slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }
                inventory.setItem(slot, createItem(player, key, section));
                holder.setSlot(slot, cleanKey(key));
            }
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RtpGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String key = holder.getKey(event.getRawSlot());
        if (key == null) {
            return;
        }
        player.closeInventory();
        startTeleport(player, key);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        PendingRtp current = pending.get(event.getPlayer().getUniqueId());
        if (current == null || event.getTo() == null) {
            return;
        }
        Location from = current.startLocation();
        Location to = event.getTo();
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            cancelTeleport(event.getPlayer(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelTeleport(event.getPlayer(), false);
    }

    private void startTeleport(Player player, String key) {
        if (pending.containsKey(player.getUniqueId())) {
            player.sendMessage(message(player, "rtp.messages.already-teleporting", "&8• &bʀᴛᴘ &8» &cYou are already teleporting."));
            return;
        }

        ConfigurationSection section = getWorldSection(key);
        if (section == null || !section.getBoolean("enabled", true)) {
            player.sendMessage(message(player, "rtp.messages.world-disabled", "&8• &bʀᴛᴘ &8» &cThat random teleport world is disabled."));
            return;
        }

        String worldName = section.getString("world", "world");
        World world = Bukkit.getWorld(worldName == null ? "world" : worldName);
        if (world == null) {
            player.sendMessage(message(player, "rtp.messages.world-not-found", "&8• &bʀᴛᴘ &8» &cWorld &e%world% &cwas not found.", "%world%", worldName == null ? "world" : worldName));
            return;
        }

        int delay = Math.max(0, plugin.getConfig().getInt("rtp.teleport-delay-seconds", 5));
        if (delay <= 0) {
            player.sendMessage(message(player, "rtp.messages.searching", "&8• &bʀᴛᴘ &8» &7Finding a safe location...", "%world%", getDisplayName(section, key)));
            startSafeSearch(player, world, section, key);
            return;
        }

        player.sendMessage(message(player, "rtp.messages.teleport-start", "&8• &bʀᴛᴘ &8» &7ᴛᴇʟᴇᴘᴏʀᴛɪɴɢ ɪɴ &b%seconds% &7sᴇᴄᴏɴᴅs. &8(ᴅᴏɴ'ᴛ ᴍᴏᴠᴇ!)", "%seconds%", String.valueOf(delay), "%world%", getDisplayName(section, key)));
        sendActionBar(player, plugin.getConfig().getString("rtp.messages.actionbar-countdown", "&bʀᴛᴘ ɪɴ &f%seconds% &bꜱᴇᴄᴏɴᴅꜱ...").replace("%seconds%", String.valueOf(delay)));

        BukkitRunnable runnable = new BukkitRunnable() {
            private int seconds = delay;

            @Override
            public void run() {
                if (!player.isOnline() || !pending.containsKey(player.getUniqueId())) {
                    pending.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                seconds--;
                if (seconds > 0) {
                    sendActionBar(player, plugin.getConfig().getString("rtp.messages.actionbar-countdown", "&bʀᴛᴘ ɪɴ &f%seconds% &bꜱᴇᴄᴏɴᴅꜱ...").replace("%seconds%", String.valueOf(seconds)));
                    return;
                }
                player.sendMessage(message(player, "rtp.messages.searching", "&8• &bʀᴛᴘ &8» &7Finding a safe location...", "%world%", getDisplayName(section, key)));
                startSafeSearch(player, world, section, key);
                cancel();
            }
        };
        pending.put(player.getUniqueId(), new PendingRtp(player.getLocation().clone(), key, delay, runnable));
        runnable.runTaskTimer(plugin, 20L, 20L);
    }

    private void startSafeSearch(Player player, World world, ConfigurationSection section, String key) {
        PendingRtp current = pending.get(player.getUniqueId());
        Location startLocation = current == null ? player.getLocation().clone() : current.startLocation();
        int attempts = Math.max(1, section.getInt("attempts", plugin.getConfig().getInt("rtp.attempts", 64)));
        int attemptsPerTick = Math.max(1, Math.min(16, plugin.getConfig().getInt("rtp.attempts-per-tick", 2)));

        BukkitRunnable searchTask = new BukkitRunnable() {
            private int attempted;

            @Override
            public void run() {
                if (!player.isOnline() || !pending.containsKey(player.getUniqueId())) {
                    pending.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                for (int i = 0; i < attemptsPerTick && attempted < attempts; i++) {
                    attempted++;
                    Location destination = findOneSafeLocation(world, section);
                    if (destination != null) {
                        pending.remove(player.getUniqueId());
                        teleportNow(player, destination, key);
                        cancel();
                        return;
                    }
                }

                if (attempted >= attempts) {
                    pending.remove(player.getUniqueId());
                    player.sendMessage(message(player, "rtp.messages.no-safe-location", "&8• &bʀᴛᴘ &8» &cCould not find a safe location. Try again."));
                    cancel();
                }
            }
        };

        pending.put(player.getUniqueId(), new PendingRtp(startLocation, key, 0, searchTask));
        searchTask.runTaskTimer(plugin, 1L, 1L);
    }

    private void teleportNow(Player player, Location destination, String key) {
        player.teleport(destination);
        ConfigurationSection section = getWorldSection(key);
        player.sendMessage(message(player, "rtp.messages.teleported", "&8• &bʀᴛᴘ &8» &aTeleported to &e%world%&a.", "%world%", section == null ? key : getDisplayName(section, key)));
        sendActionBar(player, plugin.getConfig().getString("rtp.messages.actionbar-teleported", "&aʀᴀɴᴅᴏᴍ ᴛᴇʟᴇᴘᴏʀᴛᴇᴅ!"));
        playSound(player, "rtp.sounds.teleported");
    }

    private void cancelTeleport(Player player, boolean sendMessage) {
        PendingRtp current = pending.remove(player.getUniqueId());
        if (current == null) {
            return;
        }
        current.cancelTask();
        if (sendMessage) {
            player.sendMessage(message(player, "rtp.messages.teleport-cancelled", "&8• &bʀᴛᴘ &8» &cTeleport cancelled because you moved!"));
            sendActionBar(player, plugin.getConfig().getString("rtp.messages.actionbar-cancelled", "&cʀᴛᴘ ᴄᴀɴᴄᴇʟʟᴇᴅ (ᴍᴏᴠᴇᴅ)!"));
            playSound(player, "rtp.sounds.cancelled");
        }
    }

    private Location findOneSafeLocation(World world, ConfigurationSection section) {
        int minRadius = Math.max(0, section.getInt("min-radius", plugin.getConfig().getInt("rtp.default-min-radius", 250)));
        int maxRadius = Math.max(minRadius + 1, section.getInt("max-radius", plugin.getConfig().getInt("rtp.default-max-radius", 5000)));
        int centerX = section.getInt("center-x", 0);
        int centerZ = section.getInt("center-z", 0);
        boolean avoidWater = section.getBoolean("avoid-water", true);
        int minY = Math.max(world.getMinHeight() + 1, section.getInt("min-y", world.getMinHeight() + 1));
        int maxY = Math.min(world.getMaxHeight() - 2, section.getInt("max-y", world.getMaxHeight() - 2));
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int radius = random.nextInt(minRadius, maxRadius + 1);
        int x = centerX + (random.nextBoolean() ? 1 : -1) * random.nextInt(0, radius + 1);
        int z = centerZ + (random.nextBoolean() ? 1 : -1) * random.nextInt(0, radius + 1);
        return scanSafeLocation(world, x, z, avoidWater, minY, maxY);
    }

    private Location scanSafeLocation(World world, int x, int z, boolean avoidWater, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            Block ground = world.getBlockAt(x, y - 1, z);
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            if (!ground.getType().isSolid() || !feet.isPassable() || !head.isPassable()) {
                continue;
            }
            Material groundType = ground.getType();
            if (isUnsafe(groundType, avoidWater) || isUnsafe(feet.getType(), avoidWater) || isUnsafe(head.getType(), avoidWater)) {
                continue;
            }
            return new Location(world, x + 0.5D, y, z + 0.5D);
        }
        return null;
    }

    private boolean isUnsafe(Material material, boolean avoidWater) {
        String name = material.name();
        if (name.contains("LAVA") || name.contains("FIRE") || name.contains("CACTUS") || name.contains("MAGMA") || name.contains("VOID") || name.contains("PORTAL")) {
            return true;
        }
        return avoidWater && (name.contains("WATER") || name.contains("KELP") || name.contains("SEAGRASS"));
    }

    private ItemStack createItem(Player player, String key, ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("material", "GRASS_BLOCK"));
        if (material == null) {
            material = Material.GRASS_BLOCK;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getRankManager().applyPlaceholders(player, section.getString("name", "&a" + key)));
            List<String> lore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                lore.add(plugin.getRankManager().applyPlaceholders(player, line.replace("%world%", getDisplayName(section, key))));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ConfigurationSection getWorldSection(String key) {
        return plugin.getConfig().getConfigurationSection("rtp.worlds." + cleanKey(key));
    }

    private List<String> getWorldKeys() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("rtp.worlds");
        if (section == null) {
            return List.of();
        }
        return section.getKeys(false).stream().map(this::cleanKey).toList();
    }

    private String getDisplayName(ConfigurationSection section, String fallback) {
        return MessageUtil.color(section.getString("display-name", fallback));
    }

    private String cleanKey(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    private String message(Player player, String path, String fallback, String... replacements) {
        String raw = plugin.getConfig().getString(path, fallback);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return plugin.getRankManager().applyPlaceholders(player, raw);
    }

    private String color(String path, String fallback) {
        return MessageUtil.color(plugin.getConfig().getString(path, fallback));
    }

    private void sendActionBar(Player player, String message) {
        String colored = plugin.getRankManager().applyPlaceholders(player, message);
        ActionBarUtil.send(player, colored);
    }

    private void playSound(Player player, String path) {
        String name = plugin.getConfig().getString(path + ".name", "");
        Sound sound = SoundUtil.fromConfig(name);
        if (sound == null) {
            return;
        }
        float volume = (float) plugin.getConfig().getDouble(path + ".volume", 1.0D);
        float pitch = (float) plugin.getConfig().getDouble(path + ".pitch", 1.0D);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private record PendingRtp(Location startLocation, String worldKey, int secondsLeft, BukkitRunnable task) {
        private void cancelTask() {
            if (task != null) {
                task.cancel();
            }
        }
    }
}

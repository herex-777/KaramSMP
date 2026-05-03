package me.herex.karmsmp.afk;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.utils.ActionBarUtil;
import me.herex.karmsmp.utils.MessageUtil;
import me.herex.karmsmp.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AfkManager implements CommandExecutor, TabCompleter, Listener {

    private final KaramSMP plugin;
    private final Map<UUID, AfkState> active = new HashMap<>();
    private final Map<UUID, CountdownState> countdowns = new HashMap<>();
    private File file;
    private YamlConfiguration yaml;
    private Location afkRoom;
    private BukkitTask tickerTask;

    public AfkManager(KaramSMP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        String fileName = plugin.getConfig().getString("afk.data-file", "afkroom.yml");
        file = new File(plugin.getDataFolder(), fileName == null || fileName.isBlank() ? "afkroom.yml" : fileName);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not create afk room file: " + exception.getMessage());
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        afkRoom = readLocation("room");
        restartTicker();
    }

    public void save() {
        if (yaml == null || file == null) {
            return;
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save afk room file: " + exception.getMessage());
        }
    }

    public void stop() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
        for (UUID uuid : List.copyOf(countdowns.keySet())) {
            cancelCountdown(uuid);
        }
        for (UUID uuid : List.copyOf(active.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                leaveAfk(player, false, false);
            }
        }
        save();
    }

    public void reload() {
        stop();
        load();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("setafkroom")) {
            handleSetAfkRoom(sender);
            return true;
        }
        handleAfk(sender);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        CountdownState countdown = countdowns.get(uuid);
        if (countdown != null && changedBlock(event)) {
            cancelCountdown(uuid);
            player.sendMessage(color(player, plugin.getConfig().getString("afk.messages.teleport-cancelled", "&8• &bAFK &8» &cTeleport cancelled because you moved!")));
            ActionBarUtil.send(player, plugin.getRankManager().applyPlaceholders(player, plugin.getConfig().getString("afk.messages.actionbar-cancelled", "&cAFK teleport cancelled!")));
            return;
        }
        AfkState state = active.get(uuid);
        if (state != null && afkRoom != null) {
            double radius = plugin.getConfig().getDouble("afk.room-radius", 8.0D);
            if (!player.getWorld().equals(afkRoom.getWorld()) || player.getLocation().distanceSquared(afkRoom) > radius * radius) {
                leaveAfk(player, true, true);
            }
        }
    }


    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("afk.hide-players", true)) {
            return;
        }
        Player joined = event.getPlayer();
        for (UUID uuid : active.keySet()) {
            Player afkPlayer = Bukkit.getPlayer(uuid);
            if (afkPlayer == null || afkPlayer.equals(joined)) {
                continue;
            }
            joined.hidePlayer(plugin, afkPlayer);
            afkPlayer.hidePlayer(plugin, joined);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelCountdown(event.getPlayer().getUniqueId());
        leaveAfk(event.getPlayer(), false, false);
    }

    public boolean isAfk(Player player) {
        return player != null && active.containsKey(player.getUniqueId());
    }

    private void handleSetAfkRoom(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(plugin.getConfig().getString("afk.messages.only-players", "&cOnly players can use this command.")));
            return;
        }
        if (!player.hasPermission("karamsmp.afk.set") && !player.hasPermission("karamsmp.admin")) {
            player.sendMessage(MessageUtil.color(plugin.getConfig().getString("afk.messages.no-permission", "&cYou don't have permission to use this command!")));
            return;
        }
        afkRoom = player.getLocation().clone();
        writeLocation("room", afkRoom);
        save();
        player.sendMessage(color(player, plugin.getConfig().getString("afk.messages.room-set", "&8• &bAFK &8» &aAFK room has been set.")));
    }

    private void handleAfk(CommandSender sender) {
        if (!plugin.getConfig().getBoolean("afk.enabled", true)) {
            sender.sendMessage(MessageUtil.color(plugin.getConfig().getString("afk.messages.disabled", "&cAFK room is disabled.")));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(plugin.getConfig().getString("afk.messages.only-players", "&cOnly players can use this command.")));
            return;
        }
        if (isAfk(player)) {
            leaveAfk(player, true, true);
            return;
        }
        if (countdowns.containsKey(player.getUniqueId())) {
            player.sendMessage(color(player, plugin.getConfig().getString("afk.messages.already-teleporting", "&8• &bAFK &8» &cYou are already teleporting.")));
            return;
        }
        if (afkRoom == null || afkRoom.getWorld() == null) {
            player.sendMessage(color(player, plugin.getConfig().getString("afk.messages.not-set", "&8• &bAFK &8» &cThe AFK room has not been set yet.")));
            return;
        }
        startCountdown(player);
    }

    private void startCountdown(Player player) {
        int seconds = Math.max(1, plugin.getConfig().getInt("afk.teleport-delay-seconds", 5));
        CountdownState state = new CountdownState(player.getLocation().clone(), seconds);
        countdowns.put(player.getUniqueId(), state);
        sendCountdown(player, seconds);
        BukkitTask task = new BukkitRunnable() {
            private int remaining = seconds;

            @Override
            public void run() {
                if (!player.isOnline() || !countdowns.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }
                remaining--;
                if (remaining <= 0) {
                    countdowns.remove(player.getUniqueId());
                    enterAfk(player);
                    cancel();
                    return;
                }
                sendCountdown(player, remaining);
            }
        }.runTaskTimer(plugin, 20L, 20L);
        state.task = task;
    }

    private void sendCountdown(Player player, int seconds) {
        String message = plugin.getConfig().getString("afk.messages.teleport-start", "&8• &bAFK &8» &7Teleporting to the AFK room in &b%seconds% &7seconds. &8(Don't move!)")
                .replace("%seconds%", String.valueOf(seconds));
        player.sendMessage(color(player, message));
        String action = plugin.getConfig().getString("afk.messages.actionbar-countdown", "&bAFK room in &f%seconds% &bseconds...")
                .replace("%seconds%", String.valueOf(seconds));
        ActionBarUtil.send(player, plugin.getRankManager().applyPlaceholders(player, action));
    }

    private void enterAfk(Player player) {
        if (afkRoom == null || afkRoom.getWorld() == null) {
            return;
        }
        AfkState state = new AfkState(player.getLocation().clone(), plugin.getConfig().getInt("afk.shards.interval-seconds", 60));
        active.put(player.getUniqueId(), state);
        player.teleport(afkRoom);
        makeHidden(player);
        player.sendMessage(color(player, plugin.getConfig().getString("afk.messages.entered", "&8• &bAFK &8» &aYou entered the AFK room.")));
    }

    private void leaveAfk(Player player, boolean teleportBack, boolean message) {
        AfkState state = active.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        makeVisible(player);
        if (teleportBack && state.lastLocation != null && state.lastLocation.getWorld() != null) {
            player.teleport(state.lastLocation);
        }
        if (message) {
            player.sendMessage(color(player, plugin.getConfig().getString("afk.messages.left", "&8• &bAFK &8» &7You left the AFK room.")));
        }
    }

    private void restartTicker() {
        if (tickerTask != null) {
            tickerTask.cancel();
        }
        tickerTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickAfkPlayers();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void tickAfkPlayers() {
        if (!plugin.getConfig().getBoolean("afk.enabled", true) || active.isEmpty()) {
            return;
        }
        int interval = Math.max(1, plugin.getConfig().getInt("afk.shards.interval-seconds", 60));
        double amount = Math.max(0.0D, plugin.getConfig().getDouble("afk.shards.amount", 1.0D));
        for (UUID uuid : List.copyOf(active.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            AfkState state = active.get(uuid);
            if (player == null || !player.isOnline() || state == null) {
                active.remove(uuid);
                continue;
            }
            state.nextShardSeconds--;
            if (state.nextShardSeconds <= 0) {
                if (plugin.getShardManager() != null) {
                    plugin.getShardManager().addShards(player.getUniqueId(), player.getName(), amount);
                }
                state.nextShardSeconds = interval;
                SoundUtil.play(player,
                        plugin.getConfig().getString("afk.shards.sound.name", "ENTITY_EXPERIENCE_ORB_PICKUP"),
                        (float) plugin.getConfig().getDouble("afk.shards.sound.volume", 1.0D),
                        (float) plugin.getConfig().getDouble("afk.shards.sound.pitch", 1.2D));
            }
            String action = plugin.getConfig().getString("afk.messages.actionbar-shard-countdown", "&7Next shard in &b%seconds%s")
                    .replace("%seconds%", String.valueOf(Math.max(0, state.nextShardSeconds)))
                    .replace("%shards%", plugin.getShardManager() == null ? "0" : plugin.getShardManager().format(plugin.getShardManager().getShards(player)));
            ActionBarUtil.send(player, plugin.getRankManager().applyPlaceholders(player, action));
        }
    }

    private void makeHidden(Player player) {
        if (plugin.getConfig().getBoolean("afk.invisible", true)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
        }
        if (!plugin.getConfig().getBoolean("afk.hide-players", true)) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) {
                continue;
            }
            online.hidePlayer(plugin, player);
            player.hidePlayer(plugin, online);
        }
    }

    private void makeVisible(Player player) {
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) {
                continue;
            }
            online.showPlayer(plugin, player);
            player.showPlayer(plugin, online);
        }
    }

    private void cancelCountdown(UUID uuid) {
        CountdownState state = countdowns.remove(uuid);
        if (state != null && state.task != null) {
            state.task.cancel();
        }
    }

    private boolean changedBlock(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return false;
        }
        return event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()
                || !event.getFrom().getWorld().equals(event.getTo().getWorld());
    }

    private String color(Player player, String text) {
        return plugin.getRankManager().applyPlaceholders(player, text == null ? "" : text);
    }

    private void writeLocation(String path, Location location) {
        yaml.set(path + ".world", location.getWorld() == null ? "world" : location.getWorld().getName());
        yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY());
        yaml.set(path + ".z", location.getZ());
        yaml.set(path + ".yaw", location.getYaw());
        yaml.set(path + ".pitch", location.getPitch());
    }

    private Location readLocation(String path) {
        if (yaml == null || !yaml.contains(path + ".world")) {
            return null;
        }
        World world = Bukkit.getWorld(yaml.getString(path + ".world", "world"));
        if (world == null) {
            return null;
        }
        return new Location(world,
                yaml.getDouble(path + ".x"),
                yaml.getDouble(path + ".y"),
                yaml.getDouble(path + ".z"),
                (float) yaml.getDouble(path + ".yaw"),
                (float) yaml.getDouble(path + ".pitch"));
    }

    private static final class AfkState {
        private final Location lastLocation;
        private int nextShardSeconds;

        private AfkState(Location lastLocation, int nextShardSeconds) {
            this.lastLocation = lastLocation;
            this.nextShardSeconds = Math.max(1, nextShardSeconds);
        }
    }

    private static final class CountdownState {
        private final Location startLocation;
        private BukkitTask task;

        private CountdownState(Location startLocation, int seconds) {
            this.startLocation = startLocation;
        }
    }
}

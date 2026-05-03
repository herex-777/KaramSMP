package me.herex.karmsmp.commands;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.utils.MessageUtil;
import me.herex.karmsmp.utils.PlayerStatsUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class StatsCommand implements CommandExecutor, TabCompleter, Listener {

    private final KaramSMP plugin;

    public StatsCommand(KaramSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!plugin.getConfig().getBoolean("stats.enabled", true)) {
            sender.sendMessage(MessageUtil.color(plugin.getConfig().getString("stats.messages.disabled", "&cStats are disabled.")));
            return true;
        }

        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(MessageUtil.color(plugin.getConfig().getString("stats.messages.only-players", "&cOnly players can use this command.")));
            return true;
        }

        Player target = viewer;
        boolean other = false;
        if (args.length > 0) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                target = Bukkit.getPlayer(args[0]);
            }
            if (target == null || !target.isOnline()) {
                viewer.sendMessage(MessageUtil.color(plugin.getConfig().getString("stats.messages.player-not-found", "&cThat player is not online.")));
                return true;
            }
            other = true;
        }

        openStatsGui(viewer, target, other);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    @EventHandler
    public void onStatsInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof StatsGuiHolder) {
            event.setCancelled(true);
        }
    }

    private void openStatsGui(Player viewer, Player target, boolean other) {
        int rows = Math.min(6, Math.max(1, plugin.getConfig().getInt("stats.gui.rows", 3)));
        String titlePath = other ? "stats.gui.title-other" : "stats.gui.title-self";
        String fallback = other ? "%player%'s Stats" : "MY STATS";
        String title = plugin.getRankManager().applyPlaceholders(target, plugin.getConfig().getString(titlePath, fallback));
        Inventory inventory = Bukkit.createInventory(new StatsGuiHolder(target.getUniqueId(), target.getName()), rows * 9, MessageUtil.safeLegacySubstring(title, 32));

        addConfiguredItem(inventory, target, "balance", 10, "EMERALD",
                "&a&lBALANCE",
                List.of("&fBalance: &a%balance%"));
        addConfiguredItem(inventory, target, "kills", 11, "DIAMOND_SWORD",
                "&c&lKILLS",
                List.of("&fKills: &c%kills%"));
        addConfiguredItem(inventory, target, "mobs", 12, "ZOMBIE_HEAD",
                "&5&lMOBS KILLED",
                List.of("&fMobs Killed: &5%mob_kills%"));
        addConfiguredItem(inventory, target, "playtime", 13, "CLOCK",
                "&e&lPLAYTIME",
                List.of("&fPlaytime: &e%playtime%"));
        addConfiguredItem(inventory, target, "deaths", 14, "SKELETON_SKULL",
                "&6&lDEATHS",
                List.of("&fDeaths: &6%deaths%"));
        addConfiguredItem(inventory, target, "ping", 15, "PAPER",
                "&b&lPING",
                List.of("&fPing: &b%ping%ms"));
        addConfiguredItem(inventory, target, "coming-soon", 16, "RED_BANNER",
                "&c&lComing Soon...",
                List.of("&7More stats will be added soon."));

        viewer.openInventory(inventory);
    }

    private void addConfiguredItem(Inventory inventory, Player target, String key, int fallbackSlot, String fallbackMaterial, String fallbackName, List<String> fallbackLore) {
        String path = "stats.gui.items." + key;
        int slot = plugin.getConfig().getInt(path + ".slot", fallbackSlot);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        ItemStack item = createItem(target,
                plugin.getConfig().getString(path + ".material", fallbackMaterial),
                plugin.getConfig().getString(path + ".name", fallbackName),
                plugin.getConfig().getStringList(path + ".lore").isEmpty() ? fallbackLore : plugin.getConfig().getStringList(path + ".lore"));
        inventory.setItem(slot, item);
    }

    private ItemStack createItem(Player target, String materialName, String name, List<String> lore) {
        Material material = Material.matchMaterial(materialName == null ? "STONE" : materialName.toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) {
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(apply(target, name));
            meta.setLore(lore.stream().map(line -> apply(target, line)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String apply(Player target, String text) {
        String mobs = String.valueOf(PlayerStatsUtil.getMobKills(target));
        return plugin.getRankManager().applyPlaceholders(target, text == null ? "" : text)
                .replace("%mob_kills%", mobs)
                .replace("%mobs_killed%", mobs)
                .replace("%karamsmp_mob_kills%", mobs)
                .replace("%karamsmp_mobs_killed%", mobs);
    }

    public record StatsGuiHolder(java.util.UUID targetUuid, String targetName) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}

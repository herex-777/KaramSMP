package me.herex.karmsmp.leaderboards;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.economy.EconomyManager;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class LeaderboardCommand implements CommandExecutor, TabCompleter, Listener {

    private static final int PAGE_SIZE = 45;
    private final KaramSMP plugin;
    private final BountyManager bountyManager;

    public LeaderboardCommand(KaramSMP plugin, BountyManager bountyManager) {
        this.plugin = plugin;
        this.bountyManager = bountyManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(plugin.getConfig().getString("leaderboards.messages.only-players", "&cOnly players can use this command.")));
            return true;
        }
        if (commandName.equals("bounty")) {
            if (args.length >= 3 && args[0].equalsIgnoreCase("add")) {
                handleBountyAdd(player, args[1], args[2]);
                return true;
            }
            openBounty(player, 0);
            return true;
        }
        openMostMoney(player, 0);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("bounty")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return List.of("add").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            return List.of("100", "1K", "10K", "100K");
        }
        return Collections.emptyList();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof LeaderboardGuiHolder guiHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }
        if (slot == plugin.getConfig().getInt("leaderboards.gui.previous-slot", 45)) {
            int page = Math.max(0, guiHolder.page() - 1);
            open(player, guiHolder.type(), page);
        } else if (slot == plugin.getConfig().getInt("leaderboards.gui.next-slot", 53)) {
            open(player, guiHolder.type(), guiHolder.page() + 1);
        } else if (slot == plugin.getConfig().getInt("leaderboards.gui.refresh-slot", 49)) {
            open(player, guiHolder.type(), guiHolder.page());
        }
    }

    public void openMostMoney(Player player, int page) {
        open(player, "money", page);
    }

    public void openBounty(Player player, int page) {
        open(player, "bounty", page);
    }

    private void open(Player player, String type, int requestedPage) {
        int page = Math.max(0, requestedPage);
        boolean bounty = type.equalsIgnoreCase("bounty");
        int total = bounty ? bountyManager.getTopBounties(500).size() : plugin.getEconomyManager().getTopBalances(500).size();
        int maxPage = Math.max(0, (int) Math.ceil(Math.max(1, total) / (double) PAGE_SIZE) - 1);
        page = Math.min(page, maxPage);
        String titlePath = bounty ? "leaderboards.bounty-title" : "leaderboards.money-title";
        String fallbackTitle = bounty ? "BOUNTIES" : "MOST MONEY (Page %page%/%pages%)";
        String title = plugin.getConfig().getString(titlePath, fallbackTitle)
                .replace("%page%", String.valueOf(page + 1))
                .replace("%pages%", String.valueOf(maxPage + 1));
        Inventory inventory = Bukkit.createInventory(new LeaderboardGuiHolder(bounty ? "bounty" : "money", page), 54, MessageUtil.safeLegacySubstring(title, 32));
        if (bounty) {
            fillBounties(inventory, page);
        } else {
            fillMoney(inventory, page);
        }
        addControls(inventory, bounty);
        player.openInventory(inventory);
    }

    private void fillMoney(Inventory inventory, int page) {
        List<EconomyManager.TopBalance> entries = plugin.getEconomyManager().getTopBalances((page + 1) * PAGE_SIZE + 1);
        int start = page * PAGE_SIZE;
        int end = Math.min(entries.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            EconomyManager.TopBalance entry = entries.get(index);
            inventory.setItem(index - start, createPlayerHead(entry.uuid(), entry.username(),
                    plugin.getConfig().getString("leaderboards.money-item.name", "&e#%position% &f%player%"),
                    plugin.getConfig().getStringList("leaderboards.money-item.lore").isEmpty()
                            ? List.of("&7Balance: &a%balance%")
                            : plugin.getConfig().getStringList("leaderboards.money-item.lore"),
                    index + 1,
                    plugin.getEconomyManager().format(entry.balance())));
        }
    }

    private void fillBounties(Inventory inventory, int page) {
        List<BountyManager.BountyEntry> entries = bountyManager.getTopBounties((page + 1) * PAGE_SIZE + 1);
        int start = page * PAGE_SIZE;
        int end = Math.min(entries.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            BountyManager.BountyEntry entry = entries.get(index);
            inventory.setItem(index - start, createPlayerHead(entry.uuid(), entry.username(),
                    plugin.getConfig().getString("leaderboards.bounty-item.name", "&c#%position% &f%player%"),
                    plugin.getConfig().getStringList("leaderboards.bounty-item.lore").isEmpty()
                            ? List.of("&7Bounty: &a%balance%")
                            : plugin.getConfig().getStringList("leaderboards.bounty-item.lore"),
                    index + 1,
                    plugin.getEconomyManager().format(entry.amount())));
        }
    }

    private ItemStack createPlayerHead(UUID uuid, String username, String rawName, List<String> rawLore, int position, String amount) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            try {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            } catch (Throwable ignored) {
            }
            meta = skullMeta;
        }
        if (meta != null) {
            String name = replace(rawName, username, position, amount);
            meta.setDisplayName(MessageUtil.color(name));
            meta.setLore(rawLore.stream().map(line -> MessageUtil.color(replace(line, username, position, amount))).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void addControls(Inventory inventory, boolean bounty) {
        inventory.setItem(plugin.getConfig().getInt("leaderboards.gui.previous-slot", 45), createSimpleItem("leaderboards.previous-item", "ARROW", "&ePrevious Page", List.of("&7Click to go back.")));
        inventory.setItem(plugin.getConfig().getInt("leaderboards.gui.next-slot", 53), createSimpleItem("leaderboards.next-item", "ARROW", "&eNext Page", List.of("&7Click to go next.")));
        String path = bounty ? "leaderboards.bounty-refresh-item" : "leaderboards.refresh-item";
        inventory.setItem(plugin.getConfig().getInt("leaderboards.gui.refresh-slot", 49), createSimpleItem(path, bounty ? "TARGET" : "EMERALD", bounty ? "&aBOUNTIES" : "&aRefresh", bounty
                ? List.of("&7Click to refresh", "", "&7Set a bounty using this:", "&f/bounty add <player> <amount>")
                : List.of("&7Click to refresh")));
    }

    private ItemStack createSimpleItem(String path, String fallbackMaterial, String fallbackName, List<String> fallbackLore) {
        Material material = Material.matchMaterial(plugin.getConfig().getString(path + ".material", fallbackMaterial));
        if (material == null || !material.isItem()) {
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.color(plugin.getConfig().getString(path + ".name", fallbackName)));
            List<String> lore = plugin.getConfig().getStringList(path + ".lore");
            meta.setLore((lore.isEmpty() ? fallbackLore : lore).stream().map(MessageUtil::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String replace(String text, String username, int position, String amount) {
        return (text == null ? "" : text)
                .replace("%player%", username == null ? "Unknown" : username)
                .replace("%position%", String.valueOf(position))
                .replace("%rank%", String.valueOf(position))
                .replace("%balance%", amount)
                .replace("%amount%", amount)
                .replace("%bounty%", amount);
    }

    @SuppressWarnings("deprecation")
    private void handleBountyAdd(Player player, String targetName, String amountRaw) {
        if (!plugin.getConfig().getBoolean("bounties.enabled", true)) {
            player.sendMessage(MessageUtil.color(plugin.getConfig().getString("bounties.messages.disabled", "&cBounties are disabled.")));
            return;
        }
        try {
            double amount = plugin.getEconomyManager().parseAmount(amountRaw);
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (target == null || (target.getName() == null && !target.hasPlayedBefore() && !target.isOnline())) {
                player.sendMessage(MessageUtil.color(plugin.getConfig().getString("bounties.messages.player-not-found", "&cThat player was not found.")));
                return;
            }
            if (target.getUniqueId().equals(player.getUniqueId()) && !plugin.getConfig().getBoolean("bounties.allow-self", false)) {
                player.sendMessage(MessageUtil.color(plugin.getConfig().getString("bounties.messages.cannot-bounty-self", "&cYou can't bounty yourself.")));
                return;
            }
            if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), player.getName(), amount)) {
                player.sendMessage(MessageUtil.color(plugin.getConfig().getString("bounties.messages.not-enough-money", "&cYou don't have enough money.")));
                return;
            }
            bountyManager.addBounty(target, amount);
            String formatted = plugin.getEconomyManager().format(amount);
            Bukkit.broadcastMessage(MessageUtil.color(plugin.getConfig().getString("bounties.messages.added", "&8• &cBOUNTY &8» &e%player% &7placed &a%amount% &7on &e%target%&7.")
                    .replace("%player%", player.getName())
                    .replace("%target%", target.getName() == null ? targetName : target.getName())
                    .replace("%amount%", formatted)));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(MessageUtil.color(plugin.getConfig().getString("bounties.messages.invalid-amount", "&cPlease enter a valid amount.")));
        }
    }
}

package me.herex.karmsmp.auction;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.economy.EconomyManager;
import me.herex.karmsmp.utils.MessageUtil;
import me.herex.karmsmp.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class AuctionHouseManager implements Listener, CommandExecutor, TabCompleter {
    private enum ChatMode {
        SEARCH,
        SELL_PRICE,
        TRANSACTION_SEARCH
    }

    private static final int ITEMS_PER_PAGE = 45;
    private static final int SELL_ITEM_SLOT = 4;

    private final KaramSMP plugin;
    private final EconomyManager economy;
    private final Map<UUID, AuctionItem> auctions = new ConcurrentHashMap<>();
    private final Map<UUID, AuctionTransaction> transactions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pages = new HashMap<>();
    private final Map<UUID, String> sortModes = new HashMap<>();
    private final Map<UUID, String> filterModes = new HashMap<>();
    private final Map<UUID, String> searchQueries = new HashMap<>();
    private final Map<UUID, ChatMode> chatModes = new HashMap<>();
    private final Map<UUID, ItemStack> pendingSellItems = new HashMap<>();
    private final Map<UUID, Boolean> suppressSellReturn = new HashMap<>();
    private File auctionFile;
    private File transactionFile;
    private int cleanupTaskId = -1;

    public AuctionHouseManager(KaramSMP plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public void load() {
        auctions.clear();
        transactions.clear();
        File folder = new File(plugin.getDataFolder(), plugin.getConfig().getString("auction-house.storage.folder", "data/auction-house"));
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create auction storage folder: " + folder.getPath());
        }
        auctionFile = new File(folder, plugin.getConfig().getString("auction-house.storage.auctions-file", "auctions.yml"));
        transactionFile = new File(folder, plugin.getConfig().getString("auction-house.storage.transactions-file", "transactions.yml"));
        loadAuctions();
        loadTransactions();
    }

    public void reload() {
        pages.clear();
        sortModes.clear();
        filterModes.clear();
        searchQueries.clear();
        chatModes.clear();
        pendingSellItems.clear();
        load();
    }

    public void start() {
        stop();
        long interval = Math.max(20L, plugin.getConfig().getLong("auction-house.cleanup-interval-ticks", 1200L));
        cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::returnExpiredAuctionsToOnlineSellers, interval, interval);
    }

    public void stop() {
        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId);
            cleanupTaskId = -1;
        }
        saveData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("sell")) {
            return handleSellCommand(sender, args);
        }
        return handleAuctionCommand(sender, args);
    }

    private boolean handleAuctionCommand(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!hasAdmin(sender)) {
                sender.sendMessage(configColor("auction-house.messages.no-permission", "&cYou don't have permission to use this command!"));
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(configColor("auction-house.messages.reloaded", "&aAuction house reloaded."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(configColor("auction-house.messages.only-players", "&cOnly players can use this command."));
            return true;
        }
        if (!isEnabled()) {
            player.sendMessage(configColor("auction-house.messages.disabled", "&cThe auction house is disabled."));
            return true;
        }
        if (!canUse(player)) {
            player.sendMessage(configColor("auction-house.messages.no-permission", "&cYou don't have permission to use this command!"));
            return true;
        }

        if (args.length == 0) {
            openMain(player, 1);
            click(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("sell")) {
            if (args.length < 2) {
                player.sendMessage(configColor("auction-house.messages.sell-usage", "&cUsage: /ah sell <price>"));
                return true;
            }
            return beginHandSell(player, args[1]);
        }
        if (sub.equals("search")) {
            if (args.length < 2) {
                promptSearch(player);
                return true;
            }
            String query = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            searchQueries.put(player.getUniqueId(), query.toLowerCase(Locale.ROOT));
            player.sendMessage(configColor("auction-house.messages.searching", "&aSearching for: &f%query%").replace("%query%", query));
            openMain(player, 1);
            return true;
        }
        if (sub.equals("mine") || sub.equals("items")) {
            openPlayerItems(player);
            return true;
        }
        if (sub.equals("transactions") || sub.equals("history")) {
            openTransactions(player);
            return true;
        }
        openMain(player, 1);
        return true;
    }

    private boolean handleSellCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configColor("auction-house.messages.only-players", "&cOnly players can use this command."));
            return true;
        }
        if (!isEnabled()) {
            player.sendMessage(configColor("auction-house.messages.disabled", "&cThe auction house is disabled."));
            return true;
        }
        if (!canSell(player)) {
            player.sendMessage(configColor("auction-house.messages.no-permission-sell", "&cYou don't have permission to sell items!"));
            return true;
        }
        if (args.length == 0) {
            openSellMenu(player);
            click(player);
            return true;
        }
        return beginHandSell(player, args[0]);
    }

    private boolean beginHandSell(Player player, String priceRaw) {
        if (!canSell(player)) {
            player.sendMessage(configColor("auction-house.messages.no-permission-sell", "&cYou don't have permission to sell items!"));
            return true;
        }
        double price;
        try {
            price = economy.parseAmount(priceRaw);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(configColor("auction-house.messages.invalid-price", "&cInvalid price! Please enter a valid number."));
            return true;
        }
        if (price < minPrice()) {
            player.sendMessage(configColor("auction-house.messages.price-too-low", "&cPrice must be at least &e%price%&c.").replace("%price%", economy.format(minPrice())));
            return true;
        }
        if (getPlayerAuctions(player.getUniqueId()).size() >= getMaxListings(player)) {
            player.sendMessage(configColor("auction-house.messages.max-listings", "&cYou already have the maximum amount of auction listings."));
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            player.sendMessage(configColor("auction-house.messages.no-item", "&cYou must hold an item to sell!"));
            return true;
        }
        openSellConfirm(player, hand.clone(), price, true);
        click(player);
        return true;
    }

    public void openMain(Player player, int page) {
        List<AuctionItem> filtered = filteredAuctions(player);
        int pagesTotal = Math.max(1, (int) Math.ceil(filtered.size() / (double) ITEMS_PER_PAGE));
        int safePage = Math.max(1, Math.min(page, pagesTotal));
        AuctionGuiHolder holder = new AuctionGuiHolder(AuctionGuiType.MAIN, safePage);
        String title = config("auction-house.gui.main-title", "ᴀᴜᴄᴛɪᴏɴ ʜᴏᴜꜱᴇ (Page %page%/%pages%)")
                .replace("%page%", String.valueOf(safePage))
                .replace("%pages%", String.valueOf(pagesTotal));
        Inventory inventory = Bukkit.createInventory(holder, 54, color(player, title));
        holder.setInventory(inventory);

        int start = (safePage - 1) * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, filtered.size());
        for (int index = start; index < end; index++) {
            int slot = index - start;
            AuctionItem auction = filtered.get(index);
            holder.setAuctionSlot(slot, auction.id());
            inventory.setItem(slot, createAuctionDisplay(player, auction));
        }

        inventory.setItem(45, navItem(player, Material.SPECTRAL_ARROW, safePage > 1 ? "&aʙᴀᴄᴋ ᴘᴀɢᴇ" : "&7ʙᴀᴄᴋ ᴘᴀɢᴇ", List.of("&7Click to go back.")));
        inventory.setItem(47, navItem(player, Material.CAULDRON, "&aꜱᴏʀᴛ", sortLore(player)));
        inventory.setItem(48, navItem(player, Material.HOPPER, "&aꜰɪʟᴛᴇʀ", filterLore(player)));
        inventory.setItem(49, navItem(player, Material.ANVIL, "&aʀᴇꜰʀᴇꜱʜ", List.of("&7Click to refresh.", "&7Also resets search/filter.")));
        inventory.setItem(50, navItem(player, Material.OAK_SIGN, "&aꜱᴇᴀʀᴄʜ", List.of("&7Click to search the auction house.")));
        inventory.setItem(51, navItem(player, Material.CHEST, "&aʏᴏᴜʀ ɪᴛᴇᴍꜱ", List.of("&7View your active listings.")));
        inventory.setItem(53, navItem(player, Material.SPECTRAL_ARROW, safePage < pagesTotal ? "&aɴᴇxᴛ ᴘᴀɢᴇ" : "&7ɴᴇxᴛ ᴘᴀɢᴇ", List.of("&7Click to go next.")));
        pages.put(player.getUniqueId(), safePage);
        player.openInventory(inventory);
    }

    private void openSellMenu(Player player) {
        AuctionGuiHolder holder = new AuctionGuiHolder(AuctionGuiType.SELL, 1);
        Inventory inventory = Bukkit.createInventory(holder, 9, color(player, config("auction-house.gui.sell-title", "ꜱᴇʟʟ ɪᴛᴇᴍ")));
        holder.setInventory(inventory);
        inventory.setItem(2, navItem(player, Material.RED_DYE, "&cᴄᴀɴᴄᴇʟ", List.of("&7Click to cancel.")));
        inventory.setItem(3, filler(player));
        inventory.setItem(5, filler(player));
        inventory.setItem(6, navItem(player, Material.LIME_DYE, "&aᴄᴏɴꜰɪʀᴍ", List.of("&7Put an item in the middle", "&7then click to enter a price.")));
        player.openInventory(inventory);
    }

    private void openSellConfirm(Player player, ItemStack item, double price, boolean fromHand) {
        AuctionGuiHolder holder = new AuctionGuiHolder(AuctionGuiType.SELL_CONFIRM, 1);
        holder.item(item);
        holder.price(price);
        Inventory inventory = Bukkit.createInventory(holder, 27, color(player, config("auction-house.gui.sell-confirm-title", "ᴄᴏɴꜰɪʀᴍ ꜱᴇʟʟ")));
        holder.setInventory(inventory);
        inventory.setItem(11, navItem(player, Material.RED_DYE, "&cᴄᴀɴᴄᴇʟ", List.of("&7Click to cancel.")));
        inventory.setItem(13, createSellDisplay(player, item, price, fromHand));
        inventory.setItem(15, navItem(player, Material.LIME_DYE, "&aᴄᴏɴꜰɪʀᴍ ʟɪꜱᴛɪɴɢ", List.of("&7Click to list this item.", "&7Price: &a" + economy.format(price))));
        player.openInventory(inventory);
    }

    private void openPlayerItems(Player player) {
        AuctionGuiHolder holder = new AuctionGuiHolder(AuctionGuiType.PLAYER_ITEMS, 1);
        Inventory inventory = Bukkit.createInventory(holder, 27, color(player, config("auction-house.gui.player-items-title", "ʏᴏᴜʀ ɪᴛᴇᴍꜱ")));
        holder.setInventory(inventory);
        inventory.setItem(0, navItem(player, Material.EMERALD, "&aꜱᴇʟʟ ɪᴛᴇᴍ", List.of("&7Click to open the sell menu.", "&7Or use &f/sell <price>&7.")));
        inventory.setItem(26, navItem(player, Material.BOOK, "&aᴛʀᴀɴꜱᴀᴄᴛɪᴏɴꜱ", List.of("&7Click to view your history.")));
        List<AuctionItem> own = getPlayerAuctions(player.getUniqueId());
        for (int i = 0; i < Math.min(25, own.size()); i++) {
            AuctionItem auction = own.get(i);
            int slot = i + 1;
            holder.setAuctionSlot(slot, auction.id());
            inventory.setItem(slot, createPlayerAuctionDisplay(player, auction));
        }
        player.openInventory(inventory);
    }

    private void openTransactions(Player player) {
        AuctionGuiHolder holder = new AuctionGuiHolder(AuctionGuiType.TRANSACTIONS, 1);
        Inventory inventory = Bukkit.createInventory(holder, 54, color(player, config("auction-house.gui.transactions-title", "ᴛʀᴀɴꜱᴀᴄᴛɪᴏɴꜱ")));
        holder.setInventory(inventory);
        List<AuctionTransaction> list = getPlayerTransactions(player.getUniqueId());
        double spent = list.stream().filter(t -> t.type() == AuctionTransaction.Type.BUY).mapToDouble(AuctionTransaction::amount).sum();
        double made = list.stream().filter(t -> t.type() == AuctionTransaction.Type.SELL).mapToDouble(AuctionTransaction::amount).sum();
        for (int i = 0; i < Math.min(45, list.size()); i++) {
            inventory.setItem(i, createTransactionDisplay(player, list.get(i)));
        }
        inventory.setItem(48, navItem(player, Material.BOOK, "&aᴛᴏᴛᴀʟ ꜱᴘᴇɴᴛ / ᴍᴀᴅᴇ", List.of("&7Spent: &c" + economy.format(spent), "&7Made: &a" + economy.format(made))));
        inventory.setItem(49, navItem(player, Material.ANVIL, "&aʀᴇꜰʀᴇꜱʜ", List.of("&7Click to refresh.")));
        inventory.setItem(50, navItem(player, Material.OAK_SIGN, "&aꜱᴇᴀʀᴄʜ ᴛʀᴀɴꜱᴀᴄᴛɪᴏɴꜱ", List.of("&7Click to search.")));
        player.openInventory(inventory);
    }

    private void openCancelConfirm(Player player, AuctionItem auction) {
        AuctionGuiHolder holder = new AuctionGuiHolder(AuctionGuiType.CANCEL_CONFIRM, 1);
        holder.auctionId(auction.id());
        Inventory inventory = Bukkit.createInventory(holder, 27, color(player, config("auction-house.gui.cancel-title", "ᴄᴀɴᴄᴇʟ ʟɪꜱᴛɪɴɢ")));
        holder.setInventory(inventory);
        inventory.setItem(11, navItem(player, Material.RED_DYE, "&cʙᴀᴄᴋ", List.of("&7Click to go back.")));
        inventory.setItem(13, createCancelDisplay(player, auction));
        inventory.setItem(15, navItem(player, Material.LIME_DYE, "&aᴄᴏɴꜰɪʀᴍ ᴄᴀɴᴄᴇʟ", List.of("&7Click to remove your listing.")));
        player.openInventory(inventory);
    }

    private void openShulkerPreview(Player player, ItemStack shulkerItem) {
        AuctionGuiHolder holder = new AuctionGuiHolder(AuctionGuiType.SHULKER_PREVIEW, 1);
        Inventory inventory = Bukkit.createInventory(holder, 54, color(player, config("auction-house.gui.shulker-title", "&6&lShulker Box Preview")));
        holder.setInventory(inventory);
        if (shulkerItem != null && shulkerItem.getItemMeta() instanceof BlockStateMeta blockMeta && blockMeta.getBlockState() instanceof ShulkerBox shulkerBox) {
            ItemStack[] contents = shulkerBox.getInventory().getContents();
            for (int i = 0; i < Math.min(27, contents.length); i++) {
                if (contents[i] != null) {
                    inventory.setItem(i, contents[i].clone());
                }
            }
        }
        inventory.setItem(49, navItem(player, Material.BARRIER, "&cReturn to Auction", List.of("&7Click to return.")));
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        ChatMode mode = chatModes.remove(event.getPlayer().getUniqueId());
        if (mode == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        String message = event.getMessage();
        if (mode == ChatMode.SEARCH) {
            if (isCancelWord(message)) {
                searchQueries.remove(player.getUniqueId());
                player.sendMessage(configColor("auction-house.messages.search-cancelled", "&cSearch cancelled."));
                Bukkit.getScheduler().runTask(plugin, () -> openMain(player, 1));
                return;
            }
            searchQueries.put(player.getUniqueId(), message.toLowerCase(Locale.ROOT));
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(configColor("auction-house.messages.searching", "&aSearching for: &f%query%").replace("%query%", message));
                openMain(player, 1);
            });
            return;
        }
        if (mode == ChatMode.TRANSACTION_SEARCH) {
            Bukkit.getScheduler().runTask(plugin, () -> openTransactions(player));
            return;
        }
        if (mode == ChatMode.SELL_PRICE) {
            ItemStack item = pendingSellItems.remove(player.getUniqueId());
            if (item == null) {
                player.sendMessage(configColor("auction-house.messages.no-item", "&cNo item to sell."));
                return;
            }
            if (isCancelWord(message)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    returnItem(player, item);
                    player.sendMessage(configColor("auction-house.messages.sell-cancelled", "&cSelling cancelled."));
                    openMain(player, 1);
                });
                return;
            }
            double price;
            try {
                price = economy.parseAmount(message);
            } catch (IllegalArgumentException exception) {
                pendingSellItems.put(player.getUniqueId(), item);
                chatModes.put(player.getUniqueId(), ChatMode.SELL_PRICE);
                player.sendMessage(configColor("auction-house.messages.invalid-price", "&cInvalid price! Please enter a valid number."));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> listAuctionFromDetachedItem(player, item, price));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getInventory().getHolder() instanceof AuctionGuiHolder holder)) {
            return;
        }
        switch (holder.type()) {
            case MAIN -> handleMainClick(event, player, holder);
            case SELL -> handleSellClick(event, player, holder);
            case SELL_CONFIRM -> handleSellConfirmClick(event, player, holder);
            case PLAYER_ITEMS -> handlePlayerItemsClick(event, player, holder);
            case TRANSACTIONS -> handleTransactionsClick(event, player);
            case CANCEL_CONFIRM -> handleCancelConfirmClick(event, player, holder);
            case SHULKER_PREVIEW -> handleShulkerClick(event, player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof AuctionGuiHolder holder)) {
            return;
        }
        if (holder.type() == AuctionGuiType.SELL) {
            for (int slot : event.getRawSlots()) {
                if (slot < event.getInventory().getSize() && slot != SELL_ITEM_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !(event.getInventory().getHolder() instanceof AuctionGuiHolder holder)) {
            return;
        }
        if (holder.type() != AuctionGuiType.SELL) {
            return;
        }
        if (Boolean.TRUE.equals(suppressSellReturn.remove(player.getUniqueId()))) {
            return;
        }
        ItemStack item = event.getInventory().getItem(SELL_ITEM_SLOT);
        if (item != null && item.getType() != Material.AIR) {
            returnItem(player, item);
            event.getInventory().setItem(SELL_ITEM_SLOT, null);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        ItemStack pending = pendingSellItems.remove(event.getPlayer().getUniqueId());
        chatModes.remove(event.getPlayer().getUniqueId());
        if (pending != null) {
            event.getPlayer().getInventory().addItem(pending).values().forEach(item -> event.getPlayer().getWorld().dropItemNaturally(event.getPlayer().getLocation(), item));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, this::returnExpiredAuctionsToOnlineSellers, 20L);
    }

    private void handleMainClick(InventoryClickEvent event, Player player, AuctionGuiHolder holder) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 45) {
            openMain(player, Math.max(1, holder.page() - 1));
            click(player);
            return;
        }
        if (slot == 47) {
            cycleSort(player);
            return;
        }
        if (slot == 48) {
            cycleFilter(player);
            return;
        }
        if (slot == 49) {
            resetFilters(player);
            openMain(player, 1);
            click(player);
            return;
        }
        if (slot == 50) {
            promptSearch(player);
            return;
        }
        if (slot == 51) {
            openPlayerItems(player);
            click(player);
            return;
        }
        if (slot == 53) {
            openMain(player, holder.page() + 1);
            click(player);
            return;
        }
        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            UUID auctionId = holder.getAuctionAt(slot);
            if (auctionId == null) {
                return;
            }
            AuctionItem auction = auctions.get(auctionId);
            if (auction == null) {
                player.sendMessage(configColor("auction-house.messages.auction-not-found", "&cAuction not found."));
                openMain(player, holder.page());
                return;
            }
            if (event.getClick() == ClickType.RIGHT && AuctionCategoryUtil.isShulkerBox(auction.item().getType())) {
                openShulkerPreview(player, auction.item());
                click(player);
                return;
            }
            buyAuction(player, auction.id());
        }
    }

    private void handleSellClick(InventoryClickEvent event, Player player, AuctionGuiHolder holder) {
        int raw = event.getRawSlot();
        if (raw == 2) {
            event.setCancelled(true);
            player.closeInventory();
            deny(player);
            return;
        }
        if (raw == 6) {
            event.setCancelled(true);
            ItemStack item = event.getInventory().getItem(SELL_ITEM_SLOT);
            if (item == null || item.getType() == Material.AIR) {
                player.sendMessage(configColor("auction-house.messages.no-item-center", "&cPlease place an item in the center slot!"));
                deny(player);
                return;
            }
            event.getInventory().setItem(SELL_ITEM_SLOT, null);
            pendingSellItems.put(player.getUniqueId(), item.clone());
            chatModes.put(player.getUniqueId(), ChatMode.SELL_PRICE);
            suppressSellReturn.put(player.getUniqueId(), true);
            player.closeInventory();
            player.sendMessage(configColor("auction-house.messages.price-prompt", "&aType the price for this item:"));
            click(player);
            return;
        }
        if (raw < event.getInventory().getSize()) {
            if (raw != SELL_ITEM_SLOT) {
                event.setCancelled(true);
            }
            return;
        }
        if ((event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) && event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
            event.setCancelled(true);
            if (event.getInventory().getItem(SELL_ITEM_SLOT) == null || event.getInventory().getItem(SELL_ITEM_SLOT).getType() == Material.AIR) {
                event.getInventory().setItem(SELL_ITEM_SLOT, event.getCurrentItem().clone());
                event.getCurrentItem().setAmount(0);
                click(player);
            } else {
                player.sendMessage(configColor("auction-house.messages.sell-slot-full", "&cThe sell slot is already occupied!"));
                deny(player);
            }
        }
    }

    private void handleSellConfirmClick(InventoryClickEvent event, Player player, AuctionGuiHolder holder) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 11) {
            player.closeInventory();
            deny(player);
            return;
        }
        if (slot != 15) {
            return;
        }
        ItemStack item = holder.item();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(configColor("auction-house.messages.no-item", "&cNo item to sell."));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR || !hand.isSimilar(item) || hand.getAmount() < item.getAmount()) {
            player.sendMessage(configColor("auction-house.messages.item-changed", "&cYou must still hold the same item to list it."));
            deny(player);
            player.closeInventory();
            return;
        }
        int newAmount = hand.getAmount() - item.getAmount();
        if (newAmount <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(newAmount);
        }
        listAuction(player, item, holder.price());
        player.closeInventory();
    }

    private void handlePlayerItemsClick(InventoryClickEvent event, Player player, AuctionGuiHolder holder) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 0) {
            openSellMenu(player);
            click(player);
            return;
        }
        if (slot == 26) {
            openTransactions(player);
            click(player);
            return;
        }
        UUID auctionId = holder.getAuctionAt(slot);
        if (auctionId != null) {
            AuctionItem auction = auctions.get(auctionId);
            if (auction != null) {
                openCancelConfirm(player, auction);
                click(player);
            }
        }
    }

    private void handleTransactionsClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (event.getRawSlot() == 49) {
            openTransactions(player);
            click(player);
        } else if (event.getRawSlot() == 50) {
            chatModes.put(player.getUniqueId(), ChatMode.TRANSACTION_SEARCH);
            player.closeInventory();
            player.sendMessage(configColor("auction-house.messages.transaction-search-prompt", "&aType the item name to search in your transactions:"));
            click(player);
        }
    }

    private void handleCancelConfirmClick(InventoryClickEvent event, Player player, AuctionGuiHolder holder) {
        event.setCancelled(true);
        if (event.getRawSlot() == 11) {
            openPlayerItems(player);
            deny(player);
            return;
        }
        if (event.getRawSlot() == 15) {
            cancelAuction(player, holder.auctionId());
        }
    }

    private void handleShulkerClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (event.getRawSlot() == 49) {
            openMain(player, pages.getOrDefault(player.getUniqueId(), 1));
            click(player);
        }
    }

    private void listAuctionFromDetachedItem(Player player, ItemStack item, double price) {
        if (price < minPrice()) {
            returnItem(player, item);
            player.sendMessage(configColor("auction-house.messages.price-too-low", "&cPrice must be at least &e%price%&c.").replace("%price%", economy.format(minPrice())));
            return;
        }
        if (getPlayerAuctions(player.getUniqueId()).size() >= getMaxListings(player)) {
            returnItem(player, item);
            player.sendMessage(configColor("auction-house.messages.max-listings", "&cYou already have the maximum amount of auction listings."));
            return;
        }
        listAuction(player, item, price);
        openMain(player, 1);
    }

    private void listAuction(Player player, ItemStack item, double price) {
        AuctionItem auction = new AuctionItem(player.getUniqueId(), player.getName(), item, price, durationMillis());
        auctions.put(auction.id(), auction);
        saveData();
        String itemName = displayNamePlain(item);
        player.sendMessage(configColor("auction-house.messages.listed", "&aSuccessfully listed &e%item% &afor &e%price%&a!")
                .replace("%item%", itemName)
                .replace("%price%", economy.format(price)));
        success(player);
    }

    private void buyAuction(Player buyer, UUID auctionId) {
        AuctionItem auction = auctions.get(auctionId);
        if (auction == null) {
            buyer.sendMessage(configColor("auction-house.messages.auction-not-found", "&cAuction not found."));
            return;
        }
        if (auction.isExpired()) {
            buyer.sendMessage(configColor("auction-house.messages.auction-expired", "&cThat auction has expired."));
            openMain(buyer, pages.getOrDefault(buyer.getUniqueId(), 1));
            return;
        }
        if (auction.sellerId().equals(buyer.getUniqueId())) {
            buyer.sendMessage(configColor("auction-house.messages.cannot-buy-own", "&cYou cannot buy your own item!"));
            deny(buyer);
            return;
        }
        ItemStack item = auction.item();
        if (!canFit(buyer, item)) {
            buyer.sendMessage(configColor("auction-house.messages.inventory-full", "&cYour inventory is full!"));
            deny(buyer);
            return;
        }
        if (!economy.withdraw(buyer.getUniqueId(), buyer.getName(), auction.price())) {
            buyer.sendMessage(configColor("auction-house.messages.not-enough-money", "&cYou don't have enough money!"));
            deny(buyer);
            return;
        }
        double taxRate = Math.max(0.0D, Math.min(1.0D, plugin.getConfig().getDouble("auction-house.tax-rate", 0.05D)));
        double sellerAmount = auction.price() * (1.0D - taxRate);
        economy.deposit(auction.sellerId(), auction.sellerName(), sellerAmount);
        auctions.remove(auction.id());
        buyer.getInventory().addItem(item).values().forEach(left -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), left));
        transactions.put(UUID.randomUUID(), new AuctionTransaction(buyer.getUniqueId(), buyer.getName(), AuctionTransaction.Type.BUY, item, auction.price(), auction.id()));
        transactions.put(UUID.randomUUID(), new AuctionTransaction(auction.sellerId(), auction.sellerName(), AuctionTransaction.Type.SELL, item, sellerAmount, auction.id()));
        saveData();
        buyer.sendMessage(configColor("auction-house.messages.bought", "&aSuccessfully purchased &e%item%&a for &e%price%&a!")
                .replace("%item%", displayNamePlain(item))
                .replace("%price%", economy.format(auction.price())));
        Player seller = Bukkit.getPlayer(auction.sellerId());
        if (seller != null) {
            seller.sendMessage(configColor("auction-house.messages.sold", "&aYour &e%item% &asold for &e%price%&a!")
                    .replace("%item%", displayNamePlain(item))
                    .replace("%price%", economy.format(sellerAmount)));
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().updatePlayer(seller);
            }
        }
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().updatePlayer(buyer);
        }
        success(buyer);
        openMain(buyer, pages.getOrDefault(buyer.getUniqueId(), 1));
    }

    private void cancelAuction(Player player, UUID auctionId) {
        if (auctionId == null) {
            return;
        }
        AuctionItem auction = auctions.get(auctionId);
        if (auction == null || !auction.sellerId().equals(player.getUniqueId())) {
            player.sendMessage(configColor("auction-house.messages.auction-not-found", "&cAuction not found."));
            return;
        }
        auctions.remove(auctionId);
        returnItem(player, auction.item());
        transactions.put(UUID.randomUUID(), new AuctionTransaction(player.getUniqueId(), player.getName(), AuctionTransaction.Type.CANCEL, auction.item(), auction.price(), auction.id()));
        saveData();
        player.sendMessage(configColor("auction-house.messages.cancelled", "&aSuccessfully cancelled listing!"));
        success(player);
        openPlayerItems(player);
    }

    private void returnExpiredAuctionsToOnlineSellers() {
        boolean changed = false;
        for (AuctionItem auction : new ArrayList<>(auctions.values())) {
            if (!auction.isExpired()) {
                continue;
            }
            Player seller = Bukkit.getPlayer(auction.sellerId());
            if (seller == null) {
                continue;
            }
            auctions.remove(auction.id());
            returnItem(seller, auction.item());
            transactions.put(UUID.randomUUID(), new AuctionTransaction(auction.sellerId(), auction.sellerName(), AuctionTransaction.Type.EXPIRE, auction.item(), auction.price(), auction.id()));
            seller.sendMessage(configColor("auction-house.messages.expired", "&eYour auction listing expired and was returned."));
            changed = true;
        }
        if (changed) {
            saveData();
        }
    }

    private List<AuctionItem> filteredAuctions(Player player) {
        String filter = filterModes.getOrDefault(player.getUniqueId(), "all");
        String sort = sortModes.getOrDefault(player.getUniqueId(), "recent");
        String search = searchQueries.get(player.getUniqueId());
        List<AuctionItem> list = auctions.values().stream()
                .filter(a -> !a.isExpired())
                .filter(a -> AuctionCategoryUtil.belongsTo(a.item().getType(), filter))
                .filter(a -> matchesSearch(a, search))
                .collect(Collectors.toCollection(ArrayList::new));
        switch (sort) {
            case "highest" -> list.sort((a, b) -> Double.compare(b.price(), a.price()));
            case "lowest" -> list.sort(Comparator.comparingDouble(AuctionItem::price));
            case "oldest" -> list.sort(Comparator.comparingLong(AuctionItem::listedAt));
            case "shulker" -> list.removeIf(a -> !AuctionCategoryUtil.isShulkerBox(a.item().getType()));
            default -> list.sort((a, b) -> Long.compare(b.listedAt(), a.listedAt()));
        }
        return list;
    }

    private boolean matchesSearch(AuctionItem auction, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String query = search.toLowerCase(Locale.ROOT);
        ItemStack item = auction.item();
        StringBuilder haystack = new StringBuilder(item.getType().name().toLowerCase(Locale.ROOT)).append(' ').append(auction.sellerName().toLowerCase(Locale.ROOT));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                haystack.append(' ').append(ChatColor.stripColor(meta.getDisplayName()).toLowerCase(Locale.ROOT));
            }
            if (meta.hasLore()) {
                for (String line : meta.getLore()) {
                    haystack.append(' ').append(ChatColor.stripColor(line).toLowerCase(Locale.ROOT));
                }
            }
        }
        return haystack.toString().contains(query);
    }

    private List<AuctionItem> getPlayerAuctions(UUID playerId) {
        return auctions.values().stream()
                .filter(a -> a.sellerId().equals(playerId))
                .filter(a -> !a.isExpired())
                .sorted((a, b) -> Long.compare(b.listedAt(), a.listedAt()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<AuctionTransaction> getPlayerTransactions(UUID playerId) {
        return transactions.values().stream()
                .filter(t -> t.playerId().equals(playerId))
                .sorted((a, b) -> Long.compare(b.timestamp(), a.timestamp()))
                .limit(45)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public int getActiveAuctionCount() {
        return (int) auctions.values().stream().filter(a -> !a.isExpired()).count();
    }

    public int getPlayerAuctionCount(UUID playerId) {
        return getPlayerAuctions(playerId).size();
    }

    private ItemStack createAuctionDisplay(Player player, AuctionItem auction) {
        ItemStack display = auction.item();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (meta.hasLore()) {
                lore.addAll(meta.getLore());
                lore.add("");
            }
            lore.add(color(player, "&7Price: &a" + economy.format(auction.price())));
            lore.add(color(player, "&7Seller: &f" + auction.sellerName()));
            lore.add(color(player, "&7Time left: &e" + auction.timeLeftFormatted()));
            lore.add("");
            lore.add(color(player, "&aLeft-click to buy."));
            if (AuctionCategoryUtil.isShulkerBox(display.getType())) {
                lore.add(color(player, "&eRight-click to preview contents."));
            }
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private ItemStack createPlayerAuctionDisplay(Player player, AuctionItem auction) {
        ItemStack display = auction.item();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (meta.hasLore()) {
                lore.addAll(meta.getLore());
                lore.add("");
            }
            lore.add(color(player, "&7Price: &a" + economy.format(auction.price())));
            lore.add(color(player, "&7Time left: &e" + auction.timeLeftFormatted()));
            lore.add("");
            lore.add(color(player, "&cClick to cancel this listing."));
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private ItemStack createSellDisplay(Player player, ItemStack item, double price, boolean fromHand) {
        ItemStack display = item.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (meta.hasLore()) {
                lore.addAll(meta.getLore());
                lore.add("");
            }
            lore.add(color(player, "&7Price: &a" + economy.format(price)));
            lore.add("");
            lore.add(color(player, fromHand ? "&eConfirming removes this stack from your hand." : "&eConfirming lists this item."));
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private ItemStack createCancelDisplay(Player player, AuctionItem auction) {
        ItemStack display = auction.item();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (meta.hasLore()) {
                lore.addAll(meta.getLore());
                lore.add("");
            }
            lore.add(color(player, "&7Price: &a" + economy.format(auction.price())));
            lore.add(color(player, "&7Time left: &e" + auction.timeLeftFormatted()));
            lore.add("");
            lore.add(color(player, "&cThis listing will be cancelled."));
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private ItemStack createTransactionDisplay(Player player, AuctionTransaction transaction) {
        ItemStack display = transaction.item();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            String typeText = switch (transaction.type()) {
                case BUY -> "&cPurchased";
                case SELL -> "&aSold";
                case CANCEL -> "&6Cancelled Listing";
                case EXPIRE -> "&7Expired Listing";
            };
            lore.add(color(player, "&7Type: " + typeText));
            lore.add(color(player, "&7Amount: &a" + economy.format(transaction.amount())));
            lore.add(color(player, "&7Time: &f" + transaction.formattedAge()));
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private ItemStack navItem(Player player, Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(player, name));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore == null ? List.<String>of() : lore) {
                coloredLore.add(color(player, line));
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack filler(Player player) {
        return navItem(player, Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private List<String> sortLore(Player player) {
        String current = sortModes.getOrDefault(player.getUniqueId(), "recent");
        return List.of("&7Click to change sorting.", "", option("recent", "Recent Listed", current), option("highest", "Highest Price", current), option("lowest", "Lowest Price", current), option("oldest", "Oldest Listed", current), option("shulker", "Shulker Only", current));
    }

    private List<String> filterLore(Player player) {
        String current = filterModes.getOrDefault(player.getUniqueId(), "all");
        return List.of("&7Click to filter items.", "", option("all", "All Categories", current), option("blocks", "Blocks", current), option("tools", "Tools", current), option("food", "Food", current), option("combat", "Combat", current), option("potions", "Potions", current), option("books", "Books", current), option("ingredients", "Ingredients", current), option("utilities", "Utilities", current));
    }

    private String option(String id, String label, String current) {
        return (id.equals(current) ? "&a" : "&7") + label;
    }

    private void cycleSort(Player player) {
        List<String> modes = List.of("recent", "highest", "lowest", "oldest", "shulker");
        String current = sortModes.getOrDefault(player.getUniqueId(), "recent");
        int next = (modes.indexOf(current) + 1) % modes.size();
        if (next < 0) {
            next = 0;
        }
        sortModes.put(player.getUniqueId(), modes.get(next));
        player.sendMessage(configColor("auction-house.messages.sort-changed", "&aSort mode changed to: &f%sort%").replace("%sort%", modes.get(next)));
        openMain(player, 1);
        click(player);
    }

    private void cycleFilter(Player player) {
        List<String> modes = List.of("all", "blocks", "tools", "food", "combat", "potions", "books", "ingredients", "utilities");
        String current = filterModes.getOrDefault(player.getUniqueId(), "all");
        int next = (modes.indexOf(current) + 1) % modes.size();
        if (next < 0) {
            next = 0;
        }
        filterModes.put(player.getUniqueId(), modes.get(next));
        player.sendMessage(configColor("auction-house.messages.filter-changed", "&aFilter changed to: &f%filter%").replace("%filter%", AuctionCategoryUtil.display(modes.get(next))));
        openMain(player, 1);
        click(player);
    }

    private void resetFilters(Player player) {
        sortModes.put(player.getUniqueId(), "recent");
        filterModes.put(player.getUniqueId(), "all");
        searchQueries.remove(player.getUniqueId());
    }

    private void promptSearch(Player player) {
        chatModes.put(player.getUniqueId(), ChatMode.SEARCH);
        player.closeInventory();
        player.sendMessage(configColor("auction-house.messages.search-prompt", "&aType the item name you want to search for:"));
        player.sendMessage(configColor("auction-house.messages.search-cancel-help", "&7Type 'cancel' to cancel."));
        click(player);
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("auction-house.enabled", true);
    }

    private boolean canUse(Player player) {
        return player.hasPermission("karamsmp.auction.use") || player.hasPermission("karamsmp.admin");
    }

    private boolean canSell(Player player) {
        return player.hasPermission("karamsmp.auction.sell") || player.hasPermission("karamsmp.admin");
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission("karamsmp.auction.admin") || sender.hasPermission("karamsmp.admin");
    }

    private int getMaxListings(Player player) {
        int max = plugin.getConfig().getInt("auction-house.max-listings-per-player", 10);
        if (player.hasPermission("karamsmp.auction.bypasslimit") || player.hasPermission("karamsmp.admin")) {
            return Math.max(max, plugin.getConfig().getInt("auction-house.admin-max-listings", 100));
        }
        return Math.max(1, max);
    }

    private double minPrice() {
        return Math.max(0.0D, plugin.getConfig().getDouble("auction-house.min-price", 0.01D));
    }

    private long durationMillis() {
        double hours = plugin.getConfig().getDouble("auction-house.duration-hours", 24.0D);
        double maxHours = plugin.getConfig().getDouble("auction-house.max-duration-hours", 720.0D);
        return (long) (Math.min(Math.max(0.1D, hours), Math.max(1.0D, maxHours)) * 60.0D * 60.0D * 1000.0D);
    }

    private boolean isCancelWord(String message) {
        return message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("exit") || message.equalsIgnoreCase("back");
    }

    private boolean canFit(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return true;
        }
        int remaining = item.getAmount();
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null || content.getType() == Material.AIR) {
                remaining -= item.getMaxStackSize();
            } else if (content.isSimilar(item)) {
                remaining -= Math.max(0, content.getMaxStackSize() - content.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private void returnItem(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        player.getInventory().addItem(item.clone()).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private String displayNamePlain(ItemStack item) {
        if (item == null) {
            return "Unknown";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return ChatColor.stripColor(meta.getDisplayName());
        }
        String name = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = name.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private void loadAuctions() {
        if (auctionFile == null || !auctionFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(auctionFile);
        ConfigurationSection section = yaml.getConfigurationSection("auctions");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            AuctionItem item = AuctionItem.load(id, section.getConfigurationSection(id));
            if (item != null) {
                auctions.put(item.id(), item);
            } else {
                plugin.getLogger().warning("Could not load auction entry: " + id);
            }
        }
        plugin.getLogger().info("Loaded " + auctions.size() + " auction listing(s).");
    }

    private void loadTransactions() {
        if (transactionFile == null || !transactionFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(transactionFile);
        ConfigurationSection section = yaml.getConfigurationSection("transactions");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            AuctionTransaction transaction = AuctionTransaction.load(id, section.getConfigurationSection(id));
            if (transaction != null) {
                transactions.put(transaction.id(), transaction);
            }
        }
    }

    private void saveData() {
        saveAuctions();
        saveTransactions();
    }

    private void saveAuctions() {
        if (auctionFile == null) {
            return;
        }
        try {
            ensureParent(auctionFile);
            YamlConfiguration yaml = new YamlConfiguration();
            for (AuctionItem auction : auctions.values()) {
                auction.save(yaml.createSection("auctions." + auction.id()));
            }
            yaml.save(auctionFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save auctions.yml: " + exception.getMessage());
        }
    }

    private void saveTransactions() {
        if (transactionFile == null) {
            return;
        }
        try {
            ensureParent(transactionFile);
            YamlConfiguration yaml = new YamlConfiguration();
            List<AuctionTransaction> newest = transactions.values().stream()
                    .sorted((a, b) -> Long.compare(b.timestamp(), a.timestamp()))
                    .limit(Math.max(100, plugin.getConfig().getInt("auction-house.max-saved-transactions", 5000)))
                    .toList();
            transactions.clear();
            for (AuctionTransaction transaction : newest) {
                transactions.put(transaction.id(), transaction);
                transaction.save(yaml.createSection("transactions." + transaction.id()));
            }
            yaml.save(transactionFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save transactions.yml: " + exception.getMessage());
        }
    }

    private void ensureParent(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create folder: " + parent.getPath());
        }
    }

    private String config(String path, String fallback) {
        return plugin.getConfig().getString(path, fallback);
    }

    private String configColor(String path, String fallback) {
        return MessageUtil.color(config(path, fallback));
    }

    private String color(Player player, String text) {
        return plugin.getRankManager() == null ? MessageUtil.color(text) : plugin.getRankManager().applyPlaceholders(player, text);
    }

    private void click(Player player) {
        SoundUtil.play(player, plugin.getConfig().getString("auction-house.sounds.click", "UI_BUTTON_CLICK"), 0.5F, 1.0F);
    }

    private void success(Player player) {
        SoundUtil.play(player, plugin.getConfig().getString("auction-house.sounds.success", "BLOCK_NOTE_BLOCK_PLING"), 0.5F, 1.0F);
    }

    private void deny(Player player) {
        SoundUtil.play(player, plugin.getConfig().getString("auction-house.sounds.error", "ENTITY_VILLAGER_NO"), 0.5F, 1.0F);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("sell")) {
            if (args.length == 1) {
                return filter(List.of("100", "1K", "10K", "100K", "1M"), args[0]);
            }
            return List.of();
        }
        if (args.length == 1) {
            List<String> sub = new ArrayList<>(List.of("sell", "search", "mine", "items", "transactions", "history"));
            if (hasAdmin(sender)) {
                sub.add("reload");
            }
            return filter(sub, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return filter(List.of("100", "1K", "10K", "100K", "1M"), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return values;
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}

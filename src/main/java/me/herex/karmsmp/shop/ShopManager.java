package me.herex.karmsmp.shop;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.economy.EconomyManager;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * KaramSMP shop system.
 *
 * This is a full merge of the uploaded DonutShop logic into KaramSMP:
 * - /shop main menu with category items from config.
 * - Category GUI with configured shop items.
 * - Buy confirmation menu with +/- amount buttons.
 * - /adminshop category selector and editor.
 * - Right click in the admin editor to remove configured items.
 * - Click an item from the player's inventory in the admin editor, then type a price in chat to add it.
 *
 * Improvements over the standalone DonutShop project:
 * - Uses KaramSMP's internal EconomyManager, so Vault is not required.
 * - Uses InventoryHolder instead of title matching, so color/custom titles cannot break clicks.
 * - Stores full ItemStack data for admin-added items, while still supporting simple material/name/price config.
 * - Checks inventory space before withdrawing money.
 * - Supports /shop <category>, /shop reload, and /adminshop <category>.
 */
public final class ShopManager implements Listener, CommandExecutor, TabCompleter {

    private static final int BUY_ITEM_SLOT = 13;
    private static final int BUY_CANCEL_SLOT = 21;
    private static final int BUY_CONFIRM_SLOT = 23;

    private final KaramSMP plugin;
    private final EconomyManager economy;
    private final Map<UUID, BuySession> buySessions = new HashMap<>();
    private final Map<UUID, PendingAdminItem> pendingAdminItems = new HashMap<>();

    public ShopManager(KaramSMP plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public void reload() {
        buySessions.clear();
        pendingAdminItems.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("adminshop")) {
            return handleAdminShop(sender, args);
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!hasAdmin(sender)) {
                sender.sendMessage(configColor("shop.messages.no-permission", "&cYou don't have permission to use this command!"));
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(configColor("shop.messages.reloaded", "&aShop configuration reloaded!"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(configColor("shop.messages.only-players", "&cOnly players can use this command!"));
            return true;
        }

        if (!plugin.getConfig().getBoolean("shop.enabled", true)) {
            player.sendMessage(configColor("shop.messages.disabled", "&cThe shop is disabled."));
            return true;
        }

        String permission = plugin.getConfig().getString("shop.permission", "");
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission) && !hasAdmin(player)) {
            player.sendMessage(configColor("shop.messages.no-permission", "&cYou don't have permission to use this command!"));
            return true;
        }

        if (args.length > 0) {
            String category = args[0].toLowerCase(Locale.ROOT);
            if (getCategoryIds().contains(category)) {
                openCategory(player, category);
                return true;
            }
            player.sendMessage(configColor("shop.messages.category-not-found", "&cThat shop category does not exist."));
            return true;
        }

        openMainMenu(player);
        return true;
    }

    private boolean handleAdminShop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configColor("shop.messages.only-players", "&cOnly players can use this command!"));
            return true;
        }
        if (!hasAdmin(player)) {
            player.sendMessage(configColor("shop.messages.no-permission", "&cYou don't have permission to use this command!"));
            return true;
        }
        if (args.length > 0) {
            String category = args[0].toLowerCase(Locale.ROOT);
            if (getCategoryIds().contains(category)) {
                openAdminEditor(player, category);
                return true;
            }
            player.sendMessage(configColor("shop.messages.category-not-found", "&cThat shop category does not exist."));
            return true;
        }
        openAdminSelector(player);
        return true;
    }

    public void openMainMenu(Player player) {
        String title = colorWithPlayer(player, plugin.getConfig().getString("shop.main-menu.title", "&8Shop"));
        int size = normalizeSize(plugin.getConfig().getInt("shop.main-menu.size", 27));
        Inventory inventory = Bukkit.createInventory(new ShopGuiHolder(ShopMenuType.MAIN, null), size, title);

        ConfigurationSection items = plugin.getConfig().getConfigurationSection("shop.main-menu.items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                int slot = parseSlot(key, -1);
                if (slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }
                inventory.setItem(slot, createMenuItem(player, "shop.main-menu.items." + key, 1, 0.0D, 0.0D));
            }
        }
        player.openInventory(inventory);
    }

    public void openCategory(Player player, String category) {
        if (category == null || category.isBlank()) {
            return;
        }
        String path = "shop.categories." + category;
        if (!plugin.getConfig().contains(path)) {
            player.sendMessage(configColor("shop.messages.category-not-found", "&cThat shop category does not exist."));
            return;
        }

        String title = colorWithPlayer(player, plugin.getConfig().getString(path + ".title", "&8" + category));
        int size = normalizeSize(plugin.getConfig().getInt(path + ".size", 27));
        Inventory inventory = Bukkit.createInventory(new ShopGuiHolder(ShopMenuType.CATEGORY, category), size, title);

        ConfigurationSection items = plugin.getConfig().getConfigurationSection(path + ".items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                int slot = parseSlot(key, -1);
                if (slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }
                String base = path + ".items." + key;
                ShopItem shopItem = loadShopItem(base);
                if (shopItem == null) {
                    plugin.getLogger().warning("Invalid shop item at " + base + ". Skipping it.");
                    continue;
                }
                inventory.setItem(slot, createShopDisplayItem(player, shopItem, base, 1, shopItem.price));
            }
        }

        int backSlot = plugin.getConfig().getInt("shop.category.back-slot", 18);
        if (backSlot >= 0 && backSlot < inventory.getSize()) {
            inventory.setItem(backSlot, createSimpleItem(player,
                    parseMaterial(plugin.getConfig().getString("shop.category.back-item.material", "RED_STAINED_GLASS_PANE"), Material.RED_STAINED_GLASS_PANE),
                    plugin.getConfig().getString("shop.category.back-item.name", "&cBack"),
                    getStringListFlex("shop.category.back-item.lore", List.of("&7Click to return"))));
        }
        player.openInventory(inventory);
    }

    public void openBuyMenu(Player player, BuySession session) {
        String rawTitle = plugin.getConfig().getString("shop.buy-menu.title", "&8Buy: %item%")
                .replace("%item%", session.displayNamePlain())
                .replace("%category%", session.category);
        Inventory inventory = Bukkit.createInventory(new ShopGuiHolder(ShopMenuType.BUY, session.category), 27, colorWithPlayer(player, rawTitle));
        updateBuyMenu(player, inventory, session);
        player.openInventory(inventory);
    }

    private void updateBuyMenu(Player player, Inventory inventory, BuySession session) {
        inventory.clear();
        double total = session.totalPrice();
        ItemStack display = session.createStack(Math.min(Math.max(1, session.amount), Math.max(1, session.prototype.getMaxStackSize())));
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorWithPlayer(player, applyBuyPlaceholders(
                    plugin.getConfig().getString("shop.buy-menu.display-item.name", "&e%item%"), session)));
            List<String> lore = new ArrayList<>();
            for (String line : getStringListFlex("shop.buy-menu.display-item.lore", List.of("&7Amount: &f%amount%", "&7Total Price: &a%total%"))) {
                lore.add(colorWithPlayer(player, applyBuyPlaceholders(line, session)));
            }
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        inventory.setItem(BUY_ITEM_SLOT, display);
        inventory.setItem(BUY_CANCEL_SLOT, createSimpleItem(player, Material.RED_STAINED_GLASS_PANE, "&cCancel", List.of("&7Click to cancel")));
        inventory.setItem(BUY_CONFIRM_SLOT, createSimpleItem(player, Material.LIME_STAINED_GLASS_PANE, "&aConfirm", List.of("&7Click to buy")));

        if (session.prototype.getMaxStackSize() > 1) {
            inventory.setItem(15, createSimpleItem(player, Material.LIME_STAINED_GLASS_PANE, "&aAdd 1", List.of("&7Click to add 1")));
            inventory.setItem(16, createSimpleItem(player, new ItemStack(Material.LIME_STAINED_GLASS_PANE, 10), "&aAdd 10", List.of("&7Click to add 10")));
            inventory.setItem(17, createSimpleItem(player, new ItemStack(Material.LIME_STAINED_GLASS_PANE, 64), "&aAdd 64", List.of("&7Click to add 64")));
            if (session.amount > 1) {
                inventory.setItem(11, createSimpleItem(player, Material.RED_STAINED_GLASS_PANE, "&cRemove 1", List.of("&7Click to remove 1")));
            }
            if (session.amount > 10) {
                inventory.setItem(10, createSimpleItem(player, new ItemStack(Material.RED_STAINED_GLASS_PANE, 10), "&cRemove 10", List.of("&7Click to remove 10")));
            }
            if (session.amount > 64) {
                inventory.setItem(9, createSimpleItem(player, new ItemStack(Material.RED_STAINED_GLASS_PANE, 64), "&cRemove 64", List.of("&7Click to remove 64")));
            }
        }
    }

    public void openAdminSelector(Player player) {
        String title = colorWithPlayer(player, plugin.getConfig().getString("shop.admin.selector-title", "&cAdmin: Select Category"));
        Inventory inventory = Bukkit.createInventory(new ShopGuiHolder(ShopMenuType.ADMIN_SELECTOR, null), 27, title);
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("shop.main-menu.items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                int slot = parseSlot(key, -1);
                if (slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }
                inventory.setItem(slot, createMenuItem(player, "shop.main-menu.items." + key, 1, 0.0D, 0.0D));
            }
        }
        player.openInventory(inventory);
    }

    public void openAdminEditor(Player player, String category) {
        String rawTitle = plugin.getConfig().getString("shop.admin.editor-title", "&cEdit: %category%").replace("%category%", category);
        int size = normalizeSize(plugin.getConfig().getInt("shop.categories." + category + ".size", 27));
        Inventory inventory = Bukkit.createInventory(new ShopGuiHolder(ShopMenuType.ADMIN_EDITOR, category), size, colorWithPlayer(player, rawTitle));
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("shop.categories." + category + ".items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                int slot = parseSlot(key, -1);
                if (slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }
                String base = "shop.categories." + category + ".items." + key;
                ShopItem shopItem = loadShopItem(base);
                if (shopItem == null) {
                    continue;
                }
                ItemStack item = createSimpleItem(player, shopItem.prototype,
                        shopItem.displayName,
                        List.of("&7Price: &a" + economy.format(shopItem.price), "&cRight-click to remove"));
                inventory.setItem(slot, item);
            }
        }
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopGuiHolder holder)) {
            return;
        }

        ShopMenuType type = holder.getType();
        if (type == ShopMenuType.ADMIN_EDITOR && event.getClickedInventory() == event.getView().getBottomInventory()) {
            handleAdminInventoryItemClick(event, player, holder.getCategory());
            return;
        }

        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        int slot = event.getSlot();
        switch (type) {
            case MAIN -> handleMainClick(player, slot);
            case CATEGORY -> handleCategoryClick(player, holder.getCategory(), slot);
            case BUY -> handleBuyClick(player, event.getView().getTopInventory(), slot);
            case ADMIN_SELECTOR -> handleAdminSelectorClick(player, slot);
            case ADMIN_EDITOR -> handleAdminEditorClick(player, holder.getCategory(), slot, event.isRightClick());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopGuiHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ShopGuiHolder holder)) {
            return;
        }
        if (holder.getType() == ShopMenuType.BUY) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof ShopGuiHolder openHolder)
                        || openHolder.getType() != ShopMenuType.BUY) {
                    buySessions.remove(player.getUniqueId());
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        PendingAdminItem pending = pendingAdminItems.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> handlePendingAdminPrice(event.getPlayer(), pending, message));
    }

    private void handleMainClick(Player player, int slot) {
        String category = plugin.getConfig().getString("shop.main-menu.items." + slot + ".category");
        if (category != null && !category.isBlank()) {
            openCategory(player, category);
        }
    }

    private void handleCategoryClick(Player player, String category, int slot) {
        int backSlot = plugin.getConfig().getInt("shop.category.back-slot", 18);
        if (slot == backSlot) {
            openMainMenu(player);
            return;
        }
        String path = "shop.categories." + category + ".items." + slot;
        ShopItem shopItem = loadShopItem(path);
        if (shopItem == null) {
            return;
        }
        BuySession session = new BuySession(category, slot, shopItem.prototype, shopItem.displayName, shopItem.price);
        buySessions.put(player.getUniqueId(), session);
        openBuyMenu(player, session);
    }

    private void handleBuyClick(Player player, Inventory inventory, int slot) {
        BuySession session = buySessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        if (slot == BUY_CANCEL_SLOT) {
            buySessions.remove(player.getUniqueId());
            openCategory(player, session.category);
            return;
        }
        if (slot == BUY_CONFIRM_SLOT) {
            processPurchase(player, session);
            return;
        }
        if (session.prototype.getMaxStackSize() > 1) {
            int maxAmount = Math.max(1, plugin.getConfig().getInt("shop.buy-menu.max-amount", 2304));
            if (slot == 15) {
                session.amount = Math.min(maxAmount, session.amount + 1);
            } else if (slot == 16) {
                session.amount = Math.min(maxAmount, session.amount + 10);
            } else if (slot == 17) {
                session.amount = Math.min(maxAmount, session.amount + 64);
            } else if (slot == 11) {
                session.amount = Math.max(1, session.amount - 1);
            } else if (slot == 10) {
                session.amount = Math.max(1, session.amount - 10);
            } else if (slot == 9) {
                session.amount = Math.max(1, session.amount - 64);
            }
            updateBuyMenu(player, inventory, session);
        }
    }

    private void processPurchase(Player player, BuySession session) {
        if (!plugin.getConfig().getBoolean("economy.enabled", true)) {
            player.sendMessage(configColor("shop.messages.economy-disabled", "&cThe economy system is disabled."));
            return;
        }
        double total = session.totalPrice();
        if (total <= 0.0D || !Double.isFinite(total)) {
            player.sendMessage(configColor("shop.messages.invalid-price", "&cThis item has an invalid price."));
            return;
        }
        double balance = economy.getBalance(player);
        if (balance + 0.000001D < total) {
            player.sendMessage(formatShopMessage(player, "shop.messages.not-enough-money", "&cInsufficient funds! Need &e%total%&c.", session, total));
            return;
        }
        if (!canFit(player, session.prototype, session.amount)) {
            player.sendMessage(configColor("shop.messages.inventory-full", "&cYou don't have enough inventory space."));
            return;
        }
        if (!economy.withdraw(player.getUniqueId(), player.getName(), total)) {
            player.sendMessage(formatShopMessage(player, "shop.messages.not-enough-money", "&cInsufficient funds! Need &e%total%&c.", session, total));
            return;
        }
        giveItems(player, session.prototype, session.amount);
        player.sendMessage(formatShopMessage(player, "shop.messages.bought", "&aBought &e%amount%x %item% &afor &e%total%&a.", session, total));
        plugin.getScoreboardManager().updatePlayer(player);
        updateBuyMenu(player, player.getOpenInventory().getTopInventory(), session);
    }

    private void handleAdminSelectorClick(Player player, int slot) {
        String category = plugin.getConfig().getString("shop.main-menu.items." + slot + ".category");
        if (category != null && !category.isBlank()) {
            openAdminEditor(player, category);
        }
    }

    private void handleAdminEditorClick(Player player, String category, int slot, boolean rightClick) {
        if (!rightClick) {
            return;
        }
        String path = "shop.categories." + category + ".items." + slot;
        if (!plugin.getConfig().contains(path)) {
            return;
        }
        plugin.getConfig().set(path, null);
        plugin.saveConfig();
        player.sendMessage(configColor("shop.messages.admin-removed", "&cItem removed!"));
        openAdminEditor(player, category);
    }

    private void handleAdminInventoryItemClick(InventoryClickEvent event, Player player, String category) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        event.setCancelled(true);
        player.closeInventory();
        ItemStack copy = clicked.clone();
        copy.setAmount(1);
        pendingAdminItems.put(player.getUniqueId(), new PendingAdminItem(category, copy));
        player.sendMessage(configColor("shop.messages.admin-enter-price", "&aEnter the price for this item in chat:"));
    }

    private void handlePendingAdminPrice(Player player, PendingAdminItem pending, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(configColor("shop.messages.admin-price-cancelled", "&cItem addition cancelled."));
            openAdminEditor(player, pending.category);
            return;
        }
        double price;
        try {
            price = economy.parseAmount(input);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(configColor("shop.messages.invalid-price", "&cInvalid price. Item addition cancelled."));
            openAdminEditor(player, pending.category);
            return;
        }
        int slot = findFreeItemSlot(pending.category);
        if (slot < 0) {
            player.sendMessage(configColor("shop.messages.admin-no-space", "&cThis category has no free slots."));
            openAdminEditor(player, pending.category);
            return;
        }
        ItemStack item = pending.item.clone();
        item.setAmount(1);
        String path = "shop.categories." + pending.category + ".items." + slot;
        plugin.getConfig().set(path + ".item", item);
        plugin.getConfig().set(path + ".material", item.getType().name());
        plugin.getConfig().set(path + ".name", getItemDisplayName(item, prettify(item.getType().name())));
        plugin.getConfig().set(path + ".price", price);
        plugin.saveConfig();
        player.sendMessage(configColor("shop.messages.admin-added", "&aItem added successfully!"));
        openAdminEditor(player, pending.category);
    }

    private int findFreeItemSlot(String category) {
        int size = normalizeSize(plugin.getConfig().getInt("shop.categories." + category + ".size", 27));
        int backSlot = plugin.getConfig().getInt("shop.category.back-slot", 18);
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("shop.categories." + category + ".items");
        for (int slot = 0; slot < size; slot++) {
            if (slot == backSlot) {
                continue;
            }
            if (items == null || !items.contains(String.valueOf(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private boolean hasAdmin(CommandSender sender) {
        String permission = plugin.getConfig().getString("shop.admin-permission", "karamsmp.shop.admin");
        return sender.hasPermission("karamsmp.admin")
                || (permission != null && !permission.isBlank() && sender.hasPermission(permission))
                || sender.isOp();
    }

    private boolean canFit(Player player, ItemStack prototype, int amount) {
        int remaining = amount;
        int maxStack = Math.max(1, prototype.getMaxStackSize());
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null || content.getType() == Material.AIR) {
                remaining -= maxStack;
            } else if (content.isSimilar(prototype)) {
                remaining -= Math.max(0, maxStack - content.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private void giveItems(Player player, ItemStack prototype, int amount) {
        int remaining = amount;
        int maxStack = Math.max(1, prototype.getMaxStackSize());
        while (remaining > 0) {
            int stackAmount = Math.min(maxStack, remaining);
            ItemStack stack = prototype.clone();
            stack.setAmount(stackAmount);
            player.getInventory().addItem(stack);
            remaining -= stackAmount;
        }
    }

    private ItemStack createMenuItem(Player player, String base, int amount, double price, double total) {
        Material material = parseMaterial(plugin.getConfig().getString(base + ".material", "STONE"), Material.STONE);
        String name = plugin.getConfig().getString(base + ".name", prettify(material.name()));
        List<String> lore = getStringListFlex(base + ".lore", List.of());
        return createSimpleItem(player, material, name, lore, amount, null, price, total);
    }

    private ItemStack createShopDisplayItem(Player player, ShopItem shopItem, String base, int amount, double total) {
        List<String> lore = getStringListFlex(base + ".lore", getStringListFlex("shop.category.item-lore", List.of("&7Buy price: &a%price%", "&eClick to buy")));
        return createSimpleItem(player, shopItem.prototype, shopItem.displayName, lore, amount, null, shopItem.price, total);
    }

    private ItemStack createSimpleItem(Player player, Material material, String name, List<String> lore) {
        return createSimpleItem(player, material, name, lore, 1, null, 0.0D, 0.0D);
    }

    private ItemStack createSimpleItem(Player player, Material material, String name, List<String> lore, int amount, @Nullable String category, double price, double total) {
        return createSimpleItem(player, new ItemStack(material, Math.max(1, Math.min(amount, 64))), name, lore, category, price, total);
    }

    private ItemStack createSimpleItem(Player player, ItemStack item, String name, List<String> lore) {
        return createSimpleItem(player, item, name, lore, null, 0.0D, 0.0D);
    }

    private ItemStack createSimpleItem(Player player, ItemStack item, String name, List<String> lore, @Nullable String category, double price, double total) {
        ItemStack copy = item.clone();
        copy.setAmount(Math.max(1, Math.min(copy.getAmount(), 64)));
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorWithPlayer(player, applyCommonShopPlaceholders(name, category, copy.getAmount(), price, total)));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore == null ? List.<String>of() : lore) {
                coloredLore.add(colorWithPlayer(player, applyCommonShopPlaceholders(line, category, copy.getAmount(), price, total)));
            }
            meta.setLore(coloredLore);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    private ShopItem loadShopItem(String base) {
        if (!plugin.getConfig().contains(base)) {
            return null;
        }
        ItemStack prototype = plugin.getConfig().getItemStack(base + ".item");
        if (prototype == null || prototype.getType() == Material.AIR) {
            Material material = parseMaterial(plugin.getConfig().getString(base + ".material", "STONE"), null);
            if (material == null || material == Material.AIR) {
                return null;
            }
            prototype = new ItemStack(material);
        } else {
            prototype = prototype.clone();
        }
        prototype.setAmount(1);
        double price = plugin.getConfig().getDouble(base + ".price", plugin.getConfig().getDouble(base + ".buy-price", 0.0D));
        String fallbackName = getItemDisplayName(prototype, prettify(prototype.getType().name()));
        String displayName = plugin.getConfig().getString(base + ".name", fallbackName);
        return new ShopItem(prototype, displayName, price);
    }

    private String getItemDisplayName(ItemStack item, String fallback) {
        if (item != null && item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return fallback;
    }

    private String formatShopMessage(Player player, String path, String fallback, BuySession session, double total) {
        String raw = plugin.getConfig().getString(path, fallback);
        return colorWithPlayer(player, applyBuyPlaceholders(raw, session).replace("%total%", economy.format(total)));
    }

    private String applyBuyPlaceholders(String input, BuySession session) {
        return applyCommonShopPlaceholders(input, session.category, session.amount, session.unitPrice, session.totalPrice())
                .replace("%item%", session.displayName)
                .replace("%item_plain%", session.displayNamePlain());
    }

    private String applyCommonShopPlaceholders(String input, @Nullable String category, int amount, double price, double total) {
        if (input == null) {
            return "";
        }
        return input
                .replace("%category%", category == null ? "" : category)
                .replace("%amount%", String.valueOf(amount))
                .replace("%price%", economy.format(price))
                .replace("%price_plain%", economy.formatPlain(price))
                .replace("%total%", economy.format(total))
                .replace("%total_plain%", economy.formatPlain(total));
    }

    private String colorWithPlayer(Player player, String text) {
        return plugin.getRankManager().applyPlaceholders(player, text == null ? "" : text);
    }

    private String configColor(String path, String fallback) {
        return MessageUtil.color(plugin.getConfig().getString(path, fallback));
    }

    private List<String> getStringListFlex(String path, List<String> fallback) {
        if (plugin.getConfig().isList(path)) {
            return plugin.getConfig().getStringList(path);
        }
        String single = plugin.getConfig().getString(path);
        if (single != null) {
            return List.of(single);
        }
        return fallback == null ? List.of() : fallback;
    }

    private Material parseMaterial(String raw, @Nullable Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private int parseSlot(String key, int fallback) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private int normalizeSize(int size) {
        int rows = Math.max(1, Math.min(6, (int) Math.ceil(size / 9.0D)));
        return rows * 9;
    }

    private String prettify(String materialName) {
        String[] parts = materialName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("shop")) {
            if (args.length == 1) {
                List<String> values = new ArrayList<>(getCategoryIds());
                if (hasAdmin(sender)) {
                    values.add("reload");
                }
                return filter(values, args[0]);
            }
        }
        if (commandName.equals("adminshop") && args.length == 1 && hasAdmin(sender)) {
            return filter(new ArrayList<>(getCategoryIds()), args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return values;
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    public Set<String> getCategoryIds() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("shop.categories");
        if (section == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(section.getKeys(false));
    }

    private static final class ShopItem {
        private final ItemStack prototype;
        private final String displayName;
        private final double price;

        private ShopItem(ItemStack prototype, String displayName, double price) {
            this.prototype = prototype;
            this.displayName = displayName;
            this.price = price;
        }
    }

    private static final class BuySession {
        private final String category;
        private final int slot;
        private final ItemStack prototype;
        private final String displayName;
        private final double unitPrice;
        private int amount = 1;

        private BuySession(String category, int slot, ItemStack prototype, String displayName, double unitPrice) {
            this.category = category;
            this.slot = slot;
            this.prototype = prototype.clone();
            this.prototype.setAmount(1);
            this.displayName = displayName;
            this.unitPrice = unitPrice;
        }

        private double totalPrice() {
            return amount * unitPrice;
        }

        private String displayNamePlain() {
            return MessageUtil.stripColor(displayName);
        }

        private ItemStack createStack(int amount) {
            ItemStack stack = prototype.clone();
            stack.setAmount(Math.max(1, Math.min(amount, 64)));
            return stack;
        }
    }

    private static final class PendingAdminItem {
        private final String category;
        private final ItemStack item;

        private PendingAdminItem(String category, ItemStack item) {
            this.category = category;
            this.item = item;
        }
    }
}

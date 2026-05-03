package me.herex.karmsmp.settings;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.utils.MessageUtil;
import me.herex.karmsmp.utils.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SettingsManager {

    private final KaramSMP plugin;
    private final File file;
    private YamlConfiguration data;
    private final Map<String, SettingOption> options = new LinkedHashMap<>();

    public SettingsManager(KaramSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), plugin.getConfig().getString("settings.data-file", "settings.yml"));
        load();
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        data = YamlConfiguration.loadConfiguration(file);
        loadOptions();
        save();
    }

    public void reload() {
        loadOptions();
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save settings.yml: " + exception.getMessage());
        }
    }

    public boolean isEnabled(Player player, String settingId) {
        return isEnabled(player.getUniqueId(), settingId);
    }

    public boolean isEnabled(UUID uuid, String settingId) {
        String id = normalize(settingId);
        SettingOption option = options.get(id);
        boolean fallback = option == null || option.isDefaultEnabled();
        return data.getBoolean("players." + uuid + "." + id, fallback);
    }

    public void setEnabled(Player player, String settingId, boolean enabled) {
        String id = normalize(settingId);
        data.set("players." + player.getUniqueId() + "." + id, enabled);
        save();
    }

    public boolean toggle(Player player, String settingId) {
        boolean newValue = !isEnabled(player, settingId);
        setEnabled(player, settingId, newValue);
        return newValue;
    }

    public boolean toggleBySlot(Player player, int slot) {
        SettingOption option = getOptionBySlot(slot);
        if (option == null) {
            return false;
        }
        boolean enabled = toggle(player, option.getId());
        String sound = plugin.getConfig().getString("settings.toggle-sound.name", "BLOCK_DISPENSER_FAIL");
        float volume = (float) plugin.getConfig().getDouble("settings.toggle-sound.volume", 1.0D);
        float pitch = (float) plugin.getConfig().getDouble("settings.toggle-sound.pitch", enabled ? 1.2D : 0.8D);
        SoundUtil.play(player, sound, volume, pitch);
        open(player);
        return true;
    }

    public void open(Player player) {
        if (!plugin.getConfig().getBoolean("settings.enabled", true)) {
            player.sendMessage(MessageUtil.color(plugin.getConfig().getString("settings.messages.disabled", "&cSettings are disabled.")));
            return;
        }

        int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("settings.gui.rows", 4)));
        String title = MessageUtil.color(plugin.getConfig().getString("settings.gui.title", "<##3F3F3F>Settings"));
        Inventory inventory = Bukkit.createInventory(new SettingsGuiHolder(player.getUniqueId()), rows * 9, title);

        for (SettingOption option : options.values()) {
            if (option.getSlot() < 0 || option.getSlot() >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(option.getSlot(), createItem(player, option));
        }

        player.openInventory(inventory);
    }

    public Collection<SettingOption> getOptions() {
        return options.values();
    }

    public SettingOption getOption(String id) {
        return options.get(normalize(id));
    }

    public SettingOption getOptionBySlot(int slot) {
        for (SettingOption option : options.values()) {
            if (option.getSlot() == slot) {
                return option;
            }
        }
        return null;
    }

    public String getStatusText(Player player, String settingId) {
        return isEnabled(player, settingId)
                ? plugin.getConfig().getString("settings.status.on-text", "ON")
                : plugin.getConfig().getString("settings.status.off-text", "OFF");
    }

    public String getStatusColor(Player player, String settingId) {
        return isEnabled(player, settingId)
                ? plugin.getConfig().getString("settings.status.on-color", "<##7AFC00>")
                : plugin.getConfig().getString("settings.status.off-color", "<##FC0000>");
    }

    public static String normalize(String id) {
        if (id == null) {
            return "";
        }
        return id.toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }

    public static String placeholderKey(String id) {
        return normalize(id).replace('-', '_');
    }

    private ItemStack createItem(Player player, SettingOption option) {
        Material material = Material.matchMaterial(option.getMaterial());
        if (material == null) {
            material = Material.PAPER;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(applySettingPlaceholders(player, option, option.getName()));
            List<String> lore = new ArrayList<>();
            for (String line : option.getLore()) {
                lore.add(applySettingPlaceholders(player, option, line));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String applySettingPlaceholders(Player player, SettingOption option, String text) {
        String value = text == null ? "" : text;
        value = value.replace("%setting%", option.getId())
                .replace("%status%", getStatusText(player, option.getId()))
                .replace("%status_color%", getStatusColor(player, option.getId()))
                .replace("%enabled%", String.valueOf(isEnabled(player, option.getId())));
        return plugin.getRankManager().applyPlaceholders(player, value);
    }

    private void loadOptions() {
        options.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("settings.options");
        if (section == null) {
            loadDefaultOptions();
            return;
        }

        for (String key : section.getKeys(false)) {
            String base = "settings.options." + key;
            String id = normalize(key);
            int slot = plugin.getConfig().getInt(base + ".slot", -1);
            String material = plugin.getConfig().getString(base + ".material", "PAPER");
            String name = plugin.getConfig().getString(base + ".name", "&a" + id);
            List<String> lore = plugin.getConfig().getStringList(base + ".lore");
            if (lore.isEmpty()) {
                lore = List.of("&fCurrently: %status_color%&l%status%");
            }
            boolean defaultEnabled = plugin.getConfig().getBoolean(base + ".default", true);
            options.put(id, new SettingOption(id, slot, material, name, lore, defaultEnabled));
        }

        if (options.isEmpty()) {
            loadDefaultOptions();
        }
    }

    private void loadDefaultOptions() {
        addDefault("public-chat", 10, "OAK_SIGN", "<##00F986>ᴘᴜʙʟɪᴄ ᴄʜᴀᴛ", true);
        addDefault("private-messages", 11, "DARK_OAK_SIGN", "<##00F986>ᴘʀɪᴠᴀᴛᴇ ᴍᴇꜱꜱᴀɢᴇꜱ", true);
        addDefault("chat-server-messages", 12, "WARPED_SIGN", "<##00F986>ᴄʜᴀᴛ ꜱᴇʀᴠᴇʀ ᴍᴇꜱꜱᴀɢᴇꜱ", true);
        addDefault("hotbar-server-messages", 13, "CRIMSON_SIGN", "<##00F986>ʜᴏᴛʙᴀʀ ꜱᴇʀᴠᴇʀ ᴍᴇꜱꜱᴀɢᴇꜱ", true);
        addDefault("pay-alerts", 14, "CHERRY_SIGN", "<##00F986>ᴘᴀʏ ᴀʟᴇʀᴛꜱ", true);
        addDefault("bounty-alerts", 15, "BAMBOO_SIGN", "<##00F986>ʙᴏᴜɴᴛʏ ᴀʟᴇʀᴛꜱ", true);
        addDefault("auction-alerts", 16, "ACACIA_SIGN", "<##00F986>ᴀᴜᴄᴛɪᴏɴ ᴀʟᴇʀᴛꜱ", true);
        addDefault("fast-crystals", 19, "END_CRYSTAL", "<##00F986>ꜰᴀꜱᴛ ᴄʀʏꜱᴛᴀʟꜱ", true);
        addDefault("tpa-requests", 20, "ENDER_PEARL", "<##00F986>ᴛᴘᴀ ʀᴇqᴜᴇꜱᴛꜱ", true);
        addDefault("tpa-here-requests", 21, "ENDER_EYE", "<##00F986>ᴛᴘᴀ ʜᴇʀᴇ ʀᴇqᴜᴇꜱᴛꜱ", true);
        addDefault("team-invites", 22, "SHIELD", "<##00F986>ᴛᴇᴀᴍ ɪɴᴠɪᴛᴇꜱ", true);
        addDefault("payments", 23, "EMERALD", "<##00F986>ᴘᴀʏᴍᴇɴᴛꜱ", true);
        addDefault("team-chat", 24, "BELL", "<##00F986>ᴛᴇᴀᴍ ᴄʜᴀᴛ", false);
        options.put("do-not-disturb", new SettingOption("do-not-disturb", 25, "PAPER", "<##00F986>ᴅᴏ ɴᴏᴛ ᴅɪꜱᴛʀᴜʙ", List.of(
                "&7Tired of spam tpa messages?",
                "&7We're trying to improve our chat",
                "&7enable this setting and try it out",
                "&7give us feedback in /discord.",
                "&fCurrently: %status_color%&l%status%",
                "",
                "<##00F986>ᴘʀᴏᴛᴏᴛʏᴘᴇ ꜱʏꜱᴛᴇᴍ"
        ), false));
    }

    private void addDefault(String id, int slot, String material, String name, boolean defaultEnabled) {
        options.put(id, new SettingOption(id, slot, material, name, List.of("&fCurrently: %status_color%&l%status%"), defaultEnabled));
    }
}

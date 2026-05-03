package me.herex.karmsmp.regions;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionManager {

    public static final String ADMIN_PERMISSION = "karamsmp.regions.admin";
    public static final String BYPASS_PERMISSION = "karamsmp.regions.bypass";

    private final KaramSMP plugin;
    private final Map<String, Region> regions = new ConcurrentHashMap<>();
    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();
    private final NamespacedKey wandKey;
    private final Set<String> availableFlags = new HashSet<>();
    private File file;
    private YamlConfiguration data;

    public RegionManager(KaramSMP plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "region_wand");
    }

    public void load() {
        regions.clear();
        loadAvailableFlags();

        file = new File(plugin.getDataFolder(), plugin.getConfig().getString("regions.data-file", "regions.yml"));
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not create regions.yml: " + exception.getMessage());
            }
        }

        data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("regions");
        Map<String, Boolean> defaults = getDefaultFlags();

        if (section != null) {
            for (String regionName : section.getKeys(false)) {
                Region.fromConfig(regionName, section.getConfigurationSection(regionName), defaults)
                        .ifPresent(region -> regions.put(region.getName(), region));
            }
        }

        plugin.getLogger().info("Loaded " + regions.size() + " KaramSMP region(s).");
    }

    public void reload() {
        load();
    }

    public void save() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), plugin.getConfig().getString("regions.data-file", "regions.yml"));
        }

        YamlConfiguration output = new YamlConfiguration();
        for (Region region : getRegions()) {
            ConfigurationSection section = output.createSection("regions." + region.getName());
            region.writeTo(section);
        }

        try {
            output.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save regions.yml: " + exception.getMessage());
        }
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("regions.enabled", true);
    }

    public Selection getSelection(Player player) {
        return selections.computeIfAbsent(player.getUniqueId(), uuid -> new Selection());
    }

    public void setPositionOne(Player player, Location location) {
        getSelection(player).setPositionOne(location.getBlock().getLocation());
    }

    public void setPositionTwo(Player player, Location location) {
        getSelection(player).setPositionTwo(location.getBlock().getLocation());
    }

    public Optional<Region> createRegion(Player creator, String name) {
        String cleanName = Region.cleanName(name);
        if (cleanName.isBlank() || regions.containsKey(cleanName)) {
            return Optional.empty();
        }

        Selection selection = getSelection(creator);
        if (!selection.isComplete()) {
            return Optional.empty();
        }

        Region region = new Region(cleanName, selection.getPositionOne(), selection.getPositionTwo(), plugin.getConfig().getInt("regions.defaults.priority", 0), getDefaultFlags());
        region.addOwner(creator.getUniqueId());
        regions.put(region.getName(), region);
        save();
        return Optional.of(region);
    }

    public boolean deleteRegion(String name) {
        Region removed = regions.remove(Region.cleanName(name));
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public boolean renameRegion(String oldName, String newName) {
        String oldKey = Region.cleanName(oldName);
        String newKey = Region.cleanName(newName);
        if (oldKey.isBlank() || newKey.isBlank() || regions.containsKey(newKey)) {
            return false;
        }

        Region region = regions.remove(oldKey);
        if (region == null) {
            return false;
        }

        World world = getWorldOrNull(region.getWorldName());
        if (world == null) {
            regions.put(oldKey, region);
            return false;
        }

        Region copy = new Region(newKey,
                new Location(world, region.getMinX(), region.getMinY(), region.getMinZ()),
                new Location(world, region.getMaxX(), region.getMaxY(), region.getMaxZ()),
                region.getPriority(), region.getFlags());
        copy.setGreeting(region.getGreeting());
        copy.setFarewell(region.getFarewell());
        region.getOwners().forEach(copy::addOwner);
        region.getMembers().forEach(copy::addMember);

        regions.put(copy.getName(), copy);
        save();
        return true;
    }

    public boolean redefineRegion(Player player, String name) {
        Optional<Region> optional = getRegion(name);
        if (optional.isEmpty()) {
            return false;
        }

        Selection selection = getSelection(player);
        if (!selection.isComplete()) {
            return false;
        }

        optional.get().redefine(selection.getPositionOne(), selection.getPositionTwo());
        save();
        return true;
    }

    public boolean setFlag(String name, String flag, boolean value) {
        Optional<Region> region = getRegion(name);
        String normalized = Region.normalizeFlag(flag);
        if (region.isEmpty() || !availableFlags.contains(normalized)) {
            return false;
        }

        region.get().setFlag(normalized, value);
        save();
        return true;
    }

    public boolean resetFlag(String name, String flag) {
        Optional<Region> region = getRegion(name);
        String normalized = Region.normalizeFlag(flag);
        if (region.isEmpty() || !availableFlags.contains(normalized)) {
            return false;
        }

        region.get().removeFlag(normalized);
        save();
        return true;
    }

    public boolean setPriority(String name, int priority) {
        Optional<Region> region = getRegion(name);
        if (region.isEmpty()) {
            return false;
        }

        region.get().setPriority(priority);
        save();
        return true;
    }

    public boolean expand(String name, String direction, int amount) {
        Optional<Region> region = getRegion(name);
        if (region.isEmpty()) {
            return false;
        }

        region.get().expand(direction, amount);
        save();
        return true;
    }

    public boolean contract(String name, String direction, int amount) {
        Optional<Region> region = getRegion(name);
        if (region.isEmpty()) {
            return false;
        }

        region.get().contract(direction, amount);
        save();
        return true;
    }

    public boolean setMessage(String name, String type, String message) {
        Optional<Region> region = getRegion(name);
        if (region.isEmpty()) {
            return false;
        }

        if (type.equalsIgnoreCase("greeting") || type.equalsIgnoreCase("enter")) {
            region.get().setGreeting(message);
        } else if (type.equalsIgnoreCase("farewell") || type.equalsIgnoreCase("leave")) {
            region.get().setFarewell(message);
        } else {
            return false;
        }

        save();
        return true;
    }

    public boolean addOwner(String name, OfflinePlayer player) {
        Optional<Region> region = getRegion(name);
        if (region.isEmpty()) {
            return false;
        }
        region.get().addOwner(player.getUniqueId());
        save();
        return true;
    }

    public boolean removeOwner(String name, OfflinePlayer player) {
        Optional<Region> region = getRegion(name);
        if (region.isEmpty()) {
            return false;
        }
        region.get().removeOwner(player.getUniqueId());
        save();
        return true;
    }

    public boolean addMember(String name, OfflinePlayer player) {
        Optional<Region> region = getRegion(name);
        if (region.isEmpty()) {
            return false;
        }
        region.get().addMember(player.getUniqueId());
        save();
        return true;
    }

    public boolean removeMember(String name, OfflinePlayer player) {
        Optional<Region> region = getRegion(name);
        if (region.isEmpty()) {
            return false;
        }
        region.get().removeMember(player.getUniqueId());
        save();
        return true;
    }

    public Optional<Region> getRegion(String name) {
        return Optional.ofNullable(regions.get(Region.cleanName(name)));
    }

    public List<Region> getRegions() {
        return regions.values().stream()
                .sorted(Comparator.comparingInt(Region::getPriority).reversed().thenComparing(Region::getName))
                .toList();
    }

    public List<Region> getRegionsAt(Location location) {
        return getRegions().stream().filter(region -> region.contains(location)).toList();
    }

    public Optional<Region> getHighestRegionAt(Location location) {
        return getRegionsAt(location).stream().findFirst();
    }

    public boolean isActionAllowed(Player player, Location location, String flag) {
        if (!isEnabled()) {
            return true;
        }

        Optional<Region> optional = getHighestRegionAt(location);
        if (optional.isEmpty()) {
            return true;
        }

        Region region = optional.get();
        if (canBypass(player)) {
            return true;
        }

        String normalized = Region.normalizeFlag(flag);
        if (region.isMember(player.getUniqueId())) {
            if (isBuildFlag(normalized) && plugin.getConfig().getBoolean("regions.members.bypass-build-flags", true)) {
                return true;
            }
            if (isInteractFlag(normalized) && plugin.getConfig().getBoolean("regions.members.bypass-interact-flags", true)) {
                return true;
            }
        }

        return region.isFlagAllowed(normalized, getDefaultFlag(normalized));
    }

    public boolean isLocationFlagAllowed(Location location, String flag) {
        if (!isEnabled()) {
            return true;
        }

        Optional<Region> optional = getHighestRegionAt(location);
        return optional.map(region -> region.isFlagAllowed(flag, getDefaultFlag(flag))).orElse(true);
    }

    public boolean canBypass(Player player) {
        return player != null && (player.hasPermission(BYPASS_PERMISSION) || player.hasPermission(ADMIN_PERMISSION));
    }

    public ItemStack createWand() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("regions.wand.material", "GOLDEN_AXE"));
        if (material == null) {
            material = Material.GOLDEN_AXE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.color(plugin.getConfig().getString("regions.wand.name", "&6KaramSMP Region Wand")));
            meta.setLore(plugin.getConfig().getStringList("regions.wand.lore").stream().map(MessageUtil::color).toList());
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        if (meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE)) {
            return true;
        }

        Material configuredMaterial = Material.matchMaterial(plugin.getConfig().getString("regions.wand.material", "GOLDEN_AXE"));
        if (configuredMaterial == null) {
            configuredMaterial = Material.GOLDEN_AXE;
        }

        if (item.getType() != configuredMaterial) {
            return false;
        }

        if (plugin.getConfig().getBoolean("regions.wand.match-any-configured-material", true)) {
            return true;
        }

        if (!plugin.getConfig().getBoolean("regions.wand.match-by-name", true)) {
            return false;
        }

        String configuredName = MessageUtil.color(plugin.getConfig().getString("regions.wand.name", "&6KaramSMP Region Wand"));
        return meta.hasDisplayName() && meta.getDisplayName().equals(configuredName);
    }

    public Set<String> getAvailableFlags() {
        return Set.copyOf(availableFlags);
    }

    public String formatMessage(String path, Player player, Region region) {
        String message = plugin.getConfig().getString(path, "");
        return formatRawMessage(message, player, region);
    }

    public String formatRawMessage(String message, Player player, Region region) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String text = message
                .replace("%region%", region == null ? "" : region.getName())
                .replace("%player%", player == null ? "" : player.getName());
        if (player != null) {
            text = plugin.getRankManager().applyPlaceholders(player, text);
        }
        return MessageUtil.color(text);
    }

    public String getRegionsPlaceholder(Player player) {
        List<String> names = getRegionsAt(player.getLocation()).stream().map(Region::getName).toList();
        return names.isEmpty() ? "none" : String.join(", ", names);
    }

    public String getTopRegionPlaceholder(Player player) {
        return getHighestRegionAt(player.getLocation()).map(Region::getName).orElse("none");
    }

    public String getRegionCountPlaceholder(Player player) {
        return String.valueOf(getRegionsAt(player.getLocation()).size());
    }

    private void loadAvailableFlags() {
        availableFlags.clear();
        availableFlags.addAll(List.of(
                "block-break",
                "block-place",
                "pvp",
                "fall-damage",
                "interact",
                "chest-access",
                "item-drop",
                "item-pickup",
                "mob-spawning",
                "explosions",
                "fire-spread",
                "entry",
                "exit",
                "double-jump"
        ));
        for (String flag : plugin.getConfig().getStringList("regions.available-flags")) {
            String normalized = Region.normalizeFlag(flag);
            if (!normalized.isBlank()) {
                availableFlags.add(normalized);
            }
        }
    }

    private Map<String, Boolean> getDefaultFlags() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        for (String flag : availableFlags) {
            defaults.put(flag, getDefaultFlag(flag));
        }
        return defaults;
    }

    private boolean getDefaultFlag(String flag) {
        String normalized = Region.normalizeFlag(flag);
        Map<String, Boolean> hardDefaults = new HashMap<>();
        hardDefaults.put("block-break", false);
        hardDefaults.put("block-place", false);
        hardDefaults.put("pvp", false);
        hardDefaults.put("fall-damage", true);
        hardDefaults.put("interact", true);
        hardDefaults.put("chest-access", true);
        hardDefaults.put("item-drop", true);
        hardDefaults.put("item-pickup", true);
        hardDefaults.put("mob-spawning", true);
        hardDefaults.put("explosions", false);
        hardDefaults.put("fire-spread", false);
        hardDefaults.put("entry", true);
        hardDefaults.put("exit", true);
        hardDefaults.put("double-jump", false);
        return plugin.getConfig().getBoolean("regions.defaults.flags." + normalized, hardDefaults.getOrDefault(normalized, true));
    }

    private boolean isBuildFlag(String flag) {
        return flag.equals("block-break") || flag.equals("block-place");
    }

    private boolean isInteractFlag(String flag) {
        return flag.equals("interact") || flag.equals("chest-access");
    }

    private World getWorldOrNull(String name) {
        return name == null ? null : plugin.getServer().getWorld(name);
    }
}

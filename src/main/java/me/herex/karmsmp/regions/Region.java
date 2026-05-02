package me.herex.karmsmp.regions;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Region {

    private final String name;
    private String worldName;
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    private int priority;
    private String greeting;
    private String farewell;
    private final Set<UUID> owners = new HashSet<>();
    private final Set<UUID> members = new HashSet<>();
    private final Map<String, Boolean> flags = new HashMap<>();

    public Region(String name, Location positionOne, Location positionTwo, int priority, Map<String, Boolean> defaultFlags) {
        this.name = cleanName(name);
        redefine(positionOne, positionTwo);
        this.priority = priority;
        if (defaultFlags != null) {
            this.flags.putAll(defaultFlags);
        }
        this.greeting = "";
        this.farewell = "";
    }

    private Region(String name) {
        this.name = cleanName(name);
    }

    public static Optional<Region> fromConfig(String name, ConfigurationSection section, Map<String, Boolean> defaultFlags) {
        if (section == null) {
            return Optional.empty();
        }

        Region region = new Region(name);
        region.worldName = section.getString("world", "world");
        region.minX = section.getInt("min.x");
        region.minY = section.getInt("min.y");
        region.minZ = section.getInt("min.z");
        region.maxX = section.getInt("max.x");
        region.maxY = section.getInt("max.y");
        region.maxZ = section.getInt("max.z");
        region.normalizeBounds();
        region.priority = section.getInt("priority", 0);
        region.greeting = section.getString("greeting", "");
        region.farewell = section.getString("farewell", "");

        for (String owner : section.getStringList("owners")) {
            parseUuid(owner).ifPresent(region.owners::add);
        }
        for (String member : section.getStringList("members")) {
            parseUuid(member).ifPresent(region.members::add);
        }

        if (defaultFlags != null) {
            region.flags.putAll(defaultFlags);
        }

        ConfigurationSection flagSection = section.getConfigurationSection("flags");
        if (flagSection != null) {
            for (String flag : flagSection.getKeys(false)) {
                region.flags.put(normalizeFlag(flag), flagSection.getBoolean(flag));
            }
        }

        return Optional.of(region);
    }

    public void writeTo(ConfigurationSection section) {
        section.set("world", worldName);
        section.set("min.x", minX);
        section.set("min.y", minY);
        section.set("min.z", minZ);
        section.set("max.x", maxX);
        section.set("max.y", maxY);
        section.set("max.z", maxZ);
        section.set("priority", priority);
        section.set("greeting", greeting == null ? "" : greeting);
        section.set("farewell", farewell == null ? "" : farewell);
        section.set("owners", owners.stream().map(UUID::toString).sorted().toList());
        section.set("members", members.stream().map(UUID::toString).sorted().toList());

        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            section.set("flags." + entry.getKey(), entry.getValue());
        }
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public void redefine(Location positionOne, Location positionTwo) {
        if (positionOne == null || positionTwo == null || positionOne.getWorld() == null || positionTwo.getWorld() == null) {
            throw new IllegalArgumentException("Both positions must be valid locations.");
        }
        if (!positionOne.getWorld().equals(positionTwo.getWorld())) {
            throw new IllegalArgumentException("Both positions must be in the same world.");
        }

        this.worldName = positionOne.getWorld().getName();
        this.minX = Math.min(positionOne.getBlockX(), positionTwo.getBlockX());
        this.minY = Math.min(positionOne.getBlockY(), positionTwo.getBlockY());
        this.minZ = Math.min(positionOne.getBlockZ(), positionTwo.getBlockZ());
        this.maxX = Math.max(positionOne.getBlockX(), positionTwo.getBlockX());
        this.maxY = Math.max(positionOne.getBlockY(), positionTwo.getBlockY());
        this.maxZ = Math.max(positionOne.getBlockZ(), positionTwo.getBlockZ());
    }

    public void expand(String direction, int amount) {
        if (amount < 0) {
            amount = -amount;
        }

        switch (direction.toLowerCase(Locale.ROOT)) {
            case "up", "u" -> maxY += amount;
            case "down", "d" -> minY -= amount;
            case "north", "n" -> minZ -= amount;
            case "south", "s" -> maxZ += amount;
            case "east", "e" -> maxX += amount;
            case "west", "w" -> minX -= amount;
            case "vertical", "vert", "v" -> {
                World world = Bukkit.getWorld(worldName);
                minY = world == null ? -64 : world.getMinHeight();
                maxY = world == null ? 319 : world.getMaxHeight() - 1;
            }
            default -> throw new IllegalArgumentException("Unknown direction.");
        }
        normalizeBounds();
    }

    public void contract(String direction, int amount) {
        if (amount < 0) {
            amount = -amount;
        }

        switch (direction.toLowerCase(Locale.ROOT)) {
            case "up", "u" -> maxY -= amount;
            case "down", "d" -> minY += amount;
            case "north", "n" -> minZ += amount;
            case "south", "s" -> maxZ -= amount;
            case "east", "e" -> maxX -= amount;
            case "west", "w" -> minX += amount;
            default -> throw new IllegalArgumentException("Unknown direction.");
        }
        normalizeBounds();
    }

    public Location getTeleportLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        int centerX = minX + ((maxX - minX) / 2);
        int centerZ = minZ + ((maxZ - minZ) / 2);
        int y = Math.min(maxY + 1, world.getMaxHeight() - 1);
        return new Location(world, centerX + 0.5, y, centerZ + 0.5);
    }

    public boolean isFlagAllowed(String flag, boolean defaultValue) {
        return flags.getOrDefault(normalizeFlag(flag), defaultValue);
    }

    public void setFlag(String flag, boolean value) {
        flags.put(normalizeFlag(flag), value);
    }

    public void removeFlag(String flag) {
        flags.remove(normalizeFlag(flag));
    }

    public boolean hasFlag(String flag) {
        return flags.containsKey(normalizeFlag(flag));
    }

    public void addOwner(UUID uuid) {
        owners.add(uuid);
    }

    public void removeOwner(UUID uuid) {
        owners.remove(uuid);
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public boolean isOwner(UUID uuid) {
        return owners.contains(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid) || owners.contains(uuid);
    }

    public static String cleanName(String input) {
        if (input == null) {
            return "";
        }
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    public static String normalizeFlag(String flag) {
        if (flag == null) {
            return "";
        }
        return flag.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private void normalizeBounds() {
        int oldMinX = minX;
        int oldMinY = minY;
        int oldMinZ = minZ;
        int oldMaxX = maxX;
        int oldMaxY = maxY;
        int oldMaxZ = maxZ;
        minX = Math.min(oldMinX, oldMaxX);
        minY = Math.min(oldMinY, oldMaxY);
        minZ = Math.min(oldMinZ, oldMaxZ);
        maxX = Math.max(oldMinX, oldMaxX);
        maxY = Math.max(oldMinY, oldMaxY);
        maxZ = Math.max(oldMinZ, oldMaxZ);
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getGreeting() {
        return greeting;
    }

    public void setGreeting(String greeting) {
        this.greeting = greeting == null ? "" : greeting;
    }

    public String getFarewell() {
        return farewell;
    }

    public void setFarewell(String farewell) {
        this.farewell = farewell == null ? "" : farewell;
    }

    public Set<UUID> getOwners() {
        return Set.copyOf(owners);
    }

    public Set<UUID> getMembers() {
        return Set.copyOf(members);
    }

    public Map<String, Boolean> getFlags() {
        return Map.copyOf(flags);
    }

    public List<String> describeBounds() {
        List<String> lines = new ArrayList<>();
        lines.add("World: " + worldName);
        lines.add("Min: " + minX + ", " + minY + ", " + minZ);
        lines.add("Max: " + maxX + ", " + maxY + ", " + maxZ);
        return lines;
    }
}

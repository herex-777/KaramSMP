package me.herex.karmsmp.auction;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class AuctionItem {
    private final UUID id;
    private final UUID sellerId;
    private final String sellerName;
    private final ItemStack item;
    private final double price;
    private final long listedAt;
    private final long expiresAt;

    public AuctionItem(UUID sellerId, String sellerName, ItemStack item, double price, long durationMillis) {
        this(UUID.randomUUID(), sellerId, sellerName, item, price, System.currentTimeMillis(), System.currentTimeMillis() + Math.max(1000L, durationMillis));
    }

    private AuctionItem(UUID id, UUID sellerId, String sellerName, ItemStack item, double price, long listedAt, long expiresAt) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName == null || sellerName.isBlank() ? sellerId.toString() : sellerName;
        this.item = item == null ? null : item.clone();
        this.price = Math.max(0.0D, price);
        this.listedAt = listedAt;
        this.expiresAt = expiresAt;
    }

    public UUID id() {
        return id;
    }

    public UUID sellerId() {
        return sellerId;
    }

    public String sellerName() {
        return sellerName;
    }

    public ItemStack item() {
        return item == null ? null : item.clone();
    }

    public double price() {
        return price;
    }

    public long listedAt() {
        return listedAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    public long timeLeftMillis() {
        return Math.max(0L, expiresAt - System.currentTimeMillis());
    }

    public String timeLeftFormatted() {
        long seconds = timeLeftMillis() / 1000L;
        if (seconds <= 0L) {
            return "Expired";
        }
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }

    public void save(ConfigurationSection section) {
        section.set("seller-id", sellerId.toString());
        section.set("seller-name", sellerName);
        section.set("price", price);
        section.set("listed-at", listedAt);
        section.set("expires-at", expiresAt);
        section.set("item", item);
    }

    public static AuctionItem load(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        try {
            UUID auctionId = UUID.fromString(id);
            UUID sellerId = UUID.fromString(section.getString("seller-id", ""));
            String sellerName = section.getString("seller-name", sellerId.toString());
            ItemStack item = section.getItemStack("item");
            if (item == null) {
                return null;
            }
            double price = section.getDouble("price", 0.0D);
            long listedAt = section.getLong("listed-at", System.currentTimeMillis());
            long expiresAt = section.getLong("expires-at", listedAt + 86400000L);
            return new AuctionItem(auctionId, sellerId, sellerName, item, price, listedAt, expiresAt);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}

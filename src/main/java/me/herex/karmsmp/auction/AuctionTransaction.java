package me.herex.karmsmp.auction;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class AuctionTransaction {
    public enum Type {
        BUY,
        SELL,
        CANCEL,
        EXPIRE
    }

    private final UUID id;
    private final UUID playerId;
    private final String playerName;
    private final Type type;
    private final ItemStack item;
    private final double amount;
    private final long timestamp;
    private final UUID auctionId;

    public AuctionTransaction(UUID playerId, String playerName, Type type, ItemStack item, double amount, UUID auctionId) {
        this(UUID.randomUUID(), playerId, playerName, type, item, amount, System.currentTimeMillis(), auctionId);
    }

    private AuctionTransaction(UUID id, UUID playerId, String playerName, Type type, ItemStack item, double amount, long timestamp, UUID auctionId) {
        this.id = id;
        this.playerId = playerId;
        this.playerName = playerName == null || playerName.isBlank() ? playerId.toString() : playerName;
        this.type = type == null ? Type.BUY : type;
        this.item = item == null ? null : item.clone();
        this.amount = amount;
        this.timestamp = timestamp;
        this.auctionId = auctionId;
    }

    public UUID id() {
        return id;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public Type type() {
        return type;
    }

    public ItemStack item() {
        return item == null ? null : item.clone();
    }

    public double amount() {
        return amount;
    }

    public long timestamp() {
        return timestamp;
    }

    public UUID auctionId() {
        return auctionId;
    }

    public String formattedAge() {
        long seconds = Math.max(0L, (System.currentTimeMillis() - timestamp) / 1000L);
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (days > 0L) {
            return days + "d ago";
        }
        if (hours > 0L) {
            return hours + "h ago";
        }
        if (minutes > 0L) {
            return minutes + "m ago";
        }
        return seconds + "s ago";
    }

    public void save(ConfigurationSection section) {
        section.set("player-id", playerId.toString());
        section.set("player-name", playerName);
        section.set("type", type.name());
        section.set("item", item);
        section.set("amount", amount);
        section.set("timestamp", timestamp);
        section.set("auction-id", auctionId == null ? "" : auctionId.toString());
    }

    public static AuctionTransaction load(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        try {
            UUID transactionId = UUID.fromString(id);
            UUID playerId = UUID.fromString(section.getString("player-id", ""));
            String playerName = section.getString("player-name", playerId.toString());
            Type type = Type.valueOf(section.getString("type", "BUY").toUpperCase());
            ItemStack item = section.getItemStack("item");
            if (item == null) {
                return null;
            }
            double amount = section.getDouble("amount", 0.0D);
            long timestamp = section.getLong("timestamp", System.currentTimeMillis());
            String auctionRaw = section.getString("auction-id", "");
            UUID auctionId = auctionRaw == null || auctionRaw.isBlank() ? null : UUID.fromString(auctionRaw);
            return new AuctionTransaction(transactionId, playerId, playerName, type, item, amount, timestamp, auctionId);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}

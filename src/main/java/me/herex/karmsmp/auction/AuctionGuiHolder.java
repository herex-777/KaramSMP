package me.herex.karmsmp.auction;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AuctionGuiHolder implements InventoryHolder {
    private final AuctionGuiType type;
    private final int page;
    private final Map<Integer, UUID> auctionSlots = new HashMap<>();
    private UUID auctionId;
    private ItemStack item;
    private double price;
    private Inventory inventory;

    public AuctionGuiHolder(AuctionGuiType type, int page) {
        this.type = type;
        this.page = Math.max(1, page);
    }

    public AuctionGuiType type() {
        return type;
    }

    public int page() {
        return page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setAuctionSlot(int slot, UUID id) {
        if (id != null) {
            auctionSlots.put(slot, id);
        }
    }

    public UUID getAuctionAt(int slot) {
        return auctionSlots.get(slot);
    }

    public UUID auctionId() {
        return auctionId;
    }

    public void auctionId(UUID auctionId) {
        this.auctionId = auctionId;
    }

    public ItemStack item() {
        return item == null ? null : item.clone();
    }

    public void item(ItemStack item) {
        this.item = item == null ? null : item.clone();
    }

    public double price() {
        return price;
    }

    public void price(double price) {
        this.price = price;
    }
}

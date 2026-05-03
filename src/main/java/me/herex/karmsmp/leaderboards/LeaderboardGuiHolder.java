package me.herex.karmsmp.leaderboards;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record LeaderboardGuiHolder(String type, int page) implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return null;
    }
}

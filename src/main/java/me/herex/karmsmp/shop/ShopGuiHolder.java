package me.herex.karmsmp.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class ShopGuiHolder implements InventoryHolder {

    private final ShopMenuType type;
    private final String category;

    public ShopGuiHolder(ShopMenuType type, String category) {
        this.type = type;
        this.category = category;
    }

    public ShopMenuType getType() {
        return type;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("Shop GUI holder does not store an inventory instance.");
    }
}

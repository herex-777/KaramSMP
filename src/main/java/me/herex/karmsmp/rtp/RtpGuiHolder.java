package me.herex.karmsmp.rtp;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class RtpGuiHolder implements InventoryHolder {

    private Inventory inventory;
    private final Map<Integer, String> slots = new HashMap<>();

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void setSlot(int slot, String key) {
        slots.put(slot, key);
    }

    public String getKey(int slot) {
        return slots.get(slot);
    }
}

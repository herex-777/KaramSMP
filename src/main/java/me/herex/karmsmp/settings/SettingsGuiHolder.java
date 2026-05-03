package me.herex.karmsmp.settings;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class SettingsGuiHolder implements InventoryHolder {

    private final UUID playerId;

    public SettingsGuiHolder(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}

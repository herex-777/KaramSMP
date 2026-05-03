package me.herex.karmsmp.settings;

import java.util.List;

public final class SettingOption {

    private final String id;
    private final int slot;
    private final String material;
    private final String name;
    private final List<String> lore;
    private final boolean defaultEnabled;

    public SettingOption(String id, int slot, String material, String name, List<String> lore, boolean defaultEnabled) {
        this.id = id;
        this.slot = slot;
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.defaultEnabled = defaultEnabled;
    }

    public String getId() {
        return id;
    }

    public int getSlot() {
        return slot;
    }

    public String getMaterial() {
        return material;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }
}

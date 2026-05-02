package me.herex.karmsmp.managers;

import java.util.Collections;
import java.util.List;

public final class Rank {

    private final String name;
    private final String permission;
    private final String prefix;
    private final String suffix;
    private final int priority;
    private final List<String> permissions;

    public Rank(String name, String permission, String prefix, String suffix, int priority, List<String> permissions) {
        this.name = name;
        this.permission = permission;
        this.prefix = prefix;
        this.suffix = suffix;
        this.priority = priority;
        this.permissions = permissions == null ? Collections.emptyList() : List.copyOf(permissions);
    }

    public String getName() {
        return name;
    }

    public String getPermission() {
        return permission;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public int getPriority() {
        return priority;
    }

    public List<String> getPermissions() {
        return permissions;
    }
}

package me.herex.karmsmp.listeners;

import me.herex.karmsmp.spawn.SpawnManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class SpawnListener implements Listener {

    private final SpawnManager spawnManager;

    public SpawnListener(SpawnManager spawnManager) {
        this.spawnManager = spawnManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        spawnManager.handleJoin(event.getPlayer(), event.getPlayer().hasPlayedBefore());
    }
}

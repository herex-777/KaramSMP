package me.herex.karmsmp.commands;

import me.herex.karmsmp.KaramSMP;
import me.herex.karmsmp.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public final class NightVisionCommand implements CommandExecutor {

    private final KaramSMP plugin;

    public NightVisionCommand(KaramSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(plugin.getConfig().getString("night-vision.only-players-message", "&cOnly players can use this command.")));
            return true;
        }

        if (!plugin.getConfig().getBoolean("night-vision.enabled", true)) {
            player.sendMessage(MessageUtil.color(plugin.getConfig().getString("night-vision.command-disabled-message", "&cThe night vision command is disabled.")));
            return true;
        }

        if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            player.sendMessage(MessageUtil.color(plugin.getConfig().getString("night-vision.disabled-message", "&cNight vision disabled.")));
            return true;
        }

        int amplifier = Math.max(0, plugin.getConfig().getInt("night-vision.amplifier", 0));
        boolean ambient = plugin.getConfig().getBoolean("night-vision.ambient", false);
        boolean particles = plugin.getConfig().getBoolean("night-vision.show-particles", false);
        boolean icon = plugin.getConfig().getBoolean("night-vision.show-icon", true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, amplifier, ambient, particles, icon));
        player.sendMessage(MessageUtil.color(plugin.getConfig().getString("night-vision.enabled-message", "&aNight vision enabled.")));
        return true;
    }
}

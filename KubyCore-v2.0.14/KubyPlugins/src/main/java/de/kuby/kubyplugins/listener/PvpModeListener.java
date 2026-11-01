package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.List;

/** PvP nur in den Modi aus pvp.modi (Standard SMP und Skyblock); in Survival und in der Lobby kein Schaden zwischen Spielern. */
public final class PvpModeListener implements Listener {

    private final KubyPlugins plugin;

    public PvpModeListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player a = attacker(e.getDamager());
        if (a == null || a.equals(victim)) return;
        List<String> modes = plugin.getConfig().getStringList("pvp.modi");
        if (modes.isEmpty()) modes = List.of("smp", "skyblock");
        if (!modes.contains(plugin.modes().modeOf(victim.getUniqueId()))) e.setCancelled(true);
    }
}

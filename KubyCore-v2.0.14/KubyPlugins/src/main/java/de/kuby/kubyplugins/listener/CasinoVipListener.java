package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * VIP-Lounge und Casino-Musik: Zutritt zur Lounge nur für VIPs (auch per Teleport), Barkeeper anklicken gibt die
 * Tages-Chips, und beim Betreten oder Verlassen des Casinos startet oder stoppt die Musik.
 */
public final class CasinoVipListener implements Listener {

    private final KubyPlugins plugin;

    public CasinoVipListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedBlock()) return;
        if (blocked(e.getPlayer(), e.getFrom(), e.getTo())) {
            e.setCancelled(true);
            return;
        }
        plugin.casinoMusic().update(e.getPlayer(), e.getTo());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (blocked(e.getPlayer(), e.getFrom(), e.getTo())) {
            e.setCancelled(true);
            return;
        }
        plugin.casinoMusic().update(e.getPlayer(), e.getTo());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) plugin.casinoMusic().update(p, p.getLocation());
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.casinoMusic().stop(e.getPlayer());
        plugin.casinoMusic().forget(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBarkeeper(PlayerInteractEntityEvent e) {
        if (!plugin.casinoVip().isBarkeeper(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        plugin.casinoVip().serve(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBarkeeperDamage(EntityDamageEvent e) {
        if (plugin.casinoVip().isBarkeeper(e.getEntity())) e.setCancelled(true);
    }

    /** True, wenn der Spieler die VIP-Lounge betreten will, aber nicht darf. */
    private boolean blocked(Player p, Location from, Location to) {
        if (to == null || !plugin.casinoVip().inLounge(to)) return false;
        if (from != null && plugin.casinoVip().inLounge(from)) return false;
        if (plugin.casinoVip().allowed(p)) return false;
        p.sendActionBar(Msg.parse("<red>" + plugin.casinoVip().denyMessage()));
        return true;
    }
}

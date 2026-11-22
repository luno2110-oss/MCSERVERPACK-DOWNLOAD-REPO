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

    /** Casino-Befehle, die Bedrock-Spieler trotz Sperre nutzen dürfen (nur lesen, nichts spielen). */
    private static final java.util.Set<String> BEDROCK_OK = java.util.Set.of("hilfe", "regeln", "stats", "status");
    private static final java.util.Set<String> CASINO_ROOTS = java.util.Set.of("casino", "poker", "roulette", "cashout", "plinko");

    /** Bedrock-Sperre auch für Befehle (von außerhalb setzen, Chips tauschen, Crash, Poker …), Admins ausgenommen. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCasinoCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!plugin.casino().bedrockBlocked(p) || p.hasPermission("kuby.admin")) return;
        String[] parts = e.getMessage().substring(1).trim().split("\\s+");
        if (parts.length == 0) return;
        String root = parts[0].toLowerCase(java.util.Locale.ROOT);
        int colon = root.indexOf(':');
        if (colon >= 0) root = root.substring(colon + 1);
        if (!CASINO_ROOTS.contains(root)) return;
        if (root.equals("casino") && parts.length > 1 && BEDROCK_OK.contains(parts[1].toLowerCase(java.util.Locale.ROOT))) return;
        e.setCancelled(true);
        plugin.casino().bedrockNotice(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            // Bedrock-Spieler, die im Casino ausgeloggt hatten: hinaus zum Survival-Spawn (Casino vorerst nur Java)
            if (plugin.casino().bedrockBlocked(p) && plugin.casinoMusic().inCasino(p.getLocation())) {
                Location out = plugin.modes().spawnOf("survival");
                if (out != null) p.teleport(out);
                plugin.casino().bedrockNotice(p);
                return;
            }
            plugin.casinoMusic().update(p, p.getLocation());
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.casinoMusic().stop(e.getPlayer());
        plugin.casinoMusic().forget(e.getPlayer());
        plugin.casinoHolos().forget(e.getPlayer());
        plugin.casino().forgetBedrockNotice(e.getPlayer().getUniqueId());
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
        if (to != null && plugin.casino().bedrockBlocked(p) && plugin.casinoMusic().inCasino(to)
                && (from == null || !plugin.casinoMusic().inCasino(from))) {
            plugin.casino().bedrockNotice(p);
            return true;
        }
        if (to == null || !plugin.casinoVip().inLounge(to)) return false;
        if (from != null && plugin.casinoVip().inLounge(from)) return false;
        if (plugin.casinoVip().allowed(p)) return false;
        p.sendActionBar(Msg.parse("<red>" + plugin.casinoVip().denyMessage()));
        return true;
    }
}

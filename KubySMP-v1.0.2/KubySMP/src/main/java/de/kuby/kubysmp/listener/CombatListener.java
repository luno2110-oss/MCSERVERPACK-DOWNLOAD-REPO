package de.kuby.kubysmp.listener;

import de.kuby.kubysmp.KubySMP;

import de.kuby.kubysmp.service.TrophyService;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.StatsService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/** PvP-Regeln, Kills, Tode, Kopfgeld-Auszahlung und Logout-im-Kampf. */
public final class CombatListener implements Listener {

    private final KubyPlugins plugin;

    public CombatListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        // Duelle haben eigene Regeln: PvP-Schutz gilt hier nicht
        if (KubySMP.get().duels().inSameDuel(attacker.getUniqueId(), victim.getUniqueId())) return;
        if (plugin.rtpQueue().inPair(attacker.getUniqueId(), victim.getUniqueId())) return;

        if (KubySMP.get().combat().protectedFromPvp(attacker, victim)) {
            e.setCancelled(true);
            Msg.send(attacker, "<gray>PvP ist hier geschützt.");
            return;
        }
        KubySMP.get().combat().tag(attacker.getUniqueId());
        KubySMP.get().combat().tag(victim.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (KubySMP.get().duels().handleDeath(e)) return;
        plugin.rtpQueue().onDeath(e.getEntity().getUniqueId());
        Player victim = e.getEntity();
        KubySMP.get().combat().clear(victim.getUniqueId());
        StatsService.Stats vs = plugin.stats().get(victim.getUniqueId());
        vs.deaths++;

        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        StatsService.Stats ks = plugin.stats().get(killer.getUniqueId());
        ks.kills++;
        ks.xp += Math.round(plugin.getConfig().getInt("xp.per-kill", 20) * plugin.booster().multiplier());
        TrophyService.Result res = KubySMP.get().trophies().transfer(killer.getUniqueId(), victim.getUniqueId());
        Msg.send(victim, "<red>-" + res.lost() + " Trophäen <gray>durch <white>" + killer.getName());
        plugin.battlepass().progress(killer.getUniqueId(), "kill", 1);
        plugin.cosmetics().onKill(killer, victim.getLocation());

        long bounty = KubySMP.get().bounties().claim(victim.getUniqueId());
        if (bounty > 0) {
            plugin.economy().deposit(killer.getUniqueId(), bounty);
            Sfx.play(plugin, killer, Sfx.Kind.BOUNTY);
            plugin.notifications().send("notify.duel", "<gold>Kopfgeld! <white>" + killer.getName()
                    + " <gray>hat <white>" + victim.getName() + " <gray>erledigt und <gold>"
                    + Msg.coins(bounty) + "$ <gray>kassiert.");
        }
        Sfx.play(plugin, killer, Sfx.Kind.KILL);
        Msg.send(killer, "<green>+" + res.gained()
                + " Trophäen <gray>für den Kill auf <white>" + victim.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        KubySMP.get().duels().handleQuit(p);
        plugin.rtpQueue().onQuit(p.getUniqueId());
        KubySMP.get().tournaments().onQuit(p.getUniqueId());
        if (KubySMP.get().combat().inCombat(p.getUniqueId())) {
            // Logout im Kampf zählt als Tod: Inventar fällt zu Boden
            plugin.stats().get(p.getUniqueId()).deaths++;
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), item);
                }
            }
            p.getInventory().clear();
            plugin.notifications().send("notify.broadcast", "<red>" + p.getName() + " <gray>hat sich im Kampf ausgeloggt.");
        }
        KubySMP.get().combat().clear(p.getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        KubySMP.get().duels().onRespawn(e);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        StatsService.Stats s = plugin.stats().get(e.getPlayer().getUniqueId());
        if (s.firstJoin == 0) s.firstJoin = System.currentTimeMillis();
    }

    private static Player resolveAttacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) return shooter;
        return null;
    }
}

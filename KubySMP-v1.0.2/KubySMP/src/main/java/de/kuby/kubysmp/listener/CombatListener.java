package de.kuby.kubysmp.listener;

import de.kuby.kubysmp.KubySMP;

import de.kuby.kubysmp.service.TrophyService;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.service.StatsService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** PvP-Regeln, Kills, Tode, Kopfgeld-Auszahlung und Logout-im-Kampf. */
public final class CombatListener implements Listener {

    private final KubyPlugins plugin;

    public CombatListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(e);
        if (attacker == null || attacker.equals(victim)) return;
        // Duelle haben eigene Regeln: PvP-Schutz gilt hier nicht
        if (KubySMP.get().duels().inSameDuel(attacker.getUniqueId(), victim.getUniqueId())) return;
        if (plugin.rtpQueue().inPair(attacker.getUniqueId(), victim.getUniqueId())) return;

        if (KubySMP.get().combat().protectedFromPvp(attacker, victim)) {
            e.setCancelled(true);
            Msg.send(attacker, "<gray>PvP ist hier geschützt.");
            return;
        }
        KubySMP.get().combat().tag(victim.getUniqueId(), attacker.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (KubySMP.get().duels().handleDeath(e)) return;
        // Kills, Tode, Trophäen und Kopfgelder zählen nur im SMP
        if (!ModeService.SMP.equals(plugin.modes().modeOf(e.getEntity().getUniqueId()))) return;
        plugin.rtpQueue().onDeath(e.getEntity().getUniqueId());
        Player victim = e.getEntity();
        KubySMP.get().combat().clear(victim.getUniqueId());
        StatsService.Stats vs = plugin.stats().get(victim.getUniqueId());
        vs.deaths++;

        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;
        credit(killer, victim.getUniqueId(), victim.getName(), victim.getLocation());
    }

    /**
     * Kill gutschreiben: Trophäen, XP, Battle-Pass, Quests nur, wenn derselbe Killer dasselbe Opfer nicht gerade erst
     * erledigt hat (pvp.kill-sperre-minuten); das Kopfgeld gibt es immer (es ist nur einmal da).
     */
    private void credit(Player killer, UUID victimId, String victimName, org.bukkit.Location at) {
        Player victim = Bukkit.getPlayer(victimId);
        if (KubySMP.get().combat().countsKill(killer.getUniqueId(), victimId)) {
            StatsService.Stats ks = plugin.stats().get(killer.getUniqueId());
            ks.kills++;
            ks.xp += Math.round(plugin.getConfig().getInt("xp.per-kill", 20) * plugin.booster().multiplier());
            TrophyService.Result res = KubySMP.get().trophies().transfer(killer.getUniqueId(), victimId);
            if (victim != null) Msg.send(victim, "<red>-" + res.lost() + " Trophäen <gray>durch <white>" + killer.getName());
            plugin.battlepass().progress(killer.getUniqueId(), "kill", 1);
            if (victim != null) plugin.questProgress(killer, "pvp", victim, 1);
            if (at != null) plugin.cosmetics().onKill(killer, at);
            Msg.send(killer, "<green>+" + res.gained() + " Trophäen <gray>für den Kill auf <white>" + victimName);
        } else {
            Msg.send(killer, "<gray>Keine Wertung: Du hast <white>" + victimName + " <gray>gerade erst erledigt.");
        }

        // Kopfgeld nicht über ein Zweitkonto (gleiche IP) einlösen: es bleibt stehen
        if (plugin.alts() != null && plugin.alts().blocks(killer.getUniqueId(), victimId)) {
            Sfx.play(plugin, killer, Sfx.Kind.KILL);
            return;
        }
        long bounty = KubySMP.get().bounties().claim(victimId);
        if (bounty > 0) {
            plugin.modes().depositIn(killer.getUniqueId(), ModeService.SMP, bounty);
            plugin.questProgress(killer, "kopfgeld", null, 1);
            Sfx.play(plugin, killer, Sfx.Kind.BOUNTY);
            plugin.notifications().send("notify.duel", "<gold>Kopfgeld! <white>" + killer.getName()
                    + " <gray>hat <white>" + victimName + " <gray>erledigt und <gold>"
                    + Msg.coins(bounty) + "$ <gray>kassiert.");
        }
        Sfx.play(plugin, killer, Sfx.Kind.KILL);
    }

    /** LOW: vor dem Speichern des Inventars durch den Kern (der speichert beim Ausloggen erst ganz zum Schluss). */
    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        KubySMP.get().duels().handleQuit(p);
        plugin.rtpQueue().onQuit(p.getUniqueId());
        KubySMP.get().tournaments().onQuit(p.getUniqueId());
        if (KubySMP.get().combat().inCombat(p.getUniqueId())) {
            UUID attackerId = KubySMP.get().combat().lastAttacker(p.getUniqueId());
            // Logout im Kampf zählt als Tod: Inventar fällt zu Boden
            plugin.stats().get(p.getUniqueId()).deaths++;
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), item);
                }
            }
            p.getInventory().clear();
            plugin.notifications().send("notify.broadcast", "<red>" + p.getName() + " <gray>hat sich im Kampf ausgeloggt.");
            // ... und als Kill für den letzten Angreifer (Trophäen, Kopfgeld), damit Ausloggen sich nicht lohnt
            Player attacker = attackerId == null ? null : Bukkit.getPlayer(attackerId);
            if (attacker != null && ModeService.SMP.equals(plugin.modes().modeOf(p.getUniqueId()))
                    && ModeService.SMP.equals(plugin.modes().modeOf(attacker.getUniqueId()))) {
                credit(attacker, p.getUniqueId(), p.getName(), p.getLocation());
            }
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
        KubySMP.get().duels().onJoin(e.getPlayer());
    }

    /**
     * Wer hat wirklich angegriffen? Spieler, Geschosse, gezähmte Tiere (Besitzer), gezündetes TNT und alles, was der
     * Server einem Spieler zuschreibt (z. B. ein zerschlagener End-Kristall). Früher galten Kristalle, TNT und Hunde
     * nicht als Angriff: Spawn-, Neulings-, Freundes- und Team-Schutz ließen sich so umgehen, und es gab keinen Kampf-Tag.
     */
    private static Player resolveAttacker(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Player p) return p;
        if (d instanceof Projectile proj && proj.getShooter() instanceof Player shooter) return shooter;
        if (d instanceof Tameable t && t.isTamed() && t.getOwner() instanceof Player owner) return owner;
        if (d instanceof TNTPrimed tnt && tnt.getSource() instanceof Player src) return src;
        Entity cause = e.getDamageSource().getCausingEntity();
        return cause instanceof Player p ? p : null;
    }
}

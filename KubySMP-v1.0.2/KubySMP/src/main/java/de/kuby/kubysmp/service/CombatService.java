package de.kuby.kubysmp.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Combat-Tag, PvP-Schutz, Anti-Logout und Kill-Sperre gegen Kill-Farming. */
public final class CombatService {

    private static final long TAG_MILLIS = 15_000L;

    private final KubyPlugins plugin;
    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<>();
    /** Wer zuletzt getroffen hat (für Logout im Kampf: zählt als Kill für ihn). */
    private final Map<UUID, UUID> lastAttacker = new ConcurrentHashMap<>();
    /** Letzter gewerteter Kill je Paar „Killer;Opfer“ (gegen Farmen mit Zweitkonten). */
    private final Map<String, Long> lastKill = new ConcurrentHashMap<>();

    public CombatService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public void tag(UUID id) {
        taggedUntil.put(id, System.currentTimeMillis() + TAG_MILLIS);
    }

    /** Beide markieren und den Angreifer beim Opfer merken. */
    public void tag(UUID victim, UUID attacker) {
        tag(victim);
        tag(attacker);
        lastAttacker.put(victim, attacker);
    }

    public boolean inCombat(UUID id) {
        Long until = taggedUntil.get(id);
        return until != null && until > System.currentTimeMillis();
    }

    /** Letzter Angreifer, solange der Kampf läuft (sonst null). */
    public UUID lastAttacker(UUID victim) {
        return inCombat(victim) ? lastAttacker.get(victim) : null;
    }

    public void clear(UUID id) {
        taggedUntil.remove(id);
        lastAttacker.remove(id);
    }

    /**
     * Zählt dieser Kill (Trophäen, XP, Battle-Pass, Quests)? Derselbe Killer bekommt für dasselbe Opfer höchstens alle
     * pvp.kill-sperre-minuten (Standard 10) eine Wertung. Ruft man es auf, wird der Kill gemerkt.
     */
    public boolean countsKill(UUID killer, UUID victim) {
        // Zweitkonten (gleiche IP) bringen keine Wertung
        if (plugin.alts() != null && plugin.alts().blocks(killer, victim)) return false;
        long minutes = plugin.getConfig().getLong("pvp.kill-sperre-minuten", 10);
        if (minutes <= 0) return true;
        String k = killer + ";" + victim;
        long now = System.currentTimeMillis();
        Long last = lastKill.get(k);
        if (last != null && now - last < minutes * 60_000L) return false;
        lastKill.put(k, now);
        if (lastKill.size() > 5000) lastKill.values().removeIf(t -> now - t > minutes * 60_000L);
        return true;
    }

    /** True, wenn zwischen den beiden kein PvP stattfinden darf. */
    public boolean protectedFromPvp(Player attacker, Player victim) {
        UUID a = attacker.getUniqueId();
        UUID v = victim.getUniqueId();
        if (plugin.friends().areFriends(a, v)) return true;
        var teamA = plugin.teams().of(a);
        var teamV = plugin.teams().of(v);
        if (teamA != null && teamV != null && teamA.name().equals(teamV.name())) return true;
        if (teamA != null && teamV != null && plugin.teams().areAllied(teamA, teamV)) return true;
        if (isNewbie(attacker) || isNewbie(victim)) return true;
        return isAtSpawn(victim) || isAtSpawn(attacker);
    }

    private boolean isNewbie(Player p) {
        long minutes = plugin.getConfig().getLong("pvp.newbie-minutes", 30);
        long joined = plugin.stats().get(p.getUniqueId()).firstJoin;
        return joined > 0 && System.currentTimeMillis() - joined < minutes * 60_000L;
    }

    /** Spawn-Schutz um den SMP-Spawn (früher um den Welt-Spawn, der nicht der SMP-Spawn sein muss). */
    private boolean isAtSpawn(Player p) {
        int radius = plugin.getConfig().getInt("pvp.spawn-radius", 30);
        Location spawn = plugin.modes().spawnOf(ModeService.SMP);
        if (spawn == null || spawn.getWorld() == null || !spawn.getWorld().equals(p.getWorld())) spawn = p.getWorld().getSpawnLocation();
        return spawn.getWorld().equals(p.getWorld()) && spawn.distanceSquared(p.getLocation()) <= (double) radius * radius;
    }
}

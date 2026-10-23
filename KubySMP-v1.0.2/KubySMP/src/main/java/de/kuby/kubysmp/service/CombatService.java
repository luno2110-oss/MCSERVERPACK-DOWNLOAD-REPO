package de.kuby.kubysmp.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Combat-Tag, PvP-Schutz und Anti-Logout. */
public final class CombatService {

    private static final long TAG_MILLIS = 15_000L;

    private final KubyPlugins plugin;
    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<>();

    public CombatService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public void tag(UUID id) {
        taggedUntil.put(id, System.currentTimeMillis() + TAG_MILLIS);
    }

    public boolean inCombat(UUID id) {
        Long until = taggedUntil.get(id);
        return until != null && until > System.currentTimeMillis();
    }

    public void clear(UUID id) {
        taggedUntil.remove(id);
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

    private boolean isAtSpawn(Player p) {
        int radius = plugin.getConfig().getInt("pvp.spawn-radius", 30);
        var spawn = p.getWorld().getSpawnLocation();
        return spawn.getWorld().equals(p.getWorld()) && spawn.distanceSquared(p.getLocation()) <= (double) radius * radius;
    }
}

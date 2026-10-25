package de.kuby.kubyplugins.util;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Deko-Partikel (Hüte, Teleports, Casino, Kisten, Hochzeiten): gehen nur an Spieler in der Nähe, deren Schalter
 * "Partikel &amp; Effekte" an ist. Partikel, die etwas anzeigen (Grundstücksgrenzen, Gräber), laufen nicht hierüber.
 */
public final class Fx {

    private static final double RANGE = 64;

    private Fx() {
    }

    public static void particle(KubyPlugins plugin, Location at, Particle particle, int count, double dx, double dy,
                                double dz, double speed) {
        particle(plugin, at, particle, count, dx, dy, dz, speed, null);
    }

    public static <T> void particle(KubyPlugins plugin, Location at, Particle particle, int count, double dx, double dy,
                                    double dz, double speed, T data) {
        World w = at.getWorld();
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(at) > RANGE * RANGE) continue;
            if (plugin.playerSettings() != null && !plugin.playerSettings().get(p.getUniqueId(), "particles")) continue;
            p.spawnParticle(particle, at, count, dx, dy, dz, speed, data);
        }
    }

    public static void particle(KubyPlugins plugin, Location at, Particle particle, int count) {
        particle(plugin, at, particle, count, 0, 0, 0, 0);
    }
}

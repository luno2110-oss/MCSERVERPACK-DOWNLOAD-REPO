package de.kuby.kubyplugins.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/** Speichert Positionen als Text: welt;x;y;z;yaw;pitch */
public final class Locs {

    private Locs() {}

    public static String serialize(Location l) {
        return String.format(Locale.ROOT, "%s;%.3f;%.3f;%.3f;%.2f;%.2f",
                l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public static Location parse(String raw) {
        if (raw == null) return null;
        String[] p = raw.split(";");
        if (p.length != 6) return null;
        World world = Bukkit.getWorld(p[0]);
        if (world == null) return null;
        return new Location(world,
                Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                Float.parseFloat(p[4]), Float.parseFloat(p[5]));
    }
}

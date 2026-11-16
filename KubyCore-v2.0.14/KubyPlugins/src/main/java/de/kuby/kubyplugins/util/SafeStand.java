package de.kuby.kubyplugins.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Sicher aufstehen (v2.02). Sitze sind unsichtbare Rüstungsständer. Beim Absteigen stellt Minecraft den Spieler auf die
 * Höhe des Ständers, und die liegt bei /sit 0,1 unter der Bodenoberkante: man steckt im Block darunter. Hier wird
 * stattdessen eine freie Stelle mit festem Boden gesucht: zuerst die bevorzugte Seite (vor dem Möbel, hinter dem
 * Hocker), dann genau am Sitz, dann ringsum. Die Füße kommen auf die Oberkante des Bodens (auch Teppich, Stufe,
 * Platte), Kopf und Körper müssen frei sein.
 */
public final class SafeStand {

    private static final double HALF = 0.3, HEIGHT = 1.8;

    private SafeStand() {
    }

    /**
     * Einen Tick nach dem Absteigen umsetzen (vorher ist der Spieler noch Reiter).
     *
     * @param floor  Bodenhöhe unter dem Sitz (Füße beim Stehen), x/z = Sitzmitte
     * @param prefer bevorzugte Verschiebung (z. B. 0,7 Blöcke vor das Möbel) oder null
     */
    public static void later(Plugin plugin, Entity who, Location floor, Vector prefer) {
        if (!(who instanceof Player p)) return;
        Location f = floor.clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && !p.isInsideVehicle() && p.getWorld().equals(f.getWorld())) now(p, f, prefer);
        });
    }

    /** Sofort umsetzen (Spieler sitzt nicht mehr). Gibt true zurück, wenn eine freie Stelle gefunden wurde. */
    public static boolean now(Player p, Location floor, Vector prefer) {
        Location to = find(floor, prefer);
        if (to == null) return false;
        to.setYaw(p.getLocation().getYaw());
        to.setPitch(p.getLocation().getPitch());
        p.setFallDistance(0);
        return p.teleport(to);
    }

    /** Freie Stelle zum Stehen nahe dem Sitz oder null. */
    public static Location find(Location floor, Vector prefer) {
        World w = floor.getWorld();
        if (w == null) return null;
        List<Vector> tries = new ArrayList<>();
        if (prefer != null) {
            tries.add(prefer);
            tries.add(prefer.clone().multiply(1.5));
        }
        tries.add(new Vector());
        for (double r : new double[]{0.75, 1.3}) {
            for (int k = 0; k < 8; k++) {
                double a = Math.PI / 4 * k;
                tries.add(new Vector(Math.cos(a) * r, 0, Math.sin(a) * r));
            }
        }
        for (Vector o : tries) {
            Location l = standAt(w, floor.getX() + o.getX(), floor.getY(), floor.getZ() + o.getZ());
            if (l != null) return l;
        }
        // nichts frei: senkrecht über dem Sitz nach oben (z. B. ganz eingebaut)
        for (int up = 1; up <= 3; up++) {
            Location l = standAt(w, floor.getX(), floor.getY() + up, floor.getZ());
            if (l != null) return l;
        }
        return null;
    }

    /** Füße auf den Boden der Spalte (x, z), höchstens 0,6 über und 1,5 unter der erwarteten Bodenhöhe y. */
    static Location standAt(World w, double x, double y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int top = (int) Math.floor(y + 0.6), bottom = (int) Math.floor(y - 1.5);
        for (int by = top; by >= bottom; by--) {
            Block b = w.getBlockAt(bx, by, bz);
            double h = -1;
            for (BoundingBox bb : b.getCollisionShape().getBoundingBoxes()) h = Math.max(h, bb.getMaxY());
            if (h < 0) {
                if (b.getType() == Material.LAVA) return null;
                continue;
            }
            double feet = by + h;
            if (feet > y + 0.6 || feet < y - 1.5) return null;
            Material t = b.getType();
            if (t == Material.MAGMA_BLOCK || t == Material.CAMPFIRE || t == Material.SOUL_CAMPFIRE) return null;
            BoundingBox body = new BoundingBox(x - HALF, feet + 0.001, z - HALF, x + HALF, feet + HEIGHT, z + HALF);
            if (w.hasCollisionsIn(body)) return null;
            Material in = w.getBlockAt(bx, (int) Math.floor(feet + 0.01), bz).getType();
            if (in == Material.LAVA || in == Material.FIRE || in == Material.SOUL_FIRE) return null;
            return new Location(w, x, feet, z);
        }
        return null;
    }

    /** Blickrichtung (Yaw in Grad) als waagrechter Einheitsvektor. */
    public static Vector facing(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vector(-Math.sin(r), 0, Math.cos(r));
    }
}

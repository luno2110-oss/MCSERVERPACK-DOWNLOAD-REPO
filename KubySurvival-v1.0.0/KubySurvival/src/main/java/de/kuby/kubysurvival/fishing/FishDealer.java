package de.kuby.kubysurvival.fishing;

import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

/** Fischhändler: Dorfbewohner ohne KI. Rechtsklick verkauft alle Fische im Inventar. */
public final class FishDealer {

    private final NamespacedKey tag;

    public FishDealer(KubySurvival addon) {
        this.tag = new NamespacedKey(addon, "fish_dealer");
    }

    /** Stellt einen Fischhändler an die Position des Admins. */
    public void spawn(Player admin) {
        admin.getWorld().spawn(admin.getLocation(), Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setCollidable(false);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.customName(Msg.parse("<aqua><b>Fischhändler</b>"));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** Entfernt den nächsten Fischhändler im Umkreis von 5 Blöcken. False, wenn keiner da ist. */
    public boolean removeNear(Player admin) {
        Villager nearest = null;
        double best = Double.MAX_VALUE;
        for (Villager v : admin.getWorld().getNearbyEntitiesByType(Villager.class, admin.getLocation(), 5)) {
            if (!is(v)) continue;
            double d = v.getLocation().distanceSquared(admin.getLocation());
            if (d < best) {
                best = d;
                nearest = v;
            }
        }
        if (nearest == null) return false;
        nearest.remove();
        return true;
    }

    public boolean is(Entity e) {
        return e.getPersistentDataContainer().has(tag, PersistentDataType.BYTE);
    }
}

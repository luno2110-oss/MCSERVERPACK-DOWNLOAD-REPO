package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

/** Fischhändler: Dorfbewohner (Fischer) ohne KI. Rechtsklick öffnet das Händler-Menü. */
public final class AngelDealer {

    private final NamespacedKey tag;

    public AngelDealer(KubySurvival addon) {
        // gleicher Schlüssel wie vor dem Umbau, damit bestehende Händler weiter funktionieren
        this.tag = new NamespacedKey(addon, "fish_dealer");
    }

    public void spawn(Player admin) {
        admin.getWorld().spawn(admin.getLocation(), Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setCollidable(false);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setProfession(Villager.Profession.FISHERMAN);
            v.setVillagerLevel(5);
            v.customName(Msg.parse("<#6CE086><b>Fischhändler</b>"));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
    }

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

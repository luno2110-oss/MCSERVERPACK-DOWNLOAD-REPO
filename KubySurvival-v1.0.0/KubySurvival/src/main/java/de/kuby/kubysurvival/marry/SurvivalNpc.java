package de.kuby.kubysurvival.marry;

import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerTextures;

/**
 * Figuren mit eigenem Skin aus dem Pack (kuby:entity/survival/&lt;skin&gt;) und Schild darüber: Priester in der Kirche
 * und Bankier an der Bank am Spawn. Unverwundbar, ohne KI, bleiben über Neustarts erhalten (PDC-Markierung).
 */
public final class SurvivalNpc implements Listener {

    public enum Role {
        PRIESTER("priester", "Priester", "#FF7AC0", "Rechtsklick · Heiraten"),
        BANKIER("bankier", "Bankier", "#F2C84A", "Rechtsklick · Bank und Konten");

        final String id;
        final String title;
        final String color;
        final String line;

        Role(String id, String title, String color, String line) {
            this.id = id;
            this.title = title;
            this.color = color;
            this.line = line;
        }
    }

    private final NamespacedKey roleKey;
    private final NamespacedKey holoKey;

    public SurvivalNpc(KubySurvival addon) {
        this.roleKey = new NamespacedKey(addon, "survival_npc");
        this.holoKey = new NamespacedKey(addon, "survival_npc_holo");
    }

    public Entity spawn(Role role, Location at) {
        Location loc = at.clone();
        loc.setPitch(0);
        Entity npc = loc.getWorld().spawn(loc, Mannequin.class, m -> {
            m.setProfile(ResolvableProfile.resolvableProfile()
                    .name(role.title)
                    .skinPatch(sp -> sp.body(Key.key("kuby", "entity/survival/" + role.id))
                            .model(PlayerTextures.SkinModel.CLASSIC))
                    .build());
            m.setImmovable(true);
            m.setDescription(null);
            m.setAI(false);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setPersistent(true);
            m.setRemoveWhenFarAway(false);
            m.setCustomNameVisible(false);
            m.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, role.id);
        });
        Location top = loc.clone().add(0, 2.15, 0);
        top.setYaw(0);
        String owner = npc.getUniqueId().toString();
        top.getWorld().spawn(top, TextDisplay.class, d -> {
            d.text(Msg.parse("<" + role.color + "><b>" + role.title.toUpperCase() + "</b></" + role.color + ">\n<gray>"
                    + role.line));
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setPersistent(true);
            d.getPersistentDataContainer().set(holoKey, PersistentDataType.STRING, owner);
        });
        return npc;
    }

    public Role roleOf(Entity e) {
        String id = e.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
        if (id == null) return null;
        for (Role r : Role.values()) if (r.id.equals(id)) return r;
        return null;
    }

    /** Nächste Figur dieser Rolle im Umkreis oder null. */
    public Entity nearest(Location l, Role role, double radius) {
        if (l == null || l.getWorld() == null) return null;
        Entity best = null;
        double bestD = radius * radius;
        for (Mannequin m : l.getWorld().getNearbyEntitiesByType(Mannequin.class, l, radius)) {
            if (roleOf(m) != role) continue;
            double d = m.getLocation().distanceSquared(l);
            if (d <= bestD) {
                best = m;
                bestD = d;
            }
        }
        return best;
    }

    /** Entfernt Figuren dieser Rolle im Umkreis samt Schild. Gibt die Anzahl zurück. */
    public int remove(Location l, Role role, double radius) {
        int n = 0;
        for (Mannequin m : l.getWorld().getNearbyEntitiesByType(Mannequin.class, l, radius)) {
            if (roleOf(m) != role) continue;
            String owner = m.getUniqueId().toString();
            for (TextDisplay d : m.getWorld().getNearbyEntitiesByType(TextDisplay.class, m.getLocation(), 4)) {
                if (owner.equals(d.getPersistentDataContainer().get(holoKey, PersistentDataType.STRING))) d.remove();
            }
            m.remove();
            n++;
        }
        return n;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (roleOf(e.getEntity()) != null) e.setCancelled(true);
    }
}

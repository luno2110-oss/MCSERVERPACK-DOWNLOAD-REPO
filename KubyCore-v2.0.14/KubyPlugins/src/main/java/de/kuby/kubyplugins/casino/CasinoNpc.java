package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * Casino-Figuren mit Hologramm darüber (Kasse, später Croupier, Dealer, DJ ...). Admins stellen sie mit
 * /casino npc &lt;rolle&gt; an ihre Position; Rechtsklick öffnet das passende Menü. Figuren und Hologramme sind
 * dauerhaft (überleben Neustarts) und über eine Markierung miteinander verbunden.
 */
public final class CasinoNpc implements Listener {

    /** Rolle einer Figur: Titel, Zeilen darunter, Aktion beim Rechtsklick. */
    public enum Role {
        KASSE("Kasse", "kasse");

        public final String title;
        public final String id;

        Role(String title, String id) {
            this.title = title;
            this.id = id;
        }

        public static Role of(String id) {
            for (Role r : values()) if (r.id.equalsIgnoreCase(id)) return r;
            return null;
        }
    }

    private final KubyPlugins plugin;
    private final NamespacedKey roleKey;
    private final NamespacedKey holoKey;

    public CasinoNpc(KubyPlugins plugin) {
        this.plugin = plugin;
        this.roleKey = new NamespacedKey(plugin, "casino_npc");
        this.holoKey = new NamespacedKey(plugin, "casino_npc_holo");
    }

    public Role roleOf(Entity e) {
        String id = e.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
        return id == null ? null : Role.of(id);
    }

    /** Stellt eine Figur an die Position des Admins (Blickrichtung wie der Admin). */
    public Entity spawn(Role role, Location at) {
        Location loc = at.clone();
        loc.setPitch(0);
        Entity npc = switch (role) {
            case KASSE -> loc.getWorld().spawn(loc, Villager.class, v -> {
                v.setProfession(Villager.Profession.CARTOGRAPHER);
                v.setVillagerType(Villager.Type.PLAINS);
                v.setVillagerLevel(5);
                prepare(v);
            });
        };
        npc.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, role.id);
        spawnHolo(role, npc);
        return npc;
    }

    private void prepare(LivingEntity e) {
        e.setAI(false);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setCollidable(false);
        e.setPersistent(true);
        e.setRemoveWhenFarAway(false);
        e.setCustomNameVisible(false);
    }

    /** Hologramm: großer Titel zwischen zwei Chips, darunter die Zeilen der Rolle. */
    private void spawnHolo(Role role, Entity npc) {
        Location base = npc.getLocation().clone();
        base.setYaw(0);
        base.setPitch(0);
        String owner = npc.getUniqueId().toString();
        TextComponent.Builder title = Component.text();
        Key chipFont = Key.key("kuby", "k/i-5");
        title.append(Component.text(String.valueOf(KitData.SmallIcon.C_PINK.c)).font(chipFont).color(NamedTextColor.WHITE));
        title.append(Component.text(de.kuby.kubyplugins.ui.Canvas.titleChars(" " + role.title + " ", true))
                .font(Key.key("kuby", "title")).color(NamedTextColor.WHITE));
        title.append(Component.text(String.valueOf(KitData.SmallIcon.C_PINK.c)).font(chipFont).color(NamedTextColor.WHITE));
        holo(base.clone().add(0, 2.62, 0), title.build(), 0.62f, owner);
        holo(base.clone().add(0, 2.12, 0), body(role), 1.0f, owner);
    }

    private static Component body(Role role) {
        TextColor light = TextColor.color(0xE8E4DC);
        return switch (role) {
            case KASSE -> Component.text()
                    .append(Component.text("Coins ", light)).append(Component.text("⇄", NamedTextColor.GRAY))
                    .append(Component.text(" Chips", light)).append(Component.newline())
                    .append(Component.text("➜ ", NamedTextColor.YELLOW)).append(Component.text("Rechtsklick", light))
                    .build();
        };
    }

    private void holo(Location at, Component text, float scale, String owner) {
        at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setLineWidth(400);
            d.setPersistent(true);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale),
                    new AxisAngle4f()));
            d.getPersistentDataContainer().set(holoKey, PersistentDataType.STRING, owner);
        });
    }

    /** Entfernt die nächste Casino-Figur (mit Hologramm) im Umkreis von 5 Blöcken. */
    public boolean removeNear(Player admin) {
        Entity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity e : admin.getNearbyEntities(5, 5, 5)) {
            if (roleOf(e) == null) continue;
            double d = e.getLocation().distanceSquared(admin.getLocation());
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        if (nearest == null) return false;
        removeHolo(nearest);
        nearest.remove();
        return true;
    }

    private void removeHolo(Entity npc) {
        String owner = npc.getUniqueId().toString();
        for (TextDisplay d : npc.getWorld().getNearbyEntitiesByType(TextDisplay.class, npc.getLocation(), 6)) {
            if (owner.equals(d.getPersistentDataContainer().get(holoKey, PersistentDataType.STRING))) d.remove();
        }
    }

    /** True, wenn eine Figur dieser Rolle näher als r Blöcke ist. */
    public boolean near(Location l, Role role, double r) {
        if (l.getWorld() == null) return false;
        for (Entity e : l.getWorld().getNearbyEntities(l, r, r, r)) {
            if (roleOf(e) == role) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        Role role = roleOf(e.getRightClicked());
        if (role == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        action(role).accept(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (roleOf(e.getEntity()) != null) e.setCancelled(true);
    }

    private Consumer<Player> action(Role role) {
        return switch (role) {
            case KASSE -> p -> {
                String blocked = plugin.casino().checkMode(p);
                if (blocked != null) {
                    CasinoText.error(p, blocked);
                    return;
                }
                new KasseMenu(plugin).open(p);
            };
        };
    }

    /** /casino npc &lt;rolle&gt; | entfernen | liste */
    public void command(Player p, String[] args) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase() : "";
        if (sub.equals("entfernen")) {
            Msg.send(p, removeNear(p) ? "<green>Casino-Figur entfernt." : "<red>Keine Casino-Figur im Umkreis von 5 Blöcken.");
            return;
        }
        Role role = Role.of(sub);
        if (role == null) {
            StringBuilder ids = new StringBuilder();
            for (Role r : Role.values()) ids.append(ids.isEmpty() ? "" : "|").append(r.id);
            Msg.send(p, "<red>Nutzung: /casino npc <" + ids + "> | entfernen");
            return;
        }
        spawn(role, p.getLocation());
        Msg.send(p, "<green>" + role.title + " aufgestellt. Entfernen: /casino npc entfernen (davor stehen).");
    }
}

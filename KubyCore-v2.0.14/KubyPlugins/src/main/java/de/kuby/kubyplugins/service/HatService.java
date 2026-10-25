package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Hüte: eine Item-Anzeige (Modell kuby:hat/&lt;id&gt;) fährt auf dem Spieler mit und dreht sich mit Kopf-Gier und
 * -Neigung um den Hals. Liegen, Schwimmen, Gleiten, Unsichtbarkeit und Zuschauer-Modus blenden den Hut aus.
 * Wer den Spieler nicht sieht, sieht auch den Hut nicht; den eigenen Hut kann man ausblenden (Einstellung hat.self).
 * Die Anzeigen werden nicht gespeichert und nach jedem Teleport wieder aufgesetzt.
 */
public final class HatService implements Listener {

    /** Spieler-Modell 0,9375: Kopf oben bei 1,875, Hals bei 1,406; Mitfahrer sitzen bei 1,8 (geduckt 1,5). */
    private static final float LIFT_STAND = 0.075f, PIVOT_STAND = -0.394f, LIFT_SNEAK = 0.012f, PIVOT_SNEAK = -0.457f;

    private static final class Worn {
        final ItemDisplay display;
        final String model;
        float yaw = Float.NaN, pitch, lift;

        Worn(ItemDisplay display, String model) {
            this.display = display;
            this.model = model;
        }
    }

    private final KubyPlugins plugin;
    private final Map<UUID, Worn> worn = new HashMap<>();
    private int ticks;

    public HatService(KubyPlugins plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 2L);
    }

    private void tick() {
        ticks++;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            CosmeticService.Item it = plugin.cosmetics().find(plugin.cosmetics().activeId(p.getUniqueId(), "HAT"));
            boolean show = it != null && it.model() != null && visible(p)
                    && plugin.cosmetics().catalogue(p.getUniqueId()).stream().anyMatch(x -> x.id().equals(it.id()));
            Worn w = worn.get(p.getUniqueId());
            if (!show) {
                if (w != null) remove(p.getUniqueId());
                continue;
            }
            if (w != null && (!w.display.isValid() || !w.model.equals(it.model()) || w.display.getWorld() != p.getWorld())) {
                remove(p.getUniqueId());
                w = null;
            }
            if (w == null) {
                w = spawn(p, it);
                worn.put(p.getUniqueId(), w);
            } else if (!p.equals(w.display.getVehicle())) {
                // nach Teleport, Tod oder Weltwechsel abgeworfen: wieder aufsetzen
                w.display.teleport(p.getLocation());
                p.addPassenger(w.display);
            }
            update(p, w, false);
        }
        if (ticks % 10 == 0) syncVisibility();
    }

    private static boolean visible(Player p) {
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.hasPotionEffect(PotionEffectType.INVISIBILITY)) return false;
        if (p.isGliding() || p.isSwimming() || p.isSleeping() || p.isRiptiding()) return false;
        Pose pose = p.getPose();
        return pose == Pose.STANDING || pose == Pose.SNEAKING;
    }

    private Worn spawn(Player p, CosmeticService.Item it) {
        Location at = p.getLocation();
        at.setPitch(0);
        at.setYaw(0);
        ItemDisplay d = p.getWorld().spawn(at, ItemDisplay.class, e -> {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setItemModel(new NamespacedKey("kuby", "hat/" + it.model()));
            item.setItemMeta(meta);
            e.setItemStack(item);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setPersistent(false);
            e.setViewRange(0.6f);
            e.setShadowRadius(0);
            if (it.model().equals("heiligenschein")) e.setBrightness(new Display.Brightness(15, 15));
        });
        p.addPassenger(d);
        Worn w = new Worn(d, it.model());
        for (Player viewer : plugin.getServer().getOnlinePlayers()) applyVisibility(viewer, p, d);
        return w;
    }

    /** Drehung um den Hals: Gier und Neigung des Kopfes, Höhe nach Haltung. */
    private void update(Player p, Worn w, boolean force) {
        Location l = p.getLocation();
        float yaw = l.getYaw(), pitch = l.getPitch();
        boolean sneak = p.getPose() == Pose.SNEAKING;
        float lift = sneak ? LIFT_SNEAK : LIFT_STAND;
        if (!force && !Float.isNaN(w.yaw) && Math.abs(wrap(yaw - w.yaw)) < 1.5f && Math.abs(pitch - w.pitch) < 1.5f && lift == w.lift) {
            return;
        }
        boolean first = Float.isNaN(w.yaw);
        w.yaw = yaw;
        w.pitch = pitch;
        w.lift = lift;
        float pivot = sneak ? PIVOT_SNEAK : PIVOT_STAND;
        Quaternionf r = new Quaternionf().rotateY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(pitch));
        // Punkt m auf dem Kopf: P + R (m + L - P)  =>  Verschiebung P + R (L - P)
        Vector3f t = new Vector3f(0, lift - pivot, 0);
        r.transform(t);
        t.add(0, pivot, 0);
        w.display.setInterpolationDelay(0);
        w.display.setInterpolationDuration(first ? 0 : 2);
        w.display.setTransformation(new Transformation(t, r, new Vector3f(1, 1, 1), new Quaternionf()));
    }

    private static float wrap(float a) {
        a %= 360;
        if (a > 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }

    private void applyVisibility(Player viewer, Player owner, ItemDisplay d) {
        boolean should = viewer.equals(owner) ? plugin.playerSettings().get(owner.getUniqueId(), "hat.self") : viewer.canSee(owner);
        if (should && !viewer.canSee(d)) viewer.showEntity(plugin, d);
        else if (!should && viewer.canSee(d)) viewer.hideEntity(plugin, d);
    }

    private void syncVisibility() {
        for (Map.Entry<UUID, Worn> e : worn.entrySet()) {
            Player owner = plugin.getServer().getPlayer(e.getKey());
            if (owner == null) continue;
            for (Player viewer : plugin.getServer().getOnlinePlayers()) applyVisibility(viewer, owner, e.getValue().display);
        }
    }

    /** Sofort neu prüfen (nach An-/Ablegen oder Umschalten der Einstellung). */
    public void refresh(Player p) {
        Worn w = worn.get(p.getUniqueId());
        if (w != null) for (Player viewer : plugin.getServer().getOnlinePlayers()) applyVisibility(viewer, p, w.display);
    }

    private void remove(UUID id) {
        Worn w = worn.remove(id);
        if (w != null) w.display.remove();
    }

    public void removeAll() {
        for (Iterator<Worn> it = worn.values().iterator(); it.hasNext(); ) {
            Entity e = it.next().display;
            e.remove();
            it.remove();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        remove(e.getPlayer().getUniqueId());
    }
}

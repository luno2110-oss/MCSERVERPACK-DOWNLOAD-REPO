package de.kuby.kubyplugins.cookie;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.cookie.CookieService.Data;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Die Station im Spiel: ein großer, sich drehender Keks über einem Sockel (Linksklick backt, Rechtsklick öffnet das
 * Menü), darüber für jeden Spieler sein eigenes Schild (Titel, Stufe, Kekse, pro Sekunde, je Klick, Hinweise,
 * Keksregen), daneben die Rangliste. Glückskekse erscheinen nur für den jeweiligen Spieler neben dem Keks und leuchten
 * golden. Alle 30 Minuten regnet es Kekse, die jeder fangen kann. Ort in der config (cookie.station).
 */
public final class CookieStation implements Listener {

    private static final float SCALE = 2.6f;

    private final KubyPlugins plugin;
    private final CookieService cookies;
    private final NamespacedKey rainKey;
    private final Random rnd = new Random();
    private ItemDisplay cookie;
    private Interaction box;
    private TextDisplay top;
    private final Map<UUID, TextDisplay> holos = new HashMap<>();
    /** Glückskeks je Spieler: Anzeige, Klickfläche, Ende. */
    private final Map<UUID, Object[]> golden = new HashMap<>();
    private final List<Item> rain = new ArrayList<>();
    private float angle;
    private int ticks;
    private long nextRain, rainUntil;

    public CookieStation(KubyPlugins plugin, CookieService cookies) {
        this.plugin = plugin;
        this.cookies = cookies;
        this.rainKey = new NamespacedKey(plugin, "cookie_rain");
        this.nextRain = System.currentTimeMillis() + rainEvery();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 5L);
    }

    long rainEvery() {
        return Math.max(1, plugin.getConfig().getLong("cookie.keksregen.minuten", 30)) * 60_000L;
    }

    // =================================================================================================================
    // Ort
    // =================================================================================================================

    public Location location() {
        String s = plugin.getConfig().getString("cookie.station", "");
        if (s == null || s.isBlank()) return null;
        String[] p = s.split(";");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void set(Location l) {
        despawn();
        plugin.getConfig().set("cookie.station", l.getWorld().getName() + ";" + (l.getBlockX() + 0.5) + ";" + l.getBlockY() + ";" + (l.getBlockZ() + 0.5));
        plugin.saveConfig();
    }

    public void remove() {
        despawn();
        plugin.getConfig().set("cookie.station", "");
        plugin.saveConfig();
    }

    /** Zum Keks (vor die Station, Blick auf den Keks). */
    public Location spot() {
        Location l = location();
        if (l == null) return null;
        Location s = l.clone().add(0, 0, 4);
        s.setYaw(180);
        s.setPitch(-15);
        return s;
    }

    // =================================================================================================================
    // Entities
    // =================================================================================================================

    private boolean valid(Entity e) {
        return e != null && e.isValid();
    }

    private void spawn(Location l) {
        World w = l.getWorld();
        if (!valid(cookie)) {
            cookie = w.spawn(l.clone().add(0, 2.4, 0), ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setItemStack(item(Material.COOKIE, "kuby:cookie/big"));
                d.setBillboard(Display.Billboard.FIXED);
                d.setTransformation(transform(0, SCALE));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTeleportDuration(0);
            });
        }
        if (!valid(box)) {
            box = w.spawn(l.clone().add(0, 1.15, 0), Interaction.class, i -> {
                i.setPersistent(false);
                i.setInteractionWidth(2.6f);
                i.setInteractionHeight(2.6f);
                i.setResponsive(true);
            });
        }
        if (!valid(top)) {
            top = w.spawn(l.clone().add(3.6, 2.2, 0), TextDisplay.class, t -> {
                t.setPersistent(false);
                t.setBillboard(Display.Billboard.CENTER);
                t.setDefaultBackground(false);
                t.setBackgroundColor(Color.fromARGB(110, 20, 12, 6));
                t.setShadowed(true);
                t.setLineWidth(220);
            });
            updateTop();
        }
    }

    public void despawn() {
        for (Entity e : new Entity[]{cookie, box, top}) if (valid(e)) e.remove();
        cookie = null;
        box = null;
        top = null;
        for (TextDisplay t : holos.values()) if (valid(t)) t.remove();
        holos.clear();
        for (UUID id : new ArrayList<>(golden.keySet())) removeGolden(id, false);
        for (Item i : rain) if (valid(i)) i.remove();
        rain.clear();
    }

    private ItemStack item(Material base, String model) {
        ItemStack it = new ItemStack(base);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(NamespacedKey.fromString(model));
        it.setItemMeta(m);
        return it;
    }

    private static Transformation transform(float deg, float scale) {
        Quaternionf q = new Quaternionf(new AxisAngle4f((float) Math.toRadians(deg), 0, 1, 0));
        return new Transformation(new Vector3f(), q, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    // =================================================================================================================
    // Alle 5 Ticks
    // =================================================================================================================

    private void tick() {
        Location l = location();
        if (l == null || !l.isChunkLoaded()) return;
        ticks++;
        boolean anyone = false;
        for (Player p : l.getWorld().getPlayers()) if (p.getLocation().distanceSquared(l) < 48 * 48) anyone = true;
        if (!anyone) {
            if (valid(cookie)) despawn();
            return;
        }
        spawn(l);
        // Drehen: alle 20 Ticks eine Vierteldrehung, weich dazwischen
        if (ticks % 4 == 0 && valid(cookie)) {
            angle = (angle + 90) % 360;
            cookie.setInterpolationDelay(0);
            cookie.setInterpolationDuration(20);
            cookie.setTransformation(transform(angle, SCALE));
        }
        long now = System.currentTimeMillis();
        // Eigene Schilder und Glückskekse
        for (Player p : l.getWorld().getPlayers()) {
            boolean near = p.getLocation().distanceSquared(l) < 32 * 32;
            UUID id = p.getUniqueId();
            if (!near) {
                TextDisplay t = holos.remove(id);
                if (valid(t)) t.remove();
                continue;
            }
            if (ticks % 4 == 0) holo(p, l);
            Data d = cookies.data(id);
            Object[] g = golden.get(id);
            if (g != null && now > (long) g[2]) {
                removeGolden(id, true);
            } else if (g == null && p.getLocation().distanceSquared(l) < 24 * 24) {
                if (d.nextGolden == 0) d.nextGolden = now + cookies.goldenDelay(d);
                else if (now >= d.nextGolden) spawnGolden(p, l);
            }
        }
        for (UUID id : new ArrayList<>(holos.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.getWorld().equals(l.getWorld())) {
                TextDisplay t = holos.remove(id);
                if (valid(t)) t.remove();
            }
        }
        if (ticks % 40 == 0) updateTop();
        rainTick(l, now);
    }

    private void holo(Player p, Location l) {
        UUID id = p.getUniqueId();
        TextDisplay t = holos.get(id);
        if (!valid(t)) {
            t = l.getWorld().spawn(l.clone().add(0, 4.35, 0), TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setShadowed(true);
                d.setLineWidth(260);
            });
            p.showEntity(plugin, t);
            holos.put(id, t);
        }
        t.text(Msg.parse(holoText(cookies.data(id))));
    }

    String holoText(Data d) {
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        sb.append("<#F2C443><b>").append(Msg.escape(cookies.title(d).toUpperCase())).append("</b> <dark_gray>· <gray>STUFE <white>")
                .append(CookieService.fmt(Math.max(d.level, CookieService.levelFor(d.allTime)))).append('\n');
        sb.append("<gray>KEKSE <dark_gray>» <white><b>").append(CookieService.fmt(d.cookies)).append("</b>\n");
        sb.append("<gray>PRO SEK <dark_gray>» <green>+").append(CookieService.fmt(d.cps())).append("   <gray>KLICK <dark_gray>» <yellow>+")
                .append(CookieService.fmt(cookies.clickValue(d) * (now < d.clickUntil ? d.clickMult : 1))).append('\n');
        if (d.prodName != null && now < d.prodUntil) {
            sb.append("<gold>⚡ ").append(d.prodName).append(" x").append(CookieService.fmt(d.prodMult)).append(" <gray>noch <white>")
                    .append((d.prodUntil - now) / 1000).append(" s\n");
        }
        if (d.clickName != null && now < d.clickUntil) {
            sb.append("<gold>⚡ ").append(d.clickName).append(" x").append(CookieService.fmt(d.clickMult)).append(" <gray>noch <white>")
                    .append((d.clickUntil - now) / 1000).append(" s\n");
        }
        if (cookies.affordableUpgrades(d) + cookies.affordableBuildings(d) > 0) {
            sb.append("<green>▲ Neues Upgrade kaufbar <dark_gray>– <gray>Rechtsklick\n");
        } else if (d.allTime < 50) {
            sb.append("<yellow>Linksklick <gray>backt · <yellow>Rechtsklick <gray>öffnet das Menü\n");
        }
        if (now < rainUntil) sb.append("<gold><b>KEKSREGEN!</b> <white>Fang die Kekse");
        else sb.append("<gray>KEKSREGEN IN <yellow>").append(time(nextRain - now));
        return sb.toString();
    }

    static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 60 ? (s / 60) + " min " + (s % 60) + " s" : s + " s";
    }

    private void updateTop() {
        if (!valid(top)) return;
        StringBuilder sb = new StringBuilder("<#F2C443><b>KEKSE ALLER ZEITEN</b>");
        int i = 0;
        for (Map.Entry<UUID, Data> e : cookies.top(CookieService.Board.ALLTIME, 10)) {
            i++;
            String c = i == 1 ? "<gold>" : i == 2 ? "<#D8DEE8>" : i == 3 ? "<#D08A4A>" : "<gray>";
            sb.append('\n').append(c).append('#').append(i).append(" <white>").append(Msg.escape(e.getValue().name.isEmpty() ? "?" : e.getValue().name))
                    .append(" <dark_gray>· <#F2C443>").append(CookieService.fmt(e.getValue().allTime));
        }
        if (i == 0) sb.append("\n<gray>Noch niemand. Klick den Keks!");
        top.text(Msg.parse(sb.toString()));
    }

    // =================================================================================================================
    // Glückskekse
    // =================================================================================================================

    private void spawnGolden(Player p, Location l) {
        double a = rnd.nextDouble() * Math.PI * 2, r = 2 + rnd.nextDouble() * 1.5;
        Location at = l.clone().add(Math.cos(a) * r, 1.2 + rnd.nextDouble() * 1.8, Math.sin(a) * r);
        ItemDisplay d = l.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setVisibleByDefault(false);
            e.setItemStack(item(Material.COOKIE, "kuby:cookie/gold"));
            e.setBillboard(Display.Billboard.CENTER);
            e.setTransformation(transform(0, 0.9f));
            e.setGlowing(true);
            e.setGlowColorOverride(Color.fromRGB(0xF2C443));
            e.setBrightness(new Display.Brightness(15, 15));
        });
        Interaction i = l.getWorld().spawn(at.clone().add(0, -0.45, 0), Interaction.class, e -> {
            e.setPersistent(false);
            e.setVisibleByDefault(false);
            e.setInteractionWidth(0.9f);
            e.setInteractionHeight(0.9f);
        });
        p.showEntity(plugin, d);
        p.showEntity(plugin, i);
        long life = plugin.getConfig().getLong("cookie.glueckskeks.sekunden", 13) * 1000;
        golden.put(p.getUniqueId(), new Object[]{d, i, System.currentTimeMillis() + life});
        p.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.6f);
        p.spawnParticle(Particle.WAX_ON, at, 12, 0.3, 0.3, 0.3, 0.5);
        p.sendActionBar(Msg.parse("<gold><b>✦ GLÜCKSKEKS ✦</b> <white>schnell anklicken!"));
    }

    private void removeGolden(UUID id, boolean missed) {
        Object[] g = golden.remove(id);
        if (g == null) return;
        for (int k = 0; k < 2; k++) if (g[k] instanceof Entity e && e.isValid()) e.remove();
        Data d = cookies.data(id);
        if (missed) d.goldenMissed++;
        d.nextGolden = System.currentTimeMillis() + cookies.goldenDelay(d);
    }

    private boolean catchGolden(Player p, Entity clicked) {
        Object[] g = golden.get(p.getUniqueId());
        if (g == null || (g[0] != clicked && g[1] != clicked)) return false;
        Location at = ((Entity) g[0]).getLocation();
        removeGolden(p.getUniqueId(), false);
        String msg = cookies.catchGolden(p);
        Msg.send(p, "<#F2C443>✦ " + msg);
        p.sendActionBar(Msg.parse(msg));
        p.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
        p.spawnParticle(Particle.TOTEM_OF_UNDYING, at, 30, 0.3, 0.3, 0.3, 0.3);
        return true;
    }

    // =================================================================================================================
    // Keksregen
    // =================================================================================================================

    private void rainTick(Location l, long now) {
        if (rainUntil == 0 && now >= nextRain) {
            rainUntil = now + 20_000;
            nextRain = now + rainEvery();
            for (Player p : l.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(l) < 48 * 48) {
                    Msg.send(p, "<gold><b>KEKSREGEN!</b> <white>20 Sekunden lang regnet es Kekse an der Station. Fang so viele du kannst!");
                    p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1.2f);
                }
            }
        }
        if (rainUntil > 0) {
            if (now < rainUntil) {
                for (int k = 0; k < 3; k++) {
                    double a = rnd.nextDouble() * Math.PI * 2, r = 1.5 + rnd.nextDouble() * 5;
                    Location at = l.clone().add(Math.cos(a) * r, 7 + rnd.nextDouble() * 2, Math.sin(a) * r);
                    Item it = l.getWorld().dropItem(at, item(Material.COOKIE, "kuby:cookie/regen"), e -> {
                        e.setPersistent(false);
                        e.setPickupDelay(5);
                        e.setCanMobPickup(false);
                        e.getPersistentDataContainer().set(rainKey, PersistentDataType.BYTE, (byte) 1);
                        e.setVelocity(new Vector(0, -0.1, 0));
                    });
                    rain.add(it);
                }
            } else if (now > rainUntil + 15_000) {
                for (Item i : rain) if (valid(i)) i.remove();
                rain.clear();
                rainUntil = 0;
            }
        }
    }

    /** Admin: Keksregen sofort. */
    public void startRain() {
        nextRain = System.currentTimeMillis();
        rainUntil = 0;
    }

    /** Admin: Glückskeks für den Spieler sofort. */
    public void goldenNow(Player p) {
        cookies.data(p.getUniqueId()).nextGolden = 1;
    }

    private boolean isRain(Item i) {
        return i.getPersistentDataContainer().has(rainKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent e) {
        if (!isRain(e.getItem())) return;
        e.setCancelled(true);
        if (!(e.getEntity() instanceof Player p)) return;
        e.getItem().remove();
        double v = cookies.catchRain(p);
        p.sendActionBar(Msg.parse("<gold>🍪 Keksregen <white>+" + CookieService.fmt(v) + " Kekse"));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.4f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHopper(InventoryPickupItemEvent e) {
        if (isRain(e.getItem())) {
            e.setCancelled(true);
            e.getItem().remove();
        }
    }

    @EventHandler
    public void onMerge(ItemMergeEvent e) {
        if (isRain(e.getEntity()) || isRain(e.getTarget())) e.setCancelled(true);
    }

    // =================================================================================================================
    // Klicken
    // =================================================================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        Entity t = e.getAttacked();
        Player p = e.getPlayer();
        if (catchGolden(p, t)) {
            e.setCancelled(true);
            return;
        }
        if (t != box || box == null) return;
        e.setCancelled(true);
        double v = cookies.click(p);
        if (v <= 0) return;
        Data d = cookies.data(p.getUniqueId());
        double combo = cookies.combo(d);
        p.sendActionBar(Msg.parse("<#F2C443>+" + CookieService.fmt(v) + " Kekse" + (combo > 1.01 ? " <gray>· Combo <yellow>x"
                + String.format(java.util.Locale.GERMANY, "%.2f", combo) : "")));
        p.playSound(cookie.getLocation(), Sound.BLOCK_WOOD_HIT, 0.5f, 1.4f + (float) (combo - 1) * 0.3f);
        p.spawnParticle(Particle.ITEM, cookie.getLocation(), 6, 0.4, 0.4, 0.4, 0.08, new ItemStack(Material.COOKIE));
        // kurz eindrücken
        cookie.setInterpolationDelay(0);
        cookie.setInterpolationDuration(2);
        cookie.setTransformation(transform(angle, SCALE * 0.9f));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!valid(cookie)) return;
            cookie.setInterpolationDelay(0);
            cookie.setInterpolationDuration(3);
            cookie.setTransformation(transform(angle, SCALE));
        }, 2L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        if (e.getHand() != EquipmentSlot.HAND) {
            if (t == box || isGolden(t)) e.setCancelled(true);
            return;
        }
        Player p = e.getPlayer();
        if (catchGolden(p, t)) {
            e.setCancelled(true);
            return;
        }
        if (t != box || box == null) return;
        e.setCancelled(true);
        Sfx.play(plugin, p, Sfx.Kind.OPEN);
        CookieScreen.main(p);
    }

    private boolean isGolden(Entity t) {
        for (Object[] g : golden.values()) if (g[0] == t || g[1] == t) return true;
        return false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        TextDisplay t = holos.remove(e.getPlayer().getUniqueId());
        if (valid(t)) t.remove();
        removeGolden(e.getPlayer().getUniqueId(), false);
    }
}

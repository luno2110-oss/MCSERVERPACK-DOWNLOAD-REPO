package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Möbel im Casino frei aufstellen (v2.07, Wunsch des Besitzers: „ich brauche eine Möglichkeit, die Möbel im Casino frei
 * platzieren zu können“). Katalog wie im Survival-Juwelenshop (resources/casino/moebel.yml, 190 Stücke aus
 * tools/build_furniture.py, Modelle kuby:furniture/&lt;id&gt;): Stühle, Bänke, Hocker, Tische, Pflanzen, Deko, Lampen.
 *
 * <p>Admins stellen ein Stück mit /casino moebel &lt;id&gt; genau dort auf, wo sie stehen (nicht auf Blöcke gerastert),
 * Vorderseite zu ihnen. Verschieben, Drehen in 15°-Schritten, anderes Stück desselben Reiters („Variante“) und
 * Entfernen im Casino-Admin (Kategorie Möbel). Sitzplätze funktionieren (Rechtsklick), Lampen leuchten.
 * Gespeichert in casino-moebel.yml; die Anzeigen sind dauerhaft und tragen ihre Nummer.
 */
public final class CasinoDecor implements Placeable, Listener {

    /** Katalog-Eintrag: Reiter, Name, Höhe der Klickfläche, Sitze [x, z] (Blöcke, +z = vorne), Sitzhöhe, Licht. */
    public record Def(String id, String tab, String name, float height, List<float[]> seats, float seatY, int light) {}

    /** Aufgestelltes Stück. */
    private static final class Placed {
        final String id;
        String def;
        Location at;
        float yaw;

        Placed(String id, String def, Location at, float yaw) {
            this.id = id;
            this.def = def;
            this.at = at;
            this.yaw = yaw;
        }
    }

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey partKey, seatKey;
    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<String, String> tabs = new LinkedHashMap<>();
    private final Map<String, Placed> placed = new LinkedHashMap<>();
    private int nextId = 1;

    public CasinoDecor(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "casino-moebel.yml");
        this.partKey = new NamespacedKey(plugin, "casino_decor");
        this.seatKey = new NamespacedKey(plugin, "casino_decor_seat");
        loadCatalog();
        load();
    }

    private void loadCatalog() {
        try (InputStream in = CasinoDecor.class.getResourceAsStream("/casino/moebel.yml")) {
            if (in == null) return;
            YamlConfiguration y = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String t : y.getStringList("reiter")) {
                String[] p = t.split(";");
                if (p.length >= 2) tabs.put(p[0], p[1]);
            }
            ConfigurationSection m = y.getConfigurationSection("moebel");
            if (m == null) return;
            for (String id : m.getKeys(false)) {
                ConfigurationSection c = m.getConfigurationSection(id);
                if (c == null) continue;
                List<float[]> seats = new ArrayList<>();
                for (Object o : c.getList("sitze", List.of())) {
                    if (o instanceof List<?> l && l.size() >= 2) {
                        seats.add(new float[]{((Number) l.get(0)).floatValue(), ((Number) l.get(1)).floatValue()});
                    }
                }
                defs.put(id, new Def(id, c.getString("reiter", "deko"), c.getString("name", id), (float) c.getDouble("hoehe", 1),
                        seats, (float) c.getDouble("sitzhoehe", 0.5), c.getInt("licht", -1)));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("casino/moebel.yml nicht lesbar: " + e.getMessage());
        }
    }

    public Map<String, Def> catalog() {
        return defs;
    }

    // ---------- Speichern ----------

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = y.getInt("next", 1);
        for (String line : y.getStringList("moebel")) {
            String[] p = line.split(";");
            if (p.length < 7) continue;
            World w = plugin.getServer().getWorld(p[2]);
            if (w == null || !defs.containsKey(p[1])) continue;
            try {
                Location at = new Location(w, Double.parseDouble(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5]));
                placed.put(p[0], new Placed(p[0], p[1], at, Float.parseFloat(p[6])));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next", nextId);
        List<String> out = new ArrayList<>();
        for (Placed f : placed.values()) {
            out.add(String.format(Locale.ROOT, "%s;%s;%s;%.3f;%.3f;%.3f;%.1f", f.id, f.def, f.at.getWorld().getName(),
                    f.at.getX(), f.at.getY(), f.at.getZ(), f.yaw));
        }
        y.set("moebel", out);
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("casino-moebel.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // ---------- Anzeigen ----------

    /** Beim Start und wenn Chunks laden: fehlende Anzeigen ergänzen (doppelte entfernen). */
    public void respawn() {
        for (Placed f : placed.values()) ensure(f);
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent e) {
        for (Placed f : placed.values()) {
            if (f.at.getWorld() == e.getWorld() && (f.at.getBlockX() >> 4) == e.getChunk().getX() && (f.at.getBlockZ() >> 4) == e.getChunk().getZ()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> ensure(f));
            }
        }
    }

    private List<Entity> partsOf(Placed f) {
        List<Entity> out = new ArrayList<>();
        if (!f.at.isChunkLoaded()) return out;
        for (Entity e : f.at.getWorld().getNearbyEntities(f.at, 4, 4, 4)) {
            if (f.id.equals(e.getPersistentDataContainer().get(partKey, PersistentDataType.STRING))) out.add(e);
        }
        return out;
    }

    private void ensure(Placed f) {
        if (!f.at.isChunkLoaded()) return;
        List<Entity> parts = partsOf(f);
        long displays = parts.stream().filter(e -> e instanceof ItemDisplay).count();
        if (displays == 1) return;
        for (Entity e : parts) e.remove();
        spawn(f);
    }

    private void spawn(Placed f) {
        Def d = defs.get(f.def);
        if (d == null) return;
        Location base = f.at.clone();
        base.setYaw(f.yaw);
        base.setPitch(0);
        ItemStack shown = ItemStack.of(Material.PAPER);
        shown.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, Key.key("kuby", "furniture/" + d.id()));
        // Modell aus dem Pack: Mitte (8, 8, 8) einen halben Block über dem Boden, wie im Survival-Juwelenshop
        f.at.getWorld().spawn(base.clone().add(0, 0.5, 0), ItemDisplay.class, e -> {
            e.setItemStack(shown);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setPersistent(true);
            e.getPersistentDataContainer().set(partKey, PersistentDataType.STRING, f.id);
        });
        if (!d.seats().isEmpty()) {
            for (float[] s : d.seats()) {
                Location l = world(f, s[0], 0, s[1]);
                f.at.getWorld().spawn(l, Interaction.class, e -> {
                    e.setInteractionWidth(0.8f);
                    e.setInteractionHeight(Math.max(0.3f, d.seatY() + 0.1f));
                    e.setResponsive(true);
                    e.setPersistent(true);
                    e.getPersistentDataContainer().set(partKey, PersistentDataType.STRING, f.id);
                });
            }
        }
        if (d.light() >= 0) {
            Block b = f.at.getBlock();
            if (b.getType() == Material.AIR || b.getType() == Material.LIGHT) {
                b.setType(Material.LIGHT, false);
                if (b.getBlockData() instanceof org.bukkit.block.data.Levelled lv) {
                    lv.setLevel(Math.max(1, Math.min(15, d.light() == 0 ? 15 : d.light())));
                    b.setBlockData(lv, false);
                }
            }
        }
    }

    private void despawn(Placed f) {
        for (Entity e : partsOf(f)) {
            for (Entity p : e.getPassengers()) e.removePassenger(p);
            e.remove();
        }
        Def d = defs.get(f.def);
        if (d != null && d.light() >= 0 && f.at.isChunkLoaded() && f.at.getBlock().getType() == Material.LIGHT) {
            f.at.getBlock().setType(Material.AIR, false);
        }
    }

    /** Lokaler Punkt (x, y, z; +z = vorne) in Weltkoordinaten, gedreht um yaw (wie FurnitureService). */
    private static Location world(Placed f, double x, double y, double z) {
        double r = Math.toRadians(f.yaw);
        double cos = Math.cos(r), sin = Math.sin(r);
        return f.at.clone().add(x * cos - z * sin, y, x * sin + z * cos);
    }

    // ---------- Sitzen ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        String id = e.getRightClicked().getPersistentDataContainer().get(partKey, PersistentDataType.STRING);
        if (id == null || !(e.getRightClicked() instanceof Interaction hit)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Placed f = placed.get(id);
        Player p = e.getPlayer();
        if (f == null || p.isInsideVehicle()) return;
        Def d = defs.get(f.def);
        Location seat = hit.getLocation().add(0, d.seatY() - 0.1, 0);
        for (Entity s : seat.getWorld().getNearbyEntities(seat, 0.3, 0.3, 0.3)) {
            if (s instanceof ArmorStand && s.getPersistentDataContainer().has(seatKey, PersistentDataType.DOUBLE) && !s.getPassengers().isEmpty()) {
                Msg.send(p, "<gray>Hier sitzt schon jemand.");
                return;
            }
        }
        seat.setYaw(f.yaw);
        double floor = f.at.getY();
        ArmorStand a = seat.getWorld().spawn(seat, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setMarker(true);
            s.setGravity(false);
            s.setSmall(true);
            s.setInvulnerable(true);
            s.setPersistent(false);
            s.getPersistentDataContainer().set(seatKey, PersistentDataType.DOUBLE, floor);
        });
        if (!a.addPassenger(p)) a.remove();
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getDismounted() instanceof ArmorStand a)) return;
        Double floor = a.getPersistentDataContainer().get(seatKey, PersistentDataType.DOUBLE);
        if (floor == null) return;
        Location to = a.getLocation().add(a.getLocation().getDirection().setY(0).normalize().multiply(0.7));
        to.setY(floor);
        to.setYaw(a.getLocation().getYaw());
        Entity who = e.getEntity();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            a.remove();
            if (who.isValid()) who.teleport(to);
        });
    }

    // ---------- Befehl ----------

    /** /casino moebel &lt;id&gt; | liste [reiter] | entfernen */
    public void command(Player p, String[] args) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "liste";
        if (sub.equals("liste")) {
            String tab = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : null;
            if (tab == null) {
                StringBuilder b = new StringBuilder("<gray>Möbel-Reiter: ");
                for (Map.Entry<String, String> t : tabs.entrySet()) {
                    b.append("<click:run_command:'/casino moebel liste ").append(t.getKey()).append("'><gold>[").append(t.getValue())
                            .append("]</gold></click> ");
                }
                Msg.send(p, b.toString());
                return;
            }
            StringBuilder b = new StringBuilder("<gray>" + tabs.getOrDefault(tab, tab) + ": ");
            for (Def d : defs.values()) {
                if (!d.tab().equals(tab)) continue;
                b.append("<click:run_command:'/casino moebel ").append(d.id()).append("'><hover:show_text:'Hier aufstellen'><white>")
                        .append(Msg.escape(d.name())).append("</white></hover></click><dark_gray> · </dark_gray>");
            }
            Msg.send(p, b.toString());
            return;
        }
        if (sub.equals("entfernen")) {
            Placed near = null;
            double best = 16;
            for (Placed f : placed.values()) {
                if (f.at.getWorld() != p.getWorld()) continue;
                double d = f.at.distanceSquared(p.getLocation());
                if (d < best) {
                    best = d;
                    near = f;
                }
            }
            Msg.send(p, near != null && remove(near.id) == null ? "<green>Möbel entfernt." : "<red>Kein Casino-Möbel im Umkreis von 4 Blöcken.");
            return;
        }
        Def d = defs.get(sub);
        if (d == null) {
            Msg.send(p, "<red>Unbekanntes Möbel „" + Msg.escape(sub) + "“. Katalog: /casino moebel liste");
            return;
        }
        Location at = p.getLocation().clone();
        place(d.id(), at, Placeable.norm(Math.round((at.getYaw() + 180f) / 15f) * 15f));
        Msg.send(p, "<green>" + d.name() + " aufgestellt. Feinjustieren, drehen oder tauschen im Casino-Admin (Möbel).");
    }

    /** Stück aufstellen (Boden-Mitte at, Vorderseite in Richtung yaw); null, wenn es das Stück nicht gibt. */
    public String place(String def, Location at, float yaw) {
        if (!defs.containsKey(def)) return null;
        Placed f = new Placed("d" + nextId++, def, new Location(at.getWorld(), at.getX(), at.getY(), at.getZ()), yaw);
        placed.put(f.id, f);
        spawn(f);
        save();
        return f.id;
    }

    public List<String> tab(String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 2) {
            out.add("liste");
            out.add("entfernen");
            out.addAll(defs.keySet());
        } else if (args.length == 3 && args[1].equalsIgnoreCase("liste")) {
            out.addAll(tabs.keySet());
        }
        return out;
    }

    // ---------- Casino-Admin ----------

    @Override
    public List<Piece> pieces() {
        List<Piece> out = new ArrayList<>();
        for (Placed f : placed.values()) {
            Def d = defs.get(f.def);
            out.add(new Piece(f.id, d == null ? f.def : d.name(), f.at, f.yaw, Double.NaN,
                    d == null ? "" : tabs.getOrDefault(d.tab(), d.tab()), false));
        }
        return out;
    }

    @Override
    public String move(String id, double dx, double dy, double dz, float dyaw) {
        Placed f = placed.get(id);
        if (f == null) return "Möbel nicht gefunden.";
        despawn(f);
        f.at = f.at.clone().add(dx, dy, dz);
        // Möbel drehen in 15°-Schritten (die 90°-Knöpfe drehen um 90°)
        f.yaw = Placeable.norm(f.yaw + dyaw);
        spawn(f);
        save();
        return null;
    }

    @Override
    public String remove(String id) {
        Placed f = placed.remove(id);
        if (f == null) return "Möbel nicht gefunden.";
        despawn(f);
        save();
        return null;
    }

    @Override
    public String variant(String id, int dir) {
        Placed f = placed.get(id);
        if (f == null) return "Möbel nicht gefunden.";
        Def cur = defs.get(f.def);
        List<String> same = new ArrayList<>();
        for (Def d : defs.values()) if (cur == null || d.tab().equals(cur.tab())) same.add(d.id());
        if (same.size() < 2) return "Kein anderes Stück in diesem Reiter.";
        int i = Math.max(0, same.indexOf(f.def));
        despawn(f);
        f.def = same.get(Math.floorMod(i + dir, same.size()));
        spawn(f);
        save();
        return null;
    }

    @Override
    public String variantName(String id) {
        Placed f = placed.get(id);
        if (f == null) return null;
        Def cur = defs.get(f.def);
        if (cur == null) return f.def;
        int n = 0, i = 0;
        for (Def d : defs.values()) {
            if (!d.tab().equals(cur.tab())) continue;
            n++;
            if (d.id().equals(cur.id())) i = n;
        }
        return cur.name() + " (" + i + "/" + n + ")";
    }

    /** Selbsttest: Katalog geladen. */
    public List<String> selfTest() {
        List<String> err = new ArrayList<>();
        if (defs.size() < 100) err.add("Katalog: nur " + defs.size() + " Möbel");
        if (tabs.isEmpty()) err.add("Katalog: keine Reiter");
        return err;
    }
}

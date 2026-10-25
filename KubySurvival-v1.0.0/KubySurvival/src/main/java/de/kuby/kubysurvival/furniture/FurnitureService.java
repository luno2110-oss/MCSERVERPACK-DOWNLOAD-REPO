package de.kuby.kubysurvival.furniture;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Möbel und Sitzen in Survival. Möbel kauft man im Juwelenshop (Item), Rechtsklick auf einen Block in einem Grundstück,
 * in dem man bauen darf, stellt es auf (zum Spieler gedreht). Schleichen + Rechtsklick dreht, Schleichen + Linksklick
 * hebt es auf. Höchstens furniture.max (50) je Spieler. Auf Stühle, Bänke, Hocker und Sofas setzt man sich mit
 * Rechtsklick (überall), aufstehen mit Schleichen. /sit setzt dich auf den Boden, /lay legt dich hin (zählt beim
 * Schlafen mit). Gespeichert in furniture.yml.
 */
public final class FurnitureService implements Listener {

    /** Ein aufgestelltes Möbelstück; at = Mitte unten des ersten Blocks, yaw in 45°-Schritten. */
    public static final class Placed {
        final String id;
        final UUID owner;
        final FurnitureCatalog.Def def;
        final Location at;
        float yaw;

        Placed(String id, UUID owner, FurnitureCatalog.Def def, Location at, float yaw) {
            this.id = id;
            this.owner = owner;
            this.def = def;
            this.at = at;
            this.yaw = yaw;
        }
    }

    private static final String PREFIX = "<#F0A050><b>MÖBEL</b> <dark_gray>▶ <gray>";

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File file;
    private final NamespacedKey itemKey, partKey, seatKey;
    private final Map<String, Placed> placed = new LinkedHashMap<>();
    /** Belegte Blöcke "welt:x:y:z" -> Möbel-ID. */
    private final Map<String, String> cells = new HashMap<>();
    /** Wer gerade liegt (/lay). */
    private final Set<UUID> lying = new HashSet<>();
    private long nextId = 1;

    public FurnitureService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "furniture.yml");
        this.itemKey = new NamespacedKey(addon, "furniture_item");
        this.partKey = new NamespacedKey(addon, "furniture");
        this.seatKey = new NamespacedKey(addon, "seat");
        load();
    }

    public static void send(Player p, String mini) {
        Msg.send(p, PREFIX + mini);
    }

    private int max() {
        return core.getConfig().getInt("furniture.max", 50);
    }

    private boolean survival(Player p) {
        return ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()));
    }

    public synchronized int countOf(UUID owner) {
        int n = 0;
        for (Placed f : placed.values()) if (f.owner.equals(owner)) n++;
        return n;
    }

    // =================================================================================================================
    // Items
    // =================================================================================================================

    /** Möbel-Item zum Aufstellen (nicht als Block setzbar: Papier mit dem Aussehen des Icons). */
    public ItemStack item(FurnitureCatalog.Def d) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.item("<#F0A050>" + d.name()));
        meta.lore(List.of(Msg.item("<gray>Möbel · " + d.size().label + (d.seat() ? " · Sitzplatz" : "")
                        + (d.width() > 1 ? " · 2 Blöcke breit" : "")),
                Msg.item(""), Msg.item("<yellow>Rechtsklick auf einen Block: aufstellen"),
                Msg.item("<gray>(in deinem Grundstück oder wo du bauen darfst)")));
        meta.setItemModel(d.icon().getKey());
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, d.id());
        item.setItemMeta(meta);
        return item;
    }

    public void give(Player p, FurnitureCatalog.Def d) {
        p.getInventory().addItem(item(d)).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
    }

    // =================================================================================================================
    // Geometrie
    // =================================================================================================================

    /** Lokaler Punkt (x, y, z) des Möbels in Weltkoordinaten (gedreht um yaw). */
    private static Location world(Placed f, double x, double y, double z) {
        double r = Math.toRadians(f.yaw);
        double cos = Math.cos(r), sin = Math.sin(r);
        // Minecraft-Yaw: 0 = Süden (+Z), 90 = Westen (-X)
        double wx = x * cos - z * sin, wz = x * sin + z * cos;
        return f.at.clone().add(wx, y, wz);
    }

    private static List<int[]> cellsOf(Location at, float yaw, int width) {
        List<int[]> out = new ArrayList<>();
        double r = Math.toRadians(yaw);
        for (int i = 0; i < width; i++) {
            double wx = i * Math.cos(r), wz = i * Math.sin(r);
            out.add(new int[]{(int) Math.floor(at.getX() + wx), at.getBlockY(), (int) Math.floor(at.getZ() + wz)});
        }
        return out;
    }

    private static String cellKey(World w, int[] c) {
        return w.getName() + ":" + c[0] + ":" + c[1] + ":" + c[2];
    }

    // =================================================================================================================
    // Aufstellen, drehen, aufheben
    // =================================================================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseItem(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        ItemStack hand = e.getItem();
        if (hand == null || !hand.hasItemMeta()) return;
        String id = hand.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        FurnitureCatalog.Def d = FurnitureCatalog.get(id);
        if (d == null) return;
        String err = place(p, d, e.getClickedBlock().getRelative(e.getBlockFace()));
        if (err != null) {
            send(p, "<red>" + Msg.escape(err));
            p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
    }

    private String place(Player p, FurnitureCatalog.Def d, Block target) {
        if (!survival(p)) return "Möbel gibt es nur in Survival.";
        if (addon.claims().at(target.getLocation()) == null) return "Möbel stellst du in deinem Grundstück auf (oder wo du bauen darfst).";
        if (countOf(p.getUniqueId()) >= max()) return "Du hast schon " + max() + " Möbel aufgestellt. Heb eins auf (Schleichen + Linksklick).";
        // zum Spieler gedreht, in 45°-Schritten (breite Möbel: 90°)
        float step = d.width() > 1 ? 90f : 45f;
        float yaw = Math.round((p.getLocation().getYaw() + 180f) / step) * step;
        yaw = ((yaw % 360) + 360) % 360;
        Location at = target.getLocation().add(0.5, 0, 0.5);
        String err = checkCells(p, at, yaw, d.width(), null);
        if (err != null) return err;
        Placed f;
        synchronized (this) {
            f = new Placed("m" + (nextId++), p.getUniqueId(), d, at, yaw);
            placed.put(f.id, f);
            for (int[] c : cellsOf(at, yaw, d.width())) cells.put(cellKey(at.getWorld(), c), f.id);
            save();
        }
        spawn(f);
        p.playSound(at, Sound.BLOCK_WOOD_PLACE, 1f, 1f);
        return null;
    }

    /** Alle Blöcke des Möbels müssen frei sein und im Bau-Recht des Spielers liegen. */
    private String checkCells(Player p, Location at, float yaw, int width, String self) {
        for (int[] c : cellsOf(at, yaw, width)) {
            Block b = at.getWorld().getBlockAt(c[0], c[1], c[2]);
            if (!b.isPassable() || b.isLiquid()) return "Hier ist kein Platz.";
            if (!addon.claims().canBuild(p, b.getLocation())) return "Hier darfst du nicht bauen.";
            if (addon.claims().at(b.getLocation()) == null) return "Das Möbel ragt aus dem Grundstück heraus.";
            String other = cells.get(cellKey(at.getWorld(), c));
            if (other != null && !other.equals(self)) return "Hier steht schon ein Möbel.";
        }
        return null;
    }

    private void spawn(Placed f) {
        Location base = f.at.clone();
        base.setYaw(f.yaw);
        base.setPitch(0);
        for (FurnitureCatalog.Part part : f.def.parts()) {
            f.at.getWorld().spawn(base, BlockDisplay.class, d -> {
                d.setBlock(part.m().createBlockData());
                d.setTransformation(new Transformation(new Vector3f(part.x(), part.y(), part.z()), new AxisAngle4f(),
                        new Vector3f(part.sx(), part.sy(), part.sz()), new AxisAngle4f()));
                if (part.glow()) d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(true);
                d.getPersistentDataContainer().set(partKey, PersistentDataType.STRING, f.id);
            });
        }
        float height = 0.5f;
        for (FurnitureCatalog.Part part : f.def.parts()) height = Math.max(height, part.y() + part.sy());
        float h = Math.min(height, 1.9f);
        for (int i = 0; i < f.def.width(); i++) {
            Location c = world(f, i, 0, 0);
            f.at.getWorld().spawn(c, Interaction.class, it -> {
                it.setInteractionWidth(0.95f);
                it.setInteractionHeight(h);
                it.setResponsive(true);
                it.setPersistent(true);
                it.getPersistentDataContainer().set(partKey, PersistentDataType.STRING, f.id);
            });
        }
        if (f.def.light() >= 0) {
            Block b = f.at.getBlock().getRelative(0, f.def.light(), 0);
            if (b.getType().isAir()) {
                b.setType(Material.LIGHT, false);
                if (b.getBlockData() instanceof Levelled l) {
                    l.setLevel(13);
                    b.setBlockData(l, false);
                }
            }
        }
    }

    private void despawn(Placed f) {
        World w = f.at.getWorld();
        if (w == null) return;
        for (Entity e : w.getNearbyEntities(f.at, 3, 3, 3)) {
            if (f.id.equals(e.getPersistentDataContainer().get(partKey, PersistentDataType.STRING))) e.remove();
        }
        if (f.def.light() >= 0) {
            Block b = f.at.getBlock().getRelative(0, f.def.light(), 0);
            if (b.getType() == Material.LIGHT) b.setType(Material.AIR, false);
        }
    }

    private Placed byEntity(Entity e) {
        String id = e.getPersistentDataContainer().get(partKey, PersistentDataType.STRING);
        if (id == null) return null;
        synchronized (this) {
            return placed.get(id);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClickFurniture(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Interaction it)) return;
        Placed f = byEntity(it);
        if (f == null) {
            if (it.getPersistentDataContainer().has(partKey, PersistentDataType.STRING)) it.remove();
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (p.isSneaking()) {
            rotate(p, f);
            return;
        }
        if (f.def.seat()) sitOn(p, f, it.getLocation());
    }

    private void rotate(Player p, Placed f) {
        if (!addon.claims().canBuild(p, f.at)) {
            send(p, "<red>Drehen darf nur, wer hier bauen darf.");
            return;
        }
        float step = f.def.width() > 1 ? 90f : 45f;
        float next = (f.yaw + step) % 360;
        if (f.def.width() > 1) {
            String err = checkCells(p, f.at, next, f.def.width(), f.id);
            if (err != null) {
                send(p, "<red>Zum Drehen ist kein Platz.");
                return;
            }
        }
        despawn(f);
        synchronized (this) {
            for (int[] c : cellsOf(f.at, f.yaw, f.def.width())) cells.remove(cellKey(f.at.getWorld(), c));
            f.yaw = next;
            for (int[] c : cellsOf(f.at, f.yaw, f.def.width())) cells.put(cellKey(f.at.getWorld(), c), f.id);
            save();
        }
        spawn(f);
        p.playSound(f.at, Sound.BLOCK_WOOD_HIT, 0.8f, 1.2f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        Placed f = byEntity(e.getAttacked());
        if (f == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.isSneaking()) {
            send(p, "Schleichen + Linksklick hebt das Möbel auf, Schleichen + Rechtsklick dreht es.");
            return;
        }
        if (!addon.claims().canBuild(p, f.at)) {
            send(p, "<red>Aufheben darf nur, wer hier bauen darf.");
            return;
        }
        synchronized (this) {
            // nur einmal zurückgeben, auch wenn zwei im selben Moment zuschlagen
            if (placed.remove(f.id) == null) return;
            for (int[] c : cellsOf(f.at, f.yaw, f.def.width())) cells.remove(cellKey(f.at.getWorld(), c));
            save();
        }
        for (Entity s : seatsOf(f)) s.eject();
        despawn(f);
        // das Möbel geht an den Besitzer zurück (online direkt, sonst in die Abholbox)
        Player owner = Bukkit.getPlayer(f.owner);
        if (owner != null && survival(owner)) give(owner, f.def);
        else core.modes().abholboxIn(f.owner, ModeService.SURVIVAL, item(f.def));
        if (!f.owner.equals(p.getUniqueId())) send(p, f.def.name() + " aufgehoben, es geht an den Besitzer zurück.");
        p.playSound(f.at, Sound.BLOCK_WOOD_BREAK, 1f, 1f);
    }

    // =================================================================================================================
    // Sitzen und Liegen
    // =================================================================================================================

    private List<Entity> seatsOf(Placed f) {
        List<Entity> out = new ArrayList<>();
        for (ArmorStand a : f.at.getWorld().getNearbyEntitiesByType(ArmorStand.class, f.at, 3, 3, 3)) {
            if (f.id.equals(a.getPersistentDataContainer().get(seatKey, PersistentDataType.STRING))) out.add(a);
        }
        return out;
    }

    private void sitOn(Player p, Placed f, Location clicked) {
        if (p.isInsideVehicle()) return;
        float[] best = null;
        double bestD = Double.MAX_VALUE;
        for (float[] s : f.def.seats()) {
            double dd = world(f, s[0], 0, s[1]).distanceSquared(clicked);
            if (dd < bestD) {
                bestD = dd;
                best = s;
            }
        }
        if (best == null) return;
        Location seat = world(f, best[0], f.def.seatY(), best[1]);
        for (Entity s : seatsOf(f)) {
            if (s.getLocation().distanceSquared(seat) < 0.05 && !s.getPassengers().isEmpty()) {
                send(p, "Hier sitzt schon jemand.");
                return;
            }
        }
        // Reiter sitzt 0,6 unter dem Sitzpunkt, Hüfte 0,7 über seinen Füßen: Hüfte genau auf der Sitzfläche
        seat(p, seat.clone().add(0, -0.1, 0), f.yaw, f.id);
    }

    private void seat(Player p, Location l, float yaw, String tag) {
        l.setYaw(yaw);
        l.setPitch(0);
        ArmorStand a = l.getWorld().spawn(l, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setMarker(true);
            s.setGravity(false);
            s.setSmall(true);
            s.setInvulnerable(true);
            s.setPersistent(false);
            s.getPersistentDataContainer().set(seatKey, PersistentDataType.STRING, tag);
        });
        if (!a.addPassenger(p)) a.remove();
    }

    /** /sit: auf den Boden setzen. */
    public void sit(Player p) {
        if (!survival(p)) {
            send(p, "<red>Das geht nur in Survival.");
            return;
        }
        if (p.isInsideVehicle() || lying.contains(p.getUniqueId())) return;
        if (!p.isOnGround() || p.isFlying() || p.isGliding() || p.isSwimming()) {
            send(p, "<red>Hinsetzen geht nur, wenn du auf dem Boden stehst.");
            return;
        }
        seat(p, p.getLocation().add(0, -0.1, 0), p.getLocation().getYaw(), "boden");
        p.sendActionBar(Msg.parse("<gray>Aufstehen: <white>Schleichen"));
    }

    /** /lay: hinlegen (andere sehen dich liegen, zählt beim Überspringen der Nacht mit). */
    public void lay(Player p) {
        if (!survival(p)) {
            send(p, "<red>Das geht nur in Survival.");
            return;
        }
        if (p.isInsideVehicle()) p.leaveVehicle();
        if (lying.contains(p.getUniqueId())) {
            standUp(p);
            return;
        }
        if (!p.isOnGround() || p.isFlying() || p.isGliding() || p.isSwimming()) {
            send(p, "<red>Hinlegen geht nur, wenn du auf dem Boden stehst.");
            return;
        }
        lying.add(p.getUniqueId());
        p.setPose(Pose.SLEEPING, true);
        p.setSleepingIgnored(true);
        p.sendActionBar(Msg.parse("<gray>Aufstehen: <white>Schleichen"));
    }

    private void standUp(Player p) {
        if (!lying.remove(p.getUniqueId())) return;
        p.setPose(Pose.STANDING, false);
        p.setSleepingIgnored(false);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getDismounted() instanceof ArmorStand a)) return;
        String tag = a.getPersistentDataContainer().get(seatKey, PersistentDataType.STRING);
        if (tag == null) return;
        Location stand = a.getLocation();
        float yaw = a.getLocation().getYaw();
        addon.getServer().getScheduler().runTask(addon, () -> {
            a.remove();
            if (e.getEntity() instanceof Player p && p.isOnline() && !"boden".equals(tag)) {
                // vor dem Möbel aufstehen
                Vector front = new Vector(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw))).multiply(0.7);
                Location out = stand.clone().add(front);
                out.setY(Math.floor(stand.getY() + 0.1));
                out.setYaw(p.getLocation().getYaw());
                out.setPitch(p.getLocation().getPitch());
                if (out.getBlock().isPassable() && out.clone().add(0, 1, 0).getBlock().isPassable()) p.teleport(out);
            }
        });
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking() && lying.contains(e.getPlayer().getUniqueId())) standUp(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!lying.contains(e.getPlayer().getUniqueId())) return;
        Location f = e.getFrom(), t = e.getTo();
        if (f.getX() != t.getX() || f.getZ() != t.getZ() || t.getY() > f.getY()) {
            // nur umsehen, nicht laufen
            Location keep = f.clone();
            keep.setYaw(t.getYaw());
            keep.setPitch(t.getPitch());
            e.setTo(keep);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        standUp(e.getPlayer());
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        standUp(e.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        standUp(p);
        if (p.getVehicle() instanceof ArmorStand a && a.getPersistentDataContainer().has(seatKey, PersistentDataType.STRING)) {
            p.leaveVehicle();
            a.remove();
        }
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(lying)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) standUp(p);
        }
        for (World w : Bukkit.getWorlds()) {
            for (ArmorStand a : w.getEntitiesByClass(ArmorStand.class)) {
                if (a.getPersistentDataContainer().has(seatKey, PersistentDataType.STRING)) {
                    a.eject();
                    a.remove();
                }
            }
        }
        save();
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private synchronized void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = y.getLong("next-id", 1);
        ConfigurationSection s = y.getConfigurationSection("moebel");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) continue;
            FurnitureCatalog.Def d = FurnitureCatalog.get(c.getString("typ"));
            World w = Bukkit.getWorld(c.getString("welt", ""));
            if (d == null || w == null) continue;
            try {
                Placed f = new Placed(id, UUID.fromString(c.getString("besitzer", "")), d,
                        new Location(w, c.getDouble("x"), c.getDouble("y"), c.getDouble("z")), (float) c.getDouble("yaw"));
                placed.put(id, f);
                for (int[] cell : cellsOf(f.at, f.yaw, d.width())) cells.put(cellKey(w, cell), id);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-id", nextId);
        for (Placed f : placed.values()) {
            String k = "moebel." + f.id + ".";
            y.set(k + "typ", f.def.id());
            y.set(k + "besitzer", f.owner.toString());
            y.set(k + "welt", f.at.getWorld().getName());
            y.set(k + "x", f.at.getX());
            y.set(k + "y", f.at.getY());
            y.set(k + "z", f.at.getZ());
            y.set(k + "yaw", f.yaw);
        }
        try {
            addon.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            addon.getLogger().warning("furniture.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}

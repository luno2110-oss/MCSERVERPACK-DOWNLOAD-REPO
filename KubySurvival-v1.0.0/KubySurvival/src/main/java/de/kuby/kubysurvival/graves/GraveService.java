package de.kuby.kubysurvival.graves;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gräber in Survival: Stirbt ein Spieler, entsteht an der Stelle ein Grabstein (Anzeigen, kein Block) mit allen
 * Sachen und den XP. graves.minutes (30, mit Farmpass graves.farmpass-minutes = 60) kommt nur der Besitzer heran,
 * ein Rechtsklick legt alles, was passt, zurück ins Inventar. Danach zerfällt das Grab und alles fällt heraus.
 * Tod im Void: Sachen in die Abholbox. Gespeichert in graves.yml.
 */
public final class GraveService implements Listener {

    public static final class Grave {
        public final String id;
        public final UUID owner;
        public final String ownerName;
        public final Location at;
        public final long created;
        public final long expires;
        public int xp;
        public final List<ItemStack> items;

        Grave(String id, UUID owner, String ownerName, Location at, long created, long expires, int xp, List<ItemStack> items) {
            this.id = id;
            this.owner = owner;
            this.ownerName = ownerName;
            this.at = at;
            this.created = created;
            this.expires = expires;
            this.xp = xp;
            this.items = items;
        }

        public long left() {
            return Math.max(0, expires - System.currentTimeMillis());
        }

        public int count() {
            int n = 0;
            for (ItemStack i : items) if (i != null) n += i.getAmount();
            return n;
        }
    }

    private static final String PREFIX = "<#A8ADB8><b>GRAB</b> <dark_gray>▶ <gray>";

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File file;
    private final NamespacedKey key;
    private final Map<String, Grave> graves = new LinkedHashMap<>();
    private long nextId = 1;

    public GraveService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "graves.yml");
        this.key = new NamespacedKey(addon, "grave");
        load();
        addon.getServer().getScheduler().runTaskTimer(addon, this::tick, 20L, 20L);
    }

    public static void send(Player p, String mini) {
        Msg.send(p, PREFIX + mini);
    }

    private long minutes(UUID owner) {
        boolean pass = addon.farmpass() != null && addon.farmpass().active(owner);
        return core.getConfig().getLong(pass ? "graves.farmpass-minutes" : "graves.minutes", pass ? 60 : 30);
    }

    private int xpPercent() {
        return Math.max(0, Math.min(100, core.getConfig().getInt("graves.xp-percent", 100)));
    }

    public synchronized List<Grave> of(UUID owner) {
        List<Grave> out = new ArrayList<>();
        for (Grave g : graves.values()) if (g.owner.equals(owner)) out.add(g);
        out.sort((a, b) -> Long.compare(b.created, a.created));
        return out;
    }

    public static String time(long millis) {
        long s = (millis + 999) / 1000;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    public static String worldName(World w) {
        if (w == null) return "?";
        return switch (w.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> w.getName().toLowerCase().contains("farm") ? "Farmwelt" : "Oberwelt";
        };
    }

    // =================================================================================================================
    // Tod
    // =================================================================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (e.getKeepInventory()) return;
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) return;
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack i : e.getDrops()) if (i != null && !i.getType().isAir()) items.add(i.clone());
        int xp = (int) Math.min(Integer.MAX_VALUE, (long) p.calculateTotalExperiencePoints() * xpPercent() / 100);
        if (items.isEmpty() && xp <= 0) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        Location spot = spot(p.getLocation());
        if (spot == null) {
            // Void: nichts kann dort liegen bleiben, alles in die Abholbox
            for (ItemStack i : items) core.abholbox().add(p.getUniqueId(), i);
            int give = xp;
            addon.getServer().getScheduler().runTaskLater(addon, () -> {
                if (p.isOnline()) p.giveExp(give);
            }, 40L);
            send(p, "Du bist ins Leere gefallen. Deine Sachen liegen in der <white>/abholbox</white>, deine XP bekommst du zurück.");
            return;
        }
        long now = System.currentTimeMillis();
        long mins = minutes(p.getUniqueId());
        Grave g;
        synchronized (this) {
            g = new Grave("g" + (nextId++), p.getUniqueId(), p.getName(), spot, now, now + mins * 60_000L, xp, items);
            graves.put(g.id, g);
            save();
        }
        spawn(g);
        String where = worldName(spot.getWorld()) + " " + spot.getBlockX() + " / " + spot.getBlockY() + " / " + spot.getBlockZ();
        addon.getServer().getScheduler().runTaskLater(addon, () -> {
            if (!p.isOnline()) return;
            send(p, "Dein Grab steht in <white>" + where + "</white>. " + mins + " Minuten kommst nur du dran, ein Rechtsklick holt "
                    + "alles zurück. <click:run_command:'/grab'><#A8ADB8>[Deine Gräber]</click>");
        }, 20L);
    }

    /** Stelle für den Grabstein: dort, wo der Spieler starb, über Flüssigkeiten hinaus; null im Void. */
    private Location spot(Location death) {
        World w = death.getWorld();
        if (w == null) return null;
        int x = death.getBlockX(), z = death.getBlockZ();
        int y = death.getBlockY();
        if (y < w.getMinHeight()) {
            int top = w.getHighestBlockYAt(x, z);
            if (top < w.getMinHeight() || w.getBlockAt(x, top, z).getType().isAir()) return null;
            y = top + 1;
        }
        y = Math.min(y, w.getMaxHeight() - 2);
        for (int i = 0; i < 24 && y < w.getMaxHeight() - 2; i++) {
            Block b = w.getBlockAt(x, y, z);
            if (b.isPassable() && !b.isLiquid()) break;
            y++;
        }
        Location at = new Location(w, x + 0.5, y, z + 0.5);
        at.setYaw(Math.round(death.getYaw() / 90f) * 90f);
        return at;
    }

    // =================================================================================================================
    // Grabstein (Anzeigen)
    // =================================================================================================================

    private void spawn(Grave g) {
        Location at = g.at.clone();
        // Sockel, Grabstein, Kappe und Kreuz (Ursprung = Mitte unten, gedreht mit der Blickrichtung beim Tod)
        part(g, at, Material.MOSSY_COBBLESTONE, -0.42f, 0f, -0.30f, 0.84f, 0.14f, 0.60f);
        part(g, at, Material.POLISHED_ANDESITE, -0.30f, 0.14f, 0.02f, 0.60f, 0.70f, 0.16f);
        part(g, at, Material.POLISHED_ANDESITE, -0.22f, 0.84f, 0.02f, 0.44f, 0.08f, 0.16f);
        part(g, at, Material.POLISHED_DEEPSLATE, -0.035f, 0.36f, -0.005f, 0.07f, 0.36f, 0.03f);
        part(g, at, Material.POLISHED_DEEPSLATE, -0.11f, 0.56f, -0.005f, 0.22f, 0.07f, 0.03f);
        Location label = at.clone().add(0, 1.15, 0);
        label.setYaw(0);
        at.getWorld().spawn(label, TextDisplay.class, d -> {
            d.text(Msg.parse(text(g)));
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setPersistent(true);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.8f, 0.8f, 0.8f), new AxisAngle4f()));
            d.getPersistentDataContainer().set(key, PersistentDataType.STRING, g.id);
        });
        at.getWorld().spawn(at.clone(), Interaction.class, i -> {
            i.setInteractionWidth(0.9f);
            i.setInteractionHeight(1.0f);
            i.setResponsive(true);
            i.setPersistent(true);
            i.getPersistentDataContainer().set(key, PersistentDataType.STRING, g.id);
        });
    }

    private void part(Grave g, Location at, Material m, float x, float y, float z, float sx, float sy, float sz) {
        at.getWorld().spawn(at.clone(), BlockDisplay.class, d -> {
            d.setBlock(m.createBlockData());
            d.setTransformation(new Transformation(new Vector3f(x, y, z), new AxisAngle4f(), new Vector3f(sx, sy, sz), new AxisAngle4f()));
            d.setPersistent(true);
            d.setBrightness(null);
            d.getPersistentDataContainer().set(key, PersistentDataType.STRING, g.id);
        });
    }

    private static String text(Grave g) {
        return "<#D2D2DA>✝ <b>" + Msg.escape(g.ownerName) + "</b>\n<gray>noch <white>" + time(g.left()) + "</white> · "
                + g.count() + " Items";
    }

    private void despawn(Grave g) {
        World w = g.at.getWorld();
        if (w == null) return;
        w.getChunkAt(g.at);
        for (Entity e : w.getNearbyEntities(g.at, 2, 3, 2)) {
            if (g.id.equals(e.getPersistentDataContainer().get(key, PersistentDataType.STRING))) e.remove();
        }
    }

    // =================================================================================================================
    // Abholen, Zerfallen
    // =================================================================================================================

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        String id = e.getRightClicked().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Grave g;
        synchronized (this) {
            g = graves.get(id);
        }
        if (g == null) {
            e.getRightClicked().remove();
            return;
        }
        if (!g.owner.equals(p.getUniqueId())) {
            send(p, "Das Grab von <white>" + Msg.escape(g.ownerName) + "</white>. Noch <white>" + time(g.left())
                    + "</white> kommt nur " + Msg.escape(g.ownerName) + " heran, danach zerfällt es.");
            p.playSound(p, Sound.BLOCK_STONE_HIT, 0.6f, 0.8f);
            return;
        }
        collect(p, g);
    }

    /** Legt alles, was passt, zurück (Rüstung und Zweithand an ihren Platz) und gibt die XP. */
    private void collect(Player p, Grave g) {
        PlayerInventory inv = p.getInventory();
        List<ItemStack> rest = new ArrayList<>();
        int taken = 0;
        synchronized (this) {
            if (!graves.containsKey(g.id)) return;
            for (ItemStack i : g.items) {
                if (i == null) continue;
                ItemStack item = i.clone();
                EquipmentSlot slot = item.getType().getEquipmentSlot();
                if (slot == EquipmentSlot.HEAD && isEmpty(inv.getHelmet())) inv.setHelmet(item);
                else if (slot == EquipmentSlot.CHEST && isEmpty(inv.getChestplate())) inv.setChestplate(item);
                else if (slot == EquipmentSlot.LEGS && isEmpty(inv.getLeggings())) inv.setLeggings(item);
                else if (slot == EquipmentSlot.FEET && isEmpty(inv.getBoots())) inv.setBoots(item);
                else {
                    Map<Integer, ItemStack> left = inv.addItem(item);
                    if (!left.isEmpty()) {
                        rest.addAll(left.values());
                        taken += item.getAmount() - left.values().stream().mapToInt(ItemStack::getAmount).sum();
                        continue;
                    }
                }
                taken += item.getAmount();
            }
            g.items.clear();
            g.items.addAll(rest);
            if (g.xp > 0) {
                p.giveExp(g.xp);
                g.xp = 0;
            }
            if (g.items.isEmpty()) graves.remove(g.id);
            save();
        }
        p.playSound(p, Sound.ITEM_ARMOR_EQUIP_GENERIC, 1f, 1f);
        p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.1f);
        if (g.items.isEmpty()) {
            despawn(g);
            g.at.getWorld().spawnParticle(Particle.CLOUD, g.at.clone().add(0, 0.6, 0), 12, 0.3, 0.3, 0.3, 0.02);
            send(p, "Alles zurück in deinem Inventar (" + taken + " Items). Das Grab ist verschwunden.");
        } else {
            refreshLabel(g);
            send(p, taken + " Items zurück. <white>" + g.count() + "</white> passen nicht mehr ins Inventar und bleiben im Grab.");
        }
    }

    private static boolean isEmpty(ItemStack i) {
        return i == null || i.getType().isAir();
    }

    private void refreshLabel(Grave g) {
        World w = g.at.getWorld();
        if (w == null || !w.isChunkLoaded(g.at.getBlockX() >> 4, g.at.getBlockZ() >> 4)) return;
        for (TextDisplay d : w.getNearbyEntitiesByType(TextDisplay.class, g.at, 1, 2.5, 1)) {
            if (g.id.equals(d.getPersistentDataContainer().get(key, PersistentDataType.STRING))) d.text(Msg.parse(text(g)));
        }
    }

    private void tick() {
        List<Grave> due = new ArrayList<>();
        synchronized (this) {
            for (Grave g : graves.values()) {
                long left = g.left();
                if (left <= 0) due.add(g);
                else if (left > 299_000 && left <= 300_000) {
                    Player o = Bukkit.getPlayer(g.owner);
                    if (o != null) send(o, "Dein Grab in " + worldName(g.at.getWorld()) + " zerfällt in 5 Minuten. <click:run_command:'/grab'><#A8ADB8>[Deine Gräber]</click>");
                }
            }
        }
        for (Grave g : graves.values()) refreshLabel(g);
        for (Grave g : due) decay(g);
    }

    /** Zeit abgelaufen: Grab zerfällt, alles fällt heraus und darf von allen aufgehoben werden. */
    private void decay(Grave g) {
        synchronized (this) {
            if (graves.remove(g.id) == null) return;
            save();
        }
        World w = g.at.getWorld();
        if (w == null) {
            toAbholbox(g);
            return;
        }
        despawn(g);
        Location drop = g.at.clone().add(0, 0.5, 0);
        for (ItemStack i : g.items) if (i != null) w.dropItemNaturally(drop, i);
        if (g.xp > 0) w.spawn(drop, ExperienceOrb.class, o -> o.setExperience(g.xp));
        w.spawnParticle(Particle.BLOCK, drop, 30, 0.3, 0.3, 0.3, Material.POLISHED_ANDESITE.createBlockData());
        w.playSound(drop, Sound.BLOCK_STONE_BREAK, 1f, 0.8f);
        Player o = Bukkit.getPlayer(g.owner);
        if (o != null) send(o, "Dein Grab in " + worldName(w) + " ist zerfallen. Die Sachen liegen jetzt dort, jeder kann sie aufheben.");
    }

    private void toAbholbox(Grave g) {
        for (ItemStack i : g.items) if (i != null) core.modes().abholboxIn(g.owner, ModeService.SURVIVAL, i);
        g.items.clear();
    }

    /** Vor dem Neuerstellen einer Welt (Farmwelt): alle Gräber dort in die Abholbox der Besitzer. */
    public void evacuate(String world) {
        List<Grave> list = new ArrayList<>();
        synchronized (this) {
            for (Iterator<Grave> it = graves.values().iterator(); it.hasNext(); ) {
                Grave g = it.next();
                if (g.at.getWorld() != null && g.at.getWorld().getName().equals(world)) {
                    list.add(g);
                    it.remove();
                }
            }
            save();
        }
        for (Grave g : list) {
            despawn(g);
            toAbholbox(g);
            Player o = Bukkit.getPlayer(g.owner);
            if (o != null) send(o, "Die Farmwelt wird neu erstellt. Die Sachen aus deinem Grab liegen in der <white>/abholbox</white>.");
        }
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private synchronized void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = y.getLong("next-id", 1);
        ConfigurationSection s = y.getConfigurationSection("graeber");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) continue;
            try {
                World w = Bukkit.getWorld(c.getString("welt", ""));
                Location at = new Location(w, c.getDouble("x"), c.getDouble("y"), c.getDouble("z"), (float) c.getDouble("yaw"), 0);
                List<ItemStack> items = new ArrayList<>();
                for (String b : c.getStringList("items")) items.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(b)));
                graves.put(id, new Grave(id, UUID.fromString(c.getString("besitzer", "")), c.getString("name", "?"), at,
                        c.getLong("erstellt"), c.getLong("ablauf"), c.getInt("xp"), items));
            } catch (RuntimeException ex) {
                addon.getLogger().warning("graves.yml: Grab " + id + " übersprungen (" + ex.getMessage() + ")");
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-id", nextId);
        for (Grave g : graves.values()) {
            String k = "graeber." + g.id + ".";
            y.set(k + "besitzer", g.owner.toString());
            y.set(k + "name", g.ownerName);
            y.set(k + "welt", g.at.getWorld() == null ? "" : g.at.getWorld().getName());
            y.set(k + "x", g.at.getX());
            y.set(k + "y", g.at.getY());
            y.set(k + "z", g.at.getZ());
            y.set(k + "yaw", g.at.getYaw());
            y.set(k + "erstellt", g.created);
            y.set(k + "ablauf", g.expires);
            y.set(k + "xp", g.xp);
            List<String> items = new ArrayList<>();
            for (ItemStack i : g.items) if (i != null) items.add(Base64.getEncoder().encodeToString(i.serializeAsBytes()));
            y.set(k + "items", items);
        }
        try {
            addon.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            addon.getLogger().warning("graves.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}

package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Modus-NPCs in der Lobby: Figuren mit Spieler-Skin, die einen Modus vertreten (SMP, Survival, Skyblock). Klick bringt
 * in den Modus, darüber steht ein Schild mit Name und Spielern im Modus. Schaut man eine Figur an, wird sie sanft etwas
 * größer (und wieder kleiner, wenn niemand mehr hinsieht).
 *
 * Frei anpassbar mit /kp modusnpc: setzen &lt;modus&gt; [skin] [Name], skin, name, item (Gegenstand aus deiner Hand),
 * drehen, entfernen, liste. Gespeichert in der config unter hub.modus-npcs; die Figuren selbst werden nicht gespeichert,
 * sondern beim Laden des Chunks neu gesetzt.
 */
public final class ModeNpcService implements Listener {

    private static final double HOVER_SCALE = 1.12, RANGE = 12;

    private static final class Npc {
        final String id;
        String mode, skin, name;
        Location at;
        Material item;
        Mannequin body;
        TextDisplay sign;
        double scale = 1.0;
        boolean aimed;

        Npc(String id) {
            this.id = id;
        }
    }

    private final KubyPlugins plugin;
    private final NamespacedKey tag;
    private final Map<String, Npc> npcs = new LinkedHashMap<>();

    public ModeNpcService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.tag = new NamespacedKey(plugin, "mode_npc");
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::hover, 40L, 2L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 40L, 20L);
    }

    // ---------- Speicherung ----------

    private void load() {
        npcs.clear();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("hub.modus-npcs");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) continue;
            World w = Bukkit.getWorld(c.getString("welt", ""));
            Npc n = new Npc(id);
            n.mode = c.getString("modus", ModeService.SMP);
            n.skin = c.getString("skin", "");
            n.name = c.getString("name", null);
            n.at = w == null ? null : new Location(w, c.getDouble("x"), c.getDouble("y"), c.getDouble("z"), (float) c.getDouble("yaw"), 0);
            Material m = Material.matchMaterial(c.getString("item", ""));
            n.item = m == null || m.isAir() ? null : m;
            if (n.at == null) {
                // Welt noch nicht geladen: später erneut versuchen (refresh)
                n.at = null;
                pendingWorld.put(id, c.getString("welt", ""));
            }
            npcs.put(id, n);
        }
    }

    private final Map<String, String> pendingWorld = new HashMap<>();

    private void save(Npc n) {
        String b = "hub.modus-npcs." + n.id + ".";
        plugin.getConfig().set(b + "modus", n.mode);
        plugin.getConfig().set(b + "skin", n.skin);
        plugin.getConfig().set(b + "name", n.name);
        plugin.getConfig().set(b + "item", n.item == null ? "" : n.item.name());
        if (n.at != null) {
            plugin.getConfig().set(b + "welt", n.at.getWorld().getName());
            plugin.getConfig().set(b + "x", n.at.getX());
            plugin.getConfig().set(b + "y", n.at.getY());
            plugin.getConfig().set(b + "z", n.at.getZ());
            plugin.getConfig().set(b + "yaw", (double) n.at.getYaw());
        }
        plugin.saveConfig();
    }

    // ---------- Figuren ----------

    private static String label(String mode) {
        return switch (mode) {
            case ModeService.SMP -> "SMP";
            case ModeService.SURVIVAL -> "Survival";
            case ModeService.SKYBLOCK -> "Skyblock";
            default -> mode;
        };
    }

    private static String color(String mode) {
        return switch (mode) {
            case ModeService.SMP -> "#FF6A5A";
            case ModeService.SURVIVAL -> "#6CE086";
            case ModeService.SKYBLOCK -> "#6FE3D2";
            default -> "#F2C84A";
        };
    }

    private Component signText(Npc n) {
        int count = 0;
        for (Player o : Bukkit.getOnlinePlayers()) if (n.mode.equals(plugin.modes().modeOf(o.getUniqueId()))) count++;
        String title = n.name != null && !n.name.isBlank() ? n.name : "<b>" + label(n.mode) + "</b>";
        return Msg.parse("<" + color(n.mode) + ">" + title + "\n<gray>" + count + (count == 1 ? " Spieler" : " Spieler")
                + "\n<yellow>▶ Klick zum Betreten");
    }

    /** Setzt fehlende Figuren und Schilder (nach dem Entladen des Chunks sind sie weg) und zählt die Spieler. */
    private void refresh() {
        for (Npc n : npcs.values()) {
            if (n.at == null) {
                World w = Bukkit.getWorld(pendingWorld.getOrDefault(n.id, ""));
                if (w == null) continue;
                ConfigurationSection c = plugin.getConfig().getConfigurationSection("hub.modus-npcs." + n.id);
                if (c == null) continue;
                n.at = new Location(w, c.getDouble("x"), c.getDouble("y"), c.getDouble("z"), (float) c.getDouble("yaw"), 0);
                pendingWorld.remove(n.id);
            }
            if (!n.at.getWorld().isChunkLoaded(n.at.getBlockX() >> 4, n.at.getBlockZ() >> 4)) continue;
            if (n.body == null || !n.body.isValid()) spawn(n);
            if (n.sign != null && n.sign.isValid()) n.sign.text(signText(n));
        }
    }

    private void spawn(Npc n) {
        despawn(n);
        World w = n.at.getWorld();
        for (Entity e : w.getNearbyEntities(n.at, 1.5, 3, 1.5)) {
            String v = e.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
            if (n.id.equals(v)) e.remove();
        }
        n.scale = 1.0;
        n.body = w.spawn(n.at, Mannequin.class, m -> {
            if (n.skin != null && !n.skin.isBlank()) m.setProfile(ResolvableProfile.resolvableProfile().name(n.skin).build());
            m.setImmovable(true);
            m.setDescription(null);
            m.setAI(false);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setPersistent(false);
            m.setCustomNameVisible(false);
            if (n.item != null) m.getEquipment().setItemInMainHand(new ItemStack(n.item));
            m.getPersistentDataContainer().set(tag, PersistentDataType.STRING, n.id);
        });
        n.sign = w.spawn(n.at.clone().add(0, 2.35, 0), TextDisplay.class, d -> {
            d.text(signText(n));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setShadowed(true);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(120, 10, 14, 24));
            d.getPersistentDataContainer().set(tag, PersistentDataType.STRING, n.id);
        });
    }

    private void despawn(Npc n) {
        if (n.body != null && n.body.isValid()) n.body.remove();
        if (n.sign != null && n.sign.isValid()) n.sign.remove();
        n.body = null;
        n.sign = null;
    }

    public void shutdown() {
        for (Npc n : npcs.values()) despawn(n);
    }

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        if (!npcs.isEmpty()) Bukkit.getScheduler().runTask(plugin, this::refresh);
    }

    // ---------- Anvisieren: sanft größer ----------

    private void hover() {
        if (npcs.isEmpty()) return;
        Map<Npc, Boolean> aimed = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location eye = p.getEyeLocation();
            RayTraceResult r = null;
            boolean near = false;
            for (Npc n : npcs.values()) {
                if (n.body != null && n.body.isValid() && n.body.getWorld() == eye.getWorld()
                        && n.body.getLocation().distanceSquared(eye) <= RANGE * RANGE) {
                    near = true;
                    break;
                }
            }
            if (!near) continue;
            Vector dir = eye.getDirection();
            r = eye.getWorld().rayTraceEntities(eye, dir, RANGE, 0.25,
                    e -> e instanceof Mannequin && e.getPersistentDataContainer().has(tag, PersistentDataType.STRING));
            if (r == null || r.getHitEntity() == null) continue;
            // Wand dazwischen? Dann zählt es nicht
            RayTraceResult block = eye.getWorld().rayTraceBlocks(eye, dir, eye.distance(r.getHitEntity().getLocation()) + 0.5);
            if (block != null && block.getHitBlock() != null
                    && block.getHitPosition().distanceSquared(eye.toVector()) < r.getHitPosition().distanceSquared(eye.toVector())) continue;
            String id = r.getHitEntity().getPersistentDataContainer().get(tag, PersistentDataType.STRING);
            Npc n = npcs.get(id);
            if (n != null) aimed.put(n, true);
        }
        for (Npc n : npcs.values()) {
            if (n.body == null || !n.body.isValid()) continue;
            boolean now = aimed.containsKey(n);
            double target = now ? HOVER_SCALE : 1.0;
            if (Math.abs(n.scale - target) < 0.002) {
                n.aimed = now;
                continue;
            }
            n.scale += (target - n.scale) * 0.35;
            if (Math.abs(n.scale - target) < 0.004) n.scale = target;
            AttributeInstance a = n.body.getAttribute(Attribute.SCALE);
            if (a != null) a.setBaseValue(n.scale);
            if (n.sign != null && n.sign.isValid()) n.sign.teleport(n.at.clone().add(0, 2.35 * n.scale, 0));
            if (now && !n.aimed) {
                n.body.getWorld().playSound(n.body.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.6f);
            }
            n.aimed = now;
        }
    }

    // ---------- Klick ----------

    private Npc npcOf(Entity e) {
        if (!(e instanceof Mannequin)) return null;
        String id = e.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
        return id == null ? null : npcs.get(id);
    }

    private void enter(Player p, Npc n) {
        if (n.mode.equals(plugin.modes().modeOf(p.getUniqueId()))) {
            Msg.send(p, "<gray>Du bist schon in <white>" + label(n.mode) + "<gray>.");
            return;
        }
        String err = plugin.modes().switchTo(p, n.mode);
        if (err != null) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>" + err);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        Npc n = npcOf(e.getRightClicked());
        if (n == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        enter(e.getPlayer(), n);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent e) {
        Npc n = npcOf(e.getEntity());
        if (n == null) return;
        e.setCancelled(true);
        if (e.getDamager() instanceof Player p) enter(p, n);
    }

    // ---------- /kp modusnpc ----------

    private Npc nearest(Player p) {
        Npc best = null;
        double bd = 36;
        for (Npc n : npcs.values()) {
            if (n.at == null || n.at.getWorld() != p.getWorld()) continue;
            double d = n.at.distanceSquared(p.getLocation());
            if (d < bd) {
                bd = d;
                best = n;
            }
        }
        return best;
    }

    public void command(Player p, String[] args) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "setzen", "add" -> {
                String mode = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
                if (!List.of(ModeService.SMP, ModeService.SURVIVAL, ModeService.SKYBLOCK).contains(mode)) {
                    Msg.send(p, "<red>Nutzung: /kp modusnpc setzen <smp|survival|skyblock> [skin] [Name]");
                    return;
                }
                String id = mode + "_" + (System.currentTimeMillis() % 100000);
                Npc n = new Npc(id);
                n.mode = mode;
                n.skin = args.length > 3 ? args[3] : p.getName();
                n.name = args.length > 4 ? String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length)) : null;
                Location at = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
                at.setYaw(Math.round(p.getLocation().getYaw() / 45f) * 45f + 180f);
                at.setPitch(0);
                n.at = at;
                npcs.put(id, n);
                save(n);
                spawn(n);
                Msg.send(p, "<green>Modus-NPC für <white>" + label(mode) + " <green>gesetzt (Skin <white>" + Msg.escape(n.skin)
                        + "<green>). Anpassen: /kp modusnpc skin|name|item|drehen, entfernen: /kp modusnpc entfernen");
            }
            case "skin", "name", "item", "drehen", "entfernen", "remove" -> {
                Npc n = nearest(p);
                if (n == null) {
                    Msg.send(p, "<red>Kein Modus-NPC in der Nähe (6 Blöcke).");
                    return;
                }
                switch (sub) {
                    case "skin" -> {
                        if (args.length < 3) {
                            Msg.send(p, "<red>Nutzung: /kp modusnpc skin <Spielername>");
                            return;
                        }
                        n.skin = args[2];
                    }
                    case "name" -> n.name = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : null;
                    case "item" -> {
                        Material m = p.getInventory().getItemInMainHand().getType();
                        n.item = m.isAir() ? null : m;
                    }
                    case "drehen" -> n.at.setYaw(Math.round(p.getLocation().getYaw() / 45f) * 45f + 180f);
                    default -> {
                        despawn(n);
                        npcs.remove(n.id);
                        plugin.getConfig().set("hub.modus-npcs." + n.id, null);
                        plugin.saveConfig();
                        Msg.send(p, "<green>Modus-NPC entfernt.");
                        return;
                    }
                }
                save(n);
                spawn(n);
                Msg.send(p, "<green>Modus-NPC angepasst.");
            }
            case "liste", "list" -> {
                if (npcs.isEmpty()) Msg.send(p, "<gray>Noch keine Modus-NPCs. /kp modusnpc setzen <modus>");
                for (Npc n : npcs.values()) {
                    Msg.send(p, "<white>" + n.id + " <gray>· " + label(n.mode) + " · Skin " + Msg.escape(n.skin)
                            + (n.at == null ? " · Welt nicht geladen" : " · " + n.at.getWorld().getName() + " "
                            + n.at.getBlockX() + " " + n.at.getBlockY() + " " + n.at.getBlockZ()));
                }
            }
            default -> {
                Msg.send(p, "<gold><b>Modus-NPCs</b> <gray>(Lobby, werden beim Anschauen größer)");
                Msg.send(p, "<white>/kp modusnpc setzen <smp|survival|skyblock> [skin] [Name] <gray>an deiner Position");
                Msg.send(p, "<white>/kp modusnpc skin <Spieler> <gray>· <white>name [Text] <gray>· <white>item <gray>(aus der Hand) · <white>drehen");
                Msg.send(p, "<white>/kp modusnpc entfernen <gray>· <white>/kp modusnpc liste");
            }
        }
    }

    /** Für den Selbsttest: alle Modus-NPCs. */
    public List<String> ids() {
        return new ArrayList<>(npcs.keySet());
    }
}

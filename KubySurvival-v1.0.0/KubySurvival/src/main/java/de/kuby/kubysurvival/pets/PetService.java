package de.kuby.kubysurvival.pets;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerLeashEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

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
import java.util.regex.Pattern;

/**
 * Haustiere in Survival: laufen hinterher, manche sind reitbar (Pferde-Arten, Kamel). Sie sind unverwundbar,
 * werden nicht gespeichert (verschwinden beim Ausloggen oder Moduswechsel) und gehören nur ihrem Besitzer.
 * Quellen: Kauf im Menü, Farmpass, Kisten/Ränge (/pet give oder Permission kuby.pet.<id>).
 */
public final class PetService implements Listener {

    private static final Pattern NAME = Pattern.compile("[\\p{L}0-9 _\\-]{1,16}");

    /** Ein Haustier aus pets.yml. */
    public record Kind(String id, String name, EntityType type, boolean baby, boolean rideable, long price, String source) {}

    static final class Data {
        final Set<String> owned = new HashSet<>();
        final Map<String, String> names = new HashMap<>();
        final Set<String> favorites = new HashSet<>();
    }

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File defFile;
    private final File dataFile;
    private final NamespacedKey tag;
    private final Map<String, Kind> kinds = new LinkedHashMap<>();
    private final Map<UUID, Data> players = new HashMap<>();
    /** Gerufene Haustiere: Besitzer → Tier. */
    private final Map<UUID, LivingEntity> active = new HashMap<>();
    private final Map<UUID, String> activeKind = new HashMap<>();

    public PetService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.defFile = new File(addon.getDataFolder(), "pets.yml");
        this.dataFile = new File(addon.getDataFolder(), "petdata.yml");
        this.tag = new NamespacedKey(addon, "pet_owner");
        if (!defFile.exists()) addon.saveResource("pets.yml", false);
        loadDefs();
        loadData();
        addon.getServer().getScheduler().runTaskTimer(addon, this::follow, 20L, 10L);
        // Übrig gebliebene Haustiere (Absturz) beim Laden von Chunks entfernen.
        addon.getServer().getScheduler().runTaskLater(addon, () -> {
            for (var w : addon.getServer().getWorlds()) {
                for (Entity e : w.getEntities()) if (isPet(e) && !active.containsValue(e)) e.remove();
            }
        }, 40L);
    }

    // ---------- Katalog ----------

    public synchronized void loadDefs() {
        kinds.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(defFile);
        ConfigurationSection s = yml.getConfigurationSection("pets");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            EntityType t;
            try {
                t = EntityType.valueOf(s.getString(id + ".tier", "").toUpperCase());
            } catch (IllegalArgumentException e) {
                addon.getLogger().warning("pets.yml: Tier von " + id + " unbekannt.");
                continue;
            }
            if (!t.isSpawnable() || !LivingEntity.class.isAssignableFrom(t.getEntityClass())) continue;
            kinds.put(id, new Kind(id, s.getString(id + ".name", id), t, s.getBoolean(id + ".baby"), s.getBoolean(id + ".reitbar"),
                    s.getLong(id + ".preis"), s.getString(id + ".quelle", "")));
        }
    }

    public synchronized List<Kind> kinds() {
        return new ArrayList<>(kinds.values());
    }

    public synchronized Kind kind(String id) {
        return id == null ? null : kinds.get(id.toLowerCase());
    }

    /** Sucht ein Haustier nach ID, Standardname oder eigenem Namen des Spielers. */
    public synchronized Kind find(UUID owner, String name) {
        Kind k = kind(name);
        if (k != null) return k;
        Data d = data(owner);
        for (Kind c : kinds.values()) {
            if (c.name().equalsIgnoreCase(name) || name.equalsIgnoreCase(d.names.get(c.id()))) return c;
        }
        return null;
    }

    // ---------- Besitz ----------

    synchronized Data data(UUID id) {
        return players.computeIfAbsent(id, k -> new Data());
    }

    public boolean owns(Player p, Kind k) {
        UUID id = p.getUniqueId();
        synchronized (this) {
            if (data(id).owned.contains(k.id())) return true;
        }
        if (p.hasPermission("kuby.pet." + k.id())) return true;
        return addon.farmpass() != null && addon.farmpass().allowedPets(id).contains(k.id());
    }

    public synchronized void grant(UUID id, String kind) {
        data(id).owned.add(kind);
        save();
    }

    /** Kaufen im Menü. Null bei Erfolg, sonst eine Meldung. */
    public String buy(Player p, Kind k) {
        if (k.price() <= 0) return "Dieses Haustier kann man nicht kaufen (" + k.source() + ").";
        if (owns(p, k)) return "Das hast du schon.";
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) return "Haustiere kaufst du in Survival.";
        if (!core.economy().withdraw(p.getUniqueId(), k.price())) return "Dafür brauchst du " + Msg.coins(k.price()) + " Coins.";
        grant(p.getUniqueId(), k.id());
        return null;
    }

    public synchronized String displayName(UUID owner, Kind k) {
        String own = data(owner).names.get(k.id());
        return own != null ? own : k.name();
    }

    public synchronized String rename(UUID owner, Kind k, String name) {
        if (name == null || !NAME.matcher(name).matches()) return "Der Name darf 1 bis 16 Buchstaben, Zahlen, Leerzeichen, _ oder - haben.";
        data(owner).names.put(k.id(), name);
        save();
        LivingEntity e = active.get(owner);
        if (e != null && k.id().equals(activeKind.get(owner))) e.customName(Msg.parse("<gold>" + Msg.escape(name)));
        return null;
    }

    public synchronized boolean toggleFavorite(UUID owner, Kind k) {
        Data d = data(owner);
        boolean on = !d.favorites.remove(k.id());
        if (on) d.favorites.add(k.id());
        save();
        return on;
    }

    public synchronized boolean favorite(UUID owner, Kind k) {
        return data(owner).favorites.contains(k.id());
    }

    public synchronized String activeKind(UUID owner) {
        return activeKind.get(owner);
    }

    // ---------- Rufen ----------

    public boolean isPet(Entity e) {
        return e.getPersistentDataContainer().has(tag, PersistentDataType.STRING);
    }

    private UUID ownerOf(Entity e) {
        String s = e.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Ruft ein Haustier. Null bei Erfolg, sonst eine Meldung. */
    public String summon(Player p, Kind k) {
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) return "Haustiere gibt es nur in Survival.";
        if (!owns(p, k)) return "Dieses Haustier hast du nicht.";
        dismiss(p.getUniqueId());
        Location l = p.getLocation();
        String name = displayName(p.getUniqueId(), k);
        @SuppressWarnings("unchecked")
        Class<? extends Entity> cls = (Class<? extends Entity>) k.type().getEntityClass();
        if (cls == null) return "Dieses Haustier gibt es gerade nicht.";
        LivingEntity e = (LivingEntity) l.getWorld().spawn(l, cls, ent -> {
            ent.getPersistentDataContainer().set(tag, PersistentDataType.STRING, p.getUniqueId().toString());
            ent.setPersistent(false);
            ent.setInvulnerable(true);
            ent.customName(Msg.parse("<gold>" + Msg.escape(name)));
            ent.setCustomNameVisible(true);
            if (ent instanceof LivingEntity le) {
                le.setRemoveWhenFarAway(false);
                le.setCanPickupItems(false);
                le.setCollidable(false);
            }
            if (ent instanceof Ageable a) {
                if (k.baby()) a.setBaby();
                else a.setAdult();
                if (ent instanceof org.bukkit.entity.Breedable b) {
                    b.setAgeLock(true);
                    b.setBreed(false);
                }
            }
            if (ent instanceof Tameable t && !(ent instanceof org.bukkit.entity.Parrot)) {
                t.setTamed(true);
                t.setOwner(p);
            }
            if (ent instanceof Sittable s) s.setSitting(false);
            if (ent instanceof AbstractHorse h) {
                h.setTamed(true);
                h.setOwner(p);
                if (k.rideable() && h.getInventory().getSaddle() == null) h.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            }
        });
        synchronized (this) {
            active.put(p.getUniqueId(), e);
            activeKind.put(p.getUniqueId(), k.id());
        }
        p.playSound(l, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.5f);
        return null;
    }

    public boolean dismiss(UUID owner) {
        LivingEntity e;
        synchronized (this) {
            e = active.remove(owner);
            activeKind.remove(owner);
        }
        if (e == null) return false;
        e.eject();
        e.remove();
        return true;
    }

    /** Aufsteigen (nur reitbare Haustiere). */
    public String ride(Player p) {
        LivingEntity e;
        String kindId;
        synchronized (this) {
            e = active.get(p.getUniqueId());
            kindId = activeKind.get(p.getUniqueId());
        }
        Kind k = kind(kindId);
        if (e == null || k == null) return "Ruf zuerst ein Haustier: /pet";
        if (!k.rideable()) return k.name() + " kann man nicht reiten.";
        if (e.getWorld() != p.getWorld() || e.getLocation().distanceSquared(p.getLocation()) > 100) e.teleport(p.getLocation());
        e.addPassenger(p);
        return null;
    }

    private void follow() {
        List<UUID> gone = new ArrayList<>();
        Map<UUID, LivingEntity> copy;
        synchronized (this) {
            copy = new HashMap<>(active);
        }
        for (Map.Entry<UUID, LivingEntity> en : copy.entrySet()) {
            Player p = addon.getServer().getPlayer(en.getKey());
            LivingEntity e = en.getValue();
            if (p == null || !e.isValid() || !ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) {
                gone.add(en.getKey());
                continue;
            }
            if (e.getPassengers().contains(p)) continue;
            if (e.getWorld() != p.getWorld()) {
                e.teleport(p.getLocation());
                continue;
            }
            double d = e.getLocation().distanceSquared(p.getLocation());
            if (d > 400) {
                e.teleport(p.getLocation());
            } else if (d > 9 && e instanceof Mob m) {
                m.getPathfinder().moveTo(p, 1.3);
            }
        }
        for (UUID id : gone) dismiss(id);
    }

    // ---------- Schutz ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        if (!isPet(t)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!p.getUniqueId().equals(ownerOf(t))) return;
        Kind k = kind(activeKind(p.getUniqueId()));
        if (k != null && k.rideable()) {
            t.addPassenger(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (isPet(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTarget(EntityTargetEvent e) {
        if (e.getTarget() != null && isPet(e.getTarget())) e.setCancelled(true);
        if (isPet(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLeash(PlayerLeashEntityEvent e) {
        if (isPet(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTransform(EntityTransformEvent e) {
        if (isPet(e.getEntity())) e.setCancelled(true);
    }

    /** Pferde-Inventar (Sattel) nicht öffnen lassen. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onOpen(InventoryOpenEvent e) {
        if (e.getInventory().getHolder(false) instanceof Entity ent && isPet(ent)) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        dismiss(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        // Folgt in die neue Welt (follow teleportiert), außer der Modus ist nicht mehr Survival.
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(e.getPlayer().getUniqueId()))) dismiss(e.getPlayer().getUniqueId());
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(active.keySet())) dismiss(id);
        save();
    }

    // ---------- Speichern ----------

    private void loadData() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection ps = yml.getConfigurationSection("players");
        if (ps == null) return;
        for (String k : ps.getKeys(false)) {
            try {
                Data d = new Data();
                d.owned.addAll(ps.getStringList(k + ".owned"));
                d.favorites.addAll(ps.getStringList(k + ".favorites"));
                ConfigurationSection n = ps.getConfigurationSection(k + ".names");
                if (n != null) for (String id : n.getKeys(false)) d.names.put(id, n.getString(id));
                players.put(UUID.fromString(k), d);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, Data> e : players.entrySet()) {
            String b = "players." + e.getKey() + ".";
            out.set(b + "owned", new ArrayList<>(e.getValue().owned));
            out.set(b + "favorites", new ArrayList<>(e.getValue().favorites));
            for (Map.Entry<String, String> n : e.getValue().names.entrySet()) out.set(b + "names." + n.getKey(), n.getValue());
        }
        try {
            addon.getDataFolder().mkdirs();
            out.save(dataFile);
        } catch (IOException ex) {
            addon.getLogger().warning("petdata.yml: " + ex.getMessage());
        }
    }
}

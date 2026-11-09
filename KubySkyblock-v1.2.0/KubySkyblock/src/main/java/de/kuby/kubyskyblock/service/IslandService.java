package de.kuby.kubyskyblock.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Inseln der Spieler. Jede Insel hat einen Platz in einer Spirale um den Skyblock-Spawn (Abstand insel.abstand,
 * Standard 200 Blöcke), einen Besitzer und Mitglieder, ein Zuhause, Besucher-Schalter, Upgrades (Größe, Mitglieder,
 * Generator) und Punkte für das Insel-Level. Inseln aus der Zeit vor der Spirale behalten ihre alte Lage.
 * Daten: islands.yml.
 */
public final class IslandService {

    /** Rollen auf einer Insel. Besitzer und Co-Besitzer dürfen alles (nur der Besitzer kann neu starten oder aufgeben). */
    public enum Role {
        BESITZER("Besitzer"), CO_BESITZER("Co-Besitzer"), MITGLIED("Mitglied"), GAST("Gast"), BESUCHER("Besucher");

        public final String label;

        Role(String label) {
            this.label = label;
        }
    }

    /** Rechte, die der Besitzer je Rolle (Mitglied, Gast, Besucher) an- und ausschalten kann. */
    public enum Right {
        BAUEN("Bauen und Abbauen"), KISTEN("Kisten, Öfen, Trichter"), TUEREN("Türen, Knöpfe, Hebel"), TIERE("Tiere füttern, reiten, schlagen"),
        GENERATOREN("Generatoren öffnen"), EINLADEN("Leute einladen"), UPGRADES("Upgrades kaufen");

        public final String label;

        Right(String label) {
            this.label = label;
        }
    }

    /** Rollen, deren Rechte einstellbar sind. */
    public static final List<Role> EDITABLE = List.of(Role.MITGLIED, Role.GAST, Role.BESUCHER);

    static java.util.EnumMap<Role, java.util.EnumSet<Right>> defaultRights() {
        java.util.EnumMap<Role, java.util.EnumSet<Right>> m = new java.util.EnumMap<>(Role.class);
        m.put(Role.MITGLIED, java.util.EnumSet.of(Right.BAUEN, Right.KISTEN, Right.TUEREN, Right.TIERE, Right.GENERATOREN));
        m.put(Role.GAST, java.util.EnumSet.of(Right.TUEREN, Right.TIERE));
        m.put(Role.BESUCHER, java.util.EnumSet.noneOf(Right.class));
        return m;
    }

    public static final class Island {
        public final int slot;
        public UUID owner;
        public final Set<UUID> members = new LinkedHashSet<>();
        public String name = "Insel";
        public String template = "";
        public boolean open = true;
        public int size, slots, generator, growth, spawn, limit;
        public long points;
        public long created, lastReset, lastLevel;
        /** Zuhause: Welt-Koordinaten x, y, z, yaw, pitch (null = Spawn der Vorlage). */
        public double[] home;
        /** Alte Lage (vor der Spirale), sonst null. */
        public int[] legacy;
        /** Co-Besitzer (sind auch Mitglieder), Gäste (Vertrauensliste, keine Mitglieder), gesperrte Spieler. */
        public final Set<UUID> coOwners = new LinkedHashSet<>();
        public final Set<UUID> trusted = new LinkedHashSet<>();
        public final Set<UUID> banned = new LinkedHashSet<>();
        public final java.util.EnumMap<Role, java.util.EnumSet<Right>> rights = defaultRights();
        /** Freigeschaltete Stufe (0 = keine, 1 = Nether, 2 = End) und gebaute Stufen-Inseln (Bit je Stufe). */
        public int stage, built;

        Island(int slot) {
            this.slot = slot;
        }

        public boolean isMember(UUID id) {
            return id.equals(owner) || members.contains(id);
        }

        public Set<UUID> everyone() {
            Set<UUID> all = new LinkedHashSet<>();
            if (owner != null) all.add(owner);
            all.addAll(members);
            return all;
        }
    }

    /** Offene Einladung: Insel, wer eingeladen hat, bis wann. */
    public record Invite(int slot, UUID from, long until) {}

    static final int HEIGHT = 100;

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final File file;
    private final Map<Integer, Island> bySlot = new HashMap<>();
    private final Map<UUID, Island> byPlayer = new HashMap<>();
    private final Map<Long, Island> byCell = new HashMap<>();
    private final List<Island> legacy = new ArrayList<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    /** Inseln, die gerade geleert werden (Neustart, Aufgeben). */
    private final Set<Integer> busy = new HashSet<>();
    private int next = 1;
    private boolean dirty;
    /** Format der gespeicherten Upgrade-Stände (UpgradeLevels.VERSION = Level 0 bis 20). */
    private int upgradeVersion = UpgradeLevels.VERSION;

    public IslandService(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "islands.yml");
        load();
        World w = world();
        if (w != null) {
            WorldBorder b = w.getWorldBorder();
            b.setCenter(0, 0);
            b.setSize(addon.getConfig().getInt("insel.weltgrenze", 50_000));
        }
    }

    public World world() {
        return Bukkit.getWorld(core.modes().worldOf(ModeService.SKYBLOCK));
    }

    // =================================================================================================================
    // Lage
    // =================================================================================================================

    public int spacing() {
        return Math.max(64, addon.getConfig().getInt("insel.abstand", 200));
    }

    /** Spirale um (0, 0): Platz n → Zelle (x, z). Platz 0 ist der Spawn. */
    static int[] cell(int n) {
        if (n <= 0) return new int[]{0, 0};
        int k = (int) Math.ceil((Math.sqrt(n + 1) - 1) / 2);
        int t = 2 * k + 1;
        int m = t * t - 1;
        t -= 1;
        if (n >= m - t) return new int[]{k - (m - n), -k};
        m -= t;
        if (n >= m - t) return new int[]{-k, -k + (m - n)};
        m -= t;
        if (n >= m - t) return new int[]{-k + (m - n), k};
        return new int[]{k, k - (m - n - t)};
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    /** Mitte der Insel (Füße am Spawn der Vorlage). */
    public Location center(Island i) {
        World w = world();
        if (i.legacy != null) return new Location(w, i.legacy[0], HEIGHT, i.legacy[1]);
        int[] c = cell(i.slot);
        return new Location(w, c[0] * spacing(), HEIGHT, c[1] * spacing());
    }

    /** Radius der Insel je Größen-Stufe (insel.groessen). */
    public int radius(int level) {
        List<Integer> sizes = addon.getConfig().getIntegerList("insel.groessen");
        if (sizes.isEmpty()) sizes = UpgradeLevels.SIZES;
        int r = sizes.get(Math.max(0, Math.min(level, sizes.size() - 1)));
        return Math.min(r, spacing() / 2 - 5);
    }

    /** Höchstes Größen-Level: das letzte, bei dem die Insel noch wächst (der Radius endet bei abstand / 2 - 5). */
    public int maxSize() {
        List<Integer> sizes = addon.getConfig().getIntegerList("insel.groessen");
        int n = (sizes.isEmpty() ? UpgradeLevels.SIZES.size() : sizes.size()) - 1;
        int max = 0;
        for (int l = 1; l <= n; l++) if (radius(l) > radius(l - 1)) max = l;
        return max;
    }

    public int radius(Island i) {
        return radius(i.size);
    }

    /** Insel an dieser Stelle (innerhalb ihres Radius oder auf einer beanspruchten Biom-Insel), sonst null. */
    public Island islandAt(Location l) {
        Island i = mainIslandAt(l);
        if (i != null || l == null || addon.biomes() == null) return i;
        BiomeIslandService.BIsland b = addon.biomes().at(l);
        return b == null || b.owner <= 0 ? null : bySlot.get(b.owner);
    }

    /** Nur die Insel selbst (ohne Biom-Inseln), z. B. für die Punkte. In den Stufen-Welten (Nether, End) liegt die
     *  Stufen-Insel an derselben Stelle wie die Insel im Skyblock. */
    public Island mainIslandAt(Location l) {
        if (l == null || l.getWorld() == null) return null;
        boolean main = l.getWorld().getName().equals(core.modes().worldOf(ModeService.SKYBLOCK));
        if (!main && (addon.stages() == null || addon.stages().stageOf(l.getWorld()) == null)) return null;
        for (Island i : legacy) {
            int r = radius(i);
            if (Math.abs(l.getBlockX() - i.legacy[0]) <= r && Math.abs(l.getBlockZ() - i.legacy[1]) <= r) return i;
        }
        int s = spacing();
        int cx = Math.round(l.getBlockX() / (float) s), cz = Math.round(l.getBlockZ() / (float) s);
        Island i = byCell.get(key(cx, cz));
        if (i == null) return null;
        int r = radius(i);
        return Math.abs(l.getBlockX() - cx * s) <= r && Math.abs(l.getBlockZ() - cz * s) <= r ? i : null;
    }

    /** Etwas an einer Insel geändert (z. B. Stufe freigeschaltet): beim nächsten Speichern mitschreiben. */
    public void markDirty() {
        dirty = true;
    }

    public Island islandOf(UUID id) {
        return byPlayer.get(id);
    }

    public Island bySlot(int slot) {
        return bySlot.get(slot);
    }

    public int count() {
        return bySlot.size();
    }

    public List<Island> all() {
        return new ArrayList<>(bySlot.values());
    }

    /** Darf der Spieler hier bauen? (Recht BAUEN) */
    public boolean canBuild(Player p, Location l) {
        return can(p, l, Right.BAUEN);
    }

    public Role role(Island i, UUID id) {
        if (i == null || id == null) return Role.BESUCHER;
        if (id.equals(i.owner)) return Role.BESITZER;
        if (i.coOwners.contains(id)) return Role.CO_BESITZER;
        if (i.members.contains(id)) return Role.MITGLIED;
        if (i.trusted.contains(id)) return Role.GAST;
        return Role.BESUCHER;
    }

    public boolean has(Island i, UUID id, Right r) {
        Role role = role(i, id);
        if (role == Role.BESITZER || role == Role.CO_BESITZER) return true;
        java.util.EnumSet<Right> set = i.rights.get(role);
        return set != null && set.contains(r);
    }

    /** Darf der Spieler an dieser Stelle das? Admins überall; außerhalb von Inseln niemand sonst; während Neustart niemand. */
    public boolean can(Player p, Location l, Right r) {
        if (p.hasPermission("kuby.admin")) return true;
        Island i = islandAt(l);
        return i != null && !busy.contains(i.slot) && has(i, p.getUniqueId(), r);
    }

    /** Besitzer und Co-Besitzer verwalten die Insel. */
    public boolean canManage(Island i, UUID id) {
        Role r = role(i, id);
        return r == Role.BESITZER || r == Role.CO_BESITZER;
    }

    /** Darf der Spieler die Insel betreten? Gesperrte nie; geschlossene Inseln nur Mitglieder und Gäste. */
    public boolean mayEnter(Player p, Island i) {
        if (i == null || p.hasPermission("kuby.admin")) return true;
        UUID id = p.getUniqueId();
        if (i.banned.contains(id)) return false;
        return i.open || role(i, id) != Role.BESUCHER;
    }

    // ---------- Rollen, Gäste, Sperren ----------

    private Island managed(Player p) {
        Island i = islandOf(p.getUniqueId());
        return i != null && canManage(i, p.getUniqueId()) ? i : null;
    }

    /** Mitglied zum Co-Besitzer machen oder zurückstufen (nur der Besitzer). */
    public String setCoOwner(Player p, UUID target, boolean co) {
        Island i = islandOf(p.getUniqueId());
        if (i == null) return "Du hast keine Insel.";
        if (!p.getUniqueId().equals(i.owner)) return "Nur der Besitzer vergibt Co-Besitzer.";
        if (!i.members.contains(target)) return "Das geht nur mit Mitgliedern.";
        if (co) i.coOwners.add(target);
        else i.coOwners.remove(target);
        dirty = true;
        Player t = Bukkit.getPlayer(target);
        if (t != null) Msg.send(t, "<#9AD8FF>Du bist auf " + Msg.escape(i.name) + " jetzt <white>" + (co ? "Co-Besitzer" : "Mitglied") + "<#9AD8FF>.");
        return null;
    }

    public String trust(Player p, UUID target, boolean add) {
        Island i = managed(p);
        if (i == null) return "Das dürfen nur Besitzer und Co-Besitzer.";
        if (i.isMember(target)) return "Mitglieder brauchen keinen Gast-Platz.";
        if (add) {
            if (i.trusted.size() >= 20) return "Höchstens 20 Gäste.";
            i.banned.remove(target);
            i.trusted.add(target);
        } else {
            i.trusted.remove(target);
        }
        dirty = true;
        return null;
    }

    /** Sperren (wirft auch gleich raus) oder entsperren. */
    public String ban(Player p, UUID target, boolean add) {
        Island i = managed(p);
        if (i == null) return "Das dürfen nur Besitzer und Co-Besitzer.";
        if (add) {
            if (i.isMember(target)) return "Mitglieder kannst du nicht sperren. Entferne sie erst.";
            Player t = Bukkit.getPlayer(target);
            if (t != null && t.hasPermission("kuby.admin")) return "Admins kannst du nicht sperren.";
            i.trusted.remove(target);
            i.banned.add(target);
            if (t != null && islandAt(t.getLocation()) == i) {
                t.teleportAsync(core.modes().spawnOf(ModeService.SKYBLOCK));
                Msg.send(t, "<red>Du bist auf der Insel " + Msg.escape(i.name) + " gesperrt.");
            }
        } else {
            i.banned.remove(target);
        }
        dirty = true;
        return null;
    }

    /** Besucher von der Insel werfen (zum Spawn). */
    public String expel(Player p, UUID target) {
        Island i = managed(p);
        if (i == null) return "Das dürfen nur Besitzer und Co-Besitzer.";
        if (i.isMember(target)) return "Mitglieder kannst du nicht rauswerfen.";
        Player t = Bukkit.getPlayer(target);
        if (t == null || islandAt(t.getLocation()) != i) return "Der Spieler ist gerade nicht auf deiner Insel.";
        if (t.hasPermission("kuby.admin")) return "Admins kannst du nicht rauswerfen.";
        t.teleportAsync(core.modes().spawnOf(ModeService.SKYBLOCK));
        Msg.send(t, "<red>Du wurdest von der Insel " + Msg.escape(i.name) + " geworfen.");
        return null;
    }

    public String toggleRight(Player p, Role role, Right right) {
        Island i = managed(p);
        if (i == null) return "Das dürfen nur Besitzer und Co-Besitzer.";
        if (!EDITABLE.contains(role)) return "Diese Rolle darf schon alles.";
        java.util.EnumSet<Right> set = i.rights.computeIfAbsent(role, k -> java.util.EnumSet.noneOf(Right.class));
        if (!set.remove(right)) set.add(right);
        dirty = true;
        return null;
    }

    /** Spieler, die gerade auf der Insel sind und nicht dazugehören. */
    public List<Player> visitors(Island i) {
        List<Player> out = new ArrayList<>();
        World w = world();
        if (w == null || i == null) return out;
        for (Player v : w.getPlayers()) if (!i.isMember(v.getUniqueId()) && islandAt(v.getLocation()) == i) out.add(v);
        return out;
    }

    /** Für alte Aufrufer: liegt die Stelle auf der Insel des Spielers? */
    public boolean isIslandOwner(UUID id, Location l) {
        Island i = islandAt(l);
        return i != null && i.isMember(id);
    }

    // =================================================================================================================
    // Anlegen und Teleport
    // =================================================================================================================

    /** Beim Betreten von Skyblock: zur eigenen Insel, sonst Vorlage wählen (mehrere) oder gleich anlegen (eine). */
    public void ensureIsland(Player p) {
        if (world() == null) {
            Msg.send(p, "<red>Die Skyblock-Welt ist noch nicht eingerichtet.");
            return;
        }
        Island i = islandOf(p.getUniqueId());
        if (i != null) {
            home(p, i);
            return;
        }
        List<TemplateService.Template> ts = addon.templates().all();
        if (ts.size() <= 1) {
            create(p, ts.isEmpty() ? null : ts.get(0).id());
            return;
        }
        Bukkit.getScheduler().runTaskLater(addon, () -> {
            if (p.isOnline() && islandOf(p.getUniqueId()) == null) de.kuby.kubyskyblock.gui.IslandScreen.choose(p);
        }, 20L);
    }

    /** Neue Insel aus der Vorlage anlegen und hinbringen. */
    public synchronized String create(Player p, String templateId) {
        if (islandOf(p.getUniqueId()) != null) return "Du hast schon eine Insel.";
        World w = world();
        if (w == null) return "Die Skyblock-Welt ist noch nicht eingerichtet.";
        Island i = new Island(nextFreeSlot());
        i.owner = p.getUniqueId();
        i.name = "Insel von " + p.getName();
        i.created = System.currentTimeMillis();
        i.open = addon.getConfig().getBoolean("insel.besucher-standard", true);
        TemplateService.Template t = addon.templates().get(templateId);
        i.template = t == null ? "" : t.id();
        register(i);
        Location c = center(i);
        if (t != null) {
            addon.templates().build(t, c);
            i.points = templatePoints(t);
        } else {
            fallback(c);
        }
        dirty = true;
        save();
        Msg.send(p, "<#9AD8FF><b>Willkommen auf deiner Insel!</b> <gray>Mit <white>/insel</white> verwaltest du sie.");
        Sfx.play(core, p, Sfx.Kind.SUCCESS_BIG);
        home(p, i);
        return null;
    }

    private int nextFreeSlot() {
        int s = spacing();
        while (true) {
            int n = next++;
            int[] c = cell(n);
            boolean clash = false;
            for (Island l : legacy) {
                if (Math.abs(l.legacy[0] - c[0] * s) < s && Math.abs(l.legacy[1] - c[1] * s) < s) clash = true;
            }
            if (!clash && !bySlot.containsKey(n)) return n;
        }
    }

    private void register(Island i) {
        bySlot.put(i.slot, i);
        if (i.legacy != null) legacy.add(i);
        else {
            int[] c = cell(i.slot);
            byCell.put(key(c[0], c[1]), i);
        }
        for (UUID u : i.everyone()) byPlayer.put(u, i);
    }

    private void unregister(Island i) {
        bySlot.remove(i.slot);
        legacy.remove(i);
        if (i.legacy == null) {
            int[] c = cell(i.slot);
            byCell.remove(key(c[0], c[1]));
        }
        for (UUID u : i.everyone()) if (byPlayer.get(u) == i) byPlayer.remove(u);
        if (addon.biomes() != null) addon.biomes().releaseAll(i.slot);
        if (addon.limits() != null) addon.limits().forget(i.slot);
    }

    /** Ohne Vorlage: 5 x 5 Erde mit Gras und Kiste. */
    private void fallback(Location c) {
        World w = c.getWorld();
        int ox = c.getBlockX(), oy = c.getBlockY(), oz = c.getBlockZ();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.getBlockAt(ox + dx, oy - 3, oz + dz).setType(Material.DIRT, false);
                w.getBlockAt(ox + dx, oy - 2, oz + dz).setType(Material.DIRT, false);
                w.getBlockAt(ox + dx, oy - 1, oz + dz).setType(Material.GRASS_BLOCK, false);
            }
        }
        w.getBlockAt(ox - 1, oy, oz - 1).setType(Material.CHEST, false);
    }

    /** Teleport zum Zuhause der Insel (sicherer Platz). */
    public void home(Player p, Island i) {
        Location l;
        if (i.home != null) {
            l = new Location(world(), i.home[0], i.home[1], i.home[2], (float) i.home[3], (float) i.home[4]);
        } else {
            l = center(i).add(0.5, 0, 0.5);
        }
        p.setFallDistance(0);
        p.teleportAsync(l);
    }

    public String setHome(Player p) {
        Island i = islandOf(p.getUniqueId());
        if (i == null) return "Du hast keine Insel.";
        if (!canManage(i, p.getUniqueId())) return "Das dürfen nur Besitzer und Co-Besitzer.";
        if (addon.stages() != null && addon.stages().stageOf(p.getWorld()) != null) return "Das Zuhause setzt du auf deiner Insel im Skyblock.";
        if (islandAt(p.getLocation()) != i) return "Stell dich dafür auf deine Insel.";
        Location l = p.getLocation();
        i.home = new double[]{l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()};
        dirty = true;
        return null;
    }

    /** Besuchen: nur offene Inseln (Mitglieder immer). */
    public String visit(Player p, String target) {
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(target);
        Island i = op == null ? null : islandOf(op.getUniqueId());
        if (i == null) return "Dieser Spieler hat keine Insel.";
        return visit(p, i);
    }

    /** Offene Inseln zum Besuchen (ohne die eigene und gesperrte), beste zuerst. */
    public List<Island> openIslands(Player p) {
        List<Island> l = new ArrayList<>();
        for (Island i : bySlot.values()) {
            if (i.owner == null || !i.open || busy.contains(i.slot) || i.isMember(p.getUniqueId()) || i.banned.contains(p.getUniqueId())) continue;
            l.add(i);
        }
        l.sort(Comparator.comparingLong((Island i) -> i.points).reversed());
        return l;
    }

    /** Wie viele Mitglieder der Insel sind online? */
    public int online(Island i) {
        int n = 0;
        for (UUID u : i.everyone()) if (Bukkit.getPlayer(u) != null) n++;
        return n;
    }

    public String visit(Player p, Island i) {
        if (i == null || bySlot.get(i.slot) != i) return "Diese Insel gibt es nicht mehr.";
        if (busy.contains(i.slot)) return "Die Insel wird gerade neu gebaut.";
        if (i.banned.contains(p.getUniqueId()) && !p.hasPermission("kuby.admin")) return "Du bist auf dieser Insel gesperrt.";
        if (!mayEnter(p, i)) return "Diese Insel ist für Besucher geschlossen.";
        home(p, i);
        Msg.send(p, "<gray>Du besuchst <white>" + Msg.escape(i.name) + "<gray>.");
        if (!i.isMember(p.getUniqueId())) core.questProgress(p, "insel_besuch", null, 1);
        return null;
    }

    public String toggleOpen(Player p) {
        Island i = islandOf(p.getUniqueId());
        if (i == null) return "Du hast keine Insel.";
        if (!canManage(i, p.getUniqueId())) return "Das dürfen nur Besitzer und Co-Besitzer.";
        i.open = !i.open;
        dirty = true;
        if (!i.open) {
            for (Player v : world().getPlayers()) {
                if (role(i, v.getUniqueId()) == Role.BESUCHER && islandAt(v.getLocation()) == i && !v.hasPermission("kuby.admin")) {
                    v.teleportAsync(core.modes().spawnOf(ModeService.SKYBLOCK));
                    Msg.send(v, "<gray>Die Insel wurde für Besucher geschlossen.");
                }
            }
        }
        return null;
    }

    public String rename(Player p, String name) {
        Island i = islandOf(p.getUniqueId());
        if (i == null) return "Du hast keine Insel.";
        if (!canManage(i, p.getUniqueId())) return "Das dürfen nur Besitzer und Co-Besitzer.";
        String n = name.trim();
        if (n.isEmpty() || n.length() > 32) return "Der Name braucht 1 bis 32 Zeichen.";
        i.name = n;
        dirty = true;
        return null;
    }

    // =================================================================================================================
    // Mitglieder
    // =================================================================================================================

    public int memberLimit(Island i) {
        List<Integer> l = addon.getConfig().getIntegerList("insel.mitglieder");
        if (l.isEmpty()) l = UpgradeLevels.MEMBERS;
        return l.get(Math.max(0, Math.min(i.slots, l.size() - 1)));
    }

    public int maxSlots() {
        List<Integer> l = addon.getConfig().getIntegerList("insel.mitglieder");
        return (l.isEmpty() ? UpgradeLevels.MEMBERS.size() : l.size()) - 1;
    }

    public String invite(Player p, Player target) {
        Island i = islandOf(p.getUniqueId());
        if (i == null) return "Du hast keine Insel.";
        if (!has(i, p.getUniqueId(), Right.EINLADEN)) return "Einladen dürfen nur Besitzer, Co-Besitzer und Mitglieder mit dem Recht dazu.";
        if (target.equals(p)) return "Du bist schon auf deiner Insel.";
        if (i.isMember(target.getUniqueId())) return target.getName() + " ist schon Mitglied.";
        if (i.members.size() >= memberLimit(i)) return "Deine Insel ist voll (" + memberLimit(i) + " Mitglieder). Erweitere sie unter Upgrades.";
        invites.put(target.getUniqueId(), new Invite(i.slot, p.getUniqueId(), System.currentTimeMillis() + 5 * 60_000L));
        Msg.send(target, "<#9AD8FF><b>Insel-Einladung:</b> <white>" + p.getName() + " <gray>lädt dich auf <white>" + Msg.escape(i.name)
                + "<gray> ein. <click:run_command:'/insel annehmen'><green>[Annehmen]</click> <gray>(5 Minuten)");
        Sfx.play(core, target, Sfx.Kind.CHIME);
        return null;
    }

    public String accept(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        if (inv == null || inv.until() < System.currentTimeMillis()) return "Du hast keine offene Einladung.";
        Island i = bySlot.get(inv.slot());
        if (i == null) return "Diese Insel gibt es nicht mehr.";
        Island own = islandOf(p.getUniqueId());
        if (own != null) {
            if (own.owner != null && own.owner.equals(p.getUniqueId()) && !own.members.isEmpty()) {
                return "Du besitzt eine Insel mit Mitgliedern. Gib sie erst ab oder entferne die Mitglieder.";
            }
            return "Du hast schon eine Insel. Verlass sie erst mit /insel verlassen oder gib sie mit /insel aufgeben auf.";
        }
        if (i.members.size() >= memberLimit(i)) return "Die Insel ist inzwischen voll.";
        i.members.add(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), i);
        dirty = true;
        for (UUID u : i.everyone()) {
            Player o = Bukkit.getPlayer(u);
            if (o != null && !o.equals(p)) Msg.send(o, "<green>" + p.getName() + " ist jetzt Mitglied deiner Insel.");
        }
        Msg.send(p, "<green>Du bist jetzt Mitglied von <white>" + Msg.escape(i.name) + "<green>.");
        if (ModeService.SKYBLOCK.equals(core.modes().modeOf(p.getUniqueId()))) home(p, i);
        return null;
    }

    public String kick(Player p, UUID target) {
        Island i = islandOf(p.getUniqueId());
        if (i == null) return "Du hast keine Insel.";
        if (!canManage(i, p.getUniqueId())) return "Mitglieder entfernen dürfen nur Besitzer und Co-Besitzer.";
        if (i.coOwners.contains(target) && !p.getUniqueId().equals(i.owner)) return "Co-Besitzer kann nur der Besitzer entfernen.";
        if (!i.members.remove(target)) return "Dieser Spieler ist kein Mitglied.";
        i.coOwners.remove(target);
        byPlayer.remove(target);
        dirty = true;
        Player t = Bukkit.getPlayer(target);
        if (t != null) {
            Msg.send(t, "<red>Du wurdest von der Insel " + Msg.escape(i.name) + " entfernt.");
            if (islandAt(t.getLocation()) == i) t.teleportAsync(core.modes().spawnOf(ModeService.SKYBLOCK));
        }
        return null;
    }

    public String leave(Player p) {
        Island i = islandOf(p.getUniqueId());
        if (i == null) return "Du hast keine Insel.";
        if (p.getUniqueId().equals(i.owner)) return "Als Besitzer kannst du nicht gehen. Gib die Insel mit /insel aufgeben auf.";
        i.members.remove(p.getUniqueId());
        byPlayer.remove(p.getUniqueId());
        dirty = true;
        if (islandAt(p.getLocation()) == i) p.teleportAsync(core.modes().spawnOf(ModeService.SKYBLOCK));
        Msg.send(p, "<gray>Du hast die Insel verlassen. Mit <white>/insel</white> startest du eine neue.");
        return null;
    }

    /** Besitzer gibt die Insel auf: geht an das erste Mitglied, ohne Mitglieder wird sie geleert. */
    public String abandon(Player p) {
        Island i = islandOf(p.getUniqueId());
        if (i == null) return "Du hast keine Insel.";
        if (!p.getUniqueId().equals(i.owner)) return "Nur der Besitzer kann die Insel aufgeben.";
        if (!i.members.isEmpty()) {
            UUID heir = i.members.iterator().next();
            i.members.remove(heir);
            i.coOwners.remove(heir);
            i.owner = heir;
            byPlayer.remove(p.getUniqueId());
            dirty = true;
            Player h = Bukkit.getPlayer(heir);
            if (h != null) Msg.send(h, "<gold>Du bist jetzt Besitzer von " + Msg.escape(i.name) + ".");
            if (islandAt(p.getLocation()) == i) p.teleportAsync(core.modes().spawnOf(ModeService.SKYBLOCK));
            return null;
        }
        unregister(i);
        if (addon.stages() != null) addon.stages().clear(i);
        dirty = true;
        busy.add(i.slot);
        if (ModeService.SKYBLOCK.equals(core.modes().modeOf(p.getUniqueId()))) p.teleportAsync(core.modes().spawnOf(ModeService.SKYBLOCK));
        Msg.send(p, "<gray>Deine Insel ist aufgegeben. Mit <white>/insel</white> startest du eine neue.");
        clear(i, () -> busy.remove(i.slot));
        save();
        return null;
    }

    // =================================================================================================================
    // Neustart
    // =================================================================================================================

    public long resetCooldownLeft(Island i) {
        long cd = addon.getConfig().getLong("insel.neustart-abklingzeit-stunden", 24) * 3_600_000L;
        return Math.max(0, i.lastReset + cd - System.currentTimeMillis());
    }

    /** Insel leeren und neu aus der Vorlage bauen. Upgrades bleiben, Punkte werden neu gezählt. */
    public String reset(Player p, String templateId) {
        Island i = islandOf(p.getUniqueId());
        if (i == null) return "Du hast keine Insel.";
        if (!p.getUniqueId().equals(i.owner)) return "Nur der Besitzer kann die Insel neu starten.";
        if (busy.contains(i.slot)) return "Die Insel wird gerade neu gebaut.";
        long left = resetCooldownLeft(i);
        if (left > 0) return "Neustart wieder in " + de.kuby.kubyplugins.service.KitService.format(left) + ".";
        i.lastReset = System.currentTimeMillis();
        i.home = null;
        busy.add(i.slot);
        dirty = true;
        Location spawn = core.modes().spawnOf(ModeService.SKYBLOCK);
        for (Player o : world().getPlayers()) if (islandAt(o.getLocation()) == i) o.teleportAsync(spawn);
        Msg.send(p, "<gray>Deine Insel wird geleert und neu gebaut ...");
        clear(i, () -> {
            busy.remove(i.slot);
            if (addon.limits() != null) addon.limits().forget(i.slot);
            TemplateService.Template t = addon.templates().get(templateId != null ? templateId : i.template);
            i.template = t == null ? "" : t.id();
            if (t != null) addon.templates().build(t, center(i));
            else fallback(center(i));
            i.points = t == null ? 0 : templatePoints(t);
            dirty = true;
            for (UUID u : i.everyone()) {
                Player o = Bukkit.getPlayer(u);
                if (o != null && ModeService.SKYBLOCK.equals(core.modes().modeOf(u))) {
                    Msg.send(o, "<green>Die Insel ist neu gebaut.");
                    home(o, i);
                }
            }
        });
        return null;
    }

    /** Leert den Bereich der Insel nach und nach (Spalten, je Tick begrenzt), danach done. */
    private void clear(Island i, Runnable done) {
        World w = world();
        Location c = center(i);
        int r = radius(maxSize());
        int x0 = c.getBlockX() - r, z0 = c.getBlockZ() - r, side = 2 * r + 1;
        int min = w.getMinHeight();
        int[] col = {0};
        int perTick = addon.getConfig().getInt("insel.leeren-spalten-pro-tick", 400);
        Bukkit.getScheduler().runTaskTimer(addon, task -> {
            for (int n = 0; n < perTick && col[0] < side * side; n++, col[0]++) {
                int x = x0 + col[0] % side, z = z0 + col[0] / side;
                if (!w.isChunkLoaded(x >> 4, z >> 4)) w.getChunkAt(x >> 4, z >> 4);
                int top = w.getHighestBlockYAt(x, z);
                for (int y = top; y >= min; y--) {
                    Block b = w.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) b.setType(Material.AIR, false);
                }
            }
            if (col[0] >= side * side) {
                task.cancel();
                for (var en : w.getNearbyEntities(c, r, 200, r)) if (!(en instanceof Player)) en.remove();
                done.run();
            }
        }, 1L, 1L);
    }

    // =================================================================================================================
    // Upgrades
    // =================================================================================================================

    /** Größe, Mitglieder, Bruchstein-Generator, Wachstum (Pflanzen), Spawnrate (Tiere und Monster), Limits (Lag-Grenzen). */
    public enum Upgrade { SIZE, SLOTS, GENERATOR, GROWTH, SPAWN, LIMIT }

    public int level(Island i, Upgrade u) {
        return switch (u) {
            case SIZE -> i.size;
            case SLOTS -> i.slots;
            case GENERATOR -> i.generator;
            case GROWTH -> i.growth;
            case SPAWN -> i.spawn;
            case LIMIT -> i.limit;
        };
    }

    /** Preisliste aus der config (Index 0 = Start). */
    private List<Long> prices(Upgrade u) {
        String key = switch (u) {
            case SIZE -> "groesse";
            case SLOTS -> "mitglieder";
            case GROWTH -> "wachstum";
            case SPAWN -> "spawnrate";
            default -> "limits";
        };
        List<Long> l = addon.getConfig().getLongList("insel.upgrades." + key);
        if (!l.isEmpty()) return l;
        return UpgradeLevels.prices(switch (u) {
            case SIZE -> 6_700;
            case SLOTS -> 2_900;
            case GROWTH -> 4_200;
            case SPAWN -> 4_600;
            default -> 5_000;
        });
    }

    /** Hat die config schon die Level-Listen (mindestens 20 Level, Preise in $ für Level 1 bis 20)? */
    public boolean levelsReady() {
        int need = UpgradeLevels.BASE + 1;
        if (addon.getConfig().getIntegerList("insel.groessen").size() < need || addon.getConfig().getIntegerList("insel.mitglieder").size() < need
                || generatorLevels().size() < need) return false;
        for (Upgrade u : Upgrade.values()) if (u != Upgrade.GENERATOR && prices(u).size() != need) return false;
        return true;
    }

    public int maxLevel(Upgrade u) {
        return switch (u) {
            case SIZE -> maxSize();
            case SLOTS -> maxSlots();
            case GENERATOR -> Math.max(0, generatorLevels().size() - 1);
            default -> {
                int m = prices(u).size() - 1;
                yield m >= UpgradeLevels.BASE ? UpgradeLevels.MAX : m;
            }
        };
    }

    /** Ist das nächste Level aus Stufe 4 (Level 21-50, nur mit Juwelen)? */
    public boolean jewelStep(Island i, Upgrade u) {
        return UpgradeLevels.jewelLevel(level(i, u) + 1);
    }

    /** Preis für das nächste Level (-1 = schon höchstes): bis Level 20 in $, in Stufe 4 in Juwelen (jewelStep). */
    public long price(Island i, Upgrade u) {
        int next = level(i, u) + 1;
        if (next > maxLevel(u)) return -1;
        if (UpgradeLevels.jewelLevel(next)) {
            return UpgradeLevels.jewels(next, addon.getConfig().getDouble("insel.juwelen-stufe.basis", 4),
                    addon.getConfig().getDouble("insel.juwelen-stufe.faktor", 1.05));
        }
        if (u == Upgrade.GENERATOR) return generatorLevels().get(next).cost();
        List<Long> l = prices(u);
        return l.get(Math.min(next, l.size() - 1));
    }

    /** Bonus des Wachstums-Upgrades: Chance auf eine Stufe mehr beim Wachsen (bis 1 bei Level 20, in Stufe 4 bis 2:
     *  über 1 sicher eine mehr, der Rest ist die Chance auf eine zweite). */
    public double growthChance(Island i) {
        return i == null ? 0 : growthAt(i.growth);
    }

    public double growthAt(int level) {
        return bonus(level, "wachstum", 0.05, 0.0333);
    }

    public double spawnAt(int level) {
        return bonus(level, "spawnrate", 0.04, 0.02);
    }

    /** Bonus des Spawnrate-Upgrades: Chance auf ein weiteres Wesen beim natürlichen Spawnen (bis 0,8 bei Level 20, in
     *  Stufe 4 bis 1,4: über 1 sicher eines mehr, der Rest ist die Chance auf ein drittes). */
    public double spawnChance(Island i) {
        return i == null ? 0 : spawnAt(i.spawn);
    }

    /** Bonus eines Levels: bis Level 20 bonus-je-stufe je Level, darüber bonus-stufe4 je Level (höchstens 2). */
    private double bonus(int level, String key, double per, double per4) {
        int base = Math.min(level, UpgradeLevels.BASE), extra = Math.max(0, level - UpgradeLevels.BASE);
        double v = base * addon.getConfig().getDouble(key + ".bonus-je-stufe", per) + extra * addon.getConfig().getDouble(key + ".bonus-stufe4", per4);
        return Math.max(0, Math.min(2.0, v));
    }

    /** Wie oft ein Bonus greift: ganzer Teil sicher, Rest als Chance (z. B. 1,4 = einmal sicher, zu 40 % zweimal). */
    public static int rolls(double chance) {
        int n = (int) Math.floor(chance);
        return n + (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < chance - n ? 1 : 0);
    }

    public String upgrade(Player p, Upgrade u) {
        Island i = islandOf(p.getUniqueId());
        if (i == null) return "Du hast keine Insel.";
        if (!has(i, p.getUniqueId(), Right.UPGRADES)) return "Upgrades kaufen dürfen nur Besitzer, Co-Besitzer und Mitglieder mit dem Recht dazu.";
        long price = price(i, u);
        if (price < 0) return "Das ist schon das höchste Level.";
        if (jewelStep(i, u)) {
            // Stufe 4 (Level 21-50): nur mit Juwelen
            if (!core.jewels().take(p.getUniqueId(), price)) return "Stufe 4 gibt es nur mit Juwelen: du brauchst " + Msg.coins(price) + " Juwelen.";
        } else if (!core.economy().withdraw(p.getUniqueId(), price)) {
            return "Dafür brauchst du " + Msg.coins(price) + " $.";
        }
        switch (u) {
            case SIZE -> i.size++;
            case SLOTS -> i.slots++;
            case GENERATOR -> i.generator++;
            case GROWTH -> i.growth++;
            case SPAWN -> i.spawn++;
            case LIMIT -> i.limit++;
        }
        dirty = true;
        core.questProgress(p, "insel_upgrade", u.name().toLowerCase(), 1);
        for (UUID m : i.everyone()) {
            Player o = Bukkit.getPlayer(m);
            if (o != null) Msg.send(o, "<green>Insel-Upgrade: <white>" + switch (u) {
                case SIZE -> "Größe " + (2 * radius(i) + 1) + " x " + (2 * radius(i) + 1);
                case SLOTS -> memberLimit(i) + " Mitglieder";
                case GENERATOR -> "Generator Level " + i.generator;
                case GROWTH -> "Wachstum +" + Math.round(growthChance(i) * 100) + " %";
                case SPAWN -> "Spawnrate +" + Math.round(spawnChance(i) * 100) + " %";
                case LIMIT -> "Limits +" + Math.round(addon.limits().bonus(i) * 100) + " %";
            });
        }
        Sfx.play(core, p, Sfx.Kind.LEVEL_UP);
        return null;
    }

    /** Generator-Stufe: Preis und Gewichte der Blöcke, die statt Bruchstein entstehen. */
    public record GenLevel(long cost, Map<Material, Integer> weights) {}

    public List<GenLevel> generatorLevels() {
        List<GenLevel> out = new ArrayList<>();
        for (Map<?, ?> m : addon.getConfig().getMapList("generator.stufen")) {
            long cost = m.get("kosten") instanceof Number n ? n.longValue() : 0;
            Map<Material, Integer> w = new EnumMap<>(Material.class);
            if (m.get("bloecke") instanceof Map<?, ?> b) {
                for (var e : b.entrySet()) {
                    Material mat = Material.matchMaterial(String.valueOf(e.getKey()));
                    if (mat != null && mat.isBlock() && e.getValue() instanceof Number n) w.put(mat, n.intValue());
                }
            }
            if (!w.isEmpty()) out.add(new GenLevel(cost, w));
        }
        if (out.isEmpty()) out.add(new GenLevel(0, Map.of(Material.COBBLESTONE, 100)));
        return out;
    }

    /** Zufälliger Block des Generators der Insel. */
    public Material generate(Island i) {
        List<GenLevel> ls = generatorLevels();
        GenLevel g = ls.get(Math.max(0, Math.min(i.generator, ls.size() - 1)));
        int total = 0;
        for (int v : g.weights().values()) total += Math.max(0, v);
        if (total <= 0) return Material.COBBLESTONE;
        int r = java.util.concurrent.ThreadLocalRandom.current().nextInt(total);
        for (var e : g.weights().entrySet()) {
            r -= Math.max(0, e.getValue());
            if (r < 0) return e.getKey();
        }
        return Material.COBBLESTONE;
    }

    // =================================================================================================================
    // Level
    // =================================================================================================================

    public long perLevel() {
        return Math.max(1, addon.getConfig().getLong("level.punkte-pro-level", 100));
    }

    public long levelOf(Island i) {
        return i.points / perLevel();
    }

    /** Punkte eines Blocks (level.werte, sonst level.standard-wert; Luft und Flüssigkeiten 0). */
    public int value(Material m) {
        if (m == null || m.isAir() || m == Material.WATER || m == Material.LAVA) return 0;
        ConfigurationSection s = addon.getConfig().getConfigurationSection("level.werte");
        if (s != null && s.contains(m.name())) return s.getInt(m.name());
        return addon.getConfig().getInt("level.standard-wert", 1);
    }

    private long templatePoints(TemplateService.Template t) {
        long sum = 0;
        for (TemplateService.Block3 b : t.blocks()) {
            try {
                sum += value(Bukkit.createBlockData(b.data()).getMaterial());
            } catch (IllegalArgumentException ignored) {
                // unbekannter Block
            }
        }
        return sum;
    }

    /** Gesetzter oder abgebauter Block auf einer Insel (Stufen-Inseln zählen nicht, wie beim Neu-Zählen). */
    public void addPoints(Location l, Material m, int sign) {
        if (addon.stages() != null && l != null && addon.stages().stageOf(l.getWorld()) != null) return;
        Island i = mainIslandAt(l);
        if (i == null) return;
        long v = value(m);
        if (v == 0) return;
        i.points = Math.max(0, i.points + sign * v);
        dirty = true;
    }

    /** Insel neu zählen (Schnappschüsse der Chunks, Zählen im Hintergrund). Höchstens einmal pro Minute. */
    public String recalc(Island i, Consumer<Long> done) {
        long now = System.currentTimeMillis();
        if (now - i.lastLevel < 60_000L) return "Das Level wurde gerade erst berechnet.";
        i.lastLevel = now;
        World w = world();
        Location c = center(i);
        int r = radius(i);
        int x0 = c.getBlockX() - r, x1 = c.getBlockX() + r, z0 = c.getBlockZ() - r, z1 = c.getBlockZ() + r;
        List<ChunkSnapshot> snaps = new ArrayList<>();
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) snaps.add(w.getChunkAt(cx, cz).getChunkSnapshot(false, false, false));
        }
        int min = w.getMinHeight(), max = w.getMaxHeight();
        Map<Material, Integer> cache = new HashMap<>();
        Bukkit.getScheduler().runTaskAsynchronously(addon, () -> {
            long sum = 0;
            for (ChunkSnapshot s : snaps) {
                int bx = s.getX() << 4, bz = s.getZ() << 4;
                for (int dx = 0; dx < 16; dx++) {
                    int x = bx + dx;
                    if (x < x0 || x > x1) continue;
                    for (int dz = 0; dz < 16; dz++) {
                        int z = bz + dz;
                        if (z < z0 || z > z1) continue;
                        int top = Math.min(max - 1, s.getHighestBlockYAt(dx, dz));
                        for (int y = min; y <= top; y++) {
                            Material m = s.getBlockType(dx, y, dz);
                            if (m.isAir()) continue;
                            sum += cache.computeIfAbsent(m, this::value);
                        }
                    }
                }
            }
            long total = sum;
            Bukkit.getScheduler().runTask(addon, () -> {
                i.points = total;
                dirty = true;
                if (done != null) done.accept(total);
            });
        });
        return null;
    }

    /** Beste Inseln nach Punkten. */
    public List<Island> top(int n) {
        List<Island> l = new ArrayList<>(bySlot.values());
        l.removeIf(i -> i.owner == null);
        l.sort(Comparator.comparingLong((Island i) -> i.points).reversed());
        return l.subList(0, Math.min(n, l.size()));
    }

    /** Platz nach Punkten. Aus einem Zwischenspeicher, der höchstens alle 30 Sekunden neu sortiert wird (Scoreboard fragt oft). */
    public int rank(Island i) {
        if (i == null) return 0;
        long now = System.currentTimeMillis();
        if (now - rankAt > 30_000) {
            rankAt = now;
            java.util.Map<Integer, Integer> m = new java.util.HashMap<>();
            List<Island> l = top(Integer.MAX_VALUE);
            for (int k = 0; k < l.size(); k++) m.put(l.get(k).slot, k + 1);
            ranks = m;
        }
        return ranks.getOrDefault(i.slot, 0);
    }

    private volatile java.util.Map<Integer, Integer> ranks = java.util.Map.of();
    private long rankAt;

    public static String nameOf(UUID id) {
        if (id == null) return "?";
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        upgradeVersion = levelsReady() ? UpgradeLevels.VERSION : 1;
        if (!y.contains("version")) {
            // altes Format: UUID -> Nummer im Raster (100er Abstand, 500 je Reihe)
            int slot = 1;
            for (String k : y.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(k);
                    int n = y.getInt(k);
                    Island i = new Island(slot++);
                    i.owner = id;
                    i.name = "Insel von " + nameOf(id);
                    i.legacy = new int[]{(n % 500) * 100, (n / 500) * 100};
                    i.created = System.currentTimeMillis();
                    register(i);
                } catch (IllegalArgumentException ignored) {
                    // kein Spieler
                }
            }
            next = slot;
            if (!bySlot.isEmpty()) {
                dirty = true;
                save();
            }
            return;
        }
        next = y.getInt("next", 1);
        // Upgrade-Stände aus der Zeit mit 4 bis 6 Stufen auf Level 0 bis 20 umrechnen (gleiche Wirkung)
        int stored = y.getInt("upgrade-version", 1);
        boolean migrate = stored < UpgradeLevels.VERSION && levelsReady();
        upgradeVersion = migrate ? UpgradeLevels.VERSION : stored;
        if (stored < UpgradeLevels.VERSION && !migrate) {
            addon.getLogger().warning("islands.yml: Upgrades noch im alten Format, die config hat aber keine 20-Level-Listen "
                    + "(insel.groessen, insel.mitglieder, insel.upgrades.*, generator.stufen). Stände bleiben unverändert.");
        }
        ConfigurationSection root = y.getConfigurationSection("islands");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(k);
            if (s == null) continue;
            try {
                Island i = new Island(Integer.parseInt(k));
                String o = s.getString("owner");
                i.owner = o == null || o.isEmpty() ? null : UUID.fromString(o);
                for (String m : s.getStringList("members")) i.members.add(UUID.fromString(m));
                for (String m : s.getStringList("co-owners")) i.coOwners.add(UUID.fromString(m));
                for (String m : s.getStringList("trusted")) i.trusted.add(UUID.fromString(m));
                for (String m : s.getStringList("banned")) i.banned.add(UUID.fromString(m));
                ConfigurationSection rs = s.getConfigurationSection("rights");
                if (rs != null) {
                    for (Role role : EDITABLE) {
                        if (!rs.contains(role.name().toLowerCase(Locale.ROOT))) continue;
                        java.util.EnumSet<Right> set = java.util.EnumSet.noneOf(Right.class);
                        for (String r : rs.getStringList(role.name().toLowerCase(Locale.ROOT))) {
                            try {
                                set.add(Right.valueOf(r.toUpperCase(Locale.ROOT)));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        i.rights.put(role, set);
                    }
                }
                i.name = s.getString("name", "Insel");
                i.template = s.getString("template", "");
                i.open = s.getBoolean("open", true);
                i.size = s.getInt("size");
                i.slots = s.getInt("slots");
                i.generator = s.getInt("generator");
                i.growth = s.getInt("growth");
                i.spawn = s.getInt("spawn");
                i.limit = s.getInt("limit");
                i.stage = s.getInt("stufe");
                i.built = s.getInt("stufe-gebaut");
                if (migrate) {
                    i.size = UpgradeLevels.islandSize(i.size);
                    i.slots = UpgradeLevels.islandSlots(i.slots);
                    i.generator = UpgradeLevels.islandGenerator(i.generator);
                    i.growth = UpgradeLevels.islandBonus(i.growth);
                    i.spawn = UpgradeLevels.islandBonus(i.spawn);
                    i.limit = UpgradeLevels.islandBonus(i.limit);
                    dirty = true;
                }
                i.points = s.getLong("points");
                i.created = s.getLong("created");
                i.lastReset = s.getLong("reset");
                String h = s.getString("home");
                if (h != null && !h.isEmpty()) {
                    String[] p = h.split(";");
                    i.home = new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                            Double.parseDouble(p[3]), Double.parseDouble(p[4])};
                }
                String lg = s.getString("legacy");
                if (lg != null && !lg.isEmpty()) {
                    String[] p = lg.split(";");
                    i.legacy = new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
                }
                if (i.owner != null) register(i);
            } catch (RuntimeException ex) {
                addon.getLogger().warning("islands.yml: Insel " + k + " übersprungen (" + ex.getMessage() + ")");
            }
        }
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        out.set("version", 2);
        out.set("upgrade-version", upgradeVersion);
        out.set("next", next);
        for (Island i : bySlot.values()) {
            String b = "islands." + i.slot + ".";
            out.set(b + "owner", i.owner == null ? "" : i.owner.toString());
            List<String> m = new ArrayList<>();
            for (UUID u : i.members) m.add(u.toString());
            out.set(b + "members", m);
            out.set(b + "co-owners", i.coOwners.stream().map(UUID::toString).toList());
            out.set(b + "trusted", i.trusted.stream().map(UUID::toString).toList());
            out.set(b + "banned", i.banned.stream().map(UUID::toString).toList());
            for (Role role : EDITABLE) {
                out.set(b + "rights." + role.name().toLowerCase(Locale.ROOT), i.rights.getOrDefault(role, java.util.EnumSet.noneOf(Right.class))
                        .stream().map(r -> r.name().toLowerCase(Locale.ROOT)).toList());
            }
            out.set(b + "name", i.name);
            out.set(b + "template", i.template);
            out.set(b + "open", i.open);
            out.set(b + "size", i.size);
            out.set(b + "slots", i.slots);
            out.set(b + "generator", i.generator);
            out.set(b + "growth", i.growth);
            out.set(b + "spawn", i.spawn);
            out.set(b + "limit", i.limit);
            if (i.stage > 0) out.set(b + "stufe", i.stage);
            if (i.built > 0) out.set(b + "stufe-gebaut", i.built);
            out.set(b + "points", i.points);
            out.set(b + "created", i.created);
            out.set(b + "reset", i.lastReset);
            if (i.home != null) out.set(b + "home", String.format(Locale.ROOT, "%.2f;%.2f;%.2f;%.1f;%.1f", i.home[0], i.home[1], i.home[2],
                    i.home[3], i.home[4]));
            if (i.legacy != null) out.set(b + "legacy", i.legacy[0] + ";" + i.legacy[1]);
        }
        DataFiles.save(addon, out, file);
        dirty = false;
    }
}

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

    public static final class Island {
        public final int slot;
        public UUID owner;
        public final Set<UUID> members = new LinkedHashSet<>();
        public String name = "Insel";
        public String template = "";
        public boolean open = true;
        public int size, slots, generator;
        public long points;
        public long created, lastReset, lastLevel;
        /** Zuhause: Welt-Koordinaten x, y, z, yaw, pitch (null = Spawn der Vorlage). */
        public double[] home;
        /** Alte Lage (vor der Spirale), sonst null. */
        public int[] legacy;

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

    private static final int HEIGHT = 100;

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
        if (sizes.isEmpty()) sizes = List.of(15, 25, 35, 50, 70);
        int r = sizes.get(Math.max(0, Math.min(level, sizes.size() - 1)));
        return Math.min(r, spacing() / 2 - 5);
    }

    public int maxSize() {
        List<Integer> sizes = addon.getConfig().getIntegerList("insel.groessen");
        return (sizes.isEmpty() ? 5 : sizes.size()) - 1;
    }

    public int radius(Island i) {
        return radius(i.size);
    }

    /** Insel an dieser Stelle (innerhalb ihres Radius), sonst null. */
    public Island islandAt(Location l) {
        if (l == null || l.getWorld() == null || !l.getWorld().getName().equals(core.modes().worldOf(ModeService.SKYBLOCK))) return null;
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

    /** Darf der Spieler hier bauen? Mitglieder auf ihrer Insel, Admins überall; außerhalb von Inseln niemand sonst. */
    public boolean canBuild(Player p, Location l) {
        if (p.hasPermission("kuby.admin")) return true;
        Island i = islandAt(l);
        return i != null && i.isMember(p.getUniqueId()) && !busy.contains(i.slot);
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
        if (!i.open && !i.isMember(p.getUniqueId()) && !p.hasPermission("kuby.admin")) return "Diese Insel ist für Besucher geschlossen.";
        home(p, i);
        Msg.send(p, "<gray>Du besuchst <white>" + Msg.escape(i.name) + "<gray>.");
        return null;
    }

    public String toggleOpen(Player p) {
        Island i = islandOf(p.getUniqueId());
        if (i == null) return "Du hast keine Insel.";
        if (!p.getUniqueId().equals(i.owner)) return "Nur der Besitzer kann das ändern.";
        i.open = !i.open;
        dirty = true;
        if (!i.open) {
            for (Player v : world().getPlayers()) {
                if (!i.isMember(v.getUniqueId()) && islandAt(v.getLocation()) == i && !v.hasPermission("kuby.admin")) {
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
        if (!p.getUniqueId().equals(i.owner)) return "Nur der Besitzer kann die Insel umbenennen.";
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
        if (l.isEmpty()) l = List.of(2, 4, 6, 10);
        return l.get(Math.max(0, Math.min(i.slots, l.size() - 1)));
    }

    public int maxSlots() {
        List<Integer> l = addon.getConfig().getIntegerList("insel.mitglieder");
        return (l.isEmpty() ? 4 : l.size()) - 1;
    }

    public String invite(Player p, Player target) {
        Island i = islandOf(p.getUniqueId());
        if (i == null) return "Du hast keine Insel.";
        if (!p.getUniqueId().equals(i.owner)) return "Nur der Besitzer kann einladen.";
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
        if (!p.getUniqueId().equals(i.owner)) return "Nur der Besitzer kann Mitglieder entfernen.";
        if (!i.members.remove(target)) return "Dieser Spieler ist kein Mitglied.";
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
            i.owner = heir;
            byPlayer.remove(p.getUniqueId());
            dirty = true;
            Player h = Bukkit.getPlayer(heir);
            if (h != null) Msg.send(h, "<gold>Du bist jetzt Besitzer von " + Msg.escape(i.name) + ".");
            if (islandAt(p.getLocation()) == i) p.teleportAsync(core.modes().spawnOf(ModeService.SKYBLOCK));
            return null;
        }
        unregister(i);
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

    public enum Upgrade { SIZE, SLOTS, GENERATOR }

    public int level(Island i, Upgrade u) {
        return switch (u) {
            case SIZE -> i.size;
            case SLOTS -> i.slots;
            case GENERATOR -> i.generator;
        };
    }

    public int maxLevel(Upgrade u) {
        return switch (u) {
            case SIZE -> maxSize();
            case SLOTS -> maxSlots();
            case GENERATOR -> Math.max(0, generatorLevels().size() - 1);
        };
    }

    /** Preis für die nächste Stufe (-1 = schon höchste). */
    public long price(Island i, Upgrade u) {
        int next = level(i, u) + 1;
        if (next > maxLevel(u)) return -1;
        if (u == Upgrade.GENERATOR) return generatorLevels().get(next).cost();
        List<Long> l = addon.getConfig().getLongList(u == Upgrade.SIZE ? "insel.upgrades.groesse" : "insel.upgrades.mitglieder");
        if (l.isEmpty()) l = u == Upgrade.SIZE ? List.of(0L, 25_000L, 75_000L, 200_000L, 500_000L) : List.of(0L, 10_000L, 40_000L, 100_000L);
        return l.get(Math.min(next, l.size() - 1));
    }

    public String upgrade(Player p, Upgrade u) {
        Island i = islandOf(p.getUniqueId());
        if (i == null) return "Du hast keine Insel.";
        long price = price(i, u);
        if (price < 0) return "Das ist schon die höchste Stufe.";
        if (!core.economy().withdraw(p.getUniqueId(), price)) return "Dafür brauchst du " + Msg.coins(price) + " $.";
        switch (u) {
            case SIZE -> i.size++;
            case SLOTS -> i.slots++;
            case GENERATOR -> i.generator++;
        }
        dirty = true;
        for (UUID m : i.everyone()) {
            Player o = Bukkit.getPlayer(m);
            if (o != null) Msg.send(o, "<green>Insel-Upgrade: <white>" + switch (u) {
                case SIZE -> "Größe " + (2 * radius(i) + 1) + " x " + (2 * radius(i) + 1);
                case SLOTS -> memberLimit(i) + " Mitglieder";
                case GENERATOR -> "Generator Stufe " + (i.generator + 1);
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

    /** Gesetzter oder abgebauter Block auf einer Insel. */
    public void addPoints(Location l, Material m, int sign) {
        Island i = islandAt(l);
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

    public int rank(Island i) {
        List<Island> l = top(Integer.MAX_VALUE);
        return l.indexOf(i) + 1;
    }

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
                i.name = s.getString("name", "Insel");
                i.template = s.getString("template", "");
                i.open = s.getBoolean("open", true);
                i.size = s.getInt("size");
                i.slots = s.getInt("slots");
                i.generator = s.getInt("generator");
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
        out.set("next", next);
        for (Island i : bySlot.values()) {
            String b = "islands." + i.slot + ".";
            out.set(b + "owner", i.owner == null ? "" : i.owner.toString());
            List<String> m = new ArrayList<>();
            for (UUID u : i.members) m.add(u.toString());
            out.set(b + "members", m);
            out.set(b + "name", i.name);
            out.set(b + "template", i.template);
            out.set(b + "open", i.open);
            out.set(b + "size", i.size);
            out.set(b + "slots", i.slots);
            out.set(b + "generator", i.generator);
            out.set(b + "points", i.points);
            out.set(b + "created", i.created);
            out.set(b + "reset", i.lastReset);
            if (i.home != null) out.set(b + "home", String.format(Locale.ROOT, "%.2f;%.2f;%.2f;%.1f;%.1f", i.home[0], i.home[1], i.home[2],
                    i.home[3], i.home[4]));
            if (i.legacy != null) out.set(b + "legacy", i.legacy[0] + ";" + i.legacy[1]);
        }
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
            dirty = false;
        } catch (IOException e) {
            addon.getLogger().warning("islands.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}

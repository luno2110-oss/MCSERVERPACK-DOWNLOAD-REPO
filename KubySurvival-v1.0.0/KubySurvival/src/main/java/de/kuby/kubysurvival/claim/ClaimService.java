package de.kuby.kubysurvival.claim;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.RegionService;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Landclaims in Survival: rechteckige Grundstücke über die volle Höhe. Claimblöcke: Startwert plus Zuwachs pro
 * Stunde Spielzeit in Survival, minus die Fläche aller eigenen Claims. Vertrauen gilt für alle Claims eines Besitzers.
 * Für schnelle Abfragen sind die Claims nach Chunks einsortiert.
 */
public final class ClaimService {

    /** Ein Claim. Ecken inklusive. */
    public record Claim(String id, UUID owner, String world, int x1, int z1, int x2, int z2) {
        public boolean contains(Location l) {
            return l.getWorld() != null && l.getWorld().getName().equals(world)
                    && l.getBlockX() >= x1 && l.getBlockX() <= x2 && l.getBlockZ() >= z1 && l.getBlockZ() <= z2;
        }

        public int width() {
            return x2 - x1 + 1;
        }

        public int length() {
            return z2 - z1 + 1;
        }

        public long area() {
            return (long) width() * length();
        }

        boolean overlaps(String w, int ax1, int az1, int ax2, int az2) {
            return world.equals(w) && ax1 <= x2 && ax2 >= x1 && az1 <= z2 && az2 >= z1;
        }
    }

    private static final String SURVIVAL = "survival";

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File file;
    private final List<Claim> claims = new ArrayList<>();
    private final Map<String, Map<Long, List<Claim>>> index = new HashMap<>();
    private final Map<UUID, Double> earned = new HashMap<>();
    private final Map<UUID, Set<UUID>> trust = new HashMap<>();
    /** Was Fremde auf allen Grundstücken eines Besitzers dürfen: tueren, knoepfe, kisten, tiere. */
    private final Map<UUID, Set<String>> flags = new HashMap<>();

    public static final List<String> FLAGS = List.of("tueren", "knoepfe", "kisten", "tiere");

    public ClaimService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "claims.yml");
        load();
        reindex();
    }

    // ---------- Abfragen ----------

    public synchronized Claim at(Location l) {
        if (l == null || l.getWorld() == null) return null;
        Map<Long, List<Claim>> byChunk = index.get(l.getWorld().getName());
        if (byChunk == null) return null;
        List<Claim> list = byChunk.get(key(l.getBlockX() >> 4, l.getBlockZ() >> 4));
        if (list == null) return null;
        for (Claim c : list) if (c.contains(l)) return c;
        return null;
    }

    /** Darf der Spieler hier bauen und Dinge benutzen? */
    public boolean canBuild(Player p, Location l) {
        Claim c = at(l);
        if (c == null) return true;
        if (p.hasPermission("kuby.admin")) return true;
        return isTrusted(c.owner(), p.getUniqueId());
    }

    public synchronized boolean isTrusted(UUID owner, UUID who) {
        return owner.equals(who) || trust.getOrDefault(owner, Set.of()).contains(who);
    }

    public synchronized List<Claim> claimsOf(UUID owner) {
        List<Claim> out = new ArrayList<>();
        for (Claim c : claims) if (c.owner().equals(owner)) out.add(c);
        return out;
    }

    public synchronized boolean allows(UUID owner, String flag) {
        return flags.getOrDefault(owner, Set.of()).contains(flag);
    }

    public synchronized boolean toggle(UUID owner, String flag) {
        Set<String> set = flags.computeIfAbsent(owner, k -> new HashSet<>());
        boolean on = !set.remove(flag);
        if (on) set.add(flag);
        save();
        return on;
    }

    /** Zusätzliche Claimblöcke (Token-Shop, Farmpass). */
    public synchronized void addBlocks(UUID owner, long blocks) {
        earned.merge(owner, (double) blocks, Double::sum);
        save();
    }

    public synchronized Set<UUID> trusted(UUID owner) {
        return new HashSet<>(trust.getOrDefault(owner, Set.of()));
    }

    public long startBlocks() {
        return core.getConfig().getLong("landclaim.start-blocks", 500);
    }

    public synchronized long used(UUID owner) {
        long sum = 0;
        for (Claim c : claims) if (c.owner().equals(owner)) sum += c.area();
        return sum;
    }

    /** Freie Claimblöcke. */
    public synchronized long available(UUID owner) {
        return startBlocks() + (long) Math.floor(earned.getOrDefault(owner, 0.0)) - used(owner);
    }

    // ---------- Ändern ----------

    /** Legt einen Claim zwischen zwei Ecken an. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public synchronized String create(Player p, Location a, Location b) {
        UUID id = p.getUniqueId();
        if (a == null || b == null) return "Markiere erst zwei Ecken mit der Claim-Axt (/claim axt).";
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return "Beide Ecken müssen in derselben Welt liegen.";
        String world = a.getWorld().getName();
        if (!world.equals(core.modes().worldOf(SURVIVAL))) return "Claims gibt es nur in der Survival-Welt.";
        int x1 = Math.min(a.getBlockX(), b.getBlockX());
        int z1 = Math.min(a.getBlockZ(), b.getBlockZ());
        int x2 = Math.max(a.getBlockX(), b.getBlockX());
        int z2 = Math.max(a.getBlockZ(), b.getBlockZ());
        int minSide = core.getConfig().getInt("landclaim.min-side", 5);
        int maxSide = core.getConfig().getInt("landclaim.max-side", 128);
        int w = x2 - x1 + 1;
        int l = z2 - z1 + 1;
        if (w < minSide || l < minSide) return "Ein Claim muss mindestens " + minSide + "x" + minSide + " Blöcke groß sein.";
        if (w > maxSide || l > maxSide) return "Ein Claim darf höchstens " + maxSide + " Blöcke lang sein.";
        int max = core.getConfig().getInt("landclaim.max-claims", 10);
        if (claimsOf(id).size() >= max) return "Du hast schon " + max + " Claims. Lösch erst einen.";
        long area = (long) w * l;
        long free = available(id);
        if (area > free) return "Dafür brauchst du " + area + " Claimblöcke, du hast " + free + ".";
        for (Claim c : claims) {
            if (c.overlaps(world, x1, z1, x2, z2)) {
                String owner = Bukkit.getOfflinePlayer(c.owner()).getName();
                return "Das überschneidet sich mit dem Claim von " + (owner == null ? "jemandem" : owner) + ".";
            }
        }
        for (RegionService.Region r : core.regions().all()) {
            if (r.world().equals(world) && x1 <= r.x2() && x2 >= r.x1() && z1 <= r.z2() && z2 >= r.z1()) {
                return "Das überschneidet sich mit einem geschützten Bereich (" + r.id() + ").";
            }
        }
        claims.add(new Claim(UUID.randomUUID().toString().substring(0, 8), id, world, x1, z1, x2, z2));
        reindex();
        save();
        return null;
    }

    /** Ändert die Größe eines eigenen Claims (Ecke verschieben). Null bei Erfolg, sonst eine Meldung. */
    public synchronized String resize(Player p, Claim old, int nx1, int nz1, int nx2, int nz2) {
        int index = claims.indexOf(old);
        if (index < 0) return "Dieses Grundstück gibt es nicht mehr.";
        int x1 = Math.min(nx1, nx2), x2 = Math.max(nx1, nx2), z1 = Math.min(nz1, nz2), z2 = Math.max(nz1, nz2);
        int minSide = core.getConfig().getInt("landclaim.min-side", 5);
        int maxSide = core.getConfig().getInt("landclaim.max-side", 128);
        int w = x2 - x1 + 1, l = z2 - z1 + 1;
        if (w < minSide || l < minSide) return "Ein Claim muss mindestens " + minSide + "x" + minSide + " Blöcke groß sein.";
        if (w > maxSide || l > maxSide) return "Ein Claim darf höchstens " + maxSide + " Blöcke lang sein.";
        long extra = (long) w * l - old.area();
        long free = available(old.owner());
        if (extra > free) return "Dafür brauchst du " + extra + " Claimblöcke mehr, du hast " + free + ".";
        for (Claim c : claims) {
            if (c != old && c.overlaps(old.world(), x1, z1, x2, z2)) return "Das überschneidet sich mit einem anderen Claim.";
        }
        for (RegionService.Region r : core.regions().all()) {
            if (r.world().equals(old.world()) && x1 <= r.x2() && x2 >= r.x1() && z1 <= r.z2() && z2 >= r.z1()) {
                return "Das überschneidet sich mit einem geschützten Bereich (" + r.id() + ").";
            }
        }
        claims.set(index, new Claim(old.id(), old.owner(), old.world(), x1, z1, x2, z2));
        reindex();
        save();
        return null;
    }

    public synchronized void delete(Claim c) {
        claims.remove(c);
        reindex();
        save();
    }

    public synchronized boolean trust(UUID owner, UUID who) {
        boolean added = trust.computeIfAbsent(owner, k -> new HashSet<>()).add(who);
        if (added) save();
        return added;
    }

    public synchronized boolean untrust(UUID owner, UUID who) {
        Set<UUID> set = trust.get(owner);
        boolean removed = set != null && set.remove(who);
        if (removed) save();
        return removed;
    }

    /** Jede Minute: Claimblöcke für alle, die gerade in Survival spielen. */
    public synchronized void tick() {
        double perMinute = core.getConfig().getDouble("landclaim.blocks-per-hour", 100) / 60.0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) earned.merge(p.getUniqueId(), perMinute, Double::sum);
        }
    }

    // ---------- Index und Speichern ----------

    private void reindex() {
        index.clear();
        for (Claim c : claims) {
            Map<Long, List<Claim>> byChunk = index.computeIfAbsent(c.world(), k -> new HashMap<>());
            for (int cx = c.x1() >> 4; cx <= c.x2() >> 4; cx++) {
                for (int cz = c.z1() >> 4; cz <= c.z2() >> 4; cz++) {
                    byChunk.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(c);
                }
            }
        }
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yml.getStringList("claims")) {
            String[] p = raw.split(";");
            if (p.length < 7) continue;
            try {
                claims.add(new Claim(p[0], UUID.fromString(p[1]), p[2], Integer.parseInt(p[3]), Integer.parseInt(p[4]),
                        Integer.parseInt(p[5]), Integer.parseInt(p[6])));
            } catch (IllegalArgumentException ignored) {
            }
        }
        ConfigurationSection e = yml.getConfigurationSection("earned");
        if (e != null) {
            for (String key : e.getKeys(false)) {
                try {
                    earned.put(UUID.fromString(key), e.getDouble(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection fl = yml.getConfigurationSection("flags");
        if (fl != null) {
            for (String owner : fl.getKeys(false)) {
                try {
                    flags.put(UUID.fromString(owner), new HashSet<>(fl.getStringList(owner)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection t = yml.getConfigurationSection("trust");
        if (t != null) {
            for (String owner : t.getKeys(false)) {
                try {
                    Set<UUID> set = new HashSet<>();
                    for (String who : t.getStringList(owner)) set.add(UUID.fromString(who));
                    trust.put(UUID.fromString(owner), set);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (Claim c : claims) {
            list.add(c.id() + ";" + c.owner() + ";" + c.world() + ";" + c.x1() + ";" + c.z1() + ";" + c.x2() + ";" + c.z2());
        }
        out.set("claims", list);
        for (Map.Entry<UUID, Double> e : earned.entrySet()) out.set("earned." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, Set<String>> e : flags.entrySet()) out.set("flags." + e.getKey(), new ArrayList<>(e.getValue()));
        for (Map.Entry<UUID, Set<UUID>> e : trust.entrySet()) {
            List<String> ids = new ArrayList<>();
            for (UUID who : e.getValue()) ids.add(who.toString());
            out.set("trust." + e.getKey(), ids);
        }
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            addon.getLogger().warning("claims.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}

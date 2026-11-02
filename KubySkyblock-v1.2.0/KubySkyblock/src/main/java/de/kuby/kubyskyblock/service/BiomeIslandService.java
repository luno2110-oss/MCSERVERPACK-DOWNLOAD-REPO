package de.kuby.kubyskyblock.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.IslandService.Island;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Snow;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Biom-Inseln: kleine, leere Inseln mit eigenem Biom (Dschungel, Wüste, Pilzland, Kirschhain …), verteilt auf der Map
 * zwischen den Spieler-Inseln. Dort spawnen die Tiere des Bioms (Mooshrooms, Pandas, Frösche, Füchse …), Pflanzen
 * wachsen wie im Biom. Eine Insel kann eine Biom-Insel beanspruchen (Preis, eine je Insel): dann gehört sie zur Insel
 * (gleiche Rechte, Schutz und Limits). War lange kein Mitglied online (biom-inseln.inaktiv-tage), wird sie wieder frei.
 * <p>
 * Lage: an den Ecken des Insel-Rasters (zwischen vier Spieler-Inseln), damit sie nie mit einer Insel überlappen.
 * Daten: biomes.yml.
 */
public final class BiomeIslandService {

    /** Eine Biom-Insel. owner = Platz der Insel, die sie beansprucht hat (0 = frei). */
    public static final class BIsland {
        public final int id;
        public final String biome;
        public final int x, z, y, radius;
        public int owner;
        public long since, active;

        BIsland(int id, String biome, int x, int y, int z, int radius) {
            this.id = id;
            this.biome = biome;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }
    }

    /** Biome, die gebaut werden können: Schlüssel → Anzeigename. */
    public static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put("jungle", "Dschungel");
        NAMES.put("desert", "Wüste");
        NAMES.put("mushroom_fields", "Pilzland");
        NAMES.put("cherry_grove", "Kirschhain");
        NAMES.put("swamp", "Sumpf");
        NAMES.put("snowy_taiga", "Verschneite Taiga");
        NAMES.put("savanna", "Savanne");
        NAMES.put("mangrove_swamp", "Mangroven");
        NAMES.put("dark_forest", "Dunkelwald");
        NAMES.put("badlands", "Tafelberge");
        NAMES.put("flower_forest", "Blumenwald");
        NAMES.put("bamboo_jungle", "Bambusdschungel");
        NAMES.put("pale_garden", "Blasser Garten");
    }

    public static String label(String biome) {
        return NAMES.getOrDefault(biome, biome);
    }

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final File file;
    private final Map<Integer, BIsland> byId = new LinkedHashMap<>();
    private final Map<Long, BIsland> byCorner = new HashMap<>();
    private int next = 1;
    private boolean dirty;

    public BiomeIslandService(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "biomes.yml");
        load();
    }

    public List<BIsland> all() {
        return new ArrayList<>(byId.values());
    }

    public BIsland byId(int id) {
        return byId.get(id);
    }

    /** Biom-Inseln einer Spieler-Insel. */
    public List<BIsland> of(int slot) {
        List<BIsland> out = new ArrayList<>();
        for (BIsland b : byId.values()) if (b.owner == slot) out.add(b);
        return out;
    }

    private static long key(int kx, int kz) {
        return ((long) kx << 32) ^ (kz & 0xffffffffL);
    }

    /** Biom-Insel an dieser Stelle (nur Nachschlagen in einer Map, schnell). */
    public BIsland at(Location l) {
        if (byCorner.isEmpty() || l.getWorld() == null) return null;
        World w = addon.islands().world();
        if (w == null || l.getWorld() != w) return null;
        int s = addon.islands().spacing();
        BIsland b = byCorner.get(key(Math.floorDiv(l.getBlockX(), s), Math.floorDiv(l.getBlockZ(), s)));
        if (b == null) return null;
        return Math.abs(l.getBlockX() - b.x) <= b.radius && Math.abs(l.getBlockZ() - b.z) <= b.radius ? b : null;
    }

    public int price() {
        return Math.max(0, addon.getConfig().getInt("biom-inseln.preis", 75_000));
    }

    public int perIsland() {
        return Math.max(0, addon.getConfig().getInt("biom-inseln.je-insel", 1));
    }

    // =================================================================================================================
    // Beanspruchen
    // =================================================================================================================

    public String claim(Player p, int id) {
        BIsland b = byId.get(id);
        if (b == null) return "Diese Biom-Insel gibt es nicht.";
        IslandService is = addon.islands();
        Island i = is.islandOf(p.getUniqueId());
        if (i == null) return "Du brauchst zuerst eine eigene Insel.";
        if (!is.has(i, p.getUniqueId(), IslandService.Right.UPGRADES)) return "Das dürfen nur Besitzer, Co-Besitzer und Mitglieder mit dem Recht „Upgrades“.";
        if (b.owner == i.slot) return "Die gehört schon deiner Insel.";
        if (b.owner > 0 && is.bySlot(b.owner) != null) return "Diese Biom-Insel gehört schon " + is.bySlot(b.owner).name + ".";
        if (of(i.slot).size() >= perIsland()) return "Deine Insel hat schon " + perIsland() + " Biom-Insel" + (perIsland() == 1 ? "" : "n") + ". Gib sie zuerst frei.";
        if (!core.economy().withdraw(p.getUniqueId(), price())) return "Dafür brauchst du " + Msg.coins(price()) + " $.";
        b.owner = i.slot;
        b.since = b.active = System.currentTimeMillis();
        dirty = true;
        save();
        for (UUID u : i.everyone()) {
            Player o = Bukkit.getPlayer(u);
            if (o != null) Msg.send(o, "<green>Die Biom-Insel <white>" + label(b.biome) + " #" + b.id + " <green>gehört jetzt zu deiner Insel.");
        }
        Sfx.play(core, p, Sfx.Kind.SUCCESS_BIG);
        return null;
    }

    public String release(Player p, int id) {
        BIsland b = byId.get(id);
        Island i = addon.islands().islandOf(p.getUniqueId());
        if (b == null || i == null || b.owner != i.slot) return "Diese Biom-Insel gehört nicht deiner Insel.";
        if (!addon.islands().canManage(i, p.getUniqueId())) return "Freigeben dürfen nur Besitzer und Co-Besitzer.";
        b.owner = 0;
        dirty = true;
        save();
        return null;
    }

    /** Insel aufgegeben: ihre Biom-Inseln werden frei. */
    public void releaseAll(int slot) {
        for (BIsland b : byId.values()) {
            if (b.owner == slot) {
                b.owner = 0;
                dirty = true;
            }
        }
    }

    /** Stündlich: aktive Inseln merken, lange inaktive Biom-Inseln freigeben. */
    public void tick() {
        long now = System.currentTimeMillis(), max = addon.getConfig().getLong("biom-inseln.inaktiv-tage", 14) * 86_400_000L;
        for (BIsland b : byId.values()) {
            if (b.owner <= 0) continue;
            Island i = addon.islands().bySlot(b.owner);
            if (i == null) {
                b.owner = 0;
                dirty = true;
                continue;
            }
            if (addon.islands().online(i) > 0) {
                b.active = now;
                dirty = true;
            } else if (max > 0 && now - b.active > max) {
                b.owner = 0;
                dirty = true;
                addon.getLogger().info("Biom-Insel #" + b.id + " ist wieder frei (Insel " + i.name + " inaktiv).");
            }
        }
        saveIfDirty();
    }

    /** Hinfliegen: auf die Mitte der Biom-Insel (Betreten-Regeln der Besitzer-Insel gelten). */
    public String teleport(Player p, int id) {
        BIsland b = byId.get(id);
        World w = addon.islands().world();
        if (b == null || w == null) return "Diese Biom-Insel gibt es nicht.";
        Island i = b.owner > 0 ? addon.islands().bySlot(b.owner) : null;
        if (i != null && !addon.islands().mayEnter(p, i)) return i.banned.contains(p.getUniqueId()) ? "Du bist auf dieser Insel gesperrt." : "Diese Biom-Insel ist für Besucher geschlossen.";
        int y = w.getHighestBlockYAt(b.x, b.z);
        p.setFallDistance(0);
        p.teleportAsync(new Location(w, b.x + 0.5, Math.max(y, b.y) + 1, b.z + 0.5, p.getLocation().getYaw(), 0));
        Msg.send(p, "<gray>Du bist auf der Biom-Insel <white>" + label(b.biome) + " #" + b.id + "<gray>.");
        return null;
    }

    // =================================================================================================================
    // Bauen
    // =================================================================================================================

    /** Freie Ecken des Rasters auf einem Ring um den Spawn (gleichmäßig verteilt). */
    private List<int[]> corners(int count) {
        int s = addon.islands().spacing();
        double dist = Math.max(s * 2, addon.getConfig().getDouble("biom-inseln.abstand-vom-spawn", 1500));
        List<int[]> out = new ArrayList<>();
        int total = byId.size() + count;
        for (int k = 0; out.size() < count && k < total * 8; k++) {
            double a = 2 * Math.PI * (k + 0.37) / total + (k / total) * 0.21;
            double d = dist + (k / total) * s;
            int kx = Math.floorDiv((int) Math.round(Math.cos(a) * d), s), kz = Math.floorDiv((int) Math.round(Math.sin(a) * d), s);
            if (byCorner.containsKey(key(kx, kz))) continue;
            boolean dup = false;
            for (int[] o : out) if (o[0] == kx && o[1] == kz) dup = true;
            if (!dup) out.add(new int[]{kx, kz});
        }
        return out;
    }

    /** Baut count neue Biom-Inseln (eine je Tick). Biome reihum aus biom-inseln.biome. */
    public void generate(int count, Runnable done) {
        World w = addon.islands().world();
        if (w == null || count <= 0) {
            if (done != null) done.run();
            return;
        }
        List<String> biomes = addon.getConfig().getStringList("biom-inseln.biome");
        if (biomes.isEmpty()) biomes = new ArrayList<>(NAMES.keySet());
        List<String> pool = new ArrayList<>();
        for (String b : biomes) if (biome(b) != null) pool.add(b.toLowerCase(Locale.ROOT));
        if (pool.isEmpty()) pool.add("jungle");
        List<int[]> cs = corners(count);
        int s = addon.islands().spacing(), r = Math.max(5, Math.min(addon.getConfig().getInt("biom-inseln.radius", 9), s / 2 - addon.islands().radius(addon.islands().maxSize()) - 2));
        List<String> fp = pool;
        int[] at = {0};
        Bukkit.getScheduler().runTaskTimer(addon, task -> {
            if (at[0] >= cs.size()) {
                task.cancel();
                save();
                if (done != null) done.run();
                return;
            }
            int[] c = cs.get(at[0]);
            String bio = fp.get((byId.size()) % fp.size());
            int x = c[0] * s + s / 2, z = c[1] * s + s / 2;
            Random rnd = new Random(31L * x + z);
            int y = 92 + rnd.nextInt(10);
            BIsland b = new BIsland(next++, bio, x, y, z, r);
            build(w, b, rnd);
            byId.put(b.id, b);
            byCorner.put(key(c[0], c[1]), b);
            dirty = true;
            at[0]++;
        }, 1L, 1L);
    }

    static Biome biome(String key) {
        return Registry.BIOME.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
    }

    /** Eine Biom-Insel bauen: Biom setzen (auch ringsum, damit Tiere passend spawnen), Erde, Oberfläche, Pflanzen. */
    private void build(World w, BIsland b, Random r) {
        Biome bio = biome(b.biome);
        int R = b.radius;
        if (bio != null) {
            int br = R + 6;
            for (int x = b.x - br; x <= b.x + br; x += 4) {
                for (int z = b.z - br; z <= b.z + br; z += 4) {
                    for (int y = b.y - 24; y <= b.y + 40; y += 4) w.setBiome(x, y, z, bio);
                }
            }
        }
        Material top, under, deep;
        switch (b.biome) {
            case "desert" -> {
                top = Material.SAND;
                under = Material.SAND;
                deep = Material.SANDSTONE;
            }
            case "badlands" -> {
                top = Material.RED_SAND;
                under = Material.ORANGE_TERRACOTTA;
                deep = Material.TERRACOTTA;
            }
            case "mushroom_fields" -> {
                top = Material.MYCELIUM;
                under = Material.DIRT;
                deep = Material.STONE;
            }
            case "mangrove_swamp" -> {
                top = Material.MUD;
                under = Material.MUD;
                deep = Material.CLAY;
            }
            case "bamboo_jungle" -> {
                top = Material.PODZOL;
                under = Material.DIRT;
                deep = Material.STONE;
            }
            case "pale_garden" -> {
                top = Material.PALE_MOSS_BLOCK;
                under = Material.DIRT;
                deep = Material.STONE;
            }
            default -> {
                top = Material.GRASS_BLOCK;
                under = Material.DIRT;
                deep = Material.STONE;
            }
        }
        Material[] bands = {Material.ORANGE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.WHITE_TERRACOTTA, Material.RED_TERRACOTTA, Material.TERRACOTTA};
        for (int dx = -R - 1; dx <= R + 1; dx++) {
            for (int dz = -R - 1; dz <= R + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                double edge = R + Math.sin(Math.atan2(dz, dx) * 4 + b.id) * 0.9;
                if (d > edge) continue;
                int x = b.x + dx, z = b.z + dz;
                w.getBlockAt(x, b.y, z).setType(top, false);
                for (int y = 1; y <= 3; y++) w.getBlockAt(x, b.y - y, z).setType(b.biome.equals("badlands") ? bands[y % bands.length] : under, false);
                double depth = (edge - d) * 0.8 + r.nextDouble() * 1.5;
                for (int y = 4; y <= 3 + depth; y++) {
                    w.getBlockAt(x, b.y - y, z).setType(b.biome.equals("badlands") ? bands[y % bands.length] : r.nextInt(8) == 0 ? Material.ANDESITE : deep, false);
                }
            }
        }
        decorate(w, b, r);
    }

    private void plant(World w, int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        if (b.getType().isAir()) b.setType(m, false);
    }

    private void tall(World w, int x, int y, int z, Material m) {
        Block lo = w.getBlockAt(x, y, z), hi = w.getBlockAt(x, y + 1, z);
        if (!lo.getType().isAir() || !hi.getType().isAir()) return;
        lo.setType(m, false);
        hi.setType(m, false);
        if (lo.getBlockData() instanceof Bisected l && hi.getBlockData() instanceof Bisected h) {
            l.setHalf(Bisected.Half.BOTTOM);
            h.setHalf(Bisected.Half.TOP);
            lo.setBlockData(l, false);
            hi.setBlockData(h, false);
        }
    }

    /** Bäume und Pflanzen je Biom. */
    private void decorate(World w, BIsland b, Random r) {
        int y = b.y + 1, R = b.radius - 2;
        Location c = new Location(w, b.x, y, b.z);
        switch (b.biome) {
            case "jungle" -> {
                w.generateTree(c, TreeType.SMALL_JUNGLE);
                w.generateTree(c.clone().add(-4, 0, 3), TreeType.JUNGLE_BUSH);
                plant(w, b.x + 4, y, b.z - 2, Material.MELON);
                for (int k = 0; k < 6; k++) plant(w, b.x + r.nextInt(2 * R + 1) - R, y, b.z + r.nextInt(2 * R + 1) - R, Material.FERN);
            }
            case "desert" -> {
                for (int[] o : new int[][]{{3, 2}, {-3, -3}, {-4, 3}}) {
                    for (int k = 0; k < 2 + r.nextInt(2); k++) plant(w, b.x + o[0], y + k, b.z + o[1], Material.CACTUS);
                }
                for (int k = 0; k < 4; k++) plant(w, b.x + r.nextInt(2 * R + 1) - R, y, b.z + r.nextInt(2 * R + 1) - R, Material.DEAD_BUSH);
            }
            case "badlands" -> {
                for (int k = 0; k < 3; k++) plant(w, b.x + 3, y + k, b.z - 2, Material.CACTUS);
                for (int k = 0; k < 4; k++) plant(w, b.x + r.nextInt(2 * R + 1) - R, y, b.z + r.nextInt(2 * R + 1) - R, Material.DEAD_BUSH);
            }
            case "mushroom_fields" -> {
                w.generateTree(c, TreeType.RED_MUSHROOM);
                w.generateTree(c.clone().add(4, 0, -3), TreeType.BROWN_MUSHROOM);
                for (int k = 0; k < 5; k++) plant(w, b.x + r.nextInt(2 * R + 1) - R, y, b.z + r.nextInt(2 * R + 1) - R, r.nextBoolean() ? Material.RED_MUSHROOM : Material.BROWN_MUSHROOM);
            }
            case "cherry_grove" -> {
                w.generateTree(c, TreeType.CHERRY);
                for (int k = 0; k < 8; k++) plant(w, b.x + r.nextInt(2 * R + 1) - R, y, b.z + r.nextInt(2 * R + 1) - R, Material.PINK_PETALS);
            }
            case "swamp" -> {
                for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 1; dz++) w.getBlockAt(b.x + 3 + dx, b.y, b.z + dz).setType(Material.WATER, false);
                plant(w, b.x + 3, y, b.z, Material.LILY_PAD);
                w.generateTree(c.clone().add(-3, 0, -2), TreeType.SWAMP);
                plant(w, b.x - 1, y, b.z + 4, Material.BLUE_ORCHID);
            }
            case "mangrove_swamp" -> {
                for (int dx = -1; dx <= 1; dx++) w.getBlockAt(b.x + 4 + dx, b.y, b.z + 3).setType(Material.WATER, false);
                if (!w.generateTree(c, TreeType.MANGROVE)) w.generateTree(c, TreeType.TREE);
            }
            case "snowy_taiga" -> {
                w.generateTree(c, TreeType.REDWOOD);
                w.generateTree(c.clone().add(-4, 0, 3), TreeType.REDWOOD);
                for (int dx = -R; dx <= R; dx++) {
                    for (int dz = -R; dz <= R; dz++) {
                        Block t = w.getBlockAt(b.x + dx, y, b.z + dz);
                        if (t.getType().isAir() && w.getBlockAt(b.x + dx, b.y, b.z + dz).getType() == Material.GRASS_BLOCK) {
                            t.setType(Material.SNOW, false);
                            if (t.getBlockData() instanceof Snow s) {
                                s.setLayers(1);
                                t.setBlockData(s, false);
                            }
                        }
                    }
                }
                plant(w, b.x + 3, y, b.z - 3, Material.SWEET_BERRY_BUSH);
            }
            case "savanna" -> {
                w.generateTree(c, TreeType.ACACIA);
                for (int k = 0; k < 8; k++) plant(w, b.x + r.nextInt(2 * R + 1) - R, y, b.z + r.nextInt(2 * R + 1) - R, Material.SHORT_GRASS);
            }
            case "dark_forest" -> {
                w.generateTree(c, TreeType.DARK_OAK);
                plant(w, b.x + 4, y, b.z + 2, Material.RED_MUSHROOM);
                plant(w, b.x - 3, y, b.z - 4, Material.BROWN_MUSHROOM);
            }
            case "flower_forest" -> {
                w.generateTree(c, TreeType.BIRCH);
                Material[] fl = {Material.ALLIUM, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.RED_TULIP,
                        Material.ORANGE_TULIP, Material.PINK_TULIP, Material.AZURE_BLUET, Material.POPPY, Material.DANDELION};
                for (int k = 0; k < 14; k++) plant(w, b.x + r.nextInt(2 * R + 1) - R, y, b.z + r.nextInt(2 * R + 1) - R, fl[r.nextInt(fl.length)]);
                tall(w, b.x + 3, y, b.z + 3, Material.LILAC);
                tall(w, b.x - 3, y, b.z + 2, Material.PEONY);
            }
            case "bamboo_jungle" -> {
                for (int k = 0; k < 7; k++) {
                    int x = b.x + r.nextInt(2 * R + 1) - R, z = b.z + r.nextInt(2 * R + 1) - R, h = 3 + r.nextInt(5);
                    for (int t = 0; t < h; t++) plant(w, x, y + t, z, Material.BAMBOO);
                }
                w.generateTree(c.clone().add(3, 0, -3), TreeType.JUNGLE_BUSH);
            }
            case "pale_garden" -> {
                w.generateTree(c, TreeType.PALE_OAK_CREAKING);
                for (int k = 0; k < 6; k++) plant(w, b.x + r.nextInt(2 * R + 1) - R, y, b.z + r.nextInt(2 * R + 1) - R, Material.PALE_MOSS_CARPET);
            }
            default -> w.generateTree(c, TreeType.TREE);
        }
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        next = y.getInt("next", 1);
        ConfigurationSection root = y.getConfigurationSection("islands");
        if (root == null) return;
        int s = addon.islands().spacing();
        for (String k : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(k);
            if (c == null) continue;
            try {
                BIsland b = new BIsland(Integer.parseInt(k), c.getString("biome", "jungle"), c.getInt("x"), c.getInt("y", 95), c.getInt("z"), c.getInt("radius", 9));
                b.owner = c.getInt("owner");
                b.since = c.getLong("since");
                b.active = c.getLong("active");
                byId.put(b.id, b);
                byCorner.put(key(Math.floorDiv(b.x, s), Math.floorDiv(b.z, s)), b);
                next = Math.max(next, b.id + 1);
            } catch (RuntimeException ex) {
                addon.getLogger().warning("biomes.yml: " + k + " übersprungen");
            }
        }
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        YamlConfiguration out = new YamlConfiguration();
        out.set("next", next);
        for (BIsland b : byId.values()) {
            String p = "islands." + b.id + ".";
            out.set(p + "biome", b.biome);
            out.set(p + "x", b.x);
            out.set(p + "y", b.y);
            out.set(p + "z", b.z);
            out.set(p + "radius", b.radius);
            out.set(p + "owner", b.owner);
            out.set(p + "since", b.since);
            out.set(p + "active", b.active);
        }
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
            dirty = false;
        } catch (IOException e) {
            addon.getLogger().warning("biomes.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}

package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.service.WorldService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Angelwelt: Unter-Modus von Survival (gleiches Inventar, Geld und Angel-Profil). Eine eigene leere Welt mit einer
 * Spawn-Insel und acht Gewässern im Kreis darum, verbunden über Stege. Jedes Gewässer ist anders gut (Glück, Tempo,
 * größere Fische, Schätze, andere Fischarten) und manche lassen dich nur mit einer bestimmten Angel, einem aktiven
 * Köder und einem Angel-Level angeln. In der Overworld angelt man weiter wie gewohnt.
 *
 * Werte in der config unter angeln.welt (Name, automatisch anlegen) und angeln.welt.gewaesser.&lt;id&gt; (Name, angel,
 * koeder, level, glueck, tempo, gewicht, schatz). Die Welt ist geschützt (nur Admins bauen), Monster spawnen nicht,
 * wer herunterfällt, landet wieder am Spawn.
 */
public final class AngelWorld implements Listener {

    /** Aussehen eines Gewässers: Boden, Unterseite der Insel, Ufer, Grund, Wassertiefe und Deko. */
    public enum Look {
        TEICH(Material.GRASS_BLOCK, Material.DIRT, Material.GRASS_BLOCK, Material.SAND, 3),
        BERGSEE(Material.SNOW_BLOCK, Material.STONE, Material.PACKED_ICE, Material.GRAVEL, 4),
        MANGROVE(Material.MUD, Material.PACKED_MUD, Material.MUDDY_MANGROVE_ROOTS, Material.CLAY, 3),
        LAGUNE(Material.SAND, Material.SANDSTONE, Material.SMOOTH_SANDSTONE, Material.SAND, 3),
        TIEFSEE(Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE, Material.DARK_PRISMARINE, Material.DARK_PRISMARINE, 7),
        EISLOCH(Material.SNOW_BLOCK, Material.PACKED_ICE, Material.BLUE_ICE, Material.PACKED_ICE, 5),
        MYTHEN(Material.BLACKSTONE, Material.BASALT, Material.POLISHED_BLACKSTONE_BRICKS, Material.CRYING_OBSIDIAN, 5),
        STERNE(Material.END_STONE_BRICKS, Material.END_STONE, Material.PURPUR_BLOCK, Material.SEA_LANTERN, 5);

        final Material ground, under, rim, floor;
        final int depth;

        Look(Material ground, Material under, Material rim, Material floor, int depth) {
            this.ground = ground;
            this.under = under;
            this.rim = rim;
            this.floor = floor;
            this.depth = depth;
        }
    }

    /** Ein Gewässer: Fischarten (Gewässer-Typ), Boni und Voraussetzungen. */
    public static final class Spot {
        public final String id;
        public final Look look;
        public final int index;
        public String name;
        public Water water;
        /** nur Mythen-Fische (sonst die Arten des Gewässer-Typs). */
        public boolean mythosOnly;
        /** Mindest-Angel (null: jede Angel); RAINBOW heißt: nur Juwelen-Angeln. */
        public Rod rod;
        /** dieser Köder muss aktiv sein (null: keiner nötig). */
        public Bait bait;
        public int level;
        public double luck, faster, treasure;
        public int weight;
        public int color;
        public String iconFish;
        public int cx, cz;

        Spot(String id, Look look, int index) {
            this.id = id;
            this.look = look;
            this.index = index;
        }

        public TextColor textColor() {
            return TextColor.color(color);
        }

        /** Glück und Tempo, z. B. "Glück +12 % · Tempo +8 %". */
        public String bonus() {
            List<String> l = new ArrayList<>();
            if (luck > 0) l.add("Glück +" + AngelText.dec(luck) + " %");
            if (faster > 0) l.add("Tempo +" + AngelText.dec(faster) + " %");
            return l.isEmpty() ? "Für den Anfang" : String.join(" · ", l);
        }

        /** Größere Fische und Schätze, z. B. "größere Fische · Schatz +2 %" (leer, wenn keins). */
        public String extras() {
            List<String> l = new ArrayList<>();
            if (weight > 0) l.add(weight > 1 ? "viel größere Fische" : "größere Fische");
            if (treasure > 0) l.add("Schatz +" + AngelText.dec(treasure) + " %");
            return String.join(" · ", l);
        }

        /** Alle Boni in einer Zeile (Hologramm, Chat). */
        public String allBonus() {
            String x = extras();
            return x.isEmpty() ? bonus() : bonus() + " · " + x;
        }

        public String waterLabel() {
            return mythosOnly ? "Nur Mythen-Fische" : water.label;
        }
    }

    public static final int Y = 64;
    private static final int RING = 36, ISLAND = 11, POND = 6, SPAWN = 7;

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final List<Spot> spots = new ArrayList<>();
    private final NamespacedKey tag;
    /** Hologramme: Position und Text; die Anzeige wird nicht gespeichert und beim Laden des Chunks neu gesetzt. */
    private final List<Holo> holos = new ArrayList<>();

    private static final class Holo {
        final Location at;
        final Component text;
        TextDisplay live;

        Holo(Location at, Component text) {
            this.at = at;
            this.text = text;
        }
    }

    public AngelWorld(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.tag = new NamespacedKey(addon, "angelwelt");
        loadSpots();
        addon.getServer().getScheduler().runTask(addon, this::start);
        addon.getServer().getScheduler().runTaskTimer(addon, this::rescue, 40L, 20L);
    }

    // =================================================================================================================
    // Gewässer
    // =================================================================================================================

    private void preset(String id, Look look, String name, Water water, boolean mythosOnly, Rod rod, Bait bait,
                        int level, double luck, double faster, int weight, double treasure, int color, String fish) {
        Spot s = new Spot(id, look, spots.size());
        s.name = name;
        s.water = water;
        s.mythosOnly = mythosOnly;
        s.rod = rod;
        s.bait = bait;
        s.level = level;
        s.luck = luck;
        s.faster = faster;
        s.weight = weight;
        s.treasure = treasure;
        s.color = color;
        s.iconFish = fish;
        spots.add(s);
    }

    /** Die acht Gewässer, von leicht bis schwer; Werte aus angeln.welt.gewaesser.&lt;id&gt; überschreiben die Vorgaben. */
    public void loadSpots() {
        spots.clear();
        preset("dorfteich", Look.TEICH, "Dorfteich", Water.FLUSS, false, null, null, 1, 0, 0, 0, 0, 0x8CE07A, "rotauge");
        preset("bergsee", Look.BERGSEE, "Klarer Bergsee", Water.KALT, false, Rod.STEIN, null, 5, 5, 5, 0, 0, 0xB8E4FF,
                "saibling");
        preset("mangroven", Look.MANGROVE, "Mangroven-Sumpf", Water.SUMPF, false, Rod.KUPFER, Bait.REGENWURM, 8, 8, 5,
                0, 0.5, 0x9AC85A, "axolotl");
        preset("lagune", Look.LAGUNE, "Korallen-Lagune", Water.WARM, false, Rod.EISEN, null, 12, 10, 8, 0, 1, 0xFFD86A,
                "clownfisch");
        preset("tiefsee", Look.TIEFSEE, "Tiefsee-Graben", Water.OZEAN, false, Rod.DIAMANT, Bait.GOLD, 20, 12, 5, 2, 2,
                0x5AD8D0, "seeteufel");
        preset("eisloch", Look.EISLOCH, "Eisloch", Water.KALT, false, Rod.AMETHYST, Bait.NACHT, 25, 15, 10, 1, 1,
                0x9AD8FF, "groenlandhai");
        preset("mythensee", Look.MYTHEN, "Mythensee", Water.MYTHOS, true, Rod.NETHERITE, Bait.GLITZER, 35, 20, 10, 1, 2,
                0xC88AFF, "phoenixfisch");
        preset("sternenquelle", Look.STERNE, "Sternenquelle", Water.MYTHOS, true, Rod.RAINBOW, null, 40, 30, 15, 2, 3,
                0xFFE7A8, "ur_koi");
        ConfigurationSection c = core.getConfig().getConfigurationSection("angeln.welt.gewaesser");
        for (Spot s : spots) {
            double a = Math.PI * 2 * s.index / spots.size();
            s.cx = (int) Math.round(Math.sin(a) * RING);
            s.cz = (int) Math.round(-Math.cos(a) * RING);
            ConfigurationSection o = c == null ? null : c.getConfigurationSection(s.id);
            if (o == null) continue;
            s.name = o.getString("name", s.name);
            String rod = o.getString("angel", null);
            if (rod != null) s.rod = rod.isBlank() || rod.equalsIgnoreCase("keine") ? null : Rod.byId(rod);
            String bait = o.getString("koeder", null);
            if (bait != null) s.bait = Bait.of(bait);
            s.level = o.getInt("level", s.level);
            s.luck = o.getDouble("glueck", s.luck);
            s.faster = o.getDouble("tempo", s.faster);
            s.weight = o.getInt("gewicht", s.weight);
            s.treasure = o.getDouble("schatz", s.treasure);
        }
    }

    public List<Spot> spots() {
        return spots;
    }

    public Spot spot(String id) {
        for (Spot s : spots) if (s.id.equalsIgnoreCase(id)) return s;
        return null;
    }

    public String worldName() {
        return core.getConfig().getString("angeln.welt.name", "angelwelt");
    }

    public World world() {
        return Bukkit.getWorld(worldName());
    }

    public boolean isWorld(World w) {
        return w != null && w.getName().equals(worldName());
    }

    /** Gewässer, in dem der Schwimmer liegt (Wasserfläche mit etwas Rand). */
    public Spot spotAt(Location l) {
        if (l == null || !isWorld(l.getWorld())) return null;
        for (Spot s : spots) {
            double dx = l.getX() - (s.cx + 0.5), dz = l.getZ() - (s.cz + 0.5);
            if (dx * dx + dz * dz <= (POND + 1.5) * (POND + 1.5)) return s;
        }
        return null;
    }

    /** Insel, auf der jemand steht. */
    public Spot islandAt(Location l) {
        if (l == null || !isWorld(l.getWorld())) return null;
        for (Spot s : spots) {
            double dx = l.getX() - (s.cx + 0.5), dz = l.getZ() - (s.cz + 0.5);
            if (dx * dx + dz * dz <= (ISLAND + 1.5) * (ISLAND + 1.5)) return s;
        }
        return null;
    }

    /** Fehlende Voraussetzungen als Text, null wenn alles passt. */
    public String missing(Player p, Spot s) {
        List<String> miss = new ArrayList<>();
        if (!rodOk(p, s)) miss.add(rodText(s));
        if (!baitOk(p, s)) miss.add(s.bait.label + " (aktiv)");
        if (!levelOk(p, s)) miss.add("Angel-Level " + s.level);
        return miss.isEmpty() ? null : String.join(", ", miss);
    }

    public boolean open(Player p, Spot s) {
        return rodOk(p, s) && baitOk(p, s) && levelOk(p, s);
    }

    public boolean rodOk(Player p, Spot s) {
        if (s.rod == null) return true;
        Rod r = AngelService.rodOf(p);
        return r != null && r.ordinal() >= s.rod.ordinal();
    }

    public boolean baitOk(Player p, Spot s) {
        if (s.bait == null) return true;
        AngelProfile a = addon.angeln().profile(p.getUniqueId());
        return a.active == s.bait && a.bait(s.bait) > 0;
    }

    public boolean levelOk(Player p, Spot s) {
        return addon.angeln().profile(p.getUniqueId()).level() >= s.level;
    }

    public static String rodText(Spot s) {
        if (s.rod == null) return "Jede Angel";
        if (s.rod.jewels) return "Juwelen-Angel";
        return "Ab " + s.rod.label;
    }

    // =================================================================================================================
    // Welt anlegen und bauen
    // =================================================================================================================

    private void start() {
        if (!core.getConfig().getBoolean("angeln.welt.aktiv", true)) return;
        String name = worldName();
        boolean exists = new File(Bukkit.getWorldContainer(), name).exists();
        if (!exists && !core.getConfig().getBoolean("angeln.welt.auto-erstellen", true)) return;
        World w = load();
        if (w == null) return;
        if (!exists) {
            build(w);
            addon.getLogger().info("Angelwelt " + name + " angelegt und gebaut.");
        }
        holograms(w);
    }

    private World load() {
        World w = world();
        if (w != null) return w;
        try {
            w = new WorldCreator(worldName()).environment(World.Environment.NORMAL)
                    .generator(new WorldService.VoidGenerator()).generateStructures(false).createWorld();
        } catch (RuntimeException ex) {
            addon.getLogger().warning("Angelwelt konnte nicht geladen werden: " + ex.getMessage());
            return null;
        }
        if (w == null) return null;
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_INSOMNIA, false);
        w.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        w.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        w.setGameRule(GameRule.FALL_DAMAGE, false);
        w.setDifficulty(Difficulty.PEACEFUL);
        w.setSpawnLocation(0, Y + 1, 0);
        return w;
    }

    /** Für Admins: Welt anlegen (falls nötig) und alles neu bauen. Gibt eine Meldung zurück. */
    public String rebuild() {
        World w = load();
        if (w == null) return "Die Angelwelt konnte nicht angelegt werden.";
        build(w);
        holograms(w);
        return "Angelwelt " + w.getName() + " gebaut: Spawn-Insel und " + spots.size() + " Gewässer.";
    }

    public Location spawn() {
        World w = world();
        return w == null ? null : new Location(w, 0.5, Y + 1, 0.5, 180f, 0f);
    }

    /** Teleport-Punkt am Gewässer: am Rand der Insel zum Spawn hin, Blick aufs Wasser. */
    public Location travelPoint(Spot s) {
        World w = world();
        if (w == null) return null;
        double len = Math.sqrt((double) s.cx * s.cx + (double) s.cz * s.cz);
        double ux = -s.cx / len, uz = -s.cz / len;
        double r = POND + 3;
        Location l = new Location(w, s.cx + 0.5 + ux * r, Y + 1, s.cz + 0.5 + uz * r);
        l.setDirection(new org.bukkit.util.Vector(-ux, -0.25, -uz));
        return l;
    }

    private void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private void build(World w) {
        // Spawn-Insel
        disc(w, 0, 0, SPAWN, Material.SPRUCE_PLANKS, Material.STONE_BRICKS, 4);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) set(w, dx, Y, dz, Material.CHISELED_STONE_BRICKS);
        }
        // Laternen zwischen den Stegen
        for (int[] q : new int[][]{{2, -5}, {5, 2}, {-2, 5}, {-5, -2}}) post(w, q[0], q[1], Material.SPRUCE_FENCE, Material.LANTERN);
        for (Spot s : spots) {
            bridge(w, s);
            island(w, s);
        }
    }

    /** Scheibe mit Oberfläche top und nach unten schmaler werdender Unterseite. */
    private void disc(World w, int cx, int cz, int r, Material top, Material under, int layers) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.3) continue;
                set(w, cx + dx, Y, cz + dz, top);
                for (int k = 1; k <= layers; k++) {
                    if (d <= r - k * 1.6 + 0.3) set(w, cx + dx, Y - k, cz + dz, under);
                }
            }
        }
    }

    private void post(World w, int x, int z, Material fence, Material light) {
        set(w, x, Y + 1, z, fence);
        set(w, x, Y + 2, z, light);
    }

    /** Steg von der Spawn-Insel zur Insel des Gewässers: 3 breit, Geländer an den Seiten. */
    private void bridge(World w, Spot s) {
        double len = Math.sqrt((double) s.cx * s.cx + (double) s.cz * s.cz);
        double ux = s.cx / len, uz = s.cz / len;
        double px = -uz, pz = ux;
        for (double t = SPAWN - 0.5; t <= len - ISLAND + 0.8; t += 0.4) {
            for (int side = -1; side <= 1; side++) {
                int x = (int) Math.floor(ux * t + px * side + 0.5), z = (int) Math.floor(uz * t + pz * side + 0.5);
                set(w, x, Y, z, Material.SPRUCE_PLANKS);
                set(w, x, Y - 1, z, Material.SPRUCE_SLAB);
            }
        }
        for (double t = SPAWN + 1; t <= len - ISLAND - 0.5; t += 0.4) {
            for (int side : new int[]{-2, 2}) {
                int x = (int) Math.floor(ux * t + px * side + 0.5), z = (int) Math.floor(uz * t + pz * side + 0.5);
                if (w.getBlockAt(x, Y, z).getType() != Material.SPRUCE_PLANKS) {
                    set(w, x, Y, z, Material.SPRUCE_PLANKS);
                    set(w, x, Y + 1, z, Material.SPRUCE_FENCE);
                }
            }
        }
    }

    private void island(World w, Spot s) {
        Look l = s.look;
        int layers = l.depth + 3;
        for (int dx = -ISLAND; dx <= ISLAND; dx++) {
            for (int dz = -ISLAND; dz <= ISLAND; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > ISLAND + 0.3) continue;
                int x = s.cx + dx, z = s.cz + dz;
                // Insel mit Unterseite
                for (int k = 1; k <= layers; k++) {
                    if (d <= ISLAND - k * 1.1 + 0.3) set(w, x, Y - k, z, l.under);
                }
                if (d <= POND + 0.3) {
                    // Becken: Wasser bis zur Oberfläche, Grund darunter
                    for (int k = 0; k < l.depth; k++) set(w, x, Y - k, z, Material.WATER);
                    set(w, x, Y - l.depth, z, l.floor);
                    for (int k = l.depth + 1; k <= l.depth + 2; k++) set(w, x, Y - k, z, l.under);
                } else if (d <= POND + 1.3) {
                    set(w, x, Y, z, l.rim);
                    for (int k = 1; k <= l.depth; k++) set(w, x, Y - k, z, l.under);
                } else {
                    set(w, x, Y, z, l.ground);
                }
            }
        }
        decorate(w, s);
    }

    /** Deko je Aussehen, an festen Stellen am Ufer (bewusst nicht ganz gleichmäßig). */
    private void decorate(World w, Spot s) {
        int[][] ring = {{9, 2}, {-8, 5}, {3, -9}, {-6, -7}, {7, -6}, {-3, 9}};
        double len = Math.sqrt((double) s.cx * s.cx + (double) s.cz * s.cz);
        double ux = -s.cx / len, uz = -s.cz / len;
        for (int i = 0; i < ring.length; i++) {
            // nicht auf den Steg und nicht vor das Schild
            double along = ring[i][0] * ux + ring[i][1] * uz, across = Math.abs(ring[i][0] * uz - ring[i][1] * ux);
            if (along > 0 && across < 3.5) continue;
            int x = s.cx + ring[i][0], z = s.cz + ring[i][1];
            switch (s.look) {
                case TEICH -> set(w, x, Y + 1, z, i % 3 == 0 ? Material.POPPY : i % 3 == 1 ? Material.DANDELION
                        : Material.OXEYE_DAISY);
                case BERGSEE -> {
                    set(w, x, Y + 1, z, Material.COBBLESTONE);
                    if (i % 2 == 0) set(w, x, Y + 2, z, Material.SNOW);
                }
                case MANGROVE -> {
                    set(w, x, Y + 1, z, Material.MANGROVE_ROOTS);
                    if (i % 2 == 0) set(w, x, Y + 2, z, Material.MANGROVE_LEAVES);
                }
                case LAGUNE -> set(w, x, Y + 1, z, i % 2 == 0 ? Material.DEAD_BUSH : Material.SEA_PICKLE);
                case TIEFSEE, STERNE -> post(w, x, z, s.look == Look.TIEFSEE ? Material.PRISMARINE_WALL
                        : Material.PURPUR_PILLAR, s.look == Look.TIEFSEE ? Material.SEA_LANTERN : Material.END_ROD);
                case EISLOCH -> {
                    set(w, x, Y + 1, z, Material.PACKED_ICE);
                    if (i % 2 == 1) set(w, x, Y + 2, z, Material.PACKED_ICE);
                }
                case MYTHEN -> post(w, x, z, Material.POLISHED_BLACKSTONE_WALL, Material.SOUL_LANTERN);
            }
        }
        // im Wasser: Seerosen, Korallen
        if (s.look == Look.TEICH || s.look == Look.MANGROVE) {
            for (int[] q : new int[][]{{2, 1}, {-3, -2}, {1, -4}}) set(w, s.cx + q[0], Y + 1, s.cz + q[1], Material.LILY_PAD);
        }
        if (s.look == Look.LAGUNE) {
            Material[] coral = {Material.BRAIN_CORAL_BLOCK, Material.TUBE_CORAL_BLOCK, Material.FIRE_CORAL_BLOCK,
                    Material.HORN_CORAL_BLOCK};
            int[][] at = {{2, 2}, {-3, 1}, {1, -3}, {-2, -3}};
            for (int i = 0; i < at.length; i++) set(w, s.cx + at[i][0], Y - s.look.depth + 1, s.cz + at[i][1], coral[i]);
        }
    }

    // =================================================================================================================
    // Hologramme
    // =================================================================================================================

    private void holograms(World w) {
        clearHolos();
        for (Entity e : w.getEntitiesByClass(TextDisplay.class)) {
            if (e.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) e.remove();
        }
        holos.add(new Holo(new Location(w, 0.5, Y + 3.2, 0.5), Msg.parse("<gradient:#62CDFF:#B8F0FF><b>ANGELWELT</b></gradient>\n"
                + "<gray>Jedes Gewässer ist anders gut.\n<white>/angeln gewaesser <gray>· Stege führen hin")));
        for (Spot s : spots) {
            double len = Math.sqrt((double) s.cx * s.cx + (double) s.cz * s.cz);
            double ux = -s.cx / len, uz = -s.cz / len;
            Location at = new Location(w, s.cx + 0.5 + ux * (POND + 2.2), Y + 2.6, s.cz + 0.5 + uz * (POND + 2.2));
            String hex = String.format(Locale.ROOT, "#%06X", s.color);
            String req = s.rod == null && s.bait == null && s.level <= 1 ? "<green>Für alle offen"
                    : "<gray>" + rodText(s) + (s.bait != null ? " · " + s.bait.label : "") + " · Level " + s.level;
            holos.add(new Holo(at, Msg.parse("<" + hex + "><b>" + Msg.escape(s.name) + "</b>\n<gray>" + s.waterLabel()
                    + "\n<aqua>" + s.allBonus() + "\n" + req)));
        }
        refreshHolos();
    }

    /** Setzt fehlende Hologramme, deren Chunk geladen ist (nach dem Entladen sind sie weg). */
    private void refreshHolos() {
        for (Holo h : holos) {
            if (h.live != null && h.live.isValid()) continue;
            World w = h.at.getWorld();
            if (w == null || !w.isChunkLoaded(h.at.getBlockX() >> 4, h.at.getBlockZ() >> 4)) continue;
            h.live = w.spawn(h.at, TextDisplay.class, d -> {
                d.text(h.text);
                d.setBillboard(Display.Billboard.CENTER);
                d.setPersistent(false);
                d.setShadowed(true);
                d.setBackgroundColor(org.bukkit.Color.fromARGB(110, 10, 14, 24));
                d.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
            });
        }
    }

    private void clearHolos() {
        for (Holo h : holos) if (h.live != null && h.live.isValid()) h.live.remove();
        holos.clear();
    }

    @EventHandler
    public void onChunk(org.bukkit.event.world.ChunkLoadEvent e) {
        if (!holos.isEmpty() && isWorld(e.getWorld())) addon.getServer().getScheduler().runTask(addon, this::refreshHolos);
    }

    public void shutdown() {
        clearHolos();
    }

    // =================================================================================================================
    // Reisen, Schutz, Absturz
    // =================================================================================================================

    /** Bringt einen Survival-Spieler in die Angelwelt (Spawn oder ans Gewässer). Gibt eine Fehlermeldung oder null. */
    public String travel(Player p, Spot s) {
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) {
            return "Die Angelwelt gehört zu Survival. Wechsle zuerst nach Survival.";
        }
        Location to = s == null ? spawn() : travelPoint(s);
        if (to == null) return "Die Angelwelt ist noch nicht eingerichtet.";
        p.teleportAsync(to);
        return null;
    }

    private void rescue() {
        World w = world();
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            if (p.getLocation().getY() < Y - 24) {
                p.setFallDistance(0);
                p.teleportAsync(spawn());
            }
        }
    }

    private boolean guarded(Player p, World w) {
        return isWorld(w) && (p == null || !p.hasPermission("kuby.admin"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (guarded(e.getPlayer(), e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (guarded(e.getPlayer(), e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (guarded(e.getPlayer(), e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent e) {
        if (guarded(e.getPlayer(), e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        // Wasser bleibt in den Becken
        if (isWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (isWorld(e.getLocation().getWorld())) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!isWorld(e.getLocation().getWorld())) return;
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r != CreatureSpawnEvent.SpawnReason.CUSTOM && r != CreatureSpawnEvent.SpawnReason.COMMAND
                && r != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && isWorld(e.getEntity().getWorld())
                && e.getCause() != EntityDamageEvent.DamageCause.CUSTOM) e.setCancelled(true);
    }
}

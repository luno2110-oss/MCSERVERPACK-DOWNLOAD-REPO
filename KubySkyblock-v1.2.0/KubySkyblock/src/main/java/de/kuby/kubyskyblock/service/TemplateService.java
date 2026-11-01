package de.kuby.kubyskyblock.service;

import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Insel-Vorlagen, aus denen Spieler beim Start ihre Insel wählen. Eingebaut: Klassisch, Wüste, Schnee (im Code
 * gebaut). Eigene Vorlagen speichern Admins aus zwei markierten Ecken (/kp skyblock template save <id> [Name]): alle
 * Blöcke mit Ausrichtung (BlockData) und der Inhalt von Kisten, Fässern usw.; der Standplatz des Admins wird der
 * Spawn der Insel. Dateien: templates/<id>.yml. Eine eigene Vorlage mit der ID einer eingebauten ersetzt diese.
 */
public final class TemplateService {

    /** Block relativ zum Spawn (Füße des Spielers): x, y, z und BlockData als Text. */
    public record Block3(int x, int y, int z, String data) {}

    /** Vorlage: ID, Name, Beschreibung, Icon, Blöcke (relativ zum Spawn), Kisten-Inhalte je Position „x;y;z“. */
    public record Template(String id, String name, String desc, Material icon, List<Block3> blocks,
                           Map<String, List<ItemStack>> chests, boolean builtin) {}

    private final KubySkyblock addon;
    private final File dir;
    private final Map<String, Template> templates = new LinkedHashMap<>();

    public TemplateService(KubySkyblock addon) {
        this.addon = addon;
        this.dir = new File(addon.getDataFolder(), "templates");
        migrate();
        load();
    }

    /** Alle wählbaren Vorlagen (eigene vor eingebauten gleicher ID). */
    public List<Template> all() {
        return new ArrayList<>(templates.values());
    }

    public Template get(String id) {
        Template t = id == null ? null : templates.get(id);
        return t != null ? t : templates.isEmpty() ? null : templates.values().iterator().next();
    }

    public boolean hasTemplate() {
        return !templates.isEmpty();
    }

    // ------------------------------------------------------------------ Laden

    public void load() {
        templates.clear();
        Map<String, Template> own = new LinkedHashMap<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File f : files) {
                Template t = read(f);
                if (t != null) own.put(t.id(), t);
            }
        }
        if (addon.getConfig().getBoolean("insel.eingebaute-vorlagen", true)) {
            for (Template t : builtins()) if (!own.containsKey(t.id())) templates.put(t.id(), t);
        }
        templates.putAll(own);
    }

    private Template read(File f) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
        List<Block3> blocks = new ArrayList<>();
        for (String s : y.getStringList("blocks")) {
            String[] p = s.split(";", 4);
            if (p.length < 4) continue;
            try {
                blocks.add(new Block3(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), p[3]));
            } catch (NumberFormatException ignored) {
                // Zeile überspringen
            }
        }
        if (blocks.isEmpty()) return null;
        Map<String, List<ItemStack>> chests = new LinkedHashMap<>();
        ConfigurationSection k = y.getConfigurationSection("kisten");
        if (k != null) {
            for (String pos : k.getKeys(false)) {
                List<ItemStack> items = new ArrayList<>();
                for (String b64 : k.getStringList(pos)) {
                    try {
                        items.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(b64)));
                    } catch (RuntimeException ignored) {
                        // kaputtes Item überspringen
                    }
                }
                chests.put(pos.replace('_', ';'), items);
            }
        }
        Material icon = Material.matchMaterial(y.getString("icon", "GRASS_BLOCK"));
        return new Template(id, y.getString("name", id), y.getString("beschreibung", ""), icon == null ? Material.GRASS_BLOCK : icon,
                blocks, chests, false);
    }

    /** Alte template.yml (eine Vorlage, nur Materialien) wird zu templates/standard.yml. */
    private void migrate() {
        File old = new File(addon.getDataFolder(), "template.yml");
        if (!old.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(old);
        List<Integer> sp = y.getIntegerList("spawn");
        List<String> out = new ArrayList<>();
        if (sp.size() == 3) {
            for (String s : y.getStringList("blocks")) {
                String[] p = s.split(";");
                if (p.length < 4) continue;
                Material m = Material.matchMaterial(p[3]);
                if (m == null) continue;
                out.add((Integer.parseInt(p[0]) - sp.get(0)) + ";" + (Integer.parseInt(p[1]) - sp.get(1)) + ";"
                        + (Integer.parseInt(p[2]) - sp.get(2)) + ";" + m.createBlockData().getAsString());
            }
        }
        if (!out.isEmpty()) {
            YamlConfiguration n = new YamlConfiguration();
            n.set("name", "Standard");
            n.set("beschreibung", "Die Insel des Servers");
            n.set("icon", "GRASS_BLOCK");
            n.set("blocks", out);
            try {
                dir.mkdirs();
                n.save(new File(dir, "standard.yml"));
            } catch (IOException e) {
                addon.getLogger().warning("templates/standard.yml: " + e.getMessage());
                return;
            }
        }
        if (old.renameTo(new File(addon.getDataFolder(), "template.yml.alt"))) {
            addon.getLogger().info("Insel-Vorlage nach templates/standard.yml übernommen.");
        }
    }

    // ------------------------------------------------------------------ Speichern (Admin)

    /** Speichert alle Blöcke zwischen den zwei Ecken als Vorlage id. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public String save(String id, String name, Material icon, Location a, Location b, Location standing) {
        World w = a.getWorld();
        if (w == null || !w.equals(b.getWorld())) return "Beide Ecken müssen in derselben Welt liegen.";
        if (!id.matches("[a-z0-9_-]{1,32}")) return "Die ID darf nur a-z, 0-9, _ und - enthalten.";
        int minX = Math.min(a.getBlockX(), b.getBlockX()), maxX = Math.max(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY()), maxY = Math.max(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ()), maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > 200_000) return "Die Vorlage ist zu groß (höchstens 200.000 Blöcke).";
        int sx = standing.getBlockX(), sy = standing.getBlockY(), sz = standing.getBlockZ();
        int r = addon.islands().radius(0);
        if (Math.max(Math.max(Math.abs(minX - sx), Math.abs(maxX - sx)), Math.max(Math.abs(minZ - sz), Math.abs(maxZ - sz))) > r) {
            return "Vom Standplatz aus darf die Vorlage höchstens " + r + " Blöcke in jede Richtung reichen (Startgröße der Insel).";
        }
        List<String> blocks = new ArrayList<>();
        YamlConfiguration out = new YamlConfiguration();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block bl = w.getBlockAt(x, y, z);
                    if (bl.getType().isAir()) continue;
                    String rel = (x - sx) + ";" + (y - sy) + ";" + (z - sz);
                    blocks.add(rel + ";" + bl.getBlockData().getAsString());
                    if (bl.getState() instanceof Container c) {
                        List<String> items = new ArrayList<>();
                        for (ItemStack it : c.getInventory().getContents()) {
                            if (it != null && !it.getType().isAir()) items.add(Base64.getEncoder().encodeToString(it.serializeAsBytes()));
                        }
                        if (!items.isEmpty()) out.set("kisten." + rel.replace(';', '_'), items);
                    }
                }
            }
        }
        if (blocks.isEmpty()) return "Zwischen den Ecken ist nichts gebaut.";
        out.set("name", name);
        out.set("beschreibung", "");
        out.set("icon", (icon == null || icon.isAir() ? Material.GRASS_BLOCK : icon).name());
        out.set("blocks", blocks);
        try {
            dir.mkdirs();
            out.save(new File(dir, id + ".yml"));
        } catch (IOException e) {
            return "Speichern fehlgeschlagen: " + e.getMessage();
        }
        load();
        return null;
    }

    public boolean delete(String id) {
        File f = new File(dir, id + ".yml");
        boolean ok = f.exists() && f.delete();
        load();
        return ok;
    }

    // ------------------------------------------------------------------ Bauen

    /** Baut die Vorlage so, dass der Spawn (Füße) bei feet liegt. Leere Kisten bekommen die Start-Items. */
    public void build(Template t, Location feet) {
        World w = feet.getWorld();
        int ox = feet.getBlockX(), oy = feet.getBlockY(), oz = feet.getBlockZ();
        List<ItemStack> start = startItems();
        for (Block3 b : t.blocks()) {
            Block bl = w.getBlockAt(ox + b.x(), oy + b.y(), oz + b.z());
            BlockData data;
            try {
                data = Bukkit.createBlockData(b.data());
            } catch (IllegalArgumentException e) {
                continue;
            }
            bl.setBlockData(data, false);
            if (bl.getState() instanceof Container c) {
                List<ItemStack> items = t.chests().get(b.x() + ";" + b.y() + ";" + b.z());
                if (items == null && t.builtin()) items = start;
                if (items != null) for (ItemStack it : items) c.getInventory().addItem(it.clone());
            }
        }
    }

    /** Start-Items der eingebauten Vorlagen (insel.start-kiste: MATERIAL:MENGE). */
    private List<ItemStack> startItems() {
        List<ItemStack> out = new ArrayList<>();
        List<String> cfg = addon.getConfig().getStringList("insel.start-kiste");
        if (cfg.isEmpty()) cfg = List.of("ICE:2", "LAVA_BUCKET:1", "BONE_MEAL:3", "MELON_SEEDS:1", "PUMPKIN_SEEDS:1", "SUGAR_CANE:1",
                "RED_MUSHROOM:1", "BROWN_MUSHROOM:1", "WHEAT_SEEDS:2");
        for (String s : cfg) {
            String[] p = s.split(":");
            Material m = Material.matchMaterial(p[0].trim());
            if (m == null || !m.isItem()) continue;
            int n = 1;
            try {
                if (p.length > 1) n = Integer.parseInt(p[1].trim());
            } catch (NumberFormatException ignored) {
                n = 1;
            }
            out.add(new ItemStack(m, Math.max(1, n)));
        }
        return out;
    }

    // ------------------------------------------------------------------ Eingebaute Vorlagen

    private static final class Builder {
        final List<Block3> blocks = new ArrayList<>();

        void set(int x, int y, int z, String data) {
            blocks.removeIf(b -> b.x() == x && b.y() == y && b.z() == z);
            blocks.add(new Block3(x, y, z, data));
        }

        void layer(int y, int x0, int z0, int x1, int z1, String data) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) set(x, y, z, data);
        }

        /** Baum: Stamm (h hoch) ab y, Blätter rundherum. */
        void tree(int x, int y, int z, String log, String leaves, int h) {
            for (int dy = h - 2; dy <= h + 1; dy++) {
                int r = dy >= h ? 1 : 2;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) == r && Math.abs(dz) == r && (dy == h + 1 || r == 2 && dy == h - 1)) continue;
                        set(x + dx, y + dy, z + dz, leaves);
                    }
                }
            }
            for (int dy = 0; dy < h; dy++) set(x, y + dy, z, log);
        }
    }

    private static List<Template> builtins() {
        List<Template> out = new ArrayList<>();
        // Klassisch: die bekannte L-Insel mit Eiche und Kiste
        Builder k = new Builder();
        for (int y = -3; y <= -1; y++) {
            String d = y == -1 ? "minecraft:grass_block" : "minecraft:dirt";
            k.layer(y, -1, -1, 4, 1, d);
            k.layer(y, -1, 2, 1, 4, d);
        }
        k.set(0, -3, 0, "minecraft:bedrock");
        k.tree(3, 0, 0, "minecraft:oak_log[axis=y]", "minecraft:oak_leaves[persistent=true]", 4);
        k.set(-1, 0, 4, "minecraft:chest[facing=east]");
        out.add(new Template("klassisch", "Klassisch", "Die bekannte L-Insel mit Eiche und Kiste", Material.GRASS_BLOCK, k.blocks,
                Map.of(), true));
        // Wüste: Sand mit Kaktus und Sandstein darunter
        Builder w = new Builder();
        w.layer(-1, -2, -2, 2, 2, "minecraft:sand");
        w.layer(-2, -2, -2, 2, 2, "minecraft:sandstone");
        w.layer(-3, -1, -1, 1, 1, "minecraft:sandstone");
        w.set(0, -3, 0, "minecraft:bedrock");
        w.set(2, 0, 2, "minecraft:cactus");
        w.set(-2, 0, 2, "minecraft:dead_bush");
        w.set(2, 0, -2, "minecraft:chest[facing=west]");
        out.add(new Template("wueste", "Wüste", "Sand, Kaktus und viel Sonne", Material.SAND, w.blocks, Map.of(), true));
        // Schnee: verschneite Insel mit Fichte und Eis
        Builder s = new Builder();
        s.layer(-1, -2, -2, 2, 2, "minecraft:grass_block[snowy=true]");
        s.layer(-2, -2, -2, 2, 2, "minecraft:dirt");
        s.layer(-3, -1, -1, 1, 1, "minecraft:stone");
        s.set(0, -3, 0, "minecraft:bedrock");
        s.set(-2, -1, 2, "minecraft:ice");
        s.tree(2, 0, -2, "minecraft:spruce_log[axis=y]", "minecraft:spruce_leaves[persistent=true]", 5);
        for (int x = -2; x <= 1; x++) for (int z = -1; z <= 2; z++) if (!(x == 0 && z == 0)) s.set(x, 0, z, "minecraft:snow[layers=1]");
        s.set(-2, 0, -2, "minecraft:chest[facing=east]");
        out.add(new Template("schnee", "Schnee", "Eine verschneite Insel mit Fichte", Material.SNOW_BLOCK, s.blocks, Map.of(), true));
        return out;
    }
}

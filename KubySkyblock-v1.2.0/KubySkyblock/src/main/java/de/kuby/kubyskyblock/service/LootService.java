package de.kuby.kubyskyblock.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Loot-Inseln in der Skyblock-Welt. Jede Insel hat eine Stufe (von nutzlos bis overpowered).
 * Die Kiste auf der Insel gibt Loot. Pro Spieler und Insel gibt es eine Abklingzeit.
 */
public final class LootService {

    public record Island(String key, int x, int y, int z, String tier) {}

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final File file;
    private final Random rnd = new Random();
    private final Map<String, Island> islands = new HashMap<>();
    private final Map<String, Long> cooldown = new HashMap<>();

    public LootService(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "loot.yml");
        for (String s : YamlConfiguration.loadConfiguration(file).getStringList("islands")) {
            String[] p = s.split(";");
            if (p.length < 5) continue;
            Island i = new Island(p[0] + ";" + p[1] + ";" + p[2] + ";" + p[3], Integer.parseInt(p[1]),
                    Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4]);
            islands.put(i.key(), i);
        }
    }

    public Map<String, Island> all() { return islands; }

    /** Erzeugt Loot-Inseln zufällig verteilt in der Weltgrenze. Gibt die Anzahl zurück. */
    public int generate(World w, int count) {
        ConfigurationSection tiers = addon.getConfig().getConfigurationSection("loot.tiers");
        if (tiers == null) return 0;
        List<String> names = new ArrayList<>(tiers.getKeys(false));
        int made = 0;
        for (int i = 0; i < count; i++) {
            int x = rnd.nextInt(40000) - 20000;
            int z = rnd.nextInt(40000) - 20000;
            String tier = names.get(weighted(tiers, names));
            int r = tiers.getInt(tier + ".radius", 3);
            Location base = new Location(w, x, 100, z);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    w.getBlockAt(x + dx, 99, z + dz).setType(Material.STONE, false);
                    w.getBlockAt(x + dx, 98, z + dz).setType(Material.DIRT, false);
                    w.getBlockAt(x + dx, 100, z + dz).setType(Material.GRASS_BLOCK, false);
                }
            }
            w.getBlockAt(x, 101, z).setType(Material.CHEST, false);
            Island isl = new Island(x + ";100;" + z, x, 101, z, tier);
            islands.put(isl.key(), isl);
            made++;
        }
        save();
        return made;
    }

    private int weighted(ConfigurationSection tiers, List<String> names) {
        int total = 0;
        for (String n : names) total += Math.max(1, tiers.getInt(n + ".weight", 1));
        int r = rnd.nextInt(total);
        for (int i = 0; i < names.size(); i++) {
            r -= Math.max(1, tiers.getInt(names.get(i) + ".weight", 1));
            if (r < 0) return i;
        }
        return 0;
    }

    /** Ist der Block eine Loot-Kiste? */
    public Island islandAt(Block b) {
        return islands.get(b.getX() + ";" + b.getY() + ";" + b.getZ());
    }

    /** Öffnet die Kiste: gibt Items, Geld, XP und manchmal Juwelen. Gibt eine Meldung zurück oder null. */
    public String open(Player p, Island isl) {
        String key = isl.key() + ";" + p.getUniqueId();
        long now = System.currentTimeMillis();
        long wait = addon.getConfig().getLong("loot.cooldown-minutes", 360) * 60_000L;
        Long last = cooldown.get(key);
        if (last != null && now - last < wait) {
            long min = (wait - (now - last)) / 60_000L + 1;
            return "Diese Kiste ist leer. Komm in " + min + " Minuten wieder.";
        }
        cooldown.put(key, now);
        addon.progress().recordLoot(p);
        ConfigurationSection t = addon.getConfig().getConfigurationSection("loot.tiers." + isl.tier());
        if (t == null) return "Diese Stufe hat keine Belohnung.";
        int items = t.getInt("items", 1);
        for (int i = 0; i < items; i++) {
            ItemStack stack = rollItem(t);
            if (stack != null) {
                var left = p.getInventory().addItem(stack);
                left.values().forEach(r -> p.getWorld().dropItem(p.getLocation(), r));
                addon.progress().recordItem(p, stack.getType());
            }
        }
        core.economy().deposit(p.getUniqueId(), t.getLong("money", 0));
        core.stats().get(p.getUniqueId()).xp += t.getLong("xp", 0);
        if (t.getDouble("jewel-chance", 0) > rnd.nextDouble()) {
            core.jewels().add(p.getUniqueId(), t.getLong("jewels", 1));
        }
        Sfx.play(core, p, Sfx.Kind.CHIME);
        return null;
    }

    /** Zufälliges Item: zufälliger Eintrag aus allen Materialien, oder aus der Stufen-Liste. */
    private ItemStack rollItem(ConfigurationSection t) {
        if (t.getBoolean("random-all", false)) {
            List<Material> all = new ArrayList<>();
            for (Material m : Material.values()) if (m.isItem() && !m.isAir()) all.add(m);
            return new ItemStack(all.get(rnd.nextInt(all.size())), 1 + rnd.nextInt(Math.max(1, t.getInt("max-amount", 1))));
        }
        List<String> entries = t.getStringList("list");
        if (entries.isEmpty()) return null;
        String e = entries.get(rnd.nextInt(entries.size()));
        String[] p = e.split(";");
        try {
            Material m = Material.valueOf(p[0].trim().toUpperCase());
            int max = p.length > 1 ? Integer.parseInt(p[1].trim()) : 1;
            return new ItemStack(m, 1 + rnd.nextInt(Math.max(1, max)));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Gibt einem Spieler n Würfe aus einer Stufe, ohne Abklingzeit (z. B. für Meilensteine). */
    public void giveRoll(Player p, String tier, int n) {
        ConfigurationSection t = addon.getConfig().getConfigurationSection("loot.tiers." + tier);
        if (t == null) return;
        for (int i = 0; i < n; i++) {
            ItemStack stack = rollItem(t);
            if (stack == null) continue;
            var left = p.getInventory().addItem(stack);
            left.values().forEach(r -> p.getWorld().dropItem(p.getLocation(), r));
            addon.progress().recordItem(p, stack.getType());
        }
    }

    public void save() {
        YamlConfiguration out = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (Island i : islands.values()) list.add(i.x() + ";" + i.y() + ";" + i.z() + ";" + i.tier());
        out.set("islands", list);
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            addon.getLogger().warning("loot.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}

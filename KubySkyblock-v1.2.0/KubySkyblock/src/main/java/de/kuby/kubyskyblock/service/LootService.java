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
 * Loot-Inseln in der Skyblock-Welt. Jede Insel hat eine Stufe (von nutzlos bis gott). Die Kiste auf der Insel gibt
 * Loot aus der Loot-Tabelle der Stufe: zuerst die im Spiel bearbeitete Tabelle (/kp skyblock loot edit <stufe>,
 * loot-tabellen.yml, Menge im Slot = Höchstmenge), sonst die Liste in der config, sonst ein zufälliges erreichbares
 * Item (nie Creative-Blöcke). Pro Spieler und Insel gibt es eine Abklingzeit.
 */
public final class LootService {

    public record Island(String key, int x, int y, int z, String tier) {}

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final File file;
    private final Random rnd = new Random();
    private final Map<String, Island> islands = new HashMap<>();
    private final Map<String, Long> cooldown = new HashMap<>();
    /** Im Spiel bearbeitete Loot-Tabellen je Stufe. */
    private final Map<String, List<ItemStack>> tables = new HashMap<>();
    private final File tableFile;

    public LootService(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "loot.yml");
        this.tableFile = new File(addon.getDataFolder(), "loot-tabellen.yml");
        YamlConfiguration ty = YamlConfiguration.loadConfiguration(tableFile);
        for (String tier : ty.getKeys(false)) {
            List<ItemStack> items = new ArrayList<>();
            for (String b64 : ty.getStringList(tier)) {
                try {
                    items.add(ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(b64)));
                } catch (RuntimeException ignored) {
                    // kaputtes Item überspringen
                }
            }
            tables.put(tier, items);
        }
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
            // außerhalb der Spieler-Inseln (Spirale um den Spawn) und innerhalb der Weltgrenze
            int inner = addon.getConfig().getInt("loot.abstand-zum-spawn", 13000);
            int outer = Math.max(inner + 1000, addon.getConfig().getInt("insel.weltgrenze", 50000) / 2 - 1000);
            int x, z;
            do {
                x = rnd.nextInt(2 * outer) - outer;
                z = rnd.nextInt(2 * outer) - outer;
            } while (Math.max(Math.abs(x), Math.abs(z)) < inner);
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

    /** Stufen aus der config (Reihenfolge nutzlos ... gott). */
    public List<String> tiers() {
        ConfigurationSection s = addon.getConfig().getConfigurationSection("loot.tiers");
        return s == null ? List.of() : new ArrayList<>(s.getKeys(false));
    }

    public List<ItemStack> table(String tier) {
        return tables.getOrDefault(tier, List.of());
    }

    /** Im Spiel bearbeitete Tabelle speichern (leer = wieder config/zufällig). */
    public void setTable(String tier, List<ItemStack> items) {
        if (items.isEmpty()) tables.remove(tier);
        else tables.put(tier, new ArrayList<>(items));
        YamlConfiguration out = new YamlConfiguration();
        tables.forEach((k, v) -> {
            List<String> l = new ArrayList<>();
            for (ItemStack it : v) l.add(java.util.Base64.getEncoder().encodeToString(it.serializeAsBytes()));
            out.set(k, l);
        });
        try {
            addon.getDataFolder().mkdirs();
            out.save(tableFile);
        } catch (IOException e) {
            addon.getLogger().warning("loot-tabellen.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    /** Zufälliges Item der Stufe: bearbeitete Tabelle, sonst config-Liste, sonst zufällig (nur erreichbare Items). */
    private ItemStack rollItem(ConfigurationSection t) {
        List<ItemStack> table = tables.get(t.getName());
        if (table != null && !table.isEmpty()) {
            ItemStack pick = table.get(rnd.nextInt(table.size())).clone();
            pick.setAmount(1 + rnd.nextInt(Math.max(1, pick.getAmount())));
            return pick;
        }
        List<String> entries = t.getStringList("list");
        boolean randomAll = t.getBoolean("random-all", false);
        if (entries.isEmpty() || randomAll && rnd.nextInt(4) == 0) {
            if (!randomAll) return null;
            List<Material> all = Obtainable.all();
            return new ItemStack(all.get(rnd.nextInt(all.size())), 1 + rnd.nextInt(Math.max(1, t.getInt("max-amount", 1))));
        }
        String e = entries.get(rnd.nextInt(entries.size()));
        String[] p = e.split(";");
        try {
            Material m = Material.valueOf(p[0].trim().toUpperCase());
            if (!Obtainable.is(m)) return null;
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

package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Locs;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Homes: Grundanzahl plus Bonus. Bonus durch XP-Kauf (bis 3), Voten (+1), /verify (+3).
 * Ränge geben eine feste Obergrenze: der höhere Wert gilt.
 */
public final class HomeService implements ModeData {

    public static final int MAX_BOUGHT = 3;

    private final KubyPlugins plugin;
    private final File file;
    private final File perkFile;
    private final YamlConfiguration yml;
    private final YamlConfiguration perks;

    public HomeService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        this.perkFile = new File(plugin.getDataFolder(), "homeperks.yml");
        this.yml = YamlConfiguration.loadConfiguration(file);
        this.perks = YamlConfiguration.loadConfiguration(perkFile);
    }

    public Map<String, Location> homes(UUID id) {
        Map<String, Location> result = new LinkedHashMap<>();
        ConfigurationSection section = yml.getConfigurationSection(id.toString());
        if (section == null) return result;
        for (String name : section.getKeys(false)) {
            Location loc = Locs.parse(section.getString(name));
            if (loc != null) result.put(name, loc);
        }
        return result;
    }

    public Location get(UUID id, String name) {
        return homes(id).get(sanitize(name));
    }

    public boolean set(Player player, String rawName, Location loc) {
        String name = sanitize(rawName);
        Map<String, Location> current = homes(player.getUniqueId());
        if (!current.containsKey(name) && current.size() >= limit(player)) return false;
        yml.set(player.getUniqueId() + "." + name, Locs.serialize(loc));
        return true;
    }

    public void delete(UUID id, String rawName) {
        yml.set(id + "." + sanitize(rawName), null);
    }

    /** Maximale Anzahl Homes: Grundwert plus Bonus, mindestens der Rang-Wert. */
    public int limit(Player player) {
        UUID id = player.getUniqueId();
        int base = plugin.getConfig().getInt("homes.default-limit", 2);
        int bonus = extraBought(id) + (voted(id) ? 1 : 0) + (verified(id) ? 3 : 0);
        int rank = 0;
        ConfigurationSection ranks = plugin.getConfig().getConfigurationSection("homes.ranks");
        if (ranks != null) {
            for (String r : ranks.getKeys(false)) {
                if (player.hasPermission("kuby.rank." + r)) rank = Math.max(rank, ranks.getInt(r));
            }
        }
        return Math.max(base + bonus, rank);
    }

    public synchronized int extraBought(UUID id) {
        return perks.getInt(id + ".xp", 0);
    }

    public synchronized long nextCost(UUID id) {
        return plugin.getConfig().getLong("homes.xp-cost", 500) * (extraBought(id) + 1L);
    }

    /** Kauft einen zusätzlichen Homeplatz mit XP. Gibt null bei Erfolg zurück. */
    public synchronized String buyExtra(Player p) {
        UUID id = p.getUniqueId();
        if (extraBought(id) >= MAX_BOUGHT) return "Du hast bereits alle " + MAX_BOUGHT + " Erweiterungen gekauft.";
        long cost = nextCost(id);
        var st = plugin.stats().get(id);
        if (st.xp < cost) return "Dir fehlen " + (cost - st.xp) + " XP.";
        st.xp -= cost;
        plugin.stats().save();
        perks.set(id + ".xp", extraBought(id) + 1);
        savePerks();
        return null;
    }

    public synchronized boolean voted(UUID id) {
        return perks.getBoolean(id + ".voted", false);
    }

    public synchronized boolean verified(UUID id) {
        return perks.getBoolean(id + ".verified", false);
    }

    /** Gibt einen Bonus-Platz für eine Abstimmung. */
    public synchronized void grantVote(UUID id) {
        perks.set(id + ".voted", true);
        savePerks();
    }

    /** Gibt drei Bonus-Plätze für die Discord-Verknüpfung. */
    public synchronized void grantVerify(UUID id) {
        perks.set(id + ".verified", true);
        savePerks();
    }

    private synchronized void savePerks() {
        try {
            plugin.getDataFolder().mkdirs();
            perks.save(perkFile);
        } catch (IOException e) {
            plugin.getLogger().warning("homeperks.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public void save() {
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("homes.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
        savePerks();
    }

    private static String sanitize(String name) {
        String clean = name.replaceAll("[^A-Za-z0-9_-]", "");
        if (clean.isEmpty()) return "base";
        return clean.substring(0, Math.min(16, clean.length()));
    }

    @Override
    public String key() { return "homes"; }

    @Override
    public synchronized Map<String, Object> export(UUID id) {
        Map<String, Object> m = new LinkedHashMap<>();
        ConfigurationSection s = yml.getConfigurationSection(id.toString());
        if (s != null) {
            for (String name : s.getKeys(false)) m.put("home|" + name, s.getString(name));
        }
        m.put("perk|xp", extraBought(id));
        m.put("perk|voted", voted(id));
        m.put("perk|verified", verified(id));
        return m;
    }

    @Override
    public synchronized void importData(UUID id, Map<String, Object> m) {
        yml.set(id.toString(), null);
        perks.set(id + ".xp", null);
        perks.set(id + ".voted", null);
        perks.set(id + ".verified", null);
        if (m != null) {
            m.forEach((k, v) -> {
                if (k.startsWith("home|")) yml.set(id + "." + k.substring(5), v);
                else if (k.equals("perk|xp")) perks.set(id + ".xp", ModeData.integer(v));
                else if (k.equals("perk|voted")) perks.set(id + ".voted", Boolean.TRUE.equals(v));
                else if (k.equals("perk|verified")) perks.set(id + ".verified", Boolean.TRUE.equals(v));
            });
        }
        save();
    }
}

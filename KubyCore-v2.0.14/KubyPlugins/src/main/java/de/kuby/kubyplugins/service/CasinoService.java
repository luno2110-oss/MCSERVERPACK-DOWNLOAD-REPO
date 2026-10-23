package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Casino-Grundlagen: Chips als eigene Währung, Umtausch am Kassentisch, Jackpot-Anzeige.
 * Nur im Survival. Der Casino-Bereich ist eine Region mit der ID "casino" (Schutz über das Regionssystem).
 */
public final class CasinoService {

    public static final String REGION = "casino";
    private static final String SURVIVAL = "survival";

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey tag;
    private final Map<UUID, Long> chips = new HashMap<>();
    private long jackpot;
    private String kasse;
    private String tisch;

    public CasinoService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "casino.yml");
        this.tag = new NamespacedKey(plugin, "kp_casino_jackpot");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = yml.getConfigurationSection("chips");
        if (s != null) {
            for (String key : s.getKeys(false)) {
                try {
                    chips.put(UUID.fromString(key), s.getLong(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        jackpot = yml.getLong("jackpot", plugin.getConfig().getLong("casino.jackpot-start", 10000));
        kasse = yml.getString("kasse");
        tisch = yml.getString("tisch");
    }

    public synchronized long chips(UUID id) {
        return chips.getOrDefault(id, 0L);
    }

    public synchronized long jackpot() {
        return jackpot;
    }

    public synchronized void addJackpot(long amount) {
        jackpot += amount;
        save();
        updateDisplay();
    }

    /** Prüft Modus und Kassentisch. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public String checkUse(Player p) {
        if (!SURVIVAL.equals(plugin.modes().modeOf(p.getUniqueId()))) {
            return "Das Casino gibt es nur in Survival.";
        }
        if (!nearKasse(p.getLocation())) {
            return "Geh zum Kassentisch, um Chips zu tauschen.";
        }
        return null;
    }

    /** Kauft Chips. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public synchronized String buy(Player p, long count) {
        if (count <= 0) return "Die Anzahl muss größer als 0 sein.";
        long cost = count * price();
        if (!plugin.economy().withdraw(p.getUniqueId(), cost)) {
            return "Nicht genug$. " + count + " Chips kosten " + Msg.coins(cost) + "$.";
        }
        chips.merge(p.getUniqueId(), count, Long::sum);
        save();
        return null;
    }

    /** Verkauft Chips. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public synchronized String sell(Player p, long count) {
        if (count <= 0) return "Die Anzahl muss größer als 0 sein.";
        long have = chips(p.getUniqueId());
        if (have < count) return "Du hast nur " + have + " Chips.";
        chips.put(p.getUniqueId(), have - count);
        plugin.economy().deposit(p.getUniqueId(), count * price());
        save();
        return null;
    }

    public long price() {
        return plugin.getConfig().getLong("casino.chip-price", 100);
    }

    /** Nimmt Chips weg, etwa für den Einsatz. False, wenn nicht genug da ist. */
    public synchronized boolean takeChips(UUID id, long count) {
        long have = chips(id);
        if (count <= 0 || have < count) return false;
        chips.put(id, have - count);
        save();
        return true;
    }

    /** Gibt Chips zurück, etwa eine Auszahlung. */
    public synchronized void giveChips(UUID id, long count) {
        if (count <= 0) return;
        chips.merge(id, count, Long::sum);
        save();
    }

    /** Setzt den Blackjack-Tisch auf den angegebenen Block. */
    public synchronized void setTable(Location block) {
        tisch = format(block);
        save();
    }

    /** Position des Blackjack-Tischs (Block-Mitte am Boden), null wenn nicht gesetzt oder die Welt fehlt. */
    public Location tableLocation() {
        return parse(tisch);
    }

    /** Setzt den Kassentisch auf den angegebenen Block und zeigt dort den Jackpot an. */
    public synchronized void setKasse(Location block) {
        kasse = format(block);
        save();
        updateDisplay();
    }

    public boolean nearKasse(Location l) {
        Location k = kasseLocation();
        if (k == null || l.getWorld() == null || !l.getWorld().equals(k.getWorld())) return false;
        double r = plugin.getConfig().getDouble("casino.kasse-radius", 5);
        return l.distanceSquared(k) <= r * r;
    }

    /** Stellt die Jackpot-Anzeige nach einem Neustart wieder her. */
    public synchronized void respawn() {
        updateDisplay();
    }

    private Location kasseLocation() {
        return parse(kasse);
    }

    private static String format(Location block) {
        return block.getWorld().getName() + ";" + block.getBlockX() + ";" + block.getBlockY() + ";" + block.getBlockZ();
    }

    private static Location parse(String raw) {
        if (raw == null) return null;
        String[] p = raw.split(";");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w, Integer.parseInt(p[1]) + 0.5, Integer.parseInt(p[2]), Integer.parseInt(p[3]) + 0.5);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Aktualisiert den schwebenden Jackpot-Text über dem Kassentisch oder legt ihn neu an. */
    private void updateDisplay() {
        Location k = kasseLocation();
        if (k == null || k.getWorld() == null || !k.getWorld().isChunkLoaded(k.getBlockX() >> 4, k.getBlockZ() >> 4)) return;
        Location at = k.clone().add(0, 1.6, 0);
        TextDisplay display = null;
        for (TextDisplay d : k.getWorld().getNearbyEntitiesByType(TextDisplay.class, at, 1)) {
            if (d.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) {
                display = d;
                break;
            }
        }
        if (display == null) {
            display = k.getWorld().spawn(at, TextDisplay.class, d -> {
                d.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
                d.setPersistent(true);
            });
        }
        display.text(Msg.parse("<gold>JACKPOT <yellow>" + Msg.coins(jackpot) + "$"));
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : chips.entrySet()) {
            out.set("chips." + e.getKey(), e.getValue());
        }
        out.set("jackpot", jackpot);
        if (kasse != null) out.set("kasse", kasse);
        if (tisch != null) out.set("tisch", tisch);
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("casino.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}

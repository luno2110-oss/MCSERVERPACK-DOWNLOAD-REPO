package de.kuby.kubysmp.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Kopfgelder: $ auf den Kopf eines Spielers. Gilt nur im Modus, in dem das Kopfgeld gesetzt wurde. */
public final class BountyService {

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Long> bounties = new HashMap<>();
    private final Map<UUID, String> modes = new HashMap<>();

    public BountyService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bounties.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            UUID id = UUID.fromString(key);
            ConfigurationSection sec = yml.getConfigurationSection(key);
            if (sec != null) {
                bounties.put(id, sec.getLong("amount"));
                modes.put(id, sec.getString("mode", "smp"));
            } else {
                // altes Format: nur der Betrag, gehört zum SMP
                bounties.put(id, yml.getLong(key));
                modes.put(id, "smp");
            }
        }
    }

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String place(UUID placer, UUID target, long amount) {
        if (placer.equals(target)) return "Du kannst kein Kopfgeld auf dich selbst setzen.";
        if (amount < 100) return "Mindestbetrag ist 100$.";
        String mode = plugin.modes().modeOf(placer);
        if (!mode.equals(plugin.modes().modeOf(target))) return "Der Spieler ist gerade in einem anderen Modus.";
        if (bounties.containsKey(target) && !mode.equals(modes.get(target))) {
            return "Auf diesen Spieler läuft schon ein Kopfgeld in einem anderen Modus.";
        }
        if (!plugin.economy().withdraw(placer, amount)) return "Nicht genug$.";
        bounties.merge(target, amount, Long::sum);
        modes.put(target, mode);
        save();
        return null;
    }

    /** Liefert den Betrag, der beim Tod im selben Modus ausgezahlt wird, und entfernt ihn. */
    public synchronized long claim(UUID victim) {
        if (!bounties.containsKey(victim)) return 0;
        if (!plugin.modes().modeOf(victim).equals(modes.get(victim))) return 0;
        Long amount = bounties.remove(victim);
        modes.remove(victim);
        save();
        return amount == null ? 0 : amount;
    }

    public synchronized long of(UUID target) {
        return bounties.getOrDefault(target, 0L);
    }

    public synchronized Map<UUID, Long> all() {
        return new HashMap<>(bounties);
    }

    /** Nur Kopfgelder des angegebenen Modus. */
    public synchronized Map<UUID, Long> allIn(String mode) {
        Map<UUID, Long> out = new HashMap<>();
        bounties.forEach((id, v) -> { if (mode.equals(modes.get(id))) out.put(id, v); });
        return out;
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        bounties.forEach((id, v) -> {
            out.set(id + ".amount", v);
            out.set(id + ".mode", modes.getOrDefault(id, "smp"));
        });
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("bounties.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}

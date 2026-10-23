package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Freundschaften pro Modus. Freunde gelten nur im Modus, in dem sie geschlossen wurden.
 * Anfragen gehen nur zwischen Spielern im selben Modus. Offene Anfragen bleiben nur bis zum Neustart.
 */
public final class FriendService {

    private final KubyPlugins plugin;
    private final File file;
    private final Map<String, Set<UUID>> friends = new HashMap<>();   // Schlüssel: modus|uuid
    private final Map<String, Set<UUID>> requests = new HashMap<>();  // Schlüssel: modus|uuid

    public FriendService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "friends.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            String fk = key.contains("|") ? key : "smp|" + key;   // altes Format gehört zum SMP
            Set<UUID> set = new HashSet<>();
            for (String s : yml.getStringList(key)) set.add(UUID.fromString(s));
            friends.put(fk, set);
        }
    }

    private String mode(UUID id) {
        return plugin.modes().modeOf(id);
    }

    private static String fk(String mode, UUID id) {
        return mode + "|" + id;
    }

    public synchronized boolean areFriends(UUID a, UUID b) {
        if (!mode(a).equals(mode(b))) return false;
        return friends.getOrDefault(fk(mode(a), a), Set.of()).contains(b);
    }

    public synchronized Set<UUID> friendsOf(UUID id) {
        return new HashSet<>(friends.getOrDefault(fk(mode(id), id), Set.of()));
    }

    public synchronized Set<UUID> requestsOf(UUID id) {
        return new HashSet<>(requests.getOrDefault(fk(mode(id), id), Set.of()));
    }

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String request(UUID from, UUID to) {
        if (from.equals(to)) return "Du kannst dir selbst keine Anfrage schicken.";
        if (!mode(from).equals(mode(to))) return "Der Spieler ist gerade in einem anderen Modus.";
        if (areFriends(from, to)) return "Ihr seid schon befreundet.";
        requests.computeIfAbsent(fk(mode(to), to), k -> new HashSet<>()).add(from);
        return null;
    }

    public synchronized String accept(UUID me, UUID from) {
        String m = mode(me);
        if (!m.equals(mode(from))) return "Der Spieler ist gerade in einem anderen Modus.";
        Set<UUID> pending = requests.get(fk(m, me));
        if (pending == null || !pending.remove(from)) return "Keine Anfrage von diesem Spieler.";
        friends.computeIfAbsent(fk(m, me), k -> new HashSet<>()).add(from);
        friends.computeIfAbsent(fk(m, from), k -> new HashSet<>()).add(me);
        save();
        return null;
    }

    public synchronized void remove(UUID a, UUID b) {
        String m = mode(a);
        friends.getOrDefault(fk(m, a), new HashSet<>()).remove(b);
        friends.getOrDefault(fk(m, b), new HashSet<>()).remove(a);
        save();
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        friends.forEach((key, set) -> {
            if (!set.isEmpty()) out.set(key, set.stream().map(UUID::toString).toList());
        });
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("friends.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}

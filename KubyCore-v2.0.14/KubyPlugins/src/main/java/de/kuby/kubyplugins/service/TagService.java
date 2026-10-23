package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Titel neben dem Namen. Liga-Titel gibt es automatisch ab der Trophäenzahl, andere werden freigeschaltet. */
public final class TagService implements ModeData {

    public record Tag(String id, String display) {}

    public static final List<Tag> LEAGUE_TAGS = java.util.Arrays.stream(League.values())
            .map(l -> new Tag(l.tagId(), l.display())).toList();

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Set<String>> unlocked = new HashMap<>();
    private final Map<UUID, String> active = new HashMap<>();

    public TagService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tags.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            UUID id = UUID.fromString(key);
            unlocked.put(id, new HashSet<>(yml.getStringList(key + ".unlocked")));
            String a = yml.getString(key + ".active");
            if (a != null) active.put(id, a);
        }
    }

    public List<Tag> all() {
        List<Tag> list = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList("tags")) {
            String[] p = raw.split(";", 2);
            if (p.length == 2) list.add(new Tag(p[0].trim(), p[1].trim()));
        }
        list.addAll(LEAGUE_TAGS);
        return list;
    }

    public synchronized boolean has(UUID id, String tagId) {
        for (League l : League.values()) {
            if (l.tagId().equals(tagId)) return plugin.stats().get(id).trophies >= l.min();
        }
        return unlocked.getOrDefault(id, Set.of()).contains(tagId);
    }

    public String displayOf(String tagId) {
        for (Tag t : all()) {
            if (t.id().equals(tagId)) return t.display();
        }
        return null;
    }

    public synchronized String activeId(UUID id) {
        return active.get(id);
    }

    public synchronized String activeDisplay(UUID id) {
        String a = active.get(id);
        return a == null ? null : displayOf(a);
    }

    /** tagId null entfernt den Titel. Gibt null bei Erfolg zurück. */
    public synchronized String setActive(UUID id, String tagId) {
        if (tagId == null) {
            active.remove(id);
            save();
            return null;
        }
        if (displayOf(tagId) == null) return "Diesen Titel gibt es nicht.";
        if (!has(id, tagId)) return "Dieser Titel ist noch nicht freigeschaltet.";
        active.put(id, tagId);
        save();
        return null;
    }

    public synchronized void grant(UUID id, String tagId) {
        unlocked.computeIfAbsent(id, k -> new HashSet<>()).add(tagId);
        save();
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        unlocked.forEach((id, set) -> out.set(id + ".unlocked", new ArrayList<>(set)));
        active.forEach((id, tag) -> out.set(id + ".active", tag));
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("tags.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    @Override
    public String key() { return "tags"; }

    @Override
    public synchronized Map<String, Object> export(UUID id) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        Set<String> s = unlocked.get(id);
        if (s != null) for (String t : s) m.put("tag|" + t, true);
        if (active.containsKey(id)) m.put("active", active.get(id));
        return m;
    }

    @Override
    public synchronized void importData(UUID id, Map<String, Object> m) {
        unlocked.remove(id);
        active.remove(id);
        if (m != null) {
            Set<String> s = new java.util.HashSet<>();
            m.forEach((k, v) -> { if (k.startsWith("tag|")) s.add(k.substring(4)); });
            if (!s.isEmpty()) unlocked.put(id, s);
            if (m.get("active") != null) active.put(id, m.get("active").toString());
        }
        save();
    }
}

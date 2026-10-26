package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Wochen-Ereignisse, jeweils Montag 00:05 Berliner Zeit: Juwelen für Baltop-Platz 1 und das neue Wochen-Meta. */
public final class WeeklyService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, String> votes = new HashMap<>();
    private String lastReset = "";
    private String metaItem;

    public WeeklyService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "weekly.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        lastReset = yml.getString("lastReset", "");
        metaItem = yml.getString("meta");
        ConfigurationSection vs = yml.getConfigurationSection("votes");
        if (vs != null) {
            for (String k : vs.getKeys(false)) votes.put(UUID.fromString(k), vs.getString(k));
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::check, 1200L, 1200L);
    }

    public List<Material> candidates() {
        List<Material> list = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList("meta.candidates")) {
            Material m = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
            if (m != null && m.isItem()) list.add(m);
        }
        return list;
    }

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String vote(UUID id, Material m) {
        if (!plugin.discord().linkedOrDisabled(id, "require-for-meta")) return "Für die Abstimmung brauchst du einen verknüpften Discord-Account.";
        if (!candidates().contains(m)) return "Dieses Item steht nicht zur Wahl.";
        votes.put(id, m.name());
        save();
        return null;
    }

    public synchronized Map<Material, Integer> tally() {
        Map<Material, Integer> t = new HashMap<>();
        for (String name : votes.values()) {
            Material m = Material.matchMaterial(name);
            if (m != null) t.merge(m, 1, Integer::sum);
        }
        return t;
    }

    public synchronized Material myVote(UUID id) {
        String v = votes.get(id);
        return v == null ? null : Material.matchMaterial(v);
    }

    public synchronized Material activeMeta() {
        return metaItem == null ? null : Material.matchMaterial(metaItem);
    }

    public boolean isMeta(Material m) {
        return m != null && m == activeMeta();
    }

    private void check() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        if (now.getDayOfWeek() != DayOfWeek.MONDAY || now.getHour() != 0 || now.getMinute() < 5) return;
        String key = now.get(IsoFields.WEEK_BASED_YEAR) + "-W" + now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (!key.equals(lastReset)) reset(key);
    }

    private synchronized void reset(String key) {
        List<Map.Entry<UUID, Long>> top = plugin.economy().top(1);
        if (!top.isEmpty()) {
            UUID winner = top.get(0).getKey();
            long reward = plugin.getConfig().getLong("weekly.jewel-reward", 1000);
            plugin.jewels().add(winner, reward);
            plugin.notifications().send("notify.broadcast", "<aqua>Wochenpreis: <white>" + Msg.escape(name(winner))
                    + " <gray>erhält <aqua>" + reward + " Juwelen <gray>für Platz 1 im Baltop!");
        }

        Material best = null;
        int bestVotes = 0;
        for (Map.Entry<Material, Integer> e : tally().entrySet()) {
            if (e.getValue() > bestVotes) {
                bestVotes = e.getValue();
                best = e.getKey();
            }
        }
        metaItem = best == null ? null : best.name();
        votes.clear();
        lastReset = key;
        save();

        if (best != null) {
            plugin.notifications().send("notify.broadcast", "<gold>Neues Wochen-Meta: <white>" + best.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                    + " <gray>bringt <green>+25 % Verkaufspreis<gray>!");
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        out.set("lastReset", lastReset);
        if (metaItem != null) out.set("meta", metaItem);
        votes.forEach((id, m) -> out.set("votes." + id, m));
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("weekly.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private static String name(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "Unbekannt" : n;
    }
}

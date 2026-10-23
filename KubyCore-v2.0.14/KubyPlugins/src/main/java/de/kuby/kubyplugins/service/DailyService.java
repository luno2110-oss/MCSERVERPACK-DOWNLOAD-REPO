package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DailyService implements ModeData {

    public static final int CYCLE = 30;
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    public record Progress(int claimed, LocalDate last) {}

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Progress> data = new ConcurrentHashMap<>();

    public DailyService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "daily.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(key);
            if (s == null) continue;
            String last = s.getString("last");
            data.put(UUID.fromString(key),
                    new Progress(s.getInt("claimed"), last == null ? null : LocalDate.parse(last)));
        }
    }

    /** Anzahl abgeholter Tage im aktuellen Zyklus (0 bis 30). Bei verpasstem Tag: 0. */
    public int claimedDays(UUID id) {
        Progress p = data.get(id);
        if (p == null || p.last() == null) return 0;
        if (p.last().isBefore(today().minusDays(1))) return 0;
        return p.claimed();
    }

    public boolean canClaim(UUID id) {
        Progress p = data.get(id);
        return p == null || p.last() == null || !p.last().equals(today());
    }

    public long rewardFor(int day) {
        long reward = plugin.getConfig().getLong("daily.base-reward")
                + (long) day * plugin.getConfig().getLong("daily.reward-per-day");
        if (day == CYCLE) reward += plugin.getConfig().getLong("daily.cycle-bonus");
        return reward;
    }

    /** Liefert den abgeholten Tag oder 0, wenn heute schon abgeholt wurde. */
    public int claim(UUID id) {
        if (!canClaim(id)) return 0;
        int done = claimedDays(id);
        if (done >= CYCLE) done = 0;
        int day = done + 1;
        data.put(id, new Progress(day, today()));
        return day;
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        data.forEach((id, p) -> {
            yml.set(id + ".claimed", p.claimed());
            if (p.last() != null) yml.set(id + ".last", p.last().toString());
        });
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("daily.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    @Override
    public String key() { return "daily"; }

    @Override
    public synchronized Map<String, Object> export(UUID id) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        Progress p = data.get(id);
        if (p != null) {
            m.put("claimed", p.claimed());
            m.put("last", p.last() == null ? "" : p.last().toString());
        }
        return m;
    }

    @Override
    public synchronized void importData(UUID id, Map<String, Object> m) {
        data.remove(id);
        if (m != null && m.get("claimed") != null) {
            Object l = m.get("last");
            java.time.LocalDate last = (l == null || l.toString().isBlank()) ? null : java.time.LocalDate.parse(l.toString());
            data.put(id, new Progress(ModeData.integer(m.get("claimed")), last));
        }
        save();
    }
}

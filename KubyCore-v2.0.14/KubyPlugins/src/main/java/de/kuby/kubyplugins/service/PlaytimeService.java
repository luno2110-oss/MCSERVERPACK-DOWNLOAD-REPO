package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Spielzeit zählen und Belohnungen nach Minuten freischalten. Format: MINUTEN;ART;WERT (COINS, XP, JEWELS, TAG, COMMAND). */
public final class PlaytimeService {

    public record Reward(long minutes, String type, String value) {
        public String key() { return minutes + "|" + type + "|" + value; }
    }

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Long> minutes = new HashMap<>();
    private final Map<UUID, Set<String>> claimed = new HashMap<>();

    public PlaytimeService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playtime.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String k : yml.getKeys(false)) {
            UUID id = UUID.fromString(k);
            minutes.put(id, yml.getLong(k + ".minutes"));
            claimed.put(id, new HashSet<>(yml.getStringList(k + ".claimed")));
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1200L, 1200L);
    }

    private synchronized void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            minutes.merge(p.getUniqueId(), 1L, Long::sum);
        }
        save();
    }

    public synchronized long minutesOf(UUID id) {
        return minutes.getOrDefault(id, 0L);
    }

    public List<Reward> rewards() {
        List<Reward> list = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList("playtime.rewards")) {
            String[] p = raw.split(";", 3);
            if (p.length < 3) continue;
            try {
                list.add(new Reward(Long.parseLong(p[0].trim()), p[1].trim().toUpperCase(java.util.Locale.ROOT), p[2].trim()));
            } catch (NumberFormatException ignored) {
                plugin.getLogger().warning("Ungültige Spielzeit-Belohnung: " + raw);
            }
        }
        return list;
    }

    public synchronized boolean isClaimed(UUID id, Reward r) {
        return claimed.getOrDefault(id, Set.of()).contains(r.key());
    }

    /** Gibt null bei Erfolg zurück. */
    public synchronized String claim(Player p, Reward r) {
        UUID id = p.getUniqueId();
        if (minutesOf(id) < r.minutes()) return "Noch nicht erreicht.";
        if (isClaimed(id, r)) return "Schon abgeholt.";
        switch (r.type()) {
            case "COINS" -> plugin.economy().deposit(id, Long.parseLong(r.value()));
            case "XP" -> plugin.stats().get(id).xp += Long.parseLong(r.value());
            case "JEWELS" -> plugin.jewels().add(id, Long.parseLong(r.value()));
            case "TAG" -> plugin.tags().grant(id, r.value());
            case "COMMAND" -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                    r.value().replace("%player%", p.getName()));
            default -> { return "Unbekannte Belohnungsart."; }
        }
        claimed.computeIfAbsent(id, k -> new HashSet<>()).add(r.key());
        plugin.stats().save();
        save();
        Sfx.play(plugin, p, Sfx.Kind.LEVEL_UP);
        Msg.send(p, "<green>Spielzeit-Belohnung abgeholt!");
        return null;
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        minutes.forEach((id, m) -> out.set(id + ".minutes", m));
        claimed.forEach((id, set) -> out.set(id + ".claimed", new ArrayList<>(set)));
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("playtime.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}

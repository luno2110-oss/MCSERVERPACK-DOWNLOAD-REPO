package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Zweitkonto-Erkennung über die IP-Adresse. Gespeichert wird nur ein Hash der IP mit eigenem Geheimwert (nicht die IP
 * selbst), je Konto mit dem Zeitpunkt der letzten Nutzung. Zwei Konten gelten als verknüpft, wenn sie in den letzten
 * alts.tage (30) Tagen dieselbe IP hatten und nicht als Ausnahme freigegeben sind (/kp alts erlauben, z. B.
 * Geschwister).
 * <p>
 * Folgen (alts.sperren, Standard an): kein Kopfgeld, keine Trophäen/Kill-Wertung und keine Raid-Versicherung zwischen
 * verknüpften Konten, Willkommens-Juwelen nur für das erste Konto einer IP. Das Team (kuby.alts.see oder kuby.admin)
 * sieht beim Login, wenn jemand verknüpfte Konten hat, besonders deutlich bei einem gebannten Konto.
 */
public final class AltService implements Listener {

    private final KubyPlugins plugin;
    private final File file;
    /** IP-Hash → (Konto → zuletzt gesehen). */
    private final Map<String, Map<UUID, Long>> byIp = new HashMap<>();
    /** Freigegebene Paare „a;b“ (sortiert). */
    private final Set<String> allowed = new HashSet<>();
    private String salt;
    private boolean dirty;

    public AltService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "zweitkonten.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save();
        }, 1200L, 1200L);
    }

    private long window() {
        return Math.max(1, plugin.getConfig().getLong("alts.tage", 30)) * 86_400_000L;
    }

    public boolean enforce() {
        return plugin.getConfig().getBoolean("alts.sperren", true);
    }

    private String hash(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest(ip.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pair(UUID a, UUID b) {
        String x = a.toString(), y = b.toString();
        return x.compareTo(y) < 0 ? x + ";" + y : y + ";" + x;
    }

    // =================================================================================================================
    // Erfassen
    // =================================================================================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED || e.getAddress() == null) return;
        String h = hash(e.getAddress().getHostAddress());
        synchronized (this) {
            byIp.computeIfAbsent(h, k -> new HashMap<>()).put(e.getUniqueId(), System.currentTimeMillis());
            dirty = true;
        }
    }

    /** Beim Betreten: das Team sieht verknüpfte Konten. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        List<UUID> alts = alts(p.getUniqueId());
        if (alts.isEmpty()) return;
        List<String> names = new ArrayList<>();
        boolean banned = false;
        for (UUID u : alts) {
            OfflinePlayer o = Bukkit.getOfflinePlayer(u);
            boolean b = o.isBanned();
            banned |= b;
            names.add((b ? "<red>" : "<white>") + (o.getName() == null ? u.toString().substring(0, 8) : o.getName()) + (b ? " (gebannt)" : ""));
        }
        String msg = (banned ? "<red><b>⚠ ZWEITKONTO</b> " : "<gold>⚠ Zweitkonto? ") + "<white>" + p.getName()
                + " <gray>teilt die IP mit " + String.join("<gray>, ", names) + "<gray>. <click:run_command:'/kp alts " + p.getName()
                + "'><yellow>[Details]</click>";
        plugin.getLogger().info("[Zweitkonten] " + p.getName() + " teilt die IP mit " + alts.size() + " Konto/Konten" + (banned ? " (eins gebannt)" : ""));
        if (banned && plugin.alerts() != null) {
            plugin.alerts().raise(AlertService.Kind.ZWEITKONTO, p.getUniqueId(), p.getName() + " teilt die IP mit einem gebannten Konto");
        }
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(p) && (o.hasPermission("kuby.alts.see") || o.hasPermission("kuby.admin"))) Msg.send(o, msg);
        }
    }

    // =================================================================================================================
    // Abfragen
    // =================================================================================================================

    /** Andere Konten, die in den letzten alts.tage Tagen dieselbe IP hatten (ohne Freigaben). */
    public synchronized List<UUID> alts(UUID id) {
        long since = System.currentTimeMillis() - window();
        Set<UUID> out = new HashSet<>();
        for (Map<UUID, Long> m : byIp.values()) {
            Long mine = m.get(id);
            if (mine == null || mine < since) continue;
            for (Map.Entry<UUID, Long> e : m.entrySet()) {
                if (e.getKey().equals(id) || e.getValue() < since) continue;
                if (allowed.contains(pair(id, e.getKey()))) continue;
                out.add(e.getKey());
            }
        }
        return new ArrayList<>(out);
    }

    /** Sind a und b verknüpft (gleiche IP, keine Freigabe)? */
    public boolean linked(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) return false;
        return alts(a).contains(b);
    }

    /** Für Sperren: verknüpft und Sperren eingeschaltet. */
    public boolean blocks(UUID a, UUID b) {
        return enforce() && linked(a, b);
    }

    /** Gibt es schon ein anderes Konto mit dieser IP (für einmalige Geschenke)? */
    public boolean hasOther(UUID id) {
        return enforce() && !alts(id).isEmpty();
    }

    public synchronized void allow(UUID a, UUID b, boolean on) {
        if (on) allowed.add(pair(a, b));
        else allowed.remove(pair(a, b));
        dirty = true;
        save();
    }

    /** Ein Konto mit gleicher IP: zuletzt gesehen, freigegeben, noch im Zeitraum. */
    public record Link(UUID other, long lastSeen, boolean allowed, boolean recent) {}

    /** Alle Konten, die je dieselbe IP hatten (auch freigegebene und alte), zuletzt gesehen zuerst. */
    public synchronized List<Link> links(UUID id) {
        Map<UUID, Long> seen = new HashMap<>();
        for (Map<UUID, Long> m : byIp.values()) {
            if (!m.containsKey(id)) continue;
            for (Map.Entry<UUID, Long> e : m.entrySet()) {
                if (!e.getKey().equals(id)) seen.merge(e.getKey(), e.getValue(), Math::max);
            }
        }
        long since = System.currentTimeMillis() - window();
        List<Link> out = new ArrayList<>();
        seen.forEach((u, t) -> out.add(new Link(u, t, allowed.contains(pair(id, u)), t >= since)));
        out.sort((a, b) -> Long.compare(b.lastSeen(), a.lastSeen()));
        return out;
    }

    public synchronized boolean isAllowed(UUID a, UUID b) {
        return allowed.contains(pair(a, b));
    }

    /** Für /kp alts: Zeilen mit Name, zuletzt gesehen, gebannt, freigegeben. */
    public synchronized List<String> describe(UUID id) {
        List<String> out = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        long since = System.currentTimeMillis() - window();
        for (Map<UUID, Long> m : byIp.values()) {
            if (!m.containsKey(id)) continue;
            for (Map.Entry<UUID, Long> e : m.entrySet()) {
                if (e.getKey().equals(id) || !seen.add(e.getKey())) continue;
                OfflinePlayer o = Bukkit.getOfflinePlayer(e.getKey());
                long days = (System.currentTimeMillis() - e.getValue()) / 86_400_000L;
                String state = allowed.contains(pair(id, e.getKey())) ? "<green>freigegeben" : e.getValue() < since ? "<gray>alt (über "
                        + plugin.getConfig().getLong("alts.tage", 30) + " Tage)" : "<gold>verknüpft";
                out.add("<white>" + (o.getName() == null ? e.getKey() : o.getName()) + " <dark_gray>· " + state + " <dark_gray>· <gray>zuletzt vor "
                        + days + " Tag(en)" + (o.isBanned() ? " <red>· gebannt" : ""));
            }
        }
        return out;
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private synchronized void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        salt = y.getString("geheim");
        if (salt == null || salt.length() < 16) {
            byte[] b = new byte[24];
            new SecureRandom().nextBytes(b);
            salt = HexFormat.of().formatHex(b);
            dirty = true;
        }
        long drop = System.currentTimeMillis() - 365L * 86_400_000L;
        ConfigurationSection ips = y.getConfigurationSection("ips");
        if (ips != null) {
            for (String h : ips.getKeys(false)) {
                ConfigurationSection s = ips.getConfigurationSection(h);
                if (s == null) continue;
                Map<UUID, Long> m = new HashMap<>();
                for (String k : s.getKeys(false)) {
                    try {
                        long t = s.getLong(k);
                        if (t > drop) m.put(UUID.fromString(k), t);
                    } catch (IllegalArgumentException ignored) {
                        // kaputter Eintrag
                    }
                }
                if (!m.isEmpty()) byIp.put(h, m);
            }
        }
        allowed.addAll(y.getStringList("freigaben"));
        if (dirty) save();
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Zweitkonto-Erkennung: nur Hashes der IP-Adressen (mit dem Geheimwert unten), keine IPs.",
                "Den Geheimwert nicht weitergeben und nicht ändern (sonst passen die alten Einträge nicht mehr)."));
        y.set("geheim", salt);
        byIp.forEach((h, m) -> m.forEach((u, t) -> y.set("ips." + h + "." + u, t)));
        y.set("freigaben", new ArrayList<>(allowed));
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("zweitkonten.yml: " + e.getMessage());
        }
    }
}

package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Team-Warnungen bei Auffälligkeiten: große Gutschriften, viel Geld in kurzer Zeit, große Überweisungen, Geld zwischen
 * Zweitkonten, Login mit gebanntem Zweitkonto, fehlgeschlagene Backups. Online-Team (kuby.alerts.see oder Admin)
 * bekommt eine Chat-Zeile mit [Ansehen]; alle Warnungen stehen im Kadmin unter „Warnungen“ (erledigen, Akte öffnen).
 * Gespeichert in warnungen.yml (die letzten 300).
 */
public final class AlertService {

    public enum Kind {
        GROSSE_GUTSCHRIFT("Große Gutschrift", "#F4D46A"),
        SCHNELLES_GELD("Viel Geld in kurzer Zeit", "#FF9A4A"),
        UEBERWEISUNG("Große Überweisung", "#F4D46A"),
        ZWEITKONTO("Zweitkonto", "#FF5A5A"),
        BACKUP("Backup", "#FF5A5A");

        public final String label, color;

        Kind(String label, String color) {
            this.label = label;
            this.color = color;
        }
    }

    public static final class Alert {
        public final long id, time;
        public final Kind kind;
        public final UUID player;
        public final String text;
        public boolean done;

        Alert(long id, long time, Kind kind, UUID player, String text, boolean done) {
            this.id = id;
            this.time = time;
            this.kind = kind;
            this.player = player;
            this.text = text;
            this.done = done;
        }
    }

    private static final int KEEP = 300;

    private final KubyPlugins plugin;
    private final File file;
    private final List<Alert> alerts = new ArrayList<>();
    private long nextId = 1;

    public AlertService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "warnungen.yml");
        load();
    }

    /** Neue Warnung (von jedem Thread aus; Meldung an das Team auf dem Hauptthread). */
    public void raise(Kind kind, UUID player, String text) {
        Alert a;
        synchronized (this) {
            a = new Alert(nextId++, System.currentTimeMillis(), kind, player, text, false);
            alerts.add(0, a);
            while (alerts.size() > KEEP) alerts.remove(alerts.size() - 1);
        }
        plugin.getLogger().warning("[Warnung] " + kind.label + ": " + text);
        Runnable notify = () -> {
            save();
            String line = "<" + kind.color + "><b>⚠ " + kind.label.toUpperCase(java.util.Locale.ROOT) + "</b> <white>" + Msg.escape(text)
                    + " <click:run_command:'/kadmin warnungen'><yellow>[Ansehen]</click>";
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (o.hasPermission("kuby.alerts.see") || o.hasPermission("kuby.admin")) {
                    Msg.send(o, line);
                    Sfx.play(plugin, o, Sfx.Kind.PING);
                }
            }
        };
        if (Bukkit.isPrimaryThread()) notify.run();
        else Bukkit.getScheduler().runTask(plugin, notify);
    }

    public synchronized List<Alert> list(boolean onlyOpen) {
        List<Alert> out = new ArrayList<>();
        for (Alert a : alerts) if (!onlyOpen || !a.done) out.add(a);
        return out;
    }

    public synchronized int open() {
        int n = 0;
        for (Alert a : alerts) if (!a.done) n++;
        return n;
    }

    public synchronized void done(long id) {
        for (Alert a : alerts) if (a.id == id) a.done = true;
        save();
    }

    public synchronized void doneAll() {
        for (Alert a : alerts) a.done = true;
        save();
    }

    private synchronized void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = y.getLong("naechste", 1);
        ConfigurationSection s = y.getConfigurationSection("warnungen");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            ConfigurationSection a = s.getConfigurationSection(k);
            if (a == null) continue;
            try {
                String p = a.getString("spieler");
                alerts.add(new Alert(Long.parseLong(k), a.getLong("zeit"), Kind.valueOf(a.getString("art", "BACKUP")),
                        p == null || p.isEmpty() ? null : UUID.fromString(p), a.getString("text", ""), a.getBoolean("erledigt")));
            } catch (IllegalArgumentException ignored) {
                // kaputter Eintrag
            }
        }
        alerts.sort((x, z) -> Long.compare(z.time, x.time));
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("naechste", nextId);
        for (Alert a : alerts) {
            String b = "warnungen." + a.id + ".";
            y.set(b + "zeit", a.time);
            y.set(b + "art", a.kind.name());
            y.set(b + "spieler", a.player == null ? "" : a.player.toString());
            y.set(b + "text", a.text);
            y.set(b + "erledigt", a.done);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("warnungen.yml: " + e.getMessage());
        }
    }
}

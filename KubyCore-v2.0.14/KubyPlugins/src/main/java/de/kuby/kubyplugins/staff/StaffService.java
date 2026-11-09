package de.kuby.kubyplugins.staff;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Team-Bereich („Discord im Spiel“, nur für Teamler): Kanäle mit Nachrichten (#allgemein, #moderation, #admin,
 * #ankündigungen; wer was lesen und schreiben darf, hängt an der Rolle), gespeicherte Meldungen aus /report mit Stand
 * und Bearbeiter, ein Ereignis-Feed (neue Meldung, Ticket ans Team, Ankündigung) und Hinweise an Teamler im Chat.
 * Tickets und Helfer kommen aus dem Support (SupportService). Daten: staff.yml (im Hintergrund gespeichert).
 *
 * Rollen (Permission): Admin (kuby.admin), Moderator (kuby.support.team), Team (kuby.staff oder kuby.report.see).
 */
public final class StaffService {

    public enum Role {
        TEAM("Team", "#8EC8F0"), MODERATOR("Moderator", "#6BF07A"), ADMIN("Admin", "#FF5A5A");

        public final String label, color;

        Role(String label, String color) {
            this.label = label;
            this.color = color;
        }
    }

    /** Ein Kanal: Kennung, Name, wer lesen (read) und schreiben (write) darf. */
    public enum Channel {
        ALLGEMEIN("allgemein", "Alles fürs ganze Team", Role.TEAM, Role.TEAM),
        MODERATION("moderation", "Meldungen, Strafen, schwierige Fälle", Role.MODERATOR, Role.MODERATOR),
        ADMIN("admin", "Nur für Admins", Role.ADMIN, Role.ADMIN),
        ANKUENDIGUNGEN("ankündigungen", "Wichtiges von der Leitung (nur Admins schreiben)", Role.TEAM, Role.ADMIN);

        public final String key, desc;
        public final Role read, write;

        Channel(String key, String desc, Role read, Role write) {
            this.key = key;
            this.desc = desc;
            this.read = read;
            this.write = write;
        }

        public static Channel of(String s) {
            for (Channel c : values()) if (c.key.equalsIgnoreCase(s) || c.name().equalsIgnoreCase(s)) return c;
            return null;
        }
    }

    public record Message(long time, UUID author, String text) {}

    public enum ReportStatus {
        OFFEN("offen"), IN_ARBEIT("in Arbeit"), ERLEDIGT("erledigt");

        public final String label;

        ReportStatus(String label) {
            this.label = label;
        }
    }

    public static final class Report {
        public final int id;
        public final long time;
        public final UUID reporter, target;
        public final String reason, world;
        public final int x, y, z;
        public ReportStatus status = ReportStatus.OFFEN;
        public UUID handler;
        public String note = "";

        Report(int id, long time, UUID reporter, UUID target, String reason, String world, int x, int y, int z) {
            this.id = id;
            this.time = time;
            this.reporter = reporter;
            this.target = target;
            this.reason = reason;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** Ereignis im Feed: Zeit, Art (meldung, ticket, strafe, info), Text. */
    public record Event(long time, String kind, String text) {}

    /** Verwarnung: Zeit, von wem, Grund. */
    public record Warn(long time, UUID by, String reason) {}

    private static final int KEEP_MESSAGES = 200, KEEP_REPORTS = 500, KEEP_EVENTS = 50;

    private final KubyPlugins plugin;
    private final File file;
    private final Map<Channel, Deque<Message>> messages = new LinkedHashMap<>();
    private final Map<Integer, Report> reports = new LinkedHashMap<>();
    private final Deque<Event> events = new ArrayDeque<>();
    private final Map<UUID, List<Warn>> warns = new HashMap<>();
    /** Zuletzt gelesen je Spieler und Kanal (Zeit). */
    private final Map<UUID, Map<Channel, Long>> read = new HashMap<>();
    /** Spieler, die Kanal-Nachrichten nicht im Chat sehen wollen. */
    private final Map<UUID, Boolean> muted = new HashMap<>();
    private int nextReport = 1;
    private boolean dirty;
    private volatile boolean saving;

    public StaffService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "staff.yml");
        for (Channel c : Channel.values()) messages.put(c, new ArrayDeque<>());
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) saveAsync();
        }, 1200L, 1200L);
    }

    // =================================================================================================================
    // Rollen
    // =================================================================================================================

    /** Rolle des Spielers im Team (null = kein Teamler). */
    public Role role(Player p) {
        if (p.hasPermission("kuby.admin")) return Role.ADMIN;
        if (p.hasPermission("kuby.support.team")) return Role.MODERATOR;
        if (p.hasPermission("kuby.staff") || p.hasPermission("kuby.report.see")) return Role.TEAM;
        return null;
    }

    public boolean isStaff(Player p) {
        return role(p) != null;
    }

    public boolean canRead(Player p, Channel c) {
        Role r = role(p);
        return r != null && r.ordinal() >= c.read.ordinal();
    }

    public boolean canWrite(Player p, Channel c) {
        Role r = role(p);
        return r != null && r.ordinal() >= c.write.ordinal();
    }

    public List<Player> onlineStaff() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (isStaff(p)) out.add(p);
        out.sort((a, b) -> {
            int r = role(b).ordinal() - role(a).ordinal();
            return r != 0 ? r : a.getName().compareToIgnoreCase(b.getName());
        });
        return out;
    }

    public static String nameOf(UUID id) {
        if (id == null) return "-";
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() == null ? "?" : op.getName();
    }

    // =================================================================================================================
    // Kanäle
    // =================================================================================================================

    public synchronized List<Message> messages(Channel c) {
        return new ArrayList<>(messages.get(c));
    }

    public synchronized int unread(Player p, Channel c) {
        if (!canRead(p, c)) return 0;
        long since = read.getOrDefault(p.getUniqueId(), Map.of()).getOrDefault(c, 0L);
        int n = 0;
        for (Message m : messages.get(c)) if (m.time() > since && !p.getUniqueId().equals(m.author())) n++;
        return n;
    }

    public synchronized int unreadAll(Player p) {
        int n = 0;
        for (Channel c : Channel.values()) n += unread(p, c);
        return n;
    }

    public synchronized void markRead(Player p, Channel c) {
        read.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(c, System.currentTimeMillis());
        dirty = true;
    }

    /** Nachricht schreiben. Gibt null bei Erfolg zurück. */
    public String post(Player p, Channel c, String text) {
        if (!canWrite(p, c)) return "In #" + c.key + " darfst du nicht schreiben.";
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return "Die Nachricht ist leer.";
        if (t.length() > 256) t = t.substring(0, 256);
        Message m = new Message(System.currentTimeMillis(), p.getUniqueId(), t);
        synchronized (this) {
            Deque<Message> d = messages.get(c);
            d.addLast(m);
            while (d.size() > KEEP_MESSAGES) d.pollFirst();
            read.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(c, m.time());
            dirty = true;
        }
        if (c == Channel.ANKUENDIGUNGEN) event("info", p.getName() + " in #ankündigungen: " + t);
        Role r = role(p);
        String line = "<#F2B829><b>#" + c.key + "</b> <" + (r == null ? "#ACA48E" : r.color) + ">" + Msg.escape(p.getName())
                + "<dark_gray>: <#E8E0D8>" + Msg.escape(t) + " <click:run_command:'/tb kanal " + c.key + "'><dark_gray>[öffnen]</click>";
        for (Player o : onlineStaff()) {
            if (!canRead(o, c) || o.equals(p) || Boolean.TRUE.equals(muted.get(o.getUniqueId()))) continue;
            Msg.send(o, line);
            Sfx.play(plugin, o, Sfx.Kind.TICK);
        }
        return null;
    }

    public synchronized boolean muted(UUID id) {
        return Boolean.TRUE.equals(muted.get(id));
    }

    public synchronized void toggleMute(UUID id) {
        muted.put(id, !Boolean.TRUE.equals(muted.get(id)));
        dirty = true;
    }

    // =================================================================================================================
    // Meldungen
    // =================================================================================================================

    /** Neue Meldung aus /report: speichern, Team benachrichtigen. */
    public Report report(Player reporter, UUID target, String reason) {
        Report r;
        synchronized (this) {
            var l = reporter.getLocation();
            r = new Report(nextReport++, System.currentTimeMillis(), reporter.getUniqueId(), target, reason, l.getWorld().getName(),
                    l.getBlockX(), l.getBlockY(), l.getBlockZ());
            reports.put(r.id, r);
            while (reports.size() > KEEP_REPORTS) reports.remove(reports.keySet().iterator().next());
            dirty = true;
        }
        event("meldung", "Meldung #" + r.id + ": " + reporter.getName() + " meldet " + nameOf(target) + " (" + reason + ")",
                "/tb meldung " + r.id, true);
        return r;
    }

    public synchronized List<Report> reports(boolean openOnly) {
        List<Report> out = new ArrayList<>();
        for (Report r : reports.values()) if (!openOnly || r.status != ReportStatus.ERLEDIGT) out.add(r);
        out.sort((a, b) -> Long.compare(b.time, a.time));
        return out;
    }

    public synchronized Report reportById(int id) {
        return reports.get(id);
    }

    public synchronized int openReports() {
        int n = 0;
        for (Report r : reports.values()) if (r.status == ReportStatus.OFFEN) n++;
        return n;
    }

    public synchronized void claim(Player p, Report r) {
        r.status = ReportStatus.IN_ARBEIT;
        r.handler = p.getUniqueId();
        dirty = true;
    }

    /** Erledigte Meldung wieder öffnen. */
    public synchronized void reopen(Report r) {
        r.status = r.handler == null ? ReportStatus.OFFEN : ReportStatus.IN_ARBEIT;
        dirty = true;
    }

    /** Meldungen gegen einen Spieler, neueste zuerst. */
    public synchronized List<Report> reportsAgainst(UUID target) {
        List<Report> out = new ArrayList<>();
        for (Report r : reports.values()) if (r.target.equals(target)) out.add(r);
        out.sort((a, b) -> Long.compare(b.time, a.time));
        return out;
    }

    /** Offene (nicht erledigte) Meldungen gegen einen Spieler. */
    public synchronized int openAgainst(UUID target) {
        int n = 0;
        for (Report r : reports.values()) if (r.target.equals(target) && r.status != ReportStatus.ERLEDIGT) n++;
        return n;
    }

    public synchronized int inWork() {
        int n = 0;
        for (Report r : reports.values()) if (r.status == ReportStatus.IN_ARBEIT) n++;
        return n;
    }

    public synchronized int totalReports() {
        return reports.size();
    }

    // =================================================================================================================
    // Verwarnungen und Kicks
    // =================================================================================================================

    /** Verwarnen: speichern, Spieler informieren (wenn online), Team-Hinweis. */
    public void warn(Player by, UUID target, String reason) {
        String why = reason == null || reason.isBlank() ? "kein Grund angegeben" : reason.trim();
        if (why.length() > 120) why = why.substring(0, 120);
        synchronized (this) {
            warns.computeIfAbsent(target, k -> new ArrayList<>()).add(new Warn(System.currentTimeMillis(), by.getUniqueId(), why));
            dirty = true;
        }
        Player t = Bukkit.getPlayer(target);
        if (t != null) {
            t.showTitle(net.kyori.adventure.title.Title.title(Msg.parse("<#FF5A5A><b>Verwarnung"), Msg.parse("<#E8E0D8>" + Msg.escape(why))));
            Msg.send(t, "<#FF5A5A><b>Verwarnung</b> <dark_gray>▶ <#E8E0D8>" + Msg.escape(why)
                    + " <gray>(Regeln: <click:run_command:'/regeln'><#F2B829>/regeln</click><gray>)");
            Sfx.play(plugin, t, Sfx.Kind.ERROR);
        }
        event("strafe", by.getName() + " verwarnt " + nameOf(target) + ": " + why, "/tb spieler " + nameOf(target), true);
    }

    public synchronized List<Warn> warns(UUID target) {
        List<Warn> l = new ArrayList<>(warns.getOrDefault(target, List.of()));
        l.sort((a, b) -> Long.compare(b.time(), a.time()));
        return l;
    }

    /** Kicken mit Grund (nur online). Gibt null bei Erfolg zurück. */
    public String kick(Player by, UUID target, String reason) {
        Player t = Bukkit.getPlayer(target);
        if (t == null) return "Der Spieler ist nicht online.";
        Role mine = role(by), theirs = role(t);
        if (theirs != null && (mine == null || theirs.ordinal() >= mine.ordinal())) return "Teamler mit gleicher oder höherer Rolle kannst du nicht kicken.";
        String why = reason == null || reason.isBlank() ? "Verstoß gegen die Regeln" : reason.trim();
        t.kick(Msg.parse("<#FF5A5A><b>Vom Server geworfen</b>\n\n<#E8E0D8>" + Msg.escape(why) + "\n\n<gray>Regeln: /regeln"));
        event("strafe", by.getName() + " kickt " + t.getName() + ": " + why, "/tb spieler " + t.getName(), true);
        return null;
    }

    public void done(Player p, Report r, String note) {
        synchronized (this) {
            r.status = ReportStatus.ERLEDIGT;
            if (r.handler == null) r.handler = p.getUniqueId();
            if (note != null && !note.isBlank()) r.note = note.trim();
            dirty = true;
        }
        event("meldung", "Meldung #" + r.id + " erledigt von " + p.getName() + (r.note.isEmpty() ? "" : ": " + r.note),
                "/tb meldung " + r.id, false);
    }

    // =================================================================================================================
    // Ereignisse
    // =================================================================================================================

    /** Ereignis in den Feed und als Hinweis an alle Teamler online (Meldungen und Tickets mit Ton). */
    public void event(String kind, String text) {
        event(kind, text, "/tb", true);
    }

    /** Farbe einer Ereignis-Art (Feed und Chat). */
    public static String kindColor(String kind) {
        return switch (kind) {
            case "meldung" -> "#FF5A5A";
            case "ticket" -> "#6BF07A";
            case "strafe" -> "#FF9A4A";
            default -> "#F2B829";
        };
    }

    /**
     * @param cmd    Befehl hinter „[öffnen]“ im Chat-Hinweis
     * @param notify false: nur in den Feed, kein Chat-Hinweis
     */
    public void event(String kind, String text, String cmd, boolean notify) {
        Event e = new Event(System.currentTimeMillis(), kind, text);
        synchronized (this) {
            events.addFirst(e);
            while (events.size() > KEEP_EVENTS) events.pollLast();
            dirty = true;
        }
        if (!notify) return;
        String line = "<#F2B829><b>TEAM</b> <dark_gray>▶ <" + kindColor(kind) + ">" + Msg.escape(text) + " <click:run_command:'"
                + (cmd == null ? "/tb" : cmd) + "'><hover:show_text:'Im Team-Bereich öffnen'><#F2B829>[öffnen]</hover></click>";
        for (Player o : onlineStaff()) {
            Msg.send(o, line);
            if (!kind.equals("info")) Sfx.play(plugin, o, Sfx.Kind.PING);
        }
    }

    public synchronized List<Event> events(int max) {
        List<Event> out = new ArrayList<>();
        for (Event e : events) {
            if (out.size() >= max) break;
            out.add(e);
        }
        return out;
    }

    /** Kurze Zusammenfassung beim Einloggen eines Teamlers. */
    public void greet(Player p) {
        if (!isStaff(p)) return;
        int rep = openReports();
        int team = plugin.support() == null ? 0 : plugin.support().queue(p, "team").size();
        int unread = unreadAll(p);
        if (rep + team + unread == 0) return;
        Msg.send(p, "<#F2B829><b>TEAM</b> <dark_gray>▶ <gray>" + rep + " offene Meldungen · " + team + " Tickets beim Team · " + unread
                + " neue Nachrichten <click:run_command:'/tb'><#F2B829>[Team-Bereich öffnen]</click>");
    }

    public static String ago(long t) {
        long s = Math.max(0, (System.currentTimeMillis() - t) / 1000);
        if (s < 60) return "gerade";
        long m = s / 60;
        if (m < 60) return "vor " + m + " Min.";
        long h = m / 60;
        if (h < 24) return "vor " + h + " Std.";
        long d = h / 24;
        return "vor " + d + (d == 1 ? " Tag" : " Tagen");
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextReport = y.getInt("next-report", 1);
        for (Channel c : Channel.values()) {
            for (String s : y.getStringList("channels." + c.name())) {
                String[] p = s.split(";", 3);
                if (p.length < 3) continue;
                try {
                    messages.get(c).addLast(new Message(Long.parseLong(p[0]), UUID.fromString(p[1]), p[2]));
                } catch (RuntimeException ignored) {
                    // kaputte Zeile
                }
            }
        }
        ConfigurationSection rs = y.getConfigurationSection("reports");
        if (rs != null) {
            for (String k : rs.getKeys(false)) {
                ConfigurationSection s = rs.getConfigurationSection(k);
                if (s == null) continue;
                try {
                    Report r = new Report(Integer.parseInt(k), s.getLong("time"), UUID.fromString(s.getString("reporter")),
                            UUID.fromString(s.getString("target")), s.getString("reason", ""), s.getString("world", "world"),
                            s.getInt("x"), s.getInt("y"), s.getInt("z"));
                    r.status = ReportStatus.valueOf(s.getString("status", "OFFEN"));
                    String h = s.getString("handler", "");
                    r.handler = h.isEmpty() ? null : UUID.fromString(h);
                    r.note = s.getString("note", "");
                    reports.put(r.id, r);
                } catch (RuntimeException ignored) {
                    // kaputte Meldung
                }
            }
        }
        for (String s : y.getStringList("events")) {
            String[] p = s.split(";", 3);
            if (p.length < 3) continue;
            try {
                events.addLast(new Event(Long.parseLong(p[0]), p[1], p[2]));
            } catch (RuntimeException ignored) {
                // kaputte Zeile
            }
        }
        ConfigurationSection rd = y.getConfigurationSection("read");
        if (rd != null) {
            for (String u : rd.getKeys(false)) {
                ConfigurationSection s = rd.getConfigurationSection(u);
                if (s == null) continue;
                Map<Channel, Long> m = new HashMap<>();
                for (String c : s.getKeys(false)) {
                    Channel ch = Channel.of(c);
                    if (ch != null) m.put(ch, s.getLong(c));
                }
                try {
                    read.put(UUID.fromString(u), m);
                } catch (IllegalArgumentException ignored) {
                    // ungültig
                }
            }
        }
        ConfigurationSection ws = y.getConfigurationSection("warns");
        if (ws != null) {
            for (String u : ws.getKeys(false)) {
                List<Warn> l = new ArrayList<>();
                for (String line : ws.getStringList(u)) {
                    String[] p = line.split(";", 3);
                    if (p.length < 3) continue;
                    try {
                        l.add(new Warn(Long.parseLong(p[0]), UUID.fromString(p[1]), p[2]));
                    } catch (RuntimeException ignored) {
                        // kaputte Zeile
                    }
                }
                try {
                    if (!l.isEmpty()) warns.put(UUID.fromString(u), l);
                } catch (IllegalArgumentException ignored) {
                    // ungültig
                }
            }
        }
        for (String u : y.getStringList("muted")) {
            try {
                muted.put(UUID.fromString(u), true);
            } catch (IllegalArgumentException ignored) {
                // ungültig
            }
        }
    }

    private synchronized YamlConfiguration snapshot() {
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-report", nextReport);
        for (Channel c : Channel.values()) {
            List<String> l = new ArrayList<>();
            for (Message m : messages.get(c)) l.add(m.time() + ";" + m.author() + ";" + m.text());
            y.set("channels." + c.name(), l);
        }
        for (Report r : reports.values()) {
            String b = "reports." + r.id + ".";
            y.set(b + "time", r.time);
            y.set(b + "reporter", r.reporter.toString());
            y.set(b + "target", r.target.toString());
            y.set(b + "reason", r.reason);
            y.set(b + "world", r.world);
            y.set(b + "x", r.x);
            y.set(b + "y", r.y);
            y.set(b + "z", r.z);
            y.set(b + "status", r.status.name());
            y.set(b + "handler", r.handler == null ? "" : r.handler.toString());
            y.set(b + "note", r.note);
        }
        List<String> ev = new ArrayList<>();
        for (Event e : events) ev.add(e.time() + ";" + e.kind() + ";" + e.text());
        y.set("events", ev);
        read.forEach((u, m) -> m.forEach((c, t) -> y.set("read." + u + "." + c.name().toLowerCase(Locale.ROOT), t)));
        List<String> mu = new ArrayList<>();
        muted.forEach((u, v) -> {
            if (Boolean.TRUE.equals(v)) mu.add(u.toString());
        });
        y.set("muted", mu);
        warns.forEach((u, l) -> {
            List<String> out = new ArrayList<>();
            for (Warn w : l) out.add(w.time() + ";" + w.by() + ";" + w.reason());
            y.set("warns." + u, out);
        });
        return y;
    }

    public void save() {
        write(snapshot());
    }

    private void saveAsync() {
        if (saving) return;
        YamlConfiguration y = snapshot();
        saving = true;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                write(y);
            } finally {
                saving = false;
            }
        });
    }

    private void write(YamlConfiguration y) {
        try {
            plugin.getDataFolder().mkdirs();
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.writeString(tmp.toPath(), y.saveToString(), StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("staff.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}

package de.kuby.kubyplugins.support;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Support-Tickets: Spieler schreiben mit /support new ein Ticket (Titel und Frage). Helfer (erfahrene Spieler mit
 * verlinktem Discord) übernehmen und antworten, der Spieler bekommt jede Antwort im Chat. Kommt ein Helfer nicht
 * weiter, gibt er das Ticket an das Team (Permission kuby.support.team). Ist die Frage gelöst, bestätigt der Spieler
 * und vergibt 1 bis 5 Sterne. /support top zeigt die besten Helfer. Gespeichert in support.yml.
 */
public final class SupportService {

    public enum Status {
        OFFEN("offen"), IN_ARBEIT("in Arbeit"), TEAM("beim Team"), GELOEST("gelöst"), GESCHLOSSEN("geschlossen");

        public final String label;

        Status(String label) {
            this.label = label;
        }

        public boolean open() {
            return this == OFFEN || this == IN_ARBEIT || this == TEAM;
        }
    }

    /** Eine Nachricht im Ticket; role: spieler, helfer, team, system. */
    public record Message(long time, UUID author, String role, String text) {}

    public static final class Ticket {
        public final int id;
        public final UUID author;
        public final String title;
        public final long created;
        public Status status = Status.OFFEN;
        public UUID helper;
        public int stars;
        public final List<Message> messages = new ArrayList<>();
        /** Ungelesene Antworten für den Spieler. */
        public boolean unread;

        Ticket(int id, UUID author, String title, long created) {
            this.id = id;
            this.author = author;
            this.title = title;
            this.created = created;
        }
    }

    /** Statistik eines Helfers. */
    public record HelperStat(UUID id, int solved, int ratings, int starSum) {
        public double average() {
            return ratings == 0 ? 0 : (double) starSum / ratings;
        }
    }

    private static final String PREFIX = "<#FF6478><b>SUPPORT</b> <dark_gray>▶ <gray>";

    private final KubyPlugins plugin;
    private final File file;
    private final Map<Integer, Ticket> tickets = new LinkedHashMap<>();
    private final Set<UUID> helpers = new HashSet<>();
    private final Map<UUID, Long> lastTicket = new HashMap<>();
    private int nextId = 1;

    public SupportService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "support.yml");
        load();
    }

    public static void send(Player p, String mini) {
        Msg.send(p, PREFIX + mini);
    }

    public static String nameOf(UUID id) {
        if (id == null) return "-";
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() == null ? "?" : op.getName();
    }

    // =================================================================================================================
    // Rollen
    // =================================================================================================================

    public boolean isTeam(Player p) {
        return p.hasPermission("kuby.support.team") || p.hasPermission("kuby.admin");
    }

    public synchronized boolean isHelper(UUID id) {
        return helpers.contains(id);
    }

    public boolean canHelp(Player p) {
        return isTeam(p) || isHelper(p.getUniqueId());
    }

    private long helperHours() {
        return plugin.getConfig().getLong("support.helper-min-hours", 50);
    }

    /** Helfer werden: lange genug gespielt und Discord verlinkt. Null bei Erfolg, sonst warum nicht. */
    public synchronized String becomeHelper(Player p) {
        UUID id = p.getUniqueId();
        if (helpers.contains(id)) return "Du bist schon Helfer.";
        long hours = plugin.playtime().minutesOf(id) / 60;
        if (hours < helperHours()) return "Helfer kann werden, wer mindestens " + helperHours() + " Stunden gespielt hat (du: " + hours + ").";
        if (plugin.discord().available() && plugin.discord().discordIdOf(id) == null) {
            return "Verlinke zuerst deinen Discord-Account (/verify zeigt dir den Weg).";
        }
        helpers.add(id);
        save();
        return null;
    }

    public synchronized void leaveHelper(UUID id) {
        helpers.remove(id);
        save();
    }

    public synchronized void removeHelper(UUID id) {
        helpers.remove(id);
        save();
    }

    // =================================================================================================================
    // Tickets
    // =================================================================================================================

    public synchronized Ticket get(int id) {
        return tickets.get(id);
    }

    public synchronized List<Ticket> of(UUID author) {
        List<Ticket> out = new ArrayList<>();
        for (Ticket t : tickets.values()) if (t.author.equals(author)) out.add(t);
        out.sort((a, b) -> Long.compare(b.created, a.created));
        return out;
    }

    /** Für Helfer: offene (unbesetzt), eigene, und fürs Team die weitergegebenen. */
    public synchronized List<Ticket> queue(Player p, String tab) {
        List<Ticket> out = new ArrayList<>();
        for (Ticket t : tickets.values()) {
            boolean add = switch (tab) {
                case "offen" -> t.status == Status.OFFEN;
                case "meine" -> t.status.open() && p.getUniqueId().equals(t.helper);
                case "team" -> t.status == Status.TEAM;
                default -> false;
            };
            if (add && !t.author.equals(p.getUniqueId())) out.add(t);
        }
        out.sort((a, b) -> Long.compare(a.created, b.created));
        return out;
    }

    public synchronized int openCount(UUID author) {
        int n = 0;
        for (Ticket t : tickets.values()) if (t.author.equals(author) && t.status.open()) n++;
        return n;
    }

    public synchronized String create(Player p, String title, String text) {
        String t = title == null ? "" : title.trim();
        String q = text == null ? "" : text.trim();
        if (t.length() < 3) return "Der Titel braucht mindestens 3 Zeichen.";
        if (q.length() < 10) return "Beschreib deine Frage etwas genauer (mindestens 10 Zeichen).";
        if (openCount(p.getUniqueId()) >= plugin.getConfig().getInt("support.max-open", 2)) {
            return "Du hast schon " + plugin.getConfig().getInt("support.max-open", 2) + " offene Tickets. Warte auf eine Antwort.";
        }
        Long last = lastTicket.get(p.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < 120_000) return "Warte kurz, bevor du das nächste Ticket schreibst.";
        Ticket tk = new Ticket(nextId++, p.getUniqueId(), t.length() > 40 ? t.substring(0, 40) : t, System.currentTimeMillis());
        tk.messages.add(new Message(tk.created, p.getUniqueId(), "spieler", q.length() > 500 ? q.substring(0, 500) : q));
        tickets.put(tk.id, tk);
        lastTicket.put(p.getUniqueId(), tk.created);
        save();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p) || !canHelp(o)) continue;
            send(o, "Neues Ticket <white>#" + tk.id + "</white> von <white>" + p.getName() + "</white>: " + Msg.escape(tk.title)
                    + " <click:run_command:'/support ticket " + tk.id + "'><#FF6478>[Ansehen]</click>");
        }
        return null;
    }

    public synchronized String claim(Player p, Ticket t) {
        if (!canHelp(p)) return "Nur Helfer können Tickets übernehmen.";
        if (t.author.equals(p.getUniqueId())) return "Dein eigenes Ticket kannst du nicht übernehmen.";
        if (t.status != Status.OFFEN && !(t.status == Status.TEAM && isTeam(p))) return "Dieses Ticket hat schon jemand.";
        t.helper = p.getUniqueId();
        t.status = Status.IN_ARBEIT;
        t.messages.add(new Message(System.currentTimeMillis(), p.getUniqueId(), "system", p.getName() + " kümmert sich um dein Ticket."));
        t.unread = true;
        save();
        notifyAuthor(t, p.getName() + " kümmert sich um dein Ticket #" + t.id + ".");
        return null;
    }

    public synchronized String reply(Player p, Ticket t, String text) {
        String msg = text == null ? "" : text.trim();
        if (msg.isEmpty()) return "Die Nachricht ist leer.";
        if (!t.status.open()) return "Dieses Ticket ist schon geschlossen.";
        boolean author = t.author.equals(p.getUniqueId());
        boolean helper = p.getUniqueId().equals(t.helper) || isTeam(p);
        if (!author && !helper) return "Übernimm das Ticket zuerst.";
        String role = author ? "spieler" : isTeam(p) && !isHelper(p.getUniqueId()) ? "team" : "helfer";
        t.messages.add(new Message(System.currentTimeMillis(), p.getUniqueId(), role, msg.length() > 500 ? msg.substring(0, 500) : msg));
        if (author) {
            Player h = t.helper == null ? null : Bukkit.getPlayer(t.helper);
            if (h != null) send(h, "Neue Nachricht in Ticket <white>#" + t.id + "</white> von " + p.getName()
                    + " <click:run_command:'/support ticket " + t.id + "'><#FF6478>[Öffnen]</click>");
        } else {
            t.unread = true;
            if (t.helper == null) t.helper = p.getUniqueId();
            if (t.status == Status.OFFEN) t.status = Status.IN_ARBEIT;
            notifyAuthor(t, "Antwort auf dein Ticket #" + t.id + " von " + p.getName() + ": "
                    + (msg.length() > 60 ? msg.substring(0, 60) + "…" : msg));
        }
        save();
        return null;
    }

    public synchronized String escalate(Player p, Ticket t) {
        if (!canHelp(p) || t.author.equals(p.getUniqueId())) return "Nur Helfer können ein Ticket weitergeben.";
        if (!isTeam(p) && !p.getUniqueId().equals(t.helper)) return "Übernimm das Ticket zuerst.";
        if (!t.status.open()) return "Dieses Ticket ist schon geschlossen.";
        t.status = Status.TEAM;
        t.messages.add(new Message(System.currentTimeMillis(), p.getUniqueId(), "system", p.getName() + " hat das Ticket an das Team gegeben."));
        t.unread = true;
        save();
        notifyAuthor(t, "Dein Ticket #" + t.id + " liegt jetzt beim Team.");
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (isTeam(o) && !o.equals(p)) send(o, "Ticket <white>#" + t.id + "</white> wurde ans Team gegeben: " + Msg.escape(t.title)
                    + " <click:run_command:'/support ticket " + t.id + "'><#FF6478>[Ansehen]</click>");
        }
        return null;
    }

    /** Spieler: gelöst mit Sternen (1 bis 5). */
    public synchronized String solve(Player p, Ticket t, int stars) {
        if (!t.author.equals(p.getUniqueId())) return "Nur wer das Ticket geschrieben hat, kann es als gelöst markieren.";
        if (!t.status.open()) return "Dieses Ticket ist schon geschlossen.";
        if (stars < 1 || stars > 5) return "Bitte 1 bis 5 Sterne vergeben.";
        t.status = Status.GELOEST;
        t.stars = t.helper == null ? 0 : stars;
        t.unread = false;
        t.messages.add(new Message(System.currentTimeMillis(), p.getUniqueId(), "system", "Gelöst" + (t.helper == null ? "" : " · " + stars + " Sterne")));
        save();
        Player h = t.helper == null ? null : Bukkit.getPlayer(t.helper);
        if (h != null) send(h, "Ticket <white>#" + t.id + "</white> ist gelöst. " + p.getName() + " gibt dir <gold>" + "★".repeat(stars) + "</gold>.");
        return null;
    }

    /** Spieler zieht zurück oder Team schließt. */
    public synchronized String close(Player p, Ticket t) {
        boolean author = t.author.equals(p.getUniqueId());
        if (!author && !isTeam(p)) return "Schließen kann nur, wer das Ticket geschrieben hat, oder das Team.";
        if (!t.status.open()) return "Dieses Ticket ist schon geschlossen.";
        t.status = Status.GESCHLOSSEN;
        t.messages.add(new Message(System.currentTimeMillis(), p.getUniqueId(), "system", "Geschlossen von " + p.getName()));
        if (!author) {
            t.unread = true;
            notifyAuthor(t, "Dein Ticket #" + t.id + " wurde vom Team geschlossen.");
        }
        save();
        return null;
    }

    public synchronized void markRead(Ticket t) {
        if (t.unread) {
            t.unread = false;
            save();
        }
    }

    public synchronized int unreadOf(UUID author) {
        int n = 0;
        for (Ticket t : tickets.values()) if (t.author.equals(author) && t.unread) n++;
        return n;
    }

    private void notifyAuthor(Ticket t, String text) {
        Player a = Bukkit.getPlayer(t.author);
        if (a != null) send(a, Msg.escape(text) + " <click:run_command:'/support ticket " + t.id + "'><#FF6478>[Öffnen]</click>");
    }

    /** Beste Helfer: Durchschnitt der Sterne (ab 3 Bewertungen), dann Anzahl gelöster Tickets. */
    public synchronized List<HelperStat> top(int n) {
        Map<UUID, int[]> stats = new HashMap<>();
        for (Ticket t : tickets.values()) {
            if (t.helper == null || t.status != Status.GELOEST) continue;
            int[] s = stats.computeIfAbsent(t.helper, k -> new int[3]);
            s[0]++;
            if (t.stars > 0) {
                s[1]++;
                s[2] += t.stars;
            }
        }
        List<HelperStat> out = new ArrayList<>();
        for (Map.Entry<UUID, int[]> e : stats.entrySet()) out.add(new HelperStat(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]));
        out.sort((a, b) -> {
            boolean ra = a.ratings() >= 3, rb = b.ratings() >= 3;
            if (ra != rb) return ra ? -1 : 1;
            int c = Double.compare(b.average(), a.average());
            return c != 0 ? c : Integer.compare(b.solved(), a.solved());
        });
        return out.size() > n ? out.subList(0, n) : out;
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private synchronized void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = y.getInt("next-id", 1);
        for (String h : y.getStringList("helfer")) {
            try {
                helpers.add(UUID.fromString(h));
            } catch (IllegalArgumentException ignored) {
            }
        }
        ConfigurationSection s = y.getConfigurationSection("tickets");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(key);
            if (c == null) continue;
            try {
                Ticket t = new Ticket(Integer.parseInt(key), UUID.fromString(c.getString("autor", "")), c.getString("titel", "?"), c.getLong("erstellt"));
                t.status = Status.valueOf(c.getString("status", "OFFEN"));
                String h = c.getString("helfer");
                t.helper = h == null || h.isEmpty() ? null : UUID.fromString(h);
                t.stars = c.getInt("sterne");
                t.unread = c.getBoolean("ungelesen");
                for (String m : c.getStringList("nachrichten")) {
                    String[] p = m.split(";", 4);
                    if (p.length == 4) t.messages.add(new Message(Long.parseLong(p[0]), UUID.fromString(p[1]), p[2], p[3]));
                }
                // Erledigte Tickets nach 60 Tagen vergessen (die Helfer-Rangliste zählt die letzten 60 Tage)
                long last = t.messages.isEmpty() ? t.created : t.messages.get(t.messages.size() - 1).time();
                if (!t.status.open() && System.currentTimeMillis() - last > 60L * 24 * 3600 * 1000) continue;
                tickets.put(t.id, t);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("support.yml: Ticket " + key + " übersprungen (" + ex.getMessage() + ")");
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-id", nextId);
        List<String> h = new ArrayList<>();
        for (UUID u : helpers) h.add(u.toString());
        y.set("helfer", h);
        for (Ticket t : tickets.values()) {
            String k = "tickets." + t.id + ".";
            y.set(k + "autor", t.author.toString());
            y.set(k + "titel", t.title);
            y.set(k + "erstellt", t.created);
            y.set(k + "status", t.status.name());
            y.set(k + "helfer", t.helper == null ? "" : t.helper.toString());
            y.set(k + "sterne", t.stars);
            y.set(k + "ungelesen", t.unread);
            List<String> msgs = new ArrayList<>();
            for (Message m : t.messages) msgs.add(m.time() + ";" + m.author() + ";" + m.role() + ";" + m.text());
            y.set(k + "nachrichten", msgs);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("support.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}

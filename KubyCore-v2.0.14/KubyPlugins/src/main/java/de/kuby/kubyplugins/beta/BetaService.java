package de.kuby.kubyplugins.beta;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Beta-Test: Rolle „Beta-Tester“ (gespeichert in beta.yml, beim Join als Permission kuby.beta gesetzt; per
 * Permission-Plugin geht kuby.beta ebenso; beta.alle-tester: true macht jeden zum Tester), Feedback-Formular mit
 * Entwürfen (Antworten bleiben beim Blättern und nach dem Ausloggen erhalten), Bug-Meldungen (/bug) mit Ort und Modus,
 * Auswertung im Kadmin und Export als CSV (plugins/KubyPlugins/beta-export.csv).
 */
public final class BetaService implements Listener {

    public static final String PERM = "kuby.beta";

    /** Antworten eines Spielers: Schlüssel -> Wert ("1".."6", "n" = nicht getestet, Text), abgeschickt am (0 = Entwurf). */
    public static final class Answers {
        public final Map<String, String> values = new LinkedHashMap<>();
        public long submitted;
        public int submits;
        public String name = "?";
    }

    /** Bug-Meldung: wer, wann, wo, Text. */
    public record Bug(int id, UUID player, String name, long time, String mode, String where, String text) {}

    private final KubyPlugins plugin;
    private final File file;
    private final Set<UUID> testers = new LinkedHashSet<>();
    private final Map<UUID, Answers> answers = new HashMap<>();
    private final List<Bug> bugs = new ArrayList<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private int nextBug = 1;
    private boolean dirty;

    public BetaService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "beta.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save();
        }, 1200L, 1200L);
    }

    public boolean everyone() {
        return plugin.getConfig().getBoolean("beta.alle-tester", false);
    }

    public synchronized boolean isTester(UUID id) {
        return everyone() || testers.contains(id);
    }

    public boolean isTester(Player p) {
        return isTester(p.getUniqueId()) || p.hasPermission(PERM);
    }

    public synchronized List<UUID> testers() {
        return new ArrayList<>(testers);
    }

    public synchronized boolean setTester(UUID id, boolean on) {
        boolean changed = on ? testers.add(id) : testers.remove(id);
        if (changed) {
            dirty = true;
            save();
            Player p = Bukkit.getPlayer(id);
            if (p != null) applyPermission(p);
        }
        return changed;
    }

    private void applyPermission(Player p) {
        PermissionAttachment old = attachments.remove(p.getUniqueId());
        if (old != null) {
            try {
                p.removeAttachment(old);
            } catch (IllegalArgumentException ignored) {
                // schon entfernt
            }
        }
        if (isTester(p.getUniqueId())) attachments.put(p.getUniqueId(), p.addAttachment(plugin, PERM, true));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        applyPermission(p);
        if (!isTester(p)) return;
        Answers a = answersOf(p.getUniqueId());
        synchronized (this) {
            a.name = p.getName();
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            boolean done = a.submitted > 0;
            Msg.send(p, "<#7CF5FF><b>BETA</b> <dark_gray>▶ <white>Danke, dass du testest! " + (done
                    ? "<gray>Dein Feedback ist angekommen, du kannst es jederzeit ändern: "
                    : "<gray>Bitte fülle das Feedback-Formular aus: ")
                    + "<click:run_command:'/feedback'><yellow>[/feedback]</click> <gray>· Bugs sofort melden: <yellow>/bug <Text>");
        }, 80L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PermissionAttachment a = attachments.remove(e.getPlayer().getUniqueId());
        if (a != null) {
            try {
                e.getPlayer().removeAttachment(a);
            } catch (IllegalArgumentException ignored) {
                // schon entfernt
            }
        }
    }

    // =================================================================================================================
    // Antworten
    // =================================================================================================================

    public synchronized Answers answersOf(UUID id) {
        return answers.computeIfAbsent(id, k -> new Answers());
    }

    public synchronized Map<UUID, Answers> allAnswers() {
        return new LinkedHashMap<>(answers);
    }

    /** Entwurf übernehmen (leere Texte löschen den Wert, "0" bei Sternen = noch nicht gewählt). */
    public synchronized void draft(UUID id, String name, Map<String, String> values) {
        Answers a = answersOf(id);
        a.name = name;
        values.forEach((k, v) -> {
            if (v == null || v.isBlank() || v.equals("0")) a.values.remove(k);
            else a.values.put(k, v.length() > 1000 ? v.substring(0, 1000) : v);
        });
        dirty = true;
    }

    /** Pflichtfragen (Sterne), die noch fehlen. */
    public synchronized List<FeedbackQuestions.Question> missing(UUID id) {
        Answers a = answersOf(id);
        List<FeedbackQuestions.Question> out = new ArrayList<>();
        for (FeedbackQuestions.Question q : FeedbackQuestions.all()) {
            if (q.type() == FeedbackQuestions.Type.STARS && !a.values.containsKey(q.key())) out.add(q);
        }
        return out;
    }

    public synchronized void submit(UUID id) {
        Answers a = answersOf(id);
        a.submitted = System.currentTimeMillis();
        a.submits++;
        dirty = true;
        save();
    }

    /** Durchschnitt einer Sterne-Frage über alle abgeschickten Formulare: {Summe, Anzahl, nicht getestet}. */
    public synchronized double[] stats(String key) {
        double sum = 0;
        int n = 0, skipped = 0;
        for (Answers a : answers.values()) {
            if (a.submitted == 0) continue;
            String v = a.values.get(key);
            if (v == null) continue;
            if (v.equals("n")) {
                skipped++;
                continue;
            }
            try {
                sum += Integer.parseInt(v);
                n++;
            } catch (NumberFormatException ignored) {
                // kein Sterne-Wert
            }
        }
        return new double[] {sum, n, skipped};
    }

    public synchronized int submittedCount() {
        int n = 0;
        for (Answers a : answers.values()) if (a.submitted > 0) n++;
        return n;
    }

    // =================================================================================================================
    // Bugs
    // =================================================================================================================

    public synchronized Bug reportBug(Player p, String text) {
        Location l = p.getLocation();
        String where = l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
        String mode = plugin.modes() == null ? "?" : String.valueOf(plugin.modes().modeOf(p.getUniqueId()));
        Bug b = new Bug(nextBug++, p.getUniqueId(), p.getName(), System.currentTimeMillis(), mode, where,
                text.length() > 500 ? text.substring(0, 500) : text);
        bugs.add(b);
        dirty = true;
        save();
        return b;
    }

    public synchronized List<Bug> bugs() {
        List<Bug> l = new ArrayList<>(bugs);
        java.util.Collections.reverse(l);
        return l;
    }

    public synchronized boolean removeBug(int id) {
        boolean r = bugs.removeIf(b -> b.id() == id);
        if (r) {
            dirty = true;
            save();
        }
        return r;
    }

    // =================================================================================================================
    // Export und Speichern
    // =================================================================================================================

    private static String csv(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"").replace("\n", " ") + "\"";
    }

    /** Schreibt alle abgeschickten Formulare und Bugs als CSV (Semikolon, für Excel). Gibt die Datei zurück. */
    public synchronized File export() throws IOException {
        File f = new File(plugin.getDataFolder(), "beta-export.csv");
        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        try (PrintWriter w = new PrintWriter(f, StandardCharsets.UTF_8)) {
            w.print('﻿');
            List<FeedbackQuestions.Question> qs = FeedbackQuestions.all();
            StringBuilder head = new StringBuilder("Spieler;Abgeschickt");
            for (FeedbackQuestions.Question q : qs) head.append(';').append(csv(q.text()));
            w.println(head);
            for (Answers a : answers.values()) {
                if (a.submitted == 0) continue;
                StringBuilder row = new StringBuilder(csv(a.name)).append(';').append(df.format(new Date(a.submitted)));
                for (FeedbackQuestions.Question q : qs) row.append(';').append(csv(a.values.getOrDefault(q.key(), "")));
                w.println(row);
            }
            w.println();
            w.println("Bug;Spieler;Zeit;Modus;Ort;Text");
            for (Bug b : bugs) {
                w.println(b.id() + ";" + csv(b.name()) + ";" + df.format(new Date(b.time())) + ";" + csv(b.mode()) + ";"
                        + csv(b.where()) + ";" + csv(b.text()));
            }
        }
        return f;
    }

    private synchronized void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String s : y.getStringList("tester")) {
            try {
                testers.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // kaputter Eintrag
            }
        }
        ConfigurationSection as = y.getConfigurationSection("antworten");
        if (as != null) {
            for (String k : as.getKeys(false)) {
                ConfigurationSection s = as.getConfigurationSection(k);
                if (s == null) continue;
                try {
                    Answers a = new Answers();
                    a.name = s.getString("name", "?");
                    a.submitted = s.getLong("abgeschickt");
                    a.submits = s.getInt("mal");
                    ConfigurationSection v = s.getConfigurationSection("werte");
                    if (v != null) for (String q : v.getKeys(false)) a.values.put(q, v.getString(q));
                    answers.put(UUID.fromString(k), a);
                } catch (IllegalArgumentException ignored) {
                    // kaputter Eintrag
                }
            }
        }
        nextBug = y.getInt("naechster-bug", 1);
        ConfigurationSection bs = y.getConfigurationSection("bugs");
        if (bs != null) {
            for (String k : bs.getKeys(false)) {
                ConfigurationSection s = bs.getConfigurationSection(k);
                if (s == null) continue;
                try {
                    bugs.add(new Bug(Integer.parseInt(k), UUID.fromString(s.getString("spieler")), s.getString("name", "?"), s.getLong("zeit"),
                            s.getString("modus", "?"), s.getString("ort", "?"), s.getString("text", "")));
                } catch (IllegalArgumentException | NullPointerException ignored) {
                    // kaputter Eintrag
                }
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> t = new ArrayList<>();
        for (UUID u : testers) t.add(u.toString());
        y.set("tester", t);
        answers.forEach((id, a) -> {
            String b = "antworten." + id + ".";
            y.set(b + "name", a.name);
            y.set(b + "abgeschickt", a.submitted);
            y.set(b + "mal", a.submits);
            a.values.forEach((k, v) -> y.set(b + "werte." + k, v));
        });
        y.set("naechster-bug", nextBug);
        for (Bug bug : bugs) {
            String b = "bugs." + bug.id() + ".";
            y.set(b + "spieler", bug.player().toString());
            y.set(b + "name", bug.name());
            y.set(b + "zeit", bug.time());
            y.set(b + "modus", bug.mode());
            y.set(b + "ort", bug.where());
            y.set(b + "text", bug.text());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("beta.yml: " + e.getMessage());
        }
    }
}

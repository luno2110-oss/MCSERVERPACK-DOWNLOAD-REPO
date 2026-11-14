package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chat-Quiz. Fragen kommen aus quiz_questions.txt im Datenordner (bearbeitbar).
 * Jede Woche stimmen Spieler über eine Kategorie ab, die Gewinnerkategorie gilt in der nächsten Woche (80 % der Fragen).
 */
public final class QuizService {

    public record Question(String category, String text, List<String> answers, long reward) {}

    public static final List<String> CATEGORIES =
            List.of("physik", "mathe", "deutsch", "geografie", "chemie", "minecraft", "zufall");
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    /** Laufende Frage: Start-Multiplikator und Startzeit (der Multiplikator sinkt jede Sekunde bis x1). */
    private double startMult = 1;
    private long askedAt;
    private org.bukkit.scheduler.BukkitTask ticker;

    private final KubyPlugins plugin;
    private final File questionFile;
    private final File stateFile;
    private final List<Question> questions = new ArrayList<>();
    private final Map<UUID, String> votes = new HashMap<>();
    private String activeCategory;
    private String resetKey = "";
    private Question current;

    public QuizService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.questionFile = new File(plugin.getDataFolder(), "quiz_questions.txt");
        this.stateFile = new File(plugin.getDataFolder(), "quiz.yml");
        if (!questionFile.exists()) plugin.saveResource("quiz_questions.txt", false);
        loadQuestions();
        loadState();
        long interval = 20L * 60 * plugin.getConfig().getInt("quiz.interval-minutes", 20);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::ask, interval, interval);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkWeekly, 1200L, 1200L);
    }

    public int questionCount() {
        return questions.size();
    }

    private void loadQuestions() {
        questions.clear();
        try {
            for (String line : Files.readAllLines(questionFile.toPath(), StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                String[] p = t.split("[|]");
                if (p.length < 4) continue;
                try {
                    List<String> answers = new ArrayList<>();
                    for (String a : p[2].split("/")) {
                        if (!a.isBlank()) answers.add(a.trim());
                    }
                    questions.add(new Question(p[0].trim().toLowerCase(Locale.ROOT), p[1].trim(),
                            answers, Long.parseLong(p[3].trim())));
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Ungültige Quizfrage: " + t);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("quiz_questions.txt konnte nicht gelesen werden: " + e.getMessage());
        }
    }

    private synchronized void loadState() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(stateFile);
        activeCategory = yml.getString("active");
        resetKey = yml.getString("resetKey", "");
        var vs = yml.getConfigurationSection("votes");
        if (vs != null) {
            for (String k : vs.getKeys(false)) votes.put(UUID.fromString(k), vs.getString(k));
        }
    }

    private synchronized void saveState() {
        YamlConfiguration out = new YamlConfiguration();
        if (activeCategory != null) out.set("active", activeCategory);
        out.set("resetKey", resetKey);
        votes.forEach((id, c) -> out.set("votes." + id, c));
        try {
            plugin.getDataFolder().mkdirs();
            out.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("quiz.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public synchronized String vote(UUID id, String category) {
        if (!CATEGORIES.contains(category)) return "Unbekannte Kategorie.";
        votes.put(id, category);
        saveState();
        return null;
    }

    public synchronized String myVote(UUID id) {
        return votes.get(id);
    }

    public synchronized Map<String, Integer> tally() {
        Map<String, Integer> t = new HashMap<>();
        for (String c : votes.values()) t.merge(c, 1, Integer::sum);
        return t;
    }

    public synchronized String activeCategory() {
        return activeCategory;
    }

    /** Montags um 00:05 (Berliner Zeit) wird die Gewinnerkategorie übernommen und die Abstimmung zurückgesetzt. */
    private synchronized void checkWeekly() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        if (now.getDayOfWeek() != DayOfWeek.MONDAY || now.getHour() != 0 || now.getMinute() < 5) return;
        String key = de.kuby.kubyplugins.service.BattlepassService.weekKey();
        if (key.equals(resetKey)) return;
        String winner = null;
        int best = 0;
        for (Map.Entry<String, Integer> e : tally().entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                winner = e.getKey();
            }
        }
        activeCategory = winner;
        votes.clear();
        resetKey = key;
        saveState();
        plugin.notifications().send("chat.quiz", "<aqua>Quiz-Kategorie dieser Woche: <white>"
                + (winner == null ? "alle Kategorien" : winner));
    }

    private int windowSeconds() {
        return Math.max(15, plugin.getConfig().getInt("quiz.zeit-sekunden", 60));
    }

    /** Start-Multiplikator: schwerere Fragen (höherer Grundgewinn) starten höher, dazu Zufall; zwischen min und max. */
    private double startMultiplier(Question q) {
        double min = plugin.getConfig().getDouble("quiz.multi-min", 4), max = plugin.getConfig().getDouble("quiz.multi-max", 25);
        double d = Math.max(0, Math.min(1, (q.reward() - 100) / 200.0));
        double m = min + d * (max - min) * 0.5 + ThreadLocalRandom.current().nextDouble() * (max - min) * 0.5;
        return Math.round(Math.max(min, Math.min(max, m)));
    }

    /** Aktueller Multiplikator der laufenden Frage (sinkt linear von Start auf x1 bis zum Ende der Antwortzeit). */
    public synchronized double multiplier() {
        if (current == null) return 1;
        double t = (System.currentTimeMillis() - askedAt) / 1000.0;
        double m = startMult - (startMult - 1) * Math.min(1, t / windowSeconds());
        return Math.max(1, Math.round(m * 10) / 10.0);
    }

    private static String mult(double m) {
        return (m == Math.floor(m) ? String.valueOf((long) m) : String.format(Locale.GERMANY, "%.1f", m));
    }

    /** Spieler, die das Quiz über der Hotbar sehen wollen (statt der Chat-Zeilen). */
    private boolean hotbar(Player pl) {
        return plugin.playerSettings() != null && plugin.playerSettings().get(pl.getUniqueId(), "quiz.hotbar");
    }

    /** Jede Sekunde: Hotbar-Anzeige, alle chat-schritte Sekunden eine Chat-Zeile, am Ende Auflösung. */
    private void tick(Question q) {
        if (current != q) {
            stopTicker();
            return;
        }
        long sec = (System.currentTimeMillis() - askedAt) / 1000;
        int window = windowSeconds();
        long left = window - sec;
        if (left <= 0) {
            current = null;
            stopTicker();
            plugin.notifications().send("chat.quiz", "<gray>Quiz beendet, niemand hat geantwortet. Antwort: <white>"
                    + Msg.escape(q.answers().get(0)));
            return;
        }
        double m = multiplier();
        long prize = Math.round(q.reward() * m);
        String color = m >= startMult * 0.66 ? "#7CF57C" : m >= startMult * 0.33 ? "#FFD25A" : "#FF7A5C";
        net.kyori.adventure.text.Component bar = Msg.parse("<aqua><b>QUIZ</b> <dark_gray>▶ <" + color + "><b>x" + mult(m)
                + "</b></" + color + "> <dark_gray>· <gold>" + Msg.coins(prize) + "$ <dark_gray>· <gray>noch " + left + " s");
        int step = Math.max(5, plugin.getConfig().getInt("quiz.chat-schritte", 10));
        for (Player pl : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.notifications().wants(pl, "chat.quiz")) continue;
            if (hotbar(pl)) {
                if (plugin.hotbar() != null) plugin.hotbar().flash(pl, bar, 25, 5);
                else pl.sendActionBar(bar);
            } else if (sec > 0 && sec % step == 0) {
                Msg.raw(pl, "<dark_gray>⏱ <aqua>Quiz <dark_gray>▶ <" + color + "><b>x" + mult(m) + "</b></" + color
                        + "> <gray>· Gewinn jetzt <gold>" + Msg.coins(prize) + "$ <gray>· noch " + left + " s");
            }
        }
    }

    private void stopTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    /** Stellt eine Frage. Wird auch vom Admin-Panel aufgerufen. */
    public void ask() {
        if (questions.isEmpty()) return;
        List<Question> pool = questions;
        String cat = activeCategory();
        if (cat != null && ThreadLocalRandom.current().nextInt(100) < 80) {
            List<Question> inCat = new ArrayList<>();
            for (Question q : questions) {
                if (q.category().equals(cat)) inCat.add(q);
            }
            if (!inCat.isEmpty()) pool = inCat;
        }
        Question q = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        stopTicker();
        synchronized (this) {
            startMult = startMultiplier(q);
            askedAt = System.currentTimeMillis();
            current = q;
        }
        plugin.notifications().send("chat.quiz", "<aqua><b>Chat-Quiz</b> <gray>[" + q.category() + "]: <white>"
                + Msg.escape(q.text()) + "\n<gray>Start-Multiplikator <#7CF57C><b>x" + mult(startMult) + "</b><gray> = <gold>"
                + Msg.coins(Math.round(q.reward() * startMult)) + "$<gray>, sinkt jede Sekunde · " + windowSeconds()
                + " s Zeit · erste richtige Antwort gewinnt!");
        for (Player pl : plugin.getServer().getOnlinePlayers()) {
            if (plugin.notifications().wants(pl, "chat.quiz")) Sfx.play(plugin, pl, Sfx.Kind.QUIZ);
        }
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(q), 20L, 20L);
    }

    /** Muss im Hauptthread laufen. */
    public void onChat(Player p, String message) {
        Question q = current;
        if (q == null) return;
        String m = message.trim().toLowerCase(Locale.ROOT);
        for (String a : q.answers()) {
            if (a.trim().toLowerCase(Locale.ROOT).equals(m)) {
                double mu = multiplier();
                double secs = (System.currentTimeMillis() - askedAt) / 1000.0;
                long prize = Math.round(q.reward() * mu);
                current = null;
                stopTicker();
                plugin.economy().deposit(p.getUniqueId(), prize);
                Sfx.play(plugin, p, Sfx.Kind.COIN);
                plugin.notifications().send("chat.quiz", "<gold>" + Msg.escape(p.getName())
                        + " <gray>hat das Quiz in <white>" + String.format(Locale.GERMANY, "%.1f", secs) + " s</white> gelöst: <#7CF57C><b>x"
                        + mult(mu) + "</b> <gray>= <gold>" + Msg.coins(prize) + "$<gray>!");
                if (plugin.hotbar() != null && hotbar(p)) {
                    plugin.hotbar().flash(p, Msg.parse("<gold><b>+" + Msg.coins(prize) + "$</b> <gray>(Quiz x" + mult(mu) + ")"), 60, 6);
                }
                return;
            }
        }
    }
}

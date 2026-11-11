package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Bukkit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

/**
 * Wirtschafts-Protokoll: jede Gutschrift und Abbuchung ab wirtschaft.protokoll-ab (1.000) und jede Überweisung oder
 * Admin-Buchung, mit Spieler, Modus, Betrag, neuem Stand und Herkunft (z. B. „Markt“, „Überweisung an Max“). Im
 * Speicher die letzten 20.000 Einträge (beim Start aus den Dateien der letzten 7 Tage), auf der Platte eine Datei je
 * Tag in wirtschaft/ (wirtschaft.tage, 30 Tage). Suche im Kadmin unter „Wirtschaft“. Meldet Auffälliges an die
 * Team-Warnungen.
 */
public final class EconomyLog {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final int KEEP = 20_000;

    /** Ein Eintrag: Zeit, Spieler, Modus, Betrag (+/-), Stand danach, Herkunft. */
    public record Entry(long time, UUID player, String mode, long amount, long balance, String source) {}

    /** Herkunft für die nächste Buchung im selben Thread (z. B. „Überweisung an Max“). */
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    private final KubyPlugins plugin;
    private final File dir;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final ConcurrentLinkedQueue<Entry> pending = new ConcurrentLinkedQueue<>();
    /** Gutschriften der letzten 10 Minuten je Spieler (für „schnelles Geld“). */
    private final Map<UUID, Deque<long[]>> recent = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFastAlert = new ConcurrentHashMap<>();

    public EconomyLog(KubyPlugins plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "wirtschaft");
        load();
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flush, 200L, 200L);
    }

    /** Buchungen in run bekommen die Herkunft label (verschachtelt: die innerste gilt). */
    public static void with(String label, Runnable run) {
        String old = CONTEXT.get();
        CONTEXT.set(label);
        try {
            run.run();
        } finally {
            if (old == null) CONTEXT.remove();
            else CONTEXT.set(old);
        }
    }

    // =================================================================================================================
    // Erfassen
    // =================================================================================================================

    /** Von EconomyService (live) und ModeService (anderer Modus) aufgerufen. */
    public void record(UUID id, String mode, long amount, long balance) {
        if (amount == 0) return;
        String ctx = CONTEXT.get();
        String source = ctx != null ? ctx : source();
        long min = plugin.getConfig().getLong("wirtschaft.protokoll-ab", 1000);
        boolean always = ctx != null || source.startsWith("Admin");
        if (!always && Math.abs(amount) < min) {
            if (amount > 0) fast(id, amount, source);
            return;
        }
        Entry e = new Entry(System.currentTimeMillis(), id, mode, amount, balance, source);
        synchronized (entries) {
            entries.addFirst(e);
            while (entries.size() > KEEP) entries.removeLast();
        }
        pending.add(e);
        if (amount > 0) {
            fast(id, amount, source);
            long big = plugin.getConfig().getLong("warnungen.gutschrift-ab", 1_000_000);
            if (big > 0 && amount >= big && plugin.alerts() != null) {
                plugin.alerts().raise(AlertService.Kind.GROSSE_GUTSCHRIFT, id, name(id) + " bekam auf einmal " + money(amount) + " $ (" + source + ")");
            }
        }
    }

    /** Viel Geld in kurzer Zeit (Summe der letzten 10 Minuten). */
    private void fast(UUID id, long amount, String source) {
        long limit = plugin.getConfig().getLong("warnungen.verdienst-10min", 5_000_000);
        if (limit <= 0 || plugin.alerts() == null) return;
        long now = System.currentTimeMillis();
        Deque<long[]> d = recent.computeIfAbsent(id, k -> new ArrayDeque<>());
        long sum = 0;
        synchronized (d) {
            d.addLast(new long[]{now, amount});
            while (!d.isEmpty() && now - d.peekFirst()[0] > 600_000L) d.removeFirst();
            for (long[] x : d) sum += x[1];
        }
        if (sum >= limit) {
            Long last = lastFastAlert.get(id);
            if (last == null || now - last > 600_000L) {
                lastFastAlert.put(id, now);
                plugin.alerts().raise(AlertService.Kind.SCHNELLES_GELD, id, name(id) + " verdiente " + money(sum)
                        + " $ in 10 Minuten (zuletzt: " + source + ")");
            }
        }
    }

    /** Herkunft aus dem Aufrufer ablesen (erste Klasse außerhalb von Konto, Modus und Protokoll). */
    private static String source() {
        String cls = StackWalker.getInstance().walk(s -> s
                .map(StackWalker.StackFrame::getClassName)
                .filter(c -> !c.endsWith(".EconomyService") && !c.endsWith(".ModeService") && !c.endsWith(".EconomyLog"))
                .findFirst().orElse("?"));
        String simple = cls.substring(cls.lastIndexOf('.') + 1);
        int dollar = simple.indexOf('$');
        if (dollar > 0) simple = simple.substring(0, dollar);
        return label(simple);
    }

    private static String label(String cls) {
        String c = cls.toLowerCase(Locale.ROOT);
        if (c.contains("market") || c.contains("markt")) return "Markt";
        if (c.contains("order")) return "Aufträge";
        if (c.contains("chestshop")) return "Kistenladen";
        if (c.contains("sell")) return "Verkauf";
        if (c.contains("shop")) return "Shop";
        if (c.contains("casino") || c.contains("blackjack") || c.contains("roulette") || c.contains("slots") || c.contains("crash")
                || c.contains("poker") || c.contains("losbude") || c.contains("coinflip")) return "Casino";
        if (c.contains("duel")) return "Duell";
        if (c.contains("combat") || c.contains("bount")) return "Kopfgeld";
        if (c.contains("insurance")) return "Versicherung";
        if (c.contains("tournament")) return "Turnier";
        if (c.contains("streak")) return "Skyblock-Combo";
        if (c.contains("generator")) return "Generator";
        if (c.contains("oneblock")) return "One-Block";
        if (c.contains("toprew")) return "Top-Insel";
        if (c.contains("job")) return "Job";
        if (c.contains("quest") || c.contains("event")) return "Quest";
        if (c.contains("daily") || c.contains("pot")) return "Daily";
        if (c.contains("vote")) return "Vote";
        if (c.contains("trade")) return "Handel";
        if (c.contains("bank") || c.contains("marry")) return "Bank";
        if (c.contains("angel") || c.contains("fish")) return "Angeln";
        if (c.contains("admin") || c.contains("staff") || c.contains("kp")) return "Admin";
        if (c.contains("battlepass")) return "Battlepass";
        if (c.contains("intro")) return "Einführung";
        if (c.contains("invest") || c.contains("trading")) return "Börse";
        return cls;
    }

    private static String name(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString().substring(0, 8) : n;
    }

    static String money(long v) {
        return String.format(Locale.GERMANY, "%,d", v);
    }

    // =================================================================================================================
    // Suchen
    // =================================================================================================================

    /** Einträge, neueste zuerst, die filter erfüllen (höchstens max). */
    public List<Entry> search(Predicate<Entry> filter, int max) {
        List<Entry> out = new ArrayList<>();
        synchronized (entries) {
            for (Entry e : entries) {
                if (filter.test(e)) {
                    out.add(e);
                    if (out.size() >= max) break;
                }
            }
        }
        return out;
    }

    public int size() {
        synchronized (entries) {
            return entries.size();
        }
    }

    // =================================================================================================================
    // Datei
    // =================================================================================================================

    private void flush() {
        if (pending.isEmpty()) return;
        dir.mkdirs();
        Map<String, List<String>> byDay = new java.util.TreeMap<>();
        for (Entry e; (e = pending.poll()) != null; ) {
            String day = LocalDate.ofInstant(Instant.ofEpochMilli(e.time()), ZONE).toString();
            byDay.computeIfAbsent(day, k -> new ArrayList<>()).add(e.time() + ";" + e.player() + ";" + e.mode() + ";" + e.amount()
                    + ";" + e.balance() + ";" + e.source().replace(';', ',').replace('\n', ' '));
        }
        for (Map.Entry<String, List<String>> d : byDay.entrySet()) {
            File f = new File(dir, d.getKey() + ".csv");
            try (BufferedWriter w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                for (String line : d.getValue()) {
                    w.write(line);
                    w.newLine();
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("[Wirtschaft] Protokoll: " + ex.getMessage());
            }
        }
        // alte Tage löschen
        int days = Math.max(1, plugin.getConfig().getInt("wirtschaft.tage", 30));
        String oldest = LocalDate.now(ZONE).minusDays(days).toString();
        File[] fs = dir.listFiles((x, n) -> n.endsWith(".csv"));
        if (fs != null) for (File f : fs) if (f.getName().substring(0, 10).compareTo(oldest) < 0) f.delete();
    }

    public void shutdown() {
        flush();
    }

    private void load() {
        File[] fs = dir.listFiles((x, n) -> n.endsWith(".csv"));
        if (fs == null) return;
        String from = LocalDate.now(ZONE).minusDays(7).toString();
        java.util.Arrays.sort(fs);
        List<Entry> all = new ArrayList<>();
        for (File f : fs) {
            if (f.getName().substring(0, 10).compareTo(from) < 0) continue;
            try {
                for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                    String[] p = line.split(";", 6);
                    if (p.length < 6) continue;
                    try {
                        all.add(new Entry(Long.parseLong(p[0]), UUID.fromString(p[1]), p[2], Long.parseLong(p[3]), Long.parseLong(p[4]), p[5]));
                    } catch (IllegalArgumentException ignored) {
                        // kaputte Zeile
                    }
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("[Wirtschaft] " + f.getName() + ": " + ex.getMessage());
            }
        }
        int start = Math.max(0, all.size() - KEEP);
        synchronized (entries) {
            for (int i = start; i < all.size(); i++) entries.addFirst(all.get(i));
        }
    }
}

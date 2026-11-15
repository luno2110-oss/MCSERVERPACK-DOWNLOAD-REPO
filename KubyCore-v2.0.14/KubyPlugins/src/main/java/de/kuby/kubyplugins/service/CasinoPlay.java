package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Gemeinsamer Einsatz- und Gewinn-Kern aller Casino-Spiele gegen die Bank (Blackjack, Roulette, Slots, Crash, Losbude).
 *
 * <ul>
 *   <li><b>Währung</b>: Chips für alle, Juwelen für VIP (Rang vip oder höher). Juwelen: Einsatz 50 bis 10.000 je Spiel
 *       und Runde, höchstens 50.000 Juwelen Spielvolumen je Tag. Die Währung eines offenen Einsatzes ist fest
 *       (gespeichert, auch für Rückgaben nach einem Absturz).</li>
 *   <li><b>Siegesserie</b>: jeder Sieg in Folge erhöht den Gewinn-Multiplikator um 0,2 (1. Sieg x1, 2. x1,2 … bis x2,5).
 *       Unentschieden lassen die Serie stehen, eine Niederlage beendet sie.</li>
 *   <li><b>Goldene Zeit</b>: jeden Tag 17 bis 22 Uhr werden Gewinne verdoppelt.</li>
 *   <li><b>Trost</b>: jede Niederlage gibt einen kleinen Teil des Einsatzes als Cashback zurück, nach drei
 *       Niederlagen in Folge gibt es auf den nächsten Sieg einen Comeback-Bonus.</li>
 * </ul>
 * Bonus gilt nur auf den Reingewinn (nicht auf den Einsatz).
 */
public final class CasinoPlay {

    public enum Cur { CHIPS, JEWELS }

    /** Ergebnis einer Abrechnung. payout = was ausgezahlt wurde (inkl. Bonus), bonus = Anteil aus Serie/Goldener Zeit. */
    public record Outcome(Cur cur, long stake, long gross, long payout, long bonus, double mult, int streak,
                          boolean golden, boolean comeback, long cashback, int lossStreak) {
        public long net() {
            return payout + cashback - stake;
        }

        public boolean win() {
            return gross > stake;
        }

        public boolean push() {
            return gross == stake;
        }
    }

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Cur> mode = new HashMap<>();
    /** Spieler mit erstem Jackpot: dreifache Höchsteinsätze im ganzen Casino (casino.jackpot-bonus.faktor). */
    private final java.util.Set<UUID> highLimit = new java.util.HashSet<>();
    /** Offene Einsätze je Spieler und Spiel: Währung und Summe. Schlüssel "uuid|spiel". */
    private final Map<String, Cur> bound = new HashMap<>();
    private final Map<String, Long> open = new HashMap<>();
    private final Map<UUID, Integer> streak = new HashMap<>();
    private final Map<UUID, Integer> best = new HashMap<>();
    private final Map<UUID, Integer> losses = new HashMap<>();
    private final Map<UUID, Long> volume = new HashMap<>();
    private String volumeDay = "";
    private BossBar goldenBar;
    /** Spieltest (/kubytest casino): jede Abrechnung als Zeile im Log. */
    public boolean testLog;
    private boolean goldenWas;

    public CasinoPlay(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "casino-play.yml");
        load();
    }

    // =================================================================================================================
    // Einstellungen
    // =================================================================================================================

    private double cfg(String key, double def) {
        return plugin.getConfig().getDouble("casino.bonus." + key, def);
    }

    /** Spiele, in denen man mit Juwelen spielen kann (casino.juwelen.spiele, Standard nur Blackjack). Alle anderen immer Chips. */
    public boolean jewelGame(String game) {
        java.util.List<String> l = plugin.getConfig().getStringList("casino.juwelen.spiele");
        if (l.isEmpty()) l = java.util.List.of("blackjack");
        String base = game.endsWith("_hr") ? game.substring(0, game.length() - 3) : game;
        for (String g : l) if (g.equalsIgnoreCase(base)) return true;
        return false;
    }

    /** Lesbare Liste der Juwelen-Spiele für Meldungen. */
    public String jewelGamesLabel() {
        java.util.List<String> l = plugin.getConfig().getStringList("casino.juwelen.spiele");
        if (l.isEmpty()) l = java.util.List.of("blackjack");
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String g : l) out.add(g.isEmpty() ? g : Character.toUpperCase(g.charAt(0)) + g.substring(1));
        return String.join(", ", out);
    }

    public long jewelMin() {
        return plugin.getConfig().getLong("casino.juwelen.min", 50);
    }

    public long jewelMax() {
        return plugin.getConfig().getLong("casino.juwelen.max", 10_000);
    }

    public long jewelVolume() {
        return plugin.getConfig().getLong("casino.juwelen.volumen", plugin.getConfig().getLong("casino.juwelen.volumen-tag", 50_000));
    }

    /** Zeitraum des Juwelen-Volumens: "tag" (Standard), "woche", "monat" oder "gesamt". */
    private String volumePeriod() {
        return plugin.getConfig().getString("casino.juwelen.zeitraum", "tag").toLowerCase(java.util.Locale.ROOT);
    }

    private String periodKey() {
        LocalDate d = LocalDate.now(ZONE);
        return switch (volumePeriod()) {
            case "gesamt" -> "gesamt";
            case "woche" -> d.getYear() + "-W" + d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case "monat" -> d.getYear() + "-" + d.getMonthValue();
            default -> d.toString();
        };
    }

    public String volumePeriodLabel() {
        return switch (volumePeriod()) {
            case "gesamt" -> "insgesamt";
            case "woche" -> "diese Woche";
            case "monat" -> "diesen Monat";
            default -> "heute";
        };
    }

    /** Serien-Multiplikator für den Sieg nach n Siegen in Folge (normale Tische). */
    public double streakMult(int winsBefore) {
        return streakMult("", winsBefore);
    }

    /** Faktor auf alle Höchsteinsätze (Chips): 3 nach dem ersten Jackpot, sonst 1. */
    public synchronized double limitFactor(UUID id) {
        return highLimit.contains(id) ? plugin.getConfig().getDouble("casino.jackpot-bonus.faktor", 3.0) : 1.0;
    }

    public synchronized boolean hasHighLimit(UUID id) {
        return highLimit.contains(id);
    }

    /** Erster Jackpot: Status setzen. true, wenn er neu ist (dann läuft die Szene beim Geschäftsführer). */
    public synchronized boolean firstJackpot(UUID id) {
        if (!highLimit.add(id)) return false;
        save();
        return true;
    }

    /** Höchsteinsatz für diesen Spieler (Grundwert x Faktor). */
    public long maxFor(UUID id, long base) {
        return Math.round(base * limitFactor(id));
    }

    /** High-Roller-Spiel (weinroter Tisch): Name endet auf _hr. */
    public static boolean highRoller(String game) {
        return game != null && game.endsWith("_hr");
    }

    /**
     * Serien-Multiplikator je Spiel: normal +0,2x je Sieg bis x2,5, an High-Roller-Tischen +0,5x bis x8
     * (casino.highroller.serie-schritt / serie-max).
     */
    public double streakMult(String game, int winsBefore) {
        boolean hr = highRoller(game);
        double step = hr ? plugin.getConfig().getDouble("casino.highroller.serie-schritt", 0.5) : cfg("serie-schritt", 0.2);
        double max = hr ? plugin.getConfig().getDouble("casino.highroller.serie-max", 8.0) : cfg("serie-max", 2.5);
        return Math.min(max, 1.0 + step * Math.max(0, winsBefore));
    }

    // =================================================================================================================
    // Goldene Zeit
    // =================================================================================================================

    public boolean goldenEnabled() {
        return plugin.getConfig().getBoolean("casino.goldene-zeit.aktiv", true);
    }

    private int goldenStart() {
        return plugin.getConfig().getInt("casino.goldene-zeit.von", 17);
    }

    private int goldenEnd() {
        return plugin.getConfig().getInt("casino.goldene-zeit.bis", 22);
    }

    public double goldenFactor() {
        return plugin.getConfig().getDouble("casino.goldene-zeit.faktor", 2.0);
    }

    public boolean golden() {
        if (!goldenEnabled()) return false;
        int h = LocalTime.now(ZONE).getHour();
        return h >= goldenStart() && h < goldenEnd();
    }

    /** Minuten bis zum Ende (läuft) bzw. bis zum Start (läuft nicht). */
    public long goldenMinutes() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime target = now.withHour(golden() ? goldenEnd() : goldenStart()).withMinute(0).withSecond(0).withNano(0);
        if (!target.isAfter(now)) target = target.plusDays(1);
        return java.time.Duration.between(now, target).toMinutes();
    }

    public String goldenLabel() {
        long m = goldenMinutes();
        String t = m >= 60 ? (m / 60) + " Std. " + (m % 60) + " Min." : m + " Min.";
        return golden() ? "Goldene Zeit · Gewinne x" + fmt(goldenFactor()) + " · noch " + t
                : "Goldene Zeit " + goldenStart() + "–" + goldenEnd() + " Uhr · in " + t;
    }

    /** Jede Sekunde: Bossbar für alle im Casino während der Goldenen Zeit, Ansage bei Start und Ende. */
    public void tick() {
        boolean g = golden();
        if (g != goldenWas) {
            goldenWas = g;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!"survival".equals(plugin.modes().modeOf(p.getUniqueId()))) continue;
                if (g) {
                    p.sendMessage(Msg.parse("<gradient:#FFE066:#F2A900><b>GOLDENE ZEIT</b></gradient> <dark_gray>▶ <white>Bis "
                            + goldenEnd() + " Uhr zahlt das Casino <gold><b>doppelte Gewinne</b></gold>!"));
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
                } else {
                    p.sendMessage(Msg.parse("<gold>GOLDENE ZEIT <dark_gray>▶ <gray>vorbei. Morgen ab " + goldenStart() + " Uhr wieder."));
                }
            }
        }
        if (goldenBar == null) {
            goldenBar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_10);
        }
        if (!g) {
            if (!goldenBar.getPlayers().isEmpty()) goldenBar.removeAll();
            return;
        }
        long total = Math.max(1, (goldenEnd() - goldenStart()) * 60L);
        goldenBar.setProgress(Math.max(0, Math.min(1, goldenMinutes() / (double) total)));
        goldenBar.setTitle("§6§l✦ GOLDENE ZEIT ✦ §r§eGewinne x" + fmt(goldenFactor()) + " §7· noch §f" + goldenMinutes() / 60 + ":"
                + String.format(Locale.ROOT, "%02d", goldenMinutes() % 60) + " h");
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean in = plugin.casino() != null && plugin.regions().find(CasinoService.REGION) != null
                    && plugin.regions().find(CasinoService.REGION).contains(p.getLocation());
            if (in && !goldenBar.getPlayers().contains(p)) goldenBar.addPlayer(p);
            else if (!in && goldenBar.getPlayers().contains(p)) goldenBar.removePlayer(p);
        }
    }

    public void shutdown() {
        if (goldenBar != null) goldenBar.removeAll();
        save();
    }

    // =================================================================================================================
    // Währung
    // =================================================================================================================

    /** VIP: Rang vip oder höher (LuckPerms group.x oder kuby.rank.x) oder kuby.casino.juwelen. */
    public boolean vip(Player p) {
        if (p.hasPermission("kuby.casino.juwelen") || p.hasPermission("kuby.admin")) return true;
        java.util.List<String> order = plugin.kits().rankOrder();
        int need = order.indexOf(plugin.getConfig().getString("casino.juwelen.rang", "vip"));
        return need >= 0 && plugin.kits().rankLevel(p) >= need;
    }

    public synchronized Cur mode(UUID id) {
        return mode.getOrDefault(id, Cur.CHIPS);
    }

    public synchronized boolean hasOpen(UUID id) {
        String pre = id + "|";
        for (Map.Entry<String, Long> e : open.entrySet()) if (e.getKey().startsWith(pre) && e.getValue() > 0) return true;
        return false;
    }

    /** Währung wechseln. Gibt null zurück oder den Grund, warum es nicht geht. */
    public synchronized String setMode(Player p, Cur cur) {
        if (cur == Cur.JEWELS && !vip(p)) return "Mit Juwelen spielen nur VIPs (ab Rang VIP).";
        if (hasOpen(p.getUniqueId())) return "Erst die laufenden Einsätze abwarten.";
        if (cur == Cur.CHIPS) mode.remove(p.getUniqueId());
        else mode.put(p.getUniqueId(), cur);
        save();
        return null;
    }

    public String toggle(Player p) {
        return setMode(p, mode(p.getUniqueId()) == Cur.CHIPS ? Cur.JEWELS : Cur.CHIPS);
    }

    private static String key(UUID id, String game) {
        return id + "|" + game.toLowerCase(Locale.ROOT);
    }

    /** Währung, in der der Spieler in diesem Spiel gerade spielt (offener Einsatz, sonst gewählte). */
    public synchronized Cur cur(UUID id, String game) {
        Cur c = bound.get(key(id, game));
        if (c != null) return c;
        return jewelGame(game) ? mode(id) : Cur.CHIPS;
    }

    public long balance(UUID id, Cur cur) {
        return cur == Cur.JEWELS ? plugin.jewels().balance(id) : plugin.casino().chips(id);
    }

    public long balance(UUID id, String game) {
        return balance(id, cur(id, game));
    }

    public static String unit(Cur cur, long amount) {
        return de.kuby.kubyplugins.casino.CasinoText.num(amount) + (cur == Cur.JEWELS ? " Juwelen" : amount == 1 ? " Chip" : " Chips");
    }

    public String unit(UUID id, String game, long amount) {
        return unit(cur(id, game), amount);
    }

    /** Farbe der Währung für MiniMessage. */
    public static String color(Cur cur) {
        return cur == Cur.JEWELS ? "#7FE3FF" : "#F2C94C";
    }

    /** Einsatz-Werte der Rack-Chips je Währung. */
    public long[] rack(UUID id, String game, long[] chipRack) {
        if (cur(id, game) != Cur.JEWELS) return chipRack;
        return new long[]{50, 100, 500, 1000, 5000, 10000};
    }

    private void rollDay() {
        String today = periodKey();
        if (!today.equals(volumeDay)) {
            volumeDay = today;
            volume.clear();
        }
    }

    public synchronized long volumeLeft(UUID id) {
        rollDay();
        return Math.max(0, jewelVolume() - volume.getOrDefault(id, 0L));
    }

    /** Offener Einsatz des Spielers in diesem Spiel. */
    public synchronized long openStake(UUID id, String game) {
        return open.getOrDefault(key(id, game), 0L);
    }

    /**
     * Nimmt einen Einsatz (auch Nachsetzen wie Doppeln/Teilen). Gibt null bei Erfolg zurück, sonst die Meldung.
     * Juwelen: zusammen mit dem offenen Einsatz 50 bis 10.000 je Runde, Tagesvolumen 50.000.
     */
    public synchronized String take(Player p, String game, long amount) {
        return take(p, game, amount, null);
    }

    /** Wie take, aber mit fester Währung (z. B. Lose der Losbude kosten immer Chips). */
    public synchronized String take(Player p, String game, long amount, Cur force) {
        if (amount <= 0) return "Ungültiger Einsatz.";
        UUID id = p.getUniqueId();
        String k = key(id, game);
        Cur c = force != null && !bound.containsKey(k) ? force : cur(id, game);
        long already = open.getOrDefault(k, 0L);
        if (c == Cur.JEWELS) {
            if (!vip(p)) return "Mit Juwelen spielen nur VIPs. Stell mit /casino waehrung auf Chips um.";
            if (already + amount < jewelMin()) return "Mit Juwelen setzt du mindestens " + unit(c, jewelMin()) + ".";
            if (already + amount > jewelMax()) return "Höchstens " + unit(c, jewelMax()) + " je Runde.";
            if (amount > volumeLeft(id)) {
                return "Dein Juwelen-Volumen " + volumePeriodLabel() + " ist aufgebraucht (" + unit(c, jewelVolume()) + ", frei: "
                        + unit(c, volumeLeft(id)) + ").";
            }
            if (!plugin.jewels().take(id, amount)) return "So viele Juwelen hast du nicht.";
            volume.merge(id, amount, Long::sum);
        } else {
            if (!plugin.casino().takeChips(id, amount)) return "So viele Chips hast du nicht. Tausch sie an der Kasse.";
            long feed = Math.round(amount * plugin.getConfig().getDouble("casino.jackpot-share", 0.01));
            if (feed > 0 && !"poker".equalsIgnoreCase(game)) plugin.casino().addJackpot(feed);
        }
        bound.put(k, c);
        open.put(k, already + amount);
        save();
        return null;
    }

    /** Gibt einen offenen Einsatz ganz oder teilweise zurück (Aufstehen, Rücknahme, Abbruch, Absturz). */
    public synchronized void refund(UUID id, String game, long amount) {
        if (amount <= 0) return;
        String k = key(id, game);
        Cur c = bound.getOrDefault(k, Cur.CHIPS);
        pay(id, c, amount);
        if (c == Cur.JEWELS) {
            rollDay();
            volume.computeIfPresent(id, (u, v) -> Math.max(0, v - amount));
        }
        reduce(k, amount);
        save();
    }

    private void reduce(String k, long amount) {
        long left = open.getOrDefault(k, 0L) - amount;
        if (left <= 0) {
            open.remove(k);
            bound.remove(k);
        } else {
            open.put(k, left);
        }
    }

    private void pay(UUID id, Cur c, long amount) {
        if (amount <= 0) return;
        if (c == Cur.JEWELS) plugin.jewels().add(id, amount);
        else plugin.casino().giveChips(id, amount);
    }

    /**
     * Rechnet einen Einsatz ab: gross = Auszahlung nach den Spielregeln (inkl. Einsatz). Zahlt aus, wendet Siegesserie,
     * Goldene Zeit, Comeback-Bonus und Cashback an und schreibt die Statistik.
     */
    public synchronized Outcome settle(UUID id, String game, long stake, long gross) {
        String k = key(id, game);
        Cur c = bound.getOrDefault(k, jewelGame(game) ? mode(id) : Cur.CHIPS);
        reduce(k, stake);
        int before = streak.getOrDefault(id, 0);
        int lost = losses.getOrDefault(id, 0);
        long bonus = 0, cashback = 0;
        double mult = 1;
        boolean golden = false, comeback = false;
        int newStreak = before;
        if (gross > stake) {
            long profit = gross - stake;
            mult = streakMult(game, before);
            if (golden()) {
                golden = true;
                mult *= goldenFactor();
            }
            if (lost >= (int) cfg("comeback-ab", 3)) {
                comeback = true;
                mult += cfg("comeback-bonus", 0.25);
            }
            mult = Math.min(mult, highRoller(game) ? plugin.getConfig().getDouble("casino.highroller.max-gesamt", 16.0)
                    : cfg("max-gesamt", 6.0));
            bonus = Math.round(profit * (mult - 1));
            newStreak = before + 1;
            streak.put(id, newStreak);
            best.merge(id, newStreak, Math::max);
            losses.remove(id);
            lost = 0;
        } else if (gross < stake) {
            cashback = Math.round((stake - gross) * cfg("cashback", 0.03));
            if (cashback <= 0 && stake - gross >= 20) cashback = 1;
            newStreak = 0;
            streak.remove(id);
            lost++;
            losses.put(id, lost);
        }
        long payout = gross + bonus;
        pay(id, c, payout + cashback);
        if (c == Cur.CHIPS) {
            if (plugin.casinoLog() != null) plugin.casinoLog().record(capital(game), id, stake, payout + cashback);
        } else {
            jewelStats(id, stake, payout + cashback);
        }
        save();
        if (testLog) {
            String name = Bukkit.getOfflinePlayer(id).getName();
            plugin.getLogger().info("[Casino-Test] " + game + " " + name + ": Einsatz " + stake + ", Spiel zahlt " + gross
                    + ", ausgezahlt " + payout + (bonus > 0 ? " (Bonus " + bonus + ", x" + fmt(mult) + ")" : "")
                    + (cashback > 0 ? ", Cashback " + cashback : "") + ", Serie " + newStreak + (golden ? ", Goldene Zeit" : "")
                    + ", Währung " + c);
        }
        return new Outcome(c, stake, gross, payout, bonus, mult, newStreak, golden, comeback, cashback, lost);
    }

    /** Ergebnis ohne Bank-Abrechnung (z. B. Poker): nur Serie fortschreiben, kein Bonus. */
    public synchronized int streak(UUID id) {
        return streak.getOrDefault(id, 0);
    }

    public synchronized int bestStreak(UUID id) {
        return best.getOrDefault(id, 0);
    }

    public synchronized int lossStreak(UUID id) {
        return losses.getOrDefault(id, 0);
    }

    private final Map<UUID, long[]> jewelTotals = new HashMap<>();

    private void jewelStats(UUID id, long staked, long paid) {
        long[] t = jewelTotals.computeIfAbsent(id, k -> new long[2]);
        t[0] += staked;
        t[1] += paid;
    }

    public synchronized long[] jewelTotals(UUID id) {
        return jewelTotals.getOrDefault(id, new long[2]).clone();
    }

    private static String capital(String game) {
        return game.isEmpty() ? game : Character.toUpperCase(game.charAt(0)) + game.substring(1);
    }

    // =================================================================================================================
    // Rückmeldung
    // =================================================================================================================

    /** Zeile mit den Boni eines Gewinns, z. B. "Serie 3 x1,4 · Goldene Zeit x2". Leer ohne Bonus. */
    public static String bonusLine(Outcome o) {
        StringBuilder sb = new StringBuilder();
        if (o.win() && o.streak() >= 2) sb.append("<#FF8A3D>🔥 Serie ").append(o.streak()).append("</#FF8A3D>");
        if (o.golden()) sb.append(sb.length() > 0 ? " <dark_gray>· " : "").append("<gold>✦ Goldene Zeit</gold>");
        if (o.comeback()) sb.append(sb.length() > 0 ? " <dark_gray>· " : "").append("<#7CFF7C>Comeback</#7CFF7C>");
        if (o.bonus() > 0) sb.append(" <dark_gray>· <white>x").append(fmt(o.mult())).append(" <green>+").append(
                de.kuby.kubyplugins.casino.CasinoText.num(o.bonus())).append(" Bonus");
        return sb.toString();
    }

    /**
     * Gemeinsames Feedback nach einer Abrechnung: Actionbar mit Ergebnis und Boni, Töne, Partikel am Tisch, Titel bei
     * Serien-Meilensteinen. Bei Verlust: Cashback und Serie zum Weitermachen.
     */
    public void feedback(Player p, Outcome o, Location at) {
        if (p == null || !p.isOnline()) return;
        String col = color(o.cur());
        if (o.win()) {
            String line = "<green><b>+" + de.kuby.kubyplugins.casino.CasinoText.num(o.payout() - o.stake()) + "</b> <" + col + ">"
                    + (o.cur() == Cur.JEWELS ? "Juwelen" : "Chips") + "</" + col + ">";
            String b = bonusLine(o);
            p.sendActionBar(Msg.parse(line + (b.isEmpty() ? "" : " <dark_gray>| " + b)));
            float pitch = (float) Math.min(2.0, 1.0 + 0.08 * o.streak());
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, pitch);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, pitch);
            if (o.bonus() > 0) later(() -> p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.6f), 6);
            if (at != null && at.getWorld() != null) {
                at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 10 + 2 * o.streak(), 0.3, 0.2, 0.3, 0);
                if (o.golden()) at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.3, 0), 24, 0.35, 0.3, 0.35, 0,
                        new Particle.DustOptions(Color.fromRGB(0xFFD34D), 1.2f));
                if (o.streak() >= 3) at.getWorld().spawnParticle(Particle.FLAME, at, 6 + o.streak(), 0.25, 0.1, 0.25, 0.01);
            }
            int s = o.streak();
            if (s == 3 || s == 5 || s == 8 || s % 10 == 0) {
                p.showTitle(net.kyori.adventure.title.Title.title(Msg.parse("<gradient:#FFB347:#FF4D2E><b>SERIE " + s + "!</b></gradient>"),
                        Msg.parse("<gray>Nächster Sieg zahlt <white>x" + fmt(streakMult(s)) + "</white>"),
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(150), java.time.Duration.ofMillis(1400),
                                java.time.Duration.ofMillis(400))));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.3f);
            }
        } else if (o.push()) {
            p.sendActionBar(Msg.parse("<gray>Unentschieden · Einsatz zurück"
                    + (streak(p.getUniqueId()) >= 2 ? " <dark_gray>· <#FF8A3D>🔥 Serie " + streak(p.getUniqueId()) + " bleibt" : "")));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
        } else {
            StringBuilder sb = new StringBuilder("<#FF6B6B>-" + de.kuby.kubyplugins.casino.CasinoText.num(o.stake() - o.gross()) + "</#FF6B6B>");
            if (o.cashback() > 0) sb.append(" <dark_gray>· <aqua>+").append(de.kuby.kubyplugins.casino.CasinoText.num(o.cashback()))
                    .append(" Cashback</aqua>");
            int need = (int) cfg("comeback-ab", 3);
            if (o.lossStreak() >= need) sb.append(" <dark_gray>· <#7CFF7C>Comeback-Bonus aktiv: nächster Sieg +x")
                    .append(fmt(cfg("comeback-bonus", 0.25))).append("</#7CFF7C>");
            else sb.append(" <dark_gray>· <gray>Noch ").append(need - o.lossStreak()).append(" bis zum Comeback-Bonus");
            p.sendActionBar(Msg.parse(sb.toString()));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            if (o.cashback() > 0) later(() -> p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.4f), 8);
            if (at != null && at.getWorld() != null) at.getWorld().spawnParticle(Particle.SMOKE, at, 6, 0.2, 0.1, 0.2, 0.01);
        }
    }

    private void later(Runnable r, long ticks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, r, ticks);
    }

    public static String fmt(double d) {
        String s = String.format(Locale.GERMANY, "%.2f", d);
        while (s.contains(",") && (s.endsWith("0") || s.endsWith(","))) s = s.substring(0, s.length() - 1);
        return s;
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        volumeDay = y.getString("volume-day", "");
        readMap(y.getConfigurationSection("mode"), (k, v) -> mode.put(UUID.fromString(k), Cur.valueOf(v.toString())));
        for (String s : y.getStringList("high-limit")) {
            try {
                highLimit.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
        readMap(y.getConfigurationSection("streak"), (k, v) -> streak.put(UUID.fromString(k), ((Number) v).intValue()));
        readMap(y.getConfigurationSection("best"), (k, v) -> best.put(UUID.fromString(k), ((Number) v).intValue()));
        readMap(y.getConfigurationSection("losses"), (k, v) -> losses.put(UUID.fromString(k), ((Number) v).intValue()));
        readMap(y.getConfigurationSection("volume"), (k, v) -> volume.put(UUID.fromString(k), ((Number) v).longValue()));
        for (String line : y.getStringList("open")) {
            String[] p = line.split(";");
            if (p.length != 3) continue;
            try {
                bound.put(p[0], Cur.valueOf(p[1]));
                open.put(p[0], Long.parseLong(p[2]));
            } catch (IllegalArgumentException ignored) {
            }
        }
        readMap(y.getConfigurationSection("jewel-totals"), (k, v) -> {
            String[] p = v.toString().split(";");
            if (p.length == 2) jewelTotals.put(UUID.fromString(k), new long[]{Long.parseLong(p[0]), Long.parseLong(p[1])});
        });
    }

    private interface Reader {
        void read(String key, Object value);
    }

    private static void readMap(ConfigurationSection s, Reader r) {
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                Object v = s.get(k);
                if (v != null) r.read(k, v);
            } catch (RuntimeException ignored) {
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("volume-day", volumeDay);
        mode.forEach((k, v) -> y.set("mode." + k, v.name()));
        y.set("high-limit", highLimit.stream().map(UUID::toString).sorted().toList());
        streak.forEach((k, v) -> y.set("streak." + k, v));
        best.forEach((k, v) -> y.set("best." + k, v));
        losses.forEach((k, v) -> y.set("losses." + k, v));
        volume.forEach((k, v) -> y.set("volume." + k, v));
        jewelTotals.forEach((k, v) -> y.set("jewel-totals." + k, v[0] + ";" + v[1]));
        java.util.List<String> o = new java.util.ArrayList<>();
        open.forEach((k, v) -> o.add(k + ";" + bound.getOrDefault(k, Cur.CHIPS).name() + ";" + v));
        y.set("open", o);
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("casino-play.yml: " + e.getMessage());
        }
    }
}

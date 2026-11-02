package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Börse wie eine Trading-App. Jedes Item der Shop-Bereiche Blöcke, Essen, Redstone und Dekoration ist ein Wert
 * (Ticker). Der Kurs läuft von selbst wie an einer echten Börse, ohne dass Spieler ihn bewegen (simulierter Verlauf):
 * <ul>
 *   <li>zufällige Schwankung je Schritt, je nach Seltenheit des Items stärker oder schwächer,</li>
 *   <li>Trends (Auf- und Abwärtsphasen), die einige Stunden halten und dann wechseln,</li>
 *   <li>ruhige und hektische Phasen (die Schwankung selbst schwankt),</li>
 *   <li>Marktstimmung und Bereiche: alle Werte laufen zum Teil gemeinsam, Werte eines Bereichs noch mehr,</li>
 *   <li>Nachrichten: seltene Kurssprünge eines Werts oder des ganzen Markts (mit Meldung),</li>
 *   <li>ein schwacher Zug zurück zum Grundpreis, damit der Kurs langfristig in der Nähe bleibt (Grenzen 35 % bis 300 %).</li>
 * </ul>
 * Neue Werte bekommen einen simulierten Verlauf der letzten 60 Tage, damit die Charts nicht leer sind. Optional
 * (trading.spieler-einfluss) bewegen Shop-Käufe, Verkäufe und große Orders den Kurs zusätzlich. Der Shop verkauft zum
 * Kurs der Börse, aber nie so billig, dass Kaufen im Shop und Verkaufen an den Server Gewinn bringt.
 *
 * Spieler kaufen und verkaufen zum Kurs (Gebühr je Seite), halten Positionen mit Einstandspreis und legen Orders an:
 * Limit-Kauf (Geld wird reserviert), Limit-Verkauf, Stop-Loss und Take-Profit; sie werden bei jedem Kurs-Schritt
 * geprüft, auch wenn der Spieler offline oder in einem anderen Modus ist. Kerzen in drei Zeitrahmen (5 Minuten,
 * 1 Stunde, 1 Tag). Positionen, Orders und Verlauf gelten je Modus (eigenes Geld je Modus). Daten: trading.yml
 * (gespeichert im Hintergrund).
 */
public final class TradingService {

    public enum Frame {
        M5(300_000L, 96, "5M"), H1(3_600_000L, 96, "1H"), D1(86_400_000L, 60, "1T");

        public final long ms;
        public final int keep;
        public final String label;

        Frame(long ms, int keep, String label) {
            this.ms = ms;
            this.keep = keep;
            this.label = label;
        }
    }

    public static final class Candle {
        public long t;
        public double o, h, l, c, vol;

        Candle(long t, double price) {
            this.t = t;
            o = h = l = c = price;
        }
    }

    public static final class Ticker {
        public final String id, symbol, name;
        public final Material material;
        public double base, price, factor = 1.0;
        /** Simulation: Bereich (0..3), Grenzen, laufender Trend (je Tag), Ende des Trends, Unruhe (log), Schwankungs-Faktor. */
        int sector;
        double floor, cap, drift, lv, volMul = 1.0, rvol = 1.0;
        long trendUntil;
        final EnumMap<Frame, Deque<Candle>> candles = new EnumMap<>(Frame.class);

        Ticker(String id, String symbol, String name, Material material, double base) {
            this.id = id;
            this.symbol = symbol;
            this.name = name;
            this.material = material;
            this.base = base;
            this.price = base;
            for (Frame f : Frame.values()) candles.put(f, new ArrayDeque<>());
        }

        public List<Candle> candles(Frame f) {
            return new ArrayList<>(candles.get(f));
        }
    }

    public enum OrderType {
        LIMIT_BUY("Limit-Kauf"), LIMIT_SELL("Limit-Verkauf"), STOP_LOSS("Stop-Loss"), TAKE_PROFIT("Take-Profit");

        public final String label;

        OrderType(String label) {
            this.label = label;
        }
    }

    /** Order: bei LIMIT_BUY ist coins reserviert, sonst qty Stück (höchstens die Position). */
    public record Order(int id, UUID owner, String mode, String ticker, OrderType type, double trigger, double qty, long coins, long created) {}

    public static final class Position {
        public double qty, avg;
    }

    public record Trade(long t, String ticker, String side, double qty, double price, long coins, long pnl) {}

    /** Nachricht: Zeit, Symbol (leer = ganzer Markt), Text, Veränderung in Prozent. */
    public record News(long t, String symbol, String text, double pct) {}

    /** Version des Kurs-Modells; ältere Verläufe werden beim Laden einmal neu simuliert. */
    private static final int MODEL = 2;

    private final KubyPlugins plugin;
    private final File file;
    private final Random rnd = new Random();
    private final Map<String, Ticker> tickers = new LinkedHashMap<>();
    private final Map<String, Map<UUID, Map<String, Position>>> positions = new HashMap<>();
    private final List<Order> orders = new ArrayList<>();
    private final Map<String, Deque<Trade>> history = new HashMap<>();
    private int nextOrder = 1;
    private long lastTick;
    private boolean dirty;
    private int model;
    private final Deque<News> news = new ArrayDeque<>();
    /** Marktstimmung und Bereiche: Trend je Tag, Ende des Trends, Unruhe (log). Index 0 = Markt, 1..4 = Bereiche. */
    private final double[] grpDrift = new double[5], grpLv = new double[5];
    private final long[] grpUntil = new long[5];
    private Map<Material, Long> worthCache;
    private long worthAt;
    private volatile boolean saving;

    public TradingService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "trading.yml");
        load();
        long period = Math.max(5, plugin.getConfig().getLong("trading.tick-sekunden", 20)) * 20L;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 200L, period);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) saveAsync();
        }, 1200L, 1200L);
    }

    // =================================================================================================================
    // Einstellungen
    // =================================================================================================================

    private double cfg(String key, double def) {
        return plugin.getConfig().getDouble("trading." + key, def);
    }

    public double feePercent() {
        return cfg("gebuehr-prozent", 0.5);
    }

    /** Bewegen Spieler den Kurs (Shop, große Orders)? Standard: nein, der Kurs läuft nur simuliert. */
    public boolean playerInfluence() {
        return plugin.getConfig().getBoolean("trading.spieler-einfluss", false);
    }

    private double sim(String key, double def) {
        return plugin.getConfig().getDouble("trading.kurs." + key, def);
    }

    private long tickMs() {
        return Math.max(5, plugin.getConfig().getLong("trading.tick-sekunden", 10)) * 1000L;
    }

    /** Einstellungen der Simulation, einmal je Schritt gelesen (nicht je Wert). */
    private record Params(double sigma, double trend, double hours, double unrest, double kappa, double wm, double ws, double newsRate,
                          double newsSize, boolean influence) {}

    private Params params() {
        double wm = Math.max(0, Math.min(0.9, sim("markt-anteil", 0.3)));
        return new Params(sim("volatilitaet-tag", 0.045), sim("trend-staerke", 0.06), Math.max(0.1, sim("trend-dauer-stunden", 8)),
                sim("unruhe", 0.5), sim("rueckkehr-tag", 0.25), wm, Math.max(0, Math.min(0.9 - wm, sim("sektor-anteil", 0.25))),
                sim("nachrichten-pro-tag", 0.2), sim("nachrichten-staerke", 0.07), playerInfluence());
    }

    /** Wie stark ein Wert schwankt: seltene Items mehr (trading.kurs.seltenheit). */
    private double rarityVol(Ticker t) {
        String r = plugin.investments() == null ? "common" : plugin.investments().rarityOf(t.material).name().toLowerCase(Locale.ROOT);
        double def = switch (r) {
            case "frequent" -> 0.8;
            case "rare" -> 1.2;
            case "very_rare" -> 1.4;
            case "unique" -> 1.6;
            default -> 1.0;
        };
        return sim("seltenheit." + r, def);
    }

    // =================================================================================================================
    // Ticker
    // =================================================================================================================

    /** Ticker für alle Items der Börsen-Bereiche anlegen (neue Shop-Items kommen dazu). */
    public synchronized void ensureTickers() {
        Set<String> used = new HashSet<>();
        for (Ticker t : tickers.values()) used.add(t.symbol);
        long now = System.currentTimeMillis();
        if (worthCache == null || now - worthAt > 60_000L) {
            worthCache = plugin.sell() == null ? Map.of() : plugin.sell().worth();
            worthAt = now;
        }
        // Höchster Verkaufswert an den Server: Verkaufs-Stufe ganz oben und Wochen-Meta (+25 %)
        double maxSell = (1.0 + plugin.getConfig().getInt("sellmulti.max-level", 10) * plugin.getConfig().getDouble("sellmulti.step", 0.05)) * 1.25;
        double lo = sim("untergrenze", 0.35), hi = sim("obergrenze", 3.0);
        for (int si = 0; si < EconomyMarketService.DYNAMIC.size(); si++) {
            String sec = EconomyMarketService.DYNAMIC.get(si);
            for (ShopService.Item it : plugin.shop().items(sec)) {
                Ticker t = tickers.get(it.id());
                double base = Math.max(1, it.price());
                boolean fresh = t == null;
                if (fresh) {
                    String sym = symbol(it.material(), used);
                    used.add(sym);
                    t = new Ticker(it.id(), sym, nice(it.material()), it.material(), base);
                }
                t.base = base;
                t.sector = si;
                t.rvol = rarityVol(t);
                // Untergrenze: nie so billig, dass Kaufen im Shop und Verkaufen an den Server Gewinn bringt
                long w = worthCache.getOrDefault(it.material(), 0L);
                t.floor = Math.max(base * lo, w > 0 ? w * Math.max(1, it.amount()) * maxSell * 1.05 : 0);
                t.cap = Math.max(t.floor * 1.5, base * hi);
                if (fresh) {
                    seed(t, -1);
                    tickers.put(it.id(), t);
                    dirty = true;
                } else if (t.price < t.floor || t.price > t.cap) {
                    t.price = Math.max(t.floor, Math.min(t.cap, t.price));
                }
            }
        }
    }

    public static String nice(Material m) {
        String n = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private static String symbol(Material m, Set<String> used) {
        String[] w = m.name().split("_");
        String s = w.length == 1 ? w[0].substring(0, Math.min(4, w[0].length()))
                : (w[0].substring(0, Math.min(2, w[0].length())) + w[w.length - 1].substring(0, Math.min(2, w[w.length - 1].length())));
        String out = s;
        for (int i = 2; used.contains(out); i++) out = s.substring(0, Math.min(3, s.length())) + i;
        return out;
    }

    /**
     * Verlauf der letzten 60 Tage mit demselben Modell simulieren, damit die Charts nicht leer sind: stündliche Schritte
     * bis vor 4 Tagen, dann 5-Minuten-Schritte bis vor 8 Stunden, danach echte Kurs-Schritte. Mit endPrice &gt; 0 wird
     * der Verlauf so verschoben, dass er genau beim heutigen Kurs endet (bestehende Werte springen nicht).
     */
    private void seed(Ticker t, double endPrice) {
        long now = System.currentTimeMillis(), tick = tickMs();
        Params pr = params();
        for (Frame f : Frame.values()) t.candles.get(f).clear();
        t.price = t.base;
        t.drift = 0;
        t.lv = 0;
        t.trendUntil = 0;
        long time = now - now % Frame.D1.ms - (Frame.D1.keep - 1) * Frame.D1.ms;
        long[][] plan = {{now - 4 * Frame.D1.ms, Frame.H1.ms}, {now - 8 * 3_600_000L, Frame.M5.ms}, {now, tick}};
        for (long[] stage : plan) {
            long step = stage[1];
            for (; time < stage[0]; time += step) {
                step(pr, t, time, step / 86_400_000.0, rnd.nextGaussian(), rnd.nextGaussian(), 0, null);
            }
        }
        if (endPrice > 0 && t.price > 0) {
            double k = endPrice / t.price;
            for (Frame f : Frame.values()) {
                for (Candle c : t.candles.get(f)) {
                    c.o *= k;
                    c.h *= k;
                    c.l *= k;
                    c.c *= k;
                }
            }
            t.price = endPrice;
        }
    }

    public synchronized List<Ticker> tickers() {
        return new ArrayList<>(tickers.values());
    }

    public synchronized Ticker ticker(String id) {
        return tickers.get(id);
    }

    /** Kurs eines Shop-Items (für den Shop), ohne Ticker der Grundpreis. */
    public synchronized double price(String id, double fallback) {
        Ticker t = tickers.get(id);
        return t == null ? fallback : t.price;
    }

    /** Veränderung in Prozent über 24 Stunden. */
    public synchronized double change24(Ticker t) {
        List<Candle> h = t.candles(Frame.H1);
        if (h.isEmpty()) return 0;
        Candle ref = h.size() > 24 ? h.get(h.size() - 25) : h.get(0);
        return ref.c <= 0 ? 0 : (t.price - ref.c) / ref.c * 100.0;
    }

    /** Hoch und Tief über 24 Stunden. */
    public synchronized double[] range24(Ticker t) {
        List<Candle> h = t.candles(Frame.H1);
        double hi = t.price, lo = t.price;
        for (int i = Math.max(0, h.size() - 24); i < h.size(); i++) {
            hi = Math.max(hi, h.get(i).h);
            lo = Math.min(lo, h.get(i).l);
        }
        return new double[]{hi, lo};
    }

    // =================================================================================================================
    // Kurs-Schritte
    // =================================================================================================================

    public void tick() {
        List<Runnable> notes = new ArrayList<>();
        synchronized (this) {
            ensureTickers();
            long now = System.currentTimeMillis();
            if (model < MODEL) {
                // alter, fast flacher Verlauf: einmal neu simulieren, der heutige Kurs bleibt
                for (Ticker t : tickers.values()) seed(t, t.price);
                model = MODEL;
            }
            // Schrittweite aus der echten Zeit (nach einer Pause höchstens zwei Schritte, kein Sprung)
            long tick = tickMs();
            long elapsed = lastTick == 0 ? tick : Math.max(1000L, Math.min(2 * tick, now - lastTick));
            lastTick = now;
            double dt = elapsed / 86_400_000.0;
            Params pr = params();
            List<News> fresh = new ArrayList<>();
            stepGroups(pr, now, dt);
            double zm = rnd.nextGaussian();
            double[] zs = new double[grpDrift.length];
            for (int k = 1; k < zs.length; k++) zs[k] = rnd.nextGaussian();
            double marketJump = 0;
            if (rnd.nextDouble() < sim("markt-ereignisse-pro-tag", 0.1) * dt) {
                marketJump = rnd.nextGaussian() * pr.newsSize() * 0.6;
                fresh.add(new News(now, "", (marketJump >= 0 ? MARKET_UP : MARKET_DOWN)[rnd.nextInt(MARKET_UP.length)],
                        (Math.exp(marketJump) - 1) * 100));
            }
            for (Ticker t : tickers.values()) step(pr, t, now, dt, zm, zs[1 + Math.min(3, t.sector)], marketJump, fresh);
            if (pr.influence()) {
                double decay = cfg("nachfrage-abbau", 0.0005);
                for (Ticker t : tickers.values()) t.factor += (1.0 - t.factor) * decay;
            }
            for (News n : fresh) {
                news.addFirst(n);
                while (news.size() > 40) news.pollLast();
                Runnable note = announce(n);
                if (note != null) notes.add(note);
            }
            Iterator<Order> it = orders.iterator();
            while (it.hasNext()) {
                Order o = it.next();
                Ticker t = tickers.get(o.ticker());
                if (t == null) continue;
                boolean hit = switch (o.type()) {
                    case LIMIT_BUY, STOP_LOSS -> t.price <= o.trigger();
                    case LIMIT_SELL, TAKE_PROFIT -> t.price >= o.trigger();
                };
                if (!hit) continue;
                it.remove();
                Runnable note = execute(o, t);
                if (note != null) notes.add(note);
            }
            dirty = true;
        }
        notes.forEach(Runnable::run);
    }

    private static final String[] UP = {"Großauftrag eingegangen", "Lieferengpass treibt den Preis", "Händler kaufen alles auf",
            "Neue Bauprojekte, hohe Nachfrage", "Lager fast leer", "Sammler greifen zu"};
    private static final String[] DOWN = {"Riesiges Lager entdeckt", "Überangebot drückt den Preis", "Händler verkaufen in Panik",
            "Neue Abbau-Methode macht es billig", "Nachfrage bricht ein", "Großverkauf eines Händlers"};
    private static final String[] MARKET_UP = {"Kauflaune an der ganzen Börse", "Rekordtag: alle Werte steigen", "Händler sind in Feierlaune"};
    private static final String[] MARKET_DOWN = {"Börsenbeben: alle Werte fallen", "Panik am ganzen Markt", "Händler ziehen ihr Geld ab"};

    /**
     * Marktstimmung (Index 0) und die vier Bereiche: Trends wechseln nach einigen Stunden, die Unruhe schwankt langsam
     * (ruhige und hektische Phasen).
     */
    private void stepGroups(Params pr, long now, double dt) {
        for (int k = 0; k < grpDrift.length; k++) {
            if (now >= grpUntil[k]) {
                grpDrift[k] = rnd.nextGaussian() * pr.trend() * (k == 0 ? 0.5 : 0.7);
                grpUntil[k] = now + (long) (-Math.log(1 - rnd.nextDouble()) * pr.hours() * 1.5 * 3_600_000L);
            }
            grpLv[k] = ou(grpLv[k], pr.unrest(), dt);
        }
    }

    /** Langsam schwankender Wert um 0 mit Streuung sd (Halbwertszeit etwa ein halber Tag). */
    private double ou(double v, double sd, double dt) {
        double theta = 1.4;
        return v - theta * v * dt + sd * Math.sqrt(2 * theta * dt) * rnd.nextGaussian();
    }

    /**
     * Ein Kurs-Schritt eines Werts über dt Tage: Trend und Stimmung, Zug zum fairen Preis, Schwankung (Markt, Bereich,
     * eigener Anteil), dazu selten ein Sprung durch eine Nachricht. Mit out == null (Verlauf simulieren) laufen Markt und
     * Bereich nur als Zufall mit, ohne Nachrichten.
     */
    private void step(Params pr, Ticker t, long now, double dt, double zm, double zs, double marketJump, List<News> out) {
        if (now >= t.trendUntil) {
            t.drift = rnd.nextGaussian() * pr.trend();
            t.trendUntil = now + (long) (-Math.log(1 - rnd.nextDouble()) * pr.hours() * 3_600_000L);
        }
        t.lv = ou(t.lv, pr.unrest(), dt);
        int g = 1 + Math.min(3, t.sector);
        double groupLv = out == null ? 0 : 0.5 * grpLv[0] + 0.5 * grpLv[g];
        t.volMul = Math.exp(0.7 * t.lv + 0.5 * groupLv - 0.3 * pr.unrest() * pr.unrest());
        double s = pr.sigma() * t.rvol * t.volMul;
        double fair = t.base * (pr.influence() ? t.factor : 1.0);
        double x = Math.log(t.price / fair);
        double mu = t.drift - pr.kappa() * x + (out == null ? 0 : 0.7 * grpDrift[0] + 0.7 * grpDrift[g]);
        double z = Math.sqrt(pr.wm()) * zm + Math.sqrt(pr.ws()) * zs + Math.sqrt(1 - pr.wm() - pr.ws()) * rnd.nextGaussian();
        double r = (mu - 0.5 * s * s) * dt + s * Math.sqrt(dt) * z + marketJump;
        if (rnd.nextDouble() < pr.newsRate() * dt) {
            double j = Math.abs(rnd.nextGaussian()) * pr.newsSize() + 0.015;
            // weit über dem Grundpreis eher schlechte Nachrichten, weit darunter eher gute
            double pUp = Math.max(0.15, Math.min(0.85, 0.5 - x));
            boolean up = rnd.nextDouble() < pUp;
            r += up ? j : -j;
            if (out != null) {
                double pct = (Math.exp(up ? j : -j) - 1) * 100;
                out.add(new News(now, t.symbol, t.name + ": " + (up ? UP : DOWN)[rnd.nextInt(UP.length)], pct));
            }
        }
        t.price = Math.max(t.floor > 0 ? t.floor : t.base * 0.2, Math.min(t.cap > 0 ? t.cap : t.base * 5.0, t.price * Math.exp(r)));
        candle(t, now, 0);
    }

    /** Große Nachrichten (ab 5 %) an Spieler, die den Wert halten; Markt-Nachrichten an alle mit Positionen. */
    private Runnable announce(News n) {
        if (Math.abs(n.pct()) < 5) return null;
        Set<UUID> who = new HashSet<>();
        for (Map<UUID, Map<String, Position>> byPlayer : positions.values()) {
            byPlayer.forEach((u, book) -> {
                if (book.isEmpty()) return;
                if (n.symbol().isEmpty()) who.add(u);
                else for (String id : book.keySet()) {
                    Ticker t = tickers.get(id);
                    if (t != null && t.symbol.equals(n.symbol())) who.add(u);
                }
            });
        }
        if (who.isEmpty()) return null;
        String msg = "<#64D8FF><b>Börse:</b> " + (n.symbol().isEmpty() ? "<white>Ganzer Markt " : "<white>" + n.symbol() + " ")
                + (n.pct() >= 0 ? "<green>" : "<red>") + String.format(Locale.GERMANY, "%+.1f %%", n.pct()) + " <gray>· " + Msg.escape(n.text());
        return () -> {
            for (UUID u : who) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) Msg.send(p, msg);
            }
        };
    }

    /** Letzte Nachrichten (neueste zuerst); symbol != null: nur zu diesem Wert und zum ganzen Markt. */
    public synchronized List<News> news(String symbol, int max) {
        List<News> out = new ArrayList<>();
        for (News n : news) {
            if (out.size() >= max) break;
            if (symbol == null || n.symbol().isEmpty() || n.symbol().equals(symbol)) out.add(n);
        }
        return out;
    }

    private void candle(Ticker t, long now, double vol) {
        for (Frame f : Frame.values()) {
            Deque<Candle> d = t.candles.get(f);
            long bucket = now - now % f.ms;
            Candle last = d.peekLast();
            if (last == null || last.t != bucket) {
                Candle c = new Candle(bucket, last == null ? t.price : last.c);
                c.h = Math.max(c.o, t.price);
                c.l = Math.min(c.o, t.price);
                c.c = t.price;
                d.addLast(c);
                while (d.size() > f.keep) d.pollFirst();
                last = c;
            }
            last.h = Math.max(last.h, t.price);
            last.l = Math.min(last.l, t.price);
            last.c = t.price;
            last.vol += vol;
        }
    }

    /** Shop-Kauf (sign 1) oder Verkauf an den Server (sign -1): Nachfrage und Kurs bewegen sich. */
    public synchronized void demand(String id, int units, int sign) {
        Ticker t = tickers.get(id);
        if (t == null || units <= 0) return;
        if (!playerInfluence()) {
            // Kurs läuft nur simuliert: Umsatz zählen, Kurs nicht bewegen
            candle(t, System.currentTimeMillis(), units);
            return;
        }
        Material m = t.material;
        long target = Math.max(1, plugin.getConfig().getLong("economy.target." + plugin.investments().rarityOf(m).name().toLowerCase(Locale.ROOT), 100));
        double impact = cfg("nachfrage-wirkung", 0.02) * units / (target / 7.0);
        impact = Math.max(-0.08, Math.min(0.08, sign * impact));
        t.factor = Math.max(0.3, Math.min(3.0, t.factor * (1 + impact)));
        t.price = Math.max(t.floor, Math.min(t.cap, t.price * (1 + impact * 0.5)));
        candle(t, System.currentTimeMillis(), units);
        dirty = true;
    }

    /** Verkauf an den Server über das Material (SellService). */
    public synchronized void demandMaterial(Material m, int units, int sign) {
        for (Ticker t : tickers.values()) {
            if (t.material == m) {
                demand(t.id, units, sign);
                return;
            }
        }
    }

    // =================================================================================================================
    // Handeln
    // =================================================================================================================

    private Map<String, Position> book(UUID id, String mode) {
        return positions.computeIfAbsent(mode, k -> new HashMap<>()).computeIfAbsent(id, k -> new LinkedHashMap<>());
    }

    public synchronized Map<String, Position> positions(UUID id, String mode) {
        return new LinkedHashMap<>(book(id, mode));
    }

    public synchronized List<Order> orders(UUID id, String mode) {
        List<Order> out = new ArrayList<>();
        for (Order o : orders) if (o.owner().equals(id) && o.mode().equals(mode)) out.add(o);
        return out;
    }

    public synchronized List<Trade> history(UUID id, String mode) {
        return new ArrayList<>(history.getOrDefault(mode + ":" + id, new ArrayDeque<>()));
    }

    private void log(UUID id, String mode, Trade tr) {
        Deque<Trade> d = history.computeIfAbsent(mode + ":" + id, k -> new ArrayDeque<>());
        d.addFirst(tr);
        while (d.size() > 30) d.pollLast();
    }

    /** Kurs durch eigene Order bewegen (große Beträge mehr). */
    private void impact(Ticker t, double coins, int sign) {
        if (playerInfluence()) {
            double liq = Math.max(1000, cfg("liquiditaet", 2_000_000));
            t.price = Math.max(t.floor, Math.min(t.cap, t.price * (1 + sign * Math.min(0.05, coins / liq))));
        }
        candle(t, System.currentTimeMillis(), coins / Math.max(0.01, t.price));
    }

    /** Für coins kaufen (Market-Order). Gibt null bei Erfolg zurück. */
    public synchronized String buy(Player p, String id, long coins) {
        Ticker t = tickers.get(id);
        if (t == null) return "Diesen Wert gibt es nicht.";
        if (coins < 1) return "Ungültiger Betrag.";
        if (!plugin.economy().withdraw(p.getUniqueId(), coins)) return "Nicht genug $.";
        String mode = plugin.modes().modeOf(p.getUniqueId());
        fill(p.getUniqueId(), mode, t, coins);
        return null;
    }

    private double fill(UUID id, String mode, Ticker t, long coins) {
        double fee = coins * feePercent() / 100.0;
        double qty = (coins - fee) / t.price;
        Position pos = book(id, mode).computeIfAbsent(t.id, k -> new Position());
        pos.avg = (pos.avg * pos.qty + t.price * qty) / (pos.qty + qty);
        pos.qty += qty;
        log(id, mode, new Trade(System.currentTimeMillis(), t.symbol, "Kauf", qty, t.price, coins, 0));
        impact(t, coins, 1);
        dirty = true;
        return qty;
    }

    /** Anteil der Position verkaufen (fraction 0..1). Gibt null bei Erfolg zurück. */
    public synchronized String sell(Player p, String id, double fraction) {
        Ticker t = tickers.get(id);
        if (t == null) return "Diesen Wert gibt es nicht.";
        String mode = plugin.modes().modeOf(p.getUniqueId());
        Position pos = book(p.getUniqueId(), mode).get(id);
        if (pos == null || pos.qty <= 1e-9) return "Davon hast du nichts.";
        double qty = pos.qty * Math.max(0, Math.min(1, fraction));
        long payout = close(p.getUniqueId(), mode, t, qty, "Verkauf");
        plugin.economy().deposit(p.getUniqueId(), payout);
        return null;
    }

    /** Position (teilweise) schließen: Auszahlung nach Gebühr, Gewinn oder Verlust im Verlauf. */
    private long close(UUID id, String mode, Ticker t, double qty, String side) {
        Map<String, Position> b = book(id, mode);
        Position pos = b.get(t.id);
        if (pos == null) return 0;
        qty = Math.min(qty, pos.qty);
        double gross = qty * t.price;
        long payout = Math.round(gross * (1 - feePercent() / 100.0));
        long pnl = Math.round(payout - qty * pos.avg);
        pos.qty -= qty;
        if (pos.qty <= 1e-6) b.remove(t.id);
        log(id, mode, new Trade(System.currentTimeMillis(), t.symbol, side, qty, t.price, payout, pnl));
        impact(t, gross, -1);
        dirty = true;
        return payout;
    }

    /** Order anlegen. LIMIT_BUY reserviert coins, die anderen verkaufen fraction der Position. */
    public synchronized String place(Player p, String id, OrderType type, double trigger, long coins, double fraction) {
        Ticker t = tickers.get(id);
        if (t == null) return "Diesen Wert gibt es nicht.";
        if (trigger <= 0) return "Ungültiger Kurs.";
        UUID uid = p.getUniqueId();
        String mode = plugin.modes().modeOf(uid);
        if (orders(uid, mode).size() >= plugin.getConfig().getInt("trading.max-orders", 10)) return "Du hast schon zu viele offene Orders.";
        double qty = 0;
        if (type == OrderType.LIMIT_BUY) {
            if (coins < 1) return "Ungültiger Betrag.";
            if (!plugin.economy().withdraw(uid, coins)) return "Nicht genug $.";
        } else {
            Position pos = book(uid, mode).get(id);
            if (pos == null || pos.qty <= 1e-9) return "Für diese Order brauchst du eine Position in " + t.symbol + ".";
            qty = pos.qty * Math.max(0.01, Math.min(1, fraction));
            if (type == OrderType.STOP_LOSS && trigger >= t.price) return "Der Stop-Loss muss unter dem Kurs liegen.";
            if ((type == OrderType.TAKE_PROFIT || type == OrderType.LIMIT_SELL) && trigger <= t.price) return "Das Ziel muss über dem Kurs liegen.";
        }
        if (type == OrderType.LIMIT_BUY && trigger >= t.price) return "Der Limit-Kauf muss unter dem Kurs liegen (sonst kauf direkt).";
        orders.add(new Order(nextOrder++, uid, mode, id, type, trigger, qty, coins, System.currentTimeMillis()));
        dirty = true;
        return null;
    }

    public synchronized String cancel(Player p, int orderId) {
        for (Iterator<Order> it = orders.iterator(); it.hasNext(); ) {
            Order o = it.next();
            if (o.id() != orderId || !o.owner().equals(p.getUniqueId())) continue;
            it.remove();
            if (o.type() == OrderType.LIMIT_BUY) plugin.modes().depositIn(o.owner(), o.mode(), o.coins());
            dirty = true;
            return null;
        }
        return "Diese Order gibt es nicht mehr.";
    }

    /** Order ausführen; gibt die Nachricht an den Spieler zurück (nach dem Sperrbereich ausführen). */
    private Runnable execute(Order o, Ticker t) {
        String msg;
        if (o.type() == OrderType.LIMIT_BUY) {
            double qty = fill(o.owner(), o.mode(), t, o.coins());
            msg = "<green>" + o.type().label + " ausgeführt: <white>" + fmt(qty) + " " + t.symbol + " <gray>zu " + money(t.price);
        } else {
            long payout = close(o.owner(), o.mode(), t, o.qty(), o.type().label);
            if (payout <= 0) return null;
            plugin.modes().depositIn(o.owner(), o.mode(), payout);
            msg = (o.type() == OrderType.STOP_LOSS ? "<red>" : "<green>") + o.type().label + " ausgeführt: <white>" + t.symbol
                    + " <gray>zu " + money(t.price) + ", <gold>+" + Msg.coins(payout) + " $";
        }
        return () -> {
            Player p = Bukkit.getPlayer(o.owner());
            if (p != null) {
                Msg.send(p, "<#64D8FF><b>Börse:</b> " + msg);
                Sfx.play(plugin, p, o.type() == OrderType.STOP_LOSS ? Sfx.Kind.ERROR : Sfx.Kind.COIN);
            }
        };
    }

    public static String fmt(double qty) {
        return qty >= 100 ? String.format(Locale.GERMANY, "%,.0f", qty) : String.format(Locale.GERMANY, "%,.2f", qty);
    }

    public static String money(double price) {
        return price >= 1000 ? String.format(Locale.GERMANY, "%,.0f $", price) : String.format(Locale.GERMANY, "%,.2f $", price);
    }

    public long lastTick() {
        return lastTick;
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextOrder = y.getInt("next-order", 1);
        model = y.getInt("model", y.contains("tickers") ? 1 : MODEL);
        for (int k = 0; k < grpDrift.length; k++) {
            String[] g = y.getString("groups." + k, "0;0;0").split(";");
            try {
                grpDrift[k] = Double.parseDouble(g[0]);
                grpLv[k] = Double.parseDouble(g[1]);
                grpUntil[k] = Long.parseLong(g[2]);
            } catch (RuntimeException ignored) {
            }
        }
        for (String n : y.getStringList("news")) {
            String[] p = n.split(";", 4);
            try {
                news.addLast(new News(Long.parseLong(p[0]), p[1], p[3], Double.parseDouble(p[2])));
            } catch (RuntimeException ignored) {
            }
        }
        ConfigurationSection ts = y.getConfigurationSection("tickers");
        if (ts != null) {
            for (String id : ts.getKeys(false)) {
                ConfigurationSection s = ts.getConfigurationSection(id);
                if (s == null) continue;
                Material m = Material.matchMaterial(s.getString("material", "STONE"));
                if (m == null) continue;
                Ticker t = new Ticker(id, s.getString("symbol", id), s.getString("name", nice(m)), m, s.getDouble("base", 1));
                t.price = s.getDouble("price", t.base);
                t.factor = s.getDouble("factor", 1.0);
                String[] st = s.getString("sim", "0;0;0").split(";");
                try {
                    t.drift = Double.parseDouble(st[0]);
                    t.lv = Double.parseDouble(st[1]);
                    t.trendUntil = Long.parseLong(st[2]);
                } catch (RuntimeException ignored) {
                }
                for (Frame f : Frame.values()) {
                    for (String c : s.getStringList("candles." + f.name())) {
                        String[] p = c.split(";");
                        if (p.length < 6) continue;
                        Candle k = new Candle(Long.parseLong(p[0]), Double.parseDouble(p[4]));
                        k.o = Double.parseDouble(p[1]);
                        k.h = Double.parseDouble(p[2]);
                        k.l = Double.parseDouble(p[3]);
                        k.vol = Double.parseDouble(p[5]);
                        t.candles.get(f).addLast(k);
                    }
                }
                tickers.put(id, t);
            }
        }
        ConfigurationSection ps = y.getConfigurationSection("positions");
        if (ps != null) {
            for (String mode : ps.getKeys(false)) {
                ConfigurationSection pm = ps.getConfigurationSection(mode);
                if (pm == null) continue;
                for (String u : pm.getKeys(false)) {
                    ConfigurationSection pu = pm.getConfigurationSection(u);
                    if (pu == null) continue;
                    for (String id : pu.getKeys(false)) {
                        String[] p = pu.getString(id, "0;0").split(";");
                        Position pos = new Position();
                        pos.qty = Double.parseDouble(p[0]);
                        pos.avg = Double.parseDouble(p[1]);
                        book(UUID.fromString(u), mode).put(id, pos);
                    }
                }
            }
        }
        for (String s : y.getStringList("orders")) {
            String[] p = s.split(";");
            if (p.length < 9) continue;
            try {
                orders.add(new Order(Integer.parseInt(p[0]), UUID.fromString(p[1]), p[2], p[3], OrderType.valueOf(p[4]), Double.parseDouble(p[5]),
                        Double.parseDouble(p[6]), Long.parseLong(p[7]), Long.parseLong(p[8])));
            } catch (RuntimeException ignored) {
                // kaputte Order überspringen
            }
        }
        ConfigurationSection hs = y.getConfigurationSection("history");
        if (hs != null) {
            for (String k : hs.getKeys(false)) {
                Deque<Trade> d = new ArrayDeque<>();
                for (String s : hs.getStringList(k)) {
                    String[] p = s.split(";");
                    if (p.length < 7) continue;
                    d.addLast(new Trade(Long.parseLong(p[0]), p[1], p[2], Double.parseDouble(p[3]), Double.parseDouble(p[4]), Long.parseLong(p[5]),
                            Long.parseLong(p[6])));
                }
                history.put(k.replace('|', ':'), d);
            }
        }
    }

    /** Speichern beim Abschalten (sofort). */
    public void save() {
        YamlConfiguration out = snapshot();
        write(out);
    }

    /** Regelmäßiges Speichern: Stand im Hauptthread einsammeln, Schreiben (das Teure) im Hintergrund. */
    public void saveAsync() {
        if (saving) return;
        YamlConfiguration out = snapshot();
        saving = true;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                write(out);
            } finally {
                saving = false;
            }
        });
    }

    private void write(YamlConfiguration out) {
        try {
            plugin.getDataFolder().mkdirs();
            File tmp = new File(file.getParentFile(), file.getName() + "." + System.nanoTime() + ".tmp");
            java.nio.file.Files.writeString(tmp.toPath(), out.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
            try {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("trading.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private synchronized YamlConfiguration snapshot() {
        dirty = false;
        YamlConfiguration out = new YamlConfiguration();
        out.set("next-order", nextOrder);
        out.set("model", model);
        for (int k = 0; k < grpDrift.length; k++) out.set("groups." + k, r4(grpDrift[k]) + ";" + r4(grpLv[k]) + ";" + grpUntil[k]);
        List<String> ns = new ArrayList<>();
        for (News n : news) ns.add(n.t() + ";" + n.symbol() + ";" + r4(n.pct()) + ";" + n.text());
        out.set("news", ns);
        for (Ticker t : tickers.values()) {
            String b = "tickers." + t.id + ".";
            out.set(b + "symbol", t.symbol);
            out.set(b + "name", t.name);
            out.set(b + "material", t.material.name());
            out.set(b + "base", t.base);
            out.set(b + "price", t.price);
            out.set(b + "factor", t.factor);
            out.set(b + "sim", r4(t.drift) + ";" + r4(t.lv) + ";" + t.trendUntil);
            for (Frame f : Frame.values()) {
                List<String> l = new ArrayList<>();
                for (Candle c : t.candles.get(f)) {
                    l.add(c.t + ";" + r4(c.o) + ";" + r4(c.h) + ";" + r4(c.l) + ";" + r4(c.c) + ";" + r4(c.vol));
                }
                out.set(b + "candles." + f.name(), l);
            }
        }
        positions.forEach((mode, byPlayer) -> byPlayer.forEach((u, book) -> book.forEach((id, pos) ->
                out.set("positions." + mode + "." + u + "." + id, r4(pos.qty) + ";" + r4(pos.avg)))));
        List<String> os = new ArrayList<>();
        for (Order o : orders) {
            os.add(o.id() + ";" + o.owner() + ";" + o.mode() + ";" + o.ticker() + ";" + o.type().name() + ";" + o.trigger() + ";" + o.qty() + ";"
                    + o.coins() + ";" + o.created());
        }
        out.set("orders", os);
        history.forEach((k, d) -> {
            List<String> l = new ArrayList<>();
            for (Trade tr : d) l.add(tr.t() + ";" + tr.ticker() + ";" + tr.side() + ";" + r4(tr.qty()) + ";" + r4(tr.price()) + ";" + tr.coins() + ";" + tr.pnl());
            out.set("history." + k.replace(':', '|'), l);
        });
        return out;
    }

    /** Schnell auf 4 Stellen runden (String.format ist bei Zehntausenden Kerzen zu langsam). */
    private static String r4(double v) {
        return Double.toString(Math.round(v * 10_000.0) / 10_000.0);
    }
}

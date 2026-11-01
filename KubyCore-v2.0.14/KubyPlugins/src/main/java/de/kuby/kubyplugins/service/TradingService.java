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
 * (Ticker). Der Kurs läuft live: alle paar Sekunden eine kleine zufällige Schwankung und ein Zug zurück zum fairen
 * Preis (Shop-Grundpreis mal Nachfrage). Die Nachfrage kommt aus dem Shop: Käufe heben sie, Verkäufe an den Server
 * senken sie, mit der Zeit kehrt sie zu 1 zurück. Große Orders bewegen den Kurs (Liquidität). Der Shop verkauft zum
 * Kurs der Börse.
 *
 * Spieler kaufen und verkaufen zum Kurs (Gebühr je Seite), halten Positionen mit Einstandspreis und legen Orders an:
 * Limit-Kauf (Geld wird reserviert), Limit-Verkauf, Stop-Loss und Take-Profit; sie werden bei jedem Kurs-Schritt
 * geprüft, auch wenn der Spieler offline oder in einem anderen Modus ist. Kerzen in drei Zeitrahmen (5 Minuten,
 * 1 Stunde, 1 Tag). Positionen, Orders und Verlauf gelten je Modus (eigenes Geld je Modus). Daten: trading.yml.
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

    public TradingService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "trading.yml");
        load();
        long period = Math.max(5, plugin.getConfig().getLong("trading.tick-sekunden", 20)) * 20L;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 200L, period);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save();
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

    // =================================================================================================================
    // Ticker
    // =================================================================================================================

    /** Ticker für alle Items der Börsen-Bereiche anlegen (neue Shop-Items kommen dazu). */
    public synchronized void ensureTickers() {
        Set<String> used = new HashSet<>();
        for (Ticker t : tickers.values()) used.add(t.symbol);
        for (String sec : EconomyMarketService.DYNAMIC) {
            for (ShopService.Item it : plugin.shop().items(sec)) {
                Ticker t = tickers.get(it.id());
                double base = Math.max(1, it.price());
                if (t != null) {
                    t.base = base;
                    continue;
                }
                String sym = symbol(it.material(), used);
                used.add(sym);
                t = new Ticker(it.id(), sym, nice(it.material()), it.material(), base);
                seed(t);
                tickers.put(it.id(), t);
                dirty = true;
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

    /** Neuer Ticker: Kursverlauf rückwärts mit demselben Modell füllen, damit die Charts nicht leer sind. */
    private void seed(Ticker t) {
        long now = System.currentTimeMillis();
        double sigma = cfg("schwankung", 0.0008);
        for (Frame f : Frame.values()) {
            Deque<Candle> d = t.candles.get(f);
            d.clear();
            double steps = Math.max(1, f.ms / (Math.max(5, cfg("tick-sekunden", 20)) * 1000.0));
            double vol = sigma * Math.sqrt(steps);
            double p = t.base;
            List<Candle> back = new ArrayList<>();
            long start = now - now % f.ms;
            for (int i = 0; i < f.keep; i++) {
                Candle c = new Candle(start - (long) i * f.ms, p);
                double o = p * Math.exp(rnd.nextGaussian() * vol);
                o += (t.base - o) * 0.15;
                c.o = o;
                c.h = Math.max(o, p) * (1 + Math.abs(rnd.nextGaussian()) * vol * 0.6);
                c.l = Math.min(o, p) * (1 - Math.abs(rnd.nextGaussian()) * vol * 0.6);
                c.vol = Math.abs(rnd.nextGaussian()) * 50;
                back.add(0, c);
                p = o;
            }
            d.addAll(back);
        }
        Candle last = t.candles.get(Frame.M5).peekLast();
        t.price = last == null ? t.base : last.c;
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
            lastTick = now;
            double sigma = cfg("schwankung", 0.0008), revert = cfg("rueckkehr", 0.02), decay = cfg("nachfrage-abbau", 0.0005);
            for (Ticker t : tickers.values()) {
                double fair = t.base * t.factor;
                t.price = t.price * Math.exp(rnd.nextGaussian() * sigma) + (fair - t.price) * revert;
                t.price = Math.max(t.base * 0.2, Math.min(t.base * 5.0, t.price));
                t.factor += (1.0 - t.factor) * decay;
                candle(t, now, 0);
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
        Material m = t.material;
        long target = Math.max(1, plugin.getConfig().getLong("economy.target." + plugin.investments().rarityOf(m).name().toLowerCase(Locale.ROOT), 100));
        double impact = cfg("nachfrage-wirkung", 0.02) * units / (target / 7.0);
        impact = Math.max(-0.08, Math.min(0.08, sign * impact));
        t.factor = Math.max(0.3, Math.min(3.0, t.factor * (1 + impact)));
        t.price = Math.max(t.base * 0.2, Math.min(t.base * 5.0, t.price * (1 + impact * 0.5)));
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
        double liq = Math.max(1000, cfg("liquiditaet", 2_000_000));
        t.price = Math.max(t.base * 0.2, Math.min(t.base * 5.0, t.price * (1 + sign * Math.min(0.05, coins / liq))));
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

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        out.set("next-order", nextOrder);
        for (Ticker t : tickers.values()) {
            String b = "tickers." + t.id + ".";
            out.set(b + "symbol", t.symbol);
            out.set(b + "name", t.name);
            out.set(b + "material", t.material.name());
            out.set(b + "base", t.base);
            out.set(b + "price", t.price);
            out.set(b + "factor", t.factor);
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
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("trading.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private static String r4(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }
}

package de.kuby.kubyplugins.cookie;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.cookie.CookieContent.Achievement;
import de.kuby.kubyplugins.cookie.CookieContent.Building;
import de.kuby.kubyplugins.cookie.CookieContent.Kind;
import de.kuby.kubyplugins.cookie.CookieContent.Node;
import de.kuby.kubyplugins.cookie.CookieContent.Req;
import de.kuby.kubyplugins.cookie.CookieContent.Upgrade;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cookie Clicker: Stand je Spieler (gilt in allen Modi, keine Coins, keine XP, keine Items), Produktion jede Sekunde,
 * Klicks mit Combo, Gebäude, Upgrades, Erfolge (+2 % je Erfolg), Glückskeks-Effekte, Keksregen, Backen ohne dich
 * (Ofen), Aufstieg mit Backsternen und Sternen-Baum, Ranglisten. Daten in cookies.yml.
 */
public final class CookieService implements Listener {

    static final int N = CookieContent.BUILDINGS.size();

    /** Stand eines Spielers. */
    public static final class Data {
        public double cookies, baked, allTime, handmade, bestCps;
        public long clicks, golden, goldenMissed, rainCaught;
        public final int[] buildings = new int[N];
        public final Set<String> upgrades = new HashSet<>();
        public final Set<String> achievements = new HashSet<>();
        public final Set<String> tree = new HashSet<>();
        /** Stufe beim letzten Aufstieg (bringt +2 % je Stufe), Backsterne frei und insgesamt. */
        public long level, stars, starsTotal;
        public int ascensions;
        public long lastSeen, runStart;
        public String name = "";
        // Laufzeit
        double cps, clickBase, clickPct;
        double prodMult = 1, clickMult = 1;
        long prodUntil, clickUntil;
        String prodName, clickName;
        long lastClick;
        double combo = 1;
        long nextGolden;
        int sinceCheck;

        public double cps() {
            return cps * (System.currentTimeMillis() < prodUntil ? prodMult : 1);
        }

        public int totalBuildings() {
            int n = 0;
            for (int b : buildings) n += b;
            return n;
        }
    }

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Data> players = new ConcurrentHashMap<>();
    private final Random rnd = new Random();
    private boolean dirty;

    public CookieService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cookies.yml");
        load();
        for (Data d : players.values()) recalc(d);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveIfDirty, 6000L, 6000L);
    }

    public static String fmt(double v) {
        return CookieContent.fmt(v);
    }

    public Data data(UUID id) {
        return players.computeIfAbsent(id, k -> {
            Data d = new Data();
            d.lastSeen = d.runStart = System.currentTimeMillis();
            Player p = Bukkit.getPlayer(id);
            if (p != null) d.name = p.getName();
            recalc(d);
            return d;
        });
    }

    public Map<UUID, Data> all() {
        return players;
    }

    // =================================================================================================================
    // Werte
    // =================================================================================================================

    /** Stufe aus allen je gebackenen Keksen: Kubikwurzel aus (Kekse / 1 Mrd). */
    public static long levelFor(double allTime) {
        return (long) Math.floor(Math.cbrt(Math.max(0, allTime) / 1e9));
    }

    public static double cookiesForLevel(long level) {
        return Math.pow(level, 3) * 1e9;
    }

    public double tierMult(Data d, int b) {
        double m = 1;
        String id = CookieContent.BUILDINGS.get(b).id();
        for (int t = 1; t <= CookieContent.TIER_AT.length; t++) if (d.upgrades.contains("t_" + id + "_" + t)) m *= 2;
        return m;
    }

    /** Faktor auf alles: Rezepte, Erfolge, Stufe, Sternen-Baum. */
    public double globalMult(Data d) {
        double pct = 0;
        for (String u : d.upgrades) {
            Upgrade up = CookieContent.upgrade(u);
            if (up != null && up.kind() == Kind.GLOBAL) pct += up.value();
        }
        double m = (1 + pct / 100.0) * (1 + 0.02 * d.achievements.size()) * (1 + 0.02 * d.level);
        if (d.tree.contains("teig1")) m *= 1.10;
        if (d.tree.contains("teig2")) m *= 1.25;
        if (d.tree.contains("sternenbaeckerei")) m *= 1.50;
        return m;
    }

    /** Kekse pro Sekunde eines Gebäudes (alle Stück, ohne Glückskeks-Effekt). */
    public double buildingCps(Data d, int b) {
        return d.buildings[b] * CookieContent.BUILDINGS.get(b).cps() * tierMult(d, b) * globalMult(d);
    }

    public void recalc(Data d) {
        double base = 0;
        for (int b = 0; b < N; b++) base += d.buildings[b] * CookieContent.BUILDINGS.get(b).cps() * tierMult(d, b);
        d.cps = base * globalMult(d);
        double click = 1, pct = 0;
        for (String u : d.upgrades) {
            Upgrade up = CookieContent.upgrade(u);
            if (up == null) continue;
            if (up.kind() == Kind.CLICK_X) click *= up.value();
            if (up.kind() == Kind.CLICK_CPS) pct += up.value();
        }
        if (d.tree.contains("finger")) click *= 1.25;
        d.clickBase = click;
        d.clickPct = pct / 100.0;
        d.bestCps = Math.max(d.bestCps, d.cps);
    }

    /** Kekse je Klick ohne Combo und ohne Klickfieber. */
    public double clickValue(Data d) {
        return d.clickBase + d.cps() * d.clickPct;
    }

    public double maxCombo(Data d) {
        return d.tree.contains("combo") ? 3 : 2;
    }

    public int ofenLevel(Data d) {
        int l = 0;
        for (String u : d.upgrades) {
            Upgrade up = CookieContent.upgrade(u);
            if (up != null && up.kind() == Kind.OFEN) l = Math.max(l, (int) up.value());
        }
        return l;
    }

    /** Ohne dich: Wirkungsgrad 0..1 und Stunden. */
    public double offlineRate(Data d) {
        return 0.10 + 0.10 * ofenLevel(d) + (d.tree.contains("glut") ? 0.10 : 0);
    }

    public int offlineHours(Data d) {
        return CookieContent.OFEN_HOURS[ofenLevel(d)] + (d.tree.contains("glut") ? 4 : 0);
    }

    int glueckLevel(Data d) {
        int l = 0;
        for (String u : d.upgrades) {
            Upgrade up = CookieContent.upgrade(u);
            if (up != null && up.kind() == Kind.GLUECK) l = Math.max(l, (int) up.value());
        }
        return l;
    }

    /** Abstand bis zum nächsten Glückskeks in ms (4 bis 10 Minuten, mit Glück kürzer). */
    long goldenDelay(Data d) {
        double f = Math.pow(0.85, glueckLevel(d)) * (d.tree.contains("gluecksstern") ? 0.85 : 1);
        double min = plugin.getConfig().getDouble("cookie.glueckskeks.min-minuten", 4), max = plugin.getConfig().getDouble("cookie.glueckskeks.max-minuten", 10);
        return (long) ((min + rnd.nextDouble() * (max - min)) * 60_000 * f);
    }

    double effectLength(Data d) {
        return (1 + 0.1 * glueckLevel(d)) * (d.tree.contains("rausch") ? 1.25 : 1);
    }

    public String title(Data d) {
        return CookieContent.title(Math.max(d.level, levelFor(d.allTime)));
    }

    // =================================================================================================================
    // Klicken
    // =================================================================================================================

    /** Klick auf den großen Keks. Gibt die gebackenen Kekse zurück (0 = zu schnell). */
    public double click(Player p) {
        Data d = data(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (now - d.lastClick < 45) return 0;
        long gap = now - d.lastClick;
        if (gap < 450) d.combo = Math.min(maxCombo(d), d.combo + 0.04);
        else if (gap > 1500) d.combo = 1;
        d.lastClick = now;
        double v = clickValue(d) * d.combo * (now < d.clickUntil ? d.clickMult : 1);
        add(d, v);
        d.handmade += v;
        d.clicks++;
        dirty = true;
        return v;
    }

    public double combo(Data d) {
        return System.currentTimeMillis() - d.lastClick > 1500 ? 1 : d.combo;
    }

    void add(Data d, double v) {
        d.cookies += v;
        d.baked += v;
        d.allTime += v;
    }

    // =================================================================================================================
    // Gebäude
    // =================================================================================================================

    /** Preis für n weitere Stück (jedes 15 % teurer). */
    public double price(Data d, int b, int n) {
        double base = CookieContent.BUILDINGS.get(b).cost() * Math.pow(1.15, d.buildings[b]);
        return base * (Math.pow(1.15, n) - 1) / 0.15;
    }

    public int maxAffordable(Data d, int b) {
        double base = CookieContent.BUILDINGS.get(b).cost() * Math.pow(1.15, d.buildings[b]);
        if (d.cookies < base) return 0;
        return (int) Math.floor(Math.log(d.cookies * 0.15 / base + 1) / Math.log(1.15));
    }

    /** Ist das Gebäude sichtbar? Das erste immer, sonst sobald das vorige gekauft ist. */
    public boolean unlocked(Data d, int b) {
        return b == 0 || d.buildings[b] > 0 || d.buildings[b - 1] > 0;
    }

    /** Kaufen: amount 1, 10, 100 oder -1 (so viele wie möglich). */
    public String buy(Player p, int b, int amount) {
        Data d = data(p.getUniqueId());
        if (b < 0 || b >= N || !unlocked(d, b)) return "Dieses Gebäude gibt es noch nicht.";
        int n = amount < 0 ? maxAffordable(d, b) : amount;
        if (n <= 0) return "Dafür reichen deine Kekse nicht.";
        double price = price(d, b, n);
        if (price > d.cookies) return "Dir fehlen noch " + fmt(price - d.cookies) + " Kekse.";
        d.cookies -= price;
        d.buildings[b] += n;
        recalc(d);
        checkAchievements(p, d);
        dirty = true;
        return null;
    }

    public int affordableBuildings(Data d) {
        int n = 0;
        for (int b = 0; b < N; b++) if (unlocked(d, b) && price(d, b, 1) <= d.cookies) n++;
        return n;
    }

    // =================================================================================================================
    // Upgrades
    // =================================================================================================================

    public boolean reqMet(Data d, Req r) {
        return switch (r.type()) {
            case "building" -> d.buildings[CookieContent.buildingIndex(r.target())] >= r.amount();
            case "clicks" -> d.clicks >= r.amount();
            case "baked" -> d.baked >= r.amount();
            case "golden" -> d.golden >= r.amount();
            default -> false;
        };
    }

    /** Freie Upgrades (Bedingung erfüllt, noch nicht gekauft), nach Preis. */
    public List<Upgrade> available(Data d) {
        List<Upgrade> out = new ArrayList<>();
        for (Upgrade u : CookieContent.UPGRADES) if (!d.upgrades.contains(u.id()) && reqMet(d, u.req())) out.add(u);
        return out;
    }

    /** Noch gesperrte, nicht gekaufte Upgrades. */
    public int locked(Data d) {
        int n = 0;
        for (Upgrade u : CookieContent.UPGRADES) if (!d.upgrades.contains(u.id()) && !reqMet(d, u.req())) n++;
        return n;
    }

    public int affordableUpgrades(Data d) {
        int n = 0;
        for (Upgrade u : available(d)) if (u.cost() <= d.cookies) n++;
        return n;
    }

    public String buyUpgrade(Player p, String id) {
        Data d = data(p.getUniqueId());
        Upgrade u = CookieContent.upgrade(id);
        if (u == null || d.upgrades.contains(id)) return "Dieses Upgrade gibt es nicht (mehr).";
        if (!reqMet(d, u.req())) return "Dieses Upgrade ist noch gesperrt.";
        if (u.cost() > d.cookies) return "Dir fehlen noch " + fmt(u.cost() - d.cookies) + " Kekse.";
        d.cookies -= u.cost();
        d.upgrades.add(id);
        recalc(d);
        checkAchievements(p, d);
        dirty = true;
        return null;
    }

    // =================================================================================================================
    // Erfolge
    // =================================================================================================================

    public boolean reached(Data d, Achievement a) {
        return switch (a.type()) {
            case "alltime" -> d.allTime >= a.amount();
            case "clicks" -> d.clicks >= a.amount();
            case "handmade" -> d.handmade >= a.amount();
            case "building" -> d.buildings[CookieContent.buildingIndex(a.target())] >= a.amount();
            case "buildings" -> d.totalBuildings() >= a.amount();
            case "golden" -> d.golden >= a.amount();
            case "rain" -> d.rainCaught >= a.amount();
            case "cps" -> d.cps >= a.amount();
            case "ascensions" -> d.ascensions >= a.amount();
            case "level" -> d.level >= a.amount();
            default -> false;
        };
    }

    public void checkAchievements(Player p, Data d) {
        boolean any = false;
        for (Achievement a : CookieContent.ACHIEVEMENTS) {
            if (d.achievements.contains(a.id()) || !reached(d, a)) continue;
            d.achievements.add(a.id());
            any = true;
            if (p != null) {
                Msg.send(p, "<#F2C443>🍪 Erfolg: <white>" + Msg.escape(a.name()) + " <gray>– " + Msg.escape(a.desc()) + " <green>+2 % Kekse");
                Sfx.play(plugin, p, Sfx.Kind.CHIME);
            }
        }
        if (any) {
            recalc(d);
            dirty = true;
        }
    }

    // =================================================================================================================
    // Glückskekse und Keksregen
    // =================================================================================================================

    /** Glückskeks gefangen: zufälliger Effekt. Gibt die Meldung zurück. */
    public String catchGolden(Player p) {
        Data d = data(p.getUniqueId());
        d.golden++;
        long now = System.currentTimeMillis();
        double len = effectLength(d);
        int r = rnd.nextInt(100);
        String msg;
        if (r < 45) {
            double gain = Math.min(d.cookies * 0.15, d.cps() * 900) + 13;
            if (d.tree.contains("goldader")) gain *= 2;
            add(d, gain);
            msg = "<gold><b>Keksschatz!</b> <white>+" + fmt(gain) + " Kekse";
        } else if (r < 80) {
            d.prodMult = 5;
            d.prodUntil = now + (long) (60_000 * len);
            d.prodName = "Backrausch";
            msg = "<gold><b>Backrausch!</b> <white>Kekse pro Sekunde x5 für " + Math.round(60 * len) + " s";
        } else if (r < 90) {
            d.clickMult = 10;
            d.clickUntil = now + (long) (15_000 * len);
            d.clickName = "Klickfieber";
            msg = "<gold><b>Klickfieber!</b> <white>Klicks x10 für " + Math.round(15 * len) + " s";
        } else {
            d.prodMult = 2;
            d.prodUntil = now + (long) (180_000 * len);
            d.prodName = "Zuckerschub";
            msg = "<gold><b>Zuckerschub!</b> <white>Kekse pro Sekunde x2 für " + Math.round(180 * len) + " s";
        }
        checkAchievements(p, d);
        dirty = true;
        return msg;
    }

    /** Keks aus dem Keksregen gefangen: 30 Sekunden Produktion (mindestens 25). */
    public double catchRain(Player p) {
        Data d = data(p.getUniqueId());
        double v = Math.max(25, d.cps() * 30) * (d.tree.contains("magnet") ? 2 : 1);
        add(d, v);
        d.rainCaught++;
        checkAchievements(p, d);
        dirty = true;
        return v;
    }

    // =================================================================================================================
    // Aufstieg und Sternen-Baum
    // =================================================================================================================

    /** Backsterne, die ein Aufstieg jetzt bringen würde. */
    public long starsOnAscend(Data d) {
        return Math.max(0, levelFor(d.allTime) - d.level);
    }

    public String ascend(Player p) {
        Data d = data(p.getUniqueId());
        long gain = starsOnAscend(d);
        if (gain <= 0) return "Ein Aufstieg bringt noch keinen Backstern. Nächste Stufe bei " + fmt(cookiesForLevel(d.level + 1)) + " Keksen insgesamt.";
        d.level += gain;
        d.stars += gain;
        d.starsTotal += gain;
        d.ascensions++;
        d.cookies = d.tree.contains("start2") ? 1_000_000 : d.tree.contains("start1") ? 1_000 : 0;
        d.baked = 0;
        java.util.Arrays.fill(d.buildings, 0);
        d.upgrades.clear();
        d.prodUntil = d.clickUntil = 0;
        d.runStart = System.currentTimeMillis();
        recalc(d);
        checkAchievements(p, d);
        dirty = true;
        Msg.send(p, "<gold><b>Aufstieg!</b> <white>Stufe " + fmt(d.level) + " <gray>(+" + fmt(gain) + " Backsterne, jetzt +" + (2 * d.level)
                + " % Kekse für immer). <white>Titel: " + Msg.escape(title(d)));
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS_BIG);
        return null;
    }

    public boolean nodeAvailable(Data d, int i) {
        return i == 0 || d.tree.contains(CookieContent.TREE.get(i - 1).id());
    }

    /** Kann der Spieler gerade einen Stern im Baum kaufen? */
    public boolean nodeAffordable(Data d) {
        for (int i = 0; i < CookieContent.TREE.size(); i++) {
            Node n = CookieContent.TREE.get(i);
            if (!d.tree.contains(n.id()) && nodeAvailable(d, i) && d.stars >= n.cost()) return true;
        }
        return false;
    }

    public String buyNode(Player p, String id) {
        Data d = data(p.getUniqueId());
        int i = -1;
        for (int k = 0; k < CookieContent.TREE.size(); k++) if (CookieContent.TREE.get(k).id().equals(id)) i = k;
        if (i < 0 || d.tree.contains(id)) return "Diesen Stern hast du schon.";
        if (!nodeAvailable(d, i)) return "Erst den Stern davor kaufen.";
        Node n = CookieContent.TREE.get(i);
        if (d.stars < n.cost()) return "Dir fehlen noch " + (n.cost() - d.stars) + " Backsterne.";
        d.stars -= n.cost();
        d.tree.add(id);
        recalc(d);
        dirty = true;
        Sfx.play(plugin, p, Sfx.Kind.LEVEL_UP);
        return null;
    }

    // =================================================================================================================
    // Jede Sekunde, Ankommen und Gehen
    // =================================================================================================================

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Data d = players.get(p.getUniqueId());
            if (d == null) continue;
            if (now > d.prodUntil && d.prodName != null) {
                d.prodName = null;
                d.prodMult = 1;
            }
            if (now > d.clickUntil && d.clickName != null) {
                d.clickName = null;
                d.clickMult = 1;
            }
            double v = d.cps();
            if (v > 0) {
                add(d, v);
                dirty = true;
            }
            d.lastSeen = now;
            if (++d.sinceCheck >= 5) {
                d.sinceCheck = 0;
                checkAchievements(p, d);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Data d = players.get(p.getUniqueId());
        if (d == null) return;
        d.name = p.getName();
        long away = System.currentTimeMillis() - d.lastSeen;
        if (away < 60_000 || d.cps <= 0) return;
        double hours = Math.min(away / 3_600_000.0, offlineHours(d));
        double gain = d.cps * hours * 3600 * offlineRate(d);
        if (gain < 1) return;
        add(d, gain);
        d.lastSeen = System.currentTimeMillis();
        dirty = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) Msg.send(p, "<#F2C443>🍪 Deine Bäckerei hat ohne dich <white>" + fmt(gain) + " Kekse <#F2C443>gebacken. <gray>/cookie");
        }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Data d = players.get(e.getPlayer().getUniqueId());
        if (d != null) d.lastSeen = System.currentTimeMillis();
    }

    // =================================================================================================================
    // Ranglisten
    // =================================================================================================================

    public enum Board {
        ALLTIME("Kekse aller Zeiten"), CPS("Kekse pro Sekunde"), CLICKS("Klicks"), LEVEL("Stufe");

        public final String label;

        Board(String label) {
            this.label = label;
        }
    }

    public double value(Data d, Board b) {
        return switch (b) {
            case ALLTIME -> d.allTime;
            case CPS -> d.cps;
            case CLICKS -> d.clicks;
            case LEVEL -> Math.max(d.level, levelFor(d.allTime));
        };
    }

    public List<Map.Entry<UUID, Data>> top(Board b, int n) {
        List<Map.Entry<UUID, Data>> list = new ArrayList<>(players.entrySet());
        list.removeIf(e -> value(e.getValue(), b) <= 0);
        list.sort((x, y) -> Double.compare(value(y.getValue(), b), value(x.getValue(), b)));
        return n <= 0 ? list : list.subList(0, Math.min(n, list.size()));
    }

    public int rank(UUID id, Board b) {
        List<Map.Entry<UUID, Data>> list = top(b, 0);
        for (int i = 0; i < list.size(); i++) if (list.get(i).getKey().equals(id)) return i + 1;
        return 0;
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ps = y.getConfigurationSection("players");
        if (ps == null) return;
        for (String k : ps.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(k);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection s = ps.getConfigurationSection(k);
            if (s == null) continue;
            Data d = new Data();
            d.name = s.getString("name", "");
            d.cookies = s.getDouble("cookies");
            d.baked = s.getDouble("baked");
            d.allTime = s.getDouble("all-time");
            d.handmade = s.getDouble("handmade");
            d.bestCps = s.getDouble("best-cps");
            d.clicks = s.getLong("clicks");
            d.golden = s.getLong("golden");
            d.goldenMissed = s.getLong("golden-missed");
            d.rainCaught = s.getLong("rain");
            d.level = s.getLong("level");
            d.stars = s.getLong("stars");
            d.starsTotal = s.getLong("stars-total");
            d.ascensions = s.getInt("ascensions");
            d.lastSeen = s.getLong("last-seen", System.currentTimeMillis());
            d.runStart = s.getLong("run-start", d.lastSeen);
            ConfigurationSection bs = s.getConfigurationSection("buildings");
            if (bs != null) for (String b : bs.getKeys(false)) {
                int i = CookieContent.buildingIndex(b);
                if (i >= 0) d.buildings[i] = bs.getInt(b);
            }
            d.upgrades.addAll(s.getStringList("upgrades"));
            d.achievements.addAll(s.getStringList("achievements"));
            d.tree.addAll(s.getStringList("tree"));
            players.put(id, d);
        }
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Data> e : players.entrySet()) {
            Data d = e.getValue();
            if (d.allTime <= 0 && d.clicks == 0) continue;
            String b = "players." + e.getKey() + ".";
            y.set(b + "name", d.name);
            y.set(b + "cookies", d.cookies);
            y.set(b + "baked", d.baked);
            y.set(b + "all-time", d.allTime);
            y.set(b + "handmade", d.handmade);
            y.set(b + "best-cps", d.bestCps);
            y.set(b + "clicks", d.clicks);
            y.set(b + "golden", d.golden);
            y.set(b + "golden-missed", d.goldenMissed);
            y.set(b + "rain", d.rainCaught);
            y.set(b + "level", d.level);
            y.set(b + "stars", d.stars);
            y.set(b + "stars-total", d.starsTotal);
            y.set(b + "ascensions", d.ascensions);
            y.set(b + "last-seen", d.lastSeen);
            y.set(b + "run-start", d.runStart);
            for (int i = 0; i < N; i++) if (d.buildings[i] > 0) y.set(b + "buildings." + CookieContent.BUILDINGS.get(i).id(), d.buildings[i]);
            y.set(b + "upgrades", new ArrayList<>(d.upgrades));
            y.set(b + "achievements", new ArrayList<>(d.achievements));
            y.set(b + "tree", new ArrayList<>(d.tree));
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("cookies.yml: " + ex.getMessage());
        }
    }
}

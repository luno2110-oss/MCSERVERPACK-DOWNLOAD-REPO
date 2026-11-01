package de.kuby.kubysurvival.quests;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Events mit Kapiteln als Presets je Feiertag (plugins/KubySurvival/events/<id>.yml: Halloween, Weihnachten, Silvester,
 * Valentinstag, Ostern, Sommerfest). Jedes Preset bringt alles mit: Zeitfenster (fest oder relativ zu Ostern), Kapitel
 * mit Zielen und Belohnungen, Sammel-Items am Survival-Spawn (Event-Währung), Shop, Titel, Grafik (Pack) und
 * Spezial-Aktionen (Sammel-Regen, Feuerwerk). Läuft jedes Jahr automatisch oder von Hand geladen (/kp event laden).
 * Kapitel der Reihe nach annehmen, Ziele zählen (aus QuestService.progress und EventListener), abholen: Coins,
 * Event-Währung und Tokens (wie Quest-Tokens). Spielerdaten je Event und Jahr in events-data.yml.
 */
public final class EventService {

    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    /** Mitgelieferte Presets (resources/events/<id>.yml) in der Reihenfolge des Jahres. */
    public static final List<String> PRESETS = List.of("silvester", "valentinstag", "ostern", "sommer", "halloween", "weihnachten");

    public record Goal(String type, String arg, long amount, String cond, String text) {}

    public record Chapter(int index, String name, String text, long coins, long candy, long tokens, String from, List<Goal> goals) {}

    /** Angebot im Candy-Shop: Art (TITLE, COSMETIC, ITEM, KEY, COINS, TOKENS), Wert, Menge, Name, Preis. */
    public record Offer(String kind, String value, int amount, String name, long price) {
        /** Einmalige Angebote (Titel, Kosmetik) zeigen „gekauft“. */
        public boolean once() {
            return kind.equals("TITLE") || kind.equals("COSMETIC");
        }
    }

    /**
     * Sammel-Items am Spawn (Event-Währung): Item-Modell kuby:event/<item>, Material ohne Pack, Name (MiniMessage); dazu
     * die seltene Sorte, die mehr wert ist und leuchtet.
     */
    public record Collect(String item, Material material, String name, String rareItem, Material rareMaterial, String rareName,
                          int radius, int max, int seconds, double rareChance, int rareValue) {}

    /** Spezial-Aktionen: Sammel-Regen alle n Minuten (Menge), Feuerwerk am Spawn („MM-TT HH:mm“ oder täglich „HH:mm“). */
    public record Special(int rainMinutes, int rainAmount, String fireworks, int fireworkSeconds) {}

    /** Event (Preset): Farbe als #RRGGBB, Grafik = Name der Bilder im Pack (QuestScreen.Theme). */
    public record Event(String id, String name, String theme, String color, boolean automatic, String start, String end,
                        String currency, String shopName, Collect collect, Special special, List<Chapter> chapters,
                        List<Offer> shop, Map<String, String> titles, File file) {
        /** MiniMessage-Farbe, z. B. „<#FC981F>“. */
        public String tag() {
            return "<" + color + ">";
        }
    }

    public enum State { LOCKED, OPEN, ACCEPTED, DONE, CLAIMED }

    static final class Data {
        int accepted = -1;
        final Set<Integer> claimed = new TreeSet<>();
        final Map<String, Long> progress = new HashMap<>();
        long candy;
    }

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File dir, dataFile;
    private final Map<String, Event> events = new LinkedHashMap<>();
    /** Spielerdaten je Event-Instanz (id-Jahr). */
    private final Map<String, Map<UUID, Data>> data = new HashMap<>();
    /** Von Hand gestartet (null = nach Datum); off = von Hand beendet. */
    private String forced;
    private boolean off;
    private boolean dirty;
    final NamespacedKey candyKey;
    final NamespacedKey fireworkKey;
    private final Set<UUID> candyItems = new HashSet<>();
    private long lastCandy, lastRain;

    public EventService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.dir = new File(addon.getDataFolder(), "events");
        this.dataFile = new File(addon.getDataFolder(), "events-data.yml");
        this.candyKey = new NamespacedKey(addon, "candy");
        this.fireworkKey = new NamespacedKey(addon, "event_firework");
        migrate();
        installPresets();
        loadDefs();
        loadData();
    }

    /** Alte events.yml (ein Event je Abschnitt, vor den Presets) beiseitelegen: die Presets ersetzen sie. */
    private void migrate() {
        File old = new File(addon.getDataFolder(), "events.yml");
        if (!old.exists()) return;
        File to = new File(addon.getDataFolder(), "events.yml.alt");
        if (old.renameTo(to)) addon.getLogger().info("Events liegen jetzt als Presets in events/ (alte Datei: events.yml.alt)");
    }

    /** Fehlende Presets aus dem Plugin in den Ordner events/ schreiben (vorhandene bleiben, wie sie sind). */
    private void installPresets() {
        if (!dir.exists() && !dir.mkdirs()) return;
        for (String id : PRESETS) {
            if (!new File(dir, id + ".yml").exists()) addon.saveResource("events/" + id + ".yml", false);
        }
    }

    // =================================================================================================================
    // Laden und Speichern
    // =================================================================================================================

    public synchronized void loadDefs() {
        events.clear();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files, (a, b) -> {
            int ia = PRESETS.indexOf(a.getName().replace(".yml", "")), ib = PRESETS.indexOf(b.getName().replace(".yml", ""));
            return ia != ib ? Integer.compare(ia < 0 ? 99 : ia, ib < 0 ? 99 : ib) : a.getName().compareTo(b.getName());
        });
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
            try {
                events.put(id, parse(id, YamlConfiguration.loadConfiguration(f), f));
            } catch (RuntimeException ex) {
                addon.getLogger().warning("events/" + f.getName() + ": " + ex.getMessage());
            }
        }
        ensureTitles();
    }

    private static Event parse(String id, ConfigurationSection e, File file) {
        List<Chapter> chapters = new ArrayList<>();
        int i = 0;
        for (Map<?, ?> m : e.getMapList("kapitel")) {
            List<Goal> goals = new ArrayList<>();
            Object zl = m.get("ziele");
            if (zl instanceof List<?> list) {
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> g)) continue;
                    String type = str(g.get("type"), "").toLowerCase(Locale.ROOT);
                    String arg = str(g.get("arg"), null);
                    if (type.equals("candy") || type.equals("sammeln")) {
                        type = "collect";
                        if (arg != null && (arg.equalsIgnoreCase("lila") || arg.equalsIgnoreCase("selten"))) arg = "rare";
                    }
                    goals.add(new Goal(type, arg, num(g.get("menge"), 1), str(g.get("bedingung"), null), str(g.get("text"), "")));
                }
            }
            Object reward = m.containsKey("waehrung") ? m.get("waehrung") : m.get("candy");
            chapters.add(new Chapter(i++, str(m.get("name"), "Kapitel " + i), str(m.get("text"), ""), num(m.get("coins"), 0),
                    num(reward, 0), num(m.get("tokens"), 0), str(m.get("ab"), null), goals));
        }
        List<Offer> shop = new ArrayList<>();
        for (String line : e.getStringList("shop")) {
            String[] p = line.split(";");
            if (p.length < 5) continue;
            try {
                shop.add(new Offer(p[0].trim().toUpperCase(Locale.ROOT), p[1].trim(), Integer.parseInt(p[2].trim()), p[3].trim(),
                        Long.parseLong(p[4].trim())));
            } catch (NumberFormatException ignored) {
                // Zeile überspringen
            }
        }
        Map<String, String> titles = new LinkedHashMap<>();
        ConfigurationSection t = e.getConfigurationSection("titel");
        if (t != null) for (String k : t.getKeys(false)) titles.put(k, t.getString(k, k));
        String currency = e.getString("waehrung", "Candy Corn");
        Collect collect = null;
        ConfigurationSection c = e.getConfigurationSection("sammeln");
        if (c != null) {
            String item = c.getString("item", id);
            collect = new Collect(item, mat(c.getString("material"), Material.SUNFLOWER), c.getString("name", currency),
                    c.getString("selten-item", item + "_selten"), mat(c.getString("selten-material"), Material.GOLD_NUGGET),
                    c.getString("selten-name", "Seltene " + currency), c.getInt("radius", 60), c.getInt("max", 30),
                    Math.max(5, c.getInt("alle-sekunden", 20)), c.getDouble("selten-chance", 0.08), Math.max(1, c.getInt("selten-wert", 5)));
        } else if ((c = e.getConfigurationSection("candy")) != null) {
            // Halloween vor den Presets
            collect = new Collect("candy_corn", Material.ORANGE_DYE, "<#FC981F>Candy Corn", "candy_corn_purple", Material.PURPLE_DYE,
                    "<light_purple>Lila Candy Corn", c.getInt("radius", 60), c.getInt("max", 30), Math.max(5, c.getInt("alle-sekunden", 20)),
                    c.getDouble("lila-chance", 0.08), Math.max(1, c.getInt("lila-wert", 5)));
        }
        ConfigurationSection sp = e.getConfigurationSection("spezial");
        Special special = sp == null ? null : new Special(sp.getInt("regen-minuten", 0), sp.getInt("regen-menge", 25),
                sp.getString("feuerwerk"), sp.getInt("feuerwerk-sekunden", 120));
        return new Event(id, e.getString("name", id), e.getString("grafik", id), e.getString("farbe", "#FC981F"),
                e.getBoolean("automatisch", true), e.getString("start", "01-01 00:00"), e.getString("ende", "01-02 00:00"), currency,
                e.getString("shop-name", currency + "-Shop"), collect, special, chapters, shop, titles, file);
    }

    private static Material mat(String name, Material def) {
        Material m = name == null ? null : Material.matchMaterial(name);
        return m != null && m.isItem() ? m : def;
    }

    /** Titel aus den Events in die Kern-Config (tags) übernehmen, wenn sie dort fehlen (nur im Speicher). */
    private void ensureTitles() {
        List<String> tags = new ArrayList<>(core.getConfig().getStringList("tags"));
        Set<String> ids = new HashSet<>();
        for (String s : tags) ids.add(s.split(";", 2)[0].trim());
        boolean add = false;
        for (Event e : events.values()) {
            for (Map.Entry<String, String> t : e.titles().entrySet()) {
                if (ids.add(t.getKey())) {
                    tags.add(t.getKey() + ";" + t.getValue());
                    add = true;
                }
            }
        }
        if (add) core.getConfig().set("tags", tags);
    }

    private static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static long num(Object o, long def) {
        if (o instanceof Number n) return n.longValue();
        try {
            return o == null ? def : Long.parseLong(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private synchronized void loadData() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile);
        forced = yml.getString("forced");
        off = yml.getBoolean("off", false);
        ConfigurationSection root = yml.getConfigurationSection("data");
        if (root == null) return;
        for (String inst : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(inst);
            if (s == null) continue;
            Map<UUID, Data> map = new HashMap<>();
            for (String key : s.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    Data d = new Data();
                    d.accepted = s.getInt(key + ".accepted", -1);
                    d.claimed.addAll(s.getIntegerList(key + ".claimed"));
                    d.candy = s.getLong(key + ".candy");
                    ConfigurationSection pr = s.getConfigurationSection(key + ".progress");
                    if (pr != null) for (String k : pr.getKeys(false)) d.progress.put(k.replace('_', ':'), pr.getLong(k));
                    map.put(id, d);
                } catch (IllegalArgumentException ignored) {
                    // kein Spieler
                }
            }
            data.put(inst, map);
        }
    }

    public synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("forced", forced);
        yml.set("off", off);
        for (Map.Entry<String, Map<UUID, Data>> inst : data.entrySet()) {
            for (Map.Entry<UUID, Data> e : inst.getValue().entrySet()) {
                String base = "data." + inst.getKey() + "." + e.getKey();
                Data d = e.getValue();
                yml.set(base + ".accepted", d.accepted);
                yml.set(base + ".claimed", new ArrayList<>(d.claimed));
                yml.set(base + ".candy", d.candy);
                for (Map.Entry<String, Long> p : d.progress.entrySet()) yml.set(base + ".progress." + p.getKey().replace(':', '_'), p.getValue());
            }
        }
        try {
            yml.save(dataFile);
            dirty = false;
        } catch (IOException ex) {
            addon.getLogger().warning("events-data.yml: " + ex.getMessage());
        }
    }

    // =================================================================================================================
    // Zeitfenster
    // =================================================================================================================

    private static final Pattern EASTER = Pattern.compile("ostern\\s*([+-]\\s*\\d+)?(?:\\s+(\\d{1,2}):(\\d{2}))?");

    /** Ostersonntag (gregorianisch, Gauß/Meeus). */
    static LocalDate easter(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        return LocalDate.of(y, (h + l - 7 * m + 114) / 31, (h + l - 7 * m + 114) % 31 + 1);
    }

    /** „MM-TT HH:mm“ oder „ostern±Tage HH:mm“ im Jahr year. */
    static ZonedDateTime at(String spec, int year) {
        Matcher em = EASTER.matcher(spec.trim().toLowerCase(Locale.ROOT));
        if (em.matches()) {
            int off = em.group(1) == null ? 0 : Integer.parseInt(em.group(1).replace(" ", ""));
            int h = em.group(2) == null ? 0 : Integer.parseInt(em.group(2)), mi = em.group(3) == null ? 0 : Integer.parseInt(em.group(3));
            return ZonedDateTime.of(easter(year).plusDays(off), LocalTime.of(h, mi), ZONE);
        }
        String mmddhhmm = spec;
        String[] p = mmddhhmm.trim().split("[- :]");
        int mo = Integer.parseInt(p[0]), d = Integer.parseInt(p[1]);
        int h = p.length > 2 ? Integer.parseInt(p[2]) : 0, mi = p.length > 3 ? Integer.parseInt(p[3]) : 0;
        return ZonedDateTime.of(year, mo, d, h, mi, 0, 0, ZONE);
    }

    /** Laufendes Zeitfenster [start, ende) des Events, sonst null. */
    static ZonedDateTime[] window(Event e, ZonedDateTime now) {
        try {
            for (int y = now.getYear() - 1; y <= now.getYear(); y++) {
                ZonedDateTime s = at(e.start(), y), en = at(e.end(), y);
                if (!en.isAfter(s)) en = en.plusYears(1);
                if (!now.isBefore(s) && now.isBefore(en)) return new ZonedDateTime[]{s, en};
            }
        } catch (RuntimeException ignored) {
            // falsches Datum im Preset
        }
        return null;
    }

    /** Das gerade laufende Event (null = keins). */
    public synchronized Event active() {
        if (off) return null;
        if (forced != null) return events.get(forced);
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        for (Event e : events.values()) if (e.automatic() && window(e, now) != null) return e;
        return null;
    }

    /** Start und Ende des laufenden Events; von Hand gestartet: das nächste Ende nach jetzt. */
    synchronized ZonedDateTime[] span(Event e) {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime[] w = window(e, now);
        if (w != null) return w;
        ZonedDateTime en = at(e.end(), now.getYear());
        if (!en.isAfter(now)) en = en.plusYears(1);
        ZonedDateTime s = at(e.start(), en.getYear());
        if (!s.isBefore(en)) s = s.minusYears(1);
        return new ZonedDateTime[]{s, en};
    }

    private String instance(Event e) {
        return e.id() + "-" + span(e)[0].getYear();
    }

    /** „endet 1.11. um 04:00 Uhr“ */
    public synchronized String endText(Event e) {
        ZonedDateTime en = span(e)[1];
        return "endet " + en.getDayOfMonth() + "." + en.getMonthValue() + ". um " + String.format("%02d:%02d", en.getHour(), en.getMinute()) + " Uhr";
    }

    private LocalDate fromDate(Event e, Chapter c) {
        if (c.from() == null) return null;
        ZonedDateTime s = span(e)[0];
        LocalDate d = at(c.from() + " 00:00", s.getYear()).toLocalDate();
        if (d.isBefore(s.toLocalDate())) d = d.plusYears(1);
        return d;
    }

    // =================================================================================================================
    // Spielerstand
    // =================================================================================================================

    private Data data(Event e, UUID id) {
        return data.computeIfAbsent(instance(e), k -> new HashMap<>()).computeIfAbsent(id, k -> new Data());
    }

    public synchronized State state(UUID id, int i) {
        Event e = active();
        if (e == null || i < 0 || i >= e.chapters().size()) return State.LOCKED;
        Data d = data(e, id);
        if (d.claimed.contains(i)) return State.CLAIMED;
        if (d.accepted == i) return goalsDone(e, d, i) >= e.chapters().get(i).goals().size() ? State.DONE : State.ACCEPTED;
        LocalDate from = fromDate(e, e.chapters().get(i));
        if (from != null && LocalDate.now(ZONE).isBefore(from)) return State.LOCKED;
        if (i > 0 && !d.claimed.contains(i - 1)) return State.LOCKED;
        return d.accepted < 0 ? State.OPEN : State.LOCKED;
    }

    /** Warum ein Kapitel gesperrt ist: „Ab Di. 27.10.“ oder „Erst Kapitel 3“. */
    public synchronized String lockText(UUID id, int i) {
        Event e = active();
        if (e == null) return "";
        LocalDate from = fromDate(e, e.chapters().get(i));
        if (from != null && LocalDate.now(ZONE).isBefore(from)) {
            String wd = from.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.GERMAN);
            return "Ab " + Character.toUpperCase(wd.charAt(0)) + wd.substring(1).replace(".", "") + ". " + from.getDayOfMonth() + "."
                    + from.getMonthValue() + ".";
        }
        Data d = data(e, id);
        if (d.accepted >= 0 && d.accepted != i) return "Erst Kapitel " + (d.accepted + 1) + " abschließen";
        return "Erst Kapitel " + i;
    }

    private static int goalsDone(Event e, Data d, int i) {
        int n = 0;
        List<Goal> goals = e.chapters().get(i).goals();
        for (int g = 0; g < goals.size(); g++) if (d.progress.getOrDefault(i + ":" + g, 0L) >= goals.get(g).amount()) n++;
        return n;
    }

    public synchronized int goalsDone(UUID id, int i) {
        Event e = active();
        return e == null ? 0 : goalsDone(e, data(e, id), i);
    }

    public synchronized long goalProgress(UUID id, int i, int g) {
        Event e = active();
        return e == null ? 0 : Math.min(e.chapters().get(i).goals().get(g).amount(), data(e, id).progress.getOrDefault(i + ":" + g, 0L));
    }

    public synchronized int claimedCount(UUID id) {
        Event e = active();
        return e == null ? 0 : data(e, id).claimed.size();
    }

    public synchronized int accepted(UUID id) {
        Event e = active();
        return e == null ? -1 : data(e, id).accepted;
    }

    public synchronized long candy(UUID id) {
        Event e = active();
        return e == null ? 0 : data(e, id).candy;
    }

    /** Nächstes Kapitel zum Anzeigen (angenommen oder als nächstes offen), -1 = alle abgeholt. */
    public synchronized int current(UUID id) {
        Event e = active();
        if (e == null) return -1;
        Data d = data(e, id);
        if (d.accepted >= 0) return d.accepted;
        for (int i = 0; i < e.chapters().size(); i++) if (!d.claimed.contains(i)) return i;
        return -1;
    }

    // ---- Annehmen und Abholen ----

    public synchronized String accept(Player p, int i) {
        Event e = active();
        if (e == null) return "Gerade läuft kein Event.";
        if (state(p.getUniqueId(), i) != State.OPEN) return "Dieses Kapitel kannst du gerade nicht annehmen.";
        data(e, p.getUniqueId()).accepted = i;
        dirty = true;
        addon.quests().setTracked(p.getUniqueId(), "e:" + i);
        Msg.send(p, e.tag() + "<b>" + Msg.escape(e.name()) + ":</b> <white>" + Msg.escape(e.chapters().get(i).name())
                + " <gray>angenommen. Es wird oben angezeigt.");
        p.playSound(p.getLocation(), sound(e), 0.6f, 1.2f);
        return null;
    }

    public synchronized String claim(Player p, int i) {
        Event e = active();
        if (e == null) return "Gerade läuft kein Event.";
        UUID id = p.getUniqueId();
        if (state(id, i) != State.DONE) return "Dieses Kapitel ist noch nicht geschafft.";
        Data d = data(e, id);
        Chapter c = e.chapters().get(i);
        d.claimed.add(i);
        d.accepted = -1;
        d.candy += c.candy();
        dirty = true;
        addon.quests().reward(id, c.coins(), c.tokens());
        if (("e:" + i).equals(addon.quests().trackedKey(id))) addon.quests().setTracked(id, null);
        Msg.send(p, e.tag() + "<b>Kapitel " + (i + 1) + " geschafft!</b> <gold>+" + Msg.coins(c.coins()) + " Coins " + e.tag() + "+"
                + Msg.coins(c.candy()) + " " + Msg.escape(e.currency()) + " <light_purple>+" + c.tokens() + " Tokens");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
        if (i + 1 < e.chapters().size() && state(id, i + 1) == State.OPEN) {
            Msg.send(p, "<gray>Kapitel " + (i + 2) + " wartet: <click:run_command:'/quest event'>" + e.tag() + "[Ansehen]</click>");
        }
        return null;
    }

    // ---- Ziele zählen ----

    /** Meldet eine Aktion (Typen wie in events.yml). at = Ort für Bedingungen (Grundstück, Nacht, Nether). */
    public void progress(Player p, String type, Object subject, long amount, Location at) {
        if (amount <= 0 || !ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) return;
        String done = null;
        boolean finished = false;
        Event e;
        synchronized (this) {
            e = active();
            if (e == null) return;
            Data d = data(e, p.getUniqueId());
            if (d.accepted < 0 || d.accepted >= e.chapters().size()) return;
            int i = d.accepted;
            List<Goal> goals = e.chapters().get(i).goals();
            int before = goalsDone(e, d, i);
            for (int g = 0; g < goals.size(); g++) {
                Goal goal = goals.get(g);
                if (!goal.type().equals(type) || !argMatches(goal, type, subject) || !condition(goal.cond(), p, at)) continue;
                String key = i + ":" + g;
                long cur = d.progress.getOrDefault(key, 0L);
                if (cur >= goal.amount()) continue;
                long v = Math.min(goal.amount(), cur + amount);
                d.progress.put(key, v);
                dirty = true;
                if (v >= goal.amount()) done = goal.text();
            }
            finished = before < goals.size() && goalsDone(e, d, i) >= goals.size();
        }
        if (done != null) {
            Msg.send(p, e.tag() + "✔ <white>" + Msg.escape(done));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        }
        if (finished) {
            Msg.send(p, e.tag() + "<b>Kapitel geschafft!</b> <click:run_command:'/quest event'><gold>[Belohnung abholen]</click>");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.3f);
        }
    }

    private static boolean argMatches(Goal g, String type, Object subject) {
        String arg = g.arg();
        switch (type) {
            case "collect":
                return arg == null ? "any".equals(subject) : "rare".equals(subject) && arg.equals("rare");
            case "place", "pickup", "throw":
                return arg == null || (subject instanceof Material m && materialMatches(arg, m));
            case "break":
                if (arg != null && subject instanceof Material m && (arg.startsWith("#") || isGroup(arg))) return materialMatches(arg, m);
                return QuestService.matches(new QuestService.Def("", "", "", type, arg, g.amount(), 0, 0, 0), type, subject);
            case "snowball", "build", "breed", "tame":
                return arg == null || (subject instanceof Entity en && entityMatches(arg, en));
            case "carve", "brew", "fish", "treasure", "cure", "raid", "trade", "shear", "enchant":
                return true;
            default:
                return QuestService.matches(new QuestService.Def("", "", "", type, arg, g.amount(), 0, 0, 0), type, subject);
        }
    }

    private static boolean isGroup(String arg) {
        return switch (arg.toLowerCase(Locale.ROOT)) {
            case "lanterns", "candles", "lights", "flowers", "saplings", "wool", "snow", "eggs" -> true;
            default -> false;
        };
    }

    /** Material-Ziel: Name, Gruppe (lanterns, candles, lights, flowers, saplings, wool, snow, eggs) oder Minecraft-Tag (#name). */
    static boolean materialMatches(String arg, Material m) {
        String a = arg.toLowerCase(Locale.ROOT);
        if (a.startsWith("#")) {
            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(a.substring(1)), Material.class);
            if (tag == null) tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft(a.substring(1)), Material.class);
            return tag != null && tag.isTagged(m);
        }
        String n = m.name();
        return switch (a) {
            case "lanterns" -> m == Material.LANTERN || m == Material.SOUL_LANTERN || n.endsWith("COPPER_LANTERN");
            case "candles" -> Tag.CANDLES.isTagged(m);
            case "lights" -> m == Material.LANTERN || m == Material.SOUL_LANTERN || n.endsWith("COPPER_LANTERN") || Tag.CANDLES.isTagged(m)
                    || m == Material.SEA_LANTERN || m == Material.GLOWSTONE || m == Material.SHROOMLIGHT || n.endsWith("FROGLIGHT")
                    || m == Material.JACK_O_LANTERN || m == Material.REDSTONE_LAMP || n.endsWith("COPPER_BULB") || m == Material.END_ROD;
            case "flowers" -> Tag.FLOWERS.isTagged(m);
            case "saplings" -> Tag.SAPLINGS.isTagged(m);
            case "wool" -> Tag.WOOL.isTagged(m);
            case "snow" -> m == Material.SNOW || m == Material.SNOW_BLOCK || m == Material.POWDER_SNOW;
            case "eggs" -> m == Material.EGG || m == Material.BROWN_EGG || m == Material.BLUE_EGG;
            default -> n.equalsIgnoreCase(arg);
        };
    }

    /** Wesen-Ziel: Name (z. B. RABBIT, SNOW_GOLEM), „animal“, „monster“ oder „player“. */
    static boolean entityMatches(String arg, Entity en) {
        return switch (arg.toLowerCase(Locale.ROOT)) {
            case "animal" -> en instanceof org.bukkit.entity.Animals;
            case "monster" -> en instanceof org.bukkit.entity.Monster;
            case "player" -> en instanceof Player;
            default -> en.getType().name().equalsIgnoreCase(arg);
        };
    }

    private boolean condition(String cond, Player p, Location at) {
        if (cond == null || cond.isBlank()) return true;
        Location l = at != null ? at : p.getLocation();
        World w = l.getWorld();
        String c = cond.toLowerCase(Locale.ROOT);
        if (c.equals("nacht")) {
            long t = w == null ? 0 : w.getTime();
            return w != null && w.getEnvironment() == World.Environment.NORMAL && t >= 13000 && t < 23000;
        }
        if (c.equals("nether")) return w != null && w.getEnvironment() == World.Environment.NETHER;
        if (c.equals("tag")) {
            long t = w == null ? 0 : w.getTime();
            return w != null && w.getEnvironment() == World.Environment.NORMAL && (t < 12000 || t >= 23500);
        }
        if (c.equals("schnee")) return l.getBlock().getTemperature() < 0.15;
        if (c.equals("wasser")) return p.isInWater() || l.getBlock().isLiquid();
        if (c.startsWith("uhrzeit:")) {
            String[] r = c.substring(8).split("-");
            try {
                int h = ZonedDateTime.now(ZONE).getHour(), a = Integer.parseInt(r[0]), b = Integer.parseInt(r[1]);
                return h >= a && h < b;
            } catch (RuntimeException ex) {
                return false;
            }
        }
        if (c.equals("grundstueck")) {
            var claim = addon.claims() == null ? null : addon.claims().at(l);
            return claim != null && (claim.owner().equals(p.getUniqueId()) || addon.claims().isTrusted(claim.owner(), p.getUniqueId()));
        }
        return true;
    }

    /** Für die Anzeige oben: das angenommene Kapitel mit dem ersten offenen Ziel. */
    public synchronized QuestService.Track track(UUID id) {
        Event e = active();
        if (e == null) return null;
        Data d = data(e, id);
        if (d.accepted < 0 || d.accepted >= e.chapters().size()) return null;
        Chapter c = e.chapters().get(d.accepted);
        if (c.goals().isEmpty()) return null;
        int g = 0;
        while (g < c.goals().size() - 1 && d.progress.getOrDefault(d.accepted + ":" + g, 0L) >= c.goals().get(g).amount()) g++;
        Goal goal = c.goals().get(g);
        QuestService.Def def = new QuestService.Def("e" + c.index(), c.name(), goal.text(), goal.type(), goal.arg(), goal.amount(),
                c.coins(), c.tokens(), 0);
        return new QuestService.Track("e:" + c.index(), def, Math.min(goal.amount(), d.progress.getOrDefault(d.accepted + ":" + g, 0L)));
    }

    // =================================================================================================================
    // Sammel-Items, Sammel-Regen und Feuerwerk
    // =================================================================================================================

    /** Sammel-Item als ItemStack (mit Pack eigenes Bild, sonst das Ersatz-Material). */
    ItemStack collectStack(Collect c, boolean rare) {
        ItemStack it = new ItemStack(rare ? c.rareMaterial() : c.material());
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString("kuby:event/" + (rare ? c.rareItem() : c.item())));
        meta.displayName(Msg.item(rare ? c.rareName() : c.name()));
        it.setItemMeta(meta);
        return it;
    }

    /** Alle 5 Sekunden: Sammel-Items nachlegen, Sammel-Regen und Feuerwerk; ohne Event aufräumen. */
    public void tick() {
        Event e = active();
        if (e == null || e.collect() == null) {
            if (!candyItems.isEmpty()) clearCandy();
            lastRain = 0;
            if (e == null) return;
        }
        long now = System.currentTimeMillis();
        if (e.collect() != null && now - lastCandy >= e.collect().seconds() * 1000L) {
            lastCandy = now;
            tickCandy();
        }
        Special sp = e.special();
        if (sp == null) return;
        if (sp.rainMinutes() > 0 && e.collect() != null) {
            if (lastRain == 0) lastRain = now;
            else if (now - lastRain >= sp.rainMinutes() * 60_000L) {
                lastRain = now;
                rain(e, sp.rainAmount());
            }
        }
        if (fireworksNow(sp)) fireworks(5);
    }

    /** Läuft gerade das Feuerwerk? „MM-TT HH:mm“ (an diesem Tag) oder „HH:mm“ (jeden Tag des Events). */
    private static boolean fireworksNow(Special sp) {
        if (sp.fireworks() == null || sp.fireworks().isBlank()) return false;
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        try {
            ZonedDateTime start;
            String f = sp.fireworks().trim();
            if (f.contains("-")) start = at(f, now.getYear());
            else start = now.with(LocalTime.parse(f.length() == 4 ? "0" + f : f));
            return !now.isBefore(start) && now.isBefore(start.plusSeconds(Math.max(10, sp.fireworkSeconds())));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Sammel-Items um den Survival-Spawn verteilen (nur wenn Spieler in der Nähe sind). */
    public void tickCandy() {
        Event e = active();
        if (e == null || e.collect() == null) {
            clearCandy();
            return;
        }
        Collect c = e.collect();
        Location spawn = core.modes().spawnOf(ModeService.SURVIVAL);
        if (spawn == null || spawn.getWorld() == null) return;
        World w = spawn.getWorld();
        double near = (c.radius() + 48.0) * (c.radius() + 48.0);
        boolean someone = false;
        for (Player pl : w.getPlayers()) if (pl.getLocation().distanceSquared(spawn) < near) someone = true;
        if (!someone) return;
        prune();
        for (int k = 0, tries = 0; k < 3 && candyItems.size() < c.max() && tries < 20; tries++) {
            if (drop(e, spawn, c.radius(), 0)) k++;
        }
    }

    private void prune() {
        candyItems.removeIf(u -> {
            Entity en = Bukkit.getEntity(u);
            return en == null || !en.isValid();
        });
    }

    /** Ein Sammel-Item auf festen Boden im Umkreis legen (fall = Höhe darüber, für den Regen). */
    private boolean drop(Event e, Location spawn, int radius, int fall) {
        Collect c = e.collect();
        World w = spawn.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double ang = r.nextDouble(Math.PI * 2), dist = 4 + r.nextDouble(Math.max(1, radius - 4));
        int x = (int) Math.floor(spawn.getX() + Math.cos(ang) * dist), z = (int) Math.floor(spawn.getZ() + Math.sin(ang) * dist);
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return false;
        Block ground = null;
        for (int y = spawn.getBlockY() + 6; y >= spawn.getBlockY() - 10; y--) {
            Block b = w.getBlockAt(x, y, z);
            if (b.getType().isSolid() && b.getRelative(0, 1, 0).isPassable() && !b.getRelative(0, 1, 0).isLiquid()
                    && b.getRelative(0, 2, 0).isPassable()) {
                ground = b;
                break;
            }
        }
        if (ground == null) return false;
        boolean rare = r.nextDouble() < c.rareChance();
        Location l = ground.getLocation().add(0.5, 1.15 + fall, 0.5);
        if (fall > 0) {
            for (int dy = 1; dy <= fall; dy++) if (!ground.getRelative(0, dy, 0).isPassable()) return false;
        }
        Item item = w.dropItem(l, collectStack(c, rare), it -> {
            it.setVelocity(new Vector());
            it.setUnlimitedLifetime(true);
            it.setPersistent(false);
            it.setCanMobPickup(false);
            it.setGlowing(rare);
            it.getPersistentDataContainer().set(candyKey, PersistentDataType.BYTE, (byte) (rare ? 2 : 1));
        });
        candyItems.add(item.getUniqueId());
        return true;
    }

    /** Sammel-Regen: viele Items fallen auf einmal rund um den Spawn, alle Survival-Spieler bekommen Bescheid. */
    public int rain(Event e, int amount) {
        if (e == null || e.collect() == null) return 0;
        Location spawn = core.modes().spawnOf(ModeService.SURVIVAL);
        if (spawn == null || spawn.getWorld() == null) return 0;
        List<Player> survival = new ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) if (ModeService.SURVIVAL.equals(core.modes().modeOf(pl.getUniqueId()))) survival.add(pl);
        if (survival.isEmpty()) return 0;
        prune();
        int n = 0;
        int radius = Math.max(12, e.collect().radius() / 2);
        for (int tries = 0; n < amount && tries < amount * 4; tries++) if (drop(e, spawn, radius, 6)) n++;
        if (n == 0) return 0;
        for (Player pl : survival) {
            Msg.send(pl, e.tag() + "<b>☄ " + Msg.escape(e.name()) + ":</b> <white>Am Spawn regnet es " + Msg.escape(e.currency())
                    + "! <click:run_command:'/spawn'>" + e.tag() + "[Zum Spawn]</click>");
            pl.playSound(pl.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
        }
        return n;
    }

    /** Feuerwerk über dem Survival-Spawn (count Raketen). Schadet niemandem (EventListener). */
    public void fireworks(int count) {
        Location spawn = core.modes().spawnOf(ModeService.SURVIVAL);
        if (spawn == null || spawn.getWorld() == null) return;
        World w = spawn.getWorld();
        boolean someone = false;
        for (Player pl : w.getPlayers()) if (pl.getLocation().distanceSquared(spawn) < 120 * 120) someone = true;
        if (!someone) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        FireworkEffect.Type[] types = FireworkEffect.Type.values();
        Color[] colors = {Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.AQUA, Color.FUCHSIA, Color.WHITE, Color.PURPLE};
        for (int k = 0; k < count; k++) {
            double ang = r.nextDouble(Math.PI * 2), dist = 6 + r.nextDouble(24);
            Location l = spawn.clone().add(Math.cos(ang) * dist, 2, Math.sin(ang) * dist);
            if (!w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
            w.spawn(l, Firework.class, fw -> {
                FireworkMeta m = fw.getFireworkMeta();
                m.addEffect(FireworkEffect.builder().with(types[r.nextInt(types.length)]).withColor(colors[r.nextInt(colors.length)],
                        colors[r.nextInt(colors.length)]).withFade(colors[r.nextInt(colors.length)]).flicker(r.nextBoolean())
                        .trail(r.nextBoolean()).build());
                m.setPower(1 + r.nextInt(2));
                fw.setFireworkMeta(m);
                fw.getPersistentDataContainer().set(fireworkKey, PersistentDataType.BYTE, (byte) 1);
            });
        }
    }

    public void clearCandy() {
        for (UUID u : candyItems) {
            Entity en = Bukkit.getEntity(u);
            if (en != null) en.remove();
        }
        candyItems.clear();
    }

    boolean isCandy(Item item) {
        return item.getPersistentDataContainer().has(candyKey, PersistentDataType.BYTE);
    }

    /** Ein Spieler hebt ein Sammel-Item auf. */
    void pickCandy(Player p, Item item) {
        boolean purple = item.getPersistentDataContainer().getOrDefault(candyKey, PersistentDataType.BYTE, (byte) 1) == 2;
        Event e = active();
        item.remove();
        candyItems.remove(item.getUniqueId());
        if (e == null || e.collect() == null) return;
        int value = purple ? e.collect().rareValue() : 1;
        long total;
        synchronized (this) {
            Data d = data(e, p.getUniqueId());
            d.candy += value;
            total = d.candy;
            dirty = true;
        }
        progress(p, "collect", "any", value, p.getLocation());
        if (purple) progress(p, "collect", "rare", 1, p.getLocation());
        p.sendActionBar(Msg.parse((purple ? "<light_purple>+" : e.tag() + "+") + value + " " + Msg.escape(e.currency())
                + " <gray>(" + Msg.coins(total) + ")"));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, purple ? 0.8f : 1.6f);
        if (purple) p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
    }

    // =================================================================================================================
    // Event-Shop
    // =================================================================================================================

    public synchronized boolean owned(UUID id, Offer o) {
        return switch (o.kind()) {
            case "TITLE" -> core.tags().has(id, o.value());
            case "COSMETIC" -> {
                var it = core.cosmetics().find(o.value());
                yield it != null && core.cosmetics().owns(id, it);
            }
            default -> false;
        };
    }

    public String buy(Player p, Offer o) {
        UUID id = p.getUniqueId();
        Event e = active();
        if (e == null) return "Gerade läuft kein Event.";
        if (o.once() && owned(id, o)) return "Das hast du schon.";
        synchronized (this) {
            Data d = data(e, id);
            if (d.candy < o.price()) return "Dafür brauchst du " + Msg.coins(o.price()) + " " + e.currency() + ".";
            String err = give(p, o);
            if (err != null) return err;
            d.candy -= o.price();
            dirty = true;
        }
        Msg.send(p, "<green>Gekauft: <white>" + Msg.escape(o.name()) + " <gray>(-" + Msg.coins(o.price()) + " " + Msg.escape(e.currency()) + ")");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        return null;
    }

    private String give(Player p, Offer o) {
        UUID id = p.getUniqueId();
        switch (o.kind()) {
            case "TITLE" -> core.tags().grant(id, o.value());
            case "COSMETIC" -> {
                var it = core.cosmetics().find(o.value());
                if (it == null) return "Dieses Angebot ist gerade nicht verfügbar.";
                core.cosmetics().grant(id, it);
            }
            case "ITEM" -> {
                Material m = Material.matchMaterial(o.value());
                if (m == null || !m.isItem()) return "Dieses Angebot ist gerade nicht verfügbar.";
                int rest = Math.max(1, o.amount());
                while (rest > 0) {
                    ItemStack st = new ItemStack(m, Math.min(rest, m.getMaxStackSize()));
                    rest -= st.getAmount();
                    p.getInventory().addItem(st).values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
                }
            }
            case "KEY" -> {
                if (core.keys().type(o.value()) == null) return "Dieses Angebot ist gerade nicht verfügbar.";
                core.keys().give(id, ModeService.SURVIVAL, o.value(), Math.max(1, o.amount()));
            }
            case "COINS" -> core.modes().depositIn(id, ModeService.SURVIVAL, parse(o.value()));
            case "TOKENS" -> addon.jobs().addTokens(id, parse(o.value()));
            default -> {
                return "Dieses Angebot ist gerade nicht verfügbar.";
            }
        }
        return null;
    }

    private static long parse(String v) {
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // =================================================================================================================
    // Admin
    // =================================================================================================================

    public synchronized Map<String, Event> events() {
        return events;
    }

    /** Klang beim Annehmen, passend zum Preset. */
    private static Sound sound(Event e) {
        return switch (e.theme()) {
            case "halloween" -> Sound.ENTITY_WITCH_CELEBRATE;
            case "weihnachten" -> Sound.BLOCK_NOTE_BLOCK_BELL;
            case "silvester" -> Sound.ENTITY_FIREWORK_ROCKET_LAUNCH;
            case "ostern" -> Sound.ENTITY_CHICKEN_EGG;
            case "valentinstag" -> Sound.ENTITY_CAT_PURREOW;
            default -> Sound.BLOCK_NOTE_BLOCK_CHIME;
        };
    }

    /** „23.10. 04:00 bis 1.11. 04:00“ für das nächste oder laufende Zeitfenster. */
    synchronized String windowText(Event e) {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime[] w = window(e, now);
        try {
            if (w == null) {
                ZonedDateTime s = at(e.start(), now.getYear()), en = at(e.end(), now.getYear());
                if (!en.isAfter(s)) en = en.plusYears(1);
                if (!en.isAfter(now)) {
                    s = at(e.start(), now.getYear() + 1);
                    en = at(e.end(), now.getYear() + 1);
                    if (!en.isAfter(s)) en = en.plusYears(1);
                }
                w = new ZonedDateTime[]{s, en};
            }
        } catch (RuntimeException ex) {
            return "Datum fehlerhaft";
        }
        return fmt(w[0]) + " bis " + fmt(w[1]);
    }

    private static String fmt(ZonedDateTime t) {
        return t.getDayOfMonth() + "." + t.getMonthValue() + "." + t.getYear() + " " + String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    /** Schaltet „automatisch“ im Preset um (schreibt die Datei, lädt neu). */
    private boolean setAutomatic(String id, boolean on) {
        Event e = events.get(id);
        if (e == null || e.file() == null) return false;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(e.file());
        yml.set("automatisch", on);
        try {
            yml.save(e.file());
        } catch (IOException ex) {
            return false;
        }
        loadDefs();
        return true;
    }

    public void admin(Player p, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        String id = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : null;
        switch (sub) {
            case "presets", "liste" -> {
                Event cur = active();
                Msg.send(p, "<#FC981F><b>Event-Presets</b> <gray>(Dateien in plugins/KubySurvival/events/)");
                for (Event e : events.values()) {
                    String state = cur == e ? "<green>läuft" : e.automatic() ? "<gray>automatisch" : "<dark_gray>aus";
                    Msg.send(p, " " + e.tag() + Msg.escape(e.name()) + " <dark_gray>(" + e.id() + ") " + state + " <gray>· "
                            + windowText(e) + " · " + e.chapters().size() + " Kapitel <click:run_command:'/kp event laden " + e.id()
                            + "'><green>[Laden]</click>");
                }
            }
            case "start", "laden" -> {
                if (id == null && !events.isEmpty()) id = events.keySet().iterator().next();
                if (id == null || !events.containsKey(id)) {
                    Msg.send(p, "<red>Presets: " + String.join(", ", events.keySet()));
                    return;
                }
                synchronized (this) {
                    forced = id;
                    off = false;
                    save();
                }
                clearCandy();
                lastRain = 0;
                Event e = events.get(id);
                tickCandy();
                Msg.send(p, "<green>Preset " + e.tag() + Msg.escape(e.name()) + "<green> geladen und gestartet: " + e.chapters().size()
                        + " Kapitel, " + (e.collect() == null ? "keine Sammel-Items" : Msg.escape(e.currency()) + " am Spawn")
                        + ", Shop mit " + e.shop().size() + " Angeboten. <gray>/kp event auto = wieder nach Datum");
            }
            case "stop" -> {
                synchronized (this) {
                    forced = null;
                    off = true;
                    save();
                }
                clearCandy();
                Msg.send(p, "<gray>Events sind aus, bis <white>/kp event auto<gray> oder <white>/kp event laden<gray>.");
            }
            case "auto" -> {
                synchronized (this) {
                    forced = null;
                    off = false;
                    save();
                }
                clearCandy();
                Event e = active();
                Msg.send(p, "<green>Events laufen wieder nach Datum. <gray>Gerade: <white>" + (e == null ? "keins" : e.name()));
            }
            case "an", "aus" -> {
                if (id == null || !events.containsKey(id)) {
                    Msg.send(p, "<red>/kp event " + sub + " <Preset> <gray>(" + String.join(", ", events.keySet()) + ")");
                    return;
                }
                boolean on = sub.equals("an");
                Msg.send(p, setAutomatic(id, on) ? "<green>" + id + (on ? " läuft jetzt jedes Jahr automatisch." : " läuft nicht mehr automatisch.")
                        : "<red>Konnte die Datei nicht speichern.");
            }
            case "candy", "sammeln" -> {
                tickCandy();
                Msg.send(p, "<green>Sammel-Items verteilt. <gray>Liegen gerade: <white>" + candyItems.size());
            }
            case "regen" -> {
                Event e = active();
                int n = rain(e, e == null || e.special() == null ? 25 : e.special().rainAmount());
                Msg.send(p, n > 0 ? "<green>Sammel-Regen: " + n + " Items am Spawn." : "<red>Kein Event mit Sammel-Items oder niemand in Survival.");
            }
            case "feuerwerk" -> {
                fireworks(8);
                Msg.send(p, "<green>Feuerwerk über dem Spawn gezündet.");
            }
            case "reload" -> {
                installPresets();
                loadDefs();
                Msg.send(p, "<green>Presets neu geladen: <white>" + String.join(", ", events.keySet()));
            }
            case "reset" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : p;
                Event e = active();
                if (t == null || e == null) {
                    Msg.send(p, "<red>Spieler nicht online oder kein Event aktiv.");
                    return;
                }
                synchronized (this) {
                    data.computeIfAbsent(instance(e), k -> new HashMap<>()).remove(t.getUniqueId());
                    dirty = true;
                }
                Msg.send(p, "<green>Event-Stand von " + t.getName() + " zurückgesetzt.");
            }
            default -> {
                Event e = active();
                Msg.send(p, "<#FC981F><b>Events</b> <gray>Gerade: <white>" + (e == null ? "keins" : e.name() + " (" + endText(e) + ")")
                        + (forced != null ? " <gray>[von Hand]" : off ? " <gray>[aus]" : ""));
                Msg.send(p, "<white>/kp event presets <gray>· <white>laden <Preset> <gray>· <white>stop <gray>· <white>auto <gray>· "
                        + "<white>an|aus <Preset> <gray>· <white>sammeln <gray>· <white>regen <gray>· <white>feuerwerk <gray>· "
                        + "<white>reset <Spieler> <gray>· <white>reload");
            }
        }
    }

    public void saveIfDirty() {
        if (dirty) save();
    }
}

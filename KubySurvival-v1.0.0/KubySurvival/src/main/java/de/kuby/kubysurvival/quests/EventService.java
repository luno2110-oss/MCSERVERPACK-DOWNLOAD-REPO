package de.kuby.kubysurvival.quests;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
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
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
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

/**
 * Events mit Kapiteln (events.yml), z. B. Halloween: läuft jedes Jahr in seinem Zeitfenster oder von Hand gestartet.
 * Kapitel der Reihe nach annehmen, Ziele zählen (aus QuestService.progress und EventListener), abholen: Coins,
 * Candy Corn (Event-Währung für den Candy-Shop) und Tokens (wie Quest-Tokens). Candy Corn liegt am Survival-Spawn.
 * Spielerdaten je Event und Jahr in events-data.yml.
 */
public final class EventService {

    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    public record Goal(String type, String arg, long amount, String cond, String text) {}

    public record Chapter(int index, String name, String text, long coins, long candy, long tokens, String from, List<Goal> goals) {}

    /** Angebot im Candy-Shop: Art (TITLE, COSMETIC, ITEM, KEY, COINS, TOKENS), Wert, Menge, Name, Preis. */
    public record Offer(String kind, String value, int amount, String name, long price) {
        /** Einmalige Angebote (Titel, Kosmetik) zeigen „gekauft“. */
        public boolean once() {
            return kind.equals("TITLE") || kind.equals("COSMETIC");
        }
    }

    public record Candy(int radius, int max, int seconds, double purpleChance, int purpleValue) {}

    public record Event(String id, String name, String start, String end, String currency, Candy candy, List<Chapter> chapters,
                        List<Offer> shop, Map<String, String> titles) {}

    public enum State { LOCKED, OPEN, ACCEPTED, DONE, CLAIMED }

    static final class Data {
        int accepted = -1;
        final Set<Integer> claimed = new TreeSet<>();
        final Map<String, Long> progress = new HashMap<>();
        long candy;
    }

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File defFile, dataFile;
    private final Map<String, Event> events = new LinkedHashMap<>();
    /** Spielerdaten je Event-Instanz (id-Jahr). */
    private final Map<String, Map<UUID, Data>> data = new HashMap<>();
    /** Von Hand gestartet (null = nach Datum); off = von Hand beendet. */
    private String forced;
    private boolean off;
    private boolean dirty;
    final NamespacedKey candyKey;
    private final Set<UUID> candyItems = new HashSet<>();
    private long lastCandy;

    public EventService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.defFile = new File(addon.getDataFolder(), "events.yml");
        this.dataFile = new File(addon.getDataFolder(), "events-data.yml");
        this.candyKey = new NamespacedKey(addon, "candy");
        if (!defFile.exists()) addon.saveResource("events.yml", false);
        loadDefs();
        loadData();
    }

    // =================================================================================================================
    // Laden und Speichern
    // =================================================================================================================

    public synchronized void loadDefs() {
        events.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(defFile);
        ConfigurationSection root = yml.getConfigurationSection("events");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection e = root.getConfigurationSection(id);
            if (e == null) continue;
            List<Chapter> chapters = new ArrayList<>();
            int i = 0;
            for (Map<?, ?> m : e.getMapList("kapitel")) {
                List<Goal> goals = new ArrayList<>();
                Object zl = m.get("ziele");
                if (zl instanceof List<?> list) {
                    for (Object o : list) {
                        if (!(o instanceof Map<?, ?> g)) continue;
                        goals.add(new Goal(str(g.get("type"), "").toLowerCase(Locale.ROOT), str(g.get("arg"), null),
                                num(g.get("menge"), 1), str(g.get("bedingung"), null), str(g.get("text"), "")));
                    }
                }
                chapters.add(new Chapter(i++, str(m.get("name"), "Kapitel " + i), str(m.get("text"), ""), num(m.get("coins"), 0),
                        num(m.get("candy"), 0), num(m.get("tokens"), 0), str(m.get("ab"), null), goals));
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
            ConfigurationSection c = e.getConfigurationSection("candy");
            Candy candy = c == null ? null : new Candy(c.getInt("radius", 60), c.getInt("max", 30), Math.max(5, c.getInt("alle-sekunden", 20)),
                    c.getDouble("lila-chance", 0.08), Math.max(1, c.getInt("lila-wert", 5)));
            events.put(id, new Event(id, e.getString("name", id), e.getString("start", "01-01 00:00"), e.getString("ende", "01-02 00:00"),
                    e.getString("waehrung", "Candy Corn"), candy, chapters, shop, titles));
        }
        ensureTitles();
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

    private static ZonedDateTime at(String mmddhhmm, int year) {
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
            // falsches Datum in events.yml
        }
        return null;
    }

    /** Das gerade laufende Event (null = keins). */
    public synchronized Event active() {
        if (off) return null;
        if (forced != null) return events.get(forced);
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        for (Event e : events.values()) if (window(e, now) != null) return e;
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
        Msg.send(p, "<#FC981F><b>" + Msg.escape(e.name()) + ":</b> <white>" + Msg.escape(e.chapters().get(i).name())
                + " <gray>angenommen. Es wird oben angezeigt.");
        p.playSound(p.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 0.6f, 1.2f);
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
        Msg.send(p, "<#FC981F><b>Kapitel " + (i + 1) + " geschafft!</b> <gold>+" + Msg.coins(c.coins()) + " Coins <#FC8888>+"
                + Msg.coins(c.candy()) + " " + Msg.escape(e.currency()) + " <light_purple>+" + c.tokens() + " Tokens");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
        if (i + 1 < e.chapters().size() && state(id, i + 1) == State.OPEN) {
            Msg.send(p, "<gray>Kapitel " + (i + 2) + " wartet: <click:run_command:'/quest event'><#FC981F>[Ansehen]</click>");
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
            Msg.send(p, "<#FC981F>✔ <white>" + Msg.escape(done));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        }
        if (finished) {
            Msg.send(p, "<#FC981F><b>Kapitel geschafft!</b> <click:run_command:'/quest event'><gold>[Belohnung abholen]</click>");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.3f);
        }
    }

    private static boolean argMatches(Goal g, String type, Object subject) {
        String arg = g.arg();
        switch (type) {
            case "candy":
                return arg == null ? "any".equals(subject) : "lila".equals(subject) && arg.equalsIgnoreCase("lila");
            case "place":
                if (arg == null || !(subject instanceof Material m)) return arg == null;
                if (arg.equalsIgnoreCase("lanterns")) return m == Material.LANTERN || m == Material.SOUL_LANTERN || m.name().endsWith("COPPER_LANTERN");
                if (arg.equalsIgnoreCase("candles")) return Tag.CANDLES.isTagged(m);
                return m.name().equalsIgnoreCase(arg);
            case "pickup":
                return arg == null || (subject instanceof Material m && m.name().equalsIgnoreCase(arg));
            case "carve", "brew", "fish", "treasure", "cure", "raid":
                return true;
            default:
                return QuestService.matches(new QuestService.Def("", "", "", type, arg, g.amount(), 0, 0, 0), type, subject);
        }
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
    // Candy Corn
    // =================================================================================================================

    /** Candy Corn als Item (mit Pack eigenes Bild, sonst Farbstoff). */
    ItemStack candyStack(boolean purple) {
        ItemStack it = new ItemStack(purple ? Material.PURPLE_DYE : Material.ORANGE_DYE);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString(purple ? "kuby:event/candy_corn_purple" : "kuby:event/candy_corn"));
        meta.displayName(Msg.item(purple ? "<light_purple>Lila Candy Corn" : "<#FC981F>Candy Corn"));
        it.setItemMeta(meta);
        return it;
    }

    /** Alle 5 Sekunden: im Takt des Events Candy Corn verteilen, ohne Event aufräumen. */
    public void tick() {
        Event e = active();
        if (e == null || e.candy() == null) {
            if (!candyItems.isEmpty()) clearCandy();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCandy < e.candy().seconds() * 1000L) return;
        lastCandy = now;
        tickCandy();
    }

    /** Candy Corn um den Survival-Spawn verteilen (nur wenn Spieler in der Nähe sind). */
    public void tickCandy() {
        Event e = active();
        if (e == null || e.candy() == null) {
            clearCandy();
            return;
        }
        Candy c = e.candy();
        Location spawn = core.modes().spawnOf(ModeService.SURVIVAL);
        if (spawn == null || spawn.getWorld() == null) return;
        World w = spawn.getWorld();
        double near = (c.radius() + 48.0) * (c.radius() + 48.0);
        boolean someone = false;
        for (Player pl : w.getPlayers()) if (pl.getLocation().distanceSquared(spawn) < near) someone = true;
        if (!someone) return;
        candyItems.removeIf(u -> {
            Entity en = Bukkit.getEntity(u);
            return en == null || !en.isValid();
        });
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0, tries = 0; k < 3 && candyItems.size() < c.max() && tries < 20; tries++) {
            double ang = r.nextDouble(Math.PI * 2), dist = 4 + r.nextDouble(Math.max(1, c.radius() - 4));
            int x = (int) Math.floor(spawn.getX() + Math.cos(ang) * dist), z = (int) Math.floor(spawn.getZ() + Math.sin(ang) * dist);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block ground = null;
            for (int y = spawn.getBlockY() + 6; y >= spawn.getBlockY() - 10; y--) {
                Block b = w.getBlockAt(x, y, z);
                if (b.getType().isSolid() && b.getRelative(0, 1, 0).isPassable() && !b.getRelative(0, 1, 0).isLiquid()
                        && b.getRelative(0, 2, 0).isPassable()) {
                    ground = b;
                    break;
                }
            }
            if (ground == null) continue;
            boolean purple = r.nextDouble() < c.purpleChance();
            Location l = ground.getLocation().add(0.5, 1.15, 0.5);
            Item item = w.dropItem(l, candyStack(purple), it -> {
                it.setVelocity(new Vector());
                it.setUnlimitedLifetime(true);
                it.setPersistent(false);
                it.setCanMobPickup(false);
                it.setGlowing(purple);
                it.getPersistentDataContainer().set(candyKey, PersistentDataType.BYTE, (byte) (purple ? 2 : 1));
            });
            candyItems.add(item.getUniqueId());
            k++;
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

    /** Ein Spieler hebt Candy Corn auf. */
    void pickCandy(Player p, Item item) {
        boolean purple = item.getPersistentDataContainer().getOrDefault(candyKey, PersistentDataType.BYTE, (byte) 1) == 2;
        Event e = active();
        item.remove();
        candyItems.remove(item.getUniqueId());
        if (e == null || e.candy() == null) return;
        int value = purple ? e.candy().purpleValue() : 1;
        long total;
        synchronized (this) {
            Data d = data(e, p.getUniqueId());
            d.candy += value;
            total = d.candy;
            dirty = true;
        }
        progress(p, "candy", "any", value, p.getLocation());
        if (purple) progress(p, "candy", "lila", 1, p.getLocation());
        p.sendActionBar(Msg.parse((purple ? "<light_purple>+" : "<#FC981F>+") + value + " " + Msg.escape(e.currency())
                + " <gray>(" + Msg.coins(total) + ")"));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, purple ? 0.8f : 1.6f);
        if (purple) p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
    }

    // =================================================================================================================
    // Candy-Shop
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

    public void admin(Player p, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "start" -> {
                String id = args.length > 2 ? args[2] : events.isEmpty() ? null : events.keySet().iterator().next();
                if (id == null || !events.containsKey(id)) {
                    Msg.send(p, "<red>Events: " + String.join(", ", events.keySet()));
                    return;
                }
                synchronized (this) {
                    forced = id;
                    off = false;
                    save();
                }
                Msg.send(p, "<green>Event " + id + " läuft jetzt (von Hand). <gray>/kp event auto = wieder nach Datum");
            }
            case "stop" -> {
                synchronized (this) {
                    forced = null;
                    off = true;
                    save();
                }
                clearCandy();
                Msg.send(p, "<gray>Events sind aus, bis <white>/kp event auto<gray> oder <white>/kp event start<gray>.");
            }
            case "auto" -> {
                synchronized (this) {
                    forced = null;
                    off = false;
                    save();
                }
                Event e = active();
                Msg.send(p, "<green>Events laufen wieder nach Datum. <gray>Gerade: <white>" + (e == null ? "keins" : e.name()));
            }
            case "candy" -> {
                tickCandy();
                Msg.send(p, "<green>Candy Corn verteilt. <gray>Liegen gerade: <white>" + candyItems.size());
            }
            case "reload" -> {
                loadDefs();
                Msg.send(p, "<green>events.yml neu geladen.");
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
                Msg.send(p, "<white>/kp event start <id> <gray>· <white>stop <gray>· <white>auto <gray>· <white>candy <gray>· <white>reset <Spieler> "
                        + "<gray>· <white>reload");
            }
        }
    }

    public void saveIfDirty() {
        if (dirty) save();
    }
}

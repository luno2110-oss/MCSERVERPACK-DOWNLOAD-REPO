package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Battle-Pass mit Seasons, zwei Spuren und Aufgaben.
 *
 * <p>Stufe = Punkte / battlepass.xp-per-level + gekaufte Stufen (höchstens 100). FREE hat Belohnungen auf jeder
 * zweiten Stufe, PREMIUM (Juwelen) auf jeder Stufe; Premium gilt rückwirkend. Belohnungen stehen in battlepass.rewards
 * (leer = Standard), Format: STUFE;SPUR;ART;WERT[;ICON[;NAME]] mit ART COINS, XP, JEWELS, COMMAND, ITEM (z. B.
 * NETHERITE_INGOT:5), ICON = Icon-Name aus dem Dialog-Kit (sonst nach Art bzw. Material).
 *
 * <p>Aufgaben: aus battlepass.quests-daily und quests-weekly werden jeden Tag bzw. jede Woche (Montag) für alle
 * Spieler gleich daily-count und weekly-count ausgewählt. Format: ID;Name[|English];ICON;TYP[:ZIEL];ANZAHL;PUNKTE[;modi]
 * mit TYP KILL, MINE, COLLECT, FISH, BREED, TAME, CRATE, LEVELS, TRAVEL (km), PLAYTIME (Minuten), VOTE, TRADE, DUEL,
 * DAILY, EAT, CRAFT, ENCHANT, PLACE.
 *
 * <p>Seasons: Season first-season beginnt am season-start (JJJJ-MM-TT) und dauert season-days Tage, danach folgt
 * automatisch die nächste. Zum Start einer neuen Season beginnt jeder Spieler wieder bei Stufe 0 ohne Premium.
 */
public final class BattlepassService implements ModeData {

    public static final int MAX_LEVEL = 100;
    public static final String FREE = "FREE";
    public static final String PREMIUM = "PREMIUM";
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    public enum Period { DAILY, WEEKLY }

    /** Aufgabe; arg leer = jedes Ziel (z. B. KILL ohne Ziel: jedes Monster und Tier). */
    public record Quest(String id, String name, String nameEn, String icon, String type, String arg, int target,
                        int points, Period period, Set<String> modes) {
        public String label(boolean en) {
            return en && !nameEn.isEmpty() ? nameEn : name;
        }

        /** Interne Zähl-Einheit je angezeigter Einheit (Reisen: Meter je km). */
        public int scale() {
            return "TRAVEL".equals(type) ? 1000 : 1;
        }
    }

    /** Belohnung; icon und name optional (leer = nach Art). */
    public record Reward(int level, String track, String type, String value, String icon, String name) {
        public Reward(int level, String track, String type, String value) {
            this(level, track, type, value, "", "");
        }

        /** Zeile für battlepass.rewards. */
        public String line() {
            String s = level + ";" + track + ";" + type + ";" + value;
            if (!icon.isEmpty() || !name.isEmpty()) s += ";" + icon;
            if (!name.isEmpty()) s += ";" + name;
            return s;
        }
    }

    /** Stand einer Aufgabe für einen Spieler (progress in angezeigten Einheiten). */
    public record QuestState(Quest quest, int progress, boolean done) {}

    private static final class Data {
        long xp;
        int bought;
        int claimedFree;
        int claimedPremium;
        boolean premium;
        int season;
        String day = "";
        String week = "";
        String buyDay = "";
        int boughtToday;
        final Map<String, Integer> dayProgress = new HashMap<>();
        final Set<String> dayDone = new HashSet<>();
        final Map<String, Integer> progress = new HashMap<>();
        final Set<String> done = new HashSet<>();
    }

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Data> data = new HashMap<>();
    private boolean dirty;

    public BattlepassService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "battlepass.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Data d = new Data();
            d.xp = yml.getLong(key + ".xp");
            d.bought = yml.getInt(key + ".bought");
            d.claimedFree = yml.getInt(key + ".claimedFree", yml.getInt(key + ".claimed"));
            d.claimedPremium = yml.getInt(key + ".claimedPremium");
            d.premium = yml.getBoolean(key + ".premium");
            d.season = yml.getInt(key + ".season");
            d.day = yml.getString(key + ".day", "");
            d.week = yml.getString(key + ".week", "");
            d.buyDay = yml.getString(key + ".buyDay", "");
            d.boughtToday = yml.getInt(key + ".boughtToday");
            readCounts(yml.getConfigurationSection(key + ".dayProgress"), d.dayProgress);
            d.dayDone.addAll(yml.getStringList(key + ".dayDone"));
            readCounts(yml.getConfigurationSection(key + ".progress"), d.progress);
            d.done.addAll(yml.getStringList(key + ".done"));
            data.put(id, d);
        }
    }

    private static void readCounts(ConfigurationSection s, Map<String, Integer> into) {
        if (s == null) return;
        for (String q : s.getKeys(false)) into.put(q, s.getInt(q));
    }

    /** Speichert alle 60 s, wenn sich etwas geändert hat (Aufgaben zählen bei jedem Block, Treffer usw.). */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save();
        }, 1200L, 1200L);
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // =================================================================================================================
    // Season, Tag, Woche
    // =================================================================================================================

    public static String dayKey() {
        return LocalDate.now(ZONE).toString();
    }

    public static String weekKey() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        return now.get(IsoFields.WEEK_BASED_YEAR) + "-W" + now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    /** Millisekunden bis zum nächsten Wechsel der Tages- bzw. Wochenaufgaben (Mitternacht, Montag). */
    public static long untilReset(Period period) {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime next = now.toLocalDate().plusDays(1).atStartOfDay(ZONE);
        if (period == Period.WEEKLY) {
            next = now.toLocalDate().with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).atStartOfDay(ZONE);
        }
        return Math.max(0, ChronoUnit.MILLIS.between(now, next));
    }

    /** {Nummer der laufenden Season, Ende in ms (oder -1 ohne season-start)}. */
    private long[] seasonInfo() {
        int first = cfg().getInt("battlepass.first-season", 1);
        int days = Math.max(1, cfg().getInt("battlepass.season-days", 60));
        String raw = cfg().getString("battlepass.season-start", "");
        LocalDate start;
        try {
            start = raw == null || raw.isBlank() ? null : LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            start = null;
        }
        if (start == null) return new long[]{first, -1};
        ZonedDateTime s = start.atStartOfDay(ZONE);
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        if (now.isBefore(s)) return new long[]{first, s.plusDays(days).toInstant().toEpochMilli()};
        long idx = ChronoUnit.DAYS.between(s, now) / days;
        return new long[]{first + idx, s.plusDays((idx + 1) * days).toInstant().toEpochMilli()};
    }

    public int season() {
        return (int) seasonInfo()[0];
    }

    /** Millisekunden bis zum Ende der Season, -1 ohne Ende. */
    public long seasonLeft() {
        long end = seasonInfo()[1];
        return end < 0 ? -1 : Math.max(0, end - System.currentTimeMillis());
    }

    // =================================================================================================================
    // Einstellungen
    // =================================================================================================================

    public int xpPerLevel() {
        return Math.max(1, cfg().getInt("battlepass.xp-per-level", 250));
    }

    public long premiumPrice() {
        return cfg().getLong("battlepass.premium-price", 1000);
    }

    public long levelPrice() {
        return Math.max(0, cfg().getLong("battlepass.level-price", 100));
    }

    public int levelDailyLimit() {
        return Math.max(0, cfg().getInt("battlepass.level-daily-limit", 999));
    }

    public int questCount(Period period) {
        return Math.max(0, cfg().getInt(period == Period.DAILY ? "battlepass.daily-count" : "battlepass.weekly-count",
                period == Period.DAILY ? 4 : 6));
    }

    // =================================================================================================================
    // Aufgaben
    // =================================================================================================================

    /** Alle Aufgaben eines Zeitraums aus der Config (in dieser Reihenfolge angezeigt). */
    public List<Quest> pool(Period period) {
        List<Quest> list = new ArrayList<>();
        String path = period == Period.DAILY ? "battlepass.quests-daily" : "battlepass.quests-weekly";
        for (String raw : cfg().getStringList(path)) {
            Quest q = parseQuest(raw, period);
            if (q != null) list.add(q);
        }
        // ältere Configs: battlepass.quests (ID;Name;Anzahl;Punkte) als Wochenaufgaben
        if (period == Period.WEEKLY && !cfg().isList(path)) {
            for (String raw : cfg().getStringList("battlepass.quests")) {
                String[] p = raw.split(";");
                if (p.length < 4) continue;
                try {
                    String id = p[0].trim();
                    String type = switch (id.toLowerCase(Locale.ROOT)) {
                        case "kill" -> "KILL:PLAYER";
                        default -> id.toUpperCase(Locale.ROOT);
                    };
                    String[] t = type.split(":", 2);
                    list.add(new Quest(id, p[1].trim(), "", "paper", t[0], t.length > 1 ? t[1] : "",
                            Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()), period, Set.of()));
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Ungültige Battlepass-Aufgabe: " + raw);
                }
            }
        }
        return list;
    }

    private Quest parseQuest(String raw, Period period) {
        String[] p = raw.split(";");
        if (p.length < 6) {
            plugin.getLogger().warning("Ungültige Battlepass-Aufgabe (zu wenig Felder): " + raw);
            return null;
        }
        try {
            String[] names = p[1].split("\\|", 2);
            String[] type = p[3].trim().toUpperCase(Locale.ROOT).split(":", 2);
            Set<String> modes = new HashSet<>();
            if (p.length > 6) {
                for (String m : p[6].split(",")) if (!m.isBlank()) modes.add(m.trim().toLowerCase(Locale.ROOT));
            }
            return new Quest(p[0].trim(), names[0].trim(), names.length > 1 ? names[1].trim() : "",
                    p[2].trim().toLowerCase(Locale.ROOT), type[0], type.length > 1 ? type[1] : "",
                    Math.max(1, Integer.parseInt(p[4].trim())), Math.max(0, Integer.parseInt(p[5].trim())), period, modes);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Ungültige Battlepass-Aufgabe: " + raw);
            return null;
        }
    }

    private record Cached(long at, List<Quest> quests) {}

    /** Auswahl je Zeitraum, Tag/Woche und Modus, 30 s zwischengespeichert (zählt bei jedem Block und Treffer). */
    private final Map<String, Cached> activeCache = new HashMap<>();

    /** Heute bzw. diese Woche aktive Aufgaben im Modus (für alle Spieler gleich, Reihenfolge wie im Pool). */
    public synchronized List<Quest> active(Period period, String mode) {
        String key = (period == Period.DAILY ? dayKey() : weekKey()) + "|" + mode;
        long now = System.currentTimeMillis();
        Cached c = activeCache.get(period + key);
        if (c != null && now - c.at() < 30_000) return c.quests();
        List<Quest> list = choose(period, mode, key);
        if (activeCache.size() > 64) activeCache.clear();
        activeCache.put(period + key, new Cached(now, list));
        return list;
    }

    private List<Quest> choose(Period period, String mode, String key) {
        List<Quest> pool = new ArrayList<>();
        for (Quest q : pool(period)) {
            if (q.modes().isEmpty() || q.modes().contains(mode)) pool.add(q);
        }
        int n = questCount(period);
        if (pool.size() <= n) return pool;
        List<Quest> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, new Random(key.hashCode() * 31L + period.ordinal()));
        Set<Quest> pick = new HashSet<>(shuffled.subList(0, n));
        pool.removeIf(q -> !pick.contains(q));
        return pool;
    }

    /** Aufgaben mit Stand für einen Spieler. */
    public synchronized List<QuestState> quests(UUID id, Period period) {
        Data d = data(id);
        Map<String, Integer> prog = period == Period.DAILY ? d.dayProgress : d.progress;
        Set<String> done = period == Period.DAILY ? d.dayDone : d.done;
        List<QuestState> out = new ArrayList<>();
        for (Quest q : active(period, plugin.modes().modeOf(id))) {
            boolean ok = done.contains(q.id());
            int v = prog.getOrDefault(q.id(), 0) / q.scale();
            out.add(new QuestState(q, ok ? q.target() : Math.min(v, q.target()), ok));
        }
        return out;
    }

    /**
     * Zählt für alle aktiven Aufgaben des Typs. tags: Ziele, die diese Handlung erfüllt (z. B. SPIDER, MONSTER);
     * eine Aufgabe ohne Ziel zählt immer.
     */
    public synchronized void count(UUID id, String type, long amount, String... tags) {
        if (amount <= 0) return;
        String mode = plugin.modes().modeOf(id);
        if (ModeService.HUB.equals(mode)) return;
        Data d = data(id);
        for (Period period : Period.values()) {
            Map<String, Integer> prog = period == Period.DAILY ? d.dayProgress : d.progress;
            Set<String> done = period == Period.DAILY ? d.dayDone : d.done;
            for (Quest q : active(period, mode)) {
                if (!q.type().equals(type) || done.contains(q.id()) || !matches(q.arg(), tags)) continue;
                long goal = (long) q.target() * q.scale();
                int value = (int) Math.min(goal, prog.getOrDefault(q.id(), 0) + amount);
                prog.put(q.id(), value);
                dirty = true;
                if (value >= goal) {
                    done.add(q.id());
                    completed(id, d, q);
                }
            }
        }
    }

    private static boolean matches(String arg, String[] tags) {
        if (arg.isEmpty()) return true;
        for (String t : tags) {
            if (arg.equals(t)) return true;
        }
        return false;
    }

    private void completed(UUID id, Data d, Quest q) {
        int before = level(d);
        d.xp += q.points();
        int after = level(d);
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        boolean en = plugin.playerSettings().english(id);
        Msg.send(p, (en ? "<gold>Quest done: <white>" : "<gold>Aufgabe geschafft: <white>") + Msg.escape(q.label(en))
                + " <gray>(+" + q.points() + (en ? " points)" : " Punkte)"));
        Sfx.play(plugin, p, Sfx.Kind.CHIME);
        if (after > before) {
            Msg.send(p, (en ? "<light_purple>Battle Pass level " : "<light_purple>Battle-Pass-Stufe ") + after
                    + (en ? " reached! <gray>Rewards: <white>/battlepass" : " erreicht! <gray>Belohnungen: <white>/battlepass"));
            Sfx.play(plugin, p, Sfx.Kind.LEVEL_UP);
        }
    }

    /** Ältere Aufrufe (SMP: kill, duel; Handel: trade; Tagesbelohnung: daily). */
    public void progress(UUID id, String key, int amount) {
        switch (key.toLowerCase(Locale.ROOT)) {
            case "kill" -> count(id, "KILL", amount, "PLAYER");
            case "duel" -> count(id, "DUEL", amount);
            case "trade" -> count(id, "TRADE", amount);
            case "daily" -> count(id, "DAILY", amount);
            default -> count(id, key.toUpperCase(Locale.ROOT), amount);
        }
    }

    // =================================================================================================================
    // Belohnungen
    // =================================================================================================================

    /** Alle Belohnungen. Ist die Liste in der config leer, gelten die Standardbelohnungen. */
    public List<Reward> rewards() {
        List<Reward> list = new ArrayList<>();
        for (String raw : cfg().getStringList("battlepass.rewards")) {
            String[] p = raw.split(";", 6);
            if (p.length < 4) continue;
            try {
                list.add(new Reward(Integer.parseInt(p[0].trim()), p[1].trim().toUpperCase(Locale.ROOT),
                        p[2].trim().toUpperCase(Locale.ROOT), p[3].trim(),
                        p.length > 4 ? p[4].trim().toLowerCase(Locale.ROOT) : "", p.length > 5 ? p[5].trim() : ""));
            } catch (NumberFormatException ignored) {
                plugin.getLogger().warning("Ungültige Battlepass-Belohnung: " + raw);
            }
        }
        return list.isEmpty() ? defaults() : list;
    }

    public List<Reward> rewardsAt(int level, String track) {
        return byLevel(track).getOrDefault(level, List.of());
    }

    /** Belohnungen einer Spur nach Stufe. */
    public Map<Integer, List<Reward>> byLevel(String track) {
        Map<Integer, List<Reward>> out = new HashMap<>();
        for (Reward r : rewards()) {
            if (r.track().equals(track)) out.computeIfAbsent(r.level(), k -> new ArrayList<>()).add(r);
        }
        return out;
    }

    /** Anzahl der Stufen mit Belohnung in einer Spur. */
    public int rewardLevels(String track) {
        return byLevel(track).size();
    }

    /** Premium-Gegenstände im Wechsel (Standard). */
    private static final String[] SPECIALS = {"IRON_PICKAXE", "GOLDEN_APPLE:4", "DIAMOND:8", "ENDER_PEARL:16",
            "DIAMOND_PICKAXE", "TOTEM_OF_UNDYING", "DIAMOND_SWORD", "NETHERITE_INGOT", "GOLDEN_APPLE:8",
            "NETHERITE_PICKAXE", "BLAZE_ROD:16", "SHULKER_SHELL:4", "ELYTRA"};

    /**
     * Standard: Free auf jeder zweiten Stufe (abwechselnd XP und ein Keyall-Schlüssel, alle 20 Stufen Juwelen),
     * Premium auf jeder Stufe im Wechsel XP, Schlüssel, XP, Schlüssel, Schlüssel, Gegenstand, XP; alle 10 Stufen
     * Juwelen, auf Stufe 100 fünf Netherite-Barren.
     */
    private List<Reward> defaults() {
        String keyCmd = cfg().getString("battlepass.key-command", "keys give %player% keyall 1");
        String keyName = cfg().getString("battlepass.key-name", "Keyall-Schlüssel");
        List<Reward> list = new ArrayList<>();
        for (int lvl = 1; lvl <= MAX_LEVEL; lvl++) {
            if (lvl % 20 == 0) {
                list.add(new Reward(lvl, FREE, "JEWELS", "10"));
            } else if (lvl % 4 == 2) {
                list.add(new Reward(lvl, FREE, "XP", String.valueOf(lvl * 20L)));
            } else if (lvl % 4 == 0) {
                list.add(new Reward(lvl, FREE, "COMMAND", keyCmd, "bp_key", keyName));
            }
            if (lvl == MAX_LEVEL) {
                list.add(new Reward(lvl, PREMIUM, "ITEM", "NETHERITE_INGOT:5"));
                list.add(new Reward(lvl, PREMIUM, "JEWELS", "100"));
            } else if (lvl % 10 == 0) {
                list.add(new Reward(lvl, PREMIUM, "JEWELS", "25"));
            } else {
                switch ((lvl - 1) % 7) {
                    case 0, 2, 6 -> list.add(new Reward(lvl, PREMIUM, "XP", String.valueOf(lvl * 30L)));
                    case 5 -> list.add(new Reward(lvl, PREMIUM, "ITEM", SPECIALS[((lvl - 1) / 7) % SPECIALS.length]));
                    default -> list.add(new Reward(lvl, PREMIUM, "COMMAND", keyCmd, "bp_key", keyName));
                }
            }
        }
        return list;
    }

    private void grant(Player p, Reward r) {
        UUID id = p.getUniqueId();
        try {
            switch (r.type()) {
                case "COINS" -> plugin.economy().deposit(id, Long.parseLong(r.value()));
                case "JEWELS" -> plugin.jewels().add(id, Long.parseLong(r.value()));
                case "XP" -> plugin.stats().get(id).xp += Long.parseLong(r.value());
                case "COMMAND" -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                        r.value().replace("%player%", p.getName()));
                case "ITEM" -> {
                    String[] parts = r.value().split(":");
                    Material m = Material.matchMaterial(parts[0].toUpperCase(Locale.ROOT));
                    int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                    if (m != null) {
                        p.getInventory().addItem(new ItemStack(m, amount)).values()
                                .forEach(rest -> plugin.abholbox().add(id, rest));
                    }
                }
                default -> plugin.getLogger().warning("Unbekannte Battlepass-Art: " + r.type());
            }
        } catch (NumberFormatException ex) {
            plugin.getLogger().warning("Ungültiger Wert in Battlepass-Belohnung: " + r.value());
        }
    }

    /** Anzeige eines Belohnungs-Eintrags (MiniMessage). */
    public static String describe(Reward r) {
        return describe(r, false);
    }

    public static String describe(Reward r, boolean en) {
        if (!r.name().isEmpty()) return "<white>" + Msg.escape(r.name());
        return switch (r.type()) {
            case "COINS" -> "<gold>" + Msg.coins(parse(r.value())) + "$";
            case "XP" -> "<green>" + Msg.coins(parse(r.value())) + " XP";
            case "JEWELS" -> "<aqua>" + Msg.coins(parse(r.value())) + (en ? " jewels" : " Juwelen");
            case "COMMAND" -> en ? "<light_purple>Special reward" : "<light_purple>Spezial-Belohnung";
            case "ITEM" -> {
                String[] parts = r.value().split(":");
                String n = parts[0].toLowerCase(Locale.ROOT).replace('_', ' ');
                yield "<white>" + (parts.length > 1 ? parts[1] + "x " : "") + n;
            }
            default -> "<gray>" + r.value();
        };
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static Material iconOf(Reward r) {
        return switch (r.type()) {
            case "COINS" -> Material.GOLD_NUGGET;
            case "XP" -> Material.EXPERIENCE_BOTTLE;
            case "JEWELS" -> Material.EMERALD;
            case "COMMAND" -> Material.TRIPWIRE_HOOK;
            case "ITEM" -> {
                Material m = Material.matchMaterial(r.value().split(":")[0].toUpperCase(Locale.ROOT));
                yield m == null ? Material.CHEST : m;
            }
            default -> Material.PAPER;
        };
    }

    // =================================================================================================================
    // Stufen, Spuren, Kauf
    // =================================================================================================================

    private Data data(UUID id) {
        Data d = data.computeIfAbsent(id, k -> new Data());
        int season = season();
        if (d.season == 0) {
            d.season = season;               // Stand von vor den Seasons übernehmen
        } else if (d.season != season) {
            d.season = season;
            d.xp = 0;
            d.bought = 0;
            d.claimedFree = 0;
            d.claimedPremium = 0;
            d.premium = false;
            d.dayProgress.clear();
            d.dayDone.clear();
            d.progress.clear();
            d.done.clear();
            dirty = true;
        }
        String day = dayKey();
        if (!day.equals(d.day)) {
            d.day = day;
            d.dayProgress.clear();
            d.dayDone.clear();
        }
        String wk = weekKey();
        if (!wk.equals(d.week)) {
            d.week = wk;
            d.progress.clear();
            d.done.clear();
        }
        if (!day.equals(d.buyDay)) {
            d.buyDay = day;
            d.boughtToday = 0;
        }
        return d;
    }

    private int level(Data d) {
        return (int) Math.min(MAX_LEVEL, d.xp / xpPerLevel() + d.bought);
    }

    public synchronized long xp(UUID id) {
        return data(id).xp;
    }

    public synchronized int level(UUID id) {
        return level(data(id));
    }

    /** Punkte in der laufenden Stufe (0 auf der höchsten Stufe). */
    public synchronized long pointsInLevel(UUID id) {
        Data d = data(id);
        return level(d) >= MAX_LEVEL ? 0 : d.xp % xpPerLevel();
    }

    public synchronized boolean hasPremium(UUID id) {
        return data(id).premium;
    }

    public synchronized int claimedOf(UUID id, String track) {
        Data d = data(id);
        return PREMIUM.equals(track) ? d.claimedPremium : d.claimedFree;
    }

    /** Erreichte, noch nicht abgeholte Stufen mit Belohnung (Premium nur mit Premium-Pass). */
    public synchronized int claimable(UUID id, String track) {
        Data d = data(id);
        if (PREMIUM.equals(track) && !d.premium) return 0;
        int from = PREMIUM.equals(track) ? d.claimedPremium : d.claimedFree;
        Map<Integer, List<Reward>> all = byLevel(track);
        int n = 0;
        for (int lvl = from + 1; lvl <= level(d); lvl++) {
            if (all.containsKey(lvl)) n++;
        }
        return n;
    }

    /** Heute noch kaufbare Stufen. */
    public synchronized int levelsLeftToday(UUID id) {
        Data d = data(id);
        return Math.max(0, Math.min(levelDailyLimit() - d.boughtToday, MAX_LEVEL - level(d)));
    }

    /** Gibt null bei Erfolg zurück. */
    public synchronized String buyPremium(Player p) {
        UUID id = p.getUniqueId();
        Data d = data(id);
        boolean en = plugin.playerSettings().english(id);
        if (d.premium) return en ? "You already have the premium pass." : "Du hast den Premium-Pass schon.";
        long price = premiumPrice();
        if (!plugin.jewels().take(id, price)) {
            return en ? "You need " + Msg.coins(price) + " jewels for the premium pass."
                    : "Du brauchst " + Msg.coins(price) + " Juwelen für den Premium-Pass.";
        }
        d.premium = true;
        save();
        return null;
    }

    /** Schaltet den Premium-Pass frei (Juwelenshop, auch als Geschenk). False, wenn er schon freigeschaltet ist. */
    public synchronized boolean grantPremium(UUID id) {
        Data d = data(id);
        if (d.premium) return false;
        d.premium = true;
        save();
        return true;
    }

    /** Kauft n Stufen für Juwelen (ohne Punkte). Gibt null bei Erfolg zurück. */
    public synchronized String buyLevels(Player p, int n) {
        UUID id = p.getUniqueId();
        Data d = data(id);
        boolean en = plugin.playerSettings().english(id);
        if (n < 1) return en ? "Choose at least one level." : "Wähle mindestens eine Stufe.";
        if (level(d) >= MAX_LEVEL) return en ? "You are already at the highest level." : "Du hast schon die höchste Stufe.";
        if (n > levelsLeftToday(id)) {
            return en ? "You can buy " + levelsLeftToday(id) + " more levels today."
                    : "Heute kannst du noch " + levelsLeftToday(id) + " Stufen kaufen.";
        }
        long price = levelPrice() * n;
        if (!plugin.jewels().take(id, price)) {
            long missing = price - plugin.jewels().balance(id);
            return en ? "You are missing " + Msg.coins(missing) + " jewels." : "Dir fehlen " + Msg.coins(missing) + " Juwelen.";
        }
        d.bought += n;
        d.boughtToday += n;
        save();
        return null;
    }

    /** Holt alle erreichten, noch nicht abgeholten Belohnungen einer Spur. Gibt die Anzahl der Stufen zurück. */
    public synchronized int claimTrack(Player p, String track) {
        UUID id = p.getUniqueId();
        Data d = data(id);
        if (PREMIUM.equals(track) && !d.premium) return 0;
        int level = level(d);
        int from = PREMIUM.equals(track) ? d.claimedPremium : d.claimedFree;
        Map<Integer, List<Reward>> all = byLevel(track);
        int count = 0;
        for (int lvl = from + 1; lvl <= level; lvl++) {
            List<Reward> rs = all.getOrDefault(lvl, List.of());
            for (Reward r : rs) grant(p, r);
            if (!rs.isEmpty()) count++;
        }
        if (PREMIUM.equals(track)) {
            d.claimedPremium = Math.max(d.claimedPremium, level);
        } else {
            d.claimedFree = Math.max(d.claimedFree, level);
        }
        if (level > from) save();
        return count;
    }

    // =================================================================================================================
    // Speichern und Modus-Daten
    // =================================================================================================================

    public synchronized void save() {
        dirty = false;
        YamlConfiguration out = new YamlConfiguration();
        data.forEach((id, d) -> {
            String k = id.toString();
            out.set(k + ".xp", d.xp);
            out.set(k + ".bought", d.bought);
            out.set(k + ".claimedFree", d.claimedFree);
            out.set(k + ".claimedPremium", d.claimedPremium);
            out.set(k + ".premium", d.premium);
            out.set(k + ".season", d.season);
            out.set(k + ".day", d.day);
            out.set(k + ".week", d.week);
            out.set(k + ".buyDay", d.buyDay);
            out.set(k + ".boughtToday", d.boughtToday);
            d.dayProgress.forEach((q, v) -> out.set(k + ".dayProgress." + q, v));
            out.set(k + ".dayDone", new ArrayList<>(d.dayDone));
            d.progress.forEach((q, v) -> out.set(k + ".progress." + q, v));
            out.set(k + ".done", new ArrayList<>(d.done));
        });
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("battlepass.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    @Override
    public String key() { return "battlepass"; }

    @Override
    public synchronized Map<String, Object> export(UUID id) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        Data d = data.get(id);
        if (d == null) return m;
        m.put("xp", d.xp);
        m.put("bought", d.bought);
        m.put("claimedFree", d.claimedFree);
        m.put("claimedPremium", d.claimedPremium);
        m.put("premium", d.premium);
        m.put("season", d.season);
        m.put("day", d.day);
        m.put("week", d.week);
        m.put("buyDay", d.buyDay);
        m.put("boughtToday", d.boughtToday);
        d.dayProgress.forEach((q, v) -> m.put("dayProgress|" + q, v));
        m.put("dayDone", new ArrayList<>(d.dayDone));
        d.progress.forEach((q, v) -> m.put("progress|" + q, v));
        m.put("done", new ArrayList<>(d.done));
        return m;
    }

    @Override
    public synchronized void importData(UUID id, Map<String, Object> m) {
        if (m == null || m.isEmpty()) {
            data.remove(id);
        } else {
            Data d = new Data();
            d.xp = ModeData.lng(m.get("xp"));
            d.bought = ModeData.integer(m.get("bought"));
            d.claimedFree = ModeData.integer(m.get("claimedFree"));
            d.claimedPremium = ModeData.integer(m.get("claimedPremium"));
            d.premium = Boolean.TRUE.equals(m.get("premium"));
            d.season = ModeData.integer(m.get("season"));
            d.day = string(m.get("day"));
            d.week = string(m.get("week"));
            d.buyDay = string(m.get("buyDay"));
            d.boughtToday = ModeData.integer(m.get("boughtToday"));
            m.forEach((k, v) -> {
                if (k.startsWith("dayProgress|")) d.dayProgress.put(k.substring(12), ModeData.integer(v));
                if (k.startsWith("progress|")) d.progress.put(k.substring(9), ModeData.integer(v));
            });
            strings(m.get("dayDone"), d.dayDone);
            strings(m.get("done"), d.done);
            data.put(id, d);
        }
        save();
    }

    private static String string(Object o) {
        return o == null ? "" : o.toString();
    }

    private static void strings(Object o, Set<String> into) {
        if (o instanceof List<?> l) {
            for (Object x : l) into.add(String.valueOf(x));
        }
    }
}

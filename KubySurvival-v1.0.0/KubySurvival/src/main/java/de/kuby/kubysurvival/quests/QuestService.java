package de.kuby.kubysurvival.quests;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.service.TeamService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.jobs.Job;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Survival-Quests: täglich, wöchentlich, lange Geschichte (Kapitel nacheinander), Meilensteine, Team-Quests
 * (eine pro Woche und Team) und geheime Quests. Fertige Quests werden im Menü abgeholt. Wer an aufeinanderfolgenden
 * Tagen eine Tages-Quest abholt, baut eine Serie auf. Die verfolgte Quest steht oben am Bildschirm (SurvivalHud).
 * Definitionen in quests.yml, Spielerdaten in questdata.yml.
 *
 * Schlüssel einer Quest: "d:" täglich, "w:" wöchentlich, "s:" Geschichte, "x:" geheim, "t:" Team, "m:id:Stufe" Meilenstein.
 */
public final class QuestService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    /** Eine Quest aus quests.yml. */
    public record Def(String id, String name, String task, String type, String arg, long amount, long coins, long tokens, long xp) {
        public Material icon() {
            return switch (type) {
                case "break" -> arg == null ? Material.IRON_PICKAXE : switch (arg) {
                    case "logs" -> Material.OAK_LOG;
                    case "stones" -> Material.STONE;
                    case "crops" -> Material.WHEAT;
                    case "dig" -> Material.IRON_SHOVEL;
                    default -> arg.startsWith("ores") ? Material.IRON_ORE : Material.IRON_PICKAXE;
                };
                case "kill" -> Material.IRON_SWORD;
                case "fish" -> Material.FISHING_ROD;
                case "breed" -> Material.WHEAT;
                case "shear" -> Material.SHEARS;
                case "enchant" -> Material.ENCHANTED_BOOK;
                case "craft" -> Material.CRAFTING_TABLE;
                case "smelt" -> Material.FURNACE;
                case "eat" -> Material.COOKED_BEEF;
                case "trade" -> Material.EMERALD;
                case "jobxp" -> Material.EXPERIENCE_BOTTLE;
                case "playtime" -> Material.CLOCK;
                default -> Material.NETHER_STAR;
            };
        }
    }

    /** Meilenstein mit Stufen. */
    public record Mile(String id, String name, String stat, long[] tiers, long coins, long tokens) {}

    /** Was oben am Bildschirm steht. */
    public record Track(String key, Def def, long progress) {}

    /** Daten eines Spielers. */
    static final class Data {
        String day = "";
        String week = "";
        List<String> daily = new ArrayList<>();
        List<String> weekly = new ArrayList<>();
        final Map<String, Long> progress = new HashMap<>();
        final Set<String> done = new HashSet<>();
        final Set<String> claimed = new HashSet<>();
        int story;
        int streak;
        int bestStreak;
        String streakDay = "";
        long total;
        /** null: automatisch (Geschichte), "-": aus. */
        String tracked;
    }

    /** Team-Quest einer Woche. */
    static final class TeamQuest {
        String week = "";
        String id = "";
        long progress;
    }

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File defFile;
    private final File dataFile;
    private final Map<String, Def> daily = new LinkedHashMap<>();
    private final Map<String, Def> weekly = new LinkedHashMap<>();
    private final List<Def> story = new ArrayList<>();
    private final Map<String, Def> secret = new LinkedHashMap<>();
    private final Map<String, Def> team = new LinkedHashMap<>();
    private final List<Mile> milestones = new ArrayList<>();
    private final Map<Integer, Long> streakRewards = new HashMap<>();
    private int dailyCount = 3;
    /** Zusätzliche Tages-Quests mit Farmpass. */
    private int farmpassDaily = 1;
    private int weeklyCount = 3;
    private long streakBonus = 25;
    private final Map<UUID, Data> players = new HashMap<>();
    private final Map<String, TeamQuest> teams = new HashMap<>();

    public QuestService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.defFile = new File(addon.getDataFolder(), "quests.yml");
        this.dataFile = new File(addon.getDataFolder(), "questdata.yml");
        if (!defFile.exists()) addon.saveResource("quests.yml", false);
        loadDefs();
        loadData();
    }

    // ---------- Definitionen ----------

    public synchronized void loadDefs() {
        daily.clear();
        weekly.clear();
        story.clear();
        secret.clear();
        team.clear();
        milestones.clear();
        streakRewards.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(defFile);
        dailyCount = Math.max(1, yml.getInt("daily-count", 3));
        farmpassDaily = Math.max(0, Math.min(4, yml.getInt("farmpass-extra-daily", 1)));
        weeklyCount = Math.max(1, yml.getInt("weekly-count", 3));
        streakBonus = yml.getLong("streak.bonus-coins-per-day", 25);
        for (String raw : yml.getStringList("streak.rewards")) {
            String[] p = raw.split(";");
            try {
                streakRewards.put(Integer.parseInt(p[0].trim()), Long.parseLong(p[1].trim()));
            } catch (RuntimeException e) {
                addon.getLogger().warning("quests.yml: Serie ungültig: " + raw);
            }
        }
        for (Def d : defs(yml, "daily")) daily.put(d.id(), d);
        for (Def d : defs(yml, "weekly")) weekly.put(d.id(), d);
        story.addAll(defs(yml, "story"));
        for (Def d : defs(yml, "secret")) secret.put(d.id(), d);
        for (Def d : defs(yml, "team")) team.put(d.id(), d);
        for (String raw : yml.getStringList("milestones")) {
            String[] p = raw.split(";");
            try {
                String[] t = p[3].split(",");
                long[] tiers = new long[t.length];
                for (int i = 0; i < t.length; i++) tiers[i] = Long.parseLong(t[i].trim());
                milestones.add(new Mile(p[0].trim(), p[1].trim(), p[2].trim(), tiers, Long.parseLong(p[4].trim()),
                        Long.parseLong(p[5].trim())));
            } catch (RuntimeException e) {
                addon.getLogger().warning("quests.yml: Meilenstein ungültig: " + raw);
            }
        }
    }

    private List<Def> defs(YamlConfiguration yml, String path) {
        List<Def> out = new ArrayList<>();
        for (String raw : yml.getStringList(path)) {
            String[] p = raw.split(";");
            try {
                String[] type = p[3].trim().split(":", 2);
                out.add(new Def(p[0].trim(), p[1].trim(), p[2].trim(), type[0].toLowerCase(), type.length > 1 ? type[1].trim() : null,
                        Long.parseLong(p[4].trim()), Long.parseLong(p[5].trim()), Long.parseLong(p[6].trim()), Long.parseLong(p[7].trim())));
            } catch (RuntimeException e) {
                addon.getLogger().warning("quests.yml: Quest ungültig (" + path + "): " + raw);
            }
        }
        return out;
    }

    public List<Mile> milestones() {
        return milestones;
    }

    public synchronized int dailyCount() {
        return dailyCount;
    }

    public synchronized int farmpassExtraDaily() {
        return farmpassDaily;
    }

    // ---------- Zeit ----------

    static String today() {
        return LocalDate.now(ZONE).toString();
    }

    static String week() {
        LocalDate d = LocalDate.now(ZONE);
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    // ---------- Spielerdaten ----------

    synchronized Data data(UUID id) {
        Data d = players.computeIfAbsent(id, k -> new Data());
        String today = today();
        if (!today.equals(d.day)) {
            payLeftovers(id, d, "d:");
            d.day = today;
            d.daily = pick(daily, dailyCount, id.hashCode() * 31L + today.hashCode());
        }
        // Farmpass: weitere Tages-Quests (gleicher Zufall, die ersten bleiben dieselben; auch wenn der Pass mitten am
        // Tag dazukommt)
        int want = dailyCount + (addon.farmpass() != null && addon.farmpass().active(id) ? farmpassDaily : 0);
        if (d.daily.size() < want) {
            for (String q : pick(daily, want, id.hashCode() * 31L + today.hashCode())) {
                if (d.daily.size() >= want) break;
                if (!d.daily.contains(q)) d.daily.add(q);
            }
        }
        String week = week();
        if (!week.equals(d.week)) {
            payLeftovers(id, d, "w:");
            payLeftovers(id, d, "t:");
            d.week = week;
            d.weekly = pick(weekly, weeklyCount, id.hashCode() * 17L + week.hashCode());
        }
        return d;
    }

    private static List<String> pick(Map<String, Def> pool, int count, long seed) {
        List<String> ids = new ArrayList<>(pool.keySet());
        Collections.shuffle(ids, new Random(seed));
        return new ArrayList<>(ids.subList(0, Math.min(count, ids.size())));
    }

    /** Fertige, aber nicht abgeholte Quests eines abgelaufenen Zeitraums werden still ausgezahlt; der Rest verfällt. */
    private void payLeftovers(UUID id, Data d, String prefix) {
        for (String key : new ArrayList<>(d.done)) {
            if (!key.startsWith(prefix)) continue;
            if (!d.claimed.contains(key)) {
                Def def = def(d, key);
                if (def != null) {
                    pay(id, def.coins(), def.tokens(), def.xp());
                    d.total++;
                }
            }
            d.done.remove(key);
        }
        d.claimed.removeIf(k -> k.startsWith(prefix));
        d.progress.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** Definition zu einem Schlüssel. */
    synchronized Def def(Data d, String key) {
        String id = key.substring(2);
        return switch (key.substring(0, 2)) {
            case "d:" -> daily.get(id);
            case "w:" -> weekly.get(id);
            case "x:" -> secret.get(id);
            case "t:" -> team.get(id);
            case "s:" -> {
                for (Def s : story) if (s.id().equals(id)) yield s;
                yield null;
            }
            default -> null;
        };
    }

    synchronized List<Def> dailies(UUID id) {
        List<Def> out = new ArrayList<>();
        for (String q : data(id).daily) if (daily.containsKey(q)) out.add(daily.get(q));
        return out;
    }

    synchronized List<Def> weeklies(UUID id) {
        List<Def> out = new ArrayList<>();
        for (String q : data(id).weekly) if (weekly.containsKey(q)) out.add(weekly.get(q));
        return out;
    }

    /** Aktuelles Kapitel der Geschichte oder null, wenn alles geschafft ist. */
    synchronized Def storyChapter(UUID id) {
        Data d = data(id);
        return d.story < story.size() ? story.get(d.story) : null;
    }

    synchronized int storyIndex(UUID id) {
        return data(id).story;
    }

    int storySize() {
        return story.size();
    }

    synchronized List<Def> secretsFound(UUID id) {
        Data d = data(id);
        List<Def> out = new ArrayList<>();
        for (Def s : secret.values()) if (d.done.contains("x:" + s.id())) out.add(s);
        return out;
    }

    int secretCount() {
        return secret.size();
    }

    synchronized long progressOf(UUID id, String key) {
        if (key.startsWith("t:")) {
            TeamQuest tq = teamQuest(id);
            return tq == null ? 0 : tq.progress;
        }
        return data(id).progress.getOrDefault(key, 0L);
    }

    synchronized boolean isDone(UUID id, String key) {
        if (key.startsWith("t:")) {
            TeamQuest tq = teamQuest(id);
            Def def = tq == null ? null : team.get(tq.id);
            return def != null && tq.progress >= def.amount();
        }
        return data(id).done.contains(key);
    }

    synchronized boolean isClaimed(UUID id, String key) {
        return data(id).claimed.contains(key);
    }

    /** Aktuelle Serie (0, wenn gestern keine Tages-Quest abgeholt wurde). */
    public synchronized int streak(UUID id) {
        Data d = data(id);
        String today = today();
        String yesterday = LocalDate.now(ZONE).minusDays(1).toString();
        return today.equals(d.streakDay) || yesterday.equals(d.streakDay) ? d.streak : 0;
    }

    public synchronized int bestStreak(UUID id) {
        return data(id).bestStreak;
    }

    public synchronized long total(UUID id) {
        return data(id).total;
    }

    Map<Integer, Long> streakRewards() {
        return streakRewards;
    }

    long streakBonus() {
        return streakBonus;
    }

    // ---------- Team ----------

    private TeamService.Team survivalTeam(UUID id) {
        TeamService.Team t = core.teams().of(id);
        return t != null && ModeService.SURVIVAL.equals(t.mode()) ? t : null;
    }

    /** Team-Quest der Woche für das Team des Spielers (null ohne Survival-Team). */
    synchronized TeamQuest teamQuest(UUID id) {
        TeamService.Team t = survivalTeam(id);
        if (t == null || team.isEmpty()) return null;
        String key = (t.mode() + "_" + t.name().toLowerCase()).replaceAll("[^a-z0-9_-]", "_");
        TeamQuest tq = teams.computeIfAbsent(key, k -> new TeamQuest());
        String week = week();
        if (!week.equals(tq.week) || !team.containsKey(tq.id)) {
            tq.week = week;
            tq.progress = 0;
            tq.id = pick(team, 1, key.hashCode() * 13L + week.hashCode()).get(0);
        }
        return tq;
    }

    synchronized Def teamDef(UUID id) {
        TeamQuest tq = teamQuest(id);
        return tq == null ? null : team.get(tq.id);
    }

    // ---------- Fortschritt ----------

    private boolean survival(Player p) {
        return ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()));
    }

    /**
     * Meldet eine Aktion. subject ist je nach Typ ein Material (break, craft, smelt, eat), eine Entity (kill),
     * ein Job (jobxp) oder null.
     */
    public synchronized void progress(Player p, String type, Object subject, long amount) {
        if (amount <= 0 || !survival(p)) return;
        UUID id = p.getUniqueId();
        if (addon.sideQuests() != null) addon.sideQuests().progress(p, type, subject, amount);
        Data d = data(id);
        for (String q : d.daily) advance(p, d, "d:", daily.get(q), type, subject, amount);
        for (String q : d.weekly) advance(p, d, "w:", weekly.get(q), type, subject, amount);
        if (d.story < story.size()) advance(p, d, "s:", story.get(d.story), type, subject, amount);
        for (Def s : secret.values()) advance(p, d, "x:", s, type, subject, amount);

        TeamQuest tq = teamQuest(id);
        Def td = tq == null ? null : team.get(tq.id);
        if (td != null && tq.progress < td.amount() && matches(td, type, subject)) {
            tq.progress = Math.min(td.amount(), tq.progress + amount);
            if (tq.progress >= td.amount()) {
                TeamService.Team t = survivalTeam(id);
                if (t != null) {
                    for (UUID m : t.members()) {
                        Player mp = Bukkit.getPlayer(m);
                        if (mp == null) continue;
                        Msg.send(mp, "<gold><b>Team-Quest geschafft:</b> <white>" + Msg.escape(td.name())
                                + " <click:run_command:'/quest abholen'><green>[Belohnung abholen]</click>");
                        mp.playSound(mp.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
                    }
                }
            }
        }
    }

    private void advance(Player p, Data d, String prefix, Def q, String type, Object subject, long amount) {
        if (q == null || "stat".equals(q.type())) return;
        String key = prefix + q.id();
        if (d.done.contains(key) || !matches(q, type, subject)) return;
        long v = Math.min(q.amount(), d.progress.getOrDefault(key, 0L) + amount);
        d.progress.put(key, v);
        if (v >= q.amount()) finish(p, d, key, q);
    }

    private void finish(Player p, Data d, String key, Def q) {
        d.done.add(key);
        if (key.startsWith("x:")) {
            Msg.send(p, "<light_purple><b>Geheime Quest entdeckt:</b> <white>" + Msg.escape(q.name()) + " <gray>– " + Msg.escape(q.task()));
        } else {
            Msg.send(p, "<green><b>Quest geschafft:</b> <white>" + Msg.escape(q.name()));
        }
        Msg.send(p, "<click:run_command:'/quest abholen'><gold>[Belohnung abholen]</click> <gray>oder im Menü <white>/quest");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.3f);
        if (key.equals(d.tracked)) d.tracked = null;
    }

    public static boolean matches(Def q, String type, Object subject) {
        if (!q.type().equals(type)) return false;
        String arg = q.arg();
        if (arg == null || arg.equals("*")) return true;
        return switch (type) {
            case "break" -> subject instanceof Material m && blockMatches(arg, m);
            case "kill" -> subject instanceof Entity e && (arg.equalsIgnoreCase("monster") ? e instanceof Monster
                    : arg.equalsIgnoreCase("animal") ? e instanceof Animals : e.getType().name().equalsIgnoreCase(arg));
            case "jobxp" -> subject instanceof Job j && (j.name().equalsIgnoreCase(arg) || j.ascii.equalsIgnoreCase(arg));
            default -> subject instanceof Material m && m.name().equalsIgnoreCase(arg);
        };
    }

    private static boolean blockMatches(String arg, Material m) {
        String n = m.name();
        return switch (arg.toLowerCase()) {
            case "logs" -> Tag.LOGS.isTagged(m);
            case "ores" -> n.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
            case "stones" -> n.equals("STONE") || n.equals("DEEPSLATE") || n.equals("ANDESITE") || n.equals("DIORITE")
                    || n.equals("GRANITE") || n.equals("TUFF") || n.equals("NETHERRACK") || n.equals("BLACKSTONE")
                    || n.equals("BASALT") || n.equals("END_STONE") || n.equals("CALCITE");
            case "crops" -> Tag.CROPS.isTagged(m) || m == Material.NETHER_WART || m == Material.COCOA || m == Material.MELON
                    || m == Material.PUMPKIN || m == Material.SUGAR_CANE || m == Material.CACTUS || m == Material.BAMBOO;
            case "dig" -> n.contains("DIRT") || n.contains("SAND") || n.equals("GRAVEL") || n.equals("CLAY") || n.equals("MUD")
                    || n.equals("GRASS_BLOCK") || n.equals("PODZOL") || n.equals("MYCELIUM") || n.equals("SOUL_SOIL")
                    || n.equals("SNOW_BLOCK");
            default -> arg.toLowerCase().startsWith("ores_")
                    ? n.endsWith(arg.substring(5).toUpperCase() + "_ORE") : n.equalsIgnoreCase(arg);
        };
    }

    /** Werte-Quests (stat:...) prüfen. Läuft jede Minute und beim Öffnen des Menüs. */
    public synchronized void checkStats(Player p) {
        if (!survival(p)) return;
        UUID id = p.getUniqueId();
        Data d = data(id);
        List<Map.Entry<String, Def>> all = new ArrayList<>();
        for (String q : d.daily) if (daily.containsKey(q)) all.add(Map.entry("d:" + q, daily.get(q)));
        for (String q : d.weekly) if (weekly.containsKey(q)) all.add(Map.entry("w:" + q, weekly.get(q)));
        if (d.story < story.size()) all.add(Map.entry("s:" + story.get(d.story).id(), story.get(d.story)));
        for (Def s : secret.values()) all.add(Map.entry("x:" + s.id(), s));
        for (Map.Entry<String, Def> e : all) {
            Def q = e.getValue();
            if (!"stat".equals(q.type()) || q.arg() == null || d.done.contains(e.getKey())) continue;
            long v = Math.min(q.amount(), stat(q.arg(), id));
            d.progress.put(e.getKey(), v);
            if (v >= q.amount()) finish(p, d, e.getKey(), q);
        }
    }

    long stat(String key, UUID id) {
        return switch (key) {
            case "quests_done" -> total(id);
            case "quest_streak_best" -> bestStreak(id);
            default -> core.hasStat(key) ? core.statValue(key, id) : 0;
        };
    }

    // ---------- Abholen ----------

    /** Holt eine fertige Quest ab. Gibt eine Fehlermeldung zurück oder null. */
    public synchronized String claim(Player p, String key) {
        UUID id = p.getUniqueId();
        Data d = data(id);
        if (key.startsWith("m:")) return claimMilestone(p, key);
        Def def = key.startsWith("t:") ? teamDef(id) : def(d, key);
        if (def == null) return "Diese Quest gibt es nicht mehr.";
        if (key.startsWith("t:") && !key.equals("t:" + def.id())) return "Diese Team-Quest ist vorbei.";
        if (!isDone(id, key)) return "Diese Quest ist noch nicht fertig.";
        if (d.claimed.contains(key)) return "Die Belohnung hast du schon abgeholt.";
        d.claimed.add(key);
        if (key.startsWith("t:")) d.done.add(key);
        d.total++;
        long coins = def.coins();
        if (key.startsWith("d:")) coins += streakStep(p, d);
        pay(id, coins, def.tokens(), def.xp());
        Msg.send(p, "<green>Belohnung für <white>" + Msg.escape(def.name()) + "<green>: " + rewardText(coins, def.tokens(), def.xp()));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        if (key.startsWith("s:")) {
            d.story++;
            d.done.remove(key);
            d.claimed.remove(key);
            d.progress.remove(key);
            Def next = d.story < story.size() ? story.get(d.story) : null;
            if (next != null) {
                Msg.send(p, "<gold>Neues Kapitel: <white>" + Msg.escape(next.name()) + " <gray>– " + Msg.escape(next.task()));
                checkStats(p);
            } else {
                Msg.send(p, "<gold><b>Die Geschichte ist geschafft!</b> <gray>Danke fürs Mitspielen.");
            }
        }
        return null;
    }

    /** Serie weiterzählen (erste Tages-Quest des Tages). Gibt die Extra-Coins zurück. */
    private long streakStep(Player p, Data d) {
        String today = today();
        if (today.equals(d.streakDay)) return 0;
        String yesterday = LocalDate.now(ZONE).minusDays(1).toString();
        d.streak = yesterday.equals(d.streakDay) ? d.streak + 1 : 1;
        d.streakDay = today;
        d.bestStreak = Math.max(d.bestStreak, d.streak);
        long bonus = streakBonus * Math.min(30, d.streak);
        Msg.send(p, "<gold>Serie: <white>" + d.streak + (d.streak == 1 ? " Tag" : " Tage") + " <gray>(+" + Msg.coins(bonus) + " Coins Extra)");
        Long tokens = streakRewards.get(d.streak);
        if (tokens != null && tokens > 0) {
            addon.jobs().addTokens(p.getUniqueId(), tokens);
            Msg.send(p, "<light_purple><b>" + d.streak + " Tage am Stück!</b> +" + tokens + " Tokens");
        }
        return bonus;
    }

    private String claimMilestone(Player p, String key) {
        String[] parts = key.split(":");
        if (parts.length != 3) return "Unbekannter Meilenstein.";
        Mile m = null;
        for (Mile c : milestones) if (c.id().equals(parts[1])) m = c;
        if (m == null) return "Diesen Meilenstein gibt es nicht mehr.";
        int tier;
        try {
            tier = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return "Unbekannte Stufe.";
        }
        if (tier < 1 || tier > m.tiers().length) return "Unbekannte Stufe.";
        UUID id = p.getUniqueId();
        Data d = data(id);
        if (d.claimed.contains(key)) return "Die Belohnung hast du schon abgeholt.";
        if (stat(m.stat(), id) < m.tiers()[tier - 1]) return "Diese Stufe hast du noch nicht erreicht.";
        d.claimed.add(key);
        long coins = m.coins() * tier;
        long tokens = m.tokens() * tier;
        pay(id, coins, tokens, 0);
        Msg.send(p, "<green>Meilenstein <white>" + Msg.escape(m.name()) + " " + roman(tier) + "<green>: " + rewardText(coins, tokens, 0));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        return null;
    }

    /** Nächste offene Stufe eines Meilensteins (1-basiert), oder tiers.length + 1, wenn alle abgeholt sind. */
    synchronized int nextTier(UUID id, Mile m) {
        Data d = data(id);
        int t = 1;
        while (t <= m.tiers().length && d.claimed.contains("m:" + m.id() + ":" + t)) t++;
        return t;
    }

    /** Alle abholbaren Schlüssel in fester Reihenfolge. */
    public synchronized List<String> claimable(UUID id) {
        Data d = data(id);
        List<String> out = new ArrayList<>();
        for (String q : d.daily) if (d.done.contains("d:" + q) && !d.claimed.contains("d:" + q)) out.add("d:" + q);
        for (String q : d.weekly) if (d.done.contains("w:" + q) && !d.claimed.contains("w:" + q)) out.add("w:" + q);
        if (d.story < story.size() && d.done.contains("s:" + story.get(d.story).id())) out.add("s:" + story.get(d.story).id());
        for (Def s : secret.values()) if (d.done.contains("x:" + s.id()) && !d.claimed.contains("x:" + s.id())) out.add("x:" + s.id());
        Def td = teamDef(id);
        if (td != null && isDone(id, "t:" + td.id()) && !d.claimed.contains("t:" + td.id())) out.add("t:" + td.id());
        for (Mile m : milestones) {
            int t = nextTier(id, m);
            if (t <= m.tiers().length && stat(m.stat(), id) >= m.tiers()[t - 1]) out.add("m:" + m.id() + ":" + t);
        }
        return out;
    }

    /** Holt alles Fertige ab. Gibt die Anzahl zurück. */
    public synchronized int claimAll(Player p) {
        int n = 0;
        // Geschichte und Meilensteine können nach dem Abholen gleich wieder etwas freigeben.
        for (int round = 0; round < 20; round++) {
            List<String> keys = claimable(p.getUniqueId());
            if (keys.isEmpty()) break;
            for (String k : keys) if (claim(p, k) == null) n++;
        }
        return n;
    }

    private void pay(UUID id, long coins, long tokens, long xp) {
        if (coins > 0) core.modes().depositIn(id, ModeService.SURVIVAL, coins);
        if (tokens > 0) addon.jobs().addTokens(id, tokens);
        if (xp > 0) addon.jobs().addPlayerXp(id, xp);
    }

    public static String rewardText(long coins, long tokens, long xp) {
        StringBuilder sb = new StringBuilder();
        if (coins > 0) sb.append("<gold>+").append(Msg.coins(coins)).append(" Coins ");
        if (tokens > 0) sb.append("<light_purple>+").append(tokens).append(tokens == 1 ? " Token " : " Tokens ");
        if (xp > 0) sb.append("<aqua>+").append(Msg.coins(xp)).append(" XP");
        return sb.toString().trim();
    }

    static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    // ---------- Verfolgen ----------

    /** Verfolgte Quest setzen ("-" schaltet die Anzeige aus, null = automatisch). */
    public synchronized void setTracked(UUID id, String key) {
        data(id).tracked = key;
    }

    synchronized String trackedKey(UUID id) {
        return data(id).tracked;
    }

    /** Was oben angezeigt wird: die verfolgte Quest, sonst das aktuelle Kapitel der Geschichte. */
    public synchronized Track track(UUID id) {
        Data d = data(id);
        if ("-".equals(d.tracked)) return null;
        String key = d.tracked;
        Def def = null;
        if (key != null) {
            def = key.startsWith("t:") ? teamDef(id) : def(d, key);
            if (def == null || isDone(id, key) || (key.startsWith("d:") && !d.daily.contains(def.id()))
                    || (key.startsWith("w:") && !d.weekly.contains(def.id()))
                    || (key.startsWith("s:") && (d.story >= story.size() || !story.get(d.story).id().equals(def.id())))) {
                d.tracked = null;
                key = null;
                def = null;
            }
        }
        if (def == null && addon.sideQuests() != null) {
            // Ohne eigene Auswahl hat eine laufende Nebenquest Vorrang vor der Geschichte.
            Def side = addon.sideQuests().running(id);
            if (side != null) return new Track("n:" + side.id(), side, addon.sideQuests().runningProgress(id));
        }
        if (def == null && d.story < story.size()) {
            def = story.get(d.story);
            key = "s:" + def.id();
            if (d.done.contains(key)) return null;
        }
        return def == null ? null : new Track(key, def, progressOf(id, key));
    }

    // ---------- Rangliste ----------

    public synchronized List<Map.Entry<UUID, Long>> top(int n) {
        List<Map.Entry<UUID, Long>> list = new ArrayList<>();
        for (Map.Entry<UUID, Data> e : players.entrySet()) if (e.getValue().total > 0) list.add(Map.entry(e.getKey(), e.getValue().total));
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return list.subList(0, Math.min(n, list.size()));
    }

    // ---------- Speichern ----------

    private void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection ps = yml.getConfigurationSection("players");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                ConfigurationSection s = ps.getConfigurationSection(key);
                if (s == null) continue;
                UUID id;
                try {
                    id = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                Data d = new Data();
                d.day = s.getString("day", "");
                d.week = s.getString("week", "");
                d.daily = new ArrayList<>(s.getStringList("daily"));
                d.weekly = new ArrayList<>(s.getStringList("weekly"));
                ConfigurationSection pr = s.getConfigurationSection("progress");
                if (pr != null) for (String k : pr.getKeys(false)) d.progress.put(k.replace('|', ':'), pr.getLong(k));
                d.done.addAll(s.getStringList("done"));
                d.claimed.addAll(s.getStringList("claimed"));
                d.story = s.getInt("story");
                d.streak = s.getInt("streak");
                d.bestStreak = s.getInt("best-streak");
                d.streakDay = s.getString("streak-day", "");
                d.total = s.getLong("total");
                d.tracked = s.getString("tracked");
                players.put(id, d);
            }
        }
        ConfigurationSection ts = yml.getConfigurationSection("teams");
        if (ts != null) {
            for (String key : ts.getKeys(false)) {
                TeamQuest tq = new TeamQuest();
                tq.week = ts.getString(key + ".week", "");
                tq.id = ts.getString(key + ".id", "");
                tq.progress = ts.getLong(key + ".progress");
                teams.put(key, tq);
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, Data> e : players.entrySet()) {
            Data d = e.getValue();
            String b = "players." + e.getKey() + ".";
            out.set(b + "day", d.day);
            out.set(b + "week", d.week);
            out.set(b + "daily", d.daily);
            out.set(b + "weekly", d.weekly);
            // Punkte in Schlüsseln würden YAML-Ebenen bilden, Doppelpunkte sind erlaubt, aber sicher ist sicher.
            for (Map.Entry<String, Long> p : d.progress.entrySet()) out.set(b + "progress." + p.getKey().replace(':', '|'), p.getValue());
            out.set(b + "done", new ArrayList<>(d.done));
            out.set(b + "claimed", new ArrayList<>(d.claimed));
            out.set(b + "story", d.story);
            out.set(b + "streak", d.streak);
            out.set(b + "best-streak", d.bestStreak);
            out.set(b + "streak-day", d.streakDay);
            out.set(b + "total", d.total);
            out.set(b + "tracked", d.tracked);
        }
        for (Map.Entry<String, TeamQuest> e : teams.entrySet()) {
            String b = "teams." + e.getKey() + ".";
            out.set(b + "week", e.getValue().week);
            out.set(b + "id", e.getValue().id);
            out.set(b + "progress", e.getValue().progress);
        }
        try {
            addon.getDataFolder().mkdirs();
            out.save(dataFile);
        } catch (IOException ex) {
            addon.getLogger().warning("questdata.yml: " + ex.getMessage());
        }
    }
}

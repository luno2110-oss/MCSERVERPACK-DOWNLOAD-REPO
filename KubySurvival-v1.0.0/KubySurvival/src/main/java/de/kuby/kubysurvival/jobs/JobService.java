package de.kuby.kubysurvival.jobs;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Jobs, Spielerlevel und Tokens.
 * Job-Level 1 bis 100: Level L braucht insgesamt 50 * L * (L - 1) Job-XP. Spielerlevel: alle Job-XP zusammen,
 * Level L braucht 150 * L * (L - 1). Aufstiege bringen Coins (Job) und Tokens (Job und Spieler).
 * Coins aus Jobs sind pro Stunde gedeckelt; Limit und Lohn wachsen mit dem Job-Level. XP gibt es weiter.
 * Jede Woche gibt es einen Auftrag: eine Menge Job-XP im Job, den man zu Wochenbeginn hatte.
 */
public final class JobService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    /** Daten eines Spielers. */
    public static final class Worker {
        public Job job;
        public final Map<Job, Long> xp = new EnumMap<>(Job.class);
        public long totalXp;
        public long tokens;
        /** Coins aus Jobs in der laufenden Stunde. */
        public long earnedHour;
        public String hour = "";
        public long switchedAt;
        /** Wochenauftrag: Woche (z. B. 2026-W43), Job, Ziel und Stand in Job-XP, abgeschlossen. */
        public String orderWeek = "";
        public Job orderJob;
        public long orderGoal;
        public long orderDone;
        public boolean orderFinished;

        public long xp(Job j) {
            return xp.getOrDefault(j, 0L);
        }
    }

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File file;
    private final Map<UUID, Worker> workers = new HashMap<>();

    public JobService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "jobs.yml");
        load();
    }

    // ---------- Level ----------

    public static long jobXpFor(int level) {
        return 50L * level * (level - 1);
    }

    public static long playerXpFor(int level) {
        return 150L * level * (level - 1);
    }

    public int maxLevel() {
        return Math.max(2, core.getConfig().getInt("jobs.max-level", 100));
    }

    public int jobLevel(Worker w, Job j) {
        int level = 1;
        while (level < maxLevel() && jobXpFor(level + 1) <= w.xp(j)) level++;
        return level;
    }

    public static int playerLevel(Worker w) {
        int level = 1;
        while (playerXpFor(level + 1) <= w.totalXp) level++;
        return level;
    }

    /** Fortschritt zum nächsten Job-Level in Prozent (100 bei Höchstlevel). */
    public int progress(Worker w, Job j) {
        int level = jobLevel(w, j);
        if (level >= maxLevel()) return 100;
        long from = jobXpFor(level);
        long to = jobXpFor(level + 1);
        return (int) Math.min(99, (w.xp(j) - from) * 100 / Math.max(1, to - from));
    }

    /** Coins pro Stunde für den aktiven Job; wächst mit dessen Level. */
    public long hourlyCap(Worker w) {
        int level = w.job == null ? 1 : jobLevel(w, w.job);
        return core.getConfig().getLong("jobs.hourly-coin-cap-base", 300)
                + core.getConfig().getLong("jobs.hourly-coin-cap-per-level", 15) * level;
    }

    /** Lohn-Faktor durch das Job-Level: Level 1 zahlt normal, Level 100 etwa doppelt. */
    public double payFactor(int level) {
        return 1.0 + (level - 1) * core.getConfig().getDouble("jobs.pay-bonus-per-level", 0.01);
    }

    public long orderCoins() {
        return core.getConfig().getLong("jobs.weekly-order.reward-coins", 3000);
    }

    public long orderTokens() {
        return core.getConfig().getLong("jobs.weekly-order.reward-tokens", 3);
    }

    /** Minuten bis das Stundenlimit zurückgesetzt wird. */
    public static long minutesToNextHour() {
        return 60 - LocalDateTime.now(ZONE).getMinute();
    }

    private static String hourKey() {
        return LocalDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH"));
    }

    private static String weekKey() {
        LocalDate d = LocalDate.now(ZONE);
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    // ---------- Daten ----------

    public synchronized Worker worker(UUID id) {
        Worker w = workers.computeIfAbsent(id, k -> new Worker());
        String hour = hourKey();
        if (!hour.equals(w.hour)) {
            w.hour = hour;
            w.earnedHour = 0;
        }
        String week = weekKey();
        if (!week.equals(w.orderWeek)) {
            w.orderWeek = week;
            newOrder(w);
        }
        return w;
    }

    /** Neuer Wochenauftrag für den aktuellen Job (ohne Job gibt es ihn bei der ersten Wahl). */
    private void newOrder(Worker w) {
        w.orderJob = w.job;
        w.orderDone = 0;
        w.orderFinished = false;
        if (w.job == null) {
            w.orderGoal = 0;
            return;
        }
        int level = jobLevel(w, w.job);
        w.orderGoal = core.getConfig().getLong("jobs.weekly-order.xp-base", 1500)
                + core.getConfig().getLong("jobs.weekly-order.xp-per-level", 40) * level;
    }

    /** Minuten bis zum nächsten erlaubten Wechsel, 0 wenn sofort möglich. */
    public synchronized long cooldownMinutes(UUID id) {
        Worker w = worker(id);
        if (w.job == null) return 0;
        long wait = core.getConfig().getLong("jobs.switch-cooldown-minutes", 30) * 60_000L;
        long left = w.switchedAt + wait - System.currentTimeMillis();
        return left <= 0 ? 0 : (left + 59_999) / 60_000;
    }

    /** Wählt einen Job. Gibt eine Fehlermeldung zurück oder null. */
    public synchronized String choose(Player p, Job job) {
        Worker w = worker(p.getUniqueId());
        if (w.job == job) return "Das ist schon dein Job.";
        long wait = cooldownMinutes(p.getUniqueId());
        if (wait > 0) return "Du kannst deinen Job erst in " + wait + " Minuten wechseln (oder im Token-Shop sofort).";
        boolean first = w.job == null;
        w.job = job;
        w.switchedAt = first ? 0 : System.currentTimeMillis();
        if (w.orderJob == null && !w.orderFinished) newOrder(w);
        return null;
    }

    /** Wartezeit mit Tokens überspringen. */
    public synchronized String skipCooldown(Player p) {
        Worker w = worker(p.getUniqueId());
        long price = core.getConfig().getLong("jobs.skip-cooldown-tokens", 2);
        if (cooldownMinutes(p.getUniqueId()) == 0) return "Du kannst deinen Job schon jetzt wechseln.";
        if (w.tokens < price) return "Dafür brauchst du " + price + " Tokens.";
        w.tokens -= price;
        w.switchedAt = 0;
        return null;
    }

    public synchronized boolean takeTokens(UUID id, long amount) {
        Worker w = worker(id);
        if (w.tokens < amount) return false;
        w.tokens -= amount;
        return true;
    }

    public synchronized void addTokens(UUID id, long amount) {
        worker(id).tokens += amount;
    }

    // ---------- Lohn ----------

    /** Lohn für eine Aktion. Zählt nur in Survival und nur für den aktiven Job. */
    public synchronized void reward(Player p, Job job, int baseXp, int baseCoins) {
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) return;
        Worker w = worker(p.getUniqueId());
        if (w.job != job) return;
        int jobBefore = jobLevel(w, job);
        long xp = Math.round(baseXp * core.getConfig().getDouble("jobs.xp-multiplier", 1.0) * core.booster().multiplier());
        long coins = Math.round(baseCoins * core.getConfig().getDouble("jobs.coin-multiplier", 1.0) * payFactor(jobBefore));

        int playerBefore = playerLevel(w);
        if (jobBefore < maxLevel()) w.xp.merge(job, xp, Long::sum);
        w.totalXp += xp;

        long cap = hourlyCap(w);
        long paid = Math.max(0, Math.min(coins, cap - w.earnedHour));
        if (paid > 0) {
            core.economy().deposit(p.getUniqueId(), paid);
            w.earnedHour += paid;
            if (w.earnedHour >= cap) {
                Msg.send(p, "<yellow>Stundenlimit für Job-Coins erreicht (" + Msg.coins(cap) + "). XP bekommst du weiter, "
                        + "Coins wieder in " + minutesToNextHour() + " Min.");
            }
        }

        if (w.orderJob == job && !w.orderFinished && w.orderGoal > 0) {
            w.orderDone = Math.min(w.orderGoal, w.orderDone + xp);
            if (w.orderDone >= w.orderGoal) {
                w.orderFinished = true;
                long oc = orderCoins();
                long ot = orderTokens();
                core.economy().deposit(p.getUniqueId(), oc);
                w.tokens += ot;
                Msg.send(p, "<gold><b>Wochenauftrag erfüllt!</b> <gold>+" + Msg.coins(oc) + " Coins <light_purple>+" + ot + " Tokens");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.1f);
            }
        }

        int jobAfter = jobLevel(w, job);
        if (jobAfter > jobBefore) {
            long bonus = core.getConfig().getLong("jobs.levelup-coins", 100) * jobAfter;
            long tokens = core.getConfig().getLong("jobs.tokens-per-job-level", 1);
            core.economy().deposit(p.getUniqueId(), bonus);
            w.tokens += tokens;
            Msg.send(p, job.color + job.display + " <white>Level " + jobAfter + "! <gold>+" + Msg.coins(bonus)
                    + " Coins <light_purple>+" + tokens + " Token" + (tokens == 1 ? "" : "s"));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        }
        int playerAfter = playerLevel(w);
        if (playerAfter > playerBefore) {
            long tokens = core.getConfig().getLong("jobs.tokens-per-player-level", 3);
            w.tokens += tokens;
            Msg.send(p, "<gold><bold>Level " + playerAfter + "!</bold> <light_purple>+" + tokens + " Tokens");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.0f);
        }
    }

    // ---------- Speichern ----------

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ps = yml.getConfigurationSection("players");
        if (ps == null) return;
        for (String key : ps.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection s = ps.getConfigurationSection(key);
            if (s == null) continue;
            Worker w = new Worker();
            w.job = Job.of(s.getString("job"));
            w.totalXp = s.getLong("total-xp");
            w.tokens = s.getLong("tokens");
            w.earnedHour = s.getLong("earned-hour");
            w.hour = s.getString("hour", "");
            w.switchedAt = s.getLong("switched-at");
            w.orderWeek = s.getString("order.week", "");
            w.orderJob = Job.of(s.getString("order.job"));
            w.orderGoal = s.getLong("order.goal");
            w.orderDone = s.getLong("order.done");
            w.orderFinished = s.getBoolean("order.finished");
            ConfigurationSection x = s.getConfigurationSection("xp");
            if (x != null) {
                for (String j : x.getKeys(false)) {
                    Job job = Job.of(j);
                    if (job != null) w.xp.put(job, x.getLong(j));
                }
            }
            workers.put(id, w);
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, Worker> e : workers.entrySet()) {
            Worker w = e.getValue();
            String base = "players." + e.getKey();
            out.set(base + ".job", w.job == null ? null : w.job.name());
            out.set(base + ".total-xp", w.totalXp);
            out.set(base + ".tokens", w.tokens);
            out.set(base + ".earned-hour", w.earnedHour);
            out.set(base + ".hour", w.hour);
            out.set(base + ".switched-at", w.switchedAt);
            out.set(base + ".order.week", w.orderWeek);
            out.set(base + ".order.job", w.orderJob == null ? null : w.orderJob.name());
            out.set(base + ".order.goal", w.orderGoal);
            out.set(base + ".order.done", w.orderDone);
            out.set(base + ".order.finished", w.orderFinished);
            for (Map.Entry<Job, Long> x : w.xp.entrySet()) out.set(base + ".xp." + x.getKey().name(), x.getValue());
        }
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException ex) {
            addon.getLogger().warning("jobs.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }
}

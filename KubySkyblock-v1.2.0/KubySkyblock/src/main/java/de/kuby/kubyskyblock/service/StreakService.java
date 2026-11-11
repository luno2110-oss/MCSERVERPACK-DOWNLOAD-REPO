package de.kuby.kubyskyblock.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Die Belohnungsschleifen in Skyblock (schnelle, häufige Belohnungen wie in Roblox-Spielen):
 * <ul>
 *   <li><b>Combo</b>: jeder natürlich abgebaute Block (auch der One-Block) zahlt Geld und XP, der Multiplikator steigt
 *   mit jedem Block um 0,1 (in der Prime Time 18 bis 24 Uhr um 0,2) bis zur Grenze (belohnungen.combo.max, Standard
 *   x100). Die Combo beginnt in jedem Zeitfenster (combo.fenster-stunden, Standard 2 Stunden) neu und reißt nach einer
 *   Pause. Alle paar Blöcke ein
 *   Meilenstein mit Bonus, Klang und Titel. Anzeige über der Hotbar mit Balken bis zum nächsten Meilenstein.</li>
 *   <li><b>Glücksfunde</b>: mit kleiner Chance (steigt mit der Combo) steckt im Block ein Fund: Gewöhnlich, Selten,
 *   Episch oder Legendär (Geld, XP, Generator-Booster, Schlüssel, Juwelen). Legendäre Funde sieht der ganze Server.</li>
 *   <li><b>Belohnungskiste</b>: alle paar aktiven Minuten in Skyblock eine Kiste; jede Kiste am selben Tag ist größer
 *   als die vorige, jede dritte enthält zusätzlich einen Glücksfund, jede sechste einen Schlüssel.</li>
 * </ul>
 * Geld landet immer auf dem Skyblock-Konto. Alle Werte stehen unter belohnungen.* in der config.
 */
public final class StreakService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    /** Seltenheit eines Glücksfunds. */
    public enum Tier {
        GEWOEHNLICH("Gewöhnlich", "#B8C4D0"), SELTEN("Selten", "#5FB3FF"), EPISCH("Episch", "#C77DFF"), LEGENDAER("Legendär", "#FFB02E");

        public final String label, color;

        Tier(String label, String color) {
            this.label = label;
            this.color = color;
        }
    }

    private static final class Combo {
        String hour = "";
        int count;
        long last;
        boolean maxShown;
    }

    private static final class Chest {
        String day = "";
        int opened;
        int minutes;
        Location lastPos;
    }

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final File file;
    private final Map<UUID, Combo> combos = new HashMap<>();
    private final Map<UUID, Chest> chests = new HashMap<>();
    /** Beste Combo je Spieler am Tag bestDay (Tages-Rangliste, Preise um Mitternacht). */
    private final Map<UUID, Integer> best = new HashMap<>();
    private String bestDay = "";
    /** Erster Skyblock-Block je Spieler (Start-Boost); 1 = schon vor dem Boost gespielt. */
    private final Map<UUID, Long> firstSeen = new HashMap<>();
    /** Gewinner des letzten ausgewerteten Tages (Name und Combo) für die Anzeige. */
    private final List<String> lastWinners = new ArrayList<>();
    private UUID leader;
    private long leaderShout;

    public StreakService(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "streaks.yml");
        load();
        // jede Minute: aktive Spieler in Skyblock sammeln Minuten für die Belohnungskiste
        addon.getServer().getScheduler().runTaskTimer(addon, this::minute, 1200L, 1200L);
    }

    private ConfigurationSection cfg() {
        ConfigurationSection s = addon.getConfig().getConfigurationSection("belohnungen");
        return s != null ? s : new YamlConfiguration();
    }

    public static boolean primeTime() {
        int h = ZonedDateTime.now(ZONE).getHour();
        return h >= 18;
    }

    /** Alte Bezeichnung (Prime Time 18 bis 24 Uhr). */
    public static boolean inWindow() {
        return primeTime();
    }

    /** Länge eines Combo-Zeitfensters in Stunden (belohnungen.combo.fenster-stunden, Standard 2). */
    private int windowHours() {
        return Math.max(1, Math.min(24, cfg().getInt("combo.fenster-stunden", 2)));
    }

    /** Schlüssel des aktuellen Zeitfensters (z. B. 2026-11-11 und Fenster 7 = 14 bis 16 Uhr bei 2 Stunden). */
    private String hourKey() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        return now.toLocalDate() + "-" + (now.getHour() / windowHours());
    }

    /** Minuten bis zum Ende des aktuellen Combo-Fensters. */
    public int minutesLeft() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        int w = windowHours();
        int end = (now.getHour() / w + 1) * w * 60;
        return Math.max(0, end - (now.getHour() * 60 + now.getMinute()));
    }

    private double scale(Player p) {
        IslandService is = addon.islands();
        IslandService.Island i = is == null ? null : is.islandOf(p.getUniqueId());
        long lvl = i == null ? 0 : is.levelOf(i);
        return 1.0 + Math.min(200, lvl) * cfg().getDouble("level-faktor", 0.05);
    }

    private void pay(Player p, long money, long xp) {
        if (money > 0) core.modes().depositIn(p.getUniqueId(), ModeService.SKYBLOCK, money);
        if (xp > 0) core.stats().get(p.getUniqueId()).xp += xp;
    }

    // =================================================================================================================
    // Combo
    // =================================================================================================================

    /** Aktueller Multiplikator des Spielers (1,0 ohne Combo). */
    public double multiplier(UUID id) {
        Combo c = combos.get(id);
        if (c == null || !c.hour.equals(hourKey())) return 1.0;
        return mult(c.count);
    }

    private double mult(int count) {
        boolean prime = primeTime();
        double step = prime ? cfg().getDouble("combo.schritt-prime", 0.2) : cfg().getDouble("combo.schritt", 0.1);
        double cap = prime ? cfg().getDouble("combo.max-prime", 100.0) : cfg().getDouble("combo.max", 100.0);
        return Math.max(1.0, Math.min(cap, 1.0 + step * (count - 1)));
    }

    /** Belohnung für einen abgebauten Block (ohne Ort: keine Partikel beim Glücksfund). */
    public void reward(Player p) {
        reward(p, null);
    }

    /** Belohnung für einen natürlich abgebauten Block: Combo, Meilensteine, Glücksfund. */
    public void reward(Player p, Block b) {
        if (!ModeService.SKYBLOCK.equals(core.modes().modeOf(p.getUniqueId()))) return;
        UUID id = p.getUniqueId();
        Combo c = combos.computeIfAbsent(id, k -> new Combo());
        long now = System.currentTimeMillis();
        String hour = hourKey();
        if (!hour.equals(c.hour)) {
            if (c.count >= 20) Msg.send(p, "<#FFD25A>⏰ Neue Combo-Runde! <gray>Letzte Runde: <white>" + c.count + " Blöcke <dark_gray>· "
                    + "<gray>Dein Tagesbestwert: <white>" + bestToday(id) + " <click:run_command:'/combo'><yellow>[Rangliste]</click>");
            c.hour = hour;
            c.count = 0;
            c.maxShown = false;
        } else {
            long pause = cfg().getLong("combo.pause-sekunden", 60) * 1000L;
            if (pause > 0 && c.count >= 10 && now - c.last > pause) {
                p.sendActionBar(Msg.parse("<red>Combo gerissen bei <white>" + c.count + "</white>! <gray>Weiter geht's"));
                Sfx.play(core, p, Sfx.Kind.FAIL);
                c.count = 0;
                c.maxShown = false;
            }
        }
        c.count++;
        c.last = now;
        firstSeen.putIfAbsent(id, now);
        if (firstSeen.get(id) == now && starter(id) > 1.0) welcomeBoost(p);
        trackBest(p, c.count);
        double m = mult(c.count);
        double boost = starter(id);
        long baseMoney = addon.getConfig().getLong("reward.money", 2), baseXp = addon.getConfig().getLong("reward.xp", 1);
        long money = Math.round(baseMoney * m * boost), xp = Math.round(baseXp * m * boost);
        pay(p, money, xp);

        int every = Math.max(5, cfg().getInt("combo.meilenstein", 50));
        boolean milestone = c.count % every == 0;
        double cap = primeTime() ? cfg().getDouble("combo.max-prime", 100.0) : cfg().getDouble("combo.max", 100.0);
        if (milestone) {
            long bonus = Math.round(baseMoney * m * cfg().getDouble("combo.meilenstein-bonus", 25) * scale(p) * boost);
            pay(p, bonus, Math.round(baseXp * m * 10));
            float pitch = Math.min(2.0f, 0.8f + 0.1f * (c.count / every));
            Sfx.play(core, p, Sfx.Kind.LEVEL_UP, pitch);
            p.showTitle(Title.title(Msg.parse("<gradient:#FFE066:#FF8A3D><b>COMBO " + c.count + "!</b></gradient>"),
                    Msg.parse("<gold>+" + Msg.coins(bonus) + " $ <dark_gray>· <yellow>x" + fmt(m)),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(900), Duration.ofMillis(300))));
            if (b != null) b.getWorld().spawnParticle(Particle.FIREWORK, b.getLocation().add(0.5, 0.8, 0.5), 25, 0.3, 0.4, 0.3, 0.05);
            // jeder fünfte Meilenstein: garantiert ein seltener Fund oder besser
            if (c.count % (every * 5) == 0) find(p, b, rollTier(Tier.SELTEN));
        } else if (m >= cap && !c.maxShown) {
            c.maxShown = true;
            Sfx.play(core, p, Sfx.Kind.EPIC);
            p.showTitle(Title.title(Msg.parse("<gradient:#FF5AE0:#FFD25A><b>MAX COMBO</b></gradient>"),
                    Msg.parse("<yellow>x" + fmt(m) + " <gray>bis zur neuen Runde (" + left() + ")"),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1000), Duration.ofMillis(300))));
        }
        actionBar(p, c, m, money, xp, every);

        // Glücksfund: Grundchance plus etwas je Combo-Stufe
        double chance = cfg().getDouble("gluecksfund.chance", 0.015) + (m - 1) * cfg().getDouble("gluecksfund.combo-plus", 0.0005);
        if (ThreadLocalRandom.current().nextDouble() < chance) find(p, b, rollTier(Tier.GEWOEHNLICH));
    }

    private void actionBar(Player p, Combo c, double m, long money, long xp, int every) {
        int into = c.count % every;
        int filled = Math.round(10f * into / every);
        StringBuilder bar = new StringBuilder();
        bar.append("<#FFD25A>").append("▮".repeat(filled)).append("<dark_gray>").append("▮".repeat(10 - filled));
        String prime = (primeTime() ? "<#FF8A3D><b>PRIME</b> " : "") + (starter(p.getUniqueId()) > 1.0 ? "<#7CF5FF><b>START x"
                + fmt(starter(p.getUniqueId())) + "</b> " : "");
        p.sendActionBar(Msg.parse(prime + "<#FFE066><b>COMBO " + c.count + "</b> <yellow>x" + fmt(m) + " " + bar
                + " <green>+" + Msg.coins(money) + "$ <aqua>+" + Msg.coins(xp) + " XP <dark_gray>· <gray>⏰ " + left()));
    }

    /** Restzeit der Combo-Runde, z. B. „1:23“. */
    private String left() {
        int m = minutesLeft();
        return m / 60 + ":" + String.format("%02d", m % 60);
    }

    static String fmt(double v) {
        return String.format(Locale.GERMANY, "%.1f", v);
    }

    // =================================================================================================================
    // Glücksfunde
    // =================================================================================================================

    /** Seltenheit würfeln, mindestens min. */
    private Tier rollTier(Tier min) {
        ConfigurationSection w = cfg().getConfigurationSection("gluecksfund.gewichte");
        double[] weights = new double[Tier.values().length];
        double sum = 0;
        for (Tier t : Tier.values()) {
            double def = switch (t) {
                case GEWOEHNLICH -> 68;
                case SELTEN -> 24;
                case EPISCH -> 7;
                case LEGENDAER -> 1;
            };
            double v = t.ordinal() < min.ordinal() ? 0 : (w == null ? def : w.getDouble(t.name().toLowerCase(Locale.ROOT), def));
            weights[t.ordinal()] = v;
            sum += v;
        }
        double r = ThreadLocalRandom.current().nextDouble() * sum;
        for (Tier t : Tier.values()) {
            r -= weights[t.ordinal()];
            if (r < 0) return t;
        }
        return min;
    }

    private long range(String key, long lo, long hi, Player p) {
        long a = cfg().getLong("gluecksfund." + key + "-min", lo), b = Math.max(a, cfg().getLong("gluecksfund." + key + "-max", hi));
        return Math.round(ThreadLocalRandom.current().nextLong(a, b + 1) * scale(p));
    }

    /** Einen Fund der Seltenheit t geben (mit Klang, Partikeln, Titel). Gibt die Beschreibung zurück. */
    public String find(Player p, Block b, Tier t) {
        StringBuilder what = new StringBuilder();
        switch (t) {
            case GEWOEHNLICH -> {
                long money = range("gewoehnlich", 20, 60, p);
                pay(p, money, 0);
                what.append(Msg.coins(money)).append(" $");
            }
            case SELTEN -> {
                long money = range("selten", 80, 200, p);
                pay(p, money, 30);
                what.append(Msg.coins(money)).append(" $ + 30 XP");
            }
            case EPISCH -> {
                int pick = ThreadLocalRandom.current().nextInt(3);
                IslandService.Island i = addon.islands() == null ? null : addon.islands().islandOf(p.getUniqueId());
                if (pick == 0 && i != null && addon.generators() != null && !addon.generators().onIsland(i.slot).isEmpty()) {
                    int min = cfg().getInt("gluecksfund.booster-minuten", 10);
                    addon.generators().addBoost(i.slot, 2.0, min);
                    what.append("Generator-Booster x2 für ").append(min).append(" Min.");
                } else if (pick == 1 && giveKey(p, cfg().getString("gluecksfund.episch-schluessel", "selten"), 1)) {
                    what.append("1 ").append(keyName(cfg().getString("gluecksfund.episch-schluessel", "selten"))).append("-Schlüssel");
                } else {
                    long money = range("episch", 500, 900, p);
                    pay(p, money, 100);
                    what.append(Msg.coins(money)).append(" $ + 100 XP");
                }
            }
            case LEGENDAER -> {
                long money = range("legendaer", 2000, 3500, p);
                long jewels = cfg().getLong("gluecksfund.legendaer-juwelen", 3);
                pay(p, money, 300);
                what.append(Msg.coins(money)).append(" $");
                if (jewels > 0) {
                    core.jewels().add(p.getUniqueId(), jewels);
                    what.append(" + ").append(jewels).append(" Juwelen");
                }
                String key = cfg().getString("gluecksfund.legendaer-schluessel", "episch");
                if (giveKey(p, key, 1)) what.append(" + 1 ").append(keyName(key)).append("-Schlüssel");
            }
        }
        // Rückmeldung: je seltener, desto größer
        Location at = b != null ? b.getLocation().add(0.5, 0.6, 0.5) : p.getLocation().add(0, 1, 0);
        switch (t) {
            case GEWOEHNLICH -> {
                Sfx.play(core, p, Sfx.Kind.COIN);
                at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 8, 0.3, 0.3, 0.3, 0);
            }
            case SELTEN -> {
                Sfx.play(core, p, Sfx.Kind.CHIME);
                at.getWorld().spawnParticle(Particle.END_ROD, at, 14, 0.3, 0.4, 0.3, 0.03);
            }
            case EPISCH -> {
                Sfx.play(core, p, Sfx.Kind.EPIC);
                at.getWorld().spawnParticle(Particle.WITCH, at, 30, 0.4, 0.5, 0.4, 0.05);
            }
            case LEGENDAER -> {
                Sfx.play(core, p, Sfx.Kind.JACKPOT);
                at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at, 60, 0.5, 0.8, 0.5, 0.25);
            }
        }
        if (t.ordinal() >= Tier.SELTEN.ordinal()) {
            p.showTitle(Title.title(Msg.parse("<" + t.color + "><b>" + t.label.toUpperCase(Locale.ROOT) + "ER FUND!</b>"),
                    Msg.parse("<white>" + Msg.escape(what.toString())),
                    Title.Times.times(Duration.ofMillis(80), Duration.ofMillis(1400), Duration.ofMillis(300))));
        }
        Msg.send(p, "<" + t.color + ">✦ " + t.label + "er Fund: <white>" + Msg.escape(what.toString()));
        if (t == Tier.LEGENDAER) {
            core.notifications().send("notify.broadcast", "<#FFB02E><b>LEGENDÄR!</b> <white>" + p.getName()
                    + " <gray>hat beim Abbauen einen legendären Fund gemacht: <white>" + Msg.escape(what.toString()));
        }
        return what.toString();
    }

    private boolean giveKey(Player p, String key, int n) {
        if (key == null || key.isBlank() || core.keys().type(key) == null) return false;
        return core.keys().give(p.getUniqueId(), ModeService.SKYBLOCK, key, n);
    }

    private String keyName(String key) {
        var t = core.keys().type(key);
        return t == null ? key : t.name();
    }

    // =================================================================================================================
    // Belohnungskiste
    // =================================================================================================================

    /** Minuten bis zur nächsten Belohnungskiste (für Anzeigen). */
    public int minutesToChest(UUID id) {
        Chest c = chests.get(id);
        int every = Math.max(1, cfg().getInt("kiste.minuten", 10));
        return every - (c == null ? 0 : c.minutes % every);
    }

    private void minute() {
        rollDay();
        if (!cfg().getBoolean("kiste.aktiv", true)) return;
        int every = Math.max(1, cfg().getInt("kiste.minuten", 10));
        String today = LocalDate.now(ZONE).toString();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!ModeService.SKYBLOCK.equals(core.modes().modeOf(p.getUniqueId()))) continue;
            Chest c = chests.computeIfAbsent(p.getUniqueId(), k -> new Chest());
            if (!today.equals(c.day)) {
                c.day = today;
                c.opened = 0;
            }
            // nur aktive Minuten (bewegt oder abgebaut), damit AFK-Stehen nichts bringt
            Location now = p.getLocation();
            Combo combo = combos.get(p.getUniqueId());
            boolean mined = combo != null && System.currentTimeMillis() - combo.last < 70_000L;
            boolean moved = c.lastPos == null || c.lastPos.getWorld() != now.getWorld() || c.lastPos.distanceSquared(now) > 4;
            c.lastPos = now;
            if (!mined && !moved) continue;
            c.minutes++;
            int left = every - c.minutes % every;
            if (left == 1 && every > 2) {
                p.sendActionBar(Msg.parse("<#FFD25A>🎁 Noch <white>1 Minute</white> bis zur nächsten Belohnungskiste!"));
            }
            if (c.minutes % every == 0) openChest(p, c);
        }
    }

    private void openChest(Player p, Chest c) {
        c.opened++;
        int k = c.opened;
        int maxSteps = cfg().getInt("kiste.max-stufe", 12);
        double grow = 1.0 + cfg().getDouble("kiste.wachstum", 0.25) * (Math.min(k, maxSteps) - 1);
        double boost = starter(p.getUniqueId());
        long money = Math.round(cfg().getLong("kiste.geld", 150) * grow * scale(p) * boost);
        long xp = Math.round(cfg().getLong("kiste.xp", 40) * grow * boost);
        pay(p, money, xp);
        StringBuilder extra = new StringBuilder();
        if (k % 3 == 0) {
            String f = find(p, null, rollTier(Tier.SELTEN));
            extra.append(" + ").append(f);
        }
        String key = cfg().getString("kiste.schluessel", "vote");
        if (k % 6 == 0 && giveKey(p, key, 1)) extra.append(" + 1 ").append(keyName(key)).append("-Schlüssel");
        Sfx.play(core, p, Sfx.Kind.SUCCESS_BIG);
        p.showTitle(Title.title(Msg.parse("<gradient:#7CF5FF:#FFD25A><b>BELOHNUNGSKISTE #" + k + "</b></gradient>"),
                Msg.parse("<gold>+" + Msg.coins(money) + " $ <aqua>+" + Msg.coins(xp) + " XP"),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1600), Duration.ofMillis(400))));
        p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation().add(0, 1.2, 0), 30, 0.4, 0.6, 0.4, 0.05);
        int every = Math.max(1, cfg().getInt("kiste.minuten", 10));
        String next = k + 1 <= maxSteps ? " Die nächste ist noch größer" : "";
        Msg.send(p, "<#7CF5FF>🎁 Belohnungskiste #" + k + ": <gold>+" + Msg.coins(money) + " $ <aqua>+" + Msg.coins(xp) + " XP"
                + "<white>" + Msg.escape(extra.toString()) + "<gray>." + next + " (in " + every + " Min.)."
                + (k % 3 == 2 ? " <light_purple>Nächste Kiste: mit Glücksfund!" : ""));
    }

    // =================================================================================================================
    // Start-Boost für neue Spieler
    // =================================================================================================================

    /**
     * Start-Boost: in den ersten start-boost.stunden (72) Stunden nach dem ersten Skyblock-Block gibt es mehr Geld und
     * XP (start-boost.faktor, Standard x2) aus Blöcken, Meilensteinen und Belohnungskisten. Nicht für Zweitkonten.
     */
    public double starter(UUID id) {
        if (!cfg().getBoolean("start-boost.aktiv", true)) return 1.0;
        Long t = firstSeen.get(id);
        if (t == null || t <= 1) return 1.0;
        if (System.currentTimeMillis() - t > cfg().getLong("start-boost.stunden", 72) * 3_600_000L) return 1.0;
        if (isAlt(id)) return 1.0;
        return Math.max(1.0, cfg().getDouble("start-boost.faktor", 2.0));
    }

    /** Zweitkonto-Prüfung für den Start-Boost, eine Minute zwischengespeichert (läuft sonst bei jedem Block). */
    private final Map<UUID, long[]> altCache = new HashMap<>();

    private boolean isAlt(UUID id) {
        if (core.alts() == null) return false;
        long now = System.currentTimeMillis();
        long[] c = altCache.get(id);
        if (c == null || now - c[0] > 60_000L) {
            c = new long[] {now, core.alts().hasOther(id) ? 1 : 0};
            altCache.put(id, c);
        }
        return c[1] == 1;
    }

    /** Restzeit des Start-Boosts in Stunden (0 = keiner). */
    public long starterHoursLeft(UUID id) {
        if (starter(id) <= 1.0) return 0;
        long end = firstSeen.get(id) + cfg().getLong("start-boost.stunden", 72) * 3_600_000L;
        return Math.max(1, (end - System.currentTimeMillis() + 3_599_999L) / 3_600_000L);
    }

    private void welcomeBoost(Player p) {
        Sfx.play(core, p, Sfx.Kind.EPIC);
        p.showTitle(Title.title(Msg.parse("<gradient:#7CF5FF:#FFD25A><b>START-BOOST x" + fmt(starter(p.getUniqueId())) + "</b></gradient>"),
                Msg.parse("<gray>" + cfg().getLong("start-boost.stunden", 72) + " Stunden mehr Geld und XP"),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2200), Duration.ofMillis(400))));
        Msg.send(p, "<#7CF5FF>🚀 Start-Boost: <white>" + cfg().getLong("start-boost.stunden", 72) + " Stunden lang x"
                + fmt(starter(p.getUniqueId())) + " Geld und XP <gray>aus Blöcken, Meilensteinen und Belohnungskisten. Bau los!");
    }

    // =================================================================================================================
    // Tages-Rangliste (beste Combo des Tages, Preise um Mitternacht)
    // =================================================================================================================

    private static String today() {
        return LocalDate.now(ZONE).toString();
    }

    private void trackBest(Player p, int count) {
        rollDay();
        UUID id = p.getUniqueId();
        if (count <= best.getOrDefault(id, 0)) return;
        best.put(id, count);
        if (count < cfg().getInt("rangliste.ansage-ab", 100)) return;
        UUID top = top(1).isEmpty() ? null : top(1).get(0).getKey();
        if (id.equals(top) && !id.equals(leader)) {
            UUID before = leader;
            leader = id;
            long now = System.currentTimeMillis();
            if (before != null && now - leaderShout > 5 * 60_000L) {
                leaderShout = now;
                String msg = "<#FFD25A>👑 <white>" + p.getName() + " <gray>führt jetzt die Combo-Rangliste an <dark_gray>(<yellow>" + count
                        + "</yellow>)<gray>. <click:run_command:'/combo'><yellow>[Rangliste]</click>";
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (ModeService.SKYBLOCK.equals(core.modes().modeOf(o.getUniqueId()))) Msg.send(o, msg);
                }
            }
        }
    }

    /** Die besten n Spieler des Tages (Combo absteigend). */
    public List<Map.Entry<UUID, Integer>> top(int n) {
        rollDay();
        List<Map.Entry<UUID, Integer>> l = new ArrayList<>(best.entrySet());
        l.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return l.subList(0, Math.min(n, l.size()));
    }

    public int bestToday(UUID id) {
        rollDay();
        return best.getOrDefault(id, 0);
    }

    /** Gewinner des Vortags („1. Name (Combo)“). */
    public List<String> lastWinners() {
        return List.copyOf(lastWinners);
    }

    /** Preis für Platz place (1-basiert): {Geld, Juwelen} aus belohnungen.rangliste.plaetze. */
    public long[] prize(int place, UUID id) {
        ConfigurationSection s = cfg().getConfigurationSection("rangliste.plaetze." + place);
        long money = s != null ? s.getLong("geld") : place == 1 ? 50_000 : place == 2 ? 30_000 : place == 3 ? 15_000 : 0;
        long jewels = s != null ? s.getLong("juwelen") : place == 1 ? 5 : place == 2 ? 3 : place == 3 ? 1 : 0;
        return new long[] {money, jewels};
    }

    public int prizePlaces() {
        return Math.max(0, cfg().getInt("rangliste.preise-plaetze", 3));
    }

    /** Tageswechsel: Rangliste des alten Tages auszahlen und leeren. */
    private void rollDay() {
        String t = today();
        if (t.equals(bestDay)) return;
        if (!bestDay.isEmpty() && !best.isEmpty() && cfg().getBoolean("rangliste.aktiv", true)) payout();
        bestDay = t;
        best.clear();
        leader = null;
    }

    private void payout() {
        List<Map.Entry<UUID, Integer>> l = new ArrayList<>(best.entrySet());
        l.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        lastWinners.clear();
        int min = cfg().getInt("rangliste.mindest-combo", 50);
        StringBuilder bc = new StringBuilder();
        for (int i = 0; i < Math.min(prizePlaces(), l.size()); i++) {
            UUID id = l.get(i).getKey();
            int count = l.get(i).getValue();
            if (count < min) break;
            long[] pr = prize(i + 1, id);
            if (pr[0] > 0) core.modes().depositIn(id, ModeService.SKYBLOCK, pr[0]);
            if (pr[1] > 0) core.jewels().add(id, pr[1]);
            String name = Bukkit.getOfflinePlayer(id).getName();
            if (name == null) name = "?";
            lastWinners.add((i + 1) + ". " + name + " (" + count + ")");
            bc.append(i == 0 ? "" : "<dark_gray>, ").append(i == 0 ? "<#FFD25A>" : "<white>").append(i + 1).append(". ").append(name)
                    .append(" <gray>(").append(count).append(")");
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                Sfx.play(core, p, Sfx.Kind.SUCCESS_BIG);
                Msg.send(p, "<#FFD25A>👑 Combo-Rangliste von gestern: Platz " + (i + 1) + "! <gold>+" + Msg.coins(pr[0]) + " $"
                        + (pr[1] > 0 ? " <light_purple>+" + pr[1] + " Juwelen" : ""));
            }
        }
        if (!bc.isEmpty()) {
            core.notifications().send("notify.broadcast", "<#FFD25A>👑 <b>Combo-Rangliste</b> <gray>(gestern): " + bc + "<gray>. Heute neu: <yellow>/combo",
                    o -> ModeService.SKYBLOCK.equals(core.modes().modeOf(o.getUniqueId())));
        }
        save();
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        bestDay = y.getString("rangliste.tag", "");
        ConfigurationSection r = y.getConfigurationSection("rangliste.beste");
        if (r != null) {
            for (String k : r.getKeys(false)) {
                try {
                    best.put(UUID.fromString(k), r.getInt(k));
                } catch (IllegalArgumentException ignored) {
                    // kaputter Eintrag
                }
            }
        }
        lastWinners.addAll(y.getStringList("rangliste.gewinner"));
        for (String k : y.getKeys(false)) {
            if (k.equals("rangliste")) continue;
            try {
                UUID id = UUID.fromString(k);
                // wer schon vor dem Start-Boost gespielt hat, bekommt ihn nicht nachträglich (1 = alt)
                firstSeen.put(id, y.getLong(k + ".erstes", 1));
                Combo c = new Combo();
                c.hour = y.getString(k + ".stunde", "");
                c.count = y.getInt(k + ".combo");
                c.last = System.currentTimeMillis();
                combos.put(id, c);
                Chest ch = new Chest();
                ch.day = y.getString(k + ".kiste-tag", "");
                ch.opened = y.getInt(k + ".kisten");
                ch.minutes = y.getInt(k + ".minuten");
                chests.put(id, ch);
            } catch (IllegalArgumentException ignored) {
                // kaputter Eintrag
            }
        }
    }

    public void save() {
        YamlConfiguration out = new YamlConfiguration();
        combos.forEach((id, c) -> {
            out.set(id + ".stunde", c.hour);
            out.set(id + ".combo", c.count);
        });
        firstSeen.forEach((id, t) -> out.set(id + ".erstes", t));
        out.set("rangliste.tag", bestDay);
        best.forEach((id, n) -> out.set("rangliste.beste." + id, n));
        out.set("rangliste.gewinner", new ArrayList<>(lastWinners));
        chests.forEach((id, c) -> {
            out.set(id + ".kiste-tag", c.day);
            out.set(id + ".kisten", c.opened);
            out.set(id + ".minuten", c.minutes);
        });
        DataFiles.save(addon, out, file);
    }

    /** Für Anzeigen: Combo-Zahl in dieser Stunde. */
    public int combo(UUID id) {
        Combo c = combos.get(id);
        return c == null || !c.hour.equals(hourKey()) ? 0 : c.count;
    }
}

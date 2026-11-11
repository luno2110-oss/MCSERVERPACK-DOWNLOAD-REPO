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
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Die Belohnungsschleifen in Skyblock (schnelle, häufige Belohnungen wie in Roblox-Spielen):
 * <ul>
 *   <li><b>Combo</b>: jeder natürlich abgebaute Block (auch der One-Block) zahlt Geld und XP, der Multiplikator steigt
 *   mit jedem Block um 0,1 (in der Prime Time 18 bis 24 Uhr um 0,2) bis zur Grenze (belohnungen.combo.max, Prime Time
 *   eigene Grenze). Die Combo beginnt jede volle Stunde neu und reißt nach einer Pause. Alle paar Blöcke ein
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
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");

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

    private static String hourKey() {
        return ZonedDateTime.now(ZONE).format(HOUR);
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
        double cap = prime ? cfg().getDouble("combo.max-prime", 15.0) : cfg().getDouble("combo.max", 10.0);
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
            if (c.count >= 20) Msg.send(p, "<#FFD25A>⏰ Neue Stunde, neue Combo! <gray>Letzte Stunde: <white>" + c.count + " Blöcke");
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
        double m = mult(c.count);
        long baseMoney = addon.getConfig().getLong("reward.money", 2), baseXp = addon.getConfig().getLong("reward.xp", 1);
        long money = Math.round(baseMoney * m), xp = Math.round(baseXp * m);
        pay(p, money, xp);

        int every = Math.max(5, cfg().getInt("combo.meilenstein", 50));
        boolean milestone = c.count % every == 0;
        double cap = primeTime() ? cfg().getDouble("combo.max-prime", 15.0) : cfg().getDouble("combo.max", 10.0);
        if (milestone) {
            long bonus = Math.round(baseMoney * m * cfg().getDouble("combo.meilenstein-bonus", 25) * scale(p));
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
                    Msg.parse("<yellow>x" + fmt(m) + " <gray>bis zur vollen Stunde"),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1000), Duration.ofMillis(300))));
        }
        actionBar(p, c, m, money, xp, every);

        // Glücksfund: Grundchance plus etwas je Combo-Stufe
        double chance = cfg().getDouble("gluecksfund.chance", 0.015) + (m - 1) * cfg().getDouble("gluecksfund.combo-plus", 0.002);
        if (ThreadLocalRandom.current().nextDouble() < chance) find(p, b, rollTier(Tier.GEWOEHNLICH));
    }

    private void actionBar(Player p, Combo c, double m, long money, long xp, int every) {
        int into = c.count % every;
        int filled = Math.round(10f * into / every);
        StringBuilder bar = new StringBuilder();
        bar.append("<#FFD25A>").append("▮".repeat(filled)).append("<dark_gray>").append("▮".repeat(10 - filled));
        String prime = primeTime() ? "<#FF8A3D><b>PRIME</b> " : "";
        p.sendActionBar(Msg.parse(prime + "<#FFE066><b>COMBO " + c.count + "</b> <yellow>x" + fmt(m) + " " + bar
                + " <green>+" + Msg.coins(money) + "$ <aqua>+" + Msg.coins(xp) + " XP"));
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
        long money = Math.round(cfg().getLong("kiste.geld", 150) * grow * scale(p));
        long xp = Math.round(cfg().getLong("kiste.xp", 40) * grow);
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
    // Speichern
    // =================================================================================================================

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
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

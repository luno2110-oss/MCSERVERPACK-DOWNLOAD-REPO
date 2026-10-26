package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.jobs.Job;
import de.kuby.kubysurvival.jobs.JobService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Angeln in Survival (nach den Referenz-Clips): 48 Arten auf 6 Gewässern, Seltenheiten, Sterne nach Gewicht,
 * Angel-Punkte, Level und Ränge, Upgrades, Köder, Tagesaufträge, Fische des Tages, Goldene Stunde, Händler mit
 * Stundenbudget, Fischbuch mit Seitenbelohnung, Bestenlisten, Angel-Zonen (Bonus, Mythen-Fische, Schätze), Turnier.
 */
public final class AngelService {

    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final String SURVIVAL = ModeService.SURVIVAL;

    /** Angel-Zone: Rechteck, Teleport-Punkt ("Zur Angel-Zone") und Hologramm-Position. */
    public static final class Zone {
        public final String id;
        public final String world;
        public final int x1, z1, x2, z2, y;
        public String name;
        public Location tp;
        public Location holo;

        public Zone(String id, String world, int x1, int z1, int x2, int z2, int y, String name) {
            this.id = id;
            this.world = world;
            this.x1 = x1;
            this.z1 = z1;
            this.x2 = x2;
            this.z2 = z2;
            this.y = y;
            this.name = name;
        }

        public boolean contains(Location l) {
            return l != null && l.getWorld() != null && l.getWorld().getName().equals(world)
                    && l.getBlockX() >= x1 && l.getBlockX() <= x2 && l.getBlockZ() >= z1 && l.getBlockZ() <= z2;
        }

        public Location center(World w) {
            return new Location(w, (x1 + x2) / 2.0 + 0.5, y + 1.0, (z1 + z2) / 2.0 + 0.5);
        }
    }

    /** Ergebnis eines Fangs, für Anzeige und Animation. */
    public record Catch(FishSpecies species, double kg, int stars, int points, int amount, boolean record,
                        double previousBest, int levelBefore, int levelAfter, boolean zone, Water water,
                        String treasure, long autoSold, Water pageDone) {}

    /** Zeile einer Bestenliste. */
    public record TopEntry(UUID id, String name, String value) {}

    /** Verkaufsart beim Händler. */
    public enum SellMode {
        ALLES, KLEINE, SELTENE, TAGESFISCHE
    }

    public record SaleResult(int count, long money, long fullPrice, boolean capped) {}

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File file;
    private final Map<UUID, AngelProfile> profiles = new HashMap<>();
    private final Map<String, Zone> zones = new LinkedHashMap<>();
    private final Map<UUID, Long> pendingMoney = new HashMap<>();
    private final Map<UUID, Double> tournament = new HashMap<>();
    private long tournamentEnds;
    private final Map<String, List<TopEntry>> topCache = new HashMap<>();
    /** Geändert seit dem letzten Speichern (gespeichert wird jede Minute und beim Beenden). */
    private boolean dirty;
    private long topUpdated;

    public AngelService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "angeln.yml");
        load();
    }

    private long cfg(String path, long def) {
        return core.getConfig().getLong("angeln." + path, def);
    }

    public synchronized AngelProfile profile(UUID id) {
        AngelProfile a = profiles.computeIfAbsent(id, k -> new AngelProfile());
        String week = week();
        if (!week.equals(a.week)) {
            a.week = week;
            a.weekCatches = 0;
        }
        return a;
    }

    // =================================================================================================================
    // Level, Rang, Boni
    // =================================================================================================================

    /** Punkte, die man für ein Level insgesamt braucht (Level 1 ab 0, Level 2 ab 70, Level 8 ab 1.960). */
    public static long pointsFor(int level) {
        return 35L * level * (level - 1);
    }

    public static int levelOf(long points) {
        int level = 1;
        while (level < 100 && pointsFor(level + 1) <= points) level++;
        return level;
    }

    public static String rank(int level) {
        if (level >= 75) return "Angel-Legende";
        if (level >= 50) return "Meisterangler";
        if (level >= 35) return "Profi-Angler";
        if (level >= 20) return "Angler";
        if (level >= 10) return "Hobby-Angler";
        return "Anfänger";
    }

    /** Angler-Job (Haupt- oder Nebenjob): Bonus in Prozent auf Glück und Preis = Job-Level - 1. -1 ohne Job. */
    public int jobBonus(UUID id) {
        JobService jobs = addon.jobs();
        if (jobs == null) return -1;
        JobService.Worker w = jobs.worker(id);
        if (w.job != Job.ANGLER && w.sideJob != Job.ANGLER) return -1;
        return Math.max(0, jobs.jobLevel(w, Job.ANGLER) - 1);
    }

    public int jobLevel(UUID id) {
        JobService jobs = addon.jobs();
        return jobs == null ? 1 : jobs.jobLevel(jobs.worker(id), Job.ANGLER);
    }

    public static boolean night(World w) {
        if (w == null) return false;
        long t = w.getTime();
        return t >= 13000 && t <= 23000;
    }

    /** Glück in Prozent: Level, Glückshaken, Köder, Angel-Zone, Angler-Job. */
    public synchronized double luck(UUID id, boolean inZone, boolean night) {
        AngelProfile a = profile(id);
        double luck = 0.3 * a.level() + 4 * a.upgrade(Upgrade.GLUECKSHAKEN);
        if (a.active != null && a.bait(a.active) > 0 && (!a.active.nightLuck || night)) luck += a.active.luck;
        if (inZone) luck += 10;
        luck += Math.max(0, jobBonus(id));
        return luck;
    }

    /** Glück ohne Köder und Zone (für die Anzeige "Deine Angel"). */
    public synchronized double baseLuck(UUID id) {
        AngelProfile a = profile(id);
        return 0.3 * a.level() + 4 * a.upgrade(Upgrade.GLUECKSHAKEN);
    }

    /** Wartezeit kürzer in Prozent: Schnellspule, Köder, Angel-Zone. */
    public synchronized double faster(UUID id, boolean inZone) {
        AngelProfile a = profile(id);
        double f = 4 * a.upgrade(Upgrade.SCHNELLSPULE);
        if (a.active != null && a.bait(a.active) > 0) f += a.active.faster;
        if (inZone) f += 10;
        return f;
    }

    /** Schatzchance in Prozent (Schätze gibt es nur in Angel-Zonen). */
    public synchronized double treasureChance(UUID id) {
        AngelProfile a = profile(id);
        double c = 1.2 + 0.5 * a.upgrade(Upgrade.SCHATZSINN);
        if (a.active != null && a.bait(a.active) > 0) c += a.active.treasure;
        return c;
    }

    // =================================================================================================================
    // Zonen
    // =================================================================================================================

    public synchronized Zone zoneAt(Location l) {
        for (Zone z : zones.values()) if (z.contains(l)) return z;
        return null;
    }

    public synchronized List<Zone> zones() {
        return new ArrayList<>(zones.values());
    }

    public synchronized Zone zone(String id) {
        return zones.get(id);
    }

    public synchronized String createZone(String id, Location a, Location b, String name, Location tp) {
        if (!id.matches("[a-z0-9_-]{1,24}")) return "Ungültige ID. Erlaubt: Kleinbuchstaben, Ziffern, - und _.";
        if (zones.containsKey(id)) return "Eine Angel-Zone mit der ID " + id + " gibt es schon.";
        if (a == null || b == null) return "Setze erst beide Ecken: /kp angel pos1 und /kp angel pos2.";
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return "Beide Ecken müssen in derselben Welt liegen.";
        Zone z = new Zone(id, a.getWorld().getName(), Math.min(a.getBlockX(), b.getBlockX()),
                Math.min(a.getBlockZ(), b.getBlockZ()), Math.max(a.getBlockX(), b.getBlockX()),
                Math.max(a.getBlockZ(), b.getBlockZ()), Math.max(a.getBlockY(), b.getBlockY()), name);
        z.tp = tp == null ? null : tp.clone();
        zones.put(id, z);
        save();
        return null;
    }

    public synchronized boolean deleteZone(String id) {
        Zone z = zones.remove(id);
        if (z == null) return false;
        save();
        return true;
    }

    public synchronized void changed() {
        save();
    }

    /** Teleport-Ziel "Zur Angel-Zone": die erste Zone mit Teleport-Punkt, sonst die Mitte der ersten Zone. */
    public synchronized Location zoneTeleport() {
        for (Zone z : zones.values()) if (z.tp != null && z.tp.getWorld() != null) return z.tp.clone();
        for (Zone z : zones.values()) {
            World w = Bukkit.getWorld(z.world);
            if (w != null) {
                Location c = z.center(w);
                c.setY(w.getHighestBlockYAt(c) + 1);
                return c;
            }
        }
        return null;
    }

    // =================================================================================================================
    // Fische des Tages, Goldene Stunde
    // =================================================================================================================

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static String week() {
        LocalDate d = today();
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    /** Zwei Fische des Tages (+50 % beim Händler): einer gewöhnlich, einer ungewöhnlich. */
    public static List<FishSpecies> fishOfDay() {
        Random r = new Random(today().toEpochDay() * 7919L + 17);
        List<FishSpecies> g = new ArrayList<>(), u = new ArrayList<>();
        for (FishSpecies s : FishSpecies.ALL) {
            if (s.water() == Water.MYTHOS) continue;
            if (s.rarity() == Rarity.GEWOEHNLICH) g.add(s);
            if (s.rarity() == Rarity.UNGEWOEHNLICH) u.add(s);
        }
        return List.of(g.get(r.nextInt(g.size())), u.get(r.nextInt(u.size())));
    }

    /** Beginn der Goldenen Stunde heute (zwischen 15 und 21 Uhr, jeden Tag anders). */
    public static ZonedDateTime goldenStart(LocalDate day) {
        Random r = new Random(day.toEpochDay() * 31337L + 5);
        int minute = 15 * 60 + r.nextInt(6 * 60 + 1);
        return day.atStartOfDay(ZONE).plusMinutes(minute);
    }

    public static boolean goldenActive() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime s = goldenStart(today());
        return !now.isBefore(s) && now.isBefore(s.plusHours(1));
    }

    /** Sekunden bis zur nächsten Goldenen Stunde (0 wenn sie gerade läuft). */
    public static long secondsToGolden() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime s = goldenStart(today());
        if (now.isBefore(s)) return java.time.Duration.between(now, s).getSeconds();
        if (now.isBefore(s.plusHours(1))) return 0;
        return java.time.Duration.between(now, goldenStart(today().plusDays(1))).getSeconds();
    }

    public static long goldenSecondsLeft() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime end = goldenStart(today()).plusHours(1);
        return Math.max(0, java.time.Duration.between(now, end).getSeconds());
    }

    // =================================================================================================================
    // Fangen
    // =================================================================================================================

    /** Würfelt Art, Gewicht und Sterne aus und verbucht den Fang. */
    public synchronized Catch catchFish(Player p, Location hook) {
        UUID id = p.getUniqueId();
        AngelProfile a = profile(id);
        Zone zone = zoneAt(hook);
        boolean inZone = zone != null;
        boolean night = night(hook.getWorld());
        Water water = Water.at(hook);
        double luck = luck(id, inZone, night);

        // Art: Grundgewicht der Seltenheit, Glück verschiebt nach oben, verteilt auf die Arten der Seltenheit
        List<FishSpecies> pool = new ArrayList<>(FishSpecies.of(water));
        if (inZone) pool.addAll(FishSpecies.of(Water.MYTHOS));
        Map<Rarity, Integer> perRarity = new EnumMap<>(Rarity.class);
        for (FishSpecies s : pool) perRarity.merge(s.rarity(), 1, Integer::sum);
        double total = 0;
        double[] w = new double[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            FishSpecies s = pool.get(i);
            Rarity r = s.rarity();
            double weight = r.weight * (1 + luck / 100.0 * r.ordinal()) / perRarity.get(r);
            if (s.night() && night) weight *= 2;
            w[i] = weight;
            total += weight;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double roll = rnd.nextDouble(total);
        FishSpecies species = pool.get(pool.size() - 1);
        for (int i = 0; i < pool.size(); i++) {
            roll -= w[i];
            if (roll < 0) {
                species = pool.get(i);
                break;
            }
        }

        // Gewicht: kleine häufiger; Großfang-Rolle würfelt öfter und nimmt das Beste
        double q = 0;
        for (int i = 0; i <= a.upgrade(Upgrade.GROSSFANG); i++) q = Math.max(q, rnd.nextDouble());
        double kg = species.minKg() + (species.maxKg() - species.minKg()) * Math.pow(q, 1.6);
        kg = Math.round(kg * (kg < 10 ? 100 : 10)) / (kg < 10 ? 100.0 : 10.0);
        int stars = q < 0.35 ? 1 : q < 0.6 ? 2 : q < 0.8 ? 3 : q < 0.93 ? 4 : 5;

        // Statistik
        int levelBefore = a.level();
        double previous = a.best.getOrDefault(species.id(), 0.0);
        boolean record = previous > 0 && kg > previous;
        if (kg > previous) a.best.put(species.id(), kg);
        boolean firstOfSpecies = a.caught.getOrDefault(species.id(), 0) == 0;
        int amount = 1;
        if (a.doubleCatch > 0 && rnd.nextDouble() < 0.05 * a.doubleCatch) amount = 2;
        a.caught.merge(species.id(), amount, Integer::sum);
        a.catches += amount;
        a.weekCatches += amount;
        long now = System.currentTimeMillis();
        a.streak = now - a.lastCatch <= 120_000 ? a.streak + 1 : 1;
        a.bestStreak = Math.max(a.bestStreak, a.streak);
        a.lastCatch = now;
        int points = (int) Math.round(species.rarity().points * (0.8 + 0.1 * stars) * (record ? 1.2 : 1.0)) * amount;
        a.points += points;

        // Köder verbrauchen
        boolean usedBait = false;
        if (a.active != null && a.bait(a.active) > 0) {
            a.baits.merge(a.active, -1, Integer::sum);
            usedBait = true;
            if (a.bait(a.active) <= 0) {
                Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <gray>Dein " + a.active.label + " ist aufgebraucht.");
                a.active = null;
            }
        }

        // Schatz (nur in Angel-Zonen)
        String treasure = null;
        if (inZone && rnd.nextDouble() * 100 < treasureChance(id)) {
            treasure = treasure(p, a);
            a.treasures++;
        }

        // Fischbuch-Seite voll?
        Water pageDone = null;
        if (firstOfSpecies && !a.pagesDone.contains(species.water()) && a.caughtSpecies(species.water()) == 8) {
            a.pagesDone.add(species.water());
            pageDone = species.water();
            pay(id, cfg("page-coins", 3000));
            if (addon.tokens() != null) addon.tokens().give(id, cfg("page-tokens", 2), "Fischbuch-Seite " + species.water().label);
        }

        // Tagesaufträge
        ensureOrders(id, a);
        for (AngelProfile.Order o : a.orders) {
            if (o.finished) continue;
            int add = switch (o.type) {
                case "fish" -> amount;
                case "stars" -> stars >= 4 ? amount : 0;
                case "water" -> species.water().name().equals(o.param) ? amount : 0;
                case "rare" -> species.rarity().atLeast(Rarity.SELTEN) ? amount : 0;
                case "night" -> night ? amount : 0;
                case "bait" -> usedBait ? amount : 0;
                case "kg" -> (int) Math.ceil(kg * amount);
                default -> 0;
            };
            if (add > 0) advance(p, a, o, add);
        }

        // Auto-Verkauf bis Ungewöhnlich
        long autoSold = 0;
        if (a.autoSell && !species.rarity().atLeast(Rarity.SELTEN)) {
            autoSold = sellNow(p, a, species, stars, amount);
        }

        // Turnier
        if (tournamentRunning()) tournament.merge(id, kg * amount, Double::sum);

        int levelAfter = a.level();
        if (levelAfter > levelBefore) {
            long coins = 50L * levelAfter;
            pay(id, coins);
            if (levelAfter % 10 == 0 && addon.tokens() != null) addon.tokens().give(id, 1, "Angel-Level " + levelAfter);
        }
        dirty = true;
        return new Catch(species, kg, stars, points, amount, record, previous, levelBefore, levelAfter, inZone, water,
                treasure, autoSold, pageDone);
    }

    private String treasure(Player p, AngelProfile a) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double x = r.nextDouble();
        if (x < 0.55) {
            long coins = 150 + r.nextInt(451);
            pay(p.getUniqueId(), coins);
            return AngelText.money(coins);
        }
        if (x < 0.8) {
            Bait b = Bait.values()[r.nextInt(Bait.values().length)];
            a.baits.merge(b, 3, Integer::sum);
            return "3x " + b.label;
        }
        if (x < 0.95) {
            if (addon.tokens() != null) addon.tokens().give(p.getUniqueId(), 1, "Schatz beim Angeln");
            return "1 Token";
        }
        ItemStack book = new ItemStack(r.nextBoolean() ? Material.NAME_TAG : Material.HEART_OF_THE_SEA);
        giveItem(p, book);
        return book.getType() == Material.NAME_TAG ? "Namensschild" : "Herz des Meeres";
    }

    void giveItem(Player p, ItemStack item) {
        Map<Integer, ItemStack> rest = p.getInventory().addItem(item);
        for (ItemStack it : rest.values()) p.getWorld().dropItemNaturally(p.getLocation(), it);
    }

    void pay(UUID id, long amount) {
        if (amount <= 0) return;
        core.modes().depositIn(id, SURVIVAL, amount);
    }

    // =================================================================================================================
    // Händler
    // =================================================================================================================

    private static String hourKey() {
        ZonedDateTime n = ZonedDateTime.now(ZONE);
        return n.toLocalDate() + "T" + n.getHour();
    }

    public long hourBudget() {
        return cfg("dealer.hour-budget", 1000);
    }

    /** Noch so viel diese Stunde zum vollen Preis. */
    public synchronized long budgetLeft(UUID id) {
        AngelProfile a = profile(id);
        if (!hourKey().equals(a.dealerHour)) return hourBudget();
        return Math.max(0, hourBudget() - a.dealerSold);
    }

    /** Preis-Aufschlag in Prozent: Angler-Job, Goldene Stunde (+50), Fisch des Tages (+50). */
    public int bonusPercent(UUID id, FishSpecies s) {
        int b = Math.max(0, jobBonus(id));
        if (goldenActive()) b += 50;
        if (fishOfDay().contains(s)) b += 50;
        return b;
    }

    /** Preis eines Fisches beim Händler (volles Budget). */
    public long price(UUID id, FishSpecies s, int stars) {
        Rarity r = s.rarity();
        double f = Math.pow((Math.max(1, Math.min(5, stars)) - 1) / 4.0, 1.3);
        double base = r.minPrice + (r.maxPrice - r.minPrice) * f;
        return Math.max(1, Math.round(base * (100 + bonusPercent(id, s)) / 100.0));
    }

    private boolean matches(SellMode mode, FishSpecies s) {
        return switch (mode) {
            case ALLES -> true;
            case KLEINE -> !s.rarity().atLeast(Rarity.SELTEN);
            case SELTENE -> s.rarity().atLeast(Rarity.SELTEN);
            case TAGESFISCHE -> fishOfDay().contains(s);
        };
    }

    /** Wert der Fische im Inventar zum vollen Preis. */
    public long inventoryValue(Player p, SellMode mode) {
        long sum = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            AngelItems.FishItem f = AngelItems.read(it);
            if (f == null || !matches(mode, f.species())) continue;
            sum += price(p.getUniqueId(), f.species(), f.stars()) * it.getAmount();
        }
        return sum;
    }

    /** Bucht einen Verkauf: über dem Stundenbudget gibt es nur noch einen Teil des Preises. */
    private long book(AngelProfile a, long full) {
        String h = hourKey();
        if (!h.equals(a.dealerHour)) {
            a.dealerHour = h;
            a.dealerSold = 0;
        }
        long left = Math.max(0, hourBudget() - a.dealerSold);
        long atFull = Math.min(full, left);
        long rest = full - atFull;
        long reduced = Math.round(rest * cfg("dealer.over-budget-percent", 20) / 100.0);
        a.dealerSold += atFull;
        return atFull + reduced;
    }

    private long sellNow(Player p, AngelProfile a, FishSpecies s, int stars, int amount) {
        long full = price(p.getUniqueId(), s, stars) * amount;
        long money = book(a, full);
        a.earned += money;
        pay(p.getUniqueId(), money);
        sold(p, a, money);
        return money;
    }

    public synchronized SaleResult sell(Player p, SellMode mode) {
        AngelProfile a = profile(p.getUniqueId());
        PlayerInventory inv = p.getInventory();
        long full = 0;
        int count = 0;
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            AngelItems.FishItem f = AngelItems.read(contents[i]);
            if (f == null || !matches(mode, f.species())) continue;
            full += price(p.getUniqueId(), f.species(), f.stars()) * contents[i].getAmount();
            count += contents[i].getAmount();
            contents[i] = null;
        }
        if (count == 0) return new SaleResult(0, 0, 0, false);
        inv.setStorageContents(contents);
        long leftBefore = budgetLeft(p.getUniqueId());
        long money = book(a, full);
        a.earned += money;
        pay(p.getUniqueId(), money);
        sold(p, a, money);
        dirty = true;
        return new SaleResult(count, money, full, full > leftBefore);
    }

    private void sold(Player p, AngelProfile a, long money) {
        ensureOrders(p.getUniqueId(), a);
        for (AngelProfile.Order o : a.orders) {
            if (!o.finished && o.type.equals("sell")) advance(p, a, o, (int) Math.min(Integer.MAX_VALUE, money));
        }
    }

    // =================================================================================================================
    // Upgrades und Köder
    // =================================================================================================================

    private String survivalOnly(Player p) {
        return SURVIVAL.equals(core.modes().modeOf(p.getUniqueId())) ? null : "Das geht nur in Survival.";
    }

    public synchronized String buyUpgrade(Player p, Upgrade u) {
        String err = survivalOnly(p);
        if (err != null) return err;
        AngelProfile a = profile(p.getUniqueId());
        int level = a.upgrade(u);
        if (level >= u.max) return u.label + " ist schon auf der höchsten Stufe.";
        long price = u.price(level);
        if (!core.economy().withdraw(p.getUniqueId(), price)) return "Dafür fehlen dir Coins (" + AngelText.money(price) + ").";
        a.upgrades.put(u, level + 1);
        dirty = true;
        return null;
    }

    public long autoSellPrice() {
        return cfg("auto-sell-tokens", 150);
    }

    public synchronized String buyAutoSell(Player p) {
        String err = survivalOnly(p);
        if (err != null) return err;
        AngelProfile a = profile(p.getUniqueId());
        if (a.autoSell) return "Auto-Verkauf hast du schon.";
        if (!addon.jobs().takeTokens(p.getUniqueId(), autoSellPrice())) return "Dafür brauchst du " + autoSellPrice() + " Tokens.";
        a.autoSell = true;
        dirty = true;
        return null;
    }

    /** Doppelfang (Token-Shop): je Stufe 5 % Chance auf zwei Fische. */
    public static final int DOUBLE_MAX = 3;

    public long doublePrice(int level) {
        return cfg("double-catch-tokens", 100) * (level + 1);
    }

    public synchronized String buyDouble(Player p) {
        String err = survivalOnly(p);
        if (err != null) return err;
        AngelProfile a = profile(p.getUniqueId());
        if (a.doubleCatch >= DOUBLE_MAX) return "Doppelfang ist schon auf der höchsten Stufe.";
        long price = doublePrice(a.doubleCatch);
        if (!addon.jobs().takeTokens(p.getUniqueId(), price)) return "Dafür brauchst du " + price + " Tokens.";
        a.doubleCatch++;
        dirty = true;
        return null;
    }

    public synchronized String buyBait(Player p, Bait b) {
        String err = survivalOnly(p);
        if (err != null) return err;
        if (!core.economy().withdraw(p.getUniqueId(), b.price10)) return "Dafür fehlen dir Coins (" + AngelText.money(b.price10) + ").";
        profile(p.getUniqueId()).baits.merge(b, 10, Integer::sum);
        dirty = true;
        return null;
    }

    /** Fehlende Zutaten als Text, null wenn alles da ist. */
    public String missing(Player p, Bait b) {
        List<String> miss = new ArrayList<>();
        for (Map.Entry<Material, Integer> e : b.recipe.entrySet()) {
            int have = 0;
            for (ItemStack it : p.getInventory().getStorageContents()) {
                if (it != null && it.getType() == e.getKey() && !it.hasItemMeta()) have += it.getAmount();
            }
            if (have < e.getValue()) miss.add((e.getValue() - have) + "x " + materialName(e.getKey()));
        }
        return miss.isEmpty() ? null : String.join(", ", miss);
    }

    public synchronized String craftBait(Player p, Bait b) {
        String err = survivalOnly(p);
        if (err != null) return err;
        String miss = missing(p, b);
        if (miss != null) return "Dir fehlt: " + miss + ".";
        for (Map.Entry<Material, Integer> e : b.recipe.entrySet()) {
            int need = e.getValue();
            ItemStack[] contents = p.getInventory().getStorageContents();
            for (int i = 0; i < contents.length && need > 0; i++) {
                ItemStack it = contents[i];
                if (it == null || it.getType() != e.getKey() || it.hasItemMeta()) continue;
                int take = Math.min(need, it.getAmount());
                it.setAmount(it.getAmount() - take);
                if (it.getAmount() <= 0) contents[i] = null;
                need -= take;
            }
            p.getInventory().setStorageContents(contents);
        }
        profile(p.getUniqueId()).baits.merge(b, b.craftAmount, Integer::sum);
        dirty = true;
        return null;
    }

    /** Köder benutzen oder (wenn schon aktiv) abnehmen. */
    public synchronized String toggleBait(Player p, Bait b) {
        AngelProfile a = profile(p.getUniqueId());
        if (a.active == b) {
            a.active = null;
            dirty = true;
            return null;
        }
        if (a.bait(b) <= 0) return "Du hast keinen " + b.label + " im Beutel.";
        a.active = b;
        dirty = true;
        return null;
    }

    public static String materialName(Material m) {
        return switch (m) {
            case DIRT -> "Erde";
            case BONE_MEAL -> "Knochenmehl";
            case GLOWSTONE_DUST -> "Glowstone-Staub";
            case IRON_NUGGET -> "Eisenklumpen";
            case STRING -> "Faden";
            case GLOW_INK_SAC -> "Leuchttintenbeutel";
            case SPIDER_EYE -> "Spinnenauge";
            case GOLD_NUGGET -> "Goldklumpen";
            default -> m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    // =================================================================================================================
    // Tagesaufträge
    // =================================================================================================================

    /** Neue Aufträge um Mitternacht. */
    public static long secondsToNewOrders() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        return java.time.Duration.between(now, today().plusDays(1).atStartOfDay(ZONE)).getSeconds();
    }

    public synchronized List<AngelProfile.Order> orders(UUID id) {
        AngelProfile a = profile(id);
        ensureOrders(id, a);
        List<AngelProfile.Order> out = new ArrayList<>();
        for (AngelProfile.Order o : a.orders) out.add(o.copy());
        return out;
    }

    private void ensureOrders(UUID id, AngelProfile a) {
        String day = today().toString();
        if (day.equals(a.orderDay) && a.orders.size() == 3) return;
        a.orderDay = day;
        a.orders.clear();
        a.bonusDone = false;
        Random r = new Random(today().toEpochDay() * 1_000_003L ^ id.getMostSignificantBits() ^ id.getLeastSignificantBits());
        List<String> types = new ArrayList<>(List.of("fish", "stars", "water", "rare", "night", "bait", "kg", "sell"));
        // Der erste Auftrag ist immer "Fische fangen", die anderen zufällig
        types.remove("fish");
        java.util.Collections.shuffle(types, r);
        List<String> pick = new ArrayList<>(List.of("fish"));
        pick.addAll(types.subList(0, 2));
        for (String t : pick) {
            AngelProfile.Order o = new AngelProfile.Order();
            o.type = t;
            switch (t) {
                case "fish" -> { o.goal = 10 + r.nextInt(3) * 5; o.money = o.goal * 50L / 3; o.points = o.goal * 8 / 3; }
                case "stars" -> { o.goal = 2 + r.nextInt(3); o.money = o.goal * 230L; o.points = o.goal * 30; }
                case "water" -> {
                    Water w = Water.values()[r.nextInt(5)];
                    o.param = w.name();
                    o.goal = 4 + r.nextInt(5);
                    o.money = o.goal * 250L / 3;
                    o.points = o.goal * 10;
                }
                case "rare" -> { o.goal = 2 + r.nextInt(2); o.money = o.goal * 300L; o.points = o.goal * 40; }
                case "night" -> { o.goal = 5 + r.nextInt(6); o.money = o.goal * 50L; o.points = o.goal * 6; }
                case "bait" -> { o.goal = 5 + r.nextInt(6); o.money = o.goal * 45L; o.points = o.goal * 5; }
                case "kg" -> { o.goal = 20 + r.nextInt(5) * 10; o.money = o.goal * 10L; o.points = o.goal; }
                case "sell" -> { o.goal = 200 + r.nextInt(5) * 50; o.money = o.goal; o.points = o.goal / 6; }
                default -> { }
            }
            a.orders.add(o);
        }
    }

    public static String orderTitle(AngelProfile.Order o) {
        return switch (o.type) {
            case "fish" -> "Fange " + o.goal + " Fische";
            case "stars" -> "Fange " + o.goal + " Fische mit 4 oder 5 Sternen";
            case "water" -> {
                Water w;
                try {
                    w = Water.valueOf(o.param);
                } catch (IllegalArgumentException e) {
                    w = Water.FLUSS;
                }
                yield "Fange " + o.goal + " Fische aus " + w.label;
            }
            case "rare" -> "Fange " + o.goal + " Fische ab Selten";
            case "night" -> "Fange " + o.goal + " Fische in der Nacht";
            case "bait" -> "Fange " + o.goal + " Fische mit Köder";
            case "kg" -> "Fange zusammen " + o.goal + " kg Fisch";
            case "sell" -> "Verkaufe Fische für " + AngelText.money(o.goal);
            default -> o.type;
        };
    }

    private void advance(Player p, AngelProfile a, AngelProfile.Order o, int add) {
        o.done = Math.min(o.goal, o.done + add);
        if (o.done < o.goal) return;
        o.finished = true;
        pay(p.getUniqueId(), o.money);
        a.points += o.points;
        Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <gold>Tagesauftrag erledigt: <white>" + Msg.escape(orderTitle(o))
                + " <gray>· <gold>+" + AngelText.money(o.money) + " <gray>· <aqua>+" + o.points + " Punkte");
        Sfx.play(core, p, Sfx.Kind.CHIME);
        if (!a.bonusDone && a.ordersDone() == 3) {
            a.bonusDone = true;
            if (addon.tokens() != null) addon.tokens().give(p.getUniqueId(), 1, "alle Tagesaufträge");
            Bait b = Bait.values()[ThreadLocalRandom.current().nextInt(Bait.values().length)];
            a.baits.merge(b, 3, Integer::sum);
            Msg.send(p, "<light_purple><b>BONUS</b> <dark_gray>▶ <white>Alle drei Aufträge erledigt: <light_purple>1 Token "
                    + "<white>und <aqua>3x " + b.label + "<white>!");
            Sfx.play(core, p, Sfx.Kind.VICTORY);
        }
    }

    // =================================================================================================================
    // Bestenlisten (alle zwei Minuten neu)
    // =================================================================================================================

    public synchronized List<TopEntry> top(String tab) {
        if (System.currentTimeMillis() - topUpdated > 120_000 || topCache.isEmpty()) refreshTop();
        return topCache.getOrDefault(tab, List.of());
    }

    private String nameOf(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    private void refreshTop() {
        topUpdated = System.currentTimeMillis();
        String week = week();
        List<Map.Entry<UUID, AngelProfile>> all = new ArrayList<>(profiles.entrySet());
        topCache.put("woche", list(all.stream().filter(e -> week.equals(e.getValue().week) && e.getValue().weekCatches > 0)
                .sorted(Comparator.comparingInt((Map.Entry<UUID, AngelProfile> e) -> -e.getValue().weekCatches))
                .map(e -> new TopEntry(e.getKey(), nameOf(e.getKey()), AngelText.num(e.getValue().weekCatches) + " Fänge")).toList()));
        topCache.put("raenge", list(all.stream().filter(e -> e.getValue().points > 0)
                .sorted(Comparator.comparingLong((Map.Entry<UUID, AngelProfile> e) -> -e.getValue().points))
                .map(e -> new TopEntry(e.getKey(), nameOf(e.getKey()), "Level " + e.getValue().level() + " · "
                        + rank(e.getValue().level()))).toList()));
        topCache.put("faenge", list(all.stream().filter(e -> e.getValue().catches > 0)
                .sorted(Comparator.comparingInt((Map.Entry<UUID, AngelProfile> e) -> -e.getValue().catches))
                .map(e -> new TopEntry(e.getKey(), nameOf(e.getKey()), AngelText.num(e.getValue().catches) + " Fänge")).toList()));
        // Rekorde: je Art der schwerste Fang, sortiert nach Seltenheit und Anteil am Höchstgewicht
        List<TopEntry> rec = new ArrayList<>();
        List<FishSpecies> species = new ArrayList<>(FishSpecies.ALL);
        species.sort(Comparator.comparingInt((FishSpecies s) -> -s.rarity().ordinal()));
        for (FishSpecies s : species) {
            UUID bestId = null;
            double bestKg = 0;
            for (Map.Entry<UUID, AngelProfile> e : all) {
                double kg = e.getValue().best.getOrDefault(s.id(), 0.0);
                if (kg > bestKg) {
                    bestKg = kg;
                    bestId = e.getKey();
                }
            }
            if (bestId != null) rec.add(new TopEntry(bestId, nameOf(bestId), s.name() + " · " + AngelText.kg(bestKg)));
            if (rec.size() >= 10) break;
        }
        topCache.put("rekorde", rec);
    }

    private static List<TopEntry> list(List<TopEntry> l) {
        return new ArrayList<>(l.subList(0, Math.min(10, l.size())));
    }

    /** Server-Rekord einer Art: Spieler und Gewicht. */
    public synchronized Map.Entry<UUID, Double> recordOf(String fishId) {
        Map.Entry<UUID, Double> best = null;
        for (Map.Entry<UUID, AngelProfile> e : profiles.entrySet()) {
            Double kg = e.getValue().best.get(fishId);
            if (kg != null && (best == null || kg > best.getValue())) best = Map.entry(e.getKey(), kg);
        }
        return best;
    }

    // =================================================================================================================
    // Turnier (Admin)
    // =================================================================================================================

    public synchronized boolean tournamentRunning() {
        return tournamentEnds > System.currentTimeMillis();
    }

    public synchronized long tournamentSecondsLeft() {
        return Math.max(0, (tournamentEnds - System.currentTimeMillis()) / 1000);
    }

    public synchronized String startTournament(int minutes) {
        if (tournamentRunning()) return "Es läuft schon ein Turnier.";
        if (minutes < 1 || minutes > 240) return "Dauer zwischen 1 und 240 Minuten.";
        tournament.clear();
        tournamentEnds = System.currentTimeMillis() + minutes * 60_000L;
        save();
        core.notifications().send("notify.angel", "<aqua><b>ANGEL-TURNIER</b> <dark_gray>▶ <white>Startet jetzt für " + minutes
                + " Minuten! <gray>Das größte Gesamtgewicht gewinnt. /angeln");
        return null;
    }

    public synchronized String stopTournament() {
        if (!tournamentRunning()) return "Es läuft kein Turnier.";
        finishTournament();
        return null;
    }

    public synchronized List<Map.Entry<UUID, Double>> tournamentTop(int n) {
        List<Map.Entry<UUID, Double>> l = new ArrayList<>(tournament.entrySet());
        l.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
        return new ArrayList<>(l.subList(0, Math.min(n, l.size())));
    }

    private void finishTournament() {
        tournamentEnds = 0;
        List<Map.Entry<UUID, Double>> top = tournamentTop(10);
        List<Long> rewards = core.getConfig().getLongList("angeln.tournament.rewards");
        if (rewards.isEmpty()) rewards = List.of(5000L, 2500L, 1000L);
        core.notifications().send("notify.angel", "<aqua><b>ANGEL-TURNIER</b> <dark_gray>▶ <white>Das Turnier ist vorbei!");
        if (top.isEmpty()) core.notifications().send("notify.angel", "<gray>Niemand hat etwas gefangen.");
        for (int i = 0; i < Math.min(rewards.size(), top.size()); i++) {
            UUID id = top.get(i).getKey();
            long money = rewards.get(i);
            pendingMoney.merge(id, money, Long::sum);
            core.notifications().send("notify.angel", "<gold>" + (i + 1) + ". <white>" + nameOf(id) + " <gray>"
                    + AngelText.kg(top.get(i).getValue()) + " <gold>+" + AngelText.money(money));
        }
        tournament.clear();
        save();
        payPending();
    }

    /** Jede Minute: Turnier-Ende, Preisgeld, Goldene Stunde ansagen. */
    private boolean goldenAnnounced;

    public synchronized void tick() {
        if (dirty) save();
        if (tournamentEnds > 0 && !tournamentRunning()) finishTournament();
        payPending();
        boolean golden = goldenActive();
        if (golden && !goldenAnnounced) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) continue;
                Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <gold><b>Goldene Stunde!</b> <white>Eine Stunde lang "
                        + "<green>+50 % <white>beim Fischhändler.");
            }
        }
        goldenAnnounced = golden;
    }

    private void payPending() {
        if (pendingMoney.isEmpty()) return;
        for (UUID id : new ArrayList<>(pendingMoney.keySet())) {
            long money = pendingMoney.remove(id);
            pay(id, money);
            Player p = Bukkit.getPlayer(id);
            if (p != null) Msg.send(p, "<aqua>Turnier-Preis: <gold>+" + AngelText.money(money) + " <gray>auf dein Survival-Konto.");
        }
        dirty = true;
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private void load() {
        File old = new File(addon.getDataFolder(), "fishing.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file.exists() ? file : old);
        boolean migrate = !file.exists() && old.exists();
        ConfigurationSection players = yml.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                UUID id;
                try {
                    id = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                ConfigurationSection s = players.getConfigurationSection(key);
                if (s == null) continue;
                AngelProfile a = new AngelProfile();
                if (migrate) {
                    a.points = s.getLong("xp");
                } else {
                    a.points = s.getLong("points");
                    a.catches = s.getInt("catches");
                    a.weekCatches = s.getInt("week-catches");
                    a.week = s.getString("week", "");
                    a.earned = s.getLong("earned");
                    a.bestStreak = s.getInt("best-streak");
                    a.treasures = s.getInt("treasures");
                    a.autoSell = s.getBoolean("auto-sell");
                    a.doubleCatch = s.getInt("double-catch");
                    a.active = Bait.of(s.getString("active"));
                    a.orderDay = s.getString("order-day", "");
                    a.bonusDone = s.getBoolean("bonus-done");
                    a.dealerHour = s.getString("dealer-hour", "");
                    a.dealerSold = s.getLong("dealer-sold");
                    for (Upgrade u : Upgrade.values()) a.upgrades.put(u, Math.min(u.max, s.getInt("upgrades." + u.name())));
                    for (Bait b : Bait.values()) a.baits.put(b, Math.max(0, s.getInt("baits." + b.name())));
                    for (String w : s.getStringList("pages")) {
                        try {
                            a.pagesDone.add(Water.valueOf(w));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    ConfigurationSection os = s.getConfigurationSection("orders");
                    if (os != null) {
                        for (String k : os.getKeys(false)) {
                            AngelProfile.Order o = new AngelProfile.Order();
                            o.type = os.getString(k + ".type", "fish");
                            o.param = os.getString(k + ".param", "");
                            o.goal = os.getInt(k + ".goal");
                            o.done = os.getInt(k + ".done");
                            o.money = os.getLong(k + ".money");
                            o.points = os.getInt(k + ".points");
                            o.finished = os.getBoolean(k + ".finished");
                            a.orders.add(o);
                        }
                    }
                }
                ConfigurationSection c = s.getConfigurationSection("caught");
                if (c != null) {
                    for (String f : c.getKeys(false)) {
                        String fid = migrate ? migrateId(f) : f;
                        if (fid != null && FishSpecies.byId(fid) != null) a.caught.merge(fid, c.getInt(f), Integer::sum);
                    }
                }
                ConfigurationSection b = s.getConfigurationSection("best");
                if (b != null) {
                    for (String f : b.getKeys(false)) {
                        String fid = migrate ? migrateId(f) : f;
                        if (fid != null && FishSpecies.byId(fid) != null) a.best.merge(fid, b.getDouble(f), Math::max);
                    }
                }
                if (migrate) for (int v : a.caught.values()) a.catches += v;
                profiles.put(id, a);
            }
        }
        ConfigurationSection z = yml.getConfigurationSection("zones");
        if (z != null) {
            for (String id : z.getKeys(false)) {
                ConfigurationSection s = z.getConfigurationSection(id);
                if (s == null) continue;
                Zone zone = new Zone(id, s.getString("world"), s.getInt("x1"), s.getInt("z1"), s.getInt("x2"),
                        s.getInt("z2"), s.getInt("y"), s.getString("name", id));
                zone.tp = s.getLocation("tp");
                zone.holo = s.getLocation("holo");
                zones.put(id, zone);
            }
        }
        ConfigurationSection pm = yml.getConfigurationSection("pending");
        if (pm != null) {
            for (String key : pm.getKeys(false)) {
                try {
                    pendingMoney.put(UUID.fromString(key), pm.getLong(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        tournamentEnds = yml.getLong("tournament.ends");
        ConfigurationSection t = yml.getConfigurationSection("tournament.weights");
        if (t != null) {
            for (String key : t.getKeys(false)) {
                try {
                    tournament.put(UUID.fromString(key), t.getDouble(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (migrate) {
            save();
            addon.getLogger().info("Angeln: Daten aus fishing.yml übernommen (Zonen, Punkte, Fänge).");
        }
    }

    /** Alte Fisch-IDs (vor dem Umbau) auf die neuen Arten. */
    private static String migrateId(String old) {
        return switch (old) {
            case "barsch" -> "flussbarsch";
            case "sardine" -> "lodde";
            case "koi" -> "karpfen";
            default -> old;
        };
    }

    public synchronized void save() {
        dirty = false;
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, AngelProfile> e : profiles.entrySet()) {
            AngelProfile a = e.getValue();
            String base = "players." + e.getKey() + ".";
            out.set(base + "points", a.points);
            out.set(base + "catches", a.catches);
            out.set(base + "week-catches", a.weekCatches);
            out.set(base + "week", a.week);
            out.set(base + "earned", a.earned);
            out.set(base + "best-streak", a.bestStreak);
            out.set(base + "treasures", a.treasures);
            out.set(base + "auto-sell", a.autoSell);
            out.set(base + "double-catch", a.doubleCatch);
            out.set(base + "active", a.active == null ? null : a.active.name());
            out.set(base + "order-day", a.orderDay);
            out.set(base + "bonus-done", a.bonusDone);
            out.set(base + "dealer-hour", a.dealerHour);
            out.set(base + "dealer-sold", a.dealerSold);
            for (Map.Entry<Upgrade, Integer> u : a.upgrades.entrySet()) {
                if (u.getValue() > 0) out.set(base + "upgrades." + u.getKey().name(), u.getValue());
            }
            for (Map.Entry<Bait, Integer> b : a.baits.entrySet()) {
                if (b.getValue() > 0) out.set(base + "baits." + b.getKey().name(), b.getValue());
            }
            out.set(base + "pages", a.pagesDone.stream().map(Enum::name).toList());
            for (int i = 0; i < a.orders.size(); i++) {
                AngelProfile.Order o = a.orders.get(i);
                String ob = base + "orders." + i + ".";
                out.set(ob + "type", o.type);
                out.set(ob + "param", o.param);
                out.set(ob + "goal", o.goal);
                out.set(ob + "done", o.done);
                out.set(ob + "money", o.money);
                out.set(ob + "points", o.points);
                out.set(ob + "finished", o.finished);
            }
            for (Map.Entry<String, Integer> c : a.caught.entrySet()) out.set(base + "caught." + c.getKey(), c.getValue());
            for (Map.Entry<String, Double> b : a.best.entrySet()) out.set(base + "best." + b.getKey(), b.getValue());
        }
        for (Zone z : zones.values()) {
            String base = "zones." + z.id + ".";
            out.set(base + "world", z.world);
            out.set(base + "x1", z.x1);
            out.set(base + "z1", z.z1);
            out.set(base + "x2", z.x2);
            out.set(base + "z2", z.z2);
            out.set(base + "y", z.y);
            out.set(base + "name", z.name);
            out.set(base + "tp", z.tp);
            out.set(base + "holo", z.holo);
        }
        for (Map.Entry<UUID, Long> e : pendingMoney.entrySet()) out.set("pending." + e.getKey(), e.getValue());
        out.set("tournament.ends", tournamentEnds);
        for (Map.Entry<UUID, Double> e : tournament.entrySet()) out.set("tournament.weights." + e.getKey(), e.getValue());
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            addon.getLogger().warning("angeln.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}

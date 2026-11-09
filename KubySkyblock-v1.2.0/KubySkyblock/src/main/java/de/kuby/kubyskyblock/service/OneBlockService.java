package de.kuby.kubyskyblock.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.service.TeamService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Die (höchstens drei) One-Block-Plattformen. Der Block wird nach jedem Abbau durch einen zufälligen ersetzt; wer ihn
 * abbaut, bekommt den Loot, Geld und XP.
 *
 * Ein Skyblock-Team kann eine Plattform beanspruchen (eine je Team, Preis in der config). Dann dürfen nur noch die
 * Mitglieder dort abbauen, und jeder Abbau füllt die Block-XP des Teams. Dazu können die Mitglieder eigene XP einzahlen.
 * Mit Block-XP kauft das Team Upgrades: Glück (seltenere Blöcke), Cooldown (Block schneller zurück), Tempo (Eile am
 * Block), XP- und Geld-Bonus je Abbau und doppelte Beute. Die Upgrades gehören dem Team und bleiben, wenn es die
 * Plattform verliert (wer länger als verfall-tage nicht abbaut, gibt sie frei).
 * Daten in oneblock.yml.
 */
public final class OneBlockService {

    /** Upgrades des Blocks: Name, Wirkung, Höchst-Level (20, drei Stufen), Grundkosten und Faktor je Level (Block-XP). */
    public enum Upgrade {
        GLUECK("Glück", "Seltene Blöcke kommen öfter", 50, 125, 1.245),
        COOLDOWN("Cooldown", "Der neue Block ist schneller da", 50, 100, 1.225),
        TEMPO("Tempo", "Eile am Block: schneller abbauen", 50, 150, 1.218),
        XP("XP-Bonus", "Mehr XP je Abbau", 50, 100, 1.225),
        GELD("Geld-Bonus", "Mehr Geld je Abbau", 50, 100, 1.225),
        BEUTE("Doppel-Beute", "Chance auf doppelten Loot", 50, 150, 1.245);

        public final String label, desc;
        final int max;
        final long base;
        final double factor;

        Upgrade(String label, String desc, int max, long base, double factor) {
            this.label = label;
            this.desc = desc;
            this.max = max;
            this.base = base;
            this.factor = factor;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Fortschritt eines Teams (gehört dem Team, nicht der Plattform). */
    public static final class Progress {
        public long xp;
        public long mined;
        public long earned;
        final EnumMap<Upgrade, Integer> levels = new EnumMap<>(Upgrade.class);

        public int level(Upgrade u) {
            return levels.getOrDefault(u, 0);
        }

        /** Block-Stufe: Summe aller Upgrade-Stufen. */
        public int total() {
            int n = 0;
            for (int v : levels.values()) n += v;
            return n;
        }
    }

    /** Eine Plattform: Lage und (falls beansprucht) das Team. */
    public static final class Platform {
        public final int index;
        public final int x, y, z;
        public String team;
        public String teamName;
        public long since, active;
        boolean regenerating;
        UUID display;
        /** Spieler, die diesen One-Block schon gefunden haben (erst dann: hinreisen, beanspruchen). */
        public final java.util.Set<UUID> found = new java.util.HashSet<>();

        Platform(int index, int x, int y, int z) {
            this.index = index;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public boolean claimed() {
            return team != null;
        }

        public String name() {
            return "One-Block " + (index + 1);
        }
    }

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final Random rnd = new Random();
    private final File file;
    private final List<Platform> platforms = new ArrayList<>();
    private final Map<String, Progress> progress = new HashMap<>();
    private boolean dirty;
    /** Format der gespeicherten Upgrade-Stände (UpgradeLevels.VERSION = Level 0 bis 20). */
    private int upgradeVersion = UpgradeLevels.VERSION;

    public OneBlockService(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "oneblock.yml");
        loadPlatforms();
        load();
        Bukkit.getScheduler().runTaskTimer(addon, this::tick, 40L, 20L);
        Bukkit.getScheduler().runTaskLater(addon, this::relocateIfNeeded, 100L);
        Bukkit.getScheduler().runTaskTimer(addon, this::saveIfDirty, 1200L, 1200L);
    }

    // =================================================================================================================
    // Plattformen
    // =================================================================================================================

    /** One-Blöcke weit verteilt auf der Map (müssen erst gefunden werden)? Sonst feste Plätze aus oneblock.positions. */
    public boolean scattered() {
        return addon.getConfig().getBoolean("oneblock.verteilt", true);
    }

    /** Neu ausgewürfelte Plätze, die beim Start noch gebaut werden müssen (alte Plattformen am Spawn werden abgebaut). */
    private List<int[]> oldPlaces = null;

    private void loadPlatforms() {
        platforms.clear();
        if (scattered()) {
            YamlConfiguration y = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
            List<String> stored = y.getStringList("positions");
            if (stored.size() < 3) {
                stored = scatter();
                oldPlaces = new ArrayList<>();
                for (String s : addon.getConfig().getStringList("oneblock.positions")) {
                    String[] q = s.split(";");
                    try {
                        oldPlaces.add(new int[]{Integer.parseInt(q[0].trim()), Integer.parseInt(q[1].trim()), Integer.parseInt(q[2].trim())});
                    } catch (RuntimeException ignored) {
                        // ungültig
                    }
                }
                dirty = true;
            }
            addPlatforms(stored);
            return;
        }
        addPlatforms(addon.getConfig().getStringList("oneblock.positions"));
    }

    /** Drei Plätze in drei Richtungen (je etwa 120 Grad auseinander), weit draußen, nicht bei Loot-Inseln. */
    private List<String> scatter() {
        int min = Math.max(500, addon.getConfig().getInt("oneblock.abstand-min", 6000));
        int max = Math.max(min + 100, addon.getConfig().getInt("oneblock.abstand-max", 20000));
        int border = addon.getConfig().getInt("insel.weltgrenze", 50_000) / 2 - 300;
        max = Math.min(max, border);
        min = Math.min(min, max - 100);
        int y = addon.getConfig().getInt("oneblock.hoehe", 100);
        double base = rnd.nextDouble() * Math.PI * 2;
        List<String> out = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            int x = 0, z = 0;
            for (int tries = 0; tries < 20; tries++) {
                double a = base + k * Math.PI * 2 / 3 + (rnd.nextDouble() - 0.5) * Math.toRadians(50);
                double d = min + rnd.nextDouble() * (max - min);
                x = (int) Math.round(Math.cos(a) * d);
                z = (int) Math.round(Math.sin(a) * d);
                if (!nearLoot(x, z)) break;
            }
            out.add(x + ";" + y + ";" + z);
        }
        return out;
    }

    private boolean nearLoot(int x, int z) {
        if (addon.loot() == null) return false;
        for (var li : addon.loot().all().values()) {
            if (Math.abs(li.x() - x) < 150 && Math.abs(li.z() - z) < 150) return true;
        }
        return false;
    }

    private void addPlatforms(List<String> pos) {
        for (String s : pos) {
            if (platforms.size() >= 3) break;
            String[] p = s.split(";");
            if (p.length < 3) continue;
            try {
                platforms.add(new Platform(platforms.size(), Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
                        Integer.parseInt(p[2].trim())));
            } catch (NumberFormatException e) {
                addon.getLogger().warning("oneblock.positions: ungültig: " + s);
            }
        }
    }

    public List<Platform> platforms() {
        return platforms;
    }

    private World world() {
        return Bukkit.getWorld(core.modes().worldOf(ModeService.SKYBLOCK));
    }

    /** Baut die Plattformen: kleine schwebende Insel (Gras, Erde, Stein nach unten spitz), Laterne, der One-Block in der
     *  Mitte. Lädt die Chunks dafür (die Plätze liegen weit draußen). */
    public int setup(World w) {
        int made = 0;
        for (Platform pf : platforms) {
            w.getChunkAt(pf.x >> 4, pf.z >> 4);
            double[] radius = {4.4, 3.6, 2.6, 1.6};
            for (int layer = 0; layer < radius.length; layer++) {
                int r = (int) Math.ceil(radius[layer]);
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (dx * dx + dz * dz > radius[layer] * radius[layer]) continue;
                        Material m = layer == 0 ? Material.GRASS_BLOCK : layer == 1 ? Material.DIRT : Material.STONE;
                        if (layer >= 2 && (dx + dz) % 3 == 0) m = Material.COBBLESTONE;
                        w.getBlockAt(pf.x + dx, pf.y - 1 - layer, pf.z + dz).setType(m, false);
                    }
                }
            }
            w.getBlockAt(pf.x + 3, pf.y, pf.z - 2).setType(Material.SPRUCE_FENCE, false);
            w.getBlockAt(pf.x + 3, pf.y + 1, pf.z - 2).setType(Material.LANTERN, false);
            w.getBlockAt(pf.x, pf.y, pf.z).setType(randomBlock(0), false);
            made++;
        }
        return made;
    }

    /** Beim Start nach dem Auswürfeln: alte Plattformen am Spawn abbauen und die neuen weit draußen bauen. */
    private void relocateIfNeeded() {
        if (oldPlaces == null) return;
        World w = world();
        if (w == null) return;
        for (int[] o : oldPlaces) {
            w.getChunkAt(o[0] >> 4, o[2] >> 4);
            // nur abbauen, was nach unserer alten Plattform aussieht (5 x 5 Stein unter dem Block)
            if (w.getBlockAt(o[0], o[1] - 1, o[2]).getType() != Material.STONE) continue;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (w.getBlockAt(o[0] + dx, o[1] - 1, o[2] + dz).getType() == Material.STONE) w.getBlockAt(o[0] + dx, o[1] - 1, o[2] + dz).setType(Material.AIR, false);
                }
            }
            w.getBlockAt(o[0], o[1], o[2]).setType(Material.AIR, false);
        }
        int n = setup(w);
        oldPlaces = null;
        dirty = true;
        save();
        addon.getLogger().info("One-Block: " + n + " Plattformen weit draußen gebaut (müssen erst gefunden werden): " + positionsText());
    }

    private String positionsText() {
        List<String> l = new ArrayList<>();
        for (Platform pf : platforms) l.add(pf.name() + " " + pf.x + " " + pf.y + " " + pf.z);
        return String.join(", ", l);
    }

    // ---------- Finden ----------

    /** Hat der Spieler diesen One-Block schon gefunden (oder ist er nicht verteilt, oder gehört er seinem Team)? */
    public boolean mayTravel(Player p, Platform pf) {
        if (!scattered() || p.hasPermission("kuby.admin") || pf.found.contains(p.getUniqueId())) return true;
        TeamService.Team t = team(p.getUniqueId());
        return t != null && pf == claimOf(t);
    }

    /** Hinweis für die Suche: Himmelsrichtung vom Spawn und ungefähre Entfernung, z. B. „Nordosten, ca. 14.000 Blöcke“. */
    public String hint(Platform pf) {
        double deg = (Math.toDegrees(Math.atan2(pf.x, -pf.z)) + 360) % 360;
        String[] dirs = {"Norden", "Nordosten", "Osten", "Südosten", "Süden", "Südwesten", "Westen", "Nordwesten"};
        String dir = dirs[(int) Math.round(deg / 45) % 8];
        long dist = Math.round(Math.hypot(pf.x, pf.z) / 1000.0) * 1000;
        return dir + ", ca. " + Msg.coins(dist) + " Blöcke";
    }

    /** Jede Sekunde: wer einem One-Block nahe kommt (32 Blöcke), hat ihn gefunden. */
    private void discover(World w) {
        if (!scattered()) return;
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            for (Platform pf : platforms) {
                double dx = l.getX() - pf.x, dz = l.getZ() - pf.z;
                if (dx * dx + dz * dz > 32 * 32 || !pf.found.add(p.getUniqueId())) continue;
                dirty = true;
                Msg.send(p, "<#9AD8FF><b>" + pf.name() + " entdeckt!</b> <gray>Ab jetzt kannst du über <white>/oneblock</white> hinreisen.");
                Sfx.play(core, p, Sfx.Kind.SUCCESS_BIG);
            }
        }
    }

    public Platform at(Block b) {
        for (Platform pf : platforms) if (b.getX() == pf.x && b.getY() == pf.y && b.getZ() == pf.z) return pf;
        return null;
    }

    public boolean isOneBlock(Block b) {
        return at(b) != null;
    }

    public Location spot(Platform pf) {
        World w = world();
        if (w == null) return null;
        Location l = new Location(w, pf.x + 0.5, pf.y, pf.z + 2.5);
        l.setYaw(180);
        l.setPitch(20);
        return l;
    }

    // =================================================================================================================
    // Teams
    // =================================================================================================================

    static String key(TeamService.Team t) {
        return (t.mode() + "_" + t.name().toLowerCase(Locale.ROOT)).replaceAll("[^a-z0-9_-]", "_");
    }

    /** Skyblock-Team des Spielers (null ohne Team). */
    public TeamService.Team team(UUID id) {
        TeamService.Team t = core.teams().of(id);
        return t != null && ModeService.SKYBLOCK.equals(t.mode()) ? t : null;
    }

    public Progress progress(TeamService.Team t) {
        return progress.computeIfAbsent(key(t), k -> new Progress());
    }

    /** Plattform des Teams (oder null). */
    public Platform claimOf(TeamService.Team t) {
        if (t == null) return null;
        String k = key(t);
        for (Platform pf : platforms) if (k.equals(pf.team)) return pf;
        return null;
    }

    private TeamService.Team teamOf(Platform pf) {
        if (pf.team == null) return null;
        for (TeamService.Team t : core.teams().all()) if (pf.team.equals(key(t))) return t;
        return null;
    }

    public long claimPrice() {
        return addon.getConfig().getLong("oneblock.claim.preis", 25_000);
    }

    public int minMembers() {
        return Math.max(1, addon.getConfig().getInt("oneblock.claim.min-mitglieder", 1));
    }

    public int expiryDays() {
        return Math.max(1, addon.getConfig().getInt("oneblock.claim.verfall-tage", 7));
    }

    /** Plattform für das eigene Team beanspruchen. Gibt eine Fehlermeldung zurück oder null. */
    public String claim(Player p, int index) {
        if (index < 0 || index >= platforms.size()) return "Diese Plattform gibt es nicht.";
        TeamService.Team t = team(p.getUniqueId());
        if (t == null) return "Beanspruchen geht nur im Team. Gründe eins mit /team erstellen <Name>.";
        if (t.members().size() < minMembers()) return "Dein Team braucht mindestens " + minMembers() + " Mitglieder.";
        if (claimOf(t) != null) return "Dein Team hat schon " + claimOf(t).name() + ".";
        Platform pf = platforms.get(index);
        if (pf.claimed()) return pf.name() + " gehört schon Team " + pf.teamName + ".";
        if (!mayTravel(p, pf)) return pf.name() + " musst du erst finden: " + hint(pf) + " vom Spawn.";
        long price = claimPrice();
        if (price > 0 && !core.economy().withdraw(p.getUniqueId(), price)) return "Dafür brauchst du " + Msg.coins(price) + " $.";
        pf.team = key(t);
        pf.teamName = t.name();
        pf.since = pf.active = System.currentTimeMillis();
        progress(t);
        dirty = true;
        hologram(pf, true);
        for (UUID m : t.members()) {
            Player o = Bukkit.getPlayer(m);
            if (o != null) {
                Msg.send(o, "<#9AD8FF><b>" + pf.name() + "</b> <gray>gehört jetzt eurem Team. Nur ihr baut dort ab, jeder Abbau füllt eure Block-XP. <white>/oneblock");
                Sfx.play(core, o, Sfx.Kind.SUCCESS_BIG);
            }
        }
        return null;
    }

    /** Plattform freigeben (nur der Teamleiter). Die Upgrades bleiben beim Team. */
    public String release(Player p) {
        TeamService.Team t = team(p.getUniqueId());
        Platform pf = claimOf(t);
        if (pf == null) return "Dein Team hat keinen One-Block.";
        if (!t.leader().equals(p.getUniqueId()) && !p.hasPermission("kuby.admin")) return "Nur der Teamleiter kann den One-Block freigeben.";
        free(pf, "<gray>" + pf.name() + " wurde freigegeben. Eure Upgrades bleiben für den nächsten One-Block.");
        return null;
    }

    private void free(Platform pf, String msg) {
        TeamService.Team t = teamOf(pf);
        pf.team = null;
        pf.teamName = null;
        dirty = true;
        hologram(pf, true);
        if (t != null && msg != null) {
            for (UUID m : t.members()) {
                Player o = Bukkit.getPlayer(m);
                if (o != null) Msg.send(o, msg);
            }
        }
    }

    public boolean mayMine(Player p, Platform pf) {
        if (!pf.claimed() || p.hasPermission("kuby.admin")) return true;
        TeamService.Team t = team(p.getUniqueId());
        return t != null && pf.team.equals(key(t));
    }

    // =================================================================================================================
    // Upgrades und Werte
    // =================================================================================================================

    public int max(Upgrade u) {
        return Math.max(0, addon.getConfig().getInt("oneblock.upgrades." + u.key() + ".max", u.max));
    }

    /** Anteil an der Grundwirkung (Stufe 1 bis 3, bis Level 20): 0 bis 1, die volle Wirkung bei Level 20 bleibt gleich. */
    private double frac(Upgrade u, int lvl) {
        int m = Math.min(max(u), UpgradeLevels.BASE);
        return m <= 0 ? 0 : Math.max(0, Math.min(1, (double) lvl / m));
    }

    /** Anteil an Stufe 4 (Level 21 bis Höchst-Level, nur mit Juwelen): 0 bis 1. */
    private double extra(Upgrade u, int lvl) {
        int m = max(u) - UpgradeLevels.BASE;
        return m <= 0 ? 0 : Math.max(0, Math.min(1, (double) (lvl - UpgradeLevels.BASE) / m));
    }

    /** Eile-Stufe am Block (0 = keine): bis Level 20 Eile I bis V (alle 4 Level eine mehr), in Stufe 4 bis Eile VIII
     *  (alle 10 Level eine mehr). */
    public int haste(int lvl) {
        if (lvl <= 0) return 0;
        return (int) Math.ceil(5 * frac(Upgrade.TEMPO, lvl)) + (int) Math.floor(3 * extra(Upgrade.TEMPO, lvl) + 1e-9);
    }

    /** Haben alle One-Block-Upgrades in der config schon mindestens 20 Level? */
    public boolean levelsReady() {
        for (Upgrade u : Upgrade.values()) if (max(u) < UpgradeLevels.BASE) return false;
        return true;
    }

    /** Ist das nächste Level aus Stufe 4 (Level 21-50, nur mit Juwelen des Spielers)? */
    public boolean jewelStep(Progress pr, Upgrade u) {
        return UpgradeLevels.jewelLevel(pr.level(u) + 1);
    }

    /** Preis des nächsten Levels, -1 wenn ausgebaut: bis Level 20 in Block-XP, in Stufe 4 in Juwelen (jewelStep). */
    public long price(Progress pr, Upgrade u) {
        int lvl = pr.level(u);
        if (lvl >= max(u)) return -1;
        if (UpgradeLevels.jewelLevel(lvl + 1)) {
            var c = addon.getConfig();
            return UpgradeLevels.jewels(lvl + 1, c.getDouble("oneblock.juwelen-stufe.je-upgrade." + u.key(), c.getDouble("oneblock.juwelen-stufe.basis", 15)),
                    c.getDouble("oneblock.juwelen-stufe.faktor", 1.05));
        }
        long base = addon.getConfig().getLong("oneblock.upgrades." + u.key() + ".basis", u.base);
        double f = addon.getConfig().getDouble("oneblock.upgrades." + u.key() + ".faktor", u.factor);
        return Math.round(base * Math.pow(f, lvl));
    }

    public String upgrade(Player p, Upgrade u) {
        TeamService.Team t = team(p.getUniqueId());
        if (t == null) return "Upgrades gibt es nur im Team.";
        Progress pr = progress(t);
        long price = price(pr, u);
        if (price < 0) return u.label + " ist schon ausgebaut.";
        if (jewelStep(pr, u)) {
            // Stufe 4: bezahlt der Spieler, der klickt, mit eigenen Juwelen
            if (!core.jewels().take(p.getUniqueId(), price)) return "Stufe 4 gibt es nur mit Juwelen: du brauchst " + Msg.coins(price) + " Juwelen.";
        } else {
            if (pr.xp < price) return "Euch fehlen noch " + Msg.coins(price - pr.xp) + " Block-XP.";
            pr.xp -= price;
        }
        pr.levels.put(u, pr.level(u) + 1);
        dirty = true;
        Platform pf = claimOf(t);
        if (pf != null) hologram(pf, true);
        for (UUID m : t.members()) {
            Player o = Bukkit.getPlayer(m);
            if (o != null) Msg.send(o, "<gold>One-Block-Upgrade: <white>" + u.label + " " + pr.level(u) + " <gray>(" + effect(u, pr.level(u)) + ", von " + p.getName() + ")");
        }
        Sfx.play(core, p, Sfx.Kind.LEVEL_UP);
        return null;
    }

    /** Eigene XP (Skyblock) in die Block-XP des Teams einzahlen; amount < 0 = alles. */
    public String deposit(Player p, long amount) {
        TeamService.Team t = team(p.getUniqueId());
        if (t == null) return "Einzahlen geht nur im Team.";
        var st = core.stats().get(p.getUniqueId());
        long n = amount < 0 ? st.xp : Math.min(amount, st.xp);
        if (n <= 0) return "Du hast keine XP zum Einzahlen.";
        st.xp -= n;
        Progress pr = progress(t);
        pr.xp += n;
        pr.earned += n;
        dirty = true;
        Msg.send(p, "<green>" + Msg.coins(n) + " XP eingezahlt. <gray>Block-XP eures Teams: <white>" + Msg.coins(pr.xp));
        Sfx.play(core, p, Sfx.Kind.COIN);
        return null;
    }

    /** Wirkung einer Stufe als kurzer Text. */
    public String effect(Upgrade u, int lvl) {
        return switch (u) {
            case GLUECK -> "Selten x" + fmt(luckFactor(lvl));
            case COOLDOWN -> fmt(cooldown(lvl)) + " s";
            case TEMPO -> lvl == 0 ? "aus" : "Eile " + roman(haste(lvl));
            case XP, GELD -> "+" + Math.round((frac(u, lvl) + extra(u, lvl)) * 100) + " %";
            case BEUTE -> Math.round(frac(u, lvl) * 40 + extra(u, lvl) * 30) + " %";
        };
    }

    /** Wie viel öfter der seltenste Block kommt (für die Anzeige). */
    private double luckFactor(int lvl) {
        ConfigurationSection s = addon.getConfig().getConfigurationSection("oneblock.blocks");
        if (s == null || s.getKeys(false).isEmpty()) return 1;
        int max = 1, min = Integer.MAX_VALUE, total = 0;
        double total2 = 0;
        for (String n : s.getKeys(false)) {
            int w = Math.max(1, s.getInt(n, 1));
            max = Math.max(max, w);
            min = Math.min(min, w);
            total += w;
            total2 += Math.pow(w, exponent(lvl));
        }
        double before = (double) min / total, after = Math.pow(min, exponent(lvl)) / total2;
        return Math.round(after / before * 10) / 10.0;
    }

    /** Glück: Gewichte hoch e (volles Upgrade 0,4 = seltene Blöcke viel öfter). */
    private double exponent(int luck) {
        return Math.max(0.2, 1 - 0.6 * frac(Upgrade.GLUECK, luck) - 0.15 * extra(Upgrade.GLUECK, luck));
    }

    /** Cooldown: volles Upgrade -80 %. */
    public double cooldown(int lvl) {
        double base = addon.getConfig().getDouble("oneblock.cooldown", 1.5);
        return Math.round(base * Math.max(0.05, 1 - 0.8 * frac(Upgrade.COOLDOWN, lvl) - 0.15 * extra(Upgrade.COOLDOWN, lvl)) * 100) / 100.0;
    }

    public static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.GERMANY, "%.2f", v).replaceAll("0$", "");
    }

    static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    // =================================================================================================================
    // Abbauen
    // =================================================================================================================

    /** Abbau: Loot geben, Geld und XP, Block-XP fürs Team, dann (nach dem Cooldown) ein neuer zufälliger Block. */
    public void harvest(Player p, Block b) {
        Platform pf = at(b);
        if (pf == null || pf.regenerating) return;
        if (!mayMine(p, pf)) {
            p.sendActionBar(Msg.parse("<red>" + pf.name() + " gehört Team " + Msg.escape(pf.teamName) + ". <gray>/oneblock"));
            return;
        }
        TeamService.Team t = pf.claimed() ? teamOf(pf) : null;
        Progress pr = t == null ? null : progress(t);
        Material old = b.getType();
        Collection<ItemStack> drops = b.getDrops(p.getInventory().getItemInMainHand(), p);
        List<ItemStack> give = new ArrayList<>();
        if (drops.isEmpty()) give.add(new ItemStack(old, 1));
        else for (ItemStack d : drops) give.add(d.clone());
        boolean doubled = pr != null && rnd.nextDouble() < frac(Upgrade.BEUTE, pr.level(Upgrade.BEUTE)) * 0.4 + extra(Upgrade.BEUTE, pr.level(Upgrade.BEUTE)) * 0.3;
        if (doubled) for (ItemStack d : new ArrayList<>(give)) give.add(d.clone());
        for (ItemStack d : give) p.getInventory().addItem(d).values().forEach(r -> p.getWorld().dropItem(p.getLocation(), r));
        double money = addon.getConfig().getLong("oneblock.money", 5) * (1 + (pr == null ? 0 : frac(Upgrade.GELD, pr.level(Upgrade.GELD)) + extra(Upgrade.GELD, pr.level(Upgrade.GELD))));
        double xp = addon.getConfig().getLong("oneblock.xp", 3) * (1 + (pr == null ? 0 : frac(Upgrade.XP, pr.level(Upgrade.XP)) + extra(Upgrade.XP, pr.level(Upgrade.XP))));
        long coins = Math.round(money), xpl = Math.round(xp);
        core.economy().deposit(p.getUniqueId(), coins);
        core.stats().get(p.getUniqueId()).xp += xpl;
        String extra = "";
        if (pr != null) {
            long bx = Math.round(addon.getConfig().getLong("oneblock.team-xp", 2) * (1 + frac(Upgrade.XP, pr.level(Upgrade.XP)) + extra(Upgrade.XP, pr.level(Upgrade.XP))));
            pr.xp += bx;
            pr.earned += bx;
            pr.mined++;
            pf.active = System.currentTimeMillis();
            dirty = true;
            extra = " <dark_gray>· <#9AD8FF>Block-XP " + Msg.coins(pr.xp);
        }
        p.sendActionBar(Msg.parse((doubled ? "<light_purple><b>Doppelte Beute!</b> " : "") + "<gold>+" + Msg.coins(coins) + " $ <green>+"
                + Msg.coins(xpl) + " XP" + extra));
        addon.progress().recordOneBlock(p);
        core.questProgress(p, "oneblock", old, 1);
        addon.progress().recordItem(p, old);
        Sfx.play(core, p, doubled ? Sfx.Kind.SUCCESS : Sfx.Kind.CHIME);
        int luck = pr == null ? 0 : pr.level(Upgrade.GLUECK);
        long ticks = Math.max(0, Math.round(cooldown(pr == null ? 0 : pr.level(Upgrade.COOLDOWN)) * 20));
        if (ticks <= 1) {
            b.setType(randomBlock(luck), false);
            return;
        }
        pf.regenerating = true;
        b.setType(Material.BEDROCK, false);
        Bukkit.getScheduler().runTaskLater(addon, () -> {
            pf.regenerating = false;
            Block nb = b.getWorld().getBlockAt(pf.x, pf.y, pf.z);
            nb.setType(randomBlock(luck), false);
            nb.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, nb.getLocation().add(0.5, 0.8, 0.5), 6, 0.35, 0.3, 0.35, 0);
        }, ticks);
    }

    /** Zufälliger Block aus der gewichteten Liste; Glück macht seltene Blöcke häufiger (Gewicht hoch kleiner 1). */
    public Material randomBlock(int luck) {
        ConfigurationSection s = addon.getConfig().getConfigurationSection("oneblock.blocks");
        List<String> names = new ArrayList<>();
        if (s != null) names.addAll(s.getKeys(false));
        if (names.isEmpty()) return Material.STONE;
        double e = exponent(luck);
        double total = 0;
        double[] w = new double[names.size()];
        for (int i = 0; i < names.size(); i++) {
            w[i] = Math.pow(Math.max(1, s.getInt(names.get(i), 1)), e);
            total += w[i];
        }
        double r = rnd.nextDouble() * total;
        for (int i = 0; i < names.size(); i++) {
            r -= w[i];
            if (r < 0) {
                Material m = Material.matchMaterial(names.get(i));
                return m != null && m.isBlock() ? m : Material.STONE;
            }
        }
        return Material.STONE;
    }

    public Material randomBlock() {
        return randomBlock(0);
    }

    // =================================================================================================================
    // Jede Sekunde: Eile am Block, Verfall, Schilder
    // =================================================================================================================

    private void tick() {
        World w = world();
        if (w == null) return;
        discover(w);
        long now = System.currentTimeMillis();
        for (Platform pf : platforms) {
            if (pf.claimed()) {
                TeamService.Team t = teamOf(pf);
                if (t == null) {
                    free(pf, null);
                } else if (now - pf.active > expiryDays() * 86_400_000L) {
                    free(pf, "<red>" + pf.name() + " ist wieder frei: " + expiryDays() + " Tage hat niemand aus eurem Team dort abgebaut.");
                } else {
                    int tempo = progress(t).level(Upgrade.TEMPO);
                    if (tempo > 0) {
                        Location c = new Location(w, pf.x + 0.5, pf.y + 0.5, pf.z + 0.5);
                        for (UUID m : t.members()) {
                            Player o = Bukkit.getPlayer(m);
                            if (o != null && o.getWorld().equals(w) && o.getLocation().distanceSquared(c) < 36) {
                                o.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 50, haste(tempo) - 1, true, false, true));
                            }
                        }
                    }
                }
            }
            hologram(pf, false);
        }
    }

    /** Schild über der Plattform: frei oder Team mit Block-Stufe. */
    private void hologram(Platform pf, boolean force) {
        World w = world();
        if (w == null || !w.isChunkLoaded(pf.x >> 4, pf.z >> 4)) return;
        Location l = new Location(w, pf.x + 0.5, pf.y + 2.1, pf.z + 0.5);
        boolean near = false;
        for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(l) < 40 * 40) near = true;
        if (!near && !force) return;
        Entity e = pf.display == null ? null : Bukkit.getEntity(pf.display);
        TextDisplay td;
        if (e instanceof TextDisplay ok && ok.isValid()) {
            td = ok;
        } else {
            td = w.spawn(l, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setDefaultBackground(false);
                d.setShadowed(true);
            });
            pf.display = td.getUniqueId();
        }
        String text;
        if (!pf.claimed()) {
            text = "<#9AD8FF><b>" + pf.name() + "</b>\n<green>Frei <gray>· Team kann ihn beanspruchen\n<white>/oneblock";
        } else {
            TeamService.Team t = teamOf(pf);
            Progress pr = t == null ? null : progress(t);
            text = "<#9AD8FF><b>" + pf.name() + "</b>\n<gold>Team " + Msg.escape(pf.teamName) + (pr == null ? "" : " <gray>· Stufe <white>"
                    + pr.total() + "\n<gray>" + Msg.coins(pr.mined) + " abgebaut");
        }
        td.text(Msg.parse(text));
    }

    public void removeHolograms() {
        for (Platform pf : platforms) {
            Entity e = pf.display == null ? null : Bukkit.getEntity(pf.display);
            if (e != null) e.remove();
            pf.display = null;
            if (pf.regenerating) {
                World w = world();
                if (w != null) w.getBlockAt(pf.x, pf.y, pf.z).setType(randomBlock(0), false);
                pf.regenerating = false;
            }
        }
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private void load() {
        upgradeVersion = levelsReady() ? UpgradeLevels.VERSION : 1;
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (Platform pf : platforms) {
            String b = "platforms." + pf.index + ".";
            pf.team = y.getString(b + "team");
            pf.teamName = y.getString(b + "team-name", pf.team);
            pf.since = y.getLong(b + "since");
            pf.active = y.getLong(b + "active", System.currentTimeMillis());
            for (String u : y.getStringList(b + "found")) {
                try {
                    pf.found.add(UUID.fromString(u));
                } catch (IllegalArgumentException ignored) {
                    // ungültig
                }
            }
        }
        // Upgrade-Stände aus der Zeit mit 5 oder 10 Stufen auf Level 0 bis 20 umrechnen (gleiche Wirkung)
        int stored = y.getInt("upgrade-version", 1);
        boolean migrate = stored < UpgradeLevels.VERSION && levelsReady();
        upgradeVersion = migrate ? UpgradeLevels.VERSION : stored;
        ConfigurationSection ts = y.getConfigurationSection("teams");
        if (ts != null) {
            for (String k : ts.getKeys(false)) {
                Progress pr = new Progress();
                pr.xp = ts.getLong(k + ".xp");
                pr.mined = ts.getLong(k + ".mined");
                pr.earned = ts.getLong(k + ".earned");
                for (Upgrade u : Upgrade.values()) {
                    int v = ts.getInt(k + ".upgrades." + u.key());
                    if (migrate && v > 0) {
                        v = UpgradeLevels.oneBlock(u.key(), v);
                        dirty = true;
                    }
                    if (v > 0) pr.levels.put(u, v);
                }
                progress.put(k, pr);
            }
        }
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("upgrade-version", upgradeVersion);
        if (scattered()) {
            List<String> pos = new ArrayList<>();
            for (Platform pf : platforms) pos.add(pf.x + ";" + pf.y + ";" + pf.z);
            y.set("positions", pos);
        }
        for (Platform pf : platforms) {
            if (!pf.found.isEmpty()) y.set("platforms." + pf.index + ".found", pf.found.stream().map(UUID::toString).toList());
        }
        for (Platform pf : platforms) {
            if (!pf.claimed()) continue;
            String b = "platforms." + pf.index + ".";
            y.set(b + "team", pf.team);
            y.set(b + "team-name", pf.teamName);
            y.set(b + "since", pf.since);
            y.set(b + "active", pf.active);
        }
        for (Map.Entry<String, Progress> e : progress.entrySet()) {
            Progress pr = e.getValue();
            String b = "teams." + e.getKey() + ".";
            y.set(b + "xp", pr.xp);
            y.set(b + "mined", pr.mined);
            y.set(b + "earned", pr.earned);
            for (Map.Entry<Upgrade, Integer> u : pr.levels.entrySet()) y.set(b + "upgrades." + u.getKey().key(), u.getValue());
        }
        DataFiles.save(addon, y, file);
        dirty = false;
    }
}

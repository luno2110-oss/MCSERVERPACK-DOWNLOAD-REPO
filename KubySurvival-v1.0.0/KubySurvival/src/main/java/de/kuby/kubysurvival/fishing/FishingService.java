package de.kuby.kubysurvival.fishing;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Angeln in Survival: Fischarten aus angel.fish in der config.yml von KubyCore, Gewicht und Wert je Fang,
 * Angel-Level mit XP, Ruten-Stufen, Fischbuch, Bestenliste, Turnier, Angelzonen und Fischverkauf.
 * Tageszeit: Nachts beißen seltene Fische öfter, tagsüber die häufigen.
 */
public final class FishingService {

    /** Fischart. index = Seltenheit (0 = häufigste). */
    public record FishType(String id, String name, Material material, int chance, long price, int xp, String color,
                           double minKg, double maxKg, int index) {
        public String display() {
            return "<" + color + ">" + name;
        }
    }

    /** Angelzone als Rechteck (Höhe egal), mit Namen und schwebendem Schild. */
    public record Zone(String id, String world, int x1, int z1, int x2, int z2, int y, String name) {
        boolean contains(Location l) {
            return l.getWorld() != null && l.getWorld().getName().equals(world)
                    && l.getBlockX() >= x1 && l.getBlockX() <= x2 && l.getBlockZ() >= z1 && l.getBlockZ() <= z2;
        }

        Location center(World w) {
            return new Location(w, (x1 + x2) / 2.0 + 0.5, y + 2.5, (z1 + z2) / 2.0 + 0.5);
        }
    }

    /** Daten eines Spielers. */
    public static final class Angler {
        public long xp;
        public int rod = 1;
        public final Map<String, Integer> caught = new HashMap<>();
        public final Map<String, Double> best = new HashMap<>();

        public int total() {
            int n = 0;
            for (int v : caught.values()) n += v;
            return n;
        }
    }

    /** Standard-Gewichte (kg), falls in der Config keine stehen. */
    private static final Map<String, double[]> DEFAULT_KG = Map.of(
            "sardine", new double[]{0.05, 0.2}, "hering", new double[]{0.1, 0.4}, "lachs", new double[]{2, 8},
            "barsch", new double[]{0.3, 2}, "hecht", new double[]{2, 12}, "clownfisch", new double[]{0.1, 0.3},
            "stoer", new double[]{20, 80}, "koi", new double[]{1, 6});

    private static final String SURVIVAL = "survival";

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File file;
    private final NamespacedKey keyId;
    private final NamespacedKey keyKg;
    private final NamespacedKey keyValue;
    private final NamespacedKey keyZone;
    private final List<FishType> types = new ArrayList<>();
    private final Map<UUID, Angler> anglers = new HashMap<>();
    private final Map<String, Zone> zones = new LinkedHashMap<>();
    private final Map<UUID, Long> pendingMoney = new HashMap<>();
    private final Map<UUID, Double> tournament = new HashMap<>();
    private long tournamentEnds;

    public FishingService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "fishing.yml");
        this.keyId = new NamespacedKey(addon, "fish_id");
        this.keyKg = new NamespacedKey(addon, "fish_kg");
        this.keyValue = new NamespacedKey(addon, "fish_value");
        this.keyZone = new NamespacedKey(addon, "fish_zone");
        loadTypes();
        load();
    }

    private void loadTypes() {
        List<String> raw = core.getConfig().getStringList("angel.fish");
        int index = 0;
        for (String line : raw) {
            String[] p = line.split(";");
            if (p.length < 7) continue;
            try {
                Material mat = Material.matchMaterial(p[2]);
                if (mat == null) mat = Material.COD;
                double[] kg = DEFAULT_KG.getOrDefault(p[0], new double[]{0.5, 3});
                double min = p.length > 8 ? Double.parseDouble(p[7]) : kg[0];
                double max = p.length > 8 ? Double.parseDouble(p[8]) : kg[1];
                types.add(new FishType(p[0], p[1], mat, Integer.parseInt(p[3]), Long.parseLong(p[4]),
                        Integer.parseInt(p[5]), p[6], min, max, index++));
            } catch (NumberFormatException e) {
                addon.getLogger().warning("Ungültige Fischart in angel.fish: " + line);
            }
        }
    }

    public List<FishType> types() {
        return types;
    }

    public FishType type(String id) {
        for (FishType t : types) if (t.id().equals(id)) return t;
        return null;
    }

    public synchronized Angler angler(UUID id) {
        return anglers.computeIfAbsent(id, k -> new Angler());
    }

    // ---------- Level und Rute ----------

    /** XP, die man für ein Level insgesamt braucht (Level 1 ab 0 XP, jedes Level 100 XP mehr). */
    public static long xpFor(int level) {
        return 50L * level * (level - 1);
    }

    public static int levelOf(long xp) {
        int level = 1;
        while (xpFor(level + 1) <= xp) level++;
        return level;
    }

    public int maxRod() {
        return core.getConfig().getInt("angel.max-rod", 5);
    }

    /** Preis für die nächste Rutenstufe, 0 wenn schon ganz oben. */
    public long rodUpgradeCost(int current) {
        if (current >= maxRod()) return 0;
        return core.getConfig().getLong("angel.rod-base", 2000) * current;
    }

    public synchronized String upgradeRod(Player p) {
        if (!SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) return "Angeln gibt es nur in Survival.";
        Angler a = angler(p.getUniqueId());
        long cost = rodUpgradeCost(a.rod);
        if (cost <= 0) return "Deine Rute ist schon auf der höchsten Stufe.";
        if (!core.economy().withdraw(p.getUniqueId(), cost)) return "Nicht genug Coins. Die nächste Stufe kostet " + Msg.coins(cost) + ".";
        a.rod++;
        save();
        Sfx.play(core, p, Sfx.Kind.LEVEL_UP);
        Msg.send(p, "<green>Rute verbessert auf Stufe " + a.rod + "!");
        return null;
    }

    /** Wartezeit-Faktor der Rute: jede Stufe 8 % schneller. */
    public double waitFactor(UUID id) {
        return Math.max(0.5, 1.0 - 0.08 * (angler(id).rod - 1));
    }

    // ---------- Fangen ----------

    /** Ob das Fangsystem hier gilt: in einer Angelzone, oder überall, solange es keine Zonen gibt. */
    public synchronized boolean activeAt(Location l) {
        if (zones.isEmpty() || !core.getConfig().getBoolean("angel.only-in-zones", true)) return true;
        for (Zone z : zones.values()) if (z.contains(l)) return true;
        return false;
    }

    /** Würfelt einen Fang aus. Rute und Tageszeit verschieben die Chancen zu seltenen Fischen. */
    public FishType roll(UUID id, World world) {
        int rod = angler(id).rod;
        boolean night = world != null && world.getTime() >= 13000 && world.getTime() <= 23000;
        int last = Math.max(1, types.size() - 1);
        double total = 0;
        double[] w = new double[types.size()];
        for (int i = 0; i < types.size(); i++) {
            FishType t = types.get(i);
            double rarity = (double) t.index() / last;
            double weight = t.chance() * (1 + (rod - 1) * 0.25 * rarity);
            if (night && rarity >= 0.5) weight *= 1.5;
            if (!night && rarity <= 0.25) weight *= 1.2;
            w[i] = weight;
            total += weight;
        }
        double r = ThreadLocalRandom.current().nextDouble(total);
        for (int i = 0; i < types.size(); i++) {
            r -= w[i];
            if (r < 0) return types.get(i);
        }
        return types.get(0);
    }

    /** Baut das Fisch-Item mit Gewicht und Wert und verbucht den Fang (XP, Fischbuch, Turnier). */
    public synchronized ItemStack catchFish(Player p, FishType t, Location at) {
        double f = ThreadLocalRandom.current().nextDouble();
        // Schwere Exemplare sind seltener.
        double kg = Math.round((t.minKg() + (t.maxKg() - t.minKg()) * f * f) * 100) / 100.0;
        double quality = (t.maxKg() - t.minKg()) <= 0 ? 0.5 : (kg - t.minKg()) / (t.maxKg() - t.minKg());
        long value = Math.max(1, Math.round(t.price() * (0.75 + 0.5 * quality)));

        Angler a = angler(p.getUniqueId());
        int before = levelOf(a.xp);
        a.xp += t.xp();
        a.caught.merge(t.id(), 1, Integer::sum);
        boolean record = kg > a.best.getOrDefault(t.id(), 0.0);
        if (record) a.best.put(t.id(), kg);
        int after = levelOf(a.xp);
        if (tournamentRunning()) tournament.merge(p.getUniqueId(), kg, Double::sum);
        save();

        String kgText = String.format(Locale.GERMANY, "%.2f kg", kg);
        floating(at, t.display() + " <white>" + kgText);
        Sfx.play(core, p, t.index() >= types.size() - 3 ? Sfx.Kind.VICTORY : Sfx.Kind.CHIME);
        Msg.send(p, "<aqua>Gefangen: " + t.display() + " <white>" + kgText + " <gray>· Wert " + Msg.coins(value)
                + " · +" + t.xp() + " Angel-XP" + (record ? " <gold>· Neuer Rekord!" : ""));
        if (t.index() >= types.size() - 2) {
            Bukkit.broadcast(Msg.parse("<aqua>Angeln: <white>" + p.getName() + " <aqua>hat einen " + t.display()
                    + " <aqua>gefangen (" + kgText + ")!"));
        }
        if (after > before) levelUp(p, after);

        ItemStack item = new ItemStack(t.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.item(t.display()));
        meta.lore(List.of(Msg.item("<gray>Gewicht: <white>" + kgText), Msg.item("<gray>Wert: <gold>" + Msg.coins(value) + " Coins"),
                Msg.item("<dark_gray>Gefangen von " + p.getName())));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyId, PersistentDataType.STRING, t.id());
        pdc.set(keyKg, PersistentDataType.DOUBLE, kg);
        pdc.set(keyValue, PersistentDataType.LONG, value);
        item.setItemMeta(meta);
        return item;
    }

    private void levelUp(Player p, int level) {
        long reward = core.getConfig().getLong("angel.level-reward", 500) * (level - 1);
        core.economy().deposit(p.getUniqueId(), reward);
        Sfx.play(core, p, Sfx.Kind.LEVEL_UP);
        Msg.send(p, "<gold><b>Angel-Level " + level + "!</b> <green>+" + Msg.coins(reward) + " Coins");
    }

    private void floating(Location at, String mini) {
        if (at == null || at.getWorld() == null) return;
        Location start = at.clone().add(0, 0.8, 0);
        TextDisplay d = at.getWorld().spawn(start, TextDisplay.class, e -> {
            e.text(Msg.parse(mini));
            e.setBillboard(Display.Billboard.CENTER);
            e.setPersistent(false);
            e.setTeleportDuration(30);
        });
        addon.getServer().getScheduler().runTaskLater(addon, () -> d.teleport(start.clone().add(0, 1.2, 0)), 2L);
        addon.getServer().getScheduler().runTaskLater(addon, d::remove, core.getConfig().getLong("angel.popup-ticks", 50));
    }

    // ---------- Verkauf ----------

    /** Wert aller Fische im Inventar (ohne zu verkaufen). */
    public long inventoryValue(Player p) {
        long sum = 0;
        for (ItemStack it : p.getInventory().getContents()) sum += valueOf(it);
        return sum;
    }

    public long valueOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        Long v = it.getItemMeta().getPersistentDataContainer().get(keyValue, PersistentDataType.LONG);
        return v == null ? 0 : v * it.getAmount();
    }

    /** Verkauft alle Fische im Inventar. Nur in Survival, weil das Geld pro Modus getrennt ist. */
    public synchronized String sellAll(Player p) {
        if (!SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) return "Fische verkaufen geht nur in Survival.";
        long sum = 0;
        int count = 0;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            long v = valueOf(contents[i]);
            if (v <= 0) continue;
            sum += v;
            count += contents[i].getAmount();
            p.getInventory().setItem(i, null);
        }
        if (count == 0) return "Du hast keine Fische dabei.";
        core.economy().deposit(p.getUniqueId(), sum);
        Sfx.play(core, p, Sfx.Kind.COIN);
        Msg.send(p, "<green>" + count + " Fische verkauft für <gold>" + Msg.coins(sum) + " Coins<green>.");
        return null;
    }

    // ---------- Zonen ----------

    public synchronized String createZone(String id, Location a, Location b, String name) {
        if (!id.matches("[a-z0-9_-]{1,24}")) return "Ungültige ID. Erlaubt: Kleinbuchstaben, Ziffern, - und _.";
        if (zones.containsKey(id)) return "Eine Angelzone mit der ID " + id + " gibt es schon.";
        if (a == null || b == null) return "Setze erst beide Ecken: /kp angel pos1 und /kp angel pos2 (oder mit der Blaze Rod).";
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return "Beide Ecken müssen in derselben Welt liegen.";
        Zone z = new Zone(id, a.getWorld().getName(), Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockZ(), b.getBlockZ()), Math.max(a.getBlockY(), b.getBlockY()), name);
        zones.put(id, z);
        save();
        spawnSign(z);
        return null;
    }

    public synchronized boolean deleteZone(String id) {
        Zone z = zones.remove(id);
        if (z == null) return false;
        removeSign(z);
        save();
        return true;
    }

    public synchronized List<Zone> zones() {
        return new ArrayList<>(zones.values());
    }

    /** Stellt die Schilder der Zonen nach einem Neustart wieder her. */
    public synchronized void respawnSigns() {
        for (Zone z : zones.values()) {
            removeSign(z);
            spawnSign(z);
        }
    }

    private void spawnSign(Zone z) {
        World w = Bukkit.getWorld(z.world());
        if (w == null) return;
        Location c = z.center(w);
        if (!w.isChunkLoaded(c.getBlockX() >> 4, c.getBlockZ() >> 4)) return;
        w.spawn(c, TextDisplay.class, e -> {
            e.text(Msg.parse("<aqua><b>ANGELZONE</b>\n<white>" + Msg.escape(z.name()) + "\n<gray>Hier beißen besondere Fische · /angeln"));
            e.setBillboard(Display.Billboard.VERTICAL);
            e.getPersistentDataContainer().set(keyZone, PersistentDataType.STRING, z.id());
        });
    }

    private void removeSign(Zone z) {
        World w = Bukkit.getWorld(z.world());
        if (w == null) return;
        for (TextDisplay d : w.getNearbyEntitiesByType(TextDisplay.class, z.center(w), 4)) {
            if (z.id().equals(d.getPersistentDataContainer().get(keyZone, PersistentDataType.STRING))) d.remove();
        }
    }

    // ---------- Turnier ----------

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
        Bukkit.broadcast(Msg.parse("<aqua><b>ANGEL-TURNIER</b> <white>startet jetzt für " + minutes
                + " Minuten! <gray>Das größte Gesamtgewicht gewinnt. /angeln"));
        return null;
    }

    public synchronized String stopTournament() {
        if (!tournamentRunning()) return "Es läuft kein Turnier.";
        finishTournament();
        return null;
    }

    /** Platzierung im laufenden Turnier, höchstes Gewicht zuerst. */
    public synchronized List<Map.Entry<UUID, Double>> tournamentTop(int n) {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>(tournament.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return new ArrayList<>(list.subList(0, Math.min(n, list.size())));
    }

    public synchronized double tournamentWeight(UUID id) {
        return tournament.getOrDefault(id, 0.0);
    }

    private void finishTournament() {
        tournamentEnds = 0;
        List<Map.Entry<UUID, Double>> top = new ArrayList<>(tournament.entrySet());
        top.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<Long> rewards = core.getConfig().getLongList("angel.tournament.rewards");
        if (rewards.isEmpty()) rewards = List.of(5000L, 2500L, 1000L);
        Bukkit.broadcast(Msg.parse("<aqua><b>ANGEL-TURNIER</b> <white>ist vorbei!"));
        if (top.isEmpty()) Bukkit.broadcast(Msg.parse("<gray>Niemand hat etwas gefangen."));
        for (int i = 0; i < Math.min(rewards.size(), top.size()); i++) {
            UUID id = top.get(i).getKey();
            long money = rewards.get(i);
            pendingMoney.merge(id, money, Long::sum);
            String name = Bukkit.getOfflinePlayer(id).getName();
            Bukkit.broadcast(Msg.parse("<gold>" + (i + 1) + ". <white>" + (name == null ? "?" : name) + " <gray>"
                    + String.format(Locale.GERMANY, "%.2f kg", top.get(i).getValue()) + " <gold>+" + Msg.coins(money) + " Coins"));
        }
        tournament.clear();
        save();
        payPending();
    }

    /** Jede Minute: Turnier-Ende prüfen und Preisgeld auszahlen, sobald der Gewinner in Survival ist. */
    public synchronized void tick() {
        if (tournamentEnds > 0 && !tournamentRunning()) finishTournament();
        payPending();
    }

    private void payPending() {
        if (pendingMoney.isEmpty()) return;
        boolean changed = false;
        for (UUID id : new ArrayList<>(pendingMoney.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !SURVIVAL.equals(core.modes().modeOf(id))) continue;
            long money = pendingMoney.remove(id);
            core.economy().deposit(id, money);
            Msg.send(p, "<aqua>Turnier-Preis: <gold>+" + Msg.coins(money) + " Coins <gray>auf dein Survival-Konto.");
            Sfx.play(core, p, Sfx.Kind.VICTORY);
            changed = true;
        }
        if (changed) save();
    }

    // ---------- Bestenliste ----------

    public synchronized List<Map.Entry<UUID, Angler>> topAnglers(int n) {
        List<Map.Entry<UUID, Angler>> list = new ArrayList<>(anglers.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue().xp, a.getValue().xp));
        return new ArrayList<>(list.subList(0, Math.min(n, list.size())));
    }

    /** Schwerster Fang einer Art auf dem ganzen Server: Spieler und Gewicht. */
    public synchronized Map.Entry<UUID, Double> recordOf(String fishId) {
        Map.Entry<UUID, Double> best = null;
        for (Map.Entry<UUID, Angler> e : anglers.entrySet()) {
            Double kg = e.getValue().best.get(fishId);
            if (kg != null && (best == null || kg > best.getValue())) best = Map.entry(e.getKey(), kg);
        }
        return best;
    }

    // ---------- Speichern ----------

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yml.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                UUID id;
                try {
                    id = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                Angler a = new Angler();
                a.xp = players.getLong(key + ".xp");
                a.rod = Math.max(1, players.getInt(key + ".rod", 1));
                ConfigurationSection c = players.getConfigurationSection(key + ".caught");
                if (c != null) for (String f : c.getKeys(false)) a.caught.put(f, c.getInt(f));
                ConfigurationSection b = players.getConfigurationSection(key + ".best");
                if (b != null) for (String f : b.getKeys(false)) a.best.put(f, b.getDouble(f));
                anglers.put(id, a);
            }
        }
        ConfigurationSection z = yml.getConfigurationSection("zones");
        if (z != null) {
            for (String id : z.getKeys(false)) {
                zones.put(id, new Zone(id, z.getString(id + ".world"), z.getInt(id + ".x1"), z.getInt(id + ".z1"),
                        z.getInt(id + ".x2"), z.getInt(id + ".z2"), z.getInt(id + ".y"), z.getString(id + ".name", id)));
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
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, Angler> e : anglers.entrySet()) {
            String base = "players." + e.getKey();
            out.set(base + ".xp", e.getValue().xp);
            out.set(base + ".rod", e.getValue().rod);
            for (Map.Entry<String, Integer> c : e.getValue().caught.entrySet()) out.set(base + ".caught." + c.getKey(), c.getValue());
            for (Map.Entry<String, Double> b : e.getValue().best.entrySet()) out.set(base + ".best." + b.getKey(), b.getValue());
        }
        for (Zone z : zones.values()) {
            String base = "zones." + z.id();
            out.set(base + ".world", z.world());
            out.set(base + ".x1", z.x1());
            out.set(base + ".z1", z.z1());
            out.set(base + ".x2", z.x2());
            out.set(base + ".z2", z.z2());
            out.set(base + ".y", z.y());
            out.set(base + ".name", z.name());
        }
        for (Map.Entry<UUID, Long> e : pendingMoney.entrySet()) out.set("pending." + e.getKey(), e.getValue());
        out.set("tournament.ends", tournamentEnds);
        for (Map.Entry<UUID, Double> e : tournament.entrySet()) out.set("tournament.weights." + e.getKey(), e.getValue());
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            addon.getLogger().warning("fishing.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}

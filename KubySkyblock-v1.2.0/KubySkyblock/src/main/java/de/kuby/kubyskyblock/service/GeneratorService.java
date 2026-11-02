package de.kuby.kubyskyblock.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generatoren als Grundmechanik von Skyblock. Drei Arten: Item-Generatoren (Erz, Holz, Farm, Mob, Nether, Edelstein),
 * Geld-Generator (ab 0,50 $/s) und XP-Generator (ab 1 XP/s). Generatoren sind Items, die man auf der eigenen Insel
 * aufstellt (Plätze je Inselgröße); sie produzieren ins eigene Lager, das man im Generator-Dialog einsammelt oder
 * verkauft. Stufen 1 bis 10 (schneller, mehr Lager). Qualitäten Normal, Silber, Gold und Diamant (Juwelenshop):
 * mehr Ertrag, günstigere Upgrades, größeres Lager, Offline-Produktion; Gold und Diamant verkaufen Items automatisch,
 * Diamant zahlt Geld und XP direkt aus. Booster je Insel (x2, x3 für eine Zeit). Ohne Spieler der Insel online läuft
 * die Produktion mit dem Offline-Anteil der Qualität. Über jedem Generator steht ein Schild. Daten: generators.yml.
 */
public final class GeneratorService {

    public enum Kind { ITEM, MONEY, XP }

    /** Qualität: Ertrag, Upgrade-Kosten, Lager, Offline-Anteil, Auto-Verkauf, Auto-Einsammeln, Farbe. */
    public enum Quality {
        NORMAL("", 1.0, 1.0, 1.0, 0.5, false, false, "#C8CED8"),
        SILBER("Silber", 1.5, 0.8, 1.5, 0.75, false, false, "#D8E2EE"),
        GOLD("Gold", 2.0, 0.65, 2.0, 1.0, true, false, "#F8D35A"),
        DIAMANT("Diamant", 3.0, 0.5, 3.0, 1.0, true, true, "#7AE8FF");

        public final String label, color;
        public final double output, upgrade, storage, offline;
        public final boolean autoSell, autoCollect;

        Quality(String label, double output, double upgrade, double storage, double offline, boolean autoSell, boolean autoCollect, String color) {
            this.label = label;
            this.output = output;
            this.upgrade = upgrade;
            this.storage = storage;
            this.offline = offline;
            this.autoSell = autoSell;
            this.autoCollect = autoCollect;
            this.color = color;
        }

        public Quality next() {
            return this == DIAMANT ? null : values()[ordinal() + 1];
        }
    }

    /** Art eines Generators aus der config. */
    public record Type(String id, Kind kind, String name, Material block, long price, int unlockLevel, double perSecond,
                       double intervalFactor, TreeMap<Integer, Map<Material, Integer>> yields) {}

    public static final class Gen {
        public final int id;
        public final int island;
        public final String world;
        public final int x, y, z;
        public final String type;
        public Quality quality;
        public int level;
        public final Map<Material, Long> items = new EnumMap<>(Material.class);
        public double money, xp, progress;
        public long last;
        UUID display;
        /** Spawner-Käfig schon eingerichtet (seit dem Start)? */
        boolean dressed;

        Gen(int id, int island, String world, int x, int y, int z, String type, Quality quality, int level) {
            this.id = id;
            this.island = island;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.quality = quality;
            this.level = level;
        }

        public long itemCount() {
            long n = 0;
            for (long v : items.values()) n += v;
            return n;
        }

        String key() {
            return world + ";" + x + ";" + y + ";" + z;
        }
    }

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final File file;
    private final NamespacedKey typeKey, qualityKey, levelKey;
    private final Map<String, Type> types = new LinkedHashMap<>();
    private final Map<String, Gen> byPos = new HashMap<>();
    private final Map<Integer, double[]> boosts = new HashMap<>();
    private int nextId = 1;
    private boolean dirty;

    public GeneratorService(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "generators.yml");
        this.typeKey = new NamespacedKey(addon, "gen_type");
        this.qualityKey = new NamespacedKey(addon, "gen_quality");
        this.levelKey = new NamespacedKey(addon, "gen_level");
        loadTypes();
        load();
        long period = Math.max(2, addon.getConfig().getLong("generatoren.tick-sekunden", 10)) * 20L;
        Bukkit.getScheduler().runTaskTimer(addon, this::tick, period, period);
        Bukkit.getScheduler().runTaskTimer(addon, () -> {
            if (dirty) save();
        }, 1200L, 1200L);
    }

    // =================================================================================================================
    // Arten
    // =================================================================================================================

    private void loadTypes() {
        types.clear();
        ConfigurationSection s = addon.getConfig().getConfigurationSection("generatoren.typen");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection t = s.getConfigurationSection(id);
            if (t == null) continue;
            Kind kind = switch (t.getString("art", "item").toLowerCase(Locale.ROOT)) {
                case "geld" -> Kind.MONEY;
                case "xp" -> Kind.XP;
                default -> Kind.ITEM;
            };
            Material block = Material.matchMaterial(t.getString("block", "STONE"));
            TreeMap<Integer, Map<Material, Integer>> yields = new TreeMap<>();
            ConfigurationSection y = t.getConfigurationSection("ertrag");
            if (y != null) {
                for (String lvl : y.getKeys(false)) {
                    ConfigurationSection w = y.getConfigurationSection(lvl);
                    if (w == null) continue;
                    Map<Material, Integer> map = new EnumMap<>(Material.class);
                    for (String m : w.getKeys(false)) {
                        Material mat = Material.matchMaterial(m);
                        if (mat != null && mat.isItem()) map.put(mat, w.getInt(m));
                    }
                    try {
                        yields.put(Integer.parseInt(lvl), map);
                    } catch (NumberFormatException ignored) {
                        // keine Zahl
                    }
                }
            }
            types.put(id, new Type(id, kind, t.getString("name", id), block == null || !block.isBlock() ? Material.STONE : block,
                    t.getLong("preis", 5000), t.getInt("ab-insel-level", 0), t.getDouble("pro-sekunde", 1.0),
                    t.getDouble("intervall-faktor", 1.0), yields));
        }
    }

    public List<Type> types() {
        return new ArrayList<>(types.values());
    }

    public Type type(String id) {
        return types.get(id);
    }

    public int maxLevel() {
        return Math.max(1, addon.getConfig().getInt("generatoren.max-stufe", 10));
    }

    /** Anzeigename mit Qualität, z. B. „Gold-Geld-Generator“. */
    public String name(Type t, Quality q) {
        return q == Quality.NORMAL ? t.name() : q.label + "-" + t.name();
    }

    // =================================================================================================================
    // Werte je Stufe
    // =================================================================================================================

    private double levelMult(int level) {
        return 1 + (level - 1) * addon.getConfig().getDouble("generatoren.ertrag-pro-stufe", 0.35);
    }

    /** Geld oder XP je Sekunde (ohne Booster). */
    public double perSecond(Gen g) {
        Type t = types.get(g.type);
        return t == null ? 0 : t.perSecond() * levelMult(g.level) * g.quality.output;
    }

    /** Sekunden je Item (Item-Generatoren, ohne Booster). */
    public double interval(Gen g) {
        Type t = types.get(g.type);
        double base = addon.getConfig().getDouble("generatoren.intervall-sekunden", 30) * (t == null ? 1 : t.intervalFactor());
        double speed = 1 + (g.level - 1) * addon.getConfig().getDouble("generatoren.tempo-pro-stufe", 0.12);
        return Math.max(1, base / speed / g.quality.output);
    }

    /** Lager: Items (Stückzahl) oder Geld/XP (Stunden Ertrag). */
    public double capacity(Gen g) {
        Type t = types.get(g.type);
        if (t != null && t.kind() != Kind.ITEM) {
            return perSecond(g) * 3600 * addon.getConfig().getDouble("generatoren.lager-stunden", 2) * g.quality.storage;
        }
        List<Integer> l = addon.getConfig().getIntegerList("generatoren.lager");
        if (l.isEmpty()) l = List.of(64, 128, 256, 384, 512, 768, 1024, 1536, 2048, 3072);
        return l.get(Math.max(0, Math.min(g.level - 1, l.size() - 1))) * g.quality.storage;
    }

    /** Preis der nächsten Stufe (-1 = höchste). */
    public long upgradePrice(Gen g) {
        if (g.level >= maxLevel()) return -1;
        List<Long> l = addon.getConfig().getLongList("generatoren.upgrade-kosten");
        if (l.isEmpty()) l = List.of(0L, 2_000L, 5_000L, 12_000L, 25_000L, 50_000L, 100_000L, 200_000L, 400_000L, 800_000L);
        Type t = types.get(g.type);
        double typeFactor = t == null ? 1 : Math.max(0.2, t.price() / 5000.0);
        return Math.round(l.get(Math.min(g.level, l.size() - 1)) * typeFactor * g.quality.upgrade);
    }

    /** Juwelen für Kauf (neu) oder Veredeln auf diese Qualität. */
    public long jewels(Quality q, boolean refine) {
        return addon.getConfig().getLong("generatoren.qualitaeten." + q.name().toLowerCase(Locale.ROOT) + (refine ? ".veredeln" : ".juwelen"),
                switch (q) {
                    case SILBER -> refine ? 100 : 150;
                    case GOLD -> refine ? 300 : 400;
                    case DIAMANT -> refine ? 700 : 900;
                    default -> 0;
                });
    }

    /** Ertrag der Stufe (Item-Generatoren): die höchste Stufe aus „ertrag“, die erreicht ist. */
    public Map<Material, Integer> yields(Gen g) {
        Type t = types.get(g.type);
        if (t == null || t.yields().isEmpty()) return Map.of(Material.COBBLESTONE, 1);
        Map.Entry<Integer, Map<Material, Integer>> e = t.yields().floorEntry(g.level);
        return e == null ? t.yields().firstEntry().getValue() : e.getValue();
    }

    public int slots(IslandService.Island i) {
        List<Integer> l = addon.getConfig().getIntegerList("generatoren.plaetze");
        if (l.isEmpty()) l = List.of(3, 5, 8, 12, 16);
        return l.get(Math.max(0, Math.min(i.size, l.size() - 1)));
    }

    // =================================================================================================================
    // Booster
    // =================================================================================================================

    /** Aktiver Booster der Insel: [Faktor, Ende in ms] oder null. */
    public double[] boost(int island) {
        double[] b = boosts.get(island);
        if (b == null || b[1] < System.currentTimeMillis()) return null;
        return b;
    }

    public void addBoost(int island, double factor, int minutes) {
        long now = System.currentTimeMillis();
        double[] cur = boost(island);
        long start = cur != null && cur[0] == factor ? (long) cur[1] : now;
        boosts.put(island, new double[]{factor, start + minutes * 60_000L});
        dirty = true;
    }

    // =================================================================================================================
    // Generatoren
    // =================================================================================================================

    public Gen at(Block b) {
        return byPos.get(b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ());
    }

    /** Anzahl der Generatoren je Insel (Zwischenspeicher, wird bei jeder Änderung neu gezählt). */
    private java.util.Map<Integer, Integer> counts;

    public int count(int island) {
        java.util.Map<Integer, Integer> c = counts;
        if (c == null) {
            c = new HashMap<>();
            for (Gen g : byPos.values()) c.merge(g.island, 1, Integer::sum);
            counts = c;
        }
        return c.getOrDefault(island, 0);
    }

    public List<Gen> onIsland(int island) {
        List<Gen> out = new ArrayList<>();
        for (Gen g : byPos.values()) if (g.island == island) out.add(g);
        out.sort((a, b) -> Integer.compare(a.id, b.id));
        return out;
    }

    /** Generator-Item: ein Spawner (wird als Spawner-Käfig aufgestellt) mit eigenem 3D-Modell je Art und Qualität
     *  (kuby:generator/<art>[_silber|_gold|_diamant]); Art, Qualität und Stufe stehen im Item. */
    public ItemStack item(Type t, Quality q, int level) {
        ItemStack it = new ItemStack(Material.SPAWNER);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(NamespacedKey.fromString("kuby:generator/" + t.id() + (q == Quality.NORMAL ? "" : "_" + q.name().toLowerCase(java.util.Locale.ROOT))));
        m.displayName(Msg.item("<" + q.color + "><b>" + name(t, q) + "</b> <gray>Stufe " + level));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Msg.item("<gray>Auf deine Insel stellen: produziert von selbst."));
        if (q != Quality.NORMAL) lore.add(Msg.item("<" + q.color + ">Premium: x" + fmt(q.output) + " Ertrag, Upgrades -" + Math.round((1 - q.upgrade) * 100) + " %"));
        lore.add(Msg.item("<dark_gray>Rechtsklick auf den Generator öffnet ihn."));
        m.lore(lore);
        if (q != Quality.NORMAL) m.setEnchantmentGlintOverride(true);
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(typeKey, PersistentDataType.STRING, t.id());
        pdc.set(qualityKey, PersistentDataType.STRING, q.name());
        pdc.set(levelKey, PersistentDataType.INTEGER, level);
        it.setItemMeta(m);
        return it;
    }

    /** Ist das ein Generator-Item? Dann [Art, Qualität, Stufe]. */
    public Object[] read(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        String t = pdc.get(typeKey, PersistentDataType.STRING);
        if (t == null || !types.containsKey(t)) return null;
        Quality q;
        try {
            q = Quality.valueOf(pdc.getOrDefault(qualityKey, PersistentDataType.STRING, "NORMAL"));
        } catch (IllegalArgumentException e) {
            q = Quality.NORMAL;
        }
        return new Object[]{types.get(t), q, Math.max(1, pdc.getOrDefault(levelKey, PersistentDataType.INTEGER, 1))};
    }

    /** Aufgestellt: auf der eigenen Insel und mit freiem Platz. Gibt null bei Erfolg zurück. */
    public String place(Player p, Block b, Type t, Quality q, int level) {
        IslandService.Island i = addon.islands().islandAt(b.getLocation());
        if (i == null || !i.isMember(p.getUniqueId())) return "Generatoren stellst du auf deiner Insel auf.";
        int used = onIsland(i.slot).size();
        if (used >= slots(i)) return "Deine Insel hat nur " + slots(i) + " Generator-Plätze. Mehr gibt es mit dem Größen-Upgrade.";
        IslandService is = addon.islands();
        if (is.levelOf(i) < t.unlockLevel() && !p.hasPermission("kuby.admin")) return "Diesen Generator gibt es ab Insel-Level " + t.unlockLevel() + ".";
        Gen g = new Gen(nextId++, i.slot, b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), t.id(), q, Math.max(1, Math.min(level, maxLevel())));
        g.last = System.currentTimeMillis();
        counts = null;
        byPos.put(g.key(), g);
        dirty = true;
        // Käfig einrichten, wenn der Block sicher steht (nach dem Platzieren)
        Bukkit.getScheduler().runTask(addon, () -> dress(g));
        hologram(g, true);
        core.questProgress(p, "gen_place", t.id(), 1);
        return null;
    }

    /** Abbauen: Generator als Item zurück, Lager wird ausgezahlt. */
    public void remove(Player p, Gen g) {
        collect(p, g);
        counts = null;
        byPos.remove(g.key());
        removeHologram(g);
        Type t = types.get(g.type);
        if (t != null) p.getInventory().addItem(item(t, g.quality, g.level)).values().forEach(r -> core.abholbox().add(p.getUniqueId(), r));
        dirty = true;
    }

    // =================================================================================================================
    // Produktion
    // =================================================================================================================

    private boolean islandOnline(int slot) {
        IslandService.Island i = addon.islands().bySlot(slot);
        if (i == null) return false;
        for (UUID u : i.everyone()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && ModeService.SKYBLOCK.equals(core.modes().modeOf(u))) return true;
        }
        return false;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        Map<Integer, Boolean> online = new HashMap<>();
        for (Gen g : byPos.values()) {
            produce(g, now, online.computeIfAbsent(g.island, this::islandOnline));
        }
        for (Gen g : byPos.values()) hologram(g, false);
        dirty = true;
    }

    /** Bis jetzt produzieren (auch beim Öffnen des Dialogs aufgerufen). */
    public void produce(Gen g, long now, boolean online) {
        Type t = types.get(g.type);
        if (t == null) return;
        double dt = Math.max(0, (now - g.last) / 1000.0);
        g.last = now;
        if (dt <= 0) return;
        double factor = online ? 1.0 : g.quality.offline;
        double[] b = boost(g.island);
        if (b != null) factor *= b[0];
        double cap = capacity(g);
        IslandService.Island isl = addon.islands().bySlot(g.island);
        UUID owner = isl == null ? null : isl.owner;
        switch (t.kind()) {
            case MONEY -> {
                double add = perSecond(g) * dt * factor;
                if (g.quality.autoCollect && owner != null) {
                    g.money += add;
                    long whole = (long) g.money;
                    if (whole > 0) {
                        core.modes().depositIn(owner, ModeService.SKYBLOCK, whole);
                        g.money -= whole;
                    }
                } else {
                    g.money = Math.min(cap, g.money + add);
                }
            }
            case XP -> {
                double add = perSecond(g) * dt * factor;
                Player op = owner == null ? null : Bukkit.getPlayer(owner);
                if (g.quality.autoCollect && op != null && ModeService.SKYBLOCK.equals(core.modes().modeOf(owner))) {
                    g.xp += add;
                    long whole = (long) g.xp;
                    if (whole > 0) {
                        core.stats().get(owner).xp += whole;
                        g.xp -= whole;
                    }
                } else {
                    g.xp = Math.min(cap, g.xp + add);
                }
            }
            default -> {
                g.progress += dt * factor / interval(g);
                int n = (int) Math.floor(g.progress);
                if (n <= 0) return;
                g.progress -= n;
                Map<Material, Integer> w = yields(g);
                int total = 0;
                for (int v : w.values()) total += Math.max(0, v);
                if (total <= 0) return;
                ThreadLocalRandom r = ThreadLocalRandom.current();
                // viele Schritte auf einmal (lange offline): statt einzeln würfeln anteilig verteilen
                Map<Material, Long> made = new EnumMap<>(Material.class);
                if (n > 200) {
                    for (var e : w.entrySet()) made.merge(e.getKey(), Math.round((double) n * e.getValue() / total), Long::sum);
                } else {
                    for (int k = 0; k < n; k++) {
                        int x = r.nextInt(total);
                        for (var e : w.entrySet()) {
                            x -= e.getValue();
                            if (x < 0) {
                                made.merge(e.getKey(), 1L, Long::sum);
                                break;
                            }
                        }
                    }
                }
                if (g.quality.autoSell) {
                    long value = 0;
                    for (var e : made.entrySet()) value += sellValue(e.getKey()) * e.getValue();
                    if (g.quality.autoCollect && owner != null) core.modes().depositIn(owner, ModeService.SKYBLOCK, value);
                    else g.money = Math.min(Math.max(cap * 50, 1_000_000), g.money + value);
                } else {
                    long room = (long) cap - g.itemCount();
                    for (var e : made.entrySet()) {
                        if (room <= 0) break;
                        long put = Math.min(room, e.getValue());
                        g.items.merge(e.getKey(), put, Long::sum);
                        room -= put;
                    }
                }
            }
        }
    }

    /** Verkaufswert je Stück (Server-Ankauf, sonst 1). */
    public long sellValue(Material m) {
        Long v = core.sell().worth().get(m);
        return v == null || v <= 0 ? 1 : v;
    }

    /** Lager einsammeln: Items ins Inventar (Rest in die Abholbox), Geld aufs Konto, XP dazu. */
    public String collect(Player p, Gen g) {
        produce(g, System.currentTimeMillis(), true);
        long coins = (long) g.money, xp = (long) g.xp, items = 0;
        if (coins > 0) {
            core.economy().deposit(p.getUniqueId(), coins);
            g.money -= coins;
        }
        if (xp > 0) {
            core.stats().get(p.getUniqueId()).xp += xp;
            g.xp -= xp;
        }
        for (var e : new ArrayList<>(g.items.entrySet())) {
            long left = e.getValue();
            while (left > 0) {
                int n = (int) Math.min(left, e.getKey().getMaxStackSize());
                p.getInventory().addItem(new ItemStack(e.getKey(), n)).values().forEach(r -> core.abholbox().add(p.getUniqueId(), r));
                left -= n;
                items += n;
            }
            addon.progress().recordItem(p, e.getKey());
        }
        g.items.clear();
        dirty = true;
        if (coins == 0 && xp == 0 && items == 0) return "Das Lager ist leer.";
        // Quests (Skyblock): eingesammelte Items, Geld und XP aus Generatoren
        core.questProgress(p, "gen_collect", g.type, items);
        core.questProgress(p, "gen_geld", g.type, coins);
        core.questProgress(p, "gen_xp", g.type, xp);
        Msg.send(p, "<#9AD8FF>Eingesammelt: " + (coins > 0 ? "<gold>" + Msg.coins(coins) + " $ " : "") + (xp > 0 ? "<green>" + Msg.coins(xp) + " XP " : "")
                + (items > 0 ? "<white>" + Msg.coins(items) + " Items" : ""));
        return null;
    }

    /** Items des Lagers zum Ankaufspreis verkaufen. */
    public String sell(Player p, Gen g) {
        produce(g, System.currentTimeMillis(), true);
        long value = 0, n = 0;
        for (var e : g.items.entrySet()) {
            value += sellValue(e.getKey()) * e.getValue();
            n += e.getValue();
            if (core.trading() != null) core.trading().demandMaterial(e.getKey(), (int) Math.min(Integer.MAX_VALUE, e.getValue()), -1);
        }
        long coins = value + (long) g.money;
        if (coins <= 0) return "Es gibt nichts zu verkaufen.";
        g.items.clear();
        g.money -= (long) g.money;
        core.economy().deposit(p.getUniqueId(), coins);
        dirty = true;
        core.questProgress(p, "gen_geld", g.type, coins);
        Msg.send(p, "<#9AD8FF>Verkauft: <white>" + Msg.coins(n) + " Items <gray>für <gold>" + Msg.coins(coins) + " $");
        return null;
    }

    public String upgrade(Player p, Gen g) {
        long price = upgradePrice(g);
        if (price < 0) return "Das ist schon die höchste Stufe.";
        if (!core.economy().withdraw(p.getUniqueId(), price)) return "Dafür brauchst du " + Msg.coins(price) + " $.";
        produce(g, System.currentTimeMillis(), true);
        g.level++;
        dirty = true;
        hologram(g, false);
        Sfx.play(core, p, Sfx.Kind.LEVEL_UP);
        core.questProgress(p, "gen_upgrade", g.type, 1);
        return null;
    }

    /** Veredeln: nächste Qualität für Juwelen (Stufe und Lager bleiben). */
    public String refine(Player p, Gen g) {
        Quality next = g.quality.next();
        if (next == null) return "Das ist schon die beste Qualität.";
        long cost = jewels(next, true);
        if (!core.jewels().take(p.getUniqueId(), cost)) return "Dafür brauchst du " + cost + " Juwelen.";
        produce(g, System.currentTimeMillis(), true);
        g.quality = next;
        dirty = true;
        hologram(g, false);
        core.questProgress(p, "gen_veredeln", g.type, 1);
        Sfx.play(core, p, Sfx.Kind.SUCCESS_BIG);
        return null;
    }

    // =================================================================================================================
    // Schild über dem Generator
    // =================================================================================================================

    /**
     * Im Spiel steht jeder Generator als Spawner-Käfig; innen dreht sich das Symbol der Art (mit Pack das eigene Bild
     * kuby:generator_core/<art>, ohne Pack Papier). Er spawnt nie etwas: höchstens 0 Wesen in der Nähe, dazu fängt der
     * GeneratorListener jedes Spawnen ab. Alte Generatoren (andere Blöcke) werden beim Laden umgestellt.
     */
    public void dress(Gen g) {
        World w = Bukkit.getWorld(g.world);
        if (w == null || !w.isChunkLoaded(g.x >> 4, g.z >> 4) || byPos.get(g.key()) != g) return;
        org.bukkit.block.Block b = w.getBlockAt(g.x, g.y, g.z);
        if (b.getType() != Material.SPAWNER) b.setType(Material.SPAWNER, false);
        g.dressed = true;
        if (!(b.getState() instanceof org.bukkit.block.CreatureSpawner cs)) return;
        try {
            ItemStack core = new ItemStack(Material.PAPER);
            ItemMeta cm = core.getItemMeta();
            cm.setItemModel(NamespacedKey.fromString("kuby:generator_core/" + g.type));
            core.setItemMeta(cm);
            cs.setSpawnedItem(core);
            cs.setMaxNearbyEntities(0);
            cs.setRequiredPlayerRange(24);
            cs.setMaxSpawnDelay(400);
            cs.setMinSpawnDelay(200);
            cs.setDelay(300);
            cs.update(true, false);
        } catch (RuntimeException ex) {
            addon.getLogger().warning("Generator-Käfig " + g.key() + ": " + ex.getMessage());
        }
    }

    private void hologram(Gen g, boolean force) {
        World w = Bukkit.getWorld(g.world);
        if (w == null || !w.isChunkLoaded(g.x >> 4, g.z >> 4)) return;
        if (!g.dressed) dress(g);
        Location l = new Location(w, g.x + 0.5, g.y + 1.35, g.z + 0.5);
        boolean near = false;
        for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(l) < 48 * 48) near = true;
        if (!near && !force) return;
        Entity e = g.display == null ? null : Bukkit.getEntity(g.display);
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
            g.display = td.getUniqueId();
        }
        Type t = types.get(g.type);
        if (t == null) return;
        String store = switch (t.kind()) {
            case MONEY -> g.quality.autoCollect ? "direkt aufs Konto" : Msg.coins((long) g.money) + " / " + Msg.coins((long) capacity(g)) + " $";
            case XP -> g.quality.autoCollect ? "direkt zu dir" : Msg.coins((long) g.xp) + " / " + Msg.coins((long) capacity(g)) + " XP";
            default -> g.quality.autoSell ? "Auto-Verkauf · " + Msg.coins((long) g.money) + " $" : Msg.coins(g.itemCount()) + " / " + Msg.coins((long) capacity(g));
        };
        double[] b = boost(g.island);
        td.text(Msg.parse("<" + g.quality.color + "><b>⚙ " + Msg.escape(name(t, g.quality)) + "</b>\n<gray>Stufe <white>" + g.level
                + " <dark_gray>· <white>" + store + (b != null ? "\n<gold>⚡ Booster x" + fmt(b[0]) : "")));
        if (g.quality == Quality.DIAMANT) w.spawnParticle(Particle.END_ROD, l.clone().add(0, -0.6, 0), 2, 0.3, 0.2, 0.3, 0.01);
    }

    private void removeHologram(Gen g) {
        Entity e = g.display == null ? null : Bukkit.getEntity(g.display);
        if (e != null) e.remove();
        g.display = null;
    }

    public void removeHolograms() {
        for (Gen g : byPos.values()) removeHologram(g);
    }

    public static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.GERMANY, "%.2f", v);
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = y.getInt("next", 1);
        ConfigurationSection gs = y.getConfigurationSection("gens");
        if (gs != null) {
            for (String k : gs.getKeys(false)) {
                ConfigurationSection s = gs.getConfigurationSection(k);
                if (s == null) continue;
                try {
                    Gen g = new Gen(Integer.parseInt(k), s.getInt("island"), s.getString("world"), s.getInt("x"), s.getInt("y"), s.getInt("z"),
                            s.getString("type"), Quality.valueOf(s.getString("quality", "NORMAL")), s.getInt("level", 1));
                    g.money = s.getDouble("money");
                    g.xp = s.getDouble("xp");
                    g.progress = s.getDouble("progress");
                    g.last = s.getLong("last", System.currentTimeMillis());
                    ConfigurationSection it = s.getConfigurationSection("items");
                    if (it != null) {
                        for (String m : it.getKeys(false)) {
                            Material mat = Material.matchMaterial(m);
                            if (mat != null) g.items.put(mat, it.getLong(m));
                        }
                    }
                    counts = null;
                    byPos.put(g.key(), g);
                } catch (RuntimeException ex) {
                    addon.getLogger().warning("generators.yml: Generator " + k + " übersprungen (" + ex.getMessage() + ")");
                }
            }
        }
        ConfigurationSection bs = y.getConfigurationSection("boosts");
        if (bs != null) {
            for (String k : bs.getKeys(false)) {
                List<Double> v = bs.getDoubleList(k);
                if (v.size() == 2) boosts.put(Integer.parseInt(k), new double[]{v.get(0), v.get(1)});
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        out.set("next", nextId);
        for (Gen g : byPos.values()) {
            String b = "gens." + g.id + ".";
            out.set(b + "island", g.island);
            out.set(b + "world", g.world);
            out.set(b + "x", g.x);
            out.set(b + "y", g.y);
            out.set(b + "z", g.z);
            out.set(b + "type", g.type);
            out.set(b + "quality", g.quality.name());
            out.set(b + "level", g.level);
            out.set(b + "money", g.money);
            out.set(b + "xp", g.xp);
            out.set(b + "progress", g.progress);
            out.set(b + "last", g.last);
            g.items.forEach((m, n) -> out.set(b + "items." + m.name(), n));
        }
        boosts.forEach((k, v) -> {
            if (v[1] > System.currentTimeMillis()) out.set("boosts." + k, List.of(v[0], v[1]));
        });
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
            dirty = false;
        } catch (IOException e) {
            addon.getLogger().warning("generators.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}

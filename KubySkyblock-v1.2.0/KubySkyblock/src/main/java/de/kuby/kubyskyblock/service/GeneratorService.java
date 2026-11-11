package de.kuby.kubyskyblock.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generatoren als Grundmechanik von Skyblock. Drei Arten: Item-Generatoren (Erz, Holz, Farm, Mob, Nether, Edelstein),
 * Geld-Generator (ab 0,50 $/s) und XP-Generator (ab 1 XP/s).
 * <p>
 * Generatoren laufen nur in der UI (Entscheidung des Besitzers): sie stehen nicht als Block auf der Insel und nehmen
 * keinen Platz weg. Gekauft wird im Generator-Shop, der Generator läuft sofort; Plätze gibt es je Bereich (Insel, Nether,
 * End) nach Inselgröße. Ein Generator-Item (Ausbauen, Belohnungen, Handel) wird per Rechtsklick eingesetzt. Sie
 * produzieren ins eigene Lager, das man im Generator-Dialog einsammelt oder verkauft. Stufen 1 bis 10 (schneller, mehr
 * Lager). Qualitäten Normal, Silber, Gold und Diamant (Juwelenshop): mehr Ertrag, günstigere Upgrades, größeres Lager,
 * Offline-Produktion; Gold und Diamant verkaufen Items automatisch, Diamant zahlt Geld und XP direkt aus. Booster je
 * Insel (x2, x3 für eine Zeit). Ohne Spieler der Insel online läuft die Produktion mit dem Offline-Anteil der Qualität.
 * Früher aufgestellte Generatoren werden beim Laden übernommen, ihre alten Blöcke beim Laden des Chunks entfernt.
 * Daten: generators.yml.
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

    /** Art eines Generators aus der config; stage: "" = normale Insel, "nether" oder "end" = nur auf der Stufen-Insel. */
    public record Type(String id, Kind kind, String name, Material block, long price, int unlockLevel, double perSecond,
                       double intervalFactor, TreeMap<Integer, Map<Material, Integer>> yields, String stage) {}

    public static final class Gen {
        public final int id;
        public final int island;
        public final String type;
        public Quality quality;
        public int level;
        public final Map<Material, Long> items = new EnumMap<>(Material.class);
        public double money, xp, progress;
        public long last;

        Gen(int id, int island, String type, Quality quality, int level) {
            this.id = id;
            this.island = island;
            this.type = type;
            this.quality = quality;
            this.level = level;
        }

        public long itemCount() {
            long n = 0;
            for (long v : items.values()) n += v;
            return n;
        }
    }

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final File file;
    private final NamespacedKey typeKey, qualityKey, levelKey;
    private final Map<String, Type> types = new LinkedHashMap<>();
    private final Map<Integer, Gen> gens = new LinkedHashMap<>();
    /** Blöcke früher aufgestellter Generatoren (welt;x;y;z): werden beim Laden ihres Chunks entfernt. */
    private final java.util.Set<String> legacy = new LinkedHashSet<>();
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
        // alte Generator-Blöcke in schon geladenen Chunks entfernen
        Bukkit.getScheduler().runTaskLater(addon, this::cleanLoaded, 40L);
        Bukkit.getScheduler().runTaskTimer(addon, () -> {
            if (dirty) save();
        }, 1200L, 1200L);
    }

    // =================================================================================================================
    // Arten
    // =================================================================================================================

    /** Zahlen aus generatoren.* (einmal beim Start gelesen, nicht je Generator und Tick). */
    private double intervalBase, speedPerLevel, yieldPerLevel, storageHours, moneyFactor;
    private List<Integer> storage = List.of();

    private void loadSettings() {
        var c = addon.getConfig();
        // Faktoren für schnelleren Fortschritt (gelten zusätzlich zu den Werten oben, auch für ältere configs)
        double tempo = Math.max(0.1, c.getDouble("generatoren.tempo-faktor", 1.6));
        intervalBase = c.getDouble("generatoren.intervall-sekunden", 30) / tempo;
        speedPerLevel = c.getDouble("generatoren.tempo-pro-stufe", 0.12);
        yieldPerLevel = c.getDouble("generatoren.ertrag-pro-stufe", 0.35);
        storageHours = c.getDouble("generatoren.lager-stunden", 2) * Math.max(1, c.getDouble("generatoren.lager-faktor", 1.5));
        moneyFactor = Math.max(0.1, c.getDouble("generatoren.geld-faktor", 2.0));
        List<Integer> l = c.getIntegerList("generatoren.lager");
        storage = l.isEmpty() ? List.of(64, 128, 256, 384, 512, 768, 1024, 1536, 2048, 3072) : List.copyOf(l);
    }

    private void loadTypes() {
        loadSettings();
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
            double buyFactor = Math.max(0.05, addon.getConfig().getDouble("generatoren.kauf-faktor", 0.7));
            types.put(id, new Type(id, kind, t.getString("name", id), block == null || !block.isBlock() ? Material.STONE : block,
                    Math.max(50, Math.round(t.getLong("preis", 5000) * buyFactor / 50.0) * 50), t.getInt("ab-insel-level", 0), t.getDouble("pro-sekunde", 1.0),
                    t.getDouble("intervall-faktor", 1.0), yields, t.getString("stufe", "").toLowerCase(Locale.ROOT)));
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
        return 1 + (level - 1) * yieldPerLevel;
    }

    /** Geld oder XP je Sekunde (ohne Booster). */
    public double perSecond(Gen g) {
        Type t = types.get(g.type);
        return t == null ? 0 : t.perSecond() * moneyFactor * levelMult(g.level) * g.quality.output;
    }

    /** Sekunden je Item (Item-Generatoren, ohne Booster). */
    public double interval(Gen g) {
        Type t = types.get(g.type);
        double base = intervalBase * (t == null ? 1 : t.intervalFactor());
        double speed = 1 + (g.level - 1) * speedPerLevel;
        return Math.max(1, base / speed / g.quality.output);
    }

    /** Sekunden je Item einer Art auf einer Stufe (normale Qualität, für den Shop). */
    public double intervalOf(Type t, int level) {
        double base = intervalBase * t.intervalFactor();
        return Math.max(1, base / (1 + (level - 1) * speedPerLevel));
    }

    /** Geld oder XP je Sekunde einer Art auf einer Stufe (normale Qualität, für den Shop). */
    public double perSecondOf(Type t, int level) {
        return t.perSecond() * moneyFactor * levelMult(level);
    }

    /** Lager: Items (Stückzahl) oder Geld/XP (Stunden Ertrag). */
    public double capacity(Gen g) {
        Type t = types.get(g.type);
        if (t != null && t.kind() != Kind.ITEM) {
            return perSecond(g) * 3600 * storageHours * g.quality.storage;
        }
        List<Integer> l = storage;
        return l.get(Math.max(0, Math.min(g.level - 1, l.size() - 1))) * g.quality.storage;
    }

    /** Inselpunkte eines Generators: Grundwert, je Stufe mehr, Qualität vervielfacht (Stufen-Generatoren mehr). */
    public long points(Gen g) {
        var c = addon.getConfig();
        Type t = types.get(g.type);
        double base = c.getDouble("level.bonus.generator", 60) * (t != null && !t.stage().isEmpty() ? 1.5 : 1.0);
        double lvl = 1 + c.getDouble("level.bonus.generator-je-stufe", 0.3) * (g.level - 1);
        double q = 1 + 0.6 * g.quality.ordinal();
        return Math.round(base * lvl * q);
    }

    /** Inselpunkte aller Generatoren einer Insel (auch auf den Stufen-Inseln). */
    public long pointsOf(int island) {
        long sum = 0;
        for (Gen g : gens.values()) if (g.island == island) sum += points(g);
        return sum;
    }

    /** Preis der nächsten Stufe (-1 = höchste). */
    public long upgradePrice(Gen g) {
        if (g.level >= maxLevel()) return -1;
        List<Long> l = addon.getConfig().getLongList("generatoren.upgrade-kosten");
        if (l.isEmpty()) l = List.of(0L, 2_000L, 5_000L, 12_000L, 25_000L, 50_000L, 100_000L, 200_000L, 400_000L, 800_000L);
        Type t = types.get(g.type);
        double typeFactor = t == null ? 1 : Math.max(0.2, t.price() / 5000.0);
        double factor = Math.max(0.05, addon.getConfig().getDouble("generatoren.upgrade-faktor", 0.85));
        return Math.max(1, Math.round(l.get(Math.min(g.level, l.size() - 1)) * typeFactor * g.quality.upgrade * factor / 50.0) * 50);
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

    /** Generator-Plätze je Insel (und je Stufen-Insel). generatoren.plaetze gilt für die alten 5 Größen-Stufen, die jetzt
     *  auf Level 0, 5, 10, 15 und 20 liegen: dazwischen gleichmäßig, darüber (Stufe 4) ein Platz mehr je 5 Level. */
    public int slots(IslandService.Island i) {
        List<Integer> l = addon.getConfig().getIntegerList("generatoren.plaetze");
        if (l.isEmpty()) l = List.of(3, 5, 8, 12, 16);
        int last = l.size() - 1;
        int lvl = Math.max(0, i.size);
        if (lvl >= last * 5) return l.get(last) + (lvl - last * 5) / 5;
        int k = lvl / 5;
        double t = (lvl - k * 5) / 5.0;
        return (int) Math.round(l.get(k) + (l.get(k + 1) - l.get(k)) * t);
    }

    /** Plätze aller freigeschalteten Bereiche zusammen (Insel, dazu Nether und End nach dem Freischalten). */
    public int totalSlots(IslandService.Island i) {
        int areas = 1;
        if (addon.stages() != null) {
            for (StageService.Stage st : StageService.Stage.values()) if (addon.stages().unlocked(i, st)) areas++;
        }
        return slots(i) * areas;
    }

    /** Bereich eines Generators: "" = Insel, "nether" oder "end" (Stufen-Generatoren). Die Plätze gelten je Bereich. */
    public String area(Gen g) {
        return StageService.stageKey(types.get(g.type));
    }

    /** Generatoren der Insel in einem Bereich ("" Insel, "nether", "end"). */
    public int onIslandIn(int island, String area) {
        int n = 0;
        for (Gen g : gens.values()) if (g.island == island && area.equals(area(g))) n++;
        return n;
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

    public Gen byId(int id) {
        return gens.get(id);
    }

    /** Anzahl der Generatoren je Insel (Zwischenspeicher, wird bei jeder Änderung neu gezählt). */
    private java.util.Map<Integer, Integer> counts;

    public int count(int island) {
        java.util.Map<Integer, Integer> c = counts;
        if (c == null) {
            c = new HashMap<>();
            for (Gen g : gens.values()) c.merge(g.island, 1, Integer::sum);
            counts = c;
        }
        return c.getOrDefault(island, 0);
    }

    public List<Gen> onIsland(int island) {
        List<Gen> out = new ArrayList<>();
        for (Gen g : gens.values()) if (g.island == island) out.add(g);
        out.sort((a, b) -> Integer.compare(a.id, b.id));
        return out;
    }

    /** Generator-Item (zum Einsetzen per Rechtsklick, z. B. nach dem Ausbauen oder als Belohnung) mit eigenem 3D-Modell
     *  je Art und Qualität (kuby:generator/<art>[_silber|_gold|_diamant]); Art, Qualität und Stufe stehen im Item. */
    public ItemStack item(Type t, Quality q, int level) {
        ItemStack it = new ItemStack(Material.SPAWNER);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(NamespacedKey.fromString("kuby:generator/" + t.id() + (q == Quality.NORMAL ? "" : "_" + q.name().toLowerCase(java.util.Locale.ROOT))));
        m.displayName(Msg.item("<" + q.color + "><b>" + name(t, q) + "</b> <gray>Stufe " + level));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Msg.item("<gray>Läuft im Generator-Menü deiner Insel, braucht keinen Platz."));
        if (q != Quality.NORMAL) lore.add(Msg.item("<" + q.color + ">Premium: x" + fmt(q.output) + " Ertrag, Upgrades -" + Math.round((1 - q.upgrade) * 100) + " %"));
        lore.add(Msg.item("<dark_gray>Rechtsklick: auf deiner Insel einsetzen."));
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

    /** Darf der Spieler diese Art auf seiner Insel einsetzen? Gibt null zurück, wenn ja, sonst den Grund. */
    public String canInstall(Player p, Type t) {
        IslandService is = addon.islands();
        IslandService.Island i = is.islandOf(p.getUniqueId());
        if (i == null) return "Du hast noch keine Insel (/insel).";
        if (!is.has(i, p.getUniqueId(), IslandService.Right.GENERATOREN) && !p.hasPermission("kuby.admin")) {
            return "Generatoren darfst du auf dieser Insel nicht verwalten.";
        }
        String area = StageService.stageKey(t);
        StageService.Stage st = StageService.Stage.of(area);
        if (st != null && (addon.stages() == null || !addon.stages().unlocked(i, st))) {
            return "Diesen Generator gibt es erst mit der " + st.label + "-Stufe (/insel stufen).";
        }
        if (is.levelOf(i) < t.unlockLevel() && !p.hasPermission("kuby.admin")) return "Diesen Generator gibt es ab Insel-Level " + t.unlockLevel() + ".";
        int used = onIslandIn(i.slot, area);
        if (used >= slots(i)) {
            return (st == null ? "Deine Insel" : "Dein " + st.label + "-Bereich") + " hat nur " + slots(i)
                    + " Generator-Plätze. Mehr gibt es mit dem Größen-Upgrade, oder du baust einen aus.";
        }
        return null;
    }

    /** Kurzer Sperr-Grund für Shop-Karten (null = kann eingesetzt werden). */
    public String lockShort(Player p, Type t) {
        IslandService is = addon.islands();
        IslandService.Island i = is.islandOf(p.getUniqueId());
        if (i == null) return "keine Insel";
        StageService.Stage st = StageService.Stage.of(StageService.stageKey(t));
        if (st != null && (addon.stages() == null || !addon.stages().unlocked(i, st))) return "ab " + st.label + "-Stufe";
        if (is.levelOf(i) < t.unlockLevel() && !p.hasPermission("kuby.admin")) return "ab Insel-Level " + t.unlockLevel();
        if (onIslandIn(i.slot, StageService.stageKey(t)) >= slots(i)) return "alle Plätze belegt";
        return null;
    }

    /** Generator auf der Insel des Spielers einsetzen (läuft sofort). Gibt null bei Erfolg zurück. */
    public String install(Player p, Type t, Quality q, int level) {
        String err = canInstall(p, t);
        if (err != null) return err;
        IslandService.Island i = addon.islands().islandOf(p.getUniqueId());
        Gen g = new Gen(nextId++, i.slot, t.id(), q, Math.max(1, Math.min(level, maxLevel())));
        g.last = System.currentTimeMillis();
        counts = null;
        gens.put(g.id, g);
        dirty = true;
        core.questProgress(p, "gen_place", t.id(), 1);
        addon.islands().addBonus(i, points(g));
        return null;
    }

    /** Insel aufgegeben (Platz wird frei): ihre Generatoren und ihr Booster verfallen, damit eine neue Insel auf
     *  demselben Platz sie nicht erbt. */
    public void forget(int island) {
        if (gens.values().removeIf(g -> g.island == island) | boosts.remove(island) != null) {
            counts = null;
            dirty = true;
        }
    }

    /**
     * Darf p diesen Generator gerade bedienen? Wird bei jedem Klick neu geprüft: früher galt der Stand beim Öffnen des
     * Menüs, ein gerade entferntes Mitglied konnte über ein offenes Menü noch Lager abholen oder den Generator ausbauen
     * (und als Item mitnehmen). Null = ja, sonst der Grund.
     */
    public String guard(Player p, Gen g) {
        if (gens.get(g.id) != g) return "Diesen Generator gibt es nicht mehr.";
        if (!ModeService.SKYBLOCK.equals(core.modes().modeOf(p.getUniqueId()))) return "Generatoren gibt es nur in Skyblock.";
        IslandService is = addon.islands();
        IslandService.Island i = is.islandOf(p.getUniqueId());
        if (i == null || i.slot != g.island) return "Das ist nicht der Generator deiner Insel.";
        if (!is.has(i, p.getUniqueId(), IslandService.Right.GENERATOREN) && !p.hasPermission("kuby.admin")) {
            return "Generatoren darfst du auf dieser Insel nicht verwalten.";
        }
        return null;
    }

    /** Ausbauen: Lager wird ausgezahlt, der Generator kommt mit Stufe und Qualität als Item zurück. */
    public void remove(Player p, Gen g) {
        String err = guard(p, g);
        if (err != null) {
            Msg.send(p, "<red>" + err);
            return;
        }
        collect(p, g);
        IslandService.Island isl = addon.islands().bySlot(g.island);
        if (isl != null) addon.islands().addBonus(isl, -points(g));
        counts = null;
        gens.remove(g.id);
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
        for (Gen g : gens.values()) {
            produce(g, now, online.computeIfAbsent(g.island, this::islandOnline));
        }
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
        long own = addon.shop() == null ? 0 : addon.shop().sellPrice(m);
        if (own > 0) return own;
        Long v = core.sell().worth().get(m);
        return v == null || v <= 0 ? 1 : v;
    }

    /** Lager einsammeln: Items ins Inventar (Rest in die Abholbox), Geld aufs Konto, XP dazu. */
    public String collect(Player p, Gen g) {
        String err = guard(p, g);
        if (err != null) return err;
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
        String err = guard(p, g);
        if (err != null) return err;
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
        String err = guard(p, g);
        if (err != null) return err;
        long price = upgradePrice(g);
        if (price < 0) return "Das ist schon die höchste Stufe.";
        if (!core.economy().withdraw(p.getUniqueId(), price)) return "Dafür brauchst du " + Msg.coins(price) + " $.";
        produce(g, System.currentTimeMillis(), true);
        long before = points(g);
        g.level++;
        IslandService.Island isl = addon.islands().bySlot(g.island);
        if (isl != null) addon.islands().addBonus(isl, points(g) - before);
        dirty = true;
        Sfx.play(core, p, Sfx.Kind.LEVEL_UP);
        core.questProgress(p, "gen_upgrade", g.type, 1);
        return null;
    }

    /** Veredeln: nächste Qualität für Juwelen (Stufe und Lager bleiben). */
    public String refine(Player p, Gen g) {
        String err = guard(p, g);
        if (err != null) return err;
        Quality next = g.quality.next();
        if (next == null) return "Das ist schon die beste Qualität.";
        long cost = jewels(next, true);
        if (!core.jewels().take(p.getUniqueId(), cost)) return "Dafür brauchst du " + cost + " Juwelen.";
        produce(g, System.currentTimeMillis(), true);
        long before = points(g);
        g.quality = next;
        IslandService.Island isl = addon.islands().bySlot(g.island);
        if (isl != null) addon.islands().addBonus(isl, points(g) - before);
        dirty = true;
        core.questProgress(p, "gen_veredeln", g.type, 1);
        Sfx.play(core, p, Sfx.Kind.SUCCESS_BIG);
        return null;
    }

    // =================================================================================================================
    // Alte Generator-Blöcke (aus der Zeit, als Generatoren auf der Insel standen)
    // =================================================================================================================

    /** Ein Chunk ist geladen: alte Generator-Blöcke darin entfernen (Barriere oder Spawner an der gespeicherten Stelle). */
    public void chunkLoaded(org.bukkit.Chunk ch) {
        if (legacy.isEmpty()) return;
        String w = ch.getWorld().getName();
        List<String> hit = new ArrayList<>();
        for (String k : legacy) {
            String[] a = k.split(";");
            if (a.length != 4 || !a[0].equals(w)) continue;
            try {
                if ((Integer.parseInt(a[1]) >> 4) == ch.getX() && (Integer.parseInt(a[3]) >> 4) == ch.getZ()) hit.add(k);
            } catch (NumberFormatException e) {
                hit.add(k);
            }
        }
        if (hit.isEmpty()) return;
        Bukkit.getScheduler().runTask(addon, () -> {
            for (String k : hit) clearLegacy(k);
        });
    }

    private void cleanLoaded() {
        for (String k : new ArrayList<>(legacy)) {
            String[] a = k.split(";");
            World w = a.length == 4 ? Bukkit.getWorld(a[0]) : null;
            try {
                if (w != null && w.isChunkLoaded(Integer.parseInt(a[1]) >> 4, Integer.parseInt(a[3]) >> 4)) clearLegacy(k);
            } catch (NumberFormatException e) {
                legacy.remove(k);
            }
        }
    }

    private void clearLegacy(String k) {
        if (!legacy.remove(k)) return;
        dirty = true;
        String[] a = k.split(";");
        World w = a.length == 4 ? Bukkit.getWorld(a[0]) : null;
        if (w == null) return;
        try {
            org.bukkit.block.Block b = w.getBlockAt(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            if (b.getType() == Material.BARRIER || b.getType() == Material.SPAWNER) b.setType(Material.AIR, false);
            // nicht gespeicherte Anzeigen (Käfig, Symbol, Schild) sind nach dem Neustart ohnehin weg; zur Sicherheit aufräumen
            for (org.bukkit.entity.Entity e : w.getNearbyEntities(b.getLocation().add(0.5, 0.5, 0.5), 0.6, 1.4, 0.6)) {
                if (e instanceof org.bukkit.entity.Display && !e.isPersistent()) e.remove();
            }
        } catch (NumberFormatException ignored) {
            // kaputter Eintrag
        }
    }

    public boolean isLegacyBlock(org.bukkit.block.Block b) {
        return !legacy.isEmpty() && legacy.contains(b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ());
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
                    Gen g = new Gen(Integer.parseInt(k), s.getInt("island"), s.getString("type"), Quality.valueOf(s.getString("quality", "NORMAL")),
                            s.getInt("level", 1));
                    // früher stand der Generator als Block auf der Insel: den Block später entfernen
                    if (s.contains("world")) legacy.add(s.getString("world") + ";" + s.getInt("x") + ";" + s.getInt("y") + ";" + s.getInt("z"));
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
                    gens.put(g.id, g);
                    nextId = Math.max(nextId, g.id + 1);
                } catch (RuntimeException ex) {
                    addon.getLogger().warning("generators.yml: Generator " + k + " übersprungen (" + ex.getMessage() + ")");
                }
            }
        }
        legacy.addAll(y.getStringList("alte-bloecke"));
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
        for (Gen g : gens.values()) {
            String b = "gens." + g.id + ".";
            out.set(b + "island", g.island);
            out.set(b + "type", g.type);
            out.set(b + "quality", g.quality.name());
            out.set(b + "level", g.level);
            out.set(b + "money", g.money);
            out.set(b + "xp", g.xp);
            out.set(b + "progress", g.progress);
            out.set(b + "last", g.last);
            g.items.forEach((m, n) -> out.set(b + "items." + m.name(), n));
        }
        if (!legacy.isEmpty()) out.set("alte-bloecke", new ArrayList<>(legacy));
        boosts.forEach((k, v) -> {
            if (v[1] > System.currentTimeMillis()) out.set("boosts." + k, List.of(v[0], v[1]));
        });
        DataFiles.save(addon, out, file);
        dirty = false;
    }
}

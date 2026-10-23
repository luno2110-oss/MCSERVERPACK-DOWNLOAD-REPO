package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shop-Sortiment (im Editor bearbeitbar) und der Wochenende-Shop: jeder Spieler bekommt eigene zufällige Angebote. */
public final class ShopService {

    public record Item(String id, Material material, int amount, long price, String name, List<String> lore) {}

    public static final String WEEKEND = "weekend";
    public static final String DAILY = "daily";
    public static final List<String> SECTIONS = List.of("blocks", "food", "redstone", "deco", WEEKEND, DAILY);
    public static final Map<String, String> TITLES = Map.of(
            "blocks", "Blöcke", "food", "Essen", "redstone", "Redstone", "deco", "Dekoration", WEEKEND, "Wochenende", DAILY, "Daily Shop");

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final String[][] DEFAULTS = {
            {"blocks", "COBBLESTONE", "64", "128"}, {"blocks", "OAK_PLANKS", "64", "300"},
            {"blocks", "GLASS", "64", "500"}, {"blocks", "STONE_BRICKS", "64", "640"},
            {"food", "BREAD", "16", "160"}, {"food", "COOKED_BEEF", "16", "480"},
            {"food", "GOLDEN_CARROT", "8", "400"}, {"food", "APPLE", "16", "240"},
            {"redstone", "REDSTONE", "64", "200"}, {"redstone", "REPEATER", "8", "160"},
            {"redstone", "PISTON", "4", "280"}, {"redstone", "OBSERVER", "4", "360"}, {"redstone", "HOPPER", "2", "400"},
            {"deco", "FLOWER_POT", "4", "100"}, {"deco", "PAINTING", "2", "80"},
            {"deco", "LANTERN", "4", "200"}, {"deco", "CANDLE", "8", "240"},
            {WEEKEND, "DIAMOND_BLOCK", "1", "5000"}, {WEEKEND, "NETHERITE_SCRAP", "2", "9000"},
            {WEEKEND, "ENCHANTED_GOLDEN_APPLE", "1", "15000"}, {WEEKEND, "TNT", "16", "800"},
            {WEEKEND, "EMERALD_BLOCK", "1", "4000"}, {WEEKEND, "ANVIL", "1", "2000"},
            {WEEKEND, "TOTEM_OF_UNDYING", "1", "12000"}, {WEEKEND, "ENDER_PEARL", "16", "1200"},
            {DAILY, "GOLDEN_APPLE", "1", "30"}, {DAILY, "DIAMOND", "3", "40"},
            {DAILY, "EMERALD_BLOCK", "2", "35"}, {DAILY, "NETHERITE_INGOT", "1", "80"},
            {DAILY, "SADDLE", "1", "20"}, {DAILY, "NAME_TAG", "1", "10"},
            {DAILY, "SHULKER_SHELL", "2", "25"}, {DAILY, "BEACON", "1", "100"}
    };

    private static final class Weekend {
        String key = "";
        final List<String> picks = new ArrayList<>();
        final Set<String> bought = new HashSet<>();
        int rerolls;
    }

    private final KubyPlugins plugin;
    private final File file;
    private final File weekendFile;
    private final File dailyFile;
    private final Map<String, List<Item>> sections = new LinkedHashMap<>();
    private final Map<UUID, Weekend> weekend = new HashMap<>();
    private String dailyDay = "";
    private final List<String> dailyPicks = new ArrayList<>();
    private final Map<UUID, Set<String>> dailyBought = new HashMap<>();

    public ShopService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shop.yml");
        this.weekendFile = new File(plugin.getDataFolder(), "weekend.yml");
        this.dailyFile = new File(plugin.getDataFolder(), "dailyshop.yml");
        for (String s : SECTIONS) sections.put(s, new ArrayList<>());
        if (file.exists()) {
            load();
        } else {
            for (String[] d : DEFAULTS) {
                Material m = Material.matchMaterial(d[1]);
                if (m != null) {
                    sections.get(d[0]).add(new Item(newId(), m, Integer.parseInt(d[2]), Long.parseLong(d[3]), null, List.of()));
                }
            }
            save();
        }
        loadWeekend();
        loadDaily();
        if (sections.get(DAILY).isEmpty()) {
            for (String[] d : DEFAULTS) {
                if (!DAILY.equals(d[0])) continue;
                Material m = Material.matchMaterial(d[1]);
                if (m != null) sections.get(DAILY).add(new Item(newId(), m, Integer.parseInt(d[2]), Long.parseLong(d[3]), null, List.of()));
            }
            save();
        }
    }

    public static String newId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String sec : SECTIONS) {
            ConfigurationSection cs = yml.getConfigurationSection(sec);
            if (cs == null) continue;
            for (String id : cs.getKeys(false)) {
                ConfigurationSection s = cs.getConfigurationSection(id);
                if (s == null) continue;
                Material m = Material.matchMaterial(s.getString("material", ""));
                if (m == null) continue;
                sections.get(sec).add(new Item(id, m, s.getInt("amount", 1), s.getLong("price", 0),
                        s.getString("name"), s.getStringList("lore")));
            }
        }
    }

    public synchronized List<Item> items(String sec) {
        return new ArrayList<>(sections.getOrDefault(sec, List.of()));
    }

    public synchronized Item byId(String id) {
        for (String s : SECTIONS) {
            for (Item it : sections.get(s)) {
                if (it.id().equals(id)) return it;
            }
        }
        return null;
    }

    public synchronized String sectionOf(String id) {
        for (String s : SECTIONS) {
            for (Item it : sections.get(s)) {
                if (it.id().equals(id)) return s;
            }
        }
        return null;
    }

    public synchronized void add(String sec, Item it) {
        sections.get(sec).add(it);
        save();
    }

    public synchronized void replace(String sec, Item it) {
        List<Item> list = sections.get(sec);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(it.id())) {
                list.set(i, it);
                break;
            }
        }
        save();
    }

    public synchronized void remove(String id) {
        for (String s : SECTIONS) {
            if (sections.get(s).removeIf(it -> it.id().equals(id))) break;
        }
        save();
    }

    /** Laufende Nummer über alle Sektionen außer dem Wochenende. 0 = nicht gefunden. */
    public synchronized int slotNumber(String id) {
        int n = 0;
        for (String s : SECTIONS) {
            if (WEEKEND.equals(s) || DAILY.equals(s)) continue;
            for (Item it : sections.get(s)) {
                n++;
                if (it.id().equals(id)) return n;
            }
        }
        return 0;
    }

    /** Suche nach Nummer, ID, Material oder Name. */
    public synchronized List<Item> search(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Item> out = new ArrayList<>();
        try {
            int n = Integer.parseInt(q);
            for (String s : SECTIONS) {
                if (WEEKEND.equals(s) || DAILY.equals(s)) continue;
                for (Item it : sections.get(s)) {
                    if (slotNumber(it.id()) == n) out.add(it);
                }
            }
            return out;
        } catch (NumberFormatException ignored) {
            // Kein Zahlenwert, weiter mit Textsuche
        }
        String mat = q.replace(' ', '_');
        for (String s : SECTIONS) {
            for (Item it : sections.get(s)) {
                String name = it.name() == null ? "" : it.name().toLowerCase(Locale.ROOT);
                if (it.material().name().toLowerCase(Locale.ROOT).contains(mat)
                        || it.id().equalsIgnoreCase(q) || name.contains(q)) {
                    out.add(it);
                }
            }
        }
        return out;
    }

    // ---------- Wochenende ----------

    /** Schlüssel des aktuellen Wochenendes: das letzte Samstag-Datum. */
    public static String weekendKey() {
        LocalDate d = LocalDate.now(ZONE);
        int back = (d.getDayOfWeek().getValue() - DayOfWeek.SATURDAY.getValue() + 7) % 7;
        return d.minusDays(back).toString();
    }

    public int count() { return Math.max(1, plugin.getConfig().getInt("weekend.count", 4)); }
    public long rerollCost() { return plugin.getConfig().getLong("weekend.reroll-jewels", 50); }
    public int maxRerolls() { return plugin.getConfig().getInt("weekend.max-rerolls", 3); }

    private Weekend weekendOf(UUID id) {
        return weekend.computeIfAbsent(id, k -> new Weekend());
    }

    /** Die Angebote dieses Spielers für das aktuelle Wochenende. Neu gewürfelt, sobald ein neues Wochenende beginnt. */
    public synchronized List<Item> weekendPicks(UUID id) {
        Weekend w = weekendOf(id);
        String key = weekendKey();
        if (!key.equals(w.key)) {
            w.key = key;
            w.bought.clear();
            w.rerolls = 0;
            resample(w);
            saveWeekend();
        }
        List<Item> out = new ArrayList<>();
        for (String pid : w.picks) {
            Item it = byId(pid);
            if (it != null) out.add(it);
        }
        return out;
    }

    private void resample(Weekend w) {
        List<Item> pool = new ArrayList<>(sections.get(WEEKEND));
        Collections.shuffle(pool);
        w.picks.clear();
        for (int i = 0; i < Math.min(count(), pool.size()); i++) w.picks.add(pool.get(i).id());
    }

    public synchronized int rerollsLeft(UUID id) {
        return Math.max(0, maxRerolls() - weekendOf(id).rerolls);
    }

    /** Neuwurf nur für diesen Spieler, kostet Juwelen. Gibt null bei Erfolg zurück. */
    public synchronized String reroll(Player p) {
        UUID id = p.getUniqueId();
        weekendPicks(id);
        Weekend w = weekendOf(id);
        if (w.rerolls >= maxRerolls()) return "Du hast alle Neuwürfe für dieses Wochenende verbraucht.";
        if (!plugin.jewels().take(id, rerollCost())) return "Du brauchst " + rerollCost() + " Juwelen für einen Neuwurf.";
        w.rerolls++;
        resample(w);
        saveWeekend();
        return null;
    }

    public synchronized boolean isBought(UUID id, String itemId) {
        return weekendOf(id).bought.contains(itemId);
    }

    public synchronized void markBought(UUID id, String itemId) {
        weekendOf(id).bought.add(itemId);
        saveWeekend();
    }

    private void loadWeekend() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(weekendFile);
        for (String k : yml.getKeys(false)) {
            Weekend w = new Weekend();
            w.key = yml.getString(k + ".key", "");
            w.picks.addAll(yml.getStringList(k + ".picks"));
            w.bought.addAll(yml.getStringList(k + ".bought"));
            w.rerolls = yml.getInt(k + ".rerolls", 0);
            weekend.put(UUID.fromString(k), w);
        }
    }

    private synchronized void saveWeekend() {
        YamlConfiguration out = new YamlConfiguration();
        weekend.forEach((id, w) -> {
            String k = id.toString();
            out.set(k + ".key", w.key);
            out.set(k + ".picks", new ArrayList<>(w.picks));
            out.set(k + ".bought", new ArrayList<>(w.bought));
            out.set(k + ".rerolls", w.rerolls);
        });
        try {
            plugin.getDataFolder().mkdirs();
            out.save(weekendFile);
        } catch (IOException e) {
            plugin.getLogger().warning("weekend.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // ---------- Daily Shop: für alle gleich, täglich neu, bezahlt mit Juwelen ----------

    public static String dailyKey() {
        return LocalDate.now(ZONE).toString();
    }

    public int dailyCount() {
        return Math.max(1, plugin.getConfig().getInt("daily-shop.count", 6));
    }

    /** Die heutigen Angebote. Werden neu gewürfelt, sobald ein neuer Tag beginnt. */
    public synchronized List<Item> dailyItems(UUID id) {
        String day = dailyKey();
        if (!day.equals(dailyDay)) {
            dailyDay = day;
            dailyPicks.clear();
            dailyPicks.addAll(sample(DAILY, dailyCount()));
            dailyBought.clear();
            saveDaily();
        }
        List<Item> out = new ArrayList<>();
        for (String pid : dailyPicks) {
            Item it = byId(pid);
            if (it != null) out.add(it);
        }
        return out;
    }

    public synchronized boolean dailyBoughtBy(UUID id, String itemId) {
        return dailyBought.getOrDefault(id, Set.of()).contains(itemId);
    }

    public synchronized void markDailyBought(UUID id, String itemId) {
        dailyBought.computeIfAbsent(id, k -> new HashSet<>()).add(itemId);
        saveDaily();
    }

    private List<String> sample(String sec, int n) {
        List<Item> pool = new ArrayList<>(sections.get(sec));
        Collections.shuffle(pool);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < Math.min(n, pool.size()); i++) ids.add(pool.get(i).id());
        return ids;
    }

    private void loadDaily() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dailyFile);
        dailyDay = yml.getString("day", "");
        dailyPicks.addAll(yml.getStringList("picks"));
        ConfigurationSection b = yml.getConfigurationSection("bought");
        if (b != null) {
            for (String k : b.getKeys(false)) dailyBought.put(UUID.fromString(k), new HashSet<>(b.getStringList(k)));
        }
    }

    private synchronized void saveDaily() {
        YamlConfiguration out = new YamlConfiguration();
        out.set("day", dailyDay);
        out.set("picks", new ArrayList<>(dailyPicks));
        dailyBought.forEach((id, set) -> out.set("bought." + id, new ArrayList<>(set)));
        try {
            plugin.getDataFolder().mkdirs();
            out.save(dailyFile);
        } catch (IOException e) {
            plugin.getLogger().warning("dailyshop.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (String sec : SECTIONS) {
            for (Item it : sections.get(sec)) {
                String base = sec + "." + it.id() + ".";
                out.set(base + "material", it.material().name());
                out.set(base + "amount", it.amount());
                out.set(base + "price", it.price());
                if (it.name() != null) out.set(base + "name", it.name());
                if (it.lore() != null && !it.lore().isEmpty()) out.set(base + "lore", it.lore());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("shop.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}

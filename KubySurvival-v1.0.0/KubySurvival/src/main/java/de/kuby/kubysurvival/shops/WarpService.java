package de.kuby.kubysurvival.shops;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Locs;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.claim.ClaimService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Spieler-Warps in Survival: jeder kann bis zu drei kaufen (Preise in shops.warp-prices), genau dort, wo er steht (auf
 * dem eigenen Grundstück). Jeder Warp hat Kategorie, Namensfarbe, Ort-Namen und Beschreibung; gezählt werden Besuche
 * gesamt und in dieser Woche (ab Montag). Spieler können Warps anpinnen (stehen dann oben). Gespeichert in warps.yml.
 */
public final class WarpService {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-]{3,16}");

    /** Kategorien (id, Name im Chip, Name auf der Karte). */
    public enum Category {
        MOBFARM("mobfarm", "Mobfarm", "Mobfarm"), SHOPS("shops", "Shops", "Shops"),
        BUECHER("buecher", "Bücher", "Bücher/Handelshalle"), SPIELE("spiele", "Spiele", "Spiele"),
        PARKOUR("parkour", "Parkour", "Parkour");

        public final String id, chip, label;

        Category(String id, String chip, String label) {
            this.id = id;
            this.chip = chip;
            this.label = label;
        }

        public static Category of(String id) {
            for (Category c : values()) if (c.id.equalsIgnoreCase(id)) return c;
            return SHOPS;
        }
    }

    /** Farben für den Namen (Minecraft-Farben). */
    public static final Map<String, Integer> COLORS = new LinkedHashMap<>();

    static {
        COLORS.put("Grün", 0x54FC54);
        COLORS.put("Dunkelgrün", 0x00A800);
        COLORS.put("Türkis", 0x54FCFC);
        COLORS.put("Blau", 0x5454FC);
        COLORS.put("Gold", 0xFCA800);
        COLORS.put("Gelb", 0xFCFC54);
        COLORS.put("Rot", 0xFC5454);
        COLORS.put("Pink", 0xFC54FC);
        COLORS.put("Lila", 0xA800A8);
        COLORS.put("Weiß", 0xFCFCFC);
        COLORS.put("Grau", 0xA8A8A8);
    }

    /** Ein Warp. Name ist zugleich die ID (kleingeschrieben eindeutig). */
    public static final class Warp {
        public final String name;
        public final UUID owner;
        public Location location;
        public Material icon = Material.ENDER_PEARL;
        public long visits;
        public final long created;
        public Category category = Category.SHOPS;
        /** Farbe des Namens (Name aus COLORS). */
        public String color = "Grün";
        /** Ort-Name auf der Karte, z. B. „Lux Mall“; leer = „<Besitzer>s Grundstück“. */
        public String place = "";
        public String desc = "";
        /** Besuche in der Woche week (Montag, yyyy-MM-dd). */
        public long weekVisits;
        public String week = "";

        Warp(String name, UUID owner, Location location, long created) {
            this.name = name;
            this.owner = owner;
            this.location = location;
            this.created = created;
        }
    }

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File file;
    private final Map<String, Warp> warps = new LinkedHashMap<>();
    /** Angepinnte Warps je Spieler (Namen klein). */
    private final Map<UUID, java.util.Set<String>> pins = new java.util.HashMap<>();

    public WarpService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "warps.yml");
        load();
    }

    public List<Long> prices() {
        List<Long> list = core.getConfig().getLongList("shops.warp-prices");
        return list.isEmpty() ? List.of(50_000L, 150_000L, 500_000L) : list;
    }

    public synchronized List<Warp> all() {
        List<Warp> out = new ArrayList<>(warps.values());
        out.sort(Comparator.comparingLong((Warp w) -> -w.visits).thenComparing(w -> w.name.toLowerCase()));
        return out;
    }

    public synchronized List<Warp> of(UUID owner) {
        List<Warp> out = new ArrayList<>();
        for (Warp w : warps.values()) if (w.owner.equals(owner)) out.add(w);
        return out;
    }

    public synchronized Warp get(String name) {
        return name == null ? null : warps.get(name.toLowerCase());
    }

    /** Montag der laufenden Woche (Berlin), für die Besuche „diese Woche“. */
    public static String weekKey() {
        java.time.LocalDate d = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Berlin"));
        return d.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString();
    }

    public synchronized long weekVisits(Warp w) {
        return weekKey().equals(w.week) ? w.weekVisits : 0;
    }

    public synchronized boolean pinned(UUID player, Warp w) {
        return pins.getOrDefault(player, java.util.Set.of()).contains(w.name.toLowerCase());
    }

    public synchronized boolean togglePin(UUID player, Warp w) {
        java.util.Set<String> set = pins.computeIfAbsent(player, k -> new java.util.HashSet<>());
        boolean now = !set.remove(w.name.toLowerCase());
        if (now) set.add(w.name.toLowerCase());
        save();
        return now;
    }

    /** Ort-Name für die Karte. */
    public static String placeOf(Warp w) {
        if (!w.place.isBlank()) return w.place;
        String owner = org.bukkit.Bukkit.getOfflinePlayer(w.owner).getName();
        // Englisch direkt (die Vorlage „s Grundstück“ würde auch „Dieses Grundstück“ treffen)
        if (de.kuby.kubyplugins.util.I18n.context()) return (owner == null ? "Player" : owner) + "'s plot";
        return (owner == null ? "Spieler" : owner) + "s Grundstück";
    }

    /** Einstellungen des Besitzers: Kategorie, Farbe, Ort-Name, Beschreibung. Null bei Erfolg, sonst warum nicht. */
    public synchronized String edit(Player p, Warp w, Category category, String color, String place, String desc) {
        if (!w.owner.equals(p.getUniqueId()) && !p.hasPermission("kuby.admin")) return "Das ist nicht dein Warp.";
        String pl = place == null ? "" : place.trim();
        String de = desc == null ? "" : desc.trim();
        if (pl.length() > 24) return "Der Ort-Name darf höchstens 24 Zeichen haben.";
        if (de.length() > 60) return "Die Beschreibung darf höchstens 60 Zeichen haben.";
        w.category = category;
        if (COLORS.containsKey(color)) w.color = color;
        w.place = pl;
        w.desc = de;
        save();
        return null;
    }

    /** Preis für den nächsten Warp oder -1, wenn das Maximum erreicht ist. */
    public synchronized long nextPrice(UUID owner) {
        int n = of(owner).size();
        List<Long> prices = prices();
        return n >= prices.size() ? -1 : prices.get(n);
    }

    /** Darf hier ein Warp stehen? Null wenn ja, sonst der Grund. */
    private String placeError(Player p, Location l) {
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) return "Warps gibt es nur in Survival.";
        if (l.getWorld() == null || !l.getWorld().getName().equals(core.modes().worldOf(ModeService.SURVIVAL))) {
            return "Warps gehen nur in der Survival-Welt (nicht in der Farmwelt, im Nether oder End).";
        }
        if (!addon.claims().canBuild(p, l)) return "Hier darfst du keinen Warp setzen, das Grundstück gehört jemand anderem.";
        String blocked = core.homes().blockedReason(l);
        if (blocked != null) return "Hier darf kein Warp stehen.";
        return null;
    }

    /** Kauft einen Warp an der Position des Spielers. Null bei Erfolg, sonst eine Meldung. */
    public synchronized String create(Player p, String name) {
        if (name == null || !NAME.matcher(name).matches()) return "Der Name braucht 3 bis 16 Zeichen: Buchstaben, Zahlen, _ oder -.";
        if (warps.containsKey(name.toLowerCase())) return "Einen Warp mit diesem Namen gibt es schon.";
        if (List.of("create", "erstellen", "kaufen", "manage", "verwalten", "meine", "delete", "loeschen", "help", "hilfe")
                .contains(name.toLowerCase())) return "Dieser Name ist reserviert.";
        String err = placeError(p, p.getLocation());
        if (err != null) return err;
        ClaimService.Claim claim = addon.claims().at(p.getLocation());
        if (claim == null || !claim.owner().equals(p.getUniqueId())) return "Stell dich auf dein Grundstück, um hier einen Warp zu kaufen.";
        long price = nextPrice(p.getUniqueId());
        if (price < 0) return "Du hast schon " + prices().size() + " Warps, mehr gehen nicht.";
        if (!core.economy().withdraw(p.getUniqueId(), price)) return "Dein " + (of(p.getUniqueId()).size() + 1)
                + ". Warp kostet " + de.kuby.kubyplugins.util.Msg.coins(price) + " Coins.";
        warps.put(name.toLowerCase(), new Warp(name, p.getUniqueId(), p.getLocation(), System.currentTimeMillis()));
        save();
        return null;
    }

    public synchronized String move(Player p, Warp w) {
        if (!w.owner.equals(p.getUniqueId())) return "Das ist nicht dein Warp.";
        String err = placeError(p, p.getLocation());
        if (err != null) return err;
        w.location = p.getLocation();
        save();
        return null;
    }

    public synchronized void setIcon(Warp w, Material m) {
        w.icon = m;
        save();
    }

    public synchronized void delete(Warp w) {
        warps.remove(w.name.toLowerCase());
        for (java.util.Set<String> set : pins.values()) set.remove(w.name.toLowerCase());
        save();
    }

    /** Reist zu einem Warp (nur aus Survival, mit der normalen Teleport-Wartezeit). */
    public void travel(Player p, Warp w) {
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) {
            de.kuby.kubyplugins.util.Msg.send(p, "<red>Spieler-Warps gibt es nur in Survival.");
            return;
        }
        synchronized (this) {
            if (!w.owner.equals(p.getUniqueId())) {
                w.visits++;
                String week = weekKey();
                if (!week.equals(w.week)) {
                    w.week = week;
                    w.weekVisits = 0;
                }
                w.weekVisits++;
            }
        }
        de.kuby.kubyplugins.util.Teleports.delayed(core, p, w.location.clone(), "Warp " + w.name);
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection pinned = yml.getConfigurationSection("pins");
        if (pinned != null) {
            for (String k : pinned.getKeys(false)) {
                try {
                    pins.put(UUID.fromString(k), new java.util.HashSet<>(pinned.getStringList(k)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection s = yml.getConfigurationSection("warps");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            try {
                Location l = Locs.parse(s.getString(key + ".location", ""));
                if (l == null) continue;
                Warp w = new Warp(s.getString(key + ".name", key), UUID.fromString(s.getString(key + ".owner", "")), l,
                        s.getLong(key + ".created"));
                Material icon = Material.matchMaterial(s.getString(key + ".icon", "ENDER_PEARL"));
                if (icon != null && icon.isItem()) w.icon = icon;
                w.visits = s.getLong(key + ".visits");
                w.category = Category.of(s.getString(key + ".kategorie", "shops"));
                w.color = s.getString(key + ".farbe", "Grün");
                if (!COLORS.containsKey(w.color)) w.color = "Grün";
                w.place = s.getString(key + ".ort", "");
                w.desc = s.getString(key + ".beschreibung", "");
                w.weekVisits = s.getLong(key + ".woche-besuche");
                w.week = s.getString(key + ".woche", "");
                warps.put(key, w);
            } catch (IllegalArgumentException e) {
                addon.getLogger().warning("warps.yml: Warp " + key + " ungültig.");
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<String, Warp> e : warps.entrySet()) {
            Warp w = e.getValue();
            String b = "warps." + e.getKey() + ".";
            out.set(b + "name", w.name);
            out.set(b + "owner", w.owner.toString());
            out.set(b + "location", Locs.serialize(w.location));
            out.set(b + "icon", w.icon.name());
            out.set(b + "visits", w.visits);
            out.set(b + "created", w.created);
            out.set(b + "kategorie", w.category.id);
            out.set(b + "farbe", w.color);
            out.set(b + "ort", w.place);
            out.set(b + "beschreibung", w.desc);
            out.set(b + "woche-besuche", w.weekVisits);
            out.set(b + "woche", w.week);
        }
        for (Map.Entry<UUID, java.util.Set<String>> e : pins.entrySet()) {
            if (!e.getValue().isEmpty()) out.set("pins." + e.getKey(), new ArrayList<>(e.getValue()));
        }
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException ex) {
            addon.getLogger().warning("warps.yml: " + ex.getMessage());
        }
    }
}

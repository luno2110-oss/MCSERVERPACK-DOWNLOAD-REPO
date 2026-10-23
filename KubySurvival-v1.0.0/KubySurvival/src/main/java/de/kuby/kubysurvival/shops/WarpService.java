package de.kuby.kubysurvival.shops;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Locs;
import de.kuby.kubysurvival.KubySurvival;
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
 * Spieler-Warps in Survival: jeder kann bis zu drei kaufen (Preise in shops.warp-prices), genau dort, wo er steht.
 * Andere reisen über /sw mit einem Klick hin. Gespeichert in warps.yml.
 */
public final class WarpService {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-]{3,16}");

    /** Ein Warp. Name ist zugleich die ID (kleingeschrieben eindeutig). */
    public static final class Warp {
        public final String name;
        public final UUID owner;
        public Location location;
        public Material icon = Material.ENDER_PEARL;
        public long visits;
        public final long created;

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
        if (List.of("create", "erstellen", "kaufen", "manage", "verwalten", "delete", "loeschen", "help", "hilfe")
                .contains(name.toLowerCase())) return "Dieser Name ist reserviert.";
        String err = placeError(p, p.getLocation());
        if (err != null) return err;
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
        save();
    }

    /** Reist zu einem Warp (nur aus Survival, mit der normalen Teleport-Wartezeit). */
    public void travel(Player p, Warp w) {
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) {
            de.kuby.kubyplugins.util.Msg.send(p, "<red>Spieler-Warps gibt es nur in Survival.");
            return;
        }
        synchronized (this) {
            if (!w.owner.equals(p.getUniqueId())) w.visits++;
        }
        de.kuby.kubyplugins.util.Teleports.delayed(core, p, w.location.clone(), "Warp " + w.name);
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
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
        }
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException ex) {
            addon.getLogger().warning("warps.yml: " + ex.getMessage());
        }
    }
}
